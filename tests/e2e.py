"""Проверка Android-клиента на телефоне: интерфейс управляется через adb и uiautomator.

Снимки экрана не подходят: FLAG_SECURE делает их черными. Элементы ищутся по resource-id,
который Compose берет из testTag. Запуск через run.sh, с --biometric - еще и разблокировка
отпечатком (нужно приложить палец к сканеру).
"""

import argparse
import base64
import hashlib
import hmac
import json
import os
import re
import shutil
import struct
import subprocess
import sys
import tempfile
import time
import xml.etree.ElementTree as ET

ADB = os.path.expanduser("~/Android/Sdk/platform-tools/adb")
MASTER = "correct horse battery staple"
NEW_MASTER = "new horse battery staple"
WEB_MASTER = "web horse battery staple"
TOTP_SECRET = "JBSWY3DPEHPK3PXP"
CONTAINER = os.environ.get("PROSODY_CONTAINER", "inkvault-android-ui")
# отдельная папка: тест не листает файлы пользователя в загрузках и удаляет ее после прогона
DOWNLOADS = "/sdcard/Download/inkvault-e2e"
EXPORT_TOOL = os.path.join(os.path.dirname(os.path.abspath(__file__)), "export_file.mjs")
# тестовое хранилище из ядра: учетная запись и записи на сервере от имени другого устройства
VAULT_CLI = os.path.join(os.path.dirname(os.path.abspath(__file__)), "../inkvault-core/testkit/vault")
results = []


def adb(*args: str) -> str:
    return subprocess.run([ADB, *args], capture_output=True, text=True, check=True).stdout


def report(ok: bool, name: str, detail: str = "") -> None:
    results.append(ok)
    print(f"[{'OK' if ok else 'FAIL'}] {name}{': ' + detail if detail else ''}", flush=True)


def dump() -> ET.Element:
    # во время смены окон uiautomator на Android 12 бывает убит системой или не находит корень окна
    for attempt in range(3):
        try:
            adb("shell", "uiautomator", "dump", "/sdcard/inkvault-ui.xml")
            text = adb("shell", "cat", "/sdcard/inkvault-ui.xml")
            return ET.fromstring(text[text.index("<?xml"):])
        except (subprocess.CalledProcessError, ValueError, ET.ParseError):
            if attempt == 2:
                raise
            time.sleep(1)
    raise AssertionError("недостижимо")


def find(root: ET.Element, rid: str | None = None, text: str | None = None) -> ET.Element | None:
    for node in root.iter("node"):
        if rid and node.get("resource-id") == rid:
            return node
        if text and text in node.get("text", ""):
            return node
    return None


def wait(rid: str | None = None, text: str | None = None, timeout: float = 20) -> ET.Element | None:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        node = find(dump(), rid, text)
        if node is not None:
            return node
        time.sleep(1)
    return None


def bounds(node: ET.Element) -> list[int]:
    return list(map(int, re.findall(r"\d+", node.get("bounds", ""))))


def tap(rid: str | None = None, text: str | None = None, timeout: float = 20) -> None:
    node = wait(rid, text, timeout)
    if node is None:
        raise RuntimeError(f"на экране нет элемента {rid or text}")
    # элемент за краем прокрутки uiautomator отдает с нулевыми границами: сначала прокрутка к нему
    if rid and bounds(node)[3] - bounds(node)[1] <= 40:
        node = reveal(rid)
    x1, y1, x2, y2 = bounds(node)
    adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))


def find_any(root: ET.Element, labels: tuple[str, ...]) -> ET.Element | None:
    """Элемент чужого приложения по тексту или content-desc без учета регистра: подписи зависят от языка телефона."""
    wanted = [label.lower() for label in labels]
    for node in root.iter("node"):
        text = (node.get("text", "") + " " + node.get("content-desc", "")).lower()
        if any(label in text for label in wanted):
            return node
    return None


def tap_any(labels: tuple[str, ...], timeout: float = 15) -> None:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        node = find_any(dump(), labels)
        if node is not None:
            x1, y1, x2, y2 = bounds(node)
            adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
            return
        time.sleep(1)
    raise RuntimeError(f"на экране нет элемента {labels[0]}")


def open_downloads() -> None:
    """Папка теста в загрузках в системном диалоге выбора файла (DocumentsUI)."""
    # в DocumentsUI на Samsung с Android 14 папка называется "Скачанные"
    labels = ("Скачанные", "Загрузки", "Downloads")
    time.sleep(2)
    if find_any(dump(), labels) is None:
        tap_any(("Показать корневые папки", "Show roots"))
    tap_any(labels)
    tap(text=os.path.basename(DOWNLOADS))
    time.sleep(1)


def media_scan(path: str) -> None:
    """Раздел "Скачанные" в DocumentsUI берет список из MediaStore: файл после adb push нужно проиндексировать, удаленный - убрать из индекса."""
    adb("shell", "am", "broadcast", "-a", "android.intent.action.MEDIA_SCANNER_SCAN_FILE", "-d", f"file://{path}")


def downloads() -> list[str]:
    return adb("shell", "ls", DOWNLOADS).split()


def vault_cli(command: str, master: str = MASTER) -> str:
    env = {**os.environ, "MASTER_PASSWORD": master}
    return subprocess.run(["node", "vault.mjs", command], cwd=VAULT_CLI, check=True, capture_output=True, text=True, env=env).stdout


def server_has(names: tuple[str, ...], master: str = MASTER, timeout: float = 30) -> str:
    """Записи на сервере глазами другого устройства; ждет, пока телефон их опубликует."""
    deadline = time.monotonic() + timeout
    out = ""
    while time.monotonic() < deadline:
        try:
            out = vault_cli("list", master)
        except subprocess.CalledProcessError as e:
            out = e.stderr or e.stdout
        if all(name in out for name in names):
            break
        time.sleep(2)
    return out


def restart_server() -> None:
    """Чистый сервер: тот же образ с учетными записями XMPP, но без данных PEP."""
    subprocess.run(["podman", "rm", "-f", CONTAINER], check=True, capture_output=True)
    subprocess.run(["podman", "run", "-d", "--name", CONTAINER, "-p", "127.0.0.1:15222:5222", "-p", "127.0.0.1:15280:5280", "inkvault-test-prosody"], check=True, capture_output=True)
    for _ in range(60):
        logs = subprocess.run(["podman", "logs", CONTAINER], capture_output=True, text=True)
        if "Activated service 'c2s'" in logs.stdout + logs.stderr:
            return
        time.sleep(0.5)
    raise RuntimeError("тестовый Prosody не запустился")


def relock(master: str = MASTER) -> None:
    tap("menu")
    tap("lock")
    type_into("master-password", master)
    tap("unlock")
    if wait("new", timeout=60) is None:
        raise RuntimeError("хранилище не открылось после блокировки")


def fill_login(master: str) -> None:
    type_into("jid", "alice@vault.test")
    type_into("xmpp-password", "secret")
    type_into("master-password", master)
    type_into("host", "127.0.0.1")
    tap("port")
    adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
    for _ in range(6):
        adb("shell", "input", "keyevent", "KEYCODE_DEL")
    adb("shell", "input", "text", "15222")
    hide_keyboard()
    tap("insecure")
    tap("open")


def export_tool(*args: str) -> str:
    return subprocess.run(["node", EXPORT_TOOL, *args], check=True, capture_output=True, text=True).stdout


def reveal(rid: str) -> ET.Element:
    """Прокручивает карточку записи, пока элемент не окажется на экране: карточка длиннее экрана."""
    width, height = map(int, re.search(r"(\d+)x(\d+)", adb("shell", "wm", "size")).groups())
    for start, end in ((0.7, 0.3), (0.3, 0.7)):
        for _ in range(5):
            node = find(dump(), rid)
            # у элемента за краем прокрутки обрезанные границы нулевой высоты
            if node is not None and bounds(node)[3] - bounds(node)[1] > 40:
                return node
            adb("shell", "input", "swipe", str(width // 2), str(int(height * start)), str(width // 2), str(int(height * end)), "300")
            time.sleep(0.5)
    raise RuntimeError(f"элемент {rid} не найден при прокрутке")


def clear_field(rid: str, length: int) -> None:
    tap(rid)
    adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
    for _ in range(length):
        adb("shell", "input", "keyevent", "KEYCODE_DEL")
    hide_keyboard()


def app_file_exists(name: str) -> bool:
    # отладочная сборка доступна через run-as
    return name in adb("shell", "run-as", "dev.inkov.inkvault", "ls", "files").split()


def totp(secret: str, unix_time: int) -> str:
    """Эталонный TOTP (RFC 6238, SHA1, 6 цифр, 30 с), независимо от ядра."""
    mac = hmac.new(base64.b32decode(secret), struct.pack(">Q", unix_time // 30), hashlib.sha1).digest()
    offset = mac[-1] & 15
    return f"{(struct.unpack('>I', mac[offset:offset + 4])[0] & 0x7FFFFFFF) % 1_000_000:06d}"


def hide_keyboard() -> None:
    # Back закрывает клавиатуру, но без нее закрыл бы экран, поэтому только при открытой клавиатуре
    if "mInputShown=true" in adb("shell", "dumpsys", "input_method"):
        adb("shell", "input", "keyevent", "KEYCODE_BACK")
        time.sleep(0.5)


def field_text(rid: str) -> str:
    node = find(dump(), rid)
    return node.get("text", "") if node is not None else ""


def type_into(rid: str, value: str) -> None:
    """Ввод с проверкой: на медленном телефоне текст, отправленный до фокуса поля, теряется."""
    before = field_text(rid)
    for _ in range(3):
        tap(rid)
        # поле внизу экрана: клавиатура открывается, форма сдвигается, и только потом поле принимает текст
        deadline = time.monotonic() + 8
        while time.monotonic() < deadline:
            node = find(dump(), rid)
            if node is not None and node.get("focused") == "true" and "mInputShown=true" in adb("shell", "dumpsys", "input_method"):
                break
            time.sleep(0.3)
        adb("shell", "input", "text", "'" + value.replace(" ", "%s") + "'")
        hide_keyboard()
        text = field_text(rid)
        # поле пароля показывает точки, поэтому для него сверяется только длина
        hidden = text != "" and set(text) == {"•"}
        if (len(text) >= len(before) + len(value)) if hidden else (value in text):
            return
    raise RuntimeError(f"текст не попал в поле {rid}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--biometric", action="store_true", help="проверить разблокировку отпечатком")
    args = parser.parse_args()

    # погасший или заблокированный экран: uiautomator видит экран блокировки, а не приложение
    adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
    if "isKeyguardShowing=true" in adb("shell", "dumpsys", "window"):
        print("Разблокируйте телефон", flush=True)
        deadline = time.monotonic() + 60
        while "isKeyguardShowing=true" in adb("shell", "dumpsys", "window") and time.monotonic() < deadline:
            time.sleep(1)
    adb("shell", "am", "start", "-n", "dev.inkov.inkvault/dev.inkov.inkvault.android.MainActivity")
    if wait("jid", timeout=60) is None:
        raise RuntimeError("приложение не открылось на экране входа")
    fill_login(MASTER)
    report(wait("repeat-password", timeout=30) is not None, "на сервере нет хранилища, клиент предлагает создать")
    type_into("repeat-password", MASTER)
    # на телефоне с экраном меньше кнопка ниже видимой части формы
    reveal("create")
    tap("create")
    report(wait("new", timeout=60) is not None, "хранилище создано, открыт список")
    report(wait(text="в сети", timeout=30) is not None, "клиент в сети")
    account = field_text("account")
    report(account == "alice@vault.test", "JID учетной записи в заголовке", account)

    tap("new")
    type_into("f-name", "github.com")
    type_into("f-username", "alice")
    tap("gen-password")
    tap("g-use")
    reveal("save")
    tap("save")
    tap("back")
    report(wait(text="github.com", timeout=20) is not None, "запись создана с паролем из генератора")

    subprocess.run(["node", "vault.mjs", "add"], cwd=VAULT_CLI, check=True, capture_output=True, env={**os.environ, "MASTER_PASSWORD": MASTER})
    report(wait(text="prod-db", timeout=30) is not None, "запись с другого устройства пришла уведомлением")

    # пароль вида PIN + одноразовый код
    tap("new")
    type_into("f-name", "vpn")
    report(find(dump(), "f-totp") is None, "в режиме обычного пароля поля секрета TOTP нет")
    tap("mode-pin-totp")
    type_into("f-totp", "0189")
    reveal("save")
    tap("save")
    time.sleep(1)
    # неудачное сохранение не открывает созданную запись: заголовок остается прежним
    not_saved = find(dump(), text="Новая запись") is not None
    reveal("f-totp")
    report(not_saved and find(dump(), text="неверный секрет TOTP") is not None, "неверный секрет TOTP отмечен у поля, запись не сохранена")
    clear_field("f-totp", 4)
    type_into("f-totp", TOTP_SECRET)
    hidden = reveal("otp-value").get("text", "")
    report(hidden == "•" * 6, "готовый пароль скрыт по умолчанию", hidden)
    reveal("otp-show")
    tap("otp-show")
    shown = wait("otp-value").get("text", "")
    # время телефона, а не компьютера; код мог смениться между чтением и расчетом
    phone_time = int(adb("shell", "date", "+%s").strip())
    codes = [totp(TOTP_SECRET, t) for t in (phone_time, phone_time - 30)]
    report(shown in codes, "без PIN готовый пароль - текущий код TOTP", shown if shown in codes else f"{shown}, ожидалось {' или '.join(codes)}")
    reveal("f-password")
    type_into("f-password", "pinpin")
    shown = reveal("otp-value").get("text", "")
    phone_time = int(adb("shell", "date", "+%s").strip())
    expected = [f"pinpin{totp(TOTP_SECRET, t)}" for t in (phone_time, phone_time - 30)]
    report(shown in expected, "готовый пароль - PIN и текущий код TOTP", shown if shown in expected else f"{shown}, ожидалось {' или '.join(expected)}")
    before = wait("otp-remaining").get("text", "")
    after = before
    deadline = time.monotonic() + 5
    while after == before and time.monotonic() < deadline:
        after = wait("otp-remaining").get("text", "")
    report(before != after and re.fullmatch(r"обновится через \d+ с", after) is not None, "обратный отсчет обновляется", f"{before} -> {after}")
    reveal("otp-copy")
    tap("otp-copy")
    report(wait(text="скопировано", timeout=10) is not None, "готовый пароль копируется")
    reveal("save")
    tap("save")
    tap("back")
    tap(text="vpn")
    report(reveal("otp-show") is not None, "режим PIN + код сохранен в записи")
    tap("back")

    # экспорт и импорт: файл из ядра WASM, как у web-клиента, открывается на телефоне, файл с телефона - в WASM
    tmp = tempfile.mkdtemp(prefix="inkvault-e2e-")
    exported: list[str] = []
    try:
        web_file = os.path.join(tmp, "web.json")
        export_tool("make", web_file, "web export password")
        adb("shell", "mkdir", "-p", DOWNLOADS)
        adb("push", web_file, f"{DOWNLOADS}/inkvault-web-export.json")
        media_scan(f"{DOWNLOADS}/inkvault-web-export.json")
        tap("menu")
        tap("backup")
        tap("b-file")
        open_downloads()
        tap(text="inkvault-web-export.json")
        type_into("b-import-password", "web export password")
        # кнопка ниже видимой части диалога
        reveal("b-import")
        tap("b-import")
        report(wait(text="web-export-1", timeout=30) is not None, "файл экспорта из web импортируется на Android")

        before = set(downloads())
        tap("menu")
        tap("backup")
        type_into("b-password", "android export password")
        type_into("b-repeat", "android export password")
        tap("b-export")
        open_downloads()
        tap_any(("Сохранить", "Save"))
        deadline = time.monotonic() + 15
        while not exported and time.monotonic() < deadline:
            exported = [f for f in downloads() if f not in before and f.startswith("inkvault-export-")]
            time.sleep(1)
        names: list[str] = []
        if exported:
            local = os.path.join(tmp, "android.json")
            adb("pull", f"{DOWNLOADS}/{exported[0]}", local)
            names = json.loads(export_tool("read", local, "android export password"))
        report({"github.com", "vpn", "web-export-1"} <= set(names), "файл экспорта с Android открывается в ядре WASM", ", ".join(exported + names))
    finally:
        shutil.rmtree(tmp, ignore_errors=True)
        adb("shell", "rm", "-rf", DOWNLOADS)
        for path in [f"{DOWNLOADS}/inkvault-web-export.json", *(f"{DOWNLOADS}/{f}" for f in exported), DOWNLOADS]:
            media_scan(path)

    if args.biometric:
        tap("menu")
        tap("biometric-on")
        print("Приложите палец к сканеру отпечатков", flush=True)
        # уведомление видно 3 с, а снимок интерфейса занимает больше 2 с и может его пропустить,
        # поэтому включение проверяется по ключу устройства, который приложение сохраняет после отпечатка
        deadline = time.monotonic() + 90
        while not app_file_exists("device.blob") and time.monotonic() < deadline:
            time.sleep(0.5)
        report(app_file_exists("device.blob"), "разблокировка отпечатком включена", "ключ устройства сохранен")
        tap("menu")
        tap("lock")
        tap("unlock-biometric", timeout=30)
        print("Приложите палец к сканеру еще раз", flush=True)
        report(wait("new", timeout=90) is not None, "хранилище разблокировано отпечатком без мастер-пароля")
        report(wait(text="github.com", timeout=20) is not None, "записи на месте после разблокировки отпечатком")

    # чистый сервер: телефон с кэшем возвращает на него учетную запись и записи
    restart_server()
    relock()
    out = server_has(("github.com", "web-export-1"))
    report("github.com" in out and "web-export-1" in out, "чистый сервер: телефон вернул учетную запись и записи", out.strip().splitlines()[-1] if out.strip() else "")

    # чужая учетная запись: синхронизация остановлена, учетная запись на сервере не перезаписывается
    foreign = re.search(r"новая учетная запись (\S+)", vault_cli("foreign", "other master password")).group(1)
    relock()
    report(wait("account-conflict", timeout=60) is not None and wait(text="синхронизация остановлена", timeout=10) is not None, "чужая учетная запись: синхронизация остановлена, выбор показан")
    report(foreign in vault_cli("key-id"), "чужая учетная запись не перезаписана")
    tap("conflict-keep")
    time.sleep(1)
    hidden = find(dump(), "account-conflict") is None
    tap("status")
    report(hidden and wait("account-conflict", timeout=10) is not None, "скрытый выбор открывается снова по статусу")
    tap("conflict-replace")
    tap("confirm-ok")
    out = server_has(("github.com", "web-export-1"))
    report("github.com" in out and foreign not in vault_cli("key-id"), "замена учетной записи на сервере: записи опубликованы заново")

    # переход на учетную запись с сервера
    vault_cli("foreign", "other master password")
    relock()
    tap("conflict-server", timeout=60)
    tap("confirm-ok")
    type_into("master-password", "other master password")
    tap("unlock")
    opened = wait("new", timeout=60) is not None and wait(text="в сети", timeout=60) is not None
    report(opened and find(dump(), text="github.com") is None, "переход на учетную запись с сервера: открыта ее мастер-паролем")

    # записи без учетной записи: создание на новом устройстве заблокировано подсказкой
    vault_cli("add", "other master password")
    vault_cli("delete-account")
    tap("menu")
    tap("lock")
    tap("forget")
    if wait("jid", timeout=30) is None:
        raise RuntimeError("экран входа не открылся после выхода с устройства")
    fill_login(MASTER)
    hint = wait(text="На сервере есть записи хранилища, но нет учетной записи", timeout=30) is not None
    # кнопка создания ниже видимой части формы входа
    create = reveal("create")
    report(hint and create.get("enabled") == "false", "записи без учетной записи: подсказка вместо создания", f"подсказка: {hint}, кнопка доступна: {create.get('enabled')}")
    type_into("repeat-password", MASTER)
    reveal("confirm-orphans")
    tap("confirm-orphans")
    reveal("create")
    tap("create")
    created = wait("new", timeout=60) is not None and wait(text="в сети", timeout=60) is not None
    report(created and "записей 0" in server_has(("записей 0",)), "подтвержденное создание: новое хранилище без прежних записей")

    # смена мастер-пароля на телефоне
    tap("menu")
    tap("change-password")
    type_into("cp-current", "wrong password")
    type_into("cp-new", NEW_MASTER)
    type_into("cp-repeat", NEW_MASTER)
    tap("cp-save")
    report(wait(text="неверный мастер-пароль", timeout=30) is not None, "смена мастер-пароля: неверный текущий пароль отклоняется")
    clear_field("cp-current", len("wrong password"))
    type_into("cp-current", MASTER)
    tap("cp-save")
    out = server_has(("записей 0",), NEW_MASTER)
    old = server_has(("неверный мастер-пароль",), MASTER, timeout=5)
    report("записей 0" in out and "неверный мастер-пароль" in old, "смена мастер-пароля: на сервере открывается только новым паролем")
    tap("menu")
    tap("lock")
    type_into("master-password", MASTER)
    tap("unlock")
    report(wait(text="неверный мастер-пароль", timeout=60) is not None, "после смены старый мастер-пароль не открывает хранилище")
    type_into("master-password", NEW_MASTER)
    tap("unlock")
    report(wait("new", timeout=60) is not None, "новый мастер-пароль открывает хранилище")

    # пароль сменили на другом устройстве, пока телефон заблокирован: копия учетной записи на телефоне устарела
    tap("menu")
    tap("lock")
    env = {**os.environ, "MASTER_PASSWORD": NEW_MASTER, "NEW_MASTER_PASSWORD": WEB_MASTER}
    subprocess.run(["node", "vault.mjs", "passwd"], cwd=VAULT_CLI, check=True, capture_output=True, env=env)
    type_into("master-password", WEB_MASTER)
    tap("unlock")
    opened = wait("new", timeout=60) is not None and wait(text="в сети", timeout=60) is not None
    report(opened, "устаревшая копия учетной записи: телефон открывается паролем, смененным на другом устройстве")

    # сервер недоступен: в статусе короткая причина и отсчет до повтора, без текста исключения
    tap("menu")
    tap("lock")
    subprocess.run(["podman", "stop", "-t", "1", CONTAINER], check=True, capture_output=True)
    type_into("master-password", WEB_MASTER)
    tap("unlock")
    statuses = []
    seconds_seen = []
    repeated_notice = False
    deadline = time.monotonic() + 25
    while time.monotonic() < deadline:
        root = dump()
        node = find(root, "status")
        text = node.get("text", "").replace("\u00a0", " ") if node is not None else ""
        statuses.append(text)
        if match := re.search(r"повтор через (\d+) с", text):
            seconds_seen.append(int(match.group(1)))
        if any("Exception" in n.get("text", "") for n in root.iter("node")):
            statuses.append("на экране текст исключения")
            break
        # отсчет вырос - была новая попытка; после нее уведомление о той же причине не повторяется
        retried = any(b > a for a, b in zip(seconds_seen, seconds_seen[1:]))
        if retried and any("сервер недоступен" in n.get("text", "") and n is not node for n in root.iter("node")):
            repeated_notice = True
    offline = [s for s in statuses if re.fullmatch(r"сервер недоступен(, повтор через \d+ с)?", s)]
    countdown = {s for s in offline if "повтор" in s}
    report(
        len(countdown) >= 2 and "на экране текст исключения" not in statuses,
        "сервер недоступен: короткая причина и отсчет до повтора в статусе",
        "; ".join(dict.fromkeys(statuses)),
    )
    report(
        any(b > a for a, b in zip(seconds_seen, seconds_seen[1:])) and not repeated_notice,
        "после повторной попытки уведомление об ошибке не повторяется",
    )

    print(f"\nИтог: OK {results.count(True)}, FAIL {results.count(False)}")
    return 0 if all(results) else 1


if __name__ == "__main__":
    sys.exit(main())

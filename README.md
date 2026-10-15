# InkVault для Android

Android-клиент InkVault - менеджера паролей для самостоятельного размещения со сквозным шифрованием и синхронизацией через XMPP-сервер.

Хранилище живет на вашем XMPP-сервере (например, Prosody) в узлах PEP вашей учетной записи. Сервер хранит только шифротекст, мастер-пароль не покидает телефон. Тот же формат используют web-клиент и терминальный клиент, изменения доходят до других устройств за 1-2 секунды.

## Возможности

- Создание хранилища или вход в существующее: JID, пароль XMPP и мастер-пароль.
- Разблокировка мастер-паролем или отпечатком пальца.
- Список с поиском, фильтры "Все", "Избранное", "Корзина" и дерево тегов.
- Записи: название, адрес, логин, пароль, заметка, теги, избранное, история паролей.
- Генератор паролей.
- Пароль вида PIN + одноразовый код (TOTP): готовый пароль считается при показе, с отсчетом до смены кода.
- Работа без сети: хранилище открывается из зашифрованного кэша, изменения отправляются после подключения.
- Резервная копия: зашифрованный экспорт и импорт через системный диалог файлов, файлы совместимы с другими клиентами.
- Смена мастер-пароля, очистка корзины, полное удаление хранилища.
- Восстановление: если сервер потерял данные или вернулся из старого бэкапа, телефон возвращает на сервер учетную запись и записи.

Защита на устройстве:

- окно приложения закрыто для снимков экрана (`FLAG_SECURE`), резервное копирование приложения выключено;
- скопированный пароль помечается как секретный и удаляется из буфера обмена через 45 секунд;
- ключ быстрой разблокировки хранится в Android Keystore и требует отпечаток на каждую операцию;
- пароль XMPP хранится зашифрованным ключом Android Keystore.

Требуется Android 8.0 (API 26) или новее, процессор arm64-v8a или x86_64.

## Установка

Готовые APK - на странице [релизов](https://gitlab.com/inkvault/inkvault-android/-/releases). Ниже - сборка из исходников.

## Требования к серверу

Prosody 13 или другой XMPP-сервер с PEP (XEP-0163), `publish-options` и Stream Management (XEP-0198).

Клиент работает с настройками Prosody по умолчанию: новое хранилище создается с лимитом 256 записей, это `pep_max_items` по умолчанию. В лимит входят и записи в корзине. Для хранилищ больше 256 записей рекомендуется поднять лимит на сервере:

```lua
pep_max_items = 10000
```

После этого лимит узла хранилища поднимается в меню "Еще" - "Лимит записей". Новое значение применяется сразу и действует на всех устройствах: остальные клиенты читают лимит узла с сервера. Лимит больше `pep_max_items` сервер не примет, а меньше числа записей клиент не установит: при уменьшении сервер удалил бы старые записи. Подробности - в [описании протокола](https://gitlab.com/inkvault/inkvault-core/-/blob/main/docs/protocol.md).

## Сборка

Ядро подключено как git submodule `inkvault-core` на теге версии и собирается из исходников. Адрес submodule относительный: если репозиторий клонирован по SSH, ядро тоже загружается по SSH, если по HTTPS - по HTTPS.

Нужны:

- JDK 17 (Gradle 9.7.1 скачивает `./gradlew` при первом запуске, контрольная сумма дистрибутива зафиксирована в `gradle/wrapper/gradle-wrapper.properties`);
- Android SDK с платформой 37, Android NDK 26 (`ANDROID_NDK_HOME`, по умолчанию `~/Android/Sdk/ndk/26.1.10909125`);
- Rust 1.85 или новее, `cargo-ndk` и цели `aarch64-linux-android`, `x86_64-linux-android`.

```sh
git clone --recurse-submodules https://gitlab.com/inkvault/inkvault-android.git
cd inkvault-android

rustup target add aarch64-linux-android x86_64-linux-android
cargo install cargo-ndk

inkvault-core/bindings/build.sh android    # Kotlin-биндинги и библиотеки ядра
./gradlew assembleDebug                    # build/outputs/apk/debug/inkvault-android-debug.apk
adb install build/outputs/apk/debug/inkvault-android-debug.apk
```

Путь к Android SDK задается в `local.properties` (`sdk.dir=...`) или переменной `ANDROID_HOME`.

Обновление ядра до новой версии:

```sh
git -C inkvault-core fetch --tags
git -C inkvault-core checkout v0.2.0
git add inkvault-core
```

## Тесты

Сквозной тест управляет приложением на телефоне по USB через adb и uiautomator: создание хранилища, синхронизация с другим клиентом, PIN + код, экспорт и импорт, восстановление сервера, смена мастер-пароля. Тестовый сервер запускается в podman, телефон подключается к нему через `adb reverse`.

```sh
podman build -t inkvault-test-prosody inkvault-core/testkit/prosody
inkvault-core/bindings/build.sh wasm android
(cd inkvault-core/testkit/vault && npm ci)
./gradlew assembleDebug
tests/run.sh                # с --biometric - еще и разблокировка отпечатком
```

Нужны podman, Node.js 22 и `wasm-bindgen-cli` (см. README ядра). Тест очищает данные приложения на телефоне.

Лицензии зависимостей APK:

```sh
python3 -I tools/licenses.py
```

## Структура

| Путь | Содержимое |
|------|------------|
| `src/main/kotlin/dev/inkov/inkvault/android/` | приложение: экраны на Jetpack Compose, контроллер хранилища, биометрия, буфер обмена |
| `src/main/kotlin/dev/inkov/inkvault/pep/` | адаптер PEP для движка синхронизации ядра на Smack |
| `inkvault-core/` | ядро (git submodule) |
| `gradlew`, `gradle/wrapper/` | Gradle Wrapper |
| `tests/` | сквозной тест на телефоне |
| `art/` | исходники иконки и логотипа в SVG |

## Лицензия

[GNU AGPL v3.0](LICENSE) (`AGPL-3.0-only`). Copyright (C) 2026 Evgeny Inkov.

## Автор

Evgeny Inkov

- Почта: [me@inkov.dev](mailto:me@inkov.dev)
- XMPP: [tzx1z@inkov.dev](xmpp:tzx1z@inkov.dev)
- Сайт: [inkov.dev](https://inkov.dev)

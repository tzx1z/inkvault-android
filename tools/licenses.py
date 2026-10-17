"""Лицензии зависимостей APK и их совместимость с лицензией проекта (AGPL-3.0-only).

Зависимости берутся из Gradle (releaseRuntimeClasspath), лицензии - из POM-файлов кэша Gradle.
Зависимости ядра на Rust проверяет tools/licenses.py в inkvault-core. Код возврата 1 - есть
несовместимая или неизвестная лицензия.

Запуск из корня репозитория: python3 -I tools/licenses.py [--details]
"""


import argparse
import collections
import glob
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
GRADLE_HOME = os.environ.get("GRADLE_USER_HOME") or os.path.expanduser("~/.gradle")
GRADLE_CACHE = os.path.join(GRADLE_HOME, "caches/modules-2/files-2.1")
POM_NS = {"m": "http://maven.apache.org/POM/4.0.0"}

# Лицензии, совместимые с AGPL-3.0: код под ними можно распространять в составе программы под AGPL.
# MPL-2.0 совместима благодаря разделу 3.3, пока автор не пометил код "Incompatible With Secondary
# Licenses"; LGPL-2.1 разрешает перейти на GPL версии 2 или более поздней.
COMPATIBLE = {
    "0BSD", "Apache-2.0", "BSD-2-Clause", "BSD-3-Clause", "BSL-1.0", "CC0-1.0", "ISC", "MIT",
    "MIT-0", "MPL-2.0", "Unicode-3.0", "Unicode-DFS-2016", "Unlicense", "WTFPL", "Zlib",
    "LGPL-2.1-only", "LGPL-2.1-or-later", "LGPL-3.0-only", "LGPL-3.0-or-later",
    "GPL-3.0-only", "GPL-3.0-or-later", "AGPL-3.0-only", "AGPL-3.0-or-later",
}
EXCEPTIONS = {"LLVM-exception"}


def tokens(expression: str) -> list[str]:
    # старая запись "MIT/Apache-2.0" означает выбор одной из лицензий
    return re.findall(r"\(|\)|[^\s()]+", expression.replace("/", " OR "))


def compatible(expression: str) -> bool:
    """Выражение SPDX с OR, AND, WITH и скобками."""
    items = tokens(expression)
    pos = 0

    def parse_or() -> bool:
        nonlocal pos
        result = parse_and()
        while pos < len(items) and items[pos] == "OR":
            pos += 1
            result = parse_and() or result
        return result

    def parse_and() -> bool:
        nonlocal pos
        result = parse_atom()
        while pos < len(items) and items[pos] == "AND":
            pos += 1
            result = parse_atom() and result
        return result

    def parse_atom() -> bool:
        nonlocal pos
        item = items[pos]
        pos += 1
        if item == "(":
            result = parse_or()
            pos += 1
        else:
            result = item in COMPATIBLE
        if pos < len(items) and items[pos] == "WITH":
            result = result and items[pos + 1] in EXCEPTIONS
            pos += 2
        return result

    try:
        return parse_or() and pos == len(items)
    except IndexError:
        return False


def maven_license(name: str, url: str) -> str:
    text = f"{name} {url}".lower()
    rules = [
        (("apache",), "Apache-2.0"),
        (("mit license", "opensource.org/licenses/mit", " mit "), "MIT"),
        (("lgpl-2.1", "lesser general public license, version 2.1", "lgpl, version 2.1", "lgpl 2.1"), "LGPL-2.1-or-later"),
        (("wtfpl",), "WTFPL"),
        (("bsd-3", "bsd 3", "new bsd", "revised bsd"), "BSD-3-Clause"),
        (("bsd-2", "bsd 2", "simplified bsd"), "BSD-2-Clause"),
        (("eclipse public license",), "EPL"),
    ]
    for needles, spdx in rules:
        if any(needle in f" {text} " for needle in needles):
            return spdx
    return f"неизвестна: {name}"


def pom_licenses(group: str, artifact: str, version: str, depth: int = 0) -> list[str]:
    poms = glob.glob(os.path.join(GRADLE_CACHE, group, artifact, version, "*", f"{artifact}-{version}.pom"))
    if not poms or depth > 5:
        return []
    root = ET.parse(poms[0]).getroot()
    licenses = [
        maven_license(item.findtext("m:name", "", POM_NS), item.findtext("m:url", "", POM_NS))
        for item in root.findall("m:licenses/m:license", POM_NS)
    ]
    parent = root.find("m:parent", POM_NS)
    if not licenses and parent is not None:
        get = lambda tag: parent.findtext(f"m:{tag}", "", POM_NS)
        return pom_licenses(get("groupId"), get("artifactId"), get("version"), depth + 1)
    return licenses


def android(gradle: str) -> list[tuple[str, str]]:
    out = subprocess.run(
        [gradle, "-q", "--no-daemon", "dependencies", "--configuration", "releaseRuntimeClasspath"],
        cwd=ROOT, check=True, capture_output=True, text=True,
    ).stdout
    coordinates = set()
    for line in out.splitlines():
        found = re.search(r"--- ([\w.-]+):([\w.-]+):([\w.+-]+)(?: -> ([\w.+-]+))?", line)
        if found:
            group, artifact, version, resolved = found.groups()
            coordinates.add((group, artifact, resolved or version))
    result = []
    for group, artifact, version in sorted(coordinates):
        # ядро - часть проекта под той же лицензией, в его POM лицензии нет, а в релизе на GitHub нет и POM
        if group == "dev.inkov.inkvault":
            result.append((f"{group}:{artifact}:{version}", "AGPL-3.0-only"))
            continue
        # несколько лицензий в POM у этих библиотек - выбор одной из них (JNA, MiniDNS)
        licenses = pom_licenses(group, artifact, version)
        result.append((f"{group}:{artifact}:{version}", " OR ".join(licenses) or "не найдена в POM"))
    return result


def report(title: str, packages: list[tuple[str, str]], details: bool) -> list[tuple[str, str]]:
    counts = collections.Counter(license for _, license in packages)
    print(f"\n{title}: {len(packages)}")
    for license, count in counts.most_common():
        print(f"  {count:4} {license}")
    if details:
        for name, license in sorted(packages):
            print(f"       {name}: {license}")
    return [(name, license) for name, license in packages if not compatible(license)]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--details", action="store_true", help="перечислить все пакеты")
    parser.add_argument("--gradle", default=os.environ.get("GRADLE", os.path.join(ROOT, "gradlew")))
    args = parser.parse_args()

    problems = report("Android: зависимости APK", android(args.gradle), args.details)

    print()
    for name, license in problems:
        print(f"[FAIL] {name}: {license}")
    print(f"Итог: {'есть несовместимые или неизвестные лицензии' if problems else 'все лицензии совместимы с AGPL-3.0-only'}")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())

#!/bin/sh
# Проверка Android-клиента на телефоне по USB. Нужны образ inkvault-test-prosody из
# inkvault-core/testkit/prosody, inkvault-core/bindings/build.sh wasm android, npm ci
# в inkvault-core/testkit/vault и ./gradlew assembleDebug. Ключ --biometric - проверка отпечатка.
set -eu
cd "$(dirname "$0")/.."
ADB=${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb
APK=build/outputs/apk/debug/inkvault-android-debug.apk

# приложение с прошлого прогона ставится заново и очищается до запуска сервера: иначе оставшийся
# процесс переподключится к новому серверу и восстановит на нем свою учетную запись и записи
# потоковая установка на Samsung Galaxy S10+ (Android 12) зависает без ошибки
"$ADB" install --no-streaming -r "$APK" >/dev/null
"$ADB" shell pm clear dev.inkov.inkvault >/dev/null
# сценарий сверяет русские тексты интерфейса: с Android 13 язык задается приложению,
# на более старых версиях нужен русский язык системы
if [ "$("$ADB" shell getprop ro.build.version.sdk | tr -d '\r')" -ge 33 ]; then
    "$ADB" shell cmd locale set-app-locales dev.inkov.inkvault --locales ru-RU
fi

# e2e.py перезапускает и останавливает этот контейнер: восстановление на чистом сервере и сервер без связи
export PROSODY_CONTAINER=inkvault-android-ui
podman rm -f "$PROSODY_CONTAINER" >/dev/null 2>&1 || true
podman run -d --name "$PROSODY_CONTAINER" -p 127.0.0.1:15222:5222 -p 127.0.0.1:15280:5280 inkvault-test-prosody >/dev/null
# под set -e ошибка adb reverse (телефон отключился) завершала бы ловушку раньше podman rm
trap '"$ADB" reverse --remove tcp:15222 >/dev/null 2>&1 || true; podman rm -f "$PROSODY_CONTAINER" >/dev/null 2>&1 || true' EXIT
for _ in $(seq 30); do
    podman logs "$PROSODY_CONTAINER" 2>&1 | grep -q "Activated service 'c2s'" && break
    sleep 0.5
done

"$ADB" reverse tcp:15222 tcp:15222 >/dev/null
# SCENARIO - путь к другому сценарию на этом же стенде
python3 -I "${SCENARIO:-tests/e2e.py}" "$@"

# Android release signing

Файлы для подписи release-сборки. **Не коммитить** keystore и `key.properties`.

## Пути

| Файл | Путь | В git |
|------|------|-------|
| Keystore | `upload-keystore.jks` (корень репо) | нет |
| Пароли и alias | `key.properties` (корень) | нет |
| Шаблон | `key.properties.example` | да |
| Gradle | `app/build.gradle.kts` | да |
| Backup (max 3) | `scripts/build/android-signing-backup_*.zip` | нет |
| SHA-256 (в backup) | `sha256.txt` внутри zip | нет |

`storeFile` в `key.properties` — **относительно** `app/`:

```properties
storeFile=../upload-keystore.jks
```

## Первичная настройка

```bash
keytool -genkey -v -keystore upload-keystore.jks -keyalg RSA \
  -storetype JKS -keysize 2048 -validity 10000 -alias upload
cp key.properties.example key.properties
# Заполнить storePassword и keyPassword
```

Можно скопировать keystore из `demar_vpn/android/` (тот же `com.demar.vpn`).

## Сборка

```bash
./gradlew :app:assembleRelease
# или
./gradlew :app:bundleRelease
```

Без `key.properties` release завершится ошибкой Gradle (debug keystore для release отключён).

## SHA-256 fingerprint

```bash
keytool -list -v -keystore upload-keystore.jks -alias upload
```

Из APK:

```bash
keytool -printcert -jarfile app/build/outputs/apk/release/app-release.apk
```

## Backup

```powershell
.\scripts\backup-android-signing.ps1
```

Zip в `scripts/build/` (max 3 копии). Хранить вне репозитория.

## Восстановление

1. Распаковать `key.properties` и `upload-keystore.jks` в корень репо.
2. Проверить `storeFile=../upload-keystore.jks`.
3. `./gradlew :app:assembleRelease`.

import java.util.Properties

plugins {
    id("com.android.application") version "9.2.1"
    // версия совпадает с Kotlin, встроенным в AGP 9.2.1
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10"
}

// ключ подписи release хранится вне репозитория; без signing.properties release-APK собирается неподписанным
val signing = rootProject.file("signing.properties").takeIf { it.exists() }?.let { file ->
    Properties().apply { file.inputStream().use { load(it) } }
}

fun signingProperty(name: String): String =
    signing?.getProperty(name)?.takeIf { it.isNotBlank() } ?: error("signing.properties: не задан $name")

android {
    namespace = "dev.inkov.inkvault.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.inkov.inkvault"
        minSdk = 26
        targetSdk = 37
        versionCode = 5
        versionName = "0.3.0"
        // ядро собрано под эти ABI, без фильтра JNA добавил бы библиотеки для всех остальных
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    signingConfigs {
        if (signing != null) {
            create("release") {
                storeFile = file(signingProperty("keystore"))
                storePassword = signingProperty("keystore.password")
                keyAlias = signingProperty("keystore.alias")
                // в PKCS12 пароль ключа совпадает с паролем хранилища
                keyPassword = signingProperty("keystore.password")
            }
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.findByName("release")
        }
    }

    // блок зависимостей шифруется ключом Google Play и нужен только ему, APK распространяется вне Google Play
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    androidResources {
        // язык интерфейса - системный; locales_config нужен для выбора языка приложения в настройках Android 13+
        generateLocaleConfig = true
        // переводы библиотек на другие языки не попадают в APK: интерфейс целиком на одном языке
        localeFilters += setOf("en", "ru")
    }

    buildFeatures {
        compose = true
        // версия в диалоге "О программе"
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*")
        }
    }
}

// XmlPull есть в самой платформе Android, копии из xpp3 дают дубли классов
configurations.all {
    exclude(group = "xpp3")
    exclude(group = "org.codelibs", module = "xpp3")
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("org.igniterealtime.smack:smack-android:4.5.0")
    implementation("org.igniterealtime.smack:smack-tcp:4.5.0")
    implementation("org.igniterealtime.smack:smack-extensions:4.5.0")
    implementation("org.igniterealtime.smack:smack-im:4.5.0")
    // QR-код с секретом TOTP распознается на устройстве: ML Kit и сканер из Google Play Services
    // закрытые и не работают на телефонах без сервисов Google
    implementation("androidx.camera:camera-camera2:1.6.2")
    implementation("androidx.camera:camera-lifecycle:1.6.2")
    implementation("androidx.camera:camera-compose:1.6.2")
    implementation("com.google.zxing:core:3.5.4")
    // AAR ядра с Kotlin-биндингами и библиотеками. В релизе ядра на GitHub нет POM, поэтому @aar
    // и JNA отдельно; версия JNA - как в bindings/uniffi/android/build.gradle.kts ядра.
    // AAR-вариант JNA содержит libjnidispatch.so для Android, обычный JAR - только для настольных систем
    implementation("dev.inkov.inkvault:inkvault-core-android:0.2.1@aar")
    implementation("net.java.dev.jna:jna:5.19.1@aar")
}

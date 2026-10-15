plugins {
    id("com.android.application") version "9.2.1"
    // версия совпадает с Kotlin, встроенным в AGP 9.2.1
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10"
}

android {
    namespace = "dev.inkov.inkvault.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.inkov.inkvault"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        // ядро собрано под эти ABI, без фильтра JNA добавил бы библиотеки для всех остальных
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main") {
            // ядро из submodule inkvault-core: Kotlin-биндинги и библиотеки собирает bindings/build.sh android
            kotlin.directories.add("inkvault-core/bindings/uniffi/kotlin")
            jniLibs.directories.add("inkvault-core/bindings/uniffi/jniLibs")
        }
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
    implementation("net.java.dev.jna:jna:5.19.1@aar")
}

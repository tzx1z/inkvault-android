pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        // ядро берется только из реестра пакетов группы inkvault, другие репозитории его не подменят.
        // С -PinkvaultCoreLocal - из mavenLocal: туда ядро из соседнего checkout публикует
        // ./gradlew publishToMavenLocal в inkvault-core/bindings/uniffi/android
        exclusiveContent {
            forRepository {
                if (providers.gradleProperty("inkvaultCoreLocal").isPresent) {
                    mavenLocal()
                } else {
                    maven("https://gitlab.com/api/v4/groups/inkvault/-/packages/maven") {
                        name = "inkvaultCore"
                        // в CI - токен задания, локально - personal access token (read_api) или deploy token
                        // (read_package_registry) в ~/.gradle/gradle.properties: gitlabToken=...
                        // Заголовок зависит от типа токена, deploy token GitLab выдает с префиксом gldt-
                        val jobToken = System.getenv("CI_JOB_TOKEN")
                        val token = jobToken ?: providers.gradleProperty("gitlabToken").orNull
                        credentials(HttpHeaderCredentials::class) {
                            name = when {
                                jobToken != null -> "Job-Token"
                                token?.startsWith("gldt-") == true -> "Deploy-Token"
                                else -> "Private-Token"
                            }
                            value = token
                        }
                        authentication {
                            create<HttpHeaderAuthentication>("header")
                        }
                    }
                }
            }
            filter {
                includeGroup("dev.inkov.inkvault")
            }
        }
    }
}

rootProject.name = "inkvault-android"

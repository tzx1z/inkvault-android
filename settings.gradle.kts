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
        // ядро берется только из одного источника, другие репозитории его не подменят:
        // с -PinkvaultCoreLocal - из mavenLocal, туда ядро из соседнего checkout публикует
        // ./gradlew publishToMavenLocal в inkvault-core/bindings/uniffi/android;
        // с токеном GitLab - из закрытого реестра пакетов группы inkvault, без токена - из релиза на GitHub
        exclusiveContent {
            forRepository {
                // в CI - токен задания, локально - personal access token (read_api) или deploy token
                // (read_package_registry) в ~/.gradle/gradle.properties: gitlabToken=...
                val jobToken = System.getenv("CI_JOB_TOKEN")
                val token = jobToken ?: providers.gradleProperty("gitlabToken").orNull
                when {
                    providers.gradleProperty("inkvaultCoreLocal").isPresent -> mavenLocal()
                    token != null -> maven("https://gitlab.com/api/v4/groups/inkvault/-/packages/maven") {
                        name = "inkvaultCore"
                        // заголовок зависит от типа токена, deploy token GitLab выдает с префиксом gldt-
                        credentials(HttpHeaderCredentials::class) {
                            name = when {
                                jobToken != null -> "Job-Token"
                                token.startsWith("gldt-") -> "Deploy-Token"
                                else -> "Private-Token"
                            }
                            value = token
                        }
                        authentication {
                            create<HttpHeaderAuthentication>("header")
                        }
                    }
                    // в релизе только AAR без POM, поэтому JNA объявлена в build.gradle.kts явно.
                    // Файл тот же, что CI ядра публикует в реестр GitLab
                    else -> ivy("https://github.com/tzx1z/inkvault-core/releases/download") {
                        name = "inkvaultCoreGithub"
                        patternLayout {
                            artifact("v[revision]/[module]-[revision].[ext]")
                        }
                        metadataSources {
                            artifact()
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

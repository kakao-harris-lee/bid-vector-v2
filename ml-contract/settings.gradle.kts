pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    // included build 는 루트의 카탈로그를 자동으로 보지 못한다(`build-logic/settings.gradle.kts`와
    // 같은 배선, D-2A-0 (c)) — 버전 리터럴의 정본은 `gradle/libs.versions.toml` 하나뿐이다.
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "ml-contract"

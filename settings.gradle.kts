pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    // 카탈로그는 settings 의 plugins 블록에서 아직 보이지 않으므로 이 한 좌표만 리터럴이다.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}

rootProject.name = "bid-vector"

// v2-지침서.md §3.1 · ADR 0006 D-2 의 모듈 목록 중 아홉.
// `bidding` 은 소유 capability 가 서지 않아 보류한다 — scope.md D-2 와 capability-map.md §12.
include(
    "shared-kernel",
    "procurement",
    "qualification",
    "strategy",
    "decision",
    "settlement",
    "workflow",
    "adapters",
    "app",
)

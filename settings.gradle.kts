pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

// M2/2A — `.proto` 생성 전용 included build(subproject 가 아니다, D-2A-0 (c)). `include(...)`
// 목록(아래, subprojects)에는 넣지 않는다 — 1A 게이트 가족이 subproject 안의 생성물을 거부하기
// 때문이다(`reports/evidence/m2/2a/scope.md`).
includeBuild("ml-contract")

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

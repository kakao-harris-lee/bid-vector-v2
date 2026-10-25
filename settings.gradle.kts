pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

// `.proto` 생성 전용 included build(subproject 가 아니다, D-2A-0 (c)). `include(...)`
// 목록(아래, subprojects)에는 넣지 않는다 — 게이트 가족이 subproject 안의 생성물을 거부하기
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

// v2-지침서.md §3.1 · ADR 0006 D-2 의 모듈 목록 **열 전부**.
// `bidding` 은 M7/7B 가 되살렸다 — D-2.1 의 되살리는 조건(`OPEN-ADR-14` (c) 가 답이 되어 소유
// capability 가 등재되고 그 분류를 받는 것)이 `BID-01 · 입찰 건의 상태와 전이`(`V2 필수`)로
// 충족됐다(운영자 결정 2026-09-07 Q-7 — capability-map.md §0.6·§10).
include(
    "shared-kernel",
    "procurement",
    "qualification",
    "strategy",
    "bidding",
    "decision",
    "settlement",
    "workflow",
    "adapters",
    "app",
)

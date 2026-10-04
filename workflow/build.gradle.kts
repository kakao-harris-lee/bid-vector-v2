import org.gradle.api.tasks.PathSensitivity

plugins {
    id("bidvector.kotlin-conventions")
}

// ADR 0006 D-4 — 업무 모듈 교차는 workflow 가 조합한다. 의존 **선언**이 방향의 1차 강제이므로
// 코드가 들어오기 전에도 방향을 선언해 둔다(Gradle 이 순환을 task graph 를 짜며 거부한다).
dependencies {
    implementation(project(":shared-kernel"))
    implementation(project(":procurement"))
    implementation(project(":qualification"))
    implementation(project(":strategy"))
    implementation(project(":decision"))
    implementation(project(":settlement"))
    // kotest-property 의 checkAll 은 suspend 함수이고 kotest-property-jvm 은
    // coroutines-core 를 runtime scope 로만 선언해 컴파일 classpath 에 전이되지 않으므로
    // 이 모듈이 직접 건다(strategy/qualification/decision 과 같은 이유).
    testImplementation(libs.kotlinx.coroutines.core)
}

// kotest-property 시드 고정(strategy 등 관례). 값은 임의 상수, 반복 실행 안정성만
// 목적이고 도메인 의미는 없다.
tasks.withType<Test>().configureEach {
    systemProperty("kotest.proptest.default.seed", "20260908")

    // `SampleSelectionTest`(D-6G2c-19 (b))도 같은 자리다 — 용도 구분자의 정본은 스냅숏 스키마
    // 계약 문서이고 그 test 가 **문서에서 읽어** 코드와 맞댄다. 선언이 없으면 문서만 바꾼 편집이
    // task 를 UP-TO-DATE 로 건너뛰어 등식이 꺼진 채 초록이 된다(실측: 용도 토큰 변경 뒤 재실행 0).
    // 루트가 빌드 캐시를 켜 두므로 CI 도 같다 — 안 돌린 게이트는 아무것도 막지 못한다.
    inputs
        .file(rootProject.file("reports/evidence/m6/6g/snapshot-schema.md"))
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

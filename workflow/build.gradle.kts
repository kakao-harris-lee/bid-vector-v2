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
    // M4/4A — kotest-property 의 checkAll 은 suspend 함수이고 kotest-property-jvm 은
    // coroutines-core 를 runtime scope 로만 선언해 컴파일 classpath 에 전이되지 않으므로
    // 이 모듈이 직접 건다(strategy/qualification/decision 과 같은 이유).
    testImplementation(libs.kotlinx.coroutines.core)
}

// M4/4A — kotest-property 시드 고정(strategy 등 관례). 값은 임의 상수, 반복 실행 안정성만
// 목적이고 도메인 의미는 없다.
tasks.withType<Test>().configureEach {
    systemProperty("kotest.proptest.default.seed", "20260908")
}

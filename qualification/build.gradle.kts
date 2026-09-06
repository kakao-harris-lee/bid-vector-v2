plugins {
    id("bidvector.kotlin-conventions")
}

// M1/1C — qualification 은 domain 층이라 shared-kernel 하나만 참조할 수 있다(ADR 0006 D-4,
// architecture-policy.properties layer.domain.shareable). PolicyVersion·EffectiveDatedPolicy
// 재사용(decision 23)에 필요하다.
//
// kotest-property 의 checkAll 은 suspend 함수다 — shared-kernel 과 같은 이유로 이 모듈도
// coroutines-core 를 직접 건다(kotest-property-jvm 은 runtime scope 로만 선언해 컴파일
// classpath 에 전이되지 않는다).
dependencies {
    implementation(project(":shared-kernel"))
    testImplementation(libs.kotlinx.coroutines.core)
}

// kotest-property 시드 고정(shared-kernel 관례) — 이 모듈도 kotest 의 JUnit5 러너를 붙이지
// 않으므로(순수 junit-jupiter + kotest-property) AbstractProjectConfig 자동탐지가 적용되지
// 않는다. 값은 임의 상수 — 반복 실행 안정성만 목적이고 도메인 의미는 없다.
tasks.withType<Test>().configureEach {
    systemProperty("kotest.proptest.default.seed", "20260906")
}

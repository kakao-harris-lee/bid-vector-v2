plugins {
    id("bidvector.kotlin-conventions")
}

// M3/3A — procurement 는 domain 층이라 shared-kernel 하나만 참조할 수 있다(ADR 0006 D-4,
// architecture-policy.properties layer.domain.shareable). decision/qualification 과 같은
// 배선이다.
//
// kotest-property 의 checkAll 은 suspend 함수다 — decision/qualification 과 같은 이유로
// coroutines-core 를 직접 건다.
dependencies {
    implementation(project(":shared-kernel"))
    testImplementation(libs.kotlinx.coroutines.core)
}

// kotest-property 시드 고정(shared-kernel·qualification·decision 관례) — 값은 임의 상수,
// 반복 실행 안정성만 목적이고 도메인 의미는 없다.
tasks.withType<Test>().configureEach {
    systemProperty("kotest.proptest.default.seed", "20260907")
}

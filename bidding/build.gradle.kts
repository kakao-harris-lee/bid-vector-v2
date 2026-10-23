plugins {
    id("bidvector.kotlin-conventions")
}

// bidding 은 domain 층이라 shared-kernel 하나만 참조할 수 있다(ADR 0006 D-4,
// architecture-policy.properties layer.domain.shareable). Currency·VatTreatment·NoticeRound 와
// EffectiveDatedPolicy 재사용에 필요하다. qualification/strategy 의 build.gradle.kts 를
// 그대로 옮긴다 — 배선은 복제로 충분하다.
//
// kotest-property 의 checkAll 은 suspend 함수이고 kotest-property-jvm 은 coroutines-core 를
// runtime scope 로만 선언해 컴파일 classpath 에 전이되지 않으므로 이 모듈이 직접 건다
// (qualification·strategy 와 같은 이유). 전이표의 **모양**은 전수 열거 test 가 182 쌍(14 × 13)으로
// 골든과 맞대고, 판정 함수의 **불변식**은 `BidTransitionPropertyTest` 가 property 로 잰다.
dependencies {
    implementation(project(":shared-kernel"))
    testImplementation(libs.kotlinx.coroutines.core)
}

// kotest-property 시드 고정(shared-kernel·qualification·strategy 관례) — 값은 임의 상수,
// 반복 실행 안정성만 목적이고 도메인 의미는 없다.
tasks.withType<Test>().configureEach {
    systemProperty("kotest.proptest.default.seed", "20261021")
}

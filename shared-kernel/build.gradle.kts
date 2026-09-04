plugins {
    id("bidvector.kotlin-conventions")
}

// kotest-property 의 checkAll/forAll 은 suspend 함수다 — 평범한 Jupiter @Test 안에서 부르려면
// coroutines-core 의 runBlocking 이 필요하다. kotest-property-jvm 은 coroutines-core 를
// runtime scope 로만 선언해(jvmRuntimeElements) 컴파일 classpath 에 전이되지 않으므로 이
// 모듈이 testImplementation 으로 직접 건다 — 카탈로그 좌표는 이미 build-logic 이 쓰던 것과
// 같다(gradle/libs.versions.toml 의 kotlinx-coroutines-core).
// 컴파일 실패 하네스(설계 검토 §4.9)가 kotlin-compiler-embeddable 을 test 안에서 구동해
// 「상호 대입이 컴파일되지 않는다」(ADR 0002 §6 ①)를 기계로 증명한다. 좌표는 build-logic 이
// 이미 쓰는 것과 같다(gradle/libs.versions.toml 의 kotlin-compilerEmbeddable) — 로컬·리뷰
// 레인 RO 캐시 양쪽에 2.4.10 이 있다(설계 검토 §4.9 표 실측).
dependencies {
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlin.compilerEmbeddable)
}

// kotest-property 시드 고정(verifier r1 L-6) — 이 모듈은 kotest 의 JUnit5 러너를 붙이지
// 않으므로(순수 junit-jupiter + kotest-property, 위 dependencies 주석 참고)
// `AbstractProjectConfig` 자동탐지가 적용되지 않는다. 시스템 property
// `kotest.proptest.default.seed` 가 시드를 거는 유일한 전역 지점이다 — 미고정 시 property
// 실패의 재현이 그 실행에서 콘솔에 찍힌 시드 값에만 의존해 재현성이 없다. `build-logic/**`
// 은 1B in_scope 밖이라(`scope.md`) 공용 convention plugin 이 아니라 이 모듈 자신에 건다 —
// checkAll 을 쓰는 test 클래스가 전부 이 모듈 안에 있다(`ArithmeticTest`·`MoneyTest`·
// `PolicyTest`·`RateTest`). 값은 임의 상수다 — 반복 실행 안정성(같은 시드 = 같은 케이스
// 순서)만 목적이고 도메인 의미는 없다.
tasks.withType<Test>().configureEach {
    systemProperty("kotest.proptest.default.seed", "20260904")
}

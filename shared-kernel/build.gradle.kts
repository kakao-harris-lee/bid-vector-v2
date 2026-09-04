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

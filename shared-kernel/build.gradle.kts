plugins {
    id("bidvector.kotlin-conventions")
}

// kotest-property 의 checkAll/forAll 은 suspend 함수다 — 평범한 Jupiter @Test 안에서 부르려면
// coroutines-core 의 runBlocking 이 필요하다. kotest-property-jvm 은 coroutines-core 를
// runtime scope 로만 선언해(jvmRuntimeElements) 컴파일 classpath 에 전이되지 않으므로 이
// 모듈이 testImplementation 으로 직접 건다 — 카탈로그 좌표는 이미 build-logic 이 쓰던 것과
// 같다(gradle/libs.versions.toml 의 kotlinx-coroutines-core).
dependencies {
    testImplementation(libs.kotlinx.coroutines.core)
}

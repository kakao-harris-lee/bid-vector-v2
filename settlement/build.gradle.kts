plugins {
    id("bidvector.kotlin-conventions")
}

// settlement 는 domain 층이라 shared-kernel 하나만 참조할 수 있다(ADR 0006 D-4,
// architecture-policy.properties layer.domain.shareable). strategy/decision/qualification 의
// build.gradle.kts 를 그대로 옮긴다 — shared-kernel 은 정책 version 축
// (`EffectiveDatedPolicy`·`EffectiveFrom`)을 준다.
//
// 컴파일 실패 하네스(1B `CompileFailureHarnessTest` 관례, scope.md 설계 검토 (2))가
// kotlin-compiler-embeddable 을 test 안에서 구동해 ① 판정 함수에 금액 인자를 더한 호출과
// ② 모듈 밖에서의 `NormalizedCompanyName` 직접 생성이 컴파일되지 않음을 기계로 증명한다.
// 좌표는 shared-kernel·strategy 가 이미 쓰는 것과 같다.
dependencies {
    implementation(project(":shared-kernel"))
    testImplementation(libs.kotlin.compilerEmbeddable)
}

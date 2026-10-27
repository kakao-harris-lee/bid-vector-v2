plugins {
    id("bidvector.kotlin-conventions")
}

// decision 은 domain 층이라 shared-kernel 하나만 참조할 수 있다(ADR 0006 D-4,
// architecture-policy.properties layer.domain.shareable). qualification/build.gradle.kts
// 와 같은 배선이다(scout §4.4 — 배관을 그대로 옮길 수 있다).
//
// kotest-property 의 checkAll 은 suspend 함수다 — qualification 과 같은 이유로 이 모듈도
// coroutines-core 를 직접 건다(kotest-property-jvm 은 runtime scope 로만 선언해 컴파일
// classpath 에 전이되지 않는다).
//
// 컴파일 실패 하네스(`menu.MenuCompileFailureHarnessTest`, M6/G1-DEC05)가
// kotlin-compiler-embeddable 을 test 안에서 구동해 **메뉴 옵션·정상 3안을 모듈 밖에서 지을 수
// 없음**을 기계로 증명한다 — 같은 모듈 test 는 `internal` 생성자를 그냥 부를 수 있어 그
// 폐쇄를 재지 못한다. 좌표는 shared-kernel·strategy 가 이미 쓰는 것과 같다.
dependencies {
    implementation(project(":shared-kernel"))
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlin.compilerEmbeddable)
}

// kotest-property 시드 고정(shared-kernel·qualification 관례) — 값은 임의 상수, 반복 실행
// 안정성만 목적이고 도메인 의미는 없다.
tasks.withType<Test>().configureEach {
    systemProperty("kotest.proptest.default.seed", "20260906")
}

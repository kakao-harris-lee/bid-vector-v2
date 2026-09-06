plugins {
    id("bidvector.kotlin-conventions")
}

// M1/1E — strategy 는 domain 층이라 shared-kernel 하나만 참조할 수 있다(ADR 0006 D-4,
// architecture-policy.properties layer.domain.shareable). qualification/decision의
// build.gradle.kts 를 그대로 옮긴다(scout §7.1 — 배선은 1C·1D 복제로 충분하다).
//
// kotest-property 의 checkAll 은 suspend 함수이고 kotest-property-jvm 은 coroutines-core 를
// runtime scope 로만 선언해 컴파일 classpath 에 전이되지 않으므로 이 모듈이 직접 건다
// (qualification·decision 과 같은 이유).
//
// 컴파일 실패 하네스(1B `CompileFailureHarnessTest` 관례, scope.md 위협 모델 (c)(d))가
// kotlin-compiler-embeddable 을 test 안에서 구동해 basis 교차·텍스트 범위 교차가
// 컴파일되지 않음을 기계로 증명한다. 좌표는 shared-kernel 이 이미 쓰는 것과 같다.
dependencies {
    implementation(project(":shared-kernel"))
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlin.compilerEmbeddable)
}

// kotest-property 시드 고정(shared-kernel·qualification·decision 관례) — 값은 임의 상수,
// 반복 실행 안정성만 목적이고 도메인 의미는 없다.
tasks.withType<Test>().configureEach {
    systemProperty("kotest.proptest.default.seed", "20260906")
}

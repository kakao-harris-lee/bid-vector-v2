plugins {
    id("bidvector.kotlin-conventions")
}

dependencies {
    implementation(project(":workflow"))
    implementation(project(":shared-kernel"))

    // M2/2A — round-trip test 가 ml-contract 의 생성 stub 을 본다. composite 치환(같은
    // 좌표를 `settings.gradle.kts`의 `includeBuild("ml-contract")`가 잇는다) — main 의존은
    // M4 4D(도메인 ↔ 계약 매핑·client 배선)까지 미룬다.
    testImplementation("bidvector:ml-contract")
    testImplementation(platform(libs.grpc.bom))
    testImplementation(libs.grpc.kotlin.stub)
    testImplementation(libs.grpc.stub)
    testImplementation(libs.kotlinx.coroutines.core)
    // §4b 런타임 스모크(GrpcKotlinStackSmokeTest) 전용 — in-process 채널/서버 구성에
    // `io.grpc:grpc-core`가 필요하다(`grpc-stub`/`grpc-kotlin-stub`은 그것을 끌어오지 않고,
    // `grpc-testing`도 전이하지 않음을 실측). 조사 노트 02 가 권고한 in-process 대역
    // (2D 의 fake servicer consumer/provider test 도 재사용할 후보).
    testImplementation(libs.grpc.testing)
    testImplementation(libs.grpc.core)
    testImplementation(libs.grpc.inprocess)
}

// M2/2A — `ContractRoundTripTest`가 `contracts/testdata/*.binpb`(canonical, VCS 커밋)를 읽는다.
// 다른 게이트 test 의 System.getProperty 주입 관례(app/build.gradle.kts)와 같은 형태.
tasks.test {
    val contractsTestdata = layout.settingsDirectory.dir("contracts/testdata")
    inputs.dir(contractsTestdata).withPropertyName("contractsTestdata")
    systemProperty("bidvector.contracts.testdata", contractsTestdata.asFile.absolutePath)
}

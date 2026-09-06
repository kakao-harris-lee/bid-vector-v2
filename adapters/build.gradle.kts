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
}

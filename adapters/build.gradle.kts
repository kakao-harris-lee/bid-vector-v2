import bidvector.buildlogic.PresentSpec

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
    // M2/2D ⑤ — `ContractMaxPayloadTest` 전용. in-process 전송은 메시지를 marshaling 없이
    // 참조로 넘겨 `maxInboundMessageSize`를 강제하지 않는다(실측) — 경계 쌍(D-2D-6)의 실제
    // 강제를 재려면 localhost 소켓의 진짜 프레이밍 경로가 필요하다.
    testImplementation(libs.grpc.netty.shaded)
}

// M2/2A — `ContractRoundTripTest`가 `contracts/testdata/*.binpb`(canonical, VCS 커밋)를 읽는다.
// 다른 게이트 test 의 System.getProperty 주입 관례(app/build.gradle.kts)와 같은 형태.
// M2/2D — `max.message.bytes`·`retry.sample.max-attempts` 같은 정책 값도 같은 관례로 넘긴다
// (경로가 아니라 정책 **파일**을 넘기고, 파싱은 test 쪽 `ContractPolicySupport.kt` 가 한다 —
// build-logic 의 `internal ContractPolicy`는 다른 모듈의 build.gradle.kts 에서 보이지 않는다).
tasks.test {
    val contractsTestdata = layout.settingsDirectory.dir("contracts/testdata")
    val contractPolicy = layout.settingsDirectory.file("config/quality/contract-policy.properties")
    inputs.dir(contractsTestdata).withPropertyName("contractsTestdata")
    inputs.file(contractPolicy).withPropertyName("contractPolicy")
    systemProperty("bidvector.contracts.testdata", contractsTestdata.asFile.absolutePath)
    systemProperty("bidvector.contracts.policy", contractPolicy.asFile.absolutePath)
    // M2/2D S-6, D-2D-3 (a) — 교차 언어 socket 스모크는 상시 게이트가 아니다. 일반 `test`
    // (따라서 `check`)는 이 class 를 이름으로 제외한다 — Python 서버 없이 도는 보통의
    // 실행에서 연결 실패로 죽지 않게 한다. 실행은 `crossLangSmokeTest`(아래)만 한다.
    filter { excludeTestsMatching("*CrossLangSmokeTest") }
}

// M2/2D S-6 — `tools/contract-crosslang-smoke.sh`가 Python 서버를 띄운 뒤 이 task 만 골라
// 돈다(로컬 실측 1회, D-2D-3 (a)). 같은 test 소스셋을 재사용하되 이 class 하나만 포함한다.
// **실측(2026-09-07)** — Gradle 은 `Test` 타입 task 를 `group`·이름과 무관하게 `check`의
// 의존 그래프에 자동으로 엮는다(`./gradlew :adapters:check --dry-run` 로 확인). `dependsOn`
// 을 직접 걷어내는 것은 base plugin 배선 순서에 기대는 취약한 우회라, 대신 **task 자신이
// 전제 부재를 스스로 건너뛴다** — `bidvector.crosslang.address` 프로퍼티가 없으면(보통의
// `check`) `onlyIf` 가 이 task 를 SKIPPED 로 낸다. 이 프로퍼티는 스크립트만 명시적으로
// 넘긴다 — D-2D-3 (a)의 "상시 게이트가 아니다"를 실행 결과가 아니라 실행 여부로 만족한다.
// config cache 안전을 위해 `onlyIf { ... }` 스크립트 람다 대신 컴파일된 `PresentSpec`
// (build-logic)을 쓴다 — 스크립트 closure 는 지역 값만 참조해도 그 람다가 사는 스크립트
// 클래스 자체를 붙들어 "cannot (de)serialize Gradle script object references"로 config
// cache 저장이 깨진다(실측, M2/2D). 값 자체는 구성 시점의 평범한 Boolean/String 이다.
val crossLangAddressPresent = project.hasProperty("bidvector.crosslang.address")
val crossLangAddressValue = project.findProperty("bidvector.crosslang.address") as String? ?: "127.0.0.1:50099"
val crossLangSmokeTest =
    tasks.register<Test>("crossLangSmokeTest") {
        group = "verification (manual)"
        description = "M2/2D S-6 — 교차 언어 socket 스모크(Python 서버 필요, check 밖, 수동 실행 전용)"
        testClassesDirs = sourceSets["test"].output.classesDirs
        classpath = sourceSets["test"].runtimeClasspath
        useJUnitPlatform()
        filter { includeTestsMatching("*CrossLangSmokeTest") }
        onlyIf(PresentSpec(crossLangAddressPresent))
        systemProperty("bidvector.crosslang.address", crossLangAddressValue)
        val crossLangTestdata = layout.settingsDirectory.dir("contracts/testdata")
        systemProperty("bidvector.contracts.testdata", crossLangTestdata.asFile.absolutePath)
    }

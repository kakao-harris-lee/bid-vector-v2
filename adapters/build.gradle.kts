import bidvector.buildlogic.PresentSpec

plugins {
    id("bidvector.kotlin-conventions")
}

dependencies {
    implementation(project(":workflow"))
    implementation(project(":shared-kernel"))
    // M3/3B — `KonepsOpenApiNoticeSource : NoticeSourcePort` 구현이 3A 의 도메인 타입
    // (`RawNoticeObservation`·`CollectionAccounting`·`KONEPS_COLLECTION_POLICY` 등)을 쓴다.
    // procurement 는 domain 층이라 adapters(그 위 층)의 허용 project 의존이다.
    implementation(project(":procurement"))
    // M3/3C — `WatchGatedExtractor`가 1E `WatchVerdict`를(게이트 인자), `ExtractionToQualification`
    // 이 1C `RequirementRow`/`RequirementCollection`을 쓴다(D-3C-3 1C 변환, D-3C-6 게이트).
    // adapters 는 domain 층이 아니라 이 둘을 함께 참조할 수 있다(ModuleDependencyGate S-5).
    implementation(project(":qualification"))
    implementation(project(":strategy"))
    // M4/4D-1 — `GrpcBidPredictionGateway`가 `BidPredictionOutcome.Unavailable`을 짓는 데
    // `bidvector.decision.MlUnavailableReason`을 직접 참조한다(D-4D-6). `workflow`는 이 좌표를
    // `implementation`으로만 선언해 하위 소비자(이 모듈)의 컴파일 classpath 에 전이되지
    // 않는다 — adapters 는 그 자신이 domain 층 아래(모듈 의존 방향 D-3)이므로 이 project 를
    // 직접 선언하는 것이 허용 범위 안이다(architecture-policy.properties layer.domain).
    implementation(project(":decision"))
    // M3/3B ② — quota·bounded retry/backoff·rate limiter Resilience4j **한 계층**(ADR 0005
    // D-11). `resilience4j-kotlin`은 카탈로그에만 두고 여기서 끌어오지 않는다(D-3B-2,
    // gradle/libs.versions.toml 주석 — Java API로 충분해 불필요한 결합을 늘리지 않는다).
    implementation(libs.resilience4j.retry)
    implementation(libs.resilience4j.ratelimiter)
    // M3/3C ⑤ — LLM 호출의 TimeLimiter+CircuitBreaker 한 계층(ADR 0005 D-11).
    implementation(libs.resilience4j.circuitbreaker)
    implementation(libs.resilience4j.timelimiter)
    // M3/3C ④ D-3C-2 — JSON Schema(Draft 2020-12) 검증(운영자 승인). Jackson 은 이 좌표의
    // 전이 의존으로만 쓴다(별도 선언 없음, libs.versions.toml 주석).
    implementation(libs.json.schema.validator)
    // M3/3C D-3C-4 (a) — PDF 텍스트 층 추출 한 의존(운영자 승인).
    implementation(libs.pdfbox)

    // M3/3D — D-3D-1 (a) JDBC 직접 + Flyway(ADR 0004 D-1·D-2). `flyway-core`·`postgresql-driver`
    // 는 카탈로그에 버전이 없다 — Boot BOM 이 관리한다(app 의 관례와 같다, `app/build.gradle.kts`).
    // HikariCP 는 넣지 않는다(측정된 필요 없음, 3D 설계 검토 「구현 지침」).
    implementation(platform(libs.spring.boot.bom))
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.postgresql.driver)
}

// M3/3C — sizeGate 의 함수 50줄 축은 `.kts` 람다도 잰다(size-policy.properties). 위
// `dependencies {}` 가 3C 추가로 그 상한에 닿아, 관련 없는 두 번째 배선(M2/2A gRPC 계약
// 셋)을 별도 블록으로 나눈다 — Gradle 은 같은 스크립트 안 `dependencies {}` 를 여러 번
// 받아 누적 적용한다(내용 변경 없음, 크기 축 회피만).
dependencies {
    // M2/2A — round-trip test 가 ml-contract 의 생성 stub 을 본다. composite 치환(같은
    // 좌표를 `settings.gradle.kts`의 `includeBuild("ml-contract")`가 잇는다). M4/4D-1 —
    // main 의존을 연다(`GrpcBidPredictionGateway`가 실 coroutine stub 을 배선한다, 2A
    // 주석이 예고했던 「도메인 ↔ 계약 매핑·client 배선」 시점). `platform(libs.grpc.bom)`도
    // `implementation`으로 옮겨 main 컴파일 classpath 의 `io.grpc:*` 좌표를 정렬한다 —
    // `testImplementation`은 `implementation`을 상속하므로(Gradle Java 플러그인 기본) test
    // 쪽에 다시 적을 필요가 없다(중복 금지).
    implementation("bidvector:ml-contract")
    implementation(platform(libs.grpc.bom))
    implementation(libs.grpc.kotlin.stub)
    implementation(libs.grpc.stub)
    implementation(libs.grpc.core)
    implementation(libs.kotlinx.coroutines.core)
    // §4b 런타임 스모크(GrpcKotlinStackSmokeTest) 전용 — in-process 채널/서버 구성에
    // `io.grpc:grpc-inprocess`가 필요하다(`grpc-stub`/`grpc-kotlin-stub`은 그것을 끌어오지
    // 않고, `grpc-testing`도 전이하지 않음을 실측). 조사 노트 02 가 권고한 in-process 대역
    // (2D 의 fake servicer consumer/provider test 도 재사용할 후보 — M4/4D-1 `adapters.ml`
    // test 도 같은 대역을 쓴다).
    testImplementation(libs.grpc.testing)
    testImplementation(libs.grpc.inprocess)
    // M2/2D ⑤ — `ContractMaxPayloadTest` 전용. in-process 전송은 메시지를 marshaling 없이
    // 참조로 넘겨 `maxInboundMessageSize`를 강제하지 않는다(실측) — 경계 쌍(D-2D-6)의 실제
    // 강제를 재려면 localhost 소켓의 진짜 프레이밍 경로가 필요하다.
    testImplementation(libs.grpc.netty.shaded)

    // M3/3D S-2~S-5 — Testcontainers PostgreSQL 통합 test(ADR 0004 D-1). BOM 은 main 쪽에서
    // 이미 platform 으로 얹었으므로 test 는 모듈 좌표만 더한다.
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
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

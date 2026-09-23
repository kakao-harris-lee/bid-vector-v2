import bidvector.buildlogic.CompatibilitySmokeTask
import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    id("bidvector.kotlin-conventions")
    alias(libs.plugins.spring.boot)
}

// M6/6A-1 D-6A1-4 — 이 slice가 처음으로 진입점(main())·controller를 만든다. bootJar를
// 켠다(1A가 disabled로 남겨 둔 자리). **D-6A1-28 정정** — 평범한 jar는 끄지 않는다. 이전
// 판은 「파일명 충돌」을 근거로 껐으나 실측과 다르다: Boot Gradle 플러그인은 둘 다 켜져
// 있으면 plain jar에 `-plain` classifier를 자동으로 붙여(`app-plain.jar`) `bootJar`
// (`app.jar`)와 공존시킨다. `jar`를 끄면 `jarContentGate`(하드코딩된 `jar` task 산출물
// 대조)가 빈 아카이브를 보게 되어 게이트가 아무것도 검증하지 않는 채로 초록이 된다
// (verifier 실측 — entries 1→0). 배포물은 `bootJar`이지만 게이트는 `jar`만 본다는
// 사실은 바뀌지 않으므로(build-logic 하드코딩), `jar`를 켜 두는 것이 이 게이트가 실제로
// 뭔가를 재게 하는 유일한 방법이다.
tasks.named<BootJar>("bootJar") { enabled = true }
tasks.named<Jar>("jar") { enabled = true }

dependencies {
    implementation(project(":adapters"))
    implementation(project(":workflow"))
    // M6/6A-1 — `StrategyReadController`·`PersistenceWiring`이 `OperatorStrategy`·
    // `STRATEGY_POLICY`(strategy)·`Provenance`·`Resolution`·`Money.export()`(shared-kernel)를
    // 직접 참조한다. `adapters`·`workflow`가 이 둘을 `implementation`(비전이)으로만 물어
    // app의 main compile classpath에 원래 없었다(1B-c의 test 전용 배선과 같은 이유,
    // 다만 이번은 main — 이 slice가 처음으로 controller/조립에서 도메인 타입을 직접
    // 다룬다는 사실 자체가 이 선언을 요구한다).
    implementation(project(":strategy"))
    implementation(project(":shared-kernel"))

    implementation(platform(libs.spring.boot.bom))
    implementation(libs.spring.boot.starter)
    // M6/6A-1 — HTTP 골격의 유일한 신규 production 좌표(D-6A1-16, scope.md in_scope).
    implementation(libs.spring.boot.starter.web)
    implementation(libs.db.scheduler)
    implementation(libs.resilience4j.retry)
    implementation(libs.micrometer.core)
    implementation(libs.flyway.core)
    // M6/6A-1 — `PersistenceWiring`이 production DataSource·migration을 처음 배선한다
    // (adapters의 관례와 같은 좌표, `implementation`은 전이되지 않아 app이 다시 선언해야
    // 한다). D-6A1-18 — HikariCP는 들이지 않는다(`OPEN-6A1-CONNECTION-POOL`).
    implementation(libs.flyway.database.postgresql)
    implementation(libs.postgresql.driver)

    testImplementation(platform(libs.spring.boot.bom))
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.archunit.junit6)
    // M6/6A-1 — D-6A1-21 디스패치 실측(실 임베디드 서버, webEnvironment=RANDOM_PORT)에
    // 필요한 test 전용 신규 좌표(scope.md 문면 밖 최소 추가 — MockMvc 대신 이 둘만으로
    // `@SpringBootTest`+`TestRestTemplate`를 쓴다, evidence에 사유 등재).
    testImplementation(libs.spring.boot.test)
    testImplementation(libs.spring.boot.resttestclient)
    testImplementation(libs.spring.boot.restclient)
}

// M6/6A-1 — sizeGate 의 함수 50줄 축은 `.kts` 람다도 잰다(size-policy.properties, `adapters
// /build.gradle.kts` 주석과 같은 이유). 위 `dependencies {}` 가 이 slice의 추가로 그 상한에
// 닿아, 관련 없는 나머지 배선(corpus 소비 test 전용 project 의존)을 별도 블록으로 나눈다
// — Gradle 은 같은 스크립트 안 `dependencies {}` 를 여러 번 받아 누적 적용한다(내용 변경
// 없음, 크기 축 회피만).
dependencies {
    // 위반 fixture 전용 — 금지 가족을 실제로 컴파일해야 게이트가 그것을 잡는지 잴 수 있다.
    testImplementation(libs.jakarta.persistence)
    testImplementation(libs.jakarta.jms)
    testImplementation(libs.jackson.databind)
    testImplementation(libs.grpc.api)
    testImplementation(libs.protobuf.java)

    // M1/1B-c ④(decision 22, D5(d)) — corpus 소비 테스트(conformance runner). shared-kernel
    // 공개 API 만으로 값을 대조한다(testFixtures 는 하네스 게이트 셋을 깨 쓰지 않는다 —
    // `scope.md` 「Phase 3 중 계약 정정」). M6/6A-1부터는 위 `implementation(project(":shared-
    // kernel"))`이 이미 test classpath에 전이돼 별도 testImplementation 선언이 중복이라
    // 지운다(`adapters/build.gradle.kts`의 같은 관례 — "testImplementation은 implementation을
    // 상속하므로 중복 금지").
    // M1/1C — license-* corpus 실행자가 qualification 공개 API(`LicenseEligibility.judge` 등)를
    // 직접 부른다. shared-kernel과 달리 qualification은 main에서 쓰지 않아 test 전용으로 남는다.
    testImplementation(project(":qualification"))
    // M1/1D — base-amount-provenance·floor-shortfall·floor-threshold corpus 실행자가
    // decision 공개 API(`ProvenanceRules.judge`·`measureFloorShortfall` 등)를 직접 부른다.
    testImplementation(project(":decision"))
    // M1/1E — strategy-watch·strategy-validation corpus 실행자가 strategy 공개 API
    // (`WatchRules.evaluate`·`validate` 등)를 직접 부른다. M6/6A-1부터는 위
    // `implementation(project(":strategy"))`이 이미 전이돼 별도 선언이 중복이라 지운다.
    // M3/3A — koneps-collection corpus 실행자가 procurement 공개 API(`canonicalize`·
    // `resolveAmount`·`decideDetailFetch`·`transition`·`mayOverwrite`·`parseSourceZonedInstant`
    // 등)를 직접 부른다. 27 case 전건 authoritative 승격(운영자 승인 2026-09-07) 뒤 배선한다.
    testImplementation(project(":procurement"))
    // manifest.yaml(YAML) 을 읽기 위한 snakeyaml — 카탈로그 좌표는 이미 Boot BOM 관리 하에
    // transitively 해석되던 것을 명시로 올린 것뿐이다(`gradle/libs.versions.toml` 주석 참고).
    testImplementation(libs.snakeyaml)
}

// 아키텍처 게이트는 조합 지점에서 돈다 — app 의 test runtime classpath 에 아홉 모듈이 모두 있다.
tasks.test {
    val architecturePolicy = layout.settingsDirectory.file("config/quality/architecture-policy.properties")
    inputs.file(architecturePolicy).withPropertyName("architecturePolicy")
    systemProperty("bidvector.architecture.policy", architecturePolicy.asFile.absolutePath)
    // T-D 의 정본은 도출된 후보의 **분류**다 — 같은 목록을 여기 두 벌로 두지 않는다.
    val memberEffects = layout.settingsDirectory.file("config/quality/member-effects.properties")
    inputs.file(memberEffects).withPropertyName("memberEffects")
    systemProperty("bidvector.member.effects", memberEffects.asFile.absolutePath)

    // M1/1B-c ④ — corpus 소비 테스트(`SharedKernelCorpusConformanceTest`)가 manifest 와
    // 그 아래 input/expected fixture 전체를 읽는다. `ArchitecturePolicy.kt` 와 같은
    // `System.getProperty` 주입 관례(조사 §6)를 그대로 쓴다.
    val fixturesRoot = layout.settingsDirectory.dir("fixtures")
    inputs.dir(fixturesRoot).withPropertyName("fixturesRoot")
    systemProperty("bidvector.fixtures.root", fixturesRoot.asFile.absolutePath)
    val fixturesManifest = fixturesRoot.file("manifest.yaml")
    systemProperty("bidvector.fixtures.manifest", fixturesManifest.asFile.absolutePath)

    // rate-unit-003·004·money-basis-001·004(compile-fixture 위임)가 이 디렉터리 안 fixture
    // 가족(negative/positive/mutant-N)의 존재만 확인한다 — 컴파일 실패 자체의 단언은
    // `CompileFailureHarnessTest`(`gate.tests.shared-kernel`)의 몫이다.
    val sharedKernelCompileFixtures = layout.settingsDirectory.dir("shared-kernel/src/test/resources/compile-fixtures")
    inputs.dir(sharedKernelCompileFixtures).withPropertyName("sharedKernelCompileFixtures")
    systemProperty("bidvector.sharedkernel.compile-fixtures", sharedKernelCompileFixtures.asFile.absolutePath)

    // M6/6A-1 — `OpenApiContractTest`가 D-6A1-8 단일 출처 YAML을 읽는다. 같은
    // `System.getProperty` 주입 관례(위 두 항목과 같은 이유 — 상대 경로를 test가 직접
    // 추측하지 않는다).
    val openApiSpec = layout.settingsDirectory.file("openapi/bidvector-operator-api.yaml")
    inputs.file(openApiSpec).withPropertyName("openApiSpec")
    systemProperty("bidvector.openapi.spec", openApiSpec.asFile.absolutePath)
}

// 해석만 재면 「호환」을 주장할 수 없다 — 그 버전의 API 로 컴파일되고 JVM 에서 로드되는지는
// 테스트가 잰다. 두 층을 한 명령으로 묶는다.
val compatibilitySmokeTest =
    tasks.register<Test>("compatibilitySmokeTest") {
        group = "verification"
        description = "채택 라이브러리의 대표 API 를 컴파일하고 JVM 에서 로드한다"
        val testSourceSet = sourceSets.getByName("test")
        testClassesDirs = testSourceSet.output.classesDirs
        classpath = testSourceSet.runtimeClasspath
        filter { includeTestsMatching("*BootCompatibilitySmokeTest") }
    }

val compatibilitySmoke =
    tasks.register<CompatibilitySmokeTask>("compatibilitySmoke") {
        group = "verification"
        description = "채택 라이브러리가 Boot 4.x BOM 아래에서 해석·컴파일·로드되는지 잰다"
        dependsOn(compatibilitySmokeTest)
        graphs.add(configurations.named("testRuntimeClasspath").flatMap { it.incoming.resolutionResult.rootComponent })
        expectedModules =
            setOf(
                "org.springframework.boot:spring-boot-starter",
                // M6/6A-1 D-6A1-16 — 등재하지 않으면 이 의존의 해석·컴파일·로드를 아무도 재지 않는다.
                "org.springframework.boot:spring-boot-starter-web",
                "com.github.kagkarlsson:db-scheduler",
                "io.github.resilience4j:resilience4j-retry",
                "io.micrometer:micrometer-core",
                "org.flywaydb:flyway-core",
                "org.testcontainers:testcontainers-postgresql",
                "com.tngtech.archunit:archunit-junit6",
                // 카탈로그가 BOM 을 이기는지 — Boot 4.1.1 BOM 은 kotlin 2.3.21 / jupiter 6.0.3 을 관리한다.
                "org.jetbrains.kotlin:kotlin-stdlib",
                "org.junit.jupiter:junit-jupiter",
            )
        report = layout.buildDirectory.file("reports/compatibility-smoke/resolved-modules.txt")
    }

tasks.named("check") {
    dependsOn(compatibilitySmoke)
}

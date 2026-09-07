import bidvector.buildlogic.CompatibilitySmokeTask
import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    id("bidvector.kotlin-conventions")
    alias(libs.plugins.spring.boot)
}

// 1A 는 controller 도 진입점도 만들지 않는다 — Boot 플러그인은 4.x 호환 실측의 자리로만 선다.
tasks.named<BootJar>("bootJar") { enabled = false }
tasks.named<Jar>("jar") { enabled = true }

dependencies {
    implementation(project(":adapters"))
    implementation(project(":workflow"))

    implementation(platform(libs.spring.boot.bom))
    implementation(libs.spring.boot.starter)
    implementation(libs.db.scheduler)
    implementation(libs.resilience4j.retry)
    implementation(libs.micrometer.core)
    implementation(libs.flyway.core)

    testImplementation(platform(libs.spring.boot.bom))
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.archunit.junit6)

    // 위반 fixture 전용 — 금지 가족을 실제로 컴파일해야 게이트가 그것을 잡는지 잴 수 있다.
    testImplementation(libs.jakarta.persistence)
    testImplementation(libs.jakarta.jms)
    testImplementation(libs.jackson.databind)
    testImplementation(libs.grpc.api)
    testImplementation(libs.protobuf.java)

    // M1/1B-c ④(decision 22, D5(d)) — corpus 소비 테스트(conformance runner). shared-kernel
    // 공개 API 만으로 값을 대조한다(testFixtures 는 하네스 게이트 셋을 깨 쓰지 않는다 —
    // `scope.md` 「Phase 3 중 계약 정정」). `adapters`·`workflow` 가 shared-kernel 을
    // `implementation`(비전이)으로 물어 app 의 compile classpath 에 원래 없었다 — runner 가
    // `Rate`·`Money`·`Fact` 등 공개 API 를 직접 참조하려면 이 test 전용 의존이 필요하다.
    testImplementation(project(":shared-kernel"))
    // M1/1C — license-* corpus 실행자가 qualification 공개 API(`LicenseEligibility.judge` 등)를
    // 직접 부른다. 같은 이유로 shared-kernel 을 test 전용으로 무는 것과 같은 배선이다.
    testImplementation(project(":qualification"))
    // M1/1D — base-amount-provenance·floor-shortfall·floor-threshold corpus 실행자가
    // decision 공개 API(`ProvenanceRules.judge`·`measureFloorShortfall` 등)를 직접 부른다.
    testImplementation(project(":decision"))
    // M1/1E — strategy-watch·strategy-validation corpus 실행자가 strategy 공개 API
    // (`WatchRules.evaluate`·`validate` 등)를 직접 부른다.
    testImplementation(project(":strategy"))
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

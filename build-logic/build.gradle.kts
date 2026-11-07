import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.testing.Test
import java.util.Properties

plugins {
    `kotlin-dsl`
    // 게이트 구현이 자기 규율 밖에 있으면 게이트가 아니다 — build-logic 소스도 lint·detekt 대상이다.
    // convention plugin(`bidvector.kotlin-conventions`)을 여기 적용할 수는 없다: 그 plugin 이
    // 이 빌드의 산출물이라 자기 자신을 적용하는 순환이 된다. 그래서 같은 외부 plugin 둘을
    // 직접 적용하고 **설정 파일을 공유**한다 — 임계의 정본은 루트의 config/ 하나뿐이다.
    // 파일 크기 축은 루트의 `buildLogicSizeGate` 가 이 트리를 걸어 잰다(같은 순환 때문에
    // SizeGateTask 를 여기서 쓸 수 없다 — 그 클래스가 이 빌드의 산출물이다).
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

// included build 는 루트의 gradle.properties 를 상속하지 않는다. 값을 여기 복사하지 않고 읽는다.
val repoRoot: File = rootDir.parentFile
val repoProperties =
    Properties().apply { repoRoot.resolve("gradle.properties").inputStream().use(::load) }

kotlin {
    jvmToolchain(repoProperties.getProperty("bidvector.jvmToolchain").toInt())
}

// `kotlin-dsl` 이 생성 소스를 main source set 에 넣는다. 그것은 우리 코드가 아니고
// ktlint_official 규칙을 만족할 수도 없으므로 두 게이트 모두 손으로 쓴 트리만 본다.
val handWrittenSources = "src/main/kotlin"

detekt {
    buildUponDefaultConfig = true
    config.from(repoRoot.resolve("config/detekt/detekt.yml"))
    source.setFrom(handWrittenSources)
}

// 이 빌드 스크립트에는 `libs` 타입 세이프 접근자가 생성되지 않는다(plugins 블록에만 있다).
val ktlintVersion =
    extensions
        .getByType<VersionCatalogsExtension>()
        .named("libs")
        .findVersion("ktlint")
        .orElseThrow { IllegalStateException("카탈로그에 버전 'ktlint' 가 없다") }
        .requiredVersion

ktlint {
    version = ktlintVersion
}

// **이 빌드의 ktlint 검사는 매번 다시 돈다.** included build 라 루트 `clean` 이 닿지 않는데
// (그 빌드의 `clean` 을 잇는 것은 위험하다 — 실행 중인 루트가 쓰는 plugin jar 를 지우고 같은
// 그래프에서 그 빌드의 다른 task 와 경쟁한다), 위반 파일을 지워도 검사 task 가 UP-TO-DATE 로
// 남아 낡은 리포트가 계속 실패를 냈다. 이 빌드의 소스는 열 몇 개라 재실행 비용이 무시할 만하고,
// 게이트가 결정적으로 도는 편이 낫다.
tasks.withType<org.jlleitschuh.gradle.ktlint.tasks.BaseKtLintCheckTask>().configureEach {
    outputs.upToDateWhen { false }
}

dependencies {
    // **compiler-embeddable 을 먼저 건다.** KGP 는 일부 패키지를 relocate 한 컴파일러를 품고 있어
    // 뒤에 걸면 `KotlinCoreEnvironment` 가 그쪽으로 해석되고 시그니처가 어긋난다(실측).
    implementation(libs.kotlin.compilerEmbeddable)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.detekt.gradlePlugin)
    implementation(libs.ktlint.gradlePlugin)
    implementation(libs.kover.gradlePlugin)
    implementation(libs.cpd.gradlePlugin)
    implementation(libs.archunit.core)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(kotlin("test"))
    testRuntimeOnly(libs.junit.platform.launcher)

    // 이 저장소에서 TestKit 을 쓰는 첫 사례. `withPluginClasspath()`가
    // `pluginUnderTestMetadata`(kotlin-dsl 이 자동 생성)로 이 모듈의 runtime classpath 를
    // 격리된 fixture 프로젝트에 주입해, 오프라인으로 실제 `bidvector.kotlin-conventions`
    // 플러그인을 적용해 볼 수 있다.
    testImplementation(gradleTestKit())
}

/**
 * **판정 test 가 읽는 실제 정책·fixture 를 선언 입력으로 둔다.** 선언하지 않으면 정책을 느슨하게 고쳐도
 * UP-TO-DATE 로 통과해 「규칙이 죽는다」는 단언이 조용히 무의미해진다. 경로도 시스템 속성으로 넘긴다 —
 * 작업 디렉터리에 기대면 실행 위치가 바뀔 때 조용히 깨진다(app 과 같은 형태).
 */
fun Test.declarePolicyInputs(repoRoot: File) {
    val architecturePolicy = repoRoot.resolve("config/quality/architecture-policy.properties")
    inputs.file(architecturePolicy).withPropertyName("architecturePolicy")
    systemProperty("bidvector.architecture.policy", architecturePolicy.absolutePath)

    // ApiTypePolicyTest·DomainApiTypeFixtureTest 가 public API 타입 표면의 금지 목록을 읽는다.
    val apiTypePolicy = repoRoot.resolve("config/quality/api-type-policy.properties")
    inputs.file(apiTypePolicy).withPropertyName("apiTypePolicy")
    systemProperty("bidvector.apitype.policy", apiTypePolicy.absolutePath)

    // `GateRegistrationCensusTest` 의 마지막 test 가 **등재 장부 전체**의 열 모양을 잰다(리뷰 LOW-7).
    inputs.file(repoRoot.resolve("config/quality/gate-tests.properties")).withPropertyName("gateTests")

    // `MigrationOrderTest` 의 뒤 둘이 실제 정책과 실제 디렉터리를 읽는다. 경로는 그 test 가
    // `bidvector.architecture.policy` 에서 유도하므로 system property 를 더 넘기지 않는다.
    val migrationPolicy = repoRoot.resolve("config/quality/migration-policy.properties")
    inputs.file(migrationPolicy).withPropertyName("migrationPolicy")
    val migrationValues = Properties().apply { migrationPolicy.reader(Charsets.UTF_8).use(::load) }
    // 키가 없으면 `null/null` 경로가 되어 **정책 오류가 모호한 `inputs.dir` 실패로** 나온다. 같은
    // 저장소의 `requireValue` 관례대로 읽는 자리에서 끊는다(리뷰 LOW-6).
    fun migrationValue(key: String): String =
        migrationValues.getProperty(key) ?: error("정책 파일 '${migrationPolicy.name}' 에 키 '$key' 가 없다")
    inputs
        .dir(repoRoot.resolve("${migrationValue("migration.module")}/${migrationValue("migration.resource-path")}"))
        .withPropertyName("migrationDirectory")
}

/**
 * fixture 와 **저장소 전수 소스**. 뒤의 것은 `UnusedImportsTest` 의 죽은 면제 단언이 저장소의 import 를
 * 모집단으로 쓰기 때문이다(리뷰 M-3) — 선언하지 않으면 면제를 넓히거나 마지막 도입을 지워도
 * UP-TO-DATE 로 통과한다. 이 저장소가 「선언 입력이 아닌 판정 근거」로 반복해 물린 자리다.
 */
fun Test.declareSourceInputs(repoRoot: File) {
    // DomainSourceReferenceFixtureTest 가 app 모듈에 커밋된 fixture 를 같은 판정 로직으로 재현한다 —
    // project 의존을 만들지 않고 정책 파일과 같은 형태(경로를 시스템 속성으로)로 넘긴다.
    val violatingFixtures = repoRoot.resolve("app/src/test/kotlin/bidvector/archfixture/violating")
    inputs.dir(violatingFixtures).withPropertyName("violatingFixtures")
    systemProperty("bidvector.archfixture.violating", violatingFixtures.absolutePath)

    val allowedFixtures = repoRoot.resolve("app/src/test/kotlin/bidvector/archfixture/allowed")
    inputs.dir(allowedFixtures).withPropertyName("allowedFixtures")
    systemProperty("bidvector.archfixture.allowed", allowedFixtures.absolutePath)

    inputs
        .files(
            fileTree(repoRoot) {
                include("**/*.kt", "**/*.kts")
                exclude("**/build/**", "**/.gradle/**", "**/.gradle-home/**", "**/node_modules/**", "**/.git/**")
            },
        ).withPropertyName("repositoryKotlinSources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    declarePolicyInputs(repoRoot)
    declareSourceInputs(repoRoot)
}

import org.gradle.api.artifacts.VersionCatalogsExtension
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
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.detekt.gradlePlugin)
    implementation(libs.ktlint.gradlePlugin)
    implementation(libs.kover.gradlePlugin)
    implementation(libs.archunit.core)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(kotlin("test"))
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // 판정 테스트가 실제 정책 파일을 읽는다. input 으로 선언하지 않으면 정책을 느슨하게 고쳐도
    // UP-TO-DATE 로 통과해 「규칙이 죽는다」는 단언이 조용히 무의미해진다.
    // 경로도 넘긴다 — 작업 디렉터리에 기대면 실행 위치가 바뀔 때 조용히 깨진다(app 과 같은 형태).
    val architecturePolicy = repoRoot.resolve("config/quality/architecture-policy.properties")
    inputs.file(architecturePolicy).withPropertyName("architecturePolicy")
    systemProperty("bidvector.architecture.policy", architecturePolicy.absolutePath)
}

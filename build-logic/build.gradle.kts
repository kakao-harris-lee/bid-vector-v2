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

dependencies {
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.detekt.gradlePlugin)
    implementation(libs.ktlint.gradlePlugin)
    implementation(libs.kover.gradlePlugin)
    implementation(libs.archunit.core)
}

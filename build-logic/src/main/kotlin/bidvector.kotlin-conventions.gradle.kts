import bidvector.build.DetektThresholdOverlayTask
import bidvector.build.ModuleBaselineSpec
import bidvector.build.QualityBaselineTask
import bidvector.build.SizeGateTask
import bidvector.build.lib
import bidvector.build.version
import bidvector.build.versionCatalog

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("dev.detekt")
    id("com.diffplug.spotless")
    id("org.jetbrains.kotlinx.kover")
}

val libs = versionCatalog
val configDir = layout.settingsDirectory.dir("config")
val sizePolicy = configDir.file("quality/size-policy.properties")

kotlin {
    jvmToolchain(providers.gradleProperty("bidvector.jvmToolchain").get().toInt())
    compilerOptions {
        allWarningsAsErrors = true
    }
}

dependencies {
    testImplementation(platform(libs.lib("junit-bom")))
    testImplementation(libs.lib("junit-jupiter"))
    testRuntimeOnly(libs.lib("junit-platform-launcher"))

    // domain test 는 mock framework 를 쓰지 않는다(v2-지침서.md §5) — 값과 fake port 만.
    testImplementation(libs.lib("kotest-property"))
    testImplementation(libs.lib("kotest-assertions-core"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint(libs.version("ktlint"))
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint(libs.version("ktlint"))
    }
}

val detektThresholdOverlay =
    tasks.register<DetektThresholdOverlayTask>("detektThresholdOverlay") {
        description = "승인된 함수 크기 임계를 정책 파일에서 detekt 설정으로 옮긴다"
        policyFile = sizePolicy
        overlayFile = layout.buildDirectory.file("detekt/threshold-overlay.yml")
    }

detekt {
    buildUponDefaultConfig = true
    config.from(configDir.file("detekt/detekt.yml"), detektThresholdOverlay.flatMap { it.overlayFile })
}

val sizeGate =
    tasks.register<SizeGateTask>("sizeGate") {
        description = "파일 크기 래칫 — 도구에 의존하지 않는 자체 검사(ADR 0007 D-7)"
        policyFile = sizePolicy
        sources.from(layout.projectDirectory.dir("src"))
        report = layout.buildDirectory.file("reports/size-gate/size-gate.txt")
    }

tasks.named("check") {
    dependsOn(sizeGate)
}

rootProject.tasks.named<QualityBaselineTask>("qualityBaseline").configure {
    dependsOn(this@Project.tasks.named("classes"))
    modules +=
        objects.newInstance(ModuleBaselineSpec::class.java).apply {
            moduleName = this@Project.name
            projectDependencies =
                this@Project.configurations
                    .getByName("compileClasspath")
                    .allDependencies
                    .withType(ProjectDependency::class.java)
                    .map { it.path.removePrefix(":") }
                    .toSet()
            sources.from(this@Project.layout.projectDirectory.dir("src/main/kotlin"))
            classes.from(this@Project.layout.buildDirectory.dir("classes/kotlin/main"))
        }
}

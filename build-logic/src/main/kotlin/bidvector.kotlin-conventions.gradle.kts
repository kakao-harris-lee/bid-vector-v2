import bidvector.buildlogic.ModuleBaselineSpec
import bidvector.buildlogic.QualityBaselineTask
import bidvector.buildlogic.SizeGateTask
import bidvector.buildlogic.lib
import bidvector.buildlogic.version
import bidvector.buildlogic.versionCatalog

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("dev.detekt")
    id("org.jlleitschuh.gradle.ktlint")
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

ktlint {
    version = libs.version("ktlint")
    // 규칙 집합과 스타일은 .editorconfig 가 갖는다 — 같은 설정을 두 자리에 두지 않는다.
}

detekt {
    buildUponDefaultConfig = true
    config.from(configDir.file("detekt/detekt.yml"))
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
                this@Project
                    .configurations
                    .getByName("compileClasspath")
                    .allDependencies
                    .withType(ProjectDependency::class.java)
                    .map { it.path.removePrefix(":") }
                    .toSet()
            sources.from(this@Project.layout.projectDirectory.dir("src/main/kotlin"))
            classes.from(this@Project.layout.buildDirectory.dir("classes/kotlin/main"))
        }
}

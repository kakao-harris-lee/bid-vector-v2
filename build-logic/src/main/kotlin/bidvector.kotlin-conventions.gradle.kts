import bidvector.buildlogic.ModuleBaselineSpec
import bidvector.buildlogic.ModuleDependencyGateTask
import bidvector.buildlogic.PackageOwnershipGateTask
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

// 경계의 1차 강제는 **모든 모듈**에 건다. domain 만 걸면 workflow 가 adapters 를 참조하는
// 역방향 선언이 1차에서 통과한다 — 층 규칙은 domain 전용이 아니다(ADR 0006 D-3).
val moduleDependencyGate =
    tasks.register<ModuleDependencyGateTask>("moduleDependencyGate") {
        description = "허용된 project 의존과 금지 group 을 의존 그래프에서 잰다"
        policyFile = configDir.file("quality/architecture-policy.properties")
        moduleName = project.name
        graphs.add(configurations.named("compileClasspath").flatMap { it.incoming.resolutionResult.rootComponent })
        graphs.add(configurations.named("testCompileClasspath").flatMap { it.incoming.resolutionResult.rootComponent })
        report = layout.buildDirectory.file("reports/module-dependency-gate/resolved.txt")
    }

// 경계 규칙 전체가 클래스의 **자기 신고**에 기대지 않게 한다 — 선언 없는 패키지와 남의
// 세그먼트 참칭을 산출물 위치로 잡는다.
val packageOwnershipGate =
    tasks.register<PackageOwnershipGateTask>("packageOwnershipGate") {
        description = "모듈의 class output 이 그 모듈이 소유한 패키지 아래에만 있는지 잰다"
        policyFile = configDir.file("quality/architecture-policy.properties")
        moduleName = project.name
        classDirectories.from(layout.buildDirectory.dir("classes/kotlin/main"))
        dependsOn(tasks.named("classes"))
        report = layout.buildDirectory.file("reports/package-ownership-gate/packages.txt")
    }

val sizeGate =
    tasks.register<SizeGateTask>("sizeGate") {
        description = "파일 크기 래칫 — 도구에 의존하지 않는 자체 검사(ADR 0007 D-7)"
        policyFile = sizePolicy
        sources.from(layout.projectDirectory.dir("src"))
        report = layout.buildDirectory.file("reports/size-gate/size-gate.txt")
    }

// coverage 는 **측정만** 한다. 승인 문서에 임계 수치가 없어 검증 규칙을 두지 않지만
// (그 결정은 1B 로 이월), 리포트를 내지 않으면 CI 가 올릴 산출물도 사람이 볼 수치도 없다 —
// 「배선했다」와 「측정한다」는 다르다. XML 은 기계가, HTML 은 사람이 읽는다.
tasks.named("check") {
    dependsOn(
        sizeGate,
        moduleDependencyGate,
        packageOwnershipGate,
        tasks.named("koverXmlReport"),
        tasks.named("koverHtmlReport"),
    )
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

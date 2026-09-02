import bidvector.buildlogic.ModuleBaselineSpec
import bidvector.buildlogic.ModuleDependencyGateTask
import bidvector.buildlogic.PackageOwnershipGateTask
import bidvector.buildlogic.QualityBaselineTask
import bidvector.buildlogic.SizeGateTask
import bidvector.buildlogic.SourceLanguageGateTask
import bidvector.buildlogic.lib
import bidvector.buildlogic.version
import bidvector.buildlogic.versionCatalog
import org.gradle.api.file.SourceDirectorySet

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("dev.detekt")
    id("org.jlleitschuh.gradle.ktlint")
    id("org.jetbrains.kotlinx.kover")
}

val libs = versionCatalog

/** 사용자가 의존을 **선언하는** 버킷. 도구 자신의 classpath 는 이 혈통을 갖지 않는다. */
val declarableBuckets =
    listOf("implementation", "api", "compileOnly", "compileOnlyApi", "runtimeOnly", "annotationProcessor")
val configDir = layout.settingsDirectory.dir("config")
val sizePolicy = configDir.file("quality/size-policy.properties")

// Java 소스를 구조적으로 봉쇄한다 — 산출물이 `classes/java/main` 으로 새면 소유·크기·경계
// 게이트가 통째로 비껴간다(Codex 4차 b). 봉쇄가 1차이고 sourceLanguageGate 가 2차 그물이다.
sourceSets.configureEach {
    java.setSrcDirs(emptyList<String>())
}

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
        // `compileClasspath` 둘만 보면 `runtimeOnly`·`annotationProcessor`·`ksp` 로 들어오는
        // 프레임워크가 빠져나간다(U-4). 그렇다고 해석 가능한 전건을 넣으면 **컴파일러·도구 자신의
        // classpath**(kotlinCompilerPluginClasspath·detekt·ktlint·kover)까지 들어와 오탐이 난다 —
        // 실제로 kotlinx-serialization 이 그 경로로 잡혔다.
        // 기준은 이름 열거가 아니라 **혈통**이다: 모듈이 *선언한* 의존이 흘러드는 configuration 만 본다.
        configurations.configureEach {
            val declared =
                hierarchy.any { parent ->
                    declarableBuckets.any { bucket ->
                        parent.name == bucket || parent.name == "test${bucket.replaceFirstChar(Char::uppercase)}"
                    }
                }
            // **두 판정의 범위가 다르다.** main 은 *무엇이든 닿는 것*을 본다 — 닿으면 배포되는
            // 코드에 들어갈 수 있다. test 는 *test 코드가 이름 붙일 수 있는 것*(compile classpath)만
            // 본다 — 테스트 도구의 runtime 전이(kotest → xmlutil → kotlinx-serialization)는
            // 도메인의 선택이 아니고, 그것까지 재면 정당한 도구가 위반으로 잡힌다(실측).
            if (isCanBeResolved && declared) {
                val root = incoming.resolutionResult.rootComponent
                when {
                    !name.startsWith("test") -> graphs.add(root)
                    name == "testCompileClasspath" -> testGraphs.add(root)
                }
            }
        }
        report = layout.buildDirectory.file("reports/module-dependency-gate/resolved.txt")
    }

// 경계 규칙 전체가 클래스의 **자기 신고**에 기대지 않게 한다 — 선언 없는 패키지와 남의
// 세그먼트 참칭을 산출물 위치로 잡는다.
val packageOwnershipGate =
    tasks.register<PackageOwnershipGateTask>("packageOwnershipGate") {
        description = "모듈의 class output 이 그 모듈이 소유한 패키지 아래에만 있는지 잰다"
        policyFile = configDir.file("quality/architecture-policy.properties")
        moduleName = project.name
        classDirectories.from(sourceSets.named("main").map { it.output.classesDirs })
        dependsOn(tasks.named("classes"))
        report = layout.buildDirectory.file("reports/package-ownership-gate/packages.txt")
    }

// 손으로 쓴 소스의 실제 위치. `src/` 를 하드코딩하면 `srcDir("gen")` 으로 더한 트리가
// 크기 래칫만 빠져나간다.
val kotlinSourceDirectories =
    provider {
        sourceSets.flatMap { set -> (set.extensions.getByName("kotlin") as SourceDirectorySet).srcDirs }
    }

val sourceLanguageGate =
    tasks.register<SourceLanguageGateTask>("sourceLanguageGate") {
        description = "소스 트리에 Kotlin 아닌 소스가 있으면 실패한다 — java.setSrcDirs 봉쇄의 2차 그물"
        sourceDirectories.from(layout.projectDirectory.dir("src"), kotlinSourceDirectories)
        excludedDirectories.from(provider { sourceSets.flatMap { it.resources.srcDirs } })
        // `md` 는 소스가 아니라 그 트리를 설명하는 문서다 — 컴파일되지 않으므로 class output 을
        // 만들 수 없고, 이 게이트가 막으려는 것(게이트를 비껴가는 산출물)에 해당하지 않는다.
        allowedExtensions = setOf("kt", "kts", "md")
        report = layout.buildDirectory.file("reports/source-language-gate/offenders.txt")
    }

val sizeGate =
    tasks.register<SizeGateTask>("sizeGate") {
        description = "파일 크기 래칫 — 도구에 의존하지 않는 자체 검사(ADR 0007 D-7)"
        policyFile = sizePolicy
        sources.from(kotlinSourceDirectories)
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
        sourceLanguageGate,
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

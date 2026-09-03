import bidvector.buildlogic.CompilerSourceContainmentGateTask
import bidvector.buildlogic.JarContentGateTask
import bidvector.buildlogic.ModuleBaselineSpec
import bidvector.buildlogic.ModuleDependencyGateTask
import bidvector.buildlogic.PackageOwnershipGateTask
import bidvector.buildlogic.QualityBaselineTask
import bidvector.buildlogic.SizeGateTask
import bidvector.buildlogic.SourceLanguageGateTask
import bidvector.buildlogic.lib
import bidvector.buildlogic.version
import bidvector.buildlogic.versionCatalog
import org.jetbrains.kotlin.gradle.tasks.KotlinCompileTool

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

// 컴파일 task 가 실제로 먹는 소스. 봉쇄 게이트의 한쪽 입력이다.
val compiledSourceFiles =
    objects.fileCollection().from(
        tasks
            .named("compileKotlin", KotlinCompileTool::class.java)
            .map { it.sources },
    )

// 형식·크기·언어 도구가 보는 집합. **원산지 판정의 신뢰 이름 집합도 여기서 나온다** —
// 컴파일러가 먹은 목록에서 뽑으면 거기 들어온 파일이 정의상 통과해 자기 인증이 된다.
val sourceSetKotlinFiles =
    objects.fileCollection().from(
        provider { sourceSets["main"].extensions.getByName<SourceDirectorySet>("kotlin") },
    )

// Java 소스를 구조적으로 봉쇄한다 — 산출물이 `classes/java/main` 으로 새면 소유·크기·경계
// 게이트가 통째로 비껴간다. 봉쇄가 1차이고 sourceLanguageGate 가 2차 그물이다.
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
        // test 출력은 뺀다 — fixture 는 일부러 `bidvector.archfixture` 에 산다. 그 대신
        // source set 집합을 고정해 `create("extra")` 경로를 닫는다.
        classDirectories.from(provider { sourceSets.filter { it.name != "test" }.flatMap { it.output.classesDirs } })
        verifiedSources.from(sourceSetKotlinFiles)
        expectedSourceSets = setOf("main", "test")
        actualSourceSets = provider { sourceSets.map { it.name }.toSet() }
        dependsOn(provider { sourceSets.filter { it.name != "test" }.map { it.classesTaskName } })
        report = layout.buildDirectory.file("reports/package-ownership-gate/packages.txt")
    }

// 손으로 쓴 소스의 실제 위치. **`allSource` 는 살아 있는 뷰다** — 나중에 등록된 디렉터리를
// 언어를 가리지 않고 그대로 본다. `kotlin` 확장의 `srcDirs` 를 읽으면 그 확장이 추적하는
// 것만 보이고, 추적 여부는 KGP 의 내부 동작이라 우리가 기댈 계약이 아니다.
// (실측: KGP 2.4.10 은 `java.srcDir` 를 kotlin srcDirs 에도 넣는다. 그래서 현행 배선도 그
//  경로는 잡았다 — 다만 그 사실에 기대지 않는 편이 옳다.)
val handWrittenSourceDirectories =
    provider {
        val resources = sourceSets.flatMap { it.resources.srcDirs }.toSet()
        sourceSets.flatMap { it.allSource.srcDirs } - resources
    }

val sourceLanguageGate =
    tasks.register<SourceLanguageGateTask>("sourceLanguageGate") {
        description = "소스 트리에 Kotlin 아닌 소스가 있으면 실패한다 — java.setSrcDirs 봉쇄의 2차 그물"
        sourceDirectories.from(layout.projectDirectory.dir("src"), handWrittenSourceDirectories)
        excludedDirectories.from(provider { sourceSets.flatMap { it.resources.srcDirs } })
        // `md` 는 소스가 아니라 그 트리를 설명하는 문서다 — 컴파일되지 않으므로 class output 을
        // 만들 수 없고, 이 게이트가 막으려는 것(게이트를 비껴가는 산출물)에 해당하지 않는다.
        allowedExtensions = setOf("kt", "kts", "md")
        report = layout.buildDirectory.file("reports/source-language-gate/offenders.txt")
    }

val jarContentGate =
    tasks.register<JarContentGateTask>("jarContentGate") {
        description = "배포되는 아카이브의 class 가 게이트를 통과한 산출물 그 바이트인지 잰다"
        policyFile = configDir.file("quality/architecture-policy.properties")
        moduleName = project.name
        archives.from(tasks.named("jar").map { (it as Jar).archiveFile })
        verifiedSources.from(sourceSetKotlinFiles)
        // 대조 대상 = 소유 게이트가 이미 본 class output. 판정이 「이름이 맞는가」가 아니라
        // **「게이트를 거친 바이트인가」**가 되려면 이 집합이 필요하다.
        classDirectories.from(provider { sourceSets.filter { it.name != "test" }.flatMap { it.output.classesDirs } })
        dependsOn(provider { sourceSets.filter { it.name != "test" }.map { it.classesTaskName } })
        report = layout.buildDirectory.file("reports/jar-content-gate/entries.txt")
    }

val compilerSourceContainmentGate =
    tasks.register<CompilerSourceContainmentGateTask>("compilerSourceContainmentGate") {
        description = "컴파일 대상이 source set 안에 갇혀 있는지 잰다 — 형식·크기 도구의 사각을 없앤다"
        compilerSources.from(compiledSourceFiles)
        sourceSetSources.from(sourceSetKotlinFiles)
        report = layout.buildDirectory.file("reports/compiler-source-containment/escaped.txt")
    }

val sizeGate =
    tasks.register<SizeGateTask>("sizeGate") {
        description = "파일 크기 래칫 — 도구에 의존하지 않는 자체 검사(ADR 0007 D-7)"
        policyFile = sizePolicy
        // 모듈의 빌드 스크립트도 잰다. 어느 source set 에도 속하지 않아 `allSource` 에 보이지
        // 않지만 실제 코드이고, 빼 두면 긴 함수가 그리로 옮겨 가는 것이 우회가 된다.
        sources.from(handWrittenSourceDirectories, layout.projectDirectory.file("build.gradle.kts"))
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
        jarContentGate,
        compilerSourceContainmentGate,
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

import bidvector.buildlogic.CpdReportPresenceGateTask
import bidvector.buildlogic.DomainApiTypeGateTask
import bidvector.buildlogic.DomainSourceReferenceGateTask
import bidvector.buildlogic.DuplicatePolicy
import bidvector.buildlogic.GateExecutionGateTask
import bidvector.buildlogic.JarContentGateTask
import bidvector.buildlogic.ModuleBaselineSpec
import bidvector.buildlogic.ModuleDependencyGateTask
import bidvector.buildlogic.PackageOwnershipGateTask
import bidvector.buildlogic.QualityBaselineTask
import bidvector.buildlogic.SizeGateTask
import bidvector.buildlogic.SourceLanguageGateTask
import bidvector.buildlogic.SourceSetLayoutGateTask
import bidvector.buildlogic.TypeShapeGateTask
import bidvector.buildlogic.lib
import bidvector.buildlogic.readPolicy
import bidvector.buildlogic.requireList
import bidvector.buildlogic.sourceSetLayoutFacts
import bidvector.buildlogic.version
import bidvector.buildlogic.versionCatalog
import org.jetbrains.kotlin.gradle.tasks.KotlinCompileTool

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("dev.detekt")
    id("org.jlleitschuh.gradle.ktlint")
    id("org.jetbrains.kotlinx.kover")
    id("de.aaschmid.cpd")
}

// M1/1A-b ④(a)(D-2) — `java-test-fixtures` 는 전 모듈에서 허용하지 않는다. 그 source set 을
// 순수성 규칙(도메인 게이트 면제 여부)이 어떻게 다룰지 ADR 로 먼저 정해야 하고 지금 그 요구가
// 없다(1B-c 가 공개 API 로 해결했다) — `OPEN-1BC-TESTFIXTURES-GATE`. `plugins.withId`는 이
// 편집(뒤) 이전에 적용됐든 이후에 적용됐든 같은 프로젝트 안에서는 반드시 한 번 불린다 —
// 플러그인 선언 순서를 가리지 않는다. 나머지 세 게이트((b)(c)(d))는 이 constructive 차단을
// 손으로 우회했을 때(예: `java-test-fixtures`와 이름만 같은 source set 을 직접 만드는 경로)
// 를 잡는 방어 심층이다.
plugins.withId("java-test-fixtures") {
    throw GradleException(
        "모듈 '${project.name}'이 'java-test-fixtures'를 적용했다 — 전 모듈에서 허용하지 않는다 " +
            "(D-2, OPEN-1BC-TESTFIXTURES-GATE). 면제·순수성 규칙을 ADR 로 먼저 정한 뒤에 연다.",
    )
}

val libs = versionCatalog

/** 사용자가 의존을 **선언하는** 버킷. 도구 자신의 classpath 는 이 혈통을 갖지 않는다. */
val declarableBuckets =
    listOf("implementation", "api", "compileOnly", "compileOnlyApi", "runtimeOnly", "annotationProcessor")
val configDir = layout.settingsDirectory.dir("config")
val sizePolicy = configDir.file("quality/size-policy.properties")

// M1/1A-b ④(b)(D-2) — 모듈이 가질 수 있는 source set 집합. 정책 데이터 하나가 아래 두
// 게이트의 리터럴을 대신한다(중복 금지) — `java-test-fixtures`가 추가하는 `testFixtures`는
// 이 집합 밖이다.
private val expectedModuleSourceSets =
    readPolicy(configDir.file("quality/architecture-policy.properties").asFile)
        .requireList("module.expected-source-sets")
        .toSet()

// D-6(Phase 3 중 신설) — 타입 멤버 축만 이 source set 집합을 잰다(관례상 `main`). 파일·함수
// 축은 여전히 전 source set 을 받는다 — 면제는 멤버 축 하나뿐이다.
private val typeMemberSourceSets =
    readPolicy(sizePolicy.asFile)
        .requireList("limit.type.members.source-sets")
        .toSet()

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
        provider { sourceSets["main"].kotlin },
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

// M1/1A-b ③(OPEN-ADR-16 (a)) — PMD CPD **관찰 모드**. 값은 전부 정책 데이터에서 온다
// (`duplicate-policy.properties`, 매직 넘버 금지) — `ignoreFailures`가 `mode=observe`를
// 그대로 번역하므로 이 편집 한 줄이 곧 실패 모드로의 전환 스위치가 되지 않는다(D-4).
// Kotlin property 문법(`ext.language = ...`)은 `CodeQualityExtension`이 상속받은
// `ignoreFailures`에서 private field 접근으로 오판되어 컴파일이 안 됐다 — 명시적 setter
// 호출로 그 모호성을 피한다.
private val duplicatePolicy = DuplicatePolicy.load(configDir.file("quality/duplicate-policy.properties").asFile)

extensions.configure(de.aaschmid.gradle.plugins.cpd.CpdExtension::class.java) {
    setLanguage(duplicatePolicy.language)
    setMinimumTokenCount(duplicatePolicy.minimumTokenCount)
    setToolVersion(duplicatePolicy.toolVersion)
    setIgnoreFailures(duplicatePolicy.ignoreFailures)
}

// 리포트 위치를 우리가 직접 정한다 — 플러그인 기본 report 객체의 `outputLocation`을 다른
// task 의 input 으로 그대로 연결하면 "does not have a task associated with it"로 구성
// 단계에서 실패한다(실측). 경로를 명시하면 이 모호성이 없다.
val cpdReportFile = layout.buildDirectory.file("reports/cpd/${project.name}.xml")

tasks.named<de.aaschmid.gradle.plugins.cpd.Cpd>("cpdCheck") {
    reports.xml.required.set(true)
    reports.xml.outputLocation.set(cpdReportFile)
}

// 관찰 모드가 "실행 안 함"으로 조용히 퇴화하지 않게 리포트 산출 자체를 단언한다(D-4).
val cpdReportPresenceGate =
    tasks.register<CpdReportPresenceGateTask>("cpdReportPresenceGate") {
        description = "PMD CPD 리포트 산출을 단언한다 — 관찰 모드가 '실행 안 함'으로 퇴화하지 않게(D-4)"
        cpdXmlReport.set(cpdReportFile)
        dependsOn(tasks.named("cpdCheck"))
    }

// 경계의 1차 강제는 **모든 모듈**에 건다. domain 만 걸면 workflow 가 adapters 를 참조하는
// 역방향 선언이 1차에서 통과한다 — 층 규칙은 domain 전용이 아니다(ADR 0006 D-3).
val moduleDependencyGate =
    tasks.register<ModuleDependencyGateTask>("moduleDependencyGate") {
        description = "허용된 project 의존과 금지 group 을 의존 그래프에서 잰다"
        policyFile = configDir.file("quality/architecture-policy.properties")
        moduleName = project.name
        // `compileClasspath` 둘만 보면 `runtimeOnly`·`annotationProcessor`·`ksp` 로 들어오는
        // 프레임워크가 빠져나간다. 그렇다고 해석 가능한 전건을 넣으면 **컴파일러·도구 자신의
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

                    // M1/1A-b ④(c) — constructive 가드(④(a))를 우회해 `java-test-fixtures`
                    // 없이 같은 이름의 configuration 을 손으로 만드는 경로까지 1차 게이트가
                    // 보게 한다(방어 심층). 실제로는 ④(a)가 이 configuration 이 생기기 전에
                    // project 평가를 끊으므로 이 분기가 정상 경로에서 값을 받는 일은 없다.
                    name == "testFixturesCompileClasspath" -> testGraphs.add(root)
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
        expectedSourceSets = expectedModuleSourceSets
        actualSourceSets = provider { sourceSets.map { it.name }.toSet() }
        dependsOn(provider { sourceSets.filter { it.name != "test" }.map { it.classesTaskName } })
        report = layout.buildDirectory.file("reports/package-ownership-gate/packages.txt")
    }

// 손으로 쓴 소스의 실제 위치. **`allSource` 는 살아 있는 뷰다** — 나중에 등록된 디렉터리를
// 언어를 가리지 않고 그대로 본다. `kotlin` 확장의 `srcDirs` 를 읽으면 그 확장이 추적하는
// 것만 보이고, 추적 여부는 KGP 의 내부 동작이라 우리가 기댈 계약이 아니다.
// (실측: KGP 2.4.10 은 `java.srcDir` 를 kotlin srcDirs 에도 넣는다. 그래서 현행 배선도 그
//  경로는 잡았다 — 다만 그 사실에 기대지 않는 편이 옳다.)
val conventionSourceDirectories =
    provider { sourceSets.map { layout.projectDirectory.dir("src/${it.name}/kotlin").asFile } }

val sourceLanguageGate =
    tasks.register<SourceLanguageGateTask>("sourceLanguageGate") {
        description = "소스 트리에 Kotlin 아닌 소스가 있으면 실패한다 — java.setSrcDirs 봉쇄의 2차 그물"
        sourceDirectories.from(layout.projectDirectory.dir("src"))
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

val sourceSetLayoutGate =
    tasks.register<SourceSetLayoutGateTask>("sourceSetLayoutGate") {
        description = "소스가 관례 자리에 있고 컴파일 대상이 그 자리에 갇혀 있는지 잰다"
        sourceDirs.putAll(
            provider {
                val root = layout.projectDirectory.asFile
                sourceSets.fold(emptyMap<String, String>()) { acc, set ->
                    acc +
                        sourceSetLayoutFacts(
                            set.name,
                            set.kotlin.srcDirs,
                            set.java.srcDirs,
                            set.resources.srcDirs,
                            root,
                        )
                }
            },
        )
        expectedSourceSets = expectedModuleSourceSets
        actualSourceSets = provider { sourceSets.map { it.name }.toSet() }
        expectedCompileTasks = setOf("compileKotlin", "compileTestKotlin")
        actualCompileTasks =
            provider {
                tasks
                    .withType(KotlinCompileTool::class.java)
                    .names
                    .toSet()
            }
        compileFilters.putAll(
            provider {
                tasks
                    .withType(KotlinCompileTool::class.java)
                    .associate { it.name to it.excludes.joinToString(",") } +
                    sourceSets.associate { "sourceSet:${it.name}" to it.kotlin.excludes.joinToString(",") }
            },
        )
        mainSourceRoot.from(layout.projectDirectory.dir("src/main/kotlin"))
        testSourceRoot.from(layout.projectDirectory.dir("src/test/kotlin"))
        mainSourceSetFiles.from(sourceSetKotlinFiles)
        mainCompilerFiles.from(compiledSourceFiles)
        testSourceSetFiles.from(provider { sourceSets["test"].kotlin })
        testCompilerFiles.from(
            tasks
                .named("compileTestKotlin", KotlinCompileTool::class.java)
                .map { it.sources },
        )
        resourceFiles.from(provider { sourceSets.map { it.resources } })
        report = layout.buildDirectory.file("reports/source-set-layout/violations.txt")
    }

// 게이트를 test 로 표현하면 그 test 를 실행 집합에서 빼는 것이 게이트를 끄는 것과 같아진다.
// 「통과했다」와 「돌았다」는 다른 말이라 후자를 따로 단언한다.
val gateExecutionGate =
    tasks.register<GateExecutionGateTask>("gateExecutionGate") {
        description = "게이트 test class 가 실제로 실행됐고 실패·건너뜀이 없는지 잰다"
        policyFile = configDir.file("quality/gate-tests.properties")
        moduleName = project.name
        resultDirectories.from(
            tasks.named("test", Test::class.java).map { it.reports.junitXml.outputLocation },
        )
        report = layout.buildDirectory.file("reports/gate-execution/violations.txt")
    }

// 바이트코드 층(ArchUnit)과 의존 그래프 층이 함께 놓치는 자리 — 컴파일 시 인라인되는 상수와
// class literal 은 domain 소스가 **이름으로** 부르지만 산출물에 타입 참조를 남기지 않는다
// (알려진 제한 7·11). 소스 자체를 걸어 그 사각을 덮는다.
val domainSourceReferenceGate =
    tasks.register<DomainSourceReferenceGateTask>("domainSourceReferenceGate") {
        description = "domain main 소스가 허용 목록 밖 좌표를 이름으로 부르는지 잰다"
        policyFile = configDir.file("quality/architecture-policy.properties")
        moduleName = project.name
        sourceRoot.from(layout.projectDirectory.dir("src/main/kotlin"))
        report = layout.buildDirectory.file("reports/domain-source-reference/references.txt")
    }

// public domain API 의 타입 표면에 raw 부동소수가 없는지 잰다 — milestone-1.md 「완료 조건」의
// 강제 장치(설계 검토 부록 「요구의 귀속」 — 내용은 1B, 장치는 1A). 소스 참조 게이트와 축이
// 달라(이름 참조 대 타입 표면) 별도 task 로 둔다.
val domainApiTypeGate =
    tasks.register<DomainApiTypeGateTask>("domainApiTypeGate") {
        description = "domain main 의 public(+protected) API 타입 표면에 raw 부동소수가 없는지 잰다"
        policyFile = configDir.file("quality/architecture-policy.properties")
        apiPolicyFile = configDir.file("quality/api-type-policy.properties")
        moduleName = project.name
        sourceRoot.from(layout.projectDirectory.dir("src/main/kotlin"))
        report = layout.buildDirectory.file("reports/domain-api-type/violations.txt")
    }

val sizeGate =
    tasks.register<SizeGateTask>("sizeGate") {
        description = "파일·함수·타입 멤버 크기 래칫 — 도구에 의존하지 않는 자체 검사(ADR 0007 D-7)"
        policyFile = sizePolicy
        // 모듈의 빌드 스크립트도 잰다. 어느 source set 에도 속하지 않아 `allSource` 에 보이지
        // 않지만 실제 코드이고, 빼 두면 긴 함수가 그리로 옮겨 가는 것이 우회가 된다.
        sources.from(conventionSourceDirectories, layout.projectDirectory.file("build.gradle.kts"))
        // D-6 — 타입 멤버 축은 `main`만(build.gradle.kts 는 함수·파일 축에서만 잰다, 타입
        // 없는 스크립트라 실질 영향은 없다).
        typeSources.from(provider { sourceSets.filter { it.name in typeMemberSourceSets }.map { it.kotlin } })
        report = layout.buildDirectory.file("reports/size-gate/size-gate.txt")
    }

// 상속 깊이·구현 인터페이스 수 래칫(D-3) — 바이트코드 기준이라 sizeGate(PSI)와 별도 task.
// qualityBaseline 이 같은 함수(`TypeShape.kt`)로 재는 값과 어긋나지 않는다(중복 금지).
val typeShapeGate =
    tasks.register<TypeShapeGateTask>("typeShapeGate") {
        description = "상속 깊이·구현 인터페이스 수 래칫 — 증가 금지(D-3, OPEN-ADR-06 (a))"
        policyFile = sizePolicy
        classes.from(provider { sourceSets["main"].output.classesDirs })
        dependsOn(tasks.named("classes"))
        report = layout.buildDirectory.file("reports/type-shape-gate/type-shape-gate.txt")
    }

// coverage 는 **측정만** 한다. 승인 문서에 임계 수치가 없어 검증 규칙을 두지 않지만
// (그 결정은 1B 로 이월), 리포트를 내지 않으면 CI 가 올릴 산출물도 사람이 볼 수치도 없다 —
// 「배선했다」와 「측정한다」는 다르다. XML 은 기계가, HTML 은 사람이 읽는다.
tasks.named("check") {
    dependsOn(
        sizeGate,
        typeShapeGate,
        moduleDependencyGate,
        packageOwnershipGate,
        sourceLanguageGate,
        jarContentGate,
        sourceSetLayoutGate,
        gateExecutionGate,
        domainSourceReferenceGate,
        domainApiTypeGate,
        cpdReportPresenceGate,
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
            // 관례 경로를 박지 않고 **실제 source set 과 output** 에서 읽는다 — 측정 정의가
            // 배선을 따라가야 하고, 위치를 고정하는 것은 레이아웃 게이트의 일이다.
            sources.from(this@Project.provider { this@Project.sourceSets["main"].kotlin })
            classes.from(this@Project.provider { this@Project.sourceSets["main"].output.classesDirs })
        }
}

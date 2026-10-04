import bidvector.buildlogic.ContractGateTask
import bidvector.buildlogic.ConventionCoverageGateTask
import bidvector.buildlogic.GateExecutionGateTask
import bidvector.buildlogic.GateRegistrationGateTask
import bidvector.buildlogic.LeakPatternGateTask
import bidvector.buildlogic.MemberEffectGateTask
import bidvector.buildlogic.QualityBaselineTask
import bidvector.buildlogic.SizeGateTask
import bidvector.buildlogic.TestShapeGateTask
import bidvector.buildlogic.TypeShapeGateTask
import bidvector.buildlogic.lib
import bidvector.buildlogic.readPolicy
import bidvector.buildlogic.requireList
import bidvector.buildlogic.requireValue
import bidvector.buildlogic.version
import bidvector.buildlogic.versionCatalog

// D-6(Phase 3 중 신설) — 타입 멤버 축이 재는 source set 이름. `build-logic/src/<name>` 으로
// 그대로 디렉터리가 된다 — included build 는 Gradle source set 이 아니라 원시 디렉터리라
// kotlin-conventions 의 `sourceSets[...]` 조회를 쓸 수 없다.
private val typeMemberSourceSets =
    readPolicy(layout.settingsDirectory.file("config/quality/size-policy.properties").asFile)
        .requireList("limit.type.members.source-sets")

// D-7 — kotlin-conventions 의 `typeShapeRootPackagePrefix` 와 같은 값·같은 이유.
private val typeShapeRootPackagePrefix =
    readPolicy(layout.settingsDirectory.file("config/quality/architecture-policy.properties").asFile)
        .requireValue("package.root")

// 모듈별 입력은 각 모듈의 convention plugin 이 붙인다 — 루트가 subproject 의 configuration 을
// 먼저 읽으려 하면 평가 순서에 걸린다.
tasks.register<QualityBaselineTask>("qualityBaseline") {
    group = "verification"
    description = "OPEN-ADR-06 입력 — v2-지침서.md §5 의 크기·결합도 축과 타입 축을 잰다"
    report = layout.buildDirectory.file("reports/quality-baseline/quality-baseline.md")
}

// build-logic 의 파일 크기 축은 루트가 든다. 그 included build 안에서 `SizeGateTask` 를 쓰면
// 자기가 산출한 클래스를 자기 빌드 스크립트가 쓰는 순환이 된다 — 여기서는 소스를 **텍스트로**
// 읽을 뿐이라 순환이 없다.
val buildLogicSizeGate =
    tasks.register<SizeGateTask>("buildLogicSizeGate") {
        group = "verification"
        description = "included build 의 소스에도 파일 크기 래칫을 건다"
        policyFile = layout.settingsDirectory.file("config/quality/size-policy.properties")
        sources.from(layout.settingsDirectory.dir("build-logic/src"))
        // D-6 — 타입 멤버 축은 `main`만(정책 데이터, `typeMemberSourceSets`). 이 값이
        // build-logic 자신의 `SourceReferencesTest`(35개, D-5 트리거)를 그 축에서 뺀다 —
        // 파일·함수 축(`sources`)은 여전히 test 를 포함한다.
        typeSources.from(typeMemberSourceSets.map { layout.settingsDirectory.dir("build-logic/src/$it") })
        report = layout.buildDirectory.file("reports/size-gate/build-logic.txt")
    }

// 세 축(파일·함수·타입 멤버)은 위에서 build-logic 을 재지만 넷째 축(상속
// 깊이·인터페이스 수 래칫)은 `kotlin-conventions`(9 모듈)에만 등록돼 build-logic 자신은
// 빠져 있었다. `TypeShapeGateTask`는 **바이트코드**가 필요해(PSI로는 상위 타입 해석이 안
// 된다) `SizeGateTask`처럼 소스를 텍스트로 읽을 수 없다 — 대신 이미 컴파일된 build-logic
// 자신의 main 산출물(`build-logic/build/classes/kotlin/main`)을 가리키고, 그 산출물을 만드는
// `:build-logic:classes`에 명시적으로 dependsOn 한다. 이것은 "included build 안에서
// SizeGateTask를 쓰는" 순환(스크립트 자신이 자기 산출물에 의존)과 다르다 — 루트가 이미 완성된
// build-logic 산출물을 **바깥에서** 읽을 뿐이라 순환이 아니다.
val buildLogicTypeShapeGate =
    tasks.register<TypeShapeGateTask>("buildLogicTypeShapeGate") {
        group = "verification"
        description = "included build main 에도 상속 깊이·인터페이스 수 래칫을 건다"
        policyFile = layout.settingsDirectory.file("config/quality/size-policy.properties")
        classes.from(layout.settingsDirectory.dir("build-logic/build/classes/kotlin/main"))
        rootPackagePrefix = typeShapeRootPackagePrefix
        dependsOn(gradle.includedBuild("build-logic").task(":classes"))
        report = layout.buildDirectory.file("reports/type-shape-gate/build-logic.txt")
    }

// build-logic 자신에는 `gateExecutionGate`가 없어 이 slice 가 더한 test
// 여섯(과 기존 게이트 test 열셋)이 "돌았다"는 증거층 밖이었다. `--tests` 로
// `TestFixturesGateTest`(유일한 실행 증거)를 빼도 아무도 알려주지 못했다. 이 task 도
// `GateExecutionGateTask` 클래스를 build-logic 자신의 build.gradle.kts 안에서 쓸 수 없어
// (같은 순환 — 그 클래스가 이 빌드의 산출물이다) 루트에 둔다. JUnit XML 만 읽으므로
// `buildLogicTypeShapeGate`와 달리 컴파일된 클래스는 필요 없다 — `:build-logic:test` 산출물
// 디렉터리만 가리키면 된다.
val buildLogicGateExecutionGate =
    tasks.register<GateExecutionGateTask>("buildLogicGateExecutionGate") {
        group = "verification"
        description = "build-logic 자신의 게이트 test 가 실제로 돌았는지 잰다(M-3)"
        policyFile = layout.settingsDirectory.file("config/quality/gate-tests.properties")
        moduleName = "build-logic"
        resultDirectories.from(layout.settingsDirectory.dir("build-logic/build/test-results/test"))
        dependsOn(gradle.includedBuild("build-logic").task(":test"))
        report = layout.buildDirectory.file("reports/gate-execution/build-logic.txt")
    }

// `buildLogicGateExecutionGate` 의 반대축 — build-logic 자신의 등재 장부가 소스와 같은지 잰다.
// 같은 순환(그 클래스가 이 빌드의 산출물이다) 때문에 루트에 둔다. 컴파일된 test 클래스를 읽으므로
// `:build-logic:testClasses` 에 의존한다.
//
// **메타 애노테이션 조회 자리 둘**(vr r1 H-2·M-1). ① build-logic 자신의 test 출력 — 저자가 그 소스에
// 선언한 합성 애노테이션은 다른 어디에도 없다. ② JUnit 아티팩트 — `@ParameterizedTest` 가
// `@TestTemplate` 이라는 사실이 그 jar 안에 있다. 앞 판은 ② 가 비어 있어 `@ParameterizedTest` 만 가진
// **미등재** build-logic test 가 모집단 밖으로 빠져 조용히 통과했다(그 방향은 fail-loud 가 아니다).
// included build 의 `testRuntimeClasspath` 를 루트에서 집는 대신, 같은 좌표를 카탈로그에서 읽어
// detached configuration 하나로 만든다 — 해소에 필요한 것은 **애노테이션 클래스**뿐이다.
val buildLogicMetaAnnotations =
    configurations.detachedConfiguration(
        dependencies.create("${versionCatalog.lib("junit-jupiter").get().module}:${versionCatalog.version("junit")}"),
    )

val buildLogicGateRegistrationGate =
    tasks.register<GateRegistrationGateTask>("buildLogicGateRegistrationGate") {
        group = "verification"
        description = "build-logic 자신의 게이트 등재 장부가 test 클래스 전수와 같은지 잰다"
        policyFile = layout.settingsDirectory.file("config/quality/gate-tests.properties")
        moduleName = "build-logic"
        testClasses.from(layout.settingsDirectory.dir("build-logic/build/classes/kotlin/test"))
        testSources.from(layout.settingsDirectory.dir("build-logic/src/test"))
        testRuntimeClasspath.from(buildLogicMetaAnnotations)
        // **알려진 제한 — Gradle 필터 출처가 없다**(PR #60 H). build-logic 은 included build 라 루트가 그
        // 빌드의 `Test` task **속성**을 읽지 못한다(`gradle.includedBuild(…)` 가 주는 것은 task 참조뿐).
        // 오늘 그 모듈의 `tasks.withType<Test>` 에 `filter` 블록이 없음을 확인했고, 나중에 생기면 그
        // 제외가 **선언과 어긋나 래칫이 붉는다**(제외 선언은 이미 등식의 한 변이다) — 미탐이 아니라
        // 오탐 방향이다. 유도하려면 그 빌드가 자기 필터를 파일로 내보내야 하고 그 파일은 이 slice 의
        // in_scope 밖이다.
        excludePatterns = emptySet<String>()
        dependsOn(gradle.includedBuild("build-logic").task(":testClasses"))
        report = layout.buildDirectory.file("reports/gate-registration/build-logic.txt")
    }

// 하네스 `test-discovery-guard`(`OPEN-2B-TEST-DISCOVERY-GUARD`) — build-logic 자신의 test
// 소스에도 test-shape 게이트를 건다(M-3 계열, `buildLogicGateExecutionGate` 와 같은 이유로
// 루트에 둔다 — 순환 회피). 소스를 텍스트로 읽을 뿐이라 순환이 없다(`buildLogicSizeGate` 관례).
val buildLogicTestShapeGate =
    tasks.register<TestShapeGateTask>("buildLogicTestShapeGate") {
        group = "verification"
        description = "included build 의 test 소스에도 test-shape 게이트를 건다"
        policyFile = layout.settingsDirectory.file("config/quality/test-shape-policy.properties")
        sources.from(layout.settingsDirectory.dir("build-logic/src/test"))
        report = layout.buildDirectory.file("reports/test-shape-gate/build-logic.txt")
    }

// `.proto` 계약 drift·생성물 수동 편집·게이트 밖 소스를 잡는 게이트(scope.md
// 「이 slice 가 하는 일」 ①②③). `contracts`·`ml-contract`는 어느 subproject 에도 속하지 않아
// (D-2A-0 (c)) 다른 곳의 게이트가 자연히 못 본다 — build-logic 자신처럼 루트에 둔다.
// 외부 프로세스(`buf`·`gradlew`·`git`)에 기댄 판정이라 항상 재실행한다.
val contractGate =
    tasks.register<ContractGateTask>("contractGate") {
        group = "verification"
        description = "buf lint·breaking(승인 태그)·generateProto 결정성·비커밋·무소스를 증명한다"
        policyFile = layout.settingsDirectory.file("config/quality/contract-policy.properties")
        repoRoot = layout.settingsDirectory
        rootGradlew = layout.settingsDirectory.file("gradlew")
        catalogProtobufRuntimeVersion = versionCatalog.version("protobuf-runtime")
        catalogGrpcKotlinVersion = versionCatalog.version("grpc-kotlin")
        // `ml-contract`가 `protoc-gen-grpc-java`를 이 카탈로그 버전
        // 그대로 참조한다(별도 alias 없음, ContractPolicy.protocGenGrpcJavaVersion 참고).
        catalogGrpcJavaVersion = versionCatalog.version("grpc-java")
        report = layout.buildDirectory.file("reports/contract-gate/violations.txt")
        outputs.upToDateWhen { false }
    }

// 허용 클래스 **안**의 효과 멤버는 손으로 열거하지 않고 도출한다. 루트에 두는 이유는 모듈과
// 무관한 전역 사실(JDK 바이트코드 + 허용 목록)이기 때문이다 — 모듈마다 돌릴 이유가 없다.
val memberEffectGate =
    tasks.register<MemberEffectGateTask>("memberEffectGate") {
        group = "verification"
        description = "허용 클래스의 효과 멤버를 도출하고 전부 분류돼 있는지 잰다 — T-D 의 래칫"
        policyFile = layout.settingsDirectory.file("config/quality/architecture-policy.properties")
        derivedFile = layout.settingsDirectory.file("config/quality/member-effects.generated.properties")
        classificationFile = layout.settingsDirectory.file("config/quality/member-effects.properties")
        expectedJdk = providers.gradleProperty("bidvector.jvmToolchain")
        report = layout.buildDirectory.file("reports/member-effects/derived.properties")
    }

// `leak-patterns.txt`가 어느 task 에도
// 배선돼 있지 않아 slice 마다 손으로 돌리는 관행이었다(gate-tests.properties 에도 부재).
// 스캔 대상은 `reports/evidence/`(실제 관행을 승격 — `LeakPatternGateTask` KDoc의
// 근거). 모듈에도 source set 에도 속하지 않으므로(evidence 는 어느 Kotlin 모듈도 아니다)
// `contractGate`·`memberEffectGate`처럼 루트에 둔다.
val leakPatternGate =
    tasks.register<LeakPatternGateTask>("leakPatternGate") {
        group = "verification"
        description = "evidence 문서에 비밀값·raw 식별자 어휘가 baseline 밖에서 새로 나타나는지 잰다"
        patternsFile = layout.settingsDirectory.file("config/quality/leak-patterns.txt")
        baselineFile = layout.settingsDirectory.file("config/quality/leak-pattern-baseline.txt")
        scanRoot.from(layout.settingsDirectory.dir("reports/evidence"))
        // 4E 관례(scope.md S-3c) — `scope.md` 는 계약 문서라 육안 리뷰 대상, 자동 스캔에서 뺀다.
        excludedFileNames.set(setOf("scope.md"))
        repoRoot = layout.settingsDirectory
        report = layout.buildDirectory.file("reports/leak-pattern-gate/matches.txt")
    }

// 루트와 included build 의 `*.gradle.kts` — 모듈에도 source set 에도 속하지 않아 다른 어느
// 게이트의 입력에도 없다. 이름을 열거하면 새 스크립트가 조용히 빠지므로 두 디렉터리를
// **훑어서**(재귀 아님) 낸다.
val looseBuildScripts =
    providers.provider {
        listOf(layout.settingsDirectory.asFile, layout.settingsDirectory.dir("build-logic").asFile)
            .flatMap { directory -> directory.listFiles().orEmpty().toList() }
            .filter { it.isFile && it.name.endsWith(".gradle.kts") }
            .sortedBy { it.path }
    }

val scriptSizeGate =
    tasks.register<SizeGateTask>("scriptSizeGate") {
        group = "verification"
        description = "모듈 밖 빌드 스크립트에도 크기 래칫을 건다"
        policyFile = layout.settingsDirectory.file("config/quality/size-policy.properties")
        sources.from(looseBuildScripts)
        // 루트 밖 스크립트는 source set 구분이 없다(전부 build 로직) — 타입 멤버 축도
        // 같은 집합을 그대로 잰다.
        typeSources.from(looseBuildScripts)
        report = layout.buildDirectory.file("reports/size-gate/build-scripts.txt")
    }

// 루트 프로젝트에는 `check` 가 없어 `./gradlew check` 가 included build 를 지나친다.
// 여기서 만들어 그 빌드의 `check`(ktlint·detekt)와 위 크기 게이트를 함께 건다.
val baseline = tasks.named<QualityBaselineTask>("qualityBaseline")

val conventionCoverageGate =
    tasks.register<ConventionCoverageGateTask>("conventionCoverageGate") {
        group = "verification"
        description = "convention plugin 이 모든 모듈에 적용됐는지 — 게이트의 부재는 조용하다"
        expectedModules = provider { subprojects.map { it.name }.toSet() }
        registeredModules = baseline.map { task -> task.modules.map { it.moduleName.get() }.toSet() }
        report = layout.buildDirectory.file("reports/convention-coverage/modules.txt")
    }

tasks.register("check") {
    group = "verification"
    description = "included build 의 검증까지 루트 check 에 포함한다"
    dependsOn(
        buildLogicSizeGate,
        buildLogicTypeShapeGate,
        buildLogicGateExecutionGate,
        buildLogicGateRegistrationGate,
        buildLogicTestShapeGate,
        scriptSizeGate,
        conventionCoverageGate,
        memberEffectGate,
        contractGate,
        leakPatternGate,
    )
    dependsOn(gradle.includedBuild("build-logic").task(":check"))
}

import bidvector.buildlogic.ConventionCoverageGateTask
import bidvector.buildlogic.GateExecutionGateTask
import bidvector.buildlogic.MemberEffectGateTask
import bidvector.buildlogic.QualityBaselineTask
import bidvector.buildlogic.SizeGateTask
import bidvector.buildlogic.TypeShapeGateTask
import bidvector.buildlogic.readPolicy
import bidvector.buildlogic.requireList

// D-6(Phase 3 중 신설) — 타입 멤버 축이 재는 source set 이름. `build-logic/src/<name>` 으로
// 그대로 디렉터리가 된다 — included build 는 Gradle source set 이 아니라 원시 디렉터리라
// kotlin-conventions 의 `sourceSets[...]` 조회를 쓸 수 없다.
private val typeMemberSourceSets =
    readPolicy(layout.settingsDirectory.file("config/quality/size-policy.properties").asFile)
        .requireList("limit.type.members.source-sets")

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

// verifier r1 M-2 — 세 축(파일·함수·타입 멤버)은 위에서 build-logic 을 재지만 넷째 축(상속
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
        dependsOn(gradle.includedBuild("build-logic").task(":classes"))
        report = layout.buildDirectory.file("reports/type-shape-gate/build-logic.txt")
    }

// verifier r1 M-3 — build-logic 자신에는 `gateExecutionGate`가 없어 이 slice 가 더한 test
// 여섯(과 1A 부터의 기존 게이트 test 열둘)이 "돌았다"는 증거층 밖이었다. `--tests` 로
// `TestFixturesGateTest`(④(a)의 유일한 실행 증거)를 빼도 아무도 알려주지 못했다. 이 task 도
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
        scriptSizeGate,
        conventionCoverageGate,
        memberEffectGate,
    )
    dependsOn(gradle.includedBuild("build-logic").task(":check"))
}

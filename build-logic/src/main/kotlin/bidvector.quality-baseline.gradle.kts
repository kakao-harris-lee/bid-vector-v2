import bidvector.buildlogic.ConventionCoverageGateTask
import bidvector.buildlogic.QualityBaselineTask
import bidvector.buildlogic.SizeGateTask

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
        report = layout.buildDirectory.file("reports/size-gate/build-logic.txt")
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
    dependsOn(buildLogicSizeGate, scriptSizeGate, conventionCoverageGate)
    dependsOn(gradle.includedBuild("build-logic").task(":check"))
}

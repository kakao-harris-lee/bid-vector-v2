import bidvector.buildlogic.MutationCoverageGateTask
import bidvector.buildlogic.MutationPolicy
import bidvector.buildlogic.MutationReportGateTask
import bidvector.buildlogic.PackageOwnershipPolicy
import bidvector.buildlogic.readPolicy
import bidvector.buildlogic.requireList
import bidvector.buildlogic.requireValue
import bidvector.buildlogic.version
import bidvector.buildlogic.versionCatalog

plugins {
    id("bidvector.kotlin-conventions")
    id("info.solidsoft.pitest")
}

// ADR 0007 `OPEN-ADR-07` — mutation 도구는 pitest 다. 이 convention 은 **`check` 에 아무것도
// 걸지 않는다**(D-1): 유일한 실행 자리는 루트의 `mutationCheck` 와 그것을 부르는 CI job 이다.
// `check` 에 결합하면 모든 PR 이 전 모듈 변이 분석을 물고, 그 비용은 실측 전에 질 수 없다.

val libs = versionCatalog
val configDir = layout.settingsDirectory.dir("config")
val mutationPolicyFile = configDir.file("quality/mutation-policy.properties")
// 구성 단계에서 꺼내는 것은 **판정에 쓰이지 않는 값**(실행 폭·출력 형식)뿐이다. 판정하는 두 값
// (모듈 집합과 모듈별 래칫)은 각 게이트 task 가 `@InputFile` 로 받아 실행 시점에 읽는다 —
// 그래야 warm configuration cache 에서도 **정책만 바꾼 편집이 그 task 를 다시 돌게** 한다
// (`GateRegistrationGateTask` 가 자기 장부에 쓰는 것과 같은 사유). Gradle 9 는 구성 단계의 파일
// 읽기도 undeclared configuration input 으로 추적해 CC 엔트리를 무효화하지만, 그것은 **구성을**
// 다시 돌리는 것이고 task 를 다시 돌리는 것은 선언된 입력이 한다.
private val mutationPolicy = MutationPolicy.load(mutationPolicyFile.asFile)
val architecturePolicy = readPolicy(configDir.file("quality/architecture-policy.properties").asFile)

// D-1 — 대상은 **순수 도메인 모듈**뿐이다. 구성 단계에서 끊는다: 리포트를 읽는 게이트보다
// 세고, `kotlin-conventions` 의 `java-test-fixtures` 가드와 같은 자리다. 「정책 집합에 있는가」는
// 여기서 보지 않는다 — 그쪽을 여기서 끊으면 `mutationCoverageGate` 의 반대 방향 가지가
// 프로덕션에서 닿지 못하는 죽은 가지가 된다.
if (project.name !in architecturePolicy.requireList("layer.domain").toSet()) {
    throw GradleException(
        "모듈 '${project.name}' 은 순수 도메인 모듈이 아니다 — mutation 대상은 layer.domain 안에서만 고른다 " +
            "(ADR 0007 OPEN-ADR-07 D-1).",
    )
}

// 대상 클래스를 손으로 열거하지 않는다 — 모듈 소유 패키지 규칙(`packageOwnershipGate` 의
// 정본 함수)이 그대로 답을 준다. `mutableCodePaths` 기본값(이 모듈의 main 산출물)이 둘째
// 울타리이고, 리포트가 그 밖의 클래스를 담았는지는 래칫 게이트가 따로 단언한다.
val ownedPackage =
    PackageOwnershipPolicy(architecturePolicy.requireValue("package.root")).ownedPackage(project.name)

// 리포트 자리를 우리가 정한다 — 게이트와 도구가 **한 값**을 보게 한다(cpd 관례).
val pitestReportDir = layout.buildDirectory.dir("reports/pitest")
val pitestXmlReport = pitestReportDir.map { it.file("mutations.xml") }

pitest {
    // 버전은 카탈로그가 정본이다(정책 데이터에 복제하지 않는다). plugin 기본 PIT 는 우리가 고정한
    // 판보다 낮으므로 `pitestVersion` 명시는 선택이 아니라 필수다(카탈로그 주석).
    pitestVersion = libs.version("pitest")
    junit5PluginVersion = libs.version("pitest-junit5-plugin")

    targetClasses.set(listOf("$ownedPackage.*"))
    threads.set(mutationPolicy.threads)
    outputFormats.set(mutationPolicy.outputFormats)

    // 자리가 실행마다 바뀌면 게이트가 그 자리를 가리킬 수 없다.
    timestampedReports.set(false)
    reportDir.set(pitestReportDir)

    // **`mutationThreshold` 를 설정하지 않는다.** 판정은 `mutationReportGate` 가 리포트에서
    // 재계산한다 — 같은 수가 두 자리에 있으면 안 되고(detekt `LongMethod` 를 끈 자리와 같은
    // 이유), 도구 옵션 하나를 0 으로 돌리는 것이 게이트를 끄는 길이 되어서도 안 된다.
    // mutator 집합도 도구 기본값(`DEFAULTS`)을 쓴다 — 수치도 집합도 지어내지 않는다.
}

val mutationReportGate =
    tasks.register<MutationReportGateTask>("mutationReportGate") {
        group = "verification"
        description = "mutation 점수·변이 수·변이 클래스 수의 절대값 래칫(ADR 0007 OPEN-ADR-07)"
        moduleName = project.name
        policyFile = mutationPolicyFile
        // **한 줄이다** — 같은 선언이 메시지용 경로와 task 지문을 함께 낸다(task KDoc). 지문을
        // 따로 배선하던 앞 판은 그 둘째 줄이 지워지면 test 가 초록인 채로 거짓 초록으로 돌아갔다.
        mutationXmlReport.set(pitestXmlReport)
        classDirectories.from(provider { sourceSets["main"].output.classesDirs })
        dependsOn(tasks.named("pitest"))
        report = layout.buildDirectory.file("reports/mutation-gate/violations.txt")
    }

// 한쪽만 부를 수 없게 한다 — `pitest` 만 돌려 리포트를 보고 래칫을 건너뛰는 경로를 닫는다.
tasks.named("pitest") {
    finalizedBy(mutationReportGate)
}

rootProject.tasks.named<MutationCoverageGateTask>("mutationCoverageGate").configure {
    registeredModules.add(this@Project.name)
}

rootProject.tasks.named("mutationCheck").configure {
    dependsOn(mutationReportGate)
}

package bidvector.app.http

import bidvector.app.wiring.javap
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File

/**
 * 우회 5 폐쇄(scope.md) — `EvaluationDryRunController`가 `workflow.evaluation`에서 참조하는
 * 타입에 port 인터페이스가 없다. 컨트롤러가 `EvaluationDryRunFactory`(app.wiring)를 거치지
 * 않고 `CandidateSourcePort`·`WatchSubjectPort`·`LicenseGatePort`·`MlAnalysisPort`·
 * `CapacityPort`·`NotificationRequestPort`·`CorrelationIdFactory`를 직접 불러 use case 를
 * 우회하는 경로를 막는다(6A 완료 조건 「컨트롤러는 use case만 부른다」).
 *
 * **스캔 범위는 `EvaluationDryRunController` 접두 class 파일만이다 — `app.http` 패키지
 * 전체가 아니다.** 실측: `RequestAuditFilter`(6A-1, 이 slice 무관)가 감사 correlation id
 * 발급에 **같은 port 인터페이스**(`CorrelationIdFactory`)를 이미 정당하게 쓰고 있어,
 * 패키지 전체를 스캔하면 그 기존·무관 참조가 이 컨트롤러의 위반인 것처럼 오검출된다
 * (실제로 이 오검출을 실측한 뒤 스캔 범위를 좁혔다 — `EvaluationDryRunResponse` 결과
 * DTO 는 애초에 workflow.evaluation 을 참조하지 않는다, 같은 좁힘이 아니라 우연히 무관).
 */
class EvaluationHttpDependencyTest {
    @Test
    fun `EvaluationDryRunController 가 참조하는 workflow evaluation 타입에 port 인터페이스가 없다`() {
        val output = controllerClassOutput()
        referencedWorkflowEvaluationTypes(output) intersect PORT_INTERFACE_NAMES shouldBe emptySet()
    }

    /** 양성 대조 — port 인터페이스 참조를 심은 표본은 이 술어에 걸린다. */
    @Test
    fun `port 인터페이스 참조를 심은 표본은 이 술어에 걸린다 — 양성 대조`() {
        val synthetic =
            """
            // Lbidvector/workflow/evaluation/CandidateEvaluation;
            // Lbidvector/workflow/evaluation/CandidateSourcePort;
            """.trimIndent()

        referencedWorkflowEvaluationTypes(synthetic) intersect PORT_INTERFACE_NAMES shouldBe
            setOf("CandidateSourcePort")
    }

    @Test
    fun `현재 컨트롤러는 결과 타입 셋을 실제로 참조한다 — 술어가 공허하게 통과하지 않는다`() {
        val output = controllerClassOutput()
        // CandidateEvaluation(evaluate() 결과 List 원소) · EvaluateCandidatesUseCase(run.useCase
        // 타입, evaluate() 호출) — 둘 다 port 가 아니라 결과·use case 타입. NotificationRequest
        // 참조는 EvaluationDryRunResponse.from()(같은 소스 파일의 다른 컴파일 class)이 진다 —
        // 컨트롤러 class 파일 자신에는 없다(실측).
        referencedWorkflowEvaluationTypes(output) shouldBe setOf("CandidateEvaluation", "EvaluateCandidatesUseCase")
    }
}

private val WORKFLOW_EVALUATION_REFERENCE = Regex("""bidvector[/.]workflow[/.]evaluation[/.]([A-Za-z0-9_$]+)""")

/** `workflow.evaluation`의 port 인터페이스 전수(`Ports.kt`) — use case·결과 타입은 여기 없다. */
private val PORT_INTERFACE_NAMES =
    setOf(
        "CandidateSourcePort",
        "WatchSubjectPort",
        "LicenseGatePort",
        "MlAnalysisPort",
        "CapacityPort",
        "NotificationRequestPort",
        "CorrelationIdFactory",
        "CompetitionSamplePort",
    )

private fun referencedWorkflowEvaluationTypes(javapOutput: String): Set<String> =
    WORKFLOW_EVALUATION_REFERENCE
        .findAll(javapOutput)
        .map { it.groupValues[1].substringBefore('$') }
        .toSet()

/** `EvaluationDryRunController` 자신과 그 컴파일 산출 형제(Kt 파사드·suspend 람다) class 파일만. */
private fun controllerClassOutput(): String {
    val packageDir = File("build/classes/kotlin/main/bidvector/app/http")
    check(packageDir.isDirectory) {
        "빌드 산출물을 찾지 못했다: ${packageDir.absolutePath} — :app:compileKotlin 선행 필요"
    }
    val controllerClassFiles =
        packageDir
            .listFiles { file -> file.isFile && file.name.startsWith("EvaluationDryRunController") }
            .orEmpty()
    check(controllerClassFiles.isNotEmpty()) { "EvaluationDryRunController*.class 를 찾지 못했다" }
    return controllerClassFiles.joinToString("\n") { javap(it) }
}

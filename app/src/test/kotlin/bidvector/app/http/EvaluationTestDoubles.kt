package bidvector.app.http

import bidvector.procurement.Notice
import bidvector.workflow.evaluation.CandidateSourcePort
import bidvector.workflow.evaluation.LicenseGatePort
import bidvector.workflow.evaluation.LicenseVerdict
import bidvector.workflow.evaluation.WatchSubjectOutcome
import bidvector.workflow.evaluation.WatchSubjectPort

/**
 * 빈 후보 목록 위에서는 **절대 불리지 않는** 포트 둘 — 불리면 그 자체가 결함이므로 `error()` 로
 * 사실을 잠근다(`EmptyCandidateSource` 와 한 벌).
 *
 * `HttpTestSupport.kt` 에서 옮겨 왔다. 그 파일이 크기 한도(500줄)에 닿아 레인마다 자기 조립을
 * 얹을 자리가 없었다 — 자기 완결인 이중체를 밖으로 내보내 자리를 만든다(거동 변화 0, 같은
 * 패키지라 호출부의 import 도 그대로다).
 */
internal class UnreachableWatchSubjectPort : WatchSubjectPort {
    override fun subjectFor(notice: Notice): WatchSubjectOutcome = error("빈 후보 목록에서는 불릴 수 없다")
}

internal class UnreachableLicenseGatePort : LicenseGatePort {
    override fun verdictFor(notice: Notice): LicenseVerdict = error("빈 후보 목록에서는 불릴 수 없다")
}

/**
 * `EvaluationDryRunController`(같은 패키지 main)가 `EvaluationDryRunFactory`
 * 를 요구해 `HttpTestApplication` 기반 test(auth·OpenAPI 계약 등, 후보 거동을 보지 않는
 * test)가 빈 후보 목록으로 최소 배선을 한다. 후보가 비어 있어 [watchSubjects]·[licenseGate]
 * 는 절대 안 불린다 — `error()`로 그 사실 자체를 잠근다(우연히 불리면 test가 곧바로 실패).
 *
 * **D-6A3-20 — [failure]는 `TestStrategyRepository.
 * loadFailure`와 같은 관례다.** `OpenApiContractTest`가 409 `CANDIDATE_CAP_EXCEEDED`
 * (D-6A3-7)의 HTTP 층 형태를 잴 때만 이 값을 채운다 — 기본값은 `null`(빈 목록 그대로).
 */
class EmptyCandidateSource : CandidateSourcePort {
    @Volatile var failure: (() -> Throwable)? = null

    override fun openCandidates(): List<Notice> = failure?.let { throw it() } ?: emptyList()
}

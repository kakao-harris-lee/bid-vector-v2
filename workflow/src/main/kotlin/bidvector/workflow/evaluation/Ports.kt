package bidvector.workflow.evaluation

import bidvector.decision.UnitScore
import bidvector.decision.Verdict
import bidvector.procurement.Notice
import bidvector.procurement.NoticeId
import bidvector.qualification.LicenseVerdict
import bidvector.strategy.WatchSubject
import bidvector.workflow.event.CorrelationId

/**
 * 후보 원천 port(scope.md ⑦, ADR 0005 D-9) — 열린 공고 목록을 낸다. 실 조회(3A repository
 * 질의)는 이 slice 밖(구현은 test fake만) — `NoticeRepository`(3A)는 단건 `find`만 내고
 * 다건 스캔 질의가 없어 이 slice가 자신의 port로 그 경계를 낸다.
 */
fun interface CandidateSourcePort {
    fun openCandidates(): List<Notice>
}

/** [WatchSubjectPort]의 결과 — 원문 텍스트 조립 실패는 감시 판정 자체와 다른 축이다. */
sealed interface WatchSubjectOutcome {
    data class Found(
        val subject: WatchSubject,
    ) : WatchSubjectOutcome

    data object Unavailable : WatchSubjectOutcome
}

/**
 * 감시 predicate 입력 조립 port(scope.md ⑦) — `WatchRules.evaluate`(1E, 순수)가 요구하는
 * [WatchSubject]를 공고로부터 만든다. 원문 텍스트는 `Notice`(3A canonical fact)에 없어
 * (제목·본문 필드 없음) 이 slice가 직접 만들 수 없다 — port 뒤로 가른다.
 */
fun interface WatchSubjectPort {
    fun subjectFor(notice: Notice): WatchSubjectOutcome
}

/**
 * 면허 게이트 port(scope.md ⑦) — 1C `LicenseEligibility.judge`(순수 커널)가 이미 낸
 * 판정을 읽기만 한다. 이 slice는 `RequirementCollection`·정책 resolution을 다시
 * 조립하지 않는다(port 뒤로 가른다 — 값 획득 축, 「경계로 처리」).
 */
fun interface LicenseGatePort {
    fun verdictFor(notice: Notice): LicenseVerdict
}

/**
 * ML 분석 port의 결과(scope.md ⑦) — 점수 자체의 부재는 [Analyzed]의 nullable 필드가
 * 진다(4B-1 `LadderInput` 관례, `Verdict.judge`가 그 결측을 `Review(MlUnavailable)`로
 * 옮긴다 — 이 port가 접지 않는다). [SimilarityProjectionNotReady]는 점수 산출 **이전**
 * 단계의 일시적 부재라 다른 신호다(D-11).
 */
sealed interface MlAnalysisOutcome {
    data class Analyzed(
        val priorityScore: UnitScore,
        val probabilityScore: UnitScore?,
        val matchedScore: UnitScore?,
    ) : MlAnalysisOutcome

    data object SimilarityProjectionNotReady : MlAnalysisOutcome
}

/** ML 분석 port(scope.md ⑦) — 실 ML 호출·gRPC·deadline·breaker는 4D. */
fun interface MlAnalysisPort {
    fun analyze(notice: Notice): MlAnalysisOutcome
}

/**
 * run 범위 용량 스냅샷(scope.md ⑧, 결정 10) — [EvaluateCandidatesUseCase.evaluate] 진입에서
 * **정확히 한 번**만 읽는다(legacy 실패 형태 ⑧ 「용량이 스캔·영속 두 시점에 다르게
 * 세어진다」의 뒤집기 — 두 번째 읽기 경로 자체가 시그니처에 없다).
 */
data class CapacitySnapshot(
    val currentActiveBids: Int,
    val maxActiveBids: Int,
)

/** 용량 조회 port(scope.md ⑧) — 실 계수는 이 slice 밖(3D/4C-2 저장소 질의). */
fun interface CapacityPort {
    fun snapshot(): CapacitySnapshot
}

/**
 * 알림 요청 값(scope.md — 「알림 요청을 낳는 자리까지」, 설계 검토 (3) 미달 점검) —
 * **요청**과 **배달**을 타입으로 가른다. `verdict`가 [Verdict.BidNow]로 고정돼 있어
 * 이 값 자체가 「판정이 확정적으로 승격이었다」를 나른다. 생성자는 `internal` — 밖에서
 * 지으면 「판정 없이 알림을 요청했다」를 위조.
 */
@ConsistentCopyVisibility
data class NotificationRequest internal constructor(
    val noticeId: NoticeId,
    val correlationId: CorrelationId,
    val verdict: Verdict.BidNow,
)

/** [NotificationRequestPort]의 결과 — 배달 성공을 주장하지 않는다(요청 접수/실패만). */
sealed interface NotificationRequestOutcome {
    data object Requested : NotificationRequestOutcome

    data object Failed : NotificationRequestOutcome
}

/** 알림 요청 port(scope.md) — 실 발송·렌더링은 4E. */
fun interface NotificationRequestPort {
    fun request(notification: NotificationRequest): NotificationRequestOutcome
}

/**
 * trace 축 생성 port(scope.md ④, 4C-1 `EventIdFactory` 관례) — `workflow`는 난수를
 * 직접 잡지 않는다(4A 「workflow 외부 좌표 0」의 구조적 사실). 반환 타입은 4C-1이 이미
 * 세운 [CorrelationId](같은 `workflow` 모듈, `bidvector.workflow.event`)를 그대로
 * 재사용한다 — 이 축이 4C-2에서 실제 이벤트 봉투에 실릴 값과 같은 정체성을 가져야
 * 「trace가 수집→판정→ML→알림 요청까지 유지」(M4 완료 조건)가 성립한다.
 */
fun interface CorrelationIdFactory {
    fun newId(): CorrelationId
}

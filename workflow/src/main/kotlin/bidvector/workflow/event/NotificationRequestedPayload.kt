package bidvector.workflow.event

/**
 * 알림 요청 판정의 outbox 투영(D-6F7-1·D-6F7-2·D-6F7-5) — `NotificationRequest`
 * (`bidvector.workflow.evaluation`)의 outbox payload 타입. 이 패키지에 두는 이유는
 * 저장 위치가 아니라 **막힘**이다: outbox payload 직렬화를 맡는 어댑터 쪽 codec의
 * 의존 게이트(`EventAdapterDependencyTest`) 허용 루트 다섯에 `workflow.evaluation`이
 * 없어, payload가 거기 있으면 codec이 그것을 참조하는 순간 그 게이트가 막는다.
 *
 * **필드가 원시 타입뿐이다** — `Verdict.BidNow`·`BidNowReason`·`MlUnavailableReason`
 * (전부 `bidvector.decision`)을 이름으로 참조하지 않는다. `EventBoundaryTest`(이 패키지의
 * allow-list, `bidvector.decision` 밖)가 그것을 막을 뿐 아니라, 그 타입들의 생성자가
 * `decision` 모듈에 `internal`로 닫혀 있어 애초에 `workflow`가 재구성할 수 없다 — 이
 * 타입은 값의 **재구성**이 아니라 **투영**(스냅샷)이다.
 *
 * `data-dictionary.md` §3.1 「사람이 읽는 문장은 렌더링 시점에 생성하고 영속하지 않는다」
 * 를 지킨다 — [bidNowReasons]는 `EvidenceLines.kt`가 만드는 한국어 문장이 아니라
 * `BidNowReason`요소의 Kotlin data class 기본 `toString()`(클래스명+필드=값 형태,
 * 로케일 무관 구조 스냅샷)이다. 렌더링은 이 payload를 읽는 미래 발송 slice(`OPEN-STR-12`)
 * 몫이다.
 */
data class NotificationRequestedPayload(
    val noticeId: String,
    val bidNowReasons: List<String>,
    val evidence: NotificationEvidencePayload,
)

/** [NotificationRequestedPayload.evidence]의 두 갈래 — `PredictionEvidence`의 원시 투영. */
sealed interface NotificationEvidencePayload {
    /**
     * `PredictionEvidence.Diagnosed`의 투영(D-6F7-2) — `PredictionDiagnostics`·
     * `ModelReleaseRef`(전부 `bidvector.workflow.prediction`, 이 패키지의 허용 루트)의
     * 필드를 그대로 옮긴다. `excludedSamples`의 키는 `SampleExclusionReason.name`
     * (enum, `workflow.evaluation`)이다.
     */
    data class Diagnosed(
        val trainingRowCount: Int,
        val segmentSupport: String,
        val shrinkageWeight: String,
        val excludedObservations: Int,
        val agencySampleCount: Int,
        val agencySampleBelowThreshold: Boolean,
        val releaseId: String,
        val artifactChecksum: String,
        val featureSchemaVersion: String,
        val codeVersion: String,
        val datasetId: String,
        val releaseKind: String,
        val excludedSamples: Map<String, Int>,
    ) : NotificationEvidencePayload

    /** `PredictionEvidence.NotPredicted`의 투영 — [reason]은 `MlUnavailableReason`의 `toString()`(전부 `data object`라 클래스명과 같다). */
    data class NotPredicted(
        val reason: String,
    ) : NotificationEvidencePayload
}

package bidvector.procurement

import java.time.Instant
import java.util.Objects

/**
 * 원문 raw 키 — legacy `base.py`가 자인한 "문자열 dict 키의 오타를 무시하는" 회귀를 값
 * 객체로 막는다(D-3A-3). 정규화는 하지 않는다 — 원문 그대로다.
 */
data class RawKey(
    val name: String,
) {
    init {
        require(name.isNotBlank()) { "RawKey는 빈 문자열일 수 없다" }
    }
}

/** 이 관측이 어느 KONEPS 엔드포인트에서 왔는가 — 필드 계약의 `presentIn`이 참조한다. */
enum class SourceEndpoint {
    NOTICE_LIST,
    NOTICE_DETAIL,
    LICENSE_LIMIT_DETAIL,
    OPENING_RESULT,
}

/**
 * 수집 어댑터가 넘기는 원문 관측(②, D-3A-3) — raw 키→문자열 값 그대로, 관측 시각, 출처
 * 엔드포인트를 보존한다(감사 — 값을 버리지 않는다). `fields`는 **비공개**다 — 값 취득은
 * [valueOf] 하나(계약을 인자로 요구)뿐이고, 어떤 raw 키가 왔는지는 [keys]로만 열람한다.
 * `Map<RawKey, String>`을 공개하면 계약 없는 소비 경로가 열린다(위협 모델 우회 (1)(10)).
 *
 * `data class`가 아니다 — 합성 `copy()`가 `private val fields`를 이름 있는 매개변수로
 * 다시 열어 같은 우회를 만든다. [equals]/[hashCode]는 test 비교 편의를 위해 값으로 두되
 * getter는 두지 않는다(값을 반환하지 않고 비교 결과만 낸다).
 */
class RawNoticeObservation private constructor(
    private val fields: Map<RawKey, String>,
    val sourceEndpoint: SourceEndpoint,
    val observedAt: Instant,
) {
    val keys: Set<RawKey> get() = fields.keys

    /** 계약이 선언한 `rawName`으로만 값을 얻는다 — 계약 없는 키는 이 경로에 들어올 수 없다. */
    fun valueOf(contract: KonepsFieldContract): String? = fields[contract.rawName]

    override fun equals(other: Any?): Boolean =
        other is RawNoticeObservation &&
            fields == other.fields &&
            sourceEndpoint == other.sourceEndpoint &&
            observedAt == other.observedAt

    override fun hashCode(): Int = Objects.hash(fields, sourceEndpoint, observedAt)

    override fun toString(): String =
        "RawNoticeObservation(keys=${fields.keys}, sourceEndpoint=$sourceEndpoint, observedAt=$observedAt)"

    companion object {
        fun of(
            fields: Map<RawKey, String>,
            sourceEndpoint: SourceEndpoint,
            observedAt: Instant,
        ): RawNoticeObservation = RawNoticeObservation(fields.toMap(), sourceEndpoint, observedAt)
    }
}

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

/**
 * 이 관측이 어느 KONEPS 엔드포인트에서 왔는가 — 필드 계약의 `presentIn`이 참조한다.
 *
 * **P-9 ④ 승인(3B-2, 2026-09-08)** — `OPENING_AWARD_LIST`(낙찰 목록)·`OPENING_RESULT_LIST`
 * (개찰결과 목록)·`RESERVE_PRICE_DETAIL`(예비가격 상세) 셋을 더한다. 낙찰정보서비스가 부르는
 * 세 오퍼레이션 군이 서로 다른 필드 집합을 주므로(`policy-values.md` §1.9.1) 한 토큰으로
 * 접으면 계약의 `presentIn`이 그 구별을 나르지 못한다 — 기존 `OPENING_RESULT`는 지우지 않고
 * (P-9 승인 문면 「옆에 세운다」) 옆에 둔다.
 *
 * **P-13 (a) 승인(M3/3F, 2026-09-09, §1.11)** — `OPENING_COMPLETE`(개찰완료, 투찰 행) 넷째
 * 군을 더한다. legacy 가 부르지 않던 오퍼레이션이라 §1.7 계약이 없었고, 3F 구현 조사가
 * 「어댑터가 계약 없이 값을 꺼낼 경로가 구조적으로 없다」는 것을 드러냈다.
 */
enum class SourceEndpoint {
    NOTICE_LIST,
    NOTICE_DETAIL,
    LICENSE_LIMIT_DETAIL,
    OPENING_RESULT,
    OPENING_AWARD_LIST,
    OPENING_RESULT_LIST,
    RESERVE_PRICE_DETAIL,
    OPENING_COMPLETE,
}

/**
 * 원문 키 하나의 값 — **명시 `null`**과 **문자열 값**을 구분한다(v2-defect, 3A 잔여 일괄
 * verifier r3 전 수정, koneps-collection-003·004). 이전 판은 `Map<RawKey, String>`뿐이라
 * "키가 명시 `null`로 왔다"와 "키 자체가 없다"가 관측 시점에 이미 같은 것(맵에서 빠짐)으로
 * 접혀 그 구분을 [RawNoticeObservation]이 나를 수 없었다.
 */
sealed interface RawValue {
    data class Present(
        val text: String,
    ) : RawValue

    data object ExplicitNull : RawValue
}

/**
 * [RawNoticeObservation.presenceOf] 한 호출의 결과 — [valueOf]는 `Present`만 문자열로 내고
 * `ExplicitNull`·`Missing`을 둘 다 `null`로 접는다(기존 호출부 하위호환), `presenceOf`는
 * 그 셋을 구분해 낸다.
 */
sealed interface FieldPresence {
    data class Present(
        val text: String,
    ) : FieldPresence

    data object ExplicitNull : FieldPresence

    data object Missing : FieldPresence
}

/**
 * 수집 어댑터가 넘기는 원문 관측(②, D-3A-3) — raw 키→값(문자열 또는 명시 `null`) 그대로,
 * 관측 시각, 출처 엔드포인트를 보존한다(감사 — 값을 버리지 않는다). `fields`는 **비공개**다
 * — 값 취득은 [valueOf]·[presenceOf](계약을 인자로 요구)뿐이고, 어떤 raw 키가 왔는지는
 * [keys]로만 열람한다. `Map<RawKey, RawValue>`를 공개하면 계약 없는 소비 경로가 열린다
 * (위협 모델 우회 (1)(10)).
 *
 * `data class`가 아니다 — 합성 `copy()`가 `private val fields`를 이름 있는 매개변수로
 * 다시 열어 같은 우회를 만든다. [equals]/[hashCode]는 test 비교 편의를 위해 값으로 두되
 * getter는 두지 않는다(값을 반환하지 않고 비교 결과만 낸다).
 */
class RawNoticeObservation private constructor(
    private val fields: Map<RawKey, RawValue>,
    val sourceEndpoint: SourceEndpoint,
    val observedAt: Instant,
    /**
     * 항목 원문 JSON 텍스트(**저장 전용**, verifier r1 F-7 뒤 운영자 결정 2026-09-08) —
     * 계약 열람 규칙([valueOf]·[presenceOf])과 무관하다. 도메인 소비 함수는 이 값을
     * 읽지 않는다 — raw persistence 어댑터(3D)가 append 감사 기록에 원문 그대로 싣기
     * 위한 통로일 뿐, 계약 없는 값의 도메인 유입 금지(위협 모델 우회 (1)(10))는 그대로다.
     * 원문이 없는 관측(3B 밖 호출부, 기존 fixture)은 `null`.
     */
    val sourceText: String? = null,
) {
    val keys: Set<RawKey> get() = fields.keys

    /**
     * 계약이 선언한 `rawName`으로만 값을 얻는다 — 계약 없는 키는 이 경로에 들어올 수 없다.
     * 명시 `null`과 키 부재를 구분하지 않는다(기존 소비 함수 다수의 계약, [presenceOf] 가
     * 그 구분이 필요한 호출부의 자리다).
     */
    fun valueOf(contract: KonepsFieldContract): String? = (fields[contract.rawName] as? RawValue.Present)?.text

    /** 명시 `null`(값 있음, 내용 없음)과 키 부재(값 자체가 안 왔음)를 구분해야 하는 호출부용. */
    fun presenceOf(contract: KonepsFieldContract): FieldPresence =
        when (val value = fields[contract.rawName]) {
            is RawValue.Present -> FieldPresence.Present(value.text)
            RawValue.ExplicitNull -> FieldPresence.ExplicitNull
            null -> FieldPresence.Missing
        }

    override fun equals(other: Any?): Boolean =
        other is RawNoticeObservation &&
            fields == other.fields &&
            sourceEndpoint == other.sourceEndpoint &&
            observedAt == other.observedAt &&
            sourceText == other.sourceText

    override fun hashCode(): Int = Objects.hash(fields, sourceEndpoint, observedAt, sourceText)

    override fun toString(): String =
        "RawNoticeObservation(keys=${fields.keys}, sourceEndpoint=$sourceEndpoint, observedAt=$observedAt)"

    companion object {
        /** 문자열 값만 있는 관측(대다수 호출부) — 명시 `null`을 나를 수 없다, [ofRawValues] 참고. */
        fun of(
            fields: Map<RawKey, String>,
            sourceEndpoint: SourceEndpoint,
            observedAt: Instant,
            sourceText: String? = null,
        ): RawNoticeObservation =
            RawNoticeObservation(
                fields.mapValues { (_, text) -> RawValue.Present(text) },
                sourceEndpoint,
                observedAt,
                sourceText,
            )

        /** 명시 `null`을 나를 수 있는 관측 — 부재(사유)를 구분해야 하는 호출부(예: [presenceOf] 소비자)용. */
        fun ofRawValues(
            fields: Map<RawKey, RawValue>,
            sourceEndpoint: SourceEndpoint,
            observedAt: Instant,
            sourceText: String? = null,
        ): RawNoticeObservation = RawNoticeObservation(fields.toMap(), sourceEndpoint, observedAt, sourceText)
    }
}

package bidvector.app.http

import bidvector.sharedkernel.AmountRecord

/**
 * 확정 낙찰가를 응답의 **다섯 칸**으로 편 값 — 금액은 값만 싣지 않는다(R-BASIS-06).
 *
 * 자리가 하나인 이유: 공고 단위 개찰 조회(M7/7C⑤-a)와 내 입찰 결과 조회(M7/7C⑤-b)가 **같은
 * 사실을 같은 칸 이름으로** 낸다. 사상을 두 벌 두면 한쪽만 고쳐지는 길이 생기고, 그때 두 응답이
 * 같은 행을 다르게 말한다.
 *
 * 중첩 object 가 아니라 **접두사로 펴는** 것은 계약의 평탄 규칙이다 — 이 타입은 Kotlin 안에서만
 * 묶여 있고 JSON 에는 다섯 칸이 그대로 나간다.
 *
 * 낙찰가가 없으면 다섯이 함께 `null` 이다(부분적으로 채워진 금액이 없다).
 */
internal class ConfirmedAwardFields(
    award: AmountRecord?,
) {
    val won: Long? = award?.won
    val currency: String? = award?.currency?.name
    val basis: String? = award?.basis?.name
    val vatTreatment: String? = award?.vatTreatment?.name
    val provenance: String? = award?.provenance?.let(::provenanceLabel)
}

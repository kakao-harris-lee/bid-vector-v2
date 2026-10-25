package bidvector.workflow.profile

import bidvector.qualification.LicenseName
import bidvector.workflow.access.AccountId
import java.time.Instant

/**
 * 계정 사업 정보 **읽기** port(M7/7C④) — 모든 메서드의 첫 인자가 [AccountId] 다(R-2, 7B
 * `BidCaseStorePort` 관례). 주체 없이 부르는 메서드가 없으므로 「계정 필터를 빠뜨린 질의」가 이
 * 서명으로는 표현되지 않는다 — 실제로 좁혀지는지는 실 DB test 가 잰다.
 *
 * 읽기 자리에 쓰기 능력을 주지 않으려고 쓰기와 가른다(`StrategyReader` 와 같은 이유, D-6A2b-41).
 *
 * **V12 자격 사실(업종 · 면허 · 지역어)은 이 port 에 없다.** 그 표는 아직 싱글턴이고 그 port
 * (`OperatorProfilePort`)는 [AccountId] 를 받지 않는다 — 받는 척하고 무시하면 「이미 계정
 * 단위다」라는 거짓 신호가 된다. 계정 단위 전환은 R-2 남은 절반(별 slice)이다(checklist §0.2 대가).
 */
interface BusinessProfileReader {
    /** 행이 없는 계정은 [BusinessDetails.EMPTY] 다 — 필드마다 「미설정」이다. */
    fun details(account: AccountId): BusinessDetails

    /** 면허명 순. 없으면 빈 목록. */
    fun capacities(account: AccountId): List<ConstructionCapacity>
}

/**
 * 계정 사업 정보 **쓰기** port. 쓰기 단위는 절 하나 또는 시평액 한 행이다 — 한 메서드가 다른 절에
 * 닿지 않는다(D-7C4-1 P5: 부분 갱신이 다른 필드를 지우지 않는다를 구조로).
 *
 * 시각은 호출자가 싣는다(V22 에 DEFAULT 가 없다 — 시계는 use case 가 쥔다).
 */
interface BusinessProfileStorePort : BusinessProfileReader {
    /** 사업 정보 절을 통째로 교체한다(행이 없으면 만든다). */
    fun replaceDetails(
        account: AccountId,
        details: BusinessDetails,
        at: Instant,
    )

    /** 면허명이 같은 행이 있으면 금액 · 기준 연도를 바꾸고, 없으면 더한다. */
    fun upsertCapacity(
        account: AccountId,
        capacity: ConstructionCapacity,
        at: Instant,
    )

    /** 지운 행이 있었으면 `true`. 다른 계정의 같은 면허명 행에는 닿지 않는다. */
    fun removeCapacity(
        account: AccountId,
        licenseName: LicenseName,
    ): Boolean
}

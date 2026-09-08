package bidvector.adapters.koneps

import bidvector.procurement.NoticeId
import java.net.URI

/**
 * KONEPS 오퍼레이션 하나의 요청 계약(P-12 (a) 승인, `policy-values.md` §1.9.2·§1.9.5) —
 * `inqryDiv`는 **전역 상수가 아니다**: 같은 자리의 코드가 오퍼레이션 군마다 다른 조회 축을
 * 뜻한다(§1.9.2 표). 이 값 객체를 **필수 생성자 인자**로 두고 [buildKonepsOperationUri]에
 * 기본값·폴백을 두지 않는 것이 게이트다 — 리터럴 `"inqryDiv=1"`을 쓰려면 새 인스턴스를
 * 만들어야 하므로 실수로 조용한 오조회가 나지 않는다(설계 검토 Phase 2.5 게이트 ③).
 *
 * `requiresPeriodWindow`(기간 조건 — `inqryBgnDt`/`inqryEndDt`)와 `requiresNoticeNumber`
 * (`bidNtceNo`) 는 문서상 상호배타다(날짜창 스윕 대 단건 조회, D-3B2-2·D-3B2-3) — 함께 참일
 * 근거가 없어 `init`이 거부한다. `requiresNoticeRound`(`bidNtceOrd`)는 `bidNtceNo`가 필요한
 * 오퍼레이션 안에서만 뜻이 있다(license-limit, §1.9.5 — 둘 다 필수).
 */
data class KonepsOperationDescriptor(
    val inquiryDivValue: String,
    val requiresPeriodWindow: Boolean,
    val requiresNoticeNumber: Boolean,
    val requiresNoticeRound: Boolean,
) {
    init {
        require(inquiryDivValue.isNotBlank()) { "inquiryDivValue는 빈 문자열일 수 없다" }
        require(!(requiresPeriodWindow && requiresNoticeNumber)) {
            "기간 조회와 단건 조회는 같은 오퍼레이션에서 동시에 요구되지 않는다(D-3B2-2·D-3B2-3)"
        }
        require(!requiresNoticeRound || requiresNoticeNumber) {
            "requiresNoticeRound는 requiresNoticeNumber가 참인 오퍼레이션에서만 뜻이 있다"
        }
    }
}

/**
 * P-12 (a) 승인값 — 오퍼레이션별 `inqryDiv` 축(§1.9.2)과 필수 항목(§1.9.5). **걷는 축 초기값은
 * 개찰일시**다(P-12 (a) 결정문) — 낙찰 목록·개찰결과 목록 둘 다 개찰일시가 `3`.
 */
internal object KonepsOperationPolicy {
    /** 낙찰 목록(1~4) — `inqryDiv=3`(개찰일시), 기간창 필수(§1.9.2 표). */
    val AWARD_LIST =
        KonepsOperationDescriptor(
            inquiryDivValue = "3",
            requiresPeriodWindow = true,
            requiresNoticeNumber = false,
            requiresNoticeRound = false,
        )

    /** 개찰결과 목록(5~8) — `inqryDiv=3`(개찰일시), 기간창 필수(§1.9.2 표). */
    val OPENING_RESULT_LIST =
        KonepsOperationDescriptor(
            inquiryDivValue = "3",
            requiresPeriodWindow = true,
            requiresNoticeNumber = false,
            requiresNoticeRound = false,
        )

    /** 예비가격 상세(9~12) — `inqryDiv=2`(입찰공고번호, legacy 와 일치 — D-3B2-2 (a)), 단건 조회. */
    val RESERVE_PRICE_DETAIL =
        KonepsOperationDescriptor(
            inquiryDivValue = "2",
            requiresPeriodWindow = false,
            requiresNoticeNumber = true,
            requiresNoticeRound = false,
        )

    /**
     * license-limit(다른 서비스, 입찰공고정보서비스 오퍼레이션 15, §1.9.5) — `inqryDiv=2`
     * (입찰공고번호), `bidNtceNo`·`bidNtceOrd` **둘 다 필수**(누락 시 resultCode `08`).
     */
    val LICENSE_LIMIT_DETAIL =
        KonepsOperationDescriptor(
            inquiryDivValue = "2",
            requiresPeriodWindow = false,
            requiresNoticeNumber = true,
            requiresNoticeRound = true,
        )
}

/**
 * `KonepsOperationDescriptor`를 URI 로 해석한다(P-12 (a)) — 값을 읽을 뿐 오퍼레이션 지식을
 * 갖지 않는다. `periodWindow`/`noticeId`의 존재 여부는 `operation`의 요구와 정확히 일치해야
 * 한다(`require` — 호출부가 계약과 다른 인자를 조립하면 URI 를 만들기 전에 실패한다).
 */
internal fun buildKonepsOperationUri(
    base: URI,
    serviceKey: ServiceKey,
    operation: KonepsOperationDescriptor,
    pageNo: Int,
    numOfRows: Int,
    periodWindow: Pair<String, String>? = null,
    noticeId: NoticeId? = null,
): URI {
    require(operation.requiresPeriodWindow == (periodWindow != null)) {
        "periodWindow 존재 여부는 operation.requiresPeriodWindow(${operation.requiresPeriodWindow})와 같아야 한다"
    }
    require(operation.requiresNoticeNumber == (noticeId != null)) {
        "noticeId 존재 여부는 operation.requiresNoticeNumber(${operation.requiresNoticeNumber})와 같아야 한다"
    }
    val params =
        mutableListOf(
            "serviceKey=${serviceKey.urlEncoded}",
            "pageNo=$pageNo",
            "numOfRows=$numOfRows",
            "type=json",
            "inqryDiv=${operation.inquiryDivValue}",
        )
    if (periodWindow != null) {
        params += "inqryBgnDt=${periodWindow.first}"
        params += "inqryEndDt=${periodWindow.second}"
    }
    if (noticeId != null) {
        params += "bidNtceNo=${noticeId.number.value}"
        if (operation.requiresNoticeRound) params += "bidNtceOrd=${noticeId.round.value}"
    }
    return URI.create("$base?${params.joinToString("&")}")
}

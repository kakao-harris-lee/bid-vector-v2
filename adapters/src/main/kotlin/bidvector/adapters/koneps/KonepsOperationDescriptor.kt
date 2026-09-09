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
 * `inquiryDivValue`는 **nullable**이다(M3/3F, §1.9.2 — 개찰완료·유찰·재입찰 오퍼레이션 군은
 * "`inqryDiv` 자체가 없다"). `null`은 「이 오퍼레이션 군엔 그 축이 없다」는 사실이지 "값을
 * 안 정했다"가 아니다 — [buildKonepsOperationUri]는 `null`일 때 `inqryDiv` 쿼리 파라미터
 * 자체를 내지 않는다(빈 문자열을 보내는 것과 다르다, `init`이 여전히 blank 문자열은 거부한다).
 *
 * `requiresPeriodWindow`(기간 조건 — `inqryBgnDt`/`inqryEndDt`)와 `requiresNoticeNumber`
 * (`bidNtceNo`) 는 문서상 상호배타다(날짜창 스윕 대 단건 조회, D-3B2-2·D-3B2-3) — 함께 참일
 * 근거가 없어 `init`이 거부한다. `requiresNoticeRound`(`bidNtceOrd`)는 `bidNtceNo`가 필요한
 * 오퍼레이션 안에서만 뜻이 있다(license-limit, §1.9.5 — 둘 다 필수).
 *
 * `rowIdentifierRawKeys`(verifier r1 F-1 수정) — (공고번호,차수) 만으로 행을 구별할 수 없는
 * 오퍼레이션이 추가로 선언하는 raw 키. 목록 오퍼레이션(한 행=한 공고)은 `emptyList()`, 상세
 * 오퍼레이션(한 공고=여러 행)은 그 행을 가르는 키를 적는다 — 예비가격 상세는
 * `compnoRsrvtnPrceSno`(복수예가순번), license-limit 은 `lmtGrpNo`+`lmtSno`. **기본값이 없다**
 * — `inqryDiv`와 같은 규율로, 새 오퍼레이션을 추가하면 이 값을 반드시 선언해야 컴파일된다.
 * 비워 두면 같은 공고의 여러 행이 dedup 식별자 충돌로 `duplicate` 에 접혀 사라진다(F-1).
 */
data class KonepsOperationDescriptor(
    val inquiryDivValue: String?,
    val requiresPeriodWindow: Boolean,
    val requiresNoticeNumber: Boolean,
    val requiresNoticeRound: Boolean,
    val rowIdentifierRawKeys: List<String>,
) {
    init {
        require(inquiryDivValue == null || inquiryDivValue.isNotBlank()) {
            "inquiryDivValue는 빈 문자열일 수 없다(축이 없으면 null 을 쓴다)"
        }
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
    /** 낙찰 목록(1~4) — `inqryDiv=3`(개찰일시), 기간창 필수(§1.9.2 표). 한 행=한 공고. */
    val AWARD_LIST =
        KonepsOperationDescriptor(
            inquiryDivValue = "3",
            requiresPeriodWindow = true,
            requiresNoticeNumber = false,
            requiresNoticeRound = false,
            rowIdentifierRawKeys = emptyList(),
        )

    /** 개찰결과 목록(5~8) — `inqryDiv=3`(개찰일시), 기간창 필수(§1.9.2 표). 한 행=한 공고. */
    val OPENING_RESULT_LIST =
        KonepsOperationDescriptor(
            inquiryDivValue = "3",
            requiresPeriodWindow = true,
            requiresNoticeNumber = false,
            requiresNoticeRound = false,
            rowIdentifierRawKeys = emptyList(),
        )

    /**
     * 예비가격 상세(9~12) — `inqryDiv=2`(입찰공고번호, legacy 와 일치 — D-3B2-2 (a)), 단건 조회.
     * **한 공고=여러 행**(복수예비가격, §1.7.1 — "행이 compnoRsrvtnPrceSno 마다 반복") —
     * F-1(verifier r1) 수정으로 그 순번을 행 식별자에 더한다.
     */
    val RESERVE_PRICE_DETAIL =
        KonepsOperationDescriptor(
            inquiryDivValue = "2",
            requiresPeriodWindow = false,
            requiresNoticeNumber = true,
            requiresNoticeRound = false,
            rowIdentifierRawKeys = listOf("compnoRsrvtnPrceSno"),
        )

    /**
     * license-limit(다른 서비스, 입찰공고정보서비스 오퍼레이션 15, §1.9.5) — `inqryDiv=2`
     * (입찰공고번호), `bidNtceNo`·`bidNtceOrd` **둘 다 필수**(누락 시 resultCode `08`). **한
     * 공고=여러 행**(제한그룹번호·제한순번 축) — F-1(verifier r1) 수정으로 그 짝을 행
     * 식별자에 더한다.
     */
    val LICENSE_LIMIT_DETAIL =
        KonepsOperationDescriptor(
            inquiryDivValue = "2",
            requiresPeriodWindow = false,
            requiresNoticeNumber = true,
            requiresNoticeRound = true,
            rowIdentifierRawKeys = listOf("lmtGrpNo", "lmtSno"),
        )

    /**
     * 개찰완료(13, M3/3F) — `inqryDiv` **자체가 없다**(§1.9.2 「13-15: 자체가 없다」). `bidNtceNo`
     * 필수 단건 조회, `bidNtceOrd`는 문서상 옵션이지만 이 서술자는 **보낸다**
     * (`requiresNoticeRound = true`) — 한 공고번호에 차수가 여럿일 때 옵션 취급으로 조회를
     * 넓히는 것보다 좁혀 보내는 편이 안전하다는 판단(구현 레인 판단, evidence 「판단이 갈린
     * 지점」). 투찰자별 행이 여럿이라 `rowIdentifierRawKeys`가 필요한데, `opengRank`는
     * 실측(§1.9.7)에서 전 행 채워지고 유일한 경우가 4/15뿐이라(결측·중복 흔함) 자연 키로
     * 못 쓴다 — 상호(`prcbdrNm`)가 표본에서 완전·유일했다(15/15). **이 raw 키는 SourceBatch
     * 내부 dedup 회계 전용이지 canonical 정체성이 아니다**(D-3F-3 해소로 canonical 승격 자체가
     * 없다) — 같은 이름의 두 투찰자가 실제로 있으면 뒤 행이 `duplicate` 로 잘못 접힐 수 있는
     * 잔여 위험을 그 대가로 안는다(알려진 제한).
     */
    val OPENING_COMPLETE =
        KonepsOperationDescriptor(
            inquiryDivValue = null,
            requiresPeriodWindow = false,
            requiresNoticeNumber = true,
            requiresNoticeRound = true,
            rowIdentifierRawKeys = listOf("prcbdrNm"),
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
        )
    // M3/3F — inquiryDivValue 가 null 이면 이 오퍼레이션 군엔 그 축 자체가 없다(§1.9.2,
    // 개찰완료·유찰·재입찰). 파라미터를 아예 안 낸다 — 빈 문자열이나 지어낸 값을 보내지 않는다.
    operation.inquiryDivValue?.let { params += "inqryDiv=$it" }
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

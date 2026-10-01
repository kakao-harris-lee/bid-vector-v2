package bidvector.procurement

import bidvector.sharedkernel.Basis
import bidvector.sharedkernel.Resolution
import bidvector.sharedkernel.VatTreatment
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDate

private val REFERENCE_DATE: LocalDate = LocalDate.of(2026, 9, 7)

private val RESOLVED_POLICY: KonepsCollectionPolicyData =
    when (val resolution = KONEPS_COLLECTION_POLICY.resolve(REFERENCE_DATE)) {
        is Resolution.Resolved -> {
            resolution.value
        }

        is Resolution.NotApplicable -> {
            error("KONEPS_COLLECTION_POLICY 가 $REFERENCE_DATE 에 해석되지 않는다: ${resolution.reason}")
        }
    }

// sizeGate(함수 50줄)는 본문 있는 선언만 잰다 — 「값을 담을 뿐인 프로퍼티 초기화식」은 그
// 축이 아니다(CleanMigrationColumnTest.kt 관례와 같은 예외, size-policy.properties). 채택
// rawName 리터럴 목록을 함수 본문 밖으로 뽑은 이유가 그것이다(발주기관 넷
// 추가 뒤 함수 본문이 50줄 한도를 넘겨서).
private val EXPECTED_ADOPTED_FIELD_RAW_NAMES: Set<String> =
    setOf(
        "bidNtceNo",
        "bidNtceOrd",
        "bssamt",
        "asignBdgtAmt",
        "bdgtAmt",
        "presmptPrce",
        "sucsfbidLwltRate",
        "bidClseDt",
        "opengDt",
        "bsnsDivNm",
        // D-6F8-2 — 공고명(공고 목록 응답 항목).
        "bidNtceNm",
        // D-6F9-2 — 업무구분 세부 분류 넷(응답에서 실측한 키: 용역 앞 셋, 공사 마지막).
        "pubPrcrmntClsfcNo",
        "pubPrcrmntClsfcNm",
        "srvceDivNm",
        "mainCnsttyNm",
        // D-3H-1 — 발주기관 넷(참고자료 응답 항목 표, P-14). 담당자 키는
        // 등재하지 않는다(scope.md 우회 (4)).
        "dminsttCd",
        "dminsttNm",
        "ntceInsttCd",
        "ntceInsttNm",
        // D-3A-8, §5.5.
        "cnstrtnAbltyEvlAmtList",
        // P-9 승인 — 개찰 축 12행. `bidwinnrBizno`는 P-10 (a) 로
        // 저장하지 않아 등재되지 않는다(13행 중 12행만 인스턴스화).
        "sucsfbidAmt",
        "sucsfbidRate",
        "bidwinnrNm",
        "rlOpengDt",
        "prtcptCnum",
        "fnlSucsfDate",
        // 대문자 변형(외자 2종) — §1.7.4 「두 표기를 각각 등재」.
        "FnlSucsfDate",
        "plnprc",
        "bsisPlnprc",
        "compnoRsrvtnPrceSno",
        "drwtYn",
        "progrsDivCdNm",
        "opengCorpInfo",
        // license-limit(§1.9.5) — 행 식별자로 쓰는 키를 계약에 등재.
        "lmtGrpNo",
        "lmtSno",
        // P-13 (a) 승인(§1.11) — 개찰완료(투찰 행) 10행. 평가점수
        // 넷·prcbdrBizno·prcbdrCeoNm·rmrk·cnsttyAccotBidAmtUrl 은 제외돼 등재되지 않는다.
        "opengRsltDivNm",
        "bidClsfcNo",
        "rbidNo",
        "opengRank",
        "prcbdrNm",
        "bidprcAmt",
        "bidprcrt",
        "drwtNo1",
        "drwtNo2",
        "bidprcDt",
        // M6/6G D-6G-12 — 낙찰방법 둘. 공고 목록 응답에 이미 오던 키이고 계약이 없어
        // 떨어지고 있었다(제외 조건 ①⑦⑩의 1차 입력).
        "sucsfbidMthdCd",
        "sucsfbidMthdNm",
        // M6/6G D-6G-12 — 입찰가격산식 A 정보(op 24) 13행. A 합산 항목 일곱 + 표준시장단가
        // 금액 + 적용 여부 술어 둘 + 일시 둘 + 예정가격결정방법명.
        "npnInsrprm",
        "mrfnHealthInsrprm",
        "odsnLngtrmrcprInsrprm",
        "rtrfundNon",
        "sftyMngcst",
        "sftyChckMngcst",
        "qltyMngcst",
        "smkpAmt",
        "qltyMngcstAObjYn",
        "smkpAmtYn",
        "ntceNticeDt",
        "bidPrceCalclAOpenDt",
        "prearngPrceDcsnMthdNm",
        // M6/6G D-6G-19 — 기초금액 조회(op 5·6·7) 신규 다섯. A 합산 항목 열은 행을 늘리지 않고
        // presentIn 만 넓혔다(같은 raw 키를 두 행으로 등재할 수 없다).
        "rsrvtnPrceRngBgnRate",
        "rsrvtnPrceRngEndRate",
        "bssamtOpenDt",
        "bidPrceCalclAYn",
        "bssAmtPurcnstcst",
        "industSftyHelthMngcst",
        // M6/6G D-6G-22 — 새 호출 비용 0(공고 목록 응답에 이미 온다).
        "sucsfbidMthdAppStd",
        "aplBssCntnts",
        // M6/6G D-6G-28 — 공고일을 목록 축에서도 읽는다(A 오퍼레이션은 공사 전용이라 그것만으로는
        // 용역·물품의 공고일이 빈다).
        "bidNtceDt",
    )

/**
 * 운영 정책 인스턴스와 승인 표(`policy-values.md` §6)의 일치 대조 — 옮겨 적기 오류 방지.
 * 표 항목 수와 대표 키 몇 개의 값을 대조한다.
 */
class CollectionPolicyTest {
    @Test
    fun `필드 계약은 승인된 채택분만 등재한다 — 미확정 칸은 인스턴스화하지 않는다`() {
        RESOLVED_POLICY.fieldContracts.contracts
            .map { it.rawName.name }
            .toSet() shouldBe EXPECTED_ADOPTED_FIELD_RAW_NAMES
    }

    @Test
    fun `개찰 축 금액 개념은 각자 basis 를 갖는다 — sucsfbidAmt AWARD, plnprc YEGA, bsisPlnprc 는 basis 미확정(P-9)`() {
        RESOLVED_POLICY.fieldContracts.contractFor(RawKey("sucsfbidAmt"))!!.basis shouldBe Basis.AWARD
        RESOLVED_POLICY.fieldContracts.contractFor(RawKey("plnprc"))!!.basis shouldBe Basis.YEGA
        RESOLVED_POLICY.fieldContracts.contractFor(RawKey("bsisPlnprc"))!!.basis shouldBe null
    }

    @Test
    fun `sucsfbidRate 는 기존 WINNING_RATE 토큰을 재사용한다 — 새 토큰을 짓지 않는다`() {
        val contract = RESOLVED_POLICY.fieldContracts.contractFor(RawKey("sucsfbidRate"))!!

        contract.concept shouldBe FieldConcept.WINNING_RATE
        contract.scale shouldBe FieldScale.PERCENT
        contract.unit shouldBe FieldUnit.PERCENT
        contract.expectedRange shouldBe null
    }

    @Test
    fun `셈 축(참가업체수·복수예가순번)은 COUNT 스케일이고 unit 은 NONE 이다 — P-9 ②`() {
        listOf("prtcptCnum", "compnoRsrvtnPrceSno").forEach { key ->
            val contract = RESOLVED_POLICY.fieldContracts.contractFor(RawKey(key))!!
            contract.scale shouldBe FieldScale.COUNT
            contract.unit shouldBe FieldUnit.NONE
        }
    }

    @Test
    fun `rlOpengDt 는 낙찰 목록과 예비가격 상세 양쪽에 있다 — presentIn 이 오퍼레이션 군을 구별한다(P-9 ④)`() {
        RESOLVED_POLICY.fieldContracts.contractFor(RawKey("rlOpengDt"))!!.presentIn shouldBe
            setOf(SourceEndpoint.OPENING_AWARD_LIST, SourceEndpoint.RESERVE_PRICE_DETAIL)
        RESOLVED_POLICY.fieldContracts.contractFor(RawKey("opengCorpInfo"))!!.presentIn shouldBe
            setOf(SourceEndpoint.OPENING_RESULT_LIST)
    }

    @Test
    fun `bidNtceNo·bidNtceOrd 는 식별자는 공고 축 밖 여섯 엔드포인트에도 실린다 — F-6·G-4·P-13·D-6G-12`() {
        val allEndpoints =
            setOf(
                SourceEndpoint.NOTICE_LIST,
                SourceEndpoint.OPENING_AWARD_LIST,
                SourceEndpoint.OPENING_RESULT_LIST,
                SourceEndpoint.RESERVE_PRICE_DETAIL,
                SourceEndpoint.LICENSE_LIMIT_DETAIL,
                // P-13 (a) 승인.
                SourceEndpoint.OPENING_COMPLETE,
                // M6/6G D-6G-12 — 입찰가격산식 A 정보. 좁히면 이 축의 allow-list 반전이
                // 식별자부터 떨어뜨려 전 항목이 「공고번호 없음」으로 오분류된다.
                SourceEndpoint.BID_PRICE_FORMULA_A,
                // M6/6G D-6G-19 — 기초금액 조회.
                SourceEndpoint.BASE_AMOUNT_DETAIL,
            )
        RESOLVED_POLICY.fieldContracts.contractFor(RawKey("bidNtceNo"))!!.presentIn shouldBe allEndpoints
        RESOLVED_POLICY.fieldContracts.contractFor(RawKey("bidNtceOrd"))!!.presentIn shouldBe allEndpoints
    }

    @Test
    fun `lmtGrpNo·lmtSno 는 license-limit 전용으로 등재된다 — G-4, §1-9-5`() {
        RESOLVED_POLICY.fieldContracts.contractFor(RawKey("lmtGrpNo"))!!.presentIn shouldBe
            setOf(SourceEndpoint.LICENSE_LIMIT_DETAIL)
        RESOLVED_POLICY.fieldContracts.contractFor(RawKey("lmtSno"))!!.presentIn shouldBe
            setOf(SourceEndpoint.LICENSE_LIMIT_DETAIL)
    }

    @Test
    fun `bssamt 의 presentIn 은 예비가격 상세·기초금액 조회로 넓어진다 — F-6 · D-6G-19`() {
        RESOLVED_POLICY.fieldContracts.contractFor(RawKey("bssamt"))!!.presentIn shouldBe
            setOf(
                SourceEndpoint.NOTICE_LIST,
                SourceEndpoint.RESERVE_PRICE_DETAIL,
                SourceEndpoint.BASE_AMOUNT_DETAIL,
            )
    }

    @Test
    fun `bidwinnrBizno 는 어떤 개찰 축 행에도 등재되지 않는다 — P-10 (a), 저장하지 않는 값은 계약을 두지 않는다`() {
        RESOLVED_POLICY.fieldContracts.contractFor(RawKey("bidwinnrBizno")) shouldBe null
    }

    @Test
    fun `fnlSucsfDate 대문자 변형도 등재된다 — F-5, 두 표기가 같은 개념을 공유한다`() {
        val lower = RESOLVED_POLICY.fieldContracts.contractFor(RawKey("fnlSucsfDate"))!!
        val upper = RESOLVED_POLICY.fieldContracts.contractFor(RawKey("FnlSucsfDate"))!!

        lower.concept shouldBe FieldConcept.FINAL_AWARD_DATE
        upper.concept shouldBe FieldConcept.FINAL_AWARD_DATE
    }

    @Test
    fun `시공능력평가금액목록은 캐럿 구분 DELIMITED_LIST 다 — policy-values md 1-5, v2-defect 018 회귀 가드`() {
        val contract = RESOLVED_POLICY.fieldContracts.contractFor(RawKey("cnstrtnAbltyEvlAmtList"))!!

        contract.scale shouldBe FieldScale.DELIMITED_LIST
        contract.listComponentSeparator shouldBe '^'
        contract.unit shouldBe FieldUnit.NONE
    }

    @Test
    fun `presmptPrce 는 과세 제외·원화 단위다 — policy-values md 1-1`() {
        val contract = RESOLVED_POLICY.fieldContracts.contractFor(RawKey("presmptPrce"))!!

        contract.vatTreatment shouldBe VatTreatment.EXCLUSIVE
        contract.unit shouldBe FieldUnit.WON
        contract.basis shouldBe Basis.ESTIMATED
    }

    @Test
    fun `asignBdgtAmt·bdgtAmt 는 과세 미선언(UNKNOWN)이고 배정예산 basis 다 — 1-1`() {
        val fields = listOf("asignBdgtAmt", "bdgtAmt").map { RESOLVED_POLICY.fieldContracts.contractFor(RawKey(it))!! }

        fields.forEach { contract ->
            contract.vatTreatment shouldBe VatTreatment.UNKNOWN
            contract.basis shouldBe Basis.ALLOCATED_BUDGET
            contract.provenanceTemplate shouldBe FieldProvenanceTemplate.FILLED_FROM_BUDGET_KEY
        }
    }

    /**
     * M6/6G D-6G-19 로 `bssAmtPurcnstcst` 가 **등재된다** — 다만 기초금액으로가 아니라 순공사원가
     * (제외 ⑨의 입력)로다. 이 test 가 지키는 것은 「그 키가 없다」가 아니라 **「기초금액 축에 서는
     * raw 키는 `bssamt` 하나다」**였다(legacy 가 부분을 전체 자리에 넣은 것을 되돌린 판정) — 그
     * 판정을 개념 축으로 다시 세운다.
     */
    @Test
    fun `기초금액 축에 서는 raw 키는 bssamt 하나다 — 순공사원가는 다른 개념으로 등재된다`() {
        RESOLVED_POLICY.fieldContracts.contractsFor(FieldConcept.BASE_AMOUNT).map { it.rawName } shouldBe
            listOf(RawKey("bssamt"))
        RESOLVED_POLICY.fieldContracts.contractFor(RawKey("bssAmt")) shouldBe null
        RESOLVED_POLICY.fieldContracts.contractFor(RawKey("bssAmtPurcnstcst"))!!.concept shouldBe
            FieldConcept.PURE_CONSTRUCTION_COST
        RESOLVED_POLICY.baseAmountResolutionOrder shouldNotContain RawKey("bssAmtPurcnstcst")
    }

    @Test
    fun `기초금액 해석 순서는 bssamt 를 앞세우고 배정예산 폴백 둘을 잇는다 — P-3`() {
        RESOLVED_POLICY.baseAmountResolutionOrder shouldBe
            listOf(RawKey("bssamt"), RawKey("asignBdgtAmt"), RawKey("bdgtAmt"))
        RESOLVED_POLICY.estimatedPriceResolutionOrder shouldBe listOf(RawKey("presmptPrce"))
    }

    @Test
    fun `resultCode 범주는 16 코드를 담고 03 은 NO_DATA 다 — P-4`() {
        val categories = RESOLVED_POLICY.resultCodeCategories
        categories.size shouldBe 16
        categories.first { it.code == "03" }.category shouldBe ResultCodeCategory.NO_DATA
        categories.first { it.code == "08" }.category shouldBe ResultCodeCategory.INPUT_ERROR
        categories.first { it.code == "22" }.category shouldBe ResultCodeCategory.QUOTA_EXCEEDED
        categories.first { it.code == "30" }.category shouldBe ResultCodeCategory.NOT_RETRYABLE
    }

    @Test
    fun `일시 두 필드는 ASSUME_KST 규칙을 갖는다 — P-2`() {
        listOf("bidClseDt", "opengDt").forEach { key ->
            RESOLVED_POLICY.fieldContracts.contractFor(RawKey(key))!!.sourceZone shouldBe SourceZoneRuleId.ASSUME_KST
        }
    }

    @Test
    fun `조회 가치 게이트는 24h_48h 잠정값과 축 재호출 상한을 담는다 — P-5 · D-6G2d-8 ⓒ`() {
        RESOLVED_POLICY.detailFetchGates shouldBe
            DetailFetchGates(ageGateHours = 24, recheckGateHours = 48, axisRetryLimit = 3)
    }

    @Test
    fun `업무구분명 문서 열거 어휘는 물품_용역_공사_외자 넷이다 — policy-values md 1-5, v2-defect 016 회귀 가드`() {
        RESOLVED_POLICY.businessCategoryDocumentedLabels shouldBe
            DocumentedVocabulary(listOf("물품", "용역", "공사", "외자"))
    }

    @Test
    fun `일시 패턴은 문서 authoritative 형식(공백 구분자) 하나다 — policy-values md 1-4, v2-defect 026 회귀 가드`() {
        RESOLVED_POLICY.dateTimePatterns shouldBe listOf(DateTimePatternId.KONEPS_SPACE_DELIMITED_19)
    }
}

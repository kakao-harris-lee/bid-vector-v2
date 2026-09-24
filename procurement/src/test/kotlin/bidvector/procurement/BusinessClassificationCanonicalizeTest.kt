package bidvector.procurement

import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.Resolution
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

/**
 * D-6F9-1·2(M6/6F-9) — 업무구분 축의 **네 칸**이 각자 자기 원천에서만 채워진다: 대분류는 관측이 나르는 수집
 * 오퍼레이션 값, 용역구분·공공조달분류(→ [BusinessCategory])·주공종은 응답 필드. 축을 한 자리에 접지 않는다
 * (P-7 · `OPEN-COL-03`) — 용역구분은 라벨 칸에 없고, 주공종은 코드를 낳지 않는다(우회 2·3).
 */
class BusinessClassificationCanonicalizeTest {
    private val operationalPolicy: KonepsCollectionPolicyData =
        (KONEPS_COLLECTION_POLICY.resolve(LocalDate.of(2026, 9, 24)) as Resolution.Resolved).value

    private val identity = mapOf("bidNtceNo" to "20260101001", "bidNtceOrd" to "000")

    private fun collected(
        fields: Map<String, String>,
        division: BusinessDivision? = null,
        policy: KonepsCollectionPolicyData = operationalPolicy,
    ): NoticeCollected {
        val observation =
            RawNoticeObservation.of(
                (identity + fields).mapKeys { RawKey(it.key) },
                SourceEndpoint.NOTICE_LIST,
                Instant.EPOCH,
                sourceDivision = division,
            )
        return canonicalize(observation, policy)
            .shouldBeInstanceOf<CanonicalizationOutcome.Normalized>()
            .command
    }

    private val serviceItem =
        mapOf(
            "srvceDivNm" to " 기술용역 ",
            "pubPrcrmntClsfcNo" to " 81111500 ",
            "pubPrcrmntClsfcNm" to " 정보시스템 개발 서비스 ",
        )

    @Test
    fun `용역 항목 — 대분류는 오퍼레이션 값, 용역구분·공공조달분류는 응답 필드에서 각자의 칸으로 간다`() {
        val command = collected(serviceItem, BusinessDivision.SERVICE)

        command.businessDivision shouldBe BusinessDivision.SERVICE
        command.serviceDivision shouldBe ServiceDivision.of("기술용역")
        command.businessCategory shouldBe
            BusinessCategory(CategoryCode.of("81111500"), CategoryLabel("정보시스템 개발 서비스"))
        command.mainConstructionType shouldBe null
    }

    @Test
    fun `공사 항목 — 주공종은 이름만 싣고 코드를 지어내지 않는다 — 공고 업무구분은 null 이다`() {
        val command = collected(mapOf("mainCnsttyNm" to " 전기공사업 "), BusinessDivision.CONSTRUCTION)

        command.businessDivision shouldBe BusinessDivision.CONSTRUCTION
        command.mainConstructionType shouldBe MainConstructionType.of("전기공사업")
        command.businessCategory shouldBe null
        command.serviceDivision shouldBe null
    }

    @Test
    fun `용역구분은 업무구분 라벨 칸에 섞이지 않는다 — 라벨은 공공조달분류명뿐이다`() {
        val command = collected(serviceItem, BusinessDivision.SERVICE)

        command.businessCategory?.label?.value shouldBe "정보시스템 개발 서비스"
        command.businessCategory?.label?.value shouldNotBe command.serviceDivision?.value
        command.serviceDivision?.value shouldBe "기술용역"
    }

    @Test
    fun `공공조달분류 번호는 업무구분 코드와 같은 정규화 규칙 하나를 지난다`() {
        val command = collected(mapOf("pubPrcrmntClsfcNo" to "  AB00120 "), BusinessDivision.SERVICE)

        command.businessCategory?.code shouldBe CategoryCode.of("ab00120")
        command.businessCategory?.code?.value shouldBe "ab00120"
    }

    @Test
    fun `대분류는 수집 오퍼레이션 값만 쓴다 — 응답의 문서 열거 라벨이 무엇이든 바꾸지 않고 탈락시키지도 않는다`() {
        listOf("용역", "공사", "물품", "외자", "임의의 값", "", " ").forEach { responseLabel ->
            withClue("응답 bsnsDivNm='$responseLabel'") {
                collected(mapOf("bsnsDivNm" to responseLabel), BusinessDivision.SERVICE).businessDivision shouldBe
                    BusinessDivision.SERVICE
            }
        }
    }

    @Test
    fun `오퍼레이션이 대분류를 나르지 않은 관측은 응답 라벨이 있어도 대분류가 null 이다 — 응답에서 추측하지 않는다`() {
        collected(mapOf("bsnsDivNm" to "용역"), division = null).businessDivision shouldBe null
    }

    @Test
    fun `새 칸 셋은 빈 값·공백뿐인 값에서 null 이고 항목은 탈락하지 않는다`() {
        listOf("", " ", "\t", "\u00A0", "\u3000", " \n ").forEach { blank ->
            val command =
                collected(
                    mapOf(
                        "srvceDivNm" to blank,
                        "pubPrcrmntClsfcNo" to blank,
                        "pubPrcrmntClsfcNm" to blank,
                        "mainCnsttyNm" to blank,
                    ),
                    BusinessDivision.SERVICE,
                )

            command.serviceDivision shouldBe null
            command.mainConstructionType shouldBe null
            command.businessCategory shouldBe null
        }
    }

    @Test
    fun `분류 코드가 비어 있으면 분류명이 있어도 업무구분을 만들지 않는다 — 코드 없는 라벨을 지어내지 않는다`() {
        collected(mapOf("pubPrcrmntClsfcNm" to "정보시스템 개발 서비스"), BusinessDivision.SERVICE)
            .businessCategory shouldBe null
    }

    @Test
    fun `분류명이 비어 있으면 코드만 산다 — 라벨은 null 이다`() {
        val category =
            collected(mapOf("pubPrcrmntClsfcNo" to "81111500", "pubPrcrmntClsfcNm" to " "), BusinessDivision.SERVICE)
                .businessCategory

        category?.code shouldBe CategoryCode.of("81111500")
        category?.label shouldBe null
    }

    @Test
    fun `업무구분의 코드·라벨은 같은 원천 쌍에서만 읽는다 — 다른 쌍의 라벨을 빌리지 않는다`() {
        val legacyAndClassification =
            TEST_POLICY.copy(
                fieldContracts =
                    KonepsFieldContractRegistry.of(
                        TEST_POLICY.fieldContracts.contracts +
                            operationalPolicy.fieldContracts.contracts.filter {
                                it.concept == FieldConcept.PUBLIC_PROCUREMENT_CLASS_CODE ||
                                    it.concept == FieldConcept.PUBLIC_PROCUREMENT_CLASS_NAME
                            },
                    ),
            )

        val legacyWins =
            collected(
                mapOf("bsnsDivCd" to "0411", "pubPrcrmntClsfcNo" to "81111500", "pubPrcrmntClsfcNm" to "분류명"),
                policy = legacyAndClassification,
            )
        legacyWins.businessCategory shouldBe BusinessCategory(CategoryCode.of("0411"), null)

        val fallsThrough =
            collected(
                mapOf("bsnsDivCd" to " ", "pubPrcrmntClsfcNo" to "81111500", "pubPrcrmntClsfcNm" to "분류명"),
                policy = legacyAndClassification,
            )
        fallsThrough.businessCategory shouldBe BusinessCategory(CategoryCode.of("81111500"), CategoryLabel("분류명"))
    }

    @Test
    fun `새 칸 셋은 계약이 가리키는 키에서만 읽는다 — canonicalize 에 키 리터럴이 없다`() {
        val retargeted =
            operationalPolicy.fieldContracts.contracts.map { contract ->
                when (contract.concept) {
                    FieldConcept.SERVICE_DIVISION -> contract.copy(rawName = RawKey("svcDiv"))
                    FieldConcept.MAIN_CONSTRUCTION_TYPE -> contract.copy(rawName = RawKey("mainType"))
                    FieldConcept.PUBLIC_PROCUREMENT_CLASS_CODE -> contract.copy(rawName = RawKey("clsCode"))
                    FieldConcept.PUBLIC_PROCUREMENT_CLASS_NAME -> contract.copy(rawName = RawKey("clsName"))
                    else -> contract
                }
            }
        val policy = operationalPolicy.copy(fieldContracts = KonepsFieldContractRegistry.of(retargeted))
        val oldKeys = serviceItem + mapOf("mainCnsttyNm" to "전기공사업")
        val newKeys =
            mapOf("svcDiv" to "새 용역구분", "mainType" to "새 주공종", "clsCode" to "99", "clsName" to "새 분류명")

        val fromOld = collected(oldKeys, BusinessDivision.SERVICE, policy)
        fromOld.serviceDivision shouldBe null
        fromOld.mainConstructionType shouldBe null
        fromOld.businessCategory shouldBe null

        val fromNew = collected(newKeys, BusinessDivision.SERVICE, policy)
        fromNew.serviceDivision shouldBe ServiceDivision.of("새 용역구분")
        fromNew.mainConstructionType shouldBe MainConstructionType.of("새 주공종")
        fromNew.businessCategory shouldBe BusinessCategory(CategoryCode.of("99"), CategoryLabel("새 분류명"))
    }

    @Test
    fun `운영 정책 계약 — 새 원문 키 넷은 공고 목록 오퍼레이션의 선택 필드이고 기존 업무구분 라벨 행은 그대로다`() {
        val added =
            setOf(
                FieldConcept.PUBLIC_PROCUREMENT_CLASS_CODE,
                FieldConcept.PUBLIC_PROCUREMENT_CLASS_NAME,
                FieldConcept.SERVICE_DIVISION,
                FieldConcept.MAIN_CONSTRUCTION_TYPE,
            )
        val contracts = operationalPolicy.fieldContracts.contracts.filter { it.concept in added }

        contracts.map { it.rawName.name } shouldContainExactlyInAnyOrder
            listOf("pubPrcrmntClsfcNo", "pubPrcrmntClsfcNm", "srvceDivNm", "mainCnsttyNm")
        contracts.forEach { contract ->
            withClue(contract.rawName.name) {
                contract.presentIn shouldBe setOf(SourceEndpoint.NOTICE_LIST)
                contract.nullability shouldBe FieldNullability.OPTIONAL
                contract.authoritative shouldBe true
                contract.effectiveFrom shouldBe EffectiveFrom.Initial
            }
        }
        // 분류 번호는 제로패딩을 보존하는 식별자다(int 변환 금지) — 나머지는 이름 텍스트.
        contracts.single { it.concept == FieldConcept.PUBLIC_PROCUREMENT_CLASS_CODE }.scale shouldBe
            FieldScale.IDENTIFIER
        contracts
            .filter { it.concept != FieldConcept.PUBLIC_PROCUREMENT_CLASS_CODE }
            .map { it.scale }
            .distinct() shouldBe listOf(FieldScale.OPAQUE_TEXT)
        operationalPolicy.fieldContracts
            .contractsFor(FieldConcept.BUSINESS_CATEGORY_LABEL)
            .map { it.rawName.name } shouldContainExactly listOf("bsnsDivNm")
    }

    @Test
    fun `새 원문 키 넷은 이제 미지 필드가 아니다 — 계약이 소비하는 키로 센다`() {
        val fields = identity + serviceItem + mapOf("mainCnsttyNm" to "전기공사업")
        val observation =
            RawNoticeObservation.of(fields.mapKeys { RawKey(it.key) }, SourceEndpoint.NOTICE_LIST, Instant.EPOCH)

        operationalPolicy.fieldContracts.unknownKeysIn(observation) shouldBe emptySet()
    }
}

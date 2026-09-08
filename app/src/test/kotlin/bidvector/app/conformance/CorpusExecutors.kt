package bidvector.app.conformance

import bidvector.qualification.LICENSE_QUALIFICATION_POLICY
import bidvector.qualification.LicenseEligibility
import bidvector.qualification.LicenseJudgement
import bidvector.qualification.LicenseName
import bidvector.qualification.LicenseQualificationPolicyData
import bidvector.qualification.LicenseValidity
import bidvector.qualification.LicenseVerdict
import bidvector.qualification.LmtGrpNo
import bidvector.qualification.LmtSno
import bidvector.qualification.OperatorLicenses
import bidvector.qualification.RequirementCollection
import bidvector.qualification.RequirementGroupId
import bidvector.qualification.RequirementRow
import bidvector.qualification.RequirementSourceField
import bidvector.qualification.UncertainReason
import bidvector.sharedkernel.BaseAmount
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.EstimatedAmount
import bidvector.sharedkernel.Fact
import bidvector.sharedkernel.Money
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.Rate
import bidvector.sharedkernel.Resolution
import bidvector.sharedkernel.VatTreatment
import bidvector.sharedkernel.compareKnownVat
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import tools.jackson.databind.JsonNode
import java.time.LocalDate

/*
 * M1/1B-c ④ · M1/1C — case id → executor dispatch 표와 1B·1C 계약 호출부.
 * `SharedKernelCorpusConformanceTest.kt` 가 fixture/JSON 인프라를 갖고, 이 파일은 「계약과
 * 무엇을 대조하는가」만 갖는다(크기 한도 회피가 아니라 관심사 분리 — 클래스 KDoc 참고).
 *
 * 계약에 없는 함수를 지어내지 않는다 — `Rate.ofPercent`/`ofFraction` 이 `Fact` 로
 * 감싸지 않고 제수를 방출하지 않는 것처럼, 이 파일도 계약이 실제로 내는 값만 조립한다.
 * sealed 타입의 variant 이름은 리플렉션(`::class.simpleName`) 대신 소진 `when` 으로 얻는다 —
 * 새 variant 가 생기면 컴파일이 여기서 먼저 깨진다.
 */

// ---- rate-unit 실행자 보조 — Rate 는 data class 라 구조적 동등이 internal 을 대신한다 ----

/**
 * `Rate.ofFraction`/`ofPercent` 둘 다 `normalized` 를 거치므로, 기대값 fraction 으로 만든
 * `Rate` 와 실제 산출 `Rate` 를 `==`(data class 구조적 동등) 로 비교하면 `internal fraction`
 * 을 이 모듈이 직접 읽지 않고도 값이 잠긴다. `verifiedPaths` 가 정확히 `$.rate.fraction`
 * 하나임을 먼저 확인한다 — manifest 가 조용히 다른 경로를 더하면 이 executor 가 놓친다.
 */
private fun assertRateFractionMatches(
    actualRate: Rate,
    expected: JsonNode,
    verifiedPaths: List<String>,
) {
    require(verifiedPaths == listOf("$.rate.fraction")) {
        "이 executor 는 \$.rate.fraction 하나만 다룬다 — manifest 의 실제 verified_paths: $verifiedPaths"
    }
    val expectedFraction = expected.atDollarPath("$.rate.fraction").decimalValue()
    val expectedRate = Rate.ofFraction(expectedFraction)
    withClue("\$.rate.fraction — actual=$actualRate expected=$expectedRate") {
        actualRate shouldBe expectedRate
    }
}

/**
 * verifier r1 M-2 — 입력의 `$.rawRate.declaredUnit` 이 percent/fraction 갈래를 정한다.
 * case 별 하드코딩을 두면 `rate-unit-005`(*"선언이 개연성을 이긴다"*)의 실질을 기계가
 * 재지 못한다 — `declaredUnit` 을 바꿔도 결과가 그대로면 이 executor 가 놓친 것이다.
 */
private fun rateFromDeclaredUnit(input: JsonNode): Rate {
    val numeric = input.atDollarPath("$.rawRate.numeric").decimalValue()
    return when (val declaredUnit = input.atDollarPath("$.rawRate.declaredUnit").asString()) {
        "percent" -> Rate.ofPercent(numeric)
        "fraction" -> Rate.ofFraction(numeric)
        else -> error("이 corpus 는 declaredUnit='$declaredUnit' 를 다루지 않는다 — 지원: percent, fraction")
    }
}

/** `Rate` 값 동등 비교로 대조하는 case — `internal fraction` 을 읽지 않는다(D5(d)). */
internal val RATE_EXECUTORS: Map<String, (JsonNode, JsonNode, List<String>) -> Unit> =
    mapOf(
        "rate-unit-001" to { input, expected, verifiedPaths ->
            assertRateFractionMatches(rateFromDeclaredUnit(input), expected, verifiedPaths)
        },
        "rate-unit-002" to { input, expected, verifiedPaths ->
            assertRateFractionMatches(rateFromDeclaredUnit(input), expected, verifiedPaths)
        },
        "rate-unit-005" to { input, expected, verifiedPaths ->
            assertRateFractionMatches(rateFromDeclaredUnit(input), expected, verifiedPaths)
        },
    )

// ---- money-basis value executor 보조 — Money 조립 ----

/** 이 corpus 의 어느 case 도 `noticeRevision` 을 값으로 주장하지 않는다 — 없으면 쓰는 자리표시자. */
private val UNASSERTED_NOTICE_REVISION = NoticeRound.of("000")

/**
 * `provenance` 토큰 문자열 → 계약 값. 형제 노드(`siblingNode`)는 `Published` 의
 * `noticeRevision`·`FilledFromBudgetKey` 의 `key` 처럼 variant 별 부가 성분을 읽는 자리다.
 * `noticeRevision` 은 [NoticeRound] 다(M3/3A D-3A-0 (a)) — fixture 는 이미 제로패딩 문자열
 * (`"000"`)을 준다, `toInt` 접힘을 두지 않는다(R-QUAL-05).
 */
private fun provenanceFromToken(
    name: String,
    siblingNode: JsonNode,
): Provenance =
    when (name) {
        "Published" -> {
            val noticeRevisionNode = siblingNode.path("noticeRevision")
            val noticeRevision =
                if (noticeRevisionNode.isMissingNode || noticeRevisionNode.isNull) {
                    UNASSERTED_NOTICE_REVISION
                } else {
                    NoticeRound.of(noticeRevisionNode.asString())
                }
            Provenance.Published(noticeRevision)
        }

        "DerivedFromOpening" -> {
            Provenance.DerivedFromOpening
        }

        "FilledFromBudgetKey" -> {
            Provenance.FilledFromBudgetKey(siblingNode.path("key").asString())
        }

        "CopiedFromBaseAmount" -> {
            Provenance.CopiedFromBaseAmount
        }

        "OperatorDeclared" -> {
            Provenance.OperatorDeclared
        }

        "Undeclared" -> {
            Provenance.Undeclared
        }

        else -> {
            error("이 corpus 가 다루지 않는 provenance 토큰: $name")
        }
    }

/**
 * verifier r1 M-1 과 같은 원칙 — `provenance` 누락을 `Undeclared` 로 접지 않는다. 이 helper 를
 * 쓰는 money-basis-002·005 의 입력은 전부 명시 선언(`OperatorDeclared`·`Published`)이라
 * 이 요구가 실제로 실패를 내는 자리는 없다 — 관대한 fallback 을 남겨 두지 않는 것 자체가
 * 목적이다.
 */
private fun provenanceFrom(node: JsonNode): Provenance {
    val provenanceNode = node.path("provenance")
    require(!provenanceNode.isMissingNode && !provenanceNode.isNull) {
        "provenance 는 명시 선언이어야 한다 — 이 corpus 는 누락을 Undeclared 로 지어내지 않는다"
    }
    return provenanceFromToken(provenanceNode.asString(), node)
}

/** `Provenance` variant 이름 — 리플렉션 대신 소진 `when`(sealed 라 컴파일러가 소진을 강제한다). */
private fun provenanceName(provenance: Provenance): String =
    when (provenance) {
        is Provenance.Published -> "Published"
        Provenance.DerivedFromOpening -> "DerivedFromOpening"
        is Provenance.FilledFromBudgetKey -> "FilledFromBudgetKey"
        Provenance.CopiedFromBaseAmount -> "CopiedFromBaseAmount"
        Provenance.OperatorDeclared -> "OperatorDeclared"
        Provenance.Undeclared -> "Undeclared"
    }

private fun moneyFrom(node: JsonNode): Money {
    val won = node.path("amount").asLong()
    val currency = Currency.valueOf(node.path("currency").asString())
    val vatTreatment = VatTreatment.valueOf(node.path("vatTreatment").asString())
    val provenance = provenanceFrom(node)
    return when (val basis = node.path("basis").asString()) {
        "BASE_AMOUNT" -> BaseAmount(won, currency, vatTreatment, provenance)
        "ESTIMATED" -> EstimatedAmount(won, currency, vatTreatment, provenance)
        else -> error("이 corpus 는 basis='$basis' 를 다루지 않는다 — 지원 축: BASE_AMOUNT, ESTIMATED")
    }
}

private fun compareBaseAmounts(
    left: Money,
    right: Money,
): Fact<Int> {
    require(left is BaseAmount && right is BaseAmount) { "이 corpus 의 money-basis value executor 는 BASE_AMOUNT 쌍만 다룬다" }
    return compareKnownVat(left, right)
}

/** `Fact<Int>` 상태 축 — 공개 sealed 타입의 소진 `when` 이라 리플렉션이 필요 없다. */
private fun factStateName(fact: Fact<Int>): String =
    when (fact) {
        is Fact.Known -> "Known"
        is Fact.Absent -> "Absent"
    }

private fun factComparisonProjection(
    fact: Fact<Int>,
    left: Money,
    right: Money,
): Map<String, Any?> {
    val projection =
        mutableMapOf<String, Any?>(
            "fact" to factStateName(fact),
            "comparedBases" to listOf(left.basis.name, right.basis.name),
        )
    if (fact is Fact.Absent) projection["reasonCode"] = fact.reason.name
    return projection
}

private val MONEY_BASIS_VALUE_EXECUTORS: Map<String, (JsonNode) -> Map<String, Any?>> =
    mapOf(
        "money-basis-002" to { input ->
            val left = moneyFrom(input.atDollarPath("$.operatorFilter"))
            val right = moneyFrom(input.atDollarPath("$.noticeAmount"))
            factComparisonProjection(compareBaseAmounts(left, right), left, right)
        },
        "money-basis-005" to { input ->
            val operands = input.atDollarPath("$.operands")
            val left = moneyFrom(operands.path(0))
            val right = moneyFrom(operands.path(1))
            factComparisonProjection(compareBaseAmounts(left, right), left, right)
        },
        // verifier r1 M-1 — null → UNKNOWN/Undeclared 로 접는 fallback 을 없앤다. 그 접기가
        // 있으면 「legacy 유래 행을 정의상 기본값으로 자동 태깅하지 않는다」의 실질을 계약이
        // 아니라 runner 상수가 지게 된다(재현: fallback 을 INCLUSIVE 로 바꿔도 그 case 만
        // FAILED, shared-kernel 무변경 — 계약이 아니라 runner 가 답을 정하고 있었다는 뜻).
        // 입력이 **명시 선언**(UNKNOWN·Undeclared)을 갖고 있어야 하고, 없으면 지어내지 않고
        // 실패한다. 이 case 는 **선언 값 왕복(value executor)** 만 겐다 — compile fixture
        // 위임은 걷었다: fixture 7(`vat-fixed-money-no-vat-arg`/`explicit-vat`)은
        // `YegaAmount` 가 **명시 VAT 인자를 거부**함을 재는 다른 명제이지 `BaseAmount` 의
        // `vatTreatment`/`provenance` 가 기본값 없는 필수 파라미터라는 사실을 재지 않는다
        // (curator `fixtures.md` §7-8 실측). 그 축을 재는 컴파일 fixture 는 지금 없고,
        // 새 fixture 신설은 이 slice `out_of_scope` 라 알려진 제한으로만 등재한다.
        "money-basis-006" to { input ->
            val row = input.atDollarPath("$.row")
            require(row.path("conceptSlot").asString() == "BASE_AMOUNT") { "이 case 는 BASE_AMOUNT 슬롯만 다룬다" }
            val won = row.path("amount").asLong()
            val currency = Currency.valueOf(row.path("currency").asString())
            val vatTreatmentNode = row.path("declaredVatTreatment")
            require(!vatTreatmentNode.isMissingNode && !vatTreatmentNode.isNull) {
                "이 case 는 declaredVatTreatment 가 명시 선언(예: UNKNOWN)이어야 한다 — null 을 UNKNOWN 으로 지어내지 않는다"
            }
            val vatTreatment = VatTreatment.valueOf(vatTreatmentNode.asString())
            val provenanceNode = row.path("declaredProvenance")
            require(!provenanceNode.isMissingNode && !provenanceNode.isNull) {
                "이 case 는 declaredProvenance 가 명시 선언(예: Undeclared)이어야 한다 — null 을 Undeclared 로 지어내지 않는다"
            }
            val provenance = provenanceFromToken(provenanceNode.asString(), row)
            val baseAmount = BaseAmount(won, currency, vatTreatment, provenance)
            mapOf(
                "vatTreatment" to baseAmount.vatTreatment.name,
                "provenance" to provenanceName(baseAmount.provenance),
            )
        },
    )

// ---- license value executor 보조 — M1/1C, qualification 공개 API 호출 → projection ----

/**
 * OPEN-QUAL-07 — 내용이 비어 있는 정책을 실제로 resolve 해서 쓴다(지어낸 리터럴이 아니다).
 * `judge` 가 `Resolution.Resolved` 하나를 받으므로(verifier r1 F-5) 값과 version 이
 * 여기서부터 같은 객체로 나온다 — runner 가 값만 꺼내고 version 을 fixture 문자열로 따로
 * 만드는 경로 자체가 없어졌다.
 *
 * verifier r1 F-8 — `LocalDate.now()` 는 이 정책에 `Initial` 하나뿐인 지금은 항상 성립하지만
 * 미래 일자 항목이 생기면 test 가 벽시계에 의존하게 되고, `as Resolution.Resolved` 강제
 * 캐스트는 그때 `ClassCastException` 으로 죽는다. 고정 기준일 + 소진 `when` 으로 두 문제를
 * 함께 없앤다 — `NotApplicable` 이 나오면 (지금은 나올 수 없지만) 캐스트 실패 대신 이 자리를
 * 정확히 지목하는 메시지로 멈춘다.
 */
private val LICENSE_POLICY_REFERENCE_DATE: LocalDate = LocalDate.of(2026, 8, 30)

private val LICENSE_RESOLVED_POLICY: Resolution.Resolved<LicenseQualificationPolicyData> =
    when (val resolution = LICENSE_QUALIFICATION_POLICY.resolve(LICENSE_POLICY_REFERENCE_DATE)) {
        is Resolution.Resolved -> {
            resolution
        }

        is Resolution.NotApplicable -> {
            error(
                "LICENSE_QUALIFICATION_POLICY 가 $LICENSE_POLICY_REFERENCE_DATE 에 해석되지 않는다 " +
                    "— reason=${resolution.reason}",
            )
        }
    }

/**
 * verifier r1 F-7 — 물리 행 하나가 `lcnsLmtNm`(항상)과 `permsnIndstrytyList`(선택, 배열)를
 * 동시에 가질 수 있다(조사 §3 — 동반 필드, 별도 행 아님). 이전 판은 `sourceField` 를
 * `LcnsLmtNm` 으로 하드코딩해 `permsnIndstrytyList` 를 조용히 무시했다 — 지어내지 않기
 * 원칙(1B-c 관례) 위반이었다. 이제 실제로 읽어 두 번째 `RequirementRow.Parsed`(같은
 * `groupNo`, `sourceField=PermsnIndstrytyList`)를 만든다. 배열이 아니거나 빈 배열이면
 * 이 corpus 가 다루지 않는 형태이니 지어내지 않고 멈춘다.
 */
private fun requirementRowsFrom(rowNode: JsonNode): List<RequirementRow.Parsed> {
    val groupNoNode = rowNode.path("lmtGrpNo")
    val groupNo = if (groupNoNode.isMissingNode || groupNoNode.isNull) null else LmtGrpNo(groupNoNode.asString())
    val serialNo = LmtSno(rowNode.path("lmtSno").asString())
    val restrictedRow =
        RequirementRow.Parsed(
            groupNo = groupNo,
            serialNo = serialNo,
            sourceField = RequirementSourceField.LcnsLmtNm,
            licenseNames = listOf(LicenseName(rowNode.path("lcnsLmtNm").asString())),
        )
    val permsnNode = rowNode.path("permsnIndstrytyList")
    if (permsnNode.isMissingNode || permsnNode.isNull) return listOf(restrictedRow)
    require(permsnNode.isArray) { "permsnIndstrytyList 는 배열이어야 한다 — 이 corpus 가 다루지 않는 형태: $permsnNode" }
    val permsnNames = permsnNode.values().map { LicenseName(it.asString()) }
    require(permsnNames.isNotEmpty()) { "permsnIndstrytyList 가 빈 배열이다 — 이 corpus 는 다루지 않는다" }
    val permsnRow =
        RequirementRow.Parsed(
            groupNo = groupNo,
            serialNo = serialNo,
            sourceField = RequirementSourceField.PermsnIndstrytyList,
            licenseNames = permsnNames,
        )
    return listOf(restrictedRow, permsnRow)
}

/** 「행이 없다」(`notice.licenseLimitRows` null)와 「수집이 실패했다」(`notice.collection.status`)는 다른 사유다. */
private fun requirementCollectionFrom(noticeNode: JsonNode): RequirementCollection {
    val collectionNode = noticeNode.path("collection")
    val collectionFailed =
        !collectionNode.isMissingNode && !collectionNode.isNull && collectionNode.path("status").asString() == "FAILED"
    val rowsNode = noticeNode.path("licenseLimitRows")
    return when {
        collectionFailed -> RequirementCollection.CollectionFailed
        rowsNode.isMissingNode || rowsNode.isNull -> RequirementCollection.DataAbsent
        else -> RequirementCollection.Collected(rowsNode.values().flatMap(::requirementRowsFrom))
    }
}

/**
 * `operatorLicenses` 는 대부분 문자열 배열이지만 license-012 는 `{name, validUntil}` 객체
 * 배열이다 — 유효기간은 이 slice 가 검증하지 않으므로(U-7) `name` 만 읽는다.
 */
private fun operatorLicensesFrom(input: JsonNode): OperatorLicenses {
    val node = input.path("operatorLicenses")
    if (node.isMissingNode || node.isNull) return OperatorLicenses.NotDeclared
    val names =
        node.values().map { element ->
            LicenseName(if (element.isObject) element.path("name").asString() else element.asString())
        }
    return OperatorLicenses.Declared(names)
}

private fun requirementGroupIdKey(id: RequirementGroupId): String =
    when (id) {
        is RequirementGroupId.Numbered -> id.groupNo.value
        RequirementGroupId.Ungrouped -> "__ungrouped__"
    }

private fun licenseVerdictName(verdict: LicenseVerdict): String =
    when (verdict) {
        is LicenseVerdict.Eligible -> "Eligible"
        is LicenseVerdict.Ineligible -> "Ineligible"
        is LicenseVerdict.Uncertain -> "Uncertain"
    }

private fun uncertainReasonName(reason: UncertainReason): String =
    when (reason) {
        UncertainReason.RequirementDataAbsent -> "RequirementDataAbsent"
        UncertainReason.RequirementUnparsable -> "RequirementUnparsable"
        UncertainReason.OperatorLicensesNotDeclared -> "OperatorLicensesNotDeclared"
        UncertainReason.CollectionFailed -> "CollectionFailed"
        UncertainReason.PermittedIndustryCombinationRuleUndecided -> "PermittedIndustryCombinationRuleUndecided"
    }

private fun requirementSourceFieldName(field: RequirementSourceField): String =
    when (field) {
        RequirementSourceField.LcnsLmtNm -> "lcnsLmtNm"
        RequirementSourceField.PermsnIndstrytyList -> "permsnIndstrytyList"
    }

/**
 * license-005(결측 그룹) 전용 파생값 — 조사 §1 어휘 불일치 ③. 커널 필드가 아니라 `Ungrouped`
 * 그룹의 존재에서 낸다(verdict 가 그 그룹을 어느 쪽으로 접었든 존재 자체가 신호다).
 */
private fun foldedUngrouped(verdict: LicenseVerdict): Boolean =
    when (verdict) {
        is LicenseVerdict.Eligible -> RequirementGroupId.Ungrouped in verdict.satisfiedGroups
        is LicenseVerdict.Ineligible -> RequirementGroupId.Ungrouped in verdict.missingByGroup.keys
        is LicenseVerdict.Uncertain -> false
    }

/**
 * verifier r1 F-6 — `$.expiryEvaluated` 를 runner 리터럴 `false` 가 답하면 그 경로는 계약이
 * 내는 값을 재지 않는다(1B-c verifier r1 M-1 과 같은 갈래). `LicenseValidity` 를 소진
 * `when` 으로 소비해서 낸다 — 지금은 `NotVerified` 단일 variant라 값이 같지만, `Verified`
 * variant 가 생기면 이 `when` 이 컴파일 에러로 먼저 깨져 리터럴이 조용히 낡는 것을 막는다.
 */
private fun expiryEvaluated(validity: LicenseValidity): Boolean =
    when (validity) {
        LicenseValidity.NotVerified -> false
    }

private fun licenseJudgementProjection(judgement: LicenseJudgement): Map<String, Any?> {
    val verdict = judgement.verdict
    val projection =
        mutableMapOf<String, Any?>(
            "verdict" to licenseVerdictName(verdict),
            "requiredLicenses" to judgement.requiredLicenses?.map { it.value },
            "missingByGroup" to
                judgement.missingByGroup.entries.associate { (id, names) ->
                    requirementGroupIdKey(id) to names.map { it.value }
                },
            "unparsableRowCount" to judgement.unparsableRowCount,
            "requirementSourceFields" to judgement.requirementSourceFields.map(::requirementSourceFieldName),
            "policyVersion" to judgement.policyVersion.source,
            "licenseValidityUnverified" to (judgement.validity is LicenseValidity.NotVerified),
            "expiryEvaluated" to expiryEvaluated(judgement.validity),
            "foldedUngroupedRowsIntoSingleAndGroup" to foldedUngrouped(verdict),
        )
    when (verdict) {
        is LicenseVerdict.Eligible -> {
            projection["satisfiedGroups"] = verdict.satisfiedGroups.map(::requirementGroupIdKey)
        }

        is LicenseVerdict.Uncertain -> {
            projection["uncertainReason"] = uncertainReasonName(verdict.reason)
        }

        is LicenseVerdict.Ineligible -> {
            // 사유 있는 미충족은 이미 projection["missingByGroup"]에 있다 — 더할 것이 없다.
        }
    }
    return projection
}

/** authoritative 8건(002·003·004·005·006·007·009·012) 전부가 같은 executor 를 공유한다 — 입력 형태가 같다. */
private fun licenseExecutor(input: JsonNode): Map<String, Any?> {
    val collection = requirementCollectionFrom(input.path("notice"))
    val operatorLicenses = operatorLicensesFrom(input)
    val judgement = LicenseEligibility.judge(collection, operatorLicenses, LICENSE_RESOLVED_POLICY)
    return licenseJudgementProjection(judgement)
}

internal val LICENSE_AUTHORITATIVE_CASE_IDS =
    listOf(
        "license-002",
        "license-003",
        "license-004",
        "license-005",
        "license-006",
        "license-007",
        "license-009",
        "license-012",
    )

internal val LICENSE_EXECUTORS: Map<String, (JsonNode) -> Map<String, Any?>> =
    LICENSE_AUTHORITATIVE_CASE_IDS.associateWith { ::licenseExecutor }

// ---- dispatch 표 ----

/**
 * money-basis(1B-c)·license(1C)·provenance/floor(1D)·strategy-watch·strategy-validation·
 * money-basis-003(1E)·koneps-collection(3A) 축의 value executor 를 하나의 dispatch 표로
 * 합친다. koneps-collection 은 이제 27 case 전건을 담는다 — 세 파일로 나뉜다
 * (`KonepsCollectionExecutors.kt`·`KonepsCollectionAccountingExecutors.kt`·
 * `KonepsCollectionDefectFixExecutors.kt`, 500줄 한도가 아니라 관심사 분리).
 * `KONEPS_COLLECTION_PENDING_CAPABILITY`(3A 잔여 일괄 ②)는 이제 빈 집합이다.
 */
internal val VALUE_EXECUTORS: Map<String, (JsonNode) -> Map<String, Any?>> =
    MONEY_BASIS_VALUE_EXECUTORS +
        LICENSE_EXECUTORS +
        PROVENANCE_FLOOR_EXECUTORS +
        STRATEGY_EXECUTORS +
        KONEPS_COLLECTION_FIELD_EXECUTORS +
        KONEPS_COLLECTION_ACCOUNTING_EXECUTORS +
        KONEPS_COLLECTION_DEFECT_FIX_EXECUTORS +
        STRATEGY_EDIT_EXECUTORS

/**
 * compile-fixture 위임 case → shared-kernel `compile-fixtures` 의 fixture 번호. 실제
 * 컴파일 실패 단언은 `CompileFailureHarnessTest`(`gate.tests.shared-kernel`)의 몫이고,
 * 이 runner 는 그 fixture 가족(negative/positive/mutant-N)이 존재함만 확인한다
 * (`assertCompileFixtureFamilyExists`) — basis 교차 비교(11)와 단위 미선언 `Rate`(12)
 * 는 둘 다 「컴파일 시점에 표현 불가」라는 같은 성질이다.
 */
internal val COMPILE_DELEGATION_FIXTURES: Map<String, Int> =
    mapOf(
        "money-basis-001" to 11,
        "money-basis-004" to 11,
        "rate-unit-003" to 12,
        "rate-unit-004" to 12,
    )

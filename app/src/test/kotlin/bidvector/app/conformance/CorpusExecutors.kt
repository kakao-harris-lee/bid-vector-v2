package bidvector.app.conformance

import bidvector.sharedkernel.BaseAmount
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.EstimatedAmount
import bidvector.sharedkernel.Fact
import bidvector.sharedkernel.Money
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.Rate
import bidvector.sharedkernel.VatTreatment
import bidvector.sharedkernel.compareKnownVat
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import tools.jackson.databind.JsonNode

/*
 * M1/1B-c ④ — case id → executor dispatch 표와 1B 계약 호출부.
 * `SharedKernelCorpusConformanceTest.kt` 가 fixture/JSON 인프라를 갖고, 이 파일은 「계약과
 * 무엇을 대조하는가」만 갖는다(크기 한도 회피가 아니라 관심사 분리 — 클래스 KDoc 참고).
 *
 * 계약에 없는 함수를 지어내지 않는다 — `Rate.ofPercent`/`ofFraction` 이 `Fact` 로
 * 감싸지 않고 제수를 방출하지 않는 것처럼, 이 파일도 계약이 실제로 내는 값만 조립한다.
 * `Provenance` variant 이름은 리플렉션(`::class.simpleName`) 대신 소진 `when` 으로 얻는다 —
 * sealed 라 새 variant 가 생기면 컴파일이 여기서 먼저 깨진다.
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

// ---- money-basis value executor 보조 — Money 조립 ----

/** 이 corpus 의 어느 case 도 `noticeRevision` 을 값으로 주장하지 않는다 — 없으면 쓰는 자리표시자. */
private const val UNASSERTED_NOTICE_REVISION = 0

/**
 * `provenance` 토큰 문자열 → 계약 값. 형제 노드(`siblingNode`)는 `Published` 의
 * `noticeRevision`·`FilledFromBudgetKey` 의 `key` 처럼 variant 별 부가 성분을 읽는 자리다.
 */
private fun provenanceFromToken(
    name: String,
    siblingNode: JsonNode,
): Provenance =
    when (name) {
        "Published" -> {
            val noticeRevision =
                siblingNode.path("noticeRevision").asString(null)?.toIntOrNull() ?: UNASSERTED_NOTICE_REVISION
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

// ---- dispatch 표 ----

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

internal val VALUE_EXECUTORS: Map<String, (JsonNode) -> Map<String, Any?>> =
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
        // 실패한다. 「정의상 기본값 없음」의 실질은 계약의 구성적 사실(생성자에 기본값 없는
        // 필수 파라미터 — compile fixture 7 `vat-fixed-money-no-vat-arg`/`explicit-vat`
        // 가족이 증명)이라 그 위임도 함께 건다 — 이 case 는 「선언 값 왕복(value) +
        // compile-fixture 7 위임」 둘을 겸한다.
        "money-basis-006" to { input ->
            assertCompileFixtureFamilyExists(NO_DEFAULT_VAT_PROVENANCE_FIXTURE)
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

/**
 * `money-basis-006` 이 함께 거는 compile-fixture — `vatTreatment`·`provenance` 가 기본값
 * 없는 필수 파라미터라는 구성적 사실(`vat-fixed-money-no-vat-arg`/`explicit-vat` 가족).
 * `COMPILE_DELEGATION_FIXTURES` 에 넣지 않는 이유: 그 표는 「대조 대상이 컴파일 실패뿐인」
 * case 전용이고 006 은 value executor 를 겸하기 때문이다 — 표 하나에 성질이 둘인 항목을
 * 두면 `dispatch 표 밖의 authoritative case 가 없다` test 의 「셋 중 하나」가정이 깨진다.
 */
private const val NO_DEFAULT_VAT_PROVENANCE_FIXTURE = 7

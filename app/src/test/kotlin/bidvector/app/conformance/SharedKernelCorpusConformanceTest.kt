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
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.yaml.snakeyaml.Yaml
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import java.io.File

/**
 * M1/1B-c ④(`scope.md` 「이 slice 가 하는 일」④, decision 22 — D5(d)) — corpus 소비 테스트
 * (conformance runner). `rate-unit`·`money-basis` 축의 `authoritative` case 를 실제 1B
 * 계약(`shared-kernel`) 위에서 실행해 `fixtures/manifest.yaml` 의 `verified_paths` 로
 * 대조한다. 지금까지 이 축을 지키는 것은 Python 스윕(계약 강건성 — 변이체가 통과하지
 * 않는다)뿐이었고, **V2 산출을 기대값과 대조하는 실행자가 없었다.**
 *
 * **shared-kernel `internal` 을 읽지 않는다 — 공개 API 만 쓴다.** D5(b′)(testFixtures)는
 * 하네스 게이트 셋(`packageOwnershipGate`·`sourceSetLayoutGate`·`ArchitectureGateTest` 의
 * ArchUnit 프로덕션 스캔)이 `testFixtures` source set 을 인식하지 못해 실측 4 failures 로
 * 깨졌다(`checklist.md` 알려진 제한). `Rate` 가 `internal fraction` 만 나르는 문제
 * (verifier r1 L-4)는 `Rate` 가 `data class` 라는 사실로 우회한다 — 기대값으로 만든
 * `Rate.ofFraction(expected)` 과 실제 산출을 `==`(data class 구조적 동등)로 비교하면
 * `internal` 성분을 이 모듈이 직접 읽지 않고도 값이 잠긴다(양쪽 다 `normalized` 를 거치므로
 * P-1a 왕복 성질이 동등을 보증한다).
 *
 * **dispatch 는 case id → executor 명시 표 셋이 전부다** — [RATE_EXECUTORS](`Rate` 값 동등
 * 비교), [VALUE_EXECUTORS](그 밖의 계약 함수를 실제로 불러 대조), [COMPILE_DELEGATION_FIXTURES]
 * (compile-fixture 위임, 대조 대상이 컴파일 실패라 이 runner 는 fixture 가족의 존재만
 * 확인한다). **표 밖의 authoritative case 가 있으면 실패한다**
 * ([`dispatch 표 밖의 authoritative case 가 없다`]) — 새 case 가 조용히 빠지지 못한다
 * (위협 모델 우회 (4)).
 *
 * **계약에 없는 함수를 지어내지 않는다** — `Rate.ofPercent`/`ofFraction` 이 `Fact` 로
 * 감싸지 않고 제수를 방출하지 않는 것처럼, 이 runner 도 계약이 실제로 내는 값만 조립한다.
 * `Provenance` variant 이름은 리플렉션(`::class.simpleName`) 대신 소진 `when` 으로 얻는다 —
 * sealed 라 새 variant 가 생기면 컴파일이 이 파일에서 먼저 깨진다.
 */
class SharedKernelCorpusConformanceTest {
    @TestFactory
    fun `authoritative rate-unit·money-basis case 가 1B 계약과 대조된다`(): List<DynamicTest> =
        targetCases().map { case -> dynamicTest(case.id) { runCase(case) } }

    /** 위협 모델 우회 (4) — 소비 테스트를 실행 집합에서 빼거나 dispatch 표에서 새 case 를 빠뜨리는 것을 막는다. */
    @Test
    fun `dispatch 표 밖의 authoritative case 가 없다`() {
        val undispatched =
            targetCases().map { it.id }.filterNot { id ->
                id in RATE_EXECUTORS || id in VALUE_EXECUTORS || id in COMPILE_DELEGATION_FIXTURES
            }
        withClue("dispatch 표에 없는 authoritative case: $undispatched") {
            undispatched shouldBe emptyList()
        }
    }

    /** money-basis-003(`OPEN-1BC-STR16` 이월)만 남아야 한다 — 「④가 넷을 닫는다」의 인계 경계. */
    @Test
    fun `1B 축 insufficient-evidence 이월은 money-basis-003 하나뿐이다`() {
        val ids =
            allCases()
                .filter { it.domain in TARGET_DOMAINS && it.classification == "insufficient-evidence" }
                .map { it.id }
        ids shouldBe listOf("money-basis-003")
    }

    private fun runCase(case: ManifestCase) {
        val expected = readFixtureJson(case.expectedFile)
        when {
            case.id in COMPILE_DELEGATION_FIXTURES -> {
                assertCompileFixtureFamilyExists(COMPILE_DELEGATION_FIXTURES.getValue(case.id))
                val actual = MAPPER.valueToTree<JsonNode>(REPRESENTABLE_FALSE)
                case.verifiedPaths.forEach { path -> assertPathEquals(actual, expected, path) }
            }

            case.id in RATE_EXECUTORS -> {
                val input = readFixtureJson(case.inputFile)
                RATE_EXECUTORS.getValue(case.id).invoke(input, expected, case.verifiedPaths)
            }

            else -> {
                val executor = VALUE_EXECUTORS[case.id] ?: error("dispatch 표에 없는 case: ${case.id}")
                val input = readFixtureJson(case.inputFile)
                val actual = MAPPER.valueToTree<JsonNode>(executor(input))
                case.verifiedPaths.forEach { path -> assertPathEquals(actual, expected, path) }
            }
        }
    }
}

private val TARGET_DOMAINS = setOf("rate-unit", "money-basis")

private const val MANIFEST_PROPERTY = "bidvector.fixtures.manifest"
private const val FIXTURES_ROOT_PROPERTY = "bidvector.fixtures.root"
private const val COMPILE_FIXTURES_DIR_PROPERTY = "bidvector.sharedkernel.compile-fixtures"

/**
 * `compareKnownVat` 가 성립하려면 두 인자가 같은 basis 여야 하고, 그때는 애초에
 * compile-fixture 위임이 아니다 — 이 corpus 의 basis 교차 쌍은 값을 계산하지 않는다.
 */
private val REPRESENTABLE_FALSE: Map<String, Any?> = mapOf("representable" to false)

private val MAPPER: ObjectMapper =
    JsonMapper
        .builder()
        .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
        .build()

private data class ManifestCase(
    val id: String,
    val domain: String,
    val classification: String,
    val verifiedPaths: List<String>,
    val inputFile: String,
    val expectedFile: String,
)

private fun allCases(): List<ManifestCase> {
    val manifestPath = System.getProperty(MANIFEST_PROPERTY) ?: error("시스템 속성 '$MANIFEST_PROPERTY' 가 없다")

    @Suppress("UNCHECKED_CAST")
    val root =
        File(manifestPath).reader(Charsets.UTF_8).use { reader -> Yaml().load(reader) as Map<String, Any?> }

    @Suppress("UNCHECKED_CAST")
    val rawCases = root.getValue("cases") as List<Map<String, Any?>>
    return rawCases.map(::toManifestCase)
}

@Suppress("UNCHECKED_CAST")
private fun toManifestCase(raw: Map<String, Any?>): ManifestCase =
    ManifestCase(
        id = raw.getValue("id") as String,
        domain = raw.getValue("domain") as String,
        classification = raw.getValue("classification") as String,
        verifiedPaths = (raw["verified_paths"] as? List<String>).orEmpty(),
        inputFile = raw.getValue("input_file") as String,
        expectedFile = raw.getValue("expected_file") as String,
    )

private fun targetCases(): List<ManifestCase> =
    allCases().filter { it.domain in TARGET_DOMAINS && it.classification == "authoritative" }

private fun readFixtureJson(manifestRelativePath: String): JsonNode {
    val fixturesRoot = System.getProperty(FIXTURES_ROOT_PROPERTY) ?: error("시스템 속성 '$FIXTURES_ROOT_PROPERTY' 가 없다")
    val file = File(fixturesRoot, manifestRelativePath.removePrefix("fixtures/"))
    return MAPPER.readTree(file)
}

// ---- verified_paths 대조 — JSON 경로 walker + BigDecimal 인지 비교 ----

private fun JsonNode.atDollarPath(path: String): JsonNode =
    path.removePrefix("$.").split(".").fold(this) { node, segment -> node.path(segment) }

private fun JsonNode.canonical(): Any? =
    when {
        isMissingNode || isNull -> null
        isBoolean -> booleanValue()
        isNumber -> decimalValue()
        isString -> asString()
        isArray -> values().map { it.canonical() }
        else -> error("이 runner 가 다루지 않는 JSON 노드 형태: $this")
    }

private fun valuesMatch(
    actual: Any?,
    expected: Any?,
): Boolean =
    when {
        actual == null && expected == null -> {
            true
        }

        actual is java.math.BigDecimal && expected is java.math.BigDecimal -> {
            actual.compareTo(expected) == 0
        }

        actual is List<*> && expected is List<*> -> {
            actual.size == expected.size && actual.zip(expected).all { (a, e) -> valuesMatch(a, e) }
        }

        else -> {
            actual == expected
        }
    }

private fun assertPathEquals(
    actual: JsonNode,
    expected: JsonNode,
    path: String,
) {
    val actualValue = actual.atDollarPath(path).canonical()
    val expectedValue = expected.atDollarPath(path).canonical()
    withClue("경로 $path — actual=$actualValue expected=$expectedValue") {
        valuesMatch(actualValue, expectedValue) shouldBe true
    }
}

private fun assertCompileFixtureFamilyExists(fixtureNumber: Int) {
    val dirPath =
        System.getProperty(COMPILE_FIXTURES_DIR_PROPERTY) ?: error("시스템 속성 '$COMPILE_FIXTURES_DIR_PROPERTY' 가 없다")
    val dir = File(dirPath)
    listOf("negative-$fixtureNumber-", "positive-$fixtureNumber-", "mutant-$fixtureNumber-").forEach { prefix ->
        val matches = dir.listFiles { file -> file.name.startsWith(prefix) && file.name.endsWith(".kt.txt") }.orEmpty()
        withClue("compile-fixtures(${dir.absolutePath})에 '$prefix*.kt.txt' 가 있어야 한다") {
            matches.isNotEmpty() shouldBe true
        }
    }
}

// ---- rate-unit 실행자 보조 — Rate 는 data class 라 구조적 동등이 internal 을 대신한다 ----

/**
 * `Rate.ofFraction`/`ofPercent` 둘 다 [normalized] 를 거치므로, 기대값 fraction 으로 만든
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

// ---- money-basis value executor 보조 — Money 조립 ----

/** 이 corpus 의 어느 case 도 `noticeRevision` 을 값으로 주장하지 않는다 — 없으면 쓰는 자리표시자. */
private const val UNASSERTED_NOTICE_REVISION = 0

private fun provenanceFrom(node: JsonNode): Provenance {
    val provenanceNode = node.path("provenance")
    if (provenanceNode.isMissingNode || provenanceNode.isNull) return Provenance.Undeclared
    return when (val name = provenanceNode.asString()) {
        "Published" -> {
            val noticeRevision = node.path("noticeRevision").asString(null)?.toIntOrNull() ?: UNASSERTED_NOTICE_REVISION
            Provenance.Published(noticeRevision)
        }

        "DerivedFromOpening" -> {
            Provenance.DerivedFromOpening
        }

        "FilledFromBudgetKey" -> {
            Provenance.FilledFromBudgetKey(node.path("key").asString())
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
private val RATE_EXECUTORS: Map<String, (JsonNode, JsonNode, List<String>) -> Unit> =
    mapOf(
        "rate-unit-001" to { input, expected, verifiedPaths ->
            val actual = Rate.ofPercent(input.atDollarPath("$.rawRate.numeric").decimalValue())
            assertRateFractionMatches(actual, expected, verifiedPaths)
        },
        "rate-unit-002" to { input, expected, verifiedPaths ->
            val actual = Rate.ofFraction(input.atDollarPath("$.rawRate.numeric").decimalValue())
            assertRateFractionMatches(actual, expected, verifiedPaths)
        },
        "rate-unit-005" to { input, expected, verifiedPaths ->
            val actual = Rate.ofPercent(input.atDollarPath("$.rawRate.numeric").decimalValue())
            assertRateFractionMatches(actual, expected, verifiedPaths)
        },
    )

private val VALUE_EXECUTORS: Map<String, (JsonNode) -> Map<String, Any?>> =
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
        "money-basis-006" to { input ->
            val row = input.atDollarPath("$.row")
            require(row.path("conceptSlot").asString() == "BASE_AMOUNT") { "이 case 는 BASE_AMOUNT 슬롯만 다룬다" }
            val won = row.path("amount").asLong()
            val currency = Currency.valueOf(row.path("currency").asString())
            val vatTreatmentNode = row.path("declaredVatTreatment")
            val vatTreatment =
                if (vatTreatmentNode.isMissingNode || vatTreatmentNode.isNull) {
                    VatTreatment.UNKNOWN
                } else {
                    VatTreatment.valueOf(vatTreatmentNode.asString())
                }
            val provenanceNode = row.path("declaredProvenance")
            val provenance =
                if (provenanceNode.isMissingNode || provenanceNode.isNull) {
                    Provenance.Undeclared
                } else {
                    error("이 corpus 는 legacy 행의 선언된 provenance 를 다루지 않는다")
                }
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
private val COMPILE_DELEGATION_FIXTURES: Map<String, Int> =
    mapOf(
        "money-basis-001" to 11,
        "money-basis-004" to 11,
        "rate-unit-003" to 12,
        "rate-unit-004" to 12,
    )

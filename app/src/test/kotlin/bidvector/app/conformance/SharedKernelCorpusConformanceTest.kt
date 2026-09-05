package bidvector.app.conformance

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
 * (verifier r1 L-4)는 `Rate` 가 `data class` 라는 사실로 우회한다(`assertRateFractionMatches`,
 * `CorpusExecutors.kt`).
 *
 * **이 파일은 fixture/JSON 인프라와 test 진입점만 갖는다** — manifest 로딩(`ManifestCase`),
 * JSON 경로 walker·비교(`atDollarPath`·`canonical`·`assertPathEquals`), compile-fixture
 * 존재 확인이 그것이다. **case 별 executor(계약 함수 호출·dispatch 표)는
 * `CorpusExecutors.kt` 에 있다** — 크기 한도(v2-지침서.md §5, 500줄)를 기계적으로
 * 회피하려는 분할이 아니라 「fixture 를 읽고 비교하는 법」과 「계약과 무엇을 대조하는가」가
 * 서로 다른 관심사이기 때문이다.
 *
 * **dispatch 는 case id → executor 명시 표 셋이 전부다**([CorpusExecutors] 의
 * `RATE_EXECUTORS`·`VALUE_EXECUTORS`·`COMPILE_DELEGATION_FIXTURES]). **표 밖의
 * authoritative case 가 있으면 실패한다**([`dispatch 표 밖의 authoritative case 가 없다`])
 * — 새 case 가 조용히 빠지지 못한다(위협 모델 우회 (4)).
 *
 * **verifier r1 수정 라운드(M-1~M-3, L-1)**:
 * - **M-1** — `money-basis-006` 은 입력의 `declaredVatTreatment`/`declaredProvenance`
 *   누락을 `UNKNOWN`/`Undeclared` 로 접지 않는다(`CorpusExecutors.kt`).
 * - **M-2** — `rate-unit-001`·`002`·`005` 는 입력 `$.rawRate.declaredUnit` 로 percent/
 *   fraction 갈래를 정한다(`CorpusExecutors.kt` `rateFromDeclaredUnit`).
 * - **M-3** — [assertPathEquals] 가 값을 비교하기 전에 actual·expected 양쪽에 그 경로가
 *   실존함을 먼저 단언한다 — 존재하지 않는 경로가 missing==missing 으로 공허하게 통과하던
 *   자리를 닫는다.
 * - **L-1** — [assertFixtureNumberMatchesContractBinding] 가 `COMPILE_DELEGATION_FIXTURES`
 *   의 번호와 manifest `contract_binding.type_path` 의 「fixture N」표기를 대조한다.
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
                val fixtureNumber = COMPILE_DELEGATION_FIXTURES.getValue(case.id)
                assertFixtureNumberMatchesContractBinding(case.id, fixtureNumber, case.contractBindingTypePath)
                assertCompileFixtureFamilyExists(fixtureNumber)
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

internal val TARGET_DOMAINS = setOf("rate-unit", "money-basis")

private const val MANIFEST_PROPERTY = "bidvector.fixtures.manifest"
private const val FIXTURES_ROOT_PROPERTY = "bidvector.fixtures.root"
private const val COMPILE_FIXTURES_DIR_PROPERTY = "bidvector.sharedkernel.compile-fixtures"

/**
 * `compareKnownVat` 가 성립하려면 두 인자가 같은 basis 여야 하고, 그때는 애초에
 * compile-fixture 위임이 아니다 — 이 corpus 의 basis 교차 쌍은 값을 계산하지 않는다.
 */
private val REPRESENTABLE_FALSE: Map<String, Any?> = mapOf("representable" to false)

internal val MAPPER: ObjectMapper =
    JsonMapper
        .builder()
        .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
        .build()

internal data class ManifestCase(
    val id: String,
    val domain: String,
    val classification: String,
    val verifiedPaths: List<String>,
    val inputFile: String,
    val expectedFile: String,
    /** compile-delegation case 의 fixture 번호 대조(L-1)에만 쓴다 — 그 밖의 case 는 안 읽는다. */
    val contractBindingTypePath: String?,
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
        contractBindingTypePath = (raw["contract_binding"] as? Map<String, Any?>)?.get("type_path") as? String,
    )

private fun targetCases(): List<ManifestCase> =
    allCases().filter { it.domain in TARGET_DOMAINS && it.classification == "authoritative" }

private fun readFixtureJson(manifestRelativePath: String): JsonNode {
    val fixturesRoot = System.getProperty(FIXTURES_ROOT_PROPERTY) ?: error("시스템 속성 '$FIXTURES_ROOT_PROPERTY' 가 없다")
    val file = File(fixturesRoot, manifestRelativePath.removePrefix("fixtures/"))
    return MAPPER.readTree(file)
}

// ---- verified_paths 대조 — JSON 경로 walker + BigDecimal 인지 비교 ----

internal fun JsonNode.atDollarPath(path: String): JsonNode =
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

/**
 * verifier r1 M-3 — 존재하지 않는 경로가 missing==missing 으로 공허하게 통과하던 자리.
 * 값을 비교하기 전에 **두 쪽 다 경로가 실존함**을 먼저 단언한다(explicit `null` 은 통과 —
 * `isMissingNode` 만 본다, 그런 case 는 이 corpus 에 없지만 구분을 지운다).
 */
private fun assertPathEquals(
    actual: JsonNode,
    expected: JsonNode,
    path: String,
) {
    val actualNode = actual.atDollarPath(path)
    val expectedNode = expected.atDollarPath(path)
    withClue("경로 $path 가 actual projection 에 없다 — verified_paths 와 executor 가 어긋난다") {
        actualNode.isMissingNode shouldBe false
    }
    withClue("경로 $path 가 기대값 파일에 없다 — manifest verified_paths 가 잘못됐거나 fixture 가 낡았다") {
        expectedNode.isMissingNode shouldBe false
    }
    val actualValue = actualNode.canonical()
    val expectedValue = expectedNode.canonical()
    withClue("경로 $path — actual=$actualValue expected=$expectedValue") {
        valuesMatch(actualValue, expectedValue) shouldBe true
    }
}

/**
 * verifier r1 L-1 — `COMPILE_DELEGATION_FIXTURES` 의 번호와 manifest
 * `contract_binding.type_path` 산문의 「fixture N」표기가 어긋나도 잡는 장치가 없었다
 * (runner 는 파일 접두만 본다 — 번호가 틀려도 다른 fixture 가족이 있으면 초록). 두 벌
 * 표기가 실제로 같은 숫자를 가리키는지 여기서 대조한다.
 */
private fun assertFixtureNumberMatchesContractBinding(
    caseId: String,
    fixtureNumber: Int,
    contractBindingTypePath: String?,
) {
    val typePath = contractBindingTypePath ?: error("case $caseId 의 manifest 에 contract_binding.type_path 가 없다")
    withClue("case $caseId 의 contract_binding.type_path 가 'fixture $fixtureNumber' 를 언급해야 한다 — 실제: $typePath") {
        typePath.contains("fixture $fixtureNumber") shouldBe true
    }
}

internal fun assertCompileFixtureFamilyExists(fixtureNumber: Int) {
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

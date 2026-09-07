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
    /**
     * `KONEPS_COLLECTION_PENDING_CAPABILITY`(3A 잔여 일괄 ②) 7건은 이 dynamic 실행 집합에서
     * 뺀다 — `classification: authoritative`라 [targetCases]엔 남지만, dispatch 표가
     * 의도적으로 비어 있으므로 이 자리에서 돌리면 예외를 흉내 낸 것일 뿐이다. 완전성은
     * [`dispatch 표 밖의 authoritative case 가 없다`]가 별도로 지킨다.
     */
    @TestFactory
    fun `authoritative rate-unit·money-basis·license case 가 1B·1C 계약과 대조된다`(): List<DynamicTest> =
        targetCases()
            .filterNot { it.id in KONEPS_COLLECTION_PENDING_CAPABILITY }
            .map { case -> dynamicTest(case.id) { runCase(case) } }

    /**
     * 위협 모델 우회 (4) — 소비 테스트를 실행 집합에서 빼거나 dispatch 표에서 새 case 를
     * 빠뜨리는 것을 막는다. `KONEPS_COLLECTION_PENDING_CAPABILITY`(3A 잔여 일괄 ②)는
     * 값을 맞추기 위한 예외가 아니라 — 능력 공백을 정직하게 등재한 목록이다. 목록의
     * **정확한 6건 구성**은 아래 별도 test 가 잠가, 새 case 가 이 예외를 통해 조용히
     * 빠지지 못하게 한다.
     */
    @Test
    fun `dispatch 표 밖의 authoritative case 가 없다`() {
        val undispatched =
            targetCases().map { it.id }.filterNot { id ->
                id in RATE_EXECUTORS ||
                    id in VALUE_EXECUTORS ||
                    id in COMPILE_DELEGATION_FIXTURES ||
                    id in KONEPS_COLLECTION_PENDING_CAPABILITY
            }
        withClue("dispatch 표에 없는 authoritative case: $undispatched") {
            undispatched shouldBe emptyList()
        }
    }

    /**
     * M3/3A 잔여 일괄 ② — koneps-collection 7건은 procurement 공개 API 로 값을 만들면
     * fixture 가 요구하는 축(판정 근거는 evidence `checklist.md` 「판정 필요 case」표)을
     * 강제로 맞추게 된다 — 이 slice 는 그렇게 하지 않는다. 002·003·004·016·018·023 은
     * 팀리드 사전 지정, 026 은 배선 중 신규 발견(v2-defect — `KonepsCollectionExecutors.kt`
     * 머리 문서 참고). 이 test 는 예외 목록이 **정확히 이 7건**임을 잠가, 목록이 새 case 로
     * 조용히 자라거나(값 우회 은폐) 줄어드는데(능력이 생겼는데 미반영) evidence 갱신 없이
     * 지나가지 못하게 한다.
     */
    @Test
    fun `koneps-collection 판정 필요 7건은 예외로 고정된다`() {
        KONEPS_COLLECTION_PENDING_CAPABILITY shouldBe
            setOf(
                "koneps-collection-002",
                "koneps-collection-003",
                "koneps-collection-004",
                "koneps-collection-016",
                "koneps-collection-018",
                "koneps-collection-023",
                "koneps-collection-026",
            )
    }

    /**
     * M1/1E ⑪ — `money-basis-003`가 curator 커밋(`c9022d9`, decision 29)으로 authoritative에
     * 되돌아가 이 축의 insufficient-evidence 이월이 0건이 됐다(`OPEN-1BC-STR16` 해소).
     * 「④가 넷을 닫는다」의 인계 경계가 이제 빈 목록이라는 것 자체가 그 해소의 증거다.
     */
    @Test
    fun `1B 축 insufficient-evidence 이월은 이제 없다 — money-basis-003 승격으로 닫혔다`() {
        val axisDomains = setOf("rate-unit", "money-basis")
        val ids =
            allCases()
                .filter { it.domain in axisDomains && it.classification == "insufficient-evidence" }
                .map { it.id }
        ids shouldBe emptyList()
    }

    /**
     * M1/1C — license-001(policyVersion 요구가 QUAL-03 acceptance 축 위반) · 008(파싱 실패
     * 행 수 근거 부족) · 010(`requirementsBySourceField` 초과 주장) · 011(`OPEN-QUAL-11`
     * provisional 자체)은 인계 경계다(scope.md 「조사 결과」).
     */
    @Test
    fun `1C 축 insufficient-evidence 이월은 license-001·008·010·011 넷뿐이다`() {
        val ids =
            allCases()
                .filter { it.domain == "license" && it.classification == "insufficient-evidence" }
                .map { it.id }
        ids shouldBe listOf("license-001", "license-008", "license-010", "license-011")
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

internal val TARGET_DOMAINS =
    setOf(
        "rate-unit",
        "money-basis",
        "license",
        // M1/1D — provenance first-match·floor shortfall 커널 둘(scope.md 「이 slice 가
        // 하는 일」⑩).
        "base-amount-provenance",
        "floor-shortfall",
        "floor-threshold",
        // M1/1E ⑪ — 감시 predicate·전략 값 validation 커널 둘(scope.md 「이 slice 가
        // 하는 일」①·⑤). money-basis-003 은 이미 money-basis 축에 있어 여기 추가하지
        // 않는다 — curator 가 되돌린 authoritative case 가 위 목록으로 이미 대상이 된다.
        "strategy-watch",
        "strategy-validation",
        // M3/3A 잔여 일괄 ② — koneps-collection 27 case 전건 authoritative(운영자 승인
        // 2026-09-07). 21건은 `KONEPS_COLLECTION_EXECUTORS`가 dispatch 하고, 나머지 6건은
        // `KONEPS_COLLECTION_PENDING_CAPABILITY` 예외(아래 완전성 test 참고)로 명시 등재한다.
        "koneps-collection",
    )

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

/** M3/3A 잔여 일괄 ② — koneps-collection 이 이 corpus 최초로 배열 인덱스 표기(`name[n]`)를 쓴다. */
private val ARRAY_SEGMENT = Regex("(\\w+)\\[(\\d+)]")

internal fun JsonNode.atDollarPath(path: String): JsonNode =
    path.removePrefix("$.").split(".").fold(this) { node, segment ->
        val arrayMatch = ARRAY_SEGMENT.matchEntire(segment)
        if (arrayMatch != null) {
            val (name, index) = arrayMatch.destructured
            node.path(name).path(index.toInt())
        } else {
            node.path(segment)
        }
    }

/**
 * M1/1C — `missingByGroup`(license-002·003·005)이 JSON object 다. 순서 무관 비교라
 * `LinkedHashMap`(Map.equals)에 맡긴다 — `atDollarPath` 가 세그먼트 이름으로만 내려가므로
 * object 는 오직 이 leaf 비교 경로에서만 나타난다(중첩 object 순회는 하지 않는다).
 */
private fun JsonNode.canonical(): Any? =
    when {
        isMissingNode || isNull -> null
        isBoolean -> booleanValue()
        isNumber -> decimalValue()
        isString -> asString()
        isArray -> values().map { it.canonical() }
        isObject -> properties().associate { (key, value) -> key to value.canonical() }
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
        // 1A-b ⑤(1B-c verifier r2 low ①) — `contains("fixture $n")`는 접두 오탐이 있다
        // (`fixture 1`이 `fixture 11`·`fixture 12`에도 부분 문자열로 든다). 단어 경계로
        // 안전화한다 — 뒤에 숫자가 이어지지 않을 때만 대조된 것으로 본다.
        Regex("fixture $fixtureNumber(?!\\d)").containsMatchIn(typePath) shouldBe true
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

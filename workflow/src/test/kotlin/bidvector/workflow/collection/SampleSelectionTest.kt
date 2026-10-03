package bidvector.workflow.collection

import bidvector.procurement.BusinessDivision
import bidvector.workflow.collection.NoticeKeyHash
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import java.io.File
import java.time.LocalDate

private val SEED = SamplingSeed("6g-2026-09-27")

private fun candidate(
    number: String,
    division: BusinessDivision = BusinessDivision.SERVICE,
    noticeDate: LocalDate = LocalDate.of(2026, 6, 3),
): SampleCandidate = SampleCandidate(NoticeKeyHash.of(number, "000"), division, noticeDate)

private fun candidates(
    count: Int,
    division: BusinessDivision = BusinessDivision.SERVICE,
    noticeDate: LocalDate = LocalDate.of(2026, 6, 3),
// 합성 공고번호에 층 축을 섞는다 — 같은 번호를 다른 층에 두면 같은 공고가 되어(키 해시가
// 같다) 후보가 하나로 접힌다.
): List<SampleCandidate> =
    (1..count).map { candidate("SYN-6G-$division-$noticeDate-%05d".format(it), division, noticeDate) }

/**
 * D-6G-11 · 우회 ⑦(표본 쇼핑) — 표본은 **결과를 보기 전에**, 결과와 무관한 값(공고 식별자
 * 해시 + 정책 seed)으로 확정된다. 이 test 는 그 성질을 값으로 잰다.
 */
class SampleSelectionTest {
    @Test
    fun `같은 seed 와 같은 후보 집합이면 같은 표본이 나온다`() {
        val pool = candidates(50)
        val sampler = StratifiedSampler(SEED, SampleSize(10))

        val first = sampler.select(pool)
        val second = sampler.select(pool.reversed())

        first.selected shouldContainExactly second.selected
    }

    @Test
    fun `후보를 주는 순서가 표본을 바꾸지 않는다 — 수집 순서는 표본의 입력이 아니다`() {
        val pool = candidates(40)
        val sampler = StratifiedSampler(SEED, SampleSize(7))

        val shuffled = sampler.select(pool.shuffled(java.util.Random(1)))

        shuffled.selected shouldContainExactly sampler.select(pool).selected
    }

    @Test
    fun `seed 가 다르면 표본이 다르다 — seed 하나에 묶여 있지 않다`() {
        val pool = candidates(60)

        val a = StratifiedSampler(SEED, SampleSize(10)).select(pool)
        val b = StratifiedSampler(SamplingSeed("6g-other-seed"), SampleSize(10)).select(pool)

        a.selected shouldNotBe b.selected
    }

    @Test
    fun `층은 업무 대분류 × 공고 주다 — 같은 주의 다른 업무는 다른 층이다`() {
        val sameWeek = LocalDate.of(2026, 6, 3)
        val pool =
            candidates(20, BusinessDivision.SERVICE, sameWeek) +
                candidates(20, BusinessDivision.CONSTRUCTION, sameWeek)
        // 층 둘이 같은 크기라 비례 배분은 층마다 절반이다.
        val sampler = StratifiedSampler(SEED, SampleSize(10))

        val outcome = sampler.select(pool)

        outcome.strata.keys
            .map { it.division }
            .toSet() shouldBe
            setOf(BusinessDivision.SERVICE, BusinessDivision.CONSTRUCTION)
        outcome.selected.size shouldBe 10
    }

    @Test
    fun `같은 업무의 다른 주는 다른 층이다 — 주 경계가 층을 가른다`() {
        val pool =
            candidates(20, noticeDate = LocalDate.of(2026, 6, 3)) +
                candidates(20, noticeDate = LocalDate.of(2026, 6, 10))
        val sampler = StratifiedSampler(SEED, SampleSize(10))

        val outcome = sampler.select(pool)

        outcome.strata.keys
            .map { it.noticeWeek }
            .toSet()
            .size shouldBe 2
        outcome.selected.size shouldBe 10
    }

    @Test
    fun `후보가 목표보다 적으면 전부 뽑고 모자란 사실을 나른다 — 조용히 채우지 않는다`() {
        val pool = candidates(3)
        val sampler = StratifiedSampler(SEED, SampleSize(10))

        val outcome = sampler.select(pool)

        outcome.selected.size shouldBe 3
        outcome.short shouldBe true
        outcome.strata.values
            .single()
            .available shouldBe 3
    }

    /**
     * D-6G-42 M-6 — 층마다 **같은 수**를 뽑으면 층 크기가 다를 때 층화 표본이 아니라 균등 추출이다.
     * 작은 층이 과대표집되고 큰 층이 과소표집돼, 층화가 막으려던 편향을 층화가 만든다.
     */
    @Test
    fun `배분은 층 크기에 비례한다 — 층마다 같은 수가 아니다`() {
        val big = candidates(90, BusinessDivision.SERVICE, LocalDate.of(2026, 6, 3))
        val small = candidates(10, BusinessDivision.CONSTRUCTION, LocalDate.of(2026, 6, 3))

        val outcome = StratifiedSampler(SEED, SampleSize(20)).select(big + small)

        outcome.selected.size shouldBe 20
        outcome.short shouldBe false
        val byDivision =
            outcome.strataByKey.values
                .groupingBy { it.division }
                .eachCount()
        byDivision[BusinessDivision.SERVICE] shouldBe 18
        byDivision[BusinessDivision.CONSTRUCTION] shouldBe 2
    }

    /** 반올림만 하면 합이 목표와 어긋난다 — 최대 잔여법이 그 차이를 메운다. */
    @Test
    fun `나누어떨어지지 않아도 합이 정확히 목표다`() {
        val pool =
            candidates(7, BusinessDivision.SERVICE, LocalDate.of(2026, 6, 3)) +
                candidates(7, BusinessDivision.CONSTRUCTION, LocalDate.of(2026, 6, 3)) +
                candidates(7, BusinessDivision.GOODS, LocalDate.of(2026, 6, 3))

        val outcome = StratifiedSampler(SEED, SampleSize(10)).select(pool)

        outcome.selected.size shouldBe 10
        outcome.strata.values.sumOf(StratumOutcome::target) shouldBe 10
    }

    /** 잔여 배분이 후보 순서에 기대면 수집 순서가 표본의 입력이 된다. */
    @Test
    fun `잔여 배분도 후보 순서와 무관하다`() {
        val pool =
            candidates(7, BusinessDivision.SERVICE, LocalDate.of(2026, 6, 3)) +
                candidates(7, BusinessDivision.CONSTRUCTION, LocalDate.of(2026, 6, 3)) +
                candidates(7, BusinessDivision.GOODS, LocalDate.of(2026, 6, 3))
        val sampler = StratifiedSampler(SEED, SampleSize(10))

        val shuffled = sampler.select(pool.shuffled(java.util.Random(3)))

        shuffled.selected shouldContainExactly sampler.select(pool).selected
        shuffled.strata shouldBe sampler.select(pool).strata
    }

    /**
     * **D-6G-74 (cr r5 L-4) · D-6G2c-19 (b) (cr r5-t L-3) — 용도 구분자를 스키마 문서에 묶는다.**
     * `SAMPLE_DRAW_DOMAIN` 을 바꾸거나 재료에서 빼는 변이는 두 레인 어느 test 도 붉히지 않았다 —
     * Python 쪽 단언은 문서의 값만 읽고, Kotlin 쪽에는 그 값이 **실제로 쓰인다**는 단언이 없었다.
     * 그 값이 바뀌면 같은 seed 에서 다른 표본이 뽑힌다(사전 등록 값이다).
     *
     * 재료를 **문서에서 읽는다**(앞 판은 이 test 안의 리터럴과 코드를 맞댔다 — 그러면 등식의 양끝이
     * 모두 코드라서 문서가 혼자 움직여도 아무것도 붉어지지 않는다). 스키마 §2.2 의 「표본 추첨 순서」
     * 행에서 용도 토큰과 구분자를 뽑아 그것으로 순서를 짓는다: **문서만 바꿔도 RED** 다.
     */
    @Test
    fun `표본 추첨 순서는 스키마 §2·2 가 적은 재료식 그대로다`() {
        val seed = SamplingSeed("6g-purpose-seed")
        val pool = candidates(4)
        val keys = pool.map { it.key }
        val (purpose, separator) = schemaDrawRecipe()

        // 목표를 **둘**로 둔다 — 전수를 뽑으면 순서가 무엇이든 같은 집합이라 재료식이 드러나지 않는다.
        val drawn = StratifiedSampler(seed, SampleSize(2)).select(pool).selected

        // 뽑힌 뒤의 나열 순서는 계약이 아니므로 집합으로 맞댄다.
        val expected =
            keys
                .sortedBy { sha256Hex("$purpose$separator${seed.value}$separator${it.value}") }
                .take(2)
        drawn.toSet() shouldBe expected.toSet()
    }

    /**
     * 스키마 §2.2 의 「표본 추첨 순서」 행에서 **용도 토큰과 구분자**를 읽는다. 표 안에서는 구분자가
     * 셀 구분과 겹쳐 `\|` 로 적히므로 역슬래시를 걷어 낸다. 읽히지 않으면 멈춘다 — 조용히 기본값을
     * 쓰면 등식이 꺼진 채 초록이 된다.
     */
    private fun schemaDrawRecipe(): Pair<String, String> {
        val document = File(SNAPSHOT_SCHEMA_PATH)
        check(document.isFile) { "스키마 문서를 찾지 못했다: ${document.absolutePath}" }
        val section =
            document
                .readText(Charsets.UTF_8)
                .substringAfter(HASH_PURPOSE_SECTION)
                .substringBefore("\n## ")
        val row =
            section.lineSequence().firstOrNull { DRAW_ROW_LABEL in it && "sha256(" in it }
                ?: error("스키마 §2.2 에서 「$DRAW_ROW_LABEL」 재료식 행을 찾지 못했다")
        val literals =
            Regex("\"([^\"]*)\"")
                .findAll(row)
                .map { it.groupValues[1] }
                .toList()
        check(literals.size >= 2) { "재료식에서 리터럴 둘을 읽지 못했다: $row" }
        return literals[0] to literals[1].replace("\\", "")
    }

    @Test
    fun `표본 목록 sha256 은 뽑힌 해시의 정렬 목록에서만 나온다 — 후보 순서와 무관하다`() {
        val pool = candidates(30)
        val sampler = StratifiedSampler(SEED, SampleSize(8))

        val direct = sampler.select(pool).sampleListSha256
        val shuffled = sampler.select(pool.shuffled(java.util.Random(7))).sampleListSha256

        direct shouldBe shuffled
        direct.length shouldBe 64
    }

    @Test
    fun `공고 키 해시는 공고번호와 차수 둘 다에 달려 있다 — 차수가 다르면 다른 공고다`() {
        NoticeKeyHash.of("SYN-6G-00001", "000") shouldNotBe NoticeKeyHash.of("SYN-6G-00001", "001")
        NoticeKeyHash.of("SYN-6G-00001", "000") shouldBe NoticeKeyHash.of("SYN-6G-00001", "000")
        NoticeKeyHash.of("SYN-6G-00001", "000").value.length shouldBe 64
    }
}

/**
 * 스냅숏 스키마 계약 문서 — test 의 작업 디렉터리는 모듈 자리라 한 단계 올라간다
 * (`WorkflowGateRegistrationTest` 가 정책 파일을 읽는 관례와 같다). 이 slice 는 이 파일을 **읽기만**
 * 한다(편집은 6G-2c-형식 몫이다).
 */
private const val SNAPSHOT_SCHEMA_PATH = "../reports/evidence/m6/6g/snapshot-schema.md"

/** 용도 구분자 표가 선 절의 제목 — 절 번호가 아니라 제목 문면으로 가리킨다(번호는 밀린다). */
private const val HASH_PURPOSE_SECTION = "해시의 용도 구분자"

/** 두 레인의 재료식 중 Kotlin 몫 — 같은 표의 다른 행(S0 밴드)과 섞이지 않게 행 이름으로 좁힌다. */
private const val DRAW_ROW_LABEL = "표본 추첨 순서"

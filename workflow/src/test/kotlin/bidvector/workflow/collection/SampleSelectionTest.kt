package bidvector.workflow.collection

import bidvector.procurement.BusinessDivision
import bidvector.workflow.collection.NoticeKeyHash
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
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

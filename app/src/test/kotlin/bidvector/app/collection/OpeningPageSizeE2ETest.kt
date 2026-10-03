package bidvector.app.collection

import bidvector.procurement.SourceEndpoint
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import java.nio.file.Files

/**
 * M6/6G-2f E2E — **한 호출이 몇 행을 받는가**. 값 한 줄 slice 의 두 축을 출하 조립에서 잰다
 * (실 KONEPS 호출 없음).
 *
 * ① **wire**(D-6G2f-2): mock 이 받은 요청의 `numOfRows` 가 개찰 다섯 축 전부에서 설정 값이다.
 *    설정에 칸이 있는 것과 그 값이 밖으로 나가는 것은 다르고, 이 slice 전에는 어느 test 도
 *    `numOfRows` 를 재지 않았다.
 * ② **거동**(D-6G2f-3): 쪽 크기가 **호출 수**를 바꾸고 **표본**을 바꾸지 않는다. 둘째가 선결이다 —
 *    실행 상태 디렉터리를 이어 쓰므로 표본이 쪽 크기에 따라 달라지면 확정 바이트가 갈린다.
 *
 * 「얼마나 부르는가」의 다른 축(승인 상한·잠금·이어 돌기)은 [OpeningBudgetE2ETest] 가 진다.
 */
class OpeningPageSizeE2ETest {
    companion object {
        /** 개찰 축 다섯 — 이 집합이 통째로 요청에 설정 값을 실어야 한다. */
        private val OPENING_AXES =
            setOf("개찰 목록", "개찰완료", "예비가격 상세", "기초금액", "산식 A")

        /** 한 호출에 들어가지 않는 참가 규모 — day1 실측의 2쪽 이상 공고(111건)에 해당하는 자리다. */
        private const val CROWDED_BIDDERS = 150

        /** 되돌림 인자와 같은 값(`--…rows-per-page=100`) — 이 slice 전의 쪽 크기다. */
        private const val NARROW_ROWS = 100

        /** 참가 150 행을 100 씩 받으면 쪽이 둘이다 — 수를 적지 않고 둘의 관계로 둔다. */
        private const val PAGES_AT_NARROW_ROWS = (CROWDED_BIDDERS + NARROW_ROWS - 1) / NARROW_ROWS

        /** 표본 불변을 재는 쪽 크기 — 쪽이 여럿 나오기만 하면 되므로 가장 작은 쪽으로 둔다. */
        private const val TINY_ROWS = 2

        /** 용역 한 업무만 — 공사 층에는 상세가 비어 오는 공고가 섞여(D-6G-42) 호출 수가 1 로 떨어진다. */
        private const val CROWDED_SAMPLE_SIZE = 3

        private const val ROWS_PER_PAGE_PROPERTY = "bidvector.koneps.opening.rows-per-page"

        private val e2e = OpeningCollectionE2EHarness()

        @AfterAll
        @JvmStatic
        fun stop() {
            e2e.stop()
        }
    }

    @Test
    fun `출하 배선이 개찰 다섯 축의 요청에 설정한 행 수를 싣는다`() {
        val (exitCodes, mock) = e2e.bootAndRun(emptyMap())

        exitCodes shouldContainExactly listOf(0)
        val requestedByAxis = mock.requestedRows.groupBy({ axisOf(it.operation) }, { it.numOfRows })
        val opening = requestedByAxis.filterKeys { it in OPENING_AXES }
        // 다섯 축이 **실제로 불렸다** — 한 축이라도 빠지면 그 축의 단언이 공집합에서 참이 된다.
        opening.keys shouldBe OPENING_AXES
        opening.values.flatten().toSet() shouldBe setOf(KONEPS_MAX_ROWS_PER_PAGE)
    }

    /**
     * D-6G2f-3 ⓐ — 쪽 크기가 **호출 수**를 바꾼다. 기준을 둘 둔다: mock 이 받은 요청(원장 밖)과
     * 원장의 `HTTP` 줄(상한이 세는 것). 한쪽만 보면 세는 자리의 결함을 그 자리로 재게 된다.
     */
    @Test
    fun `참가 150 행 공고를 쪽 999 는 한 호출로 걷고 쪽 100 은 두 호출로 걷는다`() {
        val crowded = MockPagingMode(respectsRequestedRows = true, biddersPerNotice = CROWDED_BIDDERS)

        val (wideExit, wide) = e2e.bootAndRun(crowdedServiceStratum(), paging = crowded)
        val wideCalls = wide.openingCompleteNotices.groupingBy { it }.eachCount()
        val wideLedger = httpAttemptCountOf(e2e.runStateDir, SourceEndpoint.OPENING_COMPLETE)

        val (narrowExit, narrow) =
            e2e.bootAndRun(
                crowdedServiceStratum() + mapOf(ROWS_PER_PAGE_PROPERTY to NARROW_ROWS.toString()),
                paging = crowded,
            )
        val narrowCalls = narrow.openingCompleteNotices.groupingBy { it }.eachCount()
        val narrowLedger = httpAttemptCountOf(e2e.runStateDir, SourceEndpoint.OPENING_COMPLETE)

        wideExit shouldContainExactly listOf(0)
        narrowExit shouldContainExactly listOf(0)
        // 두 판이 같은 수의 공고를 걸었다 — 공고 수가 갈리면 호출 수 비교가 다른 것을 재게 된다.
        wideCalls.keys.size shouldBe CROWDED_SAMPLE_SIZE
        narrowCalls.keys.size shouldBe CROWDED_SAMPLE_SIZE
        wideCalls.values.toSet() shouldBe setOf(1)
        narrowCalls.values.toSet() shouldBe setOf(PAGES_AT_NARROW_ROWS)
        wideLedger shouldBe CROWDED_SAMPLE_SIZE
        narrowLedger shouldBe CROWDED_SAMPLE_SIZE * PAGES_AT_NARROW_ROWS
    }

    /**
     * D-6G2f-3 ⓑ — 쪽 크기가 **표본을 바꾸지 않는다.** 실수집은 이미 확정한 실행 상태 디렉터리를 이어
     * 쓰고, 그 장부의 `sample_list_sha256` 은 이 파일의 바이트다. 쪽 크기가 표본을 움직이면 jar 를
     * 바꾼 다음 기동이 「표본 목록이 장부와 다르다」로 거부된다.
     */
    @Test
    fun `같은 모집단을 쪽 2 와 쪽 999 로 걷어 확정한 표본 목록은 바이트가 같다`() {
        val nonce = "PAGESIZE"
        val respectful = MockPagingMode(respectsRequestedRows = true)

        e2e.bootAndRun(mapOf(ROWS_PER_PAGE_PROPERTY to TINY_ROWS.toString()), nonce = nonce, paging = respectful)
        val tiny = Files.readAllBytes(e2e.runStateDir.resolve(SAMPLE_LIST_FILE))

        e2e.bootAndRun(emptyMap(), nonce = nonce, paging = respectful)
        val wide = Files.readAllBytes(e2e.runStateDir.resolve(SAMPLE_LIST_FILE))

        tiny.isNotEmpty() shouldBe true
        // 바이트 동일성이 주장이고, 텍스트 비교는 갈렸을 때 어디가 갈렸는지 보이게 하려고 함께 둔다.
        wide.decodeToString() shouldBe tiny.decodeToString()
        wide.contentEquals(tiny) shouldBe true
    }

    /**
     * 용역 한 업무만 걷는다 — 공사 층에는 상세 응답이 비어 오는 공고가 섞여 있어(D-6G-42) 그 공고의
     * 개찰완료가 쪽 크기와 무관하게 한 호출로 끝난다. 섞인 채로 재면 「쪽 100 은 두 호출」이 표본
     * 추첨에 따라 참이기도 거짓이기도 하다.
     */
    private fun crowdedServiceStratum(): Map<String, String> =
        mapOf(
            "bidvector.opening-collection.categories" to "service",
            "bidvector.opening-collection.sample-size" to CROWDED_SAMPLE_SIZE.toString(),
        )

    /** 개찰 축 다섯의 이름 — mock 이 쓰는 것과 같은 기준(오퍼레이션 경로의 접미)으로 가른다. */
    private fun axisOf(operation: String): String =
        when {
            operation.endsWith("PreparPcDetail") -> "예비가격 상세"
            operation.endsWith("OpengCompt") -> "개찰완료"
            operation.endsWith("BsisAmount") -> "기초금액"
            operation.endsWith("BidPrceCalclAInfo") -> "산식 A"
            operation.startsWith("getOpengResultListInfo") -> "개찰 목록"
            else -> "그 밖"
        }
}

private const val SAMPLE_LIST_FILE = "sample-list.tsv"

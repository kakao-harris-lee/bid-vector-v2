package bidvector.app.collection

import bidvector.adapters.persistence.JdbcCollectedAxisStore
import bidvector.adapters.snapshot.RunStateDirectory
import bidvector.adapters.snapshot.RunStateLock
import bidvector.procurement.CollectedAxisStore
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test

/**
 * M6/6G D-6G-1·11·19·20 E2E — **무엇을 부르는가**. 출하 조립을 mock KONEPS 로 기동해 표본틀 →
 * 표본 → 상세 넷 → 원문 적재까지 끝에서 끝으로 잰다(실 KONEPS 호출 없음).
 *
 * 잠그는 것: ① 표본에 뽑힌 공고만 상세를 부른다 ② 공사는 A값까지 넷, 용역은 셋 ③ 원문이
 * `raw_observation` 에 남는다 ④ 로그에 서비스 키도 상호도 없다 ⑤ 출하 조립의 빈이 대역이 아니다.
 *
 * **얼마나·언제 부르는가**(상한·잠금·이어 돌기)는 [OpeningBudgetE2ETest] 가 진다 — 두 축은 같은
 * 기동기([OpeningCollectionE2EHarness])를 쓰고 단언만 다르다.
 */
class OpeningCollectionE2ETest {
    companion object {
        private val e2e = OpeningCollectionE2EHarness()

        @AfterAll
        @JvmStatic
        fun stop() {
            e2e.stop()
        }
    }

    @Test
    fun `표본에 뽑힌 공고만 상세를 부르고 공사는 A값까지 넷을 부른다`() {
        val (exitCodes, mock) = e2e.bootAndRun(emptyMap())

        exitCodes shouldContainExactly listOf(0)
        // 업무 둘 × 공고일 하나 = 목록 슬롯 둘. 층마다 목표 2 씩 = 표본 넷(표본틀 여덟 가운데).
        mock.listCalls.size shouldBe 2
        mock.reservePriceNotices.size shouldBe TARGET_PER_STRATUM * 2
        mock.openingCompleteNotices.size shouldBe TARGET_PER_STRATUM * 2
        mock.baseAmountNotices.size shouldBe TARGET_PER_STRATUM * 2
        // A값은 공사 층에서만 — 용역 표본에는 안 나간다.
        mock.formulaANotices.size shouldBe TARGET_PER_STRATUM
        // 공고번호는 canonical 화에서 대문자로 선다 — 요청에 실려 나가는 것은 그 canonical 값이다.
        mock.formulaANotices.forEach { it shouldContain "CNSTWK" }
        // 표본틀 전체(8)를 부르지 않았다.
        mock.reservePriceNotices.size shouldBe 4
    }

    @Test
    fun `원문이 적재되고 어느 로그에도 서비스 키와 상호가 없다`() {
        e2e.bootAndRun(emptyMap())

        val rawRows = e2e.query("SELECT count(*) FROM raw_observation") { it.getInt(1) }
        rawRows shouldBeGreaterThan 0
        assertNoServiceKey(e2e.capturedEverything())
        // 상호는 raw 관측까지는 오지만 **로그에는 없다**(러너의 줄이 계수와 열거값뿐이다).
        e2e.capturedEverything() shouldNotContain e2e.sentinels.second
        e2e.capturedLog() shouldContain "opening-collection finished"
        // 실행 상태 파일(원장·표본·장부)에도 키가 없다 — 그 디렉터리는 저장소 밖에 오래 남는다.
        assertNoServiceKey(e2e.runStateText())
    }

    /**
     * **D-6G-73 (privacy r2 LOW-3) — 실패 경로에서도 키가 새지 않는다.** 키가 새는 가장 흔한 길은
     * 정상 로그가 아니라 **실패 보고의 스택 트레이스**다(URI 를 통째로 싣는 예외 메시지). 닿을 수
     * 없는 주소를 주어 전송이 실패하게 만들고, 로그·예외 cause 체인·표준 출력·표준 오류 어디에도
     * 키가 원문으로도 **URL 인코딩 형태로도** 없는지 본다.
     */
    @Test
    fun `전송이 실패해도 서비스 키가 어느 채널에도 없다`() {
        // 닿을 수 없는 주소면 표본틀이 절단돼 실행 자체가 실패한다(D-6G-50) — 그것이 이 test 가
        // 원하는 상태다. 실패를 삼키지 않고 **일어났음을 단언**한 뒤 채널을 훑는다.
        val failure = runCatching {
            e2e.bootAndRun(mapOf("bidvector.koneps.opening.scsbid-base-url" to "http://127.0.0.1:1/mock"))
        }

        failure.isFailure shouldBe true
        assertNoServiceKey(e2e.capturedEverything())
        assertNoServiceKey(e2e.runStateText())
    }

    /** 원문과 URL 인코딩 형태 둘 다 — 키는 쿼리 문자열에 실려 나가므로 인코딩된 채로 샌다. */
    private fun assertNoServiceKey(text: String) {
        val serviceKey = e2e.sentinels.first
        text shouldNotContain serviceKey
        text shouldNotContain URLEncoder.encode(serviceKey, StandardCharsets.UTF_8)
        text shouldNotContain "ServiceKey"
    }

    /**
     * `@ConditionalOnMissingBean` 은 배선 조건 test 가 DB 없이 기동 조건을 재게 하려고 연 자리다.
     * 그 자리가 **출하에서도** 열려 있으면 이어 돌기 저장소가 조용히 다른 빈으로 갈릴 수 있다 —
     * 메모리 대역이 서면 아무것도 기억하지 못한다. 실 DB 로 뜬 **출하 조립**에서 실제 타입을
     * 잰다(D-6G-44).
     */
    @Test
    fun `출하 조립의 이어 돌기 저장소는 JDBC 구현이다`() {
        e2e.bootAndRun(emptyMap()) { context ->
            context.getBean(CollectedAxisStore::class.java).shouldBeInstanceOf<JdbcCollectedAxisStore>()
            // 잠금도 출하 조립에서 실제로 잡힌다 — 대역이 서면 동시 실행 차단이 사라진다.
            context.getBean(RunStateDirectory::class.java).lock.shouldBeInstanceOf<RunStateLock.Held>()
        }
    }
}

package bidvector.app.collection

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

/**
 * M6/6G-2f D-6G2f-1 — 개찰 축 페이지 크기의 **값과 경계**.
 *
 * 기본값이 게이트웨이 실측 상한과 같다는 것을 수로 적지 않고 **상한 상수와 맞댄다**: 수를 두 자리에
 * 적으면 한쪽만 바뀌는 날이 오고, 그때 이 test 는 바뀐 쪽을 따라가 아무것도 말하지 않는다.
 *
 * 그 값이 실제 요청에 실려 나가는지(wire)와 호출 수를 줄이는지(거동)는 [OpeningPageSizeE2ETest] 가
 * 잰다 — 설정 값이 있다는 것과 그 값이 밖으로 나간다는 것은 다르다.
 */
class OpeningRowsPerPageTest {
    @Test
    fun `기본 페이지 크기는 게이트웨이 실측 상한이다`() {
        KonepsOpeningEndpointProperties().rowsPerPage shouldBe KONEPS_MAX_ROWS_PER_PAGE
    }

    /**
     * 상한 밖은 **기동 거부**다 — 게이트웨이가 무엇을 하는지 모르는 수로 실수집이 도는 길을 열지
     * 않는다. `@ConfigurationProperties` 바인딩은 생성자를 지나므로 이 거부가 곧 기동 실패다.
     */
    @Test
    fun `상한을 넘는 페이지 크기는 거부한다`() {
        val rejected =
            shouldThrow<IllegalArgumentException> {
                KonepsOpeningEndpointProperties(rowsPerPage = KONEPS_MAX_ROWS_PER_PAGE + 1)
            }

        rejected.message.orEmpty() shouldContain "rows-per-page"
    }

    /** 0 과 음수도 거부한다 — 「행을 하나도 받지 않는 호출」은 상한을 쓰면서 아무것도 얻지 못한다. */
    @Test
    fun `행 수가 양수가 아니면 거부한다`() {
        shouldThrow<IllegalArgumentException> { KonepsOpeningEndpointProperties(rowsPerPage = 0) }
        shouldThrow<IllegalArgumentException> { KonepsOpeningEndpointProperties(rowsPerPage = -1) }
    }
}

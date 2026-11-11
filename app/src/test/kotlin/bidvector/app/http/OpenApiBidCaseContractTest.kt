package bidvector.app.http

import bidvector.bidding.BidCommandTag
import bidvector.bidding.OPEN_CASE_COMMAND
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

/**
 * M10/10B — 입찰 건 응답 계열의 **문서 ↔ Kotlin 어휘 등식**. 지금 드는 축은 타임라인의 명령 칸
 * 하나다(`OPEN-M9-COMMAND-OPENAPI-ENUM`).
 *
 * **이 등식이 없는 동안 어휘 사슬에 공백이 둘 있었다.** 그 칸이 문서에서 `type: string` 이라
 * ① 문서가 Kotlin 어휘를 나르지 않고 ② 생성 타입도 유니온이 아닌 `string` 을 내서, web 이
 * 리터럴 열다섯을 **손으로 적어** 전수 표를 잠갔다. 그 손 유니온은 「표와 유니온을 함께 줄이는」
 * 변이를 지나갔고, 그래서 M9 는 **JS 에서 Kotlin 원문을 텍스트로 파싱**하는 교차언어 게이트로
 * 그 둘을 임시로 메웠다. 이 등식이 서면 사슬이 DB CHECK → Kotlin → 문서 → 생성 타입 → web 표
 * 로 **전부 컴파일·바이트 층**이 되고 그 텍스트 파싱이 필요 없어진다.
 *
 * **wire 값은 바뀌지 않는다.** 그 칸의 값은 전이 행의 열에서 오고, 그 열의 CHECK 가 Kotlin 어휘와
 * 같다는 것은 어댑터 통합 test 가 실 DB 위에서 들고 있다 — 여기서 더하는 것은 **문서가 그 집합을
 * 선언하게 하는 것**뿐이다.
 */
class OpenApiBidCaseContractTest {
    private val spec: Map<String, Any?> by lazy { loadOpenApiSpec() }

    /**
     * 전수는 **전이표의 열 키 열넷 + 건을 여는 명령 하나**다. 여는 명령은 출발 상태가 없어 전이표의
     * 행이 될 수 없고 타임라인에는 생성 전이로 남으므로, `BidCommandTag` 만으로는 전수가 아니다.
     *
     * 기댓값을 어댑터 쪽 CHECK 등식과 **같은 꼴로** 만든다 — 두 층이 같은 식을 쓰면 한쪽만 늘어난
     * 상태가 생기지 않는다.
     */
    @Test
    fun `타임라인 명령 어휘가 Kotlin 전수와 같다 — 전이 명령 열넷과 여는 명령 하나`() {
        spec.propertyEnum("BidTimelineEntry", "command") shouldContainExactlyInAnyOrder
            BidCommandTag.entries.map { it.name } + OPEN_CASE_COMMAND
    }

    /**
     * **어휘를 다시 열면 초록이 아니라 빨강이다.** 위 등식의 왼쪽을 `null`·빈 목록으로 만드는 길이
     * 곧 「문서가 `type: string` 으로 돌아가는」 변이이고, 그 길이 조용히 통과하면 생성 타입과 web
     * 전수 검사까지 함께 공허해진다. 그 성질을 합성 문서로 직접 잰다 — 실제 문서를 고치지 않고도
     * 변이의 방향을 측정할 수 있는 자리다.
     */
    @Test
    fun `닫힌 어휘를 열린 string 으로 되돌리면 읽기가 던진다 — 공허한 초록이 아니다`() {
        val openVocabulary =
            parseOpenApiSpec(
                """
                components:
                  schemas:
                    Probe:
                      type: object
                      properties:
                        command:
                          type: string
                """.trimIndent(),
            )
        val emptyVocabulary =
            parseOpenApiSpec(
                """
                components:
                  schemas:
                    Probe:
                      type: object
                      properties:
                        command:
                          type: string
                          enum: []
                """.trimIndent(),
            )

        shouldThrow<IllegalArgumentException> {
            openVocabulary.propertyEnum("Probe", "command")
        }.message.orEmpty() shouldContain "enum 을 선언하지 않았다"
        shouldThrow<IllegalArgumentException> {
            emptyVocabulary.propertyEnum("Probe", "command")
        }.message.orEmpty() shouldContain "비었다"
    }
}

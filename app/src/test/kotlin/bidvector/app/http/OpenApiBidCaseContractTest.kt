package bidvector.app.http

import bidvector.bidding.BidCommandTag
import bidvector.bidding.BidStateTag
import bidvector.bidding.OPEN_CASE_COMMAND
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import java.lang.reflect.Modifier

/**
 * M10/10B — 입찰 건 응답 계열의 **문서 ↔ Kotlin 어휘 등식**. 처음 든 축은 타임라인의 명령 칸
 * 하나였고(`OPEN-M9-COMMAND-OPENAPI-ENUM`), M11/11B 가 **상태 축**을 같은 자리에 들였다
 * (`OPEN-M10-TIMELINE-STATE-OPENAPI-ENUM`).
 *
 * **이 등식이 없는 동안 어휘 사슬에 공백이 둘 있었다.** 그 칸이 문서에서 `type: string` 이라
 * ① 문서가 Kotlin 어휘를 나르지 않고 ② 생성 타입도 유니온이 아닌 `string` 을 내서, web 이
 * 리터럴 열다섯을 **손으로 적어** 전수 표를 잠갔다. 그 손 유니온은 「표와 유니온을 함께 줄이는」
 * 변이를 지나갔고, 그래서 M9 는 **JS 에서 Kotlin 원문을 텍스트로 파싱**하는 교차언어 게이트로
 * 그 둘을 임시로 메웠다. 이 등식이 서면 그 텍스트 파싱이 필요 없어진다.
 *
 * **사슬의 층은 고리마다 다르다**(v L-1 — 앞 판은 「전부 컴파일·바이트 층」이라고 과대 서술했다):
 * DB CHECK ↔ Kotlin 은 **실 DB 통합 test**, Kotlin ↔ 문서는 **이 test**(런타임 JVM),
 * 문서 ↔ 생성 타입은 **바이트 등식**(W-5), 생성 타입 ↔ web 표는 **컴파일**(W-3)이다. 네 고리가
 * 전부 **기계 검사**라는 것이 요점이고, 그 가운데 컴파일·바이트인 것은 뒤 둘이다.
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
    /**
     * **키 집합 등식의 두 방향**(M14/14E 가 세운다 — 그전까지 이 계열에 이 축이 **없었다**).
     * 어휘 축(위·아래 test)은 enum 의 값을 재지 칸의 **집합**을 재지 않는다. 그래서 응답 DTO 에
     * 칸을 더하고 문서를 놔두면(또는 그 반대) 이 계열에서 아무것도 붉지 않았다 — 같은 저장소의
     * `NotificationItem`·`SuggestedNotice`·`NoticeSummary` 는 이미 그 등식을 들고 있다.
     *
     * 두 방향을 묶는다: 문서 ↔ **축어 집합**(문서가 조용히 줄어드는 쪽) · 문서 ↔ **DTO 필드
     * 전수**(DTO 가 조용히 늘어나는 쪽, 즉 문서에 없는 칸이 wire 에 닿는 경로). 한쪽만 두면
     * 둘 다 같은 방향으로 바꾸는 편집이 지나간다.
     *
     * `kotlin-reflect` 를 들이지 않고 순수 Java reflection 을 쓴다(app 모듈에 그 의존이 없다).
     * 이 DTO 도 `companion object` 를 가져 `declaredFields` 에 정적 `Companion` 필드가 섞이므로
     * synthetic 과 static 을 함께 걷어낸다 — 걷지 않으면 「문서에 `Companion` 키가 없다」는
     * 엉뚱한 이유로 붉는다.
     */
    @Test
    fun `건 응답의 키 집합이 Kotlin DTO 와 완전히 일치한다 — 여분 필드가 생기면 깨진다`() {
        val dtoFieldNames =
            BidCaseResponse::class.java.declaredFields
                .filterNot { it.isSynthetic || Modifier.isStatic(it.modifiers) }
                .map { it.name }
                .toSet()

        spec.propertyKeys("BidCase") shouldBe CASE_KEYS
        spec.propertyKeys("BidCase") shouldBe dtoFieldNames
        // **required 는 전수가 아니다** — `nextCheckpointAt` 하나가 빠져 있다(착수 실측). 서버는
        // 그 칸을 언제나 싣지만(DTO 의 필드이고 직렬화가 null 을 생략하지 않는다) 문서가 그것을
        // 선언하지 않아 생성 타입에서만 선택 칸이다. 이 slice 는 **그 줄을 고치지 않는다** — 기존
        // 칸의 선언을 바꾸면 생성 타입이 움직여 이 slice 밖의 자리가 함께 흔들린다(OPEN 후보로
        // 보고한다). 여기서는 **오늘의 사실을 그대로** 못박아, 이 slice 가 더한 넷이 required 에
        // 들어 있음과 그 하나의 예외가 늘지 않음을 함께 잰다.
        spec.requiredKeys("BidCase") shouldBe CASE_KEYS - "nextCheckpointAt"
    }

    @Test
    fun `타임라인 명령 어휘가 Kotlin 전수와 같다 — 전이 명령 열넷과 여는 명령 하나`() {
        spec.propertyEnum("BidTimelineEntry", "command") shouldContainExactlyInAnyOrder
            BidCommandTag.entries.map { it.name } + OPEN_CASE_COMMAND
    }

    /**
     * **상태 축의 선언 넷이 한 집합이다**(M11/11B). 앞 판은 한 응답 계열 안에서 선언이 갈려 있었다
     * — 건 스키마의 상태 칸은 `enum` 인데 타임라인의 출발·도착 칸은 `type: string` 이었다. 그래서
     * 같은 어휘를 나르는 칸들이 서로 달라질 수 있었고, **등식을 가진 사본은 하나뿐**이었다(대사 쪽
     * 기록 상태 칸 — 착수 실측).
     *
     * 넷째는 **목록 질의의 상태 탭 param** 이다. 그 칸은 선언이 모자란 것이 아니라(이미 `enum`
     * 이다) **등식이 없었다** — Kotlin 에 상태가 늘면 응답 쪽은 붉어지는데 그 param 만 조용히
     * 뒤처진다. 선언 꼴이 응답 칸과 달라 읽기는 다르지만 **맞댈 오른쪽은 같다**.
     *
     * 넷을 한 단언에 둔다. 같은 식의 오른쪽을 보므로 **한 자리만 늘어난 상태**가 설 수 없다.
     *
     * **wire 값은 바뀌지 않는다.** 두 상태 칸의 값은 포트의 `BidStateTag`(출발은 nullable)에서
     * `.name` 으로만 나온다 — 어휘 밖 값이 나갈 창이 **타입으로 이미** 닫혀 있고, 여기서 더하는
     * 것은 **문서가 그 집합을 선언하게 하는 것**뿐이다. 명령 칸보다 한 겹 더 닫힌 자리다.
     */
    @Test
    fun `상태 어휘 네 자리가 Kotlin 전수와 같다 — 타임라인 출발 · 도착 · 건 상태 · 목록 질의 탭`() {
        val states = BidStateTag.entries.map { it.name }

        spec.propertyEnum("BidTimelineEntry", "toState") shouldContainExactlyInAnyOrder states
        spec.nullablePropertyEnum("BidTimelineEntry", "fromState") shouldContainExactlyInAnyOrder states
        spec.propertyEnum("BidCase", "state") shouldContainExactlyInAnyOrder states
        spec.queryParameterEnum("/api/bid-cases", "get", "state") shouldContainExactlyInAnyOrder states
    }

    /**
     * **질의 인자 읽기도 fail-closed 다.** 인자를 못 찾으면 빈 어휘를 답으로 내서는 안 된다 —
     * 그러면 위 등식의 넷째 줄이 **공허하게 초록**이 되고, 그 param 이 이름을 바꾸거나 사라져도
     * 아무것도 붉어지지 않는다. 열거 자신의 부재·빈 목록·비문자는 응답 쪽 읽기와 **같은 코드**가
     * 보므로(아래 음성 대조 둘이 그 자리다) 여기서 재는 것은 **탐색**의 방향이다.
     */
    @Test
    fun `없는 질의 인자를 빈 어휘로 답하지 않는다 — 탐색이 던진다`() {
        val withoutStateParameter =
            parseOpenApiSpec(
                """
                paths:
                  /api/bid-cases:
                    get:
                      parameters:
                        - name: kind
                          in: query
                          schema:
                            type: string
                            enum: [REAL, PAPER]
                """.trimIndent(),
            )

        shouldThrow<IllegalArgumentException> {
            withoutStateParameter.queryParameterEnum("/api/bid-cases", "get", "state")
        }.message.orEmpty() shouldContain "하나로 선언되지 않았다"
    }

    /**
     * **출발 칸의 `null` 은 어휘와 선언 둘 다에 서야 한다.** 생성 전이에서 실제로 `null` 이 나가는
     * 칸이라 둘 가운데 하나만 있으면 선언이 그 값과 어긋난다 — 열거에 원소가 없으면 엄격한 검증기가
     * 나가는 값을 거부하고, `nullable` 이 없으면 선언이 나르지 않는 값을 어휘가 허용한다.
     *
     * 한쪽만 세운 합성 문서 둘로 읽기가 **던지는** 것을 잰다. 실제 문서를 고치지 않고도 두 변이의
     * 방향을 측정할 수 있는 자리다(어휘를 다시 여는 변이를 아래에서 재는 것과 같은 꼴).
     */
    @Test
    fun `null 을 싣는 어휘는 nullable 선언과 함께 서야 한다 — 한쪽만이면 던진다`() {
        val vocabularyWithoutNull =
            parseOpenApiSpec(
                """
                components:
                  schemas:
                    Probe:
                      type: object
                      properties:
                        fromState:
                          type: string
                          nullable: true
                          enum: [WATCHING]
                """.trimIndent(),
            )
        val nullWithoutNullable =
            parseOpenApiSpec(
                """
                components:
                  schemas:
                    Probe:
                      type: object
                      properties:
                        fromState:
                          type: string
                          enum: [null, WATCHING]
                """.trimIndent(),
            )

        shouldThrow<IllegalArgumentException> {
            vocabularyWithoutNull.nullablePropertyEnum("Probe", "fromState")
        }.message.orEmpty() shouldContain "enum 이 null 을 싣지 않았다"
        shouldThrow<IllegalArgumentException> {
            nullWithoutNullable.nullablePropertyEnum("Probe", "fromState")
        }.message.orEmpty() shouldContain "nullable 선언이 없다"
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

    /**
     * **비문자 열거도 어휘가 아니다**(cr r0 L-3). 소거 때문에 `as? List<String>` 은 `enum: [1, 2]`
     * 같은 선언을 **통과시킨다** — 그러면 위 집합 등식이 Int 와 String 을 비교해 읽기 어려운
     * 실패를 낸다(초록이 되지는 않으니 공허하지는 않다 — **읽을 수 없게** 되는 것이다). 읽기가
     * 그 꼴을 답으로 내지 않고 **던지는** 것을 잰다.
     */
    @Test
    fun `문자열이 아닌 열거는 어휘로 읽지 않는다 — 소거를 지나가지 못한다`() {
        val nonStringVocabulary =
            parseOpenApiSpec(
                """
                components:
                  schemas:
                    Probe:
                      type: object
                      properties:
                        command:
                          type: string
                          enum: [1, 2]
                """.trimIndent(),
            )

        shouldThrow<IllegalArgumentException> {
            nonStringVocabulary.propertyEnum("Probe", "command")
        }.message.orEmpty() shouldContain "문자열이 아닌 값"
    }

    private companion object {
        /**
         * 건 응답의 칸 전수 — **축어**다. 이 집합이 DTO 에서 유도되면 등식의 두 변이 같은
         * 자리에서 와서 공허해진다(한쪽은 손으로 적어야 「둘 다 바꾸는」 편집이 눈에 띈다).
         *
         * 뒤 넷은 저장된 값이 아니라 읽을 때 합쳐 온 공고 칸이다(M14/14E).
         */
        val CASE_KEYS =
            setOf(
                "caseId",
                "noticeNumber",
                "noticeRound",
                "bidKind",
                "state",
                "caseVersion",
                "nextCheckpointKind",
                "nextCheckpointAt",
                "overdue",
                "reconciliationCandidate",
                "noticeKnown",
                "noticeTitle",
                "demandAgencyName",
                "noticeAgencyName",
            )
    }
}

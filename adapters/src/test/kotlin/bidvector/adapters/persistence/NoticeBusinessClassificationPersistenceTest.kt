package bidvector.adapters.persistence

import bidvector.procurement.BusinessCategory
import bidvector.procurement.BusinessDivision
import bidvector.procurement.CategoryCode
import bidvector.procurement.CategoryLabel
import bidvector.procurement.MainConstructionType
import bidvector.procurement.NoticeCollected
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.procurement.PersistOutcome
import bidvector.procurement.RawKey
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.ServiceDivision
import bidvector.procurement.SourceEndpoint
import bidvector.sharedkernel.NoticeRound
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.postgresql.util.PSQLException
import java.time.Instant

/**
 * D-6F9-3(M6/6F-9, V17) — 업무구분 새 칸 셋(`business_division`·`service_division`·`main_construction_type`)의 **쓰기·읽기
 * 왕복 전 구간**: 삽입·갱신·존재 가드·복원, 그리고 DB CHECK. V16 교훈(6A-3) — `UPDATE_NOTICE` 의 `SET` 한 줄이나 바인딩
 * 순서 한 칸이 빠져도 초록이던 자리를 **열마다 따로** 잠근다: 칸 하나만 실린 명령이 그 열만 채우고 다른 열은 건드리지
 * 않는다(위치 바인딩이 밀리면 값이 옆 열로 간다). 각 축은 자기 열에만 있다 — 용역구분은 업무구분 라벨 열에 없고 주공종은 코드
 * 열을 낳지 않는다(P-7 · 우회 2·3).
 */
class NoticeBusinessClassificationPersistenceTest : PersistenceTestSupport() {
    private val repository get() = JdbcNoticeRepository(dataSource())

    private fun idOf(suffix: String) = NoticeId(NoticeNumber.of("CLASSIFY-6F9-$suffix"), NoticeRound.of("000"))

    private var observedSeconds = 0L

    private fun commandOf(
        id: NoticeId,
        division: BusinessDivision? = null,
        service: ServiceDivision? = null,
        main: MainConstructionType? = null,
        category: BusinessCategory? = null,
    ): NoticeCollected {
        val observation =
            RawNoticeObservation.of(
                mapOf(RawKey("bidNtceNo") to id.number.value, RawKey("bidNtceOrd") to id.round.value),
                SourceEndpoint.NOTICE_LIST,
                Instant.parse("2026-09-24T00:00:00Z").plusSeconds(observedSeconds++),
            )
        return NoticeCollected(
            id = id,
            businessCategory = category,
            baseAmount = null,
            estimatedAmount = null,
            allocatedBudget = null,
            floorRate = null,
            deadlineAt = null,
            openingScheduledAt = null,
            raw = observation,
            businessDivision = division,
            serviceDivision = service,
            mainConstructionType = main,
        )
    }

    private fun persist(command: NoticeCollected): PersistOutcome =
        repository.persist(command, appendRawObservation(command.raw))

    /** 새 열 셋과 업무구분 코드·라벨 열을 **원시 SQL 로** 읽는다 — 저장 표현 그대로(복원 경로를 거치지 않는다). */
    private fun columnsOf(id: NoticeId): Map<String, String?> =
        dataSource().connection.use { connection ->
            connection
                .prepareStatement(
                    "SELECT business_division, service_division, main_construction_type, " +
                        "business_category_code, business_category_label " +
                        "FROM notice WHERE notice_number = ? AND notice_round = ?",
                ).use { statement ->
                    statement.setString(1, id.number.value)
                    statement.setString(2, id.round.value)
                    statement.executeQuery().use { rs ->
                        check(rs.next()) { "notice 행이 없다: $id" }
                        listOf(
                            "business_division",
                            "service_division",
                            "main_construction_type",
                            "business_category_code",
                            "business_category_label",
                        ).associateWith { rs.getString(it) }
                    }
                }
        }

    private val serviceCategory = BusinessCategory(CategoryCode.of("81111500"), CategoryLabel("정보시스템 개발 서비스"))
    private val technicalService = ServiceDivision.of("기술용역")
    private val electricalWork = MainConstructionType.of("전기공사업")

    @Test
    fun `삽입 — 세 칸이 각자 자기 열에 저장되고 용역구분은 업무구분 라벨 열에 없다`() {
        val id = idOf("INSERT")

        persist(
            commandOf(
                id,
                BusinessDivision.SERVICE,
                ServiceDivision.of("기술용역"),
                MainConstructionType.of("전기공사업"),
                serviceCategory,
            ),
        ) shouldBe PersistOutcome.Inserted

        columnsOf(id) shouldBe
            mapOf(
                "business_division" to "용역",
                "service_division" to "기술용역",
                "main_construction_type" to "전기공사업",
                "business_category_code" to "81111500",
                "business_category_label" to "정보시스템 개발 서비스",
            )
    }

    @Test
    fun `갱신 — 기존 행을 재수집이 채우면 Updated 이고 칸 하나만 실린 명령은 그 열만 채운다 — 열마다 따로 잠근다`() {
        val onlyDivision = commandOf(idOf("U-DIV"), division = BusinessDivision.CONSTRUCTION)
        val onlyService = commandOf(idOf("U-SVC"), service = ServiceDivision.of("일반용역"))
        val onlyMain = commandOf(idOf("U-MAIN"), main = MainConstructionType.of("토목공사업"))

        listOf(
            onlyDivision to mapOf("business_division" to "공사"),
            onlyService to mapOf("service_division" to "일반용역"),
            onlyMain to mapOf("main_construction_type" to "토목공사업"),
        ).forEach { (filling, expectedFilled) ->
            withClue(filling.id.number.value) {
                persist(commandOf(filling.id)) shouldBe PersistOutcome.Inserted
                columnsOf(filling.id).values.toSet() shouldBe setOf(null)

                persist(filling).shouldBeInstanceOf<PersistOutcome.Updated>().revision shouldBe 2L

                columnsOf(filling.id) shouldBe
                    mapOf(
                        "business_division" to null,
                        "service_division" to null,
                        "main_construction_type" to null,
                        "business_category_code" to null,
                        "business_category_label" to null,
                    ) + expectedFilled
                persist(filling) shouldBe PersistOutcome.Unchanged
            }
        }
    }

    @Test
    fun `갱신 — 값이 바뀌면 새 값이 이긴다 — 세 열 모두 SET 에 들어 있다`() {
        val id = idOf("CHANGE")
        persist(
            commandOf(id, BusinessDivision.SERVICE, ServiceDivision.of("일반용역"), MainConstructionType.of("건축공사업")),
        )

        persist(
            commandOf(id, BusinessDivision.CONSTRUCTION, ServiceDivision.of("기술용역"), MainConstructionType.of("토목공사업")),
        ).shouldBeInstanceOf<PersistOutcome.Updated>()

        columnsOf(
            id,
        ).filterKeys { it in setOf("business_division", "service_division", "main_construction_type") } shouldBe
            mapOf("business_division" to "공사", "service_division" to "기술용역", "main_construction_type" to "토목공사업")
    }

    @Test
    fun `존재 가드 — 값이 없는 재수집은 기존 값을 지우지 않는다`() {
        val id = idOf("GUARD")
        persist(commandOf(id, BusinessDivision.SERVICE, ServiceDivision.of("기술용역"), MainConstructionType.of("전기공사업")))

        persist(commandOf(id)) shouldBe PersistOutcome.Unchanged

        columnsOf(id).filterKeys { it != "business_category_code" && it != "business_category_label" } shouldBe
            mapOf("business_division" to "용역", "service_division" to "기술용역", "main_construction_type" to "전기공사업")
    }

    /**
     * code-review r1 MEDIUM-2 — 같은 공고가 **두 오퍼레이션**에 모두 나오면 대분류는 last-writer-wins 다. 공고 목록
     * 관측의 대분류는 절대 `null` 이 아니므로(오퍼레이션이 정한다) 존재 가드가 「유입이 항상 이긴다」로 작동한다.
     * 이 test 는 그 전제를 **측정한다**: 두 순서가 서로 다른 값을 남기고(순서 독립이 아니다), 교대를 한 번 더 돌린
     * 세 번째 저장도 `Updated` 라 그 행은 `Unchanged` 로 **수렴하지 않는다**(code-review r2 LOW — 두 번만 재던 이전
     * 판은 「매번」을 재지 못했다). 겹친 행이 용역 전용 칸과 공사 전용 칸을 **둘 다** 채운 상태도 함께 잰다 —
     * checklist §7 SQL ⑨(a) 가 겹침 탐지에 쓰는 바로 그 상태다. 병합 의미는 이 slice 가 바꾸지 않는다 — 알려진
     * 제한으로 등재하고, 실제로 겹치는 공고가 있는지는 D-6F9-5 의 SQL 이 잰다.
     */
    @Test
    fun `두 오퍼레이션이 같은 공고를 내면 대분류는 나중 관측이 이긴다 — 슬롯 순서에 의존한다`() {
        val serviceLast = idOf("CROSSOP-A")
        val constructionLast = idOf("CROSSOP-B")

        persist(commandOf(serviceLast, BusinessDivision.CONSTRUCTION, main = electricalWork))
        persist(commandOf(serviceLast, BusinessDivision.SERVICE, service = technicalService))
            .shouldBeInstanceOf<PersistOutcome.Updated>()
        persist(commandOf(constructionLast, BusinessDivision.SERVICE, service = technicalService))
        persist(commandOf(constructionLast, BusinessDivision.CONSTRUCTION, main = electricalWork))
            .shouldBeInstanceOf<PersistOutcome.Updated>()

        columnsOf(serviceLast)["business_division"] shouldBe "용역"
        columnsOf(constructionLast)["business_division"] shouldBe "공사"
        columnsOf(serviceLast).filterKeys { it in setOf("service_division", "main_construction_type") } shouldBe
            mapOf("service_division" to "기술용역", "main_construction_type" to "전기공사업")

        persist(commandOf(serviceLast, BusinessDivision.CONSTRUCTION, main = electricalWork))
            .shouldBeInstanceOf<PersistOutcome.Updated>()
        columnsOf(serviceLast)["business_division"] shouldBe "공사"
    }

    @Test
    fun `공사 공고는 주공종만 싣고 업무구분 코드를 낳지 않는다 — find 로 복원해도 null 이다`() {
        val id = idOf("CONSTRUCTION")

        persist(commandOf(id, BusinessDivision.CONSTRUCTION, main = MainConstructionType.of("정보통신공사업")))

        val found = requireNotNull(repository.find(id))
        found.businessDivision shouldBe BusinessDivision.CONSTRUCTION
        found.mainConstructionType shouldBe MainConstructionType.of("정보통신공사업")
        found.businessCategory shouldBe null
        found.serviceDivision shouldBe null
        columnsOf(id)["business_category_code"] shouldBe null
    }

    @Test
    fun `복원 — find 는 세 칸과 업무구분을 저장한 그대로 낸다`() {
        val id = idOf("FIND")
        persist(
            commandOf(
                id,
                BusinessDivision.SERVICE,
                ServiceDivision.of("기술용역"),
                MainConstructionType.of("전기공사업"),
                serviceCategory,
            ),
        )

        val found = requireNotNull(repository.find(id))

        found.businessDivision shouldBe BusinessDivision.SERVICE
        found.serviceDivision shouldBe ServiceDivision.of("기술용역")
        found.mainConstructionType shouldBe MainConstructionType.of("전기공사업")
        found.businessCategory shouldBe serviceCategory
    }

    @Test
    fun `CHECK — 대분류는 문서 열거 어휘 넷만 받는다 — 어휘 밖·라벨 변형은 DB 가 거부한다`() {
        val id = idOf("CHECK-DIV")
        persist(commandOf(id))

        listOf("basket", "SERVICE", "용역 ", "", "기술용역").forEach { invalid ->
            withClue("'$invalid'") { shouldThrow<PSQLException> { setColumn(id, "business_division", invalid) } }
        }
        BusinessDivision.entries.forEach { division ->
            setColumn(id, "business_division", division.label)
            columnsOf(id)["business_division"] shouldBe division.label
        }
    }

    @Test
    fun `CHECK — 용역구분·주공종은 공백류만 있는 값을 DB 가 거부한다 — 빈 문자열이 조용히 성립하지 않는다`() {
        val id = idOf("CHECK-BLANK")
        persist(commandOf(id))

        listOf("service_division", "main_construction_type").forEach { column ->
            listOf("", " ", "\t", "\u00A0", "\u3000").forEach { blank ->
                withClue("$column='${blank.replace("\t", "\\t")}'") {
                    shouldThrow<PSQLException> { setColumn(id, column, blank) }
                }
            }
            setColumn(id, column, "일반")
        }
    }

    private fun setColumn(
        id: NoticeId,
        column: String,
        value: String,
    ) {
        dataSource().connection.use { connection ->
            connection
                .prepareStatement("UPDATE notice SET $column = ? WHERE notice_number = ? AND notice_round = ?")
                .use { statement ->
                    statement.setString(1, value)
                    statement.setString(2, id.number.value)
                    statement.setString(3, id.round.value)
                    statement.executeUpdate()
                }
        }
    }
}

package bidvector.adapters.qualification

import bidvector.adapters.persistence.JdbcNoticeRepository
import bidvector.adapters.persistence.PersistenceTestSupport
import bidvector.procurement.NoticeCollected
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.procurement.PersistOutcome
import bidvector.procurement.RawKey
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.SourceEndpoint
import bidvector.qualification.LicenseName
import bidvector.qualification.LmtGrpNo
import bidvector.qualification.LmtSno
import bidvector.qualification.RequirementCollection
import bidvector.qualification.RequirementRow
import bidvector.qualification.RequirementSourceField
import bidvector.sharedkernel.NoticeRound
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * [JdbcRequirementStore] — `RequirementCollection`의 세 갈래(D-6F5-4)를 표가 잃지 않게
 * 왕복하는지를 각각 잠근다. 「수집 실패」가 「없음」으로 접히거나 그 반대가 되면(6F-5 우회
 * 1) 이 test들 중 하나 이상이 FAIL한다 — 세 갈래를 서로 다른 assertion으로 분리해 둔다
 * (게이트가 자기 입력을 재계산해 항진명제가 되는 함정, 6F-2·6F-6 HIGH를 피하려고
 * `RequirementCollection` 값을 이 test가 직접 조립하고 store가 낸 값과 비교한다 — store
 * 내부 매핑을 다시 쓰지 않는다).
 */
class JdbcRequirementStoreTest : PersistenceTestSupport() {
    private val now = Instant.parse("2026-09-19T00:00:00Z")

    private fun store() = JdbcRequirementStore(dataSource())

    private fun insertNotice(number: String): NoticeId {
        val id = NoticeId(NoticeNumber.of(number), NoticeRound.of("000"))
        val observation =
            RawNoticeObservation.of(
                mapOf(RawKey("bidNtceNo") to id.number.value, RawKey("bidNtceOrd") to id.round.value),
                SourceEndpoint.NOTICE_LIST,
                now,
            )
        val key = appendRawObservation(observation)
        val command =
            NoticeCollected(
                id = id,
                businessCategory = null,
                baseAmount = null,
                estimatedAmount = null,
                allocatedBudget = null,
                floorRate = null,
                deadlineAt = null,
                openingScheduledAt = null,
                raw = observation,
            )
        JdbcNoticeRepository(dataSource()).persist(command, key) shouldBe PersistOutcome.Inserted
        return id
    }

    @Test
    fun `저장한 적 없는 공고는 DataAbsent 를 낸다`() {
        val id = insertNotice("REQ-ABSENT")

        store().find(id) shouldBe RequirementCollection.DataAbsent
    }

    @Test
    fun `CollectionFailed 를 저장하면 CollectionFailed 로 복원된다`() {
        val id = insertNotice("REQ-FAILED")

        store().save(id, RequirementCollection.CollectionFailed)

        store().find(id) shouldBe RequirementCollection.CollectionFailed
    }

    @Test
    fun `Collected 빈 목록을 저장하면 빈 목록으로 복원된다 — DataAbsent 로 접히지 않는다`() {
        val id = insertNotice("REQ-COLLECTED-EMPTY")

        store().save(id, RequirementCollection.Collected(emptyList()))

        store().find(id) shouldBe RequirementCollection.Collected(emptyList())
    }

    @Test
    fun `Parsed 행 하나를 저장하면 groupNo·sourceField·licenseNames 를 그대로 복원한다`() {
        val id = insertNotice("REQ-PARSED")
        val row =
            RequirementRow.Parsed(
                groupNo = LmtGrpNo("001"),
                serialNo = LmtSno("01"),
                sourceField = RequirementSourceField.LcnsLmtNm,
                licenseNames = listOf(LicenseName("전기공사업"), LicenseName("정보통신공사업")),
            )

        store().save(id, RequirementCollection.Collected(listOf(row)))

        store().find(id) shouldBe RequirementCollection.Collected(listOf(row))
    }

    @Test
    fun `groupNo 결측 Parsed 행은 null 그대로 복원된다 — Ungrouped 왕복`() {
        val id = insertNotice("REQ-UNGROUPED")
        val row =
            RequirementRow.Parsed(
                groupNo = null,
                serialNo = LmtSno("01"),
                sourceField = RequirementSourceField.PermsnIndstrytyList,
                licenseNames = listOf(LicenseName("건설업")),
            )

        store().save(id, RequirementCollection.Collected(listOf(row)))

        store().find(id) shouldBe RequirementCollection.Collected(listOf(row))
    }

    @Test
    fun `Unparsable 행은 serialNo 만으로 복원된다`() {
        val id = insertNotice("REQ-UNPARSABLE")
        val row = RequirementRow.Unparsable(serialNo = LmtSno("07"))

        store().save(id, RequirementCollection.Collected(listOf(row)))

        store().find(id) shouldBe RequirementCollection.Collected(listOf(row))
    }

    @Test
    fun `Parsed 와 Unparsable 이 섞인 여러 행을 순서대로 복원한다`() {
        val id = insertNotice("REQ-MIXED")
        val rows =
            listOf(
                RequirementRow.Parsed(
                    groupNo = LmtGrpNo("001"),
                    serialNo = LmtSno("01"),
                    sourceField = RequirementSourceField.LcnsLmtNm,
                    licenseNames = listOf(LicenseName("전기공사업")),
                ),
                RequirementRow.Unparsable(serialNo = LmtSno("02")),
                RequirementRow.Parsed(
                    groupNo = LmtGrpNo("002"),
                    serialNo = LmtSno("03"),
                    sourceField = RequirementSourceField.PermsnIndstrytyList,
                    licenseNames = listOf(LicenseName("정보통신공사업"), LicenseName("소방시설공사업")),
                ),
            )

        store().save(id, RequirementCollection.Collected(rows))

        store().find(id) shouldBe RequirementCollection.Collected(rows)
    }

    /** 제로패딩 보존(R-QUAL-05) — `LmtGrpNo`·`LmtSno`는 문자열로만 나른다. */
    @Test
    fun `groupNo·serialNo 의 제로패딩이 보존된다`() {
        val id = insertNotice("REQ-ZEROPAD")
        val row =
            RequirementRow.Parsed(
                groupNo = LmtGrpNo("007"),
                serialNo = LmtSno("003"),
                sourceField = RequirementSourceField.LcnsLmtNm,
                licenseNames = listOf(LicenseName("전기공사업")),
            )

        store().save(id, RequirementCollection.Collected(listOf(row)))

        val restored = store().find(id) as RequirementCollection.Collected
        val restoredRow = restored.rows.single() as RequirementRow.Parsed
        restoredRow.groupNo shouldBe LmtGrpNo("007")
        restoredRow.serialNo shouldBe LmtSno("003")
    }

    @Test
    fun `재저장은 이전 행을 남기지 않고 통째로 교체한다 — COLLECTED 에서 FAILED 로`() {
        val id = insertNotice("REQ-OVERWRITE-TO-FAILED")
        val row =
            RequirementRow.Parsed(
                groupNo = null,
                serialNo = LmtSno("01"),
                sourceField = RequirementSourceField.LcnsLmtNm,
                licenseNames = listOf(LicenseName("전기공사업")),
            )
        store().save(id, RequirementCollection.Collected(listOf(row)))

        store().save(id, RequirementCollection.CollectionFailed)

        store().find(id) shouldBe RequirementCollection.CollectionFailed
    }

    @Test
    fun `재저장은 이전 행을 남기지 않고 통째로 교체한다 — FAILED 에서 COLLECTED 로`() {
        val id = insertNotice("REQ-OVERWRITE-TO-COLLECTED")
        store().save(id, RequirementCollection.CollectionFailed)
        val row =
            RequirementRow.Parsed(
                groupNo = null,
                serialNo = LmtSno("09"),
                sourceField = RequirementSourceField.LcnsLmtNm,
                licenseNames = listOf(LicenseName("전기공사업")),
            )

        store().save(id, RequirementCollection.Collected(listOf(row)))

        store().find(id) shouldBe RequirementCollection.Collected(listOf(row))
    }

    @Test
    fun `DataAbsent 로 저장하면 헤더가 지워져 다시 DataAbsent 로 복원된다`() {
        val id = insertNotice("REQ-RESET")
        store().save(id, RequirementCollection.CollectionFailed)

        store().save(id, RequirementCollection.DataAbsent)

        store().find(id) shouldBe RequirementCollection.DataAbsent
    }

    @Test
    fun `한 공고의 저장은 다른 공고의 요건에 실리지 않는다`() {
        val idA = insertNotice("REQ-ISOLATE-A")
        val idB = insertNotice("REQ-ISOLATE-B")
        val row =
            RequirementRow.Parsed(
                groupNo = null,
                serialNo = LmtSno("01"),
                sourceField = RequirementSourceField.LcnsLmtNm,
                licenseNames = listOf(LicenseName("전기공사업")),
            )

        store().save(idA, RequirementCollection.Collected(listOf(row)))

        store().find(idB) shouldBe RequirementCollection.DataAbsent
    }
}

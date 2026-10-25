package bidvector.adapters.persistence

import bidvector.adapters.snapshot.RunStateDirectory
import bidvector.procurement.AttemptKind
import bidvector.procurement.AttemptOutcome
import bidvector.procurement.BusinessDivision
import bidvector.procurement.CollectionAttempt
import bidvector.procurement.CollectionReferenceDate
import bidvector.procurement.KONEPS_COLLECTION_POLICY
import bidvector.procurement.KonepsCollectionPolicyData
import bidvector.procurement.NOTICE_NUMBER_RAW_KEY
import bidvector.procurement.NOTICE_ROUND_RAW_KEY
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.procurement.RawKey
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.SourceEndpoint
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Resolution
import bidvector.workflow.collection.NoticeKeyHash
import bidvector.workflow.collection.OpeningPromotionRun
import bidvector.workflow.collection.PromoteOpeningResultsUseCase
import bidvector.workflow.collection.SampleConfirmation
import bidvector.workflow.collection.SampleOutcome
import bidvector.workflow.collection.SampleScope
import bidvector.workflow.collection.SampleStratum
import bidvector.workflow.retention.RETENTION_POLICY
import bidvector.workflow.retention.RetentionPolicyData
import bidvector.workflow.strategy.Clock
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate

private val REFERENCE_DATE = CollectionReferenceDate(LocalDate.of(2026, 10, 23))

private val NOW: Instant = Instant.parse("2026-10-23T00:00:00Z")

private val WALK: Instant = Instant.parse("2026-10-20T01:00:00Z")

/**
 * M7/7C⑤-0 — **출하 조립을 거치는 실 DB 승격**(code-review r1 MEDIUM). 앞 판의 실 DB test 는 어댑터·
 * 순수 사상·저장소를 test 가 직접 엮어서, 「저장 경로가 멱등 술어를 통과해야 쓴다」는 **배선**이 실
 * DB 에서 한 번도 재어지지 않았다. 여기서는 운영자가 실제로 돌리는 조립 그대로 — 실 읽기 어댑터 +
 * 실 canonical 저장소 + 파일 기반 실행 상태(표본·원장) + 보존 정책 — 로 use case 를 세운다.
 *
 * 실행 상태 디렉터리는 test 마다 새로 만든다. 한 JVM 에서 같은 디렉터리를 두 번 열면 잠금이 겹친다.
 */
class OpeningPromotionAssemblyTest : PersistenceTestSupport() {
    private val policy: KonepsCollectionPolicyData =
        (KONEPS_COLLECTION_POLICY.resolve(REFERENCE_DATE.date) as Resolution.Resolved).value

    private val retention: RetentionPolicyData =
        (RETENTION_POLICY.resolve(REFERENCE_DATE.date) as Resolution.Resolved).value

    private fun id(number: String) = NoticeId(NoticeNumber.of(number), NoticeRound.of("000"))

    private fun seed(
        notice: NoticeId,
        endpoint: SourceEndpoint,
        fields: Map<String, String>,
    ) = appendRawObservation(
        RawNoticeObservation.of(
            fields =
                fields.mapKeys { (name, _) -> RawKey(name) } +
                    mapOf(
                        NOTICE_NUMBER_RAW_KEY to notice.number.value,
                        NOTICE_ROUND_RAW_KEY to notice.round.value,
                    ),
            sourceEndpoint = endpoint,
            observedAt = WALK,
            sourceDivision = null,
        ),
    )

    /** 수집이 남긴 모양의 실행 상태 — 확정 표본 하나와 그 공고의 축 결말 하나. */
    private fun runStateFor(
        notice: NoticeId,
        endpoint: SourceEndpoint,
    ): RunStateDirectory {
        val root = Files.createTempDirectory("7c5p-assembly-run-state")
        val runState = RunStateDirectory(root)
        val hash = NoticeKeyHash.of(notice.number.value, notice.round.value)
        val stratum = SampleStratum(BusinessDivision.CONSTRUCTION, "2026-W42")
        runState.sampleList.confirm(
            SampleConfirmation(
                sample =
                    SampleOutcome(
                        selected = listOf(hash),
                        strata = emptyMap(),
                        strataByKey = mapOf(hash to stratum),
                        requested = 1,
                    ),
                scope =
                    SampleScope(
                        LocalDate.of(2026, 10, 1),
                        LocalDate.of(2026, 10, 7),
                        setOf(BusinessDivision.CONSTRUCTION),
                    ),
            ),
        )
        runState.attempts.append(
            CollectionAttempt(
                noticeKey = hash.value,
                axis = endpoint,
                outcome = AttemptOutcome.Succeeded,
                at = WALK,
                kind = AttemptKind.AXIS,
                walk = WALK,
            ),
        )
        return runState
    }

    private fun useCaseOn(runState: RunStateDirectory): PromoteOpeningResultsUseCase =
        PromoteOpeningResultsUseCase(
            rawAxes = JdbcOpeningRawAxisSource(dataSource()),
            results = JdbcOpeningResultRepository(dataSource()),
            attempts = runState.attempts,
            sampleList = runState.sampleList,
            policyFor = { requested -> policyFor(requested) },
            retention = retention,
            referenceDate = REFERENCE_DATE,
            clock = Clock { NOW },
        )

    /** 배선이 넘기는 기준일이 실제로 쓰이는지 함께 잰다 — 인자를 버리는 대역이 아니다. */
    private val policyRequests = mutableListOf<CollectionReferenceDate>()

    private fun policyFor(requested: CollectionReferenceDate): KonepsCollectionPolicyData {
        policyRequests += requested
        return policy
    }

    private fun rowStamp(notice: NoticeId): Pair<Long, Timestamp> =
        dataSource().connection.use { connection ->
            connection
                .prepareStatement(
                    "SELECT revision, updated_at FROM opening_result WHERE notice_number = ? AND notice_round = ?",
                ).use { statement ->
                    statement.setString(1, notice.number.value)
                    statement.setString(2, notice.round.value)
                    statement.executeQuery().use { rows ->
                        rows.next() shouldBe true
                        rows.getLong("revision") to rows.getTimestamp("updated_at")
                    }
                }
        }

    @Test
    fun `출하 조립이 원문을 canonical 로 올리고 둘째 실행은 행을 움직이지 않는다`() {
        val notice = id("PROMO-ASM-001")
        seed(notice, SourceEndpoint.RESERVE_PRICE_DETAIL, mapOf("plnprc" to "1,234,000", "bssamt" to "1,200,000"))
        val runState = runStateFor(notice, SourceEndpoint.RESERVE_PRICE_DETAIL)
        val useCase = useCaseOn(runState)

        val first = useCase.promote().shouldBeInstanceOf<OpeningPromotionRun.Completed>().report
        first.promoted shouldBe 1
        first.groupsRead shouldBe 1
        val stamp = rowStamp(notice)

        val second = useCase.promote().shouldBeInstanceOf<OpeningPromotionRun.Completed>().report

        second.promoted shouldBe 0
        second.skippedUnchanged shouldBe 1
        // 멱등은 **배선**이 진다 — test 가 술어를 직접 부르지 않는다.
        rowStamp(notice) shouldBe stamp
        // 기준일은 배선이 정한 그 값으로 불린다.
        policyRequests.distinct() shouldBe listOf(REFERENCE_DATE)
        runState.close()
    }

    @Test
    fun `표본 밖 공고는 출하 조립에서도 승격되지 않는다`() {
        val sampled = id("PROMO-ASM-002")
        val outside = id("PROMO-ASM-003")
        seed(sampled, SourceEndpoint.RESERVE_PRICE_DETAIL, mapOf("plnprc" to "100"))
        seed(outside, SourceEndpoint.RESERVE_PRICE_DETAIL, mapOf("plnprc" to "200"))
        val runState = runStateFor(sampled, SourceEndpoint.RESERVE_PRICE_DETAIL)

        val report = useCaseOn(runState).promote().shouldBeInstanceOf<OpeningPromotionRun.Completed>().report

        report.promoted shouldBe 1
        report.outsideSample shouldBe 1
        JdbcOpeningResultRepository(dataSource()).find(outside) shouldBe null
        runState.close()
    }

    @Test
    fun `확정 표본이 없으면 출하 조립이 아무것도 쓰지 않는다`() {
        val notice = id("PROMO-ASM-004")
        seed(notice, SourceEndpoint.RESERVE_PRICE_DETAIL, mapOf("plnprc" to "100"))
        val root = Files.createTempDirectory("7c5p-assembly-no-sample")
        val runState = RunStateDirectory(root)

        useCaseOn(runState).promote() shouldBe OpeningPromotionRun.SampleNotConfirmed

        JdbcOpeningResultRepository(dataSource()).find(notice) shouldBe null
        runState.close()
    }
}

package bidvector.adapters.e2e

import bidvector.adapters.event.OutboxPayloadCodec
import bidvector.adapters.koneps.KonepsEnvelopeFixtures
import bidvector.adapters.koneps.KonepsOpenApiNoticeSource
import bidvector.adapters.koneps.MockKonepsResponse
import bidvector.adapters.koneps.MockKonepsServer
import bidvector.adapters.koneps.ServiceKey
import bidvector.adapters.koneps.resolvedCollectionPolicy
import bidvector.adapters.koneps.testCallGate
import bidvector.adapters.koneps.testKonepsHttpPolicy
import bidvector.adapters.persistence.JdbcCollectionRunStore
import bidvector.adapters.persistence.JdbcNoticeRepository
import bidvector.adapters.persistence.JdbcRawObservationStore
import bidvector.adapters.persistence.PersistenceTestSupport
import bidvector.adapters.profile.JdbcOperatorProfileRepository
import bidvector.adapters.qualification.JdbcRequirementStore
import bidvector.procurement.BusinessDivision
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.qualification.LicenseName
import bidvector.qualification.LmtSno
import bidvector.qualification.OperatorLicenses
import bidvector.qualification.RequirementCollection
import bidvector.qualification.RequirementRow
import bidvector.qualification.RequirementSourceField
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Resolution
import bidvector.workflow.collection.COLLECTION_RANGE_POLICY
import bidvector.workflow.collection.CollectNoticesUseCase
import bidvector.workflow.collection.CollectionRange
import bidvector.workflow.collection.CollectionRangeOutcome
import bidvector.workflow.collection.CollectionReport
import bidvector.workflow.collection.CollectionSource
import bidvector.workflow.collection.CollectionSourceName
import bidvector.workflow.evaluation.ProfileFacts
import bidvector.workflow.event.NotificationRequestedPayload
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * M6/6D-1 — mock KONEPS → 수집 → canonical → 요건·면허 gate → ML(in-process fake) → 평가 →
 * outbox → test relay → 발송(fake) 한 줄을 **한 test 프로세스 안에서 production use case·
 * 어댑터로** 잇는 E2E 의 공용 하네스.
 *
 * **왜 `adapters` test 인가**(계약 문면과 다른 자리 — evidence 에 등재). `app` test 의
 * classpath 에는 `ml-contract` 생성 stub 도 `grpc-inprocess` 도 없어 in-process ML 대역을
 * 지을 수 없고(ML timeout·unknown field·unsupported schema·EXACT rollback 네 축이 통째로
 * 불가), 종단 전이 SQL 상수(`EventSql`)도 보이지 않는다. 그 둘이 함께 보이는 유일한 소스셋이
 * 여기이고 `adapters` 의 test 소스셋은 이미 in_scope 다. Testcontainers 하네스·KONEPS mock·
 * ML fixture 도 전부 이 소스셋에 이미 있어 **재사용**한다(바퀴 재발명 금지).
 *
 * 단언은 **DB 상태와 sender 기록**으로만 한다(설계 검토 (1)) — 로그 줄·반환값 문자열로
 * 「지나갔다」를 주장하지 않는다.
 */
internal abstract class PipelineE2ESupport : PersistenceTestSupport() {
    protected fun requirementStore() = JdbcRequirementStore(dataSource())

    protected fun profileRepository() = JdbcOperatorProfileRepository(dataSource())

    /**
     * 공고 목록 수집 — mock 서버가 내는 합성 항목을 **production 수집 경로**(KONEPS 어댑터 →
     * [CollectNoticesUseCase] → 정규화 → `notice` 표)로 넣는다. 같은 script 를 두 번 돌리면
     * 중복 공고 축(②)이 된다.
     */
    protected fun collectNotices(items: List<Map<String, String?>>): CollectionReport {
        val body = KonepsEnvelopeFixtures.success(items, totalCount = items.size, pageNo = 1, numOfRows = 100)
        return MockKonepsServer.start(listOf(MockKonepsResponse.Reply(200, body))).use { server ->
            collectFrom(server)
        }
    }

    private fun collectFrom(server: MockKonepsServer): CollectionReport {
        val source =
            KonepsOpenApiNoticeSource(
                gate = testCallGate(),
                baseUri = server.baseUri,
                serviceKey = ServiceKey.of("e2e-service-key"),
                httpPolicy = testKonepsHttpPolicy(),
                collectionPolicyProvider = ::resolvedCollectionPolicy,
                businessDivision = BusinessDivision.SERVICE,
                clock = java.time.Clock.fixed(E2E_NOW, ZoneOffset.UTC),
            )
        val useCase =
            CollectNoticesUseCase(
                rawObservations = JdbcRawObservationStore(dataSource(), testFieldContracts(), E2E_RELEASE_SHA),
                notices = JdbcNoticeRepository(dataSource()),
                runs = JdbcCollectionRunStore(dataSource()),
                policyFor = ::resolvedCollectionPolicy,
                clock = fixedClock(E2E_NOW),
            )
        return useCase.collect(e2eRange(), listOf(CollectionSource(E2E_SOURCE_NAME, source)))
    }

    private fun e2eRange(): CollectionRange {
        val policy = COLLECTION_RANGE_POLICY.resolve(E2E_DATE)
        check(policy is Resolution.Resolved) { "COLLECTION_RANGE_POLICY 가 $E2E_DATE 에 적용되지 않는다" }
        val outcome = CollectionRange.of(E2E_DATE, E2E_DATE, E2E_DATE, policy.value)
        check(outcome is CollectionRangeOutcome.Valid) { "E2E 수집 범위가 거부됐다: $outcome" }
        return outcome.range
    }

    /** 운영자 프로필 — `OpportunityAnalysis` 가 프로필 없이는 분석을 시작하지 않는다(`findProfile`). */
    protected fun seedProfile(licenses: List<String> = listOf(E2E_LICENSE)) {
        profileRepository().save(
            ProfileFacts(
                businessTypes = emptySet(),
                licenses = OperatorLicenses.Declared(licenses.map(::LicenseName)),
                regionTerms = emptyList(),
            ),
        )
    }

    /** 공고 하나에 면허 요건을 심는다 — 면허 gate 가 실제로 판정을 내게 하는 입력(LLM 추출 체인은 미배선). */
    protected fun seedLicenseRequirement(
        noticeId: NoticeId,
        licenseName: String,
    ) {
        val row =
            RequirementRow.Parsed(
                groupNo = null,
                serialNo = LmtSno("01"),
                sourceField = RequirementSourceField.LcnsLmtNm,
                licenseNames = listOf(LicenseName(licenseName)),
            )
        requirementStore().save(noticeId, RequirementCollection.Collected(listOf(row)))
    }

    /**
     * 전략 단일 행 — 편집 HTTP 경로는 6A 소관이라 직접 INSERT 한다(`EvaluationDryRunE2ETest` 와
     * 같은 알려진 제한). `exclude_keyword_terms` 하나만 채워 감시 규칙을 **비어 있지 않게**
     * 만든다: 비면 `WatchVerdict.NoGate` 라 모든 후보가 감시 단계에서 탈락한다.
     */
    protected fun seedStrategy(
        maxActiveBids: Int = E2E_MAX_ACTIVE_BIDS,
        bidNowThreshold: String = E2E_BID_NOW_THRESHOLD,
        reviewThreshold: String = E2E_REVIEW_THRESHOLD,
    ) {
        dataSource().connection.use { connection ->
            connection
                .prepareStatement(
                    "INSERT INTO operator_strategy (id, revision, focus_categories, focus_region_terms, " +
                        "exclude_region_terms, required_keyword_terms, exclude_keyword_terms, " +
                        "bid_now_threshold, review_threshold, max_active_bids) " +
                        "VALUES (1, 1, ?, ?, ?, ?, ?, ?, ?, ?)",
                ).use { statement ->
                    val empty = connection.createArrayOf("text", emptyArray<String>())
                    statement.setArray(1, empty)
                    statement.setArray(2, empty)
                    statement.setArray(3, empty)
                    statement.setArray(4, empty)
                    statement.setArray(5, connection.createArrayOf("text", arrayOf(E2E_EXCLUDE_TERM)))
                    statement.setBigDecimal(6, BigDecimal(bidNowThreshold))
                    statement.setBigDecimal(7, BigDecimal(reviewThreshold))
                    statement.setInt(8, maxActiveBids)
                    statement.executeUpdate()
                }
        }
    }

    protected fun canonicalNoticeNumbers(): List<String> =
        queryStrings("SELECT notice_number FROM notice ORDER BY notice_number")

    protected fun outboxStates(): List<String> = queryStrings("SELECT state FROM outbox ORDER BY inserted_at, entry_id")

    protected fun outboxPayloads(): List<String> = queryStrings("SELECT payload FROM outbox ORDER BY idempotency_key")

    /**
     * outbox 행 하나를 **production codec 으로 되살려** 타입·필드로 단언할 수 있게 한다
     * (verifier r1 F-5 / review G-3). 직렬화 문자열의 부분 일치는 값이 어느 칸에 있는지를
     * 가르지 못한다 — `bidNowReasons` 가 자유 형식 문자열 목록이라 더 그렇다.
     */
    protected fun decodedOutboxPayload(): NotificationRequestedPayload =
        OutboxPayloadCodec.decode(
            OutboxPayloadCodec.NOTIFICATION_REQUESTED_TYPE,
            outboxPayloads().single(),
        ) as NotificationRequestedPayload

    protected fun outboxIdempotencyKeys(): List<String> =
        queryStrings("SELECT idempotency_key FROM outbox ORDER BY idempotency_key")

    protected fun inboxKeys(): List<String> = queryStrings("SELECT idempotency_key FROM inbox ORDER BY idempotency_key")

    protected fun outboxCorrelationIds(): List<String> =
        queryStrings("SELECT correlation_id FROM outbox ORDER BY inserted_at, entry_id")

    protected fun outboxOccurredAt(): List<String> =
        queryStrings("SELECT occurred_at::text FROM outbox ORDER BY inserted_at, entry_id")

    protected fun queryStrings(sql: String): List<String> =
        dataSource().connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { rs ->
                    generateSequence { if (rs.next()) rs.getString(1) else null }.toList()
                }
            }
        }
}

internal fun noticeIdOf(number: String): NoticeId = NoticeId(NoticeNumber.of(number), NoticeRound.of("000"))

/**
 * `OutboxNotificationRequestPort` 가 짓는 멱등 키의 모양 — test 가 **전체 등식**으로 단언하려면
 * 이 형태가 필요하다(review G-3: 부분 일치는 어느 칸에 있는지를 가르지 못한다). 형식 자체의
 * 정본은 그 production 클래스이고 `OutboxNotificationRequestPortTest` 가 축어로 잠근다.
 */
internal fun idempotencyKeyFor(number: String): String = "notification-requested-$number-000"

/**
 * 합성 공고 항목 — `bssamt`(기초금액)가 있어야 예측 호출이 일어난다(`OpportunityAnalysis`
 * 는 기초금액 없는 공고의 예측을 생략한다). 마감은 평가 시각 뒤라 후보 질의에 걸린다.
 */
internal fun e2eNoticeItem(
    number: String,
    baseAmount: String = E2E_BASE_AMOUNT,
    closing: String = E2E_CLOSING,
): Map<String, String?> =
    mapOf(
        "bidNtceNo" to number,
        "bidNtceOrd" to "000",
        "bidNtceNm" to "E2E 통합 유지관리 용역 $number",
        "bssamt" to baseAmount,
        "bidClseDt" to closing,
    )

/** 수집·평가가 공유하는 고정 시각 — 재현 등식(⑤)의 전제이자 후보 질의의 기준이다. */
internal val E2E_NOW: Instant = Instant.parse("2026-09-24T03:00:00Z")
internal val E2E_DATE: LocalDate = LocalDate.ofInstant(E2E_NOW, ZoneOffset.UTC)
internal val E2E_SOURCE_NAME: CollectionSourceName = checkNotNull(CollectionSourceName.of("service"))
internal const val E2E_RELEASE_SHA = "e2e-6d1"
internal const val E2E_BASE_AMOUNT = "1000000"
internal const val E2E_CLOSING = "2026-12-31 10:00:00"
internal const val E2E_LICENSE = "정보통신공사업"
internal const val E2E_EXCLUDE_TERM = "zzz-없는-제외어"
internal const val E2E_MAX_ACTIVE_BIDS = 10
internal const val E2E_BID_NOW_THRESHOLD = "0.50"
internal const val E2E_REVIEW_THRESHOLD = "0.10"

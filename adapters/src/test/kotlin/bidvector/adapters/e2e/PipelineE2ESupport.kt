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
import io.kotest.matchers.shouldBe
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
     *
     * revision 은 [E2E_STRATEGY_REVISION] 고정이고 **인자가 아니다**(cr G-3) — 넘기는 호출부가
     * 하나도 없는 파라미터는 과잉이고, 전부 기본값인 목록의 맨 앞에 두면 뒤에 누가 positional 로
     * 부를 때 `maxActiveBids` 자리가 조용히 revision 이 된다. run 사이에 revision 을 바꾸는 쪽은
     * [setStrategyRevision] 이 든다.
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
                        "VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                ).use { statement ->
                    val empty = connection.createArrayOf("text", emptyArray<String>())
                    statement.setInt(1, E2E_STRATEGY_REVISION)
                    statement.setArray(2, empty)
                    statement.setArray(3, empty)
                    statement.setArray(4, empty)
                    statement.setArray(5, empty)
                    statement.setArray(6, connection.createArrayOf("text", arrayOf(E2E_EXCLUDE_TERM)))
                    statement.setBigDecimal(7, BigDecimal(bidNowThreshold))
                    statement.setBigDecimal(8, BigDecimal(reviewThreshold))
                    statement.setInt(9, maxActiveBids)
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
    protected fun decodedOutboxPayload(): NotificationRequestedPayload = decodedOutboxPayloads().single()

    /**
     * **행 전부**를 되살린다 — 재현 등식은 두 run 의 payload 를 서로 대조하므로 한 행만 보는
     * 단수형으로는 쓸 수 없다. 타입과 본문을 **한 질의로** 함께 읽는다: 두 열을 따로 질의하면
     * 같은 멱등 키의 행끼리 정렬 순서가 묶이지 않아 타입과 본문의 짝이 어긋날 수 있다.
     *
     * 저장된 타입을 **읽어서** 복원한다 — 넣어서 복원하면 「저장된 타입이 그것이다」가 어디에도
     * 단언되지 않는다(review r2 R-4).
     */
    protected fun decodedOutboxPayloads(): List<NotificationRequestedPayload> =
        outboxTypedPayloads().map { (storedType, payload) ->
            storedType shouldBe OutboxPayloadCodec.NOTIFICATION_REQUESTED_TYPE
            OutboxPayloadCodec.decode(storedType, payload) as NotificationRequestedPayload
        }

    /**
     * 전략 revision 을 **DB 행에서** 바꾼다(설계 검토 (2) 우회 8) — 상수나 조립 인자를 바꾸면
     * `operator_strategy` → repository → 평가 → payload → codec → outbox 사슬이 비어 있어도
     * 값이 달라져 초록이 된다. `app` test 의 `setStrategyRevision` 과 같은 모양이지만 그쪽은
     * 다른 소스셋이라 여기서 쓸 수 없다.
     */
    protected fun setStrategyRevision(revision: Int) =
        dataSource().connection.use { connection ->
            connection.prepareStatement("UPDATE operator_strategy SET revision = ? WHERE id = 1").use { statement ->
                statement.setInt(1, revision)
                check(statement.executeUpdate() == 1) { "전략 행이 없어 revision 을 바꾸지 못했다" }
            }
        }

    protected fun outboxIdempotencyKeys(): List<String> =
        queryStrings("SELECT idempotency_key FROM outbox ORDER BY idempotency_key")

    protected fun inboxKeys(): List<String> = queryStrings("SELECT idempotency_key FROM inbox ORDER BY idempotency_key")

    protected fun outboxCorrelationIds(): List<String> =
        queryStrings("SELECT correlation_id FROM outbox ORDER BY inserted_at, entry_id")

    protected fun outboxOccurredAt(): List<String> =
        queryStrings("SELECT occurred_at::text FROM outbox ORDER BY inserted_at, entry_id")

    private fun outboxTypedPayloads(): List<Pair<String, String>> =
        dataSource().connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(OUTBOX_TYPED_PAYLOADS_SQL).use { rs ->
                    generateSequence { if (rs.next()) rs.getString(1) to rs.getString(2) else null }.toList()
                }
            }
        }

    protected fun queryStrings(sql: String): List<String> =
        dataSource().connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { rs ->
                    generateSequence { if (rs.next()) rs.getString(1) else null }.toList()
                }
            }
        }
}

/** 타입과 본문을 **한 질의로** 읽는다 — 두 열을 따로 질의하면 같은 키의 행끼리 짝이 어긋난다. */
private const val OUTBOX_TYPED_PAYLOADS_SQL = "SELECT payload_type, payload FROM outbox ORDER BY inserted_at, entry_id"

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

/**
 * 시딩 전략의 revision — **어느 기본값과도 겹치지 않는 값**이다. `operator_strategy` 의 첫 행도,
 * `JdbcStrategyRepository` 의 행 부재 대체값(0)도, 앞 판의 시딩값(1)도 아니다.
 */
internal const val E2E_STRATEGY_REVISION = 7
internal const val E2E_BID_NOW_THRESHOLD = "0.50"
internal const val E2E_REVIEW_THRESHOLD = "0.10"

/*
 * outbox 상태 어휘(cr G-7) — **정본은 V6 CHECK 와 전이 SQL** 이고 `main` 에는 이 문자열을 담은
 * Kotlin 상수가 없다(그래서 test 상수를 둔다). 자리를 이 파일로 둔 이유는 세 e2e 클래스가 함께
 * 쓰기 때문이다 — 크래시 주입 파일에 두면 그 파일의 주제와 무관한 어휘가 섞인다.
 *
 * 같은 어휘가 `bidvector.adapters.relay` 에는 축어 리터럴로 남아 있다 — 그 패키지는 이 slice 의
 * in_scope 밖이다. 거동 위험은 없다: 양쪽 모두 DB 가 낸 문자열과 대조하므로 production 이 어휘를
 * 바꾸면 fail-closed 로 붉어진다.
 */
internal const val PENDING_STATE = "PENDING"
internal const val CLAIMED_STATE = "CLAIMED"
internal const val DELIVERED_STATE = "DELIVERED"
internal const val ISOLATED_STATE = "ISOLATED"

/*
 * 스레드 경합 test 의 시한 셋(cr G-6) — 값·역할·사유가 같은 사본이 이 패키지에 **둘** 있었다.
 * 선례(`PipelineFailureInjectionE2ETest`)의 것은 `private companion object` 라 재사용이 막혀
 * 있었고, 이 slice 가 top-level `internal` 로 같은 셋을 또 지었다. 집을 하나로 모으고 선례의
 * companion 상수를 지웠다.
 *
 * **여기는 `e2e` 패키지의 자리다**(PR #64 F6) — 같은 값·같은 역할의 **셋째 사본**이
 * `adapters.event` 의 claim 경합 test(6F-10 산출물)에 따로 있다. 그 파일은 이 slice 의 in_scope
 * 밖이라 합치지 않았고, 합치는 것은 그 파일을 만지는 다음 slice 다(checklist 알려진 제한).
 *
 * 이름이 두 쓰임을 함께 담는다 — 「신호」는 「첫 워커가 행을 집었다」(claim 경합)와 「홀더가
 * 막혔다」(임대 경합) 둘이다.
 */
internal const val E2E_SIGNAL_TIMEOUT_SECONDS = 5L

/**
 * 첫 스레드가 **쥐고 있는** 시한. 상대가 돌아오면 즉시 풀리므로 정상 경로의 비용은 0 이고, 이
 * 값은 **거짓 RED 의 여유**로만 쓰인다 — 호스트가 느려 상대의 한 질의가 오래 걸리면 쥔 쪽이
 * 조기에 풀려 막히지 않았는데도 막힌 것처럼 보인다. 넉넉히 둔다.
 */
internal const val E2E_HOLD_TIMEOUT_SECONDS = 30L

/** 합류 시한 — 쥠 시한보다 커야 그 만료가 합류 실패로 가려지지 않는다. */
internal const val E2E_JOIN_TIMEOUT_SECONDS = 60L

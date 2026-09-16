package bidvector.adapters.ml

import bidvector.decision.MlUnavailableReason
import bidvector.decision.ProvenancePolicyData
import bidvector.procurement.Notice
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.procurement.NoticeRepository
import bidvector.procurement.OpeningResult
import bidvector.procurement.OpeningResultRepository
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Resolution
import bidvector.workflow.evaluation.CompetitionSamplePort
import bidvector.workflow.evaluation.CompetitionSampleQuery
import bidvector.workflow.evaluation.CompetitionSampleSupply
import bidvector.workflow.evaluation.SAMPLE_ELIGIBILITY_POLICY
import bidvector.workflow.evaluation.SAMPLE_PROVENANCE_POLICY
import bidvector.workflow.evaluation.SampleEligibilityOutcome
import bidvector.workflow.evaluation.SampleEligibilityPolicyData
import bidvector.workflow.evaluation.SampleExclusionReason
import bidvector.workflow.evaluation.judgeEligibility
import bidvector.workflow.prediction.CompetitionSample
import java.sql.PreparedStatement
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import javax.sql.DataSource

/**
 * [CompetitionSamplePort] JDBC 구현(M4/4B-7, D-4B7-3·4). **`adapters.persistence`가 아니라
 * `adapters.ml` 패키지에 둔다** — `PersistenceAdapterDependencyTest`가 `persistence` 패키지의
 * domain import를 `procurement`·`shared-kernel`로 좁게 막는데(`bidvector.decision`·
 * `bidvector.workflow` 거부), 이 클래스는 `judgeEligibility`(`decision.ProvenancePolicyData`
 * 소비)와 `CompetitionSamplePort`(`workflow.evaluation`)를 직접 다뤄 그 경계를 넘는다.
 * `MlAdapterDependencyTest`는 정확히 이 넷(`procurement`·`decision`·`sharedkernel`·
 * `workflow`)을 허용 목록으로 둔다 — 이 slice의 실제 배치는 scope.md in_scope가 적은
 * `adapters/persistence/JdbcCompetitionSampleSource.kt` 경로에서 빗나갔다(구현 중 게이트
 * 실측으로 발견, `reports/evidence/m4/4b7/checklist.md` 「설계 이탈」 절).
 *
 * **`adapters.persistence`의 `JdbcNoticeRepository`·`JdbcOpeningResultRepository` 구현체를
 * 직접 import하지 않는다** — 같은 게이트(`MlAdapterDependencyTest`)가 `bidvector.adapters.*`
 * 형제 패키지 참조도 허용 목록 밖으로 거부한다(실측: 첫 배치가 이 경계에도 걸렸다). 그래서
 * 이 클래스는 `procurement`가 이미 소유한 port 인터페이스([NoticeRepository]·
 * [OpeningResultRepository])만 생성자로 받는다 — 실 구현(JDBC)은 조립 근(M6, `app`)이
 * 주입한다. `SELECT_COMPETITION_SAMPLE_CANDIDATES`도 같은 이유로 `Sql.kt`(persistence)가
 * 아니라 이 파일에 직접 둔다(공유하면 그 참조 자체가 게이트에 걸린다 — 중복이 아니라
 * 경계가 강제한 위치다).
 *
 * RO 조회만(쓰기 0, 트랜잭션 없음) — `candidateIds`가 공종·대상 제외·개찰일 창(초과 집합)
 * 으로 후보 `NoticeId`를 좁히고, 자격 판정·라벨링·변환은 workflow 순수 함수
 * (`SampleEligibility.judgeEligibility`)를 그대로 불러 진다 — 이 어댑터는 판정 로직을
 * 갖지 않는다(D-4B7-4 「판정을 SQL에 두지 않는다」).
 *
 * 후보 하나마다 [NoticeRepository.find]·[OpeningResultRepository.find]를 그대로 재사용해
 * `Notice`·`OpeningResult`를 복원한다(기존 복원 경로 재사용 — 손으로 다시 짜지 않는다).
 * N+1 조회다 — 인덱스·성능은 이 slice 밖이다(D-4B7-6, `OPEN-4B7-QUERY-INDEX`).
 *
 * DB 예외는 [CompetitionSampleSupply.Unavailable] 값으로 접는다(D-4B7-9, 위협 모델 (8)) —
 * 예외를 호출부(`OpportunityAnalysis`)까지 전파하지 않는다.
 */
class JdbcCompetitionSampleSource(
    private val dataSource: DataSource,
    private val noticeRepository: NoticeRepository,
    private val openingResultRepository: OpeningResultRepository,
    private val clock: Clock = Clock.systemUTC(),
) : CompetitionSamplePort {
    override fun samplesFor(query: CompetitionSampleQuery): CompetitionSampleSupply =
        try {
            aggregate(candidateIds(query))
        } catch (
            @Suppress("SwallowedException") failure: SQLException,
        ) {
            CompetitionSampleSupply.Unavailable(MlUnavailableReason.TransportFailed)
        }

    /**
     * verifier r1 F-3 — `candidatePair`가 `null`이면(스캔과 복원 사이 행 소실, KDoc
     * 참고) `continue`로 조용히 건너뛰지 않고 `CANDIDATE_VANISHED`로 계수한다. 합계
     * 불변식 `samples.size + excluded.values.sum() == ids.size`가 이 함수 밖에서 항상
     * 성립한다(`JdbcCompetitionSampleSourceTest` 실측).
     */
    private fun aggregate(ids: List<NoticeId>): CompetitionSampleSupply {
        val referenceDate = LocalDate.now(clock)
        val eligibilityPolicy = resolveEligibilityPolicy(referenceDate)
        val provenancePolicy = resolveProvenancePolicy(referenceDate)
        val samples = mutableListOf<CompetitionSample>()
        val excluded = mutableMapOf<SampleExclusionReason, Int>()
        for (id in ids) {
            when (val pair = candidatePair(id)) {
                null -> {
                    excluded.merge(SampleExclusionReason.CANDIDATE_VANISHED, 1, Int::plus)
                }

                else -> {
                    val outcome = judgeEligibility(pair.first, pair.second, eligibilityPolicy, provenancePolicy)
                    recordOutcome(outcome, samples, excluded)
                }
            }
        }
        return CompetitionSampleSupply.Supplied(samples, excluded)
    }

    private fun recordOutcome(
        outcome: SampleEligibilityOutcome,
        samples: MutableList<CompetitionSample>,
        excluded: MutableMap<SampleExclusionReason, Int>,
    ) {
        when (outcome) {
            is SampleEligibilityOutcome.Eligible -> samples += outcome.sample
            is SampleEligibilityOutcome.Excluded -> excluded.merge(outcome.reason, 1, Int::plus)
        }
    }

    /**
     * notice·opening 둘 다 있어야 판정 대상이다(둘 다 기존 repository `find` 재사용).
     * `null`은 스캔(SQL)과 이 복원 사이에 행이 사라졌다는 뜻이다 — `CANDIDATE_VANISHED`로
     * 계수된다(verifier r1 F-3, `aggregate` KDoc).
     */
    private fun candidatePair(id: NoticeId): Pair<Notice, OpeningResult>? =
        noticeRepository.find(id)?.let { notice ->
            openingResultRepository.find(id)?.let { opening -> notice to opening }
        }

    /**
     * `Initial` 한 entry뿐이라 항상 resolve되지만, `resolve(referenceDate)`를 쓴다(verifier
     * r1 F-2) — `.single()`은 entry가 하나 더 붙는 순간(policy-values.md가 예고한 5C/5E
     * 갱신) `IllegalArgumentException`을 던지고 `catch(SQLException)`가 못 잡는다.
     * `error()`는 배선 방어다.
     */
    private fun resolveEligibilityPolicy(referenceDate: LocalDate): SampleEligibilityPolicyData =
        when (val resolution = SAMPLE_ELIGIBILITY_POLICY.resolve(referenceDate)) {
            is Resolution.Resolved -> {
                resolution.value
            }

            is Resolution.NotApplicable -> {
                error("SAMPLE_ELIGIBILITY_POLICY 가 $referenceDate 에 적용되지 않는다: ${resolution.reason}")
            }
        }

    /** `SAMPLE_PROVENANCE_POLICY`는 `Initial` 한 entry뿐이라 항상 resolve된다 — `error()`는 배선 방어다. */
    private fun resolveProvenancePolicy(referenceDate: LocalDate): Resolution.Resolved<ProvenancePolicyData> =
        when (val resolution = SAMPLE_PROVENANCE_POLICY.resolve(referenceDate)) {
            is Resolution.Resolved -> {
                resolution
            }

            is Resolution.NotApplicable -> {
                error("SAMPLE_PROVENANCE_POLICY 가 $referenceDate 에 적용되지 않는다: ${resolution.reason}")
            }
        }

    private fun candidateIds(query: CompetitionSampleQuery): List<NoticeId> =
        dataSource.connection.use { connection ->
            connection.prepareStatement(SELECT_COMPETITION_SAMPLE_CANDIDATES).use { statement ->
                bindCandidateQuery(statement, query)
                statement.executeQuery().use { rs ->
                    val ids = mutableListOf<NoticeId>()
                    while (rs.next()) {
                        ids +=
                            NoticeId(
                                NoticeNumber(rs.getString("notice_number")),
                                NoticeRound(rs.getString("notice_round")),
                            )
                    }
                    ids
                }
            }
        }

    private fun bindCandidateQuery(
        statement: PreparedStatement,
        query: CompetitionSampleQuery,
    ) {
        var index = 1
        statement.setString(index++, query.categoryCode.value)
        statement.setString(index++, query.excludeNoticeId.number.value)
        statement.setString(index++, query.excludeNoticeId.round.value)
        statement.setTimestamp(index++, Timestamp.from(query.asOf))
        statement.setTimestamp(index++, Timestamp.from(query.asOf.minus(Duration.ofDays(query.windowDays.toLong()))))
        statement.setInt(index, query.limit)
    }

    private companion object {
        // M4/4B-7(D-4B7-3·4) — 다건 스캔 SELECT 첫 사례. 공종 일치(정확 일치 — D-4B7-2 우회
        // (16) 실측: `CategoryCode.init`이 공백/대소문자를 정규화·거부하지 않는다, 팀장
        // 지시에 따라 조회 술어에 정규화를 두지 않고 팀장에게 보고) · 대상 공고 자신 제외 ·
        // 개찰일 창(미래 표본 누출 방지, 우회 (1))은 **개찰일 결측 행을 걸러내지 않는다** —
        // 결측 행도 초과 집합으로 후보에 오르고, 실격은 workflow `SampleEligibility`
        // (OPENING_DATE_MISSING)가 진다(D-4B7-4, 설계 검토 (5)-4). 정렬은 최신 순(우회
        // (13)), 결측 날짜는 맨 뒤로(`NULLS LAST`) — 유효한 날짜를 가진 후보가 상한
        // (`LIMIT`)을 먼저 채운다. 자격(예비가격 건수·1위 투찰율 등)은 SQL 밖(워크플로 순수
        // 함수)이 진다 — 여기서는 조인만.
        const val SELECT_COMPETITION_SAMPLE_CANDIDATES =
            """
            SELECT n.notice_number, n.notice_round
            FROM notice n
            JOIN opening_result o ON o.notice_number = n.notice_number AND o.notice_round = n.notice_round
            WHERE n.business_category_code = ?
              AND NOT (n.notice_number = ? AND n.notice_round = ?)
              AND (o.actual_opening_at IS NULL OR (o.actual_opening_at < ? AND o.actual_opening_at >= ?))
            ORDER BY o.actual_opening_at DESC NULLS LAST
            LIMIT ?
            """
    }
}

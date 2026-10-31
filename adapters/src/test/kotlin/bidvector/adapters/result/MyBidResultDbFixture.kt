package bidvector.adapters.result

import bidvector.adapters.bidding.BidCaseDbFixture
import bidvector.adapters.snapshot.jsonbKeyOf
import bidvector.bidding.BidCaseId
import bidvector.bidding.BidCommand
import bidvector.bidding.BidKind
import bidvector.bidding.BidMemo
import bidvector.bidding.NoticeKey
import bidvector.procurement.NOTICE_NUMBER_RAW_KEY
import bidvector.procurement.NOTICE_ROUND_RAW_KEY
import bidvector.workflow.access.AccountId
import bidvector.workflow.bidding.BidActor
import java.sql.Timestamp

/**
 * 결과 조회 실 DB test 의 **행 심기**(`OpeningViewDbFixture` 와 같은 관례).
 *
 * 거동 단언은 두지 않는다 — 심기만 한다. 「그 상태에서 조회가 무엇을 답해야 하는가」는
 * `JdbcMyBidResultViewTest` 가 진다. 자기 파일로 가른 이유는 **크기 한도**다: 한 파일에 두면 그
 * 한도가 심기와 단언 사이의 경합이 된다(`HttpTestApplication` 과 같은 사유).
 */
abstract class MyBidResultDbFixture : BidCaseDbFixture() {
    /** 건 하나를 열고 투찰을 기록한다 — 출하 전이 경로를 그대로 쓴다(손으로 INSERT 하지 않는다). */
    protected fun recordBid(
        account: AccountId,
        key: NoticeKey,
        kind: BidKind = BidKind.REAL,
        won: Long = 1_000_000L,
        memo: BidMemo? = null,
        draft: Boolean = true,
    ): BidCaseId {
        val workflow = workflow()
        val caseId =
            workflow
                .open(account, key, kind)
                .view.case.id
        // 초안은 공고 스냅샷을 요구한다 — 공고 행을 못 찾는 건은 초안 없이 바로 기록한다
        // (전이표가 「관심」에서 RECORD_BID 를 연다).
        if (draft) workflow.draft(account, caseId, amount(won), now, BidActor.User(account))
        workflow.command(
            account,
            caseId,
            BidCommand.RecordBid(submission().copy(amount = amount(won), memo = memo), null),
            BidActor.User(account),
        )
        return caseId
    }

    /** 개찰 결과 축 **원문** 한 행 — 계약 등재 키로 심는다(손 리터럴이 아니다). */
    protected fun seedOpeningAxisRaw(
        rawNumber: String,
        rawRound: String,
        masked: Boolean = false,
    ) {
        val fields = """{"${jsonbKeyOf(
            NOTICE_NUMBER_RAW_KEY,
        )}":"$rawNumber","${jsonbKeyOf(NOTICE_ROUND_RAW_KEY)}":"$rawRound"}"""
        dataSource().connection.use { connection ->
            connection
                .prepareStatement(
                    "INSERT INTO raw_observation (observation_key, source_endpoint, payload, payload_fields, " +
                        "observed_at, release_sha, inserted_at) VALUES (?, 'OPENING_RESULT_LIST', '{}', " +
                        "?::jsonb, ?, 'test', ?)",
                ).use { statement ->
                    statement.setString(1, "obs-rawaxis-$rawNumber-$rawRound")
                    statement.setString(2, fields)
                    statement.setTimestamp(3, Timestamp.from(now))
                    statement.setTimestamp(4, Timestamp.from(now))
                    statement.executeUpdate()
                }
            // **적재는 표지를 찍지 못한다**(DB 트리거) — 마스킹은 보존 작업의 UPDATE 로만 선다.
            if (masked) {
                connection
                    .prepareStatement("UPDATE raw_observation SET pii_masked_at = now() WHERE observation_key = ?")
                    .use { statement ->
                        statement.setString(1, "obs-rawaxis-$rawNumber-$rawRound")
                        statement.executeUpdate()
                    }
            }
        }
    }

    protected fun seedOpeningResult(
        key: NoticeKey,
        winner: String?,
        progress: String = "개찰완료",
    ) {
        val observationKey = "obs-open-${key.noticeNumber}"
        dataSource().connection.use { connection ->
            connection
                .prepareStatement(
                    "INSERT INTO raw_observation (observation_key, source_endpoint, payload, payload_fields, " +
                        "observed_at, release_sha, inserted_at) VALUES (?, 'OPENING_RESULT_LIST', '{}', " +
                        "'{}'::jsonb, ?, 'test', ?)",
                ).use { statement ->
                    statement.setString(1, observationKey)
                    statement.setTimestamp(2, Timestamp.from(now))
                    statement.setTimestamp(3, Timestamp.from(now))
                    statement.executeUpdate()
                }
            connection
                .prepareStatement(
                    "INSERT INTO opening_result (notice_number, notice_round, observed_at, observation_key, " +
                        "progress_division, final_award_company_name) VALUES (?, ?, ?, ?, ?, ?)",
                ).use { statement ->
                    statement.setString(1, key.noticeNumber)
                    statement.setString(2, key.noticeRound.value)
                    statement.setTimestamp(3, Timestamp.from(now))
                    statement.setString(4, observationKey)
                    statement.setString(5, progress)
                    statement.setString(6, winner)
                    statement.executeUpdate()
                }
        }
    }
}

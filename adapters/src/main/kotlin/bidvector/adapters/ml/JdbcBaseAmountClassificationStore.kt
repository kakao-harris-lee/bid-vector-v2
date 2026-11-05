package bidvector.adapters.ml

import bidvector.decision.ProvenanceJudgement
import bidvector.decision.ProvenanceRuleId
import bidvector.procurement.NoticeId
import bidvector.sharedkernel.BaseAmountProvenance
import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.export
import bidvector.workflow.evaluation.BaseAmountClassificationRecordPort
import javax.sql.DataSource

/**
 * [BaseAmountClassificationRecordPort] JDBC 구현(M9 DEC-08, V26).
 *
 * **`adapters.ml` 패키지에 둔다** — `JdbcCompetitionSampleSource` 와 **같은 사유**다: 이
 * 클래스는 `bidvector.decision`(판정 봉투·규칙 id)과 `bidvector.workflow.evaluation`(port)을
 * 동시에 다루고, `MlAdapterDependencyTest` 만이 그 넷(`procurement`·`decision`·`sharedkernel`·
 * `workflow`)을 허용한다. `adapters.persistence` 의 allow-list(procurement·shared-kernel)를
 * 넓히지 않는다. 같은 게이트가 형제 adapter 패키지 참조도 막으므로 SQL 과 커넥션 획득을 이
 * 파일이 직접 든다(공유하면 그 참조 자체가 게이트에 걸린다 — 중복이 아니라 경계가 정한 위치다).
 *
 * **자기 커넥션으로 쓴다**(별 트랜잭션) — 판정 기록은 감사 증적이고, 호출부(평가 분석)는
 * 트랜잭션 경계를 갖지 않는다. 알려진 제한: 분석이 뒤에서 실패하면 **기록만 남는다**(없는
 * 판정을 적지 않는다 — 판정은 이미 났다). 반대 방향(판정은 났는데 기록이 없다)은 쓰기 실패
 * 때 생기고, 그쪽이 「검증하지 않은 것을 기록」보다 정직하다(OPS-10 ML 릴리스 감사 원칙).
 */
class JdbcBaseAmountClassificationStore(
    private val dataSource: DataSource,
) : BaseAmountClassificationRecordPort {
    override fun record(
        noticeId: NoticeId,
        judgement: ProvenanceJudgement,
    ) {
        val amount = judgement.original.export()
        dataSource.connection.use { connection ->
            connection.prepareStatement(INSERT_CLASSIFICATION).use { statement ->
                // 3D 저장소 관례 — 인자 번호를 리터럴로 적지 않는다(열 하나가 들어오면 전부 어긋난다).
                var index = 1
                statement.setString(index++, noticeId.number.value)
                statement.setString(index++, noticeId.round.value)
                statement.setString(index++, judgement.evidence.policyVersion.source)
                statement.setString(index++, effectiveFromToken(judgement.evidence.policyVersion.effectiveFrom))
                statement.setString(index++, classificationToken(judgement.classification))
                statement.setString(index++, judgement.evidence.firstMatchedRule?.let(::ruleToken))
                statement.setLong(index++, amount.won)
                statement.setString(index++, amount.currency.name)
                statement.setString(index, judgement.original.vatTreatment.name)
                statement.executeUpdate()
            }
        }
    }
}

/**
 * `ON CONFLICT DO NOTHING` — 같은 공고를 같은 정책 version 으로 다시 판정하면 **같은 사실**
 * 이다(멱등). `DO UPDATE` 는 두지 않는다: 이 표는 append-only 이고 트리거가 UPDATE 를 거부한다.
 */
private const val INSERT_CLASSIFICATION =
    """
    INSERT INTO base_amount_classification (
        notice_number, notice_round, policy_source, policy_effective_from,
        classification, first_matched_rule,
        judged_amount_won, judged_amount_currency, judged_amount_vat
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
    ON CONFLICT (notice_number, notice_round, policy_source, policy_effective_from) DO NOTHING
    """

/** `EffectiveFrom`(sealed)의 저장 인코딩 — 소진 `when` 이라 변이가 하나 늘면 컴파일이 깨진다. */
private fun effectiveFromToken(effectiveFrom: EffectiveFrom): String =
    when (effectiveFrom) {
        EffectiveFrom.Initial -> "INITIAL"
        is EffectiveFrom.On -> effectiveFrom.date.toString()
    }

/** 라벨 다섯의 저장 토큰 — V26 CHECK 어휘와 같은 집합이고 그 등식을 test 가 잰다. */
private fun classificationToken(classification: BaseAmountProvenance): String =
    when (classification) {
        BaseAmountProvenance.Clean -> "CLEAN"
        BaseAmountProvenance.DerivedYega -> "DERIVED_YEGA"
        BaseAmountProvenance.DerivedVat -> "DERIVED_VAT"
        BaseAmountProvenance.SuspectRatio -> "SUSPECT_RATIO"
        BaseAmountProvenance.Unknown -> "UNKNOWN"
    }

/** 규칙 넷의 저장 토큰 — legacy 술어 어휘를 대문자로 옮긴 것이고 커널의 결속을 따른다. */
private fun ruleToken(rule: ProvenanceRuleId): String =
    when (rule) {
        ProvenanceRuleId.SuspectRatio -> "SUSPECT_RATIO"
        ProvenanceRuleId.CleanInteger -> "CLEAN_INTEGER"
        ProvenanceRuleId.DerivedYega -> "DERIVED_YEGA"
        ProvenanceRuleId.DerivedVat -> "DERIVED_VAT"
    }

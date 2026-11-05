package bidvector.adapters.ml

import bidvector.adapters.persistence.PersistenceTestSupport
import bidvector.decision.ProvenanceEvidence
import bidvector.decision.ProvenanceJudgement
import bidvector.decision.ProvenanceRuleId
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.sharedkernel.BaseAmount
import bidvector.sharedkernel.BaseAmountProvenance
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.Fact
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.PolicyVersion
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.ReasonCode
import bidvector.sharedkernel.VatTreatment
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import java.sql.SQLException
import java.time.LocalDate

private val NOTICE = NoticeId(NoticeNumber.of("20260101001"), NoticeRound.of("001"))

/** 판정 대상 원본 금액 — 값 자체는 중요하지 않고 **그대로 남는지**가 중요하다. */
private const val JUDGED_WON = 1_000_000L

/**
 * M9/DEC-08 — 「분류에 사용된 정책 version 이 결과와 함께 저장된다」를 실 DB 로 잰다(V26).
 *
 * 앞 판에는 그 값을 담는 칸이 **0** 이었고 커널의 근거는 메모리에서 사라졌다. 여기서 재는 것은
 * ① 기록 경로가 라벨·규칙·정책 두 축·원본 금액을 함께 적는가 ② 같은 정책 version 재판정이
 * 멱등인가(정책이 바뀌면 새 행) ③ 판정 이력을 고쳐 쓸 수 없는가 ④ 라벨↔규칙 결속이 저장 층에서
 * 닫히는가 ⑤ CHECK 어휘가 Kotlin 라벨 전수와 **같은 집합**인가.
 */
class BaseAmountClassificationStoreTest : PersistenceTestSupport() {
    @Test
    fun `판정은 라벨·규칙·정책 두 축·원본 금액과 함께 기록된다`() {
        JdbcBaseAmountClassificationStore(dataSource()).record(NOTICE, judgement())

        row() shouldBe
            listOf(
                "20260101001",
                "001",
                "test-policy",
                "2026-01-01",
                "CLEAN",
                "CLEAN_INTEGER",
                "1000000",
                "KRW",
                "EXCLUSIVE",
            )
    }

    @Test
    fun `매치 없음은 UNKNOWN 이고 규칙 칸이 비어 있다`() {
        JdbcBaseAmountClassificationStore(dataSource())
            .record(NOTICE, judgement(classification = BaseAmountProvenance.Unknown, rule = null))

        countRows() shouldBe 1
        selectOne("SELECT classification FROM base_amount_classification") shouldBe "UNKNOWN"
        selectOne("SELECT first_matched_rule FROM base_amount_classification") shouldBe null
    }

    @Test
    fun `같은 정책 version 으로 다시 판정하면 같은 행이다 — 멱등`() {
        val store = JdbcBaseAmountClassificationStore(dataSource())

        store.record(NOTICE, judgement())
        store.record(NOTICE, judgement())

        countRows() shouldBe 1
    }

    @Test
    fun `정책 version 이 다르면 새 행이다 — 과거 판정이 덮이지 않는다`() {
        val store = JdbcBaseAmountClassificationStore(dataSource())

        store.record(NOTICE, judgement())
        store.record(NOTICE, judgement(effectiveFrom = EffectiveFrom.On(LocalDate.parse("2026-06-01"))))

        countRows() shouldBe 2
    }

    @Test
    fun `Initial 정책은 날짜 sentinel 없이 INITIAL 로 남는다`() {
        JdbcBaseAmountClassificationStore(dataSource())
            .record(NOTICE, judgement(effectiveFrom = EffectiveFrom.Initial))

        selectOne("SELECT policy_effective_from FROM base_amount_classification") shouldBe "INITIAL"
    }

    @Test
    fun `판정 이력은 고쳐 쓸 수도 지울 수도 없다 — append-only`() {
        JdbcBaseAmountClassificationStore(dataSource()).record(NOTICE, judgement())

        shouldThrow<SQLException> {
            execute("UPDATE base_amount_classification SET classification = 'SUSPECT_RATIO'")
        }
        shouldThrow<SQLException> { execute("DELETE FROM base_amount_classification") }

        selectOne("SELECT classification FROM base_amount_classification") shouldBe "CLEAN"
    }

    /**
     * 결속 전수 — 커널의 `classificationFor`(규칙 → 라벨)와 **다른** 쌍은 거부다. 어휘만 세는
     * CHECK 둘로 나누면 `CLEAN` + `DERIVED_VAT` 같은 조합이 통과한다.
     */
    @Test
    fun `라벨과 규칙이 어긋난 행은 CHECK 가 거부한다 — 결속 전수`() {
        val bound =
            mapOf(
                "SUSPECT_RATIO" to "SUSPECT_RATIO",
                "CLEAN" to "CLEAN_INTEGER",
                "DERIVED_YEGA" to "DERIVED_YEGA",
                "DERIVED_VAT" to "DERIVED_VAT",
            )

        bound.forEach { (label, rule) ->
            bound.values.filter { it != rule }.forEach { wrongRule ->
                shouldThrow<SQLException> { insertRaw(label, wrongRule) }
                    .message
                    .orEmpty() shouldContain "base_amount_classification_rule_label_check"
            }
            // 「규칙이 있는데 UNKNOWN」·「규칙이 없는데 라벨이 있음」도 같은 제약이 막는다.
            shouldThrow<SQLException> { insertRaw("UNKNOWN", rule) }
                .message
                .orEmpty() shouldContain "base_amount_classification_rule_label_check"
            shouldThrow<SQLException> { insertRaw(label, null) }
                .message
                .orEmpty() shouldContain "base_amount_classification_rule_label_check"
            // 맞는 쌍은 통과한다 — 양성 대조(술어가 늘 거부만 하는 회귀를 막는다).
            insertRaw(label, rule, round = "00${bound.keys.indexOf(label) + 1}")
        }
        countRows() shouldBe bound.size
    }

    /**
     * **집합 등식** — 거부 실측은 아는 토큰만 겨누므로 CHECK 가 모르는 라벨을 더 받아들이게
     * 넓어져도 초록이다. 제약 본문에서 토큰을 뽑아 Kotlin 라벨 전수와 대조한다.
     */
    @Test
    fun `라벨 CHECK 어휘가 Kotlin BaseAmountProvenance 전수와 같다`() {
        val definition = constraintDefinition("base_amount_classification_classification_check")

        val tokens = Regex("'([A-Z_]+)'::text").findAll(definition).map { it.groupValues[1] }.toSet()

        tokens shouldBe setOf("CLEAN", "DERIVED_YEGA", "DERIVED_VAT", "SUSPECT_RATIO", "UNKNOWN")
        // 커널 쪽 전수가 다섯이라는 사실도 함께 고정한다(한쪽만 늘면 이 단언이 깨진다).
        allLabels().size shouldBe tokens.size
    }

    private fun allLabels(): Set<BaseAmountProvenance> =
        setOf(
            BaseAmountProvenance.Clean,
            BaseAmountProvenance.DerivedYega,
            BaseAmountProvenance.DerivedVat,
            BaseAmountProvenance.SuspectRatio,
            BaseAmountProvenance.Unknown,
        )

    private fun judgement(
        classification: BaseAmountProvenance = BaseAmountProvenance.Clean,
        rule: ProvenanceRuleId? = ProvenanceRuleId.CleanInteger,
        effectiveFrom: EffectiveFrom = EffectiveFrom.On(LocalDate.parse("2026-01-01")),
    ): ProvenanceJudgement =
        ProvenanceJudgement(
            original =
                BaseAmount(
                    JUDGED_WON,
                    Currency.KRW,
                    VatTreatment.EXCLUSIVE,
                    Provenance.Published(NOTICE.round),
                ),
            classification = classification,
            evidence =
                ProvenanceEvidence(
                    firstMatchedRule = rule,
                    ruleOrder = listOf(ProvenanceRuleId.SuspectRatio, ProvenanceRuleId.CleanInteger),
                    policyVersion = PolicyVersion(effectiveFrom, "test-policy"),
                ),
            recoveryEstimate = Fact.Absent(ReasonCode.POLICY_NOT_APPLICABLE),
        )

    private fun insertRaw(
        label: String,
        rule: String?,
        round: String = "009",
    ) = dataSource().connection.use { connection ->
        connection
            .prepareStatement(
                "INSERT INTO base_amount_classification (notice_number, notice_round, policy_source, " +
                    "policy_effective_from, classification, first_matched_rule, judged_amount_won, " +
                    "judged_amount_currency, judged_amount_vat) " +
                    "VALUES ('20260101001', ?, 'test-policy', 'INITIAL', ?, ?, 1, 'KRW', 'EXCLUSIVE')",
            ).use { statement ->
                statement.setString(1, round)
                statement.setString(2, label)
                statement.setString(3, rule)
                statement.executeUpdate()
            }
    }

    private fun row(): List<String?> =
        dataSource().connection.use { connection ->
            connection
                .prepareStatement(
                    "SELECT notice_number, notice_round, policy_source, policy_effective_from, classification, " +
                        "first_matched_rule, judged_amount_won::text, judged_amount_currency, judged_amount_vat " +
                        "FROM base_amount_classification",
                ).use { statement ->
                    statement.executeQuery().use { rs ->
                        check(rs.next()) { "판정 행이 없다" }
                        (1..9).map { rs.getString(it) }
                    }
                }
        }

    private fun countRows(): Int = selectOne("SELECT count(*)::text FROM base_amount_classification")?.toInt() ?: 0

    private fun constraintDefinition(name: String): String =
        dataSource().connection.use { connection ->
            connection
                .prepareStatement("SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = ?")
                .use { statement ->
                    statement.setString(1, name)
                    statement.executeQuery().use { rs ->
                        check(rs.next()) { "제약을 찾지 못했다: $name" }
                        rs.getString(1)
                    }
                }
        }

    private fun selectOne(sql: String): String? =
        dataSource().connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
            }
        }

    private fun execute(sql: String) =
        dataSource().connection.use { connection -> connection.createStatement().use { it.execute(sql) } }
}

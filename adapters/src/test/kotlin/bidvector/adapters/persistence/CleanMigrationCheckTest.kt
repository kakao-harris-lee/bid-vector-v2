package bidvector.adapters.persistence

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * S-5 축 8(CHECK) — `CleanMigrationTest`(sizeGate 500줄, v2-지침서.md §5)에서 분리한 파일 —
 * `CleanMigrationTriggerTest`(축 7)와 같은 이유·같은 전례다. D-3D-6 여덟 축 중 CHECK 하나만
 * 다룬다(다른 일곱 축은 `CleanMigrationTest`).
 *
 * **M3/3E — 층 B·C 신규 CHECK(추가만, 스키마 스냅샷 래칫 예외 운영자 승인 2026-09-08)** —
 * `opening_result`가 5(3E 착수 시점 V1)에서 11·14(provenance 페어 3, verifier r1 H-1 뒤)로,
 * `opening_reserve_price`(신규 자식 표)가 6으로 는다. **verifier r1 L-6** — 이 문단의
 * 「14」는 3E 가 개발 중 거쳐 간 초안 파일 이름 `V5`(→흡수 뒤 최종적으로 `V4` 로 push 됨)
 * 시절의 셈이다 — 지금 저장소에 파일 `V5`는 없다(3E 는 `V4` 하나만 남겼다). 아래 3F 문단의
 * `V5`는 **그 흡수와 무관한, 3F 자신의 신규 마이그레이션 파일**(`V5__opening_complete_axis
 * .sql`)이다 — 이름만 같고 다른 파일을 가리킨다.
 *
 * **M3/3F — 개찰완료 축 부모 슬롯 신규 CHECK(추가만, 스키마 스냅샷 래칫 예외 D-3F-6)** —
 * `V5__opening_complete_axis.sql`이 `opening_result`의 CHECK 를 14 에서 24 로 늘린다
 * (opening_rank_one·draw_numbers 두 축의 enum 둘·값 페어 셋·최소값 하나 + verifier r1
 * F-2 뒤 관측 시각 페어 둘 + F-3 뒤 `OUT_OF_RANGE`↔총예가건수 요구 하나 — **verifier r2
 * N-1 뒤 이 마지막 하나를 같은 개수로 대체**했다: 요구 대상이 부모의
 * `total_reserve_price_candidate_count`(다른 축, `ON CONFLICT` 에서 병합 전 tuple 만 보여
 * 오검출)에서 `draw_numbers` 축 자신의 `draw_numbers_valid_range_max` 로 바뀌었을 뿐 총
 * 개수는 그대로 24다).
 *
 * **M4/4C-2 — outbox·inbox 신규(스키마 스냅샷 래칫 예외 D-4C2-2, 추가만).** `outbox`는
 * 존재 가드 아홉(빈 문자열 여섯 + `aggregate_version >= 0` + `state` enum + `payload` 빈
 * 문자열)에 actor 짝 CHECK 둘(`actor_kind`↔`actor_detail` 짝, `actor_kind` enum)을 더해
 * 11. `inbox`는 `idempotency_key <> ''` 하나뿐이라 1(V6__outbox_inbox.sql).
 */
class CleanMigrationCheckTest : PersistenceTestSupport() {
    private val expectedCheckCountByTable =
        mapOf(
            "collection_run" to 13,
            // M6/6F-4 D-6F4-1·8 — 신규(추가만). `notice_title`이 감시 키워드 매칭 입력이라
            // (기관 열 넷과 달리) 빈 문자열이 조용히 성립하지 않도록 CHECK 하나를 더 얹는다.
            "notice" to 13,
            "notice_audit" to 1,
            "opening_result" to 24,
            "provenance_authority" to 1,
            "qualification_text" to 3,
            // F-7 운영자 결정 — payload 가 TEXT 로 바뀌며 `payload <> ''` CHECK 가 하나 늘었다.
            "raw_observation" to 4,
            "rejected_write" to 1,
            "opening_reserve_price" to 6,
            // M4/4C-2 — 신규(추가만, D-4C2-2).
            "outbox" to 11,
            "inbox" to 1,
            // M6/6B-1 — 신규(추가만, D-6B1-8). V8__edit_session.sql: id<>''·operator_id<>''·
            // state enum·session_version>=0·(state=EXPIRED)=(state_payload IS NULL) 짝 — 5.
            "edit_session" to 5,
            // M6/6F-1 — 신규(추가만, D-6F1-1). operator_strategy: id 싱글턴 1 + score 넷
            // + candidate_limit 1 + revision 1 + 예산 페어 여섯(min 셋 + max 셋) = 13,
            // + Codex 심판 HIGH 수정(won 범위 CHECK 둘, min·max 각 하나) = 15.
            // operator_strategy_revision: revision(PK 인라인) 1 + score 넷 + candidate_limit 1
            // + 예산 페어 여섯 = 12(id 가 없다) + 같은 won 범위 CHECK 둘 = 14.
            "operator_strategy" to 15,
            "operator_strategy_revision" to 14,
            // M6/6F-6 — 신설(추가만, D-6F6-3). V12__operator_profile.sql: id 싱글턴 1 +
            // licenses_declared↔license_names 짝 1 = 2.
            "operator_profile" to 2,
        )

    @Test
    fun `축8 CHECK 개수가 테이블별로 기대와 같다`() {
        val actual = mutableMapOf<String, Int>()
        dataSource().connection.use { connection ->
            connection.createStatement().use { statement ->
                statement
                    .executeQuery(
                        "SELECT conrelid::regclass::text AS table_name, count(*) AS n " +
                            "FROM pg_constraint WHERE contype='c' AND connamespace = 'public'::regnamespace " +
                            "GROUP BY conrelid::regclass::text",
                    ).use { rs ->
                        while (rs.next()) actual[rs.getString("table_name")] = rs.getInt("n")
                    }
            }
        }
        actual shouldBe expectedCheckCountByTable
    }

    @Test
    fun `축8 부가 — COL-06 항등식·H-3 결합식·notice_round 형식 CHECK 가 본문에 살아 있다`() {
        val checkBodies = queryCheckBodies()
        checkBodies.any { it.contains("received = ((normalized + duplicate) + dropped))") } shouldBe true
        checkBodies.any { it.contains("truncated = (truncation_cause IS NOT NULL))") } shouldBe true
        checkBodies.any { it.contains("notice_round ~ '^[0-9]{3}\$'") } shouldBe true
    }

    /**
     * verifier r1 M-1 시정 — `outbox_state_check` CHECK **개수**만 보면(위 테스트) IN 목록에
     * 값을 하나 더해도(예: `'RETRYING'`) 개수는 그대로라 잡히지 않는다. D-M4-5 (a)의
     * at-most-once 종단 다섯 어휘는 넓어지는 것 자체가 재시도 문이므로, 본문을 **정확히**
     * 대조한다(3D COL-06/H-3 관례와 달리 `contains`가 아니라 `shouldBe` — 다섯 값이 늘거나
     * 줄면 본문 문자열 자체가 달라진다).
     */
    @Test
    fun `축8 부가 — outbox_state_check 본문이 다섯 어휘로 정확히 고정된다(D-M4-5 (a))`() {
        val body = queryConstraintDef("outbox_state_check")
        val expected =
            "CHECK ((state = ANY (ARRAY['PENDING'::text, 'CLAIMED'::text, 'DELIVERED'::text, " +
                "'FAILED'::text, 'ISOLATED'::text])))"
        body shouldBe expected
    }

    /**
     * M6/6B-1 verifier r1 MEDIUM-1(c) 시정 — 위 개수 축(테스트 「축8 CHECK 개수가...」)만으로는
     * `state` 어휘 다섯에 여섯째 값을 더해도 `edit_session` 의 CHECK 총수(5)가 그대로라
     * 안 잡힌다. `outbox_state_check`(바로 위 test)와 같은 관례로 본문을 정확히 고정한다.
     */
    @Test
    fun `축8 부가 — edit_session_state_check 본문이 다섯 어휘로 정확히 고정된다(D-6B1-3)`() {
        val body = queryConstraintDef("edit_session_state_check")
        val expected =
            "CHECK ((state = ANY (ARRAY['WAITING_FOR_VALUE'::text, 'WAITING_FOR_CONFIRMATION'::text, " +
                "'APPLIED'::text, 'CANCELLED'::text, 'EXPIRED'::text])))"
        body shouldBe expected
    }

    /**
     * 팀장 지적(`OPEN-CHECK-BODY-PRESENCE-ASSERTIONS`, 6F-6 세션 실측) — `any { contains ... }`
     * 형태(위 COL-06/H-3 test)는 CHECK 를 제자리에서 항진명제로 약화해도 통과한다. V14 의
     * 문자 클래스가 verifier r2·팀장 2차 지적으로 두 번 좁아졌을 때도 개수 축(축8 CHECK
     * 개수)은 계속 초록이었다 — `outbox_state_check`·`edit_session_state_check`(위 두 test)와
     * 같은 관례로 본문을 **정확히** 고정한다. 클래스가 좁아지는 순간 이 test 가 먼저 붉어진다.
     */
    @Test
    fun `축8 부가 — notice_notice_title_check 본문이 V4 bracket 과 정확히 같은 문자 클래스로 고정된다(D-6F4-1)`() {
        val body = queryConstraintDef("notice_notice_title_check")
        val expected =
            "CHECK (((notice_title IS NULL) OR (notice_title ~ '[^\\u0009-\\u000D\\u001C-\\u001F\\u0020\\u00A0" +
                "\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000]'::text)))"
        body shouldBe expected
    }

    private fun queryConstraintDef(constraintName: String): String =
        dataSource().connection.use { connection ->
            connection
                .prepareStatement(
                    "SELECT pg_get_constraintdef(oid) AS def FROM pg_constraint WHERE conname = ?",
                ).use { statement ->
                    statement.setString(1, constraintName)
                    statement.executeQuery().use { rs ->
                        rs.next()
                        rs.getString("def")
                    }
                }
        }

    private fun queryCheckBodies(): List<String> {
        val bodies = mutableListOf<String>()
        dataSource().connection.use { connection ->
            connection.createStatement().use { statement ->
                statement
                    .executeQuery(
                        "SELECT pg_get_constraintdef(oid) AS def FROM pg_constraint " +
                            "WHERE contype='c' AND connamespace = 'public'::regnamespace",
                    ).use { rs ->
                        while (rs.next()) bodies += rs.getString("def")
                    }
            }
        }
        return bodies
    }
}

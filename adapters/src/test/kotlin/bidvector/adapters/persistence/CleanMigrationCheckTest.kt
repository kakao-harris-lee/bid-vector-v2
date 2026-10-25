package bidvector.adapters.persistence

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * S-5 축 8(CHECK) — `CleanMigrationTest`(sizeGate 500줄, v2-지침서.md §5)에서 분리한 파일 —
 * `CleanMigrationTriggerTest`(축 7)와 같은 이유·같은 전례다. D-3D-6 여덟 축 중 CHECK 하나만
 * 다룬다(다른 일곱 축은 `CleanMigrationTest`).
 *
 * **층 B·C 신규 CHECK(추가만, 스키마 스냅샷 래칫 예외)** —
 * `opening_result`가 5(3E 착수 시점 V1)에서 11·14(provenance 페어 3, H-1 뒤)로,
 * `opening_reserve_price`(신규 자식 표)가 6으로 는다. 이 문단의
 * 「14」는 3E 가 개발 중 거쳐 간 초안 파일 이름 `V5`(→흡수 뒤 최종적으로 `V4` 로 push 됨)
 * 시절의 셈이다 — 지금 저장소에 파일 `V5`는 없다(3E 는 `V4` 하나만 남겼다). 아래 3F 문단의
 * `V5`는 **그 흡수와 무관한, 3F 자신의 신규 마이그레이션 파일**(`V5__opening_complete_axis
 * .sql`)이다 — 이름만 같고 다른 파일을 가리킨다.
 *
 * **개찰완료 축 부모 슬롯 신규 CHECK(추가만, 스키마 스냅샷 래칫 예외 D-3F-6)** —
 * `V5__opening_complete_axis.sql`이 `opening_result`의 CHECK 를 14 에서 24 로 늘린다
 * (opening_rank_one·draw_numbers 두 축의 enum 둘·값 페어 셋·최소값 하나 +
 * F-2 뒤 관측 시각 페어 둘 + F-3 뒤 `OUT_OF_RANGE`↔총예가건수 요구 하나 — **N-1
 * 뒤 이 마지막 하나를 같은 개수로 대체**했다: 요구 대상이 부모의
 * `total_reserve_price_candidate_count`(다른 축, `ON CONFLICT` 에서 병합 전 tuple 만 보여
 * 오검출)에서 `draw_numbers` 축 자신의 `draw_numbers_valid_range_max` 로 바뀌었을 뿐 총
 * 개수는 그대로 24다).
 *
 * **outbox·inbox 신규(스키마 스냅샷 래칫 예외 D-4C2-2, 추가만).** `outbox`는
 * 존재 가드 아홉(빈 문자열 여섯 + `aggregate_version >= 0` + `state` enum + `payload` 빈
 * 문자열)에 actor 짝 CHECK 둘(`actor_kind`↔`actor_detail` 짝, `actor_kind` enum)을 더해
 * 11. `inbox`는 `idempotency_key <> ''` 하나뿐이라 1(V6__outbox_inbox.sql).
 */
class CleanMigrationCheckTest : PersistenceTestSupport() {
    private val expectedCheckCountByTable =
        mapOf(
            "collection_run" to 13,
            // D-6F4-1·8 — 신규(추가만). `notice_title`이 감시 키워드 매칭 입력이라
            // (기관 열 넷과 달리) 빈 문자열이 조용히 성립하지 않도록 CHECK 하나를 더 얹는다.
            // D-6F9-3 — 업무구분 새 칸 셋(V17, 추가만): 대분류 어휘 1 + 용역구분·주공종 공백류 2 → 16.
            "notice" to 16,
            "notice_audit" to 1,
            "opening_result" to 24,
            "provenance_authority" to 1,
            "qualification_text" to 3,
            // F-7 운영자 결정 — payload 가 TEXT 로 바뀌며 `payload <> ''` CHECK 가 하나 늘었다.
            // M6/6B-3(추가만) — 투영 열 형태 제약 하나(`jsonb_typeof(payload_fields) = 'object'`,
            // V18 이 `NOT VALID` 로 세우고 V19 가 검증한다, vr F-9) → 5.
            "raw_observation" to 5,
            "rejected_write" to 1,
            "opening_reserve_price" to 6,
            // 신규(추가만, D-4C2-2).
            "outbox" to 11,
            "inbox" to 1,
            // 신규(추가만, D-6B1-8). V8__edit_session.sql: id<>''·operator_id<>''·
            // state enum·session_version>=0·(state=EXPIRED)=(state_payload IS NULL) 짝 — 5.
            "edit_session" to 5,
            // 신규(추가만, D-6F1-1). operator_strategy: id 싱글턴 1 + score 넷
            // + candidate_limit 1 + revision 1 + 예산 페어 여섯(min 셋 + max 셋) = 13,
            // + won 범위 CHECK 둘(min·max 각 하나) = 15.
            // operator_strategy_revision: revision(PK 인라인) 1 + score 넷 + candidate_limit 1
            // + 예산 페어 여섯 = 12(id 가 없다) + 같은 won 범위 CHECK 둘 = 14.
            // 여력 상한 CHECK 하나씩 추가(D-6A3-4·D-6A3-14, V16, 추가만).
            "operator_strategy" to 16,
            "operator_strategy_revision" to 15,
            // 신설(추가만, D-6F6-3). V12__operator_profile.sql: id 싱글턴 1 +
            // licenses_declared↔license_names 짝 1 = 2.
            "operator_profile" to 2,
            // 신규(추가만, D-6F5-4). V13__notice_requirement.sql.
            // notice_requirement: status enum = 1.
            "notice_requirement" to 1,
            // notice_requirement_row: kind enum(컬럼 인라인) + source_field enum(컬럼 인라인)
            // + (kind='PARSED')=(source_field IS NOT NULL) + (kind='PARSED')=(license_names
            // IS NOT NULL) + (kind='PARSED' OR group_no IS NULL) + license_names 비어있지
            // 않음 = 6.
            "notice_requirement_row" to 6,
            // 신규(추가만, D-6A1-7). V15__api_audit.sql: status_code 범위 1 +
            // duration_ms>=0 1 = 2.
            "api_request_audit" to 2,
            // M7/7D(추가만). V20: account 는 login_name<>''·passphrase_hash<>''·failed_attempts>=0,
            // browser_session 은 session_digest<>''·csrf_digest<>''·유휴<=절대 셋이다.
            "account" to 3,
            "browser_session" to 3,
            // M7/7B 신규(추가만). V21__bid_case.sql — 실측값이고 내역은 다음과 같다.
            //  · bid_case 11 = 존재 가드 셋(notice_number·notice_round 형식·skip_reason) +
            //    bid_kind enum + state enum(열넷) + case_version>=0 + 상태별 필수 다섯
            //    (나중에⇒리마인드 · 건너뜀⇒사유 · 사유는 건너뜀에만 · 개찰 대기⇒개찰일시 ·
            //    심사 중⇒서류 기한).
            //  · bid_case_transition 10 = 어휘 enum 셋(from_state·to_state·command — 계약 D-7B-3 에서
            //    공백 가드가 열거로 바뀌었다, 수는 같다) + actor_detail 공백 + reason 공백·상한
            //    + case_version>=0 + actor_kind enum + actor 짝 + 사유 필수 + 소급 출처 제한.
            //  · bid_draft 19 = 금액 범위·통화·과세·출처 enum + 메뉴 짝 + chosen_option_ref 공백
            //    + draft_seq>=0 + 스냅샷 판 번호 하한 + 기초금액 범위 + 짝 넷(하한율 1 · 기초금액 3)
            //    + 계약 D-7B-3 의 여섯(하한율 출처 enum · detail 공백 · detail 짝 · 기초금액 출처 enum
            //    · detail 공백 · detail 짝) + D-7B-4 의 둘(스냅샷 기초금액 통화 · 과세 공백).
            //  · bid_record 11 = 금액 범위·통화·과세·출처 enum + 메뉴 짝 + chosen_option_ref 공백
            //    + record_seq>=0 + evidence_ref 공백·상한 + 증빙 짝 + memo 공백·상한 + 추첨번호 부호(D-7B-3 · D-7B-4 —
            //    상한은 기존 CHECK 에 붙어 수가 그대로다).
            "bid_case" to 11,
            "bid_case_transition" to 10,
            "bid_draft" to 21,
            "bid_record" to 11,
            // M7/7C④(추가만). V22 — 실측값이고 내역은 다음과 같다.
            //  · account_business_profile 5 = 상호(공백만 아님 · 길이) + 소재지 어휘(17) + 등록 여부 어휘(셋)
            //    + 등록분야(어휘 부분집합 · 값마다 한 번뿐) + 「등록이 아니면 분야 · 만료일 없음」 짝.
            //    한 열의 CHECK 가 조건 여럿을 AND 로 들어도 제약은 하나다 — 이 수는 제약 수다.
            //  · account_construction_capacity 3 = 면허명(빈 값 · 길이) + 금액 범위 + 기준 연도 범위.
            "account_business_profile" to 5,
            "account_construction_capacity" to 3,
            // M7/7C③(추가만). V23: kind 어휘 1 + notice_number<>'' 1 + notice_round 형식 1 +
            // detail_code 어휘 1 + 종류↔detail 짝 1 + read_at>=created_at 1 = 6.
            "notification_inbox" to 6,
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
     * `outbox_state_check` CHECK **개수**만 보면(위 테스트) IN 목록에
     * 값을 하나 더해도(예: `'RETRYING'`) 개수는 그대로라 잡히지 않는다. D-M4-5 (a)의
     * at-most-once 종단 다섯 어휘는 넓어지는 것 자체가 재시도 문이므로, 본문을 **정확히**
     * 대조한다(COL-06/H-3 관례와 달리 `contains`가 아니라 `shouldBe` — 다섯 값이 늘거나
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
     * 위 개수 축(테스트 「축8 CHECK 개수가...」)만으로는
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
     * (`OPEN-CHECK-BODY-PRESENCE-ASSERTIONS`) — `any { contains ... }`
     * 형태(위 COL-06/H-3 test)는 CHECK 를 제자리에서 항진명제로 약화해도 통과한다. V14 의
     * 문자 클래스가 두 번 좁아졌을 때도 개수 축(축8 CHECK
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

    /**
     * D-6F9-3 — V17 의 CHECK 셋을 개수가 아니라 **본문**으로 고정한다(위 `notice_title` 선례, `OPEN-CHECK-BODY-
     * PRESENCE-ASSERTIONS`). 대분류는 P-7 문서 열거 어휘 넷을 문서 순서 그대로, 용역구분·주공종은 `notice_title` 과 **같은**
     * 공백류 문자 클래스다 — 이 열들도 감시 매칭 입력이라 빈 문자열이 조용히 성립하지 않게 한다(D-6F4-8·D-6F8-11).
     */
    @Test
    fun `축8 부가 — 업무구분 새 칸 셋의 CHECK 본문이 정확히 고정된다(D-6F9-3)`() {
        queryConstraintDef("notice_business_division_check") shouldBe
            "CHECK (((business_division IS NULL) OR (business_division = ANY (ARRAY['물품'::text, '용역'::text, " +
            "'공사'::text, '외자'::text]))))"
        val nonBlankClass =
            "'[^\\u0009-\\u000D\\u001C-\\u001F\\u0020\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F" +
                "\\u205F\\u3000]'::text)))"
        queryConstraintDef("notice_service_division_check") shouldBe
            "CHECK (((service_division IS NULL) OR (service_division ~ $nonBlankClass"
        queryConstraintDef("notice_main_construction_type_check") shouldBe
            "CHECK (((main_construction_type IS NULL) OR (main_construction_type ~ $nonBlankClass"
    }

    /**
     * D-6F5-16 시정 — 위 개수 축(`축8 CHECK 개수가...`)은 V13 두 표의 CHECK 일곱을
     * **테이블별 개수**로만 받는다. `outbox_state_check`·`edit_session_state_check`와 같은 결함
     * 클래스(있는지만 보는 단언)라, 상태 열거 셋(`status`·`kind`·`source_field`)에 값을 더하거나
     * `CHECK (TRUE)`로 약화해도 개수 축은 그대로 초록이었다(실측). 세 열거를
     * `shouldBe`로 본문까지 정확히 고정한다(D-M4-5 (a)·D-6B1-3 관례).
     */
    @Test
    fun `축8 부가 — notice_requirement·notice_requirement_row 열거 셋이 본문으로 정확히 고정된다(D-6F5-16)`() {
        queryConstraintDef("notice_requirement_status_check") shouldBe
            "CHECK ((status = ANY (ARRAY['FAILED'::text, 'COLLECTED'::text])))"
        queryConstraintDef("notice_requirement_row_kind_check") shouldBe
            "CHECK ((kind = ANY (ARRAY['PARSED'::text, 'UNPARSABLE'::text])))"
        val expectedSourceFieldCheck =
            "CHECK (((source_field IS NULL) OR (source_field = ANY (ARRAY['LcnsLmtNm'::text, " +
                "'PermsnIndstrytyList'::text]))))"
        queryConstraintDef("notice_requirement_row_source_field_check") shouldBe expectedSourceFieldCheck
    }

    /**
     * D-6F5-21 시정 — 위 D-6F5-16의 결합식 넷 존재 단언(`any { contains }`)은
     * **존재 단언**이라, 항등식 하나에 `… OR TRUE`를 제자리로 붙여 제약을 **항진명제**로
     * 약화해도 부분 문자열도 개수도 그대로라 전건 `check`가 초록이었다(실측) —
     * D-6F5-10·D-6F5-14·D-6F5-16에 이은 같은 결함 클래스의 네 번째. 나머지 둘(`group_no`·
     * `license_names`)이 그때 막힌 것은 술어의 힘이 아니라 Postgres가 중첩 `OR`를 평탄화해
     * 문자열 형태 자체가 바뀌는 **우연**이었다(실측).
     *
     * 이 결함 클래스는 「범위를 한 칸 넓히는」 처방으로는 안 닫힌다(D-6F5-20의 교훈과 같은
     * 형태) — `notice_requirement_row`의 CHECK 본문 **집합**을 `shouldBe`로 고정한다.
     * 개수·존재·본문·소속이 한 단언으로 닫힌다: 어떤 제자리 편집(약화·삭제·추가)도 이
     * 집합 자체를 바꾸므로 우회할 제자리가 없다. **정정** — 이 test가 대체하는 앞
     * D-6F5-16 KDoc의 「항등식이라 부분 대조로 충분하다」는 근거는 위 실측으로 반증된다.
     */
    @Test
    fun `축8 부가 — notice_requirement_row CHECK 본문 집합이 정확히 고정된다(D-6F5-21)`() {
        val expectedCheckBodies =
            setOf(
                "CHECK ((kind = ANY (ARRAY['PARSED'::text, 'UNPARSABLE'::text])))",
                "CHECK (((source_field IS NULL) OR (source_field = ANY (ARRAY['LcnsLmtNm'::text, " +
                    "'PermsnIndstrytyList'::text]))))",
                "CHECK (((kind = 'PARSED'::text) = (source_field IS NOT NULL)))",
                "CHECK (((kind = 'PARSED'::text) = (license_names IS NOT NULL)))",
                "CHECK (((kind = 'PARSED'::text) OR (group_no IS NULL)))",
                "CHECK (((license_names IS NULL) OR (cardinality(license_names) > 0)))",
            )
        queryCheckBodiesForTable("notice_requirement_row").toSet() shouldBe expectedCheckBodies
    }

    /**
     * D-6A1-31 — 위 개수 축(`api_request_audit` to 2)만으로는 제자리 `OR TRUE` 항진명제화를
     * 못 잡는다(D-6F5-21과 같은 결함 클래스). `notice_requirement_row` 선례와 같은 관례로
     * CHECK 본문 **집합**을 `shouldBe`로 고정한다.
     */
    @Test
    fun `축8 부가 — api_request_audit CHECK 본문 집합이 정확히 고정된다(D-6A1-31)`() {
        val expectedCheckBodies =
            setOf(
                "CHECK (((status_code >= 100) AND (status_code <= 599)))",
                "CHECK ((duration_ms >= 0))",
            )
        queryCheckBodiesForTable("api_request_audit").toSet() shouldBe expectedCheckBodies
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

    /**
     * **mig M-2 — `NOT VALID` 와 검증된 제약을 개수가 구별하지 못한다.** 위 축8 은 `pg_constraint` 의
     * CHECK **개수**를 세고 `NOT VALID` 제약도 그 개수에 든다. 그래서 V19 의
     * `ALTER TABLE … VALIDATE CONSTRAINT` 한 줄을 지워도 개수는 그대로이고 비객체 적재도 그대로
     * 거부된다(그 거부는 `NOT VALID` 가 이미 진다) — 그 줄이 지는 주장은 **「기존 행에 비객체가
     * 없다」**이고, 깨끗한 test DB 에는 기존 행이 없어 **거동으로는 영원히 실측되지 않는다**.
     * 그래서 장부가 유일한 잠금 자리다.
     */
    @Test
    fun `축8 부가 — 투영 열 형태 제약은 검증까지 끝났다 (NOT VALID 로 남지 않는다)`() {
        convalidatedOf("raw_observation_payload_fields_object") shouldBe true
    }

    private fun convalidatedOf(constraintName: String): Boolean =
        dataSource().connection.use { connection ->
            connection
                .prepareStatement("SELECT convalidated FROM pg_constraint WHERE conname = ?")
                .use { statement ->
                    statement.setString(1, constraintName)
                    statement.executeQuery().use { rs ->
                        check(rs.next()) { "제약이 없다: $constraintName" }
                        rs.getBoolean(1)
                    }
                }
        }

    /** D-6F5-21 — [queryCheckBodies]와 같은 조회를 **표 하나로 좁힌다**(집합 등식 대상). */
    private fun queryCheckBodiesForTable(tableName: String): List<String> {
        val bodies = mutableListOf<String>()
        dataSource().connection.use { connection ->
            connection
                .prepareStatement(
                    "SELECT pg_get_constraintdef(oid) AS def FROM pg_constraint " +
                        "WHERE contype='c' AND conrelid = ?::regclass",
                ).use { statement ->
                    statement.setString(1, tableName)
                    statement.executeQuery().use { rs ->
                        while (rs.next()) bodies += rs.getString("def")
                    }
                }
        }
        return bodies
    }
}

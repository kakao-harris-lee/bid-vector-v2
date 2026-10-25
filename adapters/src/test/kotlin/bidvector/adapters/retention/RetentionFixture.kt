package bidvector.adapters.retention

import bidvector.procurement.FieldConcept
import bidvector.procurement.KONEPS_COLLECTION_POLICY
import bidvector.sharedkernel.Resolution
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import javax.sql.DataSource

/**
 * **test 쪽 앵커다** — `RETENTION_POLICY` 에서 읽지 않는다. 이 값과 출하 정책의 보존 기간이
 * 독립이라야 정책 값을 89·91 로 바꾼 변이가 경계 test 를 RED 로 만든다(P-5 「값이 거동에
 * 닿는가」). 정책에서 읽어 오면 둘이 함께 움직여 어떤 변이도 초록이다.
 */
internal val RETENTION_ANCHOR: Duration = Duration.ofDays(90)
internal val NOW: Instant = Instant.parse("2026-10-14T00:00:00Z")
internal val JUST_OLDER: Instant = NOW - RETENTION_ANCHOR - Duration.ofSeconds(1)
internal val JUST_NEWER: Instant = NOW - RETENTION_ANCHOR + Duration.ofSeconds(1)

/**
 * 계약 해소 기준일 — **수집 정책을 해소하는 test** 가 쓴다(개찰 축 항목을 출하 매퍼로 만들 때).
 * 보존 저장소는 이 값을 받지 않는다: 마스킹 키 집합이 run 날짜에 달리면 묵은 행의 옛 키가 지울 집합
 * 밖이 되기 때문이고(cr M-2), 그래서 그 생성자에서 **인자를 없앴다**.
 */
internal val RETENTION_REFERENCE_DATE: LocalDate = LocalDate.ofInstant(NOW, ZoneOffset.UTC)

/** `raw_observation` 의 열 목록 — fixture 세 자리가 같은 목록을 쓴다(사본 셋을 두지 않는다). */
private const val RAW_OBSERVATION_COLUMNS =
    "(observation_key, source_endpoint, payload, payload_fields, observed_at, " +
        "release_sha, inserted_at)"

/**
 * 보존 test 의 행 심기(P-3 경계·통제군) — **표마다 한 쌍**을 심는다.
 *
 * 왜 production 쓰기 경로가 아니라 직접 INSERT 인가: 재는 축이 **삭제**이고, 그러려면 적재
 * 시각을 ±1초로 **고르는** 것이 전제다. production 경로는 적재 시각을 `DEFAULT now()` 로
 * 맡기고(그것이 그 경로의 계약이다) 그 값을 뒤에서 고칠 수도 없다(`raw_observation` 은
 * append-only 라 UPDATE 가 거부된다). 그래서 fixture 가 시각을 명시한다.
 *
 * 목록은 **rule table** 이다(size-policy: 데이터가 길다고 함수가 긴 것이 아니다). 심은 표
 * 집합이 레지스트리의 분류 집합과 같은지는 test 가 등식으로 잰다 — 새 표가 분류만 되고
 * fixture 가 없으면 그 등식이 RED 다.
 *
 * PII 값은 **형태를 흉내내지 않는다** — 담당자 키가 마스킹 뒤 사라지는지만 재므로 자리값이
 * 전화·전자우편 꼴일 필요가 없다(누출 스캔 어휘를 fixture 가 심지 않는다).
 */

internal class RetentionFixture(
    private val dataSource: DataSource,
) {
    /** [tag] 로 구별되는 한 벌을 [at] 시각으로 심는다 — 심은 표 집합을 돌려준다. */
    fun seedAll(
        tag: String,
        at: Instant,
    ): Set<String> {
        val seeded = mutableSetOf<String>()
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                for (seed in RETENTION_SEEDS) {
                    seed.statements(tag, at).forEach(statement::execute)
                    seeded += seed.table
                }
            }
        }
        return seeded
    }

    /** 판독할 수 없는 원문 한 행 — 마스킹의 fail-closed 경로를 태운다. */
    fun seedUnparsablePayload(
        tag: String,
        at: Instant,
    ) = dataSource.connection.use { connection ->
        connection.createStatement().use { statement ->
            statement.execute(
                "INSERT INTO raw_observation " +
                    "$RAW_OBSERVATION_COLUMNS " +
                    "VALUES ('ret-bad-$tag', 'NOTICE_LIST', 'NOT-JSON-AT-ALL', '{}'::jsonb, " +
                    "'$at', 'ret-test', '$at')",
            )
        }
    }

    /**
     * 기한을 넘은 **부모**와 기한 안쪽 **자식** 한 쌍(설계 검토 우회 6) — `opening_reserve_price`
     * 는 재수집 때 행이 늘어나므로 부모보다 늦게 생길 수 있다. 가드가 없으면 부모 DELETE 가
     * FK 위반으로 run 전체를 중단시킨다.
     */
    fun seedLateChild(
        parentAt: Instant,
        childAt: Instant,
    ) = dataSource.connection.use { connection ->
        connection.createStatement().use { statement ->
            statement.execute(
                "INSERT INTO raw_observation " +
                    "$RAW_OBSERVATION_COLUMNS " +
                    "VALUES ('ret-fk', 'NOTICE_LIST', '{}', '{}'::jsonb, '$parentAt', 'ret-test', '$parentAt')",
            )
            statement.execute(
                "INSERT INTO opening_result " +
                    "(notice_number, notice_round, observed_at, observation_key, created_at, updated_at) " +
                    "VALUES ('RET-fk', '000', '$parentAt', 'ret-fk', '$parentAt', '$parentAt')",
            )
            statement.execute(
                "INSERT INTO opening_reserve_price " +
                    "(notice_number, notice_round, reserve_price_sequence, observed_at, observation_key, " +
                    " created_at, updated_at) " +
                    "VALUES ('RET-fk', '000', '1', '$childAt', 'ret-fk', '$childAt', '$childAt')",
            )
        }
    }

    /**
     * 기한을 넘은 **헤더**와 기한 안쪽 **CASCADE 자식** 한 쌍(운영자 결정 2026-10-14, D-6B3-3 ②).
     * 출하 경로에서 이 상태가 생긴다: 헤더 upsert 는 `created_at` 을 유지하고(`updated_at` 만
     * 갱신) 행은 delete-then-insert 로 새 `created_at` 을 받는다 — 120일 전 생성된 헤더를 오늘
     * 다시 저장하면 행만 기한 안쪽이 된다. `ON DELETE CASCADE` 가 가드 없이 그 행을 함께 지운다.
     */
    fun seedLateCascadeChild(
        parentAt: Instant,
        childAt: Instant,
    ) = dataSource.connection.use { connection ->
        connection.createStatement().use { statement ->
            statement.execute(
                "INSERT INTO notice_requirement (notice_number, notice_round, status, created_at, updated_at) " +
                    "VALUES ('RET-cascade', '000', 'COLLECTED', '$parentAt', '$childAt')",
            )
            statement.execute(
                "INSERT INTO notice_requirement_row " +
                    "(notice_number, notice_round, serial_no, kind, created_at) " +
                    "VALUES ('RET-cascade', '000', '1', 'UNPARSABLE', '$childAt')",
            )
        }
    }

    fun countsByTable(): Map<String, Long> {
        val counts = linkedMapOf<String, Long>()
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                for (table in RETENTION_SEEDS.map { it.table }) {
                    statement.executeQuery("SELECT count(*) FROM $table").use { rs ->
                        rs.next()
                        counts[table] = rs.getLong(1)
                    }
                }
            }
        }
        return counts
    }

    fun payloadOf(observationKey: String): Pair<String, Boolean> =
        dataSource.connection.use { connection ->
            val sql =
                "SELECT payload, pii_masked_at IS NOT NULL AS masked " +
                    "FROM raw_observation WHERE observation_key = ?"
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, observationKey)
                statement.executeQuery().use { rs ->
                    check(rs.next()) { "행이 없다: $observationKey" }
                    rs.getString("payload") to rs.getBoolean("masked")
                }
            }
        }

    /** 투영 열의 문면 — 마스킹이 **두 열**을 건드리는지 재는 쪽이 읽는다(D-6B3-3 ①). */
    fun payloadFieldsOf(observationKey: String): String =
        dataSource.connection.use { connection ->
            val sql = "SELECT payload_fields::text AS value FROM raw_observation WHERE observation_key = ?"
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, observationKey)
                statement.executeQuery().use { rs ->
                    check(rs.next()) { "행이 없다: $observationKey" }
                    rs.getString("value")
                }
            }
        }

    fun outboxStatesPresent(): Set<String> =
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT DISTINCT state FROM outbox").use { rs ->
                    val states = mutableSetOf<String>()
                    while (rs.next()) states += rs.getString(1)
                    states
                }
            }
        }
}

/** 표 하나를 심는 문장들 — `(tag, at)` 을 받아 실행할 SQL 목록을 낸다. */
internal class TableSeed(
    val table: String,
    val statements: (String, Instant) -> List<String>,
)

/** `outbox.state` 의 CHECK 어휘 전수(V6) — 다섯을 다 심어 **둘만** 사라지는지 잰다. */
internal val OUTBOX_STATE_VOCABULARY = listOf("PENDING", "CLAIMED", "DELIVERED", "FAILED", "ISOLATED")

/** 담당자 키 — 필드 계약에 **등재되지 않는다**(D-3H-7). 자리값은 전화·전자우편 꼴을 흉내내지 않는다. */
internal val OFFICER_FIXTURE_KEYS =
    mapOf(
        "ntceInsttOfclNm" to "OFFICER-NAME-FIXTURE",
        "ntceInsttOfclTelNo" to "OFFICER-TEL-FIXTURE",
        "ntceInsttOfclEmailAdrs" to "OFFICER-MAIL-FIXTURE",
        "dminsttOfclNm" to "DEMAND-OFFICER-FIXTURE",
    )

/**
 * 업체 식별 개념의 rawName 전수 — **이름도, 개념도 열거하지 않는다**(D-6B3-5 ①). 선언은
 * [COMPANY_IDENTITY_CONCEPTS] 한 자리이고 fixture 는 그것을 계약에 물어 키를 얻는다. 앞 판은 이
 * 자리에 개념 둘을 따로 적어 두었고, 그래서 셋째 운반자(`opengCorpInfo`)가 선언에서 빠졌을 때
 * **fixture 도 함께 빠져** 어느 test 도 그 누락을 보지 못했다.
 */
internal val COMPANY_NAME_FIXTURE_KEYS: List<String> = rawNamesOfConcepts(COMPANY_IDENTITY_CONCEPTS).sorted()

/** 업체명 자리값 — 마스킹 뒤 이 문자열이 남아 있으면 업체명이 남은 것이다. */
internal const val COMPANY_NAME_FIXTURE_VALUE = "COMPANY-NAME-FIXTURE"

/** 계약 등재 키 둘(공고 식별자) — 마스킹 뒤에도 남아야 하는 축. */
private fun identityFields(tag: String) = listOf(""""bidNtceNo":"RET-$tag"""", """"bidNtceOrd":"000"""")

private fun companyFields() = COMPANY_NAME_FIXTURE_KEYS.map { """"$it":"$COMPANY_NAME_FIXTURE_VALUE"""" }

/** 원문 — 담당자 키·업체명 키·중첩 값까지 담는다(마스킹이 지울 것과 남길 것이 섞여 있어야 한다). */
private fun officerFixturePayload(tag: String): String =
    (
        identityFields(tag) +
            OFFICER_FIXTURE_KEYS.map { (name, value) -> """"$name":"$value"""" } +
            companyFields() +
            listOf(""""nested":{"inner":"value"}""")
    ).joinToString(separator = ",", prefix = "{", postfix = "}")

/** 투영 — 계약 등재 키만이다(담당자 키는 없고 업체명은 **있다**). */
private fun keptFieldsFixture(tag: String): String =
    (identityFields(tag) + companyFields()).joinToString(separator = ",", prefix = "{", postfix = "}")

/**
 * 표 전수 — **순서는 FK 순서**다(부모를 먼저 심는다). 이 목록의 표 집합이
 * `RETENTION_TABLE_RULES` 의 표 집합과 같은지는 `RetentionRegistrySchemaEqualityTest` 가 잰다.
 */
internal val RETENTION_SEEDS: List<TableSeed> =
    listOf(
        TableSeed("raw_observation") { tag, at ->
            listOf(
                "INSERT INTO raw_observation " +
                    "$RAW_OBSERVATION_COLUMNS " +
                    "VALUES ('ret-$tag', 'NOTICE_LIST', '${officerFixturePayload(tag)}', " +
                    "'${keptFieldsFixture(tag)}'::jsonb, '$at', 'ret-test', '$at')",
            )
        },
        TableSeed("notice") { tag, at ->
            listOf(
                "INSERT INTO notice (notice_number, notice_round, status, observation_key, created_at, updated_at) " +
                    "VALUES ('RET-$tag', '000', 'OPEN', 'ret-$tag', '$at', '$at')",
            )
        },
        TableSeed("notice_audit") { tag, at ->
            listOf(
                "INSERT INTO notice_audit " +
                    "(notice_number, notice_round, revision, observation_key, reason, previous_row, recorded_at) " +
                    "VALUES ('RET-$tag', '000', 1, 'ret-$tag', 'RET_FIXTURE', '{}'::jsonb, '$at')",
            )
        },
        TableSeed("rejected_write") { tag, at ->
            listOf(
                "INSERT INTO rejected_write (notice_number, notice_round, observation_key, reason, recorded_at) " +
                    "VALUES ('RET-$tag', '000', 'ret-$tag', 'RET_FIXTURE', '$at')",
            )
        },
        TableSeed("opening_result") { tag, at ->
            listOf(
                "INSERT INTO opening_result " +
                    "(notice_number, notice_round, observed_at, observation_key, created_at, updated_at) " +
                    "VALUES ('RET-$tag', '000', '$at', 'ret-$tag', '$at', '$at')",
            )
        },
        TableSeed("opening_reserve_price") { tag, at ->
            listOf(
                "INSERT INTO opening_reserve_price " +
                    "(notice_number, notice_round, reserve_price_sequence, observed_at, observation_key, " +
                    " created_at, updated_at) " +
                    "VALUES ('RET-$tag', '000', '1', '$at', 'ret-$tag', '$at', '$at')",
            )
        },
        TableSeed("qualification_text") { tag, at ->
            listOf(
                "INSERT INTO qualification_text " +
                    "(notice_number, notice_round, raw_text, observed_at, observation_key, created_at, updated_at) " +
                    "VALUES ('RET-$tag', '000', 'RET_FIXTURE', '$at', 'ret-$tag', '$at', '$at')",
            )
        },
        TableSeed("notice_requirement") { tag, at ->
            listOf(
                "INSERT INTO notice_requirement (notice_number, notice_round, status, created_at, updated_at) " +
                    "VALUES ('RET-$tag', '000', 'COLLECTED', '$at', '$at')",
            )
        },
        TableSeed("notice_requirement_row") { tag, at ->
            listOf(
                "INSERT INTO notice_requirement_row " +
                    "(notice_number, notice_round, serial_no, kind, created_at) " +
                    "VALUES ('RET-$tag', '000', '1', 'UNPARSABLE', '$at')",
            )
        },
        TableSeed("api_request_audit") { tag, at ->
            listOf(
                "INSERT INTO api_request_audit " +
                    "(occurred_at, subject, method, path, status_code, duration_ms, correlation_id) " +
                    "VALUES ('$at', 'operator', 'GET', '/ret/$tag', 200, 1, 'ret-$tag')",
            )
        },
        TableSeed("outbox") { tag, at ->
            OUTBOX_STATE_VOCABULARY.map { state ->
                "INSERT INTO outbox (entry_id, event_id, aggregate_id, aggregate_version, occurred_at, " +
                    "correlation_id, idempotency_key, payload_type, payload, state, inserted_at) " +
                    "VALUES ('ret-$tag-$state', 'ev-$tag-$state', 'agg-$tag', 1, '$at', " +
                    "'corr-$tag-$state', 'idem-$tag-$state', 'RET_FIXTURE', '{}', '$state', '$at')"
            }
        },
        TableSeed("inbox") { tag, at ->
            listOf("INSERT INTO inbox (idempotency_key, processed_at) VALUES ('ret-$tag', '$at')")
        },
        TableSeed("edit_session") { tag, at ->
            listOf(
                "INSERT INTO edit_session " +
                    "(id, operator_id, state, state_payload, expires_at, session_version, created_at) " +
                    "VALUES ('ret-$tag', 'operator', 'WAITING_FOR_VALUE', '{}', '$at', 0, '$at')",
            )
        },
        TableSeed("collection_run") { _, at ->
            listOf(
                "INSERT INTO collection_run (reference_date, source_endpoint, started_at, finished_at, " +
                    "received, normalized, duplicate, dropped, drop_reasons, pages_fetched, truncated, " +
                    "unknown_fields, inserted_at) " +
                    "VALUES (DATE '2026-01-01', 'NOTICE_LIST', '$at', '$at', 0, 0, 0, 0, '{}'::jsonb, 0, " +
                    "false, 0, '$at')",
            )
        },
        TableSeed("operator_strategy") { _, at ->
            listOf(
                "INSERT INTO operator_strategy (id, focus_categories, focus_region_terms, exclude_region_terms, " +
                    "required_keyword_terms, exclude_keyword_terms, revision, updated_at) " +
                    "VALUES (1, '{}', '{}', '{}', '{}', '{}', 0, '$at') ON CONFLICT (id) DO NOTHING",
            )
        },
        TableSeed("operator_strategy_revision") { _, at ->
            listOf(
                "INSERT INTO operator_strategy_revision (revision, focus_categories, focus_region_terms, " +
                    "exclude_region_terms, required_keyword_terms, exclude_keyword_terms, applied_at) " +
                    "VALUES ((SELECT COALESCE(MAX(revision), -1) + 1 FROM operator_strategy_revision), " +
                    "'{}', '{}', '{}', '{}', '{}', '$at')",
            )
        },
        TableSeed("operator_profile") { _, at ->
            listOf(
                "INSERT INTO operator_profile (id, business_types, licenses_declared, license_names, " +
                    "region_terms, updated_at) " +
                    "VALUES (1, '{}', false, '{}', '{}', '$at') ON CONFLICT (id) DO NOTHING",
            )
        },
        // M7/7D — 계정은 제외 표이고 세션은 파기 대상이다. 세션이 계정을 FK 로 참조하므로
        // **계정 먼저**다(목록 순서가 적용 순서다, `notice_requirement` → `…_row` 와 같은 자리).
        TableSeed("account") { tag, at ->
            listOf(
                "INSERT INTO account (login_name, passphrase_hash, created_at, updated_at, failed_attempts) " +
                    "VALUES ('ret-$tag', 'ret-fixture-hash-value', '$at', '$at', 0)",
            )
        },
        TableSeed("browser_session") { tag, at ->
            listOf(
                "INSERT INTO browser_session (session_digest, account_id, csrf_digest, created_at, " +
                    " last_used_at, idle_expires_at, absolute_expires_at) " +
                    "SELECT 'ret-$tag', id, 'ret-csrf-$tag', '$at', '$at', '$at', '$at' " +
                    "FROM account WHERE login_name = 'ret-$tag'",
            )
        },
        // M7/7C③ — 알림함도 계정을 FK 로 참조하므로 `account` 뒤에 온다. 공고 키는 FK 가
        // 아니라(V23 주석 ③) 심지 않은 공고 번호를 써도 된다 — 그 **부재가 설계 사실**이므로
        // 여기서 그 성질을 함께 쓴다(공고 행 없이도 알림 행이 선다).
        TableSeed("notification_inbox") { tag, at ->
            listOf(
                "INSERT INTO notification_inbox (account_id, kind, notice_number, notice_round, " +
                    " occurred_at, created_at, updated_at) " +
                    "SELECT id, 'NOTICE_REVISED', 'RET-$tag', '001', '$at', '$at', '$at' " +
                    "FROM account WHERE login_name = 'ret-$tag'",
            )
        },
        // **심지 않는다.** 이 표는 `PersistenceTestSupport` 의 test 간 TRUNCATE 목록 밖이다
        // (V2 migration 이 seed 하고 그 행이 운영 데이터다) — fixture 가 한 행을 더하면 그 행이
        // **다른 test 로 새어 나간다**(`ProvenanceAuthoritySeedTest` 가 seed 를 열거와 대조하다가
        // 미지 provenance 로 터진다, 실측). 제외 표의 「행 수 불변」 단언은 V2 seed 행이 있어
        // 심지 않아도 공허하지 않다.
        // M7/7B — 입찰 건 넷. 전부 제외 표(`OPERATOR_BID_HISTORY`)이고 「행 수 불변」을 재려면
        // 행이 있어야 한다. 건이 계정을 FK 로 참조하므로 **계정 뒤**이고, 자식 셋은 건 뒤다
        // (목록 순서가 적용 순서다). `notice` FK 가 없으므로 공고를 먼저 심을 필요가 없다.
        TableSeed("bid_case") { tag, at ->
            listOf(
                "INSERT INTO bid_case (account_id, notice_number, notice_round, bid_kind, state, " +
                    "case_version, created_at, updated_at) " +
                    "SELECT id, 'ret-notice-$tag', '000', 'REAL', 'WATCHING', 0, '$at', '$at' " +
                    "FROM account WHERE login_name = 'ret-$tag'",
            )
        },
        TableSeed("bid_case_transition") { tag, at ->
            listOf(
                "INSERT INTO bid_case_transition (case_id, account_id, case_version, from_state, " +
                    "to_state, command, actor_kind, retroactive, occurred_at, recorded_at) " +
                    "SELECT id, account_id, 0, NULL, 'WATCHING', 'ADD_TO_BID_BOX', 'USER', false, " +
                    "'$at', '$at' FROM bid_case WHERE notice_number = 'ret-notice-$tag'",
            )
        },
        TableSeed("bid_draft") { tag, at ->
            listOf(
                "INSERT INTO bid_draft (case_id, account_id, draft_seq, amount_won, amount_currency, " +
                    "amount_vat, amount_provenance, presented_at, snapshot_notice_revision, " +
                    "snapshot_taken_at, created_at) " +
                    "SELECT id, account_id, 0, 1000, 'KRW', 'INCLUSIVE', 'OPERATOR_ENTERED', '$at', " +
                    "1, '$at', '$at' FROM bid_case WHERE notice_number = 'ret-notice-$tag'",
            )
        },
        TableSeed("bid_record") { tag, at ->
            listOf(
                "INSERT INTO bid_record (case_id, account_id, record_seq, amount_won, amount_currency, " +
                    "amount_vat, amount_provenance, submitted_at, confirmed_at, retroactive, created_at) " +
                    "SELECT id, account_id, 0, 1000, 'KRW', 'INCLUSIVE', 'OPERATOR_ENTERED', '$at', " +
                    "'$at', false, '$at' FROM bid_case WHERE notice_number = 'ret-notice-$tag'",
            )
        },
        TableSeed("provenance_authority") { _, _ -> emptyList() },
        // Flyway 이력은 migrate 가 이미 채웠다 — 심지 않고 **행 수 불변**만 잰다(건드리면
        // `validate()` 가 깨지므로 fixture 가 손대지 않는 것 자체가 그 제외의 등재다).
        TableSeed("flyway_schema_history") { _, _ -> emptyList() },
    )

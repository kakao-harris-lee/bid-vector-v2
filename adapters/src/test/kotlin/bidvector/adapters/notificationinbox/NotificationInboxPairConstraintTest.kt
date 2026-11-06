package bidvector.adapters.notificationinbox

import bidvector.adapters.persistence.PersistenceTestSupport
import bidvector.workflow.notificationinbox.AwardResult
import bidvector.workflow.notificationinbox.BidKindAxis
import bidvector.workflow.notificationinbox.NotificationBidKind
import bidvector.workflow.notificationinbox.NotificationDetail
import bidvector.workflow.notificationinbox.NotificationFact
import bidvector.workflow.notificationinbox.NotificationKind
import bidvector.workflow.notificationinbox.axis
import bidvector.workflow.notificationinbox.bidKindAxis
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * **종류 ↔ 부가 축의 짝이 표 층에서 닫힌다**(M7/7C③ 의 detail 축 · M9/paper-bid 의 투찰 종류 축).
 *
 * `NotificationInboxPrivacyTest` 에서 **책임으로 갈라 나왔다**(`sizeGate` 500줄이 계기였지만
 * 경계는 의미가 정한다 — 7C③ 이 `NotificationInboxQueryTest` 를 가른 것과 같은 선례다).
 * 두 파일의 **술어가 다르다**: 그쪽은 카탈로그 정의를 **읽어** 어휘가 같은 집합인지 보고,
 * 이쪽은 실 DB 에 INSERT 를 **쏘아** 짝이 거부되는지 본다. 한 파일에 있으면 「어휘가 갈렸다」와
 * 「짝이 열렸다」가 같은 실패로 보이고, 그 둘은 실제로 **따로 열린다**(V24 변이 ③b 실측:
 * 어휘는 넓히고 짝만 옛 본문으로 두면 어휘 등식은 **초록**이고 교차만 붉다).
 *
 * Kotlin 쪽은 sealed 구성과 전수 표라 불법 짝이 **표현되지 않지만**, 어댑터의 codec 이 어긋나게
 * 쓰면 그것을 잡는 것은 표의 CHECK 다(심층 방어).
 */
internal class NotificationInboxPairConstraintTest : PersistenceTestSupport() {
    /**
     * **종류 ↔ detail 짝이 표 층에서도 닫힌다**(심층 방어 · 우회 ⑩). Kotlin 쪽은 sealed 구성이라
     * 불법 쌍이 표현되지 않지만, 어댑터의 codec 이 어긋나게 쓰면 잡는 것은 이 CHECK 다.
     *
     * detail 을 갖는 종류 둘은 **반드시** 자기 어휘를 싣고, 나머지 여섯은 **반드시** NULL 이다.
     */
    @Test
    fun `detail 을 갖지 않는 종류에 detail 을 실으면 DB 가 거부한다`() {
        val account = insertFixtureAccount()
        val forbidden =
            NotificationKind.entries.filterNot { it in DETAIL_BEARING_KINDS }

        forbidden.forEach { kind ->
            insertFails(account, kind.name, NotificationDetail.AWARDED.name) shouldBe true
        }
    }

    @Test
    fun `detail 을 갖는 종류에 detail 이 없으면 DB 가 거부한다`() {
        val account = insertFixtureAccount()

        DETAIL_BEARING_KINDS.forEach { kind ->
            insertFails(account, kind.name, null) shouldBe true
        }
    }

    /**
     * **짝의 전수 교차**(D-M9-1 로 어휘가 넷에서 다섯이 되며 세운 자리) — detail 을 갖는 종류
     * 둘 × detail 어휘 전수의 **모든 칸**을 재고, 들어가야 하는 칸과 막혀야 하는 칸을 같은
     * 등식에서 가른다.
     *
     * 앞 판은 손으로 고른 짝 다섯(개찰에 낙찰 어휘 둘 · 올바른 짝 셋)이었고, 그 다섯 어디에도
     * 새 어휘가 없다 — 어휘를 하나 더할 때 **그 값이 어느 종류에 붙는지를 아무 단언도 묻지
     * 않는다.** 변이로 그 축을 쟀다: V24 의 detail 어휘는 넓히고 **짝 CHECK 만** 옛 어휘로
     * 두면 아래 「detail 어휘는 Kotlin enum 전수와 같다」가 **초록**이고 이 교차만 붉다.
     *
     * 기대 집합은 **사실의 구성에서 짓는다**: 개찰 축은 `Boolean` 둘, 낙찰 축은 결과 전수다.
     * 손으로 어휘를 다시 적으면 그 목록이 둘째 정본이 되어 구성과 갈린다.
     *
     * 음성 대조가 이 등식 **안**에 있다 — 「막혀야 하는 칸」만 재면 전부 막는 술어도 초록이다.
     */
    @Test
    fun `종류 × detail 전수 교차에서 자기 어휘만 들어간다`() {
        val account = insertFixtureAccount()
        val expected =
            mapOf(
                NotificationKind.OPENING_COMPLETED to
                    listOf(true, false).mapNotNull { NotificationFact.OpeningCompleted(it).detail },
                NotificationKind.AWARD_DECIDED to
                    AwardResult.entries.mapNotNull { NotificationFact.AwardDecided(it).detail },
            )
        expected.keys shouldBe DETAIL_BEARING_KINDS

        expected.forEach { (kind, own) ->
            NotificationDetail.entries.forEach { detail ->
                insertFails(account, kind.name, detail.name) shouldBe (detail !in own)
            }
        }
    }

    /** 음성 대조 — detail 을 갖지 않는 종류에 `null` 은 실제로 들어간다. */
    @Test
    fun `detail 을 갖지 않는 종류에 null 은 들어간다 — 음성 대조`() {
        val account = insertFixtureAccount()

        insertFails(account, NotificationKind.NOTICE_REVISED.name, null) shouldBe false
    }

    /**
     * **종류 ↔ 투찰 종류 짝의 전수 교차**(심층 방어 · 설계 검토 우회 ⑥). 어휘만 닫고 짝을 두지
     * 않으면 마감 알림이 `NONE` 으로, 투찰가 요청이 `PAPER` 로 들어간다 — 전자는 서면·실투찰을
     * 다시 한 항목으로 합치고 후자는 공고 소식을 실투찰로 라벨한다.
     *
     * 기대는 **축 표에서 짓는다**(`bidKindAxis` · `axis`) — 손으로 적으면 둘째 정본이 되어 Kotlin
     * 표와 갈린다. 음성 대조가 이 등식 **안**에 있다: 들어가야 하는 칸도 같은 자리에서 잰다
     * (「막혀야 하는 칸」만 재면 전부 막는 제약도 초록이다).
     */
    @Test
    fun `종류 × 투찰 종류 전수 교차에서 자기 축만 들어간다`() {
        val account = insertFixtureAccount()

        OWN_DETAIL.keys shouldBe DETAIL_BEARING_KINDS

        NotificationKind.entries.forEach { kind ->
            val detail = OWN_DETAIL[kind]
            NotificationBidKind.entries.forEach { bidKind ->
                val allowed = kind.bidKindAxis == bidKind.axis
                insertFails(account, kind.name, detail, bidKind) shouldBe !allowed
            }
        }
    }

    private fun insertFixtureAccount(): Long =
        dataSource().connection.use { connection ->
            connection
                .createStatement()
                .use { statement ->
                    statement
                        .executeQuery(
                            "INSERT INTO account (login_name, passphrase_hash, created_at, updated_at, " +
                                " failed_attempts) VALUES ('privacy-probe', 'h', now(), now(), 0) RETURNING id",
                        ).use { rows ->
                            rows.next()
                            rows.getLong("id")
                        }
                }
        }

    /** 삽입이 거부됐는가 — 거부는 성공으로도 실패로도 읽히지 않게 **boolean 으로** 돌려준다. */
    private fun insertFails(
        account: Long,
        kind: String,
        detail: String?,
        bidKind: NotificationBidKind = axisBidKindOf(kind),
    ): Boolean =
        runCatching {
            dataSource().connection.use { connection ->
                connection
                    .prepareStatement(
                        "INSERT INTO notification_inbox (account_id, kind, notice_number, notice_round, " +
                            " bid_kind, detail_code, occurred_at, updated_at) " +
                            "VALUES (?, ?, ?, '000', ?, ?, now(), now())",
                    ).use { statement ->
                        statement.setLong(1, account)
                        statement.setString(2, kind)
                        // 키가 겹치지 않게 공고 번호를 짝마다 다르게 둔다(PK 위반을 짝 CHECK 위반으로
                        // 착각하지 않도록 — 그 둘을 가르지 않으면 이 test 가 공허해진다). 투찰 종류도
                        // 키의 한 열이라 번호에 함께 싣는다.
                        statement.setString(3, "N-$kind-$detail-$bidKind")
                        statement.setString(4, bidKind.name)
                        statement.setString(5, detail)
                        statement.executeUpdate()
                    }
            }
        }.isFailure

    /**
     * 그 종류의 축에 맞는 투찰 종류 — detail 축 test 들이 **투찰 종류 짝 CHECK 에 걸려** 붉어지는
     * 것을 막는다(두 제약이 한 INSERT 에 함께 서므로, 축을 틀리면 어느 제약이 거부했는지 알 수
     * 없고 detail 축 단언이 공허해진다).
     */
    private fun axisBidKindOf(kind: String): NotificationBidKind =
        when (NotificationKind.valueOf(kind).bidKindAxis) {
            BidKindAxis.PER_BID_CASE -> NotificationBidKind.REAL
            BidKindAxis.PER_NOTICE -> NotificationBidKind.NONE
        }

    private companion object {
        val DETAIL_BEARING_KINDS: Set<NotificationKind> =
            setOf(NotificationKind.OPENING_COMPLETED, NotificationKind.AWARD_DECIDED)

        /**
         * 종류 → 그 종류가 **실제로 싣는** detail 하나. 투찰 종류 교차 test 가 **detail** 짝
         * CHECK 에 걸려 붉어지지 않게 한다 — 두 제약이 한 INSERT 에 함께 서므로 detail 을 틀리면
         * 어느 제약이 거부했는지 알 수 없다.
         *
         * **사실의 구성에서 짓는다**(손으로 어휘를 다시 적지 않는다). 키 집합이
         * [DETAIL_BEARING_KINDS] 와 같다는 것은 투찰 종류 교차 test 가 단언한다 — detail 을 싣는
         * 종류가 늘고 여기 빠지면 이 표가 조용히 `null` 을 내고 그 칸이 **detail 제약 때문에**
         * 붉어진다.
         */
        val OWN_DETAIL: Map<NotificationKind, String> =
            listOf(
                NotificationFact.OpeningCompleted(provisionallyFirst = true),
                NotificationFact.AwardDecided(AwardResult.AWARDED),
            ).mapNotNull { fact -> fact.detail?.let { fact.kind to it.name } }.toMap()
    }
}

package bidvector.app.collection

import javax.sql.DataSource

/**
 * 스냅숏 추출 E2E 의 적재 — 6G 수집 갈래가 남겼을 원문과, 공고 목록 갈래가 세웠을 canonical 을 넣는다.
 * **두 갈래가 다 있어야 스냅숏이 선다**는 것이 추출의 실제 계약이라, 표본도 그 형태를 그대로 만든다.
 *
 * 두 공고를 만든다: 기초금액 공개가 마감 **전**인 것과 **뒤**인 것 — provenance 분리는 값이 갈릴 때만
 * 드러난다.
 *
 * **지우지 않고 넣기만 한다.** `raw_observation`·`notice_audit` 은 append-only 라 DELETE 자체가 거부된다
 * (그 가드가 이 표의 존재 이유다) — 적재는 멱등(`ON CONFLICT DO NOTHING`)이고 여러 번 불러도 같은 상태다.
 */
internal class SnapshotSeed(
    private val dataSource: DataSource,
) {
    /** 스키마를 먼저 세운다 — 적재가 기동보다 앞서므로 마이그레이션을 직접 돌린다(출하 배선과 같은 위치). */
    fun migrate() {
        org.flywaydb.core.Flyway
            .configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate()
    }

    fun load(
        noticeNumber: String,
        lateNoticeNumber: String,
        bidderName: String,
    ) {
        migrate()
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            seedNotice(connection, noticeNumber, disclosedAt = "2026-06-10 09:00:00", bidderName = bidderName)
            seedNotice(connection, lateNoticeNumber, disclosedAt = "2026-06-30 09:00:00", bidderName = bidderName)
            connection.commit()
        }
    }

    private fun seedNotice(
        connection: java.sql.Connection,
        number: String,
        disclosedAt: String,
        bidderName: String,
    ) {
        seedRawAxes(connection, number, disclosedAt, bidderName)
        seedCanonical(connection, number)
    }

    /** 6G 수집 갈래가 남겼을 개찰 축 원문 여섯. */
    private fun seedRawAxes(
        connection: java.sql.Connection,
        number: String,
        disclosedAt: String,
        bidderName: String,
    ) {
        raw(connection, "$number-list", "NOTICE_LIST", number, NOTICE_LIST_FIELDS)
        raw(connection, "$number-open", "OPENING_RESULT_LIST", number, """"progrsDivCdNm":"개찰완료","prtcptCnum":"7"""")
        raw(
            connection,
            "$number-base",
            "BASE_AMOUNT_DETAIL",
            number,
            """"bssamt":"1234567890","bssamtOpenDt":"$disclosedAt",""" +
                """"rsrvtnPrceRngBgnRate":"-3","rsrvtnPrceRngEndRate":"+3","bidPrceCalclAYn":"Y"""",
        )
        raw(
            connection,
            "$number-a",
            "BID_PRICE_FORMULA_A",
            number,
            """"npnInsrprm":"1000","qltyMngcst":"500","qltyMngcstAObjYn":"Y","smkpAmtYn":"N",""" +
                """"bidPrceCalclAOpenDt":"2026-06-11 09:00:00","prearngPrceDcsnMthdNm":"복수예가"""",
        )
        raw(
            connection,
            "$number-reserve",
            "RESERVE_PRICE_DETAIL",
            number,
            """"bssamt":"1239999999","plnprc":"1250000000","compnoRsrvtnPrceSno":"01"""",
        )
        raw(
            connection,
            "$number-bidder",
            "OPENING_COMPLETE",
            number,
            """"opengRank":"1","prcbdrNm":"$bidderName","bidprcAmt":"1100000000"""",
        )
    }

    /** 공고 목록 갈래가 세웠을 canonical — 대분류·하한율·마감이 여기서만 온다. */
    private fun seedCanonical(
        connection: java.sql.Connection,
        number: String,
    ) {
        connection.prepareStatement(NOTICE_SQL).use { statement ->
            statement.setString(1, number)
            statement.setString(2, "$number-list")
            statement.executeUpdate()
        }
        connection.prepareStatement(OPENING_SQL).use { statement ->
            statement.setString(1, number)
            statement.setString(2, "$number-open")
            statement.executeUpdate()
        }
    }

    private fun raw(
        connection: java.sql.Connection,
        key: String,
        endpoint: String,
        number: String,
        extraFields: String,
    ) {
        val payload = """{"bidNtceNo":"$number","bidNtceOrd":"000",$extraFields}"""
        connection.prepareStatement(RAW_SQL).use { statement ->
            statement.setString(1, key)
            statement.setString(2, endpoint)
            statement.setString(3, payload)
            statement.setString(4, payload)
            statement.executeUpdate()
        }
    }
}

private const val NOTICE_LIST_FIELDS =
    """"sucsfbidLwltRate":"87.745","sucsfbidMthdCd":"낙030001","sucsfbidMthdNm":"적격심사제",""" +
        """"sucsfbidMthdAppStd":"조달청 기준","pubPrcrmntClsfcNo":"81112200","dminsttCd":"6110000""""

private const val RAW_SQL =
    """
    INSERT INTO raw_observation
        (observation_key, source_endpoint, payload, payload_fields, observed_at, release_sha)
    VALUES (?, ?, ?, ?::jsonb, TIMESTAMPTZ '2026-06-15 00:00:00+09', 'e2e')
    ON CONFLICT (observation_key) DO NOTHING
    """

private const val NOTICE_SQL =
    """
    INSERT INTO notice
        (notice_number, notice_round, status, business_division, deadline_at,
         floor_rate_fraction, floor_rate_origin_kind, observation_key)
    VALUES (?, '000', 'Open', '용역', TIMESTAMPTZ '2026-06-20 10:00:00+09', 0.87745, 'PUBLISHED', ?)
    ON CONFLICT (notice_number, notice_round) DO NOTHING
    """

private const val OPENING_SQL =
    """
    INSERT INTO opening_result (notice_number, notice_round, observed_at, actual_opening_at, observation_key)
    VALUES (?, '000', TIMESTAMPTZ '2026-06-21 00:00:00+09', TIMESTAMPTZ '2026-06-21 11:00:00+09', ?)
    ON CONFLICT (notice_number, notice_round) DO NOTHING
    """

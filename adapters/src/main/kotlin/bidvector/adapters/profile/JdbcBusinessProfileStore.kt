package bidvector.adapters.profile

import bidvector.adapters.persistence.getTextList
import bidvector.adapters.persistence.setTextArray
import bidvector.qualification.LicenseName
import bidvector.workflow.access.AccountId
import bidvector.workflow.profile.BusinessDetails
import bidvector.workflow.profile.BusinessProfileStorePort
import bidvector.workflow.profile.ConstructionCapacity
import bidvector.workflow.profile.HeadquartersRegion
import bidvector.workflow.profile.NaraReadiness
import bidvector.workflow.profile.NaraRegistrationField
import bidvector.workflow.profile.StorageWriteOutcome
import bidvector.workflow.profile.TradeName
import java.sql.Date
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource

/**
 * V22 표 둘의 JDBC 어댑터(M7/7C④) — [BusinessProfileStorePort] 의 production 구현.
 *
 * **소유 경계는 읽기 · 삭제에서 SQL 이 지고, 쓰기에서는 호출자가 진다**(r1 CR M-1). `SELECT` 둘과
 * `DELETE` 는 첫 조건이 `account_id = ?` 로 행을 좁히고([BusinessProfileSql]) 그 값은 늘 port 의 첫
 * 인자다. 그러나 upsert 둘에서 `account_id` 는 **필터가 아니라 실리는 값이자 `ON CONFLICT` 대상**
 * 이다 — 틀린 [AccountId] 를 넘기면 그 계정의 행을 덮어쓰고, 열 단위 GRANT 는 그것을 막지 않는다
 * (권한 층이 막는 것은 **있는 행을 다른 계정으로 옮기는 일** = `account_id` 의 UPDATE 이고,
 * `BusinessProfileSchemaGuardTest` 가 재는 것도 정확히 그것이다). 그래서 쓰기의 소유 경계는
 * 「호출자가 세션 계정을 넘긴다」는 **전제**이고, 그 전제는 2b 의 controller 가 자기 test 로 진다
 * (scope 알려진 제한 1 — 구조로 닫으려면 RLS 나 세션 범위 역할이고 그것은 별 판단이다).
 * 「읽기 질의가 실제로 계정으로 좁혀지는가」는 fake 로 증명되지 않으므로
 * `JdbcBusinessProfileStoreTest` 가 실 DB 위에서 두 계정으로 잰다.
 *
 * 도메인 값을 지어내지 않는다 — 열 값을 그대로 옮기고, 어휘 밖 값이 저장돼 있으면(CHECK 가 막지만
 * 표가 바뀐 경우) 크게 실패한다. 실패 문면에 **값을 싣지 않는다**(P4 — 열 이름만).
 *
 * 각 메서드는 문장 하나라 커넥션의 자동 커밋으로 원자다(`JdbcOperatorProfileRepository` 와 같은 관례).
 */
class JdbcBusinessProfileStore(
    private val dataSource: DataSource,
) : BusinessProfileStorePort {
    override fun details(account: AccountId): BusinessDetails =
        dataSource.connection.use { connection ->
            connection.prepareStatement(BusinessProfileSql.SELECT_DETAILS).use { statement ->
                statement.setLong(1, account.value)
                statement.executeQuery().use { rs ->
                    if (rs.next()) rs.toBusinessDetails() else BusinessDetails.EMPTY
                }
            }
        }

    override fun capacities(account: AccountId): List<ConstructionCapacity> =
        dataSource.connection.use { connection ->
            connection.prepareStatement(BusinessProfileSql.SELECT_CAPACITIES).use { statement ->
                statement.setLong(1, account.value)
                statement.executeQuery().use { rs ->
                    buildList { while (rs.next()) add(rs.toConstructionCapacity()) }
                }
            }
        }

    override fun replaceDetails(
        account: AccountId,
        details: BusinessDetails,
        at: Instant,
    ): StorageWriteOutcome =
        writingRow {
            dataSource.connection.use { connection ->
                connection.prepareStatement(BusinessProfileSql.UPSERT_DETAILS).use { statement ->
                    statement.setLong(1, account.value)
                    val updatedAtIndex = statement.bindDetails(2, details)
                    statement.setTimestamp(updatedAtIndex, Timestamp.from(at))
                    statement.executeUpdate()
                }
            }
        }

    override fun upsertCapacity(
        account: AccountId,
        capacity: ConstructionCapacity,
        at: Instant,
    ): StorageWriteOutcome =
        writingRow {
            dataSource.connection.use { connection ->
                connection.prepareStatement(BusinessProfileSql.UPSERT_CAPACITY).use { statement ->
                    var index = 1
                    statement.setLong(index++, account.value)
                    statement.setString(index++, capacity.licenseName.value)
                    statement.setLong(index++, capacity.amountWon)
                    statement.setInt(index++, capacity.assessmentYear)
                    statement.setTimestamp(index, Timestamp.from(at))
                    statement.executeUpdate()
                }
            }
        }

    override fun removeCapacity(
        account: AccountId,
        licenseName: LicenseName,
    ): Boolean =
        dataSource.connection.use { connection ->
            connection.prepareStatement(BusinessProfileSql.DELETE_CAPACITY).use { statement ->
                statement.setLong(1, account.value)
                statement.setString(2, licenseName.value)
                statement.executeUpdate() > 0
            }
        }
}

/**
 * 무결성 제약 위반(SQL 표준 SQLSTATE class 23)을 **값으로** 옮긴다 — M7/7C④ 2b 표적 1, D-7C4-8 Q2.
 *
 * 왜 여기인가: 드라이버의 예외 문면은 거부된 **행의 값**을 `detail` 에 싣는다(`Failing row contains
 * (…)`). 그 예외를 그대로 올리면 상호 · 면허명이 스택트레이스로 로그에 남는다. 이 함수 안에서 잡아
 * [StorageWriteOutcome.RejectedByStorage] 로 바꾸면 **문면이 지날 통로 자체가 없다** — 그 타입이 지니는
 * 것은 다섯 글자 SQLSTATE 뿐이다(운영자 입력이 아니다).
 *
 * class 23 **밖은 그대로 올린다**: 연결 끊김 · 권한 거부는 요청 값의 문제가 아니라 장애이고, 값으로
 * 접으면 호출자가 「거부당했다」로 오독한다. 그 경로의 문면에는 행 값이 없다.
 *
 * **이 slice 의 두 표에만 듣는다** — 저장소 전역으로 닫는 길(`logServerErrorDetail`)은
 * `OPEN-7C4-LOG-DETAIL-GLOBAL` 로 중앙에 올린다.
 */
private inline fun writingRow(write: () -> Unit): StorageWriteOutcome =
    try {
        write()
        StorageWriteOutcome.Written
    } catch (rejected: SQLException) {
        val sqlState = rejected.sqlState
        if (sqlState?.startsWith(INTEGRITY_CONSTRAINT_VIOLATION) == true) {
            StorageWriteOutcome.RejectedByStorage(sqlState)
        } else {
            throw rejected
        }
    }

/** SQLSTATE class 23 — 무결성 제약 위반(CHECK · 외래 키 · 유일성). 표준 값이라 드라이버에 묶이지 않는다. */
private const val INTEGRITY_CONSTRAINT_VIOLATION = "23"

/**
 * 사업 정보 다섯 열을 `UPSERT_DETAILS` 순서대로 싣고 **다음 자리를 낸다** — 자리 수를 상수로 손으로
 * 적지 않는다(r1 CR M-2: 열을 하나 더하면 그 상수가 조용히 어긋나 다른 열 위에 덮어쓴다).
 */
private fun PreparedStatement.bindDetails(
    startIndex: Int,
    details: BusinessDetails,
): Int {
    val readiness = details.naraReadiness
    val registered = readiness as? NaraReadiness.Registered
    var index = startIndex
    setString(index++, details.tradeName?.raw)
    setString(index++, details.headquartersRegion?.name)
    setString(index++, readiness.storedName)
    val fields = registered?.fields ?: emptySet()
    setTextArray(index++, fields.map(NaraRegistrationField::name).sorted())
    setDate(index++, registered?.businessCertificateExpiresOn?.let(Date::valueOf))
    return index
}

private fun ResultSet.toBusinessDetails(): BusinessDetails =
    BusinessDetails(
        // 운영자가 입력한 원문 그대로 — 정규화하지 않는다(이중 정규화 금지).
        tradeName = getString("trade_name")?.let(::TradeName),
        headquartersRegion = getString("headquarters_region")?.let { stored(it, HeadquartersRegion.entries) },
        naraReadiness = toNaraReadiness(),
    )

private fun ResultSet.toNaraReadiness(): NaraReadiness =
    when (val stored = getString("nara_registration")) {
        NaraReadiness.UNANSWERED_NAME -> {
            NaraReadiness.Unanswered
        }

        NaraReadiness.NOT_REGISTERED_NAME -> {
            NaraReadiness.NotRegistered
        }

        NaraReadiness.REGISTERED_NAME -> {
            NaraReadiness.Registered(
                fields =
                    getTextList("nara_registered_fields")
                        .map { stored(it, NaraRegistrationField.entries) }
                        .toSet(),
                businessCertificateExpiresOn = getDate("business_certificate_expires_on")?.toLocalDate(),
            )
        }

        else -> {
            error("nara_registration 에 어휘 밖 값이 저장돼 있다(길이 ${stored?.length})")
        }
    }

private fun ResultSet.toConstructionCapacity(): ConstructionCapacity =
    ConstructionCapacity(
        licenseName = LicenseName(getString("license_name")),
        amountWon = getLong("amount_won"),
        assessmentYear = getInt("assessment_year"),
    )

/** 저장된 열거 이름 → 값. 어휘 밖이면 크게 실패한다(값은 싣지 않는다). */
private fun <E : Enum<E>> stored(
    name: String,
    vocabulary: List<E>,
): E = vocabulary.firstOrNull { it.name == name } ?: error("저장된 열거 값이 어휘 밖이다(길이 ${name.length})")

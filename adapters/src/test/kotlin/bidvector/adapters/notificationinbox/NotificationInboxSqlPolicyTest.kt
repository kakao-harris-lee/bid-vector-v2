package bidvector.adapters.notificationinbox

import bidvector.workflow.notificationinbox.NotificationKind
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldNotBeEmpty
import org.junit.jupiter.api.Test
import java.lang.reflect.Modifier

/**
 * M9/inbox-vocab — **읽음 해제 정책이 SQL 로 흘러내리지 않는다**(D-M9-3 · 설계 검토 우회 5).
 *
 * 왜 이 게이트가 따로 필요한가: 정책을 SQL 에 `WHEN kind = 'NOTICE_REVISED' THEN NULL` 로
 * 적어도 **거동 test 는 전부 초록이다**(같은 답을 내므로). 잃는 것은 거동이 아니라 **종류를
 * 더할 때 컴파일이 막아 준다**는 성질이고, 그것은 거동으로는 보이지 않는다. 그래서 「정책이
 * Kotlin 전수 표에 있다」를 그 표의 반대편 — 문장에 종류 이름이 없다 — 으로 잰다.
 *
 * 모집단은 **문장 전수**이고(상수 하나를 집어 두면 둘째 문장에 적는 길이 열린다) 어휘는
 * **종류 전수**다(`NotificationKind.entries` — 종류를 더하면 모집단이 함께 자란다). 상수
 * 열거는 `BiddingSqlOwnershipTest` 가 세운 관례를 그대로 쓴다.
 *
 * 실 DB 를 쓰지 않으므로 컨테이너 하네스를 상속하지 않는다 — 재는 것이 문장의 **문면**이다.
 */
internal class NotificationInboxSqlPolicyTest {
    private val statements: Map<String, String> =
        NotificationInboxSql::class.java.declaredFields
            .filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java }
            .associate { field ->
                field.isAccessible = true
                field.name to (field.get(null) as String)
            }

    @Test
    fun `알림함 문장 전수에 종류 이름이 하나도 없다 — 정책은 Kotlin 전수 표가 진다`() {
        statements.shouldNotBeEmpty()

        leakedKindsIn(statements).shouldBeEmpty()
    }

    /**
     * 양성 대조 — 술어가 실제로 종류 이름을 집는다. 없으면 「늘 통과하는 검사」와 구별되지
     * 않는다(상수 집합이 비거나 이름이 바뀌어도 위 단언은 초록이다).
     */
    @Test
    fun `종류 이름을 담은 문장은 같은 술어에 걸린다 — 양성 대조`() {
        val planted =
            mapOf(
                "PLANTED" to
                    "… WHEN kind = '${NotificationKind.NOTICE_REVISED.name}' THEN NULL …",
            )

        leakedKindsIn(planted) shouldContainExactly listOf("PLANTED" to NotificationKind.NOTICE_REVISED)
    }

    private fun leakedKindsIn(statements: Map<String, String>): List<Pair<String, NotificationKind>> =
        statements.flatMap { (name, sql) ->
            NotificationKind.entries.filter { sql.contains(it.name) }.map { name to it }
        }
}

package bidvector.buildlogic

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 금지 목록 판정. 목록 자체가 경계 정책과 어긋나지 않는지도 여기서 잰다 — 설계 검토 부록 §1
 * 「판정 테스트가 「금지 엔트리는 전부 경계 허용 목록이 통과시키는 좌표여야 한다」를 건다」.
 */
class ApiTypePolicyTest {
    private val policy = ApiTypePolicy.load(File(REAL_POLICY))

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(
        "kotlin.Double,kotlin.Double",
        "java.lang.Float,java.lang.Float",
        "Double,kotlin.Double",
        "Float,kotlin.Float",
        "DoubleArray,kotlin.DoubleArray",
        "Number,kotlin.Number",
    )
    fun `완전수식과 단순 이름 둘 다 금지 엔트리를 낸다`(
        name: String,
        expectedEntry: String,
    ) {
        assertEquals(expectedEntry, policy.forbids(name))
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
        "kotlin.String",
        "java.math.BigDecimal",
        "bidvector.settlement.Won",
        "String",
        "Long",
        // 수식된 이름은 마지막 세그먼트만으로 대조하지 않는다 — 다른 패키지의 동명 타입까지
        // 금지로 번지면 안 된다.
        "com.example.Double",
    )
    fun `허용 좌표는 금지되지 않는다`(name: String) {
        assertNull(policy.forbids(name))
    }

    @Test
    fun `금지 엔트리는 전부 경계 허용 목록이 통과시키는 좌표다`() {
        val boundary = SourceReferencePolicy.load(File(REAL_ARCHITECTURE_POLICY))
        assertAll(
            forbiddenEntries().map { entry ->
                { assertTrue(boundary.admits(entry), "'$entry' 가 경계 허용 목록 밖이다 — 죽은 무게이거나 경계가 바뀌었다") }
            },
        )
    }

    private fun forbiddenEntries(): List<String> = readPolicy(File(REAL_POLICY)).requireList("api.forbidden.types")

    private companion object {
        val REAL_POLICY: String =
            System.getProperty("bidvector.apitype.policy")
                ?: error("시스템 속성 'bidvector.apitype.policy' 가 없다 — 빌드가 넘긴다")
        val REAL_ARCHITECTURE_POLICY: String =
            System.getProperty("bidvector.architecture.policy")
                ?: error("시스템 속성 'bidvector.architecture.policy' 가 없다 — 빌드가 넘긴다")
    }
}

package bidvector.buildlogic

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 소스 층 판정의 **완전 술어**를 실제 정책 파일로 시험한다. 목록 자체의 옳음은
 * `ForbiddenFamilyCoverageTest` 가 승인 문서와 대조하고, 여기서는 술어의 판정 로직만 잰다.
 */
class SourceReferencePolicyTest {
    private val policy = SourceReferencePolicy.load(File(REAL_POLICY))

    @ParameterizedTest(name = "{0}")
    @CsvSource(
        "bidvector.settlement.Money",
        "java.math.BigDecimal",
        "kotlin.collections.List",
        "java.lang.String",
        "java.util.List",
        "java.util.Map\$Entry",
    )
    fun `허용 목록 안의 좌표를 허용한다`(fqn: String) {
        assertTrue(policy.admits(fqn), "$fqn 이 거부됐다")
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
        "java.net.HttpURLConnection",
        "org.springframework.stereotype.Component",
        "java.util.Random",
        "java.lang.Class",
        "java.io.File",
        "kotlin.io.ConsoleKt",
    )
    fun `허용 목록 밖의 좌표를 거부한다`(fqn: String) {
        assertFalse(policy.admits(fqn), "$fqn 이 허용됐다")
    }

    @Test
    fun `T-A 하위·T-B 정확 패키지의 wildcard 는 허용한다`() {
        assertTrue(policy.admitsWildcard("bidvector.settlement"))
        assertTrue(policy.admitsWildcard("java.math"))
    }

    @Test
    fun `T-C 클래스 단위 패키지의 wildcard 는 목록 유무와 무관하게 위반이다`() {
        assertFalse(policy.admitsWildcard("java.util"))
        assertFalse(policy.admitsWildcard("java.lang"))
    }

    @Test
    fun `domain 모듈을 판정한다`() {
        assertTrue(policy.isDomain("settlement"))
        assertTrue(policy.isDomain("shared-kernel"))
        assertFalse(policy.isDomain("workflow"))
        assertFalse(policy.isDomain("adapters"))
        assertFalse(policy.isDomain("app"))
    }

    private companion object {
        val REAL_POLICY: String =
            System.getProperty("bidvector.architecture.policy")
                ?: error("시스템 속성 'bidvector.architecture.policy' 가 없다 — 빌드가 넘긴다")
    }
}

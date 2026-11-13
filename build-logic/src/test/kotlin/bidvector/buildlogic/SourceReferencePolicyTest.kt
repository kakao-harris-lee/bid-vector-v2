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

    // ---- 패키지 범위 축(`OPEN-BYTECODE-GATE-CONST-VAL-BLINDSPOT`) ----

    @Test
    fun `네 adapters 축의 패키지는 범위 축에 든다`() {
        assertTrue(policy.scopeOf("bidvector.adapters.audit") != null)
        assertTrue(policy.scopeOf("bidvector.adapters.evaluation") != null)
        assertTrue(policy.scopeOf("bidvector.adapters.profile") != null)
        assertTrue(policy.scopeOf("bidvector.adapters.qualification") != null)
    }

    /**
     * 표본은 **축이 아닌** 패키지여야 한다 — 축이 되면 이 단언이 거짓이 된다. 앞 판은
     * `bidvector.adapters.persistence` 를 들었고 M11/11C 가 그 패키지를 축으로 만들어
     * (`OPEN-M10-SCOPED-PACKAGES-COVERAGE` 종결, 모집단 여덟 → 열넷) 이 자리가 붉었다.
     * 지금 드는 `bidvector.adapters.snapshot` 은 dependency test 가 없어 축 열넷에 들지 않는
     * 형제 패키지다 — 같은 모듈 이웃이라 `scopeOf` 의 접두 판정을 그대로 시험한다.
     */
    @Test
    fun `범위 축 밖 패키지는 판정 대상이 아니다`() {
        assertTrue(policy.scopeOf("bidvector.adapters.snapshot") == null)
        assertTrue(policy.scopeOf("bidvector.workflow.evaluation") == null)
    }

    @Test
    fun `audit 축은 자기 패키지만 허용한다 — 형제 좌표도 거부다`() {
        val roots = policy.scopeOf("bidvector.adapters.audit") ?: error("축이 없다")

        assertTrue(policy.admitsInScope("bidvector.adapters.audit.ApiAuditSql", roots))
        assertFalse(policy.admitsInScope("bidvector.adapters.persistence.Sql", roots))
        assertFalse(policy.admitsInScope("bidvector.decision.Verdict", roots))
    }

    /**
     * M9/gates-build-followups(리뷰 cr L-2) — 여덟째 병합이 이 축에 알림함 **포트**의 뿌리를 더했고
     * 「넓어진 것은 포트의 뿌리뿐이고 **어댑터**는 들어오지 않는다」를 그때는 일회 양성 대조로만 보였다.
     * 일회 측정은 세션과 함께 죽는다 — 그 음성 단언을 이름 층에도 상시로 남긴다(바이트코드 층은
     * `EvaluationAdapterDependencyTest` 가 같은 뿌리에 같은 단언을 이미 갖는다).
     *
     * 마지막 줄은 **접두 경계**다: `뿌리.` 가 아니라 단순 접두로 견주면 `…notificationinboxadapter` 가
     * 들어온다. 그 경계가 `admitsInScope` 술어의 한 축이라 여기서 함께 잰다.
     */
    @Test
    fun `evaluation 축은 알림함 포트의 뿌리만 허용한다 — 그 어댑터와 접두 위장은 거부다`() {
        val roots = policy.scopeOf("bidvector.adapters.evaluation") ?: error("축이 없다")

        assertTrue(policy.admitsInScope("bidvector.workflow.notificationinbox.NotificationInboxPort", roots))
        assertTrue(policy.admitsInScope("bidvector.workflow.notificationinbox.NotificationAudiencePort", roots))
        assertFalse(policy.admitsInScope("bidvector.adapters.notificationinbox.JdbcNotificationAudience", roots))
        assertFalse(policy.admitsInScope("bidvector.workflow.notificationinboxadapter.Thing", roots))
    }

    @Test
    fun `자기 저장소 좌표만 범위 축의 대상이다 — 외부 좌표는 다른 축이 든다`() {
        assertTrue(policy.isSelfNamespace("bidvector.decision.Verdict"))
        assertFalse(policy.isSelfNamespace("java.math.BigDecimal"))
        assertFalse(policy.isSelfNamespace("bidvectorish.Thing"))
    }

    // ---- 환경 스니핑 축(OPS-13, 임계 0) ----

    @Test
    fun `환경 질의 쌍을 잡는다 — 수신자는 마지막 세그먼트로 견준다`() {
        assertTrue(policy.isEnvSniff(call("System", "getenv")))
        assertTrue(policy.isEnvSniff(call("System", "getProperty")))
    }

    @Test
    fun `별표 수신자는 어떤 수신자든 잡는다 — Spring Environment 처럼 지역 이름인 축`() {
        assertTrue(policy.isEnvSniff(call("environment", "getActiveProfiles")))
        assertTrue(policy.isEnvSniff(call("env", "acceptsProfiles")))
    }

    @Test
    fun `환경 질의가 아닌 쌍은 잡지 않는다`() {
        assertFalse(policy.isEnvSniff(call("System", "currentTimeMillis")))
        assertFalse(policy.isEnvSniff(call("properties", "getProperty2")))
        assertFalse(policy.isEnvSniff(call("clock", "instant")))
    }

    @Test
    fun `명시적 허용은 바이트코드 축과 같은 키에서 읽는다 — 두 층이 한 목록을 본다`() {
        // 수정 라운드 4: 억제를 한 층에만 두면 다른 층이 조용히 붉고, OPS-07 (a) 가 약속한 「명시적
        // 정책 값」이 실제로는 쓸 수 없는 경로가 된다(실측 — 바이트코드 축을 연 뒤 소스 축이 붉었다).
        assertTrue(policy.allowsEnvSniff("bidvector.app.wiring.RelayWiring", "getProperty"))
    }

    @Test
    fun `허용은 그 타입의 그 멤버만 연다`() {
        assertFalse(policy.allowsEnvSniff("bidvector.app.wiring.RelayWiring", "getenv"))
        assertFalse(policy.allowsEnvSniff("bidvector.app.wiring.Other", "getProperty"))
    }

    private fun call(
        receiver: String,
        member: String,
    ) = SourceMemberCall("Probe.kt", 1, receiver, member)

    private companion object {
        val REAL_POLICY: String =
            System.getProperty("bidvector.architecture.policy")
                ?: error("시스템 속성 'bidvector.architecture.policy' 가 없다 — 빌드가 넘긴다")
    }
}

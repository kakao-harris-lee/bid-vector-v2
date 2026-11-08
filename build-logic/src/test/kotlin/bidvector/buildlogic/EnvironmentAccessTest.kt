package bidvector.buildlogic

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * OPS-13 환경 스니핑 축의 **바이트코드 술어**. task 배선과 분리해 인라인 접근으로 잰다
 * (`MigrationOrderTest`·`GateRegistrationCensusTest` 관례).
 *
 * 앞 여섯 test 는 **소스 층이 놓친 형태**를 각각 고정한다(verifier H-1 의 넷 + 리뷰 HIGH 의 둘).
 * 그 형태들이 소스에서는 서로 다른 글자이고 바이트코드에서는 **같은 쌍**이라는 것이 이 축의 근거다 —
 * 그래서 입력은 쌍 하나이고, 「어느 소스 표기에서 왔는가」는 판정에 들어오지 않는다.
 */
class EnvironmentAccessTest {
    @Test
    fun `정적 import 로 수신자를 지운 형태도 같은 쌍으로 잡힌다`() {
        // `import java.lang.System.getenv` 뒤 `getenv("CI")` — 소스에 수신자가 없어 글자 쌍이 만들어지지 않았다.
        val violations = violations(access("bidvector.app.Probe", "java.lang.System", "getenv"))

        assertEquals(1, violations.size, "$violations")
        assertTrue(violations.single().contains("java.lang.System#getenv"), "$violations")
    }

    @Test
    fun `별칭 import 로 수신자를 바꾼 형태도 같은 쌍으로 잡힌다`() {
        // `import java.lang.System as Sys` 뒤 `Sys.getenv("CI")` — 소스 수신자가 `Sys` 라 쌍이 어긋났다.
        assertEquals(1, violations(access("bidvector.adapters.Probe", "java.lang.System", "getenv")).size)
    }

    @Test
    fun `getProperties 를 거쳐 가는 형태가 잡힌다`() {
        // `System.getProperties().getProperty(...)` — 앞 판 쌍 집합의 `System.getProperty` 를 비껴갔다.
        assertEquals(1, violations(access("bidvector.app.Probe", "java.lang.System", "getProperties")).size)
    }

    @Test
    fun `ProcessBuilder 의 환경 맵이 잡힌다`() {
        assertEquals(1, violations(access("bidvector.app.Probe", "java.lang.ProcessBuilder", "environment")).size)
    }

    @Test
    fun `Spring Environment 의 활성 프로파일 질의가 잡힌다`() {
        val owner = "org.springframework.core.env.Environment"

        assertEquals(1, violations(access("bidvector.app.Wiring", owner, "getActiveProfiles")).size)
    }

    @Test
    fun `하위 타입 수신자로 받아도 잡힌다 — 바이트코드 owner 는 수신자의 정적 타입이다`() {
        // 리뷰 M-2 의 원 결함이 이 형태였다: owner 를 `Environment` 로만 열거하면 `ConfigurableEnvironment`
        // 로 받은 같은 호출이 비껴간다. `*#멤버` 는 수신자를 **묻지 않는다** — 그 성질을 입력으로 고정한다
        // (앞 판에는 상위 타입 test 만 있어 변이 표가 말하는 이 자리가 비어 있었다 — 리뷰 LOW-4).
        val owner = "org.springframework.core.env.ConfigurableEnvironment"

        assertEquals(1, violations(access("bidvector.app.Wiring", owner, "getActiveProfiles")).size)
    }

    @Test
    fun `모집단 등식이 어긋나면 붉는다 — 판정 앞에 끼운 필터는 이 수에서 보인다`() {
        // 리뷰 M-1: 익명 클래스 필터를 되돌리는 변이가 **어느 test 도 깨뜨리지 않았다**. 바닥값
        // (「익명 ≥ 1」)은 전제가 거짓이라 쓸 수 없다 — 열 모듈 가운데 여섯이 오늘 0 이다(실측).
        val failure = populationFailure("app", classFiles = 279, importedTypes = 266)

        assertTrue(failure != null && failure.contains("279") && failure.contains("266"), "$failure")
    }

    @Test
    fun `모집단 등식이 맞으면 조용하다`() {
        assertEquals(null, populationFailure("app", classFiles = 279, importedTypes = 279))
    }

    // ---- 명시적 허용(OPS-07 (a)) — 게이트 문면이 약속하던 억제 경로 ----

    @Test
    fun `허용한 정확 쌍은 통과한다`() {
        assertEquals(
            emptyList<String>(),
            EnvironmentAccess.violations(
                access(ALLOWED_CALLER, "java.lang.System", "getProperty"),
                DENIED,
                listOf(ALLOWED_PAIR),
                setOf(ALLOWED_CALLER),
            ),
        )
    }

    @Test
    fun `허용은 그 클래스의 그 멤버만 연다 — 다른 멤버는 그대로 위반이다`() {
        val violations =
            EnvironmentAccess.violations(
                access(ALLOWED_CALLER, "java.lang.System", "getenv"),
                DENIED,
                listOf(ALLOWED_PAIR),
                setOf(ALLOWED_CALLER),
            )

        assertEquals(1, violations.size, "$violations")
    }

    @Test
    fun `허용은 그 클래스만 연다 — 다른 호출자는 같은 좌표라도 위반이다`() {
        val violations =
            EnvironmentAccess.violations(
                access("bidvector.app.Other", "java.lang.System", "getProperty"),
                DENIED,
                listOf(ALLOWED_PAIR),
                setOf(ALLOWED_CALLER, "bidvector.app.Other"),
            )

        assertEquals(1, violations.size, "$violations")
    }

    @Test
    fun `대상 없는 허용은 위반이다 — 죽은 허용은 그 자리를 비워 둔다`() {
        // 그 클래스는 읽혔는데 그 호출이 없다. 「고쳤는데 허용만 남은」 상태이고, 그대로 두면
        // 다음 사람의 같은 호출이 말없이 통과한다.
        val violations =
            EnvironmentAccess.violations(
                access(ALLOWED_CALLER, "java.lang.System", "currentTimeMillis"),
                DENIED,
                listOf(ALLOWED_PAIR),
                setOf(ALLOWED_CALLER),
            )

        assertEquals(1, violations.size, "$violations")
        assertTrue(violations.single().contains("대상이 없다"), "$violations")
    }

    @Test
    fun `남의 모듈 허용은 죽었다고 세지 않는다 — 모듈마다 모집단이 다르다`() {
        val violations =
            EnvironmentAccess.violations(
                access("bidvector.workflow.Run", "java.lang.System", "currentTimeMillis"),
                DENIED,
                listOf(ALLOWED_PAIR),
                setOf("bidvector.workflow.Run"),
            )

        assertEquals(emptyList<String>(), violations)
    }

    @Test
    fun `정책의 허용 목록이 이 저장소의 그 한 쌍이다 — 넓어지면 여기가 붉는다`() {
        // 임계 0 축의 허용 목록은 **조용히 자라면 안 되는** 자리다. 거부 목록과 달리 이 목록은
        // 늘어나는 방향이 느슨해지는 방향이라, 수와 값을 둘 다 고정한다.
        val allowed =
            readPolicy(File(requireProperty("bidvector.architecture.policy")))
                .requireList("bytecode.env-access.allowed-callers")

        assertEquals(listOf(ALLOWED_PAIR), allowed)
    }

    @Test
    fun `거부 집합 밖의 멤버는 통과한다 — 같은 선언 클래스여도`() {
        // 같은 `java.lang.System` 의 `currentTimeMillis` 는 환경 질의가 아니다. 클래스 전체를 거부하면
        // 과잉이고, 그 과잉이 게이트를 끄는 쪽으로 몬다.
        assertEquals(
            emptyList<String>(),
            violations(access("bidvector.app.Probe", "java.lang.System", "currentTimeMillis")),
        )
    }

    @Test
    fun `위반 문면이 호출한 클래스를 지목한다 — 어디를 고칠지가 판정에 들어 있다`() {
        val violations = violations(access("bidvector.app.wiring.Probe", "java.lang.System", "getenv"))

        assertTrue(violations.single().contains("bidvector.app.wiring.Probe"), "$violations")
    }

    @Test
    fun `잴 것이 없으면 위반이다 — 비-공허 단언`() {
        // 게이트가 「클래스를 하나도 읽지 못했다」와 「읽었고 0 건이다」는 다른 말이다. 앞의 것이
        // 초록이면 배선이 끊긴 게이트가 영원히 통과한다(이 저장소가 세 자리에 같은 단언을 둔 이유).
        val violations = EnvironmentAccess.violations(emptyList(), DENIED)

        assertEquals(1, violations.size, "$violations")
        assertTrue(violations.single().contains("읽은 멤버 접근이 없다"), "$violations")
    }

    @Test
    fun `거부 집합에 대상이 없는 항목은 위반이 아니다 — 허용 목록과 방향이 반대다`() {
        // 허용 목록의 죽은 항목은 범위를 넓히므로 위반이다(`MigrationOrder` 의 은퇴 목록). 거부 목록은
        // 임계가 0 이라 대상 0 이 바라는 상태다. 이 비대칭을 test 로 고정해 둔다.
        val onlyUnrelated = access("bidvector.app.Probe", "java.lang.System", "currentTimeMillis")

        assertEquals(emptyList<String>(), violations(onlyUnrelated))
    }

    @Test
    fun `여러 자리에서 같은 쌍을 불러도 자리마다 센다`() {
        val violations =
            EnvironmentAccess.violations(
                listOf(
                    ClassMemberAccess("bidvector.app.A", "java.lang.System", "getenv"),
                    ClassMemberAccess("bidvector.app.B", "java.lang.System", "getenv"),
                ),
                DENIED,
            )

        assertEquals(2, violations.size, "$violations")
    }

    private fun access(
        source: String,
        owner: String,
        member: String,
    ) = listOf(ClassMemberAccess(source, owner, member))

    private fun violations(accesses: List<ClassMemberAccess>): List<String> =
        EnvironmentAccess.violations(accesses, DENIED)

    @Test
    fun `정책의 거부 집합이 비어 있지 않다 — 비-공허 단언`() {
        // 앞 판은 거부 집합을 이 test 의 companion 에 **하드코딩**했다(리뷰 M-3). 그래서 정책의 그 한
        // 줄을 비워도 test 열 전부 초록이고 리포트는 `denied=0 violations=0` 으로 평소와 같은 모양을
        // 냈다 — 축소가 아무 자리에서도 보이지 않았다. 같은 라운드가 ⑤ 에서는 반대로 하고 있었다.
        assertTrue(DENIED.isNotEmpty(), "정책 키가 비었다")
    }

    @Test
    fun `이 test 가 잠그는 좌표가 전부 정책에 있다 — 한 줄을 지우면 여기가 붉는다`() {
        val exercised =
            setOf(
                "java.lang.System#getenv",
                "java.lang.System#getProperty",
                "java.lang.System#getProperties",
                "java.lang.ProcessBuilder#environment",
                "*#getActiveProfiles",
            )

        assertEquals(emptySet<String>(), exercised - DENIED, "정책에서 사라진 좌표")
    }

    private companion object {
        const val ALLOWED_CALLER = "bidvector.app.wiring.RelayWiring"
        const val ALLOWED_PAIR = "$ALLOWED_CALLER->java.lang.System#getProperty"

        /**
         * **실제 정책을 읽는다** — 사본을 쓰면 정책 축소가 어느 test 도 깨뜨리지 않는다(리뷰 M-3).
         * 경로는 `:build-logic:test` 가 선언 입력으로 넘기는 것이고, 그 파일은 이미 입력 목록에 있다.
         *
         * 속성이 없으면 **그 사실을 말하고 죽는다**(리뷰 LOW-5) — 앞 판은 `File(null)` 로 NPE 가 나
         * 「클래스 초기화 실패」라는 모호한 오류가 됐고, 그 오류는 「배선이 끊겼다」를 가리키지 않았다.
         */
        val DENIED: Set<String> =
            readPolicy(File(requireProperty("bidvector.architecture.policy")))
                .requireList("bytecode.env-access.denied-members")
                .toSet()

        fun requireProperty(name: String): String =
            System.getProperty(name)
                ?: error("시스템 속성 '$name' 이 없다 — `:build-logic:test` 의 선언 입력 배선이 끊겼다")
    }
}

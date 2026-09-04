package bidvector.buildlogic

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 커밋된 fixture 로 판정 전체(추출 + 정책)를 잰다 — 설계 검토 부록 §4 「음성 fixture 둘」과
 * 「양성 대조」. task 를 거치지 않고 순수 함수만으로 같은 판정을 재현한다. task 수준 실측은
 * `commands.md` 가 갖는다(알려진 제한 12 와 같은 형태).
 *
 * **이 게이트는 오늘 domain main 에서 실질적으로 비어 있다** — 유일한 선언
 * `ModuleBoundaryAnchor` 가 `internal` 이라 `publicDeclarations` 가 0 이다(설계 검토 부록 §4
 * 「공허 통과 방지」). 그래서 추출기의 liveness 는 여기 fixture 로 고정한다.
 */
class DomainApiTypeFixtureTest {
    private val apiPolicy = ApiTypePolicy.load(File(API_POLICY))

    @Test
    fun `RawDoubleApi 는 위반 셋을 낸다`() {
        val violations = violationsIn(File(VIOLATING, "strategy/RawDoubleApi.kt"))
        assertEquals(3, violations.size, "$violations")
        assertTrue(violations.any { it.declaration.contains("rate") })
        assertTrue(violations.any { it.declaration.contains("ratio") })
        assertTrue(violations.any { it.declaration.contains("weights") })
    }

    /**
     * **별칭 import 는 원 FQN 으로 해석되어 잡힌다**(D-9). `typealias` 와 한 파일에 두면 Kotlin
     * 이 `typealias Amount = Double` 우변의 리터럴 대입을 `java.lang.Double` 대 `kotlin.Double`
     * 플랫폼 타입 불일치로 거부한다(실측) — 그래서 파일을 나눴고, `scalar` 는 값을 구성하지 않는
     * **파라미터** 자리로 별칭 해석 자체만 시험한다.
     */
    @Test
    fun `AliasedDoubleApi 는 별칭 import 를 원 FQN 으로 잡고 타입 미명시도 낸다`() {
        val file = File(VIOLATING, "strategy/AliasedDoubleApi.kt")
        val surface = PublicApiTypes.extract(file.path, file.readText())
        val violations = surface.uses.filter { apiPolicy.forbids(it.name) != null }

        val violation = violations.single()
        assertEquals(1, violations.size, "$violations")
        assertTrue(
            violation.declaration.contains("scalar") && violation.written == "Scalar" &&
                violation.name == "kotlin.Double",
        )
        assertEquals(1, surface.untyped.size, "${surface.untyped}")
        assertTrue(
            surface.untyped
                .single()
                .declaration
                .contains("inferred"),
        )
    }

    /**
     * **typealias 는 사용 자리가 아니라 선언 자리에서 잡힌다**(D-5) — `fun amount(): Amount` 는
     * 별칭을 해석하지 않으므로 그 자체로는 걸리지 않는다. `typealias Amount = Double` 의 우변이
     * 걸린다.
     */
    @Test
    fun `TypeAliasDoubleApi 는 typealias 선언 자리에서 잡힌다`() {
        val violations = violationsIn(File(VIOLATING, "strategy/TypeAliasDoubleApi.kt"))
        assertEquals(1, violations.size, "$violations")
        assertTrue(violations.single().let { it.declaration.contains("typealias Amount") && it.written == "Double" })
    }

    /**
     * **사각의 양성 고정 — 소스 참조 층(13차)도 같은 fixture 를 못 본다.** 수식 없는 `Double`
     * 은 단순 이름이라 `SourceReferences` 가 아예 수집하지 않고(S-2 단언은 소문자 접두가
     * 있어야 후보를 만든다), `AliasedDoubleApi` 의 별칭 import(`kotlin.Double as Scalar`)는
     * 잡히긴 하지만 `kotlin` 이 `package.allowed.exact` 라 허용된다 — 두 층의 사각이 다른
     * 이유로 같은 자리에서 겹친다(설계 검토 부록 §4 evidence 3).
     */
    @Test
    fun `raw Double public API 는 소스 참조 층도 보고하지 않는다`() {
        val boundary = SourceReferencePolicy.load(File(ARCHITECTURE_POLICY))
        val fixtures =
            listOf(
                File(VIOLATING, "strategy/RawDoubleApi.kt"),
                File(VIOLATING, "strategy/AliasedDoubleApi.kt"),
                File(VIOLATING, "strategy/TypeAliasDoubleApi.kt"),
            )
        fixtures.forEach { file ->
            val refs = SourceReferences.extract(file.path, file.readText())
            val violations =
                refs.filterNot {
                    if (it.wildcard) {
                        boundary.admitsWildcard(
                            it.fqn,
                        )
                    } else {
                        boundary.admits(it.fqn)
                    }
                }
            assertTrue(violations.isEmpty(), "$file 이 소스 참조 층에서 잡혔다: $violations")
        }
    }

    @Test
    fun `실제 도메인 형태는 위반이 없고 public 선언이 있다`() {
        val file = File(ALLOWED, "settlement/DomainShapes.kt")
        val surface = PublicApiTypes.extract(file.path, file.readText())
        val violations = surface.uses.filter { apiPolicy.forbids(it.name) != null }
        assertTrue(violations.isEmpty(), "허용 fixture 가 위반으로 잡혔다: $violations")
        assertTrue(surface.publicDeclarations > 0, "양성 corpus 에 public 선언이 없다 — liveness 가 서지 않는다")
        assertTrue(surface.untyped.isEmpty(), "${surface.untyped}")
    }

    private fun violationsIn(file: File): List<ApiTypeUse> {
        val surface = PublicApiTypes.extract(file.path, file.readText())
        return surface.uses.filter { apiPolicy.forbids(it.name) != null }
    }

    private companion object {
        val API_POLICY: String =
            System.getProperty("bidvector.apitype.policy")
                ?: error("시스템 속성 'bidvector.apitype.policy' 가 없다 — 빌드가 넘긴다")
        val ARCHITECTURE_POLICY: String =
            System.getProperty("bidvector.architecture.policy")
                ?: error("시스템 속성 'bidvector.architecture.policy' 가 없다 — 빌드가 넘긴다")
        val VIOLATING: String =
            System.getProperty("bidvector.archfixture.violating")
                ?: error("시스템 속성 'bidvector.archfixture.violating' 가 없다 — 빌드가 넘긴다")
        val ALLOWED: String =
            System.getProperty("bidvector.archfixture.allowed")
                ?: error("시스템 속성 'bidvector.archfixture.allowed' 가 없다 — 빌드가 넘긴다")
    }
}

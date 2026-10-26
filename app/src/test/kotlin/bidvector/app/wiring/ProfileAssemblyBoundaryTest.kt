package bidvector.app.wiring

import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Bean
import java.io.File

/** 출하 조립이 사는 자리 — 이름 좌표를 이 아래 class 파일에서 거둔다. */
private val wiringClasses = File("build/classes/kotlin/main/bidvector/app/wiring")

/** V22(계정 사업 정보)의 타입이 사는 패키지. */
private const val PROFILE_PACKAGE = "bidvector.workflow.profile"

/** V22 를 조립해도 되는 **유일한** 자리. */
private const val PROFILE_ASSEMBLY = "bidvector.app.wiring.ProfileWiring"

/**
 * **2b 표적 4 — 진짜 방어는 조립 자리의 포트 경계다.**
 *
 * 2a 의 격리 게이트들은 「판정 패키지가 V22 를 **이름으로** 보지 않는다」를 재고, 그 술어가 덮지 못하는
 * 길(이행 값 흐름 · 제3 패키지의 인터페이스 · 실행 시 조립한 이름)을 알려진 제한으로 적어 두었다.
 * 그 길들이 실제로 값을 나르려면 **누군가 판정 쪽에 V22 를 건네야** 하고, 건네는 자리는 조립뿐이다.
 * 그래서 여기서는 술어를 하나 더 만들지 않고 **집합 규칙** 하나를 세운다:
 *
 * > `app.wiring` 의 `@Bean` 가운데 [PROFILE_PACKAGE] 의 타입을 **받거나 내는** 것은 [PROFILE_ASSEMBLY]
 * > 의 것뿐이다.
 *
 * 자격 판정 조립(`SuggestionWiring` · `EvaluationWiring`)을 **이름으로 열거하지 않는다** — 새 조립이
 * 생겨도 기본이 거부라서, 판정 쪽에 V22 를 넣으려면 이 게이트를 먼저 고쳐야 한다.
 *
 * 재지 않는 것: `@Bean` 이 **몸통 안에서** 만들어 넘기는 값(생성자 인자로 보이지 않는다). 그 길은
 * 조립 코드를 직접 고치는 일이고, 그때는 이 파일의 KDoc 이 무엇을 약속했는지가 보인다.
 */
class ProfileAssemblyBoundaryTest {
    @Test
    fun `V22 타입을 받거나 내는 조립은 ProfileWiring 하나다`() {
        val beansTouchingProfile =
            assemblyClasses().flatMap { assembly ->
                assembly.declaredMethods
                    .filter { it.isAnnotationPresent(Bean::class.java) }
                    .filter { method ->
                        (method.parameterTypes.toList() + method.returnType)
                            .any { it.name.startsWith("$PROFILE_PACKAGE.") }
                    }.map { assembly.name }
            }

        beansTouchingProfile.shouldNotBeEmpty() // 모집단이 비면 이 게이트는 아무것도 막지 않는다.
        beansTouchingProfile.toSet() shouldBe setOf(PROFILE_ASSEMBLY)
    }

    private fun assemblyClasses(): List<Class<*>> {
        val files = wiringClasses.listFiles { file -> file.name.endsWith(".class") }.orEmpty()
        check(files.isNotEmpty()) { "조립 class 를 찾지 못했다: ${wiringClasses.absolutePath}" }
        return files
            .map { "bidvector.app.wiring." + it.name.removeSuffix(".class") }
            .filterNot { it.contains('$') }
            .map { Class.forName(it, false, this::class.java.classLoader) }
    }
}

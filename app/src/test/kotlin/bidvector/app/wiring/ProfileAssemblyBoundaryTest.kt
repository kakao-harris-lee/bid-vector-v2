package bidvector.app.wiring

import bidvector.adapters.profile.JdbcBusinessProfileStore
import bidvector.workflow.evaluation.OperatorProfilePort
import bidvector.workflow.profile.BusinessProfileStorePort
import bidvector.workflow.profile.ReadProfileUseCase
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Bean
import java.io.File
import javax.sql.DataSource

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
 * > `app.wiring` 의 `@Bean` 가운데 **V22 타입을 받거나 내는** 것은 [PROFILE_ASSEMBLY] 의 것뿐이다.
 *
 * **「V22 타입」은 이름 접두가 아니라 타입 관계로 판정한다**(r2 v F1). 앞 판은 매개변수·반환 타입의
 * **이름이** [PROFILE_PACKAGE] 로 시작하는지만 봤고, 그래서 **어댑터 구현 타입**으로 받으면 지나갔다
 * (실측: 판정 조립에 `JdbcBusinessProfileStore` 를 내는 `@Bean` 을 심어도 `:app:test` 전체가 초록이었다).
 * 지금은 타입 `t` 의 **상위 타입 전이 폐포**(상위 클래스 + 인터페이스, 재귀)에 [PROFILE_PACKAGE] 선언이
 * 하나라도 있으면 V22 로 센다 — 구현 타입으로 감싸 넘기는 길이 같은 술어에 걸린다.
 *
 * 자격 판정 조립(`SuggestionWiring` · `EvaluationWiring`)을 **이름으로 열거하지 않는다** — 새 조립이
 * 생겨도 기본이 거부라서, 판정 쪽에 V22 를 넣으려면 이 게이트를 먼저 고쳐야 한다.
 *
 * **재지 않는 것**(실측 범위):
 * ⓐ `@Bean` 이 **몸통 안에서** 만들어 다른 값 뒤에 숨겨 넘기는 길 — 서명에 타입이 보이지 않는다.
 *   조립 코드를 직접 고치는 일이고, 그때는 이 파일의 약속이 눈앞에 있다.
 * ⓑ 제네릭 **타입 인자**(`List<BusinessProfileReader>` 같은 꼴) — 소거되어 `parameterTypes` 에 남지
 *   않는다. 오늘 `app.wiring` 에 그런 `@Bean` 은 0 이다.
 * ⓒ `app.wiring` 밖의 조립 — 출하 조립에 무엇이 꽂혔는가는 표면 census 가 따로 잰다.
 */
class ProfileAssemblyBoundaryTest {
    @Test
    fun `V22 타입을 받거나 내는 조립은 ProfileWiring 하나다`() {
        val beansTouchingProfile =
            assemblyClasses().flatMap { assembly ->
                assembly.declaredMethods
                    .filter { it.isAnnotationPresent(Bean::class.java) }
                    .filter { method ->
                        (method.parameterTypes.toList() + method.returnType).any(::carriesProfileType)
                    }.map { assembly.name }
            }

        beansTouchingProfile.shouldNotBeEmpty() // 모집단이 비면 이 게이트는 아무것도 막지 않는다.
        beansTouchingProfile.toSet() shouldBe setOf(PROFILE_ASSEMBLY)
    }

    /**
     * 양성·음성 대조 — **구현 타입으로 감싼 길**이 술어에 걸리는지 실제 타입으로 고정한다. 이름 접두
     * 술어는 첫 줄(어댑터 구현)을 놓쳤다.
     */
    @Test
    fun `술어는 이름이 아니라 타입 관계로 V22 를 센다 — 양성·음성 대조`() {
        carriesProfileType(JdbcBusinessProfileStore::class.java) shouldBe true
        carriesProfileType(BusinessProfileStorePort::class.java) shouldBe true
        carriesProfileType(ReadProfileUseCase::class.java) shouldBe true
        carriesProfileType(OperatorProfilePort::class.java) shouldBe false
        carriesProfileType(DataSource::class.java) shouldBe false
        carriesProfileType(String::class.java) shouldBe false
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

/** [type] 자신이나 그 상위 타입 가운데 V22 패키지의 선언이 있는가. */
private fun carriesProfileType(type: Class<*>): Boolean =
    supertypeClosure(type).any { it.name.startsWith("$PROFILE_PACKAGE.") }

private fun supertypeClosure(type: Class<*>): Set<Class<*>> {
    val seen = mutableSetOf<Class<*>>()
    val pending = ArrayDeque(listOf(type))
    while (pending.isNotEmpty()) {
        val next = pending.removeFirst()
        if (!seen.add(next)) continue
        next.superclass?.let(pending::addLast)
        next.interfaces.forEach(pending::addLast)
    }
    return seen
}

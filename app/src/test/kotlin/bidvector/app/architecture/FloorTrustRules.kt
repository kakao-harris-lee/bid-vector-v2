package bidvector.app.architecture

import com.tngtech.archunit.core.domain.JavaAccess
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.domain.JavaFieldAccess.AccessType

/**
 * DEC-10 게이트 등식의 술어 — 호출자를 **코드 단위**(메서드)로 센다.
 *
 * `DivisionValueRules` 는 호출자를 `outermostClassName()` 으로 센다. Kotlin 파일 파사드이므로
 * **같은 파일 안에서 함수를 더하는 것**이 그 입도에서는 보이지 않는다. r1·r2 가 그 사각을 두 번
 * 실측했다 — ⓐ 쪽은 둘째 생성 함수, ⓑ 쪽은 **이미 허용된 가격 경로 파일**이 파생 값을 내보내는
 * 형태(확장 함수 · 람다 값)였고 둘 다 전건 초록이었다. 그래서 원값·통과 값·정책 값의 축 전부를
 * 이 파일이 코드 단위로 센다.
 *
 * 키는 `패키지.클래스.메서드(인자|인자)` 다 — 인자 타입을 `|` 로 잇는다. 쉼표를 쓰지 않는 것은
 * 정책 파일의 목록 구분자가 쉼표이기 때문이고, 인자를 **넣는** 것은 같은 이름 오버로드를 가르기
 * 위해서다(r2 v L-1: 같은 이름 오버로드가 밴드 검사 없이 통과 값을 만들어도 이름 입도에서는
 * 집합이 그대로였다).
 */
internal class FloorTrustRules(
    private val type: String,
) {
    /** 생성 계열(`<init>`·`copy…`)을 만지는 코드 단위 — 「생성 경로는 하나」가 이 집합이다. */
    fun observedCreationCodeUnits(classes: JavaClasses): Set<String> =
        accessesTo(classes).filter { isCreation(it.name) }.map { codeUnitOf(it) }.toSet()

    /** 그 축이 **무엇을** 생성 계열로 셌는지 — 빈 집합을 세고 초록인 상태를 가른다. */
    fun observedCreationMembers(classes: JavaClasses): Set<String> =
        accessesTo(classes).filter { isCreation(it.name) }.map { it.name }.toSet()

    /**
     * 그 타입 **자신의 멤버를 만지는** (코드 단위, 멤버) 쌍. 파생 접근자(원값에서 숫자만 꺼내
     * 내보내는 함수)와 운반 클래스가 이 축에만 남는다 — 획득 축은 반환·필드 **타입**만 본다.
     */
    fun observedMemberAccessUnits(classes: JavaClasses): Set<Pair<String, String>> =
        accessesTo(classes).map { codeUnitOf(it) to it.name }.toSet()

    /**
     * 그 타입 값을 **얻는** (코드 단위, `소유자#멤버`) 쌍 — 반환 타입이 그 타입인 호출과 그 타입
     * 필드 읽기. **자기 클래스의 읽기도 센다**(r3 v H-1): 앞 판은 그것을 제외하면서 「그 자리는
     * 멤버 접근 축이 센다」고 적었는데, **운반 타입의 자기 읽기**에는 그 말이 성립하지 않는다 —
     * 멤버 접근 축은 원값 **자신의** 멤버만 세기 때문이다. 그래서 운반 타입이 자기 필드를
     * 소거 타입으로 내보내는 메서드가 두 축 어디에도 없었다.
     */
    fun observedAcquisitionUnits(classes: JavaClasses): Set<Pair<String, String>> =
        classes.flatMap { candidate -> acquisitionsIn(candidate) }.toSet()

    /**
     * **자기 제외가 없다**(r3 v H-1·H-2 · CR r3 M-1). 앞 판은 `origin.owner == type` 인 접근을
     * 걸렀고, 그래서 **그 타입 자신에 쓴 메서드**가 모든 축에서 보이지 않았다 — 운반 타입의
     * `fun floorAny(): Any? = floorRate`(원값을 소거 타입으로 내보낸다) · 정책 타입의
     * `fun widened() = PlausibilityBand(…)`(위조 밴드를 자기 안에서 만든다)가 전건 초록이었다.
     * 지금은 컴파일러가 만드는 자기 멤버(getter·`componentN`·`copy`·`copy$default`·`toString`·
     * `hashCode`·`equals`·생성자)까지 **서명 키로 허용 목록에 적는다** — 이름(`get*`·`component*`)
     * 으로 면제하지 않는다(같은 약점의 뒤집기다). 자기 메서드가 하나 새로 생기면 N1·N2·N3 와
     * 똑같이 **새 키**로 나타난다.
     */
    private fun accessesTo(classes: JavaClasses): List<JavaAccess<*>> =
        classes
            .flatMap { candidate -> candidate.accessesFromSelf }
            .filter { access -> access.targetOwner.fullName == type }

    private fun acquisitionsIn(candidate: JavaClass): List<Pair<String, String>> {
        val accesses =
            buildList<JavaAccess<*>> {
                addAll(candidate.codeUnitAccessesFromSelf.filter { yieldsType(it.target.rawReturnType) })
                addAll(
                    candidate.fieldAccessesFromSelf.filter {
                        it.accessType == AccessType.GET && yieldsType(it.target.rawType)
                    },
                )
            }
        return accesses.map { access -> codeUnitOf(access) to "${access.targetOwner.fullName}#${access.name}" }
    }

    private fun codeUnitOf(access: JavaAccess<*>): String {
        val origin = access.origin
        val parameters = origin.rawParameterTypes.joinToString("|") { it.name }
        return "${origin.owner.fullName}.${origin.name}($parameters)"
    }

    private fun yieldsType(candidate: JavaClass): Boolean = candidate.baseComponentType.fullName == type

    private fun isCreation(member: String): Boolean = member == "<init>" || member.startsWith("copy")
}

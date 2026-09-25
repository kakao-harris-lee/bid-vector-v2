package bidvector.app.architecture

import com.tngtech.archunit.core.domain.JavaAccess
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.domain.JavaFieldAccess.AccessType
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses

/**
 * M6/6F-9 D-6F9-1 우회 1 — 업무 대분류 **값 획득 축** 게이트. `CollectionArchitectureRules` 의 형태(패키지·타입을
 * 값으로 받아 production 을 지키는 규칙을 fixture 루트에 그대로 적용, 허용 집합은 정책 파일)와 같고 500줄 한도로
 * 파일이 갈렸다.
 *
 * **verifier r1 F-1 뒤 개정.** 이전 판은 열거한 멤버 넷(`fromLabel`·`valueOf`·`values`·`getEntries`)의 **호출**만
 * 보았다 — enum 상수 읽기(`getstatic`), 목록에 없는 새 companion 멤버, `java.lang.Enum.valueOf(Class, String)`
 * 셋이 전부 전체 `check` 를 초록으로 통과했다(실측). 멤버 목록을 늘리는 것은 다음 표기에서 다시 열린다. 그래서
 * **값이 생기는 자리 전부**를 컴파일된 바이트코드에서 도출해 세 축으로 닫는다:
 *
 * ① [typeAccessRules] — 타입 자신의 **모든** 멤버 접근(상수 필드·companion·`valueOf`·`values`·`entries`·어떤 새
 *    멤버든). 허용 목록이 (호출자, 멤버) 쌍이라 멤버 이름 목록이 없다 — 새 멤버가 생기면 그 쌍이 새로 관측된다.
 * ② [acquisitionRules] — **반환 타입이 그 타입인 호출**과 **그 타입 필드 읽기**. 타입 자신을 거치지 않고 값을 얻는
 *    길(운반 슬롯의 getter, 허용 클래스 안에 새로 생긴 파생 함수)이 이 축에 남는다. 자기 소유 멤버 읽기는 획득이
 *    아니므로 제외한다(호출자 == 소유자) — 그 값은 생성 인자로 받은 것이고, 넘긴 자리가 이 축에 이미 있다.
 * ③ [classObjectRules] — 타입의 **클래스 리터럴 참조**(`BusinessDivision::class.java`). `Enum.valueOf(Class, String)`·
 *    `Class.getEnumConstants` 는 호출 소유자가 `java.lang.Enum`·`java.lang.Class` 라 ①②에 걸리지 않는다. 리터럴이
 *    그 길의 입구이므로 입구를 닫는다.
 *
 * 세 축 모두 허용 집합이 관측 집합과 **같아야** 한다(`CollectionArchitectureGateTest` — 낡은 허용 항목이 게이트를
 * 조용히 느슨하게 두지 않는다). 운반 슬롯의 getter 까지 축 ②에 드는 것은 의도다 — 대분류 값이 닿는 자리를 늘리는
 * 변경은 정책 파일 한 줄을 명시적으로 더하게 된다. 나르기만 하는 자리(생성 인자를 자기 필드에 두고 자기 필드만 읽는
 * 클래스)는 축 ② 밖이고, 그 경계는 `CollectionArchitectureGateCatchesViolationsTest` 의 `CleanDivisionCarrier` 가 잠근다.
 *
 * **닫지 못하는 범위**(verifier r2 R2-1 — 주장을 사실로 좁힌다). 축 ②는 디스크립터의 반환·필드 **타입**만 보므로
 * 제네릭이 **소멸된** 자리에서 얻은 값은 세 축 어디에도 남지 않는다: 대분류를 담은 컨테이너의 원소 읽기(`Map.get` 의
 * 반환은 `Object`), `Class.getEnumConstants`(`Object[]`), 역직렬화기의 타입 토큰(뒤따르는 `as` 는 CHECKCAST 이고
 * access 가 아니다). 축 ③은 클래스 **리터럴**만 보므로 인스턴스의 `javaClass`(`Object.getClass`)나 문자열의
 * `Class.forName` 으로 얻은 `Class` 도 밖이다. 6F-8 리플렉션·`Class` 멤버 게이트가 그 길을 덮는 root 는
 * `collection.raw-access.roots`(= `bidvector.workflow`·`bidvector.app`)뿐이라 `procurement`·`adapters` 에는 그 보완이
 * 없다 — 구조로 닫는 방향은 `OPEN-6F9-DIVISION-REFLECTION`(evidence 알려진 제한 7).
 */
internal class DivisionValueRules(
    private val type: String,
) {
    /** 축 ① — 타입 자신의 멤버를 만지는 (호출자 최상위 클래스, 멤버) 쌍은 [allowedPairs] 의 부분집합이다. */
    fun typeAccessRules(allowedPairs: Set<Pair<String, String>>): List<ArchRule> =
        listOf(
            noClasses()
                .should(reportPairs("$type 의 멤버를 만진다", allowedPairs, ::typeAccessesIn))
                .because("D-6F9-1 우회 1 — 대분류 타입의 멤버 접근 쌍은 허용 쌍의 부분집합이다(멤버 목록 없음)"),
        )

    /** 축 ② — 값을 얻는 (호출자, `소유자#멤버`) 쌍은 [allowedPairs] 의 부분집합이다. */
    fun acquisitionRules(allowedPairs: Set<Pair<String, String>>): List<ArchRule> =
        listOf(
            noClasses()
                .should(reportPairs("$type 값을 얻는다", allowedPairs, ::acquisitionsIn))
                .because("D-6F9-1 우회 1 — 대분류 값을 얻는 자리는 허용 쌍의 부분집합이다(반환 타입·필드 읽기)"),
        )

    /** 축 ③ — 타입의 클래스 객체를 참조하는 클래스는 [allowed] 의 부분집합이다. */
    fun classObjectRules(allowed: Set<String>): List<ArchRule> =
        listOf(
            noClasses()
                .should(
                    object : ArchCondition<JavaClass>("$type 의 클래스 객체를 참조한다 (허용 ${allowed.size}종)") {
                        override fun check(
                            item: JavaClass,
                            events: ConditionEvents,
                        ) {
                            val caller = item.topLevel().fullName
                            if (caller == type || caller in allowed || !referencesClassObject(item)) return
                            events.add(SimpleConditionEvent.satisfied(item, "$caller -> $type 클래스 객체"))
                        }
                    },
                ).because("D-6F9-1 우회 1 — 대분류 클래스 객체 참조 집합은 허용 집합의 부분집합이다(Enum.valueOf 입구)"),
        )

    /** 실제로 관측된 축 ① 쌍 — 허용 집합과 **같아야** 한다(낡은 항목 금지). */
    fun observedTypeAccesses(classes: JavaClasses): Set<Pair<String, String>> =
        classes.flatMap { typeAccessesIn(it) }.toSet()

    /** 실제로 관측된 축 ② 쌍 — 허용 집합과 **같아야** 한다. */
    fun observedAcquisitions(classes: JavaClasses): Set<Pair<String, String>> =
        classes.flatMap { acquisitionsIn(it) }.toSet()

    /** 실제로 관측된 축 ③ 클래스 — 허용 집합과 **같아야** 한다. */
    fun observedClassObjectReferences(classes: JavaClasses): Set<String> =
        classes
            .filter { it.topLevel().fullName != type && referencesClassObject(it) }
            .map { it.topLevel().fullName }
            .toSet()

    private fun typeAccessesIn(item: JavaClass): List<Pair<String, String>> {
        val caller = item.topLevel().fullName
        if (caller == type) return emptyList()
        return item.accessesFromSelf
            .filter { it.targetOwner.topLevel().fullName == type }
            .map { caller to it.name }
    }

    private fun acquisitionsIn(item: JavaClass): List<Pair<String, String>> {
        val caller = item.topLevel().fullName
        if (caller == type) return emptyList()
        val accesses =
            buildList<JavaAccess<*>> {
                addAll(item.codeUnitAccessesFromSelf.filter { yieldsType(it.target.rawReturnType) })
                addAll(
                    item.fieldAccessesFromSelf.filter {
                        it.accessType == AccessType.GET && yieldsType(it.target.rawType)
                    },
                )
            }
        return accesses
            .map { it.targetOwner.topLevel().fullName to it.name }
            .filter { (owner, _) -> owner != caller }
            .map { (owner, member) -> caller to "$owner#$member" }
    }

    private fun referencesClassObject(item: JavaClass): Boolean =
        item.referencedClassObjects.any { it.value.baseComponentType.fullName == type }

    private fun yieldsType(candidate: JavaClass): Boolean = candidate.baseComponentType.fullName == type

    private fun reportPairs(
        what: String,
        allowedPairs: Set<Pair<String, String>>,
        observedIn: (JavaClass) -> List<Pair<String, String>>,
    ): ArchCondition<JavaClass> =
        object : ArchCondition<JavaClass>("$what — 허용 밖 쌍 (허용 ${allowedPairs.size}쌍)") {
            override fun check(
                item: JavaClass,
                events: ConditionEvents,
            ) {
                observedIn(item)
                    .filter { it !in allowedPairs }
                    .distinct()
                    .forEach { events.add(SimpleConditionEvent.satisfied(item, "${it.first} -> ${it.second}")) }
            }
        }

    /** 중첩 클래스(`Outer$Inner`)를 가장 바깥 클래스로 접는다 — 허용 집합은 최상위 이름으로 적는다. */
    private fun JavaClass.topLevel(): JavaClass = enclosingClass.map { it.topLevel() }.orElse(this)
}

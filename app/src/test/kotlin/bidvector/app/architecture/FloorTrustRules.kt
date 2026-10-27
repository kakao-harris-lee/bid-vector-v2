package bidvector.app.architecture

import com.tngtech.archunit.core.domain.JavaClasses

/**
 * DEC-10 게이트 등식 가운데 **`DivisionValueRules` 가 낼 수 없는 축**(r1 v H-1 (b)).
 *
 * `DivisionValueRules` 는 호출자를 `outermostClassName()` 으로 센다 — Kotlin 파일 파사드다.
 * 그 입도에서는 **같은 파일 안의 둘째 생성 함수**가 기존 쌍과 구별되지 않는다: verifier 가
 * `PublishedFloorTrust.kt` 에 밴드를 무시하는 `rogueTrust(...)` 를 심고 ⓐ·ⓐ′ 가 **둘 다 초록**인
 * 것을 실측했다. 「생성 경로는 게이트 함수 **하나**」라는 주장은 파일이 아니라 **함수**에 대한
 * 것이므로, 그 축은 code unit(메서드) 입도로 세야 한다.
 *
 * 그래서 이 파일은 축 하나만 낸다 — 게이트 통과 값의 **생성 계열 멤버**(`<init>`·`copy`)를
 * 만지는 **코드 단위**의 집합. 나머지 축(멤버 접근 쌍 · 획득 쌍)은 `DivisionValueRules` 가
 * 그대로 낸다(같은 기계를 두 벌로 두지 않는다).
 */
internal class FloorTrustRules(
    private val gatedType: String,
) {
    /**
     * 생성 계열 멤버를 만지는 코드 단위의 집합 — `패키지.클래스.메서드` 다. 인자 목록은 **넣지
     * 않는다**: 정책 파일의 목록이 쉼표로 갈리므로 서명을 그대로 적을 수 없고, 닫으려는 위협은
     * 「같은 파일의 **다른 함수**」라 이름 입도로 충분하다(같은 이름의 오버로드 둘은 한 항으로
     * 접힌다 — 그것이 이 입도가 닫지 못하는 범위다).
     * 선언 클래스가 그 타입 자신인 접근은 제외한다 — 자기 생성자를 부르는 `copy` 같은 합성
     * 멤버가 집합을 흔들지 않게 한다.
     *
     * 멤버 이름을 열거하지 않는다 — `<init>` 과 `copy` 로 시작하는 **모든** 멤버를 생성 계열로
     * 본다(`copy$default` 같은 합성 오버로드도 그 접두사에 든다). 새 생성 멤버(companion
     * factory 등)가 생기면 그 이름이 무엇이든 [observedMemberNames] 가 먼저 바뀌고, 이 집합의
     * 호출자도 함께 바뀐다.
     */
    fun observedCreationCodeUnits(classes: JavaClasses): Set<String> =
        creationAccesses(classes).map { access -> "${access.origin.owner.fullName}.${access.origin.name}" }.toSet()

    /** 그 축이 실제로 무엇을 생성 계열로 세는지 — 공허한 통과를 가르는 양성 쪽 값이다. */
    fun observedMemberNames(classes: JavaClasses): Set<String> = creationAccesses(classes).map { it.name }.toSet()

    private fun creationAccesses(classes: JavaClasses) =
        classes
            .flatMap { candidate -> candidate.accessesFromSelf }
            .filter { access -> access.targetOwner.fullName == gatedType }
            .filter { access -> access.origin.owner.fullName != gatedType }
            .filter { access -> isCreation(access.name) }

    private fun isCreation(member: String): Boolean = member == "<init>" || member.startsWith("copy")
}

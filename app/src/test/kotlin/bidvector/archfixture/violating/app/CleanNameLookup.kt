package bidvector.archfixture.violating.app

/**
 * D-6F8-13 F2-2 음성 대조 — 이 클래스는 **잡히면 안 된다**. `Class` 의 이름 조회는 반사가 아니다(허용 멤버).
 * 리플렉션 규칙이 이름 조회까지 막으면 러너의 원인 코드(예외 클래스 이름)가 서지 못한다.
 */
class CleanNameLookup {
    fun typeName(value: Any): String = value.javaClass.name
}

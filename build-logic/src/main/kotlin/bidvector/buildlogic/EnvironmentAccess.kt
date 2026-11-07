package bidvector.buildlogic

/**
 * 바이트코드 한 자리에서 본 **멤버 접근** — 호출하는 클래스, 호출 대상의 선언 클래스, 멤버 이름.
 *
 * 소스 표기가 아니라 산출물의 참조다. 그래서 정적 import(`import java.lang.System.getenv`)·
 * 별칭(`import java.lang.System as Sys`)·수식 호출이 **같은 하나**로 모인다 — 그 셋을 가르는 것은
 * 소스의 글자뿐이고 컴파일된 참조는 하나다.
 *
 * **다만 「참조가 하나」가 「한 클래스에 있다」는 뜻은 아니다**(수정 라운드 3 정정 — verifier H-1 ·
 * 리뷰 H-1). callable reference(`System::getenv`)·suspend 람다·익명 object 는 그 참조를 **이름 없는
 * 내부 클래스**로 옮긴다. 앞 판은 모집단에서 익명 클래스를 빼고 있었고(타입 모양 래칫의 관례를 접근
 * 전수 조사에 그대로 옮긴 자리) 그래서 그 셋이 통과했다 — 실측 59 클래스 · 718 접근이 조용히 빠졌다.
 * 지금 모집단은 **읽은 클래스 전수**이고, 귀속만 감싼 쪽으로 올린다([ClassMemberAccess.enclosingClass]).
 */
internal data class ClassMemberAccess(
    val sourceClass: String,
    val ownerFqn: String,
    val member: String,
    /**
     * [sourceClass] 를 품은 **최상위 클래스**. Kotlin 의 callable reference·suspend 람다·익명 object 는
     * 바이트코드에서 이름 없는 내부 클래스(`Outer$f$1`)가 되는데, 고칠 자리는 그 내부 클래스가 아니라
     * 감싼 쪽이다. 둘이 다르면 판정 문면이 **둘 다** 든다.
     */
    val enclosingClass: String = sourceClass,
)

/**
 * **실행 환경 스니핑 0**(OPS-13) 을 바이트코드 층에서 재는 순수 술어.
 *
 * 왜 소스 층으로 모자랐나 — 앞 판은 소스의 `수신자.멤버` **글자 쌍**이었다. 수신자를 별칭으로 바꾸거나
 * 정적 import 로 수신자를 아예 지우면 쌍이 만들어지지 않아 통과했고(실측 — verifier H-1 · 리뷰 HIGH),
 * 「좌표 축이 그것을 받는다」는 문면은 비-domain 모듈에서 성립하지 않았다(그 축은 domain 모듈에서만,
 * 그리고 자기 저장소 좌표만 본다). 글자를 세는 술어는 글자를 바꾸는 것으로 열린다.
 *
 * 거부 집합은 **`선언클래스#멤버`** 다. 디스크립터가 아니라 이름인 이유는 [deriveMemberEffects] 와 같다 —
 * 오버로드가 서명으로 갈려도 효과는 같은 이름 아래 있다.
 *
 * **거부 집합에 대상이 없는 항목은 위반이 아니다.** 허용 목록의 죽은 항목은 범위를 조용히 넓히지만
 * (`MigrationOrder` 의 은퇴 목록·`source.import.syntax-names` 가 그 꼴이다), 거부 목록의 임계는 0 이라
 * 대상이 0 인 것이 **바라는 상태**다. 두 목록의 방향이 반대라 같은 검출을 걸지 않는다.
 *
 * **순수 함수다** — 파일 시스템도 Gradle 도 ASM 도 모른다.
 */
internal object EnvironmentAccess {
    fun violations(
        accesses: List<ClassMemberAccess>,
        denied: Set<String>,
    ): List<String> =
        if (accesses.isEmpty()) {
            listOf(
                "읽은 멤버 접근이 없다 — 이 축이 아무것도 보지 못했다. " +
                    "「0 건이다」가 아니라 「재지 못했다」이고, 그 초록은 배선이 끊긴 게이트의 초록이다",
            )
        } else {
            accesses
                .filter { isDenied(it, denied) }
                .sortedBy { "${it.enclosingClass} ${it.sourceClass} ${key(it)}" }
                .map {
                    val where =
                        if (it.sourceClass == it.enclosingClass) {
                            it.enclosingClass
                        } else {
                            "${it.enclosingClass} (안쪽 ${it.sourceClass})"
                        }
                    "$where → ${key(it)} — production 코드가 실행 환경을 스니핑한다 " +
                        "(OPS-13 임계 0; 억제는 명시적 정책 값으로, OPS-07 (a))"
                }
        }

    /**
     * 거부 키는 `선언클래스#멤버` 이고 **`*#멤버` 는 「어떤 수신자든」**이다.
     *
     * 별표가 필요한 이유: 바이트코드의 Methodref owner 는 수신자의 **정적 타입**이다. 그래서
     * `Environment#getActiveProfiles` 만 들면 `ConfigurableEnvironment`·`StandardEnvironment` 로
     * 받은 같은 호출이 비껴간다(실측 — 리뷰 M-2). 하위 타입을 열거하는 것은 또 다른 열거라
     * 수신자를 **묻지 않는** 쪽으로 닫는다 — 소스 층의 `*.getActiveProfiles` 와 같은 모양이다.
     * `java.lang.System`·`ProcessBuilder` 는 `final` 이라 그쪽은 정확 좌표로 둔다(과잉 방지).
     */
    private fun isDenied(
        access: ClassMemberAccess,
        denied: Set<String>,
    ): Boolean = key(access) in denied || "*#${access.member}" in denied

    private fun key(access: ClassMemberAccess): String = "${access.ownerFqn}#${access.member}"
}

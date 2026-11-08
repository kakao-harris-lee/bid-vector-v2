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
 * **모집단 등식** — 읽은 타입 수가 읽은 `.class` 파일 수와 **같아야** 한다.
 *
 * 이 축의 반복된 사고는 「판정이 틀렸다」가 아니라 **「모집단이 조용히 작아졌다」**였다: 앞 판은
 * `filterNot { it.isAnonymousClass }` 로 59 클래스 · 718 접근을 빼고 있었고, 그 필터를 지운 것은
 * **어느 test 도 잠그지 않는 task 층 한 줄**이었다(리뷰 M-1 — 되돌리면 전부 조용히 초록이다).
 *
 * 왜 「익명 클래스 수 ≥ 1」이 아닌가(리뷰 M-1 의 처방): 그 바닥값의 전제가 **실측으로 거짓**이다 —
 * 열 모듈 가운데 **여섯이 오늘 0**(`settlement`·`decision`·`procurement`·`qualification`·`bidding`·
 * `strategy`)이라 바닥값은 그 여섯을 상시로 붉힌다. 등식은 전제가 필요 없다: 어떤 필터를 어디에
 * 끼워 넣어도 두 수가 갈라진다. 실측으로 열 모듈 전부에서 `types == class 파일 수` 다.
 *
 * **순수 함수다** — 세는 것은 task 가 하고 판정은 여기서 한다.
 */
internal fun populationFailure(
    module: String,
    classFiles: Int,
    importedTypes: Int,
): String? =
    if (classFiles == importedTypes) {
        null
    } else {
        "모듈 '$module' 의 모집단이 읽은 class 파일 수와 다르다 — class 파일 $classFiles · 읽은 타입 " +
            "$importedTypes. 이 축은 **전수**라야 하고, 차이는 판정 앞에 필터가 끼었다는 뜻이다"
    }

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
        allowed: List<String> = emptyList(),
        presentClasses: Set<String> = emptySet(),
    ): List<String> =
        if (accesses.isEmpty()) {
            listOf(
                "읽은 멤버 접근이 없다 — 이 축이 아무것도 보지 못했다. " +
                    "「0 건이다」가 아니라 「재지 못했다」이고, 그 초록은 배선이 끊긴 게이트의 초록이다",
            )
        } else {
            sniffViolations(accesses, denied, allowed.toSet()) +
                allowanceViolations(accesses, allowed, presentClasses)
        }

    private fun sniffViolations(
        accesses: List<ClassMemberAccess>,
        denied: Set<String>,
        allowed: Set<String>,
    ): List<String> =
        accesses
            .filter { isDenied(it, denied) && allowKey(it) !in allowed }
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

    /**
     * **허용 하나는 호출 하나만 연다** — 대상이 없어도, 둘 이상이어도 위반이다.
     *
     * 대상이 없는 쪽(OPS-13 원리 4): 죽은 허용은 그 좌표의 자리를 비워 두어, 나중에 누가 같은 자리에
     * 같은 호출을 넣으면 말없이 통과시킨다.
     *
     * **둘 이상인 쪽**(수정 라운드 5, 리뷰 M-1 · verifier M-1): 허용 키의 귀속은 **최상위 클래스**라,
     * 한 번 열면 그 클래스 안의 다른 메서드·중첩 클래스·람다에서 **몇 번이든** 같은 호출이 통과했다
     * (실측 — 네 변이 전부 초록). 메서드 단위 키는 람다·callable reference 가 다른 메서드로 나가
     * 모양이 부서지므로, 범위를 **행사 횟수 1**로 묶는다: 허용된 자리는 하나로 남고, 두 번째 호출은
     * 그 자리를 넓히는 것이므로 정책을 다시 쓰게 한다. (닫히지 않는 것 하나: 그 **한 호출이 읽는
     * 값**은 키에 없다 — 인자가 상수가 아니라 enum 값이라 속성 이름 단위 허용이 이 저장소에서는
     * 성립하지 않는다. `checklist.md` 제한 21 에 범위를 그대로 적는다.)
     *
     * 검출 범위는 **그 클래스를 읽은 모듈**로 제한한다 — 게이트는 모듈마다 돌고 모집단이 다르므로,
     * 범위를 두지 않으면 아홉 모듈이 남의 모듈 허용을 「죽었다」고 센다. 클래스가 통째로 사라진
     * 경우는 어느 모듈도 세지 않는다(알려진 제한).
     *
     * **모양이 아닌 항목은 모든 모듈에서 위반이다**(리뷰 LOW-5). `->` 나 `#` 가 없는 줄은 어떤 접근과도
     * 같아질 수 없어 앞 판에서 **아무 말 없이 무효**였다 — 오타 하나가 허용을 지우고도 조용했다.
     * 그 줄은 모집단과 무관한 **정책 데이터의 오류**라 모듈 범위를 묻지 않는다.
     */
    private fun allowanceViolations(
        accesses: List<ClassMemberAccess>,
        allowed: List<String>,
        presentClasses: Set<String>,
    ): List<String> {
        val exercised = accesses.map(::allowKey)
        val (shaped, malformed) = allowed.partition(::isAllowKeyShaped)
        return malformed.sorted().map {
            "허용 항목이 '귀속클래스${CALLER_SEPARATOR}선언클래스${MEMBER_SEPARATOR}멤버' 모양이 아니다 — " +
                "어떤 접근과도 같아질 수 없어 **말없이 무효**인 허용이다: $it"
        } + shaped.sorted().mapNotNull { allowance -> countViolation(allowance, exercised, presentClasses) }
    }

    private fun countViolation(
        allowance: String,
        exercised: List<String>,
        presentClasses: Set<String>,
    ): String? =
        when (val count = exercised.count { it == allowance }) {
            1 -> {
                null
            }

            0 -> {
                if (allowance.substringBefore(CALLER_SEPARATOR) !in presentClasses) {
                    null
                } else {
                    "허용한 좌표에 대상이 없다 — 그 클래스는 읽혔는데 그 호출이 없다. 죽은 허용은 " +
                        "그 자리를 비워 두어 다음 사람의 같은 호출을 말없이 통과시킨다: $allowance"
                }
            }

            else -> {
                "허용 하나가 호출 ${count}건을 연다 — 이 키의 귀속은 **최상위 클래스**라 그 안의 다른 " +
                    "메서드·중첩 클래스·람다까지 함께 열린다. 허용은 자리 하나까지다: $allowance"
            }
        }

    private fun isAllowKeyShaped(allowance: String): Boolean =
        allowance.contains(CALLER_SEPARATOR) && allowance.substringAfter(CALLER_SEPARATOR).contains(MEMBER_SEPARATOR)

    /**
     * 허용 키는 **`귀속클래스->선언클래스#멤버`** 다 — 고칠 자리(귀속)와 능력(좌표)의 쌍이고,
     * 와일드카드가 없다. 이 저장소의 `collection.transport.holders.*` 와 같은 모양이다.
     */
    private fun allowKey(access: ClassMemberAccess): String = "${access.enclosingClass}$CALLER_SEPARATOR${key(access)}"

    private const val CALLER_SEPARATOR = "->"
    private const val MEMBER_SEPARATOR = "#"

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

    private fun key(access: ClassMemberAccess): String = "${access.ownerFqn}$MEMBER_SEPARATOR${access.member}"
}

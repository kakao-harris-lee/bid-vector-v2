package bidvector.buildlogic

/**
 * **각 모듈이 자기 패키지만 소유한다**는 판정. 순수 함수라 테스트가 붙는다.
 *
 * 이 게이트가 없으면 경계 규칙 전체가 **클래스의 자기 신고**에 기댄다 — `decision` 프로젝트에
 * `package leak`(선언 없음)이나 `package bidvector.app.sneaky` 를 두면 앞의 것은 어느 규칙의
 * 대상도 아니고 뒤의 것은 app 층으로 오인된다. 위반 fixture 가 전부 자기 세그먼트를 스스로
 * 선언하고 있었으므로 그 약점은 fixture 로도 드러나지 않았다.
 *
 * 판정은 **class output 의 실제 위치**로 한다 — 선언이 아니라 산출물이 근거다.
 */
internal class PackageOwnershipPolicy(
    private val packageRoot: String,
) {
    /** 모듈 이름 → 패키지 세그먼트. 규칙은 하이픈 제거 하나이고 정본은 정책 파일의 `package.root` 주석이다. */
    fun ownedPackage(module: String): String = "$packageRoot.${module.replace("-", "")}"

    fun violations(
        module: String,
        classPackages: Collection<String>,
    ): List<String> {
        val owned = ownedPackage(module)
        return classPackages
            .distinct()
            .sorted()
            .filterNot { it == owned || it.startsWith("$owned.") }
            .map { observed ->
                val shown = observed.ifEmpty { "(기본 패키지 — 선언 없음)" }
                "$shown — '$module' 이 소유하는 것은 '$owned' 아래뿐이다"
            }
    }
}

package bidvector.archfixture.violating.qualification

/**
 * Codex 14차 #1 원 재현 — **다른 함수**의 동명 지역 변수(`val java = 1`)가 이 함수의 완전수식
 * 참조를 지우지 못해야 한다. 이 상수는 바이트코드에 타입 참조를 남기지 않고(인라인) JDK 타입
 * 이라 1차 게이트도 못 본다 — 소스 층이 유일한 방어선이다.
 */
internal object ShadowedRootLeak {
    private fun other(): Int {
        val java = 1
        return java
    }

    fun status(): String =
        java.net.HttpURLConnection.HTTP_OK
            .toString()
}

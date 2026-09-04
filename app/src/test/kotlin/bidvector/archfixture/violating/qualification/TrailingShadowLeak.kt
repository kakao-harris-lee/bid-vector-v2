package bidvector.archfixture.violating.qualification

/**
 * verifier r20 H-1 원 재현(P10) — 같은 함수의 완전수식 참조 **뒤쪽**에 동명 지역 변수를 두어도
 * 그 참조를 가리지 못해야 한다. Kotlin 의 지역 변수 스코프는 선언 지점부터 시작하므로 `java` 는
 * 이 참조 자리에서 아직 선언되지 않아 패키지로 해석된다 — 컴파일도 실제로 된다.
 */
internal object TrailingShadowLeak {
    fun v(): Int {
        val x = java.net.HttpURLConnection.HTTP_OK
        val java = 1
        return x + java
    }
}

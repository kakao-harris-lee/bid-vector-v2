package bidvector.archfixture.violating.qualification

/**
 * verifier r21 H-1' 원 재현 — 참조를 **가리는 선언 자신의 초기화식 안**에 두어도 그 참조를
 * 지우지 못해야 한다. Kotlin 은 초기화식 안에서 그 지역 변수를 아직 보지 않으므로(자기 자신을
 * 참조할 수 없다) `java` 는 이 자리에서 패키지로 해석된다 — 컴파일도 실제로 된다.
 */
internal object SelfInitShadowLeak {
    fun status(): Int {
        val java = java.net.HttpURLConnection.HTTP_OK
        return java
    }
}

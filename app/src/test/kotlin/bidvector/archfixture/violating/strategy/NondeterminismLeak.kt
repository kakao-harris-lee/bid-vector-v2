package bidvector.archfixture.violating.strategy

/**
 * 허용된 클래스 안의 **비결정성**. `Math` 와 `Collections` 는 도메인에 필수인데 그 안의
 * `random`·`shuffle` 만 결과를 실행마다 바꾼다 — 클래스 단위로 가를 수 없는 자리다.
 *
 * 도출이 이 둘을 「효과 표면(`java.util.Random`)에 닿는다」로 스스로 냈다. 손으로 생각해 낸
 * 목록이 아니라는 것이 이 fixture 가 고정하는 성질이다.
 */
class NondeterminismLeak {
    fun jitter(): Double = Math.random()

    fun scrambled(rows: MutableList<String>): List<String> {
        java.util.Collections.shuffle(rows)
        return rows
    }
}

package bidvector.decision.priority

import java.math.BigDecimal

/**
 * `[0,1]` clamp — [composePriority]·[SemanticMatch.of] 둘의 결과 경계라 한 자리로 모은다
 * (중복 금지, 위협 모델 (9) 「clamp 경계」). 조용한 하한 올림이 아니라 명시적 상하한
 * 자름이다 — 입력이 이미 [0,1] 밖일 수 있는 자리(가중합 − penalty, 코사인 + offset)에서만
 * 쓴다.
 */
internal fun clamp01(value: BigDecimal): BigDecimal =
    when {
        value < BigDecimal.ZERO -> BigDecimal.ZERO
        value > BigDecimal.ONE -> BigDecimal.ONE
        else -> value
    }

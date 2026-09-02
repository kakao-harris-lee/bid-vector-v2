package bidvector.archfixture.violating.strategy

import bidvector.archfixture.violating.decision.CycleRight

/** 업무 모듈 사이의 직접 참조이자 순환의 한쪽. */
class CycleLeft {
    fun other(): CycleRight = CycleRight()
}

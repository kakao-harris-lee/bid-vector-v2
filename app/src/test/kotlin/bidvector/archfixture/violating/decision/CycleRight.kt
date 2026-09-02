package bidvector.archfixture.violating.decision

import bidvector.archfixture.violating.strategy.CycleLeft

/** 순환의 반대쪽. 이 참조가 사라지면 순환 단언이 실패한다. */
class CycleRight {
    fun other(): CycleLeft = CycleLeft()
}

package bidvector.workflow.evaluation

import bidvector.decision.UnitScore
import bidvector.decision.priority.derive.DerivationOutcome
import bidvector.qualification.OperatorLicenses
import bidvector.strategy.CategoryCode

/**
 * 사업자 프로필 fact(scope.md ⑤, D-4B6A-1) — [synthesizeProfileText]의 합성 fact allow-list
 * 자체다. 사업자번호·대표자·연락처는 이 타입에 필드가 없어 구조적으로 못 들어온다(위협
 * 모델 우회 (2)). 저장·편집·조회는 이 slice 밖(`OPEN-4B6-PROFILE-SOURCE`, M6).
 */
data class ProfileFacts(
    val businessTypes: Set<CategoryCode>,
    val licenses: OperatorLicenses,
    val regionTerms: List<String>,
)

/** 사업자 프로필 조회 port(scope.md ⑤, ADR 0005 D-9) — `null`은 「프로필 미설정」. 실 구현은 M6. */
fun interface OperatorProfilePort {
    fun current(): ProfileFacts?
}

/**
 * workload 집계 port(scope.md ⑤, ADR 0005 D-9) — 4B-5 [DerivationOutcome]을 재사용한다
 * (`bidvector.decision.priority.derive.DerivationAbsence.WorkloadNotCollected`가 항상-미가용
 * fake의 사유). 실 집계 구현은 이 slice 밖(후속).
 */
fun interface WorkloadPort {
    fun current(): DerivationOutcome<UnitScore>
}

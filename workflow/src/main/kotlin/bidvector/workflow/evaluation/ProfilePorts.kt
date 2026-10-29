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
 * 사업자 프로필 **저장** port(M7/7C④ 2b) — 조회([OperatorProfilePort])와 가른다. 읽기 자리에 쓰기
 * 능력을 주지 않기 위해서다(`StrategyReader` 와 같은 이유, D-6A2b-41).
 *
 * **계정을 받지 않는다**: V12 는 아직 싱글턴이고(`CHECK (id = 1)`), 받는 척하고 무시하면 「이미 계정
 * 단위다」라는 거짓 신호가 된다. 계정 단위 전환은 R-2 남은 절반(별 slice)이고, 그때 이 서명이 바뀌면
 * 컴파일 오류가 고칠 자리를 전부 가리킨다.
 *
 * 이 port 가 `workflow.evaluation` 에 있는 이유: V12 는 **자격 판정의 입력**이고 그 조회 port 와 값
 * 타입([ProfileFacts])이 여기 산다. V22(계정 사업 정보)의 port 는 `workflow.profile` 에 따로 있고, 두
 * 세계를 한 class 가 함께 지니지 못하게 하는 게이트가 `adapters` 에 걸려 있다(2a 격리 규칙).
 */
fun interface OperatorProfileStorePort {
    fun save(facts: ProfileFacts)
}

/**
 * workload 집계 port(scope.md ⑤, ADR 0005 D-9) — 4B-5 [DerivationOutcome]을 재사용한다
 * (`bidvector.decision.priority.derive.DerivationAbsence.WorkloadNotCollected`가 항상-미가용
 * fake의 사유). 실 집계 구현은 이 slice 밖(후속).
 */
fun interface WorkloadPort {
    fun current(): DerivationOutcome<UnitScore>
}

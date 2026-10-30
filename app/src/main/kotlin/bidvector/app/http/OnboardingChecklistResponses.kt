package bidvector.app.http

import bidvector.workflow.profile.AwaitingItem
import bidvector.workflow.profile.OnboardingChecklist

/**
 * 시작하기 체크리스트 응답 — **세 목록이다**(D-7C4C-8). 진행률 칸을 두지 않는다: 소비자가 없고,
 * `awaiting` 이 분자에도 분모에도 들지 않기로 한 판단(D-7C4-2)을 숫자 하나가 조용히 되돌린다.
 *
 * **세 상태를 두 상태로 접지 않는다**(D-7C4-8 Q3). 「해당 없음」 항목은 어느 목록에도 없고, 어휘가
 * 닫혀 있으므로 화면은 세 목록의 여집합으로 그것을 안다 — 「해당 없음」 목록을 따로 내면 같은 사실이
 * 두 자리에 생긴다.
 *
 * 순서는 커널이 낸 **선언 순서 그대로** 싣는다(정렬하지 않는다) — 체크리스트가 보이는 순서가 그
 * 순서이고, 여기서 다시 정렬하면 화면 순서가 응답 조립의 판단이 된다.
 *
 * [awaiting] 이 **이 계약의 유일한 깊이 1 object** 다. 스칼라 배열 둘(`awaitingItems` +
 * `awaitingPrerequisites`)로 내는 길은 기각됐다(D-7C4C-1) — 짝이 자리로만 묶여 길이가 어긋난 응답이
 * 표현 가능해진다.
 */
data class OnboardingChecklistResponse(
    val completed: List<String>,
    val pending: List<String>,
    val awaiting: List<AwaitingItemResponse>,
) {
    companion object {
        fun from(checklist: OnboardingChecklist): OnboardingChecklistResponse =
            OnboardingChecklistResponse(
                completed = checklist.completed.map { it.name },
                pending = checklist.pending.map { it.name },
                awaiting = checklist.awaiting.map(AwaitingItemResponse::from),
            )
    }
}

/**
 * 「아직 모름」 한 줄 — 항목과 **그 답을 기다리는 선행 항목**의 짝이다. 선행을 응답의 다른 칸에 두면
 * 두 자리가 어긋나 선행 없는 대기가 표현된다(커널이 타입으로 막은 것과 같은 결함, [AwaitingItem]).
 */
data class AwaitingItemResponse(
    val item: String,
    val prerequisite: String,
) {
    companion object {
        fun from(awaiting: AwaitingItem): AwaitingItemResponse =
            AwaitingItemResponse(item = awaiting.item.name, prerequisite = awaiting.prerequisite.name)
    }
}

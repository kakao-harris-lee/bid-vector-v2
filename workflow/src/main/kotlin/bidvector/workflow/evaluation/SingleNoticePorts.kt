package bidvector.workflow.evaluation

import bidvector.procurement.Notice
import bidvector.procurement.NoticeId
import bidvector.qualification.LicenseJudgement

/**
 * 공고 **하나**를 식별자로 읽는 포트(M7/7C ②) — 읽기 하나뿐이다. 쓰기·상태 전이 메서드가
 * 시그니처에 없으므로 이 포트를 쥔 코드가 공고를 바꿀 길이 없다.
 *
 * `bidvector.procurement.NoticeRepository` 를 그대로 받지 않는 이유가 이것이다 — 그 포트는
 * `persist` 를 함께 들고 있어서, 운영자 요청 평가 경로가 그것을 쥐면 「평가가 공고를 고칠 수
 * 있다」가 타입으로 가능해진다. 좁히는 어댑터 하나가 그 능력을 떼어낸다.
 *
 * [CandidateSourcePort] 를 넓히지 않는다 — 그 포트는 **감시 후보 스캔**(전략이 조건을 정하고
 * 상한 초과가 실패다)이고 이 포트는 **운영자가 지목한 공고 하나**다. 둘을 한 포트로 합치면
 * 「조건 없이 전부」와 「정확히 이것」이 같은 메서드의 두 모드가 된다.
 */
fun interface NoticeByIdPort {
    fun find(id: NoticeId): Notice?
}

/**
 * 자격 판정 **봉투**를 내는 포트(M7/7C ②) — [LicenseGatePort] 는 `verdict` 만 내고 동반
 * 산출(요건 면허명·출처 필드·파싱 실패 행 수·정책 version·유효기간 표시)을 버린다. 화면 C 의
 * 자격 카드는 그 동반 산출이 **근거**라서(QUAL-05 「어떤 원문 → 어떤 규칙 → 어떤 결론」) 버린
 * 값을 쓸 포트가 필요하다.
 *
 * **[LicenseGatePort] 의 시그니처를 넓히지 않는다.** 그 포트를 고치면 `EvaluateCandidatesUseCase`
 * 의 호출 자리가 함께 바뀌어야 하고, 그것은 커널 문면 변경이다. 대신 이 포트가 봉투를 내고
 * 호출부가 그 `verdict` 를 [LicenseGatePort] 자리에 **pin** 해 넘긴다 — 요청당 판정은 한 번이고
 * 응답의 근거와 커널이 쓴 판정이 같은 값에서 나온다(두 자리에 같은 사실을 두지 않는다).
 */
fun interface LicenseJudgementPort {
    fun judgementFor(notice: Notice): LicenseJudgement
}

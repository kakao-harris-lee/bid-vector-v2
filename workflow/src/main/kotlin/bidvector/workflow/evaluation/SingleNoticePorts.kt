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

/**
 * **아직 판정하지 않는 자격 축**(QUAL-08 · QUAL-10 · QUAL-11) — 입력 칸이 있든 없든 V2 에
 * 판정 로직이 없는 축이다. 「판정했고 통과했다」와 「판정하지 않았다」를 운영자가 구분할 수
 * 있게 응답이 이 목록을 그대로 싣는다(자격 카드가 「면허만 보면 끝」으로 읽히지 않게 한다).
 *
 * **이 enum 이 두 표면의 단일 출처다**(`D-7C2-1` Q5) — 7C ④ 의 프로필 조회 응답도 같은 축
 * 어휘를 쓴다. 7C ④ 레인은 미머지라 지금 그쪽을 고칠 수 없으므로, **뒤에 머지되는 쪽이 자기
 * 리터럴을 지우고 이 enum 을 참조한다**(evidence 「머지 때 맞출 자리」).
 *
 * 판정이 생기는 날 그 slice 가 해당 값을 이 enum 에서 **지워야** 한다 — 지우면 응답 계약
 * test 의 집합 등식이 붉어지므로 「판정이 생겼는데 아직 판정하지 않았다고 말하는」 상태가
 * 조용히 남지 않는다.
 */
enum class UnjudgedQualificationAxis {
    /** 지역 자격 — 구조화 지역제한 필드가 수집되지만 판정에 쓰이지 않는다. */
    QUAL_08_REGION,

    /** 수행실적 자격 — V2 에 판정이 없다. */
    QUAL_10_PERFORMANCE,

    /** 시공능력평가금액 요건 — 입력만 서고 미달 처리가 미결(`OPEN-QUAL-09`). */
    QUAL_11_CONSTRUCTION_CAPACITY,
}

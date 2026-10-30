package bidvector.app.http

import bidvector.workflow.evaluation.CandidateEvaluation

/*
 * 투찰가 요청 응답의 **상태 어휘**(M7/7C ②) — 「값이 없다」를 말하는 enum 들이 한자리에 있다.
 * 값이 서는 slice 가 갈래를 더하는 자리이기도 하다.
 */

/**
 * 고지 — **무엇을 고지해야 하는가**만 나르고 한국어 문장은 표면이 짓는다(DEC-12 A1: legacy 는
 * 문구를 영속해 계산이 바뀐 뒤에도 옛 분모를 말했다). 앞 둘은 `decision.menu.BidMenuDisclosure`
 * 의 variant 와 이름이 맞물린다 — 그 커널이 머지되는 slice 가 이 둘을 그 enum 매핑으로 바꾸고
 * 집합 등식 test 를 더한다(어휘 이중화를 그때 닫는다).
 *
 * **전수를 늘 싣는다.** 메뉴가 서지 않아도 「자동 제출이 아니다」·「예정가격은 비공개 추첨」은
 * 투찰가 요청 자체에 대한 사실이고, 그 고지를 빼면 가격 화면이 안내 없이 뜬다.
 */
enum class BidPriceDisclosure {
    YEGA_DRAWN_PRIVATELY,
    NOT_AUTO_SUBMITTED,
    STATUTORY_FLOOR_RATE_NOT_INTERPRETED,
    PRICE_FITNESS_IS_NOT_PROBABILITY,
}

/**
 * 게시 하한율이 근거로 설 수 있는가 — 갈래가 **DEC-10 게이트 판정 넷과 하나씩 맞물린다**
 * (D-7C2B-1, `decision.PublishedFloorTrust`). 「없다」(ABSENT)·「게시값이 아니다」
 * (ORIGIN_NOT_PUBLISHED)·「게시값이지만 개연 밴드 밖이다」(IMPLAUSIBLE)는 서로 다른 사실이고,
 * 셋을 한 값으로 접으면 화면이 원문 품질과 출처 문제를 가를 수 없다(DEC-03 · DEC-10).
 *
 * 갈래를 더하는 유일한 사유는 **게이트 판정이 늘어나는 것**이다 — 표시는 자기 판단으로 상태를
 * 지어내지 않고, 그 사상은 `floorRateGroundsOf` 의 소진 `when` 하나다.
 */
enum class FloorRateGroundsState {
    USABLE,
    ABSENT,
    IMPLAUSIBLE,
    ORIGIN_NOT_PUBLISHED,
}

/**
 * 미보유 면허를 평탄하게 낼 수 있는가(QUAL-03 그룹 간 OR) — **합집합으로 접지 않는다.**
 * 그룹이 둘 이상이면 「이 면허들이 다 필요하다」로 읽히므로 목록을 비우고 이 상태로 말한다.
 */
enum class LicenseMissingDetailState {
    NOT_APPLICABLE,
    SINGLE_GROUP,
    MULTIPLE_GROUPS_NOT_FLATTENED,
}

/** 이 slice 가 산출하지 않는 축의 상태 — 값 하나다. 값이 서는 slice 가 갈래를 더한다. */
enum class NotComputedState {
    NOT_COMPUTED,
}

/** 신뢰도 상태 — 값 하나다. 값이 서면 그것은 가격 적합도이고 확률이 아니다(ML-03). */
enum class ConfidenceState {
    NOT_AVAILABLE,
}

/**
 * 하한 미달 빈도를 재지 못한 사유 — **`FloorUnmeasurableReason` 을 쓰지 않는다.** 그 어휘는
 * 「재려 했으나 재지 못했다」이고 여기는 「잴 기계가 배선되지 않았다」다. 표본이 0 이라고 적으면
 * 보지 않은 것을 봤다고 말하는 것이 된다.
 */
enum class FloorShortfallNotComputedReason {
    SHORTFALL_POLICY_NOT_PUBLISHED,
}

/**
 * 3안을 산출하지 않은 사유 — **이 배선에 산출 경로가 없다**는 사실 하나만 말한다. 커널을 불러
 * 「출처가 없었다」를 받은 것이 아니다(부르지 않았다). 상류 사실 둘(DEC-05 커널 미머지 · 후보
 * 비율의 출처가 미가용 ML)은 계약 문서의 operation description 이 적는다.
 */
enum class BidPriceMenuNotComputedReason {
    MENU_PATH_NOT_WIRED,
}

/** 알림함 기록 상태 — 쓰기 포트가 머지되기 전의 사실이다(R-4 공백을 계약 문면으로 둔다). */
enum class InboxRecordState {
    NOT_RECORDED_YET,
}

enum class InboxRecordNotRecordedReason {
    INBOX_WRITE_PORT_NOT_MERGED,
}

/** 평가가 판정에 이르렀는가 — `CandidateEvaluation` 두 갈래. */
enum class EvaluationOutcomeLabel {
    REACHED,
    NOT_REACHED,
}

/** ML 단계에서 무엇이 일어났는가 — 「이르지 못했다」와 「미가용」은 다른 사실이다. */
enum class MlAnalysisStateLabel {
    ANALYZED,
    UNAVAILABLE,
    SIMILARITY_PROJECTION_NOT_READY,
    NOT_REACHED,
}

/** 신뢰도가 없는 사유 — ML 상태에서 기계적으로 따라온다(두 자리에 다른 값을 두지 않는다). */
enum class ConfidenceNotAvailableReason {
    ML_SCORE_NOT_PROVIDED,
    ML_UNAVAILABLE,
    SIMILARITY_PROJECTION_NOT_READY,
    EVALUATION_NOT_REACHED_ML_STAGE,
}

import type { components } from '../api/generated/operator-api';

import type { StateDisplay } from './stateDisplay';

/**
 * 화면 C(공고 워크시트)가 소비하는 상태 축의 표시 표(D-7E-4 · G2 13 행).
 *
 * 타입은 **생성 타입에서만** 온다 — 계약이 상태 값을 늘리면 `satisfies` 가 컴파일에서
 * 멈춘다(예: 진행 중인 7C②-b 가 `floorRateGroundsState` 에 `IMPLAUSIBLE` 을 더하면 이
 * 파일이 붉어진다). 문면은 **표면이 짓는다**(계약은 코드만 나른다) — 계산이 바뀐 뒤에도
 * 옛 문구가 남는 것을 막으려고 계약이 문장을 싣지 않기 때문이다.
 */
type Response = components['schemas']['BidPriceRequestResponse'];

/** `null` 가능한 칸은 값이 있을 때만 표를 본다 — `null` 은 「그 칸이 없다」이고 상태가 아니다. */
type Present<T> = Exclude<T, null | undefined>;

export const OUTCOME_DISPLAY = {
  REACHED: { label: '판정까지 갔음', tone: 'neutral' },
  NOT_REACHED: { label: '판정 전에 멈춤', why: '어디서 왜 멈췄는지는 아래 단계·사유가 말한다', tone: 'neutral' },
} as const satisfies Record<Response['outcome'], StateDisplay>;

/**
 * DEC-05 · DEC-02 — **3안 자리**. 계약에 금액 칸이 없고 상태와 사유만 있다. 「빈 배열」이
 * 아니라 「이유 있는 상태」라는 것이 이 표의 요점이다.
 */
export const BID_PRICE_MENU_DISPLAY = {
  NOT_COMPUTED: {
    label: '아직 산출 안 함',
    why: '근거가 서지 않아 금액을 내지 않는다 — 빈 값이 아니라 아직 산출하지 않았다는 답이다',
    tone: 'absent',
  },
} as const satisfies Record<Response['bidPriceMenuState'], StateDisplay>;

export const BID_PRICE_MENU_REASON_DISPLAY = {
  MENU_PATH_NOT_WIRED: {
    label: '산출 경로가 이 배선에 없음',
    why: '커널을 불러 「출처가 없었다」를 받은 것이 아니라 부르지 않았다',
    tone: 'absent',
  },
} as const satisfies Record<Response['bidPriceMenuNotComputedReason'], StateDisplay>;

/**
 * DEC-10 — 근거가 되지 못하는 사유를 **갈라서** 말한다: 없다 · 게시값이 아니다 · 게시값이지만
 * 개연 범위 밖이다. 셋을 한 문구로 접으면 운영자가 원문 품질과 출처 문제를 구별할 수 없다.
 *
 * `IMPLAUSIBLE` 이 생긴 경위가 D-7E-4 의 설계 그대로다(7C②-b, 나중 머지 쪽이 더한다) — 계약이
 * 상태를 늘린 순간 생성 타입이 바뀌고 `satisfies` 가 **컴파일에서** 이 파일을 멈춰 세웠다.
 */
export const FLOOR_RATE_GROUNDS_DISPLAY = {
  USABLE: { label: '게시 하한율', why: '공고가 게시한 값이라 근거로 쓴다', tone: 'neutral' },
  ABSENT: { label: '게시 하한율 없음', why: '공고에 그 값이 없다', tone: 'absent' },
  IMPLAUSIBLE: {
    label: '게시 하한율이 개연 범위 밖',
    why: '게시값이지만 하한으로 성립하는 범위를 벗어나 근거로 쓰지 않는다 — 값을 고치지도 않는다',
    tone: 'absent',
  },
  ORIGIN_NOT_PUBLISHED: {
    label: '게시값이 아님',
    why: '값은 있으나 출처가 게시값이 아니라 근거로 쓰지 않는다',
    tone: 'absent',
  },
} as const satisfies Record<Response['floorRateGroundsState'], StateDisplay>;

export const FLOOR_RATE_ORIGIN_DISPLAY = {
  NOTICE_VALUE: { label: '공고 게시값', tone: 'neutral' },
  STATUTORY_TABLE: { label: '법정 표', tone: 'neutral' },
} as const satisfies Record<Present<Response['publishedFloorRateOrigin']>, StateDisplay>;

/** DEC-04 — 「재려 했으나 재지 못했다」와 다른 어휘다. 표본을 보지 않았으므로 표본 수를 적지 않는다. */
export const FLOOR_SHORTFALL_STATE_DISPLAY = {
  NOT_COMPUTED: { label: '재지 못함', tone: 'absent' },
} as const satisfies Record<Response['floorShortfallState'], StateDisplay>;

export const FLOOR_SHORTFALL_REASON_DISPLAY = {
  SHORTFALL_POLICY_NOT_PUBLISHED: {
    label: '하한 미달 정책 표가 아직 출하되지 않음',
    tone: 'absent',
  },
} as const satisfies Record<Response['floorShortfallNotComputedReason'], StateDisplay>;

export const ML_ANALYSIS_STATE_DISPLAY = {
  ANALYZED: { label: '분석함', tone: 'neutral' },
  UNAVAILABLE: { label: '분석 미가용', why: '분석 단계까지 갔으나 쓸 수 없었다', tone: 'absent' },
  SIMILARITY_PROJECTION_NOT_READY: { label: '유사 공고 투영이 준비되지 않음', tone: 'absent' },
  NOT_REACHED: { label: '분석 단계까지 가지 않음', why: '앞 단계에서 멈췄다 — 미가용과 다른 사실이다', tone: 'absent' },
} as const satisfies Record<Response['mlAnalysisState'], StateDisplay>;

export const ML_UNAVAILABLE_REASON_DISPLAY = {
  SCORE_NOT_PROVIDED: { label: '점수 제공자가 없음', tone: 'absent' },
  DEADLINE_EXCEEDED: { label: '분석 기한 초과', tone: 'absent' },
  CIRCUIT_OPEN: { label: '분석 회로 차단', tone: 'absent' },
  RETRY_BUDGET_EXHAUSTED: { label: '재시도 예산 소진', tone: 'absent' },
  TRANSPORT_FAILED: { label: '전송 실패', tone: 'absent' },
  MODEL_NOT_READY: { label: '모델이 준비되지 않음', tone: 'absent' },
  RELEASE_MISMATCH: { label: '릴리스가 맞지 않음', tone: 'absent' },
  CONTRACT_VIOLATION: { label: '분석 계약 위반', tone: 'absent' },
  UNSUPPORTED_SCHEMA: { label: '지원하지 않는 스키마', tone: 'absent' },
  UNSUPPORTED_RELEASE: { label: '지원하지 않는 릴리스', tone: 'absent' },
  INVALID_REQUEST: { label: '요청이 유효하지 않음', tone: 'absent' },
} as const satisfies Record<Present<Response['mlUnavailableReason']>, StateDisplay>;

/** ML-03 — 값이 서는 날에도 그것은 **가격 적합도이고 확률이 아니다**. 이 문면은 확률 어휘를 쓰지 않는다. */
export const CONFIDENCE_STATE_DISPLAY = {
  NOT_AVAILABLE: { label: '신뢰도 없음', tone: 'absent' },
} as const satisfies Record<Response['confidenceState'], StateDisplay>;

export const CONFIDENCE_REASON_DISPLAY = {
  ML_SCORE_NOT_PROVIDED: { label: '분석 점수가 제공되지 않음', tone: 'absent' },
  ML_UNAVAILABLE: { label: '분석이 미가용', tone: 'absent' },
  SIMILARITY_PROJECTION_NOT_READY: { label: '유사 공고 투영이 준비되지 않음', tone: 'absent' },
  EVALUATION_NOT_REACHED_ML_STAGE: { label: '분석 단계까지 가지 않음', tone: 'absent' },
} as const satisfies Record<Response['confidenceNotAvailableReason'], StateDisplay>;

/** QUAL-01 · 03 — **판정 불가는 미달이 아니다**. 두 값이 같은 어조를 쓰지 않는다. */
export const LICENSE_VERDICT_DISPLAY = {
  ELIGIBLE: { label: '면허 충족', tone: 'good' },
  INELIGIBLE: { label: '면허 미달', tone: 'bad' },
  UNCERTAIN: { label: '면허 판정 불가', why: '미달이 아니라 판정하지 못했다', tone: 'unjudged' },
} as const satisfies Record<Response['licenseVerdict'], StateDisplay>;

/** COL-04 — 「요건 없음」과 「수집 실패」를 가른다. */
export const LICENSE_UNCERTAIN_REASON_DISPLAY = {
  REQUIREMENT_DATA_ABSENT: { label: '공고에 요건이 없음', tone: 'unjudged' },
  REQUIREMENT_UNPARSABLE: { label: '요건을 읽지 못함', tone: 'unjudged' },
  OPERATOR_LICENSES_NOT_DECLARED: { label: '내 면허가 아직 선언되지 않음', tone: 'unjudged' },
  COLLECTION_FAILED: { label: '요건 수집에 실패함', why: '요건이 없는 것과 다른 사실이다', tone: 'unjudged' },
  PERMITTED_INDUSTRY_COMBINATION_RULE_UNDECIDED: { label: '허용업종 결합 규칙이 미결', tone: 'unjudged' },
} as const satisfies Record<Present<Response['licenseUncertainReason']>, StateDisplay>;

export const LICENSE_MISSING_DETAIL_DISPLAY = {
  NOT_APPLICABLE: { label: '해당 없음', tone: 'neutral' },
  SINGLE_GROUP: { label: '한 그룹의 미보유 면허', tone: 'neutral' },
  MULTIPLE_GROUPS_NOT_FLATTENED: {
    label: '미충족 그룹이 여럿',
    why: '그룹 간 OR 이라 합쳐 적으면 「이 면허들이 다 필요하다」로 읽힌다',
    tone: 'neutral',
  },
} as const satisfies Record<Response['licenseMissingDetailState'], StateDisplay>;

/** QUAL-02 — 유효기간을 보지 않는다는 사실이 판정 결과에 드러나야 한다. */
export const LICENSE_VALIDITY_DISPLAY = {
  NOT_VERIFIED: { label: '유효기간 미검증', why: '유효기간을 보지 않는다', tone: 'unjudged' },
} as const satisfies Record<Response['licenseValidity'], StateDisplay>;

/** QUAL-08 · QUAL-10 · QUAL-11 — 「판정했고 통과」와 「판정하지 않음」을 가른다. */
export const UNJUDGED_AXIS_DISPLAY = {
  QUAL_08_REGION: { label: '지역', why: '아직 판정하지 않는 축이다', tone: 'unjudged' },
  QUAL_10_PERFORMANCE: { label: '수행실적', why: '아직 판정하지 않는 축이다', tone: 'unjudged' },
  QUAL_11_CONSTRUCTION_CAPACITY: { label: '시공능력평가금액', why: '아직 판정하지 않는 축이다', tone: 'unjudged' },
} as const satisfies Record<Response['unjudgedAxes'][number], StateDisplay>;

/** DEC-01 — 기초금액이 **어떻게 정해졌나**. 파생이 원본을 덮지 않는다는 사실이 이 칸으로 보인다. */
export const BASE_AMOUNT_RESOLUTION_DISPLAY = {
  DIRECT: { label: '게시값 그대로', tone: 'neutral' },
  FALLBACK_FROM_BUDGET: { label: '예산 키에서 폴백', tone: 'neutral' },
  DERIVED_FROM_OPENING: { label: '개찰에서 역산', tone: 'neutral' },
} as const satisfies Record<Present<Response['baseAmountResolution']>, StateDisplay>;

/** R-4 — 채널 발송은 없고 알림함 기록까지다. 그 포트가 아직 서지 않았다는 사실을 그대로 보인다. */
export const INBOX_RECORD_STATE_DISPLAY = {
  NOT_RECORDED_YET: { label: '아직 기록하지 않음', tone: 'absent' },
} as const satisfies Record<Response['inboxRecordState'], StateDisplay>;

export const INBOX_RECORD_REASON_DISPLAY = {
  INBOX_WRITE_PORT_NOT_MERGED: { label: '알림함 쓰기 경로가 아직 머지되지 않음', tone: 'absent' },
} as const satisfies Record<Present<Response['inboxRecordNotRecordedReason']>, StateDisplay>;

/**
 * **고지 넷은 늘 전수가 실린다.** 계약은 코드만 나르고 문장은 여기서 짓는다 — DEC-03 의
 * 「법정 하한율 미해석」이 이 표의 한 행이다.
 */
export const DISCLOSURE_SENTENCE = {
  YEGA_DRAWN_PRIVATELY: {
    label: '예정가격은 개찰 전 비공개 추첨으로 정해져 정확한 낙찰하한을 보장할 수 없습니다.',
    tone: 'neutral',
  },
  NOT_AUTO_SUBMITTED: {
    label: '이 화면은 투찰을 대신 제출하지 않습니다. 제출은 나라장터에서 직접 하십시오.',
    tone: 'neutral',
  },
  STATUTORY_FLOOR_RATE_NOT_INTERPRETED: {
    label: '법정 하한율은 해석하지 않습니다. 근거로 쓰는 것은 공고가 게시한 하한율뿐입니다.',
    tone: 'neutral',
  },
  PRICE_FITNESS_IS_NOT_PROBABILITY: {
    label: '가격 적합도는 낙찰 확률이 아닙니다.',
    tone: 'neutral',
  },
} as const satisfies Record<Response['disclosures'][number], StateDisplay>;

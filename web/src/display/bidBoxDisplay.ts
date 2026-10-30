import type { components } from '../api/generated/operator-api';

import { BID_CASE_STATE_DISPLAY, type BidCaseState } from './caseDisplay';

type Outcome = components['schemas']['RecordOutcomeRequest']['outcome'];

/**
 * 화면 D 내 입찰함의 두 표(M7/7E-2, D-7E2-8).
 *
 * **상태는 API 가 주고 이 파일은 그것을 어디에 놓을지만 정한다.** 탭도 다음 행동도 상태
 * 하나에서 **표 조회**로 나온다 — 문자열 접두를 보거나(`state.startsWith('OPENED')`) 시각을
 * 비교하는 추론이 없다(D-7E-4). 계약에 상태가 늘면 두 표의 `satisfies` 가 컴파일에서 멈춘다.
 */

/** 요구표 「D. 내 입찰함」의 상태별 목록 여섯 — 탭의 **정본 순서**가 이 배열이다. */
export const BID_BOX_TABS = [
  { id: 'watching', label: '관심' },
  { id: 'price-received', label: '투찰가 받음' },
  { id: 'recorded', label: '투찰함' },
  { id: 'awaiting-opening', label: '개찰 대기' },
  { id: 'under-review', label: '심사 중' },
  { id: 'closed', label: '종료' },
] as const;

export type BidBoxTabId = (typeof BID_BOX_TABS)[number]['id'];

/**
 * 상태 14 → 탭 6. 요구표의 여섯 이름을 그대로 쓰므로 **정확히 들어맞지 않는 두 자리**를
 * 팀장이 문면으로 정했다(D-7E2-8):
 *  · `BID_PLANNED`(투찰 예정)는 「투찰가 받음」에 둔다 — 둘 다 제출 전이고 투찰가를 쥔 단계다.
 *  · `DEADLINE_PASSED_UNRECORDED` 는 「종료」에 둔다 — 소급 기록 간선이 남아 있지만 운영자가
 *    매일 보는 묶음은 아니다. 그 사실은 카드의 `bad` 어조 배지가 나른다.
 */
export const STATE_TAB = {
  WATCHING: 'watching',
  DEFERRED: 'watching',
  PRICE_RECEIVED: 'price-received',
  BID_PLANNED: 'price-received',
  BID_RECORDED: 'recorded',
  AWAITING_OPENING: 'awaiting-opening',
  OPENED_PROVISIONAL_FIRST: 'under-review',
  OPENED_OUT_OF_RANK: 'under-review',
  UNDER_QUALIFICATION_REVIEW: 'under-review',
  WON: 'closed',
  LOST: 'closed',
  FAILED_OR_REBID: 'closed',
  SKIPPED: 'closed',
  DEADLINE_PASSED_UNRECORDED: 'closed',
} as const satisfies Record<BidCaseState, BidBoxTabId>;

/** 표 조회의 **유일한 자리** — 폴백이 없다(표 밖의 상태에 탭을 지어내지 않는다). */
export function tabOf(state: BidCaseState): BidBoxTabId {
  return STATE_TAB[state];
}

/**
 * D 가 낼 수 있는 행동의 종류. `OPEN_WORKSHEET` 만 **전이가 아니다** — 담기 뒤 네 상태의
 * 다음 전이는 화면 C 가 가지고 있으므로 D 는 그 화면으로 보낸다.
 */
export type BidBoxActionKind =
  | 'OPEN_WORKSHEET'
  | 'RESUME'
  | 'AWAIT_OPENING'
  | 'RECORD_OPENING'
  | 'ENTER_QUALIFICATION_REVIEW'
  | 'RECORD_OUTCOME';

/**
 * 행동 → 그것이 부르는 **(메서드, 경로)**. `null` 은 「서버를 부르지 않는다」다.
 *
 * 이 표가 있는 이유는 test 가 **승인 목록에서 파생한 집합**과 등식을 둘 수 있게 하는 것이다 —
 * 승인되지 않은 operation 을 부르는 행동을 표에 두면 그 등식이 깨진다.
 */
export const ACTION_OPERATION = {
  OPEN_WORKSHEET: null,
  RESUME: 'POST /api/bid-cases/{caseId}/resume',
  AWAIT_OPENING: 'POST /api/bid-cases/{caseId}/await-opening',
  RECORD_OPENING: 'POST /api/bid-cases/{caseId}/opening',
  ENTER_QUALIFICATION_REVIEW: 'POST /api/bid-cases/{caseId}/qualification-review',
  RECORD_OUTCOME: 'POST /api/bid-cases/{caseId}/outcome',
} as const satisfies Record<BidBoxActionKind, string | null>;

export interface BidBoxAction {
  readonly label: string;
  readonly kind: BidBoxActionKind;
}

/** 한 상태의 행동 둘 — `primary` 가 §6.5 의 「다음 행동 하나」이고 `secondary` 는 갈림이 있을 때만. */
export interface BidBoxActionRow {
  readonly primary: BidBoxAction | null;
  readonly secondary: BidBoxAction | null;
}

const OPEN_WORKSHEET: BidBoxAction = { label: '공고 워크시트', kind: 'OPEN_WORKSHEET' };
const RESUME: BidBoxAction = { label: '다시 보기', kind: 'RESUME' };
const AWAIT_OPENING: BidBoxAction = { label: '개찰 대기로', kind: 'AWAIT_OPENING' };
const RECORD_OPENING: BidBoxAction = { label: '개찰 결과 기록', kind: 'RECORD_OPENING' };
const ENTER_REVIEW: BidBoxAction = { label: '적격심사 중으로', kind: 'ENTER_QUALIFICATION_REVIEW' };
const RECORD_OUTCOME: BidBoxAction = { label: '결과 기록', kind: 'RECORD_OUTCOME' };

const NO_ACTION: BidBoxActionRow = { primary: null, secondary: null };

/**
 * 상태 14 → 다음 행동. **계약 전이표(`BID_TRANSITIONS`)와 같은 모양**이다 — 표에 없는 쌍을
 * 버튼으로 내면 그 요청이 409 가 되므로, 어느 상태에서 어느 전이가 열리는지를 분기가 아니라
 * 데이터로 둔다.
 *
 * **담기 뒤 네 상태**(관심·투찰가 받음·투찰 예정·마감 지남)는 C 로 보낸다 — 그 전이(투찰가
 * 요청·초안·제출함)를 가진 화면이 C 다. `null` 은 「이 slice 가 그 전이를 만들지 않는다」이고
 * 그때 버튼을 **두지 않는다**(「준비 중」 버튼을 지어내지 않는다).
 *
 * `OPENED_PROVISIONAL_FIRST` 만 둘째 행동을 갖는다 — 계약 전이표가 그 상태에서 사용자에게
 * 두 간선(적격심사 진입 · 결과 기록)을 열어 두기 때문이다.
 */
export const BID_BOX_ACTION = {
  WATCHING: { primary: OPEN_WORKSHEET, secondary: null },
  DEFERRED: { primary: RESUME, secondary: null },
  PRICE_RECEIVED: { primary: OPEN_WORKSHEET, secondary: null },
  BID_PLANNED: { primary: OPEN_WORKSHEET, secondary: null },
  BID_RECORDED: { primary: AWAIT_OPENING, secondary: null },
  AWAITING_OPENING: { primary: RECORD_OPENING, secondary: null },
  OPENED_PROVISIONAL_FIRST: { primary: ENTER_REVIEW, secondary: RECORD_OUTCOME },
  OPENED_OUT_OF_RANK: { primary: RECORD_OUTCOME, secondary: null },
  UNDER_QUALIFICATION_REVIEW: { primary: RECORD_OUTCOME, secondary: null },
  WON: NO_ACTION,
  LOST: NO_ACTION,
  FAILED_OR_REBID: NO_ACTION,
  SKIPPED: NO_ACTION,
  DEADLINE_PASSED_UNRECORDED: { primary: OPEN_WORKSHEET, secondary: null },
} as const satisfies Record<BidCaseState, BidBoxActionRow>;

export function bidBoxActionOf(state: BidCaseState): BidBoxActionRow {
  return BID_BOX_ACTION[state];
}

/**
 * 「지난 일정」 경고의 문면 — **판정은 서버가 한다**(`BidCase.overdue`). 이 값은 그 boolean 을
 * 문면으로 옮길 뿐이고 시각을 비교하지 않는다.
 */
export const OVERDUE_DISPLAY = {
  label: '지난 일정',
  why: '다음 확인 시점이 지났다고 서버가 판정했다',
  tone: 'bad',
} as const;

/**
 * 개찰 결과의 두 선택지 — 계약은 **순위 숫자를 받지 않는다**(`provisionalFirst` boolean 하나).
 * 문면은 그 두 결과 상태의 라벨이다.
 */
const OPENING_CHOICES = [
  { value: 'true', label: BID_CASE_STATE_DISPLAY.OPENED_PROVISIONAL_FIRST.label },
  { value: 'false', label: BID_CASE_STATE_DISPLAY.OPENED_OUT_OF_RANK.label },
] as const;

/** 결과 셋 — 계약 enum 전수(`RecordOutcomeRequest.outcome`)이고 문면은 상태 표에서 온다. */
const OUTCOME_CHOICES: readonly { readonly value: Outcome; readonly label: string }[] = [
  { value: 'WON', label: BID_CASE_STATE_DISPLAY.WON.label },
  { value: 'LOST', label: BID_CASE_STATE_DISPLAY.LOST.label },
  { value: 'FAILED_OR_REBID', label: BID_CASE_STATE_DISPLAY.FAILED_OR_REBID.label },
];

/** 행동이 받아야 하는 입력의 모양. `LINK` 만 서버를 부르지 않는다. */
export type ActionInputShape = 'LINK' | 'INSTANT' | 'DATETIME' | 'CHOICE';

export interface ActionChoice {
  readonly value: string;
  readonly label: string;
}

export interface ActionInput {
  readonly shape: ActionInputShape;
  /** 입력 칸의 라벨 — `LINK`·`INSTANT` 에는 칸이 없다. */
  readonly label?: string;
  readonly choices?: readonly ActionChoice[];
}

/**
 * 행동 → 그것이 운영자에게 묻는 것. **기본값을 두지 않는다**(§6.5 「값이 없으면 빈 자리」):
 * 개찰 결과와 결과 셋은 고르기 전에는 요청을 보내지 않는다 — `false`·`LOST` 를 기본값으로
 * 두면 운영자가 고르지 않은 사실이 기록으로 남는다.
 *
 * 선택지의 문면은 **상태 표에서 가져온다**(`BID_CASE_STATE_DISPLAY`) — 같은 말을 두 곳에
 * 적지 않는다. 계약이 결과 어휘를 늘리면 그 표가 먼저 멈춘다.
 */
export const ACTION_INPUT: Record<BidBoxActionKind, ActionInput> = {
  OPEN_WORKSHEET: { shape: 'LINK' },
  RESUME: { shape: 'INSTANT' },
  AWAIT_OPENING: { shape: 'DATETIME', label: '개찰 일시' },
  RECORD_OPENING: { shape: 'CHOICE', label: '개찰 결과', choices: OPENING_CHOICES },
  ENTER_QUALIFICATION_REVIEW: { shape: 'DATETIME', label: '서류 기한' },
  RECORD_OUTCOME: { shape: 'CHOICE', label: '결과', choices: OUTCOME_CHOICES },
};

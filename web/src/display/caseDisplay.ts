import type { components } from '../api/generated/operator-api';

import { displayOrUnknown, type StateDisplay } from './stateDisplay';

type BidCase = components['schemas']['BidCase'];
export type BidCaseState = BidCase['state'];

/**
 * 입찰 건의 상태 어휘(§6.1). **「보류」는 어휘에 없다** — 「나중에」와 「건너뜀」으로 갈렸고,
 * 그 갈림이 표의 두 행으로 서 있다.
 */
export const BID_CASE_STATE_DISPLAY = {
  WATCHING: { label: '관심', why: '입찰함에 담았다', tone: 'neutral' },
  DEFERRED: { label: '나중에', why: '리마인드 시각을 가진다', tone: 'neutral' },
  PRICE_RECEIVED: { label: '투찰가 받음', tone: 'neutral' },
  BID_PLANNED: { label: '투찰 예정', why: '초안이 있고 아직 제출 기록이 없다', tone: 'neutral' },
  BID_RECORDED: { label: '투찰 기록됨', tone: 'good' },
  AWAITING_OPENING: { label: '개찰 대기', tone: 'neutral' },
  OPENED_PROVISIONAL_FIRST: { label: '잠정 1위', tone: 'good' },
  OPENED_OUT_OF_RANK: { label: '순위 밖', tone: 'neutral' },
  UNDER_QUALIFICATION_REVIEW: { label: '적격심사 중', tone: 'neutral' },
  WON: { label: '낙찰', tone: 'good' },
  LOST: { label: '패찰', tone: 'neutral' },
  FAILED_OR_REBID: { label: '유찰·재공고', tone: 'neutral' },
  SKIPPED: { label: '건너뜀', why: '사유를 가진다', tone: 'neutral' },
  DEADLINE_PASSED_UNRECORDED: {
    label: '마감 지남(미확인)',
    why: '소급 확인이 가능하다',
    tone: 'bad',
  },
} as const satisfies Record<BidCaseState, StateDisplay>;

/**
 * 다음 확인 시점 — **「없음」은 빈 값이 아니라 답이다**(§6.1). 시각 하나로 접으면 화면이
 * 「아직 안 정했다」와 「없는 것이 맞다」를 구별할 수 없다.
 */
export const NEXT_CHECKPOINT_DISPLAY = {
  DEADLINE: { label: '투찰 마감', tone: 'neutral' },
  OPENING: { label: '개찰', tone: 'neutral' },
  DOCUMENT_DUE: { label: '서류 기한', tone: 'neutral' },
  NOTICE_EXPECTED: { label: '통보 예정', tone: 'neutral' },
  REMINDER: { label: '리마인드', tone: 'neutral' },
  NONE: { label: '없음', tone: 'absent' },
} as const satisfies Record<BidCase['nextCheckpointKind'], StateDisplay>;

/** 페이퍼와 실투찰은 같은 상태 어휘를 쓰되 **같은 목록에 섞이지 않는다**(SET-03). */
export const BID_KIND_DISPLAY = {
  REAL: { label: '실투찰', tone: 'neutral' },
  PAPER: { label: '페이퍼(가상)', why: '실측과 섞지 않는다', tone: 'neutral' },
} as const satisfies Record<BidCase['bidKind'], StateDisplay>;

export const TIMELINE_ACTOR_DISPLAY = {
  USER: { label: '나', tone: 'neutral' },
  SYSTEM: { label: '시스템', why: '사람 이름이 아니라 경로의 이름이다', tone: 'neutral' },
} as const satisfies Record<components['schemas']['BidTimelineEntry']['actorKind'], StateDisplay>;

/**
 * 타임라인에 남는 **명령 어휘**(`OPEN-M9-TIMELINE-COMMAND-WORDING`). 앞 판은 이 칸만 서버가 준
 * 토큰 원문이었다 — 상태·주체·부가 사실은 이미 표를 지났고 명령이 마지막 원문 축이었다.
 *
 * **계약이 이 칸을 `string` 으로 선언하므로**(`BidTimelineEntry.command`) 생성 타입에서 유니온을
 * 끌어올 수 없다 — `moneyDisplay` 의 basis·provenance 와 같은 자리다. 그래서 어휘를 여기
 * 선언하고 `satisfies` 로 전수를 잠그되, 그 유니온이 **Kotlin 정본과 어긋나는 것**은
 * `scripts/money-vocab-cross-lang.test.mjs` 의 집합 등식이 잡는다(유니온까지 함께 줄이는 변이가
 * `satisfies` 를 지나가기 때문이다).
 *
 * 열다섯 = 전이표의 열 키 열넷 + **건을 여는 명령 하나**. 여는 명령은 출발 상태가 없어 전이표의
 * 행이 될 수 없고, 타임라인에는 `fromState = null` 인 생성 전이로 남는다.
 */
export type BidCommandToken =
  | 'ADD_TO_BID_BOX'
  | 'DEFER'
  | 'RESUME'
  | 'RECEIVE_PRICE_MENU'
  | 'DRAFT_BID'
  | 'RECORD_BID'
  | 'AWAIT_OPENING'
  | 'RECORD_OPENING_FIRST'
  | 'RECORD_OPENING_OUT_OF_RANK'
  | 'ENTER_QUALIFICATION_REVIEW'
  | 'RECORD_OUTCOME'
  | 'CORRECT_OUTCOME'
  | 'SKIP'
  | 'MARK_DEADLINE_PASSED'
  | 'REVOKE_DEADLINE_PASSED';

export const BID_COMMAND_DISPLAY = {
  ADD_TO_BID_BOX: { label: '입찰함에 담음', why: '건이 열린 전이라 출발 상태가 없다', tone: 'neutral' },
  DEFER: { label: '나중에로 미룸', tone: 'neutral' },
  RESUME: { label: '나중에 해제', tone: 'neutral' },
  RECEIVE_PRICE_MENU: { label: '투찰가 메뉴 받음', tone: 'neutral' },
  // 도메인이 명령을 **상태별로 쪼개지 않는다** — 초안 만들기와 수정이 한 명령이고, 무엇이
  // 달랐는지는 같은 줄의 출발 상태가 적는다. 그 사실을 아래층에 둔다.
  DRAFT_BID: { label: '투찰 예정 초안', why: '초안 수정도 같은 명령이다 — 무엇이 달랐는지는 출발 상태가 적는다', tone: 'neutral' },
  RECORD_BID: {
    label: '투찰 기록',
    why: '「제출함」 확인 · 기록 갱신 · 소급 확인이 모두 이 하나다',
    tone: 'neutral',
  },
  AWAIT_OPENING: { label: '개찰 대기로 넘김', tone: 'neutral' },
  // 잠정 1위와 순위 밖을 한 문구로 접지 않는다(SET-02 와 같은 축).
  RECORD_OPENING_FIRST: { label: '개찰 결과 기록 — 잠정 1위', tone: 'neutral' },
  RECORD_OPENING_OUT_OF_RANK: { label: '개찰 결과 기록 — 순위 밖', tone: 'neutral' },
  ENTER_QUALIFICATION_REVIEW: { label: '적격심사 진입', tone: 'neutral' },
  RECORD_OUTCOME: { label: '결과 기록', tone: 'neutral' },
  CORRECT_OUTCOME: { label: '결과 정정', why: '원본을 덮지 않는다 — 이전 결과는 출발 상태로 남는다', tone: 'neutral' },
  SKIP: { label: '건너뜀 처리', tone: 'neutral' },
  MARK_DEADLINE_PASSED: { label: '마감 지남 표시', why: '시스템이 시각으로 세운 판정이다', tone: 'neutral' },
  REVOKE_DEADLINE_PASSED: {
    label: '마감 지남 판정 거둠',
    why: '유효 마감이 다시 미래가 되어 시스템이 거뒀다',
    tone: 'neutral',
  },
} as const satisfies Record<BidCommandToken, StateDisplay>;

/**
 * 명령 칸을 읽는 **한 자리**. 계약이 `string` 이라 키 좁힘을 여기 한 번만 둔다 — 표 밖 값은
 * `displayOrUnknown` 이 **보이게** 낸다(`moneyDisplay` 의 네 조회와 같은 관례).
 */
export function commandDisplayOf(token: string): StateDisplay {
  return displayOrUnknown<string>(BID_COMMAND_DISPLAY, token);
}

/** 다음 행동 하나의 종류 — 화면이 어느 전이를 부를지 고르는 자리다. */
export type NextActionKind = 'REQUEST_PRICE' | 'DRAFT_BID' | 'RECORD_BID' | 'RESUME';

export interface NextAction {
  readonly label: string;
  readonly kind: NextActionKind;
}

const REQUEST_PRICE: NextAction = { label: '투찰가 요청', kind: 'REQUEST_PRICE' };
const DRAFT_BID: NextAction = { label: '투찰 예정 초안', kind: 'DRAFT_BID' };
const RECORD_BID: NextAction = { label: '제출함', kind: 'RECORD_BID' };
const RESUME: NextAction = { label: '다시 보기', kind: 'RESUME' };

/** 투찰가 결과를 **지금 손에 쥐고 있는가** — 같은 상태에서 다음 행동을 가르는 둘째 축이다. */
interface NextActionRow {
  readonly withoutPrice: NextAction | null;
  readonly withPrice: NextAction | null;
}

/**
 * §6.5 「다음 행동 하나가 화면을 지배한다」 — primary 버튼은 **그 상태의 다음 전이**다.
 *
 * **왜 축이 둘인가**(Phase 1 실측): 투찰가 요청은 건의 상태를 바꾸지 않는다(요청 뒤에도
 * `WATCHING` 이다 — 그 요청의 종착점은 알림함 기록이고 그 포트는 아직 서지 않았다). 그래서
 * 「요청했는가」는 계약에 적히는 사실이 아니라 **이 화면이 쥔 결과**이고, 그 둘째 축을 상태와
 * 함께 표의 열로 둔다. 분기로 적으면 상태마다 예외가 늘어난다.
 *
 * 표이므로 「어느 상태에서 어느 버튼」이 **데이터**이고 상태가 늘면 `satisfies` 가 이 자리에서
 * 멈춘다. `null` 은 「이 slice 가 그 전이를 만들지 않는다」이고 화면은 그때 primary 버튼을
 * **두지 않는다**(「준비 중」 버튼을 지어내지 않는다).
 */
const NEXT_ACTION = {
  WATCHING: { withoutPrice: REQUEST_PRICE, withPrice: DRAFT_BID },
  DEFERRED: { withoutPrice: RESUME, withPrice: RESUME },
  PRICE_RECEIVED: { withoutPrice: DRAFT_BID, withPrice: DRAFT_BID },
  BID_PLANNED: { withoutPrice: RECORD_BID, withPrice: RECORD_BID },
  BID_RECORDED: { withoutPrice: null, withPrice: null },
  AWAITING_OPENING: { withoutPrice: null, withPrice: null },
  OPENED_PROVISIONAL_FIRST: { withoutPrice: null, withPrice: null },
  OPENED_OUT_OF_RANK: { withoutPrice: null, withPrice: null },
  UNDER_QUALIFICATION_REVIEW: { withoutPrice: null, withPrice: null },
  WON: { withoutPrice: null, withPrice: null },
  LOST: { withoutPrice: null, withPrice: null },
  FAILED_OR_REBID: { withoutPrice: null, withPrice: null },
  SKIPPED: { withoutPrice: null, withPrice: null },
  DEADLINE_PASSED_UNRECORDED: { withoutPrice: null, withPrice: null },
} as const satisfies Record<BidCaseState, NextActionRow>;

export function nextActionOf(state: BidCaseState, priceInHand = false): NextAction | null {
  return NEXT_ACTION[state][priceInHand ? 'withPrice' : 'withoutPrice'];
}

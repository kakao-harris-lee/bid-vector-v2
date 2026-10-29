import type { components } from '../api/generated/operator-api';

import type { StateDisplay } from './stateDisplay';

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

/** 다음 행동 하나의 종류 — 화면이 어느 전이를 부를지 고르는 자리다. */
export type NextActionKind = 'REQUEST_PRICE' | 'DRAFT_BID' | 'RECORD_BID' | 'RESUME';

export interface NextAction {
  readonly label: string;
  readonly kind: NextActionKind;
}

/**
 * §6.5 「다음 행동 하나가 화면을 지배한다」 — primary 버튼은 **그 상태의 다음 전이**다.
 *
 * 상태별 표이므로 「어느 상태에서 어느 버튼」이 분기가 아니라 **데이터**이고, 상태가 늘면
 * `satisfies` 가 이 자리에서 멈춘다. `null` 은 「이 slice 가 그 전이를 만들지 않는다」이고
 * 화면은 그때 primary 버튼을 **두지 않는다**(「준비 중」 버튼을 지어내지 않는다).
 */
const NEXT_ACTION: Readonly<Record<BidCaseState, NextAction | null>> = {
  WATCHING: { label: '투찰가 요청', kind: 'REQUEST_PRICE' },
  DEFERRED: { label: '다시 보기', kind: 'RESUME' },
  PRICE_RECEIVED: { label: '투찰 예정 초안', kind: 'DRAFT_BID' },
  BID_PLANNED: { label: '제출함', kind: 'RECORD_BID' },
  BID_RECORDED: null,
  AWAITING_OPENING: null,
  OPENED_PROVISIONAL_FIRST: null,
  OPENED_OUT_OF_RANK: null,
  UNDER_QUALIFICATION_REVIEW: null,
  WON: null,
  LOST: null,
  FAILED_OR_REBID: null,
  SKIPPED: null,
  DEADLINE_PASSED_UNRECORDED: null,
} as const satisfies Record<BidCaseState, NextAction | null>;

export function nextActionOf(state: BidCaseState): NextAction | null {
  return NEXT_ACTION[state];
}

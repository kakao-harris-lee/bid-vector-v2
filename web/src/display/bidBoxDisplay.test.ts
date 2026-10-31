import { describe, expect, it } from 'vitest';

import { APPROVED_OPERATIONS } from '../api/approvedOperations';
import { BID_CASE_STATE_DISPLAY, type BidCaseState } from './caseDisplay';
import {
  ACTION_OPERATION,
  BID_BOX_ACTION,
  BID_BOX_TABS,
  STATE_TAB,
  bidBoxActionOf,
  tabOf,
  type BidBoxActionKind,
  type BidBoxTabId,
} from './bidBoxDisplay';

/** 계약 enum 전수 — 이 집합이 표의 키 집합과 같은지가 아래 등식들의 기준이다. */
const CONTRACT_STATES = Object.keys(BID_CASE_STATE_DISPLAY) as readonly BidCaseState[];

describe('D 내 입찰함의 상태 → 탭 표', () => {
  it('요구표의 여섯 탭이고 id 가 중복되지 않는다', () => {
    const ids = BID_BOX_TABS.map((one) => one.id);

    expect(ids).toStrictEqual(['watching', 'price-received', 'recorded', 'awaiting-opening', 'under-review', 'closed']);
    expect(new Set(ids).size).toBe(ids.length);
  });

  /**
   * **탭은 상태의 묶음이고 추론이 아니다**(D-7E-4). 상태 값은 API 가 주고, 그 값을 어느 탭에
   * 넣는지만 이 표가 정한다. 계약에 상태가 늘면 `satisfies` 가 컴파일에서 멈추고, 이 등식은
   * 그 전제(키 집합 == 계약 전수)가 실제로 성립하는지를 잰다.
   */
  it('표의 키 집합이 계약 상태 전수와 같다', () => {
    expect(Object.keys(STATE_TAB).sort()).toStrictEqual([...CONTRACT_STATES].sort());
  });

  it('모든 상태가 여섯 탭 가운데 하나로 간다 — 표 밖의 탭 0', () => {
    const tabIds = new Set<string>(BID_BOX_TABS.map((one) => one.id));

    for (const state of CONTRACT_STATES) {
      expect(tabIds, `${state} 가 표에 없는 탭으로 간다`).toContain(STATE_TAB[state]);
    }
  });

  it('여섯 탭 전부 적어도 한 상태를 받는다 — 빈 탭을 만들지 않는다', () => {
    const used = new Set<BidBoxTabId>(CONTRACT_STATES.map((state) => STATE_TAB[state]));

    expect([...used].sort()).toStrictEqual(BID_BOX_TABS.map((one) => one.id).sort());
  });

  /** D-7E2-8 이 문면으로 정한 두 자리 — 여섯 이름에 정확히 들어맞지 않아 결정이 필요했던 곳이다. */
  it('D-7E2-8 의 두 결정이 표에 있다', () => {
    expect(STATE_TAB.BID_PLANNED).toBe('price-received');
    expect(STATE_TAB.DEADLINE_PASSED_UNRECORDED).toBe('closed');
  });

  it('조회 자리에 폴백이 없다 — 표의 값을 그대로 낸다', () => {
    for (const state of CONTRACT_STATES) {
      expect(tabOf(state)).toBe(STATE_TAB[state]);
    }
  });
});

describe('D 내 입찰함의 다음 행동 표', () => {
  it('표의 키 집합이 계약 상태 전수와 같다', () => {
    expect(Object.keys(BID_BOX_ACTION).sort()).toStrictEqual([...CONTRACT_STATES].sort());
  });

  /**
   * **전이 행동은 승인 목록에서 파생한다**(목록을 복제하지 않는다). 승인되지 않은 operation 을
   * 부르는 행동을 표에 두면 이 등식이 깨진다 — 「화면의 능력 == 승인 목록」을 표 쪽에서 잠근다.
   */
  it('표의 전이 행동이 전부 승인 목록 안이다', () => {
    const approved = new Set(APPROVED_OPERATIONS.map((one) => `${one.method} ${one.path}`));
    const operations = Object.values(ACTION_OPERATION).filter((one) => one !== null);

    expect(operations.length, '전이 행동이 하나도 없으면 이 단언이 공허해진다').toBeGreaterThan(0);
    expect(operations.filter((one) => !approved.has(one))).toStrictEqual([]);
  });

  it('행동 종류 전수가 operation 표에 있다 — 모르는 종류 0', () => {
    const kinds = new Set<BidBoxActionKind>();
    for (const state of CONTRACT_STATES) {
      const row = BID_BOX_ACTION[state];
      for (const action of [row.primary, row.secondary]) {
        if (action !== null) {
          kinds.add(action.kind);
        }
      }
    }

    for (const kind of kinds) {
      expect(Object.keys(ACTION_OPERATION), `${kind} 가 operation 표에 없다`).toContain(kind);
    }
  });

  /**
   * 계약 전이표(`BID_TRANSITIONS`)가 허락하는 쌍과 같은 모양인지 — 「제출함」 뒤 넷은 D 가
   * 가지고, 담기 뒤 넷은 C 가 가진다(전이가 아니라 화면 이동이다).
   */
  it('「제출함」 뒤 생애주기 넷이 그 상태의 primary 다', () => {
    expect(bidBoxActionOf('BID_RECORDED').primary?.kind).toBe('AWAIT_OPENING');
    expect(bidBoxActionOf('AWAITING_OPENING').primary?.kind).toBe('RECORD_OPENING');
    expect(bidBoxActionOf('OPENED_PROVISIONAL_FIRST').primary?.kind).toBe('ENTER_QUALIFICATION_REVIEW');
    expect(bidBoxActionOf('OPENED_OUT_OF_RANK').primary?.kind).toBe('RECORD_OUTCOME');
    expect(bidBoxActionOf('UNDER_QUALIFICATION_REVIEW').primary?.kind).toBe('RECORD_OUTCOME');
  });

  it('잠정 1위에서만 둘째 행동이 있다 — 적격심사를 거치지 않는 결과 기록', () => {
    const withSecondary = CONTRACT_STATES.filter((state) => BID_BOX_ACTION[state].secondary !== null);

    expect(withSecondary).toStrictEqual(['OPENED_PROVISIONAL_FIRST']);
    expect(bidBoxActionOf('OPENED_PROVISIONAL_FIRST').secondary?.kind).toBe('RECORD_OUTCOME');
  });

  it('담기 뒤 네 상태는 C 로 보낸다 — D 가 그 전이를 부르지 않는다', () => {
    for (const state of ['WATCHING', 'PRICE_RECEIVED', 'BID_PLANNED', 'DEADLINE_PASSED_UNRECORDED'] as const) {
      expect(bidBoxActionOf(state).primary?.kind, state).toBe('OPEN_WORKSHEET');
    }
    expect(ACTION_OPERATION.OPEN_WORKSHEET, '화면 이동은 operation 을 부르지 않는다').toBeNull();
  });

  it('나중에는 되돌림이 primary 이고 워크시트가 둘째가 아니다 — 다음 행동은 하나다', () => {
    expect(bidBoxActionOf('DEFERRED').primary?.kind).toBe('RESUME');
    expect(bidBoxActionOf('DEFERRED').secondary).toBeNull();
  });

  /**
   * 결과 셋과 건너뜀에 버튼을 **두지 않는다** — 정정(`/outcome-correction`)은 목록 밖이고
   * (D-7E2-8) 「준비 중」 버튼을 지어내지 않는다(D-7E-5).
   */
  it('종단·결과 상태에는 행동이 없다', () => {
    for (const state of ['WON', 'LOST', 'FAILED_OR_REBID', 'SKIPPED'] as const) {
      expect(bidBoxActionOf(state).primary, state).toBeNull();
      expect(bidBoxActionOf(state).secondary, state).toBeNull();
    }
  });
});

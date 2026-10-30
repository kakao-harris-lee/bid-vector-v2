import { describe, expect, it } from 'vitest';

import { BID_CASE_STATE_DISPLAY, type BidCaseState } from './caseDisplay';
import { RESULT_PENDING_SLOTS, RESULT_STAGE, isResultStage } from './resultDisplay';

const CONTRACT_STATES = Object.keys(BID_CASE_STATE_DISPLAY) as readonly BidCaseState[];

describe('화면 E 가 보이는 상태의 묶음', () => {
  it('표의 키 집합이 계약 상태 전수와 같다 — 폴백이 없다', () => {
    expect(Object.keys(RESULT_STAGE).sort()).toStrictEqual([...CONTRACT_STATES].sort());
  });

  /**
   * 계약 문면(「화면 E 건별」)이 든 일곱이다. 「제출함」(`BID_RECORDED`)은 **아직 결과 단계가
   * 아니다** — 그 건의 다음 행동은 개찰 대기로 넘기는 것이고 그 전이는 D 가 든다.
   */
  it('결과 단계는 일곱이고 계약이 든 이름과 같다', () => {
    const shown = CONTRACT_STATES.filter((state) => RESULT_STAGE[state]);

    expect([...shown].sort()).toStrictEqual(
      [
        'AWAITING_OPENING',
        'OPENED_PROVISIONAL_FIRST',
        'OPENED_OUT_OF_RANK',
        'UNDER_QUALIFICATION_REVIEW',
        'WON',
        'LOST',
        'FAILED_OR_REBID',
      ].sort(),
    );
  });

  it('담기 뒤·종료 가지는 E 가 보이지 않는다', () => {
    for (const state of ['WATCHING', 'DEFERRED', 'PRICE_RECEIVED', 'BID_PLANNED', 'BID_RECORDED', 'SKIPPED', 'DEADLINE_PASSED_UNRECORDED'] as const) {
      expect(isResultStage(state), `${state} 가 E 에 들어왔다`).toBe(false);
    }
  });

  it('조회 자리가 표를 그대로 낸다 — 추론이 없다', () => {
    for (const state of CONTRACT_STATES) {
      expect(isResultStage(state)).toBe(RESULT_STAGE[state]);
    }
  });
});

describe('E 의 빈 자리 (D-7E3a-3)', () => {
  /**
   * 빈 자리를 **표로** 두는 이유: 「준비 중」을 화면 여기저기에 글로 적으면 7E-3b 가 무엇을
   * 채워야 하는지가 코드에서 사라진다. 표에 있으면 그 slice 가 줄을 지우는 diff 로 끝난다.
   */
  it('일곱 자리이고 id 가 중복되지 않는다', () => {
    const ids = RESULT_PENDING_SLOTS.map((one) => one.id);

    expect(ids).toStrictEqual([
      'my-bid-amount',
      'award-difference',
      'chosen-plan',
      'reconciliation',
      'win-rate',
      'back-calculation',
      'recommendation-error',
    ]);
    expect(new Set(ids).size).toBe(ids.length);
  });

  /** 뒤에 오는 것과 **영영 오지 않는 것**은 다른 사실이다 — 운영자가 기다릴지 말지가 갈린다. */
  it('7E-3b 몫 다섯과 영구 빈 자리 둘이 갈려 있다', () => {
    const later = RESULT_PENDING_SLOTS.filter((one) => one.kind === 'LATER').map((one) => one.id);
    const permanent = RESULT_PENDING_SLOTS.filter((one) => one.kind === 'PERMANENT').map((one) => one.id);

    expect(later).toStrictEqual([
      'my-bid-amount',
      'award-difference',
      'chosen-plan',
      'reconciliation',
      'win-rate',
    ]);
    expect(permanent).toStrictEqual(['back-calculation', 'recommendation-error']);
  });

  it('자리마다 사유가 있다 — 「준비 중」만 적지 않는다', () => {
    for (const slot of RESULT_PENDING_SLOTS) {
      expect(slot.why.length, `${slot.id} 에 사유가 없다`).toBeGreaterThan(0);
    }
  });
});

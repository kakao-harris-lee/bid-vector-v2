import { describe, expect, it } from 'vitest';

import { BID_CASE_STATE_DISPLAY, type BidCaseState } from './caseDisplay';
import { RESULT_PENDING_SLOTS, RESULT_STAGE, isResultStage, pendingBadgeOf } from './resultDisplay';

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

describe('E 가 다루지 않는 자리 (D-7E3a-3 → M7/7E-3b)', () => {
  /**
   * 빈 자리를 **표로** 둔 덕에 7E-3b 의 일이 **줄을 지우는 diff** 로 끝났다: 「준비 중」이던
   * 다섯(내 투찰가 · 차이 · 고른 안 · 자동 대사 · 낙찰률)은 이제 화면에 서므로 표에서 사라졌고,
   * 남은 둘은 **영영 오지 않는** 자리다.
   */
  it('둘뿐이고 id 가 중복되지 않는다', () => {
    const ids = RESULT_PENDING_SLOTS.map((one) => one.id);

    expect(ids).toStrictEqual(['back-calculation', 'recommendation-error']);
    expect(new Set(ids).size).toBe(ids.length);
  });

  /**
   * **「준비 중」이 남아 있으면 안 된다.** 7E-3b 가 채운 자리를 표에 남겨 두면 화면이 이미 그린
   * 값 옆에 「준비 중」이 함께 서고, 운영자는 그 칸이 아직 안 되는 것으로 읽는다.
   */
  it('뒤에 오는 자리(LATER)가 0 이다 — 다섯은 화면이 받았다', () => {
    const later = RESULT_PENDING_SLOTS.filter((one) => one.kind === 'LATER');

    expect(later, `아직 「준비 중」인 자리가 남았다: ${later.map((one) => one.id).join(' · ')}`).toStrictEqual(
      [],
    );
  });

  /**
   * **`kind` 는 그대로 둔다**(팀장 판정 2026-11-20 · 13A OPEN ⓖ). 장부가 2026-11-14 에 페이퍼
   * 투찰 정산을 **채택**했으므로 이 둘이 영영 오지 않는다는 말은 낡았다. 그러나 그 기능
   * 마일스톤이 값을 낼 때까지는 `kind` 가 무엇이어야 하는지도 정해지지 않았다 — 그래서 이
   * slice 는 **배지 문면만** 사유 쪽에 맞춘다. 이 단언은 그 사실을 적는다: 자리 둘의 분류는
   * 움직이지 않았다.
   */
  it('남은 둘의 분류가 그대로다 — 이 slice 는 `kind` 를 건드리지 않았다', () => {
    const permanent = RESULT_PENDING_SLOTS.filter((one) => one.kind === 'PERMANENT').map((one) => one.id);

    expect(permanent).toStrictEqual(['back-calculation', 'recommendation-error']);
  });

  it('자리마다 사유가 있다 — 배지만 적지 않는다', () => {
    for (const slot of RESULT_PENDING_SLOTS) {
      expect(slot.why.length, `${slot.id} 에 사유가 없다`).toBeGreaterThan(0);
    }
  });

  /**
   * **배지가 사유와 같은 말을 한다**(13A OPEN ⓖ 를 닫는 자리).
   *
   * 앞 판은 한 카드에서 두 말을 했다 — 배지는 「다루지 않음」(영영 오지 않는다), 사유는
   * 「지금은 제공하지 않습니다」(지금은 아니다). 13A 가 **사유만** 장부에 맞췄고 배지는 낡은
   * 결정 쪽에 남았다. 추천은 장부 쪽이므로 배지를 옮긴다.
   *
   * 술어가 **축어 비교가 아닌** 까닭: 「다루지 않음」이라는 글자를 금지하면 비슷한 다른 말로
   * 비켜 갈 수 있다. 대신 배지와 사유가 **같은 시간을 말하는지**를 잰다 — 배지가 영구를
   * 주장하면 사유의 「지금은」과 어긋난다.
   */
  it('배지가 영구를 주장하지 않는다 — 사유의 「지금은」과 어긋나지 않는다', () => {
    const permanent = RESULT_PENDING_SLOTS.filter((one) => one.kind === 'PERMANENT');

    expect(permanent.length, '영구 자리가 0 이면 이 단언이 공허하다').toBeGreaterThan(0);
    for (const slot of permanent) {
      expect(slot.why, `${slot.id}: 사유가 「지금은」을 말하지 않는다`).toContain('지금은');
      expect(
        pendingBadgeOf(slot.kind),
        `${slot.id}: 배지가 영영 오지 않는다고 말하는데 사유는 「지금은」이라 한다`,
      ).toContain('지금은');
    }
  });

  it('뒤에 오는 자리와 지금 없는 자리의 배지가 서로 다르다 — 분류가 문면으로 산다', () => {
    expect(pendingBadgeOf('LATER')).not.toBe(pendingBadgeOf('PERMANENT'));
    expect(pendingBadgeOf('LATER').length).toBeGreaterThan(0);
    expect(pendingBadgeOf('PERMANENT').length).toBeGreaterThan(0);
  });
});

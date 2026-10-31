// @vitest-environment jsdom
import { cleanup, render, screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';

import type { MyBidAggregate } from '../api/queries';
import {
  AWARD_RATE_STATE_DISPLAY,
  RECONCILIATION_BLOCKER_DISPLAY,
  SAMPLE_NOTE,
} from '../display/myBidResultDisplay';

import { ResultsAggregate } from './ResultsAggregate';

/**
 * 칸마다 **다른 수**를 둔다 — 두 축이 같은 수를 쓰면 접두를 바꿔 끼운 변이(`recorded*` ↔
 * `reconciled*`)가 화면 단언을 조용히 지나간다.
 */
function aggregate(overrides: Partial<MyBidAggregate> = {}): MyBidAggregate {
  return {
    sampleSize: 30,
    recordedRateState: 'MEASURED',
    recordedAwardRate: '0.5',
    recordedWonCount: 1,
    recordedLostCount: 2,
    recordedDenominator: 3,
    recordedFailedOrRebidCount: 4,
    recordedUndecidedCount: 5,
    reconciledRateState: 'ABSENT_NO_DECIDED_CASES',
    reconciledAwardRate: null,
    reconciledWonCount: 11,
    reconciledLostCount: 12,
    reconciledDenominator: 13,
    reconciledFailedOrRebidCount: 14,
    reconciledUndecidedCount: 15,
    reconciliationBlockers: ['NOT_OPENED_YET'],
    ...overrides,
  };
}

function renderAggregate(overrides: Partial<MyBidAggregate> = {}) {
  return render(<ResultsAggregate aggregate={aggregate(overrides)} />);
}

afterEach(() => {
  cleanup();
});

describe('집계는 두 축을 나란히 낸다 (D-7E3b-2)', () => {
  /** 축 하나를 지우는 변이가 여기서 멈춘다 — 「병기」가 이 slice 의 술어다. */
  it('두 축이 각자의 자리로 선다', () => {
    renderAggregate();

    expect(screen.getByTestId('rate-axis-recorded')).toBeTruthy();
    expect(screen.getByTestId('rate-axis-reconciled')).toBeTruthy();
  });

  it('운영자 기록 축이 recorded* 일곱 칸을 그린다', () => {
    renderAggregate();
    const axis = within(screen.getByTestId('rate-axis-recorded'));

    expect(axis.getByTestId('rate-value-recorded').textContent ?? '').toContain('0.5');
    expect(axis.getByTestId('rate-won-recorded').textContent ?? '').toContain('1');
    expect(axis.getByTestId('rate-lost-recorded').textContent ?? '').toContain('2');
    expect(axis.getByTestId('rate-denominator-recorded').textContent ?? '').toContain('3');
    expect(axis.getByTestId('rate-failed-recorded').textContent ?? '').toContain('4');
    expect(axis.getByTestId('rate-undecided-recorded').textContent ?? '').toContain('5');
  });

  it('대사 축이 reconciled* 일곱 칸을 그린다 — 접두가 섞이지 않는다', () => {
    renderAggregate();
    const axis = within(screen.getByTestId('rate-axis-reconciled'));

    expect(axis.getByTestId('rate-won-reconciled').textContent ?? '').toContain('11');
    expect(axis.getByTestId('rate-lost-reconciled').textContent ?? '').toContain('12');
    expect(axis.getByTestId('rate-denominator-reconciled').textContent ?? '').toContain('13');
    expect(axis.getByTestId('rate-failed-reconciled').textContent ?? '').toContain('14');
    expect(axis.getByTestId('rate-undecided-reconciled').textContent ?? '').toContain('15');
  });

  /**
   * **비율은 서버 문자열 그대로**다(D-7E3b-8). 서버가 뒤 0 을 떼어 `"1"`·`"0.5"` 로 보내므로
   * (실측) 퍼센트로 환산하면 그것은 화면이 계산한 값이다. 분자·분모를 곁에 두어 「1」이 건수로
   * 읽히지 않게 한다.
   */
  it('비율이 서버 문자열 그대로이고 퍼센트로 바뀌지 않는다', () => {
    renderAggregate({ recordedAwardRate: '1', recordedWonCount: 1, recordedDenominator: 1 });
    const axis = screen.getByTestId('rate-axis-recorded');

    expect(within(axis).getByTestId('rate-value-recorded').textContent ?? '').toContain('1');
    expect(axis.textContent ?? '', '퍼센트로 환산했다').not.toMatch(/\d\s*%/u);
    expect(axis.textContent ?? '', '분자·분모가 곁에 없다').toMatch(/1[\s\S]*1/u);
  });
});

describe('분모 0 은 0% 가 아니다', () => {
  /** 비율 **값 노드 자체가 없다** — 문면 대조가 아니라 부재로 잠근다(0 을 지어내는 변이가 RED). */
  it('부재 축에는 비율 값 자리가 없고 상태가 답이다', () => {
    renderAggregate();
    const axis = within(screen.getByTestId('rate-axis-reconciled'));

    expect(axis.queryByTestId('rate-value-reconciled'), '분모 0 인데 비율 값을 그렸다').toBeNull();
    expect(axis.getByTestId('rate-state-reconciled').textContent ?? '').toContain(
      AWARD_RATE_STATE_DISPLAY.ABSENT_NO_DECIDED_CASES.label,
    );
  });

  it('「셀 것이 없다」와 「전부 분모 밖이다」가 다른 문면이다', () => {
    renderAggregate({ recordedRateState: 'ABSENT_NO_RECORDED_BIDS', recordedAwardRate: null });

    const recorded = screen.getByTestId('rate-state-recorded').textContent ?? '';
    const reconciled = screen.getByTestId('rate-state-reconciled').textContent ?? '';
    expect(recorded).toContain(AWARD_RATE_STATE_DISPLAY.ABSENT_NO_RECORDED_BIDS.label);
    expect(recorded).not.toBe(reconciled);
  });

  it('어느 자리에도 0% 가 없다', () => {
    const { container } = renderAggregate({
      recordedRateState: 'ABSENT_NO_RECORDED_BIDS',
      recordedAwardRate: null,
      recordedWonCount: 0,
      recordedLostCount: 0,
      recordedDenominator: 0,
      recordedFailedOrRebidCount: 0,
      recordedUndecidedCount: 0,
    });

    expect(container.textContent ?? '').not.toMatch(/\d\s*%/u);
  });
});

describe('분모 0 의 사유를 말한다 (D-7E3b-7 ⒜)', () => {
  /**
   * 사유는 **서버가 준 전수**다 — 화면이 특정 사유(`WINNER_NAME_NOT_PUBLISHED`)를 전제하지
   * 않는다. 하네스의 답은 `NOT_OPENED_YET` 이고 운영 환경의 답은 다를 수 있다(착수 실측).
   */
  it('사유 전수가 각자의 문면으로 뜬다', () => {
    renderAggregate({ reconciliationBlockers: ['NOT_OPENED_YET', 'WINNER_NAME_NOT_PUBLISHED'] });

    const blockers = within(screen.getByTestId('reconciliation-blockers'));
    expect(blockers.getByTestId('blocker-NOT_OPENED_YET').textContent ?? '').toContain(
      RECONCILIATION_BLOCKER_DISPLAY.NOT_OPENED_YET.label,
    );
    expect(blockers.getByTestId('blocker-WINNER_NAME_NOT_PUBLISHED').textContent ?? '').toContain(
      RECONCILIATION_BLOCKER_DISPLAY.WINNER_NAME_NOT_PUBLISHED.label,
    );
  });

  /** 표본이 0 이면 사유 목록이 비고(실측) 그때는 **상태 자신이** 답이다 — 빈 목록을 그리지 않는다. */
  it('사유가 없으면 사유 자리를 만들지 않는다', () => {
    renderAggregate({
      sampleSize: 0,
      recordedRateState: 'ABSENT_NO_RECORDED_BIDS',
      reconciledRateState: 'ABSENT_NO_RECORDED_BIDS',
      reconciliationBlockers: [],
    });

    expect(screen.queryByTestId('reconciliation-blockers')).toBeNull();
    expect(screen.getByTestId('rate-state-reconciled').textContent ?? '').toContain(
      AWARD_RATE_STATE_DISPLAY.ABSENT_NO_RECORDED_BIDS.label,
    );
  });
});

describe('표본 정의 문면 (D-7E3b-7 ⒟)', () => {
  /**
   * 집계의 표본은 **화면 카드 수가 아니다**(실측: 표본 3 · 카드 2). 그 말이 없으면 운영자가
   * 카드를 세어 검산하다 어긋난다.
   */
  it('표본 수와 그 정의가 함께 뜬다', () => {
    renderAggregate({ sampleSize: 3 });

    expect(screen.getByTestId('sample-size').textContent ?? '').toContain('3');
    expect(screen.getByTestId('sample-note').textContent ?? '').toContain(SAMPLE_NOTE);
  });
});

/** 계약이 주는 칸을 화면이 버리지 않는다 — 칸이 늘면 `satisfies` 와 이 등식이 먼저 틀린다. */
describe('집계의 칸 전수 (D-7E3b-3)', () => {
  const FIELD_PLACEMENT = {
    sampleSize: 'sample-size',
    recordedRateState: 'rate-state-recorded',
    recordedAwardRate: 'rate-value-recorded',
    recordedWonCount: 'rate-won-recorded',
    recordedLostCount: 'rate-lost-recorded',
    recordedDenominator: 'rate-denominator-recorded',
    recordedFailedOrRebidCount: 'rate-failed-recorded',
    recordedUndecidedCount: 'rate-undecided-recorded',
    reconciledRateState: 'rate-state-reconciled',
    reconciledAwardRate: 'rate-value-reconciled',
    reconciledWonCount: 'rate-won-reconciled',
    reconciledLostCount: 'rate-lost-reconciled',
    reconciledDenominator: 'rate-denominator-reconciled',
    reconciledFailedOrRebidCount: 'rate-failed-reconciled',
    reconciledUndecidedCount: 'rate-undecided-reconciled',
    reconciliationBlockers: 'reconciliation-blockers',
  } as const satisfies Record<keyof MyBidAggregate, string>;

  it('표의 키 집합이 계약 칸 전수와 같다', () => {
    expect(Object.keys(FIELD_PLACEMENT).sort()).toStrictEqual(Object.keys(aggregate()).sort());
  });

  /** 두 축이 **둘 다 MEASURED** 인 답에서 재야 비율 값 자리까지 전수가 선다. */
  it('칸마다 자리가 화면에 있다', () => {
    renderAggregate({ reconciledRateState: 'MEASURED', reconciledAwardRate: '0.25' });

    for (const place of new Set(Object.values(FIELD_PLACEMENT))) {
      expect(screen.queryByTestId(place), `${place} 자리가 화면에 없다`).not.toBeNull();
    }
  });
});

import { describe, expect, it } from 'vitest';

import type { OnboardingChecklist } from '../api/queries';

import {
  firstStepWithPending,
  itemRowsOf,
  nextStepWithPending,
  ONBOARDING_ITEM_DISPLAY,
  ONBOARDING_ITEM_ORDER,
  ONBOARDING_ITEM_STEP,
  ONBOARDING_STEPS,
  progressOf,
} from './onboardingDisplay';

function checklist(overrides: Partial<OnboardingChecklist> = {}): OnboardingChecklist {
  return { completed: [], pending: [], awaiting: [], ...overrides };
}

describe('체크리스트 어휘 — 계약 enum 전수', () => {
  /**
   * 순서 상수의 **완전성**은 단계 표에서 빌려 온다: 그 표가 `satisfies Record<OnboardingItem, …>`
   * 라 계약이 항목을 늘리면 **컴파일이** 먼저 멈춘다. 여기서는 두 상수가 같은 어휘를 드는지만 센다
   * — 한쪽만 늘면 목록에서 항목이 조용히 사라진다.
   */
  it('순서 상수가 단계 표의 어휘를 꼭 한 번씩 든다', () => {
    expect([...ONBOARDING_ITEM_ORDER].sort()).toStrictEqual(Object.keys(ONBOARDING_ITEM_STEP).sort());
    expect(new Set(ONBOARDING_ITEM_ORDER).size).toBe(ONBOARDING_ITEM_ORDER.length);
  });

  it('항목마다 문면이 있고 그 단계가 선언된 단계 가운데 하나다', () => {
    const stepIds = new Set(ONBOARDING_STEPS.map((step) => step.id));
    for (const item of ONBOARDING_ITEM_ORDER) {
      expect(ONBOARDING_ITEM_DISPLAY[item], item).toBeDefined();
      expect(stepIds.has(ONBOARDING_ITEM_STEP[item]), item).toBe(true);
    }
  });

  /** 단계는 **쓰기 단위**다(D-7E2b-7) — 빈 단계가 있으면 운영자가 할 일 없는 자리를 걷는다. */
  it('선언된 단계마다 적어도 한 항목이 걸린다', () => {
    for (const step of ONBOARDING_STEPS) {
      const items = ONBOARDING_ITEM_ORDER.filter((item) => ONBOARDING_ITEM_STEP[item] === step.id);
      expect(items.length, step.id).toBeGreaterThan(0);
    }
  });
});

describe('항목의 상태 — 서버의 세 목록과 그 여집합', () => {
  it('세 목록에 없는 항목은 해당 없음이다 — 미완으로 접지 않는다', () => {
    const rows = itemRowsOf(checklist({ completed: ['TRADE_NAME'], pending: ['BUDGET_RANGE'] }));

    const capacity = rows.find((row) => row.item === 'CONSTRUCTION_CAPACITY');
    expect(capacity?.state).toBe('NOT_APPLICABLE');
    expect(rows.filter((row) => row.state === 'PENDING').map((row) => row.item)).toStrictEqual(['BUDGET_RANGE']);
  });

  it('선행 대기는 서버가 준 선행 항목을 함께 든다', () => {
    const rows = itemRowsOf(
      checklist({ awaiting: [{ item: 'CONSTRUCTION_CAPACITY', prerequisite: 'NARA_READINESS' }] }),
    );

    const capacity = rows.find((row) => row.item === 'CONSTRUCTION_CAPACITY');
    expect(capacity?.state).toBe('AWAITING');
    expect(capacity?.prerequisite).toBe('NARA_READINESS');
  });

  it('선행 대기가 아닌 항목의 선행은 null 이다 — 자리를 지어내지 않는다', () => {
    const rows = itemRowsOf(checklist({ completed: ['TRADE_NAME'] }));

    expect(rows.find((row) => row.item === 'TRADE_NAME')?.prerequisite).toBeNull();
  });

  /** 목록이 셋이라 **전체 순서는 순서 상수**가 든다 — 응답에 실린 차례가 아니다. */
  it('행의 순서가 순서 상수 그대로다 — 세 목록이 섞여도', () => {
    const rows = itemRowsOf(
      checklist({
        completed: ['WATCH_CONDITIONS', 'TRADE_NAME'],
        pending: ['BUSINESS_TYPES'],
        awaiting: [{ item: 'CONSTRUCTION_CAPACITY', prerequisite: 'NARA_READINESS' }],
      }),
    );

    expect(rows.map((row) => row.item)).toStrictEqual([...ONBOARDING_ITEM_ORDER]);
  });

  it('항목 전수가 꼭 한 행씩이다', () => {
    const rows = itemRowsOf(checklist({ completed: ['TRADE_NAME'] }));

    expect(rows.length).toBe(ONBOARDING_ITEM_ORDER.length);
  });
});

describe('진행률 — 선행 대기도 해당 없음도 세지 않는다', () => {
  it('분모는 완료 + 미완이다', () => {
    expect(
      progressOf(
        checklist({
          completed: ['TRADE_NAME', 'NARA_READINESS'],
          pending: ['BUSINESS_TYPES'],
          awaiting: [{ item: 'CONSTRUCTION_CAPACITY', prerequisite: 'NARA_READINESS' }],
        }),
      ),
    ).toStrictEqual({ done: 2, total: 3 });
  });

  /**
   * 어휘 크기(8)로 나누면 **해당 없음이 있는 계정은 영원히 100% 가 되지 않는다** — 공사로
   * 입찰하지 않는 운영자에게 끝나지 않는 마법사를 주는 꼴이다.
   */
  it('어휘 크기로 나누지 않는다 — 해당 없음이 있어도 전부 완료가 된다', () => {
    const everyApplicable = ONBOARDING_ITEM_ORDER.filter((item) => item !== 'CONSTRUCTION_CAPACITY');

    expect(progressOf(checklist({ completed: [...everyApplicable] }))).toStrictEqual({
      done: everyApplicable.length,
      total: everyApplicable.length,
    });
  });

  it('빈 계정은 분자가 0 이다', () => {
    expect(progressOf(checklist({ pending: ['TRADE_NAME'] }))).toStrictEqual({ done: 0, total: 1 });
  });
});

describe('다음 단계 — 미완 항목이 있는 첫 단계', () => {
  it('단계 순서에서 앞선 미완이 이긴다', () => {
    expect(firstStepWithPending(checklist({ pending: ['WATCH_CONDITIONS', 'TRADE_NAME'] }))).toBe(
      ONBOARDING_ITEM_STEP.TRADE_NAME,
    );
  });

  it('미완이 없으면 다음 단계가 없다', () => {
    expect(
      firstStepWithPending(
        checklist({
          completed: ['TRADE_NAME'],
          awaiting: [{ item: 'CONSTRUCTION_CAPACITY', prerequisite: 'NARA_READINESS' }],
        }),
      ),
    ).toBeNull();
  });

  /** 선행 대기는 **막지도 채우지도 않는다** — 그것 때문에 단계가 열리면 할 일이 없는 자리다. */
  it('선행 대기만 있는 단계는 다음 단계가 아니다', () => {
    expect(
      firstStepWithPending(checklist({ awaiting: [{ item: 'CONSTRUCTION_CAPACITY', prerequisite: 'NARA_READINESS' }] })),
    ).toBeNull();
  });
});

/**
 * 저장 뒤의 걸음(CR r2 LOW) — **현재 단계 뒤**의 첫 미완이다. 늘 첫 미완으로 가면 운영자가
 * 일부러 뒷단계를 골라 저장했을 때 앞쪽으로 끌려간다.
 */
describe('다음 걸음 — 현재 단계 뒤부터 찾는다', () => {
  it('뒤에 미완이 있으면 그리로 간다 — 앞의 미완을 지나친다', () => {
    expect(
      nextStepWithPending(
        checklist({ pending: ['TRADE_NAME', 'WATCH_CONDITIONS'] }),
        ONBOARDING_ITEM_STEP.BUSINESS_TYPES,
      ),
    ).toBe(ONBOARDING_ITEM_STEP.WATCH_CONDITIONS);
  });

  it('뒤에 미완이 없으면 앞의 첫 미완으로 돌아온다', () => {
    expect(
      nextStepWithPending(checklist({ pending: ['TRADE_NAME'] }), ONBOARDING_ITEM_STEP.WATCH_CONDITIONS),
    ).toBe(ONBOARDING_ITEM_STEP.TRADE_NAME);
  });

  it('기준이 없으면 첫 미완이다 — `firstStepWithPending` 과 같은 자리다', () => {
    const list = checklist({ pending: ['BUDGET_RANGE', 'TRADE_NAME'] });
    expect(nextStepWithPending(list, null)).toBe(firstStepWithPending(list));
  });

  it('미완이 어디에도 없으면 null 이다', () => {
    expect(
      nextStepWithPending(checklist({ completed: ['TRADE_NAME'] }), ONBOARDING_ITEM_STEP.TRADE_NAME),
    ).toBeNull();
  });

  /** 자기 단계에 미완이 남아도 **뒤로** 간다 — 저장했는데 같은 자리에 머무르면 걸음이 아니다. */
  it('기준 단계 자신은 건너뛴다', () => {
    expect(
      nextStepWithPending(checklist({ pending: ['TRADE_NAME', 'BUDGET_RANGE'] }), ONBOARDING_ITEM_STEP.TRADE_NAME),
    ).toBe(ONBOARDING_ITEM_STEP.BUDGET_RANGE);
  });
});

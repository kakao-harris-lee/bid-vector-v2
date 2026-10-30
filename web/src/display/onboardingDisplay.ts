import type { OnboardingChecklist } from '../api/queries';

import type { StateDisplay } from './stateDisplay';

/**
 * 시작하기 체크리스트의 표시(M7/7E-2b) — **판정은 전부 서버의 세 목록에서 온다**(7C④-3).
 * 이 파일이 드는 것은 문면·순서·단계 배정뿐이고, 어느 것도 「걸리는가 · 끝났는가」를 정하지 않는다.
 */

/** 항목 어휘 — **계약 enum 전수**다(세 목록이 같은 어휘를 쓴다). */
export type OnboardingItem = OnboardingChecklist['completed'][number];

/**
 * 항목의 상태 — 서버의 **세 목록과 그 여집합**이다. 넷째 상태를 만들지 않는다: 「해당 없음」은
 * 어느 목록에도 이름이 없다는 사실이고, 그것을 미완으로 접으면 공사로 입찰하지 않는 운영자에게
 * 영원히 끝나지 않는 항목이 보인다.
 */
export type ItemState = 'COMPLETED' | 'PENDING' | 'AWAITING' | 'NOT_APPLICABLE';

export const ITEM_STATE_DISPLAY = {
  COMPLETED: { label: '완료', tone: 'good' },
  PENDING: { label: '아직', tone: 'absent' },
  AWAITING: {
    label: '선행 대기',
    why: '앞 항목의 답이 있어야 이 항목이 걸리는지 정해집니다 — 막지도 채우지도 않습니다',
    tone: 'unjudged',
  },
  NOT_APPLICABLE: { label: '해당 없음', why: '이 계정에는 걸리지 않습니다', tone: 'neutral' },
} as const satisfies Record<ItemState, StateDisplay>;

/** 마법사의 단계 — **쓰기 단위**다(D-7E2b-7). 항목 여덟이 저장 다섯 뒤에 선다. */
export type OnboardingStepId =
  | 'BUSINESS_DETAILS'
  | 'QUALIFICATION_FACTS'
  | 'CONSTRUCTION_CAPACITY'
  | 'BUDGET'
  | 'WATCH';

export interface OnboardingStep {
  readonly id: OnboardingStepId;
  readonly label: string;
  readonly why: string;
}

/**
 * 단계의 순서 — **항목 선언 순서가 아니다**(D-7E2b-7). 선언 순서로 걸으면 같은 절 단위 교체 폼
 * (사업 정보)이 1·2·5번째에 세 번 서고 같은 절을 세 번 덮어쓴다. 체크리스트 **목록**은
 * [ONBOARDING_ITEM_ORDER] 로 선언 순서를 지킨다.
 */
export const ONBOARDING_STEPS = [
  { id: 'BUSINESS_DETAILS', label: '사업 정보', why: '상호 · 본사 소재지 · 나라장터 준비를 한 번에 저장합니다' },
  { id: 'QUALIFICATION_FACTS', label: '자격 사실', why: '업종 · 면허 · 지역어를 한 번에 저장합니다' },
  { id: 'CONSTRUCTION_CAPACITY', label: '시공능력평가액', why: '공사로 입찰하는 계정에만 걸립니다' },
  { id: 'BUDGET', label: '감시 금액대', why: '편집 세션을 지나 바뀝니다' },
  { id: 'WATCH', label: '감시 관심 업종', why: '편집 세션을 지나 바뀝니다' },
] as const satisfies readonly OnboardingStep[];

/**
 * 항목 → 단계 — **표시용 배정이고 판정이 아니다**. `satisfies Record<OnboardingItem, …>` 라
 * 계약이 항목을 늘리면 이 자리가 컴파일에서 멈춘다(새 항목이 조용히 어느 단계에도 안 들어가는
 * 일이 없다).
 */
export const ONBOARDING_ITEM_STEP = {
  TRADE_NAME: 'BUSINESS_DETAILS',
  NARA_READINESS: 'BUSINESS_DETAILS',
  BUSINESS_TYPES: 'QUALIFICATION_FACTS',
  LICENSES: 'QUALIFICATION_FACTS',
  HEADQUARTERS_REGION: 'BUSINESS_DETAILS',
  CONSTRUCTION_CAPACITY: 'CONSTRUCTION_CAPACITY',
  BUDGET_RANGE: 'BUDGET',
  WATCH_CONDITIONS: 'WATCH',
} as const satisfies Record<OnboardingItem, OnboardingStepId>;

export const ONBOARDING_ITEM_DISPLAY = {
  TRADE_NAME: { label: '상호', why: '법인 표기 — 공고문 대사의 운영자 쪽 입력입니다', tone: 'neutral' },
  NARA_READINESS: {
    label: '나라장터 준비',
    why: '등록 여부 · 등록분야 · 인증서 만료일 — 자기 신고입니다',
    tone: 'neutral',
  },
  BUSINESS_TYPES: { label: '업종', why: '자격 판정의 입력입니다', tone: 'neutral' },
  LICENSES: { label: '면허', why: '신고하지 않았다고 답한 것도 답입니다', tone: 'neutral' },
  HEADQUARTERS_REGION: { label: '본사 소재지', why: '지역 자격의 입력입니다 — 판정은 아직 없습니다', tone: 'neutral' },
  CONSTRUCTION_CAPACITY: {
    label: '시공능력평가액',
    why: '공사로 입찰하는 계정에만 걸립니다 — 판정은 아직 없습니다',
    tone: 'neutral',
  },
  BUDGET_RANGE: { label: '감시 금액대', why: '최소 · 최대 가운데 하나 이상', tone: 'neutral' },
  WATCH_CONDITIONS: { label: '감시 관심 업종', why: '분류 코드 완전일치', tone: 'neutral' },
} as const satisfies Record<OnboardingItem, StateDisplay>;

/**
 * 항목의 **선언 순서** — 서버가 목록마다 선언 순서를 지키지만 목록이 셋이라 전체 순서는 여기
 * 한 자리에 있다. 어휘의 완전성은 [ONBOARDING_ITEM_STEP] 이 컴파일로 들고, **순서 자체**는
 * 생성 타입에서 읽는 게이트(`scripts/onboarding-vocabulary.test.mjs`)가 맞댄다.
 */
export const ONBOARDING_ITEM_ORDER = [
  'TRADE_NAME',
  'NARA_READINESS',
  'BUSINESS_TYPES',
  'LICENSES',
  'HEADQUARTERS_REGION',
  'CONSTRUCTION_CAPACITY',
  'BUDGET_RANGE',
  'WATCH_CONDITIONS',
] as const satisfies readonly OnboardingItem[];

export interface ItemRow {
  readonly item: OnboardingItem;
  readonly state: ItemState;
  /** `AWAITING` 일 때 **서버가 준** 선행 항목 — 그 밖에는 `null` 이다(짝을 지어내지 않는다). */
  readonly prerequisite: OnboardingItem | null;
}

/** 항목 전수를 선언 순서로 — 상태는 **세 목록의 멤버십**이고 그 밖은 여집합이다. */
export function itemRowsOf(checklist: OnboardingChecklist): readonly ItemRow[] {
  const completed = new Set<string>(checklist.completed);
  const pending = new Set<string>(checklist.pending);
  const awaiting = new Map<string, OnboardingItem>(
    checklist.awaiting.map((pair) => [pair.item, pair.prerequisite]),
  );

  return ONBOARDING_ITEM_ORDER.map((item) => {
    const prerequisite = awaiting.get(item);
    if (prerequisite !== undefined) {
      return { item, state: 'AWAITING', prerequisite };
    }
    if (completed.has(item)) {
      return { item, state: 'COMPLETED', prerequisite: null };
    }
    return { item, state: pending.has(item) ? 'PENDING' : 'NOT_APPLICABLE', prerequisite: null };
  });
}

/**
 * 진행률 — 분모는 **걸리는 항목**(완료 + 미완)뿐이다. 어휘 크기로 나누면 「해당 없음」이 있는
 * 계정은 100% 가 되지 않고, `awaiting` 을 분모에 넣으면 선행이 풀리기 전까지 막힌 것처럼 보인다
 * (D-7C4-2 — 막지도 채우지도 않는다).
 */
export function progressOf(checklist: OnboardingChecklist): { readonly done: number; readonly total: number } {
  return { done: checklist.completed.length, total: checklist.completed.length + checklist.pending.length };
}

/**
 * [after] **뒤의** 첫 미완 단계 — 뒤에 없으면 앞으로 한 바퀴 돌아 찾는다. 미완이 어디에도 없으면
 * `null` 이다(선행 대기만 남은 단계는 걸을 자리가 아니다).
 *
 * 「뒤부터」인 이유(CR r2 LOW): 저장 뒤에 **늘 첫 미완**으로 가면 운영자가 일부러 뒷단계를 골라
 * 저장했을 때 앞쪽으로 끌려간다. 마법사의 걸음은 앞으로 가는 것이고, 뒤에 미완이 남지 않았을
 * 때에만 앞의 미완으로 되돌아온다.
 */
export function nextStepWithPending(
  checklist: OnboardingChecklist,
  after: OnboardingStepId | null,
): OnboardingStepId | null {
  const pendingSteps = new Set<OnboardingStepId>(
    checklist.pending.map((item) => ONBOARDING_ITEM_STEP[item]),
  );
  const order = ONBOARDING_STEPS.map((step) => step.id);
  const start = after === null ? 0 : order.indexOf(after) + 1;
  for (let step = 0; step < order.length; step += 1) {
    const id = order[(start + step) % order.length];
    if (id !== undefined && pendingSteps.has(id)) {
      return id;
    }
  }
  return null;
}

/** 미완 항목이 있는 **첫 단계** — 마법사가 아직 아무 데도 머물지 않았을 때의 자리다. */
export function firstStepWithPending(checklist: OnboardingChecklist): OnboardingStepId | null {
  return nextStepWithPending(checklist, null);
}

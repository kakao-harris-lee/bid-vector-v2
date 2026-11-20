import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState, type ReactNode } from 'react';
import { Link } from 'react-router';

import {
  listSuggestions,
  readOnboardingChecklist,
  readProfile,
  readStrategy,
  type OnboardingChecklist,
  type Profile,
  type StrategyReadResponse,
  type SuggestedNotice,
} from '../api/queries';
import { PROFILE_QUERY_KEY } from '../api/queryKeys';
import { EmptyFace, ErrorFace, Skeleton } from '../components/ScreenFaces';
import { controlClass, LinkButton } from '../components/Button';
import { cardClass } from '../components/Card';
import { PageHeader } from '../components/PageHeader';
import { StateBadge } from '../components/StateBadge';
import { StatusPill } from '../components/StatusPill';
import { TwoColumn } from '../components/TwoColumn';
import { isUnauthorized, SignedOutFace } from '../components/WriteFailure';
import type { StrategyField } from '../display/editSessionDisplay';
import { compactAmountTextOf } from '../display/moneyDisplay';
import { SUGGESTIONS_RESULT_STATE_DISPLAY } from '../display/noticeDisplay';
import { displayOrUnknown } from '../display/stateDisplay';
import { dayCountText, daysUntil, readClock } from '../display/timeDisplay';
import {
  nextStepWithPending,
  ITEM_STATE_DISPLAY,
  itemRowsOf,
  ONBOARDING_ITEM_DISPLAY,
  ONBOARDING_ITEM_STEP,
  ONBOARDING_STEPS,
  progressOf,
  type ItemRow,
  type ItemState,
  type OnboardingStepId,
} from '../display/onboardingDisplay';
import { useSessionCsrf } from '../session/useSessionCsrf';

import { worksheetPathOf } from './NoticeCard';
import { ProfileCapacityCard } from './ProfileCapacityCard';
import { ProfileDetailsCard } from './ProfileDetailsCard';
import { ProfileFactsCard } from './ProfileFactsCard';
import {
  STRATEGY_QUERY_KEY,
  StrategyEditPanel,
  StrategyFieldRow,
  StrategyWriteFailure,
  useStrategyEditSession,
} from './strategyEdit';

export const ONBOARDING_QUERY_KEY = ['onboarding-checklist'] as const;

/** 잠긴 단계 버튼이 가리키는 사유 문단의 id — 버튼과 문면이 같은 자리를 쓴다. */
const STEP_LOCK_REASON_ID = 'onboarding-step-lock-reason';

/** 단계 본문의 id — 단계 버튼의 `aria-controls` 가 가리키는 **한 자리**다. */
const STEP_BODY_ID = 'onboarding-step-body';

/**
 * 절 하나 — **와이어프레임 A 의 번호 절 다섯**(M14/14B · 팀장 판정 ①).
 *
 * 신원은 와이어프레임이 정하고 **쓰기 단위는 바뀌지 않는다**: 절이 담는 것은 기존 단계이고,
 * 자격·관심 조건은 와이어프레임이 「한 곳에서」라고 적은 그대로 네 단계를 한 절에 묶는다.
 *
 * `steps` 가 비면 **준비 중**이다 — 그 절은 **상태 문면만** 보인다(입력칸·버튼을 만들지
 * 않는다, D-7E2b-3). 「언젠가 된다」는 약속을 그리지 않는 것이 요점이고, `why` 가 **왜 지금
 * 없는지**를 말한다.
 */
interface OnboardingSection {
  readonly id: string;
  readonly label: string;
  /** 이 절이 담는 쓰기 단계. 비면 준비 중이다. */
  readonly steps: readonly OnboardingStepId[];
  readonly why: string;
}

const ONBOARDING_SECTIONS = [
  {
    id: 'nts',
    label: '사업자번호 확인',
    steps: [],
    why: '저장할 자리가 아직 없어 입력을 받지 않습니다',
  },
  {
    id: 'nara',
    label: '나라장터 준비',
    steps: ['BUSINESS_DETAILS'],
    why: '앱이 대신할 수 없는 것입니다 — 상호 · 본사 소재지와 함께 한 번에 저장합니다',
  },
  {
    id: 'qualification',
    label: '자격과 관심 조건',
    steps: ['QUALIFICATION_FACTS', 'CONSTRUCTION_CAPACITY', 'BUDGET', 'WATCH'],
    why: '업종 · 면허 · 지역어와 감시 금액대 · 관심 업종을 한 곳에서 적습니다',
  },
  {
    id: 'channels',
    label: '알림 받는 곳',
    steps: [],
    why: '보내는 방법을 정한 뒤에 엽니다',
  },
  {
    id: 'preview',
    label: '지금 맞는 공고 미리보기',
    steps: [],
    why: '조건을 채우면 곁의 미리보기에 뜹니다 — 어느 입력이 결과를 좁혔는지 알려 주는 기능은 아직 없습니다',
  },
] as const satisfies readonly OnboardingSection[];

/** 준비 중인 절 — `steps` 가 비어 있다는 **구조**가 그 사실이다(따로 선언하지 않는다). */
const isNotReady = (section: OnboardingSection): boolean => section.steps.length === 0;

/** 준비 중 절의 알약 — 값이 없다는 뜻이 아니라 **그 기능이 아직 없다**는 뜻이다. */
const NOT_READY_PILL = { tone: 'unjudged', label: '준비 중' } as const;

/**
 * 절의 상태를 접는 **약함의 차례**. 하나라도 미완이면 그 절은 미완이고, 미완이 없어도 선행
 * 대기가 있으면 아직 정해지지 않았다. 전부 「해당 없음」이면 그 절은 이 계정에 걸리지 않는다.
 */
const SECTION_STATE_ORDER = [
  'PENDING',
  'AWAITING',
  'COMPLETED',
  'NOT_APPLICABLE',
] as const satisfies readonly ItemState[];

/**
 * 절의 상태 알약 — **그 절이 담은 항목들의 가장 약한 상태**다(새 어휘를 세우지 않는다).
 *
 * 판정은 전부 서버의 세 목록에서 오고(7C④-3), 이 함수가 하는 일은 **여럿을 하나로 접는 것**
 * 뿐이다. 항목이 **하나도 없으면** 접을 것이 없다(`null`) — 준비 중인 절이 그 꼴이고, 그때
 * 알약은 항목 상태가 아니라 [NOT_READY_PILL] 이다.
 */
function sectionStateOf(rows: readonly ItemRow[]): ItemState | null {
  const states = new Set(rows.map((row) => row.state));
  return SECTION_STATE_ORDER.find((state) => states.has(state)) ?? null;
}

/** 단계가 쓰는 전략 칸 — 체크리스트 항목 하나가 이 칸들 뒤에 선다. */
const STEP_STRATEGY_FIELDS: Readonly<Record<'BUDGET' | 'WATCH', readonly StrategyField[]>> = {
  BUDGET: ['MIN_BUDGET', 'MAX_BUDGET'],
  WATCH: ['FOCUS_CATEGORY'],
};

/**
 * 레일 미리보기가 보이는 줄 수 — **곁다리의 크기**이고 질의 인자가 아니다(제안 operation 에는
 * 쪽·정렬 인자가 없다 · 승인 경계). 더 있으면 그 사실을 말하고 오늘의 공고로 보낸다.
 */
const PREVIEW_ROWS = 3;

/**
 * 화면 A 시작하기 — **항목의 진행 상태는 서버가 판정한다**(7C④-3 · D-7E2b-1). 이 화면은 세
 * 목록을 그리고, 「해당 없음」은 그 여집합으로 안다. 프로필·전략 값에서 완료를 추론하는 코드가
 * 없다 — 폼이 그 값을 **출발값으로** 쓸 뿐이고, 쓰기 뒤에는 체크리스트를 **다시 읽는다**
 * (낙관적 완료 표시 0).
 */
export function OnboardingScreen() {
  const { signedIn } = useSessionCsrf();
  const checklist = useQuery({ queryKey: ONBOARDING_QUERY_KEY, queryFn: readOnboardingChecklist });

  if (signedIn === false || isUnauthorized(checklist.error)) {
    return (
      <section data-testid="screen-onboarding" className="mx-auto max-w-5xl p-4">
        <SignedOutFace />
      </section>
    );
  }
  if (checklist.isPending) {
    return (
      <section data-testid="screen-onboarding" className="mx-auto max-w-5xl p-4">
        <Skeleton testId="onboarding-loading" />
      </section>
    );
  }
  if (checklist.isError) {
    // **빈 체크리스트로 접지 않는다** — 그러면 미완 0 이 되어 「전부 끝났다」로 보인다.
    return (
      <section data-testid="screen-onboarding" className="mx-auto max-w-5xl p-4">
        <ErrorFace testId="onboarding-error" onRetry={() => void checklist.refetch()} />
      </section>
    );
  }

  return <OnboardingWizard checklist={checklist.data} />;
}

function OnboardingWizard({ checklist }: { readonly checklist: OnboardingChecklist }) {
  const queryClient = useQueryClient();
  /** 운영자가 고른 단계. `null` 이면 **서버가 말하는 첫 미완 단계**를 따라간다. */
  const [picked, setPicked] = useState<OnboardingStepId | null>(null);
  /** 전략 단계에 **진행 중인 편집 세션**이 있는가 — 있으면 단계를 옮기지 않는다(아래 잠금의 사유). */
  const [sessionOpen, setSessionOpen] = useState(false);
  /** 마지막으로 저장한 단계 — 다음 걸음은 **그 뒤의** 첫 미완이다(CR r2 LOW). */
  const [advanceFrom, setAdvanceFrom] = useState<OnboardingStepId | null>(null);
  const profile = useQuery({ queryKey: PROFILE_QUERY_KEY, queryFn: readProfile });

  const rows = itemRowsOf(checklist);
  const progress = progressOf(checklist);
  const current = picked ?? nextStepWithPending(checklist, advanceFrom) ?? ONBOARDING_STEPS[0].id;
  /**
   * 쓰기가 끝나면 **서버에 다시 묻는다** — 쓰기의 200 을 완료로 세지 않는다.
   *
   * 고른 단계를 **놓고**(CR r1 LOW) 그 자리를 다음 걸음의 **기준**으로 적는다(CR r2 LOW).
   * 앞 판은 `picked` 가 한 번 세워지면 지워지지 않아 저장해도 그 자리에 머물렀고, 그 다음 판은
   * 늘 **첫 미완**으로 가서 뒷단계에서 저장하면 앞쪽으로 끌려갔다. 지금은 **현재 단계 뒤의 첫
   * 미완**으로 가고, 뒤에 없으면 앞의 첫 미완으로 돌아온다.
   */
  const reread = () => {
    setAdvanceFrom(current);
    setPicked(null);
    void queryClient.invalidateQueries({ queryKey: ONBOARDING_QUERY_KEY });
  };

  const stepBody = (
    <div id={STEP_BODY_ID} data-testid="onboarding-step-body" className="mt-3 flex flex-col gap-4">
      {profile.isPending ? <Skeleton testId="onboarding-profile-loading" /> : null}
      {/* 프로필만 401 이면 **말 없이 비지 않는다**(CR r1 LOW) — 체크리스트는 읽혔으므로
          목록·진행률은 그대로 두고 이 자리에만 로그인 길을 낸다. */}
      {profile.isError && isUnauthorized(profile.error) ? <SignedOutFace /> : null}
      {profile.isError && !isUnauthorized(profile.error) ? (
        <ErrorFace testId="onboarding-profile-error" onRetry={() => void profile.refetch()} />
      ) : null}
      {profile.isSuccess ? (
        <StepBody step={current} profile={profile.data} onSaved={reread} onSessionOpen={setSessionOpen} />
      ) : null}
    </div>
  );

  return (
    <section data-testid="screen-onboarding" className="mx-auto flex max-w-5xl flex-col gap-6 p-4">
      <PageHeader
        testId="onboarding-header"
        title="시작하기"
        // 저장 뒤 이 수가 바뀐다 — 보조기술이 그 변화를 읽게 한다(§6.5 · CR r1 LOW).
        summary={
          <span data-testid="onboarding-progress" aria-live="polite">
            <span className="font-numeric">
              {progress.done} / {progress.total}
            </span>{' '}
            완료
          </span>
        }
      />

      <TwoColumn
        testId="onboarding-layout"
        body={
          <>
            {checklist.pending.length === 0 ? (
              /* **다음 행동 하나**(§6.5 · D-7E2b-4) — 미완이 0 이면 오늘의 공고로 간다. */
              <div data-testid="onboarding-complete" className="rounded-lg border border-accent/40 bg-card p-4">
                <p className="text-sm font-medium">걸리는 항목을 모두 채웠습니다.</p>
                <div className="mt-3">
                  <LinkButton to="/" tone="primary">
                    오늘의 공고 보기
                  </LinkButton>
                </div>
              </div>
            ) : null}

            {/*
              절은 **번호가 붙은 차례**다(와이어프레임 A). `ol` 이라 그 차례가 보조기기에도 서고,
              번호는 선언 순서에서 나온다 — 손으로 적으면 절을 끼울 때 둘이 갈린다.
            */}
            <ol data-testid="onboarding-items" className="flex flex-col gap-3">
              {ONBOARDING_SECTIONS.map((section, at) => (
                <SectionCard
                  key={section.id}
                  section={section}
                  ordinal={at + 1}
                  rows={rows.filter((row) =>
                    section.steps.some((step) => ONBOARDING_ITEM_STEP[row.item] === step),
                  )}
                  current={current}
                  sessionOpen={sessionOpen}
                  stepBody={stepBody}
                  onPick={setPicked}
                />
              ))}
            </ol>

            {/*
              live region 은 **비어 있는 채 먼저 서 있어야** 변화가 확실히 읽힌다(CR r3 L-6) — 내용째
              삽입되는 노드의 낭독은 스크린리더마다 갈린다. 그래서 문단은 늘 그리고 **글만 바꾼다**.

              편집 세션이 **진행 중인 동안**에는 단계를 옮기지 않는다(v r1 L-1). 옮기면 전략 단계가
              언마운트되어 **보낸 값이 적용되지도 취소되지도 않은 세션**이 서버에 남는다. 술어가
              「열린」이 아니라 「진행 중」인 이유(CR·v r2 MEDIUM): 서버가 이미 끝낸 세션에서는 그 뒤
              모든 명령이 409 라 `open` 이 비워지지 않아 **마법사가 갇혔다**.
            */}
            <p
              id={STEP_LOCK_REASON_ID}
              data-testid="onboarding-step-lock"
              role="status"
              className="text-xs text-ink-soft"
            >
              {sessionOpen ? '편집을 마치거나 그만두면 단계를 옮길 수 있습니다.' : ''}
            </p>
          </>
        }
        rail={<SuggestionPreview />}
      />
    </section>
  );
}

/**
 * 절 한 장 — 번호 · 제목 · 상태 알약 · 사유 · (쓰기 절이면) 항목과 단계 버튼.
 *
 * **준비 중 절은 여기서 일찍 끝난다**: 상태 문면만 그리고 입력칸도 버튼도 만들지 않는다. 그
 * 사실이 `steps` 가 비었다는 **구조**에서 나오므로, 절을 더하는 사람이 따로 선언할 것이 없다.
 */
function SectionCard({
  section,
  ordinal,
  rows,
  current,
  sessionOpen,
  stepBody,
  onPick,
}: {
  readonly section: OnboardingSection;
  readonly ordinal: number;
  readonly rows: readonly ItemRow[];
  readonly current: OnboardingStepId;
  readonly sessionOpen: boolean;
  readonly stepBody: ReactNode;
  readonly onPick: (step: OnboardingStepId) => void;
}) {
  const state = sectionStateOf(rows);
  const holdsCurrent = section.steps.some((step) => step === current);
  return (
    <li data-testid={`onboarding-section-${section.id}`} className={cardClass('sm')}>
      <div className="flex flex-wrap items-center gap-2">
        <span className="font-numeric text-sm text-ink-soft">{ordinal}.</span>
        <span className="text-sm font-medium">{section.label}</span>
        {state === null ? (
          <StatusPill
            tone={NOT_READY_PILL.tone}
            label={NOT_READY_PILL.label}
            testId={`onboarding-section-state-${section.id}`}
          />
        ) : (
          <StateBadge display={ITEM_STATE_DISPLAY[state]} testId={`onboarding-section-state-${section.id}`} />
        )}
      </div>
      <p className="mt-1 text-xs text-ink-soft">{section.why}</p>

      {isNotReady(section) ? null : (
        <>
          <ul className="mt-2 flex flex-col gap-1">
            {rows.map((row) => (
              <li key={row.item} className="flex flex-wrap items-center gap-2">
                <span className="text-xs">{ONBOARDING_ITEM_DISPLAY[row.item].label}</span>
                <StateBadge display={ITEM_STATE_DISPLAY[row.state]} testId={`onboarding-state-${row.item}`} />
                {row.prerequisite === null ? null : (
                  <span data-testid={`onboarding-prerequisite-${row.item}`} className="text-xs text-ink-soft">
                    선행: {ONBOARDING_ITEM_DISPLAY[row.prerequisite].label}
                  </span>
                )}
              </li>
            ))}
          </ul>
          <div className="mt-2 flex flex-wrap gap-2">
            {ONBOARDING_STEPS.filter((step) => section.steps.some((one) => one === step.id)).map((step) => (
              <button
                key={step.id}
                type="button"
                data-testid={`onboarding-step-${step.id}`}
                aria-current={step.id === current ? 'step' : undefined}
                aria-controls={STEP_BODY_ID}
                // **`disabled` 가 아니라 `aria-disabled`**(CR r3 L-6): `disabled` 버튼은 포커스를 받지
                // 않아 잠금 사유를 가리키는 `aria-describedby` 가 **낭독될 기회가 없다**. 포커스를
                // 남기고 누름만 무시하면 키보드·스크린리더 운영자가 그 사유를 듣는다.
                aria-disabled={sessionOpen || undefined}
                aria-describedby={sessionOpen ? STEP_LOCK_REASON_ID : undefined}
                onClick={() => {
                  if (sessionOpen) {
                    return;
                  }
                  onPick(step.id);
                }}
                // 단계 버튼은 자기 ARIA 배선(현재 단계·잠금 사유)을 쥔다 — 하한만 공유한다.
                className={`inline-flex min-h-11 items-center rounded-md px-3 text-sm aria-disabled:opacity-50 ${
                  step.id === current
                    ? 'bg-neutral-skin font-medium text-neutral-ink'
                    : 'text-ink-soft hover:bg-neutral-skin'
                }`}
              >
                {step.label}
              </button>
            ))}
          </div>
          {/* 단계 본문은 **그 단계를 담은 절 안**에 선다 — 멀리 두면 누른 자리와 열린 자리가
              갈린다. id 는 한 자리이므로 `aria-controls` 의 대상도 하나다. */}
          {holdsCurrent ? stepBody : null}
        </>
      )}
    </li>
  );
}

/**
 * 레일의 **지금 맞는 공고 미리보기**(와이어프레임 A 의 오른쪽). 기존 제안 질의를 그대로
 * 쓴다 — 새 operation 도 새 인자도 없다(승인 경계).
 *
 * 비는 이유는 **서버가 주는 만큼만** 말한다(D-7E-9 의 상태 표). 화면이 「조건이 좁다」를
 * 추론하지 않는다 — 어느 입력이 좁혔는지는 아직 아무도 모르고, 그 사실이 절 5 의 사유다.
 */
function SuggestionPreview() {
  const suggestions = useQuery({ queryKey: ['suggestions'], queryFn: listSuggestions });
  // **한 번 읽고 내린다**(M9 D-1) — 줄마다 다시 읽으면 같은 목록의 두 줄이 서로 다른 「지금」으로
  // 남은 날을 센다.
  const [now] = useState(readClock);

  return (
    <div data-testid="onboarding-preview" className={cardClass('sm')}>
      <h3 className="text-sm font-semibold">지금 맞는 공고 미리보기</h3>
      {suggestions.isPending ? <Skeleton testId="onboarding-preview-loading" rows={2} /> : null}
      {suggestions.isError ? (
        <ErrorFace testId="onboarding-preview-error" onRetry={() => void suggestions.refetch()} />
      ) : null}
      {suggestions.data === undefined ? null : suggestions.data.rows.length === 0 ? (
        <EmptyFace
          testId="onboarding-preview-empty"
          display={displayOrUnknown(SUGGESTIONS_RESULT_STATE_DISPLAY, suggestions.data.resultState)}
        />
      ) : (
        <>
          <ul className="mt-2 flex flex-col gap-2">
            {suggestions.data.rows.slice(0, PREVIEW_ROWS).map((row) => (
              <PreviewRow key={`${row.noticeNumber}/${row.noticeRound}`} suggestion={row} now={now} />
            ))}
          </ul>
          {/* 곁다리가 **전부가 아니라는 사실**을 말한다 — 몇 장만 보이고 끝나면 운영자가 그것을
              전수로 읽는다. 전체는 오늘의 공고가 든다(그 화면이 상한 초과까지 공시한다). */}
          {suggestions.data.rows.length > PREVIEW_ROWS ? (
            <p data-testid="onboarding-preview-more" className="mt-2 text-xs text-ink-soft">
              통과 <span className="font-numeric">{suggestions.data.total}</span>건 가운데{' '}
              <span className="font-numeric">{PREVIEW_ROWS}</span>건입니다 —{' '}
              <Link to="/" className="underline">
                오늘의 공고
              </Link>
              에서 전부 봅니다.
            </p>
          ) : null}
        </>
      )}
    </div>
  );
}

/** 미리보기 한 줄 — 제목 · 압축 금액 · D-day. 카드의 축약이라 판정 배지를 싣지 않는다. */
function PreviewRow({ suggestion, now }: { readonly suggestion: SuggestedNotice; readonly now: Date }) {
  const won = suggestion.baseAmountWon;
  const compact = won === null || won === undefined ? null : compactAmountTextOf(won, suggestion.baseAmountCurrency);
  const days = dayCountText(daysUntil(suggestion.deadlineAt, now));
  return (
    <li className={cardClass('sm')}>
      <Link
        to={worksheetPathOf(suggestion)}
        data-testid={`onboarding-preview-${suggestion.noticeNumber}`}
        className="flex min-h-11 items-center rounded-md text-sm font-medium break-words hover:underline"
      >
        {suggestion.title ?? suggestion.noticeNumber}
      </Link>
      {/* **없는 축은 빠진다**(빈 자리를 만들지 않는다) — 곁다리에 부재 문면을 늘어놓으면 그
          자리가 본문보다 시끄러워진다. 값이 궁금하면 제목을 눌러 워크시트에서 본다. */}
      <p className="font-numeric text-xs text-ink-soft">
        {[compact, days].filter((one): one is string => one !== null).join(' · ')}
      </p>
    </li>
  );
}

/**
 * 단계 하나의 폼 — **설정 화면과 같은 컴포넌트**다(D-7E2b-8). 단계가 늘면 이 `switch` 가
 * 소진되지 않아 컴파일이 막는다.
 */
function StepBody({
  step,
  profile,
  onSaved,
  onSessionOpen,
}: {
  readonly step: OnboardingStepId;
  readonly profile: Profile;
  readonly onSaved: () => void;
  readonly onSessionOpen: (open: boolean) => void;
}) {
  switch (step) {
    case 'BUSINESS_DETAILS':
      return <ProfileDetailsCard profile={profile} onSaved={onSaved} />;
    case 'QUALIFICATION_FACTS':
      return <ProfileFactsCard profile={profile} onSaved={onSaved} />;
    case 'CONSTRUCTION_CAPACITY':
      return <ProfileCapacityCard onSaved={onSaved} />;
    case 'BUDGET':
    case 'WATCH':
      return (
        <OnboardingStrategyStep
          fields={STEP_STRATEGY_FIELDS[step]}
          onSaved={onSaved}
          onSessionOpen={onSessionOpen}
        />
      );
    default: {
      const unreachable: never = step;
      throw new Error(`모르는 단계: ${String(unreachable)}`);
    }
  }
}

/** 전략 단계 — 쓰기는 **편집 세션 하나**를 지난다(계약에 전략을 직접 쓰는 operation 이 없다). */
function OnboardingStrategyStep({
  fields,
  onSaved,
  onSessionOpen,
}: {
  readonly fields: readonly StrategyField[];
  readonly onSaved: () => void;
  readonly onSessionOpen: (open: boolean) => void;
}) {
  const strategy = useQuery({ queryKey: STRATEGY_QUERY_KEY, queryFn: readStrategy });

  if (strategy.isPending) {
    return <Skeleton testId="onboarding-strategy-loading" />;
  }
  if (strategy.isError) {
    return isUnauthorized(strategy.error) ? <SignedOutFace /> : (
      <ErrorFace testId="onboarding-strategy-error" onRetry={() => void strategy.refetch()} />
    );
  }

  return (
    <OnboardingStrategyFields
      strategy={strategy.data}
      fields={fields}
      onSaved={onSaved}
      onSessionOpen={onSessionOpen}
    />
  );
}

function OnboardingStrategyFields({
  strategy,
  fields,
  onSaved,
  onSessionOpen,
}: {
  readonly strategy: StrategyReadResponse;
  readonly fields: readonly StrategyField[];
  readonly onSaved: () => void;
  readonly onSessionOpen: (open: boolean) => void;
}) {
  const session = useStrategyEditSession(strategy, onSaved);

  /**
   * 열린 세션을 **위로 알린다** — 마법사가 그동안 단계 탐색을 닫는다(v r1 L-1). 언마운트에서
   * 반드시 `false` 를 보내 「세션이 열린 채 사라진 단계」가 탐색을 영원히 막지 않게 한다.
   */
  const { inProgress } = session;
  useEffect(() => {
    onSessionOpen(inProgress);
    return () => onSessionOpen(false);
  }, [inProgress, onSessionOpen]);

  return (
    <div className="flex flex-col gap-3">
      <StrategyWriteFailure session={session} />
      <StrategyEditPanel session={session} strategy={strategy} />
      <dl className="grid gap-3 sm:grid-cols-2">
        {fields.map((field) => (
          <StrategyFieldRow
            key={field}
            field={field}
            strategy={strategy}
            disabled={session.inProgress}
            onBegin={() => session.begin(field)}
          />
        ))}
      </dl>
    </div>
  );
}

/**
 * 셸·홈에서 A 로 가는 **진입 하나**(D-7E2b-4) — 서버의 `pending` 이 비어 있지 않을 때만 뜬다.
 * 체크리스트를 읽지 못했거나 아직 못 읽었으면 **아무것도 그리지 않는다**(없는 사실을 지어내지
 * 않는다).
 */
export function OnboardingBanner() {
  const checklist = useQuery({ queryKey: ONBOARDING_QUERY_KEY, queryFn: readOnboardingChecklist });
  const pending = checklist.data?.pending.length ?? 0;

  if (pending === 0) {
    return null;
  }
  return (
    <div data-testid="onboarding-banner" className={cardClass('sm')}>
      <p className="text-sm">
        시작하기에서 아직 채우지 않은 항목이 <span className="font-numeric">{pending}</span>개 있습니다.
      </p>
      <Link to="/start" className={`mt-2 ${controlClass('secondary', 'md')}`}>
        시작하기로
      </Link>
    </div>
  );
}

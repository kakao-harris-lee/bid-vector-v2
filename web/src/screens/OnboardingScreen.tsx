import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { Link } from 'react-router';

import {
  readOnboardingChecklist,
  readProfile,
  readStrategy,
  type OnboardingChecklist,
  type Profile,
  type StrategyReadResponse,
} from '../api/queries';
import { ErrorFace, Skeleton } from '../components/ScreenFaces';
import { StateBadge } from '../components/StateBadge';
import { isUnauthorized, SignedOutFace } from '../components/WriteFailure';
import type { StrategyField } from '../display/editSessionDisplay';
import {
  nextStepWithPending,
  ITEM_STATE_DISPLAY,
  itemRowsOf,
  ONBOARDING_ITEM_DISPLAY,
  ONBOARDING_STEPS,
  progressOf,
  type OnboardingStepId,
} from '../display/onboardingDisplay';
import { useSessionCsrf } from '../session/useSessionCsrf';

import { ProfileCapacityCard } from './ProfileCapacityCard';
import { ProfileDetailsCard } from './ProfileDetailsCard';
import { ProfileFactsCard } from './ProfileFactsCard';
import { PROFILE_QUERY_KEY } from './profileWrites';
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

/**
 * 범위 밖 자리 — **빈 자리 + 「준비 중」뿐이다**(D-7E2b-3). 입력칸·버튼을 만들지 않는다.
 * 사업자번호는 계약에 칸이 0 이고(D-7E2-7), 알림 채널은 발송 경로 결정 뒤이며(R-4), 첫 공고
 * 미리보기의 「어느 입력이 좁혔는가」는 `OPEN-6A3-EVALUATION-DETAIL` 이다.
 */
const NOT_READY = [
  { id: 'nts', label: '사업자번호·국세청 확인', why: '계약에 그 칸이 없습니다(자기 신고 입력도 두지 않습니다)' },
  { id: 'channels', label: '알림 채널(텔레그램·메일)', why: '발송 경로 결정 뒤입니다' },
  { id: 'preview', label: '첫 공고 미리보기와 「어느 입력이 좁혔는가」', why: '좁힌 사유를 내는 API 가 아직 없습니다' },
] as const;

/** 단계가 쓰는 전략 칸 — 체크리스트 항목 하나가 이 칸들 뒤에 선다. */
const STEP_STRATEGY_FIELDS: Readonly<Record<'BUDGET' | 'WATCH', readonly StrategyField[]>> = {
  BUDGET: ['MIN_BUDGET', 'MAX_BUDGET'],
  WATCH: ['FOCUS_CATEGORY'],
};

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
      <section data-testid="screen-onboarding" className="mx-auto max-w-3xl p-4">
        <SignedOutFace />
      </section>
    );
  }
  if (checklist.isPending) {
    return (
      <section data-testid="screen-onboarding" className="mx-auto max-w-3xl p-4">
        <Skeleton testId="onboarding-loading" />
      </section>
    );
  }
  if (checklist.isError) {
    // **빈 체크리스트로 접지 않는다** — 그러면 미완 0 이 되어 「전부 끝났다」로 보인다.
    return (
      <section data-testid="screen-onboarding" className="mx-auto max-w-3xl p-4">
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
  /** 전략 단계에 **진행 중인 편집 세션**이 있는가 — 있으면 단계를 옮기지 않는다(아래 `nav` 의 사유). */
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

  return (
    <section data-testid="screen-onboarding" className="mx-auto flex max-w-3xl flex-col gap-6 p-4">
      <header className="flex flex-wrap items-baseline gap-3">
        <h2 className="text-lg font-semibold">시작하기</h2>
        {/* 저장 뒤 이 수가 바뀐다 — 보조기술이 그 변화를 읽게 한다(§6.5 · CR r1 LOW). */}
        <p data-testid="onboarding-progress" aria-live="polite" className="text-sm text-ink-soft">
          <span className="font-numeric">
            {progress.done} / {progress.total}
          </span>{' '}
          완료
        </p>
      </header>

      <ol data-testid="onboarding-items" className="flex flex-col gap-2">
        {rows.map((row) => (
          <li
            key={row.item}
            data-testid={`onboarding-item-${row.item}`}
            className="rounded-md border border-line p-3"
          >
            <div className="flex flex-wrap items-center gap-2">
              <span className="text-sm font-medium">{ONBOARDING_ITEM_DISPLAY[row.item].label}</span>
              <StateBadge display={ITEM_STATE_DISPLAY[row.state]} testId={`onboarding-state-${row.item}`} />
            </div>
            <p className="mt-1 text-xs text-ink-soft">{ONBOARDING_ITEM_DISPLAY[row.item].why}</p>
            {row.prerequisite === null ? null : (
              <p data-testid={`onboarding-prerequisite-${row.item}`} className="mt-1 text-xs text-ink-soft">
                선행: {ONBOARDING_ITEM_DISPLAY[row.prerequisite].label}
              </p>
            )}
          </li>
        ))}
      </ol>

      {checklist.pending.length === 0 ? (
        /* **다음 행동 하나**(§6.5 · D-7E2b-4) — 미완이 0 이면 오늘의 공고로 간다. */
        <div data-testid="onboarding-complete" className="rounded-lg border border-accent/40 bg-card p-4">
          <p className="text-sm font-medium">걸리는 항목을 모두 채웠습니다.</p>
          <Link
            to="/"
            className="mt-3 inline-block rounded-md bg-accent px-3 py-1 text-sm font-medium text-accent-ink"
          >
            오늘의 공고 보기
          </Link>
        </div>
      ) : null}

      {/*
        편집 세션이 **진행 중인 동안**에는 단계를 옮기지 않는다(v r1 L-1). 옮기면 전략 단계가
        언마운트되어 **보낸 값이 적용되지도 취소되지도 않은 세션**이 서버에 남는다. 설정 화면에는
        이 길이 없었다 — 거기서는 화면을 떠나야 생기던 일이 마법사에서는 버튼 한 번으로 생긴다.

        **술어가 「열린」이 아니라 「진행 중」인 이유**(CR·v r2 MEDIUM): 첫 판은 열린 세션으로
        잠갔고, 서버가 이미 끝낸 세션에서는 그 뒤 모든 명령이 409 라 `open` 이 비워지지 않아
        **마법사가 갇혔다**. 그때 이 자리의 주석은 「패널의 「그만두기」가 늘 있으므로 갇히는 자리는
        없다」였는데, 종말 state 에서는 그 버튼이 그려지지 않는다 — 거짓인 전제였다. 지금은
        ⓐ 잠금이 진행 중에만 걸리고 ⓑ 끝난 세션의 패널에 「닫기」가 선다.
      */}
      <nav aria-label="단계" className="flex flex-col gap-2">
        <div className="flex flex-wrap gap-2">
          {ONBOARDING_STEPS.map((step) => (
            <button
              key={step.id}
              type="button"
              data-testid={`onboarding-step-${step.id}`}
              aria-current={step.id === current ? 'step' : undefined}
              aria-controls="onboarding-step-body"
              disabled={sessionOpen}
              // 잠긴 사유를 **보조기술에도** 준다(CR r2 LOW): `disabled` 버튼은 탭 순서에서 빠지고
              // `title` 은 읽히지 않아, 키보드·스크린리더 운영자에게는 탐색이 사유 없이 사라진다.
              aria-describedby={sessionOpen ? STEP_LOCK_REASON_ID : undefined}
              onClick={() => setPicked(step.id)}
              className={`rounded-md px-2 py-1 text-xs disabled:opacity-50 ${
                step.id === current
                  ? 'bg-neutral-skin font-medium text-neutral-ink'
                  : 'text-ink-soft hover:bg-neutral-skin'
              }`}
            >
              {step.label}
            </button>
          ))}
        </div>
        {sessionOpen ? (
          <p id={STEP_LOCK_REASON_ID} data-testid="onboarding-step-lock" role="status" className="text-xs text-ink-soft">
            편집을 마치거나 그만두면 단계를 옮길 수 있습니다.
          </p>
        ) : null}
      </nav>

      <div id="onboarding-step-body" data-testid="onboarding-step-body" className="flex flex-col gap-4">
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

      <section data-testid="onboarding-not-ready" className="flex flex-col gap-2">
        <h3 className="text-base font-semibold">준비 중</h3>
        <ul className="flex flex-col gap-2">
          {NOT_READY.map((one) => (
            <li key={one.id} className="rounded-md border border-line p-3">
              <p className="text-sm">{one.label}</p>
              <p className="mt-1 text-xs text-ink-soft">{one.why}</p>
            </li>
          ))}
        </ul>
      </section>
    </section>
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
    <div data-testid="onboarding-banner" className="rounded-lg border border-line bg-card p-3">
      <p className="text-sm">
        시작하기에서 아직 채우지 않은 항목이 <span className="font-numeric">{pending}</span>개 있습니다.
      </p>
      <Link to="/start" className="mt-2 inline-block rounded-md border border-line px-3 py-1 text-xs underline">
        시작하기로
      </Link>
    </div>
  );
}

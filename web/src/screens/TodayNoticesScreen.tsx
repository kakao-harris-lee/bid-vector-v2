import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent, type ReactNode } from 'react';

import {
  addBidCase,
  listBidCases,
  listSuggestions,
  searchNotices,
  type BidCase,
  type NoticeSearchQuery,
  type NoticeSummary,
} from '../api/queries';
import { bidCasesQueryKey } from '../api/queryKeys';
import { Button, LinkButton } from '../components/Button';
import { EmptyFace, ErrorFace, Skeleton } from '../components/ScreenFaces';
import { ToastHost, useToast } from '../components/Toast';
import { isUnauthorized, SignedOutFace, WriteFailureFace } from '../components/WriteFailure';
import { NOTICES_RESULT_STATE_DISPLAY, SUGGESTIONS_RESULT_STATE_DISPLAY } from '../display/noticeDisplay';
import { instantDisplayOf, localInputToIso, readClock } from '../display/timeDisplay';
import { displayOrUnknown } from '../display/stateDisplay';
import { useSessionCsrf } from '../session/useSessionCsrf';

import { NoticeCard, SuggestionCard } from './NoticeCard';
import { OnboardingBanner } from './OnboardingScreen';

/** 한 쪽 크기 — 계약의 상한(100) 안이다. 「더 보기」가 다음 쪽을 이어 붙인다(CR M-1). */
const PAGE_SIZE = 20;

/** 담기는 **실투찰 건**으로 연다 — 페이퍼는 별 화면이고 섞지 않는다(SET-03). */
const BID_KIND: BidCase['bidKind'] = 'REAL';

/** 운영자가 적는 칸 — **서버 파라미터 이름 그대로**다(사본을 두면 둘이 갈린다). */
type FieldId = 'q' | 'agency' | 'amountMin' | 'amountMax' | 'deadlineFrom' | 'deadlineTo';
type SearchForm = Readonly<Record<FieldId, string>>;

const EMPTY_FORM: SearchForm = {
  q: '',
  agency: '',
  amountMin: '',
  amountMax: '',
  deadlineFrom: '',
  deadlineTo: '',
};

/** 칸의 모양 — 적는 방법만 다르고 뜻은 서버 파라미터가 정한다. */
type FieldKind = 'text' | 'numeric' | 'instant';

interface ChipField {
  readonly field: Exclude<FieldId, 'q'>;
  readonly label: string;
  readonly kind: FieldKind;
}

interface ChipAxis {
  readonly id: string;
  readonly label: string;
  readonly fields: readonly ChipField[];
}

/**
 * **조건은 칩이다**(§6.5 「검색 우선·필터는 칩」). 앞 판은 접힌 입력칸 셋이라 조건이 걸려
 * 있는지가 접힌 채로는 보이지 않았다 — 칩은 **적용된 조건을 자기 얼굴에** 싣는다.
 *
 * **축은 서버의 기존 파라미터뿐이다**(팀장 착수 판정 ⓒ). 구간 프리셋(「1억 미만」·「3일 내」)을
 * 두지 않는 이유가 둘이다: 그 구간 값이 화면에 사는 정책 상수가 되고, 마감일 쪽은 KST 날 경계
 * 셈을 화면이 다시 지어야 한다. 입력칸 칩은 `timeDisplay` 의 변환 한 자리로 선다.
 */
const FILTER_CHIPS = [
  {
    id: 'agency',
    label: '기관',
    fields: [{ field: 'agency', label: '기관명', kind: 'text' }],
  },
  {
    id: 'amount',
    label: '금액대',
    fields: [
      { field: 'amountMin', label: '기초금액 하한', kind: 'numeric' },
      { field: 'amountMax', label: '기초금액 상한', kind: 'numeric' },
    ],
  },
  {
    id: 'deadline',
    label: '마감일',
    fields: [
      { field: 'deadlineFrom', label: '마감일 부터', kind: 'instant' },
      { field: 'deadlineTo', label: '마감일 까지', kind: 'instant' },
    ],
  },
] as const satisfies readonly ChipAxis[];

/** 적는 방법 → 입력 속성. 표가 한 자리라 칸마다 속성이 갈리지 않는다. */
const FIELD_INPUT = {
  text: { type: 'text' },
  numeric: { type: 'text', inputMode: 'numeric' },
  instant: { type: 'datetime-local' },
} as const satisfies Record<FieldKind, { readonly type: string; readonly inputMode?: 'numeric' }>;

/**
 * 빈 칸은 **조건이 아니다** — 빈 문자열·0 을 조건으로 보내면 서버가 다른 질문을 받는다.
 *
 * **이 함수가 「검색 중인가」의 유일한 출처이기도 하다**: 조건 집합이 비면 기본 화면(제안)이고,
 * 하나라도 있으면 그 결과가 목록을 대신한다. 두 판정을 따로 적으면 「조건이 있는데 제안이
 * 보인다」 같은 틈이 생긴다.
 */
function conditionsOf(form: SearchForm): Omit<NoticeSearchQuery, 'page' | 'size'> {
  const amountMin = Number.parseInt(form.amountMin, 10);
  const amountMax = Number.parseInt(form.amountMax, 10);
  // 운영자가 적은 지역 시각 → wire 의 순간. 그 변환은 `timeDisplay` 한 자리다(M9 D-1).
  const deadlineFrom = localInputToIso(form.deadlineFrom);
  const deadlineTo = localInputToIso(form.deadlineTo);
  return {
    ...(form.q.trim() === '' ? {} : { q: form.q.trim() }),
    ...(form.agency.trim() === '' ? {} : { agency: form.agency.trim() }),
    ...(Number.isNaN(amountMin) ? {} : { amountMin }),
    ...(Number.isNaN(amountMax) ? {} : { amountMax }),
    ...(deadlineFrom === null ? {} : { deadlineFrom }),
    ...(deadlineTo === null ? {} : { deadlineTo }),
  };
}

function queryOf(form: SearchForm, page: number): NoticeSearchQuery {
  return { page, size: PAGE_SIZE, ...conditionsOf(form) };
}

function hasConditions(form: SearchForm): boolean {
  return Object.keys(conditionsOf(form)).length > 0;
}

/** 그 칸들을 비운 폼 — 칩 하나를 지우는 일이 다른 조건을 건드리지 않는다. */
function withCleared(form: SearchForm, axis: ChipAxis): SearchForm {
  return { ...form, ...Object.fromEntries(axis.fields.map((one) => [one.field, ''])) };
}

/**
 * 칸 하나의 **보이는 값**. 시각은 **표시층의 문면**으로 낸다 — 운영자가 적은 벽시계를 그대로
 * 되비추면 이 저장소가 금지한 원문 자리가 하나 더 생긴다(M9 D-1 · D-4).
 */
function fieldTextOf(field: ChipField, raw: string): string | null {
  if (raw.trim() === '') {
    return null;
  }
  if (field.kind !== 'instant') {
    return raw.trim();
  }
  const iso = localInputToIso(raw);
  return iso === null ? null : instantDisplayOf(iso).text;
}

/** 칩에 보이는 요약 — **적용된** 조건만이다(적다 만 값은 칩의 얼굴을 바꾸지 않는다). */
function chipSummaryOf(axis: ChipAxis, applied: SearchForm): string | null {
  const parts = axis.fields
    .map((one) => fieldTextOf(one, applied[one.field]))
    .filter((one): one is string => one !== null);
  return parts.length === 0 ? null : parts.join(' ~ ');
}

/**
 * 빈 화면의 **다음 걸음**(받는 항목 7). 상태마다 운영자가 할 수 있는 일이 다르고, **없는
 * 상태도 있다** — 「아직 수집 전」·「열린 공고가 없다」에 버튼을 두면 가짜 걸음이 된다.
 *
 * 전수는 표시 표의 키에서 받는다(그 표가 계약 enum 전수를 `satisfies` 로 들고 있다) — 상태가
 * 늘면 이 표가 컴파일에서 멈춘다.
 */
type NextStep =
  | { readonly kind: 'link'; readonly to: string; readonly label: string }
  | { readonly kind: 'clear'; readonly label: string };

const SUGGESTIONS_NEXT_STEP = {
  MATCHED: null,
  // 열린 공고 자체가 없다 — 조건을 넓혀도 통과할 후보가 없으므로 걸음을 지어내지 않는다.
  NO_OPEN_NOTICES: null,
  WATCH_NOT_CONFIGURED: { kind: 'link', to: '/settings', label: '설정에서 감시 조건 세우기' },
  NO_MATCH: { kind: 'link', to: '/settings', label: '설정에서 감시 조건 넓히기' },
} as const satisfies Record<keyof typeof SUGGESTIONS_RESULT_STATE_DISPLAY, NextStep | null>;

const NOTICES_NEXT_STEP = {
  MATCHED: null,
  NO_MATCH: { kind: 'clear', label: '조건 지우고 제안 보기' },
  // 수집 전은 **조건이 좁은 것이 아니다** — 조건을 지워도 나올 것이 없다.
  NOTHING_COLLECTED: null,
} as const satisfies Record<keyof typeof NOTICES_RESULT_STATE_DISPLAY, NextStep | null>;

function nextStepNode(step: NextStep | null, onClear: () => void): ReactNode {
  if (step === null) {
    return undefined;
  }
  switch (step.kind) {
    case 'link':
      return <LinkButton to={step.to}>{step.label}</LinkButton>;
    case 'clear':
      return <Button onClick={onClear}>{step.label}</Button>;
    default: {
      const unreachable: never = step;
      throw new Error(`모르는 다음 걸음: ${JSON.stringify(unreachable)}`);
    }
  }
}

/** 조건 칩 하나 — 누르면 자기 칸이 펼쳐지고, 값이 서면 그 값과 지우기를 보인다. */
function FilterChip({
  axis,
  form,
  applied,
  expanded,
  onToggle,
  onEdit,
  onClear,
}: {
  readonly axis: ChipAxis;
  readonly form: SearchForm;
  readonly applied: SearchForm;
  readonly expanded: boolean;
  readonly onToggle: () => void;
  readonly onEdit: (field: FieldId, value: string) => void;
  readonly onClear: () => void;
}) {
  const panelId = `chip-panel-${axis.id}`;
  const summary = chipSummaryOf(axis, applied);
  return (
    <span className="inline-flex flex-col items-start">
      <span className="inline-flex items-center gap-1 rounded-full border border-line px-1">
        <button
          type="button"
          data-testid={`chip-${axis.id}`}
          aria-expanded={expanded}
          aria-controls={panelId}
          onClick={onToggle}
          className="inline-flex min-h-11 items-center px-2 text-sm"
        >
          {summary === null ? axis.label : `${axis.label} ${summary}`}
        </button>
        {summary === null ? null : (
          <button
            type="button"
            data-testid={`chip-clear-${axis.id}`}
            aria-label={`${axis.label} 조건 지우기`}
            onClick={onClear}
            className="inline-flex min-h-11 items-center px-2 text-sm"
          >
            ×
          </button>
        )}
      </span>
      {/*
        접힌 칸은 **DOM 에 두고 숨긴다**. 지우면 `aria-controls` 가 없는 자리를 가리켜 보조기기가
        「무엇을 펼치는 버튼인지」를 잃는다. 숨기는 수단이 `hidden` **속성**인 것이 요점이다 —
        전역 CSS 가 그 속성에 `display:none` 을 `!important` 로 걸어 두어 이 칸의 `flex` 가 그것을
        덮지 못하고(실측), 그래서 접힌 칸은 터치 타깃 모집단에서도 빠진다.
      */}
      <span
        id={panelId}
        data-testid={panelId}
        hidden={!expanded}
        className="mt-1 flex flex-col gap-2 rounded-md border border-line p-2"
      >
        {axis.fields.map((one) => (
          /*
            레이블이 터치 타깃 하한을 **자기도** 든다(13B 의 레이블 축). 그 게이트는 입력의
            종류를 **증명하지 못하면** 체크박스·라디오와 같은 의무를 씌우는데, 여기서 종류는
            위 표에서 오므로 원문에 리터럴이 없다 — 표를 풀어 쓰는 대신 의무를 채운다.
          */
          <label key={one.field} className="flex min-h-11 flex-col gap-1">
            <span className="text-xs text-ink-soft">{one.label}</span>
            <input
              {...FIELD_INPUT[one.kind]}
              name={one.field}
              value={form[one.field]}
              onChange={(event) => onEdit(one.field, event.target.value)}
              className="min-h-11 rounded-md border border-line bg-card px-3 text-sm"
            />
          </label>
        ))}
      </span>
    </span>
  );
}

/**
 * 화면 B — 오늘의 공고(§6.2 B).
 *
 * **기본 목록은 제안이다**(M13/13E, 받는 항목 8): 서버가 「마감 임박 × 자격 통과」 차례로 주는
 * 그 목록이 운영자가 매일 보는 것이고, 앞 판은 **검색 결과가 그 위**에 와서 제안이 아래로
 * 밀렸다(조건 없는 검색은 전체 목록이다). 검색하면 그 결과가 **제안 자리를 대신한다** — 두
 * 목록이 함께 서면 어느 쪽이 답인지 알 수 없다.
 *
 * 화면은 `/api/notices`·`/api/suggestions`·`/api/bid-cases` 만 부른다(계약의 operation 목록).
 * 빈 결과의 뜻·제안 목록이 빈 이유는 **서버 상태 값**에서 오고 화면이 추론하지 않는다(D-7E-4).
 * **정렬도 서버가 준 차례 그대로**다 — 제안에는 정렬·쪽 파라미터가 없다(승인 경계).
 */
export function TodayNoticesScreen() {
  const queryClient = useQueryClient();
  // 쓰기를 하는 화면이다 — 새로고침 뒤에도 CSRF 값을 쥐게 한다(7A 알려진 제한 L-1).
  const { signedIn } = useSessionCsrf();
  const { toast, show, dismiss } = useToast();
  const [form, setForm] = useState<SearchForm>(EMPTY_FORM);
  const [submitted, setSubmitted] = useState<SearchForm>(EMPTY_FORM);
  const [openChip, setOpenChip] = useState<string | null>(null);
  // **한 번 읽고 내린다**(M9 D-1) — 카드마다 다시 읽으면 같은 목록의 두 카드가 서로 다른
  // 「지금」으로 남은 날을 센다. 벽시계를 읽는 자리는 `timeDisplay` 하나다.
  const [now] = useState(readClock);
  const searching = hasConditions(submitted);

  /**
   * CR M-1 — 앞 판은 첫 쪽만 불러 **전체 42건**을 보이면서 카드 20장만 냈다(나머지로 가는 길이
   * 없었다). 「더 보기」가 다음 쪽을 이어 붙인다 — 다음 쪽의 유무는 **헤더의 전체 건수**와
   * 지금까지 받은 수로 정한다.
   */
  const notices = useInfiniteQuery({
    queryKey: ['notices', submitted],
    queryFn: ({ pageParam }) => searchNotices(queryOf(submitted, pageParam)),
    initialPageParam: 0,
    // **조건이 없으면 묻지 않는다** — 조건 없는 검색은 전체 목록이고, 그것은 이 화면이 답하려는
    // 질문이 아니다(그 질문의 답은 제안이다).
    enabled: searching,
    getNextPageParam: (lastPage, allPages) => {
      const loaded = allPages.reduce((count, page) => count + page.rows.length, 0);
      // CR r2 L-10 — 빈 쪽을 받으면 더 받을 것이 없다. 계수와 행이 갈리는 상황에서 「더 보기」가
      // **아무것도 더하지 않으면서** 남는 자리를 막는다.
      if (lastPage.rows.length === 0) {
        return undefined;
      }
      return loaded < lastPage.total ? allPages.length : undefined;
    },
  });
  const noticePages = notices.data?.pages;
  const noticeRows = noticePages?.flatMap((page) => page.rows);
  const noticeSummary = noticePages?.[0];
  const suggestions = useQuery({ queryKey: ['suggestions'], queryFn: listSuggestions, enabled: !searching });
  const cases = useQuery({ queryKey: bidCasesQueryKey(BID_KIND), queryFn: () => listBidCases(BID_KIND) });

  const add = useMutation({
    mutationFn: (notice: NoticeSummary) =>
      addBidCase({ noticeNumber: notice.noticeNumber, noticeRound: notice.noticeRound, bidKind: BID_KIND }),
    onSuccess: async () => {
      // 담기의 **역전이는 계약에 없다** — 그래서 토스트에 실행 취소를 두지 않는다(D-7E-5).
      show({ text: '입찰함에 담았습니다.' });
      await queryClient.invalidateQueries({ queryKey: bidCasesQueryKey(BID_KIND) });
    },
    // 실패는 **서버 상태를 다시 읽고** 얼굴을 낸다(CR H-2) — 카드가 계속 「담기」를 보이면
    // 운영자는 다시 누르고, 그 사이 실제로 담겼는지는 아무도 모른다.
    onError: async () => {
      dismiss();
      await queryClient.invalidateQueries({ queryKey: bidCasesQueryKey(BID_KIND) });
    },
  });

  function submit(event: FormEvent<HTMLFormElement>): void {
    event.preventDefault();
    setSubmitted(form);
  }

  /** 조건을 모두 지운다 — **제안으로 돌아가는 길**이다(검색은 되돌릴 수 있어야 한다). */
  function clearAll(): void {
    setForm(EMPTY_FORM);
    setSubmitted(EMPTY_FORM);
    setOpenChip(null);
  }

  /** 칩 하나만 지운다 — 적용된 조건에서 그 칸들만 뺀다(나머지 조건은 그대로다). */
  function clearChip(axis: ChipAxis): void {
    setForm(withCleared(form, axis));
    setSubmitted(withCleared(submitted, axis));
  }

  const inBox = new Set((cases.data ?? []).map((row) => `${row.noticeNumber}\u0000${row.noticeRound}`));

  /** 카드의 **다음 행동 하나**(§6.5) — 검색 결과와 제안이 같은 버튼을 쓴다(§6.2 B). */
  function actionFor(notice: NoticeSummary): ReactNode {
    if (inBox.has(`${notice.noticeNumber}\u0000${notice.noticeRound}`)) {
      return <span className="text-xs text-ink-soft">담긴 건</span>;
    }
    return (
      <Button
        tone="primary"
        disabled={add.isPending}
        onClick={() => add.mutate(notice)}
        // CR L-8 — 스크린리더의 버튼 목록에서 어느 공고의 담기인지 구별된다.
        ariaLabel={`${notice.title ?? notice.noticeNumber} 입찰함에 담기`}
      >
        담기
      </Button>
    );
  }

  // CR M-2 — 세션이 없다는 **서버의 답**을 소비한다. 재시도로 풀리지 않으므로 일시 장애와
  // 같은 얼굴을 내지 않는다.
  // CR r2 L-6 — 마운트 시점의 `signedIn` 뿐 아니라 **읽기 401**(떠 있는 화면에서 세션이
  // 끝난 경우)도 같은 답이다. 그 재시도는 영원히 실패한다.
  // `??` 연쇄는 **첫 오류 하나**만 봤다(CR r3 L-2) — 공고 읽기가 500 이고 건 목록이 401 이면
  // 로그인 얼굴로 가지 않았다. 셋 가운데 **하나라도** 401 이면 그 답이다.
  if (signedIn === false || [notices.error, suggestions.error, cases.error].some(isUnauthorized)) {
    return (
      <section data-testid="screen-home" className="mx-auto max-w-3xl p-4">
        <SignedOutFace />
      </section>
    );
  }

  return (
    <section data-testid="screen-home" className="mx-auto flex max-w-3xl flex-col gap-6 p-4">
      <h2 className="text-lg font-semibold">오늘의 공고</h2>

      {/* 시작하기로 가는 **진입 하나**(M7/7E-2b, D-7E2b-4) — 서버의 미완이 0 이면 뜨지 않는다. */}
      <OnboardingBanner />

      <form onSubmit={submit} className="flex flex-col gap-2">
        <label className="flex flex-col gap-1">
          <span className="text-xs text-ink-soft">공고명·공고번호</span>
          <input
            name="q"
            value={form.q}
            onChange={(event) => setForm({ ...form, q: event.target.value })}
            className="min-h-11 rounded-md border border-line bg-card px-3 text-sm"
          />
        </label>
        <div className="flex flex-wrap items-start gap-2">
          {FILTER_CHIPS.map((axis) => (
            <FilterChip
              key={axis.id}
              axis={axis}
              form={form}
              applied={submitted}
              expanded={openChip === axis.id}
              onToggle={() => setOpenChip(openChip === axis.id ? null : axis.id)}
              onEdit={(field, value) => setForm({ ...form, [field]: value })}
              onClear={() => clearChip(axis)}
            />
          ))}
        </div>
        <div className="flex flex-wrap gap-2">
          <Button type="submit" tone="primary">
            검색
          </Button>
          {/* 지울 조건이 없으면 **버튼도 없다** — 누를 때 아무 일도 없는 자리를 두지 않는다. */}
          {searching ? (
            <Button testId="search-clear" onClick={clearAll}>
              조건 지우기
            </Button>
          ) : null}
        </div>
      </form>

      {add.isError ? (
        <WriteFailureFace
          what="입찰함에 담지"
          error={add.error}
          onRetry={() => {
            if (add.variables !== undefined) {
              add.mutate(add.variables);
            }
          }}
        />
      ) : null}

      {searching ? (
        <div className="flex flex-col gap-3">
          {notices.isPending ? <Skeleton testId="notices-loading" /> : null}
          {notices.isError ? <ErrorFace testId="notices-error" onRetry={() => void notices.refetch()} /> : null}
          {noticeSummary === undefined || noticeRows === undefined ? null : (
            <>
              <p data-testid="notice-total" className="text-xs text-ink-soft">
                전체 {noticeSummary.total}건 · 지금 {noticeRows.length}장
              </p>
              {noticeRows.length === 0 ? (
                <EmptyFace
                  testId="notices-empty"
                  display={displayOrUnknown(NOTICES_RESULT_STATE_DISPLAY, noticeSummary.resultState)}
                  action={nextStepNode(NOTICES_NEXT_STEP[noticeSummary.resultState] ?? null, clearAll)}
                />
              ) : (
                <ul className="flex flex-col gap-3">
                  {noticeRows.map((notice) => (
                    <NoticeCard
                      key={`${notice.noticeNumber}/${notice.noticeRound}`}
                      notice={notice}
                      now={now}
                      action={actionFor(notice)}
                    />
                  ))}
                </ul>
              )}
              {notices.hasNextPage ? (
                <div className="self-start">
                  <Button
                    testId="notices-more"
                    disabled={notices.isFetchingNextPage}
                    onClick={() => void notices.fetchNextPage()}
                  >
                    더 보기
                  </Button>
                </div>
              ) : null}
            </>
          )}
        </div>
      ) : (
        <div className="flex flex-col gap-3">
          <h3 className="text-sm font-semibold">제안 — 감시 조건을 통과한 공고</h3>
          {suggestions.isPending ? <Skeleton testId="suggestions-loading" rows={2} /> : null}
          {suggestions.isError ? (
            <ErrorFace testId="suggestions-error" onRetry={() => void suggestions.refetch()} />
          ) : null}
          {suggestions.data === undefined ? null : suggestions.data.rows.length === 0 ? (
            <EmptyFace
              testId="suggestions-empty"
              display={displayOrUnknown(SUGGESTIONS_RESULT_STATE_DISPLAY, suggestions.data.resultState)}
              action={nextStepNode(SUGGESTIONS_NEXT_STEP[suggestions.data.resultState] ?? null, clearAll)}
            />
          ) : (
            <>
              {/*
                **표시 상한 초과를 말한다**(팀장 착수 판정 ⓐ). `GET /api/suggestions` 에는 쪽
                인자가 없어 상한 초과분에 가는 길이 **없다** — 그 사실을 숨기면 운영자는 통과한
                건을 다 본 줄 안다. 상한은 전략의 값이므로 다음 걸음은 설정이다.
              */}
              {suggestions.data.total > suggestions.data.rows.length ? (
                <div className="flex flex-wrap items-center gap-2">
                  <p data-testid="suggestion-total" className="text-xs text-ink-soft">
                    통과 {suggestions.data.total}건 · 지금 {suggestions.data.rows.length}장
                  </p>
                  <LinkButton to="/settings">설정에서 표시 상한 넓히기</LinkButton>
                </div>
              ) : null}
              <ul className="flex flex-col gap-3">
                {suggestions.data.rows.map((suggestion) => (
                  <SuggestionCard
                    key={`${suggestion.noticeNumber}/${suggestion.noticeRound}`}
                    suggestion={suggestion}
                    now={now}
                    action={actionFor(suggestion)}
                  />
                ))}
              </ul>
            </>
          )}
        </div>
      )}

      <ToastHost toast={toast} onDismiss={dismiss} />
    </section>
  );
}

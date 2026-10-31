import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';

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
import { EmptyFace, ErrorFace, Skeleton } from '../components/ScreenFaces';
import { ToastHost, useToast } from '../components/Toast';
import { isUnauthorized, SignedOutFace, WriteFailureFace } from '../components/WriteFailure';
import { NOTICES_RESULT_STATE_DISPLAY, SUGGESTIONS_RESULT_STATE_DISPLAY } from '../display/noticeDisplay';
import { displayOrUnknown } from '../display/stateDisplay';
import { useSessionCsrf } from '../session/useSessionCsrf';

import { NoticeCard, SuggestionCard } from './NoticeCard';
import { OnboardingBanner } from './OnboardingScreen';

/** 한 쪽 크기 — 계약의 상한(100) 안이다. 「더 보기」가 다음 쪽을 이어 붙인다(CR M-1). */
const PAGE_SIZE = 20;

/** 담기는 **실투찰 건**으로 연다 — 페이퍼는 별 화면이고 섞지 않는다(SET-03). */
const BID_KIND: BidCase['bidKind'] = 'REAL';

interface SearchForm {
  readonly q: string;
  readonly agency: string;
  readonly amountMin: string;
  readonly amountMax: string;
}

const EMPTY_FORM: SearchForm = { q: '', agency: '', amountMin: '', amountMax: '' };

/** 빈 칸은 **조건이 아니다** — 빈 문자열·0 을 조건으로 보내면 서버가 다른 질문을 받는다. */
function queryOf(form: SearchForm, page: number): NoticeSearchQuery {
  const query: NoticeSearchQuery = { page, size: PAGE_SIZE };
  const amountMin = Number.parseInt(form.amountMin, 10);
  const amountMax = Number.parseInt(form.amountMax, 10);
  return {
    ...query,
    ...(form.q.trim() === '' ? {} : { q: form.q.trim() }),
    ...(form.agency.trim() === '' ? {} : { agency: form.agency.trim() }),
    ...(Number.isNaN(amountMin) ? {} : { amountMin }),
    ...(Number.isNaN(amountMax) ? {} : { amountMax }),
  };
}

/**
 * 화면 B — 오늘의 공고(§6.2 B). 검색창 하나가 위, 제안 목록이 아래이고 **같은 카드**를 쓴다.
 *
 * 화면은 `/api/notices`·`/api/suggestions`·`/api/bid-cases` 만 부른다(계약의 operation 목록).
 * 빈 결과의 뜻·제안 목록이 빈 이유는 **서버 상태 값**에서 오고 화면이 추론하지 않는다(D-7E-4).
 */
export function TodayNoticesScreen() {
  const queryClient = useQueryClient();
  // 쓰기를 하는 화면이다 — 새로고침 뒤에도 CSRF 값을 쥐게 한다(7A 알려진 제한 L-1).
  const { signedIn } = useSessionCsrf();
  const { toast, show, dismiss } = useToast();
  const [form, setForm] = useState<SearchForm>(EMPTY_FORM);
  const [submitted, setSubmitted] = useState<SearchForm>(EMPTY_FORM);
  const [now] = useState(() => new Date());

  /**
   * CR M-1 — 앞 판은 첫 쪽만 불러 **전체 42건**을 보이면서 카드 20장만 냈다(나머지로 가는 길이
   * 없었다). `queryOf` 의 `page` 인자가 죽어 있던 자리이기도 하다. 「더 보기」가 다음 쪽을
   * 이어 붙인다 — 다음 쪽의 유무는 **헤더의 전체 건수**와 지금까지 받은 수로 정한다.
   */
  const notices = useInfiniteQuery({
    queryKey: ['notices', submitted],
    queryFn: ({ pageParam }) => searchNotices(queryOf(submitted, pageParam)),
    initialPageParam: 0,
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
  const suggestions = useQuery({ queryKey: ['suggestions'], queryFn: listSuggestions });
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

  const inBox = new Set((cases.data ?? []).map((row) => `${row.noticeNumber}\u0000${row.noticeRound}`));

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
            className="rounded-md border border-line bg-card px-3 py-2 text-sm"
          />
        </label>
        {/* 점진적 공개(§6.5) — 조건은 접어 둔다. 기본 화면은 검색창 하나다. */}
        <details className="rounded-md border border-line px-3 py-2">
          <summary className="cursor-pointer text-xs text-ink-soft">조건 더하기</summary>
          <div className="mt-2 grid gap-2 sm:grid-cols-3">
            <label className="flex flex-col gap-1">
              <span className="text-xs text-ink-soft">기관명</span>
              <input
                name="agency"
                value={form.agency}
                onChange={(event) => setForm({ ...form, agency: event.target.value })}
                className="rounded-md border border-line bg-card px-2 py-1 text-sm"
              />
            </label>
            <label className="flex flex-col gap-1">
              <span className="text-xs text-ink-soft" title="기초금액 기준(원)">
                기초금액 하한
              </span>
              <input
                name="amountMin"
                inputMode="numeric"
                value={form.amountMin}
                onChange={(event) => setForm({ ...form, amountMin: event.target.value })}
                className="rounded-md border border-line bg-card px-2 py-1 font-numeric text-sm"
              />
            </label>
            <label className="flex flex-col gap-1">
              <span className="text-xs text-ink-soft" title="기초금액 기준(원)">
                기초금액 상한
              </span>
              <input
                name="amountMax"
                inputMode="numeric"
                value={form.amountMax}
                onChange={(event) => setForm({ ...form, amountMax: event.target.value })}
                className="rounded-md border border-line bg-card px-2 py-1 font-numeric text-sm"
              />
            </label>
          </div>
        </details>
        <button
          type="submit"
          className="self-start rounded-md bg-accent px-3 py-2 text-sm font-medium text-accent-ink"
        >
          검색
        </button>
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
              />
            ) : (
              <ul className="flex flex-col gap-3">
                {noticeRows.map((notice) => (
                  <NoticeCard
                    key={`${notice.noticeNumber}/${notice.noticeRound}`}
                    notice={notice}
                    now={now}
                    action={
                      inBox.has(`${notice.noticeNumber}\u0000${notice.noticeRound}`) ? (
                        <span className="text-xs text-ink-soft">담긴 건</span>
                      ) : (
                        <button
                          type="button"
                          disabled={add.isPending}
                          onClick={() => add.mutate(notice)}
                          // CR L-8 — 스크린리더의 버튼 목록에서 어느 공고의 담기인지 구별된다.
                          aria-label={`${notice.title ?? notice.noticeNumber} 입찰함에 담기`}
                          className="rounded-md bg-accent px-3 py-1 text-sm font-medium text-accent-ink disabled:opacity-50"
                        >
                          담기
                        </button>
                      )
                    }
                  />
                ))}
              </ul>
            )}
            {notices.hasNextPage ? (
              <button
                type="button"
                data-testid="notices-more"
                disabled={notices.isFetchingNextPage}
                onClick={() => void notices.fetchNextPage()}
                className="self-start rounded-md border border-line px-3 py-2 text-sm disabled:opacity-50"
              >
                더 보기
              </button>
            ) : null}
          </>
        )}
      </div>

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
          />
        ) : (
          <ul className="flex flex-col gap-3">
            {suggestions.data.rows.map((suggestion) => (
              <SuggestionCard
                key={`${suggestion.noticeNumber}/${suggestion.noticeRound}`}
                suggestion={suggestion}
                now={now}
                action={
                  inBox.has(`${suggestion.noticeNumber}\u0000${suggestion.noticeRound}`) ? (
                    <span className="text-xs text-ink-soft">담긴 건</span>
                  ) : (
                    <button
                      type="button"
                      disabled={add.isPending}
                      onClick={() => add.mutate(suggestion)}
                      aria-label={`${suggestion.title ?? suggestion.noticeNumber} 입찰함에 담기`}
                      className="rounded-md bg-accent px-3 py-1 text-sm font-medium text-accent-ink disabled:opacity-50"
                    >
                      담기
                    </button>
                  )
                }
              />
            ))}
          </ul>
        )}
      </div>

      <ToastHost toast={toast} onDismiss={dismiss} />
    </section>
  );
}

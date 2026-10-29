import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
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
import { EmptyFace, ErrorFace, Skeleton } from '../components/ScreenFaces';
import { ToastHost, useToast } from '../components/Toast';
import { WriteFailureFace } from '../components/WriteFailure';
import { NOTICES_RESULT_STATE_DISPLAY, SUGGESTIONS_RESULT_STATE_DISPLAY } from '../display/noticeDisplay';
import { displayOrUnknown } from '../display/stateDisplay';
import { useSessionCsrf } from '../session/useSessionCsrf';

import { NoticeCard, SuggestionCard } from './NoticeCard';

/** 한 페이지 크기 — 계약의 상한(100) 안이고, 「더 보기」가 다음 페이지를 잇는다. */
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
  useSessionCsrf();
  const { toast, show, dismiss } = useToast();
  const [form, setForm] = useState<SearchForm>(EMPTY_FORM);
  const [submitted, setSubmitted] = useState<SearchForm>(EMPTY_FORM);
  const [now] = useState(() => new Date());

  const notices = useQuery({
    queryKey: ['notices', submitted],
    queryFn: () => searchNotices(queryOf(submitted, 0)),
  });
  const suggestions = useQuery({ queryKey: ['suggestions'], queryFn: listSuggestions });
  const cases = useQuery({ queryKey: ['bid-cases', BID_KIND], queryFn: () => listBidCases(BID_KIND) });

  const add = useMutation({
    mutationFn: (notice: NoticeSummary) =>
      addBidCase({ noticeNumber: notice.noticeNumber, noticeRound: notice.noticeRound, bidKind: BID_KIND }),
    onSuccess: async () => {
      // 담기의 **역전이는 계약에 없다** — 그래서 토스트에 실행 취소를 두지 않는다(D-7E-5).
      show({ text: '입찰함에 담았습니다.' });
      await queryClient.invalidateQueries({ queryKey: ['bid-cases', BID_KIND] });
    },
    // 실패는 **서버 상태를 다시 읽고** 얼굴을 낸다(CR H-2) — 카드가 계속 「담기」를 보이면
    // 운영자는 다시 누르고, 그 사이 실제로 담겼는지는 아무도 모른다.
    onError: async () => {
      dismiss();
      await queryClient.invalidateQueries({ queryKey: ['bid-cases', BID_KIND] });
    },
  });

  function submit(event: FormEvent<HTMLFormElement>): void {
    event.preventDefault();
    setSubmitted(form);
  }

  const inBox = new Set((cases.data ?? []).map((row) => `${row.noticeNumber}\u0000${row.noticeRound}`));

  return (
    <section data-testid="screen-home" className="mx-auto flex max-w-3xl flex-col gap-6 p-4">
      <h2 className="text-lg font-semibold">오늘의 공고</h2>

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
        {notices.data === undefined ? null : (
          <>
            <p data-testid="notice-total" className="text-xs text-ink-soft">
              전체 {notices.data.total}건
            </p>
            {notices.data.rows.length === 0 ? (
              <EmptyFace
                testId="notices-empty"
                display={displayOrUnknown(NOTICES_RESULT_STATE_DISPLAY, notices.data.resultState)}
              />
            ) : (
              <ul className="flex flex-col gap-3">
                {notices.data.rows.map((notice) => (
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

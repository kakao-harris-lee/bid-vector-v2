import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useParams } from 'react-router';

import {
  draftBid,
  listBidCases,
  readTimeline,
  recordBid,
  requestBidPrice,
  resumeCase,
  searchNotices,
  type BidCase,
  type BidPriceRequestResponse,
} from '../api/queries';
import { EmptyFace, ErrorFace, Skeleton } from '../components/ScreenFaces';
import { Field, StateBadge } from '../components/StateBadge';
import { ToastHost, useToast } from '../components/Toast';
import { NEXT_CHECKPOINT_DISPLAY, nextActionOf, type NextActionKind } from '../display/caseDisplay';
import { amountDisplayOf, daysUntil, noticeStatusDisplayOf } from '../display/noticeDisplay';
import { displayOf } from '../display/stateDisplay';
import { useSessionCsrf } from '../session/useSessionCsrf';

import { WorksheetGrounds } from './WorksheetGrounds';
import {
  ActiveBidsDeclaration,
  BidAmountFields,
  EMPTY_BID_FORM,
  KonepsHandoff,
  parseCount,
  parseDrawNumbers,
  parseWon,
  type BidAmountForm,
} from './WorksheetRecord';
import { CaseStateBadge, WorksheetTimeline } from './WorksheetTimeline';

const BID_KIND: BidCase['bidKind'] = 'REAL';

/**
 * 화면 C — 공고 워크시트(§6.2 C). ②③④를 한 화면에 둔다: 머리 → 자격·근거 → 나라장터 이동 →
 * 투찰 기록 → 타임라인.
 *
 * **공고 단건 조회 operation 이 계약에 없다**(요구표 ⓑ, 담당 7C①) — 그래서 검색으로 한 건을
 * 고른다(`q=<공고번호>` 뒤 번호·차수 일치). 알려진 제한으로 등재하고 7C① 후속으로 보고한다.
 */
export function WorksheetScreen() {
  const params = useParams<{ noticeNumber: string; noticeRound: string }>();
  const noticeNumber = params.noticeNumber ?? '';
  const noticeRound = params.noticeRound ?? '';
  const queryClient = useQueryClient();
  // 쓰기를 하는 화면이다 — 새로고침 뒤에도 CSRF 값을 쥐게 한다(7A 알려진 제한 L-1).
  useSessionCsrf();
  const { toast, show, dismiss } = useToast();
  const [form, setForm] = useState<BidAmountForm>(EMPTY_BID_FORM);
  // 진행 중 투찰 건수의 **선언** — 기본값이 없다(CR H-1). 빈 칸이면 요청 경로가 막힌다.
  const [activeBids, setActiveBids] = useState('');
  const [price, setPrice] = useState<BidPriceRequestResponse | null>(null);
  const [blocked, setBlocked] = useState<string | null>(null);
  const [activeBidsBlocked, setActiveBidsBlocked] = useState<string | null>(null);
  const [now] = useState(() => new Date());

  const notices = useQuery({
    queryKey: ['notice', noticeNumber, noticeRound],
    queryFn: () => searchNotices({ q: noticeNumber, size: 100 }),
  });
  const cases = useQuery({ queryKey: ['bid-cases', BID_KIND], queryFn: () => listBidCases(BID_KIND) });

  const notice = notices.data?.rows.find(
    (row) => row.noticeNumber === noticeNumber && row.noticeRound === noticeRound,
  );
  const bidCase = (cases.data ?? []).find(
    (row) => row.noticeNumber === noticeNumber && row.noticeRound === noticeRound,
  );

  const timeline = useQuery({
    queryKey: ['timeline', bidCase?.caseId],
    queryFn: () => readTimeline(bidCase?.caseId ?? 0),
    enabled: bidCase !== undefined,
  });

  async function refreshCase(): Promise<void> {
    await queryClient.invalidateQueries({ queryKey: ['bid-cases', BID_KIND] });
    await queryClient.invalidateQueries({ queryKey: ['timeline'] });
  }

  const askPrice = useMutation({
    // 선언된 값만 wire 에 닿는다 — 호출 자리에 리터럴이 없다(CR H-1 · v M-5).
    mutationFn: (currentActiveBids: number) =>
      requestBidPrice({ noticeNumber, noticeRound, currentActiveBids }),
    onSuccess: (result) => {
      setPrice(result);
    },
  });

  const makeDraft = useMutation({
    mutationFn: (payload: { readonly caseId: number; readonly amountWon: number; readonly vat: 'INCLUSIVE' | 'EXCLUSIVE' | 'UNKNOWN' }) =>
      draftBid(payload.caseId, {
        amountWon: payload.amountWon,
        vatTreatment: payload.vat,
        presentedAt: new Date().toISOString(),
      }),
    onSuccess: async () => {
      show({ text: '투찰 예정 초안을 만들었습니다.' });
      await refreshCase();
    },
  });

  const makeRecord = useMutation({
    mutationFn: (payload: {
      readonly caseId: number;
      readonly amountWon: number;
      readonly vat: 'INCLUSIVE' | 'EXCLUSIVE' | 'UNKNOWN';
      readonly drawNumbers: number[];
      readonly memo: string;
    }) =>
      recordBid(payload.caseId, {
        amountWon: payload.amountWon,
        vatTreatment: payload.vat,
        submittedAt: new Date().toISOString(),
        drawNumbers: payload.drawNumbers,
        ...(payload.memo.trim() === '' ? {} : { memo: payload.memo.trim() }),
      }),
    onSuccess: async () => {
      show({ text: '투찰 기록을 남겼습니다.' });
      await refreshCase();
    },
  });

  const resume = useMutation({
    mutationFn: (caseId: number) => resumeCase(caseId),
    onSuccess: async () => {
      show({ text: '다시 보기로 돌렸습니다.' });
      await refreshCase();
    },
  });

  /** 금액·과세가 **둘 다** 있어야 전이를 부른다 — 없으면 멈추고 무엇이 없는지 말한다. */
  function amountOrBlock(): { readonly amountWon: number; readonly vat: 'INCLUSIVE' | 'EXCLUSIVE' | 'UNKNOWN' } | null {
    const amountWon = parseWon(form.amount);
    if (amountWon === null) {
      setBlocked('투찰 금액을 원 단위 정수로 적으십시오.');
      return null;
    }
    if (form.vat === '') {
      setBlocked('과세 구분을 고르십시오.');
      return null;
    }
    setBlocked(null);
    return { amountWon, vat: form.vat };
  }

  function runPrimary(kind: NextActionKind, caseId: number): void {
    if (kind === 'REQUEST_PRICE') {
      const declared = parseCount(activeBids);
      if (declared === null) {
        setActiveBidsBlocked('진행 중 투찰 건수를 0 이상의 정수로 선언하십시오 — 이 수는 지어내지 않습니다.');
        return;
      }
      setActiveBidsBlocked(null);
      askPrice.mutate(declared);
      return;
    }
    if (kind === 'RESUME') {
      resume.mutate(caseId);
      return;
    }
    const amount = amountOrBlock();
    if (amount === null) {
      return;
    }
    if (kind === 'DRAFT_BID') {
      makeDraft.mutate({ caseId, ...amount });
      return;
    }
    const drawNumbers = parseDrawNumbers(form.drawNumbers);
    if (drawNumbers === null) {
      setBlocked('추첨번호는 정수로 적으십시오.');
      return;
    }
    makeRecord.mutate({ caseId, ...amount, drawNumbers, memo: form.memo });
  }

  if (notices.isPending || cases.isPending) {
    return (
      <section data-testid="screen-worksheet" className="mx-auto max-w-3xl p-4">
        <Skeleton testId="worksheet-loading" />
      </section>
    );
  }
  if (notices.isError || cases.isError) {
    return (
      <section data-testid="screen-worksheet" className="mx-auto max-w-3xl p-4">
        <ErrorFace
          testId="worksheet-error"
          onRetry={() => {
            void notices.refetch();
            void cases.refetch();
          }}
        />
      </section>
    );
  }
  if (notice === undefined) {
    return (
      <section data-testid="screen-worksheet" className="mx-auto max-w-3xl p-4">
        <EmptyFace
          testId="worksheet-missing"
          display={{ label: '그 공고를 찾지 못했습니다', why: '수집된 공고 가운데 이 번호·차수가 없습니다', tone: 'absent' }}
        />
      </section>
    );
  }

  const amount = amountDisplayOf(notice);
  const days = daysUntil(notice.deadlineAt, now);
  const action = bidCase === undefined ? null : nextActionOf(bidCase.state, price !== null);
  const amountWon = parseWon(form.amount);

  return (
    <section data-testid="screen-worksheet" className="mx-auto flex max-w-3xl flex-col gap-4 p-4">
      <header className="rounded-lg border border-line bg-card p-4">
        <div className="flex flex-wrap items-start justify-between gap-2">
          <h2 data-testid="worksheet-title" className="text-base font-semibold break-words">
            {notice.title ?? notice.noticeNumber}
          </h2>
          <span className="flex flex-wrap items-center gap-2">
            <StateBadge display={noticeStatusDisplayOf(notice.status)} testId="worksheet-notice-status" />
            {bidCase === undefined ? null : <CaseStateBadge state={bidCase.state} testId="worksheet-case-state" />}
          </span>
        </div>
        <dl className="mt-3 grid gap-3 sm:grid-cols-2">
          <Field
            label="발주기관"
            testId="worksheet-agency"
            value={notice.demandAgencyName ?? notice.noticeAgencyName ?? null}
          />
          <Field label="공고번호 · 차수" testId="worksheet-number" value={`${notice.noticeNumber} · ${notice.noticeRound}`} />
          <Field label="기초금액" testId="worksheet-amount" hint="투찰 기준 금액. 추정가격과 다르다." value={amount.present ? `${amount.amount} (${amount.provenance ?? ''})` : null} />
          <Field
            label="투찰 마감"
            testId="worksheet-deadline"
            value={notice.deadlineAt === null || notice.deadlineAt === undefined ? null : `${notice.deadlineAt}${days === null ? '' : ` (D${days >= 0 ? '-' : '+'}${Math.abs(days)})`}`}
          />
          {bidCase === undefined ? null : (
            <div className="flex flex-col gap-1">
              <dt className="text-xs text-ink-soft">다음 확인 시점</dt>
              <dd data-testid="worksheet-checkpoint" className="flex flex-wrap items-center gap-2 text-sm">
                <StateBadge display={displayOf(NEXT_CHECKPOINT_DISPLAY, bidCase.nextCheckpointKind)} />
                {bidCase.nextCheckpointAt === null || bidCase.nextCheckpointAt === undefined ? null : (
                  <time dateTime={bidCase.nextCheckpointAt} className="font-numeric text-xs">
                    {bidCase.nextCheckpointAt}
                  </time>
                )}
                {bidCase.overdue ? <span className="text-xs text-bad-ink">지난 일정</span> : null}
              </dd>
            </div>
          )}
        </dl>
        {bidCase === undefined ? (
          <p className="mt-3 text-xs text-ink-soft">이 공고는 아직 입찰함에 없습니다 — 「오늘의 공고」에서 담으십시오.</p>
        ) : action === null ? (
          <p className="mt-3 text-xs text-ink-soft">이 상태에서 할 다음 전이가 없습니다.</p>
        ) : (
          <>
            {/* 요청의 전제이므로 버튼 옆에 둔다 — 선언이 없으면 요청이 가지 않는다(CR H-1). */}
            {action.kind === 'REQUEST_PRICE' ? (
              <ActiveBidsDeclaration value={activeBids} onChange={setActiveBids} blocked={activeBidsBlocked} />
            ) : null}
            <button
              type="button"
              data-testid="worksheet-primary"
              onClick={() => runPrimary(action.kind, bidCase.caseId)}
              disabled={askPrice.isPending || makeDraft.isPending || makeRecord.isPending || resume.isPending}
              className="mt-3 rounded-md bg-accent px-4 py-2 text-sm font-medium text-accent-ink disabled:opacity-50"
            >
              {action.label}
            </button>
          </>
        )}
      </header>

      {askPrice.isPending ? <Skeleton testId="price-loading" rows={2} /> : null}
      {askPrice.isError ? (
        <ErrorFace
          testId="price-error"
          onRetry={() => {
            const declared = parseCount(activeBids);
            if (declared !== null) {
              askPrice.mutate(declared);
            }
          }}
        />
      ) : null}
      {price === null ? null : <WorksheetGrounds price={price} />}

      {bidCase === undefined ? null : (
        <>
          <KonepsHandoff noticeNumber={notice.noticeNumber} amountWon={amountWon} />
          <section className="rounded-lg border border-line bg-card p-4">
            <h3 className="text-sm font-semibold">투찰 금액과 기록</h3>
            <p className="mt-1 text-xs text-ink-soft">
              3안이 아직 산출되지 않아 금액은 직접 적습니다. 초안과 기록이 같은 입력을 씁니다.
            </p>
            <div className="mt-3">
              <BidAmountFields form={form} onChange={setForm} blocked={blocked} />
            </div>
          </section>
        </>
      )}

      {timeline.data === undefined ? null : <WorksheetTimeline entries={timeline.data} />}

      <ToastHost toast={toast} onDismiss={dismiss} />
    </section>
  );
}

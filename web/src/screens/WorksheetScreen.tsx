import { skipToken, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useId, useState } from 'react';
import { useParams } from 'react-router';

import {
  addBidCase,
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
import { bidCasesQueryKey } from '../api/queryKeys';
import { Button, LinkButton } from '../components/Button';
import { cardClass } from '../components/Card';
import { EmptyFace, ErrorFace, Skeleton } from '../components/ScreenFaces';
import { Field, StateBadge } from '../components/StateBadge';
import { ToastHost, useToast } from '../components/Toast';
import { isUnauthorized, SignedOutFace, WriteFailureFace } from '../components/WriteFailure';
import { NEXT_CHECKPOINT_DISPLAY, nextActionOf, type NextActionKind } from '../display/caseDisplay';
import { floorMarginOf, type FloorMargin } from '../display/floorMargin';
import { koreanAmountTextOf } from '../display/moneyDisplay';
import { amountDisplayOf, noticeStatusDisplayOf } from '../display/noticeDisplay';
import { displayOrUnknown } from '../display/stateDisplay';
import { dayCountText, daysUntil, instantDisplayOf, readClock } from '../display/timeDisplay';
import { useSessionCsrf } from '../session/useSessionCsrf';

import { InstantText } from './InstantText';
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
  type VatTreatment,
} from './WorksheetRecord';
import { CaseStateBadge, WorksheetTimeline } from './WorksheetTimeline';

const BID_KIND: BidCase['bidKind'] = 'REAL';

/**
 * **보내기 전 확인**(§6.2 C 3 — 「안을 고르면 확인이 뜨고, 확인하면 초안이 만들어진다」).
 *
 * 확인에 **보낼 값을 담는다**. 지금 입력을 다시 읽으면 확인한 수와 보내는 수가 갈릴 수 있고,
 * 이 화면의 축이 바로 투찰 금액이다. 입력이 바뀌면 이 확인은 **닫힌다**(다른 수를 가리키는
 * 확인을 들고 있지 않는다).
 */
interface BidConfirmation {
  readonly amountWon: number;
  readonly vat: VatTreatment;
}

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
  const { signedIn } = useSessionCsrf();
  const { toast, show, dismiss } = useToast();
  const [form, setForm] = useState<BidAmountForm>(EMPTY_BID_FORM);
  // 진행 중 투찰 건수의 **선언** — 기본값이 없다(CR H-1). 빈 칸이면 요청 경로가 막힌다.
  const [activeBids, setActiveBids] = useState('');
  const [price, setPrice] = useState<BidPriceRequestResponse | null>(null);
  const [blocked, setBlocked] = useState<string | null>(null);
  const [activeBidsBlocked, setActiveBidsBlocked] = useState<string | null>(null);
  const [confirming, setConfirming] = useState<BidConfirmation | null>(null);
  const confirmTitleId = `bid-confirm-title-${useId()}`;
  // **한 번 읽고 내린다**(M9 D-1) — 렌더마다 다시 읽으면 같은 화면의 두 칸이 서로 다른
  // 「지금」으로 날을 센다. 벽시계를 읽는 자리는 `timeDisplay` 하나다.
  const [now] = useState(readClock);

  const notices = useQuery({
    queryKey: ['notice', noticeNumber, noticeRound],
    queryFn: () => searchNotices({ q: noticeNumber, size: 100 }),
  });
  const cases = useQuery({ queryKey: bidCasesQueryKey(BID_KIND), queryFn: () => listBidCases(BID_KIND) });

  const notice = notices.data?.rows.find(
    (row) => row.noticeNumber === noticeNumber && row.noticeRound === noticeRound,
  );
  const bidCase = (cases.data ?? []).find(
    (row) => row.noticeNumber === noticeNumber && row.noticeRound === noticeRound,
  );

  // CR L-5 — 가짜 id(`?? 0`)를 인코딩하지 않는다. 건이 정해진 분기에서 **그 값을 잡아** 넘기면
  // 「0번 건을 조회하는」 자리가 아예 없다(`enabled` 를 훗날 건드려도 그렇다).
  const caseId = bidCase?.caseId;
  const timeline = useQuery({
    queryKey: ['timeline', caseId],
    queryFn: caseId === undefined ? skipToken : () => readTimeline(caseId),
  });

  async function refreshCase(): Promise<void> {
    await queryClient.invalidateQueries({ queryKey: bidCasesQueryKey(BID_KIND) });
    await queryClient.invalidateQueries({ queryKey: ['timeline'] });
  }

  /**
   * 입력이 바뀌면 **열어 둔 확인은 닫는다** — 확인한 금액과 보낼 금액이 갈리지 않게 한다.
   * 어느 칸이 바뀌어도 닫는다: 과세 구분도 보낼 진술의 일부다.
   */
  function changeForm(next: BidAmountForm): void {
    setForm(next);
    setConfirming(null);
  }

  /**
   * **그 명령이 이미 서버에 닿았는가** — 재시도의 공통 전제다(v r3 M-3).
   *
   * 하네스로 서버 거동을 직접 쟀다: `BID_RECORDED` 인 건에 같은 본문을 다시 보내면 200 이
   * 돌아오고 **기록 행이 하나 더 생긴다**(`record_seq` 0·1·2 — 금액이 달라도 받는다). 초안도
   * 같다(`draft_seq` 0·1·2). 되돌림만 서버가 409 로 막는다. 즉 **재전송은 멱등이 아니다.**
   *
   * 실패 얼굴을 내기 전에 건을 다시 읽으므로(`onError`), 그때의 `caseVersion` 이 보낼 때보다
   * 앞서 있으면 그 쓰기는 닿은 것이다 — 다시 보내지 않는다. 다른 행위자가 건을 움직인
   * 경우에도 막히는데, 그쪽이 안전한 방향이다(중복을 만들지 않고 운영자가 상태를 다시 본다).
   */
  function alreadyLanded(sentFromVersion: number | undefined): boolean {
    return sentFromVersion !== undefined && bidCase !== undefined && bidCase.caseVersion > sentFromVersion;
  }

  /** 재시도는 **보낸 그 진술**을 다시 보낸다 — 단, 이미 닿았으면 보내지 않는다. */
  function writeRetry<Sent extends { readonly fromVersion: number }>(mutation: {
    readonly variables: Sent | undefined;
    readonly mutate: (sent: Sent) => void;
    readonly reset: () => void;
  }): () => void {
    return () => {
      const sent = mutation.variables;
      if (sent === undefined) {
        return;
      }
      if (alreadyLanded(sent.fromVersion)) {
        mutation.reset();
        // 문면은 **술어가 세운 것만** 말한다(CR r4 L-4). `alreadyLanded` 는 「건이 움직였다」를
        // 세고, 그것이 내 쓰기였는지 다른 행위자였는지는 가리지 않는다 — 「이미 반영됐습니다」는
        // 운영자가 「내 기록이 남았다」로 읽을 수 있고, 이 slice 의 축이 바로 투찰 기록 장부다.
        show({
          text: '건이 그 사이 움직였습니다 — 중복을 만들지 않으려고 보내지 않았습니다. 아래 상태를 확인하십시오.',
        });
        return;
      }
      mutation.mutate(sent);
    };
  }

  const askPrice = useMutation({
    // 선언된 값만 wire 에 닿는다 — 호출 자리에 리터럴이 없다(CR H-1 · v M-5).
    mutationFn: (payload: { readonly currentActiveBids: number; readonly fromVersion: number }) =>
      requestBidPrice({ noticeNumber, noticeRound, currentActiveBids: payload.currentActiveBids }),
    onSuccess: (result) => {
      setPrice(result);
    },
    // 투찰가 요청도 **서버 상태를 움직일 수 있다**(건이 `PRICE_RECEIVED` 로 간다). 실패 얼굴이
    // 「다시 읽었습니다」라고 말하므로 그 말이 사실이어야 한다(CR r3 M-2).
    onError: async () => {
      dismiss();
      await refreshCase();
    },
  });

  /**
   * 투찰가 요청은 **POST 인데 옛 읽기 얼굴**을 쓰고 있었다(CR r2 L-5) — 세션이 중간에 끝나면
   * 그 자리에서 영원히 재시도한다. 쓰기 넷과 같은 얼굴·같은 401 갈래로 모은다.
   */
  const priceFailure = askPrice.isError
    ? {
        what: '투찰가를 요청하지',
        error: askPrice.error,
        /**
         * **지금 화면에 보이는 선언값**을 보낸다(CR r3 M-3) — 누를 때의 값을 다시 보내면
         * 운영자가 그 사이 고친 수와 보낸 수가 갈린다. 선언은 「그때 적었던 것」이 아니라
         * 「지금 적혀 있는 것」이다.
         */
        retry: () => {
          if (alreadyLanded(askPrice.variables?.fromVersion)) {
            askPrice.reset();
            show({ text: '건이 그 사이 움직였습니다 — 중복 요청을 만들지 않으려고 보내지 않았습니다.' });
            return;
          }
          sendPriceRequest();
        },
      }
    : null;

  const makeDraft = useMutation({
    /**
     * **시각을 payload 로 받는다**(CR r2 M-3). `mutationFn` 안에서 만들면 「다시 시도」가 같은
     * 진술을 보내지 않는다 — 네트워크가 실패했다고 운영자가 제시한 시각이 바뀔 이유가 없다.
     */
    mutationFn: (payload: {
      readonly caseId: number;
      readonly amountWon: number;
      readonly vat: 'INCLUSIVE' | 'EXCLUSIVE' | 'UNKNOWN';
      readonly at: string;
      readonly fromVersion: number;
    }) =>
      draftBid(payload.caseId, {
        amountWon: payload.amountWon,
        vatTreatment: payload.vat,
        presentedAt: payload.at,
      }),
    onSuccess: async () => {
      show({ text: '투찰 예정 초안을 만들었습니다.' });
      await refreshCase();
    },
    // 실패는 성공 토스트를 걷고 **서버 상태를 다시 읽은 뒤** 얼굴을 낸다(CR H-2).
    onError: async () => {
      dismiss();
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
      readonly at: string;
      readonly fromVersion: number;
    }) =>
      recordBid(payload.caseId, {
        amountWon: payload.amountWon,
        vatTreatment: payload.vat,
        submittedAt: payload.at,
        drawNumbers: payload.drawNumbers,
        ...(payload.memo.trim() === '' ? {} : { memo: payload.memo.trim() }),
      }),
    onSuccess: async () => {
      show({ text: '투찰 기록을 남겼습니다.' });
      await refreshCase();
    },
    // 실패는 성공 토스트를 걷고 **서버 상태를 다시 읽은 뒤** 얼굴을 낸다(CR H-2).
    onError: async () => {
      dismiss();
      await refreshCase();
    },
  });

  const resume = useMutation({
    mutationFn: (payload: { readonly caseId: number; readonly fromVersion: number }) =>
      resumeCase(payload.caseId),
    onSuccess: async () => {
      show({ text: '다시 보기로 돌렸습니다.' });
      await refreshCase();
    },
    // 실패는 성공 토스트를 걷고 **서버 상태를 다시 읽은 뒤** 얼굴을 낸다(CR H-2).
    onError: async () => {
      dismiss();
      await refreshCase();
    },
  });

  /**
   * **담기**(계약 받는 항목 3·7 의 워크시트 자리) — 이 공고가 입찰함에 없으면 그 자리에서 담는다.
   * 앞 판은 「오늘의 공고에서 담으십시오」라고만 말해, 워크시트까지 온 운영자를 되돌려 보냈다.
   *
   * 서버가 같은 (공고, 종류)에 건을 **하나만** 두고 이미 담긴 건이면 그 건을 돌려주므로, 재시도가
   * 중복 행을 만들지 않는다(그래서 다른 쓰기들과 달리 버전 가드가 필요 없다).
   */
  const addCase = useMutation({
    mutationFn: () => addBidCase({ noticeNumber, noticeRound, bidKind: BID_KIND }),
    onSuccess: async () => {
      show({ text: '입찰함에 담았습니다.' });
      await refreshCase();
    },
    onError: async () => {
      dismiss();
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

  /** 투찰가 요청 한 자리 — 첫 누름과 재시도가 **같은 길**로 간다(CR r3 M-3). */
  function sendPriceRequest(): void {
    if (bidCase === undefined) {
      return;
    }
    const declared = parseCount(activeBids);
    // 사유를 그 자리에서 말한다(CR r3 L-4) — 「적지 않았다」와 「범위를 넘었다」는 다른 답이다.
    if (declared.kind === 'not-declared') {
      setActiveBidsBlocked('진행 중 투찰 건수를 0 이상의 정수로 선언하십시오 — 이 수는 지어내지 않습니다.');
      return;
    }
    if (declared.kind === 'out-of-range') {
      setActiveBidsBlocked(`선언할 수 있는 건수는 ${declared.max.toLocaleString('ko-KR')} 까지입니다.`);
      return;
    }
    setActiveBidsBlocked(null);
    askPrice.mutate({ currentActiveBids: declared.value, fromVersion: bidCase.caseVersion });
  }

  function runPrimary(kind: NextActionKind, caseId: number, fromVersion: number): void {
    if (kind === 'REQUEST_PRICE') {
      sendPriceRequest();
      return;
    }
    if (kind === 'RESUME') {
      resume.mutate({ caseId, fromVersion });
      return;
    }
    const amount = amountOrBlock();
    if (amount === null) {
      return;
    }
    if (kind === 'DRAFT_BID') {
      // **초안 앞에만 확인이 선다**(§6.2 C 3). 「제출함」은 §6.2 C 5 가 「한 번으로 ④ 완료」로
      // 둔 자리라 같은 단계를 더하지 않는다 — 운영자는 이미 나라장터에서 제출을 끝내고 돌아온다.
      setConfirming({ amountWon: amount.amountWon, vat: amount.vat });
      return;
    }
    const drawNumbers = parseDrawNumbers(form.drawNumbers);
    if (drawNumbers === null) {
      setBlocked('추첨번호는 정수로 적으십시오.');
      return;
    }
    makeRecord.mutate({
      caseId,
      ...amount,
      drawNumbers,
      memo: form.memo,
      at: readClock().toISOString(),
      fromVersion,
    });
  }

  /** 확인에 담긴 **그 진술**을 보낸다 — 보내는 순간 확인은 닫힌다. */
  function acceptConfirmed(caseId: number, fromVersion: number): void {
    if (confirming === null) {
      return;
    }
    setConfirming(null);
    makeDraft.mutate({
      caseId,
      amountWon: confirming.amountWon,
      vat: confirming.vat,
      at: readClock().toISOString(),
      fromVersion,
    });
  }

  // CR r2 L-6 — 마운트 시점의 `signedIn` 뿐 아니라 **읽기 401**(떠 있는 화면에서 세션이
  // 끝난 경우)도 같은 답이다. 그 재시도는 영원히 실패한다.
  // `??` 연쇄는 **첫 오류 하나**만 봤다(CR r3 L-2). 둘 가운데 하나라도 401 이면 그 답이다.
  if (signedIn === false || [notices.error, cases.error].some(isUnauthorized)) {
    return (
      <section data-testid="screen-worksheet" className="mx-auto max-w-3xl p-4">
        <SignedOutFace />
      </section>
    );
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
          // 이유만 말하고 멈추지 않는다 — 여기서 할 수 있는 다음 걸음은 목록으로 돌아가는 것이다.
          action={
            <LinkButton to="/" testId="worksheet-missing-next">
              오늘의 공고로 가기
            </LinkButton>
          }
        />
      </section>
    );
  }

  /**
   * 쓰기 실패 얼굴 하나 — primary 버튼이 하나이므로 한 번에 하나의 쓰기만 돈다. 어느 전이가
   * 실패했는지는 **그 전이의 이름**으로 말한다(CR H-2).
   */
  const writeFailure = makeDraft.isError
    ? {
        what: '투찰 예정 초안을 만들지',
        error: makeDraft.error,
        // **실제로 다시 보낸다**(CR r2 M-3) — 앞 판은 `reset()` 이라 얼굴만 사라졌고, 운영자는
        // 「다시 시도」를 누르고 메시지가 사라지는 것을 **성공으로 읽을 수 있었다**.
        retry: writeRetry(makeDraft),
      }
    : makeRecord.isError
      ? {
          what: '투찰 기록을 기록하지',
          error: makeRecord.error,
          retry: writeRetry(makeRecord),
        }
      : resume.isError
        ? {
            what: '다시 보기로 되돌리지',
            error: resume.error,
            retry: writeRetry(resume),
          }
        : addCase.isError
          ? {
              what: '입찰함에 담지',
              error: addCase.error,
              // 담기는 **멱등**이라(서버가 이미 담긴 건을 돌려준다) 보낸 진술을 그대로 다시 보낸다.
              retry: () => addCase.mutate(),
            }
          : null;

  const amount = amountDisplayOf(notice);
  // 마감은 **절대 시각과 남은 날이 한 칸**이다 — 둘 다 `timeDisplay` 를 지난다(M9 D-1·D-2).
  const deadline = instantDisplayOf(notice.deadlineAt);
  const deadlineDays = dayCountText(daysUntil(notice.deadlineAt, now));
  const checkpoint = bidCase === undefined ? null : instantDisplayOf(bidCase.nextCheckpointAt);
  const action = bidCase === undefined ? null : nextActionOf(bidCase.state, price !== null);
  const amountWon = parseWon(form.amount);
  /**
   * 하한 대비 여유 — **투찰가 응답이 있어야** 하한율이 있다. 요청 전에는 자리 자체를 만들지
   * 않는다(없는 축을 「재지 못함」으로 그리면 화면이 묻지 않은 것에 답한다).
   */
  const margin: FloorMargin | null =
    price === null
      ? null
      : floorMarginOf({
          groundsState: price.floorRateGroundsState,
          rateFraction: price.publishedFloorRateFraction,
          baseAmountWon: price.baseAmountWon,
          baseAmountVatTreatment: price.baseAmountVatTreatment,
          bidAmountWon: amountWon,
          bidVatTreatment: form.vat,
        });

  return (
    <section data-testid="screen-worksheet" className="mx-auto flex max-w-3xl flex-col gap-4 p-4">
      <header className={cardClass()}>
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
            value={deadline.kind === 'absent' ? null : `${deadline.text}${deadlineDays === null ? '' : ` (${deadlineDays})`}`}
          />
          {bidCase === undefined ? null : (
            <div className="flex flex-col gap-1">
              <dt className="text-xs text-ink-soft">다음 확인 시점</dt>
              <dd data-testid="worksheet-checkpoint" className="flex flex-wrap items-center gap-2 text-sm">
                <StateBadge display={displayOrUnknown(NEXT_CHECKPOINT_DISPLAY, bidCase.nextCheckpointKind)} />
                {/* 시점이 없으면 **자리도 없다** — 옆 배지가 「없음」을 말하므로 같은 사실을
                    두 번 적지 않는다(M9 D-1 의 없음 표지는 배지가 없는 자리의 일이다). */}
                {checkpoint === null || checkpoint.kind === 'absent' ? null : (
                  <InstantText display={checkpoint} className="text-xs" />
                )}
                {bidCase.overdue ? <span className="text-xs text-bad-ink">지난 일정</span> : null}
              </dd>
            </div>
          )}
        </dl>
        {bidCase === undefined ? (
          /* **다음 걸음을 그 자리에 둔다**(계약 받는 항목 7) — 앞 판은 다른 화면으로 돌려보냈다. */
          <div className="mt-3 flex flex-col gap-2">
            <p className="text-xs text-ink-soft">
              이 공고는 아직 입찰함에 없습니다 — 담으면 투찰가를 요청하고 투찰을 기록할 수 있습니다.
            </p>
            <div>
              <Button
                tone="primary"
                size="lg"
                testId="worksheet-add-case"
                onClick={() => addCase.mutate()}
                disabled={addCase.isPending}
              >
                입찰함에 담기
              </Button>
            </div>
          </div>
        ) : action === null ? (
          <p className="mt-3 text-xs text-ink-soft">이 상태에서 할 다음 전이가 없습니다.</p>
        ) : (
          <>
            {/* 요청의 전제이므로 버튼 옆에 둔다 — 선언이 없으면 요청이 가지 않는다(CR H-1). */}
            {action.kind === 'REQUEST_PRICE' ? (
              <ActiveBidsDeclaration value={activeBids} onChange={setActiveBids} blocked={activeBidsBlocked} />
            ) : null}
            {/* 그 상태의 **다음 행동 하나** — 화면에서 가장 큰 타깃이다(§6.5). */}
            <div className="mt-3">
              <Button
                tone="primary"
                size="lg"
                testId="worksheet-primary"
                onClick={() => runPrimary(action.kind, bidCase.caseId, bidCase.caseVersion)}
                disabled={askPrice.isPending || makeDraft.isPending || makeRecord.isPending || resume.isPending}
              >
                {action.label}
              </Button>
            </div>
          </>
        )}
      </header>

      {writeFailure === null ? null : (
        <WriteFailureFace what={writeFailure.what} error={writeFailure.error} onRetry={writeFailure.retry} />
      )}

      {askPrice.isPending ? <Skeleton testId="price-loading" rows={2} /> : null}
      {priceFailure === null ? null : (
        <WriteFailureFace what={priceFailure.what} error={priceFailure.error} onRetry={priceFailure.retry} />
      )}
      {price === null ? null : <WorksheetGrounds price={price} />}

      {bidCase === undefined ? null : (
        <>
          <KonepsHandoff noticeNumber={notice.noticeNumber} amountWon={amountWon} margin={margin} />
          <section className={cardClass()}>
            <h3 className="text-sm font-semibold">투찰 금액과 기록</h3>
            {/* v L-1 — 이 문장은 **API 상태에서만** 나온다. 앞 판은 요청 전에도 늘 떠 있어
                「아직 산출 안 함」을 화면이 추론해 말하는 꼴이었다(D-7E-4). */}
            {price !== null && price.bidPriceMenuState === 'NOT_COMPUTED' ? (
              <p data-testid="amount-self-entry-note" className="mt-1 text-xs text-ink-soft">
                투찰가 3안이 아직 산출되지 않아 금액은 직접 적습니다(위 근거의 상태 그대로).
              </p>
            ) : null}
            <p className="mt-1 text-xs text-ink-soft">초안과 기록이 같은 입력을 씁니다.</p>
            <div className="mt-3">
              <BidAmountFields form={form} onChange={changeForm} blocked={blocked} />
            </div>
            {/* **보내기 전 확인**(§6.2 C 3) — 보낼 금액을 숫자와 한글로 함께 보이고, 하한 대비
                자리도 같은 자리에 둔다. 확인에 담긴 값이 그대로 가고, 입력이 바뀌면 닫힌다. */}
            {confirming === null ? null : (
              <div
                data-testid="bid-confirm"
                role="group"
                aria-labelledby={confirmTitleId}
                className="mt-3 rounded-md border border-line p-3"
              >
                <p id={confirmTitleId} className="text-sm font-medium">
                  이 금액으로 투찰하시겠습니까?
                </p>
                <p className="mt-2 font-numeric text-base">{confirming.amountWon.toLocaleString('ko-KR')}</p>
                {koreanAmountTextOf(confirming.amountWon) === null ? null : (
                  <p className="mt-1 text-xs text-ink-soft">{koreanAmountTextOf(confirming.amountWon)}</p>
                )}
                <p className="mt-2 text-xs text-ink-soft">
                  이 수로 투찰 예정 초안을 남깁니다. 나라장터 제출은 운영자가 직접 합니다.
                </p>
                <div className="mt-3 flex flex-wrap gap-2">
                  <Button
                    tone="primary"
                    testId="bid-confirm-accept"
                    onClick={() => acceptConfirmed(bidCase.caseId, bidCase.caseVersion)}
                    disabled={makeDraft.isPending}
                  >
                    이 금액으로 초안 만들기
                  </Button>
                  <Button testId="bid-confirm-cancel" onClick={() => setConfirming(null)}>
                    취소
                  </Button>
                </div>
              </div>
            )}
          </section>
        </>
      )}

      {timeline.data === undefined ? null : <WorksheetTimeline entries={timeline.data} />}

      <ToastHost toast={toast} onDismiss={dismiss} />
    </section>
  );
}

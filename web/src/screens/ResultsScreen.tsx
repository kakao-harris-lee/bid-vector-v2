import { useQueries, useQuery } from '@tanstack/react-query';

import { isUnauthorized as isSessionGone } from '../api/apiError';
import {
  listBidCases,
  readMyBidResults,
  readOpeningResult,
  type BidCase,
  type MyBidAggregate,
} from '../api/queries';
import { bidCasesQueryKey, MY_BID_RESULTS_QUERY_KEY, openingResultQueryKey } from '../api/queryKeys';
import { AbsentValue } from '../components/AbsentValue';
import { LinkButton } from '../components/Button';
import { Card } from '../components/Card';
import { PageHeader } from '../components/PageHeader';
import { EmptyFace, ErrorFace, Skeleton } from '../components/ScreenFaces';
import { StatusPill } from '../components/StatusPill';
import { SummaryTile, tileTwoAxis, tileUnavailable, tileValue } from '../components/SummaryTile';
import { isUnauthorized, SignedOutFace } from '../components/WriteFailure';
import { BID_CASE_STATE_DISPLAY, NOTICE_ROW_ABSENT_TEXT } from '../display/caseDisplay';
import { caseResultsById, rateAxesOf, SAMPLE_NOTE, type CaseResult } from '../display/myBidResultDisplay';
import { RESULT_PENDING_SLOTS, isResultStage, pendingBadgeOf } from '../display/resultDisplay';
import { useSessionCsrf } from '../session/useSessionCsrf';

import { ResultCard } from './ResultCard';
import { ResultsAggregate } from './ResultsAggregate';

/** E 도 **실투찰**만 보인다 — 페이퍼 탭은 SET-03 이 `근거 부족` 이라 만들지 않는다. */
const BID_KIND: BidCase['bidKind'] = 'REAL';

const QUERY_KEY = bidCasesQueryKey(BID_KIND);

/**
 * 결과 단계 건이 하나도 없을 때의 문면. **「결과가 없다」로 접지 않는다** — 결과가 없는
 * 것이 아니라 **결과를 기다리는 건이 아직 없다**(제출 전이거나 담기 전이다).
 */
const EMPTY_DISPLAY = {
  label: '결과를 기다리는 건이 없습니다.',
  why: '「제출함」으로 기록하고 개찰 대기로 넘기면 여기에 섭니다',
  tone: 'absent',
} as const;

/**
 * **영영 오지 않는** 자리의 목록. 자리마다 사유가 붙고 입력칸·버튼이 없다(D-7E3a-3 · D-7E-5
 * 「범위 밖 기능은 빈 자리뿐」).
 *
 * 7E-3a 가 「준비 중」으로 둔 다섯은 M7/7E-3b 에서 화면이 받아 이 목록에서 사라졌다 — 제목이
 * 「아직 비어 있는」이면 남은 둘에 대해 틀린 말이 된다.
 */
function PendingSlots() {
  return (
    <section data-testid="results-pending" className="rounded-lg border border-dashed border-line p-4">
      <h3 className="text-sm font-medium">아직 없는 자리</h3>
      <dl className="mt-2 grid gap-2 sm:grid-cols-2">
        {RESULT_PENDING_SLOTS.map((slot) => (
          <div key={slot.id} data-testid={`pending-${slot.id}`} className="flex flex-col gap-0.5">
            <dt className="text-xs font-medium">
              {slot.label}
              {/* 배지 문면은 **표 한 자리**에서 온다(M14/14D — 13A OPEN ⓖ). 화면이 분류를 보고
                  글자를 고르던 앞 판은 배지가 「다루지 않음」이라 사유의 「지금은」과 어긋났다. */}
              <span className="ml-1 text-absent-ink">{pendingBadgeOf(slot.kind)}</span>
            </dt>
            {/* 사유를 **함께** 적는다 — 배지만 적으면 무엇이 막고 있는지 말하지 않는다. */}
            <dd className="text-xs text-ink-soft">{slot.why}</dd>
          </div>
        ))}
      </dl>
    </section>
  );
}

/**
 * 요약 타일 넷(와이어프레임 E). **둘은 서버가 값을 내지 않는다** — 그 자리는 숫자 칸을 아예
 * 갖지 않고 사유를 든다(계약 「데이터」 절 · `tileUnavailable`).
 *
 * 타일 ③ 은 **두 축 병기**다. `beside` 가 아니라 `twoAxis` 얼굴에 넣는다 — `beside` 의 뜻은
 * 「분모처럼 같은 한 값의 일부」이고 거기에 둘째 **축**을 넣는 것은 그 뜻을 깬다(14A 인계).
 * 축이 길이 둘의 튜플이라 한 축으로 부르는 것은 **타입에서 서지 않는다**.
 */
function SummaryTiles({ aggregate }: { readonly aggregate: MyBidAggregate }) {
  const [recorded, reconciled] = rateAxesOf(aggregate);

  return (
    <div data-testid="results-tiles" className="flex flex-wrap gap-3">
      <SummaryTile
        label="투찰 (기록됨)"
        face={tileValue(String(aggregate.sampleSize), { note: SAMPLE_NOTE })}
        testId="tile-submitted"
      />
      {/* 와이어프레임의 「적격 n/m」 — `aggregate` 에 적격 계수가 없다. 0 으로 접지 않는다. */}
      <SummaryTile
        label="적격 (하한 이상)"
        face={tileUnavailable('서버가 적격 계수를 내지 않습니다')}
        testId="tile-eligible"
      />
      <SummaryTile
        label="낙찰"
        face={tileTwoAxis(
          [
            { label: recorded.label, text: String(recorded.wonCount) },
            { label: reconciled.label, text: String(reconciled.wonCount) },
          ],
          { note: '두 축은 서로 다른 수를 낼 수 있습니다 — 아래 집계가 분모까지 보입니다.' },
        )}
        testId="tile-won"
      />
      {/* 와이어프레임의 「하한 대비 위치 중앙값」 — 하한 계열 칸이 응답에 없다. */}
      <SummaryTile
        label="하한 대비 위치 중앙값"
        face={tileUnavailable('서버가 하한 대비 위치를 내지 않습니다')}
        testId="tile-floor-position"
      />
    </div>
  );
}

/**
 * 잠정 1위 경고 카드(와이어프레임 E). **잠정 1위는 낙찰이 아니다** — 그 문장은 상태 표의
 * `why` 에서 오고 이 카드가 자기 글을 따로 들지 않는다.
 *
 * 목록에 그 상태의 건이 없으면 **카드가 서지 않는다**(경고할 것이 없다).
 */
function ProvisionalFirstWarning({ cases }: { readonly cases: readonly BidCase[] }) {
  const shown = cases.filter((one) => one.state === 'OPENED_PROVISIONAL_FIRST');
  if (shown.length === 0) {
    return null;
  }
  const display: { readonly label: string; readonly why?: string } =
    BID_CASE_STATE_DISPLAY.OPENED_PROVISIONAL_FIRST;

  return (
    <Card as="section" testId="provisional-first-warning">
      <div className="flex flex-wrap items-baseline gap-2">
        <StatusPill tone="good" label={display.label} />
        <h3 className="text-sm font-semibold">낙찰로 확정되지 않았습니다</h3>
      </div>
      {display.why === undefined ? null : <p className="mt-1 text-xs text-ink-soft">{display.why}</p>}
      <ul className="mt-2 flex flex-col gap-1">
        {shown.map((one) => (
          <li key={one.caseId} data-testid={`provisional-first-${one.caseId}`} className="text-xs">
            {/* 제목은 14E 투영이다 — 공고 행이 없으면 그 사실을 말한다(빈 칸으로 접지 않는다). */}
            {one.noticeKnown ? (
              (one.noticeTitle ?? <AbsentValue reason="notSent" />)
            ) : (
              <span className="text-absent-ink">{NOTICE_ROW_ABSENT_TEXT}</span>
            )}
          </li>
        ))}
      </ul>
    </Card>
  );
}

/**
 * 화면 E — 결과 확인(§6.2 E, M7/7E-3a).
 *
 * **읽기 화면이다**(D-7E3a-8): 부르는 operation 은 목록 읽기 하나와 건마다의 개찰 결과 읽기
 * 하나뿐이고, 결과 기록 전이는 그것을 가진 화면 D 로 보낸다.
 *
 * 보이는 값은 전부 서버가 준 것이다 — 상태·다음 확인 시점은 `GET /api/bid-cases` 가, 개찰
 * 결과의 아홉 갈래와 두 금액 축은 `GET /api/opening-results` 가 준다. 화면이 세거나 고르거나
 * 빼는 자리가 없다(집계 자리가 빈 것도 그 때문이다 — 목록을 세어 낙찰률을 짓지 않는다).
 */
export function ResultsScreen() {
  const { signedIn } = useSessionCsrf();
  const cases = useQuery({ queryKey: QUERY_KEY, queryFn: () => listBidCases(BID_KIND) });

  /**
   * 결과 조회는 **화면이 한 번** 부른다(M7/7E-3b) — 건별과 집계가 한 응답이라 카드마다 부를
   * 것이 없고, 그래서 화면이 띄운 목록과 집계가 어긋날 수 없다.
   */
  const results = useQuery({ queryKey: MY_BID_RESULTS_QUERY_KEY, queryFn: readMyBidResults });

  const shown = (cases.data ?? []).filter((row) => isResultStage(row.state));

  /**
   * 카드 하나가 받는 답. **「아직 안 왔다」·「못 읽었다」·「없다」를 한 값으로 접지 않는다** —
   * 접으면 적재 중에도 카드가 「이 건이 없습니다」를 적고, 그것은 거짓이다.
   */
  const byId = caseResultsById(results.data?.cases ?? []);
  const resultOf = (caseId: number): CaseResult => {
    if (results.isPending) {
      return { kind: 'LOADING' };
    }
    if (!results.isSuccess) {
      return { kind: 'FAILED' };
    }
    const rows = byId.get(caseId);
    if (rows === undefined) {
      return { kind: 'MISSING' };
    }
    // 둘 이상이면 **고르지 않는다** — 어느 쪽인지 모른 채 믿게 하는 것보다 진단이 낫다.
    return rows.length === 1 ? { kind: 'FOUND', result: rows[0]! } : { kind: 'AMBIGUOUS', count: rows.length };
  };

  /**
   * 개찰 조회를 **화면이 든다**(r3 CR L-1). 카드마다 질의를 두면 401 일 때 `SignedOutFace` 가
   * 카드 수만큼 서고 — 같은 `role="alert"`·같은 testId 가 N 개 — 보조기술이 같은 문장을 N 번
   * 읽고 `getByTestId('signed-out')` 가 strict 에서 모호해진다. 화면이 들면 그 얼굴이 **하나**다.
   */
  const openings = useQueries({
    queries: shown.map((row) => ({
      queryKey: openingResultQueryKey(row.noticeNumber, row.noticeRound),
      queryFn: () => readOpeningResult(row.noticeNumber, row.noticeRound),
    })),
  });

  /** 건별 개찰 읽기나 결과 조회가 401 이면 **세션이 없는 것**이다 — 목록이 캐시로 살아 있어도 그렇다. */
  const sessionGone = openings.some((one) => isSessionGone(one.error)) || isSessionGone(results.error);

  // 세션이 없다는 **서버의 답**은 재시도로 풀리지 않는다 — 일시 장애와 같은 얼굴을 내지 않는다.
  // **이 분기는 모든 hook 뒤에 온다** — 위의 `useQueries` 를 건너뛰면 렌더마다 hook 수가 달라진다.
  if (signedIn === false || isUnauthorized(cases.error)) {
    return (
      <section data-testid="screen-results" className="mx-auto max-w-3xl p-4">
        <SignedOutFace />
      </section>
    );
  }


  return (
    <section data-testid="screen-results" className="mx-auto flex max-w-3xl flex-col gap-4 p-4">
      <PageHeader
        title="결과 확인"
        summary="실측만 셉니다. 페이퍼(가상)는 섞지 않습니다."
        testId="results-header"
      />

      {/* 타일은 **집계 응답이 선 뒤**에만 선다 — 받지 못한 수를 0 으로 그리지 않는다. */}
      {results.isSuccess ? <SummaryTiles aggregate={results.data.aggregate} /> : null}

      {/* 잠정 1위 경고는 **목록이 선 뒤**다. 그 상태의 건이 없으면 카드 자체가 없다. */}
      {cases.isSuccess ? <ProvisionalFirstWarning cases={shown} /> : null}

      {cases.isPending ? <Skeleton testId="results-loading" /> : null}
      {cases.isError && !isUnauthorized(cases.error) ? (
        <ErrorFace testId="results-error" onRetry={() => void cases.refetch()} />
      ) : null}
      {cases.isSuccess && shown.length === 0 ? (
        /* 빈 얼굴이 **다음 걸음을 준다**(항목 7). 사유는 이미 문면에 있다 — 없던 것은 그
           걸음을 걷는 자리로 가는 길이고, 그 자리가 입찰함이다. */
        <EmptyFace
          display={EMPTY_DISPLAY}
          testId="results-empty"
          action={
            <LinkButton to="/bids" tone="primary" testId="results-empty-action">
              내 입찰함 열기
            </LinkButton>
          }
        />
      ) : null}

      {sessionGone ? <SignedOutFace /> : null}

      {shown.length === 0 ? null : (
        <ul className="flex flex-col gap-3">
          {shown.map((row, index) => (
            <ResultCard
              key={row.caseId}
              bidCase={row}
              opening={openings[index]!}
              result={resultOf(row.caseId)}
            />
          ))}
        </ul>
      )}

      {/* 결과 조회의 **얼굴은 화면이 하나로** 그린다 — 카드마다 그리면 같은 alert 이 N 개 선다. */}
      {results.isError && !isSessionGone(results.error) ? (
        <ErrorFace testId="my-bid-results-error" onRetry={() => void results.refetch()} />
      ) : null}
      {results.isPending ? <Skeleton testId="aggregate-loading" rows={3} /> : null}
      {results.isSuccess ? <ResultsAggregate aggregate={results.data.aggregate} /> : null}

      <PendingSlots />
    </section>
  );
}

import { useQueries, useQuery } from '@tanstack/react-query';

import { isUnauthorized as isSessionGone } from '../api/apiError';
import { listBidCases, readMyBidResults, readOpeningResult, type BidCase } from '../api/queries';
import { bidCasesQueryKey, MY_BID_RESULTS_QUERY_KEY, openingResultQueryKey } from '../api/queryKeys';
import { EmptyFace, ErrorFace, Skeleton } from '../components/ScreenFaces';
import { isUnauthorized, SignedOutFace } from '../components/WriteFailure';
import { caseResultsById, type CaseResult } from '../display/myBidResultDisplay';
import { RESULT_PENDING_SLOTS, isResultStage } from '../display/resultDisplay';
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
      <h3 className="text-sm font-medium">다루지 않는 자리</h3>
      <dl className="mt-2 grid gap-2 sm:grid-cols-2">
        {RESULT_PENDING_SLOTS.map((slot) => (
          <div key={slot.id} data-testid={`pending-${slot.id}`} className="flex flex-col gap-0.5">
            <dt className="text-xs font-medium">
              {slot.label}
              <span className="ml-1 text-absent-ink">{slot.kind === 'LATER' ? '준비 중' : '다루지 않음'}</span>
            </dt>
            {/* 사유를 **함께** 적는다 — 「준비 중」만 적으면 영영 안 오는 자리와 같아 보인다. */}
            <dd className="text-xs text-ink-soft">{slot.why}</dd>
          </div>
        ))}
      </dl>
    </section>
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
    const found = byId.get(caseId);
    return found === undefined ? { kind: 'MISSING' } : { kind: 'FOUND', result: found };
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
      <h2 className="text-lg font-semibold">결과 확인</h2>

      {cases.isPending ? <Skeleton testId="results-loading" /> : null}
      {cases.isError && !isUnauthorized(cases.error) ? (
        <ErrorFace testId="results-error" onRetry={() => void cases.refetch()} />
      ) : null}
      {cases.isSuccess && shown.length === 0 ? (
        <EmptyFace display={EMPTY_DISPLAY} testId="results-empty" />
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

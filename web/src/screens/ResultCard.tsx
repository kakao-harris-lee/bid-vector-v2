import type { UseQueryResult } from '@tanstack/react-query';
import { Link } from 'react-router';

import { isUnauthorized } from '../api/apiError';
import type { BidCase, OpeningResultView } from '../api/queries';
import { ErrorFace, Skeleton } from '../components/ScreenFaces';
import { StateBadge } from '../components/StateBadge';
import { tabOf } from '../display/bidBoxDisplay';
import { BID_CASE_STATE_DISPLAY, NEXT_CHECKPOINT_DISPLAY } from '../display/caseDisplay';
import {
  OPENING_RESULT_STATE_DISPLAY,
  PROVISIONAL_RANK_ONE_DISPLAY,
  confirmedAwardReading,
  provisionalRankOneReading,
  type MoneyReading,
} from '../display/openingResultDisplay';
import { displayOrUnknown } from '../display/stateDisplay';

/** 값이 없는 자리의 **한 문면** — 화면 D·C 와 같은 말이다(같은 뜻에 두 말을 두지 않는다). */
const ABSENT = '값 없음';

/** 원 단위 정수를 자릿점과 함께. **단위·통화는 이 함수가 붙이지 않는다** — 부르는 자리가 칸으로 싣는다. */
function amountText(amountWon: number): string {
  return amountWon.toLocaleString('ko-KR');
}

function AbsentText() {
  return <span className="text-absent-ink">{ABSENT}</span>;
}

/**
 * 금액 한 축을 그린다 — **세 갈래가 다른 얼굴이다**(`MoneyReading`).
 *
 * `INCOMPLETE` 에서 **숫자를 그리지 않는 것**이 이 컴포넌트의 일이다: 통화·basis·과세·
 * provenance 없이 숫자만 보이면 운영자가 그 넷을 짐작한다. 대신 빠진 칸의 이름을 말한다 —
 * 그것이 서버가 계약을 어긴 자리라 진단이 되어야 한다.
 */
function MoneyAxis<T extends { readonly amountWon: number }>({
  reading,
  testId,
  parts,
}: {
  readonly reading: MoneyReading<T>;
  readonly testId: string;
  /** 금액과 **함께 실리는 칸들** — 축마다 수가 다르다(확정 다섯 · 잠정 셋). */
  readonly parts: (value: T) => readonly string[];
}) {
  switch (reading.kind) {
    case 'ABSENT':
      return (
        <span data-testid={testId}>
          <AbsentText />
        </span>
      );
    case 'INCOMPLETE':
      return (
        <span data-testid={testId} className="text-unjudged-ink text-xs">
          일부 칸이 비어 금액을 보이지 않습니다 — 빠진 칸: {reading.missing.join(', ')}
        </span>
      );
    case 'PRESENT':
      return (
        <span data-testid={testId} className="flex flex-wrap items-baseline gap-1">
          <span className="font-numeric text-sm font-medium">{amountText(reading.value.amountWon)}</span>
          {parts(reading.value).map((part) => (
            <span key={part} className="text-xs text-ink-soft">
              {part}
            </span>
          ))}
        </span>
      );
    default: {
      const unreachable: never = reading;
      throw new Error(`모르는 금액 읽기: ${JSON.stringify(unreachable)}`);
    }
  }
}

/**
 * 결과 카드의 칸 하나. **이름이 `ResultField` 인 이유**(r1 CR L-4): `components/StateBadge.tsx`
 * 에 같은 이름의 공용 `Field` 가 있고 계약이 다르다(`value: string | null` — 배지를 담지 못해
 * 이 지역 판이 생겼다). 이름이 겹치면 읽는 사람이 `aria-describedby` 배선까지 같다고 믿는다.
 */
function ResultField({ label, children }: { readonly label: string; readonly children: React.ReactNode }) {
  return (
    <div className="flex flex-col gap-0.5">
      <dt className="text-xs text-ink-soft">{label}</dt>
      <dd className="text-sm">{children}</dd>
    </div>
  );
}

/** 개찰 결과 절 — **값은 전부 `readOpeningResult` 의 칸**이고 화면이 세거나 고르지 않는다. */
function OpeningSection({ caseId, view }: { readonly caseId: number; readonly view: OpeningResultView }) {
  return (
    <dl className="mt-3 grid gap-3 border-t border-line pt-3 sm:grid-cols-2">
      <ResultField label="개찰 결과">
        <StateBadge
          display={displayOrUnknown(OPENING_RESULT_STATE_DISPLAY, view.state)}
          testId={`opening-state-${caseId}`}
        />
      </ResultField>
      <ResultField label="잠정 1위 축">
        <StateBadge
          display={displayOrUnknown(PROVISIONAL_RANK_ONE_DISPLAY, view.provisionalRankOneState)}
          testId={`provisional-state-${caseId}`}
        />
      </ResultField>
      <ResultField label="순위 1 중복 건수">
        {/* 계약이 「동값을 임의로 깨지 않는다, **건수는 `provisionalRankOneDuplicateCount`**」라
            적는다 — 「여럿」만 보이고 몇인지를 버리면 그 칸이 사라진다(r1 CR M-3). */}
        <span data-testid={`provisional-duplicate-${caseId}`} className="font-numeric">
          {view.provisionalRankOneDuplicateCount === null ||
          view.provisionalRankOneDuplicateCount === undefined ? (
            <AbsentText />
          ) : (
            view.provisionalRankOneDuplicateCount
          )}
        </span>
      </ResultField>
      <ResultField label="참가자 수">
        <span data-testid={`participants-${caseId}`} className="font-numeric">
          {view.participantCount === null || view.participantCount === undefined ? (
            <AbsentText />
          ) : (
            view.participantCount
          )}
        </span>
      </ResultField>
      <ResultField label="결과 축 관측 시각">
        {/* 계약: 「시각은 모두 관측 시각이고 … 소비자가 **낡음을 스스로 판정**한다」. 이 칸이
            없으면 확정 축의 낡음을 잴 재료가 없다(r1 CR M-3). */}
        <span data-testid={`observed-at-${caseId}`}>
          {view.observedAt === null || view.observedAt === undefined ? (
            <AbsentText />
          ) : (
            <time dateTime={view.observedAt} className="font-numeric">
              {view.observedAt}
            </time>
          )}
        </span>
      </ResultField>
      <ResultField label="개찰 시각">
        <span data-testid={`opening-at-${caseId}`}>
          {view.actualOpeningAt === null || view.actualOpeningAt === undefined ? (
            <AbsentText />
          ) : (
            <time dateTime={view.actualOpeningAt} className="font-numeric">
              {view.actualOpeningAt}
            </time>
          )}
        </span>
      </ResultField>

      {/* **확정과 잠정은 다른 칸이다**(SET-02). 두 읽기가 서로의 칸을 보지 않으므로 한쪽이
          비었을 때 다른 쪽 값이 들어설 자리가 없다 — 그 보증은 아래 JSX 가 아니라
          `confirmedAwardReading`·`provisionalRankOneReading` 이 진다. */}
      <ResultField label="확정 낙찰가">
        <MoneyAxis
          reading={confirmedAwardReading(view)}
          testId={`confirmed-award-${caseId}`}
          parts={(value) => [value.currency, value.basis, value.vatTreatment, value.provenance]}
        />
      </ResultField>
      <ResultField label="잠정 1위 투찰가">
        <MoneyAxis
          reading={provisionalRankOneReading(view)}
          testId={`provisional-award-${caseId}`}
          parts={(value) => [value.currency, value.vatTreatment]}
        />
      </ResultField>
      <ResultField label="잠정 1위 투찰율">
        {/* 서버가 **문자열로** 싣는 값이다 — 숫자로 바꾸면 소수 자리를 잃는다(계약). */}
        <span data-testid={`provisional-rate-${caseId}`} className="font-numeric">
          {view.provisionalRankOneBidRateFraction === null ||
          view.provisionalRankOneBidRateFraction === undefined ? (
            <AbsentText />
          ) : (
            view.provisionalRankOneBidRateFraction
          )}
        </span>
      </ResultField>
      <ResultField label="잠정 축 관측 시각">
        <span data-testid={`provisional-observed-${caseId}`}>
          {view.provisionalRankOneObservedAt === null || view.provisionalRankOneObservedAt === undefined ? (
            <AbsentText />
          ) : (
            <time dateTime={view.provisionalRankOneObservedAt} className="font-numeric">
              {view.provisionalRankOneObservedAt}
            </time>
          )}
        </span>
      </ResultField>
    </dl>
  );
}

/**
 * 결과 단계 건 하나의 카드(M7/7E-3a).
 *
 * **전이 버튼이 없다**(D-7E3a-8) — E 는 읽기 화면이고, 다음 행동은 그 전이를 가진 화면으로
 * 가는 링크 둘이다. 전이표의 사본을 여기 두면 D 의 표와 갈리고, 여정 ④⑤ 가 같은 건을 민다.
 */
export function ResultCard({
  bidCase,
  opening,
}: {
  readonly bidCase: BidCase;
  /** 개찰 조회 결과 — **화면이 든다**(r3 CR L-1). 카드는 그린다. */
  readonly opening: UseQueryResult<OpeningResultView>;
}) {
  return (
    <li data-testid={`result-case-${bidCase.caseId}`} className="rounded-lg border border-line bg-card p-4">
      <div className="flex flex-wrap items-center gap-2">
        <span className="font-numeric text-sm font-medium">{bidCase.noticeNumber}</span>
        <span className="text-xs text-ink-soft">차수 {bidCase.noticeRound}</span>
        <StateBadge
          display={displayOrUnknown(BID_CASE_STATE_DISPLAY, bidCase.state)}
          testId={`result-state-${bidCase.caseId}`}
        />
      </div>

      <dl className="mt-3 grid gap-3 sm:grid-cols-2">
        <ResultField label="다음 확인 시점">
          {/* 「없음」은 빈 값이 아니라 **답**이다(D-7E3a-7 · §6.1). */}
          <StateBadge
            display={displayOrUnknown(NEXT_CHECKPOINT_DISPLAY, bidCase.nextCheckpointKind)}
            testId={`result-checkpoint-kind-${bidCase.caseId}`}
          />
        </ResultField>
        <ResultField label="시점">
          <span data-testid={`result-checkpoint-at-${bidCase.caseId}`}>
            {bidCase.nextCheckpointAt === null || bidCase.nextCheckpointAt === undefined ? (
              <AbsentText />
            ) : (
              <time dateTime={bidCase.nextCheckpointAt} className="font-numeric">
                {bidCase.nextCheckpointAt}
              </time>
            )}
          </span>
        </ResultField>
      </dl>

      {opening.isPending ? <Skeleton testId={`opening-loading-${bidCase.caseId}`} rows={2} /> : null}
      {/* **세션이 없다는 답은 재시도로 풀리지 않는다**(r1 CR L-5) — 화면의 규율이 목록 쪽에만
          걸려 있어 카드 쪽 401 이 「다시 시도」 버튼을 달았다(`retry` 까지 더해 더 때린다). */}
      {/* 401 의 **얼굴은 화면이 하나로** 그린다(r3 CR L-1) — 카드는 그 사실만 조용히 적는다.
          앞 판은 카드마다 `SignedOutFace` 를 그려 같은 `role="alert"` 이 N 개 섰다. */}
      {opening.isError && isUnauthorized(opening.error) ? (
        <p data-testid={`opening-signed-out-${bidCase.caseId}`} className="mt-3 text-xs text-absent-ink">
          세션이 없어 개찰 결과를 읽지 못했습니다.
        </p>
      ) : null}
      {opening.isError && !isUnauthorized(opening.error) ? (
        <ErrorFace testId={`opening-error-${bidCase.caseId}`} onRetry={() => void opening.refetch()} />
      ) : null}
      {opening.isSuccess ? <OpeningSection caseId={bidCase.caseId} view={opening.data} /> : null}

      {/* 다음 행동 둘 — **전이가 아니라 이동**이다(D-7E3a-8). */}
      <div className="mt-3 flex flex-wrap gap-2 text-sm">
        {/* **그 건이 보이는 탭으로** 보낸다(D-7E3a-8 · r1 M-2). 맨 `/bids` 는 첫 탭(관심)을 열고
            결과 단계 건은 거기 없다 — 운영자가 탭을 스스로 찾아야 「다음 행동」에 닿는다. */}
        <Link
          to={`/bids?tab=${tabOf(bidCase.state)}`}
          className="rounded-md border border-line px-3 py-1 hover:bg-neutral-skin"
        >
          입찰함에서 기록
        </Link>
        <Link
          to={`/notices/${encodeURIComponent(bidCase.noticeNumber)}/${encodeURIComponent(bidCase.noticeRound)}`}
          className="rounded-md border border-line px-3 py-1 hover:bg-neutral-skin"
        >
          공고 워크시트
        </Link>
      </div>
    </li>
  );
}

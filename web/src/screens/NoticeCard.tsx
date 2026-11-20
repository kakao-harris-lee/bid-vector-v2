import type { ReactNode } from 'react';
import { Link } from 'react-router';

import type { NoticeSummary, SuggestedNotice } from '../api/queries';
import { AbsentValue } from '../components/AbsentValue';
import { Card } from '../components/Card';
import { StateBadge } from '../components/StateBadge';
import { useTermHint } from '../components/KeyValueRow';
import { compactAmountTextOf } from '../display/moneyDisplay';
import { amountDisplayOf, noticeStatusDisplayOf, SUGGESTION_LICENSE_VERDICT_DISPLAY } from '../display/noticeDisplay';
import { displayOrUnknown } from '../display/stateDisplay';
import { dayCountText, daysUntil } from '../display/timeDisplay';
import { UNJUDGED_AXIS_DISPLAY } from '../display/worksheetDisplay';

/** 워크시트 주소 — 공고번호·차수가 **경로의 두 칸**이다(식별자를 한 문자열로 접지 않는다). */
export function worksheetPathOf(notice: Pick<NoticeSummary, 'noticeNumber' | 'noticeRound'>): string {
  return `/notices/${encodeURIComponent(notice.noticeNumber)}/${encodeURIComponent(notice.noticeRound)}`;
}

/**
 * 숫자의 **뜻**을 말하는 유일한 산출물 문면. 셈이 KST 달력일 차로 바뀌었으므로 이 자리도 그
 * 말을 쓴다 — 앞 판은 「남은 날(내림)」이었고 그것은 **사라진 기계**를 가리킨다. 「남은」도 덜
 * 맞는다: 새 정의에는 거울상(`D+1` — 마감일이 어제)이 있다.
 */
const DEADLINE_HINT = '마감일까지의 KST 달력일 차';

/**
 * 남은 날 — **숫자만** 낸다. 「마감됨」 같은 상태 말은 만들지 않는다(그것은 `status` 가 한다).
 *
 * **이미 센 날 수를 받는다**(M9 D-4): `*At` 원문이 JSX 를 지나면 게이트가 멈춘다. 세는 일과
 * 문면을 만드는 일은 둘 다 `timeDisplay` 가 한다 — 이 컴포넌트는 그릴 뿐이다.
 */
function DeadlineBadge({ days }: { readonly days: number | null }) {
  const text = dayCountText(days);
  if (text === null) {
    return <span className="text-xs text-absent-ink">마감 없음</span>;
  }
  return (
    <span data-testid="notice-deadline" className="font-numeric text-xs" title={DEADLINE_HINT}>
      {text}
    </span>
  );
}

/** 혼동하면 투찰 금액이 틀리는 문면 — 용어와 같은 자리에 둔다. */
const AMOUNT_HINT = '투찰 기준 금액. 추정가격과 다르다.';

/**
 * 좁은 화면의 금액 **축 이름**(와이어프레임의 「기초 4.2억」). 넓은 화면의 `dt` 는 「기초금액」
 * 전체를 쓰고, 좁은 쪽은 와이어프레임이 줄인 그 말을 쓴다 — 둘은 **같은 축**이고 길이만 다르다.
 */
const COMPACT_AMOUNT_LABEL = '기초';

/**
 * 좁은 화면의 **압축 금액**(M14/14B · 와이어프레임 모바일 카드의 「기초 4.2억」).
 *
 * 자리를 버린 읽기라 **정확 금액에 닿는 길을 카드 안에 남긴다** — 2층 라벨의 아래층이 그
 * 길이고 배선은 `useTermHint` 한 자리다(같은 꼴을 손으로 또 지으면 그 둘이 갈린다).
 *
 * **넓은 화면에는 서지 않는다**(`md:hidden`). 거기서는 아래의 `dl` 이 정확 금액과 부속 세 축을
 * 다 보이므로, 같은 값을 두 꼴로 겹쳐 두면 어느 쪽이 금액인지 흐려진다.
 */
function CompactAmount({ notice }: { readonly notice: NoticeSummary }) {
  const won = notice.baseAmountWon;
  const compact = won === null || won === undefined ? null : compactAmountTextOf(won, notice.baseAmountCurrency);
  const amount = amountDisplayOf(notice);
  // 아래층은 **정확 금액 + 부속 축**이다 — 좁은 화면은 `dl` 을 숨기므로 그 사실이 여기로 온다.
  const exact = amount.provenance === null ? amount.amount : `${amount.amount} · ${amount.provenance}`;
  const hint = useTermHint(compact === null ? undefined : exact, { testId: 'notice-amount-compact' });

  return (
    <span className="inline-flex flex-col items-start text-xs md:hidden">
      {/*
        **금액의 축을 좁은 화면에서도 말한다**(v M-2 · 와이어프레임의 「기초 4.2억」). 압축 글자만
        두면 그 수가 무슨 금액인지 탭하기 전에는 알 수 없는데, 기초금액과 추정가격을 혼동하면
        투찰 금액이 틀린다(`AMOUNT_HINT` 가 적은 그 자리다).
      */}
      <span className="text-ink-soft">{COMPACT_AMOUNT_LABEL}</span>
      {/*
        **읽을 수 없는 금액은 사라지지 않고 사유를 말한다**(v M-2). 압축 자리를 통째로 비우면
        좁은 화면에서 「금액이 없다」와 「배치가 숨겼다」가 같은 얼굴이 된다 — 정확 금액의 부재
        어휘는 `md` 이상에서만 서는 `dl` 안에 있기 때문이다. 「약 0원」을 지어내지 않는 규율은
        그대로다: 지어내는 것이 아니라 **없다고 말하는** 것이다.
      */}
      {compact === null ? (
        <AbsentValue reason="notSent" testId="notice-amount-compact-absent" />
      ) : (
        <>
          {hint.termNode(compact)}
          {hint.hintNode}
        </>
      )}
    </span>
  );
}

/**
 * 카드 한 장의 **배치**(M14/14B · 와이어프레임 B). 제목 + 판정 배지 / 기관 · 기초금액 /
 * 오른쪽에 D-day 와 다음 행동 하나.
 *
 * 검색 결과와 제안이 **같은 배치**를 쓴다(§6.2 B). 둘을 가르는 것은 `verdict` 슬롯 하나다 —
 * 제안은 서버가 준 면허 판정을, 검색 결과는 「여기서는 판정하지 않는다」는 사실을 든다. 그
 * 자리를 **비워 두지 않는 것**이 요점이다: 빈 자리는 「판정했는데 통과」로 읽힌다.
 *
 * 내보내지 않는다 — 어휘 게이트의 카드 모집단은 **모듈의 수출**에서 유도되므로, 안쪽 배치
 * 조각을 수출하면 마운트 의무가 하나 더 생기고 그것은 같은 두 카드를 두 번 재는 일이다.
 */
function NoticeCardBody({
  notice,
  now,
  action,
  verdict,
  footer,
}: {
  readonly notice: NoticeSummary;
  readonly now: Date;
  readonly action: ReactNode;
  readonly verdict: ReactNode;
  readonly footer?: ReactNode;
}) {
  const amount = amountDisplayOf(notice);
  const amountHint = useTermHint(AMOUNT_HINT);
  return (
    <div className="flex flex-wrap items-start justify-between gap-x-3 gap-y-2">
      <div className="flex min-w-0 flex-1 flex-col gap-1">
        {/*
          **제목과 판정이 한 줄**이다(와이어프레임 B). 좁아지면 `flex-wrap` 이 배지를 아래로
          내린다 — 배치를 CSS 로 뒤집지 않으므로 읽는 차례와 보는 차례가 갈리지 않는다.

          제목은 **블록 타깃**이다(M13/13B) — 모바일에서 손가락이 닿는 것은 글자가 아니라 상자다.
          이동 대상과 동작은 그대로이고 상자만 커진다.
        */}
        <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
          <Link
            to={worksheetPathOf(notice)}
            className="flex min-h-11 min-w-0 items-center rounded-md hover:underline"
          >
            <span data-testid="notice-title" className="block text-sm font-semibold break-words">
              {notice.title ?? notice.noticeNumber}
            </span>
          </Link>
          {verdict}
          <StateBadge display={noticeStatusDisplayOf(notice.status)} testId="notice-status" />
          <CompactAmount notice={notice} />
        </div>
        {/*
          **넓은 화면의 사실 줄**. 좁은 화면에서는 압축 금액이 그 일을 하므로 숨긴다 —
          와이어프레임의 모바일 카드가 기관을 싣지 않는 자리이고, 기관과 부속 축은 제목을 눌러
          워크시트에서 본다.

          「방식」(적격심사 · 복수예가) 칸은 **그리지 않는다**: 계약의 응답 스키마에 그 칸이
          없다(nullable 도 아니다). 부재 사유를 지어 그리면 화면이 「서버가 방식을 빚졌다」고
          주장하게 된다 — 대조표 B 행의 처분이 「방식 = 데이터로 막힘」이다.
        */}
        <dl className="hidden flex-wrap items-baseline gap-x-2 gap-y-1 text-sm md:flex">
          {/* 발주기관은 **값만** 보인다(와이어프레임의 사실 줄) — 라벨은 보조기기에만 남긴다.
              `dl` 의 내용 모형을 지키려면 `dt` 가 있어야 하고, 그 글자를 눈에까지 두면 와이어프레임
              한 줄이 라벨 둘로 늘어난다. */}
          <div className="flex min-w-0 items-baseline gap-1">
            <dt className="sr-only">발주기관</dt>
            <dd data-testid="notice-agency" className="break-words">
              {notice.demandAgencyName ?? notice.noticeAgencyName ?? <AbsentValue reason="notSent" />}
            </dd>
          </div>
          <span aria-hidden="true" className="text-ink-soft">
            ·
          </span>
          <div className="flex min-w-0 flex-wrap items-baseline gap-1">
            {/* CR r3 M-5 · M14/14A(M13 잔여 ⓖ) — 같은 문면의 **세 번째 자리**였다. `title` 하나로
                두면 터치·보조기기에서 볼 수 없다. 이제 라벨이 누를 수 있는 자리이고 배선은
                `useTermHint` 한 자리다. */}
            <dt className="text-xs text-ink-soft">{amountHint.termNode('기초금액')}</dt>
            <dd aria-describedby={amountHint.describedBy} className="font-numeric">
              {/* 값은 **자기 요소**가 들고 아래층 문면은 그 옆에 둔다 — 값 단언이 설명까지 읽지
                  않게 하면서 `dl` 내용 모형도 지킨다(한 용어에 `<dd>` 하나). */}
              <span data-testid="notice-amount">{amount.amount}</span>
              {amountHint.hintNode}
            </dd>
            {/* CR L-7 — provenance 가 없으면 **빈 `<dd>` 를 내지 않는다**(빈 칸이 값처럼 읽힌다). */}
            {amount.provenance === null ? null : (
              <dd data-testid="notice-amount-provenance" className="text-xs text-ink-soft">
                {amount.provenance}
              </dd>
            )}
          </div>
        </dl>
        {footer}
      </div>
      {/*
        **오른쪽 묶음**(계약 14B 행) — D-day 와 다음 행동 하나가 한 자리에 선다. 좁은 화면에서는
        `flex-wrap` 이 이 묶음을 아래로 내린다(배치를 CSS 로 뒤집지 않으므로 읽는 차례와 보는
        차례가 갈리지 않는다).
      */}
      <div data-testid="notice-aside" className="flex flex-wrap items-center justify-end gap-2">
        <DeadlineBadge days={daysUntil(notice.deadlineAt, now)} />
        {action}
      </div>
    </div>
  );
}

/**
 * **검색 결과 카드의 자격 자리**(계약 「범위 밖」 · M13 승계). 새 판정 축이라 이 slice 가 값을
 * 만들지 않는다 — 그러나 **빈 자리로 두지 않는다**: 와이어프레임의 그 자리에 아무것도 없으면
 * 운영자가 「판정했고 통과」로 읽는다. 어디서 판정이 나오는지를 말한다.
 */
const SEARCH_QUALIFICATION_COPY = '제안 목록에서만 판정합니다';

/**
 * 공고 카드 한 줄(§6.2 B) — 제목 · 발주기관 · 기초금액(+provenance) · D-day · 상태 · 다음
 * 행동 하나. 모든 값은 **응답 필드 그대로**이고 카드가 계산하는 것은 남은 날뿐이다.
 */
export function NoticeCard({
  notice,
  now,
  action,
}: {
  readonly notice: NoticeSummary;
  readonly now: Date;
  readonly action: ReactNode;
}) {
  return (
    <Card as="li" testId={`notice-card-${notice.noticeNumber}`}>
      <NoticeCardBody
        notice={notice}
        now={now}
        action={action}
        verdict={
          <span data-testid="notice-qualification" className="text-xs text-unjudged-ink">
            {SEARCH_QUALIFICATION_COPY}
          </span>
        }
      />
    </Card>
  );
}

/**
 * 제안 카드 — 공고 카드와 **같은 배치**에 면허 축 판정을 더한다(§6.2 B 「검색 결과와 제안
 * 목록이 같은 카드」). 지역·실적·시공능력은 아직 판정하지 않으므로 그 사실을 함께 싣는다.
 */
export function SuggestionCard({
  suggestion,
  now,
  action,
}: {
  readonly suggestion: SuggestedNotice;
  readonly now: Date;
  readonly action: ReactNode;
}) {
  return (
    <Card as="li" testId={`suggestion-card-${suggestion.noticeNumber}`}>
      <NoticeCardBody
        notice={suggestion}
        now={now}
        action={action}
        verdict={
          <StateBadge
            display={displayOrUnknown(SUGGESTION_LICENSE_VERDICT_DISPLAY, suggestion.licenseVerdict)}
            testId="suggestion-license"
          />
        }
        footer={
          <p data-testid="suggestion-unjudged" className="mt-1 flex flex-wrap gap-1">
            {suggestion.unjudgedAxes.map((axis) => (
              <StateBadge key={axis} display={displayOrUnknown(UNJUDGED_AXIS_DISPLAY, axis)} />
            ))}
          </p>
        }
      />
    </Card>
  );
}

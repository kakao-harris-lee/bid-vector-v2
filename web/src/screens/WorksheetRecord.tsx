import { useId, useState } from 'react';

import { Button, controlClass } from '../components/Button';
import { cardClass } from '../components/Card';
import { StateBadge } from '../components/StateBadge';
import {
  CRITICAL_RATE_STANDING_DISPLAY,
  FLOOR_MARGIN_REASON_DISPLAY,
  type FloorMargin,
} from '../display/floorMargin';
import { VAT_TREATMENT_DISPLAY, koreanAmountTextOf, type VatTreatment } from '../display/moneyDisplay';

/** 나라장터 입찰공고 상세 — **이동만** 한다(D-UX-2: 자동 제출은 범위 밖이고 상한이 「이동 + 복사」다). */
const KONEPS_NOTICE_URL = 'https://www.g2b.go.kr/index.jsp';

export type { VatTreatment };

/**
 * 과세 구분은 **운영자가 고른다** — 기본값을 지어내면 화면이 그 사실을 아는 척하게 된다.
 *
 * 라벨을 **표에서 끌어온다**(M7/7E-3b r1 — CR M-1): 앞 판은 여기에 한글 라벨을 적고 결과 화면은
 * 원문 토큰을 보여, 운영자가 고른 말과 돌아오는 말이 달랐다. 목록도 표의 선언 순서를 그대로 쓴다.
 */
export const VAT_CHOICES: readonly { readonly value: VatTreatment; readonly label: string }[] = (
  Object.keys(VAT_TREATMENT_DISPLAY) as readonly VatTreatment[]
).map((value) => ({ value, label: VAT_TREATMENT_DISPLAY[value].label }));

export interface BidAmountForm {
  readonly amount: string;
  readonly vat: VatTreatment | '';
  readonly drawNumbers: string;
  readonly memo: string;
}

export const EMPTY_BID_FORM: BidAmountForm = { amount: '', vat: '', drawNumbers: '', memo: '' };

/** 원 단위 정수만 받는다 — 쉼표·공백은 걷고, 숫자가 아니면 `null`(지어내지 않는다). */
export function parseWon(raw: string): number | null {
  const digits = raw.replaceAll(',', '').trim();
  if (!/^\d+$/.test(digits)) {
    return null;
  }
  return Number.parseInt(digits, 10);
}

/** 계약의 정수 범위(int32) — 넘는 선언은 서버 400 이 되므로 입력에서 가른다. */
const MAX_DECLARED_COUNT = 2_147_483_647;

/**
 * 진행 중 투찰 건수의 선언 — **0 을 기본값으로 두지 않는다**(CR H-1 · v M-5). 계약이 「호출자가
 * 선언한다, 서버가 0 으로 지어내지 않는다」로 둔 칸이고, 그 수를 **서버가 세어 주는 표면이 계약에
 * 없다**(실측: `listBidCases` 에 집계도 `X-Total-Count` 도 없다). 클라이언트가 상태를 「진행 중」
 * 으로 분류해 세는 것은 계약에 없는 정의를 화면이 만드는 것이라 D-7E-4 위반이다. 그래서 운영자가
 * 적고, 적지 않으면 요청하지 않는다.
 *
 * **왜 `null` 하나가 아닌가**(CR r3 L-4): 「적지 않았다」와 「범위를 넘었다」는 운영자가 할 일이
 * 다르다. 하나로 접으면 21자리를 적은 사람에게 「0 이상의 정수로 적으라」는 **틀린 말**을 한다.
 */
export type DeclaredCount =
  | { readonly kind: 'declared'; readonly value: number }
  | { readonly kind: 'not-declared' }
  | { readonly kind: 'out-of-range'; readonly max: number };

export function parseCount(raw: string): DeclaredCount {
  const digits = raw.trim();
  if (!/^\d+$/.test(digits)) {
    return { kind: 'not-declared' };
  }
  const parsed = Number.parseInt(digits, 10);
  if (!Number.isSafeInteger(parsed) || parsed > MAX_DECLARED_COUNT) {
    return { kind: 'out-of-range', max: MAX_DECLARED_COUNT };
  }
  return { kind: 'declared', value: parsed };
}

/** 추첨번호는 정수 여럿 — 빈 칸이면 빈 배열이고 형식이 어긋나면 `null` 이다. */
export function parseDrawNumbers(raw: string): number[] | null {
  const trimmed = raw.trim();
  if (trimmed === '') {
    return [];
  }
  const parts = trimmed.split(/[\s,]+/u);
  const parsed = parts.map((part) => (/^\d+$/.test(part) ? Number.parseInt(part, 10) : Number.NaN));
  return parsed.some(Number.isNaN) ? null : parsed;
}

/**
 * 진행 중 투찰 건수를 **선언하는 자리**. 요청의 전제이므로 투찰가 요청 버튼 옆에 둔다.
 * 기본값이 없고, 비어 있으면 요청 경로가 막힌다.
 */
export function ActiveBidsDeclaration({
  value,
  onChange,
  blocked,
}: {
  readonly value: string;
  readonly onChange: (next: string) => void;
  readonly blocked: string | null;
}) {
  // CR r2 L-7 — 안내 문면을 입력에 **프로그램적으로** 묶고, 차단 상태를 `aria-invalid` 로 드러낸다.
  const generatedId = useId();
  const noteId = `active-bids-note-${generatedId}`;
  return (
    <div className="mt-3 flex flex-col gap-1">
      <label className="flex flex-col gap-1">
        <span className="text-xs text-ink-soft">
          진행 중 투찰 건수(선언)
        </span>
        <input
          data-testid="active-bids"
          name="currentActiveBids"
          inputMode="numeric"
          value={value}
          aria-describedby={noteId}
          aria-invalid={blocked === null ? undefined : true}
          onChange={(event) => onChange(event.target.value)}
          className="min-h-11 w-32 rounded-md border border-line bg-card px-3 font-numeric text-sm"
        />
      </label>
      <p id={noteId} className="text-xs text-ink-soft">
        여력 축이 이 값을 씁니다. 서버도 화면도 이 수를 지어내지 않으므로 직접 적으십시오.
      </p>
      {blocked === null ? null : (
        <p data-testid="active-bids-blocked" role="alert" className="text-xs text-bad-ink">
          {blocked}
        </p>
      )}
    </div>
  );
}

/**
 * **복사의 결과는 그 금액에 매여 있다** — 금액을 고치면 앞의 「복사했습니다」가 다른 수를
 * 가리킨다. 그래서 상태에 **복사한 금액**을 함께 담고 지금 금액과 같을 때만 보인다(지우는
 * 시점을 따로 두면 그 시점이 빠지는 자리가 생긴다).
 */
interface CopyAttempt {
  readonly amountWon: number;
  readonly copied: boolean;
}

/**
 * 나라장터 이동 카드(§6.2 C 4) — 딥링크 + 투찰 입력값(**복사 · 한글 병기 · 하한 대비 여유**) +
 * 체크리스트. 체크리스트는 **정적 안내**이고 요구표가 「클라이언트」로 판정한 자리다.
 *
 * 이 화면은 대신 제출하지 않으므로(D-UX-2 — 상한이 「이동 + 복사」다) 운영자가 금액을 손으로
 * 옮긴다. 그 길에서 나는 실수가 가이드 실측의 첫 두 줄이다 — 「0 을 하나 더/덜」과 「하한 미달
 * 즉시 실격」. 이 카드의 세 자리가 그 둘을 겨눈다.
 */
export function KonepsHandoff({
  noticeNumber,
  amountWon,
  margin,
}: {
  readonly noticeNumber: string;
  readonly amountWon: number | null;
  /** 투찰가를 요청하기 **전에는 자리 자체가 없다** — 하한율이 아직 응답에 없다. */
  readonly margin: FloorMargin | null;
}) {
  const query = new URLSearchParams({ bidNtceNo: noticeNumber }).toString();
  const [attempt, setAttempt] = useState<CopyAttempt | null>(null);
  const shown = attempt !== null && attempt.amountWon === amountWon ? attempt : null;
  /**
   * **다룰 수 없는 수는 그리지도 복사하지도 않는다**(cr M-5). 입력 파서의 상한은 M7 자리라 이
   * slice 가 바꾸지 않지만, 안전 정수 밖 값은 이미 **운영자가 적지 않은 수**다 — 그것을 복사해
   * 나라장터에 붙이면 서버를 지나지 않는 경로로 틀린 금액이 나간다. 병기가 꺼지는 자리와 같은
   * 조건으로 묶어, 가장 많이 틀린 수에서 표지가 함께 꺼지지 않게 한다.
   */
  const usable = amountWon !== null && Number.isSafeInteger(amountWon);
  const korean = usable && amountWon !== null ? koreanAmountTextOf(amountWon) : null;

  async function copyAmount(): Promise<void> {
    if (amountWon === null || !usable) {
      return;
    }
    // 브라우저가 클립보드를 **주지 않을 수 있다**(비보안 출처 · 권한 거부 · 구형). 선언을 넓혀 그
    // 사실을 타입으로 들고, 없으면 거부와 **같은 길**로 간다 — 조용히 아무 일도 없지 않다.
    const clipboard: Clipboard | undefined = navigator.clipboard;
    if (clipboard === undefined) {
      setAttempt({ amountWon, copied: false });
      return;
    }
    try {
      await clipboard.writeText(`${amountWon}`);
      setAttempt({ amountWon, copied: true });
    } catch {
      setAttempt({ amountWon, copied: false });
    }
  }

  return (
    <section className={cardClass()}>
      <h3 className="text-sm font-semibold">나라장터에서 투찰하기</h3>
      <p className="mt-1 text-xs text-ink-soft">이 화면은 대신 제출하지 않습니다. 아래 금액을 복사해 직접 제출하십시오.</p>
      <a
        data-testid="koneps-deeplink"
        href={`${KONEPS_NOTICE_URL}?${query}`}
        target="_blank"
        rel="noopener noreferrer"
        className={`mt-3 ${controlClass('secondary', 'md')}`}
      >
        나라장터 열기
      </a>
      <dl className="mt-3 flex flex-col gap-1 text-sm">
        <dt className="text-xs text-ink-soft">투찰 입력값(원 단위 정수)</dt>
        <dd data-testid="handoff-amount" className="font-numeric">
          {amountWon === null ? (
            <span className="text-absent-ink">값 없음</span>
          ) : usable ? (
            amountWon.toLocaleString('ko-KR')
          ) : (
            <span className="text-bad-ink">적은 금액이 다룰 수 없는 크기입니다 — 자리 수를 다시 보십시오</span>
          )}
        </dd>
        {/* 병기는 **같은 수의 다른 읽기**다 — 자리 수를 세지 않아도 0 하나가 드러난다. */}
        {korean === null ? null : (
          <dd data-testid="handoff-amount-korean" className="text-xs text-ink-soft">
            {korean}
          </dd>
        )}
      </dl>
      <div className="mt-3 flex flex-wrap items-center gap-2">
        {/* **없는 값을 복사하지 않는다** — 금액을 적기 전에는 누를 자리가 막혀 있다. */}
        <Button
          testId="koneps-copy"
          onClick={() => {
            void copyAmount();
          }}
          disabled={!usable}
        >
          금액 복사
        </Button>
        {shown === null ? null : (
          <p
            data-testid="koneps-copy-status"
            role={shown.copied ? 'status' : 'alert'}
            className={`text-xs ${shown.copied ? 'text-ink-soft' : 'text-bad-ink'}`}
          >
            {shown.copied
              ? '복사했습니다 — 나라장터 입력칸에 붙여 넣으십시오.'
              : '복사하지 못했습니다 — 아래 값을 직접 고르십시오.'}
          </p>
        )}
      </div>
      {/* 거부에는 **손으로 할 길**이 있다. 포커스가 오면 전체를 골라 둔다. */}
      {shown === null || shown.copied || amountWon === null || !usable ? null : (
        <input
          data-testid="koneps-copy-fallback"
          type="text"
          readOnly
          value={`${amountWon}`}
          aria-label="투찰 입력값 원문"
          onFocus={(event) => event.currentTarget.select()}
          className="mt-2 min-h-11 w-full rounded-md border border-line bg-card px-3 font-numeric text-sm"
        />
      )}
      {margin === null ? null : (
        <dl className="mt-3 flex flex-col gap-1 text-sm">
          <dt className="text-xs text-ink-soft">하한 대비 여유</dt>
          <dd data-testid="floor-margin" className="flex flex-col gap-1">
            {margin.kind === 'measured' ? (
              <>
                <span className="flex flex-wrap items-center gap-2">
                  <StateBadge display={CRITICAL_RATE_STANDING_DISPLAY[margin.standing]} />
                  <span className="font-numeric text-sm">
                    임계 사정률 {margin.criticalRateText} ({margin.marginPercentText}%)
                  </span>
                </span>
                {/* **율 축으로 말한다** — 금액을 곱해 하한가를 짓지 않는다(폐기된 basis 혼합이다).
                    실현 사정률이 임계보다 크면 그때 하한 아래가 된다. */}
                <span className="text-xs text-ink-soft">
                  투찰율 <span className="font-numeric">{margin.bidRateText}</span> · 게시 하한율{' '}
                  <span className="font-numeric">{margin.floorRateText}</span>
                </span>
              </>
            ) : (
              // **재지 않는 자리는 빈 자리 + 사유**다 — 0% 나 대시로 접지 않는다(계약).
              <StateBadge display={FLOOR_MARGIN_REASON_DISPLAY[margin.reason]} />
            )}
          </dd>
        </dl>
      )}
      <ul className="mt-3 flex list-inside list-disc flex-col gap-1 text-xs text-ink-soft">
        <li>인증서 두 종(서명용·암호용)을 준비하십시오.</li>
        <li>추첨번호 두 개를 적어 두십시오.</li>
        <li>제출확인서를 저장하십시오.</li>
        <li>마감 한 시간 전에 제출을 끝내십시오.</li>
      </ul>
    </section>
  );
}

/**
 * 투찰 금액 폼 — 초안과 기록이 **같은 입력**을 쓴다(§6.2 C 3·5: 기록 확인은 초안이 채워진
 * 상태로 열린다). 3안이 아직 산출되지 않으므로 금액은 운영자가 적는다.
 *
 * **기본값을 지어내지 않는다**: 계약의 `BidCase` 에 초안 금액 칸이 없어 화면이 그 값을 되읽을
 * 수 없고(알려진 제한), 과세 구분에는 안전한 기본이 없다.
 */
export function BidAmountFields({
  form,
  onChange,
  blocked,
}: {
  readonly form: BidAmountForm;
  readonly onChange: (next: BidAmountForm) => void;
  readonly blocked: string | null;
}) {
  return (
    <div className="flex flex-col gap-3">
      <label className="flex flex-col gap-1">
        <span className="text-xs text-ink-soft">투찰 금액(원)</span>
        <input
          data-testid="record-amount"
          name="amountWon"
          inputMode="numeric"
          value={form.amount}
          onChange={(event) => onChange({ ...form, amount: event.target.value })}
          className="min-h-11 rounded-md border border-line bg-card px-3 font-numeric text-sm"
        />
      </label>
      <label className="flex flex-col gap-1">
        <span className="text-xs text-ink-soft">과세 구분</span>
        <select
          data-testid="record-vat"
          name="vatTreatment"
          value={form.vat}
          onChange={(event) => onChange({ ...form, vat: event.target.value as VatTreatment | '' })}
          className="min-h-11 rounded-md border border-line bg-card px-3 text-sm"
        >
          <option value="">고르십시오</option>
          {VAT_CHOICES.map((choice) => (
            <option key={choice.value} value={choice.value}>
              {choice.label}
            </option>
          ))}
        </select>
      </label>
      <label className="flex flex-col gap-1">
        <span className="text-xs text-ink-soft">추첨번호(쉼표·공백으로 구분, 선택)</span>
        <input
          data-testid="record-draw-numbers"
          name="drawNumbers"
          value={form.drawNumbers}
          onChange={(event) => onChange({ ...form, drawNumbers: event.target.value })}
          className="min-h-11 rounded-md border border-line bg-card px-3 font-numeric text-sm"
        />
      </label>
      <label className="flex flex-col gap-1">
        <span className="text-xs text-ink-soft">메모(선택)</span>
        <textarea
          data-testid="record-memo"
          name="memo"
          value={form.memo}
          onChange={(event) => onChange({ ...form, memo: event.target.value })}
          className="min-h-11 rounded-md border border-line bg-card px-3 text-sm"
          rows={2}
        />
      </label>
      {blocked === null ? null : (
        <p data-testid="record-blocked" role="alert" className="text-xs text-bad-ink">
          {blocked}
        </p>
      )}
    </div>
  );
}

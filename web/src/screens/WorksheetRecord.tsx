import { useId } from 'react';

import type { components } from '../api/generated/operator-api';

/** 나라장터 입찰공고 상세 — **이동만** 한다(D-UX-2: 자동 제출은 범위 밖이고 상한이 「이동 + 복사」다). */
const KONEPS_NOTICE_URL = 'https://www.g2b.go.kr/index.jsp';

export type VatTreatment = components['schemas']['RecordBidRequest']['vatTreatment'];

/** 과세 구분은 **운영자가 고른다** — 기본값을 지어내면 화면이 그 사실을 아는 척하게 된다. */
export const VAT_CHOICES: readonly { readonly value: VatTreatment; readonly label: string }[] = [
  { value: 'INCLUSIVE', label: '부가세 포함' },
  { value: 'EXCLUSIVE', label: '부가세 별도' },
  { value: 'UNKNOWN', label: '모름' },
];

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
          className="w-32 rounded-md border border-line bg-card px-3 py-2 font-numeric text-sm"
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
 * 나라장터 이동 카드(§6.2 C 4) — 딥링크 + 투찰 입력값 + 체크리스트. 체크리스트는 **정적
 * 안내**이고 요구표가 「클라이언트」로 판정한 자리다.
 */
export function KonepsHandoff({
  noticeNumber,
  amountWon,
}: {
  readonly noticeNumber: string;
  readonly amountWon: number | null;
}) {
  const query = new URLSearchParams({ bidNtceNo: noticeNumber }).toString();
  return (
    <section className="rounded-lg border border-line bg-card p-4">
      <h3 className="text-sm font-semibold">나라장터에서 투찰하기</h3>
      <p className="mt-1 text-xs text-ink-soft">이 화면은 대신 제출하지 않습니다. 금액을 복사해 직접 제출하십시오.</p>
      <a
        data-testid="koneps-deeplink"
        href={`${KONEPS_NOTICE_URL}?${query}`}
        target="_blank"
        rel="noopener noreferrer"
        className="mt-3 inline-block rounded-md border border-line px-3 py-2 text-sm hover:bg-neutral-skin"
      >
        나라장터 열기
      </a>
      <dl className="mt-3 flex flex-col gap-1 text-sm">
        <dt className="text-xs text-ink-soft">투찰 입력값(원 단위 정수)</dt>
        <dd data-testid="handoff-amount" className="font-numeric">
          {amountWon === null ? <span className="text-absent-ink">값 없음</span> : amountWon.toLocaleString('ko-KR')}
        </dd>
      </dl>
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
          className="rounded-md border border-line bg-card px-3 py-2 font-numeric text-sm"
        />
      </label>
      <label className="flex flex-col gap-1">
        <span className="text-xs text-ink-soft">과세 구분</span>
        <select
          data-testid="record-vat"
          name="vatTreatment"
          value={form.vat}
          onChange={(event) => onChange({ ...form, vat: event.target.value as VatTreatment | '' })}
          className="rounded-md border border-line bg-card px-3 py-2 text-sm"
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
          className="rounded-md border border-line bg-card px-3 py-2 font-numeric text-sm"
        />
      </label>
      <label className="flex flex-col gap-1">
        <span className="text-xs text-ink-soft">메모(선택)</span>
        <textarea
          data-testid="record-memo"
          name="memo"
          value={form.memo}
          onChange={(event) => onChange({ ...form, memo: event.target.value })}
          className="rounded-md border border-line bg-card px-3 py-2 text-sm"
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

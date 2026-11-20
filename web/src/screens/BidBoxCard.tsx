import { useEffect, useId, useRef, useState } from 'react';

import type { components } from '../api/generated/operator-api';
import type { BidCase } from '../api/queries';

type Outcome = components['schemas']['RecordOutcomeRequest']['outcome'];
import { AbsentValue } from '../components/AbsentValue';
import { Button, LinkButton, controlClass } from '../components/Button';
import { Card } from '../components/Card';
import { StateBadge } from '../components/StateBadge';
import {
  BID_CASE_STATE_DISPLAY,
  NEXT_CHECKPOINT_DISPLAY,
  NOTICE_ROW_ABSENT_TEXT,
  shownText,
} from '../display/caseDisplay';
import {
  ACTION_INPUT,
  OUTCOME_VALUES,
  OVERDUE_DISPLAY,
  bidBoxActionOf,
  blankToServer,
  exitsOf,
  type BidBoxAction,
  type BidBoxActionKind,
} from '../display/bidBoxDisplay';
import { displayOrUnknown } from '../display/stateDisplay';
import { instantDisplayOf } from '../display/timeDisplay';

import { InstantText } from './InstantText';

/**
 * 운영자가 적은 것 — 전이 하나를 보낼 때 실리는 값이다.
 *
 * **결과 기록만 다른 모양이다**(CR r1 L-8): `outcome` 이 **필수**이고 타입이 계약 enum 그대로다.
 * 그래서 전송 자리에 캐스팅도 `undefined` 갈래도 없다 — 「결과 기록인데 결과가 없다」가 타입으로
 * 표현되지 않는다.
 */
export type ActionPayload =
  | { readonly kind: Exclude<BidBoxActionKind, 'RECORD_OUTCOME'>; readonly value: string }
  | { readonly kind: 'RECORD_OUTCOME'; readonly value: string; readonly outcome: Outcome };

/**
 * 그 선택지 값이 결과 enum 인가 — **선택지 표에서 파생한다**(CR r2 L2R-6). 앞 판은 결과 셋을
 * 손으로 **두 번째** 적었고, 계약이 결과를 늘리면 `Outcome` 은 넓어지는데 이 술어만 새 값을
 * 거부했다. 표를 출처로 쓰면 그 갈림이 생기지 않는다(L-9 의 소재지 처방과 같은 꼴).
 */
function isOutcome(value: string): value is Outcome {
  return OUTCOME_VALUES.has(value);
}

/**
 * 전이 하나를 받는 자리. 입력의 모양은 `ACTION_INPUT` 표가 정하고 이 컴포넌트는 그 모양을
 * 그린다 — 행동마다 `if` 를 더하지 않는다.
 *
 * **기본값이 없다**: 고르기 전에 보내면 무엇이 빠졌는지 말하고 요청을 보내지 않는다.
 *
 * **화면 C 도 이 폼을 쓴다**(M13/13C 머지 차례) — 워크시트의 내려놓기 둘이 같은 입력 꼴·같은
 * 빈 값 가드·같은 확인 한 단을 지나야 하고, 그것을 두 번 적으면 한쪽만 고쳐진다. 그래서 내보낸다.
 */
export function ActionForm({
  action,
  formId,
  pending,
  onSend,
  onCancel,
}: {
  readonly action: BidBoxAction;
  /** 이 폼의 자리 — 내려놓기 버튼이 `aria-controls` 로 가리킨다. */
  readonly formId: string;
  /** 쓰기가 보류 중인가 — 보류 중에는 **다시 보내지 않는다**(cr MEDIUM). */
  readonly pending: boolean;
  readonly onSend: (payload: ActionPayload) => void;
  readonly onCancel: () => void;
}) {
  const input = ACTION_INPUT[action.kind];
  const [value, setValue] = useState('');
  const [missing, setMissing] = useState(false);
  /** 고르기는 했는데 **그 값을 보낼 수 없다** — 표와 계약이 갈린 자리다(오늘 닿을 길은 없다). */
  const [unsendable, setUnsendable] = useState(false);
  /** 되돌릴 수 없는 전이에서 한 번 더 물은 뒤인가 — 표의 `confirm` 이 있는 행만 이 자리를 쓴다. */
  const [confirming, setConfirming] = useState(false);
  const groupId = useId();
  /** 세는 칸의 id — 입력과 **묶어** 둔다(그 값이 칸의 이름이 되지 않게 하면서 낭독에 남긴다). */
  const lengthId = `${groupId}-length`;
  const formRef = useRef<HTMLDivElement>(null);

  /** 이 폼의 첫 입력칸 — 열었을 때와 거절했을 때 둘 다 그리로 보낸다. */
  function focusInput(): void {
    formRef.current?.querySelector<HTMLElement>('input, textarea')?.focus();
  }

  /**
   * 폼이 열리면 **입력칸으로 간다**. 키보드 운영자가 Tab 으로 찾아 내려가지 않게 한다.
   * 폼의 정체가 행동에 묶여 있으므로(`key`) 다른 내려놓기를 열면 이 자리가 다시 선다.
   */
  useEffect(focusInput, []);

  /**
   * 「값이 없다」의 뜻이 꼴마다 다르다 — 고르는 입력은 **빈 문자열**이고 자유 텍스트는 **서버가
   * 공백뿐으로 볼 값**이다(공백만 적은 사유는 사유가 아니다. 서버도 그것을 거부한다).
   */
  const blank = input.shape === 'TEXT' ? blankToServer(value) : value === '';
  /** 글자 수 상한을 넘었는가 — 상한이 없는 꼴에서는 늘 거짓이다. */
  const tooLong = input.maxChars !== undefined && value.length > input.maxChars;

  /**
   * 값이 바뀌면 **확인을 되돌린다** — 확인한 뒤 글자를 고치면 운영자가 본 문장과 실제로 보내는
   * 값이 달라진다. 되돌릴 수 없는 전이에서 그 어긋남은 기록으로 남는다.
   */
  function change(next: string): void {
    setValue(next);
    setConfirming(false);
  }

  function send(): void {
    if (blank) {
      setMissing(true);
      return;
    }
    // **자르지 않는다**(팀장 판정): 네이티브 길이 제약으로 붙여넣기를 조용히 자르면 운영자가
    // 잘린 줄 모른 채 기록을 남긴다. 넘으면 보내지 않고 말한다.
    //
    // 경고는 글자를 넘긴 순간부터 이미 떠 있다 — 그래서 누른 순간 **화면이 전혀 바뀌지 않는
    // 것**이 문제였다(cr LOW). 고칠 자리로 보낸다(이 파일이 같은 꼴을 한 번 고친 적이 있다:
    // 「앞 판은 setMissing 만 해서 아무 말도 없이 아무 일도 일어나지 않았다」).
    if (tooLong) {
      focusInput();
      return;
    }
    // 되돌림 전이가 없는 전이는 한 번 더 묻는다(D-7E-5 의 짝 — 실행 취소를 줄 수 없는 자리다).
    if (input.confirm !== undefined && !confirming) {
      setConfirming(true);
      return;
    }
    if (action.kind === 'RECORD_OUTCOME') {
      // 표의 선택지가 결과 enum 이 아니면 **보내지 않고 그 사실을 말한다**(앞 판은 `setMissing`
      // 만 해서 경고가 `value === ''` 조건에 걸려 **아무 말도 없이 아무 일도 일어나지 않았다**).

      // 결과 기록은 **계약 enum 을 그대로** 싣는다 — 고른 선택지의 값이 그 타입이다. 표의
      // 선택지가 결과 enum 이 아니면 보내지 않는다(그 꼴은 표가 바뀌어야 생긴다).
      if (!isOutcome(value)) {
        setUnsendable(true);
        return;
      }
      onSend({ kind: 'RECORD_OUTCOME', value, outcome: value });
      return;
    }
    onSend({ kind: action.kind, value });
  }

  return (
    <div id={formId} ref={formRef} className="mt-3 rounded-md border border-line p-3">
      <p className="text-xs font-medium">{action.label}</p>
      {input.shape === 'DATETIME' ? (
        <label className="mt-2 flex flex-col gap-1">
          <span className="text-xs text-ink-soft">{input.label}</span>
          <input
            type="datetime-local"
            value={value}
            onChange={(event) => change(event.target.value)}
            className="min-h-11 rounded-md border border-control-line bg-card px-3 text-sm"
          />
        </label>
      ) : null}
      {input.shape === 'CHOICE' ? (
        <fieldset className="mt-2">
          <legend className="text-xs text-ink-soft">{input.label}</legend>
          <div className="mt-1 flex flex-wrap gap-3">
            {/*
              손가락이 닿는 상자는 **레이블**이다(M13/13B cr P-3 — 팀장 판정: 13D 착수 전이라
              13B 가 고친다). 암묵 레이블이 `min-h-11` 을 들고 컨트롤은 기본 크기다.
            */}
            {(input.choices ?? []).map((choice) => (
              <label key={choice.value} className="flex min-h-11 items-center gap-1 text-sm">
                <input
                  type="radio"
                  name={groupId}
                  value={choice.value}
                  checked={value === choice.value}
                  onChange={() => change(choice.value)}
                />
                {choice.label}
              </label>
            ))}
          </div>
        </fieldset>
      ) : null}
      {input.shape === 'TEXT' ? (
        <div className="mt-2 flex flex-col gap-1">
          {/* 세는 칸은 **레이블 밖**이다 — 안에 두면 칸의 접근 가능 이름이 「사유 0 / 500」이 되어
              이름이 입력할 때마다 바뀐다. 대신 `aria-describedby` 로 묶어 낭독에서 잃지 않는다. */}
          <label className="flex flex-col gap-1">
            <span className="text-xs text-ink-soft">{input.label}</span>
            {/* 네이티브 길이 제약을 **두지 않는다**(팀장 판정): 그것은 붙여넣기를 조용히 자르고,
                운영자는 기록에 남은 사유가 잘린 줄 모른다. 세어 보이고 넘으면 거부한다. */}
            <textarea
              value={value}
              rows={3}
              onChange={(event) => change(event.target.value)}
              aria-describedby={input.maxChars === undefined ? undefined : lengthId}
              className="rounded-md border border-control-line bg-card px-3 py-2 text-sm"
            />
          </label>
          {input.maxChars === undefined ? null : (
            <span
              id={lengthId}
              data-testid="action-length"
              // 타자마다 값이 바뀌는 칸이다 — **낭독을 조르지 않는다**(cr LOW). 설명으로 묶여
              // 있으므로 포커스가 올 때 읽히고, 그것으로 충분하다.
              aria-live="off"
              className={`text-xs ${tooLong ? 'text-bad-ink' : 'text-ink-soft'}`}
            >
              {value.length} / {input.maxChars}
            </span>
          )}
        </div>
      ) : null}
      {missing && blank ? (
        <p data-testid="action-missing" role="alert" className="mt-2 text-xs text-bad-ink">
          {input.shape === 'TEXT'
            ? `${input.label} 을(를) 먼저 적으십시오 — 빈 사유를 보내지 않습니다.`
            : `${input.label} 을(를) 먼저 고르십시오 — 고르지 않은 값을 보내지 않습니다.`}
        </p>
      ) : null}
      {tooLong ? (
        <p data-testid="action-too-long" role="alert" className="mt-2 text-xs text-bad-ink">
          {input.label} 이(가) 너무 깁니다 — 줄여서 다시 보내십시오. 잘라서 보내지 않습니다.
        </p>
      ) : null}
      {confirming ? (
        <p data-testid="action-confirm" role="alert" className="mt-2 text-xs text-bad-ink">
          {input.confirm}
        </p>
      ) : null}
      {unsendable ? (
        <p data-testid="action-unsendable" role="alert" className="mt-2 text-xs text-bad-ink">
          고른 값을 보낼 수 없습니다 — 이 화면이 아는 결과 어휘가 아닙니다.
        </p>
      ) : null}
      <div className="mt-3 flex flex-wrap gap-2">
        {/* 확인 단계에서는 **어조와 글자가 함께** 바뀐다 — 같은 버튼이 같은 모양으로 두 뜻을
            가지면 운영자가 두 번째 누름이 무엇인지 모른다. */}
        <Button
          tone={confirming ? 'danger' : 'primary'}
          onClick={send}
          disabled={pending}
          testId="action-send"
        >
          {confirming ? '확인' : '보내기'}
        </Button>
        <Button onClick={onCancel}>그만두기</Button>
      </div>
    </div>
  );
}

/** 행동 버튼 하나 — `LINK` 는 화면 이동이고 `INSTANT` 는 바로 보내며 나머지는 폼을 연다. */
function ActionButton({
  action,
  bidCase,
  formId,
  expanded,
  pending,
  onOpen,
  onSend,
}: {
  readonly action: BidBoxAction;
  readonly bidCase: BidCase;
  readonly formId: string;
  /** 이 버튼이 연 폼이 지금 떠 있는가 — 폼을 여는 버튼에만 뜻이 있다. */
  readonly expanded: boolean;
  readonly pending: boolean;
  readonly onOpen: (action: BidBoxAction) => void;
  readonly onSend: (payload: ActionPayload) => void;
}) {
  const shape = ACTION_INPUT[action.kind].shape;
  if (shape === 'LINK') {
    return (
      <LinkButton
        tone="primary"
        to={`/notices/${encodeURIComponent(bidCase.noticeNumber)}/${encodeURIComponent(bidCase.noticeRound)}`}
      >
        {action.label}
      </LinkButton>
    );
  }
  /**
   * 이 버튼이 **폼을 여는가**. 한 번만 유도해 클릭 분기와 보조기기 배선이 **같은 판정**을 쓰게
   * 한다 — 두 자리에 적으면 갈린다.
   *
   * 결과 기록은 표에서 `CHOICE` 라 이 자리에 오지 않는다. 표가 바뀌어 오더라도 **결과 없는
   * 요청을 보내지 않고** 폼을 연다(타입이 그 요청을 만들 수 없게 돼 있다).
   */
  const opensForm = shape !== 'INSTANT' || action.kind === 'RECORD_OUTCOME';
  /*
    폼을 여는 버튼은 자기 ARIA 배선을 쥐므로 공유 `Button` 이 아니라 **같은 표**를 쓰고 요소만
    여기서 그린다(내려놓기 버튼과 같은 사유). `aria-controls` 는 **열렸을 때만** 건다.
  */
  return (
    <button
      type="button"
      disabled={pending}
      aria-expanded={opensForm ? expanded : undefined}
      aria-controls={opensForm && expanded ? formId : undefined}
      onClick={() => {
        if (opensForm) {
          onOpen(action);
          return;
        }
        onSend({ kind: action.kind, value: 'instant' });
      }}
      className={controlClass('primary', 'md')}
    >
      {action.label}
    </button>
  );
}

/**
 * 건 하나의 카드. 보이는 값 전부가 `BidCase` 의 칸이다 — 상태·다음 확인 시점·지난 일정은
 * **서버가 준 값**이고 화면은 표로 문면만 고른다(D-7E-4).
 */
export function BidBoxCard({
  bidCase,
  submittedAmount,
  pending,
  onSend,
}: {
  readonly bidCase: BidCase;
  /**
   * 내 투찰 금액의 **보이는 글자** — 서식은 부르는 자리가 짓는다(카드가 숫자를 받으면 그
   * 서식이 자리마다 갈린다). 투찰 기록이 없는 건에서는 `undefined` 이고 그 자리는 서지 않는다.
   */
  readonly submittedAmount?: string;
  /**
   * 쓰기가 보류 중인가. 보류 중에는 버튼을 잠근다 — `RESUME` 은 폼 없이 바로 가는 유일한
   * 행동이라 연타가 그대로 요청 셋이 된다(cr MEDIUM 실측). 둘째·셋째는 409 이고 그 실패
   * 얼굴이 **성공한 쓰기 위에** 뜬다.
   */
  readonly pending: boolean;
  readonly onSend: (caseId: number, payload: ActionPayload) => void;
}) {
  const [open, setOpen] = useState<BidBoxAction | null>(null);
  const row = bidBoxActionOf(bidCase.state);
  /** 이 상태에서 열린 내려놓기 — **표에서 유도된다**(카드가 상태를 보고 고르지 않는다). */
  const exits = exitsOf(bidCase.state);
  /** 카드마다 하나 — 폼과 그것을 여는 버튼을 묶는 자리다. */
  const formId = `${useId()}-exit-form`;

  return (
    <Card as="li" testId={`bid-case-${bidCase.caseId}`}>
      {/* 머리는 **제목 + 상태 알약**이다(M14/14D · 와이어프레임 D). 앞 판은 공고번호가 가장 큰
          글자였고 공고명이 아예 없었다 — 운영자가 건을 알아보는 것은 번호가 아니라 이름이다.
          번호·차수는 아래 식별 줄로 내려간다(사라지지 않는다). */}
      <div className="flex flex-wrap items-start justify-between gap-x-3 gap-y-1">
        <h3 className="min-w-0 text-sm font-semibold" data-testid={`notice-title-${bidCase.caseId}`}>
          {/* **「공고 행이 없다」와 「행은 있고 제목이 비었다」는 다른 사실이다**(14E 투영의 계약).
              앞쪽은 서버가 적극적으로 답한 사실이라 자기 문면을 가지고, 뒤쪽은 응답이 그 칸을
              싣지 않은 것이라 부재 어휘다. */}
          {bidCase.noticeKnown ? (
            (bidCase.noticeTitle ?? <AbsentValue reason="notSent" />)
          ) : (
            <span className="text-xs font-normal text-absent-ink">{NOTICE_ROW_ABSENT_TEXT}</span>
          )}
        </h3>
        <div className="flex shrink-0 flex-wrap items-center gap-2">
          <StateBadge
            display={displayOrUnknown(BID_CASE_STATE_DISPLAY, bidCase.state)}
            testId={`state-${bidCase.caseId}`}
          />
          {/* 경고는 **서버의 판정 하나**에서만 뜬다 — 시각 비교가 이 자리에 없다. */}
          {bidCase.overdue ? <StateBadge display={OVERDUE_DISPLAY} testId={`overdue-${bidCase.caseId}`} /> : null}
        </div>
      </div>

      {/* 기관·내 투찰·식별 — 와이어프레임의 둘째 줄이다. **없는 값은 자리도 없다**: 선택 자리에
          부재 문면을 그리면 화면이 「서버가 그 칸을 빚졌다」고 주장하게 된다(14A 인계). */}
      <div className="mt-1 flex flex-wrap items-baseline gap-x-2 gap-y-0.5 text-xs text-ink-soft">
        {shownText(bidCase.demandAgencyName) === undefined ? null : (
          <span data-testid={`demand-agency-${bidCase.caseId}`}>{bidCase.demandAgencyName}</span>
        )}
        {/* 내 투찰 금액은 **이 응답의 칸이 아니다** — 투찰 기록이 있는 건만 결과 조회가 낸다.
            그래서 값이 있을 때만 서고, 없을 때는 「아직 투찰하지 않았다」를 **상태**가 말한다. */}
        {submittedAmount === undefined ? null : (
          <span data-testid={`submitted-amount-${bidCase.caseId}`}>
            내 투찰 <span className="font-numeric text-ink">{submittedAmount}</span>
          </span>
        )}
        <span className="font-numeric">{bidCase.noticeNumber}</span>
        <span>차수 {bidCase.noticeRound}</span>
      </div>

      <dl className="mt-3 grid gap-3 sm:grid-cols-2">
        <div className="flex flex-col gap-0.5">
          <dt className="text-xs text-ink-soft">다음 확인 시점</dt>
          <dd className="text-sm">
            <StateBadge
              display={displayOrUnknown(NEXT_CHECKPOINT_DISPLAY, bidCase.nextCheckpointKind)}
              testId={`checkpoint-kind-${bidCase.caseId}`}
            />
          </dd>
        </div>
        {/* 시각은 **KST 표기**다(M9 D-1 — 앞 판은 응답의 ISO 원문이었다). 「N일 남음/지남」을
            여기서 내지 않는다(v r1 M-1): 브라우저 시계로 재면 사용자마다 답이 다르고,
            `overdue:false` 인 건에 「지남」이 붙어 서버 판정과 어긋난 말을 하게 된다. 「지남」을
            말하는 것은 위의 경고 배지 하나다. `<time dateTime>` 의 기계 가독 원문은 그대로
            싣는다(`InstantText` — 서식은 보이는 글자의 일이고 기계 칸의 일이 아니다). */}
        <div className="flex flex-col gap-0.5">
          <dt className="text-xs text-ink-soft">시점</dt>
          <dd className="text-sm">
            {/* testId 는 **`InstantText` 자신이 단다**(r1 cr LOW) — 래퍼에 달면 같은 뜻의 칸이
                DOM 모양 둘이 되고, e2e 가 같은 사실을 두 꼴로 묻게 된다(한 쪽에 래퍼가 생기거나
                사라지면 조용히 미측정이다). 화면 E 와 같은 꼴로 맞춘다. */}
            <InstantText
              display={instantDisplayOf(bidCase.nextCheckpointAt)}
              testId={`checkpoint-at-${bidCase.caseId}`}
            />
          </dd>
        </div>
      </dl>

      {row.primary === null && row.secondary === null ? null : (
        <div className="mt-3 flex flex-wrap gap-2">
          {[row.primary, row.secondary]
            .filter((action): action is BidBoxAction => action !== null)
            .map((action) => (
              <ActionButton
                key={action.kind}
                action={action}
                bidCase={bidCase}
                formId={formId}
                expanded={open?.kind === action.kind}
                pending={pending}
                onOpen={setOpen}
                onSend={(payload) => onSend(bidCase.caseId, payload)}
              />
            ))}
        </div>
      )}

      {/* 내려놓기는 **다른 줄**이다(M13/13D). 위 줄의 「다음 행동 하나」와 섞으면 관심 상태에서
          행동이 넷이 되고 무엇이 다음 걸음인지 화면이 말하지 못한다(§6.5). 머리 줄의 상태 배지와도
          떨어진다. 둘 다 입력을 받으므로 바로 폼을 연다 — 즉시 보내는 자리가 아니다.

          testId 를 **종류마다** 단다: 카드 안 버튼을 전부 집는 술어로는 내려놓기 집합을 셀 수
          없다(배지가 버튼이 되면 그 집합에 함께 든다 — 13E). 상태 전수 루프가 이 testId 를 쓴다. */}
      {exits.length === 0 ? null : (
        <div className="mt-2 flex flex-wrap gap-2">
          {exits.map((action) => (
            /*
              자기 ARIA 배선(`aria-expanded`·`aria-controls`)을 쥐므로 공유 `Button` 이 아니라
              **같은 표**를 쓰고 요소만 여기서 그린다 — 이 저장소의 선 패턴이다(탭 줄·쓰기 실패
              얼굴의 링크). 사본이 생기지 않는 것이 요점이다.

              `aria-controls` 는 **열렸을 때만** 건다: 없는 id 를 가리키면 보조기기가 빈 자리를
              듣는다(같은 꼴을 탭 패널에서 한 번 고쳤다).
            */
            <button
              key={action.kind}
              type="button"
              data-testid={`exit-${action.kind}-${bidCase.caseId}`}
              disabled={pending}
              aria-expanded={open?.kind === action.kind}
              aria-controls={open?.kind === action.kind ? formId : undefined}
              onClick={() => setOpen(action)}
              className={controlClass('secondary', 'md')}
            >
              {action.label}
            </button>
          ))}
        </div>
      )}

      {open === null ? null : (
        /* **폼의 정체를 행동에 묶는다**(cr HIGH). 내려놓기 버튼 줄은 폼이 열려 있어도 계속
           그려지므로 「닫지 않고 다른 것을 연다」가 평범한 조작이다. `key` 가 없으면 같은 자리·
           같은 타입이라 React 가 컴포넌트를 유지하고 앞의 값·확인 상태가 살아남는다 — 그러면
           빈 값 가드가 걸리지 않은 채 **되돌릴 수 없는 전이**가 앞 폼의 입력을 싣고 나간다. */
        <ActionForm
          key={open.kind}
          action={open}
          formId={formId}
          pending={pending}
          onCancel={() => setOpen(null)}
          onSend={(payload) => {
            setOpen(null);
            onSend(bidCase.caseId, payload);
          }}
        />
      )}
    </Card>
  );
}

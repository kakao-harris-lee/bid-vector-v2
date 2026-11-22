/**
 * 여정 teardown 의 걸음 감싸개 — **던지지 않고**(15F r1 cr L-3) **진단도 버리지 않는다**(M15/15I ⓒ).
 *
 * 되돌림 둘은 `!ok()` 를 사유 **문자열**로 올리지만, 그 안의 세션 읽기와 `post` 는 전송 오류를
 * 그대로 던졌다. 첫 걸음이 거기서 던지면 둘째 되돌림은 한 줄도 돌지 않고 심은 값이 공유 계정에
 * 남는다 — 그래서 15F 가 예외를 사유 문자열로 바꿨다. 그 판은 **메시지만** 남겼고, 스택과
 * `cause` 사슬은 거기서 사라졌다: 전송 오류의 메시지는 어느 걸음에서 났는지 말하지 않으므로
 * 진단이 사라진 자리에서 다시 시작해야 했다.
 *
 * 그래서 둘을 **가른다**. 단언의 머리말로 가는 [TeardownOutcome.failure] 는 한 줄로 두고,
 * 스택과 사슬은 [TeardownOutcome.detail] 로 보존해 문면 아래에 붙인다([teardownMessage]).
 *
 * 이 모듈이 spec 밖에 있는 이유: 여정 레인의 **초록 실행에는 teardown 이 던지는 경로가 없어**
 * 이 고침이 거기서 측정되지 않는다. 단위 test(`e2e/harness/journey-teardown.test.mjs`, vitest)가
 * 그 자리를 든다.
 */

/** 걸음 하나의 결과 — 값·짧은 문면·진단 셋을 따로 든다. */
export interface TeardownOutcome<T> {
  readonly value: T | null;
  /**
   * 실패 문면 — **한 줄이다**(라벨 + 메시지의 **첫 줄**). 단언의 머리말로 간다.
   *
   * 「첫 줄」인 것이 요점이다(r1 v L-3): Playwright 의 요청 오류 메시지는 `Call log:` 여러 줄을
   * 담으므로, 메시지를 그대로 실으면 머리말이 리포트를 덮는다. 잘린 뒷부분은 [detail] 이 든다.
   */
  readonly failure: string | null;
  /** 스택과 `cause` 사슬 — 문면을 짧게 두고 진단은 여기 남는다. 남길 것이 없으면 `null`. */
  readonly detail: string | null;
}

/**
 * `cause` 사슬을 따라가는 걸음 상한. 사슬이 길어지는 것보다 **멈추지 않는 것**이 teardown 에서
 * 더 비싸다 — 상한과 순환 가드를 둘 다 둔다.
 */
const MAX_CAUSE_DEPTH = 8;

/** 잘린 꼬리를 알리는 표지 — 머리말이 전부가 아님을 그 자리에서 말한다. */
const TRUNCATED_MARK = ' …(진단 참조)';

/**
 * **아무것도 던지지 않고** 값을 문자열로 — `String(x)` 가 던질 수 있다(r1 cr L-b 실측:
 * `Object.create(null)` 은 `toString` 이 없어 `TypeError` 다). 이 모듈의 계약이 「던지지
 * 않는다」이므로, 그 계약을 지키는 자리는 여기 한 곳이다.
 */
function safeString(value: unknown): string {
  try {
    return String(value);
  } catch {
    return `[문자열로 바꿀 수 없는 값: ${typeof value}]`;
  }
}

/**
 * 던진 것의 한 줄 요약 — 머리말에 싣는 값이라 스택도, **둘째 줄 이후도** 담지 않는다
 * (r1 v L-3). 잘렸으면 그 사실을 표지로 남긴다.
 */
function thrownHeadline(thrown: unknown): string {
  const message = thrown instanceof Error ? safeString(thrown.message) : safeString(thrown);
  const firstBreak = message.indexOf('\n');
  return firstBreak === -1 ? message : `${message.slice(0, firstBreak)}${TRUNCATED_MARK}`;
}

/** 던진 것 하나의 서술 — `Error` 면 스택(없으면 이름+메시지), 그 밖은 문자열화. */
function describeThrown(thrown: unknown): string {
  if (thrown instanceof Error) {
    return thrown.stack ?? `${safeString(thrown.name)}: ${safeString(thrown.message)}`;
  }
  return safeString(thrown);
}

/**
 * 던진 것의 **스택과 `cause` 사슬** — 한 문자열로. 남길 것이 없으면 **`null`** 이다
 * (r1 cr L-a: `throw undefined` 에서 빈 문자열을 내면 머리말 뒤에 빈 줄만 붙는다).
 *
 * `cause` 는 `Error` 가 아닐 수 있다(아무 값이나 실을 수 있는 칸이다). 사슬이 자기를 가리키면
 * 그 사실을 적고 멈춘다 — 조용히 끊으면 「사슬이 거기서 끝났다」와 구별되지 않는다.
 */
export function thrownDiagnostic(thrown: unknown): string | null {
  const lines: string[] = [];
  const seen = new Set<unknown>();
  let current: unknown = thrown;
  for (let depth = 0; depth < MAX_CAUSE_DEPTH; depth += 1) {
    if (current === undefined || current === null || seen.has(current)) {
      break;
    }
    seen.add(current);
    lines.push(depth === 0 ? describeThrown(current) : `cause[${depth}]: ${describeThrown(current)}`);
    current = current instanceof Error ? (current as Error & { cause?: unknown }).cause : undefined;
  }
  if (current !== undefined && current !== null) {
    lines.push(
      seen.has(current)
        ? 'cause 사슬이 이미 지난 것을 가리킨다 — 더 따라가지 않는다'
        : `cause 사슬이 ${MAX_CAUSE_DEPTH} 걸음에서 잘렸다`,
    );
  }
  return lines.length === 0 ? null : lines.join('\n');
}

/**
 * 걸음 하나를 돌린다 — 던지면 사유 문자열과 진단으로 바꾼다.
 *
 * 되돌림들을 **번갈아 단언 없이** 다 돌리는 teardown 의 순서를 예외가 깨지 못하게 하는 것이
 * 이 함수의 일이다.
 */
export async function teardownStep<T>(
  label: string,
  step: () => Promise<T>,
): Promise<TeardownOutcome<T>> {
  try {
    return { value: await step(), failure: null, detail: null };
  } catch (thrown) {
    return {
      value: null,
      failure: `${label}: ${thrownHeadline(thrown)}`,
      detail: thrownDiagnostic(thrown),
    };
  }
}

/**
 * 단언 문면 — **머리말이 첫 줄**이고, 진단이 있으면 그 아래에 붙는다. 문면 길이와 진단 보존의
 * 교환(ⓒ 가 결정 사안이었던 자리)을 이 모양으로 푼다: 읽는 사람은 첫 줄만 보고, 고치는 사람은
 * 그 아래를 본다.
 */
export function teardownMessage(headline: string, outcome: TeardownOutcome<unknown>): string {
  return outcome.detail === null ? headline : `${headline}\n${outcome.detail}`;
}

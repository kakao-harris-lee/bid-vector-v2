/**
 * 시각 표시의 **한 자리**(M9/screen-display D-1·D-2). 화면 파일은 `*At` 원문을 보간하지 않고
 * 이 모듈이 낸 값을 그린다 — 절대 시각(KST)과 상대 날 수가 둘 다 여기서 나온다. 앞 판은
 * 알림함·타임라인·입찰함 카드·워크시트가 **응답의 ISO 문자열을 그대로** 그렸다
 * (`OPEN-7E3B-TIME-DISPLAY`).
 *
 * **벽시계를 읽는 자리도 여기 하나다**(`OPEN-7E-GATE-RESIDUAL` ⑨ 처방 — 7E-2 checklist §3.1).
 * 나머지 `src/screens`·`src/display` 에서는 `Date.now()`·`new Date()` 를
 * lint(`no-restricted-syntax`)가 막고 **예외는 이 파일 하나**다. 그래서 「브라우저 시계로
 * 지남/남음을 판정하는 카드」가 생기면 문면 술어가 아니라 **게이트**에 걸린다.
 */

/** 값이 없는 시각 칸의 문면 — 0 이나 빈칸으로 지어내지 않는다(D-1). */
export const ABSENT_INSTANT = '—';

/**
 * 읽히지 않은 시각의 문면 — **조용한 빈칸 금지**(D-1). 원문을 그대로 내지 않는다: 보낸 값을
 * 읽지 못한 것과 보내지 않은 것이 같은 얼굴이면 운영자가 그 둘을 가릴 수 없다.
 */
export const UNREADABLE_INSTANT = '알 수 없음';

/** 시각 한 칸의 세 갈래 — 읽힌 값 · 없는 값 · 읽히지 않은 값. */
export type InstantKind = 'readable' | 'absent' | 'unreadable';

export interface InstantDisplay {
  readonly kind: InstantKind;
  /** 화면에 보이는 문면. KST `YYYY-MM-DD HH:mm`(초 이하 절삭) 또는 위 두 상수. */
  readonly text: string;
  /**
   * `<time dateTime>` 에 실을 **기계 가독 원문**. 읽히지 않았으면 `null` 이다 — 읽지 못한 값을
   * 기계 칸에 실으면 보조기기·추출기가 그 값을 시각으로 읽는다.
   */
  readonly machine: string | null;
}

/**
 * KST 는 **UTC+9 고정**이다(1988 년 이후 일광절약시간이 없다). 그래서 시간대 자료(ICU)를 타지
 * 않고 고정 오프셋 산술로 센다 — 서식 게이트가 실행 환경의 자료 판본에 달려 있으면 게이트가
 * 아니기 때문이다(Node·브라우저·CI 러너의 ICU 가 서로 다른 판본일 수 있다).
 */
const KST_OFFSET_MILLIS = 9 * 60 * 60 * 1000;

function pad2(value: number): string {
  return String(value).padStart(2, '0');
}

/**
 * 오프셋을 더한 뒤 **UTC 칸으로 읽는다** — 그러면 실행 장비의 시간대 설정이 결과에 들어오지
 * 않는다(`getHours()` 는 들어온다).
 */
function kstText(millis: number): string {
  const shifted = new Date(millis + KST_OFFSET_MILLIS);
  const date = `${shifted.getUTCFullYear()}-${pad2(shifted.getUTCMonth() + 1)}-${pad2(shifted.getUTCDate())}`;
  // 분까지만 읽는 것이 **절삭**이다 — 초를 어림해 분을 올리면 마감 시각이 과장된다.
  return `${date} ${pad2(shifted.getUTCHours())}:${pad2(shifted.getUTCMinutes())}`;
}

/** 시각 한 칸을 읽는 **유일한 자리**. 화면은 `*At` 원문 대신 이 반환값을 그린다(D-2). */
export function instantDisplayOf(at: string | null | undefined): InstantDisplay {
  if (at === null || at === undefined) {
    return { kind: 'absent', text: ABSENT_INSTANT, machine: null };
  }
  const millis = Date.parse(at);
  if (Number.isNaN(millis)) {
    return { kind: 'unreadable', text: UNREADABLE_INSTANT, machine: null };
  }
  return { kind: 'readable', text: kstText(millis), machine: at };
}

const MILLIS_PER_DAY = 86_400_000;

/**
 * 마감까지 남은 날 — 요구표가 **클라이언트 계산으로 승인한 유일한 자리**(ⓐ 행)다. 「마감됨」
 * 같은 **상태 라벨은 만들지 않는다** — 그것은 `status` 가 말한다(D-7E-4). 마감이 없으면 날 수도
 * 없다.
 *
 * **남은 쪽과 지난 쪽의 어림이 다르다**(7E-1 CR L-2). 남은 쪽은 **내림**이다(반 나절 남았으면
 * `D-0` — 「아직 오늘」). 지난 쪽에 같은 내림을 쓰면 6시간 전에 마감한 공고가 `D+1` 로 **하루
 * 과장**된다(-0.25 → -1). 그래서 지난 쪽은 0 쪽으로 자른다(`Math.ceil`).
 */
export function daysUntil(deadlineAt: string | null | undefined, now: Date): number | null {
  if (deadlineAt === null || deadlineAt === undefined) {
    return null;
  }
  const deadline = Date.parse(deadlineAt);
  if (Number.isNaN(deadline)) {
    return null;
  }
  const days = (deadline - now.getTime()) / MILLIS_PER_DAY;
  const counted = days >= 0 ? Math.floor(days) : Math.ceil(days);
  // `Math.ceil(-0.25)` 는 **`-0`** 이다 — 그것은 `>= 0` 비교에서 참이라 「지난 날」이 「남은 날」
  // 분기로 새고, 표시도 `D+-0` 꼴이 될 수 있다. 0 으로 모은다.
  return counted === 0 ? 0 : counted;
}

/** D-day 문면 — 날 수가 없으면 **자리도 없다**(`null`). 상태 말은 여기서도 만들지 않는다. */
export function dayCountText(days: number | null): string | null {
  if (days === null) {
    return null;
  }
  return days >= 0 ? `D-${days}` : `D+${-days}`;
}

/**
 * 지금 — **화면이 벽시계를 읽는 유일한 입구**다. 화면은 이 값을 한 번 받아 props 로 내린다
 * (렌더마다 다시 읽으면 같은 화면의 두 카드가 서로 다른 「지금」으로 날을 센다).
 */
export function readClock(): Date {
  return new Date();
}

/**
 * 상태 → 표시의 **공통 형태**(D-7E-4). 표시 문면은 계약의 상태 값에서만 나오고 화면이
 * 추론하지 않는다 — 그래서 각 축의 표는 `satisfies Record<Enum, StateDisplay>` 로 두고,
 * 계약에 값이 늘면 `tsc` 가 그 자리에서 멈춘다.
 */
export type DisplayTone =
  /** 사실을 말할 뿐 좋고 나쁨이 없다. */
  | 'neutral'
  /** 값이 없다 — 「없음」은 0 이 아니다. */
  | 'absent'
  /** 아직 판정하지 않았다 — 통과로도 미달로도 읽히면 안 된다. */
  | 'unjudged'
  | 'good'
  | 'bad';

export interface StateDisplay {
  readonly label: string;
  /** 짧은 라벨 뒤의 한 겹 — 2층 라벨(§6.4)의 아래층이다. */
  readonly why?: string;
  readonly tone: DisplayTone;
}

/**
 * 표 조회의 **유일한 자리**다. 폴백을 두지 않는다 — 표 밖의 값에 문면을 지어내면 화면이
 * 계약에 없는 상태를 아는 것처럼 보인다.
 *
 * **반환 타입이 `| undefined` 다**(CR M-3). 앞 판은 `StateDisplay` 라고 선언하면서 표 밖 값에
 * `undefined` 를 냈다 — 타입이 거짓말을 하고, 그 뒤 거동이 자리마다 갈렸다(어떤 자리는
 * TypeError 로 오류 경계에 가고 배지는 **조용히 아무것도 그리지 않았다**). 정직하게 선언하면
 * 호출 자리가 `tsc` 로 드러나고, 거동은 아래 [unknownStateDisplay] 하나로 모인다.
 */
export function displayOf<K extends string>(
  table: Readonly<Record<K, StateDisplay>>,
  value: K,
): StateDisplay | undefined {
  return table[value];
}

/**
 * 표 밖의 값을 **보이게** 낸다(CR M-3). 그런 값이 오는 경로는 「계약이 늘었는데 번들이 아직
 * 모른다」(배포 비틀림) 하나이고 — 진행 중인 7C②-b 의 새 하한율 상태가 바로 그 경우다 — 그때
 * 조용한 빈 자리는 **서버가 안 보낸 것**으로 읽힌다. 서버가 준 코드를 그대로 보이고 어조는
 * 「아직 판정하지 않음」으로 둔다(`noticeStatusDisplayOf` 와 같은 관례).
 */
export function unknownStateDisplay(code: string): StateDisplay {
  return { label: `알 수 없는 상태(${code})`, why: '이 화면이 아직 모르는 상태 값이다', tone: 'unjudged' };
}

/** 표에 있으면 그 행, 없으면 **보이는** 미지 상태 — 화면이 쓰는 한 자리다. */
export function displayOrUnknown<K extends string>(
  table: Readonly<Record<K, StateDisplay>>,
  value: K,
): StateDisplay {
  return displayOf(table, value) ?? unknownStateDisplay(value);
}

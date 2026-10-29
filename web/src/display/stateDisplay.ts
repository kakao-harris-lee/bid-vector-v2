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
 * 계약에 없는 상태를 아는 것처럼 보인다. 그런 값이 실제로 오는 경로는 「계약이 늘었는데
 * 화면이 아직 모른다」 하나이고, 그때는 오류 경계가 받는 편이 옳다(가짜 라벨보다 낫다).
 */
export function displayOf<K extends string>(table: Readonly<Record<K, StateDisplay>>, value: K): StateDisplay {
  return table[value];
}

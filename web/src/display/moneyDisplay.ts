import type { components } from '../api/generated/operator-api';

import { displayOrUnknown, type StateDisplay } from './stateDisplay';

/**
 * 금액 수식어의 표시(M7/7E-3b r1 — CR M-1 · v L-2).
 *
 * **한 값에 두 말을 두지 않는다.** 운영자는 화면 C 에서 「부가세 포함」을 고르는데 화면 E 가
 * 그것을 `INCLUSIVE` 로 돌려주고 있었다 — 같은 개념이 입력과 출력에서 다른 말이면 운영자는
 * 자기가 고른 값을 못 알아본다. 입력 폼(`VAT_CHOICES`)과 결과 표시가 **이 표 하나**를 읽는다.
 *
 * 키를 **요청 스키마의 enum** 에서 따온다: 응답 쪽 칸은 계약이 `string` 이라 `satisfies` 가
 * 서지 않지만, 어휘는 같은 축이고 요청 쪽에 전수가 선언돼 있다. 응답의 값을 읽을 때는
 * `displayOrUnknown` 으로 — 표 밖 값이 와도 **보이게** 낸다(조용한 빈 자리는 「서버가 안
 * 보냈다」로 읽힌다).
 *
 * **basis·provenance 는 이 표에 없다.** 그 둘은 화면 B·7E-3a 가 원문 토큰으로 보이는 축이고
 * (`BASE_AMOUNT`·`PUBLISHED`), 운영자가 **고르는 값이 아니라** 상류가 선언하는 값이라 입력과
 * 출력이 갈릴 자리가 없다. 과세만 입력 폼을 갖는다.
 */
export type VatTreatment = components['schemas']['RecordBidRequest']['vatTreatment'];

export const VAT_TREATMENT_DISPLAY = {
  INCLUSIVE: { label: '부가세 포함', tone: 'neutral' },
  EXCLUSIVE: { label: '부가세 별도', tone: 'neutral' },
  /** 상류가 과세 축을 선언하지 않은 값 — 「0%」가 아니라 **모른다**는 사실이다. */
  UNKNOWN: { label: '과세 구분 모름', tone: 'absent' },
} as const satisfies Record<VatTreatment, StateDisplay>;

/**
 * 응답의 과세 칸을 읽는 **한 자리**. 계약이 그 칸을 `string` 으로 선언하므로(요청 쪽만 enum)
 * 표 조회의 키 좁힘을 여기 한 번만 둔다 — 부르는 자리마다 넓히면 그 자리마다 폴백이 생긴다.
 *
 * 표 밖 값은 `displayOrUnknown` 이 **보이게** 낸다(계약이 늘었는데 번들이 아직 모르는 경우).
 */
export function vatTreatmentDisplayOf(token: string): StateDisplay {
  return displayOrUnknown<string>(VAT_TREATMENT_DISPLAY, token);
}

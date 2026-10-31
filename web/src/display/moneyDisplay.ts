import type { components } from '../api/generated/operator-api';

import { displayOrUnknown, type StateDisplay } from './stateDisplay';

/**
 * 금액 수식어의 표시(M7/7E-3b — r1 CR M-1 · v L-2, 경계는 r2 CR M-3 · v M-2 로 정정).
 *
 * **이 표가 덮는 경계는 둘이다**(측정된 경계를 적는다): ⓐ **화면 E 카드 안**의 과세 표시 네
 * 자리(`ResultCard.tsx` — 내 투찰가 · 확정 낙찰가 둘 · 잠정 1위) ⓑ **화면 C 의 입력 선택지**
 * (`VAT_CHOICES`). 그 둘이 같은 말을 쓰게 하는 것이 이 표의 일이다 — 운영자는 화면 C 에서
 * 「부가세 포함」을 고르는데 화면 E 가 `INCLUSIVE` 를 돌려주고 있었다.
 *
 * 키를 **요청 스키마의 enum** 에서 따온다: 응답 쪽 칸은 계약이 `string` 이라 `satisfies` 가
 * 서지 않지만, 어휘는 같은 축이고 요청 쪽에 전수가 선언돼 있다. 응답의 값을 읽을 때는
 * `displayOrUnknown` 으로 — 표 밖 값이 와도 **보이게** 낸다(조용한 빈 자리는 「서버가 안
 * 보냈다」로 읽힌다).
 *
 * **그 밖은 아직 원문 토큰이다** — 화면 B 카드와 화면 C **머리**의 기초금액은 과세를
 * `UNKNOWN` 으로 그린다(`noticeDisplay.ts` 의 금액 부속 조립). 그래서 화면 C 안에서 같은 축이
 * 두 말로 보인다. 경계를 「운영자가 고르는 값이냐」로 적었던 앞 판 문장은 **틀렸다**: 개찰 절의
 * 과세도 상류 선언값인데 표를 태웠다. 실제 경계는 위 ⓐⓑ 다. 남은 축(금액 부속 토큰 전반 —
 * `BASE_AMOUNT`·`PUBLISHED` 포함)은 화면 정리 slice 의 일로 `OPEN-7E3B-MONEY-WORDING`
 * 에 등재돼 있다.
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

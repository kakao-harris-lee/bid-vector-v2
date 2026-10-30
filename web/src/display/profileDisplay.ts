import type { components } from '../api/generated/operator-api';

import type { StateDisplay } from './stateDisplay';

type Profile = components['schemas']['Profile'];
type BusinessDetailsRequest = components['schemas']['BusinessDetailsRequest'];

/** 받아들이는 정수의 모양 — **문자열 전체**가 자리 구분 없는 정수다(쉼표·단위·소수는 거부). */
const INTEGER_SHAPE = /^\d+$/u;

export type NaraRegistration = Profile['naraRegistration'];
export type NaraField = Profile['naraRegisteredFields'][number];
export type HeadquartersRegion = NonNullable<Profile['headquartersRegion']>;
export type UnjudgedAxis = Profile['unjudgedAxes'][number];

/**
 * 프로필 표시의 표들(M7/7E-2, D-7E2-3).
 *
 * **「미응답」은 값이다**(계약) — null 이나 「미등록」으로 접지 않는다. 화면의 경고가 다르기
 * 때문이다: 묻지 않은 것과 아니라고 답한 것은 같은 사실이 아니다.
 */
export const NARA_REGISTRATION_DISPLAY = {
  UNANSWERED: { label: '미응답', why: '아직 답하지 않았다 — 「미등록」과 다른 사실이다', tone: 'absent' },
  REGISTERED: { label: '등록', tone: 'good' },
  NOT_REGISTERED: { label: '미등록', why: '등록하지 않았다고 답했다', tone: 'neutral' },
} as const satisfies Record<NaraRegistration, StateDisplay>;

export const NARA_FIELD_DISPLAY = {
  GOODS: { label: '물품', tone: 'neutral' },
  SERVICE: { label: '용역', tone: 'neutral' },
  CONSTRUCTION: { label: '공사', tone: 'neutral' },
} as const satisfies Record<NaraField, StateDisplay>;

/** 본사 소재지 — 계약 enum 전수 열일곱. 목록을 손으로 늘리지 않는다. */
export const REGION_DISPLAY = {
  SEOUL: { label: '서울', tone: 'neutral' },
  BUSAN: { label: '부산', tone: 'neutral' },
  DAEGU: { label: '대구', tone: 'neutral' },
  INCHEON: { label: '인천', tone: 'neutral' },
  GWANGJU: { label: '광주', tone: 'neutral' },
  DAEJEON: { label: '대전', tone: 'neutral' },
  ULSAN: { label: '울산', tone: 'neutral' },
  SEJONG: { label: '세종', tone: 'neutral' },
  GYEONGGI: { label: '경기', tone: 'neutral' },
  GANGWON: { label: '강원', tone: 'neutral' },
  CHUNGBUK: { label: '충북', tone: 'neutral' },
  CHUNGNAM: { label: '충남', tone: 'neutral' },
  JEONBUK: { label: '전북', tone: 'neutral' },
  JEONNAM: { label: '전남', tone: 'neutral' },
  GYEONGBUK: { label: '경북', tone: 'neutral' },
  GYEONGNAM: { label: '경남', tone: 'neutral' },
  JEJU: { label: '제주', tone: 'neutral' },
} as const satisfies Record<HeadquartersRegion, StateDisplay>;

/**
 * **아직 판정하지 않는 자격 축**(M7 완료 조건 G2) — 입력이 있든 없든 V2 에 판정이 없는 축이다.
 * 화면은 **이 목록에서** 그 문면을 그린다(클라이언트가 어느 축이 미판정인지 추론하지 않는다).
 */
export const UNJUDGED_AXIS_DISPLAY = {
  QUAL_08_REGION: { label: '지역', why: '아직 판정하지 않는다', tone: 'unjudged' },
  QUAL_10_PERFORMANCE: { label: '수행실적', why: '아직 판정하지 않는다', tone: 'unjudged' },
  QUAL_11_CONSTRUCTION_CAPACITY: { label: '시공능력평가금액', why: '아직 판정하지 않는다', tone: 'unjudged' },
} as const satisfies Record<UnjudgedAxis, StateDisplay>;

/**
 * 자격 사실의 **세 상태**(계약) — `qualificationDeclared` 와 `licensesDeclared` 짝이 가른다.
 *
 * 왜 셋인가: `qualificationDeclared` 가 거짓이면 그때의 빈 목록은 **신고된 빈 값이 아니다**.
 * 둘을 한 얼굴로 접으면 「면허가 없다고 신고함」과 「아직 아무것도 적지 않음」이 같아 보인다.
 */
export type QualificationState = 'UNSET' | 'LICENSES_NOT_DECLARED' | 'DECLARED';

export const QUALIFICATION_STATE_DISPLAY = {
  UNSET: { label: '프로필 미설정', why: '빈 목록은 신고된 빈 값이 아니다', tone: 'absent' },
  LICENSES_NOT_DECLARED: { label: '면허 미신고', why: '업종·지역어는 신고했고 면허는 아직이다', tone: 'absent' },
  DECLARED: { label: '신고함', tone: 'neutral' },
} as const satisfies Record<QualificationState, StateDisplay>;

/** 판정은 **서버가 준 두 boolean** 에서만 나온다 — 목록의 길이로 추론하지 않는다. */
export function qualificationStateOf(profile: Profile): QualificationState {
  if (!profile.qualificationDeclared) {
    return 'UNSET';
  }
  return profile.licensesDeclared ? 'DECLARED' : 'LICENSES_NOT_DECLARED';
}

/** 사업 정보 폼이 쥔 값 — 전부 운영자 입력이고 기본값이 없다. */
export interface BusinessDetailsForm {
  readonly tradeName: string;
  readonly headquartersRegion: string;
  readonly naraRegistration: NaraRegistration;
  readonly naraRegisteredFields: readonly NaraField[];
  readonly businessCertificateExpiresOn: string;
}

/**
 * 사업 정보 요청 본문 — **절 단위 교체**다(부분 갱신이 다른 칸을 지우는 모양이 계약에 없다).
 *
 * 계약의 두 제약을 **본문의 모양으로** 지킨다:
 *  · `naraRegistration` 이 `REGISTERED` 가 **아니면** `naraRegisteredFields` ·
 *    `businessCertificateExpiresOn` 을 **실을 수 없다**(400). 받아 두고 버리지 않는다.
 *  · `REGISTERED` 이면 `naraRegisteredFields` 는 **필수**다(빈 배열 가능).
 *  · 상호는 **공백뿐이면 400** 이다 — 「미설정」은 키를 빼거나 `null` 이다. 그래서 빈 입력은
 *    `null` 로 보낸다(공백이 조용히 「지우기」가 되지 않게).
 */
/** 그 문자열이 계약의 소재지 어휘인가 — **표의 키**로 좁힌다(CR r1 L-9, 캐스팅 없이). */
export function isHeadquartersRegion(value: string): value is HeadquartersRegion {
  return Object.hasOwn(REGION_DISPLAY, value);
}

export function businessDetailsBodyOf(form: BusinessDetailsForm): BusinessDetailsRequest {
  const trimmed = form.tradeName.trim();
  const base: BusinessDetailsRequest = {
    naraRegistration: form.naraRegistration,
    tradeName: trimmed === '' ? null : form.tradeName,
    // 고르지 않았거나 표 밖의 값이면 `null` 이다 — 지어낸 소재지를 보내지 않는다.
    headquartersRegion: isHeadquartersRegion(form.headquartersRegion) ? form.headquartersRegion : null,
  };
  if (form.naraRegistration !== 'REGISTERED') {
    return base;
  }
  return {
    ...base,
    naraRegisteredFields: [...form.naraRegisteredFields],
    ...(form.businessCertificateExpiresOn === ''
      ? {}
      : { businessCertificateExpiresOn: form.businessCertificateExpiresOn }),
  };
}

/** 시평액 한 행의 입력 — 셋 다 계약이 필수로 둔 칸이다. */
export interface CapacityForm {
  readonly licenseName: string;
  readonly amountWon: string;
  readonly assessmentYear: string;
}

type ConstructionCapacityRequest = components['schemas']['ConstructionCapacityRequest'];

/**
 * 시평액 요청 본문 — **문자열 전체가 정수일 때만** 받는다(CR M-1 · v H-1 과 같은 처방).
 *
 * 왜 엄격해야 하는가: 앞 판은 `Number.parseInt` 로 앞자리만 읽었고 `'1,500,000,000'` → **1** 이
 * 서버로 갔다. 계약의 `minimum: 1` 이라 서버가 받고, 목록은 그 값을 `toLocaleString` 으로
 * 보이므로 **운영자가 보이는 꼴 그대로 되타이핑하는 것이 자연스러운 입력**이었다. 전략 쪽과 달리
 * 여기는 **확인 단계가 없어 더 조용하다**.
 *
 * `null` 은 「보내지 않는다」다 — 0 이나 올해를 지어내지 않는다.
 */
export function capacityBodyOf(form: CapacityForm): ConstructionCapacityRequest | null {
  const licenseName = form.licenseName.trim();
  const amount = form.amountWon.trim();
  const year = form.assessmentYear.trim();
  if (licenseName === '' || !INTEGER_SHAPE.test(amount) || !INTEGER_SHAPE.test(year)) {
    return null;
  }
  const amountWon = Number(amount);
  const assessmentYear = Number(year);
  // **안전 정수 범위를 넘으면 보내지 않는다**(v r2 L-1 · CR r2 L2R-3). 계약의 `maximum` 이
  // 1e15 라 서버도 소리를 내지만, 접힌 값을 보내고 서버가 거부하는 것과 **보내지 않는 것**은
  // 다르다 — 거부 사유를 운영자에게 말할 수 있는 쪽이 앞이다.
  if (!Number.isSafeInteger(amountWon) || !Number.isSafeInteger(assessmentYear)) {
    return null;
  }
  return { licenseName, amountWon, assessmentYear };
}

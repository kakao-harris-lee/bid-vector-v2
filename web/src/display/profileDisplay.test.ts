import { describe, expect, it } from 'vitest';

import type { components } from '../api/generated/operator-api';

import {
  businessDetailsBodyOf,
  capacityBodyOf,
  NARA_FIELD_DISPLAY,
  NARA_REGISTRATION_DISPLAY,
  QUALIFICATION_STATE_DISPLAY,
  qualificationStateOf,
  REGION_DISPLAY,
  UNJUDGED_AXIS_DISPLAY,
  type BusinessDetailsForm,
} from './profileDisplay';

type Profile = components['schemas']['Profile'];

function profile(overrides: Partial<Profile> = {}): Profile {
  return {
    qualificationDeclared: false,
    businessTypes: [],
    licensesDeclared: false,
    licenseNames: [],
    regionTerms: [],
    naraRegistration: 'UNANSWERED',
    naraRegisteredFields: [],
    unjudgedAxes: [],
    ...overrides,
  };
}

function form(overrides: Partial<BusinessDetailsForm> = {}): BusinessDetailsForm {
  return {
    tradeName: '',
    headquartersRegion: '',
    naraRegistration: 'UNANSWERED',
    naraRegisteredFields: [],
    businessCertificateExpiresOn: '',
    ...overrides,
  };
}

describe('프로필 표 — 계약 enum 전수', () => {
  it('나라장터 준비 셋이 전부 표에 있고 「미응답」이 값이다', () => {
    expect(Object.keys(NARA_REGISTRATION_DISPLAY).sort()).toStrictEqual(
      ['UNANSWERED', 'REGISTERED', 'NOT_REGISTERED'].sort(),
    );
    // 미응답과 미등록이 **다른 문면**이어야 한다 — 묻지 않은 것과 아니라고 답한 것은 다르다.
    expect(NARA_REGISTRATION_DISPLAY.UNANSWERED.label).not.toBe(NARA_REGISTRATION_DISPLAY.NOT_REGISTERED.label);
    expect(NARA_REGISTRATION_DISPLAY.UNANSWERED.tone).toBe('absent');
  });

  it('등록 분야 셋과 소재지 열일곱이 전부 표에 있다', () => {
    expect(Object.keys(NARA_FIELD_DISPLAY).sort()).toStrictEqual(['GOODS', 'SERVICE', 'CONSTRUCTION'].sort());
    expect(Object.keys(REGION_DISPLAY)).toHaveLength(17);
  });

  it('아직 판정하지 않는 축 셋이 전부 표에 있고 어조가 unjudged 다', () => {
    expect(Object.keys(UNJUDGED_AXIS_DISPLAY).sort()).toStrictEqual(
      ['QUAL_08_REGION', 'QUAL_10_PERFORMANCE', 'QUAL_11_CONSTRUCTION_CAPACITY'].sort(),
    );
    for (const display of Object.values(UNJUDGED_AXIS_DISPLAY)) {
      // 「아직 판정하지 않음」이 통과로도 미달로도 읽히면 안 된다.
      expect(display.tone).toBe('unjudged');
    }
  });
});

describe('자격 사실의 세 상태', () => {
  /** 빈 목록은 **신고된 빈 값이 아니다** — 그 구분이 이 세 상태의 요점이다. */
  it('미설정이면 빈 목록이 신고된 빈 값이 아니다', () => {
    expect(qualificationStateOf(profile({ qualificationDeclared: false }))).toBe('UNSET');
    expect(QUALIFICATION_STATE_DISPLAY.UNSET.why).toContain('신고된 빈 값이 아니다');
  });

  it('신고했고 면허가 없으면 「면허 미신고」다 — 미설정과 다른 얼굴이다', () => {
    const state = qualificationStateOf(profile({ qualificationDeclared: true, licensesDeclared: false }));

    expect(state).toBe('LICENSES_NOT_DECLARED');
    expect(QUALIFICATION_STATE_DISPLAY.LICENSES_NOT_DECLARED.label).not.toBe(
      QUALIFICATION_STATE_DISPLAY.UNSET.label,
    );
  });

  it('신고했고 면허가 없다고 신고한 것도 신고다', () => {
    expect(
      qualificationStateOf(profile({ qualificationDeclared: true, licensesDeclared: true, licenseNames: [] })),
    ).toBe('DECLARED');
  });

  /** **목록의 길이로 추론하지 않는다** — 판정은 서버가 준 두 boolean 에서만 나온다. */
  it('목록이 비어 있어도 신고 플래그가 참이면 신고다', () => {
    expect(
      qualificationStateOf(
        profile({ qualificationDeclared: true, licensesDeclared: true, businessTypes: [], regionTerms: [] }),
      ),
    ).toBe('DECLARED');
  });

  it('목록에 값이 있어도 미설정 플래그가 거짓이면 미설정이다', () => {
    expect(qualificationStateOf(profile({ qualificationDeclared: false, businessTypes: ['건축'] }))).toBe('UNSET');
  });
});

describe('사업 정보 요청 본문 — 계약의 모양을 지킨다', () => {
  /** `REGISTERED` 가 아니면 두 칸을 **실을 수 없다**(400) — 받아 두고 버리지 않는다. */
  it('등록이 아니면 분야·인증서 만료일을 싣지 않는다', () => {
    const body = businessDetailsBodyOf(
      form({
        naraRegistration: 'NOT_REGISTERED',
        naraRegisteredFields: ['GOODS'],
        businessCertificateExpiresOn: '2027-01-01',
      }),
    );

    expect(Object.keys(body).sort()).toStrictEqual(['naraRegistration', 'tradeName', 'headquartersRegion'].sort());
    expect('naraRegisteredFields' in body).toBe(false);
    expect('businessCertificateExpiresOn' in body).toBe(false);
  });

  it('미응답도 같다 — 그 두 칸이 가지 않는다', () => {
    const body = businessDetailsBodyOf(form({ naraRegistration: 'UNANSWERED', naraRegisteredFields: ['SERVICE'] }));

    expect('naraRegisteredFields' in body).toBe(false);
  });

  /** `REGISTERED` 이면 분야는 **필수**이고 빈 배열이 가능하다(계약). */
  it('등록이면 분야가 필수로 실리고 빈 배열도 보낸다', () => {
    const body = businessDetailsBodyOf(form({ naraRegistration: 'REGISTERED' }));

    expect(body.naraRegisteredFields).toStrictEqual([]);
  });

  it('등록이면 인증서 만료일은 적었을 때만 실린다', () => {
    expect('businessCertificateExpiresOn' in businessDetailsBodyOf(form({ naraRegistration: 'REGISTERED' }))).toBe(
      false,
    );
    expect(
      businessDetailsBodyOf(form({ naraRegistration: 'REGISTERED', businessCertificateExpiresOn: '2027-03-04' }))
        .businessCertificateExpiresOn,
    ).toBe('2027-03-04');
  });

  /**
   * 상호는 **공백뿐이면 400** 이다 — 「미설정」은 `null` 이다. 빈 입력을 공백 문자열로 보내면
   * 그것이 조용히 「지우기」가 되거나 400 으로 막힌다.
   */
  it('상호가 비거나 공백뿐이면 null 로 보낸다', () => {
    expect(businessDetailsBodyOf(form({ tradeName: '' })).tradeName).toBeNull();
    expect(businessDetailsBodyOf(form({ tradeName: '   ' })).tradeName).toBeNull();
  });

  /** 앞뒤 공백은 **자르지 않는다** — 계약이 「입력 원문 그대로」라 적는다(정규화는 커널 몫이다). */
  it('상호에 값이 있으면 앞뒤 공백을 자르지 않고 원문 그대로 보낸다', () => {
    expect(businessDetailsBodyOf(form({ tradeName: ' 합성건설 ' })).tradeName).toBe(' 합성건설 ');
  });

  it('소재지를 고르지 않으면 null 이다 — 지어낸 기본값이 없다', () => {
    expect(businessDetailsBodyOf(form({ headquartersRegion: '' })).headquartersRegion).toBeNull();
    expect(businessDetailsBodyOf(form({ headquartersRegion: 'JEJU' })).headquartersRegion).toBe('JEJU');
  });
});

/**
 * **CR M-1 의 회귀 test.** 앞 판은 `parseInt` 로 앞자리만 읽어 `'1,500,000,000'` → **1** 을
 * 보냈다(계약 `minimum: 1` 이라 서버가 받는다). 목록이 그 값을 `1,500,000,000원` 으로 보이므로
 * **보이는 꼴 그대로 되타이핑하는 것이 자연스러운 입력**이고, 전략 쪽과 달리 **확인 단계가 없다**.
 */
describe('시평액 요청 본문 — 문자열 전체가 정수일 때만', () => {
  const form = (overrides: Partial<Parameters<typeof capacityBodyOf>[0]> = {}) => ({
    licenseName: '토목공사업',
    amountWon: '1500000000',
    assessmentYear: '2026',
    ...overrides,
  });

  it('모양에 맞으면 수로 바꿔 보낸다', () => {
    expect(capacityBodyOf(form())).toStrictEqual({
      licenseName: '토목공사업',
      amountWon: 1_500_000_000,
      assessmentYear: 2026,
    });
  });

  it.each([
    ['자리 구분 쉼표', { amountWon: '1,500,000,000' }],
    ['단위', { amountWon: '15억' }],
    ['소수점', { amountWon: '1500.5' }],
    ['음수', { amountWon: '-1' }],
    ['지수', { amountWon: '1.5e9' }],
    ['꼬리 문자', { amountWon: '1500000000원' }],
    ['연도의 쉼표', { assessmentYear: '2,026' }],
    ['연도의 소수점', { assessmentYear: '2026.5' }],
    ['빈 금액', { amountWon: '' }],
    ['빈 연도', { assessmentYear: '' }],
    ['공백뿐인 면허명', { licenseName: '   ' }],
  ])('%s 은 보내지 않는다', (_label, overrides) => {
    expect(capacityBodyOf(form(overrides))).toBeNull();
  });

  it('앞뒤 공백만 정규화한다', () => {
    expect(capacityBodyOf(form({ licenseName: ' 토목공사업 ', amountWon: ' 10 ', assessmentYear: ' 2026 ' }))).toStrictEqual(
      { licenseName: '토목공사업', amountWon: 10, assessmentYear: 2026 },
    );
  });
});

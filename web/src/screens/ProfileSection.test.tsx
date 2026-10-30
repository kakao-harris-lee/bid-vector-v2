// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../api/apiError';
import * as queries from '../api/queries';
import { NARA_REGISTRATION_DISPLAY, QUALIFICATION_STATE_DISPLAY } from '../display/profileDisplay';

import { ProfileSection } from './ProfileSection';

vi.mock('../api/queries');

type Profile = queries.Profile;

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

function renderSection() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <ProfileSection />
    </QueryClientProvider>,
  );
}

const mocked = vi.mocked(queries);

beforeEach(() => {
  vi.resetAllMocks();
  mocked.readProfile.mockResolvedValue(profile());
  mocked.listConstructionCapacities.mockResolvedValue([]);
});

afterEach(() => {
  cleanup();
});

describe('프로필 — 보이는 값은 API 가 준 값이다', () => {
  it('미설정이면 그 얼굴이고 목록은 빈 자리다', async () => {
    renderSection();

    expect((await screen.findByTestId('qualification-state')).textContent).toContain(
      QUALIFICATION_STATE_DISPLAY.UNSET.label,
    );
    expect((await screen.findByTestId('profile-business-types')).textContent).toBe('값 없음');
  });

  /** 빈 목록은 **신고된 빈 값이 아니다** — 두 얼굴이 달라야 한다. */
  it('면허 미신고와 미설정이 다른 얼굴이다', async () => {
    mocked.readProfile.mockResolvedValue(profile({ qualificationDeclared: true, licensesDeclared: false }));
    renderSection();

    expect((await screen.findByTestId('qualification-state')).textContent).toContain(
      QUALIFICATION_STATE_DISPLAY.LICENSES_NOT_DECLARED.label,
    );
  });

  it('나라장터 「미응답」을 미등록으로 접지 않는다', async () => {
    renderSection();

    const badge = await screen.findByTestId('nara-registration');
    expect(badge.textContent).toContain(NARA_REGISTRATION_DISPLAY.UNANSWERED.label);
    expect(badge.textContent).not.toContain(NARA_REGISTRATION_DISPLAY.NOT_REGISTERED.label);
  });

  it('상호·소재지·등록 분야를 API 가 준 그대로 보인다', async () => {
    mocked.readProfile.mockResolvedValue(
      profile({
        tradeName: '합성건설',
        headquartersRegion: 'JEJU',
        naraRegistration: 'REGISTERED',
        naraRegisteredFields: ['CONSTRUCTION', 'GOODS'],
      }),
    );
    renderSection();

    expect((await screen.findByTestId('profile-trade-name')).textContent).toBe('합성건설');
    expect((await screen.findByTestId('profile-region')).textContent).toBe('제주');
    expect((await screen.findByTestId('profile-nara-fields')).textContent).toBe('공사, 물품');
  });

  /** G2 — 「아직 판정하지 않는 축」은 **서버가 준 목록**에서 그린다. */
  it('미판정 축을 서버가 준 목록 그대로 그린다', async () => {
    mocked.readProfile.mockResolvedValue(
      profile({ unjudgedAxes: ['QUAL_08_REGION', 'QUAL_11_CONSTRUCTION_CAPACITY'] }),
    );
    renderSection();

    const box = await screen.findByTestId('unjudged-axes');
    expect(within(box).getByTestId('unjudged-QUAL_08_REGION')).toBeTruthy();
    expect(within(box).getByTestId('unjudged-QUAL_11_CONSTRUCTION_CAPACITY')).toBeTruthy();
    expect(within(box).queryByTestId('unjudged-QUAL_10_PERFORMANCE')).toBeNull();
  });

  it('서버가 미판정 축을 비워 보내면 그 사실을 말한다 — 셋을 지어내지 않는다', async () => {
    renderSection();

    const box = await screen.findByTestId('unjudged-axes');
    expect(box.textContent).toContain('비워 보냈습니다');
    expect(within(box).queryByTestId('unjudged-QUAL_08_REGION')).toBeNull();
  });
});

describe('프로필 — 쓰기는 계약의 모양을 지킨다', () => {
  it('자격 사실을 절 단위로 교체한다', async () => {
    mocked.replaceQualificationFacts.mockResolvedValue(profile({ qualificationDeclared: true }));
    renderSection();

    fireEvent.change(await screen.findByLabelText('업종'), { target: { value: '건축, 토목' } });
    fireEvent.change(screen.getByLabelText('지역어'), { target: { value: '제주' } });
    fireEvent.click(screen.getByRole('button', { name: '자격 사실 저장' }));

    await waitFor(() => expect(mocked.replaceQualificationFacts).toHaveBeenCalled());
    expect(mocked.replaceQualificationFacts.mock.calls[0]![0]).toStrictEqual({
      businessTypes: ['건축', '토목'],
      licensesDeclared: false,
      licenseNames: [],
      regionTerms: ['제주'],
    });
  });

  /** 신고하지 않으면 면허 목록은 **비어 있어야 한다**(계약 — 아니면 400). */
  it('면허 미신고면 적어 둔 면허를 보내지 않는다', async () => {
    mocked.readProfile.mockResolvedValue(
      profile({ qualificationDeclared: true, licensesDeclared: true, licenseNames: ['토목공사업'] }),
    );
    mocked.replaceQualificationFacts.mockResolvedValue(profile());
    renderSection();

    fireEvent.click(await screen.findByLabelText('면허를 신고함'));
    fireEvent.click(screen.getByRole('button', { name: '자격 사실 저장' }));

    await waitFor(() => expect(mocked.replaceQualificationFacts).toHaveBeenCalled());
    const body = mocked.replaceQualificationFacts.mock.calls[0]![0];
    expect(body.licensesDeclared).toBe(false);
    expect(body.licenseNames).toStrictEqual([]);
  });

  /** 등록이 아니면 두 칸은 **열리지도 않고 가지도 않는다**. */
  it('미응답이면 등록 분야·인증서 칸이 없고 본문에도 없다', async () => {
    mocked.replaceBusinessDetails.mockResolvedValue(profile());
    renderSection();

    await screen.findByLabelText('상호');
    expect(screen.queryByTestId('nara-fields-choice')).toBeNull();
    expect(screen.queryByLabelText('인증서 만료일')).toBeNull();

    fireEvent.click(screen.getByRole('button', { name: '사업 정보 저장' }));

    await waitFor(() => expect(mocked.replaceBusinessDetails).toHaveBeenCalled());
    const body = mocked.replaceBusinessDetails.mock.calls[0]![0];
    expect('naraRegisteredFields' in body).toBe(false);
    expect('businessCertificateExpiresOn' in body).toBe(false);
  });

  it('등록을 고르면 분야 칸이 열리고 고른 것이 그대로 간다', async () => {
    mocked.replaceBusinessDetails.mockResolvedValue(profile());
    renderSection();

    fireEvent.change(await screen.findByLabelText('나라장터 준비'), { target: { value: 'REGISTERED' } });
    expect(await screen.findByTestId('nara-fields-choice')).toBeTruthy();
    fireEvent.click(screen.getByLabelText('공사'));
    fireEvent.click(screen.getByRole('button', { name: '사업 정보 저장' }));

    await waitFor(() => expect(mocked.replaceBusinessDetails).toHaveBeenCalled());
    const body = mocked.replaceBusinessDetails.mock.calls[0]![0];
    expect(body.naraRegistration).toBe('REGISTERED');
    expect(body.naraRegisteredFields).toStrictEqual(['CONSTRUCTION']);
  });

  it('상호가 비면 null 로 보낸다 — 공백이 조용히 지우기가 되지 않게', async () => {
    mocked.replaceBusinessDetails.mockResolvedValue(profile());
    renderSection();

    fireEvent.change(await screen.findByLabelText('상호'), { target: { value: '   ' } });
    fireEvent.click(screen.getByRole('button', { name: '사업 정보 저장' }));

    await waitFor(() => expect(mocked.replaceBusinessDetails).toHaveBeenCalled());
    expect(mocked.replaceBusinessDetails.mock.calls[0]![0].tradeName).toBeNull();
  });

  it('소재지를 고르기 전에는 빈 자리다 — 첫 지역이 기본값이 아니다', async () => {
    renderSection();

    const select = (await screen.findByLabelText('본사 소재지')) as HTMLSelectElement;
    expect(select.value).toBe('');
  });
});

describe('프로필 — 시공능력평가액은 행 단위다', () => {
  it('행이 없으면 그 사실을 말한다', async () => {
    renderSection();

    expect(await screen.findByTestId('capacity-empty')).toBeTruthy();
  });

  it('행을 API 가 준 그대로 보인다', async () => {
    mocked.listConstructionCapacities.mockResolvedValue([
      { licenseName: '토목공사업', amountWon: 1_500_000_000, assessmentYear: 2026 },
    ]);
    renderSection();

    const row = await screen.findByTestId('capacity-토목공사업');
    expect(row.textContent).toContain('1,500,000,000원');
    expect(row.textContent).toContain('2026년 평가');
  });

  /** 셋 다 필수다(계약) — **0 이나 올해를 기본값으로 지어내지 않는다**. */
  it('셋 가운데 하나라도 없으면 보내지 않는다', async () => {
    renderSection();

    fireEvent.change(await screen.findByLabelText('시평액 면허명'), { target: { value: '토목공사업' } });
    fireEvent.click(screen.getByRole('button', { name: '시평액 저장' }));

    expect(await screen.findByTestId('capacity-missing')).toBeTruthy();
    expect(mocked.upsertConstructionCapacity).not.toHaveBeenCalled();
  });

  it('셋 다 있으면 수로 바꿔 보낸다', async () => {
    mocked.upsertConstructionCapacity.mockResolvedValue([]);
    renderSection();

    fireEvent.change(await screen.findByLabelText('시평액 면허명'), { target: { value: '토목공사업' } });
    fireEvent.change(screen.getByLabelText('시평액 금액'), { target: { value: '1500000000' } });
    fireEvent.change(screen.getByLabelText('평가 연도'), { target: { value: '2026' } });
    fireEvent.click(screen.getByRole('button', { name: '시평액 저장' }));

    await waitFor(() => expect(mocked.upsertConstructionCapacity).toHaveBeenCalled());
    expect(mocked.upsertConstructionCapacity.mock.calls[0]![0]).toStrictEqual({
      licenseName: '토목공사업',
      amountWon: 1_500_000_000,
      assessmentYear: 2026,
    });
  });

  it('지우기는 면허명으로 보낸다', async () => {
    mocked.listConstructionCapacities.mockResolvedValue([
      { licenseName: '토목공사업', amountWon: 1, assessmentYear: 2026 },
    ]);
    mocked.removeConstructionCapacity.mockResolvedValue([]);
    renderSection();

    const row = await screen.findByTestId('capacity-토목공사업');
    fireEvent.click(within(row).getByRole('button', { name: '지우기' }));

    await waitFor(() =>
      expect(mocked.removeConstructionCapacity).toHaveBeenCalledWith({ licenseName: '토목공사업' }),
    );
  });
});

describe('프로필 — 실패 얼굴', () => {
  it('읽기가 실패하면 오류 얼굴이다', async () => {
    mocked.readProfile.mockRejectedValue(new ApiError(500, 'INTERNAL_ERROR'));
    renderSection();

    expect(await screen.findByTestId('profile-error')).toBeTruthy();
  });

  it('쓰기가 실패하면 얼굴을 내고 프로필을 다시 읽는다', async () => {
    mocked.replaceBusinessDetails.mockRejectedValue(new ApiError(400, 'INVALID_REQUEST'));
    renderSection();

    await screen.findByLabelText('상호');
    fireEvent.click(screen.getByRole('button', { name: '사업 정보 저장' }));

    expect(await screen.findByTestId('write-failure')).toBeTruthy();
    expect(mocked.readProfile.mock.calls.length).toBeGreaterThan(1);
  });
});

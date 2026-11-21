import { useState } from 'react';

import { replaceBusinessDetails, type Profile } from '../api/queries';
import { PROFILE_QUERY_KEY } from '../api/queryKeys';
import { Button } from '../components/Button';
import { StateBadge } from '../components/StateBadge';
import { KeyValueRow } from '../components/KeyValueRow';
import {
  BUSINESS_REGISTRATION_NUMBER_TEST_ID,
  businessDetailsBodyOf,
  businessRegistrationNumberText,
  NARA_FIELD_DISPLAY,
  NARA_REGISTRATION_DISPLAY,
  REGION_DISPLAY,
  type BusinessDetailsForm,
  type NaraField,
  type NaraRegistration,
} from '../display/profileDisplay';
import { displayOrUnknown } from '../display/stateDisplay';

import { attemptOf, ProfileWriteFailure, useProfileWrite } from './profileWrites';

function detailsFormOf(profile: Profile): BusinessDetailsForm {
  return {
    tradeName: profile.tradeName ?? '',
    // 계약의 값은 숫자 10자리다 — 운영자가 읽는 꼴로 **서식만** 입힌다(그대로 돌려보내도 같은 값).
    businessRegistrationNumber:
      profile.businessRegistrationNumber == null
        ? ''
        : businessRegistrationNumberText(profile.businessRegistrationNumber),
    headquartersRegion: profile.headquartersRegion ?? '',
    naraRegistration: profile.naraRegistration,
    naraRegisteredFields: profile.naraRegisteredFields,
    businessCertificateExpiresOn: profile.businessCertificateExpiresOn ?? '',
  };
}

/**
 * 사업 정보(상호·본사 소재지·나라장터 준비) — **절 단위 교체**이고 「미응답」이 값이다.
 *
 * 체크리스트 항목 `TRADE_NAME`·`NARA_READINESS`·`HEADQUARTERS_REGION` 셋이 이 폼 하나에 묶인다
 * (M7/7E-2b 착수 실측) — 항목마다 따로 저장하면 같은 절을 세 번 덮어쓴다.
 *
 * **사업자번호는 선택 입력이다**(M15/15C): V30 이 열을 두고 계약이 칸을 열어, 적은 값이 갈 곳이
 * 생겼다. 비워 두어도 어느 화면도 막지 않는다(체크리스트 항목이 아니다).
 *
 * 그 칸은 **운영자 자기 계정의 값**이라 누출 게이트의 예외 하나다 — 예외는 testId
 * [BUSINESS_REGISTRATION_NUMBER_TEST_ID] 하나로 좁혀지고, 그래서 이 값을 **다른 자리에
 * 그리지 않는다**(위 읽기 전용 줄 목록에도 넣지 않는다). 예외가 하나면 「어디까지 열렸는가」가
 * 한 줄로 읽힌다.
 */
export function ProfileDetailsCard({
  profile,
  onSaved,
}: {
  readonly profile: Profile;
  readonly onSaved?: () => void;
}) {
  const [details, setDetails] = useState<BusinessDetailsForm | null>(null);
  const save = useProfileWrite(replaceBusinessDetails, PROFILE_QUERY_KEY, () => {
    setDetails(null);
    onSaved?.();
  });

  const form = details ?? detailsFormOf(profile);

  return (
    <>
      <ProfileWriteFailure attempts={[attemptOf(save)]} />
      <div className="rounded-md border border-line p-3">
        <div className="flex flex-wrap items-center gap-2">
          <span className="text-sm font-medium">사업 정보</span>
          <StateBadge
            display={displayOrUnknown(NARA_REGISTRATION_DISPLAY, profile.naraRegistration)}
            testId="nara-registration"
          />
        </div>
        <dl className="mt-3 grid gap-3 sm:grid-cols-3">
          <KeyValueRow label="상호" value={profile.tradeName ?? null} testId="profile-trade-name" />
          <KeyValueRow
            label="본사 소재지"
            value={
              profile.headquartersRegion === null || profile.headquartersRegion === undefined
                ? null
                : displayOrUnknown(REGION_DISPLAY, profile.headquartersRegion).label
            }
            testId="profile-region"
          />
          <KeyValueRow
            label="등록 분야"
            value={
              profile.naraRegisteredFields.length === 0
                ? null
                : profile.naraRegisteredFields
                    .map((one) => displayOrUnknown(NARA_FIELD_DISPLAY, one).label)
                    .join(', ')
            }
            testId="profile-nara-fields"
          />
        </dl>
        <div className="mt-3 grid gap-2 sm:grid-cols-2">
          <label className="flex flex-col gap-1">
            <span className="text-xs text-ink-soft">상호</span>
            <input
              data-testid="profile-trade-name-input"
              value={form.tradeName}
              onChange={(event) => setDetails({ ...form, tradeName: event.target.value })}
              className="min-h-11 rounded-md border border-control-line bg-card px-3 text-sm"
            />
          </label>
          <label className="flex flex-col gap-1">
            <span className="text-xs text-ink-soft">사업자번호 (선택)</span>
            <input
              data-testid={BUSINESS_REGISTRATION_NUMBER_TEST_ID}
              inputMode="numeric"
              value={form.businessRegistrationNumber}
              onChange={(event) =>
                setDetails({ ...form, businessRegistrationNumber: event.target.value })
              }
              className="min-h-11 rounded-md border border-control-line bg-card px-3 text-sm"
            />
            {/* 보기 글(placeholder)로 번호 **꼴의 예시**를 두지 않는다 — 그 글자가 화면에 서는
                순간 누출 게이트가 보는 값이 되고, 예외 하나로 좁힌 뜻이 흐려진다. */}
            <span className="text-xs text-ink-soft">숫자 열 자리로 적습니다 — 하이픈을 넣어도 됩니다.</span>
          </label>
          <label className="flex flex-col gap-1">
            <span className="text-xs text-ink-soft">본사 소재지</span>
            <select
              value={form.headquartersRegion}
              onChange={(event) => setDetails({ ...form, headquartersRegion: event.target.value })}
              className="min-h-11 rounded-md border border-control-line bg-card px-3 text-sm"
            >
              {/* 고르기 전에는 **빈 자리**다 — 첫 지역을 기본값으로 두지 않는다. */}
              <option value="">고르지 않음</option>
              {Object.entries(REGION_DISPLAY).map(([value, display]) => (
                <option key={value} value={value}>
                  {display.label}
                </option>
              ))}
            </select>
          </label>
          <label className="flex flex-col gap-1">
            <span className="text-xs text-ink-soft">나라장터 준비</span>
            <select
              value={form.naraRegistration}
              onChange={(event) =>
                setDetails({ ...form, naraRegistration: event.target.value as NaraRegistration })
              }
              className="min-h-11 rounded-md border border-control-line bg-card px-3 text-sm"
            >
              {Object.entries(NARA_REGISTRATION_DISPLAY).map(([value, display]) => (
                <option key={value} value={value}>
                  {display.label}
                </option>
              ))}
            </select>
          </label>
          {/* 등록일 때만 열린다 — 계약이 그때만 받는다(아니면 400). */}
          {form.naraRegistration === 'REGISTERED' ? (
            <label className="flex flex-col gap-1">
              <span className="text-xs text-ink-soft">인증서 만료일</span>
              <input
                type="date"
                value={form.businessCertificateExpiresOn}
                onChange={(event) => setDetails({ ...form, businessCertificateExpiresOn: event.target.value })}
                className="min-h-11 rounded-md border border-control-line bg-card px-3 text-sm"
              />
            </label>
          ) : null}
        </div>
        {form.naraRegistration === 'REGISTERED' ? (
          <fieldset data-testid="nara-fields-choice" className="mt-2">
            <legend className="text-xs text-ink-soft">등록 분야</legend>
            <div className="mt-1 flex flex-wrap gap-3">
              {/* 손가락이 닿는 상자는 **레이블**이다(M13/13B cr P-3) — 암묵 레이블이 `min-h-11` 을 든다. */}
              {Object.entries(NARA_FIELD_DISPLAY).map(([value, display]) => (
                <label key={value} className="flex min-h-11 items-center gap-1 text-sm">
                  <input
                    type="checkbox"
                    checked={form.naraRegisteredFields.includes(value as NaraField)}
                    onChange={(event) =>
                      setDetails({
                        ...form,
                        naraRegisteredFields: event.target.checked
                          ? [...form.naraRegisteredFields, value as NaraField]
                          : form.naraRegisteredFields.filter((one) => one !== value),
                      })
                    }
                  />
                  {display.label}
                </label>
              ))}
            </div>
          </fieldset>
        ) : null}
        <div className="mt-2">
          <Button tone="primary" disabled={save.isPending} onClick={() => save.mutate(businessDetailsBodyOf(form))}>
            사업 정보 저장
          </Button>
        </div>
      </div>
    </>
  );
}

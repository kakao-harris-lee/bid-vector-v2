import { useState } from 'react';

import { replaceBusinessDetails, type Profile } from '../api/queries';
import { Field, StateBadge } from '../components/StateBadge';
import {
  businessDetailsBodyOf,
  NARA_FIELD_DISPLAY,
  NARA_REGISTRATION_DISPLAY,
  REGION_DISPLAY,
  type BusinessDetailsForm,
  type NaraField,
  type NaraRegistration,
} from '../display/profileDisplay';
import { displayOrUnknown } from '../display/stateDisplay';

import { attemptOf, PROFILE_QUERY_KEY, ProfileWriteFailure, useProfileWrite } from './profileWrites';

function detailsFormOf(profile: Profile): BusinessDetailsForm {
  return {
    tradeName: profile.tradeName ?? '',
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
 * **사업자번호 입력칸이 없다**(D-7E2-7): 계약의 `Profile`·`BusinessDetailsRequest` 에 그 칸이
 * 0 이고 `/business-details` 문면이 「자리가 없다」고 적는다. 받으면 값이 클라이언트에 머물고
 * 그것은 API 밖 값 보관이다 — 그 자리는 설정 화면의 「준비 중」 목록에 있다.
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
          <Field label="상호" value={profile.tradeName ?? null} testId="profile-trade-name" />
          <Field
            label="본사 소재지"
            value={
              profile.headquartersRegion === null || profile.headquartersRegion === undefined
                ? null
                : displayOrUnknown(REGION_DISPLAY, profile.headquartersRegion).label
            }
            testId="profile-region"
          />
          <Field
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
              aria-label="상호"
              value={form.tradeName}
              onChange={(event) => setDetails({ ...form, tradeName: event.target.value })}
              className="rounded-md border border-line bg-card px-2 py-1 text-sm"
            />
          </label>
          <label className="flex flex-col gap-1">
            <span className="text-xs text-ink-soft">본사 소재지</span>
            <select
              aria-label="본사 소재지"
              value={form.headquartersRegion}
              onChange={(event) => setDetails({ ...form, headquartersRegion: event.target.value })}
              className="rounded-md border border-line bg-card px-2 py-1 text-sm"
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
              aria-label="나라장터 준비"
              value={form.naraRegistration}
              onChange={(event) =>
                setDetails({ ...form, naraRegistration: event.target.value as NaraRegistration })
              }
              className="rounded-md border border-line bg-card px-2 py-1 text-sm"
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
                aria-label="인증서 만료일"
                value={form.businessCertificateExpiresOn}
                onChange={(event) => setDetails({ ...form, businessCertificateExpiresOn: event.target.value })}
                className="rounded-md border border-line bg-card px-2 py-1 text-sm"
              />
            </label>
          ) : null}
        </div>
        {form.naraRegistration === 'REGISTERED' ? (
          <fieldset data-testid="nara-fields-choice" className="mt-2">
            <legend className="text-xs text-ink-soft">등록 분야</legend>
            <div className="mt-1 flex flex-wrap gap-3">
              {Object.entries(NARA_FIELD_DISPLAY).map(([value, display]) => (
                <label key={value} className="flex items-center gap-1 text-sm">
                  <input
                    type="checkbox"
                    aria-label={display.label}
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
        <button
          type="button"
          disabled={save.isPending}
          onClick={() => save.mutate(businessDetailsBodyOf(form))}
          className="mt-2 rounded-md bg-accent px-3 py-1 text-sm font-medium text-accent-ink disabled:opacity-50"
        >
          사업 정보 저장
        </button>
      </div>
    </>
  );
}

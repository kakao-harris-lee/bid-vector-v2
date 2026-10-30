import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';

import {
  listConstructionCapacities,
  readProfile,
  removeConstructionCapacity,
  replaceBusinessDetails,
  replaceQualificationFacts,
  upsertConstructionCapacity,
  type ConstructionCapacity,
  type Profile,
} from '../api/queries';
import { ErrorFace, Skeleton } from '../components/ScreenFaces';
import { Field, StateBadge } from '../components/StateBadge';
import { isUnauthorized, WriteFailureFace } from '../components/WriteFailure';
import {
  businessDetailsBodyOf,
  NARA_FIELD_DISPLAY,
  NARA_REGISTRATION_DISPLAY,
  QUALIFICATION_STATE_DISPLAY,
  qualificationStateOf,
  REGION_DISPLAY,
  UNJUDGED_AXIS_DISPLAY,
  type BusinessDetailsForm,
  type NaraField,
  type NaraRegistration,
} from '../display/profileDisplay';
import { displayOrUnknown } from '../display/stateDisplay';
import { termsOf } from '../display/editSessionDisplay';

const PROFILE_KEY = ['profile'] as const;
const CAPACITY_KEY = ['construction-capacities'] as const;

/** 자격 사실 폼 — 세 어휘 배열과 신고 여부. 기본값을 지어내지 않는다. */
interface FactsForm {
  readonly businessTypes: string;
  readonly licensesDeclared: boolean;
  readonly licenseNames: string;
  readonly regionTerms: string;
}

/** 시평액 한 행의 입력 — 셋 다 필수다(계약). */
interface CapacityForm {
  readonly licenseName: string;
  readonly amountWon: string;
  readonly assessmentYear: string;
}

const EMPTY_CAPACITY: CapacityForm = { licenseName: '', amountWon: '', assessmentYear: '' };

/** 서버가 준 프로필에서 폼의 출발값을 만든다 — **값을 더하지 않는다**(보이는 것이 그대로다). */
function factsFormOf(profile: Profile): FactsForm {
  return {
    businessTypes: profile.businessTypes.join(', '),
    licensesDeclared: profile.licensesDeclared,
    licenseNames: profile.licenseNames.join(', '),
    regionTerms: profile.regionTerms.join(', '),
  };
}

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
 * 프로필 설정(D-7E2-3) — 자격 사실·사업 정보는 **절 단위 교체**이고 시평액은 행 단위다.
 *
 * **사업자번호 입력칸이 없다**(D-7E2-7): 계약의 `Profile`·`BusinessDetailsRequest` 에 그 칸이
 * 0 이고 `/business-details` 문면이 「자리가 없다」고 적는다. 받으면 값이 클라이언트에 머물고
 * 그것은 API 밖 값 보관이다 — 그 자리는 설정 화면의 「준비 중」 목록에 있다.
 *
 * 「아직 판정하지 않는 축」은 **서버가 준 목록**(`unjudgedAxes`)에서 그린다 — 어느 축이
 * 미판정인지 화면이 추론하지 않는다(G2).
 */
export function ProfileSection() {
  const queryClient = useQueryClient();
  const [facts, setFacts] = useState<FactsForm | null>(null);
  const [details, setDetails] = useState<BusinessDetailsForm | null>(null);
  const [capacity, setCapacity] = useState<CapacityForm>(EMPTY_CAPACITY);
  const [capacityMissing, setCapacityMissing] = useState(false);

  const profile = useQuery({ queryKey: PROFILE_KEY, queryFn: readProfile });
  const capacities = useQuery({ queryKey: CAPACITY_KEY, queryFn: listConstructionCapacities });

  const saveFacts = useMutation({
    mutationFn: (form: FactsForm) =>
      replaceQualificationFacts({
        businessTypes: termsOf(form.businessTypes),
        licensesDeclared: form.licensesDeclared,
        // 신고하지 않으면 **비어 있어야 한다**(계약 — 아니면 400). 받아 두고 버리지 않는다.
        licenseNames: form.licensesDeclared ? termsOf(form.licenseNames) : [],
        regionTerms: termsOf(form.regionTerms),
      }),
    onSuccess: async () => {
      setFacts(null);
      await queryClient.invalidateQueries({ queryKey: PROFILE_KEY });
    },
    onError: async () => {
      await queryClient.invalidateQueries({ queryKey: PROFILE_KEY });
    },
  });

  const saveDetails = useMutation({
    mutationFn: (form: BusinessDetailsForm) => replaceBusinessDetails(businessDetailsBodyOf(form)),
    onSuccess: async () => {
      setDetails(null);
      await queryClient.invalidateQueries({ queryKey: PROFILE_KEY });
    },
    onError: async () => {
      await queryClient.invalidateQueries({ queryKey: PROFILE_KEY });
    },
  });

  const saveCapacity = useMutation({
    mutationFn: (form: CapacityForm) =>
      upsertConstructionCapacity({
        licenseName: form.licenseName,
        amountWon: Number.parseInt(form.amountWon, 10),
        assessmentYear: Number.parseInt(form.assessmentYear, 10),
      }),
    onSuccess: async () => {
      setCapacity(EMPTY_CAPACITY);
      await queryClient.invalidateQueries({ queryKey: CAPACITY_KEY });
    },
    onError: async () => {
      await queryClient.invalidateQueries({ queryKey: CAPACITY_KEY });
    },
  });

  const dropCapacity = useMutation({
    mutationFn: (licenseName: string) => removeConstructionCapacity({ licenseName }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: CAPACITY_KEY });
    },
    onError: async () => {
      await queryClient.invalidateQueries({ queryKey: CAPACITY_KEY });
    },
  });

  if (profile.isPending) {
    return <Skeleton testId="profile-loading" />;
  }
  if (profile.isError) {
    return isUnauthorized(profile.error) ? null : (
      <ErrorFace testId="profile-error" onRetry={() => void profile.refetch()} />
    );
  }

  const current = profile.data;
  const qualification = qualificationStateOf(current);
  const factsForm = facts ?? factsFormOf(current);
  const detailsForm = details ?? detailsFormOf(current);
  // 넷 가운데 **지금 실패한 것** 하나. `??` 연쇄는 `null` 을 건너뛰므로 실패가 없으면 `null` 이다.
  const writeError: unknown = saveFacts.error ?? saveDetails.error ?? saveCapacity.error ?? dropCapacity.error;

  return (
    <section data-testid="settings-profile" className="flex flex-col gap-4">
      <h3 className="text-base font-semibold">프로필</h3>

      {writeError === null ? null : (
        <WriteFailureFace
          what="프로필을 저장하지"
          error={writeError}
          onRetry={() => {
            // 마지막으로 실패한 쓰기만 다시 보낸다 — 넷을 한꺼번에 재전송하지 않는다.
            if (saveFacts.isError && saveFacts.variables !== undefined) {
              saveFacts.mutate(saveFacts.variables);
            } else if (saveDetails.isError && saveDetails.variables !== undefined) {
              saveDetails.mutate(saveDetails.variables);
            } else if (saveCapacity.isError && saveCapacity.variables !== undefined) {
              saveCapacity.mutate(saveCapacity.variables);
            } else if (dropCapacity.isError && dropCapacity.variables !== undefined) {
              dropCapacity.mutate(dropCapacity.variables);
            }
          }}
        />
      )}

      {/* 자격 사실 — 세 상태가 **서버의 두 boolean** 에서 나온다. */}
      <div className="rounded-md border border-line p-3">
        <div className="flex flex-wrap items-center gap-2">
          <span className="text-sm font-medium">자격 사실</span>
          <StateBadge
            display={displayOrUnknown(QUALIFICATION_STATE_DISPLAY, qualification)}
            testId="qualification-state"
          />
        </div>
        <dl className="mt-3 grid gap-3 sm:grid-cols-3">
          <Field
            label="업종"
            value={current.businessTypes.length === 0 ? null : current.businessTypes.join(', ')}
            testId="profile-business-types"
          />
          <Field
            label="면허"
            value={current.licenseNames.length === 0 ? null : current.licenseNames.join(', ')}
            testId="profile-licenses"
          />
          <Field
            label="지역어"
            value={current.regionTerms.length === 0 ? null : current.regionTerms.join(', ')}
            testId="profile-region-terms"
          />
        </dl>
        <div className="mt-3 grid gap-2 sm:grid-cols-3">
          <label className="flex flex-col gap-1">
            <span className="text-xs text-ink-soft">업종(쉼표)</span>
            <input
              aria-label="업종"
              value={factsForm.businessTypes}
              onChange={(event) => setFacts({ ...factsForm, businessTypes: event.target.value })}
              className="rounded-md border border-line bg-card px-2 py-1 text-sm"
            />
          </label>
          <label className="flex flex-col gap-1">
            <span className="text-xs text-ink-soft">면허(쉼표)</span>
            <input
              aria-label="면허"
              disabled={!factsForm.licensesDeclared}
              value={factsForm.licenseNames}
              onChange={(event) => setFacts({ ...factsForm, licenseNames: event.target.value })}
              className="rounded-md border border-line bg-card px-2 py-1 text-sm disabled:opacity-50"
            />
          </label>
          <label className="flex flex-col gap-1">
            <span className="text-xs text-ink-soft">지역어(쉼표)</span>
            <input
              aria-label="지역어"
              value={factsForm.regionTerms}
              onChange={(event) => setFacts({ ...factsForm, regionTerms: event.target.value })}
              className="rounded-md border border-line bg-card px-2 py-1 text-sm"
            />
          </label>
        </div>
        <label className="mt-2 flex items-center gap-2 text-sm">
          <input
            type="checkbox"
            aria-label="면허를 신고함"
            checked={factsForm.licensesDeclared}
            onChange={(event) => setFacts({ ...factsForm, licensesDeclared: event.target.checked })}
          />
          면허를 신고함
          <span className="text-xs text-ink-soft">(끄면 면허 목록을 비워 보냅니다)</span>
        </label>
        <button
          type="button"
          disabled={saveFacts.isPending}
          onClick={() => saveFacts.mutate(factsForm)}
          className="mt-2 rounded-md bg-accent px-3 py-1 text-sm font-medium text-accent-ink disabled:opacity-50"
        >
          자격 사실 저장
        </button>
      </div>

      {/* 사업 정보 — 「미응답」이 값이고 등록일 때만 두 칸이 열린다. */}
      <div className="rounded-md border border-line p-3">
        <div className="flex flex-wrap items-center gap-2">
          <span className="text-sm font-medium">사업 정보</span>
          <StateBadge
            display={displayOrUnknown(NARA_REGISTRATION_DISPLAY, current.naraRegistration)}
            testId="nara-registration"
          />
        </div>
        <dl className="mt-3 grid gap-3 sm:grid-cols-3">
          <Field label="상호" value={current.tradeName ?? null} testId="profile-trade-name" />
          <Field
            label="본사 소재지"
            value={
              current.headquartersRegion === null || current.headquartersRegion === undefined
                ? null
                : displayOrUnknown(REGION_DISPLAY, current.headquartersRegion).label
            }
            testId="profile-region"
          />
          <Field
            label="등록 분야"
            value={
              current.naraRegisteredFields.length === 0
                ? null
                : current.naraRegisteredFields
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
              value={detailsForm.tradeName}
              onChange={(event) => setDetails({ ...detailsForm, tradeName: event.target.value })}
              className="rounded-md border border-line bg-card px-2 py-1 text-sm"
            />
          </label>
          <label className="flex flex-col gap-1">
            <span className="text-xs text-ink-soft">본사 소재지</span>
            <select
              aria-label="본사 소재지"
              value={detailsForm.headquartersRegion}
              onChange={(event) => setDetails({ ...detailsForm, headquartersRegion: event.target.value })}
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
              value={detailsForm.naraRegistration}
              onChange={(event) =>
                setDetails({ ...detailsForm, naraRegistration: event.target.value as NaraRegistration })
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
          {detailsForm.naraRegistration === 'REGISTERED' ? (
            <label className="flex flex-col gap-1">
              <span className="text-xs text-ink-soft">인증서 만료일</span>
              <input
                type="date"
                aria-label="인증서 만료일"
                value={detailsForm.businessCertificateExpiresOn}
                onChange={(event) =>
                  setDetails({ ...detailsForm, businessCertificateExpiresOn: event.target.value })
                }
                className="rounded-md border border-line bg-card px-2 py-1 text-sm"
              />
            </label>
          ) : null}
        </div>
        {detailsForm.naraRegistration === 'REGISTERED' ? (
          <fieldset data-testid="nara-fields-choice" className="mt-2">
            <legend className="text-xs text-ink-soft">등록 분야</legend>
            <div className="mt-1 flex flex-wrap gap-3">
              {Object.entries(NARA_FIELD_DISPLAY).map(([value, display]) => (
                <label key={value} className="flex items-center gap-1 text-sm">
                  <input
                    type="checkbox"
                    aria-label={display.label}
                    checked={detailsForm.naraRegisteredFields.includes(value as NaraField)}
                    onChange={(event) =>
                      setDetails({
                        ...detailsForm,
                        naraRegisteredFields: event.target.checked
                          ? [...detailsForm.naraRegisteredFields, value as NaraField]
                          : detailsForm.naraRegisteredFields.filter((one) => one !== value),
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
          disabled={saveDetails.isPending}
          onClick={() => saveDetails.mutate(detailsForm)}
          className="mt-2 rounded-md bg-accent px-3 py-1 text-sm font-medium text-accent-ink disabled:opacity-50"
        >
          사업 정보 저장
        </button>
      </div>

      {/* 시공능력평가액 — 행 단위다. 기준 연도가 **필수**다(연 1회 갱신되는 값이다). */}
      <div className="rounded-md border border-line p-3">
        <span className="text-sm font-medium">시공능력평가액</span>
        {capacities.isError && !isUnauthorized(capacities.error) ? (
          <ErrorFace testId="capacity-error" onRetry={() => void capacities.refetch()} />
        ) : null}
        {capacities.isSuccess && capacities.data.length === 0 ? (
          <p data-testid="capacity-empty" className="mt-2 text-xs text-ink-soft">
            신고한 행이 없습니다.
          </p>
        ) : null}
        <ul className="mt-2 flex flex-col gap-2">
          {(capacities.data ?? []).map((row: ConstructionCapacity) => (
            <li
              key={row.licenseName}
              data-testid={`capacity-${row.licenseName}`}
              className="flex flex-wrap items-center gap-2 text-sm"
            >
              <span>{row.licenseName}</span>
              <span className="font-numeric">{row.amountWon.toLocaleString('ko-KR')}원</span>
              <span className="text-xs text-ink-soft">{row.assessmentYear}년 평가</span>
              <button
                type="button"
                onClick={() => dropCapacity.mutate(row.licenseName)}
                className="rounded-md border border-line px-2 py-0.5 text-xs hover:bg-neutral-skin"
              >
                지우기
              </button>
            </li>
          ))}
        </ul>
        <div className="mt-2 grid gap-2 sm:grid-cols-3">
          <input
            aria-label="시평액 면허명"
            value={capacity.licenseName}
            onChange={(event) => setCapacity({ ...capacity, licenseName: event.target.value })}
            className="rounded-md border border-line bg-card px-2 py-1 text-sm"
          />
          <input
            aria-label="시평액 금액"
            inputMode="numeric"
            value={capacity.amountWon}
            onChange={(event) => setCapacity({ ...capacity, amountWon: event.target.value })}
            className="rounded-md border border-line bg-card px-2 py-1 font-numeric text-sm"
          />
          <input
            aria-label="평가 연도"
            inputMode="numeric"
            value={capacity.assessmentYear}
            onChange={(event) => setCapacity({ ...capacity, assessmentYear: event.target.value })}
            className="rounded-md border border-line bg-card px-2 py-1 font-numeric text-sm"
          />
        </div>
        {capacityMissing ? (
          <p data-testid="capacity-missing" role="alert" className="mt-2 text-xs text-bad-ink">
            면허명 · 금액 · 평가 연도 셋 다 필요합니다 — 빈 값을 보내지 않습니다.
          </p>
        ) : null}
        <button
          type="button"
          disabled={saveCapacity.isPending}
          onClick={() => {
            // 셋 다 있어야 보낸다 — 0 이나 올해를 **기본값으로 지어내지 않는다**(계약이 필수로 둔 칸이다).
            const amount = Number.parseInt(capacity.amountWon, 10);
            const year = Number.parseInt(capacity.assessmentYear, 10);
            if (capacity.licenseName.trim() === '' || !Number.isFinite(amount) || !Number.isFinite(year)) {
              setCapacityMissing(true);
              return;
            }
            setCapacityMissing(false);
            saveCapacity.mutate(capacity);
          }}
          className="mt-2 rounded-md bg-accent px-3 py-1 text-sm font-medium text-accent-ink disabled:opacity-50"
        >
          시평액 저장
        </button>
      </div>

      {/* G2 — 「아직 판정하지 않는 축」은 **서버가 준 목록**에서 그린다. */}
      <div data-testid="unjudged-axes" className="rounded-md border border-line p-3">
        <span className="text-sm font-medium">아직 판정하지 않는 자격 축</span>
        {current.unjudgedAxes.length === 0 ? (
          <p className="mt-1 text-xs text-ink-soft">서버가 이 축을 비워 보냈습니다.</p>
        ) : (
          <ul className="mt-2 flex flex-wrap gap-2">
            {current.unjudgedAxes.map((axis) => (
              <li key={axis}>
                <StateBadge display={displayOrUnknown(UNJUDGED_AXIS_DISPLAY, axis)} testId={`unjudged-${axis}`} />
              </li>
            ))}
          </ul>
        )}
      </div>
    </section>
  );
}

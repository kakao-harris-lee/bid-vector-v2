import { useState } from 'react';

import { replaceQualificationFacts, type Profile } from '../api/queries';
import { PROFILE_QUERY_KEY } from '../api/queryKeys';
import { Button } from '../components/Button';
import { StateBadge } from '../components/StateBadge';
import { KeyValueRow } from '../components/KeyValueRow';
import { termsOf } from '../display/editSessionDisplay';
import { QUALIFICATION_STATE_DISPLAY, qualificationStateOf } from '../display/profileDisplay';
import { displayOrUnknown } from '../display/stateDisplay';

import { attemptOf, ProfileWriteFailure, useProfileWrite } from './profileWrites';

/** 자격 사실 폼 — 세 어휘 배열과 신고 여부. 기본값을 지어내지 않는다. */
interface FactsForm {
  readonly businessTypes: string;
  readonly licensesDeclared: boolean;
  readonly licenseNames: string;
  readonly regionTerms: string;
}

/** 서버가 준 프로필에서 폼의 출발값을 만든다 — **값을 더하지 않는다**(보이는 것이 그대로다). */
function factsFormOf(profile: Profile): FactsForm {
  return {
    businessTypes: profile.businessTypes.join(', '),
    licensesDeclared: profile.licensesDeclared,
    licenseNames: profile.licenseNames.join(', '),
    regionTerms: profile.regionTerms.join(', '),
  };
}

/**
 * 자격 사실(업종·면허·지역어) — **절 단위 교체**다. 세 상태(미설정 / 미신고 / 신고)가 서버의 두
 * boolean 에서 나오고 화면이 그것을 추론하지 않는다.
 *
 * 체크리스트 항목 `BUSINESS_TYPES`·`LICENSES` 가 이 폼 하나에 묶인다(M7/7E-2b 착수 실측) —
 * 그래서 마법사의 한 단계가 이 카드다.
 */
export function ProfileFactsCard({
  profile,
  onSaved,
}: {
  readonly profile: Profile;
  readonly onSaved?: () => void;
}) {
  const [facts, setFacts] = useState<FactsForm | null>(null);
  const save = useProfileWrite(replaceQualificationFacts, PROFILE_QUERY_KEY, () => {
    setFacts(null);
    onSaved?.();
  });

  const qualification = qualificationStateOf(profile);
  const form = facts ?? factsFormOf(profile);

  return (
    <>
      <ProfileWriteFailure attempts={[attemptOf(save)]} />
      <div className="rounded-md border border-line p-3">
        <div className="flex flex-wrap items-center gap-2">
          <span className="text-sm font-medium">자격 사실</span>
          <StateBadge
            display={displayOrUnknown(QUALIFICATION_STATE_DISPLAY, qualification)}
            testId="qualification-state"
          />
        </div>
        <dl className="mt-3 grid gap-3 sm:grid-cols-3">
          <KeyValueRow
            label="업종"
            value={profile.businessTypes.length === 0 ? null : profile.businessTypes.join(', ')}
            testId="profile-business-types"
          />
          <KeyValueRow
            label="면허"
            value={profile.licenseNames.length === 0 ? null : profile.licenseNames.join(', ')}
            testId="profile-licenses"
          />
          <KeyValueRow
            label="지역어"
            value={profile.regionTerms.length === 0 ? null : profile.regionTerms.join(', ')}
            testId="profile-region-terms"
          />
        </dl>
        <div className="mt-3 grid gap-2 sm:grid-cols-3">
          <label className="flex flex-col gap-1">
            <span className="text-xs text-ink-soft">업종(쉼표)</span>
            <input
              value={form.businessTypes}
              onChange={(event) => setFacts({ ...form, businessTypes: event.target.value })}
              className="min-h-11 rounded-md border border-control-line bg-card px-3 text-sm"
            />
          </label>
          <label className="flex flex-col gap-1">
            <span className="text-xs text-ink-soft">면허(쉼표)</span>
            <input
              disabled={!form.licensesDeclared}
              value={form.licenseNames}
              onChange={(event) => setFacts({ ...form, licenseNames: event.target.value })}
              className="min-h-11 rounded-md border border-control-line bg-card px-3 text-sm disabled:opacity-50"
            />
          </label>
          <label className="flex flex-col gap-1">
            <span className="text-xs text-ink-soft">지역어(쉼표)</span>
            <input
              value={form.regionTerms}
              onChange={(event) => setFacts({ ...form, regionTerms: event.target.value })}
              className="min-h-11 rounded-md border border-control-line bg-card px-3 text-sm"
            />
          </label>
        </div>
        {/*
          손가락이 닿는 상자는 **이 레이블**이다(M13/13B cr P-3) — 암묵 레이블이라 어디를 눌러도
          체크박스가 켜지므로 `min-h-11` 은 레이블이 들고 컨트롤은 기본 크기다. 값·동작·이름은
          라운드 2 에서도 바뀌지 않았다.
        */}
        <label className="mt-2 flex min-h-11 items-center gap-2 text-sm">
          <input
            type="checkbox"
            checked={form.licensesDeclared}
            onChange={(event) => setFacts({ ...form, licensesDeclared: event.target.checked })}
          />
          면허를 신고함
          <span className="text-xs text-ink-soft">(끄면 면허 목록을 비워 보냅니다)</span>
        </label>
        <div className="mt-2">
          <Button
            tone="primary"
            disabled={save.isPending}
            onClick={() =>
            save.mutate({
              businessTypes: termsOf(form.businessTypes),
              licensesDeclared: form.licensesDeclared,
              // 신고하지 않으면 **비어 있어야 한다**(계약 — 아니면 400). 받아 두고 버리지 않는다.
              licenseNames: form.licensesDeclared ? termsOf(form.licenseNames) : [],
              regionTerms: termsOf(form.regionTerms),
              })
            }
          >
            자격 사실 저장
          </Button>
        </div>
      </div>
    </>
  );
}

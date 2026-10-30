import { useQuery } from '@tanstack/react-query';

import { readProfile, type Profile } from '../api/queries';
import { ErrorFace, Skeleton } from '../components/ScreenFaces';
import { StateBadge } from '../components/StateBadge';
import { isUnauthorized } from '../components/WriteFailure';
import { UNJUDGED_AXIS_DISPLAY } from '../display/profileDisplay';
import { displayOrUnknown } from '../display/stateDisplay';

import { ProfileCapacityCard } from './ProfileCapacityCard';
import { ProfileDetailsCard } from './ProfileDetailsCard';
import { ProfileFactsCard } from './ProfileFactsCard';
import { PROFILE_QUERY_KEY } from './profileWrites';

/**
 * 프로필 설정(D-7E2-3) — 블록 셋을 조립한다. 각 블록은 **마법사의 한 단계이기도 하다**
 * (M7/7E-2b, D-7E2b-8): 같은 필드를 두 폼이 따로 저장하지 않게 한 자리에서 온다.
 *
 * 「아직 판정하지 않는 축」은 **서버가 준 목록**(`unjudgedAxes`)에서 그린다 — 어느 축이
 * 미판정인지 화면이 추론하지 않는다(G2).
 */
export function ProfileSection() {
  const profile = useQuery({ queryKey: PROFILE_QUERY_KEY, queryFn: readProfile });

  if (profile.isPending) {
    return <Skeleton testId="profile-loading" />;
  }
  if (profile.isError) {
    return isUnauthorized(profile.error) ? null : (
      <ErrorFace testId="profile-error" onRetry={() => void profile.refetch()} />
    );
  }

  const current = profile.data;

  return (
    <section data-testid="settings-profile" className="flex flex-col gap-4">
      <h3 className="text-base font-semibold">프로필</h3>

      <ProfileFactsCard profile={current} />
      <ProfileDetailsCard profile={current} />
      <ProfileCapacityCard />
      <UnjudgedAxes axes={current.unjudgedAxes} />
    </section>
  );
}

/** G2 — 「아직 판정하지 않는 축」은 **서버가 준 목록**에서 그린다. */
function UnjudgedAxes({ axes }: { readonly axes: Profile['unjudgedAxes'] }) {
  return (
    <div data-testid="unjudged-axes" className="rounded-md border border-line p-3">
      <span className="text-sm font-medium">아직 판정하지 않는 자격 축</span>
      {axes.length === 0 ? (
        <p className="mt-1 text-xs text-ink-soft">서버가 이 축을 비워 보냈습니다.</p>
      ) : (
        <ul className="mt-2 flex flex-wrap gap-2">
          {axes.map((axis) => (
            <li key={axis}>
              <StateBadge display={displayOrUnknown(UNJUDGED_AXIS_DISPLAY, axis)} testId={`unjudged-${axis}`} />
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

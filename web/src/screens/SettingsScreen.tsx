import { useQuery } from '@tanstack/react-query';

import { readStrategy } from '../api/queries';
import { isUnauthorized, SignedOutFace } from '../components/WriteFailure';
import { useSessionCsrf } from '../session/useSessionCsrf';

import { ProfileSection } from './ProfileSection';
import { STRATEGY_QUERY_KEY } from './strategyEdit';
import { StrategySection } from './StrategySection';

/**
 * 범위 밖 자리 — **빈 자리 + 「준비 중」뿐이다**(D-7E2-4·7). 탭·버튼·입력칸을 만들지 않는다.
 *
 * **사업자번호는 더 이상 여기 없다**(M15/15C): 적은 값이 갈 자리가 생겨(V30) 사업 정보 카드가
 * 선택 입력칸으로 받는다. 여기 남은 것은 **국세청 진위 확인**이고, 그것은 여전히 범위 밖이다 —
 * 번호를 저장한다는 것과 그 번호가 진짜인지 확인한다는 것은 다른 사실이다.
 */
const NOT_READY = [
  { id: 'nts', label: '사업자번호 진위 확인(국세청)', why: '적은 번호가 진짜인지는 아직 확인하지 않습니다' },
  { id: 'channels', label: '알림 채널(텔레그램·메일)', why: '보내는 방법을 정한 뒤에 엽니다' },
  { id: 'paper', label: '페이퍼(가상) 투찰', why: '근거가 더 필요합니다' },
] as const;

/**
 * 설정 화면 — 전략과 프로필 한 자리(요구표 「설정」). 전략은 **편집 세션**으로만 바뀌고
 * (계약에 직접 쓰는 길이 없다) 프로필은 절·행 단위 교체다.
 */
export function SettingsScreen() {
  const { signedIn } = useSessionCsrf();
  // 세션 판정을 전략 읽기와 **같은 질의**로 본다 — 두 번 읽지 않는다.
  const strategy = useQuery({ queryKey: STRATEGY_QUERY_KEY, queryFn: readStrategy });

  if (signedIn === false || isUnauthorized(strategy.error)) {
    return (
      <section data-testid="screen-settings" className="mx-auto max-w-3xl p-4">
        <SignedOutFace />
      </section>
    );
  }

  return (
    <section data-testid="screen-settings" className="mx-auto flex max-w-3xl flex-col gap-6 p-4">
      <h2 className="text-lg font-semibold">설정</h2>

      <StrategySection />

      <ProfileSection />

      <section data-testid="settings-not-ready" className="flex flex-col gap-2">
        <h3 className="text-base font-semibold">준비 중</h3>
        <ul className="flex flex-col gap-2">
          {NOT_READY.map((one) => (
            <li key={one.id} className="rounded-md border border-line p-3">
              <p className="text-sm">{one.label}</p>
              <p className="mt-1 text-xs text-ink-soft">{one.why}</p>
            </li>
          ))}
        </ul>
      </section>
    </section>
  );
}

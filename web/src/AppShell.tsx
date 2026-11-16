import { QueryClientProvider, useQuery } from '@tanstack/react-query';
import { useEffect, useRef, useState } from 'react';
import { NavLink, Outlet, useLocation, useMatches, useNavigate } from 'react-router';

import { rememberCsrf } from './api/client';
import { listNotifications, signOut } from './api/queries';
import { notificationsUnreadQueryKey } from './api/queryKeys';
import { Button } from './components/Button';
import { queryClient } from './queryClient';
import { notFoundSlot, screenSlots, type ScreenSlot } from './routes';

/** 셸의 이름 — 문서 제목도 이 한 자리를 쓴다(머리글과 탭 이름이 갈리지 않는다). */
const APP_NAME = 'bid-vector 운영자';

const LOGIN_PATH = '/login';

/** 하단 탭이 칸을 주는 자리와 「더 보기」가 품는 자리 — 분류는 자리 표가 든다(`routes.tsx`). */
const PRIMARY_SLOTS = screenSlots.filter((slot) => slot.nav === 'primary');
const MORE_SLOTS = screenSlots.filter((slot) => slot.nav === 'more');

/** 넓은 화면의 한 줄 — 좁은 화면에서 둘로 갈리는 것이 같은 집합의 **배치**일 뿐임을 보인다. */
const TOP_SLOTS = [...PRIMARY_SLOTS, ...MORE_SLOTS];

/**
 * 문서 제목이 읽는 자리 전수. 자리 표 밖의 「없는 주소」도 라벨이 있으므로 함께 든다 —
 * 로그인만 라벨이 없고 그 자리는 앱 이름만 쓴다(알려진 제한).
 */
const TITLE_SLOTS: readonly ScreenSlot[] = [...screenSlots, notFoundSlot];

/** 알림함 자리가 배지를 단다 — 좁은 화면에서는 그 자리가 「더 보기」 안이라 탭에도 함께 선다. */
const INBOX_SLOT_ID = 'inbox';

function navLinkClass({ isActive }: { readonly isActive: boolean }): string {
  const tone = isActive ? 'bg-neutral-skin font-medium text-neutral-ink' : 'text-ink-soft hover:bg-neutral-skin';
  return `inline-flex min-h-11 shrink-0 items-center gap-2 rounded-md px-3 text-sm ${tone}`;
}

function tabLinkClass({ isActive }: { readonly isActive: boolean }): string {
  const tone = isActive ? 'font-medium text-neutral-ink' : 'text-ink-soft';
  return `flex min-h-14 w-full flex-col items-center justify-center gap-0.5 rounded-md px-1 text-xs ${tone}`;
}

/**
 * 안 읽음 수 — **0 이면 그리지 않는다**(0 을 보이면 「안 읽은 것이 있다」와 「다 읽었다」가 같은
 * 모양이 된다). 읽지 못했을 때도 그리지 않는다: 배지는 **서버가 센 값**만 보인다.
 */
function UnreadBadge({ count, testId }: { readonly count: number; readonly testId: string }) {
  if (count <= 0) {
    return null;
  }
  return (
    <span
      data-testid={testId}
      className="inline-flex min-w-5 items-center justify-center rounded-full bg-bad-skin px-1.5 text-xs font-medium text-bad-ink"
    >
      {count}
    </span>
  );
}

/**
 * 공통 셸 — **모바일 우선 반응형 하나**다(§6.5).
 *
 * 탐색이 폭에 따라 두 모양이다: `md` 미만은 **하단 탭**(주 화면 넷 + 「더 보기」), 이상은 **상단
 * 줄**이다. 둘을 함께 그리고 CSS 로 가린다 — 숨는 쪽은 `display:none` 이라 접근성 트리에서도
 * 빠지고(같은 이름이 둘로 겹치지 않는다) 미디어 질의를 JS 가 읽지 않으므로 첫 렌더가 흔들리지
 * 않는다.
 *
 * 색은 전부 토큰(`index.css`)이라 시스템 테마가 바뀌면 함께 바뀐다 — 컴포넌트에 색 리터럴이 없다.
 *
 * 서버 상태 provider 가 **여기** 있다(M7/7E-1) — 라우트 트리가 그것을 품으므로 「화면이 질의를
 * 걸 수 있는 자리」와 「셸이 뜨는 자리」가 같아지고, 라우트를 렌더하는 test 가 provider 를 따로
 * 세우지 않는다. 셸 자신도 질의를 걸므로(안 읽음 배지) 그 몸통은 provider **안**에 있다.
 */
export function AppShell() {
  return (
    <QueryClientProvider client={queryClient}>
      <ShellFrame />
    </QueryClientProvider>
  );
}

function ShellFrame() {
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const matches = useMatches();
  /**
   * 「더 보기」의 펼침 — **자리와 함께** 둔다. 자리를 옮기면 펼침이 풀려야 하는데, 그것을
   * effect 에서 `setState` 로 하면 렌더가 한 번 더 돈다(그리고 그 꼴을 lint 가 막는다).
   * 펼친 자리를 상태에 담아 **렌더에서 비교**하면 같은 일이 한 번의 렌더로 끝난다.
   */
  const [more, setMore] = useState<{ readonly open: boolean; readonly at: string }>({ open: false, at: '' });
  const [signOutFailed, setSignOutFailed] = useState(false);
  const [signingOut, setSigningOut] = useState(false);
  const mainRef = useRef<HTMLElement | null>(null);
  const moreButtonRef = useRef<HTMLButtonElement | null>(null);
  const firstRender = useRef(true);

  // 로그인 자리에는 탐색을 그리지 않는다 — 세션 전이고, 그 화면에서 갈 수 있는 곳이 없다.
  const atLogin = pathname === LOGIN_PATH;
  const moreOpen = more.open && more.at === pathname;
  const setMoreOpen = (open: boolean): void => {
    setMore({ open, at: pathname });
  };

  /**
   * 안 읽음 전수는 **헤더 값**이다(`X-Unread-Count`) — 목록 필터·쪽 꼴과 무관하다. 한 행만
   * 받아 오는 이유는 화면이 그 본문을 쓰지 않기 때문이다(배지가 읽는 것은 헤더뿐이다).
   */
  const unread = useQuery({
    queryKey: notificationsUnreadQueryKey(),
    queryFn: () => listNotifications({ size: 1 }),
    enabled: !atLogin,
  });
  const unreadCount = unread.data?.unread ?? 0;

  /** 화면마다 문서 제목 — 라벨은 자리 표가 든다(사본을 만들지 않는다). */
  const leafId = matches[matches.length - 1]?.id ?? '';
  useEffect(() => {
    const slot = TITLE_SLOTS.find((one) => one.id === leafId);
    document.title = slot === undefined ? APP_NAME : `${slot.label} · ${APP_NAME}`;
  }, [leafId]);

  /**
   * 이동 뒤 **본문 머리**로 포커스를 옮긴다(§6.5 키보드 접근). 키는 `pathname` **하나**다 —
   * 질의 문자열까지 보면 입찰함의 `?tab=` 이동과 홈의 검색이 포커스를 빼앗는다(그 둘은 같은
   * 화면 안의 상태 변화다). 첫 렌더에서는 옮기지 않는다: 그때 포커스는 아직 사용자의 것이다.
   */
  useEffect(() => {
    if (firstRender.current) {
      firstRender.current = false;
      return;
    }
    mainRef.current?.focus();
  }, [pathname]);

  /**
   * Escape 로 닫고 포커스를 **연 버튼으로** 돌린다. 이것은 modal 이 아니라 disclosure 라 포커스
   * 덫을 두지 않는다(바깥 자리도 계속 쓸 수 있는 것이 이 꼴의 뜻이다).
   */
  useEffect(() => {
    if (!moreOpen) {
      return undefined;
    }
    const onKeyDown = (event: KeyboardEvent): void => {
      if (event.key === 'Escape') {
        setMore({ open: false, at: pathname });
        moreButtonRef.current?.focus();
      }
    };
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [moreOpen, pathname]);

  async function runSignOut(): Promise<void> {
    setSigningOut(true);
    setSignOutFailed(false);
    try {
      await signOut();
    } catch {
      // **삼키지 않는다** — 폐기되지 않았는데 로그아웃된 것처럼 보이면 운영자가 세션을 남긴 채
      // 떠난다. 실패하면 자리를 옮기지도, 기억한 값을 버리지도 않는다.
      setSignOutFailed(true);
      return;
    } finally {
      setSigningOut(false);
    }
    rememberCsrf(null);
    // 앞 사람의 서버 상태가 다음 화면에 남지 않게 한다 — 캐시는 계정의 것이다.
    queryClient.clear();
    await navigate(LOGIN_PATH);
  }

  const moreActive = MORE_SLOTS.some((slot) => slot.path === pathname);
  const signOutButton = (
    <Button tone="secondary" onClick={() => void runSignOut()} disabled={signingOut} testId="sign-out">
      로그아웃
    </Button>
  );

  return (
    <div data-testid="app-shell" className="min-h-screen bg-page text-ink">
      {/* 키보드 접근(§6.5) — 첫 탭이 본문으로 건너뛸 수 있게 한다. 시각 은폐 상태라 터치 타깃
          게이트의 모집단 밖이고, **포커스됐을 때의 크기**를 그 게이트가 따로 잰다. */}
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-30 focus:inline-flex focus:min-h-11 focus:items-center focus:rounded-md focus:bg-card focus:px-3 focus:text-sm"
      >
        본문으로 건너뛰기
      </a>
      <header className="border-b border-line px-4 py-3 sm:px-6">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h1 className="text-lg font-bold">{APP_NAME}</h1>
          {atLogin ? null : <div className="hidden md:block">{signOutButton}</div>}
        </div>
        {atLogin ? null : (
          <nav aria-label="화면" className="mt-2 hidden flex-wrap gap-1 md:flex">
            {TOP_SLOTS.map((slot) => (
              <NavLink key={slot.id} to={slot.path} end={slot.path === '/'} className={navLinkClass}>
                {slot.label}
                {slot.id === INBOX_SLOT_ID ? <UnreadBadge count={unreadCount} testId="nav-unread" /> : null}
              </NavLink>
            ))}
          </nav>
        )}
      </header>
      {/*
        본문은 포커스를 받을 수 있는 자리다(`tabIndex={-1}`) — 이동 뒤 포커스가 머리로 간다.
        좁은 화면에서는 아래 고정 탭이 본문을 덮으므로 그만큼 바닥 여백을 둔다.
      */}
      <main id="main" ref={mainRef} tabIndex={-1} className={atLogin ? '' : 'pb-28 md:pb-0'}>
        <Outlet />
      </main>
      {atLogin ? null : (
        <nav aria-label="화면" className="fixed inset-x-0 bottom-0 z-20 border-t border-line bg-card md:hidden">
          {signOutFailed ? (
            <p data-testid="sign-out-failure" role="alert" className="px-3 py-2 text-xs text-bad-ink">
              로그아웃하지 못했습니다. 다시 시도하십시오.
            </p>
          ) : null}
          {moreOpen ? (
            <div id="nav-more" data-testid="nav-more" className="border-b border-line p-2">
              <ul className="flex flex-col gap-1">
                {MORE_SLOTS.map((slot) => (
                  <li key={slot.id}>
                    <NavLink to={slot.path} className={navLinkClass}>
                      {slot.label}
                      {slot.id === INBOX_SLOT_ID ? (
                        <UnreadBadge count={unreadCount} testId="nav-more-unread" />
                      ) : null}
                    </NavLink>
                  </li>
                ))}
                <li>{signOutButton}</li>
              </ul>
            </div>
          ) : null}
          <ul className="flex items-stretch">
            {PRIMARY_SLOTS.map((slot) => (
              <li key={slot.id} className="flex flex-1 items-stretch">
                <NavLink to={slot.path} end={slot.path === '/'} className={tabLinkClass}>
                  {slot.label}
                </NavLink>
              </li>
            ))}
            <li className="flex flex-1 items-stretch">
              {/*
                「더 보기」는 disclosure 다. 그 안의 자리가 현재일 때 **버튼도 현재로 표시**한다 —
                그러지 않으면 알림함에 서 있는데 어느 탭도 켜지지 않는다.
              */}
              <button
                type="button"
                ref={moreButtonRef}
                onClick={() => setMoreOpen(!moreOpen)}
                aria-expanded={moreOpen}
                aria-controls="nav-more"
                aria-current={moreActive ? true : undefined}
                data-testid="nav-more-toggle"
                className={tabLinkClass({ isActive: moreActive })}
              >
                <span className="inline-flex items-center gap-1">
                  더 보기
                  <UnreadBadge count={unreadCount} testId="nav-tab-unread" />
                </span>
              </button>
            </li>
          </ul>
        </nav>
      )}
    </div>
  );
}

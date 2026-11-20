import { QueryClientProvider, useQuery } from '@tanstack/react-query';
import { useEffect, useRef, useState } from 'react';
import { NavLink, Outlet, useLocation, useMatches, useNavigate } from 'react-router';

import { rememberCsrf } from './api/client';
import { listBidCases, listNotifications, readProfile, signOut } from './api/queries';
import { bidCasesQueryKey, notificationsUnreadQueryKey, PROFILE_QUERY_KEY } from './api/queryKeys';
import { AbsentValue } from './components/AbsentValue';
import { Button } from './components/Button';
import { queryClient } from './queryClient';
import { notFoundSlot, screenSlots, type ScreenSlot } from './routes';

/** 셸의 이름 — 문서 제목도 이 한 자리를 쓴다(머리글과 탭 이름이 갈리지 않는다). */
const APP_NAME = 'bid-vector 운영자';

const LOGIN_PATH = '/login';

/** 하단 탭이 칸을 주는 자리와 「더 보기」가 품는 자리 — 분류는 자리 표가 든다(`routes.tsx`). */
const PRIMARY_SLOTS = screenSlots.filter((slot) => slot.nav === 'primary');
const MORE_SLOTS = screenSlots.filter((slot) => slot.nav === 'more');

/**
 * 넓은 화면의 **왼쪽 사이드바**(M14/14A · 와이어프레임의 공통 셸). 좁은 화면에서 둘로 갈리는
 * 것이 같은 집합의 **배치**일 뿐임을 보인다 — 앞 판은 같은 집합이 상단 한 줄이었다.
 *
 * **항목이 여섯이다**(계약의 이탈 하나). 와이어프레임이 「시작하기」를 뺀 것은 그 일이 끝난
 * 뒤의 모습이고, 체크리스트가 남은 계정에게는 그 자리가 첫 걸음이다. 차례는 **자리 표의 배열
 * 순서**를 그대로 쓴다 — 자리 표의 순서는 다른 등식이 이미 들고 있어, 셸이 자기 차례를 따로
 * 들면 그 둘이 갈릴 자리가 생긴다.
 */
const SIDEBAR_SLOTS = [...PRIMARY_SLOTS, ...MORE_SLOTS];

/**
 * 문서 제목이 읽는 자리 전수. 자리 표 밖의 「없는 주소」도 라벨이 있으므로 함께 든다 —
 * 로그인만 라벨이 없고 그 자리는 앱 이름만 쓴다(알려진 제한).
 */
const TITLE_SLOTS: readonly ScreenSlot[] = [...screenSlots, notFoundSlot];

/** 알림함 자리가 배지를 단다 — 좁은 화면에서는 그 자리가 「더 보기」 안이라 탭에도 함께 선다. */
const INBOX_SLOT_ID = 'inbox';

/**
 * 입찰함 자리의 배지(M14/14A · 와이어프레임). 세는 것은 **실투찰(REAL)** 뿐이다 — 목록
 * operation 이 `kind` 를 필수로 받고 계약이 두 종류를 한 응답에 섞지 않는다(§6.1). 페이퍼는
 * 따로 보기이고, 그래서 이 배지는 서버 변경 없이 선다.
 */
const BID_BOX_SLOT_ID = 'bid-box';

/** 사이드바가 세는 종류 — 입찰함 화면과 **같은 질의 키**라 담기·전이의 무효화가 배지에 닿는다. */
const BADGE_BID_KIND = 'REAL';

function navLinkClass({ isActive }: { readonly isActive: boolean }): string {
  const tone = isActive ? 'bg-neutral-skin font-medium text-neutral-ink' : 'text-ink-soft hover:bg-neutral-skin';
  return `inline-flex min-h-11 shrink-0 items-center gap-2 rounded-md px-3 text-sm ${tone}`;
}

/** 사이드바의 한 줄 — 한 단이라 가로를 다 쓰고, 배지는 오른쪽 끝으로 밀린다. */
function sidebarLinkClass({ isActive }: { readonly isActive: boolean }): string {
  // 선택 어조는 상태 어조 다섯과 **다른 축**이다 — 무엇인지가 아니라 어디에 서 있는지를 말한다.
  const tone = isActive ? 'bg-selected-skin font-medium text-selected-ink' : 'text-ink-soft hover:bg-neutral-skin';
  return `flex min-h-11 w-full items-center gap-2 rounded-md px-3 text-sm ${tone}`;
}

function tabLinkClass({ isActive }: { readonly isActive: boolean }): string {
  const tone = isActive ? 'font-medium text-neutral-ink' : 'text-ink-soft';
  return `flex min-h-14 w-full flex-col items-center justify-center gap-0.5 rounded-md px-1 text-xs ${tone}`;
}

/**
 * 탐색 배지 — **0 이면 그리지 않는다**(0 을 보이면 「있다」와 「다 읽었다·비었다」가 같은 모양이
 * 된다). 읽지 못했을 때도 그리지 않는다: 배지는 **서버가 센 값**만 보인다.
 *
 * 「아직 받지 못했다」를 글자로 말하지 않고 **아무 말도 하지 않는** 것이 여기서는 정직한 쪽이다 —
 * 배지는 한 자리 수를 담는 칸이라 사유가 들어갈 자리가 아니고, 없는 배지는 아무것도 주장하지
 * 않는다. 사유를 말해야 하는 자리(알림함 화면의 배지)는 자기 자리에서 그렇게 한다.
 */
function CountBadge({ count, testId }: { readonly count: number; readonly testId: string }) {
  if (count <= 0) {
    return null;
  }
  return (
    <span
      data-testid={testId}
      className="ml-auto inline-flex min-w-5 items-center justify-center rounded-full bg-bad-skin px-1.5 text-xs font-medium text-bad-ink"
    >
      {count}
    </span>
  );
}

/** 자리 하나가 다는 배지 — 어느 자리가 무엇을 세는지는 **이 함수 하나**가 안다. */
function SlotBadge({
  slotId,
  unread,
  bidBox,
  place,
}: {
  readonly slotId: string;
  readonly unread: number;
  readonly bidBox: number;
  /** 같은 자리가 두 탐색에 서므로 표지를 자리별로 가른다(여정의 strict mode). */
  readonly place: string;
}) {
  if (slotId === INBOX_SLOT_ID) {
    return <CountBadge count={unread} testId={`${place}-unread`} />;
  }
  if (slotId === BID_BOX_SLOT_ID) {
    return <CountBadge count={bidBox} testId={`${place}-bid-box`} />;
  }
  return null;
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
   * 「더 보기」의 펼침 — **마지막으로 본 자리**와 함께 둔다.
   *
   * 앞 판은 `open && at === pathname` 으로 **접어 읽기만** 했다. 그러면 「닫혔다」가 상태에 남지
   * 않아서, 펼친 자리를 떠났다가 **돌아오면 저절로 다시 펼쳐졌다**(cr M-1 — 펼침 → 패널 링크로
   * 이동 → 하단 탭으로 복귀 → `aria-expanded` 가 참). 자리가 바뀌는 **순간** 값을 갱신해 그 기억을
   * 지운다 — effect 가 아니라 렌더에서 하므로 한 번의 렌더로 끝나고 중간 상태가 그려지지 않는다.
   */
  const [more, setMore] = useState<{ readonly open: boolean; readonly at: string }>({ open: false, at: '' });
  const [signOutFailed, setSignOutFailed] = useState(false);
  const [signingOut, setSigningOut] = useState(false);
  const mainRef = useRef<HTMLElement | null>(null);
  const moreButtonRef = useRef<HTMLButtonElement | null>(null);
  const firstRender = useRef(true);

  // 로그인 자리에는 탐색을 그리지 않는다 — 세션 전이고, 그 화면에서 갈 수 있는 곳이 없다.
  const atLogin = pathname === LOGIN_PATH;
  if (more.at !== pathname) {
    // 렌더 중 갱신 — React 가 커밋 없이 곧바로 다시 렌더한다(「props 가 바뀔 때 state 를 맞추는」
    // 표준 꼴). 자리를 옮기면 펼침이 **값으로** 풀린다.
    setMore({ open: false, at: pathname });
    // 실패 알림도 그 자리의 것이다 — 남겨 두면 다른 화면까지 따라간다(cr L-7).
    setSignOutFailed(false);
  }
  const moreOpen = more.open;
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

  /**
   * 입찰함 배지와 사업자명(M14/14A). **둘 다 이미 승인된 읽기**이고 새 operation 이 0 이라
   * 승인 쌍 등식이 움직이지 않는다.
   *
   * 입찰함 키는 **화면 D 와 같은 자리**다(`bidCasesQueryKey`) — 담기·전이가 거는 무효화가
   * 배지까지 닿는 것이 배선이 아니라 구조로 선다. 그 operation 에는 쪽 인자가 없고 `state` 를
   * 빼면 전 상태를 내므로 **계수는 받은 배열의 길이**다(화면이 상태로 거르지 않는다).
   */
  const bidCases = useQuery({
    queryKey: bidCasesQueryKey(BADGE_BID_KIND),
    queryFn: () => listBidCases(BADGE_BID_KIND),
    enabled: !atLogin,
  });
  const bidBoxCount = bidCases.data?.length ?? 0;

  const profile = useQuery({ queryKey: PROFILE_QUERY_KEY, queryFn: readProfile, enabled: !atLogin });

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
    try {
      // **이동 뒤에** 비운다(cr L-10). 비우기가 먼저면 아직 로그인 자리가 아닌 찰나에 배지
      // 질의가 한 번 더 나가 세션 없는 401 을 받는다.
      await navigate(LOGIN_PATH);
    } finally {
      // 그래도 **어떤 경우에도** 비운다(cr N-5) — 이동이 거부돼도 앞 사람의 서버 상태가
      // 다음 화면에 남지 않는다. 캐시는 계정의 것이다.
      queryClient.clear();
    }
  }

  const moreActive = MORE_SLOTS.some((slot) => slot.path === pathname);
  /**
   * 로그아웃은 **두 자리**에 있다(머리글 · 「더 보기」 패널). 같은 `testId` 를 두 번 그리면
   * 여정이 `getByTestId('sign-out')` 를 쓰는 순간 strict mode 위반이다(cr L-1) — 자리로 가른다.
   */
  const signOutButtonAt = (testId: string) => (
    <Button tone="secondary" onClick={() => void runSignOut()} disabled={signingOut} testId={testId}>
      로그아웃
    </Button>
  );
  const signOutButton = signOutButtonAt('sign-out');
  const signOutButtonInMore = signOutButtonAt('sign-out-more');

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
      {/*
        **사이드바와 본문이 나란하다**(M14/14A · 와이어프레임). `md` 미만에서는 이 묶음이 그냥
        위아래이고, 머리띠(앱 이름)만 남아 앞 판과 같은 모양이다 — 탐색은 아래 고정 탭이 든다.

        `h1` 은 **한 노드**다. 폭마다 다른 h1 을 그리면 한 문서에 머리글이 둘이 되고, 그 가운데
        하나가 CSS 로만 숨어 있어 도구마다 다르게 읽는다. 그래서 그 노드를 **사이드바 묶음
        안**에 두고 배치만 갈린다: 좁으면 위쪽 띠, 넓으면 사이드바의 머리다.
      */}
      <div className="md:flex md:min-h-screen md:items-stretch">
        <div className="flex flex-col border-b border-line md:w-54 md:shrink-0 md:border-r md:border-b-0">
          <header className="px-4 py-3 sm:px-6 md:px-4">
            <h1 className="text-lg font-bold">{APP_NAME}</h1>
          </header>
          {atLogin ? null : (
            <>
              <nav aria-label="화면" className="hidden flex-col gap-1 px-2 md:flex">
                {SIDEBAR_SLOTS.map((slot) => (
                  <NavLink key={slot.id} to={slot.path} end={slot.path === '/'} className={sidebarLinkClass}>
                    {slot.label}
                    <SlotBadge slotId={slot.id} unread={unreadCount} bidBox={bidBoxCount} place="nav" />
                  </NavLink>
                ))}
              </nav>
              {/*
                **사업자명과 로그아웃은 사이드바의 발치**다(와이어프레임). 이름은 **받은 뒤에만**
                말한다 — 아직 받지 못한 동안 아무 말도 하지 않는 것이 정직한 쪽이고, 받았는데
                그 칸이 비었으면 그 사실을 사유로 말한다(운영자가 미설정을 알아야 한다).
              */}
              <div className="mt-auto hidden flex-col gap-2 px-4 py-4 md:flex">
                {profile.data === undefined ? null : (
                  <p data-testid="nav-trade-name" className="text-sm break-words text-ink-soft">
                    {profile.data.tradeName ?? <AbsentValue reason="notSent" />}
                  </p>
                )}
                {signOutButton}
              </div>
            </>
          )}
        </div>
        <div className="flex min-w-0 flex-1 flex-col">
          {/*
            **로그아웃 실패는 폭과 무관한 자리에 선다**(cr H-1). 앞 판은 이 알림이 `md:hidden`
            하단 탐색 **안**에 있어서, 로그아웃 버튼이 머리글에 있는 데스크톱에서는 실패가
            **아무 데도 뜨지 않았다** — 숨는 쪽이 접근성 트리에서도 빠지는 성질(이 셸이 두
            탐색을 가르는 근거)이 그대로 침묵을 만들었다. jsdom test 는 CSS 가 없어 그 축에
            공허했다. 이제 로그아웃 버튼이 사이드바 발치로 갔으므로 그 사유는 그대로다.
          */}
          {signOutFailed ? (
            <p
              data-testid="sign-out-failure"
              role="alert"
              className="border-b border-line px-4 py-2 text-sm text-bad-ink sm:px-6"
            >
              로그아웃하지 못했습니다. 다시 시도하십시오.
            </p>
          ) : null}
          {/*
            본문은 포커스를 받을 수 있는 자리다(`tabIndex={-1}`) — 이동 뒤 포커스가 머리로 간다.
            좁은 화면에서는 아래 고정 탭이 본문을 덮으므로 그만큼 바닥 여백을 둔다.
          */}
          <main id="main" ref={mainRef} tabIndex={-1} className={atLogin ? '' : 'pb-28 md:pb-0'}>
            <Outlet />
          </main>
        </div>
      </div>
      {/*
        **DOM 순서가 disclosure 의 탭 순서다**(cr M-2). 패널을 토글보다 앞에 두면, 펼친 키보드
        운영자가 Tab 을 눌렀을 때 드러난 링크가 아니라 탐색 **밖**으로 나간다(닿으려면 Shift+Tab —
        편 사람이 기대하는 반대 방향이다). 그래서 DOM 은 `목록(마지막이 토글) → 패널` 이고, 시각
        배치만 `flex-col-reverse` 로 뒤집어 패널이 탭 줄 위에 선다.

        **패널을 늘 그린다**(cr L-2). 조건부로 그리면 접힌 동안 `aria-controls` 가 **없는 id** 를
        가리킨다. `hidden` 으로 가리면 IDREF 가 늘 서고, 숨는 동안 접근성 트리와 포커스 순서에서
        함께 빠진다.
      */}
      {atLogin ? null : (
        <nav
          aria-label="화면"
          className="fixed inset-x-0 bottom-0 z-20 flex flex-col-reverse border-t border-line bg-card md:hidden"
        >
          <ul className="flex items-stretch">
            {PRIMARY_SLOTS.map((slot) => (
              <li key={slot.id} className="flex flex-1 items-stretch">
                <NavLink to={slot.path} end={slot.path === '/'} className={tabLinkClass}>
                  {/* 배지가 **같은 자리에서** 선다 — 넓은 화면에만 수가 보이면 좁은 화면의
                      운영자는 같은 사실을 알 길이 없다. 한 줄이라 라벨 곁에 둔다. */}
                  <span className="inline-flex items-center gap-1">
                    {slot.label}
                    <SlotBadge slotId={slot.id} unread={unreadCount} bidBox={bidBoxCount} place="nav-tab" />
                  </span>
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
                  <CountBadge count={unreadCount} testId="nav-tab-unread" />
                </span>
              </button>
            </li>
          </ul>
          <div id="nav-more" data-testid="nav-more" hidden={!moreOpen} className="border-t border-line p-2">
            <ul className="flex flex-col gap-1">
              {MORE_SLOTS.map((slot) => (
                <li key={slot.id}>
                  <NavLink to={slot.path} className={navLinkClass}>
                    {slot.label}
                    <SlotBadge slotId={slot.id} unread={unreadCount} bidBox={bidBoxCount} place="nav-more" />
                  </NavLink>
                </li>
              ))}
              <li>{signOutButtonInMore}</li>
            </ul>
          </div>
        </nav>
      )}
    </div>
  );
}

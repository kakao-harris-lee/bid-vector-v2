/**
 * 두 단 배치의 **모양 한 자리**(M14/14A) — 표지 이름과 클래스.
 *
 * `TwoColumn.tsx` 와 **배치 게이트**가 이 모듈을 함께 읽는다. React 를 들이지 않는 이유가
 * 그것이다: 게이트는 Playwright 안에서 도므로 컴포넌트를 import 하면 JSX 런타임이 그 레인으로
 * 딸려 들어온다. 모양만 떼어 두면 게이트가 **사본 없이** 출하 클래스를 그대로 쓴다
 * (`e2e/journey/shared.ts` 가 승인 목록을 `src` 에서 읽는 선례와 같은 꼴이다).
 *
 * Tailwind 의 훑기 뿌리가 `src/**` 라 이 파일의 클래스 글자도 출하 CSS 에 실린다.
 */

/**
 * 배치 게이트가 모집단을 고르는 **표지**. class 이름으로 고르지 않는 것이 그 게이트의
 * 규율이라(13B 알려진 제한 27) 전용 속성을 둔다.
 *
 * **JSX 는 이 상수가 아니라 같은 글자를 직접 적는다.** 전개(`{...{[NAME]: ''}}`)로 쓰면 핸들러
 * 금지 게이트가 그것을 「숨은 핸들러일 수 있다」로 세고, 그 판정은 옳다 — 전개는 무엇이든
 * 실을 수 있다. 두 글자가 갈리지 않는 것은 짝 test 가 **그려진 DOM** 에서 이 상수로 속성을
 * 읽어 확인한다(선언을 맞춰 보는 것이 아니라 거동을 잰다).
 */
export const TWO_COLUMN_ATTRIBUTE = 'data-layout-two-column';

/** 두 칸을 가르는 표지. 값은 `body` · `rail` 둘이다. */
export const LAYOUT_SLOT_ATTRIBUTE = 'data-layout-slot';

export const TWO_COLUMN_BODY_SLOT = 'body';
export const TWO_COLUMN_RAIL_SLOT = 'rail';

/**
 * 레일의 폭은 와이어프레임의 300px 이고 `md` 미만에서는 한 단이라 폭을 주지 않는다.
 *
 * 본문 쪽 `min-w-0` 이 **가로 넘침을 막는 자리**다: flex 자식의 기본 `min-width` 는 `auto` 라
 * 긴 글자 하나가 칸을 뷰포트 밖으로 밀어낸다(배치 게이트의 넘침 축이 보는 바로 그 꼴).
 */
export const TWO_COLUMN_CONTAINER_CLASS = 'flex flex-col gap-4 md:flex-row md:items-start md:gap-6';
export const TWO_COLUMN_BODY_CLASS = 'flex min-w-0 flex-1 flex-col gap-4';
export const TWO_COLUMN_RAIL_CLASS = 'flex w-full min-w-0 flex-col gap-4 md:w-[300px] md:shrink-0';

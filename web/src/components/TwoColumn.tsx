import type { ReactNode } from 'react';

/**
 * 본문 + 오른쪽 레일(M14/14A · 와이어프레임의 배치). **배치의 한 자리**다 — 화면마다
 * `flex` 유틸리티를 적던 꼴을 두지 않는다(공유 `Button`·`Card` 와 같은 이유).
 *
 * 넓은 화면에서 레일은 본문 **오른쪽**이고, `md` 미만에서는 한 단이 되어 본문 **뒤**에 선다.
 * 그 둘이 한 선언으로 참인 것은 **문서 순서가 이미 본문 → 레일**이기 때문이다 — 시각 배치를
 * CSS 로 뒤집지 않으므로 좁은 화면에서 읽는 차례와 넓은 화면에서 보는 차례가 갈리지 않는다.
 *
 * **레일이 필수다.** 배치 게이트의 레일 모집단은 아래 표지를 단 요소 전수인데, 레일 없는 두 단을
 * 허용하면 「표지는 있는데 잴 레일이 없는」 자리가 생기고 게이트가 그것을 건너뛸 수밖에 없다 —
 * 공허가 들어오는 입구다. 레일이 없는 화면은 이 컴포넌트를 쓰지 않는다.
 */

/**
 * 배치 게이트가 모집단을 고르는 **표지**. class 이름으로 고르지 않는 것이 그 게이트의 규율이라
 * (13B 알려진 제한 27) 전용 속성을 둔다.
 *
 * **아래 JSX 는 이 상수가 아니라 같은 글자를 직접 적는다.** JSX 전개(`{...{[NAME]: ''}}`)로
 * 쓰면 핸들러 금지 게이트가 그 전개를 「숨은 핸들러일 수 있다」로 세는데, 그 판정은 옳다 —
 * 전개는 무엇이든 실을 수 있다. 두 글자가 갈리지 않는 것은 짝 test 가 **그려진 DOM** 에서 이
 * 상수로 속성을 읽어 확인한다(선언을 맞춰 보는 것이 아니라 거동을 잰다).
 */
export const TWO_COLUMN_ATTRIBUTE = 'data-layout-two-column';

/** 두 칸을 가르는 표지. 값은 `body` · `rail` 둘이다. */
export const LAYOUT_SLOT_ATTRIBUTE = 'data-layout-slot';

/**
 * 레일의 폭 — 와이어프레임의 300px. `md` 미만에서는 한 단이므로 폭을 주지 않는다.
 *
 * 본문 쪽 `min-w-0` 이 **가로 넘침을 막는 자리**다: flex 자식의 기본 `min-width` 는 `auto` 라
 * 긴 글자 하나가 칸을 뷰포트 밖으로 밀어낸다(배치 게이트의 넘침 축이 보는 바로 그 꼴).
 */
const CONTAINER_CLASS = 'flex flex-col gap-4 md:flex-row md:items-start md:gap-6';
const BODY_CLASS = 'flex min-w-0 flex-1 flex-col gap-4';
const RAIL_CLASS = 'flex w-full min-w-0 flex-col gap-4 md:w-[300px] md:shrink-0';

export function TwoColumn({
  body,
  rail,
  testId,
}: {
  readonly body: ReactNode;
  readonly rail: ReactNode;
  readonly testId?: string;
}) {
  return (
    <div data-testid={testId} data-layout-two-column="" className={CONTAINER_CLASS}>
      <div data-layout-slot="body" className={BODY_CLASS}>
        {body}
      </div>
      {/* 본문 곁의 보조 묶음이다 — `aside` 라야 보조기기가 「곁다리」로 건너뛸 수 있다. */}
      <aside data-layout-slot="rail" className={RAIL_CLASS}>
        {rail}
      </aside>
    </div>
  );
}

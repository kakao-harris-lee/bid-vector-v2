import type { ReactNode } from 'react';

import {
  TWO_COLUMN_BODY_CLASS,
  TWO_COLUMN_CONTAINER_CLASS,
  TWO_COLUMN_RAIL_CLASS,
} from './twoColumnShape';

/**
 * 본문 + 오른쪽 레일(M14/14A · 와이어프레임의 배치). **배치의 한 자리**다 — 화면마다
 * `flex` 유틸리티를 적던 꼴을 두지 않는다(공유 `Button`·`Card` 와 같은 이유).
 *
 * 넓은 화면에서 레일은 본문 **오른쪽**이고, `md` 미만에서는 한 단이 되어 본문 **뒤**에 선다.
 * 그 둘이 한 선언으로 참인 것은 **문서 순서가 이미 본문 → 레일**이기 때문이다 — 시각 배치를
 * CSS 로 뒤집지 않으므로 좁은 화면에서 읽는 차례와 넓은 화면에서 보는 차례가 갈리지 않는다.
 *
 * **레일이 필수다.** 배치 게이트의 레일 모집단은 표지를 단 요소 전수인데, 레일 없는 두 단을
 * 허용하면 「표지는 있는데 잴 레일이 없는」 자리가 생기고 게이트가 그것을 건너뛸 수밖에 없다 —
 * 공허가 들어오는 입구다. 레일이 없는 화면은 이 컴포넌트를 쓰지 않는다.
 *
 * 표지 이름과 클래스는 `twoColumnShape` 한 자리다 — 배치 게이트가 **같은 자리**를 읽는다.
 */

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
    <div data-testid={testId} data-layout-two-column="" className={TWO_COLUMN_CONTAINER_CLASS}>
      <div data-layout-slot="body" className={TWO_COLUMN_BODY_CLASS}>
        {body}
      </div>
      {/* 본문 곁의 보조 묶음이다 — `aside` 라야 보조기기가 「곁다리」로 건너뛸 수 있다. */}
      <aside data-layout-slot="rail" className={TWO_COLUMN_RAIL_CLASS}>
        {rail}
      </aside>
    </div>
  );
}

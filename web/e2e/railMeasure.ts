import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import type { Page } from '@playwright/test';

import { appScreenSlots } from '../scripts/journey-screen-table.mjs';
import {
  LAYOUT_SLOT_ATTRIBUTE,
  TWO_COLUMN_ATTRIBUTE,
  TWO_COLUMN_BODY_SLOT,
  TWO_COLUMN_RAIL_SLOT,
} from '../src/components/twoColumnShape';

/**
 * 레일 축의 **술어 한 자리**(M14/14B).
 *
 * 두 레인이 같은 축을 **다른 모집단**으로 잰다: 스모크 레인은 세션 없이(백엔드 0), 여정 레인은
 * 세션과 씨앗을 쥐고. 술어를 각 spec 에 적으면 그 둘이 갈리고, 갈린 날 어느 쪽이 맞는지 아무도
 * 모른다 — 그래서 **재는 법과 위반의 정의**가 이 모듈 하나에 있고 spec 은 모집단만 고른다.
 *
 * 단언은 여기서 하지 않는다. 위반 목록을 **값으로** 내고 spec 이 그것을 빈 목록과 맞댄다 —
 * 실패 메시지에 그 레인의 주소가 실려야 하고, 그 주소는 spec 이 안다.
 */

export interface RailBox {
  readonly left: number;
  readonly right: number;
  readonly top: number;
  readonly width: number;
}

export interface RailGroup {
  readonly body: RailBox;
  readonly rail: RailBox;
}

/** 표지를 단 묶음 **전수**의 두 칸 상자 — 화면 이름을 적지 않는다. */
export async function measureRails(page: Page): Promise<readonly RailGroup[]> {
  return page.evaluate(
    ({ attribute, slotAttribute, bodySlot, railSlot }) => {
      const read = (node: Element) => {
        const rect = node.getBoundingClientRect();
        return { left: rect.left, right: rect.right, top: rect.top, width: rect.width };
      };
      const found: { body: ReturnType<typeof read>; rail: ReturnType<typeof read> }[] = [];
      for (const group of document.querySelectorAll(`[${attribute}]`)) {
        const body = group.querySelector(`[${slotAttribute}="${bodySlot}"]`);
        const rail = group.querySelector(`[${slotAttribute}="${railSlot}"]`);
        if (body === null || rail === null) {
          throw new Error('두 단 묶음에 칸이 빠졌다');
        }
        found.push({ body: read(body), rail: read(rail) });
      }
      return found;
    },
    {
      attribute: TWO_COLUMN_ATTRIBUTE,
      slotAttribute: LAYOUT_SLOT_ATTRIBUTE,
      bodySlot: TWO_COLUMN_BODY_SLOT,
      railSlot: TWO_COLUMN_RAIL_SLOT,
    },
  );
}

export type RailViewport = 'wide' | 'narrow';

/**
 * 묶음 전수의 **위반 목록**. 빈 목록이 통과다.
 *
 * 넓은 화면에서 레일은 본문 **오른쪽**이고, 좁은 화면에서는 한 단이 되어 본문 **아래**에 선다.
 * 두 쪽 다 **폭이 0 이 아닌 것**을 먼저 본다 — 숨겨서 좌표를 0 으로 만드는 길을 닫는 자리다.
 *
 * **가로 넘침은 이 축이 재지 않는다.** 정책이 다르다 — 일부러 가로로 스크롤되는 줄(입찰함의
 * 상태 탭)이 출하에 있고, 그 축을 여기서 함께 재면 그 줄이 거짓으로 붉는다(실측).
 */
export function railViolations(groups: readonly RailGroup[], viewport: RailViewport): readonly string[] {
  const broken: string[] = [];
  for (const [at, group] of groups.entries()) {
    if (group.body.width <= 0) {
      broken.push(`묶음 ${at}: 본문 칸의 폭이 0 이다`);
    }
    if (group.rail.width <= 0) {
      broken.push(`묶음 ${at}: 레일 칸의 폭이 0 이다`);
    }
    if (viewport === 'wide') {
      if (group.rail.left < group.body.right - 1) {
        broken.push(`묶음 ${at}: 레일이 본문 오른쪽이 아니다`);
      }
      continue;
    }
    if (group.rail.top <= group.body.top) {
      broken.push(`묶음 ${at}: 좁은 화면에서 레일이 본문 아래가 아니다`);
    }
    // 한 단 = 두 칸이 같은 왼쪽 끝에서 시작한다.
    if (Math.abs(group.rail.left - group.body.left) >= 2) {
      broken.push(`묶음 ${at}: 좁은 화면에서 한 단이 아니다`);
    }
  }
  return broken;
}

/** 경로 인자를 채우는 토큰 — **주소를 만드는 값**이고 판정하는 값이 아니다. */
const PATH_PARAM_TOKEN = 'SYN-LAYOUT-PROBE';

/** catch-all 자리가 도는 주소 — 어느 자리도 받지 않는 것이 그 자리의 뜻이다. */
export const CATCH_ALL_ADDRESS = '/no-such-place';

/**
 * 출하 화면의 주소 전수 — **자리 표에서 구성한다**(열거하지 않는다).
 *
 * `appScreenSlots` 는 자리 표를 못 읽거나 비면 **던진다** — 조용한 빈 집합이 되지 않는다.
 * 그래서 14C 의 워크시트 레일·14D 의 입찰함 레일은 머지되는 순간 두 레인 모두에 자동으로 든다.
 *
 * 경로 인자를 채우는 토큰이 **그 자리의 값과 맞지 않아도 된다**: 이 축이 재는 것은 「그려진 레일이
 * 제자리인가」이고, 값이 없어 레일이 안 그려지면 그 화면은 묶음 0 으로 빠진다(비공허 하한이
 * 전체에서 그 자리를 지킨다). 값이 **있어야** 서는 레일은 세션을 쥔 레인이 받는다.
 */
export function shippedAddresses(): readonly string[] {
  const routesText = readFileSync(
    resolve(dirname(fileURLToPath(import.meta.url)), '..', 'src', 'routes.tsx'),
    'utf8',
  );
  const slots = appScreenSlots(routesText);
  return slots.map((slot: { readonly path: string }) =>
    slot.path === '*'
      ? CATCH_ALL_ADDRESS
      : slot.path
          .split('/')
          .map((one) => (one.startsWith(':') ? PATH_PARAM_TOKEN : one))
          .join('/'),
  );
}

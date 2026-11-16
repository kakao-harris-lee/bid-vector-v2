import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { describe, expect, it } from 'vitest';

import { appScreenSlots, gateScreens, pathMatches } from './journey-screen-table.mjs';

/**
 * M13/13B — 터치 타깃 게이트의 **화면 모집단**이 앱의 자리 표와 같은가.
 *
 * 요소 축은 선택자 전수가 들지만 화면 축은 spec 안의 표다. 등식이 없으면 새 화면이 자리 표에
 * 들어와도 게이트가 그 화면에 대해 **아무 말도 하지 않는다** — 저장소가 이미 한 번 겪은 꼴이다
 * (M9 에서 화면 E 카드가 마운트 목록 밖이라 그 공백에 위반이 살아 있었다).
 */
const webRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const routesText = readFileSync(resolve(webRoot, 'src', 'routes.tsx'), 'utf8');
const specText = readFileSync(resolve(webRoot, 'e2e', 'journey', 'touch-target.spec.ts'), 'utf8');

const slots = appScreenSlots(routesText);
const screens = gateScreens(specText);

describe('화면 모집단의 등식 (M13/13B)', () => {
  it('두 표를 실제로 읽었다 — 빈 표는 등식을 공허하게 만든다', () => {
    expect(slots.length).toBeGreaterThan(5);
    expect(screens.length).toBeGreaterThan(5);
    expect(slots.every((one) => typeof one.id === 'string' && typeof one.path === 'string')).toBe(true);
    expect(screens.every((one) => typeof one.slotId === 'string' && typeof one.path === 'string')).toBe(true);
  });

  /**
   * **등식이다 — 포함이 아니다.** 포함으로 두면 ⓐ 자리를 더하고 게이트에 안 적는 쪽과 ⓑ 게이트
   * 표에 없는 자리를 적는 쪽 가운데 한쪽이 조용해진다.
   */
  it('앱의 자리 전수 == 게이트가 도는 화면 전수', () => {
    expect([...screens.map((one) => one.slotId)].sort()).toStrictEqual([...slots.map((one) => one.id)].sort());
  });

  it('자리 이름이 겹치지 않는다 — 겹치면 등식이 집합으로 접혀 하나가 사라진다', () => {
    expect(new Set(slots.map((one) => one.id)).size).toBe(slots.length);
    expect(new Set(screens.map((one) => one.slotId)).size).toBe(screens.length);
  });

  /** 이름만 맞대면 **다른 주소를 도는** 게이트가 초록이다. 경로가 그 자리의 꼴인지도 본다. */
  it('게이트가 도는 주소가 그 자리의 경로 꼴이다', () => {
    const wrong = screens
      .map((screen) => ({ screen, slot: slots.find((one) => one.id === screen.slotId) }))
      .filter(({ screen, slot }) => slot !== undefined && !pathMatches(slot.path, screen.path))
      .map(({ screen, slot }) => `${screen.slotId}: ${screen.path} 가 ${slot.path} 의 꼴이 아니다`);

    expect(wrong).toStrictEqual([]);
  });
});

describe('경로 꼴 술어의 양성·음성 대조', () => {
  it('경로 인자는 한 칸을 받고 칸 수가 다르면 받지 않는다', () => {
    expect(pathMatches('/notices/:noticeNumber/:noticeRound', '/notices/SYN-1/000')).toBe(true);
    expect(pathMatches('/notices/:noticeNumber/:noticeRound', '/notices/SYN-1')).toBe(false);
    expect(pathMatches('/notices/:noticeNumber/:noticeRound', '/notices/SYN-1/000/extra')).toBe(false);
  });

  it('질의 문자열은 자리를 바꾸지 않는다', () => {
    expect(pathMatches('/bids', '/bids?tab=closed')).toBe(true);
  });

  it('다른 자리의 주소는 받지 않는다', () => {
    expect(pathMatches('/bids', '/results')).toBe(false);
    expect(pathMatches('/', '/start')).toBe(false);
  });

  it('catch-all 은 어느 주소든 받는다 — 그 자리의 뜻이 그렇다', () => {
    expect(pathMatches('*', '/no-such-place')).toBe(true);
  });
});

describe('읽기의 비공허 — 표가 사라지면 던진다', () => {
  it('자리 표가 없으면 던진다', () => {
    expect(() => appScreenSlots('export const other = [];\n')).toThrow(/screenSlots/u);
  });

  it('자리 표가 비면 던진다', () => {
    expect(() => appScreenSlots('export const screenSlots = [];\n')).toThrow(/비었다/u);
  });

  it('표 밖의 두 자리가 사라지면 던진다', () => {
    const text = "export const screenSlots = [{ id: 'home', path: '/' }];\n";

    expect(() => appScreenSlots(text)).toThrow(/loginRoute/u);
  });

  it('게이트 표가 없으면 던진다', () => {
    expect(() => gateScreens('export const other = [];\n')).toThrow(/TOUCH_TARGET_SCREENS/u);
  });
});

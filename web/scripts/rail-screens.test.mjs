import { mkdtempSync, mkdirSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { describe, expect, it } from 'vitest';

import { RAIL_SCREEN_FLOOR, railScreenSlots } from './rail-screens.mjs';

/**
 * M14/14B 수정 라운드 1 (v HIGH-1) — **레일을 그리는 자리 전수**를 자리 표에서 구성하는 술어.
 *
 * 이 술어가 없으면 레일 축의 비공허가 **합계 하나**이고, 레일이 둘 이상 선 뒤에는 어느 하나가
 * 측정에서 빠져도 나머지가 그 수를 떠받친다. 여기서 잠그는 것은 「집합이 소스에서 나온다」와
 * 「조용한 빈 집합·조용한 누락이 없다」 둘이다.
 */
const webRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');

describe('출하 트리', () => {
  const slots = railScreenSlots(webRoot);

  it('레일을 그리는 자리를 **실제로** 찾는다 — 빈 집합은 축을 공허하게 만든다', () => {
    expect(slots.length).toBeGreaterThan(0);
    expect(slots.every((one) => typeof one.id === 'string' && one.path.startsWith('/'))).toBe(true);
  });

  /**
   * 오늘 레일을 그리는 화면은 시작하기 하나다. 이 단언은 **수를 박지 않는다** — 뒤 레인이 레일을
   * 붙이면 집합이 늘고 그때 이 줄은 그대로 참이다(포함이지 등식이 아니다).
   */
  it('시작하기가 그 집합에 든다', () => {
    expect(slots.map((one) => one.id)).toContain('onboarding');
  });

  /**
   * **래칫** — 구성이 늘어나는 쪽을 맡고 이 목록이 줄어드는 쪽을 막는다.
   *
   * 구성만으로는 **손으로 짠 두 단**을 못 막는다: 화면이 `TwoColumn` 을 그만 쓰고 같은 모양을
   * 직접 적으면 그 자리가 집합에서 조용히 빠지고 의무도 함께 사라진다(§12.3 (z) ⓓ). 그때
   * 이 단언이 붉는다 — **트리와 무관하게**(머지한 트리에서도 같다).
   */
  it('래칫 — 한 번 레일을 가진 자리는 집합에서 빠지지 않는다', () => {
    expect(slots.map((one) => one.id)).toEqual(expect.arrayContaining([...RAIL_SCREEN_FLOOR]));
  });

  /** 레일이 없는 화면은 들지 않는다 — 들면 그 주소에서 영영 못 찾을 레일을 요구하게 된다. */
  it('로그인·없는 주소는 들지 않는다', () => {
    expect(slots.map((one) => one.id)).not.toContain('login');
    expect(slots.map((one) => one.id)).not.toContain('not-found');
  });
});

/**
 * 술어 자신의 **양성·음성 대조**. 합성 트리를 디스크에 세워 실제 파일 해소를 지나게 한다 —
 * 문자열을 맞춰 보는 것이 아니라 거동을 잰다.
 */
describe('술어의 양성·음성 대조', () => {
  function tree(files) {
    const root = mkdtempSync(resolve(tmpdir(), 'rail-screens-'));
    for (const [path, text] of Object.entries(files)) {
      const full = resolve(root, path);
      mkdirSync(dirname(full), { recursive: true });
      writeFileSync(full, text);
    }
    return root;
  }

  const TWO_COLUMN = 'export function TwoColumn() { return null; }\n';
  const ROUTES = (body) => `import { Alpha } from './screens/Alpha';
import { Beta } from './screens/Beta';

export const screenSlots = [
  { id: 'alpha', path: '/alpha', element: <Alpha /> },
  { id: 'beta', path: '/beta', element: <Beta /> },
];
export const loginRoute = { id: 'login', path: '/login' };
export const notFoundSlot = { id: 'not-found', path: '*' };
${body}`;

  it('레일을 그리는 화면이 든다 · 안 그리는 화면은 빠진다', () => {
    const root = tree({
      'src/components/TwoColumn.tsx': TWO_COLUMN,
      'src/screens/Alpha.tsx':
        "import { TwoColumn } from '../components/TwoColumn';\nexport const Alpha = () => <TwoColumn />;\n",
      'src/screens/Beta.tsx': 'export const Beta = () => null;\n',
      'src/routes.tsx': ROUTES(''),
    });

    expect(railScreenSlots(root).map((one) => one.id)).toStrictEqual(['alpha']);
  });

  /**
   * **들여오기만 하고 안 그리면 거짓이다**(수정 라운드 1 실측의 거짓 양성).
   *
   * 출하 트리에서 오늘의 공고 화면이 시작하기 모듈에서 배너를 들여온다 — import 폐포로 물으면
   * 그 화면이 `TwoColumn` 에 **닿고**, 레일을 하나도 그리지 않는데 레일 의무가 걸린다. 여정
   * 레인이 그 자리를 실제로 붉혔다.
   */
  it('레일을 그리는 모듈을 들여오기만 한 화면은 들지 않는다', () => {
    const root = tree({
      'src/components/TwoColumn.tsx': TWO_COLUMN,
      'src/screens/Alpha.tsx':
        "import { TwoColumn } from '../components/TwoColumn';\nexport const Alpha = () => <TwoColumn />;\nexport const Banner = () => null;\n",
      'src/screens/Beta.tsx': "import { Banner } from './Alpha';\nexport const Beta = () => <Banner />;\n",
      'src/routes.tsx': ROUTES(''),
    });

    expect(railScreenSlots(root).map((one) => one.id)).toStrictEqual(['alpha']);
  });

  /** 들여오고 **안 그리는** 자기 모듈도 거짓이다 — 심볼을 아는 것이 레일의 근거가 아니다. */
  it('들여왔지만 그리지 않으면 들지 않는다', () => {
    const root = tree({
      'src/components/TwoColumn.tsx': TWO_COLUMN,
      'src/screens/Alpha.tsx':
        "import { TwoColumn } from '../components/TwoColumn';\nexport const Alpha = () => (TwoColumn ? null : null);\n",
      'src/screens/Beta.tsx': 'export const Beta = () => null;\n',
      'src/routes.tsx': ROUTES(''),
    });

    expect(railScreenSlots(root)).toStrictEqual([]);
  });

  /**
   * **알려진 제한**: 레일을 **자기 모듈 밖**에서 그리면 못 본다. 그 자리는 래칫 목록이 받는다 —
   * 제한을 적기만 하는 것이 아니라 **거동으로** 고정해, 다음 사람이 이 성질을 모르고 기대지
   * 않게 한다.
   */
  it('레일을 다른 모듈에서 그리면 못 본다 — 그 자리는 래칫이 받는다', () => {
    const root = tree({
      'src/components/TwoColumn.tsx': TWO_COLUMN,
      'src/screens/inner/Frame.tsx':
        "import { TwoColumn } from '../../components/TwoColumn';\nexport const Frame = () => <TwoColumn />;\n",
      'src/screens/Alpha.tsx': "import { Frame } from './inner/Frame';\nexport const Alpha = () => <Frame />;\n",
      'src/screens/Beta.tsx': 'export const Beta = () => null;\n',
      'src/routes.tsx': ROUTES(''),
    });

    expect(railScreenSlots(root)).toStrictEqual([]);
  });

  /** **타입만** 들여온 모듈은 그릴 수 없다 — 레일의 근거가 되지 못한다. */
  it('타입 전용 import 는 근거가 아니다', () => {
    const root = tree({
      'src/components/TwoColumn.tsx': TWO_COLUMN,
      'src/screens/Alpha.tsx':
        "import type { TwoColumn } from '../components/TwoColumn';\nexport const Alpha = () => <TwoColumn />;\n",
      'src/screens/Beta.tsx': 'export const Beta = () => null;\n',
      'src/routes.tsx': ROUTES(''),
    });

    expect(railScreenSlots(root)).toStrictEqual([]);
  });

  it('자리 표가 없거나 비면 던진다 — 조용한 빈 집합을 두지 않는다', () => {
    const empty = tree({
      'src/components/TwoColumn.tsx': TWO_COLUMN,
      'src/routes.tsx': 'export const screenSlots = [];\nexport const loginRoute = {};\n',
    });

    expect(() => railScreenSlots(empty)).toThrow(/screenSlots/u);
  });

  /** element 는 있는데 그 태그를 **import 에서 못 찾으면** 던진다(조용히 빠지지 않는다). */
  it('element 태그를 import 에서 못 찾으면 던진다', () => {
    const root = tree({
      'src/components/TwoColumn.tsx': TWO_COLUMN,
      'src/routes.tsx': `export const screenSlots = [{ id: 'alpha', path: '/alpha', element: <Ghost /> }];
export const loginRoute = { id: 'login', path: '/login' };
export const notFoundSlot = { id: 'not-found', path: '*' };
`,
    });

    expect(() => railScreenSlots(root)).toThrow(/Ghost/u);
  });

  it('레일 원시 요소가 사라지면 던진다', () => {
    const root = tree({
      'src/screens/Alpha.tsx': 'export const Alpha = () => null;\n',
      'src/screens/Beta.tsx': 'export const Beta = () => null;\n',
      'src/routes.tsx': ROUTES(''),
    });

    expect(() => railScreenSlots(root)).toThrow(/TwoColumn/u);
  });
});

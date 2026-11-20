import { mkdtempSync, mkdirSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { describe, expect, it } from 'vitest';

import { RAIL_SCREEN_FLOOR, railScreenSlots } from './rail-screens.mjs';

/**
 * M14/14B — **레일을 그리는 자리 전수**를 자리 표에서 구성하는 술어.
 *
 * 이 술어가 없으면 레일 축의 비공허가 **합계 하나**이고, 레일이 둘 이상 선 뒤에는 어느 하나가
 * 측정에서 빠져도 나머지가 그 수를 떠받친다. 여기서 잠그는 것은 셋이다 — 집합이 소스에서
 * 나온다 · 신원이 **심볼**이라 이름을 바꿔 불러도 같다 · **등식**이라 줄어드는 쪽이 붉는다.
 */
const webRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');

describe('출하 트리', () => {
  const slots = railScreenSlots(webRoot);

  it('레일을 그리는 자리를 **실제로** 찾는다 — 빈 집합은 축을 공허하게 만든다', () => {
    expect(slots.length).toBeGreaterThan(0);
    expect(slots.every((one) => typeof one.id === 'string' && one.path.startsWith('/'))).toBe(true);
  });

  /**
   * **래칫은 등식이다**(v HIGH-1′). 포함(`arrayContaining`)으로 두면 한쪽 방향이 조용해진다 —
   * 실제로 14D 의 입찰함 두 단을 표지 없이 손으로 짜자 그 자리가 유도 집합에서 빠지고 세 게이트가
   * 모두 초록이었다.
   *
   * 등식이라 **양쪽**이 막힌다: 줄면 그 자리가 빠진 것이고, 늘면 머지하는 쪽이 자기 이름을
   * [RAIL_SCREEN_FLOOR] 에 적을 차례다.
   */
  it('유도 집합 == 래칫 목록 — 줄어도 늘어도 붉는다', () => {
    expect([...slots.map((one) => one.id)].sort()).toStrictEqual([...RAIL_SCREEN_FLOOR].sort());
  });

  /** 레일이 없는 화면은 들지 않는다 — 들면 그 주소에서 영영 못 찾을 레일을 요구하게 된다. */
  it('로그인·없는 주소는 들지 않는다', () => {
    expect(slots.map((one) => one.id)).not.toContain('login');
    expect(slots.map((one) => one.id)).not.toContain('not-found');
  });

  /** 래칫 목록은 **밀어 넣을 수 없다** — 부르는 쪽이 늘리면 등식이 뜻을 잃는다(cr N-4). */
  it('래칫 목록이 얼어 있다', () => {
    expect(Object.isFrozen(RAIL_SCREEN_FLOOR)).toBe(true);
  });
});

/**
 * 술어 자신의 **양성·음성 대조**. 합성 트리를 디스크에 세워 실제 타입 검사기를 지나게 한다 —
 * 글자를 맞춰 보는 것이 아니라 거동을 잰다.
 */
describe('술어의 양성·음성 대조', () => {
  const TS_CONFIG = JSON.stringify({
    compilerOptions: {
      target: 'ES2023',
      lib: ['ES2023', 'DOM'],
      module: 'ESNext',
      moduleResolution: 'bundler',
      jsx: 'react-jsx',
      rootDir: 'src',
      noEmit: true,
      strict: true,
      skipLibCheck: true,
    },
    include: ['src'],
  });

  function tree(files) {
    const root = mkdtempSync(resolve(tmpdir(), 'rail-screens-'));
    writeFileSync(resolve(root, 'tsconfig.json'), TS_CONFIG);
    for (const [path, text] of Object.entries(files)) {
      const full = resolve(root, path);
      mkdirSync(dirname(full), { recursive: true });
      writeFileSync(full, text);
    }
    return root;
  }

  const TWO_COLUMN = 'export function TwoColumn() { return null; }\n';
  const ROUTES = `import { Alpha } from './screens/Alpha';
import { Beta } from './screens/Beta';

export const screenSlots = [
  { id: 'alpha', path: '/alpha', element: <Alpha /> },
  { id: 'beta', path: '/beta', element: <Beta /> },
];
export const loginRoute = { id: 'login', path: '/login' };
export const notFoundSlot = { id: 'not-found', path: '*' };
`;
  const PLAIN_BETA = 'export const Beta = () => null;\n';

  const idsOf = (root) => railScreenSlots(root).map((one) => one.id);

  it('레일을 그리는 화면이 든다 · 안 그리는 화면은 빠진다', () => {
    const root = tree({
      'src/components/TwoColumn.tsx': TWO_COLUMN,
      'src/screens/Alpha.tsx':
        "import { TwoColumn } from '../components/TwoColumn';\nexport const Alpha = () => <TwoColumn />;\n",
      'src/screens/Beta.tsx': PLAIN_BETA,
      'src/routes.tsx': ROUTES,
    });

    expect(idsOf(root)).toStrictEqual(['alpha']);
  });

  /**
   * **별칭**(v HIGH-1′). 이름 글자로 맞대던 판은 이 꼴을 놓쳐 그 화면을 의무에서 조용히 빼냈다.
   */
  it('별칭으로 바꿔 불러도 든다', () => {
    const root = tree({
      'src/components/TwoColumn.tsx': TWO_COLUMN,
      'src/screens/Alpha.tsx':
        "import { TwoColumn as TC } from '../components/TwoColumn';\nexport const Alpha = () => <TC />;\n",
      'src/screens/Beta.tsx': PLAIN_BETA,
      'src/routes.tsx': ROUTES,
    });

    expect(idsOf(root)).toStrictEqual(['alpha']);
  });

  /** **네임스페이스**(v HIGH-1′ 의 둘째 우회) — `<L.TwoColumn/>` 도 같은 심볼이다. */
  it('네임스페이스로 그려도 든다', () => {
    const root = tree({
      'src/components/TwoColumn.tsx': TWO_COLUMN,
      'src/screens/Alpha.tsx':
        "import * as L from '../components/TwoColumn';\nexport const Alpha = () => <L.TwoColumn />;\n",
      'src/screens/Beta.tsx': PLAIN_BETA,
      'src/routes.tsx': ROUTES,
    });

    expect(idsOf(root)).toStrictEqual(['alpha']);
  });

  /** 화면을 **배럴 index** 로 들여와도 그 모듈을 찾는다(심볼이 끝까지 풀린다). */
  it('배럴 index 를 지나도 화면 모듈을 찾는다', () => {
    const root = tree({
      'src/components/TwoColumn.tsx': TWO_COLUMN,
      'src/screens/Alpha.tsx':
        "import { TwoColumn } from '../components/TwoColumn';\nexport const Alpha = () => <TwoColumn />;\n",
      'src/screens/Beta.tsx': PLAIN_BETA,
      'src/screens/index.ts': "export { Alpha } from './Alpha';\nexport { Beta } from './Beta';\n",
      'src/routes.tsx': `import { Alpha, Beta } from './screens';

export const screenSlots = [
  { id: 'alpha', path: '/alpha', element: <Alpha /> },
  { id: 'beta', path: '/beta', element: <Beta /> },
];
export const loginRoute = { id: 'login', path: '/login' };
export const notFoundSlot = { id: 'not-found', path: '*' };
`,
    });

    expect(idsOf(root)).toStrictEqual(['alpha']);
  });

  /**
   * **들여오기만 하고 안 그리면 거짓이다**(실측의 거짓 양성).
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
      'src/routes.tsx': ROUTES,
    });

    expect(idsOf(root)).toStrictEqual(['alpha']);
  });

  /** 들여오고 **안 그리는** 자기 모듈도 거짓이다 — 심볼을 아는 것이 레일의 근거가 아니다. */
  it('들여왔지만 그리지 않으면 들지 않는다', () => {
    const root = tree({
      'src/components/TwoColumn.tsx': TWO_COLUMN,
      'src/screens/Alpha.tsx':
        "import { TwoColumn } from '../components/TwoColumn';\nexport const Alpha = () => (TwoColumn ? null : null);\n",
      'src/screens/Beta.tsx': PLAIN_BETA,
      'src/routes.tsx': ROUTES,
    });

    expect(idsOf(root)).toStrictEqual([]);
  });

  /**
   * **타입 전용 import 는 이제 든다** — 앞 판의 주장이 뒤집힌 자리라 지우지 않고 **고쳐** 남긴다.
   *
   * 이름 글자로 맞대던 판에서는 「타입만 들여왔으니 그릴 수 없다」가 참이었다. 신원이 심볼이 된
   * 지금은 같은 심볼로 풀리므로 이 술어가 그 자리를 **센다**. 그래도 해가 없다: 그런 프로그램은
   * **컴파일되지 않는다**(TS1361 — 값으로 쓸 수 없다). 즉 출하될 수 없는 꼴이고, 그 자리는 이
   * 게이트가 아니라 typecheck(W-3)가 먼저 막는다. 층이 다르다는 사실을 거동으로 적어 둔다.
   */
  it('타입 전용 import 도 센다 — 그 꼴은 typecheck 가 먼저 막는다', () => {
    const root = tree({
      'src/components/TwoColumn.tsx': TWO_COLUMN,
      'src/screens/Alpha.tsx':
        "import type { TwoColumn } from '../components/TwoColumn';\nexport const Alpha = () => <TwoColumn />;\n",
      'src/screens/Beta.tsx': PLAIN_BETA,
      'src/routes.tsx': ROUTES,
    });

    expect(idsOf(root)).toStrictEqual(['alpha']);
  });

  /** **같은 이름의 다른 심볼**은 들지 않는다 — 신원이 글자가 아니라 심볼이라는 뜻이다. */
  it('이름만 같은 남의 컴포넌트는 들지 않는다', () => {
    const root = tree({
      'src/components/TwoColumn.tsx': TWO_COLUMN,
      'src/screens/fake.tsx': 'export function TwoColumn() { return null; }\n',
      'src/screens/Alpha.tsx': "import { TwoColumn } from './fake';\nexport const Alpha = () => <TwoColumn />;\n",
      'src/screens/Beta.tsx': PLAIN_BETA,
      'src/routes.tsx': ROUTES,
    });

    expect(idsOf(root)).toStrictEqual([]);
  });

  /**
   * **알려진 제한**: 레일을 **자기 모듈 밖**에서 그리면 못 본다. 그 자리는 래칫의 등식이 받는다 —
   * 제한을 적기만 하는 것이 아니라 **거동으로** 고정해, 다음 사람이 이 성질을 모르고 기대지 않게 한다.
   */
  it('레일을 다른 모듈에서 그리면 못 본다 — 그 자리는 래칫이 받는다', () => {
    const root = tree({
      'src/components/TwoColumn.tsx': TWO_COLUMN,
      'src/screens/inner/Frame.tsx':
        "import { TwoColumn } from '../../components/TwoColumn';\nexport const Frame = () => <TwoColumn />;\n",
      'src/screens/Alpha.tsx': "import { Frame } from './inner/Frame';\nexport const Alpha = () => <Frame />;\n",
      'src/screens/Beta.tsx': PLAIN_BETA,
      'src/routes.tsx': ROUTES,
    });

    expect(idsOf(root)).toStrictEqual([]);
  });

  it('자리 표가 없거나 비면 던진다 — 조용한 빈 집합을 두지 않는다', () => {
    const empty = tree({
      'src/components/TwoColumn.tsx': TWO_COLUMN,
      'src/routes.tsx': 'export const screenSlots = [];\nexport const loginRoute = {};\n',
    });

    expect(() => railScreenSlots(empty)).toThrow(/screenSlots/u);
  });

  /** element 는 있는데 그 태그의 **심볼을 못 풀면** 던진다(조용히 빠지지 않는다). */
  it('element 태그의 심볼을 못 풀면 던진다', () => {
    const root = tree({
      'src/components/TwoColumn.tsx': TWO_COLUMN,
      'src/routes.tsx': `export const screenSlots = [{ id: 'alpha', path: '/alpha', element: <Ghost /> }];
export const loginRoute = { id: 'login', path: '/login' };
export const notFoundSlot = { id: 'not-found', path: '*' };
`,
    });

    expect(() => railScreenSlots(root)).toThrow(/alpha/u);
  });

  it('레일 원시 요소가 사라지면 던진다', () => {
    const root = tree({
      'src/screens/Alpha.tsx': 'export const Alpha = () => null;\n',
      'src/screens/Beta.tsx': PLAIN_BETA,
      'src/routes.tsx': ROUTES,
    });

    expect(() => railScreenSlots(root)).toThrow(/TwoColumn/u);
  });
});

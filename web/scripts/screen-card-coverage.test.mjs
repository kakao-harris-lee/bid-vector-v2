import { existsSync, readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { describe, expect, it } from 'vitest';

import {
  collectScreenVocabulary,
  missingVocabularyDeclarations,
  mountedCardsOf,
  screenProgram,
  vocabularyComponentsOf,
  VOCABULARY_DECLARATIONS,
} from './screen-card-coverage.mjs';

/**
 * **DOM 어휘 게이트의 모집단을 소스에서 유도한다**(r1 v M-1 처방 「열거 말고 구조」).
 *
 * r1 의 실제 사고가 이것이다: 게이트는 초록인데 화면 E 카드가 마운트 목록 밖이라 그 카드의
 * 위반(원문 통화)이 살아 있었다. 목록을 손으로 고치는 것만으로는 **다음 카드**에서 같은 일이
 * 반복된다 — 그래서 의무 집합을 세는 자리를 둔다.
 *
 * **M9/web-followups — 「쓴다」의 판정을 TS 심볼 그래프로 옮겼다**
 * (`OPEN-M9-SCREEN-COVERAGE-SYMBOL-GRAPH`). 아래 「별칭·재수출·네임스페이스·지역 변수」 넷은
 * 앞 판에서 **전부 초록**이었다(호출 식별자의 이름만 보았기 때문이다).
 */
const webRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const screensDir = resolve(webRoot, 'src', 'screens');
const gatePath = resolve(screensDir, 'screenVocabulary.test.tsx');

const { program, obligations, screens, files } = collectScreenVocabulary(webRoot);
const mounted = mountedCardsOf(readFileSync(gatePath, 'utf8'));

describe('어휘 게이트의 모집단 (r1 v M-1)', () => {
  it('화면 파일을 실제로 읽었다 — 빈 훑기는 모든 등식을 공허하게 만든다', () => {
    expect(files.length).toBeGreaterThan(5);
    expect(VOCABULARY_DECLARATIONS.length).toBeGreaterThan(0);
  });

  /**
   * **선언 목록이 썩지 않는다**(M9/web-followups). 어휘 함수의 이름을 바꾸거나 모듈을 옮기면 앞
   * 판에서는 의무 집합이 조용히 줄었다 — 이제 그 자리가 먼저 붉어진다.
   */
  it('선언 목록의 항목이 전부 그 모듈의 내보낸 이름이다', () => {
    expect(missingVocabularyDeclarations(program, webRoot)).toStrictEqual([]);
  });

  it('게이트가 마운트 목록을 선언한다', () => {
    expect(mounted, 'GATE_MOUNTED_CARDS 를 AST 로 찾지 못했다').not.toBeNull();
    expect(mounted.every((one) => !one.startsWith('<'))).toBe(true);
  });

  /**
   * **등식이다 — 포함이 아니다.** 포함으로 두면 목록에서 카드를 빼는 변이가 초록이고, 그것이
   * r1 에서 일어난 일의 거울상이다.
   */
  it('표시 어휘를 쓰는 카드 전수 == 게이트가 마운트하는 카드 전수', () => {
    const required = [...new Set(obligations.map((one) => one.name))].sort();

    expect([...mounted].sort(), `의무: ${required.join(', ')}`).toStrictEqual(required);
  });

  /**
   * 화면(`*Screen`)은 질의 목이 필요해 이 게이트가 마운트하지 못한다. 그 제외를 **이름 규약
   * 하나에 기대지 않는다** — 뺀 이름마다 짝이 되는 test 파일이 실제로 있는지 센다.
   */
  it('의무에서 뺀 화면마다 자기 test 파일이 있다', () => {
    for (const { name } of screens) {
      const spec = resolve(screensDir, `${name}.test.tsx`);
      expect(existsSync(spec), `${name} 을 의무에서 뺐는데 ${name}.test.tsx 가 없다`).toBe(true);
    }
  });
});

/**
 * 합성 원문을 **실제 트리 위에 얹어** 한 프로그램으로 잰다 — 합성 파일이 실제 표시 모듈을
 * import 하므로 대조가 **진짜 심볼 그래프**를 지난다. 프로그램을 한 번만 여는 이유는 비용이다
 * (타입 검사기를 세우는 일이다).
 */
const PROBE_DIR = resolve(screensDir, '__probe__');
const probePath = (name) => resolve(PROBE_DIR, name);

const PROBES = {
  'direct.tsx': `
    import { instantDisplayOf } from '../../display/timeDisplay';
    export function DirectCard({ at }: { readonly at: string }) {
      return <span>{instantDisplayOf(at).text}</span>;
    }
  `,
  'arrow.tsx': `
    import { currencyDisplayOf } from '../../display/moneyDisplay';
    export const ArrowCard = ({ code }: { readonly code: string }) => <span>{currencyDisplayOf(code).label}</span>;
  `,
  'alias.tsx': `
    import { instantDisplayOf as fmt } from '../../display/timeDisplay';
    export function AliasCard({ at }: { readonly at: string }) {
      return <span>{fmt(at).text}</span>;
    }
  `,
  'namespace.tsx': `
    import * as display from '../../display/timeDisplay';
    export function NamespaceCard({ at }: { readonly at: string }) {
      return <span>{display.instantDisplayOf(at).text}</span>;
    }
  `,
  'local.tsx': `
    import { instantDisplayOf } from '../../display/timeDisplay';
    const pick = instantDisplayOf;
    export function LocalCard({ at }: { readonly at: string }) {
      return <span>{pick(at).text}</span>;
    }
  `,
  'reexport.ts': `
    export { instantDisplayOf } from '../../display/timeDisplay';
  `,
  'viaReexport.tsx': `
    import { instantDisplayOf } from './reexport';
    export function ReexportCard({ at }: { readonly at: string }) {
      return <span>{instantDisplayOf(at).text}</span>;
    }
  `,
  'plain.tsx': `
    export function PlainCard({ title }: { readonly title: string }) {
      return <span>{title}</span>;
    }
  `,
  'otherModule.tsx': `
    import { tabOf } from '../../display/bidBoxDisplay';
    export function OtherCard() {
      return <span>{tabOf('WATCHING')}</span>;
    }
  `,
  'inner.tsx': `
    import { instantDisplayOf } from '../../display/timeDisplay';
    function Inner({ at }: { readonly at: string }) {
      return <span>{instantDisplayOf(at).text}</span>;
    }
    export function Outer({ at }: { readonly at: string }) {
      return <Inner at={at} />;
    }
  `,
};

const probeOverlay = new Map(Object.entries(PROBES).map(([name, text]) => [probePath(name), text]));
const probeProgram = screenProgram(webRoot, probeOverlay);

function componentsIn(name) {
  const source = probeProgram.getSourceFile(probePath(name));
  expect(source, `${name} 이 프로그램에 없다 — 대조가 공허하다`).toBeDefined();
  return vocabularyComponentsOf(probeProgram, source);
}

describe('의무 판정의 양성·음성 대조', () => {
  it('어휘를 쓰는 내보낸 컴포넌트를 집는다', () => {
    expect(componentsIn('direct.tsx')).toStrictEqual(['DirectCard']);
  });

  it('화살표 함수로 내보낸 꼴도 집는다 — 선언 꼴 규약에 기대지 않는다', () => {
    expect(componentsIn('arrow.tsx')).toStrictEqual(['ArrowCard']);
  });

  /** **앞 판에서 초록이던 넷** — 호출 지점의 글자가 선언과 다른 꼴이다. */
  it('별칭으로 부르는 꼴을 집는다', () => {
    expect(componentsIn('alias.tsx')).toStrictEqual(['AliasCard']);
  });

  it('네임스페이스로 접근하는 꼴을 집는다 — 앞 판은 멤버 호출을 아예 보지 않았다', () => {
    expect(componentsIn('namespace.tsx')).toStrictEqual(['NamespaceCard']);
  });

  it('지역 변수로 집어 가 부르는 꼴을 집는다', () => {
    expect(componentsIn('local.tsx')).toStrictEqual(['LocalCard']);
  });

  it('다른 모듈로 재수출한 것을 부르는 꼴을 집는다', () => {
    expect(componentsIn('viaReexport.tsx')).toStrictEqual(['ReexportCard']);
  });

  it('어휘를 안 쓰면 의무가 아니다', () => {
    expect(componentsIn('plain.tsx')).toStrictEqual([]);
  });

  /**
   * **음성 대조** — 표시 모듈의 함수를 아무거나 부르는 것으로는 의무가 되지 않는다. 그러지
   * 않으면 의무 집합이 질의 목이 필요한 패널까지 삼킨다(실측: 5 → 13).
   */
  it('어휘 목록 밖의 표시 함수는 의무를 만들지 않는다', () => {
    expect(componentsIn('otherModule.tsx')).toStrictEqual([]);
  });

  it('내보내지 않은 내부 컴포넌트는 세지 않는다 — 마운트 대상이 아니다', () => {
    expect(componentsIn('inner.tsx')).toStrictEqual([]);
  });
});

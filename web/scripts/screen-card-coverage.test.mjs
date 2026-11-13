import { existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { describe, expect, it } from 'vitest';

import {
  collectScreenVocabulary,
  INTERNAL_ASSEMBLER,
  missingVocabularyDeclarations,
  mountedCardsOf,
  OTHER_AXIS,
  screenFilesIn,
  screenProgram,
  vocabularyComponentsOf,
  vocabularyRegistryGaps,
  VOCABULARY_DECLARATIONS,
  VOCABULARY_MODULE_EXCLUSIONS,
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
   * **이 라운드의 거동 변화**(M11/11E) — 간접 렌더를 따라가면 `InboxScreen` 이 비로소 이 바구니에
   * 든다. 앞 판에서 그 파일은 의무도 화면도 아니었다(비공개 행 컴포넌트가 어휘를 불렀고 내보낸
   * 겉은 부르지 않았다) — 즉 이 게이트가 그 파일에 대해 **아무 말도 하지 않았다**. 합성 원문
   * 대조만 두면 실제 트리에서 폐포가 꺼져도 조용하다.
   */
  it('간접 렌더로 어휘를 그리는 화면이 바구니에 든다', () => {
    expect(screens.map((one) => one.name)).toContain('InboxScreen');
  });

  /**
   * 화면(`*Screen`)은 질의 목이 필요해 이 게이트가 마운트하지 못한다. 그 제외를 **이름 규약
   * 하나에 기대지 않는다** — 뺀 이름마다 짝이 되는 test 파일이 실제로 있는지 센다.
   */
  it('의무에서 뺀 화면마다 자기 test 파일이 있다', () => {
    for (const { name, file } of screens) {
      // **그 파일의 디렉터리에서** 찾는다 — 훑기가 재귀가 된 뒤로 화면이 뿌리에만 있지 않다.
      const beside = resolve(screensDir, dirname(file));
      const found = ['tsx', 'ts'].some((ext) => existsSync(resolve(beside, `${name}.test.${ext}`)));

      expect(found, `${name} 을 의무에서 뺐는데 짝이 되는 test 파일이 없다`).toBe(true);
    }
  });
});

/**
 * **훑기의 범위를 실제 파일시스템으로 잰다**(PR #132 v M-3). 앞 판은 한 겹 평면에 `.tsx` 만
 * 이어서 경계 문장(`src/screens/**`)보다 좁았다 — 하위 디렉터리의 카드와 `.ts` 카드가 모집단
 * 밖이었다. 합성 원문이 아니라 **버릴 디렉터리**로 재는 이유는 그 술어가 파일시스템 자체이기
 * 때문이다.
 */
describe('화면 파일 훑기의 범위 (v M-3)', () => {
  it('하위 디렉터리와 `.ts` 를 담고, test 파일과 다른 확장자는 뺀다', () => {
    const root = mkdtempSync(join(tmpdir(), 'bidvector-screen-walk-'));
    try {
      mkdirSync(join(root, 'nested', 'deeper'), { recursive: true });
      writeFileSync(join(root, 'Flat.tsx'), 'export const a = 1;\n');
      writeFileSync(join(root, 'Plain.ts'), 'export const b = 2;\n');
      writeFileSync(join(root, 'Flat.test.tsx'), 'export const c = 3;\n');
      writeFileSync(join(root, 'Plain.test.ts'), 'export const d = 4;\n');
      writeFileSync(join(root, 'notes.md'), 'x\n');
      writeFileSync(join(root, 'nested', 'Nested.tsx'), 'export const e = 5;\n');
      writeFileSync(join(root, 'nested', 'deeper', 'Deep.ts'), 'export const f = 6;\n');

      expect(screenFilesIn(root)).toStrictEqual([
        'Flat.tsx',
        'Plain.ts',
        join('nested', 'Nested.tsx'),
        join('nested', 'deeper', 'Deep.ts'),
      ]);
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });

  /** 실제 화면 디렉터리에서도 비지 않는다 — 빈 훑기는 모든 등식을 공허하게 만든다. */
  it('실제 화면 디렉터리를 담는다', () => {
    expect(screenFilesIn(screensDir).length).toBe(files.length);
    expect(files).toContain('WorksheetTimeline.tsx');
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
  // **이름을 바꿔 재수출한다** — 같은 이름으로 재수출하면 호출 지점의 글자가 선언과 같아서
  // 앞 판의 술어도 그것을 집는다(측정이 둘을 가르지 못한다). 개명이 그 둘을 가른다.
  'reexport.ts': `
    export { instantDisplayOf as formatInstant } from '../../display/timeDisplay';
  `,
  'viaReexport.tsx': `
    import { formatInstant } from './reexport';
    export function ReexportCard({ at }: { readonly at: string }) {
      return <span>{formatInstant(at).text}</span>;
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
  // ── M11/11E — `OPEN-M10-SCREEN-COVERAGE-INDIRECT-RENDER` 의 잔여 여섯 ──────────
  // **두 겹**이다. 오늘 실재하는 유일한 간접 렌더가 이 꼴이라(행 → 행 묶음 → 화면) 한 겹
  // 따라가기로는 닫히지 않는다 — 그래서 폐포로 간다.
  'twoLayer.tsx': `
    import { instantDisplayOf } from '../../display/timeDisplay';
    function Leaf({ at }: { readonly at: string }) {
      return <span>{instantDisplayOf(at).text}</span>;
    }
    function Middle({ at }: { readonly at: string }) {
      return <Leaf at={at} />;
    }
    export function TwoLayerCard({ at }: { readonly at: string }) {
      return <Middle at={at} />;
    }
  `,
  // 초기값이 **호출**이라 앞 판의 「화살표 또는 함수 표현식」 조건을 지났다.
  'wrapped.tsx': `
    import { memo } from 'react';
    import { currencyDisplayOf } from '../../display/moneyDisplay';
    export const WrappedCard = memo(({ code }: { readonly code: string }) => (
      <span>{currencyDisplayOf(code).label}</span>
    ));
  `,
  // 선언에 `export` 수식자가 없다 — 앞 판은 수식자가 붙은 선언만 보았다. 의무로 나야 하는
  // 이름은 **내보낸 이름**이다(DOM 게이트가 마운트할 이름이 그것이다).
  'renamed.tsx': `
    import { instantDisplayOf } from '../../display/timeDisplay';
    function Hidden({ at }: { readonly at: string }) {
      return <span>{instantDisplayOf(at).text}</span>;
    }
    export { Hidden as RenamedCard };
  `,
  'classCard.tsx': `
    import { Component } from 'react';
    import { vatTreatmentDisplayOf } from '../../display/moneyDisplay';
    export class ClassCard extends Component<{ readonly vat: string }> {
      render() {
        return <span>{vatTreatmentDisplayOf(this.props.vat).label}</span>;
      }
    }
  `,
  // 호출식이 없다 — **넘긴다**. 앞 판은 해소할 호출이 없어 지나갔다.
  'passed.tsx': `
    import { instantDisplayOf } from '../../display/timeDisplay';
    export function PassingCard({ ats }: { readonly ats: readonly string[] }) {
      return <ul>{ats.map(instantDisplayOf).map((one) => <li key={one.text}>{one.text}</li>)}</ul>;
    }
  `,
  // 상호 재귀 — 폐포 계산이 **끝나야** 한다(트리를 서로 그리는 꼴은 React 에서 드물지 않다).
  'cycle.tsx': `
    import { instantDisplayOf } from '../../display/timeDisplay';
    function Even({ at, depth }: { readonly at: string; readonly depth: number }) {
      return depth <= 0 ? <span>{instantDisplayOf(at).text}</span> : <Odd at={at} depth={depth - 1} />;
    }
    function Odd({ at, depth }: { readonly at: string; readonly depth: number }) {
      return <Even at={at} depth={depth - 1} />;
    }
    export function CycleCard({ at }: { readonly at: string }) {
      return <Even at={at} depth={2} />;
    }
  `,
  // **음성 대조** — 비공개 컴포넌트를 지나 그리지만 그 속이 어휘를 쓰지 않는다.
  'innerPlain.tsx': `
    function PlainLeaf({ title }: { readonly title: string }) {
      return <span>{title}</span>;
    }
    export function PlainOuter({ title }: { readonly title: string }) {
      return <PlainLeaf title={title} />;
    }
  `,
  // ── r1 F2 — **기본 내보내기**. 선언이 `ExportAssignment` 라 자리가 없었고, 그래서 React 의
  // 표준 꼴(`export default memo(Card)`)이 의무를 하나도 내지 않았다. 「심볼 경유는 내보내기의
  // 꼴을 묻지 않는다」가 이 꼴에 대해 거짓이었다.
  'defaultMemo.tsx': `
    import { memo } from 'react';
    import { instantDisplayOf } from '../../display/timeDisplay';
    function DefaultInner({ at }: { readonly at: string }) {
      return <span>{instantDisplayOf(at).text}</span>;
    }
    export default memo(DefaultInner);
  `,
  'defaultAnonymous.tsx': `
    import { memo } from 'react';
    import { currencyDisplayOf } from '../../display/moneyDisplay';
    export default memo(function ({ code }: { readonly code: string }) {
      return <span>{currencyDisplayOf(code).label}</span>;
    });
  `,
  'defaultArrow.tsx': `
    import { vatTreatmentDisplayOf } from '../../display/moneyDisplay';
    export default ({ vat }: { readonly vat: string }) => <span>{vatTreatmentDisplayOf(vat).label}</span>;
  `,
  // 무명 **기본 함수 선언** — `memo` 로 감싸지 않은 꼴. 이름이 없어 자리가 나오지 않았다.
  'defaultAnonymousDeclaration.tsx': `
    import { amountBasisDisplayOf } from '../../display/moneyDisplay';
    export default function ({ basis }: { readonly basis: string }) {
      return <span>{amountBasisDisplayOf(basis).label}</span>;
    }
  `,
  // **음성 대조** — 기본 내보내기가 어휘를 쓰지 않으면 의무가 아니다.
  'defaultPlain.tsx': `
    import { memo } from 'react';
    export default memo(function ({ title }: { readonly title: string }) {
      return <span>{title}</span>;
    });
  `,
  // ── r1 F3 — 참조 축의 구멍 둘. **구조분해한 뒤 넘기기**와 **리터럴 원소 접근으로 넘기기**.
  // 구조분해한 뒤 **부르면** 앞 판도 잡았다 — 넘기는 쪽만 빠져 있었다.
  'destructuredPass.tsx': `
    import * as display from '../../display/timeDisplay';
    const { instantDisplayOf: fmt } = display;
    export function DestructuredPassCard({ ats }: { readonly ats: readonly string[] }) {
      return <ul>{ats.map(fmt).map((one) => <li key={one.text}>{one.text}</li>)}</ul>;
    }
  `,
  'elementAccessPass.tsx': `
    import * as display from '../../display/timeDisplay';
    export function ElementAccessPassCard({ ats }: { readonly ats: readonly string[] }) {
      return <ul>{ats.map(display['instantDisplayOf']).map((one) => <li key={one.text}>{one.text}</li>)}</ul>;
    }
  `,
  // **음성 대조** — 타입만 내보내는 자리는 의무를 내지 않는다(마운트할 것이 없다).
  'typeOnly.tsx': `
    import { instantDisplayOf } from '../../display/timeDisplay';
    function Unused({ at }: { readonly at: string }) {
      return <span>{instantDisplayOf(at).text}</span>;
    }
    export type UnusedProps = Parameters<typeof Unused>[0];
  `,
};

const probeOverlay = new Map(Object.entries(PROBES).map(([name, text]) => [probePath(name), text]));
const probeProgram = screenProgram(webRoot, probeOverlay);

/** 기본 내보내기의 의무 이름은 **파일로 한정**된다 — 그 자리를 test 쪽에서도 한 곳에서 만든다. */
const PROBE_PREFIX = 'screens/__probe__/';

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

  it('이름을 바꿔 재수출한 것을 부르는 꼴을 집는다', () => {
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

  /**
   * **의무는 내보낸 이름뿐이다** — 비공개 컴포넌트 자신은 마운트 대상이 아니므로 목록에 들지
   * 않는다. 바뀐 것은 「그것을 그리는 내보낸 겉이 의무가 되는가」이고, 그 답이 이제 예다.
   */
  it('비공개 컴포넌트를 지나 그리면 **내보낸 겉**이 의무다 — 속은 이름에 없다', () => {
    expect(componentsIn('inner.tsx')).toStrictEqual(['Outer']);
  });
});

/**
 * **M11/11E — `OPEN-M10-SCREEN-COVERAGE-INDIRECT-RENDER` 의 잔여 여섯.** 아래 다섯은 앞 판에서
 * 전부 **초록**이었다(그 파일이 의무를 하나도 내지 않았다). 닫는 축이 셋이다 — 내보낸 **심볼**
 * 에서 선언으로 가기 · 어휘에 닿는 길을 호출 ∪ **참조**로 보기 · 깊이를 파일 안 **전이 폐포**로.
 */
describe('간접 렌더와 그 잔여 (M11/11E)', () => {
  it('두 겹을 지나 그려도 내보낸 겉이 의무다 — 한 겹으로는 닫히지 않는 꼴이다', () => {
    expect(componentsIn('twoLayer.tsx')).toStrictEqual(['TwoLayerCard']);
  });

  it('감싸 내보낸 꼴을 집는다 — 초기값이 호출이어도', () => {
    expect(componentsIn('wrapped.tsx')).toStrictEqual(['WrappedCard']);
  });

  it('개명 재수출을 집고, 의무로 나는 이름은 **내보낸 이름**이다', () => {
    expect(componentsIn('renamed.tsx')).toStrictEqual(['RenamedCard']);
  });

  it('클래스 컴포넌트를 집는다', () => {
    expect(componentsIn('classCard.tsx')).toStrictEqual(['ClassCard']);
  });

  it('어휘 함수를 **넘기는** 꼴을 집는다 — 해소할 호출이 없어도', () => {
    expect(componentsIn('passed.tsx')).toStrictEqual(['PassingCard']);
  });

  it('상호 재귀가 있어도 폐포가 끝난다', () => {
    expect(componentsIn('cycle.tsx')).toStrictEqual(['CycleCard']);
  });

  /** **음성 대조 둘** — 폐포가 「지나 그리기」를 다 삼키지 않는다. */
  it('속이 어휘를 안 쓰면 겉도 의무가 아니다', () => {
    expect(componentsIn('innerPlain.tsx')).toStrictEqual([]);
  });

  it('타입만 내보내는 자리는 의무를 내지 않는다', () => {
    expect(componentsIn('typeOnly.tsx')).toStrictEqual([]);
  });
});

/**
 * **r1 수정 라운드 — verifier F2·F3.** 「심볼 경유는 꼴을 묻지 않는다」가 **기본 내보내기**에
 * 대해 거짓이었고(선언이 `ExportAssignment` 라 자리가 없었다), 참조 축에 구멍 둘이 남아 있었다.
 * 오늘 `src/` 에 기본 내보내기는 0 건이고 구조분해·원소 접근 전달도 0 건이라 닫아도 아무것도
 * 붉어지지 않는다.
 */
describe('기본 내보내기와 참조 축의 구멍 (r1 F2·F3)', () => {
  it('`export default memo(지역 컴포넌트)` 가 의무를 낸다', () => {
    expect(componentsIn('defaultMemo.tsx')).toStrictEqual([`${PROBE_PREFIX}defaultMemo.tsx#default`]);
  });

  it('이름 없는 함수를 감싼 기본 내보내기도 낸다', () => {
    expect(componentsIn('defaultAnonymous.tsx')).toStrictEqual([`${PROBE_PREFIX}defaultAnonymous.tsx#default`]);
  });

  it('화살표 기본 내보내기도 낸다', () => {
    expect(componentsIn('defaultArrow.tsx')).toStrictEqual([`${PROBE_PREFIX}defaultArrow.tsx#default`]);
  });

  it('무명 기본 **함수 선언**도 낸다 — 이름이 없어도 자리는 있다', () => {
    expect(componentsIn('defaultAnonymousDeclaration.tsx')).toStrictEqual([`${PROBE_PREFIX}defaultAnonymousDeclaration.tsx#default`]);
  });

  /**
   * **파일마다 다른 이름이어야 한다**(r1 v R2 · cr N-2). 의무·화면 바구니는 **이름으로** 중복을
   * 없애므로, 기본 내보내기가 전부 `'default'` 면 둘째 카드부터 집합에서 접혀 조용해진다.
   */
  it('기본 내보내기의 의무 이름은 파일마다 다르다', () => {
    const first = componentsIn('defaultMemo.tsx');
    const second = componentsIn('defaultArrow.tsx');

    expect(first).not.toStrictEqual(second);
    expect(new Set([...first, ...second]).size).toBe(2);
  });

  it('어휘를 안 쓰는 기본 내보내기는 의무가 아니다', () => {
    expect(componentsIn('defaultPlain.tsx')).toStrictEqual([]);
  });

  it('최상위에서 구조분해한 어휘를 **넘기는** 꼴을 집는다', () => {
    expect(componentsIn('destructuredPass.tsx')).toStrictEqual(['DestructuredPassCard']);
  });

  it('리터럴 원소 접근으로 **넘기는** 꼴을 집는다', () => {
    expect(componentsIn('elementAccessPass.tsx')).toStrictEqual(['ElementAccessPassCard']);
  });
});

/**
 * **등재 표의 전수 등식**(M11/11E — 잔여 「새 어휘 함수」). 어휘 모듈의 내보낸 함수가 등재도
 * 제외도 아니면 붉다. 양성 대조는 **합성 원문을 실제 어휘 모듈 위에 얹어** 세운다 — 세 구멍을
 * 한 프로그램에서 함께 내므로, 하나가 공허해지면 그 칸이 바로 드러난다.
 */
const vocabularyModulePath = resolve(webRoot, 'src', 'display', 'timeDisplay.ts');
const registryOverlay = new Map([
  [
    vocabularyModulePath,
    `${readFileSync(vocabularyModulePath, 'utf8').replace(
      'export function readClock',
      'function readClock',
    )}\nconst buildVocabulary = (prefix: string) => (at: string) => prefix + at;\nexport const curriedVocabularyOf = buildVocabulary('x');\nexport function brandNewVocabularyOf(at: string): string {\n  return at;\n}\n`,
  ],
  [
    resolve(screensDir, '__probe__', 'leak.tsx'),
    `
      import { amountTextOf } from '../../display/moneyDisplay';
      export function LeakCard({ won }: { readonly won: number }) {
        return <span>{amountTextOf(won, 'KRW')}</span>;
      }
    `,
  ],
]);

/**
 * **TS 프로그램을 칸 예산 안에서 세우지 않는다**(r1 cr F-3). 실측 1.2~2.1s 이고 칸 예산은
 * 5000ms 다 — 이 PR 이 서명 A 에서 지운 꼴과 같은 크기의 비용이다. 이 파일은 이미 적재 단계에서
 * 프로그램을 둘 세우므로 셋째를 그 옆에 둔다(「측정 아닌 준비를 예산 밖으로」).
 */
const registryProgram = screenProgram(webRoot, registryOverlay);

describe('어휘 등재의 전수 등식 (M11/11E)', () => {
  it('오늘의 트리에 구멍이 없다', () => {
    expect(vocabularyRegistryGaps(program, webRoot)).toStrictEqual({
      unclassified: [],
      stale: [],
      leaked: [],
    });
  });

  it('제외마다 종류와 사유가 있다 — 사유 없는 제외는 그냥 구멍이다', () => {
    expect(VOCABULARY_MODULE_EXCLUSIONS.length).toBeGreaterThan(0);
    for (const one of VOCABULARY_MODULE_EXCLUSIONS) {
      expect([INTERNAL_ASSEMBLER, OTHER_AXIS], `${one.key} 의 종류가 표 밖이다`).toContain(one.kind);
      expect(one.why.length, `${one.key} 에 사유가 없다`).toBeGreaterThan(10);
      expect(VOCABULARY_DECLARATIONS, `${one.key} 가 등재와 제외에 둘 다 있다`).not.toContain(one.key);
    }
  });

  /** **양성 대조 셋** — 구멍마다 하나씩, 같은 합성 프로그램에서. */
  it('새 어휘 함수 · 썩은 제외 · 화면으로 샌 조립자를 각각 낸다', () => {
    const gaps = vocabularyRegistryGaps(registryProgram, webRoot);

    // **커링도 들어온다**(r1 cr F-1) — 선언 꼴이 아니라 **타입의 호출 시그니처**로 판정하므로
    // `build('x')` 처럼 초기값이 호출인 꼴이 열거 밖으로 새지 않는다.
    expect(gaps.unclassified).toStrictEqual([
      'display/timeDisplay.ts#brandNewVocabularyOf',
      'display/timeDisplay.ts#curriedVocabularyOf',
    ]);
    expect(gaps.stale).toStrictEqual(['display/timeDisplay.ts#readClock']);
    expect(gaps.leaked).toStrictEqual([join('__probe__', 'leak.tsx')]);
  });
});

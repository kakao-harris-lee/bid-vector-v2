import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { describe, expect, it } from 'vitest';

import {
  CONDITIONAL_EXEMPTION,
  handlerBearingTags,
  INTERACTIVE_TAGS,
  scanTree,
  shippedSourceFiles,
  TAG_SCOPED_HANDLERS,
} from './jsx-handler-tags.mjs';

/**
 * M13/13B — 터치 타깃 게이트의 **모집단을 벗어나는 길**을 요소 쪽에서 닫는 축(설계 검토 우회 2 ·
 * 팀장 판정 2026-11-16).
 *
 * 착수 실측에서 이 위반은 **0** 이었다. 그래서 이 게이트는 오늘의 production 을 하나도 붉히지
 * 않고, 막는 것은 **앞으로 들어올** `<div onClick>` 이다.
 */
const webRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const srcDir = resolve(webRoot, 'src');

describe('비-상호작용 내장 태그의 포인터·키 핸들러 (M13/13B)', () => {
  const { files, findings } = scanTree(srcDir);

  it('훑기가 실제 소스를 읽었다 — 빈 훑기는 모든 등식을 공허하게 만든다', () => {
    expect(files.length).toBeGreaterThan(5);
    expect(files).toContain('components/Button.tsx');
    // 하위 디렉터리도 담는다 — 카드를 한 겹 더 넣어 의무를 피하지 못한다.
    expect(files.some((one) => one.includes('/'))).toBe(true);
  });

  /**
   * 한 자리를 **한 줄로** 낸다. 태그 유니온이 넓으면(React 의 `ElementType` 은 내장 태그 전수다)
   * 한 요소가 150 줄을 내 실패 문면이 읽히지 않는다 — 자리와 핸들러로 묶고 태그 수를 함께 적는다.
   * 묶는 것은 **문면뿐**이고 단언은 그대로 「빈 목록」이다.
   */
  it('출하 트리에 위반이 0 이다', () => {
    const byPlace = new Map();
    for (const one of findings) {
      const key = `${one.file}:${one.line} ${one.handler}`;
      const seen = byPlace.get(key);
      byPlace.set(key, { tag: seen?.tag ?? one.tag, count: (seen?.count ?? 0) + 1 });
    }

    expect(
      [...byPlace].map(([place, { tag, count }]) => `${place} <${tag}${count > 1 ? ` 외 ${count - 1}` : ''}>`),
      '포인터·키 핸들러는 내장 상호작용 요소나 공유 Button 이 든다',
    ).toStrictEqual([]);
  });

  /** test 파일은 출하되지 않으므로 훑기 밖이다 — 그 경계가 실제로 서는지 본다. */
  it('test 파일은 훑기에 들지 않는다', () => {
    expect(shippedSourceFiles(srcDir).filter((one) => one.includes('.test.'))).toStrictEqual([]);
  });
});

describe('양성 대조 — 심은 위반이 붉는다', () => {
  it('div 에 onClick 을 달면 걸린다', () => {
    const found = handlerBearingTags('export const X = () => <div onClick={run}>누르기</div>;\n');

    expect(found).toHaveLength(1);
    expect(found[0].tag).toBe('div');
    expect(found[0].handler).toBe('onClick');
  });

  /**
   * **`role="button"` 을 면제하지 않는다**(팀장 판정 조건 ①). 면제하면 공유 `Button` 을 쓰지
   * 않는 길이 그 한 속성으로 다시 열린다.
   */
  it('role="button" 을 단 div 도 걸린다', () => {
    const found = handlerBearingTags('export const X = () => <div role="button" tabIndex={0} onClick={run} />;\n');

    expect(found).toHaveLength(1);
  });

  it('span 의 onKeyDown 도 걸린다 — 누르는 자리는 포인터만이 아니다', () => {
    const found = handlerBearingTags('export const X = () => <span onKeyDown={run}>x</span>;\n');

    expect(found.map((one) => one.handler)).toStrictEqual(['onKeyDown']);
  });

  it('자기 닫는 태그와 한 요소의 핸들러 둘을 각각 센다', () => {
    const found = handlerBearingTags('export const X = () => <p onClick={a} onKeyUp={b} />;\n');

    expect(found).toHaveLength(2);
  });
});

describe('spread 와 조건부 면제 (cr M-4 · L-9)', () => {
  /**
   * **볼 수 없는 것을 통과시키지 않는다.** `<div {...handlers} />` 는 JSX 안에서 열리는 같은
   * 성질의 구멍인데 속성 이름이 식 뒤에 숨는다. 착수 실측에서 출하 트리의 내장 태그 spread 는
   * 0 이라 이 규칙에 비용이 없다.
   */
  it('비-상호작용 태그의 spread 는 그 자체로 위반이다', () => {
    const found = handlerBearingTags('export const X = () => <div {...handlers} />;\n');

    expect(found).toHaveLength(1);
    expect(found[0].handler).toBe('{...spread}');
  });

  it('핸들러를 담은 객체 리터럴 spread 도 같다', () => {
    expect(handlerBearingTags('export const X = () => <span {...{ onClick: run }} />;\n')).toHaveLength(1);
  });

  it('상호작용 요소의 spread 는 걸리지 않는다 — 그 요소는 애초에 닿는다', () => {
    expect(handlerBearingTags('export const X = () => <button {...rest}>x</button>;\n')).toStrictEqual([]);
  });

  /** `href` 없는 `<a onClick>` 은 키보드로 닿지 않는 버튼이다 — 면제가 조건부인 사유다. */
  it('href 없는 a 의 onClick 은 걸리고, 있으면 걸리지 않는다', () => {
    expect(handlerBearingTags('export const X = () => <a onClick={run}>x</a>;\n')).toHaveLength(1);
    expect(handlerBearingTags('export const X = () => <a href="/x" onClick={run}>x</a>;\n')).toStrictEqual([]);
  });

  /**
   * **`label` 은 면제에 없다**(팀장 판정) — `htmlFor` 가 있어도 누르는 자리는 그 label 이 아니라
   * 연결된 입력이고, 그 입력이 모집단 안이다.
   */
  it('label 의 onClick 은 htmlFor 가 있어도 걸린다', () => {
    expect(handlerBearingTags('export const X = () => <label onClick={run}>x</label>;\n')).toHaveLength(1);
    expect(
      handlerBearingTags('export const X = () => <label htmlFor="q" onClick={run}>x</label>;\n'),
    ).toHaveLength(1);
  });

  /** 조건부 면제의 속성이 **spread 뒤에 숨으면** 면제도 서지 않는다(같은 방향의 규칙). */
  it('spread 로 href 를 주는 a 는 면제되지 않는다', () => {
    expect(handlerBearingTags('export const X = () => <a {...link} onClick={run}>x</a>;\n').length).toBeGreaterThan(0);
  });

  it('조건부 면제 표가 비어 있지 않다 — 비면 이 축이 공허하다', () => {
    expect(Object.keys(CONDITIONAL_EXEMPTION)).toStrictEqual(['a']);
  });

  it('보조 버튼·맥락 메뉴 핸들러도 누르는 자리다', () => {
    expect(handlerBearingTags('export const X = () => <div onAuxClick={run} />;\n')).toHaveLength(1);
    expect(handlerBearingTags('export const X = () => <div onContextMenu={run} />;\n')).toHaveLength(1);
  });
});

/**
 * **verifier 가 실측한 우회 여섯**(H-2). 앞 판에서 전부 초록이었고, 이름을 더 적는 대신 술어를
 * 접두와 집합으로 뒤집어 한 번에 닫았다. 여섯이 각각 여기서 RED 다.
 */
describe('verifier 우회 여섯 (v H-2)', () => {
  it('① spread 로 핸들러를 넘긴다', () => {
    expect(handlerBearingTags('export const X = () => <div {...{ onClick }} />;\n')).toHaveLength(1);
  });

  it('② 캡처 단계 핸들러', () => {
    expect(handlerBearingTags('export const X = () => <div onClickCapture={run} />;\n')).toHaveLength(1);
    expect(handlerBearingTags('export const X = () => <div onPointerDownCapture={run} />;\n')).toHaveLength(1);
  });

  /** 같은 파일의 문자열 상수까지 따라간다 — 변수 태그로 내장 요소를 가리키는 꼴을 닫는다. */
  it('③ 변수에 담은 내장 태그', () => {
    const text = "const Tag = 'div';\nexport const X = () => <Tag onClick={run} />;\n";

    expect(handlerBearingTags(text)).toHaveLength(1);
    expect(handlerBearingTags(text)[0].tag).toBe('div');
  });

  it('④ label 의 onClick', () => {
    expect(handlerBearingTags('export const X = () => <label onClick={run}>x</label>;\n')).toHaveLength(1);
  });

  it('⑤ onAuxClick·onContextMenu', () => {
    expect(handlerBearingTags('export const X = () => <div onAuxClick={run} />;\n')).toHaveLength(1);
  });

  /**
   * ⑥ **props 를 펼치는 래퍼.** 앞 판의 알려진 제한은 「그 컴포넌트가 `src/**` 안이면 거기서 다시
   * 이 술어를 지난다」였는데, 그 안쪽이 `<div {...p}/>` 라 **아무 데서도 걸리지 않았다**(반증됨).
   * spread 자체가 위반이 되면서 래퍼의 **안쪽**에서 닫힌다.
   */
  it('⑥ props 를 펼치는 래퍼는 그 안쪽에서 걸린다', () => {
    const text = 'export function Div(p) {\n  return <div {...p} />;\n}\n';

    expect(handlerBearingTags(text)).toHaveLength(1);
    expect(handlerBearingTags(text)[0].handler).toBe('{...spread}');
  });
});

/**
 * **verifier H-4 의 네 우회.** 태그 판정이 초기화식의 **글자 꼴**에 걸려 있어 넷이 전부 샜다.
 * 이제 검사기에게 태그 식의 **타입**을 묻는다 — 문자열 리터럴(유니온)이면 그 글자들이 태그다.
 */
/**
 * React 의 다형 태그 타입을 **곁 모듈로** 세운다. 가벼운 프로그램에는 `@types/react` 가 없고,
 * 한 파일 안에서 적으면 그 꼴이 실제 들여오기를 거치는지 알 수 없다.
 */
const ELEMENT_TYPE_MODULE = {
  react: [
    'export type ComponentType<P> = (props: P) => null;',
    "export type ElementType = 'div' | 'span' | 'button' | ComponentType<unknown>;",
    '',
  ].join('\n'),
};

describe('변수 태그의 타입 판정 (v H-4)', () => {
  it('① `as const` 로 좁힌 문자열', () => {
    const text = "const Tag = 'div' as const;\nexport const X = () => <Tag onClick={run} />;\n";

    expect(handlerBearingTags(text)).toHaveLength(1);
    expect(handlerBearingTags(text)[0].tag).toBe('div');
  });

  /** 유니온은 **구성원 하나라도** 허용 집합 밖이면 위반이다. */
  it('② 리터럴 유니온 삼항 — 구성원마다 센다', () => {
    const text = "const Tag: 'div' | 'span' = cond ? 'span' : 'div';\nexport const X = () => <Tag onClick={run} />;\n";

    expect(handlerBearingTags(text).map((one) => one.tag).sort()).toStrictEqual(['div', 'span']);
  });

  /** 검사기는 **스코프**를 본다 — 같은 파일의 이름 가림이 더 이상 Map 의 마지막 값에 걸리지 않는다. */
  it('③ 안쪽 스코프가 바깥 이름을 가린다', () => {
    const text = "const Tag = 'button';\nexport function X() {\n  const Tag = 'div';\n  return <Tag onClick={run} />;\n}\n";

    expect(handlerBearingTags(text)).toHaveLength(1);
    expect(handlerBearingTags(text)[0].tag).toBe('div');
  });

  /** 이 저장소의 `Card` 가 이미 쓰는 꼴이다 — 13C~13E 가 악의 없이 쓸 수 있는 모양. */
  it('④ 다형 `as` prop 의 안쪽', () => {
    const text = "function Poly({ as: Tag, ...rest }: { as: 'div' | 'span' }) {\n  return <Tag {...rest} />;\n}\n";

    expect(handlerBearingTags(text).length).toBeGreaterThan(0);
    expect(handlerBearingTags(text).every((one) => one.handler === '{...spread}')).toBe(true);
  });

  /**
   * **섞인 유니온**(cr P-1). React 의 다형 표준 타입 `ElementType` 이 정확히 이 꼴이다 — 내장
   * 태그 문자열 리터럴들 ∪ 컴포넌트. 앞 판은 컴포넌트 구성원을 처음 만나는 순간 **모아 둔
   * 리터럴을 통째로 버려서**, 선언한 「유니온은 하나라도 밖이면 위반」이 섞인 유니온에서 서지
   * 않았다(아무것도 위반이 아니었다).
   */
  it('⑤ 리터럴과 컴포넌트가 섞인 유니온 — 리터럴 구성원을 버리지 않는다', () => {
    const text =
      'type Elementish = "div" | "button" | ((p: { onClick?: () => void }) => null);\nexport function Box({ as: Tag }: { as: Elementish }) {\n  return <Tag onClick={run} />;\n}\n';

    expect(handlerBearingTags(text).map((one) => one.tag)).toStrictEqual(['div']);
  });

  it('음성 — 섞인 유니온의 리터럴이 전부 허용이면 걸리지 않는다', () => {
    const text =
      'type Elementish = "button" | "select" | ((p: unknown) => null);\nexport function Box({ as: Tag }: { as: Elementish }) {\n  return <Tag onClick={run} />;\n}\n';

    expect(handlerBearingTags(text)).toStrictEqual([]);
  });

  /** 템플릿 리터럴 타입은 **어느 태그인지 열거할 수 없다** — 보수적으로 증명 불가다(cr P-1). */
  it('⑥ 템플릿 리터럴 타입 태그는 증명 불가로 센다', () => {
    const found = handlerBearingTags(
      'export function X({ as: Tag }: { as: `h${number}` }) {\n  return <Tag onClick={run} />;\n}\n',
    );

    expect(found).toHaveLength(1);
    expect(found[0].tag).toContain('알 수 없는 태그');
  });

  /**
   * **verifier 가 실측한 세 꼴**(H-5). 위 ⑤ 와 같은 계열이지만 **그 꼴 자체를** 둔다 — 셋 다
   * `return []` 로 빠져 게이트 초록 · tsc 0 · lint 0 이었다. React 의 다형 `as` prop 이 쓰는
   * 관용 타입이고 13C~13E 가 악의 없이 고를 모양이다.
   */
  it('⑦ as?: ElementType 다형 prop — 기본값까지 달린 꼴', () => {
    const text = [
      `import type { ElementType } from 'react';`,
      `export function Box({ as: Tag = 'div', run }: { as?: ElementType; run: () => void }) {`,
      '  return <Tag onClick={run} />;',
      '}',
      '',
    ].join('\n');

    expect(handlerBearingTags(text, ELEMENT_TYPE_MODULE).map((one) => one.tag).sort()).toStrictEqual(['div', 'span']);
  });

  it('⑧ 삼항이 리터럴과 컴포넌트를 섞는다', () => {
    const text = [
      'function Real({ onClick }: { onClick: () => void }) {',
      '  return <button type="button" onClick={onClick} />;',
      '}',
      'export function Box({ c, run }: { c: boolean; run: () => void }) {',
      "  const Tag = c ? 'div' : Real;",
      '  return <Tag onClick={run} />;',
      '}',
      '',
    ].join('\n');

    expect(handlerBearingTags(text).map((one) => one.tag)).toStrictEqual(['div']);
  });

  it("⑨ 'div' | ComponentType 유니온", () => {
    const text = [
      `import type { ComponentType } from 'react';`,
      `export function Box({ as: Tag, run }: { as: 'div' | ComponentType<{ onClick: () => void }>; run: () => void }) {`,
      '  return <Tag onClick={run} />;',
      '}',
      '',
    ].join('\n');

    expect(handlerBearingTags(text, ELEMENT_TYPE_MODULE).map((one) => one.tag)).toStrictEqual(['div']);
  });

  /**
   * **타입 매개변수는 제약으로 푼다**(H-5 후단). 오늘은 tsc 가 이 자리를 막지만(핸들러를 실으면
   * TS2322), 게이트가 **컴파일러의 우연에 기대지 않게** 스스로 푼다.
   */
  it('⑩ 제약 있는 타입 매개변수 태그', () => {
    const text = [
      "export function Box<T extends 'div' | 'span'>({ as: Tag, run }: { as: T; run: () => void }) {",
      '  return <Tag onClick={run} />;',
      '}',
      '',
    ].join('\n');

    expect(handlerBearingTags(text).map((one) => one.tag).sort()).toStrictEqual(['div', 'span']);
  });

  it('음성 — 제약이 전부 허용 태그면 걸리지 않는다', () => {
    const text = [
      "export function Box<T extends 'button' | 'select'>({ as: Tag, run }: { as: T; run: () => void }) {",
      '  return <Tag onClick={run} />;',
      '}',
      '',
    ].join('\n');

    expect(handlerBearingTags(text)).toStrictEqual([]);
  });

  /** 제약이 **없는** 타입 매개변수는 `'div'` 일 수 있다 — 증명 불가다. */
  it('제약 없는 타입 매개변수는 보수적으로 걸린다', () => {
    const text = [
      'export function Box<T>({ as: Tag, run }: { as: T; run: () => void }) {',
      '  return <Tag onClick={run} />;',
      '}',
      '',
    ].join('\n');

    expect(handlerBearingTags(text)).toHaveLength(1);
  });

  it('음성 — 진짜 컴포넌트는 걸리지 않는다', () => {
    const text = 'function Real() { return null; }\nexport const X = () => <Real onClick={run} />;\n';

    expect(handlerBearingTags(text)).toStrictEqual([]);
  });

  it('음성 — 리터럴이 허용 태그면 걸리지 않는다', () => {
    expect(
      handlerBearingTags("const Tag = 'button' as const;\nexport const X = () => <Tag onClick={run} />;\n"),
    ).toStrictEqual([]);
    expect(
      handlerBearingTags(
        "const Tag: 'button' | 'select' = cond ? 'button' : 'select';\nexport const X = () => <Tag onClick={run} />;\n",
      ),
    ).toStrictEqual([]);
  });

  /** 넓은 `string` 은 `'div'` 일 수 있다 — 증명할 수 없으면 **보수적으로** 센다. */
  it('넓은 문자열 타입은 보수적으로 위반이다', () => {
    const text = 'export function X({ as: Tag }: { as: string }) {\n  return <Tag onClick={run} />;\n}\n';

    expect(handlerBearingTags(text).length).toBeGreaterThan(0);
  });
});

/**
 * **JSX 속성 밖의 배선**(v M-6 · M-7). 요소에 누르는 핸들러를 다는 길이고 출하 트리 사용은 0 이다.
 *
 * 술어가 **호출 텍스트**에 걸려 있던 앞 판에서 verifier 가 여섯을 실측으로 통과시켰다. 이제 부른
 * 대상은 **심볼**(별칭 해소)로, 수신자는 **타입**(`Element` 계열인가)으로 본다 — 여섯이 각각
 * 여기서 RED 다.
 *
 * DOM 모양을 원문에 **선언해 둔다**: 가벼운 프로그램은 표준 라이브러리를 싣지 않으므로
 * `HTMLDivElement`·`Document` 가 없고, 그러면 수신자 축이 전부 「증명 불가」로 접혀 **음성 대조가
 * 공허해진다**(`document` 가 초록인지 재지 못한다).
 */
const DOM = [
  // M13/13C 수정 라운드 1 — 면제 앵커가 **기본 라이브러리 선언**에서 오므로 이 묶음도 그 자리에
  // 둔다(`no-default-lib` 지시가 그 자리를 만든다). 모듈 안에 같은 이름을 세우는 우회와 갈린다.
  '/// <reference no-default-lib="true"/>',
  'interface EventTarget { addEventListener(type: string, handler: () => void): void; }',
  'interface Node extends EventTarget {}',
  'interface Element extends Node { onclick: (() => void) | null; }',
  'interface HTMLElement extends Element {}',
  'interface HTMLDivElement extends HTMLElement {}',
  'interface Document extends Node {}',
  'interface Window extends EventTarget {}',
  'declare const document: Document;',
  'declare const window: Window;',
  'declare const el: HTMLDivElement;',
  'declare const f: () => void;',
].join('\n');

const REACT_MODULE = {
  react: 'export declare function cloneElement(element: unknown, props: unknown): unknown;\nexport declare function createElement(tag: unknown, props: unknown): unknown;\n',
};

describe('JSX 속성 밖의 배선 (v M-6 · M-7)', () => {
  it('맨 식별자 cloneElement 는 걸린다', () => {
    expect(handlerBearingTags('export const X = () => cloneElement(<div />, { onClick: f });\n')).toHaveLength(1);
  });

  /** ① 이름공간을 거친 호출 — 맨 식별자만 보던 앞 판이 그대로 지나갔다. */
  it('① React.cloneElement 도 걸린다', () => {
    const text = `import * as React from 'react';\nexport const X = () => React.cloneElement(<div />, { onClick: f });\n`;

    expect(handlerBearingTags(text, REACT_MODULE)).toHaveLength(1);
  });

  /** ② 들여오며 바꾼 이름 — **글자로는 보이지 않고** 심볼로만 보인다. */
  it('② import { cloneElement as clone } 도 걸린다', () => {
    const text = `import { cloneElement as clone } from 'react';\nexport const X = () => clone(<div />, { onClick: f });\n`;
    const found = handlerBearingTags(text, REACT_MODULE);

    // M13/13C — 이제 **참조**를 세므로 한 꼴에 여러 자리가 걸린다(들여오기 · 쓰는 자리).
    // 세는 것은 「몇 자리인가」가 아니라 「걸리는가」다.
    expect(found.length).toBeGreaterThan(0);
    expect(found.every((one) => one.tag === `<${'cloneElement'}>`)).toBe(true);
  });

  /**
   * **그 경계가 닫혔다**(M13/13C · 팀장 판정 2026-11-18). 13B 는 요소 생성 호출을 「JSX 가
   * 아니다」로 경계에 두었는데 기전이 같다 — 비-상호작용 태그에 누르는 배선을 props 로 넘긴다.
   * 그래서 **주장을 뒤집어** 같은 꼴을 그대로 둔다(test 를 지우면 이 자리가 다시 열릴 때
   * 아무것도 말하지 않는다).
   */
  it('createElement 별칭으로 비-상호작용 태그에 거는 것도 걸린다', () => {
    const text = `import { createElement as h } from 'react';\nexport const X = () => h('div', { onClick: f });\n`;

    expect(handlerBearingTags(text, REACT_MODULE)).toHaveLength(1);
  });

  it('요소에 직접 거는 addEventListener 는 걸린다', () => {
    expect(handlerBearingTags(`export const X = () => { el.addEventListener('click', f); };\n`, {}, DOM)).toHaveLength(
      1,
    );
  });

  /** ③ 대괄호 접근 — 멤버 이름이 글자 리터럴 뒤로 숨는다. */
  it("③ el['addEventListener'] 도 걸린다", () => {
    expect(
      handlerBearingTags(`export const X = () => { el['addEventListener']('click', f); };\n`, {}, DOM),
    ).toHaveLength(1);
  });

  /** ④ 묶어 두고 나중에 부르기 — 호출 자리에 없지만 같은 배선이다. */
  it('④ el.addEventListener.bind(el) 도 걸린다', () => {
    expect(
      handlerBearingTags(`export const X = () => { const on = el.addEventListener.bind(el); on('click', f); };\n`, {}, DOM),
    ).toHaveLength(1);
  });

  /** ⑤ 이름 가림 — 수신자를 `document` 라고 **부르기만** 한다. 타입은 가려지지 않는다. */
  it('⑤ document 라는 이름으로 가린 요소 수신자도 걸린다', () => {
    const text = `export const X = () => { const document = el; document.addEventListener('click', f); };\n`;

    expect(handlerBearingTags(text, {}, DOM)).toHaveLength(1);
  });

  /** ⑥ 인라인 핸들러 대입 — `addEventListener` 를 아예 지나간다. */
  it('⑥ el.onclick = f 도 걸린다', () => {
    const found = handlerBearingTags(`export const X = () => { el.onclick = f; };\n`, {}, DOM);

    expect(found).toHaveLength(1);
    expect(found[0].handler).toBe('onclick 대입');
  });

  /** `document`·`window` 수신자는 **요소가 아니다** — 누르는 자리를 만들지 않는다. */
  it('음성 — document·window 수신자는 걸리지 않는다', () => {
    expect(handlerBearingTags(`export const X = () => { document.addEventListener('x', f); };\n`, {}, DOM)).toStrictEqual(
      [],
    );
    expect(handlerBearingTags(`export const X = () => { window.addEventListener('x', f); };\n`, {}, DOM)).toStrictEqual(
      [],
    );
  });

  /** 타입이 서 있지 않으면 **증명 불가**라 보수적으로 센다 — 그 자리에서 열리면 가림이 다시 통한다. */
  it('타입 없는 수신자는 보수적으로 걸린다', () => {
    expect(handlerBearingTags("export const X = (e) => { e.addEventListener('click', f); };\n", {}, DOM)).toHaveLength(1);
  });
});

/**
 * **`form` 은 통째가 아니라 제출 계열만**(팀장 판정 2026-11-16). 허용 쪽 짝 목록은 HTML 이 정한
 * 폼 이벤트 둘로 고정이고 좁히는 방향이라 「열거를 늘리는」 꼴이 아니다.
 */
describe('form 의 짝 이벤트', () => {
  it('onSubmit·onReset 은 걸리지 않는다', () => {
    expect(handlerBearingTags('export const X = () => <form onSubmit={run} />;\n')).toStrictEqual([]);
    expect(handlerBearingTags('export const X = () => <form onReset={run} />;\n')).toStrictEqual([]);
  });

  /** `<form onClick>` 은 폼 영역 전체를 누르는 자리로 만드는 `<div onClick>` 과 같은 우회다. */
  it('그 밖의 핸들러와 spread 는 걸린다', () => {
    expect(handlerBearingTags('export const X = () => <form onClick={run} />;\n')).toHaveLength(1);
    expect(handlerBearingTags('export const X = () => <form {...p} />;\n')).toHaveLength(1);
  });

  it('짝 목록이 HTML 의 폼 이벤트 둘이다', () => {
    expect(TAG_SCOPED_HANDLERS.form).toStrictEqual(['onSubmit', 'onReset']);
  });
});

describe('음성 대조 — 걸리면 안 되는 것', () => {
  it('button 의 onClick 은 걸리지 않는다', () => {
    expect(handlerBearingTags('export const X = () => <button onClick={run}>x</button>;\n')).toStrictEqual([]);
  });

  it('summary 의 onClick 은 걸리지 않는다 — 누르는 내장 요소다', () => {
    expect(handlerBearingTags('export const X = () => <summary onClick={b}>x</summary>;\n')).toStrictEqual([]);
  });

  /**
   * 사용자 정의 컴포넌트는 이 축 밖이다 — 그 핸들러가 닿는 요소는 그 컴포넌트 안에서 센다.
   *
   * **선언을 함께 둔다**: 태그 판정이 이제 타입이라, 선언 없는 이름은 `any` 가 되어 「증명 불가」로
   * 센다(보수적). 출하 트리에서는 컴포넌트가 언제나 들여와 있어 함수 타입이고, 그 사실을 이
   * 대조가 그대로 흉내 낸다.
   */
  it('대문자 태그(사용자 정의 컴포넌트)는 걸리지 않는다', () => {
    const text = 'function Button(p: { onClick?: () => void }) { return null; }\nexport const X = () => <Button onClick={run}>x</Button>;\n';

    expect(handlerBearingTags(text)).toStrictEqual([]);
  });

  /**
   * **이 술어는 일부러 과차단한다**(팀장 판정). 누르지 않는 핸들러(`onScroll`·`onFocus` 등)도
   * 비-상호작용 태그 위에서는 걸린다 — 이름으로 가르면 `onClickCapture` 를 놓친 앞 판으로
   * 되돌아가기 때문이다. 과차단은 **좁히는 방향**이고 오늘 출하 트리 위반이 0 이라 비용이 없다.
   * 필요한 짝이 생기면 [TAG_SCOPED_HANDLERS] 에 태그별로 더하는 것이 그때의 결정이다.
   */
  it('누르지 않는 핸들러도 비-상호작용 태그 위에서는 걸린다 — 의도한 과차단이다', () => {
    const text = 'export const X = () => <div onFocus={a} onScroll={b} />;\n';

    expect(handlerBearingTags(text)).toHaveLength(2);
    // 상호작용 요소 위에서는 걸리지 않는다 — 과차단이 아무 데나 번지지 않는다.
    expect(handlerBearingTags('export const X = () => <input onChange={d} />;\n')).toStrictEqual([]);
  });

  it('허용 집합이 HTML 의 상호작용 요소다 — div·span 은 그 안에 없다', () => {
    expect(INTERACTIVE_TAGS.has('button')).toBe(true);
    expect(INTERACTIVE_TAGS.has('div')).toBe(false);
    expect(INTERACTIVE_TAGS.has('span')).toBe(false);
  });
});

/**
 * M13/13C — **속성 밖 배선을 참조와 수신자로 뒤집는다**(13B v M-7b · cr Q-2 · Q-3①).
 *
 * 앞 판의 두 자리가 열거였다: 부른 대상을 **호출 자리의 이름**으로 비교하고, 수신자를
 * 「`Element` 를 상속하면 위반」이라는 **허용 쪽 기본값**으로 봤다. 그래서 값으로 옮기거나
 * 타입을 넓히는 것만으로 빠져나갔다 — 아래 꼴 열셋을 착수 라운드에 게이트 자신의 입구로 쟀다.
 */
const WIRING_DOM = [
  '/// <reference no-default-lib="true"/>',
  'interface EventTarget { addEventListener(type: string, handler: () => void): void; }',
  'interface Node extends EventTarget {}',
  'interface Element extends Node { onclick: (() => void) | null; setAttribute(name: string, value: string): void; }',
  'interface HTMLElement extends Element {}',
  'interface HTMLDivElement extends HTMLElement {}',
  'interface Document extends Node {}',
  'interface Window extends EventTarget {}',
  'interface GlobalEventHandlers { onclick: (() => void) | null; }',
  'declare const document: Document;',
  'declare const window: Window;',
  'declare const el: HTMLDivElement;',
  'declare const f: () => void;',
  'declare const Object: { assign(target: unknown, source: unknown): unknown };',
].join('\n');

const WIRING_REACT = {
  react: 'export declare function cloneElement(element: unknown, props: unknown): unknown;\nexport declare function createElement(tag: unknown, props: unknown): unknown;\n',
};

describe('속성 밖 배선 — 부른 대상은 참조로 센다 (v M-7b · cr Q-3①)', () => {
  it.each([
    ['변수 재묶음', "import { cloneElement } from 'react';\nconst cl = cloneElement;\nexport const X = () => cl(<div />, { onClick: f });\n"],
    ['구조 분해', "import * as React from 'react';\nexport const X = () => { const { cloneElement: cl } = React; return cl(<div />, { onClick: f }); };\n"],
    ['call 로 부르기', "import { cloneElement } from 'react';\nexport const X = () => cloneElement.call(null, <div />, { onClick: f });\n"],
    ['bind 로 묶기', "import { cloneElement } from 'react';\nexport const X = () => cloneElement.bind(null);\n"],
  ])('%s 도 걸린다', (_label, body) => {
    expect(handlerBearingTags(`declare const f: () => void;\n${body}`, WIRING_REACT)).not.toStrictEqual([]);
  });

  /**
   * **글자가 한 번도 나타나지 않는 꼴** — 이 자리가 「심볼로 본다」와 「글자로 본다」를 가른다.
   * 다른 모듈이 이름을 바꿔 재수출하면 쓰는 쪽 원문에는 `cl` 밖에 없다. 변이(심볼 해소를 빼고
   * 글자만 비교)를 걸면 **이 test 만** 붉는다 — 나머지 꼴은 원문 어딘가에 그 글자가 남기 때문이다.
   */
  it('이름을 바꿔 재수출한 것을 다른 모듈에서 써도 걸린다', () => {
    const modules = {
      ...WIRING_REACT,
      './relay': "export { cloneElement as cl } from 'react';\n",
    };
    const text = "import { cl } from './relay';\ndeclare const f: () => void;\nexport const X = () => cl(<div />, { onClick: f });\n";

    expect(handlerBearingTags(text, modules)).not.toStrictEqual([]);
  });

  it('들여오는 자리 자체가 참조다 — 쓰지 않아도 걸린다', () => {
    expect(handlerBearingTags("import { cloneElement } from 'react';\nexport const X = 1;\n", WIRING_REACT)).not.toStrictEqual(
      [],
    );
  });
});

describe('속성 밖 배선 — 수신자는 증명된 문서·창만 면제한다 (v M-7b · cr Q-2)', () => {
  it.each([
    ['EventTarget 으로 올리기', "export const X = () => { const t: EventTarget = el; t.addEventListener('c', f); };"],
    ['Node 로 올리기', "export const X = () => { const t: Node = el; t.addEventListener('c', f); };"],
    ['타입 매개변수', 'export function X<T extends HTMLElement>(node: T) { node.addEventListener(\'c\', f); }'],
    ['GlobalEventHandlers 로 올리기', "export const X = () => { const g: GlobalEventHandlers = el; g.onclick = f; };"],
  ])('%s 도 걸린다', (_label, body) => {
    expect(handlerBearingTags(`${body}\n`, {}, WIRING_DOM)).not.toStrictEqual([]);
  });

  it('음성 — 문서·창 수신자는 그대로 면제다', () => {
    expect(handlerBearingTags(`export const X = () => { document.addEventListener('c', f); };\n`, {}, WIRING_DOM)).toStrictEqual(
      [],
    );
    expect(handlerBearingTags(`export const X = () => { window.addEventListener('c', f); };\n`, {}, WIRING_DOM)).toStrictEqual(
      [],
    );
  });
});

describe('속성 밖 배선 — 대입과 멤버 이름의 다른 꼴 (v M-7b)', () => {
  it.each([
    ['복합 대입', "export const X = () => { el.onclick ??= f; };"],
    ['객체 합치기', 'export const X = () => { Object.assign(el, { onclick: f }); };'],
    ['속성으로 쓰기', "export const X = () => { el.setAttribute('onclick', 'run()'); };"],
    ['변수 키로 부르기', "const m = 'addEventListener';\nexport const X = () => { el[m]('c', f); };"],
  ])('%s 도 걸린다', (_label, body) => {
    expect(handlerBearingTags(`${body}\n`, {}, WIRING_DOM)).not.toStrictEqual([]);
  });

  it('음성 — 문서 수신자의 같은 꼴은 면제다', () => {
    expect(
      handlerBearingTags(`const m = 'addEventListener';\nexport const X = () => { document[m]('c', f); };\n`, {}, WIRING_DOM),
    ).toStrictEqual([]);
  });

  it('음성 — on 으로 시작하지 않는 속성 쓰기는 이 축이 아니다', () => {
    expect(
      handlerBearingTags(`export const X = () => { el.setAttribute('data-x', '1'); };\n`, {}, WIRING_DOM),
    ).toStrictEqual([]);
  });

  it('음성 — 요소가 아닌 대상의 객체 합치기는 이 축이 아니다', () => {
    expect(
      handlerBearingTags(`const plain = { onclick: null as (() => void) | null };\nexport const X = () => { Object.assign(plain, { onclick: f }); };\n`, {}, WIRING_DOM),
    ).toStrictEqual([]);
  });
});

/**
 * M13/13C — **요소 생성 호출의 경계를 닫는다**(팀장 판정 2026-11-18). 13B 는 `createElement` 를
 * 「JSX 가 아니다」로 경계에 두었지만 기전이 같다 — 비-상호작용 태그에 누르는 배선을 props 로
 * 넘긴다. 차이는 **증명 가능성**이고 그래서 판정이 다르다: 요소 복제는 태그를 볼 수 없어 참조
 * 자체가 위반이고, 요소 생성은 **첫 인자가 태그**라 같은 타입 판정을 그대로 적용할 수 있다.
 */
describe('요소 생성 호출의 태그와 props (팀장 판정 2026-11-18)', () => {
  const createElementOf = (body) =>
    handlerBearingTags(`import { createElement } from 'react';\ndeclare const f: () => void;\n${body}`, WIRING_REACT);

  it('비-상호작용 태그에 누르는 props 를 넘기면 걸린다', () => {
    expect(createElementOf("export const X = () => createElement('div', { onClick: f });\n")).toHaveLength(1);
  });

  it('들여오며 바꾼 이름으로 불러도 걸린다 — 이름이 아니라 심볼이다', () => {
    expect(
      handlerBearingTags(
        `import { createElement as h } from 'react';\ndeclare const f: () => void;\nexport const X = () => h('span', { onClickCapture: f });\n`,
        WIRING_REACT,
      ),
    ).toHaveLength(1);
  });

  it('태그를 증명할 수 없으면 걸린다', () => {
    expect(
      createElementOf("declare const tag: string;\nexport const X = () => createElement(tag, { onClick: f });\n"),
    ).toHaveLength(1);
  });

  it('props 를 증명할 수 없으면 걸린다 — 볼 수 없는 것을 통과시키지 않는다', () => {
    expect(
      createElementOf('declare const props: Record<string, unknown>;\nexport const X = () => createElement(\'div\', props);\n'),
    ).toHaveLength(1);
  });

  it('props 에 펼치기가 있으면 걸린다', () => {
    expect(
      createElementOf("declare const rest: Record<string, unknown>;\nexport const X = () => createElement('div', { ...rest });\n"),
    ).toHaveLength(1);
  });

  it('음성 — 내장 상호작용 요소는 걸리지 않는다', () => {
    expect(createElementOf("export const X = () => createElement('button', { onClick: f });\n")).toStrictEqual([]);
  });

  it('음성 — 누르는 props 가 없으면 걸리지 않는다(오늘 출하 트리의 그 한 자리)', () => {
    expect(
      createElementOf(
        "declare const as: 'li' | 'div' | 'section' | 'article';\ndeclare const kids: unknown;\nexport const X = () => createElement(as, { 'data-testid': 'x', className: 'y' }, kids);\n",
      ),
    ).toStrictEqual([]);
  });

  it('음성 — props 자리가 null 이면 거는 것이 없다', () => {
    expect(createElementOf("export const X = () => createElement('div', null);\n")).toStrictEqual([]);
  });

  it('음성 — 사용자 컴포넌트는 이 축이 아니다', () => {
    expect(
      createElementOf('declare const Thing: () => unknown;\nexport const X = () => createElement(Thing, { onClick: f });\n'),
    ).toStrictEqual([]);
  });
});

/**
 * M13/13C — `EventTarget.prototype` 경유 호출은 **선언에 따라 결과가 갈렸다**(13B verifier 는
 * 초록, 내 탐침은 RED). 한쪽 선언에서만 붉으면 우회가 남으므로 **두 선언 모두**에서 센다.
 */
describe('prototype 경유 호출 — 선언 둘 모두에서 걸린다', () => {
  it('prototype 이 선언되지 않은 꼴', () => {
    expect(
      handlerBearingTags(`export const X = () => { EventTarget.prototype.addEventListener.call(el, 'c', f); };\n`, {}, WIRING_DOM),
    ).not.toStrictEqual([]);
  });

  it('prototype 이 EventTarget 으로 선언된 꼴', () => {
    const declared = [
      '/// <reference no-default-lib="true"/>',
      'interface EventTarget { addEventListener(type: string, handler: () => void): void; }',
      'interface Node extends EventTarget {}',
      'interface Element extends Node {}',
      'interface HTMLElement extends Element {}',
      'interface HTMLDivElement extends HTMLElement {}',
      'interface Document extends Node {}',
      'interface Window extends EventTarget {}',
      'declare const EventTarget: { prototype: EventTarget };',
      'declare const el: HTMLDivElement;',
      'declare const f: () => void;',
    ].join('\n');

    expect(
      handlerBearingTags("export const X = () => { EventTarget.prototype.addEventListener.call(el, 'c', f); };\n", {}, declared),
    ).not.toStrictEqual([]);
  });
});

/**
 * M13/13C 수정 라운드 1 — verifier H-2 · H-3 · M-1, code-review M-1 · M-2.
 *
 * 앵커는 **기본 라이브러리 선언**에서 온다. 그래서 합성 대조도 DOM 을 `lib` 로 받는다(세 번째
 * 인자) — 모듈 안에 같은 이름의 interface 를 세우는 우회(h06)와 **같은 글자**를 쓰면서도 판정이
 * 갈리는 것이 이 축의 요점이다.
 */
const DOM_LIB = [
  '/// <reference no-default-lib="true"/>',
  'interface EventTarget { addEventListener(type: string, handler: () => void): void; }',
  'interface Node extends EventTarget {}',
  'interface Element extends Node { onclick: (() => void) | null; setAttribute(name: string, value: string): void; setAttributeNS(ns: string | null, name: string, value: string): void; }',
  'interface HTMLElement extends Element {}',
  'interface HTMLDivElement extends HTMLElement {}',
  'interface Document extends Node {}',
  'interface Window extends EventTarget {}',
  'interface GlobalEventHandlers { onclick: (() => void) | null; }',
  'declare const document: Document;',
  'declare const window: Window;',
  'declare const el: HTMLDivElement;',
  'declare const f: () => void;',
  'declare const Object: { assign(a: unknown, b: unknown): unknown; defineProperty(a: unknown, k: string, d: unknown): unknown; defineProperties(a: unknown, d: unknown): unknown; setPrototypeOf(a: unknown, p: unknown): unknown; getOwnPropertyDescriptor(a: unknown, k: string): unknown };',
  'declare const Reflect: { apply(fn: unknown, self: unknown, args: unknown[]): unknown; get(a: unknown, k: string): unknown; set(a: unknown, k: string, v: unknown): boolean };',
  'interface Map<K, V> { get(key: K): V | undefined; set(key: K, value: V): this; }',
  'interface WeakMap<K extends object, V> { get(key: K): V | undefined; set(key: K, value: V): this; }',
].join('\n');

const RUNTIME_MODULES = {
  ...WIRING_REACT,
  'react/jsx-runtime': 'export declare function jsx(tag: unknown, props: unknown): unknown;\nexport declare function jsxs(tag: unknown, props: unknown): unknown;\n',
};

describe('H-2 면제 수신자는 기본 라이브러리 심볼이다', () => {
  it('모듈 안에 같은 이름의 interface 를 세워도 면제되지 않는다', () => {
    const text = [
      'interface Document extends HTMLElement { readonly marker?: never }',
      "export const X = () => { const d: Document = el; d.addEventListener('c', f); };",
    ].join('\n');

    expect(handlerBearingTags(text, {}, DOM_LIB)).not.toStrictEqual([]);
  });

  it('음성 — 라이브러리의 문서·창은 그대로 면제다', () => {
    expect(handlerBearingTags("export const X = () => { document.addEventListener('c', f); };\n", {}, DOM_LIB)).toStrictEqual([]);
    expect(handlerBearingTags("export const X = () => { window.addEventListener('c', f); };\n", {}, DOM_LIB)).toStrictEqual([]);
  });

  /** **앵커가 없으면 게이트가 공허하다** — 그 자리는 조용히 통과시키지 않고 던진다. */
  it('앵커를 찾지 못하면 던진다 (fail-closed · 비공허 단언)', () => {
    const libWithoutAnchors = '/// <reference no-default-lib="true"/>\ninterface Element { onclick: (() => void) | null }\ndeclare const el: Element;\ndeclare const f: () => void;\n';

    expect(() => handlerBearingTags("export const X = () => { el.addEventListener('c', f); };\n", {}, libWithoutAnchors)).toThrow(
      /앵커/u,
    );
  });
});

describe('H-3 요소 생성 함수는 참조로 센다', () => {
  it.each([
    ['값으로 옮긴 꼴', "import { createElement } from 'react';\nconst h = createElement;\nexport const X = () => h('div', { onClick: f });"],
    ['Reflect.apply', "import { createElement } from 'react';\nexport const X = () => Reflect.apply(createElement, null, ['div', { onClick: f }]);"],
    ['다른 로더 이름', "import { jsx } from 'react/jsx-runtime';\nexport const X = () => jsx('div', { onClick: f });"],
    ['jsxs', "import { jsxs } from 'react/jsx-runtime';\nexport const X = () => jsxs('div', { onClick: f });"],
  ])('%s 도 걸린다', (_label, body) => {
    expect(handlerBearingTags(`${body}\n`, RUNTIME_MODULES, DOM_LIB)).not.toStrictEqual([]);
  });

  /** cr M-1 — 조건부 면제가 생성 갈래에도 **닿아야** 한다(JSX 와 같은 기전이다). */
  it('음성 — href 를 함께 넘긴 a 는 걸리지 않는다', () => {
    const text = "import { createElement } from 'react';\nexport const X = () => createElement('a', { href: '/x', onClick: f });\n";

    expect(handlerBearingTags(text, RUNTIME_MODULES, DOM_LIB)).toStrictEqual([]);
  });

  it('음성 — 내장 상호작용 요소와 사용자 컴포넌트는 그대로다', () => {
    const button = "import { createElement } from 'react';\nexport const X = () => createElement('button', { onClick: f });\n";
    const custom =
      "import { createElement } from 'react';\ndeclare const Thing: () => unknown;\nexport const X = () => createElement(Thing, { onClick: f });\n";

    expect(handlerBearingTags(button, RUNTIME_MODULES, DOM_LIB)).toStrictEqual([]);
    expect(handlerBearingTags(custom, RUNTIME_MODULES, DOM_LIB)).toStrictEqual([]);
  });
});

describe('M-1 · M-2 반사·서술자·변수 꼴도 센다', () => {
  it.each([
    ['변수로 넘긴 객체 합치기', 'const bag = { onclick: f };\nexport const X = () => { Object.assign(el, bag); };'],
    ['Reflect.get 으로 멤버 꺼내기', "export const X = () => { Reflect.get(el, 'addEventListener'); };"],
    ['Reflect.set 으로 핸들러 걸기', "export const X = () => { Reflect.set(el, 'onclick', f); };"],
    ['서술자로 핸들러 걸기', "export const X = () => { Object.defineProperty(el, 'onclick', { value: f }); };"],
    ['이름공간 속성 쓰기', "export const X = () => { el.setAttributeNS(null, 'onclick', 'run()'); };"],
    ['조립한 속성 이름', "export const X = () => { el.setAttribute('on' + 'click', 'run()'); };"],
    ['계산 키 구조 분해', "export const X = () => { const { ['addEventListener']: add } = el; return add; };"],
  ])('%s 도 걸린다', (_label, body) => {
    expect(handlerBearingTags(`${body}\n`, {}, DOM_LIB)).not.toStrictEqual([]);
  });

  it('음성 — 요소가 아닌 대상과 on 이 아닌 속성은 그대로다', () => {
    const plain = 'const plain = { onclick: f };\nconst bag = { onclick: f };\nexport const X = () => { Object.assign(plain, bag); };\n';
    const dataAttribute = "export const X = () => { el.setAttribute('data-x', '1'); };\n";

    expect(handlerBearingTags(plain, {}, DOM_LIB)).toStrictEqual([]);
    expect(handlerBearingTags(dataAttribute, {}, DOM_LIB)).toStrictEqual([]);
  });
});

/** cr L-4 — 한정된 소문자 이름은 **컴포넌트**다. 글자로 접으면 거짓 양성이 된다. */
describe('이름공간 컴포넌트는 내장 태그가 아니다 (cr L-4)', () => {
  it('음성 — styles.Row 는 걸리지 않는다', () => {
    const text = 'declare const styles: { Row: () => unknown };\ndeclare const f: () => void;\nexport const X = () => <styles.Row onClick={f} />;\n';

    expect(handlerBearingTags(text)).toStrictEqual([]);
  });

  it('양성 대조 — 같은 꼴의 내장 태그는 그대로 걸린다', () => {
    expect(handlerBearingTags('declare const f: () => void;\nexport const X = () => <div onClick={f} />;\n')).toHaveLength(1);
  });
});

/**
 * M13/13C 수정 라운드 2 — verifier R1-H1 · R1-M1, code-review N-1 · N-3 · N-6.
 *
 * **이 축의 경계**(design-review (0) 재작성): 이 게이트가 막는 것은 **실수로 쓴 평범한 코드**이고,
 * 타입을 속이거나 글자를 조립해 **일부러 숨기는 꼴**은 경계 밖이다. 그래서 이번 라운드는 구조로
 * 닫을 수 있는 자리(증명 가능한 리터럴 키 · 해소되는 심볼)만 닫고 나머지는 등재한다.
 */
describe('요소 생성·복제를 글자 키로 부르는 길 (R1-H1)', () => {
  const REACT_NS = {
    ...WIRING_REACT,
    'react/jsx-runtime': 'export declare function jsx(tag: unknown, props: unknown): unknown;\n',
  };

  it('리터럴 키로 꺼낸 생성 함수도 참조로 센다', () => {
    const text =
      "import * as React from 'react';\ndeclare const f: () => void;\nexport const X = () => React['createElement']('div', { onClick: f });\n";

    expect(handlerBearingTags(text, REACT_NS, DOM_LIB)).not.toStrictEqual([]);
  });

  /**
   * **이 꼴이 새 갈래의 고유 몫이다**: 복제는 글자 리터럴 참조 축이 이미 받고(그 축이 문자열
   * 노드를 본다), 호출 자리의 생성은 판정 갈래가 받는다. 호출이 아닌 자리에서 생성 함수를
   * 글자 키로 꺼내는 이 꼴만 **새 갈래가 아니면 아무도 보지 않는다**.
   */
  it('값으로 옮긴 글자 키 생성 함수도 참조로 센다', () => {
    const text =
      "import * as React from 'react';\ndeclare const f: () => void;\nconst make = React['createElement'];\nexport const X = () => make('div', { onClick: f });\n";

    expect(handlerBearingTags(text, REACT_NS, DOM_LIB)).not.toStrictEqual([]);
  });

  it('리터럴 키로 꺼낸 복제 함수도 참조로 센다', () => {
    const text =
      "import * as React from 'react';\ndeclare const el: unknown;\ndeclare const f: () => void;\nexport const X = () => React['cloneElement'](el, { onClick: f });\n";

    expect(handlerBearingTags(text, REACT_NS, DOM_LIB)).not.toStrictEqual([]);
  });

  it('키를 증명할 수 없는 이름공간 접근은 증명 불가로 센다', () => {
    const text =
      "import * as React from 'react';\ndeclare const k: 'createElement';\ndeclare const f: () => void;\nexport const X = () => React[k]('div', { onClick: f });\n";

    expect(handlerBearingTags(text, REACT_NS, DOM_LIB)).not.toStrictEqual([]);
  });

  /** cr N-3 — 같은 호출이 **판정되면서 동시에 발화**하면 안 된다(오탐). */
  it('음성 — 이름공간으로 부른 정상 생성 호출은 걸리지 않는다', () => {
    const text =
      "import * as React from 'react';\ndeclare const f: () => void;\nexport const X = () => React.createElement('button', { onClick: f });\n";

    expect(handlerBearingTags(text, REACT_NS, DOM_LIB)).toStrictEqual([]);
  });
});

describe('합치는·반사 축은 해소된 심볼로 센다 (R1-M1 · cr N-1 · N-6)', () => {
  it.each([
    ['구조 분해한 합치기', 'const { assign } = Object;\nconst bag = { onclick: f };\nexport const X = () => { assign(el, bag); };'],
    ['별칭으로 옮긴 합치기', 'const merge = Object.assign;\nexport const X = () => { merge(el, { onclick: f }); };'],
    ['서술자 여럿', 'export const X = () => { Object.defineProperties(el, { onclick: { value: f } }); };'],
    ['프로토타입 바꿔치기', 'export const X = () => { Object.setPrototypeOf(el, { onclick: f }); };'],
  ])('%s 도 걸린다', (_label, body) => {
    expect(handlerBearingTags(`${body}\n`, {}, DOM_LIB)).not.toStrictEqual([]);
  });

  /**
   * **이 둘이 「수신자가 그 전역인가」를 재는 자리다**(변이 B-4 가 드러냈다). 인자 수 규칙만으로는
   * 이름 맞추기와 심볼 묶음이 **같은 값을 낸다** — 아래 둘은 멤버 이름이 축의 이름과 같고 인자
   * 수도 맞고 핸들러 키까지 싣는 평범한 코드이므로, 심볼로 묶지 않으면 걸린다.
   */
  it.each([
    [
      '요소를 키로 쓰는 핸들러 장부',
      "declare const handlers: Map<Element, { onclick: () => void }>;\nexport const X = () => { handlers.set(el, { onclick: f }); };",
    ],
    [
      '자기 이름이 같은 평범한 합치기',
      "declare const store: { assign(target: Element, source: { onclick: () => void }): void };\nexport const X = () => { store.assign(el, { onclick: f }); };",
    ],
  ])('음성 — %s 는 걸리지 않는다(수신자가 그 전역이 아니다)', (_label, body) => {
    expect(handlerBearingTags(`${body}\n`, {}, DOM_LIB)).toStrictEqual([]);
  });

  /** cr N-6 — 요소를 **키로 쓰는** 조회는 이 축이 아니다. 수신자가 `Object`·`Reflect` 여야 한다. */
  it.each([
    ['요소를 키로 쓰는 조회', "declare const cache: Map<Element, string>;\nexport const X = () => cache.get(el);"],
    ['요소를 키로 쓰는 기록', "declare const cache: Map<Element, string>;\nexport const X = () => { cache.set(el, 'x'); };"],
    ['약한 장부', "declare const refs: WeakMap<Element, number>;\nexport const X = () => refs.get(el);"],
  ])('음성 — %s 는 걸리지 않는다', (_label, body) => {
    expect(handlerBearingTags(`${body}\n`, {}, DOM_LIB)).toStrictEqual([]);
  });
});

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

  it('출하 트리에 위반이 0 이다', () => {
    expect(
      findings.map((one) => `${one.file}:${one.line} <${one.tag} ${one.handler}>`),
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

  /** 사용자 정의 컴포넌트는 이 축 밖이다(경계) — 그 핸들러가 닿는 요소는 그 파일에서 다시 센다. */
  it('대문자 태그(사용자 정의 컴포넌트)는 걸리지 않는다', () => {
    expect(handlerBearingTags('export const X = () => <Button onClick={run}>x</Button>;\n')).toStrictEqual([]);
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

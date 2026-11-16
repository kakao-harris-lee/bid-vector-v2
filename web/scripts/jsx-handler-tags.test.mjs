import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { describe, expect, it } from 'vitest';

import { handlerBearingTags, INTERACTIVE_TAGS, scanTree, shippedSourceFiles } from './jsx-handler-tags.mjs';

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

describe('음성 대조 — 걸리면 안 되는 것', () => {
  it('button 의 onClick 은 걸리지 않는다', () => {
    expect(handlerBearingTags('export const X = () => <button onClick={run}>x</button>;\n')).toStrictEqual([]);
  });

  it('summary·details 의 onClick 은 걸리지 않는다', () => {
    const text = 'export const X = () => <details onClick={a}><summary onClick={b}>x</summary></details>;\n';

    expect(handlerBearingTags(text)).toStrictEqual([]);
  });

  /** 사용자 정의 컴포넌트는 이 축 밖이다(경계) — 그 핸들러가 닿는 요소는 그 파일에서 다시 센다. */
  it('대문자 태그(사용자 정의 컴포넌트)는 걸리지 않는다', () => {
    expect(handlerBearingTags('export const X = () => <Button onClick={run}>x</Button>;\n')).toStrictEqual([]);
  });

  it('누르는 자리를 만들지 않는 핸들러는 걸리지 않는다', () => {
    const text = 'export const X = () => <div onFocus={a} onBlur={b} onScroll={c} onChange={d} />;\n';

    expect(handlerBearingTags(text)).toStrictEqual([]);
  });

  it('허용 집합이 HTML 의 상호작용 요소다 — div·span 은 그 안에 없다', () => {
    expect(INTERACTIVE_TAGS.has('button')).toBe(true);
    expect(INTERACTIVE_TAGS.has('div')).toBe(false);
    expect(INTERACTIVE_TAGS.has('span')).toBe(false);
  });
});

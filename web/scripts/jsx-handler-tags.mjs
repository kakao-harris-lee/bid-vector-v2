// M13/13B — **포인터·키 핸들러를 든 비-상호작용 내장 태그가 0** 임을 재는 술어.
//
// 터치 타깃 게이트(390px 여정)의 모집단은 `button` · `a` · `[role="tab"]` · `[role="button"]`
// 전수다. 그 모집단을 벗어나는 길이 하나 있다 — 버튼을 `<div onClick>` 으로 적는 것이다. 그러면
// 손가락이 닿는 자리인데 게이트의 선택자에 걸리지 않고, 키보드로도 닿지 않으며, 보조기기는
// 그것을 조작 가능한 것으로 읽지 못한다.
//
// 그래서 **요소 쪽에서** 닫는다: 포인터·키 핸들러는 내장 상호작용 요소(또는 공유 `Button`)만
// 든다. `role="button"` 을 단 `div` 도 **면제하지 않는다**(팀장 판정 2026-11-16 조건 ①) — 그
// 면제를 두면 공유 버튼을 쓰지 않는 길이 다시 열린다.
//
// **경계**: 사용자 정의 컴포넌트(대문자 태그)에 넘긴 `onClick` 은 이 축이 보지 않는다. 그 핸들러가
// 어느 요소에 닿는지는 그 컴포넌트가 정하고, 그 컴포넌트가 `src/**` 안이면 거기서 다시 이 술어를
// 지난다. 저장소 밖 컴포넌트에 넘기는 꼴은 경계 밖이다(알려진 제한).
import { readdirSync, readFileSync } from 'node:fs';
import { join, resolve } from 'node:path';

import ts from 'typescript';

/**
 * 핸들러를 들어도 되는 **내장 상호작용 요소**. 열거지만 이것은 HTML 의 집합이고 저장소의
 * 집합이 아니다 — 늘지 않는다. 늘려야 할 일이 생기면 그것은 새 결정이다.
 */
export const INTERACTIVE_TAGS = new Set([
  'a',
  'button',
  'details',
  'form',
  'input',
  'label',
  'option',
  'select',
  'summary',
  'textarea',
]);

/**
 * 손가락·키로 누르는 핸들러. `onChange`·`onSubmit`·`onFocus` 는 **대상이 아니다** — 그것들은
 * 누르는 자리를 만들지 않는다(그리고 그 자리는 이미 내장 상호작용 요소다).
 */
const PRESS_HANDLER = /^on(Click|DblClick|DoubleClick|MouseDown|MouseUp|PointerDown|PointerUp|TouchStart|TouchEnd|KeyDown|KeyUp|KeyPress)$/u;

/** 소문자로 시작하는 태그 == 내장 요소. 대문자는 사용자 정의 컴포넌트다(위 「경계」). */
const isIntrinsic = (tag) => /^[a-z]/u.test(tag);

/**
 * 그 원문에서 **핸들러를 든 비-상호작용 내장 태그** 전수.
 *
 * 술어가 AST 인 이유는 저장소 규율이다 — 문자열 훑기는 주석 한 줄·문자열 리터럴·줄바꿈으로
 * 열린다(`onClick=` 를 속성과 산문이 같은 글자로 쓴다).
 */
export function handlerBearingTags(text, fileName = 'probe.tsx') {
  const source = ts.createSourceFile(fileName, text, ts.ScriptTarget.ESNext, true, ts.ScriptKind.TSX);
  const found = [];
  const visit = (node) => {
    if (ts.isJsxOpeningElement(node) || ts.isJsxSelfClosingElement(node)) {
      const tag = node.tagName.getText(source);
      if (isIntrinsic(tag) && !INTERACTIVE_TAGS.has(tag)) {
        for (const property of node.attributes.properties) {
          if (!ts.isJsxAttribute(property)) {
            continue;
          }
          const name = property.name.getText(source);
          if (PRESS_HANDLER.test(name)) {
            found.push({
              file: fileName,
              tag,
              handler: name,
              line: source.getLineAndCharacterOfPosition(property.getStart(source)).line + 1,
            });
          }
        }
      }
    }
    ts.forEachChild(node, visit);
  };
  visit(source);
  return found;
}

/** 소스 트리의 출하 파일 — 재귀이고 test 파일은 뺀다(출하되지 않는다). */
export function shippedSourceFiles(dir) {
  const found = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    if (entry.isDirectory()) {
      for (const nested of shippedSourceFiles(resolve(dir, entry.name))) {
        found.push(join(entry.name, nested));
      }
      continue;
    }
    if (!/\.tsx?$/u.test(entry.name) || /\.test\.tsx?$/u.test(entry.name)) {
      continue;
    }
    found.push(entry.name);
  }
  return found.sort();
}

/** 트리 전수 훑기 — 반환이 빈 배열이면 위반 0 이다. */
export function scanTree(dir) {
  const files = shippedSourceFiles(dir);
  if (files.length === 0) {
    // 빈 훑기는 **모든 등식을 공허하게** 만든다 — 조용히 초록이 되는 길을 두지 않는다.
    throw new Error(`훑기가 비었다: ${dir}`);
  }
  return {
    files,
    findings: files.flatMap((one) => handlerBearingTags(readFileSync(resolve(dir, one), 'utf8'), one)),
  };
}

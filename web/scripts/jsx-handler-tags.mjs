// M13/13B — 내장 태그에 붙은 **누르는 배선**이 상호작용 요소 밖에 서지 않는지 재는 술어.
//
// 터치 타깃 게이트(390px 여정)의 모집단은 선택자 전수다. 그 모집단을 벗어나는 길이 있다 —
// 버튼을 `<div onClick>` 으로 적는 것이다. 그러면 손가락이 닿는 자리인데 게이트의 선택자에
// 걸리지 않고, 키보드로도 닿지 않으며, 보조기기는 그것을 조작 가능한 것으로 읽지 못한다.
//
// **술어는 열거가 아니라 접두와 집합이다**(verifier H-2). 앞 판은 핸들러 **이름 목록**
// (`onClick|onKeyDown|…`)과 태그 목록의 곱이어서 `onClickCapture` · `onAuxClick` · spread ·
// `<label onClick>` · 변수 태그 · props 를 펼치는 래퍼가 **전부 샜다**(실측 여섯). 이름을 더
// 적는 대신 뒤집는다:
//
//   내장(소문자) 태그에 **`on` + 대문자로 시작하는 속성**이 있거나 **spread 속성**이 있으면,
//   그 태그가 허용 집합에 들지 않는 한 위반이다.
//
// 허용 쪽은 **HTML 이 정한 것**이라 저장소가 늘리는 목록이 아니고, 좁히는(과차단) 방향이다.
// 셋으로 나뉜다.
//  · [INTERACTIVE_TAGS] — 무엇을 달아도 되는 내장 상호작용 요소.
//  · [CONDITIONAL_EXEMPTION] — 그 속성이 **보일 때만** 상호작용 요소(`href` 없는 `<a>` 는
//    키보드로 닿지 않는 버튼이다). `label` 은 면제에 **없다** — 누르는 자리는 그 label 이
//    아니라 연결된 입력이다(팀장 판정).
//  · [TAG_SCOPED_HANDLERS] — 그 태그에서만 허용되는 **짝 이벤트**. `form` 의 `onSubmit`·
//    `onReset` 이 그것이고, 그 둘은 폼이 스스로 내는 이벤트라 누르는 자리를 만들지 않는다
//    (누르는 것은 폼 **안**의 제출 버튼이고 그 버튼은 이미 모집단 안이다). `form` 을 통째로
//    면제하지 않는 이유는 `<form onClick>` 이 폼 영역 전체를 누르는 자리로 만드는 `<div onClick>`
//    과 같은 우회이기 때문이다(팀장 판정 2026-11-16).
//
// **경계**: 사용자 정의 컴포넌트(대문자 태그)에 넘긴 핸들러와 `createElement` **호출 인자**로
// 넘긴 핸들러는 이 축 밖이다(알려진 제한). 변수 태그는 **같은 파일의 문자열 상수**까지 따라간다.
import { readdirSync, readFileSync } from 'node:fs';
import { join, resolve } from 'node:path';

import ts from 'typescript';

/** 무엇을 달아도 되는 내장 상호작용 요소. */
export const INTERACTIVE_TAGS = new Set(['button', 'input', 'select', 'textarea', 'summary']);

/** 그 속성이 **보일 때만** 상호작용 요소인 태그. spread 뒤에 숨으면 면제도 서지 않는다. */
export const CONDITIONAL_EXEMPTION = { a: 'href' };

/** 그 태그에서만 허용되는 짝 이벤트 — HTML 의 폼 이벤트 둘로 고정이다. */
export const TAG_SCOPED_HANDLERS = { form: ['onSubmit', 'onReset'] };

/**
 * 핸들러 속성의 **꼴**. 이름을 적지 않는다 — `onClickCapture`·`onAuxClick`·`onContextMenu` 가
 * 전부 이 하나에 든다.
 */
const HANDLER_ATTRIBUTE = /^on[A-Z]/u;

/** 소문자로 시작하는 태그 == 내장 요소. */
const isIntrinsicName = (tag) => /^[a-z]/u.test(tag);

/**
 * `const Tag = 'div'` 꼴의 **같은 파일 문자열 상수**. 변수 태그로 내장 요소를 가리키는 우회를
 * 닫는다(verifier 실측). 다른 파일에서 온 상수와 계산된 값은 경계 밖이다(알려진 제한).
 */
function intrinsicAliases(source) {
  const aliases = new Map();
  const visit = (node) => {
    if (
      ts.isVariableDeclaration(node) &&
      ts.isIdentifier(node.name) &&
      node.initializer !== undefined &&
      ts.isStringLiteralLike(node.initializer) &&
      isIntrinsicName(node.initializer.text)
    ) {
      aliases.set(node.name.text, node.initializer.text);
    }
    ts.forEachChild(node, visit);
  };
  visit(source);
  return aliases;
}

/**
 * 그 원문에서 **허용되지 않는 누르는 배선** 전수.
 *
 * 술어가 AST 인 이유는 저장소 규율이다 — 문자열 훑기는 주석 한 줄·문자열 리터럴로 열린다.
 */
export function handlerBearingTags(text, fileName = 'probe.tsx') {
  const source = ts.createSourceFile(fileName, text, ts.ScriptTarget.ESNext, true, ts.ScriptKind.TSX);
  const aliases = intrinsicAliases(source);
  const found = [];
  const visit = (node) => {
    if (ts.isJsxOpeningElement(node) || ts.isJsxSelfClosingElement(node)) {
      const written = node.tagName.getText(source);
      const tag = isIntrinsicName(written) ? written : (aliases.get(written) ?? written);
      if (isIntrinsicName(tag)) {
        const properties = node.attributes.properties;
        const names = properties.filter((one) => ts.isJsxAttribute(one)).map((one) => one.name.getText(source));
        const required = CONDITIONAL_EXEMPTION[tag];
        const fullyInteractive = INTERACTIVE_TAGS.has(tag) || (required !== undefined && names.includes(required));
        if (!fullyInteractive) {
          const allowed = new Set(TAG_SCOPED_HANDLERS[tag] ?? []);
          const at = (one) => source.getLineAndCharacterOfPosition(one.getStart(source)).line + 1;
          for (const property of properties) {
            if (ts.isJsxSpreadAttribute(property)) {
              // **볼 수 없는 것을 통과시키지 않는다** — 펼친 키가 식 뒤에 숨는다.
              found.push({ file: fileName, tag, handler: '{...spread}', line: at(property) });
              continue;
            }
            const name = property.name.getText(source);
            if (HANDLER_ATTRIBUTE.test(name) && !allowed.has(name)) {
              found.push({ file: fileName, tag, handler: name, line: at(property) });
            }
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

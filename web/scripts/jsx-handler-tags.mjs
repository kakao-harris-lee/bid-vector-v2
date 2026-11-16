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
// **태그 판정은 글자가 아니라 타입이다**(verifier H-4). 「같은 파일의 `const Tag = 'div'`」를
// 초기화식의 **리터럴 노드 꼴**로 따라가던 앞 판은 `'div' as const` · 리터럴 유니온 삼항 · 같은
// 파일의 이름 가림 · 다형 `as` prop 넷에 전부 열려 있었다(실측). 이제 TS 검사기에게 **태그 식의
// 타입**을 물어, 문자열 리터럴(또는 그 유니온)이면 그 글자들을 태그로 본다 — 유니온은 구성원
// **하나라도** 허용 집합 밖이면 위반이다. 타입이 넓은 `string`·`any` 면 어느 태그인지 증명할 수
// 없으므로 **보수적으로** 위반이다.
//
// **경계**: `createElement` **호출 인자**로 넘긴 핸들러는 이 축 밖이다(JSX 가 아니다).
import { readdirSync } from 'node:fs';
import { join, resolve } from 'node:path';

import ts from 'typescript';

import { screenProgram } from './screen-card-coverage.mjs';

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

const typeParts = (type) => (type.isUnion() ? type.types : [type]);

/**
 * 그 태그 식이 가리키는 **내장 태그 집합**.
 *
 *  · 소문자로 적힌 태그 — 글자 그대로.
 *  · 그 밖 — 검사기에게 타입을 묻는다. 전부 문자열 리터럴이면 그 글자들, 넓은 문자열이면
 *    `null`(증명 불가 → 부르는 쪽이 보수적으로 센다), 그 밖(함수·클래스 = 진짜 컴포넌트)이면
 *    빈 집합.
 */
function intrinsicTagsOf(checker, tagName, written) {
  if (isIntrinsicName(written)) {
    return [written];
  }
  const type = checker.getTypeAtLocation(tagName);
  const parts = typeParts(type);
  const tags = [];
  for (const part of parts) {
    if (part.isStringLiteral()) {
      tags.push(part.value);
      continue;
    }
    const flags = part.getFlags();
    if ((flags & ts.TypeFlags.String) !== 0 || (flags & ts.TypeFlags.Any) !== 0) {
      // 어느 태그인지 **증명할 수 없다** — 넓은 문자열은 `'div'` 일 수 있다.
      return null;
    }
    // 함수·클래스·객체 = 진짜 컴포넌트. 이 축의 대상이 아니다.
    return [];
  }
  return tags;
}

/**
 * 그 원문에서 **허용되지 않는 누르는 배선** 전수.
 *
 * 술어가 AST 인 이유는 저장소 규율이다 — 문자열 훑기는 주석 한 줄·문자열 리터럴로 열린다.
 */
/** 그 태그가 **무엇을 달아도 되는** 자리인가 — 속성이 보여야 서는 면제까지 함께 본다. */
function isFullyInteractive(tag, attributeNames) {
  if (INTERACTIVE_TAGS.has(tag)) {
    return true;
  }
  // `Object.prototype` 의 이름이 태그로 와도 **상속된 값을 집지 않는다**(cr N-7).
  const required = Object.hasOwn(CONDITIONAL_EXEMPTION, tag) ? CONDITIONAL_EXEMPTION[tag] : undefined;
  return required !== undefined && attributeNames.includes(required);
}

const scopedHandlers = (tag) =>
  new Set(Object.hasOwn(TAG_SCOPED_HANDLERS, tag) ? TAG_SCOPED_HANDLERS[tag] : []);

/**
 * **JSX 속성 밖의 배선**(verifier M-6). 둘 다 요소에 누르는 핸들러를 다는 길인데 JSX 속성을
 * 보는 술어가 닿지 못한다.
 *  · `cloneElement(<div/>, { onClick })` — 속성이 호출 인자로 간다. 출하 트리 사용 **0**.
 *  · `el.addEventListener('click', …)` — ref 콜백에서 요소에 직접 단다. 출하 트리에서 수신자가
 *    `document` 인 자리 하나뿐이고(셸의 Escape), 그것은 **요소가 아니라 문서**라 누르는 자리를
 *    만들지 않는다. 그래서 **요소 수신자**만 센다.
 */
function wiringOutsideJsx(source) {
  const found = [];
  const at = (node) => source.getLineAndCharacterOfPosition(node.getStart(source)).line + 1;
  const visit = (node) => {
    if (ts.isCallExpression(node)) {
      const callee = node.expression;
      if (ts.isIdentifier(callee) && callee.text === 'cloneElement') {
        found.push({ file: source.fileName, tag: '<cloneElement>', handler: 'props 인자', line: at(node) });
      } else if (
        ts.isPropertyAccessExpression(callee) &&
        callee.name.text === 'addEventListener' &&
        !(ts.isIdentifier(callee.expression) && (callee.expression.text === 'document' || callee.expression.text === 'window'))
      ) {
        found.push({
          file: source.fileName,
          tag: `<${callee.expression.getText(source)}>`,
          handler: 'addEventListener',
          line: at(node),
        });
      }
    }
    ts.forEachChild(node, visit);
  };
  visit(source);
  return found;
}

/** 한 소스 파일의 위반 — 검사기를 **받는다**(태그 판정이 타입이라서). */
export function handlerBearingTagsIn(program, source) {
  const checker = program.getTypeChecker();
  const fileName = source.fileName;
  const found = [];
  const visit = (node) => {
    if (ts.isJsxOpeningElement(node) || ts.isJsxSelfClosingElement(node)) {
      const written = node.tagName.getText(source);
      const tags = intrinsicTagsOf(checker, node.tagName, written);
      const properties = node.attributes.properties;
      const names = properties.filter((one) => ts.isJsxAttribute(one)).map((one) => one.name.getText(source));
      // `null` 은 「증명 불가」다 — 어느 태그인지 모르면 면제도 세울 수 없다.
      const unresolved = tags === null;
      const risky = unresolved
        ? [{ tag: `<알 수 없는 태그 ${written}>`, allowed: new Set() }]
        : tags
            .filter((one) => isIntrinsicName(one) && !isFullyInteractive(one, names))
            .map((one) => ({ tag: one, allowed: scopedHandlers(one) }));
      const at = (one) => source.getLineAndCharacterOfPosition(one.getStart(source)).line + 1;
      for (const { tag, allowed } of risky) {
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
    ts.forEachChild(node, visit);
  };
  visit(source);
  return [...found, ...wiringOutsideJsx(source)];
}

/**
 * 합성 원문 하나를 재는 입구(대조용). **가벼운 프로그램**을 세운다 — 실제 tsconfig 프로그램은
 * 대조마다 세우기에 비싸고, 리터럴 타입 추론에는 표준 라이브러리가 필요 없다.
 */
export function handlerBearingTags(text, fileName = '/probe.tsx') {
  const options = { jsx: ts.JsxEmit.Preserve, noLib: true, target: ts.ScriptTarget.ESNext, noResolve: true };
  const host = ts.createCompilerHost(options, true);
  const baseGetSourceFile = host.getSourceFile.bind(host);
  host.getSourceFile = (name, languageVersion, onError, shouldCreate) =>
    resolve(name) === resolve(fileName)
      ? ts.createSourceFile(name, text, languageVersion, true, ts.ScriptKind.TSX)
      : baseGetSourceFile(name, languageVersion, onError, shouldCreate);
  host.fileExists = (name) => resolve(name) === resolve(fileName) || ts.sys.fileExists(name);
  host.readFile = (name) => (resolve(name) === resolve(fileName) ? text : ts.sys.readFile(name));
  const program = ts.createProgram([fileName], options, host);
  const source = program.getSourceFile(fileName);
  if (source === undefined) {
    throw new Error(`합성 원문이 프로그램에 없다: ${fileName}`);
  }
  return handlerBearingTagsIn(program, source);
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
  // 실제 `tsconfig.json` 으로 연 프로그램 하나 — 태그 판정이 타입이라 검사기가 필요하다.
  // 그 자리를 **공유한다**(저장소에 이미 있는 프로그램 세우기 — 사본을 만들지 않는다).
  const program = screenProgram(resolve(dir, '..'));
  const findings = [];
  for (const one of files) {
    const source = program.getSourceFile(resolve(dir, one));
    if (source === undefined) {
      // 프로그램이 소스를 안 담으면 그 파일은 **미측정**이다 — 조용한 초록을 두지 않는다.
      throw new Error(`TS 프로그램에 소스 파일이 없다: ${one}`);
    }
    findings.push(...handlerBearingTagsIn(program, source).map((hit) => ({ ...hit, file: one })));
  }
  return { files, findings };
}

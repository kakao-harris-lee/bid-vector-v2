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
// **하나라도** 허용 집합 밖이면 위반이고, 컴포넌트 구성원이 섞여 있어도(React 의 `ElementType`
// 이 그 꼴이다) 리터럴 쪽을 버리지 않는다(cr P-1). 타입이 **열거할 수 없는 문자열**
// (넓은 `string`·`any`·템플릿 리터럴)이면 어느 태그인지 증명할 수 없으므로 **보수적으로** 위반이다.
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

/**
 * 증명할 수 없는 **문자열 꼴** — 어느 태그인지 열거할 수 없으면 보수적으로 위반이다. 넓은
 * `string`·`any` · 템플릿 리터럴 · 매핑(`Uppercase<T>`) · **제약 없는 타입 매개변수**가 든다.
 */
const UNPROVABLE_TAG_FLAGS =
  ts.TypeFlags.String |
  ts.TypeFlags.Any |
  ts.TypeFlags.TemplateLiteral |
  ts.TypeFlags.StringMapping |
  ts.TypeFlags.TypeParameter;

/** 제약을 따라가는 깊이 상한 — 순환 제약에서 멈추는 자리다. */
const CONSTRAINT_DEPTH = 8;

/**
 * 유니온을 펼치고 **타입 매개변수는 제약으로 푼다**(v H-5).
 *
 * `function X<T extends 'div' | 'span'>({ as: Tag }: { as: T })` 는 오늘 **tsc 가** 막는다(핸들러를
 * 실으면 TS2322). 그래도 여기서 푸는 이유는 게이트가 **컴파일러의 우연에 기대지 않기** 위해서다 —
 * 그 진단이 느슨해지는 날 이 축이 조용히 열린다. 제약이 없는 `T` 는 `'div'` 일 수 있으므로 위
 * 플래그에 걸려 「증명 불가」로 간다.
 */
function typeParts(checker, type, depth = 0) {
  if (type.isUnion()) {
    return type.types.flatMap((one) => typeParts(checker, one, depth));
  }
  if ((type.getFlags() & ts.TypeFlags.TypeParameter) !== 0 && depth < CONSTRAINT_DEPTH) {
    const constraint = checker.getBaseConstraintOfType(type);
    if (constraint !== undefined) {
      return typeParts(checker, constraint, depth + 1);
    }
  }
  return [type];
}

/**
 * 그 태그 식이 가리키는 **내장 태그 집합**.
 *
 *  · 소문자로 적힌 태그 — 글자 그대로.
 *  · 그 밖 — 검사기에게 타입을 묻는다. 문자열 리터럴 구성원은 그 글자들로 모으고, 열거할 수
 *    없는 문자열(넓은 `string`·`any`·템플릿 리터럴)이면 `null`(증명 불가 → 부르는 쪽이
 *    보수적으로 센다), 함수·클래스 = 진짜 컴포넌트인 구성원은 **건너뛴다**(이 축 밖이다).
 *
 * 그래서 **섞인 유니온**(리터럴 ∪ 컴포넌트 — React 의 `ElementType` 이 그 꼴이다)에서도 리터럴
 * 구성원이 그대로 살아, 「유니온은 하나라도 허용 밖이면 위반」이 선다(cr P-1).
 */
export function intrinsicTagsOf(checker, tagName, written) {
  if (isIntrinsicName(written)) {
    return [written];
  }
  const type = checker.getTypeAtLocation(tagName);
  const tags = [];
  for (const part of typeParts(checker, type)) {
    if (part.isStringLiteral()) {
      tags.push(part.value);
      continue;
    }
    if ((part.getFlags() & UNPROVABLE_TAG_FLAGS) !== 0) {
      // 어느 태그인지 **증명할 수 없다** — 넓은 문자열은 `'div'` 일 수 있다.
      return null;
    }
    // 함수·클래스·객체 = 진짜 컴포넌트. 이 축의 대상이 아니니 **건너뛴다** — 여기서 빠지면
    // 섞인 유니온(React 의 `ElementType` 꼴)에서 **모아 둔 리터럴이 함께 버려져**, 선언한
    // 「하나라도 밖이면 위반」이 서지 않는다(cr P-1).
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
 * **JSX 속성 밖의 배선**(verifier M-6 · M-7). 요소에 누르는 핸들러를 다는 길인데 JSX 속성을 보는
 * 술어가 닿지 못하는 자리들이다.
 *
 * **글자가 아니라 구조로 본다**(v M-7). 앞 판은 호출 **텍스트**에 걸려 있어 여섯이 전부 샜다
 * (실측): `React.cloneElement` · `cloneElement as clone` · `el['addEventListener']` ·
 * `el.addEventListener.bind(el)` · `const document = el` 로 이름을 가린 수신자 · `el.onclick =`.
 * 이름을 더 적는 대신 축을 둘로 옮겼다.
 *  · **부른 대상**은 검사기 심볼로 본다 — 별칭(`as clone`)과 이름공간(`React.`)이 함께 풀린다.
 *  · **수신자**는 타입이 `Element` 계열인지로 본다 — 이름을 어떻게 가려도 타입은 못 가린다.
 *    `document`·`window` 는 `Element` 를 상속하지 않으므로 그대로 빠진다(셸의 Escape 하나).
 *
 * `createElement` 별칭은 **선언된 경계**라 이 축에 넣지 않는다(JSX 가 아니다).
 */
const CLONE_ELEMENT = 'cloneElement';
const LISTENER_MEMBER = 'addEventListener';
/** 요소에 **직접 대입**하는 인라인 핸들러(`el.onclick = …`) — DOM 의 소문자 이름이다. */
const INLINE_HANDLER_MEMBER = /^on[a-z]+$/u;
/** 바탕 사슬에 이 이름이 있으면 **DOM 요소**다. `Document`·`Window` 에는 없다. */
const DOM_ELEMENT_ROOT = 'Element';
/** 바탕 사슬을 따라가는 깊이 상한. */
const BASE_CHAIN_DEPTH = 16;

/** 그 타입이 **요소**인가 — 바탕 사슬에 `Element` 가 있는지로 본다. */
function inheritsElement(type, depth = 0) {
  if (depth >= BASE_CHAIN_DEPTH) {
    return false;
  }
  const parts = type.isUnion() || type.isIntersection() ? type.types : [type];
  return parts.some((part) => {
    if (part.getSymbol()?.getName() === DOM_ELEMENT_ROOT) {
      return true;
    }
    return (part.getBaseTypes?.() ?? []).some((base) => inheritsElement(base, depth + 1));
  });
}

/**
 * 수신자가 무엇인가 — `element`(요소) · `unprovable`(타입이 없어 **증명 불가**) · `other`.
 * 증명 불가는 **보수적으로** 위반 쪽이다: 타입이 서 있지 않은 자리에서 이 축이 조용히 열리면
 * 「이름을 가리면 된다」가 다시 참이 된다.
 */
function receiverKind(checker, expression) {
  const type = checker.getNonNullableType(checker.getTypeAtLocation(expression));
  if ((type.getFlags() & (ts.TypeFlags.Any | ts.TypeFlags.Unknown)) !== 0) {
    return 'unprovable';
  }
  return inheritsElement(type) ? 'element' : 'other';
}

/** 그 접근이 가리키는 **멤버 이름** — 점 접근과 대괄호 접근(`el['addEventListener']`) 둘 다. */
function memberName(node) {
  if (ts.isPropertyAccessExpression(node)) {
    return node.name.text;
  }
  if (ts.isElementAccessExpression(node) && ts.isStringLiteralLike(node.argumentExpression)) {
    return node.argumentExpression.text;
  }
  return null;
}

/** 별칭이면 **가리키는 심볼**로 푼다. 풀 수 없으면 `undefined` — 부르는 쪽이 글자로 떨어진다. */
function aliasTarget(checker, symbol) {
  if ((symbol.getFlags() & ts.SymbolFlags.Alias) === 0) {
    return symbol;
  }
  try {
    const aliased = checker.getAliasedSymbol(symbol);
    return (aliased.getDeclarations()?.length ?? 0) > 0 ? aliased : undefined;
  } catch {
    return undefined;
  }
}

/** 부른 대상의 이름 — **심볼을 먼저** 묻고(별칭 해소), 풀리지 않으면 적힌 글자로 떨어진다. */
function calleeName(checker, callee) {
  const symbol = checker.getSymbolAtLocation(callee);
  const resolved = symbol === undefined ? undefined : aliasTarget(checker, symbol);
  return resolved?.getName() ?? memberName(callee) ?? (ts.isIdentifier(callee) ? callee.text : null);
}

function wiringOutsideJsx(checker, source) {
  const found = [];
  const at = (node) => source.getLineAndCharacterOfPosition(node.getStart(source)).line + 1;
  const hit = (node, tag, handler) => found.push({ file: source.fileName, tag, handler, line: at(node) });
  const visit = (node) => {
    if (ts.isCallExpression(node) && calleeName(checker, node.expression) === CLONE_ELEMENT) {
      hit(node, `<${CLONE_ELEMENT}>`, 'props 인자');
    }
    // **부르든 묶든** 센다 — `el.addEventListener.bind(el)` 은 호출 자리에 없지만 같은 배선이다.
    if (
      (ts.isPropertyAccessExpression(node) || ts.isElementAccessExpression(node)) &&
      memberName(node) === LISTENER_MEMBER
    ) {
      const kind = receiverKind(checker, node.expression);
      if (kind !== 'other') {
        hit(node, `<${kind === 'element' ? '요소' : '알 수 없는'} 수신자>`, LISTENER_MEMBER);
      }
    }
    if (ts.isBinaryExpression(node) && node.operatorToken.kind === ts.SyntaxKind.EqualsToken) {
      const name = memberName(node.left);
      if (name !== null && INLINE_HANDLER_MEMBER.test(name)) {
        const kind = receiverKind(checker, node.left.expression);
        if (kind !== 'other') {
          hit(node, `<${kind === 'element' ? '요소' : '알 수 없는'} 수신자>`, `${name} 대입`);
        }
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
  return [...found, ...wiringOutsideJsx(checker, source)];
}

const PROBE_FILE = '/probe.tsx';
const probeModulePath = (specifier) => resolve('/', `${specifier.replace(/[^\w.-]/gu, '_')}.d.ts`);

/**
 * 합성 원문 하나를 재는 입구(대조용). **가벼운 프로그램**을 세운다 — 실제 tsconfig 프로그램은
 * 대조마다 세우기에 비싸고, 리터럴 타입 추론에는 표준 라이브러리가 필요 없다.
 *
 * `modules` 로 **곁 모듈**을 줄 수 있다(v M-7). 들여오며 이름을 바꾼 꼴과 다른 파일의 상수 태그는
 * 한 파일 안에서 흉내 낼 수 없다 — 별칭 해소가 모듈 해소를 거치기 때문이다. 이 자리가 없으면 그
 * 둘을 **심어서만** 잴 수 있고, 심은 것은 측정이지 잠금이 아니다.
 */
export function handlerBearingTags(text, modules = {}) {
  const { program, source } = probeProgram(text, modules);
  return handlerBearingTagsIn(program, source);
}

/**
 * 합성 원문 하나로 **가벼운 프로그램**을 세운다 — 표준 라이브러리를 싣지 않고, `modules` 로 준
 * 곁 모듈만 푼다. 레이블 축도 태그 판정에 검사기가 필요해 같은 자리를 쓴다(사본을 만들지 않는다).
 */
export function probeProgram(text, modules = {}) {
  const sources = new Map([[resolve(PROBE_FILE), { text, kind: ts.ScriptKind.TSX }]]);
  for (const [specifier, body] of Object.entries(modules)) {
    sources.set(probeModulePath(specifier), { text: body, kind: ts.ScriptKind.TS });
  }
  const options = { jsx: ts.JsxEmit.Preserve, noLib: true, target: ts.ScriptTarget.ESNext };
  const host = ts.createCompilerHost(options, true);
  const baseGetSourceFile = host.getSourceFile.bind(host);
  host.getSourceFile = (name, languageVersion, onError, shouldCreate) => {
    const own = sources.get(resolve(name));
    return own === undefined
      ? baseGetSourceFile(name, languageVersion, onError, shouldCreate)
      : ts.createSourceFile(name, own.text, languageVersion, true, own.kind);
  };
  host.fileExists = (name) => sources.has(resolve(name)) || ts.sys.fileExists(name);
  host.readFile = (name) => sources.get(resolve(name))?.text ?? ts.sys.readFile(name);
  // 준 것만 푼다 — 그 밖의 들여오기는 **해소되지 않은 채** 둔다(실제 패키지를 끌어오지 않는다).
  host.resolveModuleNames = (names) =>
    names.map((one) =>
      sources.has(probeModulePath(one))
        ? { resolvedFileName: probeModulePath(one), extension: ts.Extension.Dts }
        : undefined,
    );
  const program = ts.createProgram([PROBE_FILE], options, host);
  const source = program.getSourceFile(PROBE_FILE);
  if (source === undefined) {
    throw new Error(`합성 원문이 프로그램에 없다: ${PROBE_FILE}`);
  }
  return { program, source };
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

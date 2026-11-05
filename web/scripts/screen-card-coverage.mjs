// 화면 카드의 **어휘 게이트 모집단**을 소스에서 유도한다(M9/screen-display r1 v M-1).
//
// DOM 게이트(`src/screens/screenVocabulary.test.tsx`)는 카드를 마운트해 「그려진 글자에 금액
// 토큰·ISO 원문이 0」을 잰다. 그 힘은 **모집단**에 달려 있다 — 손으로 적은 목록은 새 카드가
// 생길 때 조용히 뒤처지고, r1 에서 실제로 그렇게 됐다(화면 E 카드가 목록 밖이라 그 공백에
// 위반이 살아 있었다).
//
// 그래서 **의무 집합을 소스에서 센다**: `src/screens/**` 에서 내보낸 컴포넌트 가운데 표시 어휘
// 함수를 쓰는 것이 의무다. 그 집합과 게이트의 마운트 목록에 **등식**을 건다 — 새 카드는 등식을
// 깨고, 목록에서 카드를 빼는 것도 등식을 깬다.
//
// **화면(`*Screen`)은 의무에서 뺀다**: 질의 목이 필요해 이 게이트가 마운트하지 못하고, 각자의
// 화면 test 가 같은 주장을 든다. 그 제외는 **이름 규약 하나에 기대지 않는다** — 뺀 이름마다
// 짝이 되는 test 파일이 실제로 있는지 함께 센다.
import { readdirSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';

import ts from 'typescript';

/**
 * 표시 어휘를 화면에 내는 함수들. 이 가운데 하나라도 부르면 그 컴포넌트는 금액·시각 문면을
 * 그린다는 뜻이고, 따라서 「원문 0」 단언의 대상이다.
 */
export const VOCABULARY_CALLS = [
  'instantDisplayOf',
  'amountDisplayOf',
  'amountQualifierTextOf',
  'currencyDisplayOf',
  'amountBasisDisplayOf',
  'amountProvenanceDisplayOf',
  'vatTreatmentDisplayOf',
];

function sourceOf(path, text) {
  return ts.createSourceFile(path, text, ts.ScriptTarget.ESNext, true, ts.ScriptKind.TSX);
}

function isExported(node) {
  return (ts.getCombinedModifierFlags(node) & ts.ModifierFlags.Export) !== 0;
}

/** 그 노드 아래에서 **호출된** 이름들. 식별자 이름만 본다(멤버 호출은 여기 대상이 아니다). */
function calledNames(node) {
  const names = new Set();
  const walk = (child) => {
    if (ts.isCallExpression(child) && ts.isIdentifier(child.expression)) {
      names.add(child.expression.text);
    }
    ts.forEachChild(child, walk);
  };
  walk(node);
  return names;
}

/**
 * 한 파일에서 **내보낸 함수 선언** 가운데 표시 어휘를 쓰는 것의 이름.
 *
 * 화살표 함수를 `export const` 로 내보내는 꼴도 받는다 — 이 저장소의 화면 파일은 함수 선언을
 * 쓰지만, 규약 하나에 기대면 그 규약을 벗는 날 게이트가 조용해진다.
 */
export function vocabularyComponentsOf(text, path = 'screen.tsx') {
  const source = sourceOf(path, text);
  const found = [];
  const consider = (name, node) => {
    if (name === undefined) {
      return;
    }
    const called = calledNames(node);
    if (VOCABULARY_CALLS.some((one) => called.has(one))) {
      found.push(name);
    }
  };
  ts.forEachChild(source, (node) => {
    if (ts.isFunctionDeclaration(node) && isExported(node)) {
      consider(node.name?.text, node);
      return;
    }
    if (ts.isVariableStatement(node) && isExported(node)) {
      for (const declaration of node.declarationList.declarations) {
        if (
          ts.isIdentifier(declaration.name) &&
          declaration.initializer !== undefined &&
          (ts.isArrowFunction(declaration.initializer) || ts.isFunctionExpression(declaration.initializer))
        ) {
          consider(declaration.name.text, declaration);
        }
      }
    }
  });
  return found;
}

/** 게이트가 마운트한다고 **선언한** 목록을 AST 로 읽는다(문자열 grep 은 주석 한 줄로 열린다). */
export function mountedCardsOf(text, path = 'screenVocabulary.test.tsx') {
  const source = sourceOf(path, text);
  let names = null;
  ts.forEachChild(source, (node) => {
    if (!ts.isVariableStatement(node) || !isExported(node)) {
      return;
    }
    for (const declaration of node.declarationList.declarations) {
      if (
        ts.isIdentifier(declaration.name) &&
        declaration.name.text === 'GATE_MOUNTED_CARDS' &&
        declaration.initializer !== undefined &&
        ts.isArrayLiteralExpression(declaration.initializer)
      ) {
        names = declaration.initializer.elements.map((one) =>
          ts.isStringLiteral(one) ? one.text : `<리터럴이 아님: ${one.getText()}>`,
        );
      }
    }
  });
  return names;
}

/** 화면 디렉터리를 훑어 의무 집합과 화면 집합을 가른다. */
export function collectScreenVocabulary(screensDir) {
  const obligations = [];
  const screens = [];
  const files = readdirSync(screensDir).filter((one) => one.endsWith('.tsx') && !one.endsWith('.test.tsx'));
  for (const file of files.sort()) {
    const text = readFileSync(resolve(screensDir, file), 'utf8');
    for (const name of vocabularyComponentsOf(text, file)) {
      (name.endsWith('Screen') ? screens : obligations).push({ name, file });
    }
  }
  return { obligations, screens, files };
}

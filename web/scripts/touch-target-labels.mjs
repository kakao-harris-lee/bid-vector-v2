// M13/13B — **체크박스·라디오의 누르는 자리**가 44px 를 드는지 원문에서 재는 술어(v L-5).
//
// 터치 타깃 게이트(390px 여정)는 이 컨트롤들의 **감싼 레이블 상자**를 잰다(cr P-3). 그 측정이
// 닿는 것은 fixture 가 그리는 자리뿐이고, 입찰함의 라디오는 **결과 기록 폼을 열어야** 서므로
// 그 fixture 에서 한 번도 그려지지 않는다 — 되돌려도 어느 게이트도 붉지 않았다(verifier 실측).
//
// 그래서 **원문에서** 같은 규칙을 잰다. 상자가 아니라 구조라서 약하지만, 약한 자리는 e2e 가
// 닿지 못하는 **그 한 자리뿐**이다: 나머지 둘은 여기서도 재고 e2e 에서 상자로도 잰다. 둘이 함께
// 서면 「규칙을 적어 둔 자리」와 「그 규칙이 실제 화소로 서는 자리」가 짝을 이룬다.
//
// 규칙은 둘이다.
//  ① 체크박스·라디오는 **레이블이 감싼다**(암묵 연결) — 감싸지 않으면 누르는 자리가 13px 컨트롤
//     자신이 되고, 터치 타깃 게이트의 대입도 그 전제 위에 서 있다.
//  ② 그 레이블이 **최소 높이**를 든다.
//
// `MIN_TOUCH_CLASS` 가 §6.5 의 44px 를 Tailwind 어휘로 적은 **한 자리**다. 세 화면이 각자 적지
// 않고 이 술어가 그것을 든다 — 값이 바뀌면 여기만 고친다.
import { resolve } from 'node:path';

import ts from 'typescript';

import { shippedSourceFiles } from './jsx-handler-tags.mjs';
import { screenProgram } from './screen-card-coverage.mjs';

/** §6.5 의 44px(= `2.75rem`)를 레이블이 드는 Tailwind 어휘. */
export const MIN_TOUCH_CLASS = 'min-h-11';

/** 암묵 레이블을 요구하는 컨트롤 — 손가락이 누르는 자리가 컨트롤 자신보다 큰 종류다. */
export const WRAPPED_CONTROL_TYPES = new Set(['checkbox', 'radio']);

/** 그 여는 태그의 속성 값이 **글자 그대로** 무엇인가 — 식이면 `null`(증명 불가). */
function literalAttribute(element, name) {
  for (const property of element.attributes.properties) {
    if (!ts.isJsxAttribute(property) || property.name.getText() !== name) {
      continue;
    }
    const value = property.initializer;
    if (value === undefined) {
      return null;
    }
    if (ts.isStringLiteral(value)) {
      return value.text;
    }
    if (ts.isJsxExpression(value) && value.expression !== undefined && ts.isStringLiteral(value.expression)) {
      return value.expression.text;
    }
    return null;
  }
  return undefined;
}

/** 그 노드를 **감싸는 가장 가까운 `<label>`** 의 여는 태그 — 없으면 `null`. */
function enclosingLabel(node) {
  for (let parent = node.parent; parent !== undefined; parent = parent.parent) {
    if (ts.isJsxElement(parent) && parent.openingElement.tagName.getText() === 'label') {
      return parent.openingElement;
    }
  }
  return null;
}

/** 한 소스의 위반 — 감싸지 않았거나, 감쌌는데 최소 높이를 들지 않은 자리. */
export function unwrappedControlsIn(source) {
  const found = [];
  const visit = (node) => {
    if (ts.isJsxSelfClosingElement(node) || ts.isJsxOpeningElement(node)) {
      const type = node.tagName.getText() === 'input' ? literalAttribute(node, 'type') : undefined;
      if (type !== undefined && (type === null || WRAPPED_CONTROL_TYPES.has(type))) {
        const line = source.getLineAndCharacterOfPosition(node.getStart(source)).line + 1;
        // `type` 이 식이면 체크박스일 수 **있다** — 증명할 수 없으면 같은 의무를 지운다.
        const label = enclosingLabel(node);
        if (type === null && label === null) {
          // 글자로 적지 않은 `type` 을 레이블 밖에 둔 자리. 체크박스가 아닐 수도 있으나
          // **증명할 수 없으므로** 센다.
          found.push({ file: source.fileName, line, why: 'type 을 증명할 수 없는 input 이 레이블 밖이다' });
        } else if (type !== null && label === null) {
          found.push({ file: source.fileName, line, why: `${type} 를 감싼 label 이 없다` });
        } else if (label !== null && WRAPPED_CONTROL_TYPES.has(type ?? '')) {
          const className = literalAttribute(label, 'className');
          if (className === null || className === undefined || !className.split(/\s+/u).includes(MIN_TOUCH_CLASS)) {
            found.push({ file: source.fileName, line, why: `감싼 label 이 ${MIN_TOUCH_CLASS} 를 들지 않는다` });
          }
        }
      }
    }
    ts.forEachChild(node, visit);
  };
  visit(source);
  return found;
}

/** 합성 원문 하나를 재는 입구(대조용). */
export function unwrappedControls(text) {
  return unwrappedControlsIn(ts.createSourceFile('/probe.tsx', text, ts.ScriptTarget.ESNext, true, ts.ScriptKind.TSX));
}

/** 출하 트리 전수 — 반환이 빈 배열이면 위반 0 이다. */
export function scanControls(dir) {
  const files = shippedSourceFiles(dir);
  if (files.length === 0) {
    throw new Error(`훑기가 비었다: ${dir}`);
  }
  const program = screenProgram(resolve(dir, '..'));
  const findings = [];
  for (const one of files) {
    const source = program.getSourceFile(resolve(dir, one));
    if (source === undefined) {
      throw new Error(`TS 프로그램에 소스 파일이 없다: ${one}`);
    }
    findings.push(...unwrappedControlsIn(source).map((hit) => ({ ...hit, file: one })));
  }
  return { files, findings };
}

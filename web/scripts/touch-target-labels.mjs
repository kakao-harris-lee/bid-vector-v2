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

import { intrinsicTagsOf, probeProgram, shippedSourceFiles } from './jsx-handler-tags.mjs';
import { screenProgram } from './screen-card-coverage.mjs';

/** §6.5 의 44px(= `2.75rem`)를 레이블이 드는 Tailwind 어휘. */
export const MIN_TOUCH_CLASS = 'min-h-11';

/** 암묵 레이블을 요구하는 컨트롤 — 손가락이 누르는 자리가 컨트롤 자신보다 큰 종류다. */
export const WRAPPED_CONTROL_TYPES = new Set(['checkbox', 'radio']);

/**
 * **레이블이 켜는 컨트롤 후보**(HTML 의 labelable). 레이블 하나가 켜는 것은 이 가운데 **첫
 * 자손 하나뿐**이라, 둘 이상이 들어 있으면 뒤의 것은 레이블을 눌러도 켜지지 않는다.
 */
const LABELABLE_TAGS = new Set(['button', 'input', 'meter', 'output', 'progress', 'select', 'textarea']);

/**
 * 최소 높이를 **덮어쓸 수 있는** 어휘. 변형 접두(`max-md:`)와 우선 표시(`!`)까지 받는다 —
 * 하나라도 붙어 있으면 그 레이블이 44px 를 든다고 **증명할 수 없다**.
 */
const MIN_HEIGHT_TOKEN = /^(?:[\w.-]+:)*!?min-h-/u;

/** 그 여는 태그의 속성 값이 **글자 그대로** 무엇인가 — 식이면 `null`(증명 불가). */
function literalAttribute(element, name) {
  let spread = false;
  for (const property of element.attributes.properties) {
    if (ts.isJsxSpreadAttribute(property)) {
      // **볼 수 없는 것을 통과시키지 않는다**(cr Q-5 와 같은 규율) — 펼친 키가 식 뒤에 숨는다.
      spread = true;
      continue;
    }
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
  // 글자로 적힌 그 속성이 없는데 spread 가 있으면 **없다고 말할 수 없다**.
  return spread ? null : undefined;
}

/** 그 여는 태그가 가리키는 내장 태그 집합 — ① 축과 **같은 층**이다(글자가 아니라 타입). */
function tagsOf(checker, element) {
  return intrinsicTagsOf(checker, element.tagName, element.tagName.getText());
}

/** 그 노드를 감싸는 가장 가까운 `<label>` 요소(여는 태그가 아니라 요소) — 없으면 `null`. */
function enclosingLabelElement(checker, node) {
  for (let parent = node.parent; parent !== undefined; parent = parent.parent) {
    // **증명된 레이블만** 레이블이다. 태그가 불확실한 조상을 레이블로 쳐 주면 그 불확실성이
    // 의무를 **면제하는** 쪽으로 쓰인다 — 이 축의 방향은 반대다.
    if (ts.isJsxElement(parent) && tagsOf(checker, parent.openingElement)?.includes('label') === true) {
      return parent;
    }
  }
  return null;
}

/** 그 레이블 안의 labelable 자손 전수 — 둘 이상이면 어느 것이 켜지는지 증명할 수 없다. */
function labelableDescendants(checker, labelElement) {
  const found = [];
  const visit = (node) => {
    if (ts.isJsxSelfClosingElement(node) || ts.isJsxOpeningElement(node)) {
      const tags = tagsOf(checker, node);
      if (tags === null || tags.some((one) => LABELABLE_TAGS.has(one))) {
        found.push(node);
      }
    }
    ts.forEachChild(node, visit);
  };
  for (const child of labelElement.children) {
    visit(child);
  }
  return found;
}

/** 그 레이블이 **44px 를 든다고 증명되는가**. */
function labelCarriesMinHeight(labelOpening) {
  const className = literalAttribute(labelOpening, 'className');
  if (className === null || className === undefined) {
    return false;
  }
  const tokens = className.split(/\s+/u).filter((one) => one !== '');
  if (!tokens.includes(MIN_TOUCH_CLASS)) {
    return false;
  }
  // 덮어쓰는 어휘가 함께 있으면 **실효 높이를 증명할 수 없다**(v M-10).
  return !tokens.some((one) => one !== MIN_TOUCH_CLASS && MIN_HEIGHT_TOKEN.test(one));
}

/**
 * 한 소스의 위반.
 *
 * **규칙은 하나다**: 체크박스·라디오(또는 그것임을 **배제할 수 없는** `input`)는 ① 자기를 켜는
 * 레이블이 감싸고 ② 그 레이블이 최소 높이를 **증명 가능하게** 들어야 한다. 증명할 수 없는 자리는
 * 전부 ①②를 같이 진다 — 레이블 안이라고 해서 ②가 사라지지 않는다(cr Q-1 · v M-10).
 */
export function unwrappedControlsIn(checker, source) {
  const found = [];
  const visit = (node) => {
    if (ts.isJsxSelfClosingElement(node) || ts.isJsxOpeningElement(node)) {
      const tags = tagsOf(checker, node);
      const maybeInput = tags === null || tags.includes('input');
      if (maybeInput) {
        const line = source.getLineAndCharacterOfPosition(node.getStart(source)).line + 1;
        const rawType = literalAttribute(node, 'type');
        // HTML 은 `type` 의 대소문자를 가리지 않는다 — 가리면 한 글자로 열린다(v M-10).
        const type = typeof rawType === 'string' ? rawType.toLowerCase() : rawType;
        const unprovable = tags === null || type === null;
        if (unprovable || WRAPPED_CONTROL_TYPES.has(type ?? '')) {
          const why = checkedWhy(checker, node, source, unprovable);
          if (why !== null) {
            found.push({ file: source.fileName, line, why });
          }
        }
      }
    }
    ts.forEachChild(node, visit);
  };
  visit(source);
  return found;
}

/** 그 컨트롤이 의무를 어겼다면 **그 사유**, 아니면 `null`. */
function checkedWhy(checker, node, source, unprovable) {
  const suffix = unprovable ? '(증명할 수 없는 자리라 같은 의무를 진다)' : '';
  const labelElement = enclosingLabelElement(checker, node);
  if (labelElement === null) {
    return `감싼 label 이 없다${suffix}`;
  }
  const labelOpening = labelElement.openingElement;
  // `htmlFor` 가 있으면 그 레이블의 컨트롤은 **그 id** 다 — 이 노드가 아니면 켜지지 않는다.
  const htmlFor = literalAttribute(labelOpening, 'htmlFor');
  if (htmlFor !== undefined) {
    const id = literalAttribute(node, 'id');
    if (htmlFor === null || id === null || htmlFor !== id) {
      return `label 의 htmlFor 가 이 컨트롤을 가리키지 않는다${suffix}`;
    }
  } else {
    // 암묵 연결이면 켜지는 것은 **첫 labelable 자손 하나**다.
    const labelable = labelableDescendants(checker, labelElement);
    if (labelable.length > 1) {
      return `label 하나가 labelable 컨트롤 ${labelable.length} 을 감싼다 — 어느 것이 켜지는지 증명할 수 없다${suffix}`;
    }
  }
  if (!labelCarriesMinHeight(labelOpening)) {
    return `감싼 label 이 ${MIN_TOUCH_CLASS} 를 증명 가능하게 들지 않는다${suffix}`;
  }
  return null;
}

/** 합성 원문 하나를 재는 입구(대조용) — 태그 판정이 타입이라 **검사기가 필요하다**. */
export function unwrappedControls(text, modules = {}) {
  const { program, source } = probeProgram(text, modules);
  return unwrappedControlsIn(program.getTypeChecker(), source);
}

/** 출하 트리 전수 — 반환이 빈 배열이면 위반 0 이다. */
export function scanControls(dir) {
  const files = shippedSourceFiles(dir);
  if (files.length === 0) {
    throw new Error(`훑기가 비었다: ${dir}`);
  }
  const program = screenProgram(resolve(dir, '..'));
  const checker = program.getTypeChecker();
  const findings = [];
  for (const one of files) {
    const source = program.getSourceFile(resolve(dir, one));
    if (source === undefined) {
      throw new Error(`TS 프로그램에 소스 파일이 없다: ${one}`);
    }
    findings.push(...unwrappedControlsIn(checker, source).map((hit) => ({ ...hit, file: one })));
  }
  return { files, findings };
}

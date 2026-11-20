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

/**
 * **`min-height` 가 실제로 서는 display 어휘**(M13/13C — 13B v M-11).
 *
 * 레이블의 기본 display 는 `inline` 이고 CSS 는 inline 요소에 `min-height` 를 **적용하지 않는다**.
 * 그래서 `min-h-11` 만 든 레이블은 Chromium 에서 17px 로 섰다(verifier 실측). 높이가 서는 쪽을
 * **허용 집합**으로 두고 나머지(어휘가 **없는 경우를 포함**)는 증명 불가로 센다 — `contents`·
 * `inline`·`hidden` 을 금지 목록에 따로 적지 않는다(목록이 둘이면 갈린다).
 *
 * 변형 접두가 붙은 display(`md:flex`)는 **그 폭에서만** 서므로 증명이 되지 않는다. 이 축이 지키는
 * 자리는 모바일 폭이다.
 */
export const MIN_HEIGHT_DISPLAY_TOKENS = new Set([
  'block',
  'flex',
  'flow-root',
  'grid',
  'inline-block',
  'inline-flex',
  'inline-grid',
  'table',
]);

/** 그 `style` 이 상자를 덮는가 — 덮는 키가 있거나 **객체를 볼 수 없으면** 참이다. */
const BOX_STYLE_KEYS = new Set(['display', 'minHeight', 'height', 'maxHeight']);

function styleOverridesBox(element) {
  for (const property of element.attributes.properties) {
    if (!ts.isJsxAttribute(property) || property.name.getText() !== 'style') {
      continue;
    }
    const initializer = property.initializer;
    if (initializer === undefined || !ts.isJsxExpression(initializer) || initializer.expression === undefined) {
      return true;
    }
    const value = initializer.expression;
    if (!ts.isObjectLiteralExpression(value)) {
      return true;
    }
    return value.properties.some((one) => {
      if (ts.isSpreadAssignment(one)) {
        return true;
      }
      const name = one.name;
      const text = name !== undefined && (ts.isIdentifier(name) || ts.isStringLiteralLike(name)) ? name.text : null;
      return text === null || BOX_STYLE_KEYS.has(text);
    });
  }
  return false;
}

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

/**
 * 그 레이블 안의 labelable 자손 전수 — 둘 이상이면 어느 것이 켜지는지 증명할 수 없다.
 *
 * **컴포넌트도 센다**(M13/13C — 13B v M-11 셋째 꼴). 레이블이 켜는 것은 첫 labelable 자손
 * 하나인데, 그 자리에 컴포넌트가 있으면 그것이 무엇을 그리는지 이 축은 모른다. 모르는 것을
 * 「labelable 이 아니다」로 접으면 컴포넌트가 버튼을 그리는 순간 레이블이 체크박스를 켜지
 * 않는데도 초록이다 — 보수적으로 센다.
 */
function labelableDescendants(checker, labelElement) {
  const found = [];
  const visit = (node) => {
    if (ts.isJsxSelfClosingElement(node) || ts.isJsxOpeningElement(node)) {
      const tags = tagsOf(checker, node);
      const custom = tags !== null && tags.length === 0;
      if (tags === null || custom || tags.some((one) => LABELABLE_TAGS.has(one))) {
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

/**
 * **display 를 덮어쓰는 어휘**(M13/13C 수정 라운드 1 — v M-3). 변형 접두(`max-md:`)와 우선
 * 표시(`!`)까지 받는다 — 하나라도 붙어 있으면 그 폭에서 어떤 display 가 서는지 증명할 수 없다.
 * 높이 어휘 쪽과 **같은 규율**이다(앞 판은 높이만 그렇게 보고 display 는 보지 않았다).
 */
const DISPLAY_TOKEN = new RegExp(
  `^(?:[\\w.-]+:)*!?(?:${[...MIN_HEIGHT_DISPLAY_TOKENS, 'contents', 'inline', 'hidden', 'table-cell', 'list-item'].join('|')})$`,
  'u',
);

/** 그 레이블이 **44px 를 든다고 증명되는가**. */
function labelCarriesMinHeight(labelOpening) {
  /**
   * **인라인 `style` 로 덮는 길**(설계 검토 B (2) 의 약속 · v M-3). 그 자리의 `display`·높이는
   * 클래스보다 세므로, 그 키가 있으면(또는 객체를 볼 수 없으면) 증명 불가로 접는다. 덮지 않는
   * `style` 은 그대로 둔다 — 과차단을 이유 없이 넓히지 않는다.
   */
  if (styleOverridesBox(labelOpening)) {
    return false;
  }
  const className = literalAttribute(labelOpening, 'className');
  if (className === null || className === undefined) {
    return false;
  }
  const tokens = className.split(/\s+/u).filter((one) => one !== '');
  if (!tokens.includes(MIN_TOUCH_CLASS)) {
    return false;
  }
  // **높이가 서는 display** 가 함께 있어야 한다(v M-11) — 없으면 레이블은 inline 이고 그 값은
  // 적용되지 않는다.
  const standing = tokens.filter((one) => MIN_HEIGHT_DISPLAY_TOKENS.has(one));
  if (standing.length === 0) {
    return false;
  }
  // 그 위에 **다른 display 어휘가 겹치면** 어느 것이 이기는지 증명할 수 없다(v M-3).
  if (tokens.some((one) => !MIN_HEIGHT_DISPLAY_TOKENS.has(one) && DISPLAY_TOKEN.test(one))) {
    return false;
  }
  // 덮어쓰는 높이 어휘가 함께 있으면 **실효 높이를 증명할 수 없다**(v M-10).
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

/* ── 축 ② 암묵 레이블 위의 이름 덮어쓰기 (M14/14F · cr D4) ──────────────────────── */

/**
 * **이름을 덮는 속성.** `aria-labelledby` 를 함께 드는 까닭은 그것이 이름 계산에서 `aria-label`
 * 보다 **더 세기** 때문이다 — 한쪽만 재면 술어가 한 글자로 비켜 간다(설계 검토 (2) ③).
 *
 * `title` 은 들지 않는다: 레이블이 있으면 이름이 **이미** 섰고 `title` 은 쓰이지 않는다. 그
 * 속성의 다른 문제(손가락이 볼 수 없다)는 **다른 축**이다(설계 검토 (2) ④).
 */
export const NAME_OVERRIDE_ATTRIBUTES = new Set(['aria-label', 'aria-labelledby']);

/**
 * 한 소스의 위반 — **보이는 레이블이 이미 이름을 주는 컨트롤의 이름을 덮는 자리**.
 *
 * 저장소가 적어 둔 규칙(`components/Button.tsx` 의 KDoc — 「보이는 글자가 있으면 주지 않는다 ·
 * 낭독이 갈린다」)을 재는 자리다. 선언만 있고 재는 자리가 없었다. 지워지는 것이 형식 지시와
 * **파괴적 결과 경고**라, 눈으로 보는 운영자와 듣는 운영자가 서로 다른 지시를 받는다.
 *
 * 암묵 레이블의 정의와 태그 판정은 ① 축과 **같은 함수**를 쓴다 — 사본을 두면 「암묵 레이블」이
 * 두 벌이 되고, 갈린 날 어느 쪽이 맞는지 아무도 모른다.
 */
export function ariaLabelOverridesIn(checker, source) {
  const found = [];
  const visit = (node) => {
    if (ts.isJsxSelfClosingElement(node) || ts.isJsxOpeningElement(node)) {
      const tags = tagsOf(checker, node);
      const custom = tags !== null && tags.length === 0;
      /**
       * 모집단은 **labelable 후보**다 — 태그를 증명할 수 없는 자리와 컴포넌트도 든다(① 축의
       * 보수적 셈과 같은 규율: 모르는 것을 면제로 쓰지 않는다). 레이블 **자신**은 모집단이
       * 아니다 — 레이블의 `aria-label` 은 컨트롤의 이름을 덮지 않는다.
       */
      const labelable = tags === null || custom || tags.some((one) => LABELABLE_TAGS.has(one));
      const isLabel = tags !== null && tags.length > 0 && tags.every((one) => one === 'label');
      if (labelable && !isLabel && enclosingLabelElement(checker, node) !== null) {
        const line = source.getLineAndCharacterOfPosition(node.getStart(source)).line + 1;
        for (const name of NAME_OVERRIDE_ATTRIBUTES) {
          const value = literalAttribute(node, name);
          if (value === undefined) {
            continue;
          }
          // `null` 은 **볼 수 없다**는 뜻이다(식 · 펼친 속성) — 통과시키지 않는다.
          const why =
            value === null
              ? `감싼 label 이 이름을 주는데 ${name} 을 볼 수 없다(식 또는 펼친 속성)`
              : `감싼 label 이 이름을 주는데 ${name} 이 그것을 덮는다`;
          found.push({ file: source.fileName, line, why });
          break;
        }
      }
    }
    ts.forEachChild(node, visit);
  };
  visit(source);
  return found;
}

/** 합성 원문 하나를 재는 입구(대조용). */
export function ariaLabelOverrides(text, modules = {}) {
  const { program, source } = probeProgram(text, modules);
  return ariaLabelOverridesIn(program.getTypeChecker(), source);
}

/** 출하 트리 전수 — `findings` 가 빈 배열이면 위반 0 이다. */
export function scanAriaLabelOverrides(dir) {
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
    findings.push(...ariaLabelOverridesIn(checker, source).map((hit) => ({ ...hit, file: one })));
  }
  return { files, findings };
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

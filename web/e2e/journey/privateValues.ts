import { expect, type Page } from '@playwright/test';

import {
  BUSINESS_REGISTRATION_NUMBER_TEST_ID,
  businessRegistrationNumberText,
} from '../../src/display/profileDisplay';

import { journeyContext } from './shared';

/**
 * 「화면에 개인정보·비밀값이 서지 않는다」의 **술어 한 자리**(D-7E-6 · M15/15C).
 *
 * 앞 판은 여정 세 spec 이 각자 `body.innerText()` 에 정규식을 걸었다. 그 술어는 **입력칸의
 * `value` 를 담지 않는다** — 그래서 사업자번호 입력칸이 생기는 순간, 예외를 하나도 주지 않아도
 * 게이트는 조용했다(방어 부재와 미측정이 같은 값이 된다). 여기서는 **보이는 글 + 폼 값·속성**을
 * 한 문자열로 묶어 같은 질문을 던지고, 그 넓힌 모집단에서 **testId 하나의 `value`** 만 뺀다
 * (노드 전체가 아니다 — r1 cr L-11).
 *
 * 예외가 한 자리인 것이 요점이다: 부르는 spec 이 예외를 고르지 않으므로 「어느 화면에서만
 * 열려 있다」가 생기지 않고, 「무엇이 예외인가」가 이 파일의 한 줄로 읽힌다. 캡처 하네스는 이
 * 모듈을 쓰지 않는다 — 그 산출물(PNG)은 저장소 밖으로 나가므로 거기서는 0 이 그대로 0 이다.
 */

/**
 * **남의 업체** 번호를 잡는 축 — 꼴만 본다(r1 cr H-1 의 절반). 숫자 열 자리 연속을 금지하면
 * 금액·공고번호가 전부 걸리므로 하이픈 꼴로 좁힌다. 화면이 남의 값을 보일 때 그 꼴을 쓸지는
 * 아무도 보장하지 않지만, 이 축이 겨냥하는 것은 **공고·개찰 문면에 섞여 오는 표기**다.
 */
const BUSINESS_REGISTRATION_NUMBER_SHAPE = /\b\d{3}-\d{2}-\d{5}\b/u;

/**
 * **자기 값**을 잡는 축 — 이 실행이 실제로 저장한 합성 번호의 **축어**다(r1 cr H-1 · v F1).
 *
 * 꼴 정규식만으로는 **저장되는 꼴(숫자 열 자리)이 영원히 사각**이다: 어떤 화면이 계약 값을
 * 그대로 그리면 화면에 서는 글자가 `1234567890` 이고 그 정규식은 매치하지 않는다. 리터럴은
 * 금액·공고번호 오탐을 만들지 않으므로 꼴 정규식의 제약을 건드리지 않고 그 사각을 닫는다.
 *
 * 두 꼴을 **한 값에서** 낸다(서식은 표시층의 함수다) — 둘을 따로 적으면 한쪽만 고쳐도 조용하다.
 */
export const PLANTED_BUSINESS_REGISTRATION_NUMBER = '1234567890';
export const PLANTED_BUSINESS_REGISTRATION_NUMBER_TYPED = businessRegistrationNumberText(
  PLANTED_BUSINESS_REGISTRATION_NUMBER,
);

/**
 * 모집단에서 빼는 **유일한** 자리 — 운영자 자기 계정의 사업자번호 입력칸이다. 상수를 화면
 * 모듈에서 가져오므로 그 testId 를 바꾸면 예외가 조용히 비는 일이 없다(둘이 한 값이다).
 */
export const OWN_BUSINESS_REGISTRATION_NUMBER = BUSINESS_REGISTRATION_NUMBER_TEST_ID;

/**
 * 술어가 **값 밖에서** 훑는 속성 모집단 — **한 자리다**. 아래 `querySelectorAll` 의 선택자와
 * 수집 줄이 둘 다 이 값의 **구성**이라, 속성을 더하거나 빼는 길이 이 상수 하나다(두 자리에
 * 열거하면 한쪽만 고치는 변이가 조용하다).
 *
 * **밖으로 내는 이유는 대조가 등식으로 재기 위해서다**(M15/15I ⓑ). 양성 대조는 자기 리터럴을
 * 그대로 들고 심고, 그 리터럴과 이 값의 **집합 등식**을 따로 단언한다 — 대조가 이 값을
 * 순회하면 속성이 **느는** 변이가 대조의 모집단도 함께 늘려 아무것도 붉지 않는다(그것이 그
 * 목록을 일부러 두 번 적어 온 사유다). 등식은 **잃는 쪽과 얻는 쪽을 둘 다** 붉힌다.
 */
export const SCANNED_ATTRIBUTES = ['placeholder', 'title', 'alt', 'aria-label'] as const;

/**
 * 두 축이 내는 **문면** — 양성 대조가 「어느 축이 붉었는가」를 축어로 단언하기 위해 밖으로
 * 낸다(r1 v F1). 한 축을 꺼도 다른 축이 가리는 것을 「문제가 있다」만으로는 가를 수 없다.
 */
export const PRIVATE_VALUE_PROBLEM_SHAPE = '사업자등록번호 꼴(3-2-5)이 자기 입력칸 밖에 있다';
export const PRIVATE_VALUE_PROBLEM_PLANTED = '저장한 사업자등록번호(숫자 열 자리)가 자기 입력칸 밖에 있다';

/**
 * 지금 그려진 화면이 개인정보를 들고 있는가 — **문제 목록**을 낸다(빈 목록이 통과다).
 *
 * 값을 내는 것은 양성 대조를 위해서다: 같은 값을 예외 밖 칸에 두면 이 함수가 **문제를 내야**
 * 하고, 그 전이를 재는 test 가 「예외가 화면 전체를 열지 않았다」의 증거다.
 *
 * **면제된 노드 수도 센다**(r1 cr L-4): 예외는 「그 testId 를 가진 **모든** 요소」라, 같은
 * testId 가 둘이 되는 날 예외가 조용히 넓어진다. 「한 자리」라는 주장을 화면마다 재는 것이
 * 아니라 **술어 자신이** 재게 한다 — 그러면 그 카드가 서는 어느 화면에서든 서는 단언이다.
 */
export async function privateValueProblems(page: Page): Promise<string[]> {
  const body = await page.locator('body').innerText();
  const scanned = await page.evaluate(
    (input: { readonly allowedTestId: string; readonly attributes: readonly string[] }) => {
      const { allowedTestId, attributes } = input;
      // 선택자도 **같은 목록의 구성**이다 — 훑는 속성과 고르는 속성이 갈리면 모집단이 조용히
      // 비는 자리가 생긴다.
      const selector = ['input', 'textarea', 'select', ...attributes.map((name) => `[${name}]`)].join(', ');
      const nodes = [...document.querySelectorAll(selector)];
      const exempted = nodes.filter((element) => element.getAttribute('data-testid') === allowedTestId);
      const text = nodes
        .flatMap((element) => [
          // **면제는 그 칸의 `value` 하나다**(r1 cr L-11). 노드 전체를 빼면 계약의 예외(「자기
          // 칸의 자기 값」)보다 술어의 예외가 넓어지고, 뒷날 그 칸에 `title={번호}` 류가 붙는 날
          // 예외가 **조용히** 함께 넓어진다. 속성 넷은 예외 칸에서도 모집단에 남는다.
          element.getAttribute('data-testid') === allowedTestId
            ? ''
            : ((element as Partial<HTMLInputElement>).value ?? ''),
          ...attributes.map((name) => element.getAttribute(name) ?? ''),
        ])
        .join('\n');
      return { text, exempted: exempted.length };
    },
    { allowedTestId: OWN_BUSINESS_REGISTRATION_NUMBER, attributes: [...SCANNED_ATTRIBUTES] },
  );

  const shown = `${body}\n${scanned.text}`;
  const problems: string[] = [];
  if (BUSINESS_REGISTRATION_NUMBER_SHAPE.test(shown)) {
    problems.push(PRIVATE_VALUE_PROBLEM_SHAPE);
  }
  if (shown.includes(PLANTED_BUSINESS_REGISTRATION_NUMBER)) {
    problems.push(PRIVATE_VALUE_PROBLEM_PLANTED);
  }
  if (shown.includes(journeyContext().passphrase)) {
    problems.push('실행 비밀 문구가 화면에 있다');
  }
  if (scanned.exempted > 1) {
    problems.push(`누출 게이트의 예외 testId 가 한 화면에 ${scanned.exempted} 개다 — 예외는 한 자리다`);
  }
  return problems;
}

/** 통과 단언 — 부르는 자리가 예외를 고르지 않는다(그 선택은 이 모듈 하나에 있다). */
export async function assertNoPrivateValues(page: Page): Promise<void> {
  expect(await privateValueProblems(page)).toStrictEqual([]);
}

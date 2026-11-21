import { expect, type Page } from '@playwright/test';

import { BUSINESS_REGISTRATION_NUMBER_TEST_ID } from '../../src/screens/ProfileDetailsCard';

import { journeyContext } from './shared';

/**
 * 「화면에 개인정보·비밀값이 서지 않는다」의 **술어 한 자리**(D-7E-6 · M15/15C).
 *
 * 앞 판은 여정 세 spec 이 각자 `body.innerText()` 에 정규식을 걸었다. 그 술어는 **입력칸의
 * `value` 를 담지 않는다** — 그래서 사업자번호 입력칸이 생기는 순간, 예외를 하나도 주지 않아도
 * 게이트는 조용했다(방어 부재와 미측정이 같은 값이 된다). 여기서는 **보이는 글 + 폼 값·속성**을
 * 한 문자열로 묶어 같은 질문을 던지고, 그 넓힌 모집단에서 **testId 하나**만 뺀다.
 *
 * 예외가 한 자리인 것이 요점이다: 부르는 spec 이 예외를 고르지 않으므로 「어느 화면에서만
 * 열려 있다」가 생기지 않고, 「무엇이 예외인가」가 이 파일의 한 줄로 읽힌다. 캡처 하네스는 이
 * 모듈을 쓰지 않는다 — 그 산출물(PNG)은 저장소 밖으로 나가므로 거기서는 0 이 그대로 0 이다.
 *
 * 사업자번호를 **하이픈 꼴로** 보는 이유: 숫자 열 자리 연속을 금지하면 금액·공고번호가 전부
 * 걸린다. 화면이 그 값을 보일 때 자리 구분을 입히는 것이 이 게이트가 서는 전제이고, 그 서식은
 * 표시층 한 자리에서 나온다.
 */
const BUSINESS_REGISTRATION_NUMBER_SHAPE = /\b\d{3}-\d{2}-\d{5}\b/u;

/**
 * 모집단에서 빼는 **유일한** 자리 — 운영자 자기 계정의 사업자번호 입력칸이다. 상수를 화면
 * 모듈에서 가져오므로 그 testId 를 바꾸면 예외가 조용히 비는 일이 없다(둘이 한 값이다).
 */
export const OWN_BUSINESS_REGISTRATION_NUMBER = BUSINESS_REGISTRATION_NUMBER_TEST_ID;

/**
 * 지금 그려진 화면이 개인정보를 들고 있는가 — **문제 목록**을 낸다(빈 목록이 통과다).
 *
 * 값을 내는 것은 양성 대조를 위해서다: 같은 값을 예외 밖 칸에 두면 이 함수가 **문제를 내야**
 * 하고, 그 전이를 재는 test 가 「예외가 화면 전체를 열지 않았다」의 증거다.
 */
export async function privateValueProblems(page: Page): Promise<string[]> {
  const body = await page.locator('body').innerText();
  const attributes = await page.evaluate((allowedTestId: string) => {
    const nodes = document.querySelectorAll(
      'input, textarea, select, [title], [alt], [aria-label], [placeholder]',
    );
    return [...nodes]
      .filter((element) => element.getAttribute('data-testid') !== allowedTestId)
      .flatMap((element) => [
        (element as Partial<HTMLInputElement>).value ?? '',
        element.getAttribute('placeholder') ?? '',
        element.getAttribute('title') ?? '',
        element.getAttribute('alt') ?? '',
        element.getAttribute('aria-label') ?? '',
      ])
      .join('\n');
  }, OWN_BUSINESS_REGISTRATION_NUMBER);

  const shown = `${body}\n${attributes}`;
  const problems: string[] = [];
  if (BUSINESS_REGISTRATION_NUMBER_SHAPE.test(shown)) {
    problems.push('사업자등록번호 꼴(3-2-5)이 자기 입력칸 밖에 있다');
  }
  if (shown.includes(journeyContext().passphrase)) {
    problems.push('실행 비밀 문구가 화면에 있다');
  }
  return problems;
}

/** 통과 단언 — 부르는 자리가 예외를 고르지 않는다(그 선택은 이 모듈 하나에 있다). */
export async function assertNoPrivateValues(page: Page): Promise<void> {
  expect(await privateValueProblems(page)).toStrictEqual([]);
}

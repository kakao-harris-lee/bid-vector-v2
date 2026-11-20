// 컨트롤 경계의 **그려진 색**을 재는 한 자리(M14/14A · v M-1 · cr L-2).
//
// AST 축(`scripts/control-line.mjs`)은 JSX 의 className **리터럴**을 본다. 그래서 클래스 글자가
// 모듈 상수나 헬퍼에 들어가면(`className={CONST}` · `className={inputClass()}`) 그 축이 아무 말도
// 하지 않는다 — 리뷰가 변이로 실측한 구멍이다. 공유 버튼은 어조 표를 **값으로** 재서 닫았지만
// 그것은 그 컴포넌트 하나의 처분이고, 일반형이 남아 있었다.
//
// 이 층은 **브라우저가 실제로 그린 색**을 본다. 클래스가 어디서 왔든, 글자가 리터럴이든 상수든
// 헬퍼든, 그려진 테두리가 카드 선이면 여기서 붉는다.
//
// 이 함수는 **브라우저 안에서** 돈다(`page.evaluate` 가 원문을 직렬화한다) — 그래서 바깥
// 식별자를 하나도 쓰지 않고 자기 안에서 전부 세운다(`touchMeasure` 와 같은 규율).

export interface MeasuredBorder {
  readonly tag: string;
  readonly testId: string;
  readonly name: string;
  /** computed `border-*-color` 넷이 모두 같으면 그 값, 다르면 네 값을 이어 붙인 글자다. */
  readonly color: string;
  /** 가장 두꺼운 변의 두께(px). 0 이면 테두리를 그리지 않는 컨트롤이다. */
  readonly width: number;
}

/**
 * 보이는 컨트롤 전수의 경계. **모집단은 내장 컨트롤 태그**라 새 입력칸이 생기면 자동으로 든다.
 *
 * 「보이지 않는 것」을 빼는 술어는 computed style 이다 — 클래스 이름으로 빼면 그 글자 하나로
 * 게이트가 열린다(`touchMeasure` 와 같은 사유).
 */
export function measureControlBordersInPage(): readonly MeasuredBorder[] {
  const found: MeasuredBorder[] = [];
  const nodes = document.querySelectorAll('input, select, textarea, button');
  for (const node of nodes) {
    const style = getComputedStyle(node);
    if (style.display === 'none' || style.visibility === 'hidden') {
      continue;
    }
    const rect = node.getBoundingClientRect();
    if (rect.width === 0 && rect.height === 0) {
      continue;
    }
    const widths = [style.borderTopWidth, style.borderRightWidth, style.borderBottomWidth, style.borderLeftWidth].map(
      (one) => Number.parseFloat(one) || 0,
    );
    const colors = [style.borderTopColor, style.borderRightColor, style.borderBottomColor, style.borderLeftColor];
    const sameColor = colors.every((one) => one === colors[0]);
    found.push({
      tag: node.tagName.toLowerCase(),
      testId: node.getAttribute('data-testid') ?? '',
      name: (node.textContent ?? '').trim().slice(0, 30),
      color: sameColor ? (colors[0] ?? '') : colors.join(' | '),
      width: Math.max(...widths),
    });
  }
  return found;
}

/**
 * **허용 색을 브라우저가 계산하게 한다**(cr M-1 · 실측).
 *
 * 앞 판은 허용 색을 Node 에서 **글자로 조립**했다(`rgba(178, 58, 58, 0.4)` 꼴). 그런데 출하
 * CSS 의 어조 테두리는 `color-mix(in oklab, …)` 이고 Chromium 은 그것을
 * `oklab(0.522668 … / 0.4)` 로 돌려준다 — **영영 맞지 않는 두 글자**다. 그래서 `danger` 버튼의
 * 경계는 사실상 한 번도 허용되지 않았고, 오늘 통과한 것은 그 버튼이 모집단에 **없었기** 때문이다
 * (쓰기 실패 얼굴은 쓰기가 실패해야 선다). 14C·14D 가 실패 얼굴을 띄우는 순간 붉었을 자리다.
 *
 * 고치는 길은 **같은 파서를 지나게** 하는 것이다: 허용 클래스를 버릴 요소에 걸어 브라우저가 낸
 * computed 값을 쓴다. 그러면 재는 쪽과 재어지는 쪽이 같은 자리에서 온다.
 *
 * 이 함수도 **브라우저 안에서** 돈다 — 바깥 식별자를 쓰지 않는다.
 */
export function computeBorderColorsInPage(classes: readonly string[]): readonly string[] {
  const found: string[] = [];
  for (const one of classes) {
    const probe = document.createElement('div');
    probe.className = `border ${one}`;
    probe.style.position = 'absolute';
    probe.style.visibility = 'hidden';
    document.body.append(probe);
    found.push(getComputedStyle(probe).borderTopColor);
    probe.remove();
  }
  return found;
}

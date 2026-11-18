// M13/13B 가 세운 **터치 타깃 측정**을 한 자리로 옮긴 모듈(M13/13C).
//
// 이 함수는 **브라우저 안에서** 돈다(`page.evaluate` 가 원문을 직렬화한다) — 그래서 바깥 식별자를
// 하나도 쓰지 않고 자기 안에서 전부 세운다. 자리를 옮긴 이유는 **대조**다: 조상 잘라내기와
// 손가락 축을 합성 페이지에서 재려면 여정 하네스(출하 jar · Postgres · 수집)가 필요 없고,
// 그 대조가 게이트와 **같은 함수**를 보아야 의미가 있다(사본을 만들지 않는다).

export interface MeasuredTarget {
  readonly tag: string;
  readonly name: string;
  readonly width: number;
  readonly height: number;
}

export interface Measurement {
  readonly targets: readonly MeasuredTarget[];
  readonly overflow: number;
  /** 뷰포트 **밖으로 삐져나간** 타깃 — 손가락이 그 상자의 일부에 닿지 못하는 자리다. */
  readonly offscreen: readonly string[];
  /** 부분 잘라내기가 걸린 타깃 — 눌리는 띠를 상자로 잴 수 없다. */
  readonly partiallyClipped: readonly string[];
}

/**
 * 한 화면의 측정 — **상자**로 본다.
 *
 * 「보이지 않는 것」을 빼는 술어가 **computed style** 인 것이 요점이다(착수 판정 ⓘ):
 * `.sr-only` 라는 **클래스 이름**으로 빼면 그 글자 하나로 게이트가 열린다. 시각 은폐의 표준
 * 기법(`clip: rect(0,0,0,0)` · `clip-path: inset(50%)`)을 보면 기법을 쓴 자리만 빠진다.
 *
 * 그 예외가 **진짜 버튼을 숨기는 데** 쓰이면 집합이 줄고, 그때는 화면별 계수 하한이 붉는다.
 */
export function measureInPage(): Measurement {
  {
    /**
     * 모집단(팀장 판정 2026-11-16 보충) — 계약의 넷에 **누르는 내장 요소 셋**을 더한다:
     * `input[type=button|submit|reset]` · `summary` · `select`, 그리고 **체크박스·라디오**
     * (cr N-1 · 팀장 판정). 손가락이 누르는 자리이고, AST 축이 `input` 을 **무조건** 면제하므로
     * 이쪽에서 받지 않으면 두 게이트 사이에 틈이 생긴다 — 그 틈에 실물이 있던 쪽이 바로
     * 체크박스였다(실측 7자리 13px). `label` 은 **선택자에** 넣지 않는다 — 레이블이 혼자 서면
     * 누르는 자리가 아니고, 감싼 레이블은 아래 `touchBoxOf` 가 그 컨트롤을 통해 집는다.
     */
    const nodes = [
      ...document.querySelectorAll(
        'button, a, [role="tab"], [role="button"], input[type="button"], input[type="submit"], input[type="reset"], input[type="checkbox"], input[type="radio"], summary, select',
      ),
    ];
    /**
     * **누르는 자리가 그 요소 자신이 아닐 수 있다**(cr P-3). 체크박스·라디오를 `<label>` 이
     * 감싸고 **그 레이블이 켜는 컨트롤이 이 노드이면** 레이블 어디를 눌러도 이것이 켜진다 —
     * 손가락이 닿는 상자는 레이블이다. 그 자리는 레이블을 잰다.
     *
     * **`closest('label')` 만으로는 모자란다**(v M-9 · cr Q-4). 레이블이 켜는 것은 자기
     * **labeled control 하나뿐**이라, 레이블 하나가 컨트롤 둘을 감싸면 뒤의 것은 레이블을 눌러도
     * 켜지지 않고, `for` 가 다른 곳을 가리키면 감싸고도 켜지지 않는다. 그 자리들의 누르는 상자는
     * **자기 13px** 인데 감싼 레이블을 재면 44px 로 세 **앞 판보다 느슨해진다**(실측).
     * `HTMLLabelElement.control` 이 그 피연결 컨트롤을 그대로 주므로 그것으로 가른다.
     */
    const touchBoxOf = (node: Element): Element => {
      if (!(node instanceof HTMLInputElement) || (node.type !== 'checkbox' && node.type !== 'radio')) {
        return node;
      }
      const label = node.closest('label');
      return label instanceof HTMLLabelElement && label.control === node ? label : node;
    };
    /**
     * **조상이 자르면 보이는 띠는 그만큼이다**(M13/13C — 13B v M-8). 앞 판은 타깃 **자신의**
     * 상자만 보아, 44px 버튼을 12px 조상 안에 둔 꼴을 전부 통과시켰다(verifier 실측 — 셋).
     *
     * 자르는 조상과 **스크롤되는** 조상을 가른다: 스크롤은 밀면 닿으므로 잘라내기가 아니다
     * (그 구분이 없으면 긴 화면의 모든 타깃이 거짓으로 걸린다). `clip-path` 는 사각형이 아닐 수
     * 있어 **상자로 잴 수 없으므로** 자기 잘라내기와 같은 처분(위반)으로 돌린다.
     */
    const clipAncestry = (node: Element, box: DOMRect): { readonly top: number; readonly bottom: number; readonly unmeasurable: boolean } => {
      let top = box.top;
      let bottom = box.bottom;
      let unmeasurable = false;
      for (let parent = node.parentElement; parent !== null; parent = parent.parentElement) {
        const parentStyle = getComputedStyle(parent);
        if (parentStyle.clipPath !== 'none') {
          unmeasurable = true;
          continue;
        }
        const containsPaint = /\b(?:paint|strict|content)\b/u.test(parentStyle.contain);
        const hidesY = parentStyle.overflowY !== 'visible';
        if (!containsPaint && !hidesY) {
          continue;
        }
        // 세로로 **스크롤되는** 조상은 펴고 자르지 않는다 — 운영자가 밀어서 본다.
        const scrollsY =
          (parentStyle.overflowY === 'auto' || parentStyle.overflowY === 'scroll') &&
          parent.scrollHeight > parent.clientHeight;
        if (scrollsY && !containsPaint) {
          continue;
        }
        const parentBox = parent.getBoundingClientRect();
        top = Math.max(top, parentBox.top);
        bottom = Math.min(bottom, parentBox.bottom);
      }
      return { top, bottom, unmeasurable };
    };
    const targets = [];
    const offscreen = [];
    const partiallyClipped = [];
    for (const node of nodes) {
      const measured = touchBoxOf(node);
      const style = getComputedStyle(measured);
      let box = measured.getBoundingClientRect();
      /**
       * **시각 은폐는 위치 지정된 요소에서만 선다**(v M-2). `clip` 은 absolute·fixed 가 아닌
       * 요소에 **적용되지 않는데** computed 값에는 그대로 남는다 — 그 값만 보던 앞 판은 보이고
       * 눌리는 44×29 버튼을 모집단에서 뺐다(실측).
       */
      const positioned = style.position === 'absolute' || style.position === 'fixed';
      const clipped =
        positioned && (style.clip === 'rect(0px, 0px, 0px, 0px)' || style.clipPath === 'inset(50%)');
      /**
       * **상자가 0 이어도 글자가 보일 수 있다**(v M-2). `display: contents` 요소는 자기 상자가
       * 없지만 글자는 그려지고 클릭이 발화한다. 그 자리는 **글자 범위**로 다시 잰다.
       */
      if (!clipped && box.width === 0 && box.height === 0) {
        const range = document.createRange();
        range.selectNodeContents(measured);
        const textBox = range.getBoundingClientRect();
        if (textBox.width > 0 && textBox.height > 0) {
          box = textBox;
        }
      }
      const visible =
        !clipped &&
        style.visibility !== 'hidden' &&
        style.display !== 'none' &&
        Number(style.opacity) > 0 &&
        box.width > 0 &&
        box.height > 0;
      if (!visible) {
        continue;
      }
      const name = (node.getAttribute('aria-label') ?? measured.textContent ?? '').trim().slice(0, 40);
      /**
       * **부분 잘라내기는 잴 수 없으므로 위반이다**(v M-4). `clip-path: inset(0 0 70% 0)` 는 상자를
       * 줄이지 않아 높이 단언을 지나는데, 실제로 눌리는 띠는 그 일부뿐이다. 시각 은폐의 표준
       * 꼴(위 `clipped`)이 아닌 잘라내기가 걸려 있으면 **보수적으로** 센다.
       *
       * **보이는 것만 센다**(cr P-5) — `display:none`·0 상자처럼 안 보이는 자리까지 보고하면
       * 「보이는 타깃」을 재는 게이트가 안 보이는 것 때문에 붉는다. 위 `continue` 뒤라 여기 오는
       * 것은 전부 보이는 자리이고, 그래서 `clipped` 는 이미 거짓이다.
       */
      const ancestry = clipAncestry(measured, box);
      if (style.clipPath !== 'none' || style.clip !== 'auto' || ancestry.unmeasurable) {
        partiallyClipped.push(`${measured.tagName.toLowerCase()} "${name.slice(0, 24)}"`);
      }
      targets.push({
        tag: measured.tagName.toLowerCase(),
        name,
        width: Math.round(box.width * 10) / 10,
        // **보이는 띠**의 높이다(v M-8) — 조상이 자르면 그만큼만 손가락에 닿는다.
        height: Math.max(0, Math.round((ancestry.bottom - ancestry.top) * 10) / 10),
      });
      /**
       * **가로로 스크롤되는 줄 안의 타깃은 닿을 수 있다** — 운영자가 밀어서 보면 된다. 닿지
       * 못하는 것은 그런 조상이 없는 채 뷰포트 밖에 **고정된** 자리다(변이 H 가 그 꼴이다).
       * 그 둘을 조상의 `overflow-x` 와 실제 스크롤 여지로 가른다.
       */
      let scrollable = false;
      for (let parent = measured.parentElement; parent !== null; parent = parent.parentElement) {
        const parentStyle = getComputedStyle(parent);
        const overflowX = parentStyle.overflowX;
        if (!((overflowX === 'auto' || overflowX === 'scroll') && parent.scrollWidth > parent.clientWidth)) {
          continue;
        }
        /**
         * **손가락으로 밀 수 있어야 예외다**(M13/13C — 13B v L-4). `touch-action: pan-y` 면
         * 가로로는 밀 수 없는데 앞 판은 「스크롤 여지가 있다」만 보고 예외를 세웠다 — 그러면
         * 운영자가 닿을 수 없는 타깃이 면제된다. 가로 밀기를 허용하는 값만 예외로 받는다.
         */
        const touch = parentStyle.touchAction;
        const pannableX = touch === 'auto' || touch === 'manipulation' || /\bpan-(?:x|left|right)\b/u.test(touch);
        if (!pannableX) {
          continue;
        }
        const container = parent.getBoundingClientRect();
        /**
         * **그 스크롤 상자 자신이 뷰포트 안**이어야 한다(v M-5). 뷰포트 밖에 고정된 상자에
         * `overflow-x-auto` 한 줄을 더하는 것만으로 진짜 넘침이 면제되던 자리다.
         */
        if (container.left < -0.5 || container.right > window.innerWidth + 0.5) {
          continue;
        }
        // 그리고 타깃이 그 상자의 **스크롤 범위 안**이어야 한다(cr N-6) — 범위 밖은 밀어도 닿지 않는다.
        const start = box.left - container.left + parent.scrollLeft;
        const end = box.right - container.left + parent.scrollLeft;
        if (start >= -0.5 && end <= parent.scrollWidth + 0.5) {
          scrollable = true;
          break;
        }
      }
      // 부분 화소 반올림을 0.5px 로 받는다 — 그보다 큰 삐져나감만 센다.
      if (!scrollable && (box.left < -0.5 || box.right > window.innerWidth + 0.5)) {
        offscreen.push(
          `${measured.tagName.toLowerCase()} [${Math.round(box.left)}, ${Math.round(box.right)}] "${name}"`,
        );
      }
    }
    return {
      targets,
      offscreen,
      partiallyClipped,
      // **문서와 본문 둘 다** 본다 — 한쪽만 보면 넘침이 다른 쪽에만 서는 꼴을 놓친다.
      overflow:
        Math.max(document.documentElement.scrollWidth, document.body.scrollWidth) - window.innerWidth,
    };
  }
}

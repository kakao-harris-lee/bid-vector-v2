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
  /**
   * 그 자리의 `data-testid`(없으면 빈 글자). **이름으로 고르는 자리**가 이 칸을 쓴다 — 이름은
   * 화면의 문면이라 **다른 요소가 같은 이름을 가질 수 있고**(M13/13E: 상태 배지가 버튼이 되면서
   * 상태 낱말을 이름으로 들었다), 그때 이름 필터는 **엉뚱한 자리를 세고도 초록**이 된다.
   */
  readonly testId: string;
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
    /** 그 `touch-action` 이 **그 축으로 밀기**를 허용하는가 — 유효값은 사슬의 교집합이다(v M-2). */
    const allowsPan = (value: string, axis: 'x' | 'y'): boolean => {
      if (value === 'auto' || value === 'manipulation') {
        return true;
      }
      const along = axis === 'x' ? /\bpan-(?:x|left|right)\b/u : /\bpan-(?:y|up|down)\b/u;
      return along.test(value);
    };

    const clipAncestry = (
      node: Element,
      box: DOMRect,
    ): { readonly top: number; readonly bottom: number; readonly cap: number; readonly unmeasurable: boolean } => {
      let top = box.top;
      let bottom = box.bottom;
      let cap = Number.POSITIVE_INFINITY;
      let unmeasurable = false;
      // **사슬은 타깃에서 시작한다**(cr N-9) — 가로 축과 같은 자리에서 시작해야 두 축이 갈리지 않는다.
      let pannableY = allowsPan(getComputedStyle(node).touchAction, 'y');
      for (let parent = node.parentElement; parent !== null; parent = parent.parentElement) {
        const parentStyle = getComputedStyle(parent);
        // 손가락 축은 **사슬의 교집합**이다 — 중간 한 요소가 막으면 위쪽 스크롤러도 밀리지 않는다.
        pannableY = pannableY && allowsPan(parentStyle.touchAction, 'y');
        if (parentStyle.clipPath !== 'none') {
          unmeasurable = true;
          continue;
        }
        const containsPaint = /\b(?:paint|strict|content)\b/u.test(parentStyle.contain);
        const hidesY = parentStyle.overflowY !== 'visible';
        if (!containsPaint && !hidesY) {
          continue;
        }
        const scrollsY =
          (parentStyle.overflowY === 'auto' || parentStyle.overflowY === 'scroll') &&
          parent.scrollHeight > parent.clientHeight;
        /**
         * **세로로 스크롤되는 조상은 자르지 않지만 막는다**(v H-4). 밀면 닿으므로 지금 보이는
         * 사각형으로 자르지는 않되, **한 번에 보이는 띠는 그 상자 높이를 넘을 수 없다** — 앞 판은
         * 통째로 면제해서 12px 스크롤러 안의 44px 버튼을 44 로 셌다. 세로로 밀 수 없게 막아 둔
         * 자리는 스크롤이 아니므로 그냥 자르는 조상이다.
         */
        if (scrollsY && !containsPaint && pannableY) {
          cap = Math.min(cap, parent.clientHeight);
          continue;
        }
        const parentBox = parent.getBoundingClientRect();
        top = Math.max(top, parentBox.top);
        bottom = Math.min(bottom, parentBox.bottom);
      }
      return { top, bottom, cap, unmeasurable };
    };
    /**
     * **밀어서 볼 수 없는 자리인가**(v R1-M2 · r3 에서 `sticky` 를 뺐다). 고정 배치는 문서와
     * 함께 스크롤되지 않으므로 뷰포트 밖으로 걸친 부분이 **영원히 닿지 않는다**.
     *
     * `sticky` 는 **붙기 전에는 흐름과 함께 스크롤된다** — 접힘 아래의 sticky 머리는 밀면 닿으므로
     * 이 축에 두면 과차단이고, 이 축에는 가로가 가진 「스크롤되면 예외」 같은 탈출구가 없다
     * (cr R2-5 · verifier medium). 목록의 절 머리는 평범한 꼴이라 13D·13E 가 그 자리를 쓴다.
     */
    const inFixedSubtree = (node: Element): boolean => {
      for (let link: Element | null = node; link !== null; link = link.parentElement) {
        if (getComputedStyle(link).position === 'fixed') {
          return true;
        }
      }
      return false;
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
        testId: measured.getAttribute('data-testid') ?? '',
        width: Math.round(box.width * 10) / 10,
        // **보이는 띠**의 높이다(v M-8 · H-4) — 조상이 자르면 그만큼만, 스크롤러 안이면 그 상자
        // 높이까지만 한 번에 닿는다.
        height: Math.max(0, Math.round(Math.min(ancestry.bottom - ancestry.top, ancestry.cap) * 10) / 10),
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
         * **손가락으로 밀 수 있어야 예외다**(13B v L-4 · v M-2). `touch-action` 의 유효값은
         * **사슬의 교집합**이라 스크롤러 하나만 보면 안쪽이 막아 둔 자리를 놓친다 — 타깃부터 그
         * 스크롤러까지 모두 가로 밀기를 허용할 때만 예외가 선다.
         */
        let pannableX = true;
        for (let link: Element | null = measured; link !== null; link = link.parentElement) {
          pannableX = pannableX && allowsPan(getComputedStyle(link).touchAction, 'x');
          if (link === parent) {
            break;
          }
        }
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
      /**
       * **세로로도 뷰포트 밖이 있다**(v R1-M2). 보통 흐름의 아래쪽은 밀면 닿으므로 세지 않고,
       * **고정 배치**로 걸친 자리만 센다 — 13B 의 하단 탭이 그 꼴의 자리다.
       */
      if (inFixedSubtree(measured) && (box.top < -0.5 || box.bottom > window.innerHeight + 0.5)) {
        offscreen.push(
          `${measured.tagName.toLowerCase()} [세로 ${Math.round(box.top)}, ${Math.round(box.bottom)}] "${name}"`,
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

/**
 * **내려놓기 두 자리**(M13/13D 의 「나중에」·「건너뜀」)를 고르는 한 자리.
 *
 * 이름으로 고르면 **상태 배지가 같은 이름을 들 때** 그것까지 센다 — 13E 가 2층 라벨을 버튼으로
 * 만들면서 생긴 자리다(「나중에」 상태의 건에서는 배지의 이름이 그 낱말이다). 그러면 두 컨트롤이
 * 사라져도 배지가 그 자리를 메워 **거짓 초록**이 된다.
 *
 * 그래서 **testId 로 고른다** — 그 자리는 `exit-<행동>-<건>` 꼴이고 배지는 그 접두를 갖지 않는다
 * (이 저장소의 「행은 testId 로」와 같은 축). 이름은 그 뒤에 **값으로** 대조한다.
 */
export const EXIT_TARGET_TESTID_PREFIX = 'exit-';

export function exitTargetsOf(targets: readonly MeasuredTarget[]): readonly MeasuredTarget[] {
  return targets.filter((one) => one.testId.startsWith(EXIT_TARGET_TESTID_PREFIX));
}

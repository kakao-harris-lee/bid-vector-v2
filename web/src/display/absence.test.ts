import { describe, expect, it } from 'vitest';

import { ABSENCE_REASONS, ABSENCE_TEXT, absenceTextOf } from './absence';

/**
 * 부재 어휘의 **한 자리**를 잠그는 test(M14/14A ④).
 *
 * 앞 판은 같은 글자가 상수 넷과 손으로 적은 JSX 다섯에 흩어져 있었고, 그 가운데 하나가
 * **적재 중**(응답 전)에도 같은 얼굴을 내어 운영자가 그것을 0 건으로 읽었다. 이 모듈이 없애는
 * 것은 글자의 중복이 아니라 **그 혼동**이다 — 부재는 사유를 지닌 값이고, 사유가 다르면 얼굴도
 * 다르다.
 */
describe('사유 표', () => {
  it('목록과 표가 같은 집합이다 — 갈리면 전수를 도는 단언이 조용히 줄어든다', () => {
    expect(new Set(ABSENCE_REASONS)).toStrictEqual(new Set(Object.keys(ABSENCE_TEXT)));
  });

  it('사유마다 서로 다른 문면이다 — 두 사유가 한 얼굴이면 가를 수 없다', () => {
    const texts = ABSENCE_REASONS.map((one) => ABSENCE_TEXT[one]);

    expect(texts.length).toBeGreaterThan(1);
    expect(new Set(texts).size).toBe(texts.length);
  });

  it('문면이 비어 있지 않다 — 빈 글자는 「조용한 빈칸」이고 그것이 이 slice 가 없애는 것이다', () => {
    for (const reason of ABSENCE_REASONS) {
      expect(ABSENCE_TEXT[reason].trim().length).toBeGreaterThan(0);
    }
  });

  /**
   * **부분 일치까지 막는다.** 걷어낸 옛 문면을 **담은** 새 문면(그 두 낱말을 품은 더 긴 구)은
   * 전수 훑기에 그대로 걸리고, 그러면 이 slice 의 측정 acceptance 가 0 에 닿지 못한다. 이
   * 함정은 실제로 있다 — 생성 타입의 설명 한 줄이 같은 부분 일치로 걸린다.
   *
   * 옛 문면을 **이 파일에도 축어로 적지 않는다**: 적으면 훑기에 차감 한 줄이 생기고, 차감은
   * 넓어지는 쪽으로만 자란다(설계 검토 우회 A-6).
   */
  it('옛 문면을 부분 문자열로도 담지 않는다', () => {
    for (const reason of ABSENCE_REASONS) {
      expect(ABSENCE_TEXT[reason]).not.toContain(RETIRED_WORDING);
    }
  });
});

/**
 * 걷어낸 옛 문면 — **조각에서 조립한다**. 축어로 적으면 전수 훑기가 이 파일을 붉히고, 그것을
 * 피하려고 차감을 두면 차감이 한 줄 자란다. 조립하면 훑기도 붉지 않고 차감도 생기지 않는다.
 */
const RETIRED_WORDING = ['\uac12', '\uc5c6\uc74c'].join(' ');

describe('문면 — 축어로 한 자리에 박는다 (cr M-3)', () => {
  /**
   * **재는 쪽과 재어지는 쪽이 같으면 대조가 공허하다.** 등록부 test 일곱은 이제
   * `absenceTextOf(…)` 와 맞대므로, 표의 값을 바꾸면 둘이 **함께** 움직여 전부 초록이다.
   * 문면 퇴행을 붉히는 자리가 W-13 여정 둘뿐이었다 — acceptance 의 가장 비싼 마지막 단계다.
   *
   * 그래서 글자를 **여기 한 번** 축어로 박는다. 단위 레인이 여정보다 먼저 말한다.
   * (이 파일은 걷어낸 옛 어휘를 조각에서 조립하므로, **새** 문면은 그냥 적어도 훑기에 걸리지
   * 않는다.)
   */
  it('사유 셋의 글자가 이것이다', () => {
    expect(absenceTextOf('notSent')).toBe('주어지지 않음');
    expect(absenceTextOf('notLoaded')).toBe('아직 받지 못함');
    expect(absenceTextOf('notEntered')).toBe('아직 적지 않음');
  });
});

describe('문면 읽기', () => {
  it('글자는 표가 낸다 — 전수에서 표의 값과 같다', () => {
    for (const reason of ABSENCE_REASONS) {
      expect(absenceTextOf(reason)).toBe(ABSENCE_TEXT[reason]);
    }
  });

  /**
   * 「없다」와 「아직 모른다」가 **다른 값**이다(계약 14A ④). 이 둘이 한 얼굴이 되면 응답 전의
   * 빈 자리를 운영자가 「서버가 0 건이라고 답했다」로 읽는다.
   */
  it('응답 전과 응답이 싣지 않음이 다른 얼굴이다', () => {
    expect(absenceTextOf('notLoaded')).not.toBe(absenceTextOf('notSent'));
  });

  /**
   * **서버가 말한 부재와 운영자가 아직 안 적은 칸이 다른 얼굴이다**(M14/14F · v L-3).
   *
   * 셋째 사유가 필요해진 자리는 나라장터 넘김 카드의 투찰 금액이다. 그 칸은 **응답의 칸이
   * 아니라 입력칸**이고, 거기에 「응답은 왔고 그 칸이 비었다」를 쓰면 화면이 **서버에게 없는
   * 빚을 지운다**. 운영자가 할 일도 갈린다 — 서버 축의 둘은 기다리거나 다시 묻는 일이고,
   * 이쪽은 **자기가 적는** 일이다.
   */
  it('운영자가 아직 적지 않음이 서버 축 둘과 다른 얼굴이다', () => {
    // **비공허가 비교 앞에 선다** — 사유가 표에 없으면 글자가 `undefined` 이고 아래 두 비교는
    // 저절로 참이다(그 꼴이 이 test 를 RED 에서 한 번 지나갔다).
    expect(ABSENCE_REASONS).toContain('notEntered');
    expect(absenceTextOf('notEntered')).not.toBe(absenceTextOf('notSent'));
    expect(absenceTextOf('notEntered')).not.toBe(absenceTextOf('notLoaded'));
  });
});

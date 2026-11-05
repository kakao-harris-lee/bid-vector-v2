import { describe, expect, it } from 'vitest';

import {
  BID_CASE_STATE_DISPLAY,
  BID_COMMAND_DISPLAY,
  BID_KIND_DISPLAY,
  NEXT_CHECKPOINT_DISPLAY,
  TIMELINE_ACTOR_DISPLAY,
  commandDisplayOf,
  nextActionOf,
} from './caseDisplay';
import { displayOrUnknown } from './stateDisplay';

describe('입찰 건 상태 표시 표 — 계약 enum 전수', () => {
  it('상태 열넷이 전부 표에 있다', () => {
    expect(Object.keys(BID_CASE_STATE_DISPLAY).sort()).toStrictEqual(
      [
        'WATCHING',
        'DEFERRED',
        'PRICE_RECEIVED',
        'BID_PLANNED',
        'BID_RECORDED',
        'AWAITING_OPENING',
        'OPENED_PROVISIONAL_FIRST',
        'OPENED_OUT_OF_RANK',
        'UNDER_QUALIFICATION_REVIEW',
        'WON',
        'LOST',
        'FAILED_OR_REBID',
        'SKIPPED',
        'DEADLINE_PASSED_UNRECORDED',
      ].sort(),
    );
  });

  it('「보류」는 어휘에 없다 — 「나중에」와 「건너뜀」으로 갈린다(§6.1)', () => {
    const labels = Object.values(BID_CASE_STATE_DISPLAY).map((entry) => entry.label);

    expect(labels).not.toContain('보류');
    expect(displayOrUnknown(BID_CASE_STATE_DISPLAY, 'DEFERRED').label).toBe('나중에');
    expect(displayOrUnknown(BID_CASE_STATE_DISPLAY, 'SKIPPED').label).toBe('건너뜀');
  });

  it('다음 확인 시점 여섯이 전부 표에 있고 「없음」이 빈 값이 아니다', () => {
    expect(Object.keys(NEXT_CHECKPOINT_DISPLAY).sort()).toStrictEqual(
      ['DEADLINE', 'OPENING', 'DOCUMENT_DUE', 'NOTICE_EXPECTED', 'REMINDER', 'NONE'].sort(),
    );
    expect(displayOrUnknown(NEXT_CHECKPOINT_DISPLAY, 'NONE').label).toBe('없음');
  });

  it('페이퍼와 실투찰이 같은 어휘를 쓰되 표시가 다르다', () => {
    expect(Object.keys(BID_KIND_DISPLAY).sort()).toStrictEqual(['REAL', 'PAPER'].sort());
    expect(displayOrUnknown(BID_KIND_DISPLAY, 'PAPER').label).not.toBe(displayOrUnknown(BID_KIND_DISPLAY, 'REAL').label);
  });

  it('타임라인 주체 둘이 표에 있다', () => {
    expect(Object.keys(TIMELINE_ACTOR_DISPLAY).sort()).toStrictEqual(['USER', 'SYSTEM'].sort());
  });
});

/**
 * 타임라인 **명령 어휘**의 전수(M9/web-followups, `OPEN-M9-TIMELINE-COMMAND-WORDING`). 앞 판은
 * 이 칸이 서버가 준 토큰 원문이었다(`ADD_TO_BID_BOX` 꼴) — 상태·주체·부가 사실은 이미 표를
 * 지나는데 명령만 남았다.
 *
 * 전수 열다섯 = 전이표의 열 키 열넷 + **건을 여는 명령 하나**. 여는 명령은 출발 상태가 없어
 * 전이표의 행이 될 수 없고 타임라인에는 생성 전이로 남는다 — 그 어휘가 `ADD_TO_BID_BOX` 다.
 */
describe('타임라인 명령 어휘 — 전수 열다섯', () => {
  it('전이 명령 열넷과 여는 명령 하나가 전부 표에 있다', () => {
    expect(Object.keys(BID_COMMAND_DISPLAY).sort()).toStrictEqual(
      [
        'ADD_TO_BID_BOX',
        'DEFER',
        'RESUME',
        'RECEIVE_PRICE_MENU',
        'DRAFT_BID',
        'RECORD_BID',
        'AWAIT_OPENING',
        'RECORD_OPENING_FIRST',
        'RECORD_OPENING_OUT_OF_RANK',
        'ENTER_QUALIFICATION_REVIEW',
        'RECORD_OUTCOME',
        'CORRECT_OUTCOME',
        'SKIP',
        'MARK_DEADLINE_PASSED',
        'REVOKE_DEADLINE_PASSED',
      ].sort(),
    );
  });

  /** 문면이 **토큰과 다른 글자**여야 이 표에 뜻이 있다 — 같으면 원문을 그린 것과 구별되지 않는다. */
  it('어느 문면도 토큰 원문이 아니다', () => {
    for (const [token, display] of Object.entries(BID_COMMAND_DISPLAY)) {
      expect(display.label, `${token} 의 문면이 토큰 그대로다`).not.toBe(token);
      expect(display.label).not.toMatch(/[A-Z]{2,}_/u);
    }
  });

  /**
   * **개찰 결과 둘은 다른 말을 쓴다.** 잠정 1위와 순위 밖을 한 문구로 접으면 타임라인이 그
   * 갈림을 지운다(SET-02 와 같은 축).
   */
  it('같은 문면을 두 토큰에 쓰지 않는다', () => {
    const labels = Object.values(BID_COMMAND_DISPLAY).map((one) => one.label);

    expect(new Set(labels).size).toBe(labels.length);
  });

  /**
   * 「제출함」·「기록 갱신」·「소급 확인」은 **한 명령**이고 무엇이 달랐는지는 출발 상태가 적는다
   * (도메인이 명령을 상태별로 쪼개지 않는다). 그 사실이 문면에 서 있다.
   */
  it('한 명령이 여러 동작인 자리는 그 사실을 아래층에 적는다', () => {
    expect(BID_COMMAND_DISPLAY.RECORD_BID.why).toBeDefined();
    expect(BID_COMMAND_DISPLAY.DRAFT_BID.why).toBeDefined();
  });

  /** 계약이 이 칸을 `string` 으로 내므로 키 좁힘은 한 자리다 — 표 밖 값은 **보이게** 낸다. */
  it('표 밖 토큰은 보이는 미지 상태로 낸다 — 조용한 빈칸이 아니다', () => {
    const unknown = commandDisplayOf('MINT_MONEY');

    expect(unknown.label).toContain('MINT_MONEY');
    expect(unknown.tone).toBe('unjudged');
  });

  it('표 안 토큰은 표의 행을 낸다', () => {
    expect(commandDisplayOf('ADD_TO_BID_BOX')).toBe(BID_COMMAND_DISPLAY.ADD_TO_BID_BOX);
  });
});

/**
 * §6.5 「다음 행동 하나가 화면을 지배한다」 — primary 버튼은 **그 상태의 다음 전이**다. 표를
 * 상태별로 두므로 「어느 상태에서 어느 버튼」이 코드 분기가 아니라 데이터다.
 */
describe('다음 행동 하나', () => {
  it('담긴 건의 다음 행동은 투찰가 요청이다', () => {
    expect(nextActionOf('WATCHING')?.label).toBe('투찰가 요청');
  });

  it('투찰 예정의 다음 행동은 「제출함」 기록이다', () => {
    expect(nextActionOf('BID_PLANNED')?.label).toBe('제출함');
  });

  /**
   * Phase 1 실측 — 투찰가 요청은 건의 상태를 바꾸지 않는다(요청 뒤에도 `WATCHING`). 그래서
   * 「요청했는가」가 둘째 축이고, 같은 상태에서 다음 행동이 갈린다.
   */
  it('투찰가를 손에 쥐면 같은 상태에서 다음 행동이 초안으로 바뀐다', () => {
    expect(nextActionOf('WATCHING', false)?.kind).toBe('REQUEST_PRICE');
    expect(nextActionOf('WATCHING', true)?.kind).toBe('DRAFT_BID');
  });

  it('투찰가를 쥐어도 종료 상태에는 다음 행동이 생기지 않는다', () => {
    expect(nextActionOf('WON', true)).toBeNull();
    expect(nextActionOf('BID_RECORDED', true)).toBeNull();
  });

  it('이 slice 가 전이를 만들지 않는 상태에는 다음 행동이 없다 — 버튼을 지어내지 않는다', () => {
    expect(nextActionOf('WON')).toBeNull();
    expect(nextActionOf('SKIPPED')).toBeNull();
  });

  it('모든 상태에 답이 있다 — 「모르는 상태」가 없다', () => {
    for (const state of Object.keys(BID_CASE_STATE_DISPLAY)) {
      expect(() => nextActionOf(state as keyof typeof BID_CASE_STATE_DISPLAY)).not.toThrow();
    }
  });
});

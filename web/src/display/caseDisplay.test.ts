import { describe, expect, it } from 'vitest';

import type { components } from '../api/generated/operator-api';

import {
  BID_CASE_STATE_DISPLAY,
  BID_COMMAND_DISPLAY,
  BID_KIND_DISPLAY,
  NEXT_CHECKPOINT_DISPLAY,
  TIMELINE_ACTOR_DISPLAY,
  commandDisplayOf,
  nextActionOf,
  stateDisplayOf,
  type BidCommandToken,
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
 *
 * M10/10B 로 표의 전수성이 **계약에서 온다**(`satisfies Record<BidCommandToken, …>` 의 유니온이
 * 생성 타입 유도다 — 상태 축과 같은 꼴). 아래 목록은 그 유도와 **독립인 명세**로 남긴다.
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

  /**
   * **계약이 이 칸의 어휘를 닫고 있다**(M10/10B). 표의 전수는 `satisfies Record<BidCommandToken, …>`
   * 이 지는데, 계약이 그 칸을 다시 `type: string` 으로 내리면 `BidCommandToken` 이 `string` 으로
   * 넓어지고 그 전수 검사는 **아무것도 막지 않으면서 초록**이 된다 — 생성 타입 유도가 그 변이를
   * 조용히 통과시키는 유일한 길이다. 그 길을 타입으로 닫는다: `string` 이 들어오는 순간 아래
   * 별칭이 `never` 가 되어 **컴파일되지 않는다**(W-3).
   *
   * `@ts-expect-error` 로는 쓸 수 없다 — eslint 가 `src/**` 에서 세 형태를 전부 막는다(W-2).
   */
  it('명령 어휘가 `string` 보다 좁다 — 계약이 어휘를 닫고 있다', () => {
    type NarrowerThanString<T extends string> = string extends T ? never : T;
    const closedVocabulary: NarrowerThanString<BidCommandToken> = 'ADD_TO_BID_BOX';

    expect(closedVocabulary).toBe('ADD_TO_BID_BOX');
  });

  /**
   * **전수성을 별칭이 아니라 계약에 묶는다**(v M-1). 표의 전수는 `satisfies Record<BidCommandToken, …>`
   * 이 지는데, 그 별칭이 **생성 타입 유도라는 사실을 잠그는 구조가 없었다**: 별칭을 리터럴 손
   * 유니온으로 되돌리면 lint·typecheck·W-5·W-6 이 전부 초록이고(실측), 그 뒤 계약에 명령이 늘면
   * web 이 그것을 「알 수 없는 상태」로 **조용히 degrade** 한다 — 10B 가 걷은 교차언어 명령 축이
   * 메우던 바로 그 구멍이 별칭 한 줄 뒤에 남아 있었다.
   *
   * 그래서 **별칭을 거치지 않고** 생성 타입 경로에서 바로 뽑아 표의 키 집합과 맞댄다. 등식은
   * **양방향**이다 — 한쪽 포함만 재면 반대쪽이 줄어든 것을 보지 못한다. 튜플로 감싸는 것은
   * 맨 `extends` 가 유니온을 갈래별로 분배해 판정하는 것을 막기 위해서다.
   */
  it('표의 키 집합이 계약의 명령 어휘와 **같다** — 별칭을 거치지 않는다', () => {
    type ContractCommand = components['schemas']['BidTimelineEntry']['command'];
    type TableKey = keyof typeof BID_COMMAND_DISPLAY;
    type MutuallySame<A, B> = [A] extends [B] ? ([B] extends [A] ? true : false) : false;

    const sameSet: MutuallySame<TableKey, ContractCommand> = true;

    expect(sameSet).toBe(true);
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

  /**
   * 계약이 어휘를 닫은 뒤에도 조회는 **런타임의 자유 문자열**을 받는다 — 번들이 아직 모르는 값이
   * 닿는 경로(배포 비틀림)가 남아 있다. 표 밖 값은 **보이게** 낸다.
   */
  it('표 밖 토큰은 보이는 미지 상태로 낸다 — 조용한 빈칸이 아니다', () => {
    const unknown = commandDisplayOf('MINT_MONEY');

    expect(unknown.label).toContain('MINT_MONEY');
    expect(unknown.tone).toBe('unjudged');
  });

  /**
   * **`Object.prototype` 의 키도 표 밖이다**(PR #132 cr L-3). 맨 색인 읽기(`table[value]`)는
   * `toString`·`constructor`·`__proto__` 에서 **프로토타입의 값**을 내고, 그것이 폴백을 삼켜
   * `label`·`tone` 이 `undefined` 인 「상태」가 화면까지 간다 — 배지가 아무것도 그리지 않는
   * **조용한 빈 자리**가 그 입력에서 되살아난다. 조회가 런타임의 자유 문자열을 받는 자리라
   * 계약이 어휘를 닫은 뒤에도 이 입력은 그대로 닿는다.
   */
  it('프로토타입 키도 보이는 미지 상태로 낸다', () => {
    for (const key of ['toString', 'constructor', 'valueOf', 'hasOwnProperty', '__proto__']) {
      const unknown = commandDisplayOf(key);

      expect(unknown.label, `${key} 가 프로토타입의 값을 냈다`).toContain(key);
      expect(unknown.tone, `${key} 의 어조가 미지가 아니다`).toBe('unjudged');
    }
  });

  it('표 안 토큰은 표의 행을 낸다', () => {
    expect(commandDisplayOf('ADD_TO_BID_BOX')).toBe(BID_COMMAND_DISPLAY.ADD_TO_BID_BOX);
  });
});

/**
 * 타임라인 **상태 두 칸**(출발·도착)의 전수(M11/11B, `OPEN-M10-TIMELINE-STATE-OPENAPI-ENUM`).
 * 앞 판은 그 두 칸이 계약에서 `type: string` 이었다 — 같은 응답 계열의 건 상태 칸에는 이미
 * `enum` 이 있어 **한 응답 안에서 선언이 갈려 있었고**, 화면은 표 밖 값에 서버가 준 코드를
 * 그대로 세웠다.
 *
 * 계약이 세 칸을 닫은 뒤로 전수성의 출처는 **생성 타입**이다 — 명령 축(M10/10B)과 같은 자리가
 * 됐고, Kotlin 어휘와의 등식은 계약 test 층이 든다.
 */
describe('타임라인 상태 어휘 — 계약이 닫은 한 집합', () => {
  /**
   * **어휘를 다시 열면 이 자리가 컴파일되지 않는다.** 표의 전수는 `satisfies Record<BidCaseState, …>`
   * 가 지는데, 계약이 그 칸들을 `type: string` 으로 내리면 유도 유니온이 `string` 으로 넓어지고
   * 전수 검사가 **아무것도 막지 않으면서 초록**이 된다. 그 길을 타입으로 닫는다.
   *
   * `@ts-expect-error` 로는 쓸 수 없다 — eslint 가 `src/**` 에서 세 형태를 전부 막는다(W-2).
   */
  it('상태 어휘가 `string` 보다 좁다 — 출발·도착 두 칸 모두', () => {
    type NarrowerThanString<T extends string> = string extends T ? never : T;
    type ContractToState = components['schemas']['BidTimelineEntry']['toState'];
    type ContractFromState = NonNullable<components['schemas']['BidTimelineEntry']['fromState']>;

    const closedToState: NarrowerThanString<ContractToState> = 'WATCHING';
    const closedFromState: NarrowerThanString<ContractFromState> = 'WATCHING';

    expect([closedToState, closedFromState]).toStrictEqual(['WATCHING', 'WATCHING']);
  });

  /**
   * **전수성을 별칭이 아니라 계약에 묶는다**(M10/10B v M-1 의 교훈 — 별칭을 손 유니온으로
   * 되돌리는 변이가 모든 게이트를 지나갔다). 그래서 별칭을 거치지 않고 생성 타입 경로에서 바로
   * 뽑아 표의 키 집합과 맞댄다. 등식은 **양방향**이다 — 한쪽 포함만 재면 반대쪽이 줄어든 것을
   * 보지 못한다. 튜플로 감싸는 것은 맨 `extends` 가 유니온을 갈래별로 분배하는 것을 막는다.
   *
   * 세 칸을 한 자리에서 맞대는 이유: 「한 응답 안에서 선언이 갈려 있다」가 이 OPEN 의 문면이고,
   * 갈림을 닫는 것은 **셋이 같은 집합임을 재는 것**이다.
   */
  it('표의 키 집합이 상태 세 칸과 **같다** — 별칭을 거치지 않는다', () => {
    type TableKey = keyof typeof BID_CASE_STATE_DISPLAY;
    type MutuallySame<A, B> = [A] extends [B] ? ([B] extends [A] ? true : false) : false;

    const sameAsToState: MutuallySame<TableKey, components['schemas']['BidTimelineEntry']['toState']> = true;
    const sameAsFromState: MutuallySame<
      TableKey,
      NonNullable<components['schemas']['BidTimelineEntry']['fromState']>
    > = true;
    const sameAsCaseState: MutuallySame<TableKey, components['schemas']['BidCase']['state']> = true;

    expect([sameAsToState, sameAsFromState, sameAsCaseState]).toStrictEqual([true, true, true]);
  });

  /**
   * **출발 칸의 `null` 은 어휘를 닫아도 남는다** — 건을 여는 전이에는 출발 상태가 없고, 그
   * 사실이 응답에 `null` 로 실린다. 어휘에서 그 값을 빠뜨리면 엄격한 검증기가 실제로 나가는
   * 값을 거부한다(계약 쪽 짝은 계약 test 가 든다).
   */
  it('출발 칸은 생성 전이의 null 을 계속 받는다', () => {
    const openingTransition: components['schemas']['BidTimelineEntry']['fromState'] = null;

    expect(openingTransition).toBeNull();
  });

  /**
   * 계약이 어휘를 닫은 뒤에도 조회는 **런타임의 자유 문자열**을 받는다(배포 비틀림). 표 밖 값은
   * **보이게** 낸다 — 서버가 준 코드를 문면 자리에 그대로 세우면 화면이 계약에 없는 상태를 아는
   * 것처럼 보인다(M11/11B D-11B-3 — 그 자리가 이 모듈 밖에 하나 있었다).
   *
   * 표본은 **도메인이 명시적으로 거부한 어휘**다(「보류」는 어휘에 없다 — 「나중에」와 「건너뜀」
   * 으로 갈렸다). 상류가 그 이름을 채택하면 음성 대조가 조용히 공허해지는 자리를 피한다.
   */
  it('표 밖 상태는 보이는 미지 상태로 낸다 — 코드 원문이 아니다', () => {
    const unknown = stateDisplayOf('ON_HOLD');

    expect(unknown.label).toContain('ON_HOLD');
    expect(unknown.label, '코드 원문이 문면 자리에 그대로 섰다').not.toBe('ON_HOLD');
    expect(unknown.tone).toBe('unjudged');
  });

  /**
   * **`Object.prototype` 의 키도 표 밖이다.** 맨 색인 읽기(`table[value]`)는 `toString`·
   * `constructor`·`__proto__` 에서 프로토타입의 값을 내고, 그것이 폴백을 삼켜 `label`·`tone` 이
   * `undefined` 인 「상태」가 화면까지 간다. 조회가 런타임의 자유 문자열을 받는 자리라 계약이
   * 어휘를 닫은 뒤에도 이 입력은 그대로 닿는다.
   */
  it('프로토타입 키도 보이는 미지 상태로 낸다', () => {
    for (const key of ['toString', 'constructor', 'valueOf', 'hasOwnProperty', '__proto__']) {
      const unknown = stateDisplayOf(key);

      expect(unknown.label, `${key} 가 프로토타입의 값을 냈다`).toContain(key);
      expect(unknown.tone, `${key} 의 어조가 미지가 아니다`).toBe('unjudged');
    }
  });

  it('표 안 상태는 표의 행을 낸다', () => {
    expect(stateDisplayOf('WATCHING')).toBe(BID_CASE_STATE_DISPLAY.WATCHING);
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

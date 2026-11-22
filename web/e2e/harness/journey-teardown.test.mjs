import { describe, expect, it } from 'vitest';

import { teardownMessage, teardownStep, thrownDiagnostic } from '../journey/teardown.ts';

/**
 * **여정 teardown 이 던진 것의 진단을 보존하는가**(M15/15I ⓒ · `OPEN-M15-TEST-RESIDUE`).
 *
 * 왜 단위 test 인가: 여정 레인의 **초록 실행에는 teardown 이 던지는 경로가 없다**. 되돌림이
 * 성공하는 한 감싸개의 `catch` 는 한 번도 돌지 않으므로, 거기서 스택을 버리든 보존하든 그
 * 실행은 똑같이 초록이다 — 방어 부재와 미측정이 같은 값이 되는 꼴이고, 그 꼴은 같은 파일의
 * 누출 술어가 이미 한 번 밟은 자리다. 그래서 그 감싸개를 spec 밖 모듈로 두고 **여기서** 잰다.
 *
 * 선례: 같은 디렉터리의 `exit-targets.test.mjs` 가 `../journey/` 의 술어를 같은 방식으로 잠근다.
 */
describe('teardown 걸음 감싸개', () => {
  it('성공한 걸음은 값만 내고 문면·진단이 없다', async () => {
    const outcome = await teardownStep('무엇을 하다', async () => '값');
    expect(outcome).toStrictEqual({ value: '값', failure: null, detail: null });
  });

  it('던지면 값 대신 문면이 서고 **던지지 않는다**', async () => {
    const outcome = await teardownStep('되돌리다', async () => {
      throw new Error('전송이 끊겼다');
    });
    expect(outcome.value).toBeNull();
    expect(outcome.failure).toBe('되돌리다: 전송이 끊겼다');
  });

  /**
   * **문면은 짧다** — 머리말에 스택이 실리면 teardown 실패 하나가 리포트를 덮고, 그 교환이
   * 바로 ⓒ 를 결정 사안으로 만든 자리다. 머리말은 한 줄이고 진단은 다른 칸에 선다.
   */
  it('문면은 한 줄이고 스택을 담지 않는다', async () => {
    const outcome = await teardownStep('되돌리다', async () => {
      throw new Error('전송이 끊겼다');
    });
    expect(outcome.failure).not.toContain('\n');
    expect(outcome.failure).not.toContain('journey-teardown.test');
  });

  it('진단에 스택이 남는다', async () => {
    const outcome = await teardownStep('되돌리다', async () => {
      throw new Error('전송이 끊겼다');
    });
    // 스택 한 줄의 꼴(`at …`)과 **이 파일 이름**이 둘 다 있어야 「어디서 났는가」가 선다.
    expect(outcome.detail).toContain('Error: 전송이 끊겼다');
    expect(outcome.detail).toMatch(/\n\s*at /u);
    expect(outcome.detail).toContain('journey-teardown.test');
  });

  it('진단에 cause 사슬이 끝까지 남는다', async () => {
    const outcome = await teardownStep('되돌리다', async () => {
      throw new Error('바깥', {
        cause: new Error('가운데', { cause: '맨 안쪽은 Error 가 아니다' }),
      });
    });
    expect(outcome.detail).toContain('Error: 바깥');
    expect(outcome.detail).toContain('cause[1]: Error: 가운데');
    expect(outcome.detail).toContain('cause[2]: 맨 안쪽은 Error 가 아니다');
  });

  it('Error 가 아닌 것을 던져도 문면과 진단이 선다', async () => {
    // 하네스가 받는 것이 `Error` 만은 아니다 — 그 사실을 잰다.
    const outcome = await teardownStep('되돌리다', async () => {
      throw '문자열을 던졌다';
    });
    expect(outcome.failure).toBe('되돌리다: 문자열을 던졌다');
    expect(outcome.detail).toBe('문자열을 던졌다');
  });
});

describe('cause 사슬 가드', () => {
  it('사슬이 자기를 가리켜도 멈춘다', () => {
    const looping = new Error('자기를 가리킨다');
    looping.cause = looping;
    const detail = thrownDiagnostic(looping);
    expect(detail).toContain('cause 사슬이 이미 지난 것을 가리킨다');
  });

  it('사슬이 길면 상한에서 잘렸다고 말한다', () => {
    let deepest = new Error('바닥');
    for (let index = 0; index < 20; index += 1) {
      deepest = new Error(`층 ${index}`, { cause: deepest });
    }
    const detail = thrownDiagnostic(deepest);
    expect(detail).toContain('걸음에서 잘렸다');
    // **잘려도 윗부분은 남는다** — 자르기가 진단을 통째로 버리는 것과 구별된다.
    expect(detail).toContain('cause[1]: Error: 층 18');
  });
});

describe('단언 문면 합치기', () => {
  it('진단이 없으면 머리말 그대로다', () => {
    expect(teardownMessage('되돌릴 수 없었다', { value: null, failure: null, detail: null })).toBe(
      '되돌릴 수 없었다',
    );
  });

  it('진단이 있으면 머리말이 **첫 줄**이고 그 아래에 붙는다', () => {
    const message = teardownMessage('되돌릴 수 없었다', {
      value: null,
      failure: '되돌리다: 전송이 끊겼다',
      detail: 'Error: 전송이 끊겼다\n    at somewhere',
    });
    expect(message.split('\n')[0]).toBe('되돌릴 수 없었다');
    expect(message).toContain('at somewhere');
  });
});

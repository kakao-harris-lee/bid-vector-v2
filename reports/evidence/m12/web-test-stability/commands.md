# M12 / web-test-stability — 실행 명령과 종료 코드

출력 전문을 붙이지 않는다. 핵심 결과는 한 줄이고, **그 수를 낸 조건(표적·부하·횟수)을 같은
줄에 적는다** — 구조나 부하가 바뀌면 다시 재야 하는 수이기 때문이다.

## ② 게이트 test 칸 예산 — 조건 셋, 각 전건 vitest 10회

측정 하네스는 레인 scratch 에 둔다(산출물이 아니다). 세 조건이 **같은 명령**을 돌리고 조건만
다르다 — `npx vitest run --reporter=json --outputFile=<run>.json` (987 test / 234 파일, `npm run
test` 와 같은 수집).

### 2026-11-14 조건 A — 16코어, 다른 레인(12C) vitest 와 일부 겹침
- cmd: `measure-cells.sh idle 10`
- exit: 0 (10/10, 실패한 run 0)
- 핵심 결과: 후보 둘의 **첫 칸 4~16ms**, 최대 칸 `kotlin-source-ownership` 1308ms(예산 5000ms 의 3.8배 여유) · `money-vocab-cross-lang` 319ms(15.7배)

### 2026-11-14 조건 B — `taskset -c 0,1`(2코어 고정)
- cmd: `measure-cells.sh cpu2 10 0,1`
- exit: 0 (10/10, 실패한 run 0)
- 핵심 결과: 칸 시간이 **내려간다**(최대 칸 353ms · 48ms) — vitest 가 affinity 를 보고 fork 를 둘만 띄워 칸 안의 경합이 줄기 때문이고, 벽시계는 반대로 10s → 39s 다

### 2026-11-14 조건 C — 전건 vitest **둘 동시**(레인 둘이 겹치는 꼴, fork 배수, loadavg 14~39)
- cmd: `measure-pair.sh pair 10`
- exit: 0 (10/10, primary·noise 모두 실패한 run 0)
- 핵심 결과: 첫 칸은 여전히 **6~22ms**, 최대 칸 `kotlin-source-ownership` 2342ms(2.1배 여유) · `money-vocab-cross-lang` 421ms(11.9배) · 대조로 둔 `display-gate-selectors`(11E 가 고친 파일) 1042ms(4.8배)

판정 근거는 `checklist.md` ② 가 정본이다. 요지: **구속 조건은 CPU 희소가 아니라 fork 과다구독**
이고(조건 B 가 그것을 가른다), 후보 둘의 **첫 칸은 어느 조건에서도 기동 비용을 지지 않는다** —
둘 다 `typescript` 를 모듈 꼭대기에서 들여 그 적재가 이미 칸 예산 밖이다.

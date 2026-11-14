# M12 / gates-web-read-3 — rollback

되돌리는 방법은 **in_scope 경로 한정 복원**이다. range revert 를 쓰지 않는다 — 이 slice 의 커밋
집합은 range 가 아니라 **in_scope 경로의 변경**이고, 공유 파일(§공유 파일)이 그 range 안에 남의
줄을 담을 수 있다.

```
git restore --source=<원천> --staged --worktree -- <아래 목록>
```

## 복원 목록 — 기계 산출

`git diff --name-status <base>..HEAD` 의 경로 전수다. **라운드마다 재산출한다.**

**원천 기준으로** `git diff --name-status e07adaa8..HEAD` 가 낸 **24 경로**(신설 9 · 수정 15)이고,
디렉터리 하나로 적는 evidence **다섯**이 그 안에 든다(목록에는 디렉터리 한 줄로 선다).

**r1·r2 에서 바뀌었다.** config 셋(`playwright.config.ts` · `playwright.journey.config.ts` ·
`eslint.config.js`)은 층 import 를 거두고 빈 줄까지 되돌려 base 와 **바이트로 같다** — 그래서
복원 목록에 **없다**(in_scope 에는 남는다: 되돌림 대상이 아니라 「만지지 않았다」가 사실이다).
`vite.config.ts` 는 r2 에 `setupFiles` 한 줄을 지워 **다시 목록에 든다**. `web/.npmrc` 와 탐침
둘(`kotlin-read-probe.mjs` · `e2e/probe-config/…`)이 r1·r2 에서 새로 들어왔다.

```
docs/discovery/capability-map.md
reports/evidence/m12/gates-web-read-3/
web/.npmrc
web/config/gate-tests.txt
web/e2e/harness/journey-up.mjs
web/e2e/harness/opening-axis-extract.mjs
web/e2e/harness/opening-axis.mjs
web/e2e/harness/opening-axis.test.mjs
web/e2e/kotlin-read-guard.spec.ts
web/e2e/probe-config/
web/package.json
web/scripts/kotlin-raw-glob-probe.mjs
web/scripts/kotlin-read-guard-setup.mjs
web/scripts/kotlin-read-guard.mjs
web/scripts/kotlin-read-guard.test.mjs
web/scripts/kotlin-read-probe.mjs
web/scripts/kotlin-source-ownership.test.mjs
web/scripts/kotlin-source.mjs
web/scripts/money-vocab-cross-lang.test.mjs
web/vite.config.ts
```

**등식 둘**(r2 — v M-2 가 이 자리의 결함을 짚었다: 목록이 **손으로 유지되다** `web/vite.config.ts`
를 빠뜨렸고, 그러면 복원 뒤 `setupFiles` 없는 설정이 남아 vitest 가 **4 failed** 다).
- 목록은 `git diff --name-only <원천>..HEAD` 를 **기계로** 접은 것이다(evidence 와 탐침 config 는
  디렉터리 한 줄). 라운드마다 재산출한다.
- 그 목록에서 in_scope 를 뺀 차집합이 **빈 출력**이다(`comm -23`, 재산출 때마다 확인).
in_scope 밖 경로는 하나도 없다 — 이 slice 는 공유 working tree 에서 경로 한정 커밋만 했다.
구현 중 생긴 신설 파일 둘(`web/scripts/kotlin-raw-glob-probe.mjs` ·
`web/e2e/kotlin-read-guard.spec.ts`)은 그 대조에서 드러나 **in_scope 에 등재**했다(scope.md r2).

**evidence 는 디렉터리 하나로 적는다** — 복원이 디렉터리 한정이라 파일 이름을 괄호로 적으면
복원에 쓰이지 않으면서 라운드마다 낡는다.

## 원천 — 고정 SHA 여야 한다

움직이는 `origin/main` 을 쓰지 않는다. 원천은 **이 브랜치에 병합된 마지막 main 커밋**이고, 머지
차례에 **`e07adaa8`**(12B `screen-coverage-3` 머지)로 바뀌었다. **전제**: 그 SHA 에 이 slice 가
없다 — 첫 산출물 커밋이 그 조상에 들어 있지 않다.

**셋째 단언 — 원천이 바뀌었으므로 쟀다.** 복원 대상 blob 이 **착수 base `10d208d2` 와 새 원천
`e07adaa8` 에서 같은가**:

| 복원 대상 | base ↔ 새 원천 |
| --- | --- |
| `docs/discovery/capability-map.md` | **다르다** — 12B 가 §12.3 에 절 `(p)` 를 더했다 |
| 나머지 **열넷**(수정 대상 전부) | 같다 |

그래서 그 파일은 **새 원천 기준으로 다시 셌다**. 복원이 착수 base 를 가리키고 있었다면 그 한 번이
12B 의 절을 지웠을 자리다 — 원천을 고정 SHA 로 두고 바뀔 때마다 이 단언을 재는 규율이 막는 것이
정확히 이것이다.

머지 **뒤** 되돌림은 이 문서가 아니라 **PR revert** 다.

## 완료 판정 — 종료 코드가 아니다

`git restore` 의 exit 0 은 「복원이 돌았다」일 뿐이다. 완료는 **계수**로 센다.

- **내 표지 0**: §12.3 의 `(p)` 절 0 · 신설 ID 둘(`…-RESIDUALS-3` · `…-AST-RESCAN-COST`) 잔존 0 ·
  `.npmrc` 의 `node-options` 선언 0 · 신설 파일 넷(`kotlin-raw-glob-probe.mjs` ·
  `kotlin-read-probe.mjs` · `kotlin-read-guard.spec.ts` · `e2e/probe-config/…`) 부재 · 내 evidence
  디렉터리 부재.
- **남의 표지 보존**: §12.3 의 `(a)`~`(p)` **열여섯**이 그대로(12B 의 `(p)` 와 그 신설 OPEN 넷 포함) ·
  `reports/evidence/` 의 남의 디렉터리 보존(12B 것 포함) · `gate-tests.txt` 등재 수 보존 ·
  `vite.config.ts` 의 `test:` 블록이 **원천의 5 키**로 돌아온다(내가 지운 `setupFiles` 복원).

**항목 집합으로 세고 어휘 grep 으로 세지 않는다** — 상류가 같은 이름을 쓰면 그 대조가 조용히
공허해진다.

## ①~⑥ 실측

**실측 HEAD: `3aaa20e5`** (원천 `e07adaa8`). 버릴 clone 에서 돌았고
레인 저장소를 건드리지 않았다. 갈음은 「HEAD 초록」이 아니라 **트리 동일성**으로만 센다.

**다섯 번 다시 쟀다**(마지막은 r3 의 B-1 수정 뒤다). 목록은 매번 **기계로** 산출하고, r3 부터는 양방향 차집합이 **빈 출력**임과 **원천 대비 트리 전체 diff 0 줄**을 함께 센다. 첫 실측은 `380eb0bd` 에서 돌았고, 그 뒤 **복원 목록 안의 원천에 존재하는
파일**(층과 그 test 의 머리 주석 · 커널 · money-vocab · 등록부 산문)을 고치는 커밋 넷이 왔다 —
낡은 범위 문면을 실제 자리로 정정한 커밋들이다. 그 순간 첫 실측은 **재측정 대상**이 되므로
목록을 재산출하고 ①~⑥ 을 다시 돌렸다(수치는 아래가 정본이고 첫 실측은 옮기지 않았다).

| | 측정 | 값 |
| --- | --- | --- |
| ① | 복원 명령 | exit **0** |
| ② | D/M 계수 | **D 9 · M 15**(신설 아홉이 지워지고 수정 열다섯이 원천으로 돌아간다) |
| ③ | 복원 경로 한정 `diff <원천>` | **0 줄** |
| ③″ | **원천 대비 트리 전체 `diff`**(r2 신설 — 경로 한정 술어는 **목록의 누락을 가린다**) | **0 줄** |
| ③′ | 내 표지 0 | 절 `(p)` **0** · 신설 ID 잔존 **0** · `.npmrc` 선언 **0** · 신설 파일 **넷 전부 부재** · 내 evidence 디렉터리 **부재** · `vite.config.ts` 의 `setupFiles` **복원됨**(내가 지운 줄이 돌아온다) |
| ③′ | 남의 표지 글자 계수 | §12.3 하위 절 **16 개**(a~p — 내 `(q)` 만 빠졌다). **12B 의 신설 OPEN 넷**과 그 evidence 디렉터리 보존 |
| ③′ | 남의 표지 보존 | §12.3 글자 **a~o**, `(n)`·`(o)` 각 **1** · `reports/evidence/` 디렉터리 **14 개 보존** · `gate-tests` **41 등재** · `vite.config` `test:` **5 키** |
| ④ | `npm ci` · `typecheck` · `build` | exit **0 · 0 · 0** |
| ⑤ | **빈 트리 `npm ci`** 뒤 `vitest run` | **44 파일 · 1011 passed**(원천의 수 — 12B 의 칸을 포함한다) |
| ⑥ | `lint`·`gate:tests`·`gate:src-tree`·`api:types:check`·`gate:boundary-canary` | exit **0** 다섯 |

⑤ 의 1011 은 **원천의 수**다 — 이 판의 1025 에서 내가 더한 칸들이 사라진 값이고, 그 감소 자체가
「내 표지가 사라졌다」의 또 다른 계수다. **파일 수는 44 로 같다** — 이 slice 는 새 vitest test
파일을 만들지 않았고(신설은 playwright spec 과 탐침 모듈이다) 그 사실이 이 줄로 선다.

**재측정 사유 넷**: 첫 실측(`380eb0bd`) 뒤 낡은 범위 문면을 고치는 커밋이 왔고, r1 수정(v H-1 등)이
다시 복원 목록 안 파일을 바꿨고, r2 수정(cr HIGH 등)이 또 바꿨고, **머지 차례의 병합이 원천을
바꿨다**. **네 번 다 목록을 재산출하고 ①~⑥ 을 다시** 돌렸다 — 앞 실측 수치는 옮기지 않았다.

**실측 뒤 이 문서와 남은 evidence 만 커밋했다.** 복원 목록 안의 **원천에 존재하는 파일**은 실측
뒤 건드리지 않았다 — 건드리면 재측정이다. 실측 HEAD 와 판정 SHA 사이에서 움직이는 경로는
`reports/evidence/m12/gates-web-read-3/**` 뿐이고, 그것은 **전부 원천에 없는 신설 파일**이다.

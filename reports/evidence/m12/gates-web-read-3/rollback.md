# M12 / gates-web-read-3 — rollback

되돌리는 방법은 **in_scope 경로 한정 복원**이다. range revert 를 쓰지 않는다 — 이 slice 의 커밋
집합은 range 가 아니라 **in_scope 경로의 변경**이고, 공유 파일(§공유 파일)이 그 range 안에 남의
줄을 담을 수 있다.

```
git restore --source=<원천> --staged --worktree -- <아래 목록>
```

## 복원 목록 — 기계 산출

`git diff --name-status <base>..HEAD` 의 경로 전수다. **라운드마다 재산출한다.**

`git diff --name-status 10d208d2..HEAD` 가 낸 **24 경로**(신설 7 · 수정 17)이고, 디렉터리 하나로
적는 evidence **다섯**이 그 안에 든다.

```
docs/discovery/capability-map.md
reports/evidence/m12/gates-web-read-3/
web/config/gate-tests.txt
web/e2e/harness/journey-up.mjs
web/e2e/harness/opening-axis-extract.mjs
web/e2e/harness/opening-axis.mjs
web/e2e/harness/opening-axis.test.mjs
web/e2e/kotlin-read-guard.spec.ts
web/eslint.config.js
web/package.json
web/playwright.config.ts
web/playwright.journey.config.ts
web/scripts/kotlin-raw-glob-probe.mjs
web/scripts/kotlin-read-guard-setup.mjs
web/scripts/kotlin-read-guard.mjs
web/scripts/kotlin-read-guard.test.mjs
web/scripts/kotlin-source-ownership.test.mjs
web/scripts/kotlin-source.mjs
web/scripts/money-vocab-cross-lang.test.mjs
web/vite.config.ts
```

**등식**: 그 목록에서 in_scope 를 뺀 차집합이 **빈 출력**이다(`comm -23`, 재산출 때마다 확인).
in_scope 밖 경로는 하나도 없다 — 이 slice 는 공유 working tree 에서 경로 한정 커밋만 했다.
구현 중 생긴 신설 파일 둘(`web/scripts/kotlin-raw-glob-probe.mjs` ·
`web/e2e/kotlin-read-guard.spec.ts`)은 그 대조에서 드러나 **in_scope 에 등재**했다(scope.md r2).

**evidence 는 디렉터리 하나로 적는다** — 복원이 디렉터리 한정이라 파일 이름을 괄호로 적으면
복원에 쓰이지 않으면서 라운드마다 낡는다.

## 원천 — 고정 SHA 여야 한다

움직이는 `origin/main` 을 쓰지 않는다. 원천은 **이 브랜치에 병합된 마지막 main 커밋**이고, 지금
그것은 착수 base 와 같은 `10d208d2664c6bb87cd7082a2853c08c2c0d3236` 다. **전제**: 그 SHA 에 이
slice 가 없다 — 첫 산출물 커밋이 그 조상에 들어 있지 않다.

**셋째 단언은 오늘 자동으로 선다** — 원천과 착수 base 가 **같은 SHA** 이므로 복원 대상 blob 이
둘에서 같은지 물을 것이 없다. **머지 차례에 main 을 병합하면 원천이 바뀌므로 그때 다시 센다**
(특히 `docs/discovery/capability-map.md` 는 앞 레인이 §12.3 에 절을 더하는 자리라 달라질 것을
전제한다 — M11 에서 실제로 그랬다).

머지 **뒤** 되돌림은 이 문서가 아니라 **PR revert** 다.

## 완료 판정 — 종료 코드가 아니다

`git restore` 의 exit 0 은 「복원이 돌았다」일 뿐이다. 완료는 **계수**로 센다.

- **내 표지 0**: §12.3 의 `(p)` 절 0 · 신설 ID `OPEN-M12-KOTLIN-READ-GATE-RESIDUALS-3` 잔존 0 ·
  신설 파일 둘(`kotlin-raw-glob-probe.mjs` · `kotlin-read-guard.spec.ts`) 부재 · 내 evidence
  디렉터리 부재.
- **남의 표지 보존**: §12.3 의 `(a)`~`(o)` 가 그대로 · `reports/evidence/` 의 남의 디렉터리 보존 ·
  `gate-tests.txt` 등재 수 보존 · `vite.config.ts` 의 `test:` 블록 키 수 보존.

**항목 집합으로 세고 어휘 grep 으로 세지 않는다** — 상류가 같은 이름을 쓰면 그 대조가 조용히
공허해진다.

## ①~⑥ 실측

**실측 HEAD: `76c20620192db2d62aba25685b117e9c65fd82fe`** (원천 `10d208d2`). 버릴 clone 에서 돌았고
레인 저장소를 건드리지 않았다. 갈음은 「HEAD 초록」이 아니라 **트리 동일성**으로만 센다.

**한 번 다시 쟀다.** 첫 실측은 `380eb0bd` 에서 돌았고, 그 뒤 **복원 목록 안의 원천에 존재하는
파일**(층과 그 test 의 머리 주석 · 커널 · money-vocab · 등록부 산문)을 고치는 커밋 넷이 왔다 —
낡은 범위 문면을 실제 자리로 정정한 커밋들이다. 그 순간 첫 실측은 **재측정 대상**이 되므로
목록을 재산출하고 ①~⑥ 을 다시 돌렸다(수치는 아래가 정본이고 첫 실측은 옮기지 않았다).

| | 측정 | 값 |
| --- | --- | --- |
| ① | 복원 명령 | exit **0** |
| ② | D/M 계수 | **D 7 · M 17**(신설 일곱이 지워지고 수정 열일곱이 원천으로 돌아간다) |
| ③ | 복원 경로 한정 `diff <원천>` | **0 줄** |
| ③′ | 내 표지 0 | 절 `(p)` **0** · 신설 ID 잔존 **0** · 신설 파일 둘 **전부 부재** · 내 evidence 디렉터리 **부재** |
| ③′ | 남의 표지 글자 계수 | §12.3 하위 절 **15 개**(a~o — 내 절만 빠졌다) |
| ③′ | 남의 표지 보존 | §12.3 글자 **a~o**, `(n)`·`(o)` 각 **1** · `reports/evidence/` 디렉터리 **14 개 보존** · `gate-tests` **41 등재** · `vite.config` `test:` **5 키** |
| ④ | `npm ci` · `typecheck` · `build` | exit **0 · 0 · 0** |
| ⑤ | `vitest run` | **44 파일 · 987 passed** |
| ⑥ | `lint`·`gate:tests`·`gate:src-tree`·`api:types:check`·`gate:boundary-canary` | exit **0** 다섯 |

⑤ 의 987 은 **원천의 수**다 — 이 판의 994 에서 내가 더한 칸들이 사라진 값이고, 그 감소 자체가
「내 표지가 사라졌다」의 또 다른 계수다. **파일 수는 44 로 같다** — 이 slice 는 새 vitest test
파일을 만들지 않았고(신설은 playwright spec 과 탐침 모듈이다) 그 사실이 이 줄로 선다.

**실측 뒤 이 문서와 남은 evidence 만 커밋했다.** 복원 목록 안의 **원천에 존재하는 파일**은 실측
뒤 건드리지 않았다 — 건드리면 재측정이다. 실측 HEAD 와 판정 SHA 사이에서 움직이는 경로는
`reports/evidence/m12/gates-web-read-3/**` 뿐이고, 그것은 **전부 원천에 없는 신설 파일**이다.

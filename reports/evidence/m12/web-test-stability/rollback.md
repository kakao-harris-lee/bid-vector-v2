# M12 / web-test-stability — 되돌리기

이 slice 는 **test 축이다** — 비활성화할 flag·route·writer 가 없고 출하 코드 변경이 0 이다.
되돌리면 포착이 착수 때의 「경로+메서드+200」 술어로 돌아간다(그 경합도 함께 돌아온다).

## 원천과 전제

- 원천: **이 브랜치가 마지막으로 병합한 main 의 고정 SHA `3fd8f62a`** (움직이는 ref 를 쓰지 않는다).
- 전제: **이 slice 는 아직 main 에 없다.** 머지 뒤의 되돌림은 그 PR 의 머지 커밋 revert 다.
- **셋째 단언** — 복원 대상 blob 이 착수 base 와 새 원천에서 같은가: 여정 파일 넷은 **같고**,
  `docs/discovery/capability-map.md` 는 **다르다**(12B·12C 가 (p)·(q) 를 더했다). 그래서 그 파일은
  **새 원천 기준으로** 센다 — base 로 되돌리면 남의 절 둘이 날아간다.
- 하네스 경로(`CLAUDE.md`·`.claude/**`)는 되돌리지 않는다 — 이 range 에 하네스 커밋이 없다.

## 복원 목록 — 기계 산출

`git diff --name-status 3fd8f62a..HEAD` 로 낸다 — **원천 기준이다**(착수 base `10d208d2` 로 읽으면
12B·12C 가 가져온 파일이 섞인다). **라운드마다 파일이 늘면 이 절차를 다시 돌린다** —
목록이 낡는 것이 이 결함의 실제 원인이다. evidence 는 **디렉터리 하나로** 받으므로 그 안에 파일이
늘어도 목록이 변하지 않는다.

| 상태 | 경로 | 원천에서 |
| --- | --- | --- |
| M | `web/e2e/journey/journey.spec.ts` | base 와 같은 blob |
| M | `web/e2e/journey/onboarding.spec.ts` | base 와 같은 blob |
| M | `web/e2e/journey/results.spec.ts` | base 와 같은 blob |
| M | `web/e2e/journey/shared.ts` | base 와 같은 blob |
| M | `docs/discovery/capability-map.md` | **base 와 다른 blob** — 남의 절 (p)·(q) 가 들어 있다 |
| A | `reports/evidence/m12/web-test-stability/` (디렉터리 — 안의 파일 전부) | 원천에 없다 |

## 명령

```sh
git restore --source=3fd8f62a --staged --worktree -- \
  web/e2e/journey/journey.spec.ts \
  web/e2e/journey/onboarding.spec.ts \
  web/e2e/journey/results.spec.ts \
  web/e2e/journey/shared.ts \
  docs/discovery/capability-map.md \
  reports/evidence/m12/web-test-stability
```

`--source` 에 없는 경로(evidence)는 **삭제**되므로 신규 파일에 별도 `git rm` 이 없다.
공유 파일은 `capability-map.md` 하나인데 **원천이 main 이라 남의 줄을 떨어뜨릴 수 없다** —
hunk 격리가 필요 없는 이유가 이것이다.

## 실측 — 버릴 clone 에서 ①~⑥

실측 HEAD: `3e3fd8e3` — **원천에 있는 파일을 만진 마지막 커밋**이다(§12.3 (r) 을 수정 라운드의
문면으로 고친 커밋). 그 뒤에 오는 커밋은 evidence 뿐이고 **원천에 없는 신설 파일**이다(열거와 원천 부재
확인은 verifier 가 한다 — 2026-11-11 예외). 그 사이에 머지 커밋도 없다.

| # | 무엇 | 결과 |
| --- | --- | --- |
| ① | 복원 명령 종료 코드 | **0** |
| ② | D/M 수 | **D 4 · M 5** (D = evidence 넷, M = 여정 파일 넷 + capability map) |
| ③ | 복원 경로의 `git diff <원천>` | **빈 출력** |
| ③″ | **트리 동일성** — 작업 트리·인덱스 전체의 `git diff 3fd8f62a` | **둘 다 빈 출력** (복원 뒤 트리가 원천과 바이트 동일) |
| 표지 | **내 표지 0** — `#### (r)` 0건 · 이 slice 가 신설한 OPEN ID 둘 **0건** · **남의 표지 보존** — `#### (a)`~`(q)` **17건** · 12C 의 `OPEN-M12-GATE-AST-RESCAN-COST` **2건 보존** | 넷 다 성립 |
| ④ | compile — `typecheck` · `build` | **0 · 0** |
| ⑤ | test — `npm run test`(vitest 전건) | **0** |
| ⑥ | 게이트 — `lint` · `gate:src-tree` · `api:types:check` · `gate:tests` · `gate:boundary-canary` | **전부 0** |

**여정 레인(W-13)과 Kotlin `check` 는 ③″ 로 갈음한다** — 근거는 「HEAD 초록」이 아니라 **트리
동일성**이다. 복원 뒤 트리가 `3fd8f62a`(병합된 main)와 바이트 동일하고 이 slice 의 evidence 도
없으므로 leak-pattern 게이트의 스캔 뿌리까지 원천과 같다.

**예상 복구 시간**: 명령 하나(초 단위), 확인까지 web 게이트 전건 약 2분.

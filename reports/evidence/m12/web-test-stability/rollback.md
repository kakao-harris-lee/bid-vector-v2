# M12 / web-test-stability — 되돌리기

이 slice 는 **test 축이다** — 비활성화할 flag·route·writer 가 없고 출하 코드 변경이 0 이다.
되돌리면 포착이 착수 때의 「경로+메서드+200」 술어로 돌아간다(그 경합도 함께 돌아온다).

## 원천과 전제

- 원천: 착수 base `10d208d2664c6bb87cd7082a2853c08c2c0d3236`.
- 전제: **이 slice 는 아직 main 에 없다.** 머지 뒤의 되돌림은 그 PR 의 머지 커밋 revert 다.
- **공유 파일은 오늘 없다** — `git log --oneline <base>..HEAD -- <각 경로>` 가 이 slice 의 커밋만
  낸다. hunk 격리가 필요 없다. **머지 차례에 `docs/discovery/capability-map.md` 가 들어오면 그때는
  공유 파일이다**: 원천을 그때 병합한 main 의 **고정 SHA** 로 바꾸고(움직이는 ref 금지), 셋째
  단언(복원 대상 blob 이 착수 base 와 새 원천에서 같은가)을 재고, 「내 표지 0 · 남의 표지 보존」을
  계수로 센 뒤 **이 문서의 실측을 다시 돌린다.**
- 하네스 경로(`CLAUDE.md`·`.claude/**`)는 되돌리지 않는다 — 이 range 에 하네스 커밋이 없다.

## 복원 목록 — 기계 산출

`git diff --name-status <base>..HEAD` 로 낸다. **라운드마다 파일이 늘면 이 절차를 다시 돌린다** —
목록이 낡는 것이 이 결함의 실제 원인이다. evidence 는 **디렉터리 하나로** 받으므로 그 안에 파일이
늘어도 목록이 변하지 않는다.

| 상태 | 경로 |
| --- | --- |
| M | `web/e2e/journey/journey.spec.ts` |
| M | `web/e2e/journey/onboarding.spec.ts` |
| M | `web/e2e/journey/results.spec.ts` |
| M | `web/e2e/journey/shared.ts` |
| A | `reports/evidence/m12/web-test-stability/` (디렉터리 — 안의 파일 전부) |

## 명령

```sh
git restore --source=10d208d2664c6bb87cd7082a2853c08c2c0d3236 --staged --worktree -- \
  web/e2e/journey/journey.spec.ts \
  web/e2e/journey/onboarding.spec.ts \
  web/e2e/journey/results.spec.ts \
  web/e2e/journey/shared.ts \
  reports/evidence/m12/web-test-stability
```

`--source` 에 없는 경로(evidence)는 **삭제**되므로 신규 파일에 별도 `git rm` 이 없다.

## 실측 — 버릴 clone 에서 ①~⑥

실측 HEAD: `11f1696ad22b9be0fd304c39c3a81d8b302e26bc` (이 slice 의 **마지막 산출물 커밋**).
그 뒤에 오는 커밋은 evidence 디렉터리뿐이고 **그 경로는 전부 원천에 없는 신설 파일**이다 —
열거와 원천 부재 확인은 verifier 가 한다(2026-11-11 예외). 그 사이에 머지 커밋도 없다.

| # | 무엇 | 결과 |
| --- | --- | --- |
| ① | 복원 명령 종료 코드 | **0** |
| ② | D/M 수 | **D 2 · M 4** (D = evidence 둘, M = 여정 파일 넷) |
| ③ | in_scope 경로의 `git diff <base>` | **빈 출력** |
| ③b | **트리 동일성** — 작업 트리·인덱스 전체의 `git diff <base>` | **둘 다 빈 출력** (복원 뒤 트리가 base 와 바이트 동일) |
| ③c | 하네스 경로가 HEAD 그대로 | **빈 출력** |
| ④ | compile — `npm run typecheck` · `npm run build` | **0 · 0** |
| ⑤ | test — `npm run test`(vitest 전건) | **0** |
| ⑥ | 게이트 — `lint` · `gate:src-tree` · `api:types:check` · `gate:tests` · `gate:boundary-canary` | **전부 0** |

**여정 레인(W-13)과 Kotlin `check` 는 ③b 로 갈음한다** — 갈음의 근거는 「HEAD 초록」이 아니라
**트리 동일성**이다(복원 뒤 트리가 base 와 바이트 동일하고 base 는 병합된 main 이다). 복원된
트리에는 이 slice 의 evidence 도 없어 leak-pattern 게이트의 스캔 뿌리가 base 와 같다.

**예상 복구 시간**: 명령 하나(초 단위), 확인까지 web 게이트 전건 약 2분.

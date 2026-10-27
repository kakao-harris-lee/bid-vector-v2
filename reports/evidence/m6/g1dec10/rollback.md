# M6/G1-DEC10 — rollback

실측 HEAD: **미실측(Phase 4 에서 이 slice 의 마지막 산출물 커밋에서 돌고 여기에 적는다)**

## 복원 기준

- base = **`23837b19`**(PR #93 DEC-05 머지 커밋). 전제: **그 SHA 에 이 slice 가 없다** —
  착수 커밋(`scope.md` 초판)이 그 조상이 아니다. 이 slice 는 아직 main 에 없으므로 공유 문서의
  복원 기준도 같은 고정 SHA 를 쓴다(움직이는 `origin/main` 을 쓰지 않는다). **머지 뒤의
  되돌림은 이 절차가 아니라 그 PR 의 머지 커밋 revert 다** — 머지 순간 같은 복원은 아무것도
  지우지 않으면서 exit 0 을 낸다.
- 되돌리는 것은 range 가 아니라 **in_scope 경로**다. 하네스 경로(`CLAUDE.md`·`.claude/`)는
  되돌리지 않는다.

## 목록 산출 (라운드마다 다시 돌린다)

목록은 손으로 쓰지 않는다 — `git diff --name-status 23837b19..HEAD` 에서 기계적으로 낸다
(`A` = 삭제 대상, `M` = base 로 restore). **수정 라운드가 파일을 더하면 이 절차를 다시 돌리고
in_scope 와 대조해 계약을 갱신한다.**

## 절차

1. `A`·`M` 경로 모두 — `git restore --source=23837b19 --staged --worktree -- <경로 개별 인자>`
   (`--source` 에 없는 경로는 삭제되므로 신규 파일에 별도 `git rm` 이 필요 없다).
   `git checkout <base> -- <경로>` 는 쓰지 않는다.
2. **공유 파일**(다른 slice 의 줄이 함께 있는 파일) — 같은 고정 SHA `23837b19` 에서 복원한다:
   `docs/discovery/capability-map.md` · `reports/evidence/m6/6e1/acceptance-trace.md` ·
   `reports/evidence/m3/3a/policy-values.md` · `reports/evidence/m6/6g/policy-values.md` ·
   `milestone-6.md` · `config/quality/architecture-policy.properties` ·
   `config/quality/gate-tests.properties` · `procurement/.../CollectionPolicy.kt` ·
   `app/build.gradle.kts`. hunk 역적용은 쓰지 않는다 — 이 SHA 가 HEAD 의 조상이고 이 slice 를
   담지 않으므로 남의 줄을 떨어뜨릴 수 없다.
3. 이 slice 의 **evidence 디렉터리**(`reports/evidence/m6/g1dec10/`)는 되돌리지 않는다.
   그로 인해 게이트가 붉어지면 그 사실과 조건을 알려진 제한에 등재한다.
4. 목록 갱신 커밋은 **evidence 경로만** 만진다(목록이 자기를 담은 커밋을 가리킬 수 없다 —
   공유 파일을 만지는 문서 커밋과 목록 갱신 커밋을 나눈다).

## 실측 (임시 clone, Phase 4)

| # | 재는 것 | 결과 |
| --- | --- | --- |
| ① | 복원 명령 exit | 미실측 |
| ② | `D`/`M` 수 | 미실측 |
| ③ | `git diff 23837b19 -- <in_scope>` 빈 출력 · 「남의 줄 보존」 계수 | 미실측 |
| ④ | 되돌린 트리 compile exit | 미실측 |
| ⑤ | 되돌린 트리 test exit | 미실측 |
| ⑥ | 되돌린 트리 **게이트**(`check`) exit | 미실측 |

유효성 확인(verifier 대조용): `git diff --name-only <실측 HEAD>..<판정 SHA> -- <위 목록의 경로들>`
이 **빈 출력**이면 실측이 유효하다.

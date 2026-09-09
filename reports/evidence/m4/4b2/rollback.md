# rollback.md — M4/4B-2

**되돌림은 range revert가 아니라 in_scope 경로 한정이다**(evidence-pack 스킬). 하네스
경로(`CLAUDE.md`·`.claude/**`)는 되돌리지 않는다 — 이 slice의 하네스 레인 변경은
착수 시점 0건이다(scope.md 「하네스 레인 변경」 절).

## 공유 파일 — 커밋 해시 hunk 격리(evidence-pack 2026-09-09)

`config/quality/gate-tests.properties`·`docs/discovery/capability-map.md`·
`milestone-4.md`는 다른 slice도 만질 수 있는 공유 파일이다. **되돌리기 전에 먼저**
`git log --oneline 401bc4d5636cd114c8282cf314d837f0d52dbc15..HEAD -- <파일>`로 그
파일을 만진 커밋을 전부 나열해, 4B-2 아닌 커밋이 같이 나오는지 확인한다.

**재실측(수정 라운드 1, head `76b0ec5`)**: 세 파일 다 이 range에서 **4B-2 자신의
커밋(`a0d254f`)만** 만졌다 — 수정 라운드 1의 커밋(`76b0ec5`)은 이 셋을 건드리지
않는다. 다른 slice 커밋이 아직 섞이지 않아 commit-hash 격리 없이 `base..HEAD`로
안전하게 되돌렸다(임시 clone 실측, commands.md). 라운드가 더 늘거나 다른 slice가
같은 파일을 만지면 이 절차를 다시 돌려 표를 갱신한다 — 다른 slice 커밋이 섞여
나오면 그 커밋 해시로 hunk를 격리하고(`git diff <commit>~1..<commit> -- <파일> |
git apply -R`), 4B-2만 있으면 `git diff <base>..HEAD -- <파일> | git apply -R`로
충분하다. 확인은 「내 줄 사라짐」과 **「남의 줄 남음」을 둘 다** 잰다.

## 신규 파일(A) — 전체 삭제

```
git diff --name-status 401bc4d5636cd114c8282cf314d837f0d52dbc15..HEAD -- \
  workflow/src/main/kotlin/bidvector/workflow/evaluation \
  workflow/src/test/kotlin/bidvector/workflow/evaluation \
  config/quality/gate-tests.properties \
  docs/discovery/capability-map.md \
  milestone-4.md \
  reports/evidence/m4/4b2
```

결과(head 확정 뒤 commands.md에 실측 기록).

## 되돌리는 명령

**신규 파일(전체 삭제)**:

```
git restore --source=401bc4d5636cd114c8282cf314d837f0d52dbc15 --staged --worktree -- \
  <신규 파일 경로 개별 인자>
```

**공유 파일(줄 단위)**: 위 「공유 파일」 절의 절차.

`git checkout <base> -- <경로>`는 쓰지 않는다(base에 없는 신규 경로마다 pathspec
오류로 exit 1).

## 확인 지점 (임시 clone에서 실제로 돌려 실측)

commands.md에 실행 결과를 한 줄씩 남긴다:

1. 위 명령들이 **임시 clone**에서 exit 0.
2. 공유 파일의 diff가 4B-2 몫만 사라지고 다른 slice 몫(있다면)은 남음.
3. 신규 파일 삭제 뒤 `git status --porcelain`이 목록과 일치.
4. 되돌린 트리에서 **모듈별 compile**이 exit 0 — `:workflow:compileKotlin
   :app:compileTestKotlin`.
5. 되돌린 트리의 **test**가 초록 — `:workflow:test :app:test --tests '*Conformance*'`.

## 되돌린 뒤 남는 것

`workflow/src/main/kotlin/bidvector/workflow/evaluation/**` 전체(조합 use case·판정
결과 어휘·port)가 사라진다. `gate.tests.workflow`는 이 slice가 추가한 `Composition
BoundaryTest`·`EvaluateCandidatesUseCase{,Isolation}Test` 세 줄만 걷힌다(4A/4C-1 몫은
그대로). `capability-map.md`의 `OPEN-4B1-OFF-LADDER-DROPS` 종결 표시와 `OPEN-4B2-*`
다섯 항목도 되돌아간다 — 그 OPEN은 M4/4B-1 종결 시점 상태(미종결)로 복귀한다.

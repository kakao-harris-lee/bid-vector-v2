# rollback.md — M4/4A

> **수정 라운드 1(2026-09-08) 반영 — 목록 재산출.** verifier B-2/B-3(장부층) — 라운드가 파일을
> 늘렸으므로(`AppliedStrategy.kt`·`EditSessionPolicyDataTest.kt` 신설) 절차를 다시 돌리고
> 5단계 실측도 새 목록으로 재실행했다.

## 되돌리는 것

4A 의 신규 wiring 전부 — `workflow` 모듈의 편집 상태 기계(production+test)·`gate.tests.workflow`
등재·app conformance 의 `strategy-edit` corpus 배선·`strategy-edit-*` fixture 다섯·
`workflow/build.gradle.kts` test 의존 추가·`milestone-4.md` 4A 구현 기록.

**되돌리지 않는 것**: `reports/evidence/m4/4a/**`(이 evidence 자체) · 하네스 경로
(`CLAUDE.md`·`.claude/**` — 이 slice 는 하네스 레인 변경이 없다, scope.md 「하네스 레인 변경」절) ·
`reports/evidence/m4/4a/scope.md`(base 이후 하네스 레인이 만든 slice 계약 고정 커밋 `2f2378b`,
내 세션 시작 전 — 이 slice 의 산출물이 아니다).

## 목록 — 기계 산출

`git diff --name-status <base>..HEAD` 에서 낸다(2026-09-08, head `022629b` — 수정 라운드 1의
마지막 코드 커밋. 이 evidence 커밋 자신은 셈에 넣지 않는다).
**A(신규, 28건)**·**M(수정, 6건 — `CorpusExecutors.kt`·`SharedKernelCorpusConformanceTest.kt`·
`gate-tests.properties`·`fixtures/manifest.yaml`·`milestone-4.md`·`workflow/build.gradle.kts`)**.
`reports/evidence/m4/4a/**` 6건(evidence 다섯 + `scope.md`, 하네스 레인)은 대상에서 뺀다.
**라운드마다 파일이 늘면 이 목록을 다시 낸다** — 수정 라운드 1 이 `AppliedStrategy.kt`(H-1/H-2)·
`EditSessionPolicyDataTest.kt`(M-2) 둘을 새로 더해 26→28건이 됐다.

## 명령

경로 한정, 개별 인자(변수 미사용 — 2026-09-03 pathspec 함정 회피):

```
git restore --source=9948c6e4056bbf71fa6683aa67d30c2a49fc6eae --staged --worktree -- \
  app/src/test/kotlin/bidvector/app/conformance/CorpusExecutors.kt \
  app/src/test/kotlin/bidvector/app/conformance/SharedKernelCorpusConformanceTest.kt \
  app/src/test/kotlin/bidvector/app/conformance/StrategyEditExecutors.kt \
  config/quality/gate-tests.properties \
  fixtures/expected/strategy-edit-001.json \
  fixtures/expected/strategy-edit-002.json \
  fixtures/expected/strategy-edit-003.json \
  fixtures/expected/strategy-edit-004.json \
  fixtures/expected/strategy-edit-005.json \
  fixtures/input/strategy-edit-001.json \
  fixtures/input/strategy-edit-002.json \
  fixtures/input/strategy-edit-003.json \
  fixtures/input/strategy-edit-004.json \
  fixtures/input/strategy-edit-005.json \
  fixtures/manifest.yaml \
  milestone-4.md \
  workflow/build.gradle.kts \
  workflow/src/main/kotlin/bidvector/workflow/strategy/Actor.kt \
  workflow/src/main/kotlin/bidvector/workflow/strategy/AppliedStrategy.kt \
  workflow/src/main/kotlin/bidvector/workflow/strategy/EditableField.kt \
  workflow/src/main/kotlin/bidvector/workflow/strategy/EditCommand.kt \
  workflow/src/main/kotlin/bidvector/workflow/strategy/EditSessionPolicyData.kt \
  workflow/src/main/kotlin/bidvector/workflow/strategy/EditSessionTypes.kt \
  workflow/src/main/kotlin/bidvector/workflow/strategy/EditStrategyWorkflow.kt \
  workflow/src/main/kotlin/bidvector/workflow/strategy/Ports.kt \
  workflow/src/main/kotlin/bidvector/workflow/strategy/RejectionReason.kt \
  workflow/src/main/kotlin/bidvector/workflow/strategy/Transition.kt \
  workflow/src/main/kotlin/bidvector/workflow/strategy/TransitionOutcome.kt \
  workflow/src/test/kotlin/bidvector/workflow/strategy/EditSessionActorAndTimeoutTest.kt \
  workflow/src/test/kotlin/bidvector/workflow/strategy/EditSessionIdempotencyPropertyTest.kt \
  workflow/src/test/kotlin/bidvector/workflow/strategy/EditSessionImportBoundaryTest.kt \
  workflow/src/test/kotlin/bidvector/workflow/strategy/EditSessionPolicyDataTest.kt \
  workflow/src/test/kotlin/bidvector/workflow/strategy/EditSessionTransitionTableTest.kt \
  workflow/src/test/kotlin/bidvector/workflow/strategy/EditStrategyWorkflowTest.kt
```

`--source` 에 없는 경로(신규 파일 28건)는 이 명령이 삭제까지 처리한다 — 별도 `git rm` 불필요.
`git checkout <base> -- <경로>` 는 쓰지 않는다(base 에 없는 새 경로마다 pathspec 오류로 exit 1,
Codex 1A 16차 high 재현 방지).

## 실측 — 2026-09-08, 임시 clone(`git clone --branch m4/2026-09-08 --single-branch`, 커밋만 담음)

1. 복원 명령 exit: **0**
2. `git status --porcelain` — **D 28건 · M 6건**, 목록의 A/M 건수와 정확히 일치.
   신규 디렉터리(`workflow/src/main/kotlin/bidvector/workflow/strategy/`·`workflow/src/test/kotlin/bidvector/workflow/strategy/`)에
   `find` 로 잔여 파일 0건(추가 `git rm` 불필요 확인).
3. `git diff <base> -- <M 대상 6개 경로>` — **비어 있음**(exit 0, 출력 0줄).
4. `./gradlew --no-daemon :workflow:compileKotlin :app:compileTestKotlin` — **`BUILD SUCCESSFUL`**
   (29 actionable tasks, 13 executed·16 from cache).
5. `./gradlew --no-daemon :workflow:test :app:test --tests '*Conformance*'` — **`BUILD SUCCESSFUL`**
   (`:workflow:test NO-SOURCE`, `:app:test` 정상 축소 — strategy-edit 5건이 사라진 conformance
   corpus 로 되돌아감).

되돌린 트리의 `workflow` 모듈은 `ModuleBoundaryAnchor.kt` 하나만 남는다(1A 관례 — 경계 규칙이
빈 집합 위에서 공허 통과하지 않게 하는 anchor). `app` 의 conformance corpus 는 4A 이전(기존
`strategy-watch`·`strategy-validation` 등)으로 되돌아간다.

## 예상 복구 시간

경로 한정 restore 는 즉시(초 단위) — 별도 마이그레이션·데이터 되돌림이 없다(port 는 fake만
있었고 실 저장·outbox 배선이 이 slice 밖).

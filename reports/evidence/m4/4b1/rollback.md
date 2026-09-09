# rollback.md — M4/4B-1

**되돌림은 range revert가 아니라 in_scope 경로 한정이다**(evidence-pack 스킬). 하네스
경로(`CLAUDE.md`·`.claude/**`)는 되돌리지 않는다 — 이 slice의 하네스 레인 변경은 착수
시점 0건이었고 리뷰 요청 시점에 재확인해 이 절을 갱신한다(scope.md 「하네스 레인
변경」 절).

## 4C-1 과 공유하는 파일 — 줄 단위 되돌림(scope.md 「4C-1 과 공유하는 파일」 절)

다음 파일은 4C-1(이벤트 봉투·outbox, `workflow` 모듈)과 이 slice(`decision` 모듈)가
함께 만진다 — **파일 전체 복원이 아니라 이 slice가 넣은 줄만 건다**:

- `config/quality/gate-tests.properties` — `gate.tests.decision` 블록만(4C-1의
  `gate.tests.workflow` 블록은 그대로).
- `fixtures/manifest.yaml` — `verdict-005`~`012` 8개 case 블록만(단일 hunk, 4C-1의
  `strategy-edit`·`verdict-001~004` 무접촉).
- `app/src/test/kotlin/bidvector/app/conformance/CorpusExecutors.kt` — `VERDICT_EXECUTORS`
  병합 줄 하나 + 주석 한 줄만.
- `app/src/test/kotlin/bidvector/app/conformance/SharedKernelCorpusConformanceTest.kt` —
  `TARGET_DOMAINS`에 `"verdict"` 추가한 3줄만.
- `docs/discovery/data-dictionary.md`·`docs/discovery/capability-map.md` — 아래 「목록
  산출」의 hunk 그대로(§3.6·§13.2·`OPEN-DIC-03` 행만, 4C-1의 §2.2.5 무접촉 — 서로 다른
  절이라 hunk 자체가 겹치지 않는다).

**신규 파일**(A 항목 — `decision/**`·`app/.../VerdictExecutors.kt`·`fixtures/{input,expected}/verdict-0[0-9][0-9].json`
중 005~012·`reports/evidence/m4/4b1/**`)은 파일 전체 삭제로 되돌린다(공유 파일이 아니다).

## 목록 산출 (기계적, 손으로 쓰지 않는다)

```
git diff --name-status 13cf0f63da18e13f0e2befd519710a8635742004..HEAD -- \
  decision/src/main/kotlin/bidvector/decision \
  decision/src/test/kotlin/bidvector/decision \
  config/quality/gate-tests.properties \
  fixtures/manifest.yaml \
  fixtures/input/verdict-005.json fixtures/input/verdict-006.json fixtures/input/verdict-007.json \
  fixtures/input/verdict-008.json fixtures/input/verdict-009.json fixtures/input/verdict-010.json \
  fixtures/input/verdict-011.json fixtures/input/verdict-012.json \
  fixtures/expected/verdict-005.json fixtures/expected/verdict-006.json fixtures/expected/verdict-007.json \
  fixtures/expected/verdict-008.json fixtures/expected/verdict-009.json fixtures/expected/verdict-010.json \
  fixtures/expected/verdict-011.json fixtures/expected/verdict-012.json \
  app/src/test/kotlin/bidvector/app/conformance/VerdictExecutors.kt \
  app/src/test/kotlin/bidvector/app/conformance/CorpusExecutors.kt \
  app/src/test/kotlin/bidvector/app/conformance/SharedKernelCorpusConformanceTest.kt \
  docs/discovery/data-dictionary.md \
  docs/discovery/capability-map.md \
  reports/evidence/m4/4b1
```

결과(head 확정 뒤 commands.md에 실측 기록).

## 되돌리는 명령

**공유 파일(줄 단위)**: `git diff HEAD~<n> -- <공유 파일>`로 이 slice가 넣은 hunk를 확인한
뒤 `git apply -R`로 그 hunk만 되돌린다(파일 전체 `git restore`는 4C-1의 변경까지 지운다).

**신규 파일(전체 삭제)**:

```
git restore --source=13cf0f63da18e13f0e2befd519710a8635742004 --staged --worktree -- \
  <신규 파일 경로 개별 인자>
```

`--source`에 없는 신규 경로는 이 명령이 삭제한다.

## 확인 지점 (임시 clone에서 실제로 돌려 실측)

commands.md에 실행 결과를 한 줄씩 남긴다:

1. 위 명령들이 **임시 clone**에서 exit 0.
2. 공유 파일 4종의 diff가 4C-1 몫만 남고 이 slice 몫은 사라짐(`grep`으로 `verdict`·
   `Verdict` 관련 줄 부재 확인).
3. 신규 파일 삭제 뒤 `git status --porcelain`이 목록과 일치.
4. 되돌린 트리에서 **모듈별 compile**이 exit 0 — `:workflow:compileKotlin
   :app:compileTestKotlin`(decision 모듈 자체는 4C-1 시점에 이미 있었으므로 별도
   컴파일 확인 불필요 — 이 slice가 추가한 파일만 사라지면 된다).
5. 되돌린 트리의 **test**가 초록 — `:workflow:test :app:test --tests '*Conformance*'`.

## 되돌린 뒤 남는 것

4C-1 종결 시점(`13cf0f6`)의 정확한 형태로 복귀한다 — `decision` 모듈은 1D 산출물
(`ProvenanceRules`·`FloorShortfallKernel`)만 남고 `Verdict` 커널 전체가 사라진다.
`gate-tests.properties`의 `gate.tests.decision`은 4C-1 이전(1D 관례) 두 줄로 복귀한다.

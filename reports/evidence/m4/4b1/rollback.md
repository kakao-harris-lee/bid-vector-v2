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
- `fixtures/manifest.yaml` — `verdict-001`~`012` 12개 case 블록만(4C-1의 `strategy-edit`
  무접촉 — 001~004는 수정 라운드 1 후속(운영자 결정 2026-09-09 선택지 (a))에서 4B-1
  **자신이** 편집했다, 4C-1 이 만진 적은 없다).

## M0 원본 fixture — 전체 복원(신규 아님, base 에도 있던 파일)

`fixtures/{input,expected}/verdict-00{1,2,3,4}.json`(8개)은 base(`13cf0f6`)에도
**이미 있던** M0 저작 파일이다 — 4B-1이 그 내용을 커널 입력 계약으로 재구성했다
(수정 라운드 1 후속). **신규 파일(A)이 아니라 M**이므로 삭제가 아니라 base 시점
내용으로 전체 복원한다(이 range 에서 4B-1 만 만졌다 — 위 「목록 산출」로 확인).
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
  fixtures/input/verdict-001.json fixtures/input/verdict-002.json fixtures/input/verdict-003.json \
  fixtures/input/verdict-004.json \
  fixtures/input/verdict-005.json fixtures/input/verdict-006.json fixtures/input/verdict-007.json \
  fixtures/input/verdict-008.json fixtures/input/verdict-009.json fixtures/input/verdict-010.json \
  fixtures/input/verdict-011.json fixtures/input/verdict-012.json \
  fixtures/expected/verdict-001.json fixtures/expected/verdict-002.json fixtures/expected/verdict-003.json \
  fixtures/expected/verdict-004.json \
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

**공유 파일(줄 단위)** — 먼저 `git log --oneline <base>..HEAD -- <공유 파일>`로 이 range
안에서 그 파일을 만진 커밋을 전부 나열해, 4C-1 커밋과 4B-1 커밋이 **같이** 나오는지
확인한다(실측: `gate-tests.properties`는 4C-1 rework(`7469bce`)와 4B-1(`381eaeb`)이
둘 다 나온다 — `fixtures/manifest.yaml`·`CorpusExecutors.kt`·
`SharedKernelCorpusConformanceTest.kt`·`data-dictionary.md`·`capability-map.md`는 이
range 에서 4B-1 커밋만 나온다).

- 다른 슬라이스 커밋과 **섞여 나오는 파일**(`gate-tests.properties`): 4B-1 자신의 커밋
  해시로 hunk 를 격리한다 — `git diff <commit>~1..<commit> -- <파일> | git apply -R`
  (실측 커밋: `381eaeb`).
- 이 range 에서 **4B-1 만 만진 파일**: `git diff <base>..HEAD -- <파일> | git apply -R`
  로 충분하다(다른 슬라이스 몫이 이 range 에 없으므로 base..HEAD 전체가 이 slice 몫과
  같다) — 단, 다음 라운드에서 그 파일에 다른 슬라이스 커밋이 새로 끼면 위 방식(커밋
  해시 격리)으로 다시 바꾼다.

파일 전체 `git restore --source=<base>`는 두 경우 모두 쓰지 않는다 — 다른 슬라이스가
그 사이 넣은 줄까지 지운다(4C-1의 `7469bce` 실사례).

**신규 파일(전체 삭제)**:

```
git restore --source=13cf0f63da18e13f0e2befd519710a8635742004 --staged --worktree -- \
  <신규 파일 경로 개별 인자>
```

`--source`에 없는 신규 경로는 이 명령이 삭제한다.

**M0 원본 fixture(전체 복원, 삭제 아님)**:

```
git diff 13cf0f63da18e13f0e2befd519710a8635742004..HEAD -- \
  fixtures/input/verdict-001.json fixtures/input/verdict-002.json fixtures/input/verdict-003.json \
  fixtures/input/verdict-004.json fixtures/expected/verdict-001.json fixtures/expected/verdict-002.json \
  fixtures/expected/verdict-003.json fixtures/expected/verdict-004.json | git apply -R
```

(이 range 에서 4B-1 만 만졌으므로 base..HEAD diff 를 되돌리면 M0 원본으로 복귀한다 —
`git restore --source=<base>`를 써도 결과는 같지만 위 procedure 를 일관되게 유지한다.)

## 확인 지점 (임시 clone에서 실제로 돌려 실측)

commands.md에 실행 결과를 한 줄씩 남긴다:

1. 위 명령들이 **임시 clone**에서 exit 0.
2. 공유 파일 4종의 diff가 4C-1 몫만 남고 이 slice 몫은 사라짐(`grep`으로 `verdict`·
   `Verdict` 관련 줄 부재 확인).
3. 신규 파일 삭제 뒤 `git status --porcelain`이 목록과 일치. M0 원본 fixture 8개는
   base 시점 내용(`gateOutcome`/`operatorFloorRateOverride` 등 서술형)으로 복귀했는지
   `diff`로 확인.
4. 되돌린 트리에서 **모듈별 compile**이 exit 0 — `:decision:compileKotlin
   :app:compileTestKotlin`. **정정(verifier B-5)** — 이전 판은 "decision 모듈 자체는
   4C-1 시점에 이미 있었으므로 별도 컴파일 확인 불필요"라 적었으나 틀렸다: `decision`
   모듈 **자체가 4B-1 신설**이라(4C-1 시점엔 `ProvenanceRules`·`FloorShortfallKernel`
   뿐이었다) 이 slice가 추가한 13개 소스가 전부 삭제되는 되돌림에서 `:decision:
   compileKotlin`이 여전히 성공하는지(남은 1D 소스만으로 모듈이 정상 컴파일되는지)를
   반드시 실측해야 한다(M3/3B-2 교훈 — exit 0 만으로는 부족, compile까지 재야 한다).
5. 되돌린 트리의 **test**가 초록 — `:decision:test :workflow:test :app:test --tests
   '*Conformance*'`.

## 되돌린 뒤 남는 것

4C-1 종결 시점(`13cf0f6`)의 정확한 형태로 복귀한다 — `decision` 모듈은 1D 산출물
(`ProvenanceRules`·`FloorShortfallKernel`)만 남고 `Verdict` 커널 전체가 사라진다.
`gate-tests.properties`의 `gate.tests.decision`은 4C-1 이전(1D 관례) 두 줄로 복귀한다.

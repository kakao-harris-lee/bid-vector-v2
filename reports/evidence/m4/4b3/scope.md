# Slice 계약 — M4 / 4B-3 · ML 미가용 경로 배선 (4B-2 후속)

> **지위**: **착수 계약 2026-09-10.** 운영자 결정 2026-09-10 D-2(c)·D-6(b) — `OPEN-4D-LADDER-SCORE-SOURCE` 결정 (a)(M2 계약 additive 확장 + M5 provider)가
> 실물을 내기 전까지 4B-2 의 조합 use case 가 **ML 미가용 경로를 실제로 밟게** 한다. 세션 모델 단독 작성.
>
> **왜 필요한가 (4D-1 착수 조사 실측, `_workspace/m4-4d/01_scout_ml_gateway.md` §3 ②).** 4B-2 `MlAnalysisOutcome` 은 `Analyzed(priorityScore: UnitScore(non-null) …)`
> 와 `SimilarityProjectionNotReady`(드롭 신호) 둘뿐이라 use case 경로에서 `Verdict.Review(MlUnavailable)` 에 닿는 배선이 없다. M4 완료 조건 「ML 장애가 위험한
> 추천으로 fail-open 하는지」·「ML timeout 시 업무 결과가 fail-safe」의 사다리 쪽 실물이 비어 있다. 또 `analyze` 가 non-suspend 라 4D-2 의 코루틴 gateway 가
> 취소·deadline(ADR 0010 D-2)을 끊어야 한다.
>
> **레인 격리.** 다른 세션은 `m4/2026-09-08`(=`main`, `ff210c1`)에서 4C-2(outbox 영속)를 진행 중. 이 slice 는 `ff210c1` 에서 가른 브랜치 `m4-4b3/2026-09-10`
> (worktree `bid-vector-v2-m4e`). 4C-2 와 겹칠 파일: `config/quality/gate-tests.properties`(`gate.tests.adapters` 키 — 줄 단위)·`milestone-4.md`·`workflow/src/test/**`
> (4C-2 는 `event` 하위만). `workflow/event/**`·`adapters/persistence/**` 무편집.

```yaml
milestone: m4
slice: 4b3-ml-unavailable-path
base_sha: ff210c187bb7885da7639de0434d815e59e7f32a   # 4E·4D-1 병합 커밋 = m4/2026-09-08 = main
head_sha: 리뷰 요청 시점의 `git rev-parse HEAD` — 값을 박지 않는다.
branch: m4-4b3/2026-09-10
in_scope:
  - workflow/src/main/kotlin/bidvector/workflow/evaluation/Ports.kt                    # `MlAnalysisOutcome.Unavailable(reason: MlUnavailableReason)` 신설 · `MlAnalysisPort.analyze` suspend
  - workflow/src/main/kotlin/bidvector/workflow/evaluation/EvaluateCandidatesUseCase.kt # `evaluate` suspend · `Unavailable` 가지 → `reach`(점수 null + 사유)
  - workflow/src/test/kotlin/bidvector/workflow/evaluation/**                          # fake suspend 화 · Unavailable case 셋 · 기존 test 무변경(suspend 래핑만)
  - decision/src/main/kotlin/bidvector/decision/LadderInput.kt                          # `mlUnavailableReason: MlUnavailableReason = ScoreNotProvided` 슬롯(기본값) 추가만
  - decision/src/main/kotlin/bidvector/decision/VerdictLadder.kt                        # `priorityScore == null` 분기가 그 슬롯을 싣도록 — 다른 분기 무변경
  - decision/src/test/kotlin/bidvector/decision/**                                      # 사유 전달 표 test
  - adapters/src/main/kotlin/bidvector/adapters/ml/UnavailableMlAnalysis.kt             # 항상-미가용 port 구현(유일한 실 배선, provider 부재 기간)
  - adapters/src/test/kotlin/bidvector/adapters/ml/UnavailableMlAnalysisTest.kt
  - config/quality/gate-tests.properties                                                # gate.tests.adapters 한 키에 UnavailableMlAnalysisTest 등재(verifier r1 L-4 정정 — decision·workflow 키의 class 는 base 에 이미 등재, 이 slice는 기존 class 에 case 를 더했을 뿐 신설 class 가 없다)
  - milestone-4.md                                                                      # 4B 절 종결 문단(승인 시점)
  - reports/evidence/m4/4b3/**
out_of_scope:
  - `Analyzed` 의 필드 nullable 화 · 새 `EvaluationDropReason` · 후보 병렬 실행 · `CallBudget` 인자(4D-2)
  - fixtures/** · app/** (기본값 슬롯이라 corpus 실행자 무변경 — 컴파일이 깨지면 **멈추고 보고**)
  - workflow/event/** · adapters/persistence/** · db/migration/** (4C-2 lane)
  - 실 점수 산출(2E·M5) · 실 gateway(4D-2) · `MlUnavailableReason` 어휘 변경(4D-1 종결)
acceptance_commands:
  - "git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check)"   # S-0
  - "./gradlew --no-build-cache clean check"                                                          # S-1
  - "./gradlew --no-daemon :workflow:test --tests 'bidvector.workflow.evaluation.*'"                  # S-2a — 4B-2 test 전건 + Unavailable case 셋
  - "./gradlew --no-daemon :decision:test"                                                             # S-2b — 사유 전달 표 + 4B-1 사다리 test 무변경
  - "./gradlew --no-daemon :adapters:test --tests 'bidvector.adapters.ml.UnavailableMlAnalysisTest'"  # S-2c
  - "./gradlew --no-daemon :app:test --tests '*Conformance*'"                                         # S-3 — verdict corpus 기대값 무변경(기본값 슬롯)
  - "./gradlew --no-daemon :workflow:gateExecutionGate :decision:gateExecutionGate :adapters:gateExecutionGate"   # S-4
  - "./gradlew --no-daemon :workflow:test --tests '*CompositionBoundaryTest*'"                        # S-5 — 4B-2 경계 스캔 유지
  - "./gradlew qualityBaseline"                                                                        # S-6
rollback: |
    **정본은 `reports/evidence/m4/4b3/rollback.md`**. 신설 둘(adapters `UnavailableMlAnalysis` main·test)은 삭제, 나머지는 **줄 단위**(커밋 해시 hunk 격리,
    최신 → 과거 순). 병합 전에는 「브랜치를 버린다」.
```

근거: `milestone-4.md` 4B·4D·완료 조건 · ADR 0010 D-2·D-6 · D-M4-6 (a) · 4B-1 `VerdictLadder` KDoc(「`priorityScore` 가 없으면 즉시 `Review(MlUnavailable)`」) ·
4B-2 scope ⑦ · 4D-1 scope ④(`MlUnavailableReason` 열 값) · 운영자 결정 2026-09-10(D-2·D-6) · `_workspace/m4-4b3/02_design-review.md`.

---

## 하네스 레인 변경 (상시 절)

`git log --oneline ff210c187bb7885da7639de0434d815e59e7f32a..HEAD -- CLAUDE.md .claude/` — 착수 시 **없음**,
종결(사용자 승인) 재실행에서도 **없음**(0건 — 같은 명령 재확인).

---

## 병합 결정 (사용자 승인 2026-09-10 ②)

병합 대상은 `m4/2026-09-08`(종결 등재 시점 HEAD `a3822c4`, 4C-2 evidence 커밋 — 이 slice
착수 시점의 base `ff210c187bb7885da7639de0434d815e59e7f32a`에서 그 사이 움직였다, 4D-1과
같은 관측). 팀장이 이 종결 등재 커밋 뒤에 병합을 실행한다 — 이 slice(구현 레인)는
병합·push를 하지 않는다.

---

## 이 slice 가 하는 일

| # | 일 | 승인 문면 |
| --- | --- | --- |
| ① | **미가용 값** — `MlAnalysisOutcome.Unavailable(reason: MlUnavailableReason)`. `Analyzed` 무변경(「분석됐는데 priority 없음」이라는 상태는 없다), `SimilarityProjectionNotReady` 는 드롭 신호로 유지 | ADR 0010 D-6 「미가용의 이름은 하나」 · 4D 조사 §3 ② |
| ② | **사유가 사다리까지** — `LadderInput.mlUnavailableReason`(기본 `ScoreNotProvided`) 슬롯을 더하고 `VerdictLadder.judge` 의 `priorityScore == null` 분기가 그 값을 `Review(MlUnavailable(reason))` 에 싣는다. 기본값이라 4B-1 test·corpus 실행자·fixture 무변경 | D-M4-6 (a) 「사유를 나른다」 · 4B-1 「이 sealed 는 4D 가 넓힌다」의 소비 |
| ③ | **use case 경로** — `analyzeAndJudge` 에 `Unavailable` 가지: `scoreThresholdDrop` 을 거치지 않고 `reach` 로 직행, `LadderInput` 점수 셋 null + 사유. 결과는 `Reached(Review(MlUnavailable(reason)))`, 알림 0 | M4 완료 조건 「ML 장애가 fail-open 하지 않는다」·NOTI-04 결함 후보(`sent:false` 정상 반환)의 뒤집기 — 운영자에게 「ML 없이 판단했다」 신호 |
| ④ | **suspend** — `MlAnalysisPort.analyze`·`EvaluateCandidatesUseCase.evaluate` suspend. 후보 순회는 순차 유지 | ADR 0010 D-2 「coroutine cancellation 이 gRPC cancel 로 전파」 |
| ⑤ | **항상-미가용 배선** — `adapters/ml/UnavailableMlAnalysis : MlAnalysisPort`, 항상 `Unavailable(ScoreNotProvided)`. provider 부재 기간(2E·M5 진행 중)의 유일한 실 배선. 이 adapter 로 use case 를 돌리는 test 가 「fail-safe 실물」 | 운영자 결정 2026-09-10 D-6 (b) |

**만들지 않는 것**: 실 점수 · 실 gateway · 병렬 순회 · 예산 인자 · 새 드롭 사유 · corpus.

---

## 위협 모델 · 우회 후보

정본은 `_workspace/m4-4b3/02_design-review.md` (0)·(4). 방어 (a) 도달 불가 fail-safe (b) 사유 접힘 (c) 미가용에서 알림 (d) 동기 port 의 취소 단절.
우회 여섯: 알림 유출 / 사유 접힘 / 최소치 드롭으로 새기 / 실 배선 부재 / 동기 회귀 / 새 사유 값 누락.

---

## OPEN

| OPEN | 처리 |
| --- | --- |
| `OPEN-4D-LADDER-SCORE-SOURCE`(결정 (a)) | 이 slice 는 그 결정의 **임시 상태 (c)** 를 실물로 — 2E·M5·4D-2 가 `UnavailableMlAnalysis` 를 실 gateway 로 갈아끼운다 |

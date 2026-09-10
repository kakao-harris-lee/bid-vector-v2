# M4 / 4D-2 — rollback.md

base_sha: `caee26c`. 목록은 `git diff --name-status caee26c..HEAD`(commands.md 작성 시점)로
기계 산출했다 — 라운드마다 파일이 늘면 이 명령을 다시 돌려 아래 목록을 갱신한다.

## 커밋 목록 (in_scope, 시간순)

| 커밋 | 내용 | 공유 파일 포함 |
| --- | --- | --- |
| `6e3d7b5` | `workflow/embedding/**` 신설(port·값 타입) | 아니오 |
| `39def39` | `ResilientPredictionCall.kt`·`GrpcBidPredictionGateway.kt` 수정(제네릭화+callMlRpc) | 아니오 |
| `5cb9a2d` | `GrpcEmbeddingGateway.kt` 등 신설 + `RequestMapping.kt` 수정(envelope 추출) | 아니오 |
| `52ce865` | `EmbeddingCallPolicy.kt`·`MlCallPolicyPlaceholder.kt` 신설 + `MlCallPolicyData.kt` 수정 | 아니오 |
| `7ac588f` | `adapters/src/test/.../Embedding*Test.kt`·`GrpcEmbeddingGatewayTest.kt` 신설 | 아니오 |
| `6371f28` | `config/quality/gate-tests.properties` 등재 7줄 | **예 — 4B-5(`m4-4b5/2026-09-10`)와 공유** |

## 되돌리는 것

새 wiring 자체가 없다(M6 배선 전 slice) — 되돌림은 **소스를 base로 복원**하는 것뿐이다.
DB write·외부 호출·flag 전환 대상이 없다.

## ① in_scope 비공유 경로 — 파일 단위 restore

`GrpcBidPredictionGateway.kt`·`MlCallPolicyData.kt`·`RequestMapping.kt`·
`ResilientPredictionCall.kt`는 **4D-1 산출물이지만 이 slice가 고친 파일**이므로 대상이다
(4D-1 test 13파일은 무편집 — 위 4개는 전부 production 파일). 나머지는 이 slice의 신설
파일이다.

```bash
git restore --source=caee26c --staged --worktree -- \
  adapters/src/main/kotlin/bidvector/adapters/ml/EmbeddingCallPolicy.kt \
  adapters/src/main/kotlin/bidvector/adapters/ml/EmbeddingReleaseShapeValidation.kt \
  adapters/src/main/kotlin/bidvector/adapters/ml/EmbeddingRequestMapping.kt \
  adapters/src/main/kotlin/bidvector/adapters/ml/EmbeddingResponseMapping.kt \
  adapters/src/main/kotlin/bidvector/adapters/ml/EmbeddingShapeValidation.kt \
  adapters/src/main/kotlin/bidvector/adapters/ml/GrpcBidPredictionGateway.kt \
  adapters/src/main/kotlin/bidvector/adapters/ml/GrpcEmbeddingGateway.kt \
  adapters/src/main/kotlin/bidvector/adapters/ml/MlCallPolicyData.kt \
  adapters/src/main/kotlin/bidvector/adapters/ml/MlCallPolicyPlaceholder.kt \
  adapters/src/main/kotlin/bidvector/adapters/ml/PredictionEnvelopeMapping.kt \
  adapters/src/main/kotlin/bidvector/adapters/ml/RequestMapping.kt \
  adapters/src/main/kotlin/bidvector/adapters/ml/ResilientPredictionCall.kt \
  adapters/src/test/kotlin/bidvector/adapters/ml/EmbeddingBreakerTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/ml/EmbeddingCallPolicyTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/ml/EmbeddingDeadlineCancellationRetryTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/ml/EmbeddingShapeFailClosedTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/ml/EmbeddingTestFixtures.kt \
  adapters/src/test/kotlin/bidvector/adapters/ml/GrpcEmbeddingGatewayTest.kt \
  workflow/src/main/kotlin/bidvector/workflow/embedding \
  workflow/src/test/kotlin/bidvector/workflow/embedding
```

`--source`에 없는 경로(신설 파일 15개)는 삭제된다 — 별도 `git rm` 불필요. `git checkout
caee26c -- <경로>`는 **쓰지 않는다**(base에 없는 신규 경로마다 pathspec 오류로 exit 1,
1A 16차 관례).

## ② 공유 파일 — 커밋 해시 hunk 격리

`git log --oneline caee26c..HEAD -- config/quality/gate-tests.properties` 결과: **이
branch(`m4/2026-09-08`) 안에서는 `6371f28` 하나만** 이 파일을 만졌다(4B-5는 별도 브랜치
`m4-4b5/2026-09-10`이라 아직 겹치지 않는다 — 두 브랜치가 나중에 합쳐지기 전까지는 아래
명령이 곧 `caee26c` 전체 복원과 같다. **합쳐진 뒤 이 rollback을 실행한다면 위 log 명령을
다시 돌려 겹치는 커밋이 있는지 먼저 확인한다** — 겹치면 아래 명령이 상대 slice의 줄까지
걷을 수 있다).

```bash
git diff 6371f28~1..6371f28 -- config/quality/gate-tests.properties | git apply -R
```

`--3way`가 필요할 정도의 인접 삽입은 없었다(수동 해소 절차 불필요 — 다른 slice의 삽입
지점과 겹치지 않는 자리에 7줄을 추가했을 뿐이다).

## ③ 확인 — 임시 clone에서 실측(2026-09-10)

| 확인 | 명령 | 결과 |
| --- | --- | --- |
| in_scope 경로가 base와 동일 | `git diff caee26c -- <in_scope 전 경로>` | 출력 0줄 |
| 하네스 경로 그대로 | `git diff --stat HEAD -- CLAUDE.md .claude/` | 출력 0줄(변경 없음) |
| 되돌린 트리 컴파일 | `./gradlew --offline :workflow:compileKotlin :workflow:compileTestKotlin :adapters:compileKotlin :adapters:compileTestKotlin` | exit 0 |
| 되돌린 트리 테스트 | `./gradlew --offline :workflow:test :adapters:test` | exit 0, workflow 134 / adapters 449 tests(4D-2 추가분만큼 감소, 4D-1 골격 보존) |

**목록은 자기를 담은 커밋을 가리킬 수 없다** — `scope.md`·`commands.md`·`rollback.md` 갱신
커밋은 `reports/evidence/m4/4d2/**`만 만지고 `config/quality/gate-tests.properties`를
건드리지 않는다(위 목록의 `6371f28`는 이 evidence 커밋보다 먼저다).

**라운드마다 파일이 늘면 `git diff --name-status caee26c..HEAD`를 다시 돌려 ①의 목록을
갱신한다.**

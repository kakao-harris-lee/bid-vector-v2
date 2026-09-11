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
| `8fe8d81` | F-1 — `EmbeddingShapeValidation.kt` 구조 수정(단락 평가 + 유한성 관문) | 아니오 |
| `e58e1e3` | F-1·F-4 회귀 — `EmbeddingShapeFailClosedTest.kt` 확장 | 아니오 |
| `3b606a3` | F-4 회귀 — `GrpcEmbeddingGatewayTest.kt` 확장 | 아니오 |
| `fdf2257` | F-2 — `EmbeddingValueTest.kt` KDoc 정정 + 비유한 값 test | 아니오 |
| `6cb843d` | F-3 — `EmbeddingCallPolicy.kt`·`EmbeddingCallPolicyTest.kt` 값 고정 test | 아니오 |
| `181892b` | F-5 — `milestone-4.md` 4D-2 착수 문단 신설 | **예 — M4 공유 승인 문서(전 slice 공용)** |
| `cce5850` | F-6·표적1 — `reports/evidence/m4/4d2/scope.md` 정정(evidence 경로, restore 대상 아님) | 아니오 |
| `6fa9f2d` | ktlint 포맷 — `EmbeddingShapeFailClosedTest.kt`·`GrpcEmbeddingGatewayTest.kt` MaxLineLength 정리 | 아니오 |
| `7b5ca69` | 코드 리뷰 F-A — `EmbeddingBreakerTest.kt` 결정적 재작성 + `EmbeddingTestFixtures.kt`(공용 fixture 이동) + `GrpcEmbeddingGatewayTest.kt`(중복 제거) | 아니오 |
| `1bc8cca` | 코드 리뷰 F-B+F-C — `EmbeddingCallPolicy.kt`·`EmbeddingCallPolicyTest.kt`·`EmbeddingVector.kt` | 아니오 |
| `af8fc11` | 코드 리뷰 F-D — `EmbeddingResponseMapping.kt`·`EmbeddingShapeValidation.kt`·`EmbeddingShapeFailClosedTest.kt`·`EmbedTextPort.kt` 인용 정정 | 아니오 |
| `204e86c` | 코드 리뷰 F-E — `GrpcBidPredictionGateway.kt`·`GrpcEmbeddingGateway.kt`·`MlCallPolicyData.kt`·`ReleaseCheck.kt`·`RetryRules.kt`(RPC/정책 해석 중복 제거) | 아니오 |
| `23f8ab7` | 코드 리뷰 F-E — `ParsedSuccessFields.kt`·`ReleaseShapeValidation.kt` 수정 + `EmbeddingReleaseShapeValidation.kt` 삭제(`hasNonBlankRelease` 통합) | 아니오 |
| `50a4719` | 코드 리뷰 finding 등재 — `reports/evidence/m4/4d2/scope.md`(evidence 경로, restore 대상 아님) | 아니오 |

## 되돌리는 것

새 wiring 자체가 없다(M6 배선 전 slice) — 되돌림은 **소스를 base로 복원**하는 것뿐이다.
DB write·외부 호출·flag 전환 대상이 없다.

## ① in_scope 비공유 경로 — 파일 단위 restore

`GrpcBidPredictionGateway.kt`·`MlCallPolicyData.kt`·`RequestMapping.kt`·
`ResilientPredictionCall.kt`·`ParsedSuccessFields.kt`·`ReleaseCheck.kt`·`RetryRules.kt`는
**4D-1 산출물이지만 이 slice가 고친 파일**이므로 대상이다(뒤 셋은 코드 리뷰 F-E
추출로 이번 라운드에 추가됨 — 4D-1 test 13파일은 여전히 무편집). 나머지는 이 slice의
신설 파일이다. `EmbeddingReleaseShapeValidation.kt`는 신설됐다가 F-E로 이번 라운드에
삭제돼(`hasNonBlankRelease` 통합) `caee26c..HEAD` net diff에 더 이상 나타나지 않는다
— 이미 없는 경로라 목록에서 뺐다(restore 대상 없음).

```bash
git restore --source=caee26c --staged --worktree -- \
  adapters/src/main/kotlin/bidvector/adapters/ml/EmbeddingCallPolicy.kt \
  adapters/src/main/kotlin/bidvector/adapters/ml/EmbeddingRequestMapping.kt \
  adapters/src/main/kotlin/bidvector/adapters/ml/EmbeddingResponseMapping.kt \
  adapters/src/main/kotlin/bidvector/adapters/ml/EmbeddingShapeValidation.kt \
  adapters/src/main/kotlin/bidvector/adapters/ml/GrpcBidPredictionGateway.kt \
  adapters/src/main/kotlin/bidvector/adapters/ml/GrpcEmbeddingGateway.kt \
  adapters/src/main/kotlin/bidvector/adapters/ml/MlCallPolicyData.kt \
  adapters/src/main/kotlin/bidvector/adapters/ml/MlCallPolicyPlaceholder.kt \
  adapters/src/main/kotlin/bidvector/adapters/ml/ParsedSuccessFields.kt \
  adapters/src/main/kotlin/bidvector/adapters/ml/PredictionEnvelopeMapping.kt \
  adapters/src/main/kotlin/bidvector/adapters/ml/ReleaseCheck.kt \
  adapters/src/main/kotlin/bidvector/adapters/ml/ReleaseShapeValidation.kt \
  adapters/src/main/kotlin/bidvector/adapters/ml/RequestMapping.kt \
  adapters/src/main/kotlin/bidvector/adapters/ml/ResilientPredictionCall.kt \
  adapters/src/main/kotlin/bidvector/adapters/ml/RetryRules.kt \
  adapters/src/test/kotlin/bidvector/adapters/ml/EmbeddingBreakerTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/ml/EmbeddingCallPolicyTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/ml/EmbeddingDeadlineCancellationRetryTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/ml/EmbeddingShapeFailClosedTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/ml/EmbeddingTestFixtures.kt \
  adapters/src/test/kotlin/bidvector/adapters/ml/GrpcEmbeddingGatewayTest.kt \
  workflow/src/main/kotlin/bidvector/workflow/embedding \
  workflow/src/test/kotlin/bidvector/workflow/embedding
```

`--source`에 없는 경로(신설 파일)는 삭제된다 — 별도 `git rm` 불필요. `git checkout
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

**`milestone-4.md`**(F-5 신설, 이 문서를 만지는 slice가 이 branch 안에서는 이 slice
하나뿐이다 — `git log --oneline caee26c..HEAD -- milestone-4.md` = `181892b` 단독)도
같은 방식이다:

```bash
git diff 181892b~1..181892b -- milestone-4.md | git apply -R
```

이 파일은 4A·4B·4C·4D-1 등 **다른 여러 slice의 이미 승인된 문단을 base 시점부터 담고
있다** — 그 문단들은 `caee26c` 이전에 커밋됐으므로 이 diff 범위 밖이라 위 명령이 건드리지
않는다. 확인은 「내 문단(4D-2 착수) 사라짐」과 **「다른 slice 문단(4D-1 종결·4B-4 착수 등)
그대로 남음」을 둘 다** 잰다(아래 ③).

## ③ 확인 — 임시 clone에서 실측(2026-09-11, 코드 리뷰 수정 라운드 반영 후 재실행)

| 확인 | 명령 | 결과 |
| --- | --- | --- |
| in_scope 코드 경로가 base와 동일 | `git diff caee26c -- <in_scope 코드 경로(gate-tests.properties·milestone-4.md 포함, evidence 제외)>` | 출력 0줄 |
| 내 문단(4D-2 착수) 사라짐 | `grep -c "4D-2 착수 2026-09-10(임베딩 gateway" milestone-4.md` | 0 |
| **남의 문단(4D-1 종결·4B-4 착수 등) 남음** | `grep -c "4D-1 종결\|4B-4 착수" milestone-4.md` | 3(되돌리기 전과 동일) |
| 하네스 경로 그대로 | `git diff --stat HEAD -- CLAUDE.md .claude/` | 출력 0줄(변경 없음) |
| 되돌린 트리 컴파일 | `./gradlew --offline --no-daemon :workflow:compileKotlin :workflow:compileTestKotlin :adapters:compileKotlin :adapters:compileTestKotlin` | exit 0 |
| 되돌린 트리 테스트 | `./gradlew --offline --no-daemon :workflow:test :adapters:test` | exit 0, workflow 134 / adapters 449 tests(1차·2차 rollback 실측과 완전히 동일한 수치 — F-E로 4D-1 production 파일 셋(`ParsedSuccessFields.kt`·`ReleaseCheck.kt`·`RetryRules.kt`)이 새로 restore 목록에 들어왔지만 4D-1 test 13파일은 여전히 무편집이라 골격 보존이 재확인된다) |

**목록은 자기를 담은 커밋을 가리킬 수 없다** — `scope.md`·`commands.md`·`rollback.md` 갱신
커밋은 `reports/evidence/m4/4d2/**`만 만지고 `config/quality/gate-tests.properties`·
`milestone-4.md`를 건드리지 않는다(위 목록의 `6371f28`·`181892b`는 이 evidence 커밋보다
먼저다).

**라운드마다 파일이 늘면 `git diff --name-status caee26c..HEAD`를 다시 돌려 ①의 목록을
갱신한다.**

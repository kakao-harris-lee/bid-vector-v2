# M2/2C checklist.md

## 리뷰 요청 조건 점검

- [x] 구현 diff가 커밋되어 base/head 고정 — `git status --porcelain <in_scope 22개
      경로>` 빈 출력, 양성 대조 통과(commands.md 「clean-tree 게이트」).
- [x] scope.md의 acceptance_commands(S-0~S-5) 전부 exit 0 — commands.md.
- [x] test/lint/type/architecture/contract 관련 명령 통과 — S-1(`--no-build-cache
      clean check`)이 ktlint·detekt·ArchUnit·moduleDependencyGate·sizeGate를,
      S-2(`buf lint && buf build`)가 계약 형태를 각각 검증. S-3·S-4가 계약 규칙
      (전이표·idempotency·조합 불변식·fail-closed)을 양쪽 언어에서 증명.
- [x] 변경된 fixture와 정책 version의 근거 기록 — `contracts/testdata/training/*`는
      round-trip·fake servicer 표본(fixture corpus와 별개 축, scope.md in_scope
      주석). 값 근거는 아래 「testdata 값 근거」. 정책 version 신규 없음(2C는 wire
      형태만 고정 — `training_spec_version`은 값이 아니라 참조 필드).
- [x] 알려진 제한과 rollback 기록 — 아래 「알려진 제한」·`rollback.md`(임시 clone
      실측 완료, commands.md).
- [x] secret 스캔 통과 — commands.md.

## 커밋 목록(진행 순서)

1. `f50bb0d` — `training.proto`(`TrainingJobService` 3 RPC + 상태 enum·전이표
   주석·참조 메시지). buf lint 요구로 `TrainingRequest` → `StartTrainingRequest`
   개명(「판단이 갈린 지점」).
2. `8093131` — testdata canonical 바이트 8종(binpb+json).
3. `f30c16a` — `TrainingContractTest.kt`(fake servicer 실제 상태 기계 + 순수 함수,
   42 tests) + `config/quality/gate-tests.properties` 등재 + `scope.md` in_scope
   갱신(「판단이 갈린 지점」).
4. `e8602f6` — `test_training_contract.py`(Python 대칭, 42 tests). well-known type
   `_proto` 경로 추가(「판단이 갈린 지점」).
5. `9eeecfd` — `TrainingContractTest.kt` 500줄 사이즈 게이트 위반(698줄) 수정.
   `TrainingContractRules.kt`(순수 함수)·`FakeTrainingJobServicer.kt`(상태 기계)로
   분리, 동작 변경 없음(「판단이 갈린 지점」).
6. `2e08937` — evidence(commands/checklist/rollback.md), r1 리뷰 요청 시점.
7. `c99cc5d` — verifier r1 F-1·F-3 잔여 반영(코드+test 한 커밋, 재검증 없음). F-2는
   team-lead가 `capability-map.md` §14.3에 별도 등재.

## verifier r1 finding 반영

| id | 심각도 | 반영 |
| --- | --- | --- |
| F-1 | medium | `isValidJobCombination`/`_is_valid_job_combination`의 `UNSPECIFIED`·정의 밖 정수 분기(`-> false`)를 단언하는 test를 언어당 1개 추가(`UNSPECIFIED 정의 밖 정수 상태의 job 은 조합 불변식을 위반한다`/`test_unspecified_or_undefined_state_job_violates_combination_invariant`). 해당 분기를 `true`로 바꾸는 변이(K14/P14)를 실제로 주입해 양쪽 언어 red 확인 후 원복(commands.md) |
| F-2 | low | team-lead 소관 — 이 커밋에서 건드리지 않음 |
| F-3 | low | testdata 로더(`bytes`)·`canonicalBytes`가 `ContractRoundTripTest`(2A)·`PredictionContractTest`(2B)·`TrainingContractTest`(2C) 세 파일에 중복돼 있던 것을 `ContractTestdataSupport.kt`(신규, 2A `ContractFractionRules.kt`와 같은 관례)로 추출. `adapters-observed.xml`의 `<duplication>` 태그 2건 → 0건(commands.md) |

## 판단이 갈린 지점 — team-lead 지시와 다르게 구현한 자리

1. **RPC 요청 타입 명명** — team-lead 지시는 `TrainingRequest`였으나 buf lint
   STANDARD ruleset이 "RPC request type ... should be named `StartTrainingRequest`
   또는 `TrainingJobServiceStartTrainingRequest`"를 요구해 `StartTrainingRequest`로
   개명했다(S-2가 hard gate). 필드 구성·의미는 지시 그대로.
2. **`config/quality/gate-tests.properties` 등재** — scope.md 초안 in_scope에는
   없었으나 team-lead 지시("gate.tests.adapters 등재 수 갱신")를 따라 scope.md
   in_scope에 이 경로를 추가하고 등재했다(2B `d097fe0` 관례 — 세션 모델이 사후
   정정). scope 확장 사유를 scope.md 자체에 남겼다.
3. **`google.protobuf.Timestamp` 생성 경로** — D-2C-5는 "buf·protoc·grpc_tools 세
   도구 모두 로컬로 생성"을 전제했다. buf·Kotlin(`ml-contract`, protobuf-java
   런타임 jar 번들)은 즉시 성립했으나 `grpc_tools.protoc`는 well-known type을
   자동 해석하지 못해(`File not found`) `grpc_tools` 배포에 번들된 `_proto`
   디렉터리를 별도 `--proto_path`로 추가해야 했다. BSR 의존은 여전히 없다(D-2C-5
   이탈 아님 — 세 도구 모두 로컬 완결이라는 결론은 그대로, 경로 지정 방식만 도구별로
   다르다는 사실이 새로 드러났다).
4. **파일 500줄 한도 초과 시정** — `TrainingContractTest.kt`(698줄)이
   `adapters:sizeGate`를 위반해, 2A `ContractFractionRules.kt` 관례를 따라
   순수 함수(`TrainingContractRules.kt`)와 fake servicer(`FakeTrainingJobServicer.
   kt`)를 별도 파일로 분리했다. 「크기 회피용 기계적 분할 금지」 원칙에 따라 임의
   분할이 아니라 응집도 있는 두 단위(계약 규칙 vs 상태 기계)로 나눴다.
5. **testdata 값** — team-lead 메시지는 구체 필드 값을 지정하지 않아 세션이
   재현 가능한 값(sha256 hex는 `shasum -a 256` 실측, timestamp는 순서 불변식을
   만족하는 임의 UTC 값, `release_id`·`dataset_id` 등은 2B 관례를 잇는 이름)을
   직접 정했다 — 아래 「testdata 값 근거」.

## ①~⑨ 대응표 — team-lead 지시 항목과 그것을 증명하는 게이트/테스트

| # | 항목 | 증명 |
| --- | --- | --- |
| ① | 3 RPC 전부 unary, `StartTraining`은 같은 `idempotency_key`에 같은 `job_id` | `training.proto` service 정의(unary만) + `TrainingContractTest`/`test_training_contract.py`의 「같은 idempotency_key 두 번은 같은 job_id」 + 변이 실측 1 |
| ② | `DatasetReference{uri, manifest_checksum, dataset_id}`, checksum 불일치는 job 안 FAILED | `training.proto` 메시지 정의 + `isValidSha256Hex`/`_is_valid_sha256_hex`(형식 검증), 실제 checksum 대조는 out_of_scope(M5 5C) |
| ③ | `GetTrainingJob` 조합 불변식(SUCCEEDED⟹artifact+evaluation, FAILED⟹failure) | `isValidJobCombination`/`_is_valid_job_combination` + 변이 실측 3 |
| ④ | `JobState` 전이표, 표 밖 전이 거부, `UNSPECIFIED`·정의 밖 정수 거부 | `isAllowedTransition`/`_is_allowed_transition` + 변이 실측 2, `isAcceptableJobState`/`_is_acceptable_job_state` |
| ⑤ | `CancelTrainingJob`은 ACCEPTED·RUNNING에서만 CANCELLED, 종료 상태 멱등 no-op | `FakeTrainingJobServicer.cancelTrainingJob`/Python `CancelTrainingJob` 실제 RPC test + 변이 실측 5 |
| ⑥ | retry는 새 job(재시도 RPC 없음), `JobFailure.retryable` | `training.proto`에 재시도 RPC 부재(구조), `JobFailure` 메시지 정의 |
| ⑦ | `ArtifactReference{uri, release, manifest_schema_version}`, checksum 한 자리(`release.artifact_checksum`), `dataset_id` 일치 | `training.proto` 메시지 정의(checksum 필드 하나) + `artifactMatchesRequestedDataset`/`_artifact_matches_requested_dataset` + 변이 실측 6 |
| ⑧ | `EvaluationReportReference{uri, checksum, report_schema_version}` — 참조만, 내용 없음 | `training.proto` 메시지 정의(내용 필드 없음) |
| ⑨ | 전부 unary, 같은 gRPC 서버, `StartTraining` 즉시 ACCEPTED | `training.proto` service 정의 + fake servicer 실제 RPC test 「StartTraining 은 즉시 ACCEPTED 를 낸다」(양쪽 언어) |

## 위협 모델 「방어한다」 목록과의 대응(scope.md)

(a) 중복 학습 → idempotency 대조 + 변이 실측 1·4(양쪽 언어 실제 RPC test).
(b) 미지 상태의 조용한 접힘 → `isAcceptableJobState`/`isAcceptableJobFailureCode`
(UNSPECIFIED·`UNRECOGNIZED`/정의 밖 정수 거부) + `isAllowedTransition`(표 밖 전이
거부, 변이 실측 2). (c) `SUCCEEDED`인데 artifact 없음 / `FAILED`인데 failure 없음 →
`isValidJobCombination` + 변이 실측 3. (d) dataset이 요청 시점과 다른 것으로 바뀜 →
`DatasetReference`가 immutable reference + checksum 필드를 가지나, **실제 바이트
동일성 검증은 이 slice가 하지 않는다**(out_of_scope, 아래 알려진 제한 1). (e) Celery·
task 이름의 wire 유입 → `training.proto`에 그런 필드가 없음(육안 대조, H-12).
(f) 응답 `artifact`의 `dataset_id`가 요청과 다름 → `artifactMatchesRequestedDataset`
+ 변이 실측 6. (g) 동기 RPC로 학습을 붙잡음 → 전부 unary + `StartTraining` 즉시
`ACCEPTED` 실제 RPC test.

**방어하지 않는다**(scope.md 명시, 이 slice가 실측하지 않음): ml-engine이 실제로
취소를 존중하는가(5C) · 폴링 주기·재시도(M4/ADR 0005) · dataset 내용 무결성(checksum
형식만 검증, 바이트 동일성은 아님) · 인증·권한(M6) · manifest 서명 검증(5C) ·
fork-불안전(5A).

## 우회 후보(≥5, scope.md) 대응

| # | 우회 | 막는 장치 |
| --- | --- | --- |
| (1) | `idempotency_key`를 빈 문자열로 | `isValidStartTrainingRequest`/`_is_valid_start_training_request`가 `false`(필수 필드 검증, 순수 함수 — 실제 Kotlin/Python validation 구현은 M4/5C 몫) |
| (2) | `RUNNING → ACCEPTED` 역전이 | `isAllowedTransition`/`_is_allowed_transition` + 변이 실측 2 |
| (3) | `CANCELLED`에 artifact 첨부 | `isValidJobCombination`/`_is_valid_job_combination`(CANCELLED는 artifact·evaluation·failure 전부 미설정 요구) |
| (4) | `dataset.uri`에 DB 연결 문자열 | 계약은 못 막는다(uri는 불투명) — 5A의 import boundary(§3.2) 소관, **알려진 제한** |
| (5) | `JobFailure.detail_code`에 Celery 원문 | lint는 못 잡는다 — **리뷰 항목**, 알려진 제한 |
| (6) | `GetTrainingJob`을 스트리밍으로 바꿈 | breaking gate 밖(2D 소관), 이 slice는 `training.proto`가 전부 unary임을 정적으로 보장 |
| (7) | 같은 `idempotency_key`로 다른 `dataset` | `ApplicationFailure(IDEMPOTENCY_CONFLICT)` 실제 RPC test + 변이 실측 1·4 |

## testdata 값 근거

- `dataset_id="dataset-2026-09-002"`·`manifest_checksum`(sha256 hex, `printf
  'bidvector-2c-dataset-002' | shasum -a 256`로 실측 생성, 임의 입력 문자열의
  실제 sha256 — 계약이 요구하는 「64자 hex」 형식 실측용이지 실제 dataset 내용과
  무관) — `artifact_checksum`·evaluation `checksum`도 같은 방식(각각 다른 입력
  문자열).
- `training_spec_version="training-spec-2026.3"` — D-2C-2 (a)에 따른 임의
  버전 문자열(ml-engine 쪽 versioned spec은 5C 소유, 이 slice는 참조 문자열
  형태만 고정).
- `job_id="018f2b21-9c4a-7c3d-8b2a-6e1f4d9a7c53"` — D-2C-3 권고(UUID 형식) 예시,
  계약이 형식을 강제하지 않음을 보이기 위해 fake servicer는 별도 형식
  (`fake-job-N`)을 발급한다.
- timestamp 셋(`accepted_at`·`started_at`·`finished_at`)은 D-2C-5 순서 불변식을
  만족하는 임의 2026-09-07 UTC 값.
- `release_id="release-2026-09-07-a"`·`feature_schema_version="award-rate-v1"`·
  `code_version="ml-engine-0.1.0"`은 2B testdata 관례를 이어 재사용.

## 알려진 제한

1. **dataset의 실제 바이트 무결성은 이 slice가 검증하지 않는다** — `manifest_
   checksum`은 형식(sha256 hex)만 정규형 검사 대상이고, 실제 파일 내용과의 대조는
   ml-engine(5C)이 job 실행 중에 한다(scope.md ② — 검증은 읽을 때 일어난다).
2. **`dataset.uri`의 scheme은 계약이 제한하지 않는다**(`OPEN-2C-DATASET-URI-SCHEME`)
   — DB 연결 문자열이 여기 실려도 이 계약은 못 막는다(우회 후보 (4)). 5A의 import
   boundary(§3.2)가 실제 방어선.
3. **`JobFailure.detail_code`에 Celery 원문이 실릴 수 있다**(우회 후보 (5)) — lint로
   못 잡는 사람 리뷰 항목.
4. **`optional` 필드 추가는 breaking gate가 못 잡는다** — 2B와 같은 한계(계약에
   업무 판정 필드가 `optional`로 슬쩍 추가되는 경로는 리뷰 항목).
5. **실제 socket·deadline·cancel 존중·폴링은 2D·M4·5C 몫** — 이 slice의 consumer/
   provider test는 전부 in-process(Kotlin, 실제 gRPC 왕복이지만 in-process
   channel) 또는 직접 호출(Python)이다.
6. **`FakeTrainingJobServicer`/`_FixtureServicer`는 실제 학습을 흉내 내지 않는다**
   — `advanceToRunning`/`advance_to_running`은 test 전용 헬퍼로 RUNNING 전이만
   시뮬레이션하고, `SUCCEEDED`/`FAILED` 전이는 fake가 만들지 않는다(그 상태의
   testdata는 静적 fixture로만 존재) — cancel이 SUCCEEDED/FAILED 상태의 job에
   호출됐을 때의 실측은 fake의 상태 기계로는 만들 수 없다(fake는 그 상태로
   전이하는 경로가 없다). 정적 fixture(`get_training_job_response_cancelled.
   binpb` 등)를 직접 파싱해 조합 불변식은 검증했지만, "실행 중에 SUCCEEDED가 된
   job을 취소하면 no-op"이라는 시나리오는 fake 상태 기계 위에서 실측하지 못했다
   — 5C가 실제 servicer를 구현할 때 재확인 대상.
7. `OPEN-2C-FAILURE-CODES`·`OPEN-2C-DATASET-URI-SCHEME`(scope.md 신설 후보)는
   이 slice가 닫지 않는다 — 전자는 5C 확장(호환 추가), 후자는 5A·M6 6C 소관.

## OPEN 처리

| OPEN | 2C 처리 |
| --- | --- |
| `OPEN-2C-FAILURE-CODES` | `JobFailureCode` 최소 집합의 5C 확장 — 호환 추가 규칙(제공자 먼저 배포). `capability-map.md` §14.3 등재 완료(verifier r1 F-2, team-lead `62abbef`) |
| `OPEN-2C-DATASET-URI-SCHEME` | `DatasetReference.uri`의 허용 scheme — 5A·M6 6C 소관. `capability-map.md` §14.3 등재 완료(verifier r1 F-2, team-lead `62abbef`) |

## 사용자 승인

대기 — verifier ready-for-review 이후 운영자 결정(CLAUDE.md 운영자 지시
2026-09-04, 코드 slice는 Codex 심판 제외).

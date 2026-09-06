# M2/2C commands.md

정본은 명령과 exit code, 핵심 결과 한 줄이다. 출력 전문은 재현 명령으로 대신한다.
base `fa2bb72`(2B 종결·2C 착수 시점), head는 리뷰 시점의 HEAD — 구체 SHA는 여기 박지
않는다(2B 관례). 커밋 목록은 `checklist.md` 「커밋 목록」이 갖는다.

## training.proto — buf lint/build + ml-contract 생성 확인

- cmd: `(cd contracts && buf lint && buf build)`
- exit: 0
- 핵심 결과: lint 위반 0, build 성공. `TrainingRequest`를 `StartTrainingRequest`로
  개명(buf lint STANDARD의 RPC 요청 타입 명명 규칙 — 「판단이 갈린 지점」 참고).

- cmd: `./gradlew --project-dir ml-contract build`
- exit: 0
- 핵심 결과: `TrainingJobServiceGrpc.java`(grpc-java)·`TrainingGrpcKt.kt`
  (`TrainingJobServiceCoroutineImplBase`·`TrainingJobServiceCoroutineStub` 포함,
  grpc-kotlin) 생성 확인.

## testdata 생성(재현 절차) — `contracts/testdata/training/`

- cmd: JSON 원본 8개를 손으로 작성(요청·ACCEPTED 핸들·RUNNING·SUCCEEDED·FAILED·
  CANCELLED·IDEMPOTENCY_CONFLICT 실패·JOB_NOT_FOUND 실패) 후 `buf convert . --type
  bidvector.ml.v1.<Type> --from <name>.json --to <name>.binpb`를 3개 타입
  (`StartTrainingRequest`·`StartTrainingResponse`×2·`GetTrainingJobResponse`×5)에 대해
  실행
- exit: 0(8/8)
- 핵심 결과: canonical `.binpb` 8개 + JSON 원본 8개 커밋.

## testdata round-trip 자체 검증 — JSON → binpb → JSON 의미 동등

- cmd: `buf convert . --type bidvector.ml.v1.<Type> --from <name>.binpb --to
  -#format=json` 후 원본 JSON과 `jq -S` 정렬 비교(8개 전건)
- exit: 0(8/8 의미 동등)
- 핵심 결과: 5개는 완전 일치, 3개(`JobFailure`·`ApplicationFailure` 포함 응답)는
  `retryable: false`(bool 기본값)가 canonical JSON 출력에서 생략됨 — proto3 기본값 필드
  elision(2B `ApplicationFailure`와 같은 표준 동작, 결함 아님). 와이어 바이트는 두 경로
  모두 동일.

## S-3 — round-trip + fake servicer consumer test(Kotlin)

- cmd: `./gradlew :adapters:test --tests '*Training*Contract*'`
- exit: 0
- 핵심 결과: `TrainingContractTest` `tests="42"`, `failures="0"`(`@Test` 개수 42와 일치 —
  discovery guard 미충돌 확인). 전부 블록 본문.

## S-4 — round-trip + fake servicer provider test(Python)

- cmd: `(cd ml-engine && .venv/bin/python -m pytest tests/test_training_contract.py
  tests/test_contract_roundtrip.py -q)`
- exit: 0
- 핵심 결과: 51 passed(신규 42 + 2A `test_contract_roundtrip.py` 9). 신규 의존 추가
  없음(가상환경 `ml-engine/.venv`, grpcio-tools 1.83.1 그대로).
- **`google.protobuf.Timestamp` well-known type 실측**: `grpc_tools.protoc`는 buf와
  달리 well-known type을 자동 해석하지 못해 최초 시도가 `File not found`로 실패(exit
  1) — grpc_tools 배포 번들 `_proto`(`Path(grpc_tools.__file__).parent / "_proto"`)를
  별도 `--proto_path`로 추가해 해결(로컬 완결, 네트워크 없음). `contracts/buf.yaml`에는
  `deps` 없음 — BSR 의존은 여전히 없다(D-2C-5 그대로 채택, 이탈 없음).
- `conftest.py`(2A, scope.md in_scope 밖)는 무편집.

## S-1 — 기존 게이트 전건(ktlint·detekt·ArchUnit·moduleDependencyGate·sizeGate 등)

- cmd: `./gradlew --no-build-cache clean check`
- exit: 0(1차 시도는 `adapters:sizeGate` FAILED — `TrainingContractTest.kt` 698줄이
  500줄 한도 초과. 순수 함수를 `TrainingContractRules.kt`로, fake servicer를
  `FakeTrainingJobServicer.kt`로 분리해 500줄로 맞춘 뒤 재실행 exit 0. 그 사이
  ktlint 위반도 `ktlintTestSourceSetFormat`으로 자동 정리)
- 핵심 결과: 322 actionable tasks, BUILD SUCCESSFUL.

## S-5 — quality baseline

- cmd: `./gradlew qualityBaseline`
- exit: 0
- 핵심 결과: `build/reports/quality-baseline/quality-baseline.md` 갱신(UP-TO-DATE).

## S-0 — clean worktree 전건

- cmd: `git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew
  --no-build-cache clean check)`
- exit: 0
- 핵심 결과: 331 actionable tasks, 전부 executed(캐시 없음), BUILD SUCCESSFUL.
  `git worktree remove --force`로 제거, 잔여 디렉터리 없음 확인(`ls` → No such
  file or directory).

## 변이 실측(≥6, 계약이 실제로 우회를 막는지 — 양쪽 언어)

1. **같은 idempotency_key 두 번은 job 을 하나만 만든다** — `FakeTrainingJobServicer.
   startTraining`/Python `_FixtureServicer.StartTraining`의 dedup 분기를 일시
   제거(`return acceptNewJob(request)`만 남김)하고 재실행 → Kotlin `tests
   completed=42, failed=2`, Python `3 failed, 39 passed`(dedup 분기가 idempotency
   conflict·spec 거부까지 겸하고 있어 세 test 가 동시에 red). 원복 후 재실행 →
   Kotlin/Python 전부 green(git diff 로 원복 확인).
2. **역전이 `RUNNING → ACCEPTED` 는 거부된다** — `isAllowedTransition`/
   `_is_allowed_transition`이 명시적 허용 집합(`setOf`/`set`) 밖의 쌍을 전부 `false`로
   낸다. 같은 파일의 「전이표에 있는 쌍은 전부 허용된다」(true)·「역전이 …는 거부된다」
   (false) 쌍으로 함수가 실제로 판별함을 실측(양쪽 언어).
3. **`SUCCEEDED` 인데 `artifact` 없음은 불변식 위반** — testdata의 `artifact`를
   `clearArtifact()`/`ClearField("artifact")`로 지운 사본에 `isValidJobCombination`/
   `_is_valid_job_combination`이 `false`(원본은 `true`, 양쪽 언어).
4. **같은 키에 다른 dataset 은 `IDEMPOTENCY_CONFLICT`** — 변이 1과 같은 실측(dedup
   분기 제거 시 dataset 대조 자체가 사라져 두 언어 모두 해당 test 가 red).
5. **종료 상태 Cancel 이 오류를 내면 계약 위반** — `FakeTrainingJobServicer.
   cancelTrainingJob`/Python `CancelTrainingJob`의 종료 상태 분기를 「no-op」에서
   「`JOB_NOT_FOUND` 오류」로 바꾸고 재실행 → Kotlin `tests completed=42, failed=1`
   (`CancelTrainingJob 은 종료 상태에서 멱등 no-op 이다` red), Python
   `test_cancel_training_job_is_idempotent_no_op_in_terminal_state` red(`assert
   'failure' == 'job'`). 원복 후 재실행 → 양쪽 green.
6. **`artifact.release.dataset_id` 가 요청과 다르면 불변식 위반** —
   `artifactMatchesRequestedDataset`/`_artifact_matches_requested_dataset`에 다른
   `dataset_id`로 덮어쓴 `release` 사본을 넣으면 `false`(원본은 `true`, 양쪽 언어).

변이 1·4·5는 실제 프로덕션(fake servicer) 코드를 일시 수정→재실행→원복(`git
checkout --`/파일 백업 복원)으로 확인했다. 변이 2·3·6은 순수 함수라 같은 test 파일
안의 양성/음성 쌍으로 판별력을 실측했다(2B `PredictionContractTest`와 같은 관례).
원복 후 `git status --porcelain`이 빈 출력임을 확인.

## secret 스캔

- cmd: `git diff fa2bb72..HEAD --name-only -- contracts/ ml-engine/ adapters/
  config/ milestone-2.md | xargs grep -lniE
  '(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))'`
- exit: 1(grep 매치 없음 = 정상)
- 핵심 결과: 변경 파일 전건 매치 0건.

## 하네스 레인 변경

- cmd: `git log --oneline fa2bb72..HEAD -- CLAUDE.md .claude/`
- exit: 0
- 핵심 결과: 출력 없음 — 없음. `fa2bb72..HEAD` range의 커밋 6개는 전부 이 slice
  산출물(`f50bb0d`·`8093131`·`f30c16a`·`e8602f6`·`9eeecfd`)과 착수 커밋
  (`08af165`, 2C 착수 문단·D-2C-1·2 (a) 기록 — 세션 모델 단독 저작, 2C 구현 이전에
  이미 존재)뿐이다.

## clean-tree 게이트(경로 개별 인자 + 양성 대조)

- cmd: `git status --porcelain <in_scope 22개 경로 개별 인자>`
- exit: 0
- 핵심 결과: 빈 출력(전건 커밋됨). `reports/evidence/m2/2c/**` 자체는 이 시점에도
  계속 편집 중이라 목록에서 제외.
- 양성 대조: `training.proto`에 한 줄 추가 → `git status --porcelain`이 `M`을 냄 →
  `git checkout -- <파일>`로 원복 → 다시 빈 출력.

## rollback 실측(임시 clone)

- cmd: 아래 `rollback.md`의 명령 그대로, 임시 clone(`/tmp`)에서 실행.
- exit: 0
- 핵심 결과: 지정 21개 경로가 base(`fa2bb72`) 상태로 복원(`git status --porcelain`
  22행 — 신규 파일 21개 삭제 + `gate-tests.properties` 수정 1개). `git diff fa2bb72 --
  config/quality/gate-tests.properties`가 빈 출력(내용이 base와 완전히 같음).
  복원 뒤 `(cd contracts && buf lint && buf build)` exit 0(2A/2B 상태 —
  `TrainingJobService` 없는 계약으로 정상 복귀, `common.proto`·`error.proto`·
  `features.proto`·`prediction.proto`만으로 계약이 자족).

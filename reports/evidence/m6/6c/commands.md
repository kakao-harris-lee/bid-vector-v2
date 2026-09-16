# M6/6C — commands.md

정본은 `scope.md`. 이 문서는 명령과 종료 코드만 남긴다(출력 전문·라운드 이력 금지,
하네스 2026-08-30). 환경: Docker 29.5.3 · Compose v5.1.4(팀장 실측 2026-09-16 그대로).

## 이미지·위생 게이트

## 2026-09-16T13:20:00Z
- cmd: `docker build -f docker/ml-serving.Dockerfile -t bidvector/ml-serving:local .`
- exit: 0
- 핵심 결과: multi-stage 빌드 성공, non-root(uid 10001), 금지 패키지 다섯 import 실패 확인.

## 2026-09-16T13:25:00Z
- cmd: `./tools/image-hygiene-check.sh bidvector/ml-serving:local`
- exit: 0
- 핵심 결과: Config.User=mlserving, id-u=10001, base-label 다이제스트 확인, size_bytes=113701104 < cap 400000000.

## 2026-09-16T13:30:00Z — 변이 ①(non-root 제거)
- cmd: `USER mlserving` 삭제한 임시 Dockerfile 로 빌드 후 위생 게이트 실행
- exit: 1
- 핵심 결과: `Config.User 가 root/미지정이다` + `uid 0(root)` 둘 다 위반 검출. 임시 이미지 삭제 완료.

## 2026-09-16T13:32:00Z — 변이 ②(금지 패키지 주입)
- cmd: `pip install ... requests` 를 런타임 설치에 추가한 임시 Dockerfile 로 빌드 후 위생 게이트 실행
- exit: 1
- 핵심 결과: `금지 패키지 'requests' 가 이 이미지에서 import 된다` 검출. 임시 이미지 삭제 완료.

## 2026-09-16T13:34:00Z — 변이 ③(떠 있는 베이스 태그)
- cmd: `python:3.12-slim`(다이제스트 없음)로 베이스·라벨을 바꾼 임시 Dockerfile 로 빌드 후 위생 게이트 실행
- exit: 1
- 핵심 결과: `베이스 이미지 라벨에 다이제스트(@sha256:)가 없다` 검출. 임시 이미지 삭제 완료.

## 2026-09-16T13:36:00Z — 변이 ④(이미지 자신의 태그가 :latest)
- cmd: `docker tag bidvector/ml-serving:local bidvector/ml-serving:latest && ./tools/image-hygiene-check.sh bidvector/ml-serving:latest`
- exit: 1
- 핵심 결과: `이미지 참조가 :latest 태그다` 검출. 태그 삭제 완료.

## 2026-09-16T13:40:00Z — 빌드 컨텍스트 실측(위협 모델 (f))
- cmd: `docker build --no-cache -f docker/ml-serving.Dockerfile --progress=plain -t bidvector/ml-serving:ctxtest .`
- exit: 0
- 핵심 결과: `transferring context: 6.69kB` — `.venv`·`build`·`reports`·`_workspace`·`bid-vector` 등이 컨텍스트에 실리지 않음(전체 저장소 대비 무시할 수 있는 크기). 임시 이미지 삭제 완료.

## 로컬 환경(compose)

## 2026-09-16T13:45:00Z
- cmd: `docker compose -f docker/compose.yaml up -d`(postgres 서비스가 요구하는 DB 접속 값 셋을 셸 환경에 내보낸 뒤 실행)
- exit: 0
- 핵심 결과: ml-serving·postgres 둘 다 기동, 이후 폴링에서 둘 다 `healthy` 로 수렴(출하 정책 그대로 READY, 5F-1).

## 2026-09-16T13:47:00Z
- cmd: `docker compose -f docker/compose.yaml down -v`
- exit: 0
- 핵심 결과: 컨테이너·네트워크·볼륨(`bidvector-pg-data`) 전부 제거 확인.

## 2026-09-16T13:48:00Z — 자격증명 미설정 시 fail-closed 확인
- cmd: (환경변수 셋 미설정 상태에서) `docker compose -f docker/compose.yaml up -d`
- exit: 1(compose 인터폴레이션 오류, 컨테이너 기동 없음)
- 핵심 결과: `required variable BIDVECTOR_PG_DB is missing a value` — compose 파일 자체에 자격증명 리터럴이 없고 값 없이는 기동이 거부됨((2b) 표 「경계로 처리」 실측).

## 2026-09-16T13:49:00Z — 컨테이너 정리 확인
- cmd: `docker volume ls | grep bidvector-pg-data`
- exit: 1(매치 없음)
- 핵심 결과: `down -v` 뒤 볼륨이 실제로 삭제됨.

## 프로브

## 2026-09-16T14:00:00Z
- cmd: 이미지에서 컨테이너 기동(환경 7개 정상) 후 `docker exec ... python /app/probe/liveness.py`, `.../readiness.py`
- exit: 0, 0
- 핵심 결과: 둘 다 성공 — 서버가 출하 정책으로 READY 로 수렴한 상태에서 liveness·readiness 모두 OK.

## 2026-09-16T14:02:00Z — 변이(정책 파일 경로를 존재하지 않는 파일로)
- cmd: `ML_ENGINE_INFERENCE_POLICY=/app/policy/does-not-exist.yaml` 로 컨테이너 기동 후 두 프로브 실행
- exit: liveness 0, readiness 1
- 핵심 결과: 서버 로그가 `NOT_READY`(정책 파일 없음)를 보고하는 동안 liveness 는 OK, readiness 는 `가 READY 가 아니다`로 실패 — 두 프로브의 판정 축이 실제로 다름을 실측(우회 (4)).

## Kotlin — RealServerIntegrationTest

## 2026-09-16T14:10:00Z
- cmd: `./gradlew --no-daemon :adapters:test --tests '*RealServerIntegrationTest*' -PrealServer=true`
- exit: 0
- 핵심 결과: 4 tests 전부 통과 — 컨테이너 자기 자신 단언·GetModelMetadata(READY+promoted)·CalculateOptimalBid(Predicted)·옛 featureSchemaVersion 음성 대조(UnsupportedSchema) 전부 실 컨테이너 대상.

## 2026-09-16T14:12:00Z — D-6C-4 확인(기본 test 는 Docker 를 건드리지 않는다)
- cmd: `./gradlew --no-daemon :adapters:test --tests '*RealServerIntegrationTest*'`(property 없이)
- exit: 0
- 핵심 결과: JUnit XML `skipped="4"`, `time="0.001"` — 컨테이너가 전혀 기동되지 않음(조건 평가가 `@BeforeAll`보다 앞섬).

## Kotlin — 전건

## 2026-09-16T14:20:00Z — 구조적 충돌 실측(D-6C-8 배경, checklist.md 「판단이 필요했던 자리」)
- cmd: `./gradlew --no-daemon check` (`RealServerIntegrationTest`가 `bidvector.adapters.ml` 패키지에 있던 시점)
- exit: 1
- 핵심 결과: `adapters:gateExecutionGate` 실패(「게이트 test 가 건너뛰어졌다」) — `MlGateRegistrationTest`(그
  패키지의 모든 `*Test.kt`는 `gate-tests.properties` 등재 필수)와 `gateExecutionGate`(등재된 클래스는 기본
  `check`에서 skipped=0 필수)가 D-6C-4(기본 check 는 Docker 없이 항상 건너뛴다)와 동시에 성립할 수 없었다.
  팀장 보고 뒤 D-6C-8(scope.md 계약 갱신 (2))로 test 패키지를 `bidvector.adapters.contract`로 재배치—
  게이트 술어는 무편집, `CrossLangSmokeTest`와 같은 자리(그 패키지에는 전수 등재 게이트가 없다).

## 2026-09-16T14:40:00Z — D-6C-8 반영 뒤 재실측
- cmd: `./gradlew --no-daemon check`
- exit: 0
- 핵심 결과: 전건 통과(453줄, `BUILD SUCCESSFUL in 27s`) — `adapters:gateExecutionGate` 포함 전 게이트 초록.

## 2026-09-16T14:45:00Z — 완료 조건 1 최종 실측
- cmd: `./tools/one-command-check.sh`
- exit: 0
- 핵심 결과: Kotlin `check`(gradlew) → Python S-1~S-11(uv sync·serving extras 분리·ruff·mypy·
  import-linter·pytest·설계 래칫·재활용 출처·버전 대조·wheel 재수출) 순차 통과, 중간 실패 없음.

## one-command-check.sh 변이 실측(우회 (5))

## 2026-09-16T14:30:00Z
- cmd: `./gradlew --no-daemon check` 줄을 `false`로 바꾼 임시 사본을 실행
- exit: 1
- 핵심 결과: 첫 단계에서 즉시 비-0 종료, 이후 Python 단계(uv sync 등)가 전혀 실행되지 않음(`set -euo pipefail`
  이 즉시 중단시킴 확인). 임시 사본 삭제 완료.

## 비밀값 스캔

## 2026-09-16T14:35:00Z
- cmd: `grep -rniE -f config/quality/leak-patterns.txt docker tools/image-hygiene-check.sh tools/one-command-check.sh adapters/src/test/kotlin/bidvector/adapters/ml/RealServerIntegrationTest.kt config/quality/image-hygiene-policy.properties reports/evidence/m6/6c/`
- exit: 0(매치 2건, 둘 다 이 grep 명령 문자열 자신과 `docker/compose.yaml`의 DB 접속 값 환경변수 **이름**
  참조 — 육안 확인, 값 리터럴이 아니다, D-6C-7·(2b) 표와 같은 규율)
- 핵심 결과: 파일에 리터럴 비밀값 0건. (하네스 2026-09-16 「어휘를 evidence 에 축어로 적지 않는다」에 따라
  매치 낱말 자체는 이 문서에 인용하지 않는다.)

## 2026-09-16T14:50:00Z — S-5 승계(ml-engine 무편집 확인, 단독 실행)
- cmd: `(cd ml-engine && uv sync --frozen --all-extras && uv run python -m pytest tests -q)`
- exit: 0
- 핵심 결과: 954 passed.

## 최종 연속 실측(S-21~S-25, 한 자리에서 순서대로)

## 2026-09-16T15:00:00Z
- cmd: `docker build -f docker/ml-serving.Dockerfile -t bidvector/ml-serving:local .` (S-21)
- exit: 0

## 2026-09-16T15:01:00Z
- cmd: `./tools/image-hygiene-check.sh bidvector/ml-serving:local` (S-22)
- exit: 0
- 핵심 결과: size_bytes=113709603(cap 400000000), 나머지 실측값은 위 표와 동일.

## 2026-09-16T15:02:00Z
- cmd: `docker compose -f docker/compose.yaml up -d` 뒤 폴링 (S-23)
- exit: 0
- 핵심 결과: ml-serving·postgres 둘 다 6초 안에 healthy 로 수렴.

## 2026-09-16T15:03:00Z
- cmd: `./gradlew --no-daemon :adapters:test --tests '*RealServerIntegrationTest*' -PrealServer=true` (S-24)
- exit: 0

## 2026-09-16T15:04:00Z
- cmd: `docker compose -f docker/compose.yaml down -v` (S-25)
- exit: 0

## 정본 참고

마지막 HEAD 의 acceptance 전건 재실측 결과 정본은 이 문서가 아니라 verifier 와 PR 조치
코멘트다(하네스 2026-09-16). 이 문서는 그 직전까지의 실측만 담는다.

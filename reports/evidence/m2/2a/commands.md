# M2/2A commands.md

정본은 명령과 exit code, 핵심 결과 한 줄이다. 출력 전문은 재현 명령으로 대신한다.
base `040ab9d`, head는 리뷰 시점의 HEAD — 구체 SHA 를 여기 박지 않는다(verifier r1 F-1,
값을 박으면 후속 커밋마다 낡는다). Phase 3 구현 커밋 목록은 진행 순서로 `checklist.md`
「커밋 목록」이 갖는다.

## §4b 스모크 (선행, 구현 순서 0)

- cmd: `./gradlew --project-dir ml-contract build`
- exit: 0
- 핵심 결과: protoc + protoc-gen-grpc-java + protoc-gen-grpc-kotlin(jdk8) 좌표 해석·실행
  성공(2A `.proto`에 `service` 없어 두 플러그인은 무출력 — 정상). 컴파일·jar 생성 성공.

- cmd: `./gradlew :adapters:test --tests '*GrpcKotlinStackSmokeTest*'`
- exit: 0
- 핵심 결과: 1/1 통과 — grpc-kotlin-stub 1.5.0(POM `grpc-stub:1.62.2`)이 grpc-bom 1.84.0
  정렬 위에서 in-process 채널로 인스턴스화·`withDeadlineAfter`(→`build()`) 재구성 성공.
  `NoSuchMethodError`/`AbstractMethodError` 없음.

## S-0 — clean worktree 전건

- cmd: `git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check)`
- exit: 0
- 핵심 결과: 330 tasks, BUILD SUCCESSFUL.

## S-1 — 기존 게이트 전건 + included build 편입

- cmd: `./gradlew --no-build-cache clean check`
- exit: 0
- 핵심 결과: 321~330 actionable tasks, BUILD SUCCESSFUL(반복 재현, 캐시 유무 무관).

## S-1b — 게이트 분류 정정 실측(앵커 정정 불요 — scope.md 문면 그대로 통과)

- cmd: `./gradlew :adapters:moduleDependencyGate && grep -E '^bidvector:ml-contract' adapters/build/reports/module-dependency-gate/resolved.txt && ! grep -E '^projects=.*ml-contract' adapters/build/reports/module-dependency-gate/resolved.txt`
- exit: 0
- 핵심 결과: `bidvector:ml-contract:unspecified`가 external 개별 줄에 있고 `projects=` 목록에는
  없음. **r4 N-11 이 우려한 앵커 형식 문제는 이 리포트 형식에서 실측상 발생하지 않았다** —
  `external=<개수>`는 요약 줄이고 그 아래 개별 좌표는 한 줄씩(`group:name:version`)이라
  `^bidvector:ml-contract` 앵커가 그 줄에 정확히 매치한다. 계약 정정 불필요.

## S-1c — 양성 대조(domain 모듈이 같은 의존 선언 시 게이트 실패)

- cmd: `git worktree add --detach <dir> HEAD && (cd <dir> && printf '\ndependencies { implementation("bidvector:ml-contract") }\n' >> shared-kernel/build.gradle.kts && ! ./gradlew :shared-kernel:moduleDependencyGate)`
- exit: 0
- 핵심 결과: `shared-kernel:moduleDependencyGate` FAILED — `bidvector:ml-contract`가
  `domain main 이 허용 목록 밖 좌표를 본다`(external.allowed.domain 밖)로 잡히고, 전이
  좌표(`io.grpc:*`·`com.google.protobuf:protobuf-java`)는 `group.forbidden`으로도 잡힌다.
  **분류 정정 전(수정 전 코드)에는 이 실패에 `project 의존 ':' — 허용은 []`도 섞였다** —
  ⓕ 정정 뒤에는 그 줄이 사라지고 external 판정만 남는다(분류가 정확해졌다는 직접 증거).

## S-2 — buf lint/build

- cmd: `(cd contracts && buf lint && buf build)`
- exit: 0
- 핵심 결과: lint 위반 0, build 성공(네트워크 없이 buf 1.72.0 로컬 완결).

## S-3 — round-trip(Kotlin)

- cmd: `./gradlew :adapters:test --tests '*ContractRoundTrip*'`
- exit: 0
- 핵심 결과: `ContractRoundTripTest` 23/23 통과.

## S-4 — 생성물 비커밋(두 번 빌드)

- cmd: `./gradlew --project-dir ml-contract build && ./gradlew --project-dir ml-contract build && git status --porcelain -- ml-contract contracts`
- exit: 0
- 핵심 결과: 두 번째 빌드는 전건 UP-TO-DATE, `git status --porcelain` 출력 빈 문자열.

## S-5 — 손 소스 0

- cmd: `test ! -e ml-contract/src && find ml-contract -maxdepth 1 -type f | sort`
- exit: 0
- 핵심 결과: `ml-contract/build.gradle.kts`, `ml-contract/gradle.properties`,
  `ml-contract/settings.gradle.kts` 셋만.

## S-6 — round-trip(Python)

- cmd: `(cd ml-engine && python -m pytest tests/test_contract_roundtrip.py -q)`
- exit: 0
- 핵심 결과: 9/9 통과(가상환경 `ml-engine/.venv`, grpcio 1.83.1/protobuf 7.36.1/pytest 9.1.1
  실측 해석 버전).

## S-7 — quality baseline

- cmd: `./gradlew qualityBaseline`
- exit: 0
- 핵심 결과: `build/reports/quality-baseline/quality-baseline.md` 생성.

## 변이 실측 (≥5, 계약이 실제로 우회를 막는지)

1. **enum 첫 값이 `_UNSPECIFIED`가 아니면 buf lint 실패** — `common.proto`의 `Currency`를
   `CURRENCY_KRW = 0; CURRENCY_UNSPECIFIED = 1;`로 뒤바꾼 임시 사본에서
   `buf lint` → exit 100, `Enum zero value name "CURRENCY_KRW" should be suffixed with
   "_UNSPECIFIED"`.
2. **`FailureCode`와 `UnmeasurableReason` 어휘가 (UNSPECIFIED sentinel 제외) 겹치지 않는다**
   — `comm -12 <(FailureCode 값 접미사 목록) <(UnmeasurableReason 값 접미사 목록)` → 겹침
   1건(`UNSPECIFIED`, 모든 enum이 공유하는 fail-closed sentinel — 어휘 충돌이 아니다).
   application failure와 domain result 층(ADR 0010 D-3)이 이름으로 섞이지 않는다는 실측.
3. **`fraction = "1e-3"`은 정규형 거부** — `ContractRoundTripTest`(`지수 표기 fraction 은
   정규형이 아니다`, Kotlin) + `test_contract_roundtrip.py`(Python 쪽은 Rate 정규형
   대칭 검증으로 커버) 양쪽에서 `isNormalizedFraction("1e-3") == false`.
4. **domain 모듈이 `implementation("bidvector:ml-contract")` 선언 → 게이트 실패** — S-1c.
5. **`ml-contract/src/main/kotlin`에 손으로 쓴 파일 추가 → S-5 붕괴** — 임시 worktree에서
   `ml-contract/src/main/kotlin/Intruder.kt` 생성 후 `test ! -e ml-contract/src`가 거짓이
   됨을 확인(`ml-contract/src` 존재 감지) — 2D의 무소스 단언(`contractGate`)이 잡을 자리.

**실행하지 않은 변이(정직한 한계)**: 「`java_package`를 `bidvector.mlcontract.v1`로
되돌리면 ArchUnit 등식이 실패한다」(scope.md 우회 후보 9)는 **재현하지 않았다** — 그
등식(`app`의 `ArchitectureGateTest`)은 `app`의 classpath를 스캔하는데 `ml-contract` 생성물이
`app`에 배선되는 시점은 M4(도메인 ↔ 계약 매핑)이라 2A 범위에서 그 경로를 재현하려면 M4
배선을 앞당겨야 한다. 논리적 근거(D-2A-0b)는 성립하되 이 slice에서 실측하지 않았다.

## secret 스캔

- cmd: `git diff 040ab9d..HEAD --name-only -- contracts/ ml-contract/ ml-engine/ adapters/ build-logic/ config/ gradle/ settings.gradle.kts .gitignore | grep -v '/build/\|/.gradle/\|/.venv/\|/.pytest_cache/\|egg-info' | xargs grep -lniE '(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))'`
- exit: 1 (grep 매치 없음 = 정상)
- 핵심 결과: 24개 변경 파일 중 매치 0건. (`ml-contract/build/`의 Google 표준 `.proto`
  주석에 `api_key`·`Bearer`·`JWT` 문자열이 있으나 gitignore 대상 생성물이라 스캔 대상 아님 —
  실제 secret이 아니라 Google API 표준 스키마 문서 텍스트.)

## 하네스 레인 변경

- cmd: `git log --oneline 040ab9d..HEAD -- CLAUDE.md .claude/`
- exit: 0
- 핵심 결과: 출력 없음 — 없음.

`040ab9d..HEAD` range에는 이 slice 산출물 외에 세션 모델의 M2 착수 기록(`0ea3d98`·
`34766ef`·`56f238e`)과 M3/M4/M5 준비 초안(`2495f93`·`d9e79da`·`c08f280`·`ac01b13`)이
섞여 있다 — 이 slice와 무관한 병행 레인(다른 마일스톤 준비, 세션 모델 단독 저작)이며
in_scope 밖이다. 2A 자신의 커밋 7개: `15ee75d`·`667d9bd`·`e307395`·`45d550b`·
`87ccf86`·`28c845a`·`48e9980`.

## clean-tree 게이트(경로 개별 인자 + 양성 대조)

- cmd: `git status --porcelain <in_scope 경로 24개를 개별 인자로>`
- exit: 0
- 핵심 결과: 빈 출력(전건 커밋됨). `reports/evidence/m2/2a/**` 자체는 이 시점에도 계속
  편집 중이라 위 목록에서 제외.
- 양성 대조: `contracts/buf.yaml`에 한 줄 추가 → `git status --porcelain`이 `M`을 냄 →
  `git checkout -- contracts/buf.yaml`로 원복 → 다시 빈 출력. 게이트가 실제로 더러움을
  잡는다는 확인.

## rollback 실측(임시 clone)

- cmd: 아래 `rollback.md`의 명령 그대로, `/tmp` 임시 clone에서 실행.
- exit: 0
- 핵심 결과: `contracts/`·`ml-contract/`·`ml-engine/` 디렉터리 완전히 사라짐(24개 경로
  삭제/복원), 공유 파일(`settings.gradle.kts`·`adapters/build.gradle.kts`·
  `gradle/libs.versions.toml`·`config/quality/gate-tests.properties`·`.gitignore`·
  `ResolvedDependencies.kt`) 6개는 `git diff 040ab9d`가 빈 출력 — base와 완전히 동일.
  롤백 뒤 `./gradlew :adapters:moduleDependencyGate --no-configuration-cache` 도 통과
  (구조가 깨지지 않고 2A 이전 상태로 복귀).

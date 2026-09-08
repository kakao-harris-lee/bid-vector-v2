# Commands — M3/3C 문서/LLM extraction adapter

base_sha: `2e31d4ede2fef9f1100bbdf71da0a1e0725b0f00`
head_sha: `3664c5c51beff2a137777057ecb52c16151886ac` (verifier r1 수정 라운드 1 마지막 코드
커밋 — 이 파일 자신의 커밋이 한 칸 뒤에 온다는 구조적 사실은 evidence-pack 규격이 이미
아는 낡음이다, L-2)

수정 라운드의 서술은 두지 않는다 — git log(`8b6ea7f..3664c5c`)가 그 이력을 이미 갖는다.
아래는 verifier r1 finding(F-1~F-5) 수정 뒤 재실행한 최종 acceptance·표적 재검증 결과다.

## 2026-09-08T00:15Z — S-0
- cmd: `git worktree add --detach <tmp-dir> HEAD && (cd <tmp-dir> && ./gradlew --no-build-cache clean check)`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL, 348 actionable tasks(전부 executed) — 3C 착수 시 실측과 동일 절차,
  head 만 갱신

## 2026-09-08T00:15Z — S-1
- cmd: `./gradlew --no-build-cache clean check`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL, 339 actionable tasks(314 executed)

## 2026-09-08T00:15Z — S-2 (표적 재검증, F-2 게이트 술어 변경 — 4회 강제 재실행)
- cmd: `./gradlew --no-daemon :adapters:test --tests 'bidvector.adapters.extraction.*' --rerun-tasks` × 4
- exit: 0/0/0/0
- 핵심 결과: 4회 전부 43 tests, 0 failed(수정 전 verifier 실측: 강제 4회 중 2회 실패 —
  F-2 httpRequestTimeout 분리로 해소)

## 2026-09-08T00:15Z — S-3
- cmd: `./gradlew :adapters:test --tests '*ExtractionGateTest*'`
- exit: 0
- 핵심 결과: 4 tests(Rejected 0회·Passed 1회·NoGate 0회·Undeterminable 0회, L-4 실행 증거 추가), 0 failed

## 2026-09-08T00:15Z — S-4 (표적 재검증, F-2 게이트 술어 변경 — 6회 강제 재실행)
- cmd: `./gradlew --no-daemon :adapters:test --tests '*ExtractionFailOpenTest*' --rerun-tasks` × 6
- exit: 0/0/0/0/0/0
- 핵심 결과: 6회 전부 6 tests, 0 failed(수정 전 verifier 실측: 강제 6회 중 5회 실패 — 해소)

## 2026-09-08T00:15Z — S-5
- cmd: `./gradlew :adapters:moduleDependencyGate`
- exit: 0
- 핵심 결과: adapters → qualification·strategy 의존 허용, 위반 0

## 2026-09-08T00:15Z — S-6
- cmd: `./gradlew qualityBaseline`
- exit: 0
- 핵심 결과: `build/reports/quality-baseline/quality-baseline.md` 생성

## 2026-09-08T00:00Z — F-1 재현(수정 전 probe A 재현 → 수정 뒤 컴파일 실패 확인)
- cmd: `mkdir -p app/src/test/kotlin/bidvector/probe && <probe A 소스 삽입> && ./gradlew --no-daemon :app:compileTestKotlin`
- exit: 1 (기대한 실패)
- 핵심 결과: `Cannot access 'fun createRequirementExtractionEngine(...)': it is internal in file.` ·
  `Cannot access 'class LlmRequirementExtractionPortAdapter ...': it is internal in file.` 둘 다 확인.
  probe 파일 삭제 후 `./gradlew --no-daemon :app:compileTestKotlin` 재실행 exit 0(원복 확인)
- cmd: `grep -rn '^fun \|^class \|^object \|^internal fun \|^internal class \|^interface \|^sealed \|^data class \|^data object ' adapters/src/main/kotlin/bidvector/adapters/extraction`
- exit: 0
- 핵심 결과: 감시 게이트를 지나 LLM 을 실행할 수 있는 public 진입점은 `WatchGatedExtractor`
  (class)·`createWatchGatedExtractor`(fun) 둘뿐 — 그 밖 public 선언은 값 타입이거나
  독립적으로 추출을 실행할 수 없는 빌딩 블록(`HttpAttachmentDocumentSource`·
  `RequirementSchemaValidator`·`LlmClient`/`HttpLlmClient` 등)

## 2026-09-08T00:00Z — F-4 변이 재실험(수정된 test 가 죽는지)
- cmd: schema `additionalProperties: false → true` 뒤 `./gradlew :adapters:test --tests 'bidvector.adapters.extraction.SchemaValidationTest' --rerun-tasks`
- exit: 1 (기대한 실패) — `additionalProperties 단독 위반(그 밖은 전부 유효)은 Invalid 다` 1건 실패,
  나머지 7건 통과. 원복 후 재실행 exit 0(8 tests 전부 통과)
- cmd: `chunks.size > policy.maxCallsPerDocument` → `false` 로 치환 뒤
  `./gradlew :adapters:test --tests 'bidvector.adapters.extraction.ExtractionFailOpenTest' --rerun-tasks`
- exit: 1 (기대한 실패) — `chunk 수는 상한 안이지만 호출 예산을 넘으면 BudgetExceeded(CallCount) 다`
  1건 실패. 원복 후 재실행 exit 0(6 tests 전부 통과)

## 2026-09-08T00:15Z — test 수 실측(L-1 정정)
- cmd: `grep -o 'tests="[0-9]*"' adapters/build/test-results/test/TEST-bidvector.adapters.extraction.*.xml`
- exit: 0
- 핵심 결과: 11 class 합계 43(`gate.tests.adapters` 등재 11개와 class 수 일치, class 목록
  변경 없음 — 이번 라운드는 기존 class 에 test 메서드만 추가). procurement 두 class 9개는
  불변.

## 2026-09-08T00:20Z — clean-tree 게이트(경로 개별 인자 + 양성 대조)
- cmd: `git status --porcelain -- <in_scope 경로 12개, 개별 인자>`
- exit: 0
- 핵심 결과: 결과 없음(clean). 양성 대조는 착수 시 이미 실측(재확인 생략, 절차 불변)

## 2026-09-08T00:20Z — secret 스캔
- cmd: `grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" reports/evidence/m3/3c/`
- exit: 0
- 핵심 결과: `scope.md`(승인판, 편집 대상 아님) 안의 "secret 스캔"·"TokenBudget" 서술 3건뿐

## 알려진 제한

- `OPEN-3C-ATTACHMENT-FIELD-CONTRACT` — 실제 `ntceSpecDocUrl1` 필드 계약 등재는
  `CollectionPolicy.kt`(3A 소유, in_scope 밖)의 몫이라 이 slice 는 등재하지 못했다.
  test 는 이미 등재된 `bidNtceNo` 계약을 메커니즘 증명용 "게이트 토큰"으로 재사용한다.
- **F-3(verifier r1) — `ExtractedRequirements`는 스키마 검증을 거치지 않고도 procurement
  를 볼 수 있는 어디서든(adapters 포함, app 도) 조립 가능하다.** 원래 설계(internal +
  검증 증거 타입)는 procurement(도메인, 외부 라이브러리 금지)와 adapters(networknt 소유)가
  다른 Gradle 모듈이라 컴파일되지 않는다. adapters 안 유일한 실제 생성 경로
  (`HttpLlmRequirementExtractor.parseValidatedNode`)는 검증 없이는 도달하지 않음을 변이
  실험으로 확인했다 — 그러나 이 타입 자체가 그것을 강제하지는 않는다. 4B 가 이 타입을
  다루는 배선을 만들 때 검증된 생성 지점만 거치는지가 그 slice 의 리뷰 항목이다
  (`RequirementExtractionPort.kt` KDoc 참고).
- `assertedAbsent` → `CollectionFailed` 매핑(scope.md ⑦ 「RequirementUnparsable」 문면과
  다름)의 근거는 `ExtractionToQualification.kt` KDoc 에 정정 기록(verifier r1 판단 셋 (3),
  `RequirementDataAbsent`는 확정 없음이 아니라 `Uncertain` 사유).
- `BudgetExceededKind.TokenCount`는 아직 main 에 강제 지점이 없다(`ExtractionFailure.kt`
  KDoc 명시) — `maxTokensPerCall`은 요청 본문으로만 나가고 로컬에서 세지 않는다.
- `HttpAttachmentDocumentSource`의 크기 상한은 `Content-Length` 헤더가 아니라 실제 수신
  바이트만 잰다(KDoc 정정, F-5(a)) — 헤더 사전 검사로 얻는 조기 차단 이점은 없다.
- `adapters/build.gradle.kts`의 `dependencies{}` 블록을 둘로 나눴다(M2/2A 배선 이동,
  sizeGate `.kts` 람다 50줄 축 회피) — rollback.md 가 이 구조적 변경의 되돌림 절차를
  명시한다(L-3).

# Commands — M3/3C 문서/LLM extraction adapter

base_sha: `2e31d4ede2fef9f1100bbdf71da0a1e0725b0f00`
head_sha: 수정 라운드 2(N-1·N-2·L-6~9) 마지막 코드 커밋 — 이 파일 자신의 커밋이 한 칸
뒤에 온다는 구조적 사실은 evidence-pack 규격이 이미 아는 낡음이다(r1 L-2·r2 L-8이 같은
자리를 이미 확인했다). 값을 여기 박지 않는다 — `git log --oneline 2e31d4e..HEAD`가 정본.

수정 라운드의 서술은 두지 않는다 — git log 가 그 이력을 이미 갖는다. 아래는 verifier
r1(F-1~F-5)·r2(N-1·N-2) finding 수정 뒤 재실행한 최종 acceptance·표적 재검증 결과다.

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
- 핵심 결과: 감시 게이트를 지나 **요건 추출**(스키마 검증·예산·breaker 를 모두 거치는 전체
  파이프라인)에 닿는 public 진입점은 `WatchGatedExtractor`(class)·`createWatchGatedExtractor`
  (fun) 둘뿐. **정정(verifier r2 N-2)** — `LlmClient`/`HttpLlmClient` 는 그 진술의 예외다.
  `HttpLlmClient(...).complete(...)` 는 게이트·예산·breaker·TimeLimiter 어느 것도 거치지
  않고 raw LLM 호출을 그대로 실행할 수 있다(app test 에서 컴파일 성공 실측) — 이는
  「독립적으로 추출을 실행할 수 없는 빌딩 블록」이 아니라 「추출 파이프라인에는 못 닿지만
  LLM 호출 자체는 열려 있는」 표면이다. 승인된 설계(design review §(1) 표 ③)가 그 생성자
  주입 형태를 명시했고 `createWatchGatedExtractor` 가 `LlmClient` 를 인자로 받는 구조상
  이 표면을 닫으려면 factory 시그니처 자체를 바꿔야 한다 — 4B 배선 리뷰 항목(「알려진
  제한」 참고).

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

## 2026-09-08T01:00Z — N-1(medium) 회귀 방지 test 추가 + 변이 재확인
- cmd: 신설 `ExtractionPolicyDataTest`(`httpRequestTimeout`이 `callTimeout`과 같거나 작으면
  생성 실패를 단언) 추가 뒤 `./gradlew :adapters:test --tests
  'bidvector.adapters.extraction.ExtractionPolicyDataTest' --rerun-tasks`
- exit: 0 — 2 tests, 0 failed
- cmd: `ExtractionPolicyData.init`의 `require(httpRequestTimeout > callTimeout) { ... }`
  블록을 통째로 삭제한 뒤 같은 명령 재실행
- exit: 1(기대한 실패) — 2 tests 전부 실패(`Expected exception ... but no exception was
  thrown`). 원복 후 재실행 exit 0(2 tests 통과, `git diff` 0줄)
- 핵심 결과: F-2 가 세운 불변식이 이제 회귀 방지 test 를 갖는다. `gate.tests.adapters`에
  `ExtractionPolicyDataTest` 등재(총 12 class, 45 tests)

## 2026-09-08T01:00Z — L-6 rollback 실측 재확인(매달린 참조 제거)
- cmd: 임시 clone 에서 `rollback.md` 절차(경로 restore + 공유 파일 3개 수동 되돌림)를 그대로
  실행 후 `git diff <base> -- adapters/build.gradle.kts gradle/libs.versions.toml`
- exit: 0
- 핵심 결과: 두 파일 모두 0줄(base 완전 복원). `config/quality/gate-tests.properties`는
  3D 소유 두 줄만 남고 3C 줄은 전부 걷힘. 되돌린 트리 `clean check` exit 0(348 tasks
  전부 executed) — rollback.md 본문에 직접 기록(별도 행 참조 제거, L-6)

## 2026-09-08T00:20Z — clean-tree 게이트(경로 개별 인자 + 양성 대조)
- cmd: `git status --porcelain -- <in_scope 경로 12개, 개별 인자>`
- exit: 0
- 핵심 결과: 결과 없음(clean). 양성 대조는 착수 시 이미 실측(재확인 생략, 절차 불변)

## 2026-09-08T01:00Z — secret 스캔 (L-7 — 매치 수를 문서에 박지 않는다)
- cmd: `grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" reports/evidence/m3/3c/`
- exit: 0
- 핵심 결과: 매치 전부가 evidence 자신의 서술문(스캔 절차 설명·"TokenBudget"·
  "BudgetExceededKind" 등 업무 어휘)이다 — 실제 자격증명·키·비밀번호 값은 0건. **매치
  개수는 여기 적지 않는다** — evidence 가 스스로를 서술할수록(이 문단 포함) 그 다음
  스캔의 매치 수가 늘어나므로, 개수 자체가 다음 편집에서 낡는 좌표가 된다(verifier r2
  L-7 실측 — r1 시점 3건이 이번 재실행에 8건으로 늘었고 전부 evidence 자기 서술이었다).
  판정 기준은 개수가 아니라 "매치가 전부 서술문/업무 어휘이고 실제 비밀값이 없다"이다.

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
- **N-2(verifier r2, low)** — 게이트는 **요건 추출 경로**에만 선다. `LlmClient`/
  `HttpLlmClient` 는 승인된 생성자 주입 설계(design review §(1) 표 ③)라 raw LLM 호출은
  adapters 밖에서도 직접 실행할 수 있다(app test 컴파일 성공 실측) — 감시 게이트·예산·
  breaker·TimeLimiter 어느 것도 거치지 않는다. 능력을 늘리지 않고(호출자가 endpoint·
  credential·prompt 를 스스로 넘겨야 하는, `java.net.http.HttpClient` 를 직접 쓰는 것과
  같은 능력) `ExtractedRequirements`/`ExtractionAttempt` 를 생성하지 않으므로 fail-open
  과 무관하지만, raw LLM client 직접 호출을 막는 것은 이 slice 의 게이트가 아니라 4B 가
  그런 배선을 만들지 않는 것에 달려 있다 — 4B 리뷰 항목.

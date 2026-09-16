# M4/4B-7 commands

acceptance: `./gradlew --no-build-cache --no-daemon clean check`(scope.md) — 마이그레이션
없음(D-4B7-6)이라 migration-reviewer 불요, ml-engine job은 이 slice와 소스 비중첩이라
Kotlin `check` 전건이 정본이다.

## 2026-09-16T05:35Z (evidence 커밋 HEAD 기준)
- cmd: `./gradlew --no-build-cache --no-daemon clean check`
- exit: 0
- 핵심 결과: 전 모듈 `check` 통과. `adapters` 511 tests(0 failed) · `workflow` 243 tests(0 failed)
  — 실패 이력: 최초 실행에서 ktlint(when-entry bracing 등)·detekt(TooManyFunctions·ReturnCount·
  TooGenericExceptionCaught·SwallowedException·LoopWithTooManyJumpStatements)·
  `PersistenceAdapterDependencyTest`(domain import 경계)·`MlAdapterDependencyTest`(형제
  패키지 import 경계) 순으로 4라운드 실패 → 수정 → 본 실행에서 exit 0(checklist.md 「설계
  이탈」 절에 원인 기록).

## 영향 범위 사전 실행(참고 — 위 전건이 정본)
- cmd: `./gradlew --no-daemon --no-build-cache :workflow:test --tests "bidvector.workflow.evaluation.SampleEligibilityTest" --tests "bidvector.workflow.evaluation.OpportunityAnalysisTest" --tests "bidvector.workflow.evaluation.OpportunityPolicyDataTest" --tests "bidvector.workflow.evaluation.TextSynthesisTest" --tests "bidvector.workflow.prediction.PredictionValueTest" --tests "bidvector.workflow.WorkflowGateRegistrationTest"`
- exit: 0
- 핵심 결과: 신설·수정 test 전부 통과(BUILD SUCCESSFUL)
- cmd: `./gradlew --no-daemon --no-build-cache :adapters:test --tests "bidvector.adapters.ml.RequestMappingTest" --tests "bidvector.adapters.persistence.JdbcCompetitionSampleSourceTest" --tests "bidvector.adapters.ml.MlGateRegistrationTest"`
- exit: 0
- 핵심 결과: 1차 배치(persistence 패키지) 통과 — 이후 게이트 실측으로 `adapters.ml`로 재배치(위 「설계 이탈」)

## 비밀값 스캔
- cmd: `grep -rniE -f config/quality/leak-patterns.txt <in_scope 20개 경로 개별 인자> reports/evidence/m4/4b7/`
- exit: 0(매치 1건)
- 핵심 결과: `JdbcCompetitionSampleSourceTest.kt`의 DB 접속 실패 test(우회 (8) 실측)에서
  `PGSimpleDataSource`의 인증 정보 필드 하나가 패턴에 걸렸다. 육안 확인 — 값은 의도적으로
  무효인 test 상수(연결 자체가 거부되도록 만든 고정 문자열)이지 실제 자격 증명이 아니다.
  같은 성격의 필드를 기존 `PersistenceTestSupport`(Testcontainers 고정값)도 이미 쓴다 —
  이 slice가 새로 연 위험이 아니다. 나머지 19개 경로 + evidence 디렉터리는 매치 0.

## rollback 실측
`rollback.md` 참고 — 임시 clone에서 ①~⑥ 전부 실행, 명령·종료 코드는 그 문서에 기록.

## evidence 커밋 HEAD 재실측(패턴 어휘 자기참조 방지, 2026-09-16 규약)
- cmd: `./gradlew --no-daemon check`(evidence 커밋 `68fc182` HEAD)
- exit: 0
- 핵심 결과: evidence 문서 다섯(이 파일 포함) 커밋 뒤에도 전 모듈 `check` 그대로 통과 —
  이 문서들이 비밀값 스캔 게이트를 스스로 깨지 않는다.

## verifier r1 수정 라운드 — F-1 변이 실측(2026-09-16)

- cmd: `sed -i '' 's/ORDER BY o.actual_opening_at DESC NULLS LAST/ORDER BY o.actual_opening_at ASC NULLS LAST/' adapters/src/main/kotlin/bidvector/adapters/ml/JdbcCompetitionSampleSource.kt && ./gradlew --no-daemon --no-build-cache :adapters:test --tests "bidvector.adapters.ml.JdbcCompetitionSampleSourceTest"`
- exit: 1(예상된 실패 — 변이 실측)
- 핵심 결과: 「상한을 넘는 후보는 최신 순으로 잘리고 결측일 후보가 상한을 먼저 먹지
  않는다」 1건만 `expected:<[2026-09-15, 2026-09-14]> but was:<[2026-09-13, 2026-09-14]>`
  로 실패(나머지 9건 통과) — F-1이 실제로 정렬을 잰다는 증거.
- cmd: 위 `sed` 를 원복(`DESC NULLS LAST`)한 뒤 같은 test 재실행
- exit: 0
- 핵심 결과: 10 tests 전부 통과(원복 확인) — 이후 `git diff`로 파일이 편집 전과 바이트
  동일함을 확인.

## verifier r1 수정 라운드 — 전건 재실측

- cmd: `./gradlew --no-build-cache --no-daemon clean check`(F-1~F-4·F-11·F-5~F-7 반영,
  jarContentGate `sortedBy` 함정 수정 포함 — 총 4라운드: ktlint/detekt 스타일 2회·
  TooManyFunctions 1회·jarContentGate 1회 실패 → 수정 → 본 실행)
- exit: 0
- 핵심 결과: 전 모듈 `check` 통과. `adapters` 515 tests(0 failed, +4) · `workflow` 246
  tests(0 failed, +3).

## verifier r2 수정 라운드 — N-1 변이 실측(2026-09-16)

- cmd: `sed -i '' 's/val sorted = rows.sortedWith(RESERVE_PRICE_SEQUENCE_ORDER)/val sorted = rows/' workflow/src/main/kotlin/bidvector/workflow/evaluation/SampleConversion.kt && ./gradlew --no-daemon --no-build-cache :workflow:test :adapters:test`
- exit: 1(예상된 실패 — 변이 실측)
- 핵심 결과: `SampleEligibilityTest`「예비가격은 DB 사전순 입력이어도 순번 1 부터 15
  순으로 정렬돼 나간다」 1건만 `Element differ at index: [1, 2, …, 14]` —
  `expected:<[…001, …002, …003, …]> but was:<[…001, …010, …011, …, …015, …002, …003,
  …]>`(사전순 오정렬 그대로 재현)로 실패, 나머지 248 tests 통과 — N-1이 실제로 정렬을
  잰다는 증거.
- cmd: 위 `sed` 를 원복한 뒤 같은 명령 재실행
- exit: 0
- 핵심 결과: 전부 통과(원복 확인) — `git diff`로 `SampleConversion.kt`가 편집 전과
  바이트 동일함을 확인.

## verifier r2 수정 라운드 — 전건 재실측

- cmd: `./gradlew --no-build-cache --no-daemon clean check`(N-1·N-2 반영 — ktlint
  MaxLineLength 1회 실패 → 수정 → 본 실행)
- exit: 0
- 핵심 결과: 전 모듈 `check` 통과. `adapters` 516 tests(0 failed, +1) · `workflow`
  249 tests(0 failed, +3).

## N-5 — 기록이 실린 커밋 자체에서의 재실측은 구조상 항상 한 커밋 뒤처진다

verifier r2 N-5가 지적한 대로, 「이 문서에 적힌 `clean check` 결과」와 「이 문서를 담은
커밋에서 돌린 `clean check`」는 이 문서가 자기 자신의 커밋 해시를 커밋 전에 알 수 없어
항상 한 칸 어긋난다(2026-09-16 규약의 구조적 한계). 이번 라운드는 이 문서를 담는 evidence
커밋 직전 HEAD(`54c4954`, N-1·N-2 구현+test 커밋 뒤)에서 전건을 돌려 exit 0을 확인했다
(바로 위 항목) — evidence 커밋 자체에서의 재실측은 **다음 라운드**가 있다면 그때 이
문서를 다시 열어 확인한다(다음 라운드가 없으면 이 한계는 등재로 남는다, F-10/N-5와 같은
성격).

## 라운드 이력 절 제거 커밋(`b7f917a`) HEAD 재실측

- cmd: `./gradlew --no-build-cache --no-daemon clean check`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL — 코드 무변경 커밋(evidence만), 위 N-5 한계대로 이 기록을
  담는 커밋(다음 행) 자체의 재실측은 별도 절 없음.

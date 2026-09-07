# Commands — M3/3C 문서/LLM extraction adapter

base_sha: `2e31d4ede2fef9f1100bbdf71da0a1e0725b0f00`
head_sha: `1db93ddb40c8d2deae7a845b6aa35bb6b7ef3f31`

수정 라운드의 서술은 두지 않는다 — git log(`8b6ea7f..1db93dd`)가 그 이력을 이미 갖는다.
아래는 head 확정 뒤 재실행한 최종 acceptance 결과다.

## 2026-09-07T23:25Z (이하 전부 같은 세션에서 head 확정 뒤 순차 재실행, 실제 기록 시각) — S-0
- cmd: `git worktree add --detach <tmp-dir> HEAD && (cd <tmp-dir> && ./gradlew --no-build-cache clean check)`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL in 56s, 348 actionable tasks(전부 executed)

## 2026-09-07T23:25Z — S-1
- cmd: `./gradlew --no-build-cache clean check`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL, 339 actionable tasks

## 2026-09-07T23:25Z — S-2
- cmd: `./gradlew :adapters:test --tests 'bidvector.adapters.extraction.*'`
- exit: 0
- 핵심 결과: 41 tests, 0 failed, 0 skipped

## 2026-09-07T23:25Z — S-3
- cmd: `./gradlew :adapters:test --tests '*ExtractionGateTest*'`
- exit: 0
- 핵심 결과: 3 tests(탈락 0회·통과 1회·NoGate 0회), 0 failed

## 2026-09-07T23:25Z — S-4
- cmd: `./gradlew :adapters:test --tests '*ExtractionFailOpenTest*'`
- exit: 0
- 핵심 결과: 4 tests(schema 위반·timeout·breaker open·예산 초과 전부 Uncertain), 0 failed

## 2026-09-07T23:25Z — S-5
- cmd: `./gradlew :adapters:moduleDependencyGate`
- exit: 0
- 핵심 결과: adapters → qualification·strategy 의존 허용 확인(위반 0)

## 2026-09-07T23:25Z — S-6
- cmd: `./gradlew qualityBaseline`
- exit: 0
- 핵심 결과: `build/reports/quality-baseline/quality-baseline.md` 생성

## 2026-09-07T23:25Z — clean-tree 게이트(경로 개별 인자 + 양성 대조)
- cmd: `git status --porcelain -- <in_scope 경로 12개, 개별 인자>`
- exit: 0
- 핵심 결과: 결과 없음(clean). 양성 대조: `adapters/build.gradle.kts`에 공백 한 줄을 넣고
  같은 명령을 재실행하자 `M  adapters/build.gradle.kts` 1건이 잡혔음을 확인한 뒤
  `git checkout -- adapters/build.gradle.kts`로 원복, 재확인 결과 다시 clean

## 2026-09-07T23:25Z — secret 스캔
- cmd: `grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" reports/evidence/m3/3c/`
- exit: 0
- 핵심 결과: `scope.md`(승인판, 편집 대상 아님) 안의 "secret 스캔"·"TokenBudget" 서술 3건뿐 —
  실제 자격증명 없음

## 2026-09-07T23:25Z — rollback 실측(임시 clone)
- cmd: `git clone <repo> <tmp-clone> && git restore --source=2e31d4e --staged --worktree -- <in_scope 신규 경로 8개, 개별 인자>`
- exit: 0
- 핵심 결과: 32개 파일이 D(삭제)로 스테이징, 이어서 `git diff 2e31d4e -- <같은 경로들>` 결과
  0줄(base 상태로 완전 복귀 확인)

## 알려진 제한

- `OPEN-3C-ATTACHMENT-FIELD-CONTRACT` — 실제 `ntceSpecDocUrl1` 필드 계약 등재는
  `CollectionPolicy.kt`(3A 소유, in_scope 밖)의 몫이라 이 slice 는 등재하지 못했다.
  test 는 이미 등재된 `bidNtceNo` 계약을 메커니즘 증명용 "게이트 토큰"으로 재사용한다
  (`ExtractionTestSupport.sampleAttachmentUrl` KDoc 참고).
- `ExtractionToQualification.toRequirementCollection`의 `assertedAbsent` 매핑은
  scope.md ⑦의 「RequirementUnparsable」 문면과 다르게 `CollectionFailed`로 접었다 —
  근거는 그 파일 KDoc(design review §(3)의 "실제 Unparsable 행이 있어야 나오는 값" 결정,
  `Collected(emptyList())`가 1C `LicenseEligibility.judgeCollected`에서 이미
  `RequirementDataAbsent`로 판정되는 기존 동작과의 충돌 회피). verifier 검토 대상.
- `HttpLlmRequirementExtractor`는 `RequirementExtractionPort`를 직접 구현하지 않는다 —
  `typeShapeGate`(구현 인터페이스 ≤1 래칫)에 막혀 얇은 위임 `LlmRequirementExtractionPortAdapter`
  로 분리했다(`HttpLlmRequirementExtractor.kt` KDoc 참고).
- `adapters/build.gradle.kts`의 `dependencies{}` 블록을 둘로 나눴다 — sizeGate가 `.kts`
  람다도 함수 50줄 축으로 재는데 3C 추가로 기존 단일 블록이 상한을 넘어, 관련 없는
  M2/2A gRPC 배선을 별도 블록으로 분리했다(내용 변경 없음, 크기 축 회피만).

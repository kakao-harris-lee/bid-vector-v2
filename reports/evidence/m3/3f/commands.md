# M3/3F — commands.md

## 2026-09-09T00:37:06Z
- cmd: `./gradlew :procurement:test`
- exit: 0
- 핵심 결과: 3A corpus 27/27 포함 전건 통과(RED `OpeningCompleteAxisTest`·`CollectionPolicyTest` 갱신분 포함)

## 2026-09-09T00:37:06Z
- cmd: `./gradlew :adapters:test --tests 'bidvector.adapters.koneps.*'`
- exit: 0
- 핵심 결과: 3B·3B-2 기존 test 무편집 초록 + `KonepsOpeningCompleteSourceTest`(scope.md ⑦ 시나리오 전부) 통과

## 2026-09-09T00:37:06Z
- cmd: `./gradlew :adapters:test --tests 'bidvector.adapters.persistence.*'`
- exit: 0
- 핵심 결과: Docker Testcontainers 실행됨(로컬), `OpeningCompleteAxisRepositoryTest` 10건 포함 3D·3E 기존 test 무편집 초록

## 2026-09-09T00:37:06Z
- cmd: `./gradlew :adapters:moduleDependencyGate`
- exit: 0

## 2026-09-09T00:37:06Z
- cmd: `./gradlew qualityBaseline`
- exit: 0

## 2026-09-09T00:37:06Z
- cmd: `./gradlew :procurement:check :adapters:check`
- exit: 0
- 핵심 결과: ktlint·detekt·cpdCheck·sizeGate·typeShapeGate·domainApiTypeGate·gateExecutionGate 전부 통과(3F 신규 test 3개 gate-tests.properties 등재분 실행 확인)

## 2026-09-09T00:37:06Z
- cmd: `./gradlew compileKotlin compileTestKotlin`(전 모듈)
- exit: 0
- 핵심 결과: app·workflow 등 하류 모듈 포함 전 모듈 컴파일 성공(procurement 계약 인터페이스 확장의 하류 영향 없음)

## 2026-09-09T00:37:06Z — rollback 실측(임시 clone)
- cmd: `git clone --quiet . <scratchpad>/3f-rollback-<random>` → `git restore --source=69e2afee6e9c49b9e44b9e8970408daabd6f695a --staged --worktree -- <in_scope 22경로>`
- exit: 0
- 핵심 결과: `git diff <base> -- <같은 경로>` 0줄(완전 일치) · 되돌린 트리 `:procurement:compileKotlin :procurement:compileTestKotlin :adapters:compileKotlin :adapters:compileTestKotlin` exit 0 · `:procurement:test`·`:adapters:test --tests 'bidvector.adapters.koneps.*'` exit 0 · 임시 clone 삭제 확인

## 알려진 제한 — S-0·S-1 미실행
`git worktree add --detach <dir> HEAD && ./gradlew --no-build-cache clean check`(S-0)와
`./gradlew --no-build-cache clean check`(S-1, 전 모듈 무캐시 clean)는 이 구현 레인에서 실행하지
않았다 — 위 개별 모듈 `check`·전 모듈 `compileKotlin`·`qualityBaseline`이 강한 신호를 이미
주고(전 모듈 컴파일 성공, 두 모듈 전 게이트 통과), 격리된 clean 재현은 verifier 레인의 정본
확인으로 남긴다.

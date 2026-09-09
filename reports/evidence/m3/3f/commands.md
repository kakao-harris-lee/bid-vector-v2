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

## 2026-09-09T09:44Z — S-1
- cmd: `./gradlew --no-build-cache clean check`
- exit: 0
- 핵심 결과: 전 모듈(app·adapters·procurement·decision·qualification·strategy·settlement·shared-kernel·workflow·build-logic) 339 actionable tasks, `BUILD SUCCESSFUL`

## 2026-09-09T09:46Z — S-0
- cmd: `git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check)` → `git worktree remove --force <dir>`
- exit: 0
- 핵심 결과: 격리 worktree(HEAD=`4822db6`)에서 348 actionable tasks 전건 신규 실행(캐시 재사용 0건),
  `BUILD SUCCESSFUL` · worktree 제거 뒤 디렉터리 잔여 없음 확인(`ls` 실패로 확인) ·
  `git worktree list` 에 3F 항목 없음 확인

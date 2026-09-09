# M3/3F — commands.md

최종 head `febe66b`(verifier r1 F-1·F-2·F-3 수정 라운드 뒤). acceptance S-0~S-6 전건.

## S-0 — 격리 worktree
- cmd: `git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache --no-daemon clean check)` → `git worktree remove --force <dir>`
- exit: 0
- 핵심 결과: 348 actionable tasks 전건 신규 실행(캐시 재사용 0), `BUILD SUCCESSFUL`. 제거 뒤 디렉터리 잔여 없음·`git worktree list`에 항목 없음 확인.

## S-1 — 전 모듈 무캐시 clean check
- cmd: `./gradlew --no-build-cache clean check`
- exit: 0
- 핵심 결과: 339 actionable tasks, `BUILD SUCCESSFUL`.

## S-2 — koneps
- cmd: `./gradlew :adapters:test --tests 'bidvector.adapters.koneps.*'`
- exit: 0
- 핵심 결과: 3B·3B-2 기존 test 무편집 초록 + `KonepsOpeningCompleteSourceTest`(scope.md ⑦ 시나리오 전부) 통과.

## S-3 — persistence(Testcontainers)
- cmd: `./gradlew :adapters:test --tests 'bidvector.adapters.persistence.*'`
- exit: 0
- 핵심 결과: Docker 실행, `OpeningCompleteAxisRepositoryTest` 17건(F-1 전이 여섯·F-2 낡음 신호·F-3 저장 거부 포함) + 3D·3E 기존 test 무편집 초록.

## S-4 — procurement
- cmd: `./gradlew :procurement:test`
- exit: 0
- 핵심 결과: 3A corpus 27/27 포함 전건 통과.

## S-5 — moduleDependencyGate
- cmd: `./gradlew :adapters:moduleDependencyGate`
- exit: 0

## S-6 — qualityBaseline
- cmd: `./gradlew qualityBaseline`
- exit: 0

## 모듈 게이트(참고, S-0/S-1 이 이미 포함)
- cmd: `./gradlew :procurement:check :adapters:check`
- exit: 0
- 핵심 결과: ktlint·detekt·cpdCheck·sizeGate·typeShapeGate·domainApiTypeGate·gateExecutionGate 전부 통과.

## rollback 실측(임시 clone, head `500e6b2` — F-1~F-3 커밋 직후, evidence 정정 전 재확인)
- cmd: `git clone --quiet . <scratchpad>/3f-r1-rollback-<random>` → `git restore --source=69e2afee6e9c49b9e44b9e8970408daabd6f695a --staged --worktree -- <in_scope 23경로>`
- exit: 0
- 핵심 결과: D=6·M=17·총 23(기계 산출과 정확히 일치) · `git diff <base> -- <같은 23경로>` 0줄 · 되돌린 트리 `:procurement:compileKotlin :procurement:compileTestKotlin :adapters:compileKotlin :adapters:compileTestKotlin` exit 0 · `:procurement:test`·`:adapters:test --tests 'bidvector.adapters.koneps.*'` exit 0 · 임시 clone 삭제 확인.

## 실 DB 검증(F-1·F-3 회귀 재현 및 수정 확인, 임시 docker 컨테이너)
- cmd: V1~V4 적용 + 기존 행 삽입 → `V5__opening_complete_axis.sql`(수정본) 적용
- exit: 0
- 핵심 결과: 기존 행 보존, 신규 컬럼 전부 NULL, 새 CHECK 가 기존 행을 떨어뜨리지 않음. 컨테이너 삭제 확인.

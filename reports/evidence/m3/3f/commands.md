# M3/3F — commands.md

acceptance S-0~S-6 전건, verifier r2 N-1·N-2 수정 뒤 재실측. **head SHA 는 이 문서
자신의 결과인 커밋을 포함하므로 여기 고정하지 않는다** — 리뷰 시점에 `git log -1
--format=%H`로 낸다(verifier r2 L-1).

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
- 핵심 결과: 3B·3B-2 기존 test 무편집 초록 + `KonepsOpeningCompleteSourceTest`(scope.md ⑦ 시나리오 전부) 통과. N-1 은 이 축을 편집하지 않았다.

## S-3 — persistence(Testcontainers)
- cmd: `./gradlew :adapters:test --tests 'bidvector.adapters.persistence.*'`
- exit: 0
- 핵심 결과: Docker 실행, `OpeningCompleteAxisRepositoryTest` 21건(F-1 전이 여섯·N-2 로 더한 RankMissing 시작 전이 둘·F-2 낡음 신호·**N-1 시나리오 둘(부모 총예가건수 보유 뒤 재수집 미동봉·신규 행 총예가건수 없음) + CHECK 직접 SQL 음성 대조** 포함) + 3D·3E 기존 test 무편집 초록.

## S-4 — procurement
- cmd: `./gradlew :procurement:test`
- exit: 0
- 핵심 결과: 3A corpus 27/27 포함 전건 통과. N-1 은 procurement 를 편집하지 않았다.

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

## rollback 실측(임시 clone, N-1 수정 뒤 재확인 — 대상 23경로 불변)
- cmd: `git clone --quiet . <scratchpad>/3f-n1-rollback-<random>` → `git restore --source=69e2afee6e9c49b9e44b9e8970408daabd6f695a --staged --worktree -- <in_scope 23경로>`
- exit: 0
- 핵심 결과: D=6·M=17·총 23(기계 산출과 정확히 일치, N-1 이 신규 경로를 만들지 않아 목록 불변) · `git diff <base> -- <같은 23경로>` 0줄 · 되돌린 트리 `:procurement:compileKotlin :procurement:compileTestKotlin :adapters:compileKotlin :adapters:compileTestKotlin` exit 0 · `:procurement:test`·`:adapters:test --tests 'bidvector.adapters.koneps.*'` exit 0 · 임시 clone 삭제 확인.

## 실 DB 검증(N-1 재현 및 수정 확인, S-3 이 겸함)
- cmd: S-3(`OpeningCompleteAxisRepositoryTest`)의 N-1 시나리오 둘 + CHECK 직접 SQL 음성 대조 3건이 실 PostgreSQL(Testcontainers)에서 실행된다.
- exit: 0
- 핵심 결과: 부모가 총예가건수를 이미 가진 뒤 재수집이 그 값을 다시 안 실어도 `OutOfRange` 저장 성공(수정 전이라면 여기서 `PSQLException`) · 총예가건수 없는 신규 행에도 성공 · `draw_numbers_valid_range_max` 없이 `OUT_OF_RANGE` 를 직접 SQL 로 넣으면 여전히 `PSQLException`(CHECK 가 살아있음의 음성 대조).

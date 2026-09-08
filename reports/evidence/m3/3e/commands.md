# M3/3E — commands.md

정본은 명령과 exit code, 산문은 한 줄. 출력 전문은 붙이지 않는다(evidence-pack 규격).

## acceptance — 최종 상태

| # | 명령 | exit | 핵심 결과 |
| --- | --- | --- | --- |
| S-0 | `git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check)` | 0 | 격리 worktree에서 전체 clean check 통과 |
| S-1 | `./gradlew --no-build-cache clean check` | 0 | 전 모듈 clean check 통과 |
| S-2 | `./gradlew :adapters:test --tests 'bidvector.adapters.persistence.*'` | 0 | Testcontainers 통합 test 전부 통과(V4~V6 스키마, `OpeningReservePriceRepositoryTest` 9건 포함) |
| S-2b | `./gradlew :adapters:test --tests 'bidvector.adapters.koneps.*'` | 0 | 3B·3B-2 koneps 시나리오 test — 편집 없이 초록(koneps 파일 무편집) |
| S-3 | `./gradlew :procurement:test` | 0 | 3A corpus 포함 procurement 전 test 통과 |
| S-4 | `./gradlew :adapters:moduleDependencyGate` | 0 | 통과 |
| S-5 | `./gradlew qualityBaseline` | 0 | 통과(gateExecutionGate 포함 — 등재 test 5건 실행 확인) |

verifier r1(2026-09-08) not-ready 판정(H-1·H-2) 수정 뒤 위 표 전건 재실행 — 전부 exit 0(V5·V6
신설 반영, M-1·L-7도 같은 재실행에 포함).

## 중간 실행 — 개발 중 실측(요지만)

- `./gradlew :procurement:compileTestKotlin :adapters:compileTestKotlin` — 층 A 컴파일 확인, exit 0.
- `./gradlew :procurement:test --tests bidvector.procurement.RowDiscriminatorTest` — exit 0.
- `./gradlew :adapters:test --tests bidvector.adapters.persistence.RowDiscriminatorRawKeyTest` — exit 0(Docker 필요, 실측 확인).
- `./gradlew :adapters:check` — 2회 실패 뒤(TooManyFunctions·MaxLineLength·sizeGate) 수정 후 exit 0. 실패 이력은 검증이 실제로 작동했다는 증거다.
- `./gradlew :adapters:test --tests 'bidvector.adapters.persistence.CleanMigrationTest' --tests 'bidvector.adapters.persistence.CleanMigrationTriggerTest'` — 1차 FOREIGN KEY 축 실패(복합 FK cross-product 미반영) 뒤 수정, 2차 exit 0.

## S-0 worktree 실측

`git worktree add --detach <scratchpad>/3e-r1-s0-<random> HEAD` → `./gradlew --no-build-cache clean check` exit 0 → `git worktree remove --force <dir>`로 정리, 잔여 디렉터리 없음 확인(`git worktree list`에 등록 없음).

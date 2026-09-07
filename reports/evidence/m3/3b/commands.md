# M3/3B — commands.md

base `c9d75632d3acafcaf41f0454e941dc49c62063ea` · head `3822351d6a3c4bbc38ac94acd5406297938bda3c`.
전 명령 로컬 실측(2026-09-07), 출력 전문은 담지 않는다(evidence 규격).

| # | 명령 | 결과 |
| --- | --- | --- |
| S-0 | `git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check)` | SUCCESS — 347 tasks, 전부 executed(격리 worktree, 캐시 미재사용) |
| S-1 | `./gradlew --no-build-cache clean check` | SUCCESS — 338 tasks |
| S-2 | `./gradlew :adapters:test --tests 'bidvector.adapters.koneps.*'` | SUCCESS — 14 test 전부 통과(시나리오 9 + ServiceKeyTest 3 + KonepsAdapterDependencyTest 1 + KonepsOpenApiNoticeSourceTest 나머지) |
| S-3 | `./gradlew :adapters:moduleDependencyGate` | SUCCESS |
| S-3b | `./gradlew :adapters:test --tests '*KonepsAdapterDependencyTest*'` | SUCCESS |
| S-4 | `./gradlew qualityBaseline` | SUCCESS |

## 부가 검증(evidence-pack 규격)

| 검사 | 명령 요지 | 결과 |
| --- | --- | --- |
| clean-tree 게이트(개별 pathspec) | `git status --porcelain -- <in_scope 경로 5개 개별 인자>` | 빈 출력(clean) — 양성 대조: 파일 하나를 일부러 더럽힌 뒤 같은 명령으로 감지·되돌림 확인 |
| secret 스캔 | `grep -rn "test-service-key\|super-secret" adapters/src/main/...koneps/` | main 소스에 0건 — 리터럴은 test 소스셋에만(`ServiceKeyTest.kt`·`KonepsOpenApiNoticeSourceTest.kt`) |
| 골든 사본 0 | `fixtures/input/koneps-collection-*.json` 의 `"SYN..."` 토큰 집합과 새 test 파일의 토큰 집합 교집합 | 최초 4건 충돌 발견(`SYN-NTC-000N` 우연 일치) → `SYN-3B-000N` 로 교체 커밋(`3822351`) 후 재검사 교집합 0 |
| 역방향 좌표 파급 | `grep -rn "adapters/build.gradle.kts:[0-9]\|gate-tests.properties:[0-9]\|libs.versions.toml:[0-9]" **/*.md` | 0건 — 이 slice 가 편집한 세 기존 파일을 `file:line` 으로 가리키는 문서 없음 |
| rollback 실행 가능성 | 임시 clone 에서 `git restore --source=<base> --staged --worktree -- <in_scope>` 실행 후 `./gradlew --no-build-cache clean check` | 되돌림 성공(koneps 디렉터리 삭제, 3 파일 base 상태로 복원) + 되돌린 트리에서 clean check SUCCESS(347 tasks) |

## 하네스 레인 변경 확인

`git log --oneline c9d7563..264f649 -- CLAUDE.md .claude/` — **없음**(구현 착수 시점부터 종료
시점까지 하네스 메타 파일 변경 0). `7ee8fed`(base 와 3B 착수 문서 커밋 `b9dd07c` 사이, 3B
착수 이전에 이미 병합된 하네스 커밋)은 `config/quality/gate-tests.properties` 만 건드리고
`CLAUDE.md`·`.claude/` 는 건드리지 않아 이 절의 정의(하네스 레인 = `CLAUDE.md`·`.claude/`) 밖이다.

# M3/3B — commands.md

base `c9d75632d3acafcaf41f0454e941dc49c62063ea` · head — **이 문서를 담은 커밋 자신**(`git rev-parse HEAD`, verifier r2 저low 8건 일괄
수정 커밋). N-6(verifier r2) 재발 방지로 SHA 를 하드코딩하지 않는다 — 자기참조 SHA 를
문면에 박으면 그 문면을 담는 커밋 자체가 새로 계산돼 값이 반영구적으로 어긋난다(실측:
이 라운드 안에서 실제로 한 번 발생 — 아래 참고). 전 명령
로컬 실측(2026-09-07), 출력 전문은 담지 않는다(evidence 규격).

| # | 명령 | 결과 |
| --- | --- | --- |
| S-0 | `git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check)` | SUCCESS — 347 tasks, 전부 executed(격리 worktree, 캐시 미재사용) |
| S-1 | `./gradlew --no-build-cache clean check` | SUCCESS — 338 tasks |
| S-2 | `./gradlew :adapters:test --tests 'bidvector.adapters.koneps.*'` | SUCCESS — 23 test 전부 통과(`KonepsOpenApiNoticeSourceTest` 19 + `ServiceKeyTest` 3 + `KonepsAdapterDependencyTest` 1) |
| S-3 | `./gradlew :adapters:moduleDependencyGate` | SUCCESS |
| S-3b | `./gradlew :adapters:test --tests '*KonepsAdapterDependencyTest*'` | SUCCESS |
| S-4 | `./gradlew qualityBaseline` | SUCCESS |

## N-6(verifier r2) — head 선언 정정

r1 수정 라운드에서 `commands.md`·`checklist.md` 가 head 를 `401a353`(코드 커밋)으로 적었으나
그 뒤 evidence 커밋(`436a755`)이 하나 더 붙어 실제 head 와 어긋났다. 이번 라운드는 「코드
커밋 뒤 evidence 커밋」 분리 대신 **finding 전체를 한 커밋에 담는다** — 그런데 그 한 커밋
안에서도 이 문서가 자기 자신의 SHA 를 하드코딩하면 같은 문제가 재발한다는 것을 실측으로
확인했다: 최초 커밋 뒤 SHA 를 알아내 문서에 박고 `--amend` 했더니 커밋 내용이 바뀌어
**SHA 자체가 다시 바뀌었다**(문서가 이미 낡은 값을 담은 채 확정). 그래서 위 head 줄은
값을 하드코딩하지 않고 「이 커밋 자신」으로 절차적으로 가리킨다 — 이 표현은 어떤 amend
를 거쳐도 낡지 않는다.

## 3A 비손상 확인(H-3 로 procurement 를 좁게 확장했으므로 매 라운드 재확인)

| 명령 | 결과 |
| --- | --- |
| `./gradlew :procurement:test` | SUCCESS — 기존 `AccountingTest` 6 test 무변경 통과 + 신설 5 test(H-3 invariant) 통과 |
| `./gradlew :app:test --tests '*Conformance*'` | SUCCESS — `SharedKernelCorpusConformanceTest` **69/69**(koneps-collection 포함 전 도메인 corpus, 0 skipped·0 failures) |

## 부가 검증(evidence-pack 규격)

| 검사 | 명령 요지 | 결과 |
| --- | --- | --- |
| clean-tree 게이트(개별 pathspec) | `git status --porcelain -- <in_scope 경로 7개 개별 인자>` | 빈 출력(clean) — 양성 대조: 파일 하나를 일부러 더럽힌 뒤 같은 명령으로 감지·되돌림 확인 |
| secret 스캔 | `grep -rn "test-service-key\|super-secret" adapters/src/main/...koneps/` | main 소스에 0건 — 리터럴은 test 소스셋에만(`ServiceKeyTest.kt`·`KonepsOpenApiNoticeSourceTest.kt`) |
| 골든 사본 0 | `fixtures/input/koneps-collection-*.json` 의 `"SYN..."` 토큰 집합과 새 test 파일의 토큰 집합 교집합 | 최초 4건 충돌 발견(`SYN-NTC-000N` 우연 일치) → `SYN-3B-000N` 로 교체(`3822351`) 후 재검사 교집합 0. **정정(verifier r1 L-2)** — `synNewKey`/`"1234"` 는 `fixtures/input/koneps-collection-001.json` 과 값까지 같지만 이것은 3A 계약 문서(D-3A-3 「미지 필드」)가 공유하는 예시 어휘의 **의도적 재사용**이지 골든 파일 바이트의 복제가 아니다(구조 자체가 다르다 — 001 은 `fieldContract`/`payload` 형태, 3B 는 KONEPS wire envelope 형태) |
| 역방향 좌표 파급 | `grep -rn "adapters/build.gradle.kts:[0-9]\|gate-tests.properties:[0-9]\|libs.versions.toml:[0-9]\|Accounting.kt:[0-9]" **/*.md` | 0건 — 이 slice 가 편집한 기존 파일을 `file:line` 으로 가리키는 문서 없음 |
| rollback 실행 가능성 | 임시 clone(이번 라운드 head)에서 `rollback.md` 의 두 명령(base 대상 + `gate-tests.properties` 전용 하네스 대상) 실행 후 `./gradlew --no-build-cache clean check` + `TestShapesTest` 등재 생존 확인 | 되돌림 성공(koneps 16 파일 삭제, 5 파일 base/하네스 상태로 복원) + `grep -c TestShapesTest` = 2(생존) + 되돌린 트리에서 clean check SUCCESS(347 tasks, `buildLogicGateExecutionGate` 포함) — H-1 이 이번 라운드 head 에서도 재확인됨 |

## 하네스 레인 변경 확인

`git log --oneline c9d7563..HEAD -- CLAUDE.md .claude/` — **없음**(구현 착수 시점부터 이
수정 라운드까지 하네스 메타 파일 변경 0). `7ee8fed`(base 와 3B 착수 문서 커밋 `b9dd07c`
사이, 3B 착수 이전에 이미 병합된 하네스 커밋)은 `config/quality/gate-tests.properties`
만 건드리고 `CLAUDE.md`·`.claude/` 는 건드리지 않아 이 절의 정의(하네스 레인 = `CLAUDE
.md`·`.claude/`) 밖이다 — 다만 그 파일을 3B 와 공유해 rollback 절차가 갈린다(H-1, 위 표).

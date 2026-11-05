# M9 / gates-tests — adapters test 술어 · 판정 타입 가시성
- base: main `df4976cc` · 공통 규율: `/tmp/claude-1000/-home-deploy-project-bid-vector-v2/b843a5c1-93ae-42d9-82cf-0a5222fc9820/scratchpad/m9-lane-rules.md`
- 사용자 지시 2026-11-06: 「남은 묶음도 지금 같이 병렬로」(2026-11-05 「남은 항목이 없도록 전체 병렬」의 연속). 결정 지점은 추천안으로 진행(전역 규칙), 외부 비용·운영 결정이 필요한 축은 만들지 않고 등재.
- rollback: 공통 규율대로(원천 `df4976cc`, 내 커밋만, 결과 트리 동일성). 하네스 레인 변경: 계약이 명시한 것 외 없음.

## 대상
| OPEN | 처방 |
| --- | --- |
| `OPEN-PERSISTENCE-GATE-PREDICATE-TYPE` | `PersistenceAdapterDependencyTest` 의 소스 텍스트 import 정규식을 형제 여덟과 같은 **상수 풀 술어**로 이식 |
| `OPEN-CHECK-BODY-PRESENCE-ASSERTIONS` | `CleanMigrationCheckTest` 의 `checkBodies.any { contains }` 셋을 같은 파일 선례의 「표 범위 본문 집합 등식」으로, `queryConstraintDef` 0 행이면 명시 실패 |
| `OPEN-VERDICT-CONSTRUCTION-VISIBILITY` | `qualification` 의 `LicenseVerdict` subtype 생성자를 `internal` + `@ConsistentCopyVisibility`; test 조립은 testFixtures factory 로 |
| `OPEN-6F4W-UNAVAILABLE-PRODUCER` | `WatchSubjectUnavailable` 의 production 생산자 0 — `NoticeWatchSubjectPort` 가 그 갈래를 실제로 낼 수 있는 경로를 세우거나(조회 실패를 값으로), 불가하면 어휘 정리 판단을 evidence 에 |

## in_scope
`adapters/src/test/kotlin/**`(해당 test) · `qualification/**` · 커널 둘·testFixtures · `adapters/src/main/kotlin/bidvector/adapters/evaluation/NoticeWatchSubjectPort.kt`(+test) · `config/quality/gate-tests.properties` · 해당 OPEN 문서 행 · evidence
## acceptance
각 술어 양성·음성 대조, 바꿔치우기 변이 RED. 전건 PR CI.

# M9 / gates-build — 게이트 술어의 약한 층 (build-logic · 정책)
- base: main `df4976cc` · 공통 규율: `/tmp/claude-1000/-home-deploy-project-bid-vector-v2/b843a5c1-93ae-42d9-82cf-0a5222fc9820/scratchpad/m9-lane-rules.md`
- 사용자 지시 2026-11-06: 「남은 묶음도 지금 같이 병렬로」(2026-11-05 「남은 항목이 없도록 전체 병렬」의 연속). 결정 지점은 추천안으로 진행(전역 규칙), 외부 비용·운영 결정이 필요한 축은 만들지 않고 등재.
- rollback: 공통 규율대로(원천 `df4976cc`, 내 커밋만, 결과 트리 동일성). 하네스 레인 변경: 계약이 명시한 것 외 없음.

## 대상 (M6 종결 open-inventory §2 「하네스 레인」 열둘 중 build-logic 축 + 소스 축)
| OPEN | 처방(구조로) |
| --- | --- |
| `OPEN-MIGRATION-ORDER-GATE` | 새 build-logic task: `db/migration` 전수에서 version 파싱 → 결번·중복·내림차순 0 을 집합 등식으로, 디렉터리를 `@InputDirectory` 선언 입력 |
| `OPEN-CPD-GATE-RENAME-BYPASS` | CPD Type-2 정규화(`ignoreIdentifiers`·`ignoreLiterals`)를 정책 키로 두 Cpd task 에; 판정식 변경(Type-3)은 알려진 한계로 선언. 낡은 리포트가 실행 안 함을 가리는 축(didWork/mtime)도 |
| `OPEN-JAR-CONTENT-GATE-BOOTJAR-BLINDSPOT` | archives 를 모듈의 Jar 타입 task 전수(`bootJar` 포함, `BOOT-INF/classes/` 접두 벗김) provider 로 + 빈 아카이브 입력은 실패(비-공허 단언) |
| `OPEN-HARNESS-GATE-TESTS-ORDER` | `GateRegistrationGateTask` 에 「모듈 블록 안 등재 행 == 정렬판」 단언 + main 에 이미 어긋난 블록(`app`·`decision`·`procurement` 등) 같은 커밋 정렬 |
| `OPEN-KTLINT-UNUSED-IMPORTS-SILENT` | 자체 소스 축 게이트(선례 `SourceLanguageGateTask`·`DomainSourceReferenceGateTask` 모양): import 집합 ∖ (본문 참조 ∪ 허용) == ∅ |
| `OPEN-BYTECODE-GATE-CONST-VAL-BLINDSPOT` | `DomainSourceReferenceGateTask` 의 정책 키를 adapters 네 게이트 축(evaluation·qualification·profile·audit)으로 확장(새 술어 종류 0) |
| M6 종결 checklist §3 ④ 「소스 축 상시 게이트 부재」 | legacy 좌표(금지 패키지 뿌리·경로)를 `architecture-policy.properties` 거부 집합으로 → 의존·소스 층 task 가 상시로 잰다 |
| M9 리뷰 후속: 레인 env-sniff | OPS-13 「실행 환경 스니핑 0」 게이트(임계 0 이라 승인 불필요) — 같은 소스 축 task 의 정책 키로 |

## in_scope
`build-logic/**` · `config/quality/**` · 위 게이트가 고쳐야 하는 기존 위반(있으면 그 파일 — 목록을 evidence 에) · 해당 OPEN 행이 있는 문서(`docs/discovery/capability-map.md` 의 그 행들, `reports/evidence/m6/closure/open-inventory.md` 덧붙이기) · `reports/evidence/m9/gates-build/**`

## acceptance
각 게이트: 양성 대조(심은 위반 → 실패) · 음성 대조 · 바꿔치우기 변이 RED. `build-logic` 자체 test. 전건은 PR CI `check`.
## out_scope
adapters test 술어(다른 레인) · CI 워크플로 편집(필요하면 멈추고 보고) · mutation 도구.

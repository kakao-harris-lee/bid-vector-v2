# M9 / gates-build — 게이트 술어의 약한 층 (build-logic · 정책)
- base: main `df4976cc` · 공통 규율: `/tmp/claude-1000/-home-deploy-project-bid-vector-v2/b843a5c1-93ae-42d9-82cf-0a5222fc9820/scratchpad/m9-lane-rules.md`
- 사용자 지시 2026-11-06: 「남은 묶음도 지금 같이 병렬로」(2026-11-05 「남은 항목이 없도록 전체 병렬」의 연속). 결정 지점은 추천안으로 진행(전역 규칙), 외부 비용·운영 결정이 필요한 축은 만들지 않고 등재.
- rollback: 공통 규율대로(원천은 **실제로 병합한 main 고정 SHA**, 내 커밋만, 결과 트리 동일성). 하네스 레인 변경: 계약이 명시한 것 외 없음 — 이 slice 는 `.claude/**` 를 만지지 않는다. **다음 하네스 편집 대상 하나를 등재만 한다**(verifier r6 L-1): rollback 유효성 술어를 「`git diff --name-only <실측 HEAD>..<판정 SHA> -- <복원 목록>` 이 빈 출력」에서 「∀ p ∈ D : p 가 원천에 없다」로 넓힌 것은 건전하지만(원천에 없는 경로는 복원이 내용과 무관하게 지운다), CLAUDE.md 하네스 요약과 `evidence-pack` 문면은 아직 「한 줄이라도 나오면 미검증」이다. 장부를 고치지 않으면 다음 검증자가 이 `rollback.md` 를 규칙 위반으로 읽는다.
- 수정 라운드 1(팀장 지시 2026-11-06, verifier `not-ready` B-1·H-1·H-2 + code-reviewer `request_changes` HIGH 1·MEDIUM 6): 처방은 아래 「수정 라운드 1 의 처방」 절. **in_scope 에 `app/build.gradle.kts` 한 경로를 더한다**(cr M-4 — 닫힌 사각을 「열린 채」로 적는 주석 한 문단, 팀장 승인). 그 파일의 거동(`jar` 켜 둠)은 바꾸지 않는다.
- 수정 라운드 4(팀장 지시 2026-11-08, **사용자가 상한 너머 한 라운드만 승인** — 다음 재검증이 not-ready 면 머지 없이 사용자 보고; verifier `not-ready` H-1 + code-reviewer `request_changes` HIGH 1·MEDIUM 4·LOW 5): 중첩 축을 경로·다중도·뿌리 밖으로 닫고, 문면을 술어 크기로 좁히며, main `3101035f` 를 병합한다. **in_scope 는 바뀌지 않는다** — 이 라운드가 만든 새 파일은 `build-logic/src/test/kotlin/bidvector/buildlogic/NestedArchivePolicyTest.kt` **하나**이고 `build-logic/**` 안이다(대조 완료). 라운드가 더한 정책 키 둘(`jar.boot-launcher-main-class` · `bytecode.env-access.allowed-callers`)도 `config/quality/**` 안이다. 뒤의 키는 **운영자 확인 대상**으로 `checklist.md` 제한 21·OPEN 에 등재했다.

- 수정 라운드 5(팀장 지시 2026-11-08, **사용자가 상한 너머 한 라운드만 승인** — 이 재검증이 not-ready 면 머지 없이 사용자 보고; verifier r5 `not-ready` H-1 + code-reviewer r4 `approve` + MEDIUM 5·LOW 6): 중첩 축이 기대던 로더가 **배포 경로의 로더가 아니었다** — 출하 이미지는 `extract --layers` 로 풀려 뜬다. 그 도구가 읽는 manifest 속성 여섯과 classpath index 등식을 게이트로 세우고, 뿌리 밖 판독을 EOCD 로 바꾼다. main `70406318` 을 병합한다. **in_scope 는 바뀌지 않는다** — 이 라운드가 만든 새 파일은 넷(`BootArchivePolicy.kt` · `NestedArchiveReader.kt` · `BootArchivePolicyTest.kt` · `ArchiveFixtures.kt`)이고 전부 `build-logic/**` 안이다(`git diff --name-status 70406318..HEAD` 의 A 행 대조 — 이 slice 가 만든 A 행 24 가운데 이 라운드가 넷). 라운드가 더한 정책 키 넷(`jar.boot-start-class` · `jar.boot-classpath-index` · `jar.boot-layers-index` · `jar.boot-archive-module`)도 `config/quality/**` 안이다.

- 수정 라운드 6(팀장 지시 2026-11-10 · **사용자 승인 2026-11-08** 「작은 수정 + 짧은 표적 재검증 → CI → 머지」; verifier r6 `ready-for-review`(차단 0 · medium 4 · low 1) + code-reviewer r5 `request_changes` HIGH 1·MEDIUM 3·LOW 3): EOCD 술어를 등식에서 **포함**으로(뒤로 붙인 포장 — 1 바이트로 열리던 자리), 판독기가 조용히 좁아지던 자리 둘에 test, 적재 자리 키의 이중 읽기를 한 번으로, 허용 목록의 빈 칸·중복을 정책 오류로, 문면 넷을 술어 크기로. main `07140729` 을 병합한다(겹침 **하나** — `docs/discovery/capability-map.md` · 충돌 0 · 손 해소 줄 0; 「충돌 0 자동 병합이 같은 본문을 접는다」를 양쪽 추가 줄 **전수 생존**으로 확인했다 — 내 28/28 · 상대 51/51 · 중복 제목 0). **in_scope 는 바뀌지 않고, 이 라운드는 새 파일을 하나도 만들지 않았다** — `git diff --name-status 4991dde9^..HEAD` 가 **전부 M 행**이고 `build-logic/**` 와 `reports/evidence/m9/gates-build/**` 안이다. 정책 키도 더하지 않았다(새 test 클래스가 없으므로 `gate-tests.properties` 등재도 해당 없음).

- 여덟째 병합(팀장 지시 2026-11-11 — PR #140 머지 뒤 main 하나로 한 번만 병합): main `0c2fd7ae`(그 안에 #141 `22c77cbb` 포함)를 **SHA 고정**으로 병합한다. 겹침 열셋 · 충돌 **하나**(`RetentionFixture.kt` 의 import 블록) · 손 해소 둘 모두 **한쪽 거동을 버리지 않는다**. **in_scope 는 바뀌지 않는다** — 이 병합이 복원 목록에 더한 경로 둘(`CollectionWiringTest` · `RelayVocabularyDatabaseTest`)은 in_scope 의 「위 게이트가 고쳐야 하는 기존 위반(있으면 그 파일)」항목에 해당하고(목록은 `checklist.md`), 정책 한 줄(evaluation 축 허용 뿌리 +1)도 `config/quality/**` 안이다. **새 파일 0 · 새 정책 키 0**(값 한 줄만 늘었다) · 새 test 클래스 0 이라 `gate-tests.properties` 등재도 해당 없다(병합이 들여온 main 의 새 항목 하나는 그 파일의 정렬 자리만 바로잡았다).

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

## 수정 라운드 1 의 처방 (팀장 지시 2026-11-06)
| finding | 처방(구조로) |
| --- | --- |
| v B-1 (blocker) | convention plugin 543 줄 > 500 → **상한을 올리지 않고** 게이트 배선을 별 파일로 나눈다. 이번 라운드 로컬 실측에 **루트 게이트**(`buildLogicSizeGate`·`scriptSizeGate`·루트 unused-import 둘)를 반드시 포함 |
| v H-1 = cr HIGH | 환경 스니핑 축을 **텍스트 쌍에서 바이트코드 메서드 참조로** — 전 모듈 production 클래스에서 거부 집합(`java.lang.System#getenv` 등)을 잰다. 소스 표기(정적 import·별칭·수식)와 무관. 우회 여섯을 양성 대조로, **RED 선행**. evidence 두 자리의 반대 단언을 측정에 맞춘다 |
| v H-2 = cr M-2 | Flyway 는 classpath 전체를 읽는다 → **비-소유 모듈에서 `db/migration` 이 비어 있지 않으면 실패**(조용한 return 금지) |
| cr M-1 | `present ∖ expected ∖ retired` 를 위반으로(= `V0`·1 미만) + test |
| cr M-6 | `MigrationOrderTest` 의 열거를 게이트와 같게(`walkTopDown`) |
| v M-2 | 비-공허 단언 둘(jar·migration)에 회귀 test — 무력화 변이가 RED |
| v M-1 | bootJar 중첩 jar(`BOOT-INF/lib`) — 닫거나, 알려진 제한 + OPEN 으로 정직하게 |
| cr M-3 | 죽은 면제 35 — 실측으로 좁히거나 「면제 ∖ 도입 이름 == ∅」 검출(① 과 같은 모양) |
| cr M-4 | `app/build.gradle.kts` 주석 한 문단 교체(in_scope 추가) |
| cr M-5 | 형제 축 넷(`{strategy,notice,opening,relay}`)을 `source.scoped-*` 에 값 복사 |
| cr LOW 7 · v L-1~L-3 | 일괄 |

## in_scope
`build-logic/**` · `config/quality/**` · 위 게이트가 고쳐야 하는 기존 위반(있으면 그 파일 — 목록을 evidence 에) · 해당 OPEN 행이 있는 문서(`docs/discovery/capability-map.md` 의 그 행들, `reports/evidence/m6/closure/open-inventory.md` 덧붙이기) · `reports/evidence/m9/gates-build/**` · `app/build.gradle.kts`(주석 한 문단 — 수정 라운드 1 cr M-4)

## acceptance
각 게이트: 양성 대조(심은 위반 → 실패) · 음성 대조 · 바꿔치우기 변이 RED. `build-logic` 자체 test. 전건은 PR CI `check`.
## out_scope
adapters test 술어(다른 레인) · CI 워크플로 편집(필요하면 멈추고 보고) · mutation 도구.

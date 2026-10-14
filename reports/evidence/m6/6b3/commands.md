# M6/6B-3 — commands

base `e2232a75` · 브랜치 `m6-6b3/2026-10-13` · worktree `bid-vector-v2-m6-6b3`.
산출물 커밋 14. **판정 SHA 후보 `b252fa0c`**(마지막 산출물 커밋).

## acceptance (CI `check` job 명령 그대로)

판정 SHA 후보 `b252fa0c` 에서 **양끝 HEAD 를 단언하고** 돌렸다(측정 중 커밋 0).

| # | 명령 | 결과 |
|---|---|---|
| 1 | `./gradlew --no-daemon check --rerun-tasks` | **exit 0** · 10m23s · **350 task 전부 실행**(캐시·up-to-date 0) · test 합계 **2,948** / fail 0 |
| 2 | `./gradlew --no-daemon qualityBaseline --rerun-tasks` | **exit 0** · 24s · 24 task 전부 실행 |

**둘 다 `--rerun-tasks` 로 쟀다** — 앞 라운드에서 초록이 캐시·실행 순서에 의존하던 것을 한 번
실측했으므로(그때 fixture 가 TRUNCATE 밖 공유 표를 오염시킨 것이 순서가 바뀌자 드러났다) 판정의
근거를 「아무것도 캐시하지 않은 run」으로 둔다.

skipped **4** 는 선언된 제외 하나(`RealServerIntegrationTest` — `@EnabledIfSystemProperty`,
`gate.tests.adapters.excluded` 등재)에서 온다(vr L-1 — 앞 판 evidence 는 「skipped 0」으로 적었고 그것은
실측과 달랐다). **6B-3 의 test class 16 은 skipped 0** 이다.

`ml-engine` job 은 생략한다 — Python 산출물 0(`git diff --name-only e2232a75..b252fa0c -- ml-engine`
빈 출력). `container` job 도 생략한다 — `docker/**`·`ci.yml` 무편집(같은 질의 빈 출력).

## test 수

모듈 합계 **2,948**(adapters 986 · app 586 · workflow 446 · build-logic 279 · procurement 263 ·
decision 184 · shared-kernel 89 · strategy 84 · qualification 31) · fail 0 · skipped 4.

이 라운드가 더한 test 는 **29**(앞 라운드 끝 2,919 → 2,948 — 등식이 닫힌다): 신설 class 셋
(`MaskingColumnEnumerationTest` 2 · `NoticeAuditPiiAxisTest` 5 · `CleanMigrationIndexTest` 2) +
기존 class 에 더한 20 — 마스킹 축 셋에 7(실 SQL 4 · 투영 등식 1 · 순수 함수 2) · 트리거 가드에 6 ·
파기 축에 2 · 트리거 장부 1 · 분류 등식 1 · use case 1 · 파기 순서 1 · 배선 운반 1.
보존 패키지 전체는 class 11 · test 107 이다.

## 변이 실측 (바꿔치우기 — 적용 직후 `git diff --numstat` 로 교체를 확인, 산출물은 먼저 커밋했다)

이 라운드 16 변이 · **16 KILLED · 0 SURVIVED**. 각 변이는 「적용 → 교체 확인 → 표적 test → 되돌림 →
산출물 경로 `porcelain` 빈 것 확인」 한 바퀴다. 앞 라운드 20 변이(M-1~M-16)는 `rollback.md` 가
가리키는 앞 evidence 와 같고, 그 가운데 거동이 바뀐 셋(M-2·M-3 의 outbox 축 · M-5)은 아래가 대체한다.

| # | 무엇을 바꿔치웠나 | 결과 (FAIL 한 test) |
|---|---|---|
| V-1 | 배선의 정책을 `RetentionPolicyData(1일, 1_000)` 로 (verifier r1 재현) | **KILLED — 컴파일 거부**(`:app:compileKotlin`). 컴파일러 문면: 「Cannot access 'constructor(retentionDays: Int, batchSize: Int)…': it is internal in 'bidvector.workflow.retention.RetentionPolicyData'」. 앞 판에서는 같은 변이가 app test 전건을 초록으로 지났다 |
| V-2 | 보존 임대 키를 relay 키와 같게 (verifier r1 재현) | KILLED (2) — 임대 양방향 |
| V-3 | `notice` 에 담당자 꼴 열 추가 + 열 장부 한 줄 (verifier r1 재현) | KILLED (1) — 「`notice` 열 집합 == 분류가 아는 열 집합」 |
| N-1 | 마스킹 **개념 집합을 비운다**(업체명이 남는다) | KILLED (6) — 두 열 각각 · 투영 등식 · 남김 집합 |
| N-2 | 관문 ③ 을 역할 **이름** deny-list 로 되돌린다 | KILLED (1) — 제3 역할 거부 |
| N-3 | INSERT 관문의 표지 검사를 무력화 | KILLED (1) — 표지를 찍은 적재 거부 |
| N-4 | 투영 열 형태 CHECK 를 제거(V18 생성·V19 검증 **쌍**으로) | KILLED (2) — 비객체 적재 거부 · CHECK 수 장부 |
| N-5 | 관문 ⑤ 에서 투영 열을 마스킹 열에서 뺀다(불변으로 되돌린다) | KILLED (6) — 두 열 마스킹이 전부 거부된다 |
| N-6 | 관문 ⑤ 를 **열 하나 열거**로 바꾼다(행 전체 비교를 버린다) | KILLED (1) — 열 카탈로그 전수 열거가 다른 열의 가변성을 잡는다 |
| N-7 | CASCADE 자식의 기한 가드를 제거(앞 판 거동) | KILLED (1) — 기한 안쪽 자식·헤더 생존 |
| N-8 | outbox 파기 집합에 `ISOLATED` 를 되돌린다 | KILLED (1) — 미발송·격리 생존 |
| N-9 | 일괄 종료 조건을 **계수한 수**로 되돌린다 | KILLED (1) — 가득 찬 일괄이 한 번 더 돈다 |
| N-10 | 자기참조 간선을 순환에 되돌린다 | KILLED (1) — 그 표 하나로 run 전체가 멈추지 않는다 |
| N-11 | 마스킹 판독 깊이를 수집 상한 밑으로(32 → 8) | KILLED (1) — 수집 상한과의 관계 |
| N-12 | cutoff 인덱스 하나를 지운다 | KILLED (1) — 인덱스 장부(축9) |
| N-13 | 배선이 **시스템 시계**로 정책을 고른다 + 뒤 날짜 entry 추가(운반 축) | KILLED (1) — 러너가 쥔 정책 == 해석값 |

> **되돌림 확인의 범위**: 변이 구동기가 매 바퀴 끝에 `git status --porcelain` 을 전역으로 봤고, 그
> 출력에 `reports/evidence/m6/6b3/checklist.md` 한 줄이 남아 있었다 — **같은 시각에 이 레인이 그
> evidence 파일을 편집하고 있었기 때문**이고 변이 파일의 되돌림과는 무관하다. 전 바퀴가 끝난 뒤
> **산출물 경로 한정** `git status --porcelain -- <경로 개별 인자>` 가 빈 출력임을 따로 확인했다.

## 게이트가 실제로 막은 것 (이 라운드에 받은 RED·정지)

| 게이트·실측 | 무엇을 막았나 |
|---|---|
| **Flyway + `CREATE INDEX CONCURRENTLY`(정지)** | 계약 ⑥ 의 문면대로 `CONCURRENTLY` + 형제 `.sql.conf`(`executeInTransaction=false`)를 넣자 test 가 **끝나지 않았다**. `pg_stat_activity` 를 보니 Flyway **메타데이터 연결이 `idle in transaction`** 이고 마이그레이션 연결이 `Lock/virtualxid` 로 막혀 있었다(14분 뒤에도 그대로 — 서로를 푸는 일이 없다). `CONCURRENTLY` 는 동시 트랜잭션 전부가 끝나기를 기다리는데 Flyway 가 자기 연결로 하나를 열어 둔다. **적용 창을 줄이려던 조치가 「실패」가 아니라 「정지」를 사는 거래**였다 — V19 분리는 남기고 `CONCURRENTLY` 는 기각했다(checklist 알려진 제한 11) |
| **`CollectionArchitectureGateTest`(D-6F8-6)** | 배선이 계약 레지스트리를 손에 쥐려 한 자리 — 그 게이트의 허용 **접근자 집합**은 수집 배선 둘이고 **집합 등식**이라 셋째가 끼면 두 test 가 함께 붉는다. 허용 목록에 한 줄 더하는 길을 **택하지 않고**, 해소를 원문 키 매핑을 소유한 모듈로 옮겨 닫았다 — 그래서 「원문 값 획득이 어디서 열리는가」의 답이 그대로다 |
| CHECK 수 장부(축8) | 새 CHECK 제약 하나를 등재 없이 더한 상태 — expected 4, was 5 |
| 인덱스 장부(축9, 이 slice 가 신설) | Flyway 자신의 보조 인덱스 하나가 등재 밖이었다 — 「우리가 만든 것」이 아니라 「있는 것」이 집합의 정의임을 그 자리에서 고쳤다 |

## D-6B3-3 ① — 「업체명을 지워도 소비자가 깨지지 않는가」 기계 수집

운영자 결정 전에 **먼저 실측했다**(계약이 요구한 순서). 결론: 업체명을 읽는 **저장 행 소비자가 0**
이므로 「90일 안쪽만 읽는다」를 보일 필요가 없다.

| 축 | 질의 | 결과 |
|---|---|---|
| ⓐ 원문 열을 읽는 production 질의 | `RawPayloadProductionReaderTest`(기계 수집 + 공허·음성 대조) | **0** |
| ⓑ 투영 열을 읽는 production 파일 | `grep -rln payload_fields adapters/src/main/kotlin workflow/src/main/kotlin app/src/main/kotlin` | 아홉 — 쓰기 경로 넷(투영 생성·키 유도·INSERT 문)·마스킹 둘·**읽기 셋** |
| ⓒ 읽기 셋이 **어느 키**를 읽는가 | 수집 축 조회는 공고 식별자 둘뿐(이름의 출처가 계약 상수다) · 스냅숏 추출은 식별자 둘을 `->>` 로 꺼내고 나머지는 **개념으로** 읽는다 | 개념 축 전수 `grep -rh 'FieldConcept\.[A-Z_]*' adapters/src/main/kotlin --exclude-dir=retention -o \| sort -u` → **35 개념 · 업체명 개념 0** |
| ⓓ ml-engine(백테스트·학습) | `grep -rn 'raw_observation\|payload_fields\|<업체명 raw 키 둘>' ml-engine/src ml-engine/tools ml-engine/policy` | **0 건** — 그 표를 전혀 읽지 않는다 |
| ⓔ 업체명 raw 키를 읽는 production 자리 | 같은 질의를 Kotlin 에 | **수집 시점 한 자리** — 개찰완료 행의 **행 식별자**(`rowIdentifierRawKeys`)다. 그 값은 **HTTP 응답 필드**에서 읽히고 저장 행에서 다시 읽히지 않는다(`ObservationKeyDerivation.of` 의 호출자는 적재 경로 하나뿐). 90일 지난 행을 마스킹해도 그 경로에 닿지 않는다 |

**포함의 다른 절반도 다시 쟀다**: 마스킹은 이제 투영 열을 **바꾼다**(앞 판은 트리거가 그 열을
불변으로 둬서 「소비자가 영향받을 수 없다」가 성립했다). 그래서 보장은 「투영이 불변」이 아니라
**「투영에서 사라지는 키 == 업체명 둘 == 소비자가 읽지 않는 키」**이고, 그 등식을 production 쓰기
경로 위의 test 가 센다(투영 키 − 업체명 == 마스킹 뒤 남은 키).

## 비밀값·누출 스캔

`grep -rniE -f config/quality/leak-patterns.txt` 를 **변경 파일 전수 + evidence + `_workspace`** 에
손으로 돌렸다 — 나온 것은 ⓐ 설정 속성 이름 대입 ⓑ `architecture-policy.properties` 의 비밀 축
**등재 키 이름** ⓒ 로그 무유출을 재는 test 의 자리값 라벨이고 **값은 하나도 없다**.
`qualityBaseline` 의 해당 게이트는 **`reports/evidence` 디렉터리만** 본다(privacy F-4 — 앞 판은 그
게이트가 산출물 경로까지 「전건으로 포함한다」고 적었고 그것은 성립하지 않는다). 산출물 경로의
결과는 위의 손 실행이 정본이다.

fixture 자리값은 **형태를 흉내내지 않는다** — 담당자 키·업체명이 사라지는지만 재므로 전화·전자우편
꼴이 필요 없다.

## clean-tree

`git status --porcelain -- <in_scope 경로 개별 인자>` → 빈 출력(위 변이 절의 각주 참조 — 변이
바퀴마다 같은 질의를 돌렸다). **양성 대조 1회**(비파괴 절삭): 산출물 파일 하나의 끝 한 줄을 지운
상태에서 같은 질의가 그 파일을 `M` 으로 냈고, 되돌린 뒤 다시 빈 출력이었다(`git checkout --` 를
쓰지 않는다 — 미커밋 산출물을 함께 지운다).

## DB 격리 단언

이 slice 의 모든 DB 실행은 **Testcontainers** 위에서다(`PersistenceTestSupport` 의 공유 컨테이너,
`postgres:16.4`). dev DB(`bid-vector-v2-dev`, 127.0.0.1:55432)·legacy DB 로의 접속은 **0** 이다.
정지 사건을 진단할 때 들여다본 것도 그 **일회용 컨테이너**이고(`pg_stat_activity`), 진단 뒤 컨테이너를
제거했다. 러너를 실제 DB 에 돌리지 않았다 — 운영 실행은 별도 승인 사항이다.

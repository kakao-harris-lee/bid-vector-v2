# M6/6F-6 — 운영자 프로필 영속(`OperatorProfilePort`) (2026-09-18)

```yaml
milestone: M6
slice: 6f6-operator-profile
base_sha: ede5d5b   # main. PR #36(6F-2)·#37(운영자 결정 셋) 병합 뒤, CI 초록
head_sha: 리뷰 요청 시점의 `git rev-parse HEAD`(값을 박지 않는다)
in_scope:
  - adapters/src/main/resources/db/migration/V12__operator_profile.sql              # 프로필 표. **V10 은 6A-1·V11 은 6F-4 가 선점**(D-6F6-7)
  - adapters/src/main/kotlin/bidvector/adapters/profile/JdbcOperatorProfileRepository.kt  # `OperatorProfilePort` 첫 production 구현 + 저장
  - adapters/src/main/kotlin/bidvector/adapters/profile/OperatorProfileRow.kt       # 행 ↔ 도메인 매핑(`internal`)
  - adapters/src/main/kotlin/bidvector/adapters/persistence/Sql.kt                  # 프로필 SQL 추가만(기존 문장 무편집)
  - adapters/src/test/kotlin/bidvector/adapters/profile/JdbcOperatorProfileRepositoryTest.kt  # 왕복 · 미설정 · 미선언↔빈 목록 · 갱신
  - adapters/src/test/kotlin/bidvector/adapters/profile/ProfileAdapterDependencyTest.kt      # 신설 패키지 의존 게이트(바이트코드 상수 풀, D-6F6-6)
  - adapters/src/test/kotlin/bidvector/adapters/profile/ProfileGateRegistrationTest.kt       # 등재 완결성(6F-2 D-6F2-10 의 귀결)
  - adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationTest.kt            # 신설 표를 여덟 축 스키마 게이트에 등재(추가만)
  - adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationColumnTest.kt      # 같은 축(컬럼 존재·타입·NOT NULL)
  - adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationCheckTest.kt       # 같은 축(CHECK 개수)
  - adapters/src/test/kotlin/bidvector/adapters/persistence/PersistenceTestSupport.kt        # 신설 표를 test 간 정리 목록에 추가(기계적)
  - config/quality/gate-tests.properties                                             # 신설 게이트 test 등재(추가만) — 공유 파일
  - reports/evidence/m6/6f6/**
  - milestone-6.md                                                                   # 6F-6 착수 문단(팀장 커밋)
out_of_scope:
  - HTTP endpoint·인가·편집 명령                # **6A 소관.** 결정 ② 의 「편집 endpoint」는 이 slice 가 아니다 — `6A-1` 계약이 `BidVectorApplication.kt`·`PersistenceWiring.kt`·`app/build.gradle.kts` 를 이미 in_scope 로 선점했고 **두 레인이 같은 파일을 만들 수 없다**(D-6F6-1)
  - strategy/**                                 # **무편집.** `CategoryCode`·`KeywordScopeText` 가 같은 파일(`strategy/Text.kt`)에 살고 **6F-4 가 그 파일을 in_scope 로 잡고 있다**(D-6F6-4)
  - `WorkloadPort` 구현                         # 운영자 결정 ②(2026-09-18)가 분리했다 — D-6F6-5
  - 면허 요구사항 영속·면허 게이트 배선          # 6F-5. 이 slice 는 `OperatorLicenses` 를 **싣기만** 하고 판정에 꽂지 않는다
  - app DI 조립·진입점                          # `OPEN-6F-ASSEMBLY`
  - 프로필 값의 초기 투입(seed·백필)             # 투입 경로는 6A 의 편집 endpoint 다. 그때까지 `current()` 는 `null`(「미설정」)을 낸다 — 도메인이 이미 표현하는 상태다
acceptance_commands:
  - "./gradlew --no-daemon check"                                                                      # S-10 — CI `check` job 전건
  - "./gradlew --no-daemon qualityBaseline"                                                            # S-11 — 같은 job 의 측정 step
  - "./gradlew --no-daemon :adapters:test --tests '*JdbcOperatorProfileRepositoryTest*' --rerun-tasks" # S-50 — 왕복·미설정·미선언 구분
  - "./gradlew --no-daemon :adapters:test --tests '*ProfileAdapterDependencyTest*' --rerun-tasks"      # S-51 — 신설 패키지 의존 게이트
  - "./gradlew --no-daemon :adapters:test --tests '*ProfileGateRegistrationTest*' --rerun-tasks"       # S-52 — 등재 완결성
  - "./tools/one-command-check.sh"                                                                     # S-20 승계(6C)
rollback: |
  `rollback.md` 첫머리에 **`실측 HEAD: <sha>`** 한 줄을 둔다(2026-09-18 하네스 신규 규율, `evidence-pack`).
  앞 라운드의 실측을 옮기지 않는다 — **base 가 움직이면 목록도 그 새 base 에서 재산출한다.**
  목록은 손으로 적지 않고 `git diff --name-status ede5d5b..HEAD -- . ':!reports/evidence'` 로
  **라운드의 마지막 내용 커밋 뒤에** 재산출한다. 신규는 삭제, 편집은 base 로
  `git restore --source=ede5d5b --staged --worktree -- <재산출한 경로 개별 인자>`.
  공유 파일 셋(`Sql.kt`·`gate-tests.properties`·`milestone-6.md`)과 게이트 test 넷은 커밋 해시 hunk 격리
  (`git diff <sha>~1..<sha> -- <파일> | git apply -R`) — `--3way` 도 자동 해소에 실패하므로 수동 절차를 미리 적는다.
  **마이그레이션 비대칭 없음**(D-6F6-8) — 적용된 DB 인스턴스가 0 이라 V12 는 파일을 지우면 완전히 되돌아간다.
  임시 clone(`git clone --no-hardlinks` 또는 `git worktree add --detach`, **`cp -r` 금지**)에서 ①~⑥ 실측.
  갈음은 「HEAD 초록」이 아니라 **트리 동일성**으로만, 확인은 「내 줄 사라짐」과 「남의 줄 남음」 둘 다.
```

작성: 2026-09-18, 세션 모델 단독. 근거: **운영자 결정 ②(2026-09-18, `milestone-6.md` 「운영자 결정 셋」 문단,
PR #37)** · 배선 재고 `_workspace/m6-wiring/01_ports.md` 축 1·3 · `OPEN-4B6-PROFILE-SOURCE`(M4 이월).
레인 점유는 병행 세션에 확인했다 — 6F-4 는 그 세션, **6F-5·6F-6 은 비어 있음**(2026-09-18 회신).

## 왜 이것인가

운영자 결정 ②가 이 축을 열었고, **6F-5 의 절반을 같이 푼다** — `OperatorLicenses` 를 두 slice 가 공유하는데
그 값의 저장소가 저장소 전체에 없다. `OpportunityAnalysis` 가 요구하는 `OperatorProfilePort` 는 port KDoc 이
「실 구현은 M6」이라 적은 채 비어 있고, 그 공백이 M4 부터 `OPEN-4B6-PROFILE-SOURCE` 로 이월돼 왔다.

## 이 slice 가 하는 일

① **프로필 표**(V12) — 운영자 하나의 업종·면허·지역 어휘. ② **`JdbcOperatorProfileRepository`** — `current()` 는
행이 없으면 `null`(「미설정」), 있으면 도메인 타입으로 복원한다. 저장은 `ProfileFacts` 를 행으로 옮긴다.
③ **「미선언」과 「빈 목록」을 섞지 않는다** — `OperatorLicenses` 는 이미 `Declared(licenseNames)`/`NotDeclared`
sealed 라 도메인이 그 구분을 갖고 있다. 열이 그것을 잃으면 판정이 `UncertainReason.OperatorLicensesNotDeclared`
대신 「면허 0개 보유」로 바뀐다 — 다른 판정이다.

## 계약 고정 결정

| ID | 결정 | 근거 |
| --- | --- | --- |
| **D-6F6-1** | 이 slice 는 **표 + 어댑터(읽기·저장)까지**다. 결정 ② 의 「편집 endpoint」는 **6A** 가 받는다 | `6A-1` 착수 계약이 `BidVectorApplication.kt`·`app/build.gradle.kts`·`PersistenceWiring.kt` 를 이미 in_scope 로 선점했다 — 두 레인이 같은 파일을 만들면 병합이 아니라 충돌이다(M4/4C-2 가 겪은 자리). **저장까지 내는 이유**: 쓰기 경로가 없으면 이 표의 유일한 writer 가 임시 SQL 이 되고, 6A 는 그것을 새로 발명해야 한다. 어댑터가 양방향을 내면 6A 는 HTTP 만 얹는다 |
| **D-6F6-2** | 어댑터는 **도메인 타입을 직접 만들지 않는다** — `ProfileFacts`·`OperatorLicenses` 의 기존 public 생성 경로만 쓴다. port 시그니처·가시성 **무편집** | 6F-1 D-6F1-2·6F-2 D-6F2-6 과 같은 규율. 전략과 달리 여기는 검증 함수가 없지만 `OperatorLicenses` 가 **sealed 로 「미선언」을 이미 닫아** 두어 어댑터가 새로 지어낼 상태가 없다 |
| **D-6F6-3** | 프로필은 **싱글턴**(id 고정)이다 — 6F-1 전략 표와 같은 관례 | 운영자 결정 2026-09-16 ②(단일 운영자 토큰)와 같은 전제이고 legacy `company_profiles` 도 single-operator 였다. 다중 운영자는 소비자가 늘 때(`OPEN-6A-RBAC` 와 같은 자리) |
| **D-6F6-4** | `businessTypes` 는 port 선언대로 **`bidvector.strategy.CategoryCode`** 를 쓴다. 그 타입에는 **정규화가 없다**(`bidvector.procurement.CategoryCode` 는 `of()` 로 정규화한다 — **다른 타입이다**). 이 slice 는 그 타입을 **열지 않고** 공백을 등재한다 | `strategy/Text.kt` 가 `CategoryCode` 와 `KeywordScopeText` 를 함께 담고 있고 **6F-4 가 그 파일을 in_scope 로 잡고 있다** — 같은 파일을 두 레인이 만지지 않는다. 공고 공종(정규화됨)과 프로필 업종(비정규화)이 문자열로 어긋날 수 있다는 사실은 **`OPEN-6F6-CATEGORY-CODE-NORMALIZATION`** 으로 등재하고, 현 거동을 test 로 고정한다(고치지 않고 **보이게** 한다) |
| **D-6F6-5** | **`WorkloadPort` 를 구현하지 않는다** — `DerivationAbsence.WorkloadNotCollected` 유지가 이 slice 의 명시 결정이다 | 운영자 결정 ②(2026-09-18). legacy 도 파생하지 않았고(요청이 싣고 `"auto"` 는 감점 배율만 바꾼다) V2 에 집계 원천이 없다. 파생은 **`OPEN-6F3-BID-RECORD`** 축(판정·투찰 기록 표)이 서는 시점 |
| **D-6F6-6** | 패키지 `bidvector.adapters.profile` 신설 + **바이트코드 상수 풀** 의존 게이트 + `gate-tests.properties` 등재 + **등재 완결성 게이트**를 같은 커밋 범위에서 낸다 | `adapters.persistence` 는 `bidvector.workflow` 참조가 금지돼 있다(M3/3D) — 6B-1·6F-1·6F-2 가 같은 벽을 만나 각각 패키지를 갈랐다. **신설 게이트는 등재까지가 한 벌**이다(6F-2 verifier r1 HIGH-2 실측: 등재 밖 게이트는 파일째 지워도 초록이었다). 소스 텍스트 정규식 형태는 쓰지 않는다 |
| **D-6F6-7** | 마이그레이션 번호는 **V12** | main 은 V9 까지 · **V10 은 6A-1 계약이 선점** · **V11 은 6F-4 가 선점**(그 레인 D-6F4-7, 실측) |
| **D-6F6-8** | **되돌리기 어려운 경로가 아니다** → Codex 심판 대상이 아니다. `migration-reviewer` 와 **`privacy-gate`** 는 붙인다 | 6F-4 가 실측한 사실을 그대로 받는다 — 이 프로젝트의 **적용된 DB 인스턴스가 0** 이라(볼륨·컨테이너 없음, 실 JDBC URL 없음; 스키마는 일회성 Testcontainers 와 CI compose 에만 선다) V12 는 파일을 지우면 완전히 되돌아간다. `privacy-gate` 를 붙이는 이유는 **새 저장소가 생기는 자리**여서다 — 「개인정보 필드가 구조적으로 못 들어온다」를 저자가 아닌 쪽이 확인해야 한다 |

## 위협 모델 — 6F-6 고유 경계

**방어한다**: ① 「프로필 미설정」과 「빈 프로필」이 섞이는 것 — 특히 `NotDeclared` 가 「면허 0개 보유」로
납작해지는 것(판정이 `Uncertain` 에서 `Ineligible` 로 바뀐다) ② 저장된 값이 도메인 생성 경로를 우회해
`ProfileFacts` 가 되는 것 ③ **개인정보 열이 이 표에 생기는 것** — `ProfileFacts` 에 필드가 없어 컴파일이
막지만, 표는 코드보다 넓어 열을 더할 수 있다.

**방어하지 않는다**: ① **누가 프로필을 바꾸는가** — 인가는 6A 다. 이 어댑터를 조립하는 주체는 이미 DataSource 를
쥔 주체다 ② 프로필 **값의 사실성**(운영자가 선언하는 값이다 — 면허 보유 여부를 이 시스템이 검증하지 않는다)
③ 업무량 파생(D-6F6-5) ④ 업종 코드 정규화 불일치(D-6F6-4 — **등재하고 보이게 하되 이 slice 가 닫지 않는다**)
⑤ **보존·파기**(D-6F6-11, `OPEN-6F6-PROFILE-RETENTION` — 6B-3 축. 이 표에 수명 정책이 없다. GRANT 가
`DELETE`·`TRUNCATE` 를 빼 최소권한은 지키지만 「언제 지우는가」는 정해지지 않았다) ⑥ **프로필 어휘의 재식별
가능성**(D-6F6-12 — 범주 어휘라 식별성이 낮으나 희소 조합 + 세부 지역명의 경우를 코드로 배제할 수 없다).

이 경계는 승인 문면을 줄이지 않는다: 결정 ② 의 문면은 「프로필 표 + 편집 endpoint」이고 endpoint 는
**폐기가 아니라 6A 로 이관**이다(D-6F6-1). 이관 사실을 `milestone-6.md` 6F-6 문단에 적는다.

## 값 획득 축 — 새 public 표면 전수 (2b)

| 새 표면 | 밖에 허락하는 것 | 판정 |
| --- | --- | --- |
| `JdbcOperatorProfileRepository`(클래스 + 생성자 `DataSource`) | 프로필을 읽는다 | **경계로 처리** — `OperatorProfilePort` 가 `fun interface`(public)라 아무 모듈이나 이미 임의 `ProfileFacts` 를 내는 구현을 지을 수 있다. **실측 항목**: 그 사실을 컴파일로 확인해 남긴다 |
| 같은 클래스의 **저장** 진입점 | 프로필을 **덮어쓴다** | **경계로 처리** — DataSource 를 쥔 주체는 이미 이 표에 쓸 수 있다(SQL). 새 권한이 아니다. 다만 **「누가 쓰는가」를 좁히는 것은 6A 의 인가**이고, 그 사실을 위협 모델에 적는다. **실측 항목**: 이 진입점이 없을 때와 있을 때 밖이 할 수 있는 일이 같은지(임의 모듈이 DataSource 없이 이 저장을 부를 수 있는가) |
| `OperatorProfileRow` | — | **닫는다** — `internal`(모듈 범위). public 으로 새면 행 표현이 계약이 된다 |

**수정 라운드마다 이 표를 갱신한다** — finding 을 닫는 커밋이 새 public 시그니처를 만들면 행을 더하고 판정을
적는다(M4/4B-2 계보: low 를 닫은 커밋이 high 를 낳았다).

## 우회 경로와 처분 (≥5)

| # | 우회 | 처분 |
| --- | --- | --- |
| 1 | 「미선언」을 빈 목록으로 저장·복원해 판정을 바꾼다 | sealed 두 갈래를 열로 구분하고 **왕복 test 가 세 상태**(미설정 · `NotDeclared` · `Declared(빈 목록)`)를 각각 잰다. 셋이 구분되지 않으면 붉어진다 |
| 2 | 표에 개인정보 열을 더한다 | `privacy-gate`(D-6F6-8) + `ProfileFacts` 에 필드가 없어 **어댑터가 그 값을 실을 곳이 없다**(컴파일). 열만 더해도 게이트가 본다 |
| 3 | 어댑터가 두 번째 복원 구현을 만든다 | D-6F6-2 — 도메인 public 생성 경로만 쓴다. 의존 게이트·리뷰가 본다 |
| 4 | 신설 게이트를 등재하지 않아 지워도 초록 | D-6F6-6 — `gate-tests.properties` 등재 + **완결성 게이트**(6F-2 선례, 그 게이트 자신도 등재한다) |
| 5 | `adapters.profile` 에서 금지 루트를 **import 없이 전체 한정 좌표**로 참조한다 | 바이트코드 상수 풀 게이트(소스 텍스트 정규식은 이 우회를 못 본다 — 6F-2 verifier MUT-E3 실측) |
| 6 | 정규화 없는 업종 코드로 공고 공종과 조용히 어긋난다 | **닫지 않는다 — 보이게 한다.** `OPEN-6F6-CATEGORY-CODE-NORMALIZATION` 등재 + 현 거동을 test 로 고정(`strategy/Text.kt` 는 6F-4 소관이라 열지 않는다) |
| 7 | 싱글턴 가정을 깨고 두 행을 넣는다 | 표 제약(PK 고정값 CHECK)으로 닫고 test 로 잰다 |

## 계약 갱신 (1) — 판정 레인 넷의 결과 (2026-09-18, 팀장)

verifier `not-ready`(BLOCKER 1 · HIGH 1 · LOW 1) · code-reviewer **지적 0** · privacy-gate 통과(수정 필요 2 ·
확인 불가 1) · migration-reviewer 통과(권고 1). 판정 대상은 `8376dd4d`.

| ID | 결정 | 근거 |
| --- | --- | --- |
| **D-6F6-9**(BLOCKER-1) | evidence 의 비밀값 스캔 어휘를 **참조형으로 다시 쓴다**. `leak-pattern-baseline.txt` 등재로 넘기지 않는다 — 규율은 「등재」가 아니라 **「적지 않는다」**이다. 그리고 **acceptance 재실측은 evidence 커밋 **뒤** HEAD 에서 한다** | 판정 대상 HEAD 에서 `:leakPatternGate` 가 `commands.md` 의 두 토큰을 신규 매치로 잡아 **`check` 와 `one-command-check.sh` 가 붉다**(verifier 2회 재현). 구현 레인은 evidence 커밋 **이전** 트리(`fcd111b0`)에서 재고 초록으로 보고했다 — 그 커밋이 게이트를 깨뜨렸다. **게이트 대상 디렉터리에 evidence 가 들어 있다**는 사실이 이 실패의 전부다: 같은 절이 「실 게이트는 이 파일을 보지 않는다」고 적었으나 게이트의 `scanRoot` 가 `reports/evidence` 이고 제외는 `scope.md` 하나뿐이다(그 서술도 함께 정정한다) |
| **D-6F6-10**(HIGH-1) | 정규화 회귀 test 는 **같은-생성자 재계산 비교를 쓰지 않는다**. 복원 집합의 **원소 수**와 **원문 리터럴 보존**을 직접 잰다 | 단언 양변이 같은 `CategoryCode(...)` 생성자를 지나 **생성자 안 멱등 정규화가 양변을 함께 접는다**. 실측: `strategy.CategoryCode`(이 OPEN 이 지목한 바로 그 타입)에 trim+lowercase 를 넣으면 집합이 2→1 로 접히고 test 주석의 주장이 거짓이 되는데 **`check` 는 exit 0**. 어댑터층 정규화(`toRow` 의 trim)만 붉어진다 — **OPEN 이 이름 붙인 도메인 공백을 잠그지 않는다.** M6/6F-2 HIGH-1(함수와 그 함수 본문 재계산값의 비교 = 거의 항진명제)과 **같은 결함 클래스 두 번째**다. 닫힘 판정은 그 변이(도메인 타입 정규화 도입)가 **붉어지는 것으로만** 한다 |
| **D-6F6-11**(privacy-gate 수정 필요 ①) | 위협 모델 「방어하지 않는다」에 **보존·파기**를 등재하고 `OPEN-6F6-PROFILE-RETENTION` 을 신설해 **6B-3**(보존·파기 축)으로 인계한다 | **팀장 누락이다.** 선행 slice 6F-1 은 같은 자리에 「전략 이력의 보존·파기」를 명시 등재했는데 이 계약이 그 관례를 따르지 않았다. 마이그레이션 SQL 주석에만 있고 위협 모델·OPEN 표 어디에도 없었다 — 실 노출 경로는 아니지만(GRANT 가 `SELECT/INSERT/UPDATE` 만 주고 `DELETE/TRUNCATE` 를 뺐다) **문서가 공백을 가리키지 않은 것은 사실**이다 |
| **D-6F6-12**(privacy-gate 확인 불가) | 프로필 어휘의 **재식별 가능성**을 알려진 제한으로 등재한다 — 면허 종류명·업종 분류명·광역 지명은 범주 어휘라 식별성이 낮으나, **1인 개인사업자가 드문 조합에 세부 지역명을 함께 쓰는 경우**를 코드만으로 배제할 근거가 없다 | 게이트가 임의로 통과·위반으로 확정하지 않고 남긴 판정이다. 통과로 삼키지 않는다 — 개인정보 판단은 운영자 결정 축이고, 세부 지역 어휘가 실제로 들어오는 시점(6A 편집 endpoint)이 그 결정 자리다 |
| **D-6F6-13**(migration-reviewer 권고) | **병합 순서는 V11(6F-4) 뒤**다. V10(6A-1)은 **파일이 없으므로 기다리지 않는다** — 그 번호는 파일이 생기는 시점에 다시 잡는다 | Flyway 기본 정책이 `outOfOrder=false` 이고 이 저장소에 그 설정이 없다. V12 가 먼저 적용된 인스턴스에 나중에 V11 을 적용하면 **거부된다**. 오늘은 적용 인스턴스 0 이라 현실화되지 않지만(migration-reviewer 가 production Flyway 호출 0건·CI `down -v`로 직접 재확인), 순서를 지키는 비용이 0 이므로 지킨다. 이는 6F-6 의 결함이 아니라 **다중 레인 Flyway 번호 관례**의 구조적 위험이다 |

**범위 밖 기존 부채(이 slice 의 finding 이 아니다 — 등재만)**: `*GateRegistrationTest` 셋(`Profile`·`Evaluation`·`Event`)이
`gate-tests.properties` 를 런타임 `File(...)` 로 읽어 **`:adapters:test` 의 선언된 입력이 아니다.** 등재 행만 지우면
증분 빌드에서 test 가 `UP-TO-DATE` 로 건너뛰어 **exit 0** 이 되고, `--rerun-tasks` 를 붙여야 붉어진다. 술어는 옳고
구멍은 **입력 미선언 stale** 이다. CI 는 clean checkout + 표적 명령이 `--rerun-tasks` 라 영향이 없다.
`OPEN-GATE-REGISTRATION-STALE-INPUT` 으로 등재한다 — 받는 쪽은 하네스 레인이다.

## 하네스 레인 변경 (상시 절)

이 slice 의 `base..HEAD` 에는 다른 레인 커밋이 섞일 수 있다. slice 의 커밋 집합은 range 가 아니라
**in_scope 경로의 변경**이다. 리뷰 요청 시점마다 이 절을 갱신한다.

- (착수 시점) 없음.
- **라운드 2** — 팀장 커밋 `55006bfb`(계약 갱신 (1), in_scope `scope.md` 편집) 와 그 뒤의 OPEN 표·이 절 갱신
  커밋. 하네스(`​.claude/`·`CLAUDE.md`) 변경은 없다 — 이 slice 의 range 에 들어온 팀장 커밋은 **모두
  in_scope 문서**(`scope.md`·`milestone-6.md`)다.

## 병행 레인

| 레인 | 상태 | 겹침 |
| --- | --- | --- |
| `m6-6f4/2026-09-18`(다른 세션) | 진행 중 | **마이그레이션 번호**(V11 ↔ V12, 겹치지 않는다) · `strategy/Text.kt`(그 레인 소유 — 이 slice **무편집**) · `Sql.kt`·`gate-tests.properties`·`CleanMigration*Test`·`milestone-6.md`(**둘 다 추가만**, 병합 시 hunk 충돌 가능) |
| `m6-6a/2026-09-17`(6A-1, 계약만 고정·정지) | 대기 | V10 선점 · app 파일 넷(이 slice 무편집) · 편집 endpoint 를 이 slice 가 넘긴다(D-6F6-1) |
| `harness/rollback-list-gate/2026-09-18`(다른 세션) | 진행 중 | 병합되면 이 slice 의 `rollback.md` 도 그 대조를 받는다 |

## OPEN 항목

| ID | 처분 |
| --- | --- |
| `OPEN-4B6-PROFILE-SOURCE`(M4 이월) | **이 slice 가 닫는다** — 저장·조회 경로가 선다. 편집 **경로**(HTTP)는 6A 로 이관(D-6F6-1) |
| `OPEN-6F6-CATEGORY-CODE-NORMALIZATION`(신설) | 프로필 업종 코드에 정규화가 없어 공고 공종과 어긋날 수 있다 — 등재만(D-6F6-4). 받는 쪽은 `strategy` 타입을 여는 slice |
| `OPEN-6F3-BID-RECORD` | 변경 없음 — `WorkloadPort` 파생의 전제(D-6F6-5) |
| `OPEN-6F-ASSEMBLY` | 변경 없음 — 어댑터를 만들 뿐 꽂지 않는다 |
| `OPEN-6F6-PROFILE-RETENTION`(신설, D-6F6-11) | 이 표의 수명 정책이 없다 — 「언제 지우는가」가 정해지지 않았다(GRANT 가 `DELETE`·`TRUNCATE` 를 빼 최소권한은 지킨다). **받는 쪽은 6B-3**(데이터 수명·마스킹, 보존 기간 결정 선행) |
| `OPEN-GATE-REGISTRATION-STALE-INPUT`(신설, 범위 밖 부채) | 등재 완결성 게이트 셋(`Profile`·`Evaluation`·`Event`)이 `gate-tests.properties` 를 런타임 `File(...)` 로 읽어 **`:adapters:test` 의 선언된 입력이 아니다** — 증분 빌드에서 등재 행만 지우면 `UP-TO-DATE` 로 건너뛰어 초록이 된다(`--rerun-tasks` 면 붉다). 술어는 옳고 CI 는 clean checkout 이라 영향 없다. **받는 쪽은 하네스 레인** |

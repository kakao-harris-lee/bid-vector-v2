# M6/6F-2 — 후보 원천(`CandidateSourcePort`) + trace 축 (2026-09-18)

```yaml
milestone: M6
slice: 6f2-candidate-source-trace
base_sha: 547fd7b   # main == origin/main. 6F-1(PR #34)·6B-1(PR #33)·M4 종결(PR #35) 병합 뒤
head_sha: 리뷰 요청 시점의 `git rev-parse HEAD`(값을 박지 않는다)
in_scope:
  - adapters/src/main/kotlin/bidvector/adapters/evaluation/JdbcCandidateSource.kt        # `CandidateSourcePort` 첫 production 구현 — 다건 스캔
  - adapters/src/main/kotlin/bidvector/adapters/evaluation/UuidCorrelationIdFactory.kt   # `CorrelationIdFactory` 구현(`JdbcEventIdFactory` 관례 복제)
  - adapters/src/main/kotlin/bidvector/adapters/strategy/SystemClock.kt                  # `Clock` 구현 — port 타입이 사는 루트의 패키지(D-6F2-8)
  - adapters/src/main/kotlin/bidvector/adapters/persistence/Sql.kt                       # 후보 스캔 SQL 추가만(기존 문장 무편집)
  - adapters/src/test/kotlin/bidvector/adapters/evaluation/EvaluationAdapterDependencyTest.kt  # 신설 패키지 전용 의존 게이트 — 바이트코드 상수 풀 형태(D-6F2-1)
  - adapters/src/test/kotlin/bidvector/adapters/evaluation/JdbcCandidateSourceTest.kt    # 상태 집합·마감 경계·순서·상한 초과 실패·복원 왕복
  - adapters/src/test/kotlin/bidvector/adapters/evaluation/CandidateStatusSetTest.kt     # 집합 등식 게이트(D-6F2-2) — SQL 이 쓰는 상태 집합 == 도메인 술어가 참인 집합
  - adapters/src/test/kotlin/bidvector/adapters/strategy/SystemClockTest.kt              # 단조 진행·`Instant.now()` 위임
  - adapters/src/test/kotlin/bidvector/adapters/persistence/PersistenceTestSupport.kt    # 필요 시 정리 목록 조정만(기계적, 신설 표 없음)
  - reports/evidence/m6/6f2/**
  - milestone-6.md                                                                       # 6F-2 착수 문단(팀장 커밋)
out_of_scope:
  - 마이그레이션·스키마 변경                  # **이 slice 는 표를 만들지 않는다** — `notice` 표(V1)의 기존 행을 읽기만 한다. V10 은 6A-1 이 잡고 있다(6F-1 scope.md 「병행 레인」)
  - 인덱스 추가                               # D-6F2-7 — `OPEN-6B1-INDEX-GAPS` 에 이 질의의 축으로 등재만. 추가는 마이그레이션이라 위 항목과 같은 자리
  - 나머지 포트 여섯                          # 6F-3(여력, 결정 선행)·6F-4(감시 대상, 결정 선행)·6F-5(면허)·6F-6(프로필·업무량)·6F-7(알림 → outbox)
  - app DI 조립·진입점                        # `OPEN-6F-ASSEMBLY` — 이 slice 는 어댑터를 만들 뿐 꽂지 않는다. 조립은 6A-1 또는 전용 slice
  - `CandidateSourcePort`·`Clock`·`CorrelationIdFactory` 시그니처 변경  # **필요 없다**(D-6F2-6) — 세 port 모두 기존 공개 타입만 주고받는다
  - 후보 순회 뒤 단계                         # 감시·면허·ML·판정은 M4 가 세웠고 이 slice 가 열지 않는다
  - HTTP 노출·인가                            # 6A
acceptance_commands:
  - "./gradlew --no-daemon check"                                                                    # S-10 — CI `check` job 전건(evidence 커밋마다 그 HEAD 에서, 마지막은 보고·PR 코멘트가 정본)
  - "./gradlew --no-daemon qualityBaseline"                                                          # S-11 — CI `check` job 의 측정 step(실패시키지 않는 측정)
  - "./gradlew --no-daemon :adapters:test --tests '*JdbcCandidateSourceTest*' --rerun-tasks"         # S-50 — 상태 집합·마감·순서·상한(캐시 우회)
  - "./gradlew --no-daemon :adapters:test --tests '*CandidateStatusSetTest*' --rerun-tasks"          # S-51 — 집합 등식 게이트(D-6F2-2)
  - "./gradlew --no-daemon :adapters:test --tests '*EvaluationAdapterDependencyTest*' --rerun-tasks" # S-52 — 신설 패키지 의존 게이트
  - "./tools/one-command-check.sh"                                                                   # S-20 승계(6C)
rollback: |
  신규 파일 삭제 + 편집을 base(`547fd7b`)로. 목록은 손으로 적지 않고
  `git diff --name-status 547fd7b..HEAD -- . ':!reports/evidence'` 로 **라운드의 마지막 내용 커밋 뒤에**
  재산출한다(6F-1 verifier r2 HIGH-1 의 교훈 — 낡은 목록이 남의 레인 파일을 지운다).
  `git restore --source=547fd7b --staged --worktree -- <재산출한 경로 개별 인자>`, 신규는 삭제.
  **마이그레이션 비대칭 없음** — 이 slice 는 스키마를 만들지 않는다(읽기 전용 질의).
  공유 파일은 `Sql.kt`·`PersistenceTestSupport.kt`·`milestone-6.md` 셋뿐이고 모두 **추가만**이라
  커밋 해시 hunk 격리(`git diff <sha>~1..<sha> -- <파일> | git apply -R`)로 되돌린다.
  임시 clone 에서 ①~⑥ 실측(명령 exit · D/M 수 · diff 빈 것 · compile · test · 게이트).
  갈음은 「HEAD 초록」이 아니라 **트리 동일성**으로만. 정본 `reports/evidence/m6/6f2/rollback.md`.
```

작성: 2026-09-18, 세션 모델 단독. 근거: **운영자 지시 2026-09-17 「어댑터 부재는 중요한 결함이고 배선이 먼저」** ·
`milestone-6.md` 6F 군 표(6F-2 = 후보 원천 + trace 축, 선행 6F-1, 「질의만 신설」) · 배선 재고
`_workspace/m6-wiring/01_ports.md` 축 1·2 · `EvaluateCandidatesUseCase.evaluate()` 둘째 줄.

## 왜 이것이 다음인가

6F-1 이 `evaluate()` 의 **첫 줄**(`strategies.load()`)을 세웠다. **둘째 줄이 `candidateSource.openCandidates()`**
이고, 그 자리는 지금 test fake 뿐이다 — 전략이 있어도 후보가 0 이면 파이프라인은 여전히 빈 목록을 돈다.
6F 군에서 **설계 미결이 없는 남은 축은 이것 하나**다(6F-3·6F-4·6F-6 은 운영자 결정 대기, 6F-5 는 요구사항
영속 설계 선행, 6F-7 은 타입 체계 연결). `notice` 표(V1)에 데이터가 이미 있고 없는 것은 **다건 스캔 질의**뿐이다
(`NoticeRepository` 는 `find(id)`·`persist` 만 내고, 그 port KDoc 이 이 경계를 직접 적어 뒀다).

trace 축(`CorrelationIdFactory`·`Clock`)을 같이 싣는 이유: 둘 다 구현이 한 줄이고(`JdbcEventIdFactory` 가
이미 같은 형태), **후보 스캔이 `Clock` 을 실제로 요구한다**(마감 경계 비교의 `now`). 축이 갈리면 같은 시계가
두 번 조립되는 자리를 만든다.

## 이 slice 가 하는 일

① **후보 스캔 질의** — `notice` 표에서 「지금 입찰 가능한」 행을 읽어 `Notice` 로 복원한다.
② **정의를 SQL 에 두지 않는다** — 「입찰 가능」의 상태 집합을 **도메인 술어에서 기계 산출**해 배열로 바인딩한다.
③ **절삭을 숨기지 않는다** — 상한은 조용한 `LIMIT` 이 아니라 초과 시 큰 실패다.
④ **trace 축 둘** — `CorrelationIdFactory`(UUIDv4)·`Clock`(`Instant.now()`).

## 계약 고정 결정

| ID | 결정 | 근거 |
| --- | --- | --- |
| **D-6F2-1** | 어댑터 패키지는 **`bidvector.adapters.evaluation`** 신설. 전용 의존 게이트 test 를 **바이트코드 상수 풀 형태**(`StrategyAdapterDependencyTest` 와 같은 형태)로 함께 낸다. 허용 루트는 `workflow.evaluation`(port)·`workflow.event`(`CorrelationId`)·`workflow.strategy`(`Clock`)·`procurement`(`Notice`)·`sharedkernel`·`adapters.persistence`(SQL·행 복원)·자기 패키지 | `PersistenceAdapterDependencyTest`(M3/3D)가 `adapters.persistence` 의 `bidvector.workflow` 참조를 금지한다 — 6B-1·6F-1 이 같은 벽을 만나 각각 패키지를 갈랐다. 소스 텍스트 import 정규식 형태(`EventAdapterDependencyTest`)는 **전체 한정 좌표 직접 참조를 못 본다**(6F-1 verifier MUT-E3 실측) — 신설 게이트는 구조로 닫는 쪽만 쓴다(CLAUDE.md 「게이트 술어는 문자열이 아니라 구조로」) |
| **D-6F2-2** | 「입찰 가능」 **상태 집합을 SQL 리터럴로 적지 않는다** — `NoticeStatus` 전 값을 도메인 술어 `isBiddable` 로 걸러 얻은 집합을 배열 파라미터로 바인딩한다. 게이트는 **집합 등식**(SQL 에 실제로 실린 집합 == 술어가 참인 집합)이고 하한 단언(`>= n`)을 쓰지 않는다 | `isBiddable` KDoc 이 그 존재 이유를 직접 적는다 — 「호출부가 상태 리터럴을 직접 고르는 경로를 만들지 않는다(legacy 의 단수 `"open"` 리터럴 잠재 버그와 같은 실패 모양)」. SQL 에 `IN ('Open','Renoticed')` 을 적으면 **정의가 두 벌**이 되고 둘째 벌은 컴파일러가 보지 않는다. 기계 산출이면 `NoticeStatus` 에 값이 늘거나 술어가 바뀔 때 질의가 **따라 움직인다**(M5/5E-3 「수집 집합 == 열거 집합」 규율) |
| **D-6F2-3** | 마감 비교는 **반개구간** — `deadline_at > :now`(같으면 이미 마감), `deadline_at IS NULL` 은 후보가 아니다. `:now` 는 **주입된 `Clock`** 에서 온다(DB 의 `now()` 를 쓰지 않는다) | 도메인 술어와 같은 방향이어야 한다(§1.5 반개구간, `isBiddable` 의 `now.isBefore(deadline)`). `EvaluateCandidatesUseCase.lifecycleDrop` 도 `deadline == null` 을 `NoticeNotBiddable` 로 떨어뜨린다. DB 시계를 쓰면 시간의 출처가 둘이 된다 |
| **D-6F2-4** | 상한은 **조용한 절삭이 아니라 큰 실패**다 — 상한 `cap` 은 조립이 주입하고(기본값 없음), 질의는 `cap + 1` 을 읽어 초과가 보이면 전용 예외로 실패한다 | port 반환 타입이 `List<Notice>` 라 **「잘렸다」를 실을 자리가 없다.** 조용한 `LIMIT` 은 legacy 실패 형태 ⑥(평가하지 않은 후보가 흔적 없이 사라진다)을 그대로 재현한다 — M4 가 `analysisBudget` 을 **결과에 남기는** 방식으로 뒤집은 바로 그 축이다. 사유를 담은 실패는 6F-1 D-6F1-3(「지어내지 않고 실패」)과 같은 규율 |
| **D-6F2-5** | 순서는 **결정적**이다 — `deadline_at ASC, notice_number ASC, notice_round ASC` | `analysisBudget` 이 「`openCandidates` 가 낸 목록 순서 그대로」 앞에서부터 예산을 쓴다(use case KDoc). 순서가 비결정적이면 **예산에 잘리는 후보가 실행마다 달라진다** — 같은 입력·정책·모델 version 의 결과 재현(M6 완료 조건 6)이 이 자리에서 깨진다. 마감 임박 순은 그 예산이 가장 급한 후보에 쓰이게 한다 |
| **D-6F2-6** | 어댑터는 **도메인 타입을 직접 만들지 않는다** — 기존 복원 경로(`NoticeId.reconstructNotice`, `adapters.persistence` 의 `internal`)를 그대로 쓴다. port 시그니처·가시성 **무편집** | 6F-1 D-6F1-2 와 같은 규율. 그 복원 경로는 `Notice.collected` + `applyEvent` 라는 **공개 문**만 쓰도록 이미 벼려져 있고(3D·verifier r1 F-8·r2 N-5 의 exhaustive `when`), 어댑터가 두 번째 복원 구현을 만들면 그 성과가 갈린다 |
| **D-6F2-7** | **인덱스를 추가하지 않는다** — 질의 형태(상태 집합 + `deadline_at` 범위 + 그 순서)를 `OPEN-6B1-INDEX-GAPS` 에 등재만 한다 | 6B-1 D-6B1-5 는 「소비 질의를 가진 slice 가 **근거와 함께**」 추가하라고 했다. 질의는 이제 있지만 **규모 근거가 없다**(운영 데이터 0). 게다가 인덱스는 마이그레이션이고 V10 은 6A-1 이 잡고 있어 번호 축이 겹친다. 부분 인덱스로 만들면 그 `WHERE` 술어가 **D-6F2-2 가 막 닫은 상태 리터럴을 DDL 에 되살린다** — 추가하더라도 전체 인덱스여야 한다는 판정까지만 이 slice 가 남긴다 |
| **D-6F2-8** | `SystemClock` 은 **`bidvector.adapters.strategy`** 에 둔다(`Clock` 타입이 사는 루트). `UuidCorrelationIdFactory` 는 `adapters.evaluation`(port 가 `workflow.evaluation` 에 선언돼 있다) | 두 패키지 모두 해당 루트를 이미 허용한다 — 게이트 allow-list 를 넓히지 않고 각 구현을 그 port 를 이미 보는 패키지에 둔다. `adapters.evaluation` 은 `Clock` 을 **주입 파라미터 타입으로만** 참조한다(D-6F2-3) |

## 위협 모델 — 6F-2 고유 경계

**방어한다**: ① 승인되지 않은 행이 후보로 들어오는 것(상태·마감 정의가 SQL 에서 도메인과 갈라지는 경로)
② 열린 후보가 **조용히 빠지는** 것(절삭·비결정 순서) ③ 저장된 행이 도메인 복원 경로를 우회해 `Notice` 가 되는 것.

**방어하지 않는다**: ① `notice` 표 **행 자체의 진실성** — 그것은 M3 수집 축(권위 트리거·append-only·감사)이
이미 지고, 이 어댑터는 그 결과를 읽을 뿐이다 ② 후보 순회 **뒤** 단계의 판정(감시·면허·ML·사다리 — M4)
③ **누가 이 어댑터를 부르는가**(인가는 6A) ④ 조립 주체가 무엇을 주입하는가 — 조립은 `OPEN-6F-ASSEMBLY`
이고, DataSource·Clock·상한을 쥔 주체는 이미 DB 를 쥔 주체다.

이 경계는 승인 문면을 줄이지 않는다: milestone-6 의 6F-2 행은 「질의만 신설(다건 스캔)」이고, 완료 조건 3
(E2E)은 6F 군 전체가 모인 뒤 6D 가 진다.

## 값 획득 축 — 새 public 표면 전수 (2b)

| 새 표면 | 밖에 허락하는 것 | 판정 |
| --- | --- | --- |
| `JdbcCandidateSource`(클래스 + 생성자 `DataSource`·`Clock`·상한) | 임의 DataSource·Clock·상한으로 후보 목록을 낼 수 있다 | **경계로 처리** — `CandidateSourcePort` 가 `fun interface`(public)라 **아무 모듈이나 이미** 임의 목록을 내는 구현을 지을 수 있다. 이 생성자는 없던 권한을 만들지 않는다. **실측 항목**: 구현 레인이 「port 를 람다로 구현해 임의 `Notice` 목록을 내는 표본」이 컴파일됨을 재고 결과를 남긴다(경계 논증이 희망이 아님을 실측으로 확인 — M4/4C-1 교훈) |
| `UuidCorrelationIdFactory` | `CorrelationId` 값을 만들 수 있다 | **닫힌 것이 애초에 없다** — `CorrelationId` 는 public `data class` 이고 누구나 만든다(실측: 선언 확인). trace 값의 무결성은 이 타입이 아니라 이벤트 봉투(4C-1)가 진다 |
| `SystemClock` | 현재 시각을 낸다 | 권한 없음 |
| 상한 초과 예외 타입 | 실패 사유를 읽을 수 있다 | 권한 없음 — 사유를 문자열로 뭉개지 않기 위한 타입 |

**수정 라운드마다 이 표를 갱신한다**(M4/4B-2 계보) — finding 을 닫는 커밋이 **새 public 시그니처를 만들면**
그 행을 여기 추가하고 판정을 적는다. 테스트 가능성을 얻으려 여는 자리가 가장 흔한 경로다.

## 우회 경로와 처분 (≥5)

| # | 우회 | 처분 |
| --- | --- | --- |
| 1 | SQL 에 상태 리터럴을 직접 적어 도메인 술어와 갈라놓는다 | D-6F2-2 — 집합을 술어에서 기계 산출하고 **집합 등식** test 가 잰다. 리터럴을 되살리면 그 test 가 붉어진다 |
| 2 | `NoticeStatus` 에 값이 추가되는데 질의가 따라가지 않는다 | 같은 집합 등식 test — 열거가 늘면 기대 집합도 같이 는다(손 목록이 아니다) |
| 3 | 마감 비교를 닫힌 구간(`>=`)으로 바꾸거나 `NULL` 을 통과시킨다 | 경계 test 둘(마감 == now · `deadline_at IS NULL`) |
| 4 | 상한을 조용한 `LIMIT` 으로 되돌려 절삭을 숨긴다 | D-6F2-4 — 상한 초과 표본이 **실패**함을 재는 test. `LIMIT cap` 으로 되돌리면 그 test 가 붉어진다 |
| 5 | 순서를 빼거나(`ORDER BY` 제거) 비결정으로 만든다 | 같은 마감·다른 식별자 표본으로 순서를 재는 test |
| 6 | `adapters.evaluation` 에서 금지된 도메인 루트를 전체 한정 좌표로 참조한다(import 없이) | D-6F2-1 — 바이트코드 상수 풀 게이트가 **컴파일러가 실제로 만든 참조**를 본다 |
| 7 | 복원 경로를 우회해 어댑터가 `Notice` 를 직접 조립한다 | D-6F2-6 — `Notice` 는 `internal constructor` 이고 `adapters` 는 다른 모듈이라 **컴파일이 거부한다**(구조로 닫힘). 공개 팩토리를 쓰더라도 복원 경로 중복은 의존 게이트·리뷰가 본다 |

## 하네스 레인 변경 (상시 절)

이 slice 의 `base..HEAD` 에는 다른 레인(하네스·병행 slice)의 커밋이 섞일 수 있다. slice 의 커밋 집합은
range 가 아니라 **in_scope 경로의 변경**이다. 리뷰 요청 시점마다 이 절을 갱신한다.

- (착수 시점) 없음.

## 병행 레인

| 레인 | 상태 | 겹침 |
| --- | --- | --- |
| `harness/rollback-list-gate/2026-09-18`(다른 세션) | 진행 중 — `tools/rollback-list-gate.sh`·`reports/evidence/harness/**` | **없음**. 단 그 게이트가 병합되면 이 slice 의 `rollback.md` 목록도 그 대조를 받는다 |
| `m6-6a/2026-09-17`(6A-1, 계약만 고정·구현 미착수) | 대기 | `Sql.kt`(둘 다 **추가만**) · 마이그레이션 번호는 겹치지 않는다(6F-2 는 표를 만들지 않는다) |
| 6B-1·6F-1 | 병합 완료 | `adapters.strategy` 패키지에 `SystemClock` 추가(기존 파일 무편집) |

## OPEN 항목

| ID | 처분 |
| --- | --- |
| `OPEN-6F-ASSEMBLY` | 변경 없음 — 이 slice 는 어댑터를 만들 뿐 꽂지 않는다 |
| `OPEN-6B1-INDEX-GAPS` | **이 질의의 축을 등재**(D-6F2-7) — 상태 집합 + `deadline_at` 범위·정렬. 추가는 규모 근거를 가진 slice |
| `OPEN-6F2-CANDIDATE-BOUND`(신설) | 상한의 **값**과 초과 시 운영 처분(실패로 멈출 것인가, 운영자 알림인가)은 조립·운영 축의 결정이다. 이 slice 는 「조용히 자르지 않는다」만 고정한다 |

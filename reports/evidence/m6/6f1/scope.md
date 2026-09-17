# M6/6F-1 — 전략 영속(`StrategyRepository` 실 구현) (2026-09-17)

```yaml
milestone: M6
slice: 6f1-strategy-persistence
base_sha: c4d09cc   # PR #31(6C) 머지 커밋 = main
head_sha: 리뷰 요청 시점의 `git rev-parse HEAD`(값을 박지 않는다)
in_scope:
  - adapters/src/main/resources/db/migration/V9__operator_strategy.sql   # 전략 영속 표 + 개정 이력. **V8 은 6B-1**(세션), **V10 은 6A-1**(요청 감사) — 「병행 레인」 절
  - adapters/src/main/kotlin/bidvector/adapters/strategy/JdbcStrategyRepository.kt      # 계약 갱신 (2)·D-6F1-7: **패키지는 `adapters.strategy`** — `persistence` 는 workflow·strategy 참조가 게이트로 금지돼 있다(6B-1 실측)
  - adapters/src/main/kotlin/bidvector/adapters/strategy/StrategyRow.kt                 # 행 → **초안** 매핑. 도메인 타입을 직접 만들지 않는다(D-6F1-2)
  - adapters/src/test/kotlin/bidvector/adapters/strategy/StrategyAdapterDependencyTest.kt  # 신설 패키지 전용 의존 게이트 — **6B-1 이 먼저 병합되면 그 파일에 합류**(중복 신설 금지, 계약 갱신 (2))
  - adapters/src/main/kotlin/bidvector/adapters/persistence/Sql.kt                      # 전략 SQL 추가만(기존 문장 무편집)
  - adapters/src/test/kotlin/bidvector/adapters/persistence/JdbcStrategyRepositoryTest.kt   # 왕복·개정 증가·정책 불일치 실패·빈 전략 초기값
  - reports/evidence/m6/6f1/**
  - milestone-6.md                                                                      # 6F slice 군 신설·6F-1 착수 문단(팀장 커밋)
out_of_scope:
  - 다른 포트 여덟                            # 6F-2~6F-7(아래 군 표). 이 slice 는 **evaluate() 의 첫 줄**을 세우는 것까지
  - app DI 조립·진입점                        # `app/src/main` 은 앵커 하나뿐이고 조립은 6A-1(HTTP) 또는 전용 조립 slice — `OPEN-6F-ASSEMBLY`
  - `StrategyRepository` port 시그니처 변경    # **필요 없다**(D-6F1-2) — 검증 함수가 이미 공개된 유일한 문이다. 세션 축(6B-1 D-6B1-7)과 달리 재개방이 필요하지 않다
  - 전략 편집 명령 경로·HTTP                   # 6A-1·6A-2
  - 정책 값 결정                               # 정책 데이터는 기존 승인 값을 읽는다. 새 값·새 키를 만들지 않는다
acceptance_commands:
  - "./gradlew --no-daemon check"                                                                 # S-10 — 전건(evidence 커밋마다 그 HEAD 에서, 마지막은 보고·PR 코멘트가 정본)
  - "./gradlew --no-daemon :adapters:test --tests '*JdbcStrategyRepositoryTest*' --rerun-tasks"   # S-50 — 왕복·개정·정책 불일치(캐시 우회)
  - "./tools/one-command-check.sh"                                                                # S-20 승계(6C)
rollback: |
  신규 파일 삭제 + 편집 둘(`Sql.kt`·`milestone-6.md`)을 base 로.
  `git restore --source=c4d09cc --staged --worktree -- <in_scope 경로 개별 인자>`, 신규는 삭제.
  **마이그레이션 비대칭**: 적용된 DB 에는 V9 가 남고 되돌린 코드는 그 표를 쓰지 않는다. 실제 삭제는 사용자 승인 대상.
  임시 clone ①~⑥ + 빈 컨테이너 재적용으로 실측. 정본 `reports/evidence/m6/6f1/rollback.md`.
```

작성: 2026-09-17, 세션 모델 단독. 근거: **운영자 지시 2026-09-17 「어댑터 부재는 중요한 결함이고 배선이 먼저」** ·
배선 재고 `_workspace/m6-wiring/01_ports.md`(포트 아홉 무구현, 권고 착수 = 전략·세션 영속) · `milestone-6.md`
「Slice 6A」 인계 · M4/4A `StrategyRepository` port 선언과 `EvaluateCandidatesUseCase.evaluate()` 의 첫 줄.

## 왜 이것이 먼저인가

`EvaluateCandidatesUseCase.evaluate()` 의 **첫 줄이 `strategies.load()`** 다 — 이 포트 없이는 어떤 후보도
판정에 들어가지 못한다. 배선 재고가 포트 아홉의 유일한 **진짜 선행 의존**으로 이것을 지목했고, 다른 축과 달리
**설계 미결이 없다**(뒤 slice 들은 원문 텍스트 출처·활성 투찰 정의·프로필 모델 결정을 기다린다).

## 이 slice 가 하는 일

① **전략 영속 표**(V9) — 현 전략 한 벌(감시 규칙·행동 임계·후보 상한·개정 번호)과 개정 이력. 값은 도메인이
정하고 DB 는 담기만 한다.
② **`JdbcStrategyRepository`** — `load()` 는 행을 **초안**으로 읽어 **기존 검증 함수**에 통과시키고 유효할 때만
전략을 낸다. `save(applied)` 는 통로 타입의 필드를 읽어 행으로 쓴다(개정 번호 포함).
③ **정책 불일치를 정직하게** — 저장된 값이 **현 정책으로 유효하지 않으면** 전략을 지어내지 않고 실패한다
(정책이 바뀌었는데 옛 전략이 남은 상태를 조용히 통과시키지 않는다). 이 축을 test 로 고정한다.

## 계약 고정 결정

| ID | 결정 | 근거 |
| --- | --- | --- |
| **D-6F1-1** | milestone-6 에 **6F slice 군**(수집↔판정 배선 어댑터)을 신설하고 포트별로 가른다 — 6F-1 전략 · 6F-2 후보 원천 + trace 축 · 6F-3 여력 · 6F-4 감시 대상 · 6F-5 면허 게이트 · 6F-6 운영자 프로필 + 업무량 · 6F-7 알림 요청 → outbox | milestone-6 의 6A~6E 에 **이 축이 없었다**. M3 가 수집을, M4 가 판정을 세웠으나 그 둘을 잇는 어댑터가 없고, 완료 조건 3(필수 capability E2E)이 여기에 걸려 있다. 포트마다 데이터 소재·외부 계약·미결 결정이 달라 한 slice 로 묶으면 6C 의 3 라운드를 반복한다 |
| **D-6F1-2** | 어댑터는 **도메인 타입을 직접 만들지 않는다** — 행을 **초안**으로 읽어 **기존 공개 검증 함수**에 통과시킨다. port 시그니처·가시성 **무편집**, 새 public 표면 0 | `OperatorStrategy` 는 `internal constructor` 이고 유일한 생성 경로가 검증 함수다. 그 문을 그대로 쓰면 「전략은 검증을 통과한 것만 존재한다」가 유지되고 어댑터가 새로 얻는 권한이 0 이다(누구나 그 함수를 부를 수 있다). **세션 축(6B-1 D-6B1-7)은 그런 문이 없어 port 반환을 원시 스냅숏으로 바꿔야 했다 — 여기는 필요 없다** |
| **D-6F1-7** (계약 갱신 (2), 6B-1 실측 인계) | 전략 어댑터의 패키지는 **`bidvector.adapters.strategy`**(6B-1 이 신설) — `adapters.persistence` 에 두지 않는다. 전용 의존 게이트 test 는 **먼저 병합되는 레인이 만들고 뒤가 합류**한다(허용 = `workflow.strategy`·`strategy`·`sharedkernel`·`adapters.persistence` 의 트랜잭션 경계). `Sql.kt` 는 `persistence` 에 두고 `internal`(모듈 범위)로 참조 | 6B-1 구현 레인 실측: `PersistenceAdapterDependencyTest`(M3/3D)가 `adapters.persistence` 의 domain 참조를 좁히고 `bidvector.workflow` 를 명시 금지한다. 같은 필요로 `adapters.event` 가 이미 갈라져 있고 그 게이트 KDoc 이 allow-list 확대를 거부해 뒀다. **전략 저장소는 `workflow.strategy.AppliedStrategy` 와 `strategy.OperatorStrategy` 를 둘 다 참조하므로 같은 벽을 만난다** |
| **D-6F1-3** | 저장된 전략이 현 정책으로 **무효면 실패**한다(전략을 지어내지 않는다). 실패 형태는 구체 예외이고 그 사유에 「무엇이 무효인가」를 담는다 | 정책 값이 바뀌면 옛 전략이 무효가 될 수 있다. 그때 기본값·부분값으로 채우면 **운영자가 승인하지 않은 전략으로 판정이 돌아간다**. 「오류·최소 표본이 0점/성공으로 변환되지 않는다」(M5 완료 조건)와 같은 규율 |
| **D-6F1-4** | 전략이 **아직 없는 상태**(첫 기동)는 실패가 아니라 **빈 전략**이다 — 그 값도 검증 함수를 지나며, 「설정됐는가」 술어가 거짓인 상태로 산다 | 도메인에 그 술어가 이미 있다(전략 미설정과 규칙 비어 있음은 다른 물음이라고 문서가 적는다). 첫 기동을 오류로 만들면 배선이 서지 않는다 |
| **D-6F1-5** | 개정 번호는 **도메인이 정한 값을 그대로** 저장한다 — DB 기본값·트리거를 두지 않는다 | 6B-1 D-6B1-3 과 같은 규율(값의 주인을 하나로). V2 의 `revision` 트리거는 공고 축이고 이 표에 상속되지 않는다 |
| **D-6F1-6** | 정책은 **조립이 주입**한다 — 어댑터가 정책 파일을 직접 읽지 않는다 | 정책의 정본은 하나이고(승인된 정책 데이터) 어댑터가 두 번째 독자가 되면 값이 갈린다. 조립 자리는 `OPEN-6F-ASSEMBLY` |

## 위협 모델 — 6F-1 고유 경계

**방어한다**: (a) 어댑터가 **검증을 지나지 않은 전략을 만들 수 없다**(새 public 표면 0, 도메인 생성자 호출 0)
(b) 정책으로 무효인 저장 값이 **조용히 통과하지 않는다** (c) 개정 번호가 DB 가 아니라 도메인이 정한 값이다
(d) 첫 기동(전략 없음)이 오류가 아니라 빈 전략이다 (e) 저장이 기존 append-only·provenance 가드를 건드리지
않는다(기존 게이트 test 무편집 통과).
**방어하지 않는다**: 동시 편집 충돌(전략 쓰기는 편집 세션이 직렬화하고 세션 축은 6B-1) · 전략 이력의 보존·파기
(6B-3) · 다른 포트 여덟(6F-2~7) · app 조립(`OPEN-6F-ASSEMBLY`) · HTTP 노출(6A).

**우회 후보**: (1) 어댑터가 **검증을 건너뛰고** 도메인 타입을 만들 길(리플렉션·같은 패키지 선언·`copy`)이
있으면 (a) 가 무너진다 → 도메인 생성자 호출 0·새 public 표면 0 을 AST/`javap` 로 실측하고, `copy` 가
`@ConsistentCopyVisibility` 로 막혀 있음을 확인. (2) 정책 불일치 시 **부분값으로 채우는** 경로가 남으면 (b) 가
무너진다 → 무효 저장 값을 심어 실패하는지 변이로 실측(기본값으로 채우는 커밋을 만들면 test 가 붉어야 한다).
(3) 개정 번호를 DB 기본값·시퀀스로 정하면 (c) 가 무너진다 → 카탈로그로 기본값·트리거 부재 확인. (4) 빈 전략을
**오류로** 만들면 배선이 서지 않고, 반대로 **무효 전략을 빈 전략으로** 접으면 (b) 와 충돌한다 → 둘을 가르는
test 를 각각 둔다(없음 ≠ 무효). (5) 어댑터가 정책 파일을 직접 읽으면 D-6F1-6 이 무너진다 → 어댑터 소스에 정책
파일 경로·로더 참조 0 을 grep. (6) `save` 가 통로 타입의 필드 대신 **자기 값을 지어** 쓰면 감사가 거짓이 된다 →
왕복 test 가 저장 전후 필드 동일성을 단언.

## (2b) 값 획득 축

| 표면 | 허락하는 것 | 판정 |
| --- | --- | --- |
| `JdbcStrategyRepository`(public class) | 전략 읽기·쓰기 | **경계로 처리** — 배선 주체가 port 로 이미 갖는 권한과 동치. 생성자는 DataSource·정책만 받고 도메인 값을 만들지 않음을 실측 |
| `StrategyRow`(행 → 초안) | **초안** 생성 | 닫는다 — 초안은 공개 타입이고 불변식을 나르지 않는다. 전략은 검증 함수만 낸다(D-6F1-2) |
| 검증 함수 재사용 | 유효한 전략 획득 | **경계로 처리** — 이미 공개된 유일한 문이고 어댑터가 새로 얻는 권한이 0 이다. 이 판정이 틀렸다면(그 함수가 실제로는 무효 입력을 통과시킨다면) HIGH — verifier 표적 |

## 병행 레인 — 마이그레이션 번호와 공유 파일

| 레인 | 마이그레이션 | 상태 |
| --- | --- | --- |
| 6B-1(세션·스키마 게이트) | **V8** | 구현 중(worktree `bid-vector-v2-m6b`) |
| **6F-1(이 slice)** | **V9** | 착수 |
| 6A-1(HTTP·인증·요청 감사) | **V10**(계약 갱신으로 변경) | 계약 고정, 구현 대기 |

`Sql.kt` 는 세 레인이 문장 추가로 만진다(같은 함수 무편집). `milestone-6.md` 는 서로 다른 절. **병합 순서가
바뀌면 번호를 다시 붙인다** — PR 요청 시점에 `main` 의 최신 번호를 재확인한다.

## 하네스 레인 변경

`git log --oneline c4d09cc..HEAD -- CLAUDE.md .claude/ docs/harness/` — 없음(착수 시점).

## OPEN — 수령·신설

| 식별자 | 처분 |
| --- | --- |
| `OPEN-6A-EVALUATION-ADAPTERS`(6A-1 신설) | **6F 군으로 대체**(D-6F1-1) — 포트별 slice 로 가르고 이 slice 가 첫째 |
| `OPEN-6F-ASSEMBLY`(신설) | app DI 조립 자리 — 포트 구현이 모이는 시점에 6A-1(HTTP) 또는 전용 slice |
| `OPEN-6F-WATCH-TEXT-SOURCE`(신설) | 감시 대상이 요구하는 **원문 텍스트**의 출처 — canonical 공고 fact 에 열이 없고 raw 원문에는 있다(3A 가 필드 계약에 등재하지 않았다). 6F-4 전 결정(계약 등재 vs raw 조회) |
| `OPEN-6F-ACTIVE-BID-DEFINITION`(신설) | 여력이 요구하는 **「활성 투찰」 정의와 상한 출처** — 저장소·discovery 에 정의가 없다(capability map 의 capacity 는 금액 도급한도 축으로 다른 것). 6F-3 전 **운영자 결정** |
| `OPEN-4B6-PROFILE-SOURCE`(M4 이월) | 운영자 프로필 출처 — 6F-6 |
| `OPEN-STR-12`(활성) | 알림 발송 채널 어댑터 — 6F-7 은 **요청 → outbox** 까지, 발송은 그 뒤 |

## 리뷰 레인

마이그레이션 파일(V9)이 생기므로 **`migration-reviewer`** 가 추가로 붙는다. Codex 는 되돌리기 어려운 경로(DB
마이그레이션)라 대상이 되지만 유료 호출이므로 리뷰 요청 시점에 범위·비용을 운영자에게 제시하고 승인받은 뒤에만 건다.

## 계약 갱신 이력

| 일자 | 갱신 | 사유 |
| --- | --- | --- |
| 2026-09-17 구현 중(2) | **D-6F1-7 신설** — 전략 어댑터 패키지를 `adapters.persistence` → **`adapters.strategy`**(6B-1 신설), 의존 게이트 test 는 먼저 병합되는 레인 소유·뒤가 합류 | 6B-1 정지·보고의 인계(`persistence` 는 workflow 참조 금지, 같은 벽을 전략 저장소도 만난다) |
| 2026-09-17 착수 | 초판 — D-6F1-1~6 | 운영자 지시(배선 우선) · 배선 재고의 착수 권고 · 전략 타입에 **이미 공개된 검증 문**이 있다는 실측 |

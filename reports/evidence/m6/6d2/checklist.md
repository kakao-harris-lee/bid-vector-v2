# M6/6D-2 — 리뷰 요청 점검표 (구현 레인)

base `f4b4ff91` · 마지막 산출물 커밋 `ea07b9a7`(승인 전 일괄) · 정본 계약 `scope.md`(D-6D2-1~9).

## 리뷰 요청 조건

| 조건 | 상태 |
|---|---|
| 구현 diff 가 커밋되어 base/head 가 고정됨 | 예 — in_scope 경로만, 혼입 0 |
| test/lint/type/architecture/contract 명령 통과 | 예 — `check` · `qualityBaseline` 둘 다 exit 0 |
| 변경된 fixture 와 정책 version 의 근거 | fixture 변경 0 · 정책 정본 변경 0. **시딩 전략 revision 만 1 → 7** 로 바꿨다(test 입력이고 정책이 아니다 — 아래 절) |
| 알려진 제한과 rollback | 아래 절과 `rollback.md` |

## 시딩 revision 을 1 에서 7 로 바꾼 사유

재현 등식의 typed 단언이 **시딩값과 대조**하려면 그 값이 어느 기본값과도 겹치지 않아야 한다 —
`operator_strategy` 의 앞 판 시딩값(1)도, 행 부재 시 repository 의 대체값(0)도 아니어야 한다. 변이 표의
「시딩 revision 을 1 로」 행이 그 사각을 실측한다(P-1 이 붉어진다). 6F-10 checklist 가 지목한 자리다.

이 값은 e2e 전체의 시딩에 걸리지만 기존 test 가운데 revision 을 읽는 단언은 0 건이었고, 바꾼 뒤
기존 13 test 전부 초록이다.

## 설계 검토 (2) 우회 ↔ 닫는 술어 ↔ 실측

| 우회 | 닫는 술어 | 실측 |
|---|---|---|
| 1. 크래시 wrapper 가 안 불려 run 이 정상 완료 | 죽은 run 은 `shouldThrow<RelayAborted>` + cause 타입 + **중간 상태**(CLAIMED 잔류 · inbox 0 · sender 계수) | 변이 「hook 미호출」 RED ×4 · 「R-2 주입 제거」 RED |
| 2. 「재기동」이 같은 인스턴스 재호출 | **인스턴스로는 닫히지 않는다**(verifier r1 R1-M-2 가 반증 — 아래 절). 닫는 것은 relay 의 무상태성이다: 임대 세션이 `withLease` **호출 단위**이고 relay 가 호출 사이에 상태를 갖지 않으므로 새 인스턴스와 재호출이 **동치**다. 그래서 「재기동이 아니었다」로 결과가 달라지는 경로가 없다 | 탐침 실측 — 막힌 홀더 앞에서 **같은 조립의 재호출도** `Busy` 를 받는다. 새 인스턴스의 실익은 sender 계수를 조립별로 가르는 것(발송 합)과 production 재기동 모양을 따르는 것뿐이다 |
| 3. test 가 상태를 강제해 격리를 흉내 | e2e 에 상태 강제 helper 미사용 — 고아는 죽은 run 이 남긴 행이고 격리 계수는 relay 보고에서 | `forceOutboxState`·`insertClaimed*` 호출 0(e2e 소스셋 전체) |
| 4. sender 가 dedup 해서 발송 1 | sender 는 기록만 하고(앞 결과를 돌려주되 호출은 센다) 판정은 `skippedDuplicates` + inbox 행 수 + 둘째 run 발송 0 | 변이 「inbox 판정 뒤집기」 RED |
| 5. 등식이 무엇을 넣어도 같다 | 음성 대조 둘(release · revision) + typed 단언이 **상수·시딩값**과 대조 | 변이 「revision 올림 제거」 RED · 「정책 버전 사용 자리 교체」 RED |
| 6. 같은 참조 비교 | DB 되읽기 — 저장된 `payload_type` 을 **읽어서** 복원하고 타입·본문을 한 질의로 짝지어 읽는다 | 6D-1 에서 이어받은 술어, 복수형으로 확장 |
| 7. 변이가 안 들어감 | 적용 직후 `git diff --numstat` | 변이 표의 모든 행 |
| 8. 투영 단계가 비어도 초록 | P-2 는 **DB 행**의 revision 을 UPDATE 해 payload 가 바뀜을 잰다(DB → repository → 평가 → reach → payload → codec → DB 전 구간) | 변이 「올림 제거」 RED |
| 9. 함수는 RED 호출 자리는 초록 | 격리·중복 판정은 production **호출 자리의 결과**(DB 상태 분포)로 읽는다 | 중복 축은 변이로 실측 · 격리 축은 verifier 소관(계약) |
| 10. R-5 교착·시한 통과 | `CountDownLatch.await` 의 **반환값**을 단언(신호 5s · 쥠 30s · 합류 60s) · 막힌 홀더는 풀린 뒤 완주 | 변이 「래치 대기 제거」 RED |
| 11. 재기동 run 이 우연히 발송 | R-1 은 행 1 에 `claimed 0` 을, R-3 은 `ISOLATED N−k` 와 **재기동 run 발송 0** 을 따로 단언 | 두 test 본문 |
| 12. B-2 를 「중복 0」 위반으로 오독 | 계약이 entry 단위·단일 재기동 문면임을 KDoc 에 적고 **실측값**(발송 합 2)을 단언해 사실로 고정 | R-2 |

## (2b) 값 획득 축 — 새 production 표면 0

`git diff --name-only f4b4ff91..HEAD -- '*/src/main/*'` 빈 출력. `internal` 완화 0 ·
`@VisibleForTesting` 0 · 새 production 클래스 0. test seam 은 `PipelineAssembly` 생성자의 람다 인자
하나이고 **기본값이 production** 이라, 아무 것도 넘기지 않은 조립은 정직하다. 그 클래스는 test
소스셋의 `internal` 이므로 production 에 허락하는 것이 없다.

**「경계로 처리」 행의 실측 — 그리고 문면 둘을 고쳤다.** 주입 조립의 협력자 그래프에는 비-MAIN 객체가
나타나고 정직한 조립에는 **하나도 없다**(seam test 가 둘을 같은 자리에서 대조한다). 첫 실행이 두 가지를
정정했다:

| 쓰려던 문면 | 실측 | 고친 단언 |
|---|---|---|
| 잡히는 것은 wrapper 하나 | wrapper + 그 **hook 람다** 둘 — 그래프가 데코레이터의 필드까지 내려간다 | 개수를 **잠근다**(`leaks shouldHaveSize 2`, cr G-1) — 개수는 이름이 아니라 표기에 낡지 않고, 이 실측 자체가 회귀 test 가 된다 |
| 감싼 production 경계는 그래프에서 **사라진다** | 사라지지 않는다 — 데코레이터가 `delegate` 로 쥐고 있어 둘 다 보인다 | 「감싸인 production 경계도 함께 보인다」 — 위임으로는 숨지 못한다 |

**건너뛴 가지 셋도 0 으로 잠갔다**(cr G-1). hook 람다는 깊이 2 라, 그 필드 읽기가 실패하거나 깊이
상한에 걸리면 `leaks` 에서 람다만 조용히 사라지고 앞 판의 단언 둘은 **그대로 통과**했다. 잠그는 것은
오늘의 거동이 아니라 그 거동이 유지된다는 사실이다.

## 재사용과 사본

`adapters` test 소스셋 안이라 `bidvector.adapters.relay` 의 `internal` 을 그대로 쓴다 — **사본 0**:
`CrashAfterDispatch`(R-2 의 사건이 그 클래스의 사건과 같다) · `RelayWorkerDied` · `outboxStateCounts` ·
`inboxKeyCount`. 가시성이 막은 자리는 하나다 — `app` test 의 `setStrategyRevision` 은 다른 소스셋이라
쓸 수 없어 **형태만** e2e support 에 옮겼다(SQL 한 줄, 그 사유를 KDoc 에 적었다).

새로 지은 것은 경계 데코레이터 하나다. `InterferingTransactions`(같은 소스셋)가 이미 있지만 **호출
순번**에 걸고, 설계 검토가 금지한 모양이라 재사용하지 않았다 — 사건에 거는 쪽을 따로 둔 이유를
그 파일 머리에 적었다.

**승인 전 일괄의 레인 판단 셋**:

| 항목 | 판단 | 사유 |
|---|---|---|
| G-3 시딩 revision 파라미터 | **제거** | 넘기는 호출부가 0 이었고(과잉), 전부 기본값인 목록의 맨 앞이라 뒤에 positional 호출이 생기면 `maxActiveBids` 자리를 조용히 먹는다. run 사이의 revision 변경은 `setStrategyRevision` 이 든다 |
| G-6 시한 상수 셋 | **통합** | 같은 패키지에 값·역할·사유가 같은 사본이 둘이었다. 선례의 `private companion` 셋을 지우고 공용 top-level 셋 하나로 모았다 — 이름은 두 쓰임(claim 경합·임대 경합)을 함께 담는다 |
| G-7 상태 어휘 넷 | **이사 + 같은 패키지 리터럴 치환** | 크래시 주입 파일의 주제와 무관해 `PipelineE2ESupport` 로 옮겼다. `bidvector.adapters.relay` 의 리터럴은 **in_scope 밖이라 그대로** 두고 그 사실을 KDoc 에 적었다. 어휘 정본은 V6 CHECK 와 전이 SQL 이고 `main` 에 재사용할 Kotlin 상수가 없다 |

## 알려진 제한

| 제한 | 받는 자리 |
|---|---|
| **전달과 T2 사이의 크래시 뒤 같은 키의 재발생은 다시 발송된다**(B-2 (a) 실측 — R-2 가 발송 합 **2** 를 단언한다). inbox 는 전달 뒤 T2 에서만 기록되므로 그 창에서는 키 중복 제거가 서지 않는다. 계약의 「중복 0」은 **entry 단위**이고 at-most-once 가 막는 것은 entry 의 재실행이다 | ADR 0005 §7 addendum(팀장) · `OPEN-NOTI-02` |
| **정책 버전의 「바꾸면 깨진다」 음성 대조가 없다**(B-3 (a)). `EVALUATION_LADDER_POLICY_VERSION` 은 `workflow` 상수라 밖에서 바꿀 자리가 없다 — 등식이 그 축을 **읽고 있음**은 판정의 **사용 자리**를 바꾼 변이로만 실측했다. **정의 자리를 바꾸는 변이는 이 등식이 잡지 않는다**(cr G-5): 판정과 단언이 같은 `val` 하나를 참조하므로 양쪽이 함께 움직여 초록으로 남는다 — 그 초록을 「등식이 그 축을 읽지 않는다」로 오독하지 않아야 한다. 같은 성질이 시딩 revision 에도 있다(상수 정의를 1 로 바꾸면 단언도 1 이 된다) | 정책 버전 주입 seam 이 생기는 slice |
| **「재기동」은 in-JVM 등가다**(B-1 (a)). 새 조립 인스턴스 집합이고 OS 프로세스 사망이 아니다 — 죽은 relay 의 예외가 `withLease` 의 `finally` 로 임대를 푸는 경로를 탄다. 전원 차단·TCP 반개방은 이 축 밖이다 | `PostgresAdvisoryLockLease` 의 알려진 제한(6F-10) |
| **R-5 의 「막혔다」 판정은 래치 신호 기반이고 쥠 시한이 30s 다.** 호스트가 극단적으로 느리면 거짓 RED 가 날 수 있다(안전한 방향) — `await` 반환값을 단언하므로 시한 만료가 초록으로 지나가지는 않는다 | — |
| **고아 격리의 「두 relay 가 겹치는 창」은 재지 않는다.** R-5 는 「살아 있는 홀더면 Busy」 한 모양만 잰다 — 임대를 쥔 채 잃는 교차는 6F-10 의 `RelayLeaseLossDatabaseTest` 가 갖는다 | 6F-10 |
| **D-2 는 구조 축이라 이 레인이 변이를 재지 않았다.** 같은 entry 의 재claim 불가는 전이표와 claim SQL 의 `WHERE state` 가 막는다 | verifier 변이 |
| **발송 채널이 fake 다** — 실 채널이 없어 route·renderer·sender 셋만 바꿔 끼운다 | `OPEN-STR-12` |
| **요건·전략·프로필은 DB 시딩이다** — 편집 HTTP 경로·LLM 추출 체인이 미배선 | 6A-2 · 추출 체인 배선 slice |
| **claim 관측 열이 없다** — 고아 판정에 시각이 없으므로 「언제 죽었는가」는 DB 에 남지 않는다 | `OPEN-6F10-CLAIM-OBSERVABILITY` |

## 신설 OPEN 후보

없다. 이 slice 가 닫은 것은 6D-1 의 C-4(재현 등식에 정책 버전·전략 revision 없음)이고, 새로 열 질문은
위 표의 「받는 자리」가 모두 기존 OPEN·후속 slice 로 간다.

## 계약 문면과 다르게 택한 자리

둘이다. ① seam test 의 단언 문면 — 위 (2b) 표의 두 행이 그 실측이다(계약·설계 검토는 「주입 조립에서
production 경계가 사라진다」를 예상했다). ② `milestone-6.md`·ADR 0005 는 계약의 in_scope 이지만
팀장 레인이 쓰므로 이 레인은 건드리지 않았다.

## 하네스 레인 변경

없음. `.claude/` 아래 파일을 만들거나 고치지 않았다.

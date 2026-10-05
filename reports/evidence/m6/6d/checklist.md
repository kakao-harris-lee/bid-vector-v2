# M6/6D-1 — 리뷰 요청 점검표 (구현 레인)

base `fd4629fe` · 마지막 산출물 커밋 `bc7f37d6` · 정본 계약 `scope.md`(D-6D-1~15).

## 리뷰 요청 조건

| 조건 | 상태 |
|---|---|
| 구현 diff 가 커밋되어 base/head 가 고정됨 | 예 — in_scope 경로만, 혼입 0 |
| test/lint/type/architecture/contract 명령 통과 | 예 — `check`(test 재실행 강제) · `qualityBaseline` 둘 다 SUCCESS |
| 변경된 fixture 와 정책 version 의 근거 | fixture 변경 0 — 기존 `contracts/testdata` 골든과 `adapters/ml` fixture 를 읽기만 한다. 정책은 아래 절 |
| 알려진 제한과 rollback | 아래 절과 `rollback.md` |

## 정책의 출처 — 출하 정본과 test fixture를 가른다

**출하 정본을 그대로 resolve 하는 것 여섯**: 기회 분석 정책(합성 규약·키워드·텍스트 상한·호출 예산·
release 선택자) · 전략 정책 · 면허 자격 정책 · 알림 배달 정책 · 수집 범위 정책 · **KONEPS 수집 정책**
(필드 계약 레지스트리·resultCode 범주표 — 수집 경로가 운영 정책을 그대로 resolve 한다).

**test 가 값을 고른 것 셋**: ML 호출 정책(시한 상한·재시도 횟수·백오프·차단기·feature schema 버전) ·
KONEPS HTTP 정책 · KONEPS 호출 관문의 승인 상한. 셋 다 `adapters` 의 기존 test fixture 관례를 그대로
쓴다(운영 값으로 돌리면 test 가 분 단위가 된다).

**둘 사이의 경계가 timeout 축의 쟁점이었다.** 지금 ML 호출 정책의 시한 상한은 **출하 예측 예산의
두 배**로 도출되고, 그래서 `minOf` 가 고르는 항은 출하 예산이다 — test 가 그 사실을 직접 단언한다.
출하 예산 값 자체도 고정점으로 잠가, 값이 바뀌면 서버를 부르기 전에 붉어진다.

## 설계 검토 (2) 우회 ↔ 닫는 술어 대응

| 우회 | 닫는 술어 | 실측 |
|---|---|---|
| 1. fake ML 이 늘 성공해 timeout 이 예산을 안 넘음 | 지연을 **출하 예측 예산**에서 도출하고, 실효 시한이 그 예산과 같음을 단언. 지연 0 대조 run 이 같은 배선에서 성공 | 변이 「지연 0」 RED · 변이 「출하 예산 1시간」(clone) RED |
| 2. 중복 test 가 inbox 를 안 지남 | outbox 2행 · inbox 1행 · 발송 1건을 함께 단언 | 변이 「둘째 평가 제거」 RED |
| 3. 골든 대신 test 안 리터럴 | 두 갈래 모두 계약 골든에서 바이트를 읽는다(부재 시 로딩이 던짐) | 변이 「골든 교체」 RED ×2 |
| 4. 재현 등식이 같은 참조 비교 | 두 run 은 다른 조립·다른 트랜잭션, 비교 대상은 DB 에서 되읽은 직렬화 문자열. 음성 대조 포함 | 변이 「둘째 release 교체」 RED |
| 5. E2E 가 기본 `check` 밖 | 조건 애노테이션·filter 0, 등재 등식이 양방향 | `check` 안에서 13 test 실행(`--rerun` 으로 실행 확인) |
| 6. use case 를 대역으로 대체 | 평가와 발송이 **실제로 쓰는** 두 객체에서 필드 그래프를 내려가 **닿는 모든 객체(출처 기준)** 를 본다 — 수집·하강 기준이 패키지 이름이 아니라 `CodeSource` 라 어느 패키지의 대역이든 그래프에 들어온다. 건너뛴 가지(깊이 상한·필드 읽기 실패)는 결과에 신호로 실려 0 을 단언한다 | 변이 셋 RED — 위임 대역 · 관례 밖 패키지 대역 · SAM 람다 대역. 깊이 상한 낮춤도 RED |
| 7. 순차 실행이라 충돌 0 | 첫 워커가 행을 **쥔 동안** 둘째가 claim 하고, 첫 워커는 롤백한다. 둘째가 막히지 않고 돌아왔다는 사실(대기 반환값)·쥔 동안 못 집음·롤백 뒤 복귀 셋을 단언 | 변이 「완전 순차」 RED · 변이 「경합 행 없음」 RED · 변이 「production `SKIP LOCKED` 제거」(clone) RED |

## 사다리 임계 — 두 방향

승격 임계가 후보를 가른다는 사실을 같은 run 에서 잠근다. 임베딩 대역이 공고 번호로 응답을 골라 한
후보에만 직교 벡터를 돌려주고, 그 후보는 코사인 유사도 0 이라 priority 가 임계 아래로 간다. 승격은
한쪽뿐이고 outbox 행도 하나다. 임계를 0 으로 내리면 둘 다 승격돼 붉어진다.

## (2b) 값 획득 축 — 새 production 표면 0

`git diff --name-only fd4629fe..HEAD -- '*/src/main/*'` 빈 출력. production `internal` 완화도
`@VisibleForTesting` 도 없다. 조립이 쓰는 모든 production 생성자는 **이미 public** 이었다. 협력자
그래프 순회는 리플렉션이고 test 소스셋 안에만 있다 — 필드 **값은 읽기만** 하며(`Field.get`), 바꾸는
것은 접근 가능 표시뿐이다. 컨테이너 가지는 `Collection`·`Map`·`Pair` 로 한정해 임의 `Iterable` 의
iterator 를 소모하지 않는다.

## 계약 문면과 다르게 택한 자리

E2E 의 자리를 `adapters` 의 test 소스셋으로 옮긴 건은 계약 r3(D-6D-5)이 결정으로 받았다. 그 밖에
문면과 다른 자리는 없다.

## 알려진 제한

| 제한 | 받는 자리 |
|---|---|
| **종단 전이가 port 메서드를 지나지 않는다.** relay 는 전이 명령 타입을 만들 수 없어 production 전이 SQL 상수를 사본 없이 직접 실행한다 — 그 통로를 열지 않는다는 것이 4C-2 의 명시 결정이다 | `OPEN-4C2-MARK-UNEXERCISED` → 6F-10 |
| **relay·reclaim·평가→outbox 커밋 경로가 production 에 없다.** 알림 요청은 자기 트랜잭션에서 커밋된다(도메인 write 와의 원자성 미소유) | 6F-10 · `OPEN-6A3-EVALUATION-COMMIT` |
| **재현 등식에 정책 버전·전략 revision 이 없다** | 6F-10 payload 확장 → 6D-2 |
| **rollback 축은 gateway 수준에서만 잰다.** 파이프라인이 쓰는 release 선택자는 기회 분석 정책에 고정돼 있고 그 교체는 production 변경이다 | 6E runbook · ML 배선 결정 |
| **restart 뒤 수렴·redelivery 는 재지 않는다** | 6D-2 |
| **승격 임계가 test 리터럴이다.** 이 slice 가 잠그는 것은 값이 아니라 그 값이 후보를 가른다는 사실이다 — 운영 값은 아직 승인 전이다 | `OPEN-4B1-LADDER-THRESHOLDS` |
| **기존 claim 경합 test 가 같은 구멍을 갖는다.** 4C 계열의 outbox claim test 는 production `SKIP LOCKED` 를 떼도 초록이다(verifier 실측 — 그 test 에 잰 변이는 이것 하나다). 이 slice 는 그 파일을 건드리지 않았다 | `OPEN-6D1-CLAIM-CONCURRENCY-TEST` → 6F-10 |
| **전략 행 INSERT 사본이 저장소에 둘이다.** 여력 상한을 설정하는 HTTP 경로가 없어 직접 INSERT 하고, `app` 쪽 dry-run E2E 가 같은 열 목록을 이미 갖는다 — 표에 NOT NULL 열이 생기면 두 자리가 함께 깨진다 | `OPEN-6A3-MAX-ACTIVE-BIDS-EDIT`(6A-2) |
| **포트 경계 여섯은 「대역이 와도 되는 자리」 목록이다.** 그중 넷은 production 구현이 아직 없다 — 생기면 그 객체가 production 출력에서 오므로 「경계마다 대역이 하나씩」 단언이 **먼저 붉어진다**. 그때 목록에서 그 경계를 빼는 것이 올바른 조치다 | 각 포트의 production 구현 slice |
| **DB conflict 의 「막혔다」 판정은 시한 기반이다.** 둘째 claim 이 막혔는지를 쥠 시한 만료로 가르므로 **막힘**과 **매우 느림**을 구별하지 못한다. 완화로 쥠 시한을 30s 로 넓혔다(둘째 claim 이 돌아오면 즉시 풀려 정상 경로 비용 0 — 실측 10.76s, 넓히기 전 10.64s). **축이 이 시한에만 기대지는 않는다**: 시한 단언을 떼도 「쥔 동안 둘째가 못 집음」 구조 단언이 같은 변이를 잡는다(판정 레인 실측). 실패 방향은 거짓 RED 다 | 6F-10(같은 축을 production 으로 옮길 때) |
| **협력자 그래프의 출처 판정은 람다 컴파일 전략에 기댄다.** 시각 포트가 SAM 람다라 그 클래스의 `CodeSource` 가 호스트에서 온다는 사실 위에 선다. 툴체인이 바뀌면 그 객체가 미상으로 떨어져 **본래 결함과 무관하게** 붉어질 수 있다 — 미상은 두 필터 모두에서 붉어지므로(판정 레인 실측) 실패 방향은 거짓 RED 다 | 툴체인 갱신 slice |
| **요건은 DB 시딩이다.** LLM 추출 체인이 미배선이다 | 추출 체인 배선 slice |
| **발송 채널이 fake 다** | `OPEN-STR-12` |

## 하네스 레인 변경

없음. `.claude/` 아래 파일을 만들거나 고치지 않았다.

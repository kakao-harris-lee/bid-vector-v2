# M6/6F-10 — 알려진 제한과 근거

## 알려진 제한 (운영자 승인 D-6F10-18 ⑨ + 구현 중 측정으로 늘어난 것)

| # | 제한 | 성립 조건 | 그 자리의 OPEN |
|---|---|---|---|
| 1 | **배선된 환경이 `Production` 이 아닌 배포에서 relay 는 늘 억제로 끝난다** — 정책표에서 `DeliveryMode.Live` 환경은 `Production` 하나이고, 그 환경 설정은 배선이 **거부**한다(실 sender 부재). 그래서 이 slice 의 relay 가 실제로 claim 하는 배포는 **없다**: claim·발송 경로는 test 가 환경을 명시 주입해서만 돈다 | 상시 — `OPEN-STR-12` 가 실 sender 를 들여 거부를 풀 때까지 | `OPEN-STR-12` |
| 2 | `claimedEntries`(`state = 'CLAIMED'`)는 **어느 인덱스도 덮지 않아 순차 스캔**이다 — 부분 인덱스는 `PENDING` 전용이고 마이그레이션 0(운영자 결정 A-2 (a))이라 인덱스를 더하지 않았다. 운영 규모에서의 실제 비용은 **측정하지 않았다** | 상시 | `OPEN-6F10-CLAIM-INDEX` |
| 3 | `payload_type` 문자열로 가는 길이 **둘**이다(payload 클래스 기준 · kind 기준) — 타입이 그 둘의 어긋남을 막지 못한다. 등식 test 가 kind 마다 표본 payload 를 짝지어 잠근다(그 test 가 유일한 방어) | 상시 | — |
| 4 | **커밋 러너를 켠 배포도 `bidvector.evaluation.candidate-cap` 이 필수**다 — `EvaluationWiring` 이 상시 활성이고 그 값에 기본값이 없다 | 상시 | — |
| 5 | **활성 투찰 수를 설정에서 받는다** — 이 저장소에 실 진행 입찰 계수기가 없다. dry-run 은 요청 본문에서, 커밋 러너는 설정에서 받고 **둘 다 그 값의 정직성은 경계 밖**이다(D-6A3-5 가 이미 그렇게 선언했다). 「0 고정」은 쓰지 않았다 — 여력을 0 으로 지어내면 용량 보류 판정이 조용히 사라진다 | 상시 | `OPEN-6F10-CAPACITY-SOURCE` |
| 6 | **relay·커밋 러너가 수집 레인의 타입 이름을 쓴다**(`CollectionLog`·`CollectionTermination`·정책 키 `app.assembly.tier3-collection-classes`) — 중복을 만들지 않으려고 재사용했고, `app/.../collection/**` 은 in_scope 밖이라 중립 이름으로 바꾸지 않았다. 규칙은 같고 이름만 어긋난다 | 상시 | — |
| 7 | **전이 예외의 종료 코드는 `INCOMPLETE` 가 아니라 `FAILED`** 다 — 수집 러너 선례가 「실행 실패」(정제된 원인 코드 + 재던짐)와 「끝났지만 미완」(값)을 가르고, 갱신 계수 0 은 앞쪽이다. 계약 문면(D-6F10-18 ⑧ 「전이 예외 → INCOMPLETE」)과 다른 처분이며 사유는 이 줄이다 | 상시 | — |
| 8 | **미지 payload 분기는 심층 방어다** — kind 필터(D-6F10-13)가 들어온 뒤 어댑터는 `payload_type` 과 payload 클래스를 함께 정하므로 그 조합을 만들 수 없다. 그래서 그 처분은 **port 경계에서만** 측정된다(fake outbox port 가 다른 종류 payload 를 돌려주는 test) | 상시 | — |
| 9 | **relay 협력자 출처 단언(6D-1 축 ①)은 relay 를 포함하지 않는다** — E2E 의 협력자 그래프는 `useCase`·`dispatcher` 두 뿌리에서 출발하고 relay use case 를 뿌리에 더하지 않았다(그 그래프의 포트 경계 집합 등식이 닫힌 6D-1 축이라 넓히지 않았다). relay 조립의 출처는 대신 `RelayAdapterDependencyTest`(상수 풀 허용 루트 + 전이 SQL 부재)가 진다 | 상시 | — |
| 10 | **컨테이너 스모크에 「relay 꺼짐」 단언을 더하지 않았다** — `.github/workflows/ci.yml` 은 in_scope 밖이다. 대신 app 배선 test 가 「`mode` 없음 → 러너 빈 0」을 잠근다 | 상시 | — |
| 11 | **임대 해제는 홀더의 생사와 다른 축이다**(R1-M-1 로 문면 정정) — 세션 advisory lock 은 **연결**이 끊기면 서버가 즉시 놓는다. 그 해제는 프로세스 사망뿐 아니라 `pg_terminate_backend`·네트워크 단절로도 일어나므로 **살아 있는 홀더**도 임대를 잃을 수 있다(앞 판 문면의 「반대 방향은 일어나지 않는다」는 거짓이었다 — verifier probe V6b 가 실측). 그래서 relay 는 **행마다 발송 전에 임대를 다시 묻고** 거짓이면 남은 행을 건드리지 않고 `LeaseLost`(종료 코드 `FAILED`)로 멈춘다. 남는 창은 둘 — ⓐ 질의가 성공한 **그 순간** 이후 발송 중에 끊기는 구간(그 행은 발송됐는데 종단 전이를 못 해 `CLAIMED` 에 남고 다음 run 이 격리한다 = 발송 1회 + `ISOLATED` 표기) ⓑ TCP 반개방이면 서버가 끊김을 keepalive 만료까지 모른다(그 구간의 다음 소비자는 `Busy` 를 받아 아무것도 하지 않는다 — 안전한 쪽) | 상시 | — |

| 12 | **배치 claim 의 손실 증폭**(R1-L-5) — `claim` 이 `limit` 단위 배치라 행 하나의 예외가 같은 배치의 **미시도 행 전부**를 다음 run 의 고아 격리로 보낸다. 임대 상실(`LeaseLost`)도 같은 모양이다 — 멈춘 뒤 남은 행이 격리된다. 행 하나씩 claim 하면 사라지지만 run 당 질의 수가 배치 크기만큼 늘어난다 | 상시 — `bidvector.relay.claim-limit` 를 1 로 두면 증폭이 0 이다(운영 선택) | — |

| 13 | **집었는데 전부 거부·격리된 run 은 비-0 이지만, 일부라도 전달된 run 은 0 이다**(D-6F10-27 ⑦ 의 경계, cr L-3). 부분 실패를 비-0 으로 올리면 정상 운영이 늘 붉어지므로 그 선을 택했다 — 개별 행의 거부·격리 수는 로그 한 줄의 계수로만 보인다 | 상시 | — |

| 14 | **relay 의 종료 코드 다섯 중 `INCOMPLETE` 의 미지 payload 갈래는 도달 불가다**(cr L-12) — kind 필터 뒤로는 어댑터가 「종류는 맞고 타입은 아닌」 행을 만들 수 없고, 형식을 어긴 행은 복호 fail-closed 로 run 을 멈춘다(그쪽은 `FAILED`). 그 갈래는 심층 방어로 남기고 그 사실을 `RelayExitCode.INCOMPLETE` KDoc 에 적었다. 복호 불가 행 하나가 그 종류의 relay 전체를 매 run 멈추는 성질의 복구는 **앱 권한 밖**이다(`rollback.md` — DBA UPDATE) | 상시 | — |

## 설계 검토 (3) 항목의 처분

- **과잉으로 걷은 것 셋**: payload v2 토큰 + 구 디코더(저장된 구행이 0 이라 죽은 코드 — 아래 근거) · lease 토큰 타입(클로저 경계로 대체) · TTL·claim 시각 열(고아 판정이 구조적 사실이라 불필요).
- **미달이 아닌 것**: 실 sender(제한 1) · `StrategyUpdated` 소비자 부재(`OPEN-6F10-STRATEGY-EVENT-CONSUMER` — `OutboxConsumerKind` 가 그 종류를 어휘에 들고 있는 것이 그 OPEN 을 보이게 하는 자리다) · 백로그 게이지 · 판정 기록 표(`OPEN-6F10-EVALUATION-DOMAIN-WRITE`) · db-scheduler(`OPEN-6F10-SCHEDULER`).

## 「production 행 0」의 근거 (D-6F10-16 — 구 디코더를 두지 않은 이유)

`payload_type` 토큰을 유지하고 필드 수만 늘렸다(17 → 20). 저장된 구행을 깨지 않는다고 말할 수 있는 근거는 **그런 행이 존재한 적이 없다**는 것이고, 그것은 세 사실의 결합이다:

1. 그 payload 를 **쓰는** 유일한 자리는 `OutboxNotificationRequestPort` 다.
2. 그 타입을 참조하는 main 코드는 **자기 파일뿐**이었다 — app 게이트(`app.forbidden.outbox-types`)가 `app` 의 그 참조를 금지한 채 초록이었고, 어댑터 조립이 없었으므로 배선된 적이 없다.
3. test 는 Testcontainers 일회성 컨테이너를 쓴다 — 영속 DB 에 남지 않는다.

구행이 없는데 구 디코더를 두는 것은 죽은 코드이므로 두지 않았다. 대신 **새 형식의 wire 골든**(축어 문자열)을 신설했다 — 이 파일의 다른 codec test 는 전부 왕복이라 encode·decode 가 **함께** 바뀌면 통과한다(칸 순서·구분자를 같이 바꿔도 초록). 축어가 그 대칭 변이를 막는다.

## 값 두 축 (D-6F10-8)

| 축 | 무엇을 재는가 | 어디서 |
|---|---|---|
| ① wire | 정책 버전·전략 개정이 **outbox 행에 실린다** — DB 에 쓰고 되읽어 typed 등식 | codec 왕복 + 골든, 그리고 E2E 가 평가→행→relay 를 실제 DB 로 지난다 |
| ② 거동 | 전략 개정이 다르면 **같은 입력의 payload 가 달라진다** | 평가 use case test — `reach` 가 `strategy.revision` 을 나르므로 개정만 바꿔도 payload 가 바뀐다 |

두 값은 **판정에 쓰인 것과 같은 인스턴스**다 — 사다리 정책 버전은 `EVALUATION_LADDER_POLICY_VERSION` 한 자리에서 와 `Resolution.Resolved` 와 payload 양쪽에 간다. 두 자리에 적으면 「판정에 쓰인 버전」과 「행에 적힌 버전」이 갈려 6D-2 의 재현 등식이 거짓을 말한다.

## 수취한 OPEN 의 처분

| 항목 | 처분 |
|---|---|
| `OPEN-4C2-MARK-UNEXERCISED` | **닫았다** — 종단 전이 셋이 production relay 에서 port 로 호출된다. 통로를 열지 않았다: `OutboxTransition.To*` 생성자와 `transitionOutbox` 는 여전히 `workflow` 의 `internal` 이고, relay 가 `workflow` 안에 살아서 부를 수 있을 뿐이다. 컴파일 probe 가 그 폐쇄를 상시 확인한다(격리 통로 probe 신설) |
| `OPEN-6D1-CLAIM-CONCURRENCY-TEST` | **닫았다** — 래치 대기의 반환값을 전부 단언한다. 앞 판은 그 값을 버려 「건너뛰었다」와 「막혀서 못 집었다」가 같은 값이었다 |
| 6D-1 「relay 가 inbox 를 dispatch 보다 먼저 기록한다」 | **닫았다** — inbox 는 `Delivered` 뒤에만 기록한다. `Rejected` 경로에서 inbox 행 0 을 단언한다 |
| 6D-1 「DB conflict 막힘 판정이 시한 기반」 | 같은 축을 production 으로 옮겼다 — 주 단언은 **구조**(쥔 동안 못 집음)이고, 래치 반환값이 그 구조 판정이 시한으로 흐려지지 않게 한다 |
| `OPEN-6A3-EVALUATION-COMMIT` | **닫았다** — 커밋 경로 진입점(일회 러너) + `Failed` 를 값으로 받는 처분 |
| 6D 표 6D-2 행 「reclaim → 정확히 한 번 발송」 | 문면 충돌의 정정은 계약이 정본이다. 구현은 **격리**다(ADR 0005 D-3) — 다음 run 이 좌초한 `CLAIMED` 를 `ISOLATED` 로 옮기는 것을 실 DB test 가 잰다 |
| `OPEN-STR-12` | 변경 없음 — 발송 축 셋은 자리지킴이고 **호출되면 던진다**. 「미가용을 값으로」 쪽을 쓰지 않은 이유는 그 값이 행을 종단으로 태우기 때문이다(단방향, 되돌릴 간선 없음) |

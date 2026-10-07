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
| 11 | **임대 해제는 홀더의 생사와 다른 축이다**(R1-M-1 로 문면 정정) — 세션 advisory lock 은 **연결**이 끊기면 서버가 즉시 놓는다. 그 해제는 프로세스 사망뿐 아니라 `pg_terminate_backend`·네트워크 단절로도 일어나므로 **살아 있는 홀더**도 임대를 잃을 수 있다(앞 판 문면의 「반대 방향은 일어나지 않는다」는 거짓이었다 — verifier probe V6b 가 실측). 그래서 relay 는 **네 지점에서 임대를 다시 묻고**(획득 직후 = 고아 목록을 읽기 전 · 고아마다 태우기 전 · claim 전 · 행마다 발송 전 — 셈의 이력은 R2-M-1 이 하나에서 넷, cr T-1 이 다섯, PR #63 finding 7 이 **연속 중복 하나를 걷어** 다시 넷이다) 거짓이면 그 뒤 질의를 돌리지 않고 `LeaseLost`(종료 코드 `FAILED`)로 멈춘다. **확인 밖에 남는 창은 제한 15 가 센다**(R3-L-3 — 앞 판은 여기서 「둘」로 닫았는데 15 가 그것을 일반화했다). 이 줄에만 있는 사실 하나: TCP 반개방이면 서버가 끊김을 keepalive 만료까지 모르고, 그 구간의 다음 소비자는 `Busy` 를 받아 아무것도 하지 않는다 — **안전한 쪽으로** 실패한다 | 상시 | — |
| 12 | **배치 claim 의 손실 증폭**(R1-L-5) — `claim` 이 `limit` 단위 배치라 행 하나의 예외가 같은 배치의 **미시도 행 전부**를 다음 run 의 고아 격리로 보낸다. 임대 상실(`LeaseLost`)도 같은 모양이다 — 멈춘 뒤 남은 행이 격리된다. 행 하나씩 claim 하면 사라지지만 run 당 질의 수가 배치 크기만큼 늘어난다 | 상시 — `bidvector.relay.claim-limit` 를 1 로 두면 증폭이 0 이다(운영 선택) | — |
| 13 | **발송이 한 번도 성공하지 않았고 종단 실패가 있는 run 은 비-0 이고, 하나라도 전달된 run 은 0 이다**(D-6F10-27 ⑦ 의 경계, cr L-3; 문면은 cr R-8 로 술어에 맞췄다 — 앞 판의 「전부 거부·격리」는 술어보다 좁았다). 중복 처리는 전달로 세지 않는다 — 한 통도 보내지 않은 run 은 발송 경로가 살아 있다는 증거가 없다. 부분 실패를 비-0 으로 올리면 정상 운영이 늘 붉어지므로 그 선을 택했다 | 상시 | — |
| 14 | **relay 의 종료 코드 다섯 중 `INCOMPLETE` 의 미지 payload 갈래는 도달 불가다**(cr L-12) — kind 필터 뒤로는 어댑터가 「종류는 맞고 타입은 아닌」 행을 만들 수 없고, 형식을 어긴 행은 복호 fail-closed 로 run 을 멈춘다(그쪽은 `FAILED`). 그 갈래는 심층 방어로 남기고 그 사실을 `RelayExitCode.INCOMPLETE` KDoc 에 적었다. 복호 불가 행 하나가 그 종류의 relay 전체를 매 run 멈추는 성질의 복구는 **앱 권한 밖**이다(`rollback.md` — DBA UPDATE) | 상시 | — |
| 15 | **임대 재확인 밖의 창은 여전히 남는다**(좁힌 것은 창의 **수**가 아니라 **크기**다) — 확인과 그 다음 질의 사이에 연결이 끊기면 그 질의는 임대 없이 돈다. 네 지점은 그 구간을 「격리 전체」·「claim 전체」에서 **질의 하나**로 줄이지만 0 으로 만들지 못한다. 가장 비싼 자리였던 고아 격리는 cr T-1 로 「목록 전체」에서 「행 하나」가 됐다 — 격리는 되돌릴 간선이 없어 그 차이가 크다. 0 으로 만드는 유일한 길은 임대와 작업을 **같은 트랜잭션**에 넣는 것이고, 그러면 T1/T2 분리(at-most-once 의 근거)가 무너진다 | 상시 | — |
| 16 | **커밋 E2E 의 세 test 는 `@Order` 로 순서가 고정돼 있다** — 「순서 독립」을 측정하기 위한 선택이다(행을 남기는 test 를 먼저 돌려 비우기가 실제로 짐을 지게 한다, R2-L-3 실측). 그래서 이 파일은 **다른 순서**에서는 돌아 본 적이 없다 — 각 test 가 자기 전제를 `@BeforeEach` 로 세우므로 어느 순서에서도 서야 하지만, 그 전칭은 **측정되지 않았다**(고정된 한 순서만 측정했다) | 상시 | — |
| 17 | **test 소스에 비밀값 어휘와 같은 모양의 리터럴이 둘 있다** — 커밋 러너의 실패 줄이 예외 메시지를 싣지 않음을 재려고 **가짜 접속 문자열**을 일부러 심었고(그 값이 로그에 없다는 것이 그 test 다), test 컨테이너 자격 리터럴이 하나 있다(저장소의 다른 app E2E 와 같은 형태). 정본 게이트(`leakPatternGate`)의 scanRoot 는 `reports/evidence` 라 test 소스를 보지 않으므로 **게이트가 이것을 막지 않는다** — 참조형 스캔으로 라운드마다 눈으로 확인한다 | 상시 | — |
| 18 | **relay 기동 거부 배선의 구조 단언은 바이트코드 모양 하나에 걸려 있다**(표적 재검증 RT-M-1) — 「배선이 판정 함수를 부른다」·「열거 상수 필드 접근 없음」 둘은 `RelayWiring` 클래스의 `GETSTATIC` 만 보므로, 함수를 부르고 결과를 버린 채 옛 술어를 `when`(합성 `$WhenMappings`)이나 `.name` 비교로 쓰면 둘 다 통과한다. 거동 잠금은 정책표를 배선에 주입해야 하는데 그것은 기동 거부의 입력을 호출자가 고르게 하는 자리라 이 slice 는 열지 않았다. 오늘 production 은 옳다(두 술어가 같은 답). **`OPEN-6F10-RELAY-BOOT-WIRING-LOCK`** — `OPEN-STR-12` 가 실 sender 를 들이며 relay 배선을 다시 짤 때 거동 잠금으로 닫는다 | 상시 | `OPEN-6F10-RELAY-BOOT-WIRING-LOCK` |
| 19 | **`OrphanIsolation.leaseHeld` 는 측정되지 않는다**(RT-L-1) — 상실 시 `true` 로 보고하거나 호출부가 무시해도 초록이다. 호출부의 「claim 전」 확인이 같은 사실을 다시 묻고 실 어댑터의 생존 질의는 연결이 죽으면 다시 참이 되지 않아 **동치 변이**다(결함 아님). 그 값은 보고 정확성(「n 개 태우고 멈춤」 대 「n 개가 전부」)만 진다 | 상시 | — |
| 20 | **일회 러너는 한 프로세스에 하나뿐이다** — 둘 이상이면 기동 실패다(PR #63 finding 1). 러너는 끝나면 JVM 을 끝내므로 둘을 켜면 먼저 끝난 쪽이 나머지를 조용히 못 돌게 한다. guard 는 `ApplicationRunner` 빈 **정의**를 세고 **종류를 모른다** — 그래서 수집 레인 러너와 섞어도 받는다. 대가: 러너 둘을 **의도적으로** 한 프로세스에서 돌리는 배포는 불가능하다(cron 으로 프로세스를 나누는 것이 전제다) | 상시 | — |
| 21 | **`causeCodeOf` 사본이 아직 셋이다**(RT2-L-3 ⓒ 로 문면 통일 — 앞 판은 같은 행에서 「셋」과 「네 사본」을 섞어 적었다) — 넷이었고 이 slice 가 자기 둘을 하나로 합쳐 **셋**이 됐다(이 slice 하나 + 수집 레인 둘, 그쪽은 `app/collection` 아래라 in_scope 밖이다, PR #63 finding 6). 그 셋이 **같은 규율**(메시지 금지·SQLSTATE 까지)을 각자 들고 있으므로 하나가 느슨해지면 그 레인만 샌다 | 상시 | `OPEN-6F10-CAUSE-CODE-DEDUP` |
| 22 | **statement/transaction 모드 pooler 를 지원하지 않는다**(PR #63 finding 3). advisory lock 은 **세션** 범위인데 그 모드의 pooler 는 질의마다 다른 서버 backend 에 붙인다 — 잠금을 잡은 backend 가 반납되면 서버가 잠금을 놓고 다음 소비자도 `Held` 를 받는다(둘이 동시에 돈다). 잠금 인식 probe 가 그 어긋남을 **조용히 넘기지 않는다**(자기 backend 에 그 잠금이 없으면 거짓 — 그 성질을 `PoolerLeaseProbeTest` 가 `DataSource` 프록시로 상설 측정한다, RT2-M-1), 애초에 잠금 획득 자체가 의미를 잃으므로 **지원이 아니다**. 지금 배포는 직접 연결이다 — 그 전제가 바뀌면 lease 설계를 다시 본다 | 상시 — 직접 연결 전제 | — |
| 23 | **pooler 흉내 test 의 프록시가 `InvocationTargetException` 을 풀지 않는다** — 그래서 probe 질의가 **던지는** 경로는 미측정이다. `java.lang.reflect.Method.invoke` 는 대상의 예외를 그 타입으로 감싸 올리므로, 그 질의가 `SQLException` 을 내면 프록시는 `SQLException` 이 아닌 것을 올리고 production 의 `catch (lost: SQLException)` 이 그것을 잡지 못한다. **지금 거짓 초록은 아니다** — 어떤 단언도 그 경로를 지나지 않는다(두 test 는 던지지 않는 경로만 돈다). 그 분기를 재는 test 를 뒤에 더하면 그 자리가 조용히 틀리므로, 더할 때 프록시에 `targetException` 되던지기를 함께 넣는다 | 상시 — 예외 분기 test 를 더할 때까지 | — |
| 24 | **일회 러너 기동 거부 메시지의 러너 이름은 단언하지 않는다** — 수(`2개`)만 단언한다. 구현은 이름을 정렬해 싣지만(`runners.sorted()`) 그 부분이 빠져도 test 가 붉지 않는다. 거부 자체는 걸리므로 **안전 쪽 실패**이고, 잃는 것은 운영자가 「어느 둘이 켜졌나」를 로그에서 바로 보는 편의다 | 상시 | — |


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

R2-H-1 이 앞 판의 이 절을 **과장**으로 판정했다 — 「되읽어 typed 등식」이라고 적었으나 그 등식을 재는 자리가 없었고, 투영의 두 줄을 고정 상수로 바꿔치우는 변이가 test 클래스 242 개에서 전부 초록이었다. 아래는 **지금 실제로 재는 것**만이다.

| 축 | 무엇을 재는가 | 어디서 |
|---|---|---|
| ① 투영 등식 | 요청의 두 값이 payload 에 **그대로** 실린다 — 기본값 아닌 값(개정 7 · `EffectiveFrom.On`)으로 typed 등식 | `OutboxNotificationRequestPortTest` 「요청의 정책 버전과 전략 개정을 그대로 나른다」 |
| ② wire 왕복 | 두 값이 저장 형식을 **왕복**한다 + 축어 골든 | `OutboxPayloadCodecTest`(개정 7·0·1·12·3, 구분자·이스케이프 포함) |
| ③ DB 열 | 그 종류의 행이 실제로 `payload_type = 'NotificationRequested'` 로 저장되고, **같은 종류의** 행을 production 경로가 디코드해 소비한다 | 커밋 E2E 가 열을 직접 단언 · relay DB test 들이 **같은 종류의 행**(test 지원 함수가 codec 으로 심은 것)을 claim→발송까지 지난다. **커밋 run 이 쓴 그 행을 relay 로 한 바퀴 돌리는 test 는 없다**(R3-L-4 로 문면 정정 — 앞 판은 「그 행을」이라고 적었다). 사슬은 조합으로 선다: production 인코드(①) + 형식 왕복(②) + 같은 형식의 행을 소비(③) |
| ④ 거동 | 전략 개정 **하나만** 바꾸면 저장된 payload 가 달라지고, 되돌리면 같아진다 | 커밋 E2E 「전략 개정을 올리면 저장되는 payload 가 달라지고 되돌리면 같아진다」(같은 공고로 세 번 run) |

**타입으로 되읽는 자리가 `app` E2E 가 아닌 이유**: `OutboxPayloadCodec` 은 `adapters` 모듈 `internal` 이고(저장 형식은 어댑터의 것이라는 경계) 그 모듈 test 는 `NotificationRequest`·`Verdict.BidNow` 의 `internal` 생성자 때문에 요청을 **만들 수 없다**(4A/4C 위조 차단). 그래서 ①(타입 등식)은 `workflow`, ②(형식)는 `adapters`, ③④(실 DB)는 `app` 으로 나뉘어 있고 가시성을 완화하지 않았다. 투영 두 줄의 상수 변이를 잡는 것은 ① 이다.

**어느 변이가 어디서 붉어지는가**(R2-H-1 의 요구):

| 변이 | ① | ④ |
|---|---|---|
| `ladderPolicyVersion` → 고정 상수 | RED | RED(저장 문자열에 승인 출처가 없다) |
| `strategyRevision` → `StrategyRevision(1)` | RED | RED(개정을 올려도 payload 가 안 바뀐다) |
| `strategyRevision` → `value + 1` | RED | 초록(결정적 사상이라 「다르다·되돌아온다」가 유지된다) |

**표본 값이 그 표를 지탱한다 — 양쪽으로 쟀다.** ① 의 개정 표본은 **7** 이고 지원 함수의
기본값은 1 이다. 그 자리를 1 로 바꾸면 「상수 1」 변이가 **초록으로 통과한다**(실측: 변이
유지 + 표본만 1 로 → BUILD SUCCESSFUL). ④ 의 두 값(7 → 11)도 둘 다 기본값이 아니고 서로
다르다 — 하나라도 1 이면 같은 모양의 사각이 열린다. 비기본값을 쓰는 것이 이 두 축의
전제이고, 그 전제가 깨지는 쪽을 측정으로 남겼다.

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

## 이 slice 가 신설한 OPEN

| 항목 | 무엇이 남았나 | 왜 지금 닫지 않는가 |
|---|---|---|
| `OPEN-6F10-CLAIM-OBSERVABILITY` | `CLAIMED` 가 얼마나 오래 그 상태였는지 관측할 열이 없다 | 고아 판정이 시각을 쓰지 않으므로 거동에 필요하지 않다 — 운영 관측용이고 마이그레이션 0 결정(A-2 (a)) 안쪽이다 |
| `OPEN-6F10-CLAIM-INDEX` | `claimedEntries` 가 순차 스캔이다 | 같은 이유(마이그레이션 0). 운영 규모의 비용을 **측정하지 않았다** — 그 측정이 선행 조건이다 |
| `OPEN-6F10-CAPACITY-SOURCE` | 활성 투찰 수를 설정에서 받는다 | 실 진행 입찰 계수기가 저장소에 없다. 개찰·낙찰 데이터가 생기는 slice 의 몫이다 |
| `OPEN-6F10-EVALUATION-DOMAIN-WRITE` | 「변한 표 == {outbox}」 단언은 **오늘** 평가에 outbox 밖 write 가 없다는 사실에 기댄다 | 판정 기록 표가 생기는 slice 가 그 단언을 다시 받아야 한다 |
| `OPEN-6F10-SCHEDULER` | cron 이 프로세스를 돌린다(상주 스케줄러 없음) | db-scheduler 도입은 측정된 필요가 없다 — 제한 **20** 의 guard 가 「둘을 켜면 안 된다」를 기동 자리에서 막는다(번호는 RT2-L-3 ⓐ 로 재번호됐다) |
| `OPEN-6F10-CAUSE-CODE-DEDUP` | `causeCodeOf` 사본 셋(이 slice 하나 + 수집 둘) | 수집 쪽 둘은 `app/collection` 아래라 in_scope 밖이다. 넷을 중립 자리로 옮기는 것이 그 OPEN 의 몫이고, 그 자리는 새 패키지(또는 수집 패키지 편집)를 요구한다 |
| `OPEN-6F10-RELAY-BOOT-WIRING-LOCK` | 배선이 기동 거부 판정 함수를 **부르는지**를 바이트코드 모양 하나로 잠근다(의존 있음 + 열거 상수 접근 없음) — 함수를 부르고 **결과를 버리는** 모양은 같은 파일의 `Production` 거부 test 가 받고, 그 둘이 짝이다 | 거동으로 가르려면 정책표를 주입 가능하게 해야 하고 그것은 **운영 배선이 기동 거부의 입력을 밖에서 받는** 구조다 — 거부가 막으려는 것과 같은 축이라 쓰지 않았다. 실 sender 가 들어와(`OPEN-STR-12`) 이 거부 자체가 사라지면 이 OPEN 도 함께 닫힌다 |
| `OPEN-6F7-REASON-CODE-STABILITY` | outbox 에 영속되는 사유 문자열이 Kotlin 합성 `toString()` 이다 | 안정적 `code` 속성을 `bidvector.decision` 에 더하는 별도 slice 의 몫이다(지금은 축어 잠금 + 소진 `when` 이 소리를 낸다) |

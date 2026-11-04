# M9/STR12 설계 검토 — 알림 실발송 채널(Telegram)

## (0) 위협 모델 경계 — 한 문장

이 slice 가 막는 것은 **봇 토큰·chat id 원문이 프로세스 밖으로(로그·예외·evidence·장부) 나가는 것**과
**모호한 발송 결과를 전달·거부로 단정해 at-most-once 장부가 거짓이 되는 것** 둘이다. 막지 않는 것:
Telegram 자신의 가용성·전달 보장, 메시지 내용의 업무적 옳음, 운영 활성화(실 토큰 투입)의 승인 절차.

## (1) 열거인가 구성인가

- **결과 어휘 대응(D-4)** — 열거다. HTTP 상태·예외 타입의 **구성**(범위 + sealed 전이 결과)으로 닫되,
  분류 축 자체는 유한 열거이고 `else` 분기가 `Unknown`(모호) 으로 떨어진다. 즉 **모르는 입력의 기본값이
  「전달」도 「거부」도 아니다** — 새 상태 코드·새 예외 타입이 생겨도 장부가 거짓이 되지 않는다.
- **비밀값 차단** — 구성이다. 문자열 술어(마스킹 정규식)가 아니라 **타입**으로 닫는다: 토큰·chat id 는
  `toString` 이 마스킹된 값 타입만을 지나고, 결과 타입(`DeliveryResult`·`RejectionReason`)에
  **자유 `String` 필드가 하나도 없다**(M4/4E 설계). 그래서 provider 문자열이 장부로 올라가는 **칸이 없다**.
- **기동 검사(D-7)** — 구성이다. 판정 함수는 설정 **값**을 받지 않고 `present: Boolean` 셋만 받는다.

## (2) 새 public 표면 — 값 획득 축 전수

`adapters` 는 모듈 밖에서 참조되지 않는다(`app` 이 조립 경계만 본다). 아래는 **같은 모듈 안에서** 값을
얻을 수 있는 자리 전수다.

| 표면 | 비밀값을 낼 수 있는가 | 막는 것 |
| --- | --- | --- |
| `TelegramBotCredential` / `TelegramChatId` 생성자 | — (값을 **넣는** 자리) | `private constructor` + `of()` 하나, 공백 거부 |
| 위 둘의 `toString()` | **아니오** | 마스킹 문자열 고정(원문 길이도 노출하지 않는다 — `MaskedTarget` 과 달리 길이조차 싣지 않는다) |
| 위 둘의 `equals`/`hashCode` | 아니오 | 값 비교만. 해시는 원문을 복원하지 않는다 |
| 원문 접근자 (`TelegramBotCredential.pathSegment()` · `TelegramChatId.formValue()`) | **예 — 설계된 유일한 출구** | `internal`. 호출자는 URI 조립 한 자리와 form body 조립 한 자리뿐이고, 그 두 산출물은 어느 필드에도 보관되지 않는다(지역 변수) |
| `TelegramSenderSettings` (data class) 의 합성 `toString`/`copy`/`componentN` | 아니오 | 필드 타입이 위 마스킹 타입이라 합성 `toString` 이 마스킹 값을 쓴다 |
| `TelegramNotificationSender.send` 의 반환 | 아니오 | `DeliveryResult` — 자유 문자열 칸 0, target 은 `MaskedTarget` |
| `telegramDeliveryResult(...)` (순수 분류) | 아니오 | 입력이 상태 코드·`TelegramCallOutcome` 이고 **설정을 받지 않는다** |
| `relayBootDecision(env, policy, presence)` | 아니오 | `presence` 는 Boolean 셋이다(값을 받지 않으므로 거부 메시지가 값을 담을 수 없다) |
| `NoticeNotificationFacts` / `JdbcNoticeNotificationFacts` | 아니오 | 공고 표시 사실만. 비밀값 축을 보지 않는다 |
| `NoticeMessageRenderer.render` | 아니오 | 공고 사실 + 설정 도메인만 |
| 예외 전파 | **아니오** | `send` 는 `Exception` 을 전부 삼키고 **catch 본문이 throwable 의 message 를 읽지 않는다**(타입만 본다). `HttpRequest`/`HttpResponse` 의 `toString` 은 URI(=토큰)를 싣는데, 그 두 객체는 메시지 조립에 **한 번도** 쓰이지 않는다 |
| 로그 | 아니오 | 이 패키지는 로거를 쥐지 않는다(`object` 커널 주입 자리도 없다) — 발송 축의 관측은 `RelayReport` 계수와 러너 로그가 진다 |

`object` 싱글턴 커널 주입 자리: 없다. 이 패키지의 모든 협력자는 생성자 주입이고 `object` 는 SQL 상수
하나뿐이다(값 획득 축 아님).

## (2b) 우회 경로와 막는 층

| # | 우회 | 막는 층 |
| --- | --- | --- |
| 1 | 실패 갈래에서 예외를 로그·예외 메시지에 실어 토큰이 담긴 URI 를 흘린다 | `TelegramCredentialLeakTest` — 아홉 갈래 각각에서 결과 `toString` · 이 패키지의 로거 부재(바이트코드) · 되던진 예외 전수에 토큰·chat id **원문 부재**를 단언. JDK 가 URI 를 싣는지 여부에 의존하지 않는다(그 객체를 아예 쓰지 않음을 거동으로 잰다) |
| 2 | 모호한 결과(timeout·5xx·429)를 `Rejected`·`Delivered` 로 접어 재발송·전달 표기한다 | `telegramDeliveryResult` 순수 분류 test 전수 + **변이 ①**(Unknown→Rejected 바꿔치움)이 RED |
| 3 | 멱등 기억을 지워 같은 키가 두 번 발송된다 | 가짜 서버 **수신 계수**로 잰다(`callCount`/`effectCount` 분리, `SenderContractTest` 와 같은 골격) + **변이 ②** RED |
| 4 | 기동 검사를 지워 토큰 없이 Live 로 뜬다 → relay 가 claim 한 뒤 발송 자리에서 터져 행을 영구히 잃는다 | `RelayBootDecisionTest`(결손 조합 전수) + `RelayWiringTest`(바이트코드로 「배선이 판정 함수를 지난다」) + **변이 ④** RED |
| 5 | 환경 이름(`Production`)으로 판정해, 정책표가 `Staging -> Live` 가 되는 날 같은 사각을 공유한 채 뚫린다 | `relayBootDecision` 이 정책표를 인자로 받고 이름을 보지 않는다(기존 cr G-1 성질 유지) + 표 변이 test |
| 6 | 설정에 없는 `RouteKey` 로 chat id 해소 실패를 **조용히 기본 chat 으로** 메운다 | 해소 실패는 네트워크 호출 0 + `Rejected(InvalidTarget)`. 「기본 chat」 경로가 타입에 없다(`Map<RouteKey, TelegramChatId>` 조회 결과가 `null` 이면 그것이 결과다) |
| 7 | 남의 owner 로 route 를 얻어 남의 chat 으로 보낸다 | `ConfiguredRouteDirectory` 가 설정된 owner 가 아니면 **빈 map** → `TargetMissing` → `Suppressed` → `FAILED`. 발송 0 |
| 8 | `parse_mode` 를 켜 공고명의 마크업 문자가 Telegram 파서를 깨거나 다른 뜻이 된다 | `parse_mode` 를 **보내지 않는다**(form body 키 둘뿐) + 수신 본문 키 집합 등식 test |
| 9 | 공고 행이 없을 때 던져 run 전체를 멈추고 남은 행을 좌초시킨다 | 렌더러가 던지지 않는다(아래 (D-6) 결정) + 축약 본문 골든 test |
| 10 | 응답 본문을 신뢰해 `ok:false` 2xx 를 전달로 읽는다 | 2xx 라도 `ok:true` 가 아니면 `Unknown`. 본문 해독 실패도 `Unknown` |
| 11 | 토큰·chat id 를 코드·yml·evidence 에 리터럴로 남긴다 | env 전용 바인딩(기본값 없음) + `grep -rniE -f config/quality/leak-patterns.txt` 를 diff 에 0 |
| 12 | 새 HTTP 클라이언트를 등재 없이 들여 전송 표면 게이트를 비껴간다 | `collection.transport.*` 쌍 등식(클래스↔전송 타입 전수) + 멤버 표면 등재 |

## (D-6) 렌더 실패 처분 — 결정

**결정: 렌더 실패를 발송 결과 어휘로 옮기지 않는다.** 공고 행이 없거나 `ContentRef` 가 공고 식별자 모양이
아니면, 렌더러는 **사실을 주장하지 않는 축약 본문**을 만들고 발송은 평소대로 일어난다(전달되면
`Delivered`).

기각한 둘:

- **`Rejected` 로 옮긴다(계약이 물은 안)** — 기각. ① `Rejected` 의 뜻은 「Telegram 이 확정 거부했다」이고
  (`RejectionReason` 은 `InvalidTarget`·`ProviderDeclined` 둘뿐) 렌더 실패는 그 명제를 **거짓으로** 만든다.
  장부(`FAILED`)가 「채널이 거부했다」로 읽히면 운영자의 처방이 틀린 곳을 향한다. ② 어휘에 「내용을 만들 수
  없다」 칸을 더하는 것은 `workflow` production 변경이라 out_scope 다. ③ 칸을 더하지 않고 옮기려면
  renderer→sender 사이에 **숨은 side channel**(contentRef 로 키를 맞춘 상태 기록)이 필요하고, 그것은 이
  저장소가 거듭 막아 온 「문자열·상태 술어로 닫은 게이트」와 같은 사각을 만든다. 계약 문면의 괄호
  (「렌더 실패 ≠ 발송 실패」)가 가리키는 결론이 이것이다.
- **던진다(자리지킴 셋의 현행 처분)** — 계약이 배제했고, 실측 근거도 그쪽이다: `claimAndSettle` 이
  `RelayAborted` 로 run 을 멈추므로 **그 행과 남은 행 전부**가 `CLAIMED` 에 좌초하고 다음 run 의 고아
  격리가 태운다. 공고 한 건의 보존 파기가 그날의 알림 전부를 잃게 만든다.

축약 본문이 정직한 이유: 운영자가 받는 문장은 「이 공고에 대한 판정 알림이 있었고, 저장된 공고 정보는
남아 있지 않다(보존 파기 또는 미수집)」다 — 이것은 참이고, 금액·마감을 **지어내지 않는다**(`값 없음` 도
적지 않는다: 그 칸 자체를 쓰지 않는다). 공고 행이 있는데 칸 하나가 빈 경우는 다른 축이다 — 그때는 그 칸만
`값 없음` 으로 적는다(웹 화면 `amountDisplayOf` 와 같은 규칙). 두 경우를 본문이 **다른 문장**으로 가른다.

## (3) 과잉·미달

- **과잉으로 보고 하지 않는 것**: 429 재시도·백오프(D-4 가 재시도 0 을 못박았다) · 영속 멱등 기억(D-5,
  ADR 0005 §7.6 B-2 창을 넓히지도 좁히지도 않는다) · Email 채널 · 알림 채널 선택 UI · 발송 metric.
- **미달로 남기는 것(알려진 제한)**: ① 재기동을 건너는 멱등 기억이 없다(B-2 창 — run 내부만 보장) ②
  실제 Telegram 엔드포인트를 상대로 한 측정은 0 이다(가짜 서버만) ③ 운영 활성화(실 토큰·timer 설치)는
  이 slice 밖이다 ④ `docker/prod.env.template` 에 키를 더하지 않았다 — 그 파일의 키 집합은
  `tools/prod-env-keys-check.sh` 가 compose 세 파일의 `${VAR` 참조 집합과 **등식**으로 재고 compose 는
  out_scope 다. 대신 runbook 의 systemd unit 이 env 키 이름을 든다(relay 러너는 compose 가 아니라 일회
  러너다 — §9 선례와 같은 자리).

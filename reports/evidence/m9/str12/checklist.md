# M9/STR12 — 점검 목록과 알려진 제한

## 1. 결정 대조 (D-STR12-1 ~ D-10)

| ID | 충족 | 어디서 |
| --- | --- | --- |
| D-1 `OPEN-STR-12` 분할 | 충족 | capability map 의 `OPEN-STR-12` 행에 발송 축 닫음 · `OPEN-STR-12-INTERACTIVE` 신설 행(둘 다 §G3 OPEN 표와 「결정 주체」 표) |
| D-2 경로는 설정에서 · chat id 는 `RouteKey` 에 없다 · 마이그레이션 0 | 충족 | `ConfiguredRouteDirectory`(owner·슬러그만) · 슬러그 → chat id 해소는 sender 설정 · Email `enabled=false` · 새 migration 0 |
| D-3 비밀값 env 전용 · 원문 0 | 충족 | `TelegramBotCredential`·`TelegramChatId`(마스킹 `toString`, 원문 출구 `internal` 하나) · 설정 기본값 0 · 누출 test 열한 갈래 · 이 패키지 로거 0(바이트코드 단언) |
| D-4 결과 어휘 대응 | 충족 | `telegramDeliveryResult`(순수) + `telegramStatusVerdict` + `telegramFailureOutcome`. 429 를 4xx 범위보다 **먼저** 본다 |
| D-5 sender 멱등 계약 | 충족 | `TelegramSenderContractTest` — `SenderContractTest` 와 같은 골격, 효과 계수를 가짜 서버 수신 수로 센다. 영속 기억 0(마지막 칸이 그 경계를 거동으로 적는다) |
| D-6 렌더러 | 충족 | `NoticeMessageRenderer` — 한국어 평문, `parse_mode` 미사용, KST·원 단위 콤마, 링크 도메인은 설정. 렌더 실패 처분은 설계 검토가 정했다(아래 §2) |
| D-7 기동 거부 해제 규칙 | 충족 | 자리지킴 셋 삭제 + `relayBootDecision(환경, 정책표, 설정 존재 여부)`. 값 판정 구조 유지, 판정 내용만 바뀜 |
| D-8 JDK `HttpClient` · 유한 timeout | 충족 | 새 의존성 0. 연결 5s · 응답 10s 기본, 둘 다 설정 가능하고 `init` 이 유한 양수를 요구 |
| D-9 실제 Telegram 호출 0 | 충족 | 전 test 가 `com.sun.net.httpserver` 가짜 서버 또는 닫힌 포트를 상대로 돈다. **이름 해소 실패 갈래만 거동이 아니라 타입으로** 쟀다 — 그 갈래를 거동으로 재려면 통제 밖 resolver 에 의존한다 |
| D-10 운영 활성화 문서 | **좁혀짐** | runbook 두 자리 수정 + `m7-7f-production.md` §13 신설(봇 만들기 → env 세 값 → systemd timer 예시 → 첫 발송 확인 → 끄는 법 → 어긋날 때). **prod env 템플릿은 편집하지 않았다** — 아래 §3 ④ |

## 2. 설계 판단이 갈린 자리

**렌더 실패를 발송 결과 어휘로 옮기지 않았다**(계약이 물은 것, design-review.md D-6 절). 계약은 「공고 행이
없으면 던지지 않고 `Rejected` 로 만드는 경로가 정직한지」를 설계 검토에 넘겼고, 검토는 **정직하지 않다**고
판정했다 — `Rejected` 의 뜻은 「Telegram 이 확정 거부했다」이고 그 명제가 거짓이 된다. 대신 **사실을
주장하지 않는 축약 본문**을 만들어 실제로 보낸다(전달되면 `Delivered`). 기각 근거 셋과 대안 비교는
design-review.md 에 있다.

## 3. 알려진 제한

1. **재기동을 건너는 멱등 기억이 없다.** sender 의 기억은 프로세스 안이라 한 run 안의 재호출만 막는다 —
   발송 뒤 종단 전이 전 크래시가 남기는 재발송 창(ADR 0005 §7.6 B-2)은 **그대로**다(넓히지도 좁히지도
   않았다, D-5). 그 창이 이제 **실물**이라는 사실을 ADR addendum 이 적는다. 경계는
   `TelegramSenderContractTest` 의 마지막 칸이 거동으로 적는다.
2. **실제 Telegram 엔드포인트를 상대로 한 측정이 0 이다**(D-9). 가짜 서버가 Bot API 의 응답 **모양**을
   흉내 내지만 그 모양이 실제와 같다는 보증은 이 slice 밖이다 — 첫 실발송이 그 첫 측정이다(runbook §13.4).
3. **운영 활성화를 하지 않았다** — 실 토큰 투입과 systemd timer 설치는 별도 운영자 확인 항목이다.
   `tools/prod-notification-relay.sh` 도 **없다**(runbook §13.3 이 그 사실을 적는다) — 훑기 러너
   (`prod-notification-sweep.sh`)와 같은 모양으로 만드는 것이 활성화 slice 의 몫이다.
4. **`docker/prod.env.template` 에 키를 더하지 않았다.** 그 파일의 키 집합은
   `tools/prod-env-keys-check.sh` 가 compose 세 파일의 참조 집합과 **등식**으로 재고 그 compose 들은
   out_scope 다 — 템플릿에만 더하면 그 검사가 붉어진다(죽은 키). relay 는 compose 서비스가 아니라 일회
   러너이므로 env 키 이름은 runbook §13.2 가 든다. D-10 문면과 갈린 자리다.
5. **거부 어휘가 두 값뿐이다**(`InvalidTarget`·`ProviderDeclined`). 「요청이 Telegram 에 닿지 못했다」
   (이름 해소 실패·연결 거부)에 맞는 칸이 없어 `ProviderDeclined` 로 보낸다 — 어휘를 늘리는 것은
   `workflow` production 변경이라 out_scope 다. 운영자가 보는 처방은 둘 다 같다(설정을 고친다).
6. **발송 축의 관측이 계수뿐이다.** 이 패키지는 로거를 쥐지 않으므로(누출 방어) 「왜 거부됐는지」가
   로그에 남지 않는다. 운영자는 `RelayReport` 계수와 runbook §13.6 의 표로 읽는다. 사유 코드만 싣는
   로그 한 줄을 더하는 것은 다음 slice 의 후보다(그때 누출 축을 다시 본다).
7. **`parse_mode` 를 쓰지 않으므로 서식이 없다** — 굵게·링크 미리보기 억제 같은 것이 없다. 이스케이프
   사고를 0 으로 두기 위한 교환이다(D-6).
8. **단일 경로·단일 운영자**다(`OPEN-STR-07`). 경로가 하나라 `RouteKey -> chat id` 가 map 이 아니고,
   여러 수신자·채널 우선순위(`OPEN-NOTI-07`)는 여전히 없다.
9. **Email 채널과 채널 선택 UI 가 없다**(계약 out_scope). 대화형 편집 채택은
   `OPEN-STR-12-INTERACTIVE` 로 분리돼 `후속` 이다.
10. **마감 시각 표기의 구역 해석은 수집 축의 것이다.** 알림 본문은 저장된 `deadline_at` 을 KST 로 적을
    뿐이고, 수집이 원문을 어느 구역으로 읽었는지는 이 slice 가 재지 않는다 — E2E 는 그래서 값이 아니라
    **표기 모양**(`yyyy-MM-dd HH:mm KST`)을 못박는다.
11. **누출 sweep 이 설정 문자열을 갈래마다 보지 않는다** — 변이 ③ 이 드러냈다(붉은 것이 **1 건**).
    갈래 열하나를 도는 쪽은 **결과 문자열**만 보고, 자격 `toString` 의 마스킹은 전용 test 한 자리가
    지킨다. 방어가 없는 것은 아니지만(그 한 자리가 실제로 붉다) 갈래마다 재는 것보다 좁다 — sweep 에
    `settings.toString` 을 더하면 열한 자리가 함께 붉어진다. 다음 라운드 후보이고 측정은
    `commands.md` §3 에 있다.
12. **app 층의 RED 가 선행 측정으로 기록되지 않았다.** 개발 중 컴파일 RED 를 보았으나 그 실행의 출력을
    잃었고(파이프가 `head` 로 버퍼링돼 0 바이트), **사후 재현도 깨끗하게 되지 않는다** — app 변경이
    모듈을 넘는 서명 변경이라 production 을 되돌리면 그 되돌림이 배선·조립로 연쇄해 「test 만 새 상태」
    트리를 만들 수 없다(두 번 시도, 둘 다 production 에서 RED). adapters 층의 RED 는 선행 측정으로
    기록됐고, app 층의 거동 방어는 **변이 ③④**(산출물 위 직접 측정)가 든다. `commands.md` §1b.
13. **전건 `check` · `container` job · S-20 을 로컬에서 돌리지 않았다**(팀장 공지 2026-11-05 — 호스트
    빌드 큐 포화). 그 셋의 정본은 **PR #127 의 CI** 이고 결과는 `commands.md` §6 이 든다. 로컬에서 든
    것은 표적 `--tests` 와 변이 넷뿐이다 — 「부분 게이트는 안 돌린 것과 같다」는 규율대로, 이 문서는
    로컬 측정을 전건의 갈음으로 들지 않는다.

## 4. rollback · 되돌림

`rollback.md` 참고. 머지 뒤에는 PR revert 다.

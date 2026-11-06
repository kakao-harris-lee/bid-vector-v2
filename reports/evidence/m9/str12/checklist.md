# M9/STR12 — 점검 목록과 알려진 제한

## 1. 결정 대조 (D-STR12-1 ~ D-10)

| ID | 충족 | 어디서 |
| --- | --- | --- |
| D-1 `OPEN-STR-12` 분할 | 충족 | capability map 의 `OPEN-STR-12` 행에 발송 축 닫음 · `OPEN-STR-12-INTERACTIVE` 신설 행(둘 다 §G3 OPEN 표와 「결정 주체」 표) |
| D-2 경로는 설정에서 · chat id 는 `RouteKey` 에 없다 · 마이그레이션 0 | 충족 | `ConfiguredRouteDirectory`(owner·슬러그만) · 슬러그 → chat id 해소는 sender 설정 · Email `enabled=false` · 새 migration 0 |
| D-3 비밀값 env 전용 · 원문 0 | 충족 | 값 타입 둘이 마스킹 `toString`·원문 출구 `internal` 하나 · **env 원문을 쥐는 app 설정 객체 둘도 `data class` 가 아니고 마스킹한다**(v H-1) · 누출 sweep 이 갈래마다 **결과와 설정 문자열을 함께** 본다 · 이 패키지 로거 0(바이트코드 단언) |
| D-4 결과 어휘 대응 | 충족 | `telegramDeliveryResult`(순수) + `telegramStatusVerdict` + `telegramFailureOutcome`. 429 를 4xx 범위보다 **먼저** 본다. **자격 모양을 생성 시점에 막아** URI 불가 문자가 `Unknown` 으로 오분류되는 길을 닫았다(v M-1) |
| D-5 sender 멱등 계약 | 충족 | `TelegramSenderContractTest` — `SenderContractTest` 와 같은 골격, 효과 계수를 가짜 서버 수신 수로 센다. 영속 기억 0(마지막 칸이 그 경계를 거동으로 적는다) |
| D-6 렌더러 | 충족 | 한국어 평문, `parse_mode` 미사용(키 집합 등식), KST·원 단위 콤마, 링크 도메인은 설정, **경로 조각 인코딩**(v L-1). 본문은 Bot API 상한에서 자르고 표지를 남긴다(cr M-6) |
| D-7 기동 거부 해제 규칙 | 충족 | 자리지킴 셋 삭제 + `relayBootDecision(환경, 정책표, 존재 여부, 채널, 활성 route)`. 거부가 **셋**이다 — 설정 결손 · **활성 route 없음**(cr M-2) · 자격 모양. 셋 다 값이 아니라 칸 이름·채널 이름·길이만 나른다 |
| D-8 JDK `HttpClient` · 유한 timeout | 충족 | 새 의존성 0. 연결 5s · 응답 10s 기본, 둘 다 설정 가능하고 `init` 이 유한 양수를 요구 |
| D-9 실제 Telegram 호출 0 | 충족 | 전 test 가 가짜 서버 또는 닫힌 포트를 상대로 돈다. **이름 해소 실패 갈래만 거동이 아니라 타입으로** 쟀다 |
| D-10 운영 활성화 문서 | **좁혀짐** | runbook 두 자리 + `m7-7f-production.md` §13. 수정 라운드가 §13 의 세 안전 축을 고쳤다(cr M-3·M-4·M-5) — argv 축의 구체 명령 · env 파일 둘의 전달 요구 · 첫 run `claim-limit=1`. 거부 사유 셋을 §13.6 과 m6-6e §5.2 표로. **prod env 템플릿은 편집하지 않았다** — 아래 §3 ④ |

## 2. 설계 판단이 갈린 자리

**렌더 실패를 발송 결과 어휘로 옮기지 않았다**(계약이 물은 것, design-review.md D-6 절). 계약은 「공고 행이
없으면 던지지 않고 `Rejected` 로 만드는 경로가 정직한지」를 설계 검토에 넘겼고, 검토는 **정직하지 않다**고
판정했다 — `Rejected` 의 뜻은 「Telegram 이 확정 거부했다」이고 그 명제가 거짓이 된다. 대신 **사실을
주장하지 않는 축약 본문**을 만들어 실제로 보낸다(전달되면 `Delivered`). 기각 근거 셋과 대안 비교는
design-review.md 에 있다.

**owner 격리는 코드로 세우지 않고 주장을 내렸다**(cr M-1, 아래 §3 ⑫). 리뷰가 준 둘 중 「설정 owner 를
별 축에서 받아 등식을 기동 검사로 올린다」를 택하지 않았다 — 단일 운영자 배포에서 출처를 둘로 늘리는
것은 방어가 아니라 **오설정 표면**이다. 지키는 값이 없는 자리에 틀릴 수 있는 설정을 더하지 않는다.

## 3. 알려진 제한

1. **재기동을 건너는 멱등 기억이 없다.** sender 의 기억은 프로세스 안이라 한 run 안의 재호출만 막는다 —
   발송 뒤 종단 전이 전 크래시가 남기는 재발송 창(ADR 0005 §7.6 B-2)은 **그대로**다(D-5). 그 창이 이제
   **실물**이라는 사실을 ADR addendum 이 적는다.
2. **실제 Telegram 엔드포인트를 상대로 한 측정이 0 이다**(D-9). 가짜 서버가 Bot API 의 응답 **모양**을
   흉내 내지만 그 모양이 실제와 같다는 보증은 이 slice 밖이다 — 첫 실발송이 그 첫 측정이다(runbook §13.4).
3. **운영 활성화를 하지 않았다** — 실 토큰 투입과 systemd timer 설치는 별도 운영자 확인 항목이다.
4. **`docker/prod.env.template` 에 키를 더하지 않았다.** 그 파일의 키 집합은 compose 세 파일의 참조
   집합과 **등식**으로 재어지고 그 compose 들은 out_scope 다 — 템플릿에만 더하면 그 검사가 죽은 키로
   붉어진다. env 키 이름은 runbook §13.2 가 든다. D-10 문면과 갈린 자리다.
5. **거부 어휘가 두 값뿐이다**(`InvalidTarget`·`ProviderDeclined`). 「요청이 Telegram 에 닿지 못했다」에
   맞는 칸이 없어 `ProviderDeclined` 로 보낸다 — 어휘를 늘리는 것은 `workflow` production 변경이라
   out_scope 다. 운영자가 보는 처방은 둘 다 같다.
6. **발송 축의 관측이 계수뿐이다.** 이 패키지는 로거를 쥐지 않으므로(누출 방어) 「왜 거부됐는지」가
   로그에 남지 않는다. 운영자는 `RelayReport` 계수와 runbook §13.6 표로 읽는다.
7. **`parse_mode` 를 쓰지 않으므로 서식이 없다** — 이스케이프 사고를 0 으로 두기 위한 교환이다(D-6).
8. **단일 경로·단일 운영자**다(`OPEN-STR-07`). 여러 수신자·채널 우선순위(`OPEN-NOTI-07`)는 없다.
9. **Email 채널과 채널 선택 UI 가 없다**(계약 out_scope). 대화형 편집 채택은
   `OPEN-STR-12-INTERACTIVE` 로 분리돼 `후속` 이다.
10. **마감 시각 표기의 구역 해석은 수집 축의 것이다.** E2E 는 값이 아니라 **표기 모양**을 못박는다.
11. ~~누출 sweep 이 설정 문자열을 갈래마다 보지 않는다~~ → **해소**(v H-1). sweep 이 갈래마다 결과와
    설정 문자열을 함께 본다. 다만 앞 판 evidence 가 적은 「고치면 **열한 자리**가 붉어진다」는 **틀린
    예측**이었다 — sweep 은 `@Test` 하나가 11 갈래를 도는 fail-fast 모양이라 붉는 test 수는 어느 쪽이든
    1 이다. 실측은 **1 → 3**(전용 toString test · sweep · 양성 대조)이고 바뀐 것은 test 수가 아니라
    **덮이는 축**이다(`commands.md` §3).
12. **owner 격리는 방어가 아니다**(cr M-1). production 에서 `ConfiguredRouteDirectory` 는 `target.owner`
    로 세워지고 조회도 같은 값이라 `owner != configuredOwner` 갈래는 **도달하지 않는다**. 실제 격리는
    단일 운영자 전제(`OPEN-STR-07`)와 「intent 가 payload 의 owner 를 보지 않는다」가 진다 — 후자는
    **행의 원래 수신자와 무관하게 설정된 owner 로 찍는다**는 뜻이다. test 는 「정의된 거동」으로 남겼고
    설계 검토 우회 7 의 주장을 내렸다.
13. **멱등·클라이언트·임대 축의 전제 넷**(cr L-1·L-2·L-3·L-5): `computeIfAbsent` 의 매핑 함수가 최대
    15초의 블로킹 호출을 돌고, 멱등 기억에 명시 상한이 없고(한 프로세스의 claim 수가 사실상의 상한),
    `HttpClient` 를 닫지 않고, 억제 반환이 임대를 묻지 않아 `LeaseBusy` 와 갈린다. 넷 다 **일회 러너
    전제**에서 실해가 0 이고 KDoc 이 그 전제를 적는다 — **상주 조립으로 가는 slice 가 넷을 함께 본다.**
14. **`400` 의 사유 축을 가르지 않는다**(cr M-6 둘째 절). 본문 길이는 상한으로 닫았지만 400 은 여전히
    「대상 거부」 한 칸이다 — 사유 축 분해는 `workflow` 어휘 변경이라 out_scope 다. runbook §13.6 의
    `failed>0` 행에서 길이를 사유에서 뺐다(상한이 닫았으므로).
15. **`tools/prod-notification-relay.sh` 가 없다.** 그 스크립트가 `relay.env` 를 **컨테이너까지** 넘겨야
    한다는 요구를 §13.3 이 명시했지만(cr M-4), 스크립트가 없으므로 **그 요구를 재는 게이트도 test 도
    없다**. 활성화 slice 가 그것을 함께 만든다.
16. **수정 라운드가 「test 먼저」를 지키지 않았다.** 리뷰 지적을 받아 코드를 먼저 고치고 test 를 뒤에
    썼다. 그 라운드가 더한 방어 넷의 근거는 선행 RED 이 아니라 **산출물 위의 변이 ⑤⑥⑦⑧** 이다
    (`commands.md` §1·§3). 변이가 같은 명제를 잠그지만 순서가 뒤바뀐 것은 사실이고 갈음으로 적지 않는다.
17. **app 층 첫 판의 RED 가 선행 측정으로 기록되지 않았다**(`commands.md` §1b — 사후 재현도 깨끗하게
    되지 않는다). 그 층의 거동 방어는 변이 ④⑦⑧ 이 든다.
18. **리뷰 지적 하나를 확인 없이 적용해 컴파일을 깼다**(cr L-8). 그 파일은 도메인 `Clock` 을 이미
    import 해 쓰고 있어 `java.time.Clock` 을 더하면 이름이 모호해진다 — 되돌리고 **왜 전규정 이름이
    맞는지**를 주석으로 남겼다. **이 라운드에 커밋한 트리가 컴파일되지 않은 구간이 있었다.**
19. **전건 `check` · `container` job · S-20 을 로컬에서 돌리지 않았다**(팀장 공지 2026-11-05 — 호스트
    빌드 큐 포화). 직전 CI run 은 `check` fail 이고 그 실패 전수가 **이 라운드가 고친 두 게이트의 네
    건뿐**임을 artifact 결과 XML 로 확인했다(그 밖의 red 0 — cr Q-1 의 답). 그러나 그 run 에서
    `qualityBaseline` 과 S-20 step 은 `check` 실패로 **skipped** 였다 — 그 둘은 이 브랜치에서 **아직
    한 번도 돌지 않았다.** 이 라운드의 전건 정본은 **팀장이 push 할 때 도는 다음 CI run** 이다.
20. **`AppHttpDependencyGateTest.kt` 가 계약 밖이었다.** 게이트 등재의 반쪽(면제 래칫 상수)이
    `config/quality/**` 가 아니라 그 test 파일에 산다. `scope.md` in_scope 에 사유와 함께 등재했고
    **팀장 확인 대상**이다.

## 4. rollback · 되돌림

`rollback.md` 참고. 머지 뒤에는 PR revert 다.

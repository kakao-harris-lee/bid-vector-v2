# M9 / STR12 — 알림 실발송 채널(Telegram)

- base: main `79f3e5c4` · 브랜치 `feature/m9-str12-telegram` · worktree `bid-vector-v2-m9-str12`
- 근거: M6 종결 open-inventory §2 「`OPEN-STR-12`(발송 채널) — outbox 까지는 6F-7 이 냈고 채널은 그 뒤」 ·
  M7 R-4 「채널 발송은 `OPEN-STR-12` 결정 뒤 별도 활성화」.
- **사용자 결정 2026-11-05**: 채널 = **Telegram**(Bot API `sendMessage` 한 방향 발송). Email 은 뒤 slice.
  **운영 활성화(실 토큰·실 발송)는 이 slice 밖** — 코드·runbook 까지 만들고, 켜는 것은 따로 확인한다.

## 결정 (D-STR12-*)

| ID | 결정 |
| --- | --- |
| D-1 | `OPEN-STR-12` 를 둘로 나눈다. **발송 축**(실 sender·렌더러·경로)은 이 slice 가 닫는다. **대화형 전략 편집 채택 축**(원래 질문 — STR-11)은 `OPEN-STR-12-INTERACTIVE` 로 분리해 `후속` 유지(capability map 의 그 행에 분리 사실을 적는다). |
| D-2 | **경로는 설정에서 온다**(단일 운영자, `OPEN-STR-07` 단일 회사). `RouteDirectory` 구현은 `bidvector.relay.owner` 와 같은 축의 설정을 읽고, `RouteKey` 는 슬러그(`telegram-primary` 같은 이름)이며 **chat id 는 RouteKey 에 넣지 않는다** — 어댑터가 RouteKey → chat id 를 설정에서 해소한다. **마이그레이션 0.** Email 경로는 `enabled=false`. |
| D-3 | **비밀값**: 봇 토큰·chat id 는 env 로만 들어온다(`BIDVECTOR_…_BOT_CREDENTIAL` · `BIDVECTOR_…_CHAT_ID` 모양, 최종 이름은 구현이 정하고 runbook·prod env 템플릿에 같은 이름으로). **로그·예외 메시지·`toString`·evidence 에 토큰·chat id 원문 0** — 토큰을 담는 URL 을 예외에 싣지 않는다(JDK `HttpClient` 예외가 URI 를 싣는지 실측하고 막는다). test 로 잠근다. |
| D-4 | **결과 어휘 대응**(D-6F10-12 해석표 그대로): HTTP 2xx + `ok:true` → `Delivered` · 요청이 Telegram 에 **닿지 않았음이 확정**(DNS·연결 거부 등 송신 전 실패) 또는 4xx 확정 거부(400 chat not found · 403 blocked · 401 토큰 무효) → `Rejected` · 그 밖(timeout·5xx·429·응답 해독 불가·송신 뒤 연결 끊김) → `Unknown`(ISOLATED — 모호는 재발송하지 않는다, at-most-once). 429 재시도 없음. |
| D-5 | **sender 멱등 계약**: `NotificationSender` KDoc 의 「같은 key 재호출 효과 0」을 실 어댑터가 `SenderContractTest` 와 같은 골격의 test 로 증명한다(그 KDoc 의 요구). 프로세스 안 결과 기억으로 충족하되 **영속 기억은 만들지 않는다**(재기동 건너 재발송 창은 ADR 0005 §7.6 의 알려진 B-2 창 — 넓히지도 좁히지도 않는다). |
| D-6 | **렌더러**: `ContentRef` 는 `noticeId` 다(`RelayOutboxNotifications.intentFor`). 렌더러는 그 공고의 표시 사실(공고명·발주기관·마감 시각·기초금액 등 화면 C 가 이미 쓰는 값)을 읽어 **한국어 평문**을 만든다 — 금액·시각 표기는 웹 화면과 같은 규칙(KST, 원 단위 콤마). 공고 행이 없으면 **던지지 않고** 결과를 `Rejected` 로 만드는 경로가 정직한지 설계 검토가 정한다(렌더 실패 ≠ 발송 실패). 링크는 `https://<도메인>/…` 공고 상세 경로, 도메인은 설정. Telegram `parse_mode` 는 쓰지 않는다(이스케이프 사고 0). |
| D-7 | **기동 거부 해제 규칙**: 자리지킴 셋(`Unavailable*`)과 `RelayWiring` 의 Live 거부는 **함께** 사라지고, 대신 **Live 모드인데 Telegram 설정(토큰·chat id·도메인)이 하나라도 비면 기동 실패**다. Live 가 아니면(개발·스테이징) 지금처럼 claim 0 이고 설정 없이 뜬다. `relayBootDecision` 의 값 판정 구조(cr R-2)는 유지하되 판정 내용이 바뀐다. |
| D-8 | HTTP 클라이언트는 JDK `java.net.http.HttpClient`(새 의존성 0). 연결·응답 timeout 은 설정 가능한 유한값(기본 예: 연결 5s · 응답 10s). |
| D-9 | **실제 Telegram 호출 0** — test 는 전부 로컬 가짜 서버(JDK `com.sun.net.httpserver.HttpServer` 등)로. base URL 은 설정(`https://api.telegram.org` 기본). |
| D-10 | **운영 활성화 문서**: `docs/runbook/m6-6e-operations.md` 의 「실 발송 채널이 없다」 두 자리를 고치고, 활성화 절차(봇 만들기 → env 두 값 → relay·평가 일회 러너의 systemd timer 예시 → 첫 발송 확인 → 끄는 법)를 `docs/runbook/m7-7f-production.md` 에 새 절로. prod env 템플릿(있으면)에 빈 키 추가. **timer 를 실제로 설치하지 않는다.** |

## in_scope

- `adapters/src/main/kotlin/bidvector/adapters/relay/**` (자리지킴 제거 · 조립 경계 갱신)
- **`adapters/src/main/kotlin/bidvector/adapters/notification/**` — 구현이 고른 새 패키지**(발송·렌더·라우팅·공고 사실 읽기). `relay` 아래에 두지 않은 이유: `RelayAdapterDependencyTest` 의 허용 루트는 「조립이 `workflow.notification` 과 `adapters.event` 를 **둘 다** 본다」를 뜻하고, 거기에 공고 표 읽기·HTTP 전송을 더하면 그 등식이 무엇을 말하는지 흐려진다. 새 패키지는 자기 게이트(`NotificationAdapterDependencyTest`)를 지고, `relay` 의 허용 루트에는 이 한 좌표만 더한다.
- `adapters/src/test/kotlin/bidvector/adapters/relay/**` · `adapters/src/test/kotlin/bidvector/adapters/notification/**`
- `adapters/src/test/kotlin/bidvector/adapters/e2e/**` (실 sender 조립 E2E — 6D-2 가 「production 조립 E2E(실 sender)는 `OPEN-STR-12` 뒤」로 넘긴 것: 가짜 Telegram 서버를 상대로 평가→outbox→relay→발송→DELIVERED·inbox 한 줄)
- `app/src/main/kotlin/bidvector/app/wiring/RelayWiring.kt` · `app/src/main/kotlin/bidvector/app/relay/**` · 그 test
- ~~`app/src/main/resources/application*.yml|properties`~~ — **쓰지 않았다.** 이 저장소에는 `application.yml` 이 하나도 없고 설정은 전부 env 다. 환경 기본값(base URL·timeout 둘·route 슬러그)은 **그 저장소 선례 그대로** `@ConfigurationProperties` 클래스의 기본 인자에 둔다(`OpeningCollectionProperties` 의 경로 기본값과 같은 자리) — 새 설정 파일을 하나 들여 기동 경로의 전제를 늘리지 않는다.
- `config/quality/**` 의 게이트 등재 — 새 test 클래스 `gate-tests.properties` 등재(만드는 커밋에서 같이) **와 `architecture-policy.properties` 의 전송 표면 쌍·멤버 등재**(새 HTTP 클라이언트 보유자가 생기므로 `collection.transport.*` 등식이 그것을 요구한다)
- `docs/runbook/m6-6e-operations.md` · `docs/runbook/m7-7f-production.md`. **`docker/prod.env.template` 은 편집하지 않았다** — 그 파일의 키 집합은 `tools/prod-env-keys-check.sh` 가 compose 세 파일의 `${VAR` 참조 집합과 **등식**으로 재고 그 compose 들은 out_scope 다. relay 는 compose 서비스가 아니라 일회 러너이므로 env 키 이름은 runbook 의 systemd unit 이 든다(§9 선례와 같은 자리).
- `docs/discovery/capability-map.md` (`OPEN-STR-12` 행: 발송 축 닫음 · `OPEN-STR-12-INTERACTIVE` 분리) · `docs/adr/0005-domain-events-and-outbox.md` (필요하면 §7 addendum 한 단락)
- `reports/evidence/m9/str12/**`

## out_scope

- `workflow/**` production 의 port·어휘·전이표 변경(필요해지면 멈추고 보고) · DB 마이그레이션 · Email 채널 · 대화형 편집 · 웹 화면(알림 채널 선택 UI) · 실 Telegram 호출 · 운영 host 변경.

## acceptance

- CI `.github/workflows/ci.yml` 의 Kotlin `check` job 명령 그대로 exit 0(qualityBaseline·S-20 포함), container job 은 production diff 가 있으므로 그 job 명령 그대로.
- 표적 test(전부 RED 선행 실측을 evidence 한 줄로):
  ① 결과 대응 D-4 전 갈래(2xx·400·401·403·429·5xx·timeout·연결 거부·해독 불가) ② 멱등 계약(같은 key 재호출 시 가짜 서버 수신 1) ③ 비밀값 누출 0 — 실패 갈래마다 예외 메시지·로그·`toString` 에 토큰·chat id 문자열 부재 ④ Live + 설정 결손 → 기동 실패, 비-Live → 설정 없이 기동·claim 0 ⑤ 렌더 문면(금액·시각 표기) 골든 ⑥ E2E 한 줄(DELIVERED 1 · inbox 1 · 가짜 서버 수신 본문이 렌더 결과와 같음).
- **변이**(바꿔치우기): 결과 대응의 Unknown→Rejected 바꿔치움 · 멱등 기억 제거 · 토큰 마스킹 제거 · 기동 검사 제거 — 각각 RED.
- `grep -rniE -f config/quality/leak-patterns.txt` 를 diff 에 0.

## rollback

in_scope 경로 한정 `git restore --source=79f3e5c4 --staged --worktree -- <경로들>` + 새 파일 삭제. 목록은 `git diff --name-status 79f3e5c4..HEAD` 기계 산출을 `rollback.md` 에. 머지 뒤는 PR revert.

## 리뷰 레인

verifier(opus) + code-reviewer(sonnet 명시) 병렬. 비밀값 취급 경로이지만 Codex 는 운영자가 따로 요청할 때만(프로젝트 규칙).

## 하네스 레인 변경

없음(게이트 등재 추가만 — in_scope 의 `config/quality/**`).

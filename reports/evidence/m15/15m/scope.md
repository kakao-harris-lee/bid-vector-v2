# M15 / 15M — 훑기 실패 경보 (systemd OnFailure → Telegram 한 줄)

```yaml
milestone: M15
slice: 15m-sweep-failure-alert
base_sha: a189e8c58c8a438e2685c8daae8f83b835b925ba   # main, PR #199 머지 커밋(아래 「base 확인」)
head_sha: <리뷰 요청 시점에 기입>                      # 이 파일을 담은 커밋이 그 SHA 라 자기 안에 적을 수 없다
in_scope:
  - tools/prod-alert-telegram.sh              # 신설 — OnFailure 가 부르는 한 줄 경보
  - tools/prod-alert-check.sh                 # 신설 — 가짜 Telegram 서버 앞에서 재는 거동 검사
  - .github/workflows/ci.yml                  # container job 에 S-27e 한 step
  - docs/runbook/m7-7f-production.md          # §9.5 신설 · §9 머리 · §9.4 · §3 사본 갱신 · §11 제한 13
  - milestone-15.md                           # OPEN 요약 한 줄 · 「배운 것」 한 줄
  - reports/evidence/m15/15m/
out_of_scope:
  - /etc · systemd · 운영 host 의 실제 설치 — **운영자 몫**이다(runbook §9.5 ①~④). 이 slice 는 저장소
    파일만 만든다.
  - 실 Telegram 으로의 발송 — 로컬 실측은 전부 `127.0.0.1` 의 가짜 서버 앞에서 돌았다.
  - docs/discovery/capability-map.md — **훑기 실패 경보를 든 행이 없다**(「훑기」 매치 전수를 봤다:
    7F 장부 L-2 · 화면 카드 모집단 · `STR-09`/`OPS-11` 주기 트리거 · 게이트 AST 훑기 · 필수 키 census).
    가장 가까운 `STR-09`/`OPS-11` 행은 **두 번째 트리거의 존재**를 묻는 축이고 이 slice 가 닫는 것은
    **그 트리거가 실패했을 때의 통보**다. 없는 행을 지어내지 않는다 — 장부는 milestone-15 가 진다.
  - 평가 커밋의 `deferred`·인터럽트를 가르는 경보(`OPEN-M15-EVAL-COST-PER-ACCOUNT` ⓑ) — 채널은 같지만
    술어가 다르다(종료 코드가 아니라 로그 줄을 읽어야 한다). 그 행은 그대로 열려 있다.
  - runbook §13.3 이 가리키는 `tools/prod-notification-relay.sh` 가 저장소에 없다 — **이 slice 보다 앞선
    사각**이고 여기서 열지 않는다(아래 「알려진 제한」 4).
acceptance_commands:
  # 이 slice 의 검사가 도는 자리는 ci.yml `container` job 하나다. 게이트가 아니라 **job** 이 단위이므로
  # 아래는 그 job 의 step 전건이고, 새 step 의 명령은 ci.yml 에서 축어다.
  - (CI) container job 전건 — S-20z · S-21~S-22f(이미지 빌드·위생·취약점) · S-23~S-25(compose 기동·
    스모크·리허설·실서버) · S-26 · S-27 · S-27b · S-27c · S-27d · **S-27e**
  # 새 step 은 러너의 `python3`·`curl` 만 쓰므로 **로컬에서 축어로 돌았다**(commands.md). 나머지 step 은
  # 이미지 빌드·trivy·compose 기동이 필요해 로컬 실행 비용이 크고, 판정은 PR 의 CI 가 낸다(15A 라운드 5
  # 와 같은 자리).
  - ./tools/prod-alert-check.sh
  # 워크플로를 고쳤으므로 같은 job 의 첫 step 도 따로 돌렸다(매달린 `steps.<id>` 0).
  - ./tools/workflow-step-refs-check.sh .github/workflows/ci.yml
rollback: reports/evidence/m15/15m/rollback.md
```

## base 확인

브랜치 `feat/m15m-sweep-failure-alert` 는 `origin/main` 의 `a189e8c58`(PR #199 머지 커밋)에서 끊었다.
그 커밋에 이 slice 의 산출물이 없다 — `tools/prod-alert-*.sh` 둘 다 신설이고, 나머지 넷은 이 slice 의
커밋에서만 바뀐다.

## 무엇을 닫는가

운영자 결정 2026-11-23. 알림 훑기 타이머(07:30·13:30 KST)의 실패는 **journal 에만** 남았고, runbook §9.4
는 그것을 「journal 이 유일한 창구」라고 적고 있었다. 2026-11-21 의 세 번 연달은 실패(반입 트리가 release
커밋이 아니어서 `exit 3`)는 그래서 며칠 뒤에야 보였다. systemd 가 실패를 **아는** 자리는 `OnFailure=` 이고,
이 slice 는 거기에 한 줄짜리 Telegram 경보를 건다.

셋을 만든다.

1. `tools/prod-alert-telegram.sh` — unit 이름 하나를 받아 Telegram `sendMessage` 를 **한 번** 친다.
2. `tools/prod-alert-check.sh` — 가짜 Telegram 서버(127.0.0.1 임의 포트) 앞에서 ①의 거동을 재는 검사.
   CI `container` job 의 S-27e 로 매 PR 에서 돈다.
3. runbook §9.5 — unit 템플릿(`bidvector-alert@.service`) · 훑기 unit 의 drop-in · 설치 네 단계 ·
   끄는 법 · 인증서 감시의 같은 drop-in(선택).

**설치는 운영자 몫으로 남는다.** 저장소는 「무엇을 깔 것인가」까지이고, `/etc` 와 `systemctl` 은 건드리지
않았다.

## 위협 모델 — 무엇이 어느 통로로 새면 안 되는가

지켜야 하는 값은 **둘**이다: Telegram 봇 자격(`BIDVECTOR_NOTIFICATION_TELEGRAM_BOT_CREDENTIAL`)과 chat id
(`…_TELEGRAM_CHAT_ID`). 그리고 **셋째**가 있다 — 훑기 journal 의 본문(공고·계정 식별자가 들어갈 수 있다).
통로는 넷이고 각각을 닫는 자리는 다음과 같다.

| 통로 | 새면 무엇이 보이는가 | 닫는 자리 |
|---|---|---|
| **argv** (`/proc/<pid>/cmdline`, 같은 host 의 누구나 읽는다) | Bot API 는 자격을 **URL 경로**에 둔다 — `curl <url>` 로 부르면 그 값이 그대로 올라간다 | URL 을 **설정으로** 넘긴다(프로세스 치환: argv 에 남는 것은 `/dev/fd/<n>`). 파일로 쓰지 않으므로 디스크에도 없다 |
| **stdout·stderr** (oneshot unit 이라 곧 journald 다) | 응답 본문(성공 응답은 chat id 를 되비춘다) · curl 의 오류 문면 · 모양 거부 메시지의 원문 | 본문은 `/dev/null` 로 버리고 curl 의 stderr 는 닫는다. 실패 사유는 **종료 코드에서 만든다**. 모양 거부는 원문도 **길이도** 적지 않는다 |
| **journald** | 위 둘이 그대로 쌓인다(journal 은 지우기 어렵다) | 위와 같은 자리 — 이 스크립트가 찍는 줄에는 자격·chat id·응답 본문이 하나도 없다. 설정 추적(`set -x`)도 꺼 둔다 |
| **Telegram 메시지 본문** | journal 본문이 외부 채널로 나간다 | 메시지는 **고정 모양**이다: unit 이름 · host · `Result`/`ExecMainStatus` · KST 시각 · `journalctl` 안내. 로그 줄을 읽지도 싣지도 않는다 |

**문자열이 아니라 구조로 닫았다.** 「새는 글자를 지운다」(redaction)는 문자열 술어라 낯선 메시지 하나로
열린다 — 그래서 curl 의 글자가 **애초에 우리 출력으로 건너오지 않게** 했다. 같은 이유로 메시지에 들어가는
모든 동적 칸은 허용 글자 밖을 `?` 로 바꾸는 한 자리를 지난다(인용·줄바꿈을 값이 만들 수 없다).

**경보가 경보를 부르지 않는다.** 경보 unit 템플릿에는 `OnFailure=` 를 달지 않는다(runbook §9.5 ②).

## 거동 검사 — 바늘이 진짜임을 먼저 잰다

`TelegramCredentialLeakTest` 와 같은 규율이다. 「없다」가 「보내지 않았다」와 구별되려면 양성 대조가 먼저다.

- **경로·form 양성 대조** — 가짜 서버가 자격을 경로에서, chat id 를 form 에서 **실제로** 받는다.
- **출력 훑개의 양성 대조** — 같은 훑개가 바늘을 심은 파일에서는 그것을 찾아낸다.
- **argv 훑개의 양성 대조** — 바늘을 `argv` 에 담은 미끼 프로세스를 `/proc` 전수에서 찾아낸다.
- **창 증인 둘** — 훑는 동안 경보 스크립트가 살아 있었고, **그 스크립트의 자식** curl 이 떠 있었다.
  (「어딘가의 curl」은 증인이 아니다 — 아래 「설계에서 두 번 고친 것」 ①.)

## 설계에서 두 번 고친 것 (변이가 가르쳐 준 자리)

둘 다 **초록이던 검사**가 변이를 놓쳐서 드러났다. 고친 뒤 같은 변이가 붉다(commands.md 의 변이 표).

1. **argv 훑기가 일찍 멈췄다.** 앞 판은 증인 셋이 모이면 `break` 했고, 그 조건은 우리 curl 이 뜨기도
   **전에** 남의 `curl --config` 로 채워질 수 있었다 — 변이 ①(자격을 argv 로 올린 판)이 그 틈으로 초록이
   됐다(실측). 지금은 창이 닫힐 때까지 멈추지 않고, 증인도 **이 스크립트의 자식 curl** 이다(PPid 로 잰다).
2. **거부 갈래가 죽은 포트를 보고 있었다.** unit 이름 모양 거부를 재는 갈래가 **앞 갈래의 env 파일**
   (이미 끝난 서버의 포트)을 재사용했고, 그래서 「네트워크 호출 0」이 **접속 실패**와 구별되지 않았다 —
   변이 ③(모양 검사 제거)이 그 틈으로 초록이 됐다(실측). 지금은 거절 갈래마다 **지금 떠 있는 서버**를
   가리키는 설정으로 돌고, 호출 수도 갈래마다 따로 센다.

## 왜 이 모양인가 — 지시안에서 고친 둘

- **실패 사유를 「가려서」 찍지 않고 종료 코드에서 만든다.** 지시는 `curl -sS` + redact 였다. 이 저장소의
  규율(「게이트 술어는 문자열이 아니라 구조로 닫는다」)에 맞춰 curl 의 글자가 **한 자도 건너오지 않게**
  했다 — 지워야 할 문자열이 없으면 지우기에 실패할 수 없다. 사유는 종료 코드 표에서 온다(이름 해소 실패 ·
  접속 실패 · 시간 초과 · TLS · 그 밖).
- **값의 출처가 파일 하나가 아니라 「키마다 파일 → 환경」이다.** `relay.env` 는 runbook §13.2 가 **root 0600**
  으로 두라고 적는다. 경보 unit 을 `User=deploy` 로 돌리면 스크립트는 그 파일을 읽을 수 없다. §13.3 의
  relay unit 이 이미 쓰는 길(`EnvironmentFile=` — systemd 가 root 로 읽어 환경으로 넘긴다)을 그대로 따르고,
  `--env-file` 은 손 실행과 이 검사를 위해 남긴다. 두 갈래 **모두** 검사가 잰다.

## 하네스 레인 변경

없음. `.claude/` 아래를 건드리지 않았다. CI 워크플로의 새 step(S-27e)은 하네스가 아니라 이 slice 의
산출물이고 `in_scope` 에 있다.

## 알려진 제한

1. **설치되지 않으면 아무것도 막지 못한다.** 이 slice 가 만드는 것은 저장소 파일이고, `OnFailure=` 가 서는
   것은 운영자가 §9.5 ①~④ 를 돌린 뒤다. 그때까지 §9.4 의 문면대로 journal 이 유일한 창구다.
2. **「실패한 run」만 알린다 — 「아예 돌지 않은 타이머」는 못 본다.** 발화하지 않는 것은 실패하지 않으므로
   `OnFailure` 가 걸리지 않는다. 그 축은 §9.4 의 주기 확인(`list-timers`)이 그대로 진다. 두 창을 합치는
   것(사표 감시·heartbeat)은 이 slice 밖이다.
3. **실패로 들어가는 길 전체는 재지 못했다.** 검사는 경보 스크립트의 거동을 끝까지 재지만, `OnFailure=`
   배선이 실제로 발화하는지는 운영 스택을 일부러 깨야 보인다. runbook §9.5 ④ 는 그래서 **배선**
   (`systemctl show -p OnFailure`)과 **경보 경로**(직접 `systemctl start`)를 나눠 잰다.
4. **relay 러너 스크립트가 저장소에 없다.** runbook §13.3 의 `ExecStart` 가 `tools/prod-notification-relay.sh`
   를 가리키는데 그 파일이 없다(착수 실측). 이 slice 가 만든 사각이 아니고 여기서 닫지도 않는다 — 경보는
   relay 에 기대지 않고 `relay.env` 의 **두 값**만 쓴다.
5. **메시지 본문의 `Result`/`ExecMainStatus` 는 `systemctl` 이 없거나 읽지 못하면 `unknown` 이다.** 경보를
   멈추지 않는 쪽을 골랐다 — 상태를 모르는 것보다 「실패했다」를 늦게 아는 쪽이 나쁘다.

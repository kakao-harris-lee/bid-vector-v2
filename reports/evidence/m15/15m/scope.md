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
  - docs/runbook/m7-7f-production.md          # §9.5 신설 · §9 머리 · §9.2 · §9.4 · §13.2 · §3 사본 갱신 · §11 제한 13
  - milestone-15.md                           # OPEN 요약 한 줄 · 「배운 것」 한 줄
  # ── 수정 라운드 1 에서 들어온 셋(cr H-1 — 미룸을 경보에서 뺀다) ──────────────
  - tools/prod-env.sh                         # 잠금 경합을 전제 실패와 가르는 `_PROD_LOCK_BUSY`
  - tools/prod-notification-sweep.sh          # 미룸 = exit 4
  - tools/prod-guards-check.sh                # 그 갈림과 drop-in 두 줄을 재는 단언 넷
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
  # 수정 라운드 1 이 러너의 종료 코드와 runbook 의 unit 문면을 바꿨다 — 그 둘을 재는 S-27d 도 축어로
  # 돌았다(잠금 자리가 있어야 한다: `sudo install -d -m 0755 -o $(id -un) -g $(id -gn) /run/lock/bidvector-v2`).
  - ./tools/prod-guards-check.sh
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
| **`/proc/<pid>/environ`**(cr M-9 — 「넷」이 전수가 아니었다) | §9.5 ② 가 고른 배선(`EnvironmentFile=`)은 자격을 **프로세스 환경**에 올리고 `curl` 자식이 그것을 물려받는다 | 노출 폭은 argv 보다 훨씬 좁다 — `environ` 은 world-readable 이 아니라 `PTRACE_MODE_READ` 를 타고, Ubuntu 기본 Yama `ptrace_scope=1` 이면 조상만 읽는다. 경계는 **`User=` 와 그 설정** 둘이다. 더 좁히려면 `LoadCredential=` 이나 경보 unit 을 root 로 두고 기본 `--env-file`(root 0600)을 쓴다 — 오늘은 relay(§13.3)와 **같은 배선**을 쓰는 쪽을 골랐다 |

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

## 수정 라운드 1 (PR #201 verifier ready-for-review + code-reviewer request_changes)

판정 SHA `f5a60c13f` 기준. **산출물 blocker 0 · high 1 · medium 11 · low 8**.

### cr H-1 — 설계된 정상(`exit 3` 잠금 경합)이 경보를 울린다

**거동으로 닫았다**(제안 둘 중 정공법). 잠금 경합은 §9.1 이 「다음 주기로 미룬다」고 적는 갈래인데
`exit 3` 을 써서, 반입·되돌림·손 실행이 07:30·13:30 창에 겹칠 때마다 2026-11-21 의 진짜 사건과 **글자
하나 다르지 않은** 경보가 한 통 나갔다. 받는 사람이 둘을 가를 수 없으면 §9.2 가 적은 그대로
「무해한 오류 줄이 사람을 길들인다」.

- `tools/prod-notification-sweep.sh` — 미룸은 **`exit 4`**. 가르는 값은 **문면이 아니라**
  `tools/prod-env.sh` 의 `_PROD_LOCK_BUSY` 다(경합일 때만 선다). 잠금 **자리·권한**이 어긋난 경우는
  미룸이 아니므로 `3` 에 남고 경보가 운다 — 「잠금 관련이면 조용히」가 아니다.
- §9.5 ③ drop-in 에 `[Service] SuccessExitStatus=4` · §9.2 의 `ExecStopPost` 조건에 `exit 4` 조항
  (미룸은 자기 일회 컨테이너를 **띄우지도 않는다** — 조건이 없으면 그때도 돌아 남의 것을 지운다).
- §9.4 의 종료 코드 목록과 러너 스크립트 머리 주석이 같은 목록을 갖는다.
  `docs/runbook/m7-7c3b-notification-sweep.md` 는 **러너 자신의** 0·1·2 만 적고 `3` 을 들지 않아
  고칠 자리가 없었다(확인하고 두었다).
- `tools/prod-guards-check.sh` 가 넷을 잰다: 프로젝트 잠금·상태 잠금을 각각 쥔 채 러너가 `4` 로
  끝나는가(둘) · `ExecStopPost` 조건식 전문(`exit 4` 조항 포함) · drop-in 의 `SuccessExitStatus=…4`.
  **거동이 갈리는 자리가 둘**이라(스크립트와 systemd) 한쪽만 고친 판이 조용히 통과하지 않게 둘 다 본다.

**이 거동은 다음 release 와 반입 뒤에 선다.** 훑기는 `/srv/bidvector-v2`(§1.4b)에서 돌고 그 트리는
반입된 release 의 커밋이다 — 그때까지 `exit 4` 는 **일어나지 않고**(옛 판은 그 자리에서 `3` 을 낸다)
경보도 지금처럼 운다. 무해하고, 반입과 함께 저절로 바뀐다. drop-in 의 `SuccessExitStatus=4` 는 먼저
깔아도 해가 없다(없는 코드를 성공으로 보는 것뿐이다).

### v M1 · cr M-8 — env 해석기가 systemd 와 같은 값을 내야 한다

`/etc/bidvector-v2/relay.env` 는 이 host 에서 **`deploy:deploy 0600`** 이다(2026-11-23 `stat` — 값은
읽지 않았다). §9.5 의 「root 0600 이라 `User=deploy` 가 못 읽는다」 전제가 현물과 달랐고, 그래서
**운영에서도 스크립트 자신의 해석기가 쓰인다.** 값 앞뒤 공백·탭·CR 을 따옴표 **밖에서** 버린 뒤
따옴표를 벗긴다(안쪽 공백은 값이다). `=` **앞** 공백은 받지 않는다 — systemd 도 그 줄의 키를 이름으로
보지 않으므로, 더 너그러우면 그것도 두 갈래가 갈리는 자리다. 변종 넷을 검사가 잰다.

### 그 밖 — 받아들인 것

- **cr M-1** `EnvironmentFile=-` — 파일이 **아예 없는** host 에서도 스크립트의 `exit 2` 가 창구다.
- **cr M-2** 평문 `http` 는 **루프백만**. Bot API 는 자격을 경로에 싣는다 — 한 줄의 다운그레이드가
  argv 에서 막은 값을 선로에 올린다. 검사는 예약 대역(RFC 5737 `192.0.2.1`)으로 재므로 바깥에 닿지 않는다.
- **cr M-3 · v L1** 기준 URL 의 끝 `/` 하나를 뗀다 + §13.2 의 그 키 줄에 공유 사실을 적었다.
- **cr M-4** §9.4 에 감시자 감시 한 줄(`systemctl list-units 'bidvector-alert@*' --state=failed`).
- **cr M-5·M-6·M-7 · v M2** 검사 **21 → 38 건**(아래 「검사가 재는 것」).
- **cr M-9** 위협 모델의 다섯째 통로 — `/proc/<pid>/environ` 과 그 경계.
- **low** `os.replace` 로 포트 파일 원자적 쓰기(L-3) · `LC_ALL=C`(L-4) · 끄는 법의 대칭 확인(L-6) ·
  `/proc` 훑기에서 프로세스마다 `tr` 포크를 없앴다(L-2 — 창 한 바퀴를 짧게 유지한다).

### 받아들이지 않은 것 (사유와 함께 등재)

- **cr L-1 재시도 0** — 더하지 않는다. 경보는 **한 번** 친다. 재시도는 「매달린 경보가 다음 run 과
  겹친다」를 열고(`TimeoutStartSec` 안에서 `--retry` 가 15초씩 쌓인다), 못 나간 경보의 자리는 이제
  M-4 의 watcher 줄이 받는다. 흔들림에 재시도를 더하는 쪽은 **그 줄이 운영에서 한 번이라도 울린 뒤**
  정할 일이다 — 측정 없이 무거운 쪽으로 가지 않는다(`v2-지침서` §7).
- **cr L-5 tzdata 부재 시 「(KST)」** — 알려진 제한 6 으로 등재한다. 문면에 `%z` 가 함께 실려
  (`+0900` ↔ `+0000`) 단서가 남고, 운영 host 에는 zone 파일이 있다(§9.2 의 타이머가 같은 zone 으로
  돈다 — 없으면 타이머부터 어긋난다).
- **cr L-7 unit hardening**(`NoNewPrivileges=`·`ProtectSystem=`·`PrivateTmp=`) — 알려진 제한 7.
  relay(§13.3)·인증서 감시(§4.5)의 unit 과 **같은 모양**을 유지하는 쪽을 골랐다. 셋 중 하나만
  단단해지면 다음 사람이 어느 쪽이 기준인지 모른다 — hardening 은 세 unit 을 한 번에 보는 slice 다.
- **cr Open Question(창 3초)** — CI 로그로 실측한 뒤 판단한다. 지금 판은 창이 닫힐 때까지 멈추지
  않고(라운드 0 의 교훈), 이 라운드가 한 바퀴의 포크를 없앴다(L-2).

### 검사가 재는 것 — 새 축 여섯

| 축 | 무엇이 공허했나 | 지금 |
|---|---|---|
| curl 실패(rc≠0) | 실패 갈래가 HTTP 403 하나뿐이었다 | 닫힌 포트로 한 번 — 종료 1 · 문면은 종료 코드에서 · 바늘 둘 부재 |
| 임시 파일 | 헤더의 「디스크에도 남지 않는다」가 미측정 | `TMPDIR`·cwd 를 빈 자리로 돌려 **창 안·창 뒤** 훑기(+ 훑개 양성 대조) |
| 해석기가 실행하지 않는다 | 적대적 값이 없었다 | 값 셋이 `$(touch …)` — 표지 셋 부재 · `source` 양성 대조 |
| `_safe` | 네 출처가 전부 얌전해 아무 단언도 안 지났다 | PATH 스텁 `systemctl` 이 인용·줄바꿈을 담은 상태 값을 낸다 |
| chat id 출력 | 자격만 봤다(chat id 는 곁효과로만 붉었다) | 가짜 서버가 chat id 를 **되비추고**, 갈래 다섯의 출력에서 부재를 잰다 |
| env 해석 | 변종이 없었다 | CRLF · 값 뒤 공백 · `=` 뒤 공백 · 따옴표 뒤 CR·공백 |

전제 거부 넷은 「0 이 아니다」가 아니라 **종료 2** 까지 본다 — 앞 판의 느슨한 술어는 **거절**과
**접속 실패**를 같은 값으로 봤고, 평문 http 변이가 바로 그 틈으로 통과한다(실측).

## 하네스 레인 변경

없음. `.claude/` 아래를 건드리지 않았다. CI 워크플로의 새 step(S-27e)은 하네스가 아니라 이 slice 의
산출물이고 `in_scope` 에 있다. **수정 라운드 1 이 만든 새 파일은 없다**(바뀐 셋은 전부 기존 파일이고
위 `in_scope` 에 등재했다 — 「수정 라운드 새 파일 ↔ in_scope」 대조).

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
6. **tzdata 가 없는 host 에서는 「시각 … (KST)」가 UTC 를 가리킨다**(cr L-5). 같은 줄의 `%z` 가
   `+0900` ↔ `+0000` 로 단서를 남기고, 운영 host 에는 zone 파일이 있다(§9.2 의 타이머가 같은 zone 으로
   돈다 — 없으면 타이머부터 어긋난다). 라벨을 `%Z` 로 바꾸는 것은 다음 손질.
7. **경보 unit 에 hardening 지시가 없다**(cr L-7 — `NoNewPrivileges=`·`ProtectSystem=`·`PrivateTmp=`).
   relay(§13.3)·인증서 감시(§4.5)의 unit 과 **같은 모양**을 유지하는 쪽을 골랐다. 셋을 한 번에 보는
   slice 의 일이다.
8. **경보는 한 번만 친다**(cr L-1). 일시적 DNS·TLS 흔들림 하나로 그 run 의 경보가 사라진다. 그 자리는
   §9.4 의 `systemctl list-units 'bidvector-alert@*' --state=failed` 가 받는다 — 재시도를 더할지는
   그 줄이 운영에서 한 번이라도 울린 뒤에 정한다.
9. **미룸(`exit 4`)은 다음 release·반입 뒤에 선다.** 훑기는 `/srv/bidvector-v2` 에서 돌고 그 트리는
   반입된 release 의 커밋이다. 그때까지 그 자리는 옛 판의 `3` 이고 경보가 그대로 운다 — 무해하고
   반입과 함께 바뀐다.

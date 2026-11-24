# M15 / 15Q — 알림 실발송 활성화 (평가 커밋 · relay 일회 러너 래퍼와 절차)

```yaml
milestone: M15
slice: 15q-telegram-activation
base_sha: 71b1ad3a7                      # main, PR #201 머지 커밋(아래 「base 확인」)
head_sha: <리뷰 요청 시점에 기입>          # 이 파일을 담은 커밋이 그 SHA 라 자기 안에 적을 수 없다
in_scope:
  - tools/prod-notification-relay.sh      # 신설 — relay 일회 러너 래퍼(§13.3 이 「아직 없다」고 적던 자리)
  - tools/prod-evaluation-commit.sh       # 신설 — 평가 커밋 일회 러너 래퍼
  - tools/prod-env.sh                     # 금지 JVM 옵션 술어 둘(두 래퍼가 함께 본다)
  - tools/prod-guards-check.sh            # 새 래퍼 둘의 거동 단언(74 → 175 건)
  - docs/runbook/m7-7f-production.md      # §13.3 재작성 · §13.4 운영자 활성화 · 첫 실 run 측정 표
  - docs/runbook/m6-6e-operations.md      # §3.2 — 래퍼가 러너의 3·4 를 5·6 으로 옮긴다는 주
  - docs/discovery/capability-map.md      # OPEN-6F10-SCHEDULER 행 · OPEN-M15-EVAL-COST-PER-ACCOUNT 행
  - milestone-15.md                       # 이 slice 가 닫는 것 한 문단 · EVAL-COST 요약 한 줄
  - reports/evidence/m15/15q/
out_of_scope:
  - /etc · systemd · 운영 host 의 실제 설치 — **운영자 몫**이다(§13.4 ①~⑤). 이 slice 는 저장소 파일만
    만든다. `systemctl`·`daemon-reload`·타이머 활성화를 하나도 돌리지 않았다.
  - 실 Telegram 으로의 발송 · 실 API 호출 — 로컬 실측은 전부 가짜 `docker`·`git` 앞에서 돌았고
    컨테이너를 하나도 만들지 않았다(compose 의 `--env-file` 의미 실측 둘만 `busybox` 를 썼고, 그것은
    이 저장소와 무관한 버릴 프로젝트 이름의 fixture 다 — commands.md).
  - `/etc/bidvector-v2/*.env` 의 **값** — 읽지 않았다(`stat` 도 하지 않았다).
  - 러너 코드(`app/**`) — 이 slice 는 러너를 바꾸지 않는다. 바꾼 것은 **거는 법**뿐이다.
  - `OPEN-M15-EVAL-COST-PER-ACCOUNT` ⓑ 의 경보 술어와 ⓒ 의 상한 — **수가 없으면 정하지 않는다.**
    이 slice 는 「무엇을 어디서 재는가」까지다(§13.4 의 표).
  - `tools/prod-notification-sweep.sh` 의 거동 — 바꾸지 않았다. 훑기 unit 의 `SPRING_MAIN_WEB_
    APPLICATION_TYPE` 부재는 **이 slice 보다 앞선 자리**이고 여기서 열지 않는다(아래 「알려진 제한」 5).
acceptance_commands:
  # 이 slice 의 검사가 도는 자리는 CI `container` job 의 S-27d 하나다(새 step 을 만들지 않았다 —
  # 새 단언은 기존 검사 안으로 들어간다). 그 명령은 축어로 로컬에서 돌았다.
  - ./tools/prod-guards-check.sh
  # 전제: 잠금 자리가 있어야 한다 —
  #   sudo install -d -m 0755 -o $(id -un) -g $(id -gn) /run/lock/bidvector-v2
  - bash -n tools/prod-notification-relay.sh tools/prod-evaluation-commit.sh tools/prod-env.sh tools/prod-guards-check.sh
  # (CI) container job 전건 — S-20z · S-21~S-22f · S-23~S-25 · S-26 · S-27 · S-27b · S-27c ·
  #   **S-27d** · S-27e. 나머지 step 은 이미지 빌드·trivy·compose 기동이 필요해 판정은 PR head 의 CI 다.
rollback: reports/evidence/m15/15q/rollback.md
```

## base 확인

브랜치 `feat/m15q-telegram-activation` 는 `origin/main` 의 `71b1ad3a7`(PR #201 머지 커밋, M15/15M)에서
끊었다. 그 커밋에 이 slice 의 산출물이 없다 — `tools/prod-notification-relay.sh`·
`tools/prod-evaluation-commit.sh` 둘 다 신설이고, 나머지 여섯은 이 slice 의 커밋에서만 바뀐다.

## 무엇을 닫는가

운영자 결정 2026-11-24 — 알림 실발송을 켠다. 오늘 운영에는 **훑기 타이머 하나뿐**이고, 평가 커밋
러너도 relay 러너도 **돌지 않는다**. 그래서 Telegram 에 닿는 것이 하나도 없다. 빈자리는 저장소 쪽에
둘 있었다.

1. **relay 래퍼가 없었다.** runbook §13.3 이 `tools/prod-notification-relay.sh` 를 `ExecStart=` 로
   가리키면서 같은 절에 「**아직 없다**」고 적고 있었다. 그 절을 읽은 운영자가 unit 을 깔면 systemd 가
   기동에서 끊는다.
2. **평가 커밋 래퍼는 가리켜진 적도 없었다.** 러너는 M6/6F-10 부터 있었지만(`bidvector.evaluation.
   mode=once`) 운영 스택에서 그것을 거는 자리가 저장소에 없었다. 그 러너가 `outbox` 에 행을 남기지
   않으면 relay 는 **집을 것이 없다.**

둘을 §9.1 의 훑기 래퍼와 **같은 모양**으로 만들고, 거는 절차(unit·timer·drop-in·활성화 다섯 단계)를
§13.3·§13.4 에 적는다. **설치와 활성화는 운영자 몫으로 남는다** — 저장소는 「무엇을 깔 것인가」까지다.

## 위협 모델 — 무엇이 어느 통로로 새면 안 되는가

**지켜야 하는 값은 셋이다**: Telegram 봇 자격 · chat id(§13.1·§13.2) · **운영 DB 자격**(일회 컨테이너가
그대로 받는다). 그리고 지켜야 하는 **불변식이 하나** 있다 — 「반입이 막는 것을 러너만 통과시키는
길이 없다」(§9.1). 이 slice 가 여는 새 표면은 **둘째 env 파일** 하나이고, 통로별로 닫는 자리는 다음과 같다.

| 통로 | 새면 / 어긋나면 무엇이 일어나는가 | 닫는 자리 |
|---|---|---|
| **둘째 env 파일의 키** | `run --env-from-file` 로 들어온 값은 서비스가 선언한 env 에 더해지고 **같은 이름이면 이긴다**(실측). `JAVA_TOOL_OPTIONS` 나 `BIDVECTOR_PERSISTENCE_JDBCURL` 한 줄이면 반입 가드를 거치지 않고 운영 app 의 DB·JVM 통로를 바꾼다 — 설정 통로 등식은 **해석된 compose 만** 보므로 이 파일은 그 밖이다 | 키 집합을 **§13.2 의 일곱으로 닫는다**(열거가 아니라 집합). 허용 이름은 Spring 의 두 사상으로 속성 이름에서 만든다 |
| **JVM 의 JDK HttpClient 내부 로그 속성** | 요청 URL 이 stderr → journald 로 나가고 그 URL 은 **경로에 봇 자격**을 싣는다. 어댑터가 로거를 쥐지 않는다는 보증 **밖**의 축이다(JDK 자신의 로그) | 래퍼가 **기동 전에** 끊는다(`exit 3`). 보는 자리는 compose 가 못 보는 둘 — 자기 프로세스의 JVM 옵션 통로 셋과 `relay.env`. 판정은 **존재만** 본다 |
| **env 파일의 값** | 래퍼가 값을 문면에 옮기면 journal 에 남는다 | 이 래퍼의 어느 문면도 값을 나르지 않는다 — 키 이름 · 속성 이름 · 줄 **개수**뿐이다(모양 거부 줄도 내용을 옮기지 않는다) |
| **종료 코드** | relay 러너의 다섯과 래퍼의 둘이 겹치면, 뜻이 **정확히 반대**인 둘이 같은 수로 만나 §9.5 의 경보가 **거꾸로** 운다(기다리면 풀리는 것에 경보가 울고, 고쳐야 하는 것이 조용하다) | 래퍼가 러너의 `3`·`4` 를 **5·6 으로 옮긴다.** drop-in 의 제외 목록은 `4 5` 이고 `6` 은 빼지 않는다 |
| **반입 불변식** | 래퍼가 훑기의 전제를 하나라도 건너뛰면, 반입이 막는 상태(옮겨진 태그 · 다른 커밋의 트리 · 끝나지 않은 반입)에서 **운영 DB 자격을 받은 컨테이너**가 뜬다 | 훑기와 **같은 술어**를 같은 순서로 쓴다. 술어의 단일 출처는 `tools/prod-env.sh` 이고, 거동은 `prod-guards-check.sh` 가 러너 셋에 **같은 모양으로** 건다 |

**문자열이 아니라 구조로 닫았다.** 키 집합은 등식이고(새 이름 하나로 열리지 않는다), 허용 철자는
손으로 적지 않고 속성 이름에서 **만든다**(문서의 철자와 바인딩되는 철자가 갈리지 않는다), 금지 속성은
값의 해석이 아니라 **존재**로 본다(JDK 의 값 해석을 흉내 내지 않는다).

## 설계 검토 (게이트·계약형 slice)

**(0) 경계 문장** — 이 래퍼가 막는 것은 「운영 스택의 일회 러너가 **반입이 승인한 것과 다른 바이트·
배선·설정**으로 뜨는 것」과 「자격이 argv·stdout·journal·컨테이너 env 를 통해 새는 것」이다. 막지
않는 것: docker 소켓을 가진 사람(이미 DB 에 닿는다) · `relay.env` **값**의 정직성(러너가 본다) ·
Telegram 쪽 거동.

**(1) 열거인가 구성인가** — 셋 다 구성이다. ⓐ 채널 env 의 키는 **집합 등식**(허용 밖이면 거절),
ⓑ 허용 철자는 Spring 의 두 사상으로 **생성**, ⓒ 경합/전제 실패의 갈림은 문면이 아니라
`_PROD_LOCK_BUSY`(`flock -E 200` 이 경합일 때만 내는 코드). 열거로 남은 자리 하나는
`--environment` 의 `{production, staging}` 인데 이것은 **좁히는** 허용 목록이라 새 값이 조용히
통과하지 않는다(fail-closed).

**(2) 우회 다섯 이상** — ① `relay.env` 에 `JAVA_TOOL_OPTIONS` 를 넣어 JVM 통로를 연다 → 키 집합이
막는다 ② 같은 파일에 `BIDVECTOR_PERSISTENCE_JDBCURL` 을 넣어 다른 DB 로 보낸다 → 같은 등식 ③ 허용된
키의 **값** 안에 금지 속성을 숨긴다 → 파일 전수 스캔이 막는다 ④ 래퍼를 부르는 셸에
`JAVA_TOOL_OPTIONS` 를 내보낸다 → 프로세스 환경 검사가 막는다 ⑤ `--env-from-file` 을 모르는 compose
에서 돌려 **세 값 없이** 뜨게 한다 → 플래그 지원 문턱이 막는다(기동 실패와 전제 실패를 가른다)
⑥ `--environment development` 로 억제 환경을 세워 「보내지 않는 run 이 성공으로」 끝나게 한다 →
환경 허용 목록이 막는다 ⑦ 남은 일회 컨테이너 옆에서 돈다 → 잔존 술어(훑기와 같은 것) ⑧ 프로젝트
이름을 정규화 전 글자로 줘 잠금과 다른 자리를 보게 한다 → 해석된 이름 등식. **여덟 전부 단언이 있다.**

**(2b) 값 획득 축 — 새 public 표면 전수.** 이 slice 가 더하는 표면은 **셸 인자 일곱**(relay 여섯 ·
평가 하나)과 **env 파일 하나**다. 「경계로 처리」 행도 실측했다.

| 새 표면 | 값이 어디까지 가는가 | 처분 |
|---|---|---|
| `--relay-env-file <경로>` | 파일 **경로**가 argv 에 · 파일 **내용**이 컨테이너 env 에 | 키 집합 등식 + 금지 속성 스캔. 경로는 자격이 아니다 |
| `--owner <id>` | argv → 컨테이너 argv | 자격이 아니다(§13.2 의 세 값과 다른 축). 빈 값이면 러너가 기동 실패하므로 래퍼가 먼저 거절한다 |
| `--environment` | argv → 컨테이너 argv | `{production, staging}` 허용 목록 |
| `--claim-limit N` | argv → 컨테이너 argv | `^[1-9][0-9]{0,4}$`. `0` 은 러너의 기동 실패라 래퍼가 먼저 거절 |
| `--current-active-bids N` | argv → 컨테이너 argv | `^(0|[1-9][0-9]{0,4})$`. **기본값을 짓지 않는다** |
| `--env-file`·`--state-dir`·`--project` | 훑기와 같다 | 훑기와 같은 술어 |
| `relay.env` 의 일곱 키 | 컨테이너 env → JVM 설정 | 값은 **보지 않는다**(결손은 러너가 말한다). 본 것은 키 이름과 금지 속성의 존재뿐 |
| `object` 커널 주입 자리 | **없다** — 이 slice 는 Kotlin 을 한 줄도 바꾸지 않는다 | — |

**(3) 과잉·미달** — 미달이 아닌 것: ⓐ `relay.env` **값**의 모양 검사(러너가 `RefusedMissingChannel
Config`·모양 거부로 이미 한다 — 두 자리에 두면 갈린다) ⓑ 재시도(경보 slice 와 같은 판단 — 러너는 한
번 돈다) ⓒ 평가 커밋 래퍼의 채널 env 축(그 러너는 채널 설정을 읽지 않는다). 과잉이 아닌 것:
평가 커밋 래퍼의 **금지 JVM 옵션 검사** — 그 러너도 바깥 호출을 하고 이 저장소의 바깥 호출은 자격을
URL 에 싣는다(조달청 서비스 키는 질의 문자열이다). 러너마다 「이쪽은 봐도 되나」를 다시 묻지 않는 쪽을
골랐다.

## 실측이 고친 것 셋 — 지시안이 사실과 달랐던 자리

세 번 다 **지시와 앞 판 문서가 그렇게 하라고 적은 것**이 실측에서 틀렸다. commands.md 에 명령과 결과가 있다.

1. **「둘째 `--env-file`」은 컨테이너에 닿지 않는다.** compose 의 **최상위** `--env-file` 은 보간에만
   쓰인다 — 두 파일을 주고 컨테이너 안에서 `env` 를 보면 **둘 다 없다**(실측 A). 컨테이너까지 가는
   통로는 `run --env-from-file` 이다(실측 B). 앞 판 §13.3 의 처방을 그대로 구현했으면 「§13.2 를
   채웠는데도 `RefusedMissingChannelConfig`」가 **첫 실발송에서** 났다 — 그 절의 §13.6 첫 행이 바로 그
   증상을 적고 있다.
2. **그 파일은 서비스 선언 env 를 이긴다.** 같은 이름이 양쪽에 있으면 **파일이 이긴다**(실측 D).
   그래서 둘째 env 파일은 「세 값을 더하는 자리」가 아니라 **새 설정 통로**이고, 키 집합을 닫는 것이
   선택이 아니라 전제가 됐다.
3. **relay 러너의 종료 코드 다섯과 래퍼의 둘이 겹친다.** 훑기 선례의 래퍼는 `3`(전제 실패)·`4`(미룸)를
   쓰는데 `RelayExitCode` 는 `3 LEASE_BUSY`·`4 ENV_SUPPRESSED` 를 쓴다 — **뜻이 정확히 반대**다. 그대로
   두면 §9.5 의 경보가 거꾸로 운다. 래퍼가 러너의 둘을 `5`·`6` 으로 옮기고 journal 줄로 남긴다.

## 하네스 레인 변경

없음. `.claude/` 아래를 건드리지 않았다. `tools/prod-guards-check.sh` 는 하네스가 아니라 이 저장소의
산출물이고 `in_scope` 에 있다. **이 라운드가 만든 새 파일은 둘**(`tools/prod-notification-relay.sh` ·
`tools/prod-evaluation-commit.sh`)이고 둘 다 `in_scope` 에 등재했다 — CI 워크플로에 새 step 을 만들지
않았다(새 단언은 기존 S-27d 안으로 들어간다).

## 알려진 제한

1. **처방과 현물이 다르다.** 이 slice 는 unit·timer·drop-in 여섯의 **문면**을 만들 뿐, `/etc` 에 아무것도
   깔지 않았다. 운영자가 §13.4 를 밟기 전까지 평가 커밋도 relay 도 **돌지 않는다** — 그래서 Telegram 에
   닿는 것도 없다. `OPEN-6F10-SCHEDULER` 가 닫히지 않는 사유의 절반이 이것이다.
2. **이 거동은 반입 뒤에 선다.** 래퍼는 `<저장소>`(§1.4b)에서 돌고 그 트리는 반입된 release 의 커밋이다 —
   `rel-65ea475e0af3` 에는 이 두 파일이 없다. 다음 반입 전에 unit 을 깔면 `ExecStart=` 가 없는 파일을
   가리킨다. **§13.4 ① 보다 반입이 먼저다.**
3. **`--env-from-file` 지원을 판 번호로 묻지 않는다.** 그 플래그가 들어온 compose 판을 지어내지 않고
   `run --help` 의 문면을 본다 — **텍스트 술어**다. docker 가 그 줄의 모양을 바꾸면 거짓 거절(`exit 3`)이
   난다. 거짓 **통과**는 아니다(fail-closed) — 그래도 느슨함이 아니라 **다른 종류의** 취약함이다.
4. **compose 자신의 실패 코드가 `3`·`4` 면 래퍼가 `5`·`6` 으로 잘못 옮긴다.** compose 는 자기 오류에
   `1`·`125`~`127` 을 쓰므로 알려진 경로는 없지만, 그 등식은 보장이 아니라 **관찰**이다. 전제 검사 여덟이
   먼저 서는 것이 이 자리의 실질적인 겹이다.
5. **훑기 러너는 `SPRING_MAIN_WEB_APPLICATION_TYPE=none` 을 주지 않는다.** 새 래퍼 둘은 준다(m6-6e
   §1.3 의 관례). 훑기의 그 자리는 **이 slice 보다 앞선 사각**이고 여기서 고치지 않았다 — 포트를 publish
   하지 않아 host 에서 닿지 않지만 compose 망 안에서는 수 초 동안 API 표면이 뜬다. 고칠 자리는 그
   래퍼이고, 고치면 §9 의 acceptance 를 다시 재야 한다.
6. **다섯 분 간격은 측정이 아니다.** 07:30 → 07:35 → 07:40 은 「앞 run 이 5 분 안에 끝난다」는 가정이고,
   오늘 그 수를 잰 적이 없다(평가 커밋 실 run 이 0 건이다). 넘으면 뒤 run 이 `exit 4` 로 미루고 다음
   주기에 낫는다 — **잃는 것은 없지만** 그 미룸이 쌓이면 간격이 틀린 것이다. §13.4 의 측정 표가 그
   자리를 든다.
7. **`--current-active-bids` 는 unit 의 고정 문자열이다.** 진행 투찰 수가 바뀌면 사람이 그 줄을 고쳐야
   한다(`OPEN-6F10-CAPACITY-SOURCE` — 이 저장소에 계수기가 없다). 고치지 않으면 용량 보류 판정이
   낡은 수로 선다.
8. **키 집합 등식은 Spring 의 두 사상만 안다.** 그 밖의 철자(예: `_` 를 더 넣은 꼴)는 거절된다 —
   바인딩되지도 않을 철자라 fail-closed 지만, 운영자가 오타를 내면 `RefusedMissingChannelConfig` 가
   아니라 **키 집합 거절**로 먼저 멈춘다(메시지는 다르고, 가리키는 자리는 같다).

## rollback

`reports/evidence/m15/15q/rollback.md`.

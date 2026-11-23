# M15 / 15M — commands

base `a189e8c58c8a438e2685c8daae8f83b835b925ba` · 브랜치 `feat/m15m-sweep-failure-alert`

환경: WSL2 Ubuntu · `python3 3.12.3` · `curl` · 실 Telegram 과 운영 host 에 닿는 명령은 **하나도 없다**
(전부 `127.0.0.1` 의 가짜 서버 앞에서 돌았다).

## 로컬 실측

| 명령 | 결과 |
|---|---|
| `bash -n tools/prod-alert-telegram.sh` · `bash -n tools/prod-alert-check.sh` | exit 0 |
| `./tools/prod-alert-check.sh` | exit 0 — **21 건 전부 통과** |
| `./tools/workflow-step-refs-check.sh .github/workflows/ci.yml` | exit 0 — job 5 · id 15 · `steps.<id>` 참조 18 · 매달린 참조 0 |
| `python3 -I -c "yaml.safe_load(ci.yml)"` 로 `container` job 의 step 이름 열거 | 끝 네 개가 S-27b · S-27c · S-27d · **S-27e** — 새 step 이 파싱되고 자리가 맞다 |
| `command grep -rniE -f config/quality/leak-patterns.txt <이 slice 가 만든·고친 파일 여섯>` | 매치 0 |

`prod-alert-check.sh` 의 21 건(요약): 2xx 종료 0 · 요청 1 회 · **경로 양성 대조** · chat id 등식 ·
문면 넷(unit·확인 명령·host·result) · 문면에 자격 0 · **출력 훑개 양성 대조** · 출력에 자격 0 ·
**argv 훑개 양성 대조(미끼)** · 창 증인 둘(스크립트·자식 curl) · argv 에 자격 0 · 느린 갈래 종료 0 ·
403 거절 문면 · chat id 부재 거절 · unit 이름 모양 거절(호출 0) · 기준 URL 모양 거절(호출 0) ·
환경 경로(systemd `EnvironmentFile=`) 성공.

## 변이 — 넷 전부 붉다

버릴 사본(`scratchpad/mut/tools/`)에 두 스크립트를 복사해 **경보 스크립트만** 바꿔치우고 같은 검사를
돌렸다. 작업 트리는 건드리지 않았다. 전건(변이 전)은 위 표의 「21 건 전부 통과」다.

| 변이 | 바꾼 것 | 결과 |
|---|---|---|
| ① argv | 설정 대신 **URL 을 인자로** 주는 `curl` 호출로 바꿈 | `BAD — argv 에 자격이 있는 프로세스: <pid>` · 어긋남 1(통과 20) |
| ② 출력 | 실패 문면에 `${BASE_URL}/bot…/sendMessage` 전체를 적음 | `BAD — 403 갈래가 기대와 다르다` · 어긋남 1(통과 20) |
| ③ 전제 | unit 이름 모양 검사를 `true` 로 치환 | `BAD — unit 이름 모양 거부가 서지 않았다(종료 0 · 요청 1)` · 어긋남 1(통과 20) |
| ④ 본문 | 설정에서 `output = "/dev/null"` 줄을 뺌(응답 본문을 버리지 않음) | 어긋남 4(통과 17) — 2xx·느린 갈래·403·환경 경로가 함께 붉다 |

**변이가 검사를 두 번 고치게 했다**(scope.md 「설계에서 두 번 고친 것」). 첫 판에서 ① 과 ③ 은 **초록**
이었다 — ① 은 argv 훑기가 남의 `curl --config` 를 증인으로 보고 일찍 멈춰서, ③ 은 거부 갈래가 이미 죽은
포트를 가리키는 설정으로 돌아 「호출 0」이 접속 실패와 구별되지 않아서다. 둘을 고친 뒤 위 표가 나왔다.

## 음성 대조 — 바늘이 진짜다

부재 단언이 「보내지 않았다」와 구별되는 근거는 검사 안의 양성 대조 셋이다(전부 `ok`):
가짜 서버가 **자격을 경로에서 실제로 받는다** · 출력 훑개가 바늘을 심은 파일에서 그것을 찾는다 ·
argv 훑개가 **미끼 프로세스의 argv** 에서 그것을 찾는다. 셋 중 하나라도 `BAD` 면 나머지 판정은 공허하다.

## 누출 어휘 스캔

```
command grep -rniE -f config/quality/leak-patterns.txt \
  tools/prod-alert-telegram.sh tools/prod-alert-check.sh \
  docs/runbook/m7-7f-production.md .github/workflows/ci.yml milestone-15.md \
  reports/evidence/m15/15m/
```

이 slice 가 만든 둘과 evidence 에서 매치 0. (`docs/runbook/m7-7f-production.md`·`milestone-15.md`·
`.github/workflows/ci.yml` 의 매치는 **이 slice 가 더한 줄 밖**이고, 그 셋은 `LeakPatternGateTask` 의
스캔 루트(`reports/evidence/`) 밖이다.)

## acceptance(정본 = PR head 의 CI)

이 slice 의 검사가 도는 자리는 `container` job 하나다. 그 job 의 나머지 step 은 이미지 빌드·trivy·
compose 기동이 필요해 로컬 실행 비용이 크므로, 전건 판정은 **PR head 의 CI** 가 낸다(15A 라운드 5 와 같은
자리). 새 step 의 명령(`./tools/prod-alert-check.sh`)은 ci.yml 에서 축어이고 위 표에서 그대로 돌았다.
`check`·`ml-engine`·`web`·`mutation` 네 job 은 이 slice 가 건드리지 않는다(소스 겹침 0 — 바꾼 파일은
`tools/*.sh` 둘 · `ci.yml` · 문서 둘 · evidence 다).

## 운영 host 에 하지 않은 것

`/etc` · `systemctl` · 실 Telegram 발송 · `/etc/bidvector-v2/*.env` 의 **값 읽기**. env 파일은 키 이름만
다뤘고(runbook §13.2 가 그 이름의 정본이다), 검사가 쓰는 자격·chat id 는 `mktemp -d` 아래에서 만든 가짜다.

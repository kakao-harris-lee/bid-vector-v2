# M15 / 15M — commands

base `a189e8c58c8a438e2685c8daae8f83b835b925ba` · 브랜치 `feat/m15m-sweep-failure-alert`

환경: WSL2 Ubuntu · `python3 3.12.3` · `curl` · 실 Telegram 과 운영 host 에 닿는 명령은 **하나도 없다**
(가짜 서버는 `127.0.0.1` 의 임의 포트, 거부 대조는 예약 대역 `192.0.2.1`).

## 로컬 실측 (수정 라운드 1 뒤)

| 명령 | 결과 |
|---|---|
| `bash -n` — `prod-alert-telegram.sh`·`prod-alert-check.sh`·`prod-notification-sweep.sh`·`prod-guards-check.sh`·`prod-env.sh` | exit 0 |
| `./tools/prod-alert-check.sh` | exit 0 — **38 건 전부 통과**(앞 판 21) |
| `./tools/prod-guards-check.sh` | exit 0 — **통과 65 · 어긋남 0**(앞 판 61 — 미룸 축 넷이 늘었다) |
| `./tools/workflow-step-refs-check.sh .github/workflows/ci.yml` | exit 0 — job 5 · id 15 · `steps.<id>` 참조 18 · 매달린 참조 0 |
| `python3 -I -c "yaml.safe_load(ci.yml)"` 로 `container` job 의 step 이름 열거 | 끝 네 개가 S-27b · S-27c · S-27d · **S-27e** |
| `systemd-analyze verify`(임시 디렉터리 · 설치 0) — 경보 템플릿 + 훑기 unit + drop-in | 남는 지적은 **「스크립트가 아직 그 자리에 없다」 하나**뿐 — `EnvironmentFile=-`·`SuccessExitStatus=4`·`OnFailure=…@%n.service` 에 지적 0. **음성 대조**: drop-in 에 `BogusKey=` 를 넣으면 그 줄을 집어 경고한다(= drop-in 을 실제로 읽었다) |
| `stat` — `/etc/bidvector-v2/relay.env` | `deploy:deploy 0600`(메타데이터만 봤다 — **값은 읽지 않았다**). §9.5 의 소유 전제를 이 현물에 맞췄다 |
| `command grep -rniE -f config/quality/leak-patterns.txt <신설 둘>` · 고친 다섯의 **더한 줄** · evidence 셋 | 매치 0 |

`prod-alert-check.sh` 38 건(요약): 성공 갈래 아홉(종료 0 · 호출 1 · **경로 양성 대조** · chat id 등식 ·
문면 넷 · 문면에 자격 0) · 출력 훑개 양성 대조와 자격 부재 · **argv 축 넷**(미끼 양성 대조 · 창 증인
둘 · 부재) · **디스크 축 둘**(훑개 양성 대조 · `TMPDIR`·cwd 부재) · 느린 갈래 종료 0 · 403 갈래 ·
전제 거부 넷(chat id 부재 · unit 이름 · 기준 URL 경로 · **루프백 밖 평문 http**) · **끝 슬래시 수용** ·
환경 경로 · **curl 실패 갈래 둘** · **해석기 비실행 둘**(표지 부재 · `source` 양성 대조) ·
**`_safe` 셋** · **chat id 출력 부재 둘** · **env 해석 변종 넷**.

`prod-guards-check.sh` 의 새 넷: 프로젝트 잠금·상태 잠금을 각각 쥔 채 러너가 **`4`** 로 끝나는가(둘,
각각 「일회 컨테이너 앞에서 끝난다」를 함께 본다) · `ExecStopPost` 조건식 전문(`exit 4` 조항 포함) ·
drop-in 의 `SuccessExitStatus=…4`.

## 변이 — 열다섯 전부 붉다

버릴 사본(`scratchpad/15m-r1-exec/mut`·`gmut`)에서 **한 축씩** 바꿔치우고 같은 검사를 돌렸다. 작업
트리는 건드리지 않았다. 전건(변이 전)은 위 표의 「38 건 전부 통과」·「통과 65」다.

### 경보 스크립트 — `prod-alert-check.sh` 가 잡는다

| 변이 | 바꾼 것 | 결과 |
|---|---|---|
| ① argv | 설정 대신 **URL 을 인자로** 주는 `curl` 호출 | `BAD argv 에 자격이 있는 프로세스` · 1/38 |
| ② 403 문면 | 실패 문면에 자격이 실린 전체 URL | `BAD 403 갈래가 기대와 다르다` · 1/38 |
| ③ 전제 | unit 이름 모양 검사를 `true` 로 치환 | `BAD unit 이름 모양 거부가 전제 실패(2)로 서지 않았다(종료 0 · 요청 1)` · 1/38 |
| ④ 본문 | 설정에서 `output = "/dev/null"` 줄을 뺌 | 11/38 — 2xx·느린·403·끝 슬래시·환경·`_safe`·**chat id 출력**·env 변종 넷 |
| ⑤ 임시 파일 | 설정을 `mktemp` 파일에 쓴 뒤 `--config <파일>` | `BAD 자격이 디스크에 있다(창 안·창 뒤)` · 1/38 |
| ⑥ curl 실패 문면 | rc≠0 갈래에 `/bot<자격>/sendMessage` | `BAD curl 실패 갈래의 출력에 바늘이 있다` · 1/38 |
| ⑦ `source` | env 파일을 `. "$ENVF"` 로 읽음(값이 **실행**된다) | 4/38 — 표지 생성 + env 변종 셋 |
| ⑧ `_safe` | `_safe` 를 항등 함수로 | `BAD _safe 치환이 문면에 보이지 않는다` · 1/38 |
| ⑨ chat id | 성공 문면에 chat id 를 덧붙임 | `BAD 출력에 chat id 가 있다: out-ok·out-slow·out-env·out-safe` · 1/38 |
| ⑩ 해석기 | 값 앞뒤 절삭(`_trim`) 제거 | 4/38 — env 변종 넷 전부 |
| ⑪ 평문 | `https` 요구를 `https?` 로 되돌림(루프백 예외 제거) | `BAD 평문 http 거부가 전제 실패(2)로 서지 않았다(종료 1 · 요청 0)` · 1/38 |
| ⑫ 끝 슬래시 | `BASE_URL="${BASE_URL%/}"` 제거 | `BAD 끝 슬래시가 붙은 기준 URL 로 보내지 못했다(종료 2)` · 1/38 |

⑪ 이 **전제 거부를 종료 2 까지 보게 만든 변이**다. 「0 이 아니다」로 두면 거절과 **접속 실패**가 같은
값이라 이 변이가 초록으로 통과한다(먼저 그렇게 재서 확인했다).

### 미룸 갈림 — `prod-guards-check.sh` 가 잡는다

| 변이 | 바꾼 것 | 결과 |
|---|---|---|
| ⑬ 종료 코드 | 러너의 잠금 거절을 다시 `3` 으로 | `BAD … 미룬다(4)` 둘(프로젝트·상태 잠금) · 통과 63 |
| ⑭ drop-in | runbook 에서 `SuccessExitStatus=4` 삭제 | `BAD runbook 에 SuccessExitStatus=…4 가 없다` · 통과 64 |
| ⑮ ExecStopPost | 조건식에서 `[ "$$EXIT_STATUS" = 4 ]` 조항 삭제 | `BAD … 1 줄 중 0 만 조건식 전문과 같다` · 통과 64 |

⑬ 과 ⑭ 가 **둘 다 필요한 이유**: 거동이 갈리는 자리가 스크립트와 systemd 둘이라, 한쪽만 고친 판은
다른 쪽의 단언이 없으면 조용히 통과한다.

## 음성 대조 — 바늘이 진짜다

부재 단언이 「보내지 않았다」와 구별되는 근거는 검사 안의 양성 대조 **다섯**이다(전부 `ok`):
가짜 서버가 **자격을 경로에서 실제로 받는다** · 출력 훑개가 심은 바늘을 찾는다 · **디스크 훑개**가
심은 바늘을 찾는다 · **argv 훑개**가 미끼 프로세스의 argv 에서 찾는다 · **표지 술어**가 `source` 로
실제 실행됨을 보인다. 여기에 chat id 훑개의 양성 대조와 창 증인 둘이 더 붙는다. 하나라도 `BAD` 면
나머지 판정은 공허하다.

## 누출 어휘 스캔

```
command grep -rniE -f config/quality/leak-patterns.txt \
  tools/prod-alert-telegram.sh tools/prod-alert-check.sh reports/evidence/m15/15m/
```

매치 0. 고친 다섯(`tools/prod-env.sh`·`tools/prod-notification-sweep.sh`·`tools/prod-guards-check.sh`·
`docs/runbook/m7-7f-production.md`·`milestone-15.md`)은 **이 slice 가 더한 줄**에서 매치 0 이고, 그 다섯은
`LeakPatternGateTask` 의 스캔 루트(`reports/evidence/`) 밖이다.

## acceptance(정본 = PR head 의 CI)

이 slice 의 검사가 도는 자리는 `container` job 하나이고, 이번 라운드는 그 job 의 **두 step**(S-27d ·
S-27e)을 건드린다. 둘 다 로컬에서 축어로 돌았다. 나머지 step 은 이미지 빌드·trivy·compose 기동이
필요해 전건 판정은 **PR head 의 CI** 가 낸다(15A 라운드 5 와 같은 자리). `check`·`ml-engine`·`web`·
`mutation` 네 job 은 이 slice 가 건드리지 않는다 — 바뀐 파일은 `tools/*.sh` 다섯 · `ci.yml` · 문서 둘 ·
evidence 다.

## 운영 host 에 하지 않은 것

`/etc` · `systemctl` 변경 · 실 Telegram 발송 · `/etc/bidvector-v2/*.env` 의 **값 읽기**. 그 파일은
`stat` 으로 소유·권한만 봤다(§9.5 의 전제를 현물에 맞추기 위해). 검사가 쓰는 자격·chat id 는
`mktemp -d` 아래에서 만든 가짜이고 **run 마다 유일하다**.

# M15 / 15Q — commands

base `71b1ad3a7` · 브랜치 `feat/m15q-telegram-activation`

환경: WSL2 Ubuntu · `docker compose 5.1.3` · bash · `python3`. **운영 host·실 Telegram·실 API 에 닿는
명령은 하나도 없다.** `/etc/bidvector-v2/*.env` 는 읽지도 `stat` 하지도 않았다.

## compose 의 env 파일 의미 — 실측 다섯

지시와 앞 판 §13.3 이 적은 「둘째 `--env-file`」이 사실인지부터 쟀다. 저장소와 무관한 **버릴 fixture**
(`busybox:1.36` · 프로젝트 이름 `bvq-envprobe`·`bvq-envprobe2` · scratchpad 안)로 돌렸고, 끝나고 치웠다.

| 실측 | 무엇을 물었나 | 결과 |
|---|---|---|
| A | 최상위 `--env-file` 둘을 주면 컨테이너가 받는가 | **`NONE`** — 둘 다 받지 않는다. 최상위 `--env-file` 은 **보간 전용**이다 |
| B | `run --env-from-file <파일>` 은 받는가 | **`RELAY_PROBE=relay`** — 받는다. 이것이 컨테이너까지 가는 통로다 |
| C | `run -e NAME`(값은 셸 env) 은 받는가 | **`RELAY_PROBE=fromshell`** — 받는다(§13.2 가 말한 둘째 길) |
| D | `--env-from-file` 은 서비스 선언 env 를 **더하는가 덮는가** | `STACK_PROBE=fromcompose`(선언 유지) · `SHARED_PROBE=**fromrelayfile**`(같은 이름은 **파일이 이긴다**) |
| E | D 의 통제군(`--env-from-file` 없이) | `SHARED_PROBE=fromcompose` · `STACK_PROBE=fromcompose` — D 의 뒤집힘이 그 플래그의 것임을 가른다 |

A 가 이 slice 의 설계를 바꿨다(앞 판 처방대로 짰으면 첫 실발송이 `RefusedMissingChannelConfig` 로
끝났다). D 가 **키 집합 등식을 전제로** 만들었다 — 그 파일은 세 값을 더하는 자리가 아니라 컨테이너 env 를
덮을 수 있는 **새 설정 통로**다.

`docker compose run --help` 에 `--env-from-file` 이 있고 `--env-file` 은 **없다**(그래서 래퍼가 지원
여부를 판 번호가 아니라 그 줄로 묻는다).

## 로컬 실측

| 명령 | 결과 |
|---|---|
| `bash -n` — `prod-notification-relay.sh`·`prod-evaluation-commit.sh`·`prod-env.sh`·`prod-guards-check.sh` | exit 0 |
| `./tools/prod-guards-check.sh` | exit 0 — **통과 216 · 어긋남 0**(74 → 175 → 216. 수정 라운드 1 이 41 건을 더했다) |
| `./tools/prod-alert-check.sh` | exit 0 — 49 건 전부 통과(이 slice 가 `tools/prod-env.sh` 를 만지므로 함께 돌린다) |
| `./tools/workflow-step-refs-check.sh .github/workflows/ci.yml` | exit 0 — job 5 · id 15 · `steps.<id>` 참조 18 · 매달린 참조 0 |
| `command grep -rniE -f config/quality/leak-patterns.txt <신설 둘>` | 매치 **0** |
| 고친 넷(`prod-env.sh`·`prod-guards-check.sh`·runbook 둘·장부 둘)의 **더한 줄**에 같은 스캔 | 매치 **0** |
| 신설 둘에 `file:line` 좌표 | **0** 건(절 번호·결정 ID·인용문으로만 가리킨다) |

더한 101 건의 갈래: **평가 커밋 23**(트리·프로젝트 이름·잔존·이름 충돌 · 여력 인자 둘 · 금지 JVM
옵션 셋 · 양성과 로그 일곱 · 미룸 둘 · 잠금 파일 하나 · flock 여섯) · **relay 66**(같은 전제 여덟 ·
채널 env 축 **열셋** · 플래그 지원 둘 · 양성과 로그 열하나 · §13.4 단계 실행 셋 · 코드 옮김 여섯 ·
미룸 셋 · 잠금 파일 하나 · flock 여섯 · 그 밖) · **신호 경로 12**(평가·relay 각 여섯) ·
**runbook 2**(ExecStopPost 이름 집합 · relay drop-in 의 `5` 포함·`6` 제외).

## 최종 라운드 — 경로 인용 · 보간 조회 집합 · 변이 여섯

| 측정 | 결과 |
|---|---|
| **v L-2** 같은 디렉터리의 `relay.env`(깨끗) 옆 `relay.env #x`(우회 줄 포함)를 stub 에 넣는다 | 따옴표 없으면 키 **3**(이웃을 읽었다 — ` #` 이 YAML 주석) · `jq -Rn --arg` 로 넣으면 키 **4**(그 파일을 읽었다) |
| **v L-3** `…ROUTEKEY=${HOME:?x}` 를 `config` 에 건다 | 평소 env: rc **0** · 키 3(통과) → `env -i PATH=`: rc **1** · 키 0 |
| 같은 자리에서 `${PATH:?x}` | `env -i PATH=` 에서도 rc **0** · 키 3 — **좁힌 env 가 못 보는 한 칸**. 그래서 「값에 `$` 를 쓰지 않는다」가 그 칸을 닫는다 |
| `env -i` 를 더 좁힐 수 있나(`env -i <절대 docker>` 만) | rc 0 · 키 3 으로 **돈다** — 다만 하네스의 가짜 docker 가 `#!/usr/bin/env` 라 `PATH` 가 필요하다. 그 한 이름을 남긴다 |
| `./tools/prod-guards-check.sh` | exit 0 — **통과 230 · 어긋남 0**(216 → 221 → 230) |
| `./tools/prod-alert-check.sh`(핀 `shfmt v3.14.1`) | exit 0 — **87 건 전부 통과**(main 병합으로 15P 의 AST 술어가 들어왔다) |
| `./tools/workflow-step-refs-check.sh .github/workflows/ci.yml` | exit 0 — 매달린 참조 0 |
| `systemd-analyze verify` | 남는 지적은 「스크립트가 아직 그 자리에 없다」 하나뿐 |

### 변이 여섯 — 다섯 RED · 하나는 **예상된 겹침**

| 변이 | 바꾼 것 | 결과 |
|---|---|---|
| P1 | stub 경로를 따옴표 없이 넣는다 | **RED** 228/2 |
| P2 | 값의 `$` 를 받아들인다 | **RED** 227/3 |
| P3 | 해석을 평소 env 로 돌린다(`env -i` 제거) | **초록 — 예상된 겹침**(아래) |
| P4 | `jq` 왕복 문턱을 걷는다 | **RED** 228/2 |
| P5 | 도구 어긋남을 compose 거절과 같은 코드로 접는다 | **RED** 228/2 |
| P6 | 재는 경로와 넘기는 경로를 다시 가른다 | **RED** 228/2 |

**P3 은 숨기지 않고 적는다.** 보간이 일어나려면 값에 `$` 가 있어야 하고, 그 `$` 는 **앞의 좁히는
전제**가 먼저 끊는다 — 그래서 `env -i` 를 걷어도 오늘의 어떤 입력도 거동이 바뀌지 않는다. 두 기제가
완전히 겹치므로 `env -i` 는 **오늘 RED 로 만들 수 없다**. 그래도 남긴 이유는 pipefail·SIGPIPE 를 걷을
때와 같다: 그 전제가 언젠가 느슨해지면 **구조 쪽이 축을 받는다**. 「오늘 RED 로 못 만드는 **단언**은
장식」이라는 규율은 단언에 대한 것이고, 여기서 단언을 더하지 않은 것이 그 규율을 지키는 쪽이다.

**첫 시도에서 넷이 초록이었다** — 단언이 주장한 것을 재지 않고 있었다(겹친 기제 둘 · 같은 문자열인
두 경로 · 앞 문턱이 가린 rc 갈림). 각각을 **단독으로 가르는 입력**으로 바꾼 뒤 다섯이 붉어졌다:
`${PATH:?x}`(전제를 단독으로) · **심볼릭 링크**(원문 ≠ 해석된 경로) · `TMPDIR` 깨뜨리기(`jq` 문턱이
가리지 못하는 `mktemp` 갈래).

## 수정 라운드 1 — 두 해석기가 같은가 (v H-1 처방 (i) 의 전제)

키 집합을 **소비자와 같은 해석기**로 뽑기로 했으므로, 그 「같다」가 지어낸 전제가 아님을 먼저 쟀다.
버릴 프로젝트 이름(`bvq-parser-probe`·`…2`)의 `busybox` fixture 로 돌렸고 끝나고 치웠다(남은 컨테이너 0).
비교한 것은 **키 집합**뿐이다 — 값은 화면에 내지 않는다.

| 입력 | `config` + `env_file:` ↔ `run --env-from-file` |
|---|---|
| 정상 셋 | **같다**(키 셋) |
| 우회 ① `…ROUTEKEY="rk" BIDVECTOR_PERSISTENCE_JDBCURL=…` | **같다** — 양쪽 모두 `BIDVECTOR_PERSISTENCE_JDBCURL` 을 **본다** |
| 우회 ② 홑따옴표 판 | **같다** — 양쪽 모두 본다 |
| 우회 ③ `…="rk"SPRING_APPLICATION_JSON={}`(공백 없음) | **같다** — 양쪽 모두 `SPRING_APPLICATION_JSON` 을 본다 |
| 우회 ④ `…="rk" JAVA_TOOL_OPTIONS=-XX:…` | **같다** — 양쪽 모두 `JAVA_TOOL_OPTIONS` 를 본다 |
| 버려짐 ① 닫히지 않은 따옴표 | **같다**(둘 다 키 0) |
| 버려짐 ② `"v" !!x` | **같다**(둘 다 키 0) |
| 버려짐 ③ `${X:?m}` | **같다**(둘 다 키 0) |
| 빈 파일 | **같다**(둘 다 키 0) |

**아홉 입력 전부에서 같다.** 우회 넷은 「앞 판 술어가 못 보던 키를 **소비자는 본다**」의 직접 측정이고,
그래서 키 집합을 compose 에게 물으면 그 넷이 **허용 집합 밖**으로 드러난다.

### 버려짐 넷의 종료 코드 — 두 통로가 **다르게** 말한다

| 입력 | `config` + `env_file:` | `run --env-from-file`(verifier 실측) |
|---|---|---|
| 정상 셋 | rc 0 · 키 3 | 키 3 |
| 닫히지 않은 따옴표 | **rc 1** · 키 0 | **rc 0 · 파일 전체 무시** |
| 값 뒤 `!!` | **rc 1** · 키 0 | **rc 0 · 무시** |
| `${X:?m}` | **rc 1** · 키 0 | **rc 0 · 무시** |
| `KEY=` 아닌 줄 | **rc 1** · 키 0 | **rc 0 · 무시** |
| 빈 파일 | rc 0 · 키 0 | 키 0 |

**이 비대칭이 검사의 값이다** — 소비자가 조용한 자리에서 래퍼가 크게 말한다(「통째로 버렸다」, exit 3).
그대로 두면 §13.2 를 채웠는데도 러너가 `RefusedMissingChannelConfig`(1)로 끝나 진단이 엉뚱한 곳을
가리킨다. 래퍼는 두 갈래(rc 비-0 · 키 0 인데 내용 줄 있음)를 **한 문면**으로 모은다.

## unit 문면 — systemd 에게 직접 물었다

§13.3 의 `ini` 블록 **여덟**(unit 둘 · timer 둘 · drop-in 넷)을 runbook 에서 **그대로 떠내** 임시
디렉터리에 쓰고 `systemd-analyze verify` 를 돌렸다(`/etc` 와 `systemctl` 을 건드리지 않는다. 자리표시자
셋은 버릴 값으로 바꿨다 — 묻는 것은 거동이 아니라 문법이다).

| 대상 | 결과 |
|---|---|
| `bidvector-evaluation-commit.service` | 지적 **1 건** — 「스크립트가 아직 그 자리에 없다」뿐. `SuccessExitStatus=4`·`ExecStopPost=`·`OnFailure=`·`OnCalendar=` 에 지적 **0** |
| `bidvector-notification-relay.service` | 지적 **1 건** — 같은 하나뿐. **`SuccessExitStatus=4 5`**(값 둘)에 지적 **0** |
| **음성 대조** — `10-defer.conf` 에 `BogusKey=1` 을 넣는다 | `Unknown key name 'BogusKey' in section 'Service'` — **drop-in 을 실제로 읽었다**(읽지 않았다면 초록이 공허하다) |

남는 그 한 건은 **반입 전이라 당연한 것**이다(알려진 제한 2 — `<저장소>` 에 이 두 파일이 아직 없다).

## 변이 — 스무 건 전부 RED

산출물을 **커밋한 뒤** 한 자리씩 바꿔치우고 `prod-guards-check.sh` 를 돌린 뒤 `git checkout --` 로
되돌렸다(미커밋 산출물을 함께 지우지 않게 커밋이 먼저다). 변이 뒤 트리는 매번 깨끗했다.

| 변이 | 바꾼 것 | 결과(통과/어긋남) |
|---|---|---|
| M1 | relay 의 `--env-from-file` 을 run 에서 뺀다 | **RED** 174/1 — 「둘째 env 파일이 일회 컨테이너까지 간다」 |
| M2 | 채널 env 키 집합 등식을 걷는다 | **RED** 171/**4** |
| M3 | relay 의 프로세스 환경 JVM 옵션 검사를 걷는다 | **RED** 173/2 |
| M4 | relay 의 채널 env JVM 옵션 스캔을 걷는다 | **RED** 173/2 |
| M5 | 러너 `LEASE_BUSY` 를 래퍼의 `3` 으로 되돌린다 | **RED** 174/1 |
| M6 | 러너 `ENV_SUPPRESSED` 를 래퍼의 `4` 로 되돌린다 | **RED** 174/1 |
| M7 | relay 의 경합/전제 갈림을 「늘 3」으로 | **RED** 173/2 |
| M8 | `--env-from-file` 지원 문턱을 걷는다 | **RED** 173/2 |
| M9 | 채널 env 필수 키 검사를 걷는다 | **RED** 172/3 |
| M10 | relay 의 잔존 검사를 걷는다 | **RED** 173/2 |
| M11 | relay 의 전역 이름 충돌 검사를 걷는다 | **RED** 173/2 |
| M12 | 평가의 여력을 `0` 으로 **지어낸다** | **RED** 173/2 |
| M13 | 평가의 JVM 옵션 검사를 걷는다 | **RED** 172/3 |
| M14 | 평가 래퍼가 `max-accounts-per-run=10` 을 덮는다 | **RED** 174/1 |
| M15 | 평가의 경합/전제 갈림을 「늘 3」으로 | **RED** 173/2 |
| M16 | 평가의 잔존 검사를 걷는다 | **RED** 173/2 |
| M17 | relay drop-in 을 `SuccessExitStatus=4` 로(5 를 뺀다) | **RED** 174/1 |
| M18 | relay drop-in 을 `4 5 6` 으로(6 까지 성공으로) | **RED** 174/1 |
| M19 | 평가 unit 의 `ExecStopPost` 가 **훑기** 컨테이너를 지운다 | **RED** 174/1 — 이름 집합 |
| M20 | relay unit 의 `ExecStopPost` 에서 미룸 조항을 뺀다 | **RED** 174/1 — 조건식 전문 |

**M17 과 M18 이 한 쌍이다** — 제외 목록은 한 방향으로만 틀릴 수 있는 것이 아니다. `5` 를 빼지 않으면
임대 경합마다 경보가 울고(M17), `6` 까지 빼면 **기다려도 풀리지 않는 설정 오류가 조용해진다**(M18).
**M19 와 M20 도 한 쌍이다** — 조건식 전문(무엇에서 건너뛰는가)과 이름 집합(무엇을 지우는가)은 다른
축이고, 한쪽만 재면 나머지가 열린다.

**M12 는 「지어내지 않는다」의 직접 측정이다.** 기본값을 `0` 으로 두면 인자 없는 run 이 그대로 돌고,
용량 보류 판정이 조용히 사라진다 — 그 변이가 두 단언을 깬다.

## 수정 라운드 1 의 변이 — 열셋 전부 RED

| 변이 | 바꾼 것 | 결과(통과/어긋남) |
|---|---|---|
| N1 | 키 집합을 **옛 텍스트 문법**(줄마다 첫 `KEY=`)으로 되돌린다 | **RED** 200/**16** — 우회 넷이 전부 열린다 |
| N2 | 「통째로 버렸다」 갈래를 걷는다 | **RED** 213/3 |
| N3 | 채널 env 의 other 권한 검사를 걷는다 | **RED** 214/2 |
| N4 | relay 의 `_need` 를 걷는다 | **RED** 214/2 |
| N5 | 평가의 `_need` 를 걷는다 | **RED** 214/2 |
| N6 | **훑기**의 `_need` 를 걷는다 | **RED** 214/2 |
| N7 | 빈 도움말을 「플래그를 모른다」와 한 문면으로 접는다 | **RED** BAD 30 |
| N8 | relay unit 에서 `After=` 를 뺀다 | **RED** 215/1 |
| N9 | **훑기 unit 의 `ExecStopPost`** 를 지운다(산문 줄은 남긴다) | **RED** 215/1 — 걸쇠를 `^ExecStopPost=` 에 묶은 뒤라 산문이 메우지 못한다 |
| N10 | 도움말 질의를 한 번 더 돌린다 | **RED** 215/1 — `' run '` 줄이 셋 |
| N11 | 평가의 이름 충돌 조회에서 `-a` 를 뺀다 | **RED** 214/2 |
| N12 | relay 의 HUP trap 을 걷는다 | **RED** 213/3 |
| N13 | 평가의 HUP trap 을 걷는다 | **RED** 213/3 |

**N1 이 이 라운드의 핵심이다** — 앞 판 문법으로 되돌리면 **16 건**이 붉다. 그 수가 「우회 줄이 거절되는
것은 우리 문법이 아니라 **소비자의 키 집합** 때문」을 가른다(가짜 docker 가 그 호출만 진짜로 넘기므로,
붉어진 단언이 재는 것은 compose 자신이다).

**N9 가 cr L-4 의 쌍이다** — 걸쇠를 묶기 전에는 산문 줄이 집합 등식을 메워 이 변이가 초록이었다.

## 자기 검토에서 고친 것 — 짧게 끊는 읽개 + `pipefail`

변이를 돌린 뒤 스크립트를 다시 읽다가 **깨지기 쉬운 꼴 둘**을 찾았다. `… | grep -q` 는 맞추는 순간
끝나고, 그때 쓰는 쪽이 SIGPIPE 로 죽는다 — `set -o pipefail` 아래에서 그 **141** 이 파이프라인의 종료
상태가 되어 **맞췄는데도 비-0** 이 된다. 자리는 둘이었다: 채널 env 의 필수 키 조회와
`run --env-from-file` 지원 질의. 둘 다 **값으로 먼저 받아** 맞추도록 고쳤다.

| 측정 | 결과 |
|---|---|
| 위험 자체의 **양성 대조** — 긴 입력(`seq 1 2000000 \| grep -qxF 1`) | **rc=141** — 맞췄는데도 비-0 이다 |
| 같은 꼴, 짧은 입력(세 줄) | **rc=0** — **세 줄짜리 fixture 로는 이 위험이 드러나지 않는다** |
| 이 host 의 현물로 그 꼴을 그대로 — `docker compose run --help \| grep -q` | **rc=0**(도움말 41 줄 · 플래그는 14 번째 줄 — 파이프 버퍼에 다 들어간다) |
| 고친 꼴(값으로 받기) | 찾음 |

**그러므로 이것은 고친 결함이 아니라 걷어 낸 꼴이다** — 현물 크기(허용 키 ≤7 줄 · 도움말 41 줄)에서는
발화하지 않는다. 그래서 **이 축에는 새 단언을 달지 않았다**: 오늘 RED 로 만들 수 없는 단언은 측정이
아니라 장식이다. 대신 그 자리의 기존 양성 단언(필수 키가 있으면 `exit 0` · 플래그를 알면 run 이 뜬다)이
회귀를 받는다 — 고친 뒤 **M8·M9 를 다시 돌려 둘 다 RED** 임을 확인했다(173/2 · 172/3).

## 양성 대조 — 가짜 쪽이 거꾸로 단언하는 것

`prod-guards-check.sh` 의 가짜 `docker`·`git` 이 이미 셋을 거꾸로 단언한다(`GIT_*` 가 보이면 99 ·
`-C` 가 기대 루트가 아니면 98 · `ps` 에 `-a` 가 없으면 멈춘 행을 숨김). 이 slice 는 **넷째**를 더했다:
`run --help` 가 `--env-from-file` 을 **말하지 않는 판**(`SHIM_NO_ENV_FROM_FILE`)에서 relay 가 `exit 3`
으로 멈추는가. 그 토글이 없으면 지원 문턱은 「늘 통과」라 측정이 아니다(M8 이 그 쌍이다).

새 단언의 **양성 대조**는 갈래마다 짝으로 있다 — 거절을 재는 자리마다 「정상 입력이면 `exit 0` 이고
run 이 뜬다」를 같은 블록에서 잰다(relay 의 `_relay_env` 되돌림 · 평가의 `EVAL_ARGS` 양성 run).
그 짝이 없으면 「거절한다」가 「아무것도 안 한다」와 같은 값이다.

## 운영 host 에 하지 않은 것

`/etc` 변경 · `systemctl`·`daemon-reload`·타이머 활성화 · 실 Telegram 발송 · 실 API 호출 ·
`/etc/bidvector-v2/*.env` 의 값 읽기(`stat` 도 하지 않았다) · 운영 compose 기동 · 공유 잠금 자리
(`/run/lock/bidvector-v2`)의 **모드 변경**. 검사가 쓰는 잠금 파일은 검사 전용 프로젝트 이름
(`bv-guards-check-<pid>`)이고 끝에 지운다. 검사가 쓰는 채널 env 의 세 값은 `mktemp -d` 아래의
자리값(`fixture`·`https://example.invalid`)이고 자격이 아니다.

## acceptance(정본 = PR head 의 CI)

이 slice 의 검사가 도는 자리는 `container` job 의 **S-27d** 하나이고(새 step 을 만들지 않았다), 그
명령은 로컬에서 축어로 돌았다. 나머지 step 은 이미지 빌드·trivy·compose 기동이 필요해 전건 판정은
**PR head 의 CI** 가 낸다. `check`·`ml-engine`·`web`·`mutation` 네 job 은 이 slice 가 건드리지 않는다 —
바뀐 파일은 `tools/*.sh` 넷 · 문서 둘 · 장부 둘 · evidence 다(**Kotlin·TypeScript·Python 은 한 줄도
바뀌지 않았다**).

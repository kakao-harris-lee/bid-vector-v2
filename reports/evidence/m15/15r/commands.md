# M15 / 15R — commands

base `928ac2fa0` · 브랜치 `fix/m15r-activation-runbook`

환경: WSL2 Ubuntu · bash · `docker compose`(stub 해석에만) · `shfmt v3.14.1`(앞 세션이 받아 둔 바이너리를
`config/quality/shell-guard-policy.properties` 의 핀과 **체크섬 대조**한 뒤 PATH 에 뒀다 — 일치).
**운영 host·systemd·운영 컨테이너·실 Telegram·실 API 에 닿는 명령은 하나도 없다.** `/etc/bidvector-v2/*.env`
는 읽지도 `stat` 하지도 않았다. `/proc/sys/kernel/core_pattern` 은 **읽기만** 했다. 실측은 scratchpad 와
버릴 compose 프로젝트 이름, 버릴 `DOCKER_CONFIG` 에서만 돌았다.

## acceptance

| 명령 | 결과 |
|---|---|
| `./tools/prod-guards-check.sh` | exit 0 — **통과 269 · 어긋남 0** · 18~19 초 · `C`·`en_US.utf8`·`ko_KR.utf8` 셋 다 같다 |
| `./tools/prod-alert-check.sh` | exit 0 — 87 건 전부 통과(문면만 고친 `prod-alert-telegram.sh` 를 포함해 그대로 초록) |
| `bash -n` — 래퍼·`prod-env`·`prod-guards-check`·`prod-alert-telegram` **파일별** | 각 exit 0 |

`bash -n a b c` 는 **첫 파일만 파싱한다**(둘째에 구문 오류를 둬도 rc 0 · 셋째 인자는 위치 매개변수가
된다 — 실측). 그래서 acceptance 를 파일별 호출로 고쳤다. `./gradlew check` 는 이 레인이 돌리지 않는다
(빌드 자리는 팀장이 배정한다) — **마지막 HEAD 의 게이트 결과 정본은 verifier 와 PR 조치 코멘트다.**

## 지적을 적용하기 전에 전제를 쟀다

| 전제 | 결과 |
|---|---|
| `core_pattern` 이 파이프 핸들러인가(sec N-1) | **그렇다** — `\|` 로 시작한다. `core(5)` 의 "RLIMIT_CORE limit is not enforced for core dumps that are piped to a program" 도 이 host 의 man 에 있다 → **`LimitCORE=0` 은 효과가 없다** |
| 기존 `0700` 디렉터리에 꼴 넷(v F1) | `install -d -m 755` · `install -m 755 -d` · `install -dm755` · `chmod 755` → **전부 755** · `mkdir -p -m 755` → 700(무해) |
| 가드를 `&&` 로 뒤집으면(v F2) | 채운 파일 24바이트 → **0바이트** · 모드 600 → **실제 사고다** |
| `currentContext` 가 셋째 축인가(v F6) | **그렇다** — 버릴 `DOCKER_CONFIG` 에 `currentContext` 를 세우면 두 변수를 비운 셸도 그 context 로 간다. `DOCKER_CONTEXT=default` 는 **그것을 이긴다**(둘 다 `docker context show` 로 실측) |
| `prod-alert-check.sh` 가 경보 스크립트의 진단 문면을 고정하는가(cr L-3) | **아니다** — 그 파일의 `EnvironmentFile` 언급은 자기 주석·자기 `_ok` 문면이다. 문면을 고친 뒤 87 건 그대로 초록 |
| 로캘이 기대 파일 대조를 뒤집는가(cr M-5 · v H3) | **뒤집는다** — 고치기 전 `LC_ALL=en_US.utf8 ./tools/prod-guards-check.sh` 가 **exit 1 · 어긋남 2**(같은 줄 스물셋이 「사라진 줄」과 「새로 생긴 줄」 양쪽에 동시에 서는 **거짓 빨강**). 양쪽을 `LC_ALL=C` 로 핀한 뒤 `C`·`en_US.utf8`·`ko_KR.utf8` 셋 다 **exit 0 · 269 · 0** |
| 채운 `prod.env` 를 §1 ③ 의 앞 판이 덮는가(cr M-6) | **덮는다** — 41바이트(운영자 값 1) → 템플릿 2911바이트(값 0) · rc 0 · 출력 없음 |
| runbook 의 틸드 펜스(v H1) | **0 줄**(단언으로 고정 — 생기면 그 안이 수집 밖이라 붉는다) |
| systemd 가 꼬리 `#` 를 주석으로 보는가(sec R-1) | **아니다**(버릴 unit 사본에 `systemd-analyze verify` — 설치·기동·reload 없음). `SuccessExitStatus=4 5 # 7 도 뺀다` → `#`·`도`·`뺀다` 만 거절하고 **`7` 에는 말이 없다**(= 받는다). 양성 대조로 `… # 300 도 뺀다` 를 주니 `300` 도 거절 — **`#` 뒤를 계속 값으로 읽는다**. `LimitCORE=0 # 메모` 는 「Failed to parse resource value: `0 # 메모`」로 **지시 전체가 버려진다**(rlimit 가 아예 안 선다). `man systemd.syntax` 도 「lines starting with」만 든다 |

## 0..255 전수 스윕의 비용 — 재고 받지 않았다 (v G3)

구현해서 **실측**했다: 전건이 **16.6 초 → 94.2 초**(+77.6 초 · **5.7 배**). 매 PR 의 `container` job 과
**매 로컬 반복**에 붙는 값이라 「수 초~수십 초」 문턱 밖이다. 그래서 지시의 대안대로 **대표 구간 + 구조**로
나눴다 — ⓐ 거동은 대표 구간 열여섯(러너 다섯 · 래퍼 셋 · `docker` CLI 125·126·127 · 신호 128·137·143 ·
상한 255)을 재고(+2.7 초), ⓑ 「가지 전수 == 인식 수」의 **느슨한 쪽을 「빈 줄이 아닌 모든 줄」**로 세어
가지의 **꼴** 축을 비용 없이 닫는다. 그 둘이 스윕이 덮던 공간을 덮는 근거: 가지가 어떤 꼴로 더해지든
분모가 늘어 붉고(G3 의 두 꼴로 실측), 라벨이 파싱되는 꼴은 **집합 등식**과 **번역 값 ∩ 러너 값 = ∅** 이
다시 받는다(라운드 1 에서 실측).

## 멱등 꼴의 거동 — 자격 파일 둘 (cr L-1 · cr M-6)

버릴 디렉터리·자리값에서 쟀다(`/etc` 에 닿지 않았다).

**`relay.env`**(§13.2) | A) 부재 → **0바이트 · 600** · B) 채운 파일(24바이트·644)에 **다시** → **24바이트 ·
600**(sha256 앞 12자리 동일 `a7fabf7066f8` — 내용 그대로, 권한만 복구) · C) 대조로 앞 판
(`install … /dev/null`) → **0바이트**(rc 0 · 출력 없음).

**`prod.env`**(§1 ③ · 이 라운드) | 앞 판(가드 없는 `install <템플릿>`)은 채운 **41바이트 → 템플릿
2911바이트**로 되돌리고 운영자 값 계수가 **1 → 0** 이 됐다(rc 0 · 출력 없음). 새 꼴에서는 B) 채운 파일
재실행 → **41바이트 그대로 · 값 계수 1 · 모드 600** · A) 부재 → **템플릿 2911바이트 · 600**.
이 파일은 PG 암호와 운영자 자격을 담아 `relay.env` 보다 무겁다.

## 설계 검토 (2) 의 실측 둘

**3번 — trap·`set -e` 와 `case` 의 겹침.** 없다. 「신호 종료」는 **두 자리에서 뜻이 다르다** — ⓐ 래퍼
자신이 받은 신호는 trap 이 받아 같은 신호로 자살하므로(TERM **143** · HUP **129**) `case` 에 닿지 않고,
ⓑ **일회 컨테이너**가 신호로 죽으면 `docker compose run` 이 128+n 을 돌려주므로 그 값은 `case` 로 와서
`7` 이 된다. `case` 가 낼 수 있는 값은 `0|1|2|5|6|7` 뿐이라 143·129 가 곧 ⓐ 의 실측이다.

**6번 — 이 host·CI 러너가 `DOCKER_HOST` 없이 도는가.** 돈다(두 변수 미설정 · context `default`).
`.github/workflows/ci.yml` 에 두 변수를 세우는 자리가 **없다**.

## D-15R-8 ③ 의 근거 — 임대는 owner 별이 아니다

`PostgresAdvisoryLockLease` 가 쥐는 키는 `lockKeyFor(kind)` 가 **`OutboxConsumerKind` 하나로** 정하는
상수이고 질의는 `pg_try_advisory_lock(?)` 에 그 키만 넣는다 — owner 는 들어가지 않고 claim 질의에도 없다.
그래서 「같은 owner 의 relay 가 임대를 쥐고 있다」는 거짓이다(다른 `--owner` 로 다시 쳐도 같은 자물쇠에서
선다). 래퍼 문면 · §13.3 drop-in 주석 · `m6-6e` §3.2·§3.8 네 자리를 그 사실로 고쳤다.

## 변이 — 버릴 clone 에서 하나씩 바꿔치우고 `git diff --numstat` 으로 적용을 먼저 봤다

통제군(변이 없음) **260 · 0 · exit 0**. 매 변이 뒤 되돌려 `git status --porcelain` 이 빈 것을 확인했다.
`RelayLines.kt`(out_of_scope)도 그 사본에서만 만졌다.

「**앞 판 GREEN**」은 세 판정(verifier·code-reviewer·보안 리뷰)이 **실제로 통과시킨 꼴**이다. 스물셋이
전부 같은 병이었다 — 술어가 꼴을 열거한다. 판정은 라운드 1 에, **수집**은 라운드 2 에 집합 등식으로 바꿨다.

**자격·상태 디렉터리 경로 — 줄 집합 등식**(기대: `config/quality/runbook-prod-path-lines.txt`)

| 변이 | 결과 |
|---|---|
| `install -d -m 755 <디렉터리>` 되살리기 | RED 1 |
| `install -m 755 -d` · `install -dm755` · `chmod 755 <디렉터리>` | 각 **RED 1**(앞 판 GREEN — v F1) |
| `mkdir -p -m 755`(거동은 무해) | **RED 1** — 집합 밖이면 무해해도 붉다(래칫) |
| 끝 슬래시 `…/bidvector-v2/` · 따옴표 경로 `"…"` | 각 **RED 1**(앞 판 GREEN — v G1) |

**자격 파일(relay.env) — 줄 집합 등식**(기대: `runbook-relay-env-lines.txt`)

| 변이 | 결과 |
|---|---|
| `test -e` 가드 떼기 · `&&` 로 뒤집기 · `;` 로 잇기 · 다른 파일 가드 · `cp /dev/null` | 각 **RED 1**(뒤 넷은 앞 판 GREEN — v F2) |
| **들여쓴 목록 안 코드 블록**에 가드 없는 BAD 줄 축어 | **RED 2**(앞 판 GREEN — v G2(a) · 그 펜스가 여덟 개였다) |
| `sed -i "s/.*//"` · `rm -f` · `mv` · `ex -sc "%d\|x"` · `ln -sf` | 각 **RED 2**(앞 판 GREEN — cr M-4 · `sed -i` 는 이 문서의 집안 관용구다) |
| 변수 경유(`F=<경로>; install … "$F"`) | **RED 2**(앞 판 GREEN — 대입 줄이 경로를 담아 걸린다) |

**unit 지시 — 구간·값 집합 등식**

| 변이 | 결과 |
|---|---|
| `LimitCORE=0` 제거 · `[Unit]` 로 옮김 · 뒤에 `infinity` 로 덮기 | 각 **RED 1**(뒤 둘은 앞 판 GREEN — v F4) |
| `LimitCORE=0 # 메모`(꼬리 주석) | **RED 2**(앞 판 GREEN — sec R-1. systemd 는 **지시 전체를 버린다**) |
| 템플릿을 `LimitCORE = 0` 으로 · 템플릿의 `User=deploy` 를 바꿈 | 각 **RED 1**(앞 판 단언 없음 — cr L-10) |
| `EnvironmentFile=` 되살리기 · 등호 공백 꼴 · 줄 머리 공백 | 각 **RED 2**(뒤 둘은 앞 판 GREEN — v F5) |
| `Environment=` 로 그 키를 실음 | **RED 2**(앞 판 단언 없음 — cr L-4) |
| `SuccessExitStatus=4 5 7` | **RED 1**(앞 판 GREEN — sec N-2) |
| `SuccessExitStatus=4 5 # 7 도 뺀다` | **RED 1**(앞 판 GREEN — sec R-1 의 마지막 구멍) |
| `ExecStopPost` 한 줄의 접두 바꾸기 | RED 1 |

**종료 코드 — 거동·집합·교집합**

| 변이 | 결과 |
|---|---|
| 래퍼 `case` 를 앞 판(`*) exit "$rc"`)으로 | RED 7(거동 다섯 + 문면 + 집합) |
| `RelayExitCode` 에 여섯째 `(5)` | RED 1 |
| `NEW_CODE(5), // 주석` · 여러 줄 꼴 | 각 **RED 1**(앞 판 GREEN — v F3. 항목 수를 **다른 술어**로 센다) |
| 래퍼에 `"8") exit 0` · `12[5-7]) exit 4` | 각 **RED 2**(앞 판 GREEN — 가지 전수 + 교집합) |
| 래퍼에 `125 \| 126) exit 5`(공백 낀 대안) · `(125) exit 5` | **RED 3 · RED 2**(앞 판 GREEN — v G3) |
| 러너에 `5` + 래퍼에 `5) exit 5`(집합은 같아진다) | **RED 2** — 거동 · **교집합 `{5}`**(cr M-3) |

**그 밖**

| 변이 | 결과 |
|---|---|
| stray 를 전수 출력으로 | RED 1(키 문자열이 출력에 보인다) |
| `prod-env.sh` 의 `unset`·못박기 떼기 | RED 1(`exit 11`·`exit 12`) |
| **음성** — `DOCKER_CONFIG` 까지 떨어뜨림(과잉) | RED 1(`exit 13` — 플러그인 탐색 축을 지킨다) |
| **음성** — 산문 한 문단 · unit `Description` 문구 · 기대 파일에 주석 한 줄 | 각 **GREEN**(술어가 붉지 않는다) |

**기대 파일·콜레이션·§1 ③**(라운드 3)

| 변이 | 결과 |
|---|---|
| **음성** — 기대 파일을 **역순 정렬**(내용은 같다) | **GREEN** — 순서가 저자 몫이 아니다(cr M-5 의 목표) |
| 기대 파일에서 줄 하나 삭제 | **RED** |
| §1 ③ 의 `test -e` 가드 떼기(앞 판으로) | **RED** |
| 틸드 펜스(`~~~`) 안에 BAD 줄 | **RED** — 펜스 0 단언이 받는다(앞 판 GREEN — v H1) |

**변이가 엉뚱한 자리에 떨어진 것 하나를 잡았다.** cr L-10 의 `User=deploy` 변이를 처음에는 파일 첫
일치(§4.5 의 인증서 검사 unit)에 떨어뜨려 **GREEN** 이 나왔다 — 게이트가 옳았고 변이가 틀렸다.
블록 한정(`/경보 템플릿/,/닫는 울타리/`)으로 다시 쳐서 RED 를 확인했다. 「적용 여부를 numstat 으로
본다」만으로는 **어디에** 적용됐는지가 안 잡힌다 — 그 줄이 문서에 다섯 번 나오는 자리에서는 바뀐 줄의
앞뒤까지 봐야 한다.

**양성 대조 하나는 고치는 중에 저절로 나왔다.** D-15R-1 의 술어는 내가 **산문에 쓴 반례**까지 집어
GREEN 을 막았다(등재된 제한 7) — 블록 밖·산문 안의 같은 꼴도 본다는 뜻이다.

## 비밀값 스캔 · 좌표

`command grep -rniE -f config/quality/leak-patterns.txt` 로 돌렸다.

| 확인 | 결과 |
|---|---|
| in_scope 산출물 전체 | base 매치 **2** · head 매치 **2**(늘지 않았다 — 둘 다 base 부터 있던 줄) |
| 이 slice 가 **더한 줄**에만 | 매치 **0**(exit 1) · evidence 셋도 각각 **0** |
| 편집한 파일들을 가리키는 `file:line` 역방향 참조 | **0** 건(이 slice 의 evidence 제외) |
| 이 evidence 가 쓰는 좌표 | 절 번호·결정 ID·인용문뿐(`file:line` **0**) |

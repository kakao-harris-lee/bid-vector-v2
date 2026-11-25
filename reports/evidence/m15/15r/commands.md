# M15 / 15R — commands

base `928ac2fa0` · 브랜치 `fix/m15r-activation-runbook`

환경: WSL2 Ubuntu · bash · `docker compose`(stub 해석에만) · `shfmt v3.14.1`(앞 세션이 받아 둔 바이너리를
`config/quality/shell-guard-policy.properties` 의 핀과 **체크섬 대조**한 뒤 PATH 에 뒀다 — 일치).
**운영 host·systemd·운영 컨테이너·실 Telegram·실 API 에 닿는 명령은 하나도 없다.** `/etc/bidvector-v2/*.env`
는 읽지도 `stat` 하지도 않았다. 실측은 scratchpad 와 버릴 compose 프로젝트 이름에서만 돌았다.

## acceptance

| 명령 | 결과 |
|---|---|
| `./tools/prod-guards-check.sh` | exit 0 — **통과 258 · 어긋남 0** |
| `./tools/prod-alert-check.sh` | exit 0 — 87 건 전부 통과(이 slice 가 `tools/prod-env.sh` 를 만진다) |
| `bash -n` — 래퍼·`prod-env`·`prod-guards-check` | exit 0 |

`./gradlew check` 는 돌리지 않았다(무거운 빌드는 팀장이 직렬로 배정한다). **마지막 HEAD 의 게이트 결과
정본은 verifier 와 PR 조치 코멘트다.**

## RED → GREEN

단언을 먼저 세우고 **고치기 전에** 돌려 실패를 확인했다 — 결정 일곱(D-15R-1~7)과 D-15R-8 ③ 이 각각 자기
줄로 붉었다: **exit 1 · 통과 244 · 어긋남 14**. 고친 뒤 **258 · 0**. 착수 base 의 통제군은 **239 · 0**
(임시 clone)이다. 더한 단언의 갈래는 그 출력이 이름으로 낸다 — 여기 옮겨 적지 않는다.

**D-15R-9(S-6)는 단언을 두지 않는다**(그 결정이 그렇게 정한다 — 산문이고 거동이 없다). 그래서 그 문면을
더한 뒤에도 게이트 계수는 **258 · 0** 그대로이고, 그 커밋에서 acceptance 셋을 다시 돌려 확인했다.

## 설계 검토 (2) 의 실측 둘

**3번 — trap·`set -e` 와 새 `case` 의 겹침.** 없다. 신호 갈래는 trap 이 받아 자기 일회 컨테이너를 치우고
같은 신호로 자살하므로 `case` 에 닿지 않는다 — relay TERM **143** · HUP **129**. `case` 가 낼 수 있는 값은
`0|1|2|5|6|7` 뿐이라 그 두 값이 곧 「닿지 않았다」의 실측이다. `docker compose run` 은 `|| rc=$?` 로 받아
`set -e` 가 그 자리에서 끊지 않는 것도 그대로다(러너 1·2 가 1·2 로 나온다).

**6번 — 이 host·CI 러너가 `DOCKER_HOST` 없이 도는가.** 돈다. 이 host 는 두 변수가 **미설정**이고 현재
docker context 가 `default`(기본 소켓)다. `.github/workflows/ci.yml` 에 두 변수를 세우는 자리가 **없다**.
그래서 D-15R-6 의 unset 은 두 자리 모두에서 거동을 바꾸지 않는다.

## D-15R-8 ③ 의 근거 — 임대는 owner 별이 아니다

`PostgresAdvisoryLockLease` 가 쥐는 키는 `lockKeyFor(kind)` 가 **`OutboxConsumerKind` 하나로** 정하는
상수이고 질의는 `pg_try_advisory_lock(?)` 에 그 키만 넣는다 — owner 는 들어가지 않고 claim 질의에도 없다.
그래서 「같은 owner 의 relay 가 임대를 쥐고 있다」는 거짓이다(다른 `--owner` 로 다시 쳐도 같은 자물쇠에서
선다). 래퍼 문면 · §13.3 drop-in 주석 · `m6-6e` §3.2·§3.8 네 자리를 그 사실로 고쳤다.

## 변이 — 표적 여덟 + 꼴 변이 하나

임시 clone 에서 **한 번에 하나씩 바꿔치우고**, 적용 여부를 `git diff --numstat` 으로 먼저 봤다.
`RelayLines.kt`(out_of_scope)도 그 사본에서만 만졌다. 통제군(변이 없음) **258 · 0 · exit 0**, 매 변이 뒤
되돌려 `git status --porcelain` 이 빈 것을 확인했다.

| # | 변이 (바꿔치우기) | numstat | 결과 |
|---|---|---|---|
| 1 | §13.2 에 `-m 755` 디렉터리 생성 되살리기 | 1/1 | RED 1 — 모드 등식(줄 3 ≠ 기대 2) |
| 2 | `relay.env` 줄의 `test -e` 가드 떼기 | 1/1 | RED 1 — 멱등 꼴 등식 |
| 3 | 래퍼 `case` 를 앞 판(`*) exit "$rc"`)으로 | 1/2 | RED 6 — 거동 넷 + 문면 + 집합 등식 |
| 4 | `RelayExitCode` 에 여섯째 상수 `(5)` | 2/0 | RED 1 — 집합 등식(러너 6 ≠ 래퍼 5) |
| 4b | 열거의 꼴 바꾸기(이름 붙인 인자) | 1/1 | RED 2 — 항목 5 ≠ 해석 4 · 집합 등식 |
| 5a | `LimitCORE=0` → `EnvironmentFile=` 로 | 1/1 | RED 2 — 지시 0 등식 · `LimitCORE` 계수 |
| 5b | `LimitCORE=0` 만 지우기 | 1/1 | RED 1 — `LimitCORE` 계수 |
| 6 | stray 이름 꼴 필터를 전수 출력으로 | 1/1 | RED 1 — 그 키 문자열이 출력에 보인다 |
| 7 | `prod-env.sh` 의 unset 떼기 | 1/1 | RED 1 — source 뒤 그 변수가 남는다(exit 11) |
| 8 | §13.3 `ExecStopPost` 한 줄의 접두 바꾸기 | 1/1 | RED 1 — 접두 집합 ≠ compose 의 `name:` |

4b 는 계약 밖에서 더했다 — 설계 검토 (2) 1번(이름 붙인 인자 꼴)이 **추출을 비워** 집합 등식을 공허하게
참으로 만드는 축이고, 그것을 막는 것이 「항목 수 == 해석된 값 수」다. 그 단언이 실제로 가른다.

**양성 대조 하나는 고치는 중에 저절로 나왔다.** D-15R-1 의 술어는 내가 **산문에 쓴 반례**(지운 줄의 꼴을
그대로 인용한 문장)까지 집어 GREEN 을 막았다 — 블록 밖·산문 안의 같은 꼴도 본다는 뜻이다. 반례를 명령
꼴이 아니게 고쳐 통과시켰다.

## 비밀값 스캔 · 좌표

`command grep -rniE -f config/quality/leak-patterns.txt` 로 돌렸다.

| 확인 | 결과 |
|---|---|
| in_scope 여섯 전체 | base 매치 **2** · head 매치 **2**(늘지 않았다 — 둘 다 base 부터 있던 줄) |
| 이 slice 가 **더한 줄**에만 | 매치 **0**(exit 1) · 이 evidence 둘도 각각 **0** |
| 편집한 여섯 + `RelayLines` 을 가리키는 `file:line` 역방향 참조 | **0** 건(이 slice 의 evidence 제외) |
| 이 evidence 가 쓰는 좌표 | 절 번호·결정 ID·인용문뿐(`file:line` **0**) |

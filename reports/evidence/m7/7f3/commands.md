# M7/7F-3 — 실행 명령과 종료 코드

**acceptance 정의**: `.github/workflows/ci.yml` 의 `container` job 중 이 slice 가 닿는 step(S-27 · S-27b · S-27c)
+ 반입·되돌림 리허설 + `check` job 의 `leakPatternGate`. `web`·`ml-engine` job 입력 변경 0.

**release 빌드·취약점 게이트를 돌리지 않은 사유**: 이 slice 는 이미지도 위생·취약점 **정책도 바꾸지 않는다**
(in_scope 는 `tools/prod-*.sh` 와 runbook 뿐). 리허설의 release 둘은 `:local` 이미지에 **태그만 더하고**
manifest 를 손으로 썼다 — 7F-2 r4 verifier 와 같은 방식이다. 태그 전후 `:local` 네 줄의 ID 는 동일하다.

**호스트 규칙**: 무거운 명령 전에 확인 셋(`pgrep -a java …` · `free -m` · `ps -eo pid,rss,args`)을 **별도 호출로**
먼저 돌렸다. Gradle 은 빌드 자리 진입점(`bv-build.sh`, 획득 순간 메모리 재확인) 아래에서 돌렸고 `--no-daemon`
뒤 잔존 daemon 0 을 확인했다.

**프로브 격리**: 레인 전용 compose 프로젝트 `w7f3-probe` · 루프백 포트 37543 · 상태 디렉터리와 env 파일은
저장소 밖 레인 scratchpad. 6G 개발 DB(`bid-vector-v2-dev`)·host `/etc/nginx`·레지스트리 무접촉.

## 고침별 RED → GREEN

RED 는 base `78b7f384` 의 버릴 clone 에서, GREEN 은 이 브랜치의 worktree 에서, **같은 입력**으로 쟀다.
잠금 홀더는 프로브 셸이 fd 로 직접 쥐고 직접 놓는다(첫 판의 `flock -c '…'` 는 자식이 fd 를 물고 남아
다음 측정을 오염시켰다 — 그 사실을 보고 바꿨다).

| 고침 | 재현 | RED (base) | GREEN (이 브랜치) |
|---|---|---|---|
| **V4-1** 잠금 자리 | 그 판이 쓰는 프로젝트 잠금을 밖에서 쥔 채 `TMPDIR=<다른 디렉터리> prod-deploy.sh … --state-dir <다른 것>` | **exit 0**(같은 입력에서 `TMPDIR` 없으면 2) | **exit 2** — `TMPDIR` 유무 무관 |
| V4-1 통제군 | 같은 명령, 잠금 놓은 뒤 | exit 0 | exit 0 |
| V4-1 음성 대조 | 고친 판에서 **옛 자리**(`/tmp/bidvector-v2-prod.<프로젝트>.lock`)를 쥐고 같은 명령 | — | exit 0 — 그 파일은 더 이상 쓰이지 않는다 |
| **V4-2** 태그 옮김 | `docker tag bidvector/web:local bidvector/app:<current>` 뒤 `prod-notification-sweep.sh` | **exit 1** · 일회 컨테이너가 **실제로 만들어졌다**(`app-run-… Created` 1) | **exit 3** · 컨테이너 0 |
| **V4-2** 당기는 길 | `current` 를 로컬에 없는 태그로 | exit 1(compose 이미지 조회까지 간다) | **exit 3** — manifest 부재에서 멈춘다(compose 를 부르지 않는다) |
| V4-2 통제군 | 바른 상태 | compose 까지 간다 | compose 까지 간다(ID 등식 통과) |
| **V4-3** 호스트 축 | env 의 `BIDVECTOR_APP_JDBC_URL` 호스트를 `elsewhere.example` 로 | **exit 0** | **exit 2** |
| **V4-3** schema 축 | 같은 키에 `?currentSchema=shadow` | **exit 0** | **exit 2** |
| **V4-3** 포트 축 | 같은 키의 포트를 `6432` 로 | **exit 0** | **exit 2** |
| V4-3 자격 누출 | 같은 키의 쿼리 매개변수에 JDBC 자격 두 쌍을 실어 | — | exit 2 · 문면은 `?<매개변수 생략>` — 값이 안 나온다 |
| V4-3 회귀 통제군 | DB 이름만 다르게(앞 판도 막던 축) | exit 2 | exit 2 |
| V4-3 통제군 | 바른 URL | exit 0 | exit 0 |
| **V4-4** 손상된 `current` | `current` 에 `bad tag!` 를 쓰고 러너 | **exit 1**(러너 자신의 「예외」와 같은 값) · 볼륨을 만들고 죽었다 | **exit 3** — 태그 모양에서 멈춘다 |
| **V4-5** 종료 코드 표 | runbook §9.4 의 종료 코드 문장 | `0`·`1`·`2` 만 | `3`(전제 실패)과 그 다섯 사유 |
| **V4-6** 되돌림의 틈 | 되돌림이 `w7f3-a` 가 떠 있다고 보았는데 `current` 는 이미 `w7f3-b` — 되돌림이 exec 하는 명령 그대로 | **exit 0**(낡은 대상으로 반입) | **exit 2** — 그 사이 다른 반입이 끝났다 |
| V4-6 통제군 | 같은 명령에 맞는 `--expect-running` | (인자 없음) | exit 0 |
| **V4-7** 반입 중 훑기 | 프로젝트 잠금을 밖에서 쥔 채 러너 | **exit 1** · 컨테이너 **1** 개 생성 | **exit 3** · 컨테이너 **0** |
| **V4-7** 훑는 중 반입 | 러너가 도는 동안 `prod-deploy.sh` dry-run | **exit 0**(러너가 잠금을 쥐지 않는다 — `exec` 라 놓고 떠난다) | **exit 2** · 러너가 끝난 뒤 통제군 exit 0 |

## 반입·되돌림 리허설 (개발 host · 외부 effect 0)

| 단계 | exit | 핵심 결과 |
|---|---|---|
| ① 첫 반입 dry-run → `--apply` | 0 · 0 | `current=w7f3-a` · 백업 0(볼륨 없음·기록 없음 = 첫 반입) |
| ② 헬스 | 0 | `prod-health: OK` |
| ③ 주기 러너(뜬 스택에서 한 번) | 143 | 이미지 ID 등식·잠금·`--pull never` 를 **지나** compose 가 컨테이너를 띄웠다. 143 은 내가 멈춰 세운 값 — 아래 제한 ① |
| ④ 둘째 반입 `--apply` | 0 | `current=w7f3-b` · `previous=w7f3-a` · **반입 직전 백업 1** |
| ⑤ 되돌림 dry-run → `--apply` | 0 · 0 | `--expect-running` 을 넘긴 채 `current=w7f3-a` 로 복귀 |
| ⑥ 되돌린 뒤 헬스 | 0 | `prod-health: OK` |
| ⑦ 우회 차단 | 3 | 떠 있는 release 가 실은 마이그레이션을 가진 채 옛 release 직접 반입 → 멈춤 |

## 게이트

| 명령 | exit | 핵심 결과 |
|---|---|---|
| `./tools/prod-env-keys-check.sh` (S-27) | 0 | 키 18 등식 |
| `./tools/prod-proxy-sites-check.sh --proxy-image bidvector/proxy:local` (S-27b) | 0 | 하위 라우트 4 등식 · 운영 compose 단언 |
| `./tools/prod-nginx-check.sh --proxy-image bidvector/proxy:local` (S-27c) | 0 | 불변 3표지 · N2~N9 |
| `./gradlew --no-daemon -q leakPatternGate` | 0 | 매치 0 |
| `bash -n` (바뀐 셸 스크립트 다섯) | 0 | 구문 오류 0 |

## 경계 확인

| 확인 | 결과 |
|---|---|
| 외부 effect | DNS 0 · ACME 0 · 운영 host 반입 0 · 포트 개방 0 · **레지스트리 pull 0**(V4-2 의 당기는 갈래는 실행하지 않았다 — `--pull never` 와 ID 등식으로 닫힘을 보였다) |
| `:local` 태그 | 프로브 태그를 더하기 전·거둔 뒤 네 줄의 ID 동일 |
| 6G 개발 DB | `bid-vector-v2-dev` 기동 유지 · 무접촉 |
| host `/etc/nginx` | 무접촉(S-27c 는 저장소의 대역 host 설정으로 돈다) |
| 프로브 잔여 | 컨테이너 0 · 볼륨 0 · 망 0 · 프로브 태그 0 · 잠금 파일 삭제 |
| 고아 프로세스 | 측정마다 `tail -f /dev/null` 계수 0 을 확인(두 번 생겼고 PID 로 거뒀다) |
| evidence 비밀값 스캔 | `grep -rniE -f config/quality/leak-patterns.txt reports/evidence/m7/7f3/` → 매치 0 |
| clean tree | `git status --porcelain -- <in_scope 개별 인자>` 빈 출력 · 양성 대조는 아래 |
| 수정 라운드 새 파일 ↔ in_scope | 새 파일 **0** — `git diff --name-status` 전건 `M`(evidence 제외) |
| 낡는 좌표 | 이 slice 가 편집하는 파일에 `file:line` 0 · 역방향 `grep -rn '<stem>:[0-9]'` 0 |

## 알려진 제한

① **주기 러너의 「훑기 자체」는 이 리허설로 재지 못한다.** `bidvector/app:local`(다른 레인이 2026-10-29 에
구운 재생 태그)의 jar 에 `NotificationSweepRunner` 가 없다 — 러너 코드는 2026-10-31 에 들어왔다. 그래서
`--bidvector.notification-sweep.mode=once` 가 무시되고 앱이 상주 서버로 떠서, 내가 그 컨테이너를 멈춰
exit 143 을 받았다. 이 slice 가 더한 것(잠금 · manifest ID 등식 · 태그 모양 · `--pull never`)은 **전부
compose 를 부르기 전**에 서므로 위 표로 다 쟀고, 그 뒤의 러너 거동은 7C③-b 의 것이며 이 slice 가 바꾸지
않는다. 호출 모양은 정적으로 확인했다(app ENTRYPOINT `[java -jar application.jar]` + 인자 하나).
app 이미지를 굽는 것은 out_scope(release 빌드)다.

② `--pull never` 의 **당기는 쪽** 음성 대조는 실행하지 않았다 — 레지스트리 호출이라 경계 밖이다.
대신 측정한 것: 로컬에 없는 태그 + `--pull never` 로 compose 를 직접 불렀을 때 **pull 을 시도하지 않고**
로컬 빌드 경로로 떨어져 오프라인에서 실패했다(운영 compose 의 app 에는 `build:` 가 있다). 1차 방어는
ID 등식이고 `--pull never` 는 그 뒤의 겹이다.

③ 이 slice 는 CI 워크플로를 바꾸지 않으므로 `container` job 의 나머지 step(이미지를 **굽는** 둘 포함)은
돌리지 않았다. 입력이 바뀐 step 은 위 셋뿐이다.

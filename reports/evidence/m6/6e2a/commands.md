# M6/6E-2a — 실행 명령과 실측 (구현 레인)

base `2deb5f9d` · 브랜치 `m6-6e2a/2026-10-08` · 이 레인의 마지막 산출물 커밋 `4ade8b51`.

## acceptance — CI 워크플로 job 명령 그대로

| 명령 | 문면 | 결과 |
|---|---|---|
| `./gradlew --no-daemon check` | CI `check` job 문면 그대로 | **exit 0** (9m 10s) |
| `./gradlew --no-daemon qualityBaseline` | 같은 job | **exit 0** (13s, 전건 UP-TO-DATE) |
| `container` job 로컬 재현(S-21a~S-25) | 그 job 의 `run` 블록을 **문면 그대로** 순서대로 | **전 step exit 0** |

`check` 의 핵심 결과 한 줄 — 클래스 **352** · test **2828** · 실패 0 · 오류 0 · skip 4. 이 slice 가 더한
것은 class 둘(`ProductionPoolRoleTest` 7 · `PooledLeaseFloorTest` 2)과 기존 class 의 test 둘이다.
종료 코드로 읽었다 — 출력을 `grep` 해 판단하거나 커밋과 한 줄로 묶지 않았다. S-20 은 Python 무변경이라
계약이 생략했고, S-21(ml-serving 이미지 빌드)은 계약 acceptance 가 `S-21a~S-25` 이므로 기존 이미지를 썼다.

### 먼저 붉었던 자리 — 검증이 실제로 작동했다는 기록

| 명령 | 결과 | 무엇이 잡혔나 |
|---|---|---|
| `:app:test --tests "…ProductionPoolRoleTest"`(구현 전) | exit 1 | **RED 실측** — 7 test 전건 실패(`PGSimpleDataSource cannot be cast to HikariDataSource`) |
| `check` r1 | exit 1 | `:app:ktlintTestSourceSetCheck` import 순서 1건 |
| `check` r2 | exit 1 | **79 failed · 원인 셋** — ⓐ `com.zaxxer.hikari` 가 app 의 바깥 참조 허용 집합 밖 ⓑ fixture 초기화 **다섯 자리**가 production 빈으로 `TRUNCATE`(42501) ⓒ 풀 구현이 classpath 에 오자 `DataSourceAutoConfiguration` 이 **DB 없는 test 조립**에서 풀을 만들려 들어 컨텍스트가 깨짐 |
| `check` r3 | exit 1 | ktlint 8건(KDoc 둘이 겹침) |
| `check` r4 | exit 1 | 1 failed — `ALTER TABLE outbox ADD CONSTRAINT`(실패 주입 DDL)은 표 소유자만 가능 |

**ⓑ·ⓒ 는 계약이 예고한 P-5 보다 넓었다** — 계약은 `ProductionAssemblyAuthAuditTest` 하나를 지목했고
실제로는 fixture 자리가 여섯(`TRUNCATE` 다섯 + 실패 주입 DDL 하나)이었다. 전부 `app/src/test/**`
(in_scope)이고 공용 `adminDataSource(container)` 한 자리로 모았다.

### `container` job 재현 — step 별 종료 코드

러너 env 와 `$GITHUB_ENV` 두 자리만 흉내내고 `run` 블록을 그 순서로 돌렸다.

| step | 결과 |
|---|---|
| 자격 값 생성 둘(DB·운영자) | 모사 |
| S-21a 앱 배포물 빌드 · S-21b 앱 이미지 빌드 | exit 0 |
| S-22a ml-serving 위생 · **S-22b 앱 위생** · **S-22c 거부 스모크** | exit 0 |
| S-23 로컬 환경 기동(app+ml-serving+postgres healthy 수렴) | exit 0 |
| S-23b 컨테이너 스모크 · S-23c 백업·복원 리허설 · S-24 실 gateway↔실 서버 | exit 0 |
| S-25 정리(볼륨까지) | exit 0 |

**P-6 의 답 — 1초 창은 깨지지 않았다.** S-22b 의 요약 줄이 `실프로세스-uid전체=[10001]` 을 냈다(컨테이너가
t=1s 에 살아 있었고 uid 표집이 실제로 돌았다). 기동 중 DB 접촉의 **첫 자리가 여전히 migration 의
비풀링 연결**이고 TEST-NET-1 주소에서 드라이버 연결 타임아웃까지 막히기 때문이다 — 풀은 그 뒤라
`initializationFailTimeout` 이 창을 당기지 못한다. **`tools/image-hygiene-check.sh` 는 편집하지 않았다.**
같은 요약 줄이 의존 layer 항목 수 **98**(하한 50)을 냈다 — 풀 jar 가 배포물에 실렸다는 두 번째 자리다.

## 문서 등식 — runbook 의 표·문면이 코드와 어긋나지 않는가

저장소 뿌리에서 이 블록을 그대로 떼어 돌린다. 세는 법: `bash <블록> | grep -c '^OK'` == **6**.

```sh
set -uo pipefail
RB=docs/runbook/m6-6e-operations.md
W=app/src/main/kotlin/bidvector/app/wiring/PersistenceWiring.kt
fails=0
eq() { if [ "$2" = "$3" ]; then echo "OK   $1"; else echo "DIFF $1: 기대 [$2] 실측 [$3]"; fails=$((fails + 1)); fi; }

sec26=$(awk '/^### 2\.6 /{s=1} /^## 3\. /{s=0} s' $RB)
sec36=$(awk '/^### 3\.6 /{s=1} /^### 3\.7 /{s=0} s' $RB)
sec6=$(awk '/^## 6\. /{s=1} /^## 7\. /{s=0} s' $RB)

setrole=$(grep -rl 'SET ROLE' --include=*.kt -- */src/main/kotlin | wc -l | tr -d ' ')
role=$(grep -oP 'val APPLICATION_ROLE = "\K[^"]+' $W)
nologin=$(grep -c "^CREATE ROLE ${role} NOLOGIN;$" adapters/src/main/resources/db/migration/V2__provenance_guard.sql | tr -d ' ')
eq "E-1 역할 전환 자리 1 · 그 역할이 NOLOGIN 으로 만들어진다" "1 1" "$setrole $nologin"

hikari=$(grep -rl 'HikariDataSource(' --include=*.kt -- */src/main/kotlin | wc -l | tr -d ' ')
simple=$(grep -rl 'PGSimpleDataSource()' --include=*.kt -- */src/main/kotlin | wc -l | tr -d ' ')
both=$(grep -rl 'HikariDataSource(' --include=*.kt -- */src/main/kotlin | xargs grep -l 'PGSimpleDataSource()' | wc -l | tr -d ' ')
eq "E-2 풀 1 · 비풀링 1 · 같은 파일" "1 1 1" "$hikari $simple $both"

maxsize=$(grep -oP 'val MAX_POOL_SIZE = \K[0-9_]+' $W | tr -d '_')
timeoutms=$(grep -oP 'val CONNECTION_TIMEOUT_MS = \K[0-9_]+' $W | tr -d '_L')
pooltag=$(grep -oP 'val POOL_APPLICATION_NAME = "\K[^"]+' $W)
migtag=$(grep -oP 'val MIGRATION_APPLICATION_NAME = "\K[^"]+' $W)
docmax=$(printf '%s\n' "$sec26" | grep -cF "| 최대 크기 | ${maxsize} |")
docto=$(printf '%s\n' "$sec26" | grep -cF "| 연결 대여 제한 시간 | $((timeoutms / 1000))초 |")
docrole=$(printf '%s\n' "$sec26" | grep -cF "SET ROLE ${role}")
doctag=$(printf '%s\n' "$sec26" | grep -cF "앱 \`${pooltag}\` · migration \`${migtag}\`")
eq "E-3 §2.6 표 ↔ 코드 상수 넷" "1 1 2 1" "$docmax $docto $docrole $doctag"

doc_deny=$(printf '%s\n' "$sec36" | grep -cE '거부한다')
doc_limit=$(printf '%s\n' "$sec36" | grep -cE 'RESET ROLE')
eq "E-4 §3.6 거부 사실 · 경계 문면" "2 1" "$doc_deny $doc_limit"

old_pool=$(printf '%s\n' "$sec6" | grep -c 'OPEN-6A1-CONNECTION-POOL')
old_role=$(printf '%s\n' "$sec6" | grep -c 'OPEN-6E1-APP-ROLE-NOT-ASSUMED')
new_open=$(grep -c 'OPEN-6E2A-OWNER-CREDENTIAL-IN-APP' $RB)
eq "E-5 §6 받은 OPEN 둘 부재 · 신설 OPEN 등재" "0 0 2" "$old_pool $old_role $new_open"

sameuser=$(python3 -c "
import re, yaml
d = yaml.safe_load(open('docker/compose.yaml', encoding='utf-8'))['services']
pick = lambda x: re.match(r'\\\$\\{([A-Z_]+)', str(x)).group(1)
print('same' if pick(d['app']['environment']['BIDVECTOR_PERSISTENCE_USERNAME']) == pick(d['postgres']['environment']['POSTGRES_USER']) else 'diff')")
newenv=$(git diff --name-only 2deb5f9d..HEAD -- docker .github | wc -l | tr -d ' ')
eq "E-6 접속 사용자 == DB 소유자 · 배포 모양 무변경" "same 0" "$sameuser $newenv"

printf '\n등식 %s — DIFF %d\n' "$([ $fails -eq 0 ] && echo 전부 일치 || echo 불일치)" "$fails"
exit $fails
```

**결과(HEAD `4ade8b51` 기준)**: `E-1`~`E-6` **전부 OK · DIFF 0 · exit 0 · OK 줄 6**.

`E-1`·`E-4` 가 6E-1 `E-25` 의 **갱신분**이다. 그 등식은 「`SET ROLE` 0 · `NOLOGIN` · 접속 사용자 == DB
소유자 · §3.6 이 **보호가 작동하지 않는다**고 말함」 넷을 쟀고, 이 slice 가 바꾼 것은 둘이다 — 역할 전환
자리가 **0 → 1**, §3.6 의 문면이 **거부 사실 + 경계**로. 바뀌지 않은 둘은 `E-1`·`E-6` 이 그대로 든다:
**접속 사용자가 여전히 DB 소유자라는 사실이 신설 OPEN 의 근거**이므로 그 칸이 조용히 바뀌면 §3.6 의
경계 문단이 거짓이 된다. `E-3` 은 §2.6 의 **새 표**가 코드 상수와 같은지 본다 — 문서에 값을 적는 것은
그 값을 두 자리에 두는 일이고, 등식이 없으면 다음 편집에서 갈린다.
6E-1 의 `commands.md` 는 그 SHA 의 기록이므로 **편집하지 않았다**.

## 변이 — 바꿔치우고 RED 를 확인한 뒤 복원

전부 **커밋 뒤**에 돌렸다(미커밋 산출물을 `git checkout --` 로 함께 지우지 않기 위해). 각 변이 뒤
`git diff --numstat` 으로 바뀐 줄 수를 보고, 복원한 뒤 `git status --porcelain` 이 빈 출력임을 확인했다.

| # | 변이 | 기대 | 실측 |
|---|---|---|---|
| **M1**(①) | 연결 초기화 SQL 한 줄 **제거** | P-1 RED | **RED** exit 1 · numstat `0 1` · `has_table_privilege(…,'outbox','DELETE')` 가 `false`→`true` 로 뒤집히고 `current_user` 단언도 붉다 |
| **M2**(②) | 최대 크기 `10` → `1` | P-3 RED | **RED** exit 1 · numstat `1 1` · `1 should be >= 2` |
| **M3**(③) | 풀을 먼저 만들고 **그 풀로** migrate | 기동 실패 | **RED** exit 1 · numstat `3 2` · 컨텍스트 기동 실패(`initializationError`) — 빈 DB 에 역할이 아직 없다 |
| **M4**(④) | 소유자 `DataSource` 를 `@Bean` 으로 노출 | P-2 RED | **RED** exit 1 · numstat `8 0` · `size 1 but has size 2 ["dataSource","ownerDataSource"]` |
| **M5a**(⑤ 문면 그대로) | `expectedModules` 에서 HikariCP 등재 **제거** | 계약은 RED 예상 | **exit 0 — 초록이다.** 그 task 는 「등재 ⊆ 해석」이라 등재를 빼면 **아무것도 재지 않는다**. 계약의 변이 문면이 성립하지 않는 자리이고, **그 초록 자체가 「등재하지 않으면 아무도 안 잰다」의 실측**이다 |
| **M5b** | 등재 좌표를 존재하지 않는 형제로 **바꿔치움** | RED | **RED** exit 1 · numstat `1 1` · `해석되지 않은 모듈: com.zaxxer:HikariCP-does-not-exist` |
| **M6a** | domain(`settlement`)이 버전 없는 카탈로그 별칭을 선언 | 금지 group RED | **exit 0** — 버전이 BOM 에서 오는 별칭이라 BOM 없는 모듈에서 **해석되지 않고** 게이트의 모집단에 들지 않는다(그 모듈은 컴파일도 안 된다) |
| **M6b** | 같은 자리에 버전을 박아 선언 | 금지 group RED | **RED** exit 1 · numstat `4 0` · `금지 group 'com.zaxxer:HikariCP' — domain 모듈은 프레임워크를 볼 수 없다` |

**음성 대조**: 복원한 뒤 `:app:test`(표적) · `:app:compatibilitySmoke` · `:settlement:moduleDependencyGate`
가 전부 다시 exit 0 이고 `git status --porcelain` 이 빈 출력이다 — 변이가 실제로 게이트를 움직였고 복원이
완전했다.

## 호스트 (무거운 빌드 전 점검)

빌드·docker 작업마다 `pgrep -af 'GradleWrapperMain|GradleWorkerMain'` · `free -m` ·
`ps -eo pid,rss,args --sort=-rss | head` 를 **별도 호출로 먼저** 돌리고 시작했다(available 11.5~16.3 GB ·
swap free 4.9~5.4 GB). 병행 레인(6E-2b)의 빌드가 돌던 두 번은 **끝날 때까지 기다렸다**(폴링).

마지막 빌드 뒤 `pgrep -af GradleDaemon` 에 **1건이 남아 있다 — 이 레인의 것으로 확인되지 않아 멈추지
않았다.** 근거: 이 레인의 모든 호출이 `--no-daemon` 이고(단발 daemon 은 빌드 뒤 종료한다) 그 daemon 의
로그에는 빌드 요청이 23:28:09 **하나뿐**이며 프로젝트 경로가 남아 있지 않다. 시각만으로 주인을 짐작하지
않는다(`./gradlew --stop` 은 다른 레인의 빌드를 함께 멈추므로 쓰지 않았다).

## clean-tree 게이트

`git status --porcelain --` 에 in_scope 경로를 **개별 인자**로 넘겨 빈 출력을 확인하고, 양성 대조로
이 파일 끝에 한 줄을 붙여 그 줄이 잡히는 것을 본 뒤 **비파괴로 절삭**해 되돌렸다(`git checkout --` 는
그 파일의 미커밋 편집 전부를 지우므로 쓰지 않는다).

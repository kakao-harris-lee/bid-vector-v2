# M6/6E-1 — 실행 명령과 실측 (구현 레인)

base `80dc33b3` · 브랜치 `m6-6e1/2026-10-07` · 이 레인의 마지막 산출물 커밋 `bf5850b2`
(공유 파일 `ci.yml` 은 `2df1da3b`).

## acceptance — CI 워크플로 job 명령 그대로

| 명령 | 문면 | 결과 |
|---|---|---|
| `./gradlew --no-daemon check` | CI `check` job 문면 그대로 | **exit 0** (9m 42s) |
| `./gradlew --no-daemon qualityBaseline` | 같은 job | **exit 0** (13s) |
| `container` job 로컬 재현(S-21a~S-25) | G-4 가 `ci.yml` 을 바꾸므로 그 job 의 `run` 블록을 순서대로 | **전 step exit 0** |

`check` 의 핵심 결과 한 줄 — 클래스 **350** · test **2817** · 실패 0 · 오류 0 · skip 4
(`adapters` 137/900 · `app` 62/562 · `workflow` 51/425 · `build-logic` 27/279 · `procurement` 33/263 ·
`decision` 24/184 · `shared-kernel` 8/89 · `strategy` 6/84 · `qualification` 2/31). e2e 는 기본 `check`
안에서 돌았다(`adapters.e2e` 결과 파일 **7**) — 조건 애노테이션도 `Test.filter` 제외도 쓰지 않았고 이
slice 가 더한 제외는 **0** 이다. `leakPatternGate`·`qualityBaseline` 은 `FROM-CACHE`·`UP-TO-DATE` 표시
없이 **실행**됐다(evidence 전부를 담은 HEAD `99b4b4cd` 에서).

종료 코드로 읽었다 — 출력을 `grep` 해 판단하거나 커밋과 한 줄로 묶지 않았다.

### `container` job 재현 — step 별 종료 코드

러너 env 와 `$GITHUB_ENV` 만 흉내내고 `run` 블록을 그 순서로 돌렸다(6B-2 와 같은 방식).
**S-21**(ml-serving 이미지 빌드)은 계약 acceptance 가 `S-21a~S-25` 이므로 기존 이미지를 쓰고 생략했다.

| step | 결과 |
|---|---|
| 자격 값 생성 둘(DB·운영자) | exit 0 |
| S-21a 앱 배포물 빌드 · S-21b 앱 이미지 빌드 | exit 0 |
| S-22a·S-22b 이미지 위생 게이트 · S-22c 거부 스모크 | exit 0 |
| S-23 로컬 환경 기동(app+ml-serving+postgres healthy 수렴) | exit 0 |
| **S-23b 컨테이너 스모크 — G-4 포함** | exit 0 |
| S-23c 백업·복원·되돌림 리허설 | exit 0 |
| S-24 실 Kotlin gateway ↔ 컨테이너의 실 Python 서버 | exit 0 |
| S-25 정리(볼륨까지) | exit 0 · 남은 `bidvector` 컨테이너·볼륨 **0** |

**G-4 블록이 실제로 돈 로그 줄**(S-23b 출력에서 그대로):

```
-- 여력 상한 세우기: begin → value → confirm (dry-run 200 의 전제) --
-- 평가 dry-run 왕복: 200 · 본문 키 아홉 · outbox 행 수 전후 등식 (G-4) --
dry-run 왕복 통과 — outbox 행 수 2 == 2 (쓰기 0)
```

S-23c 는 **G-4 가 전략을 한 번 더 쓴 뒤에도** 통과했다 — 리허설의 전략 등식이 값을 하드코딩하지 않고
읽어 비교하기 때문이다(그 step 의 출력이 「(ii) 양성 — 같은 백업으로 다시 복원하자 `candidate_limit`/
`revision` 이 돌아왔다」를 낸다).

**스모크를 다시 돌려도 통과한다** — 같은 환경에서 S-23b 를 재실행했을 때도 exit 0 이고 그때의 등식은
`outbox 행 수 8 == 8` 이었다(앞 실행이 남긴 행 때문에 수가 다르고, 재는 것은 절대 수가 아니라 **전후
동일성**이다).

**S-20(Python) 생략 사유**: Python 무변경 — Python 절반은 CI `ml-engine` job 이 정본이다(6D-1·6B-2·6D-2 와
같은 처분).

### 호스트 — 3단 점검과 1회 예외 (계약 r2 `D-6E1-4`)

빌드·컨테이너 job 전마다 **별도 호출 셋**으로 돌렸다(`pgrep` · `free -m` · `ps … --sort=-rss`).

착수 시점의 실측이 `swap free 1.58 GB` 로 계약 `D-6E1-3` 의 2 GB 문턱 미달이었고, **swap 을 쥔 것이
빌드가 아니라 상주 서비스**임을 per-process `VmSwap` 으로 확인했다(상위 여덟이 전부 상주 프로세스이고
다른 프로젝트의 유휴 Gradle·Kotlin daemon 은 그 목록에 없다 — RAM 만 쥔다). 유휴 프로세스는 자기 swap
페이지를 되불러오지 않아 **기다려도 문턱에 닿지 않으므로** waiter 를 취소하고 보고했다.

**사용자 결정으로 이 slice 한정 1회 예외**(`D-6E1-4`): available ≥ 6 GB **그리고** swap free ≥ 1.5 GB
면 Gradle·container job 을 **하나씩** 허용, swap free 1 GB 아래면 즉시 중단·보고.

| 빌드 전 점검 | available | swap free | 활성 Gradle | 판정 |
|---|---|---|---|---|
| ① `check` | 14.2 GB | 1,599 MB | 0 | 진행 |
| ② `qualityBaseline` | 13.4 GB | 1,678 MB | 0(남은 것은 내 `check` 의 TestKit daemon) | 진행 |
| ③ container job 앞 구간 | 13.3 GB | 1,682 MB | 0 | 진행 |
| ③ container job 뒤 구간 | 13.3 GB | 1,684 MB | 0 | 진행 |
| rollback ④~⑥ | 13.2 GB | 1,685 MB | 0 | 진행 |

**swap free 는 전 구간 1,599~1,686 MB 로 1 GB 중단 문턱에 닿지 않았다**(최저 1,599 MB). 무거운 작업은
**항상 하나씩** 돌렸고 foreground 로만 띄웠다.

**남은 Gradle daemon**: `check` 가 띄운 하나뿐이고(build-logic TestKit 의 계약 게이트 결정성 test 가
띄운다 — `PWD` 가 `/tmp/bidvector-contract-gate-determinism…`) 그 **PID 만** 멈췄다.
`./gradlew --stop` 은 쓰지 않았다.

**differential / golden**: **N/A** — 문서 + CI step slice 라 산출 데이터가 없다(fixture 변경 0, 골든 변경 0).

## 문서 등식 — 표마다 코드에서 기계 수집한 집합과 대조

문서 slice 의 결함 클래스는 「문서가 코드와 어긋나도 초록」이다(설계 검토 (1)). 그래서 runbook·추적표의
**모든 표**를 코드에서 뽑은 집합과 대조한다. 아래 한 블록이 전부이고 **종료 코드가 판정**이다
(DIFF 수를 exit 로 낸다 — 출력을 `grep` 해 읽지 않는다).

```sh
#!/usr/bin/env bash
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"
RB=docs/runbook/m6-6e-operations.md
TR=reports/evidence/m6/6e1/acceptance-trace.md
fails=0
eq() { if [ "$2" = "$3" ]; then printf 'OK   %s\n' "$1"
       else printf 'DIFF %s\n  기대: %s\n  실측: %s\n' "$1" "$2" "$3"; fails=$((fails+1)); fi; }

# E-1 C-1 행 수 == capability-map 의 `V2 필수` 수(분류 줄 파싱)
cap=$(awk '/^### [A-Z]+-[0-9]+/{c=1;next} /^- \*\*분류\*\*:/{if(c){if($0~/`V2 필수`/)n++;c=0}} END{print n+0}' docs/discovery/capability-map.md)
rows=$(grep -cE '^\| \*\*[A-Z]+-[0-9]+\*\* \|' "$TR")
eq "E-1 C-1 행 수 == capability-map V2 필수" "$cap" "$rows"

# E-2·E-3 C-1 의 모든 식별자·OPEN ID ⊆ 등재 집합
python3 - <<'PY'
import re,sys
txt=open('config/quality/gate-tests.properties',encoding='utf-8').read()
j,buf=[],''
for l in (x for x in txt.splitlines() if not x.lstrip().startswith('#')):
    if l.rstrip().endswith('\\'): buf+=l.rstrip()[:-1]
    else: buf+=l; j.append(buf); buf=''
if buf: j.append(buf)
inc,exc=set(),set()
for e in j:
    if '=' not in e: continue
    k,v=e.split('=',1); k=k.strip()
    if not k.startswith('gate.tests.') or k.startswith('gate.tests.discovery') or k=='gate.tests.minimum': continue
    toks={t.strip() for t in v.split(',') if re.fullmatch(r'[A-Za-z_][A-Za-z0-9_.]*\.[A-Z][A-Za-z0-9_]*',t.strip())}
    (exc if k.endswith('.excluded') else inc).update(toks)
reg=inc-exc
steps={m.group(1) for m in (re.match(r'\s*- name:\s*(.+?)\s*$',l) for l in open('.github/workflows/ci.yml',encoding='utf-8')) if m}
opens=set(re.findall(r'OPEN-[A-Z0-9-]+',open('docs/discovery/capability-map.md',encoding='utf-8').read()))
opens|=set(re.findall(r'OPEN-[A-Z0-9-]+',open('milestone-6.md',encoding='utf-8').read()))
body='\n'.join(l for l in open('reports/evidence/m6/6e1/acceptance-trace.md',encoding='utf-8')
               if re.match(r'^\| \*\*[A-Z]+-[0-9]+\*\* \|',l))
bad_f=sorted(set(re.findall(r'bidvector\.[A-Za-z0-9_.]*[A-Z][A-Za-z0-9_]*',body))-reg)
bad_s=sorted(set(re.findall(r'`([^`]*\(S-[0-9a-z]+\))`',body))-steps)
bad_o=sorted(set(re.findall(r'OPEN-[A-Z0-9-]+',body))-opens)
print('OK   E-2 C-1 식별자 ⊆ 등재 집합' if not (bad_f or bad_s) else 'DIFF E-2 미등재 FQN %s · step %s'%(bad_f,bad_s))
print('OK   E-3 C-1 OPEN ID ⊆ (capability-map ∪ milestone-6)' if not bad_o else 'DIFF E-3 미등재 OPEN %s'%bad_o)
sys.exit(1 if (bad_f or bad_s or bad_o) else 0)
PY
[ $? -eq 0 ] || fails=$((fails+1))

# E-4 §1.2 prefix 표 == @ConfigurationProperties prefix 전수
code_pfx=$(grep -rhoE 'ConfigurationProperties\(prefix = "[^"]+"' --include=*.kt */src/main/kotlin | sed 's/.*"\(.*\)"/\1/' | sort -u | tr '\n' ' ')
doc_pfx=$(awk '/^### 1\.2 /{s=1} /^### 1\.3 /{s=0} s' $RB | grep -oE '^\| `[a-z.-]+`' | tr -d '|` ' | sort -u | tr '\n' ' ')
eq "E-4 §1.2 prefix 표 == 코드 prefix 전수" "$code_pfx" "$doc_pfx"

# E-5 §1.3 mode 키 표 == @ConditionalOnProperty(name=["mode"]) 전수
code_mode=$(grep -rhoE 'ConditionalOnProperty\(prefix = "[^"]+", name = \["mode"\]' --include=*.kt app/src/main/kotlin | sed 's/.*prefix = "\([^"]*\)".*/\1.mode/' | sort -u | tr '\n' ' ')
doc_mode=$(awk '/^### 1\.3 /{s=1} /^### 1\.4 /{s=0} s' $RB | grep -oE '`[a-z.-]+\.mode=once`' | tr -d '`' | sed 's/=once//' | sort -u | tr '\n' ' ')
eq "E-5 §1.3 mode 키 표 == ConditionalOnProperty 전수" "$code_mode" "$doc_mode"

# E-6·E-7 §2 관리 표면 — 잠금 키 · 배치 자유 키 · 거부 접두사
MS=app/src/main/kotlin/bidvector/app/ManagementSurface.kt
code_lock=$(sed -n '/^val MANAGEMENT_SURFACE_LOCK/,/^    )/p' $MS | grep -oE '"[a-z.]+" to' | sed 's/" to//;s/"//' | sort | tr '\n' ' ')
doc_lock=$(awk '/^### 2\.1 /{s=1} /^### 2\.2 /{s=0} s' $RB | grep -oE '^\| `[a-z.]+`' | tr -d '|` ' | sort | tr '\n' ' ')
eq "E-6 §2.1 잠금 키 표 == MANAGEMENT_SURFACE_LOCK" "$code_lock" "$doc_lock"
code_dep=$(sed -n '/^val MANAGEMENT_SURFACE_DEPLOYMENT_KEYS/,/)/p' $MS | grep -oE '"[a-z.]+"' | tr -d '"' | sort | tr '\n' ' ')
doc_dep=$(awk '/^### 2\.2 /{s=1} /^### 2\.3 /{s=0} s' $RB | grep -oE '`management\.server\.[a-z]+`' | tr -d '`' | sort -u | tr '\n' ' ')
eq "E-7a §2.2 배치 자유 키 == MANAGEMENT_SURFACE_DEPLOYMENT_KEYS" "$code_dep" "$doc_dep"
code_gov=$(sed -n '/^val MANAGEMENT_SURFACE_GOVERNED_PREFIXES/,/)/p' $MS | grep -oE '"[a-z][a-z._-]*"' | tr -d '"' | sort | tr '\n' ' ')
doc_gov=$(awk '/MANAGEMENT_SURFACE_GOVERNED_PREFIXES` =/{p=1} p&&/^$/{exit} p' $RB | grep -oE '`[a-z][a-z._-]*`' | tr -d '`' | sort -u | tr '\n' ' ')
eq "E-7b §2.3 거부 접두사 == MANAGEMENT_SURFACE_GOVERNED_PREFIXES" "$code_gov" "$doc_gov"

# E-8·E-9·E-10 §3 종료 코드 표 == enum 값 집합(표의 둘째 칸만 읽는다)
enum_vals() { sed -n "/^enum class $1(/,/^}/p" "$2" | grep -oE '^    [A-Z_]+\(' | tr -d ' (' | sort | tr '\n' ' '; }
doc_codes() { awk -F'|' "/^### $1 /{s=1} /^### $2 /{s=0} s&&/^\\| [0-9] \\|/{gsub(/[\` ]/,\"\",\$3); print \$3}" $RB | sort -u | tr '\n' ' '; }
eq "E-8 §3.2 relay 종료 코드 == RelayExitCode" \
   "$(enum_vals RelayExitCode app/src/main/kotlin/bidvector/app/relay/RelayLines.kt)" "$(doc_codes '3\.2' '3\.3')"
eq "E-9 §3.3 수집 종료 코드 == CollectionExitCode" \
   "$(enum_vals CollectionExitCode app/src/main/kotlin/bidvector/app/collection/CollectionLines.kt)" "$(doc_codes '3\.3' '3\.4')"
eq "E-10 §3.4 커밋 종료 코드 == EvaluationCommitExitCode" \
   "$(enum_vals EvaluationCommitExitCode app/src/main/kotlin/bidvector/app/evaluation/EvaluationCommitLines.kt)" "$(doc_codes '3\.4' '3\.5')"

# E-11·E-12 §5.1 사상표 == NOTIFICATION_DELIVERY_POLICY 출하 값 · 환경 전수
ND=workflow/src/main/kotlin/bidvector/workflow/notification/NotificationDeliveryPolicyData.kt
code_map=$(sed -n '/^val NOTIFICATION_DELIVERY_POLICY/,/^    )/p' $ND \
  | grep -oE 'RuntimeEnvironment\.[A-Za-z]+ to DeliveryMode\.[A-Za-z]+' | sed 's/RuntimeEnvironment\.//;s/ to DeliveryMode\./=/' | sort | tr '\n' ' ')
doc_map=$(awk '/^### 5\.1 /{s=1} /^### 5\.2 /{s=0} s' $RB | grep -oE '`[A-Za-z]+` \| `[A-Za-z]+`' | tr -d '` ' | tr '|' '=' | sort | tr '\n' ' ')
eq "E-11 §5.1 환경→모드 사상표 == NOTIFICATION_DELIVERY_POLICY" "$code_map" "$doc_map"
code_env=$(grep -oE '^    [A-Z][A-Za-z]+,' workflow/src/main/kotlin/bidvector/workflow/notification/RuntimeEnvironment.kt | tr -d ' ,' | sort | tr '\n' ' ')
eq "E-12 §5.1 환경 행 == RuntimeEnvironment 전 값" "$code_env" \
   "$(printf '%s' "$doc_map" | tr ' ' '\n' | cut -d= -f1 | grep -v '^$' | sort | tr '\n' ' ')"

# E-13 G-4 기대 키 집합 == openapi EvaluationDryRunResponse required
spec_keys=$(awk '/^    EvaluationDryRunResponse:/{s=1;next} s&&/^      properties:/{exit} s&&/^        - /{print $2}' openapi/bidvector-operator-api.yaml | sort | paste -sd, -)
ci_keys=$(grep -oE '^          dry_run_keys="[^"]+"' .github/workflows/ci.yml | sed 's/.*="\(.*\)"/\1/')
eq "E-13 G-4 기대 키 == openapi EvaluationDryRunResponse required" "$spec_keys" "$ci_keys"

# E-14 §3.6 「DELETE 권한이 없다」 == outbox GRANT 실측
eq "E-14 §3.6 outbox 권한 == SELECT·INSERT·UPDATE(DELETE 없음)" "GRANT SELECT, INSERT, UPDATE ON outbox TO bidvector_app " \
   "$(grep -hoE 'GRANT [A-Z, ]+ ON outbox TO [a-z_]+' adapters/src/main/resources/db/migration/*.sql | sort -u | tr '\n' ' ')"

# E-15 §4.2 「운영자 경로가 없다」 == 출하 정책의 선택자 고정
eq "E-15 §4.2 출하 releaseSelector == LatestPromoted(유일)" "LatestPromoted " \
   "$(grep -oE 'releaseSelector = ModelReleaseSelector\.[A-Za-z]+' workflow/src/main/kotlin/bidvector/workflow/evaluation/OpportunityPolicyData.kt | sed 's/.*\.//' | sort -u | tr '\n' ' ')"

printf '\n등식 %s — DIFF %d\n' "$([ $fails -eq 0 ] && echo 전부 일치 || echo 불일치)" "$fails"
exit $fails
```

**결과(2026-10-07, HEAD `bf5850b2`)**: `E-1`~`E-15` **전부 OK · DIFF 0 · exit 0**.
모집단 실측 — 등재 test **349** · `ci.yml` step **25** · OPEN **298** / C-1 인용 — FQN **120** · step **2**
· OPEN **20**.

## 변이 — 바꿔치우고 RED 를 확인한 뒤 복원

전부 **커밋 뒤**에 돌렸다(미커밋 산출물을 `git checkout --` 로 함께 지우지 않기 위해). 각 변이 전에
`git diff --numstat` 으로 바뀐 줄 수를 먼저 보고, 뒤에 `git checkout --` 로 복원하고
`git status --porcelain` 이 빈 출력임을 확인했다.

| # | 변이(더하기가 아니라 **바꿔치우기**) | 기대 | 실측 |
|---|---|---|---|
| **M1** | C-1 의 ⓐ 식별자 하나를 존재하지 않는 이름으로 교체(`WatchRulesTest` → `WatchRulesNotARealTest`) | E-2 RED | **RED** — `DIFF E-2 미등재 FQN ['bidvector.strategy.WatchRulesNotARealTest']`, exit 1. numstat `1 1` |
| **M2** | runbook §3.2 의 종료 코드 **한 행 삭제**(`LEASE_BUSY` 3) | E-8 RED | **RED** — 기대 5 값, 실측 4 값, exit 1. numstat `0 1` |
| **M3** | `ci.yml` 의 G-4 기대 키 한 칸 변경(`bidNowNoticeIds` → `bidNowNoticeIdsX`) | E-13 RED | **RED** — 기대·실측 문자열이 그 한 칸에서 갈림, exit 1. numstat `1 1` |
| **M4** | G-4 의 기대 응답 코드 **교체**(`200` → `201`, 실패 문면도 같이) | container 스모크 RED | **RED** — `스모크 실패: dry-run 이 201 이 아니다: 200`, step exit 1. numstat `1 1`. 실패 문면이 실제 200 응답 본문을 함께 내고 그 본문의 칸이 **아홉**이며 `maxActiveBids` 가 G-4 가 세운 `5` 다 |

**음성 대조**: M1~M3 을 복원한 뒤 같은 등식 명령이 다시 `DIFF 0 · exit 0` 이고, M4 를 복원한 뒤
같은 환경에서 S-23b 를 다시 돌리면 `exit 0` + `dry-run 왕복 통과` 다 — 변이가 게이트를 실제로 움직였고
복원이 완전했다는 양방향 확인이다.

**M4 가 「더하기」가 아니라 「바꿔치우기」인 근거**: 기대값을 지우거나 단언을 늘린 것이 아니라 그 한
단언의 기대 코드를 **다른 값으로 교체**했다. 교체 뒤 스모크가 붉어졌으므로 그 단언이 실제로 응답 코드를
읽고 있고, 통과가 「단언이 없어서」가 아니다.

## clean-tree 게이트

```sh
git status --porcelain -- docs/runbook/m6-6e-operations.md .github/workflows/ci.yml \
  reports/evidence/m6/6e1/acceptance-trace.md reports/evidence/m6/6e1/checklist.md \
  reports/evidence/m6/6e1/commands.md
```

빈 출력. **양성 대조 1회**(비파괴 절삭) — runbook 끝에 빈 줄 하나를 더하면 같은 명령이 그 파일 한 줄을
내고, 그 한 줄을 지워 되돌리면 다시 빈 출력이다(`git checkout --` 를 쓰지 않았다).

## 비밀값·좌표 자기 점검

대상 파일 넷 — `docs/runbook/m6-6e-operations.md` · `reports/evidence/m6/6e1/{acceptance-trace,checklist,commands}.md`.

| 점검 | 명령 | 결과 |
|---|---|---|
| 누출 어휘(참조형) | `grep -rniE -f config/quality/leak-patterns.txt <대상 넷>` | 매치 **0** · exit 1 |
| 호스트 사용자 홈 경로 원문 | `grep -n "$HOME" <대상 넷>` | 매치 **0** · exit 1 |
| 낡는 좌표 | `grep -nE '\.kt:[0-9]\|\.yml:[0-9]\|\.md:[0-9]' <대상 넷>` | 매치 **0** · exit 1 — 좌표는 파일 경로 + 절 제목·상수 이름·결정 ID 로만 가리켰다 |
| 역방향 파급 | `grep -rn '6e-operations:[0-9]\|acceptance-trace:[0-9]' . --include=*.md --include=*.yml` | 매치 **0** · exit 1 |

**`ci.yml` 은 이 표의 대상이 아니다** — `leakPatternGate` 의 스캔 뿌리는 `reports/evidence/` 이고
`ci.yml` 은 그 밖이다. 같은 명령을 `ci.yml` 에 대보면 **이 slice 가 더하지 않은 기존 한 줄**(자격 값을
`$GITHUB_ENV` 로 넘기는 생성 step 의 변수 이름)이 걸린다 — 그 줄은 base 에 이미 있었고 G-4 블록이
만든 것이 아니다(`git diff 80dc33b3..HEAD -- .github/workflows/ci.yml` 가 +55/-0 이고 그 줄은 그 55 에
들지 않는다).

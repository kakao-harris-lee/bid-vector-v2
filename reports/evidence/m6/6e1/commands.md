# M6/6E-1 — 실행 명령과 실측 (구현 레인)

base `80dc33b3` · 브랜치 `m6-6e1/2026-10-07` · **수정 라운드 1**(D-6E1-9) 뒤.
이 레인의 산출물 커밋(최신 라운드 기준) — runbook `9b179d57` · C-1 `ba6c25f1` · `ci.yml` `0aa96b56`(공유) ·
C-6·C-7 `b0df65c6`.

## acceptance — CI 워크플로 job 명령 그대로

| 명령 | 문면 | r0 결과 | **r1 결과** |
|---|---|---|---|
| `./gradlew --no-daemon check` | CI `check` job 문면 그대로 | exit 0 (9m 42s) | **exit 0** (22s — 아래 주의) |
| `./gradlew --no-daemon check --no-build-cache` | 깨끗한 clone(`build/` 없음) — CI 러너와 같은 자리 | — | **exit 0** (11m 20s · **359 task 전부 executed**) |
| `./gradlew --no-daemon qualityBaseline` | 같은 job | exit 0 (13s) | **exit 0** (7s, UP-TO-DATE) |
| `container` job 로컬 재현(S-21a~S-25) | 그 job 의 `run` 블록을 순서대로 | 전 step exit 0 | **전 step exit 0**(PR #65 조치 뒤 재실행 — `ci.yml` 이 F3·F4·F6 으로 바뀌었다) |

**worktree `check` 가 22s 인 이유와 그 공백을 메운 것**: r1 의 diff 가 문서와 `ci.yml` 뿐이라 Kotlin
입력이 안 바뀌었고, 그래서 `:test` task 가 전부 `UP-TO-DATE` 로 앞 실행을 재사용했다(실행된 32 task 는
게이트·보고 쪽이다). **`leakPatternGate` 는 `UP-TO-DATE` 표시 없이 실행됐다** — evidence 가 바뀌었으므로
그 입력이 움직인 것이고, 이 slice 가 `check` 에서 실제로 건드리는 축이 그 하나다.
그 재사용을 **갈음으로 쓰지 않았다** — 깨끗한 clone(`dff87ad4`, `build/` 없음)에서
`check --no-build-cache` 를 겹쳐 돌려 **359 task 전부 executed** 를 실측했다(CI 러너는 캐시가 없다).
그 run 의 핵심 결과 한 줄 — 클래스 **350** · test **2817** · 실패 0 · 오류 0 · skip 4 ·
`adapters.e2e` 결과 파일 **7** · `leakPatternGate`·`qualityBaseline` 둘 다 실행.
**verifier r1 이 같은 형태로 실측한 수치와 같다.**

**실측 자리**: 수정 라운드 1 의 HEAD(`dff87ad4`)와 그 직전 산출물 상태다. 사용자 결정으로 이 slice 의
남은 빌드에 한해 swap 문턱이 **1.0 GB** 로 내려갔고(계약 `D-6E1-12`), 실측 시점의 호스트는 그 문턱을
넉넉히 넘었다(아래 「호스트」 — swap free 4.2~5.0 GB). 앞 라운드에 적었던 「미실측(호스트)」는 이 절이
대신한다.

**r1 이 바꾼 것과 acceptance 의 관계**: 이 라운드의 diff 는 **문서 넷과 `ci.yml` 의 주석·셸 단언**이고
Kotlin·Python 소스·build 파일·`config/quality` diff **0** 이다. `ci.yml` 축의 정본은 container job 이며
**그것도 이 라운드에서 돌렸다**(아래 step 표) — r0 의 초록으로 갈음하지 않았다.

**r0 `check` 의 핵심 결과 한 줄**(참고 — 판정 SHA 가 다르다) — 클래스 **350** · test **2817** · 실패 0 · 오류 0 · skip 4
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

**G-4 블록(r1 판)이 실제로 돈 로그 줄**(S-23b 출력에서 그대로):

```
-- 여력 상한 세우기: begin → value → confirm (dry-run 200 의 전제) --
-- 평가 dry-run 왕복: 200 · 본문 키 아홉 · outbox 행 수 전후 등식 (G-4) --
dry-run 왕복 통과 — outbox 행 수 2 == 2 (쓰기 0)
```

r1 이 더한 재조회 200 단언 둘도 같은 run 에서 통과했다(그 둘이 실패하면 상한 세우기 단계가 끊긴다).

S-23c 는 **G-4 가 전략을 한 번 더 쓴 뒤에도** 통과했다 — 리허설의 전략 등식이 값을 하드코딩하지 않고
읽어 비교하기 때문이다(그 step 의 출력이 「(ii) 양성 — 같은 백업으로 다시 복원하자 `candidate_limit`/
`revision` 이 돌아왔다」를 낸다).

**스모크를 다시 돌려도 통과한다** — 같은 환경에서 S-23b 를 재실행했을 때도 exit 0 이고 그때의 등식은
`outbox 행 수 8 == 8` 이었다(앞 실행이 남긴 행 때문에 수가 다르고, 재는 것은 절대 수가 아니라 **전후
동일성**이다).

**S-20(Python) 생략 사유**: Python 무변경 — Python 절반은 CI `ml-engine` job 이 정본이다(6D-1·6B-2·6D-2 와
같은 처분).

### 호스트 — 3단 점검과 1회 예외 (계약 `D-6E1-4`)

빌드·컨테이너 job 전마다 **별도 호출 셋**으로 돌렸다(`pgrep` · `free -m` · `ps … --sort=-rss`).

| 라운드 | 시점 | available | swap free | 활성 Gradle | 판정 |
|---|---|---|---|---|---|
| r0 | `check` 전 | 14.2 GB | 1,599 MB | 0 | 진행 |
| r0 | `qualityBaseline` 전 | 13.4 GB | 1,678 MB | 0 | 진행 |
| r0 | container 앞·뒤 구간 전 | 13.3 GB | 1,682 / 1,684 MB | 0 | 진행 |
| r0 | rollback ④~⑥ 전 | 13.2 GB | 1,685 MB | 0 | 진행 |
| r1 | 수정 직후(문턱 1.5 GB 시점) | 14.2 GB | 1,421 MB | 0 | 보류 — 미달 |
| **r1** | `check` 전(문턱 **1.0 GB**, `D-6E1-12`) | **13.9 GB** | **4,751 MB** | **0** | 진행 |
| **r1** | 깨끗한 clone `check` 전 | **12.8 GB** | **4,838 MB** | **0** | 진행 |
| **r1** | container 앞·뒤 구간 전 | **13.4 / 13.3 GB** | **5,005 / 5,007 MB** | **0** | 진행 |
| **r1** | rollback ④~⑥ 전 | **13.3 GB** | **5,008 MB** | **0** | 진행 |

**r1 수정 직후에는 문턱 미달이었다.** swap 을 쥔 것은 빌드가 아니라 상주 서비스였고(per-process
`VmSwap` 상위 여덟이 전부 상주 프로세스, MB 단위 558 · 497 · 411 · 211 · 204 · 175 · 169 · 151) 유휴
프로세스는 자기 swap 페이지를 되불러오지 않아 기다려 닿는 값이 아니었다 — 레인이 문턱을 스스로 낮추지
않고 보고했다. **사용자 결정으로 문턱이 1.0 GB 로 내려간 뒤**(`D-6E1-12`) 다시 점검했을 때 swap 이
**4.7 GB 로 회복**해 있었고(상주 서비스 쪽에서 반납된 것) 그 뒤 전 구간 **4.2~5.0 GB** 로 중단선
1.0 GB 에 닿지 않았다. 무거운 작업은 **항상 하나씩** foreground 로만 돌렸다.

**남은 Gradle daemon**: 전건 `check` 는 매번 하나를 남긴다(build-logic TestKit 의 계약 게이트 결정성
test — `PWD` 가 `/tmp/bidvector-contract-gate-determinism…`). 라운드 끝에 `pgrep -af GradleDaemon` 으로
확인하고 **그 PID 만** 멈췄다. `./gradlew --stop` 은 쓰지 않았다(같은 Gradle 버전의 다른 레인 daemon 을
함께 멈춘다).

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
eq "E-15 §4.1·§4.4 출하 releaseSelector == LatestPromoted(유일)" "LatestPromoted " \
   "$(grep -oE 'releaseSelector = ModelReleaseSelector\.[A-Za-z]+' workflow/src/main/kotlin/bidvector/workflow/evaluation/OpportunityPolicyData.kt | sed 's/.*\.//' | sort -u | tr '\n' ' ')"

# E-16 §4.2 ② 「승격·demote 연산 0」 — 레지스트리 전수
reg_promo=$(grep -rniE 'promot|demot' ml-engine/src/ml_engine/registry/ | wc -l | tr -d ' ')
eq "E-16 §4.2 레지스트리 승격·demote 어휘 == 0" "0" "$reg_promo"

# E-17 §4.2 release_id 규약 == build_derived_release 의 접두사 상수
pfx=$(grep -oE '_DERIVED_RELEASE_ID_PREFIX = "[^"]+"' ml-engine/src/ml_engine/serving/runtime.py | sed 's/.*"\(.*\)"/\1/')
doc_pfx=$(awk '/^### 4\.2 /{s=1} /^### 4\.3 /{s=0} s' $RB | grep -oE '`"distribution/" \+ [^`]+`' | sed 's/`"\(distribution\/\)".*/\1/')
eq "E-17 §4.2 release_id 접두사 문면 == serving 상수" "$pfx" "$doc_pfx"

# E-18 §4.3 비현재 EXACT 거부 == _validate_selector 의 거부 코드 쌍
rej=$(sed -n '/^def _validate_selector/,/^def _validate_objective/p' ml-engine/src/ml_engine/serving/prediction.py \
  | grep -oE 'FAILURE_CODE_[A-Z_]+|RELEASE_MISMATCH' | sort -u | tr '\n' ' ')
doc_rej=$(awk '/^### 4\.3 /{s=1} /^### 4\.4 /{s=0} s' $RB | grep -oE '`(UNSUPPORTED_RELEASE|RELEASE_MISMATCH)`' | tr -d '`' | sort -u | tr '\n' ' ')
eq "E-18 §4.3 거부 코드 문면 == 실 serving 판정(해당 둘)" \
   "RELEASE_MISMATCH UNSUPPORTED_RELEASE " "$doc_rej"
eq "E-18b §4.3 그 둘이 실 serving 거부 집합에 있다" \
   "FAILURE_CODE_INVALID_REQUEST FAILURE_CODE_UNSUPPORTED_RELEASE RELEASE_MISMATCH " "$rej"

# E-19 §4.4 「정책은 이미지에 구워진다」 — Dockerfile COPY 한 줄 · compose 의 policy 마운트 0
copy=$(grep -cE '^COPY ml-engine/policy /app/policy$' docker/ml-serving.Dockerfile | tr -d ' ')
mnt=$(python3 -c "
import yaml
v=yaml.safe_load(open('docker/compose.yaml',encoding='utf-8'))['services']['ml-serving'].get('volumes') or []
print(len(v))")
eq "E-19 §4.4 정책 COPY 1 · ml-serving 의 volume 마운트 0" "1 0" "$copy $mnt"

# E-20 §4.5 「배포 스크립트가 없다」 == tools/ 전수
tools=$(ls tools/ | sort | tr '\n' ' ')
n_tools=$(ls tools/ | wc -l | tr -d ' ')
doc_n=$(awk '/^### 4\.5 /{s=1} /^## 5\./{s=0} s' $RB | grep -oE '\*\*없다\*\*\(여섯 개|여섯 개 전부' | head -1 | grep -oE '여섯' | wc -l | tr -d ' ')
eq "E-20a §4.5 tools/ 전수 == 실측 여섯" \
   "contract-crosslang-smoke.sh db-backup.sh db-rehearsal.sh db-restore.sh image-hygiene-check.sh one-command-check.sh " "$tools"
eq "E-20b §4.5 문면의 「여섯 개」 == ls tools/ 수" "6 1" "$n_tools $doc_n"

# E-21 C-6 분류 집계·서식 수 == capability-map 실측 (R1-L-2 — C-6 가 「결과는 commands.md 에」라고 가리키는 자리)
C6=docs/discovery/legacy-v2-differences.md
cap_tally=$(awk '/^### [A-Z]+-[0-9]+/{c=1;next} /^- \*\*분류\*\*:/{if(c){if($0~/`후속`/)f++; if($0~/`폐기`/)d++; if($0~/`근거 부족`/)i++; c=0}} END{printf "%d %d %d", f+0, d+0, i+0}' docs/discovery/capability-map.md)
doc_tally=$(awk -F'|' '/^\| `후속`/{gsub(/ /,"",$3); f=$3} /^\| `폐기`/{gsub(/ /,"",$3); d=$3} /^\| `근거 부족`/{gsub(/ /,"",$3); i=$3} END{printf "%d %d %d", f, d, i}' $C6)
eq "E-21a C-6 §0 분류 집계 == capability-map 분류 줄 실측" "$cap_tally" "$doc_tally"
bold=$(grep -cE '^- \*\*분류\*\*: \*\*`' docs/discovery/capability-map.md | tr -d ' ')
eq "E-21b C-6 「굵은 서식 4」 == 실측" "4" "$bold"

# E-22 C-7 행 수·ID 집합·판정 분포 (문서 자신의 명령을 그대로)
led=$(grep -cE '^### [RP]-[A-Z]+-[0-9]+ ' docs/discovery/regression-ledger.md | tr -d ' ')
c7=$(grep -cE '^\| [RP]-[A-Z]+-[0-9]+ \|' reports/evidence/m6/6e1/ledger-constraint-trace.md | tr -d ' ')
eq "E-22a C-7 행 수 == ledger 항목 heading 수" "$led" "$c7"
idset=$(diff <(grep -oE '^### [RP]-[A-Z]+-[0-9]+' docs/discovery/regression-ledger.md | cut -c5- | sort) \
             <(grep -oE '^\| [RP]-[A-Z]+-[0-9]+' reports/evidence/m6/6e1/ledger-constraint-trace.md | cut -c3- | sort) | wc -l | tr -d ' ')
eq "E-22b C-7 ID 집합 == ledger ID 집합(diff 줄 0)" "0" "$idset"
c7d=$(awk -F'|' '/^\| [RP]-[A-Z]+-[0-9]+ \|/{gsub(/ /,"",$6); print $6}' reports/evidence/m6/6e1/ledger-constraint-trace.md | sort | uniq -c | awk '{printf "%s=%s ", $2, $1}')
c7h=$(awk -F'|' '/^\| 61 \| 43 \| 18 \|/{gsub(/ /,"",$0); print}' reports/evidence/m6/6e1/ledger-constraint-trace.md | head -1)
eq "E-22c C-7 판정 분포 == 머리 집계(연결 43 · 미연결 18)" "미연결=18 연결=43 " "$c7d"
[ -n "$c7h" ] || { printf 'DIFF E-22d C-7 머리 집계 행(61|43|18)을 찾지 못했다\n'; fails=$((fails+1)); }
[ -n "$c7h" ] && printf 'OK   E-22d C-7 머리 집계 행 실재(61|43|18)\n'

# E-23·E-24 C-1 의 r2 술어 — ⓐ 행은 production 칸이 비지 않는다 · 「강함」 표식 집합 == 머리 열거
python3 - <<'EQ23'
# -*- coding: utf-8 -*-
import re, sys
tr = open('reports/evidence/m6/6e1/acceptance-trace.md', encoding='utf-8').read()
rows = [l for l in tr.splitlines() if re.match(r'^\| \*\*[A-Z]+-\d+\*\* \|', l)]
bad_a, marked = [], set()
for l in rows:
    c = [x.strip() for x in l.split('|')]
    cid, mark, prod = c[1].strip('*'), c[3], c[5]
    # RT2-L-3 — 자리채움도 거부한다(`—`·`-`·`–`·`x`·`?`·`n/a` 류 · 20자 미만)
    filler = prod.strip().strip('*`').lower()
    if mark == 'ⓐ' and (filler in ('', '—', '-', '–', '─', 'x', '?', 'na', 'n/a', 'tbd', '없음')
                        or len(filler) < 20):
        bad_a.append(cid)
    if '부분 측정(강함)' in l:
        marked.add(cid)
print('OK   E-23 ⓐ 행 전부 production 배선 칸이 채워져 있다' if not bad_a
      else 'DIFF E-23 production 칸이 빈 ⓐ 행 %s' % bad_a)
m = re.search(r'강한 행 (.+?) 은 「부분 측정\(강함\)」으로 표시했다', tr, re.S)
listed = set(re.findall(r'`([A-Z]+-\d+)`', m.group(1))) if m else set()
print('OK   E-24 「강함」 표식 집합 == 머리 열거 (%d)' % len(marked) if marked == listed
      else 'DIFF E-24 표식만 %s · 열거만 %s' % (sorted(marked - listed), sorted(listed - marked)))
sys.exit(1 if (bad_a or marked != listed) else 0)
EQ23
[ $? -eq 0 ] || fails=$((fails+1))

# E-25 §3.6 「최소 권한 역할로 접속하지 않는다」 — 코드 사실 셋 **과 그 문면**을 함께
# (RT4-L-1: 코드만 재면 문면이 반대로 뒤집혀도 초록이다. §3.6 이 「보호가 작동하지 않는다 · 지울 수
#  있다」를 말하는지도 본다 — 둘 중 하나를 반전하면 RED.)
setrole=$(grep -rl 'SET ROLE' --include=*.kt */src/main/kotlin 2>/dev/null | wc -l | tr -d ' ')
nologin=$(grep -c '^CREATE ROLE bidvector_app NOLOGIN;$' adapters/src/main/resources/db/migration/V2__provenance_guard.sql | tr -d ' ')
sameuser=$(python3 -c "
import re, yaml
d = yaml.safe_load(open('docker/compose.yaml', encoding='utf-8'))['services']
pick = lambda x: re.match(r'\\\$\\{([A-Z_]+)', str(x)).group(1)
app = pick(d['app']['environment']['BIDVECTOR_PERSISTENCE_USERNAME'])
pg = pick(d['postgres']['environment']['POSTGRES_USER'])
print('same' if app == pg else 'diff')")
doc36=$(awk '/^### 3\.6 /{s=1} /^### 3\.7 /{s=0} s' $RB \
  | grep -cE '그 보호는 작동하지 않는다|지울 수 있다' | tr -d ' ')
eq "E-25 §3.6 코드 사실 셋 + 그 문면(보호 미작동·삭제 가능)" "0 1 same 2" "$setrole $nologin $sameuser $doc36"

printf '\n등식 %s — DIFF %d\n' "$([ $fails -eq 0 ] && echo 전부 일치 || echo 불일치)" "$fails"
exit $fails
```

**결과(표적 4 조치, HEAD `2d70d979` 기준)**: `E-1`~`E-25` **전부 OK · DIFF 0 · exit 0**. **OK 줄 32** 개다 —
**이 문서의 블록을 그대로 떼어 돌려 실측한 수**다(앞 판은 `E-25` 가 문면에만 있고 **블록에 들어가지
않아** 떼어 돌리면 31 이었다 — verifier RT4-M-1. 원인은 앞 라운드의 패치 스크립트가 블록을 교체한 뒤
다른 치환에서 예외로 끊겨 **쓰기 전에 전부 버려진 것**이고, 이번에는 블록을 **먼저 쓰고** 그 뒤 문면을
고쳤다). 앞 판이
「단언 24 개」로 적은 것은 블록의 `eq` 호출 수를 센 것이고 실제 출력은 그보다 많았다 — verifier RT-L-2.
이제 세는 법을 고정한다: `bash <블록> | grep -c '^OK'` == **32**).

`E-25` 는 **코드 사실만 재지 않는다**(RT4-L-1) — `SET ROLE` 0 · `NOLOGIN` 역할 · 접속 사용자 == DB
소유자 **셋에 더해 §3.6 의 문면**이 「그 보호는 작동하지 않는다」·「지울 수 있다」를 말하는지 센다. 코드만
재면 문면이 반대로 뒤집혀도 초록이기 때문이다(그 반전을 **M13** 이 실측한다).

`E-23`·`E-24` 는 **r2 가 더한 것**이다 — ⓐ 술어 ②(production 배선·출하 값)의 기계로 잴 수 있는 부분을
닫는다. `E-23` 은 ⓐ 행의 **production 칸이 비지 않음**을, `E-24` 는 「부분 측정(강함)」 표식 집합이 머리
열거와 같은지를 잰다. 술어 ②·③ 의 **내용**(그 식별자가 그 bullet 을 그 배선으로 재는가)은 기계로 닫히지
않는다 — `checklist.md` 의 「틀릴 수 있는 자리」 1 이 그 사실을 적는다.
모집단 실측 — 등재 test **349** · `ci.yml` step **25** · OPEN **298** / C-1 인용 — FQN **120** · step **2**
· OPEN **20**.

## 변이 — 바꿔치우고 RED 를 확인한 뒤 복원

전부 **커밋 뒤**에 돌렸다(미커밋 산출물을 `git checkout --` 로 함께 지우지 않기 위해). 각 변이 전에
`git diff --numstat` 으로 바뀐 줄 수를 먼저 보고, 뒤에 `git checkout --` 로 복원하고
`git status --porcelain` 이 빈 출력임을 확인했다. 전부 **더하기가 아니라 바꿔치우기**다.

| # | 변이 | 기대 | 실측 |
|---|---|---|---|
| **M1** | C-1 의 ⓐ 식별자 하나를 존재하지 않는 이름으로 교체 | E-2 RED | **RED** exit 1 · numstat `1 1` |
| **M2** | runbook §3.2 의 종료 코드 **한 행 삭제**(`LEASE_BUSY` 3) | E-8 RED | **RED** exit 1 · numstat `0 1` |
| **M3** | `ci.yml` 의 G-4 기대 키 한 칸 변경 | E-13 RED | **RED** exit 1 · numstat `1 1` |
| **M4** | G-4 의 기대 응답 코드 교체(`200`→`201`, 실패 문면도 같이) | container 스모크 RED | **RED** — step **exit 1** · numstat `2 2` · 문면 `스모크 실패: dry-run 이 201 이 아니다: 200 키 [아홉 칸] code none`. **그 문면이 G-3 조치를 함께 실측한다** — 본문 전문이 아니라 키 집합과 `code` 만 나온다. 복원 뒤 같은 step 이 다시 exit 0(등식 `6 == 6`) |
| **M5** | runbook §4.3 의 거부 코드 문면 교체(`UNSUPPORTED_RELEASE`→`UNSUPPORTED_REQUEST`) | E-18 RED | **RED** exit 1 · numstat `1 1` |
| **M6** | runbook §4.5 의 「여섯 개 전부」를 「일곱 개 전부」로 | E-20b RED | **RED** exit 1 · numstat `1 1` |
| **M7** | C-7 의 `R-ASYNC-03` 판정을 「연결」→「미연결」로 | E-22c RED | **RED** exit 1 · numstat `1 1` |
| **M8** | ⓐ 행 하나의 **production 배선 칸을 비운다**(`—`) | E-23 RED | **RED** exit 1 · numstat `1 1`. r3 에서 `DEC-04` 로 **자리를 바꿔** 재측정 — 앞 라운드는 `NOTI-03` 이었다 |
| **M9** | 같은 칸을 **자리채움 `-` 로** 채운다 | E-23 RED | **RED** exit 1 — verifier RT2-L-3 이 「`-` 로 채운 변이가 **초록**」임을 찾았고 `E-23` 에 자리채움 거부(`—`·`-`·`–`·`x`·`?`·`n/a`·20자 미만)를 더해 닫았다 |
| **M10**(PR #65) | `_edit_field` helper 의 `APPLIED` 기대값을 `PENDING` 으로 **교체** | container 스모크 RED | **RED** step exit 1 · 문면 `CANDIDATE_LIMIT confirm 뒤 state 가 APPLIED 가 아니다` — **첫 왕복에서 먼저 끊긴다** |
| **M11**(PR #65) | 같은 교체 + **첫 왕복 호출 제거** | 같은 단언이 **둘째 왕복에서도** RED | **RED** step exit 1 · 문면 `MAX_ACTIVE_BIDS confirm 뒤 state 가 APPLIED 가 아니다` — F3 의 목적(「두 왕복 다 `APPLIED` 를 단언한다」)이 **둘째 왕복에서 실측됐다**. helper 가 한 코드 경로이므로 단언 하나가 둘을 덮는다 |

**음성 대조**: 복원한 뒤 같은 등식 명령이 다시 `DIFF 0 · exit 0` 이다 — 변이가 게이트를 실제로 움직였고
복원이 완전했다. **M1·M8 은 r3 의 C-1 위에서 재측정**했고(둘 다 **앞 라운드와 다른 행**으로 자리를
옮겼다 — `M1` 은 `QUAL-03`, `M8` 은 `DEC-04`) 둘 다 RED 다.

**M8 을 r2 에 더한 이유**: RT-H-1 의 결함은 「production 배선에서 성립하는가」를 묻지 않은 것이었고, 그
술어를 문서에 적기만 하면 다음 라운드에 다시 비어도 초록이다. `E-23` 이 그 칸의 **존재**를 닫고 M8 이
그 게이트가 실제로 움직임을 보인다. 칸의 **내용**이 사실인지는 기계가 아니라 verifier 가 본다.

**M5·M6·M7 을 r1 에 더한 이유**: verifier r1 R1-H-1 이 지적한 결함 클래스가 「**문면이 코드와 어긋나도
등식이 잡지 못한다**」였다(앞 판 E-15 는 선택자 상수만 재서 §4 의 산문을 못 봤다). 그래서 새 §4 의 등식을
**양면 대조**로 만들고(문서에서 뽑은 값 ↔ 코드에서 뽑은 값) 그 양면이 실제로 문면 변이에 반응함을
M5·M6 으로, C-7 의 집계 축을 M7 로 실측했다.

**E-16·E-19 는 양면이 아니라 코드 쪽 불변식이다** — 문면이 「승격·demote 연산이 **없다**」·「compose 에
정책 마운트가 **없다**」라고 부재를 주장하므로, 그 부재가 **생기는 날** 게이트가 붉어지는 방향이 맞다.
부재를 문서에서 뽑아 비교하는 형태는 동어반복이 된다.

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
만든 것이 아니다 — 그 수는 **라운드마다 바뀌므로 박아 적지 않는다**(F5, PR #65: 앞 판은 G-4 신설
시점의 `+55/-0` 을 적어 둔 뒤 r1·PR #65 수정으로 낡았다). 판정 시점에
`git diff --numstat 80dc33b3..HEAD -- .github/workflows/ci.yml` 로 산출한다. 그 줄이 이 slice 의
추가분에 들지 않는다는 사실은 `git log -S` 가 보인다 — 그 줄을 들여온 커밋이 base 이전이다.

# M15 / 15S — commands

base `8e7a91ead` · 브랜치 `chore/m15s-ci-action-pins`

환경: WSL2 Ubuntu · bash · `gh`(상류 공개 조회에만) · `buf 1.72.0` · `shfmt v3.14.1`(앞 레인이 받아 둔
바이너리를 `config/quality/shell-guard-policy.properties` 의 핀과 **체크섬 대조**한 뒤 scratchpad 에서
실행 — 일치). **러너에서 액션을 돌리지 않았다** — 핀이 실제로 받아지는가의 실측 자리는 PR 의 CI 다섯 job
뿐이다. 조회 요청에 이 저장소의 이름·값을 싣지 않았다. 운영 host·systemd·운영 컨테이너에 닿는 명령은
하나도 없다.

## 액션 일곱 — 태그 → 커밋 → 판 (D-15S-1)

`gh api repos/<o>/<r>/git/ref/tags/<major>` · tag 객체면 `git/tags/<sha>` 로 벗김 ·
`git/matching-refs/tags/<major>` 로 같은 커밋을 가리키는 태그 대조. 2026-11-26.

| 액션 | ref 객체 | 커밋 | 같은 커밋의 태그 | 적은 판 |
|---|---|---|---|---|
| `actions/checkout` | commit | `fbc6f3992d…6c09` | v5 · **v5.1.0** | v5.1.0 |
| `actions/setup-java` | commit | `b6effb05e4…f961` | v5 · **v5.7.0** | v5.7.0 |
| `actions/setup-node` | commit | `a0853c2454…1444` | v5 · **v5.0.0** | v5.0.0 |
| `actions/setup-python` | commit | `ece7cb06ca…61a1` | v6 · **v6.3.0** | v6.3.0 |
| `actions/upload-artifact` | commit | `b7c566a772…789f` | v6 · **v6.0.0** | v6.0.0 |
| `astral-sh/setup-uv` | **tag**(벗김) | `37802adc94…3b78` | v7 · v7.6 · **v7.6.0** | v7.6.0 |
| `gradle/actions/setup-gradle` | **tag**(벗김) | `0723195856…7d7c` | v5 · **v5.0.2** | v5.0.2 |

일곱 전부 `vX.Y.Z` 태그가 있어 「major 만 적고 제한에 올린다」 갈래는 쓰이지 않았다. 전체 40자리 값은
`config/quality/workflow-action-pins.txt` 가 든다. 판은 올리지 않았다 — 각 줄의 커밋은 **오늘 그 major 가
가리키던 것** 그대로다.

## acceptance

| 명령 | 결과 |
|---|---|
| `./tools/workflow-action-pins-check.sh .github/workflows` | exit 0 — uses **24**(핀 24 · 저장소 안 0) · 기대 파일 7 줄과 집합 등식 · 판 주석 일치 · 자기 변이 **18** 건 전부 붉음 |
| `./tools/workflow-step-refs-check.sh .github/workflows/ci.yml` | exit 0 — job 5 · id 15 · 참조 18 · 매달린 참조 0 (base 와 같은 수) |
| `uv run --no-sync python ../tools/workflow-action-pins-parity.py ../.github/workflows`(cwd `ml-engine`) | exit 0 — uses 24 가 awk 게이트와 **PyYAML 6.0.3**(lock 핀)에서 같은 다중집합 · 음성 대조(심은 중복 키) 통과 |
| `./gradlew --no-daemon check --continue` | **exit 0** · FAILED 0 · `:leakPatternGate` 와 `:workflow:sourceLanguageGate` 는 **실행**(UP-TO-DATE 아님). `--continue` 로 돌린 이유는 바로 아래 — 알려진 빨강을 안은 전건의 「그 밖은 통과」는 미측정이다 |
| `bash -n tools/workflow-action-pins-check.sh` | exit 0 |
| `shfmt -d tools/workflow-action-pins-check.sh` | exit 1 — **이 저장소의 게이트가 아니다**. 선례 `tools/workflow-step-refs-check.sh` 도 같은 exit 1 이다(기본 들여쓰기가 탭). `shfmt` 는 `prod-alert-check.sh` 의 **파서**로만 쓰인다 |
| `shellcheck` | **이 host 에 없다**(설치하지 않았다) |

`yaml.safe_load` 로 파싱한 뒤 구조로 센 수: job 5 · `uses` 24 · 그중 40자리 핀 **24** · checkout step 5 ·
그중 `persist-credentials: false` **5** · 최상위 `permissions` = `{contents: read}` · `check` job 의
`fetch-depth: 0` 보존.

**첫 `check` 는 이 slice 와 무관한 이유로 붉었다**(34m17s · FAILED **1**) — 공유 작업 트리의 Kotlin
**소스 트리 안**에 2026-09-26 자 OMC 상태 파일 하나가 남아 있어 `:workflow:sourceLanguageGate` 가 그것을
offender 로 셌다. gitignore 대상이라 `git status` 에는 보이지 않고, CI 는 새 checkout 이라 해당 없다 —
**로컬 전용 사고**다. scratchpad 에 사본을 두고 치운 뒤 `--continue` 로 다시 돌렸다(43s · 357 up-to-date ·
exit 0). `src/*/kotlin` 아래 비-`kt`/`kts`/`md` 파일이 그 하나뿐임을 전수로 확인했다(치운 뒤 **0**).
둘째 실행이 대부분 up-to-date 인 것은 첫 실행이 test·컴파일을 **실제로 돌린** 뒤이기 때문이고, 그
사이에 바뀐 것은 그 한 파일의 부재와 evidence 뿐이다.

## RED → GREEN

게이트와 기대 파일을 먼저 커밋했다. 그 커밋에서 `.github/workflows` 는 아직 태그였고 게이트는
**exit 1 · FORM 24 건 + SET 7 건**이었다(액션 일곱이 전부 「기대 파일에만 있다」). 다음 커밋이 24 건을
핀해 exit 0 이 됐다.

## 게이트 술어 — 전부 거동으로 쟀다

버릴 디렉터리에 최소 워크플로와 최소 기대 파일을 두고 양쪽을 재는 꼴이다(자기 변이는 끈 채).

| 넣은 것 | 결과 |
|---|---|
| 흐름 매핑 `- {uses: …}` | **exit 2** 읽지 못하는 표기 |
| `uses:` 를 블록 스칼라로 쪼갬 | **exit 2** |
| YAML 앵커·별칭 | **exit 2** |
| `uses: ${{ … }}` | **exit 2** 정적으로 읽을 수 없다 |
| 따옴표 값(`"…"`·`'…'`) | **exit 0** — 벗겨서 센다 |
| 블록 스칼라 본문 안의 `- uses: …@v1` 글자 | **exit 0** — 본문은 세지 않는다 |
| `./…`(저장소 안) 한 건 + 핀 한 건 | **exit 0** — uses 2(핀 1 · 저장소 안 1) |
| 워크플로 디렉터리의 형제 `actions/` 생성 | **exit 1** LOCALDIR · **지운 뒤 exit 0**(양성 대조) |
| `uses` 0 건 | **exit 1** EMPTY — 「훑지 못했다」와 구별되지 않는다 |
| `docker://…@sha256:<64hex>` + 기대 파일 등재 | **exit 0** |
| `docker://…@sha256:<16자리>` | **exit 1** FORM |
| `docker://…:<태그>` | **exit 1** FORM(핀이 없다) |
| 한 액션을 두 SHA 로 | **exit 1** SET |
| 꼬리 주석의 판만 다르게 | **exit 1** VERSION |

**꼴 ⓒ 는 처음에 도달 불가능한 허용이었다** — 기대 파일 파서가 40자리만 받아 `docker://` 핀은 등재할 길이
없었고, 그래서 늘 SET 으로 붉었다(위 표의 「등재」 행이 그것을 드러냈다). 파서가 두 꼴을 받게 고쳤다.

**`uses` 글자 술어는 자기 step 이름에 걸렸다** — 처음 판은 「`uses` 글자가 두 꼴 밖에 있으면 exit 2」라
새 step 의 이름(「… uses 전부 commit SHA」)에서 발화했다. 키는 **콜론이 있는 자리**로만 읽도록 좁혔다.

## 자기 변이 여섯 (매 실행, D-15S-2)

임시 사본에 **바꿔치우기**로 넣고, 각각 ① 사본이 원본과 실제로 달라졌는지 ② 붉는지 ③ **기대한 사유로**
붉는지를 본다. 세 번째가 없으면 「붉긴 붉은데 다른 이유로」가 통과한다.

| 변이 | 적용 확인 | 기대 사유 | 결과 |
|---|---|---|---|
| 핀 → 태그(`@v5`) | 사본 ≠ 원본 | FORM | 붉음 |
| 39자리 SHA | 사본 ≠ 원본 | FORM | 붉음 |
| 대문자 SHA | 사본 ≠ 원본 | FORM | 붉음 |
| 기대 파일에 없는 40자리 | 사본 ≠ 원본 | SET | 붉음 |
| 꼬리 주석 삭제 | 사본 ≠ 원본 | VERSION | 붉음 |
| 새 워크플로 파일의 태그 `uses` | 파일 생성 | FORM | 붉음 |

적용 확인이 음성이면 게이트가 **exit 2 로 선다**(초록으로 넘기지 않는다). 그 분기는 `sub` 가 아무것도
바꾸지 못하는 경우(주석 없는 줄에 「주석 삭제」)를 받는다.

## 손 변이 둘 — 실물에서

변이 전에 `git status --porcelain -- <경로>` 가 **빈 출력**임을 확인하고(미커밋 산출물이 함께 지워지는
사고를 막는다) 변이 → 적용 확인 → 판정 → `git checkout --` 복원 순서로 했다.

| 바꿔치운 것 | `git diff --numstat` | 게이트 | 복원 뒤 |
|---|---|---|---|
| `ci.yml` 의 첫 checkout 핀 → `@v5` | `1 1` | exit 1 · FORM | porcelain 빈 출력 · exit 0 |
| 기대 파일의 checkout SHA → 다른 40자리 | `1 1` | exit 1 · SET(두 값을 다 찍는다) | porcelain 빈 출력 · exit 0 |

## 착수 실측

다섯 항목은 `scope.md` 의 「착수 실측」 절에 사실로 적었다(태그→커밋 조회 · 복합 액션 전이 `uses` 0 건 ·
`contractGate` 가 원격을 다시 치지 않음 · base 의 자격 사용 step 0 건 · census 등재 자리 부재). 여기서
되풀이하지 않는다.

`contractGate` 축만 명령을 적는다 — 저장소 밖 임시 clone 에서 `git remote remove origin` 한 뒤
`buf build '.git#tag=<승인 태그>,subdir=contracts' -o /dev/null` **exit 0**,
`buf breaking contracts --against '.git#tag=<승인 태그>,subdir=contracts'` **exit 0**. 원격이 아예 없는
트리에서 둘 다 서므로 `persist-credentials: false` 가 이 게이트를 깨지 않는다.

## 수정 라운드 1 — 수집을 구조로 닫았다

verifier H-1 은 **수집이 열거라서 열린 자리**다. 표기 넷을 하나씩 막지 않고 층을 하나 더 세웠다.

**㉮ awk 수집 자체**(세 원인을 구조로):
| 고친 것 | 앞 판이 왜 열렸나 |
|---|---|
| 블록 본문 기준 = **키의 열**(대시 열이 아니다) | `- if: >-` 의 대시는 6열, 형제 키는 8열 → 형제가 본문으로 삼켜졌다 |
| 지시자 앞에 **공백을 요구**하고 첫 콜론에만 건다 | `name: x:|` 을 블록으로 오인했다 |
| 따옴표 친 키·복합 키·홀로 선 지시자·선두 탭 → **exit 2** | 「`uses` 키가 아님」으로 보여 unsupported 로도 안 갔다 |

**㉯ 수집 완전성 = 고정된 파서와의 다중집합 등식**(`tools/workflow-action-pins-parity.py`,
`ml-engine` job S-12). 게이트의 새 `--count` 출력 == PyYAML 이 같은 파일들에서 읽는 `uses` 값.

H-1 의 표기 넷이 **각각 무엇에 잡히는가**(버릴 디렉터리, 자기 변이는 끈 채):

| 표기 | 게이트 | parity |
|---|---|---|
| `- if: >-` 다음 줄 형제 `uses` | **exit 1 FORM**(수집된다) | exit 0 — 등식 성립 |
| `- name: x:\|` 다음 줄 `uses` | **exit 1 FORM**(수집된다) | exit 0 — 등식 성립 |
| 따옴표 키 `"uses"`·`'uses'` | **exit 2** 따옴표 친 키 | **exit 2** 수집을 내지 않는다 |
| 복합 키 `? uses` | **exit 2** 복합 키 | **exit 2** 수집을 내지 않는다 |

**그 층이 실제로 받는가** — 앞 판 awk(블록 기준을 대시 열로 되돌린 사본)에 같은 `- if: >-` 꼴을
주면 게이트는 **exit 0 「수집 24」**(파서는 25)이고 parity 가 **exit 1 · 「파서만 수집: …」** 로
선다. 표기를 열거하지 않고도 그 부류 전체가 붉는다.

parity 자신의 음성 대조는 **매 실행** 돈다 — 각 목록을 자기 자신과 비교하되 한 건을 빼고 본다.
처음에는 두 목록을 서로 비교하며 한쪽만 줄였는데, **게이트가 한 건을 놓친 판에서 그 조작이 우연히
두 다중집합을 같게 만들어** 진짜 위반이 「음성 대조 실패」로 가려졌다(실측). 자기 자신과 비교하는
꼴로 바꿨다.

PyYAML 은 YAML 1.1 이라 맨 `on:` 을 **불리언 키 `True`** 로 읽는다(실측). `uses` 수집과는 무관하다 —
수집기가 `uses` 가 아닌 키의 **값으로 내려가므로** 그 서브트리도 그대로 훑는다.

## 자기 변이 열일곱 — 술어마다 하나 이상

앞 판 여섯은 FORM 과 「한 액션 두 SHA」만 실질로 지켰다(verifier M-2). 기대 파일 쪽 변이는 0 이었다.

| 변이(전부 바꿔치우기) | 기대 사유 |
|---|---|
| 핀→태그 · 39자리 · 대문자 · 새 워크플로 파일 · **블록 키(`- if: >-`)** | FORM |
| 같은 줄을 둘로(한 액션 두 SHA) · **모든 핀을 다른 SHA 로** | SET |
| 기대 파일: SHA 변경 · 한 줄 삭제 · 안 쓰는 줄 추가 | SET |
| 꼬리 주석 삭제 · 주석 판만 변경 · 기대 파일의 판 칸 변경 | VERSION |
| `./ci/act` 로 바꿔치움 | LOCALDIR |
| 흐름 매핑 `- {uses: …}` | UNSUPPORTED |
| 워크플로 전부를 `uses` 없는 하나로 | EMPTY |
| 기대 파일 줄을 두 칸으로 | POLICY |

열일곱 전부 ① 사본 ≠ 원본 확인 ② 붉음 ③ **기대한 사유로** 붉음. 세 번째가 없으면 「붉긴 붉은데
다른 이유로」가 통과한다. 비용 — 자기 변이 포함 전건이 1 초 안쪽이다.

## 술어 무력화 대조 — 변이가 그 술어에 **기대고 있는가**

스크립트 사본에서 술어를 하나씩 **바꿔치워** 끄고(`viol()` 가 그 분류를 버리게 하거나 `unsup()` 이
정지 깃발을 세우지 않게) 자기 변이 열일곱을 돌렸다. 무편집 사본은 exit 0(양성 대조).

| 끈 술어 | 무력화 적용 | 결과 |
|---|---|---|
| FORM | 1 줄 | **exit 2** — `tag` 변이가 초록이 됐다 |
| SET | 1 줄 | **exit 2** — `twoshas` 가 초록 |
| VERSION | 1 줄 | **exit 2** — `nocomment` 가 초록 |
| LOCALDIR | 1 줄 | **exit 2** — `localuses` 가 초록 |
| EMPTY | 1 줄 | **exit 2** — `empty` 가 다른 사유로 붉음 |
| POLICY | 1 줄 | **exit 2** — `pins_policy` 가 다른 사유로 붉음 |
| UNSUPPORTED(정지 깃발) | 1 줄 | **exit 2** — `flowmap` 이 초록 |

일곱 전부 자기 변이가 알아챈다. 앞 판에서는 이 가운데 다섯이 **꺼도 초록**이었다.

## 그 밖의 지적 처분 — 실측

| 지적 | 고친 뒤 |
|---|---|
| cr medium-2 `SELFCHECK` 가 `1` 이 아니면 말없이 꺼짐 | `true` → **exit 2** · `0` → 「건너뜀」 한 줄을 stderr 에 찍고 exit 0 |
| cr medium-3 후행 슬래시 → 「통과」 찍고 exit 1 | 인자 끝 `/` 를 벗긴다 → **exit 0 · stderr 없음** |
| cr medium-3 「통과」가 자기 변이 **앞** | 순서를 뒤집었다 — 이제 자기 변이 통과 줄이 먼저다 |
| cr low CRLF 비대칭(`v5.1.0 != v5.1.0`) | 워크플로 줄에서도 CR 을 벗긴다 → CR 1256 줄 사본에서 **exit 0** |
| cr low `--max-time` 은 시도별 | 세 자리(shfmt·buf 둘·trivy)에 `--retry-max-time 300` · 주석을 사실에 맞춤 |
| verifier L-1 `.YML`·심볼릭 링크 | 수집을 **fail-closed 로 넓혔다**(`-iname`·`-L`). GitHub 문서는 「`.yml`·`.yaml`」과 「`.github/workflows` 디렉터리」만 적고 **대문자 확장자·하위 디렉터리·symlink 실행 여부는 적지 않는다**(팀장 조회) — 「무시한다」에 기대지 않는다. parity 도 같은 규칙을 쓴다 |
| 라운드 마무리 — `tools/one-command-check.sh` | 팀장이 in_scope 를 넓혀 **같은 라운드에서 닫았다**(`_step` 한 줄 + 명령 한 줄, `git diff --numstat` **3 0**). 그 스크립트의 「세 job 의 명령 그대로」 선언이 다시 참이다 |
| cr low `local f` · `set -- $stats` | `local` 목록에 더하고 `read -r` 로 바꿨다 |
| cr low 기대 파일 머리 주석 | 칸 셋 규칙과 「경로 붙은 첫 칸은 앞 둘만 저장소」를 더했다 |

## 승인 전 일괄 — 라운드 1 리뷰의 남은 다섯

차단 지적이 아니라 **승인 전 정리**다(verifier `ready-for-review` · code-reviewer `approve`).

| 지적 | 처분 | 실측 |
|---|---|---|
| parity 의 파일 목록이 게이트와 **다르다**(cr medium · v L-A) | `Path.rglob` 은 **디렉터리 링크를 따라가지 않는다** — `os.walk(followlinks=True)` 로 맞췄다. 세 자리에 적은 「같은 규칙」이 비로소 참이다 | 링크 디렉터리를 둔 사본: `find -L` **2** · 게이트 `--count` **25** · parity **25 · exit 0**(앞 판은 exit 1 「게이트만 수집」) |
| parity 의 예외 갈래가 traceback + exit 1(cr medium) | 「어긋났다」만 1. 나머지는 **경로를 붙인 한 줄 + exit 2** | 값이 문자열 아님 · 깨진 YAML · 비-UTF8 · 게이트 실행 실패 · 링크 순환 — **다섯 다 exit 2** · 정상 대조군 exit 0 |
| 음성 대조가 구조상 늘 참(cr low · v L-B) | `mismatch(v, v[1:])` 를 버리고 **본 판정과 같은 `compare_dir`** 에 두 수집이 실제로 갈리는 입력을 준다 | 아래 |
| `--retry-max-time` 총합 문면(cr low) | 세 자리를 「그 값 + 마지막 시도의 `--max-time`, ≈364s」로 | 숫자는 그대로 |
| 따옴표 친 **값**을 키로 오진(cr low) | 따옴표 뒤에 **콜론이 올 때만** 키 | 아래 |

**음성 대조의 입력**은 한 매핑에 `uses:` 를 두 번 둔 파일이다 — awk 는 줄 단위라 **둘**을 세고
PyYAML 은 중복 키를 **하나**로 접는다(실측). 두 **진짜** 수집기가 갈리는 입력이라 `compare_dir`
전체가 그 경로를 지난다. 치환 대조 셋 — `parsed = list(gate)` · `mismatch(gate, gate)` ·
`mismatch` 가 늘 빈 목록 — **전부 exit 2**, 무치환 사본 exit 0. 앞 판은 세 치환 모두에서 초록이었다.

**따옴표 좁힘** — `- "src/**"` · `- 'docs/**'` · `- "a: b"` 는 **exit 0**(수집 1 · parity 등식
성립)이고 `- "uses":` · `- 'uses':` · `- "name": x` 는 **여전히 exit 2** 다. 좁힌 틈은 **두 층이
함께** 받는다: 줄을 넘는 따옴표 값 안에 `uses: evil/action@v1"` 을 숨기면 게이트가 **FORM**,
parity 가 **「게이트만 수집: evil/action@v1"」** 로 붉는다. 자기 변이는 `quotedkey` 를 더해
**열여덟**이 됐다.

## 하지 않은 것

- 액션을 로컬에서 실행하지 않았다(`act` 등). **핀이 받아지는가**는 PR 의 CI 다섯 job 이 유일한 실측 자리다.
- 액션의 판을 올리지 않았다. 기존 「Node 24 를 만족하는 가장 낮은 major」 주석과 그 정책은 그대로다.
- 기대 파일의 SHA↔판 대응이 **옳은가**는 오프라인 게이트가 재지 못한다 — 위 표의 조회가 그 자리이고,
  리뷰가 같은 조회로 다시 잰다(제한 5).
- `config/quality/shell-guard-policy.properties` 는 무편집이다 — 셸 census 가 아니라 `shfmt` 핀이다.

# M15 / 15S — CI 공급망: 액션 commit SHA 핀 · 토큰 권한 축소 (PR #204 독립 보안 리뷰 S-5)

```yaml
milestone: M15
slice: 15s-ci-action-pins
base_sha: 8e7a91ead0e01e6a4f4c5f4d827d2668176a80b6   # main, PR #205 머지 커밋
head_sha: <리뷰 요청 시점에 기입>
in_scope:
  - .github/workflows/ci.yml                  # uses 전부 SHA 핀 · permissions · persist-credentials · 내려받기 시간 상한 · 게이트 step
  - tools/workflow-action-pins-check.sh       # 신설 — 핀 게이트(자기 변이 대조 포함)
  - tools/workflow-action-pins-parity.py      # 신설(수정 라운드 1, 팀장 승인) — 수집 등식
  - tools/one-command-check.sh                # 수정 라운드 1(팀장 승인) — parity step 두 줄만
  - config/quality/workflow-action-pins.txt   # 신설 — 핀 기대 파일(액션 · SHA · 판)
  - config/quality/shell-guard-policy.properties   # 새 스크립트가 그 census 에 걸릴 때만
  - milestone-15.md                           # 이 slice 가 닫는 것 한 문단
  - reports/evidence/m15/15s/
out_of_scope:
  - ml-serving 이미지의 pip 해시 고정(`docker/ml-serving.Dockerfile`) — 이미지 빌드가 바뀌는 별도 slice.
  - Gradle `verification-metadata.xml` · wrapper `distributionSha256Sum` — 별도 slice(생성물이 크고
    러너·로컬의 해석 집합이 갈릴 수 있어 따로 잰다).
  - 핀을 올리는 자동화(dependabot·renovate) — 저장소에 PR 을 여는 봇을 들이는 것은 운영자 결정이다(제한 2).
  - GitHub 저장소 설정(「full-length SHA 로 핀한 액션만 허용」) — 운영자 몫(제한 1).
  - 액션의 **판을 올리는 것** — 오늘 major 태그가 가리키는 커밋을 그대로 핀한다. 거동 변화 0 이 목표다.
  - 러너 이미지(`ubuntu-latest`)·컨테이너 base 이미지의 핀 — 다른 축이다.
acceptance:
  - ./tools/workflow-action-pins-check.sh .github/workflows          # 신설 게이트(자기 변이 포함) exit 0
  - ./tools/workflow-step-refs-check.sh .github/workflows/ci.yml     # 기존 게이트가 그대로 초록
  - ./gradlew check --no-daemon                                      # Kotlin job(leakPatternGate 가 evidence 를 훑는다)
  - PR 의 CI 다섯 job(check · ml-engine · web · container · mutation) green — **핀이 실제로 받아지는가의
    유일한 실측 자리**다. 로컬에서는 액션을 돌릴 수 없다.
rollback: reports/evidence/m15/15s/rollback.md
```

## 무엇을 닫는가

`.github/workflows/ci.yml` 의 `uses:` 24 건(액션 일곱)이 전부 떠 있는 major 태그이고 commit SHA 핀이
0 건이다. 같은 파일이 trivy·shfmt·buf 를 액션으로 받지 않는 사유로 드는 사고(「액션 태그 전부가 악성
커밋으로 force-push 됐다」)가 자기가 쓰는 액션에는 그대로 열려 있다. `container` job 에서 그 액션 셋은
shfmt 설치와 자격 누출 정적 술어보다 **먼저** 돌므로, 태그가 무너지면 게이트가 재기 전에 작업 트리가
바뀌고 게이트는 심어진 내용을 보증한다.

## 위협 모델 경계

**막는 것**: 상류 액션 저장소의 **태그 이동**(force-push · 태그 재지정 · 탈취된 관리자)으로 이 CI 가
어제와 다른 코드를 돌리게 되는 것. SHA 핀은 그 축을 닫는다 — 같은 SHA 는 같은 트리다. 그리고 무너진
step 이 쥐는 것의 크기: `GITHUB_TOKEN` 을 읽기 전용으로 내리고(D-15S-4) checkout 이 그 토큰을
`.git/config` 에 남기지 않게 한다(D-15S-5).

**막지 않는 것**:
- **핀한 그 커밋이 이미 나쁜 경우** — 핀은 「바뀌지 않음」이지 「검토됨」이 아니다.
- 액션이 **실행 중에 내려받는 것**(setup-java 의 JDK · setup-node 의 Node · setup-uv 의 uv · setup-gradle
  의 배포판 · 러너 이미지 자체). 그 축은 각 도구의 판 핀과 lock 파일이 따로 진다.
- 이 저장소에 쓸 수 있는 저자 — 핀을 태그로 되돌리는 PR 은 게이트(D-15S-2)가 붉히지만, 같은 PR 이
  게이트와 기대 파일을 함께 고치면 통과한다. 그 줄은 리뷰가 본다(게이트는 **실수의 래칫**이지 저자에
  대한 방어가 아니다 — 게이트 자신이 checkout 뒤에 돈다).
- 복합 액션이 **안에서 부르는** 다른 액션 — 일곱 가운데 그런 것이 있는지는 착수 실측으로 적는다(제한 3).

요구 축소가 아니다: S-5 의 처방은 「저장소 전체의 `uses:` 를 commit SHA 로 핀하고 뒤에 `# vX.Y.Z` 주석」
이고 이 slice 는 그 전부에 게이트를 더한다. pip·Gradle 두 축은 S-5 의 문면이 아니라 뒤따른 후속 목록의
것이라 out_of_scope 로 나눴다.

## 결정

**D-15S-1 — 24 건 전부 `owner/repo[/path]@<40자리 소문자 16진>  # vX.Y.Z` 꼴.** SHA 는 **오늘 그 major
태그가 가리키는 커밋**이다(annotated tag 면 벗겨서 commit 까지). 주석의 판은 그 커밋에 붙은 **가장
구체적인 릴리스 태그**(`v5.0.1` 꼴)이고, 그런 태그가 없으면 major 를 적고 제한에 올린다. 조회는
`gh api repos/<owner>/<repo>/git/ref/tags/<tag>`(+ tag 객체면 `git/tags/<sha>`)로 한다 — 공개 정보이고
이 저장소의 어떤 값도 실어 보내지 않는다. 기존 「Node 24 를 만족하는 가장 낮은 major」 주석과 major 정책은
그대로 둔다(판을 올리지 않는다).

**D-15S-2 — 게이트 `tools/workflow-action-pins-check.sh <워크플로 디렉터리>`.** 선례는
`tools/workflow-step-refs-check.sh`(계약 D-7F-7)다: 새 의존 0(awk·bash), **읽지 못하는 표기는 통과가
아니라 정지(exit 2)**. 술어는 열거가 아니라 등식이다.
- **수집**: 디렉터리의 모든 `*.yml`·`*.yaml` 에서 `uses` 키의 값 전부 — step 자리(`- uses:` · `uses:`)와
  job 자리(재사용 워크플로) 양쪽. 블록 스칼라(`run: |`) 본문의 `uses:` 글자는 세지 않는다. 따옴표 값은
  벗겨서 센다. 따옴표 없는 흐름 매핑(`- {uses: …}`)·여러 줄로 쪼갠 값·앵커/별칭은 **exit 2**.
  **따옴표 친 키의 흐름 매핑(`- {"uses": …}`)은 이 게이트 혼자서는 초록**이고(수집 0 · exit 0) 그 꼴은
  `tools/workflow-action-pins-parity.py` 의 다중집합 등식이 받는다(M15/15T D-15T-6 문면 정정, 실측
  2026-11-27 — 앞 판 문면은 그 꼴에서 거짓이었다).
- **꼴**: 수집한 값마다 셋 중 하나여야 한다 — ⓐ `./` 로 시작(저장소 안) ⓑ `owner/repo[/path]@<40hex>`
  ⓒ `docker://…@sha256:<64hex>`. 그 밖(태그·브랜치·짧은 SHA·대문자 16진)은 실패.
- **등식**: 수집한 `(액션, SHA)` **집합** == `config/quality/workflow-action-pins.txt` 의 집합. 양쪽
  `LC_ALL=C sort -u` 뒤 `comm`. 한 액션이 두 SHA 로 쓰이면 실패(한 액션 = 한 핀).
- **주석 판 대조**: `uses` 줄의 꼬리 주석 `# vX…` == 기대 파일의 그 액션의 판 칸. 주석이 없거나 다르면
  실패(주석은 YAML 에 안 보이므로 **줄 텍스트에서** 읽는다 — 이 한 항목만 텍스트 술어이고, 그 사실을
  제한에 적는다).
- **비공허**: 수집 건수 ≥ 1 이고 건수를 출력한다(오늘 24). 0 건이면 실패 — 「오탐 0 인 판이 양성도 0」을
  막는다.
- **자기 변이 대조**: 스크립트가 임시 사본에 변이를 **바꿔치우기**로 넣고 붉어지는지를 매 실행 잰다 —
  최소 여섯: SHA→태그 · 39자리 SHA · 대문자 SHA · 기대 파일에 없는 SHA · 꼬리 주석 삭제 · 새 워크플로
  파일의 태그 `uses`. 각 변이는 **적용됐는지**(사본이 원본과 다른지) 먼저 확인한다.

**D-15S-3 — 기대 파일 `config/quality/workflow-action-pins.txt`.** 한 줄에 `액션<공백>SHA<공백>판`,
`#` 로 시작하는 줄은 주석. 일곱 줄. 머리 주석에 「핀을 올리는 법」(조회 명령 · 두 파일을 함께 고친다)을
적는다. 좌표(`file:line`)는 쓰지 않는다.

**D-15S-4 — 워크플로 최상위 `permissions: contents: read`.** 이 워크플로는 `GITHUB_TOKEN` 을 쓰는 step 이
없다(착수 실측: `secrets.`·`GITHUB_TOKEN`·`gh ` 0 건). artifact 올리기는 그 권한으로 선다. 구현 레인이
CI 에서 다섯 job 이 그대로 초록인지로 확인한다 — 붉어지면 필요한 권한을 **그 job 에만** 더하고 사유를 적는다.

**D-15S-5 — checkout 다섯 자리에 `persist-credentials: false`.** 착수 실측: checkout 뒤에 원격을 다시
치는 step(`git fetch`·`git push`·`git ls-remote`)이 0 건이다. `fetch-depth: 0` 은 checkout **안에서**
끝난다. 구현 레인이 `contractGate`(`buf breaking`)가 원격을 다시 치지 않는지를 CI 초록으로 확인한다.

**D-15S-6 — 내려받기 시간 상한.** shfmt 줄은 이미 `--connect-timeout 10 --max-time 120` 을 갖는다. trivy
줄과 buf 두 줄에 같은 꼴을 맞춘다(buf 에는 `--retry` 셋도 함께). 값은 shfmt 와 같게 — 자산 크기가 달라
`--max-time` 이 모자라면 실측 값을 적고 올린다.

**D-15S-7 — 게이트는 `container` job 의 `workflow-step-refs-check` 바로 옆 step 에서 돈다.** 같은 사유
(그 job 이 워크플로 게이트의 자리다)다. 게이트가 checkout 뒤에 도는 것은 위 경계가 이미 든다.

## 설계 검토 (세션 모델)

**(1) 열거인가 구성인가.** 꼴 검사는 「허용 셋 밖은 전부 실패」라 deny-list 가 아니다. 등식은 집합 대
집합이다. 수집이 열거로 새지 않는지가 15R 의 교훈이었다 — 그래서 수집 단위는 「`uses` 키의 값 전부」이고
못 읽는 표기는 exit 2 다.

**(2) 우회.** ① 따옴표 `uses: "a/b@v5"` → 벗겨서 센다 ② 새 워크플로 파일 → 디렉터리 전체를 훑는다
③ 재사용 워크플로의 job 자리 `uses` → 수집 대상 ④ 따옴표 없는 흐름 매핑·앵커 → exit 2(**따옴표 친 키의
흐름 매핑은 이 게이트 혼자서는 초록이고 parity 등식이 받는다** — M15/15T D-15T-6 문면 정정) ⑤ 태그 이름이 40hex 처럼
생긴 경우 → GitHub 가 그런 태그·브랜치 생성을 막는다(경계 문장으로 처리, 실측하지 않는다 — 제한 4)
⑥ `.github/actions/**/action.yml` 의 복합 액션 → 오늘 그 디렉터리가 없다. 생기는 날 게이트가 **그
디렉터리의 존재를 실패로** 본다(「보지 않는 자리가 생겼다」를 조용히 넘기지 않는다) ⑦ 주석만 바꿔 판을
속이기 → 기대 파일과 대조하지만 SHA↔판의 진위는 오프라인에서 못 잰다(제한 5 — verifier 가 한 번 조회로 잰다).

**(2b) 값 획득 축.** 새 public 표면은 스크립트 하나와 기대 파일 하나다. 스크립트는 읽기만 하고 네트워크를
치지 않는다. `permissions` 축소와 `persist-credentials: false` 는 표면을 **줄이는** 쪽이다.

**(3) 과잉·미달.** 주석 판 대조는 S-5 처방(「뒤에 `# vX.Y.Z` 주석」)을 게이트로 잠그는 것이라 과잉이
아니다. pip·Gradle 을 뺀 것은 위에 사유를 적었다.

## 착수 실측 (2026-11-26, 구현 레인)

**① 액션 일곱의 태그 → 커밋, 그리고 그 커밋의 가장 구체적인 릴리스 태그.** `gh api` 로 조회했고
요청에 이 저장소의 이름·값을 싣지 않았다(전부 상류 공개 정보). 둘(`astral-sh/setup-uv`·
`gradle/actions`)은 annotated tag 라 `git/tags/<sha>` 로 한 번 더 벗겼다. 일곱 **전부** 그 커밋을
가리키는 `vX.Y.Z` 태그가 있어 「major 만 적고 제한에 올린다」는 갈래는 쓰이지 않았다. 값은
`config/quality/workflow-action-pins.txt` 가 든다.

**② 복합 액션 안의 전이 `uses`(제한 3).** 일곱의 `action.yml` 을 그 커밋에서 읽었다 — **전부
`using: node24`** 자바스크립트 액션이다. 복합 액션(`using: composite`)이 하나도 없으므로 오늘
전이 `uses` 는 **0 건**이고, 이 저장소가 고정하지 못하는 상류 자리도 0 이다. 제한 3 은 「오늘
해당 없음, 복합 액션이 들어오는 날 생긴다」로 좁혀진다.

**③ `contractGate` 가 원격을 다시 치는가(D-15S-5).** 치지 않는다. `buf breaking` 의 대조 대상
`.git#tag=<승인 태그>,subdir=contracts` 는 **로컬 이력**을 읽는다 — `origin` 을 **지운** clone 에서
`buf build` 와 `buf breaking` 둘 다 **exit 0**(저장소 밖 임시 clone, 2026-11-26). `fetch-depth: 0`
은 checkout 안에서 끝난다. 그래서 `persist-credentials: false` 가 이 게이트를 깨지 않는다.

**④ 토큰을 쓰는 step(D-15S-4).** base(`8e7a91ead`)의 `ci.yml` 에서 `secrets.`·`GITHUB_TOKEN`·
`gh `·`git fetch`·`git push`·`git ls-remote`·`git clone` 전부 **0 건**. `permissions`·
`persist-credentials` 선언도 0 건이었다.

**⑤ census 등재(in_scope 의 조건부 항목).** `config/quality/shell-guard-policy.properties` 는 **셸
스크립트의 census 가 아니라 `shfmt` 바이너리의 핀**이다. 저장소에 `tools/*.sh` 를 전수로 훑는
게이트가 없고(선례 `tools/workflow-step-refs-check.sh` 도 어느 등록부에도 없다), `gate-tests
.properties` 는 Kotlin test 클래스 등록부다. 그래서 **새 스크립트의 등재 자리가 없다** — 이
in_scope 항목은 쓰이지 않았고 그 파일은 이 slice 에서 무편집이다.

## 계약 갱신 — 수정 라운드 1 (2026-11-26, 구현 레인의 사실 기록)

verifier `not-ready`(H-1 · M-1 · M-2 · L-1 · L-2)와 code-reviewer `approve`(medium 3 · low 6)를
받아 한 라운드로 닫았다. 계약 문면을 바꾸는 것 넷을 **사실로** 적는다.

**① D-15S-2 의 꼴 ⓐ(`./` 로 시작 = 저장소 안)는 「허용」이 아니라 「오늘은 실패」다**(팀장 결정,
verifier M-1 · cr medium-1). 앞 판은 세기만 했고, 「보지 않는 자리가 생기면 실패」를
`.github/actions` **디렉터리 이름**에 걸어 `./tools/act` 처럼 다른 자리에 두면 그 `action.yml` 안의
태그 `uses` 가 한 단계 간접으로 그대로 열렸다(실측 exit 0). 술어를 이름이 아니라 **참조 자체**로
옮겼다. 저장소 안 액션을 실제로 들이는 날에는 게이트를 그 `action.yml` 까지 넓히는 것이 맞고,
그 전에는 조용히 통과시키지 않는다. 오늘 `nlocal == 0` 이라 거동 변화는 0 이다. 디렉터리 존재
검사는 겹으로 남겼다.

**② 수집 완전성을 문자열이 아닌 층에 건다 — 새 파일 `tools/workflow-action-pins-parity.py`**
(팀장 승인 2026-11-26, in_scope 줄은 구현 레인이 직접 더했다). H-1 은 15R 과 같은 결함
클래스다(수집이 열거라서 열렸다). 표기 넷을
하나씩 막는 것으로 끝내지 않고, 게이트의 `--count` 가 내는 `uses` 다중집합 == 고정된 YAML
파서(PyYAML, `ml-engine` 의 `uv.lock` 에 핀)가 읽는 다중집합을 **CI 가 매 PR 에서** 잰다. awk 가
모르는 표기는 이제 「조용히 건너뜀」이 아니라 **등식 붉음**이다.

**③ 두 자리가 나뉜다.** 판정 게이트(꼴·집합 등식·판 주석)는 `container` job 에 남는다 — 그 job 의
전제가 「새 의존 0」이다. parity 는 `ml-engine` job 이 진다(S-12) — `pyyaml` 이 lock 으로 핀된
자리가 거기뿐이다. 파서를 container 로 들이면 그 전제가 깨지고, 게이트를 ml-engine 으로 옮기면
「새 의존 0」이 깨진다.

**④ D-15S-2 「양쪽 `LC_ALL=C sort -u` 뒤 `comm`」은 구현과 문면이 다르다**(verifier L-2). 집합
등식의 구현은 awk 연관 배열의 양방향 순회다 — 답은 같고 정렬에 기대지 않는다. `LC_ALL=C` 는
스크립트 전체에 걸려 있고, `--count` 모드의 출력만 `sort` 를 쓴다(parity 가 읽는 자리).

**⑤ `tools/one-command-check.sh` 가 in_scope 에 들어왔다**(팀장 승인 2026-11-26). 그 스크립트는
머리 주석에서 「ci.yml 의 세 job 이 돌리는 명령 **그대로**, CI 전용 step 만 뺀다」고 선언하는데
새 S-12(parity)는 그 예외 셋 중 어느 것도 아니라, 더하지 않으면 선언이 거짓이 된다. **그 등식을
기계로 재는 게이트는 없어** 드리프트가 붉지 않는다 — 그래서 문면이 유일한 방어이고, 그것을
참으로 유지한다. 고친 것은 `_step` 한 줄과 명령 한 줄뿐이고 그 밖의 줄은 건드리지 않았다.

**⑥ 수집 대상 파일 목록은 fail-closed 다**(verifier L-1 · 팀장 결정). GitHub 의 workflow-syntax
문서는 「`.yml` 또는 `.yaml`」과 「`.github/workflows` 디렉터리」만 적고 **대문자 확장자·하위
디렉터리·심볼릭 링크를 실행하는지는 적지 않는다**(팀장 조회, 읽은 범위에서). 「GitHub 가
무시한다」에 기대지 않고 게이트와 parity가 **같은 규칙으로 넓게** 모은다 — 확장자 대소문자 무시
(`-iname`), 심볼릭 링크 따라감(`-L`), 하위 디렉터리 재귀. 손해는 「안 돌릴 파일에까지 핀을
요구한다」뿐이고, 반대 방향은 조용한 수집 누락이다.

**⑦ 게이트 술어 하나를 좁혔다 — 따옴표 친 「키」만 정지다**(승인 전 일괄, cr low). 앞 판은
대시를 벗긴 뒤 따옴표로 시작하면 키인지 값인지 가리지 않고 exit 2 였고, `- "src/**"` 같은 평범한
편집이 판정 불가로 서면서 **사실과 다른 사유**(값을 키라고 말한다)를 찍었다. 따옴표 뒤에 콜론이
올 때만 키로 본다. 좁혀도 안전한 근거는 ② 다 — 수집 누락은 parity 등식이 따로 받고, 좁힌 틈
(줄을 넘는 따옴표 값에 숨긴 `uses:`)은 게이트 FORM 과 parity 양쪽으로 붉는다(실측).

**⑧ parity 의 파일 목록을 게이트와 실제로 같게 했다**(승인 전 일괄, cr medium · verifier L-A).
⑥ 이 「같은 규칙」이라고 적었지만 `Path.rglob` 은 **디렉터리 심볼릭 링크를 따라가지 않아** 그
문장이 들어온 라운드에 이미 거짓이었다. `os.walk(followlinks=True)` 로 맞췄고 링크 순환은
정지(2)다. 제한 11 이 경고한 상태가 제한 11 과 함께 들어왔던 자리이고, 문서가 아니라 코드를
고쳤다.

## 하네스 레인 변경

없음. `tools/` 의 게이트 스크립트는 `.claude/` 하네스가 아니다.

## 알려진 제한

1. **진짜 강제 층은 저장소 설정이다** — GitHub 의 「full-length commit SHA 로 핀한 액션만 허용」 정책은
   파일을 고쳐서는 우회할 수 없다. 이 slice 의 게이트는 같은 트리 안의 스크립트라 그보다 약하다. 켜는
   것은 운영자 몫이다.
2. **핀은 낡는다** — 올려 주는 봇이 없으면 보안 수정도 함께 멈춘다. 올리는 절차는 기대 파일 머리 주석이 든다.
3. 복합 액션 안의 전이 `uses` 는 이 저장소가 고정할 수 없다(상류의 것). **오늘은 해당 없음** —
   일곱이 전부 `using: node24` 라 전이 `uses` 가 0 건이다(착수 실측 ②). 복합 액션이 들어오는 날
   생기고, 그 자리는 게이트의 `.github/actions` 디렉터리 술어가 알린다.
4. 40hex 꼴의 태그·브랜치 이름은 GitHub 가 막는다는 전제에 기댄다(실측하지 않음).
5. 기대 파일의 SHA↔판 대응은 오프라인 게이트가 재지 못한다 — 리뷰 때 조회로 잰다.
6. **구현 중 생긴 것** — `permissions` 블록의 설명 주석이 `GITHUB_TOKEN`·`secrets.` 를 문면에
   담는다. 「이 워크플로에 토큰 사용 step 0 건」을 **나중에 다시 재는 사람**이 주석을 세지 않게
   해야 한다(실측은 주석 줄을 뺀 뒤 센다 — 착수 실측 ④ 와 같은 꼴).
7. **꼬리 주석의 판 대조는 이 게이트의 유일한 텍스트 술어다**(D-15S-2 가 예고한 자리). 주석은
   YAML 에 보이지 않아 구조로 닫을 수 없다. 그래서 막는 것은 「주석이 없거나 기대 파일과 다름」
   뿐이고, 기대 파일과 주석이 **함께** 틀린 경우는 상류 조회만이 잡는다(제한 5 와 같은 자리).
8. ~~`tools/one-command-check.sh` 드리프트~~ — **같은 라운드에서 닫았다**(계약 갱신 ⑤, 팀장이
   in_scope 를 넓혔다). 남는 사실 하나는 적어 둔다: **그 등식을 기계로 재는 게이트는 여전히
   없다.** 저장소에서 그 스크립트를 가리키는 자리는 산문과 ci.yml 의 S-20 호출뿐이라, 다음에
   누가 job 에 step 을 더하고 이 스크립트를 잊어도 **아무것도 붉지 않는다**. 이 slice 는 그 축에
   게이트를 세우지 않았다(범위 밖 — `uses` 핀과 다른 축이다).
9. **수정 라운드 1 신설 — 루트 `tools/*.py` 는 린트·타입 검사 밖이다.** `ruff`·`mypy --strict` 는
   `working-directory: ml-engine` 에서 `.`·`src/ml_engine` 만 본다. 새 parity 스크립트도, 먼저
   있던 `tools/floor-trust-allowlist.py` 도 그 밖이다. 그래서 작고 평범하게 썼다.
   그 스크립트의 **거짓 통과**는 매 실행 도는 음성 대조가 받는다 — 본 판정과 **같은 함수**에 두
   수집이 실제로 갈리는 입력(한 매핑의 중복 `uses:`)을 주고 1 이 나오는지 잰다. 그래서 비교
   함수를 비우거나 두 수집을 한 출처로 묶는 치환은 거기서 선다(실측 셋). **받지 못하는 것**은
   따로 적는다: 두 수집이 **같은 방식으로** 틀리는 경우(제한 11)와, 음성 대조 자신을 지우는
   편집이다 — 그 줄은 리뷰가 보는 diff 다.
10. **수정 라운드 1 신설 — 문서가 말하지 않는 것에 기대지 않았다.** GitHub 의 workflow-syntax
   문서는 대문자 확장자(`.YML`)·`.github/workflows` 하위 디렉터리·심볼릭 링크 워크플로를
   GitHub 이 실행하는지 **적지 않는다**(팀장 조회, 읽은 범위에서). 그래서 「실행하지 않으니
   수집하지 않아도 된다」가 아니라 **수집한다**(계약 갱신 ⑥). 반대로, GitHub 이 실제로 무엇을
   실행하는지를 이 slice 가 잰 것도 아니다 — 그 축은 열려 있고 fail-closed 가 덮을 뿐이다.
11. **수정 라운드 1 신설 — 두 수집이 같은 방식으로 틀리면 등식이 잡지 못한다.** parity 는 게이트와
   **같은 규칙**으로 파일 목록을 모으므로(확장자 대소문자 무시 · 심볼릭 링크 따라감), 수집 대상
   파일 자체를 놓치면 둘 다 놓친다. 그 축은 게이트의 비공허 술어와 `.github/actions` 술어가 따로
   받는다. 파일 목록 규칙을 한쪽만 바꾸면 등식이 **거짓말**이 되므로 둘을 함께 고쳐야 한다.

# M15 / 15T — commands

base `afc3b9db6` · 브랜치 `chore/m15t-ml-serving-pip-hashes`

환경: WSL2 Ubuntu · bash · Docker 29.5.1 · host `uv 0.11.28`(쓰지 않았다 — 판정에 쓴 uv 는 **이미지가
핀한 0.9.22** 하나다. `ghcr.io/astral-sh/uv:0.9.22@sha256:2320e6c2…` 에서 뜯어 scratchpad 에서 실행) ·
베이스 `python:3.12.16-slim-bookworm@sha256:6dd8b6da…`(정책 다이제스트) · 이미지 안 `pip 25.0.1`.
운영 host·systemd·운영 컨테이너(`bidvector-v2-prod-*`)·실 DB/API/Telegram 에 닿는 명령은 하나도 없다.
실험 태그는 `bidvector/ml-serving:15t-*` 로 띄우고 전부 지웠다 — `docker ps` 에서
`bidvector/ml-serving:local` 을 쓰는 컨테이너는 **0** 이었다(쓰는 `:local` 셋은 다른 프로젝트의 것).

제한·계약 변경의 정본은 `scope.md` 다(「알려진 제한」·「계약 갱신」). 여기는 **실측**만 적는다.

## uv·pip 옵션 확정 — 문서와 실측

판정 근거는 **핀한 바이너리의 자기 문면**(`uv 0.9.22` 의 `--help`, 그 판에 대해 정본)과 상류 문서다.

| 옵션 | 근거 | 확정 |
|---|---|---|
| `uv export` 가 해시를 **기본으로** 싣는다 | `export --help`: `--no-hashes  Omit hashes in the generated output` | 플래그를 더하지 않는다 |
| `--no-emit-project` | 같은 `--help`: 「Do not emit the current project」 | 프로젝트 자신을 목록에서 뺀다 |
| `--locked` vs `--frozen` | docs.astral.sh/uv/concepts/projects/sync/ · reference/cli — `--locked` 「Requires that the lockfile is up-to-date … uv will exit with an error」 / `--frozen` 「Instead of checking if the lockfile is up-to-date, uses the versions in the lockfile as the source of truth」 | **`--locked`**(아래) |
| `uv build -b/--build-constraints` | `build --help`: 「Constrain build dependencies using the given requirements files when building distributions」 | lock 닫힘 전체를 준다 |
| `uv build --require-hashes` | 같은 `--help`: 「Require a matching hash for each requirement」 | 격리 빌드 환경에 해시 강제 |
| pip 해시 검사 모드 | pip.pypa.io/en/stable/topics/secure-installs/ — 「Specifying `--hash` against *any* requirement will activate this mode globally」 | `--require-hashes` 는 **목록이 비어도** 해시를 요구하게 하는 축이다(아래 우회 ①의 뜻) |
| `pip --report` 스키마 | pip.pypa.io/en/stable/reference/installation-report/ — `version` 은 문자열 `1` 이고 「Tools must check this field」. `archive_info.hashes` 는 **source archive 가 옛 wheel 캐시에서 온 경우 없을 수 있다** | 판이 `1` 이 아니거나 해시 없는 항목이면 **정지**. `--no-cache-dir` + `--only-binary :all:` 이라 문서가 말하는 부재 경우에 닿지 않는다 |

### 보고서 해시의 출처 — pip 25.0.1 소스 두 자리 (cr Open Question)

**이미지가 쓰는 그 판**에서 직접 읽었다(`docker run --entrypoint python … -c 'import pip._internal…'`):

- `direct_url_helpers.direct_url_from_link`: `hash = f"{hash_name}={link.hash}"` → 보고서가 적는 값은
  **색인 링크가 선언한 해시**다(받은 파일에서 다시 계산한 값이 아니다).
- `operations.prepare._get_linked_req_hashes`: `if not self.require_hashes: return
  req.hashes(trust_internet=True)` / 그 밖 `return req.hashes(trust_internet=False) or MissingHashes()`,
  그리고 같은 파일의 세 자리가 내려받은 파일에 `hashes.check_against_path(...)` 를 건다.

→ 해시 검사 모드가 **아닐 때** pip 은 받은 바이트를 **그 링크 해시와** 대조하고, 모드일 때는 **요구
파일(= lock)의 집합과** 대조한다. 어느 쪽이든 「보고서의 값이 lock 에 들고 빌드가 성공했다」는
「출하된 바이트가 lock 의 해시 하나에 대응한다」를 뜻한다. **뒤집히지 않았다** — (나)는
`--require-hashes` 의 재진술이 아니다(아래 우회 ① 실측과 일관된다).

### `--locked` 를 고른 실측 (D-15T-1)

`python:3.12.16-slim` + 핀한 uv + 최소 컨텍스트 사본에서(저장소 트리 무변경):

| 측정 | 결과 |
|---|---|
| 정상 lock · `--network none` · `--locked` | **exit 0** — 「Resolved 35 packages in 1ms」. 재해석이 없어 빌드에 네트워크 의존을 더하지 않는다 |
| `pyproject.toml` 의 `pyyaml` 판만 내린 사본 · `--locked` | **exit 2** — 끊긴다(그 경로에서만 재해석을 시도한다. 빌드 안에는 네트워크가 있다) |
| 같은 사본 · `--frozen` | **exit 0** — 어긋남을 보지 않는다 |
| 같은 사본 · `uv sync --frozen --all-extras --dry-run` | **exit 0** — lock 의 집합을 그대로 설치하겠다고 답한다 |

→ **`ml-engine` job 의 어느 step 도 pyproject↔lock 어긋남을 받지 않는다**(그 job 의 설치는 전부
`--frozen` 이다). builder stage 가 그것을 처음 끊는 자리다.

`--no-dev` 는 이 프로젝트에서 **거동 0** 이다(`dev` 가 dependency group 이 아니라 extra 라
`--extra serving` 이 이미 그것을 뺀다 — 산출이 `cmp` 로 바이트까지 같다). CI `ml-engine` job 의
관용구를 그대로 둔다.

## RED — 구현 전·수정 전 실측

| RED | 결과 |
|---|---|
| 신설 게이트를 **현 main 의 이미지**(`rel-71b1ad3a7aca`)에 | **exit 2** — 「이미지 안 설치 보고서(…)를 읽지 못했다」. 보고서 생성이 없으면 통과가 아니라 정지다(우회 ⑤) |
| 그 이미지의 **설치 집합** ↔ lock 기준 보고서로 판독기만 | **exit 1** · 위반 2건 — `(가) narwhals==2.29.0 이 설치돼 있는데 보고서·프로젝트·base 어디에도 없다` / `(가) narwhals==2.26.0 이 … 설치돼 있지 않다` |
| 자기 변이 기대가 「비-0」이던 판(cr F-1): 변이 ①을 「해시 키 제거」로 바꿔 판독기가 exit 2 를 내게 한 사본 | **exit 0** · 「자기변이=4건 전부 붉음」 — **판정 불가를 붉음으로 셌다** |
| 같은 변이를 고친 판에 | **exit 2** · 「자기 변이 '보고서 해시 바꿔치우기' 가 위반(exit 1)으로 붉지 않았다(exit 2)」 + 판독기 출력 |

`rel-71b1ad3a7aca` 를 「현 main 의 이미지」 자리로 쓴 근거: `git diff --name-only 71b1ad3a7..afc3b9db6
-- docker/ml-serving.Dockerfile ml-engine/uv.lock ml-engine/pyproject.toml` 이 **빈 출력**이다.
둘째 줄이 이 slice 가 닫는 것의 실체다 — 전이 의존 `narwhals` 가 lock 의 2.26.0 대신 2.29.0 으로
떠 있었다.

## acceptance (전부 exit 0)

| 명령 | 결과 |
|---|---|
| `docker build -f docker/ml-serving.Dockerfile -t bidvector/ml-serving:local .` | **exit 0** — **18 step 전부 CACHED** 이고 이미지 id 가 앞 회차와 **같은 객체**다(이 라운드의 Dockerfile 변경이 주석뿐임을 그 동일성이 기계로 보인다). 설치 RUN 의 실제 실행은 아래 대조 넷이 각자 전건으로 쟀다 |
| `./tools/image-hygiene-check.sh … config/quality/image-hygiene-policy.properties` | **exit 0** — 금지-import 5건 · uid 10001 · base-layer 접두 일치 · `size_bytes=343,931,147` / `cap=400,000,000`(86%) |
| `./tools/image-pip-lock-check.sh … ml-engine/uv.lock /opt/bidvector/pip-install-report.json serving` | **exit 0** — 계수·자기 변이는 아래 |
| `./tools/workflow-action-pins-check.sh .github/workflows` | **exit 0** — uses 24(핀 24) · 기대 파일 7줄과 집합 등식 · 자기 변이 18건 |
| `./tools/workflow-step-refs-check.sh .github/workflows/ci.yml` | **exit 0** — job 5 · id 15 · 참조 18 · 매달린 참조 0(새 step 은 id 를 두지 않았다 — 가리키는 자리 없는 id 는 죽은 설정이다) |
| `bv-build.sh ./gradlew --no-daemon check --continue` | **exit 0** · FAILED 0 · `:leakPatternGate` 실행(UP-TO-DATE 아님) |
| `bash -n` / `python3 -m py_compile` | 둘 다 **exit 0** |
| `uv run ruff check ../tools/image-pip-lock-check.py` | exit 1(RUF002 ∪ 기호 · N818) — **이 저장소의 게이트가 아니다**. 선례 `tools/workflow-action-pins-parity.py` 도 같은 N818 과 `ruff format --check` exit 1 을 낸다(저장소 뿌리 `tools/*.py` 는 `ml-engine` 의 ruff 범위 밖) |

`tools/one-command-check.sh` 는 **안 고쳤다** — 계약의 조건(「그 파일이 container 축을 지면」)이
거짓이다(그 스크립트에 `docker build`·위생 게이트가 0건, 지는 축은 `check`·`ml-engine`·`web`).

## 게이트 계수와 자기 변이

| 축 | 계수 |
|---|---|
| lock 의 registry 패키지 | 34(전체 35 − 프로젝트 1) |
| 보고서 항목 | 8 — grpcio · lightgbm · narwhals · numpy · protobuf · pyyaml · scipy · typing-extensions |
| 해시 대조 | 8(`compared == 0` 이면 「불일치 0 이 공허하게 참」이라 정지) |
| 설치 집합 | 10(보고서 8 + 프로젝트 1 + base 1) |
| base 집합 | 1 — `pip 25.0.1`. 손 목록이 아니라 정책 다이제스트 이미지에 **같은 질의**를 걸어 받는다 |
| (다) 요구 이름 | **5** — `grpcio`·`protobuf`(무조건) + `lightgbm`·`numpy`·`pyyaml`(`extra == 'serving'`). lock 의 프로젝트 항목에서 도출하고, extra 이름이 `provides-extras` 에 없으면 정지 |

자기 변이 **다섯**(매 실행, 임시 사본). 기대는 **exit 1(위반) + 기대 사유**다 — 「비-0」으로 세면
판정 불가가 성공으로 읽힌다(위 RED). 변이기가 사본 반영을 스스로 비-0 으로 단언하고, 다섯을 한
번에 만들며 판독기 모듈을 그대로 불러 요구 집합·프로젝트 항목을 얻는다(같은 논리를 두 벌로 두지
않는다).

| 변이 | 기대 | 실측 |
|---|---|---|
| 보고서의 해시 하나를 다른 값으로 **바꿔치우기** | (나) | exit 1 · (나) |
| 보고서에서 **base∪프로젝트∪요구 밖** 항목 제거(`narwhals`) | (가)이고 (다)는 **섞이지 않는다** | exit 1 · (가) 단독 |
| lock 사본에서 한 패키지의 해시 제거(`grpcio`, 11줄) | (나) | exit 1 · (나) |
| 설치 집합 사본에서 한 줄 제거(`grpcio 1.83.1`) | (가) | exit 1 · (가) |
| 보고서에서 **요구 이름** 제거(`grpcio`) | (다) | exit 1 · (다) |

본 판정이 이미 위반이면 자기 변이를 **건너뛴다**(요약에 그렇게 찍는다) — 변이의 기대는 「초록
입력에서 위반이 생긴다」라 입력이 이미 붉으면 사유 대조가 원래 있던 위반을 집는다(아래 F-2 대조에서
실측: exit 1 이어야 할 판정이 exit 2 가 됐다).

## 빌드 층 일회 대조

매 CI 에서 실패 빌드를 돌리지 않는다. **저장소 밖 임시 컨텍스트 사본**에서 재고 사본을 지웠다 —
저장소 트리는 한 번도 변이하지 않았다(`git status --porcelain` 빈 출력로 확인). 변이는 `cmp` 로
적용을 먼저 확인하고, 해시는 그 패키지의 **집합 전체**를 갈았다.

| 대조 | 결과 |
|---|---|
| 사본 그대로(대조군) | `docker build` **exit 0** |
| lock 사본의 `pyyaml` 해시 11건 전부 바꿔치움 → serving 목록 | `docker build` **exit 1** — 설치 step 이 「THESE PACKAGES DO NOT MATCH THE HASHES FROM THE REQUIREMENTS FILE」로 끊긴다(설치 **전**) |
| lock 사본의 `setuptools` 해시 2건 전부 바꿔치움 → 빌드 제약 | `docker build` **exit 1** — `uv build` step 이 exit 2 「Hash mismatch for `setuptools==84.0.0`」 |
| **해시 하나만** 바꿔치움 | `uv build` **exit 0** — 그 패키지의 다른 해시(sdist/wheel)가 아직 맞는다. 변이를 넣는 사람은 집합 전체를 갈아야 한다 |
| **`--extra serving` 제거**(cr F-2 대조) | `docker build` **exit 0** · 신설 게이트 **exit 1** — (다)가 `lightgbm`·`numpy`·`pyyaml` 셋을 사유로 든다(보고서 항목 3 · 설치 5). 같은 이미지에 위생 게이트는 exit 1 이지만 사유가 **기동 실패**다(numpy 부재) — `lightgbm` 만 빠지는 경우는 그 축이 받지 못한다 |
| **`--extra dev` 를 더해 넓힘**(vr F-2 대조) | `docker build` **exit 0** · 신설 게이트 **exit 0**(보고서 33 · 설치 35) · 위생 게이트 **exit 1 — 크기 455,619,980 > 400,000,000** |

### 우회 ① — 설치 전 차단을 지우면

계약은 「`--require-hashes` 를 지운다」로 적었는데 **그것만으로는 열리지 않는다**:

| 변이 | 결과 |
|---|---|
| runtime 의 `--require-hashes` 만 제거(목록은 해시를 싣는다) | `docker build` **exit 1** — pip 이 해시 검사 모드를 **암묵으로** 켠다(위 문서 행) |
| 목록을 `--no-hashes` 로 만들고 builder 의 비공허 검사까지 비낀다 | `docker build` **exit 0** — 설치 전 차단이 실제로 사라진다 |
| 그 이미지에 신설 게이트 | **exit 1** — `(나) pyyaml==6.0.3 이 … lock 의 해시 집합(11개)에 없다` |

이 세 줄이 (나)가 플래그의 재진술이 아님을 잰다 — 설치 전 차단을 **통째로** 지운 초록 빌드에서
결과가 붉는다.

## 설계 검토 실측

**(2)③ extra 를 넓히는 경로.** `serving` 과 `training` extra 의 lock 닫힘은 **8건으로 동일**하다
(`diff` 빈 출력). `dev` 로 넓히면 30건이 되고, 받는 것은 **위생 게이트의 크기 상한 하나**다(위
대조 표의 마지막 줄 — 금지 패키지 다섯은 DB·HTTP 계열이라 그 도구들을 열거하지 않는다).

**(2b) 값 획득 축.** 새 공개 표면 셋 —
① 게이트 스크립트 둘: 읽기 전용(`docker run --rm --network none --security-opt no-new-privileges`
질의 + 파일 판독). 세 질의가 `--network none` 에서 그대로 선다(이 라운드 실측) — 전부 로컬 판독이다.
② 이미지 안 `/opt/bidvector/pip-install-report.json`: **114,773 바이트** · root 소유 0644. 내용은
URL·sha256·판·패키지 메타데이터(공개 색인 정보). 참조형 스캔
`grep -rniE -f config/quality/leak-patterns.txt <보고서>` → **적중 1건**이고 그 자리는 `scipy 1.18.1` 의
`license` 필드, 즉 **상류 공개 라이선스 본문**이다(비밀값 0). 이 파일은 `leakPatternGate` 의 스캔
뿌리(`reports/evidence/`) 밖이라 그 게이트의 대상도 아니다.
③ builder 가 만든 해시 목록 둘: runtime 에 **남지 않는다**(설치 RUN 이 지운다).
크기 상한 여유 — 실측 `343,931,147` / 상한 `400,000,000`(**86%**, 여유 약 56MB). 정책 주석의 직전
기록 `343,765,264` 와의 차 `+165,883` 바이트는 보고서(112KB)와 `narwhals` 판 교체의 합이다(정책
파일은 in_scope 밖이라 그 주석의 숫자는 고치지 않았다).

## evidence 크기

추가 **바이트** 기준으로도 산출물 이하다(vr F-3). verifier 와 같은 엄격 분모(문면 정정 둘을 뺀 산출물
코드 넷 — Dockerfile·게이트 둘·ci.yml)로:

| | 줄 | 바이트 |
|---|---|---|
| evidence(`reports/evidence/m15/15t/`) | 465 | 41,620 |
| 산출물 코드 넷 | 851 | 49,920 |

앞 회차의 초과(evidence 36,451 > 산출물 35,516)는 분자·분모가 **둘 다** 작았던 자리다. 이 라운드는
중복 서술(제한 목록·계약 변경 서술)을 `scope.md` 한 자리로 옮겨 분자의 증가를 억제했고, 술어 추가로
분모가 늘었다.

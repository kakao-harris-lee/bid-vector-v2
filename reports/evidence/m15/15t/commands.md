# M15 / 15T — commands

base `afc3b9db6` · 브랜치 `chore/m15t-ml-serving-pip-hashes`

환경: WSL2 Ubuntu · bash · Docker 29.5.1 · host `uv 0.11.28`(쓰지 않았다 — 판정에 쓴 uv 는 **이미지가
핀한 0.9.22** 하나다. `ghcr.io/astral-sh/uv:0.9.22@sha256:2320e6c2…` 에서 뜯어 scratchpad 에서 실행) ·
베이스 `python:3.12.16-slim-bookworm@sha256:6dd8b6da…`(정책 다이제스트) · 이미지 안 `pip 25.0.1`.
운영 host·systemd·운영 컨테이너(`bidvector-v2-prod-*`)·실 DB/API/Telegram 에 닿는 명령은 하나도 없다.
실험 태그는 `bidvector/ml-serving:15t-ctl*` 로 띄우고 전부 지웠다 — `docker ps` 에서
`bidvector/ml-serving:local` 을 쓰는 컨테이너는 **0** 이었다(쓰는 `:local` 셋은 다른 프로젝트의 것).

## uv·pip 옵션 확정 — 문서와 실측

판정 근거는 **핀한 바이너리의 자기 문면**(`uv 0.9.22` 의 `--help`, 그 판에 대해 정본)과 상류 문서다.

| 옵션 | 근거 | 확정 |
|---|---|---|
| `uv export` 가 해시를 **기본으로** 싣는다 | `uv 0.9.22` `export --help`: `--no-hashes  Omit hashes in the generated output` | 플래그를 더하지 않는다 |
| `uv export --no-emit-project` | 같은 `--help`: `Do not emit the current project` | 프로젝트 자신을 목록에서 뺀다 |
| `uv export --locked` vs `--frozen` | docs.astral.sh/uv/concepts/projects/sync/ · reference/cli — `--locked` 「Requires that the lockfile is up-to-date. If the lockfile is missing or needs to be updated, uv will exit with an error」 / `--frozen` 「Instead of checking if the lockfile is up-to-date, uses the versions in the lockfile as the source of truth」 | **`--locked`**(아래 실측) |
| `uv build -b/--build-constraints` | `uv 0.9.22` `build --help`: 「Constrain build dependencies using the given requirements files when building distributions」 | lock 닫힘 전체를 준다 |
| `uv build --require-hashes` | 같은 `--help`: 「Require a matching hash for each requirement」 | 격리 빌드 환경에 해시 강제 |
| pip 해시 검사 모드 | pip.pypa.io/en/stable/topics/secure-installs/ — 「Specifying `--hash` against *any* requirement will activate this mode globally」 | `--require-hashes` 는 **목록이 비어도** 해시를 요구하게 하는 축이다(아래 우회 ① 실측의 뜻) |
| `pip --report` 스키마 | pip.pypa.io/en/stable/reference/installation-report/ — `version` 은 문자열 `1` 이고 「Tools must check this field」. `archive_info.hashes` 는 **source archive 가 옛 wheel 캐시에서 온 경우 없을 수 있다** | 판이 `1` 이 아니거나 해시 없는 항목이면 **정지**. `--no-cache-dir` + `--only-binary :all:` 이라 문서가 말하는 부재 경우에 닿지 않는다 |

### `--locked` 를 고른 실측 (D-15T-1)

`python:3.12.16-slim` + 핀한 uv + 최소 컨텍스트 사본에서(저장소 트리 무변경):

| 측정 | 결과 |
|---|---|
| 정상 lock · `--network none` · `--locked` | **exit 0** — 「Resolved 35 packages in 1ms」. 재해석이 없어 빌드에 네트워크 의존을 더하지 않는다 |
| `pyproject.toml` 의 `pyyaml` 판만 내린 사본 · `--locked` | **exit 2** — 끊긴다(그 경로에서만 재해석을 시도하므로 사유 문면은 네트워크로 나온다. 빌드 안에는 네트워크가 있다) |
| 같은 사본 · `--frozen` | **exit 0** — 어긋남을 보지 않는다 |
| 같은 사본 · `uv sync --frozen --all-extras --dry-run` | **exit 0** — lock 의 집합을 그대로 설치하겠다고 답한다 |

→ **`ml-engine` job 의 어느 step 도 pyproject↔lock 어긋남을 받지 않는다**(그 job 의 설치는 전부
`--frozen` 이다). 이 slice 의 builder stage 가 그것을 처음 끊는 자리다.

### `--no-dev` 는 이 프로젝트에서 거동 0

`dev` 는 dependency group 이 아니라 `[project.optional-dependencies]` 의 extra 다 — `--extra serving`
유무와 무관하게 `--no-dev` 를 붙인 산출과 안 붙인 산출이 **`cmp` 로 바이트까지 같다**. CI `ml-engine`
job 의 관용구를 그대로 둔다(새 축약을 만들지 않는다).

## RED — 구현 전 실측

게이트를 먼저 쓰고 **현 main 의 이미지**에 걸었다. `bidvector/ml-serving:rel-71b1ad3a7aca` 를 그 자리로
쓴 근거: `git diff --name-only 71b1ad3a7..afc3b9db6 -- docker/ml-serving.Dockerfile ml-engine/uv.lock
ml-engine/pyproject.toml` 이 **빈 출력**이다(세 입력이 base 와 같다).

| RED | 결과 |
|---|---|
| 신설 게이트를 그 이미지에 그대로 | **exit 2** — 「이미지 안 설치 보고서(…)를 읽지 못했다」. 보고서 생성이 없으면 통과가 아니라 정지다(우회 ⑤) |
| 그 이미지의 **설치 집합** ↔ lock 기준 보고서로 판독기만 | **exit 1** · 위반 2건 — `(가) narwhals==2.29.0 이 설치돼 있는데 보고서·프로젝트·base 어디에도 없다` / `(가) narwhals==2.26.0 이 … 설치돼 있지 않다` |

둘째 줄이 이 slice 가 닫는 것의 실체다: **출하 집합과 검사 집합이 실제로 갈려 있었다**(전이 의존
`narwhals` 가 lock 의 2.26.0 대신 2.29.0 으로 떠 있었다).

## acceptance

| 명령 | 결과 |
|---|---|
| `docker build -f docker/ml-serving.Dockerfile -t bidvector/ml-serving:local .` | **exit 0** — builder 가 목록 둘을 냈다(빌드 제약 고정 34·해시 247 / serving 고정 8·해시 63) · `pip check` 통과 |
| `./tools/image-hygiene-check.sh bidvector/ml-serving:local config/quality/image-hygiene-policy.properties` | **exit 0** — 금지-import 5건 · uid 10001 · base-layer 접두 일치 · `size_bytes=343,931,147` / `cap=400,000,000`(86%) |
| `./tools/image-pip-lock-check.sh bidvector/ml-serving:local config/quality/image-hygiene-policy.properties ml-engine/uv.lock /opt/bidvector/pip-install-report.json` | **exit 0** — lock-패키지 34 · 보고서 항목 8 · 해시 대조 8 · 설치 집합 10 · base 집합 1 · 프로젝트 `ml-engine==0.1.0` · 자기 변이 4건 전부 붉음 |
| `./tools/workflow-action-pins-check.sh .github/workflows` | **exit 0** — uses 24(핀 24) · 기대 파일 7줄과 집합 등식 · 자기 변이 18건 전부 붉음 |
| `./tools/workflow-step-refs-check.sh .github/workflows/ci.yml` | **exit 0** — job 5 · id 15 · 참조 18 · 매달린 참조 0(새 step 은 id 를 두지 않았다 — 가리키는 자리가 없는 id 는 죽은 설정이다) |
| `./gradlew --no-daemon check`(`~/.local/bin/bv-build.sh` 로 자리 획득) | **exit 0** — BUILD SUCCESSFUL |
| `bash -n tools/image-pip-lock-check.sh` / `python3 -m py_compile tools/image-pip-lock-check.py` | 둘 다 **exit 0** |
| `uv run ruff check ../tools/image-pip-lock-check.py` | exit 1(RUF002 ∪ 기호 2 · N818) — **이 저장소의 게이트가 아니다**. 선례 `tools/workflow-action-pins-parity.py` 도 같은 N818 과 `ruff format --check` exit 1 을 낸다(저장소 뿌리 `tools/*.py` 는 `ml-engine` 의 ruff 범위 밖) |

`tools/one-command-check.sh` 는 **고치지 않았다.** 계약의 조건(「그 파일이 container 축을 지면」)이
거짓이다 — 실측: 그 스크립트에 `docker build`·`image-hygiene-check` 가 0 건이고 지는 축은 `check`·
`ml-engine`·`web` 세 job 이다. container 축의 로컬 진입점은 오늘 없다.

## 게이트 계수와 자기 변이

| 축 | 계수 |
|---|---|
| lock 의 registry 패키지 | 34(전체 35 − 프로젝트 1) |
| 보고서 항목 | 8 — grpcio · lightgbm · narwhals · numpy · protobuf · pyyaml · scipy · typing-extensions |
| 해시 대조 | 8(항목마다 1회 · `compared == 0` 이면 「불일치 0 이 공허하게 참」이라 정지) |
| 설치 집합 | 10(보고서 8 + 프로젝트 1 + base 1) |
| base 집합 | 1 — `pip 25.0.1`. 손 목록이 아니라 정책 다이제스트 이미지에 **같은 질의**를 걸어 받는다 |

자기 변이 넷(매 실행, 임시 사본, 변이기가 「사본에 반영됐는가」를 스스로 비-0 으로 단언):

| 변이 | 기대 | 실측 |
|---|---|---|
| 보고서의 해시 하나를 다른 값으로 **바꿔치우기** | (나) 붉음 | 붉음 |
| 보고서에서 한 항목 제거 | (가) 붉음(설치돼 있는데 보고서에 없다) | 붉음 |
| lock 사본에서 한 패키지의 해시 제거 | (나) 붉음(대조 집합이 빈다) | 붉음 |
| 설치 집합 사본에서 한 줄 제거 | (가) 붉음(보고서에 있는데 설치돼 있지 않다) | 붉음 |

넷째는 계약의 셋에 **더한** 것이다 — 앞 셋은 모두 「보고서·lock 이 틀렸다」 방향이고, 이미지 질의가
조용히 줄어드는 축은 받지 못했다. 비공허 하한(네 모집단 ≥ 1)이 완전 공백을 받고, 이 변이가 부분 감소를
받는다.

## 빌드 층 일회 대조 (D-15T-5)

매 CI 에서 실패 빌드를 돌리지 않는다(제한 1). **저장소 밖 임시 컨텍스트 사본**에서 재고 사본을 지웠다 —
저장소 트리는 한 번도 변이하지 않았다(`git status --porcelain` 빈 출력로 확인). 변이는 `cmp` 로 적용을
먼저 확인하고, 해시는 그 패키지의 **집합 전체**를 갈았다.

| 대조 | 결과 |
|---|---|
| 사본 그대로(대조군) | `docker build` **exit 0** |
| lock 사본의 `pyyaml` 해시 11건 전부 바꿔치움 → serving 목록 | `docker build` **exit 1** — 설치 step 이 「THESE PACKAGES DO NOT MATCH THE HASHES FROM THE REQUIREMENTS FILE」로 끊긴다(설치 **전**) |
| lock 사본의 `setuptools` 해시 2건 전부 바꿔치움 → 빌드 제약 | `docker build` **exit 1** — `uv build` step 이 exit 2 「Hash mismatch for `setuptools==84.0.0`」 |
| **해시 하나만** 바꿔치움(앞 라운드) | `uv build` **exit 0** — 그 패키지의 다른 해시(sdist/wheel)가 아직 맞는다. 변이를 넣는 사람은 집합 전체를 갈아야 한다 |

### 우회 ① — 설치 전 차단을 지우면

계약은 「`--require-hashes` 를 지운다」로 적었는데 **그것만으로는 열리지 않는다**(실측):

| 변이 | 결과 |
|---|---|
| runtime 의 `--require-hashes` 만 제거(목록은 해시를 싣는다) | `docker build` **exit 1** — pip 이 해시 검사 모드를 **암묵으로** 켠다(문서: 「`--hash` against *any* requirement will activate this mode globally」) |
| 목록을 `--no-hashes` 로 만들고 builder 의 비공허 검사까지 비낀다 | `docker build` **exit 0** — 설치 전 차단이 실제로 사라진다 |
| 그 이미지에 신설 게이트 | **exit 1** — `(나) pyyaml==6.0.3 이 실제로 내려받은 바이트의 sha256 이 lock 의 해시 집합(11개)에 없다` |

이 세 줄이 (나)가 플래그의 재진술이 아님을 잰다 — 설치 전 차단을 **통째로** 지운 초록 빌드에서 결과가
붉는다.

## 설계 검토 실측

**(2)③ `--extra training`·`--extra dev` 를 기존 위생 게이트가 받는가.** `serving` 과 `training` extra 의
lock 닫힘은 **8건으로 동일**하다(`diff` 빈 출력) — `training` 으로 넓히는 것은 오늘 거동 0 이다. `dev` 로
넓히면 30건이 되고 더해지는 26건은 `grpcio-tools`·`pytest`·`mypy`·`ruff`·`setuptools` 등 도구다. 그 이름들은
`config/quality/image-hygiene-policy.properties` 의 금지 패키지 다섯(DB·HTTP 계열)에 **없다** — 위생 게이트도,
두 등식도 받지 못한다(lock 안의 것이므로). 상시 축으로 남는 것은 크기 상한 하나뿐이다(여유 56MB).
알려진 제한 4 로 등재한다.

**(2b) 값 획득 축.** 새 공개 표면 셋 —
① 게이트 스크립트 둘: 읽기 전용이다(`docker run` 질의 + 파일 판독). 이미지를 바꾸지 않는다.
② 이미지 안 설치 보고서 `/opt/bidvector/pip-install-report.json`: **114,773 바이트** · root 소유 0644.
내용은 URL·sha256·판·패키지 메타데이터(전부 공개 색인 정보). 참조형 스캔
`grep -rniE -f config/quality/leak-patterns.txt <보고서>` → **적중 1건**이고 그 자리는 `scipy 1.18.1` 의
`license` 필드, 즉 **상류 공개 라이선스 본문**이다(비밀값 0). 이 파일은 `leakPatternGate` 의 스캔 뿌리
(`reports/evidence/`) 밖이라 그 게이트의 대상도 아니다.
③ builder 가 만든 해시 목록 둘: runtime 에 **남지 않는다**(설치 RUN 이 지운다).
크기 상한 여유 — 실측 `343,931,147` / 상한 `400,000,000`(**86%**, 여유 약 56MB). 정책 주석의 직전 기록
`343,765,264` 와의 차 `+165,883` 바이트는 보고서(112KB)와 `narwhals` 판 교체의 합이다. 상한에는 닿지
않는다(정책 파일은 in_scope 밖이라 그 주석의 숫자는 고치지 않았다).

## 알려진 제한 (착수 목록 1~3 에 더하는 것)

4. **extra 를 `dev` 로 넓히는 경로는 두 등식과 위생 게이트 모두 초록이다**(위 (2)③ 실측). lock 안의
   것이면 바이트도 집합도 맞기 때문이다. 상시로 남는 축은 이미지 크기 상한 하나다.
5. `(가)`의 base 모집단과 `(2)` 위생 게이트의 base layer 파생은 **같은 정책 두 키**를 읽지만 서로 다른
   방법으로 쓴다(이쪽은 index 다이제스트로 `docker run`, 저쪽은 `imagetools inspect` 로 platform
   manifest). 레지스트리 접근이 없으면 **둘 다** 판정 불가다.
6. 보고서 경로는 Dockerfile 과 CI step 인자 **두 자리**에 산다. 어긋나면 fail-closed(exit 2)지만 한
   자리는 아니다 — 정책 키로 옮기려면 `config/quality/image-hygiene-policy.properties`(in_scope 밖)를
   건드려야 해서 이 slice 에서는 하지 않았다.
7. `lightgbm` 은 이 이미지에서 **import 되지 않는다**(`libgomp.so.1` 부재) — base 에도 없고 runtime
   RUN 이 올리지 않는다. **이 slice 전에도 같았다**(출하 `rel-71b1ad3a7aca` 에서 같은 실패를 실측) —
   이 변경이 만든 것도, 고친 것도 아니다. 별건으로 보고한다.

## 계약과 달라진 점 · 계약 갱신

- 「설치되는 집합의 거동 변화 0 이 목표다」(out_of_scope 둘째 줄)는 **달성되지 않았고, 될 수 없다.**
  `narwhals` 가 2.29.0 → 2.26.0(lock 의 값)으로 내려간다. 그것이 이 slice 가 닫는 바로 그 갈림이다 —
  lock 은 CI `ml-engine` job 이 test 하는 집합이므로 방향은 「출하가 검사받은 집합으로」다. `uv.lock` ·
  `pyproject.toml` 의 **내용**은 손대지 않았다(out_of_scope 의 그 절은 지켰다).
- 우회 ①의 문면을 실측으로 좁혔다(위 「우회 ①」 절) — 플래그 제거만으로는 열리지 않는다.
- D-15T-5 의 변이 셋에 **넷째**(설치 집합 감소)를 더했다.
- `tools/one-command-check.sh` · `config/quality/shell-guard-policy.properties` · `.dockerignore` 는
  in_scope 의 조건이 전부 거짓이어서 **건드리지 않았다**(조건: container 축 보유 · census 등재 ·
  컨텍스트 제외. 셋 다 실측으로 거짓 — `.dockerignore` 는 이 저장소에 없고 `uv.lock` 이 컨텍스트에 든다).
- 새 파일 둘(`tools/image-pip-lock-check.sh`·`.py`)은 in_scope 에 이름까지 그대로 있다. 등재형 게이트
  (`gate-tests.properties` · `shell-guard-policy` census · Kotlin task 입력)에 걸리는 자리는 없다 —
  `tools/` 를 훑는 Gradle task 가 0 건임을 실측했다.

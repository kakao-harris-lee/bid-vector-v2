# M6/6B-4 실행 명령과 종료 코드

정본: `scope.md`. 판정 SHA(산출물 마지막) `e118ced7`. 명령 앞뒤로 HEAD 를 단언하고, 측정
중에는 커밋하지 않는다. 출력 전문은 싣지 않는다(핵심 결과 한 줄) — 감사자는 명령을 다시 돈다.

**마지막 HEAD 의 게이트 결과 정본은 이 파일이 아니다** — evidence 는 자기 마지막 커밋의
post-state 를 담을 수 없다. 그 자리는 verifier 리포트와 PR 조치 코멘트다.

## acceptance — `ml-engine` job 전건 (`.github/workflows/ci.yml`)

양끝 HEAD `e118ced7` 동일. `working-directory: ml-engine`.

## 2026-10-14T (판정 SHA e118ced7)
- cmd: `uv sync --frozen --all-extras` (S-1)
- exit: 0
- 핵심 결과: lock 그대로 해소(34 packages)

## 2026-10-14T
- cmd: S-1b serving extras 분리 — `uv sync --frozen --extra serving --no-dev` + 금지 패키지 다섯 import 시도 + dev 복구
- exit: 0
- 핵심 결과: 금지 패키지 전부 import 불가

## 2026-10-14T
- cmd: `uv run ruff check .` + `uv run ruff format --check .` (S-2)
- exit: 0
- 핵심 결과: 228 files already formatted

## 2026-10-14T
- cmd: `uv run mypy --strict src/ml_engine` (S-3)
- exit: 0
- 핵심 결과: 99 source files, no issues(저널 모듈 분리로 하나 늘었다)

## 2026-10-14T
- cmd: `uv run lint-imports` (S-4)
- exit: 0
- 핵심 결과: Contracts 8 kept, 0 broken

## 2026-10-14T
- cmd: `uv run python -m pytest tests -q` (S-5)
- exit: 0
- 핵심 결과: **1563 passed**(338s) — r1 수정이 46 개를 더했다

## 2026-10-14T
- cmd: `uv run python tools/design_ratchet.py --check` (S-6)
- exit: 0
- 핵심 결과: allowlist 밖 위반 0

## 2026-10-14T
- cmd: S-7 재활용 출처 두 자리 대조 + 양성 대조(어긋난 evidence 가 실패해야 한다)
- exit: 0
- 핵심 결과: 본 검사 위반 0 · 양성 대조가 기대대로 불일치 29건을 내며 실패

## 2026-10-14T
- cmd: S-9 Python 버전 두 자리 대조
- exit: 0
- 핵심 결과: `.python-version` 과 `requires-python` 둘 다 3.12

## 2026-10-14T
- cmd: `uv build --wheel -o /tmp/ml-engine-wheel` + `pytest tests/gates/test_wheel_reexport.py -q` (S-11)
- exit: 0
- 핵심 결과: 1 passed — 설치본에서 재수출 성립

## acceptance — `check` job

## 2026-10-14T
- cmd: `./gradlew --no-daemon check --rerun-tasks`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL in 10m 51s (350 tasks executed). proto 주석·Kotlin 계약 test 를
  편집했으므로 `--rerun-tasks` 로 돌렸다

## 2026-10-14T
- cmd: `./gradlew --no-daemon qualityBaseline`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL in 6s (측정, 게이트 아님)

### 수정 전 실패 이력(검증이 실제로 작동한 증거)

## 2026-10-14T
- cmd: `./gradlew --no-daemon check --rerun-tasks` (Kotlin 5x5 진리표 test 를 **새 파일**로 둔 판)
- exit: 1
- 핵심 결과: 두 게이트가 차례로 잡았다 — ① `:adapters:sizeGate` 「파일 500 줄 한도 초과 1건
  (517 줄)」 ② 분리 뒤 `GateRegistrationGateTask` 「게이트 등재 장부에 없는 test 클래스다」.
  ②의 장부(`config/quality/gate-tests.properties`)는 이 slice in_scope 밖이라 계약을 넓히지
  않고, 진리표가 열거를 **흡수**하도록 바꿔 495줄로 줄였다(새 클래스·장부 편집 0)

## 2026-10-14T
- cmd: `uv run python tools/design_ratchet.py --check` (저널 모듈 분리 전)
- exit: 1
- 핵심 결과: `file_loc_band: training/jobs/journal.py = 21`(515줄) — allowlist 등재 대신 파일
  접근을 `journal_file.py` 로 떼어내 418 + 116 줄로 내렸다

## 2026-10-14T
- cmd: `uv run ruff check src tests` (닫힌 세계 게이트 작성 직후)
- exit: 1
- 핵심 결과: RUF002/003 모호 문자 셋 — 고쳐 통과. **앞 커밋에서 `| tail -2` 로 종료 코드를
  가린 채 커밋한 사실**을 이 자리에 남긴다(「게이트 결과는 종료 코드로」 규율 위반, 별도
  커밋으로 정정)

## RED 선행 실측 (TDD)

| finding | RED 로 측정한 것 | 결과 |
| --- | --- | --- |
| cr H-1 | 동기 START runner + 붙잡힌 파이프라인에서 handle 단언 | `assert 2 == 1`(RUNNING) 둘 |
| vr H-3 | 폐기 뒤 caplog 레코드 수 | `assert 0 == 1` |
| cr M-5 · vr L-3 | 전수 사상 · 「호출자가 고를 수 없다」 · detail_code 빈 값 | 셋 fail(서명·인자) |
| cr M-2 | tz 없는 스냅숏 쓰기 | `JournalWritten(byte_count=439)` 로 통과(= 못 읽는 바이트를 썼다) |
| cr M-2 곁가지 | naive 시각으로 저장소 입구 호출 | `TypeError: can't compare offset-naive and offset-aware datetimes` |
| vr M-1 | 디렉터리 `fsync` 실패 주입 | 저널 층 `durable` 속성 부재 2 · 저장소 층 `JournalFailed(WRITE_FAILED)` 1 |
| vr H-2 · cr M-4 | 새 조립 경계 게이트 전건 | 6 fail(생성 자리 해석 · import 집합 · 파일계 집합 · `path` 표면 · 뿌리 재수출 · 공허함 대조) |
| vr H-1 | 조립 근으로 띄운 서버에서 상한+1 대기 | 새 test 가 조립 근 배선을 지난다(아래 V8 변이가 그 검출력) |
| cr L-1 · vr L-4 | 처분의 저널 쓰기 실패 주입 | 「영속」 문구 부재 |
| cr L-5 | 빈 `dataset.dataset_id` | `ValueError: dataset_id 는 비어 있을 수 없습니다.`(핸들러 이탈) |

## 비밀값 어휘 스캔

패턴은 파일을 참조해 돌린다(어휘를 이 문서에 축어로 적지 않는다). 대상은 slice 산출물 경로와
이 evidence 디렉터리.

## 2026-10-14T
- cmd: `grep -rniE -f config/quality/leak-patterns.txt reports/evidence/m6/6b4/`
- exit: 1
- 핵심 결과: 매치 없음 = 통과

## 2026-10-14T
- cmd: `grep -rniE -f config/quality/leak-patterns.txt <산출물 경로들>`
- exit: 0
- 핵심 결과: 매치는 전부 기존 어휘 계열(취소 신호 타입 이름·compose 의 `${VAR:?}` 참조)이고 새
  비밀값은 0. **구속력 있는 판정은 이 grep 이 아니라 `check` 안의 `leakPatternGate`** 이고(그
  scanRoot 는 `reports/evidence`), 위 `check` exit 0 이 그 결과다.

## clean-tree 게이트

## 2026-10-14T
- cmd: `git status --porcelain -- <in_scope 경로 개별 인자>`
- exit: 0
- 핵심 결과: 빈 출력

## 2026-10-14T
- cmd: 양성 대조 — in_scope 파일 하나에 줄을 덧붙인 뒤 같은 명령, 그 뒤 **비파괴 절삭**으로 복원
  (`checkout --` 쓰지 않는다)
- exit: 0
- 핵심 결과: 해당 파일이 ` M` 으로 잡힘 → 복원 뒤 다시 빈 출력

## 변이 실측 — 바꿔치우기(더하기 아님), 적용은 `git diff --numstat` 으로 확인

전부 커밋된 트리 위에서 심고 `git checkout -- .` 로 되돌렸으며, 매 변이 뒤 `git status
--porcelain` 빈 출력을 확인했다.

### 열거 술어로도 닫히던 자리(요약)

| # | 변이 | numstat | RED |
| --- | --- | --- | --- |
| M-1 | 정책 값 `pending_queue_max` 4 → **3** | 1/1 | 2 — 승인 값 · 값이 폐기 시점에 닿는지 |
| M-2 | 같은 값 4 → **5** | 1/1 | 같은 2 |
| M-3 | 상한 판정 off-by-one | 1/1 | 8 |
| M-4~M-6 | Python 표 · Kotlin 허용표 · proto 표 행에서 간선 제거 | 각 0/1 | 5 · 4 · 3 — 교차 등식 |
| M-7 | 저널 쓰기 **전에** 메모리 커밋 | 1/1 | 1 |
| M-8 | 원자 교체를 직접 쓰기로 | 1/3 | 4 |
| M-9 | 깨진 저널을 「없음」으로 | 2/2 | 6 |
| M-10 | `START` 를 다시 `submit()` 안으로 | 1/0 | 10 |
| M-11 | `ReadinessGate` 에 다른 이름으로 READY 복귀 | 4/0 | 1 |

### 이름 술어에서는 **초록이던** 우회 — 구조 술어로 바꾼 뒤 RED

| # | 변이(괄호는 **이름·리터럴 술어** 아래의 결과) | numstat | 구조 술어에서 RED 가 되는 test |
| --- | --- | --- | --- |
| V8 | 조립 근의 `pending_queue_max=serving_policy.…` → 상수 `1000` (**저장소를 직접 만드는 test 아래: 전건 초록**) | 1/1 | 1 — 조립 근 운반 test |
| G1 | `servicer.py` 에 `JobJournal as _J` 별칭 생성 (**초록**) | 3/0 | 2 — 생성 자리 해석 · import 집합 |
| G2 | `runner.py` 에 `Path.replace` 둘째 writer (**초록**) | 3/0 | 1 — 파일계 집합 |
| G3 | `runner.py` 에 `from os import replace as _swap` (**초록**) | 4/0 | 1 — 파일계 집합 |
| G4 | `runner.py` 에 `JobStore as _S` 로 상한 10^9 저장소 생성 (**초록**) | 2/0 | 1 — 생성 자리 해석 |
| G5 | 조립 근의 경로를 모듈 상수(절대 경로) 경유로 (**리터럴 술어 초록**) | 2/1 | 1 — 값 축(설정 뿌리·조각) |
| G6 | 패키지 뿌리에 `JobJournal` 재수출 되살림(둘째 획득 경로) | 1/0 | 1 — 뿌리 재수출 집합 |
| G7 | `JobJournal.path` property 되살림 | 4/0 | 1 — 공개 표면 집합 등식 |
| P1 | Kotlin 허용표에 `Pair(CANCELLED, RUNNING)` (**초록**) | 1/0 | 2 — 언급 수 등식 · 그 음성 대조 |
| P1b | `isAllowedTransition` 에 `\|\| from == FAILED` 절 추가 (**초록**) | 1/1 | 1 — 술어 본문 고정(+ Kotlin 5x5 진리표) |
| P2 | proto 주석에 들여쓰기 둘짜리 `//  FAILED → RUNNING` (**초록**) | 1/0 | 4 — 통치 구역 닫힌 세계 + 음성 대조 |
| P2b | proto 표 들여쓰기 산문 `(FAILED → RUNNING)` (**초록**) | 1/0 | 4 — 같은 자리 |
| P3 | ADR D-8 에 백틱 없는 `FAILED → RUNNING` 산문 (**초록**) | 1/1 | 2 — 백틱 밖 화살표 계수 + 음성 대조 |
| V-M3 | `_fill_store_unavailable` 두 분기 제거 | 2/4 | 2 — `JOB_STORE_UNAVAILABLE` wire test |

G1~G7 은 각각 **의도한 게이트 하나**에서 떨어졌다(import 오류가 아니다 — FAILED 목록을 확인).
P2·P2b 에서는 음성 대조 test 들도 함께 붉어진다: 그 test 가 **이미 변이된 문면 위에** 또
심으므로 측정 목록이 둘이 된다(기준 문면이 바뀐 것을 알아차린 것이고, 1차 술어
`test_every_arrow_in_the_governed_proto_block_is_parsed` 가 먼저 떨어진다).

## rollback 실측

`rollback.md` 참조(실측 HEAD `e118ced7` · ①~⑥ 결과를 그 파일이 갖는다).

## acceptance — `container` job (S-21 ~ S-25, ci.yml 의 run step 순서대로)

run 블록은 **파일에서 그대로 꺼내** 순서대로 실행했다(손으로 옮겨 적지 않는다) — ci.yml 을
파싱해 `container` job 의 step 을 차례로 돌리고, 러너의 `$GITHUB_ENV`·`$GITHUB_PATH`·
`$RUNNER_TEMP` 를 파일·디렉터리로 흉내 냈다. `uses:` step 셋(checkout·setup-java·
setup-gradle)은 로컬 툴체인이 이미 그 자리라 건너뛴다.

**이 job 은 재사용하지 않고 전부 다시 돌렸다** — `contracts/proto` 와 `ml-engine/src` 를 편집했고 둘 다 Dockerfile 의 입력이다.

**16 step 전부 exit 0**(요약 — 아래 목록이 실행 순서다):

| # | step | exit |
| --- | --- | --- |
| 1·2 | DB 접속 값 · 운영자 자격 값 생성(일회성) | 0 |
| S-21 | `docker build -f docker/ml-serving.Dockerfile` | 0 |
| S-21a | `./gradlew --no-daemon :app:bootJar` | 0 |
| S-21b | `docker build -f docker/app.Dockerfile` | 0 |
| S-22a·S-22b | 이미지 위생 게이트 둘(ml-serving · app) | 0 |
| S-22c | 앱 이미지 거부 스모크(운반 이름공간 조기 거부) | 0 |
| — | install trivy(정책 파일의 핀 체크섬으로 받아 `sha256sum --strict -c`) | 0 |
| S-22d·S-22e | 이미지 취약점 게이트 둘 | 0 |
| S-22f | SBOM 보관(CycloneDX, 이미지 둘) | 0 |
| S-23 | `docker compose up -d` + healthy 수렴(app·ml-serving·postgres) | 0 |
| S-23b | 컨테이너 스모크(준비 상태·인증 경계·health 격리·수집 꺼짐) | 0 |
| S-23c | 백업·복원·되돌림 리허설 | 0 |
| **S-24** | **실 Kotlin gateway ↔ 컨테이너의 실 Python 서버**(`RealServerIntegrationTest`) | 0 |
| S-25 | `docker compose down -v` | 0 |

S-24 는 **이 문서에 항목이 빠져 있던 자리**다(vr §4) — 목록을 손으로 적는 대신 ci.yml 의 step
목록 자체를 돌리므로 빠질 자리가 없다.

### Q-6 전용 실측 — 컨테이너 재생성 뒤 저널 생존 (wire 를 지나서)

아래 표는 실측 HEAD `54bbef81` 에서의 측정을 그대로 잇는다. 이 라운드는 `docker/**` 를 편집하지
않았고 저널의 **자리·형식·볼륨 배선**도 바꾸지 않았다(바뀐 것은 모듈 분할·쓰기 결과 타입·
게이트다). 위 S-23~S-25 가 그 배선 위에서 다시 exit 0 이다.

| 단계 | 실측 |
| --- | --- |
| 기동 직후 볼륨 안 | `/app/artifacts/_journal/jobs.json` 존재 · 소유자 `mlserving:mlserving` · 모드 `0600` · 내용은 `jobs` 빈 배열 |
| 기동 로그 | 처분 계수 세 줄값 `0 · 0 · 0` + `readiness=READY` |
| `StartTraining` 한 건(존재하지 않는 dataset) | handle 반환 → 폴링에서 `JOB_STATE_FAILED` / `DATASET_UNREADABLE` / `NOT_FOUND` — 저널에 그 기록이 남음 |
| `docker compose rm -sf ml-serving` → `up -d ml-serving` | 컨테이너 **id 가 바뀜**(진짜 재생성) · healthy 재수렴 |
| 재생성 뒤 같은 `job_id` 조회 | **같은 상태·실패 코드·세부 사유** 반환 |
| 재생성 뒤 처분 계수 | `0 · 0 · 0`(종단 job 이라 더 처분할 것이 없다 — 수렴) |
| 멱등 키 보존 | 같은 멱등 키 재요청이 **같은 `job_id`** 를 돌려줌(새 job 0) |
| **통제군** — `down -v` 로 볼륨을 지우고 다시 띄움 | 저널의 job 수 **1 → 0**. 생존을 만든 것이 볼륨임을 보인다(이미지도 코드도 그대로다) |

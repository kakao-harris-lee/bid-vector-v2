# M6/6B-4 실행 명령과 종료 코드

정본: `scope.md`. 판정 SHA(산출물 마지막) `e118ced7`. 명령 앞뒤로 HEAD 를 단언하고, 측정
중에는 커밋하지 않는다. 출력 전문은 싣지 않는다(핵심 결과 한 줄) — 감사자는 명령을 다시 돈다.

**마지막 HEAD 의 게이트 결과 정본은 이 파일이 아니다** — evidence 는 자기 마지막 커밋의
post-state 를 담을 수 없다. 그 자리는 verifier 리포트와 PR 조치 코멘트다.

## acceptance — `ml-engine` job 전건 (`.github/workflows/ci.yml`)

양끝 HEAD 동일. `working-directory: ml-engine`.

## 2026-10-14T
- cmd: `uv sync --frozen --all-extras` (S-1)
- exit: 0
- 핵심 결과: lock 그대로 해소(34 packages)

## 2026-10-14T
- cmd: S-1b serving extras 분리 + 금지 패키지 다섯 import 시도 + dev 복구
- exit: 0
- 핵심 결과: 금지 패키지 전부 import 불가

## 2026-10-14T
- cmd: `uv run ruff check .` + `uv run ruff format --check .` (S-2)
- exit: 0
- 핵심 결과: 230 files already formatted

## 2026-10-14T
- cmd: `uv run mypy --strict src/ml_engine` (S-3)
- exit: 0
- 핵심 결과: 99 source files, no issues

## 2026-10-14T
- cmd: `uv run lint-imports` (S-4)
- exit: 0
- 핵심 결과: Contracts 8 kept, 0 broken

## 2026-10-14T
- cmd: `uv run python -m pytest tests -q` (S-5)
- exit: 0
- 핵심 결과: **1599 passed**(369s). 감사 훅이 세션 내내 켜져 있는 비용은 **A/B 대조로 확인**
  됐다 — 훅 설치 한 줄만 주석 처리하면 `tests/app` 269 test 가 331.3초, 그대로 두면 384.0초
  (+15.9%, code-review r3 §4). 「닫는 것은 실행된 경로」의 대가다(checklist 판단 14)

## 2026-10-14T
- cmd: `uv run python tools/design_ratchet.py --check` (S-6)
- exit: 0
- 핵심 결과: allowlist 밖 위반 0

## 2026-10-14T
- cmd: S-7 재활용 출처 두 자리 대조 + 양성 대조
- exit: 0
- 핵심 결과: 위반 0 · 양성 대조가 기대대로 실패

## 2026-10-14T
- cmd: S-9 Python 버전 두 자리 대조
- exit: 0
- 핵심 결과: 둘 다 3.12

## 2026-10-14T
- cmd: `uv build --wheel` + `pytest tests/gates/test_wheel_reexport.py -q` (S-11)
- exit: 0
- 핵심 결과: 1 passed — 설치본에서 재수출 성립

## acceptance — `check` job

**승인 전 일괄 배치(이 문서의 마지막 갱신)에서는 다시 돌리지 않았다** — 그 배치가 바꾼 것은
`ml-engine/tests/**` 와 이 evidence 뿐이고 **Kotlin·proto 변경이 0** 이다(기계 확인:
`git diff --name-only <계약 r4>..HEAD | grep -cE '\.(kt|proto)$'` → 0). 아래 값은 산출물
판정 SHA 직전의 실측이고, 그 뒤 Kotlin 입력은 한 글자도 바뀌지 않았다.

## 2026-10-14T
- cmd: `./gradlew --no-daemon check --rerun-tasks`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL in 10m 36s (350 tasks executed). proto 주석·ADR·Kotlin 계약
  test 를 편집했으므로 `--rerun-tasks`. Kotlin 이 ADR 렌더링 블록을 읽어 5x5 진리표를 대조하는
  경로가 컴파일·통과한다

## 2026-10-14T
- cmd: `./gradlew --no-daemon qualityBaseline --rerun-tasks`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL in 22s (측정, 게이트 아님)

### 수정 전 실패 이력(검증이 실제로 작동한 증거)

## 2026-10-14T
- cmd: `./gradlew --no-daemon check --rerun-tasks` (Kotlin 헬퍼의 체인 줄바꿈 전)
- exit: 1
- 핵심 결과: `:adapters:ktlintTestSourceSetCheck` 가 `standard:chain-method-continuation` 둘을
  잡았다 — 고친 뒤 통과

## 2026-10-14T
- cmd: `uv run python tools/design_ratchet.py --check` (cr M-3 가드를 `transition()` 안에 둔 판)
- exit: 1
- 핵심 결과: `functions_over_soft_limit: state.py = 1`(함수 50줄) — 사유 축 거부를
  `_failure_reason_rejection` 헬퍼로 빼내 통과

## 2026-10-14T
- cmd: 세션 전역 감사 단언을 `pytest_sessionfinish` 로 두고 위반을 주입
- exit: **0**
- 핵심 결과: 메시지는 찍히는데 **종료 코드가 0** 이다(「게이트 결과는 종료 코드로」 규율 위반).
  수집 순서를 바꿔 전역 단언을 맨 뒤 test 로 돌린 뒤 같은 주입에서 **exit 1** 을 확인했다

## acceptance — `container` job (S-21 ~ S-25)

run 블록은 ci.yml 을 파싱해 **파일에서 그대로 꺼내** 순서대로 실행했다(`uses:` step 넷은
로컬 툴체인이 그 자리라 건너뜀, `COMPOSE_PROJECT_NAME=m6b4r2`). 재사용하지 않았다 —
`contracts/proto`·`ml-engine/src` 를 편집했고 둘 다 Dockerfile 입력이다.

## 2026-10-14T
- cmd: `container` job 16 run step(S-21 · S-21a · S-21b · S-22a · S-22b · S-22c · install trivy ·
  S-22d · S-22e · S-22f · S-23 · S-23b · S-23c · **S-24** · S-25 + 일회성 자격 값 둘)
- exit: **16 step 전부 0**
- 핵심 결과: 이미지 둘 빌드 · 위생·취약점 게이트 넷 · compose 세 서비스 healthy 수렴 ·
  컨테이너 스모크 · 백업·복원 리허설 · 실 Kotlin gateway ↔ 컨테이너 실 Python 서버(S-24) ·
  `down -v` 뒤 컨테이너·볼륨 0

### Q-6 전용 실측 — 컨테이너 재생성 뒤 저널 생존

아래 표는 실측 HEAD `54bbef81` 에서의 측정을 그대로 잇는다. 이 라운드는 `docker/**` 를 편집하지
않았고 저널의 **자리·형식·볼륨 배선**도 바꾸지 않았다(바뀐 것은 판독 관용도·사유 집합·게이트다).
위 S-23~S-25 가 그 배선 위에서 다시 exit 0 이다.

| 단계 | 실측 |
| --- | --- |
| 기동 직후 볼륨 안 | `/app/artifacts/_journal/jobs.json` 존재 · 소유자 `mlserving:mlserving` · 모드 `0600` · 내용은 `jobs` 빈 배열 |
| 기동 로그 | 처분 계수 세 줄값 `0 · 0 · 0` + `readiness=READY` |
| `StartTraining` 한 건(존재하지 않는 dataset) | handle 반환 → 폴링에서 `JOB_STATE_FAILED` / `DATASET_UNREADABLE` / `NOT_FOUND` |
| `docker compose rm -sf ml-serving` → `up -d ml-serving` | 컨테이너 **id 가 바뀜**(진짜 재생성) · healthy 재수렴 |
| 재생성 뒤 같은 `job_id` 조회 | **같은 상태·실패 코드·세부 사유** 반환 |
| 멱등 키 보존 | 같은 멱등 키 재요청이 **같은 `job_id`** 를 돌려줌(새 job 0) |
| **통제군** — `down -v` 로 볼륨을 지우고 다시 띄움 | 저널의 job 수 **1 → 0**. 생존을 만든 것이 볼륨임을 보인다 |

## RED 선행 실측 (TDD)

| finding | RED 로 측정한 것 | 결과 |
| --- | --- | --- |
| cr M-2 | 사본 뒤집기 · 구판 저널 기동 | `JournalFailed(INVARIANT_VIOLATED, '… retryable 이 code 의 사상과 어긋난다')` 둘 |
| cr M-3 | 사유 집합 import | 선언 데이터 부재로 collection error |
| cr M-4 | 멱등 재사용 handle 상태 | (거동은 선재 — 문면과 test 가 없었다. 아래 변이로 검출력 확인) |
| vr H-1 | 상대 import·모듈 앨리어스·재수출 경유 | 아래 A1·A2·A6 |
| vr H-2 · cr M-1 | 여섯 갈래 둘째 writer | 아래 F1~F6·F7 |
| vr M-1 | 글리프·배치·Kotlin 데이터 | 아래 PR·AD·K |

## 비밀값 어휘 스캔

패턴은 파일을 참조해 돌린다(어휘를 이 문서에 축어로 적지 않는다).

## 2026-10-14T
- cmd: `grep -rniE -f config/quality/leak-patterns.txt reports/evidence/m6/6b4/`
- exit: 1
- 핵심 결과: 매치 없음 = 통과

## clean-tree 게이트

## 2026-10-14T
- cmd: `git status --porcelain -- <in_scope 경로 개별 인자>`
- exit: 0
- 핵심 결과: 빈 출력. 측정 중 남은 산출물 없음(감사 게이트 초안이 만든 `ml-engine/build/_journal`
  디렉터리는 gitignore 대상이었지만 치웠다)

## 변이 실측 — 바꿔치우기, 적용은 `git diff --numstat` 으로 확인

전부 **커밋된 트리** 위에서 심고 `git checkout -- <경로>` 로 되돌렸으며, 매 변이 뒤
`git status --porcelain` 빈 출력을 확인했다(미커밋 산출물을 함께 지우지 않도록 **변이 전에
커밋**한다 — 이 라운드에 한 번 어겨 cr M-3 작업을 잃고 다시 썼다).

### vr H-1 — 조립 경계: 손 해석기에서 grimp 그래프로

| # | 심은 것(production) | numstat | 앞 판 | 이 판 |
| --- | --- | --- | --- | --- |
| A1 | `servicer.py` 에 `from .journal_file import JobJournal as RogueJournal` + 생성 | 9/0 | **초록**(전 acceptance) | **RED 2** — import 집합 · 이름 바인딩 집합 |
| A2 | `from ml_engine.training.jobs import journal_file as _jf` + `_jf.JobJournal(...)` | 9/0 | RED 1 | **RED 2** — 생성 자리 · import 집합 |
| A6 | `from …store import JobJournal as _J`(재수출 한 홉, `# type: ignore` 동반) | 9/0 | mypy 만 막았다 | **RED 1** — 이름 바인딩 집합(mypy exit 0 확인) |
| A3 | `factory = JobStore; factory(...)`(지역 재바인딩) | 6/0 | 초록 | **초록 — 알려진 제한으로 등재**(checklist 경계 밖 표) |

### vr H-2 · cr M-1 — 둘째 writer: 이름 목록에서 감사 훅으로

| # | 쓰는 방식 | 앞 판 | 이 판 |
| --- | --- | --- | --- |
| F1 | builtin `open(<저널 경로>, "w")` | **초록** | **포착** |
| F2 | `(<경로>).open("w")` | **초록** | **포착** |
| F3 | `Path.replace(staged, <경로>)`(비결합, 위치 둘) | **초록** | **포착** |
| F4 | `staged.replace(target=<경로>)`(키워드) | **초록** | **포착** |
| F5 | private writer 재사용(`os.open` + `os.write`) | **초록** | **포착** |
| F6 | `shutil.move` | RED(이름 목록) | **포착** |
| F7 | `subprocess.run(["mv", …])` | **초록** | **의도만 포착**(자식이 쓴다 — 알려진 제한) |
| **PROD** | `store._commit` 안에서 builtin `open` 으로 저널을 덮어쓴다(= 실제로 실행되는 production 둘째 writer) | — | **RED** — 실행 경로 test 가 위반으로 잡는다 |

F1~F7 은 버릴 모듈에 심어 **이벤트 포착**을 센다(그 모듈은 `src/` 밖이라 production writer 로
분류되지 않는다). production 분류가 위반이 되는 것은 PROD 행과 술어 단독 test 가 보인다.

### vr M-1 — 전이표: 글리프에서 렌더링 바이트로

| # | 심은 것 | 앞 판 | 이 판 |
| --- | --- | --- | --- |
| PR1·PR2 | 블록 안 `->` · `⟶` | **초록** | **RED** — 렌더링 바이트 등식 |
| PR3 | enum 값 주석의 전이 주장 | **초록** | **RED** — 블록 밖 금지 |
| PR4 | 빈 줄로 떨어진 단락 | **초록** | **RED** — 블록 밖 금지 |
| AD1·AD2·AD3 | ADR 블록 안 `->` · `&rarr;` · `⇒` | **초록** | **RED** — 렌더링 바이트 등식 |
| AD4 | `---` 뒤 재개정 절 | **초록** | **RED** — 블록 밖 금지 |
| AD6 | 블록 밖 마크다운 표 | **초록** | **RED** — 블록 밖 금지 |
| K1 | 허용표 한 행 `//` + 별칭 행 추가 | **초록**(Python·Gradle 둘 다) | **RED 둘 다** — Python 게이트 3 fail(렌더링 등식·주석 행 대조·언급 수) · Gradle `TrainingContractTest` 의 ADR 대조 fail |
| K2 | 블록 뒤 집합 산술(`) - setOf(…) + setOf(…)`) | **초록**(Python·Gradle 둘 다) | Python 게이트는 **초록**(산술이 파싱 블록 밖이다) · **Gradle RED** — ADR 파생 기대값과 런타임 값이 다르다. 두 층의 합성이 닫는 자리다 |
| PY | Python `_TRANSITIONS` 에 간선 추가 | — | **RED** — 두 문면 등식(권위가 Python 표다) |

### cr M-3 · M-2 · M-4 축

| # | 변이 | 결과 |
| --- | --- | --- |
| R1 | `_PENDING_FAILURE_REASONS` 에 넷째 쌍 추가 | **RED 3** — 문면 등식 둘 + 크기 단언 |
| R2 | 문면에 넷째 사유만 추가 | **RED** — 같은 등식(양방향) |
| R3 | `_decode_failure` 를 다시 거부로 | **RED 2** — 사본 불일치·구판 저널 기동 |

## rollback 실측

`rollback.md` 참조(실측 HEAD · ①~⑥ 결과를 그 파일이 갖는다).

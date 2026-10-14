# M6/6B-4 실행 명령과 종료 코드

정본: `scope.md`. 판정 SHA(산출물 마지막) `f8f59593`. 명령 앞뒤로 HEAD 를 단언하고, 측정
중에는 커밋하지 않는다. 출력 전문은 싣지 않는다(핵심 결과 한 줄) — 감사자는 명령을 다시 돈다.

**마지막 HEAD 의 게이트 결과 정본은 이 파일이 아니다** — evidence 는 자기 마지막 커밋의
post-state 를 담을 수 없다. 그 자리는 verifier 리포트와 PR 조치 코멘트다.

## acceptance — `ml-engine` job 전건 (`.github/workflows/ci.yml`)

양끝 HEAD 동일(`f8f59593`). `working-directory: ml-engine`.

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
- 핵심 결과: **1603 passed**(462s). 앞 라운드에 전체 실행 **세 번을 따로 더 돌려 exit
  0/0/0**(1599 셋)을 확인했다 — vr r3b H-1(저널 tmp 이름 충돌 경합)이 전체 실행에서 3/3
  붉었던 자리이고, 그 세 번은 다른 레인의 Gradle 빌드와 **경합하는 동안** 돌려 타이밍 여유를
  줄였다.

### 감사 훅의 세션 비용 — **이 저장소에서 이 수치의 유일한 자리**

A/B 대조(같은 clone, 훅 설치 한 줄만 주석 처리): `tests/app` 269 test 가 **331.3초 → 384.0초
(+15.9%)**. 「닫는 것은 실행된 경로」의 대가다. checklist 판단 14 는 수치를 적지 않고 이 자리를
가리킨다 — 앞 판은 두 곳에 서로 다른 수를 적었다(「330 → 408 · 약 25%」는 A/B 가 아닌 추정).

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

## 2026-10-14T
- cmd: `./gradlew --no-daemon check --rerun-tasks`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL in 10m 16s (359 tasks executed). **proto 를 편집했으므로**
  다시 돌렸다(`--rerun-tasks`). Kotlin 이 ADR 렌더링 블록을 읽어 5x5 진리표를 대조하는 경로가
  컴파일·통과한다

## 2026-10-14T
- cmd: `./gradlew --no-daemon qualityBaseline --rerun-tasks`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL in 24s (측정, 게이트 아님)

## 2026-10-14T
- cmd: `./tools/one-command-check.sh` (**S-20** — `check` job 의 step 이다)
- exit: 0
- 핵심 결과: 「완료 — Kotlin 전건 + Python 전건 통과」. **앞 라운드들 acceptance 에 이 step 이
  빠져 있었다**(code-review 지적). 넣자마자 값을 냈다 — `ml-engine` **저장소 뿌리**의
  `ruff format --check .` 가 `test_jobs_restart.py` 한 줄을 잡았다(내가 돌린
  `ruff format --check tests` 는 경로 범위가 달라 보지 못했다). 고친 뒤 exit 0

### 수정 전 실패 이력(검증이 실제로 작동한 증거)

## 2026-10-14T
- cmd: `./tools/one-command-check.sh` (S-20 첫 실행)
- exit: 1
- 핵심 결과: `unformatted: File would be reformatted … test_jobs_restart.py` — 범위가 더 넓은
  게이트가 좁은 게이트의 초록을 뒤집었다

## 2026-10-14T
- cmd: `uv run python -m pytest tests/gates/test_journal_write_audit.py -q` 12회 반복
  (`runner.shutdown(wait=False)` 판)
- exit: 1 (**2/12**)
- 핵심 결과: `ConfigError: job 저널 복원 실패: JournalFailed(WRITE_FAILED, "[Errno 2] No such
  file or directory: '…/_journal/jobs.json.tmp' -> '…/_journal/jobs.json'")` — 같은 디렉터리의
  두 writer 가 고정 tmp 이름을 다툰다(vr r3b H-1). `wait=True` 뒤 같은 반복 **0/20**

## 2026-10-14T
- cmd: `./gradlew --no-daemon check --rerun-tasks` (Kotlin 헬퍼의 체인 줄바꿈 전)
- exit: 1
- 핵심 결과: `:adapters:ktlintTestSourceSetCheck` 가 `standard:chain-method-continuation` 둘을
  잡았다 — 고친 뒤 통과

## 2026-10-14T
- cmd: `uv run python tools/design_ratchet.py --check` (저널 모듈 분리 전 · cr M-3 가드를
  `transition()` 안에 둔 판)
- exit: 1
- 핵심 결과: `file_loc_band: journal.py = 21`(515줄) → 파일 접근을 `journal_file.py` 로 분리 ·
  `functions_over_soft_limit: state.py = 1` → 사유 축 거부를 `_failure_reason_rejection` 으로

## 2026-10-14T
- cmd: 세션 전역 감사 단언을 `pytest_sessionfinish` 로 두고 위반을 주입
- exit: **0**
- 핵심 결과: 메시지는 찍히는데 **종료 코드가 0** 이다(「게이트 결과는 종료 코드로」 규율 위반).
  수집 순서를 바꿔 전역 단언을 맨 뒤 test 로 돌린 뒤 같은 주입에서 **exit 1** 을 확인했다

## acceptance — `container` job (S-21 ~ S-25)

run 블록은 ci.yml 을 파싱해 **파일에서 그대로 꺼내** 순서대로 실행했다. `uses:` step 은
건너뛴다 — checkout·setup-java·setup-gradle 셋은 로컬 툴체인이 그 자리이고, **S-22f 도
`uses: actions/upload-artifact@v6` 이라 `run` 블록이 없다**(앞 판은 S-22f 를 돌린 step 목록에
적어 **돌린 것처럼 보였다** — code-review 지적). 그 step 이 올리는 SBOM 은 S-22d·S-22e 가
만든 파일이고, 그 둘의 exit 0 이 생성의 근거다.

재사용하지 않았다 — `contracts/proto` 를 편집했고 그것이 ml-serving 이미지 입력이다. **이
회차는 주석만 바뀐 proto 커밋(`6fc9ec16`) 뒤에 돌렸다** — Python 생성물은 주석을 싣지 않지만
Kotlin(Java) 생성물은 javadoc 에 싣는다. 거동 변화 가능성이 0 에 가깝더라도 「이미지 입력
무변경」을 근거로 쓸 수 없으므로 16 step 을 다시 돌렸다(vr r3d §6 의 열린 항목이 이 회차로
닫힌다).

## 2026-10-14T
- cmd: `container` job 의 **run step 16** (일회성 자격 값 둘 · S-21 · S-21a · S-21b · S-22a ·
  S-22b · S-22c · install trivy · S-22d · S-22e · S-23 · S-23b · S-23c · **S-24** · S-25)
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

## RED 선행 실측 — /code-review 응답 라운드

| finding | RED 로 측정한 것 | 결과 |
| --- | --- | --- |
| **A** 취소 표지의 등록 자리 회귀 | 워커 하나를 묶고 둘째 job 을 **실행기 대기열에** 남긴 채 `cancel_all()` | `saw_cancelled is False` — 대기열의 job 이 새 표지를 받아 취소를 잃는다. 수정 뒤 30회 반복 실패 0 |
| **B** 기동 시계가 `accepted_at` 보다 앞섬 | 저널의 `accepted_at` 을 미래로 둔 기동 | `ValueError: finished_at 은 accepted_at 보다 앞일 수 없습니다.` — 조립 근을 벗어나 **매 기동 실패**. 변이(조정 제거) 재확인 RED 1 |
| **D** 새 job 의 자기 폐기 | 시계 역행으로 새 job 을 최고참 대기로 | 문면이 약속한 `ACCEPTED`(1) vs 실제 `FAILED`(4) — `assert 1 == 4` |
| 재기동 처분 per-job 로그 | 고아 하나 + 상한 초과 둘의 caplog | 레코드 **0** → 수정 뒤 3(각 줄에 job_id · accepted_at · detail_code). 통제군: 재재기동은 조용하다 |

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

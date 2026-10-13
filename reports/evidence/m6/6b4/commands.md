# M6/6B-4 실행 명령과 종료 코드

정본: `scope.md`. 판정 SHA(산출물 마지막) `54bbef81`. 명령 앞뒤로 HEAD 를 단언하고, 측정
중에는 커밋하지 않는다. 출력 전문은 싣지 않는다(핵심 결과 한 줄) — 감사자는 명령을 다시 돈다.

**마지막 HEAD 의 게이트 결과 정본은 이 파일이 아니다** — evidence 는 자기 마지막 커밋의
post-state 를 담을 수 없다. 그 자리는 verifier 리포트와 PR 조치 코멘트다.

## acceptance — `ml-engine` job 전건 (`.github/workflows/ci.yml`)

양끝 HEAD `54bbef81` 동일. `working-directory: ml-engine`.

## 2026-10-14T (판정 SHA 54bbef81)
- cmd: `uv sync --frozen --all-extras` (S-1)
- exit: 0
- 핵심 결과: lock 그대로 해소

## 2026-10-14T
- cmd: S-1b serving extras 분리 — `uv sync --frozen --extra serving --no-dev` + 금지 패키지 다섯 import 시도 + dev 복구
- exit: 0
- 핵심 결과: 금지 패키지 전부 import 불가

## 2026-10-14T
- cmd: `uv run ruff check .` + `uv run ruff format --check .` (S-2)
- exit: 0
- 핵심 결과: 227 files already formatted

## 2026-10-14T
- cmd: `uv run mypy --strict src/ml_engine` (S-3)
- exit: 0
- 핵심 결과: 98 source files, no issues

## 2026-10-14T
- cmd: `uv run lint-imports` (S-4)
- exit: 0
- 핵심 결과: Contracts 8 kept, 0 broken

## 2026-10-14T
- cmd: `uv run python -m pytest tests -q` (S-5)
- exit: 0
- 핵심 결과: 1516 passed (421s)

## 2026-10-14T
- cmd: `uv run python tools/design_ratchet.py --check` (S-6)
- exit: 0
- 핵심 결과: allowlist 밖 위반 0

## 2026-10-14T
- cmd: S-7 재활용 출처 두 자리 대조 + 양성 대조(어긋난 evidence 가 실패해야 한다)
- exit: 0
- 핵심 결과: 본 검사 통과 · 양성 대조가 기대대로 실패

## 2026-10-14T
- cmd: S-9 Python 버전 두 자리 대조
- exit: 0
- 핵심 결과: `.python-version` 과 `requires-python` 둘 다 3.12

## 2026-10-14T
- cmd: `uv build --wheel -o /tmp/ml-engine-wheel` + `pytest tests/gates/test_wheel_reexport.py -q` (S-11)
- exit: 0
- 핵심 결과: 1 passed — 설치본에서 재수출 성립

### 수정 전 실패 이력(검증이 실제로 작동한 증거)

## 2026-10-14T
- cmd: `uv run python -m pytest tests/gates/test_design_ratchet.py -q` (설계 래칫, 분할 전)
- exit: 1
- 핵심 결과: `runner.py` 의 `_on_done` 이 51줄 — 함수 분할 뒤 통과

## 2026-10-14T
- cmd: `uv run ruff check src/ml_engine/training/jobs/journal.py` (예외 이름 규약 전)
- exit: 1
- 핵심 결과: N818 — 내부 예외 이름을 고쳐 통과

## acceptance — `check` job

## 2026-10-14T
- cmd: `./gradlew --no-daemon check`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL in 10m 22s (359 actionable tasks)

## 2026-10-14T
- cmd: `./gradlew --no-daemon qualityBaseline`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL in 17s (측정, 게이트 아님)

## RED 선행 실측 (TDD)

## 2026-10-14T
- cmd: `uv run python -m pytest tests/training/test_jobs_state.py -q` (전이표 간선 추가 전)
- exit: 2
- 핵심 결과: `TransitionRejectionReason` import 불가 — 구현 뒤 31 passed

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
  scanRoot 는 `reports/evidence`), 위 `check` exit 0 이 그 결과다. 육안 확인 병기 — 식별자·연락처
  형태 값 0, compose 는 값 리터럴 0(참조만).

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

열은 「무엇을 바꿔치웠나 → 어느 test 가 RED 가 되는가」. 전부 `git restore --source=HEAD` 로
되돌리고 `git status --porcelain` 빈 출력을 확인했다.

| # | 변이 | numstat | RED |
| --- | --- | --- | --- |
| M-1 | 정책 값 `pending_queue_max` 4 → **3** | 1/1 | 2 — 승인 값 test · 값이 폐기 시점에 닿는지 test |
| M-2 | 같은 값 4 → **5** | 1/1 | 같은 2 |
| M-3 | 상한 판정 off-by-one(초과분 계산) | 1/1 | 8 — 폐기 시점·동률·래치·저널 상태·재기동 상한·수렴 |
| M-4 | Python 전이표에서 `ACCEPTED→FAILED` 제거 | 0/1 | 5 — 교차 등식 3 + 전이표 2 |
| M-5 | Kotlin 허용표에서 같은 간선 제거 | 0/1 | 4 — **Gradle 없이 Python 게이트가 잡는다** + 음성 대조 자신 |
| M-6 | proto 주석 표 행 제거 | 0/1 | 3 — 교차 등식 |
| M-7 | 저널 쓰기 **전에** 메모리 커밋(순서) | 1/1 | 1 — 쓰기 실패 시 메모리 불변 test |
| M-8 | 원자 교체를 직접 쓰기로 | 1/3 | 4 — 크래시 주입 fsync·replace × (저널·재기동) |
| M-9 | 깨진 저널을 「없음」으로 읽기 | 2/2 | 6 — schema·시각·어휘·중복 둘·필드 누락 |
| M-10 | `START` 를 다시 `submit()` 안으로 | 1/0 | 10 — 대기 관측·wire handle·취소·종료 경로 |

M-9 보충: JSON **문법** 오류는 이 변이가 건드리지 않는 갈래(`JSONDecodeError`)라
`test_malformed_payload_is_failed_not_absent` 는 초록으로 남았다. 구조·어휘·불변식 갈래 여섯이
잡았다. M-5 는 음성 대조 test 자신(`parse_kotlin_edges` 가 지운 간선을 본다)도 함께 붉어진다 —
기준 문면이 이미 바뀌었음을 그 test 가 알아차린 것이다.

## D-6B4-3 잠금 커밋 뒤 재실측 (산출물 HEAD `18069139`)

산출물 커밋 하나(`ReadinessGate` 전이 집합 잠금 test + 조립 근 NOT_READY test 의 의존 주석)가
붙은 뒤의 측정이다. **그 커밋이 바꾼 것은 Python test 파일 둘뿐**이다.

## 2026-10-14T (판정 SHA 18069139)
- cmd: `uv run python -m pytest tests -q` (S-5)
- exit: 0
- 핵심 결과: **1517 passed**(476s) — 잠금 test 하나가 늘었다. 양끝 HEAD 동일

## 2026-10-14T
- cmd: `ml-engine` job 의 S-2·S-3·S-4·S-6·S-7·S-9·S-11 재실행(전부 가볍다 — 재사용 판단을 쓰지 않았다)
- exit: 0 (일곱 전부)
- 핵심 결과: ruff · mypy · import 계약 · 설계 래칫 · 재활용 출처(+양성 대조) · 버전 두 자리 · wheel 재수출

### `check`·`container` job 의 재사용 판단

- **`container` job: 재사용한다.** 근거는 기계 측정 —
  `git diff --name-only 54bbef81..18069139 -- docker/ ml-engine/src ml-engine/policy ml-engine/pyproject.toml contracts/ tools/ config/ adapters/ app/ build-logic/ .github/`
  가 **빈 출력**이다. 이미지 입력·compose·정책·계약·게이트 스크립트·워크플로가 한 글자도
  바뀌지 않았으므로 S-21~S-25 의 입력이 동일하다. 바뀐 둘은 이미지에 들어가지 않는
  `ml-engine/tests/**` 다(Dockerfile 이 `src`·`policy`·`tools` 만 복사한다).
- **`check` job: 재사용하지 않고 다시 돌린다.** Kotlin 소스는 위 측정대로 무변경이지만
  `leakPatternGate` 의 scanRoot 가 `reports/evidence` 라 **이 evidence 편집 자체가 그 게이트의
  입력**이다. 그래서 evidence 커밋 뒤 그 HEAD 에서 다시 돌린다(결과 정본은 verifier 와
  완료 보고 — evidence 는 자기 마지막 커밋의 post-state 를 담을 수 없다).

## 변이 추가 — 열거 우회(D-6B4-3 잠금)

| # | 변이 | numstat | RED |
| --- | --- | --- | --- |
| M-11 | `ReadinessGate` 에 **다른 이름**(`retry_preload`)으로 READY 복귀 경로를 되살림 | 4/0 | 1 — 새 전이 집합 test. **기존 `mark_ready` 이름 금지 test 는 초록으로 남았다**(열거만으로는 못 막는다는 실측) |

## rollback 실측

`rollback.md` 참조(실측 HEAD · ①~⑥ 결과를 그 파일이 갖는다).

## acceptance — `container` job (S-21 ~ S-25, ci.yml 의 run step 순서대로)

S-23b·S-23c·S-25 는 워크플로의 run 블록을 **파일에서 그대로 꺼내** 실행했다(손으로 옮겨 적지
않는다). S-22f 는 CI 전용 artifact 업로드라 로컬에서는 SBOM 파일 생성 여부로 갈음한다.

## 2026-10-14T
- cmd: `docker build -f docker/ml-serving.Dockerfile -t bidvector/ml-serving:local .` (S-21)
- exit: 0
- 핵심 결과: 이미지 생성(15s)

## 2026-10-14T
- cmd: `./gradlew --no-daemon :app:bootJar` (S-21a)
- exit: 0
- 핵심 결과: 37 tasks up-to-date

## 2026-10-14T
- cmd: `docker build -f docker/app.Dockerfile -t bidvector/app:local .` (S-21b)
- exit: 0
- 핵심 결과: 이미지 생성

## 2026-10-14T
- cmd: `./tools/image-hygiene-check.sh bidvector/ml-serving:local config/quality/image-hygiene-policy.properties` (S-22a)
- exit: 0
- 핵심 결과: 위생 게이트 통과 — 금지 import 5건 부재 · pid 1 uid 10001 · base layer 접두 일치 · 크기 상한 아래

## 2026-10-14T
- cmd: `./tools/image-hygiene-check.sh bidvector/app:local config/quality/image-hygiene-policy-app.properties` (S-22b)
- exit: 0
- 핵심 결과: 위생 게이트 통과

## 2026-10-14T
- cmd: S-22c 앱 이미지 거부 스모크(운반 이름공간을 조기에 거부)
- exit: 0
- 핵심 결과: 컨테이너 비-0 + 사유 표지 D-6A2a-10 + 거부 문면이 그 이름공간 키를 말함

## 2026-10-14T
- cmd: install trivy — 정책 파일의 버전·자산·핀 체크섬으로 받아 `sha256sum --strict -c`
- exit: 0
- 핵심 결과: 0.75.0, 체크섬 OK

## 2026-10-14T
- cmd: `./tools/vuln-scan-check.sh bidvector/ml-serving:local ml-serving config/quality/vuln-policy.properties` (S-22d)
- exit: 0
- 핵심 결과: 차단 후보 52 · 미등재 0 — 게이트 통과

## 2026-10-14T
- cmd: `./tools/vuln-scan-check.sh bidvector/app:local app config/quality/vuln-policy.properties` (S-22e)
- exit: 0
- 핵심 결과: 차단 후보 15 · 미등재 0 — 게이트 통과

## 2026-10-14T
- cmd: S-22f 갈음 — SBOM 파일 생성 확인(`build/vuln-scan/*-sbom.cdx.json`)
- exit: 0
- 핵심 결과: 2개(이미지 둘)

## 2026-10-14T
- cmd: `docker compose -f docker/compose.yaml up -d` + healthy 수렴 대기 (S-23)
- exit: 0
- 핵심 결과: app·ml-serving·postgres 세 서비스 healthy(15s) — **ml-serving 에 저널 볼륨이 붙은 뒤에도** 수렴

## 2026-10-14T
- cmd: S-23b 컨테이너 스모크(ci.yml run 블록 그대로, 230줄)
- exit: 0
- 핵심 결과: 프로브·집계 health·인증 경계·쓰기 왕복·dry-run 왕복·수집 꺼짐 전 절 통과

## 2026-10-14T
- cmd: `./tools/db-rehearsal.sh docker/compose.yaml postgres` (S-23c)
- exit: 0
- 핵심 결과: 복원 등식 10가지 · forward-fix · 음성·양성 대조 통과

## 2026-10-14T
- cmd: `docker compose -f docker/compose.yaml down -v` (S-25)
- exit: 0
- 핵심 결과: 볼륨 둘(pg-data · ml-jobs) + 네트워크 삭제

### Q-6 전용 실측 — 컨테이너 재생성 뒤 저널 생존 (wire 를 지나서)

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

# M3/3H-2 commands

acceptance: `./gradlew --no-build-cache --no-daemon clean check` + `cd ml-engine && uv run pytest`(scope.md). 마이그레이션 없음 → migration-reviewer·Codex 불요.

## 2026-09-17 D-3H-8 채움률 프로브(운영자 승인 2026-09-16·17 — read-only 1회, 팀장)

- cmd: `KONEPS_ENV_FILE=<config/ 아래 환경 파일> python3 _workspace/m3-3h2/probe_agency_fillrate.py 3 100` — 입찰공고 목록(공사, `getBidPblancListInfoCnstwk`, 조회구분 1 등록일시, 최근 3일, 1페이지 100건, `type=json`). 값(기관명·코드·담당자)은 출력·저장하지 않고 비율만 낸다.
- exit: 0 (`resultCode=00`, `totalCount=1397`, sampled 100)
- 핵심 결과: `dminsttCd`·`dminsttNm`·`ntceInsttCd`·`ntceInsttNm` **전부 100/100 채움**. `dminsttCd == ntceInsttCd` 91/100(공고기관과 수요기관이 같은 건이 다수 — 문서 「동일할 수 있음」 실증). `dminsttCd` 길이 집합 {7}(문서 항목크기 7 일치), **영문자 포함 29/100**(코드는 숫자만이 아니다 — `AgencyCode.of` 의 소문자화가 실제로 작용하는 입력이 있다, Python `normalize_feature_key` 와 같은 규칙이라 키 동일성 유지). → **D-3H2-5 충족, D-3H-2(코드 키) 재확인 — 이름 키 대안 불필요.**
- 실측 앞 두 시도는 `returnReasonCode 30`(등록되지 않은 서비스키) — 원인은 인코딩이 아니라 해당 서비스 활용신청 미승인이었고, 승인 뒤 같은 명령이 위 결과를 냈다(3A §3.2 의 원인 후보 목록에 「서비스별 활용신청 미승인」을 실증으로 보탠다). 환경 파일의 키가 URL 인코딩 형태(`%` 포함)여도 프로브가 재인코딩하지 않도록 했다.

## 2026-09-17 D-3H2-3 엔진 표본 축 허용 결측 사유 확장(`ml-implementer` 레인, 운영자 「추천대로」)

- RED: `cd ml-engine && uv run pytest tests/inference/test_distribution.py -q` — 표본 축 수용 test 를 뒤집은 상태에서 프로덕션 변경 전에 실행. exit 1, 표적 test 1건만 실패(그 밖 21건 통과) — 뒤집기가 의도한 지점에서만 깨졌음을 확인.
- GREEN: 같은 명령, `_is_segment_missing_reason_allowed` 확장 뒤. exit 0.
- 전건: `cd ml-engine && uv run pytest -q` — exit 0, 956 passed(golden 서브셋 16 passed 포함, `fixtures/**` 무편집).
- `uv run ruff check` / `uv run ruff format --check` / `uv run mypy` — 수정 대상 세 파일 전부 exit 0(포맷 위반 1건은 `ruff format` 로 정정 뒤 재확인 exit 0).
- `ml-engine/src/ml_engine/features/facts.py` 모듈 docstring 정정(코드 무변경, 계약 갱신 1): `uv run ruff check src/ml_engine/features/facts.py` exit 0.

## 2026-09-17 D-3H2-1·D-3H2-2 Kotlin 요청·표본 축 조립(`kotlin-implementer` 레인)

- RED: `predictionRequestFor`/`sampleOf` 를 `agencyId = null` 로, `toSampleAgencyIdFact` 를
  M2/2F 결측 사유로 되돌린 변이 상태에서 `./gradlew --offline --no-daemon --no-build-cache
  :workflow:test --tests "bidvector.workflow.evaluation.PredictionFactsTest" --tests
  "bidvector.workflow.evaluation.SampleEligibilityTest" --tests
  "bidvector.workflow.evaluation.OpportunityAnalysisTest" :adapters:test --tests
  "bidvector.adapters.ml.RequestMappingTest"` — exit 1, 신설 test 4건만 기대대로 실패
  (그 밖 회귀 0, `AssertionFailedError: Expected AgencyId(value=1234567) but actual was
  null` 3건 + wire missing reason 불일치 1건).
- GREEN: 변이 원복 뒤 같은 명령 — exit 0.
- `./gradlew --offline --no-daemon --no-build-cache :workflow:sizeGate
  :workflow:runKtlintCheckOverTestSourceSet :workflow:compileTestKotlin` — exit 0
  (`testNoticeWithMoney`가 함수 50줄 한도를 1줄 넘겨 처음 실패했고, 추정가격 조립을
  private 함수로 분리한 뒤 재실행에서 exit 0).
- 전건: `./gradlew --no-build-cache --no-daemon clean check`(HEAD `e866be3`) — exit 0
  (346 actionable tasks, 321 executed).
- `cd ml-engine && uv run pytest -q`(Python 무변경 확인용) — exit 0, 956 passed.

## 2026-09-17 D-3H2-4 엔진 교차 실측(요청·표본 축 왕복, 일회성 probe — 커밋하지 않음)

- 방법: 4B-7 교차 실측 관례(testdata → 엔진 직접 호출)를 따르되 wire 대신 실제 `mapRequest`
  산출 바이트를 쓴다. 임시 Kotlin test 하나(scratchpad 로 삭제 — 커밋 없음, evidence-pack
  「자기 검사 하네스 금지」와 같은 결로 이 probe 도 일회성)가 요청 1건(agencyId 값 있음)+
  표본 8건(같은 agencyId·categoryCode)을 `mapRequest`로 조립해 scratchpad에 `.binpb`로
  남겼다. Python 스크립트(scratchpad, 커밋 없음)가 그 바이트를
  `CalculateOptimalBidRequest.ParseFromString`으로 그대로 읽어 `serve_bid_rates`(출하
  정책)에 통과시켰다 — Kotlin 매핑 로직을 거치지 않고 손으로 지은 proto가 아니다.
- cmd: `./gradlew --offline --no-daemon --no-build-cache :adapters:test --tests
  "bidvector.adapters.ml.Scratch3H2CrossCheckDump"` — exit 0(scratchpad에 `.binpb` 생성).
- cmd: `cd ml-engine && uv run python <scratchpad>/3h2_cross_check.py` — exit 0.
- 핵심 결과: `segment_support=DIRECT`·`agency_sample_count=8`·`agency_sample_below_
  threshold=True`(출하 임계 10 > 8)·`shrinkage_weight=0.4` — ML-04 ②(기관 표본이
  임계 미만이면 수축 가중치가 응답 근거에 실린다)를 Kotlin→Python 왕복으로 처음 관측.
- 우회 (3) 실측: 표본 9건 중 1건을 `agencyId = null`(UNKNOWN)로 섞어 같은 스크립트를
  재실행 — `excluded_observations=0`(거부되지 않음)·`agency_sample_count`은 여전히
  8(그 표본이 agency 세그먼트에 안 들어갈 뿐 요청 전체는 죽지 않음).
- 정리: probe 로 만든 Kotlin test·Python 스크립트 둘 다 커밋하지 않았다(`git status
  --short`로 in_scope 밖 무변경 확인).

## 비밀값 스캔

- cmd: `grep -rniE -f config/quality/leak-patterns.txt <in_scope Kotlin 경로 8개 개별 인자> reports/evidence/m3/3h2/`
- exit: 1(매치 없음 — 통과)

## rollback 실측(`rollback.md` ⑥, 임시 clone `--no-hardlinks`, HEAD `e866be3`)

- cmd: `git restore --source=e101a0c --staged --worktree -- <Kotlin 경로 8개>` — exit 0,
  `git diff --name-status e101a0c -- <같은 8개>` 빈 결과(바이트 동일 확인).
- cmd: `./gradlew --offline --no-daemon --no-build-cache :workflow:compileTestKotlin
  :adapters:compileTestKotlin` — exit 0.
- cmd: `./gradlew --offline --no-daemon --no-build-cache :workflow:test :adapters:test` —
  exit 0.
- cmd: `./gradlew --no-build-cache --no-daemon clean check`(되돌린 트리 전건) — exit 0
  (355 actionable tasks, 338 executed).

## 2026-09-17 verifier r1 수정 라운드 — F-1(HIGH)·F-2(MEDIUM) 계약 문면 정정

- cmd: `cd ml-engine && uv run ruff check src/ml_engine/inference/distribution.py &&
  uv run ruff format --check src/ml_engine/inference/distribution.py`(주석 인용구
  정정, 코드 무변경) — exit 0.
- cmd: `./gradlew --offline --no-daemon --no-build-cache :workflow:compileKotlin
  :workflow:runKtlintCheckOverMainSourceSet` — exit 0.
- cmd: `./gradlew --offline --no-daemon --no-build-cache :contractGate`(커밋
  `e46d96c` 뒤) — exit 0 — 주석만 정정했으므로 `buf breaking`이 승인 태그를 걸지
  않는다.
- 전건: `./gradlew --no-build-cache --no-daemon clean check`(HEAD `e46d96c`) — exit 0
  (346 actionable tasks, 328 executed).
- `cd ml-engine && uv run pytest -q`(HEAD `e46d96c`) — exit 0, 956 passed.

## 2026-09-17 verifier r1 수정 라운드 — F-3·F-4(MEDIUM, rollback.md 전체 경로 커버)

`rollback.md`를 그룹 A(Kotlin·계약 주석 10개)·그룹 B(Python 4개)·그룹 C(공유 문서 4개,
hunk 격리)로 재작성 — 임시 clone(`029ccdb6.../scratchpad/3h2-rollback-clone-r1`,
`--no-hardlinks`)에서 ①~⑧ 전부 재실행.

- cmd: 그룹 A+B `git restore --source=e101a0c --staged --worktree -- <14개 경로>` —
  exit 0. `git diff --name-status e101a0c -- <같은 14개>` 빈 결과(바이트 동일).
- cmd: 그룹 C hunk 격리 셋(`git diff <sha>~1..<sha> -- <파일> | git apply -R`,
  `milestone-3.md`/`capability-map.md`+`data-dictionary.md`/`5d3/scope.md`) — 전부
  exit 0, conflict 0. 4개 파일 모두 `git diff --name-status e101a0c -- <파일>` 빈
  결과(바이트 동일 — 각 파일이 이 range 안에서 3H-2 커밋 하나씩만 가져 hunk 격리가
  전체 restore와 같은 결과를 냈다). 「남의 줄이 남았다」 확인: `milestone-3.md`의 `##`
  절 6개·3B-2/3E/3F 종결 언급 22건, `capability-map.md`의 다른 `OPEN-*` 행 3개,
  `5d3/scope.md`의 다른 계약 갱신 행 3개 — 전부 그대로.
- cmd: 위 복구 상태를 임시 clone 전용 커밋으로 고정(`contractGate`가 `contracts`/
  `ml-contract` 경로의 **비커밋 diff**를 방향과 무관하게 위반으로 본다 — rollback.md
  ⑧ 주의 문단).
- cmd: `./gradlew --offline --no-daemon --no-build-cache :workflow:compileTestKotlin
  :adapters:compileTestKotlin` — exit 0.
- cmd: `./gradlew --offline --no-daemon --no-build-cache :workflow:test
  :adapters:test` — exit 0.
- cmd: `cd ml-engine && uv sync --extra dev --extra serving --extra training && uv
  run pytest -q` — exit 0, 954 passed(임시 clone은 인용구 정정 커밋으로 되돌아간
  `test_distribution.py`의 D-3H2-3 RED test 2건이 빠져 956→954, 3H-1 시점과 정합).
- cmd: `./gradlew --no-build-cache --no-daemon clean check`(되돌린 트리 전건,
  임시 clone 커밋 뒤) — exit 0(346 actionable tasks, 318 executed).

## 2026-09-17 verifier r1 재검증 표적 — F-1 계약 문면↔송신↔수신 세 자리 일치(checklist 우회 (6))

- cmd: `grep -n "NOT_COLLECTED_YET.*UNKNOWN\|UNKNOWN.*NOT_COLLECTED_YET"
  contracts/proto/bidvector/ml/v1/features.proto
  adapters/src/main/kotlin/bidvector/adapters/ml/RequestMapping.kt
  ml-engine/src/ml_engine/inference/distribution.py` — 세 파일 모두 매치(계약 주석·
  송신 `toAgencyIdFact` 위임·수신 `_ALLOWED_SEGMENT_MISSING_REASONS`), 세 자리가
  같은 두 값 집합을 선언한다.

## 이 문서의 마지막 HEAD

이 문서의 명령 표는 evidence 편집 커밋 직전까지만 담는다 — **이 문서를 담는 커밋
자체의 게이트 결과 정본은 verifier와 PR 조치 코멘트**다(evidence-pack 2026-09-16
규약, F-6). 마지막으로 기록된 exit 0은 HEAD `e46d96c`(F-1·F-2 정정 커밋)와 rollback
임시 clone 실측 — 이 문서를 담는 evidence 커밋 자체의 재확인은 verifier r2가 한다.

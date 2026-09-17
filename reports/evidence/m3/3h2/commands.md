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

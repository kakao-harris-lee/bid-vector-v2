# M3/3H-2 commands

acceptance: `./gradlew --no-build-cache --no-daemon clean check` + `cd ml-engine && uv run pytest`(scope.md). 마이그레이션 없음 → migration-reviewer·Codex 불요.

## 2026-09-17 D-3H-8 채움률 프로브(운영자 승인 2026-09-16·17 — read-only 1회, 팀장)

- cmd: `KONEPS_ENV_FILE=<config/ 아래 환경 파일> python3 _workspace/m3-3h2/probe_agency_fillrate.py 3 100` — 입찰공고 목록(공사, `getBidPblancListInfoCnstwk`, 조회구분 1 등록일시, 최근 3일, 1페이지 100건, `type=json`). 값(기관명·코드·담당자)은 출력·저장하지 않고 비율만 낸다.
- exit: 0 (`resultCode=00`, `totalCount=1397`, sampled 100)
- 핵심 결과: `dminsttCd`·`dminsttNm`·`ntceInsttCd`·`ntceInsttNm` **전부 100/100 채움**. `dminsttCd == ntceInsttCd` 91/100(공고기관과 수요기관이 같은 건이 다수 — 문서 「동일할 수 있음」 실증). `dminsttCd` 길이 집합 {7}(문서 항목크기 7 일치), **영문자 포함 29/100**(코드는 숫자만이 아니다 — `AgencyCode.of` 의 소문자화가 실제로 작용하는 입력이 있다, Python `normalize_feature_key` 와 같은 규칙이라 키 동일성 유지). → **D-3H2-5 충족, D-3H-2(코드 키) 재확인 — 이름 키 대안 불필요.**
- 실측 앞 두 시도는 `returnReasonCode 30`(등록되지 않은 서비스키) — 원인은 인코딩이 아니라 해당 서비스 활용신청 미승인이었고, 승인 뒤 같은 명령이 위 결과를 냈다(3A §3.2 의 원인 후보 목록에 「서비스별 활용신청 미승인」을 실증으로 보탠다). 환경 파일의 키가 URL 인코딩 형태(`%` 포함)여도 프로브가 재인코딩하지 않도록 했다.

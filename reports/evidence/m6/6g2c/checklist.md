# M6/6G-2c — 리뷰 요청 조건 점검

레인마다 자기 절만 쓴다.

## P — Python

판정 대상 SHA **`f3a47f05`**(수정 라운드 1 의 마지막 P 커밋).

| 조건 | 상태 |
|---|---|
| 구현 diff 가 커밋돼 base/head 고정 | 예 — base `ecdc9d9f`, P 커밋 열아홉(라운드 1 이 열둘) |
| 관련 test/lint/type/architecture/contract 명령 통과 | 예 — `ml-engine` job 열 step 전부 exit 0(라운드 1 뒤 재실측) |
| 구현 항목마다 변이 하나(D-6G2c-14) | 예 — 항목 여섯 + finding 열둘, 변이 전부 기대대로 |
| 게이트 술어를 바꾼 커밋 표시 | 예 — `49505a74`(D-6G2c-23) · **`a179e784`**(F-1 스윕 범위) |
| 변경된 fixture·정책 version 의 근거 기록 | 해당 없음 — fixture 바이트·정책 파일 무변경. 공유 파일 하나(스키마 문서 §2 어휘 문장, 팀장 커밋 `ae1077e4`)가 함께 움직였고 rollback 공유 파일 목록 대상이다 |
| 알려진 제한과 되돌리는 방법 기록 | 예 — commands.md 「이탈과 알려진 제한」 · rollback 은 in_scope 경로 한정 복원 |
| 자기 승인 금지 | 예 — 이 레인은 판정하지 않는다 |

### 항목 전수 대조 (계약 「이 PR 의 항목 요약」 P 줄)

| ID | 상태 |
|---|---|
| D-6G2c-12 manifest 키 세 자리 등식 | 구현·변이 RED |
| D-6G2c-17 업무 대표 표지 셋 | 구현·변이 셋 RED |
| D-6G2c-21 ③ seed 키 열거 한 자리 | 구현·변이 RED |
| D-6G2c-21 ④ 기존 판정 덮어쓰기 거부 | 구현·변이 RED |
| D-6G2c-22 판독기 URI 디코드 | 구현·변이 RED(CLI 끝까지 + 판독기 단위) · **음성 대조 넷**으로 거부가 그대로임을 확인 |
| D-6G2c-23 app 층 HTTP import 계약 | 구현·변이 RED(두 층 모두) |
| D-6G2c-24 파생 지역 변수 한 단계 | 구현·변이 둘 RED |

### 수정 라운드 1 (D-6G2c-35) 전수 대조

| finding | 상태 |
|---|---|
| F-1(high, 게이트) · F-2 · F-3 · F-4 · P-5 | 구현·변이 확인 |
| F-5 · F-7 · P-7 · P-8 · P-9 · P-13 | 구현·변이 확인 |
| F-6 · P-6 · P-10 | 문면 정정(F-6 은 39 → 41) |
| P-11 · P-12 | **등재**(commands.md 알려진 제한) |

미처리 지시 **없음**. 라운드 1 이 만진 자리 가운데 계약 밖 하나(`app/__init__.py` 문면)는
commands.md 에 in_scope 한 줄 요청으로 적었다.

### 수정 라운드가 만든 새 파일 ↔ in_scope 대조

새 파일 **넷**, 전부 `ml-engine/tests/**`(in_scope) 안이다 — D-6G2c-23 의 양성 대조용 독립 미니
프로젝트 셋(`tests/app/fixtures/bad_app_http/` 의 `pyproject.toml` 과 `ml_engine` 스캐폴드 둘)과
D-6G2c-22 의 판독기 단위 test 하나(`tests/adapters/test_snapshot_files.py` — 팀장 요청으로 음성 대조를
추가하면서 생겼다). **수정 라운드 1 은 새 파일을 만들지 않았다**(고친 파일 열둘). 계약 갱신이 필요한
새 경로는 없다.

### OPEN 처분

| OPEN | 상태 |
|---|---|
| `OPEN-6G2E-SNAPSHOT-READER-URI-DECODE` | **닫힘**(D-6G2c-22) |
| `OPEN-6G2E-APP-HTTP-IMPORT-CONTRACT` | **닫힘**(D-6G2c-23) |
| `OPEN-6G2A-CENSUS-DERIVED-LOCALS` | **닫힘**(D-6G2c-24, 한 단계까지 — 두 단계·컨테이너는 알려진 제한) |

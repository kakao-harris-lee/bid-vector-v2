# M9 / opening-harness — 명령과 결과

- 판정 SHA: **`255f7459`**(마지막 산출물 커밋) · base main `df4976cc` · 병합 커밋 **0**
- acceptance 정본은 PR CI 의 `web` job(W-13 여정 포함)과 `check` job(S-20)이다. **Playwright 여정은
  로컬에서 돌리지 않는다**(공통 규율) — 아래는 로컬에서 설 수 있는 것과 fake 단독 기동뿐이다.

## 로컬 게이트 (web, 판정 SHA)

| 명령 | 결과 |
| --- | --- |
| `npm run lint` | exit 0 |
| `npm run typecheck` | exit 0 |
| `npm run test` | **781 passed / 39 files**(착수 762 → 새 게이트 test 19) |
| `npm run gate:tests` | 등록부 기준 전부 통과 · skipped/todo/failed 0 |
| `npm run gate:src-tree` | 컴파일·번들 경계 전제 성립 |
| `npm run gate:boundary-canary` | 번들 그래프 경계 활성(메인·워커 두 그래프) |
| `npm run api:types:check` | 생성 타입이 OpenAPI 와 바이트까지 같다 |
| `npx playwright test --config=playwright.journey.config.ts --list` | **12 tests / 4 files**(착수 11/4 — spec 파일 집합 불변이라 여정 등록부 갱신이 필요 없다) |
| e2e spec 별도 `tsc`(저장소 밖 설정, `e2e`+`src` 포함) | `e2e/` 오류 **0** — spec 은 `tsconfig.json` 의 프로그램 밖이고 lint 가 타입 인식이 아니라 손으로 한 번 잰다 |

## fake 단독 기동 (node, 저장소 밖 스크립트)

개찰 축 오퍼레이션 **여덟 전부 200** — 공사 목록 6행 · 용역 목록 1행 · 예비가격 상세 15행 ·
개찰완료 3행 · 기초금액 1행 · A값 1행. 공고 축 둘도 200(공사 11 · 용역 1).
**미등재 경로 404** · **모르는 공고의 상세 축 200 + 0행**. 공사 목록의 진행구분이 설계 그대로
「개찰완료 / 유찰 / 재입찰 / 개찰취소 / (칸 없음) / 개찰완료」로 나왔다.

## 하네스가 쓰는 유도 값 (실측)

| 값 | 어디서 나오는가 | 실측 |
| --- | --- | --- |
| 공고 축 수집 업종 | 공고 축 fixture 의 오퍼레이션 집합 | `construction,service` |
| 공사 개찰 축 수집 | 설계의 공사 공고 수 | `frame=6 sampled=6` |
| 용역 개찰 축 수집 | 설계의 용역 공고 수 | `frame=1 sampled=1` |
| 승격 | 설계의 승격 업무 | `groupsRead=7 promoted=6 outsideSample=1` |

## 변이 — 전부 바꿔치우기, 전부 RED

| 변이 | 결과 |
| --- | --- |
| ① 갈래 하나(`REBID`)의 설계 항목 제거 | **1 failed** — 설계 집합이 계약 열거의 진부분집합이 됐다(acceptance 가 요구한 변이) |
| ② 오퍼레이션 이름을 출하 기본값과 갈라 적는다(`02` 접미) | **1 failed** — 출하 기본 표 대조 등식 |
| ③ 진행구분 라벨을 바꿔치운다(유찰 → `OPENED`) | **2 failed** — 정책 대조 등식과 「선언 == 유도」 둘이 함께 |
| ④ 설계가 쓰는 공고를 공고 축 fixture 에서 뺀다 | **1 failed** — 교차 등식(그 공고는 `NOTICE_ABSENT` 가 되고 집합은 여전히 아홉일 수 있다) |
| ⑤ 전수 보증 표지(`satisfies Record<…>`)를 떼어낸다 | **1 failed** — 추출이 거부한다(계약 쪽이 아무것도 보증하지 않는 등식을 지나보내지 않는다) |
| ⑥ 승격 범위를 모든 업무로 넓힌다 | **1 failed** — `OBSERVED_NOT_PROMOTED` 갈래가 사라진다 |
| **음성 대조** 설계 공고의 선언 순서만 뒤집는다(거동 0) | **19 passed** — 순서는 등식의 입력이 아니다 |

## 개인정보·비밀값

- 참조형 누출 스캔(`grep -rniE -f config/quality/leak-patterns.txt`)을 이 evidence 디렉터리에 돌려
  **0건**. `leakPatternGate` 의 스캔 뿌리는 `reports/evidence/` 이고 `scope.md` 는 제외 대상이다.
- 개찰 축 fixture에 사업자등록번호·대표자명 키가 **없다**. 그 두 이름이 이 slice 의 파일에 나오는
  자리는 **「없다」를 단언하는 test 와 그 사실을 적은 provenance** 뿐이다(생성되는 행에는 0).
- 투찰 업체 상호는 합성 문자열이고, 개찰 결과 조회 응답에 그 칸이 **없다**(계약 — 화면도 받지 않는다).
- 하네스가 새로 쓰는 설정 파일 둘(`opening-<업무>.properties`·`promote.properties`)도 **기존 설정
  파일 쓰기 함수 그대로** 지난다 — 0600 · 저장소 밖 run-state · 종료 때 디렉터리째 삭제. 민감 값을
  argv 에 싣지 않는 규율(CR L-4)을 새 걸음도 그대로 쓴다(승격 걸음은 애초에 KONEPS 를 부르지 않아
  그 값을 요구하지도 않는다).

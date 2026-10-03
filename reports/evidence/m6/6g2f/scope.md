# M6/6G-2f — 개찰 축 페이지 크기 999: 키×operation×일 1,000 한도 안에서 호출 수를 줄인다 (계약, 착수 2026-10-03)

> **지위: 착수(2026-10-03).** base `9a5aa26a`(PR #54 6G-2e 머지 뒤 `main`), worktree `bid-vector-v2-m6-6g2f`, 브랜치 `m6-6g2f/2026-10-03`.
> 운영자 결정 2026-10-03: 「운영계정 신청(운영자 작업)과 별개로 **페이지 크기 999 slice 를 지금**」 — 선택지 셋(지금 · 승인만 기다림 · 표본 축소) 중 첫째.
> 수령: 실수집 day1·day2 실측(`reports/evidence/m6/6g/commands.md` 「실 KONEPS 호출」, 10-02·10-03) · **`OPEN-6G2D-FRAME-REWALK`** 후보(day2 관찰, 이 slice 가 비용만 줄이고 닫지는 않는다).

- base: **`9a5aa26a`**.
- 레인: `kotlin-implementer` 하나(Kotlin `app` 모듈 + runbook). Python·contracts 무변경. 호스트 빌드 직렬(수집 실행과 겹치지 않게 — §「배포」).

## 왜 이 slice 인가

실수집 이틀의 실측: 이 키의 KONEPS 게이트웨이 한도는 **키 × operation × 일 1,000 건**이다(개찰완료 `HTTP 429` · `X-RateLimit-Limit: 1000` · `returnReasonCode 22`; 두 레인이 쓰는 operation 일곱 전부 `X-RateLimit-Limit: 1000`, 팀장 진단 호출 10-03). 개찰완료(`getOpengResultListInfoOpengCompt`)만 업무 공통 **단일 operation** 이라 먼저 닫히고, 그 축은 페이지 크기 **100** 으로 공고당 1~35 페이지를 부른다(day1: 476 공고에 1,000 호출 — 1페이지 365 · 2페이지 이상 111). 결과 하루 약 470 공고, 표본 24,000 에 약 50일.

게이트웨이는 `numOfRows=999` 를 그대로 받는다(팀장 실측 10-03: 개찰 목록 공사, `totalCount 1522` 에 `items 999` 반환, `numOfRows 999` 에코). 페이지 크기를 999 로 올리면 개찰완료가 사실상 공고당 1 호출이 되어 **하루 약 950 공고(2배)**, 표본틀 걷기(65,064 행)는 **812 → 약 66 호출**로 준다. 운영계정 승인(100,000/일) 뒤에도 일 상한 20,000 안의 낭비가 같은 비율로 준다. **우회가 아니다** — 같은 키·같은 한도 안에서 호출당 행 수를 올릴 뿐이다.

## 착수 실측 (팀장, 2026-10-03)

| 항목 | 값 |
|---|---|
| base SHA | `9a5aa26a` |
| 페이지 크기의 자리 | `KonepsSourceConfig.numOfRowsPerPage`(adapters, 기본 `DEFAULT_ROWS_PER_PAGE = 100`) — 단건 조회(`fetchSingleKonepsNotice`: 개찰완료·예비가격 상세·기초금액·산식 A)와 목록 걷기(`KonepsOpeningResultSource` 목록 축) **둘 다** 이 값 하나를 쓴다. 생성자 인자라 이미 주입 가능 — adapters 변경 불필요 |
| 배선 | `OpeningCollectionWiring.openingCollectionSources` 가 `KonepsSourceConfig(gate, serviceKey, httpPolicy, collectionPolicyProvider)` 를 짓고 `numOfRowsPerPage` 를 **주지 않는다**(기본 100) |
| 설정 표면 | `KonepsOpeningEndpointProperties`(`bidvector.koneps.opening`) — base URL·경로 기본값이 이미 있는 자리 |
| 공고 목록 레인 | `KonepsOpenApiNoticeSource` 는 자기 상수 100(3B 기존 코드, 재사용 금지 주석) — **이 slice 밖** |
| 기존 test 의 numOfRows 단언 | **없다** — `grep -rn 'numOfRows=' app/src/test adapters/src/test` 0건. E2E mock(`MockOpeningKonepsHttp`)은 요청의 `numOfRows` 를 **무시**하고 자기 `rowsPerPage(operation)`(개찰완료 2)로 쪽을 낸다 → 값 slice 두 축이 비어 있다 |
| `maxPages` | `KonepsHttpPolicyData.maxPages = 50`(정책 데이터) — `MAX_PAGES` 확정 제외 문턱이 「참가 5,000 초과」(50×100)로 문서화됨(runbook §0 표·§5, 6G-2d D-6G2d-37) |
| 실행 상태 호환 | `sample-list.tsv`·`sample-scope.json` 은 표본틀의 **집합**과 seed 로 정해진다(쪽 크기 무관해야 한다 — D-3 이 test 로 잠근다). 원장 `release_sha` 가 새 jar 로 바뀌는 것은 정상 기록 |
| 게이트웨이 실측 | `numOfRows=999` → `resultCode 00`, `numOfRows 999`, `items 999`(`totalCount 1522`). `1000` 도 200 이나 문서 상한을 모르므로 **999 를 상한으로 고정** |

## 재사용 조사 (Phase 2)

| 후보 | 판정 | 근거 |
|---|---|---|
| `KonepsSourceConfig.numOfRowsPerPage` 생성자 인자 | **채택** — 배선에서 값을 준다 | adapters 무변경, 이미 주입 가능 |
| `KonepsOpeningEndpointProperties` 기본값 자리 | **채택** — `rowsPerPage` 칸 추가 | 같은 prefix 의 wire 설정이 모여 있는 자리 |
| E2E 하네스 `OpeningCollectionE2EHarness` + `MockOpeningKonepsHttp` | **채택·확장** — mock 이 요청의 `numOfRows` 를 **존중하는 모드**를 얻는다(기존 고정 2 모드는 유지, 기존 test 무변경) | 두 축 test 의 자리 |

## 결정

| ID | 결정 | 근거 |
|---|---|---|
| **D-6G2f-1** | **페이지 크기는 설정 값이다.** `KonepsOpeningEndpointProperties.rowsPerPage: Int = 999`(`bidvector.koneps.opening.rows-per-page`), `init { require(rowsPerPage in 1..999) }`. `OpeningCollectionWiring` 이 `KonepsSourceConfig(numOfRowsPerPage = opening.rowsPerPage)` 로 넘긴다. 코드에 `999` 리터럴은 **이 기본값 한 자리**뿐(상한도 같은 상수에서) | 실측 상한 999; 배선이 값을 지어내지 않고 설정이 준다 |
| **D-6G2f-2** | **(값 slice 축 ① wire)** 출하 배선으로 띄운 E2E 에서 mock 이 받은 요청의 `numOfRows` 가 개찰완료·목록·예비가격·기초금액·산식 A **다섯 축 모두 999** 임을 단언한다(요청 URI 의 query 를 mock 이 기록). 변이: 배선에서 `numOfRowsPerPage` 인자를 빼면(기본 100) RED | 지금은 어떤 test 도 numOfRows 를 재지 않는다 |
| **D-6G2f-3** | **(값 slice 축 ② 거동)** mock 「요청 존중」 모드에서 ⓐ 참가 150 행 공고: `rowsPerPage=999` → 개찰완료 HTTP **1** 호출로 정착(원장 HTTP 줄 1) · `rowsPerPage=100` → **2** 호출 ⓑ 같은 mock 모집단을 `rowsPerPage=2`(기존 모드)와 `999` 로 각각 걷어 확정한 `sample-list.tsv` **바이트가 같다**(쪽 크기가 표본을 바꾸지 않는다 — 실 디렉터리의 `sample_list_sha256` 과 호환). 변이: 걷기 결과를 쪽 순서에 의존하게 하면 ⓑ RED | 실행 상태 디렉터리를 이어 쓰므로 표본 불변이 선결 |
| **D-6G2f-4** | **`maxPages = 50` 무변경.** 의미가 「참가 5,000 초과」에서 **「참가 49,950 초과」**로 넓어진다 — 폭주 방지 문턱이지 데이터 정확성 문턱이 아니다(`MAX_PAGES` 확정 제외는 그대로 계수 공시). runbook §0 표 `OPEN-6G2D-MAX-PAGES-FINAL` 행·§5 줄의 숫자를 `50 × rows-per-page` 로 고쳐 쓴다 | 쪽 수 상한을 행 수 상한으로 바꾸는 것은 별 slice |
| **D-6G2f-5** | **공고 목록 레인(`KonepsOpenApiNoticeSource`, 100) 무변경** — **`OPEN-6G2F-NOTICE-LIST-ROWS`** 신설(일일 증분은 작아 1,000 안; 백필은 끝났다). 6G-2c 후보 | 3B 기존 test 무편집 제약 |
| **D-6G2f-6** | **`OPEN-6G2D-FRAME-REWALK` 는 닫지 않는다** — 매 기동 표본틀 재걷기는 남고 비용만 812 → 약 66. OPEN 행에 이 slice 의 효과를 적는다 | 걷기 생략은 표본 불변 검사(`sampleUnseen`)의 의미를 바꾸는 설계 물음 — 별 slice |
| **D-6G2f-7** | **runbook**: §2-2 공통 인자 뒤에 「페이지 크기 기본 999(`bidvector.koneps.opening.rows-per-page`), 게이트웨이 상한 999 실측」 한 줄 · §5 쿼터 줄에 「페이지 999 뒤 하루 약 950 공고」 갱신 · §「배포」 절차(아래) | 실수집 중 교체이므로 절차가 문서에 있어야 한다 |
| **D-6G2f-8** | **게이트 술어 확장 0 · 실행 상태 형식 version 2 유지 · 스키마·golden·contracts 무변경 · 새 public 표면: `rowsPerPage` 프로퍼티 하나**(data class 생성자 — 밖에 허락하는 것은 「1..999 안의 값 선택」뿐, 999 초과는 기동 거부) | |

## 배포 (머지 뒤, 팀장)

1. 수집 실행이 돌지 않는 창(`pgrep -af 'java -jar .*app.jar'` 빈 출력)에서, 호스트 3단 점검 **별도 호출** 뒤 `main` 체크아웃에서 `./gradlew --no-daemon :app:bootJar`.
2. 다음 재실행(cron 00:41)은 같은 경로의 jar 를 집는다 — 인자 무변경(기본 999). 원장 `release_sha` 가 새 SHA 로 바뀐다. 첫 실행 뒤 개찰완료 HTTP 줄 수 ≈ 정착 공고 수임을 evidence 표에 적는다.
3. 되돌림은 `--bidvector.koneps.opening.rows-per-page=100` 인자 하나(재빌드 없이).

## scope

```yaml
base_sha: 9a5aa26a
in_scope:
  - app/src/main/kotlin/bidvector/app/collection/OpeningCollectionProperties.kt
  - app/src/main/kotlin/bidvector/app/wiring/OpeningCollectionWiring.kt
  - app/src/test/kotlin/bidvector/app/collection/**        # E2E 하네스·mock 확장 + 새 test
  - app/src/test/kotlin/bidvector/app/wiring/**            # 배선 test 가 필요하면
  - docs/runbook/m6-6g-real-collection.md                  # §0 표 · §2-2 · §5 · 배포 절(공유 파일, hunk)
  - reports/evidence/m6/6g2f/**
  - config/quality/**                                      # baseline 갱신이 필요할 때만, 사유 선언
out_scope:
  - adapters/**  domain/**  procurement/**  contracts/**  ml-engine/**
  - app/src/main/kotlin/bidvector/app/collection/* (위 둘 외)  — 공고 목록 레인 · 실행 상태 · 원장 무변경
  - .github/workflows/**
acceptance:                                     # CI `check` job 의 명령 그대로(.github/workflows/ci.yml)
  - ./gradlew --no-daemon check
  - ./gradlew --no-daemon qualityBaseline
  - ./tools/one-command-check.sh                # 첫 명령이 check — 레인은 한 번만 돌리고 결과를 둘에 적는다
rollback:
  - git restore --source=9a5aa26a --staged --worktree -- <in_scope 경로 개별 인자>; 새 파일은 삭제
  - 공유 파일(runbook)은 이 slice 의 커밋 hunk 를 새 것부터 `git apply -R`
  - 목록은 `git diff --name-status 9a5aa26a..HEAD -- <in_scope>` 기계 산출, 라운드마다 재산출
```

## 하네스 레인 변경 (상시 절)

없음(착수 시점).

## 알려진 제한

- 한도 자체(1,000/operation/일)는 바뀌지 않는다 — 운영계정 승인이 유일한 근본 조치. 이 slice 는 2배까지다.
- 개찰완료 참가 999 초과 공고는 여전히 2쪽 이상(day1 최대 35쪽 = 약 3,500 참가 → 4쪽).
- 실행 중 디렉터리에 이미 정착한 축은 다시 부르지 않으므로, 효과는 미정착 공고(23,000 여)부터다.

## OPEN

| ID | 내용 | 자리 |
|---|---|---|
| `OPEN-6G2F-NOTICE-LIST-ROWS` | 공고 목록 레인 페이지 크기 100(3B 상수) — 일일 증분은 1,000 안이나 백필 재실행 시 1,022 호출 | 6G-2c |
| `OPEN-6G2D-FRAME-REWALK` | 매 기동 표본틀 재걷기(이 slice 뒤 약 66 호출/일) | 6G-2c |
| `OPEN-6G2F-MAX-PAGES-PROVENANCE` | (cr M-1) `MAX_PAGES` 확정 제외의 **행 수 문턱**(`50 × rows-per-page`)이 `sample-scope.json`·무결성 장부·시도 원장 어디에도 실리지 않는다 — 되돌림 인자(`--bidvector.koneps.opening.rows-per-page=100`)를 쓰면 한 디렉터리에 「>5,000」과 「>49,950」 확정이 섞여 구별되지 않는다. 임시 운용: 인자를 바꾸는 실행은 evidence 표에 날짜·값을 적는다. 닫는 길은 원장 HTTP 줄 또는 `state.json` 에 쪽 크기 등재 | 6G-2c |

## 계약 갱신 r1 (2026-10-03, 팀장 — 검토 라운드 처분)

| ID | 결정 | 근거 |
|---|---|---|
| **D-6G2f-9** | **판정 SHA `e16bf85e`: verifier `ready-for-review`(low 2) · code-reviewer high 1 / medium 2 / low 4 → 차단 없음.** 장부층·low 는 규율대로 종결 전 일괄(커밋 `61787985`·`a033e80d`·`91725b1e`), verifier 재검증 없음 | CLAUDE.md 「차단 문턱」 |
| **D-6G2f-10** | **cr H-1(999 수용 실측이 한 operation) 처분 = 운영 전제로 닫는다.** 팀장이 10-03 에 나머지 여섯 operation 을 실측(전부 `resultCode 00`, `numOfRows 999` 에코, 999 행 반환 — commands.md 「실측(팀장)」). **개찰완료만 당일 쿼터 0 이라 미실측 → 배포 전제**: 새 jar 첫 기동 전에 그 operation 에 `numOfRows=999` 실 호출 1건으로 확인하고, 거부면 `--bidvector.koneps.opening.rows-per-page=100` 으로 기동(runbook 「배포」 0항·재실행 cron 절차). D-6G-65 분류: 데이터 정확성 | 거부 시 미지 코드가 일시 실패로 분류돼 A-3 재호출 상한(디렉터리 생애)을 먹는다 — 값이 싼 사전 확인 |
| **D-6G2f-11** | vr low 2(바인딩 기동 거부 test 없음 — 생성자 호출 test 뿐) · cr L-1(999 상수 잠금 test 없음) → **등재**(checklist 알려진 제한 9·10), 코드 무변경. 분류: 게이트 하드닝 | low, verifier 가 기동 probe 로 1000·0·-5 거부와 1·999 수락을 실측함 |
| **D-6G2f-12** | cr M-1 → `OPEN-6G2F-MAX-PAGES-PROVENANCE`(위 표) · cr M-2(산식 A 도 단일 operation) → runbook §5·배포 절 문면 · vr low 1(release-sha 는 실행기 계산값, 「cron」은 세션 cron) → runbook · cr L-2(`axisOf` 순서 의존) → 겹치지 않는 접미 표 · cr L-3(KDoc) → 셋으로 · cr L-4(`milestone-6.md` 의 「5,000」) → 팀장 종결 커밋에서 「50 × rows-per-page」로 | 장부층 |
| **D-6G2f-13** | **배포(머지 뒤)**: 수집 실행이 없는 창에서 호스트 3단 점검 뒤 `main` 에서 `:app:bootJar`; 다음 재실행(세션 cron 00:41)이 D-10 의 사전 확인을 거쳐 새 jar 를 집는다. 원장 `release_sha` 는 실행기가 `git rev-parse` 로 계산한 새 SHA | runbook 「배포」 |

## 하네스 레인 변경 (상시 절) — 갱신

- **팀장(2026-10-03, 종결 커밋)**: `milestone-6.md` 의 `OPEN-6G2D-MAX-PAGES-FINAL` 「5,000」 잔존 문면 정정 + 6G-2f 종결 문단. `milestone-6.md` 는 in_scope 밖(공유 파일)이라 rollback 복원 목록에 넣지 않고 여기 선언한다 — 되돌림은 그 커밋의 hunk 역적용.

## 종결 (2026-10-03, 팀장)

판정 SHA `e16bf85e` ready-for-review → 장부층 일괄 `91725b1e` → 이 계약 갱신. 재작업 0/5. PR 은 `main` 으로 열고 `/code-review` 뒤 새 high 없으면 머지(운영자 사전 승인 2026-10-02 「후속 slice 리뷰 이상 없으면 PR·머지」). 머지 뒤 D-6G2f-13.

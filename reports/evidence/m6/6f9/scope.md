# M6/6F-9 — 업무구분 수집: 대분류(오퍼레이션) + 세부 분류(용역구분·공공조달분류·주공종) (2026-09-24)

6F-8 실수집이 드러낸 공백을 닫는다 — **업무구분이 공고 0건에 채워졌다.** 운영 필드 계약은 업무구분을 `bsnsDivNm` 에서 찾는데
공사·용역 목록 응답에는 그 키가 없다. **대분류는 오퍼레이션 자체**(공사 목록·용역 목록을 따로 부른다)라 응답에 필드가 없고,
**세부 분류는 다른 키로 응답에 있다**(실측). 그래서 「관심 업종」 감시 규칙이 어떤 공고와도 맞지 않는다.

- base: `git merge-base HEAD origin/main`(고정 SHA 아님). 착수 실측 `f6ebc047`(PR #45 6F-8 머지).
- worktree `bid-vector-v2-m6-6f9`, 브랜치 `m6-6f9/2026-09-24`.

## 운영자 지시·결정 (2026-09-24)

- 사용자: 「업무구분은 … 입찰에 필요한 정보일테니 수집 해야 맞을것 같아」 — 물품·공사(토목·건축·전기·통신·소방 …)·용역(일반용역·
  기술용역)·기타(외자·리스). 선택: **PR #45 마무리 후 6F-9 로**(추천안) — 공사 주공종 빈 값은 면허제한 수집과 함께 풀도록 OPEN.
- 이 slice 의 실 KONEPS 호출(읽기)·개발 DB 쓰기는 6F-8 운영자 결정 셋(키 주입 방식·개발 DB·공사 + 용역)을 그대로 따른다.
- **Codex 없음**(팀장 판단, 6A-3+6F-3 운영자 결정 4 와 같은 근거 — V17 은 nullable 열 추가). `migration-reviewer` + `verifier` 로 닫는다.

## 실측 (6F-8 실수집 원문, 공고 22,639건, 값이 아니라 채움률·분포)

| 대분류 | 키 | 채움 | 비고 |
|---|---|---|---|
| 공사·용역 | `bsnsDivNm`(현 계약의 업무구분 라벨 키) | **0%** | 응답에 키 자체가 없다 |
| 용역 | `srvceDivNm`(용역구분명) | 100% | 일반용역 · 기술용역 · 일반용역(리스) |
| 용역 | `pubPrcrmntClsfcNo`·`pubPrcrmntClsfcNm`(공공조달분류 세분류 번호·명) · `…LrgClsfcNm`·`…MidClsfcNm` | 거의 100% | 대분류 예: 기술용역·연구조사서비스·ICT 서비스 |
| 공사 | `mainCnsttyNm`(주공종명) | **약 33%** | 전기공사업·건축공사업·토목공사업·정보통신공사업 … |

## 결정 이력과의 정합

**P-7(2026-09-08) · COL-08 · `OPEN-COL-03` 을 지킨다 — 업무구분 축을 한 자리에 접지 않는다.** P-7 은 문서 열거(`bsnsDivNm` = 물품·용역·
공사·외자)를 승인 어휘로 올리면서 「축이 셋이다(문서 열거 · legacy 코드→라벨(용역을 일반/기술로 가름) · 낙찰정보 `bsnsDivCd`),
접지 않는다」를 명시했다. 이 slice 는 새 축을 **새 칸**에 두고 기존 칸에 섞지 않는다.

## 계약 고정 결정

**D-6F9-1 — 대분류 `BusinessDivision` 은 P-7 어휘 넷(물품·용역·공사·외자)이고, 원천은 수집 오퍼레이션이다.** 응답에 필드가 없으므로
**어느 오퍼레이션으로 수집했는가**가 대분류다. 오퍼레이션 → 대분류 매핑은 **데이터**(수집 소스 설정 또는 정책 — 선례에 맞게 구현 레인이
0단계에서 추천)이고, 관측(`RawNoticeObservation`)이 **구조로** 나른다(URL 문자열을 파싱하지 않는다). `canonicalize` 가 그 값을
`NoticeCollected.businessDivision` 으로 옮긴다(변환 지점 하나 유지). 응답에 `bsnsDivNm` 이 실제로 오면(다른 오퍼레이션) 그 값과
오퍼레이션 값이 다를 때의 규칙을 정해 test 로 잠근다(추천: 불일치는 원문 우선이 아니라 **탈락 없이 오퍼레이션 값 + 회계 계수** — 구현
레인이 선택지와 함께 0단계 보고).

**D-6F9-2 — 세부 분류는 대분류별로 다른 원천, 다른 칸이다.**
- 용역: **용역구분**(`srvceDivNm`, 원문 trim — 일반용역·기술용역 …) → 새 칸 `service_division` · **공공조달분류**(`pubPrcrmntClsfcNo`·
  `pubPrcrmntClsfcNm`) → **기존** `BusinessCategory(code, label)` 칸(코드 = 분류번호 정규화, 라벨 = 분류명). 대·중분류명은 이 slice 에서
  싣지 않는다(필요해지면 후속).
- 공사: **주공종**(`mainCnsttyNm`, 원문 trim) → 새 칸 `main_construction_type`. 코드가 응답에 없으므로 **코드를 지어내지 않는다**
  (이름만). 빈 값 67% 는 **`OPEN-6F9-CONSTRUCTION-TYPE-SOURCE`**(면허제한 오퍼레이션의 허용업종 — `OPEN-6F8-OPENING-COLLECTION` 과 함께).
- 필드 계약: 위 키들을 **계약 행**으로 등재(키 리터럴은 계약 데이터에만 — 6F-8 상수 풀 게이트 확장). 새 `FieldConcept` 토큰이 필요하면
  추가만. 정본 문서 `policy-values.md` 에 **P-16** 으로 등재(P-14·P-15 형식), `data-dictionary.md` 에 필드 정의.
- 현 계약의 `bsnsDivNm`(라벨, REQUIRED) 행은 **지우지 않는다**(다른 오퍼레이션에서 올 수 있다 — P-7). 다만 이 오퍼레이션에서 늘 부재라는
  사실을 문면에 적는다.

**D-6F9-3 — 영속: V17 로 `notice` 에 열 셋.** `business_division TEXT CHECK (business_division IS NULL OR business_division IN
('물품','용역','공사','외자'))` · `service_division TEXT` · `main_construction_type TEXT` — 전부 nullable, DEFAULT 없음(기존 행 보존,
재수집이 채운다). 되돌림: 미적용 DB 는 파일 삭제, 적용 DB 는 새 V 의 `DROP COLUMN`. 스키마 정확 열거 게이트(`CleanMigration*Test`)는
**추가만**. 쓰기·읽기 왕복(삽입·갱신·복원) test 로 세 열 전부 잠근다(V16 교훈 — `DO UPDATE SET` 누락 변이가 RED 여야 한다).
기존 행의 갱신 규칙(권위·덮어쓰기)은 기존 쓰기 규율을 따른다 — 재수집이 이 열들을 채울 때 `updated` 로 세는지 E2E 로.

**D-6F9-4 — 감시에서 쓴다.**
- `WatchSubject.categories` = 비어 있지 않은 것만 모은 집합 {대분류, 용역구분, 공공조달분류 코드, 주공종} — 각각 `CategoryCode.of`
  (정규화 규칙 하나). 운영자가 「관심 업종」에 `공사`·`기술용역`·`전기공사업`·분류번호 중 무엇을 적어도 맞는다. `NoticeWatchSubjectPort`
  (6F-4-w)는 조립 커널을 부르고 값을 원문 문자열로 넘길 뿐이다 — 이어붙이기 금지 게이트 유지.
- 키워드 매칭 텍스트의 「공종」 조각 = 세부 이름(용역: 공공조달분류명, 공사: 주공종명) — `assembleKeywordScopeText` 의 인자 의미만 바뀌고
  조립 규칙은 그대로. 요건·기관명이 섞이지 않는다(6F-4 D-6F4-3b).
- 전략 조회 응답·OpenAPI 는 바뀌지 않는다(전략 필드 무변경).

**D-6F9-5 — 실수집 확인(팀장, verifier ready-for-review 뒤).** 개발 DB 에 V17 적용 → 같은 30일 재수집: 기존 22,639건이 새 필드로 **갱신**
(`updated`)되고 새 공고 0 · 대분류 채움 100% · 용역구분·조달분류(용역) · 주공종(공사) 채움률 · 원인 이상 0 · 키 흔적 0. 평가 dry-run:
「관심 업종 = 기술용역」 전략으로 후보 중 용역구분 기술용역 + 마감 전 수가 감시 통과 수와 같은가(값 수준 대조).

## 계약 갱신 — 구현 레인 0단계 보고 수령 (2026-09-24)

0단계 보고 `_workspace/m6-6f9/01_impl_stage0.md` 의 추천을 전부 채택한다.

- **D-6F9-1 확정 — 관측 필드 A.** `RawNoticeObservation` 에 nullable 대분류 필드를 더하고, 수집 소스가 생성자 필수 인자로 받아 싣는다.
  매핑은 수집 소스 설정의 오퍼레이션 행을 (경로, 대분류) 한 행으로 중첩한다 — 미지값·누락은 기동 실패. 엔드포인트 토큰 세분화(B)·유스케이스
  주입(C)은 기각(각각 두 축을 한 토큰에 접음 · 변환 지점 이탈). 한계: 원문 행은 대분류를 저장하지 않는다(정본은 `notice`) — 알려진 제한.
- **D-6F9-1 불일치 규칙 = R1.** 대분류는 오퍼레이션 값만 쓰고 `bsnsDivNm` 은 소비하지 않는다(원문에 남음). 불일치는 탈락·덮어쓰기 없음,
  test 로 「`bsnsDivNm` 이 무엇이든 대분류 불변」을 잠근다. 회계 계수(R2)는 열 신설이 필요해 D-6F9-3 초과 — 채택하지 않고, 불일치 수는
  D-6F9-5 의 사후 질의 한 줄로 잰다.
- **D-6F9-3 보강 — CHECK.** 대분류 CHECK 에 더해 새 텍스트 열 둘에도 V14 선례의 공백 금지 CHECK 를 건다(D-6F4-8 근거). 정규식 바이트는
  선례 파일에서 프로그램으로 읽어 재사용한다(6F-4 heredoc 이스케이프 치환 교훈).
- **D-6F9-4 확정 — 조립 커널.** 감시 대상 집합 조립을 `strategy` 커널 함수로 올리고 포트는 원문 문자열만 넘긴다. 의존 게이트의 허용 메서드
  참조에서 집합 생성자가 빠져 **조여진다** — 게이트 술어 변경이므로 verifier 표적 재검증 대상.
- **키 리터럴 게이트 일반화.** 공고명 한 개념에서 **개념 집합**(공고명·분류 코드·분류명·용역구분·주공종)으로 — 게이트 술어 변경, 같은 표적.
- **D-6F9-6 신설 — ML 입력 파급은 이 slice 가 바꾸지 않고 잠근다.** 공공조달분류 번호가 기존 코드 칸으로 들어가면 용역 공고에서
  기회 분석 표본 질의·ML 요청의 업종 코드 fact·표본 업종 코드가 null → 값으로 바뀐다. 이 slice 는 ML 쪽을 바꾸지 않는다. test 로 두 가지를
  잠근다: (가) 용역 공고의 분류번호가 ML 요청 fact 에 **실린다**(wire), (나) 공사 공고는 null 을 유지한다. 알려진 제한에 등재한다.
  분류번호 공간이 legacy 학습 코드 공간과 같은지는 **`OPEN-6F9-ML-CATEGORY-CODE-SPACE`** 로 넘긴다(`OPEN-ML-ANALYSIS-WIRING` 과 함께 판단).
  verifier 는 현 평가 경로(dry-run)가 ML 을 실제로 부르는지 한 줄로 확인한다.
  **r1 계약 갱신(2026-09-25, 팀장)**: 첫 구현이 (가)·(나) test 를 두지 않았다(verifier F-2 · code-reviewer HIGH). 잠금 test 자리로
  `adapters/src/test/kotlin/bidvector/adapters/ml/**` 를 in_scope 에 더한다 — ML main 코드는 여전히 밖이다(바꾸지 않고 잠근다).
  같은 라운드에서 기회 분석 표본 질의 발동(용역 코드 존재 → 표본 조회 실행, 공사 null → 생략)도 행동 test 로 잠근다(자리는 기존 in_scope).

## 위협 모델 — 6F-9 고유 경계

지키는 것: ① 업무구분 축을 섞지 않는다(대분류·용역구분·조달분류·주공종이 각자 칸) ② 값을 지어내지 않는다(코드 없는 주공종에 코드 없음,
부재는 `null`) ③ 정규화 지점·키 리터럴 규율 유지(6F-8 게이트) ④ 기존 공고 행을 재수집이 안전하게 갱신한다(권위 규칙 위반 없이).
경계 밖: 면허제한·허용업종 수집 · 물품·외자 오퍼레이션 수집 · 업무구분 코드 전체 체계(`OPEN-COL-03`).

### 우회 — 여섯
1. 대분류를 URL·오퍼레이션 이름 문자열 파싱으로 얻는다. ← 관측이 구조로 나름 + 게이트(원시 문자열 파싱 경로 부재).
2. 세부 분류를 기존 `business_category_label` 에 섞어 넣는다(축 접기). ← 칸별 왕복 test + 용역구분이 라벨 칸에 없음 부재 단언.
3. 주공종에 코드를 지어낸다(이름을 코드 칸에). ← 공사 공고의 `business_category_code` 가 `null` 임을 단언(주공종만 있을 때).
4. 새 키 리터럴을 어댑터·use case 에 박는다. ← 6F-8 상수 풀 게이트가 새 키도 덮는지(허용 집합 = 정책 클래스).
5. V17 쓰기 경로 한 곳 누락. ← 두 번 저장·갱신 왕복 + `DO UPDATE SET` 누락 변이 RED.
6. 감시 집합에 빈 값·공백 코드가 들어가 모든 규칙과 맞거나 아무것과도 안 맞는다. ← 빈 값 제외 test(6F-8 빈 값 규칙과 일관).

### (2b) 값 획득 축 — 새 public 표면 전수(구현 레인, 라운드마다)

## in_scope (게이트·fixture·build·정책 문서·스키마 게이트를 처음부터 — 6A-3+6F-3 교훈)

```yaml
in_scope:
  - adapters/src/main/resources/db/migration/V17__notice_business_division.sql
  - procurement/src/main/kotlin/bidvector/procurement/**                # 도메인 칸·계약 행·canonicalize·관측 구조
  - procurement/src/test/kotlin/bidvector/procurement/**
  - adapters/src/main/kotlin/bidvector/adapters/koneps/**               # 관측이 오퍼레이션 대분류를 나른다
  - adapters/src/test/kotlin/bidvector/adapters/koneps/**
  - adapters/src/main/kotlin/bidvector/adapters/persistence/**          # 세 열 영속
  - adapters/src/test/kotlin/bidvector/adapters/persistence/**          # CleanMigration* 추가만 포함
  - adapters/src/main/kotlin/bidvector/adapters/evaluation/**           # NoticeWatchSubjectPort
  - adapters/src/test/kotlin/bidvector/adapters/evaluation/**
  - strategy/src/main/kotlin/bidvector/strategy/**                      # WatchSubject 집합 조립이 거기 있으면
  - strategy/src/test/kotlin/bidvector/strategy/**
  - workflow/src/main/kotlin/bidvector/workflow/collection/**           # 수집 소스 → 대분류 배선이 거기 있으면
  - workflow/src/test/kotlin/bidvector/workflow/**
  - app/src/main/kotlin/bidvector/app/collection/**                     # 소스 설정 매핑표
  - app/src/main/kotlin/bidvector/app/wiring/**
  - app/src/test/kotlin/bidvector/app/**
  - app/src/test/kotlin/bidvector/archfixture/violating/**
  - adapters/src/test/kotlin/bidvector/adapters/ml/**                   # D-6F9-6 잠금 test 자리(수정 라운드 r1 추가 — main 은 밖 그대로)
  - config/quality/gate-tests.properties                                # 공유 — 추가만
  - config/quality/architecture-policy.properties                       # 공유 — 추가만
  - config/quality/member-effects*.properties
  - docs/discovery/data-dictionary.md                                   # 공유 — 필드 정의
  - reports/evidence/m3/3a/policy-values.md                             # P-16
  - fixtures/**                                                         # corpus 기대값이 새 필드에 닿으면 — 근거 기록
  - expected/**
  - reports/evidence/m6/6f9/**
  - milestone-6.md                                                      # 착수·종결 문단(팀장) — 공유
out_of_scope:
  - ml-engine/**
  - 면허제한·허용업종·물품·외자 수집
  - 전략 필드·OpenAPI 변경
```

## acceptance

CI job 명령 그대로(버릴 worktree, `--rerun-tasks` 한 번): Kotlin `check` · `qualityBaseline` · `one-command-check.sh`. 변이(구현 레인):
대분류 매핑 제거 · 용역구분을 라벨 칸에 섞기 · 주공종을 코드 칸에 · V17 `DO UPDATE SET` 누락 · 감시 집합에 빈 값 · 키 리터럴 어댑터 삽입 —
전부 RED. D-6F9-5 실수집 확인(팀장).

## rollback

in_scope 경로 한정 restore(`<base>` = merge-base), 목록 기계 산출. 공유 파일(`gate-tests`·`architecture-policy`·`data-dictionary`·
`policy-values`·`milestone-6.md`)은 커밋 해시 hunk 격리. V17 두 갈래. 임시 worktree ①~⑥, `실측 HEAD`.

## 하네스 레인 변경 (상시 절)

착수 시점 없음.

## OPEN — 수령·신설

| ID | 방향 | 내용 |
|---|---|---|
| `OPEN-6F8-BUSINESS-CATEGORY-SOURCE` | **수령·닫음** | D-6F9-1~4 |
| `OPEN-COL-03` | 변경 없음 | 업무구분 코드 전체 체계 — 이 slice 는 새 축을 새 칸에 둘 뿐 통합하지 않는다 |
| `OPEN-6F9-CONSTRUCTION-TYPE-SOURCE` | **신설** | 공사 주공종 67% 공백 — 면허제한 오퍼레이션 허용업종으로(`OPEN-6F8-OPENING-COLLECTION` 과 함께) |
| `OPEN-6F9-GOODS-FOREIGN-COLLECTION` | **신설** | 물품·외자 오퍼레이션 수집(대분류 매핑은 이 slice 가 넷 다 받는다) |
| `OPEN-6F9-ML-CATEGORY-CODE-SPACE` | **신설** | 용역 공고의 업종 코드가 공공조달분류 번호가 된다 — legacy 학습 코드 공간과의 일치 여부(D-6F9-6) |

## 리뷰 레인

`verifier`(opus) + `code-reviewer`(**`model: sonnet` 명시**) + **`migration-reviewer`**(V17) — 전용 정의가 없으면 범용 에이전트 대행.
privacy: 새 필드는 분류 어휘라 개인정보 아님 — 해당 없음(verifier 가 한 줄 확인). contract-keeper: 공개 HTTP 계약 무변경 — 해당 없음.
**Codex 없음.** 종결은 `verifier ready-for-review` + D-6F9-5 + 사용자 승인.

# M6/6F-9 checklist — 업무구분 수집(대분류 = 오퍼레이션, 세부 = 용역구분·공공조달분류·주공종)

정본 계약은 `scope.md`. 이 문서는 계약을 **구현이 어떻게 닫았는가**의 대응표, 알려진 제한, 팀장이 실수집 확인에서 볼 SQL이다. 개수·SHA 는 적지 않는다 — 명령이 낸다(`commands.md`).

## 1. 위협 모델 우회 여섯 ↔ 닫는 장치

| # | 우회(scope.md) | 닫는 장치 | 잠금 test·게이트 |
|---|---|---|---|
| 1 | 대분류를 URL·오퍼레이션 이름 문자열 파싱으로 얻는다 | 대분류는 **구조**로 흐른다: 설정 표 한 행(경로 + 대분류, 누락·어휘 밖은 기동 실패) → 어댑터 **필수** 생성 인자 → `RawNoticeObservation.sourceDivision` → `canonicalize`. 문자열에서 대분류를 만드는 표면(`fromLabel`·`valueOf`·`values`·`getEntries`)의 (호출자->멤버) 쌍 = {영속 라벨 복원, 정책 어휘 파생}이고 관측 쌍과 같다 — **컴파일된 호출 그래프**라 어떤 스타일로 써도 쌍이 바뀐다 | `KonepsSourceDivisionTest`(경로가 용역이어도 인자 값이 이김·응답 라벨 무시) · `CollectionWiringTest`(표·기동 실패) · `CollectionArchitectureGateTest`(호출 쌍 둘) + `RogueDivisionFromString` 음성 · 변이 M1a·M1b·M1c |
| 2 | 세부 분류를 `business_category_label` 에 섞는다(축 접기) | 용역구분은 자기 개념·자기 열. 업무구분 코드·라벨은 **같은 원천 쌍**에서만 읽는다(`CategorySource` 쌍 표 — 다른 쌍의 라벨을 빌리지 않음) | `BusinessClassificationCanonicalizeTest`(라벨 = 분류명뿐 · 쌍 격리) · `NoticeBusinessClassificationPersistenceTest`(열별 왕복, 용역구분 ≠ 라벨 열) · E2E(`service_division IN (라벨, 코드)` = 0) · 변이 M2 |
| 3 | 주공종에 코드를 지어낸다 | 주공종은 `MAIN_CONSTRUCTION_TYPE` 개념·자기 열만. 코드 쌍은 분류 번호 계약에서만 코드를 읽는다 | canonicalize test(공사 항목 `businessCategory` = null) · 영속 test(`find` 복원도 null) · E2E(공사 공고 코드·라벨 열 = 0) · 변이 M3 |
| 4 | 새 키 리터럴을 어댑터·use case 에 박는다 | 6F-8 게이트를 **개념 집합**으로 확장(`collection.classification-key.*` — 개념 이름만 정책 파일에, 키 문자열은 게이트가 필드 계약에서 읽음). 허용 클래스 = 계약 행 파일 클래스 하나, 관측과 같고, 허용 클래스가 상수 풀에 가진 운영 계약 키의 개념은 전부 집합에 든다(새 계약 행이 게이트 밖에 남지 않음) | `CollectionArchitectureGateTest` 셋 · `CollectionArchitectureGateCatchesViolationsTest`(키마다) · 변이 M6 |
| 5 | V17 쓰기 경로 한 곳 누락 | INSERT/UPDATE/`NOTICE_COLUMNS`/바인딩/`NoticeRow`/병합/복원 전 구간. 왕복은 **열마다 따로**(칸 하나만 실린 명령이 그 열만 채움 → 바인딩 밀림·SET 누락이 RED), 병합은 존재 가드 축별 함수(복잡도 한도 안), CHECK 는 개수가 아니라 본문 | `NoticeBusinessClassificationPersistenceTest` · `CleanMigrationColumnTest`·`CleanMigrationCheckTest`(본문 고정) · `NoticeReconstructionTest` · `JdbcCandidateSourceTest` · 변이 M4a·M4b·M4c |
| 6 | 감시 집합에 빈 값·공백 코드가 들어간다 | 집합 조립을 `strategy` 커널로 올림 — 어댑터는 원문 문자열만 넘긴다(허용 참조 집합에서 `setOf`·`emptySet`·`CategoryCode` 생성자가 **빠져 조여짐**). 커널이 trim·빈 값 제외 | `WatchCategoriesAssemblyTest`(경계 표본 전수 · 규칙 행동) · `NoticeWatchSubjectPortTest` · `EvaluationAdapterDependencyTest` · E2E(빈 값 행 = 대분류 하나뿐) · 변이 M5 |

거동 확인(D-6F9-3): 새 열이 없던 시절의 행을 흉내 낸 뒤 같은 범위 재수집 — 슬롯마다 `inserted=0`·`updated=행 수`·`unchanged=0`, 새 공고 0, 열 전부 다시 채워짐(`CollectionRunnerE2ETest`, Testcontainers + mock KONEPS, 실호출 0).

## 2. (2b) 값 획득 축 — 새 public 표면 전수

| 표면 | 값을 얻는가 / 새로 낳는가 | 판정 |
|---|---|---|
| `BusinessDivision`(enum) · `fromLabel` | 문자열 → 대분류 | 닫는다 — 호출 쌍 게이트(위 1행). 허용은 영속 복원·정책 어휘 파생 둘 |
| `ServiceDivision.of` · `MainConstructionType.of` | 원문 → 이름 값(trim, 빈 값 `null`) | 경계로 처리 — `NoticeTitle.of` 와 같은 부류의 공개 팩토리. 원문 **획득**이 아니라 이미 얻은 문자열의 포장이다(획득은 계약 경유 `valueOf` 뿐, 아래) |
| `FieldConcept` 새 토큰 넷 · `KONEPS_CLASSIFICATION_FIELD_ROWS`(`internal`) | 원시 키의 유일한 자리(데이터) | 닫는다 — 상수 풀 게이트 · 소진 `when` 파급 0 · 원문 접근 타입 게이트가 토큰 이름 붙이기를 이미 막는다(`FieldConcept` 는 접근 타입) |
| `RawNoticeObservation.sourceDivision` · `of`/`ofRawValues` 새 인자 | 관측이 나르는 대분류 | 닫는다 — 수집 use case 는 통과 전용 타입(멤버 접근 금지)이라 `getSourceDivision` 도 걸린다. 값은 계약 열람 규칙과 무관한 출처 정보 |
| `NoticeCollected`·`Notice` 새 슬롯 셋 | 도메인 fact 읽기 | 경계로 처리 — `title` 슬롯과 같은 부류. `Notice` 는 모듈 밖 `copy()` 불가(기존) |
| `assembleWatchCategories`(strategy, public) | 문자열 → 관심 업종 집합 | 경계로 처리 — 호출자 집합은 구조로 잠기지 않았다(`assembleKeywordScopeText` 와 같은 한계, 알려진 제한 8). production 호출자는 포트 하나(명령: `commands.md`) |
| `KonepsOperationProperties` · `KonepsEndpointProperties.operations` | 비밀 아닌 설정(경로 + 대분류) | 경계로 처리 — 생성자는 기본 표와 바인더뿐(명령: `commands.md`), 누락·어휘 밖은 기동 실패 |
| `KonepsOpenApiNoticeSource` 새 필수 인자 · `noticeListItemMapper`(`internal`) | 대분류를 관측에 싣는 자리 | 닫는다 — 기본값 없음(잊으면 컴파일 실패), 다른 오퍼레이션은 `null` **명시** |
| `notice` 새 열 셋 | 저장 표현 | 닫는다 — CHECK 본문 고정 · 왕복 test |
| `object`/companion 주입 자리 | — | 새로 생긴 것 없음(`CATEGORY_SOURCES` 는 파일 private 값) |

## 3. 새 파일 ↔ in_scope 대조 (A·M 둘 다)

`in_scope` 밖 경로 **0** — 판별 명령은 `commands.md`(`git diff --name-status <base>..HEAD` 의 각 경로를 `scope.md` `in_scope` 항목에 대조).
신규(A): `V17` 마이그레이션 · procurement main 셋(`BusinessDivision`·`BusinessClassification`·`KonepsClassificationFieldContracts`)+test 둘 · strategy main `WatchCategories`+test 하나 · adapters test 둘(영속·koneps) ·
app 게이트 표본 둘(`archfixture/violating`)·evidence. 변경(M): procurement(정책·계약·정규화·관측·fact) · adapters koneps/persistence/evaluation main·test · app collection·wiring main·test · 게이트 셋(`gate-tests`·`architecture-policy` 추가만) ·
`docs/discovery/data-dictionary.md`·`policy-values.md`(P-16).
**쓰지 않은 in_scope 항목**: `workflow/**`(수집 use case 는 대분류·세부 분류를 모른다 — 관측이 나른다, 소스 → 대분류 배선은 app 설정) · `fixtures/**`·`expected/**`(corpus 기대값이 새 필드에 안 닿음 — 아래 4항) ·
`member-effects*`(효과 도출 변동 없음) · `milestone-6.md`(팀장 레인).

## 4. fixture·정책 version 근거

- corpus: conformance 실행자는 필드를 골라 낸다(`NoticeCollected` 전체 직렬화 없음) — 새 슬롯이 기대 JSON 에 닿지 않는다. 문서 열거 어휘 기대(case 016)는 `BusinessDivision.entries` 파생이라 값이 같다. `SharedKernelCorpusConformanceTest` 가 Kotlin `check` 안에서 초록(`commands.md`).
- 정책 version: `KONEPS_COLLECTION_POLICY` 는 `EffectiveFrom.Initial` 단일 항목 그대로 — 필드 계약 행 넷 추가는 P-16(운영자 지시 2026-09-24). 층은 `observed`(6F-8 실수집 원문 채움률)이고 문서 표와는 대조하지 않았다 — 그래서 계약은 전부 옵션이다.

## 5. 빈 값 처리 표 (D-6F8-11 일관 — 값이 없거나 공백뿐이면 부재, 항목은 탈락하지 않는다)

| 필드(개념) | 소비 자리 | 빈·공백 값 | 잠금 |
|---|---|---|---|
| 대분류 | 관측 인자(`BusinessDivision?`) | 해당 없음 — 필드가 아니라 타입 값(문자열이 아니다) | 타입 |
| 용역구분(`SERVICE_DIVISION`) | `ServiceDivision.of` | `null` | canonicalize·`BusinessDivisionTest`·E2E · 계약 전수 × 공백 표(`CanonicalizeBlankValuesTest`, 새 행 자동 포함) |
| 주공종(`MAIN_CONSTRUCTION_TYPE`) | `MainConstructionType.of` | `null` | 같음 |
| 분류 번호(`…CLASS_CODE`) | 업무구분 코드 | 코드 없음 → **업무구분 자체가 없음**(라벨이 있어도 만들지 않는다) | canonicalize test |
| 분류명(`…CLASS_NAME`) | 업무구분 라벨 | 라벨 `null`(코드는 산다) | 같음 |
| 감시 집합 | 커널 | 어느 자리의 빈·공백도 원소가 되지 않는다 | `WatchCategoriesAssemblyTest` |
| DB | V17 CHECK | 용역구분·주공종 공백류 거부(V14 와 같은 클래스), 대분류는 어휘 넷만 | `CleanMigrationCheckTest` · 영속 test |

## 6. 알려진 제한

1. **ML 경로 파급(인지 — 결정 요청 아님)**: `business_category_code` 는 ML 경로의 입력이다(요청 `category_code` fact · 표본 조회 `WHERE business_category_code = ?` · 표본 `categoryCode`). D-6F9-2 대로 공공조달분류 번호를 이 칸에 넣으면
   용역 공고에서 그 입력이 처음으로 생긴다(이전엔 늘 `null` → 표본 조회 생략·`UNKNOWN`). 분류 번호(세분류)는 legacy 학습 코드 공간과 다를 수 있고 표본 조회는 세분류 정확 일치라 좁다. 공사는 `null` 그대로.
   이 slice 는 ML 쪽(`RequestMapping`·표본 조회 — in_scope 밖)을 바꾸지 않았다. 입력에서 이 칸을 분리하려면 별도 결정이다. `data-dictionary.md` §6.3.4 에도 적었다.
2. **`raw_observation` 은 대분류를 저장하지 않는다**(열 신설은 V17 범위 밖). `notice.business_division` 이 정본이고 원문 행에는 오퍼레이션 정보가 없다. raw 에서 정규화를 재생하는 경로는 현재 코드에 없다(명령: `commands.md`).
3. **응답 `bsnsDivNm` 불일치는 계수하지 않는다**(선택 R1): 소비하지 않고 탈락·덮어쓰기도 없다. 회계 계수(R2)는 `collection_run` 열 신설이라 D-6F9-3(열 셋)을 넘는다. 사후 측정은 SQL 한 줄(아래 7항 ⑥) — 오늘 두 오퍼레이션에서 그 키 존재율은 0 이다.
4. 공사 주공종은 응답의 약 33% 만 채워진다 — 나머지는 `OPEN-6F9-CONSTRUCTION-TYPE-SOURCE`(면허제한 오퍼레이션의 허용업종). 공사 공고의 업무구분 코드·라벨은 **항상 `null`** 이다(코드를 지어내지 않는다).
5. 물품·외자는 어휘만 받는다 — 설정 표 기본 행은 공사·용역 둘이고 두 오퍼레이션 경로는 검증하지 못해 짓지 않았다(`OPEN-6F9-GOODS-FOREIGN-COLLECTION`).
6. 설정 표 형태가 바뀌었다: `operations.<업종>=<경로>` 한 줄 → `operations.<업종>.path`·`.division`. 기본 표를 쓰는 실행(`categories=construction,service`)은 영향이 없다 — 표에 새 업종을 더하던 유일한 사용처(`CollectionWiringTest`)를 고쳤다.
7. 키 리터럴 게이트는 **보조** 잠금이다(6F-8 과 같음) — 컴파일 상수만 본다. 대분류 변환 게이트는 `entries` 를 순회해 라벨을 비교하는 코드가 **허용 클래스(정책 파일 클래스) 안에서** 쓰이면 못 본다.
8. `WatchSubject` 생성자가 공개라 커널을 거치지 않고 집합을 직접 지을 수 있다 — 호출자 집합을 구조로 잠그는 일은 `OPEN-6F4W-ASSEMBLE-CALLER` 로 이어진다(이 slice 는 어댑터의 참조 집합을 **조였다**: `setOf`·`emptySet`·`CategoryCode` 생성자 제거).
9. 응답 shape 는 6F-8 실측 채움률을 옮긴 합성 fixture(용역 응답에도 `bsnsDivNm=공사` 를 싣는 표본 포함)다 — **실 KONEPS 미검증**이다(D-6F9-5 실수집이 판정). 분류 번호 자릿수·문자 종류는 관측하지 않았다(정규화 = trim+소문자, 제로패딩 보존).
10. 이 slice 가 편집한 승인 문서의 `file:line` 인용 역방향 파급은 새 인용을 쓰지 않았다 — 명령: `commands.md`.

## 7. 실수집 확인에서 팀장이 볼 SQL (D-6F9-5 — 건수만, 값은 보지 않는다)

전제: 개발 DB 에 V17 적용 → 같은 30일 재수집(러너 슬롯 줄에서 `inserted=0`·`updated=행 수` 확인).

```sql
-- ① 새 공고 0 · 행 수(재수집 전후 비교)
SELECT COUNT(*) FROM notice;
-- ② 대분류 채움 100% (NULL 행 없음)
SELECT business_division, COUNT(*) FROM notice GROUP BY 1 ORDER BY 1;
-- ③ 용역: 용역구분 · 분류 번호 · 분류명 채움
SELECT COUNT(*) AS total, COUNT(service_division) AS svc_div, COUNT(business_category_code) AS class_code,
       COUNT(business_category_label) AS class_name FROM notice WHERE business_division = '용역';
-- ④ 공사: 주공종 채움 (나머지는 OPEN-6F9-CONSTRUCTION-TYPE-SOURCE)
SELECT COUNT(*) AS total, COUNT(main_construction_type) AS main_type FROM notice WHERE business_division = '공사';
-- ⑤ 축 섞임 이상 0 — 세 값 모두 0 이어야 한다
SELECT
  (SELECT COUNT(*) FROM notice WHERE business_division = '공사'
     AND (business_category_code IS NOT NULL OR business_category_label IS NOT NULL OR service_division IS NOT NULL)) AS construction_leak,
  (SELECT COUNT(*) FROM notice WHERE business_division = '용역' AND main_construction_type IS NOT NULL) AS service_leak,
  (SELECT COUNT(*) FROM notice WHERE service_division IN (business_category_label, business_category_code)) AS folded;
-- ⑥ 응답 bsnsDivNm 존재율 · 대분류와 다른 건수 (R1 의 사후 측정)
SELECT COUNT(*) FILTER (WHERE r.payload_fields ? 'bsnsDivNm') AS present,
       COUNT(*) FILTER (WHERE r.payload_fields->>'bsnsDivNm' <> n.business_division) AS mismatch
FROM notice n JOIN raw_observation r ON r.observation_key = n.observation_key;
-- ⑦ 빈 문자열 행 0
SELECT COUNT(*) FROM notice WHERE service_division = '' OR main_construction_type = '' OR business_category_code = '';
-- ⑧ 평가 dry-run 대조: 「관심 업종 = 기술용역」 전략의 감시 통과 수 ↔ 아래 기대 수(값 수준 대조, :now 는 dry-run 시각)
SELECT COUNT(*) FROM notice WHERE service_division = '기술용역' AND status IN ('Open', 'Renoticed') AND deadline_at > :now;
```

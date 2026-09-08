# 마일스톤 3 — KONEPS 수집·정규화·저장 경계

## 목표

외부 KONEPS 형식을 domain object와 분리하는 anti-corruption layer를 구현한다. 저비용 정형
수집을 먼저 수행하고, 필요할 때만 비정형 문서/LLM 추출을 호출한다.

## 선행 조건

- M1 domain 승인
- M0 data dictionary와 M2 계약 승인
- `data-extract.md`의 KONEPS fixture 준비

## 구현 대상

### Slice 3A — 수집 port와 canonical fact

- `NoticeSourcePort`, `OpeningResultSourcePort`, `DocumentSourcePort`
- 외부 DTO와 domain command 분리
- 원문 field, canonical value, provenance, observed-at 보존
- 공고 식별자 canonicalization과 수정 version

**M3 착수·3A 착수 2026-09-07** — 선행 조건 충족(M1 승인 `040ab9d`, M2 완료 `a9f1ff9`, `data-dictionary.md`·M2 계약 승인). KONEPS fixture 는
`koneps-collection` 9 case 가 전부 `insufficient-evidence` 라 **curator 레인이 별도 세션에서 승격·신설**한다(D-M3-8 (a)) — 3A 도메인 코드는 그 전에
시작하되 conformance dispatch 는 승인 뒤 병합. 준비 정본 `reports/evidence/m3/prep/m3-prep.md`(slice 지도·D-M3-1~8 전부 추천안·병렬 레인 셋),
3A 계약 `reports/evidence/m3/3a/scope.md`. 착수 전 결정: **D-3A-0 (a)** shared-kernel 좁은 확장 — `NoticeRound`(제로패딩 문자열 값 객체) 신설,
`Provenance.Published(noticeRevision: NoticeRound)`·`FloorRateOrigin.NoticeValue` 타입 교체, app executor 의 `toInt` 접힘 정정(fixture 는 이미
`"000"` 문자열 — R-QUAL-05 형태) · **D-3A-1 (a)** 수집 fact 셋(`Notice`·`OpeningResult`·`QualificationText`)을 `NoticeId` 로 묶음(§2.1 소유권과의
정합은 `OPEN-3A-AGGREGATE`) · **D-3A-2 (a)** 정책 값은 curator 가 legacy `field_contract_spec.py`·조달청 참고자료에서 추출 + 운영자 승인, 3A 는 형태
+ test 인스턴스, 운영 값 인스턴스(Kotlin `EffectiveDatedPolicy`, 1C 관례)는 승인 뒤 한 커밋. Phase 2.5 설계 검토는 세션 모델이 직접 한다.

**3A 종결 2026-09-07** — verifier r3 ready-for-review + 사용자 승인(`reports/evidence/m3/3a/checklist.md` 「사용자 승인」, 최종 head `c9d7563`).
산출: shared-kernel `NoticeRound`(`Published`·`FloorRateOrigin.NoticeValue` 타입 교체, app executor 의 `toInt` 접힘 제거), `procurement` 도메인(식별자·
원문 관측 `RawValue`·필드 계약 레지스트리 열셋 슬롯·`canonicalize`/`resolveAmount` 한 지점·`ResolvedBaseAmount`·회계 항등식·전이표·조회 가치 술어·
port 셋·수집 fact 셋·시각 해석·§5.5 수집 형태), 승인 정책 값 인스턴스, `koneps-collection` 27/27 dispatch. 검증이 드러낸 것: 미발견 property test
(하네스 `testShapeGate` 첫 실전), `of()` 팩토리의 조립 폐쇄 미완, corpus 가 잡은 v2-defect 7(그중 026 은 KONEPS 실제 시각 형식에서 파서가 항상
null). port 는 `suspend` 없이 동기 시그니처(`Continuation` 이 domain 허용 밖) — 취소·deadline 전파는 3B 어댑터. 후속: 016 업무구분 열거값의 정책 표
P-7 등재(curator).

### Slice 3B — OpenAPI adapter

- 비동기 HTTP client, timeout, quota, bounded retry/backoff
- result code/envelope validation
- pagination, partial response, duplicate item 처리
- contract mock server test
- 숫자/날짜/encoding parse failure를 명시적 결과로 반환

**3B 착수 2026-09-07** — 3A 종결 같은 날. 계약 정본 `reports/evidence/m3/3b/scope.md`(base `c9d7563`). 착수 전 결정: **D-3B-1 (a)** 골든 응답의 정본은
`fixtures/input/koneps/**`(curator 관리), test 는 경로 참조(1B-c 관례, 사본 금지) · D-M3-1 (a) JDK `HttpClient` + 코루틴 · D-M3-2 (c) JDK `HttpServer` mock 1차 ·
D-M3-5 (a) 키 variant 불채택 + rate limit 정책값. 문서 레인이 미리 등재한 **D-3B-6**: 개찰·예비가격 축(`OpeningResultSourcePort` 구현)은 ScsbidInfoService
참고자료 확보를 선행 조건으로 두고 **공고 축(`NoticeSourcePort` 구현)부터 진행** · **D-3B-7**: resultCode `03`(No Data) 은 성공도 실패도 아닌 세 번째 상태,
quota 는 HTTP 429 뿐 아니라 `resultCode 22·30`(키 미인코딩) 도 신호. 3A 의 port 가 동기 시그니처이므로 3B 어댑터가 timeout·취소·deadline 을 자기
경계 안에서 처리하고 port 계약은 그대로 구현한다. 설계 노트 `_workspace/m3-3b/01_design-review.md`(세션 모델 직접).

**3B(공고 축) 종결 2026-09-07 · 3B-2 분할** — verifier r2 ready-for-review + 사용자 승인(`reports/evidence/m3/3b/checklist.md` 「사용자 승인」, 최종 head
`01ecbba`). 산출: `KonepsOpenApiNoticeSource`(동기 facade, JDK HttpClient + Resilience4j 한 계층), HTTP 정책 데이터, envelope·resultCode 분류(`03` 세 번째
상태·`22`/`30`), pagination 백스톱·재개 커서, 원문 무변환 mapper, 자체 JSON 파서(깊이 상한 정책값), 서비스 키 값 객체, JDK HttpServer mock 시나리오 23 test.
검증이 드러낸 것: 공유 파일(`gate-tests.properties`) 의 경로 한정 rollback 이 다른 레인 등재를 되돌림(절차 예외 기록), quota 회계가 3A 타입에 없어 **3A
`CollectionAccounting` 좁은 확장**(운영자 결정, 추가만: truncation 사유·`quotaExceeded`·`backoffSkipped`). **분할**: 개찰·예비가격 축(`OpeningResultSourcePort`·
표적조회·license-limit 서브콜, scope ⑧)은 ScsbidInfoService 참고자료가 저장소에 없어 **3B-2 별도 slice** 로 — 자료 확보 → curator 필드 계약 추가 → 3B-2 계약 →
구현(D-3B-6 귀결). 3A 후속 셋(드롭 사유·`InputError`·quota 표면 구분)은 3B checklist 알려진 제한.

### Slice 3C — 문서/LLM extraction adapter

- M1 `OperatorStrategy.matches` 통과 후에만 실행
- provider/model을 config로 주입하고 domain에서 분리
- JSON Schema 기반 structured result
- chunk와 호출 예산 제한
- timeout/circuit breaker
- 원문과 추출값의 provenance, schema/model version 기록
- 면허를 확정할 근거가 부족하면 `Uncertain`

특정 모델 이름을 코드에 고정하지 않는다. 테스트는 fake LLM server만 사용한다.

**3C 착수 2026-09-08** — 3D 는 verifier r3 ready-for-review 뒤 P-1(가드 라벨 컬럼) 수정·r4 표적 중이며 경로가 분리돼 병행한다. 계약 정본
`reports/evidence/m3/3c/scope.md`(base `2e31d4e`). 착수 전 결정: **D-3C-1 (a)** procurement 소유 port — 착수 시 정정으로 **두 파일**(첨부 취득
`AttachmentDocumentPort`·추출 `RequirementExtractionPort`; 3A `DocumentSourcePort` 는 KONEPS 자격 원문 서브콜이라 3B-2 소관) · **D-3C-2 (a)**
`networknt json-schema-validator` · **D-3C-4 (a)** PDF 텍스트 층 + 플레인 텍스트만, 그 밖은 `Uncertain` + 회계 · D-M3-6 (a) fake LLM 만. 세션 모델 계약 고정:
**D-3C-6** 「`matches` 통과 뒤에만」 게이트는 adapters 층 공개 진입점(`WatchGatedExtractor`, `WatchVerdict.Passed` 외 호출 0)에 선다 — 같은 층 도메인 모듈끼리
참조 불가(ADR 0006 D-4)라 procurement port 가 1E 타입을 받을 수 없다 · **D-3C-7** prompt 는 adapters 리소스 파일(파일명 = version, 스키마와 짝). 1C 어휘
(`RequirementRow`·`UncertainReason`) 변환은 adapters 한 함수, 호출은 M4 4B. Phase 2.5 필수(fail-open) — 세션 모델 직접 `_workspace/m3-3c/01_design-review.md`.

**3C 종결 2026-09-08** — verifier r2 ready-for-review + 사용자 승인(`reports/evidence/m3/3c/commands.md` 「사용자 승인」, 최종 head `16b7748`). 산출: procurement
첨부 취득·추출 port, adapters `WatchGatedExtractor`(유일 공개 진입점 — public 진입점 46 전수 분류로 게이트 우회 0)·internal 추출 엔진·생성자 주입 LLM client
(코드 상수 0)·정책 데이터·Resilience4j 한 계층(재시도 없음)·networknt JSON Schema·pdfbox·provenance·원문 비로깅·1C 변환 한 함수, fake 서버 test 45. 검증이 드러낸
것: 게이트 없는 public factory·port 어댑터(blocker), HttpClient 와 TimeLimiter 의 같은 시한 경합(flaky) — 시한을 정책 값 둘로 분리하고 관계 불변식. 설계 검토의
오류 하나(procurement `internal` 을 adapters 가 호출할 수 없음)는 공개 타입 + 불변식 + 제어 흐름으로 대체하고 4B 배선 리뷰 항목으로. OPEN 둘 §14.3.

### Slice 3D — persistence adapter

- Flyway migration과 Testcontainers PostgreSQL test
- raw observation과 canonical fact 분리
- 같은 공고 재수집의 멱등 upsert/versioning
- 파생값이 공식값을 조용히 덮지 못하는 precedence rule
- 원본, 판정, 오류를 감사 가능하게 보존

**3D 착수 2026-09-07** — 3B 종결 같은 날(순서 3A → 3B → 3D → 3C, 3B-2 는 자료 뒤). 계약 정본 `reports/evidence/m3/3d/scope.md`(base `01ecbba`). 착수 전
결정: **D-3D-1 (a)** JDBC 직접 + 작은 mapper(신규 의존은 드라이버·Flyway·Testcontainers 만) · **D-3D-2 (a)** 점유 가드의 DB 측 실물은 `BEFORE UPDATE` 트리거 +
`provenance_authority` 표(전 금액 축, 3A `isAuthoritative` 데이터의 DB 사본) · D-M3-7 (a) Testcontainers 로컬 실측(Docker 29.5.3), Docker 부재는 붉게. 계약 고정
D-3D-3~5(문자열 키·항목 단위 트랜잭션·NULL 부재). 3A 가 저장 port 를 정의하지 않았으므로 도메인 소유 repository port 파일을 `procurement` 에 둔다(ADR 0005
D-10.1, in_scope 조건 충족). **Phase 2.5 필수**(스키마 precedence) — 세션 모델 직접, `_workspace/m3-3d/01_design-review.md`.

**3D 종결 2026-09-08** — verifier r4 ready-for-review + 사용자 승인(`reports/evidence/m3/3d/checklist.md` 「사용자 승인」, 최종 head `a2e12e3`). 산출: Flyway V1~V3(raw/
canonical/audit/collection_run, 점유 가드 트리거 + `provenance_authority` 표, raw·audit append-only 두 겹, SECURITY DEFINER audit), 도메인 소유 저장 port 다섯 + `ObservationKey`
(SHA-256), JDBC 직접 repository, Testcontainers PostgreSQL 통합 test 59. 검증이 드러낸 것: 값만 바꾸는 직접 SQL 통과(r1) → 그 수정이 provenance 강등을 통과시킴(r2 회귀) →
라벨 컬럼 미선언(r3·r4) — 가드 술어를 네 번 재작성했고 V-1(opening_result 라벨)은 후속 소폭으로 등재. **원문 전체 보존(F-7)**은 운영자 결정으로 3A·3B 좁은 확장
(`sourceText`)까지 닿았다. Docker 부재 시 붉음은 두 레인 다 라이브 재현 실패(소켓 심링크) — 코드 판독으로 skip 경로 부재 확정, 알려진 제한.

## 완료 조건

- fixture 기반 OpenAPI/문서 입력 전체가 명세대로 정규화
- malformed/partial/unknown field가 성공 데이터로 오염되지 않음
- watch rule 탈락 case에서 LLM port 호출 횟수 0
- retry 후에도 동일 공고의 canonical effect가 하나
- derived fact가 authoritative fact를 덮는 mutation이 실패
- 실제 네트워크·실제 LLM·운영 DB 없이 integration suite 통과
- KONEPS 장애가 transaction을 반쯤 commit하지 않음

**M3 완료 2026-09-08(운영자 결정) — 3B-2 잔여 등재** — 일곱 조건 전부 충족: fixture 입력 전체 정규화(3A corpus 27/27 authoritative) · malformed/partial/unknown
오염 없음(3A 계약 레지스트리 + 3B parse 실패 값 + 3D 가드) · watch rule 탈락 case LLM 호출 0(3C S-3) · retry 후 canonical effect 하나(3B 재시도 중복 삽입 없음 +
3D `observation_key` UNIQUE) · derived 가 authoritative 를 덮는 mutation 실패(3D 트리거, 직접 SQL 실측) · 실제 네트워크·LLM·운영 DB 없이 integration suite 통과
(mock server·fake LLM·Testcontainers) · KONEPS 장애가 반쯤 commit 없음(3D 항목 트랜잭션, 연결 단절 실측). **잔여 slice 3B-2**(개찰·예비가격 축 —
`OpeningResultSourcePort`·표적조회·license-limit 서브콜): ScsbidInfoService 참고자료 확보 → curator 필드 계약 추가 → 3B-2 계약 → 구현. M4 착수와 병행 가능하되
4B 가 개찰 fact 를 소비하기 전까지 닫는다. 후속 소폭: 3D V-1(opening_result 라벨 가드), 3A 후속 셋(3B checklist), `OPEN-3C-ATTACHMENT-FIELD-CONTRACT`(curator 표
P-8 + 정책 값 커밋).

**3B-2 준비 완료 2026-09-08 — 승인 대기** — 선행 조건(D-3B-6)이 닫혔다. 운영자가 **낙찰정보서비스 1.1 참고자료**와 **개방표준서비스 1.2 참고자료**를 확보해
`_workspace/m3-3b2/external/`(SHA-256 기록)에 두었고 `fixtures/manifest.yaml` `official_documents` 에 `koneps-scsbid-reference`·`pps-opnstd-reference` 로 등재했다.
`policy-values.md` **§1.7** 이 「문서 미확보」에서 **필드 계약 13 행**(curator 레인)으로, **§1.9** 가 **오퍼레이션 계약**(23 오퍼레이션 군 · `inqryDiv` 군별 의미 ·
공통 항목·운영 한계 · 개찰완료 오퍼레이션 · license-limit 서브콜, 세션 모델 — curator 레인 중단 뒤 이어 씀)으로 신설됐다. 계약 초안 `reports/evidence/m3/3b2/scope.md`
(D-3B2-1~9), 승인 요청 `_workspace/m3-3b2/01_curator_approval_request.md`(**P-9~P-12**, P-10 masking 이 차단 조건). 문서 대조가 드러낸 것: legacy 소비 17 키 중 13 만
문서에 있고 `bidOpenDt`·`ntceNm`·`prcmBsneSeCd` 는 어느 문서에도 없다 · `inqryDiv` 가 오퍼레이션 군마다 다른 축을 뜻한다 · **추첨번호·투찰률은 legacy 가 부르지 않는
개찰완료 오퍼레이션만 준다**(D-3B2-9) · 예비가격 존재 신호가 목록 응답에 필수로 온다(COL-03 술어 입력 후보) · **문서와 legacy 실측이 표적 개찰 조회에서 충돌한다**
(신설 `OPEN-3B2-TARGETED-OPENING-QUERY`, SET-02·ux-journey 문면에 포인터) · 페이지 크기 대 메시지 상한(신설 `OPEN-3B2-PAGE-SIZE-VS-MESSAGE-CAP`).
착수는 P-9~P-12 승인 + D-3B2-1~9 결정 뒤 — base 재고정 · Phase 2.5 설계 검토 · 구현 레인(sonnet) · verifier.

### Slice 3B-2 — 개찰·예비가격·자격 원문 서브콜 adapter

**착수 2026-09-08.** 계약 정본 `reports/evidence/m3/3b2/scope.md`(base `0e83d6e`). **운영자 승인**: **P-9 (a)** §1.7 필드 계약 13 행을 정책 데이터 초기값으로
(+ 3A 좁은 확장 ①②④ — `FieldConcept` 개찰 축 토큰·`FieldScale` 셈 축·`SourceEndpoint` 오퍼레이션 군 구별, 추가만) · **P-10 (a)** 식별자는 **상호만 남기고
사업자등록번호·대표자명을 어댑터 경계에서 치환**(`sourceText` 포함 — 「원문 무변환」의 명시적 예외) · **P-12 (a)** `inqryDiv` 를 오퍼레이션 군별 정책
데이터로 + 걷는 축 초기값 **개찰일시** · **D-3B2-9 (a)** 개찰완료 오퍼레이션은 이번 slice 밖. 결정 정본은 `policy-values.md` §6b. 나머지 D-3B2-1·5·6·7·8 은
추천안 그대로 확정. **Phase 2.5 설계 검토**(세션 모델 직접) `_workspace/m3-3b2/02_design-review.md` — 게이트 셋(치환·술어 증거 값·`inqryDiv` 표)을 열거
대신 **allow-list 반전 + masked 통로 타입 + 기본값 제거**로 닫고, 우회 여덟을 각각 컴파일·test·fail-closed 로 배정했다. **알려진 제한(착수 시 선언)**:
`COL-03` 문면의 **추첨번호**(`drwtNo1`·`drwtNo2`)와 투찰 축은 개찰완료 오퍼레이션만 주므로 이번 slice 에 없다 · 개찰 축 `sourceText` 는 계약 등재 키만
담아 미등재 키의 원문 재해석 경로가 이 축에 없다(P-10 (a) 의 대가). 이 문단은 **문서 레인이 쓴다** — 구현 레인은 `milestone-3.md` 를 편집하지 않는다.

**3B-2 종결 2026-09-08(사용자 승인) · M3 잔여 0** — verifier r3 `ready-for-review` + 장부층 low 다섯 일괄 위에서 승인(최종 head `9f0bb55`, evidence `reports/evidence/m3/3b2/checklist.md` 「사용자 승인」). **재작업 2/5.** 산출: `KonepsOpeningResultSource`(낙찰 목록·개찰결과 목록 두 군을 같은 클래스의 두 인스턴스로) · `KonepsLicenseLimitDocumentSource`(자격 원문 서브콜, `bidNtceOrd` 필수) · `KonepsOperationDescriptor`(오퍼레이션별 `inqryDiv`·필수 항목·행 식별자를 기본값 없이 선언) · `KonepsIdentifierMasking`(allow-list 반전 + `MaskedKonepsItem` 통로 타입) · 3A 좁은 확장 셋(P-9 ①②④ + D-3B2-5 술어 + `Accounting.kt` 회계 세 축). 3B 의 통신·재시도·봉투·페이지네이션·JSON 기반은 재사용하고 기존 test 는 편집하지 않았다.

**검증이 드러낸 것 — 셋 다 정적 판독으로는 나오지 않았다.** ① 단건 상세가 3B 목록 dedup 을 물려받아 **복수예비가격 15행이 1건으로 접히고 완료 판정까지** 났다(F-1). 수정 뒤에도 **선언한 행 식별자가 응답에 없을 때** 같은 실패가 재현됐고(G-1 — 문서가 그 키를 옵션으로 선언한다), 부재를 `""` 로 접지 않고 항목을 살리며 `rowIdentifierIndeterminate` 축으로 드러내는 것으로 닫았다. ② P-10 게이트가 **누출을 기본값으로** 두었다(F-4 — walker 의 `itemMapper` 기본 인자가 원문 보존 mapper). 기본값 제거로 「생략 = 누출」을 컴파일에서 닫았다. ③ 문서화된 rollback 이 **exit 0 이면서 컴파일 불가 트리**를 남겼다(G-3) — 되돌린 트리의 `compileKotlin`·test 확인을 절차에 넣었다(1A 16차 계보). 또 `presentIn` 강제(F-6)는 구현 레인의 「강제하면 깨진다」가 **승인 문면 미준수가 원인인 순환 논거**임을 오케스트레이터가 짚어 뒤집었고, 공유 키 셋을 §1.7.1·§1.9.1 문면대로 넓힌 뒤 강제가 섰다(미탐 0 실측).

**잔여 후속(M3 완료 조건 밖, M4 와 병행)**: 개찰완료 오퍼레이션 — `COL-03` 문면의 **추첨번호**(`drwtNo1`·`drwtNo2`)와 투찰 축(`bidprcAmt`·`bidprcrt`)을 주는 유일한 오퍼레이션이고 D-3B2-9 (a) 로 이번 slice 밖 · `OpeningResult` fact 확장 + 3D 스키마(D-3B2-8, P-10 masking 반영) · 3D V-1 · 3A 후속 셋(3B checklist) · `OPEN-3C-ATTACHMENT-FIELD-CONTRACT`(P-8) · P-7(업무구분 열거값 승인 항목화) · 3B-2 fixture case(후보 여덟, `_workspace/m3-3b2/01_curator_approval_request.md`). **OPEN 넷 신설** `capability-map.md` §14.3 — `OPEN-3B2-TARGETED-OPENING-QUERY`(문서는 공고번호 표적 개찰 조회를 선언하고 legacy 실측은 불가라 적는다 — 실제 호출 승인 뒤 닫히며 SET-02 의 소스 제약과 쿼터 계산이 걸려 있다) · `OPEN-3B2-PAGE-SIZE-VS-MESSAGE-CAP` · `OPEN-3B2-STORAGE-ROW-KEY-COLLISION`(G-1 해결이 어댑터 층 한정 — 3D `observation_key` 가 행 식별자 부재 행을 구별하지 못한다) · `OPEN-3B2-OPENING-FACT-SLOTS`. **M3 는 이제 잔여 slice 가 없다.**

## Codex 독립 리뷰

> **2026-09-04 운영자 결정:** 아래 관점은 Phase 4 `verifier` 가 적용한다. Codex 리뷰는 코드 slice 의
> 기본 경로가 아니며 운영자가 명시 요청할 때만 건다. 완료 조건의 「Codex `approve`」는
> 「verifier `ready-for-review`」로 읽는다.

- 외부 DTO가 domain으로 직접 새어 들어가는지
- provenance/precedence가 타입과 DB constraint/test에 반영됐는지
- retry가 무제한이거나 quota burst를 만드는지
- LLM 실패가 자격 통과로 fail-open하는지
- mock test가 실제 adapter serialization 경로를 통과하는지

## 범위 밖

- 실제 운영 KONEPS/LLM 호출
- Telegram/email
- ML inference
- 기존 Python collector 구조/API 호환

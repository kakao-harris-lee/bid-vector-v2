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

### Slice 3E — 개찰 fact 슬롯과 저장 행 키

**착수 2026-09-08.** 3B-2 가 남긴 후속 둘(**D-3B2-8** fact 슬롯 부재 · **`OPEN-3B2-STORAGE-ROW-KEY-COLLISION`** 행 식별자 없는 복수 행이 저장에서 접힘)을 한 slice 로 묶었다 —
둘 다 `procurement` fact 타입 + 3D 저장 스키마·키 유도를 건드려 마이그레이션 하나로 가는 편이 옳았다. 계약 정본 `reports/evidence/m3/3e/scope.md`(base `9948c6e`).
착수 전 결정: **D-3E-1a** raw 층 행 구별은 **값 우선 · 부재 시 응답 안 위치**(설계 검토가 계약 초안의 전제를 정정했다 — `observedAt` 이 fetch 마다 새로 잡혀 raw 키는 이미
fetch 마다 다르고, 「retry 후 canonical effect 하나」는 canonical 기본키가 지킨다. 긴장은 raw 가 아니라 **canonical 자식 행의 정체성**에만 있다) · **D-3E-1b (a) 운영자 승인**
순번이 부재·공백인 행은 **canonical 승격 없이 회계**(정체성을 지어내지 않는다 — 응답 안 위치를 키로 쓰면 문서가 선언하지 않는 행 순서 안정성에 기댄다) · D-3E-2 (a) 자식 표
신설 · D-3E-3 (a) 사라진 행을 지우지 않고 관측 시각으로 구분 · D-3E-4 (a) 부모 + 자식 목록 한 aggregate. **Phase 2.5 설계 검토**(세션 모델 직접) `_workspace/m3-3e/01_design-review.md`.

**실측이 설계를 한 자리 바꿨다** — 운영자 승인(2026-09-08) 아래 조달청 OpenAPI 를 **읽기 전용으로** 호출해 `policy-values.md` **§1.9.7** 에 등재했다. 순번 공백은 **총예가건수가 1**
일 때만 났고 **복수예비가격 15행은 순번이 전부 채워져** 있었다. 그래서 예정가격·기초금액·총예가건수·실개찰일시를 **공고 층(부모)** 으로 올리고 자식은 순번·기초예정가격·추첨
축으로 좁혔다 — 단수 예가 건도 부모 값을 잃지 않는다. 같은 호출이 **`OPEN-3B2-TARGETED-OPENING-QUERY` 를 닫았다**(표적조회는 가능하다 — legacy 의 「불가(실측)」와 SET-02 의
소스 제약을 폐기). 부수로 `OPEN-3E-RESERVE-FLAG-MISMATCH`(예비가격 파일 플래그가 상세 0행과 어긋남 2/8)를 신설했다.

**운영자 결정 하나 더** — 마이그레이션이 늘면 3D 의 스키마 스냅샷 test 둘이 **반드시** 깨진다(정확 일치 대조 + 공유 컨테이너라 우회 없음). **「기대값에 추가만」 예외**를 승인받았다
(단언 완화·기존 항목 삭제 금지, 검증 레인이 그 diff 를 표적으로 재검증). 앞으로 모든 마이그레이션이 같은 자리에 걸리므로 구조 개선은 `OPEN-3E-SCHEMA-SNAPSHOT-MAINTENANCE` 로 남겼다.

**검증이 드러낸 것 둘 — 둘 다 「저장 왕복에서 값의 뜻이 조용히 바뀐다」**: ① 읽기 경로가 세 금액의 provenance 를 `Published` 상수로 **지어내** `Undeclared` 가 왕복만으로 권위를
얻었다(H-1) — provenance 컬럼을 더해 저장한 것을 그대로 복원하는 것으로 닫았고, **권위 가드 적용 여부**는 3D 설계 변경이라 열지 않고 `OPEN-3E-OPENING-AMOUNT-AUTHORITY-GUARD`
로 등재했다. ② D-3E-3 (a) 의 「관측 시각으로 구분한다」가 **읽기 경로에 없어** 15→12 재수집 뒤 낡은 셋을 소비자가 알 수 없었다(H-2). 「조용히 사라진다」를 막고 「조용히
낡는다」가 남은 형태다.

**하네스 개선 둘이 이 slice 에서 나왔다** — rollback 실측에 **되돌린 트리의 compile·test 확인**을 더했고(3B-2 r2 가 exit 0 이면서 컴파일 불가 트리를 남긴 자리), 공유 워킹트리
커밋에 **경로 인자 명시**를 필수로 올렸다(문서 레인 커밋이 구현 레인의 스테이징 7 파일을 흡수한 사고 — 이력은 되쓰지 않고 계약에 사실로 선언).

**3E 종결 2026-09-09(사용자 승인)** — verifier r3 `ready-for-review` + 검증 레인 종결 동의 위에서 승인(최종 head `058c903`, evidence
`reports/evidence/m3/3e/checklist.md` 「사용자 승인」). **재작업 2/5.** 산출: `RowDiscriminator`(값 우선·부재 시 응답 안 위치) · `V4` 단일 마이그레이션
(`opening_reserve_price` 자식 표 — 순번 `NOT NULL` + 공백 CHECK 로 **정체성 없는 행의 승격을 스키마가 막는다** · `opening_result` 부모 슬롯과 금액 셋의
provenance 컬럼) · 부모/자식 한 트랜잭션 저장(자식을 지우지 않는다) · Testcontainers 통합 test. **닫힌 `OPEN` 셋**: 저장 행 키 충돌 · 개찰 fact 슬롯 ·
표적조회. **신설 넷**: 행 순서 안정성 · 예비가격 플래그 불일치 · 개찰 금액 권위 가드 미적용 · 스냅샷 래칫 유지보수.

**정리 라운드에서 마이그레이션 셋을 하나로 흡수했다** — 수정 라운드가 `V5`·`V6` 를 더했는데 verifier 실측으로 **`V4` 가 push 조차 안 됐고 적용 이력이 어디에도
없음**이 확인됐다(금지 규율은 **적용된** 마이그레이션 대상이다). 셋으로 가른 탓에 「`V4` 만 적용된 데이터 있는 DB 에 `V5` 적용 불가」라는 잠재 위험이 실제로
생겼고, push 하면 그 이력이 영구히 남아 모든 환경이 「컬럼을 만들고 곧바로 고치는」 절차를 재생한다 — **승인·push 전이 마지막으로 싼 순간**이라 오케스트레이터
결정으로 흡수했다. 검증 레인이 컨테이너 둘에 흡수 전후를 적용해 여덟 갈래 신호 318 대 318(차이 = 의도된 순번 CHECK 정의 1행)을 대조했다.

**검증 레인이 자기 오류를 먼저 밝힌 것이 이 slice 의 판정을 세운다** — 그 대조의 첫 질의가 타입 모호 오류로 **제약을 통째로 빼고** 비교해 「차이 없음」을 냈고,
그대로 뒀으면 의도된 변경조차 못 본 잘못된 통과였다. 고쳐 다시 돌린 결과가 정본이다.

**M3 잔여는 개찰완료 축 하나다** — `COL-03` 문면의 **추첨번호**(`drwtNo1`·`drwtNo2`)와 투찰 축(`bidprcAmt`·`bidprcrt`·`opengRank`)은 `getOpengResultListInfoOpengCompt`
만 주고 그 오퍼레이션이 **단건 조회로 실제 동작함을 실측**했다(D-3B2-9 (a) 로 3B-2 밖, 3E 도 「수집하지 않는 것의 자리를 짓지 않는다」로 슬롯을 만들지 않았다).
그 축을 여는 slice 가 P-10 masking 을 투찰업체 식별자(`prcbdrBizno`·`prcbdrCeoNm`)까지 넓혀야 한다. 그 밖 후속: 3D V-1 · 3A 후속 셋 · **P-8**(§1.10 첨부 문서 키
계약 — 표는 작성됐고 채택 대기) · fixture case(후보 여덟).

### Slice 3F — 개찰완료 축(추첨번호·개찰 1위)

**착수 2026-09-09.** M3 의 마지막 잔여 — 3B-2 가 **D-3B2-9 (a)** 로 밖에 둔 개찰완료 오퍼레이션
(`getOpengResultListInfoOpengCompt`)을 연다. 계약 정본 `reports/evidence/m3/3f/scope.md`(base `8119a47`).
**왜 지금인가**: 실현 사정률은 추첨된 예비가 평균 ÷ 기초금액이고 추첨번호가 15행의 **1-기반 인덱스**다 —
3E 가 그 15행에 순번을 기본키로 준 덕에 인덱스가 가리킬 대상이 생겼다. 두 축이 같이 있어야 사정률 분포
입력(M5)이 성립한다.

**실측이 이 slice 의 가장 어려운 결정을 지웠다.** 승인된 masking(P-10 (a))이 `prcbdrBizno` 를 치환하므로
투찰 행의 **자연 키가 사라진다**. 남는 후보를 실측했다(운영자 승인 아래 읽기 전용 호출 16회, 표본 15건은
참가업체수 상위 편향) — **`opengRank` 가 전 행 채워지고 유일한 건은 4/15**(결측뿐 아니라 **중복**까지,
참가업체수가 클수록 심함)이고 상호는 15/15 유일했다. 순위는 키로 쓸 수 없고 상호를 키로 쓰면 지우기로 한
축을 구조로 굳힌다. **그래서 한 걸음 물러서 도메인을 물었다** — 「누가 어느 번호를 골랐는가」가 필요한지.
**운영자 결정: 번호 집합으로 충분하다.** 그 답이 **투찰자별 canonical 표와 정체성 문제를 함께 지웠다**
(D-3F-3 해소). 부모가 갖는 것은 개찰 1위 축 · 관측된 추첨번호 집합 · 개찰결과구분명 셋이고, 투찰자별
원문은 `raw_observation` 감사에 남아 **나중에 귀속이 필요해지면 소급 승격의 재료**가 된다.

**구현 조사가 계약의 빈자리를 찾았다 — P-13.** legacy 가 이 오퍼레이션을 부르지 않아 투찰 축 키에는
**승인된 필드 계약이 아예 없었고**, `KonepsFieldContract` 생성자가 procurement `internal` 이라 어댑터가
계약 없이 값을 꺼낼 경로가 **구조적으로 없다**(앞선 검증 라운드들이 의도적으로 닫은 모듈 경계). 세션
모델이 문서 문면으로 `policy-values.md` **§1.11** 을 쓰고 운영자 승인을 받았다 — **열 키 등재, 평가점수
넷 제외**(문서가 범위·소수 자리를 적지 않아 scale 미확정 — 어휘를 지어내지 않는다), **사업자등록번호·
대표자명은 계약 미등재가 P-10 (a) 차단의 실물**이다. 그 승인으로 계약 초안이 빠뜨린 procurement 세 파일을
in_scope 로 정정했다(추가만). **어댑터에 allow-list 를 하드코딩하는 우회는 채택하지 않았다** — 계약
레지스트리가 allow-list·미지 필드 회계의 정본이고, 정책을 코드에 박으면 같은 사실이 두 자리에 산다.

**운영자 결정 여섯**: D-3F-1 (a) port 메서드 추가만 · D-3F-2 (a) 조회 가치 술어 재사용 · D-3F-3 해소(위) ·
D-3F-4 (a) 부모에 셋 · D-3F-5 (a) 평가점수는 수집·보존이되 P-13 으로 계약 제외 · D-3F-6 (a) 스키마 스냅샷
래칫 예외를 3E 와 같은 조건으로 · D-3F-7 = P-13 (a).

**3F 종결 2026-09-09(사용자 승인) — M3 잔여 0** — verifier r3 `ready-for-review` + M-1·low 일괄 위에서 승인(최종 head `3901acd`, evidence
`reports/evidence/m3/3f/checklist.md` 「사용자 승인」). **재작업 2/5.** 산출: `fetchOpeningCompleteResults`(공고당 1콜, 술어 증거 값 요구) ·
투찰 축 필드 계약 열(P-13, `KonepsOpeningCompleteFieldContracts.kt`) · 부모 fact 셋(개찰 1위 축 · 관측된 추첨번호 집합 · 개찰결과구분명, 각 축에 관측 시각) ·
`V5` 부모 컬럼(자식 표 없음). **`COL-03` 이 닫혔다** — 3E 의 복수예비가격 15행과 3F 의 추첨번호가 함께 서서 「복수예비가격 15개와 추첨번호를 확보한다」가 성립한다.

**검증이 세 라운드에 걸쳐 드러낸 것은 하나의 계열이다 — 저장 경로에서 값의 뜻이 조용히 바뀐다.** ① 컬럼별 `COALESCE` 가 짝 컬럼에 옛 값을 남겨 **정상 전이가
`PSQLException` 으로 죽었다**(F-1, 구현 레인 test 는 같은 상태끼리의 전이만 덮어 초록이었다) ② 축별 관측 시각이 없어 낡은 값이 최신으로 읽혔다(F-2, **3E H-2 의
재발**) ③ 그 ②를 고치려 넣은 CHECK 가 **병합 후 행이 아니라 제안 tuple** 에 걸려 **유효한 쓰기를 거부**했다(N-1 — 예외를 고친 것이 아니라 옮긴 것) ④ 범위의 하한이
타입으로 강제되지 않아 `5..15` 가 `1..15` 로 조용히 바뀌었다(M-1).

**뿌리는 매번 「불가능한 상태를 표현할 수 있게 두고 나중에 막으려 한 것」이었고, 닫는 방법도 매번 같았다 — 타입이 그 상태를 표현하지 못하게 한다.** `OutOfRange` 가
자기가 대조한 상한을 나르게 하니 오검출 원인 자체가 사라졌고, 하한은 `require` 한 줄이 도메인 진실(1-기반 인덱스)을 강제한다. 이 저장소가 3A·3B-2·3E 에서 쓴 것과 같은
수법이다.

**실측이 이 slice 의 가장 어려운 결정을 지웠다** — masking(P-10 (a))이 투찰 행의 자연 키를 지우고 남은 후보 `opengRank` 는 **전 행 채워지고 유일한 건이 4/15** 였다.
후보가 죽자 한 걸음 물러서 도메인을 물었고, **「번호 집합으로 충분하다」는 운영자 답이 투찰자별 표와 정체성 문제를 함께 지웠다.** 투찰자별 원문은 `raw_observation`
감사에 남아 나중에 귀속이 필요해지면 소급 승격의 재료가 된다.

**구현 조사가 계약의 빈자리를 찾았다** — legacy 가 이 오퍼레이션을 부르지 않아 투찰 축에는 **승인된 필드 계약이 아예 없었고**, `KonepsFieldContract` 생성자가
procurement `internal` 이라 어댑터가 계약 없이 값을 꺼낼 경로가 **구조적으로 없다**(앞선 검증 라운드들이 의도적으로 닫은 모듈 경계). 세션 모델이 §1.11 을 쓰고 P-13 을
받았다. **어댑터 하드코딩 allow-list 우회는 채택하지 않았다** — 계약 레지스트리가 allow-list·미지 필드 회계의 정본이고, 정책을 코드에 박으면 같은 사실이 두 자리에 산다.

**M3 는 이제 잔여 slice 가 없다.** 후속(별도 slice 아님): 3D V-1 · 3A 후속 셋 · **P-8**(§1.10 첨부 문서 키 계약, 표는 작성됐고 채택 대기) · fixture case(후보 여덟) ·
`bidNtceOrd` 실측 한 번 · `OPEN-3E-*` 넷과 `OPEN-3F-*` 둘.

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

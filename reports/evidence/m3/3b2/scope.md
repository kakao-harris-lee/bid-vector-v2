# Slice 계약 — M3 / 3B-2 · 개찰·예비가격·자격 원문 서브콜 adapter — **착수 2026-09-08(운영자 승인)**

> **지위**: M3 완료 선언(2026-09-08) 뒤 잔여 slice. 3B 의 범위 분할 ③(D-3B-6 귀결)이 넘긴 scope ⑧ 행 전체 — `OpeningResultSourcePort`
> (개찰·낙찰 목록 + 복수예비가격 상세) · `DocumentSourcePort`(license-limit 서브콜) · 표적조회. 세션 모델 단독 작성.
> **선행 조건 충족 2026-09-08**: 운영자가 조달청 **낙찰정보서비스 1.1** 참고자료(+ 개방표준서비스 1.2 참고자료)를 확보 —
> `_workspace/m3-3b2/external/`, SHA-256 은 그 폴더의 `SHA256SUMS`, manifest 등재 `official_documents` 의 `koneps-scsbid-reference`·
> `pps-opnstd-reference`. **문서 근거가 들어왔다**: `policy-values.md` §1.7(필드 계약 13 행, curator 레인) + §1.9(오퍼레이션 계약,
> 세션 모델 — curator 레인이 사용량 한도로 중단된 뒤 이어 씀). 승인 요청 표는 `_workspace/m3-3b2/01_curator_approval_request.md`.
> **착수 조건 충족 2026-09-08**: 운영자 승인 **P-9 (a) · P-10 (a) · P-12 (a) · D-3B2-9 (a)** — 결정 정본은 `policy-values.md` **§6b**.
> 남은 D-3B2-1·5·6·7·8 은 **추천안 그대로 오케스트레이터 확정**(승인 라운드에서 뒤집히지 않은 항목 — 뒤집으면 계약을 갱신한다).

```yaml
milestone: m3
slice: 3b2-opening-result-adapter
base_sha: 0e83d6e84a5279352aae1584e3849aa355a991c8   # 착수 2026-09-08 고정 = 승인 등재 커밋(P-9·P-10·P-12). 초안 시점은 ea58f3e
head_sha: 리뷰 시점의 HEAD
in_scope:
  - adapters/src/main/kotlin/bidvector/adapters/koneps/**   # 신규: OpeningResultSourcePort 구현(낙찰 목록 + PreparPcDetail) · DocumentSourcePort 구현(license-limit) · 오퍼레이션별 요청 정책 데이터. 3B 의 공통 기반(HttpClient·Resilience4j 한 계층·envelope·pagination·parse·회계·mapper)은 **재사용**하고 재작성하지 않는다 — 공통 기반의 시그니처 변경은 3B 기존 test 전부가 편집 없이 그대로 초록일 때만
  - adapters/src/test/kotlin/bidvector/adapters/koneps/**   # mock server 시나리오 test(⑦) · 기존 3B test 불변
  - config/quality/gate-tests.properties                     # `gate.tests.adapters` 에 3B-2 test 등재(3B 가 넣은 줄 뒤에 추가만 — 공유 파일, rollback 은 3B-2 가 넣은 줄만 제거)
  - procurement/src/main/kotlin/bidvector/procurement/{FieldContract.kt,CollectionPolicy.kt,RawObservation.kt}, procurement/src/test/kotlin/bidvector/procurement/{FieldContractTest.kt,CollectionPolicyTest.kt,RawObservationTest.kt}   # **P-9 승인분 — 3A 좁은 확장(추가만)**: ① `FieldConcept` 에 개찰 축 토큰 ② `FieldScale` 에 셈(정수 건수·순번) 축 + `SCALE_UNIT_PAIRING`·`FieldUnit` 결속 유지 ④ `SourceEndpoint` 에 오퍼레이션 군 구별(`OPENING_AWARD_LIST`·`OPENING_RESULT_LIST`·`RESERVE_PRICE_DETAIL` — 기존 `OPENING_RESULT` 를 지우지 않고 옆에 세운다) + 계약 행 helper 의 `presentIn` 고정 해제. **기존 계약 행·항등식·corpus 27/27 불변이 acceptance**
  - procurement/src/main/kotlin/bidvector/procurement/{Ports.kt,DetailFetch.kt}, procurement/src/test/kotlin/bidvector/procurement/{PortsTest.kt,DetailFetchTest.kt}   # **D-3B2-5 (a) 확정** — 자격 원문 조회 가치 술어(업종제한 플래그 `N` → 서브콜 0회)를 3A 패턴(`DetailFetchDecision.Fetch` 와 같은 internal-constructor 증거 값)으로 **추가만**. 위 여섯 파일 밖의 procurement 편집 금지
  - milestone-3.md                                           # 「Slice 3B-2」 착수 문단
  - reports/evidence/m3/3b2/**
out_of_scope:
  - procurement/**                                           # 위 조건부 두 파일 외 — `OpeningResult` fact 확장(예비가격 15·낙찰금액·낙찰자)은 D-3B2-8 별도 후속
  - reports/evidence/m3/3a/policy-values.md, fixtures/**    # curator 레인 소유 — 값이 틀리면 멈추고 보고, 고치지 않는다
  - 실제 KONEPS 호출·서비스 키·운영 설정값                        # 사용자 승인 사항. 인증키는 어느 파일·로그·evidence 에도 적지 않는다(D-3B-5)
  - DB write(3D)·LLM(3C)·스케줄·lease·업종별 인스턴스 배선(M4 4B)
  - 표적조회(`getBidPblancListInfo*` + `inqryDiv=2`) 구현       # D-3B2-6 (a) 확정 — 아래 「만들지 않는 것」
  - 개찰완료 오퍼레이션(`getOpengResultListInfoOpengCompt`) 구현     # D-3B2-9 (a) 승인 — 추첨번호·투찰 축은 후속 slice, 미구현을 알려진 제한으로 등재
  - 브라우저 크롤(COL-09)·fallback mock(COL-10)·M2 경로
acceptance_commands:
  - "git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check)"   # S-0
  - "./gradlew --no-build-cache clean check"                                                          # S-1
  - "./gradlew :adapters:test --tests 'bidvector.adapters.koneps.*'"                                 # S-2 — 3B 27 + 3B-2 시나리오 전부, 네트워크 0(loopback in-process)
  - "./gradlew :adapters:moduleDependencyGate"                                                        # S-3
  - "./gradlew :adapters:test --tests '*KonepsAdapterDependencyTest*'"                                # S-3b — koneps 패키지의 domain 참조는 procurement·shared-kernel 뿐
  - "./gradlew :procurement:test"                                                                     # S-3c — D-3B2-5 (a) 시 3A 술어 test 포함, 3A corpus 27/27 불변
  - "./gradlew qualityBaseline"                                                                        # S-4
rollback: |
    **정본은 `reports/evidence/m3/3b2/rollback.md`**(착수 시). 경로 한정 — 3B-2 가 **신설한** adapters 파일은 삭제, 3B 파일을 고쳤다면 base 로 restore,
    `config/quality/gate-tests.properties` 는 3B-2 가 넣은 줄만 제거(3B rollback 의 공유 파일 절차), procurement 조건부 두 파일은 base 로 restore.
    명령은 임시 clone 실측 뒤 기록(evidence-pack 2026-09-04 규격).
```

작성: 2026-09-08, 세션 모델 단독. 근거: `milestone-3.md` 3B 종결·M3 완료 문단(3B-2 잔여) · `reports/evidence/m3/3b/scope.md` ⑧·정정 ③·D-3B-6 ·
`capability-map.md` COL-02·03·04·06 · `policy-values.md` §1.7·§1.9·**§6b**(P-9·P-10·P-12 승인)·§6 P-6 · 3A `Ports.kt`·`DetailFetch.kt`·`NoticeFacts.kt`·`FieldContract.kt` ·
낙찰정보서비스 1.1 참고자료(문서 대조 — 아래 「조사 결과」).

---

## 하네스 레인 변경 (상시 절)

`git log --oneline <base>..HEAD -- CLAUDE.md .claude/` — 착수 시 등재. 준비 단계(2026-09-08) 커밋 여섯은 전부 문서·evidence 경로다(하네스 편집 없음).

## 역방향 파급 검사 — 준비 단계 편집 (evidence-pack 2026-09-02·09-03 규격)

준비 단계가 줄을 더한 승인 문서는 셋이다 — `docs/discovery/capability-map.md`(SET-02 포인터 +7 · §14.3 OPEN 둘 +2) ·
`milestone-3.md`(준비 완료 문단) · `docs/discovery/ux-journey-research.md`(포인터). `reports/evidence/m3/3a/policy-values.md` 는 §1.9·승인 대기 절이
**§1.8 뒤·§2 앞**에 들어가 §2 이후의 줄이 밀렸다.

**stem 기준 grep 실측**: `policy-values`·`ux-journey`·`milestone-3` 를 `file:line` 으로 가리키는 곳은 **없다**. `capability-map` 을 가리키는 좌표는 다섯이고
**둘이 밀렸다** — `:2190`(SET-09 `F-7` 인용, 실제 `F-7` 은 지금 다른 줄) · `:3171`(Codex 0A3 finding 인용, 지금 다른 절). **둘 다 이 편집 전에 이미
낡아 있었다**(`F-7` 은 인용 좌표보다 열여덟 줄 아래 — 이전 편집들이 만든 드리프트이고 이번 +7 이 더해진 것). 나머지 셋(`:164` 둘·`:1448`)은 편집 지점보다
위여서 밀리지 않았다(줄 번호를 명령으로 확인).

**밀린 둘은 이 slice 범위 밖이다** — 종결된 M0 slice 의 evidence(`reports/evidence/m0/0c/{decisions-2026-08-28,commands}.md` · `reports/evidence/m0/0a3/scope.md`)이고
evidence 는 되돌리지도 고치지도 않는다(감사 기록 보존). **알려진 제한으로 등재**하고, 그 문서를 다시 여는 작업이 생기면 좌표를 인용문·절 제목으로 바꾼다
(「낡는 좌표」 규격).

---

## 이 slice 가 하는 일

| # | 일 | 승인 문면 |
| --- | --- | --- |
| ① | **`OpeningResultSourcePort.fetchOpeningResults`** — **두 군을 각각** 기준일 창으로 걷는다(D-3B2-2: `getScsbidListSttus*` = 낙찰 축 · `getOpengResultListInfo*` = 개찰 축, 같은 구현 클래스의 두 인스턴스). 항목은 `RawNoticeObservation` 에 **군을 구별하는 `sourceEndpoint`**(P-9 ④ 확장)로 낸다. 페이지네이션·백스톱·회계는 3B ④⑤ 재사용 | COL-02 「어떤 공고가 얼마에 누구에게」 · 3B ⑧ |
| ② | **`fetchReservePrices(evidence: DetailFetchDecision.Fetch)`** — `…PreparPcDetail`(업종별 4종)을 `inqryDiv=2`(이 오퍼레이션군에서 **2 = 입찰공고번호**) + `bidNtceNo` 로 단건 조회. 서명이 3A 술어의 증거 값을 요구하므로 「예비가격 이미 있으면 0회 · age/recheck gate」는 컴파일 시점에 닫힌다 — 3B-2 는 실행만 | COL-03 acceptance 셋 · 3A ⑪ |
| ③ | **`DocumentSourcePort.fetchQualificationText`** — 입찰공고정보서비스 `getBidPblancListInfoLicenseLimit` 를 `inqryDiv=2` + `bidNtceNo` + **`bidNtceOrd`**(누락 = resultCode `08`)로 호출, `lcnsLmtNm`·`permsnIndstrytyList`·`lmtGrpNo`·`lmtSno` 를 `RawNoticeObservation(LICENSE_LIMIT_DETAIL)` 로. 「제한 없음」(resultCode `00` + `totalCount=0`)은 **성공 + 빈 항목**으로 회계에 구분되게 낸다(수집 실패와 저장 표현 분리 — COL-04) | COL-04 acceptance 첫·넷째 · 3B ⑧ |
| ④ | **업종제한 플래그 `N` → 서브콜 0회** — D-3B2-5 (a): 3A 에 순수 술어 `decideQualificationFetch(flags) → QualificationFetchDecision { Fetch(internal), Skip(NoRestriction) }` 를 **추가만** 하고 port 서명이 `Fetch` 를 받게 한다(3A ⑪ 과 같은 컴파일 시점 강제). 플래그 `indstrytyLmtYn` 은 **공고 목록 관측에 이미 있다**(§1.9.5) | COL-04 acceptance 첫째 |
| ⑤ | **오퍼레이션별 요청 정책 데이터** — `inqryDiv` 는 **전역 상수가 아니다**: 값 의미가 오퍼레이션군마다 다르다(개찰결과 목록 1 등록 · 2 공고 · 3 개찰 · 4 공고번호 / 예비가격 상세 1 입력일시 · 2 공고번호 / 낙찰 목록 1 공고게시 · 2 개찰 · 3 공고번호 — 문서 항목설명). 오퍼레이션 → (조회 축 값, 필수 항목, 기간 조건) 표를 정책 데이터로 선언하고 URI 빌더는 해석만 한다. **값의 정본은 `policy-values.md` §1.9.2**(P-12 승인)이고 **걷는 축 초기값은 개찰일시**다 | P-12 (a) · v2-지침서 §5 매직넘버 금지 · 3B ② 배관 |
| ⑥ | **개찰 축 필드 계약 소비** — `policy-values.md` §1.7 의 `authoritative` 행 13 을 3A `KonepsFieldContract` 인스턴스로 옮기는 **한 커밋**(P-9 승인, 3A P-1 과 같은 절차). `sucsfbidRate` 는 `scale = PERCENT`·`unit = PERCENT` 로 선언하고 canonical fraction 변환은 계약이 지시할 때만(제수 100, P-11 의 귀결) · 밴드는 `expectedRange` 참조로 재선언하지 않는다 | P-9·P-11 · 3A §5.3 규율 1·2 |
| ⑥b | **식별자 치환 — P-10 (a)** | `bidwinnrNm`(상호)은 계약 등재 + 원문 저장. **`bidwinnrBizno`·대표자명 축은 어댑터가 `RawNoticeObservation` 을 만들기 전에 고정 토큰으로 치환**하고 치환 사실을 회계에 남긴다 — `sourceText`(3D append 감사 통로)에도 원문이 남지 않는다. `opengCorpInfo` 는 성분 분해 뒤 상호·투찰금액·투찰율만 남기고, **분해 실패는 명시적 결과**로 회계(성분 수가 경우마다 다르다). 치환 토큰·대상 키는 정책 데이터 | P-10 (a) · `data-extract.md` §7 |
| ⑦ | **contract mock server test** — 시나리오: 낙찰 목록 정상 N 건 + 차수 없는 1 건 drop · PreparPcDetail 단건(복수예가 15 행 + 추첨 표시) · 「제한 없음」 `00`+`totalCount=0` · `bidNtceOrd` 누락 → `08` 을 `InputError` 류로 회계(재시도 아님) · 429/`22` bounded retry · 미지 resultCode · 미지 raw 키 · 목록 오퍼레이션에 `inqryDiv=4` 를 넣은 URI 가 정책 표에서 나오는지(⑤ 회귀) | 3B ⑦ 관례 · COL-03·04 |

**만들지 않는 것**: 표적조회 `getBidPblancListInfo*`+`inqryDiv=2`(D-3B2-6 (a) — 플래그·차수는 3B 목록 관측에 이미 있다) · **개찰완료 오퍼레이션**(D-3B2-9 (a) — 추첨번호 `drwtNo1`·`drwtNo2` 와 투찰 축 `bidprcAmt`·`bidprcrt` 는 이 오퍼레이션만 주고 이번 slice 밖이다, `COL-03` 추첨번호 문면 미구현을 알려진 제한으로) · `OpeningResult` fact 확장(D-3B2-8) ·
4겹 게이트를 한 함수에(COL-03 legacy 형태) · 백오프 상태를 데이터 컬럼에 · 예정가 역산(3D 소유) · 서비스 키 variant · 실제 호출.

---

## 운영자 결정 필요 — 착수 전

| ID | 물음 | 선택지 | 추천·근거 | 상태 |
| --- | --- | --- | --- | --- |
| **D-3B2-1** ✅ (a) 확정 2026-09-08 | **업종별 오퍼레이션 선택 자리** — 낙찰 목록·예비가격 상세가 업종(물품/공사/용역/외자)마다 오퍼레이션이 다르다 | (a) 3B 와 같이 **인스턴스당 오퍼레이션 하나**(baseUri 가 오퍼레이션 경로 포함), 업종 선택은 4B 배선 (b) 어댑터 안에 업종 → 오퍼레이션 카탈로그 정책 데이터 | **(a)** — 3B 관례와 같고 `Fetch(noticeId)` 가 업종을 나르지 않아 (b) 는 시그니처 확장을 부른다. 4B 착수 시 재검토 | 착수 전 |
| **D-3B2-2** ✅ 문서로 해소 2026-09-08 | **① 의 목록 오퍼레이션군** — 세 군 가운데 무엇을 부르는가 | (a) 낙찰 목록군 (b) 개찰결과 목록군 (c) **둘 다** | **(c)** — 문서 대조 결과 **둘은 대안이 아니라 다른 fact 다**(§1.9.1): 낙찰 목록(1~4)이 `sucsfbidAmt`·`sucsfbidRate`·`bidwinnrNm`·`bidwinnrBizno`·`fnlSucsfDate`·`prtcptCnum`·`rlOpengDt` 를, 개찰결과 목록(5~8)이 `opengDt`·`opengCorpInfo`·`progrsDivCdNm`·`rsrvtnPrceFileExistnceYn` 를 준다. legacy 도 둘 다 부른다. **3A port 확장이 필요 없다** — D-3B2-1 (a) 의 「인스턴스당 오퍼레이션 하나」로 같은 구현 클래스를 두 인스턴스로 세우고 4B 가 배선한다 | 해소 |
| **D-3B2-3** ✅ P-12 (a) 승인 2026-09-08 | **① 의 조회 축** = 승인 요청 **P-12 (2)** | (a) 정책값 + 초기값 **개찰일시** (b) legacy 등록일시 (c) 둘 다 지원(port 시그니처에 축 인자 = 3A 확장) | **(a)** — 문서가 개찰일시 축을 준다(낙찰 목록 `3`, 개찰결과 목록 `3`, 검색군 `2`). 기준일의 뜻이 `CollectionReferenceDate`(KST 캘린더 일자)와 맞는다. legacy 주석-문서 어긋남은 `insufficient-evidence` 등재 | P-12 |
| **D-3B2-4** ✅ P-10 (a) 승인 2026-09-08 | **`bidwinnrNm`·`bidwinnrBizno`·`opengCorpInfo` 취급** = curator **P-10** | curator 승인 요청의 선택지 | 승인 전에는 ⑥ 대로 미지 필드 회계 — 어댑터가 값을 도메인에 들이지 않는다 | P-10 |
| **D-3B2-5** ✅ (a) 확정 2026-09-08 | **④ 의 자리** — 「업종제한 플래그 `N` 이면 license-limit 0회」는 도메인 판단인데 3A `DocumentSourcePort.fetchQualificationText(noticeId)` 서명에 증거 값이 없다 | (a) **3A 좁은 확장**: `decideQualificationFetch(flags) → QualificationFetchDecision { Fetch(internal), Skip(NoRestriction) }` 순수 함수 + port 서명이 `Fetch` 를 받게(3A ⑪ 과 같은 컴파일 시점 강제, in_scope 조건부 두 파일) (b) 4B 호출부 규칙 (c) 어댑터 안 분기 | **(a)** — 3B verifier H-3 와 같은 갈래(3A 타입 부재를 좁은 추가로). (b) 는 우회 (1) 이 열리고 (c) 는 도메인 판단을 어댑터에 둔다 | 착수 전 |
| **D-3B2-6** ✅ (a) 확정 2026-09-08 | **표적조회 구현 여부** — legacy 는 `getBidPblancListInfo*`+`inqryDiv=2` 로 공고 1건을 다시 읽어 플래그·차수를 얻은 뒤 license-limit 을 불렀다 | (a) **만들지 않는다** — 3B 목록 관측이 `indstrytyLmtYn` 등 플래그와 `bidNtceOrd` 를 이미 나른다(legacy `ELIGIBILITY_RAW_KEYS` 가 목록 응답 키) (b) `NoticeSourcePort` 단건 변형으로 구현 | **(a)** — 호출 1회·쿼터 절약(COL-04 「쿼터 절약」). 4B 가 목록 밖 공고의 자격을 필요로 하면 별도 slice | 착수 전 |
| **D-3B2-7** ✅ (a) 확정 2026-09-08 | **fixture 자리** — 3B 는 wire envelope 골든이 없어 test 가 authoritative 필드명으로 envelope 을 직접 지었다(3B 알려진 제한) | (a) 3B 관례 유지 + curator 가 §1.7 필드 단위 authoritative case 만(3A 27 형태) (b) curator 가 문서 샘플 응답에서 **SYN 값 wire 골든**을 `fixtures/input/koneps/**` 에 만들고 `bidvector.fixtures.koneps` 배선까지(3B 후속 동시 해소) | **(a)** 이번 slice · (b) 는 curator 여력과 승인 요청의 fixture 후보 목록을 보고 결정 | curator 대기 |
| **D-3B2-8** ✅ (a) 확정 2026-09-08 | **3A `OpeningResult` fact 의 슬롯 부재** — 현재 `winningRate`·`derivedBaseAmount`·`observedAt` 뿐이라 복수예비가격 15·추첨·낙찰금액·낙찰자(masked)·참가자수를 canonical 로 옮길 자리가 없다 | (a) **3B-2 밖** — 3B-2 는 raw 관측 + 회계까지, fact 확장은 P-10 승인 뒤 3A 후속 소폭(4B 착수 전, 3D `opening_result` 스키마 확장과 한 묶음) (b) 3B-2 in_scope 로 | **(a)** — 어댑터 slice 에 도메인 fact 설계를 섞지 않는다(3B 가 `Accounting.kt` 만 좁게 연 것과 같은 선). `milestone-3.md` 잔여 문단에 후속으로 등재 | 착수 전 |
| **D-3B2-9** ✅ (a) 승인 2026-09-08 (P-12 (4)) | **개찰완료 오퍼레이션(`getOpengResultListInfoOpengCompt`)을 이번 slice 에서 부르는가** = 승인 요청 **P-12 (4)**. 그것만이 투찰자별 행을 준다 — `drwtNo1`·`drwtNo2`(**추첨번호**)·`bidprcAmt`·`bidprcrt`·`opengRank`·`prcbdrBizno`(필수). legacy 는 부르지 않는다 | (a) **이번 slice 밖**(알려진 제한 등재 + 후속 slice) (b) 포함 | **(a)** — 공고당 1콜의 쿼터 비용과 P-10 의 새 대상 하나가 붙는다. 다만 `COL-03` 문면이 추첨번호를 요구하므로 **미구현을 알려진 제한으로 명시**한다. (b) 선택 시 in/out scope 를 그 자리에서 갱신 | P-12 |

---

## 위협 모델 — 3B-2 고유 경계

**방어한다**: (a) 술어 우회 — `fetchReservePrices`·(D-3B2-5 (a) 시) `fetchQualificationText` 가 internal-constructor 증거 값만 받는다 (b) `inqryDiv` 오용 — 오퍼레이션별 정책 표 + URI 회귀 test(⑦) (c) 사업자 식별자 유입 — P-10 전에는 계약 부재 = 미지 필드 회계, 저장 경로 없음 (d) `08`·`03`·`00+totalCount=0` 의 혼동 — 세 결과가 회계에서 서로 다른 어휘 (e) 3B 공통 기반 회귀 — 3B 기존 test 전부 편집 없이 초록이 acceptance (f) 서비스 키 노출(D-3B-5 스캔).
**방어하지 않는다**: 실제 오퍼레이션 경로·파라미터의 실물 일치(실제 호출 승인 뒤) · 예정가 역산·기초금액 덮어쓰기 방지(3D 트리거) · 스케줄·쿼터 예산(OPS-08) · 낙찰 fact 의 canonical 저장(D-3B2-8 후속).

**우회 후보(≥5)**: (1) 4B 가 `Fetch` 없이 상세를 부름 → 서명이 막는다 (2) 목록군에 `inqryDiv=2` 를 「공고번호」로 씀 → 정책 표 + ⑦ URI test (3) `bidwinnrNm` 을 `sourceText` 로 저장 → `sourceText` 는 3D 감사 기록 통로라 P-10 이 masking 을 그 층에도 적용해야 함(P-10 선택지에 명시 요청) (4) 「제한 없음」을 실패로 회계 → ③ 어휘 test (5) `08` 을 재시도 → resultCode 범주 `InputError` 는 비재시도(P-4) (6) 3B walker 시그니처를 바꿔 3B test 를 고침 → in_scope 주석「3B test 불변」+ verifier 대조.

---

## 조사 결과 — 이 slice 에 영향을 주는 것 (문서 대조 2026-09-08 — 정본은 `policy-values.md` §1.7·§1.9)

- **문서 커버리지**: legacy 소비 17 키 중 **13 이 낙찰정보서비스 1.1** 에 있고(§1.7.1~§1.7.5), `opengDate` 는 개방표준(다른 서비스)에만, `bidOpenDt`·`ntceNm`·`prcmBsneSeCd` 는 **어느 문서에도 없다**(미등재 키 — `prcmBsneSeCd` 는 개방표준 문서의 URL 샘플 안 질의 파라미터일 뿐이라는 curator 정정).
- **오퍼레이션 23 · legacy 가 부르는 것은 세 군 12**(§1.9.1). 문서에만 있는 것: `…PPSSrch` 8 · `…OpengCompt`·`…Failing`·`…Rebid`.
- **`inqryDiv` 는 오퍼레이션 군마다 뜻이 다르다**(⑤·D-3B2-3·P-12). 예비가격 상세는 `2`=입찰공고번호(legacy 와 **일치**), 낙찰 목록은 `2`=공고일시·`3`=개찰일시, 검색군은 `3`=입찰공고번호, 개찰완료·유찰·재입찰은 **`inqryDiv` 자체가 없고 `bidNtceNo` 필수**. legacy 의 「1 = 등록일시」 주석은 낙찰 목록군에서 맞고 개찰결과 목록군(1 = 입력일시)에서 어긋난다 — 판정 없이 병기.
- **`sucsfbidRate` 는 %** 이고 **분자가 최종낙찰금액**이다(*"최종낙찰금액/예정가격 * 100"*). 투찰률은 개찰완료의 `bidprcrt` 로 따로 온다 — 두 율을 한 축으로 접지 않는다(P-11).
- **`rlOpengDt` 형식 `YYYY-MM-DD HH:MM:SS`**(타임존 표기 없음, 옵션) — 3A `dateTimePatterns`·`sourceZone`(P-2, `OPEN-3A-SOURCE-TZ`)이 그대로 적용된다. **입찰공고정보의 `opengDt` 가 「집행 가능 시작일시, 실제 개찰 아님」인 반면 이것이 실제 시각**이라 age-gate 앵커 후보가 문서로 생겼다(부재 규칙 필요).
- **예비가격 상세가 주는 것**: `plnprc`·`bssamt`·`bsisPlnprc`·`compnoRsrvtnPrceSno`·`drwtYn` + 신설 후보 `totRsrvtnPrceNum`(총예가건수)·`drwtNum`(추첨횟수)·`bssamtBssUpNum`·`compnoRsrvtnPrceMkngDt`·`PrearngPrcePurcnstcst`. **추첨번호는 여기 없다** — 개찰완료(13)만 준다(D-3B2-9).
- **`rsrvtnPrceFileExistnceYn`**(예비가격파일존재여부, 개찰결과 목록, **필수**, `Y`/`N`) — COL-03 의 「이미 있으면 상세 호출 0회」가 저장 상태 대신 **원본 신호**로 판정될 수 있다. legacy 미소비. 3A ⑪ 술어가 이 입력을 받을지는 착수 시 결정(3A 확장 여부 포함).
- **license-limit 의 legacy 실측이 문서로 승격**(§1.9.5): `inqryDiv=2` 에서 `bidNtceNo`·**`bidNtceOrd` 둘 다 필수**이고 샘플이 `000` 이다. `permsnIndstrytyList` 의 항목구분이 **`0..n`** 이라 「제한 없음」이 계약 위반이 아니며, 서브콜 0회 판단 입력 `indstrytyLmtYn` 은 **공고 목록 응답에 이미 있다**(D-3B2-6 (a) 의 근거).
- **문서 대 실측 충돌 — 판정하지 않는다**: legacy 는 *"공고번호 표적조회는 불가하다(실측)"* 를 개찰 결과 수집의 제약으로 적고 `capability-map.md` SET-02 가 소스 제약으로 승계했다. 문서는 개찰결과 목록에 `inqryDiv=4`(입찰공고번호)를 선언하고 개찰완료(13)는 `bidNtceNo` 를 필수로 요구한다. 신설 `OPEN-3B2-TARGETED-OPENING-QUERY` — 실제 호출 승인 뒤에만 닫히고, 닫히면 쿼터 계산이 바뀐다.
- **문서 운영 한계** 4000 bytes · 500 ms · 30 tps(전 23 오퍼레이션, `authoritative`) — rate limiter·페이지 크기 상한 근거. legacy 의 999 행 페이지와 대조가 필요하다(관측).
- **문서 내부 불일치 둘**: `numOfRows`·`pageNo` 의 항목구분이 오퍼레이션마다 필수/옵션으로 갈린다(항상 보내 양쪽 만족) · 식별자 항목크기가 갈린다(`bidNtceNo` 40/11, `bidNtceOrd` 2/3 — 문자열 원문 보존으로 회피).
- **대소문자 변형이 문서 안에 있다**: `fnlSucsfDate`/`FnlSucsfDate`(외자 2종만 대문자) · `PrearngPrcePurcnstcst`. **대소문자 무시 조회로 접지 않는다** — 미지 필드 리포트(§5.3 규율 1)가 무력해진다.
- **재사용**: 3B 의 `walkKonepsNoticePages`(internal)·`KonepsPageUriBuilder`·`KonepsResilientCall`·`KonepsRawItemMapper`·`MockKonepsServer` 가 그대로 쓰인다 — 단건 조회(②③)는 walker 의 1 페이지 특수화로. 새 HTTP 층·새 JSON 파서를 만들지 않는다. **응답 봉투가 §1.6 과 같은 형태**라 3B 의 envelope 검증이 그대로 선다.

---

## OPEN — 수령·신설

| OPEN | 3B-2 처리 |
| --- | --- |
| `OPEN-3A-SOURCE-TZ` | `rlOpengDt` 도 같은 규칙 — 닫지 않는다 |
| `OPEN-COL-03`(업무구분 코드 체계) | 낙찰정보서비스 `bsnsDivCd`(1 물품 · 2 외자 · 3 공사 · 5 용역 — 개방표준 문서 문면)는 curator 표로; 3B-2 는 소비하지 않는다 |
| `OPEN-3C-ATTACHMENT-FIELD-CONTRACT`(P-8) | 3B-2 대상 아님 — curator 표 별도 행 |
| 신설 후보 `OPEN-3B2-OPENING-FACT-SLOTS` | D-3B2-8 (a) 시 등재: `OpeningResult` fact 확장 + 3D 스키마, P-10 뒤 |
| 신설 후보 `OPEN-3B2-TARGETED-OPENING-QUERY` | **문서 대 실측 충돌** — 문서는 개찰결과 목록의 `inqryDiv=4`(입찰공고번호)와 개찰완료의 `bidNtceNo` 필수 단건 조회를 선언하는데, legacy 실측과 `capability-map.md` SET-02 는 「공고번호 표적조회 불가, 날짜창 스윕이 유일」이라 적는다. **실제 호출 승인 뒤에만 닫힌다.** 닫히면 SET-02 의 소스 제약 문면과 쿼터 계산이 바뀐다 |
| 신설 후보 `OPEN-3B2-PAGE-SIZE-VS-MESSAGE-CAP` | 문서 최대 메시지 **4000 bytes** 대 legacy 페이지 크기(목록 999) — 한 페이지가 상한을 넘으면 잘린 응답이 온다. 관측으로만 닫힌다 |

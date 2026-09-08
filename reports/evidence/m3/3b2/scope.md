# Slice 계약 — M3 / 3B-2 · 개찰·예비가격·자격 원문 서브콜 adapter — **초안 2026-09-08(curator 결과 반영 전)**

> **지위**: M3 완료 선언(2026-09-08) 뒤 잔여 slice. 3B 의 범위 분할 ③(D-3B-6 귀결)이 넘긴 scope ⑧ 행 전체 — `OpeningResultSourcePort`
> (개찰·낙찰 목록 + 복수예비가격 상세) · `DocumentSourcePort`(license-limit 서브콜) · 표적조회. 세션 모델 단독 작성.
> **선행 조건 충족 2026-09-08**: 운영자가 조달청 **낙찰정보서비스 1.1** 참고자료(+ 개방표준서비스 1.2 참고자료)를 확보 —
> `_workspace/m3-3b2/external/`, SHA-256 은 그 폴더의 `SHA256SUMS`. curator 레인이 `policy-values.md` §1.7 필드 계약 + 오퍼레이션
> 계약 절 + 승인 요청(P-9~)을 만들고 있다 — **그 승인 뒤 이 초안의 「curator 대기」 표시를 걷고 base 를 재고정한다.**
> 착수 조건: P-9~P-12 운영자 승인 · D-3B2-1~8 결정 · M4 4B 가 개찰 fact 를 소비하기 전(milestone-3 완료 문단).

```yaml
milestone: m3
slice: 3b2-opening-result-adapter
base_sha: 착수 시 재고정(curator 커밋·3A 좁은 확장 커밋 뒤 HEAD)   # 초안 시점 HEAD 는 ea58f3e(M3 완료 선언)
head_sha: 리뷰 시점의 HEAD
in_scope:
  - adapters/src/main/kotlin/bidvector/adapters/koneps/**   # 신규: OpeningResultSourcePort 구현(낙찰 목록 + PreparPcDetail) · DocumentSourcePort 구현(license-limit) · 오퍼레이션별 요청 정책 데이터. 3B 의 공통 기반(HttpClient·Resilience4j 한 계층·envelope·pagination·parse·회계·mapper)은 **재사용**하고 재작성하지 않는다 — 공통 기반의 시그니처 변경은 3B 기존 test 전부가 편집 없이 그대로 초록일 때만
  - adapters/src/test/kotlin/bidvector/adapters/koneps/**   # mock server 시나리오 test(⑦) · 기존 3B test 불변
  - config/quality/gate-tests.properties                     # `gate.tests.adapters` 에 3B-2 test 등재(3B 가 넣은 줄 뒤에 추가만 — 공유 파일, rollback 은 3B-2 가 넣은 줄만 제거)
  - procurement/src/main/kotlin/bidvector/procurement/{Ports.kt,DetailFetch.kt}, procurement/src/test/kotlin/bidvector/procurement/{PortsTest.kt,DetailFetchTest.kt}   # **조건부 · D-3B2-5 (a) 채택 시만** — 자격 원문 조회 가치 술어(업종제한 플래그 `N` → 서브콜 0회)를 3A 패턴(`DetailFetchDecision.Fetch` 와 같은 internal-constructor 증거 값)으로 **추가만**. 다른 procurement 파일 편집 금지
  - milestone-3.md                                           # 「Slice 3B-2」 착수 문단
  - reports/evidence/m3/3b2/**
out_of_scope:
  - procurement/**                                           # 위 조건부 두 파일 외 — `OpeningResult` fact 확장(예비가격 15·낙찰금액·낙찰자)은 D-3B2-8 별도 후속
  - reports/evidence/m3/3a/policy-values.md, fixtures/**    # curator 레인 소유 — 값이 틀리면 멈추고 보고, 고치지 않는다
  - 실제 KONEPS 호출·서비스 키·운영 설정값                        # 사용자 승인 사항. 인증키는 어느 파일·로그·evidence 에도 적지 않는다(D-3B-5)
  - DB write(3D)·LLM(3C)·스케줄·lease·업종별 인스턴스 배선(M4 4B)
  - 표적조회(`getBidPblancListInfo*` + `inqryDiv=2`) 구현       # D-3B2-6 (a) 채택 시 — 아래 「만들지 않는 것」
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
`capability-map.md` COL-02·03·04·06 · `policy-values.md` §1.7·§6 P-6(+ curator 신설 절, 대기) · 3A `Ports.kt`·`DetailFetch.kt`·`NoticeFacts.kt` ·
낙찰정보서비스 1.1 참고자료(오케스트레이터 사전 대조 — 아래 「조사 결과」).

---

## 하네스 레인 변경 (상시 절)

`git log --oneline <base>..HEAD -- CLAUDE.md .claude/` — 착수 시 등재.

---

## 이 slice 가 하는 일

| # | 일 | 승인 문면 |
| --- | --- | --- |
| ① | **`OpeningResultSourcePort.fetchOpeningResults`** — 낙찰·개찰 목록 오퍼레이션(D-3B2-2 가 군을 고른다: `getScsbidListSttus*` 또는 `getOpengResultListInfo*`)을 기준일 창으로 걷고 항목을 `RawNoticeObservation(sourceEndpoint = OPENING_RESULT)` 로 낸다. 페이지네이션·백스톱·회계는 3B ④⑤ 재사용 | COL-02 「어떤 공고가 얼마에 누구에게」 · 3B ⑧ |
| ② | **`fetchReservePrices(evidence: DetailFetchDecision.Fetch)`** — `…PreparPcDetail`(업종별 4종)을 `inqryDiv=2`(이 오퍼레이션군에서 **2 = 입찰공고번호**) + `bidNtceNo` 로 단건 조회. 서명이 3A 술어의 증거 값을 요구하므로 「예비가격 이미 있으면 0회 · age/recheck gate」는 컴파일 시점에 닫힌다 — 3B-2 는 실행만 | COL-03 acceptance 셋 · 3A ⑪ |
| ③ | **`DocumentSourcePort.fetchQualificationText`** — 입찰공고정보서비스 `getBidPblancListInfoLicenseLimit` 를 `inqryDiv=2` + `bidNtceNo` + **`bidNtceOrd`**(누락 = resultCode `08`)로 호출, `lcnsLmtNm`·`permsnIndstrytyList`·`lmtGrpNo`·`lmtSno` 를 `RawNoticeObservation(LICENSE_LIMIT_DETAIL)` 로. 「제한 없음」(resultCode `00` + `totalCount=0`)은 **성공 + 빈 항목**으로 회계에 구분되게 낸다(수집 실패와 저장 표현 분리 — COL-04) | COL-04 acceptance 첫·넷째 · 3B ⑧ |
| ④ | **업종제한 플래그 `N` → 서브콜 0회** — D-3B2-5 가 자리(3A 술어 vs 4B 호출부)를 정한다 | COL-04 acceptance 첫째 |
| ⑤ | **오퍼레이션별 요청 정책 데이터** — `inqryDiv` 는 **전역 상수가 아니다**: 값 의미가 오퍼레이션군마다 다르다(개찰결과 목록 1 등록 · 2 공고 · 3 개찰 · 4 공고번호 / 예비가격 상세 1 입력일시 · 2 공고번호 / 낙찰 목록 1 공고게시 · 2 개찰 · 3 공고번호 — 문서 항목설명). 오퍼레이션 → (조회 축 값, 필수 항목, 기간 상한) 표를 정책 데이터로 선언하고 URI 빌더는 해석만 한다. 값의 정본은 curator 오퍼레이션 계약 절(대기) | v2-지침서 §5 매직넘버 금지 · 3B ② 배관 |
| ⑥ | **개찰 축 필드 계약 소비** — `policy-values.md` §1.7 의 `authoritative` 행을 3A `KonepsFieldContract` 인스턴스로 옮기는 **한 커밋**(P-9 승인 뒤, 3A P-1 과 같은 절차). `sucsfbidRate` 는 문서가 **%**(× 100)라 `scale` 계약이 그것을 적고 fraction 변환은 계약이 지시할 때만(3B ⑥) · `bidwinnrNm`·`bidwinnrBizno` 는 P-10 masking 결정 전에는 **계약 등재 없음 = 미지 필드로 회계**(조용히 저장하지 않는다) | P-9·P-10·P-11 · 3A §5.3 규율 1 |
| ⑦ | **contract mock server test** — 시나리오: 낙찰 목록 정상 N 건 + 차수 없는 1 건 drop · PreparPcDetail 단건(복수예가 15 행 + 추첨 표시) · 「제한 없음」 `00`+`totalCount=0` · `bidNtceOrd` 누락 → `08` 을 `InputError` 류로 회계(재시도 아님) · 429/`22` bounded retry · 미지 resultCode · 미지 raw 키 · 목록 오퍼레이션에 `inqryDiv=4` 를 넣은 URI 가 정책 표에서 나오는지(⑤ 회귀) | 3B ⑦ 관례 · COL-03·04 |

**만들지 않는 것**: 표적조회 `getBidPblancListInfo*`+`inqryDiv=2`(D-3B2-6 (a) — 플래그·차수는 3B 목록 관측에 이미 있다) · `OpeningResult` fact 확장(D-3B2-8) ·
4겹 게이트를 한 함수에(COL-03 legacy 형태) · 백오프 상태를 데이터 컬럼에 · 예정가 역산(3D 소유) · 서비스 키 variant · 실제 호출.

---

## 운영자 결정 필요 — 착수 전

| ID | 물음 | 선택지 | 추천·근거 | 상태 |
| --- | --- | --- | --- | --- |
| **D-3B2-1** | **업종별 오퍼레이션 선택 자리** — 낙찰 목록·예비가격 상세가 업종(물품/공사/용역/외자)마다 오퍼레이션이 다르다 | (a) 3B 와 같이 **인스턴스당 오퍼레이션 하나**(baseUri 가 오퍼레이션 경로 포함), 업종 선택은 4B 배선 (b) 어댑터 안에 업종 → 오퍼레이션 카탈로그 정책 데이터 | **(a)** — 3B 관례와 같고 `Fetch(noticeId)` 가 업종을 나르지 않아 (b) 는 시그니처 확장을 부른다. 4B 착수 시 재검토 | 착수 전 |
| **D-3B2-2** | **① 의 목록 오퍼레이션군** — legacy 는 `getScsbidListSttus*`(낙찰 목록, `SCSBID_AWARD`)로 창을 걷고 예비가격만 `getOpengResultListInfo*PreparPcDetail` 을 쓴다. 문서에는 `getOpengResultListInfo*`(개찰결과 개찰완료 목록 — 1.1 에서 평가점수 4 항목 추가)도 있다 | (a) legacy 와 같이 낙찰 목록군 (b) 개찰결과 목록군 (c) 둘 다(port 메서드 추가 = 3A 확장) | **(a)** 잠정 — COL-02 문면(얼마에 누구에게 = 낙찰)과 맞고 §1.7 의 13 키가 어느 군에 있는지 curator 표가 확정한다. **curator 대기** | 착수 전 |
| **D-3B2-3** | **① 의 조회 축** — legacy `award_page_params` 는 `inqryDiv="1"` 을 「등록일시 구간」으로 주석하지만 낙찰 목록군의 문서 의미는 **1 = 공고게시일시**, 2 = 개찰일시. 개찰 결과를 「어느 날 개찰됐는가」로 모으려면 2 | (a) **개찰일시(2)** (b) legacy 그대로 1 (c) 둘을 정책값으로 두고 초기값 (a) | **(c)** 초기값 (a) — 기준일의 뜻이 `CollectionReferenceDate` KDoc(KST 캘린더 일자)와 맞아야 하고, legacy 의 주석-문서 불일치는 `insufficient-evidence` 로 등재(운영 피해 기록 없음). **curator 대기**(정확한 항목설명 문면) | 착수 전 |
| **D-3B2-4** | **`bidwinnrNm`·`bidwinnrBizno`·`opengCorpInfo` 취급** = curator **P-10** | curator 승인 요청의 선택지 | 승인 전에는 ⑥ 대로 미지 필드 회계 — 어댑터가 값을 도메인에 들이지 않는다 | P-10 |
| **D-3B2-5** | **④ 의 자리** — 「업종제한 플래그 `N` 이면 license-limit 0회」는 도메인 판단인데 3A `DocumentSourcePort.fetchQualificationText(noticeId)` 서명에 증거 값이 없다 | (a) **3A 좁은 확장**: `decideQualificationFetch(flags) → QualificationFetchDecision { Fetch(internal), Skip(NoRestriction) }` 순수 함수 + port 서명이 `Fetch` 를 받게(3A ⑪ 과 같은 컴파일 시점 강제, in_scope 조건부 두 파일) (b) 4B 호출부 규칙 (c) 어댑터 안 분기 | **(a)** — 3B verifier H-3 와 같은 갈래(3A 타입 부재를 좁은 추가로). (b) 는 우회 (1) 이 열리고 (c) 는 도메인 판단을 어댑터에 둔다 | 착수 전 |
| **D-3B2-6** | **표적조회 구현 여부** — legacy 는 `getBidPblancListInfo*`+`inqryDiv=2` 로 공고 1건을 다시 읽어 플래그·차수를 얻은 뒤 license-limit 을 불렀다 | (a) **만들지 않는다** — 3B 목록 관측이 `indstrytyLmtYn` 등 플래그와 `bidNtceOrd` 를 이미 나른다(legacy `ELIGIBILITY_RAW_KEYS` 가 목록 응답 키) (b) `NoticeSourcePort` 단건 변형으로 구현 | **(a)** — 호출 1회·쿼터 절약(COL-04 「쿼터 절약」). 4B 가 목록 밖 공고의 자격을 필요로 하면 별도 slice | 착수 전 |
| **D-3B2-7** | **fixture 자리** — 3B 는 wire envelope 골든이 없어 test 가 authoritative 필드명으로 envelope 을 직접 지었다(3B 알려진 제한) | (a) 3B 관례 유지 + curator 가 §1.7 필드 단위 authoritative case 만(3A 27 형태) (b) curator 가 문서 샘플 응답에서 **SYN 값 wire 골든**을 `fixtures/input/koneps/**` 에 만들고 `bidvector.fixtures.koneps` 배선까지(3B 후속 동시 해소) | **(a)** 이번 slice · (b) 는 curator 여력과 승인 요청의 fixture 후보 목록을 보고 결정 | curator 대기 |
| **D-3B2-8** | **3A `OpeningResult` fact 의 슬롯 부재** — 현재 `winningRate`·`derivedBaseAmount`·`observedAt` 뿐이라 복수예비가격 15·추첨·낙찰금액·낙찰자(masked)·참가자수를 canonical 로 옮길 자리가 없다 | (a) **3B-2 밖** — 3B-2 는 raw 관측 + 회계까지, fact 확장은 P-10 승인 뒤 3A 후속 소폭(4B 착수 전, 3D `opening_result` 스키마 확장과 한 묶음) (b) 3B-2 in_scope 로 | **(a)** — 어댑터 slice 에 도메인 fact 설계를 섞지 않는다(3B 가 `Accounting.kt` 만 좁게 연 것과 같은 선). `milestone-3.md` 잔여 문단에 후속으로 등재 | 착수 전 |

---

## 위협 모델 — 3B-2 고유 경계

**방어한다**: (a) 술어 우회 — `fetchReservePrices`·(D-3B2-5 (a) 시) `fetchQualificationText` 가 internal-constructor 증거 값만 받는다 (b) `inqryDiv` 오용 — 오퍼레이션별 정책 표 + URI 회귀 test(⑦) (c) 사업자 식별자 유입 — P-10 전에는 계약 부재 = 미지 필드 회계, 저장 경로 없음 (d) `08`·`03`·`00+totalCount=0` 의 혼동 — 세 결과가 회계에서 서로 다른 어휘 (e) 3B 공통 기반 회귀 — 3B 기존 test 전부 편집 없이 초록이 acceptance (f) 서비스 키 노출(D-3B-5 스캔).
**방어하지 않는다**: 실제 오퍼레이션 경로·파라미터의 실물 일치(실제 호출 승인 뒤) · 예정가 역산·기초금액 덮어쓰기 방지(3D 트리거) · 스케줄·쿼터 예산(OPS-08) · 낙찰 fact 의 canonical 저장(D-3B2-8 후속).

**우회 후보(≥5)**: (1) 4B 가 `Fetch` 없이 상세를 부름 → 서명이 막는다 (2) 목록군에 `inqryDiv=2` 를 「공고번호」로 씀 → 정책 표 + ⑦ URI test (3) `bidwinnrNm` 을 `sourceText` 로 저장 → `sourceText` 는 3D 감사 기록 통로라 P-10 이 masking 을 그 층에도 적용해야 함(P-10 선택지에 명시 요청) (4) 「제한 없음」을 실패로 회계 → ③ 어휘 test (5) `08` 을 재시도 → resultCode 범주 `InputError` 는 비재시도(P-4) (6) 3B walker 시그니처를 바꿔 3B test 를 고침 → in_scope 주석「3B test 불변」+ verifier 대조.

---

## 조사 결과 — 이 slice 에 영향을 주는 것 (오케스트레이터 사전 대조 2026-09-08, curator 가 재확인)

- **문서 커버리지**: §1.7 의 17 키 중 13 이 낙찰정보서비스 1.1 에 있고, `opengDate`·`prcmBsneSeCd` 는 개방표준서비스 1.2(다른 서비스)에만, `bidOpenDt`·`ntceNm` 은 어느 문서에도 없다(legacy 폴백 후순위 키 — 미등재로 둔다).
- **legacy 가 부르는 오퍼레이션 13 이 전부 문서에 있다**: `getScsbidListSttus{Servc,Cnstwk,Thng,Frgcpt}` · `getOpengResultListInfo{…}` 4 · `…PreparPcDetail` 4 · `getOpengResultListInfo`(접두). 문서에만 있는 것: `…PPSSrch` 변형, `…Rebid`·`…Failing`·`…OpengCompt`.
- **`inqryDiv` 는 오퍼레이션군마다 뜻이 다르다**(⑤·D-3B2-3). legacy `reserve_detail_params`(상세 `inqryDiv=2`+`bidNtceNo`)는 문서와 **일치**, `award_page_params` 의 「1 = 등록일시」 주석은 낙찰 목록군 문서 의미(1 = 공고게시일시)와 **어긋난다** — 판정 없이 병기.
- **`sucsfbidRate` 는 %**(*"최종낙찰금액/예정가격 × 100"*, 샘플 두 자리 소수) — legacy 계약의 `0.5~1.0` 밴드는 재선언이라 불채택(§1.2 확정, P-11).
- **`rlOpengDt` 형식 `YYYY-MM-DD HH:MM:SS`**(타임존 표기 없음) — 3A `dateTimePatterns`·`sourceZone` 규칙(P-2, `OPEN-3A-SOURCE-TZ`)이 그대로 적용된다.
- **문서가 주는 운영 한계**: 최대 메시지 4000 bytes · 평균 500 ms · 30 tps(문서 문면, `authoritative`) — rate limiter 초기값의 상한 근거. 예비가격 상세 오퍼레이션의 응답에 「총예가건수·복수예가순번·기초예정가격·추첨여부·추첨횟수·실개찰일시」가 있다(COL-03 의 15 행 + 추첨).
- **재사용**: 3B 의 `walkKonepsNoticePages`(internal)·`KonepsPageUriBuilder`·`KonepsResilientCall`·`KonepsRawItemMapper`·`MockKonepsServer` 가 그대로 쓰인다 — 단건 조회(②③)는 walker 의 1 페이지 특수화로. 새 HTTP 층·새 JSON 파서를 만들지 않는다.

---

## OPEN — 수령·신설

| OPEN | 3B-2 처리 |
| --- | --- |
| `OPEN-3A-SOURCE-TZ` | `rlOpengDt` 도 같은 규칙 — 닫지 않는다 |
| `OPEN-COL-03`(업무구분 코드 체계) | 낙찰정보서비스 `bsnsDivCd`(1 물품 · 2 외자 · 3 공사 · 5 용역 — 개방표준 문서 문면)는 curator 표로; 3B-2 는 소비하지 않는다 |
| `OPEN-3C-ATTACHMENT-FIELD-CONTRACT`(P-8) | 3B-2 대상 아님 — curator 표 별도 행 |
| 신설 후보 `OPEN-3B2-OPENING-FACT-SLOTS` | D-3B2-8 (a) 시 등재: `OpeningResult` fact 확장 + 3D 스키마, P-10 뒤 |

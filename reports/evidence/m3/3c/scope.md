# Slice 계약 — M3 / 3C · 문서/LLM extraction adapter — **종결 2026-09-08**

> **지위**: M3 착수 직후(2026-09-07) 세션 모델이 단독으로 쓴 **계약 초안**(문서 레인, 브리프 2). 구현·gradle·의존성 편집 없음.
> 착수는 **3A 승인 → 3B → 3D 뒤**(`prep/m3-prep.md` §5 순서)이며, 그때 `base_sha` 재고정(40자), 아래 D-3C-1~4 답 수령, `milestone-3.md`
> 「Slice 3C」 착수 문단. **Phase 2.5 설계 검토 대상** — 수용 기준이 「LLM 실패가 자격 통과로 fail-open 하지 않는가」·「watch rule 탈락 case 에서
> port 호출 0」이라 구성상 닫힘을 구현 전에 검토한다(아래 「위협 모델」·「우회 후보」는 저작 레인의 검토 입력).

```yaml
milestone: m3
slice: 3c-document-llm-extraction-adapter
base_sha: 2e31d4ede2fef9f1100bbdf71da0a1e0725b0f00   # 착수 2026-09-08 재고정 = 3D 수정 라운드 2 head(3D r4 와 병행 — 경로 분리). 초안 시점 앵커 1f3c4ff
head_sha: 리뷰 시점의 HEAD
in_scope:
  - adapters/src/main/kotlin/bidvector/adapters/extraction/**   # 문서 취득(`DocumentSourcePort` 구현)·chunk·LLM client port·JSON Schema 검증·provenance 조립·예산/timeout/breaker 배선
  - adapters/src/test/kotlin/bidvector/adapters/extraction/**   # fake LLM server 위 시나리오 전부(정상·schema 위반·timeout·예산 초과·breaker open·비지원 문서 형식)·게이트 test(호출 0회)
  - adapters/build.gradle.kts                                   # (D-3C-2 시) JSON Schema 검증 의존 한 줄 · resilience4j-kotlin(3B 가 이미 넣었으면 병합)
  - procurement/src/main/kotlin/bidvector/procurement/{RequirementExtractionPort,AttachmentDocumentPort}.kt, procurement/src/test/kotlin/**   # **조건 충족(착수 시 확인)** — 3A 의 `DocumentSourcePort` 는 KONEPS 자격 원문 서브콜(3B-2 소관)이라 3C 의 첨부 취득·추출 port 가 아니다 → 도메인 소유 port **두 파일**(첨부 취득 `AttachmentDocumentPort`, 추출 `RequirementExtractionPort` + 결과 타입 `ExtractedRequirements`·`ExtractionOutcome`·`ExtractionFailure`). 그 밖의 procurement 편집 금지
  - gradle/libs.versions.toml                                   # json-schema-validator 좌표(전례)
  - config/quality/gate-tests.properties                        # 조건부 — `gate.tests.adapters` 에 3C test 추가(3B 가 만든 키에 병합)
  - milestone-3.md                                              # 「Slice 3C」 착수 문단
  - reports/evidence/m3/3c/**
out_of_scope:
  - 실제 LLM provider 호출·API key·모델 이름 상수                 # D-M3-6 (a) — port 뒤 fake 만. 실제 호출은 사용자 승인 사항(agent-workflow §1)
  - 자격 판정 자체(`qualification` 1C 커널)                       # 3C 는 자격 원문의 **구조화 추출**까지. `Eligible/Ineligible` 은 만들지 않는다 — `Uncertain` 만 낸다
  - 감시 규칙(`strategy` 1E `matches`)의 변경                     # 3C 는 그 결과(증거 값)를 **요구**할 뿐
  - HWP 등 이진 문서 형식의 파서(D-3C-4)                          # `Uncertain(RequirementUnparsable)` 로 관측
  - DB write(3D)·스케줄·lease(M4)·알림
  - koneps OpenAPI 어댑터(3B)·persistence(3D)·ML client(4D) 패키지
acceptance_commands:
  - "git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check)"   # S-0
  - "./gradlew --no-build-cache clean check"                                                          # S-1
  - "./gradlew :adapters:test --tests 'bidvector.adapters.extraction.*'"                             # S-2 — fake LLM server(loopback in-process) 위 전 시나리오
  - "./gradlew :adapters:test --tests '*ExtractionGateTest*'"                                        # S-3 — watch rule 탈락 case 에서 LLM port 호출 횟수 0 · 통과 case 에서 정확히 1회(완료 조건)
  - "./gradlew :adapters:test --tests '*ExtractionFailOpenTest*'"                                    # S-4 — schema 위반·timeout·breaker open·예산 초과 넷이 전부 `Uncertain` 이고 어느 것도 요건 「없음」으로 접히지 않음
  - "./gradlew :adapters:moduleDependencyGate"                                                        # S-5
  - "./gradlew qualityBaseline"                                                                        # S-6
rollback: |
    **정본은 `reports/evidence/m3/3c/rollback.md`**(착수 시). `adapters/.../extraction/**` 과 build 의존 줄을 걷으면 3D 상태. 조건부 port 파일은 3A 가
    정의했으면 존재하지 않으므로 걷을 것이 없다.
```

작성: 2026-09-07, 세션 모델 단독. 근거: `milestone-3.md` 3C·완료 조건(「watch rule 탈락 case 에서 LLM port 호출 횟수 0」) · `prep/m3-prep.md` §2 3C 행·D-M3-6 ·
`ADR 0010` D-1(값은 정책 데이터, 규칙은 ADR)·D-5(breaker 는 소비자 정책) · `ADR 0005` D-10.1·D-11 · `data-dictionary.md` §1.6(`Unmeasurable` 을 `0` 으로 접지 않음)·§2.1·§5.2 ·
1C `LicenseVerdict.Uncertain`·`UncertainReason` · 1E `OperatorStrategy` · 조사 `_workspace/m3-prep/01_scout_collection.md` (d)(h).

---

## 하네스 레인 변경 (상시 절)

`git log --oneline 2e31d4e..HEAD -- CLAUDE.md .claude/` — 착수 시점(2026-09-08) **없음**.

**착수 2026-09-08 — 운영자 결정**: D-3C-1 (a) · D-3C-2 (a) · D-3C-4 (a) · D-M3-6 (a). **세션 모델 계약 정정·고정(착수 시)**: ① 3A `DocumentSourcePort.fetchQualificationText`
는 KONEPS 자격 원문 서브콜(3B-2)이라 3C ① 은 그것을 구현하지 않고 신설 `AttachmentDocumentPort`(첨부 URL → `FetchedDocument`)를 구현한다. ② **게이트의
자리(D-3C-6, 계약 고정)**: 같은 층 도메인 모듈끼리는 참조할 수 없어(ADR 0006 D-4) `procurement` port 가 1E `WatchVerdict` 를 인자로 받을 수 없다 — 「타입 증거」
게이트는 **adapters 층**에 선다: 공개 진입점 `WatchGatedExtractor.extract(verdict: WatchVerdict, document)` 만 존재하고 `Passed` 외에는 port 구현을 호출하지
않으며(`Skipped(NotWatched)` 값), port 구현 클래스(`HttpLlmRequirementExtractor`)는 `internal` 이라 adapters 밖에서 게이트 없이 구성할 수 없다. S-3 이 실행 증거(0회/1회).
③ **결과 타입의 자리**: `ExtractedRequirements`(schema 검증 통과한 구조화 요건 — 그룹 번호·일련·출처 필드 라벨·면허명 문자열 목록·근거 구간)는 `procurement`
타입이고, 1C `RequirementRow`·`UncertainReason` 로의 변환은 **adapters 한 함수**(`ExtractionToQualification`, D-3C-3 (a) — adapters 는 아래 층 전부를 볼 수 있다)에
두어 M4 4B 가 부른다. ④ **prompt 버전(D-3C-7, 계약 고정)**: prompt 문면은 adapters 리소스 `prompts/requirement-extraction.v1.txt`, version 은 파일명이며
스키마 `schema/requirement-extraction.v1.json` 과 짝으로 `promptVersion`·`schemaVersion` 에 실린다. 변경은 새 version 파일 + evidence(OPEN 등재 없음 —
`OPEN-3C-PROMPT-VERSIONING` 후보 닫힘). ⑤ `OPEN-3C-DOC-FORMATS` 는 종결 시 §14.3 등재.

**종결 시점(2026-09-08, 최종 head `16b7748`)** — 하네스 커밋 없음. range 에 3D 수정·종결 커밋(`15f3325`~`3be4b5f`)이 섞임 — slice 밖(공유 파일 셋은 줄 단위). verifier r1 not-ready → r2
ready-for-review, 재작업 1/5. `OPEN-3C-DOC-FORMATS`·`OPEN-3C-ATTACHMENT-FIELD-CONTRACT` 는 §14.3 등재(종결 커밋).

**병렬 레인 경계**: 3D 수정 라운드(r4 표적)가 같은 트리에서 병행한다 — `adapters/build.gradle.kts`·`gradle/libs.versions.toml`·`config/quality/gate-tests.properties`
는 **공유 파일**: 편집 전 `git status --porcelain -- <파일>` 로 다른 레인의 미커밋 변경이 없음을 확인하고, 있으면 그 파일 편집을 미루고 보고. 3C 는
`adapters/.../extraction/**`·`adapters/src/main/resources/{schema,prompts}/**`·procurement 두 파일·`reports/evidence/m3/3c/**` 만.

---

## 이 slice 가 하는 일

| # | 일 | 승인 문면 |
| --- | --- | --- |
| ① | **문서 취득** — 3A `DocumentSourcePort` 를 `HttpDocumentSourceAdapter` 가 구현. 입력은 공고가 게시한 첨부 URL(`ntceSpecDocUrl1` 계열 — 필드 계약 등재 키만), 출력은 `FetchedDocument(bytes, mediaType, sha256, fetchedAt, sourceUrl)`. 크기 상한·timeout 은 정책값. 취득 실패는 `Uncertain(CollectionFailed)` 로 관측(1C 어휘) | 3C 「원문과 추출값의 provenance」 · 조사 (d) 「첨부문서 취득 경로가 없다 — 입력 확보 자체가 신규」 |
| ② | **게이트 — `matches` 통과 뒤에만** — extraction 진입 함수의 인자는 `ExtractionRequest` 이고 그 생성자는 `internal` 로 **1E `WatchMatch`(감시 통과 증거 값) 에서만** 만들어진다(3A ⑪ `DetailFetchDecision.Fetch` 와 같은 「타입 증거」 형태). 탈락 공고로는 요청 값을 만들 수 없어 port 호출이 **컴파일 단계에서** 막힌다. S-3 이 실행 증거(0회/1회) | 3C 「M1 `OperatorStrategy.matches` 통과 후에만 실행」 · 완료 조건 「탈락 case 에서 LLM port 호출 횟수 0」 |
| ③ | **provider/model 주입** — `LlmClient` 는 adapters 안 인터페이스(port 가 아니다 — 도메인이 모른다), 구현은 `HttpLlmClient(endpoint, model, credentials)` 하나 + test 의 `FakeLlmServer`. 모델 이름·endpoint 는 설정 주입, 코드 상수 0(리뷰 항목 + grep test) | 3C 「provider/model 을 config 로 주입하고 domain 에서 분리」·「특정 모델 이름을 코드에 고정하지 않는다」 |
| ④ | **structured result — JSON Schema** — 추출 결과는 `schema/requirement-extraction.v1.json`(adapters 리소스, versioned)에 대해 **검증 통과한 것만** `ExtractedRequirements` 로 승격. 위반은 `Uncertain(RequirementUnparsable)`. 스키마 version 은 provenance 에 실린다. 검증 라이브러리는 D-3C-2 | 3C 「JSON Schema 기반 structured result」·「schema/model version 기록」 |
| ⑤ | **chunk·호출 예산·timeout·breaker** — 문서를 chunk 로 나누고 chunk 수·토큰 상한·문서당 호출 상한을 **예산**으로(초과 = `Uncertain(BudgetExceeded)` — 1C `UncertainReason` 에 variant 추가는 명세 변경이므로 **3C 는 adapters 안 사유 타입으로 두고 1C 변환은 `RequirementUnparsable` 로 접지 않는다** — D-3C-3). timeout·circuit breaker 는 Resilience4j `TimeLimiter`+`CircuitBreaker` **한 계층**(ADR 0005 D-11), 값은 adapters 정책 데이터(ADR 0010 D-1) | 3C 「chunk 와 호출 예산 제한」·「timeout/circuit breaker」 |
| ⑥ | **provenance** — `ExtractionProvenance(documentSha256, sourceUrl, chunkIndex, charRange, schemaVersion, modelId, promptVersion, extractedAt)` 를 추출 요건마다 하나. 원문 chunk 는 provenance 가 가리키고 결과에 복사하지 않는다. **응답 원문을 로그·evidence 에 남기지 않는다**(조사 (d) 이식 규율 — 문서 본문·개인정보) | 3C 「원문과 추출값의 provenance, schema/model version 기록」 |
| ⑦ | **`Uncertain` 이 유일한 실패 표현** — 취득 실패·비지원 형식·schema 위반·timeout·breaker open·예산 초과 전부 `Uncertain(reason)`. **degrade(근거 없음 → 빈 목록/`0.0`) 는 만들지 않는다**(조사 (d) 이식 금지). 결과 타입에 `Eligible` 류 값이 없어 자격 통과는 구조상 낼 수 없다 | 3C 「근거가 부족하면 `Uncertain`」 · §1.6 · 리뷰 관점 「LLM 실패가 자격 통과로 fail-open 하는지」 |
| ⑧ | **fake LLM server test** — loopback in-process 서버가 스크립트 응답(정상·schema 위반 JSON·지연·5xx 연속·빈 본문)을 낸다. 실제 provider 를 흉내내는 SDK 를 넣지 않는다 | 3C 「테스트는 fake LLM server 만」 |

**만들지 않는 것**: 휴리스틱·정규식 폴백(legacy `HeuristicDocumentAnalysisPort` — 근거 없는 값의 원천) · 평평한 dict 결과(`key_requirements`/`complexity_score` 류) · 응답 원문 로깅 ·
자격 판정 · 브라우저 크롤(COL-09, `OPEN-COL-04`) · 실제 provider.

---

## 운영자 결정 필요 — 착수 전(D-3C-1·2·4) · 계약 고정(D-3C-3·5)

| ID | 물음 | 선택지 | 추천·근거 | 상태 |
| --- | --- | --- | --- | --- |
| **D-3C-1** ✅ (a) 승인 2026-09-08(port 두 파일로 정정) | **extraction port 의 자리** — 3A 는 `DocumentSourcePort`(취득)만 정의한다. 「문서 → 구조화 요건」 port 는 어디에 서는가 | (a) **`procurement` 소유 `RequirementExtractionPort`**(자격 원문 fact `QualificationText` 의 구조화는 수집 축 — 3A 가 정의하거나 3C 가 조건부로 한 파일 추가) (b) `qualification` 소유(소비자 쪽) (c) adapters 안에만(port 없음) | **(a)** — ADR 0005 D-10.1 「domain 이 의존하는 port 는 domain 안에 선다」. (b) 는 qualification 이 문서 취득 개념을 알게 되고, (c) 는 M4 use case 가 adapters 타입을 import 하게 된다 | 착수 전 |
| **D-3C-2** ✅ (a) 승인 2026-09-08 | **JSON Schema 검증 라이브러리** | (a) **`com.networknt:json-schema-validator`**(Draft 2020-12, JVM 단일 의존) (b) kotlinx.serialization strict 디코딩만(스키마 파일 없음 — 「JSON Schema 기반」 문면 미충족) (c) everit | **(a)** — 문면이 「JSON Schema」를 요구하고, 스키마 파일이 version 의 정본이 된다. §7(측정된 필요) 은 이 한 줄에 충족 | 착수 전 |
| **D-3C-3** | **1C `UncertainReason` 과의 접점** — 3C 의 실패 사유(예산 초과·breaker open·비지원 형식)는 1C 다섯(`RequirementDataAbsent`·`RequirementUnparsable`·`OperatorLicensesNotDeclared`·`CollectionFailed`·`PermittedIndustryCombinationRuleUndecided`)에 1:1 로 없다 | (a) **adapters 안 `ExtractionFailure` sealed 를 두고 1C 로의 변환표(취득 실패 → `CollectionFailed`, 나머지 전부 → `RequirementUnparsable`)를 한 함수에** — 1C 어휘 불변, 세부 사유는 provenance·회계에 남김 (b) 1C `UncertainReason` 에 variant 추가(명세 변경·qualification 편집) | **(a)** — 1C 는 승인 산출물이고 변환표 한 곳이면 세부 사유가 사라지지 않는다. 변환표가 두 번째 자리에 생기지 않게 CPD 항목 | 계약 고정 |
| **D-3C-4** ✅ (a) 승인 2026-09-08 | **문서 형식 범위** — 첨부는 PDF·HWP·HWPX·텍스트가 섞인다 | (a) **PDF 텍스트 층 + 플레인 텍스트만**, 그 밖(HWP/HWPX/스캔 PDF)은 `Uncertain(RequirementUnparsable)` + 회계 `unsupportedFormat` (b) HWP 파서 의존 추가 | **(a)** — HWP 파서는 무거운 의존이고 근거(첨부 형식 분포)가 없다. 분포는 3B 수집 회계로 관측한 뒤 결정(`OPEN-3C-DOC-FORMATS`) | 착수 전 |
| **D-3C-5** | 정책값(chunk 크기·문서당 호출 상한·토큰 상한·timeout·breaker 임계)은 adapters 정책 데이터, 초기값 「보수적 + 관측 갱신」 — 3B D-3B-2·ADR 0010 D-1 과 같은 배관. 값은 이 문서에 적지 않는다 | — | 계약 고정 |

---

## 위협 모델 — 3C 고유 경계

**방어한다**: (a) **fail-open** — LLM 실패·schema 위반·예산 초과가 자격 「통과」나 요건 「없음」으로 읽히는 것(⑦ 결과 타입에 통과 값 없음 + S-4) (b) 감시 탈락 공고에의 호출(② 타입 증거 + S-3) (c) 무한·폭주 호출(⑤ 예산·breaker) (d) 모델 이름·endpoint 의 코드 고정(③ grep test) (e) 원문·응답의 로그 유출(⑥ 규율 + secret 스캔) (f) 스키마 없는 자유 dict 결과(④).
**방어하지 않는다**: 추출 **내용의 옳음**(모델 품질 — 운영 관측·`OPEN-QUAL-06` 정밀도 실측) · 실제 provider 의 요금·약관 · 스케줄·중복 실행(OPS-01/02) · 자격 판정의 옳음(1C) · 취득한 문서가 진본인가(KONEPS 게시 URL 신뢰).

**우회 후보(≥5)**: (1) `ExtractionRequest` 를 test 헬퍼로 직접 조립해 게이트 우회 → `internal constructor` + 생성 함수는 `WatchMatch` 만 받음, adapters test 는 1E 의 실제 `matches` 를 통과시켜 증거를 얻는다 (2) schema 검증 실패를 catch 해 부분 결과 반환 → 검증은 `ExtractedRequirements` 생성자 안(검증 없이 생성 불가) (3) breaker open 을 빈 요건으로 → `Uncertain` 만 반환 가능(결과 타입) (4) 모델 이름을 기본 인자 값으로 코드에 → 설정 주입 필수 + `grep -rn 'gpt-\|claude-\|gemini' adapters/src/main` 0건 test (5) 응답 원문을 예외 메시지에 실어 로그로 → 예외 타입이 원문 필드를 갖지 않음 + 로그 secret 스캔 (6) chunk 를 나누지 않고 문서 전체를 한 호출에 → 예산 검사가 chunk 단위 토큰 상한을 강제(초과 = `Uncertain(BudgetExceeded)`) (7) 취득 URL 을 필드 계약 밖 키에서 읽어 임의 URL 로 → 3A 필드 계약 `valueOf(contract)` 만(미등재 키 소비 불가).

---

## 조사 결과 — 이 slice 에 영향을 주는 것 (`_workspace/m3-prep/01_scout_collection.md` (d)(h))

- **legacy 에 라이브 LLM 경로가 없다** — provider SDK 0건, `ExecutorDocumentAnalysisPort` 는 배선되지 않은 seam. 3C 는 전부 신설이고 「strategy 통과 뒤에만」도 선례 없음.
- **이식 가치**: provider/model/budget **주입** 형태(`service_adapters.py` 생성자) · **응답 원문 비로깅** 규율(`llm_output_contracts.py`). **이식 금지**: degrade(타입 어긋난 필드 → 빈 리스트/`0.0`, 파싱 전체 실패 → 빈 계약 객체).
- `TokenBudget` 타입은 선언만 있고 강제 지점 없음 → ⑤ 는 신설. chunk·timeout·breaker 전무.
- 첨부문서 취득 경로 없음(`ntceSpecDocUrl1` 은 `source_url` 폴백으로만) → ①.
- `OPEN-COL-04`(브라우저 크롤) 는 3C 의 문서 취득 수단 후보로만 걸리고 OpenAPI 의 첨부 URL 이 우선 — 3C 를 막지 않는다.

---

## OPEN — 수령·신설

| OPEN | 3C 처리 |
| --- | --- |
| `OPEN-COL-04` | 브라우저 크롤 미채택 — 취득은 게시 URL 만. 활성 유지 |
| `OPEN-QUAL-06`(정밀도 실측) | 추출 정밀도는 3C 가 재지 않는다 — provenance 가 실측 가능한 형태를 남길 뿐 |
| 신설 후보 `OPEN-3C-DOC-FORMATS` | 첨부 형식 분포(PDF/HWP/HWPX/스캔)와 HWP 파서 채택 — D-3C-4 (a) 뒤 3B 수집 회계로 관측한 값이 근거. 등재는 착수 시 §14.3 |
| ~~신설 후보 `OPEN-3C-PROMPT-VERSIONING`~~ | 착수 시 계약 고정 **D-3C-7**(adapters 리소스 파일, 파일명 = version, 스키마와 짝)으로 닫힘 |

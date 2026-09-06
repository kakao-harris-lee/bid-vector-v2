# M3 준비 — KONEPS 수집·정규화·저장 경계 · slice 지도와 착수 전 결정 후보 (초안, 구현 전)

> **지위**: M2 진행 중에 세션 모델(Fable 5.1)이 단독으로 쓴 **준비 문서**다(CLAUDE.md 운영자 지시 2026-09-04). 구현·gradle·
> 의존성·fixture 편집은 하지 않았다. **M3 착수는 M2 계약 승인 뒤 운영자 별도 지시**(`milestone-3.md` 「선행 조건」 — M1 domain
> 승인 ✓ · M0 data dictionary ✓ · **M2 계약 승인** · KONEPS fixture 준비). 그때 각 slice 의 `base_sha` 를 재고정하고 「착수 전 결정」의
> 답을 받은 뒤 `milestone-3.md` 에 착수 문단을 쓴다. **M2 와 독립인 것**: 3A·3B 는 M2 계약 내용을 참조하지 않는다(ML 축이 아님) —
> 선행 조건은 순서 규칙이지 의존이 아니다.
>
> 작성 시점 HEAD `040ab9d` 이후(2026-09-07). 근거: `milestone-3.md` · `v2-지침서.md` §3.1·§4.1·§4.3·§4.5·§6 · `ADR 0004` D-1~D-6 ·
> `ADR 0005` D-1~D-11 · `ADR 0006` D-2~D-7 · `data-dictionary.md` §2.2.1·§5.1~5.3·§13.4 · `capability-map.md` COL-01~COL-10·§14.2
> `OPEN-COL-*` · `data-extract.md` 「KONEPS 수집」 · 조사 노트 `_workspace/m3-prep/01_scout_collection.md`.

---

## 1. M3 가 로드맵에서 서는 자리

| 축 | M3 의 몫 | 앞뒤 |
| --- | --- | --- |
| 수집(COL) | 외부 KONEPS 형식 ↔ 도메인 command/fact 의 **anti-corruption layer**. 정형 OpenAPI 먼저, 문서/LLM 은 감시 통과 뒤에만 | M1 1E `OperatorStrategy.matches`(3C 의 게이트) → M3 → M4 4B use case 가 소비 |
| 저장(3D) | raw observation · canonical fact · audit 분리, 멱등 upsert, precedence | ADR 0004(PostgreSQL·Flyway·원본/파생 분리) 위에 → M4 4C outbox 가 같은 트랜잭션 경계를 쓴다 → M6 6B clean 재현 |
| M2 와의 관계 | **없음** — 계약은 ML 축. 3A 의 canonical fact 가 M4 4D 에서 2B `FeatureInputs` 로 매핑될 뿐 | 병행 준비 가능 |

---

## 2. slice 지도

| slice | 하는 일(요지) | in_scope 후보 | M2 의존 | 정본 |
| --- | --- | --- | --- | --- |
| **3A 수집 port 와 canonical fact** | `NoticeSourcePort`·`OpeningResultSourcePort`·`DocumentSourcePort`(도메인 소유 port — ADR 0006 D-2 표 「`procurement` … 수집 port」, 방향은 ADR 0005 D-10.1) · 외부 DTO ↔ 도메인 command 분리 · 원문 field·canonical 값·provenance·observed-at 보존 · 공고 식별자 값 객체(번호+제로패딩 차수)·수정 version · **필드 계약 레지스트리**(§5.3, COL-07 — 정책 데이터) · 수집 회계 항등식(COL-06) · provenance first-match **한 지점**(§5.1·§4.3) | `procurement/**`(도메인)·정책 데이터 파일·fixtures `koneps-collection` 승격(curator)·`app` conformance runner dispatch·`gate-tests` 등재 | **내용 의존 없음 · 경로 겹침 있음**(`config/quality/gate-tests.properties`·`app/**` 는 2A 가 편집 — 2A 머지 뒤 병합) | `3a/scope.md`(초안 있음) |
| **3B OpenAPI adapter** | HTTP client(timeout·quota·bounded retry/backoff — Resilience4j, ADR 0005 D-6·D-11) · envelope/resultCode 검증(17 코드 정책 데이터, `OPEN-COL-02` 확정) · pagination·runaway 백스톱·partial·duplicate · parse 실패의 명시적 결과 · contract mock server test | `adapters/src/main/kotlin/bidvector/adapters/koneps/**`·`adapters/src/test/**`·`adapters/build.gradle.kts`(의존) | 없음 — 단 **경로가 M2 2A 와 겹친다**(`adapters/build.gradle.kts`·`adapters/src/test`) → 3B 코드는 2A 머지 뒤 | `3b/scope.md`(초안 있음) |
| **3C 문서/LLM extraction** | `matches` 통과 뒤에만 · provider/model 주입 · JSON Schema structured result · chunk·예산·timeout·circuit breaker · provenance·schema/model version · 근거 부족은 `Uncertain` · fake LLM server 만 | `adapters/.../extraction/**`·`qualification` 의 `Uncertain` 소비(1C 어휘) | 없음(ML 축 아님) — **LLM provider 결정**(D-M3-6)이 선행 | 착수 시 `3c/scope.md` |
| **3D persistence adapter** | Flyway `V1__` 부터(ADR 0004 D-4) · Testcontainers PostgreSQL · raw/canonical/audit 스키마 분리(D-5) · 같은 공고 재수집의 멱등 upsert/versioning · 점유 가드 precedence(§5.1 규율 1) · 감사 보존 | `adapters/.../persistence/**`·`adapters/src/main/resources/db/migration/**`·`app` 통합 test | 없음 — M4 4C outbox 가 같은 스키마 소유자(Kotlin 하나, D-3)를 전제 | 착수 시 `3d/scope.md` |

**M3 전체 out_of_scope**: 실제 KONEPS/LLM 호출 · Telegram/email · ML inference · legacy collector 구조/API 호환 · 브라우저 크롤(COL-09 `근거 부족`,
`OPEN-COL-04`) · mock/fallback_mock(COL-10 `폐기`) · 수집 스케줄·lease(OPS-01/02 — M4, ADR 0005 D-10) · 알림.

---

## 3. 착수 전 결정 후보 D-M3-1~8 (운영자 · 이 문서는 묻지 않는다)

| ID | 물음 | 선택지 | 추천·근거 |
| --- | --- | --- | --- |
| **D-M3-1** | **3B HTTP client.** `milestone-3.md` 3B 「비동기 HTTP client」 | (a) **JDK `java.net.http.HttpClient` + kotlinx-coroutines(`future().await()`)** — 신규 의존 0 (b) Spring `RestClient`(동기)/`WebClient`(Reactor) (c) Ktor client | **(a)** — adapters 층은 외부 의존 게이트가 없으나 §7(측정된 필요 없이 무거운 도구 금지). (b) 의 WebClient 는 Reactor 를 끌어와 코루틴과 두 비동기 모델, (c) 는 엔진·직렬화 스택 추가. 「비동기」의 실질은 timeout·cancellation 이 코루틴 취소로 전파되는 것이고 (a) 로 충분. Resilience4j 는 `resilience4j-kotlin` 의 suspend 데코레이터로 |
| **D-M3-2** | **contract mock server.** 3B 「contract mock server test」 | (a) **OkHttp `MockWebServer`**(test 의존만) (b) WireMock (c) 자체 in-process `HttpServer`(JDK `com.sun.net.httpserver`) | **(c)** 를 1차 — test 의존 0 이고 골든 응답(`data-extract.md` KONEPS fixture)을 그대로 서빙하기에 충분. 시나리오(지연·부분 응답·429 연속)가 늘어 유지비가 커지면 (a) 로 승격(결정 갱신) |
| **D-M3-3** | **필드 계약 레지스트리의 자리·형태**(§5.3 `KonepsFieldContract`, COL-07 「선언 데이터」) | (a) **`procurement` 도메인의 정책 데이터**(`EffectiveDatedPolicy` 관례 — 1C·1D 배관)로 두고 어댑터가 port 로 읽음 (b) adapters 안 코드 상수 (c) YAML 을 adapters 리소스로 | **(a)** — 계약이 도메인 의미(basis·scale·authoritative)를 담고 §5.3 이 「경계에서 거부하는 계약 테스트로 승격」을 확정했으므로 판정 소유는 도메인. ADR 0006 D-7 「정책 데이터는 판정을 소유한 domain 모듈의 자산」. legacy `FIELD_CONTRACTS` 등재율이 낮았던 원인(강제 없음)을 「등재되지 않은 키는 소비 불가」 타입으로 닫는다 |
| **D-M3-4** | **17 `resultCode` 범주표**(`OPEN-COL-02` 확정 — versioned policy data) 의 소유 | (a) **`procurement` 정책 데이터**(범주 = 재시도 가능/불가/quota/입력 오류) — 어댑터가 코드→범주 매핑만 (b) adapters 상수 | **(a)** — 범주는 「이 실패를 무엇으로 볼 것인가」라는 도메인 판정이고 OPS-09(실패 분류) 와 같은 축. 어댑터는 코드 문자열을 옮길 뿐 |
| **D-M3-5** | **서비스 키 3-variant 재시도**(`OPEN-COL-01`)·**rate limit 수치**(`OPEN-COL-05`) | (a) **키 variant 재시도는 채택하지 않고 단일 인코딩**(근거 문서 없음), rate limit 은 정책 데이터에 「보수적 초기값 + 429 관측으로 갱신」 (b) legacy 그대로 | **(a)** — 두 OPEN 다 「외부 문서 선행」인데 문서가 없다. 코드가 자기 근거를 갖지 않는 관용은 옮기지 않는다(§2). 429 는 Resilience4j rate limiter + bounded retry 로 정책값 갱신 근거를 수집한다 |
| **D-M3-6** | **3C LLM provider 결정**(3C 착수 전건) | (a) **provider 를 port 뒤에 두고 M3 는 fake 만** — 실제 provider 선택은 M6 6C 배치 결정과 함께 (b) 지금 provider 지정 | **(a)** — `milestone-3.md` 「특정 모델 이름을 코드에 고정하지 않는다·테스트는 fake LLM server 만」. 실제 호출은 사용자 승인 사항(agent-workflow §1) |
| **D-M3-7** | **3D 의 Testcontainers 실행 환경** — 리뷰 레인은 네트워크·소켓 차단(codex-review-gate §4b) | (a) **3D 통합 test 는 verifier 가 로컬에서 실측, Codex 는 코드 slice 비대상(2026-09-04)이라 무관**; CI 는 Docker 가능 러너 확인 후 등재 (b) H2 등 대체 엔진 | **(a)** — ADR 0004 D-1 「대체 엔진 dialect 분기를 만들지 않는다」가 (b) 를 막는다. Docker 부재 시 3D acceptance 는 「환경 미충족」으로 붉게 남긴다(조용한 skip 금지) |
| **D-M3-8** | **`koneps-collection` fixture 의 authoritative 승격** — 9 case 의 현재 layer 는 조사 (g) | (a) **3A 착수 전 curator 가 `data-extract.md` 절차로 골든(`openapi_notice_collect.json` 등)에서 재추출 + 운영자 승인**(1B-c 선례) (b) 3A 안에서 신설 | **(a) 확정** — 조사 (g): 9 case 전부 `insufficient-evidence`(authoritative 0), 회계·백오프·rate limit·타임존 축 case 없음. 골든(`openapi_notice_collect.json` 등)에서 재추출 + COL-01·05·06·07 acceptance 문면 승인으로 신설 |

---

## 4. `OPEN` 처리 후보

| OPEN | M3 처리 |
| --- | --- |
| `OPEN-COL-01` 키 variant | D-M3-5 (a) — 닫는 후보(채택 안 함) |
| `OPEN-COL-02`(확정) | D-M3-4 — 정책 데이터 등재로 소비 |
| `OPEN-COL-03` 업무구분 코드 체계 | 3A 는 「코드 + 라벨 두 값, 미지 코드는 미지로」(COL-08)까지. 전체 코드표는 외부 문서 — 활성 유지 |
| `OPEN-COL-04` 브라우저 크롤 | 3C 밖. 활성 유지 — COL-09 `근거 부족` |
| `OPEN-COL-05` rate limit 수치 | D-M3-5 (a) — 정책값 + 관측 갱신 |
| `OPEN-OPS-10` DB 큐 계약(outbox 어휘) | 3D 가 스키마를 열지만 어휘는 **M4 4C** 소유 — 3D 는 raw/canonical/audit 만 |

---

## 5. 병행 규칙 (M2 진행 중)

- **M3 코드 착수 금지**(M2 계약 승인 선행). 이 문서·3A·3B 계약 초안·조사 노트까지.
- 금지 경로: M2 in_scope 전부(`contracts/**`·`ml-contract/**`·`ml-engine/**`·`adapters/build.gradle.kts`·`adapters/src/test/**`·`build-logic/**`·
  `config/quality/**`·`settings.gradle.kts`·`gradle/libs.versions.toml`·`milestone-2.md`·`reports/evidence/m2/**`) + `capability-map.md`·`data-dictionary.md`.
- 착수 순서: 3A(도메인 코드는 무충돌 — 조건부 `gate-tests`·`app/**` 는 2A 머지 뒤 병합) → 3B(2A 가 `adapters` 편집을 끝낸 뒤) → 3D → 3C. 착수 시 `base_sha` 는 40자로.
- **Phase 2.5**: 3A(정책 데이터 분리·불변식·필드 계약 타입)·3D(스키마 precedence)는 설계 검토 대상 — scope 의 위협 모델 절은 검토 입력.

---

## 6. 조사 결과 요약 (`_workspace/m3-prep/01_scout_collection.md` — gitignore, legacy `ed4b06c`, 2026-09-07)

- **OpenAPI 경로에 재시도·backoff 가 없다** — 401 키 variant 순회만 있고 429·5xx 는 예외로 던져진다 → 3B 의 bounded retry 는 이식이 아니라 **Resilience4j 신설**(D-M3-5 (a) 의 「키 variant 불채택」 근거 보강).
- **rate limit 의 원인은 총량이 아니라 동시성**이고 429 는 약 2분에 회복된다 → 3B 정책 데이터의 1차 근거는 **동시성 상한 + 회복 대기**(rate limiter 형태), 총량 예산은 2차.
- **`resultCode` 부재가 `"00"` 으로 굳는 조각**이 잔존 → `R-COL-01` 「부재 = 분류 불가」 — 3B ③ 은 부재를 `Unclassified` 실패로.
- **페이지네이션에 진행 보장 검사가 없다** — 같은 페이지 반복이 상한 소진으로 조용히 끝남 → `R-COL-04` 신규 요구 = 3B ④ 백스톱(`truncated` 표시).
- **parse 실패가 전부 `None` → DTO 에서 `0.0`** — 실패·0원·미상이 한 값 → 3B ⑥ 결과 타입·3A ⑥ provenance 가 이 접힘을 타입으로 가른다.
- **율의 단위를 값 크기로 판별**(`PERCENT_SCALE_THRESHOLD=1.5`) — ADR 0002 D-4 금지 경로 → 3B 는 §5.3 계약의 `unit`/`scale` 선언에서만 읽는다(3A ④·⑧).
- **raw payload 가 저장되지 않는다**(메모리에만) → 3D 의 raw/canonical 분리는 legacy 선례 없이 ADR 0004 D-5 가 세운다.
- **점유 가드는 `budget_estimate` 한 축에만** — `base_amount` 는 양수 가드뿐 → 3A ⑥ `isAuthoritative` 데이터 + 3D write 규칙이 전 금액 축에 같은 가드.
- **배치 전체 한 번 commit, per-item savepoint 없음** → 3D 완료 조건 「반쯤 commit 하지 않음」의 반대 위험(전부 잃음)이 legacy 형태 — 3D 는 항목 단위 원자성과 배치 회계를 분리.
- **라이브 LLM 경로 없음** — 3C 전부 신설, 유일한 실물인 degrade(근거 없음 → `0.0`)는 **이식 금지**.
- **`Provenance.Published(noticeRevision: Int)` 가 `R-QUAL-05` 를 재현할 수 있다** — M1 은 값을 주장하지 않아 비껴갔으나 3A 는 차수를 표적조회 필수 입력으로 쓴다 → **D-3A-0 신설(착수 전)**.
- **`koneps-collection` 9 case 전부 `insufficient-evidence`**, 회계·백오프·rate limit·타임존 case 없음 → **D-M3-8 (a) 확정**: authoritative 승격 + 신설이 3A/3B 선행 작업(curator).
- **회계 항등식의 셈이 legacy 와 문면에서 다르다** — legacy `dropped_count` 는 중복을 포함, COL-06 문면은 `duplicate` 를 따로 더한다(서로소). **V2 는 문면을 그대로 채택**하고 legacy 와의 차이만 계약 주석·property test 로 고정(3A ⑦) — 문면 정정 대상 아님.
- **일시 출처 타임존 미확정(관측)** — legacy 는 KONEPS 벽시계 문자열을 UTC 로 파싱하는데 원문은 KST 로 보인다(9시간 어긋남 가능, 피해 기록·ledger 등재 없음) → 3A D-3A-4 는 해석 규칙을 정책 데이터로, `OPEN-3A-SOURCE-TZ`.

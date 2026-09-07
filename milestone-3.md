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

### Slice 3C — 문서/LLM extraction adapter

- M1 `OperatorStrategy.matches` 통과 후에만 실행
- provider/model을 config로 주입하고 domain에서 분리
- JSON Schema 기반 structured result
- chunk와 호출 예산 제한
- timeout/circuit breaker
- 원문과 추출값의 provenance, schema/model version 기록
- 면허를 확정할 근거가 부족하면 `Uncertain`

특정 모델 이름을 코드에 고정하지 않는다. 테스트는 fake LLM server만 사용한다.

### Slice 3D — persistence adapter

- Flyway migration과 Testcontainers PostgreSQL test
- raw observation과 canonical fact 분리
- 같은 공고 재수집의 멱등 upsert/versioning
- 파생값이 공식값을 조용히 덮지 못하는 precedence rule
- 원본, 판정, 오류를 감사 가능하게 보존

## 완료 조건

- fixture 기반 OpenAPI/문서 입력 전체가 명세대로 정규화
- malformed/partial/unknown field가 성공 데이터로 오염되지 않음
- watch rule 탈락 case에서 LLM port 호출 횟수 0
- retry 후에도 동일 공고의 canonical effect가 하나
- derived fact가 authoritative fact를 덮는 mutation이 실패
- 실제 네트워크·실제 LLM·운영 DB 없이 integration suite 통과
- KONEPS 장애가 transaction을 반쯤 commit하지 않음

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

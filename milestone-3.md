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

### Slice 3B — OpenAPI adapter

- 비동기 HTTP client, timeout, quota, bounded retry/backoff
- result code/envelope validation
- pagination, partial response, duplicate item 처리
- contract mock server test
- 숫자/날짜/encoding parse failure를 명시적 결과로 반환

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

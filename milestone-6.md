# 마일스톤 6 — Public API, 통합 검증과 배포 후보

## 목표

승인된 V2 capability를 독립 시스템으로 조립하고, 기존 `bid-vector` 없이 clean environment에서
검증 가능한 release candidate를 만든다. 기존 API 전체를 호환하거나 모든 기능을 재현하는
단계가 아니다.

## 선행 조건

- M1~M5 전체 승인 (slice 별 verifier `ready-for-review` + 사용자 승인; Codex 는 운영자 요청 시)
- M0 capability map의 V2 필수 범위 확정
- 실제 외부 호출/배포 여부에 대한 사용자 승인 경계 확인

**M6 착수 2026-09-16(사용자 「M6 착수」) — 전제 판정과 운영자 결정 둘** — 착수 전 입력 재고
(`_workspace/m6-prep/01_inputs.md`, 읽기 전용 조사)로 다섯 slice 의 입력 충족을 문면으로 판정했다.
**전제 상태**: M5 는 종결(PR #23, 종결 33·이월 14) · M1~M3 종결 · **M4 는 4A~4E·4B-1~4B-8 이 전부
종결됐으나 마일스톤 종결 판정 커밋이 아직 없다**(다른 세션 소관) — 각 slice 가 개별 사용자 승인으로
닫혔으므로 M6 착수를 막지 않으나, M6 완료 조건 9(verifier 최종 + release 승인)는 M4 종결 판정이
선행돼야 한다. **완료 조건 아홉 중 지금 성립하는 것은 조건 2 하나**(`bid-vector` symlink·import·DB
schema 런타임 의존 0 — 남은 문자열은 rootProject 이름·KDoc 인용뿐)이고, 조건 1(one-command)·3(E2E)·
7(rollback/restore rehearsal)·8(이미지 SBOM/스캔)은 미성립이다. **입력 판정**: 6B·6C 는 M5 가 이월
항목을 명시해 착수 가능 · 6A 는 승인된 입력이 없었다(capability-map 8축에 UI·endpoint 축이 없고
ADR 0008 §1.3 이 「UI 에는 승인된 사용자 가치도 acceptance scenario 도 없다」, 인바운드 인증 승인 문면
0, 코드 0) · 6D·6E 는 6A~6C 뒤.

그래서 **운영자 결정 둘을 받았다(2026-09-16, 선택지 + 추천)**: ① **`OPEN-ADR-09` 닫힘 — V2 의
소비자는 API 전용이고 화면은 기존 시스템을 계속 쓴다.** 6A 는 운영자 대상 HTTP endpoint·인증·audit 로
좁혀지고 UI 승인 공백을 만들지 않는다. ② **6A 인바운드 인증은 단일 운영자 토큰 + 전 요청 audit**
(정적 토큰을 secret store/env 로 주입, scope·RBAC 는 소비자가 늘 때 확장). 둘은 6A 착수 계약과 ADR
등재의 입력이다 — 6A 가 그 자리에서 문면으로 옮긴다.

**착수 순서**: **6C**(입력이 가장 명확하고 6D E2E 의 전제) → 6B(병렬 가능) → 6A(위 결정 둘로 입력이
섰다) → 6D → 6E.

## 구현 대상

### Slice 6A — public API와 auth

- M0에서 승인된 endpoint만 설계
- OpenAPI 단일 출처와 generated client/type
- 명시적 error body, pagination, nullability
- operator scope/RBAC와 audit
- controller는 workflow use case만 호출

기존 FastAPI path/schema와의 호환은 제품 요구로 승인된 항목에만 적용한다.

### Slice 6B — persistence와 migration completeness

- clean PostgreSQL에서 Flyway 전체 재현
- constraint/index/optimistic lock
- backup/restore와 migration rollback 정책
- raw/canonical/audit/outbox 데이터의 수명과 masking

**6B 분할·6B-1 착수 2026-09-17** — base `c4d09cc`(PR #31, 6C), 레인 worktree `bid-vector-v2-m6b`·브랜치
`m6-6b/2026-09-17`. 정본 `reports/evidence/m6/6b1/scope.md`(D-6B1-1~6). 위 bullet 넷이 성질이 다른 축을
한 목록에 담고 있어 **넷으로 가른다**(D-6B1-1·2): **6B-1** 스키마·동시성(Kotlin·Postgres·Testcontainers) ·
**6B-2** 백업·복원·마이그레이션 되돌림 리허설(완료 조건 7, 운영 절차 축) · **6B-3** 데이터 수명·마스킹
(보존·파기, privacy-gate 축 — **승인된 보존 기간이 없어 결정 선행**) · **6B-4** ML job 영속·큐 상한
(`OPEN-5E-JOB-PERSISTENCE`·`OPEN-5E-JOB-QUEUE-BOUND` — Python·파일 계열, 큐 정책 결정 선행). 근거는
6C 가 네 축을 한 slice 에 담아 **재작업 3 라운드**를 쓴 실측이다.

**6B-1 이 하는 것**(D-6B1-8, 착수 뒤 정정 — 아래 「수정 라운드 1」 참고): ① clean DB 전체 재현을
**기존 게이트 계열**(`CleanMigration*Test` 넷, D-3D-6 여덟 축)에 신설 표를 등재해 잇는다 —
`PersistenceTestSupport`의 공유 컨테이너가 `init`에서 Flyway 를 **한 번** 태우므로 그 뒤 다른 test 의
DML 은 스키마 카탈로그에 영향이 없고, 빈 컨테이너에 마이그레이션 전건을 적용하는 조건을 **이미
충족한다**(착수 계약의 "간접 재현만"은 팀장 오류였다). **카탈로그를 읽어** 표·PK·UNIQUE·컬럼 타입·
NOT NULL·트리거·CHECK 을 손으로 선언한 기대치와 대조한다(**인덱스는 이 게이트의 축이 아니다** — PK·
UNIQUE 만 재고, 인덱스 공백은 ②가 별도 표로 등재한다) ② 제약·인덱스 **실측과
공백 등재**(추가는 소비 질의를 가진 slice 가 근거와 함께 — D-6B1-5, `OPEN-6B1-INDEX-GAPS`) ③ **낙관적
동시성 실물** — `EditSessionRepository` 실 구현 + `session_version` **전제조건**(0행이면 실패). M4/4B 가
「세션 영속 실 구현 부재 + `sessionVersion` 낙관적 동시성 미검증」으로 남긴 자리이고, 지금 저장소에는
V2 트리거가 정하는 `revision` 만 있어 **잃어버린 갱신을 막는 전제조건이 어디에도 없다**. 충돌은 port
시그니처를 바꾸지 않고 **큰 소리로 실패**하며(D-6B1-4, 조용한 덮어쓰기는 허용하지 않는다) 결과 타입화는
두 번째 writer 가 생길 때(`OPEN-6B1-SAVE-OUTCOME`). 세션 복원은 M4 가 닫은 위조 축을 다시 열지 않는다
(D-6B1-6 — 새 public 표면 0). 마이그레이션 파일이 생기므로 **`migration-reviewer` 가 추가로 붙고**, Codex 는
되돌리기 어려운 경로라 대상이 되지만 유료 호출이므로 리뷰 요청 시점에 운영자에게 범위·비용을 묻는다.

### Slice 6C — container와 local environment

- Kotlin app, Python ML serving, PostgreSQL, broker가 필요한 경우에만 구성
- multi-stage build와 non-root runtime
- health/readiness 분리
- secret는 environment/secret store로만 주입
- dependency/image version 고정과 SBOM/vulnerability check

**6C 착수 2026-09-16** — base `f3ac571`, 레인 worktree `bid-vector-v2-m6`·브랜치 `m6-6c/2026-09-16`.
정본 `reports/evidence/m6/6c/scope.md`(D-6C-1~7). 착수 조사 실측: 컨테이너 자산 0(Dockerfile·compose
검색 0건, CI 에 이미지 단계 없음) · `app/` 모듈에 `main()` 이 **없다**(모듈 경계 앵커 + test 뿐) ·
ml-engine `serving` extra 는 이미 경량(numpy·lightgbm 만, 5A D-5A-0 (b)) · `tools/contract-crosslang-smoke.sh`
는 로컬 `.venv` 에 fake servicer 를 띄우는 **1회 수동 스모크**이고 스스로 「상시 게이트 아님」이라 적는다 ·
Python 서버에 표준 health 서비스가 없고 readiness 는 `GetModelMetadata.readiness`(5E-1 gate 실물)로 나온다.
**결정**: Kotlin 앱 이미지·entrypoint 는 실행할 것이 없으므로 **6A**(D-6C-1, 4B-6b 의 app 배선 인계와 같은
자리) · compose 는 ml-serving + postgres 둘만(broker 는 소비자 코드가 없어 세우지 않는다, D-6C-2) ·
health/readiness 는 **프로브 둘**로 분리하고 서버 코드(M5 종결)를 열지 않는다(D-6C-3, 표준 `grpc.health.v1`
채택은 6A/6D) · 실 서버 통합 test 는 태그로 기본 `check` 에서 빼되 **CI 에서는 돈다**(D-6C-4 — Docker 없는
환경의 상시 붉음과 「안 돌린 게이트」를 동시에 피한다) · SBOM·CVE 스캔 도구 채택은 6E(D-6C-5, `OPEN-6C-IMAGE-VULN-SCAN`) ·
`OPEN-5E-EMBEDDING-MODEL` 은 6C 가 닫지 않는다(D-6C-6 — 모델 선택은 컨테이너 축과 독립이고 승인 입력이 없다).
**이 slice 가 닫는 것**: `OPEN-5E2-CROSSLANG-REAL-SERVER`(실 Kotlin gateway ↔ 컨테이너의 실 Python 서버 —
5E-2 가 Python 미러로만 확인한 Kotlin 소비자 규칙 다섯과 5F-2 가 맞춘 `featureSchemaVersion` 이 실 응답에서
처음 실측된다) · 완료 조건 1 의 one-command · 완료 조건 8 의 이미지 위생 절반(고정 태그·non-root·금지 패키지).

### Slice 6D — E2E와 장애 주입

- mock KONEPS → canonical fact → qualification → ML → decision → fake notification
- 중복 공고, ML timeout, broker redelivery, DB conflict, malformed contract
- restart 후 outbox/inbox 수렴
- model rollback과 incompatible schema 거부
- 동일 input/policy/model version의 결과 재현

### Slice 6E — 제품 acceptance와 운영 runbook

- capability별 acceptance scenario
- metric/SLO, log/trace/dashboard
- backup/restore, model rollback, incident procedure
- live read probe와 notification dry-run 절차
- 기존 시스템과 V2를 비교한 차이 목록과 의도적 폐기 기능

## 완료 조건

- 새 checkout/clean database에서 one-command build/test 가능
- 기존 `bid-vector` import, symlink, DB schema에 runtime dependency 없음
- M0 필수 capability E2E 전체 통과
- 실제 외부 effect 없이 dry-run acceptance 가능
- 중요 mutation 생존 0 또는 사용자 승인된 명시적 예외
- 계약/모델/policy version으로 결과 재현
- rollback/restore rehearsal 증거 존재
- security/secret scan 통과
- verifier 최종 `ready-for-review` 와 사용자 release 승인 (Codex 최종 리뷰는 운영자가 요청하면 추가)

## Codex 독립 리뷰

> **2026-09-04 운영자 결정:** 아래 관점은 Phase 4 `verifier` 가 적용한다. Codex 리뷰는 코드 slice 의
> 기본 경로가 아니며 운영자가 명시 요청할 때만 건다. 완료 조건의 「Codex `approve`」는
> 「verifier `ready-for-review`」로 읽는다.

- E2E가 fake shortcut으로 핵심 production wiring을 우회하지 않는지
- 기존 프로젝트에 숨은 runtime dependency가 없는지
- auth/operator isolation/secret masking이 실제로 검증되는지
- failure injection 후 상태가 수렴하는지
- 완료 범위가 M0 capability map보다 과장되지 않았는지
- 배포·외부 호출이 사용자 승인 없이 자동화되지 않았는지

## 완료 후 별도 승인 사항

verifier·Codex 승인은 다음을 자동 허용하지 않는다.

- 실제 KONEPS/LLM/Telegram/email 호출
- 운영 DB 또는 credential 사용
- public 배포
- 기존 `bid-vector` 중지·삭제
- 원격 merge/push

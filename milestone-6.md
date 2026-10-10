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

**6A 분할·6A-1 착수 2026-09-17** — base `c4d09cc`(PR #31, 6C), 레인 worktree `bid-vector-v2-m6a`·브랜치
`m6-6a/2026-09-17`. 정본 `reports/evidence/m6/6a1/scope.md`(D-6A1-1~8). **운영자 결정 다섯**이 입력이다 —
2026-09-16 ① 소비자는 **API 전용**(화면은 기존 시스템, `OPEN-ADR-09` 닫힘) ② 인바운드 인증은 **단일 운영자
토큰 + 전 요청 audit**; 2026-09-17 ③ 웹 스택 **Spring Boot Web**(`app` 은 이미 Boot 플러그인·starter 를 갖고
있고 `group.forbidden` 은 domain 모듈만 겨눈다) ④ 6A 는 **기존 use case 만 노출**(검색·투찰가 요청 use case
신설은 별 slice) ⑤ audit 은 **전용 표 신설**.

**착수 조사가 범위를 바꿨다.** 노출 후보 use case 가 받는 **포트의 production 구현을 세어 보니 ML 축
하나뿐이었다** — 후보 공급·감시 대상·면허 게이트·여력·알림 요청·correlation id·전략 저장이 전부 test fake 다
(실측). 「기존 use case 를 노출한다」가 지금 상태로는 성립하지 않고, 노출하려면 **어댑터 여섯**을 먼저 써야
한다. M3 가 수집을, M4 가 판정을 세웠으나 **그 둘을 잇는 어댑터가 없다.** 그래서 6A 를 셋으로 가른다(D-6A1-1):
**6A-1** HTTP 골격·단일 운영자 토큰·요청 audit 표·전략 조회(배선이 가능한 유일한 축, `main()` 과 `bootJar`
활성 포함 — 6C 가 인계한 앱 이미지의 전제) · **6A-2** 세션 편집 명령 endpoint + 앱 이미지(`EditSessionRepository`
실 구현이 6B-1 소관이라 그 병합 뒤) · **6A-3** 후보평가·알림 축(`OPEN-6A-EVALUATION-ADAPTERS` — 어댑터 여섯의
slice 계획을 별도로 받는다).

**6A-1 의 경계**: endpoint 는 **읽기 하나**뿐이다(D-6A1-4) — 후보평가 use case 는 호출마다 알림 요청을 낳으므로
승인 문면 없는 외부 effect 를 HTTP 로 열지 않고, 실행 경로는 6A-2 에서 **dry-run 강제**로 시작한다. 토큰은
환경변수 주입·기본값 없음이고 값을 로그·응답·audit 에 싣지 않으며 실패 사유를 나누지 않는다(D-6A1-6). audit 은
**추가 전용**이고 요청 본문·토큰을 담지 않는다 — 보존·파기는 6B-3 이고 승인된 기간이 없다(D-6A1-7). OpenAPI 는
**수작성 단일 출처**이고 test 가 구현과 대조한다(생성 도구 도입 안 함 — 자동 생성하면 단일 출처가 구현이 되어
계약이 사라진다, D-6A1-8). **병행 레인**: 6B-1 이 `V8` 을 쓰므로 이 slice 는 `V9` 를 쓰고, 병합 순서가 바뀌면
번호를 다시 붙인다. 리뷰 레인은 `migration-reviewer`·`privacy-gate`·`contract-keeper` 셋이 추가로 붙는다
(전역 규약 §3 세 줄 전부 해당).

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

### Slice 6F — 수집↔판정 배선 어댑터 (2026-09-17 신설)

**운영자 지시 2026-09-17 「어댑터 부재는 중요한 결함이고 배선이 먼저」** 로 신설한 축이다. 6A 착수 조사와
배선 재고(`_workspace/m6-wiring/01_ports.md`)가 같은 사실을 냈다 — **M3 가 수집을, M4 가 판정을 세웠으나 그
둘을 잇는 어댑터가 없다.** `EvaluateCandidatesUseCase`·`OpportunityAnalysis`·`EditStrategyWorkflow` 가 받는
포트 가운데 **production 구현이 있는 것은 넷**(임베딩·투찰 예측 gRPC gateway · 경쟁 표본 JDBC 소스 · outbox
event sink)이고 **아홉이 비어 있다**(전략 저장·세션 저장·후보 원천·감시 대상·면허 게이트·여력·운영자 프로필·
업무량·알림 요청). `app/src/main` 은 앵커 파일 하나뿐이어서 **DI 조립 코드가 통째로 없다**. 완료 조건 3(필수
capability E2E 전체 통과)이 이 축에 걸려 있다.

**공백의 성격이 셋으로 갈린다** — ① **질의만 없음**(후보 원천: 표에 데이터가 있고 repository 가 단건 조회만
낸다 · trace 축 생성) ② **데이터·정의 자체가 없음**(감시 대상의 원문 텍스트는 canonical 공고 fact 에 열이 없고
raw 원문에만 있다 · 「활성 투찰」 정의가 저장소·discovery 에 없다 · 면허 요구사항 영속과 운영자 면허가 없다 ·
운영자 프로필 모델이 미결) ③ **타입 체계가 갈라져 있음**(알림 **요청** 값과 배달 **의도** 값을 잇는 코드가
없고 outbox 소비자·발송 채널이 없다). 포트마다 데이터 소재·외부 계약·미결 결정이 달라 **포트별로 가른다**
(D-6F1-1) — 한 slice 로 묶으면 6C 가 쓴 재작업 3 라운드를 반복한다.

| slice | 축 | 선행·미결 |
| --- | --- | --- |
| **6F-1** | 전략 영속(`StrategyRepository`) | **없음 — 첫째.** `evaluate()` 의 첫 줄이 `strategies.load()` 라 유일한 진짜 선행 의존이고, 도메인에 **이미 공개된 검증 문**이 있어 새 표면 없이 배선된다 |
| 6F-2 | 후보 원천 + trace 축 | 6F-1. 질의만 신설(다건 스캔) |
| 6F-3 | 여력 | **결정 ③ 로 열림**(2026-09-18) — 현재 활성 수는 평가 요청이 싣고 상한은 전략 필드. **6A 평가 endpoint 선행** |
| 6F-4 | 감시 대상 | **결정 ① 로 열림**(2026-09-18) — 원문 텍스트를 canonical 열로 싣는다(공고명부터). **셋 중 먼저** |
| 6F-5 | 면허 게이트 | **둘로 갈랐다**(2026-09-19, D-6F5-1) — **6F-5-a** 요건 영속 + 게이트가 저장된 것만 읽는다(LLM 0, 착수함) · **6F-5-b** 추출 결과를 채우는 경로(**LLM 호출 = 운영자 승인 대상**, 6F-4 선행) |
| 6F-6 | 운영자 프로필 + 업무량 | **결정 ② 로 열림**(2026-09-18) — 프로필 표 + 편집 endpoint. 업무량은 분리(`WorkloadNotCollected` 유지) |
| 6F-7 | 알림 요청 → outbox | 요청·의도 타입 연결. 발송 채널은 `OPEN-STR-12`(그 뒤) |

세션 영속은 **6B-1** 이 이미 진행 중이라 6F 군에 넣지 않는다(같은 축, 다른 레인). app DI 조립은
`OPEN-6F-ASSEMBLY` — 포트 구현이 모이는 시점에 6A-1(HTTP) 또는 전용 slice 가 받는다.

**운영자 결정 셋 2026-09-18 (선택지 + 추천, 전부 추천안 채택) — 6F-3·6F-4·6F-6 의 미결을 연다.**
세션 모델이 저장소와 legacy 를 실측해 선택지를 세우고 운영자가 「전체 추천 방향」으로 승인했다. 착수 순서는
**6F-4 → 6F-6 → 6F-3**(결정이 가장 가볍고 가치가 큰 것부터, 6F-3 은 6A 평가 endpoint 뒤).

- **결정 ① `OPEN-6F-WATCH-TEXT-SOURCE` 닫힘 — 원문 텍스트는 canonical 에 열로 싣는다(6F-4).** 조립 규칙은
  이미 승인돼 있다(capability-map STR-02: `keywordText` = 공고명 + 요건 + 공종, **description 제외** ·
  `fullText` = description 포함). V2 가 가진 것은 공종(`notice`)과 요건 원문(`qualification_text`)이고 **공고명이
  없다** — 그런데 **KONEPS 는 준다**(legacy 수집 어댑터가 `bidNtceNm`/`ntceNm` 를 읽어 title 로 넣고 필드 계약
  목록에도 등재돼 있다). 「API 가 안 준다」가 아니라 **V2 가 안 싣는다**. raw 를 판정 경로에서 읽는 안은
  **채택하지 않는다** — M3 가 세운 raw/canonical 경계가 무너지고 판정이 수집 계약(JSON 키)에 매번 묶인다.
  이 텍스트는 감시(STR-01·02)와 검색(STR-16) 두 capability 의 입력이라 정본이 하나여야 한다. **운영 데이터 0
  이라 백필 비용이 지금이 최저다.** 공고명부터 싣고, **공고 본문(`description`)의 KONEPS 출처는 미확인**이라
  6F-4 착수 조사가 실측한다 — 그때까지 「본문 없음」은 알려진 제한으로 등재하고 `fullText` 는 공고명·요건·공종
  으로만 선다(지역 규칙이 좁게 돈다).
- **결정 ② `OPEN-4B6-PROFILE-SOURCE` 닫힘 — 운영자 프로필은 표로 세우고 편집 endpoint 를 둔다(6F-6).**
  `ProfileFacts(businessTypes, licenses, regionTerms)` 는 legacy `company_profiles` 의 세 열과 1:1 이고 legacy 도
  DB 표로 들고 있었다. 설정 주입 안은 **편집이 곧 배포**가 되고 감사가 없어 버린다. 전략 표에 합치는 안은
  수명이 달라 버린다(전략 개정마다 프로필이 딸려 개정돼 개정 이력이 오염된다). 개인정보 축은 이미 닫혀 있다
  — 사업자번호·대표자·연락처는 `ProfileFacts` 에 **필드가 없어** 구조적으로 못 들어온다(4B-6a 위협 모델).
  이 결정은 **6F-5 의 절반을 같이 푼다**(`OperatorLicenses` 를 두 slice 가 공유한다). **`WorkloadPort` 는
  분리한다** — legacy 도 파생하지 않았고(요청이 싣고 `"auto"` 는 감점 배율만 바꾼다) V2 에 집계 원천이 없다.
  `DerivationAbsence.WorkloadNotCollected` 유지를 명시 결정으로 등재하고, 파생은 아래 ③ 의 기록 표가 생기는
  축으로 미룬다.
- **결정 ③ `OPEN-6F-ACTIVE-BID-DEFINITION` 닫힘 — 활성 투찰 수는 호출자가 싣고, 상한은 전략 필드다(6F-3).**
  실측: V2 표 열넷에 **판정·투찰 기록 표가 없고** `CandidateEvaluation` 을 읽는 코드도 0 이다 — V2 에 투찰 제출
  경로 자체가 없어 「지금 몇 건이 활성인가」는 **시스템 밖 사실**이다. legacy 도 세지 않았다(요청 파라미터
  `current_active_bids`, 결정 레코드에 스냅샷으로 적힘, `workload_source="provided"`). 그래서 **현재 활성 수는
  평가 요청이 싣고**(재현을 위해 입력으로 기록된다), **상한은 운영자마다 다른 정책**이라 전략 표(6F-1)에
  영속·감사되는 자리에 둔다 — 정책 데이터(`VerdictLadderPolicyData.capacityHoldPriorityThreshold` 가 사는 자리)가
  아니다. **「상한만 두고 현재값 0 고정」 안은 기각한다** — 용량 게이트가 꺼진 채 초록이 되고, 그것은 「안 돌린
  게이트는 아무것도 막지 못한다」에 정면으로 걸린다. 기록 표를 세워 진짜로 세는 안은 옳지만 slice 하나가 아니라
  축 하나이고(「투찰 사실을 무엇으로 아는가」가 먼저 승인돼야 한다) 6D·6E 와 얽힌다 — `OPEN-6F3-BID-RECORD`
  로 신설해 그 축에 남긴다. 6F-3 은 **6A 평가 endpoint 선행**이다.

**운영자 결정 둘 2026-09-19 (선택지 + 추천, 둘 다 추천안 채택) — 6F-4 착수 조사가 결정 ① 의 전제 하나를
뒤집었다.** 결정 ① 은 기록으로 보존하고, 아래가 그 조립 구절만 대체한다.

- **결정 A — 키워드 매칭 대상에서 요건 축을 뺀다: 공고명 + 공종.** 결정 ① 의 「`keywordText` = 공고명 +
  요건 + 공종」과 「`fullText` 는 공고명·요건·공종으로만 선다」 **두 구절을 이 결정이 대체한다.** 근거 셋(전부
  실측): (a) legacy 의 `requirements` 는 작업 서술이 아니라 **합성 행정 메타데이터 다섯 줄**이고 그중 둘이
  금액이다. (b) V2 `qualification_text` 는 legacy 의 그 필드가 아니라 **면허제한 오퍼레이션**
  (`getBidPblancListInfoLicenseLimit`) 응답이며 필드가 면허명·허용업종·업종분야다 — 업무 키워드가 들어올
  자리가 아니다. (c) legacy 가 같은 성격의 열(`eligibility_raw`)에 **「현재 소비자 없음」**을 주석으로 적어 뒀다.
  「키워드에서 빼고 지역 대상으로 옮긴다」는 절충안은 **성립하지 않는다** — 그 응답에 지역 어휘가 없다.
  **지역 매칭 대상**은 공고명 + 공종 + **기관명 두 열**(V7 `demand_agency_name`·`notice_agency_name`)이다.
  legacy 가 메타데이터 덤프에서 긁던 지역 단서를 **타입이 있는 열**에서 얻으므로, 기관명이 필수 키워드를
  거짓 만족시키는 오탐 경로가 구조적으로 생기지 않는다.
  **이 결정은 승인 문면을 줄인다** — capability-map STR-02 acceptance 둘째 줄(「같은 키워드가 제목 **또는
  요건**에 있으면 후보」)의 뒷절에 대응할 데이터가 V2 에 없다. 축소를 **`OPEN-6F4-STR02-REQUIREMENTS-AXIS`**
  로 등재하고, 요건 원문을 싣는 축이 생기면 그때 다시 본다.
- **결정 B — 공고명 갱신은 기존 권위 provenance 규율에 맡기고, 「없음」은 센티넬이 아니라 타입으로 닫는다.**
  별도 갱신 규칙을 만들지 않는다. legacy 는 합성 title 을 **센티넬 접두사**로 표시하고 `startswith` 로 되읽어
  덮어쓸지를 정했고, 그 결과 **진짜 공고명이 한 번 들어오면 정정 공고에도 영구 고정**됐다(실측). 재현하지
  않는다 — 값이 없으면 nullable 로 없다.
- **결정 ① 이 남긴 숙제(본문의 출처)의 답 — 본문이 아니었다.** legacy 의 `description` 은 공고 본문이 아니라
  **수집 메타데이터 덤프**(공고기관·공고번호·URL)이고, legacy 자신이 「기관명이 필수 키워드를 거짓 만족시킨다」는
  사유로 키워드 매칭에서 제외한다. KONEPS 목록 응답의 알려진 키 집합에 본문 필드가 없고 본문은
  `bidNtceDtlUrl`·`ntceSpecDocUrl1` **뒤에** 있다 → **`OPEN-6F4-NOTICE-BODY-SOURCE`**.
- **수집→canonical 배선은 6F-4 가 하지 않는다 → `OPEN-6F4-TITLE-WIRING`.** 공고명 표본이 **0건**이라
  (input fixture 119 중 이 키를 가진 것 1개, 그 값도 「공고번호 없는 행」이라는 합성 test 문자열) 배선을 잠그는
  test 의 기대값을 authoritative 하게 세울 수 없고, 실행 진입점도 아직 없다(**6A-3** — 「(6A-1)」은 6A 가 셋으로 갈리기 전 참조다. 6A-1 은 D-6A1-4 로 **읽기 endpoint 하나**에 못박혀 실행 경로를 열지 않는다; 2026-09-19 6A-1 구현 레인 판단, 팀장 확인). 그 slice 의 계약이 함께
  받을 것 둘: **실 DB 가 생긴 뒤 이 열을 되돌리려면 파일 삭제가 아니라 새 V 파일의 `DROP COLUMN`** ·
  **감시 텍스트 두 타입의 생성 경계 폐쇄**(현재 공개 생성자라 조립 함수를 우회할 수 있고, 생성 지점이 54곳이라
  6F-4 범위를 넘는다).

**6F-4 착수 2026-09-19** — base `ede5d5b`(착수는 `48cb072` 였고 진행 중 main 을 흡수 병합), worktree
`bid-vector-v2-m6f4`·브랜치 `m6-6f4/2026-09-18`. 정본 `reports/evidence/m6/6f4/scope.md`(D-6F4-1~9).
**V14** 공고명 열(+ 빈 값 금지 CHECK — 착수 시 V11 이었고 아래 2026-09-19 항에서 재번호) + `procurement` 도메인 슬롯(기본값 null, D-3H-3 형태) + 감시 텍스트
조립 규칙. **되돌리기 어려운 경로가 아니다** — 실 DB 인스턴스·실행 진입점·표본이 전부 0이라 파일 삭제로
완전히 복구된다. Codex 유료 심판의 자격 근거가 없다(착수 시점 분류를 실측으로 철회했다).

**6F-6 착수 2026-09-18** — base `ede5d5b`(PR #36·#37 병합 뒤의 `main`), 레인 worktree `bid-vector-v2-m6f6`·
브랜치 `m6-6f6/2026-09-18`. 정본 `reports/evidence/m6/6f6/scope.md`(D-6F6-1~8). 결정 ② 가 연 축이고
**6F-5 의 절반을 같이 푼다** — `OperatorLicenses` 를 두 slice 가 공유하는데 그 값의 저장소가 저장소 전체에
없다. 레인 점유는 병행 세션에 확인했다(6F-4 는 그 세션, 6F-5·6F-6 은 비어 있음).

**범위 한 가지가 결정문에서 이동한다(D-6F6-1)** — 결정 ② 의 「프로필 표 + **편집 endpoint**」에서 endpoint 는
**6A 로 이관**한다(폐기가 아니다). `6A-1` 착수 계약이 `BidVectorApplication.kt`·`app/build.gradle.kts`·
`PersistenceWiring.kt` 를 이미 in_scope 로 선점했고 **두 레인이 같은 파일을 만들면 병합이 아니라 충돌**이다.
대신 이 slice 가 어댑터의 **저장 진입점까지** 내서 6A 가 HTTP 만 얹게 한다 — 쓰기 경로가 없으면 이 표의 유일한
writer 가 임시 SQL 이 된다.

핵심 결정: **「미설정」·「미선언」·「빈 목록」 셋을 섞지 않는다**(`OperatorLicenses` 가 이미 sealed 로 그 구분을
갖고 있고, 납작해지면 판정이 `Uncertain` 에서 「면허 0개 보유」로 바뀐다) · **`WorkloadPort` 는 구현하지 않는다**
(D-6F6-5, 결정 ② — 파생 원천은 `OPEN-6F3-BID-RECORD` 축) · `businessTypes` 의 `strategy.CategoryCode` 에는
**정규화가 없어** 공고 공종(`procurement.CategoryCode.of`)과 어긋날 수 있으나 그 타입이 사는 `strategy/Text.kt` 는
**6F-4 가 잡고 있어** 열지 않고 `OPEN-6F6-CATEGORY-CODE-NORMALIZATION` 으로 **보이게만** 한다(D-6F6-4) ·
마이그레이션 **V12**(V10 6A-1 · V11 6F-4 선점) · 되돌리기 어려운 경로가 아니라 **Codex 대상 아님**이고
`migration-reviewer` 와 `privacy-gate` 를 붙인다(D-6F6-8 — 새 저장소가 생기는 자리라 「개인정보 필드가 구조적으로
못 들어온다」를 저자가 아닌 쪽이 확인한다).

**6F-6 이 신설해 인계하는 OPEN 둘**(판정 레인 넷의 결과, 계약 갱신 (1)) — ① **`OPEN-6F6-PROFILE-RETENTION`**:
프로필 표에 수명 정책이 없다(GRANT 가 `DELETE`·`TRUNCATE` 를 빼 최소권한은 지키지만 「언제 지우는가」가 없다).
**받는 쪽은 6B-3**(데이터 수명·마스킹 — 승인된 보존 기간이 없어 결정 선행). ② **`OPEN-GATE-REGISTRATION-STALE-INPUT`**:
등재 완결성 게이트 셋(`Profile`·`Evaluation`·`Event`)이 `gate-tests.properties` 를 런타임 `File(...)` 로 읽어
**Gradle 의 선언된 입력이 아니다** — 증분 빌드에서 등재 행만 지우면 `UP-TO-DATE` 로 건너뛰어 초록이 된다
(`--rerun-tasks` 면 붉고, CI 는 clean checkout 이라 영향 없다). 6F-2 가 세운 게이트도 같은 구멍이다 —
**받는 쪽은 하네스 레인**. 그 밖에 **병합 순서 제약**이 하나 선다: Flyway 가 `outOfOrder=false` 이므로
**6F-6(V12)은 6F-4(V11) 뒤에 병합한다**(V10 은 파일이 없어 기다리지 않는다, D-6F6-13).

**그 제약은 2026-09-19 운영자 지시로 뒤집혔고, 6F-4 가 재번호했다.** 운영자가 「병합하고 M6 잔여
진행해」로 지시해 **6F-6(V12)이 먼저 병합**됐다(PR #38). D-6F6-13 의 예측은 기록으로 보존한다 — 틀린
것이 아니라 그 뒤 결정이 우선한 것이다. 남은 것은 **6F-4 의 번호**였고, 그 레인이 **V11 → V14** 로
비켜섰다(D-6F4-7 갱신). 근거: 새 DB 에서는 둘 다 무해하고 영속 인스턴스도 0 이라 **오늘의 안전은
같지만**, V12 뒤에 V11 을 넣으면 「버전 순서 == 병합 순서」 불변식이 **파일 이력에 영구히 깨진 채**
남아 V12 를 적용한 긴 수명 DB 가 생기는 첫날 `outOfOrder=false` 에서 막힌다. 재번호 비용은 파일명
하나다. **V10(6A-1)은 여전히 파일이 없으므로 아무도 기다리지 않는다** — 그 번호는 파일이 생기는
시점에 그 레인이 다시 잡는다.

**6F-1 착수 2026-09-17** — base `c4d09cc`, 레인 worktree `bid-vector-v2-m6f`·브랜치 `m6-6f1/2026-09-17`.
정본 `reports/evidence/m6/6f1/scope.md`(D-6F1-1~6). 전략 표(**V9**) + `JdbcStrategyRepository` + 왕복·개정·
정책 불일치 test. 핵심 결정: 어댑터는 **도메인 타입을 직접 만들지 않고** 행을 초안으로 읽어 **기존 공개 검증
함수**에 통과시킨다(D-6F1-2 — port 시그니처·가시성 무편집, 새 public 표면 0). 6B-1 이 세션에서 만난 벽
(`internal constructor` 라 어댑터에서 생성 불가)과 **같은 계열이지만 처방이 다르다** — 전략에는 이미 문이 있다.
저장된 값이 현 정책으로 무효면 **지어내지 않고 실패**하고(D-6F1-3), 전략 **없음**과 **무효**를 가른다(D-6F1-4).
마이그레이션 번호는 6B-1 V8 · 6F-1 V9 · 6A-1 V10 으로 갈랐다.

**6F-2 착수 2026-09-18** — base `547fd7b`(6F-1·6B-1·M4 종결 병합 뒤의 `main`), 레인 worktree
`bid-vector-v2-m6f2`·브랜치 `m6-6f2/2026-09-18`. 정본 `reports/evidence/m6/6f2/scope.md`(D-6F2-1~8).
6F-1 이 `evaluate()` 의 첫 줄을 세웠고 **둘째 줄이 `candidateSource.openCandidates()`** 다 — 6F 군에서
**설계 미결이 없는 남은 축은 이것 하나**다(6F-3·6F-4·6F-6 은 운영자 결정 대기, 6F-5 는 요구사항 영속 설계
선행, 6F-7 은 타입 체계 연결). `notice` 표(V1)에 행은 있고 없는 것은 **다건 스캔 질의**뿐이라 **이 slice 는
표를 만들지 않는다**(마이그레이션 0 — V10 은 6A-1 이 그대로 잡는다). 핵심 결정: 「입찰 가능」의 **상태 집합을
SQL 리터럴로 적지 않고** 도메인 술어 `isBiddable` 에서 기계 산출해 바인딩하고 **집합 등식**으로 잰다(D-6F2-2 —
정의가 두 벌이 되면 둘째 벌은 컴파일러가 보지 않는다) · 상한은 **조용한 절삭이 아니라 큰 실패**(D-6F2-4 —
port 반환 타입에 「잘렸다」를 실을 자리가 없다) · 순서는 결정적(D-6F2-5 — `analysisBudget` 이 목록 순서를
그대로 쓴다) · 어댑터 패키지 `adapters.evaluation` 신설 + **바이트코드 상수 풀** 의존 게이트(D-6F2-1).
trace 축(`CorrelationIdFactory`·`Clock`)을 같이 싣는다 — 후보 스캔의 마감 경계가 `Clock` 을 실제로 요구한다.

**6F-2 가 `OPEN-6B1-INDEX-GAPS` 에 등재하는 축**(D-6F2-7, 계약 갱신 (2)) — 6B-1 이 「인덱스 추가는 **소비
질의를 가진 slice 가 근거와 함께**」로 남긴 자리에 **첫 소비 질의**가 생겼다: `notice` 를 **입찰 가능 상태
집합 + `deadline_at > now`** 로 걸러 **`deadline_at, notice_number, notice_round` 순**으로 읽는 다건 스캔이다.
6F-2 는 **인덱스를 추가하지 않는다** — 운영 데이터가 0 이라 규모 근거가 없고, 인덱스는 마이그레이션이라
V10(6A-1)과 번호 축이 겹친다. **부분 인덱스로 만들면 그 `WHERE` 술어가 D-6F2-2 가 막 닫은 상태 리터럴을
DDL 에 되살린다** — 추가하더라도 **전체 인덱스**여야 한다는 판정까지가 이 slice 가 남기는 것이다.

**6F-2 수정 라운드 1(2026-09-18)** — verifier r1 이 산출물 HIGH 둘을 **변이 실행으로** 냈다: ① 상태 집합
게이트가 「SQL 에 실린 집합」을 재지 않아 바인딩을 리터럴로 되돌려도 `check` 가 초록이었다 ② 신설 게이트
test 가 `gate-tests.properties` 등재 밖이라 **파일째 삭제해도** 초록이었다. 처방은 계약 갱신 (1)·(2)의
D-6F2-9(거동 등식 + 상수 풀 참조 단언 **두 축**)·D-6F2-10(등재 + **등재 결손 자체를 잡는 완결성 게이트**
신설)이고, 변이 셋(리터럴 되돌림 · 게이트 파일 삭제 · 완결성 게이트 자신의 등재 제거)이 새 HEAD 에서
**전부 붉어짐**을 실측했다. **교훈은 6F-2 고유가 아니다** — 「집합 등식」이 함수와 그 함수 본문 재계산값을
비교하면 거의 항진명제이고, 신설 게이트는 **등재까지가 한 벌**이다.

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

**6F-7 착수·종결 2026-09-23** — base `86093972`, 레인 worktree `bid-vector-v2-m6f7`·브랜치 `m6-6f7/2026-09-23`.
정본 `reports/evidence/m6/6f7/scope.md`(D-6F7-1~12). **배선 순서의 1번**이고 `NotificationRequestPort` 의
production 구현(`OutboxNotificationRequestPort`)을 낸다 — **알림 요청을 outbox 에 넣는 자리까지**이고 실
발송·렌더링은 `OPEN-STR-12` 다.

**착수 조사가 설계를 정했다** — `EventEnvelope` 생성이 `workflow` 모듈에 `internal` 로 닫혀 있고 컴파일
test 가 그것을 잠근다. 그래서 sink 가 `workflow` 안에 있어야 하고, **신설 어댑터 패키지도 마이그레이션도
불필요**하다(outbox V6 가 payload 종류에 무관하다).

**결정 하나를 조사 추천과 반대로 냈다(D-6F7-2)** — payload 에 `PredictionEvidence` 를 **싣는다**. 팀장 실측:
**V2 에 판정 기록 표가 없다**(표 18 전수, `OPEN-6F3-BID-RECORD` 가 그래서 열려 있다). 그러므로 **outbox 행이
그 판정의 유일한 영속 흔적**이고 evidence 를 빼면 **영구히 복원 불가**다. 되돌릴 수 없는 손실 쪽으로 기울이지
않는다. **멱등은 닫지 않는다(D-6F7-6)** — outbox 에 `idempotency_key` UNIQUE 가 없고 그건 4C-1 의 기존
한계다. **닫을 수 없는 것을 닫았다고 적지 않는 것**이 처분이다.

**이 slice 가 남긴 하네스 교훈 — 「안 돌린 게이트는 다른 게이트의 실패를 가린다」.** `contractGate` 가 깨진
git 래퍼(없는 `/opt/homebrew/bin/git` 을 가리킴)로 먼저 죽어 빌드가 `:app:test` 에 **도달하지 못했고**,
그래서 아키텍처 게이트가 **한 번도 안 돈 채** 「전 gate green」으로 보였다. 래퍼를 고치자마자 **패키지 순환**이
드러났다(`embedding → event → evaluation → embedding`). 그 순환은 **팀장 결정(D-6F7-5)이 만든 것**이다 —
codec 의 허용 루트만 보고 위치를 정했고 **그 위치가 만드는 의존 방향**을 재지 않았다. **D-6F7-7** 로 sink 를
`workflow.evaluation` 으로 옮기고 payload 만 `event` 에 남겨 풀었다(방향이 **이미 있는 변**과 같아진다).

**판정 레인 셋** — `privacy-gate` **통과**(payload 전 필드 비-PII, 폐쇄가 **컴파일 실패**로 실측) ·
`code-reviewer` **머지 가능**(MEDIUM 1 · LOW 2) · `verifier` **`not-ready` → HIGH 2, 둘 다 계약 문서**.
**코드·게이트·acceptance 는 전부 초록이었다.** 막은 둘은 **팀장이 쓴 문서**다: ① `in_scope` 가 산출물의
**62%** 를 안 덮어 **clean-tree 게이트가 실제로 눈이 멀었다**(D-6F7-7 로 sink 를 옮기며 YAML 을 안 고쳤다 —
**6A-1 D-6A1-26 과 같은 실수의 두 번째**) ② 신설 OPEN 의 **차단 사유 둘이 거짓**이고 **전제까지 틀렸다**
(경계 게이트는 옮겨진 sink 를 안 덮고, `internal` 생성자는 읽기를 안 막으며, code 로 바꾸면 `toString()` 이
나르는 **임계·확률 수치를 잃는다**). **6A-1 이 다섯 번 겪은 「보장을 지지 않는데 졌다고 적힘」의 거울상** —
**못 한다고 적었는데 할 수 있었다.**

**이 slice 가 신설해 인계하는 OPEN 하나** — **`OPEN-6F7-REASON-CODE-STABILITY`**: outbox 에 영속되는 값이
**Kotlin 합성 `toString()`** 이라 **형식이 조용히 바뀔 수 있다.** 축어 잠금 + 소진 `when` 이 그것을 **「소리
나게」** 했을 뿐이고 **이미 영속된 옛 행은 고치지 않는다.** 받는 쪽 **도메인 레인**. 그리고 **`OPEN-6F-ASSEMBLY`
가 둘을 더 받는다** — `SQLException`→`Failed` 가 선례(전파 → 롤백)와 갈려 **배선 뒤 `inTransaction` 에서
호출부가 `Failed` 를 무시하면 도메인 write 만 커밋되고 outbox 행이 없다**(D-6F7-11) · **하위 소비자의 `Failed`
로깅**은 배선 전이라 확인 불가(privacy-gate).

**6F-7 종결 2026-09-23** — PR **#42** 머지(`02164e44`). 판정 넷을 PR 코멘트로 남겼다(verifier 2라운드 ·
`code-reviewer` · `privacy-gate` · 조치). 좁은 재검증이 **`ready-for-review`**, CI 세 job 전부 통과
(`check` 5m48s · `container` · `ml-engine`). **CI 가 verifier 의 「확인 불가」 하나를 닫았다** — `pypi.org`
도달 불가로 로컬에서 못 돌린 Python step 이 러너에서 돌았다. **두 번째 하네스 교훈**: `check` 가 exit 0
이어도 **해당 모듈의 test task 가 `FROM-CACHE`** 일 수 있다(이번엔 `:workflow:test`, 산출물 test 가 거기
있었다). 앞 라운드의 「`:app:test` 가 `UP-TO-DATE`」는 **재현되지 않았다** — 교훈은 특정 모듈이 아니라
**acceptance 를 캐시 우회로 한 번 재야 한다** 쪽이다.

**6F-4-w 착수 2026-09-23** — base `02164e44`(6F-7 머지 뒤 `main`), 레인 worktree `bid-vector-v2-m6f4w`·
브랜치 `m6-6f4w/2026-09-23`. 정본 `reports/evidence/m6/6f4w/scope.md`(D-6F4W-1~7). **배선 순서의 2번**,
`WatchSubjectPort` 의 첫 production 구현이다.

**착수 조사가 크기를 확정했다 — 마이그레이션도 DB 재조회도 없다.** 전용 「관심대상」 표는 없지만 조립에
필요한 열이 **전부 `notice` 에 있고**(`notice_title` V14 · `business_category_label` V1 ·
`demand_agency_name`/`notice_agency_name` V7 · `base_amount_*` V1) 그 열을 읽는 어댑터도 이미 있다 —
`CandidateSourcePort` 가 내주는 `Notice` 에 다 실려 온다. 이 slice 는 **`Notice` → `WatchSubject` 순수 매퍼**다.

**`OPEN-6F4-TITLE-WIRING` 을 절반만 닫는다(D-6F4W-1).** 그 OPEN 은 **축이 다른 둘**을 한 이름으로 묶고
있었다 — ⓐ 포트 구현(도메인 조립) ⓑ KONEPS 원시 키 → canonical `notice_title` 배선(수집). ⓐ 만 이 slice 고,
ⓑ 는 **`OPEN-6F4-TITLE-INGEST`** 로 신설해 **D-6F4-6 제약**(공고명 키를 어댑터에 하드코딩하지 않는다)과 함께
수집 레인에 넘긴다. 다음 두 단계를 막는 것은 ⓐ 뿐이고 ⓑ 는 **배선이 아니라 값**에만 닿는다.

**승인 문서에 없던 도메인 규칙 하나를 정했다(D-6F4W-2·3).** 「감시 텍스트가 비었을 때 `Found("")` 인가
`Unavailable` 인가」가 어디에도 없었다. **부재는 `Found`, 실패가 `Unavailable`** 로 정한다 — 반대로 정하면
`halt(ScoreNotProvided)` → `WatchSubjectUnavailable` 경로를 타는데, `notice_title` 실 데이터가 **0건**이라
그 선택은 **전건 탈락**이다. 순수 매퍼는 I/O 가 없어 **실패할 수 없으므로**, 매퍼를 `WatchSubject` 를 내는
**전(total) 함수**로 두고 포트가 `Found` 로 감싼다 — 「이 어댑터가 `Unavailable` 을 낸다」가 **test 로 막을
것이 아니라 타입으로 없는 것**이 된다. 대가는 `OPEN-6F4W-UNAVAILABLE-PRODUCER` 로 등재한다(그 variant 의
production 생산자가 0이라 변이 실측이 그 경로를 못 잡는다).

**부재 기초금액의 사유 코드는 `EMPTY_INPUT`(D-6F4W-4).** `ReasonCode` 여덟을 전수 검토했고 나머지는 전부
**값이 있을 때의 실패**다. test fixture 가 쓰는 `POLICY_NOT_APPLICABLE` 은 뜻이 다르다 — **fixture 는 승인
문서가 아니다.** shared-kernel enum 에 값을 새로 만들지 않는다(모든 소진 `when` 에 파급한다).

**6F-4 가 넘긴 숙제를 받는다(D-6F4W-7)** — `KeywordScopeText`·`FullScopeText` 의 공개 생성자 우회는
verifier r2 가 **실측한 구멍**이다. `NoticeTitle` 과 같은 기전(`@ConsistentCopyVisibility` + private 생성자
+ 팩토리)으로 닫고 **negative 컴파일 probe** 로 확인한다 — `internal` 은 모듈 범위라 같은 모듈 test 에서
그대로 열린다. 생성 지점이 `strategy/src/main` 밖 **54곳**이라 **분기점을 걸었다**: 구현 레인이 먼저
main/test × 모듈로 갈라 보고하고, **`strategy` 밖 main 코드에 생성 지점이 있으면 멈춘다** — 부풀리지 않고
별도 slice 로 가른다.

**계약에 명문으로 박은 것** — `in_scope` 가 넓어지면 **계약 갱신 절로 명시하고 rollback 을 재산출**한다.
6A-1(D-6A1-26)·6F-7(HIGH-1)에서 **두 번 연속** 「파일이 움직였는데 `in_scope` 를 안 고쳐 clean-tree 게이트가
눈이 멀었다」가 났다. **세 번째를 만들지 않는다.**

**6F-4-w 종결 2026-09-23** — PR **#43**(head `f9624326`, 계약 갱신 절 넷·D-6F4W-1~16). 판정 넷 + 조치를 PR 코멘트로
남겼다(verifier r1 `not-ready` → r2 `ready-for-review` · `code-reviewer` 머지 가능 · `privacy-gate` 위반 0 · 조치).
재작업 **1/5**. CI 세 job 전부 통과(`check` 4m57s · `container` 2m16s · `ml-engine` 49s). Codex 는 대상 아님.

**r1 HIGH 는 게이트 술어의 결함이었다 — 「있는지만 보는」의 변형.** 컴파일 probe 의 기대 조각 `"cannot access"`
가 `private` 과 `internal` 을 구별하지 못해, 생성자를 `internal` 로 퇴행시켜도 `check` 전체가 초록이었다 —
D-6F4W-7 이 「`internal` 로 때우지 않는다」를 명시했는데 그 probe 가 바로 그 퇴행을 통과시켰다. 기대 조각을
`"it is private in"` 으로 좁히자 퇴행 변이가 4건 RED(D-6F4W-13). **같은 라운드의 MEDIUM 도 같은 계열**이다 — 우회 3
의 부재 단언이 이름 셋의 금지 목록이라 `String.join` 복제가 통과했고, 상수 풀 **허용 목록**(⊆)으로 바꾸자 템플릿·
`String.join`·`String.format`·multifile facade 변이가 전부 RED(D-6F4W-14). **둘 다 리뷰는 못 잡고 변이 실측만 잡았다**
(M6 에서 세 slice 연속).

**폐쇄가 닫은 것은 생성 경로이지 내용의 출처가 아니다(D-6F4W-15).** `assemble*` 의 인자가 `String?` 이라 요건 텍스트를
넣으면 컴파일된다 — 6F-4 r2 시나리오가 철자만 바꿔 재현된다. 위협 모델이 「원천 값의 정직성」을 경계 밖에 두었고 오늘
production 호출자는 어댑터 하나(`Notice` 타입 입력)라 이 slice 는 게이트를 세우지 않고 **문면을 고쳤다**(「실릴 길이 타입으로
없다」류 전칭 삭제). 「어댑터만 부른다」는 오늘 **실측이지 구조가 아니다** — **`OPEN-6F4W-ASSEMBLE-CALLER`**(호출자 집합
== {`NoticeWatchSubjectPortKt`}, app ArchUnit 층 집합 등식)를 신설해 **`OPEN-6F-ASSEMBLY` 의 전제 조건**으로 걸었다. 배선이
호출자를 더하기 전에 닫혀야 한다. `of` 가 `internal` 인 것도 Kotlin 한계가 아니라 설계 선택(`NoticeTitle` 선례)으로 정정했다.

**신설 OPEN**: `OPEN-6F4-TITLE-INGEST`(수집 절반) · `OPEN-6F4W-UNAVAILABLE-PRODUCER` · `OPEN-6F4W-ASSEMBLE-CALLER`.
`OPEN-6F-ASSEMBLY` 재확인 항목 ①~④(privacy-gate U-1·R-1)를 scope.md OPEN 표에 등재했다.

**장부 사실**: 수정 라운드 커밋 6개에 `Co-Authored-By` trailer 가 없다(evidence 의 SHA 앵커 보존을 위해 되쓰지 않음).
검토 레인은 장비 리부팅으로 한 번 유실돼 같은 지시문으로 재발사했다(판정 SHA 불변). evidence 크기 여유가 16줄(825 ≤ 841)
— 다음 slice 는 착수 시 evidence 예산을 먼저 잡는다.

**6A-3+6F-3 착수 2026-09-23** — base `febad567`(6F-4-w 머지 뒤 `main`), 레인 worktree `bid-vector-v2-m6-6a3f3`·브랜치
`m6-6a3f3/2026-09-23`. 정본 `reports/evidence/m6/6a3f3/scope.md`(D-6A3-1~11). **배선 순서의 3번과 4번을 한 slice 로**
— endpoint 가 조립의 뿌리라 배선 없이는 돌지 않는다.

**운영자 결정 넷(2026-09-23, 선택지 + 추천, 전부 추천안)**: ① `MlAnalysisPort` 는 **자리지킴 유지**(실 배선은
`OPEN-ML-ANALYSIS-WIRING` 신설) ② endpoint 는 **dry-run 전용**(D-6A1-4 문면 그대로 — outbox 에 쓰지 않고 「낳았을 알림
요청」의 공고 ID 만 응답. 커밋 경로는 `OPEN-6A3-EVALUATION-COMMIT` 신설, 6F-7 인계 둘이 그리로) ③ **포트 아홉 배선
흡수**(`OPEN-6F-ASSEMBLY` dry-run 닫음, `OPEN-6F4W-ASSEMBLE-CALLER` 도 여기서 세운다) ④ **Codex 없음**(V16 은 nullable
INT 한 열 — `migration-reviewer` + `verifier`).

**착수 조사가 결정 ③ 의 전제 하나를 드러냈다 — 여력 상한 필드가 없다.** 「상한은 전략 표에 영속·감사되는 자리」인데 V9
두 표와 `OperatorStrategy` 에 그 자리가 없다(`candidate_limit` 은 후보 상한). **V16** 으로 `max_active_bids` 를 두 표에
더하고(nullable, DEFAULT 없음) `StrategyDraft`→`validate()`→`OperatorStrategy` 로 올린다. **상한 미설정은 fail-closed**
(409) — 결정 ③ 이 「현재값 0 고정」을 기각한 이유의 대칭이다. 편집 경로는 6A-2 소관(`OPEN-6A3-MAX-ACTIVE-BIDS-EDIT`).

**응답은 평탄하게 둔다(D-6A3-6).** 6A-1 이 세 라운드로 닫은 D-6A1-38 평탄 게이트(object 배열 거부)를 넓히지 않는다 —
결과별 공고 ID 배열 + 건수로 답하고, 후보별 사유 상세는 `OPEN-6A3-EVALUATION-DETAIL`(contract-keeper 결정 뒤).
`wouldNotifyNoticeIds == bidNowNoticeIds` 불변식이 사다리와 알림 경로가 같은 판정을 본다는 것을 잠근다.

**6A-3+6F-3 종결 2026-09-24** — PR **#44**(head `be58cec5` + 이 문단, 계약 갱신 절 여덟·D-6A3-1~26). 판정 일곱 + 조치를 PR
코멘트로 남겼다(verifier 3라운드 · `code-reviewer` · `migration-reviewer` · `privacy-gate` · `contract-keeper`, 뒤 넷은 전용 정의 부재로
범용 에이전트 대행). 재작업 **3/5**. CI 세 job 전부 통과(`check` 7m48s · `container` · `ml-engine`). **`EvaluateCandidatesUseCase` 가
처음으로 production 조립에서 돈다** — 배선 4단계의 3·4번이 닫혔다(`OPEN-6F-ASSEMBLY`·`OPEN-6F4W-ASSEMBLE-CALLER`·
`OPEN-6F2-CANDIDATE-BOUND` 닫힘).

**세 라운드 전부 「코드는 옳고 게이트가 못 잰다」였다.** r1 은 게이트 셋이 이름 목록·패키지 하나·파일명 접두에 걸려 계약이 닫았다고 적은
우회(outbox 구현 주입·루트 패키지 실 ML 참조·헬퍼 경유 포트 호출)가 초록이었다. r2 는 팀장이 쓴 처방 문면(「port 인터페이스 × `app.http`」)
자체가 두 축의 좁힘이라 한 걸음씩 옮긴 변이가 초록이었다 — **(호출자, 포트.메서드) 호출 쌍 허용 목록, 호출 대상은 구현 타입 전부**로 닫자
r3 가 스무 가지 우회를 두드려 전부 RED 였다. r3 의 BLOCKER 는 장부 커밋이 evidence 에 스캔 어휘를 축어로 적은 것 하나였다. **변이 실측
없이는 셋 다 초록으로 머지됐을 것이다** — 6F-4-w 에 이어 같은 교훈이다.

**배선 slice 의 교훈 — in_scope 를 여섯 번 넓혔다.** 산출물 경로만 적은 착수 계약이 세션 스냅샷(workflow)·스키마 정확 열거 게이트·
`app/build.gradle.kts`·게이트 fixture 경로에 차례로 부딪혔고, 넷은 커밋 뒤 흡수였다. 전부 「기존 축에 걸친 additive 변경」이라 예견
가능했다. **다음 배선 slice 는 착수 계약에 게이트·fixture·스키마 열거 게이트·build 파일을 처음부터 넣는다.**

**넘긴 것**: `OPEN-6A3-APP-HTTP-DEPENDENCY-ALLOWLIST`(신설, r3 MEDIUM — 포트 메서드를 **호출하지 않는** 지름길 셋: 어댑터 추가 메서드 ·
생성자에서 읽는 어댑터 · `app.http` raw SQL. 오늘 그 경로를 여는 production 코드 없음. `app.http` 의존 허용 목록 + 포트 집합 도출을
**6A-2** 가 받는다) · `OPEN-ML-ANALYSIS-WIRING` · `OPEN-6A3-EVALUATION-COMMIT`(outbox 커밋 경로 — 6F-7 인계 둘) ·
`OPEN-6A3-EVALUATION-DETAIL` · `OPEN-6A3-MAX-ACTIVE-BIDS-EDIT`(6A-2). 컨트롤러 `runBlocking` 은 클라이언트 연결 종료를 전파하지 않는다
— 오늘은 어떤 포트도 suspend 하지 않아 무해, 실 ML 배선에서 재검토.

**6F-8 착수 2026-09-24** — base `74616367`(6A-3+6F-3 머지 뒤 `main`), 레인 worktree `bid-vector-v2-m6-6f8`·브랜치
`m6-6f8/2026-09-24`. 정본 `reports/evidence/m6/6f8/scope.md`(D-6F8-1~5). 운영자 지시 「관련한 배선 진행하고, 수집은 최근 한달
이내의 데이터만 수집해서 기능 완료 확인」. **수집 부품은 전부 있는데 잇는 자리가 없었다** — 수집 use case 0 · app 실행 진입점 0 ·
공고명 원시 키가 필드 계약에 없음(`OPEN-6F4-TITLE-INGEST`). 그래서 V2 DB 의 공고는 0건이었고 평가 endpoint 는 빈 후보로만 돌았다.
이 slice 는 use case·기본 꺼진 일회성 러너·공고명 계약 행을 세우고, **실제 KONEPS 최근 30일(공사 + 용역)을 V2 전용 개발 DB 에
수집해 평가 dry-run 까지** 확인한다. 운영자 결정 셋: 서비스 키는 legacy `.env` 에서 환경변수로만 · 개발 DB 신설 · 공사 + 용역.

**6F-8 종결 2026-09-24** — PR **#45**(계약 갱신 절 셋·D-6F8-1~13). 판정 다섯 + 조치를 PR 코멘트로 남겼다(verifier 3라운드 ·
`code-reviewer` · `privacy-gate` 대행). 재작업 **2/5**. **수집 → 정규화 → 영속 → 평가 dry-run 이 실데이터로 끝까지 돈다** — 실 KONEPS
최근 30일(공사 + 용역, 슬롯 62) 세 번: 공고 22,639건 전부 수집·정규화(최종 탈락 0), 공고명 채움 100%, 재실행 멱등(슬롯별 등식 위반 0),
로그의 키 흔적 0. 평가 dry-run 에서 키워드 규칙이 공고명으로 **정확히** 매칭됐다(「도로」 마감 전 공고 수 == review 수).

**합성 fixture 로는 못 잡는 결함이 실데이터에서 바로 나왔다.** 1차 실수집의 탈락 1,770 중 1,765 가 빈 입찰마감일시(OPTIONAL)였다 —
빈 값을 「해석 실패」로 다뤄 공고 전체를 버렸다(대부분 용역). 기존 스윕 test 는 빈 값을 넣고도 「던지는가」만 봐서 초록이었다.
같은 계열 둘(빈 금액 후보·빈 업무구분 라벨)과 함께 「그 필드만 부재」로 고치자 3차 탈락이 0 이 됐다. 그 전에 code-reviewer 가 정적으로
찾은 HIGH(형식 위반 차수 한 건이 예외로 **수집 전체를 죽인다**)도 실데이터를 붙이기 전에 닫아야 했던 자리다 — `canonicalize` 가 입력값으로
던지는 자리 20행을 전수해 사유 있는 탈락으로 접었다. **실데이터를 처음 붙이는 slice 는 「던지는가」가 아니라 「결과 종류」를 잠가야 한다.**

**부수 발견 — 배포물이 부팅하지 못했다.** `bootJar` 에 `kotlin-reflect` 가 없어 `java -jar` 가 즉사했다(6A-1 부터). test classpath 에는
있어 production 조립을 부팅하는 test 가 전부 초록이었다 — 배포물 내용 test 로 잠갔다.

**넘긴 것**: **`OPEN-6F8-BUSINESS-CATEGORY-SOURCE`(운영자 결정)** — 업무구분이 오늘 항상 비어 있다(운영 계약에 코드 행이 없고 목록 응답에
키도 없다) → 「관심 업종」 규칙이 어떤 공고와도 맞지 않는다 · **`OPEN-6F8-RAW-PII-RETENTION` → 6B-3**(원문 행 전부에 담당자 키,
관측마다 추가 전용) · `OPEN-6F8-NUL-CHARACTER` · `OPEN-6F8-QUOTA-XML-ENVELOPE` · `OPEN-6F8-COLLECTION-RUN-CATEGORY` ·
`OPEN-6F8-NON-THROWING-FACTORIES` · `OPEN-6F8-COLLECTION-SCHEDULE` · `OPEN-6F8-OPENING-COLLECTION`. 마감이 비어 있는 공고(1,765)는
후보 조건상 평가에 오르지 않는다. 개발 DB 컨테이너 `bid-vector-v2-dev` 는 로컬에 남아 있다(폐기는 사용자 결정).

**6F-9 착수 2026-09-24** — base `f6ebc047`(6F-8 머지 뒤 `main`), 레인 worktree `bid-vector-v2-m6-6f9`·브랜치 `m6-6f9/2026-09-24`.
정본 `reports/evidence/m6/6f9/scope.md`(D-6F9-1~5). 사용자: 「업무구분은 … 입찰에 필요한 정보일테니 수집 해야 맞을것 같아」.
**6F-8 실수집이 드러낸 공백의 원인은 「보던 자리」였다** — 대분류(물품·용역·공사·외자)는 오퍼레이션 자체라 응답에 필드가 없고, 세부
분류는 다른 키로 있다(용역구분 100% · 공공조달분류 거의 100% · 공사 주공종 약 33%). 대분류는 **수집 오퍼레이션**에서, 세부는 **각자의
칸**에 — P-7·COL-08 의 「업무구분 축을 한 자리에 접지 않는다」를 지킨다. V17 열 셋, 감시 「관심 업종」 집합이 네 값을 모두 본다.
공사 주공종의 빈 값은 면허제한 수집과 함께(`OPEN-6F9-CONSTRUCTION-TYPE-SOURCE`).

**6F-9 종결 2026-09-26** — PR **#46**(계약 갱신 r1: D-6F9-6 잠금 test 자리). 판정 일곱과 조치를 PR 코멘트로 남겼다. 레인은 verifier 3라운드
(r1 not-ready → r2·r3 ready-for-review), `code-reviewer`(sonnet) 2라운드, `migration-reviewer`(범용 에이전트 대행, approve)다. 재작업 **2/5**.
2026-09-24 WSL 정지로 앞 세션의 acceptance·rollback 실측 기록이 사라져 **처음부터 다시 쟀다**. **업무구분 네 칸이 실데이터로 찬다** —
6F-8 과 같은 범위(2026-08-25~09-24, 공사 + 용역, 슬롯 62)를 V17 위에서 재수집했다. 결과는 기존 22,639건 전부 `updated`, 대분류 채움 100%
(용역 12,780 · 공사 9,860), 용역구분 100%, 분류번호 99.96%, 주공종 32.9%다. 축 섞임·빈 문자열·오퍼레이션 겹침은 0 이고 로그의 키 흔적도 0 이다.
새 공고 1건(`inserted=1`)은 그 사이 원천 수신이 22,639 → 22,640 으로 늘어난 몫이다. 평가 dry-run 에서 두 축 모두 **집합 수준으로 정확**하다.
「관심 업종 = 기술용역」은 감시 통과 712 == SQL 712(ID 집합 동일)이고, 키워드 「도로」는 186 == SQL 186 이다 — 6F-8 의 163 에서 늘어난
23 이 세부 이름이 키워드 범위에 들어간 몫이다. 이 dry-run 의 전략은 **팀장이 개발 DB 행에 직접 썼다가 되돌렸다**. 운영자 API 에 전략
쓰기 경로가 없기 때문이다(아래 OPEN).

**게이트는 「이름을 열거하면 이름 밖이 열린다」를 한 번 더 보였다.** 첫 대분류 게이트는 문자열 → 대분류 멤버 넷을 이름으로 봤다. 그래서
배선의 `when (path)` → `BusinessDivision.CONSTRUCTION`(D-6F9-1 이 금지한 URL 파싱 그 자체), enum 안 새 함수, `java.lang.Enum.valueOf` 가 전건
`check` 를 통과했다. 세 축(멤버 접근 · 값 획득 · 클래스 객체)으로 바꾸자 관용 표기는 전부 잡혔다. 다만 **타입 소거·리플렉션**(컨테이너 원소,
`getEnumConstants`, 역직렬화 타입 토큰)은 여전히 못 본다. 이번 라운드는 게이트를 넓히지 않고 **주장을 좁혀** 알려진 제한으로 적었다.
계약이 요구한 ML 파급 잠금 test 가 첫 구현에 없던 것은 verifier 와 code-reviewer 가 각자 잡았다.

**넘긴 것**: **`OPEN-6F9-DIVISION-REFLECTION`**(리플렉션 루트를 `procurement`·`adapters` 로 넓히거나 `BusinessDivision` 의존 축 추가) ·
**`OPEN-6F9-STRATEGY-WRITE-ENDPOINT`**(관심 업종을 운영자가 설정할 경로가 없다 — `EditStrategyWorkflow` 의 호출자는 test 뿐이다, 받는 쪽
**6A-2**) · `OPEN-6F9-ML-CATEGORY-CODE-SPACE`(용역 분류번호가 ML 입력이 된다, `OPEN-ML-ANALYSIS-WIRING` 과 함께) ·
`OPEN-6F9-CONSTRUCTION-TYPE-SOURCE` · `OPEN-6F9-GOODS-FOREIGN-COLLECTION` · **`OPEN-KTLINT-UNUSED-IMPORTS-SILENT`**(ktlint 1.8.0
`ktlint_official` 의 `no-unused-imports` 가 켜져 있는데도 쓰지 않는 import 두 줄을 보고하지 않았다 — 구현 레인이 해당 태스크 단독 재실행으로
exit 0 을 실측했다. 이 저장소에는 쓰지 않는 import 를 잡는 게이트가 없다. 받는 쪽 **하네스 레인**, 운영자 등재 결정 2026-09-26) ·
`OPEN-API-WRONG-METHOD-500`(매핑된 경로에 지원하지 않는 메서드로 요청하면 405 가 아니라 500 — 상태 코드만 관측한 단서, **6A-2**).

**6A-2 분할 · 6A-2a 착수 2026-09-26** — base `4dc17214`(6F-9 머지 뒤 `main`), 레인 worktree `bid-vector-v2-m6-6a2a`·브랜치 `m6-6a2a/2026-09-26`.
정본 `reports/evidence/m6/6a2a/scope.md`(D-6A2a-1~9). 사용자: 「원래의 권장 계획 대로 진행」 — 6A-2 를 **2a 앱 이미지 + health** 와 **2b 세션 편집
endpoint** 로 가른다. endpoint 는 인증·쓰기 경로라 따로 판정받는다. 6C 가 D-6C-1 로 넘긴 자리(「`main()` 이 없어 실행할 것이 없다」)는 6A-1·6F-8 뒤로 전제가 섰다.
핵심 결정은 셋이다.
- **health 는 Actuator 를 별도 관리 포트에 낸다.** API 포트의 인증·audit 체인에 경로 예외를 두지 않는다 — 경로 예외는 정규화 우회의 문이다.
- **이미지는 밖에서 만든 `bootJar` 를 layer 로 푼다.** 이미지 안에서 Gradle 을 돌리지 않는다(호스트 빌드 동시 실행 제한).
- **위생 게이트는 이미지별 정책 파일**로 일반화하고, 앱 이미지는 JVM 구조(컴파일러 부재 · test 좌표 0)로 판정한다.

ML 은 자리지킴 그대로이고, compose 기동은 수집을 켜지 않는다. Codex 는 없다(인증 필터 편집 금지가 계약이다).

**6A-2a 종결 2026-09-26** — PR **#47**(계약 갱신 둘: D-6A2a-10~17). 판정 열 건과 조치를 PR 코멘트로 남겼다. 레인별 판정 흐름:

| 레인 | 판정 |
|---|---|
| verifier | 4라운드: not-ready ×2 → ready-for-review ×2 |
| `code-reviewer`(sonnet) | 3라운드: → APPROVE |
| `privacy-gate`(범용 대행) | 3라운드: → pass |

재작업 **3/5**. CI 세 job 은 전부 통과했다(`check` 9m7s · `container` 3m48s · `ml-engine`). 앱이 처음으로 이미지로 뜬다 — 두 이미지 모두 위생 게이트를 통과하고 세 서비스가 healthy 로 수렴한다. 스모크가 뜬 컨테이너에서 인증 경계와 health 격리를 잰다. 인증·audit 필터 두 파일은 diff 0 이다.

**세 라운드의 차단 결함은 한 계열이었다 — 「환경이 관리 표면을 넓힐 수 있다」.** 매 라운드 닫은 방법과 그다음 드러난 구멍은 이렇다.
- r1 은 잠금이 **이름으로 열거한** 10 키만 막았다. 그룹별 세부·새 그룹·상태 매핑이 환경변수 한 줄로 열렸다.
- 그래서 **접두사 거부**(구성)로 바꿨다. 그러자 r2 에서 **시점**이 드러났다 — 거부는 기동 초기에 선 소스만 봤고, refresh 중에 채워지는 servlet context 파라미터 소스가 출하 이미지에서 r1 결함 셋을 전부 다시 열었다.
- **모든 소스가 선 뒤의 재검사**로 닫았다. r3 는 「readiness 전」이 test 로 잠겨 있지 않음을 보였고(검사를 뒤로 옮겨도 초록), 기록기 test 로 잠갔다.

**열거를 구성으로 바꾸면 다음 결함은 시점에서 온다** — 게이트 술어는 「무엇을」과 「언제」를 둘 다 닫아야 한다.

**넘긴 것**:
- 신설 `OPEN-6A2A-DISTROLESS` · `OPEN-6A2A-MGMT-PORT-EXPOSURE`(관리 포트 노출은 배치 환경이 진다)
- 신설 `OPEN-6A2A-PRE-CONNECTOR-CHECK` — 포트가 재검사보다 먼저 열린다. 지금 그 창에 닿는 경로는 조기 거부가 막는다.
- **기동 거부 운영 결과는 6E runbook 입력이다**: 포트·주소 말고 다른 `MANAGEMENT_*`·`SPRING_JMX_*` 는 기동을 거부한다. k8s `management` Service 의 service link 변수도 거부 대상이다.
- 6A-2b 로(무변경): `OPEN-6F9-STRATEGY-WRITE-ENDPOINT` · `OPEN-6A3-MAX-ACTIVE-BIDS-EDIT` · `OPEN-6A3-APP-HTTP-DEPENDENCY-ALLOWLIST` · `OPEN-6A1-CREDENTIAL-RAW-REINTRODUCTION` · `OPEN-6A1-SCAN-FILTER-SIDE-EFFECT`(실측 영향 0) · `OPEN-API-WRONG-METHOD-500`(관리 포트 비 GET 500 관측 추가)
- 관측: `ml-serving:local` 336MB / 상한 400MB.

**운영자 결정 2026-09-26(6A-2a 머지 뒤 잔여 점검)** — 결정 대기로 남아 있던 넷을 정했다.
① 다음 slice = **6A-2b, Codex 없음**(verifier + code-reviewer sonnet) ② **6B-3 보존 기간 90일**(공고·평가 이력·audit — 6F-6·6F-5-a·6A-1 이 넘긴
`OPEN-…-RETENTION` 의 입력) ③ **6B-4 큐 상한 초과 시 가장 오래된 대기 job 부터 폐기**(최신성 우선) ④ **6F-5-b 실 LLM 요건 추출 승인 +
`OPEN-ML-ANALYSIS-WIRING` 실 gRPC 배선 승인** — 조건: 「LLM 추출과 ML gRPC 를 비교할 수 있도록 적절한 패턴을 쓴다」. 비교 구조(같은 입력을 두 경로에
흘려 같은 형태로 기록하고 판정에는 한쪽만 쓰는 shadow 형태 등)는 해당 slice 계약에서 설계한다. 이 결정들은 실 외부 호출의 **범위**를 승인한 것이고,
각 slice 의 실행(호출 횟수·비용 상한)은 그 계약에서 다시 확인한다.

**결정 ④ 개정 2026-09-27(운영자)** — **`OPEN-ML-ANALYSIS-WIRING` 실 gRPC 배선은 보류한다. 먼저 가격 경로 백테스트 slice 를 둔다.** 근거는 팀장 리서치다.
① 사정률은 제도상 난수다 — 15구간 균등 난수 + 번호 무작위 배열, 4개 평균의 표준편차 약 0.51%p(±2%)·0.77%p(±3%). 공개된 딥러닝 예측 RMSE(0.758%)는
「항상 100%」 기준선과 같거나 나쁘다. ② legacy 실측: LSTM 은 「학습할 신호가 없다」로 은퇴했고, LightGBM 이 그룹 평균을 이긴 흔적(잔차 sd −8.3%)은
겹치지 않는 창에서 판정할 수 없었다. **균등 난수 기준선 비교는 한 번도 없었다.** ③ 남는 이득은 사정률 예측이 아니라 **경쟁자 밀집 회피**다(몬테카를로 추정
1/N 대비 1.3배, 경쟁자가 몰리면 수 배). 백테스트는 2026-09-07 사전 등록 결정 실험 D-ML-2(`docs/discovery/ml-value-and-data-locality.md` §4)를 V2 개찰 데이터로 실행한다 —
S0 밴드 내 균등 난수 · S1 규칙 앵커 · **S2 V2 분포 엔진**(V2 가 서빙하는 것은 GBM 이 아니라 분포 엔진이다 — 5D-2 D-5D2-1 (b); 이 문단 초판의 「기존 ML 대 이론 분포」 구분은 틀렸다, 2026-09-27 팀장 정정) · S3 GBM(데이터 없음, N/A) · S4 제도 분포 + 경쟁자 분포 몬테카를로. 정본은 **6G** 계약(`reports/evidence/m6/6g/scope.md`). 경쟁자 분포에 쓸 **개찰 순위 전체**(참가자별 투찰금액)를 KONEPS 에서 얻을 수 있는지가 선행 조사다 — legacy 는 낙찰자만 가졌다.
**6F-5-b 실 LLM 요건 추출 승인은 그대로다**(가격과 무관한 축 — LLM 은 V2·legacy 어디서도 가격 예측에 쓰이지 않는다).

**6A-2b 착수 2026-09-26** — base `6f0b21f0`(PR #47 머지 뒤 `main`), 레인 worktree `bid-vector-v2-m6-6a2b`·브랜치 `m6-6a2b/2026-09-26`. 정본
`reports/evidence/m6/6a2b/scope.md`(D-6A2b-1~13). 세션 편집 endpoint 여섯을 `EditStrategyWorkflow` 위에 세운다 — 모든 전략 쓰기가 편집 세션을 지나고,
적용은 전략·outbox·세션을 **한 트랜잭션**으로 커밋한다(4A 잔여 창 폐쇄). 인증·audit 필터 무편집, 외부 effect 0, 마이그레이션 없음을 목표로 한다.
받는 OPEN 여섯 중 다섯을 닫고(`OPEN-6F9-STRATEGY-WRITE-ENDPOINT` · `OPEN-6A3-MAX-ACTIVE-BIDS-EDIT` · `OPEN-6A3-APP-HTTP-DEPENDENCY-ALLOWLIST` ·
`OPEN-API-WRONG-METHOD-500` · `OPEN-6A1-SCAN-FILTER-SIDE-EFFECT`), `OPEN-6A1-CREDENTIAL-RAW-REINTRODUCTION` 은 재측정 후 6E 로 넘긴다.

**6A-2b 종결 2026-09-27** — PR **#48**(계약 갱신 여섯 라운드: D-6A2b-14~54). 판정은 PR 코멘트 여섯에 있다. 재작업 **5/5 + 운영자 승인 초과 1** —
verifier 는 r1~r5 not-ready, **r6 ready-for-review**, code-reviewer 는 r6 까지 REQUEST_CHANGES. **r6 사전 규칙**(경계 안 HIGH 하나면 게이트 분리 종결)이
code-reviewer 의 N-r6-1(주입 표면 전개가 배열·`vararg` 를 버린다 — 한 줄)로 발동해, 수정 라운드 없이 **게이트 분리 종결**했다. 산출물(endpoint 여섯 · 한 트랜잭션
원자성 · 교차 세션 기준 revision · 오류 매핑 · OpenAPI · 관리 포트 405)은 초판부터 서 있었고, 여섯 라운드는 전부 **HTTP 층 의존 게이트** 하나에 들었다.

**게이트 여섯 라운드의 계열** — r1 금지 열거 → r2 대상 종류 열거(RouterFunction·Filter) → r3 면제 층·허용 통로(Tomcat valve·interceptor) → **운영자 결정
(A) 구조 재건**(위협 모델 경계 개정 + 의존 방향) → r4 능력 전달(②층이 쥔 쓰기 포트로 use case 자체 조립) → r5 주입 값(①층 SQL 클로저를 DI 로) → **축 전환**
(「무엇을 이름으로 아는가」→「무엇을 받을 수 있는가」, 주입 표면은 유한하다) → r6 배열 한 겹. **교훈**: ① 게이트가 막으려는 주체를 먼저 한정하라 — 조립 근을 믿지
않으면 in-tree 게이트는 서지 않는다(D-6A2b-51, 1A 빌드 저자 경계와 같은 뿌리) ② 「아는 것」을 좁히는 게이트는 「받는 것」으로 새는 능력을 못 본다 ③ 계약 문장 ↔
구현 심볼 대조표에 「이행」이라 적고 실제로 다른 것이 r4·r5 차단의 절반이었다 — 대조는 저자 아닌 레인이 독립으로 한다.

**넘긴 것**: 신설 `OPEN-6A2B-INJECTION-HARDENING`(첫 항목 N-r6-1 · meta-gate 발견 · 상속 SAM · 콜백 세터) · `OPEN-6A2B-COMPOSITION-ROOT-HARDENING`(①층 SQL
몸통 선택 R1·R8 · 정적 슬롯 M-r6-1) · `OPEN-6A2B-LOCATOR-BAN`(→ 리플렉션 봉쇄 레인) · `OPEN-6A2B-ABANDONED-SESSIONS`(→ 6B-3, 보존 90일) ·
`OPEN-6A2B-DRYRUN-ASSEMBLY-IN-APP` · `OPEN-6A2B-VIOLATION-DETAIL` · `OPEN-6A2B-CONCURRENT-SESSION-ADVANCE` · `OPEN-6A1-CREDENTIAL-RAW-REINTRODUCTION`(→ 6E).
닫은 것: `OPEN-6F9-STRATEGY-WRITE-ENDPOINT` · `OPEN-6A3-MAX-ACTIVE-BIDS-EDIT` · `OPEN-6A3-APP-HTTP-DEPENDENCY-ALLOWLIST` · `OPEN-API-WRONG-METHOD-500`(관리 포트
포함) · `OPEN-6A1-SCAN-FILTER-SIDE-EFFECT`(전제 「영향 0」은 거짓이었다 — test classpath 에서 `@TestConfiguration` 을 걷어냈다). rollback 의 공유 파일 절차는
이 종결 문단도 같은 문단 단위 삭제로 지운다.

**6G 착수 2026-09-27 — 가격 경로 결정 실험** — base `678c6ed7`(PR #48 6A-2b 머지 뒤 `main`), 레인 worktree `bid-vector-v2-m6-6g`·브랜치
`m6-6g/2026-09-27`. 정본 `reports/evidence/m6/6g/scope.md`(D-6G-1~14, 운영자 승인 A-1~A-5 · 운영계정 키). 2026-09-07 사전 등록 결정 실험 **D-ML-2** 를
V2 자체 개찰 데이터로 실행한다 — S0 밴드 내 균등 난수 · S1 규칙 앵커 · S2 V2 분포 엔진(세 후보) · S3 GBM(N/A) · S4 제도 분포 + **실측** 경쟁자 분포(개찰완료
오퍼레이션의 참가자 전 행) 몬테카를로. 산출물은 판정 하나: 분포 엔진을 운영 경로에 꽂을 것인가(`OPEN-ML-ANALYSIS-WIRING` 의 입력). 층화 무작위 24,000 공고 ·
호출 상한 일 20,000 / 총 80,000 · 실수집 전 `OPEN-6F8-QUOTA-XML-ENVELOPE` 폐쇄 · 제외 15 · 공고일 기준 창. 리뷰 레인 verifier + code-reviewer(sonnet) +
privacy-gate, Codex 없음.

**6G 종결 2026-09-30 — D-6G-76 분리 종결, 실수집 차단** — PR **#50**(계약 갱신 r1~r5·r5-t: D-6G-27~81). 판정은 PR 코멘트 여섯에 있다. 재작업 **5/5 + 운영자 승인 표적 수정 1**(D-6G-68~74: 원장의 AXIS 줄이 걷기를 가리킨다 · `budget-since` 제거 · 찢어진 끝 줄 복구 · 추출은 잠금 안 · 공고 목록 seed 잠금). verifier 는 r1~r5 not-ready, **r5 표적 not-ready(D-6G-76 발동)**. r5 의 실수집 차단 결함 H-1(끊긴 걷기의 행이 완료 행으로 실림)은 재현되지 않았으나 표적 수정이 **새 high 셋**(데이터 정확성)을 만들었다 — 복구 순서(D-6G-70 미이행, 다음 기동 영구 거부 → 상한 0 재시작) · 목록 축이 가장 오래된 관측을 씀(개발 DB 의 6F-8·6F-9 행에서 공고일·낙찰방법 null) · 옛 형식 AXIS 줄. 계약대로 더 고치지 않고 머지했다: **실 KONEPS 수집은 6G-2d(`OPEN-6G-RUN-STATE-HEAL-ORDER` · `OPEN-6G-LIST-AXIS-WALK-SELECTION` · `OPEN-6G-LEGACY-AXIS-LINE`) 머지 뒤에만**(D-6G-77). 게이트 하드닝은 D-6G-75 분리 종결(`OPEN-6G-SENSITIVITY-HARDENING` → 6G-2a · `OPEN-6G-TRANSPORT-GATE-HARDENING` → 6G-2b · `OPEN-6G-REVIEW-FOLLOWUPS` → 6G-2c). 다섯 라운드의 공통 뿌리는 **완료 판정(원장)과 데이터(원문 행)를 시각으로 짐작해 잇는 것**이었고(r3 「원문이 있으면 받은 것」 → r4 「적재 전에 결말」 → r5 「행의 시각으로 마지막 걷기」 → r5-t 「결말 줄 없는 축의 선별 소실」), 처방은 원장의 줄이 걷기를 가리키는 것이다 — 결말 줄이 없는 축에는 그 답이 없어 6G-2d 가 「가장 늦은 걷기」를 되살린다. 남긴 것: 스냅숏 snapshot-v5 · 골든 12 = 9+1+1+1 · 정책 `strategy-backtest-v1.yaml`(A-3 값) · 실수집 runbook 은 실수집 준비 slice 몫. 후속 순서(운영자 2026-09-30): 6G-2d → 6G-2a → 6G-2b → 6G-2c, 실수집 준비와 병행. rollback 의 공유 파일 절차는 이 종결 문단도 착수 문단과 같은 문단 단위 삭제로 지운다.

**6G-2d 착수 2026-09-30 — 추출·실행 상태의 데이터 정확성 셋 + 실수집 강건성 넷** — base `c357e437`(PR #50 6G 머지 뒤 `main`), worktree `bid-vector-v2-m6-6g2d`·
브랜치 `m6-6g2d/2026-09-30`. 정본 `reports/evidence/m6/6g2d/scope.md`(D-6G2d-1~8, 운영자 결정 A-1 즉시 착수 · A-2 스키마 무변경, DEC-03 현행 유지). 수령
OPEN: `OPEN-6G-RUN-STATE-HEAL-ORDER`(복구 뒤 누적 해시 · 원자 복구 쓰기) · `OPEN-6G-LIST-AXIS-WALK-SELECTION`(결말 줄 없는 축은 가장 늦은 걷기) ·
`OPEN-6G-LEGACY-AXIS-LINE`(형식 version fail-closed · walk 필수) + PR #50 `/code-review` 넷(빈 공고번호 행 · 적재와 결말 사이 크래시 · 결정적 실패 재호출 상한 ·
소수 금액). **실 KONEPS 수집은 이 slice 머지 뒤에만**(6G D-6G-77). Kotlin 단일 레인, `ml-engine/**`·golden·스키마 칸 무변경. 리뷰 레인 verifier +
code-reviewer(sonnet), Codex 없음. rollback 의 공유 파일 절차는 이 착수 문단을 문단 단위 삭제로 지운다.

**6G-2d 종결 2026-10-01** — PR **#51**(계약 갱신 r0-b~r3: D-6G2d-9~35). 판정은 PR 코멘트 넷에 있다. 재작업 **2/5** — verifier r1·r2 not-ready, **r3 ready-for-review**, 승인 전 일괄 하나(rollback 문서 경로·문면·test 강화). 닫은 것: `OPEN-6G-RUN-STATE-HEAL-ORDER`(복구 뒤 누적 해시 · 원자 복구 쓰기) · `OPEN-6G-LIST-AXIS-WALK-SELECTION`(결말 줄 없는 축은 가장 늦은 걷기) · `OPEN-6G-LEGACY-AXIS-LINE`(**실행 상태 형식 version 2 부터** — 없거나 다르면 기동 거부, walk 없는 AXIS 줄 읽기 거부) + PR #50 `/code-review` 넷(빈 공고번호 행 · 적재와 결말 사이 크래시 · 결정적 실패 확정과 재호출 상한 · 소수 금액) + 라운드가 드러낸 같은 계열 셋(A 집계 전부/무 — `open_at` 결측·구성 항목 결측·술어 미지, 6G 의 과소 합산 부채 포함 · 관문 거부는 `Refused` 로 상한 밖). 세 라운드의 계열은 **「반쪽 값이 조용히 온전한 값으로 실리는 자리」**였다 — 소수부 · 반쪽 A · 모르는 술어 — 처방은 한 문장(전부 아니면 무, 그리고 계수)이다. 스키마 칸·golden·`ml-engine/**` 무변경(A-2). **6G D-6G-77 의 실수집 차단 조건은 이 머지로 충족**되지만, 실수집 시작은 운영자 결정 A-3(`OPEN-6G2D-AXIS-RETRY-LIMIT` — 재호출 상한 값 3 · 창 = 실행 상태 디렉터리 생애 · 단위 = 마지막 정착 뒤 일시 실패 결말 수, 끊긴 라운드는 `INTERRUPTED` 결말 하나(쪽 수 무관; N=3: 4번째 기동부터 안 부름))과 운영계정 키 뒤다. 남긴 OPEN: `OPEN-6G2D-EMPTY-AXIS-REASON` · `OPEN-6G-REVIEW-FOLLOWUPS` 추가 둘. 다음: 6G-2a → 6G-2b → 6G-2c, 실수집 준비 병행. rollback 의 공유 파일 절차는 이 종결 문단도 착수 문단과 같은 문단 단위 삭제로 지운다.

**운영자 결정 2026-10-01(6G-2d 머지 뒤, 실수집 시작 조건)** — **A-3 재호출 상한 N=3 확정**(단위 = (공고, 축)마다 마지막 정착 뒤 일시 실패 결말 수, 끊긴 라운드는 `INTERRUPTED` 하나, 관문 거부 미계수 · 창 = 실행 상태 디렉터리 생애) → `OPEN-6G2D-AXIS-RETRY-LIMIT` 닫힘(정책 값 등재만, 코드 0). **`OPEN-6G2D-MAX-PAGES-FINAL` 유지**(참가 `50 × rows-per-page` 초과 축은 확정 제외 — 6G-2f 전 5,000, 6G-2f 뒤 기본 49,950 — **문턱 확장은 운영자 결정 2026-10-03 「50 × 쪽 크기 그대로」, D-6G2f-14**; 백테스트 판정 보고에 계수 공시; 많으면 그때 `maxPages` 상향 slice). 남은 실수집 시작 조건: 운영계정 키 · `OPEN-6G-REVIEW-FOLLOWUPS` ★ 셋(6G-2c).

**6G-2a 착수 2026-10-01 — 정책 값 민감도 게이트 보강** — base `30c6659e`(PR #51 6G-2d 머지 뒤 `main`), worktree `bid-vector-v2-m6-6g2a`·브랜치 `m6-6g2a/2026-10-01`.
정본 `reports/evidence/m6/6g2a/scope.md`(D-6G2a-1~9, 운영자 결정 A-1 즉시 착수 · A-2 박힌 수 발견 시 멈춤). 수령 OPEN: `OPEN-6G-SENSITIVITY-HARDENING`(6G vr r5 H-2 — 비교 투영에서
`policy_checksum`·`policy_version` 을 빼고, 정책 값 39 를 ⓐ 판정 입력 · ⓑ 산출 입력 · ⓒ 판독 밖 세 부류로 등식 분할, 판정 입력 일곱마다 경계 위의 판, 쓰이는 자리마다 측정, 변이 열 RED). `ml-engine/src/**` diff 0 이 기대값(D-6G2a-9), Python 단일 레인(`ml-implementer`), Gradle 0. 실수집·백테스트를 막지 않는다. 리뷰 레인 verifier + code-reviewer(sonnet), Codex 없음. rollback 의 공유 파일 절차는 이 착수 문단을 문단 단위 삭제로 지운다.

**6G-2a 구현 완료 2026-10-01(r1·r2 라운드 + 사후 일괄 반영) — 리뷰 대기** — base `30c6659e`, 브랜치 `m6-6g2a/2026-10-01`. `ml-engine/src/**` diff **0**(D-6G2a-9 기대값 — 판정 경로에 **실제로 박힌 수는 없었다**). r1 의 막는 결함은 **손으로 쓴 쓰임 명단**이었다(읽기를 세면 흘러가 쓰이는 자리가 가려진다). 명단을 **AST 로 생성**하고 등식을 걸었다 — 지금 **삼중 86 · (키×자리) 67 · 쓰임 0 인 키 0**, 등식 여섯(생성 명단 ↔ 등재 · 키당 부류 단언 자리 ≤1 · `PROBE:` 이름과 함수 **1:1** · 다중 자리 값마다 처분 · 공시 칸 집합 ↔ 재는 법 · 독립 레인 셈 재현). r2 사후 일괄이 축 **셋**의 구멍을 닫았다: ① 구조 — 제공자 **호출** 결과를 받은 지역 변수의 소비자를 명단이 놓쳤다(`ast.Call` 분기, 삼중 73→86) ② 거동 — **공시 칸**이 읽기를 유지한 상수에 열려 있었다(흔든 정책으로 「실린 값 == 로드된 정책 값」 단언) ③ 덮개 — `PROBE:` 이름 다섯이 함수 둘을 가리켜 블록을 지워도 등식이 초록이었고, 구간 수 probe 의 고정 seam 이 무음일 수 있었다(호출 수 단언). 부류 **ⓐ 27 · ⓑ 11 · ⓒ 1**, 판 **서른하나**, ⓒ 단언은 판 전부에서 돈다. 변이 **마흔여섯 중 거동 43 RED · 구조 36 RED**, 합집합 **46/46** — 덮이지 않은 변이 0. 두 축이 서로를 받는다: 공시 칸 변이 둘은 구조가 조용하고 거동이 받고, 관측값 밖 clamp 둘은 거동이 조용하고 구조가 받는다. 한쪽 우회는 그래서 **세 층으로 닫히고** 남는 것은 관측값 밖 상수의 순수 거동뿐이다(적합도가 도는 판의 p 0.436~0.969). acceptance 는 CI `ml-engine` job **열 단계·명령 열하나** 전부 exit 0 · `pytest tests -q` **1,350 passed**. **동결 위반 사실**: 레인이 동결 중 커밋 셋을 남겨(팀장 「보정」을 허가로 읽음) 이력을 되쓰지 않고 사후 일괄로 받았다. OPEN: 수령 `OPEN-6G-SENSITIVITY-HARDENING` **닫음** · `OPEN-6G2A-FIT-BIN-COUNT-SITE` **종결**(seam 호출 수까지 단언) · `OPEN-6G2A-SEED-COUNT-NOT-PINNED` **존속**(seed 하나까지 줄고 안정성 레그 무력 — 로더 수정은 6G-2e) · `OPEN-6G2A-INFERENCE-POLICY-SENSITIVITY` 경계 밖 등재. 신설 **셋**. 실수집·백테스트를 막지 않는다. 다음: 표적 재검증 + code-reviewer 수정 diff, 그 뒤 사용자 승인. rollback 의 공유 파일 절차는 이 문단도 착수 문단과 같은 문단 단위 삭제로 지운다. (수치는 사후 일괄 뒤 값으로 팀장 정정 2026-10-02 — PR #53 리뷰 ③.)

**6G-2a 종결 2026-10-01** — PR **#53**(계약 갱신 r0-b~r2: D-6G2a-10~23). 판정은 PR 코멘트 셋에 있다. 재작업 **1/5** — verifier r1 not-ready(손으로 쓴 쓰임 명단이 읽기를 세어 자리 넷 열림: 6G r5 E1 여전히 GREEN), **r2 ready-for-review**, 사후 일괄 뒤 종결 확인 「종결 가능」. 닫은 것: `OPEN-6G-SENSITIVITY-HARDENING`(비교 투영에서 checksum·version 메아리 제거 → 39 값 중 19 만 움직이던 사실 재현 · 부류 ⓐ27/ⓑ11/ⓒ1 등식 분할 · 판 31 · **쓰임 명단을 출하 AST 와 로드된 정책 객체에서 생성(86 쓰임) + 등식 넷 + 자리별 probe** · 공시값 == 정책값 · 변이 46 중 거동 RED 43, 합집합 46/46) · `OPEN-6G2A-FIT-BIN-COUNT-SITE`(참조 표본 고정으로 분리, patch 호출 계수로 공허 방지). `ml-engine/src/**` 변경 0 — 출하 코드에 박힌 수는 없었다(D-6G2a-9 ⓐ 미발동). 사실 선언: 레인이 r2 동결 중 커밋 셋을 올렸고 되쓰지 않고 선언된 사후 일괄로 받았다(이후 허가 어휘는 「freeze lifted」 하나). 남긴 것: `OPEN-6G2A-SEED-COUNT-NOT-PINNED` → 6G-2e D-6G2e-6b · 미달 경계(관측값 밖 순수 거동 상수 — 적합도 p 0.436~0.969) · low 둘(공시 칸 안 old→new 우연 일치 · probe 1:1 매핑 자체의 단언). 다음: **6G-2e**(실수집 전 필수) → 6G-2b → 6G-2c. rollback 의 공유 파일 절차는 이 종결 문단도 착수·구현 완료 문단과 같은 표지 단위 삭제로 지운다.

**6G-2e 착수 2026-10-02 — 실수집 전 필수 여섯** — base `c63d0d3a`(PR #53 6G-2a 머지 뒤 `main`), worktree `bid-vector-v2-m6-6g2e`·브랜치 `m6-6g2e/2026-10-02`. 정본
`reports/evidence/m6/6g2e/scope.md`(D-6G2e-1~8, 운영자 결정 A-1 개찰 갈래 `maxSpanDays` **120일** · 순서 2026-10-01 「2a → 2e → 2b → 2c」). 수령 OPEN: `OPEN-6G-OPENING-RANGE-CAP`(PR #52
리뷰 — 31일 상한이 개찰 갈래에도 걸려 A-1 16주 창 기동 거부, 표본틀이 from/to 고정) · `OPEN-6G-REVIEW-FOLLOWUPS` ★ 셋(찢어진 조각만 남은 꼬리 라운드 · `incompleteAValues` 의 기초금액 축
의존 · 복구 쓰기 순서/잠금 가드) · `OPEN-6G-BACKTEST-CLI` · `OPEN-6G2A-SEED-COUNT-NOT-PINNED`. 레인 둘(Kotlin: 범위 정책·실행 상태 셋·runbook / ml-implementer: CLI·seed 수), 파일 집합 비겹침,
호스트 빌드 직렬. **이 slice 머지가 실수집 시작 조건**(남은 것: 운영계정 키). 리뷰 레인 verifier + code-reviewer(sonnet), Codex 없음. rollback 의 공유 파일 절차는 이 착수 문단을 문단 단위 삭제로 지운다.

**6G-2e 종결 2026-10-02** — PR **#54**(계약 갱신 r0-b~r3: D-6G2e-9~24). 판정은 PR 코멘트 셋에 있다. 재작업 **1/5** — verifier r1 not-ready(정정: D-4 가 `incompleteAValues` 를 다른 모양에서 과소 계수), **r2 ready-for-review**, 승인 전 일괄 뒤 종결 확인. 닫은 것: `OPEN-6G-OPENING-RANGE-CAP`(`OPENING_COLLECTION_RANGE_POLICY` **120일**, 공고 목록 31일 그대로, 쪼갠 창 거부 못 박음; 추출 배선은 범위를 재지 않음) · `OPEN-6G-REVIEW-FOLLOWUPS` ★ 셋(찢어진 조각만 남은 꼬리 라운드 → 조각을 의도 줄로 되살려 열린 라운드로 · `incompleteAValues` 정본 = 기초금액 술어, 부재·미지만 A 행 입력, 품질관리비는 항상 입력 — 열일곱 판 · 복구 앞 대조 넷이 읽기만, 거부될 디렉터리 바이트 불변) · `OPEN-6G-BACKTEST-CLI`(`python -m ml_engine.app.backtest_cli`, `file://`·스냅숏 밖 출력, 디코딩·리터럴 이중 guard) · `OPEN-6G2A-SEED-COUNT-NOT-PINNED`(두 정책 로더 seed 다섯, 열거 키 `len`). 형식 version 2 · golden·스키마 불변 · 판정 경로 diff 0. 사실 선언: evidence 혼입 둘(공유 트리에서 두 레인이 한 파일에 append — 다음 두 레인 slice 부터 레인별 파일) · container job 은 compose 가 실행 상태 배선을 적재하지 않아 verifier 가 대신 실행. 남긴 OPEN: `OPEN-6G2E-SNAPSHOT-READER-URI-DECODE`(6G-2c) · `OPEN-6G2E-APP-HTTP-IMPORT-CONTRACT`(6G-2b). **이 머지로 실수집 시작 조건 충족** — 운영계정 키는 개발 키와 같다(사용자 2026-10-02, legacy `.env` 원문형). 다음: 실수집 준비·시작(runbook) 병행, 6G-2b → 6G-2c. rollback 의 공유 파일 절차는 이 종결 문단도 착수 문단과 같은 문단 단위 삭제로 지운다.

**6G-2f 착수·종결 2026-10-03** — 실수집 이틀 실측(포털 한도 **키 × operation × 일 1,000**; 업무 공통 단일 operation 은 개찰완료·산식 A 둘이고 개찰완료가 쪽 100 으로 먼저 닫힘, 하루 ~470 공고 → 표본 24,000 에 ~50일)에서 운영자가 「페이지 크기 999 slice 지금」(선택지 셋 중)을 골라 신설. base `9a5aa26a`, 정본 `reports/evidence/m6/6g2f/scope.md`(D-6G2f-1~17). 산출: `KonepsOpeningEndpointProperties.rowsPerPage`(기본 999, 상한 999, 생성자 `require`) → 배선이 `KonepsSourceConfig` 로 다섯 축에 전달 · 값 slice 두 축 test(wire: 다섯 operation 요청의 `numOfRows` 가 기본값·비기본값 모두 설정값 · 거동: 참가 150 행 공고 1 호출 vs 쪽 100 두 호출, 쪽 크기 2/999 에서 확정 표본 파일 바이트 동일) · `maxPages 50` 무변경(문턱 뜻이 「50 × rows-per-page」로 — **운영자 결정 2026-10-03 그대로, D-6G2f-14**). 판정 SHA `e16bf85e` verifier **ready-for-review**(low 2) · code-reviewer high 1(999 수용 실측이 한 operation → 팀장이 여섯 추가 실측, 개찰완료는 자정 뒤 배포 전제로 D-6G2f-10) / medium 2 / low 4 → PR #57 `/code-review` 80 이상 1(test 종료 코드) + 사실 정정 11(파라미터 오류 = 확정 실패 → 첫 기동 노출 상한 200, D-16 · 걷기 추정 812 → ~240 · 999 응답 1.8~2.8 s/시한 10 s) — 장부층 일괄 뒤 종결, 재작업 **0/5**. **닫은 OPEN**: `OPEN-3B2-PAGE-SIZE-VS-MESSAGE-CAP`(2026-09-08 신설 — 100 행과 999 행의 항목 바이트 동일, 742 KB 정상 수신; D-6G2f-15). 남긴 OPEN: `OPEN-6G2F-NOTICE-LIST-ROWS`(공고 목록 레인 100) · `OPEN-6G2F-MAX-PAGES-PROVENANCE`(문턱이 장부에 없음) · `OPEN-6G2D-FRAME-REWALK`(비용만 감소) → 6G-2c. 배포는 D-6G2f-13·16(수집 없는 창에 `main` bootJar, 재실행 cron 이 개찰완료 999 사전 확인 → `calls-per-day=200` 노출 → 원장에 INPUT_ERROR/NOT_RETRYABLE 0 확인 → 전량). 같은 날 운영자 지시로 legacy `bid-vector` 컨테이너 전부 stop — 서비스 키 출처(`.env` 파일)는 그대로. rollback 의 공유 파일 절차는 이 종결 문단도 착수 문단과 같은 문단 단위 삭제로 지운다.

**6G-2b 착수 2026-10-03** — base `1745a3e2`(PR #57 뒤), worktree `bid-vector-v2-m6-6g2b`·브랜치 `m6-6g2b/2026-10-03`, 정본 `reports/evidence/m6/6g2b/scope.md`(초안 2026-09-30 D-6G2b-1~9 + 착수 실측). 수령 `OPEN-6G-TRANSPORT-GATE-HARDENING`(verifier r5 변이 KA1·KA12~15 가 초록 — 술어가 호출 대상 소유 타입만 보고 금지 집합이 타입 이름 열거). 할 일: 금지를 패키지 뿌리로, 의존 수집을 인자·반환·호출 대상까지, 허용은 (클래스, 타입) 쌍 등식, 두 게이트를 하나로, 음성 fixture 상시. test·정책 파일만 바꾼다(production diff 0 기대 — 쓰이지 않는 import 둘은 이미 없다). 실수집과 병행. `OPEN-6G2E-APP-HTTP-IMPORT-CONTRACT` 는 6G-2c 로 이관(Python). A-2(반사 게이트 뿌리 `adapters` 확장)는 레인 실측 뒤 운영자 결정.

**6G-2b 종결 2026-10-03** — 정본 `scope.md` D-6G2b-1~41(계약 갱신 r1~r7). 산출(test·정책 파일만, production diff 0): 전송 표면 게이트를 **두 층**으로 — 1층 **허용 패키지 목록 + 기본 거부**(모듈별 정확 패키지 127: app 55·workflow 21·adapters 51, 양방향 등식) · 2층 전송 뿌리 열일곱 안의 **(클래스, 전송 타입) 쌍 정확 집합**(63 → NAMECUT 접기 60) + 낱개(`ProcessBuilder`·`Runtime`·`Process`·`ProcessHandle`·`ServiceLoader`) · 참조 수집은 호출 대상 소유·인자·반환 타입까지(KA1 류를 잡음) · 반사 게이트 뿌리 `adapters` 까지 + (클래스, 멤버/타입) 쌍 12(멤버 10 + 타입 2, **운영자 결정 A-2**) · 판정 대상 모듈과 반사 뿌리를 `layer.*` 선언에서 도출(손 목록 → 등식) · 음성 fixture 위반 33 + 과잉 대조 2 상시(덮개는 `archfixture.violating..` 전수 == 변이 표 양방향, 다른 slice 의 fixture 다섯은 `FOREIGN_FIXTURES_REPORTED`) · 6G 의 두 게이트 흡수(KA2~4 유지) · ArchUnit `archRule.failOnEmptyShould` 핀(구조 단언 — 초안의 키 이름이 틀려 죽어 있었음을 PR #58 리뷰가 잡음). 운영자 결정 셋: A-2 adapters 확장 · **vr r1 H-1 처방 = 허용 목록 전환**(거부 뿌리 열거 밖 JDK API — `SocketHandler`·XML `parse(URL)`·JMX·Swing — 가 초록이었다) · 문턱 없음. 판정: verifier r1 **not-ready**(H-1) → r2 @`d1b2b7f0` ready-for-review → r2-b @`79c865d2`(동결 경합 델타, D-33 사실 선언) → r3 @`837605b0`(장부 일괄 중 술어 표면 커밋 `1bcbed39` 표적) → r3 보강 @`20a7052c` 전부 ready-for-review · code-reviewer r1 medium 5 · r2 high 1(동결 중 미커밋 — 해소)·medium 3·low 11 · PR #58 `/code-review` 80 이상 3(죽은 핀 · rollback 실측 HEAD 낡음 · 위협 모델 절 미갱신) + 75 이하 9 전부 처분(D-6G2b-42~44). 재작업 **1/5**. 닫은 OPEN: `OPEN-6G-TRANSPORT-GATE-HARDENING` · `OPEN-6F9-DIVISION-REFLECTION` 의 adapters 절반(D-43). 남긴 OPEN(6G-2c): `OPEN-6G-GATE-REGISTRY-KONEPS` · `OPEN-6G2B-HOLDER-INTERNAL-SURFACE` · `OPEN-6G2B-REFLECTION-ROOT-DOMAIN`(6F9 의 procurement 잔여 포함) · `OPEN-6G2B-ALLOWED-PACKAGE-EGRESS`(허용 패키지 안의 출구 — 파일 시스템 · 라이브러리 로더 · StAX 외부 엔티티 · Spring bean factory) · `OPEN-6G2B-FOLDING-UNIFICATION` · `OPEN-6G2B-COLLECTION-DEPTH`(형제 게이트의 소유 타입만 수집) · `OPEN-6G2E-APP-HTTP-IMPORT-CONTRACT`(이관). 사실 선언: 동결 경합(팀장 지시 집행 중 동결) · evidence 바이트가 산출물을 넘음(scope.md 한 파일, 줄 축은 통과) · `qualityBaseline` up-to-date(측정 미실행). rollback 의 공유 파일 절차는 이 종결 문단도 착수 문단과 같은 문단 단위 삭제로 지운다.

**6G-2c 착수 2026-10-04** — base `ecdc9d9f`(PR #58 뒤), worktree `bid-vector-v2-m6-6g2c`·브랜치 `m6-6g2c/2026-10-04`, 정본 `reports/evidence/m6/6g2c/scope.md`(초안 2026-09-30 D-6G2c-1~15 + 계약 갱신 r0-b D-6G2c-16~30). 수령 `OPEN-6G-REVIEW-FOLLOWUPS` 전량 + 6G-2a·2d·2e·2f·2b 가 넘긴 OPEN. **운영자 결정 2026-10-04**: A-1 공고 키 해시 **그대로**(salt 없음 — 결합 키, 통제는 저장소 밖 강제) · A-2 업무 대표 표지 **셋**(`COVERED`·`UNDERPOWERED`(행은 있으나 필요 표본 미만)·`ABSENT`(행 0), 백테스트 판정 보고 전) · A-3 **셋으로 분할** — ① 이 PR = 실행 상태 형식·스냅숏 스키마에 닿지 않는 항목만(수집이 디렉터리 `m6-6g` 에서 3일째 진행 중이라 형식 변경은 머지 불가) ② **6G-2g 신설**(6G-2b 가 남긴 게이트 OPEN 여섯 — 술어 확장) ③ **6G-2c-형식**(스냅숏 추출 뒤: 명칭 셋 닫힌 어휘 · `EMPTY-AXIS-REASON` · `FRAME-REWALK` · `MAX-PAGES-PROVENANCE` · `torn` 위치). 레인 둘 — K(두 프로세스 잠금 E2E · `IOException` 사유 분리 · 표본 원장 held 가드 · 두 모드 동시 기동 실패 · 누출 자물쇠 harness `try/finally`(게이트 술어 — 표적 재검증) · `unusableRawRows` 세 계수 · `verifyThenHeal` 순서 · Busy 경로 digest) · P(표지 셋 · manifest 키 세 자리 등식 · `_APPROVED_SEED_KEYS` 공용화 · `verdict.json` 덮어쓰기 거부 · 공백·한글 경로 · import-linter 표준 HTTP 금지(게이트 술어 — 표적 재검증) · 파생 지역 변수 한 단계). 등재 유지: `NOTICE-LIST-ROWS`(M7 뒤 값 slice) · `OPEN-6B3-RAW-OBSERVATION-RETENTION` 신설(6B-3).

**6G-2c 종결 2026-10-04** — 정본 `scope.md` D-6G2c-1~41(계약 갱신 r0-b~r10). A-3 ① 의 **형식 무관 항목만**(실행 상태 바이트·판독 술어 무변경 — D-18 을 verifier 가 base↔판정 양방향 다섯 사례 × 두 사본으로 실측, 원장·표본 파일 바이트 동일). 산출 K(adapters `snapshot` · app `collection`/`wiring` · workflow test): 자물쇠를 `RunStateLock.kt` 로 갈라 **두 프로세스 잠금을 자식 JVM 으로 재는 E2E 셋**(파일 잠금을 JVM 가드로 대신하면 정확히 셋 RED, 가드를 더하기만 하면 초록) · `IOException` 사유 분리(`Unlockable`/exit 4 ↔ `Busy`/exit 3, 인터럽트는 접지 않음) · `release` 인터페이스 제거·`Held` 생성자·`tryAcquire` internal(다른 모듈 컴파일 probe 넷 거부, `Suppress` 탈출은 `-Werror`) · 표본 원장 held 가드 + **close 뒤 두 원장 거부**(승인 전 일괄 F-1) · 두 모드 동시 기동 실패 test · 누출 자물쇠 harness `try/finally`(게이트 술어 `92b35a82`) · 스키마 문서를 `:workflow:test` 입력으로 선언(게이트 술어 `d65e8a80` — 문서만 고쳐도 test 가 돈다) · 고정 시계 harness · `unusableRawRows` 원인 세 칸(`UnusableRawRows` 조밀·방어 복사, 러너 로그 한 줄만 소비) · `verifyThenHeal` 읽기 전용 셋 선행 · Busy 경로 digest 생략. 산출 P(`ml-engine` evaluation·backtest CLI·adapters·import 계약): 업무 표지 **셋**(`COVERED`·`UNDERPOWERED`·`ABSENT`, 하한 `verdict.min_window_rows`, 불변식 위반 `ValueError`) · manifest 키 세 자리 등식 · `APPROVED_SEED_KEYS` 공용화(불일치 판정 한 자리) · `verdict.json` `open("xb")` 덮어쓰기 거부 + 출력 자리 사전 생성(`OUTPUT_NOT_A_DIRECTORY`, 닫힌 어휘 넷) · 파일 URI `unquote`(공백·한글 경로) · import-linter 앱 HTTP 금지 + AST 스윕 범위를 계약의 `ignore_imports` 에서 유도(게이트 술어 `49505a74`·`a179e784`) · 파생 지역 변수 한 단계 census. 새 public 표면은 K 넷 + **P 일곱**(`SEED_PREFIX`·`APPROVED_SEED_KEYS`·`seed_key_mismatch`·`DivisionCoverage.ABSENT`·`DivisionCoverageRecord`·`INVALID_PATH`·test 의 `PolicyUse.derived`) — verifier K (2b) 와 verifier P 표적 (2b) 가 전부 수용(D-6G2c-43; 초판이 「P 없음」이라 적은 것은 PR #59 리뷰가 잡은 오기). 판정: P verifier r1 **not-ready**(F-1 high — AST 스윕이 `ignore_imports` source 모듈을 안 봄) → 수정 라운드 1(`f3a47f05`) → r2 ready-for-review · cr r1 P-2~13 · r2 새 high 없음 → 승인 전 일괄 @`fa7483cb`(1,418 passed) → PR #59 조치 @`ba6a33d2`(1,420) / K verifier r1 ready-for-review @`ec07df09`(F-1·F-2 medium) · cr r1 새 high 없음(K-1~K-9) → 승인 전 일괄 @`51b4d960`(변이 일곱 항목 전부 RED, 새 표면 없음) → PR #59 조치 @`97a7993c`(KDoc 둘 · pipe · close). **PR #59 `/code-review`** 80 이상 1(runbook 종료 코드 `4` 누락 → `4c4deae8`) + 리뷰어 다섯의 75 이하 17 전부 처분(D-6G2c-42·43). 재작업 **1/5**. acceptance 팀장 재실측 @`374ddc41`(container 는 `b2a98300` — 둘 사이 차이는 `scope.md` 문면뿐): `one-command-check.sh` exit **0**(Kotlin `check` — K 레인이 같은 트리에서 막 돌린 캐시라 19s, test **2,665**(skip 4) · `qualityBaseline` · `ml-engine` 열 step, pytest 1,418 passed; 조치 라운드 뒤 레인 재실측 1,420) · container job 열둘(자격 값 둘 · S-21 · S-21a · S-21b · S-22a · S-22b · S-22c · S-23 · S-23b · S-24 · S-25 — 워크플로 `run` 블록 그대로) 전부 exit **0**, 정리 뒤 compose 컨테이너·볼륨·포트 잔존 0. 운영자 결정 셋(A-1 해시 그대로 · A-2 표지 셋 · A-3 셋 분할). 닫은 OPEN: `OPEN-6G2E-SNAPSHOT-READER-URI-DECODE` · `OPEN-6G2E-APP-HTTP-IMPORT-CONTRACT` · `OPEN-6G2A-CENSUS-DERIVED-LOCALS`. `OPEN-6G-REVIEW-FOLLOWUPS` 는 **D-6G-75 의 항목만 닫고 OPEN 은 유지**(다른 slice 가 등재한 잔여 열하나 — 6G-2a ⑤~⑧ · 6G D-82 ⑥⑦⑨⑩ · 6G-2d 셋 — 는 코드 0 의 정리 항목으로 6G-2c-형식 입력, D-6G2c-42 ③). 신설 OPEN: `OPEN-6G2C-BUSY-SEED-ORDER`(원장 판독 불가 **그리고** 동시 기동의 이중 결함에서 전 조립 기동이 `ALREADY_RUNNING` 대신 Boot 실패 — 호출 0) · `OPEN-6G2G-REGISTRATION-PACKAGE-COVER`(등재 등식이 adapters 패키지 여섯을 덮지 않음) · `OPEN-6B3-RAW-OBSERVATION-RETENTION`(6B-3). 넘긴 OPEN: **6G-2g**(게이트 OPEN 일곱 — 2b 여섯 + 등재 덮개; 반사로 `release$bid_vector_adapters` 호출 가능은 `HOLDER-INTERNAL-SURFACE` 입력) · **6G-2c-형식**(추출 뒤 — `EMPTY-AXIS-REASON`·`FRAME-REWALK`·`MAX-PAGES-PROVENANCE`·명칭 셋·`torn` 위치) · `NOTICE-LIST-ROWS` 등재 유지. 사실 선언: P 판정 SHA 가 동결 뒤 이동(D-34 — 팀장 지시 집행 중 동결, 두 번째) · 커밋 트레일러 세 형 혼재(계정 전환, D-38 ⑥, 되쓰지 않음) · 정책 파일 쌍 다섯은 base 대비 추가만(D-41). rollback 의 공유 파일 절차는 이 종결 문단도 착수 문단과 같은 문단 단위 삭제로 지운다.

**6G-2g 착수 2026-10-04** — base `31721008`(PR #59 뒤), worktree `bid-vector-v2-m6-6g2g`·브랜치 `m6-6g2g/2026-10-04`, 정본 `reports/evidence/m6/6g2g/scope.md`(초안 + r1 착수 실측 D-6G2g-1~8 + r2 운영자 결정 D-9~13). 수령: 6G-2b 가 남긴 게이트 OPEN 여섯(`COLLECTION-DEPTH` · `FOLDING-UNIFICATION` · `REFLECTION-ROOT-DOMAIN` · `ALLOWED-PACKAGE-EGRESS` · `HOLDER-INTERNAL-SURFACE` · `GATE-REGISTRY-KONEPS`) + 6G-2c 의 `REGISTRATION-PACKAGE-COVER`. **운영자 결정 2026-10-04(추천대로)**: B-1 반사 뿌리 domain 여섯까지(실측 참조 0, 등재 0) · B-2 파일 시스템 출구만 구조 한 수(`java.nio.file` 뿌리 + `java.io` 파일 타입 낱개 여섯) · B-3 의도적 미등재는 build 사실(`filter`·`@EnabledIf`)로 · B-4 수집 깊이 축별 정책 키(raw-access 참조자·domain 순수성 OWNER_ONLY 유지) · B-5 등재 등식은 build-logic task(컴파일된 `@Test` 클래스 전수 == 등재, 양방향, 입력 선언). 착수 실측: 전송 보유자 29(2b 「28」 오기) · 3층 후보 5 · 등재 등식 adapters 131 vs 124(순수 누락 5)·shared-kernel 8 vs 1 · `GATE-REGISTRY-KONEPS` 원 문장 없음(이 slice 가 정의). test·정책·build-logic 만 바꾼다(production diff 0, 실수집 병행). 게이트 술어 slice — 모든 술어 커밋 표적 재검증.

**6G-2g 종결 2026-10-05** — 정본 `scope.md` D-6G2g-1~30(계약 갱신 r1~r14). test·정책·build-logic 만(production `src/main` diff **0**, 실수집 병행). 운영자 결정 B-1~B-5(추천대로) + D-20 낱개 아홉 확인. 산출: ① **등재 등식을 build-logic task `GateRegistrationGateTask` 로** — 모듈 전수(컴파일된 `@Test` 류 클래스, 메타 애노테이션은 모듈 자기 test 출력까지 조회, 상속·인터페이스 default 는 구현 클래스가 센다, 제외·포함 판정은 접기 전 이진 클래스 단위) == `gate-tests.properties` ∖ **build 사실 제외**(Test `filter`·`@EnabledIfSystemProperty`, 제외 자체도 래칫), 양방향, 입력 선언(선언을 `@Internal` 로 강등하면 등재 삭제가 UP-TO-DATE 초록 — 거짓 초록 실측); 등재 보강 adapters 5·shared-kernel 7·코어 1, 두 벌이 된 등식 test 여덟 삭제 ② **수집 깊이를 축별 정책 키 `collection.depth.*` 열**(domain 순수성은 깊이 개념 없음 — 키 삭제; `collection-procurement`·raw-access 참조자는 OWNER_ONLY 유지 — FULL 이면 ① 층의 뜻을 잃음) + 「구현 모드 == 정책 표」·축 모집단 등식 ③ **접기를 이름 절단 하나로**(app 아키텍처 게이트 모듈 안 자리 일곱 → 하나, 재등재 **하나**(`NoticeKeyHash`); 주입 축은 타입 동일성이라 접지 않음 — `Resolution$Resolved` 유지) ④ **반사 뿌리를 `layer.*` 전 층 도출**(domain 여섯, 등재 0) ⑤ **파일 시스템 출구 2층** — 뿌리 `java.nio.file` + 낱개 아홉(`java.io` 파일 타입 여섯 + 파일 여는 String 생성자 셋), 용도 `file-system` 쌍 41·보유자 15, 파일 아닌 목적지는 용도 둘 신설(`http-response`·`process-stdio`), 영구 음성 fixture ⑥ **3층 `collection.transport.member-surface`** — 등재 보유자의 「진입점」(비-private 멤버 + 그룹 안 호출 없는 숨은 멤버 — 람다 본문은 `invokedynamic` 이라 호출 간선 없음)이 도달 추적(전송 멤버 호출 ∪ 전송 타입 서명 멤버 호출)으로 전송에 닿는 쌍 (이진 클래스명, 이름+서술자) **47**(6G-2c vr I-1 의 `release$bid_vector_adapters`·`FileChannelAppend.append(String)` 포함). 판정: verifier r1 **not-ready**(H-1 3층이 몸 모양 의존 · H-2 메타 조회) → r2 **not-ready**(R2-H-1 전송 서명 위임 · R2-H-2 멤버 키 접힘) → r3 **ready-for-review** @`419914a6`(R3-M-1 → 승인 전 일괄 표적 재검증 @`52cbca24`(프로브 여덟 8==8)) · **PR #60 `/code-review`** 80 이상 1(하네스 표 손 목록 귀속 오류) + 리뷰어 다섯의 75 이하 19 전부 처분(D-6G2g-30 — 3층 배열 서명 술어 보정은 표적 재검증) · code-reviewer r1 medium 4 · r2 medium 1 · r3 medium 2, 새 high 없음. 재작업 **2/5**. acceptance `check`·`qualityBaseline` exit 0(test 2,677; sizeGate 가 두 번 붉어 분할 — 전건 `check` 가 잡음), container job 생략(production diff 0). 닫은 OPEN **여덟**: `OPEN-6G2B-COLLECTION-DEPTH` · `OPEN-6G2B-FOLDING-UNIFICATION` · `OPEN-6G2B-REFLECTION-ROOT-DOMAIN` · `OPEN-6G2B-ALLOWED-PACKAGE-EGRESS`(파일 시스템 출구만 — 라이브러리 로더·StAX·bean factory 는 경계 유지) · `OPEN-6G2B-HOLDER-INTERNAL-SURFACE` · `OPEN-6G-GATE-REGISTRY-KONEPS`(원 문장 없음 — 이 slice 가 정의하고 닫음) · `OPEN-6G2G-REGISTRATION-PACKAGE-COVER` · `OPEN-6C-CONDITIONAL-GATE-TEST`(제외가 build 사실이라 동시 만족 불가가 구조로 사라짐). 경계(알려진 제한): 전송 서명 없는 비-private 멤버로 넘기는 두 걸음은 호출자 미등재(송신 자리는 등재) · `java.lang.invoke` 부트스트랩 상수 비가시 · 3층 장부가 컴파일러 생성 이름을 담는 비용(철자 변경은 시끄러운 재등재). 사실 선언: evidence HEAD 이동(D-18) · 접기 지시 문면 오기(D-14) · 착수 실측 과소 추정 셋(D-15). 교훈: 멤버 표면 술어는 호출 간선만으로 닫히지 않는다 — 다음 게이트 slice 의 설계 검토 (2) 에 바이트코드 비가시 간선(`invokedynamic`·bridge·전송 서명 위임)을 기본 우회로. rollback 의 공유 파일 절차는 이 종결 문단도 착수 문단과 같은 문단 단위 삭제로 지운다.

**6B-2 착수 2026-10-05** — base `e922dc7b`(PR #60 뒤), worktree `bid-vector-v2-m6-6b2`·브랜치 `m6-6b2/2026-10-05`, 정본 `reports/evidence/m6/6b2/scope.md`(초안 + r1 D-6B2-1~3). 완료 조건 7 「rollback/restore rehearsal 증거」. 착수 조사: Flyway 15 파일(V1~V17, V10·V11 공백) 전부 추가 전용 → 되돌림 정본은 **forward-fix**, 백업 복원은 데이터 손실 축; `pg_dump` 는 `CREATE ROLE bidvector_app` 을 담지 않아 새 클러스터 복원이 권한에서 실패(1순위 실측 축); `docker/compose.yaml` 에 `name:` 이 없어 프로젝트 이름이 디렉터리 basename `docker` 로 정해져 **같은 배치의 다른 저장소**와 프로젝트·볼륨이 충돌(S-25 `down -v` 위험; 착수 때는 「worktree 끼리」로 적었으나 같은 저장소 worktree 는 이름을 박아도 같은 프로젝트를 든다 — D-6B2-5 ② 정정). **운영자 결정 2026-10-05(추천대로)**: 백업은 리허설 안에서 생성·폐기 · `container` job 상시 step · 범위는 역할·권한·소유자까지 · compose `name: bidvector-v2` 한 줄 · Codex 안 탐. 산출: `tools/db-backup.sh`·`db-restore.sh`·`db-rehearsal.sh` · ci.yml S-23c · runbook `m6-6b2-backup-restore.md` · 등식 셋(Flyway validate+이력 · 표별 행 수·해시 · 축9 권한 행렬). production 코드 0 · 새 마이그레이션 SQL 0.

**6B-2 종결 2026-10-05** — 정본 `reports/evidence/m6/6b2/scope.md` D-6B2-1~19(계약 갱신 r1~r7). production `src/main` diff **0** · 새 마이그레이션 SQL **0** · Kotlin test 수 불변(2,679). 운영자 결정 A-1~A-5(추천대로). 산출: ① `tools/db-backup.sh` — `pg_dumpall --roles-only`(로그인 암호 제외 — 맨 형태는 SCRAM 검증자를 평문에 적어 참조형 스캔 1건) + `pg_dump` custom + manifest(산출물 sha256 + **측정 열 가지**: database·flywayHistory 행 집합 해시·tables 행 수+내용 해시·columns·sequences·roles·privilegeMatrix(표 7축 + 시퀀스 3축 + 함수 EXECUTE, `has_*_privilege` 로 상속 해소)·functions(전 서명 키)·triggers·constraints — 모집단 전부 카탈로그 발견) ② `tools/db-restore.sh` — 자기 라벨(존재+값) 컨테이너에만, `--network none`·host publish 0, 역할 → 새 빈 DB(원본 소유자·인코딩·collation) → `pg_restore --exit-on-error` → **같은 스크립트로 재측정해 등식**; `--compare` 단독 모드는 형식 단언(schema·measurements object) 뒤 명시 분기(jq 실패 = 불일치) ③ `tools/db-rehearsal.sh` — 원본은 compose 파일 **생성 출처 라벨**(`config_files` realpath)로 단언·읽기만, 복원 대상 이미지는 원본 컨테이너에서; (i) forward-fix: 임시 V18 열 추가 → V19 DROP → validate(출하 이미지의 flyway-core, 이 checkout 의 V 파일 — 디렉터리·jar 둘 다 읽혀 바이트가 다르면 Flyway 가 거부) → 이력에 18,19 **순서대로 남음** + 이력 밖 측정 일치 (ii) 데이터 손실: S-23b 가 남긴 전략 행을 원본에서 읽어 TRUNCATE → 음성 대조 붉음 → 재복원 양성(리터럴 13 대신 원본 대조 — S-23b→S-23c 순서가 단언으로 잠김) ④ ci.yml `container` job **S-23c**(S-23b 와 S-24 사이, 매 PR, 자격 값 전달 0) ⑤ runbook `docs/runbook/m6-6b2-backup-restore.md`(두 축 분리 · forward-fix 정본 · 금지 · 멈춤 조건 · 제한) ⑥ `docker/compose.yaml` `name: bidvector-v2`(6C 산출물 한 줄 — 디렉터리 basename `docker` 로 **다른 저장소**와 충돌하던 축을 닫음; 같은 저장소 worktree 는 같은 이름을 들고 갈림은 `COMPOSE_PROJECT_NAME`/`-p` 몫 — D-6B2-5 ② 로 팀장 수용 문면 오기 정정). 판정: verifier r1 **not-ready**(H-1 빈 실행 표식에 라벨 등식 개방 — 남의 역할 속성 변경 재현 · M-1 `--compare` fail-open · M-2 원본 출처 미단언) → 수정 라운드 1 → r2 **ready-for-review** @`d7a93d99`(구판 술어 복귀 변이 셋 전부 재개방 재현) → 승인 전 일괄(R-1 값 자리 가드 과잉 · low 아홉) → 표적 재검증 **ready-for-review** @`91f7303a`; code-reviewer r1 G-1 high(≡M-1) · r2 새 high 없음; **PR #61 `/code-review`** 80 이상 2(A 고정 V18/V19 가 다음 실 V18 과 충돌 → max+1/+2 도출 · B 음성 대조가 비-0 전부 통과 → 정확히 1) + 75 이하 20 처분(누출 스캔 상태·`name:` 선언 단언·`grep -q` 파이프·종료 코드·runbook 전제 등) → 조치 라운드 → 표적 재검증 **ready-for-review** @`efb8c763`(구판 복귀 변이 넷 전부 구멍 재현; 새 low 1 `name: ""` 등재). 재작업 **1/5**. acceptance: `container` job 13 step 로컬 재현 exit 0(S-23c 포함, 리허설 25초, 잔여 자원 0) · `check`·`qualityBaseline` exit 0(`leakPatternGate` 실제 수행) · `one-command-check.sh` 생략(production 무변경, 사유 등재). rollback 실측 HEAD `4aaf5966`(PR #61 조치 뒤 재실측 — 복원 여섯 · milestone hunk 셋 · 되돌린 트리 `check` exit 0, test 2,679 = base) + 팀장 hunk 재실측 @이 문단의 마지막 편집 커밋(rollback.md 「팀장 재실측」). 레인이 음성 대조로 잡은 자기 결함 둘(jq `index(.)` 재결속으로 무시 목록이 전 측정을 지움 · 이력 표 이중 측정)과 판정 레인이 잡은 같은 계열의 세 번째(jq 실패가 빈 findings → 「일치」)는 **「판정이 아무것도 재지 않는 초록」** 한 계열 — 다음 등식형 slice 의 설계 검토 (2) 에 「비교기 자체의 음성 대조(깨진 입력·빈 입력·무시 목록 호출)」를 기본 우회로. 알려진 제한(checklist 11): 논리 백업만(PITR/WAL·보존·암호화·저장 위치는 6B-3·M7) · 운영 복원 뒤 로그인 암호 별도 설정 · manifest `schema` 버전은 `/1` 유지(혼재 시 붉음 — 버전 올림은 백업이 리허설 밖에 보존되기 시작하는 slice 의 첫 항목) · 복원 컨테이너 임시 암호가 `docker inspect` Env 에 수명 동안 남음(미사용 난수) · 치명 신호는 EXIT 트랩을 돌리지 않아 로컬에서 라벨 컨테이너가 남을 수 있음. 완료 조건 7 「rollback/restore rehearsal 증거」 **충족**. 6C D-6C-2(compose) 사실 추가는 닫힌 slice 의 evidence 를 고치지 않고 이 문단과 `compose.yaml` 주석이 정본(D-6B2-19). rollback 의 공유 파일 절차는 이 종결 문단도 착수 문단과 같은 문단 단위 삭제로 지운다.

**6D 분할 · 6D-1 착수 2026-10-05** — base `fd4629fe`(PR #61 뒤), worktree `bid-vector-v2-m6-6d`·브랜치 `m6-6d/2026-10-05`, 정본 `reports/evidence/m6/6d/scope.md`(초안 + r1 D-6D-1~3). 착수 조사: **합성 파이프라인이 없다** — 평가는 HTTP dry-run 뿐(outbox 미기록), ML 은 자리지킴(결정 ④), outbox 는 claim 뒤 복귀(lease/reclaim)·relay 가 없고 브로커도 없으며, model rollback 기제·판정 기록 표도 없다. 그래서 6D 다섯 축 중 「restart 뒤 수렴·redelivery」는 새 production 코드 없이 실측 불가. **운영자 결정 2026-10-05(추천대로)**: **6D-1**(지금 — test-scope 조립 E2E: mock KONEPS → 수집 → canonical → 요건/면허 gate → in-process fake ML → 평가 → outbox → test relay → DispatchNotification → FakeNotificationSender; 장애 주입 중복 공고·ML timeout·DB conflict·malformed contract·schema 거부; 재현 등식 = payload release 다섯 필드 + 판정 결과; production diff 0) → **6F-10**(production: outbox relay/dispatcher · CLAIMED lease·reclaim · 평가→outbox 커밋 경로(`OPEN-6A3-EVALUATION-COMMIT`) · payload 정책/전략 버전; 설계 검토 필수, Codex 는 그때 판단) → **6D-2**(restart 수렴·redelivery·버전 포함 재현). model rollback 의 정의 = 릴리스 선택자 EXACT 로 직전 release 지정(6E runbook 도 같은 정의). 완료 조건 3·4·6 의 자리.

**6D-1 종결 2026-10-05** — 정본 `reports/evidence/m6/6d/scope.md` D-6D-1~21(계약 갱신 r1~r11). production `src/main`·build 파일·migration·CI·docker diff **0**, `internal` 완화 0. 운영자 결정 C-1~C-5(추천대로). 산출: `adapters/src/test/kotlin/bidvector/adapters/e2e/` 열둘(E2E 클래스 다섯 — PipelineOneLine·FailureInjection·ContractRejection·Reproducibility·LadderBoundary — + 조립·fake·지원·ML fake 서버·스크립트·`CollaboratorGraph`·`ClassOrigin`) · `gate.tests.adapters` 등재 다섯(제외 0). 한 줄 E2E: MockKonepsServer → `CollectNoticesUseCase` → canonical → 요건·면허 gate → `OpportunityAnalysis` + gRPC gateway ↔ in-process fake 서버 → `EvaluateCandidatesUseCase` → `OutboxNotificationRequestPort` → test relay(production `JdbcOutboxPort.claim` → inbox 중복 제거·`markProcessed` → `DispatchNotification` → `RecordingNotificationSender`(adapters test fake) → `EventSql.MARK_DELIVERED`) — 단언은 DB 상태(canonical·outbox `DELIVERED`·inbox)와 sender 기록. 장애 주입: 중복 공고(inbox 행 수) · ML timeout(출하 예측 예산 고정점 + gateway 상한 = 예산×2 도출 — 예산 변이는 서버 호출 전에 RED) · DB conflict(「쥔 동안 claim → 롤백 → 재claim」, 래치 반환값 단언, 시한 셋 5/30/60s — production `SKIP LOCKED` 제거·완전 순차 변이 RED) · malformed contract·schema 거부(`contracts/testdata` 골든 로드, 부재 RED) · rollback = EXACT 선택자로 직전 release(LATEST 와 releaseId 상이). 재현: 별도 run 둘의 outbox payload 를 DB 에서 되읽은 **직렬화 문자열 집합 등식** + 음성 대조(release 교체 RED); typed 등식(`OutboxPayloadCodec.decode` + 저장된 `payload_type` 대조)은 장애 주입·계약 거부 축의 단언. 협력자 출처 단언: 살아 있는 use case·dispatcher 의 필드 그래프를 **CodeSource(ClassOrigin)** 로 순회 — 우리 build 출력이 아닌 객체는 포트 경계 여섯 중 하나여야 하고(양방향), 우리 타입을 구현하는 UNKNOWN(JDK 동적 Proxy 포함, handler 하강)도 필터에 닿는다; 건너뜀(깊이 상한·필드 읽기 실패)은 신호로 남아 0 을 단언. 판정: verifier r1 **not-ready**(F-1 출처 단언이 따로 지은 목록 · F-2 DB conflict 가 동시성 미측정 — 시제품 제공 · F-3 임계 리터럴 0 에서 초록 · F-4 timeout 이 test ceiling) → 수정 라운드 1 → r2 **ready-for-review** @`f186ff5b`(R2-M-1 패키지 접두 수집) → 승인 전 일괄 + 시한 보정 → 표적 재검증 ready @`fecbb75f`(R3-M-1 UNKNOWN Proxy 대역 통과 — 레인 자기 신고 「UNKNOWN 은 RED」가 오독) → 보정 → Proxy 재확인 **ready-for-review** @`45eb61c3`; code-reviewer r1 G-1 high(≡F-2) · r2 새 high 없음. 재작업 **1/5**. 변이 열아홉 전부 RED(앞 판 GREEN 「래치 대기만 제거」는 모양 교체로 소멸). acceptance: `check`·`qualityBaseline` exit 0(`:adapters:test --rerun` 13 test 실제 실행 — FROM-CACHE 는 실행 증거가 아니다) · container job 생략(production diff 0). rollback 실측 HEAD `697a231b`(복원 A 12·M 2 `comm` 양방향 0 · hunk 셋(등재 파일 둘·milestone) · 되돌린 트리 base 동일 · `check` exit 0 — evidence 가 `leakPatternGate` 입력이라 트리 동일성 갈음 불가, 전부 실측) + 팀장 hunk 재실측 @종결 문단 커밋. **사실 선언**: `ebf870be` 가 `check` 붉은 채 커밋(`grep FAILED && …` 사슬) → 다음 커밋으로 바로잡음, 이력 되쓰기 없음 — 교훈 「게이트 결과는 종료 코드로, 커밋은 별도 호출로」. 알려진 제한: `OutboxPort.markDelivered` 포트 메서드 미호출(`OPEN-4C2-MARK-UNEXERCISED`, 6F-10 수취) · 재현 등식에 정책 버전·전략 revision 없음(C-4, 6F-10 payload 확장 뒤 6D-2) · rollback 축은 gateway 수준(파이프라인 선택자가 정책에 `LatestPromoted` 고정) · 승격 임계 "0.50" 은 측정 priority 사이에서 손으로 고른 운영자 전략 값(`OPEN-4B1-LADDER-THRESHOLDS` 축; 두 방향 단언으로 0 은 RED) · 시한 기반 「막힘」 보조 단언은 거짓 RED 방향만(구조 단언이 같은 변이를 잡음) · SAM 람다 CodeSource 의존 · 정책 출처 둘로 가름(출하 정본 여섯 / test 가 고른 셋). **신설 OPEN**: `OPEN-6D1-CLAIM-CONCURRENCY-TEST` — 기존 `OutboxClaimConcurrencyTest` 가 `SKIP LOCKED` 제거에서도 초록(같은 「쥔 동안 claim」 모양으로 닫을 수 있음) → **6F-10 수취**(outbox 를 만지는 slice). 교훈: test slice 의 기본 결함 클래스는 「공허한 초록」 — 설계 검토 (2) 의 닫는 술어는 변이 실측으로만 선다(일곱 중 둘이 r1 에서 열렸고 하나는 두 번 더 갈라졌다). 완료 조건 **3·4 의 test-scope 자리** 충족, **6 은 release 축만**(정책 버전·전략 revision 축은 6F-10 payload 확장 뒤 6D-2); production 조립 E2E 는 6F-10·6D-2 뒤. rollback 의 공유 파일 절차는 이 종결 문단도 착수 문단과 같은 문단 단위 삭제로 지운다.

**6F-10 착수 2026-10-06** — base `b137c670`(PR #62 6D-1 머지 뒤 `main`), worktree `bid-vector-v2-m6-6f10`·브랜치 `m6-6f10/2026-10-06`, 정본 `reports/evidence/m6/6f10/scope.md`(초안 `19c49865` + r1 D-6F10-1~11). 6D 분할이 넘긴 production 틈 넷 — ⓐ outbox relay/dispatcher(claim → inbox 판정 → `DispatchNotification` → 종단 전이를 **port 로**, `OPEN-4C2-MARK-UNEXERCISED` 닫음) ⓑ CLAIMED 고아 처분 ⓒ 평가→outbox 커밋 경로(`OPEN-6A3-EVALUATION-COMMIT` 닫음, D-6F7-11 `Failed` 처분) ⓓ payload 에 정책 버전·전략 revision(새 `payload_type` 토큰, 구 디코더 유지). 착수 조사가 확정한 것: 전이표에 `CLAIMED → PENDING` 간선이 없고 ADR 0005 D-3 은 at-most-once(`running` 에서 죽은 행은 재실행 없이 격리)라 **6D 제안 표의 6D-2 행 「reclaim → 정확히 한 번 발송」은 승인 문서와 충돌한다**; V6 에 lease·`claimed_at` 열 없음; `JdbcOutboxPort.mark*` 가 갱신 계수를 버림; 평가 진입점은 HTTP dry-run 하나이고 오늘 평가에 도메인 write 가 없어 outbox 행이 판정의 유일한 영속 흔적; `evaluate()` 는 `suspend`·`TransactionBoundary` 는 ThreadLocal 이라 run 전체 한 트랜잭션은 서지 않음. **운영자 결정 2026-10-06(추천대로, D-6F10-9)**: A-1 고아 `CLAIMED` 는 **ISOLATED**(PENDING 복귀 없음 — 6D-2 의 기대는 「claim 중 크래시 → 재기동 → 고아 격리 → 발송 0 또는 1, 중복 0(놓침 감수)」로 읽는다, 닫힌 6D evidence 는 고치지 않음 D-6F10-10) · A-2 새 열 없이 lease 기반 고아 판정(마이그레이션 0; lease 는 ADR D-10 대로 `workflow` port, 구현은 설계 검토가 고름 D-6F10-11) · A-3 `bidvector.relay.mode=once` 일회 러너(db-scheduler 는 `OPEN-6F10-SCHEDULER`) · A-4 `bidvector.evaluation.mode=once` 일회 러너, HTTP 는 dry-run 유지 · A-5 relay 러너까지 production, **억제 환경에서는 claim 0**, sender 자리는 자리지킴(`OPEN-STR-12` 그대로) · A-6 **Codex 없음**(verifier opus + code-reviewer sonnet). 트랜잭션 모양은 at-most-once 의 귀결로 고정(D-6F10-3): claim 커밋(T1) → 발송 → 종단 전이 + inbox(T2, inbox 는 `Delivered` 뒤에만 — 6D-1 인계 「선기록 좌초」 닫음); `SkipDuplicate` 종단은 설계 검토가 정하되 어휘 확장은 없음. `OPEN-6D1-CLAIM-CONCURRENCY-TEST` 도 이 slice 가 닫는다(D-6F10-6). 설계 검토 필수(세션 모델 직접, 구현 전). 6D-2(restart 수렴·redelivery·재현 등식 버전 포함)는 그 뒤. rollback 의 공유 파일 절차는 이 착수 문단을 문단 단위로 지운다.

**6F-10 종결 2026-10-07** — 정본 `reports/evidence/m6/6f10/scope.md` D-6F10-1~39(계약 갱신 r1~r14). 산출: ⓐ production relay `RelayOutboxNotifications`(`workflow.notification` — 임대 → 환경 억제 검사(Live 아니면 claim 0) → 고아 격리 → T1 claim 커밋 → 행마다 T2(종단 전이 + inbox, inbox 는 `Delivered` 뒤에만); 어휘 해석표 D-6F10-12(DELIVERED = 전달 또는 dedup · FAILED = 전달 없음 확정(Rejected·route Suppressed) · ISOLATED = 모호(Unknown·고아·해독 불가)) ADR 0005 §7 addendum·data-dictionary §2.2.5 정정) ⓑ 고아 `CLAIMED` 는 **ISOLATED**(A-1, 재가시화 없음), 임대는 `ConsumerLeasePort` + PostgreSQL 세션 advisory lock, 재확인 네 지점(고아 목록 전·고아마다·claim 전·행마다 — 획득 직후 확인은 중복이라 PR #63 finding 7 로 제거) → `LeaseLost`(exit 1) ⓒ 평가 커밋 경로 `EvaluationCommitRun`(adapters, 요청 하나 = 트랜잭션 하나, `Reached.disposition` 으로 `Failed` 를 값으로, `bidvector.evaluation.mode=once` 러너 · `OPEN-6A3-EVALUATION-COMMIT` 닫음) ⓓ payload 에 사다리 `PolicyVersion`·`StrategyRevision`(필드 17→20, 제자리 변경 — production 행 0 근거 D-6F10-16) · `OutboxPort.claim(limit, kind)`·`claimedEntries(kind)`(`StrategyUpdated` 불변) · `mark*` 계수 0 → 예외 (`OPEN-4C2-MARK-UNEXERCISED` 닫음) · `OutboxClaimConcurrencyTest` 재작성(`OPEN-6D1-CLAIM-CONCURRENCY-TEST` 닫음) · relay 러너 `bidvector.relay.mode=once`(Live 환경 기동 거부, sender·route·renderer 자리지킴 — `OPEN-STR-12` 그대로) · 새 ArchUnit 규칙(dry-run ↔ 커밋 조립 분리) · 6D-1 test relay 사본 제거. 운영자 결정 A-1~A-6 전부 추천안(2026-10-06). 판정: verifier r1 **not-ready**(R1-H-1 production 커밋 조립을 돌리는 test 0) → r2 **not-ready**(R2-H-1 payload 투영 wire 축 미잠금 · R2-M-1 임대 재확인 위치) → r3 **ready-for-review** @`5b7f26b7` → 승인 전 일괄(cr T-1 고아마다 임대 확인 · R3-M-1 배선 호출 잠금 · 순서 잠금) → 표적 재검증 **ready-for-review** @`dad5b392`(산출물 `2bc08209`) → PR #63 `/code-review` 8건 조치(러너 상호 배제 guard · 고아 격리도 비-0 · 잠금 인식 probe · 부분 집계 · 사본 정리) → 표적 재검증 2·3·4 **ready-for-review**(최종 판정 SHA `ac50f39d`, 산출물 `d43128a9`, 새 finding 0); code-reviewer r1~r3 새 high 0. **재작업 2/5.** acceptance 넷 exit 0(`check --rerun-tasks` · `qualityBaseline` · S-20 · 컨테이너 S-21a~S-25, S-23c·S-24 포함) 을 판정 SHA 마다 재실측. 변이 전부 RED(세 라운드 합 서른 이상; verifier 가 초록을 실측한 셋은 다음 커밋이 닫음). rollback 실측 HEAD `2bc08209`(복원 69 · 되돌린 트리 check 0 · 유효성 빈 출력). **사실 선언**: 레인이 동결 뒤 evidence 커밋 하나(`a37e5d59`)를 올림 — 산출물 불변, 이력 되쓰기 없음(D-6F10-34); 구현 레인 모델은 정의(sonnet)와 달리 Opus 5(스폰 inherit) — 다음 레인부터 명시. 알려진 제한 열아홉(checklist) — 성격을 말하는 것: Live 환경은 `Production` 하나이고 그 설정은 기동이 거부되므로 **이 slice 의 relay 가 실제로 claim 하는 배포는 없다**(claim·발송 경로는 test 가 환경을 주입해서만 돈다) · 임대 재확인 밖의 창(질의 하나)은 0 이 아니다 · 배선 구조 단언은 바이트코드 모양 하나(`OPEN-6F10-RELAY-BOOT-WIRING-LOCK`) · 배치 claim 손실 증폭. **신설 OPEN**: `OPEN-6F10-STRATEGY-EVENT-CONSUMER` · `OPEN-6F10-CLAIM-OBSERVABILITY` · `OPEN-6F10-SCHEDULER` · `OPEN-6F10-CAPACITY-SOURCE`(커밋 러너의 활성 투찰 수는 설정) · `OPEN-6F10-EVALUATION-DOMAIN-WRITE` · `OPEN-6F10-CLAIM-INDEX` · `OPEN-6F10-RELAY-BOOT-WIRING-LOCK` · 하네스 후보 `OPEN-HARNESS-GATE-TESTS-ORDER`(gate-tests 블록 안 사전순 단언 — 세 라운드 연속 사람이 잡음). 교훈: 「production 조립을 실제로 돌리는 test」와 「값의 투영 단계」는 설계 검토 (2) 의 기본 항목으로 — 조립의 port 를 fake 로 바꿔치워도 초록이면 조립은 미측정이고, 사슬의 위·아래가 잠겨도 가운데 투영이 비면 wire 축은 열려 있다. 완료 조건 3·4·6 의 production 자리(outbox 수렴은 6D-2 가 restart·redelivery 로 잇는다). rollback 의 공유 파일 절차는 이 종결 문단도 착수 문단과 같은 문단 단위 삭제로 지운다.

**6D-2 착수 2026-10-07** — base `f4b4ff91`(PR #63 6F-10 머지 뒤 `main`), worktree `bid-vector-v2-m6-6d2`·브랜치 `m6-6d2/2026-10-07`, 정본 `reports/evidence/m6/6d2/scope.md`(초안 + r1 D-6D2-1~3). 6D 분할의 마지막 test 조립 slice — production diff 0. 착수 실측: 6F-10 이 adapter 수준에서 고아 격리(상태 강제 1행)·T1/T2 분리·임대 상실·SkipDuplicate·같은 run 의 키 중복 2행→발송 1 을 이미 쟀고, 6D-1 재현 등식은 6F-10 의 payload 확장으로 정책 버전·전략 revision 을 **문자열로만 암묵적으로** 덮는다(typed 단언·revision 음성 대조 0, 시딩 revision 이 기본값 1). 「같은 entry 두 번 claim」은 전이표·claim SQL(`WHERE state='PENDING'`)로 구조상 불가하므로 redelivery 는 「같은 키의 **다른 entry** 가 재기동을 건너 둘째 relay 에 닿음 → inbox 거부」로 정의한다. **B-1~B-5 추천안으로 착수**(사용자 부재, 정정 시 계약 갱신): 재기동 = 새 조립 인스턴스(새 lease 세션)·살아 있는 홀더는 Busy · 전달과 T2 사이 크래시 뒤 **같은 키 재발생은 다시 발송된다**(at-most-once 의 창) — 실측해 알려진 제한 + ADR 0005 §7 한 줄 · 정책 버전 음성 대조는 seam 없이 불가 → 경계 · Codex 없음 · adapters e2e. 산출물: `PipelineRestartConvergenceE2ETest`(R-1 claim 뒤 크래시 · R-2 발송 뒤 크래시 + 재평가 · R-3 배치 중간 · R-4 수렴 고정점 · R-5 살아 있는 홀더 Busy) · `PipelineRedeliveryE2ETest`(D-1 재기동 건너 SkipDuplicate · D-2 종단 행 재claim 0) · `PipelineReproducibilityE2ETest` 확장(P-1 typed 등식: 두 run 의 `ladderPolicyVersion` == `EVALUATION_LADDER_POLICY_VERSION`·`strategyRevision` == 비기본 시딩값 · P-2 revision 음성 대조 · P-3 C-4 제한 삭제) · relay 를 협력자 출처 그래프에 추가. 설계 검토(세션 모델 직접) 우회 12. 완료 조건 3·4·6 의 마지막 test-scope 자리. rollback 의 공유 파일 절차는 이 착수 문단을 문단 단위로 지운다.

**6D-2 종결 2026-10-07** — 정본 `reports/evidence/m6/6d2/scope.md` D-6D2-1~12(계약 갱신 r1~r6). production `src/main`·migration·CI·docker diff **0**, `internal` 완화 0, 새 production public 표면 0. 운영자 결정 B-1~B-5 는 추천안으로 착수(사용자 부재)했고 정정 지시 없음; 사용자 결정 하나 — swap 2GB 규칙 **1회 예외**(D-6D2-4, 규칙 유지). 산출: `adapters/src/test/kotlin/bidvector/adapters/e2e/` 에 `PipelineRestartConvergenceE2ETest`(R-1 claim 커밋 뒤·발송 전 크래시 → 재기동 → 고아 ISOLATED·발송 0 · R-2 발송 뒤·T2 전 크래시 → 재기동 → 격리·발송 1·inbox 0 → 같은 입력 재평가 → **발송 합 2** · R-3 3행 중 1행 처리 뒤 크래시 → DELIVERED 1·ISOLATED 2·재기동 run 발송 0 · R-4 수렴 고정점 = 재재기동 보고 0 **그리고** 알림 종류 행 전부 종단 · R-5 살아 있는 홀더가 막힌 동안 둘째 relay `Skipped(LeaseBusy)`·격리 0·풀리면 완주 + (2b) seam 단언) · `PipelineRedeliveryE2ETest`(D-1 재기동 건너온 같은 키의 둘째 entry → `skippedDuplicates 1`·발송 0·DELIVERED 2·inbox 1 · D-2 종단 행만 있으면 claimed 0) · `PipelineReproducibilityE2ETest` 확장(P-1 두 run 의 decoded payload 가 판정이 쓴 `EVALUATION_LADDER_POLICY_VERSION` 인스턴스와 비기본 시딩 revision(7)에 각각 같음 · P-2 DB 행 revision 을 올리면 payload 집합 2, 상관관계·시각 집합 1, 되돌리면 run 1 과 문자열 등식 · P-3 C-4 제한 삭제) · `EventTriggeredTransactions`(경계 호출에 **사건** 술어를 거는 데코레이터 — 순번 아님) · `PipelineAssembly` 에 relay 트랜잭션 seam(기본값 production) + relay use case 를 협력자 출처 그래프 뿌리에 · `gate.tests.adapters` 등재 둘(제외 0) · ADR 0005 **§7.6**(at-most-once 가 막는 것은 entry 의 재실행이지 키의 재발생이 아니다 — B-2 실측). redelivery 의 정의: 「같은 entry 두 번 claim」은 전이표·claim SQL 로 구조상 불가하므로 **같은 키의 다른 entry** 가 재기동을 건너 둘째 relay 에 닿는 것이고 inbox 가 거부한다. 판정: verifier r1 **ready-for-review** @`41e67c4e`(non-blocking R1-M-1 R-4 고정점이 격리 부재를 수렴으로 받음 · R1-M-2 「같은 인스턴스 = 같은 임대 세션」 전제 거짓 — `withLease` 가 호출마다 연결을 열어 재호출도 Busy, relay 무상태라 새 인스턴스와 재호출 동치 · R1-M-3 rollback 이 ADR hunk 미포함 · R1-L-1) · code-reviewer r1 high 0(G-1 주입 그래프 건너뜀 신호 미단언 + low 8) → 승인 전 일괄(산출물 `ea07b9a7` · evidence `1bb991af`: R-4 종단 단언·G-1·문면 정정·G-3 미사용 seam 제거·G-6 시한 상수 통합·G-7 어휘 이사) → 표적 재검증 **ready-for-review** @`023326fd`(새 finding 0 — 격리 no-op 이 R-4 까지 RED, G-1 두 변이 RED). **재작업 0/5.** 변이: 레인 아홉 + verifier 다섯 + 탐침 셋(wrapper·전체 교체·JDK Proxy) 전부 RED; **정의 자리 변이는 초록**(정책 버전·시딩 revision 모두 단언과 생산이 같은 상수를 참조) — 그래서 변이는 **사용 자리**에 건다(교훈). acceptance: `check`·`qualityBaseline` exit 0(`:adapters:test` 899 실제 실행, e2e 13 → 23) · S-20·container job 생략(Python·production diff 0). rollback 실측 HEAD `ea07b9a7`(복원 D 3·M 7 · ⓪ 등식 ADR 포함 양방향 빈 출력 · 되돌린 트리 2806 test·게이트 둘 exit 0) + 팀장 hunk 재실측 @이 문단 커밋. **사실 선언**: 레인이 clean-tree 양성 대조 복원에 `git checkout HEAD --` 1회(금지, 손실 0, 두 번째 측정이 정본) · verifier 의 TestKit 이 남긴 daemon 둘을 팀장이 PID 로 멈춤. 알려진 제한(checklist): B-2 창(전달·T2 사이 크래시 뒤 같은 키 재발생은 재발송 — ADR §7.6) · 정책 버전 「바꾸면 깨진다」 음성 대조 없음(상수에 seam 없음, 사용 자리 변이로 「읽고 있음」만) · 재기동은 in-JVM 등가(OS 사망·전원 차단·TCP 반개방 밖) · R-5 쥠 시한 30s 거짓 RED 방향 · 두 relay 겹침 창은 6F-10 소관 · 발송 채널 fake(`OPEN-STR-12`) · claim 관측 열 없음(`OPEN-6F10-CLAIM-OBSERVABILITY`). 신설 OPEN 0, 닫은 것 6D-1 C-4. 완료 조건 **3·4·6 의 test-scope 자리 전부 충족**(6 의 정책 버전·전략 revision 축 포함); production 조립 E2E(실 sender)는 `OPEN-STR-12` 뒤. **PR #64 조치(D-6D2-13~16)**: `/code-review` finding 8(정확성 0) — 고침 7(F1 크래시 술어를 hook 생성 시점 CLAIMED entry id **집합** 대비 증가로 좁히고 자리 잠금 test 추가 — 앞 판 술어 복귀 변이는 그 test 만 RED · F2 R-5 홀더 스레드 합류(방어적, 필요성 미증명) · F3 홀더 보고 clue · F4 seam 에 sender 전달로 `lateinit` 자기참조 넷 제거 · F5 주입 모양 통일 · F7 KDoc · F8 이중 질의) · 등재 1(F6 시한 상수 셋째 사본은 `event/OutboxClaimConcurrencyTest`, in_scope 밖) → 산출물 `3a3208d1` · evidence `a9eb1c76` → 표적 재검증 2 **ready-for-review** @`2e97d3c1`(새 finding 0) · CI 세 job pass · 변이 열 행 RED · e2e 24. **최종 판정 SHA `2e97d3c1`**, 산출물 마지막 `3a3208d1`(= rollback 실측 HEAD, ⓪~⑥ 재실측), 팀장 hunk 재실측 @이 문단의 마지막 편집 커밋. verifier TestKit daemon 셋을 팀장이 PID 로 멈춤. 사용자 결정 「PR 리뷰 이상 없으면 머지」(2026-10-07). rollback 의 공유 파일 절차는 이 종결 문단도 착수 문단과 같은 문단 단위 삭제로 지운다.

**6E 분할 · 6E-1 착수 2026-10-07** — base `80dc33b3`(PR #64 6D-2 머지 뒤 `main`), worktree `bid-vector-v2-m6-6e1`·브랜치 `m6-6e1/2026-10-07`, 정본 `reports/evidence/m6/6e1/scope.md`(초안 + r1 D-6E1-1~3). 착수 조사(`_workspace/m6-6e1/00_scout.md`): 축 ① acceptance scenario 는 capability-map 에 `V2 필수` 61 전건이 있으나 **capability → test 대응이 0**(완료 조건 3 의 판정 대상 미정의) · ② production 계측 0·구조화 로그 0·임계 미결(`OPEN-OPS-03/04`) · ③ 백업·복원은 충족, **incident 절차 0 · model rollback 절차 0**(선택자가 정책에 `LatestPromoted` 고정이라 운영자 경로 없음) · ④ dry-run 은 구조로 닫혀 있으나 **CI 가 호출하지 않음** · ⑤ 차이 목록이 세 문서에 흩어짐. 6E 로 라우팅된 OPEN 8(`OPEN-6A1-CONNECTION-POOL` 이 「여기서 닫혀야 운영 반입 가능」으로 유일 명시). **분할(E-1)**: **6E-1** = production diff 0 문서 slice(C-1 capability→acceptance 추적표 · C-2 운영 runbook · C-3 incident · C-4 model rollback · C-5 dry-run/live probe 절차 · C-6 legacy↔V2 차이 목록(팀장) · C-7 ledger↔예방 제약 전수표 · C-9 `OPEN-5E-POLICY-VALUES` 잠정 유지 선언) + **G-4** CI S-23b dry-run 왕복 한 step; **6E-2** = 운영자 결정 선행 코드·게이트(G-2 커넥션 풀 · G-1 SBOM/CVE · G-3 metric 계측 · G-7 투찰 기록 표). E-1~E-5 추천안으로 착수(사용자 「남은 것 계속 진행」). 설계 검토 핵심: 문서 slice 의 결함 클래스는 「문서가 코드와 어긋나도 초록」 — 표마다 코드에서 기계 수집한 집합과의 **등식**을 acceptance 에 둔다. rollback 의 공유 파일 절차는 이 착수 문단을 문단 단위로 지운다.

**6E-1 종결 2026-10-08** — 정본 `reports/evidence/m6/6e1/scope.md` D-6E1-1~25(계약 갱신 r1~r14). production `src/main`·migration·docker·build·`config/quality` diff **0**. 운영자 결정 E-1~E-5 는 추천안으로 착수(사용자 「남은 것 계속 진행」), 사용자 결정 둘 — swap 1회 예외(D-6E1-4 1.5GB → D-6E1-12 1.0GB, 규칙 2GB 유지). 산출: ① **C-1 capability acceptance 추적표** `reports/evidence/m6/6e1/acceptance-trace.md` — `V2 필수` 61 전건, ⓐ 술어 셋(등재 식별자 · **production 배선·출하 정책 값에서 성립** · 그 식별자가 그 배선·그 값으로 잼) + 행마다 production 칸; **최종 ⓐ13/ⓑ2/ⓒ46 → 완료 조건 3 은 오늘 성립하지 않으며 성립 범위는 15/61**(세 라운드에 걸쳐 ⓐ 29→20→15→13 으로 내려옴 — 「test 가 있다」≠「production 배선·출하 값에서 성립」이 반복 결함 클래스). ⓒ 두 갈래: 구현에 없다 42(그중 홀더 OPEN·후속 없음 **17** → OPEN 에스컬레이션: STR-06·07·10·16 · QUAL-08 · ML-05·07·08 · DEC-02·05·08·10 · SET-06 · OPS-08·09·10·13) / 구현 성립·test 만 없음 4 ② **운영 runbook** `docs/runbook/m6-6e-operations.md`(§1 기동·설정 키 전수 · §2 관리 표면 잠금·거부·포트 노출·audit fail-closed·풀 부재 경고 · §3 incident — 러너 종료 코드 처방(relay 5·수집 4·평가 커밋)·cause code·`ISOLATED` 영구 손실·임대 Busy · §4 **model rollback — 운영자 경로 없음을 사실로**: release 는 추론 정책 version 파생 런타임 상수, 승격·demote 0, 비현재 `EXACT` 는 실 serving 거부, 6D-1 C-3 정의는 fake 서버 위에서만, 유일 지렛대는 이전 정책 값 재배포이나 그 절차가 저장소에 없음 · §5 dry-run/live probe 사상표·호출법, live probe 는 절차만) — 문서 표마다 코드 기계 수집 집합과 **등식 32**(E-25: §3.6 접속 역할 사실 셋 + 문면) ③ **C-6 차이 목록** `docs/discovery/legacy-v2-differences.md`(폐기 6·후속 17·근거 부족 11·pull 변경 6, §10 집계 등식) ④ **C-7 ledger ↔ 예방 제약 전수표** `ledger-constraint-trace.md`(61 항목: 연결 43 · **미연결 18** — R-FLOOR 넷·R-ASYNC 넷·R-ML 넷·R-PROV 셋 등) ⑤ C-9 `OPEN-5E-POLICY-VALUES` 일곱 값 — 6E 실측 불가, 잠정 유지·재승인 조건을 M7 운영 반입 실측으로 이전 ⑥ **G-4** CI `container` S-23b 에 평가 dry-run 왕복(여력 상한 왕복 → 200 → 본문 키 아홉 등식 → outbox 행 수 전후 등식) — **주장은 「200 + 본문 형태 + 빈 후보에서의 비쓰기」**(후보 0 이라 커밋 포트로 바꿔치워도 초록 — 공허, verifier r1); 「외부 effect 없음」의 정본은 `DryRunCommitSeparationGateTest`(구조). 판정: verifier r1 **not-ready** @`6b72c2f3`(H-1 runbook 이 없는 레지스트리 demote 경로 약속 · H-2 G-4 등식 공허 · H-3 ⓐ 표본 3/7 위반) → 수정 라운드 1 → 표적 1 **not-ready** @`39d64a5c`(H-1 재발: ⓐ 표본 3/6 production 미성립) → 수정 라운드 2(술어 명문화·production 칸·전수) → 표적 2 **ready-for-review** @`c04aae88` → 승인 전 일괄(ML-01·02 → ⓒ, E-23 자리채움 거부) → 표적 3 **ready-for-review** @`4d8ae582`(새 high·medium 0); code-reviewer r1 high 0. **재작업 2/5.** acceptance: `check` 깨끗한 clone `--no-build-cache` 전건 실행 exit 0(2817 test) · `qualityBaseline` exit 0 · container job S-21a~S-25 로컬 재현 exit 0(G-4 실행 줄, M4 동적 변이 RED) · 등식 31 · 변이 M1~M9 RED. rollback 실측 HEAD `ba6c25f1`(⓪~⑥; 유효성은 되돌림 종류별로 — `M` hunk 둘 불변·`A` 제거 여섯은 내용 무관, 하네스 후보 `OPEN-HARNESS-ROLLBACK-VALIDITY-BY-KIND`) + 팀장 hunk 재실측 @이 문단 커밋. **사실 선언**: 레인이 백그라운드 빌드 통지를 기다리다 멈춰 재개 지시로 이어감 · verifier 가 worktree 인덱스를 잠시 덮었다 복원(잔여 0) · scope.md 에 6B-2 옵션 이름 인용 1(누출 아님, 게이트 밖). **신설 OPEN 셋**: `OPEN-6E1-MODEL-ROLLBACK-MECHANISM`(승격 상태·demote·재배포 절차 — ML 레인·운영자 결정 선행) · `OPEN-6E1-G4-NONVACUOUS`(후보 ≥1 seed — ML 배선 뒤) · **`OPEN-6E1-APP-ROLE-NOT-ASSUMED`**(outbox GRANT 는 `bidvector_app` 역할에 있으나 그 역할은 NOLOGIN 이고 `SET ROLE` 0, 출하 배포는 superuser 접속 → 앱 세션이 outbox 행을 지울 수 있다; 운영 반입 시 접속 역할 전환 — **PR #65 `/code-review` F2**). **PR #65 조치(D-6E1-23~25)**: finding 7(정확성 0) — runbook §3.6 사실화 · ci.yml `_edit_field` helper·guard·주석 · rollback 목록 실행 시 산출(하드코딩이면 hunk 둘 누락) · evidence 수치 → 표적 재검증 4 **ready-for-review** @`2d70d979`(F2 를 컨테이너 권한 질의로 확인: 앱 사용자 = superuser, DELETE 가능; container job 재현 exit 0; RT4-M-1 E-25 미커밋 → 정정 커밋). 완료 조건: **3 — 판정 대상이 처음 정의됨, 15/61 로 미성립** · **4 — 절차(runbook §5) + CI 측정(G-4, 빈 후보 한정)** · 8·9 는 6E-2 뒤. **6E-2 운영자 결정 대기 넷**: G-2 커넥션 풀 · G-1 SBOM/CVE 도구 · G-3 metric 계측(`OPEN-OPS-03` 임계) · G-7 투찰 기록 표. rollback 의 공유 파일 절차는 이 종결 문단도 착수 문단과 같은 문단 단위 삭제로 지운다.

**6E-2 분할 · 6E-2a 착수·종결 2026-10-09** — 사용자 결정 2026-10-08 「추천대로 6E-2 진행해」로 6E-1 이 넘긴 결정 가운데 G-2 커넥션 풀 · G-1 SBOM/CVE · 접속 역할 전환(`OPEN-6E1-APP-ROLE-NOT-ASSUMED`)을 착수하고 **둘로 갈랐다** — 풀과 접속 역할은 같은 DataSource 배선이라 **6E-2a**, SBOM/CVE 는 CI·도구 축이라 **6E-2b**(별 브랜치·별 PR). G-3 metric · G-7 투찰 기록은 결정 뒤. **6E-2a** 정본 `reports/evidence/m6/6e2a/scope.md` D-6E2A-1~7, base `2deb5f9d`. 산출: production `DataSource` 빈 = HikariCP(코드 상수, 최대 크기 하한 2 를 test 로 잠금 — 1 이면 relay 임대가 자기 자신과 교착) + 풀 연결 초기화 `SET ROLE bidvector_app` · migration 은 빈도 필드도 아닌 **지역 소유자 비풀링 연결**에서 먼저 돌고 그 뒤에 풀을 만든다(순서는 함수의 순서, 역전 변이 RED) · 컨텍스트 `DataSource` 빈 정확히 1 · 실 조립에서 모든 대여 연결(순차 재사용·동시 점유·재생성)이 `bidvector_app`, `DELETE FROM outbox` 실제 거부 · 마이그레이션·compose·CI 무변경(IDENTITY 열은 시퀀스 USAGE 없이 역할로 INSERT 성립, 실측) · 이미지 위생 1초 창 유지(첫 DB 접촉이 여전히 migration 연결) · runbook §2.6 풀 상수표 · §3.6 DELETE 보호 작동. 판정: verifier r1 **ready-for-review** @`32138c6f`(바꿔치우기 변이 다섯 RED · adapters 전 suite 를 역할 풀로 재구성 실행 902 중 실패 10, production 경로 0) · code-reviewer r1 high 0 medium 3 → 승인 전 일괄 → **Codex r2 approve(0 finding)** @`2a393b62`(codex-cli 0.160.1 · gpt-5.5 high; r1 은 사용자 config 의 MCP 서버가 호출돼 worktree 오염 → 무효·미저장, 하네스가 호출마다 MCP·plugin·네트워크를 끄도록 갱신). **재작업 0/5.** 닫는 OPEN 둘: `OPEN-6A1-CONNECTION-POOL` · `OPEN-6E1-APP-ROLE-NOT-ASSUMED`. **신설 OPEN 넷**: `OPEN-6E2A-OWNER-CREDENTIAL-IN-APP`(소유자 자격 값이 앱 환경에 남고 세션이 `RESET ROLE` 가능 — 이 slice 는 앱 결함을 막지 자격 유출을 막지 않는다, M7 배포 환경 결정) · `OPEN-6E2A-TX-BOUNDARY-LEAK-UNDER-POOL`(트랜잭션 경계 누출이 풀 용량 소진으로 승격, 탐지 수단 없음) · `OPEN-6E2A-ADAPTER-TESTS-RUN-AS-OWNER`(production SQL 의 저장소 test 가 소유자로 돈다) · `OPEN-6E2A-INIT-FAIL-PREDICATE`(「역할 멤버 아닌 사용자 → 기동 실패」를 잠그는 칸 — 초기화 실패 상수가 라이브러리 기본값과 같아 삭제 변이가 초록). 하네스: codex-review-gate 바이너리·버전 핀 갱신(WSL · 0.160.1, 운영자 결정) · 호출마다 MCP·plugin·샌드박스 네트워크 비활성. rollback 의 공유 파일 절차는 이 문단을 문단 단위로 지운다.

**6E-2b 착수·종결 2026-10-09** — 정본 `reports/evidence/m6/6e2b/scope.md` D-6E2B-1~15, base `2deb5f9d`(6E-2a 와 같은 날 갈라짐; 6E-2a 머지 뒤 main `b6d31b87` 병합). 완료 조건 8 「security/secret scan 통과」의 **취약점 스캔·SBOM 절반**(받은 OPEN `OPEN-6C-IMAGE-VULN-SCAN`, D-6C-5 「흉내만 낸 스캔은 상시 초록」). 산출: Trivy 0.75.0 릴리스 바이너리(버전·자산·SHA-256 을 `config/quality/vuln-policy.properties` 에 — 워크플로 숫자 0, 2026-03 배포 경로 사고 권고 밖 릴리스) · `tools/vuln-scan-check.sh`(이미지 → CycloneDX SBOM → **그 SBOM 을 스캔**, 판정은 JSON 의 jq, HIGH·CRITICAL 중 수정본 있는 것만 차단) · allowlist `vuln-allowlist.properties`(키 = 취약점 ID + 패키지 + 이미지 kind, 만료일·사유; 만료·stale 은 비-0) · CI `container` S-22d/e/f(두 이미지 스캔 + SBOM 90일 보관) · uv 빌드 stage 참조 다이제스트 고정 · runbook §8. **사용자 결정 D-6E2B-1**: 오늘의 수정 가능 HIGH/CRITICAL 67(베이스 이미지 54 · JVM 의존 13 — jackson 10 · tomcat-embed-core 3)은 등재, 상향은 **6E-2c**, CRITICAL **10**건(tomcat-embed-core 3 · 베이스 7)의 등재 만료 **2026-10-31**(HIGH 57 은 2026-12-31)(늦어지면 게이트가 스스로 붉어진다). 판정: verifier r1 **not-ready**(H-1 cwd 의 `.trivyignore`·`trivy.yaml`·`TRIVY_*` 가 둘째 면제 축 — CRITICAL 셋을 옮겨 exit 0) → 수정 1(세 겹 잠금 · 스캔 축 양성 대조: 분석 패키지 하한 · DB 메타데이터 술어 · severity 열거) → 표적 1 ready → 일괄(`--cache-dir` 샌드박스) → 표적 2 ready → 종결 일괄(`--module-dir` 샌드박스 · main 병합) → 표적 3 **ready-for-review** @`c3aff379` → 장부 일괄(rollback 복원 원천을 병합된 main SHA 로 고정). code-reviewer r1 high 1 medium 5. **재작업 1/5.** Codex 없음(CI·도구 slice). 이 slice 의 반복 교훈: 면제 축은 열거를 늘려서가 아니라 **위치를 환경에서 빼앗아**(`--ignorefile`·`--cache-dir`·`--module-dir`) 수렴했고, 매번 「명령은 exit 0 인데 뜻을 잃음」을 잡은 것은 별도 계수(대조군 · 표지)였다. **신설 OPEN**: `OPEN-6E2B-BASE-IMAGE-BUMP` · `OPEN-6E2B-DEPENDENCY-BUMP`(둘 다 6E-2c) · **`OPEN-6E2B-OS-MATCH-PREDICATE`**(분석 패키지 하한은 DB 와 맞춘 패키지를 재지 않는다 — **6E-2c 전에** 닫는다: 베이스 상향이 곧 배포판 변경·allowlist 비움의 위험 창) · `OPEN-6E2B-GRADLE-DEPENDENCY-VERIFICATION` · `OPEN-6E2B-TOOL-SIGNATURE-VERIFICATION`(cosign 부재, 체크섬 핀이 결속). 알려진 비용: 실행마다 DB 를 받아 상류 장애가 무관한 PR 을 판정 불가(exit 2)로 붉힌다. rollback 의 공유 파일은 병합된 main 커밋에서 복원(머지 뒤는 PR revert).

**6E-2c 착수·종결 2026-10-10** — 정본 `reports/evidence/m6/6e2c/scope.md` D-6E2C-1~7, base `e2232a75`(PR #68·#69 머지 뒤 `main`). 6E-2b 가 등재한 수정 가능 HIGH/CRITICAL **67** 을 실제로 걷었다 — CRITICAL 10 의 등재 만료 2026-10-31 을 미루지 않고 소진. 산출: **OS 매칭 술어**(kind 별 정책 키 `scan.os.family`·`scan.os.name` 을 스캔 `.Metadata.OS` 와 정확 일치 + 그 family 의 `os-pkgs` Result 가 패키지를 실은 채 하나 이상 — 상향 **전** 커밋에서 닫았고 바로 다음 커밋이 그 술어에 걸리는 것을 실측) · 베이스 두 개 상향(`python:3.12.15-slim-bookworm` debian 12.15 · `eclipse-temurin:21-jre-noble` 새 index, 3.12 마이너·cp312 유지) · JVM 보안 하한(Boot 4.1.1 이 4.1.x 최신이라 BOM 위에 tomcat 11.0.26 constraints · jackson 2.21.7/3.1.7 platform, 배포물 `BOOT-INF/lib` 를 test 가 잰다) · allowlist **67 → 0**(두 이미지 차단 후보 0; ml-serving 의 CRITICAL 2 · HIGH 55 는 수정판 미공시) · runbook §8 · vuln 정책 판 1→2. 판정: verifier r1 **ready-for-review** @`a4d978ec`(`check --rerun-tasks` 2,840 tests) · code-reviewer r1 새 high 없음 → 승인 전 일괄(D-6E2C-5: 술어 ⓑ 패키지 0 차단 · test 술어 · 문면을 술어만큼 좁힘) — 일괄의 변이 실측이 **기존 결함**을 드러냈다: 배포물을 여는 test 둘이 `dependsOn` 으로 순서만 걸려 jar **내용** 변경에 다시 돌지 않았다(6F-8 부터; D-6E2C-6 으로 입력 선언) → 표적 재검증 **ready-for-review** @`c4656329`. **재작업 0/5.** Codex 없음(CI·의존 버전). 이 slice 의 교훈: 술어는 배포판 **선언과 실제의 일치**를 강제할 뿐 DB 가 그 배포판을 **덮는지**는 재지 않는다 — 바뀐 배포판을 정책에 그대로 옮겨 적으면 exit 0 으로 findings 가 284→6 으로 접힌다(verifier 실측). 게이트로 닫으면 스캐너 경고 **문자열** 술어가 되어 두지 않았고 runbook 의 사람 신호와 상향 전 사전 확인이 진다. **OPEN 처분**: `OPEN-6E2B-BASE-IMAGE-BUMP` · `OPEN-6E2B-DEPENDENCY-BUMP` 해소 · `OPEN-6E2B-OS-MATCH-PREDICATE` **부분 해소** · 신설 `OPEN-6E2C-OS-DB-COVERAGE` · `OPEN-6E2C-BOOT-FLOOR-SUNSET`(Boot BOM 이 이 판들을 관리하게 되면 하한 셋을 걷는다). 등재 low: 숫자로 시작하는 꼬리를 가진 jar 이름(`…-2.21.5-1.jar`·timestamp SNAPSHOT)이 파싱 오류로 조용히 빠짐 · jackson 2·3 밖 major 미검사(오늘 그 좌표 없음). 정책 판 규칙이 저장소 안에서 엇갈린다(값만 바꾸고 올린 선례 · 키를 더하고 안 올린 선례) — 하네스 후보. rollback 의 공유 파일(runbook · 이 문서)은 base `e2232a75` 에서 복원(머지 뒤는 PR revert).

## M6 잔여 해소와 배선 — 실측 지도와 순서 (2026-09-23, 팀장)

운영자 지시 **「M6 잔여를 해소하고 미배선된 부분을 배선 작업 진행해」**. 착수 전에 `main`(`48043440`)에서
**포트별 production 구현 존재 여부를 전수**했다.

### `EvaluateCandidatesUseCase` 의 포트 아홉 — 셋이 비어 있다

| 포트 | production 구현 | 낸 slice |
| --- | --- | --- |
| `StrategyRepository` | `JdbcStrategyRepository` | 6F-1 ✅ |
| `CandidateSourcePort` | `JdbcCandidateSource` | 6F-2 ✅ |
| `LicenseGatePort` | `StoredRequirementLicenseGate` | 6F-5-a ✅ |
| `CorrelationIdFactory` · `Clock` | `UuidCorrelationIdFactory` · `SystemClock` | 6F-2·6F-1 ✅ |
| `OperatorProfilePort` | `JdbcOperatorProfileRepository` | 6F-6 ✅ |
| **`WatchSubjectPort`** | **없음** | — |
| **`CapacityPort`** | **없음** | — |
| **`NotificationRequestPort`** | **없음** | — |
| `MlAnalysisPort` | `UnavailableMlAnalysis`(**자리지킴**) | 처분은 4B-6b 가 6A 로 넘겼다 |

**6F-4 는 감시 텍스트의 「데이터」(V14 `notice_title` + canonical 조립)를 냈지 `WatchSubjectPort` 어댑터를
내지 않았다** — `OPEN-6F4-TITLE-WIRING` 이 그 자리다.

### 순환은 실재하지 않는다 — 갈라 놓아서 그렇게 보였다

계약 문면상 **6F-3(여력)은 「6A 평가 endpoint 선행」**이고 **6A-3(후보평가 축)은 「어댑터 여섯이 생긴 뒤」**라
서로를 가리킨다. **실측으로 풀린다**: `CapacityPort.snapshot()` 은 **인자를 받지 않는다.** 그러므로 결정 ③
(「현재 활성 수는 평가 요청이 싣는다」)이 성립하려면 그 어댑터는 **요청마다 그 값으로 생성**돼야 하고,
그 생성 자리가 곧 **평가 endpoint** 다. **둘은 서로를 기다리는 것이 아니라 같은 일**이다.

**결정 — 6F-3 과 6A-3 을 한 slice 로 합친다.** 갈라 두면 어느 쪽도 먼저 설 수 없다.

### 착수 순서

1. **6F-7** — `NotificationRequestPort` → outbox. **차단 없음**(outbox 기반은 V6·`JdbcOutboxPort` 로 존재).
   발송 채널은 `OPEN-STR-12` 로 그 뒤다 — 이 slice 는 **요청을 낳는 자리까지**다.
2. **6F-4-w** — `WatchSubjectPort`. **차단 없음**(6F-4 의 `notice_title` 이 `main` 에 있다).
   `OPEN-6F4-TITLE-WIRING` 의 포트 절반을 닫는다(수집 절반은 `OPEN-6F4-TITLE-INGEST`). **PR #43 — 종결 문단 참조.**
   4번(조립)의 전제에 **`OPEN-6F4W-ASSEMBLE-CALLER`** 가 더해진다.
3. **6A-3 + 6F-3**(합침) — 평가 endpoint + `CapacityPort`. 여기서 **`MlAnalysisPort` 처분**(실 gRPC gateway
   배선 vs 자리지킴 유지)을 운영자에게 올린다 — 4B-6b 가 6A 로 넘긴 결정이고 **실 외부 호출 축**이다.
4. **조립**(`OPEN-6F-ASSEMBLY`) — 포트 아홉을 app 에 꽂는다. 1~3 이 전제다. **3·4번은 한 slice 로 닫혔다 — PR #44, 6A-3+6F-3 종결
   문단 참조**(ML 자리지킴 · dry-run 전용).

### 배선 밖 잔여

**지금 열림**: **6A-2**(세션 편집 endpoint + 앱 이미지 — 6A-1 이 남긴 `OPEN-6A1-SCAN-FILTER-SIDE-EFFECT`·
`OPEN-6A1-CREDENTIAL-RAW-REINTRODUCTION` 을 받는다) · **6B-2**(백업·복원·마이그레이션 되돌림 리허설,
완료 조건 7).

**운영자 결정 선행**: **6B-3**(데이터 수명·마스킹 — **승인된 보존 기간이 없다**. 6F-6·6F-5-a·6A-1 이 각각
`OPEN-…-RETENTION` 을 여기로 넘겼다) · **6B-4**(ML job 영속·큐 상한 — **큐 정책 결정**) · **6F-5-b**(요건을
채우는 LLM 추출 — **실 LLM 호출은 운영자 승인 대상**).

**배선 뒤**: **6D**(E2E·장애 주입) · **6E**(제품 acceptance·운영 runbook — `OPEN-6A1-CONNECTION-POOL` 이
여기서 닫혀야 운영 반입이 가능하다).


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

## 다음 마일스톤 (2026-10-03, PR #55)

- 다음은 `milestone-7.md`(사용자 웹 화면·도메인·운영 반입). **M7 착수는 「완료 조건」 절의 아홉이 성립한 뒤**다.
- **완료 조건의 범위 조정(운영자 결정 2026-10-03)**: 6G 의 실 KONEPS 수집(2026-10-02 착수 승인, 포털 한도 키 × operation ×
  일 1,000 안에서 진행 — 기록은 `reports/evidence/m6/6g/commands.md`, 수집 기록 PR #56)과 그 백테스트 판정은 표본 완료까지
  수십 일이 걸리므로 **M6 완료 조건과 분리해 「6G 종결」로 따로 닫는다.** 조건 9 의 「verifier 최종 ready-for-review」는
  6G 를 제외한 M6 산출물에 대해 재며, 6G 종결은 `milestone-6.md` 6G 절의 종결 문단으로 적는다. 이 조정은 수집 실행을
  새로 허용하는 것이 아니다(실행 승인은 2026-10-02 에 이미 있었다).
- 공공데이터포털 운영계정 신청은 M7 종결 뒤 절차다(`milestone-7.md` 「M7 종결 뒤」).

**6F-5 분할·6F-5-a 착수 2026-09-19** — base `ede5d5b`, 레인 worktree `bid-vector-v2-m6f5`·브랜치
`m6-6f5/2026-09-19`. 정본 `reports/evidence/m6/6f5a/scope.md`(D-6F5-1~8). 착수 조사가 **구조적 제약 셋**을
냈고 그것이 분할 근거다: ① **`WatchGatedExtractor` 는 `WatchVerdict.Passed` 를 요구하는데
`LicenseGatePort.verdictFor(notice)` 는 그 값을 주지 않는다** — 판정 경로에서 추출을 부르려면 port 를 열거나
어댑터가 감시를 재평가해야 하고, 둘 다 이 slice 가 할 일이 아니다 ② **실제 LLM 호출은 사용자 승인 대상**이고
매 run·매 후보 호출은 비용·비결정성·지연을 판정 경로에 넣는다 ③ production 에서 `Passed` 가 나오려면
**6F-4 선행**이다(다른 세션 진행 중).

**6F-5-a 가 하는 것**: 요건 **영속 자리**(V13)를 만들고 `LicenseGatePort` 가 **저장된 것만 읽어** 판정한다 —
LLM 호출 0, port 시그니처 무편집, 6F-4·6F-6 브랜치 무의존(`OperatorLicenses` 는 **`OperatorProfilePort` 로만**
받는다, D-6F5-3). **표가 비어 있는 동안 게이트는 모든 공고에 `Uncertain(RequirementDataAbsent)` 를 낸다 —
가짜가 아니라 참인 진술**이고, 1C U-5 가 「`Uncertain` 은 미보유가 아니다」로 정해 use case 가 후보를 떨어뜨리지
않는다(실측: `licenseGateDrop` 이 `Ineligible` 만 떨어뜨린다). 이 slice 는 **판정을 바꾸지 않고 자리를 만든다.**

핵심 결정: 커널의 세 갈래(`DataAbsent`/`CollectionFailed`/`Collected`)를 **표가 잃지 않게** 저장한다(D-6F5-4 —
「행이 없다」와 「수집을 시도했으나 실패했다」는 다른 사실이고, 후자를 전자로 접으면 실패가 조용히 사라진다.
6F-6 이 `licenses_declared` 로 같은 문제를 푼 형태를 따른다) · 마이그레이션 **V13**(V10 6A-1 · V11 6F-4 ·
V12 6F-6 선점) · **병합 순서는 V12 뒤**(D-6F5-8) · `migration-reviewer`·`privacy-gate` 를 붙이고 **Codex 는
대상 아님**(적용 DB 인스턴스 0 — 6F-4·6F-6 이 각각 실측).

**6F-5-b 로 미룬 것**(`OPEN-6F5-EXTRACTION-FILL`): 요건을 **채우는 경로**. 추출 시점(감시 통과 시점 vs 별도
job)과 무효화 정책이 그 slice 의 결정이고, **실제 LLM 호출은 운영자 승인 대상**이다.

**6F-5-a 판정 결과 2026-09-19 (계약 갱신 (1), D-6F5-9~13)** — verifier `not-ready`(산출물 HIGH 2) ·
code-reviewer(HIGH 1) · migration-reviewer 통과(권고 2). **HIGH 둘은 착수 계약의 우회 처분이 틀렸다고
지목한 자리**다. ① **허용 루트 안의 좌표는 「루트 단위 금지」로 닫히지 않는다** — 정책 로더가
`bidvector.qualification`(어댑터가 커널을 보려면 필연적으로 허용해야 하는 루트)에 있어, 어댑터가 정책 파일을
직접 읽어도 의존 게이트·전건 `check` 가 초록이었다. 처방은 **상수 풀 부재 단언**이다 ② **「판정 호출 자리가
하나」는 존재 단언이라 부족하다** — 호출을 남긴 채 결과만 갈아치우면 통과한다(실측: `Collected(빈 rows)` 경로만
`Eligible` 로 뒤집었더니 전건 초록, 커널 참값은 `Uncertain`. **면허 판정에서 가장 비싼 방향의 오판**). 처방은
**`LicenseVerdict$` 부재 단언 + 빠져 있던 `Collected(빈 rows)` 케이스**다. 나란히 있던 커버리지 공백이 사실을
말한다 — D-6F5-4 가 표를 둘로 가른 그 상태를 **저장만 잠그고 판정은 안 잠갔다**.

**이 slice 가 신설해 인계하는 OPEN 둘** — **`OPEN-MIGRATION-ORDER-GATE`**: 마이그레이션 번호 순서 병합
규율에 **자동 게이트가 없다**(세 slice 가 연쇄로 수동 규율에 의존한다. 산문으로만 사는 규율은 어긴 것이
보이지 않는다 — 되돌리기 목록 낡음이 세 번 재발한 것과 같은 형태). **`OPEN-CPD-GATE-RENAME-BYPASS`**:
CPD 중복 게이트가 **이름 치환 하나로 열린다**(변수명만 바꿔도, 판정식만 바꿔도 통과 — verifier 실측).
둘 다 받는 쪽은 **하네스 레인**이고 실 배선 전에 닫아야 한다. `OPEN-6F5A-RETENTION`(요건 표 수명 정책 없음,
받는 쪽 **6B-3**)도 여기 함께 등재한다.
**r2·r3 이 이어서 같은 계열을 두 번 더 지목했고, r3 에서 그 계열이 닫혔다.** r2: 그 부재 단언들이
**클래스 파일 하나**에만 걸려 있어 **같은 패키지의 형제 파일로 한 칸 옮기면 둘 다 통과**했다(전건 `check`
exit 0, 후자는 다시 **부적격 → 적격** 방향). 원인은 **팀장이 r1 에 쓴 처방 문면이 클래스를 콕 집은 것**이다 —
술어의 범위가 계약의 방어 선언(「**어댑터가**」)보다 좁았다. 처방은 **패키지 전체 class 목록**으로의 범위 교체
(D-6F5-14). r3 에서 그 우회 넷이 전부 RED 로 재현됐다.

**r3 이 답한 구조적 물음 — 이 slice 의 가장 값진 산출이다.** 팀장이 「위치 술어를 한 칸씩 넓히는 것이 수렴인가
발산인가」를 표적으로 물었고, verifier 가 실측으로 답했다: **수렴이되 0 이 아닌 바닥으로** 수렴한다. 우회 비용이
「게이트에 한 줄 → 형제 파일 추출 → 이웃 패키지 + **관용을 벗어난** 전체 한정 좌표」로 단조 상승했고, r3 에서
처음으로 **평범한 형태가 잡힌다**(평범한 import 로 이웃 패키지에 옮기면 `PersistenceAdapterDependencyTest` 가
RED 를 낸다 — 패키지 범위가 서는 것은 그 자체 힘이 아니라 **이웃 게이트와의 사슬**로다). **발산하는 것은 술어가
아니라 「범위를 한 칸 넓힌다」는 처방** 자체다 — 모듈 전체로 넓혀도 커널 모듈 헬퍼가 뚫는다. **위치 술어 사다리에
종점이 없다.** 종점은 **타입**이다(`LicenseVerdict` subtype 생성자를 `internal` + `@ConsistentCopyVisibility` —
main 의 생성 지점은 커널 둘뿐이고 밖은 전부 읽기만이라 깨지는 것은 test 조립뿐). 그래서 **네 번째 칸을
요구하지 않았다**(D-6F5-18·D-6F5-20).

**승인 전 처분으로 닫는 산출물 MEDIUM 하나** — V13 이 더한 `CHECK` 일곱이 **테이블별 개수 축만** 받아,
셋을 `CHECK (TRUE)` 로 약화해도 상태 열거에 값을 더해도 전건 초록이었다. **이 slice 가 세 라운드에 걸쳐
닫아 온 결함 클래스(있는지만 보는 단언)가 마이그레이션 축에 그대로 남아 있었다.** 그 파일은 in_scope 안이고
이웃 두 표는 이미 본문 단언을 받았으므로 계약 확장 없이 닫는다(D-6F5-16).

**이 slice 가 신설해 인계하는 OPEN 셋 더** — **`OPEN-PERSISTENCE-GATE-PREDICATE-TYPE`**:
`PersistenceAdapterDependencyTest` 가 **소스 텍스트 import 정규식**이라 전체 한정 좌표를 못 본다. 술어 종류를
바이트코드 상수 풀로 바꾸면 `adapters` 모듈 안에 그 코드가 앉을 자리가 없어진다 — 그 파일은 이 slice 의
in_scope 밖이고 **6F-4 가 그 패키지를 편집 중**이라 여기서 건드리지 않는다(받는 쪽 **하네스 레인**).
**`OPEN-VERDICT-CONSTRUCTION-VISIBILITY`**: 위 종점 — 받는 쪽 **도메인 레인**.
**6A-1 이 신설해 인계하는 OPEN 넷**(2026-09-23) — **`OPEN-6A1-CONNECTION-POOL`**: 커넥션 풀 없이
(`PGSimpleDataSource`) 배선했다, **이 상태로 운영에 나갈 수 없다**(6C/6E) · **`OPEN-6A1-CREDENTIAL-RAW-
REINTRODUCTION`**: 자격증명 타입이 **컴파일 시점 접근**은 막으나 **런타임 리플렉션**(단일 파일 4줄)과
**허용 목록 simple name 재사용**(별칭 import)은 열려 있다 — 뿌리가 구조적이다(환경변수 원문은 어딘가에
`String` 으로 있어야 한다), 받는 쪽 **6A-2** · **`OPEN-6A1-SCAN-FILTER-SIDE-EFFECT`**: 명시
`@ComponentScan` 이 Boot 기본 `excludeFilters` 둘을 가린다(오늘 거동 영향 0, **6A-2**) ·
**`OPEN-JAR-CONTENT-GATE-BOOTJAR-BLINDSPOT`**: `jarContentGate` 가 배포물 `bootJar` 를 안 보고 `jar` 만
보며 **빈 아카이브를 통과**시킨다(두 verifier 레인 실측, **하네스 레인**).

**`OPEN-CHECK-BODY-PRESENCE-ASSERTIONS`**: `CleanMigrationCheckTest` 의 COL-06·H-3 단언 셋이
`any { contains }` **존재 단언**이라 CHECK 본문을 제자리에서 항진명제로 약화해도 통과한다(6F-5-a 가
자기 표에서 실측한 뒤 같은 형태를 남의 표에서 확인했다 — 범위를 넓히지 않고 넘긴다. 받는 쪽
**하네스 레인**).
**`OPEN-CODEX-RECORD-COORDINATES`**: Codex 심판 기록(append-only, 수정 금지)이 `file:line` 을 인용해
**원리적으로 낡는다**. 기록은 못 고쳐도 **다음 심판의 인용 관례**는 닫을 수 있다(받는 쪽 **하네스 레인**).


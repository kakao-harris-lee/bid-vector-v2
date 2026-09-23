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
   `OPEN-6F4-TITLE-WIRING` 을 닫는다.
3. **6A-3 + 6F-3**(합침) — 평가 endpoint + `CapacityPort`. 여기서 **`MlAnalysisPort` 처분**(실 gRPC gateway
   배선 vs 자리지킴 유지)을 운영자에게 올린다 — 4B-6b 가 6A 로 넘긴 결정이고 **실 외부 호출 축**이다.
4. **조립**(`OPEN-6F-ASSEMBLY`) — 포트 아홉을 app 에 꽂는다. 1~3 이 전제다.

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


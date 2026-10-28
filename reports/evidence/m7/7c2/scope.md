# M7/7C② — 투찰가 요청: 공고 단건 · 운영자 요청 평가 (착수 계약, 2026-10-23, 팀장)

- **근거**: `milestone-7.md` 7C ②(「투찰가 요청 = 기존 `evaluation-dry-runs` 를 공고 단건·운영자 요청으로(외부 effect 는 알림함 기록까지 —
  R-4)」) · 운영자 결정 2026-10-15(게시 하한율만 근거 · 「법정 하한율 미해석」 표시 · DEC-03 은 G2, DEC-10 은 G1) · 운영자 지시
  2026-10-23 「7C②·③·⑤ 도 지금 레인 열어서 병렬로 진행」 — `milestone-7.md` 선행 조건의 **운영자 예외**.
- **base**: main `46ac0949`(2026-10-26 재고정 — 7B PR #89 · 7C④-2a PR #90 을 차례로 병합했다. 최초 착수 base 는 `a61ddff1` 였고, rollback 기준도 이 새 base 다) · worktree `bid-vector-v2-m7-7c2` · 브랜치 `m7-7c2/2026-10-23`.
- **병렬 레인**(전부 push 0): **g1dec05(DEC-05 투찰가 메뉴 커널 — 이 slice 의 선행)** · 7C③(알림함 쓰기 포트 — 이 slice 의 effect 끝) ·
  7B · 7C④ · 7C⑤ · 7C①b · g1c03 · g1set01.
- **입력 정본**: `docs/discovery/m7-screen-data-requirements.md` 화면 C 의 자격·투찰가·근거 행(QUAL-01·03·05 · DEC-01 · DEC-03 · DEC-04 ·
  DEC-05 · DEC-10 · ML-01 · ML-03) · 추적표·capability map 의 같은 행과 `OPEN-6A3-EVALUATION-DETAIL` · `OPEN-ML-ANALYSIS-WIRING` ·
  `openapi/bidvector-operator-api.yaml` 의 `evaluateDryRun`(전략 전체 평가 — 이 slice 는 그 문면을 바꾸지 않는다) · g1dec05 브랜치의
  `bidvector.decision.menu.*`(읽기 전용 참고, 미머지).

## 이 slice 가 닫는 절반과 남기는 절반

화면 C 의 투찰가 카드는 **3안(recommended/aggressive/safe)** 을 요구하지만 그 타입·커널(DEC-05)은 g1dec05 레인에만 있고 미머지다. ML 은 출하
조립에서 자리지킴이다(`OPEN-ML-ANALYSIS-WIRING`). 그래서 두 단계로 나눈다.

- **7C②-a (지금, main 위)**: 공고 단건 평가 endpoint 의 표면과 **근거 절반** — 자격 판정 축별 통과/불통과 + 근거 원문(QUAL-01·03·05,
  `OPEN-6A3-EVALUATION-DETAIL` 해소) · 기초금액 basis(DEC-01) · 게시 하한율과 출처 + 「법정 하한율 미해석」 표시(DEC-03 · DEC-10 의
  표시 축) · 하한 미달 빈도·판정 불가 사유(DEC-04) · 신뢰도와 「확률 아님」 표기(ML-03). 3안 자리는 응답 계약에 **「아직 산출 안 함」 상태로
  선언**한다(빈 배열이 아니라 이유가 있는 상태 — 화면이 가짜 숫자를 그리지 않게).
- **7C②-b (g1dec05 머지 뒤 같은 브랜치에서 rebase)**: DEC-05 커널을 불러 3안을 채운다. ML 배선은 `OPEN-ML-ANALYSIS-WIRING` 결정에 따른다 —
  이 slice 가 ML 을 새로 배선하지 않는다(자리지킴이면 자리지킴임을 응답이 말한다).
- **effect**: 요청 결과를 알림함에 한 항목 기록(R-4). 7C③ 의 쓰기 포트가 머지되기 전에는 **기록하지 않고**, 7C③ 머지 뒤 rebase 에서 배선한다.
  지금은 기록이 없다는 사실을 알려진 제한으로 둔다(가짜 포트·임시 표 금지).

이 slice 의 PR 은 **-a 와 -b 를 한 PR 로** 낸다(선행이 늦으면 팀장이 -a 만 먼저 PR 할지 판단한다).

## 산출물 (-a)

1. **OpenAPI**: 공고 단건 평가 operation 하나(경로·메서드는 Phase 1 에서 — 읽기만이면 GET, 요청 기록을 남기면 POST + CSRF). 응답은 축별 판정 +
   근거 + 금액 basis + 하한율 출처 + 3안 상태. 생성 타입 재생성(W-5).
2. **Kotlin**: 기존 평가 커널을 공고 하나에 적용하는 workflow 경로 + controller. 커널 문면은 바꾸지 않는다(새 조립만). 없는 공고 404,
   프로필 미설정 시 「판정하지 않음」 상태(7C④ 와 같은 어휘 — Phase 1 에서 7C④ 레인 문서와 대조).
3. **test**: 축별 판정이 기존 dry-run 과 **같은 입력에서 같은 결과**(차분 test) · 하한율 출처 없음/있음 · 「법정 하한율 미해석」 표시 · 3안 상태 ·
   인증·CSRF · 응답 스키마에 PII·raw 관측 0 · 실 DB 출하 조립 E2E 하나.
4. **추적표·capability map**: QUAL-02(자격 사유 표면 절반) · DEC-03(표시 축) · DEC-04 · STR-16(투찰가 요청 결합 절반) 행에 근거. 판정 문자는
   측정이 서는 만큼만.

## 설계 검토 (Phase 2.5 — 레인이 Phase 1 설계 문서 §0 에서 채우고 팀장이 수령)

- **(0) 위협 모델**: 방어 — 인증 없는 평가 호출 · 다른 계정 프로필로 평가 · 반복 요청으로 비싼 계산 남용(요청당 비용 상한 선언) · 응답에
  PII·raw 관측 누출 · 3안 자리에 근거 없는 숫자. 방어하지 않음 — 단일 운영자가 공개 공고를 평가하는 것.
- **(1)·(2) 우회 ≥5 · (2b) 값 획득 축 · (3) 과잉·미달** — 레인이 채운다. 과잉 금지: 커널 변경 · ML 신규 배선 · 알림함 임시 구현 · 화면.

## in_scope / out_scope

- in: `openapi/bidvector-operator-api.yaml` · `web/src/api/generated/operator-api.ts` · 단건 평가 workflow·controller 와 test · 게이트 설정
  (`config/quality/**`)의 필요한 줄 · 추적표·capability map 의 위 행 · `reports/evidence/m7/7c2/**`. (-b 에서 DEC-05 호출 조립 추가.)
- out: 평가·자격·DEC-05 커널 문면 · 마이그레이션 · ML 배선 · 알림함 표 · `web/src/**` 의 생성물 외 · `.github/**`.

## acceptance

`ci.yml` 의 `check` job 과 `web` job 명령 그대로. `ml-engine`·`container` 는 입력 변경 0 을 path-limited diff 로 보이면 생략.

## rollback

`git restore --source=46ac0949 --staged --worktree -- <in_scope 개별 경로>`(evidence 제외), 새 파일 삭제. 목록 기계 산출, 마지막 산출물
커밋에서 ①~⑥ 실측(-b rebase 뒤 다시 잰다).

## 검토 레인

verifier(opus) · code-reviewer(sonnet) 병렬. Codex 없음(되돌리기 어려운 경로 아님). 판정은 -b 까지 들어간 SHA 에서 한 번(-a 단독 PR 이 되면
그 SHA 에서).

## 호스트 빌드

모든 Gradle·npm 명령은 `flock ~/.local/bid-vector-build.lock <명령>`. 점검 셋은 별도 호출로 먼저. 00:30~03:00 KST 무거운 빌드 금지.

## 계약 갱신 이력

**D-7C2-1 (2026-10-23, 팀장 — Phase 1 설계 `f5310421` 수령).**
- **전제 정정**: 출하 조립의 ML 분석 포트는 항상-미가용 고정이고(`OPEN-ML-ANALYSIS-WIRING` 은 운영자 결정 ④ 개정 2026-09-27 로 보류, 선행은
  6G 백테스트), g1dec05 가 「ML-01 이 닫히기 전에는 메뉴가 production 에서 `SourceAbsent` 외 값을 낼 수 없다」고 측정했다. 따라서 「-b =
  g1dec05 머지 뒤 3안을 채운다」는 성립하지 않는다.
- **Q1 → -b 를 열지 않는다. 이 slice 는 -a 단독으로 종결·PR 한다.** 3안은 ML-01 뒤 별 slice. 응답은 3안·신뢰도·하한 미달 빈도를 **상태 enum +
  사유**로만 내고 값 필드는 선언하지 않는다. 위 「-a 와 -b 를 한 PR 로」 문면은 이 결정으로 대체된다.
- operation `POST /api/bid-price-requests`(공고 식별자는 본문, CSRF 는 구성으로) · 응답 평탄 유지(그룹 둘 이상 미보유는
  `MULTIPLE_GROUPS_NOT_FLATTENED`) · 「확률 아님」은 고지 코드 + 필드 이름 집합 test · 커널 무편집 조립(단건 후보 소스 · 자격 판정 pin ·
  ML 결과 record).
- **Q2** 단건 경로에 분석 예산 미적용(의도된 어긋남, test 로 고정). **Q3** DEC-04 는 재지 않고 상태 + OPEN(정책 수치는 운영자 결정 대상).
- **Q4 in_scope 추가**: 게이트 등재 셋(`architecture-policy.properties`·`gate-tests.properties`·`api-type-policy`)과 기존 자격 어댑터의 1줄 위임
  (거동 불변 test 동반).
- **Q5** 미판정 축 ID 는 7C④ 와 바이트 동일(`QUAL_08_REGION`·`QUAL_11_CONSTRUCTION_CAPACITY`) + `QUAL_10_PERFORMANCE`. 뒤에 머지되는 쪽이
  어휘 등식을 맞춘다. **Q6** 7C③ 머지 전 `inboxRecordState: NOT_RECORDED_YET` 선언(가짜 포트 없음).

**D-7C2-3 (2026-10-23, 팀장 — 구현 중 막힘 수령).** 「응답 평탄 유지」와 `limit.type.members=30`(ADR 0007)이 함께 서지 않는다(평탄 응답 49+ 필드).
**R7**: Kotlin 응답 타입은 축별로 쪼개고 JSON·OpenAPI 스키마는 평탄 유지(직렬화 펴기). 키 집합 등식 test 를 출하 직렬화로 재고, 키 충돌 0 을
단언한다. `app.http.allowed-packages` 에 Jackson 애너테이션 패키지 한 줄 추가(근거 evidence). **사전 승인된 대체 R4**: 펴기가 실측으로 안 되면
평탄 술어를 「깊이 정확히 1, `$ref` 대상 속성이 전부 스칼라일 때만」의 허용 목록으로 넓힌다 — 게이트 술어 변경이므로 표적 재검증 대상이고
in_scope 에 해당 contract test 를 더한다. operation 분할(R6)은 하지 않는다.

**D-7C2-4 (2026-10-23, 팀장 — in_scope 대조 수령).** 추가 수용: `app/.../http/ErrorBody.kt`(새 code `NOTICE_NOT_FOUND` + 404 — 오류 형태의 단일
자리) · `app/.../wiring/EvaluationWiring.kt`(출하 배선, 자격 정책 해소 공유) · `app/src/test/.../HttpTestSupport.kt`(test 앱 빈). 전부 추가만, 기존 문면
불변. 추적표: DEC-04 ⓒ 유지(표면만) · DEC-03 「법정 하한율 미해석」 표시 test 잠금 · QUAL-02·STR-16 은 선 절반만 적는다.

**D-7C2-5 (2026-10-24, 팀장 — 허용 목록 등재 요청 수령).** 넷 다 집합 등식 등재이고 게이트 술어는 바꾸지 않는다. ① `app.port-call.allowed-pairs`:
단건 평가 팩토리 → 새 읽기 포트 둘의 메서드 + `StrategyReader.load`(dry-run 팩토리와 같은 관례). ② `app.injection.allowed-types`: 단건 평가
팩토리(`app.http.allowed-classes` 등재와 짝). ③ `app` 모듈 바깥 참조 허용 집합: `com.fasterxml.jackson.annotation` — D-7C2-3 R7 이 승인한 같은
패키지를 두 번째 허용 집합에 적는 것이며 새 능력이 아니다. 대체 R4(술어 변경)와 손 직렬화기는 택하지 않는다. ④ `ConstantTimeComparisonStructureTest`
areEqual 허용: 새 `app.http` 클래스 — 패키지를 옮겨 게이트를 비껴가지 않는다.

**D-7C2-6 (2026-10-24, 팀장 — PR #88 결정에 따른 in_scope 추가).** 추적표(`reports/evidence/m6/6e1/acceptance-trace.md`)의
**DEC-05 · DEC-02 두 행**을 이 slice 가 맡는다. 「아직 산출 안 함」 상태 test 가 서면 장부 커밋에서 두 행을 「선언 예외 → ⓐ」로 바꾼다. 판정 근거는
「커널·구조 성립 + 화면의 『아직 산출 안 함』 상태 표시가 test 로 잠김」이고, **값 출처 축은 ML-01 과 함께 6G 백테스트 판정 뒤로 남긴다**.
g1dec05 가 먼저 머지되면 두 행은 「ⓒ + 선언 예외 등재」 상태로 들어오고 이 slice 가 rebase 뒤 전환한다. **DEC-05 커널 문면은 계속 범위 밖이고
바뀌는 것은 추적표 행뿐이다.**

**갱신(`verifier r2 R2-L1`, 2026-10-28) — 이 slice 는 ⓐ 로 전환하지 않는다.** 병합된 `milestone-6.md` 가 그 판정의 **주인을 g1dec05 로**
정했다(「그 판정을 적는 자리와 주인은 g1dec05 slice 다」). 그래서 위 「이 slice 가 전환한다」는 그 M6 결정으로 **대체됐다** — 이 slice 는 두 행에
「아직 산출 안 함」이 상태로 섰다는 **근거만** 적고 판정 문자는 ⓒ 로 둔다. 추적표 행의 문면도 D-7C2-6 을 인용하는 대신 그 소유 이전을 적는다.

**D-7C2-7 (2026-10-24, 팀장 — ② 범위 확장 승인).** 주입 허용 목록에 **축별 묶음 DTO 여섯**을 더하는 것을 승인한다. 근거: **R7 을 유지하면 존재하는
표면**이고(응답 타입을 축별로 쪼갠 것이 R7 의 기법 자체다), 그 목록에 값 타입이 이미 올라 있는 **선례(감사 행 값 객체 · 전략 · 업무 대분류)와 같은 꼴**
이다. 아울러 조건 (c) 변이 규칙을 「등재마다 하나」에서 **「종류마다 대표 하나」**(여섯)로 바꾼다 — 등재 항목이 열아홉으로 늘어 빌드 시간이 과하다.

**D-7C2-8 (2026-10-25, 팀장 — 주입 허용 넷의 새 등재).** `D-7C2-5` ②·`D-7C2-7` 의 범위 밖 새 등재 넷이다. 넷 다 **등재 직전에 그 게이트가 이름으로
보고한** 항목이고(모집단 안, §0.9f (e) 의 술어), 각자 그 목록의 선례와 같은 종류다.
- `bidvector.app.wiring.BidPriceRequestRun` — **요청 스코프 run**. 선례: dry-run run 이 쥔 피주입 타입들이 이미 그 목록에 있다. 이 run 이 목록에
  오르는 것은 요청 결과 sealed 갈래가 그것을 들기 때문이다(dry-run 은 그 래퍼가 없어 run 자신은 안 올랐다).
- `bidvector.adapters.evaluation.RecordingMlAnalysis` — **기록 어댑터**. 선례: `RecordingNotificationRequestPort` 가 같은 꼴로 이미 있다.
- `bidvector.procurement.Notice` · `bidvector.qualification.LicenseJudgement` — **도메인 값**. 선례: `OperatorStrategy`·`BusinessDivision` 이 같은 꼴로 이미 있다.

**D-7C2-9 (2026-10-25, 팀장 — 조립 층(tier2) 다섯 키 등재).** 요청 스코프 팩토리·run 이 그 층에 등재되지 않아 그 층의 면제를 못 받고 어댑터 참조가
전부 위반으로 잡혔다(선행 통제군이 15 줄로 보고). **dry-run 짝의 등재를 그대로 미러링**하며 게이트 술어는 바꾸지 않는다. 키마다 「미러링 선례 ·
그 15 줄 중 이것을 요구한 줄」을 적는다.
- `app.assembly.tier2-request-scoped-classes` ← 팩토리·run. 선례 `EvaluationDryRunFactory`·`EvaluationDryRunRun`. 요구한 줄: 팩토리·run 이 **주어**인 줄 전부
  (이 둘이 그 층 구성원이 아니면 면제 자체가 적용되지 않는다).
- `app.assembly.tier2.allowed-classes` ← `RecordingMlAnalysis`·`SingleNoticeCandidateSource`·`PinnedLicenseGate`. 선례 `PinnedStrategyRepository`·
  `RecordingNotificationRequestPort`·`RequestCapacityPort`. 요구한 줄: `팩토리 -> 그 셋` 과 `run -> RecordingMlAnalysis`.
- `app.assembly.tier2.allowed-workflow-types` ← 새 읽기 포트 둘. 선례 `StrategyReader`·`EvaluateCandidatesUseCase`(이미 등재). 요구한 줄:
  `팩토리 -> NoticeByIdPort` · `팩토리 -> LicenseJudgementPort`.
- `app.workflow.use-case-construction-pairs` ← `팩토리|EvaluateCandidatesUseCase`. 선례 `EvaluationDryRunFactory|…`. 요구한 줄: `팩토리 가 조립한다 -> EvaluateCandidatesUseCase`.
- `app.adapter.member-call-pairs` ← `run|RecordingNotificationRequestPort|requested` · `run|RecordingMlAnalysis|recordedOutcome`. 선례
  `EvaluationDryRunRun|RecordingNotificationRequestPort|requested`. 요구한 줄: 그 두 메서드 호출 줄. **셋째(`callCount`)는 등재하지 않고 코드에서
  없앴다** — 유일 호출자가 test 였고 그 test 는 자기 계수 fake 로 같은 값을 이미 쟀다(넓히기 전에 없앴다).

**정정(verifier r1 M-4, 2026-10-26).** 위 문단은 `D-7C2-9` 수령 시점의 상태이고 **지금 HEAD 와 다르다.** 앞서 이 자리에
「`app.assembly.tier2.allowed-app-classes` 에는 아무것도 넣지 않았다」고 적었는데, main 병합 뒤 커밋 하나가 **넷을 더했다**:

- `app.assembly.tier2.allowed-app-classes` ← `BidPriceRequestRun` · `BidPriceRequestOutcome`
- `app.assembly.tier2.allowed-workflow-types` ← `CandidateEvaluation` · `MlAnalysisOutcome`(위 「새 읽기 포트 둘」에 더해)

병합이 조립 층 게이트의 관측 집합을 넓혀 **그 넷이 없으면 집합 등식이 붉었다**(RED 가 등재에 앞섰다 — §0.9f (e) 의 술어를 지켰다).
근거가 커밋 메시지에만 있고 이 문서에 없던 것이 결함이었고, **하중은 verifier 가 독립 변이로 다시 쟀다**(둘을 각각 지우면 RED).


**D-7C2-10 · D-7C2-11 (팀장 수령, 2026-10-24~25 — `verifier r1 M-3` · `cr r1 LOW-3` 로 여기 등재).** 앞서 이 둘은 checklist
§0.9g·§0.9h 에 레인이 스스로 적은 선언으로만 있었다. 수령 기록이 계약 문서에 없으면 **남의 표면을 바꾼 근거가 레인의 말뿐**이 된다.

- **D-7C2-10 — 병합을 먼저 한다.** 변이·web·S-20 을 먼저 재면 병합이 공유 하네스·계약·정책·등식형 게이트를 바꾸므로 그 측정이 전부
  버려진다. 그래서 순서를 바꿔 main 을 먼저 받았다.
- **D-7C2-11 — 미판정 축 어휘의 세 번째 사본도 합친다(남의 slice 표면을 바꾼다).** 7C①b 가 머지한 `workflow.suggestion` 쪽 사본은
  항목이 **둘**이라 QUAL-10 이 빠져 있었다(의도가 아니라 시점 차이 — 그 축은 그 slice 머지 **뒤** `D-7C2-1` Q5 로 들어왔다). 빠진 채로
  두면 면허 판정만 있는 제안 카드가 **수행실적까지 판정된 것처럼** 읽힌다. **wire 에 enum 값 하나가 더해지므로** `web` job 이 acceptance 에
  들어온다.

**in_scope 추가(D-7C2-11 의 귀결).** 아래 경로가 in_scope 로 들어온다 — 전부 기존 파일 수정이다.
`workflow/.../profile/{BusinessProfileValues,OnboardingChecklist}.kt` · `workflow/.../profile/UnjudgedQualificationAxis.kt`(삭제) ·
`workflow/.../suggestion/ListSuggestionsUseCase.kt` · `workflow/.../profile/ProfileJudgementIsolationTest.kt` ·
`app/.../http/SuggestionEndpointTest.kt` · **편집 필드 어휘 파일**(`app/.../http/`, 아래 자국 2 가 가리키는 그 파일 —
그 파일 이름을 축어로 적으면 누출 스캔 어휘에 걸린다. 오탐이지만 게이트는 참·거짓을 가리지 않는다).

**수정 라운드가 만든 파일 — in_scope 대조(2026-10-26).** 라운드가 끝날 때마다 계약과 맞춰 본다. **대조 축은 「새 파일」과
「수정 파일」 둘이다** — 앞 판은 새 파일만 보아, 위 일곱(전부 기존 파일 수정)이 그 대조에서 빠졌다(`verifier r1 M-3`).

- `app/src/test/.../StrategyEditTestDoubles.kt` — **in_scope 안**이다(`D-7C2-4` 가 `HttpTestSupport.kt` 를 수용했고,
  이 파일은 그 파일을 가른 조각이다). 가른 이유는 그 공유 파일이 **정확히 500줄**(파일 한도)이라 test 앱에 `@Import`
  두 줄을 더할 자리가 없었기 때문이다 — 상한이 분할을 요구한 자리다. 가른 축을 「전략 편집 이중체」로 고른 것은
  평가 축(7C⑤ `EvaluationTestDoubles.kt`)과 시계 축(7C④-2b `ClockTestDoubles.kt`)을 다른 레인이 같은 이유로 이미
  가져갔기 때문이고, **선언 교집합 0 을 기계로 확인**했다(겹치면 병합 뒤 중복 선언으로 컴파일이 깨진다).
  `HttpTestSupport.kt` 는 500 → 435 줄. 같은 패키지라 쓰는 자리의 import 는 하나도 바뀌지 않는다.
- `reports/evidence/m7/7c2/{commands,rollback}.md` — evidence 디렉터리, in_scope 안.
- **r1 이 만든 둘(2026-10-27)** — 둘 다 in_scope 안이다.
  - `app/src/test/.../OpenApiBidPriceVocabularyTest.kt` — 이 slice 응답의 문서 enum ↔ 구현 어휘 등식(`verifier r1 M-1` · `cr r1 M3` 의 처방).
  - `workflow/src/main/.../evaluation/UnjudgedQualificationAxis.kt` — 단일 출처 enum 을 `SingleNoticePorts.kt` 에서 **같은 패키지의 자기
    파일로** 옮긴 것이다(`cr r1 LOW-2`). 패키지가 같아 쓰는 자리의 import 는 하나도 바뀌지 않는다 — 새 타입이 아니라 자리만 옮겼다.

**공유 파일에 남긴 자국 넷(동결 보고 항목).** 되돌리기는 base 복원으로 닫히지만, 머지되면 남의 레인이 보는 것들이다.

0. **`HttpTestApplication` 의 `@Import` 자리**(`cr r1 HIGH`). 이 slice 는 `@Import(BidPriceTestWiring::class)` 를, 7C④-2b 는 **같은 자리**에
   `@Import(ProfileTestWiring::class)` 를 두고 있다(그 worktree 에서 실측). `@Import` 는 `@Repeatable` 이 아니라 **둘 다 남기면
   `Duplicate annotation` 으로 HTTP test 가 통째로 컴파일 실패**한다. git 이 충돌로 세워 주지 않을 수도 있다(한쪽이 먼저 머지되면 뒤 쪽은
   자기 줄만 더하는 모양이 된다).
   **병합 규칙**: 뒤에 머지되는 쪽이 **한 줄로 접는다** — `@Import(BidPriceTestWiring::class, ProfileTestWiring::class)`. 접고 나면
   HTTP test 가 서는지가 곧 검산이다(둘 중 하나를 빠뜨리면 그 빈을 받는 컨트롤러가 기동하지 못한다).
   **7C③ 분기(`cr r2 M-3`)**: 7C③ 는 그 선언을 `HttpTestApplication.kt` 로 **옮겼다**. 그 쪽이 먼저 머지됐으면 `@Import` 를
   `HttpTestSupport.kt` 에 남기지 말고 **그 새 파일의 선언으로 옮겨 거기서 한 줄로 접는다** — 옛 자리에 두면 선언이 둘이 되어
   어느 쪽도 효력을 보장할 수 없다. 7C③ 레인도 같은 규칙을 자기 계약에 적어 두었다. 7C②+7C③ 는 `git merge-tree` 로
   `HttpTestSupport.kt`·app 층 의존 게이트 test·정책 파일 셋에서 CONFLICT 가 뜨므로(조용하지 않다) 사람이 반드시 본다.
   같은 파일의 **이중체 분할**도 같은 자리다: 세 형제 브랜치(7C③·7C④-2b·7C⑤)의 `HttpTestSupport.kt` 에는 이 slice 가 옮겨 간 세 클래스가
   **아직 있다**(실측). 병합 해소에서 둘 다 남기면 중복 선언으로 컴파일이 깨진다 — 뒤 쪽이 자기 파일에서 그 셋을 지운다. 크게 실패하므로
   조용한 사고는 아니다.

1. **면제 크기 래칫 68 → 70**(`AppHttpDependencyGateTest`). 조립 층 ② 에 요청 스코프 팩토리·run 둘이 더해진 기계적
   귀결이다. 그 래칫의 목적이 「면제가 눈에 띄지 않게 느는 것을 막는다」이므로 **올린 사실 자체가 보고 항목**이다.
2. **가시성 하나를 넓혔다** — 편집 필드 토큰의 감시 규칙 → wire 토큰 사상 함수가 `private` → `internal` 이 됐다.
   투찰가 응답이 **같은 wire 어휘**를 써야 해서이고, 중복 검사(cpdCheck)가 두 번째 사본을 실제로 잡아 재사용을
   강제했다. 새 능력이 아니라 기존 함수의 노출 범위다.
3. **자리를 옮긴 것 둘** — 자격 판정 pin 어댑터는 `adapters.qualification` 으로, 단건 공고 읽기 어댑터는
   `adapters.evaluation` 으로 옮겼다. 패키지 의존 게이트가 원래 자리를 거부했다(위치로 게이트를 비껴간 것이 아니라
   게이트가 가리킨 자리로 옮긴 것이다).

## 하네스 레인 변경 (상시)

- 없음.

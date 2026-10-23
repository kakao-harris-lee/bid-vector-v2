# M7/7C② — 투찰가 요청: 공고 단건 · 운영자 요청 평가 (착수 계약, 2026-10-23, 팀장)

- **근거**: `milestone-7.md` 7C ②(「투찰가 요청 = 기존 `evaluation-dry-runs` 를 공고 단건·운영자 요청으로(외부 effect 는 알림함 기록까지 —
  R-4)」) · 운영자 결정 2026-10-15(게시 하한율만 근거 · 「법정 하한율 미해석」 표시 · DEC-03 은 G2, DEC-10 은 G1) · 운영자 지시
  2026-10-23 「7C②·③·⑤ 도 지금 레인 열어서 병렬로 진행」 — `milestone-7.md` 선행 조건의 **운영자 예외**.
- **base**: main `a61ddff1` · worktree `bid-vector-v2-m7-7c2` · 브랜치 `m7-7c2/2026-10-23`.
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

`git restore --source=a61ddff1 --staged --worktree -- <in_scope 개별 경로>`(evidence 제외), 새 파일 삭제. 목록 기계 산출, 마지막 산출물
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


## 하네스 레인 변경 (상시)

- 없음.

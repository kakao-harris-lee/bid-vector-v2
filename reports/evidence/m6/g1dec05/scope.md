# M6/G1-DEC05 — 투찰가 메뉴(DEC-05) + binding 제약 기록(DEC-02) (착수 계약, 2026-10-21, 팀장)

- **근거**: 운영자 결정 2026-10-15(`milestone-6.md` 「완료 조건」 아래 결정 블록) — G1 9 은 7B 착수 전 M6 잔여 slice 로 닫는다. 7A 조정안
  (`reports/evidence/m7/7a/m6-condition3-adjustment-proposal.md`) G1 표: DEC-05 「메뉴 타입 + 커널(7C ② 가 그 위에 표면을 얹는다)」 ·
  DEC-02 「DEC-05 와 한 slice」(선언 예외 ② — 메뉴의 근거 칸이 곧 DEC-02 의 빈 자리). 홀더: 새 M6 slice 「투찰가 메뉴」.
  운영자 지시 2026-10-21 「G1 먼저」로 COL-03 레인과 병렬로 연다.
- **base**: 착수 `bee7c36f` → `fc15975c`(머지 `851ee375`) → **현행 `46ac0949`**(병합된 main,
  머지 커밋 `20b7129a`, D-G1DEC05-3 단계 0) · worktree `bid-vector-v2-m6-g1dec05` ·
  브랜치 `m6-g1dec05/2026-10-21`.
  **rollback 복원 기준도 `46ac0949`** 다 — 착수 base 로 복원하면 머지로 들어온 남의 변경까지
  지운다(실제로 `gate-tests.properties`·`capability-map.md`·`acceptance-trace.md` 가 SET-01 ·
  COL-03 · 7B · 7C①b · 7C④ 머지분을 잃는다).
  전제(그 SHA 에 이 slice 가 없다)는 착수 커밋 `e88c069b` 미포함으로 확인했다.
- **입력 정본**: `docs/discovery/capability-map.md` DEC-05 · DEC-02 절(acceptance bullet 이 정본), 제약하는 DEC-01 · DEC-04 · DEC-10 bullet ·
  `reports/evidence/m6/6e1/acceptance-trace.md` DEC-05 · DEC-02 행(「무엇이 비었는가」) · 추적표 머리의 ⓐ 술어 ②(production 배선과 출하 정책
  값에서 성립).

## 산출물

1. **메뉴 도메인 타입**(ADR 0006 D-2: `decision` 모듈 — 「법정 하한, 추천 후보 평가, reason code」).
   - 옵션 셋(recommended · aggressive · safe). 옵션은 **(태도, 리스크 노트, 근거)가 없으면 만들 수 없다** — 생성자·팩토리가 구조로 막는다.
   - 메뉴의 결과는 sealed 다: 정상 3안 · **밴드 붕괴(상한 ≤ 하한)** · 판정 불가(사유) · 값 출처 부재. 밴드 붕괴는 「정상 3안」으로 위장될 수
     없다.
   - 낙찰 확률을 싣는 칸은 없다(DEC-04 정직 규율). 공통 고지 두 문장(예정가격 비공개 추첨 · 자동 제출·확정 낙찰 아님)의 자리를 둔다.
   - 기존 `BidRateCandidates(conservative, base, aggressive)` 와의 대응을 명시한다(라벨이 다르다 — 매핑을 한 자리에 선언).
2. **DEC-02 binding 기록** — 최종 추천가가 **어느 제약에 binding 됐는지**가 결과에 실린다. 지금 `roundedWith` 는 성공 경로에서
   `Derived<BidAmount>` 만 내고, 하한 미달은 `ROUNDED_BELOW_FLOOR` 실패로만 나온다. binding 을 싣는 결과 타입을 두고, 반올림 하한 이상 보장과
   「서로 다른 basis 의 금액이 한 min/max 에 들어갈 수 없다」를 유지한다.
   - DEC-02 의 **잠정** bullet(예산 상한 · 계약 역전 미채택)은 `OPEN-DEC-03` 재결정 대상이라 **세지 않는다**. 구현하더라도 잠정으로 표시한다.
   - 하한은 **게시 하한율만** 근거로 쓴다(운영자 결정 2026-10-15 Q-2 (b)). 법정 하한율 해석은 하지 않는다. DEC-10 의 신뢰 게이트는 별도 G1
     slice 다 — 이 slice 는 게이트 전/후 값을 구분하는 타입 자리를 침범하지 않는다.
3. **커널** — 입력(기초금액 basis · 후보 비율 셋 · 게시 하한 · 반올림 정책 · 정책 데이터) → 메뉴 결과의 **순수 함수**. 정책 값(legacy 의 매직
   상수 자리)은 **versioned policy 데이터**로 둔다(`*PolicyData` 관례). **legacy 알고리즘은 채택하지 않는다**(DEC-02 분류).
   새 수치 값이 필요하면 값을 정하지 말고 근거와 함께 보고한다(값 결정은 운영자).
4. **ⓐ 판정의 정직성** — 추적표 술어 ② 는 「production 배선과 출하 정책 값에서 성립」이다. 지금 출하 조립은 ML 을 자리지킴으로 고정한다
   (`OPEN-ML-ANALYSIS-WIRING`, ML-01 은 별도 G1). 그래서 **Phase 1 에서** 다음을 먼저 판정해 팀장에게 보고하고, 팀장 답을 받은 뒤 구현한다:
   - 메뉴가 production 경로 어디에서 만들어지는가(소비자). 7C ② endpoint 는 M7 몫이다 — 이 slice 안에서 출하 배선에 메뉴가 서는 자리가 있는가.
   - 출하 정책 값으로 메뉴의 값 출처가 서는가(ML 없이 서는 출처가 있는가, 없다면 ML-01 뒤에야 ⓐ 인가).
   - 그 결과 DEC-05 · DEC-02 각각이 이 slice 로 **ⓐ 가 될 수 있는가, ⓒ(부분 측정)로 남는가**. 의심되면 ⓒ 다.
5. **test** — 각 acceptance bullet 을 타입·커널·(가능하면) 출하 배선에서 잰다. 변이에서 RED:
   - 근거 없는 옵션 생성 허용
   - 밴드 붕괴를 정상 3안으로 반환
   - binding 칸 누락(제약 하나를 지워도 결과가 같음)
   - 반올림이 하한 아래로
   - basis 혼합 min/max 허용(컴파일 실패 하네스 관례)

   바꿔치기 변이 + `git diff --numstat` + 통제군 GREEN.
6. **추적표·capability** — DEC-05 · DEC-02 행을 실제 판정대로 갱신한다(ⓐ 또는 ⓒ 부분 측정 + 새 test FQN, 좌표 금지). 집계를 맞춘다.
7. **이월 하나**: `milestone-6.md` 결정 블록은 G3 OPEN 묶음 둘(`OPEN-M6-RESIDUAL-OPS` · `OPEN-M6-RESIDUAL-ML-LIFECYCLE`)을 「G1 의 첫 M6
   잔여 slice 착수 계약이 capability-map OPEN 표에 등재한다」고 적는다. G1-T 가 등재하지 않았다(main `bee7c36f` 의 capability-map 에 두 ID 가
   없다). 이 slice 가 등재한다 — 목록은 조정안의 G3 표가 정본이다. 활성 OPEN 개수 줄을 함께 맞춘다.

## 설계 검토 (Phase 2.5, 팀장)

- **(0) 위협 모델**: 방어 —
  - 근거 없는 숫자가 운영자에게 「추천」으로 가는 것
  - 밴드 붕괴·하한 충돌이 정상 3안으로 보이는 것
  - 확률로 읽히는 표현
  - basis 가 섞인 clamp
  - 반올림으로 하한 미달
  - 매직 상수가 코드로 돌아오는 것

  방어하지 않음 — 값 자체의 예측 정확도(ML · 백테스트 몫), 법정 하한율(DEC-03, G2).
- **(1) 구성**: 메뉴 결과는 sealed 이고 `when` 전수다(`else` 없음). 옵션·binding 은 `internal` 생성자 + 검증 팩토리. 금액은 원 단위 `Long`,
  비율은 `BigDecimal` fraction, 반올림은 이름 있는 `RoundingPolicy`(.claude/rules/kotlin-service.md).
- **(2) 우회 ≥5**: 산출물 5 의 다섯 변이 + 정책 값을 코드 상수로 되돌림 → 정책 데이터 test RED.
- **(2b) 값 획득 축**: 새 public 표면(메뉴 타입 · 커널 · binding 결과)이 밖에 허락하는 것을 표로 적는다. 특히 「근거 없이 옵션을 만들 수 있는
  public 경로」가 0 인지.
- **(3) 과잉·미달**: endpoint · OpenAPI · 화면 · ML 배선 · DEC-10 게이트 · 법정 하한율 · DB 는 하지 않는다. 미달 금지 — binding 기록 ·
  밴드 붕괴 상태 · 근거 필수.

## in_scope / out_scope

- in: `decision/**`(메뉴 타입·커널·정책 데이터·test) · `shared-kernel/**` 의 반올림 결과 타입(binding 을 싣기 위해 필요할 때만, 근거와 함께) ·
  `workflow/**` 의 prediction 주변(매핑·조합이 필요할 때만) · `config/quality/*.properties`(새 test 등재, 넓히는 줄마다 근거) ·
  `reports/evidence/m6/6e1/acceptance-trace.md` DEC-05 · DEC-02 행과 집계 · `docs/discovery/capability-map.md` DEC-05 · DEC-02 절과 OPEN 표 ·
  `reports/evidence/m6/g1dec05/**`. 경로는 레인이 기계 목록으로 적는다.
- out: 마이그레이션 · `openapi/**` · `web/**` · `.github/**` · `adapters/**` 의 ML 배선 · `app/**` 의 출하 조립(Phase 1 판정이 필요하다고 하면
  계약 갱신으로만) · DEC-10 · DEC-03.

## acceptance

`.github/workflows/ci.yml` 의 `check` job 명령 그대로. `web`·`ml-engine`·`container` 는 입력 변경 0 을 path-limited diff 로 보이면 생략한다
(`app` 이 바뀌면 container 를 `if:` 평가 재생으로 돈다).

## rollback

`git restore --source=46ac0949 --staged --worktree -- <in_scope 개별 경로>`(evidence 제외), 새 파일은 삭제. 목록은
`git diff --name-status 46ac0949..HEAD` 기계 산출. 마지막 산출물 커밋에서 ①~⑦ 실측.

**복원 기준은 「병합된 main 커밋」이지 착수 base 가 아니다**(r3 N-4 · cr M3). 이 절이 두 세대
낡아 `bee7c36f` 를 가리키고 있었다 — 적힌 대로 실행하면 공유 장부가 착수 시점으로 돌아가 남의
머지분이 사라진다. 실측(checklist)은 `46ac0949` 로 옳았고 이 문면만 틀렸다. **머지할 때마다 이
줄과 머리 base 줄을 함께 올린다.**

## 검토 레인

verifier(opus) · code-reviewer(sonnet) 병렬. 표적:
- **ⓐ/ⓒ 판정이 술어 ② 를 지키는가** — test 대역 아래서만 성립하는 것을 ⓐ 로 세지 않았는가.
- 근거 필수가 public 경로 전수에서 성립하는가.
- 밴드 붕괴 위장 0.
- binding 이 모든 제약을 구별하는가.
- 매직 상수 0.
- legacy 알고리즘 미채택.

Codex 없음(되돌리기 어려운 경로 아님).

## 호스트 빌드

레인 둘이 병렬이다(COL-03). 모든 Gradle·Testcontainers·docker 명령은 `flock ~/.local/bid-vector-build.lock <명령>` 으로 감싼다. 점검 셋
(pgrep·free·ps)은 별도 호출로 먼저 본다. **00:30~03:00(6G 수집 창)에는 무거운 작업을 시작하지 않는다.** 6G DB(55432)·run-state·collection
worktree 무접촉.

## 결정 (팀장·운영자 승인분 등재)

착수 계약 작성 뒤 **팀장 메시지로만 승인됐던 결정**을 계약에 올린다 — 메시지에만 있는 승인은
남에게는 없는 승인이다. D-G1DEC05-1·2 의 내용은 `checklist.md` 의 같은 ID 절이 정본이다.

- **D-G1DEC05-3**(2026-10-22, 수정 라운드 2 지시) — code-reviewer `REQUEST_CHANGES`. 고칠 것:
  HIGH-1 태도 라벨 **뒤집는다**(안전 = 가장 높은 투찰율. 근거 셋 — legacy `app/ai/bid_target.py`
  docstring · `app/ai/price_prediction/guardrails_apply.py` 의 후보→stance 브리지 표 ·
  `contracts/proto/bidvector/ml/v1/prediction.proto`(운영자 메뉴는 `CandidateLabel` 축이 아니다)) ·
  HIGH-2 후보 밴드 자신의 붕괴를 `FloorRateReading` 과 무관하게 판정 · HIGH-3 clamp 옵션의
  반올림과 「한 옵션 실패가 메뉴 전체를 죽이지 않게」 · MEDIUM-4 항상 통과하는 test 둘 ·
  MEDIUM-5 `AppliedFloor` KDoc 을 축 한정으로 · MEDIUM-6 `BidRateOrigin` 은 **바꾸지 않고**
  KDoc + 알려진 제한 · LOW-8 「`Offered` ≠ 제약 검토 완료」를 알려진 제한과 7C ② 계약 조건으로.
  verifier 추가분 — M-1(「fail-closed」 과장 → 표식형으로 문면만 낮추고 **거동 불변**) ·
  L-3(추적표 집계 숫자 · checklist 판정 SHA 줄).
- **D-G1DEC05-4**(2026-10-22, 라운드 2 계획 승인) — 붕괴 술어의 꼭대기 파급(`rates.safe`) ·
  HIGH-3 의 `ceil(하한 bound × 기초금액)` 근거(**제약을 만족하는 최소 정수**) · 부분 보류를
  별도 결과 타입으로 · `Offered` = 셋 전부 제시.
- **D-G1DEC05-5**(2026-10-22) — 「Phase 1 약속 ↔ 출하 실물 대조표」를 **전수로** 넣는다. 무선언
  이탈은 사유와 함께 올리고, `ArithmeticTest` KDoc 의 사후 정당화 문장은 **삭제**한다. 재리뷰
  표적에 「checklist Phase 1 서술 ↔ 출하 코드」를 넣는다. 같은 날 승인: 대조표의 **넷째 범주
  「약속 자체가 틀림」** 과 **파일 축 제외**(배치는 설계 판단이 아니다) — 이 넷째 범주는 앞으로
  **모든 레인 대조표의 표준**이다.
- **D-G1DEC05-6**(2026-10-24, 레인 재개 뒤) —
  - `DEC-05` 의 「부분 측정(강함)」 표식은 **유지**한다. D-G1DEC05-4 의 제거 승인은 bullet ② 가
    비어 있던 라운드 1 전제에서 나왔고 라운드 2 가 그 전제를 바꿨다. `E-24` 는 **양쪽 23**
    고정(2026-10-25 확정) — 머지 전 이 레인 **22** + 병합된 **`SET-01` 1**. 애초 문면은
    「양쪽 22」였고 그것은 머지 전 수다. 경위: 병합된 main `fc15975c` 에서 `E-24` 가 이미
    **표식 20 · 열거 19** 로 깨져 들어왔다 — M6/G1-SET01 라운드가 그 행에 표식을 달면서
    **머리 열거에 넣지 않았다**. 이 레인이 **열거만** 채워 수리했다(남의 표식은 그 레인의
    판정이라 건드리지 않는다. 반대로 고치면 남의 판정을 뒤집는 것이 된다). 구간 확인 —
    base `bee7c36f` 19·19 맞음 · 머지 전 `67667e3d` 22·22 맞음 · `fc15975c` **20·19 깨짐**.
  - 팀장 지시 「KDoc 라운드 서사 정리」를 **철회** — 저장소 관례 확인(레인 보고: in_scope 밖
    파일 20여 개가 같은 꼴로 쓰고 finding ID 인용도 관례다). `ArithmeticTest` 삭제는 유지.
  - `FLOOR_PROMOTION_ROUNDING`(대조표 P-20)을 **재리뷰 표적**에 올린다 — 「(e) 신설 수치 0 과의
    관계」.
  - base 를 병합된 main `fc15975c` 로 올린다(위 **base** 줄).
- **운영자 결정 2026-10-22 — `OPEN-DEC05-FLOOR-RATE-READING`** = `AsBidRateLowerBound(AtOrAbove)`.
  **이 slice 에 적용하지 않는다** — 출하 정본은 `Undecided` 그대로이고 적용은 7C ② 에서
  `BID_MENU_POLICY` 갱신으로 들어간다. 결정은 났고 **적용이 남은** 항목이라 활성 OPEN 에 둔다.
- **운영자 결정 2026-10-24 — `ML-01` 이 G1 에서 빠졌다**(docs PR #88). 그 결정 블록이
  `DEC-05`·`DEC-02` 를 **ⓐ 술어 ② 의 선언 예외**로 둔다 — 판정 근거는 「커널·구조 성립 +
  7C ② 화면의 『아직 산출 안 함』 상태 표시가 test 로 잠김」이고, 값 출처 축과 그 값에서만
  생기는 축은 `ML-01` 과 함께 **6G 백테스트 판정 뒤**다. 이 slice 는 두 행을 **「ⓒ + 선언 예외
  등재」**로 적고 **ⓐ 전환은 7C ② 장부 커밋이 한다**. 수치(ⓐ/ⓑ/ⓒ)는 PR #88 이 바꾸지 않는다.

## 계약 갱신 이력

- **2026-10-24 ①** — base 와 rollback 복원 기준을 `bee7c36f` → `fc15975c` 로 올렸다(D-G1DEC05-6).
  사유: 착수 뒤 같은 G1 의 COL-03(#83) · SET-01(#85) 과 M7/7C①b(#87) 가 머지돼 **공유 장부의
  집계가 움직였고**, 그 위에서 재산출하지 않으면 이 slice 가 적는 수가 머지 순간 틀린다.
- **2026-10-24 ②** — in_scope 재대조 결과 **범위 밖 0**. `fc15975c`..판정 SHA 의 경로는 A 26 · M 7
  이고 전부 in_scope 다. 라운드 2 가 만든 새 파일은 compile fixture 둘
  (`decision/src/test/resources/compile-fixtures/negative-3-…` · `mutant-3-…`)이라 in_scope 안이다.
- **2026-10-24 ③** — `OPEN-DEC02-SCORE-AXIS-FLOOR` 를 신설해 장부에 등재했다(라운드 2 실측의
  부산물 — DEC-02 의 빈자리로 지목됐던 호출부가 하한이 적용될 자리가 아니었고, 그 자리의 진짜
  물음이 이것이다). 판정은 **DEC-10 착수 계약**에서 함께 한다.

- **2026-10-26 ①** — base 와 rollback 복원 기준을 `fc15975c` → **`46ac0949`**(머지
  `20b7129a`)로 재고정했다(D-G1DEC05-3 단계 0). 전제(**그 SHA 에 이 slice 가 없다**)는 착수
  커밋 `e88c069b` 가 `46ac0949` 의 조상이 아님으로 재확인했다.
- **D-G1DEC05-7**(2026-10-26, 팀장 수령) — **두 축이 함께 걸리면 율 축이 이긴다.**
  하한 **아래** 후보가 clamp 돼 올라온 뒤 금액 올림까지 타면 `BindingConstraint.PublishedFloorBound`
  (율 축)를 **유지**하고, 하한 **위** 후보가 원 단위 올림만 탄 경우에만
  `BindingConstraint.PublishedFloorRounding`(금액 축, r3 N-1 신설)이다.
  - 근거: 둘을 한 값으로 접으면 「율이 하한 미만이었다」와 「율은 여유가 있었다」가 구별되지
    않는다. 두 사실은 운영자에게 다른 것을 말한다.
  - 집행: `promotedBinding(effective)` 의 `when` 이 **소진**이라 binding 어휘가 늘면 그 자리가
    컴파일로 드러난다. 변이 **A1**(새 variant 를 `CandidateRate` 로 되돌림)이 RED 로 잠근다.
  - 배경: r1 은 올림 경로에 무조건 `PublishedFloorBound` 를 달아 율 축의 뜻을 넘겼고(r2 M-2),
    r2 는 그것을 `CandidateRate`(「걸린 제약이 없다」)로 되돌려 **하한이 금액을 정한 사실을
    지웠다**(r3 N-1). 셋 중 고르는 문제가 아니라 **값이 하나 더 필요했던** 자리다.

- **2026-10-26 ②** — r3·r4 가 만든 새 파일을 in_scope 와 대조했다(cr r3 L-4). r3: compile
  fixture 둘(`negative-4-…`·`mutant-4-…`). r4: compile fixture 다섯(`negative-4a`~`4e`,
  r3 의 `negative-4-…` 하나를 타입별로 가름). 전부 `decision/src/test/resources/**` 라
  **in_scope 안이고 범위 밖 0** 이다.
- **2026-10-26 ③** — `OPEN-DEC05-STANCE-RATE-PROVENANCE` 의 역방향 포인터를 `ML-01` 쪽에
  달았다(r3 L-N2) — 닫는 자리에서 그 OPEN 이 보이지 않으면 유예가 유실된다.

## 하네스 레인 변경 (상시)

- 없음.

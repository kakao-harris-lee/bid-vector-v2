# M6/G1-DEC05 — 투찰가 메뉴(DEC-05) + binding 제약 기록(DEC-02) (착수 계약, 2026-10-21, 팀장)

- **근거**: 운영자 결정 2026-10-15(`milestone-6.md` 「완료 조건」 아래 결정 블록) — G1 9 은 7B 착수 전 M6 잔여 slice 로 닫는다. 7A 조정안
  (`reports/evidence/m7/7a/m6-condition3-adjustment-proposal.md`) G1 표: DEC-05 「메뉴 타입 + 커널(7C ② 가 그 위에 표면을 얹는다)」 ·
  DEC-02 「DEC-05 와 한 slice」(선언 예외 ② — 메뉴의 근거 칸이 곧 DEC-02 의 빈 자리). 홀더: 새 M6 slice 「투찰가 메뉴」.
  운영자 지시 2026-10-21 「G1 먼저」로 COL-03 레인과 병렬로 연다.
- **base**: main `bee7c36f` · worktree `bid-vector-v2-m6-g1dec05` · 브랜치 `m6-g1dec05/2026-10-21`.
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

`git restore --source=bee7c36f --staged --worktree -- <in_scope 개별 경로>`(evidence 제외), 새 파일은 삭제. 목록은
`git diff --name-status bee7c36f..HEAD` 기계 산출. 마지막 산출물 커밋에서 ①~⑥ 실측.

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

## 계약 갱신 이력

(없음)

## 하네스 레인 변경 (상시)

- 없음.

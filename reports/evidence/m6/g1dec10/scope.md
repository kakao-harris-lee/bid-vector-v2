# M6/G1-DEC10 — 게시 낙찰하한율 신뢰 게이트 (DEC-10) (착수 계약, 2026-10-24 초안 · 2026-10-28 착수, 팀장)

- **근거**: 운영자 결정 2026-10-15(G1 — DEC-10 은 G1 에 남고, 닫는 slice 는 「게시 하한율 신뢰 게이트」) · 운영자 결정 2026-10-24
  (ML-01 을 G1 에서 뺌 → G1 8; **개연 밴드 0.30~0.995 채택** — 아래 D-DEC10-1).
- **base**: main **`23837b19`**(PR #93 DEC-05 머지 커밋, 2026-10-28 고정) · worktree `bid-vector-v2-m6-g1dec10` · 브랜치 `m6-g1dec10/2026-10-28`.
  rollback 복원 기준도 `23837b19` 다(전제: 그 SHA 에 이 slice 없음 — 착수 커밋이 그 조상이 아님을 첫 산출물 커밋에서 단언). 게이트 자리
  `BidMenuInput.publishedFloorRate` 는 DEC-05 산출물로 그 SHA 에 있다.
- **입력 정본**:
  - `docs/discovery/capability-map.md` 「DEC-10 · 게시 낙찰하한율 신뢰 게이트」 절의 acceptance 네 줄과 「가장 교훈적인 실패 패턴」
    (「소비자가 X 만 하므로 안전」 형태의 근거를 주석이 아니라 architecture/contract test 로).
  - 추적표 DEC-10 행(계수 없음 · 타입 분리 자리 없음) · regression ledger R-FLOOR-03 · R-FLOOR-04.
  - data dictionary §1.4.3 B6 표(0.30 ~ 0.995, legacy-behavior 근거: 1.00000 라이브 47건 · 0.5 미만 3건).
  - 기존 `decision` 의 `FloorOverrideValidation`(`PlausibilityBand` — 운영자 override 쪽, production 호출자 0).

## 운영자 결정 (2026-10-24)

| ID | 결정 |
| --- | --- |
| **D-DEC10-1** | **개연 밴드 = [0.30, 0.995] 양 끝 포함**을 Kotlin 출하 정책 데이터로 둔다(versioned, 코드 상수 아님). 값의 소유는 **이 slice** 다 — `OPEN-DEC-10` 은 2026-10-15 결정대로 DEC-03(법정 하한율 표 값) 쪽에만 남는다. 「B6 밴드 값 = `OPEN-DEC-10` 소유」로 적은 문면 다섯(CollectionPolicy `rangeBands` 주석 · `reports/evidence/m3/3a/policy-values.md` · `reports/evidence/m6/6g/policy-values.md` · 추적표 COL-07 · DEC-03 행 홀더 칸)을 이 slice 가 정정한다. Python 백테스트(`ml-engine/policy/strategy-backtest-v1.yaml` `floor.rate_band_*`)와 **값 일치 test** 를 둔다(사전 등록 실험이라 Python 쪽 사유 합침은 바꾸지 않는다). |

## 팀장 결정 (설계 — 추천안으로 진행)

| ID | 결정 |
| --- | --- |
| **D-DEC10-2** | **두 타입**: 원값 `FloorRate`(오늘 그대로 — 저장·표시·분석이 쓴다)와 게이트 통과 값(이름 예: `PlausibleFloorRate`). 게이트 통과 값의 생성자는 `internal` + `@ConsistentCopyVisibility` 관례이고 **생성 경로는 게이트 함수 하나**다. 게이트 결과는 sealed 셋: 통과(게이트 값) · 부재(원값 null) · 개연 불가(원값 + 밴드 — 고치지 않는다). 게이트는 `origin == NoticeValue` 인 값에만 건다(`StatutoryTable` 은 DEC-03 해석기 몫 — 오늘 생성 자리 0). |
| **D-DEC10-3** | **가격 경로는 게이트 값만 받는다**: ① DEC-05 `BidMenuInput` 의 게시 하한율 칸 → 게이트 결과. 개연 불가는 부재와 **같은 가지**(`SourceAbsent`)로 떨어지되 사유는 별개(`PublishedFloorRateImplausible` 꼴) — acceptance ①·④. ② DEC-04 `criticalAssessmentRateFor` 의 게시값 입력도 게이트 값. ③ **평가 margin 점수**(`PredictionFacts.expectedMarginFact` → `MarginDerivation.floorHeadroomOf`)도 가격 경로로 본다 — 투찰가를 내지는 않지만 verdict 사다리 점수를 바꾸므로 0.30 미만·0.995 초과 값이 점수에 섞이는 것이 legacy 가 막으려던 실패다. 개연 불가는 「floor 없음」과 같은 값으로 접히고 사유가 남는다. 이것은 **입력 하한율 값의 신뢰** 문제이고, 「예측 비율이 하한 미달일 때 점수를 clamp 하는가」(`OPEN-DEC02-SCORE-AXIS-FLOOR`, g1dec05 등재 — budgetCapture 점수 경로)와는 다른 질문이다. 착수 때 그 OPEN 의 판정 여부를 이 계약에 함께 적되, clamp 도입은 이 slice 범위 밖이다(승인된 명세 없음). |
| **D-DEC10-4** | **분석·표시 경로는 게이트를 걸지 않는다**(legacy 의도 — 미리 접으면 계수기가 0 이 된다): 저장·재구성 · ML 스냅샷 `floor_rate` · 7B `NoticeSnapshot` · 수집 정규화(공고를 버리지 않는다 — `rangeBands`/`violatesExpectedRange` 는 금액 축 항목 drop 기계라 쓰지 않는다). |
| **D-DEC10-5** | **계수 `floor_implausible`**: 결과 값에 사유로 싣고(침묵 스킵을 타입으로 금지) 평가 실행 집계(`EvaluationCommitTally` 꼴, dry-run 대칭)에 사유별 건수로 나온다. **영속 계수(열 추가)는 하지 않는다** — 마이그레이션은 되돌리기 어려운 경로라 이 slice 밖, 필요하면 OPEN. OpenAPI 응답 필드 추가도 이 slice 밖(아래 D-DEC10-7). |
| **D-DEC10-6** | **「소비자가 늘면 깨진다」를 구조로**: ⓐ 게이트 값 생성자 호출자 집합 == {게이트 함수} (바이트코드 등식) ⓑ 가격 경로 패키지(`decision.menu` · DEC-04 커널 · `workflow` evaluation margin)에서 원값 `FloorRate` 를 하한율로 **읽는** 자리의 관측 집합 == 허용 집합(빈 집합 목표) — `DivisionValueRules` 의 「허용 == 관측」 틀(낡은 허용 항목도 실패) ⓒ 컴파일 실패 fixture: 밴드를 거치지 않은 값으로 게이트 값을 만들 수 없다(R-FLOOR-03 검증 방법) ⓓ 양성 대조: 관측 집합 비지 않음 · 음성 fixture 루트. 텍스트 grep 술어 금지. |
| **D-DEC10-7** | **7C② 표시 상태**: 7C② 의 `FloorRateGroundsState` 는 밴드 밖 값을 `USABLE` 로 말한다. 두 slice 중 **나중에 머지되는 쪽**이 개연 불가 표시 상태를 더한다(OpenAPI·생성 타입 포함). **착수 시점(2026-10-28) 7C②(`m7-7c2`)는 리뷰 통과 · main 재병합 중이라 이 slice 보다 먼저 머지될 것이 확실하다 → 이 slice 가 더한다.** 7C② 머지 전에 이 slice 가 그 자리를 만질 일이 생기면 팀장에게 먼저 묻는다(7C② 머지 뒤 main 을 병합해 그 위에서 더한다). 이 slice 가 더하므로 OpenAPI 변경이 in_scope 로 들어오고 `web` job 이 acceptance 에 붙는다. |

## 산출물

1. `decision` 의 게이트(정책 데이터 · 게이트 함수 · 결과 sealed · 게이트 값 타입). 기존 `PlausibilityBand` 를 재사용하되 override 쪽
   거동(`FloorOverrideValidation`)은 바꾸지 않는다.
2. 가격 경로 셋(D-DEC10-3)의 입력 전환과 사유 가지.
3. 계수(D-DEC10-5).
4. 구조 test(D-DEC10-6) · 값 일치 test(D-DEC10-1).
5. 문면 정정 다섯(D-DEC10-1) · 추적표 DEC-10 행 재판정 · capability map DEC-10 절 · `milestone-6.md` 결정 블록에 D-DEC10-1 등재.

## 설계 검토 (Phase 2.5 — 레인이 착수 보고에 채우고 팀장이 수령)

- (0) 위협 모델 경계 문장: 방어 — 개연 불가 게시값이 투찰가·하한 미달 판정·verdict 점수에 닿는 것, 그 제외가 조용히 사라지는 것,
  새 소비자가 원값을 가격 경로로 끌고 오는 것. 방어하지 않음 — 밴드 **안**의 틀린 값(원문 품질), 법정 하한율 해석(DEC-03).
- (1) 열거인가 구성인가 — 소비자 목록 열거가 아니라 타입 + 호출자 등식.
- (2) 우회 ≥5 (최소): 원값을 `copy` 로 게이트 값에 실어 나르기 · 리플렉션/직렬화로 게이트 값 생성 · 가격 경로가 `Notice.floorRate` 를
  직접 읽기 · 게이트 결과의 개연 불가 가지를 `else` 로 통과 처리 · 경계값(0.30·0.995 정확값, 0.2999…·0.9951) · `StatutoryTable` origin
  위장 · margin 경로가 `rate` 를 별 함수로 읽기.
- (2b) 값 획득 축: 게이트 값을 얻는 public 표면 전수, `object` 커널 주입 자리.
- (3) 과잉·미달: 표시 경로에 게이트를 걸면 과잉(계수 0 화), margin 을 빼면 미달.

## in_scope / out_scope

- in: `decision/**`(게이트·가격 커널 입력) · `shared-kernel/**` 의 필요한 자리(게이트 값을 공용으로 둘 때만, 이유를 적는다) ·
  `workflow/**/evaluation/**`(margin 입력 · 집계) · `app/**` 의 평가 집계 줄과 architecture test · `config/quality/**` 의 필요한 키 ·
  `procurement/**` 의 `rangeBands` 주석 문면 · 문면 정정 다섯의 문서 · `docs/discovery/capability-map.md` DEC-10 절 ·
  `reports/evidence/m6/6e1/acceptance-trace.md` DEC-10·COL-07·DEC-03 행과 집계 · `milestone-6.md` 결정 블록 · `reports/evidence/m6/g1dec10/**`.
  D-DEC10-7 이 이 slice 몫이 되면 `openapi/**` · `web/src/api/generated/**` · 7C② 표시 상태 파일.
- out: 마이그레이션 · 수집 정규화 거동 · Python `ml-engine/**`(값 일치 test 는 Kotlin 쪽에서 yaml 을 읽는다) · ML 배선 · DEC-03 해석기.

## acceptance

`ci.yml` 의 `check` job 명령 그대로. OpenAPI 가 in_scope 로 들어오면 `web` job 도. `ml-engine`·`container` 는 입력 변경 0 을 path-limited diff 로 보이면 생략.

## rollback

in_scope 경로 한정 `git restore --source=<base> --staged --worktree --` · 목록은 `git diff --name-status <base>..HEAD` 기계 산출 ·
공유 문서(`acceptance-trace.md` · `capability-map.md` · `milestone-6.md` · `config/quality/*`)는 병합된 main 고정 SHA 에서 복원 ·
임시 clone ①~⑥ 실측 · 실측 HEAD 기록.

## 검토

verifier(opus) + code-reviewer(sonnet) 병렬. Codex 없음 — 되돌리기 어려운 경로가 아니다(마이그레이션 0 · 인증 무편집).

## 빌드 · 측정 규율 (2026-10-28 현행)

- 빌드는 `~/.local/bin/bv-build.sh <cmd>`(호스트 자리 둘 · 획득 순간 메모리 재확인). 금지 창 없음. 도는 빌드는 `pgrep -a java | grep gradle-wrapper.jar` 로 센다.
- 실행 증거는 Gradle task 상태 줄(`FROM-CACHE`·`UP-TO-DATE` 표식 0) — XML mtime 은 증거가 아니다. 변이는 바꿔치기 · 적용 실패 시 비영 종료 · 통제군 `--rerun`(task 이름 뒤).
- 결과 판독은 `*_EXIT=` 전수 · 체인은 안쪽 종료 코드를 그대로 올린다.

## 하네스 레인 변경 (상시)

(없음 — 생기면 여기에 적는다)

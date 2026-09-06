# rollback — M1 / 1D (Provenance first-match · Floor Shortfall)

정본. `scope.md` 의 `rollback` 절이 가리키는 자리(evidence-pack 규격). 경로 한정 복원 +
신규 경로 삭제. **range revert 금지** — in_scope 경로에만 적용한다.

base_sha: `14495d035a55baaa7ee6618e2857d9096804c686`
head_sha(이 문서 정본 시점): `d586220`(evidence 갱신 커밋. `git log -1 --format=%H --
reports/evidence/m1/1d/` 로 확인 — 이 파일 자신의 최종 개정이 있으면 그 값이 갱신된다).
아래 「복원 명령」 목록은 **최초 4개 구현 커밋**(`95585b5`~`bc15983`)이 만든 파일까지만
겨눈다 — 후속 라운드(`af25a38`, ft-002 배선)가 편집한 두 파일은 별도 절 「후속 라운드
변경」이 다룬다(원본 목록에 없던 파일 소유 관계가 바뀌었기 때문).

## 변경 파일 전건 (`git diff --name-status <base>..HEAD`, in_scope 코드·설정 경로만)

`M` = base 에도 있던 파일(내용만 복원), `A` = 이 slice 가 신설(파일째 삭제).

```
M  app/build.gradle.kts
M  app/src/test/kotlin/bidvector/app/conformance/CorpusExecutors.kt
A  app/src/test/kotlin/bidvector/app/conformance/ProvenanceFloorExecutors.kt
M  app/src/test/kotlin/bidvector/app/conformance/SharedKernelCorpusConformanceTest.kt
M  config/quality/gate-tests.properties
M  decision/build.gradle.kts
A  decision/src/main/kotlin/bidvector/decision/FloorShortfallKernel.kt
A  decision/src/main/kotlin/bidvector/decision/FloorShortfallPolicyData.kt
A  decision/src/main/kotlin/bidvector/decision/FloorTypes.kt
A  decision/src/main/kotlin/bidvector/decision/Provenance.kt
A  decision/src/main/kotlin/bidvector/decision/ProvenancePolicyData.kt
A  decision/src/main/kotlin/bidvector/decision/ProvenanceRules.kt
A  decision/src/test/kotlin/bidvector/decision/FloorShortfallKernelTest.kt
A  decision/src/test/kotlin/bidvector/decision/ProvenanceRulesTest.kt
M  shared-kernel/src/main/kotlin/bidvector/sharedkernel/Rate.kt
A  shared-kernel/src/main/kotlin/bidvector/sharedkernel/RateArithmetic.kt
A  shared-kernel/src/test/kotlin/bidvector/sharedkernel/RateArithmeticTest.kt
A  reports/evidence/m1/1d/checklist.md
A  reports/evidence/m1/1d/commands.md
A  reports/evidence/m1/1d/rollback.md
```

`reports/evidence/m1/1d/scope.md`는 base_sha 이후·이 구현 레인 착수 이전에 세션 모델이
이미 커밋했다(CLAUDE.md 「기획 문서 레인」) — 이 rollback 은 코드·설정 산출물만 겨눈다.

## 범위 밖 변경 — 되돌리지 않는다

- `docs/discovery/capability-map.md`·`docs/discovery/data-dictionary.md`·`milestone-1.md`
  — 세션 모델이 착수 전(base_sha 이후 8커밋)에 완료한 「계약 갱신」. 이 slice 편집 아님
  (checklist.md 알려진 제한 ⑧).
- `data-extract.md`·`fixtures/tools/mutation_sweep_targeted.py` — **다른 레인의 혼입**
  (checklist.md 알려진 제한 ⑦). 커밋 `bc15983`에 공유 index 로 섞여 들어갔으나 그 레인
  소유이고 이력을 되쓰지 않는다. 되돌리면 그 레인의 작업을 지운다.
- `fixtures/tools/mutation_sweep_adversarial.py` — **혼합 소유**. `bc15983`의 종료 코드
  편집은 다른 레인 소유(위와 같음). `af25a38`의 `ASSERTED["floor-threshold-002"]` 행
  추가는 **이 slice 소유**지만, 파일 전체를 base 로 되돌리면 다른 레인의 종료 코드
  기능까지 지운다 — 파일 단위 restore 대상에서 뺀다. 이 slice 분만 걷어내려면 아래
  「후속 라운드 변경 — 부분 되돌림」의 수동 절차를 쓴다.
- `fixtures/input/floor-threshold-002.json`·`fixtures/manifest.yaml`
  (`floor-threshold-002` 승격·contract_binding 8건) — **fixture-curator 소유**
  (`32b1b39`·`cec7732`). 기대값 무변경 원칙의 산출물이라 이 slice 가 되돌리지 않는다.
- `CLAUDE.md`·`.claude/**` — 하네스 레인 변경 **없음**(scope.md 「하네스 레인 변경」 절,
  `git log --oneline 14495d0..HEAD -- CLAUDE.md .claude/` 결과 없음).

## 복원 명령 (경로 개별 인자 — 변수 확장 금지)

```bash
BASE=14495d035a55baaa7ee6618e2857d9096804c686

git restore --source="$BASE" --staged --worktree -- \
  app/build.gradle.kts \
  app/src/test/kotlin/bidvector/app/conformance/CorpusExecutors.kt \
  app/src/test/kotlin/bidvector/app/conformance/SharedKernelCorpusConformanceTest.kt \
  config/quality/gate-tests.properties \
  decision/build.gradle.kts \
  shared-kernel/src/main/kotlin/bidvector/sharedkernel/Rate.kt

git rm -f \
  app/src/test/kotlin/bidvector/app/conformance/ProvenanceFloorExecutors.kt \
  decision/src/main/kotlin/bidvector/decision/FloorShortfallKernel.kt \
  decision/src/main/kotlin/bidvector/decision/FloorShortfallPolicyData.kt \
  decision/src/main/kotlin/bidvector/decision/FloorTypes.kt \
  decision/src/main/kotlin/bidvector/decision/Provenance.kt \
  decision/src/main/kotlin/bidvector/decision/ProvenancePolicyData.kt \
  decision/src/main/kotlin/bidvector/decision/ProvenanceRules.kt \
  decision/src/test/kotlin/bidvector/decision/FloorShortfallKernelTest.kt \
  decision/src/test/kotlin/bidvector/decision/ProvenanceRulesTest.kt \
  shared-kernel/src/main/kotlin/bidvector/sharedkernel/RateArithmetic.kt \
  shared-kernel/src/test/kotlin/bidvector/sharedkernel/RateArithmeticTest.kt \
  reports/evidence/m1/1d/checklist.md \
  reports/evidence/m1/1d/commands.md \
  reports/evidence/m1/1d/rollback.md
```

복원 뒤 `decision` 모듈은 자리표시자로 돌아간다(base 의 `build.gradle.kts`만 —
`ModuleBoundaryAnchor.kt` 는 base 에도 있던 파일이라 위 목록에 없다, 그대로 남는다).
`shared-kernel`의 `Rate.kt`는 D-1(a) 이전 형태(`fraction` internal, `BidRate`/
`AssessmentRate` factory 없음)로 되돌아간다 — 1B 회귀 test(`RateTest.kt`)는 그대로
base 상태이므로 영향 없다.

## 후속 라운드 변경 — 부분 되돌림 (`af25a38`, ft-002 배선)

후속 라운드가 편집한 두 파일 중 `app/src/test/kotlin/bidvector/app/conformance/
ProvenanceFloorExecutors.kt`는 위 「복원 명령」의 `git rm -f` 목록에 이미 있어(파일
전체가 이 slice 소유) 별도 처리가 필요 없다 — 파일째 삭제되므로 그 안의 최초 판·후속
판 구분이 무의미하다.

`fixtures/tools/mutation_sweep_adversarial.py`는 혼합 소유라 파일 단위로 되돌릴 수
없다(위 「범위 밖 변경」). 이 slice 분(`ASSERTED["floor-threshold-002"]` 행)만 걷어
내려면:

```bash
# ASSERTED 딕셔너리에서 floor-threshold-002 행 + 그 위 주석 3줄을 삭제
sed -i '' '/curator 발견(2026-09-06)/,/\[.\$\.sampleIsShortfall.*criticalAssessmentRate\.fraction.\]/d' \
  fixtures/tools/mutation_sweep_adversarial.py
python3 fixtures/tools/mutation_sweep_adversarial.py   # exit 0 유지 확인(44건으로 복귀)
```

이 되돌림은 `floor-threshold-002`가 dispatch 표(`ProvenanceFloorExecutors.kt`)에서도
함께 빠졌을 때만 의미가 있다 — dispatch 는 남기고 스윕 행만 빼면 승격 case 에 변이체가
다시 생성되지 않는 공백이 재발한다(curator 가 발견한 원래 결함으로 복귀).

## 임시 clone 실측 (head `c501183`, 최초 4개 구현 커밋 대상 — 후속 라운드 이전)

```
git clone --no-hardlinks /Users/harris/Development/private/bid-vector-v2 /tmp/1d-rollback-check
cd /tmp/1d-rollback-check
<위 두 명령 실행>
git status --short
```

실측 결과: `restore exit: 0` · `rm exit: 0`. `git status --short` 는 정확히 위 목록대로
`M` 6개(`app/build.gradle.kts`·`CorpusExecutors.kt`·`SharedKernelCorpusConformanceTest.kt`·
`config/quality/gate-tests.properties`·`decision/build.gradle.kts`·`Rate.kt`) +
`D` 12개(신규 파일 전건)만 나왔다 — `docs/discovery/*.md`·`milestone-1.md`·
`data-extract.md`·`fixtures/tools/*.py`는 목록에 없어 그대로 HEAD 상태(무변경)임을
확인했다. clone 은 검증 뒤 `rm -rf` 로 삭제, 원본 저장소 무변경.

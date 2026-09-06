# commands — M1 / 1D (Provenance first-match · Floor Shortfall)

명령과 종료 코드만 남긴다(evidence-pack 규격 — 출력 전문 금지, 핵심 결과 한 줄).

## acceptance_commands (scope.md 대응)

- **P-0** `git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check)`
  — exit 0. 312 tasks 전건 executed(캐시 없는 격리 clone).
- **P-1** `./gradlew --no-build-cache clean check` — exit 0. 최초 312 actionable tasks(295 executed).
  후속 커밋(`af25a38`, ft-002 배선) 뒤 재실행 — exit 0, 303 actionable tasks(287 executed).
- **P-2** `./gradlew :decision:test` — exit 0. `ProvenanceRulesTest`·`FloorShortfallKernelTest` 전건 통과.
- **P-3** `./gradlew :shared-kernel:test` — exit 0. 1B 회귀 전건 + `RateArithmeticTest` 신규 통과.
- **P-4** `./gradlew :decision:domainApiTypeGate :decision:domainSourceReferenceGate :decision:typeShapeGate :decision:sizeGate :decision:cpdCheck` — exit 0.
- **P-5** `./gradlew :app:test --tests '*Conformance*'` — exit 0. 최초
  `TEST-...SharedKernelCorpusConformanceTest.xml`: `tests="28" failures="0" errors="0"`
  (authoritative 7 case). 후속(curator ft-002 승격 + dispatch 등재) 재실행 —
  `tests="29" failures="0" errors="0"`(authoritative **8** case, dispatch 완전성 test 포함).
- **P-6** `./gradlew qualityBaseline` — exit 0.
- **P-7** 최초는 조건부 생략(manifest 무편집). curator 가 `fixtures/manifest.yaml`을
  편집(`32b1b39`·`cec7732`)한 뒤 조건이 성립해 재실행 —
  `python3 fixtures/tools/mutation_sweep_adversarial.py`: exit 0, 캐치된 변이 44→47(+3,
  `floor-threshold-002` ASSERTED 행 신설분). `python3
  fixtures/tools/mutation_sweep_targeted.py`: exit 0(기존 EXPECTED_PASSES 둘만 통과,
  회귀 없음). `python3 fixtures/tools/manifest_contract.py`: exit 0(술어 self-check 22건).
  `python3 fixtures/tools/check_legacy_numbers.py`: exit 0(hits 0).
- **P-8** `./gradlew :build-logic:test` — exit 0(최초는 `qualityBaseline` 묶음 실행에 포함,
  후속 재검증에서 단독 실행으로도 exit 0).

## 모듈별 게이트(개별 확인)

- `./gradlew :app:check` — exit 0.
- `./gradlew :decision:check` — exit 0.
- `./gradlew :shared-kernel:check` — exit 0.
- `./gradlew :decision:gateExecutionGate :app:gateExecutionGate` — exit 0(`gate.tests.decision` 신설분 포함).

## rollback 임시 clone 실측

`rollback.md`의 두 명령(`git restore --source=<base> ...`·`git rm -f ...`)을 head
`c501183`에서 clone한 `/tmp/1d-rollback-check`에서 실제로 실행 — 둘 다 exit 0,
`git status --short`가 rollback.md 목록과 정확히 일치(M 6·D 12). clone은 검증 뒤 삭제.

## secret 스캔

`git diff 95585b5^..bc15983 | grep -niE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))"`
— 매치 3건, 전부 `provenanceRuleIdFromToken`(함수명) 부분 문자열의 오탐. 실제 비밀값 없음(육안 확인).

## 변이 실측(5건, 위협 모델 방어 재현 — 전부 실제로 편집·실행·원복했다)

각 항목은 `decision` main 소스를 직접 편집해 `:decision:test`(항목 4는 `:app:test
--tests '*Conformance*'` 도 동반)를 돌려 RED 를 확인한 뒤 `git checkout --` 로
원복하고 다시 GREEN 을 확인했다.

1. **rule order 하드코딩** — `ProvenanceRules.firstMatchedRule` 이 `policy.value.ruleOrder`
   대신 고정 리스트 `listOf(CleanInteger, SuspectRatio, DerivedVat)` 를 쓰도록 바꾸자
   `ProvenanceRulesTest.② 같은 매치 집합이라도 정책 순서가 다르면 다른 라벨을 낸다` 가
   실패(`expected:<SuspectRatio> but was:<CleanInteger>`). 13 tests 중 1 failed. 원복 후 재통과.
2. **`Unmeasurable→Measured(0/1)` 접기** — `measureFloorShortfall` 의 `qualifiedDenominator
   < minSamples` 분기를 `Unmeasurable` 대신 `Measured(Frequency(0,1), ...)` 로 바꾸자
   `⑥ 149 표본은 Unmeasurable(SampleInsufficient) 이다`·`fs-005 전이` 둘 다 실패(14 tests 중
   2 failed). 원복 후 재통과.
3. **밴드 밖 표본 분모 잔류** — `ShortfallTally.qualifiedDenominator` 를 `rawCount -
   outsideBand` 대신 `rawCount` 로 바꾸자 위협 모델 (f) property·`fs-005 전이`·`⑥` 경계값
   test 셋이 실패(qualifiedDenominator 가 149 대신 152 로 나와 문턱을 안 넘음). 14 tests 중
   3 failed. 원복 후 재통과.
4. **경계 등가 뒤집기** — `isShortfall` 의 `StrictlyGreater` 분기를 `realized.rate >=
   critical.rate` 로 바꾸자 `④ 경계 등가는 미달이 아니다 — strictly-greater` 가 실패
   (`expected:<false> but was:<true>`). **최초 실측(ft-002 배선 전)**: corpus
   (`floor-threshold-001`·`003`)는 이 변이를 못 잡았다 — realized(1.0499·1.0501)가
   critical(1.05)과 정확히 같지 않아 경계 등가 자체를 겨누지 않는다(28 corpus tests 는
   그대로 통과). **후속 재실측(`af25a38` 뒤, curator ft-002 승격 반영)**: 같은 변이를
   다시 넣자 이번엔 `floor-threshold-002`(realized=critical=1.05) corpus test 도 함께
   실패했다(`decision:test` 1 failed + `app:test` 1 failed, 총 2건) — 경계 등가 방어가
   이제 corpus·단위 test 이중 방어가 됐다. 원복 후 재통과(둘 다 재확인).
5. **규칙 무매치를 Clean 으로 접기** — `classificationFor(null)` 을
   `BaseAmountProvenance.Unknown` 대신 `Clean` 으로 바꾸자 `매치 없음은 Unknown 이다 — D-2,
   신뢰로 접히지 않는다` 가 실패(`expected:<Unknown> but was:<Clean>`). 13 tests 중 1 failed.
   원복 후 재통과.

## 정책 version·fixture 근거

- 이 슬라이스가 새 fixture 를 만들지 않았다 — 기존 authoritative 7 case
  (`base-amount-provenance-001~003`·`floor-threshold-001`·`003`·`floor-shortfall-001`·
  `005`) 그대로 재사용했다. 후속 라운드에서 curator 가 `floor-threshold-002`를
  decision 28(경계 등가 정책값)로 승격해 8번째 authoritative case 가 됐다(`32b1b39`) —
  fixture 편집은 curator 소관, 이 슬라이스는 dispatch 등재와 runner 배선만 했다.
- 정책값(허용 오차·경계·최소 표본)은 main 에 없다 — `decision` 모듈의 정책 데이터 타입은
  형태·불변식만 갖고, 값은 `ProvenanceRulesTest`/`FloorShortfallKernelTest` 의 test 정책
  인스턴스와 `ProvenanceFloorExecutors.kt` 의 runner 가 fixture JSON 에서 직접 읽어
  구성한다(§12.1 `legacy-behavior` 좌표는 각 파일 KDoc 주석에 있다).

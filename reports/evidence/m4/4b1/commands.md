# commands.md — M4/4B-1

명령과 종료 코드만 남긴다(출력 전문 금지, evidence-pack 스킬 규격). 감사자는 명령을 다시 돌린다.

## 2026-09-09T09:20Z — RED 확인
- cmd: `./gradlew --no-daemon :decision:compileTestKotlin`(`VerdictLadderTest` 등 test 4개를
  먼저 쓰고, main 타입이 아직 없는 상태로 실행)
- exit: 1 — `Unresolved reference` 다수(`VerdictLadder`·`Verdict`·`BidNowReason` 등). RED 확정.

## 2026-09-09T09:30Z — main 타입 구현 뒤 컴파일·test — 설계 결함 자체 발견
- cmd: `./gradlew --no-daemon :decision:compileKotlin :decision:compileTestKotlin`
- exit: 0
- cmd: `./gradlew --no-daemon :decision:test`
- exit: 1(초기) — `VerdictLadderTest`의 「③ review 밴드」·「④ 기본」 두 case 가 실패.
  원인은 test 설계 결함이지 커널 결함이 아니었다: force-bid 는 priority 와 무관하다
  (조사 §5.1 "regardless of the priority score")는 사실을 test 가 반영하지 않고
  probability·matched 를 `null`(결측)로 둔 채 「priority 만으로 review/skip 이 확정된다」고
  가정했다. 커널은 옳게 `Review(MlUnavailable)`을 냈다(force-bid 가능성을 배제하지
  못한 상태에서 review/skip 을 단정하지 않는다) — **이것이 정확히 설계 검토 (4) 7 이
  요구한 「부재에서 BidNow 로 가는 경로가 없다」의 대칭 성질**(부재에서 확정적 review/skip
  으로도 가지 않는다)이다. test 를 probability=matched=0.1(확정적으로 force-bid 거짓)로
  고쳤다 — commands.md 에 그대로 남긴다(진짜 RED 였고 원인 규명 뒤 test 를 고쳤다는
  사실 자체가 이 slice 핵심 요구의 실측 증거다).
- 수정 뒤: exit 0(62 tests, 0 failed).

## 2026-09-09T09:45Z — clean check 1차(ktlint·detekt)
- cmd: `./gradlew --no-build-cache clean check`
- exit: 1 — ① `VerdictExecutors.kt:77` MaxLineLength(ktlint) ② `decision:detekt` —
  `VerdictLadder.judge` 6 return(한도 2, `ReturnCount`).
- 수정: ① `:decision:ktlintFormat`·`:app:ktlintFormat` ② `judge`를 4A `apply()` 관례대로
  guard 함수 체인(`capacityHoldOutcome`·`priorityBidNowOutcome`·`forceBidOutcome`·
  `reviewBandOutcome`, `?:` 연쇄)으로 재구성 — return 2개(초기 priority 가드 + 체인)로.

## 2026-09-09T09:52Z — clean check 2차(전건)
- cmd: `./gradlew --no-daemon :decision:compileKotlin :decision:compileTestKotlin :decision:test :decision:ktlintCheck :decision:detekt :decision:cpdCheck`
- exit: 0
- cmd: `./gradlew --no-build-cache clean check`(S-1)
- exit: 0 — `BUILD SUCCESSFUL in 32s`, 344 actionable tasks(320 executed·24 up-to-date).

## 2026-09-09T09:58Z — S-2~S-6 개별 재확인
- cmd: `./gradlew --no-daemon :decision:test`(S-2) — exit: 0(62 tests, 0 failed)
- cmd: `./gradlew --no-daemon :decision:domainApiTypeGate :decision:domainSourceReferenceGate :decision:moduleDependencyGate :decision:sizeGate :decision:cpdCheck`(S-3) — exit: 0
- cmd: `./gradlew --no-daemon :app:test --tests '*Conformance*'`(S-4) — exit: 0 — 82 tests
  (기존 74 + 신설 verdict-005~012 여덟). `verdict-005`~`012` 전부 dynamicTest 로 실제 실행됨을
  `app/build/test-results/test/*.xml`에서 `name="verdict-0XX"` 8건으로 실측 확인.
- cmd: `./gradlew --no-daemon qualityBaseline`(S-5) — exit: 0
- cmd: `./gradlew --no-daemon :app:gateExecutionGate`(S-6) — exit: 0

## 2026-09-09T10:05Z — 승인 문서 편집(§3.6·§13.2·capability-map OPEN-DIC-03) 뒤 재확인
- cmd: `./gradlew --no-daemon :decision:test :app:test --tests '*Conformance*'`
- exit: 0(문서만 바뀌었으므로 무영향 확인)

## 2026-09-09T10:10Z — 역방향 파급 grep
- cmd: `grep -rn "data-dictionary\.md:[0-9]\+\|data-dictionary:[0-9]\+" --include="*.md" --include="*.kt" --include="*.properties" .`
- exit: 0 — 매치 4건(`m1/1b/scope.md:274`·`m1/1e/scope.md:72`·`m1/1c/checklist.md:148`·
  `m0/0c/commands.md:223`). §3.6 삽입 지점(1057행 부근, +13줄)보다 **위** 줄번호(960·980)
  둘은 영향 없음(삽입은 그 아래만 민다). **아래** 줄번호(1551·1554) 하나(`m0/0c/commands.md:223`)
  는 영향권이나, 4B-1 in_scope 밖의 닫힌 slice evidence이고 이미 `m1/1c/checklist.md:148`이
  「닫힌 slice evidence 의 좌표가 낡는다」는 사실 자체를 일반 원칙으로 선언해 두었다 —
  4B-1이 새로 만드는 낡음이 아니라 그 원칙이 이미 예견한 사례. 알려진 제한에 등재만 한다
  (checklist.md).
- cmd: `grep -rn "capability-map\.md:[0-9]\+\|capability-map:[0-9]\+" --include="*.md" --include="*.kt" --include="*.properties" .`
- exit: 0 — 매치 다수, 전부 `OPEN-DIC-03` 편집 지점(3360행 부근)보다 **위** 줄번호 —
  이 편집은 한 행을 두 행으로 나눈 net **+1**줄이라 그 지점 **아래**를 가리키는 참조가
  있었다면 영향권이었겠지만, grep 결과 전부 위쪽(144~3171행대)을 가리켜 무관함을 확인.

## 2026-09-09T10:20Z — 커밋(구현+corpus+게이트+승인문서, head 예정)
- cmd: `git add <in_scope 경로 개별 인자> && git commit -m ... -- <같은 경로>`
- (커밋 SHA는 아래 항목에 채운다)

## (커밋 뒤) — S-0(임시 clone, 구현 커밋에 pin)
- 실행 예정 — 결과를 이 절에 append한다.

## (커밋 뒤) — 값 획득 축 실측(설계 검토 (2))
- 실행 예정 — `Verdict`·reason 하위 타입 생성자 위조 거부(internal constructor)를 임시
  clone 컴파일 거부로 실측하고, **`VerdictLadder.judge`·`FloorOverrideValidation.validate`
  는 4A 와 달리 public 이 옳다는 것**(1C·1D 관례 — `app` conformance 실행자가 이미 정상
  호출 중임을 S-4 82 tests 로 실측)을 함께 적는다.

## (커밋 뒤) — rollback 5단계 실측
- 실행 예정.

## (커밋 뒤) — secret 스캔
- 실행 예정.

## (커밋 뒤) — clean-tree 게이트(양성 대조, `git checkout --` 미사용)
- 실행 예정.

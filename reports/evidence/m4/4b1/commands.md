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

## 2026-09-09T10:20Z — 커밋(구현+corpus+게이트+승인문서, head `381eaeb`)
- cmd: `git add <in_scope 경로 개별 인자> && git commit -m ... -- <같은 경로>`
- exit: 0 — 40 files changed(1959 insertions·10 deletions). 경로를 커밋 명령에 명시해
  인덱스 다른 항목 혼입을 막았다.

## 2026-09-09T10:30Z — S-0(임시 clone, `381eaeb`에 pin)
- cmd: `git clone --quiet --no-hardlinks <repo> "$d/repo" && (cd "$d/repo" && git checkout
  --quiet 381eaeb) && (cd "$d/repo" && ./gradlew --no-build-cache clean check)` — 다음
  slice(4B-2 등)가 착수되면 브랜치 tip 이 이 slice 이후로 움직일 수 있어(4C-1 선례)
  `381eaeb`로 명시 pin.
- exit: 0 — `BUILD SUCCESSFUL in 48s`, 353 actionable tasks 전부 executed(캐시 없는 임시
  clone, `381eaeb` 고정).

## 2026-09-09T10:35Z — 값 획득 축 실측(설계 검토 (2)) — 위조 다섯 형태 전부 거부, 정상 사용은 4A 와 다르게 public 이 옳다
- **1차 시도가 방법론 오류였다 — 기록으로 남긴다.** probe 를 `decision` 모듈 자신의
  test 소스셋(`decision/src/test/.../verifyprobe/`)에 심었더니 `Verdict.Skip(...)` 직접
  생성자 호출이 **컴파일 성공**했다 — `internal`은 모듈 범위이지 패키지 범위가 아니므로
  같은 모듈 test 소스셋에서는 애초에 막히지 않는다(4A M3 교훈과 같은 함정, 이번엔 직접
  걸렸다). probe 를 **다른 모듈**(`app`)로 옮겨 재실행.
- cmd: 임시 clone(`381eaeb`)의 `app/src/test/kotlin/bidvector/app/verifyprobe/`에
  `Verdict.Skip(...)`·`Verdict.BidNow(emptyList())`·`Verdict.Review(emptyList())`·
  `BidNowReason.ForceBidOverride(...)`·`ReviewReason.MlUnavailable(...)` 직접 생성자
  호출 다섯 형태를 심고 `./gradlew --no-daemon :app:compileTestKotlin`
- exit: 1(다섯 형태 전부) — 핵심 결과: `Cannot access 'constructor(reason: SkipReason):
  Verdict.Skip': it is internal in 'bidvector.decision.Verdict.Skip'` ·
  `Verdict.BidNow`·`Verdict.Review`도 각각 같은 형태로 거부 · `BidNowReason.ForceBidOverride`·
  `ReviewReason.MlUnavailable`도 `it is internal in '...'`로 거부 — reason 하위 타입까지
  전부 닫힘을 확인(설계 검토가 「reason 하위 타입도 internal」로 요구한 것과 일치).
- 양성 대조: probe 디렉터리 삭제 뒤 `./gradlew --no-daemon :app:compileTestKotlin
  :decision:test :app:test --tests '*Conformance*'` — exit 0(`BUILD SUCCESSFUL`, 정상
  배선·corpus 82 tests 무영향). probe 는 커밋하지 않았다(임시 clone 전용).
- **`VerdictLadder.judge`·`FloorOverrideValidation.validate`는 public이 옳다는 것**은
  별도 거부 실측이 아니라 **정상 배선 자체가 증거**다 — `VerdictExecutors.kt`(app 모듈)가
  이미 그 두 함수를 직접 호출해 S-4 82 tests 를 통과하고 있다(설계 검토 (2)의 판단대로
  「판정을 받는 것 자체는 권한이 아니다」).

## 2026-09-09T10:45Z — rollback 5단계 실측(같은 clone, `381eaeb`)
- cmd: `git restore --source=13cf0f63da18e13f0e2befd519710a8635742004 --staged --worktree
  -- <in_scope 경로 19개 개별 인자, 4C-1 head 를 base 로>`(rollback.md 정본 명령)
- exit: 0 — ① `git status --porcelain` **D 35 · M 6**(rollback.md 목록과 일치 — 신규
  파일 35건 삭제 대상, 공유 파일 6건 줄 단위 복원 대상) ② 공유 파일 4종 확인:
  `gate-tests.properties`의 `gate.tests.decision`이 4C-1 이전 두 줄(`FloorShortfallKernelTest`·
  `ProvenanceRulesTest`)로 복귀·`CorpusExecutors.kt`에 `VERDICT_EXECUTORS` 매치 0건·
  `fixtures/manifest.yaml`의 `id: verdict-0` 매치 4건(001~004만, 005~012 사라짐) ③ 신규
  디렉터리 잔여 파일 0건 ④ `./gradlew --no-daemon :decision:compileKotlin
  :app:compileTestKotlin` exit 0(`BUILD SUCCESSFUL`, 29 actionable tasks) ⑤ `./gradlew
  --no-daemon :decision:test :app:test --tests '*Conformance*'` exit 0(`BUILD
  SUCCESSFUL`). 다섯 확인 전부 통과.
- `reports/evidence/m4/4b1/scope.md`도 D 목록에 포함됐다 — 4B-1 착수 계약이 base(4C-1
  head `13cf0f6`) **이후**에 팀장이 별도 커밋(`52d7436`/`21f9a4e`류와 같은 패턴,
  4b1은 `4e07682`/`e2bfb99`)으로 추가한 파일이라 이 base 로 되돌리면 함께 사라진다 —
  4C-1 rollback.md 의 `reports/evidence/m4/4c1/scope.md` 처리와 같은 구조, 새로운 문제가
  아니다.
- clone 삭제(`rm -rf`), 되돌리지 않은 원 worktree에는 영향 없음(별도 clone).

## 2026-09-09T10:50Z — secret 스캔
- cmd: `grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" reports/evidence/m4/4b1/ --exclude=commands.md --exclude=checklist.md`
- exit: 1(매치 없음). Telegram id·사업자 정보 없음(육안 확인 — corpus 는 합성 점수 값만
  다룬다, 실 운영 데이터 없음).

## 2026-09-09T10:52Z — clean-tree 게이트(경로 개별 인자, 양성 대조 포함, `git checkout --` 미사용)
- cmd: `git status --porcelain -- <in_scope 경로 19개 개별 인자>`
- exit: 0, 출력 없음.
- 양성 대조: `decision/src/main/kotlin/bidvector/decision/LadderInput.kt`(이 시점 클린)에
  개행 한 줄 추가 → `M` 관측(exit 0, 비어있지 않음) → `head -n`으로 추가한 줄만 절삭(4A
  사고 이후 채택한 안전한 방식 — `git checkout --` 미사용) → 재확인(비어있음, exit 0).

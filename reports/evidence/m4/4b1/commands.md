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
- exit: 1 — ① `VerdictExecutors.kt`의 `bidNowReasonProjection`(`PriorityAboveBidNowThreshold`
  분기) MaxLineLength(ktlint, verifier B-4 정정 — 이 slice 가 만든 파일의 줄 번호는
  낡는 좌표라 절 제목·인용문으로 가리킨다) ② `decision:detekt` — `VerdictLadder.judge`
  6 return(한도 2, `ReturnCount`).
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

## 2026-09-09T11:00Z — 수정 라운드 1(corpus 분류·장부층, 코드 무변경) — manifest 편집 뒤 전건 재확인
- `verdict-005`~`012`의 `verified_paths`를 승인된 어휘까지만 잠그도록 좁혔다(§3a).
  `verdict-001~004` 승격은 시도했으나 dispatch 완전성 test와 충돌해 되돌렸다(§3a·
  checklist.md 알려진 제한 7·10).
- cmd: `./gradlew --no-build-cache clean check`
- exit: 0 — `BUILD SUCCESSFUL in 33s`, 344 actionable tasks(320 executed·24 up-to-date).
- cmd: `./gradlew --no-daemon :app:test`(필터 없이 전건 — 필터 실행 뒤 `gateExecutionGate`가
  거짓 실패를 내는 함정을 피한다, 4C-1 라운드에서 발견한 그 이유)
- exit: 0 — `SharedKernelCorpusConformanceTest`: `tests="82" failures="0" errors="0"`
  (`app/build/test-results/test/TEST-bidvector.app.conformance.SharedKernelCorpusConformanceTest.xml`
  실측). **conformance 82 유지 확인.**
- cmd: `./gradlew --no-daemon :app:gateExecutionGate :decision:test
  :decision:domainApiTypeGate :decision:domainSourceReferenceGate
  :decision:moduleDependencyGate :decision:sizeGate :decision:cpdCheck qualityBaseline`
- exit: 0(전부 UP-TO-DATE — 위 두 명령이 이미 관련 산출물을 최신화).

## 2026-09-09T11:05Z — secret 스캔 재실행(수정 라운드 반영 파일)
- cmd: `grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))"
  reports/evidence/m4/4b1/ fixtures/manifest.yaml --exclude=commands.md --exclude=checklist.md`
- exit: 0(매치 6건, 전부 `fixtures/manifest.yaml`의 `token`/`token_alignment` — M1 계약
  결속 도메인 어휘, 이 slice가 만든 줄이 아니다·자격증명 아님, 육안 확인). 실 비밀값
  패턴(`api_key`·`secret`·`password`·`Bearer `·PEM 헤더) 매치 0건.

## 2026-09-09T11:10Z — 커밋(수정 라운드 1, head `7133ccc`) — 경로 명시
- cmd: `git add fixtures/manifest.yaml reports/evidence/m4/4b1/{checklist,commands,rollback}.md
  && git commit ... -- <같은 4경로>`
- exit: 0 — 4 files changed(167 insertions·31 deletions). `decision/**`·4C-1 경로 혼입 없음
  (`git status --porcelain -- fixtures/manifest.yaml reports/evidence/m4/4b1/*.md` 커밋
  직전 실측이 이 4개와 정확히 일치).

## 2026-09-09T11:12Z — S-0(임시 clone, `7133ccc`에 pin)
- cmd: `d=$(mktemp -d) && git clone --quiet --no-hardlinks . "$d/repo" && (cd "$d/repo" &&
  git checkout --quiet 7133ccc && ./gradlew --no-build-cache clean check)`
- exit: 0 — `BUILD SUCCESSFUL in 52s`, 353 actionable tasks 전부 executed(캐시 없는
  임시 clone).

## 2026-09-09T11:20Z — M-1 재실측: 공유 파일 line-level(`git apply -R`) · 신규 파일 전체
  삭제 (verifier r1 M-1 지정 절차, `381eaeb~1..381eaeb`가 4C-1 몫과 겹치는 파일에 한해
  커밋 단위로 hunk 를 격리)
- 배경(실측): `git log --oneline 13cf0f6..HEAD -- <공유파일>` 로 확인 — `gate-tests.properties`
  는 4C-1 rework(`7469bce`)와 4B-1(`381eaeb`) 둘 다 이 range 안에서 만졌고,
  `fixtures/manifest.yaml`은 4B-1 두 커밋(`381eaeb`·`7133ccc`)만, 나머지 4개
  (`CorpusExecutors.kt`·`SharedKernelCorpusConformanceTest.kt`·`data-dictionary.md`·
  `capability-map.md`)는 이 range 에서 4B-1(`381eaeb`) 만 만졌다 — 이전 판(10:45Z)의
  「19경로 전체를 base 로 blanket restore」는 `gate-tests.properties`에서 4C-1의
  `7469bce` 몫(`OutboxEntryTest`)까지 지웠을 것이므로 verifier M-1 이 지정한 대로
  절차를 바꿨다.
- cmd(같은 clone, `7133ccc`): `gate-tests.properties`는 `git diff 381eaeb~1..381eaeb --
  config/quality/gate-tests.properties | git apply -R`(4B-1 커밋 몫만 격리) — exit 0.
  나머지 5개 공유 파일은 `git diff 13cf0f6..HEAD -- <파일> | git apply -R`(이 range 에서
  4B-1 만 만졌으므로 안전) — exit 0(5건 전부).
- cmd: `git restore --source=13cf0f6 --staged --worktree -- <신규 파일 35개 개별 인자>`
  (decision/** 12·VerdictExecutors.kt 1·fixtures/{input,expected}/verdict-0[05-12] 16·
  reports/evidence/m4/4b1/** 6) — exit 0.
- `git status --porcelain`: **D 35 · M 6**(rollback.md 목록과 일치).
- ① `gate-tests.properties`: `gate.tests.decision`이 4C-1 이전 두 줄(`FloorShortfallKernelTest`·
  `ProvenanceRulesTest`)로 복귀 / `gate.tests.workflow`는 `OutboxEntryTest` 포함 4C-1 rework
  몫 **그대로 유지**(4C-1 몫이 살아있음을 실측 확인, blanket restore 였다면 지워졌을 것).
- ② `fixtures/manifest.yaml`: `grep -n "id: verdict-0" fixtures/manifest.yaml` → `verdict-001~004`
  4건만(005~012 사라짐).
- ③④ `CorpusExecutors.kt`에 `Verdict`/`VERDICT_EXECUTORS` 매치 0건, `SharedKernelCorpusConformanceTest.kt`의
  `TARGET_DOMAINS`에 `"verdict"` 부재(실측 grep).
- ⑤ 신규 디렉터리 잔여 파일 0건(`decision/src/main`엔 1D 소스 7개, `decision/src/test`엔
  1D test 2개만 남음. `reports/evidence/m4/4b1/` 디렉터리 자체가 없어짐. `fixtures`엔
  `verdict-00{1,2,3,4}` 만 남음).
- cmd: `./gradlew --no-daemon :decision:compileKotlin :app:compileTestKotlin` — exit 0.
- cmd: `./gradlew --no-daemon :decision:test :workflow:test :app:test --tests '*Conformance*'`
  — exit 0. `decision:test` = 38 tests(`ProvenanceRulesTest` 21 + `FloorShortfallKernelTest`
  17, 4B-1 이전 1D 그대로) · conformance = **74 tests**(4B-1 이전 기준, 82-8 일치). 다섯
  확인 전부 통과.
- clone 삭제(`rm -rf`), 원 worktree에는 영향 없음(별도 clone).

## 2026-09-09T11:30Z — `verdict-001~004` 승격(운영자 결정 2026-09-09 선택지 (a)) — 입력 재구성 + dispatch 등록
- 배경: 이전 라운드(§3a, 앞선 commands.md 항목)에서 `classification`·`source.kind`·
  `verified_paths`만 편집하고 입력을 그대로 둔 1차 시도가 **86 tests, 5 failed**를
  실측했다(`verdict-001`~`004` dispatch 실패 4 + 완전성 test 1) — 그 실측이 이번
  라운드의 입력 재구성을 요구한 근거다.

## 2026-09-09T11:35Z — 입력 재구성 + dispatch 등록 + 재실행
- `fixtures/input/verdict-00{1,2,3,4}.json`·`fixtures/expected/verdict-00{1,2,3,4}.json`
  을 이 slice 커널의 입력 계약(`$.input`/`$.policy`, `$.override`/`$.band`)으로 재구성
  (checklist.md §3b — 각 case 가 단언하는 규칙·기대 결과의 의미는 불변임을 아래 실행이
  확인). `sha256`(001: `739da467a80a2bb8d982c6907e3a57271e4aace2062922d002334322f13f4062`
  · 002: `f638ae7cf372541e4fcc1dbf676bb7dcf33a9c2ebdeeb677192c60d0778905b0` · 003:
  `6b6ba9d3a0c43c270988387212e479b1cc3bfafae9ed4452a0528a09c01a7e00` · 004:
  `73089bb29ade6006eb68e8cee838de482947c4fc3859535a9012eaf6b60481bd`)·`expected_sha256`
  (001: `a8d27d43f55468b9d32877422f8c36a6e8bfde38a7a6db1aed2ed35728e8bcf5` · 002:
  `b4d73881a02835122984868cabdace0ba481b6c4b550a92c9cd68b478744dd0e` · 003:
  `5ca813c3f2a57f76ea6377f99227c67c64986a33b1def56b5bfd853ec22b5da5` · 004: 값 우연히
  동일 `85977abb95f0c1842e742e18472345b42b33bd0eeb828fa22ed41efba5c3faa9`, 밴드·거부
  사유가 원본과 같아서다) manifest.yaml 에 갱신하고 `change_history` 항목 추가.
  `VerdictExecutors.kt`의 `VERDICT_EXECUTORS`에 `verdict-001`→`verdictLadderExecutor`·
  `002`→`verdictLadderExecutor`·`003`→`verdictLadderExecutor`·`004`→`floorOverrideExecutor`
  등록.
- cmd: `./gradlew --no-daemon :app:compileTestKotlin` — exit 0.
- cmd: `./gradlew --no-daemon :app:test --tests '*Conformance*'` — exit 0 —
  `SharedKernelCorpusConformanceTest`: `tests="86" failures="0" errors="0"`. `grep -o
  'name="verdict-0[0-9][0-9]"' app/build/test-results/test/*Conformance*.xml`로 열둘 전부
  dynamicTest 실행 확인(`verdict-001`~`012`).
- cmd: `./gradlew --no-build-cache clean check` — exit 0 — `BUILD SUCCESSFUL in 32s`,
  344 actionable tasks(319 executed·25 up-to-date).
- cmd: `./gradlew --no-daemon :app:test`(전건, 필터 없이) — exit 0 —
  `tests="86" failures="0"`.
- cmd: `./gradlew --no-daemon :app:gateExecutionGate`(별도 호출, `:app:test` 뒤) — exit 0.
- cmd: `./gradlew --no-daemon qualityBaseline` — exit 0.

## 2026-09-09T11:45Z — secret 스캔(승격 반영 파일)
- cmd: `grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))"
  fixtures/manifest.yaml fixtures/input/verdict-00{1,2,3,4}.json
  fixtures/expected/verdict-00{1,2,3,4}.json
  app/src/test/kotlin/bidvector/app/conformance/VerdictExecutors.kt
  reports/evidence/m4/4b1/{checklist,rollback}.md`
- exit: 0(매치 6건, 전부 `fixtures/manifest.yaml`의 `token`/`token_alignment` — 기존과
  동일한 M1 계약 결속 도메인 어휘, 자격증명 아님). `checklist.md`의 매치 1건은 "secret
  스캔 통과" 서술 자체(체크리스트 항목 이름). 새 fixture·코드 파일에는 매치 0건.

## 2026-09-09T11:50Z — 커밋(승격, head 는 아래 실측)
- cmd: `git add fixtures/manifest.yaml fixtures/input/verdict-00{1,2,3,4}.json
  fixtures/expected/verdict-00{1,2,3,4}.json
  app/src/test/kotlin/bidvector/app/conformance/VerdictExecutors.kt
  reports/evidence/m4/4b1/{checklist,commands,rollback}.md && git commit ... -- <같은
  경로>`
- exit: 0. `decision/**` 무접촉 확인(`git status --porcelain -- decision/` 커밋 전후
  공백). head `7815d3b`.

## 2026-09-09T11:55Z — S-0(임시 clone, `7815d3b`에 pin)
- cmd: `d=$(mktemp -d) && git clone --quiet --no-hardlinks . "$d/repo" && (cd "$d/repo" &&
  git checkout --quiet 7815d3b && ./gradlew --no-build-cache clean check)`
- exit: 0 — `BUILD SUCCESSFUL in 1m 6s`, 353 actionable tasks 전부 executed(캐시 없는
  임시 clone).

## 2026-09-09T12:00Z — M-1 재실측(승격 반영, 같은 clone) — 공유 파일 line-level ·
  M0 원본 fixture 8개 line-level · 신규 파일 전체 삭제
- 배경: `git log --oneline 13cf0f6..HEAD -- <파일>`로 각 파일을 만진 커밋을 다시 나열 —
  `gate-tests.properties`는 여전히 4C-1(`7469bce`)·4B-1(`381eaeb`) 둘, 나머지(manifest.yaml·
  CorpusExecutors.kt·SharedKernelCorpusConformanceTest.kt·data-dictionary.md·
  capability-map.md·`fixtures/{input,expected}/verdict-00{1,2,3,4}.json`·
  `VerdictExecutors.kt`)는 이 range 전체가 4B-1 자신의 커밋만(`381eaeb`·`7133ccc`·
  `7815d3b` 중 하나 이상)이라 base..HEAD 로 안전.
- cmd: `git diff 381eaeb~1..381eaeb -- config/quality/gate-tests.properties | git apply -R`
  — exit 0(4B-1 hunk 만 격리).
- cmd: 나머지 12개 파일(공유 6 + M0 원본 fixture 8 중 실제 diff 있는 7 — `expected-004`는
  값이 우연히 동일해 diff 가 비어 `git apply -R`가 "No valid patches" 로 no-op, 이것도
  올바른 결과다) 각각 `git diff 13cf0f6..HEAD -- <파일> | git apply -R` — exit 0(11건),
  exit 128(1건, `expected-004` — 빈 patch, 예상된 no-op).
- cmd: `git restore --source=13cf0f6 --staged --worktree -- <신규 파일 35개>`(decision/**
  12·`VerdictExecutors.kt` 1·fixtures/{input,expected}/verdict-0[05-12] 16·
  `reports/evidence/m4/4b1/**` 6) — exit 0.
- `git status --porcelain`: **D 35 · M 13**(6 공유 + 7 M0 원본 fixture 실변경분,
  `expected-004`는 no-op 이라 diff 자체가 없다 — 목록과 일치).
- ① `gate.tests.decision` 4C-1 이전 두 줄로 복귀 / `gate.tests.workflow` 는
  `OutboxEntryTest` 포함 4C-1 rework 몫 유지(4C-1 몫 생존 확인).
- ② `fixtures/manifest.yaml`: `verdict-001`~`004` 전부 `classification:
  insufficient-evidence`·`source.kind: m0-derived-rule`(승격 전 원상)로 복귀,
  `verified_paths` 필드 부재(실측 grep). `verdict-005`~`012` 사라짐.
- ③ `fixtures/input/verdict-001.json`·`fixtures/expected/verdict-001.json` 이 M0
  원본 서술형(`gateOutcome`/`structuredPayload`, `reasonCodeRequired` 등)으로 정확히
  복귀(diff 로 실측 — 재구성 이전 파일과 byte-identical).
- ④ 신규 디렉터리 잔여 파일 0건 — `decision/src/main`엔 1D 소스 7개만,
  `app/.../conformance/`엔 `VerdictExecutors.kt` 부재, `reports/evidence/m4/4b1/`
  디렉터리 자체가 없어짐.
- cmd: `./gradlew --no-daemon :decision:compileKotlin :app:compileTestKotlin` — exit 0.
- cmd: `./gradlew --no-daemon :decision:test :workflow:test :app:test --tests
  '*Conformance*'` — exit 0. `decision:test` = 38(1D 그대로) · conformance = **74**
  (승격·8신설 이전 기준). 다섯 확인 전부 통과.
- clone 삭제(`rm -rf`), 원 worktree엔 영향 없음(별도 clone).

## 2026-09-09T13:00Z — 재검증 r2 장부층 일괄(M-1·L-1·L-2·하네스 레인) — 코드·기대값·해시 무변경
- verifier 재검증 판정 **ready-for-review**(`_workspace/m4-4b1/04_verifier_report_r2.md`),
  산출물층 blocker/high 0. M-1(medium)·L-1·L-2와 하네스 레인 누락 둘을 한 커밋으로 반영.
- **M-1(medium)**: `verdict-003`의 `verified_paths`가 `$.verdict` 하나뿐이라 정상 승격과
  force-bid 우회를 구분 못 하는데 `verifies`는 「출처가 노출된다」고 하고 이전
  `change_history`는 「그 규칙이 불변」이라 **기록까지** 했다 — 처분은 강등이 아니라
  **문면 정직화**(2026-08-31 floor-shortfall 선례와 같은 처분). `verifies`를 「force-bid
  경로가 BidNow(ForceBidOverride)를 낸다」까지로 좁히고, 「출처 노출」 축은 `not_covered`로
  옮겨 `VerdictLadderTest`의 `② force-bid 우회 — ...BidNow(ForceBidOverride)`를 이름으로
  짚었다. 이전 `change_history`의 「규칙 불변」 문장을 정정(결과는 불변, 노출 축은 corpus→
  unit test로 이동)하고 새 `change_history` 항목을 추가했다.
- **not_covered 넷 다 신설**: 001·002·004도 `not_covered`가 없었다(강등 시절 `OPEN-DIC-03`
  줄을 지운 뒤 대체 문면을 안 넣었었다) — 신설 여덟과 같은 형식으로 001·002는 임계
  운영값(005/006과 동일 축), 004는 `verdict-012`와 동일한 이유(reasonCode 리터럴·상수
  필드 셋)를 적었다.
- **L-1**: `verdict-002`의 `verifies`에 점수 주입(0.1/0.1)이 필연임을 명시 — `null`이면
  `forceBidOutcome`이 `Review(MlUnavailable)`로 새서 `LowPriority`에 닿지 못한다. 001(capacity-hold,
  점수 `null`로도 닿음)과의 대조를 "capacity-hold가 force-bid보다 먼저 평가된다"는 사다리
  순서의 값 증언으로 적었다.
- **L-2**: `golden-manifest.json`의 note가 낡아 있었다(001~004를 여전히
  `insufficient-evidence`·「dispatch 대상 밖」이라 적음, 실제로는 86 = 74+8+4) — note를
  정정하고 `cases` 배열에 001~004 네 항목(`sha256`·`expected_sha256`·`extraction_method`
  전부 manifest.yaml과 일치하도록 python 스크립트로 직접 대조해 기재)을 추가했다.
- **하네스 레인 절**: `git log --oneline 13cf0f6..HEAD -- CLAUDE.md .claude/` 재실행 →
  **2건**(`ea79355`·`d9a39cc`, 팀장이 붙인 두 스킬 개정) — scope.md에 등재, in_scope 밖·
  rollback 대상 아님을 명시.
- cmd: `python3 -c "import yaml; yaml.safe_load(open('fixtures/manifest.yaml'))"` — exit 0
  (YAML 유효성 확인).
- cmd: `python3 -c "import json; json.load(open('reports/evidence/m4/4b1/golden-manifest.json'))"`
  — exit 0(JSON 유효성, `cases` 12건 확인).
- cmd: `./gradlew --no-build-cache clean check` — exit 0 — `BUILD SUCCESSFUL in 31s`,
  344 actionable tasks(319 executed·25 up-to-date).
- cmd: `./gradlew --no-daemon :app:test`(별도 호출, 필터 없음) — exit 0 —
  `tests="86" failures="0"`.
- cmd: `./gradlew --no-daemon qualityBaseline` — exit 0.
- cmd: `grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))"
  fixtures/manifest.yaml reports/evidence/m4/4b1/golden-manifest.json
  reports/evidence/m4/4b1/scope.md` — exit 0(매치 6건, 전부 기존과 동일한
  `token`/`token_alignment` 도메인 어휘, golden-manifest.json·scope.md에는 매치 0건).

## 2026-09-09T13:05Z — 커밋(head `0aeb218`) + S-0 + M-1 재실측(커밋 해시 격리 절차)
- cmd: `git add fixtures/manifest.yaml reports/evidence/m4/4b1/{commands,golden-manifest,scope}.md`
  후 경로 명시 커밋 — exit 0. 4 files changed(145 insertions·8 deletions).
- cmd: `d=$(mktemp -d) && git clone --quiet --no-hardlinks . "$d/repo" && (cd "$d/repo" &&
  git checkout --quiet 0aeb218 && ./gradlew --no-build-cache clean check)` — exit 0 —
  `BUILD SUCCESSFUL in 49s`, 353 actionable tasks 전부 executed.
- 같은 clone에서 M-1 재실측: `git log --oneline 13cf0f6..HEAD -- <파일>`로 재확인 —
  `gate-tests.properties`만 여전히 4C-1(`7469bce`)과 겹쳐 4B-1 커밋(`381eaeb`) 해시로
  hunk 격리, 나머지(manifest.yaml·CorpusExecutors.kt·SharedKernelCorpusConformanceTest.kt·
  data-dictionary.md·capability-map.md·M0 원본 fixture 8개)는 이 range 전체가 4B-1
  커밋뿐이라 base..HEAD로 안전.
- cmd: `git diff 381eaeb~1..381eaeb -- config/quality/gate-tests.properties | git apply -R`
  — exit 0. 나머지 12개 파일 `git diff 13cf0f6..HEAD -- <파일> | git apply -R` — exit
  0(11건), exit 128(1건, `expected-004` 빈 patch, 예상된 no-op).
- cmd: `git restore --source=13cf0f6 --staged --worktree -- <신규 파일 35개>` — exit 0.
- `git status --porcelain`: **D 35 · M 13**(목록과 일치).
- ① `gate.tests.workflow`에 `OutboxEntryTest`(4C-1 몫) 유지, `gate.tests.decision` 4C-1
  이전 두 줄로 복귀. ② `fixtures/manifest.yaml`: `verdict-001`~`004`만 남고(`insufficient-evidence`
  로 복귀) `005`~`012` 사라짐(`grep -n "id: verdict-0"` 실측 — 정확히 넷).
- cmd: `./gradlew --no-daemon :decision:compileKotlin :app:compileTestKotlin` — exit 0.
- cmd: `./gradlew --no-daemon :decision:test :workflow:test :app:test --tests
  '*Conformance*'` — exit 0. `decision:test` = 38(1D 그대로, `FloorShortfallKernelTest`
  17+`ProvenanceRulesTest` 21) · conformance = **74**(승격·8신설 이전 기준). 다섯 확인
  전부 통과.
- clone 삭제(`rm -rf`), 원 worktree엔 영향 없음.

## 2026-09-09T14:00Z — 사용자 승인 반영(2026-09-09) — 4B-1 종결 + verdict 12건 case 승인
- `fixtures/manifest.yaml`: `verdict-001`~`012`의 `review.approved_by_user`를 `true`로,
  `claude_commit`(001~004는 M0 원본 저작 커밋 `9e1223a`, 005~012는 커널 신설 커밋
  `381eaeb`)·`approved_at: 2026-09-09`·`approval_record`·`approval_scope`를 채웠다
  (`strategy-edit` 다섯과 같은 형식).
- cmd: `git diff -U0 -- fixtures/manifest.yaml | grep "^@@"` — 24개 hunk 전부 base-line
  8011~8784 범위(승인 전 `verdict-001`~`012`의 review 블록 경계) 안임을 실측 확인 —
  다른 도메인(`koneps-collection`·`strategy-*` 등) 무접촉.
- cmd: `python3 -c "import yaml; yaml.safe_load(open('fixtures/manifest.yaml'))"` —
  exit 0. `review.approved_by_user`·`approved_at`·`approval_record`·`approval_scope`
  전부 12건에 존재함을 python으로 재확인.
- `reports/evidence/m4/4b1/checklist.md`에 「사용자 승인」 절 신설 — 승인 범위 셋,
  verifier r2 근거, 재작업 1/5.
- `milestone-4.md`의 「### Slice 4B」 절에 종결 문단 신설 — 분할 이유·조사 608줄이
  특정한 여섯 실패 형태와 뒤집기·`OPEN-DIC-03` 종결+`OPEN-4B1-OFF-LADDER-DROPS`
  신설·corpus 12 승격·알려진 제한. `capability-map.md`의 `OPEN-4B1-OFF-LADDER-DROPS`
  등재(4B-2 배정) 재확인 — 이미 등재돼 있어 무변경.
- **4C-1 몫만 되돌리는 재실측(4C-1 종결 문단의 근거 수치)**: 임시 clone(`efd2d60` pin)
  에서 4C-1의 커밋(`13cf0f6`·`7469bce`)이 넣은 hunk만 `git apply -R`로 걷고(4B-1 몫은
  그대로), 4C-1 신규 파일(`workflow/**/event/**`·`StrategyEditExecutors.kt`·
  `workflow/**/strategy/{Ports,EditStrategyWorkflow}.kt`·
  `EditStrategyWorkflowTest.kt`·`reports/evidence/m4/{4c1,4a/scope.md}`)을 삭제 —
  `./gradlew --no-daemon :workflow:compileKotlin :decision:compileKotlin
  :app:compileTestKotlin` exit 0, `./gradlew --no-daemon :workflow:test :decision:test
  :app:test --tests '*Conformance*'` exit 0 — conformance **86**·`decision:test`
  **62**(`FloorShortfallKernelTest`17+`FloorOverrideValidationTest`4+
  `VerdictLadderPolicyDataTest`4+`UnitScoreTest`3+`LadderInputTest`2+
  `ProvenanceRulesTest`21+`VerdictLadderPropertyTest`2+`VerdictLadderTest`9=62)·
  `workflow:test` **39**(strategy 여섯 스위트 합, event 스위트 전부 사라짐 — 4A 관례로
  복귀). `gate.tests.decision`·`data-dictionary.md` §3.6(`SkipReason` 매치 7건) 생존
  확인. 4B-1 몫만 되돌리는 재실측은 이전 라운드에서 이미 반복(`decision:test` 38·
  conformance 74). clone 삭제.
- cmd: `./gradlew --no-build-cache clean check` — exit 0 — `BUILD SUCCESSFUL in 32s`,
  344 actionable tasks(319 executed·25 up-to-date).
- cmd: `./gradlew --no-daemon :app:test`(별도 호출, 필터 없음) — exit 0 —
  `tests="86" failures="0"`.
- cmd: `git status --porcelain -- fixtures/manifest.yaml milestone-4.md
  reports/evidence/m4/4b1/checklist.md reports/evidence/m4/4c1/checklist.md` — exit 0,
  이 넷과 정확히 일치. 양성 대조(비파괴): `reports/evidence/m4/4b1/rollback.md`에 개행
  한 줄 추가 → `M` 관측 → `sed -i '' -e '$ d'`로 추가한 빈 줄만 절삭(파괴적 삭제 없음)
  → 재확인(비어있음, exit 0).
- cmd: `grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))"
  fixtures/manifest.yaml milestone-4.md reports/evidence/m4/4b1/checklist.md
  reports/evidence/m4/4c1/checklist.md` — exit 0(매치 8건, 전부 기존과 동일한
  `token`/`token_alignment` 도메인 어휘 + 두 checklist.md의 「secret 스캔 통과」
  자기참조 서술, `milestone-4.md`에는 매치 0건).

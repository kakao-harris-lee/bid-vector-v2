# M9 / award-ingest — 실행 명령과 종료 코드

base `79f3e5c4` · 브랜치 `feature/m9-award-ingest` · 실행은 전부 worktree
`bid-vector-v2-m9-award-ingest` 에서, Gradle 은 호스트 빌드 자리 래퍼(`bv-build.sh`)를 지난다.

## 표적 실행

## 2026-11-05T09:01Z
- cmd: `./gradlew --no-daemon --continue :workflow:test --tests '…CollectAwardListUseCaseTest' :adapters:test --tests '…koneps.KonepsAwardListSourceTest' --tests '…KonepsOpeningResultSourceTest' --tests '…KonepsOpeningCompleteSourceTest' --tests '…KonepsBidPriceFormulaASourceTest' :app:test --tests '…AwardCollectionWiringTest' --tests '…OneShotRunnerGuardTest' --tests '…AwardCollectionE2ETest' --tests '…architecture.CollectionArchitectureGateTest' --tests '…architecture.CollectionDepthGateTest' --tests '…architecture.CollectionArchitectureGateCatchesViolationsTest' --tests '…architecture.TransportSurfaceGateTest'`
- exit: 1
- 핵심 결과: adapters 29 통과(기존 개찰 소스 test 포함 — 걷기 공유가 기존 거동을 바꾸지 않았다) ·
  app 93 중 5 실패(전송 표면 등재 미달 넷 · 상한 둘이 어긋난 E2E 설정 하나) · workflow test 는 컴파일
  실패(`PageCursor` 토큰 타입)

## 2026-11-05T09:03Z
- cmd: `./gradlew --no-daemon --continue :workflow:test --tests '…CollectAwardListUseCaseTest' :app:test --tests '…AwardCollectionE2ETest' --tests '…TransportSurfaceGateTest' --tests '…TransportSurfaceGateCatchesViolationsTest' --tests '…CollectionDepthGateTest'`
- exit: 0
- 핵심 결과: use case 4 · E2E 3(스윕·승격·상한 정지 전부) · 전송 표면 게이트 19+8 · 깊이 게이트 3, 실패 0

## 변이

## 2026-11-05T09:07Z (무효 — 측정이 아니었다)
- cmd: `./gradlew … --rerun :adapters:test --tests … :app:test --tests …`(전역 자리의 `--rerun`)
- exit: 1
- 핵심 결과: **아무 test 도 돌지 않았다** — Gradle 이 `--rerun` 을 전역 옵션으로 거부했고, 리포트의
  `failures=0` 은 앞 실행의 낡은 XML 이었다. 변이 측정은 종료 코드만으로 판독할 수 없다(task 옵션
  자리로 옮겨 다시 쟀다 — 아래 두 줄).

## 2026-11-05T09:09Z — 변이 ① 적재 endpoint 토큰을 개찰결과 목록으로 바꿔치기
- cmd: `./gradlew --no-daemon --continue :adapters:test --tests '…KonepsAwardListSourceTest' --rerun :app:test --tests '…AwardCollectionE2ETest' --rerun`
- exit: 1
- 핵심 결과: **RED** — adapters 2/2 실패(관측 endpoint · 계약 등재 키 둘 다: `presentIn` 대조가 낙찰 축
  키를 전부 떨어뜨린다) · E2E 3 중 2 실패(적재 행 6 → 0 · 승격된 낙찰 칸 미달)

## 2026-11-05T09:10Z — 변이 ② 개인정보 경로(계약 미등재 키 제외) 우회
- cmd: 같은 명령
- exit: 1
- 핵심 결과: **RED** — adapters 1 실패(미등재 식별자가 관측에 담긴다) · E2E 1 실패(적재된 원문 전수에
  미등재 식별자가 나타난다). 양성 대조(상호는 담긴다)는 두 판 모두 통과라 부재 단언이 빈 관측을 보고
  초록이 된 것이 아니다.

## 2026-11-05T09:11Z — 변이 원복
- cmd: `git status --porcelain -- <변이 파일 둘>`
- exit: 0
- 핵심 결과: 빈 출력(변이는 cp 백업으로 되돌렸고 그 두 파일만 만졌다)

## rollback

원천은 이 브랜치가 병합한 main 의 고정 SHA `3ad4f3f6` 이고, 라운드마다 목록을 다시 산출한다. 아래가
현재 판이며 앞 판들(`df4976cc`·`79f3e5c4` 기준)은 이 줄이 갈음한다(라운드 이력은 PR 조치 코멘트).

## 2026-11-06T01:5xZ — 임시 clone 에서 22 경로를 `3ad4f3f6` 으로 되돌림(①②③)
- cmd: `git clone --no-local -b feature/m9-award-ingest …` → `git checkout <실측 HEAD>` → `git merge-base --is-ancestor` 둘 → `xargs git restore --source=3ad4f3f6 --staged --worktree --`
- exit: 0
- 핵심 결과: 전제 둘 참 · 상태 계수 `D` 12 · `M` 10(= 기계 산출 22 경로 — 수정 라운드 2 가 공유
  incident runbook 을 in_scope 로 들여 하나 늘었다) · `git diff 97ed49c4 -- <22 경로>` 0 줄 ·
  **evidence 를 뺀 전수 diff 가 빈 목록**(결과 트리 == 그 main 트리) · 내 표지 0(gate-tests 의 내 test
  클래스 다섯 · incident runbook 의 `award-collection` 문단 · capability map 의 `award-ingest`) ·
  남의 표지 보존(gate-tests 항목이 main 과 같은 **512** · main 의 새 주석 1 · incident runbook §3.3 머리 1 · capability §12.3 1) ·
  되돌리지 않은 evidence 다섯 그대로

## 2026-11-06T09:2xZ — 임시 clone 에서 22 경로를 `97ed49c4` 로 되돌림(①②③, 라운드 4 재측정)
- cmd: 앞 라운드와 같은 절차(`git clone --no-local` → `git checkout <실측 HEAD>` → `merge-base --is-ancestor` 둘 → `xargs git restore --source=97ed49c4 --staged --worktree --`)
- exit: 0
- 핵심 결과: 전제 둘 참 · `D` 12 · `M` 10(= 기계 산출 22 경로, 라운드 2 와 같다) · `git diff 3ad4f3f6 --`
  0 줄 · **evidence 를 뺀 전수 diff 빈 목록** · 내 표지 0(gate-tests 의 내 test 클래스 다섯 합 0 ·
  incident runbook 의 `award-collection` 0 · capability map 의 `award-ingest` 0 · award 파일 전부 삭제) ·
  남의 표지 보존(gate-tests 의 main 쪽 셋 합 3 · incident runbook §3.3 머리 1 · capability §12.3 1) ·
  되돌리지 않은 evidence 다섯 그대로(그 안의 `unfinishedSlots` 한 자리가 유일한 내 어휘 잔존이고 의도다)

## 2026-11-06T09:3xZ — 되돌린 트리의 ④ compile · ⑤⑥ 영향 test 와 닿은 게이트
- cmd: `./gradlew --no-daemon --continue :workflow:compileTestKotlin :adapters:compileTestKotlin :app:compileTestKotlin` 와 `… :workflow:test --tests 'bidvector.workflow.collection.*' :adapters:test --tests 'bidvector.adapters.koneps.*' :app:test --tests 'bidvector.app.architecture.*' --tests 'bidvector.app.wiring.*' gateRegistrationGate leakPatternGate sizeGate memberEffectGate`
- exit: 0 과 0
- 핵심 결과: FAILED 0 — 되돌린 트리가 서고(복원 계수 `D` 12 · `M` 10 유지) 이 slice 가 닿은 게이트
  22 task 까지 초록이다. `:adapters:test`·`:app:test` 는 실행, `:workflow:test` 는 **FROM-CACHE** —
  되돌린 입력이 그 main 트리의 통과 실행과 바이트 동일이라는 뜻이고, 이 확인이 노리는 트리 동일성과
  같은 말이다.

## 병합과 그 뒤

## 2026-11-05T10:1xZ — PR CI run 42008427198(판정 전 첫 실행)
- cmd: CI Kotlin check job 의 `./gradlew --no-daemon check`
- exit: 실패 — `:app:ktlintTestSourceSetCheck` 한 건(`function-expression-body`), 나머지 job 셋(container ·
  ml-engine · web)은 pass
- 핵심 결과: 위반 좌표는 CI 의 `reports` 산출물에서 읽었다(로컬 전건을 다시 돌리지 않았다). 그 뒤
  Kotlin 을 만지는 실행에는 모듈 `ktlint`·`detekt` 를 항상 함께 걸었다.

## 2026-11-05T15:2xZ — `git merge origin/main`(df4976cc) 과 겹침 보존 계수
- cmd: `git merge origin/main` 후 등록부 둘의 양쪽 표지 계수
- exit: 0 (충돌 0)
- 핵심 결과: 겹치는 경로는 둘(`config/quality/gate-tests.properties` · `docs/discovery/capability-map.md`).
  gate-tests 는 내 넷과 main 의 둘이 모두 1 · capability map 은 내 표지 2 와 main 표지 셋이 모두 1 이상 ·
  충돌 표지 0. `OneShotRunnerGuard` 는 main 이 건드리지 않았고 이 slice 도 고치지 않는다(guard 가 러너를
  등재 없이 센다는 것은 배선 test 가 잰다).

## 2026-11-05T15:30Z — 병합 트리 표적 실행(모듈 lint 포함)
- cmd: `./gradlew --no-daemon --continue :workflow:ktlintCheck :workflow:detekt :adapters:ktlintCheck :adapters:detekt :app:ktlintCheck :app:detekt :workflow:test --tests 'bidvector.workflow.collection.*' --rerun :adapters:test --tests 'bidvector.adapters.koneps.*' --rerun :app:test --tests '…AwardCollectionE2ETest' --tests '…AwardCollectionWiringTest' --tests '…OneShotRunnerGuardTest' --tests '…OpeningPromotionRunnerTest' --tests 'bidvector.app.architecture.*' --rerun`
- exit: 1 → 등재 뒤 0
- 핵심 결과: 203 중 3 실패는 **app 층 허용 목록 미등재**(`AppHttpDependencyGateTest`) 하나의 축이었다 —
  새 러너·배선·설정 타입을 형제 레인과 같은 자리(tier1 조립 · tier3 수집 러너)에 등재해 해소. 그 밖에는
  전부 초록: E2E 3 · 배선 10 · guard 5 · 승격 러너 3 · use case 4 · 어댑터 2 · **koneps 어댑터 의존 게이트 1**.
- 판단이 갈린 지점: 첫 판은 수집 포트를 `workflow` 에 새로 선언했고 그 게이트가 거부했다(「domain 이
  의존하는 port 는 domain 안에 선다」). 도메인에 이미 **메서드 하나짜리** `NoticeSourcePort` 가 있어 그것을
  재사용했다 — `procurement/**` 변경은 여전히 0 이고, 포트를 새로 지을 자리가 domain 뿐이라는 규약과
  「상세 축을 꽂을 수 없다」는 성질을 동시에 만족한다(대가는 알려진 제한 6).

## 수정 라운드 (cr H-1 · M-1·M-2 · L-1~L-5·L-7)

## 2026-11-06T00:48Z — H-1 회귀가 **구판에서 RED** 인가(둘째 병합 트리)
- cmd: 종료 코드 술어만 옛 문면(`halted` 만 본다)으로 **바꿔치운** 뒤 `./gradlew --no-daemon --continue :app:test --tests '…AwardExitCodeTest' --tests '…AwardCollectionE2ETest' --rerun`
- exit: 1
- 핵심 결과: **RED** — 8 중 2 실패이고 그 둘이 절단 단언이다(`AwardExitCodeTest` 「잘린 슬롯이 있으면
  멈추지 않았어도 미완이다」 · E2E 「슬롯이 잘리면 멈추지 않고도 미완으로 끝낸다」). 나머지 여섯(쪽
  걷기 포함)은 구판에서도 초록 — 절단 축만 잡는다는 음성 대조다. 원복은 그 파일 하나만(porcelain
  빈 출력).

## 2026-11-06T00:50Z — 둘째 병합 트리 표적 실행(모듈 lint · 닿은 게이트 포함)
- cmd: `./gradlew --no-daemon --continue :workflow:ktlintCheck :workflow:detekt :adapters:ktlintCheck :adapters:detekt :app:ktlintCheck :app:detekt :workflow:test --tests 'bidvector.workflow.collection.*' --rerun :adapters:test --tests 'bidvector.adapters.koneps.*' --rerun :app:test --tests 'bidvector.app.collection.*' --tests 'bidvector.app.wiring.*' --tests 'bidvector.app.architecture.*' --rerun gateRegistrationGate leakPatternGate sizeGate memberEffectGate`
- exit: 0
- 핵심 결과: FAILED 0 — test class 73(workflow 105 · adapters 102 · app 386, 실패 0이고 XML 전수가 이
  실행 것이다) · 세 test task 전부 실행(UP-TO-DATE·FROM-CACHE 아님) · `gateRegistrationGate`·`sizeGate`
  11 모듈 · `leakPatternGate` · `memberEffectGate` 실행
- **앞 라운드의 이 자리는 측정이 아니었다** — 호스트 재부팅(2026-11-06 09:00 KST) 전에 빌드 자리를
  못 잡은 실행의 결과 줄이 미리 적혀 있었다. 위 줄이 그 자리를 실측으로 바꿔치웠다.

## 2026-11-06T01:01Z — workflow ktlint 는 위 실행에서 UP-TO-DATE 였다(강제 실행으로 갈음)
- cmd: `./gradlew --no-daemon --continue :workflow:ktlintMainSourceSetCheck --rerun :workflow:ktlintTestSourceSetCheck --rerun :workflow:ktlintKotlinScriptCheck --rerun`
- exit: 0
- 핵심 결과: 세 task 모두 **실행**되고 위반 0. 앞 실행에서 이 셋만 UP-TO-DATE 였고 리포트 mtime 이
  H-1 의 workflow 편집보다 앞서 「그 판정이 현재 소스를 본 것인가」가 열려 있었다 — 강제 실행이 그
  구멍을 닫는다(CI 전건 `check` 는 깨끗한 checkout 이라 애초에 이 틈이 없다).

## 2026-11-06T00:12Z — `git merge 3ad4f3f6`(둘째 병합) 과 겹침 보존 계수
- cmd: `git merge 3ad4f3f6` 뒤 등록부 둘의 양쪽 표지 계수 · 중복 행 훑기
- exit: 0 (충돌 0)
- 핵심 결과: main 의 다섯 PR(#131 cve · #133 ledger · #130 portal-02 · #132 web-followups ·
  #135 opening-harness)이 들어왔고 겹치는 경로는 **둘**(`config/quality/gate-tests.properties` ·
  `docs/discovery/capability-map.md`). gate-tests 는 내 test 클래스 다섯과 main 의 셋이 모두 1 ·
  capability map 은 main 이 더한 줄이 전부 1 이고 `OPEN-` 행 중복 0 · 충돌 표지 0. capability map §12.3
  (ledger 레인)이 「PR #128 은 새 식별자를 만들지 않고 `OPEN-SET01-AWARD-NAME-INGEST` 를 수령한다」로
  적었으므로 이 slice 가 등재를 더할 자리는 없다.

## 수정 라운드 2 (cr r2 H-1·H-2 · M-1·M-2 · L 다섯 · v r2 M-1·M-2·L-1·L-2)

## 2026-11-06T01:44Z — H-2 회귀가 **구판 술어에서 RED** 인가(통제군 포함)
- cmd: 「다 받았는가」를 구판(`accounting.truncationCause == null`)으로 **바꿔치운** 뒤 `./gradlew --no-daemon --continue :workflow:test --tests '…CollectAwardListUseCaseTest' --rerun :app:test --tests '…AwardExitCodeTest' --tests '…AwardCollectionE2ETest' --rerun`
- exit: 1
- 핵심 결과: **RED 둘** — use case 6 중 1(「총수를 말한 원천에서 덜 받고 끝난 슬롯은 사유가 없어도
  미완이다」) · app 9 중 1(E2E 「총수에 못 미치고 곱게 멈춘 슬롯도 미완으로 끝낸다」). **통제군 13 은
  전부 초록** — 절단 E2E · 종료 코드 test 셋 · 「낙찰이 없는 날의 빈 응답은 끝낸 것이다」가 구판에서도
  통과해, 새 단언이 **짧은 걷기 축만** 잡고 빈 응답을 미완으로 돌리는 과잉이 아님을 함께 보인다.
  원복은 그 파일 하나만(porcelain 빈 출력).

## 2026-11-06T01:50Z — 수정 라운드 2 표적 실행(모듈 lint · 닿은 게이트 포함)
- cmd: 라운드 1 과 같은 표적 집합(세 모듈 `ktlintCheck`·`detekt` + 표적 test `--rerun` + `gateRegistrationGate leakPatternGate sizeGate memberEffectGate`)
- exit: 0
- 핵심 결과: FAILED 0 — test class 73(workflow 107 · adapters 102 · app 387, 실패 0이고 XML 전수가 이
  실행 것이다) · 세 test task 전부 실행 · 게이트 22 task 실행
- **측정 주의**: `:workflow:ktlint*` 가 이 실행에서 UP-TO-DATE 로 건너뛰었다 — 이 라운드가 그 모듈의
  main·test 소스를 고쳤는데도다(리포트 mtime 이 그 편집보다 앞선다). 그래서 그 모듈의 로컬 ktlint 초록은
  근거로 쓰지 않고 아래 줄의 강제 실행으로 갈음한다. 깨끗한 checkout 에서 도는 CI 전건 `check` 에는 이
  틈이 없다(라운드 1 에서도 같은 자리였다).

## 2026-11-06T01:56Z — workflow lint 강제 실행(위 UP-TO-DATE 갈음)
- cmd: `./gradlew --no-daemon --continue :workflow:ktlintMainSourceSetCheck --rerun :workflow:ktlintTestSourceSetCheck --rerun :workflow:ktlintKotlinScriptCheck --rerun :workflow:detekt --rerun`
- exit: 0
- 핵심 결과: 네 task 모두 **실행**되고 위반 0

## 수정 라운드 3 (cr r3 H-1 · M-1·M-2·M-3 · L 넷 · v r3 M-1·L-1·L-2)

이 라운드는 **새 파일을 만들지 않았다** — 복원 목록은 22 경로 그대로다(라운드 2 가 들인 공유 incident
runbook 포함). 앞 evidence 커밋 메시지가 「알려진 제한 **셋**」으로 적은 셈은 어긋났다(목록에 선 것은
둘이고 셋째는 §1 「쪽 걷기(wire)」 행의 문면으로 갔다 — cr r3 L-2). 이력은 되쓰지 않고 여기 한 줄로
정정한다. workflow lint 는 이 라운드에서도 **강제 실행**으로 돌렸다(위 측정 주의와 같은 자리).

## 2026-11-06T03:37Z — 변이 A: 총수 최댓값을 구판(마지막 배치 총수)으로 되돌림
- cmd: `tally.toSourceAccounting().copy(sourceTotal = claimedTotal)` → `tally.toSourceAccounting()` 로 **바꿔치운** 뒤 `./gradlew --no-daemon --continue :workflow:test --tests '…CollectAwardListUseCaseTest' --rerun`
- exit: 1
- 핵심 결과: **RED** — 8 중 1 실패이고 그 하나가 「쪽 상한 재개 뒤 총수를 말하지 않는 배치가 와도 앞
  배치의 총수를 잃지 않는다」다. 나머지 7(짧은 걷기·빈 응답·예산·쿼터·커서·회계 축)은 구판에서도 초록 —
  이 단언이 **총수 덮어쓰기 축만** 잡는다.

## 2026-11-06T03:38Z — 변이 B: 적재 결측 계수를 구판(싣지 않음)으로 되돌림
- cmd: 보고 생성의 `duplicates, dropped` → `0, 0` 으로 바꿔치운 뒤 같은 명령
- exit: 1
- 핵심 결과: **RED** — 8 중 1 실패이고 그 하나가 「받았지만 적재되지 않은 항목은 계수로 서고 미완으로
  세지 않는다」다. 나머지 7 초록(계수를 0 으로 눌러도 종료 코드 축은 움직이지 않는다 — 그 분리가
  팀장 결정의 실물이다). 원복은 그 파일 하나만(porcelain 빈 출력).

## 2026-11-06T03:4xZ — 수정 라운드 3 표적 실행 ①(workflow lint 강제 · 닿은 게이트 포함)
- cmd: `./gradlew --no-daemon --continue :workflow:ktlintMainSourceSetCheck --rerun :workflow:ktlintTestSourceSetCheck --rerun :workflow:detekt --rerun :adapters:ktlintCheck :adapters:detekt :app:ktlintCheck :app:detekt` + 세 모듈 표적 test `--rerun` + `gateRegistrationGate leakPatternGate sizeGate memberEffectGate`
- exit: 1
- 핵심 결과: FAILED task **하나** — `:workflow:ktlintTestSourceSetCheck`(`standard:chain-method-continuation`
  둘, 새 회계 단언의 한 줄 사슬). test·게이트는 전부 초록(게이트 22 task). **강제 실행이 실제로 숨어
  있던 위반을 잡았다** — 그 모듈의 UP-TO-DATE 가 거짓 초록이라는 두 라운드의 기록이 값을 냈다.
  구조 분해로 사슬을 끊어 고쳤다(별도 `style` 커밋, 거동 변화 0).

## 2026-11-06T03:54Z — 표적 실행 ②(lint 수정이 닿는 workflow task 강제 재실행)
- cmd: `./gradlew --no-daemon --continue :workflow:ktlintMainSourceSetCheck --rerun :workflow:ktlintTestSourceSetCheck --rerun :workflow:detekt --rerun :workflow:test --tests 'bidvector.workflow.collection.*' --rerun`
- exit: 0
- 핵심 결과: 네 task 모두 **실행**되고 위반 0 · use case test class 8/0

## 2026-11-06T04:0xZ — 표적 실행 ③(판정 트리에서 adapters·app lint·test 와 게이트 넷)
- cmd: `./gradlew --no-daemon --continue :adapters:ktlintCheck :adapters:detekt :app:ktlintCheck :app:detekt :adapters:test --tests 'bidvector.adapters.koneps.*' --rerun :app:test --tests 'bidvector.app.collection.*' --tests 'bidvector.app.wiring.*' --tests 'bidvector.app.architecture.*' --rerun gateRegistrationGate leakPatternGate sizeGate memberEffectGate`
- exit: 0
- 핵심 결과: FAILED 0 — `:adapters:test`·`:app:test` 실행(102 · 388, 실패 0) · 게이트 22 task 실행 ·
  adapters·app lint 은 UP-TO-DATE(실행 ① 에서 **같은 소스로 실행돼** 초록이었고 `style` 커밋이 그 두
  모듈을 만지지 않았다)
- 왜 한 번 더 돌렸는가: 실행 ① 은 `style` 커밋 **앞** 트리의 측정이다. 그 커밋이 workflow test 소스
  2 줄을 바꿨으므로 「입력이 같으니 앞 결과가 덮는다」로 갈음하지 않고 판정 트리에서 다시 쟀다. 크기 축의
  여유는 함께 실측했다(그 파일 380 줄 / 한도 500 · 가장 긴 함수 43 줄 / 한도 50 · 타입 멤버 축은 `main`
  전용이라 test 파일을 재지 않는다).

## 교차 지적 (ops-code 레인 — `haltOf` 소진 `when`)

거동 변화가 **오늘은 0** 인 변경이라(이 브랜치에 `AccessBlocked` 가 없다) 값으로 잴 RED 가 없다.
그래서 **게이트가 무는지**를 직접 쟀다 — 버릴 clone 에 13번째 `TruncationCause` 를 심는다.

## 2026-11-06T04:3xZ — 프로브 1판(무효 — 내 자리를 재지 못했다)
- cmd: clone 에 사유만 심고 `./gradlew --no-daemon :workflow:compileKotlin`
- exit: 1
- 핵심 결과: 컴파일이 멈춘 자리가 **procurement 의 소진 `when` 둘**(`truncationCodeOf`·`failureOf`)이었다
  — 그 모듈이 먼저 컴파일되므로 workflow 는 시작도 못 했다. 저장소 전체가 같은 형태로 닫혀 있다는
  증거이지만 **이 slice 의 자리를 잰 것이 아니다**. 프로브를 고쳐 다시 쟀다(아래).

## 2026-11-06T04:5xZ — 프로브 2판: 사유 + procurement 의 소진 `when` 둘을 채움
- cmd: 같은 clone 에서 사유를 심고 `truncationCodeOf`·`failureOf` 에 분기를 더한 뒤 `:procurement:compileKotlin` → `:workflow:compileKotlin`
- exit: 0 과 **1**
- 핵심 결과: **게이트가 내 자리에서 물었다** — procurement 는 컴파일되고(0) workflow 가 멈추며 지목한
  자리가 `CollectAwardListUseCase.kt` 의 `haltOf` 다(「'when' expression must be exhaustive. Add the
  'AccessBlockedProbe' branch or an 'else' branch.」). **음성 대조**: 사유를 심지 않은 같은 clone 의
  `:workflow:compileKotlin` 은 0 이다.
- 함께 드러난 사실(교차 보고): 같은 프로브가 **형제 공고 목록 갈래의 `CollectNoticesUseCase` 도** 지목한다
  — ops-code 의 새 사유는 이 갈래와 그 갈래 **둘**에서 결정을 강제한다(그 자리는 내 범위 밖이다).

## 2026-11-06T05:0xZ — 판정 트리 표적 전부(라운드 3 마지막)
- cmd: 라운드 3 과 같은 표적 집합(workflow lint 강제 · 세 모듈 표적 test `--rerun` · 게이트 넷)
- exit: 0
- 핵심 결과: FAILED 0 — workflow lint·detekt **강제 실행** 초록 · 세 test task 전부 실행 · 게이트 22 task
- 이 라운드에 ktlint 가 **두 번** 붉었다: 회계 단언의 한 줄 사슬(`chain-method-continuation`)과 소진
  `when` 의 분기 간 빈 줄(`blank-line-between-when-conditions`). 둘 다 **강제 실행이 잡았고** 각자
  `style` 커밋으로 고쳤다 — 그 모듈의 UP-TO-DATE 가 거짓 초록이라는 기록이 두 라운드 연속 값을 냈다.

## 교차 지적 라운드 2 — 멈춤 집합 등식 · 셋째 병합 (판정 SHA 6aa8dc46)

팀장 지적 ①(「`haltOf` 가 아직 `else` 로 끝난다」)은 **그 시점 기준으로는 이미 반영돼 있었다** —
`a1f4b80c`(보고한 판정 SHA) 다음의 `4d4d1712`·`c0388ba8` 가 그것이다. 이 라운드가 더한 것은 그
지적의 **나머지 절반**, 즉 사유마다 선택을 잠그는 등식 test 와 그 변이다.

## 2026-11-06T05:1xZ — `git merge 97ed49c4`(셋째 병합)과 합집합 계수
- cmd: `git merge 97ed49c4` 뒤 등록부 항목·주석 양쪽 계수
- exit: 0 (충돌 0)
- 핵심 결과: #134(gates-tests)가 들어왔고 겹치는 경로는 `config/quality/gate-tests.properties` **하나**다.
  그 파일에서 main 이 더한 것은 **주석 여덟 줄**이고 등재 항목은 0 — 합집합 계수가 **512 + 5 = 517**,
  중복 항목 0, 내 다섯이 각 1, main 의 주석 여덟 줄이 각 1, 앞 병합의 main 셋도 각 1, 충돌 표지 0.

## 2026-11-06T05:2xZ — 변이 A: `TransportFailure` 를 멈춤 쪽으로 **옮김**
- cmd: 계속 걷기 목록에서 빼고 멈춤 분기로 옮긴 뒤(바꿔치우기 — 더하기만 하면 소진 `when` 이 컴파일을 거부한다) `./gradlew --no-daemon --continue :workflow:test --tests '…CollectAwardListUseCaseTest' --rerun`
- exit: 1
- 핵심 결과: **RED** — 9 중 2 실패(등식 test 와 「쿼터 거절도 멈추지만 전송 실패는 다음 슬롯으로 간다」).
  등식이 한 사유의 이동을 잡는다.

## 2026-11-06T05:2xZ — 변이 B: `QuotaExhausted` 를 계속 걷기 쪽으로 **옮김**
- cmd: 같은 명령
- exit: 1
- 핵심 결과: **RED** — 9 중 2 실패(같은 둘). 반대 방향의 이동도 잡힌다 — 등식이 한쪽으로만 기울지
  않았다는 증거다. 원복은 그 파일 하나만(porcelain 빈 출력).

## 2026-11-06T05:3xZ — 병합 트리 표적 전부(workflow lint 강제 · 게이트 넷)
- cmd: 라운드 3 과 같은 표적 집합
- exit: 0
- 핵심 결과: FAILED 0 — workflow·adapters lint·detekt **실행** 초록(app 은 UP-TO-DATE — 그 모듈 소스가
  이 라운드에 바뀌지 않았다) · 세 test task 전부 실행, 실패 0 · 게이트 22 task

## 수정 라운드 4 (cr r4 H-1·M-1·M-2 · L 셋 · v r4 M-1·L-1·L-2)

## 2026-11-06T08:3xZ — runbook §3.1 SELECT 를 **축어로** 돌린다(팀장 지시)
- cmd: 버릴 clone 의 probe test 가 `docs/runbook/m9-award-collection.md` 의 첫 `sql` 펜스를 **그대로 읽어**
  날짜 자리만 넓혀 실행한다(mock 수집 한 판 — 한 행의 공고번호를 비워 드롭을 만든다)
- exit: 0
- 핵심 결과: **행 2(슬롯 둘)** — 각 행이 `received 3 · normalized 2 · duplicate 0 · dropped 1 ·
  source_total 3 · pages_fetched 2 · truncation_cause null · drop_reasons {"CollectionMissingNoticeNumber": 1}`.
  슬롯 등식 `received = normalized + duplicate + dropped` 가 두 행 다 성립하고(3 = 2+0+1), `dropped > 0`
  이라 판독 3 이 서고 `received = source_total` 이라 판독 2 는 서지 않는다 — 문서가 적은 그대로다.
  열 열 개가 전부 실재한다(질의가 행을 낸다). SQL 을 문서에서 읽으므로 문면이 바뀌면 이 probe 가 따라간다.

## 2026-11-06T08:2xZ — cr M-2 처방만으로는 **한 방향이 열린다**(측정)
- cmd: clone 에 13번째 사유를 심고 **기대 집합을 손대지 않은 채** 그 사유를 *계속 걷기* 쪽에 넣은 뒤 `:workflow:test --tests '…CollectAwardListUseCaseTest' --rerun`
- exit: **0 (초록 — 변이가 잡히지 않았다)**
- 핵심 결과: 모수를 `permittedSubclasses` 로 도출해도 **「멈춤 집합의 등식」은 그 판을 놓친다** — 새 사유가
  멈춤 집합에 들어오지 않으므로 집합이 그대로다. 그런데 그 방향이 `else` 를 지운 이유(403 이 조용히 계속
  걷는다) 바로 그 방향이다. 그래서 기대값을 **사유 → 멈추는가 전수 표**로 바꿨다(키가 하나 늘어 어느 쪽에
  넣어도 붉다). cr 처방을 그대로 넣고 재 보지 않았으면 이 구멍은 초록으로 남았다.

## 2026-11-06T09:1xZ — 표로 바꾼 뒤 같은 변이(계속 걷기 쪽)
- cmd: 같은 변이, 같은 명령
- exit: 1
- 핵심 결과: **RED** — 9 중 1 실패이고 메시지가 그 자리를 그대로 가리킨다
  (`Values differed at keys AccessBlockedProbe`). 집합이던 앞 판이 놓친 방향이 표에서는 잡힌다.

## 2026-11-06T08:1xZ — 판정 트리 표적(workflow lint 강제 · 게이트 넷)
- cmd: 라운드 3 과 같은 표적 집합
- exit: 0
- 핵심 결과: FAILED 0 · 게이트 22 task(이 판은 표 수정 **앞** 트리다 — 아래 줄이 판정 트리의 재측정이다)

## 2026-11-06T09:0xZ — 판정 트리 표적 재실행(표 수정 뒤)
- cmd: 같은 표적 집합(workflow lint 강제 · 게이트 넷)
- exit: 0
- 핵심 결과: FAILED 0 · 게이트 22 task · HEAD `cbd1eccc`(판정 SHA)

## acceptance — 전건 `check`

호스트 빌드 큐 포화(대기 12)로 팀장이 2026-11-05 에 전건 acceptance 를 **PR CI 로** 옮겼다 — 같은 명령
(`./gradlew --no-daemon check`)이 CI 의 Kotlin check job 에서 돈다. 로컬에서 큐에 걸어 둔 전건 실행은
그 공지로 내렸다(내 진행 중 실행 0 — 큐에 남은 내 랩퍼 없음을 `pgrep` 으로 확인).

첫 CI 실행(run 42008427198)은 ktlint 한 건으로 붉었고, 그 뒤 **병합 + 포트 교체 + 게이트 등재**를
담은 push 가 다음 실행을 만든다. **전건의 정본은 PR #128 의 체크 목록**이다 — evidence 를 고치는
커밋마다 CI 가 다시 도는 되돌이를 만들지 않으려고 run id 를 여기 더 적지 않는다(판정 SHA 와 그
SHA 의 체크 결과를 팀장 보고와 PR 조치 코멘트가 든다).

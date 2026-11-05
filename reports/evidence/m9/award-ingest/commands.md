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

## 2026-11-05T09:33Z — 임시 clone 에서 in_scope 19 경로만 base 로 되돌림(①②③)
- cmd: `git clone -b feature/m9-award-ingest …` → `git restore --source=79f3e5c4 --staged --worktree -- <19 경로>`
- exit: 0
- 핵심 결과: 상태 계수 `D` 11 · `M` 8(기계 산출 목록과 같다) · `git diff 79f3e5c4 -- <19 경로>` 0 줄 ·
  되돌리지 않기로 한 evidence 둘은 그대로

## 2026-11-05T15:35Z — 병합 뒤 재측정: 원천 `df4976cc`, 목록은 이 slice 의 커밋만(①②③)
- cmd: `git clone -b feature/m9-award-ingest …` → `git merge-base --is-ancestor` 둘 → `git restore --source=df4976cc --staged --worktree -- <20 경로>`
- exit: 0
- 핵심 결과: 전제 둘 참 · 상태 계수 `D` 11 · `M` 9(= 기계 산출 20 경로) · `git diff df4976cc -- <20 경로>`
  0 줄 · **evidence 를 뺀 전수 diff 가 빈 목록**(결과 트리 == 그 main 트리) · 등록부 둘에서 내 표지 0,
  main 쪽 표지 1 과 2(남의 줄 보존) · 되돌리지 않은 evidence 다섯 그대로

## 2026-11-06T00:3xZ — 되돌린 트리의 ④ compile · ⑤⑥ 영향 test 와 닿은 게이트
- cmd: `./gradlew --no-daemon --continue :workflow:compileTestKotlin :adapters:compileTestKotlin :app:compileTestKotlin` 와 `… :workflow:test --tests 'bidvector.workflow.collection.*' :adapters:test --tests 'bidvector.adapters.koneps.*' :app:test --tests 'bidvector.app.architecture.*' --tests 'bidvector.app.wiring.*' gateRegistrationGate leakPatternGate sizeGate memberEffectGate`
- exit: 0 과 0
- 핵심 결과: FAILED task 0 — 되돌린 트리가 서고 이 slice 가 닿은 게이트까지 초록이다

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

## acceptance — 전건 `check`

호스트 빌드 큐 포화(대기 12)로 팀장이 2026-11-05 에 전건 acceptance 를 **PR CI 로** 옮겼다 — 같은 명령
(`./gradlew --no-daemon check`)이 CI 의 Kotlin check job 에서 돈다. 로컬에서 큐에 걸어 둔 전건 실행은
그 공지로 내렸다(내 진행 중 실행 0 — 큐에 남은 내 랩퍼 없음을 `pgrep` 으로 확인).

첫 CI 실행(run 42008427198)은 ktlint 한 건으로 붉었고, 그 뒤 **병합 + 포트 교체 + 게이트 등재**를
담은 push 가 다음 실행을 만든다. **전건의 정본은 PR #128 의 체크 목록**이다 — evidence 를 고치는
커밋마다 CI 가 다시 도는 되돌이를 만들지 않으려고 run id 를 여기 더 적지 않는다(판정 SHA 와 그
SHA 의 체크 결과를 팀장 보고와 PR 조치 코멘트가 든다).

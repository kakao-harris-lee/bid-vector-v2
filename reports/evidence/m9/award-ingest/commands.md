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

## 2026-11-05T09:3xZ — 되돌린 트리의 ④ compile · ⑤⑥ 영향 test 와 닿은 게이트
- cmd: `./gradlew --no-daemon --continue :workflow:compileTestKotlin :adapters:compileTestKotlin :app:compileTestKotlin` 와 `… :workflow:test --tests 'bidvector.workflow.collection.*' :adapters:test --tests 'bidvector.adapters.koneps.*' :app:test --tests 'bidvector.app.architecture.*' --tests 'bidvector.app.wiring.*' gateRegistrationGate leakPatternGate sizeGate memberEffectGate`
- exit: (보강 커밋에서 기입)
- 핵심 결과: (보강 커밋에서 기입)

## acceptance — 전건 `check`

호스트 빌드 큐 포화(대기 12)로 팀장이 2026-11-05 에 전건 acceptance 를 **PR CI 로** 옮겼다 — 같은 명령
(`./gradlew --no-daemon check`)이 CI 의 Kotlin check job 에서 돈다. 로컬에서 큐에 걸어 둔 전건 실행은
그 공지로 내렸다(내 진행 중 실행 0 — 큐에 남은 내 랩퍼 없음을 `pgrep` 으로 확인). run id 와 결론은
아래 보강 커밋에 적고, **마지막 HEAD 의 정본은 PR 체크 목록**이다(evidence 를 고치는 커밋마다 CI 가
다시 도는 되돌이를 만들지 않는다).

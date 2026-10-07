# M6/6F-10 — 실행 명령과 종료 코드

base: `git merge-base HEAD origin/main`(D-6F5-30, 고정 SHA 아님). 라운드마다 재산출.
acceptance 정본은 `.github/workflows/ci.yml` 의 job 명령 그대로다 — 한 job 안에서 게이트를 좁혀 돌린 것은
돌리지 않은 것과 같게 취급한다.

**마지막 HEAD 의 게이트 결과 정본은 이 파일이 아니다** — verifier 가 판정 SHA 에서 직접 재고, 저작 레인은 PR
조치 코멘트에 적는다. 이 표는 그 직전까지만 담는다(evidence 는 자기 마지막 커밋의 post-state 를 담을 수 없다).

호스트 규율: 무거운 빌드마다 `pgrep -af 'GradleWrapperMain|GradleWorkerMain'` · `free -m`(available ≥ 6GB,
Swap free ≥ 2GB) · `ps -eo pid,rss,args --sort=-rss | head` 를 **별도 호출로 먼저** 돌려 결과를 보고 시작했다.

## 2026-10-06T13:20:00Z — 0단계(정적 판정만, 빌드 0)

- cmd: `git merge-base HEAD origin/main`
- exit: 0
- 핵심 결과: 착수 base `b137c670`(PR #62 6D-1 머지 커밋)

## 2026-10-06T13:40:00Z — 허용 목록 판정의 1차 컴파일(D-6F10-18 ⑩, 1회)

- cmd: `./gradlew --no-daemon :workflow:compileTestKotlin :adapters:compileTestKotlin :app:compileTestKotlin`
- exit: 1
- 핵심 결과: `ConsumerLeasePort`·`ConsumerTransactionPort` 가 `fun interface` 로 컴파일 거부 — SAM 변환이 메서드 자신의 타입 매개변수를 지원하지 않는다(`ConnectionSource` 가 같은 이유로 평범한 interface, D-6F10-22)

## 2026-10-06T13:45:00Z — 같은 명령, 평범한 interface 로 전환 뒤

- cmd: `./gradlew --no-daemon :workflow:compileTestKotlin :adapters:compileTestKotlin :app:compileTestKotlin`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL — 세 모듈의 main·test 소스셋 전부 컴파일

## 2026-10-06T13:50:00Z — 허용 목록 판정(0단계 §1 예측의 실측)

- cmd: `./gradlew --no-daemon :adapters:test --tests 'bidvector.adapters.relay.RelayAdapterDependencyTest' --tests 'bidvector.adapters.evaluation.EvaluationAdapterDependencyTest' --tests 'bidvector.adapters.event.EventAdapterDependencyTest' --tests 'bidvector.adapters.strategy.StrategyAdapterDependencyTest' --tests 'bidvector.adapters.persistence.PersistenceAdapterDependencyTest'`
- exit: 0
- 핵심 결과: 의존 게이트 다섯 초록 — 새 `adapters.relay` + 자기 게이트 · `adapters.evaluation` 허용 루트 한 줄 추가 · 나머지 셋 무변경

## 2026-10-06T14:10:00Z — relay use case 단위 test(fake port, mock framework 0)

- cmd: `./gradlew --no-daemon :workflow:test --tests 'bidvector.workflow.notification.RelayOutboxNotificationsTest'`
- exit: 0
- 핵심 결과: 어휘 해석표 여섯 갈래 · 고아 선격리 · Busy·억제의 claim 0 · 경계 호출 계수 4 전부 초록

## 2026-10-06T14:35:00Z — app 조립 게이트 1차(등재 전)

- cmd: `./gradlew --no-daemon :app:test --tests 'bidvector.app.architecture.*' --tests 'bidvector.app.wiring.RelayWiringTest' --tests 'bidvector.app.wiring.EvaluationCommitWiringTest'`
- exit: 1
- 핵심 결과: 새 app 클래스가 제한 층으로 분류돼 허용 목록 밖 참조로 걸림 — 조립 tier 등재가 필요하다는 판정(게이트가 요구한 등재 목록이 그 출력)

## 2026-10-06T14:50:00Z — tier·쌍·참조자 등재 뒤 app 조립 게이트

- cmd: `./gradlew --no-daemon :app:test --tests 'bidvector.app.architecture.*'`
- exit: 0
- 핵심 결과: 새 규칙(`DryRunCommitSeparationGateTest`) 양성·음성 포함 전부 초록

## 2026-10-06T15:05:00Z — 등재 등식 게이트(모집단 == 등재)

- cmd: `./gradlew --no-daemon gateRegistrationGate`
- exit: 1
- 핵심 결과: 등재 등식은 통과, 그 뒤 test 실행에서 넷 RED — payload 필드 집합 고정(새 필드 둘을 묻는 게이트가 실제로 물었다) · `workflow.event` 경계 게이트가 KDoc 의 전체 한정 어댑터 좌표를 잡음 · 커밋 배선 test 둘이 `EvaluationProperties` 빈 부재로 실패(배선 사이 숨은 의존)

## 2026-10-06T15:20:00Z — 넷 시정 뒤 배선 test

- cmd: `./gradlew --no-daemon :app:test --tests 'bidvector.app.wiring.EvaluationCommitWiringTest' --tests 'bidvector.app.wiring.RelayWiringTest'`
- exit: 0
- 핵심 결과: `mode` 없음 → 러너 빈 0 · Production 거부 · 환경·owner·상한·여력 미설정의 기동 실패 전부 초록

## 2026-10-06T16:00:00Z — adapters 전건(실 DB·E2E 포함, `check` 전건의 일부로 실행)

- cmd: `./gradlew --no-daemon check` (그중 `:adapters:test`)
- exit: 0 (그 task)
- 핵심 결과: test 클래스 132, 실패·오류 0 — 새 relay DB test 6 · 임대 test 4(실 연결 둘) · 의존 게이트 4 · claim 경합 재작성 3 · codec 13 · 내부 폐쇄 probe 8 · production relay 로 교체한 E2E 둘 포함

## 2026-10-07T00:52:00Z — `check` 전건 초록

- cmd: `./gradlew --no-daemon check`
- exit: **0**
- 핵심 결과: 전건 통과(FAILED task 0) — 커밋은 이 트리에서 만들었다

## 2026-10-07T01:05:00Z — 변이 실측 열넷 (산출물 커밋 뒤, 각각 `git diff --numstat` 으로 적용 확인)

- cmd: 변이마다 `mutate.sh <n>` → `./gradlew --no-daemon <표적 test>` → `revert.sh`
- exit: 표적 test 전부 비-0 (= RED)
- 핵심 결과: 열넷 전부 RED — 상세 표는 구현 보고(`_workspace/m6-6f10/03_impl_report.md`)에 있고, 변이 10 의 세 모양(람다 본문·클래스 리터럴·소거 제네릭)은 **게이트가 전부 잡았다**(사각 아님)

## 2026-10-07T01:30:00Z — 비밀값 스캔 (참조형, 패턴 어휘를 여기 적지 않는다)

세 번 돌렸다 — 대상을 좁혀 가며 「무엇이 매치하는가」를 가른다.

- cmd: `grep -rniE -f config/quality/leak-patterns.txt <이 slice 가 변경한 파일 59개> reports/evidence/m6/6f10/`
- exit: 0 (매치 8)
- 핵심 결과: 여덟 전부 **이 slice 가 더하지 않은 기존 줄**이다 — 게이트 정책 파일의 키 **이름**과 그 키를 읽는 접근자뿐이고(값이 아니다) 이 레인은 그 파일의 다른 자리를 편집했다

- cmd: 같은 패턴 파일로 **이 slice 가 더한 줄만**(`git diff <base>..HEAD` 의 `+` 줄 4026 줄) 스캔
- exit: 1
- 핵심 결과: 매치 없음 = 이 레인이 새로 들인 어휘는 0

- cmd: 같은 패턴 파일로 `reports/evidence/m6/6f10/` 만 스캔
- exit: 1
- 핵심 결과: 매치 없음. 루트 `leakPatternGate`(scanRoot 가 이 디렉터리)도 `check` 전건에서 통과했다

패턴으로 못 잡는 축(연락처·사업자 정보)은 육안 확인 — 이 slice 의 로그 줄 입력 타입은 계수와 열거값만 나르고, payload 투영에 대상 값이 없다(대상은 설정에서 오는 owner·channel 뿐이고 로그에 싣지 않는다)

## 2026-10-07T01:35:00Z — clean-tree 게이트 (양성 대조 포함)

- cmd: `git status --porcelain -- <in_scope 경로 개별 인자>`
- exit: 0
- 핵심 결과: 빈 출력. 양성 대조 한 번 — in_scope 파일 하나에 줄을 심어 잡히는 것을 확인하고 **비파괴 절삭**으로 되돌렸다(`git checkout --` 를 쓰지 않는다 — 같은 파일의 미커밋 편집을 함께 지운다)

## 2026-10-07T01:20:00Z — rollback 실측 (버릴 clone, 실측 HEAD `cf59db87`)

- cmd: `git restore --source=<base> --staged --worktree -- "${P[@]}"` 뒤 `./gradlew --no-daemon check`
- exit: restore 0 · compile 0 · check **0**
- 핵심 결과: `D` 30 · `M` 28, 되돌린 경로의 base 대비 diff 빈 출력, 되돌리지 않은 셋은 HEAD 그대로. 세부는 `rollback.md`

## 2026-10-07T04:30:00Z — 수정 라운드 1: `check` 전건

- cmd: `./gradlew --no-daemon check`
- exit: **0**
- 핵심 결과: 전건 통과. 라운드 중 세 번 붉었고 전부 게이트가 잡은 것이다 — detekt `TooGenericExceptionCaught`(임대 해제의 `Throwable` 포획) · `ReturnCount`(임대 상실 분기) · ktlint 체인·정렬 여섯 자리

## 2026-10-07T05:10:00Z — 수정 라운드 1: 변이 재측정 (r1 에서 초록이던 둘 + 새 처분 넷)

- cmd: 변이마다 적용 → `git diff --numstat` → 표적 test → `git checkout --` (산출물 전부 커밋 뒤)
- exit: 아래 표
- 핵심 결과: **C·12b 가 RED 로 전환**(r1 의 초록 둘) · L·L3·M4 RED · **G1 단독은 초록**이고 정책표 변이를 함께 걸어야 갈린다(그 둘을 따로 재서 사각을 재현했다)

| 변이 | numstat | 결과 | 잡은 단언 |
|---|---|---|---|
| C 커밋 조립이 outbox 대신 기록 port | 1/6 | **RED**(r1 초록) | 변한 표 `{outbox}` → `{}` · 종료 코드 2 → 0 |
| 12b 고아 조회 kind 필터 제거 | 1/1 | **RED**(r1 초록) | 다른 종류 고아 격리 0 → 1 |
| L 행마다 임대 생존 확인 제거 | 0/4 | RED | fake 둘 + 실 DB 하나(임대 끊고 LeaseLost 기대) |
| L3 `INCOMPLETE` 조건을 앞 판으로 | 1/1 | RED | 전량 거부 run 의 종료 코드 2 → 0 |
| M4 전략 고정 제거 | 1/1 | RED | 읽기 1 → 2 (test 를 `evaluate()` 까지 돌리게 고친 뒤) |
| G1 거부 술어를 enum 이름으로 | 1/1 | **초록** | 없음 — 오늘 두 술어가 같은 답을 낸다 |
| G1 + 정책표 `Staging→Live` | 2/2 | **초록**(= 사각 재현) | 없음 — Staging 이 `Live` 인데 기동한다 |
| 정책표 `Staging→Live` 만(새 술어) | 1/1 | RED | 「Live 가 아닌 환경 셋은 기동한다」 |

마지막 두 줄이 cr G-1 의 요점이다 — 새 술어는 표가 바뀌는 날 거부하고, 앞 판 술어는 통과시킨다. 그 차이는 **정책표를 함께 변이해야** 보인다.

## 2026-10-07T05:40:00Z — 수정 라운드 1: rollback 재실측 (버릴 clone, 실측 HEAD `63a15fda`)

- cmd: 목록 재산출 → `git restore --source=<base> --staged --worktree -- "${P[@]}"` → compile → `check`
- exit: restore 0 · compile 0 · check **0**
- 핵심 결과: `D` 36 · `M` 28(합 64), 되돌린 경로의 base 대비 diff 빈 출력, 되돌리지 않은 일곱(ADR 둘·마일스톤·evidence 넷)은 HEAD 그대로. 앞 라운드 실측(`cf59db87`)은 옮기지 않고 버렸다

## 2026-10-07T05:50:00Z — 수정 라운드 1: 비밀값 스캔 · clean-tree

- cmd: 참조형 패턴 스캔을 **이 라운드가 더한 줄**(1441 줄)에
- exit: 0 (매치 2)
- 핵심 결과: 둘 다 새 E2E test 의 **test 컨테이너 자격 리터럴**이고, 이 저장소의 다른 app E2E 열 곳이 쓰는 것과 **같은 형태**다(그 형태를 그대로 옮겼다). 실 비밀값이 아니다. 정본 게이트(`leakPatternGate`, scanRoot 가 evidence)는 `check` 전건에서 통과했고 evidence 디렉터리 직접 스캔도 매치 0(exit 1)

- cmd: `git status --porcelain -- <in_scope 경로 개별 인자>` + 양성 대조 1회(비파괴 절삭)
- exit: 0
- 핵심 결과: 빈 출력. 양성 대조에서 심은 줄이 `M` 으로 잡히고 `head -n` 절삭으로 복원됨(`git checkout --` 을 쓰지 않는다)

## 2026-10-07T06:40:00Z — 수정 라운드 2: `check` 전건

- cmd: `./gradlew --no-daemon check`
- exit: **0**
- 핵심 결과: workflow 50 클래스 422 · adapters 134 클래스 885 · app 61 클래스 550, 실패 0. 라운드 중 네 번 붉었고 전부 게이트가 잡은 것이다 — detekt `ReturnCount`(임대 상실 분기 셋) · ktlint `when-entry-bracing`·`no-consecutive-comments` · `sizeGate` 파일 500줄(투영 등식 test 를 더해 527) · `gateRegistrationGate` 미등재 test 둘

## 2026-10-07T07:05:00Z — 수정 라운드 2: 변이 실측

- cmd: 변이마다 적용 → `git diff --numstat` → 표적 test → `git checkout --` (산출물 전부 커밋 뒤)
- exit: 아래 표
- 핵심 결과: 새 변이 **아홉 전부 RED**. 초록이 둘 나왔고 둘 다 **의도한 음성 대조**다(R2-7b 는 옛 도출의 사각 재현, R2-10 전 단계는 순서가 유리해 비우기가 짐을 안 지던 것 — 그래서 순서를 고정했다)

| 변이 | numstat | 결과 | 잡은 단언 |
|---|---|---|---|
| R2-1 임대 재확인 세 지점 제거(행 루프 하나로) | 1/12 | **RED** | fake 넷 + 실 DB 하나(획득 직후 상실에서 상태 분포 불변) |
| R2-2 투영 두 줄을 상수로 | 4/2 | **RED** | 투영 등식 test · 커밋 E2E 의 정책 출처 축어 |
| R2-2b 개정만 `StrategyRevision(1)` | 2/1 | **RED** | 커밋 E2E 거동 축(개정을 올려도 payload 가 안 바뀐다) |
| R2-3 기동 거부를 환경 이름 술어로 | 1/1 | **RED**(r1 의 G1 초록이 전환) | 변이표 test 둘(Staging 거부 · Production 허용) |
| R2-4 `INCOMPLETE` 앞 항 제거 | 1/1 | **RED** | 앞 항 단독 표본(전달 1 + 미지 payload 1) |
| R2-5 커밋 러너가 예외를 재던짐 | 1/2 | **RED** | 예외 경로 둘(종료 코드 1 · 실패 줄) |
| R2-6 억제를 `COMPLETE` 로 사상 | 1/1 | **RED** | 러너 성공 경로 |
| R2-6b 러너가 성공 경로에서 종료를 안 부름 | 0/1 | **RED** | 러너 성공 경로만(`RelayExitCodeTest` 12 는 초록 — cr R-5 의 요점) |
| R2-7a 중첩 전용 참조자 추가(도출 접기 유지) | 신규 1 파일 | **RED** | 모집단 등식이 등재를 요구 |
| R2-7b 같은 입력 + 옛 도출(`$` 버리기) | 3/2 | **초록**(= cr R-4 사각 재현) | 없음 — 등식이 초록인 채 등재를 건너뛴다 |
| R2-8 억제 판정 제거 | 0/4 | **RED** | 억제 환경 claim 0·격리 0 |
| R2-9 claim 을 자기 트랜잭션 밖으로 | 1/1 | **RED** | 경계 호출 계수(4 → 3) |
| R2-10 커밋 E2E 의 표 비우기 제거 | 0/1 | **RED**(순서 고정 뒤) | 뒤 두 test 가 두 행을 본다 |

R2-7 두 줄과 R2-10 이 이 라운드의 교훈이다 — **방어를 더한 것만으로는 측정이 되지 않는다.** 앞 것은 사각을 보이는 입력(중첩 전용 참조자)을 함께 만들어야 갈리고, 뒤 것은 불리한 순서를 고정해야 짐을 진다.

## 2026-10-07T07:30:00Z — 수정 라운드 2: rollback 재실측 (버릴 clone, 실측 HEAD `c037c45e`)

- cmd: 목록 재산출 → `git restore --source=<base> --staged --worktree -- "${P[@]}"` → compile → `check`
- exit: restore 0 · compile 0 · check **0**
- 핵심 결과: 복원 목록 69 · `D` 40 · `M` 29(합 69), 되돌린 경로의 base 대비 diff 빈 출력, 되돌리지 않은 일곱(ADR 하나·data-dictionary 하나·마일스톤 하나·evidence 넷)은 HEAD 그대로. 앞 라운드 실측(`63a15fda`)은 옮기지 않고 버렸다. 세부는 `rollback.md`

## 2026-10-07T07:40:00Z — 수정 라운드 2: 비밀값 스캔 · clean-tree

- cmd: 참조형 패턴 스캔을 **이 라운드가 더한 줄**(900 줄)에
- exit: 0 (매치 4)
- 핵심 결과: 둘은 **일부러 심은 가짜 접속 문자열과 그것을 쓰는 단언**이다 — 커밋 러너의 실패 줄이 예외 메시지를 싣지 않음을 재는 입력이고, 그 값이 로그에 **없다**는 것이 바로 그 test 다. 나머지 둘은 test 컨테이너 자격(리터럴 하나 + 컨테이너가 생성한 값 읽기)이고 이 저장소의 다른 app E2E 와 같은 형태다. 실 비밀값 0. 정본 게이트(`leakPatternGate`, scanRoot 가 `reports/evidence`)는 `check` 전건에서 통과했고 evidence 디렉터리 직접 스캔도 매치 0

- cmd: `git status --porcelain -- <in_scope 경로 개별 인자>` + 양성 대조 1회(비파괴 절삭)
- exit: 0
- 핵심 결과: 빈 출력. 양성 대조에서 심은 줄이 `M` 으로 잡히고 `head -n` 절삭으로 복원됨(`git checkout --` 을 쓰지 않는다)

**마지막 HEAD 의 게이트 결과 정본은 이 파일이 아니다** — verifier 가 판정 SHA 에서 직접 재고, 저작 레인은 PR 조치 코멘트에 적는다(evidence 가 자기 마지막 커밋의 post-state 를 담으려 하면 커밋이 또 생긴다).

## 2026-10-07T07:55:00Z — 수정 라운드 2 보강: 투영 등식의 표본 값을 양쪽으로 측정

- cmd: 변이 `strategyRevision → StrategyRevision(1)` 적용 → `git diff --numstat` → `:workflow:test --tests '*OutboxNotificationRequestPortTest'` → 표본만 1 로 바꿔 재실행 → `git checkout --`
- exit: 변이 단독 **1**(RED) · 변이 + 표본 1 **0**(초록)
- 핵심 결과: 표본 **7**(지원 함수 기본값은 1)에서 상수 1 변이가 투영 등식 test 를 붉히고, 표본을 1 로 내리면 **같은 변이가 통과한다**. 거동 축의 두 값(7 → 11)도 둘 다 비기본값이고 서로 다르다 — 그 전제가 이 두 축을 지탱한다는 것을 양쪽으로 쟀다

## 2026-10-07T09:10:00Z — 승인 전 일괄: `check` 전건 · 변이 셋

- cmd: `./gradlew --no-daemon check`
- exit: **0**
- 핵심 결과: 전건 통과. 이 일괄은 산출물 한 커밋(`2bc08209`)이고 붉은 자리 없이 지났다

- cmd: 변이마다 적용 → `git diff --numstat` → 표적 test → `git checkout --` (산출물 커밋 뒤)
- exit: 아래 표
- 핵심 결과: 셋 **전부 RED**. ①은 fake 둘 + 실 DB 하나, ②는 verifier r3 가 초록을 실측한 그 변이가 이제 붉다, ③은 억제 환경 한 건이 순서를 잡는다

| 변이 | numstat | 결과 | 잡은 단언 |
|---|---|---|---|
| T1 고아마다 임대 확인 제거(앞 판 `forEach` 로) | 2/5 | **RED** | fake 둘(격리 도중 상실 · claim 전 상실) + 실 DB 하나(`{ISOLATED=1, CLAIMED=1}` 보존) |
| B3 배선만 옛 이름 술어로 | 1/1 | **RED**(verifier r3 에서는 초록 550/550) | 배선 구조 단언(함수 facade 의존 + 열거 상수 접근 0) |
| P1s 임대 확인과 억제 판정 순서 교환 | 4/4 | **RED**(verifier r3 에서는 상설 0) | 억제 환경 첫 지점 fake |

cr T-4(대상 쪽 접기)는 **변이로 재지 않았다** — 오늘 커밋 조립에 중첩 타입이 없어 사각이 도달 불가다. 재려면 중첩 타입과 그것을 이름으로 참조하는 dry-run 쪽을 **함께** 심어야 하고, 그 모양의 양쪽 측정은 라운드 2 의 R2-7 에서 이미 했다(접기 RED · 옛 도출 초록). 여기서 한 것은 세 자리 가운데 마지막 자리를 같은 모양으로 맞춘 것이다

## 2026-10-07T09:30:00Z — 승인 전 일괄: rollback 재실측 (버릴 clone, 실측 HEAD `2bc08209`)

- cmd: 목록 재산출 → `git restore --source=<base> --staged --worktree -- "${P[@]}"` → `--rerun-tasks` compile → `check`
- exit: restore 0 · compile 0 · check **0**
- 핵심 결과: 복원 목록 69 · `D` 40 · `M` 29, 되돌린 경로의 base 대비 diff 빈 출력, 되돌리지 않은 일곱 경로는 HEAD 그대로. 세부는 `rollback.md`

## 2026-10-07T09:35:00Z — 승인 전 일괄: 등재 정렬 전 블록 확인 (cr T-5)

- cmd: `gate-tests.properties` 의 모든 블록을 서브패키지 그룹 안 사전순으로 기계 확인
- exit: 0
- 핵심 결과: **이 slice 의 패키지 아홉은 어긋남 0**. 그 밖에 **여덟 자리**가 어긋나 있고 전부 이 slice 밖 패키지(`app.collection`·`adapters.persistence`·`adapters.snapshot`·`procurement`·`workflow.collection`)다 — in_scope 가 이 파일을 「추가만」으로 한정하므로 고치지 않고 사실로 남긴다. cr T-5 의 제안(게이트에 「블록 안이 사전순」 단언)은 `build-logic/` 변경이라 in_scope 밖이다

## 2026-10-07T12:00:00Z — PR #63 조치: `check` 전건 · 변이 다섯

- cmd: `./gradlew --no-daemon check`
- exit: **0**
- 핵심 결과: 전건 통과. 라운드 중 여섯 번 붉었고 전부 게이트가 잡은 것이다 — detekt `UtilityClassWithPublicConstructor`(companion 전용 설정 클래스) · ktlint 넷(체인·고아 KDoc·import 순서·빈 줄) · `sizeGate` 파일 500줄(임대 상실 test 를 갈랐다) · `gateRegistrationGate` 미등재 둘 · 아키텍처 게이트 다섯(바깥 패키지·러너 참조·반사 쌍·죽은 등재 둘·조립 tier)

| 변이 | numstat | 결과 | 잡은 단언 |
|---|---|---|---|
| F1 guard 를 「셋 이상」으로 | 1/1 | **RED** | 둘 켜면 실패 · 임의 러너 하나로도 둘 |
| F2 `orphansIsolated` 갈래 제거 | 1/1 | **RED** | 가운데 항 단독 표본 · 완료 줄의 exit |
| F3 잠금 인식 probe 를 `SELECT 1` 로 되돌림 | 1/3 | **초록** | 없음 — 아래 사유 |
| F4 부분 집계를 싣지 않고 통과 | 5/14 | **RED** | 집계 단언 둘(port 실패 · 도중 터짐) |
| F5 걷어낸 중복 `stillHeld()` 되돌려 넣기 | 1/3 | **RED** | 임대 상실 test 넷(서수가 밀린다) |

**F3 이 초록인 사유를 숨기지 않는다.** 두 probe 를 가르는 입력은 「연결은 살아 있는데 그 backend 가 잠금을 들고 있지 않다」이고, 그것은 **pooler 뒤에서만** 생긴다 — 이 저장소의 test 는 직접 연결이고 pooler 를 지원하지도 않는다(알려진 제한 20). 기존 상실 test 들은 백엔드를 **종료**하므로 두 probe 가 똑같이 거짓을 낸다. 그래서 이 변경은 변이로 갈리지 않고, 재는 것은 ⓐ 기존 상실 test 가 새 probe 에서도 그대로 상실을 잡는다(초록 = 잡는다) ⓑ 제한 20 이 그 경계를 문서로 선언한다 둘이다. cr T-4·R2-7 과 같은 계열(도달 불가 사각)이다

## 2026-10-07T12:30:00Z — PR #63 조치: rollback 재실측 (버릴 clone, 실측 HEAD `3db918ab`)

- cmd: 목록 재산출 → `git restore --source=<base> --staged --worktree -- "${P[@]}"` → `--rerun-tasks` compile → `check`
- exit: restore 0 · compile 0 · check **0**
- 핵심 결과: **base 가 `b137c670` → `2865c9b8` 로 전진했다**(브랜치가 main 을 병합, `c3024eb8`) — 목록·셈·공유 파일 커밋 목록을 전부 새 base 로 다시 냈다. 복원 목록 73 · `D` 44 · `M` 29, 되돌린 경로의 base 대비 diff 빈 출력, 되돌리지 않은 일곱 경로는 HEAD 그대로. 세부는 `rollback.md`

# M7/7C⑤-0 — 실행한 명령과 결과

base `a61ddff1` · **판정 SHA(마지막 산출물 커밋) `662129a0`** · 브랜치 `m7-7c5p/2026-10-23`.
모든 Gradle·docker 명령은 `flock ~/.local/bid-vector-build.lock` 아래 직렬로 돌았다(호스트 공유).
종료 코드는 각 명령에서 직접 찍었다 — 파이프 `grep` 으로 판정하지 않았다.

## 1. acceptance — Kotlin `check` job

| 명령 | 결과 |
|---|---|
| `./gradlew --no-daemon check` (판정 SHA `662129a0`) | **exit 0** · `BUILD SUCCESSFUL in 9m 47s` |

`.github/workflows/ci.yml` 의 `check` job 이 드는 게이트 전부가 이 한 명령 안이다 — 컴파일 ·
ktlint · detekt · sizeGate · cpd · domainDependencyGate · architecture test · contractGate ·
jarContentGate · gateRegistrationGate · gateExecutionGate · kover · compatibilitySmoke, 그리고
`:procurement:test`·`:workflow:test`·`:adapters:test`(실 DB, Testcontainers)·`:app:test`.

같은 job 의 뒤 step 둘은 **게이트가 아니다**: `qualityBaseline` 은 측정이고(실패시키지 않는다),
`tools/one-command-check.sh` 는 Node·브라우저 전제를 요구한다 — 이 레인에서 돌리지 않았고 그
사실을 여기 적는다.

**게이트가 먼저 붉어진 실측(자연 발생 RED)** — 정책을 건드리기 **전에** 실제 코드에서 붉었고,
전부 코드·구조를 고쳐 닫았다(허용 목록을 느슨하게 한 곳 0). 이것은 **test RED 가 아니다**.

| 게이트 | 무엇을 가리켰나 | 어떻게 닫았나 |
|---|---|---|
| `ArchitectureGateTest` domain 순수성 | `uppercase()` 가 `java.util.Locale.ROOT` 를 끌어왔다 | 대소문자를 열거로 받는다(프로토콜 플래그에 로케일 접기를 쓰지 않는다) |
| `CollectionArchitectureGateTest` 참조 집합 | use case 가 canonical fact 를 열었다 | 승격 결과가 공고 식별자를 나르게 해 참조를 없앴다 |
| `CollectionArchitectureGateTest` 로거 집합 | 미등재 로거 사용 | 로그 출구를 만드는 배선 집합에 등재 |
| `TransportSurfaceGateTest` 쌍 등식(2건) | 실행 상태 경로를 짓는 자리 | 타입 쌍 · 숨은 송신 멤버 쌍 등재(형제 배선과 같은 모양) |
| `AppHttpDependencyGateTest` 면제 집합·크기 | 미분류 app 클래스 · 면제 수 | ① 층·③ 층 분류 + 래칫 62→65(설정 타입은 넣지 않았다) |
| `jarContentGate` | inline 비교자 클래스가 stdlib 소스로 찍혔다 | 이름 있는 `Comparator`(이미 이 저장소의 관례) |
| `detekt` 파일당 함수·return 수 · `sizeGate` 파일 500줄 | 한 파일 16 함수 · test 756줄 | **책임 셋**으로 분할(production·test 같은 경계) + 금액 조립 셋을 하나로 |

## 2. `container` job

| 명령 | 결과 |
|---|---|
| (아래 §5 에서 채운다) | |

## 3. 변이 실측 — 바꿔치우기 14건

**RED 를 별 라운드로 관측하지 못했다**(사실 선언): test 와 구현을 함께 썼고 첫 실행이 초록이었다.
그래서 test 강도는 **변이**가 진다. 변이는 전부 **바꿔치우기**이고(더하기만 한 변이는 초록으로
지나간다) 음성 대조 하나를 함께 돌렸다. 변이 전에 산출물이 커밋돼 있다 — 되돌림이
`git checkout --` 이라 미커밋 산출물을 함께 지운다.

| 변이 | 기대 | 관측 | 붉어진 자리 |
|---|---|---|---|
| M0b 비교자 개명(선언 + 호출부) — **음성 대조** | GREEN | **GREEN** | 거동 0 이면 초록이다(suite 가 무관한 이유로 붉지 않다) |
| M1 걷기를 원장이 아니라 행 시각으로 고른다 | RED | **RED** | 걷기 선택 test · use case 걷기 test |
| M2 부재인 셈을 0 으로 적재한다 | RED | **RED** | 사상 표 test(부재·공백·음수·해석 불가) |
| M3 금액의 소수부를 깎는다 | RED | **RED** | 사상 표 test · 1위 축 투찰금액 |
| M4 멱등 술어를 항진식으로 | RED | **RED** | 멱등 술어 test · use case 둘째 실행 test |
| M5 추첨번호 축을 비우지 않는다 | RED | **RED** | 추첨번호 부재 test(D-7C5P-2) |
| M6b 1위 축의 전부-아니면-무를 없앤다 | RED | **RED** | `상호를 얻지 못한 투찰 행이 있으면 1위 축을 비운다` |
| M7 마스킹 표지 제외를 없앤다 | RED | **RED** | 실 DB 마스킹 제외 test |
| M8 보존 창 하한을 무력화한다 | RED | **RED** | 실 DB 보존 창 test |
| M9 확정 표본 필터를 없앤다 | RED | **RED** | 표본 밖 test · 계수 항등식 test |
| M10 canonical 저장소 생성을 한 걸음 옮긴다 | RED | **RED** | 생성 자리 등식 test |
| M11 로그 줄의 두 계수를 맞바꾼다 | RED | **RED** | 러너 줄 배선 test |
| M12b 축 선언에 계약에 없는 축을 넣는다 | RED | **RED** | `선언한 축은 그 개념의 계약 presentIn 안이다` |
| M13 domain 이 로케일을 끌어온다 | RED | **RED** | `ArchitectureGateTest` domain 순수성 |

**1라운드의 어긋남 셋과 그 원인**(숨기지 않는다 — 둘은 측정의 결함이었고 하나는 패치의 결함이었다).

- **M0 음성 대조는 패치가 틀렸다**: 선언만 개명하고 호출부 둘을 남겨 **컴파일이 깨졌다**. 「거동 0」이
  아니라 「빌드 0」이었으므로 음성 대조가 아니었다 — 선언과 호출부를 함께 바꾸는 M0b 로 다시 쟀다.
- **M6·M12 는 결함이 아니라 필터 빗나감**: 그 두 방어의 test 가 파일 분할로
  `OpeningPromotionRankOneTest` 로 옮겨갔는데 1라운드 필터가 `OpeningPromotionTest` 만 돌렸다.
  필터를 승격 test 전수로 넓혀 다시 쟀고(M6b·M12b) 붉어진 test 이름을 함께 기록했다.
- 교훈: **변이의 필터는 파일 분할을 따라가야 한다.** 좁은 필터로 초록을 받으면 「방어가 없다」와
  「재지 않았다」가 같은 값이 된다.

## 4. rollback ①~⑥ 실측

`rollback.md` 가 목록과 판정을 담는다. 실측은 **버릴 clone** 에서 돌았고 작업 트리를 건드리지 않았다.

## 5. 호스트 규율

모든 라운드 전에 점검 셋을 **별도 호출로 먼저** 돌렸다 — `pgrep -af 'java.*[Gg]radle'`(java 만 센다:
남의 bash 대기 루프를 세면 오탐이다) · `free -m` · `ps -eo pid,rss,args --sort=-rss | head`.
available 6GB 이상 · swap free 2GB 이상에서만 시작했고, 00:30~03:00 KST 에는 시작하지 않았다.

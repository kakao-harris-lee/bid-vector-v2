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

## 2. `container` job 전체 (D-7C5P-9)

손 전사를 쓰지 않는다. **기계 재생기**(COL-03 레인 산출물의 **무편집 사본** — 원본과 바이트 동일)가
`ci.yml` 의 `jobs.container` 를 파싱해 `run:` 블록을 **바이트 그대로** GitHub 기본 셸로 순서대로
돌리고, `if:` 는 앞 step 결과로 평가하며 `GITHUB_ENV`·`GITHUB_PATH`·`RUNNER_TEMP` 를 에뮬레이트한다.
그래서 측정 대상이 내 전사본이 아니라 **CI YAML 자체**다. `uses:` 넷은 runner 전용이라 실행하지 않고
`if:` 평가만 기록한다. `trivy` 는 **job 자신의 `install_trivy` step 이 설치**한다(미리 깔지 않았다).

| 재는 것 | 결과 |
|---|---|
| step 수 | **27** (run 23 · uses 4) |
| run step 결과 | **success 23 · failure 0** (각 step 종료 코드 0) |
| uses step | 4 — runner 전용, 실행하지 않고 `if:` 평가만 기록 |
| skipped | **0** — `scan_app`·`scan_web`·`scan_proxy` 의 `if:` 셋이 모두 참으로 평가돼 실행·성공 |
| JOB | **success** |

이미지 태그 기록과 복원(실행 전 ID 를 적고 끝나서 되돌린다):

| 태그 | 실행 전 | 실행 후 | 조치 |
|---|---|---|---|
| app | `94382c10` | `6faa81e1` | 실행 전 ID 로 복원(exit 0) |
| web | `85517138` | `fc8869f6` | 실행 전 ID 로 복원(exit 0) |
| proxy | `d93e25e0` | 변화 없음 | 불필요 |
| ml-serving | `326e9da3` | 변화 없음 | 불필요 |

정리 확인: compose 컨테이너 **0** · 볼륨 **0**. 6G dev DB 컨테이너는 **손대지 않았고** 실행 전후로 Up
상태 그대로다. app 이미지 ID 가 바뀐 것이 「이 slice 의 변경이 실제로 배포물에 닿는다」의 실측이다.

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

## 3b. 검토 r1 수정 뒤 재측정 (D-7C5P-10)

| 재는 것 | 자리 | 결과 |
|---|---|---|
| acceptance | 산출물 SHA `73f31710` | `./gradlew --no-daemon check` **exit 0** · 9m33s |
| 변이 전수 | 같은 SHA | **18/18 기대와 일치** — 바꿔치우기 17 RED + 음성 대조 1 GREEN |
| 회귀 test 의 구판 RED | 버릴 clone `662129a0` + **새 test 만** 얹음 | 셋 FAILED(아래) |
| container job | 같은 SHA | JOB success(§2) |
| rollback ①~⑥ | 같은 SHA | §4 |

**회귀 test 가 구판에서 RED 인가** — 수정 전 코드에 새 test 파일만 얹어 쟀다. 순수 층 쌍은 F1·F2 를
고치며 서명이 바뀌어 구판에 얹히지 않으므로, 같은 결함을 **use case 층**에서 재는 쌍으로 쟀다.

```
git clone --no-hardlinks <worktree> clone && cd clone && git checkout 662129a0
cp <새 workflow use case test> <새 procurement 사상 표 test> 제자리에
./gradlew --no-daemon :workflow:test --tests '*PromoteOpeningResultsUseCaseTest' --rerun
./gradlew --no-daemon :procurement:test --tests 'bidvector.procurement.OpeningPromotionTest' --rerun
```

| 회귀 test | 구판 결과 |
|---|---|
| `결말 없이 끊긴 상세 축의 행은 승격하지 않는다`(F1) | **FAILED** |
| `원장 걷기의 행이 창 밖이고 끊긴 재걷기 행만 남아도 실행이 던지지 않는다`(F2) | **FAILED** |
| `둘러싼 공백은 문자열 슬롯에 실리지 않는다`(trim) | **FAILED** |

F3·CR HIGH(잠금)와 확정 부재는 **결함이 아니라 미측정**이었으므로 구판 RED 가 없다 — 그 강도는
변이 `Mlock`(앞 라운드에서 GREEN 이던 것이 이제 RED)과 확정 축 변이가 진다.

## 3c. 변이 재현 명령 (L2)

변이는 스크립트 하나가 돌린다 — **적용 → 좁은 task 실행 → `git checkout --` 로 되돌림**을 변이마다
반복하고, 패치는 「정확히 한 번 출현」을 요구해 빗나간 패치가 조용히 지나가지 않는다. 산출물이 먼저
커밋돼 있어야 한다(되돌림이 미커밋 산출물을 함께 지운다).

필터는 **분할 뒤 클래스 전수**다(1라운드의 교훈):

```
:procurement:test --tests 'bidvector.procurement.OpeningPromotion*'
:workflow:test    --tests 'bidvector.workflow.collection.PromoteOpeningResultsUseCaseTest'
:adapters:test    --tests 'bidvector.adapters.persistence.OpeningPromotion*'
:app:test         --tests 'bidvector.app.collection.OpeningPromotion*' \
                  --tests 'bidvector.app.wiring.OpeningPromotionWiringTest'
```

| 변이 | 바꿔치우는 것 | 결과 |
|---|---|---|
| M0b 음성 대조 | 비교자 이름(선언 + 호출부) | GREEN(기대 GREEN) |
| MF1 | 끊긴 걷기 갈래를 없앤다 | RED |
| MF2 | 빈 선택 축을 맵에 남긴다 | RED |
| Mtrim | 문자열 슬롯의 trim | RED |
| Mlock | 러너의 잠금 래퍼 | **RED**(앞 라운드 GREEN → 닫혔다) |
| M1 | 걷기를 행 시각으로 | RED |
| M2 | 부재인 셈을 0 으로 | RED |
| M3 | 금액 소수부 절삭 | RED |
| M4 | 멱등 술어를 항진식으로 | RED |
| M5 | 추첨번호 축을 채운다 | RED |
| M6 | 1위 축 전부-아니면-무 제거 | RED |
| M7 | 마스킹 표지 제외 제거 | RED |
| M8 | 보존 창 하한 무력화 | RED |
| M9 | 확정 표본 필터 제거 | RED |
| M10 | 저장소 생성을 한 걸음 옮김 | RED |
| M11 | 로그 두 계수 맞바꿈 | RED |
| M12 | 축 선언에 계약에 없는 축 | RED |
| M13 | domain 이 로케일을 끌어옴 | RED |

붉어진 test 이름은 변이마다 기록했다 — 「붉었다」만으로는 그 방어를 쟀다는 증거가 아니다.

## 4. rollback ①~⑥ 실측

`rollback.md` 가 목록과 판정을 담는다. 실측은 **버릴 clone** 에서 돌았고 작업 트리를 건드리지 않았다.

## 4b. 잔존 상한 180일의 유도 — 빠진 두 항 (L4)

「보존 창의 두 배」는 **승격이 더하는 지연**만 센 값이다. 실제 잔존에는 두 항이 더 붙는다.
ⓐ **파기 실행 주기** — 파기는 즉시가 아니라 배치라 그 주기가 더해진다. ⓑ **정책 해석의 기준일** —
보존 정책이 실행일이 아니라 운영자 입력 `reference-date` 로 해소되므로, 정책 판이 바뀌면 승격 창과
마스킹 창이 갈릴 수 있다. 유도 자체(적재 시각 하한 = `now − 창` → 승격 지연 ≤ 창 → canonical 행
`created_at` + 창 파기)는 코드와 일치한다. 두 항은 점검표 제한 15 에 등재했다.

## 5. 호스트 규율

모든 라운드 전에 점검 셋을 **별도 호출로 먼저** 돌렸다 — `pgrep -af 'java.*[Gg]radle'`(java 만 센다:
남의 bash 대기 루프를 세면 오탐이다) · `free -m` · `ps -eo pid,rss,args --sort=-rss | head`.
available 6GB 이상 · swap free 2GB 이상에서만 시작했고, 00:30~03:00 KST 에는 시작하지 않았다.

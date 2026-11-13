# M11 / opening-resume-total — 실행 명령과 종료 코드

착수 base `94ee445b` · 브랜치 `feature/m11-opening-resume-total` · 병합한 main `32dead40` · 전부 worktree
`bid-vector-v2-m11-opening-resume-total` 에서 돌았고 Gradle 은 호스트 빌드 자리 래퍼(`bv-build.sh`)를 지난다.
시각은 **완료 시각**(UTC)이다. **마지막 HEAD 의 게이트 결과 정본은 verifier 와 PR 조치 코멘트**다.

## 표적 실행

## 2026-11-13T02:00Z — RED (구현 전)
- cmd: `./gradlew --no-daemon :workflow:test --tests '…OpeningListResumeTotalTest' --rerun`
- exit: 1
- 핵심 결과: 1 중 1 실패 `expected:<7> but was:<0>` — 재개 둘째 배치의 0 이 앞 총수를 덮었다.
  **그 앞의 커서 단언은 통과**했다(걷기가 두 배치로 실제 재개됐다 — 한 배치로 끝났다면 보호 없이도
  총수 단언이 통과한다)

## 2026-11-13T02:05Z — GREEN (구현 뒤)
- cmd: `… :workflow:test --tests 'bidvector.workflow.collection.*' --rerun`
- exit: 0
- 핵심 결과: 수집 갈래 118 통과·실패 0(앞 판 117 + 이 slice 의 1)

## 2026-11-13T03:5xZ — 공유 자리 KDoc 변경의 거동 0 (최종 소스)
- cmd: `javap -c -p …/ClaimedSourceTotalKt.class` (편집 전 기준 ↔ 최종) · 통제군 `javap -l -p`
- exit: 0
- 핵심 결과: `-c` **동일**(거동 0) · `-l` **다르다**. 통제군이 갈리는 것이 이 측정의 뜻이다 — 선언의 줄
  번호가 실제로 움직였는데도 바이트코드가 같으므로, 「동일」이 **측정 부재가 아니다**. (중간 판에서는
  교체가 7 줄 ↔ 7 줄이라 `-l` 도 동일해 통제군이 무력했고, 절 글자를 빼는 커밋이 한 줄을 더해 갈렸다.)
  **도구가 변경을 잡는지**는 아래 양성 대조가 따로 든다

## 2026-11-13T02:11Z — 그 비교의 양성 대조
- cmd: 변이 M1 을 적용해 재컴파일한 뒤 같은 `javap -c -p`
- exit: 0
- 핵심 결과: **다르다** — 이 비교는 실제 코드 변경을 잡는다. 측정 뒤 되돌려 porcelain 빈 출력

## 2026-11-13T02:12Z — CPD 리포트 재산출 (변이 뒤 · 전건 전)
- cmd: `… :workflow:cpdCheck --rerun :workflow:cpdCheckObserved --rerun`
- exit: 0
- 핵심 결과: 2 task 실행 — 변이의 적용·복원이 소스 **mtime** 만 올려 리포트가 낡는 자리를 전건 **전에**
  해소했다(10A 가 전건 2차에서 겪은 그 자리 · 로컬 전용)

## 변이 — 최종 코드

필터는 수집 갈래 test **전수**이고 모든 판이 task 옵션 `--rerun` 을 지난다. 적용은 앵커 계수 단언을 지나
어긋나면 비영 종료하고, 판마다 적용 전 porcelain 빈 출력을 확인하며 복원은 한 자리다. `:workflow:test`
task 줄에 **접미가 없는 것**(cache 적중·UP-TO-DATE 가 아닌 것)을 판마다 확인했다. 끝 트리 porcelain **0 줄**.

| 판 | **바꿔치운** 것 | exit | RED |
| --- | --- | --- | --- |
| 기준 | — | 0 | 0 / 118 |
| M1 | 공유 최댓값 함수를 「마지막 배치 값 쥐기」로 | 1 | **3** — 공고 목록 · 낙찰 · **개찰결과** 각 1 |
| M2 | 같은 함수를 「첫 배치 값 고정」으로 | 1 | **2** — 공고 목록 · 개찰결과 |
| V1M | 같은 함수의 `batch == null` 갈래를 「null 반환」으로 | 1 | **2** — 공고 목록 · 개찰결과 |
| V2M | 슬롯 회계를 **누산기 값 그대로** 적게(총수 덧싣기 제거) | 1 | **1** — 개찰결과만 |
| C | 이 slice 가 세운 누적 타입을 선언과 **모든 사용처**에서 개명(거동 0) | 0 | 0 / 118 |

- **M1 이 세 갈래를 함께 붉히는 것이 이 slice 의 계약 문장**이다 — 셋이 같은 한 자리를 부르므로, 공유
  누산기를 최댓값으로 올리는 날 걷을 자리가 하나다(10A D-2 의 전제가 이제 실제로 선다).
- M2 가 낙찰을 붉히지 않는 것은 **낙찰 test 가 그 축(「더 큰 총수가 이긴다」)을 재지 않기** 때문이다 —
  10A 때와 같은 사실이고, 이 slice 가 그 축을 개찰결과 쪽에 더해 RED 가 하나 늘었다.
- V1M 이 개찰결과를 붉히는 것이 **「null 보호가 옮겨 갔다」의 실측**이다. 고치기 전에는 누산기의 `?:` 가
  막고 있어 그 단언이 초록이었다(그래서 RED 선행이 아니라 **이동 뒤 잠금**이다).
- V2M 은 이 갈래의 **유일한 보고 경로**를 잰다 — 형제 갈래의 V2M(보고가 누산기 값을 따로 나르는 갈림)은
  여기 존재할 수 없고(총수를 나르는 자리가 슬롯 회계 행 하나다), 그 한 자리를 잠근다.
- 음성 대조 C 가 초록이라 suite 가 무관한 이유로 붉지 않는다. **더하기만 한 변이가 아니라 개명 바꿔치우기**
  이고 네 자리 전부 적용(x4)된 판에서 초록을 받았다.

## acceptance

## 2026-11-13T02:45Z — 전건 1차
- cmd: `./gradlew --no-daemon check`
- exit: 1
- 핵심 결과: `:workflow:detekt` **하나** — 이 slice 의 test 파일에 120 자를 넘는 줄 하나(`MaxLineLength`).
  **표적 실행이 잡지 못하는 축**이다(그 명령에 lint 가 없다 — 부분 게이트는 안 돌린 것과 같다는 규율의
  실례). 인자 목록을 줄마다 펴서 해소했고, 같은 커밋이 절 글자 참조도 뺐다

## 2026-11-13T03:40Z — 전건 2차, CI Kotlin job 명령 그대로 (병합 전 트리 `1ac60ba5`)
- cmd: `./gradlew --no-daemon check`
- exit: 0
- 핵심 결과: 48m32s · 424 task 중 106 실행 · `FAILED` **0 줄** · `:workflow:test` **접미 없이 실행**.
  병합 전 초록을 하나 쥔 이유는 병합 뒤 전건이 붉을 때 **내 코드인지 병합인지 가르기** 위해서다

## 2026-11-13T04:0xZ — main `32dead40` 병합 뒤, 같은 명령 (병합 커밋 `e19e7eda`)
- cmd: `./gradlew --no-daemon check`
- exit: 0
- 핵심 결과: 433 task 중 35 실행 · `FAILED` **0 줄**. 병합이 들여온 것은 저장소 루트 markdown **셋**
  (`README.md`·`milestone-10.md`·`milestone-11.md`)이라 **markdown 을 훑는 게이트가 실제로 실행**됐다
  (`:leakPatternGate`·`:contractGate` 접미 없음). `:workflow:test` 는 **`UP-TO-DATE`** 였다 — 그 task 의
  입력이 2차와 바이트 같아서이고, CI 는 새 checkout 이라 언제나 실행한다. **조용한 초록으로 두지 않고**
  아래에서 같은 HEAD 에 실행을 하나 남겼다

## 2026-11-13T04:1xZ — 그 HEAD 에서 workflow test 강제 실행
- cmd: `./gradlew --no-daemon :workflow:test --rerun`
- exit: 0
- 핵심 결과: **접미 없이 실행** · workflow 86 클래스 763 통과·실패 0(그중 수집 갈래 118). 그 HEAD 에서
  이 slice 의 단언이 **실제로 돌았다**. **「판정 SHA」는 이 자리들이 아니다** — r0 판이 병합 커밋을 그렇게
  불렀는데(v r1 지적) 판정 SHA 는 라운드의 **마지막 커밋**이고, 이 라운드의 것은 아래 r1 절이 싣는다

## r1 (v·cr 일괄 처분 라운드)

## 2026-11-13T07:0xZ — KDoc 둘의 거동 0 · 개명
- cmd: `./gradlew --no-daemon :workflow:compileKotlin :workflow:compileTestKotlin` 뒤 `javap -c -p` / `-l -p`
- exit: 0
- 핵심 결과:

  | 파일 | `-c` | 통제군 `-l` | 읽는 법 |
  | --- | --- | --- | --- |
  | `SourceAccountingTally` | **동일** | **다름** | 줄이 밀렸는데도 바이트코드가 같다 — 거동 0 |
  | `CollectNoticesUseCase` | **동일** | 동일 | 그 편집이 **2 줄 ↔ 2 줄**(순증 0)이라 줄이 밀리지 않았다. `-l` 동일은 **기대값이지 측정 부재가 아니다** — 같은 compile task 가 낸 형제 클래스의 `-l` 이 바뀐 것이 재컴파일의 증거다 |
  | `SlotWalkTally`(개명) | **한 줄만 다름** | — | 그 한 줄이 메서드 이름이고 그 밖은 없다. 호출부까지 함께 바뀐 것은 suite 초록이 든다(변이 C 와 같은 꼴) |

  도구가 변경을 잡는지는 앞 라운드의 양성 대조 둘이 든다(변이 M1 재컴파일 · 위 개명 한 줄).

## 2026-11-13T07:4xZ — r1 전건, CI Kotlin job 명령 그대로
- cmd: `./gradlew --no-daemon check`
- exit: 0
- 핵심 결과: 35m23s · 424 task 중 85 실행 · `FAILED` **0 줄** · `:workflow:test` **접미 없이 실행**
  (수집 갈래 118 통과·실패 0)

**이 라운드는 게이트 술어를 바꾸지 않았다** — 고친 것은 KDoc 셋, 파일 private 메서드 이름 하나,
test 의 단언 위치 하나, 그리고 장부다.

## 선택 LOW 둘의 처분

- **커서 자취 재단언: 했다.** 자취 단언이 호출부 한 자리에만 있어 `null` 판과 「더 큰 총수」 판은 재개가
  돌았는지 보지 않았다. `null` 판은 재개가 없으면 총수가 첫 배치 값 그대로 7 이라 **보호 없이도 통과**한다 —
  실재하는 구멍이라 helper 로 옮겨 판마다 돌게 했다.
- **`framePage` 전달 프레임: 하지 않았다.** 이 slice 가 만든 자리가 아니고(상한을 관문이 센다는 근거
  주석이 거기 산다), 없애면 이 slice 의 in_scope 밖 구조 변경이 된다. 지금 상태로도 `SlotWalkTally` 의
  존재 근거는 선다 — 배치를 받는 자리가 걷기 루프 **밖**이라 지역 변수로는 두 값이 갈릴 수 있다.

## 그 밖

- **비밀값 스캔**: `grep -rniE -f config/quality/leak-patterns.txt <in_scope 경로들>`. Kotlin 산출물 셋과
  `gate-tests.properties`·이 장부는 **매치 0**. `docs/discovery/capability-map.md` 는 in_scope 인데 **전체
  3 매치**가 있고 **전부 이 slice 이전 줄**이다 — 이 slice 가 **추가한 줄**(`<base>..HEAD` 의 `+` 줄)에서는
  **0**(기계 계수). 루트 `leakPatternGate` 는 그 셋을 baseline 이 받아 전건 안에서 돈다.
- **clean-tree**: `git status --porcelain -- <in_scope 개별 인자>` **빈 출력** · **양성 대조 1회**(in_scope
  파일 하나에 줄을 더하니 ` M` 한 줄로 잡혔고, **비파괴 절삭**으로 되돌리니 다시 빈 출력 — `checkout --` 을
  쓰지 않았다).
- **out_scope 단언**: `git diff --stat <base>..HEAD --` 로 공유 누산기·형제 두 갈래·`SlotTally`·`procurement`·
  `adapters`·`app` **빈 출력**.
- **하네스 레인 변경**: `git log --oneline <base>..HEAD -- CLAUDE.md .claude/` **빈 출력**.

# M9/ops-code-followups checklist

판정 대상 SHA 는 마지막 산출물 커밋이다(`commands.md` §1 · `rollback.md` §4). 이 slice 는 PR #140 이
후속으로 넘긴 셋만 한다 — E2E 파일 분할 · `awardExitCodeOf` KDoc · 원 evidence 두 문면.

## 1. acceptance 대조

| 요구 | 실측 |
| --- | --- |
| CI Kotlin job 의 명령 그대로 | `./gradlew --no-daemon check` **exit 0** · `FAILED` 0 회 · 433 actionable (`commands.md` §2) |
| 분할이 test 를 보존 | 이름 집합 다섯 차이 0 · XML `tests=5 skipped=0 failures=0 errors=0` · 실행 순서가 `@Order` 1→5 (§3) |
| 새 test class 등재 | **대상 없음** — 새 class 에 `@Test` 가 없어 모집단 밖이고, `gate-tests.properties` 는 한 줄도 안 바뀌었다(양방향 계수 133 = 133) |
| KDoc 수정의 거동 | 그 소스가 내는 class **둘 다** 명령 스트림 동일 · 변경 줄 열넷이 전부 KDoc 본문 |
| 원 evidence 정정 | 번호 정본(⑯ = E2E · ⑮ = `CollectAwardListUseCaseTest`)에 맞춘 원문 순서 + 정정 주석. 문면은 덮지 않았다 |
| 하네스 레인 변경 | 없음 — `.claude/`·`config/`·`build-logic/`·`.github/` diff 빈 출력 |

## 2. 왜 이 선택인가

가른 선이 class 가 아닌 사유(`@Order`+`@BeforeEach` 결합 · 컨테이너·컨텍스트 한 벌)와 심는 값을
생성자로 받는 사유는 **`scope.md` 「① 를 class 분할로 하지 않은 이유」가 정본**이다. 여기 옮겨 적지
않는다 — 두 벌로 두면 갈린다.

## 3. 우회 — 고안하고 막은 것

| 우회 | 무엇이 막는가 |
| --- | --- |
| 「갈랐다」면서 그 test 를 **실행에서 뺀다**(`Test.filter`·`--tests`) | 그 class 는 `gate-tests.properties` 등재라 `gateExecutionGate` 가 **돌았고 실패·건너뜀 0** 을 단언한다. XML 계수도 같은 사실을 든다 |
| 가르면서 **단언을 조용히 약화**한다 | test 다섯의 본문·KDoc 을 수신자 표기만 정규화해 base 와 `diff` — **95 줄 차이 0**. 음성 대조: 정규화 없이는 26 줄이 다르고 그 줄 전부가 수신자 자리다(정규화가 공허하지 않다) |
| `@Order` 를 떼어 **비우기의 짐을 없앤다** | XML 의 `testcase` 는 실행 순서로 적히고 그 순서가 1→5 그대로다. 떼면 그 자리에서 보인다 |
| 새 class 를 **등재 없이** 들인다 | `gateRegistrationGate` 가 모집단 ∖ 제외 == 등재를 양방향으로 잰다. 이 slice 는 `@Test` 없는 class 라 모집단 밖이고, `@Test` 를 하나라도 달면 **등재 없이는 즉시 붉다** |
| 한도를 **주석 지우기**로 피한다(문면 손실) | **다섯 test 의 문면은 보존된다** — 본문·KDoc 95 줄이 수신자 표기만 빼면 base 와 같다. **그 측정의 범위는 그 다섯뿐이다**(helper·상수 KDoc 은 밖이다) — 아래 문단이 범위 밖에서 접은 한 문단을 선언한다 |
| KDoc 을 고치면서 **거동을 함께** 바꾼다 | 그 소스가 내는 class **둘 다** 명령 스트림 동일 · 변경 줄 열넷이 전부 KDoc 본문 |
| 거동 0 을 **낡은 class 로** 주장한다(비교가 공허) | 음성 대조가 그 함정을 집는다 — class 해시는 **다르고** 줄 번호가 KDoc 네 줄만큼 밀렸다. 내 쪽 class 는 편집된 소스로 다시 컴파일된 것이다 |

**접은 문단 하나 — 측정 범위 밖에서 일어났고, 의도된 것이다**(cr #144 M-1). base 의 helper
`insertStrategy` KDoc 에 있던 R2-H-1 문단(개정을 1 이 아닌 값으로 심으라는 근거)은 fixture 로 **옮기지
않고 접었다.** 심는 값이 **생성자 인자**가 되면서 그 근거가 유효한 자리가 **고르는 쪽**으로 옮겨가기
때문이다 — 그 근거는 test 파일의 `SEEDED_REVISION` 상수 KDoc(base 에 이미 있던 줄)에 그대로 있고,
fixture 머리 문단이 「왜 그 값인지는 단언하는 쪽의 근거라 test 파일에 남는다」로 그 자리를 가리킨다.
fixture 쪽에 되살리면 **고를 수 없는 자리에서 되적는 목록**이 하나 더 생긴다(②의 KDoc 이 경계한 바로
그 모양이다). 위 표의 「95 줄」 측정은 이 손실을 **애초에 잡지 못한다** — 그래서 측정이 아니라 이 선언이
그 칸을 진다.

## 4. 알려진 제한

1. **`CollectAwardListUseCaseTest.kt` 의 여유는 세 줄 그대로다**(497/500). 팀장이 「필요 시, 필수
   아님」으로 넘긴 축이고 **지금 가르지 않았다** — 더하는 변경이 없는 채로 가르면 그것이 바로 §5 가
   금지하는 **크기 회피용 분할**이다. 원 slice 의 ⑮ 가 적은 처방이 오늘도 유효하다: 다음에 이 파일에
   **더하는** 변경이 관심사 경계를 보고 가른다. 그 전까지 이 파일은 여전히 병합 합집합에 약하다.
2. **같은 DB 를 물려받는 결합이 두 파일에 걸쳐 읽히고, 그 수명이 `internal` 로 열렸다.** 수명·seed 가
   fixture 로 나갔으므로 「왜 `@BeforeEach` 가 표를 비우는가」를 이해하려면 두 파일을 함께 봐야 한다.
   그리고 파일이 갈리면서 fixture class 와 지역 fake 가 `internal` 이 되어(base 에서는 파일 private
   이었다) **app test 소스셋 어디서나 세울 수 있다** — 한 번 더 세우면 Postgres 컨테이너가 두 벌 뜨고,
   그것은 class 분할을 피한 사유 ②와 같은 비용이 다른 문으로 돌아오는 것이다(cr #144 LOW). 가리는
   장치는 문면뿐이다 — fixture 머리 문단이 「쓰는 쪽은 하나다 · 둘째 사용처가 생기면 수명 공유를 다시
   설계한다」를 적는다. **구조로 닫지 않았다**(같은 패키지 안의 결합이라 마땅한 층이 없다).
3. **`javap` 비교는 그 소스가 내는 class 둘에 한정된다.** 더 넓은 「이 slice 가 거동을 안 바꿨다」는
   전건 `check` 초록이 드는 주장이고, 이 측정은 **KDoc 만 고친 파일**에 대한 것이다.
4. **`sizeGate` 는 여전히 상한 하나짜리 술어다.** 이 slice 가 만든 여유(347/500)는 다음 병합이 다시
   먹을 수 있다 — 막는 것은 게이트가 아니라 그 자리를 보는 사람이다. 구조적 답(파일마다 래칫)은 이
   계약 밖이다.
5. **원 slice evidence 를 고친 것이 그 slice 의 rollback 을 움직이지 않는다.** ops-code 의 복원 목록은
   자기 evidence 디렉터리를 **제외**로 두고 만들어졌으므로 내가 만진 두 파일은 그 목록 밖이다. 그
   slice 의 실측은 그 당시의 사실로 남는다(#140 은 머지됐고 되돌림은 PR revert 다).
6. **`stop()` 은 `boot()` 이 던진 실행에서 컨테이너 정지를 건너뛴다**(cr #144 LOW, **base 와 같은 두 줄
   같은 순서 — 이 slice 의 회귀가 아니다**). `seedContext.close()` 가 먼저라 `lateinit` 미초기화 예외가
   나고 `postgres.stop()` 에 닿지 않는다(Testcontainers Ryuk 이 뒤에 회수하므로 누출은 유한하다).
   고치는 길은 있으나(`runCatching` 로 감싸기) **이 라운드에서 택하지 않았다** — 거동을 건드리지 않는
   것이 이 slice 의 계약이고, base 와 다른 실패 모양을 새로 만들지 않는다. 부수 차이 하나는 등재한다:
   컨테이너 기동을 companion 필드 초기화에서 `@BeforeAll` 로 옮겼으므로 기동 실패가 보고되는 모양이
   「class 적재 실패 하나」에서 「`@BeforeAll` 실패 + `@AfterAll` 의 lateinit 예외 둘」로 바뀐다(원인
   예외는 그대로 보여 진단 손실은 없다).

## 5. 새 파일 ↔ in_scope 대조

evidence 넷(`scope`·`commands`·`checklist`·`rollback`)을 빼면 새 파일은 **하나**다. 목록은 손으로 세지
않는다: `git log --no-merges --diff-filter=A --format= --name-only c21adb2a..HEAD | sort -u`
(원천은 병합 뒤의 것 — `rollback.md`).

| 새 파일 | in_scope 항목 |
| --- | --- |
| `app/src/test/kotlin/bidvector/app/evaluation/CommitRunE2EFixture.kt` | scope.md in_scope 둘째 줄(신설 fixture) |

수정 라운드가 새 파일을 더 만들면 이 표와 in_scope 를 다시 대조한다(반복 사각).

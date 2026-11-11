# M9/ops-code-followups checklist

판정 대상 SHA 는 마지막 산출물 커밋이다(`commands.md` §1 · `rollback.md` §3). 이 slice 는 PR #140 이
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

**가른 선이 class 가 아니다.** 알려진 제한 ⑯ 이 예고한 「class 분할」을 하지 않았고, 하지 않은 것이
회귀 방어에 더 낫다 — class 를 가르면 둘이 함께 깨진다.

1. `@Order` 로 **불리한 쪽에 고정한 순서**(행을 남기는 거동 축 test 가 먼저, 행 하나를 `single()` 로
   단언하는 test 가 뒤)가 풀린다. 그 순서가 `@BeforeEach` 의 표 비우기에 **짐을 지우는** 장치다 —
   그 KDoc 이 든 실측이 「JUnit 기본 순서에서는 비우기를 지워도 초록이었다」다. 두 class 로 가르면 뒤
   class 가 빈 DB 에서 시작해 그 비우기가 다시 「지워도 초록」이 된다.
2. `@TestInstance(PER_CLASS)` 로 **컨테이너·컨텍스트가 클래스당 하나**인데 두 벌이 된다(PR CI 시간).

그래서 선을 **「전제를 세우는 일」 ↔ 「무엇을 단언하는가」** 에 그었다. 같은 패키지가 같은 한도 때문에
이미 그어 둔 선이고(`CommitRunDbSupport` 가 「들여다보는 질의」를 갈라 둔 자리), 그 파일의 머리 문단이
「기계적 분할이 아니다」를 적는 자리다. 새 fixture 파일은 그 셋을 든다 — 컨테이너·컨텍스트 수명 ·
seed(전략·공고·raw) · 지역 fake ML.

**심는 값은 생성자로 받는다.** 「왜 그 값인가」(개정이 1 이 아닌 근거)는 단언하는 쪽의 것이고, 공유
상수로 끌어올리면 **다른 파일의 이름이 조용히 이쪽을 가리킬 수 있다** — 같은 이름을 패키지의 다른 test
파일들이 **다른 값으로** 각자 private 하게 들고 있다(개정 7 ↔ 907 · 상한 100 ↔ 5).

## 3. 우회 — 고안하고 막은 것

| 우회 | 무엇이 막는가 |
| --- | --- |
| 「갈랐다」면서 그 test 를 **실행에서 뺀다**(`Test.filter`·`--tests`) | 그 class 는 `gate-tests.properties` 등재라 `gateExecutionGate` 가 **돌았고 실패·건너뜀 0** 을 단언한다. XML 계수도 같은 사실을 든다 |
| 가르면서 **단언을 조용히 약화**한다 | test 다섯의 본문·KDoc 을 수신자 표기만 정규화해 base 와 `diff` — **95 줄 차이 0**. 음성 대조: 정규화 없이는 26 줄이 다르고 그 줄 전부가 수신자 자리다(정규화가 공허하지 않다) |
| `@Order` 를 떼어 **비우기의 짐을 없앤다** | XML 의 `testcase` 는 실행 순서로 적히고 그 순서가 1→5 그대로다. 떼면 그 자리에서 보인다 |
| 새 class 를 **등재 없이** 들인다 | `gateRegistrationGate` 가 모집단 ∖ 제외 == 등재를 양방향으로 잰다. 이 slice 는 `@Test` 없는 class 라 모집단 밖이고, `@Test` 를 하나라도 달면 **등재 없이는 즉시 붉다** |
| 한도를 **주석 지우기**로 피한다(문면 손실) | 지운 KDoc 이 없다 — 이동이다. 위 「95 줄 차이 0」과 이름 집합이 그것을 잰다 |
| KDoc 을 고치면서 **거동을 함께** 바꾼다 | 그 소스가 내는 class **둘 다** 명령 스트림 동일 · 변경 줄 열넷이 전부 KDoc 본문 |
| 거동 0 을 **낡은 class 로** 주장한다(비교가 공허) | 음성 대조가 그 함정을 집는다 — class 해시는 **다르고** 줄 번호가 KDoc 네 줄만큼 밀렸다. 내 쪽 class 는 편집된 소스로 다시 컴파일된 것이다 |

## 4. 알려진 제한

1. **`CollectAwardListUseCaseTest.kt` 의 여유는 세 줄 그대로다**(497/500). 팀장이 「필요 시, 필수
   아님」으로 넘긴 축이고 **지금 가르지 않았다** — 더하는 변경이 없는 채로 가르면 그것이 바로 §5 가
   금지하는 **크기 회피용 분할**이다. 원 slice 의 ⑮ 가 적은 처방이 오늘도 유효하다: 다음에 이 파일에
   **더하는** 변경이 관심사 경계를 보고 가른다. 그 전까지 이 파일은 여전히 병합 합집합에 약하다.
2. **같은 DB 를 물려받는 결합이 두 파일에 걸쳐 읽힌다.** 수명·seed 가 fixture 로 나갔으므로 「왜
   `@BeforeEach` 가 표를 비우는가」를 이해하려면 두 파일을 함께 봐야 한다. 가리는 장치는 문면뿐이다 —
   fixture 의 머리 문단이 「`object` 가 아닌 이유」와 그 전제를 적고, test 쪽 머리 문단이 가른 선을
   가리킨다. 구조로 닫지 않았다.
3. **`javap` 비교는 그 소스가 내는 class 둘에 한정된다.** 더 넓은 「이 slice 가 거동을 안 바꿨다」는
   전건 `check` 초록이 드는 주장이고, 이 측정은 **KDoc 만 고친 파일**에 대한 것이다.
4. **`sizeGate` 는 여전히 상한 하나짜리 술어다.** 이 slice 가 만든 여유(347/500)는 다음 병합이 다시
   먹을 수 있다 — 막는 것은 게이트가 아니라 그 자리를 보는 사람이다. 구조적 답(파일마다 래칫)은 이
   계약 밖이다.
5. **원 slice evidence 를 고친 것이 그 slice 의 rollback 을 움직이지 않는다.** ops-code 의 복원 목록은
   자기 evidence 디렉터리를 **제외**로 두고 만들어졌으므로 내가 만진 두 파일은 그 목록 밖이다. 그
   slice 의 실측은 그 당시의 사실로 남는다(#140 은 머지됐고 되돌림은 PR revert 다).

## 5. 새 파일 ↔ in_scope 대조

evidence 넷(`scope`·`commands`·`checklist`·`rollback`)을 빼면 새 파일은 **하나**다. 목록은 손으로 세지
않는다: `git log --no-merges --diff-filter=A --format= --name-only 6e6f953c..HEAD | sort -u`.

| 새 파일 | in_scope 항목 |
| --- | --- |
| `app/src/test/kotlin/bidvector/app/evaluation/CommitRunE2EFixture.kt` | scope.md in_scope 둘째 줄(신설 fixture) |

수정 라운드가 새 파일을 더 만들면 이 표와 in_scope 를 다시 대조한다(반복 사각).

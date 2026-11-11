# M9 / ops-code-followups — E2E 파일 분할 · `awardExitCodeOf` KDoc · 원 evidence 문면 정정
- 착수 base: main `6e6f953c` · 브랜치 `chore/m9-ops-code-followups`. PR #144 수정 라운드에서 main
  `c21adb2a`(#143)를 병합했다(`commands.md` §2a).
- 유래: PR #140 의 팀장 처분 코멘트 셋(cr r7 의 M-2 네 번째 자리 · LOW-B 번호 정본 · LOW-C 행 문면). 원
  slice evidence 는 `reports/evidence/m9/ops-code/`.
- rollback: in_scope 경로 한정 복원(내 커밋만) — **원천은 `c21adb2a`** 다(병합한 main 의 고정 SHA,
  착수 base 가 아니다). `rollback.md` 가 기계 산출 목록과 실측을 든다.
- **하네스 레인 변경**: 없다. 술어는 `git diff --name-only c21adb2a..HEAD -- .claude/ config/ build-logic/
  .github/` 빈 출력이다(`commands.md` §7). 새 test class 가 생기지 않아 `gate-tests.properties` 등재도
  늘지 않는다 — 그 사실도 같은 자리에서 잰다(병합 뒤에도 `app` 등재 133 불변).

## 대상 (셋 — 전부 PR #140 이 후속으로 넘긴 것)
| 항목 | 처분 |
| --- | --- |
| **①** `EvaluationCommitRunE2ETest.kt` 가 `sizeGate` 한도와 같은 수(500/500, 술어 `> limit`) | 파일을 가른다. **test 의 의미·개수·이름 집합을 보존**한다 — 가르는 선은 class 가 아니라 **전제를 세우는 자리**다 |
| **②** `AwardCollectionRunner.awardExitCodeOf` KDoc 이 「멈춤(예산·쿼터)」 | 세 사유(예산·쿼터·차단)로 맞추고, **사유의 수를 이 술어가 보지 않는다**는 구조를 같은 KDoc 에 적는다. 거동 0 |
| **③** 원 evidence 의 두 문면 | checklist 알려진 제한 ⑮⑯ 원문 순서(GFM 재번호로 뒤바뀌어 보였다) · scope.md in_scope 행의 경로·편집 사실. **이력을 되쓰지 않고 정정 주석으로** |

## ① 를 class 분할로 하지 않은 이유 (설계 판단)
class 를 둘로 가르면 **두 가지가 함께 깨진다.**
1. **`@Order` 결합** — 이 클래스는 「행을 남기는 거동 축 test 가 먼저, 행 하나를 단언하는 test 가 뒤」로
   순서를 **불리한 쪽에 고정**해 `@BeforeEach` 의 표 비우기가 실제로 짐을 지게 만든다(그 KDoc 이 든
   실측: JUnit 기본 순서에서는 비우기를 지워도 초록이었다). 두 class 로 가르면 뒤 class 는 빈 DB 에서
   시작하므로 그 비우기가 **다시 「지워도 초록」**이 된다.
2. **컨테이너·컨텍스트 하나** — `@TestInstance(PER_CLASS)` 로 Postgres 컨테이너와 Spring 컨텍스트를
   클래스당 하나만 띄운다. class 를 가르면 그 둘이 두 벌이 된다(PR CI 시간).

그래서 가른 선은 **「전제를 세우는 일」 ↔ 「무엇을 단언하는가」**다 — 같은 패키지의 선례와 같은 선이고
(`CommitRunDbSupport.kt` 가 「들여다보는 질의」를 같은 한도 때문에 갈라 둔 자리), 기계적 분할이 아니다.
새 파일 `CommitRunE2EFixture.kt` 는 컨테이너·컨텍스트 수명과 seed(전략·공고·raw), 그리고 지역 fake ML 을
든다. **`@Test` 가 하나도 없으므로 새 test class 가 아니다**(등재 모집단은 discovery 애노테이션이 붙은
컴파일된 class 다).

심는 값(개정 7 · 상한 10 · 후보 상한 100)은 **생성자로 넘긴다** — 「왜 그 값인가」(개정이 1 이 아닌
R2-H-1 근거)는 단언하는 쪽의 것이고, 같은 이름의 상수를 패키지의 다른 test 파일들이 **다른 값으로** 각자
private 하게 들고 있어(개정 7 ↔ 907 · 상한 100 ↔ 5) 공유 상수로 끌어올리면 그 파일들의 이름이 조용히
이쪽을 가리킬 수 있다.

## in_scope
- `app/src/test/kotlin/bidvector/app/evaluation/EvaluationCommitRunE2ETest.kt`(분할)
- `app/src/test/kotlin/bidvector/app/evaluation/CommitRunE2EFixture.kt`(신설)
- `app/src/main/kotlin/bidvector/app/collection/AwardCollectionRunner.kt`(KDoc 만)
- `reports/evidence/m9/ops-code/{checklist.md,scope.md}`(정정 주석)
- `reports/evidence/m9/ops-code-followups/**`(이 evidence)
- 조건부: `config/quality/gate-tests.properties` — **새 test class 가 생기면** 같은 커밋에서 등재한다.
  생기지 않았으므로 **건드리지 않았다**(그 사실을 게이트로 잰다).

## out_scope
- **거동 변경 일체** — 종료 코드 술어 · halt 어휘 · 수집·평가 거동. ② 는 KDoc 이고 ① 은 test 의
  이름·개수·단언을 보존하는 이동이다.
- `workflow/src/test/kotlin/bidvector/workflow/collection/CollectAwardListUseCaseTest.kt`(497/500) **분할**.
  팀장이 「필요 시, 필수 아님」으로 넘긴 축이고 **지금 가르지 않는다**: 여유가 남아 있고, 더하는 변경이
  없는 채로 가르면 그것이 바로 **크기 회피용 분할**이다(§5 금지). 원 checklist 의 ⑮ 가 적은 처방이 오늘도
  유효하다 — 「다음에 이 파일에 **더하는** 변경이 관심사 경계를 보고 가른다」. 알려진 제한으로 남는다.
- 다른 레인의 파일 · main 병합 · push/PR/머지(팀장이 한다).

## acceptance
`./gradlew --no-daemon check` — CI `Kotlin` job 의 명령 그대로(`.github/workflows/ci.yml`). 부분 게이트로
갈음하지 않는다.

추가로 이 slice 가 자기 주장에 대해 드는 측정 둘(`commands.md`):
- **① 의 보존** — 분할 전후 `@Test` 이름 집합이 **같다**(다섯, 차집합 양쪽 0). 그리고 `check` 안에서
  `gateExecutionGate` 가 `EvaluationCommitRunE2ETest` 의 **실행과 실패·건너뜀 0** 을 단언한다(그 class 는
  `gate-tests.properties` 에 이미 등재돼 있다) — 「가르고 나서 안 돌린다」가 그 자리에서 붉다.
- **② 의 거동 0** — `awardExitCodeOf` 의 **명령 스트림이 같다**(`javap -c -p`, 줄 번호 표 제외). KDoc 은
  주석이라 바이트코드에 남지 않지만, 줄이 밀리면 `LineNumberTable` 은 바뀐다 — 그래서 해시가 아니라
  명령 스트림으로 잰다.

# M6/6F-2 — checklist.md

## 리뷰 요청 조건

- [x] 구현 diff 커밋, base/head 고정 — base `547fd7b`, head `b1f37b5`. `git status --porcelain
      -- <in_scope 경로>` 결과 없음(commands.md 클린 트리 기록, 양성 대조 포함).
- [x] scope.md의 acceptance_commands 6개 전부 exit 0(commands.md).
- [x] CI `check` job 전건 exit 0(부분 게이트 아님, commands.md).
- [x] 변경된 fixture·정책 version — 없음(이 slice는 fixture·정책 데이터를 추가하지 않는다.
      `notice` 표 CHECK 제약·트리거는 무편집, D-6F2-7이 마이그레이션 자체를 배제).
- [x] 알려진 제한과 rollback 기록 — 아래 「알려진 제한」, `rollback.md`.
- [x] 비밀값 스캔 exit 1(매치 없음, commands.md). Telegram id·사업자 정보 축은 이 slice의
      코드·evidence에 그런 값이 등장하지 않는다(육안 확인).

## D-6F2-* 대조 — 계약이 요구한 결정과 실측

| ID | 요구 | 실측 근거 |
| --- | --- | --- |
| D-6F2-1 | 신설 패키지 의존 게이트, 바이트코드 상수 풀 형태 | `EvaluationAdapterDependencyTest` — `javap` 상수 풀 스캔, 양성/음성 대조 test 포함. `*EvaluationAdapterDependencyTest*` exit 0(commands.md) |
| D-6F2-2 | 상태 집합 SQL 리터럴 금지, 집합 등식 게이트 | `biddableStatuses()`가 `isBiddable`에서 산출. `CandidateStatusSetTest`가 `NoticeStatus.entries.filter { isBiddable(...) }`로 독립 재계산해 등식 비교(하한 단언 없음). `JdbcCandidateSourceTest`가 6개 상태 전부 DB에 심어 SQL이 실제로 Open·Renoticed만 거르는지 통합 확인 |
| D-6F2-3 | 마감 반개구간, 주입된 Clock | SQL `deadline_at > ?`(초과만, 등호 제외) + `clock.now()` 바인딩(DB `now()` 미사용). `마감이 now 와 같으면 후보가 아니다`·`마감이 지난 공고는 후보가 아니다`·`마감이 없는 공고는 후보가 아니다`·`마감이 미래인 공고는 후보다` 4개 test |
| D-6F2-4 | 조용한 LIMIT 아님, cap+1 읽고 초과 시 실패 | `cap + 1`을 `LIMIT` 파라미터로 바인딩, `scanned.size > cap`이면 `CandidateCapExceededException` throw. `상한을 넘으면 조용히 자르지 않고 크게 실패한다`·`cap 을 넘지 않으면 성공한다` 2개 test |
| D-6F2-5 | 결정적 순서 | `ORDER BY deadline_at ASC, notice_number ASC, notice_round ASC`. `순서는 마감 오름차순, 그 다음 공고번호, 그 다음 차수다` test — 같은 마감 두 건의 상대 순서까지 확인 |
| D-6F2-6 | 도메인 타입 직접 조립 금지 | `JdbcCandidateSource`는 `NoticeId.reconstructNotice(row)`만 호출한다(같은 모듈 `internal`, `adapters.persistence` 소유). `Notice`는 `internal constructor` — `adapters.evaluation`에서 직접 생성자 호출은 컴파일 자체가 거부한다(구조로 닫힘, 별도 test 불필요) |
| D-6F2-7 | 마이그레이션·인덱스 없음 | `git diff --stat 547fd7b..HEAD`에 `db/migration/` 경로 없음(rollback.md). `OPEN-6B1-INDEX-GAPS`에 이 질의의 축(상태 집합 + `deadline_at` 범위·정렬)만 등재 — 별도 파일 생성 없이 이 checklist에 등재로 갈음(원 OPEN 항목은 6B-1 소유 문서에 있고 이 slice는 새 마이그레이션을 만들지 않으므로 그 문서 편집 자체가 범위 밖이다) |
| D-6F2-8 | SystemClock → adapters.strategy, UuidCorrelationIdFactory → adapters.evaluation | 파일 위치로 직접 확인(`SystemClock.kt`·`UuidCorrelationIdFactory.kt` 경로) |

## 위협 모델 경계 — 방어 대상 셋 실측

① 승인되지 않은 행이 후보로 들어오는 것 — D-6F2-2 집합 등식 게이트(위 표).
② 열린 후보가 조용히 빠지는 것 — D-6F2-4(절삭)·D-6F2-5(순서) test.
③ 저장된 행이 도메인 복원 경로를 우회해 `Notice`가 되는 것 — D-6F2-6(컴파일 구조로 닫힘).

## 우회 경로 대응표(scope.md 우회 1~7)

| # | 우회 | 막는 것 | 실측 |
| --- | --- | --- | --- |
| 1 | SQL에 상태 리터럴 직접 적기 | `CandidateStatusSetTest` 집합 등식 | 리터럴을 넣으면 `biddableStatuses()`가 여전히 `isBiddable` 기반이라 test는 그대로 통과하지만, `JdbcCandidateSourceTest`의 여섯 상태 통합 test가 실제 SQL 바인딩 값을 검증한다 — SQL을 리터럴로 바꾸면 그 통합 test가 여전히 통과할 수 있어(값이 같다면), **구조적**으로는 `biddableStatuses()`를 SQL이 실제로 참조하는 것이 이 게이트의 본질이다(코드 리뷰 대상, 자동 게이트는 값 등식까지만) |
| 2 | `NoticeStatus`에 값이 추가되는데 질의가 안 따라감 | `CandidateStatusSetTest`가 `NoticeStatus.entries` 순회로 재계산 | 값 추가 시 `biddableStatuses()`가 자동으로 그 값을 포함/제외 판정하므로 SQL 바인딩도 같이 는다(코드가 `biddableStatuses()` 결과를 그대로 바인딩) |
| 3 | 마감 비교를 닫힌 구간으로 바꾸거나 NULL 통과 | 경계 test 셋(now/과거/null/미래) | `>= `로 바꾸면 「마감이 now와 같으면」 test가 실패, `IS NULL` 허용 시 「마감이 없는 공고」 test가 실패 |
| 4 | 상한을 조용한 LIMIT으로 되돌리기 | `cap+1` 읽기 + 초과 시 throw, `상한을 넘으면...` test | `LIMIT cap`으로 되돌리면 그 test가 `CandidateCapExceededException`을 못 받아 실패 |
| 5 | 순서 제거·비결정화 | 순서 test(같은 마감 두 건 포함) | `ORDER BY` 제거 시 DB 반환 순서가 비결정이라 그 test가 flaky/실패 |
| 6 | `adapters.evaluation`에서 금지 좌표를 전체 한정으로 직접 참조 | `EvaluationAdapterDependencyTest`(바이트코드 상수 풀) | import 없이 `bidvector.decision.XXX(...)`처럼 써도 컴파일된 클래스 상수 풀에 좌표가 남아 잡힌다 |
| 7 | 복원 경로 우회해 `Notice` 직접 조립 | `Notice`의 `internal constructor` | `adapters` 모듈에서 `Notice(...)` 직접 호출은 컴파일 에러(구조로 닫힘, 실측 불필요 — 타입 시스템이 원천 차단) |

## 값 획득 축(scope.md (2b)) — 수정 라운드 갱신

이 구현 라운드에서 표에 등재된 새 public 표면 넷(`JdbcCandidateSource`·`UuidCorrelationIdFactory`
·`SystemClock`·`CandidateCapExceededException`) 외에 **추가로 생긴 새 public 표면은 없다**.
`biddableStatuses()`는 `internal`(모듈 범위, `adapters.evaluation` 밖에서 호출 불가). 1행
「경계로 처리」 판정은 commands.md의 `:app:compileTestKotlin`(람다 구현 컴파일 성공) 실측으로
확인했다.

## 알려진 제한

- **`OPEN-6F-ASSEMBLY`(변경 없음)** — 이 slice는 `JdbcCandidateSource`·`UuidCorrelationIdFactory`
  ·`SystemClock`을 만들 뿐 `EvaluateCandidatesUseCase`에 꽂지 않는다. 조립은 6A-1 또는 전용
  slice 소관.
- **우회 1(SQL 리터럴 재도입)은 값 등식만으로는 완전히 안 잡힌다** — 위 우회 대응표 참고.
  `biddableStatuses()`를 참조하지 않고 우연히 같은 값(`{"Open","Renoticed"}`)을 SQL에 하드코딩해도
  현재 test 셋은 통과한다. 코드 리뷰(정적 확인 — `JdbcCandidateSource.kt`가 `biddableStatuses()`를
  실제로 호출하는지)가 이 틈을 메운다. 구조적 게이트(예: SQL 문자열에 상태 리터럴 자체가
  나타나지 않는지 정적 검사)는 이 slice 범위 밖 — 값 필요성이 낮다고 판단했다(SQL 상수가
  `Sql.kt` 한 곳에만 있고 변경 시 `CandidateStatusSetTest`·`JdbcCandidateSourceTest`가 즉시
  드러낸다).
- **`OPEN-6F2-CANDIDATE-BOUND`(신설, scope.md)** — `cap`의 실제 값과 초과 시 운영 처분(실패로
  멈출 것인가, 운영자 알림인가)은 조립·운영 축의 결정이다. 이 slice는 「조용히 자르지 않는다」만
  고정한다.
- **`OPEN-6B1-INDEX-GAPS`(등재만)** — 이 질의(상태 집합 + `deadline_at` 범위·정렬)에 인덱스가
  없다. 운영 데이터 규모 근거가 없어 이 slice는 추가하지 않는다(D-6F2-7).

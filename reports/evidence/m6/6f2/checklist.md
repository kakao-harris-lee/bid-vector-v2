# M6/6F-2 — checklist.md

## 리뷰 요청 조건

- [x] 구현 diff 커밋, base/head 고정 — base `547fd7b`, head 는 판정 요청 시점의 `git rev-parse
      HEAD`(scope.md 관례 — 값을 이 문서에 박지 않는다, LEDGER-3 verifier r1 수정: 이전 판이
      라운드 도중의 `b1f37b5`를 박아 뒀다가 이후 커밋으로 낡았다). `git status --porcelain
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
| D-6F2-2 | 상태 집합 SQL 리터럴 금지, 집합 등식 게이트 | `biddableStatuses()`가 `isBiddable`에서 산출. 두 축(verifier r1 HIGH-1 수정, D-6F2-9) — 아래 D-6F2-9 행 |
| D-6F2-9(수정 라운드 1) | 집합 등식 게이트를 두 축으로 재구성 — ① 거동 등식 ② 참조 단언 | ① `JdbcCandidateSourceTest`의 상태 집합 test가 `NoticeStatus.entries` 전 값을 DB에 심고, 기대 집합을 `isBiddable`로 **그 자리에서** 걸러 산출해(손 목록 아님) 스캔 결과와 등식 비교. ② `EvaluationAdapterDependencyTest`의 참조 단언 test가 `JdbcCandidateSource.class`의 `javap` 상수 풀에 `biddableStatuses` 심볼이 실재하는지 잰다(문자열 grep 아님, 컴파일러가 만든 참조) — SQL 바인딩을 리터럴 배열로 되돌리면 이 심볼이 사라져 test가 실패한다(재실측: 아래 「변이 재실측」) |
| D-6F2-10(수정 라운드 1) | 신설 게이트 test 등재 + 완결성 검사 | `config/quality/gate-tests.properties`의 `gate.tests.adapters`에 다섯(evaluation 넷 + strategy 하나) 등재. `EvaluationGateRegistrationTest`(`MlGateRegistrationTest`·`EventGateRegistrationTest` 관례)가 `evaluation` 패키지 자체의 등재 완전성을 잰다(자기 자신 포함) |
| D-6F2-3 | 마감 반개구간, 주입된 Clock | SQL `deadline_at > ?`(초과만, 등호 제외) + `clock.now()` 바인딩(DB `now()` 미사용). `마감이 now 와 같으면 후보가 아니다`·`마감이 지난 공고는 후보가 아니다`·`마감이 없는 공고는 후보가 아니다`·`마감이 미래인 공고는 후보다` 4개 test |
| D-6F2-4 | 조용한 LIMIT 아님, cap+1 읽고 초과 시 실패 | `cap + 1`을 `LIMIT` 파라미터로 바인딩, `scanned.size > cap`이면 `CandidateCapExceededException` throw. `상한을 넘으면 조용히 자르지 않고 크게 실패한다`·`cap 을 넘지 않으면 성공한다` 2개 test. **verifier r1 LOW-1 수정** — 생성자가 `require(cap > 0)`으로 잘못된 배선을 즉시 거부(`cap 이 0 이하이면 생성자가 거부한다` test, DB 오류로 새지 않는다) |
| D-6F2-5 | 결정적 순서 | `ORDER BY deadline_at ASC, notice_number ASC, notice_round ASC`. `순서는 마감 오름차순, 그 다음 공고번호, 그 다음 차수다` test — 같은 마감 두 건의 상대 순서까지 확인. **verifier r1 MEDIUM-1 수정** — 기존 표본은 `notice_round`가 전부 `"000"`이라 그 타이브레이커를 못 쟀다(빼도 초록이었다). `같은 마감·같은 공고번호에서는 notice_round 오름차순이다` test를 더해 그 축만 따로 잠갔다 |
| D-6F2-6 | 도메인 타입 직접 조립 금지 | `JdbcCandidateSource`는 `NoticeId.reconstructNotice(row)`만 호출한다(같은 모듈 `internal`, `adapters.persistence` 소유). `Notice`는 `internal constructor` — `adapters.evaluation`에서 직접 생성자 호출은 컴파일 자체가 거부한다(구조로 닫힘, 별도 test 불필요) |
| D-6F2-7 | 마이그레이션·인덱스 없음 | `git diff --stat 547fd7b..HEAD`에 `db/migration/` 경로 없음(rollback.md). `OPEN-6B1-INDEX-GAPS`에 이 질의의 축(상태 집합 + `deadline_at` 범위·정렬, 「추가하더라도 전체 인덱스」 판정)은 **계약 갱신 (2)로 `milestone-6.md` 6F-2 문단에 등재됐다**(소유 문서인 6B-1 evidence는 병합돼 이 slice in_scope 밖이라 팀장이 그 문단에 확정) |
| D-6F2-8 | SystemClock → adapters.strategy, UuidCorrelationIdFactory → adapters.evaluation | 파일 위치로 직접 확인(`SystemClock.kt`·`UuidCorrelationIdFactory.kt` 경로) |

## 위협 모델 경계 — 방어 대상 셋 실측

① 승인되지 않은 행이 후보로 들어오는 것 — D-6F2-2 집합 등식 게이트(위 표).
② 열린 후보가 조용히 빠지는 것 — D-6F2-4(절삭)·D-6F2-5(순서) test.
③ 저장된 행이 도메인 복원 경로를 우회해 `Notice`가 되는 것 — D-6F2-6(컴파일 구조로 닫힘).

## 우회 경로 대응표(scope.md 우회 1~7)

| # | 우회 | 막는 것 | 실측 |
| --- | --- | --- | --- |
| 1 | SQL에 상태 리터럴 직접 적기(`biddableStatuses()` 호출을 `arrayOf("Open","Renoticed")`로 대체) | `EvaluationAdapterDependencyTest`의 참조 단언(D-6F2-9 ②) — `JdbcCandidateSource.class`의 상수 풀에 `biddableStatuses` 심볼이 없어진다 | **verifier r1 HIGH-1 수정.** 이전 판은 `CandidateStatusSetTest`(함수와 그 본문 재계산값의 비교, 항진명제에 가까움)만으로 닫았다고 적었으나 실측(MUT-1)이 거짓임을 보였다 — SQL을 리터럴로 바꿔도 `check`가 초록이었다. 지금은 ①(거동 등식, `JdbcCandidateSourceTest`)과 ②(참조 단언)가 함께 닫는다. 재실측: `commands.md` 「변이 재실측(수정 라운드 1)」 |
| 2 | `NoticeStatus`에 값이 추가되는데 질의가 안 따라감 | `JdbcCandidateSourceTest`의 거동 등식 test — 기대 집합을 `NoticeStatus.entries`에서 그 자리 산출 | 값이 늘면 `biddableStatuses()`·기대 집합 둘 다 자동으로 반영한다. **LEDGER-4 정정(verifier r1)** — 값을 늘렸을 때 **가장 먼저** 붉어지는 것은 이 test가 아니라 `NoticeReconstruction.kt`의 소진 `when`(컴파일 실패, MUT-2 실측)이다 — 그 상태 경로가 없으면 컴파일 자체가 안 된다. 이 test는 그 다음 층(컴파일은 되는데 값이 다른 경우)을 잡는다 |
| 3 | 마감 비교를 닫힌 구간으로 바꾸거나 NULL 통과 | 경계 test 셋(now/과거/null/미래) | `>= `로 바꾸면 「마감이 now와 같으면」 test가 실패, `IS NULL` 허용 시 「마감이 없는 공고」 test가 실패 |
| 4 | 상한을 조용한 LIMIT으로 되돌리기 | `cap+1` 읽기 + 초과 시 throw, `상한을 넘으면...` test | `LIMIT cap`으로 되돌리면 그 test가 `CandidateCapExceededException`을 못 받아 실패 |
| 5 | 순서 제거·비결정화 | 순서 test(같은 마감 두 건 포함) | `ORDER BY` 제거 시 DB 반환 순서가 비결정이라 그 test가 flaky/실패 |
| 6 | `adapters.evaluation`에서 금지 좌표를 전체 한정으로 직접 참조 | `EvaluationAdapterDependencyTest`(바이트코드 상수 풀) | import 없이 `bidvector.decision.XXX(...)`처럼 써도 컴파일된 클래스 상수 풀에 좌표가 남아 잡힌다 |
| 7 | 복원 경로 우회해 `Notice` 직접 조립 | `Notice`의 `internal constructor` | `adapters` 모듈에서 `Notice(...)` 직접 호출은 컴파일 에러(구조로 닫힘, 실측 불필요 — 타입 시스템이 원천 차단) |

## 값 획득 축(scope.md (2b)) — 수정 라운드 갱신

착수 라운드에 등재된 새 public 표면 넷(`JdbcCandidateSource`·`UuidCorrelationIdFactory`
·`SystemClock`·`CandidateCapExceededException`) 외에 **도메인 능력을 여는 새 public 표면은
없다**. `biddableStatuses()`는 여전히 `internal`(모듈 범위). 1행 「경계로 처리」 판정은
commands.md의 `:app:compileTestKotlin`(람다 구현 컴파일 성공) 실측으로 확인했다.

**수정 라운드 1이 만든 `EvaluationGateRegistrationTest`(public class)는 이 표의 대상이
아니다** — 도메인 값·DB 접근 권한을 외부에 주지 않는다(자기 모듈의 소스 디렉터리 목록과
`gate-tests.properties` 문자열만 읽는 test 인프라). 새로 여는 능력이 없어 판정할 행이
없다.

## 알려진 제한

- **`OPEN-6F-ASSEMBLY`(변경 없음)** — 이 slice는 `JdbcCandidateSource`·`UuidCorrelationIdFactory`
  ·`SystemClock`을 만들 뿐 `EvaluateCandidatesUseCase`에 꽂지 않는다. 조립은 6A-1 또는 전용
  slice 소관.
- **종결(수정 라운드 1) — 우회 1(SQL 리터럴 재도입)은 착수 라운드에 「값 등식만으로는 완전히
  안 잡힌다」로 등재했었다.** verifier r1이 그 틈을 값이 아니라 실측(MUT-1)으로 확인했고
  (HIGH-1), D-6F2-9 ②(참조 단언)가 구조로 닫았다 — `biddableStatuses`를 참조하지 않으면
  `EvaluationAdapterDependencyTest`가 실패한다(재실측: `commands.md` 「변이 재실측(수정
  라운드 1)」).
- **`OPEN-6F2-CANDIDATE-BOUND`(신설, scope.md)** — `cap`의 실제 값과 초과 시 운영 처분(실패로
  멈출 것인가, 운영자 알림인가)은 조립·운영 축의 결정이다. 이 slice는 「조용히 자르지 않는다」만
  고정한다.
- **`OPEN-6B1-INDEX-GAPS`(등재만)** — 이 질의(상태 집합 + `deadline_at` 범위·정렬)에 인덱스가
  없다. 운영 데이터 규모 근거가 없어 이 slice는 추가하지 않는다(D-6F2-7).

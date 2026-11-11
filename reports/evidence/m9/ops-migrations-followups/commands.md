# M9 / ops-migrations-followups — 명령과 종료 코드

acceptance 정본은 `scope.md` 의 두 줄(CI Kotlin job 명령 그대로). 아래는 실행 순서대로의 실측이다.
**마지막 HEAD 의 게이트 결과 정본은 evidence 가 아니라 verifier 와 PR 조치 코멘트**다(evidence 는
자기 마지막 커밋의 post-state 를 담을 수 없다).

빌드는 전부 `~/.local/bin/bv-build.sh` 경유(호스트 동시 둘)이고, 시작 전 `free -m` ·
`pgrep -af 'GradleWrapperMain|GradleWorkerMain'` 를 **별도 호출로 먼저** 봤다.

## 1. 고정점 test 의 양성 대조가 공허했다 — 쌍 둘은 이미 평평하다

| 실측 | 결과 |
| --- | --- |
| `:adapters:test` 표적 둘 `--rerun`(쌍 **둘**로 심은 양성 대조) | **BUILD FAILED** — 고정점 test 2 중 1 실패. 심은 셋이 **하나도** 잡히지 않았다(관측 `[]`) |

이 실패가 이 slice 의 첫 진단이다. 버릴 postgres 컨테이너에서 렌더링을 직접 재서 사유를 가렸다
(`docker run --rm --network none postgres:16.4` + `psql`, 세 번):

| 심은 식 | 렌더링이 자기 고정점인가 |
| --- | --- |
| 쌍 **둘**의 행 값 비교(CHECK) | **예** — 저장 트리가 인자 둘의 OR 하나라 렌더링이 이미 평평하다 |
| 쌍 **셋**의 행 값 비교 — CHECK | **아니오** — 왼쪽 깊은 중첩으로 찍히고 재파싱이 평평하게 접는다 |
| 쌍 **셋** — EXCLUDE 의 `WHERE` | **아니오** |
| 쌍 **셋** — 트리거의 `WHEN` | **아니오** |
| 손으로 중첩해 쓴 OR 사슬 · 스칼라 `IN` 목록 | 예(파서가 텍스트의 중첩을 먼저 평평하게 접는다 — 접힐 식을 만드는 것은 **행 값 비교의 전개**다) |

그래서 양성 대조의 목록을 **쌍 셋**으로 고쳤다. 쌍 둘로 둔 대조는 「심었는데 아무것도 안 잡힌다」로
**조용히 공허**해지고, 그 공허는 모집단이 빠진 것과 구별되지 않는다. V26 이 CI `container` job 에서
걸린 자리는 쌍 일곱이었다.

## 2. 표적 test — 쌍 셋으로 고친 뒤

| 실측 | 결과 |
| --- | --- |
| `:adapters:test` 표적 둘 `--rerun` | **exit 0** · 고정점 2/0 · OutboxTransitionSql 15/0 |
| 고정점 첫째 test | 스키마의 제약·트리거 전부가 자기 렌더링의 고정점이다(소견 0) |
| 고정점 둘째 test(양성 대조) | 심은 셋이 **그 셋만** 보고됐다 — CHECK · EXCLUDE 의 `WHERE` · 트리거 `WHEN` |
| 실행 실측 | 결과 XML 의 `timestamp` 와 mtime 이 그 실행의 것이고(앞 판 실패본이 아니다) 각 case 에 실행 시간이 있다 — cache 적중이 아니다 |

둘째 test 가 **모집단이 실제로 넓어졌는지**를 재는 자리다. 모집단에서 EXCLUDE 나 트리거를 빼면 그
줄이 보고되지 않아 이 test 가 붉어지고, 반대로 탐침이 멀쩡한 객체를 집으면 심은 셋 밖의 줄이 나와
역시 붉어진다(집합 등식).

## 3. 변이 — 새 판이 구판이 놓친 결함을 잡는가

버릴 clone(`git clone --no-hardlinks`, detached)에서 재고 **worktree 의 `V*.sql` 은 무편집**이다
(계약의 out_of_scope). 한 실행에 **통제군을 함께** 넣었다 — 구판(정규식 토큰) test 를 그 clone 에만
되살려 새 판과 같은 JVM·같은 스키마에서 돌렸다. 「더하기만 한 변이」가 아니라 **바꿔치우기**다:
V26 의 쌍 CHECK 본문에 동일성 절 하나를 더해 **토큰을 늘리지 않고** 수용 집합을 넓혔다.

| 실측 | 결과 |
| --- | --- |
| `:adapters:test --tests '*OutboxTransitionSqlTest' --rerun`(변이 적용) | **BUILD FAILED** · 16 중 **1 실패** |
| 실패한 것 | **새 판**(전수 시도 거동 test) 하나뿐 |
| 통제군 — 구판 정규식 토큰 집합 등식 | **초록** — v r10 M-2 가 보고한 구멍을 그대로 재현했다 |
| 나머지 열넷(거부 실측·트리거 축·INSERT 갈래 포함) | 전부 초록 — 이 변이를 잡는 자리는 **새 판 하나**다 |
| 새 판이 보고한 차이 | 수용이 **9 → 13**. 늘어난 넷은 상태와 사유가 **같은 문자열**인 쌍이고(종단 둘 + 비종단 둘) 전부 어휘 밖 조합이다 |

**통제군이 초록인 이유**가 이 바꿔치우기의 요지다 — 더한 절은 새 리터럴을 쓰지 않으므로 본문에서
수확하는 토큰 집합이 변이 전후로 같다. 렌더링을 읽는 술어는 그래서 구조상 이 확장을 볼 수 없고,
수용 집합을 **시도해서** 세는 술어만 본다.

### 새 판도 못 잡는 축 — 그리고 그 초록이 변이의 무효가 아님

같은 clone 에서 **리터럴을 남기지 않는 확장**을 심었다: 쌍 CHECK 에 `OR (terminal_reason =
lower(state))` 한 줄.

| 실측 | 결과 |
| --- | --- |
| 표적 둘 `--rerun`(변이 적용 · 후보 어휘는 출하 그대로) | **exit 0** — Outbox 15/0 · 고정점 2/0. `:adapters:test` 는 상태 줄에 cache 표식 없이 **실행**이고 결과 XML 의 timestamp 도 그 실행의 것이다 |
| **같은 변이 + 후보 어휘에 소문자 변형만 더함**(대조) | **exit 1 · 새 판 RED** — 수용 **9 → 14**, 늘어난 다섯이 상태 토큰의 **소문자** 짝이다 |

**대조가 없으면 위의 초록은 아무것도 뜻하지 않는다** — 변이가 무효인 판과 구별되지 않는다. 대조가
RED 를 내므로 그 변이는 **실제로 쌍 다섯을 열었고**, 초록의 사유는 변이의 무효가 아니라 **후보
격자가 열거**라는 것이 가려진다. 소문자 변형을 더하면 그 **한 가족만** 닫히고 계산식 일반
(`length()>N`·정규식 류)은 남으므로, 출하 판은 후보를 넓히지 않고 그 한계를 test KDoc 에 적는 쪽을
골랐다(verifier 의 처방 추천과 같다).

변이를 되돌린 뒤 clone 의 `git status --porcelain` 0 줄(두 변이 모두).

## 4. acceptance 전건

| 실측 | 결과 |
| --- | --- |
| `./gradlew --no-daemon check` (처음) | **exit 1** — `:adapters:ktlintTestSourceSetCheck` 가 네 번 이어진 호출 체인 한 줄을 막았다(`Expected newline before '.'`) |
| 같은 실행의 `./gradlew --no-daemon qualityBaseline` | exit 0 |
| 체인을 한 호출로 고친 뒤 `:adapters:ktlintTestSourceSetCheck` · `:app:ktlintTestSourceSetCheck` · 두 모듈 `detekt` | exit 0 |
| `./gradlew --no-daemon check` @ `54211baa` | **exit 0** · 36m40s · 433 task(226 실행 · 112 cache · 95 up-to-date) |
| **`./gradlew --no-daemon check`** @ **`83d97128`**(마지막 산출물 커밋) | **exit 0** · 4m35s · 424 task(51 실행 · 373 up-to-date) — 바뀐 모듈의 `:adapters:test` 는 **실행**, `:app:test` 는 app 입력이 안 바뀌어 up-to-date |
| **`./gradlew --no-daemon qualityBaseline`** @ 같은 HEAD | **exit 0** |
| 그 앞의 표적 lint(`ktlint` 둘 · `detekt` 둘) | exit 0 — 전건 한 회차를 ktlint 한 줄로 날리지 않기 위해 먼저 돌린다 |

CI 워크플로 Kotlin job 의 명령 **그대로**다(부분 게이트로 줄이지 않았다). 변경이 닿은 job 이 그
하나뿐이라 Python·web·container job 은 돌리지 않았다 — 워크플로가 job 사이 소스 독립을 선언한다.
단, `tools/db-rehearsal.sh` 는 `container` job 이 쓰므로 그 파일에 **거동 변경이 없음**을 따로 쟀다
(아래 §5).

**마지막 evidence 커밋의 게이트 결과는 이 표에 없다.** evidence 는 자기 마지막 커밋의 post-state 를
담을 수 없다 — 그 줄을 적으려면 커밋이 하나 더 필요하고 그 커밋이 다시 같은 줄을 요구한다. 그 자리의
정본은 **verifier 와 PR 조치 코멘트**다. 위 전건 두 줄은 **산출물 층**(그 HEAD 에 evidence 편집이
섞이지 않은 상태)을 덮고, evidence 문면이 건드리는 유일한 게이트인 누출 스캔은 §5 가 직접 쟀다.

## 5. 그 밖의 실측

| 실측 | 결과 |
| --- | --- |
| 비밀값 스캔 — 참조형(`-f config/quality/leak-patterns.txt`), 대상은 evidence 디렉터리 | **매치 없음**(exit 1) → 허용 목록 편집 0. 라운드 1 에서 **키 이름 하나를 runbook 에 새로 적었으므로**(계정 비밀 문구 leaf) 그 이름과 편집한 runbook 둘을 같은 스캔으로 따로 확인했다 — 역시 매치 0(그 이름은 스캔 어휘를 피해 고른 것이라는 코드 KDoc 의 주장이 실측으로 선다) |
| clean-tree 게이트 — `git status --porcelain --` 산출물 경로 **개별 인자** 여섯 | **0 줄** |
| 같은 게이트의 **양성 대조** | 한 파일에 한 줄을 심으면 **1 줄**, 비파괴 절삭(`head -n <원래 줄수>`)으로 되돌리면 다시 0 줄 · 파일은 `git diff` 가 빈 것으로 동일성 확인 · 실행 비트 보존(`checkout --` 를 쓰지 않았다) |
| `tools/db-rehearsal.sh` — 거동 무변경 | `bash -n` exit 0 · 스크립트가 생성하는 Flyway 호출자 Java 파일을 heredoc 에서 그대로 꺼내 **Flyway 스텁과 함께 `javac --release 21` 로 컴파일** exit 0(주석만 바뀌었고 그 주석이 한글이어도 컴파일된다는 사실까지 재서, `container` job 에서야 알게 되는 길을 피했다) |
| 레인 뒷정리 | 내 Gradle daemon 잔존 **0**(`--stop` 을 쓰지 않았다 — 다른 레인 것이 돌고 있었다) |

# M6/6G-2g — 실행 명령과 결과

정본 계약은 `scope.md`(D-6G2g-1~16). 이 문서는 명령과 **한 줄 결과**만 담는다 — 출력 전문은 붙이지 않는다.

> **마지막 HEAD 의 게이트 결과 정본은 이 문서가 아니다.** evidence 는 자기 마지막 커밋의 post-state 를
> 담을 수 없다(그 줄을 적으려면 커밋이 하나 더 필요하고 그 커밋이 다시 같은 줄을 요구한다). 마지막
> HEAD 의 `check` 결과는 **verifier 리포트와 PR 조치 코멘트**가 든다.

> 명령별 UTC 시각은 수집하지 않았다 — 이 slice 의 모든 실행은 2026-10-04 이고, 각 명령이 어느 커밋
> 위에서 돌았는지는 아래 표의 「HEAD」 열이 든다.

## acceptance — CI `check` job 명령 그대로

`.github/workflows/ci.yml` 의 Kotlin job 이 돌리는 둘이다. 부분 게이트로 줄이지 않았다.
production 무변경이라 `container` job 은 생략한다(6G-2e 선례 — 아래 「생략한 job」).

| HEAD | 명령 | exit | 핵심 결과 |
|---|---|---|---|
| `ace32c8b` | `./gradlew --no-daemon check` | 0 | 전건 초록 |
| `ace32c8b` | `./gradlew --no-daemon qualityBaseline` | 0 | 측정(게이트 아님) |

**크기 게이트가 두 번 붉었고 두 번 다 전건 `check` 만 잡았다.** `3ee5f905` 에서 `:app:sizeGate` —
③ 의 관측 분배 함수 55 줄. 수정 라운드 1 에서 `:buildLogicSizeGate` — 상위 사슬 판독을 더한
class 파일 판독기 55 줄. 둘 다 분할로 닫았다(`d6c79a99` · `ace32c8b`). 실패 이력은 게이트가 실제로
작동했다는 증거다 — 부분 게이트만 돌렸다면 둘 다 지나갔다.

### 생략한 job

`container` job 은 돌리지 않았다. 사유: 이 slice 의 diff 에 production 코드가 **0** 이고(아래 표),
그 job 이 재는 것은 `:app:bootJar` 산출물과 이미지 위생이다. 판정 근거는 `git diff --stat` 의
production 경로 0 줄이며, verifier 가 같은 명령으로 재현할 수 있다.

## 항목별 게이트 실측

| 항목 | 명령 | exit | 핵심 결과 |
|---|---|---|---|
| ① 등재 등식 | `./gradlew --no-daemon gateRegistrationGate -x test` | 0 | 모듈 아홉 전부 등식 성립 |
| ① build-logic | `./gradlew --no-daemon buildLogicGateRegistrationGate` | 0 | 모집단 25 == 등재 25 |
| ①  코어 | `./gradlew --no-daemon -p build-logic test --tests '*GateRegistrationCensusTest*'` | 0 | 12 tests |
| ②~⑦ | `./gradlew --no-daemon :app:test --tests 'bidvector.app.architecture.*'` | 0 | 아키텍처 게이트 전건 |
| ④ 전건 | `./gradlew --no-daemon :app:test` | 0 | app 전건 초록 |

### ① 모듈별 모집단·제외 (task 리포트의 한 줄)

HEAD `ace32c8b` 의 task 리포트. **이 표는 라운드마다 다시 잰다** — 앞 판은 ① 커밋 시점의 수를 들고
있어 ③ 이 더한 app 의 test 하나가 빠져 있었다(vr r1 L-1 · cr r1 G-7).

| 모듈 | 모집단 | 제외 | 등재 |
|---|---|---|---|
| app | 52 | 0 | 52 |
| adapters | 125 | 2 | 123 |
| workflow | 47 | 0 | 47 |
| shared-kernel | 8 | 0 | 8 |
| decision | 24 | 0 | 24 |
| procurement | 33 | 0 | 33 |
| qualification | 2 | 0 | 2 |
| strategy | 6 | 0 | 6 |
| build-logic | 27 | 0 | 27 |
| settlement | 0 | 0 | 0 |

`settlement` 은 test 소스셋이 없고 장부에 키도 없다 — **빈 모듈에서도 등식이 성립**함을 같은 task 가 잰다.

### ④ 접기 통일의 **재관측** (D-6G2g-14 요구)

`enclosingClass` 접기를 쓰던 게이트들(원문 값 획득 · 대분류 · 공고명 키 · 러너 · 로거 · 층 판정)의 등재를
이름 절단 기준으로 다시 관측했다. **재등재 둘 말고는 하나도 바뀌지 않았다** — 계약의 예상(그 등재가 전부
`bidvector..` 안이라 두 접기가 같다)이 실측으로 확인됐다.

| 확인 | 명령 | 결과 |
|---|---|---|
| 바뀐 등재가 둘뿐이다 | ④ 커밋의 `git diff -- config/quality/architecture-policy.properties` | 줄 둘(`NoticeKeyHash$Companion` → `NoticeKeyHash` · `Resolution$Resolved` → `Resolution`) |
| 나머지 게이트가 초록 | `./gradlew --no-daemon :app:test` | exit 0, app 전건 |

**바뀌는 것이 있으면 멈추는 조건**(계약 문면)에 걸리지 않았다. 주입 축은 규칙과 관측 **셋**이 모두 접어야
등식이 서는 자리라 함께 옮겼다(규칙 하나 · 관측 둘).

### ⑥⑦ 등재 규모

HEAD `ace32c8b`. 수정 라운드 1 에서 용도 둘과 낱개 셋이 늘고 3층이 도달 추적으로 바뀌었다.

| 축 | 쌍 | 보유자 |
|---|---|---|
| 전송 2층 — 기존 다섯 용도 | 65 | 29 |
| 전송 2층 — `file-system` | 41 | 15 |
| 전송 2층 — `http-response` | 2 | 2 |
| 전송 2층 — `process-stdio` | 1 | 1 |
| 전송 2층 — 합계 | 109 | 40 |
| 전송 3층 — `member-surface` | 28 | — |

전송 표면 뿌리 열여덟 · 낱개 열넷(파일 여는 아홉 + 프로세스·`ServiceLoader` 다섯) · 용도 여덟 ·
깊이 축 열.

## 변이 — 항목마다 RED

전부 **커밋 뒤** 적용하고 `git diff --numstat` 로 적용을 확인한 뒤 **저장해 둔 바이트로 복원**했다
(`git checkout --` 은 쓰지 않았다). production 에 심은 둘은 복원 뒤 `git status --porcelain` 빈 출력과
SHA-256 일치로 확인했다.

| 항목 | 변이 | 결과 |
|---|---|---|
| ① | 등재 한 줄 삭제(`shared-kernel` 의 한 class) | RED — 그 이름이 「장부에 없는 test 클래스」로 |
| ① | 미등재 test 클래스 둘을 **한 파일**에 추가 | RED — **둘 다** 신고(파일명 술어의 구멍이 닫혔다) |
| ① | 정책 파일만 편집(주석 한 줄) | task 재실행(UP-TO-DATE 아님), 초록 유지 |
| ① | 정책 파일 입력 선언을 `@Internal` 로 강등 | **같은 등재 삭제가 UP-TO-DATE 초록** — 거짓 초록 실측 |
| ① | 등재된 게이트 test 에 실행 조건 애노테이션 부착 | RED 둘 — 「잉여」 + 「선언되지 않은 제외」(제외 래칫) |
| ② | 이전에 덮이지 않던 패키지(`adapters.snapshot`)의 등재 삭제 | RED — 앞 판에서는 초록이던 자리 |
| ③ | `raw-access` 깊이 키만 `FULL` 로, 등재 그대로 | RED 둘 — 규칙과 집합 등식 |
| ③ | 구현이 넘겨받은 깊이를 무시하게 고침 | RED — 민감도 표에서 `RAW_ACCESS` 가 빠진다 |
| ③ | 쓰이지 않는 깊이 축 키 추가 | RED — **깊이 test 만**(형제 게이트는 초록) |
| ④ | 재등재한 두 축의 관측을 앞 판(접지 않음)으로 되돌림 | RED 둘 — key-hash 보유자 · 주입 표면 |
| ⑤ | domain 모듈 production 에 반사 한 줄 | RED — `qualification` 클래스가 `Class.getName` 으로 |
| ⑥ | 미등재 production 클래스가 경로를 쥐고 씀 | RED — 쌍 셋(`Path`·`Paths`·`OpenOption`) |
| ⑦ | 등재 보유자에 전송 타입 없는 송신 멤버 추가 | RED 둘 — 규칙과 집합 등식 |
| ① H-2 | 조회 자리에서 **모듈 test 출력**을 뺀다 | 합성 애노테이션만 단 클래스가 모집단에서 빠진다 — **영구 음성 대조 test** 가 든다 |
| ① M-1 | `@ParameterizedTest` 만 가진 **미등재** build-logic test | RED(앞 판은 초록 — 조회 자리가 비어 있었다) |
| ① G-2 | 추상 상위에서 물려받는 구체 test 클래스(미등재) | RED, **구체 클래스만** 신고(추상 상위는 모집단 밖) |
| ⑦ H-1 ⓐ | 보유자에 전송 멤버를 **직접** 부르는 멤버 | RED |
| ⑦ H-1 ⓑ | **private 헬퍼**로 한 번 감싸 부른다 | RED(앞 판 초록) |
| ⑦ H-1 ⓒ | **람다 안**에서 부른다 | RED — 람다 본문이 진입점으로 신고된다(앞 판 초록) |
| ⑦ G-3 | 등재된 이름의 **오버로드**를 더해 전송 | RED(앞 판은 이름만 담아 접혔다) |
| ③ M-2 | 지운 축 키(`domain-purity`)를 되살린다 | RED — 축 모집단 양방향 등식 |
| ③ G-6 | 생성자에서 깊이 인자를 뺀다 | **컴파일 거부**(`No value passed for parameter`) |
| ⑤ G-4 | 허용된 sealed 타입의 **형제**를 주입 | RED(접기를 되돌린 뒤) |
| ⑥ M-3 | 미등재 클래스가 **문자열 경로로** 파일을 연다 | RED(앞 판 초록) |

⑥ 은 **영구 음성 fixture** 둘도 함께 둔다(`FileSystemEgressSamples.kt`) — 변이 표와 덮개 양방향 등식이 든다.

## 비밀값 스캔

패턴 어휘를 이 문서에 축어로 적지 않는다 — 참조형으로만 돌린다.

| 대상 | 명령 | exit | 핵심 결과 |
|---|---|---|---|
| 이 slice 의 evidence | `grep -rniE -f config/quality/leak-patterns.txt reports/evidence/m6/6g2g/` | 1 | 매치 없음 |
| 이 slice 가 **더한 줄만** | 위 패턴 파일로 `git diff <base>..HEAD` 의 추가 줄을 거른다 | 1 | 매치 없음 |

**in_scope 경로 전체에 그대로 돌리면 매치가 난다 — 전부 이 slice 가 만들지 않은 줄이다.** 저장소의 정책 키
이름 한 계열, Kotlin 컴파일러 렉서가 내보내는 상수 이름들, 그리고 누출 게이트 자신의 test 가 패턴 파일의
어휘를 **식별자로** 쓴다(그 낱말들을 여기 옮겨 적지 않는다 — 적는 순간 이 문서가 새 매치가 된다).
그래서 위 둘로 나눠 쟀다 — 판정 대상은 **이 slice 가 더한 줄과 evidence** 다.
산출물 전체의 판정 정본은 `leakPatternGate` 이고 전건 `check` 안에서 함께 돈다(위 acceptance 표).

육안 확인: 이 slice 는 test·정책·build-logic 만 바꾸고 식별자·외부 계정 값을 다루지 않는다.

## 알려진 제한

1. **① 의 순서** — 제외의 출처가 `test` task 의 필터라 등재 등식 task 가 그 task 뒤에 선다.
   `test` 가 실패한 실행에서는 돌지 않는다(그 실행은 이미 붉다). `gateExecutionGate` 와 같은 자리다.
2. **① 메타 애노테이션 조회 자리** — test 런타임 클래스패스 ∪ **그 모듈의 test 출력**이다. build-logic
   판은 included build 라 전자를 루트에서 집을 수 없어 **같은 좌표를 카탈로그에서 읽어** detached
   configuration 하나로 준다. 남는 한계: 그 둘 어디에도 없는 애노테이션은 풀리지 않고, 그러면 그
   클래스는 모집단 밖이라 **등재돼 있으면 잉여로 붉는다**(조용히 통과하는 방향이 아니다).
   상위 사슬도 같은 자리에서 푼다 — 풀리지 않는 상위는 그 가지에서 끝난다.
3. **③ 민감도 표의 한계** — 두 깊이의 관측이 같은 축(일곱)에서는 「구현이 깊이를 무시한다」를 이
   test 가 가리지 못한다. 민감 축 넷이 그 몫을 든다.
4. **⑥ 의 열거** — `java.io` 는 패키지 뿌리로 둘 수 없어(`IOException` 이 같은 패키지) 파일을 여는 타입
   **아홉**을 낱개로 열거했다(`File`·`FileInputStream`·`FileOutputStream`·`FileReader`·`FileWriter`·
   `RandomAccessFile` + 문자열 경로 생성자를 가진 `PrintWriter`·`PrintStream`·`Formatter`).
   그 아홉 밖의 타입으로 파일을 여는 길은 재지 못한다. `Scanner(String)` 은 문자열 파싱이라 뺐다.
5. **⑦ 의 도달 범위** — 3층은 **보유자 그룹 안**(접은 이름이 같은 클래스 전부)에서 닫힌 도달을 센다.
   그룹 **밖**의 함수를 거쳐 부르는 길은 그 함수가 사는 클래스가 보유자면 **그 클래스의 멤버로** 잡히고,
   보유자가 아니면 2층(클래스, 타입)이 잡는다. 람다는 `invokedynamic` 때문에 **호출 간선이 없어**
   본문이 스스로 진입점으로 등재된다 — 그래서 람다를 품은 바깥 멤버는 등재에 나타나지 않는다.
6. **경계 유지(운영자 결정 B-2)** — 허용 패키지 안의 **라이브러리 자체 로더**·StAX 외부 엔티티·
   Spring bean factory 출구는 이 slice 가 닫지 않는다. 파일 시스템 출구만 구조 한 수로 닫았다.
7. **새 public 표면 둘** — build-logic 의 Gradle task 타입 하나(빌드 스크립트가 꽂는 자리, 그 저자는
   6G 경계 밖)와 app test 소스셋의 축 enum 하나. production public 표면 변화 0, production diff 0.

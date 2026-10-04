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
| `d6c79a99` | `./gradlew --no-daemon check` | 0 | 전건 초록 |
| `d6c79a99` | `./gradlew --no-daemon qualityBaseline` | 0 | 측정(게이트 아님) |

**첫 실행은 붉었다.** 같은 명령이 `3ee5f905` 에서 `:app:sizeGate` exit 1 — 「함수 50 줄 한도 초과 1건」
(③ 의 관측 분배 함수 55 줄). `d6c79a99` 가 축별 헬퍼로 쪼개 닫았다. 실패 이력은 게이트가 실제로
작동했다는 증거다.

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

| 모듈 | 모집단 | 제외 | 등재 |
|---|---|---|---|
| app | 51 | 0 | 51 |
| adapters | 125 | 2 | 123 |
| workflow | 47 | 0 | 47 |
| shared-kernel | 8 | 0 | 8 |
| decision | 24 | 0 | 24 |
| procurement | 33 | 0 | 33 |
| qualification | 2 | 0 | 2 |
| strategy | 6 | 0 | 6 |
| build-logic | 25 | 0 | 25 |
| settlement | 0 | 0 | 0 |

`settlement` 은 test 소스셋이 없고 장부에 키도 없다 — **빈 모듈에서도 등식이 성립**함을 같은 task 가 잰다.

### ⑥⑦ 등재 규모

| 축 | 쌍 | 보유자 |
|---|---|---|
| 전송 2층 — 기존 다섯 용도 | 65 | 29 |
| 전송 2층 — 새 용도 `file-system` | 41 | 15 |
| 전송 3층 — `member-surface` | 18 | — |

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
2. **① build-logic 의 메타 애노테이션** — included build 의 test 런타임 클래스패스를 루트에서 집기
   어려워 비워 두었다. 그래서 `@TestTemplate` 파생만 가진 build-logic test 클래스는 모집단에 들지
   못하고, 등재돼 있으면 **잉여로 붉는다**(조용히 통과하는 방향이 아니다). 오늘 그런 클래스는 없다.
3. **③ 민감도 표의 한계** — 두 깊이의 관측이 같은 축(일곱)에서는 「구현이 깊이를 무시한다」를 이
   test 가 가리지 못한다. 민감 축 넷이 그 몫을 든다.
4. **⑥ 의 열거** — `java.io` 는 패키지 뿌리로 둘 수 없어(`IOException` 이 같은 패키지) 파일 타입
   **여섯**을 낱개로 열거했다. 그 여섯 밖의 `java.io` 타입으로 파일을 여는 길은 이 게이트가 재지 못한다.
5. **⑦ 의 깊이** — 3층의 본문 분석은 **깊이 1**이다(`memberEffectGate` 의 「서명 ∪ depth-1 본문」 관례).
   같은 클래스의 다른 멤버를 거쳐 부르는 두 걸음은 재지 못한다.
6. **경계 유지(운영자 결정 B-2)** — 허용 패키지 안의 **라이브러리 자체 로더**·StAX 외부 엔티티·
   Spring bean factory 출구는 이 slice 가 닫지 않는다. 파일 시스템 출구만 구조 한 수로 닫았다.
7. **새 public 표면 둘** — build-logic 의 Gradle task 타입 하나(빌드 스크립트가 꽂는 자리, 그 저자는
   6G 경계 밖)와 app test 소스셋의 축 enum 하나. production public 표면 변화 0, production diff 0.

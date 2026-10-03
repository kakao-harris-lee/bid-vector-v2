# M6/6G-2b — 전송 표면 게이트 보강: 관문 밖으로 바이트를 내는 길 (계약, 초안 2026-09-30 · 착수 2026-10-03)

> **지위: 착수(2026-10-03).** base **`1745a3e2`**(PR #57 6G-2f 머지 뒤 `main`), worktree `bid-vector-v2-m6-6g2b`, 브랜치 `m6-6g2b/2026-10-03`.
> 운영자 결정 2026-10-01 순서(6G-2a → 6G-2e → **6G-2b** → 6G-2c)대로. 실수집(1,000/operation/일 안에서 진행 중)과 병행 — 이 slice 는 test·정책 파일만 바꾼다.
> 수령하는 OPEN: **`OPEN-6G-TRANSPORT-GATE-HARDENING`**(6G 계약 D-6G-75, verifier r4 M-1 · r5 H-3, code-review r5 L-7).
> 초안 시점 문면은 아래에 그대로 두고, 착수 시 바뀐 것은 「착수 실측(2026-10-03 채움)」·「acceptance 정정」·OPEN 표의 이관 행으로 적는다.

- base: **`1745a3e2`**.
- 레인: `kotlin-implementer` 하나. Python 변경 없음(`OPEN-6G2E-APP-HTTP-IMPORT-CONTRACT` 는 6G-2c 로 이관 — Python 레인·`ml-engine` job 소관).
- Phase 2.5 설계 검토: 아래 「위협 모델」·(1)·(2)·(2b)·(3) 절이 그것이다(세션 모델 직접 수행, 2026-09-30 초안 · 2026-10-03 재확인 — 착수 실측으로 (2) 를 갱신한다).

## 왜 이 slice 인가

6G 의 호출 상한은 **관문 하나**(`KonepsCallGate`)가 센다(D-6G-47). 관문을 지나지 않는 호출은 상한에도 원장에도 들어가지 않으므로,
「관문 밖으로 나가는 길이 없다」가 구조로 서야 한다. 6G 는 두 게이트를 세웠다.

- `java.net.http.HttpClient` 를 쥔 클래스 집합 == 등재 집합(정확 집합, production 전체).
- 우회 전송·반사 타입(`java.net.URL` 등 열 개)을 쥔 클래스 집합 == 빈 집합.

verifier r5 가 둘째 게이트의 구멍을 실측했다 — 변이 다섯이 초록이다.

| 변이 | 형태 |
|---|---|
| KA1 | `uri.toURL().readText()` — `java.net.URL` 을 **타입으로 쥐지 않고** 호출 사슬로만 지난다 |
| KA12 | `java.beans.Expression` 으로 메서드를 부른다 |
| KA13 | `ProcessBuilder("curl", …)` |
| KA14 | `java.nio.channels.AsynchronousSocketChannel` |
| KA15 | Spring `RestClient` |

원인은 둘이다. ⓐ 술어가 ArchUnit 의존의 **호출 대상 소유 타입**만 본다 — 인자·반환 타입은 보지 않는다. ⓑ 금지 집합이 **타입 이름 열거**라
목록 밖 전송 API 는 처음부터 대상이 아니다.

**출하 코드에 우회 호출은 없다** — 6G 실수집의 호출 수는 맞다. 비어 있는 것은 회귀를 잡을 자물쇠다.

## 실수집과의 관계

- 이 slice 는 **실수집을 막지 않는다**(D-6G-65 게이트 하드닝 분류).
- 이 slice 는 **test 와 정책 파일만** 바꾼다. 수집 코드·실행 상태 형식·원장 형식을 바꾸지 않으므로, 실수집이 진행 중이어도 머지할 수 있다.
- 착수 실측에서 출하 코드의 **실제 우회 호출**이 나오면 성격이 바뀐다 — 멈추고 보고한다(D-6G2b-8).

## 착수 실측 (2026-10-03 채움, 팀장 — 레인이 ArchUnit 로 재실측해 commands.md 「계약 대조」에 적는다)

| 항목 | 값 |
|---|---|
| base SHA | `1745a3e2` |
| production import 기준 전송 표면 참조(app·adapters·workflow·domain·procurement `src/main`) | `java.net.URI` 14 · `java.net.http.HttpClient` 5 · `HttpTimeoutException` 4 · `HttpResponse` 3 · `HttpRequest` 3 · `io.grpc.StatusRuntimeException` 3 · `StatusException` 3 · `ManagedChannel` 2 · `io.grpc.stub` 1 · `io.grpc.Status` 1 · `java.nio.channels.FileChannel` 2 · `WritableByteChannel` 1 · `FileLock` 1 · **`java.net.URL` 0**(초안의 「1」은 KDoc 문구) — import 기준이라 호출 사슬·인자·반환 타입은 레인이 ArchUnit 로 전수 |
| D-6G2b-8 의 「쓰이지 않는 `HttpClient` import 둘」 | **이미 없다**(`CollectionWiring`·`OpeningCollectionWiring` 모두 0) → production diff **0** 이 기대값 |
| 기존 게이트 자리 | `app/src/test/kotlin/bidvector/app/architecture/CollectionArchitectureGateTest.kt`(`referencersOf`, 반사 게이트 `moduleMustNotUseReflection` + 관측 집합 등식) · 정책 `config/quality/architecture-policy.properties` |
| `./gradlew check` test 수 | 2,607(6G-2f 종결) |
| A-2(반사 게이트 뿌리를 `adapters` 까지) | 레인이 넓혔을 때 걸리는 기존 참조 수를 **먼저 실측해 보고**하고, 구현은 그 보고 뒤 운영자 결정으로 — 그 전까지 D-6 은 현 뿌리 유지 |

### 착수 실측 (초안 시점 표)

| 항목 | 값 |
|---|---|
| base SHA | |
| production 이 참조하는 전송 표면 타입 전수(호출 대상의 소유·인자·반환 타입 포함) | (초안 시점 import 기준: `java.net.URI` 13 · `java.net.http.*` 12 · `io.grpc.*` 10 · `java.nio.channels.FileChannel`·`FileLock` 각 1 · `java.net.URLEncoder` 1) |
| 전송 표면을 쥔 production 클래스 집합 | (초안 시점: KONEPS 관문 계열 · 첨부 내려받기 · LLM 클라이언트 · ML gRPC 어댑터 · 실행 상태 잠금) |
| `./gradlew check` test 수 | (6G 판정 SHA 기준 2498) |

## 재사용 조사 (Phase 2)

| 후보 | 판정 | 근거 |
|---|---|---|
| `CollectionArchitectureRules.kt` 의 `referencersOf` 와 기존 두 게이트 | **확장** | 집합 등식 구조는 맞다. 의존을 모으는 범위와 금지 집합의 모양을 바꾼다 |
| ArchUnit `JavaCall.getTarget()` 의 `getRawParameterTypes()` · `getRawReturnType()` | **채택 후보** | 호출 대상의 인자·반환 타입을 의존으로 모은다. KA1 이 이것으로 잡히는지 착수 첫 실측 |
| 클래스 파일 상수 풀·서술자 전수(기존 「원시 키 리터럴」 게이트가 쓰는 방식) | **대안** | ArchUnit 이 놓치는 참조가 있으면 이쪽으로. 둘 다 쓰지 않는다 — 하나로 정한다 |
| `CollectionArchitectureGateCatchesViolationsTest` 의 음성 fixture 방식 | **채택** | 변이를 손으로 넣지 않고 fixture 클래스로 상시 잰다 |
| 기존 금지 의존 목록(`okhttp`·`retrofit`·`httpcomponents`·`ktor`) | **유지** | 빌드 의존 단계의 금지. 이 slice 는 클래스패스에 **이미 있는** 표면을 다룬다 |
| 네트워크 sandbox·SecurityManager·에이전트 | 기각 | 런타임 장치는 CI 게이트가 아니고 JDK 21 에서 SecurityManager 는 폐기 예정이다 |

## 결정

| ID | 결정 | 근거 |
|---|---|---|
| **D-6G2b-1** | **금지 집합을 타입 이름이 아니라 패키지 뿌리로 둔다.** 전송 표면 = `java.net..` · `javax.net..` · `java.nio.channels..` · `java.rmi..` · `javax.naming..` · `org.springframework.web.client..` · `org.springframework.web.reactive..` · `org.springframework.http.client..` · `io.grpc..` · `io.netty..` 와 낱개 타입 `java.lang.ProcessBuilder` · `java.lang.Runtime`(`exec`) · `java.beans.Expression` · `java.beans.Statement`. 뿌리 목록은 정책 파일에 두고, **착수 실측에서 클래스패스의 전송 가능 패키지를 전수**해 빠진 뿌리를 더한다 | 열거는 새 API 하나마다 열린다. 뿌리는 그 패키지에 새 타입이 생겨도 닫혀 있다 |
| **D-6G2b-2** | **의존을 모으는 범위를 넓힌다.** production 클래스가 참조하는 타입 = 필드·매개변수·반환·지역·상속 타입 + **호출 대상의 소유 타입 · 인자 타입 · 반환 타입** + 생성자 호출 + 클래스 객체 참조. KA1(`URI.toURL()` 의 반환 타입이 `URL`)이 이것으로 잡혀야 한다 | 타입을 쥐지 않고 호출 사슬로만 지나는 길이 r4·r5 두 번 열렸다 |
| **D-6G2b-3** | **허용은 (클래스, 타입) 쌍의 정확 집합이다.** 관측한 (production 클래스, 전송 표면 타입) 쌍 집합 == 정책 파일의 등재 쌍 집합. 클래스만 등재하면 그 클래스가 새 전송 타입을 더 쥐어도 초록이다. 값 타입(`java.net.URI` · `java.net.URLEncoder` · `java.nio.channels.FileChannel` · `FileLock` · `OverlappingFileLockException`)처럼 **바이트를 밖으로 내지 못하는** 타입은 「무해 타입」으로 따로 등재하고 쌍 등식에서 뺀다 — 무해 타입 목록도 정확 집합(관측과 같아야 한다) | 6G 의 보유자 게이트는 클래스 단위 등식이라 한 칸 헐겁다 |
| **D-6G2b-4** | **등재 쌍마다 용도를 적는다**(정책 파일 주석이 아니라 키 구조로 — 예: `collection.transport.holders.<용도>=`). 용도 어휘는 닫힌 셋: `koneps-gate` · `attachment` · `llm` · `ml-grpc` · `run-state-lock`. `koneps-gate` 밖의 용도가 KONEPS 주소로 호출하지 않는다는 것은 이 게이트가 재지 못한다 — **경계 밖**으로 선언한다(아래 위협 모델) | 정적 분석은 목적지를 모른다. 모른다고 적는 편이 막는다고 적는 것보다 낫다 |
| **D-6G2b-5** | **6G 의 두 게이트를 이 게이트 하나로 합친다.** `collection.http-client.*` 와 `collection.transport-bypass.*` 는 새 쌍 등식의 부분집합이 되므로 없앤다. 합치기 전후에 6G 의 변이(KA2·KA3·KA4)가 여전히 RED 임을 잰다 | 같은 것을 재는 게이트 셋은 하나가 낡는다 |
| **D-6G2b-6** | **반사 봉쇄는 기존 게이트를 그대로 쓴다**(`workflow`·`app` production 의 리플렉션 API 참조 금지). 뿌리를 `adapters` 까지 넓힐지는 착수 실측으로 정한다 — 넓혔을 때 걸리는 기존 참조가 0 이면 넓히고, 있으면 그 목록과 함께 운영자에게 묻는다 | `Class.forName` 으로 이름을 문자열로 지으면 타입 참조가 없다 |
| **D-6G2b-7** | **음성 fixture 로 상시 잰다.** KA1·KA12·KA13·KA14·KA15 와 착수 시 고안한 새 우회 ≥5 를 test 소스의 fixture 클래스로 두고, 게이트 술어가 각각을 **걸러 냄**을 단언한다. 술어를 고치지 않고 fixture 만 빼면 그 단언이 RED | verifier 가 매 라운드 손으로 심던 것을 저장소에 둔다 |
| **D-6G2b-8** | **출하 코드 변경은 import 정리뿐이다.** 쓰이지 않는 `import java.net.http.HttpClient` 둘(code-review r5 L-6 — 6G 표적 수정이 이미 지웠으면 해당 없음) 밖의 production diff 는 없어야 한다. 착수 실측에서 **등재되지 않은 실제 전송 호출**이 나오면 멈추고 보고한다 | 게이트 slice 가 제품 거동을 바꾸면 범위가 샌다 |
| **D-6G2b-9** | **게이트 등재 meta-gate 에 올린다.** 새 게이트 test 를 `AppGateRegistrationTest` 계열의 등재 목록에 넣는다 — 빠뜨리면 그 test 가 RED | 6G r5 에서 아키텍처 게이트가 한 커밋 동안 붉은 채 지나갔다(`check` 미실행). 등재는 게이트가 돌지 않는 사고를 막는다 |

## 위협 모델 — 6G-2b 고유 경계 (Phase 2.5 (0))

**방어하는 것**: 저자가 `bidvector..` production 코드에 **관문을 지나지 않는 바깥 호출**을 더하는 것 — JDK · Kotlin 표준 라이브러리 · 클래스패스에
이미 있는 라이브러리(Spring · gRPC · Netty)의 API 를 **직접 참조**해서. 타입을 쥐든 호출 사슬로만 지나든 같다.

**방어하지 않는 것(경계 밖)**:
- 게이트 test · 정책 파일 · 빌드 스크립트를 고치는 저자(M1/1A 의 경계 승계).
- 등재된 보유자(첨부 · LLM · ML gRPC)가 **KONEPS 주소로** 호출하는 것 — 정적 분석은 목적지를 모른다. 통제는 그 보유자들의 주소가 설정에서만 오고
  KONEPS 주소 설정을 읽는 자리가 관문 배선 하나라는 기존 게이트(「서비스 키 원문 설정은 배선 한 곳만 참조한다」)다.
- 이름을 문자열로 짓는 반사 가운데 기존 반사 게이트의 뿌리 밖(D-6G2b-6 에서 정한다).
- JNI · 네이티브 · JDBC 를 거친 DB 쪽 네트워크 기능 · 새 빌드 의존을 더하는 것(빌드 의존 게이트 소관).

이 경계는 6G 의 D-6G-62 문면(「관문 밖에서 … 금지. 변이 KA1~KA3 RED」)을 줄이지 않는다 — KA1 을 포함해 그 요구를 실제로 채운다.

### (1) 열거인가 구성인가

금지는 **패키지 뿌리**(구성)다. 허용은 **관측과의 등식**(구성)이다. 낱개 타입 넷(`ProcessBuilder` 등)만 열거이고, 그것은 `java.lang` 을 뿌리로
금지할 수 없어서다 — 이 넷의 근거를 계약에 적고 새 낱개 타입을 더할 때는 계약을 갱신한다.

### (2) 우회 — 다섯 이상 (착수 시 실측으로 갱신)

1. KA1 — 호출 사슬의 반환 타입으로만 지난다. ← D-6G2b-2.
2. KA12 — `java.beans.Expression`. ← 낱개 타입 금지.
3. KA13 — 프로세스 실행. ← 낱개 타입 금지.
4. KA14 — 비동기 채널. ← `java.nio.channels..` 뿌리.
5. KA15 — Spring 클라이언트. ← `org.springframework.web.client..` 뿌리.
6. Kotlin 확장 함수(`java.net.URL.readText()` — 소유 타입은 `kotlin.io.TextStreamsKt`). ← 인자 타입이 `URL` 이므로 D-6G2b-2.
7. 등재된 보유자 클래스 안에 새 전송 타입을 더 쥔다. ← D-6G2b-3 쌍 등식.
8. typealias 로 이름을 가린다(6G KA4 — 이미 RED). ← 바이트코드에는 원 타입이 남는다. 회귀로 유지.
9. 전송 표면을 쥔 **test 지원 코드**를 production 소스셋에 둔다. ← 뿌리가 production 전체다.
10. `com.sun.net.httpserver` · `sun.net..` 내부 API. ← 착수 실측에서 뿌리에 더할지 정한다(수신 전용이면 무해 타입).

### (2b) 값 획득 축

새 public 표면은 정책 파일의 키뿐이다. 키를 비우면 등식이 RED 가 되므로 「비워서 통과」는 없다. production 의 새 public 선언은 0 이어야 한다.

### (3) 과잉·미달

- 과잉 위험: 뿌리가 넓어 무해한 참조가 많이 걸릴 수 있다. 무해 타입 목록이 열 개를 넘으면 뿌리 설계를 다시 본다(착수 실측 뒤 판단).
- 미달 경계: 목적지 구분은 하지 않는다(경계 밖 선언).

## 운영자 승인 필요 (착수 전)

- **A-1 착수 시점**: 6G 머지 뒤 아무 때나. 실수집과 병행 가능하다(수집 코드 무변경). 다만 Gradle `check` 가 실수집 실행과 겹치지 않게 한다.
- **A-2 D-6G2b-6**: 반사 게이트의 뿌리를 `adapters` 까지 넓힐지 — 착수 실측 결과와 함께 묻는다.

## in_scope

- `app/src/test/kotlin/bidvector/app/architecture/**`
- `config/quality/architecture-policy.properties`
- `app/src/main/kotlin/bidvector/app/wiring/CollectionWiring.kt` · `OpeningCollectionWiring.kt`(쓰이지 않는 import 제거만)
- `reports/evidence/m6/6g2b/**` · `milestone-6.md`(착수·종결 문단만)

**out_scope**: `adapters/src/main/**` · `workflow/src/main/**` · `procurement/src/main/**`(수집·관문·실행 상태 코드 무변경) · `ml-engine/**` ·
`db/migration/**` · `contracts/**` · `docker/**` · `reports/evidence/m6/6g/**`.

## acceptance

**정정(2026-10-03)**: 현 CI `check` job 의 명령은 `./gradlew --no-daemon check` · `./gradlew --no-daemon qualityBaseline` · `./tools/one-command-check.sh`(`.github/workflows/ci.yml`)다 — 아래 초안의 `clean`·`--no-build-cache` 는 쓰지 않는다. 게이트 술어를 바꾸는 slice 이므로 **변이가 실제 적용됐는지 `git diff --numstat` 로 먼저 재고** 돌린다(CLAUDE.md 2026-09-19).

CI `check` job 명령 그대로(`clean` → `check --no-build-cache` · `qualityBaseline` · `one-command-check.sh`). container job 은 production 코드가
바뀌지 않으면 트리 동일성으로 갈음한다(import 제거만 있으면 실행). `commands.md` 에 음성 fixture 별 결과와 등재 쌍 수를 적는다.

## rollback

in_scope 경로 한정 `git restore --source=<base> --staged --worktree --`. 공유 파일 `architecture-policy.properties` 는 **커밋 해시 hunk 격리** —
다른 slice 가 같은 파일을 만지므로 수동 절차를 미리 적는다. `milestone-6.md` 는 문단 블록 삭제. 버릴 clone 에서 ①~⑥ 실측, ⑥ 은 게이트 test 다.

## 리뷰 레인

`verifier`(음성 fixture 밖의 새 우회 고안 · 등재 쌍이 관측과 같은지 · 합친 게이트가 6G 변이를 여전히 잡는지) + `code-reviewer`(sonnet).
**게이트 술어를 바꾸는 slice 이므로 수정 라운드의 모든 커밋이 severity 와 무관하게 표적 재검증 대상이다.** Codex 없음.

## 하네스 레인 변경

(착수 뒤 리뷰 요청 시점마다 등재)

- **팀장(2026-10-03, 착수)**: `milestone-6.md` 착수 문단(공유 파일 — rollback 은 hunk 역적용). 계약 파일 이 커밋.

## OPEN 수령·신설 (예상)

| OPEN | 처분 |
|---|---|
| `OPEN-6G-TRANSPORT-GATE-HARDENING` | **이 slice 가 닫는다** |
| `OPEN-6G-GATE-REGISTRY-KONEPS` | D-6G2b-9 로 함께 닫는지 착수 실측에서 판단 |
| (신설 가능) `OPEN-6G2B-DESTINATION-BINDING` | 등재 보유자의 목적지 구분 — 경계 밖으로 둔 것을 등재만 |
| `OPEN-6G2E-APP-HTTP-IMPORT-CONTRACT` | **6G-2c 로 이관(2026-10-03)** — Python `import-linter` 계약(`ml-engine/pyproject.toml`)이라 Python 레인·`ml-engine` job 소관이고 이 slice 는 Kotlin `check` job 하나로 닫는다. 초안 처분 「이 slice 가 닫는다」의 내용: `ml-engine/pyproject.toml` import-linter 「app 은 DB·HTTP·업무 모듈을 모른다」 계약의 `forbidden_modules` 가 서드파티 다섯만 열거해 `urllib.request`(표준 HTTP)가 지나간다(6G-2e cr r2 M). 금지 목록을 패키지 뿌리·표준 HTTP 모듈까지, test 쪽 AST sweep(6G-2e 임시 자물쇠)과 등식 |

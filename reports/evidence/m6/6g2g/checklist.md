# M6/6G-2g — 항목과 잠금

수령한 게이트 OPEN 일곱이 **지금 무엇으로 닫혀 있는가**를 현재형으로 적는다. 라운드가 무엇을 했는지는
적지 않는다 — 그것은 `git log` 와 리뷰 verdict 가 갖는다.

## 수령 OPEN 과 처분

| OPEN | 잠그는 자리 | 처분 |
|---|---|---|
| `OPEN-6G2B-COLLECTION-DEPTH` | `collection.depth.<축>` 열하나 + 축 모집단·민감도 양방향 등식 | **닫음** |
| `OPEN-6G2B-FOLDING-UNIFICATION` | 접기 함수 **하나**(이름 절단) — `enclosingClass` 접기와 복사 넷 삭제 | **닫음** |
| `OPEN-6G2B-REFLECTION-ROOT-DOMAIN` | 반사 뿌리 = `layer.*` 전 층 도출(domain 여섯 포함) | **닫음** |
| `OPEN-6G2B-ALLOWED-PACKAGE-EGRESS` | 전송 뿌리 + `java.nio.file`, 낱개 `java.io` 여섯, 쌍 41 | **파일 시스템 갈래만 닫음** — 라이브러리 로더·StAX·bean factory 는 경계 유지(B-2) |
| `OPEN-6G2B-HOLDER-INTERNAL-SURFACE` | 3층 `collection.transport.member-surface` 쌍 18 | **닫음** |
| `OPEN-6G-GATE-REGISTRY-KONEPS` | build-logic `gateRegistrationGate` — 모듈 전수 양방향 | **닫음** (원 문장이 없어 이 slice 가 정의, D-6G2g-6) |
| `OPEN-6G2G-REGISTRATION-PACKAGE-COVER` | 같은 task — 패키지 열거가 아니라 모듈 전수 | **닫음** |

## 항목별 잠금 — 무엇이 지금 붉게 만드는가

| 항목 | 지금의 잠금(현재형) | 그 잠금을 재는 변이 |
|---|---|---|
| ① 등재 등식 | 모듈의 **컴파일된 test 클래스 전수 ∖ 제외 == 등재**. 모집단은 `@Test`·`@TestFactory`·`@TestTemplate`(메타 애노테이션을 클래스패스에서 푼다), 제외는 **build 사실**(Gradle 필터 · 실행 조건 애노테이션 뿌리)뿐. 제외 자신도 선언과 등식이다 | 등재 삭제 · 미등재 클래스 · 한 파일의 둘 · 조건 애노테이션 부착 |
| ① 입력 선언 | task 가 정책 파일을 `@InputFile` 로 든다 — 그 파일만 바뀐 편집도 다시 돈다 | 선언 강등 시 **같은 편집이 UP-TO-DATE 초록**(거짓 초록 실측) |
| ② 두 벌 제거 | 앞 판의 등식 test 여덟이 없다 — 같은 등식이 두 벌이면 갈린다 | 이전에 덮이지 않던 패키지의 등재 삭제가 이제 RED |
| ③ 수집 깊이 | 깊이가 코드 상수가 아니라 `collection.depth.<축>`(닫힌 어휘 둘). 축 enum ↔ 키 **양방향**, 두 깊이의 관측이 갈리는 축 집합 == 실측 표 | 키만 바꾸기 · 구현이 깊이 무시 · 유령 축 키 |
| ④ 접기 | 접기 함수가 **하나**다. 등재 둘이 그 규칙으로 접힌 이름이다 | 두 축의 관측을 앞 판 접기로 되돌리기 |
| ⑤ 반사 뿌리 | 뿌리가 `layer.*` **전 층** 도출이다(손 목록이 아니다). 쌍 등식은 그대로 | domain production 에 반사 한 줄 |
| ⑥ 파일 시스템 출구 | `java.nio.file` 은 전송 **뿌리**(구성), `java.io` 파일 타입 여섯은 **낱개 열거**. 1층 허용 목록에서 두 이름이 빠졌다 | 미등재 production 클래스가 경로를 쥐고 쓰기 + 영구 음성 fixture 둘 |
| ⑦ 멤버 표면 | 등재 보유자의 비-private 멤버 중 「시그니처는 전송 타입을 말하지 않는데 몸이 전송 멤버를 부르는」 것 == 등재, 양방향 | 보유자에 전송 타입 없는 송신 멤버 추가 |

## 등식의 모양 — 앞 판과 무엇이 다른가

| 축 | 앞 판 | 지금 |
|---|---|---|
| 등재 등식의 모집단 | 소스 **파일 이름**(`*Test.kt`), 패키지마다 손으로 건 술어 | **컴파일된 클래스**, 모듈 전수 |
| 등재 등식의 덮개 | adapters 열두 패키지 중 여섯 | 모듈 아홉 전부 + 빈 모듈 |
| 등식의 방향 | adapters 여섯은 **단방향**(누락만) | 전부 **양방향** |
| 등재 장부의 뜻 | 「게이트 증거 test 선별」(선별이라는 사실이 기계로 적히지 않았다) | 「CI 가 돌려야 하는 test 전수」 |
| 제외 | 없음 — 누락과 구별되지 않았다 | **build 사실**에서만, 그 자신도 등식의 한 변 |
| 수집 깊이 | 전송·반사만 깊고 나머지는 코드 안의 기본값 | 축마다 계약 파일이 정하고 등식이 잠근다 |
| 접기 | 관례 셋 · 구현 일곱 자리 | 함수 **하나** |
| 반사 뿌리 | `workflow`·`app`·`adapters` | production **전 층** |
| 파일 시스템 출구 | 1층 허용 패키지 안이라 무판정 | 2층 쌍 등식 |
| 보유자 내부 | (클래스, 타입) 해상도 밖 | 3층 (클래스, 멤버) |

## 리뷰 요청 조건

- [x] 구현 diff 가 커밋되어 base/head 고정 — `git status --porcelain -- <in_scope 개별 인자>` 빈 출력.
      **양성 대조 1회**: in_scope 파일 하나에 줄을 더해 잡히는 것을 확인하고 **비파괴 절삭**으로 되돌렸다
      (`git checkout --` 을 쓰지 않았다).
- [x] acceptance 가 전부 exit 0 으로 `commands.md` 에 기록됨 — CI `check` job 명령 그대로, 부분 게이트 없음.
- [x] test·lint·type·architecture·contract 명령 통과 — 전건 `check` 안에서 함께 돈다.
- [x] 변경된 fixture 와 정책 version 의 근거 — `gate-tests.properties` 는 구조와 뜻이 바뀌어 `policy.version`
      2 → 3(머리말이 근거를 든다). `architecture-policy.properties` 는 **판을 올리지 않았다**: 키 계열이
      늘었을 뿐 기존 키의 모양이 바뀌지 않았다(한 slice 가 판 하나라는 관례는 6G-2b 가 썼고, 이 slice 는
      그 파일의 모양을 바꾸지 않는다). 음성 fixture 는 하나 늘었고(`FileSystemEgressSamples.kt`) 변이 표와
      덮개 양방향 등식이 그것을 든다.
- [x] 알려진 제한과 rollback 기록 — `commands.md` 「알려진 제한」 · `rollback.md`.
- [x] 비밀값 스캔 — 참조형으로 실행, 매치 없음.

## 이탈

1. **깊이 축은 계약 표의 일곱이 아니라 열하나다**(D-6G2g-15 ③). 표 밖에 깊이를 쓰는 호출 자리가 넷 더
   있었고, 선언하지 않으면 그 자리는 하드코딩으로 남는다. 넷 다 깊이 비민감이라 등재 변화는 없다.
2. **`collection-procurement` 축이 계약 표의 `FULL` 이 아니라 `OWNER_ONLY` 다**(D-6G2g-15 ①). 착수 실측의
   상수 풀 근사가 과소 추정했고, ArchUnit 실측에서 `FULL` 은 그 게이트가 **이름 붙이지 못하게 하려는 바로
   그 타입들**을 허용 집합에 들인다. 운영자 수용.
3. **domain 순수성에서 깊은 수집이 더하는 좌표는 둘이다**(D-6G2g-15 ②) — 계약 문면의 하나가 아니다.
   둘 다 저자가 쓴 좌표가 아니라 결정 자체는 그대로다.
4. **⑤ 가 test 이름 둘을 함께 고쳤다** — 뿌리를 넓히면서 앞 판의 범위를 그대로 든 이름이 남았다.
   변이 실측에서 드러났다(RED 메시지는 뿌리 아홉을 나열하는데 이름은 셋을 말했다).
5. **③ 의 첫 커밋이 `sizeGate` 를 넘겼다** — 축 열하나의 분배 함수가 55 줄. 전건 `check` 가 잡았고
   축별 헬퍼 분할로 닫았다. `commands.md` 의 acceptance 표가 그 실패와 통과를 둘 다 든다.

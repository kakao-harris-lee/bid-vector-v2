# M6/6G-2g — 게이트 술어 확장 slice: 6G-2b 가 남긴 게이트 OPEN 일곱 (계약 초안, 2026-10-04, 팀장)

- base: `31721008`(PR #59 머지 커밋 = `main`) · 브랜치 `m6-6g2g/2026-10-04` · worktree `bid-vector-v2-m6-6g2g`
- 정본: 이 파일. 결정 ID `D-6G2g-N`. 운영자 결정은 「운영자 승인」 절.
- 레인: K 하나(`kotlin-implementer`, sonnet). Python 없음. 판정은 `verifier`(opus) + `code-reviewer`(sonnet) 병렬.
- 성격: **게이트·계약형 slice** — Phase 2.5 설계 검토 필수((0) 경계 → (1) 열거/구성 → (2) 우회 ≥5 → (2b) 값 획득 축 → (3) 과잉·미달). **게이트 술어를 바꾸는 모든 커밋은 severity 무관 표적 재검증**(6G-2b 와 같다).
- 실수집과의 관계: test·정책 파일만 바꾼다(production diff 0 기대). 실행 상태 바이트·스냅숏 스키마에 닿지 않으므로 수집 중 머지 가능.

## 왜 이 slice 인가

6G-2b 가 전송 표면 게이트를 두 층으로 세우면서 **같은 모양의 간극이 형제 게이트에 남아 있음**을 실측했고(D-6G2b-20·23·35·36·39·44), 6G-2c 가 등재 덮개의 간극(D-6G2c-38 ④·42 ⑤)을 더했다. 전부 **술어 확장**이라 6G-2c 의 D-13(게이트를 넓히지 않는다)에 걸려 넘어왔다(운영자 결정 A-3 ②).

## 수령 OPEN 일곱과 항목

| OPEN | 출처 | 간극 | 이 slice 의 항목 |
|---|---|---|---|
| `OPEN-6G2B-COLLECTION-DEPTH` | D-6G2b-44 | 형제 정확 집합 게이트(`raw-access`·`key-hash`·`usecase`·domain 허용 목록)가 **소유 타입만** 수집 — 전송·반사만 깊다(인자·반환·호출 대상까지) | D-1 수집 깊이를 공통 수집기로 통일, 쌍 등식 재관측(늘어나는 쌍은 전부 등재·실측 — 6G-2b 의 14 선례) |
| `OPEN-6G2B-FOLDING-UNIFICATION` | D-6G2b-35 | `outermostClass()` 접기(전송·바깥 참조·반사) vs `enclosingClass` 접기(`key-hash`·`injection`) 가 다름 — 등재 `NoticeKeyHash$Companion`·`Resolution$Resolved` 가 반증 | D-2 접기 규칙 하나로 통일 + 영향받는 등재 재관측(재등재는 양방향 등식이 강제) |
| `OPEN-6G2B-REFLECTION-ROOT-DOMAIN` | D-6G2b-20·43 | 반사 쌍 등식의 뿌리가 `workflow`·`app`·`adapters` 까지 — domain 계열(`procurement`·`decision`·`qualification`·`settlement`·`shared-kernel`)은 순수성 게이트의 기본 거부에만 기댄다(6F-9 `procurement` 잔여 포함) | D-3 **착수 실측** 뒤 운영자 결정 B-1: domain 모듈의 기존 반사 참조 수를 재고 0 이면 뿌리 확장(등재 0), 아니면 쌍 등재 |
| `OPEN-6G2B-ALLOWED-PACKAGE-EGRESS` | D-6G2b-36·39 | 허용 패키지 **안**의 출구 — `java.io` 의 `/dev/tcp`·FIFO, `java.nio.file` 원격 FS, 라이브러리 자체 로더(`com.networknt.schema` 원격 스키마), StAX 외부 엔티티, Spring bean factory | D-4 **경계 유지 + 구조 한 수**: 낱개 열거를 늘리지 않고, 「파일 시스템 출구」는 경로 리터럴 게이트(`/dev/`·`file:` 스킴)가 아니라 **`Path`/`File` 생성 자리를 등재 보유자로 한정**하는 쌍 등식으로 — 설계 검토 (1) 이 열거인지 구성인지 판정한다. 라이브러리 로더·StAX·bean factory 는 위협 모델 「방어하지 않는 것」 유지(운영자 결정 B-2) |
| `OPEN-6G2B-HOLDER-INTERNAL-SURFACE` | D-6G2b-23, 6G-2c vr I-1 | 등재 보유자 안의 **String 시그니처 send** 류·internal 멤버를 반사로 부르는 길(`release$bid_vector_adapters`) — (클래스, 타입) 해상도 밖 | D-5 등재 보유자 public/internal 멤버 중 전송 타입을 받지 않는 send 류를 (클래스, 멤버) 층으로 세는 3층 — 보유자 28 멤버 전수(착수 실측에서 수를 낸다) |
| `OPEN-6G-GATE-REGISTRY-KONEPS` | D-6G2b-15 | 게이트 등재 장부 전반(koneps 포함) — 쌍 등식이 갈음하지 못함 | D-6 등재 장부와 소스의 양방향 등식을 **모든 모듈·모든 패키지**로(아래 D-7 과 한 묶음); 착수 실측에서 이 OPEN 의 원 문장(6G) 을 찾아 수령 범위를 못 박는다 |
| `OPEN-6G2G-REGISTRATION-PACKAGE-COVER` | D-6G2c-38 ④·40 ③·42 ⑤ | adapters 등재 등식이 패키지 여섯만 덮고 여섯(contract·extraction·koneps·persistence·snapshot·strategy)은 안 덮음, 미등재 test 일곱 · `adapters/build.gradle.kts` 의 `tasks.test` 가 `gate-tests.properties` 를 입력으로 선언하지 않음(app 은 선언) | D-7 등재 등식을 **패키지 열거가 아니라 모듈 전수**(소스 트리에서 `@Test` 클래스 기계 수집 == 등재 집합)로 바꾸고, 세 모듈 test task 에 `gate-tests.properties` 입력 선언(6G-2c D-33 선례) + 「선언 없으면 UP-TO-DATE 초록」 변이 실측 |

## 위협 모델 경계 (Phase 2.5 (0)) — 초안

**방어하는 것**: 등재 밖 전송·반사·raw 접근·키 해시·use case 호출이 production 코드에 생기면 게이트가 붉는다(수집 깊이·접기·패키지 덮개의 간극으로 조용히 통과하는 길을 닫는다). **방어하지 않는 것**: 빌드 스크립트를 임의로 쓰는 저자(6G 경계) · 허용 패키지 안의 라이브러리 자체 로더·StAX·bean factory 출구(D-4) · 동적 import 류(6G-2c 경계).

## in_scope (초안 — 착수 실측 뒤 확정)

- `app/src/test/kotlin/bidvector/app/architecture/**`(게이트 술어) · `app/src/test/kotlin/bidvector/archfixture/**`(음성 fixture) · `adapters/src/test/kotlin/bidvector/adapters/**/…GateRegistrationTest.kt`(등재 등식) · `config/quality/architecture-policy.properties` · `config/quality/gate-tests.properties` · `app|adapters|workflow/build.gradle.kts`(**test task 입력 선언 블록만**) · `reports/evidence/m6/6g2g/**` · `milestone-6.md`(착수·종결 문단만).
- **out_scope**: production 코드 전부(변경이 필요해지면 멈추고 계약 갱신) · `ml-engine/**` · 실행 상태·스냅숏 형식.

## acceptance

CI `check` job 명령 그대로(`./gradlew --no-daemon check` · `qualityBaseline`). production 무변경이면 container job 생략(6G-2e 선례, 사유 등재). 항목마다 변이 ≥1 RED(numstat 적용 확인), 게이트 술어 커밋은 verifier 표적 재검증.

## rollback

in_scope 경로 한정 `git restore --source=<base>`; 공유 파일(`architecture-policy.properties`·`gate-tests.properties`·`build.gradle.kts` 셋·`milestone-6.md`)은 커밋 해시 hunk — 목록은 실측 HEAD 에서 `git log` 로 낸다(6G-2c 교훈). ①~⑥ 버릴 clone.

## 운영자 승인 (착수 전 결정 필요)

- **B-1** 반사 뿌리를 domain 계열까지 넓힐지(착수 실측 수치 뒤) — (가) 넓힌다(0 이면 등재 0) · (나) 순수성 게이트에 맡기고 OPEN 유지.
- **B-2** 허용 패키지 안 출구 — (가) 파일 시스템 출구만 구조 한 수(D-4) · (나) 전부 경계 유지(코드 0, 문면만).
- **B-3** 등재 등식을 모듈 전수로 바꿀 때 **의도적 미등재**(fixture·base 클래스)는 어떻게 표시할지 — (가) `@Test` 0 인 클래스는 자동 제외 · (나) 명시 제외 목록(제외도 등식의 한 변).

## 하네스 레인 변경

(착수 뒤 리뷰 요청 시점마다 등재)

## 입력 OPEN (이 slice 가 닫지 않는 것)

- `OPEN-6G-REVIEW-FOLLOWUPS` 잔여 열하나 · `OPEN-6G2D-*`·`OPEN-6G2F-MAX-PAGES-PROVENANCE` · 명칭 셋 · `torn` 위치 · 5,000자 경로 OSError 문면 → **6G-2c-형식**(스냅숏 추출 뒤, ≈2026-10-27).
- `OPEN-6G2C-BUSY-SEED-ORDER` → 6G-2c-형식(seed 순서는 실행 상태 판독 경로).
- `OPEN-6G2F-NOTICE-LIST-ROWS` → M7 뒤 값 slice. `OPEN-6B3-RAW-OBSERVATION-RETENTION` → 6B-3.

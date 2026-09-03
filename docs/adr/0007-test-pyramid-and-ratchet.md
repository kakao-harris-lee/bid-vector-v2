# ADR 0007 — 테스트 pyramid, mutation 대상, 크기·경계 래칫

- **상태**: **결정** — M0 slice 0D가 Codex `approve`로 닫혔다
  (`reports/evidence/m0/0d/codex-review-20260829T222217Z.json`, `reviewed_base` `998dc217` …
  `reviewed_head` `df056259`, 2026-08-29, `model_reasoning_effort=high`). 갱신 시점은
  **2026-08-30 · M0 종료 slice 0E**다 — 0D 종료 뒤에도 이 줄이 *"제안됨 … 대기"*로 남아
  **문면이 사실을 따라가지 않았다.**
  **`milestone-0.md` 완료 조건의 「사용자 명시 승인」을 받았다** — **운영자, 2026-08-31**.
  물음 *"「M0 승인」이 ADR 0001~0009 채택까지 덮는가"*에 **「덮는다 — ADR 채택 포함」**.
  **이 승인은 Codex `approve`가 만든 것이 아니다** — 위 0D `approve`와 별개로 운영자에게
  직접 물어 받았다. 기록의 **정본**은 `milestone-0.md` 「승인에 드는 것」의 ADR 행이며,
  같은 승인의 다른 대상(활성 `OPEN` 이월)은 `docs/discovery/capability-map.md` §14다.
- **작성일**: 2026-08-28
- **대응**: `milestone-0.md` §"Slice 0D" 결정 **7** (테스트 pyramid와 mutation 대상)
- **legacy 기준 commit**: `ed4b06c`
- **`OPEN-OPS-07`과의 관계**: 이 ADR이 OPS-21의 **아키텍처 규칙 강제** 행
  (ArchUnit · Konsist · Detekt)의 조사 결과를 기입한다. 나머지 네 행은 **ADR 0005**가
  기입하며, 그중 **advisory lock 행은 판정이 끝나지 않았다**(ADR 0005 §3.2).
- **관련 ADR**: 0006(경계) · 0002(mutation 대상의 출처) · 0004(프로덕션 엔진 테스트)

---

## 1. 맥락

### 1.1 승인된 우선순위

`v2-지침서.md` §6이 순서를 규정했다 — ① 승인된 예제 기반 unit test ② 경계·결측·단위
혼입을 생성하는 property test ③ 계약 consumer/provider test ④ **mutation test로 중요
rule이 실제로 보호되는지 확인** ⑤ DB/broker/gRPC adapter integration test ⑥ mock KONEPS와
fake notification을 사용한 E2E ⑦ 승인된 환경에서만 live read probe.

§5가 도구와 규율을 규정했다 — ~~JUnit 5~~ **JUnit 6**(아래 1.1.1) + Kotest/AssertJ,
property test, Testcontainers.
**domain test에서는 mock framework를 쓰지 않고 값과 fake port를 쓴다. adapter test에서만
MockK 또는 test server/container를 쓴다.**

#### 1.1.1 테스트 플랫폼은 JUnit 6이다 — 운영자 결정 2026-09-02

- **결정**: 테스트 플랫폼 라인을 **JUnit 6**으로 옮긴다. `v2-지침서.md` §5의 문면도 함께
  개정됐고 **그쪽이 정본**이다 — 이 절은 채택 판정과 그 대가를 적는다.
- **결정자·시점**: **운영자, 2026-09-02.** Codex 리뷰 7차가 *"승인된 `v2-지침서.md` §5와
  ADR 0007은 테스트 플랫폼을 JUnit 5로 규정하지만 구현은 JUnit 6.1.3과 `archunit-junit6`을
  고정했다"*를
  medium 으로 지적했고, **구현자가 쓴 slice 계약이 승인 명세를 갈아치운 상태**였다.
  결정은 구현 문서가 아니라 **승인 문서 개정으로** 남긴다.
- **근거 — 이 이동은 이 ADR이 고른 것이 아니라 `Q1`의 귀결이다.** 운영자 결정 2026-08-29
  `Q1`이 Spring Boot 라인을 **4.x**로 옮겼고, Boot 4.x BOM이 관리하는 jupiter가 이미 6이다.
  플랫폼 하한이 그 결정과 함께 올라간 것이지 테스트 도구를 새로 고른 것이 아니다.
- **정합 실측**(조사 노트 `_workspace/m1-1a/02_survey_toolchain.md` §9 · §3.5):
  `archunit-junit6`이 1.5.0에서 신설됐고(그 이전 `archunit-junit5`는 JUnit Platform 1.x에
  묶여 JUnit 6에서 쓸 수 없다), Kotest 6.2.4는 runner를 `kotest-runner-junit5`/`-junit6`로
  나눠 배포하며, `kotest-property`는 junit-platform에 의존하지 않아 러너 결합 없이 쓴다.
- **대가 — `jqwik`이 탈락한다.** 1.10.1이 junit-platform **1.14.4**에 묶여 있고 2.x 라인이
  없다. §5가 요구하는 property test는 `kotest-property`가 든다. **§1.1의 「property test」
  요구 자체는 바뀌지 않는다** — 그것을 드는 라이브러리만 바뀐다.
- **승인 시점에 고정된 값**: 테스트 플랫폼 **JUnit 6.1.3**, 아키텍처 테스트 러너
  **`com.tngtech.archunit:archunit-junit6` 1.5.0**. **이것은 승인 당시의 사실이라 낡지 않는다** —
  이후 M1이 버전을 올리면 그것은 slice 계약(`reports/evidence/m1/1a/scope.md` D-1)이 들고,
  이 줄은 「무엇이 승인됐는가」로 남는다. 두 자리의 역할이 다르다.

#### 1.1.2 게이트 위협 모델의 경계 — 운영자 결정 2026-09-03

- **결정**: 게이트가 **방어하는 것**은 모듈·도메인 **소스**와 **의존 선언**을 통해 들어오는
  경계·크기·언어·형식 위반이다(모듈 build script 의 평범한 선언 포함). **방어하지 않는 것**은
  게이트의 **정의·배선 자체**를 바꾸는 편집이다 — `build-logic/**`·`config/quality/*.properties`·
  루트 `build.gradle.kts` 수정, `enabled = false`, `check` 의존 제거, `-x` 로 게이트 제외,
  outputs 를 선언하지 않고 산출물 디렉터리에 직접 쓰는 task. **정본 문면은 `milestone-1.md`
  「게이트 위협 모델」**이고 이 절은 근거를 든다.
- **결정자·시점**: **운영자, 2026-09-03.** 계기는 Codex 리뷰 8차(high 1)와 그에 대한 설계 검토
  3차다.
- **근거 — 경계는 선택이 아니라 강제다.** 게이트가 같은 저장소의 빌드 스크립트인 한, 「빌드
  스크립트를 임의로 쓰는 저자」를 모델에 두면 **어떤 게이트 집합도 닫히지 않는다.** 설계 검토가
  Codex 가 쓴 공격보다 **훨씬 싼 둘**을 실측했다 — `gradle :m:probe -x :m:classes` 가
  `BUILD SUCCESSFUL` 로 게이트를 건너뛰고, 모듈 build script 한 줄
  (`tasks.named("sizeGate") { enabled = false }`)이 수제 `JavaCompile` 을 짜는 것보다 압도적으로
  싸다. 우회 시도 아홉의 표와 그 실측은 `_workspace/m1-1a/18_design-review-anchor.md` §1·§2 가
  갖는다.
- **요구 축소가 아니다.** 승인 문면 어느 것도 적대적 저자를 상정하지 않는다 —
  `milestone-1.md` 완료 조건은 **소스 수준 fixture**를 요구하고, §1.2 와 `v2-지침서.md` §5
  「회귀의 구조적 방지」는 **회귀(drift)** 를 말한다. `ADR 0006` §4 의
  *"게이트가 있다는 주장이 아니라"* 로 시작하는 문장도 **증거 기준**이지 적대자 모델이 아니다.
- **파급**: 이 경계 밖 계열(`-x`·`enabled = false`·`build-logic` 편집)의 리뷰 finding 은 수정
  대상이 아니라 **경계 참조로 답한다.** 경계 안에서 열려 있는 것은 계속 수정 대상이다.
- **개정 2026-09-03 — 도구 설정 파일을 경계 밖에 명시한다.** **결정자**: 운영자. **시점**:
  2026-09-03. **근거**: `.editorconfig`·`config/quality/**`·kover 필터·convention script 의
  배선은 게이트가 **무엇을 거르는가**를 정의한다. `[**/domain/**] ktlint = disabled` 한 줄이면
  형식 게이트가 그 트리에서 꺼지므로 `enabled = false` 와 성질이 같고, 그 편집을 모델 안에 두면
  「게이트가 자기 설정을 지킨다」는 요구가 되어 §1.1.2 의 강제 논증이 그대로 적용된다.
  현행 `[**/build/generated-sources/**] ktlint = disabled` 예외도 **게이트 정의의 일부**로
  인정한다 — 그 경로는 `build/` 아래라 소스 관례 밖이고 레이아웃 고정과 충돌하지 않는다.
  표면 목록의 출처는 `_workspace/m1-1a/22_design-check-layout.md` §6 이다.
- **개정 2026-09-03 — 게이트를 실행하는 task 의 실행 집합을 줄이는 설정을 경계 밖에 명시한다
  (Codex 12차 high).** **결정자**: 운영자. **시점**: 2026-09-03. **근거**: Codex 12차 high 가
  `tasks.test { filter { excludeTestsMatching("*ArchitectureGateCatchesViolationsTest") } }`
  한 줄로 위반 fixture 를 평가하는 test class 만 실행에서 빼는 경로를 실측했다 — `check` 는
  초록이지만 게이트가 잡는다는 증거를 아무도 평가하지 않는다. **위 `enabled = false` 항목과
  같은 등가성 논증**이 `Test.filter`(`excludeTestsMatching`·`excludePatterns`)·`--tests` 선택·
  게이트 test 를 도는 task 의 `onlyIf` 에도 적용되므로 이 계열을 경계 밖에 더한다. **그럼에도
  `gateExecutionGate`(policy `config/quality/gate-tests.properties`)가 `Test.filter` 계열을
  구성상 잡는다** — 경계는 그 단언 자체를 끄는 편집에만 적용된다. 정본 문면은 `milestone-1.md`
  「게이트 위협 모델」이고 이 절은 근거를 든다.

### 1.2 래칫만으로는 막히지 않았다는 관찰

`v2-지침서.md` §2:

> | Ruff/mypy/pytest/design ratchet가 있어도 회귀 발생 | green suite 외에 property, mutation,
> contract, E2E 증거 필요 |

legacy에는 `scripts/design_ratchet.py`와 `tests/design_ratchet_baseline.json`이 있고
(§2.1 조사 앵커의 「비대화 방지」 행) **그럼에도** `regression-ledger.md`가 등재한 회귀가
났다. **이 ADR은 래칫을 채택하되 그것이 충분하지 않다는 관찰을 결정에 반영한다.**

### 1.3 파일 래칫이 우회된 실물

legacy가 mixin 합성으로 파일 한도를 우회한 형태를 스스로 문서화한다(ADR 0001 §4.3 인용).
파일 줄 수는 한도 안이지만 **합성된 클래스의 실제 크기는 한도를 크게 넘는다**
(`commands.md` **C-5.2d**). 크기 축을 파일 하나로만 두면 분할이 우회로가 된다.

---

## 2. 결정

### D-1. `v2-지침서.md` §6의 우선순위를 그대로 채택한다

이 ADR은 순서를 바꾸지 않는다. 더하는 것은 **각 층이 무엇을 소유하는지**다.

| 층 | 이 프로젝트에서의 소유 |
| --- | --- |
| unit (예제 기반) | 승인된 명세와 authoritative fixture의 기대값. **legacy 출력이 아니다**(`v2-지침서.md` §1) |
| property | 경계·결측·**단위 혼입**을 생성한다 — ADR 0002가 타입으로 막는 것을 값으로도 흔든다 |
| contract | ADR 0003의 `.proto` consumer/provider. `milestone-2.md`가 **breaking mutation을 실제로 잡는지**를 완료 조건으로 둔다 |
| **mutation** | D-3의 대상 목록 |
| adapter integration | ADR 0004 D-1 — **프로덕션 엔진(Testcontainers PostgreSQL)** |
| E2E | mock KONEPS와 fake notification |
| live read probe | 승인된 환경에서만. `v2-지침서.md` §8의 안전 규칙 |

### D-2. differential test는 보조 도구이고 판정 근거가 아니다

`v2-지침서.md` §6: 일치는 합격을 자동 보장하지 않고, 불일치는 `Python defect` /
`V2 defect` / `intentional redesign` / `insufficient evidence` 중 하나로 **판정하고 근거를
남긴다**. **승인되지 않은 Python 출력으로 V2 golden을 자동 생성하지 않는다.**

`regression-ledger.md` `R-ML-07`이 legacy에서 그 반대 형태를 등재했다 — *"golden 기대값
일괄 재생성 명령이 존재하고 테스트가 스스로 성격을 자인한다."*

### D-3. mutation 대상은 회귀 ledger가 정한다

**선정 기준**: `docs/discovery/regression-ledger.md`가 등재한 계열 중, 예방 제약이
**타입·계약·정책 데이터**로 표현되는 규칙. 그 규칙을 훼손하는 mutation이 테스트에서
생존하면 예방 제약이 실제로는 작동하지 않는다는 뜻이다.

| # | mutation 대상 | 근거 계열 | 훼손했을 때 살아나면 안 되는 것 |
| --- | --- | --- | --- |
| **M-1** | 금액 basis의 자리 바꿔치기 | `R-BASIS-01`·`02`·`03`·`04` | 기초금액 자리에 추정가격이 들어가는 것이 컴파일·테스트를 통과하지 못한다 |
| **M-2** | rate scale 변환 지점 이동·중복 | `R-RATE-01`·`03`·`05` | 도메인 안에서 크기로 단위를 추측하는 경로가 생기면 실패한다 |
| **M-3** | 측정 불가/부재를 숫자로 접기 | `R-RATE-04`·`R-FLOOR-05`·`R-PROV-08` | `Unmeasurable`·`Uncertain`·미정산이 `0`이나 `null`로 합쳐지지 않는다 |
| **M-4** | provenance first-match 순서 뒤집기 | `R-PROV-02`·`03`, capability DEC-08 | 순서가 바뀌면 분류가 달라지고 테스트가 그것을 잡는다 |
| **M-5** | 파생값이 원본을 덮게 하기 | `R-PROV-04`, `milestone-3.md` 완료 조건 | *"derived fact가 authoritative fact를 덮는 mutation이 실패"* |
| **M-6** | 법정 하한 적용 tier·범위 바꾸기 | `R-FLOOR-01`·`02` | 선언과 실행이 어긋나면 실패한다 |
| **M-7** | 자격 group AND/OR와 별칭 fold 뒤집기 | `R-QUAL-01`·`02`·`03` | 과차단·과허용 어느 방향도 살아남지 않는다 |
| **M-8** | outbox 전달 의미 훼손 | capability OPS-03 acceptance | 트랜잭션 밖 등록, dedupe 미수렴, 무한 재시도가 실패한다 |
| **M-9** | 계약 breaking change | `milestone-2.md` 완료 조건 | compatibility gate가 실제로 잡는다 |
| **M-10** | 금지 import·순환 의존 주입 | ADR 0006, `milestone-1.md`·`milestone-5.md` 완료 조건 | 경계 게이트가 실제로 잡는다 |

**M-1~M-9는 mutation test**(도구가 코드를 변형)이고, **M-10은 의도적 결함 fixture**
(사람이 위반을 심고 게이트가 잡는지 본다)다. `milestone-1.md`가 후자를 명시적으로 요구한다
— *"금지 import와 순환 의존을 **일부러 넣은 test fixture가 실제로 실패**"*.
**두 형태를 같은 이름으로 부르지 않는다.**

**생존 기준**: `milestone-6.md`의 완료 조건 — *"중요 mutation 생존 0 또는 **사용자 승인된
명시적 예외**"*. 예외는 사용자 승인 사항이며 구현자가 스스로 선언하지 않는다.

### D-4. 래칫 임계는 함수 50줄 · 파일 500줄이다

`v2-지침서.md` §5의 권고 한도를 채택한다. **이식한 ML 코드에도 같은 한도를 적용한다**
(ADR 0001 D-7). 초과는 **자동 실패 또는 명시적 allowlist 사유**를 요구하고,
**baseline을 느슨하게 갱신해서 우회하지 않는다**(§5).

**allowlist의 형식**: 사유와 **해소 계획**을 함께 적는다(`v2-지침서.md` §5 Python ML —
*"예외가 필요하면 allowlist 사유와 해소 계획을 남긴다"*). 사유만 있고 계획이 없는
allowlist는 영구 면제가 된다.

### D-5. 크기 축만으로 판정하지 않는다

`v2-지침서.md` §5가 함께 재라고 한 것 — 함수/메서드 크기와 복잡도, 파일 크기, 모듈
fan-in/fan-out, public API 수, 순환 의존, duplicate mechanical helper. **줄 수만 맞추기
위한 파일 분할을 금지한다.**

§1.3이 그 규칙이 필요한 이유의 실물이다. **클래스/타입 크기 축을 추가할지는
`OPEN-ADR-06`**이며 이 ADR이 정하지 않는다.

**`duplicate mechanical helper` 축의 측정 정의는 M1 slice 1A에서 확정되지 않는다** — 도메인
코드가 없는 상태에서 그 축을 측정하면 공허하다(Codex 12차 medium). 나머지 다섯 축은 1A의
`qualityBaseline`이 싣는다(`OPEN-ADR-06`이 묻는 세 축과 함께). **여섯째 축의 결정은 운영자,
시점은 1B 도구 조사 뒤**이며 추적은 `OPEN-ADR-16`(아래 §5).

### D-6. 아키텍처 규칙 강제는 **ArchUnit**을 채택한다

- **후보 3종 중 유일하게 활발한 안정 라인을 가진다** — 1.5.0(2026-08-04), 직전
  1.4.2(2026-04-18), 마지막 push 2026-08-24. Apache-2.0.
- **바이트코드를 분석하므로 Kotlin 소스 버전 축의 영향을 받지 않는다.** 관련 축은 class
  file 버전이고 1.5.0이 그것을 직접 다룬다 — *"Support Java 27 / class file major
  version 71"*. 지원 Java 범위는 1.8~25(**ArchUnit 저장소의** `build.gradle:48-49` —
  이 인용은 legacy `bid-vector`가 아니라 조사 노트가 취득한 외부 저장소 파일이다).
- 1.5.0이 **sealed 정보를 노출한다** — `JavaClass.isSealed()` / `getPermittedSubclasses()`.
  `v2-지침서.md` §5가 sealed type을 회귀 방지 수단으로 규정하므로 이 API는 직접 관련이 있다.
- JUnit 5/6 양쪽 러너를 제공한다(1.5.0에 `archunit-junit6` 추가).
- **확인하지 않은 것**: *"Kotlin 2.x 지원"이라는 공식 서술은 없다.* 위 두 사실만 기록하고
  추론을 사실로 쓰지 않는다. 또한 **소스 레벨 규칙**(파일 크기, Kotlin 고유 형태의 배치·
  네이밍)은 표현하지 못하며, `internal`·확장 함수·top-level 함수처럼 바이트코드에서 형태가
  바뀌는 요소의 규칙 표현은 조사하지 않았다. **그래서 ADR 0006 D-3이 빌드 의존 선언을
  1차 강제로 둔다.**

### D-7. 크기·복잡도 래칫 도구는 미결이며 그동안 게이트는 자체 검사로 선다

`v2-지침서.md` §5의 크기·복잡도 임계를 자동화하는 후보는 **Detekt**이지만 버전 경로가
미결이다(`OPEN-ADR-08`). **래칫이 CI 게이트인데 게이트 도구 자체가 alpha면 게이트의
안정성이 도구에 종속된다.**

따라서 **M1이 Kotlin 버전을 고정할 때까지 크기·복잡도 래칫은 도구에 의존하지 않는
자체 검사로 세운다.** 이는 legacy가 `scripts/design_ratchet.py`로 한 것과 같은 형태이며,
§1.2의 관찰대로 **그것만으로 회귀가 막히지 않는다**는 것을 알고 채택한다 — 래칫은
D-1~D-3의 테스트 층을 대체하지 않고 보완한다.

---

## 3. 대안 — 아키텍처·정적 규율 3종

**판정 기준은 M1에서 고정할 Kotlin 2.x + Spring Boot 3.x 조합과의 호환**이다
(`v2-지침서.md` §5, `decisions.md` `OPEN-OPS-07`). 조사 원본은
`_workspace/m0-open-decisions/ops07-library-survey.md`(2026-08-26)다.

| 후보 | 판정 | 사유 |
| --- | --- | --- |
| **ArchUnit** | **채택** | D-6 |
| **Konsist** | **불채택** | **유지보수 정체가 이 축에서 확인된 가장 명확한 리스크다.** 마지막 릴리스가 **0.17.3 / 2024-12-08**로 조사 시점(2026-08-26) 기준 약 20개월 전이고, 그 이후 main 커밋이 **1건**(문서 도구 제거)뿐이다. **단서**: GitHub API의 `pushed_at`은 **2026-08-17로 최근처럼 보인다** — 조사 노트는 그것이 *"다른 브랜치 활동으로 보이며, 이 조사로는 그 내용을 확인하지 못했다"*고 적는다. **「정체」의 근거는 릴리스 부재와 main 커밋 이력이지 `pushed_at`이 아니며, 다른 브랜치에서 무엇이 도는지는 확인되지 않았다.** 소스를 `kotlin-compiler-embeddable`로 파싱하는데 그 버전이 **2.0.21에 고정**돼 있고 2024-11에 *"Disable Kotlin updates"* 커밋이 있다 — **내장 컴파일러 버전이 분석 가능한 Kotlin 문법의 상한을 좌우한다.** `OPEN-OPS-07`이 예방하려 한 상황(*"확인 없이 채택한 라이브러리가 유지보수 중단 상태로 드러나면 M4에서 통째로 재작업"*)에 가장 가까운 후보다. 더해서 **ArchUnit이 덮는 축과 상당 부분 겹치므로, 겹치지 않는 규칙이 실제로 필요하다는 것이 먼저 확인돼야 도입 논의가 성립한다.** Kotlin 2.2+ 소스 분석 가능 여부는 **공식 호환표가 없어 확인 불가**이며, 근거 없이 "호환된다/안 된다" 어느 쪽도 쓰지 않는다 |
| **Detekt** | **조건부 — 버전 경로가 `OPEN-ADR-08`** | **채택할 이유는 분명하다**: §5의 크기·복잡도 규율에 직접 대응하는 유일한 후보이고, 1.23.8 기준으로도 `LongMethod` · `LongParameterList` · `ComplexCondition` · `CognitiveComplexMethod` · `ReturnCount`/`ThrowsCount` 같은 규칙과 **baseline 파일**이 있어 래칫 운용이 가능하다. Apache-2.0. **막는 것은 버전이다**: 안정판 **1.23.8(2025-02-21)이 Kotlin 2.0.21 기준**이고 **Kotlin 2.2 이상 기준의 안정판이 존재하지 않는다.** 대응하는 2.0.0은 alpha.0(2025-10-21)부터 alpha.6(2026-08-04)까지 **10개월째 alpha**다(2.0.0-alpha.6은 Kotlin 2.4.10 기준). **1.23.8이 상위 Kotlin 소스를 어떻게 처리하는지는 문서화돼 있지 않다 — 확인 불가.** 프로젝트 자체는 살아 있고(마지막 push 2026-08-26) 안정판만 오래됐다 |

---

## 4. 결과

- **CI가 프로덕션 엔진을 요구한다**(ADR 0004 D-1) — 동시성·격리에 의존하는 경로는
  Testcontainers PostgreSQL 없이 머지되지 않는다. 테스트 시간이 이 결정의 비용이다.
- **mutation 실행 비용이 든다.** D-3의 대상은 **전체 코드가 아니라 열거된 규칙**이며,
  그 범위를 좁게 유지하는 것이 이 결정의 일부다.
- **도구가 정해지지 않은 축이 둘 있다** — mutation 도구(`OPEN-ADR-07`)와 Detekt 버전 경로
  (`OPEN-ADR-08`). D-7이 그동안의 게이트를 자체 검사로 세운다.
- **`milestone-1.md`의 완료 조건이 이 ADR의 1차 acceptance**다 — *"`./gradlew check`
  통과"*, *"금지 import와 순환 의존을 일부러 넣은 test fixture가 실제로 실패"*,
  *"중요 rule mutation이 생존하지 않음"*, *"신규 파일/함수 예산 위반 없음"*.

---

## 5. 이 ADR이 등록하는 `OPEN`

### `OPEN-ADR-06` · 래칫 축에 클래스/타입 크기를 추가하는가

- **결정 필요 사항**: 파일·함수 크기 외에 **클래스/타입 단위 크기**(또는 상속 깊이·mixin
  수) 축을 래칫에 넣는가.
- **근거**: §1.3 — legacy가 mixin 합성으로 파일 한도를 우회했고 그 사실을 스스로
  문서화했다. 축을 파일 줄 수로만 두면 **분할이 공식 우회로가 된다.**
- **선택지**: (a) 클래스/타입 크기 축 추가 (b) `v2-지침서.md` §5의 결합도 축(fan-in/out,
  public API 수)으로 대신 잡는다 (c) 추가하지 않고 리뷰에 맡긴다 — **(c)는 §5의 "회귀
  방어를 사람의 주의력에 맡기지 않는다"와 충돌한다.**
- **소유**: M1 래칫 구현.

### `OPEN-ADR-16` · `duplicate mechanical helper` 축의 측정 정의

- **결정 필요 사항**: D-5가 「함께 잰다」로 든 여섯 축 중 `duplicate mechanical helper`를
  무엇으로, 어떤 기준으로 측정하는가.
- **근거**: M1 slice 1A는 도메인 코드가 없어 이 축을 측정하면 공허하다(그린필드 바닥값) —
  1A `qualityBaseline`(`reports/evidence/m1/1a/scope.md` D-6)이 나머지 다섯 축만 싣는다
  (Codex 12차 medium).
- **선택지**: (a) PMD CPD 등 기존 중복 탐지 도구의 Kotlin 지원 조사 후 채택 (b)
  `qualityBaseline` 안에 자체 판정을 추가 (c) 측정하지 않고 리뷰에 맡긴다 — **(c)는 §5의
  "회귀 방어를 사람의 주의력에 맡기지 않는다"와 충돌한다.**
- **소유**: M1 1B — 첫 도메인 코드와 함께 도구 조사 후 확정.
- **결정자·시점**: 운영자, 1B 도구 조사 뒤. (2026-09-03 신설 · Codex 12차 medium)

### `OPEN-ADR-07` · mutation 도구

- **결정 필요 사항**: Kotlin에서 D-3의 M-1~M-9를 무엇으로 실행하는가.
- **왜 미결인가**: `OPEN-OPS-07`의 조사 대상 9종에 mutation 도구가 없다. **조사되지
  않았다.** 도구 없이도 대상 목록(D-3)은 확정되므로 이 항목이 D-3을 막지 않는다.
- **소유**: M1.

### ~~`OPEN-ADR-08`~~ · Detekt 버전 경로 — **해소**

- **결정**: **(i) alpha 를 쓴다** — `dev.detekt:detekt-gradle-plugin:2.0.0-alpha.6`.
  **groupId와 plugin id가 2.0에서 `io.gitlab.arturbosch.detekt` → `dev.detekt`로 바뀌었다.**
- **결정자**: **M1 구현**(이 `OPEN`의 소유가 「M1 버전 고정」이므로 구현이 결정 주체다).
  **시점**: **2026-09-02 · M1 slice 1A**. 근거는 `reports/evidence/m1/1a/scope.md` D-4.
- **종료 조건과 그 충족**: 종료 조건은 **M1의 Kotlin 버전 고정**이었다. 1A가 **Kotlin 2.4.10**을
  고정했고(`gradle/libs.versions.toml`) 그것이 나머지 선택지를 지운다 — **(iv)는 서지 않는다.**
  Boot 4.1.1 BOM이 Kotlin 2.3.21을 관리하므로 2.0.x로 내려갈 자리가 없고, (ii)는 detekt
  이슈 #8865가 **closed as not planned**로 닫혔다(1.23.8을 Kotlin 2.3+에 돌리면 메타데이터
  버전 불일치로 대량 오탐이 나며 메인테이너가 백포트를 거부했다). (iii)은 출시 시점 확인 불가.
- **alpha 채택의 대가와 그 완화** — 이 결정이 지우지 않는 것:
  - **1.x와 설정 키가 호환되지 않는다**(`threshold` → `allowedLines`/`allowedComplexity`/…).
    1.x 키로 쓴 설정은 **조용히 무시**되므로 `config/detekt/detekt.yml`은 2.0 키로만 쓴다.
  - **게이트를 alpha에 종속시키지 않는다** — D-7이 요구한 자체 검사가 그대로 선다.
    승인된 두 임계를 **둘 다 도구 없이 서는 `sizeGate` task**가 든다. 파일 축은 detekt에
    규칙이 아예 없어서, 함수 축은 **억제가 임계에 닿지 못하게** 하려고 그렇게 했다 —
    `sizeGate`가 Kotlin PSI로 직접 재고 `detekt.yml`의 `LongMethod`는 비활성이다(같은 수가
    두 자리에 있으면 안 된다). **detekt이 죽어도 승인된 임계 둘은 계속 걸린다.** detekt이
    남아서 드는 것은 **승인 문서가 수치를 정하지 않은 축**(복잡도·중첩)이고, 그 값은 도구
    기본값이며 `qualityBaseline`이 실측을 남긴다(`OPEN-ADR-06`).
  - **configuration cache 비호환(#9390)은 alpha.6에 남아 있지 않다** — 1A 실측
    (`reports/evidence/m1/1a/commands.md` `T-2`). 같은 축에서 **실제로 깨진 것은 Spotless**였고
    1A가 포맷 도구를 ktlint Gradle 플러그인으로 바꿨다(`T-6`).

**아래는 미결 당시의 기록이며 지우지 않는다.**

- **결정 필요 사항**: (i) alpha(2.0.0-alpha.x)를 쓴다 (ii) 안정판 1.23.8을 상위 Kotlin
  소스에 쓴다 — **동작 미확인** (iii) 2.0 정식 출시를 기다린다 — **출시 시점 확인 불가**
  (iv) M1이 Kotlin 2.0.x를 고정하면 1.23.8이 정확히 맞는다.
- **종속**: **M1이 고정할 Kotlin 버전이 정해져야 판정된다.** 그것은 `OPEN-ADR-01`(Boot
  세대)과 함께 움직인다.
- **소유**: M1 버전 고정.

---

## 6. 확인하지 않은 것

- **Detekt 1.23.8이 Kotlin 2.2+ 소스를 분석할 때의 실제 동작**(정상/부분 실패/타입 해석
  저하). 공식 호환표는 *"1.23.8 → Kotlin 2.0.21"*만 적는다.
- **Detekt 2.0.0의 정식 출시 시점.** 로드맵 날짜를 확인하지 못했다.
- **Konsist의 다른 브랜치 활동 내용.** `pushed_at`이 2026-08-17로 최근이나 그 브랜치에서
  무엇이 도는지 조사 노트도 이 slice도 확인하지 않았다.
- **Konsist의 Kotlin 2.2+ 소스 분석 가능 여부.** 공식 호환표가 존재하지 않는다. 확인된
  것은 Konsist 자신의 빌드가 `kotlin-compiler-embeddable` 2.0.21에 고정돼 있다는 사실뿐이다.
- **ArchUnit의 Kotlin 버전 지원 공식 서술은 없다**(D-6). Kotlin 고유 형태의 규칙 표현
  범위도 조사하지 않았다.
- **mutation 도구를 조사하지 않았다**(`OPEN-ADR-07`).
- **property test·mutation의 실행 시간 예산을 재지 않았다.** CI 시간이 결정의 비용이라고
  §4에 적었으나 **그 비용을 측정하지 않았다.**
- **legacy `scripts/design_ratchet.py`의 측정 정의를 이식하지 않았다.** D-7이 자체 검사를
  세운다고 정했을 뿐 그 검사의 형태를 정하지 않았다 — M1 소관.

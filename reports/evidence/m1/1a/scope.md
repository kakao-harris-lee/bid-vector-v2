# Slice 계약 — M1 / 1A · 프로젝트와 CI 골격

```yaml
milestone: m1
slice: 1a-project-ci-skeleton
base_sha: 6b03c75016a1d5437ca7ff932282fc477be974e6
head_sha: 리뷰 시점의 HEAD
  # 1A 의 커밋 집합은 `git log --oneline 6b03c75..HEAD` 가 낸다(commands.md C-0).
  # SHA 를 여기 박으면 커밋이 늘 때마다 낡으므로 range 로 적는다 — 선언 SHA 래칫을 두지 않는다.
in_scope:
  - settings.gradle.kts · build.gradle.kts · gradle.properties
  - gradle/libs.versions.toml · gradle/wrapper/** · gradlew · gradlew.bat
  - build-logic/**                    # convention plugin 과 커스텀 task 의 included build
  - shared-kernel · procurement · qualification · strategy · decision
  - settlement · workflow · adapters · app          # 모듈 아홉. `bidding` 은 out_of_scope
  - config/quality/**                 # 크기·경계 정책 데이터(versioned)
  - .editorconfig                     # ktlint 스타일. 규칙 집합의 유일한 자리
  - config/detekt/**                  # detekt 2.0 키 규칙 집합
  - .github/workflows/ci.yml
  - .gitignore                        # Gradle 산출물 규칙. 앵커 없는 `build/` 가 B-1 을 냈다
  - v2-지침서.md                                   # §5 「Kotlin」의 테스트 플랫폼 문면 한 줄
  - docs/adr/0007-test-pyramid-and-ratchet.md      # OPEN-ADR-08 행 + §1.1 테스트 플랫폼 문면
  - docs/adr/0006-gradle-modules.md                # D-2 표 + 신설 D-2.1 — 계약 갱신으로 넓힘
  - milestone-1.md                                 # 1A 모듈 목록 — 같은 갱신
  - docs/discovery/capability-map.md               # §12 registry 의 해당 행 + bidding 보류 OPEN 신설
  - reports/evidence/m1/1a/{scope,commands,checklist,rollback}.md
out_of_scope:
  - 도메인 코드 일체                   # 1B~1E. 1A 가 넣는 것은 모듈 경계 앵커 아홉뿐
  - bidding 모듈                       # 운영자 결정 2026-09-02 보류 — D-2 · ADR 0006 D-2.1
  - Spring controller · DB schema · Flyway migration 실행 · 실제 외부 호출
  - Python ML · 기존 Python 과의 byte-for-byte 동등성
  - mutation testing 적용              # OPEN-ADR-07 — 카탈로그 좌표 등재만
  - 위에 열거한 자리 외의 v2-지침서 · docs/adr · capability-map · milestone-1.md 편집
  - bid-vector/ symlink 아래 기존 저장소   # 읽기 전용
  - _workspace/**                     # .gitignore 대상
acceptance_commands:
  # A-0 이 나머지의 전제다 — 구현자 working tree 가 아니라 **커밋된 것**이 도는지 먼저 잰다.
  # 왜 clean-tree 게이트만으로 부족한지는 commands.md 의 `A-0b` 절이 갖는다.
  - "git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check)"   # A-0
  - "./gradlew --no-build-cache clean check"       # A-1
  - "./gradlew :app:test --tests '*ArchitectureGate*'"   # A-2 — 위반 fixture 음성 테스트 포함
  - "./gradlew qualityBaseline"                    # A-3 — OPEN-ADR-06 입력 실측
  - "./gradlew :app:compatibilitySmoke"            # A-4 — 채택 라이브러리 일곱 × Boot 4.1.1 (해석·컴파일·로드)
  - "./gradlew :build-logic:test"                  # A-5 — 경계 판정의 순수 함수 테스트
rollback: |
    **정본은 `reports/evidence/m1/1a/rollback.md`**(`agent-workflow.md` §6 이 요구하는 파일).
    되풀이하지 않는다 — 같은 사실을 두 자리에 적으면 한쪽이 낡는다.
```

작성: 2026-09-02, kotlin-implementer (v2-slice-pipeline).

---

## 이 slice 가 하는 일

**빈 저장소에 Kotlin 멀티모듈 빌드와 게이트를 세운다.** 도메인 코드는 넣지 않는다 —
1A 의 산출물은 **빌드·모듈 경계·게이트**이고, 그 게이트가 실제로 잡는다는 증거다
(`ADR 0006` §4 — *"게이트가 있다는 주장이 아니라 게이트가 잡는다는 증거가 기준"*).

승인 문서가 1A 에 남긴 미결 넷을 답한다.

| 미결 | 좌표 | 이 계약의 답 |
| --- | --- | --- |
| `OPEN-ADR-08` — detekt 버전 경로 | `ADR 0007` §5 | **D-4** |
| `OPEN-ADR-06` — 래칫의 클래스/타입 축 | `ADR 0007` §5 | **D-6** — 1A 는 **재기만** 한다. 결정은 운영자 |
| 4.x 호환 실측 재확인 | `v2-지침서.md` §5 | **D-7** |
| 모듈 안의 패키지 규약 | `ADR 0006` §6 | **D-3** |

---

## 결정

### D-1. 툴체인 고정

| 축 | 값 | 근거 |
| --- | --- | --- |
| Kotlin | **2.4.10** | 프리릴리스 제외 최신 stable. `_workspace/m1-1a/02_survey_toolchain.md` §1 |
| Gradle wrapper | **9.6.1** | detekt 2.0.0-alpha.6 이 **검증한 조합**과 정확히 일치(같은 노트 §8.4). KGP 문서 상한 9.5.0 을 한 마이너 초과 — **실측으로 닫는다**. 실패 시 **9.5.1 + detekt 2.0.0-alpha.5** 로 후퇴 |
| JDK toolchain | **21** | **승인 문서에 지침 0건**(scout 노트 X-10). 근거 둘: 로컬 실측(Temurin 21.0.4) · Boot 4.1.1 하한 17 · Kotest 6 하한 11 · pitest 플러그인 하한 17 — 아홉 도구 전부의 하한을 만족하는 가장 낮은 LTS |
| Spring Boot | **4.1.1** | GA 최신. **`app` 에만 플러그인 적용**(D-7), 도메인 모듈은 Spring 무의존 |
| 테스트 플랫폼 | **JUnit 6.1.3** | 라인 자체는 **승인 문서가 정한다** — `v2-지침서.md` §5 「Kotlin」의 테스트 플랫폼 문면과 `ADR 0007` §1.1.1(운영자 결정 2026-09-02). 1A 가 고른 것은 그 라인 안의 **패치 값**뿐이다 |
| property/assertion | **Kotest 6.2.4 의 `kotest-property` + `kotest-assertions-core`만** | runner 를 붙이지 않는다 — `kotest-property` 는 junit-platform 에 의존하지 않아 평범한 Jupiter 테스트에서 호출된다. **jqwik 탈락의 근거는 `ADR 0007` §1.1.1 이 갖는다** — 플랫폼 이동의 대가라 그 결정과 같은 자리에 있어야 한다 |
| 아키텍처 테스트 | **ArchUnit 1.5.0** (`archunit-junit6`) | `ADR 0007` D-6 채택. **Konsist 불채택 유지** — 21 개월 정체 + 번들 Kotlin 컴파일러 2.0.20 이 우리 소스 2.4.10 을 파싱한다는 근거가 없다 |

**`kotlin.version` 충돌**: Boot 4.1.1 BOM 이 `kotlin-stdlib` 을 2.3.21 로 관리한다. `app` 에서
`ext["kotlin.version"]` 을 카탈로그 값으로 덮어 **카탈로그가 이기게** 한다.

### D-2. 모듈은 아홉이다 — `bidding` 은 보류

`v2-지침서.md` §3.1 과 `ADR 0006` D-2 의 목록은 열이고, 이 slice 는 그중 **아홉**을 만든다.
`bidding` 을 빼는 근거는 `milestone-1.md` 1A 항목의 *"M0 필수 capability에 없는 모듈은 만들지 않는다"* —
와 scout 노트 §1.3 (1) 의 실측이다: `capability-map.md` 의 95 개 capability 중 **「투찰 계획과 상태」를
소유하는 항목이 없다.** SET-01·SET-02 가 「실투찰 레코드」를 **전제**하나 그 전제를 소유하는
capability 가 등재돼 있지 않다.

`ADR 0006` D-2 가 목록에 넣었으므로 이것은 **ADR 과 capability map 사이의 갈림**이고, 구현자가
혼자 닫을 것이 아니다 — **운영자 결정 2026-09-02 로 보류**하고 `capability-map.md` §12 에
`OPEN-ADR-14` 를 신설해 소유 capability 가 설 때 재개하도록 등재한다. 아홉 모듈은
`ADR 0006` D-5 의 「목록 밖 신설」에 해당하지 않는다(빼는 것이지 더하는 것이 아니다).

**결정의 정본은 evidence 가 아니라 `ADR 0006` D-2.1 이다.** 처음에는 이 절과 `OPEN-ADR-14` 만
근거로 두었는데 그러면 discovery 문서가 승인된 ADR 을 대체하는 상태가 된다(Codex #1). 아래
「계약 갱신」이 그 시정을 적는다 — 승인 문서 두 자리를 고쳤고 이 절은 그것을 가리킨다.

### D-3. 패키지 규약 — `ADR 0006` §6 이 1A 로 넘긴 결정

- **루트**: `bidvector`. 모듈 X 의 코드는 `bidvector.<x>..` 에 산다(`shared-kernel` → `bidvector.sharedkernel`).
- **모듈 안은 개념으로 나눈다.** 기술 계층 이름(`util`·`helper`·`impl`·`common`·`base`·`misc`·
  `service`·`manager`)을 패키지 이름으로 쓰지 않는다. 근거: 모듈 경계가 이미 계층을 표현하므로
  모듈 **안**에 계층을 또 만들면 두 축이 겹치고, `util`·`common` 은 `ADR 0006` A-6 이 경고한
  **자석**이 되는 이름이다.
- **강제**: 위 둘 다 architecture test 로 건다(`bidvector` 1 급 하위 패키지 집합 == 모듈 아홉,
  금지 이름 없음). 규칙이 **빈 집합 위에서 공허하게 통과하지 않도록** 각 모듈에 경계 앵커
  타입 하나를 둔다 — 도메인 코드가 들어올 때까지 그 앵커가 모듈을 게이트에 보이게 한다.

### D-4. `OPEN-ADR-08` — detekt 는 **2.0.0-alpha.6**(선택지 (i))

선택지 (iv)(*"M1 이 Kotlin 2.0.x 를 고정하면 1.23.8 이 맞는다"*)는 **서지 않는다.** Boot 4.1.1 이
Kotlin 2.3.21 을 관리하고 우리는 2.4.10 을 고정하므로 2.0.x 로 내려갈 자리가 없다. (ii) 는
detekt 이슈 #8865 가 **closed as not planned** 로 닫혔다 — 1.23.8 을 Kotlin 2.3+ 에 돌리면
메타데이터 버전 불일치로 대량 오탐이 나고 메인테이너가 백포트를 거부했다. (iii) 은 출시 시점
확인 불가.

**alpha 채택의 위험을 명시한다.**

- alpha 다 — 규칙·설정 키가 정식 출시 전에 또 바뀔 수 있다.
- **configuration cache 비호환**이 alpha.4 에서 보고됐다(#9390). alpha.6 에 남았는지는 **1A 가 실측**하고,
  남아 있으면 detekt task 만 예외 처리한다(전체 비활성화하지 않는다).
- **1.x 와 설정 키가 호환되지 않는다**(`threshold` → `allowedLines`/`allowedComplexity`/…).
  설정을 1.x 키로 쓰면 **조용히 무시**되므로 `config/detekt/detekt.yml` 은 **2.0 키로만** 쓴다.

**완화 — 그리고 그 완화가 어디까지인지.** `ADR 0007` D-7 이 *"게이트 도구 자체가 alpha 면
게이트의 안정성이 도구에 종속된다"*를 근거로 자체 검사를 요구한다. 축마다 정확히 이렇다.

| 축 | 정본 | detekt 이 죽으면 |
| --- | --- | --- |
| 파일 500줄 | `sizeGate`(도구 비의존) | **남는다** |
| 함수 50줄 | `sizeGate` — Kotlin PSI 로 직접 잰다 | **남는다** |
| 복잡도·중첩·파라미터 수 등 | detekt 기본값 | 사라진다 |

**앞선 판에서 이 절은 「두 임계 다 sizeGate 가 들고 detekt 이 죽어도 걸린다」고 적었다. 사실이
아니었다** — 같은 문서 D-5 의 표와 정책 파일 주석이 처음부터 「함수 축은 detekt 정본」이라 적었고
구현도 그랬다(설계 검토 §3.2 가 그 어긋남을 지목했다).

**그 뒤 함수 축도 도구 비의존이 됐다.** 처음에는 `@Suppress("LongMethod")` 텍스트를 막았는데
그 방식은 표기의 열거 게임이 된다(다중 행·`@file:`·`@kotlin.`). 억제를 막는 대신 **무의미하게**
만들었다 — `sizeGate` 가 Kotlin PSI 로 함수 길이를 직접 재므로 detekt 을 경유하지 않는다.
`detekt.yml` 의 `LongMethod` 는 껐다(같은 수가 두 자리에 있으면 안 된다). 파서 버전은
`version.ref = "kotlin"` 으로 컴파일러 버전에 묶여 있다.

### D-5. 임계는 코드가 아니라 **versioned policy 데이터**다

승인 문서가 수치로 정한 임계는 `v2-지침서.md` §5 「크기와 결합도」 의 **함수 50줄 · 파일 500줄** 둘뿐이므로
나머지 축의 수치를 1A 가 지어내지 않는다. **각 임계는 강제하는 주체 쪽에 한 번만 산다.**

| 축 | 정본 | 강제 |
| --- | --- | --- |
| 함수 50줄 | `config/quality/size-policy.properties` | `sizeGate` — Kotlin PSI 로 직접 잰다 |
| 파일 500줄 | `config/quality/size-policy.properties` | `sizeGate` — detekt 에 파일 길이 규칙이 없다 |

**처음에는 두 수를 정책 파일 하나에 두고 detekt 설정 overlay 를 생성해 얹었다.** 그 배선을
걷었다 — `build-logic` 도 게이트 대상이 되자(Codex #5) 그 included build 가 overlay 생성
task 를 쓸 수 없기 때문이다. 그 task 클래스가 바로 그 빌드의 산출물이라 자기 자신을 쓰는
순환이 된다. 축마다 자리를 하나씩 두면 **생성 단계 없이** 애플리케이션 아홉 모듈과
`build-logic` 이 같은 설정 파일을 읽는다. 중복은 여전히 없다 — 각 수는 한 자리에만 있다.

**baseline 파일을 만들지 않는다.** 그린필드에서 detekt baseline 을 생성하면 *"지금 상태는 정상"*
을 박제하게 되고, 이것이 `ADR 0007` D-4 가 금지한 **「baseline 을 느슨하게 갱신해 우회」** 의
출발점이다. 첫날부터 무관용으로 건다.

### D-6. **「신규 파일/함수 예산」의 수치를 1A 가 정하지 않는다**

`milestone-1.md` 「완료 조건」의 *"신규 파일/함수 예산 위반 없음"* 을 들지만 **그 「예산」의
정의가 승인 문서에 없다**(scout 노트 X-3 — 인접 문면 둘이 서로 다른 축을 가리킨다). 1A 는
**정의를 자기 승인하지 않는다.** 대신 둘을 한다.

1. `v2-지침서.md` §5 「크기와 결합도」 가 **실제로 정한** 두 한도(함수 50 · 파일 500)를 게이트로 건다.
2. 같은 절이 「함께 측정한다」로 든 여섯 축과 `OPEN-ADR-06` 이 묻는 세 축(클래스/타입 크기 · 상속 깊이 · mixin 수)을
   **재는 task** 를 만들고 그 실측을 `capability-map.md` §12 에 등재한다. **결정은 운영자**다
   (`capability-map.md` §14.2 의 `OPEN-ADR-06` 행 — 결정 주체가 운영자, 시점이 1A 실측 뒤).

**1A 실측의 한계를 미리 적는다**: 그린필드라 대부분의 축이 바닥값(0/1)이다. 1A 가 남기는 값은
「현재 코드가 얼마나 큰가」가 아니라 **「각 축을 어떻게 재는가」의 정의와 그 정의가 도는 증거**다.

### D-7. 4.x 스모크는 **의존 해석 + 컴파일 + 클래스 로드**까지다

`v2-지침서.md` §5 「Kotlin」의 `OPEN-OPS-07` 항목과 `ADR 0004` §5:176 이 1A 에 지운 실측이다. 범위를 계약이 정한다
(scout 노트 X-4 가 지목한 구분): **Flyway 는 좌표를 세워 해석·컴파일되는지만 재고 migration 을
쓰지 않는다.** 마찬가지로 db-scheduler 는 스케줄을 돌리지 않고, Testcontainers 는 컨테이너를
띄우지 않는다. **runtime 사용은 해당 slice 의 일이다.**

`app` 이 스모크 자리다 — Boot 플러그인을 **`app` 에만** 적용한다(scout 노트 X-5 의 갈림에 대한 답).
`milestone-1.md` 「범위 밖」의 *"Spring controller, DB schema, Flyway"* 는 **controller** 이지 플러그인이 아니며, 플러그인을 세우지 않으면
같은 항목이 지운 측정 자체가 성립하지 않는다. `app` 에 controller·`main`·`@SpringBootApplication`
은 없고 `bootJar` 는 비활성이다.

### D-8. `broker` 의존을 세우지 않는다

`v2-지침서.md` §3.1 의 `adapters` 행이 아직 `broker` 를 적지만 `ADR 0006` D-2 가 그 항목을
뺐다(`OPEN-OPS-05`, 운영자 2026-08-26). **ADR 이 이긴다** — 지침서 문면만 낡았다.

---

## 위험과 알려진 제한

| # | 위험 | 완화 |
| --- | --- | --- |
| K-1 | **detekt 2.0 은 alpha** — 규칙·키가 바뀔 수 있고 configuration cache 비호환 이력이 있다 | D-4 의 완화. 승인된 두 임계는 detekt 밖(`sizeGate`)에 둔다 |
| K-2 | **Gradle 9.6.1 이 KGP 문서 상한(9.5.0)을 넘는다** | 실측으로 닫는다. 실패 시 9.5.1 + alpha.5 로 후퇴하고 그 사실을 commands.md 에 남긴다 |
| K-3 | **coverage 임계 수치가 승인 문서에 부재**(scout X-11) | 1A 는 Kover 를 **배선만** 하고 임계를 지어내지 않는다. 도메인 코드가 없는 상태에서 정한 수치는 근거가 없다 — 1B 가 첫 도메인 코드와 함께 정한다 |
| K-4 | **ArchUnit 은 바이트코드를 본다** — `internal`·확장 함수·top-level 함수의 형태 규칙을 표현하지 못한다(`ADR 0007` D-6) | 1 차 강제는 **빌드 의존 선언**이다(`ADR 0006` D-3). 도메인 모듈의 의존 그래프에 Spring/JPA/Jackson 이 없으면 컴파일 자체가 안 된다. arch test 는 2 차 그물 |
| K-5 | **`bidding` 보류가 `ADR 0006` D-2 목록과 어긋난다** | 신설 `OPEN` 이 그 어긋남을 들고 있다. 소유 capability 가 서면 재개 |
| K-6 | **1A 실측 baseline 은 그린필드 바닥값**이다 | D-6 이 미리 적는다. 값이 아니라 측정 정의가 산출물 |

---

## 이 slice 가 하지 않는 것

- **도메인 코드를 넣지 않는다.** 모듈마다 경계 앵커 타입 하나뿐이고 그 존재 이유는 D-3 이 적는다.
- **게이트 임계를 지어내지 않는다.** 승인 문서가 정한 둘만 건다(D-5·D-6·K-3).
- **`OPEN` 을 임의로 해소하지 않는다.** 닫는 것은 `OPEN-ADR-08` 하나이며 종료 조건(1A 가 경로를
  정한다)을 충족해서 닫는다. `OPEN-ADR-06` 은 **실측만 등재**하고 열어 둔다.
- **evidence 가 자기를 검사하는 장치를 만들지 않는다.** 대조는 `commands.md` 의 명령이 낸다.

---

## 계약 갱신

### 2026-09-02 — 포맷 도구를 Spotless 에서 ktlint Gradle 플러그인으로 바꾼다

**바뀐 것**: `Spotless 8.10.1 + ktlint 1.8.0` → `org.jlleitschuh.gradle.ktlint 14.2.0` 이 같은
ktlint 1.8.0 을 구동. 스타일 규칙은 `.editorconfig` 그대로다.

**사유**: 실측이다 — `commands.md` **T-6**. Spotless 가 configuration cache 아래에서 무작위
모듈 하나를 `InvocationTargetException` 으로 떨어뜨렸고 ktlint 세 버전과 `--no-parallel` 에서
동일했다. **게이트가 무작위로 죽으면 게이트가 아니다.**

대안 둘을 재고 골랐다. (a) Spotless 를 `notCompatibleWithConfigurationCache` 로 선언 — 동작은
하지만 `check` 가 매번 configuration cache 를 버리고 **45 줄짜리 경고를 출력**한다. 게이트의
신호를 매 빌드 노이즈로 덮는다. (b) 교체 — 조사 노트가 Spotless 를 권고한 근거는
`ratchetFrom` 과 다포맷 커버리지인데 **1A 는 둘 다 쓰지 않기로 이미 정했다**(그린필드라 래칫할
과거가 없고, 손으로 쓴 한국어 문서를 재포맷하지 않는다). 남은 용도가 ktlint 래핑 하나뿐이므로
래퍼를 걷고 도구를 직접 쓴다.

**부수 효과**: `check` 가 configuration cache 를 온전히 쓴다(`A-1`).

### 2026-09-02 — domain 경계를 deny-list 에서 **allow-list** 로 바꾼다 (Codex 3차 · 운영자 승인)

**바뀐 것**: domain 모듈의 2 차 게이트가 「금지 패키지 열거」에서 「허용 패키지 열거 + 나머지 전부
금지」로 바뀐다. 1 차 게이트(`group.forbidden`)는 그대로 두되 역할을 **의존 선언 수준의 조기
차단**으로 명시한다.

**사유 — deny-list 는 구조적으로 미완결이다.** 같은 가족의 finding 이 세 라운드 연속 나왔다:
`java.net.HttpURLConnection`(잎 좌표) → `tools.jackson`(옮긴 좌표) → `kotlin.io`(**문서가 이름으로
든 적 없는 좌표**). 앞의 둘은 목록을 넓혀 닫았고 두 번째 라운드에서 「가족을 문서에서 뽑고 뿌리로
적는다」로 구조를 한 번 바꿨다. **그런데 세 번째는 그 구조로도 잡히지 않는다** — 승인 문서는 「I/O를
금지」한다고 적을 뿐 `kotlin.io` 라는 좌표를 열거하지 않으므로, 문서에서 가족을 뽑는 출처 대조
테스트가 **정의상** 그 줄을 쓸 근거를 갖지 못했다.

비대칭이 원인이다. **deny-list 는 우리가 생각해 낸 것만 막고, domain 에 들어올 수 있는 좌표는
언제나 그보다 넓다.** allow-list 는 그 방향을 뒤집는다 — 새 라이브러리·새 JDK 패키지·새 stdlib
하위가 생겨도 도메인에는 자동으로 닫혀 있다. 출처 대조 테스트는 **살아남되 방향이 바뀐다**:
「금지 가족이 목록에 있는가」가 아니라 **「금지 가족이 허용 목록으로 도로 들어오지 않는가」**.

**허용은 뿌리가 아니라 정확 패키지다.** 처음에는 `java.lang`·`java.util`·`kotlin` 을 하위까지
열었는데, 그러자 **같은 열거 게임이 뿌리 안으로 이사했다** — `java.util.logging`·`java.lang.reflect`·
`kotlin.reflect` 가 전부 허용 뿌리 안이라 통과했고 막으려면 위험 하위를 하나씩 생각해 내어
제외 목록에 적어야 했다. 정확 패키지는 그 게임을 끝낸다: 하위는 적지 않는 한 열리지 않는다.
하위까지 여는 것은 `bidvector` 하나뿐이다.

**잔여 deny 는 허용된 정확 패키지 안에 남는다.** 정확 패키지는 하위를 열지 않지만 **한 패키지가
순수 타입과 위험 타입을 함께 담으면** 패키지 단위로 가를 수 없다 — `java.lang` 이 프로세스·스레드·
환경·클래스 로딩의 진입점을 담고, `java.util` 이 순수 컬렉션과 함께 `Timer`(내부 스레드)·
`ServiceLoader`(classpath 자원 I/O)를 담는다. **이것은 무한 열거가 아니다** — 대상이 JDK 가 고정한
패키지의 원소라 유한하고 새 좌표가 임의로 늘지 않는다. 그 유한성이 deny-list 를 버린 근거
(좌표가 무한히 는다)와 이 잔여 deny 를 가르는 지점이다. 판정 기준이 패키지·클래스 이름이라
별칭 import 로 우회되지 않는다.

**컴파일러 산출물은 허용한다** — `org.jetbrains.annotations`(비널 `@NotNull`)·`kotlin.jvm.internal`
(`Intrinsics`)·`kotlin.jvm.functions`(람다). 사용자 코드가 아니라 **언어가 스스로 내는 것**이고,
막으면 평범한 도메인 코드가 전부 걸린다(실측: `data class Won(val amount: Long)` 하나로 exit 1).

**대가**: 허용이 좁으면 정당한 도메인 코드가 막힌다. 그래서 **양성 fixture** 로 실제 도메인
형태가 통과하는지 함께 잰다 — 게이트가 「도메인이 비어 있는 동안만」 초록인 상태를 구조로
막는다. 그래도 막히는 자리는 `ADR 0007` D-4 의 allowlist 형식(사유 + 해소 계획)으로 연다.

### 2026-09-03 — 원산지 앵커를 `SourceFile` 로, 게이트 입력을 **컴파일러 소스**로 (Codex 8차 §4)

**바뀐 것**: 원산지 판정이 「`kotlin.Metadata` 보유」에서 **「class 의 `SourceFile` 이 컴파일러가
먹은 소스의 이름 집합에 드는가」**로 바뀐다(두 원산지 게이트 공통). 게이트 입력에
`compileKotlin` 의 `sources` 를 **더한다**(대체가 아니다 — 모듈 `build.gradle.kts` 는 컴파일
대상이 아니지만 `sizeGate` 대상이다).

**사유**: 앵커가 **소스 한 줄로 위조된다.** Codex 8차가 `@kotlin.Metadata` 를 단 Java 클래스로
그 층을 통과시켰다. `SourceFile` 은 컴파일러가 쓰는 속성이고, 무엇보다 **컴파일러가 실제로 먹은
파일 목록과 대조**되므로 위조만으로는 부족하다 — `E-55` 가 그 성질을 고정한다(진짜 Kotlin
산출물도 그 소스가 게이트를 안 거쳤으면 걸린다).

**성공 기준을 「Codex 재현 절차 실패」로 두지 않았다** — 설계 검토가 요구한 대로 **음성 fixture
셋 전부**(`E-53`·`E-54`·`E-55`)가 각각 다른 주입 경로로 실패하는 것을 기준으로 삼았다. 하나만
닫으면 다음 라운드가 예약된다는 판단이다.

**규격 하나를 실측으로 고쳤다** — 설계 검토의 *"`KotlinCompile` 은 `SourceTask`"* 가 이 KGP
버전에서 성립하지 않는다(`E-57`~`E-59` 절). 좌표를 `KotlinCompileTool.sources` 로 바꿨고 의미는
같다.

**잔여**: `SourceFile` 은 **경로가 아니라 이름**이라 같은 이름의 `.kt` 를 다른 디렉터리에서
컴파일해 넣으면 통과한다(알려진 제한 26). ktlint 는 컴파일러 소스를 입력으로 받지 못했다(제한 28).

### 2026-09-03 — 위협 모델 경계에 **도구 설정 파일**을 명시한다 (운영자 결정)

**넓힌 범위 없음** — `milestone-1.md` 와 `docs/adr/0007` 은 이미 `in_scope` 다. 편집 자리는
「게이트 위협 모델」 절의 「방어하지 않는다」와 `ADR 0007` §1.1.2 의 개정 항목이다.

**사유**: 레이아웃을 관례로 고정해도 **「도구가 그 소스를 어떻게 거르는가」는 닫히지 않는다.**
`.editorconfig` 의 경로별 `ktlint = disabled`, detekt 의 `excludes`·`ignoreAnnotated`, kover
필터가 그대로 열려 있고, 한 줄로 도구를 특정 트리에서 끌 수 있다는 점에서 `enabled = false` 와
성질이 같다. 그 편집을 모델 **안**에 두면 게이트가 자기 설정을 지켜야 한다는 요구가 되고,
그것은 §1.1.2 가 이미 불가능하다고 적은 것과 같은 형태다.

**현행 예외를 숨기지 않는다** — `.editorconfig` 의 `[**/build/generated-sources/**]` 는
게이트 정의의 일부로 **인정**하고, 그 경로가 `build/` 아래라 소스 관례 밖이라는 정합만 적었다.

### 2026-09-03 — 게이트 **위협 모델 경계**를 승인 문서에 적는다 (Codex 8차 · 운영자 결정)

**넓힌 범위 없음** — `milestone-1.md` 와 `docs/adr/0007` 은 이미 `in_scope` 다. 편집 자리만
넓어진다: `milestone-1.md` 는 1A 모듈 목록에 더해 **「게이트 위협 모델」 절 신설**,
`ADR 0007` 은 **§1.1.2 신설**.

**사유**: Codex 8차 high 가 「수제 `JavaCompile` + `@kotlin.Metadata`」 경로를 냈는데, 설계
검토 3차가 같은 위협 모델에서 **그보다 훨씬 싼 우회 둘**을 실측했다(`-x` 로 게이트 제외 ·
모듈 build script 한 줄 `enabled = false`). **게이트가 같은 트리의 빌드 스크립트인 한 그 계열은
설계로 닫히지 않는다** — 라운드마다 새 경로가 나온 것은 능력 집합이 무한해서이고, 그 무한은
**모델을 적지 않은 결과**다. 운영자가 경계를 채택했고(2026-09-03) **결정은 승인 문서에** 적는다.

**요구 축소가 아니다** — 완료 조건이 요구하는 fixture 는 소스 수준 위반이고, 지침서 §5 와
`ADR 0007` §1.2 는 회귀(drift)를 말한다. 그 대조는 `ADR 0007` §1.1.2 가 든다.

**역방향 파급 — 이번에는 실제로 밀렸다.** 두 문서 모두 삽입이 인용 지점 **위**에 있다.
`milestone-1.md` 를 가리키는 좌표 중 삽입 지점 아래의 것과, `docs/adr/0007` 축약형 인용
전부가 밀린다. 전부 `capability-map.md` 이고 이 slice 의 편집 경계 밖이라 **알려진 제한 16 에
합류**시켰다 — 목록과 개수는 그 항목의 명령이 낸다.

### 2026-09-03 — `in_scope` 를 승인 문서 두 자리로 더 넓힌다 (Codex 7차 medium · 운영자 결정)

**넓힌 범위**: `v2-지침서.md`(§5 「Kotlin」의 테스트 플랫폼 문면 한 줄) ·
`docs/adr/0007-test-pyramid-and-ratchet.md`(§1.1 의 같은 문면 + 신설 §1.1.1).
**그 두 자리에 한정**하고 두 문서의 다른 절은 손대지 않는다.

**사유**: Codex 7차가 **승인 명세를 slice 계약이 갈아치운 상태**를 지적했다 — 승인 문서 둘이
테스트 플랫폼을 JUnit 5 로 규정하는데 구현은 JUnit 6 을 고정했고, 그 결정은 **구현자가 쓴
`scope.md` 에만** 있었다. 운영자가 JUnit 6 을 승인하면서(2026-09-02) **결정은 승인 문서
개정으로 남긴다**는 원칙을 함께 확인했다. 그래서 이 라운드는 코드를 바꾸지 않고 **정본을
옮긴다** — 승인 문서가 라인을 정하고, `scope.md` 는 그 라인 안에서 1A 가 고른 패치 값만 든다.

**함께 걷은 것 — `v2-지침서.md` 로의 `file:line`.** 이 slice 가 그 파일을 편집하게 되면서
그것을 가리키던 좌표가 「같은 slice 안에서 움직일 수 있는 파일의 줄 번호」가 됐다
(`evidence-pack` 「낡는 좌표」). 실제로 삽입 지점 아래의 좌표 둘이 밀린다.
**수를 밀어 맞추지 않고 인용문·절 제목으로 바꿨다** — 그래야 다음 개정에서 다시 밀리지 않는다.
대상은 `git diff` 가 낸다(여기 열거하지 않는다). 삽입 지점 **위**의 좌표는 이번에 밀리지
않았지만 같은 이유로 함께 걷었다.

**같은 훑기가 이미 밀어 놓은 좌표 하나를 찾았다** — `ADR 0006` 을 가리키던 D-3 의 줄 번호가
이 slice 의 `D-2.1` 신설로 밀려 있었고(그 문서를 편집한 것이 이 slice 다) 아무도 보지 못했다.
같은 방식으로 걷었다. **역방향 훑기가 가정이 아니라 실제 파손을 낸 사례다.**

### 2026-09-03 — 아카이브 판정을 접두에서 **포함 관계**로 바꾼다 (Codex 7차)

**바뀐 것**: `jarContentGate` 가 class 엔트리의 패키지 접두를 보던 것에서, 엔트리 집합이
**게이트를 통과한 class output 의 부분집합**(경로 + 내용 해시)인지 보는 것으로 바뀐다.
엔트리마다 원산지(`kotlin.Metadata`)도 함께 본다. resource 는 대상이 아니다.

**사유**: 접두는 **넣는 쪽이 맞출 수 있는 값**이다. 소유 패키지에 이름을 맞춘 prebuilt Java
class 를 넣으면 jar 게이트를 통과하면서 원산지·ktlint·detekt·크기 게이트를 전부 비껴갔다
(Codex 7차). 앞선 실측(`E-30`)이 그 사각을 못 본 것은 소유 **밖** 경로만 심었기 때문이다.
포함 관계는 주입 **경로**를 열거하지 않는다 — `from(zipTree)` · resource 경유 · 같은 이름
변조가 모두 같은 이유로 닫힌다.

**대가**: 아카이브에 정당하게 들어갈 「컴파일하지 않은 class」가 생기면 이 게이트를 손봐야
한다. 1A 에는 그런 것이 없고, 생기면 그때가 판단할 자리다.

### 2026-09-03 — T-D 를 손 열거에서 **효과 표면 도출**로 바꾼다 (Codex 6차 · 설계 addendum2)

**바뀐 것**: 허용된 클래스 **안**의 금지 멤버를 정책 파일에 손으로 적지 않는다. 대신
`effect.surface.packages`·`effect.surface.classes` 가 **금지 표면**을 효과 어휘로 고정하고,
`memberEffectGate` 가 허용 클래스의 public/protected 멤버 중 **서명 ∪ depth-1 본문**이 그 표면에
닿는 것을 도출한다. 도출 결과는 `member-effects.generated.properties` 로 커밋되고 분류는
`member-effects.properties` 가 갖는다. **미분류 후보가 남거나 재생성 결과가 커밋본과 다르면
빌드가 실패한다.** ArchUnit 규칙은 접근의 owner 가 아니라 **선언 클래스**로 판정한다.

**사유 — 손 열거 셋으로는 닫히지 않는 자리가 있었다.** `RuntimeException("x").printStackTrace()`
가 `System.err` 로 쓰는데 어느 층도 잡지 못했다(Codex 6차). `Throwable` 계열을 허용 목록에서
빼는 것으로는 못 닫는다 — 바이트코드의 owner 가 구체 예외 타입이고, 그 타입들
(`IllegalArgumentException`·`IllegalStateException`·`NumberFormatException`)은 `require`/`check`/
`toInt()` 가 컴파일러 산출로 내므로 뺄 수 없다. **여집합**(허용 목록 밖에 닿으면 불순)도 쓰지
못한다 — 설계 검토가 `ArrayList#add`·`String#substring`·`Integer#valueOf`·`HashMap#put`·
`StringBuilder#append` 가 전부 불순이 되는 것을 실측했다.

**그래서 열거의 대상을 옮긴다 — 「어떤 멤버가 위험한가」에서 「어떤 효과가 금지인가」로.**
이 이동이 이득인 근거 둘: ① 효과 어휘는 승인 문면이 이미 고정했고(`milestone-1.md` 「구현 규칙」의
*"domain은 I/O가 없는 입력→출력 함수/객체다"* · `v2-지침서.md` §3.1) 라운드마다 늘지 않는다 —
반면 멤버 목록은 여섯 라운드 연속 늘었다. ② 미분류 후보가 빌드 실패이므로 **생각해 내지 못한
멤버가 조용히 열리지 않는다.** 손 열거 셋(`getBoolean`·`getInteger`·`getLong`)은 도출이 스스로
재도출했다 — 사람이 넣은 것이 아니다.

**폐쇄성은 부분이다. 숨기지 않는다.** 효과 표면 자체의 누락은 막지 못하고, depth-1 을 넘는
위임 중 이름이 겹치지 않는 것도 놓치며, T-B 정확 패키지의 멤버(`LocalDate.now()`)는 이 도출의
대상이 아니다(1B). 셋 다 알려진 제한에 등재했다.

**대가**: 도출이 JDK 판에 매여 있어 JDK 가 바뀌면 재생성 대조가 실패한다. **조용히 지나가는
것보다 낫다**는 판단이고, 실패 메시지가 고치는 방법을 적는다.

### 2026-09-02 — 모듈이 자기 패키지만 소유하도록 강제한다 (Codex 3차 · 운영자 승인)

**바뀐 것**: 각 Gradle 프로젝트의 class output 전건이 그 모듈이 소유한 `bidvector.<module>` 아래에
있어야 한다(`packageOwnershipGate`).

**사유**: 그 전까지 경계 규칙 전체가 **클래스의 자기 신고**에 기댔다. `decision` 에 선언 없는
클래스나 `bidvector.app.sneaky` 를 두면 앞의 것은 `importPackages("bidvector")` 의 대상이 아예
아니고 뒤의 것은 app 층으로 오인된다. **위반 fixture 열넷이 전부 자기 세그먼트를 옳게 선언하고
있었으므로 fixture 로도 드러나지 않는 약점**이었다. 판정을 **산출물의 위치**로 옮겨 닫는다 —
선언이 아니라 class 파일이 어디에 놓였는지가 근거다.

### 2026-09-02 — `in_scope` 를 승인 문서 두 자리로 넓힌다 (Codex #1 · 운영자 결정)

**넓힌 범위**: `docs/adr/0006-gradle-modules.md`(D-2 표와 신설 D-2.1) · `milestone-1.md`(1A 모듈
목록). **그 두 자리에 한정**하고 두 문서의 다른 절은 손대지 않는다.

**사유**: Codex 1차 리뷰 #1 — *"구현 evidence 가 새 `OPEN-ADR-14` 와 운영자 보류를 주장하더라도
승인된 ADR·마일스톤은 변경되지 않아 discovery 문서가 authoritative 요구사항을 대체한 상태다."*
**맞는 지적이다.** 원래 계약은 `bidding` 을 `out_of_scope` 에 두고 근거를 evidence 와
`capability-map.md` 에만 적었는데, `ADR 0006` D-2 와 `milestone-1.md` 1A 가 열 모듈을 요구하는
한 구현·정책·테스트가 승인 문서와 어긋난 채로 남는다.

**처리**: 운영자 결정(2026-09-02)으로 **정본을 고친다.** `ADR 0006` 에 D-2.1 을 신설해 보류
결정·결정자·시점·근거·되살리는 조건을 적고 D-2 표의 행에 취소선을 얹었다 — **결정 내용을
지우지 않는다**(M0 0E 의 `OPEN-ADR-01` 해소 블록과 같은 관례). `milestone-1.md` 1A 목록에서
`bidding` 을 빼고 근거는 ADR 을 가리킨다. 추적은 그대로 `OPEN-ADR-14`.

### 2026-09-02 — `in_scope` 의 두 어긋남을 정정한다 (verifier M-2)

**`.gitignore` 를 `in_scope` 에 넣는다.** 이 slice 가 Gradle 산출물 규칙을 넣으며 실제로
변경했는데 계약에 없었다. **하필 그 파일이 B-1 의 원인**이다 — 계약 밖에서 바뀐 파일이
빌드에 필요한 소스를 삼켰고, `in_scope` 가 그 파일을 들고 있었다면 계약 검토 자체가 한 번
더 눈을 붙일 자리였다.

**`docs/adr/0007-architecture-and-test-gates.md` → `docs/adr/0007-test-pyramid-and-ratchet.md`.**
앞의 경로는 **존재하지 않는다.** 계약을 쓸 때 ADR 번호만 보고 파일명을 지어냈고, 실제 편집은
올바른 파일에 했다. 계약이 없는 경로를 가리키면 「계약 밖 편집이 있었는가」를 그 계약으로
판정할 수 없다.

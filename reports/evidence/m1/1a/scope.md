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
  - docs/adr/0007-architecture-and-test-gates.md   # §5 의 OPEN-ADR-08 행 하나
  - docs/discovery/capability-map.md               # §12 registry 의 해당 행 + bidding 보류 OPEN 신설
  - reports/evidence/m1/1a/{scope,commands,checklist,rollback}.md
out_of_scope:
  - 도메인 코드 일체                   # 1B~1E. 1A 가 넣는 것은 모듈 경계 앵커 아홉뿐
  - bidding 모듈                       # 운영자 결정 2026-09-02 보류 — 아래 「결정」 D-2
  - Spring controller · DB schema · Flyway migration 실행 · 실제 외부 호출
  - Python ML · 기존 Python 과의 byte-for-byte 동등성
  - mutation testing 적용              # OPEN-ADR-07 — 카탈로그 좌표 등재만
  - 위 두 줄 외의 docs/adr · capability-map 편집
  - bid-vector/ symlink 아래 기존 저장소   # 읽기 전용
  - _workspace/**                     # .gitignore 대상
acceptance_commands:
  - "./gradlew --no-build-cache clean check"       # A1
  - "./gradlew :app:test --tests '*ArchitectureGate*'"   # A2 — 위반 fixture 음성 테스트 포함
  - "./gradlew qualityBaseline"                    # A3 — OPEN-ADR-06 입력 실측
  - "./gradlew compatibilitySmoke"                 # A4 — 채택 라이브러리 일곱 × Boot 4.1.1
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
| 테스트 플랫폼 | **JUnit 6.1.3** | Boot 4.1.1 이 이미 jupiter 6 을 관리하고 ArchUnit 1.5.0 이 `archunit-junit6` 을 냈다 |
| property/assertion | **Kotest 6.2.4 의 `kotest-property` + `kotest-assertions-core`만** | runner 를 붙이지 않는다 — `kotest-property` 는 junit-platform 에 의존하지 않아 평범한 Jupiter 테스트에서 호출된다. **jqwik 탈락**: 1.10.1 이 junit-platform 1.14.4 에 묶여 있고 2.x 라인이 없다 |
| 아키텍처 테스트 | **ArchUnit 1.5.0** (`archunit-junit6`) | `ADR 0007` D-6 채택. **Konsist 불채택 유지** — 21 개월 정체 + 번들 Kotlin 컴파일러 2.0.20 이 우리 소스 2.4.10 을 파싱한다는 근거가 없다 |

**`kotlin.version` 충돌**: Boot 4.1.1 BOM 이 `kotlin-stdlib` 을 2.3.21 로 관리한다. `app` 에서
`ext["kotlin.version"]` 을 카탈로그 값으로 덮어 **카탈로그가 이기게** 한다.

### D-2. 모듈은 아홉이다 — `bidding` 은 보류

`v2-지침서.md` §3.1 과 `ADR 0006` D-2 의 목록은 열이고, 이 slice 는 그중 **아홉**을 만든다.
`bidding` 을 빼는 근거는 `milestone-1.md:25` — *"M0 필수 capability에 없는 모듈은 만들지 않는다"* —
와 scout 노트 §1.3 (1) 의 실측이다: `capability-map.md` 의 95 개 capability 중 **「투찰 계획과 상태」를
소유하는 항목이 없다.** SET-01·SET-02 가 「실투찰 레코드」를 **전제**하나 그 전제를 소유하는
capability 가 등재돼 있지 않다.

`ADR 0006` D-2 가 목록에 넣었으므로 이것은 **ADR 과 capability map 사이의 갈림**이고, 구현자가
혼자 닫을 것이 아니다 — **운영자 결정 2026-09-02 로 보류**하고 `capability-map.md` §12 에
`OPEN` 을 신설해 소유 capability 가 설 때 재개하도록 등재한다. 아홉 모듈은 `ADR 0006` D-5 의
「목록 밖 신설」에 해당하지 않는다(빼는 것이지 더하는 것이 아니다).

### D-3. 패키지 규약 — `ADR 0006` §6:172 가 1A 로 넘긴 결정

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

**완화 — 게이트를 alpha 에 종속시키지 않는다.** `ADR 0007` D-7 이 *"게이트 도구 자체가 alpha 면
게이트의 안정성이 도구에 종속된다"*를 근거로 자체 검사를 요구한다. 그래서 **함수 50줄·파일
500줄의 두 임계는 도구 없이 서는 `sizeGate` task 가 들고**, detekt 은 그 위의 축(복잡도·중첩·
파라미터 수·인터페이스 크기·오버로드)을 맡는다. detekt 이 죽어도 승인된 두 임계는 계속 걸린다.

### D-5. 임계는 코드가 아니라 **versioned policy 데이터**다

`config/quality/size-policy.properties` 하나가 정본이고 policy version 을 갖는다. 값은
`v2-지침서.md` §5:305 의 **함수 50줄 · 파일 500줄** 둘뿐이다 — 승인 문서가 정한 임계는 이 둘이
전부이므로 나머지 축의 수치를 1A 가 지어내지 않는다.

detekt 이 같은 함수 임계를 알아야 하지만 **수를 두 자리에 적지 않는다** — 정책 파일에서 detekt
설정 overlay 를 생성해 얹는다(`detektThresholdOverlay`). 정책 파일이 유일한 자리다.

**baseline 파일을 만들지 않는다.** 그린필드에서 detekt baseline 을 생성하면 *"지금 상태는 정상"*
을 박제하게 되고, 이것이 `ADR 0007` D-4 가 금지한 **「baseline 을 느슨하게 갱신해 우회」** 의
출발점이다. 첫날부터 무관용으로 건다.

### D-6. **「신규 파일/함수 예산」의 수치를 1A 가 정하지 않는다**

`milestone-1.md:85` 가 완료 조건으로 *"신규 파일/함수 예산 위반 없음"* 을 들지만 **그 「예산」의
정의가 승인 문서에 없다**(scout 노트 X-3 — 인접 문면 둘이 서로 다른 축을 가리킨다). 1A 는
**정의를 자기 승인하지 않는다.** 대신 둘을 한다.

1. `v2-지침서.md` §5:305 가 **실제로 정한** 두 한도(함수 50 · 파일 500)를 게이트로 건다.
2. §5:296~303 의 여섯 축과 `OPEN-ADR-06` 이 묻는 세 축(클래스/타입 크기 · 상속 깊이 · mixin 수)을
   **재는 task** 를 만들고 그 실측을 `capability-map.md` §12 에 등재한다. **결정은 운영자**다
   (`capability-map.md:3284` — 결정 주체가 운영자, 시점이 1A 실측 뒤).

**1A 실측의 한계를 미리 적는다**: 그린필드라 대부분의 축이 바닥값(0/1)이다. 1A 가 남기는 값은
「현재 코드가 얼마나 큰가」가 아니라 **「각 축을 어떻게 재는가」의 정의와 그 정의가 도는 증거**다.

### D-7. 4.x 스모크는 **의존 해석 + 컴파일 + 클래스 로드**까지다

`v2-지침서.md` §5:241~247 과 `ADR 0004` §5:176 이 1A 에 지운 실측이다. 범위를 계약이 정한다
(scout 노트 X-4 가 지목한 구분): **Flyway 는 좌표를 세워 해석·컴파일되는지만 재고 migration 을
쓰지 않는다.** 마찬가지로 db-scheduler 는 스케줄을 돌리지 않고, Testcontainers 는 컨테이너를
띄우지 않는다. **runtime 사용은 해당 slice 의 일이다.**

`app` 이 스모크 자리다 — Boot 플러그인을 **`app` 에만** 적용한다(scout 노트 X-5 의 갈림에 대한 답).
`milestone-1.md:99` 의 범위 밖은 **controller** 이지 플러그인이 아니며, 플러그인을 세우지 않으면
§5:244 가 지운 측정 자체가 성립하지 않는다. `app` 에 controller·`main`·`@SpringBootApplication`
은 없고 `bootJar` 는 비활성이다.

### D-8. `broker` 의존을 세우지 않는다

`v2-지침서.md` §3.1:125 가 아직 `adapters` 에 `broker` 를 적지만 `ADR 0006` D-2 가 그 항목을
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

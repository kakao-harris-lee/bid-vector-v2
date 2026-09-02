# checklist — M1 / 1A

## M1 완료 조건 대조 (`milestone-1.md` 「완료 조건」)

`milestone-1.md` 의 완료 조건은 **M1 전체**의 것이다. 1A 가 담당하는 것과 1B~1E 로 넘어가는
것을 나눈다 — 1A 가 남의 조건까지 「충족」이라 적으면 그 서술이 곧 거짓이 된다.

**조건은 문면으로 가리킨다** — 줄 번호를 쓰지 않는다. 이 slice 가 `milestone-1.md` 를 편집했고
그 아래 좌표가 전부 밀렸다(verifier r5 M-1). 아래 「조건」 열은 그 문서의 **축어**라 그대로 grep 키다(공백까지 원문이다).

| 조건 | 1A 판정 | 근거 |
| --- | --- | --- |
| `./gradlew check` 통과 | **충족** | `commands.md` `A-1` |
| 금지 import와 순환 의존을 일부러 넣은 test fixture가 실제로 실패 | **충족** | `commands.md` RED → GREEN. 위반 다섯 형태(금지 import · 역방향 의존 · 업무 모듈 직접 참조 · 패키지 순환 · 기술 계층 패키지명)에 **금지 가족 일곱**(Spring·JPA·JSON·HTTP·broker·SQL·I/O)을 더해 심었고, fixture 가 없던 시점에 같은 단언이 **전부 실패**했다 |
| 승인된 authoritative corpus 전체 통과 | **pending — 1B~1E** | 1A 는 corpus 를 소비하지 않는다. `golden-manifest.json` 이 없는 이유이기도 하다 |
| 중요 rule mutation이 생존하지 않음 | **pending — 1B~1E** | 도구가 `OPEN-ADR-07` 로 미결. 카탈로그에 pitest 좌표만 등재하고 적용하지 않았다 |
| raw `Double` 금액/rate가 public domain API에 없음 | **pending — 1B**. 1A 는 **게이트가 표현 가능한지**까지 | ArchUnit 이 시그니처 타입을 볼 수 있으므로 1B 가 값 타입을 넣는 시점에 규칙으로 표현 가능하다. **1A 는 그 규칙을 쓰지 않았다** — 지킬 대상이 없는 규칙은 빈 집합 위에서 통과하고, 그것이 이 slice 가 피한 형태다 |
| `Uncertain`/`Unmeasurable`가 성공 또는 0으로 합쳐지지 않음 | **pending — 1C·1D** | 도메인 타입이 없다 |
| 신규 파일/함수 예산 위반 없음 | **부분 — 정의가 부재하다** | 아래 「예산」 |

### 「신규 파일/함수 예산」 — 1A 가 수치를 정하지 않은 이유

**그 「예산」의 정의를 승인 문서에서 찾지 못했다.** 인접 문면 둘이 서로 다른 축을 가리킨다 —
`v2-지침서.md` §5:305 는 **크기 한도**(함수 50 · 파일 500)를, §2:55 는 **책임·의존성 예산**을
말한다. 등록된 `OPEN` 은 없다.

1A 가 한 것: **실제로 정해진 두 한도를 게이트로 걸었다**(`E-1`·`E-2`). 하지 않은 것: 「신규 파일
수」·「신규 함수 수」의 상한을 **지어내지 않았다.** 구현이 자기 완료 조건의 판정 기준을 스스로
쓰면 그 조건은 게이트가 아니다.

## evidence 최소 목록 (`agent-workflow.md` §6)

| 파일 | 상태 |
| --- | --- |
| `scope.md` · `commands.md` · `rollback.md` | 있다 |
| `differential.json` | **N/A** — Python 과 비교할 산출이 없다. 1A 는 도메인 판정을 만들지 않는다(`agent-workflow.md` §6 이 *"적용 가능한 경우"* 로 조건을 단다) |
| `golden-manifest.json` | **N/A** — 사용한 fixture 가 없다. 1A 의 「fixture」는 **의도적 위반 코드**이고 그것은 corpus 가 아니라 게이트의 시험 대상이다(`ADR 0007` D-3 이 M-10 을 mutation test 와 구분하는 것과 같은 이유) |
| `codex-review-*.json` | 심판 레인 소유. 이 레인이 만들지 않는다 |

## 리뷰 요청 조건 (`evidence-pack` SKILL)

| 항목 | 상태 |
| --- | --- |
| 구현 diff 가 커밋되어 base/head 고정 | `git status --porcelain -- <in_scope 경로>` 가 비어 있어야 한다 — 판정은 verifier 레인이 재실행한다. **이 명령만으로는 부족하고** 그 틈은 `A-0`·`A-0b` 가 맡는다 — 근거는 `commands.md` 의 `A-0b` 절 |
| `acceptance_commands` 전부 exit 0 | 전건. 목록은 `scope.md` 의 `acceptance_commands` 가 내고 결과는 `commands.md` 의 acceptance 절이 낸다 — 여기서 다시 열거하면 목록이 늘 때마다 낡는다 |
| test/lint/type/architecture 통과 | `A-1` 이 전부를 든다 — **`build-logic` 의 lint·detekt·크기 게이트와 판정 테스트까지 포함한다**(Codex #5) |
| 변경된 fixture 와 정책 version 의 근거 | 정책 데이터 둘 다 `policy.version` 을 갖고 값의 출처를 파일 주석이 든다 — **현재 값은 `grep '^policy.version' config/quality/*.properties` 가 낸다**(수를 여기 박으면 정책이 바뀔 때마다 낡는다). **임계 수치는 1A 가 고르지 않았다** — `v2-지침서.md` §5:305 의 둘을 옮겼을 뿐이다. 반면 **금지 가족의 좌표 열거는 1A 가 고른 정책**이고(Codex #3 대응) 그 근거는 가족마다 심은 fixture 다 |
| 알려진 제한과 rollback | 아래 「알려진 제한」 · `rollback.md` |
| 비밀값 스캔 | `commands.md` 의 비밀값 스캔 절 |

## 알려진 제한

1. **coverage 는 측정하고 임계는 없다.** `check` 가 모듈마다 Kover XML·HTML 리포트를 내고
   그 경로(`*/build/reports/kover/`)를 CI 가 아티팩트로 올린다 — **수치는 나온다.** 없는 것은
   **검증 규칙**이다: 승인 문서에 임계가 부재하고(`milestone-1.md` 1A 항목이 *"formatting, lint, unit test, coverage,
   size/complexity ratchet"* 으로 `coverage` 를 이름으로만 든다) **도메인 코드가 없는 상태에서 정한 수치는 근거가 없다.** 그래서 `check` 는 coverage 로
   실패하지 않는다 — **1B 가 첫 도메인 코드와 함께 정한다.**
2. **`bidding` 모듈이 없다.** 승인 문서(`ADR 0006` D-2.1 · `milestone-1.md` 1A)가 이 보류를
   담고 있고 `OPEN-ADR-14` 가 되살리는 조건을 든다 — **더는 evidence 만의 주장이 아니다**(Codex #1).
3. **두 게이트의 사각이 서로 반대다.** ArchUnit 은 바이트코드를 보므로 **선언만 되고 참조가
   없는 의존**을 못 보고(`E-9b`), 의존 그래프 게이트는 **Maven group 이 없는 JDK 타입**을 못 본다
   (`java.net`·`java.sql`·`java.io`). 그래서 둘 다 둔다 — 어느 쪽도 혼자로는 충분하지 않다.
   `commands.md` 의 가족별 표가 그 분담의 실측이다. Kotlin `internal` 이 바이트코드에서
   public 으로 보이는 것도 같은 성질이고, `qualityBaseline` 의 public API 수가 그 영향을 받는다.

   **두 사각이 겹치는 자리가 하나 있다 — 컴파일 시 인라인되는 상수.** domain 이
   `java.net.HttpURLConnection.HTTP_OK` 만 쓰면 바이트코드에 그 타입 참조가 한 건도 남지
   않아(`javap` 로 확인) 2 차가 못 보고, JDK 타입이라 Maven group 이 없어 1 차도 못 본다 —
   **어느 층도 잡지 못한다.** 실질 위험은 낮다: 넘어오는 것이 `int` 값 하나라 프레임워크
   결합이 실제로 들어오지는 않는다. 그러나 「두 사각이 서로 반대라 둘이면 덮인다」는 말은
   **이 자리에서 참이 아니다.**
4. **detekt 2.0 은 alpha 다.** 규칙과 설정 키가 정식 출시 전에 바뀔 수 있다. 승인된 두 임계 중
   파일 축은 detekt 밖(`sizeGate`)에 있어 detekt 이 죽어도 남는다.
5. **모듈 안의 패키지 순환은 아직 시험되지 않았다.** 규칙(`bidvector.(**)` slice)은 걸려 있으나
   각 모듈에 패키지가 하나뿐이라 모듈 **내부** 순환을 만들 자리가 없다. 위반 fixture 의 순환은
   모듈 사이다. 1B 가 패키지를 늘리면 그때 안쪽에서도 구속력이 생긴다.
6. **CI 워크플로가 실행된 적 없다.** 원격에 push 하지 않았으므로 `.github/workflows/ci.yml` 은
   **문법만 갖춘 골격**이고 GitHub 러너에서 도는 것을 확인하지 못했다.
7. **뿌리 패키지 금지의 과잉 도달 — `java.net` 이 `java.net.URI` 도 막는다.** 금지 좌표를
   뿌리로 올린 대가다(잎을 적으면 형제가 새는 쪽이 더 나쁘다). 도메인이 URI 를 값으로
   모델링하려 하면 2 차 게이트가 실패한다. **1A 는 이것을 고치지 않는다** — 1A 의 값 타입
   규율에 비추면 오히려 맞는 제약일 수 있고, 판단은 실제로 필요해지는 slice 의 몫이다.
   필요해지면 `ADR 0007` D-4 의 allowlist 형식(사유 + 해소 계획)으로 연다.
   **함께 잰 것**: `java.math.BigDecimal` 과 `Charsets` 는 막히지 않는다 — 1B 의 금액 타입과
   인코딩은 안전하다.
8. **RED/GREEN 커밋을 checkout 해서는 재현할 수 없다.** 빌드에 필요한 여덟 파일이 `90e46ff`
   에서 처음 등장하므로 그 앞 구간(RED·GREEN 커밋 포함)은 clean checkout 에서
   `:build-logic:compileKotlin` 이 깨진다. **이력은 되쓰지 않는다.** head 는 영향받지 않고
   주장 자체는 확인됐다 — verifier 가 그 구간에 파일을 복원해 **RED = 음성 단언 전부 실패 ·
   양성 전부 통과, GREEN = exit 0** 을 실측했다(그 시점의 수는 verifier r1 리포트가 갖는다).

   **현재 head 에서 RED 를 재현하는 방법** — 위반 fixture 를 지우면 음성 단언이 되살아난다.

   ```
   rm -r app/src/test/kotlin/bidvector/archfixture/violating
   ./gradlew :app:test --tests '*ArchitectureGate*'      # 음성 단언 전부 실패 · 양성 전부 통과
   git checkout -- app/src/test/kotlin/bidvector/archfixture
   ```

   테스트는 fixture 를 **패키지 이름 문자열로만** 참조하므로 fixture 가 없어도 컴파일된다 —
   그래서 결과가 컴파일 오류가 아니라 「위반을 못 찾았다」가 되고, 그것이 음성 단언의
   RED 다. **수는 적지 않는다** — 음성 단언은 금지 가족이 늘면 함께 늘고(실제로 5 → 11 로
   늘었다) 그때마다 이 문단이 낡는다. 명령이 내는 수를 읽으면 된다.

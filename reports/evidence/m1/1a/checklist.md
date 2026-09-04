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
| raw `Double` 금액/rate가 public domain API에 없음 | **충족(게이트) · pending — 1B(값 타입)** | ~~1A 는 **게이트가 표현 가능한지**까지. ArchUnit 이 시그니처 타입을 볼 수 있으므로 1B 가 값 타입을 넣는 시점에 규칙으로 표현 가능하다. **1A 는 그 규칙을 쓰지 않았다** — 지킬 대상이 없는 규칙은 빈 집합 위에서 통과하고, 그것이 이 slice 가 피한 형태다.~~ **(정정, Codex 15차 low, 2026-09-04)** `domainApiTypeGate`(정책·fixture·테스트 포함)는 2026-09-04 계약 갱신(scope.md 「public domain API 의 raw `Double` 게이트를 1A 에서 세운다」)으로 **1A 가 구현·검증했다** — 위반 fixture 로 잡힘/양성 corpus 로 안 잡힘 둘 다 확인됐다(`commands.md` 「public domain API 의 raw `Double` 게이트」 절, `E-86`~`E-91`). **도메인 값 타입(`Money`/`Rate`) 자체는 여전히 1B 소유** — 1A 는 그 타입이 생기면 걸리는 규칙만 세웠고, 오늘 domain main 에 그 타입이 없어 규칙은 빈 집합 위가 아니라 committed fixture 위에서 실측됐다(`DomainApiTypeFixtureTest`) |
| `Uncertain`/`Unmeasurable`가 성공 또는 0으로 합쳐지지 않음 | **pending — 1C·1D** | 도메인 타입이 없다 |
| 신규 파일/함수 예산 위반 없음 | **부분 — 정의가 부재하다** | 아래 「예산」 |

### 「신규 파일/함수 예산」 — 1A 가 수치를 정하지 않은 이유

**그 「예산」의 정의를 승인 문서에서 찾지 못했다.** 인접 문면 둘이 서로 다른 축을 가리킨다 —
`v2-지침서.md` §5 「크기와 결합도」 는 **크기 한도**(함수 50 · 파일 500)를, §2 의 *"처음부터 책임·의존성 예산을 CI로 강제"* 는 **책임·의존성 예산**을
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
| 변경된 fixture 와 정책 version 의 근거 | 정책 데이터 둘 다 `policy.version` 을 갖고 값의 출처를 파일 주석이 든다 — **현재 값은 `grep '^policy.version' config/quality/*.properties` 가 낸다**(수를 여기 박으면 정책이 바뀔 때마다 낡는다). **임계 수치는 1A 가 고르지 않았다** — `v2-지침서.md` §5 「크기와 결합도」 의 둘을 옮겼을 뿐이다. 반면 **금지 가족의 좌표 열거는 1A 가 고른 정책**이고(Codex #3 대응) 그 근거는 가족마다 심은 fixture 다 |
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
3. **domain 경계는 네 층이고 각 층의 실패 방향이 고정돼 있다.** T-A 하위 허용(`bidvector`) ·
   T-B 정확 패키지(동질) · **T-C 클래스 단위 허용**(이질 패키지 `java.lang`·`java.util`) ·
   T-D 멤버 단위 금지. **T-C 가 핵심이다** — 그 전에는 두 패키지 217 종 중 16 종만 deny 로
   분류돼 201 종이 열려 있었고, 리뷰가 네 라운드에 걸쳐 그 안에서 좌표를 하나씩 집어냈다.
   이제 목록에 없으면 닫히므로 미분류가 자동으로 막힌다.
   **T-D 도 열거를 떠났다.** `Integer.valueOf`(필수)와 `Integer.getInteger`(환경 읽기)가 같은
   클래스라 클래스 단위로 가를 수 없는 것은 그대로인데, 이제 그 멤버를 손으로 적지 않는다 —
   **효과 표면**(승인 문면의 I/O·프로세스·스레드·환경·리플렉션)을 정책에 두고 허용 클래스의
   멤버 중 거기 닿는 것을 도출한다. **미분류 후보가 남으면 빌드가 실패**하므로 생각해 내지
   못한 멤버가 조용히 열리지 않는다. 열거가 사라진 것이 아니라 **열거의 대상이 「어떤 멤버가
   위험한가」에서 「어떤 효과가 금지인가」로 옮겨갔고**, 뒤쪽은 문면이 이미 고정한 어휘다.
   **열거를 완전히 없애는 설계는 없다** — 설계 검토가 「API 표면이 순수한가」로 기계 유도를
   시도해 `String`·`List`·`Map`·`BigDecimal` 이 전부 탈락하고 정작 `Timer` 는 통과하는 것을
   실측했다. 고를 수 있는 것은 열거의 **방향**뿐이다.
4. **`class.allowed` 는 툴체인 bump 에 취약하다 — 다만 실패가 닫히는 쪽이다.** Kotlin/JDK 가
   구현 타입을 바꾸면 목록이 낡는다. 그때 양성 corpus 가 **1A 에서 먼저 깨진다** — 낡음이
   열리는 쪽인 설계(deny-list)에서는 리뷰어가 찾아낼 때까지 보이지 않았다. 이 비대칭이 전환의
   근거이고, 그래서 corpus 를 1B~1E 형태로 채웠다.
5. **게이트가 실제 도메인 형태로 시험된다.** 양성 fixture 가 `data class`·nullable·컬렉션·
   `sealed`·`when`·비교를 담아 같은 규칙을 통과한다. 이것이 없으면 게이트는 **도메인이 비어
   있는 동안만** 초록이고, 1B 의 첫 커밋이 `check` 를 깨는 자리에서 게이트를 느슨하게 하려는
   압력이 생긴다. **음성 단언도 이름이 아니라 사유를 본다** — 컴파일러 삽입 `@NotNull` 이
   fixture 이름을 위반 상세에 남겨서, 금지를 걷어도 단언이 죽지 않는 masking 이 있었다.
6. **경계 판정의 근거는 산출물의 위치다.** 그 전에는 클래스의 자기 신고에 기댔고, 선언 없는
   클래스와 남의 세그먼트 참칭이 둘 다 게이트 밖이었다. `packageOwnershipGate` 가 class output
   위치로 그것을 닫는다 — **위반 fixture 는 이 약점을 드러내지 못한다**(전부 자기 세그먼트를
   옳게 선언하므로). 그래서 그 게이트의 음성은 fixture 가 아니라 순수 함수 테스트와 실측이 든다.
7. **두 게이트의 사각이 서로 반대다.** ArchUnit 은 바이트코드를 보므로 **선언만 되고 참조가
   없는 의존**을 못 보고(`E-9b`), 의존 그래프 게이트는 **Maven group 이 없는 JDK 타입**을 못 본다
   (`java.net`·`java.sql`·`java.io`). 그래서 둘 다 둔다 — 어느 쪽도 혼자로는 충분하지 않다.
   `commands.md` 의 가족별 표가 그 분담의 실측이다. Kotlin `internal` 이 바이트코드에서
   public 으로 보이는 것도 같은 성질이고, `qualityBaseline` 의 public API 수가 그 영향을 받는다.

   **두 사각이 겹치는 자리가 하나 있었다 — 컴파일 시 인라인되는 상수. 이제는 셋째 층이
   덮는다.** domain 이 `java.net.HttpURLConnection.HTTP_OK` 만 쓰면 바이트코드에 그 타입
   참조가 한 건도 남지 않아(`javap` 로 확인, `commands.md` `E-79`·`E-80`) 2 차가 못 보고, JDK
   타입이라 Maven group 이 없어 1 차도 못 본다. `domainSourceReferenceGate`(소스 층, Codex
   13차)가 이 자리를 덮는다 — 소스가 그 이름을 **부른다는 사실 자체**를 PSI 로 보므로 바이트코드
   에 남는지와 무관하다. 남는 사각은 소스에도 이름이 없는 형태(미수식 참조, 제한 18)뿐이다.

   **세 번째 인스턴스 — raw `Double` public API(운영자 결정 2026-09-04).** `rate(): Double`
   같은 수식 없는 반환은 JVM primitive `D` 라 바이트코드에 클래스 참조가 아예 없다. **다만
   `Double?`·`List<Double>` 은 boxing·제네릭 소거로 `java.lang.Double`/`Number` 참조가
   실제로 남는다**(`javap` 로 확인, `commands.md` `E-86`) — 그런데도 안 잡히는 이유는 참조
   부재가 아니라 그 좌표가 이미 `class.allowed.api`(T-C)로 열려 있기 때문이다. 사각의 이유가
   fixture 형태마다 다르다는 것이 이번에 새로 드러난 것이고, 그 구분 없이 「primitive 라
   참조가 없다」로만 적으면 제한 11 이 겪은 것과 같은 오류가 된다. `domainApiTypeGate`(타입
   표면 층)가 이름 자체를 보므로 참조 유무와 무관하게 덮는다.
8. **승인된 두 임계는 이제 둘 다 도구 비의존이다.** 함수 축을 Kotlin PSI 로 직접 재므로
   detekt 이 죽어도, `@Suppress` 를 어떤 표기로 쓰든 임계가 남는다 — `detekt.yml` 의
   `LongMethod` 는 껐다. detekt 은 그 위의 축(복잡도·중첩·파라미터 수)만 맡고 **그 축들은
   detekt 과 함께 죽는다.**
9. **함수를 쪼개 응집도만 나빠지는 것은 막지 못한다 — 막으려 해서도 안 된다.** 60줄 함수를
   45줄 함수 둘로 나누면 크기 축은 통과한다. `v2-지침서.md` §5 가 *"줄 수만 맞추기 위한 파일
   분할을 금지한다"* 고 적고 `ADR 0007` D-5 가 *"크기 축만으로 판정하지 않는다"* 고 적는다 —
   이 축은 `OPEN-ADR-06` 과 `qualityBaseline` 의 결합도 측정이 들 자리이지 크기 게이트가
   흉내 낼 자리가 아니다.
10. **(폐기) `kotlin.Metadata` 원산지 검사** — 그 검사는 더 이상 존재하지 않는다. 원산지 층의
   현재 판정과 그 잔여는 **알려진 제한 26** 이 든다. 다른 항목이 이 목록을 번호로 가리키므로
   번호는 비우지 않고 남긴다.
11. **(정정, Codex 13차 실측) `X::class.java` 클래스 리터럴은 바이트코드 층도 잡는다.** 이전
   판정(설계 검토 U-8, `javap` 만 근거)은 상수 풀의 Class 엔트리가 ArchUnit 의존으로 기록되지
   않는다고 봤다. `ClassLiteralLeak` fixture 로 재보니 그 반대다 — 상수 풀 엔트리는 있고
   (`javap`, `commands.md` `E-81`: `ldc #15 // class java/net/HttpURLConnection`), ArchUnit 은
   그것을 `references class object` 라는 **별도 의존 종류**로 이미 추적한다
   (`ArchitectureGateCatchesViolationsTest` 의 `class literal 은 바이트코드 층도 별도 의존
   종류로 잡는다` 가 실측을 고정). 바이트코드 상수 풀의 모양과 ArchUnit 의 의미 모델은 다른
   층이라, `javap` 로 「엔트리가 있다」를 본 것과 「ArchUnit 이 의존으로 세는가」는 별개
   물음이었다 — 전자만 보고 후자를 단정한 것이 이전 판정의 오류다. 얻은 `Class` 로 무언가
   더 하면 그 다음 호출의 owner 가 `java.lang.Class` 라 T-C 도 잡는다(원래 서술). 실제 잔여는
   없다 — 컴파일 시 인라인되는 **상수**(제한 7)만 두 게이트 모두의 사각이었고, 그 자리는
   `domainSourceReferenceGate`(소스 층)가 덮는다.
12. **게이트 *배선*의 task 수준 음성이 자동화돼 있지 않다.** 판정 로직은 순수 함수 테스트로
   고정돼 있고(경계·소유·가족·함수 길이), 배선은 실측으로 확인했다(`E-21`~`E-32`). 그러나
   「Gradle task 가 실제로 실패한다」를 **테스트로** 고정하려면 중첩 빌드(GradleRunner/TestKit)가
   필요하다. 도입하지 않았다 — 실행 시간 대가가 있고 이 빌드(included build + configuration
   cache)에서 도는지 재지 않았다. **지금 증거는 자동 테스트가 아니라 기록된 실측이다.**
13. **모듈 안의 패키지 순환은 아직 시험되지 않았다.** 규칙(`bidvector.(**)` slice)은 걸려 있으나
   각 모듈에 패키지가 하나뿐이라 모듈 **내부** 순환을 만들 자리가 없다. 위반 fixture 의 순환은
   모듈 사이다. 1B 가 패키지를 늘리면 그때 안쪽에서도 구속력이 생긴다.
14. **CI 워크플로가 실행된 적 없다.** 원격에 push 하지 않았으므로 `.github/workflows/ci.yml` 은
   **문법만 갖춘 골격**이고 GitHub 러너에서 도는 것을 확인하지 못했다.
15. **허용 목록의 과잉 도달 — `java.net` 이 허용 정확 패키지에 없어 `java.net.URI` 도 막힌다.**
   allow-list 의 대가다 — 허용을 좁게 두면 정당한 타입도 함께 막힌다. 도메인이 URI 를 값으로
   모델링하려 하면 2 차 게이트가 실패한다. **1A 는 이것을 고치지 않는다** — 1A 의 값 타입
   규율에 비추면 오히려 맞는 제약일 수 있고, 판단은 실제로 필요해지는 slice 의 몫이다.
   필요해지면 `ADR 0007` D-4 의 allowlist 형식(사유 + 해소 계획)으로 연다.
   **함께 잰 것**: `java.math.BigDecimal` 과 `Charsets` 는 막히지 않는다 — 1B 의 금액 타입과
   인코딩은 안전하다.
16. **이 slice 의 `milestone-1.md` 편집이 `capability-map.md` 의 M0 작성 좌표를 깨뜨렸다 —
   역방향 파급이다.** 지금까지의 처리는 전부 *"내가 쓰는 좌표"* 쪽만 봤고, **남이 나를 가리키던
   좌표**는 규격에도 스윕에도 없었다(규격 정본은 `evidence-pack` 「낡는 좌표 — 역방향도 본다」).

   **조건**: `e733cfa` 가 1A 모듈 목록을 개정하며 그 자리에 net +5 줄을 넣었다. 그러므로
   **삽입 지점(1A 항목의 `bidding` 보류 문단) 아래를 가리키는 좌표는 +5 밀렸고, 그 위는 정확하다.**
   영향 목록은 세지 말고 명령이 내게 한다:

   ```
   grep -n 'milestone-1.md:[0-9]' docs/discovery/capability-map.md
   ```

   **`docs/adr/0007` 을 가리키는 좌표도 같은 자리에 있다.** 이 slice 가 그 문서에
   `OPEN-ADR-08` 해소 블록과 `§1.1.1` 을 넣어 **문서 머리 쪽부터 아래가 전부 밀렸고**,
   `capability-map.md` 는 그 문서를 **`docs/adr/0007:<줄>` 축약형**으로 인용한다
   (`docs/adr/0007:186` 은 base 에서 `### OPEN-ADR-06` 이었고 지금은 표 머리다). 목록과 개수는
   명령이 낸다 — 여기 박지 않는다:

   ```
   grep -rnoE 'docs/adr/0007:[0-9]+' docs/discovery/capability-map.md
   ```

   **전체 파일명 grep 이 이것을 0 건으로 냈다**(verifier r12 L-1). ADR 은 번호만으로도
   인용되므로 스윕 패턴은 **stem 기준**이어야 한다.

   **위협 모델 경계 라운드가 둘을 다시 밀었다** — `milestone-1.md` 에 「게이트 위협 모델」 절이,
   `ADR 0007` 에 `§1.1.2` 가 들어갔고 **둘 다 인용 지점보다 위**다. 그래서 `milestone-1.md`
   좌표 중 삽입 지점 아래의 것과 `docs/adr/0007` 축약형 전부가 한 번 더 어긋났다. 같은 원인·같은
   담당이라 이 항목이 함께 든다. **누적 어긋남을 수로 적지 않는다** — 삽입 지점과 증분은
   `git diff --numstat` 과 `git diff | grep '^@@'` 가 내고, 영향 목록은 위 두 grep 이 낸다.

   **1A 가 고치지 않는다.** 그 좌표들은 이 slice 의 `capability-map.md` 편집 경계
   (`§12 registry 의 해당 행`) 밖이고, 넘어서 고치면 계약 밖 편집이 된다. **운영자 결정
   2026-09-02 로 이월**했고 담당은 `capability-map.md` §14 가 이미 예고한 **registry 통합 slice**다 —
   §12 에 신설한 `OPEN-ADR-15` 가 그것을 들고 있다. **`milestone-1.md` 좌표와 `ADR 0007` 좌표를
   같은 항목이 든다** — 원인(이 slice 의 승인 문서 편집)과 담당이 같기 때문이다.

   **`git diff` 에 나타나지 않는 부류라는 것이 요점이다** — 깨진 줄은 이 slice 가 건드린 줄이
   아니어서 diff 를 읽는 리뷰로는 구조적으로 보이지 않는다. 그래서 여기 적는다.

   **같은 부류가 이 evidence 안에도 있었다 — 이번에 찾아 고쳤다.** `scope.md` D-3 이
   `ADR 0006` 을 줄 번호로 가리켰는데, 이 slice 가 그 문서에 `D-2.1` 을 신설하면서 그 좌표가
   밀려 있었다(가리키던 줄이 다른 내용이 됐다). 절 제목으로 바꿨다. `v2-지침서.md` 편집
   라운드에서도 같은 훑기를 돌렸고 **그쪽은 삽입 지점 위만 가리키고 있어 밀린 것이 없다** —
   그래도 그 파일로의 줄 번호는 인용문·절 제목으로 함께 걷었다. 재발을 막는 것은 스윕이
   아니라 **좌표의 형태**다.

   **Codex 12차 거버넌스 라운드가 다시 밀었다 — 그리고 이번 스윕이 대상을 넓혔다.**
   `milestone-1.md`(위협 모델 절 추가분, 삽입 지점 위 인용만 있어 안 밀림) ·
   `docs/adr/0007-test-pyramid-and-ratchet.md`(§1.1.2 개정 + D-5 추가분, **삽입 지점 둘 다
   D-6~OPEN-ADR-07 구간보다 위라 그 아래 인용 전부가 다시 밀렸다**) · `v2-지침서.md`(§5 한 줄,
   삽입 지점이 §5 「Kotlin」 문면보다 아래라 안 밀림) 세 문서에 stem 기준
   (`milestone-1`·`0007`·`지침서`) 역방향 파급 grep 을 **저장소 전체**에 돌렸다 — 앞선
   라운드는 `docs/discovery/capability-map.md` 한 파일로 좁혀 돌렸는데 이번에 범위를 넓히자
   **`capability-map.md` 밖의 citer 가 새로 드러났다**: `CLAUDE.md`·
   `.claude/skills/evidence-pack/SKILL.md` 가 각각 `docs/adr/0007:186` 을 인용하고,
   `evidence-pack/SKILL.md` 는 `ADR 0007 §6:172` 도 인용한다(이쪽은 §6 이 아니라 D-4 절이라
   **이 라운드 이전부터 이미 어긋나 있었다**). 목록과 대상은 아래 명령이 낸다 — 셈은 옮겨
   적지 않는다:

   ```
   grep -rnoE 'docs/adr/0007:[0-9]+|0007-test-pyramid-and-ratchet\.md:[0-9]+|ADR ?0007[^)]{0,3}:[0-9]+' \
     --include='*.md' --include='*.kt' --include='*.kts' --include='*.properties' . \
     | grep -v '^\./bid-vector/' | grep -v '/build/'
   grep -rnoE 'milestone-1\.md:[0-9]+|milestone-1:[0-9]+' \
     --include='*.md' --include='*.kt' --include='*.kts' --include='*.properties' . \
     | grep -v '^\./bid-vector/' | grep -v '/build/'
   ```

   **1A 가 고치지 않는 이유가 더 넓어졌다** — `capability-map.md` 는 §14 가 예고한 registry
   통합 slice 몫이라 쳐도, `CLAUDE.md` 와 `.claude/skills/**` 는 **이 slice 의 `in_scope`
   문서가 아니고 v2-slice-pipeline 스킬의 소관도 아니다**(하네스 자체 수정은 별도 트랙).
   담당은 여전히 운영자 결정과 §14 registry 통합 slice — `capability-map.md` 밖 citer 는 그
   slice 가 처리 범위를 정할 때 함께 볼 항목으로 이월한다.

   **Codex 15차(계약·rollback 정정) 스윕 — 영향 없음.** 이번 라운드는 `scope.md`·`rollback.md`
   자신을 편집했다(줄이 밀렸다). stem 기준(`scope`·`rollback`) 역방향 grep 을 저장소 전체에
   돌렸다:

   ```
   grep -rn 'scope\.md:[0-9]\|rollback\.md:[0-9]' \
     --include='*.md' --include='*.kt' --include='*.properties' .
   ```

   결과는 전부 `m0/0a2`·`m0/0a3`·`m0/0c` 의 **자기 slice 안 인용**(각 slice 의 `scope.md` 가
   자기 자신을 가리킨다)이고, `m1/1a` 를 가리키는 citer 는 저장소 어디에도 없다 — 이번
   편집이 밀어낸 좌표는 없다.
17. **비결정성(시계·난수)은 게이트 대상이 아니다 — 1B 인계.** `Instant.now()`·`Clock`·
   `UUID.randomUUID()`·`java.util.Random` 이 전부 통과한다. **승인 문면이 그것을 domain 패키지
   금지로 다루지 않기 때문**이고, 1A 가 문서에 없는 정책을 지어내지 않는다: `v2-지침서.md` §3.1 은
   Spring·JPA·JSON·HTTP·broker 를 들 뿐 시계·난수를 들지 않고, `milestone-1.md` 「구현 규칙」의
   *"domain은 I/O가 없는 입력→출력 함수/객체다"* 는 I/O 규정이지 결정성 규정이 아니다.
   **다만 1B 가 시계를 port 로 주입할 근거는 이미 있다** — `v2-지침서.md` 의 테스트 관행이
   fake clock 을 기본으로 둔다. 게이트로 막을 일이 아니라 **설계로 정할 일**이라는 뜻이다.

   **실제로 무엇이 열려 있는지는 실측이 다르게 답한다.** `UUID`·`Random`·`Date` 는 T-C 목록에
   없어 **이미 막힌다**(exit 1) — 1A 가 금지 정책을 세운 것이 아니라 목록에 넣지 않았을 뿐인데
   결과가 차단이다. **허용 62 종 안의 난수 둘도 이제 막힌다** — 효과 표면 도출이
   `Math#random`·`StrictMath#random`·`Collections#shuffle` 을 `java.util.Random` 에 닿는
   후보로 스스로 냈고 분류가 `forbidden` 이다(음성 fixture `NondeterminismLeak`). **정책을
   세운 것이 아니라 도출이 낸 것을 분류했을 뿐**이지만 결과는 차단이다.

   | 좌표 | 상태 | 근거 |
   | --- | --- | --- |
   | `java.lang.Math#random` · `java.util.Collections#shuffle` | **막힌다** | 도출 후보 → `forbidden` |
   | `java.time.Clock`·`Instant#now`·`LocalDate#now` | **열려 있다** | `java.time` 이 T-B(정확 패키지)라 통째로 열리고, 도출의 대상은 T-C 클래스뿐이다 |

   **그러므로 1B 에 남는 것은 시계다.** 결정성을 정책으로 정하면 조치는 T-B 멤버를 같은 기제로
   가져오는 것 — 도출의 입력에 `package.allowed.exact` 를 더하고 새 후보를 분류하면 된다.
18. **`String.format` 은 통과한다 — 형식화의 정책 경계가 게이트에 없다.** 설계 검토는
   **「형식화는 표현 관심사라 adapters 의 일」**이라 보고 `java.text` 를 허용에서 뺐는데,
   `String.format` 은 그 경계의 반대편에 남는다. 승인 문서가 이것을 금지하지 않으므로 1A 가
   막지 않았다 — 1B 가 도메인의 표현 책임을 정할 때 함께 볼 자리다.

   **다만 이제 「눈치채지 못한 구멍」이 아니라 기록된 판단이다.** 도출이 `String#format` 을
   기본 로케일에 닿는 후보로 냈고, 분류가 `reviewed` 이며 그 줄이 사유를 적는다. 양성 corpus
   (`DomainShapes.formatted`)가 이 통과를 고정하므로 판단이 바뀌면 그 자리가 함께 움직인다.
   **막히는 쪽의 짝은 제한 25 다** — 같은 형식화·로케일 축에서 `lowercase()` 는 차단된다.
   두 쪽 다 기록된 판단이어야 비대칭이 생기지 않는다.
19. **RED/GREEN 커밋을 checkout 해서는 재현할 수 없다.** 빌드에 필요한 여덟 파일이 `90e46ff`
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

20. **프로퍼티 초기화식은 함수 축이 재지 않는다.** `val t = when (x) { … 60 행 … }` 처럼
    본문 없이 값 표현식만으로 긴 판정을 쓰면 이 축을 지나간다 — 재는 대상이 **「본문을 갖는
    선언」**이기 때문이다(정의의 정본은 `config/quality/size-policy.properties`).
    **경계를 거기 그은 것은 의도다**: `v2-지침서.md` §5 가 분기 도배의 대안으로 rule table 을
    권하므로 데이터가 길다고 함수가 긴 것이 아니고, 초기화식을 재면 정책 표가 곧바로
    위반이 된다. **좁혀지는 폭** — 초기화식이 람다를 거치면(`run { }`·`let { }`) 그 람다가
    재지므로 이 경로는 값 표현식 하나에 한정되고, 파일 500줄 축은 그대로 걸린다.
    **스크립트의 top-level 문장도 같은 자리다** — `.kts` 는 함수 축의 대상이지만 top-level
    문장 자체는 본문이 아니어서 재지 않는다(그 안의 람다는 잰다).

21. **효과 표면 목록 자체의 누락은 막지 못한다.** 우리가 「효과」로 세지 않은 부작용은 도출에
    나오지 않는다 — 이 층의 진짜 잔여다. 줄어든 것은 **리뷰 대상의 크기**다: 후보 쉰다섯이
    아니라 표면 서른 몇 줄이 리뷰 대상이고, 그 목록은 승인 문면에 근거가 있어 라운드마다 늘지
    않는다. 반면 앞선 손 열거는 **여섯 라운드 연속** 늘었다.
22. **depth-1 을 넘는 위임 체인 중 이름이 겹치지 않는 것은 놓친다.** 도출은 멤버의 서명과
    **본문 한 단계**만 본다. 허용 클래스의 멤버가 같은 클래스의 다른 이름을 거쳐 두 단계
    아래에서 효과를 내면 후보에 오르지 않는다. 키가 디스크립터가 아니라 **이름**이라 오버로드
    위임은 대부분 흡수되지만(`printStackTrace()` ↔ `printStackTrace(PrintWriter)`) 완전하지 않다.
    전이 폐포로 넓히는 길은 닫혀 있다 — 설계 검토가 `ArrayList#add`·`String#substring`·
    `Integer#valueOf` 가 전부 불순이 되는 것을 실측했다.
23. **도출은 JDK 판에 매여 있고, 어긋나면 빌드가 실패한다.** 헤더가 `jdk.version` 을 적고
    게이트가 재생성본과 커밋본을 축어로 대조하므로, JDK 가 바뀌어 후보가 달라지면 **조용히
    지나가지 않는다**. 대가는 패치 판이 달라져도 같은 실패가 난다는 것이다 — 고치는 방법은
    재생성본을 덮고 새 후보를 분류하는 것 하나이고, 실패 메시지가 그 두 경로를 적는다.
    툴체인과 다른 JDK 로 Gradle 을 띄우면 도출 전에 먼저 막는다.

24. **멤버 판정은 ArchUnit 의 classpath 해석에 기대고 있다.** 해석을 끄면 이 corpus 의 접근이
    전부 미해결이 되고, 그때는 owner 의 상위 타입도 함께 알 수 없어 훑기 fallback 이 아무것도
    잡지 못한다 — `printStackTrace` 보고가 0 이 된다(실측). 기본값이 켜짐이라 지금은 참이지만
    `archunit.properties` 한 줄로 꺼질 수 있으므로, **그 설정을 단언하는 테스트**를 두어 부재가
    조용하지 않게 했다. 훑기가 실제로 덮는 것은 「owner 의 계보는 알지만 그 멤버만 못 찾는」
    좁은 경우이고, 그 경우를 이 corpus 에서 만들지는 못했다.

25. **대소문자 정규화는 현재 허용 경로가 없다 — 차단되는 쪽의 기록된 판단이다.**
    Kotlin 의 `lowercase()`/`uppercase()`(인자 없는 형)는 **의도적으로 로케일 비의존**이고
    로케일 의존적인 `toLowerCase()` 의 권장 대체인데, 도메인에서 쓰면 exit 1 이다.
    `String.toLowerCase(Locale.ROOT)` 로 컴파일되므로 **두 층이 동시에 문다**(실측: 두 규칙이
    각각 보고한다) — 효과 분류의 `java.lang.String#toLowerCase`(로케일 의존 오버로드 때문에
    `forbidden`)와, `java.util.Locale` 이 T-C 허용 목록에 없는 것. **그래서 멤버를 재분류하는
    것만으로는 열리지 않는다** — `Locale.ROOT` 필드 접근이 남는다.

    **부분 대안만 있다**: `equals(ignoreCase = true)` 는 통과하므로 *비교*는 되지만
    **정규화 키**(map key · 중복 제거 · canonical form)를 만드는 경로가 없다. 이 프로젝트에서
    닿을 자리가 실재한다 — `capability-map.md` 의 `settlement` 「상호 정규화 토큰」과 1C 의
    별칭·포괄 코드가 같은 성질이다.

    **1A 가 고치지 않는다.** 방향이 안전한 차단이고(새는 쪽이 아니다) 도메인 코드가 없는
    상태에서 정할 판단이 아니다. 필요해지면 `ADR 0007` D-4 의 allowlist 형식으로 **둘을 함께**
    연다 — `class.allowed` 에 `java.util.Locale` 을 더하고 `String#toLowerCase` 를 사유와 함께
    재분류한다. 「허용 목록의 과잉 도달」(`java.net.URI`)과 같은 가족이되, 그쪽과 달리 **효과
    분류 층이 함께 관여**한다는 점이 다르다.

    **이 항목이 있는 이유**: 제한 18 이 `String.format` 이 **통과**하는 쪽을 기록된 판단으로
    등재했는데 **차단**되는 쪽에는 등재가 없었다. 그 비대칭이 있으면 1B 가 `lowercase()` 에
    막혔을 때 게이트 결함으로 오해할 근거가 문서에 없다.

26. **원산지 앵커는 `SourceFile` 이다 — 위조 가능한 애노테이션을 버렸다.** 앞선 판은
    `kotlin.Metadata` 보유를 앵커로 썼는데 **소스에 한 줄로 붙는다**(Codex 8차가
    `@kotlin.Metadata` 를 단 Java 로 통과시켰다). 지금 판정은 class 의 `SourceFile` 이
    **컴파일러가 실제로 먹은 소스의 이름 집합**에 드는가이며, 두 원산지 게이트가 같은 판정을
    쓴다. `E-55` 가 그 성질을 고정한다 — 확장자가 `.kt` 인 **진짜 Kotlin 산출물**이라도 그
    소스가 게이트를 통과하지 않았으면 걸린다.

    **잔여 — `SourceFile` 은 경로가 아니라 이름이다.** 게이트를 통과한 소스와 **같은 이름**의
    `.kt` 를 다른 디렉터리에서 컴파일해 신뢰 디렉터리에 넣으면 대조를 통과한다. 이름 충돌을
    요구하므로 좁지만 **0 이 아니다**. 경로까지 담는 속성이 class 파일에 없어 이 층으로는
    닫지 못한다.

    **앞서 이 자리에 적었던 `importJar` 관측은 재현되지 않았다**(1 회 관측 · 재현 실패, 원인
    미규명). 그 관측은 어떤 설계 판단의 근거도 아니다 — ArchUnit 은 이제 이 층에서 쓰이지
    않는다.

27. **게이트 위협 모델의 경계 밖은 제한이 아니라 경계다.** `-x` 로 게이트 제외 ·
    `enabled = false` · `build-logic/**` 편집 · outputs 를 선언하지 않고 산출물 디렉터리에 쓰는
    task 는 **막지 않기로 결정된 것**이며 미비가 아니다. 정본은 `milestone-1.md` 「게이트 위협
    모델」이고 근거는 `ADR 0007` §1.1.2 다. 같은 계열의 finding 은 수정 대상이 아니라 **경계
    참조로 답한다.**

28. **도구 입력을 넓히지 않고 컴파일 대상을 좁혔다.** ktlint 는 source set 기반 입력만 받고
    임의 파일 집합을 받는 자리가 없다 — 그래서 8차의 「도구마다 입력에 컴파일러 소스를 더한다」
    배선은 **ktlint 를 빠뜨린 채로만 성립했고 그것이 9차 finding** 이었다. 지금은 반대 방향이다:
    `compilerSourceContainmentGate` 가 **컴파일 대상이 source set 안에 갇혀 있기를** 요구하므로
    source set 을 보는 도구 전부가 자동으로 같은 집합을 본다. 더하기 배선은 걷었다 — 남기면
    도구 수만큼 반복해야 하고, 무엇보다 **원산지 검사가 자기 인증**이 된다(신뢰 이름 집합을
    컴파일러가 먹은 목록에서 뽑으면 거기 들어온 파일은 정의상 통과한다).

    **남는 것**: 봉쇄 게이트는 `compileKotlin` 만 본다. 다른 컴파일 task 를 등록하는 경로는
    이 게이트가 아니라 **원산지 층**이 잡는다(`E-62`) — 두 층이 독립이라 하나가 뚫려도 다른
    하나가 선다.


29. **`include(...)` 로 컴파일 대상을 좁히는 경로는 이 층이 잡지 못한다.** `excludes` 가 비어
    있는지는 단언하지만 `includes` 는 KGP 가 Kotlin 확장자 패턴으로 채워 두므로 「비어 있음」이
    성립하지 않는다. 좁히는 방향은 실행 코드를 늘리지 않아 위험이 낮고, 관례 밖 파일을 **더하는**
    방향은 양방향 등식이 잡는다.

30. **generated source 는 관례에 없다 — 1B 가 KSP·kapt 를 도입하면 이 게이트가 막는다.** 1A 에
    그 도구가 없어 관례 집합에 넣지 않았다. 도입 시점에 게이트를 느슨하게 하려는 압력이 생기지
    않도록 **지금 적어 둔다** — 그때 할 일은 게이트를 끄는 것이 아니라 generated 경로를 관례에
    **명시적으로 더하는** 것이다.

31. **레이아웃 기준은 파일 시스템이고, 그 뒤에 남는 변형 수단은 도구 설정 파일뿐이다.**
    관례 디렉터리를 직접 걸어 기준을 만들므로 Gradle 객체의 필터가 기준을 흔들지 못한다.
    컴파일러가 관례 디렉터리 **밖**에서 소스를 읽는 경로는 `srcDir` 추가·컴파일 task 의
    `source(...)`·둘째 컴파일 task 셋뿐이고 각각 다른 단언이 잡는다(확인). 남는 것은
    `.editorconfig`·`config/quality/**` 같은 **도구 설정**인데 그것은 제한이 아니라
    **경계 밖**이다 — `milestone-1.md` 「게이트 위협 모델」과 `ADR 0007` §1.1.2.

32. **`gateExecutionGate` 의 policy 는 `app` class 만 열거한다 — `build-logic` 자체의 게이트
    판정 test 는 이 단언의 대상이 아니다.** `config/quality/gate-tests.properties` 의
    `gate.tests.app` 만 값을 갖고 다른 모듈 키는 없다. **비대칭이지만 결함이 아니다** — 이
    대상 선택은 위협 모델의 경계와 정합한다: `build-logic/**` 편집 자체가 이미 「방어하지
    않는다」의 경계 밖이므로(`milestone-1.md` 「게이트 위협 모델」·`ADR 0007` §1.1.2),
    `build-logic` 이 자기 판정 로직을 재는 test(`SourceSetLayoutGateTask` 등의 순수 함수
    테스트)는 `gateExecutionGate` 가 아니라 `build-logic` 자신의 `check`(`A-5`)가 언제나
    전건 실행한다 — 그 `check` 를 우회하려면 `build-logic/**` 를 편집해야 하고 그것은 이미
    별도로 경계 밖이다. `app` 만 열거한 이유는 **`app` 이 위반 fixture 를 실행하는 유일한
    층**이기 때문이다(D-3 조합 지점).

33. **미수식 참조는 소스 층이 보지 못한다(설계 검토 S-13).** default import·확장 함수·수신자
    추론으로 이름 없이 풀리는 참조(`println`·`readln`)는 소스에 부를 이름이 없어
    `domainSourceReferenceGate` 가 볼 수 없다. 바이트코드 층이 정본이고 `ConsoleIoLeak`
    fixture 가 그 증거다 — 소스 게이트를 걸어도 이 fixture 는 여전히 2 차만 잡는다.
34. **완전수식 참조의 FQN 판별은 대소문자 관례에 기댄다.** 소문자 세그먼트 뒤 첫 대문자
    세그먼트를 타입의 시작으로 읽는다(설계 검토 §4 단계 2·3). 알려진 결과 둘: ① **(정정,
    verifier r19 M-2)** 소문자 뿌리 뒤 대문자 멤버(`config.MAX`·`tree.Node()`)는 **뿌리가
    같은 파일에 선언된 이름(프로퍼티·파라미터·지역 변수·import 별칭)이면 이제 값 체인으로
    건너뛴다** — 오탐이 아니다(`SourceReferences.locallyDeclaredNames`). 남는 오탐은 뿌리가
    **같은 패키지의 다른 파일**에 선언된 경우뿐이다(이 게이트는 파일 단위로만 훑는다).
    그 잔여 자리에서는 원래 적었던 회피책(「import 로 바꾸거나 이름을 바꾼다」)이 형태에 따라
    갈린다 — `config.MAX` 처럼 **타입 자리를 가리키는 것처럼 보이는** 참조는 import 로 벗어날
    길이 있지만, `tree.Node()` 처럼 **인스턴스를 경유하는 멤버 접근**은 가져올 타입이 없어
    import 로 벗어날 수 없다(제한 45 참고) — 그 경우의 조치는 이름을 바꾸거나 뿌리 선언을
    같은 파일로 옮기는 것뿐이다. 방향은 여전히 닫히는 쪽이다. ② 대문자로 시작하는 패키지
    세그먼트를 쓰는 외부 좌표는 접두에서 잘리지만, 잘린 접두가 실제 허용인 경우는
    `kotlin`·`java.math`·`bidvector` 뿐이고 그 아래는 이미 전면 허용이라 누출은 없다.
35. **대상은 domain `main` Kotlin 소스뿐이다(설계 검토 S-17).** `test` 소스와 `.kts` 빌드
    스크립트는 이 게이트의 대상이 아니다 — test 산출물은 배포되지 않고 `jarContentGate` 가
    그것을 든다, `.kts` 는 Gradle 타입을 정당하게 부르는 별도 표면이라 `sizeGate` 가 따로 잰다.
36. **generated source 를 들이면 이 게이트의 입력에도 더해야 한다.** `sourceRoot` 가 관례
    디렉터리(`src/main/kotlin`)를 직접 걷는다 — 알려진 제한 30(레이아웃 게이트의 같은 성질)과
    짝이다. 1B 가 KSP·kapt 를 들이면 관례 디렉터리 목록과 함께 이 task 의 `sourceRoot` 도
    갱신한다.

### raw `Double` public API 게이트 (`domainApiTypeGate`, 운영자 결정 2026-09-04)

`milestone-1.md` 「완료 조건」이 요구하는 축을 1A 가 강제 장치로 세운다 — 내용(값 타입)의
소유자는 1B, 게이트의 소유자는 1A(설계 검토 부록 「요구의 귀속」).

37. **문면보다 넓다.** 승인 문면은 「금액/rate」 한정이지만 게이트는 의미를 판별할 수 없다 —
    이름 규약은 `ADR 0002` A-1 이 이미 불채택했다. 그래서 넷을 넓혔다(운영자 결정
    2026-09-04): ① 「금액/rate」 한정 → **타입 전체 금지** ② `Float`·`DoubleArray`·
    `FloatArray` 포함(같은 취지의 우회로) ③ `Number` 포함(값 크기로 단위를 추측하는 표면)
    ④ **public 선언의 타입 명시 요구**(신설) — ①을 닫는 필요조건이다, `val rate = 0.5`
    는 소스에 `Double` 이라는 이름이 없어 PSI 만으로는 못 본다.
38. **타입 이름을 재지 의미를 재지 않는다.** `fun rate(): Any`·`fun rate(): String` 은 잡히지
    않는다 — 리뷰 항목으로 남긴다.
39. **`@PublishedApi internal` + `public inline fun` 은 대상이 아니다.** 바이너리 표면에는
    오르지만 공개 시그니처에 `Double` 이라는 이름이 없다. 해석 없이는 본문의 참조가 어느
    선언인지 모른다.
40. **`context` 파라미터·`expect`/`actual` 은 표면에 없다.** 이 저장소에 없는 형태다 — 1B 가
    들이면 타입 표면 순회에 더한다.
41. **별칭 해석은 파일 단위이고, `typealias` 는 해석 대신 선언 자리에서 잡힌다.** import
    별칭(`import kotlin.Double as Scalar`)은 원 FQN 으로 해석해서 대조하지만, `typealias
    Amount = Double` 뒤 `fun f(): Amount` 는 별칭을 풀지 않는다 — `typealias` 선언 그 자체의
    우변이 걸린다(D-5). 모듈 밖에서 들여온 public typealias 는 그 선언 모듈에서 잡힌다.
42. **`typealias` 우변과 별칭 import 를 한 파일에서 함께 쓰면, 그 별칭으로 리터럴 값을
    구성하는 자리가 컴파일되지 않을 수 있다(Kotlin 컴파일러 실측, 원인 미규명).**
    `import kotlin.Double as Scalar` 가 있는 파일에서 `typealias Amount = Double` 뒤
    `fun f(): Amount = 0.0` 를 쓰면 `kotlin.Double` 대 `java.lang.Double` 플랫폼 타입
    불일치로 컴파일러가 거부한다(`app:compileTestKotlin` 실측) — 두 선언을 각각 다른
    파일에 두면 문제가 없다. 1B 가 값 타입에 별칭·typealias 를 함께 쓸 계획이면 이 조합을
    피하거나 재현 조건을 먼저 좁힌다.
43. **오늘 domain main 에 public 선언이 0 이라 task 단언은 실질적으로 비어 있다.** 유일한
    선언 `ModuleBoundaryAnchor` 가 `internal` 이다. `publicDeclarations > 0` 을 task 에
    걸면 안 된다(경계 앵커 설계를 흔든다) — liveness 는 `DomainApiTypeFixtureTest` 가
    fixture 로 고정한다. 1B 의 첫 public 선언부터 이 게이트가 실효한다.
44. **ktlint 증분 캐시가 `clean` 없는 `check` 재실행에서 삭제된 파일을 잘못 실패시킬 수
    있다(verifier r18 F-4, 도구 거동).** 위반 fixture 를 domain 모듈에 심었다가 지운 뒤
    `clean` 을 건너뛰고 `check` 만 다시 돌리면 `ktlintMainSourceSetCheck` 가 삭제된
    파일의 캐시된 리포트로 실패를 낼 수 있다. `--no-build-cache clean check`(이 slice의
    acceptance 정본)는 매번 정상 종료했다 — ktlint-gradle 의 증분 판정 범위 문제이지 이
    slice 의 게이트 결함이 아니다. task 수준 실측을 `clean` 없이 재실행할 때는 이 거동을
    감안한다.

### 세그먼트 판별을 글자 모양에서 실재로 (verifier r19 M-1·M-2·M-3)

45. **JDK 이름공간의 중첩 클래스 판별은 `Class.forName` 이 서는 Gradle JVM 의 JDK/stdlib
    판을 기준으로 한다.** `java.`·`javax.`·`jdk.`·`kotlin.` 뿌리의 완전수식 참조에서 뿌리
    다음 세그먼트를 잇는지는 그 후보 클래스가 **실제로 로드되는가**로 정한다 —
    글자 모양(SCREAMING_CASE) 규칙을 버렸다(제한 7·11 정정과 같은 계보: 판별을 실측 가능한
    사실로 옮긴다). `memberEffectGate` 의 T-D 도출이 이미 같은 성질의 의존을 갖는다(효과
    표면 도출도 이 JVM 이 로드하는 JDK 클래스를 기준으로 삼는다) — 이 게이트가 새로 만든
    의존이 아니라 저장소가 이미 받아들인 것과 같은 축이다. 툴체인이 바뀌어 JDK 판이 달라지면
    (예: 새 JDK 버전에서 어떤 상수가 실제로 중첩 클래스로 재구현되는 경우) 이 판별도 함께
    달라질 수 있다 — 실질 위험은 낮다(JDK 가 상수를 클래스로 바꾸는 것은 바이너리 호환성을
    깨는 변경이라 실제로 일어나지 않는다).
46. **비 JDK 이름공간은 존재를 확인할 수 없어 닫히는 방향을 유지한다.** `bidvector` 를 포함한
    비 JDK 루트의 완전수식 참조는 대문자 세그먼트를 전부 타입 체인으로 잇는 예전 규칙 그대로다
    — 뿌리 클래스 컴파일 여부와 무관하게 모든 `$` 확장이 허용 목록에 있어야 통과한다. JDK
    판별과 다른 판정 경로를 쓰는 것이 의도한 비대칭이다(§1 「한 자리 규율」과 별개로, 실재
    판별 수단이 있는 이름공간과 없는 이름공간을 다르게 다룬다).
47. **(정정, verifier r21 H-1') 소문자 뿌리의 지역 선언 판별은 조상 사슬 + 「선언 끝」 위치
    조건이다 — 같은 패키지 다른 파일의 top-level 선언, 같은 파일 안의 무관한 형제 함수·클래스
    선언, 구조 분해 선언(위치 무관), `it` 무명 람다 파라미터는 못 본다.** verifier r19 M-2 가
    처음 쓴 파일 전체 이름 집합은 **미탐**을 냈다 — 다른 함수의 동명 지역 변수(`val java = 1`)
    하나가 그 파일 전체의 완전수식 참조를 지웠다(Codex 14차 #1). 조상 사슬로 좁힌 첫 정정은
    그 형태를 닫았지만 **같은 블록 안에서는 여전히 위치를 가리지 않아**, 참조 **뒤쪽**에 둔
    동명 선언도 가리는 것으로 오판하는 같은 계열의 미탐이 남았다(verifier r20 H-1). 위치 비교를
    `declaration.startOffset < reference.startOffset` 으로 좁힌 두 번째 정정은 그 형태를
    닫았지만, 참조가 **가리는 선언 자신의 초기화식 안**(`val java = java.net.HttpURLConnection.…`)
    에 있으면 `val` 키워드가 참조보다 앞서므로 여전히 가려지는 것으로 오판했다(verifier r21
    H-1' — Kotlin 은 초기화식 안에서 그 지역 변수를 아직 보지 않으므로 컴파일도 실제로 된다).
    `SourceReferences.visibleLocalNames`(`KtElement` 확장)가 이제 감싸는 블록의 형제 선언에
    한해 `declaration.endOffset <= reference.startOffset`(선언 서브트리 전체가 참조보다 먼저
    끝나야 한다)을 요구한다 — 함수·람다·보조 생성자·접근자 파라미터, 감싸는 클래스 프로퍼티·주
    생성자 파라미터, 파일 top-level 선언은 Kotlin 자체가 위치 무관이므로 그대로 둔다.
    `tree.Node()` 형태에서 `tree` 가 **감싸는 함수·람다·클래스가 아닌 자리**(다른 함수·다른
    파일의 top-level 선언)에 있거나, **같은 블록에서 참조보다 늦게 끝나는 선언**(뒤쪽 선언·
    자기 초기화식 포함)이면 그 이름은 값 체인으로 보이지 않아 여전히 오탐(닫히는 방향)일 수
    있다 — 회피책은 제한 34①을 따른다(같은 파일의 감싸는 스코프로, 참조보다 먼저 끝나도록
    선언을 옮기거나 이름을 바꾼다). **구조 분해 선언**(`val (java, other) = …`)과 **`it` 무명
    람다 파라미터**는 위치나 형태와 무관하게 이 판별에 아예 들어오지 않는다 — 둘 다 같은
    방향(닫힘)이라 게이트 술어의 원칙에 어긋나지 않는다(경계 전체 표는 `commands.md` 의 「verifier
    r21 H-1'」 절에 있다). 반대 방향(실제로는 다른 스코프의 이름인데 우연히 겹쳐 놓친다)의
    위험은 조상 사슬 축소 이전보다 줄었다 — 무관한 형제 선언·뒤쪽 선언·자기 초기화식은 더 이상
    섞이지 않는다.

## 1B 인계 목록

게이트가 아니라 **설계 결정**이거나 도메인 코드가 있어야 판정되는 것들이다. 설계 검토 §4.2 가
같은 목록을 낸다 — 여기는 그 포인터이고 판단 근거는 각 항목의 알려진 제한이 갖는다.

| 항목 | 왜 1B 인가 |
| --- | --- |
| 비결정성 정책(시계 port 주입) | 게이트가 아니라 설계 결정. **난수 둘은 도출이 이미 잡는다** — 남는 것은 T-B 의 `java.time` 이고, 정하면 도출의 입력에 그 층을 더해 새 후보를 분류한다(알려진 제한 17 이 정본) |
| coverage 임계 | 도메인 코드 없이 정한 수치는 근거가 없다 |
| `java.net.URI` 등 허용 확장 | 실제로 필요해지는 slice 의 판단. `ADR 0007` D-4 형식으로 연다 |
| mutation 도구 | `OPEN-ADR-07` |
| 모듈 **내부** 패키지 순환 | 모듈마다 패키지가 하나뿐이라 시험할 자리가 없다 |
| `class.allowed` 의 실사용 확장 | 도메인이 실제로 필요로 하는 타입이 생길 때 사유와 함께 더한다. **이 설계가 의도한 정상 운용이지 결함이 아니다** |

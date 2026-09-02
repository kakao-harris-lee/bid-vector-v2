# checklist — M1 / 1A

## M1 완료 조건 대조 (`milestone-1.md:77`~`:85`)

`milestone-1.md` 의 완료 조건은 **M1 전체**의 것이다. 1A 가 담당하는 것과 1B~1E 로 넘어가는
것을 나눈다 — 1A 가 남의 조건까지 「충족」이라 적으면 그 서술이 곧 거짓이 된다.

| 좌표 | 조건 | 1A 판정 | 근거 |
| --- | --- | --- | --- |
| `:79` | `./gradlew check` 통과 | **충족** | `commands.md` `A-1` |
| `:80` | 금지 import·순환 의존을 일부러 넣은 fixture 가 **실제로 실패** | **충족** | `commands.md` RED → GREEN. 다섯 위반을 심었고(금지 import · 역방향 의존 · 업무 모듈 직접 참조 · 패키지 순환 · 기술 계층 패키지명) fixture 가 없던 시점에 같은 단언이 **전부 실패**했다 |
| `:81` | 승인된 authoritative corpus 전체 통과 | **pending — 1B~1E** | 1A 는 corpus 를 소비하지 않는다. `golden-manifest.json` 이 없는 이유이기도 하다 |
| `:82` | 중요 rule mutation 이 생존하지 않음 | **pending — 1B~1E** | 도구가 `OPEN-ADR-07` 로 미결. 카탈로그에 pitest 좌표만 등재하고 적용하지 않았다 |
| `:83` | raw `Double` 금액/rate 가 public domain API 에 없음 | **pending — 1B**. 1A 는 **게이트가 표현 가능한지**까지 | ArchUnit 이 시그니처 타입을 볼 수 있으므로 1B 가 값 타입을 넣는 시점에 규칙으로 표현 가능하다. **1A 는 그 규칙을 쓰지 않았다** — 지킬 대상이 없는 규칙은 빈 집합 위에서 통과하고, 그것이 이 slice 가 피한 형태다 |
| `:84` | `Uncertain`/`Unmeasurable` 이 성공이나 0 으로 합쳐지지 않음 | **pending — 1C·1D** | 도메인 타입이 없다 |
| `:85` | 신규 파일/함수 예산 위반 없음 | **부분 — 정의가 부재하다** | 아래 「예산」 |

### `:85` 「예산」 — 1A 가 수치를 정하지 않은 이유

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
| 구현 diff 가 커밋되어 base/head 고정 | `git status --porcelain -- <in_scope 경로>` 가 비어 있어야 한다 — 판정은 verifier 레인이 재실행한다. **이 명령만으로는 부족하다**: ignored 파일은 여기서 비어 보이고 그것이 B-1 의 실물이었다. `commands.md` 의 `A-0`(커밋만 있는 worktree 에서 빌드)과 `A-0b`(ignore 에 걸린 소스·디스크와 커밋의 집합 차이)가 그 틈을 맡는다 |
| `acceptance_commands` 전부 exit 0 | `commands.md` `A-1`~`A-4` |
| test/lint/type/architecture 통과 | `A-1` 이 전부를 든다 |
| 변경된 fixture 와 정책 version 의 근거 | 정책 데이터 둘 다 `policy.version=1` 이고 값의 출처를 파일 주석이 든다. **1A 가 값을 고른 것은 없다** — `v2-지침서.md` §5:305 의 둘을 옮겼을 뿐이다 |
| 알려진 제한과 rollback | 아래 「알려진 제한」 · `rollback.md` |
| 비밀값 스캔 | `commands.md` 의 비밀값 스캔 절 |

## 알려진 제한

1. **coverage 임계가 없다.** Kover 는 배선돼 리포트를 내지만 검증 규칙이 없다. 승인 문서에
   수치가 부재하고(`milestone-1.md:22` 가 `coverage` 를 이름으로만 든다) **도메인 코드가 없는
   상태에서 정한 수치는 근거가 없다.** `check` 는 coverage 로 실패하지 않는다 — **1B 가 첫
   도메인 코드와 함께 정한다.**
2. **`bidding` 모듈이 없다.** `ADR 0006` D-2 목록과 어긋나며 `OPEN-ADR-14` 가 그 갈림을 든다.
3. **ArchUnit 은 바이트코드를 본다.** Kotlin `internal`·확장 함수·top-level 함수의 형태 규칙을
   표현하지 못한다. 그래서 1 차 강제를 **빌드 의존 선언**에 뒀고(`E-4` 가 그 층이 단독으로
   잡는 것을 보인다) `qualityBaseline` 의 public API 수도 같은 이유로 `internal` 을 public 으로 센다.
4. **detekt 2.0 은 alpha 다.** 규칙과 설정 키가 정식 출시 전에 바뀔 수 있다. 승인된 두 임계 중
   파일 축은 detekt 밖(`sizeGate`)에 있어 detekt 이 죽어도 남는다.
5. **모듈 안의 패키지 순환은 아직 시험되지 않았다.** 규칙(`bidvector.(**)` slice)은 걸려 있으나
   각 모듈에 패키지가 하나뿐이라 모듈 **내부** 순환을 만들 자리가 없다. 위반 fixture 의 순환은
   모듈 사이다. 1B 가 패키지를 늘리면 그때 안쪽에서도 구속력이 생긴다.
6. **CI 워크플로가 실행된 적 없다.** 원격에 push 하지 않았으므로 `.github/workflows/ci.yml` 은
   **문법만 갖춘 골격**이고 GitHub 러너에서 도는 것을 확인하지 못했다.

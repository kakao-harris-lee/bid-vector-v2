# M6/G1-DEC10 — 실행 명령과 종료 코드

acceptance(`./gradlew --no-daemon check`)는 Phase 4 에 돈다. 이 절은 Phase 1(설계) 의 실측이다.
**마지막 HEAD 의 게이트 결과 정본은 verifier 와 PR 조치 코멘트**다(evidence 는 자기 마지막
커밋의 post-state 를 담을 수 없다).

## 2026-10-28T03:39+09:00 — rollback 전제 단언 (base 고정)

- cmd: `git merge-base --is-ancestor <착수 커밋> 23837b19`
- exit: 1
- 핵심 결과: base 가 이 slice 의 착수 커밋을 담지 않는다 — 공유 문서 복원 전제 성립.

## 2026-10-28T03:39+09:00 — base 가 HEAD 의 조상인지

- cmd: `git merge-base --is-ancestor 23837b19 HEAD`
- exit: 0
- 핵심 결과: 조상이다.

## 2026-10-28T03:50+09:00 — main 클래스 컴파일 (코드 지도 측정의 전건)

- cmd: `~/.local/bin/bv-build.sh ./gradlew --no-daemon classes`
- exit: 0
- 핵심 결과: `BUILD SUCCESSFUL`, 34 actionable tasks(15 executed · 19 from cache).

## 2026-10-28T03:52+09:00 — 코드 지도 census (바이트코드)

- cmd: `dec10-census.sh <repo> <out>` — 모듈별 main 클래스를 `javap -p -c` 로 떠서 축 ①(소유자가
  `FloorRate` 인 멤버 접근) · 축 ②(반환·필드 타입이 `FloorRate` 인 획득) · 참고 축(`FloorRateOrigin`)을
  센다. 스크립트는 scratchpad(산출물 아님).
- exit: 0
- 핵심 결과: 축 ① 17 참조/15 호출자 · 축 ② 17 참조/16 호출자 · origin 21, main 클래스 13 디렉터리
  (전체 참조 84,365)에서. 자리별 목록은 `design.md` §0.

## 2026-10-28T03:48+09:00 — census 측정 도구의 양성 대조

- cmd: 같은 스크립트를 **다른 레인의 컴파일된 트리**(7C④ clone)의 `workflow` 모듈에 적용
- exit: 0
- 핵심 결과: 축 ①·② 각 1 자리(`workflow.evaluation.PredictionFactsKt` → `FloorRate.getRate` ·
  `Notice.getFloorRate`)로 **잡힌다** — 도구가 빈 집합을 내는 상태가 아님을 확인했다. 같은
  스크립트가 그 트리의 `decision` 모듈에서는 0 을 냈고, 그것은 그 base 에 DEC-05 메뉴 커널이
  없기 때문이다(측정 대상이 아니라 대조용 트리다).

## 2026-10-28 — 구현 라운드

### RED 선행 (게이트 커널)

- cmd: `./gradlew --no-daemon :decision:test --tests '…PublishedFloorTrustTest'` (골격 — 게이트가
  전부 부재를 내고 출하 밴드가 `[0, 1]` 인 상태)
- exit: 1
- 핵심 결과: 12 중 **10 거동 실패**(컴파일 오류가 아니다). 구현 뒤 같은 명령 exit 0.

### acceptance — `check` 전건

- cmd: `./gradlew --no-daemon check` (CI `check` job 의 명령 그대로) @ **`78c462ef`**(r1 판정 SHA 의
  마지막 산출물 커밋; r1 verifier 가 판정 SHA `a6a4eddc` 에서 같은 명령을 직접 재실측해 exit 0)
- exit: 0
- 핵심 결과: `BUILD SUCCESSFUL in 25m 13s`, 392 actionable tasks.
- 앞선 실패 둘(둘 다 **sizeGate**, 수정 후 위 초록): `app/build.gradle.kts` 람다 56줄 ·
  `EvaluateCandidatesUseCase.analyzeAndJudge` 51줄. 실패 이력을 남긴다 — 그 게이트가 실제로 돌았다는 증거다.
- `web`·`ml-engine`·`container` job 은 입력 변경 0 이라 생략한다(`openapi/`·`web/`·`ml-engine/`·
  `docker/`·`.github/` 경로 변경 없음 — `rollback.md` 의 복원 목록이 그 사실의 기계 산출이다).

### 변이 — 바꿔치우기, 표적 test `--rerun`

통제군은 **같은 HEAD 의 전건 `check` 초록**이다. 적용 실패 시 비영 종료로 멈추는 스크립트로 돌렸고,
여섯 전부 **의도한 test** 에서 RED 다(컴파일 오류 0 — 각 로그에 `^e:` 없음).

| 변이 | 바꾼 것 | 표적 | exit |
| --- | --- | --- | --- |
| M1 | 게이트가 밴드를 보지 않고 전부 통과 | `PublishedFloorTrustTest`(12 중 5) | 1 |
| M2 | 통과 값의 `copy` 를 모듈 밖으로 연다 | `FloorTrustCompileFailureHarnessTest` (fixture `6b`) | 1 |
| M3 | margin 이 개연 불가·심판 아님에 **원값을 쓴다** | `PredictionFactsFloorTrustTest` (9 중 3) | 1 |
| M4 | 집계 줄에서 사유 필드를 뺀다 | `EvaluationCommitExitCodeTest` (8 중 3) | 1 |
| M5 | ⓑ 허용 목록에서 한 쌍을 **뺀다** | `FloorTrustGateTest` ⓑ | 1 |
| M6 | ⓑ 허용 목록에 없는 쌍을 **더한다** | `FloorTrustGateTest` ⓑ | 1 |

M5·M6 이 등식의 **양방향**이다(누락 · 낡은 항목). M3 에서 `1.00000` test 가 초록으로 남는 이유는
`checklist.md` 알려진 제한 ⑧ 이 적는다. 변이 뒤 작업 트리 잔여 0.

**M1 표적 정정(r1 v M-2)** — 앞 판 표는 `BidMenuFloorTrustTest` 도 M1 의 표적으로 적었는데,
그 test 는 실패 판정을 **직접 주입**해서 게이트 변이로는 RED 가 될 수 없다(verifier 재실행에서
5/5 초록). r2 가 그 자리에 **종단 test 둘**(밴드 밖 원값 → 게이트 → 사유)을 더했고, 그 둘이
변이 M1 의 표적이다.

### 비밀값 스캔

- cmd: `grep -rniE -f config/quality/leak-patterns.txt <in_scope 경로들> reports/evidence/m6/g1dec10/`
- exit: 1 (= 매치 없음 = 통과)

### 낡는 좌표 — 역방향 파급 실측

- cmd: 편집한 파일의 축약형 역참조를 세고(`grep -rnoE '<stem>\.md:[0-9]+'`) 삽입 줄 번호와 대조
- exit: 0
- 핵심 결과: `capability-map.md` 는 1734 뒤에 15줄이 들어가 그 아래 역참조 **22 건(11 파일)** 이 +15
  밀린다 — 전부 다른 slice 의 evidence 라 in_scope 밖이고 `checklist.md` 알려진 제한 ⑤ 로 등재한다.
  `milestone-6.md`·`acceptance-trace.md` 는 역참조 0 건, 두 `policy-values.md` 와
  `CollectionPolicy.kt` 는 줄 수 불변(같은 줄 치환) 또는 역참조 0 건이다.

### clean-tree 판정 (양성 대조 포함)

- cmd: `git status --porcelain -- <in_scope 개별 인자>` → 미추적 evidence 한 건 외 없음
- 양성 대조: in_scope 파일 하나에 줄을 심어 `M` 으로 **잡히는 것**을 확인하고 **비파괴 절삭**
  (`head -n <원래 줄수>`)으로 되돌렸다 — `checkout --` 는 쓰지 않는다(미커밋 편집을 함께 지운다).

## 2026-10-28 — r1 수정 라운드 (r2)

판정 SHA `a6a4eddc` 에 대한 verifier not-ready(high 1) · code-reviewer request_changes(high 1) 를
한 라운드로 고쳤다. finding 별 커밋이다(r2 의 마지막 산출물 커밋은 등재 장부 커밋 `43344b8f` 였다 —
`rollback.md` 가 그 SHA 로 실측했다).

### 새 게이트 축의 변이 — 전부 바꿔치우기, 표적 test `--rerun`

| 변이 | 바꾼 것 | 표적 | exit |
| --- | --- | --- | --- |
| V1 | production `Notice` 에 **파생 접근자**(원값에서 숫자만 꺼낸다) | `FloorTrustGateTest` ⓑ′ | 1 |
| V2 | production `workflow.evaluation` 에 **운반 클래스**(인자로 받은 원값의 멤버를 읽는다) | `FloorTrustGateTest` ⓑ′ | 1 |
| V8 | 게이트 파일에 **둘째 생성 함수**(밴드를 보지 않는다) | `FloorTrustGateTest` ⓐ′ | 1 |
| M7 | 계수 사슬 hop 3 을 `null` 로(`outcome.floorExclusion` → `null`) | `FloorExclusionChainTest` | 1 |
| M8 | 최소 점수 드롭에서 계수를 다시 버린다 | `FloorExclusionChainTest` | 1 |
| M9 | D-DEC10-12 되돌림(`>1` 전용 가지 제거) | `PredictionFactsFloorTrustTest` · `PredictionFactsTest` | 1 |

여섯 전부 **의도한 test** 에서 RED 다. V1 은 **첫 적용이 구문 오류**로 떨어져(데이터 클래스
생성자 인자 목록에 property 를 넣었다) 그 판정을 버리고 클래스 **본문**에 다시 심어 재측정했다 —
그때 관측 집합이 `(procurement.Notice, getRate)` **하나만** 늘었다(의도한 단언). 적용 실패를
비영 종료로 멈추는 스크립트였기에 그 오류가 RED 로 오인되지 않았다.

### S-20 (`tools/one-command-check.sh`) — r1 v L-2

- cmd: `./tools/one-command-check.sh` (CI `check` job 의 완료 조건 1 단계)
- exit: **0**
- 핵심 결과: `one-command-check: 완료 — Kotlin 전건 + Python 전건 + web 전건 통과` @ **`43344b8f`**
  (r2 의 마지막 산출물 커밋; 그 뒤 판정 SHA 까지의 diff 는 evidence 파일뿐이다).
- **첫 실행은 exit 1 이었다** — `gateRegistrationGate` 가 새 test 클래스(`FloorExclusionChainTest`)의
  **미등재**를 잡았다. 표적 test 만 돌린 라운드에서는 보이지 않는 자리다(등재하지 않으면 그 test 가
  지워져도 `check` 가 초록이다). 등재 커밋 뒤 위 초록. 실패 이력을 남긴다 — 그 게이트가 실제로 돌았다는 증거다.
- 이 한 명령이 `check` 전건을 품으므로 r2 의 acceptance 재실측은 이것으로 갈음한다(그 안의 첫 명령이
  `./gradlew --no-daemon check` 다).

### 비밀값 스캔 · clean-tree (r2)

- `grep -rniE -f config/quality/leak-patterns.txt <in_scope 경로들> reports/evidence/m6/g1dec10/` → exit 1(통과)
- `git status --porcelain -- <in_scope 개별 인자>` → evidence 편집 외 없음(양성 대조는 r1 에서 실측)

## 2026-10-28 — r2 수정 라운드 (r3)

판정 SHA `be25c1ed` 에 대한 verifier not-ready(high 1) · code-reviewer request_changes(high 1) 를
한 라운드로 고쳤다. **재작업 누계 3/5.**

### 변이 — 전부 바꿔치우기, 적용 직전 clean 단언, 표적 test `--rerun`

| 변이 | 바꾼 것 | 표적 | exit |
| --- | --- | --- | --- |
| N1 | 허용된 가격 경로 파일에 **확장 함수**(파생 접근자) | ⓑ·ⓑ′ | 1 |
| N2 | 같은 파일에 **람다 값** | ⓑ·ⓑ′ | 1 |
| N3 | **같은 이름 오버로드**가 밴드 검사 없이 통과 값 생성 | ⓐ′ | 1 |
| B1 | 같은 파일 둘째 함수가 **위조 밴드**를 진짜 판정 함수에 넘긴다 | ⓒ | 1 |
| T1 | 집계를 다시 `reached` 만 세게 되돌린다 | `FloorExclusionTallyTest` | 1 |
| T2 | 사슬 hop 1 을 `null` 로 | `OpportunityAnalysisTest` | 1 |
| T3 | `>1` 가드를 다시 `Implausible` 전용으로 | `PredictionFactsFloorTrustTest` | 1 |

일곱 전부 **의도한 test** 에서 RED 이고 컴파일 오류 0 이다. **N2 가 RED 인 것이 측정 하나**다 —
람다의 합성 메서드가 제 코드 단위로 보이므로 그 꼴을 따로 닫을 필요가 없었다(처방의 「안 잡히면
따로 닫거나 제한으로」는 해당하지 않는다).

### S-20 (r3) — 전건 재실측

- cmd: `./tools/one-command-check.sh`
- exit: **0** @ `f21083a6` — `one-command-check: 완료 — Kotlin 전건 + Python 전건 + web 전건 통과`.
- **첫 실행은 또 exit 1 이었다** — `gateRegistrationGate` 가 새 app test 클래스(`FloorExclusionTallyTest`)의
  미등재를 잡았다. r2 와 **같은 자리를 두 라운드 연속** 놓쳤다(표적 test 만 돌리면 그 게이트가 돌지
  않는다). 재발 방지는 레인 memory 에 남겼다 — 새 test 클래스는 만드는 커밋에서 같이 등재하고,
  그 게이트 하나만 돌려도 즉시 잡힌다.

## 2026-10-28 — r3 수정 라운드 (r4)

판정 SHA `a15bdd4a` 에 대한 verifier not-ready(high 2, 뿌리 하나) · code-reviewer **approve**
(medium 1 — 같은 뿌리)를 한 라운드로 고쳤다. **재작업 4/5**(라운드 수, D-DEC10-21).

### 허용 목록이 커진 값

자기 제외를 없애면서 컴파일러가 만드는 자기 멤버가 관측 집합에 들어온다 — 허용 키 **27 → 90**
(생성 3 · 획득 48 · 멤버 34 · 정책 5). 목록은 **실패 로그에서 파서로 생성**했다(`extract-sets.py`,
scratchpad) — 키가 서명을 담아 길어서 손으로 베끼면 한 글자에 등식이 깨진다. 전체 집합을 보려고
`kotest.assertions.collection.print.size` 를 **측정 동안만** 올렸고(기본 20 에서 잘린다) 그 편집은
커밋하지 않았다(측정 뒤 원복, `git status` 로 확인).

### 변이 — 자기 제외 우회 둘과 그 통제군

| 변이 | 바꾼 것 | r3 결과 | r4 결과 | 표적 |
| --- | --- | --- | --- | --- |
| e1 | 운반 타입에 `fun floorAny(): Any? = floorRate` + 캐스트 소비자가 게이트 없는 산술로 | **초록** | **1(RED)** | ⓑ |
| e1pos | 같은 소비자 + 캐스트 값의 `.rate` | 1 | 1 | ⓑ·ⓑ′ |
| c1 | 정책 타입 자신의 `widened()`·`withBand()` 로 위조 밴드를 진짜 게이트에 | **초록** | **1(RED)** | ⓒ |
| c1pos | 같은 파일에서 인라인 생성 | 1 | 1 | ⓒ |

넷 전부 **의도한 축**에서 RED 이고 컴파일 오류 0 이다. e1·c1 이 「초록 → RED」로 뒤집힌 것이 이
라운드의 측정이고, e1pos·c1pos 가 **그대로 RED** 인 것이 「소비자가 스캔 집합에 있다」는 통제군이다.

## 2026-10-28 — r4 사전 규칙 발동 뒤 (r5, 마지막 라운드)

verifier r4 H-1(가격 경로 ③ 의 진입이 날 율이라 **표현을 바꾼 세탁**이 census 밖) → 사전 규칙
발동 → **운영자 결정: 진입 타입을 게이트 산출물로**(D-DEC10-22). **재작업 5/5.**

### 선택의 근거가 된 실측 (레인 진단, scratchpad `wdec10-options.md`)

- ① 의 변경 범위: production **3 파일** · 공유 커널 편집 **없음** · ③ 의 production 생성 자리 **1**.
- ① 의 대가로 걱정한 것(4B-5 의 경계값 test 셋이 「구성 불가」)은 **실현되지 않았다** — 생성자를
  `internal` 로 닫고 공개 factory 를 두는 꼴을 택해 **모듈 안 test 11 자리가 전부 보존**된다
  (`MarginInputs(` 11 자리 그대로, `:decision:test` exit 0). `floorHeadroomOf` 의 분기와
  `init` 술어도 건드리지 않았다.

### 변이 — 세탁 두 꼴과 통제군, 진입 되돌림

| 변이 | r4 결과 | r5 결과 | 어디서 멈추는가 |
| --- | --- | --- | --- |
| `lnd` 세탁 둘(`toString()` 정규식 · 영속 행의 `BigDecimal`) | **초록** | **1** | **컴파일** — `Argument type mismatch: actual type is 'Rate', but 'PlausibleFloorRate?' was expected`(의도한 진단) |
| `lndpos` 같은 두 파일이 원값 직접 읽기(통제군) | 1 | 1 | ⓑ·ⓑ′ 등식 |
| `rev` 진입을 다시 공개 생성자로 되돌림 | — | 1 | 컴파일 실패 fixture `10`·`10b` |

`lnd` 가 **RED 가 아니라 컴파일 실패**인 것이 이 라운드의 요점이다 — 세탁의 **형태를 열거하지
않고** 마지막 걸음(「숫자를 ③ 의 입력으로 만든다」)을 타입으로 닫았다. `lndpos` 가 그대로 RED 인
것이 「두 파일이 스캔 집합 안」이라는 통제군이다.

**이 문면의 정정(아래 r5 승인 전 일괄)** — 「세탁 두 꼴이 **컴파일에서** 멈춘다」는 변이 `lnd`
에 대해서는 맞지만, 그것을 **잠그는 fixture** 쪽에서는 한 꼴만 참이었다. fixture `12` 가 날
`BigDecimal` 을 넘기는 꼴이어서 진입이 `Rate?` 였던 시절에도 컴파일되지 않았고, 그래서
D-DEC10-22 를 가르지 못했다(verifier r5 L-1 — 되돌림 변이에서 `11` 만 RED). `12` 를 영속 행의
`BigDecimal` 을 **율로 만들어** 넘기는 꼴로 바꿔 둘 다 그 결정을 가르게 했다.

### 측정 도구

ⓐ·ⓓ 집합도 실패 로그에서 파서로 생성했다(추출기에 축 둘을 더했다). 전체 집합을 보려고
kotest 출력 한도를 **측정 동안만** 올렸고 그 편집은 커밋하지 않았다(`git status` 로 확인).

### S-20 (r5) — 전건 재실측

- cmd: `./tools/one-command-check.sh` (CI `check` job 의 완료 조건 1 단계)
- exit: **0** @ `2a8f1389` — `one-command-check: 완료 — Kotlin 전건 + Python 전건 + web 전건 통과`.
- 이 줄 뒤의 evidence 커밋이 **마지막 HEAD** 다. 그 HEAD 의 게이트 결과 정본은 evidence 가 아니라
  verifier 와 PR 조치 코멘트다(`evidence-pack` 규격 — evidence 는 자기 마지막 커밋의 post-state 를
  담을 수 없다).

## 2026-10-28~29 — r5 이어지는 라운드 (D-DEC10-24 + CR r4 처분)

호스트가 2026-10-28 23:29(KST) 재부팅되어 **앞 레인이 죽었다**. 미커밋 편집 넷(경계 셋 처분
진행분)을 이어받아 같은 라운드로 마쳤다 — 새 라운드가 아니다(**재작업 누계 5/5 그대로**).
재부팅으로 **CR r4 리뷰 원문과 변이 하네스·로그가 든 scratchpad 가 사라졌다**. 처분은 팀장이
전달한 finding 목록(H-1 · M-1 · M-2 · M-3 · LOW 여섯)에 따랐고, 하네스 두 개는 **대화 기록에서
본문을 복원**해 새 scratchpad 에 두었다. 유실을 적는 것이 「리뷰를 축소했다」와 구별되는 자리다.

### D-DEC10-24 — 경계 셋의 처분 실측

- cmd: `./gradlew --no-daemon gateRegistrationGate :decision:test --rerun --tests '…ExpectedMarginDerivationTest'`
- exit: **0** @ `825a7fd0`
- 핵심 결과: 커널 **11 test**(0 실패) · 새 게이트 수준 **7 test**(0 실패 — 같은 실행의
  `:workflow:test`). 등재 게이트가 새 클래스를 함께 봤다(만드는 커밋에서 등재했다).
- 특례 분기(`floor.signum() == 0`)를 지운 근거는 도달 불가가 아니라 **등식**이다 — 같은 입력
  (`rec 0.70 · floor 0`)에서 `0.835` 를 「일반식 등식」 test 가 **계속** 잰다. 그 test 를 지우지 않고
  **주장을 바꿔** 남겼기 때문에 분기 제거가 일회 측정이 아니라 살아 있는 술어로 지켜진다.
  (앞 레인은 그 test 를 지우고 일회 측정으로 갈음하려 했다 — 이어받으며 되돌렸다.)

### CR r4 M-2 음성 대조 — 붉은 출력이 대칭 차집합을 이름까지 내는가

- cmd: `clue-mutation.sh`(scratchpad) — 허용 목록의 관측된 키 하나를 **bogus 키로 바꿔치우고**
  (더하기만 하는 변이가 아니다) `:app:test --rerun --tests '…FloorTrustGateTest'`. 한 변이가
  두 방향(허용 밖 관측 · 낡은 허용)을 함께 만든다. 적용 직전 clean 단언 포함.
- exit: **1(RED)** · 컴파일 오류 0(`^e:` 0건) · 표적은 **ⓓ 하나**
- 핵심 결과: 실패 메시지가 `허용 밖 관측 1` + 그 키 전문, 이어 `낡은 허용 1` + bogus 키를 낸다.
  앞 판은 같은 실패에서 집합이 **20 에서 잘려** 어느 키인지 출력에 없었다. 변이 뒤 트리 잔여 0.

### CR r4 M-1 — 생성기의 양성·음성

- cmd: 합성 로그(ⓑ′ 블록 하나, 항목 둘)와 **빈 로그**로 `tools/floor-trust-allowlist.py` 실행
- exit: **0**(두 키를 정렬해 냈다) · **2**(빈 로그)
- 핵심 결과: 「축이 초록이라 블록이 없다」와 「파서가 로그 형태를 못 읽는다」가 같은 빈 출력을
  내지 않는다 — 도구 자신의 양성·음성이 둘 다 선다.

### CR r4 H-1 — 앞 라운드 변이 표의 빠진 칸(명령·HEAD)

| 라운드 | 변이 | 하네스 | HEAD |
| --- | --- | --- | --- |
| r4 | e1 · e1pos · c1 · c1pos | `dec10-mutations-r4.sh` | `c9e56522` |
| r5 | lnd · lndpos · rev | `dec10-mutations-r5.sh` | `4b3c69e9` |

두 하네스 모두 적용 직전 `git status --porcelain` 이 비었는지 단언하고(아니면 `exit 73` 으로 멈춘다),
적용 실패를 비영 종료로 멈추며(`70`~`72`), 표적은 `--rerun` 으로 돈다. 표의 결과 칸은 그 라운드의
실측이고 **이 라운드가 재실행한 것이 아니다**. HEAD 는 기록의 실행 시각과 커밋 시각을 대조해
특정했다(복원한 스크립트 본문 · 실행 로그 판독 명령이 같은 기록에 있다).

### CR r4 M-3 · LOW 여섯의 자리

| finding | 자리 |
| --- | --- |
| M-3 `toString`·`equals`·`hashCode` 잔여 | `checklist.md` 알려진 제한 ② 의 「손 재정의가 남는 범위」 절(ⓑ 18 키 · ⓑ′ 12 키 · 진입 타입이 가격 경로를 막는다) |
| LOW 새 파일 표 | `checklist.md` 표를 하나로 합치고 라운드 칸 · r5 둘 등재 |
| LOW D-DEC10-20 인용 | `scope.md` 그 행의 문면 정정 + 게이트 test KDoc(ⓐ 는 자기 제외가 남는다) |
| LOW 변이 표 명령·HEAD | 위 표 |
| LOW `FloorRateGroundsState` 단정 | 제한 ⑦ — probe 근거를 밝히고 **단정이 아니라 인계 사항**으로 |
| LOW 음성 대조 test 이름 | 이름이 약속한 둘째 축 단언을 그 test 몸통에 넣었다 |
| LOW ⓐ 자기 제외 문면 | 게이트 test 클래스 KDoc(없앤 것은 `FloorTrustRules` 축들이다) |

### S-20 (r5 이어지는 라운드) — 전건 재실측

- cmd: `./tools/one-command-check.sh` (CI `check` job 의 완료 조건 1 단계)
- exit: **0** @ **`5099b8bb`**(이 라운드의 **마지막 산출물 커밋**)
- 핵심 결과: `one-command-check: 완료 — Kotlin 전건 + Python 전건 + web 전건 통과`
  (Kotlin `check` 는 `BUILD SUCCESSFUL in 26m 23s`). 이 줄 뒤의 evidence 커밋이 마지막 HEAD 이고,
  그 HEAD 의 게이트 결과 정본은 verifier 와 PR 조치 코멘트다.

### 비밀값 스캔 · clean-tree (r5 이어지는 라운드)

- `grep -rniE -f config/quality/leak-patterns.txt <in_scope 경로들> reports/evidence/m6/g1dec10/` → exit 1(통과)
- `git status --porcelain -- <in_scope 개별 인자>` → evidence 편집 외 없음(양성 대조는 r1 에서 실측)

## 2026-10-29 — r5 승인 전 일괄 (verifier ready-for-review · CR approve 뒤)

새 라운드가 아니다 — 두 리뷰가 통과한 뒤의 **장부층·LOW 일괄**이다(재작업 누계 **5/5 그대로**,
not-ready/request_changes 계수).

### 허용 목록의 키 단위 변화 (규율 ① — CR r5 M-3)

| 축 | 나간 키 | 들어온 키 | 왜 |
| --- | --- | --- | --- |
| ⓐ | `bidvector.workflow.evaluation.PredictionFactsKt->getRate` | `bidvector.decision.priority.derive.DerivationInputsKt->getRate` | D-DEC10-22 로 **`.rate` 추출이 factory 로 옮겨갔다** — ③ 진입이 통과 값을 그대로 받으므로 호출부(`PredictionFacts`)는 더 이상 율을 꺼내지 않는다. 총수 8 그대로(순증 0) |

제한 ⑫ 의 규율 ②(「추가된 키마다 자리를 적는다」)를 **이 라운드 자신의 사례**에 적용한
자리다 — ⓓ 축의 새 키 셋은 정책 파일 주석과 `design.md`·`scope.md` 가 이미 적었고, ⓐ 의
교체만 빠져 있었다. 집합 크기만 보면(8 → 8) 보이지 않는 변화다.

### 생성기 재작성의 실측 (CR r5 M-1 · L-2 · L-6)

- cmd: `python3 -I tools/floor-trust-allowlist.py <로그>` — 로그 다섯(실 clue 변이 로그 ·
  **절단된 clue**(선언 3 · 줄 2) · **블록 넘김**(초록 축 이름이 실패 축 앞에 나온다) · 없는 파일 ·
  전건 초록)
- exit: **0** (실 로그 — ⓓ 의 `+`/`-` 키를 냈다) · **3**(절단 — 선언과 줄 수 불일치) ·
  **0**(블록 넘김 — 초록 축은 「clue 없음」, 항목은 **실패 축에** 귀속) · **66**(없는 파일 —
  traceback 아님) · **2**(전건 초록)
- 핵심 결과: 앞 판은 kotest 의 `expected:<[…]>` 줄을 읽어 **한도 20 에서 잘린 부분집합**을
  조용히 냈다(exit 0). 지금은 clue 의 `+`/`-` 줄을 읽고 **선언된 개수와 대조**하므로 절단이
  비영 종료가 된다. 축 블록을 다음 축 제목까지로 잘라 귀속 오류도 닫았다.

### 표적 재검증 — 게이트 술어를 바꾼 커밋 (CLAUDE.md: severity 무관)

fixture 단언을 바꿨으므로 그 표적을 다시 쟀다.

- cmd: `./gradlew --no-daemon :decision:gateRegistrationGate :decision:test --rerun --tests
  '…FloorTrustCompileFailureHarnessTest' --tests '…ExpectedMarginDerivationTest'`
- exit: **0** @ `f116dd02` — 하네스 **16 test**(12 의 음성·오타 변이 둘이 늘었다) · 커널 11 test, 0 실패.

**R1 되돌림 변이(바꿔치우기) — 이 수정의 요점**

- cmd: `r1-mutation.sh`(scratchpad) — ③ 진입을 다시 `Rate?` 로 바꿔치우고(적용 직전 그 경로
  clean 단언) `:decision:test --rerun --tests '…FloorTrustCompileFailureHarnessTest'`
- exit: **1(RED)** — 16 중 **2 실패: fixture `11` 과 `12` 둘 다**.
- 핵심 결과: verifier r5 가 같은 변이에서 **11 만** RED 였다고 실측했고(12 는 초록 — 그 꼴이
  진입 타입과 무관하게 컴파일 불가였다), 지금은 **둘 다** D-DEC10-22 를 가른다. 변이 뒤 그
  경로의 작업 트리 잔여 0(evidence 편집만 남는다).


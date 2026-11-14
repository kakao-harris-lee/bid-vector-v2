# M12 / adapters-javap-unify — 열네 축의 상수 풀 술어를 한 자리로

- base: main `10d208d2664c6bb87cd7082a2853c08c2c0d3236`(M11 종결 머지) · 계약 정본 `milestone-12.md` 12A 행
- 공통 규율: M12 레인 공통 지시(팀장 2026-11-14) · `CLAUDE.md` · `v2-slice-pipeline` · `evidence-pack`
- 받는 항목: 계약 「받는 항목」 **1** — `OPEN-M11-JAVAP-ROOT-REGEX-UNANCHORED` 의 다섯(①~⑤).
  정의·실측·처방의 정본은 `reports/evidence/m11/gates-adapters-2/` 의 design-review·checklist·commands 다.
- 결정 지점은 추천안으로 진행(전역 규칙). 출하 코드 변경 · 외부 effect · Flyway · 새 게이트가 오늘의
  production 을 붉히는 경우는 멈추고 보고한다.
- **게이트 slice 다** — 구현 전에 `design-review.md`(Phase 2.5)를 쓰고 팀장 판정을 받는다. 이 문서는
  착수 실측과 계약만 싣는다.

## 착수 실측 — 다섯을 코드에서 다시 쟀다

측정 자리: 이 worktree 의 `:adapters:compileKotlin`·`:adapters:compileTestKotlin` 산출물. `javap` 는
호스트 JDK 21(`openjdk 21.0.13`). 측정 스크립트는 레인 scratch 에 있고 산출물이 아니다.

### ① 형제 열 축의 비경계 정규식 — 열 축 전부에서 오늘 그대로다

| 재는 것 | 값 |
| --- | --- |
| dependency test 축 | **14** |
| 비경계 정규식(`bidvector[/.]…`)을 **파일 `private` 로 복제**한 축 | **10** — `audit`·`evaluation`·`notice`·`notification`·`opening`·`persistence`·`profile`·`qualification`·`relay`·`strategy` |
| 공유 헬퍼(`SELF_COORDINATE`, 좌측 경계 고정)를 쓰는 축 | **4** — `event`·`extraction`·`koneps`·`ml` |
| per-file `javap` 호출 꼴을 쓰는 축 | **10**(위 열과 같은 집합, 전수) |

술어 본문도 열 축에서 **같다** — `isDisallowed` 가 `startsWith("bidvector.")` + 허용 뿌리 세그먼트 접두
`none` 이고, 열 벌이 **인자 이름 하나만 다르다**(`persistence` 가 `coordinate`, 아홉이 `importedPackage` —
전수 대조). 공유 `isOutsideRoots` 와 같은 식이다. 즉 ①~③ 의 통합은 **거동이 아니라 중복을 없애는
편집**이고, 거동이 움직이는 자리는 아래 ③ 하나뿐이다.

### ② 호출 꼴의 비용 — per-file 이 일괄의 8배(이 호스트, 따뜻한 셸)

`javap` 벽시계만 쟀다(정규식 비용 제외). **M11 이 적은 수를 옮기지 않고 다시 쟀다** — 조건이 다르면
수가 다르다(M11 은 `persistence` 41 class 에 56.9초였고 같은 축을 여기서는 14.5초로 쟀다. 꼴의 비는
같고 절대값이 다르다).

| 묶음 | class | per-file 합 | 일괄 합 | 비 |
| --- | --- | --- | --- | --- |
| 형제 **열** 축(통합 대상) | **140** | **47.5초** | **5.8초** | **8.2배** |
| 이미 일괄인 **네** 축 | 260 | (측정 참고 111.0초) | 3.5초 | — |

축별로는 `persistence` 41 class 가 14.5초 ↔ 0.9초, `notification` 34 class 가 11.2초 ↔ 0.7초다.
**예상 절감은 형제 열 축에서 약 42초**이고, 이것이 `:adapters:test` 전체에서 얼마가 되는지는 전·후
동일 조건(`:adapters:test --rerun`)으로 따로 잰다 — 그 수는 `commands.md` 가 든다.

### ③ 추출 층 대조 부재 — 그리고 **경계 고정이 열 축의 추출 집합을 바꾼다**

형제 열 축에는 「무엇을 **잡나**」를 재는 자리가 없다(전수 확인). 그 위에 **계약이 상정하지 않은 사실
하나**를 쟀다: 경계를 고정하면 열 축의 추출 집합이 **줄어든다**. 줄어드는 것이 무엇인지 전수로 갈랐다.

(class, 좌표) 쌍 집합으로, U = 비경계 · A = 경계 고정:

| 재는 것 | 열 축(전수) | `ml` |
| --- | --- | --- |
| `A \ U` | **0** — A 는 U 의 부분집합이다 | 0 |
| `U \ A` | **class 수와 정확히 같다**(예: `persistence` 41/41) | 546 |
| `U \ A` 가 `Classfile` 헤더 줄의 경로 쌍 집합과 **같은가** | **전수 True** | False |
| 헤더 밖의 잔차(중첩 이름공간 오탐) | **0** | **474** |

읽는 법: 열 축에서 경계 고정이 버리는 것은 **`javap` 의 `Classfile <경로>` 머리글에서 뽑힌 경로 쌍**
하나뿐이고(class 하나당 하나) **실제 상수 풀 좌표는 하나도 잃지 않는다**. `ml` 만 헤더 72 + 중첩
오탐 474 다 — 그 474 가 M11 이 그 축에서만 경계를 고정한 이유다.

**판정 집합은 열 축 전부에서 네 조합(per-file/일괄 × U/A) 모두 위반 0** 이다. 축의 허용 뿌리에 자기
패키지가 들어 있어(열넷 전수) 경로 쌍이 늘 허용으로 떨어지기 때문이다. 그러므로 **「위반 0 == 위반 0」은
공허한 비교**이고, 이 slice 의 잠금은 위 표처럼 **쌍 집합의 등식**이어야 한다(M11 cr r1 L-1 교훈).

딸린 사실 하나 — 비경계 판에서는 **헤더 경로만으로 추출 비공허가 늘 만족된다**. 즉 오늘 형제 열 축은
정규식이 완전히 망가져도 비공허 단언이 서는 자리였다(그 단언 자체가 없으므로 관측되지 않았다).

### ④ 「다른 이름의 판정 심볼」 — 오늘 판정 호출이 축마다 산다

판정 호출(`disallowedCoordinates`/`disallowedBytecodeReferences`)이 **열네 축 test 안**에 있고, 두 층
등식 표(`BYTECODE_LAYER_ROOTS`)는 `ScopedAllowedRootsParityTest` 의 `private companion` 에 있어 **판정
입력이 아니다**. M11 verifier r1 이 그 틈으로 V-dec·V-call 둘을 통과시켰다(각 exit 0 · 27/0).

처방(표를 순회하는 단일 test 가 판정)을 쓰려면 표가 두 test 에서 보여야 한다 — 표의 자리와 「표 소유
class 의 상수 풀을 읽는」 단언의 앵커가 함께 움직인다. 그 설계는 `design-review.md` 가 정한다.

### ⑤ 양성 표본이 유한 열거다 — 열 가운데 **다섯 뿌리가 표본 밖**이다

`BytecodeConstantPoolTest` 의 양성 표본이 든 뿌리는 `procurement`·`strategy`·`sharedkernel`·
`workflow`·`adapters` **다섯**이다. 승인된 모듈은 **열**이다(`settings.gradle.kts` `include` 열 =
`layer.domain`+`layer.application`+`layer.adapters`+`layer.app` 열, 집합 일치 — 전수 확인).

표본 밖: `qualification` · `bidding` · `decision` · `settlement` · `app` — **다섯**. M11 v r1 **L-A** 의
lookahead 축소가 이 다섯을 빼도 대조가 통과하는 까닭이 이 수다.

구성으로 뽑을 자리는 이미 있다 — `architecture-policy.properties` 의 `package.root`(=`bidvector`) +
`layer.order` + `layer.<층>` 이고, 그 파일은 **이미 `:adapters:test` 의 선언 입력 + system property**다
(`ScopedAllowedRootsParityTest` 가 쓰는 자리). 모듈 이름 → 패키지 뿌리 규칙(`-` 제거)의 정본은
build-logic `PackageOwnershipPolicy.ownedPackage` 이고, `app` test `ArchitecturePolicy` 가 이미 같은 규칙을
한 벌 더 들고 `ArchitectureGateTest` 가 그 집합을 **production 패키지 전수와 등식**으로 묶는다. 세 번째
사본이 생기는 비용과 대안은 `design-review.md` 가 다룬다.

## 새 OPEN 후보 — 착수 실측이 낸 것

| 후보 | 무엇 | 왜 지금 보고하는가 |
| --- | --- | --- |
| **(A)** 축 없는 adapters 패키지 | adapters main 의 1급 패키지는 **20** 이고 dependency test 축은 **14** 다. 차는 여섯(`access`·`bidding`·`notificationinbox`·`result`·`retention`·`snapshot`) | M11 design-review (3) 「미달 — 유지」가 적은 자리(패키지 ↔ dependency test 전단사)를 **수로** 고정한 것이다. 이 slice 의 축은 아니다 |
| **(B)** `MutableSet` in-place 변경 | M11 알려진 제한 10(v r1 M-1) 의 처방 후보 「축 값을 불변 집합으로」는 **열넷 축 전부의 선언을 바꾸는 편집**이라 M11 이 하지 않았다. 이 slice 는 그 열넷을 **이미 만진다** | 같은 라운드에서 치르면 싼 자리일 수 있다. 계약 12A 행의 다섯에는 없으므로 **팀장 판정 사항**이다 |

## in_scope

- `adapters/src/test/kotlin/bidvector/adapters/architecture/BytecodeConstantPool.kt` — 공유 헬퍼를 넓힌다
  (단일 파일 본문 접근 · 경계 고정 정규식 · 허용 뿌리 술어 · 일괄 호출)
- `adapters/src/test/kotlin/bidvector/adapters/architecture/BytecodeConstantPoolTest.kt` — 양성 표본을
  모듈 뿌리에서 **구성**으로(⑤)
- `adapters/src/test/kotlin/bidvector/adapters/architecture/ScopedAllowedRootsParityTest.kt` — 표의 자리와
  소유자 앵커(④)
- 열네 축의 `*AdapterDependencyTest.kt` — 복제된 정규식·`javapExecutable`·per-file 호출 제거, 판정 호출
  이전(①②③④)
- `config/quality/gate-tests.properties` — 새 test class 가 생기면 **만드는 커밋에서** 등재
- `docs/discovery/capability-map.md` §12.3 에 이 slice 의 하위 절을 덧붙인다. **글자는 머지 차례에
  다시 정한다** — 지금 쓴 `(p)` 는 자리를 잡아 둔 것이고, 팀장이 알린 머지 순서(12B→12C→12D→12A)대로면
  그 시점 §12.3 의 다음 글자로 바꾼다. 상호 참조(KDoc·evidence)에는 글자를 쓰지 않았다
- `reports/evidence/m12/adapters-javap-unify/**`

설계 검토 뒤 새 파일(표·판정 test 의 자리)이 필요하면 **계약 갱신으로 in_scope 에 더하고 보고**한다
(M11 의 「수정 라운드 새 파일 ↔ in_scope」 규율).

## out_scope

- **production 소스 0 줄.** `adapters/src/main/**` · `app/**` · `openapi/**` · `web/**` · migration 무접촉.
  새 술어가 production 을 붉히면 고치지 않고 보고한다(승인 경계).
- **`config/quality/architecture-policy.properties` 의 값·키** — 이 slice 는 그 파일을 **읽기만** 한다.
  두 층의 허용 뿌리 목록 내용은 바뀌지 않는다(`policy.version` 무변경).
- `build-logic/**` — 모집단을 넓히지 않으므로 M11 이 겪은 표본 거짓이 생길 자리가 없다. 붉으면 보고한다.
- **두 층의 단일 출처화** · **허용 뿌리가 실재하는가** 단언 — M11 이 ADR 자리·과잉으로 돌린 그대로.
- 위 새 OPEN 후보 (A)(B) — 팀장 판정 없이는 손대지 않는다.
- 다른 slice 의 evidence · `milestone-12.md` — 팀장 레인이 받는다.

## acceptance

CI `check` job 의 명령 그대로 — `./gradlew --no-daemon check` 와 `./gradlew --no-daemon qualityBaseline`.
표적 실행(`--tests`)과 `:adapters:test` 단독 실행은 **변이·비용 측정에만** 쓰고 판정 근거로 쓰지 않는다.

## rollback

in_scope 경로 한정 `git restore --source=<원천> --staged --worktree -- <경로들>`. 원천은 착수 base
`10d208d2664c6bb87cd7082a2853c08c2c0d3236`(고정 — 움직이는 `origin/main` 이 아니다). 머지 차례에 main 을
병합하면 원천을 **그 병합 커밋 SHA** 로 바꾸고 **셋째 단언**(복원 대상 blob 이 두 원천에서 같은가)을 함께
잰다. 목록은 `git diff --name-status <원천>..HEAD` 기계 산출이고 라운드마다 재산출한다. ①~⑥ 실측은
**마지막 산출물 커밋**에서 임시 clone 으로 돌리고 `실측 HEAD:` 를 `rollback.md` 에 적는다.

## 하네스 레인 변경

**없다.** `CLAUDE.md` · `.claude/**` 커밋 **0**. 이 slice 가 만지는 `config/quality/gate-tests.properties`
는 **게이트 입력**이고 하네스 문서가 아니다.

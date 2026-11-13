# M11 / gates-adapters-2 — adapters dependency test 술어 축

- base: main `94ee445b50cafa669af435e651d811cc551c67e8` (M10 종결 머지) · 계약 정본 `milestone-11.md` 11C 행
- 공통 규율: M11 레인 공통 지시(팀장 2026-11-13) · `CLAUDE.md` · `v2-slice-pipeline` · `evidence-pack`
- 받는 항목: 계약 「받는 항목」 **3**(`OPEN-M10-SCOPED-PACKAGES-COVERAGE`) · **4**(10C 등재 우회 ⓐⓑⓒ)
- 결정 지점은 추천안으로 진행(전역 규칙). 거동을 바꾸는 선택 · 외부 effect · Flyway 는 멈추고 보고한다.
- **게이트 slice 다** — 구현 전에 `design-review.md`(Phase 2.5)를 쓴다. 이 문서는 착수 실측과 계약만 싣는다.

## 착수 실측 — 여섯 축의 오늘 술어 종류와 「새 축이 오늘 붉은가」

계약 11C 행이 든 여섯을 **코드에서 다시 쟀다**. 술어 종류는 계약·capability map §12.3 (i) 의 서술과 같았고,
**새 축의 오늘 위반 여부는 갈렸다 — 다섯은 초록이고 `ml` 하나가 붉다**(아래 「멈춤 보고」).

| 축 | 오늘 술어 | 허용 목록 심볼 | 이름 층(`source.scoped-packages`) | 신설 축의 오늘 판정 |
| --- | --- | --- | --- | --- |
| `notification` | `javap -p -v` 상수 풀 | `ALLOWED_ROOTS`(allow) | 없음 | **초록** — 이름 층 신설 |
| `persistence` | `javap -p -v` 상수 풀 | `ALLOWED_ROOTS`(allow) | 없음 | **초록** — 이름 층 신설 |
| `event` | 소스 `^import bidvector\.…` | `ALLOWED_ROOTS`(allow) | 없음 | **초록** — 두 층 신설 |
| `extraction` | 소스 `^import bidvector\.…` | `OUT_OF_LAYER_DOMAIN_ROOTS`(**deny**) | 없음 | **초록** — 두 층 신설(deny→allow 전환 포함) |
| `koneps` | 소스 `^import bidvector\.…` | `OUT_OF_LAYER_DOMAIN_ROOTS`(**deny**) | 없음 | **초록** — 두 층 신설(deny→allow 전환 포함) |
| `ml` | 소스 `^import bidvector\.…` | `ALLOWED_DOMAIN_ROOTS`(allow) | 없음 | **붉다 — 바이트코드 층에서만**(아래) |

**계약이 적지 않은 갈래가 하나 더 있다** — ⓑ 넷 가운데 `extraction`·`koneps` 둘은 **거부 목록**이다. 거부
목록은 열거하지 않은 좌표를 전부 허용하므로 `javap` 로 옮기는 것만으로는 부족하고 **allow 로 뒤집는** 편집이
함께 간다(그래야 두 층 등식의 반대쪽 항이 생긴다). 그 전환도 오늘 초록이다(실측).

### 이름 층 실측 — `domainSourceReferenceGate`

여섯 축을 `source.scoped-packages` 에 더하고 축마다 허용 뿌리를 「그 축의 바이트코드 층 목록 ∪ 자기 패키지」로
둔 뒤 **게이트를 실제로 돌렸다**(정적 근사가 아니다 · 변경은 커밋하지 않고 되돌렸다).

| 실행 | `scopedFiles` | `violations` | 종료 |
| --- | --- | --- | --- |
| base(여덟 축) | 33 | 0 | 성공 |
| 여섯을 더한 판(열넷) | **122** | **0** | 성공 |

판정 모집단이 **33 → 122 파일**로 늘고도 위반 0 이다. 수가 움직인 것이 「쟀다」의 양성 대조다(초록인데
아무것도 보지 않는 자리와 가른다).

### 바이트코드 층 실측 — `ml` 이 붉은 이유는 **층 위반이 아니라 형제 정규식의 꼴**이다

ⓑ 넷에 형제 축의 javap 술어를 그대로 이식하면 어떻게 되는지를 컴파일 산출물에서 쟀다
(`:adapters:compileKotlin` 뒤 class 파일 전수 · 형제와 같은 정규식 `bidvector[/.][A-Za-z0-9_/.$]+`).

세는 단위는 **허용 밖 좌표(distinct)** 다(test 가 내는 `class: 좌표` 쌍은 더 많다 — `ml` 의 경우 474).

| 축 | class 수 | 형제 정규식(오늘 꼴) | 경계 고정 정규식 |
| --- | --- | --- | --- |
| `event` | 13 | 0 | 0 |
| `extraction` | 88 | 0 | 0 |
| `koneps` | 87 | 0 | 0 |
| **`ml`** | 72 | **308** | **0** |

**보정 대조**(술어 이식 전에 먼저 쟀다 — 탐침이 오늘 초록인 축을 초록으로 재는가): 오늘 그 술어를 **실제로
쓰는** 두 축 `notification`(34 class)·`persistence`(41 class)가 두 꼴 모두 **0**. 탐침은 건전하다.

`ml` 의 308 은 **층을 넘는 참조가 아니다.** 이 패키지는 contracts 모듈의 생성 proto 타입을 쓰고, 그 JVM
패키지가 `contract.bidvector.ml.v1` 이다. 형제 정규식은 **경계를 고정하지 않아** 그 내부 이름
(`contract/bidvector/ml/v1/…`)의 **가운데 토막**을 `bidvector/ml/v1/…` 로 뽑는다. 뽑힌 좌표는 어느 허용
뿌리에도 들지 않으므로 전부 위반이 된다. 같은 참조를 **이름 층**은 완전수식 FQN 으로 보므로
(`isSelfNamespace` 는 `bidvector.` 접두를 본다) 조용히 지나간다 — 그래서 위 이름 층 실측이 초록이었다.

`contract.bidvector.*` 를 참조하는 adapters 패키지는 **`ml` 하나**다(전수). 그래서 오늘 여덟 축은 이 꼴을
만나지 않고, 형제 정규식의 이 성질은 **잠복한 오탐**으로 남아 있다.

## 멈춤 보고 — 승인 경계에 닿는 항목 하나

계약 11C 행과 「승인 경계」가 「**새 축이 오늘의 production 코드를 붉히면 고치지 말고 착수 보고에서 멈춘다**」로
묶은 자리다. 닿은 것은 **`ml` 하나**이고, 성질이 계약이 상정한 것과 다르다 — **production 코드의 위반이
아니라 이식할 술어 자체의 오탐**이다. 셋 가운데 하나를 팀장이 고른다.

| 안 | 무엇 | 대가 |
| --- | --- | --- |
| **(가)** | `ml` 이식 시 정규식의 **좌측 경계를 고정**하고(그 토막이 식별자 가운데에서 시작하지 않음) 같은 꼴을 형제 축에도 맞춘다 | 오늘 조용한 여덟 축의 술어 문자열을 함께 만진다 — 거동 변화 0(실측: 두 축 보정 대조에서 두 꼴이 같은 답) 이지만 in_scope 가 여덟 파일로 넓어진다 |
| **(나)** | `ml` 의 새 술어에서만 경계를 고정하고, 형제 여덟의 잠복 오탐은 **새 OPEN 으로 등재** | in_scope 최소. 같은 사각이 여덟 축에 남고 그 등재를 받는 자리가 생긴다 |
| **(다)** | `ml` 은 이식하지 않고 **소스 정규식으로 둔 채 이름 층만** 더한다 | 계약 ② 가 `ml` 을 들었으므로 계약 미달이다. ⓑ 의 사각(import 없는 전체 한정 참조)이 `ml` 에 남는다 |

**구현 레인의 추천은 (나)다.** 이 slice 의 축은 「여섯 축의 술어를 옳은 층으로 옮긴다」이고, 오늘 조용한
여덟 축의 술어 문자열을 같은 라운드에 만지는 것은 **다른 결정**(게이트 술어 변경 ⇒ 표적 재검증이 여덟 축에
붙는다)이다. (나)로 가면 `ml` 의 새 술어가 **그 사각을 갖지 않는 유일한 축**이 되고, 등재가 받는 자리는
형제 술어 공유 slice 다. 팀장이 (가)를 고르면 in_scope 를 여덟 파일로 넓혀 갱신한다.

## 받는 항목 4 — 10C 등재 우회 ⓐⓑⓒ 의 오늘 상태

| 우회 | 오늘 | 처분 후보(설계 검토가 확정한다) |
| --- | --- | --- |
| ⓐ 표의 **값 식**으로 메꾸기(`… to AUDIT_ROOTS + NOTICE_ROOTS`) | 열려 있다 | **닫는다** — key 마다 상수 풀이 지목한 소유 class 의 getter 를 리플렉션으로 불러 표 값과 **`===` 동일성** 단언. 실측으로 가능함을 확인했다: 소유자는 file facade(`…AdapterDependencyTestKt`)이고 getter 는 `public static final java.util.Set getALLOWED_ROOTS()` 다(이름 맹글링 없음 · `setAccessible` 불필요) |
| ⓑ 같은 패키지의 **다른 보유자**(`internal object AuditWide { … }`) | 열려 있다 | 설계 검토가 판정한다 — 술어를 **심볼**에 묶어 닫거나 경계 밖 등재. ⓐ 의 처방으로는 닫히지 않는다(찾은 소유자가 그 `object` 라 동일성이 성립한다) |
| ⓒ `ownClassFiles` 의 **접두 필터**가 `…ParityTestFillKt` 같은 별도 파일까지 읽는다 | 열려 있다 | **닫는다** — 접두 대신 「자기 class **정확히** + `$` 중첩」. 실측: 오늘 그 디렉터리의 class 셋 가운데 `getALLOWED_ROOTS` 참조를 가진 것은 `ScopedAllowedRootsParityTest.class` **하나**이고 `…ParityTestKt.class` 는 **0** 이라, 좁혀도 뽑히는 패키지 집합은 여덟 그대로다 |

## in_scope

- `adapters/src/test/kotlin/bidvector/adapters/architecture/ScopedAllowedRootsParityTest.kt`
  (표에 여섯 축 추가 · ⓐ 리플렉션 동일성 단언 · ⓒ 접두 필터 좁히기)
- `adapters/src/test/kotlin/bidvector/adapters/architecture/BytecodeConstantPool.kt`(**신설** —
  일괄 javap · 세그먼트 경계 정규식 · 허용 뿌리 술어. 계약 갱신 (5))
- `adapters/src/test/kotlin/bidvector/adapters/architecture/BytecodeConstantPoolTest.kt`(**신설** —
  추출 층 양성 대조, cr r1 M-2. 계약 갱신 (5))
- `build-logic/src/test/kotlin/bidvector/buildlogic/SourceReferencePolicyTest.kt`(**표본 한 줄** —
  out_scope 접촉, 계약 갱신 (6))
- 여섯 축의 dependency test — `event` · `extraction` · `koneps` · `ml`(술어 이식 + deny→allow 전환 둘) ·
  `notification` · `persistence`(`ALLOWED_ROOTS` 를 `internal` 로 여는 한 단어)
- `config/quality/architecture-policy.properties`(정책 키 신설 — `source.scoped-packages` 값 + 축별
  `source.scoped-allowed-roots.*` 여섯 · 주석)
- `config/quality/gate-tests.properties`(**주석만** — 「`EventAdapterDependencyTest` 는 여전히 소스 텍스트」가
  거짓이 된다. 여섯 class 는 이미 등재돼 있어 등재 줄 변경 0)
- `docs/discovery/capability-map.md` §12.3 에 **이 slice 의 하위 절**을 덧붙인다(글자는 D-11C-8 대로 마지막 main 병합 시점에 정한다 — 현재 판은 **(k)**, 브리프의 (m) 배정은 계약 변경으로 폐기됐다)(기존 행 수정 0,
  §12 머리 집계 무변경)
- `reports/evidence/m11/gates-adapters-2/**`

## out_scope

- **production 소스 0 줄.** `adapters/src/main/**` · `build-logic/**` · `openapi/**` · `web/**` ·
  migration 무접촉. 새 축이 production 을 붉히면 고치지 않고 보고한다(승인 경계).
- **여덟 축의 dependency test 술어** — 오늘 조용하고 이 slice 의 축이 아니다. 형제 정규식의 경계 사각은
  위 (나) 안대로 **등재**한다(팀장이 (가)를 고르면 계약 갱신으로 넓힌다).
- `OPEN-VERDICT-CONSTRUCTION-VISIBILITY` · testFixtures 개방 ADR — M11 「범위 밖」 절 그대로.
- 두 층의 **단일 출처화**(test 가 정책을 읽어 `ALLOWED_ROOTS` 를 없앤다) — 두 층의 독립성을 버리는 거래라
  ADR 자리다(§12.3 (i) 가 적는 그대로).
- 다른 slice 의 evidence · `milestone-11.md` — 팀장 레인이 받는다.

## acceptance

CI `check` job 의 명령 그대로 — `./gradlew --no-daemon check`. 표적 실행(`--tests`)은 변이 측정에만 쓰고
판정 근거로 쓰지 않는다.

## rollback

in_scope 경로 한정 `git restore --source=<원천> --staged --worktree -- <경로들>`. 원천은 착수 base
`94ee445b50cafa669af435e651d811cc551c67e8`(고정 — 움직이는 `origin/main` 이 아니다). 머지 순서상 main 을
병합하게 되면 원천을 그 병합 커밋 SHA 로 바꾸고 **셋째 단언**(복원 대상 blob 이 두 원천에서 같은가)을 함께
잰다. 목록은 `git diff --name-status <원천>..HEAD` 기계 산출이고 라운드마다 재산출한다. ①~⑥ 실측은
**마지막 산출물 커밋**에서 임시 clone 으로 돌리고 `실측 HEAD:` 를 `rollback.md` 에 적는다.

## 계약 갱신 2026-11-13 (팀장 — 착수·설계 검토 답과 수정 라운드 r1)

1. **`ml` 은 (나)** — 그 축에서만 좌측 경계를 고정하고 형제 열 축의 잠복은 신설 OPEN 이 받는다.
2. **`extraction`·`koneps` 의 deny→allow 전환 승인** — 두 층 등식의 반대쪽 항을 세우려면 필요하고 오늘
   초록이라는 실측이 근거다. 전환 전후 판정 집합을 변이로 쟀다.
3. **ⓑ 는 닫는 안(보유자 단일성) 승인.** 다만 r1 에서 **닫는 범위가 좁혀졌다** — 같은 이름의 둘째
   보유자만 닫고 「다른 이름의 판정 심볼」은 열린 채 등재한다(`checklist.md` 알려진 제한 9).
4. **javap 호출 꼴은 (B)** — 새 네 축만 일괄, 기존 두 축은 무접촉. 약 100초 비용은 신설 OPEN 이 받고,
   **일괄 귀속이 per-file 과 같은 답을 내는 대조**를 남겼다(`commands.md` §12).
5. **수정 라운드 새 파일 ↔ in_scope** — 아래 둘을 in_scope 에 더한다(팀장 승인).
   `adapters/src/test/kotlin/bidvector/adapters/architecture/BytecodeConstantPool.kt`(일괄 javap ·
   세그먼트 경계 정규식 · 허용 뿌리 술어, 다섯 자리가 쓴다 — 「복제 넷보다 공유 하나가 맞다」) ·
   `adapters/src/test/kotlin/bidvector/adapters/architecture/BytecodeConstantPoolTest.kt`(추출 층
   양성 대조, cr r1 M-2 — 만드는 커밋에서 `gate-tests.properties` 에 등재했다). 둘 다 rollback 복원
   목록에 든다.
6. **out_scope 접촉 — 표본 한 줄, 주장 무변경**(팀장 승인). 착수 계약의 `build-logic/**` 무접촉이
   깨졌다: 모집단 확장이 `SourceReferencePolicyTest` 의 「범위 축 밖 패키지」 표본
   (`bidvector.adapters.persistence`)을 거짓으로 만들었다. 표본을 `bidvector.adapters.snapshot` 으로
   바꿨고 **단언의 주장은 그대로**다. 모집단 확장이 낳은 필연이다. rollback 복원 목록에 든다.
7. **§12.3 글자는 마지막 main 병합 시점에 정한다**(D-11C-8). 현재 판은 **(k)** 이고, 앞선 PR 이 먼저
   머지되면 그 시점에 다시 센다 — 그때 main 재병합 · acceptance · rollback 을 한 라운드로 다시 잰다.

## 하네스 레인 변경

**없다.** `CLAUDE.md` · `.claude/**` 커밋 **0**. 이 slice 가 만지는 정책 파일 둘
(`architecture-policy.properties` · `gate-tests.properties`)은 **게이트 입력**이고 하네스 문서가 아니다.

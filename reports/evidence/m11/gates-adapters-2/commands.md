# M11 / gates-adapters-2 — 실측

출력 전문은 싣지 않는다(핵심 결과 한 줄). 라운드 이력 절은 두지 않는다 — 각 절은 **마지막 측정**이다.

## 1. 착수 실측 — 여섯 축의 술어 종류

`scope.md` 「착수 실측」표가 정본이다. 술어 종류·허용 목록 심볼·이름 층 유무를 축마다 셌고, 계약과
capability map §12.3 (i) 의 서술과 일치했다. **계약이 적지 않은 갈래 하나를 더 쟀다** — `extraction`·
`koneps` 둘은 **거부 목록**이라 javap 이식만으로는 두 층 등식의 반대쪽 항이 서지 않는다.

## 2. 이름 층 — 모집단이 움직였고 위반은 0

| 실행 | `scopedFiles` | `violations` | 종료 |
| --- | --- | --- | --- |
| 여덟 축(앞 판) | 33 | 0 | 성공 |
| 열넷(이 slice) | 122 | 0 | 성공 |

판정 모집단이 **33 → 122 파일**로 늘고도 위반 0 이다. 수가 움직인 것이 「쟀다」의 양성 대조다 — 초록인데
아무것도 보지 않는 자리와 가른다.

## 3. 정규식 경계 — 세 판을 **뽑은 수와 함께** 쟀다

`ml` 축이 쓰는 생성 proto 타입의 JVM 패키지가 `contract.bidvector.ml.v1` 이다. 경계 없는 형제 정규식은 그
내부 이름의 가운데 토막을 자기 좌표로 읽는다. **위반 수만 보면 판을 잘못 고른다** — 그래서 뽑은 좌표 수를
함께 센다(한 판은 오탐도 0 이지만 양성도 잃는다).

| 축 | class | 경계 없음(형제) | 과한 경계 | **세그먼트 경계**(채택) |
| --- | --- | --- | --- | --- |
| `notification` | 34 | 뽑음 294 · 위반 0 | 뽑음 260 · 위반 0 | 뽑음 260 · 위반 0 |
| `persistence` | 41 | 뽑음 718 · 위반 0 | 뽑음 **675** · 위반 0 | 뽑음 **677** · 위반 0 |
| `event` | 13 | 뽑음 210 · 위반 0 | 뽑음 197 · 위반 0 | 뽑음 197 · 위반 0 |
| `koneps` | 87 | 뽑음 794 · 위반 0 | 뽑음 707 · 위반 0 | 뽑음 707 · 위반 0 |
| `ml` | 72 | 뽑음 1096 · **위반 308** | 뽑음 **713** · 위반 0 | 뽑음 **716** · 위반 0 |

**과한 경계를 버린 근거가 이 표에 있다.** 앞 글자를 「식별자 아님」으로 요구하면 JVM 서술자
(`Lbidvector/…;`, 앞 글자가 `L`)가 **전부 빠진다** — persistence 677 → 675, ml 716 → 713 이 그 손실이고,
**위반 수만 보면 두 판이 구별되지 않는다**. 채택한 판은 `/`·`.` 둘만 배제한다(「`bidvector` 가 수식 이름의
첫 세그먼트가 아닌 자리」).

**형제 열 축과 오늘 같은 답을 내는가**(팀장 지시): 오늘 그 술어를 실제로 쓰는 두 축에서 세 판의 **위반이
모두 0** 이다(`notification`·`persistence`). 다른 답을 내는 축은 `ml` 하나이고 그것이 이 slice 가 경계를
고정하는 이유다. 형제의 잠복은 `OPEN-M11-JAVAP-ROOT-REGEX-UNANCHORED` 가 받는다.

## 4. 음성 대조 — 처방 전에 네 변이가 **오늘 어떻게 판정되는가**

전부 base `7c6b768b`(처방 없음)에서, 변이 적용을 먼저 단언하고 `--rerun` 으로 돌렸다.

| 변이 | 무엇을 바꿔치웠나 | 오늘 |
| --- | --- | --- |
| **A — ⓐ 값 식 메꾸기** | 표의 `audit` 값을 `AUDIT_ROOTS + NOTICE_ROOTS` 로 · audit 정책을 그 합집합으로 | **초록**(4 test, 실패 0) |
| **B — ⓑ 같은 패키지 다른 보유자** | audit 패키지에 `internal object AuditWide`(값 = audit ∪ notice) 신설 · 표를 그 심볼로 · 정책을 합집합으로 | **초록**(4/0) |
| **C1 — 별칭 바꿔치기만** | 표의 `audit` 값을 `NOTICE_ROOTS` 로 · 정책을 notice 뿌리로 | **RED**(4/1) — 선언 패키지 등식 하나(10C 가 닫은 자리가 실제로 닫혀 있다) |
| **C2 — ⓒ 접두 메꾸기** | C1 에 `ScopedAllowedRootsParityTestFill.kt` 신설(audit 뿌리를 참조) | **초록**(4/0) |
| **D — 술어 이식 전 사각** | `extraction` production 에 **import 없는** 전체 한정 참조(`bidvector.decision.…`) | **초록**(1/0) |

**변이 표적을 `audit` 으로 고른 근거**: audit production 은 `bidvector.*` import **0**, 수식 참조 **0**
(파일 둘)이다 — 그 축의 정책 뿌리를 무엇으로 바꿔도 `domainSourceReferenceGate` 가 오염되지 않으므로
변이가 재려는 단언 하나만 움직인다.

**D 가 드러낸 함정 — 낡은 `.class` 가 가짜 RED 를 만든다.** D 를 되돌린 뒤(소스에 탐침 0) 재컴파일 없이
상수 풀을 재자 `extraction` 에 위반 1 이 남아 있었다. 되돌린 탐침이 `DocumentFormatKt.class` 에 그대로
있었던 것이다. 하마터면 「allow 전환이 production 위반을 드러냈다」로 보고할 뻔했다. 상수 풀 측정은
**소스 되돌림이 아니라 재컴파일**로 격리한다.

**D 는 `extraction` 에 양성 대조가 없다는 것도 드러냈다**(test 1개) — D-11C-4 의 근거다.

## 5. javap 호출 꼴 — 측정이 설계를 정했다

형제 열 축은 class 파일 하나마다 프로세스 하나를 띄운다. 이 slice 가 받은 네 축은 class 260 개다.

| 꼴 | 네 축(260 class) | `persistence` 하나(41 class) |
| --- | --- | --- |
| 파일마다 한 번(형제) | **6분 9초** | **56.9초** |
| 축마다 일괄 한 번 | **12초** | **1.0초** |

그래서 네 축의 새 술어는 일괄 꼴이다 — 선택이 아니라 필요다. 형제 열 축이 오늘 치르는 같은 비용(두 축
합 약 100초)은 이 slice 의 축이 아니라 `OPEN-M11-JAVAP-ROOT-REGEX-UNANCHORED` 가 받는다.

**보유자 선별의 비용도 같은 자리에서 쟀다** — 열넷 축의 test class **559 개**를 그대로 `javap` 하면
**58초**이고, 바이트 선별 뒤 생존 **13/559** 를 일괄 호출하면 **1.6초**다.

## 6. 보유자 단일성이 오늘 서는가 — 처방 전 전수

축 패키지의 test class 에서 `ALLOWED_ROOTS` 필드·getter 의 소유 class 를 축마다 셌다.

- 심볼이 이미 `ALLOWED_ROOTS` 인 **열한 축 전부에서 소유자가 원소 하나**이고, 그것이 그 축 dependency
  test 의 file facade 다.
- `extraction`·`koneps`·`ml` 셋은 심볼명이 달라 처방 전 **비어** 있었다(개명 뒤 같은 모양이 된다).
- **축이 다른 축의 `ALLOWED_ROOTS` 를 참조하는 자리는 0** — 이 단언은 오늘 아무 축도 붉히지 않는다.

## 7. ⓒ 접두 좁히기가 오늘 뽑는 것을 잃지 않는가

그 디렉터리의 class 셋 가운데 `getALLOWED_ROOTS` 참조를 가진 것은 `ScopedAllowedRootsParityTest.class`
**하나**이고(표는 바깥 class 의 `<clinit>` 에서 초기화된다), `…ParityTestKt.class`·`…$Companion.class`
는 **0** 이다. 그래서 「정확 일치 + `$` 중첩」으로 좁혀도 뽑히는 패키지 집합은 그대로다.

## 8. 양성 쌍 — 처방 뒤 각 변이가 **그 단언에서** RED

구현 커밋 뒤, 커밋된 트리에 바꿔치우고 `--rerun` 으로 돌렸다.

| 변이 | 결과 | 붉은 단언 |
| --- | --- | --- |
| **A** ⓐ | **RED** 6 test/실패 1 | 동일성 하나 — 어긋난 축 이름(`bidvector.adapters.audit`)을 낸다 |
| **B** ⓑ | **RED** 6/2 | 보유자 단일성(기대 `{AuditWide}` ↔ 실제 둘) · 동일성(소유자가 file facade 가 아니라는 명시 실패) |
| **C2** ⓒ | **RED** 6/3 | 보유자 단일성 · **선언 패키지 등식** · 동일성 — 접두를 좁히자 별도 파일이 더는 읽히지 않아 **별칭 바꿔치기가 다시 드러난다** |
| **D** 이식 | **RED** 4/1 | `extraction` 좌표 — `DocumentFormatKt.class: bidvector.decision.AssessmentBand` |

**바이트 선별이 미탐 없는 상위집합인가 — B 가 직접 쟀다.** 심은 보유자가 선별에 **들어왔다**:
`audit` 패키지의 선별 생존이 **1 → 2**(`AuditAdapterDependencyTestKt.class` + `AuditWide.class`)이고,
들어오지 않았다면 보유자가 하나로 보여 등식이 초록이었을 것이다.

**B 가 내 코드 결함 하나를 잡았다.** 첫 판은 ⓐ 단언이 `Method.invoke(null)` 의 **NPE** 로 떨어졌다 —
Kotlin `object` 의 프로퍼티 getter 는 인스턴스 메서드다. `Modifier.isStatic` 검사를 앞에 두고 「표의 값이
top-level `val` 이 아니라 인스턴스 보유자에 산다」를 말하는 실패로 바꿨다. 판정은 전후 같고(둘 다 RED)
바뀐 것은 **읽히는 이유**다.

## 9. acceptance — `./gradlew --no-daemon check`

CI `check` job 의 명령 그대로.

| HEAD | 종료 | 실행 계수 | 비고 |
| --- | --- | --- | --- |
| `36f115e3` | **0** | 35분 24초 · 433 중 **245 executed** · 123 from cache | 전건이 실제로 돈 자리 |
| **`b4fb3117`**(판정 SHA) | **0** | 23초 · **45 executed** · 399 up-to-date | 앞 실행 뒤 델타가 **문서뿐**이라 대부분 up-to-date 다 |

**「초록인데 조용하다」를 피하려고 판정 SHA 에서 test 층을 따로 강제 실행했다**(up-to-date 추론을 근거로
쓰지 않는다): `:adapters:test --rerun` → **실행**(UP-TO-DATE 아님) · 6분 5초 · **test 1439개, 실패+오류 0**.
`:build-logic:test --rerun` → 실행 · `SourceReferencePolicyTest` **25 test, 실패 0**. 이 slice 가 만진 축의
계수는 parity **6** · `event` 4 · `extraction` 4 · `koneps` 4 · `ml` 6 · `notification` 6 · `persistence` 4.

**`--rerun` 은 명령줄의 마지막 task 에만 붙는다** — `:adapters:test :build-logic:test --rerun` 은
`:adapters:test` 를 UP-TO-DATE 로 지나갔다. 그래서 둘을 **따로** 강제했다.

판정 SHA 에서 실제로 다시 실행된 45 task 에는 모듈 아홉의 `cpdReportPresenceGate`·`koverVerify`·`check` 와
`contractGate` · build-logic ktlint 셋이 들어 있다.

## 10. 첫 `check` 가 낸 둘 — 모집단 확장의 파급

전건이 **두 자리**를 냈고 둘 다 고쳤다(`36f115e3`).

1. **`:build-logic:test` 실패** — `SourceReferencePolicyTest` 의 「범위 축 밖 패키지」 표본이
   `bidvector.adapters.persistence` 였고 이 slice 가 그 패키지를 축으로 만들어 단언이 거짓이 됐다.
   표본을 `bidvector.adapters.snapshot`(축 열넷에 들지 않는 형제 패키지)으로 바꿨다 — 단언의 주장은
   그대로이고 바뀐 것은 예시다. **착수 계약의 `build-logic` 무접촉이 여기서 깨졌다**(`checklist.md`
   계약 갱신 (2)).
2. **`ktlint`** — 체인 호출 줄바꿈 규칙. 수신자를 지역 값으로 빼서 접었다.

## 11. 레인이 겪은 사고 하나 — 변이 되돌림이 미커밋 장부를 지웠다

변이 러너의 `git reset --hard && git clean -fdq` 가 **아직 커밋하지 않은 `commands.md` 를 지웠다**
(untracked 라 `clean` 의 대상이다). 다시 썼고 **같은 라운드에 바로 커밋했다**. 산출물에는 영향이
없다(그 시점에 산출물은 전부 커밋돼 있었다) — 기록은 장부 손실뿐이다. 교훈은 「변이 전에 커밋」이
`checkout --` 뿐 아니라 **`clean -fd` 에도 걸린다**는 것이다.

# M12 / adapters-javap-unify — 처분과 알려진 제한

## 받는 항목의 처분

계약 「받는 항목」 **1** = `OPEN-M11-JAVAP-ROOT-REGEX-UNANCHORED` 의 다섯. 팀장 승인으로 **(B)** 가
더해졌다(2026-11-14).

| 항목 | 처분 | 무엇이 그것을 닫는가 |
| --- | --- | --- |
| **①** 형제 열 축의 비경계 정규식 | **닫힘(구조)** | 열 축의 파일 `private` 복제본을 지우고 좌측 경계를 고정한 `SELF_COORDINATE` **하나**로 모았다. 축 열넷이 같은 정규식을 쓴다 |
| **②** per-file 호출 꼴의 비용 | **닫힘(측정)** | 열 축의 per-file 호출을 일괄 한 번으로. `:adapters:test --rerun` **345 → 207초**(같은 조건) |
| **③** 추출 층 대조 부재 | **닫힘(구조)** | 열넷이 같은 정규식을 쓰므로 `BytecodeConstantPoolTest` 의 대조가 그 열넷을 덮는다. 더해 `Chunk.body` 가 **`Classfile` 머리글 줄을 담지 않아** 추출 비공허가 정규식의 좌측 경계와 **독립으로** 참이 된다 |
| **④** 「다른 이름의 판정 심볼」의 구조 처방 | **닫힘(구조)** | 축 파일의 판정 호출 **0**(기계 계수). 판정은 표를 순회하는 단일 test 가 `BYTECODE_LAYER_ROOTS` 의 (key, value) 로 한다. 변이 V-dec′ 가 **exit 1** 로 붉는다(처방 전 exit 0) |
| **⑤** 양성 표본이 유한 열거 | **닫힘(구조)** | 표본을 승인된 모듈 뿌리 **전부**에서 구성으로. 변이 L-A′ 가 그 구성 표본에서 **exit 1**(처방 전 exit 0) |
| **(B)** `MutableSet` 제자리 변경 | **닫힘(거동)** | 축 값을 `java.util.Set.copyOf` 로, 「종류」를 cast 뒤 `add` 가 거부하는가로 단언. 변이 M-1b′ 가 **exit 1 · 55/11**(처방 전 exit 0) |

## 신설 OPEN 하나

**`OPEN-M12-ADAPTERS-PACKAGES-WITHOUT-AXIS`** — adapters main 의 1급 패키지는 **20** 이고 dependency
test 축은 **14** 다(실측). 차 여섯(`access`·`bidding`·`notificationinbox`·`result`·`retention`·
`snapshot`)은 두 층 등식의 모집단 **밖**이라, 그 패키지로 코드를 옮기면 층 밖 참조가 이 게이트 가족의
눈에 들지 않는다. 받는 자리는 **그 패키지들의 의존 규칙을 정하는 slice**(팀장 판정 2026-11-14 —
새 축을 세우는 것은 모집단 확장이고 새 결정이다).

## 알려진 제한

1. **양쪽 목록을 함께 넓히면 조용하다.** 두 층 등식은 「둘이 같은가」만 재고 「목록이 옳은가」는 재지
   않는다. 위협 모델 경계가 선언한 자리이고 ADR·리뷰가 든다(M11·10C 승계).
2. **정책 파일을 `:adapters:test` 의 선언 입력에서 빼면** 정책만 고친 뒤 `UP-TO-DATE` 거짓 초록이
   된다. 10C·M11 이 등재한 자리이고 이 slice 도 그 한 줄을 만지지 않았다.
3. **단일 판정 test 자신의 호출 자리에 식을 쓰면** 판정이 넓어진다. 위협 모델 (0) 의 경계 밖(게이트
   파일을 알고 고치는 저자)이고, 바뀐 것은 그 자리가 **열넷에서 하나**로 줄어 diff 가 보기 쉬워진
   것이다.
4. **per-file ↔ 일괄 쌍 집합 등식은 상시 test 가 아니다.** 상시로 남는 것은 `Classfile` 헤더 계수
   (`헤더 수 == 입력 수`)뿐이다. 등식을 상시로 두면 per-file 비용(열 축 47.5초)을 `:adapters:test` 에
   되돌려 넣는 셈이고 그것이 이 slice 가 없앤 비용이다. 전환 측정으로 남긴다(`commands.md` §6).
5. **`const val` 인라인 사각**은 바이트코드 층에 그대로다 — 이름 층이 덮는 분담이고 그 분담은
   이 slice 가 바꾸지 않았다(`OPEN-BYTECODE-GATE-CONST-VAL-BLINDSPOT`).
6. **모듈 이름 → 패키지 뿌리 규칙(`-` 제거)의 세 번째 사본**이 `ArchitecturePolicyValues` 에 생겼다.
   정본은 build-logic `PackageOwnershipPolicy.ownedPackage`, 둘째 사본은 `app` test 의
   `ArchitecturePolicy` 다. 규칙과 실물을 묶는 **모듈 전수 대조**는 `app` 의 `ArchitectureGateTest`
   (「뿌리 아래 1급 패키지 == 승인된 모듈 집합」)가 **같은 `check` 안에서** 든다 — adapters 쪽에 넷째
   대조를 세우지 않은 이유다(팀장 지시에 대한 답: 대조는 이미 있고 싸다). 사본이 틀리면 조용하지
   않다 — `-` 가 남은 이름은 좌표 정규식의 문자 집합에서 끊겨 표본 자체가 깨진다.
7. **`ml` 의 retry 축은 소스 텍스트로 남는다**(M11 승계). 그 축이 재는 것은 자기 저장소 좌표가 아니라
   외부 라이브러리의 계층이다.
8. **두 층의 단일 출처화는 범위 밖 유지**(M11 팀장 판정 그대로 — ADR 자리).
9. **축 test 에 남은 「허용 루트는 걸리지 않는다」는 구성 대조와 겹친다.** 아래 계약 갱신 (2) 가
   그 선택의 사유를 적는다.

## 계약 갱신 — 구현 레인이 올린 보고 항목 둘

### (1) 새 파일 셋을 in_scope 에 더했다 (팀장 승인 2026-11-14)

- `ArchitecturePolicyValues.kt` — 정책 읽기 + 모듈 뿌리 도출. 세 자리가 쓴다(두 층 등식 · 축 판정 ·
  추출 층 표본). 앞 판은 parity test 의 companion 이 같은 세 함수를 들고 있었고, 쓰는 자리가 셋이 되면
  복제가 된다.
- `ScopedAllowedRoots.kt` — 표 + 축 경로 + adapters 1급 패키지 전수. 표가 **두 test** 에서 보여야 하고
  (`private companion` 은 밖에서 안 읽힌다), **소유자 앵커가 표를 담은 class 를 가리켜야** 하므로 표와
  앵커가 함께 옮겼다(D-12A-1).
- `ScopedBytecodeLayerGateTest.kt` — **신설 test class**. 만드는 커밋(`2f7f0add`)에서
  `config/quality/gate-tests.properties` 에 등재했다.

### (2) D-12A-3 을 어디까지 적용했는가 (팀장에게 미리 알린 대로)

축별 기계적 대조 **셋 가운데 둘**(「허용 밖 표본」·「이웃 접두」)을 구성 대조로 옮기고, 「허용 뿌리는
걸리지 않는다」는 축에 **남겼다**. 그 하나까지 옮기면 축 **일곱**(`audit`·`profile`·`strategy`·
`persistence`·`event`·`extraction`·`koneps`)의 test class 가 `@Test` **0** 이 되고, 등재된 게이트 test
class 일곱을 지우고 `ALLOWED_ROOTS` 선언 일곱을 새 파일로 옮기는 **둘째 구조 변경**이 같은 slice 에
붙는다. 남긴 그 하나는 축의 허용 뿌리 가운데 **구성 표본이 표현하지 못하는 하위 패키지**
(`bidvector.workflow.evaluation` 꼴)를 읽히게 적는 자리이기도 하다. 열넷을 같은 규칙으로 다뤘다.

## 완료 조건 대조

| 조건 | 상태 |
| --- | --- |
| 받는 항목 다섯 + (B) 의 처분 | 위 표 — 여섯 전부 **닫힘** |
| 변이 양성 쌍 | 넷 전부 처방 전 `exit 0` ↔ 처방 뒤 `exit 1`(`commands.md` §2·§3) |
| 새 게이트가 오늘의 production 을 붉히는가 | **아니다** — 출하 코드 **0 줄**, 네 조합 전부 위반 0 |
| 외부 effect · Flyway | **0** |
| 하네스 레인 변경 | **없다**(`scope.md` 그 절) |

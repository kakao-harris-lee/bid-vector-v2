# M6/G1-DEC10 — 산출물 점검 (게시 낙찰하한율 신뢰 게이트)

계약은 `scope.md`, 착수 설계·코드 지도는 `design.md`, 명령·종료 코드는 `commands.md`,
되돌림은 `rollback.md` 가 정본이다. 이 문서는 **설계 검토 ↔ 게이트 대응표**와 **알려진 제한**이다.

## 설계 검토의 우회 ↔ 그것을 막는 자리 (대응표)

`design.md` §1 (2) 의 열 가지에 각각 무엇이 걸려 있는지. 「주석으로 적은 근거」가 하나도 없다 —
전부 타입·바이트코드·컴파일러가 진다.

| # | 우회 | 막는 자리 | 실측 |
| --- | --- | --- | --- |
| 1 | 원값을 `copy` 로 통과 값에 실어 나르기 | `@ConsistentCopyVisibility` + `internal` 생성자 | 컴파일 fixture `6b` · 변이 M2(`@ExposedCopyVisibility`) RED |
| 2 | 모듈 밖에서 통과 값을 직접 생성 | `internal` 생성자 | 컴파일 fixture `6` + 오타 변이 쌍둥이 |
| 3 | 모듈 **안**에서 게이트를 거치지 않고 생성 | ⓐ 멤버 접근 쌍 등식(클래스 입도) + **ⓐ′ 코드 단위·서명 등식(자기 제외 없음)** | `FloorTrustGateTest` ⓐ·ⓐ′·ⓐ″ · 변이 V8·N3 RED · M5·M6 RED |
| 4 | 가격 경로가 원값을 직접 읽기 | ⓑ 획득 + ⓑ′ 원값 멤버 접근, 둘 다 **코드 단위·서명이고 자기 제외가 없다** — 운반 타입이 자기 값을 소거 타입으로 내보내는 메서드도 센다 | `FloorTrustGateTest` ⓑ·ⓑ′·ⓑ″ · 음성 fixture 넷 · 변이 V1·V2·N1·N2 RED · **e1(운반 타입의 `Any?` 반환 + 캐스트 소비자)** RED |
| 5 | 개연 불가 가지를 `else` 로 통과 처리 | 결과 sealed + 가격 경로가 **통과 값만** 받는 서명 → `else` 로는 재료가 없다 | 메뉴 소진 `when` · `BidMenuFloorTrustTest` 사유 셋 · 변이 M1 RED |
| 6 | 경계값(`0.30`·`0.995` 정확값 · `0.2999`·`0.9951` · 척도 변형) | 밴드 포함 비교가 `BigDecimal` 값 비교 | `PublishedFloorTrustTest` 경계·척도 전수 |
| 7 | `StatutoryTable` origin 위장 | 게이트가 `NoticeValue` 에만 통과를 내준다 — 법정표는 **심판 아님** 별 가지 | `PublishedFloorTrustTest`(밴드 **안** 값도 통과가 아니다) · `BidMenuFloorTrustTest` |
| 8 | margin 이 `rate` 를 다른 함수로 읽기 | ③ **진입 타입**이 게이트 산출물이고 그 입력 타입의 생성자·`copy` 가 `internal` 이다 — 모듈 밖에서 날 율·숫자로 입력을 지을 수 없다(D-DEC10-22) | 컴파일 fixture `10`·`10b`·`11`·`12` + 오타 변이 **셋**(`10`·`11`·`12`) · ⓓ 등식 |
| 9 | 게이트를 부르고 결과를 **버리고** 원값을 쓰기 | 원값의 멤버를 만지는 **코드 단위**가 ⓑ′ 에 전부 남는다 — 같은 파일에 함수를 더해도 집합이 바뀐다. 다만 **같은 코드 단위 안에서** 버리는 것은 구조로 닫지 못한다(제한 ②) | 변이 M3 RED · N1·N2 RED |
| 10 | 리플렉션·직렬화로 통과 값 생성 | `decision` 은 도메인 모듈이라 직렬화 표면 0(`domainDependencyGate`) · 리플렉션 뿌리 게이트가 production 전 층 | 아래 알려진 제한 ③ 이 남는 범위를 적는다 |
| 11 | **위조한 밴드**를 진짜 게이트에 넘기기 — 타입 **밖** 조립, 같은 파일 둘째 함수, 그리고 **정책 타입 자신의 메서드**(`widened()`·`withBand()`) | ⓒ 정책 값 생성 코드 단위 등식(자기 제외 없음) — production 에서 정책 값을 만드는 자리는 출하 초기화자 하나와 컴파일러가 만든 자기 `copy` 계열뿐이다 | `FloorTrustGateTest` ⓒ·ⓒ′ · 변이 **B1**·**c1** RED |
| 12 | 원값을 **다른 표현으로 세탁**해 가격 경로에 넣기(`toString()` 정규식 · 영속 행의 `BigDecimal` · JSON) | ⓑ·ⓑ′ census 로는 **닫히지 않는다**(타입을 따라가므로 표현이 바뀌면 안 보인다 — r4 실측). 닫는 것은 **③ 진입 타입**이다: 세탁한 숫자는 게이트 산출물이 될 수 없고, 진입이 그 타입만 받는다 | 컴파일 fixture `11`(파싱해 지은 율)·`12`(영속 행의 `BigDecimal` 을 **율로 만들어** 넘김) · 변이 `lnd` 가 **컴파일 단계에서** 멈춘다. 두 fixture 모두 진단을 **심볼로 지목**한다(`'PlausibleFloorRate?' was expected` — CR r5 M-2) |

## 알려진 제한

1. **세 가격 경로가 출하 배선에서 돌지 않는다**(착수 census 실측) — `MlAnalysisPort` 가
   `UnavailableMlAnalysis` 고정(D-6A3-2, 운영자 결정 1)이고 메뉴 커널의 production 호출자가 0 이다.
   그래서 `floor_implausible` 계수는 **출하 앱에서 구조상 항상 0** 이고, 계수가 실제로 움직이는
   것은 커널을 직접 도는 test 다. 홀더는 `OPEN-ML-ANALYSIS-WIRING`. 게이트 자체는 배선 전에
   구조로 닫혀 있다 — 그것이 이 slice 의 범위다.
2. **ⓑ·ⓑ′ 는 보장이 아니라 재고다**(D-DEC10-22) — census 는 **타입을 따라가므로** 값의 표현이
   바뀌면(문자열·`BigDecimal`·영속 행·JSON) 그 자리가 어느 축에도 남지 않는다(r4 실측). 그래서
   그 두 축은 「원값이 가격 경로에 닿을 수 없다」를 **보장하지 않고**, 「누가 원값을 만지는가가
   바뀌었다」를 감지한다. 보장은 **진입 타입**이 진다(ⓓ + 컴파일 실패 fixture 넷).
   남는 범위 둘: ⓐ 이미 허용된 코드 단위 **본문 안에서** 결과를 버리고 원값을 쓰는 형태(변이 M3 가
   거동을 잰다) · ⓑ **`decision` 모듈 안에서** 날 율로 ③ 입력을 짓는 형태(ⓓ 등식이 production
   생성 자리를 하나로 잠그지만, 모듈 안 test 는 그 생성자를 그대로 쓴다 — 4B-5 의 경계값 test 가
   그 자리다).
   **손 재정의가 남는 범위**(CR r4 M-3) — 허용 키가 **서명**이므로 `toString`·`equals`·`hashCode`
   는 컴파일러가 만든 것과 **손으로 쓴 것이 같은 키**다. 그 셋이 허용에 들어 있는 자리는
   ⓑ 획득 축 **18 키**(운반·결과 타입 여섯 × 셋) · ⓑ′ 멤버 접근 축 **12 키**(그 축에 나타난 것만) ·
   **ⓐ 클래스 입도 축 3 키**(`PublishedFloorTrust`·`BidMenuGrounds`·`OptionContext` 의 `hashCode`
   — CR r5 L-7 이 세어 준 잔여다. ⓐ 는 거친 그물이라 영향이 작지만 이 절은 「전수」로 읽힌다)
   이고, 그 자리에 원값을 문자열로 내보내는 `toString` 을 손으로 써도 **집합이 바뀌지 않는다**.
   그 꼴이 가격 경로에 닿는 것을 막는 것은 census 가 아니라 **③ 진입 타입**이다 — r4 의 `lnd`
   변이가 바로 그 형태(`toString()` 정규식 파싱)이고 r5 에서 **컴파일 단계**에서 멈춘다. 분석·표시
   경로로 가는 노출은 애초에 이 게이트의 범위가 아니다(D-DEC10-4).

   입도·보장 서술은 **세 번 틀렸다가 고쳤다** — r1 「파일 입도로 닫았다」, r3 「코드 단위로 닫았다」
   (자기 제외가 남아 있었다), r4 「원값을 쥘 자리가 없다」(표현을 바꾸면 남아 있었다). 지금 문면은
   그 셋을 모두 반영한 것이고, **보장의 자리를 census 에서 타입으로 옮긴 것**이 이 라운드다.

3. **축 ②가 보지 못하는 범위** — 디스크립터의 반환·필드 **타입**만 보므로 제네릭이 소멸된 자리
   (컨테이너 원소), `Class.getEnumConstants` 류, 역직렬화 토큰은 집합에 남지 않는다.
   `OPEN-6F9-DIVISION-REFLECTION` 과 같은 형태의 범위다.
4. **허용 쌍 하나가 모듈 이름을 담는다** — `getValue$bid_vector_decision` 는 `internal` getter 의
   바이트코드 이름이다. Gradle 모듈 이름이 바뀌면 그 쌍이 바뀌어 등식이 깨진다. **의도다**(그때
   사람이 본다), 다만 「이름이 바뀌어도 거동은 같다」는 실패를 한 번 만든다.
   (r3 에서 키가 **서명까지** 담게 되어 같은 이름 오버로드도 갈린다 — r2 가 그 꼴을 초록으로
   실측했고 변이 N3 가 이제 RED 다. 인자 타입은 `|` 로 잇는다; 쉼표는 목록 파서의 구분자다.)
5. **capability map 삽입이 다른 slice evidence 의 좌표를 밀었다** — DEC-10 절에 15줄이 들어가
   그 아래를 가리키는 `capability-map.md:<줄>` 역참조 **22 건(11 파일, 전부 M0·M4·M7·harness
   evidence)** 이 +15 밀린다. 그 파일들은 이 slice 의 in_scope 밖이라 고치지 않는다 — 이 항목이
   그 사실의 등재다(명령은 `commands.md`).
6. **`fraction > 1` 하한율의 거동은 유지된다** — **D-DEC10-12**(2026-10-28 팀장 결정, r1 v M-1).
   margin 성분이 떨어지고(`Absent(InvalidRequest)`) **동시에** 개연 불가로 계수된다. 사용자 승인
   거동 D-4B6-4(`milestone-4.md` 4B-5 종결 절)를 이 slice 가 되돌리지 않는다. `1.00000`
   (R-FLOOR-03 라이브 47건)은 접기 가지이고 값·점수가 **그대로**이며 계수만 생긴다.
   **운영자 확인 항목**(종결 보고): DEC-10 acceptance 첫째의 「하한 미보고와 동일하게 취급」을
   `>1` 에서는 **성분 드롭**으로 읽었다 — `MarginInputs` 가 입력으로 받을 수 있는 범위(`≤ 1`)
   에서만 「접는다」가 성립하기 때문이다. r1 전 판은 이 자리를 「D-DEC10-9 가 승인한 변화」라
   적었는데 D-DEC10-9 는 팀장 결정이고 뒤집히는 쪽이 **사용자 승인** 거동이었다.
7. **7C② 표시 상태는 7C② 후속으로 인계한다** — **D-DEC10-16**(2026-10-28 팀장 결정, D-DEC10-10 을
   갈음). 인계 내용은 「개연 불가 · 심판 아님 사유를 하한율 근거 상태(`FloorRateGroundsState`)에
   표시」이고, 그 칸의 주인은 7C② 응답 계약이다. 이 slice 는 `openapi/**`·`web/**` 를 만지지
   않으며 `web` job 도 acceptance 에 없다. **그래서 오늘 그 사유는 화면에 보이지 않을 것으로 본다** — 7C② 의 `floorRateGroundsStateOf`
   가 `origin` 만 보고 밴드를 보지 않는다(`null` → `ABSENT`, `NoticeValue` → `USABLE`, 그 밖 →
   `ORIGIN_NOT_PUBLISHED`). **이 문장의 근거는 머지 전 7C② 브랜치를 읽은 probe 하나이고**
   (2026-10-28), 이 slice 는 그 코드를 돌리지도 재지도 않았다(위의 무편집·job 부재가 그 이유다).
   그러므로 「`1.00000` 이 `USABLE` 로 표시된다」는 **단정이
   아니라 인계 사항**이다: 7C② 가 머지된 뒤 그 분기가 그대로인지는 인계받는 쪽이 다시 확인한다.
   인계 범위는 그 `when` 분기 하나 + 생성 타입·OpenAPI 어휘다(같은 probe). —
   게이트는 가격 경로를 닫고 계수를 값으로 남기지만, 운영자가 그 제외를 화면에서 읽는 것은
   7C② 후속이 들어온 뒤다.
8. **변이 M3 에서 `1.00000` test 는 초록으로 남는다** — 그 값은 접어도(`null`) 안 접어도
   (`floor == 1` 가지) `floorHeadroom` 이 산술로 **같은 값**이다. 그 자리의 판별력은 `0.2999`·`1.2`·
   법정표가 지고 둘 다 M3 에서 RED 다(`1.2` 는 D-DEC10-12 뒤 **다른 가지**라 M3 의 표적이 아니고
   변이 M9 가 잰다). 「값이 같고 사유만 다르다」가 R-FLOOR-03 의 요점이므로 이것은 결함이 아니라
   그 test 의 범위다.
9. **투찰가 clamp 축은 이 slice 밖** — 「예측 비율이 하한 미달일 때 점수를 clamp 하는가」
   (`OPEN-DEC02-SCORE-AXIS-FLOOR`)는 다른 질문이고 승인된 명세가 없다.
10. **DEC-04 test 셋의 입력 값이 바뀌었다** — 앞 판은 하한율 `3`(300%)으로 비종결 몫을 만들었는데
    그 값은 개연 밴드 밖이라 통과 값이 될 수 없다. 밴드 **안** 값(`0.3`·`0.9`)으로 같은 성질
    (자리수·모드가 실제로 소비된다)을 잰다 — 명제는 그대로이고 기대값만 그 입력에 맞춰 옮겼다.

11. **4B-5 경계 셋(`0.0`·`1.0`·`1.2`)의 처분** — D-DEC10-24(운영자 결정). 셋 다 **커널에 남고**
    이름·문면이 「게시값의 거동 주장」에서 「모듈 안 방어층」으로 바뀌었다. 같은 세 값의 종단
    주장(거부 · margin 드롭 · 양성 대조)은 `FloorBoundaryAtGateTest` 가 **더** 잰다 — 옮긴 것이
    아니다. 지운 것은 `floorHeadroomOf` 의 `floor.signum() == 0` 특례 분기이고 근거는 도달
    불가가 아니라 **일반식이 같은 값을 낸다**는 등식이다(「일반식 등식」 test 가 계속 잰다).
    `floor = 1` 분기(분모 0 가드)와 `init` 의 `≤ 1` 술어는 **남는다** — 모듈 안에서 그 값을 지을
    수 있다. 4B-5 의 evidence 는 무편집이다.

12. **허용 목록 재생성은 그 자체로 우회가 될 수 있다** — CR r4 M-1. `tools/floor-trust-allowlist.py`
    가 관측 집합을 그대로 허용 목록 형태로 내므로, 통째로 덮으면 등식은 언제나 초록이고 새로
    생긴 소비자가 그 덮기에 숨는다. 그래서 재생성 뒤에는 **키 단위 diff** 를 보고 **추가된 키마다
    그 자리를 evidence 에 한 줄로** 적는다(집합 크기 비교만으로는 「하나 늘고 하나 줄었다」가
    보이지 않는다). 도구가 그 규율을 머리글에 적고 빈 출력에 비영 종료하지만, **규율을 집행하는
    것은 사람**이다 — 그래서 이것은 게이트가 아니라 제한이다.
    도구 자신의 실패 모드도 r5 에 하나 있었다(CR r5 M-1): 앞 판은 kotest 의 `expected:<[…]>`
    줄을 읽어 **출력 한도 20 에서 잘린 부분집합**을 조용히 냈다(exit 0). 지금은 `shouldMatchAllowed`
    의 clue `+`/`-` 줄을 읽고 **선언된 개수와 줄 수를 대조**해 어긋나면 비영 종료한다.
    **저장소 루트의 `.py` 는 lint·format·type 게이트를 하나도 지나지 않는다**(CR r5 L-3) —
    `ruff check .`·`ruff format --check`·`mypy --strict` 는 CI 에서 `ml-engine` 작업 디렉터리에서
    돌고 `tools/` 는 그 범위 밖이다(루트 `tools/` 의 유일한 `.py`). 그래서 이 파일의 회귀는
    **쓰는 사람이 돌려 보는 것**으로만 잡힌다 — 그 실측을 `commands.md` 가 적는다.

13. **② 하류(`measureFloorShortfall`)는 게이트 타입을 받지 않는다** — verifier r5 M-1. ②의
    진입(`criticalAssessmentRateFor`)은 `PlausibleFloorRate` 를 받지만, 그 결과를 소비하는
    `measureFloorShortfall(critical: Derived<AssessmentRate>)` 는 **이미 계산된 임계**를 받는다 —
    그 값은 하한율의 개연 여부를 더는 담지 않으므로 이 자리를 타입으로 닫을 수 없다. 그래서
    ②의 폐쇄는 두 다리로 선다: (a) 원값 진입인 shared-kernel `criticalAssessmentRate` 를 쓰는
    코드 단위가 census ⓑ·ⓑ′ 에 **전부 남는다**(verifier 가 영속 `BigDecimal` → `FloorRate` 꼴과
    소거 컨테이너 꼴을 둘 다 심어 RED 를 실측했다) · (b) 다른 생산자
    `YegaAmount.assessmentRateAgainst` 는 **런타임 전건을 못 만족한다**(그 타입의 VAT 가 항상
    UNKNOWN 이라 같은-VAT 술어가 거짓 → 언제나 `Unmeasurable`). verifier 가 (b) 꼴을 심었을 때
    **아키텍처 게이트는 전부 초록**이었고 값이 나오지 않아 작동하는 우회가 아니었다 — 그 자리가
    **타입이 아니라 생산자 전건에 기대는 곳**이다. `assessmentRateAgainst` 가 VAT 를 알게 되는
    날 이 제한이 깨진다; 그때 ② 하류도 게이트 타입을 받아야 한다. KDoc 이 같은 사실을 적는다.

## 수정 라운드가 만든 새 파일 ↔ in_scope 대조 (r2~r5)

표가 둘로 쪼개져 같은 파일이 두 번 적혀 있었다(CR r4 LOW) — 하나로 합치고 라운드 칸을 둔다.

| 라운드 | 새 파일 | in_scope 근거 |
| --- | --- | --- |
| r2 | `app/src/test/kotlin/bidvector/app/architecture/FloorTrustRules.kt` | 「`app/**` 의 … architecture test」 |
| r2 | `app/src/test/kotlin/bidvector/archfixture/violating/floortrust/RogueFloorFractionReader.kt` · `RogueFloorRateCarrier.kt` | 같은 항(음성 fixture 루트) |
| r2 | `workflow/src/test/kotlin/bidvector/workflow/evaluation/FloorExclusionChainTest.kt` | 「`workflow/**/evaluation/**`」 |
| r3 | `app/src/test/kotlin/bidvector/app/evaluation/FloorExclusionTallyTest.kt` | 「`app/**` 의 평가 집계 줄과 architecture test」 |
| r4 | **없음** | 기존 파일의 술어·허용 목록만 바뀌었다 |
| r5 | `workflow/src/test/kotlin/bidvector/workflow/evaluation/FloorBoundaryAtGateTest.kt` | 「`workflow/**/evaluation/**`」 |
| r5 | `tools/floor-trust-allowlist.py` | **계약 갱신 D-DEC10-25** — 앞선 in_scope 열거에 `tools/` 가 없었다 |
| r5 일괄 | `decision/src/test/resources/compile-fixtures/mutant-12-margin-entry-with-bigdecimal-typo.kt.txt` | 「`decision/src/test/resources/compile-fixtures/`」(design §3) — fixture 12 의 오타 변이 쌍둥이(CR r5 L-1) |

**r5 가 계약을 갱신한다** — `tools/` 가 in_scope 에 없던 자리이고 D-DEC10-25 가 그것을 맞춘다.
r2~r4 의 다섯은 기존 in_scope 항 안이었다. 새 정책 키 넷(`floor-trust.raw-type-access-pairs` ·
`floor-trust.creation-code-units` · `floor-trust.price-entry-types` ·
`floor-trust.price-entry-creation-code-units`)은 「`config/quality/**` 의 필요한 키」 안이다.
r5 가 `config/quality/gate-tests.properties` 에 새 test 클래스를 **만드는 커밋에서** 등재했다.

## 정책 version 의 근거

| 값 | 근거 |
| --- | --- |
| 개연 밴드 `[0.30, 0.995]` 양 끝 포함 | 운영자 결정 2026-10-24(D-DEC10-1). 실데이터 근거는 `data-dictionary.md` §1.4.3 **B6** · `capability-map.md` DEC-10 분류 근거 — 상한이 1 미만인 이유는 하한율 `1.00000` 라이브 47건(열린 공고 32건)이 「예정가 전액 이상 투찰」이라 하한으로 성립하지 않기 때문이고, 하한을 더 올리지 못하는 이유는 0.5 미만 3건이 표기 형태상 진짜 게시값과 구분되지 않기 때문이다 |
| Python 백테스트의 같은 밴드 | **무편집**(사전 등록 실험). 두 자리를 값 일치 test 가 묶는다 — 한쪽만 움직이면 백테스트가 제외한 공고와 운영이 제외하는 공고가 갈린다 |
| fixture 변경 | **없다** — `fixtures/` 무편집(`golden-manifest.json` 해당 없음). corpus 의 하한율은 전부 밴드 안(`0.875`)이고, conformance 실행자가 그 사실을 게이트로 확인한다 |
| 계수 이름 두 층 | acceptance(`capability-map.md` DEC-10 셋째)는 `floor_implausible`, 로그 줄은 `floorImplausible` — **같은 계수**다(D-DEC10-15). 줄의 다른 필드 전부가 camelCase 라 한 줄 안에서 표기가 갈리지 않게 했고, 운영자 문서 어휘는 이 slice 가 고치지 않는다 |

## 리뷰 요청 조건

| 항목 | 상태 |
| --- | --- |
| 구현 diff 커밋·clean tree | `commands.md` 의 clean-tree 판정(양성 대조 포함) |
| acceptance 전건 exit 0 | **`5099b8bb`**(r5 의 마지막 산출물 커밋)에서 `./tools/one-command-check.sh` exit **0** — `commands.md` 의 S-20(r5 CR) 절이 마침 줄을 적는다. 앞 라운드들의 `check` 전건도 같은 절에 있다 |
| 변경된 정책 version 근거 | 위 표 |
| 알려진 제한·rollback | 이 문서 · `rollback.md` |
| 비밀값 스캔 | `commands.md`(참조형) |
| 재작업 누계 | **5/5 — 상한 도달** — **수정 라운드 수**로 센다(팀장 결정 2026-10-28 · D-DEC10-21). r1·r2·r3·r4·r5 다섯 라운드. r5 표적 재검증에서 또 HIGH 가 나오면 새 라운드를 열지 않고 D-DEC10-23 대로 **게이트 분리 종결 + OPEN 신설**로 전환한다 |
| `differential.json` | **N/A** — Python 거동과 대조하는 case 를 이 slice 가 만들지 않는다. 밴드 값의 양쪽 일치는 값 일치 test 가 코드로 잰다(문서 판정표가 아니다) |
| `golden-manifest.json` | **N/A** — `fixtures/` 무편집 |

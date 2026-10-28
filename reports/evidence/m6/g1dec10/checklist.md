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
| 8 | margin 이 `rate` 를 다른 함수로 읽기 | ③ **진입 타입**이 게이트 산출물이고 그 입력 타입의 생성자·`copy` 가 `internal` 이다 — 모듈 밖에서 날 율·숫자로 입력을 지을 수 없다(D-DEC10-22) | 컴파일 fixture `10`·`10b`·`11`·`12` + 오타 변이 둘 · ⓓ 등식 |
| 9 | 게이트를 부르고 결과를 **버리고** 원값을 쓰기 | 원값의 멤버를 만지는 **코드 단위**가 ⓑ′ 에 전부 남는다 — 같은 파일에 함수를 더해도 집합이 바뀐다. 다만 **같은 코드 단위 안에서** 버리는 것은 구조로 닫지 못한다(제한 ②) | 변이 M3 RED · N1·N2 RED |
| 10 | 리플렉션·직렬화로 통과 값 생성 | `decision` 은 도메인 모듈이라 직렬화 표면 0(`domainDependencyGate`) · 리플렉션 뿌리 게이트가 production 전 층 | 아래 알려진 제한 ③ 이 남는 범위를 적는다 |
| 11 | **위조한 밴드**를 진짜 게이트에 넘기기 — 타입 **밖** 조립, 같은 파일 둘째 함수, 그리고 **정책 타입 자신의 메서드**(`widened()`·`withBand()`) | ⓒ 정책 값 생성 코드 단위 등식(자기 제외 없음) — production 에서 정책 값을 만드는 자리는 출하 초기화자 하나와 컴파일러가 만든 자기 `copy` 계열뿐이다 | `FloorTrustGateTest` ⓒ·ⓒ′ · 변이 **B1**·**c1** RED |
| 12 | 원값을 **다른 표현으로 세탁**해 가격 경로에 넣기(`toString()` 정규식 · 영속 행의 `BigDecimal` · JSON) | ⓑ·ⓑ′ census 로는 **닫히지 않는다**(타입을 따라가므로 표현이 바뀌면 안 보인다 — r4 실측). 닫는 것은 **③ 진입 타입**이다: 세탁한 숫자는 게이트 산출물이 될 수 없고, 진입이 그 타입만 받는다 | 컴파일 fixture `11`(율)·`12`(`BigDecimal`) · 변이 `lnd` 가 **컴파일 단계에서** 멈춘다 |

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
   않으며 `web` job 도 acceptance 에 없다. **그래서 오늘 그 사유는 화면에 보이지 않는다** — 7C② 의 `floorRateGroundsStateOf` 가
   `origin` 만 보고 **밴드를 보지 않기 때문**이다(`null` → `ABSENT`, `NoticeValue` → `USABLE`,
   그 밖 → `ORIGIN_NOT_PUBLISHED`). 그래서 `1.00000` 같은 개연 불가 값도 `USABLE` 로 표시된다.
   인계 범위는 그 `when` 분기 하나 + 생성 타입·OpenAPI 어휘다(병합 probe 실측). —
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

## 수정 라운드가 만든 새 파일 ↔ in_scope 대조 (r2·r3·r4)

| 새 파일 | in_scope 근거 |
| --- | --- |
| `app/src/test/kotlin/bidvector/app/architecture/FloorTrustRules.kt` | 「`app/**` 의 … architecture test」 |
| `app/src/test/kotlin/bidvector/archfixture/violating/floortrust/RogueFloorFractionReader.kt` · `RogueFloorRateCarrier.kt` | 같은 항(음성 fixture 루트) |
| `workflow/src/test/kotlin/bidvector/workflow/evaluation/FloorExclusionChainTest.kt` | 「`workflow/**/evaluation/**`」 |

| `app/src/test/kotlin/bidvector/app/evaluation/FloorExclusionTallyTest.kt` (r3) | 「`app/**` 의 평가 집계 줄과 architecture test」 |
| `workflow/src/test/kotlin/bidvector/workflow/evaluation/FloorExclusionChainTest.kt` (r2) | 「`workflow/**/evaluation/**`」 |

**r4 는 새 파일 0** 이다(기존 파일의 술어·허용 목록만 바뀌었다).

**계약 갱신 없음** — 여섯 다 기존 in_scope 항 안이다. 새 정책 키 둘(`floor-trust.raw-type-access-pairs` ·
`floor-trust.creation-code-units`)도 「`config/quality/**` 의 필요한 키」 안이다.

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
| acceptance 전건 exit 0 | `commands.md` — `check` 전건 |
| 변경된 정책 version 근거 | 위 표 |
| 알려진 제한·rollback | 이 문서 · `rollback.md` |
| 비밀값 스캔 | `commands.md`(참조형) |
| 재작업 누계 | **4/5** — **수정 라운드 수**로 센다(팀장 결정 2026-10-28, 이 프로젝트 선례). r1·r2·r3·r4 네 라운드. 상한 5 |
| `differential.json` | **N/A** — Python 거동과 대조하는 case 를 이 slice 가 만들지 않는다. 밴드 값의 양쪽 일치는 값 일치 test 가 코드로 잰다(문서 판정표가 아니다) |
| `golden-manifest.json` | **N/A** — `fixtures/` 무편집 |

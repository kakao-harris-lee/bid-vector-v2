# M9/gates-tests checklist

## 1. 설계 검토 — 술어마다 경계 한 문장 · 우회

### 항목 1 — persistence 의존 게이트 (`OPEN-PERSISTENCE-GATE-PREDICATE-TYPE`)

**경계**: `adapters.persistence` 의 **컴파일된 class** 가 허용 루트 넷 밖의 `bidvector` 좌표를
상수 풀에 두지 않는다. **재지 않는 것**: 다른 패키지·모듈 · 컴파일 시 인라인되는 `const val` ·
값이 흐르는 경로 일반.

**열거인가 구성인가**: 금지 목록(층 밖 domain 루트 여섯 열거) → **허용 목록**. 열거되지 않은
새 domain 모듈이 조용히 통과하는 자리가 사라진다. 새 public 표면 없음(test 내부 술어).

| 우회 | 막히는가 |
| --- | --- |
| import 없이 전체 한정 좌표로 층 밖 domain 참조 | **막힘** — 이 slice 가 닫은 그 자리(변이 실측, commands) |
| 허용 루트의 **이름 접두**를 공유하는 이웃 패키지 | 막힘 — 세그먼트 경계 단언 |
| `Class.forName("bidvector.decision.X")` — **리터럴** 좌표 | 막힘 — 정규식이 점 표기도 본다 |
| 같은 reflection 인데 이름을 **짜 맞추거나** 바깥에서 받기 | **안 막힘**(v-134 L-3 · verifier 변이 ⓑ 실측) — 상수 풀에 조각(`"bidvector."`)만 남아 좌표가 성립하지 않는다. 위 행의 주장은 **리터럴 전체 좌표 한정**이고, 가공된 좌표는 경계 문장의 「값이 흐르는 경로 일반」에 속한다 |
| 허용 루트로 **시작하는** 한 토큰 안에 금지 좌표를 잇기 | **안 막힘**(cr LOW) — 문자 클래스에 `/`·`.` 이 있고 `findAll` 이 비겹침이라 `…persistence.Foo.bidvector.qualification.Bar` 가 한 토큰으로 삼켜진다. **실효 위험은 낮다**(실제 코드 참조는 컴파일러가 `Class`/`Methodref` 항을 따로 내 숨길 수 없다) — 틀린 것은 술어가 아니라 앞 판 문면이었다. 형제 여덟과 공유 |
| 허용된 형제 `adapters.snapshot` 을 **경유**해 층 밖에 닿기 | **안 막힘**(v-134 L-2 · verifier 변이 ⓒ 실측, 아래 「알려진 제한」) |
| typealias · 제네릭 상한 · **public inline 함수** 경유 | 막힘 — verifier 변이 실측(inline 은 SMAP·합성 class 로 드러난다) |
| `const val` 이 호출부로 인라인돼 참조를 남기지 않음 | **안 막힘** — 형제 여덟과 공유하는 등재된 사각(`OPEN-BYTECODE-GATE-CONST-VAL-BLINDSPOT`) |
| 코드를 persistence 밖 새 패키지로 옮겨 거기서 호출 | 이 test 의 모집단 밖 — 형제가 각자 자기 패키지를 보고, 새 패키지는 `packageOwnershipGate` 와 등재가 받는다 |
| 산출물 디렉터리를 비워 「위반 없음」으로 만들기 | 막힘 — 디렉터리 존재 `check` + 비지 않음 단언 |
| 등재에서 빼기 | 막힘 — `gateRegistrationGate` 양방향 등식 |
| 허용 목록에 루트를 더하기 | **구조적으로 열려 있다** — 게이트 자체를 바꾸는 일이라 리뷰가 받는다 |

### 항목 2 — CHECK 본문 단언 (`OPEN-CHECK-BODY-PRESENCE-ASSERTIONS`)

**경계**: `collection_run` 의 CHECK 본문 **집합**과, `notice_round` 형식 CHECK 를 지닌 **여섯 표**
각각의 그 본문이 정확히 고정된다. **재지 않는 것**: 그 밖의 표의 다른 CHECK(각자의 test) · 거동.

| 우회 | 막히는가 |
| --- | --- |
| 제자리 `OR TRUE` 로 항진명제화 | 막힘 — 본문 문자열 자체가 바뀐다(옛 존재 단언은 통과했다) |
| 문자 클래스·범위를 좁히기 | 막힘 — 같은 이유 |
| 같은 본문을 공유하는 **여섯 중 다섯**을 약화 | **막힘** — 단언 단위를 스키마에서 표로 내렸다(옛 술어는 하나만 살아도 초록) |
| 제약을 삭제 | 막힘 — 집합 등식(`collection_run`) · 0 행 명시 실패(`notice_round`) |
| 제약을 추가 | 막힘 — 집합 등식 + 기존 개수 축 |
| 본문이 같은 CHECK 둘(집합이 하나로 접는다) | 막힘 — 개수 축이 센다(집합 혼자서는 못 센다) |
| 같은 이름 제약을 다른 표에 두고 먼저 오게 하기 | 막힘 — 중복 이름 명시 실패 |
| 마이그레이션이 아니라 기대값을 고치기 | **구조적으로 열려 있다** — 게이트 자체 변경 |

### 항목 4 — 감시 port 필드 축 (`OPEN-6F4W-UNAVAILABLE-PRODUCER`)

**경계**: `NoticeWatchSubjectPort`·같은 파일의 file-class 두 class 의 `Fieldref` **전수**가 허용
목록(원소 하나)과 같다. **재지 않는 것**: 다른 class · 그 밖의 상수 풀 축(메서드 축이 따로 본다).

| 우회 | 막히는가 |
| --- | --- |
| `subjectFor` 가 `Unavailable` 을 반환 | **막힘** — production 파일 변이로 실측. **메서드 축은 같은 변이에 초록**(음성 대조) |
| 사유 상수를 다른 `ReasonCode` 로 교체 | 막힘 — 좌표가 바뀌어 허용 목록 하나를 벗어난다(D-6F4W-4 가 함께 잠긴다) |
| 다른 class 의 factory 를 불러 `Unavailable` 을 얻기 | 막힘 — 그 호출이 메서드 축 허용 목록 밖이다 |
| 같은 패키지에 **새 class** 를 만들어 거기서 생산 | **안 막힘** — 이 두 class 의 모집단 밖이고 패키지 단위 허용 루트는 `workflow.evaluation` 을 이미 허용한다(아래 「알려진 제한」) |
| 산출물 부재 | 막힘 — 파일 존재 `check` |
| 등재에서 빼기 | 막힘 — `gateRegistrationGate` |

## 2. OPEN 처분

| OPEN | 처분 |
| --- | --- |
| `OPEN-PERSISTENCE-GATE-PREDICATE-TYPE` | **닫힘 후보** — 술어를 상수 풀 허용 목록으로 이식, 변이로 양쪽 실측. 처방이 지목한 「술어 종류」는 바뀌었다. **신설 OPEN 후보 하나를 남긴다**(v-134 L-2): 패키지 단위 허용 목록이 **허용한 형제 패키지의 도달 범위를 물려받는 것** — 형제 여덟 전부가 공유하고 이 slice 가 만든 것이 아니다(§4) |
| `OPEN-CHECK-BODY-PRESENCE-ASSERTIONS` | **닫힘 후보** — 존재 단언 셋이 사라졌고 `any { contains }` 형태가 그 파일에 0 이다. 함께 받은 0 행 자리도 명시 실패로. 실 Postgres 원판·변이 둘 다 실측됐다(§4) |
| `OPEN-6F4W-UNAVAILABLE-PRODUCER` | **닫힘 후보 — 「불가」 처분**. 순수 전(total) 매퍼라 이 어댑터가 그 갈래를 낼 길이 없다(D-6F4W-3). 갈래 자체는 **지우지 않는다**: port 의 어휘이고 소비자 둘(후보 탈락·분석 정지)이 실제 분기를 진다. 대신 「낼 수 없다」를 산문에서 **구조**로 옮겼다 |
| `OPEN-VERDICT-CONSTRUCTION-VISIBILITY` | **열린 채 — 계약 갱신으로 이 slice 에서 분리**(팀장 결정 2026-11-06 ①). 받는 자리는 **testFixtures 개방 ADR slice**(`OPEN-1BC-TESTFIXTURES-GATE` 선행). 근거와 영향 16 파일 목록은 scope.md 계약 갱신 절 · §3 |
| 신설 OPEN | **후보 하나** — 위 행의 「형제 패키지 경유 간접 참조」. 식별자 발급과 등재는 M9 장부 레인이 받는다(이 slice 는 근거만 남긴다) |

## 3. 항목 3 분리 — 처방이 다른 차단과 충돌한다

**정본은 scope.md 의 「계약 갱신 2026-11-06」 절**이다(차단 근거 넷 · `associateWith` 필요성 ·
영향 16 파일 목록 · 받는 자리). 같은 내용을 두 자리에 두지 않는다 — 한 줄도 건드리지 않았다는
확인은 §5 가, D-6F5-20 실측이 맞다는 재확인은 verifier ④ 가 받는다.

## 4. 알려진 제한

- **항목 2 의 미실측은 해소됐다**(v-134 L-4). 기대 본문이 정적 유도였던 것이 이 slice 의 가장
  큰 미실측이었다. 팀장 승인으로 `CleanMigrationCheckTest` 를 로컬에서 **3회**(원판 · 변이 둘)
  돌렸고 **verifier 가 별개로 11/11 을 재확인했다**(PR #134 v-134). cr 가 유일한 미측정으로
  남긴 둘(제약 자동 이름 가정 · 산술 항등식 바깥 괄호)도 그 실행이 닫았다. 남는 한계는 **그 한
  클래스만 돌았다**는 것뿐 — 다른 persistence test 와 전건 `check` 는 PR CI 가 받는다.
- **허용된 형제 패키지를 경유하는 간접 참조는 이 축 밖이다**(v-134 L-2, **OPEN 후보**). 허용
  목록이 **패키지 단위**라 허용한 형제의 도달 범위를 물려받는다. 실측: 허용 루트 `adapters.snapshot`
  의 main 이 `bidvector.workflow.collection`·`adapters.koneps` 를 참조하고 **자기 의존 게이트가
  없다**. persistence 가 그 헬퍼로 층 밖에 닿으면 상수 풀에 `adapters.snapshot` 만 남아 초록인데,
  같은 좌표를 **직접** 참조하면 양성 대조가 잡는다. 형제 여덟이 **모두** 공유하는 구조적 한계로,
  닫으려면 술어를 전이 폐쇄로 바꾸거나 형제마다 게이트를 세워야 한다(다른 결정).
- 내 여섯 가운데 `notification_inbox` 하나는 알림함 privacy test 가 이미 고정한다. 단위가
  달라(그쪽은 알림함 한 표의 privacy 축, 이쪽은 여섯 표가 같은 본문을 공유한다는 주장)
  지우지 않았다 — 두 자리가 어긋나면 둘 다 붉어진다.
- 항목 1 의 `const val` 인라인 사각은 형제 여덟과 공유하는 등재된 OPEN 이고 이 slice 가
  새로 만든 것이 아니다. 앞 판의 소스 텍스트 축이 그 자리를 **덮고 있었던 것도 아니다**
  (그 축도 import 문만 봤다).
- 항목 4 의 필드 축은 **두 class 의 모집단**이다. 같은 패키지에 새 class 를 세워 거기서
  `Unavailable` 을 생산하는 경로는 이 축 밖이고, 패키지 단위 허용 루트가 그 좌표를 이미
  허용한다. 그 경로를 닫으려면 모집단을 패키지 전수로 넓혀야 하고 그것은 다른 결정이다.
- 등재부 문면 정정 하나를 함께 냈다 — 기존 문단이 `PersistenceAdapterDependencyTest` 를
  이미 「바이트코드 상수 풀 게이트」로 적었는데 그때는 소스 텍스트였다. 이제 참이 되고,
  같은 문단이 함께 드는 `EventAdapterDependencyTest` 는 **여전히 소스 텍스트**다(in_scope 밖).
- **`EvaluationAdapterDependencyTest` 가 483줄로 `sizeGate` 한도(500)에 17줄 남았다**(cr LOW) —
  이 slice 가 65줄을 더한 결과다. 통과하지만 다음 라운드가 그 파일에 손대면 거의 확실히 걸린다.
  처방은 `Recording*` 축을 형제 파일로 분리하고 등재부를 같은 커밋에서 갱신하는 것이다
  (`CleanMigrationTest` → `CleanMigrationCheckTest` 분리가 전례). **다음에 그 파일을 건드리는
  라운드의 선행 작업**으로 등재한다.
- **등재만 하는 cr LOW 셋**(cr 가 「지금 고칠 필요 없다」로 판정) — ⓐ 여섯 표가 표마다 JDBC
  connection 을 연다(`conname = ANY(?)` 로 줄일 수 있으나 지금 헬퍼의 0 행·중복 이름 명시 실패가
  그 비용보다 값지다) · ⓑ `javap` 를 class 하나당 서브프로세스로 띄우고 reader 를 닫지 않는다
  (**형제 다섯과 동일 형태** — 이 slice 의 회귀가 아니다) · ⓒ 필드 축 test 가 메서드 축 test 의
  class 쌍 목록을 되풀이한다(`productionClassFilePair()` 추출이 위 483줄 여유도 함께 늘린다).
- 항목 4 의 「`Unavailable` 갈래를 지우지 않는다」 판단은 cr 가 **운영자·verifier 몫**으로 남긴
  열린 질문이다(소비자 둘이 실제 분기를 진다는 전제는 in_scope 밖이라 재확인되지 않았다).

## 5. in_scope 대조

내 커밋 경로 전부 in_scope 안이다. 항목 4 의 필드 축을 더한 파일은 계약이 그 port 를
「(+test)」로 드는 **감시 port 게이트 test** 이고 이 읽기는 **팀장 승인**을 받았다(2026-11-06).
분리된 항목 3 의 흔적이 없음도 같은 목록이 보인다 — `qualification/**`·`build-logic/**`·
정책 파일이 내 커밋 경로에 **없다**.

**수정 라운드가 새로 만든 파일: 없다**(전부 기존 파일 수정). 새 test 클래스도 없어 등재부
추가가 없다 — 기존 class 에 `@Test` 추가와 문면 정정 하나뿐이고, 새 fixture 하나는 `@Test`
가 없어 등재 게이트 모집단 밖이다(같은 파일의 기존 fixture 둘이 선례).

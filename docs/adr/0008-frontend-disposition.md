# ADR 0008 — legacy React UI의 처분

- **상태**: **결정** — M0 slice 0D가 Codex `approve`로 닫혔다
  (`reports/evidence/m0/0d/codex-review-20260829T222217Z.json`, `reviewed_base` `998dc217` …
  `reviewed_head` `df056259`, 2026-08-29, `model_reasoning_effort=high`). 갱신 시점은
  **2026-08-30 · M0 종료 slice 0E**다 — 0D 종료 뒤에도 이 줄이 *"제안됨 … 대기"*로 남아
  **문면이 사실을 따라가지 않았다.**
  **`milestone-0.md` 완료 조건의 「사용자 명시 승인」은 아직 받지 않았다** — 그 승인의
  대상은 0E가 만든 이월 목록(`docs/discovery/capability-map.md` §14)이다.
- **작성일**: 2026-08-28
- **대응**: `milestone-0.md` §"Slice 0D" 결정 **8** (React UI 재사용/재작성/후속 여부)
- **legacy 기준 commit**: `ed4b06c`
- **관련 ADR**: 0003(계약) · 0004(스키마 호환) · 0001(두 runtime)

---

## 1. 맥락

### 1.1 legacy에는 완성된 프런트엔드가 있다

```
git -C bid-vector ls-tree -r --name-only ed4b06c | grep -cE '\.tsx?$|\.jsx?$'   → 292
```

`frontend/package.json`(`git show ed4b06c:frontend/package.json`)이 스택을 적는다 —
React 19 · Vite 8 · TypeScript 6 · react-router 7 · TanStack Query 5 · Tailwind 4 ·
zod 4 · recharts · react-hook-form, 테스트는 vitest + Testing Library, e2e는 Playwright.
빌드 타깃이 `user`와 `admin` 둘로 갈린다.

**결정적으로**, `frontend/package.json:14-15`에 이 스크립트가 있다:

```
    "sync-types": "python ../scripts/sync_openapi_types.py",
    "check:sync-types": "python ../scripts/sync_openapi_types.py --check",
```

`devDependencies`에 `openapi-typescript`가 있다(`frontend/package.json:48`). **legacy 프런트엔드의 타입은 Python
FastAPI의 OpenAPI 스키마에서 생성된다.** (재현: `commands.md` **C-5.4**)

### 1.2 승인된 문서가 UI에 대해 말하는 것

| 문서 | 문면 |
| --- | --- |
| `v2-지침서.md` §3 구조도 | *"React Web (**후속 또는 재사용 결정**)"* |
| `v2-지침서.md` §1 「가져오지 않는 것」 | *"기존 DB schema와 **public API의 자동 호환성**"* |
| `milestone-4.md` 「범위 밖」 | *"public API/UI"* |
| `milestone-6.md` Slice 6A | *"OpenAPI 단일 출처와 generated client/type"*, *"기존 FastAPI path/schema와의 호환은 **제품 요구로 승인된 항목에만** 적용한다"* |

### 1.3 capability map에는 UI 축이 없다

`docs/discovery/capability-map.md`는 8축(COL·STR·QUAL·ML·DEC·NOTI·SET·OPS)으로 구성돼
있고 **UI 축이 없다.** `React` 문자열의 매치가 0이다(`commands.md` **C-9.1**).
§12 활성 OPEN 목록에도 **UI를 소유한 항목이 없다.**

**즉 UI에는 승인된 사용자 가치도 acceptance scenario도 없다.** `milestone-0.md`의 완료
조건 *"V2 필수 capability마다 사용자 가치와 acceptance scenario가 있다"*를 UI가 만족할
방법이 현재 없다.

### 1.4 그런데 화면을 전제하는 `V2 필수` capability가 있다

- **STR-16**(입찰 목록 검색과 투찰가 요청)은 pull 모델에서 **운영자의 주 경로**로
  신설·승격됐다(`capability-map.md` §0.7).
- **NOTI-10**(앱 알림 중복 억제)은 *"앱 알림함이 **기록이자 회수 경로**로 확정"*되면서
  `V2 필수` 유지·강화됐다(같은 절).

**이 둘은 사람이 보는 화면을 전제한다.** 그럼에도 `milestone-1.md`~`milestone-6.md`
어디에도 UI slice가 없다(`commands.md` **C-9.2**).

---

## 2. 결정

### D-1. legacy React 앱의 **재작성**을 V2 마일스톤 범위에 넣지 않는다

근거: `milestone-1.md`~`milestone-6.md`에 UI slice가 없고, `milestone-4.md`가 UI를
**범위 밖**으로 명시한다. **이 결정은 승인된 마일스톤 문서로부터 도출된 것이며 이 ADR이
새로 좁히는 것이 아니다.**

### D-2. **"그대로 재사용"은 M0에서 확정할 수 없다**

- legacy 프런트엔드의 타입은 **legacy FastAPI의 OpenAPI 스키마에서 생성**된다(§1.1).
  따라서 "그대로 재사용"은 **V2가 legacy public API 계약을 재현한다**는 뜻이 된다.
- 그것은 `v2-지침서.md` §1의 *"public API의 자동 호환성을 가져오지 않는다"*와 정면으로
  만나고, `milestone-6.md` 6A가 그 호환을 *"제품 요구로 **승인된 항목에만**"*으로 한정한다.
- **따라서 재사용 가부는 V2의 OpenAPI 계약이 정해진 뒤에 판정된다.** 그 계약은
  `milestone-6.md` 6A의 산출물이며 M0에는 없다.

### D-3. 분류는 `후속`이며, 재사용 가능성을 닫지 않는다

`v2-지침서.md` §3이 열어 둔 두 선택지 중 **`후속`**을 택한다. 이것은 *"만들지 않는다"*가
아니라 *"V2 초기 범위 밖이고 backlog로 둔다"*이다(`capability-map.md` §0.3의 분류 어휘).

**재사용을 배제하지 않는다** — D-2대로 판정 시점이 뒤이기 때문이다.

### D-4. 판정 시점과 그때 볼 것을 지금 예약한다

`milestone-6.md` 6A가 OpenAPI 단일 출처를 확정할 때 **다음을 함께 판정한다**:

1. V2 계약이 legacy 화면들의 데이터 요구를 덮는가.
2. 덮지 못하는 화면이 있다면, 그것은 **legacy에만 있던 요구**인가 **V2가 누락한 capability**인가.
3. legacy 프런트엔드를 재사용하려면 어떤 어댑터(타입 재생성, 경로 매핑)가 필요하고,
   그 어댑터가 **V2 도메인을 왜곡하지 않는가**.

**3번이 게이트다.** `v2-지침서.md` §1: 화면을 붙이기 위해 V2 API를 legacy 형태로
되돌리는 것은 재작성의 목적(유지보수성·회귀 감소)을 무효로 만든다.

### D-5. 화면을 전제하는 capability의 acceptance는 UI 없이도 관찰 가능해야 한다

§1.4의 STR-16·NOTI-10은 `V2 필수`인데 UI가 `후속`이다. **이 ADR은 그 둘의 acceptance가
UI 없이 관찰 가능한지 판정하지 않는다** — capability의 acceptance는
`docs/discovery/capability-map.md`가 소유하고 이 slice의 `out_of_scope`다.

**여기서 정하는 것은 하나다**: UI가 `후속`이라는 사실이 그 capability들을 `후속`으로
끌어내리지 않는다. 그 둘의 가치는 **API 경계에서 관찰 가능**해야 하며, 그렇지 않다면
그것은 UI 결정이 아니라 **capability acceptance의 문제**다. 확인은 **0C 데이터 사전과
`milestone-6.md` 6A**가 한다.

---

## 3. 대안

| # | 대안 | 판정 | 사유 |
| --- | --- | --- | --- |
| **A-1** | **V2에서 UI를 재작성한다** | **불채택 (D-1)** | 마일스톤 전수에 UI slice가 없고 `milestone-4.md`가 범위 밖으로 명시한다. 더해서 §1.3대로 **승인된 사용자 가치와 acceptance가 없다** — `milestone-0.md` 완료 조건을 만족할 근거가 없는 채로 범위에 넣는 것은 *"'향후 결정'으로 핵심을 숨긴 채 완료 처리"*의 반대 방향이 아니라 **범위를 근거 없이 넓히는 것**이다 |
| **A-2** | **legacy React 앱을 그대로 재사용한다** (지금 확정) | **불채택 (D-2)** — 배제가 아니라 **판정 시점이 아님** | §1.1대로 타입이 legacy OpenAPI에서 생성된다. 지금 "재사용"으로 확정하면 **V2 public API가 legacy 계약을 재현해야 한다는 제약을 M0에서 걸어 버린다** — `v2-지침서.md` §1과 `milestone-6.md` 6A가 명시적으로 열어 둔 자리를 닫는다 |
| **A-3** | **`후속`으로 두고 재사용 가능성도 닫는다** | **불채택** | 닫을 근거가 없다. §1.2의 지침서 구조도가 두 선택지를 함께 열어 두었고, 어느 쪽도 배제된 바 없다 |
| **A-4** | **legacy 앱을 legacy 백엔드와 함께 병행 운영한다** (V2 기간 중) | **판정하지 않음 — `OPEN-ADR-09`** | 이것은 아키텍처 결정이 아니라 **운영자가 V2 기간 중 무엇으로 일하는가**의 문제다. 근거가 이 조사 범위 밖이며 운영자만 답할 수 있다 |
| **A-5** | **CLI·API만으로 운영한다** (화면 없음) | **판정하지 않음 — `OPEN-ADR-09`** | 같은 이유. §1.4가 화면을 전제하는 `V2 필수` capability를 보이므로, "화면 없음"은 그 capability들의 사용 방식에 대한 운영자 결정을 요구한다 |

---

## 4. 결과

- **V2 마일스톤의 산출물에 UI가 없다.** M6 6A의 **OpenAPI 계약과 generated client/type**이
  외부에 노출되는 유일한 표면이다.
- **D-4의 판정이 M6 6A의 할 일 목록에 추가된다.** 그 slice가 계약을 정하면서 legacy 화면의
  데이터 요구를 함께 본다.
- **legacy 프런트엔드는 `bid-vector` symlink 아래의 읽기 전용 참조로 남는다.**
  `CLAUDE.md`와 `README.md`가 그 저장소를 수정하지 않는다고 규정한다.
- **운영자의 실사용 경로가 미정으로 남는다**(`OPEN-ADR-09`). 이것은 이 ADR이 만든
  공백이 아니라 **드러낸 공백**이다 — capability map에 UI 축이 없다는 사실(§1.3)의 귀결이다.

---

## 5. 이 ADR이 등록하는 `OPEN`

### `OPEN-ADR-09` · V2 기간 중 운영자는 어느 화면으로 시스템을 쓰는가

- **결정 필요 사항**: V2가 M1~M6을 진행하는 동안, 그리고 M6 완료 시점에, 운영자는 무엇으로
  공고를 보고 투찰가를 요청하는가.
- **선택지**: (a) legacy 앱을 legacy 백엔드와 함께 계속 쓴다 (b) V2 API를 직접 쓴다
  (CLI·스크립트·API 클라이언트) (c) M6 6A 계약이 정해진 뒤 legacy 앱을 V2에 재접속한다
  (d) UI 재작성을 별도 마일스톤으로 신설한다.
- **왜 운영자만 답할 수 있는가**: 이것은 코드 구조가 아니라 **운영 방식**이고,
  `capability-map.md`에 UI 축이 없어 조사가 근거를 만들 자리가 없다.
- **파급**: (d)를 택하면 D-1이 바뀌고 마일스톤 목록이 늘어난다. (a)를 택하면 legacy
  백엔드의 운영 기간이 V2 완료 시점까지로 확정되고, `milestone-6.md` 6C의 컨테이너 구성과
  전환 계획에 영향한다.
- **소유**: 운영자.

---

## 6. 확인하지 않은 것

- **legacy 화면 292개가 어떤 데이터를 요구하는지 조사하지 않았다.** §1.1에서 확인한 것은
  파일 수와 스택과 **타입 생성 경로**뿐이다. 화면별 데이터 요구의 조사는 D-4가 M6 6A로
  예약한 일이다.
- **`scripts/sync_openapi_types.py`의 동작을 열어 보지 않았다.** `package.json`의 스크립트
  선언과 `openapi-typescript` 의존에서 **타입이 OpenAPI에서 생성된다**는 것까지만 확인했다.
- **legacy 프런트엔드의 테스트가 어떤 계약을 고정하는지 조사하지 않았다**(vitest·Playwright
  파일의 존재만 확인).
- **UI 재작성의 비용을 추정하지 않았다.** A-1의 기각 근거는 승인된 마일스톤 범위와
  근거 부재이지 비용 추정이 아니다.

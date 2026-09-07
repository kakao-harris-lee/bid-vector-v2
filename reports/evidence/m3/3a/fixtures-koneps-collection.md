# M3/3A — `koneps-collection` corpus evidence (curator 레인)

base `a9f1ff9c54b62fb7cfa859fff43dae7b943daf8f` · 2026-09-07 · 별도 세션(curator).
경로: `fixtures/**` · `reports/evidence/m3/3a/policy-values.md` · 이 파일. 다른 레인 경로 무편집.

**정본은 `fixtures/manifest.yaml`** — case 별 `verifies` · `source` · `expected_reasoning` ·
`classification` · 해시가 거기 있고 이 파일은 **실행과 판정**만 남긴다.

---

## 1. 실행 명령과 종료 코드

| # | cmd | exit | 핵심 결과 |
| --- | --- | --- | --- |
| F-1 | `python3 fixtures/tools/mutation_sweep_adversarial.py` | 0 | 강등 대상 0 · 잔존 authoritative 50 |
| F-2 | `python3 fixtures/tools/mutation_sweep_adversarial.py --crosscheck-pyyaml` | 0 | pyyaml crosscheck OK — cases 91 |
| F-3 | `python3 fixtures/tools/mutation_sweep_targeted.py` | 0 | 표기 변형 갈래 전부 caught |
| F-4 | `python3 fixtures/tools/check_legacy_numbers.py` | 0 | legacy-number hits 0 |
| F-5 | `python3 fixtures/tools/manifest_prose_consistency.py` | 0 | 불일치 5/6 — **기준선과 동일**(F-6) |
| F-6 | 같은 명령을 이 slice **이전** manifest 사본에 | 0 | 같은 5/6 — 이 slice 가 새 불일치를 만들지 않았다 |
| F-7 | manifest 선언 해시 대 파일 실측 전수 대조(인라인 python) | 0 | 184건 대조, 불일치 0 |
| F-10 | 활용가이드 docx 원문에서 에러코드 표 **독립 재추출** 후 인수본과 대조 | 0 | 16행 일치 · SHA-256 `afba38e6…` 일치 |

> **F-1 이 분류를 정한다.** `authoritative` = 확장 적대 집합에서 위반 변이체 통과 0 이고, 종료 코드가
> 곧 판정이다(`data-extract.md` §6). 신설 authoritative 9 건은 `mutation_sweep_adversarial.ASSERTED`
> 에 등재해 **변이체가 실제로 생성되게** 했다 — 등재 없이 `verified_paths` 만 두면 변이체가 0 이라
> 「강등 대상 0」이 공허해진다(verifier r2 N-5 가 잡은 형태).

**F-8 secret 스캔** — `grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))"`
를 `fixtures/` 와 `reports/evidence/m3/3a/` 에. **exit 0(매치 있음)이고 전부 판독상 무해다.**

- **`token` 이 상시 false-positive 바닥이다** — 도메인 어휘(`equalToUnpaddedToken` ·
  `tokenComparison` · `OTHER_TOKEN` · `token_alignment`)와 「basis 토큰」 산문이 잡힌다.
  신설 fixture 안의 매치는 `koneps-collection-012` 의 두 키뿐이다.
- **`token` 을 뺀 나머지 패턴의 매치는 이 문서와 `commands.md` 가 인용한 grep 패턴 자신**이다
  (검사가 자기 문면을 스캔하는 자리 — `codex-review-gate` 의 누출 검사 판독 규칙과 같은 갈래).
- **육안 확인**: 서비스 키·Telegram id·사업자 등록번호·개인 성명·연락처가 신설 fixture 에 없다(§4).

---

## 2. 산출물

| 축 | 수 |
| --- | --- |
| 기존 case 재추출·정합 | 9 |
| 신설 case | 18 (`koneps-collection-010` ~ `-027`) |
| 그중 `authoritative` | **10** (`-010` ~ `-018` · `-027`) |
| 그중 `insufficient-evidence` | 8 (`-019` ~ `-026`) |
| 도메인 `authoritative` 수 | 0 → **10** |
| corpus 전체 `authoritative` 수 | 41 → **51** |

**3A `scope.md` ⑩ 의 조건이 충족됐다** — runner dispatch 의 전건이 「authoritative ≥ 1」이다.

---

## 3. 층 판정의 근거

### 3.1 `authoritative` 9 건 — 조달청 공식 문서 인용

`fixtures/manifest.yaml` 의 `official_documents` 항목 `koneps-openapi-reference` 가 문서를 고정한다
(경로 · legacy blob id · SHA-256 · 열람일). `classification_policy.authoritative` **①(조달청 공식 문서
인용)** 이 이 층의 자리이고, **2026-08-31 운영자 결정(m0-derived-rule 제외)의 대상이 아니다** — 그
결정이 내린 것은 「M0 산출 문서가 스스로 도출한 규칙」이고 여기 근거는 원문 인용이다.

각 case 가 문서에서 가져오는 것:

| case | 문서가 떠받치는 주장 |
| --- | --- |
| `-010` | 율의 **단위 선언**(`(%)`) — 값 크기로 추론하지 않는다 |
| `-011` | 추정가격의 **과세 제외**와 통화 단위 |
| `-012` | 차수의 **폭 3 제로패딩**과 왕복 보존 |
| `-013` | `resultCode` 의 **필수성** — 부재는 정상이 아니다 |
| `-014` | 일시의 **타임존 미선언**(부재의 확인) |
| `-015` | 예산 키의 **과세 미선언**(부재의 확인) |
| `-016` | 업무구분명의 **4값 열거** |
| `-017` | 기초금액 키는 `bssamt` 하나 · `bssAmtPurcnstcst` 는 **다른 개념** |
| `-018` | 시공능력평가금액목록의 **형식 선언**과 **단위·과세 미선언** |
| `-027` | 「OPEN API 에러코드별 조치방안」 표의 **16 코드**와 그 문면 · `00` 이 표 밖이라는 것 · 표가 **범주와 적용 범위를 주지 않는다**는 것 |

**부재를 주장하는 셋(`-014`·`-015`·`-018`)의 기대값은 `false` 불리언**이라 적대 스윕의 값 변이
갈래가 그것을 뒤집는다 — 「문서가 선언하지 않는다」가 계약이 아니었다면 그 변이가 통과한다.

**주장하지 않는 것을 각 case 가 명시한다.** `-012` 는 「산술 미정의」를 주장하지 않고(그 자리는
`-005`), `-016` 은 「미지 코드에 임의 라벨 금지」를 주장하지 않으며(`-006`·`-007`), `-013` 은
**17 범주 분류표를 주장하지 않는다**(원문 미확보 — §5).

### 3.2 `insufficient-evidence` 8 건 — 승인 대기

`-019` ~ `-026` 의 근거는 `capability-map.md` COL-01·03·05·06 acceptance 와 3A `scope.md` D-3A-4 로,
**M0 산출 문서와 slice 계약의 자체 도출**이다. `classification_policy.insufficient_evidence` 의
되돌림 경로대로 **운영자가 그 acceptance 를 업무 규칙으로 명시 승인하면 `authoritative` 로 올라간다.**

이 여덟은 사유 어휘(`MissingNoticeNumber` · `AccountingIdentityViolated` · `AlreadyHeld` ·
`AgeGateNotPassed` · `RecheckGateNotPassed`)를 기대값에 나르지만 **`verified_paths` 를 두지 않는다** —
아직 승인 어휘가 아니라 정확 비교로 잠그면 미승인 이름을 golden 으로 굳힌다(2026-09-01 결정).

### 3.3 기존 9 건 — 승격하지 않았다

전부 `insufficient-evidence` 로 남는다. 이 slice 가 한 것은 둘이다.

1. **basis 토큰 정합**(D-3A-7) — 입력·기대값의 `ESTIMATED_PRICE` 를 코드 토큰 `ESTIMATED` 로.
   **의미 불변**이고 `money-basis-001`·`-004` 가 앞서 받은 같은 처리다. 각 case 의 `change_history` 에
   사유와 이전 해시를 남겼다.
2. **`contract_binding: pending-3a`** — 계약 타입이 3A 산출물이라 아직 없다.

**승격 후보의 지위가 달라진 것이 있다.** `-005`(제로패딩)와 `-002`(선언 범위 위반)는 이제 문서
근거를 **부분적으로** 얻었다 — 원문 형태·단위 선언은 `-012`·`-010` 이 authoritative 로 지고, 남은
절반(「산술 미정의」·「거부와 회계 등재」)만 승인 대기다. 승인 요청 표가 그 절반을 든다.

---

## 4. privacy 판정

- **신설 입력에 운영 데이터가 없다.** 식별자는 전부 `SYN-` 접두 합성값이고 각 case 가
  `privacy.synthetic: true` · `method: not-applicable-synthetic` 을 단다.
- **공식 문서에서 옮긴 것은 항목 명세 행뿐이다** — 영문 키 · 국문 개념 · 항목크기 · 항목구분 ·
  항목설명. 문서에 실린 **실제 기관명 · 담당자 성명 · 전화번호 · 이메일 · 실제 공고번호**는 옮기지
  않았다. `official_documents` 의 `privacy` 필드가 그 판정을 적는다.
- **보수적으로 지운 것**: 문서의 샘플 값 가운데 금액·율·공고번호는 합성값으로 대체했다. 율 샘플
  (`89.745` · `87.745`)은 `data-dictionary.md` §12.1 이 `legacy-behavior` 로 등재한 **법정 하한율과
  같은 수**여서 `numeric_discipline` 상 실을 수 없기도 하다 — 두 이유가 같은 처리로 만난다.
- **manifest 머리 「전부 synthetic」 문장에 단서를 달았다.** 값이 아니라 **선언**을 인용한 입력이
  생겼고, 그것은 운영 데이터가 아니므로 `observed` 가 아니다. `observed` 층은 여전히 0 건이다.
- **3B 로 넘기는 privacy 부채**: ScsbidInfoService 의 `bidwinnrNm`·`bidwinnrBizno` 는 낙찰 업체
  **성명·사업자 등록번호**다. 그 축의 fixture 는 masking 정책이 정해지기 전에 만들지 않았다.

---

## 5. 미확보 — `insufficient-evidence` 로도 만들지 않은 것

| 축 | 왜 못 만들었나 | 소유 |
| --- | --- | --- |
| **`resultCode` 코드 → 범주 매핑** | **표 본문은 2026-09-07 확보됐다**(`pps-openapi-guide` · `-027`). 그러나 표가 주는 것은 사람이 읽는 조치방안 문면이고 **재시도 가능/불가 분류는 어느 문서에도 없다** — V2 판정이라 승인 대상 | `OPEN-COL-02`. `policy-values.md` §3.2 의 「V2 범주 후보」 열이 제안이고 층이 없다 |
| **에러코드 표의 적용 범위** | 활용가이드는 `PubDataOpnStdService` 의 것이고 수집이 부르는 `BidPublicInfoService` 가 아니다. 공통 절로 보이나 **문서가 그렇게 선언하지 않는다** | 승인 대상(P-4). 방증 둘은 `observed`/`legacy-behavior` 층 |
| **개찰·예비가격 필드 계약** | ScsbidInfoService 참고자료가 이 저장소에 없다. legacy 소비 키 60 중 17 이 그 서비스 소관이고 항목크기·항목구분·단위·과세를 모른다 | 3B 선행 조건 후보(승인 요청 P-6) |
| **rate limit 수치** | 관찰된 것은 문구(`API token quota exceeded`)와 회복 시간(약 2분), 원인(동시성)뿐이고 공식 한도가 없다 | `OPEN-COL-05` · D-M3-5 (a) |
| **잘못된 인코딩 case** | legacy 에 대응 처리가 없고(응답을 그대로 쓴다) 승인된 정규화 규칙도 없다 | 소유 `OPEN` 없음 — 명세 부재 |
| **업무구분 전체 코드 체계** | legacy 매핑 4건은 관찰이고 전수가 아니다 | `OPEN-COL-03`(활성) |
| **시공능력평가금액의 단위·과세** | 문서가 그 항목에만 단위를 적지 않는다(`-018` 이 그 부재를 고정) | `OPEN-QUAL-10`(활성) |

---

## 6. 알려진 제한

1. **`authoritative` 9 건이 잠그는 것은 「문서가 무엇을 선언하는가」이지 「V2 가 어떻게 처리하는가」가
   아니다.** 처리 규칙(거부·계수·타입 구분)은 여전히 `insufficient-evidence` 쪽에 있다.
2. **`-017` 은 등재 당일 정정됐다.** 최초 등재가 `bssAmtPurcnstcst` 를 「문서에 없다」로 적었으나
   공사기초금액조회 명세에 실재한다. `change_history` 에 사유와 이전 해시가 있다. **같은 클래스의
   오류가 다른 case 에 남아 있을 수 있다** — 문서 대조는 사람이 읽은 결과다.
3. **「이 문서에 없다」는 이 문서에 한정된 주장이다.** 다른 KONEPS 서비스의 참고자료를 열람하지
   못했으므로 `bssAmt`·`presmptAmt` 등이 다른 서비스에 있을 가능성을 배제하지 않는다.
4. **`policy-values.md` §1.7 의 17 키는 계약 없이 남는다.** 3A ④ 의 타입이 「계약 없는 키는 소비
   함수에 못 들어간다」이므로 그 키들은 **소비 불가** 상태로 3B 를 맞는다.
5. **문서의 항목 명세를 기계로 파싱했다**(docx → 표 행). 파싱 오류가 표에 섞였을 가능성은 각 case 의
   `documentDescriptionQuote` 축어로 감사할 수 있다 — 원문과 대조하면 드러난다.
6. **`-027` 의 원 docx 는 저장소에 없다.** `_workspace/` 는 `.gitignore` 대상이고 legacy symlink 처럼
   blob id 로 고정할 수도 없다 — 재취득은 `source_url` + SHA-256 대조로만 된다. **그래서 표 본문의
   커밋된 정본을 fixture 입력에 두었다.** 그 파일이 원문과 같은지는 이 레인의 독립 재추출(F-10)이
   근거이고, 감사자는 URL 에서 다시 받아 같은 절차로 대조할 수 있다.
7. **`-027` 은 이 corpus 에서 유일하게 `privacy.synthetic: false` 다** — 입력이 합성이 아니라 공표된
   문서의 인용이기 때문이다. manifest 머리의 「전부 synthetic」 문장에 이미 단서가 달려 있다.

---

## 7. rollback

이 레인의 산출물만 되돌린다 — **range revert 를 쓰지 않는다**(하네스·다른 레인 커밋이 같은 range 에
있다).

```
git restore --source=a9f1ff9c54b62fb7cfa859fff43dae7b943daf8f --staged --worktree -- \
  fixtures/manifest.yaml \
  fixtures/tools/mutation_sweep_adversarial.py \
  fixtures/input fixtures/expected \
  reports/evidence/m3/3a/policy-values.md \
  reports/evidence/m3/3a/fixtures-koneps-collection.md
```

`--source` 에 없는 신규 경로는 삭제되므로 별도 `git rm` 이 필요 없다.

**F-9 — 임시 clone 실측(dry-run 아님).** 저장소를 스크래치패드로 clone 해 위 명령을 그대로 실행했다:
**exit 0 · D 35 · M 9**. 확인 지점 둘도 실측했다 — ① 되돌린 경로에 대해
`git diff a9f1ff9 -- fixtures/ reports/evidence/m3/3a/policy-values.md` 가 **0 줄**, ② 변경된 경로
가운데 `fixtures/` 와 `policy-values.md` 밖은 **없다**(다른 레인 무영향). clone 은 삭제했다.

**이 명령은 `fixtures-koneps-collection.md` 자신을 포함하지 않는다** — 실측 시점에 미커밋이었다.
되돌릴 때는 인자에 더한다(신규 파일이라 `--source` 에 없어 삭제된다).

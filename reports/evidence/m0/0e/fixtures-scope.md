# M0 / 0E — `fixtures/manifest.yaml` 착수 (fixture-curator)

## 기준

| 자리 | 값 |
| --- | --- |
| base_sha | `14686dbf3bff4085203ffdcd931564fd1e36edf0` |
| head_sha | `9e1223ac2a64f3328b8ea2bdadca9b69c933d264` |
| 기준 문서 | `data-extract.md` (단일 기준) |
| legacy 기준 SHA | `ed4b06c` — read-only. **이 slice 는 legacy Python 을 실행하지 않았다** |
| 착수 근거 | `milestone-1.md:12` M1 선행 조건 · `milestone-0.md:94` 산출물 · `m1-blocking-analysis-v2.md` `N-1`/`T-1` |

## in_scope

- `fixtures/manifest.yaml`
- `fixtures/input/*.json` · `fixtures/expected/*.json` (63 case)
- `reports/evidence/m0/0e/fixtures-*.md`

## out_of_scope

- `docs/**` · `reports/evidence/m0/0a`~`0d` · `milestone-*.md` · `v2-지침서.md` — 병행 레인이 소유한다
- `bid-vector/**` — read-only 참고 자료
- legacy Python 실행 · 운영 DB read · KONEPS/LLM/Telegram/email 호출 — **하나도 하지 않았다**
- push · merge · 배포

## 이 slice 가 등재하는 것

**`authoritative` 63 case.** `observed` 0건, `legacy-behavior` 0건.

| 도메인 | 건수 |
| --- | --- |
| license | 12 |
| koneps-collection | 9 |
| floor-shortfall | 6 |
| money-basis | 6 |
| base-amount-provenance | 5 |
| floor-applicability | 5 |
| rate-unit | 5 |
| capacity-gate | 4 |
| ml-boundary | 4 |
| verdict | 4 |
| floor-threshold | 3 |

`uncovered_axes` 18 · `insufficient_evidence` 4 · `access_approval_required` 4.

## `milestone-1.md:12`("검증 fixture 중 `authoritative` case 준비")를 어디까지 만족하는가

**부분 만족이다.** 63 case 가 `authoritative`로 서지만 `data-extract.md` §4가 요구하는 도메인별
최소 corpus 가 **전부 차지는 않는다** — 막힌 축은 `fixtures/manifest.yaml`의 `uncovered_axes`가
소유 `OPEN`과 함께 전수로 적는다. 그 목록을 비우지 않고 그럴듯하게 채우지도 않았다.

**분류가 `authoritative`인 것과 승인된 것은 다르다.** 모든 case 의
`review.approved_by_user`가 `false`, `review.codex_verdict`가 `pending`이다.

## 알려진 제한

1. **`observed` 층이 0건이다.** 실제 payload 형태의 함정(누락 필드 조합, 인코딩, 천 단위
   구분자, 실제 키 이름 변형)을 이 corpus 가 재현하지 못한다. 운영 데이터 접근 승인이 선행한다
   (`access_approval_required` **A-1**).
2. **`legacy-behavior` 층이 0건이라 differential 판정(§6)의 한쪽 항이 비어 있다.** legacy 순수
   함수 실행은 승인 대상이며 요청하지 않았다(**A-3**).
3. **numeric discipline 검사가 정수 counts 를 보지 않는다.** 면제 목록과 사유는
   `manifest.yaml`의 `numeric_discipline.check.exempt`가 적는다.
4. **`fixtures/expected/`의 위치가 `data-extract.md` §3 예시와 다르다.** 이 slice 의 쓰기 범위가
   `fixtures/`로 한정된 결과이며 `manifest.yaml`의 `layout.note`가 선언한다.
5. **`license-011`의 기대값은 잠정이다** — `OPEN-QUAL-11` 결정 전 임시 처리이고 `provisional`
   필드가 그 사실을 나른다.
6. **일부 case 의 `authoritative` 분류가 M0 완료 게이트에 걸려 있다.** 근거가 M0 산출 문서의
   **자체 도출 acceptance** 뿐이고 그것을 세운 운영자 결정·조달청 문서·`v2-지침서.md` 문면이
   없는 case 다. `source.kind: m0-derived-rule`이 그 자리를 표시하고 조건은
   `manifest.yaml`의 `classification_policy.m0_derived_rule`이 적는다 — 사용자가 그
   acceptance 를 승인하지 않으면 **재분류 대상**이다. 어느 case 가 드는지는 **F-7**이 센다.

## evidence 패키지에서 성립하지 않는 항목 (`N/A + 사유`)

- `differential.json`: **N/A** — Python/V2 차이 판정은 두 항이 있어야 성립하는데 V2 구현이
  아직 없고 `legacy-behavior` case 도 0건이다. legacy 순수 함수 실행은 승인 대상이며 요청하지
  않았다(`access_approval_required` **A-3**). 그 판정은 fixture 를 소비하는 첫 구현 slice 의 몫이다.
- `golden-manifest.json`: **N/A** — 이 slice 는 fixture 를 **소비**하지 않고 **등재**한다. 그 파일이
  담을 것(사용 fixture id · 출처 분류 · SHA-256)을 `fixtures/manifest.yaml` 이 case 마다 이미 갖고
  **F-1** 이 SHA-256 대조를 돌린다. 별도 파일로 복제하면 두 벌이 갈린다.

## rollback

`fixtures/` 디렉터리 전체와 `reports/evidence/m0/0e/fixtures-*.md`를 지우면 base 상태다.
다른 경로에 쓴 것이 없고, 이 slice 는 어떤 실행 코드도 만들지 않았다.

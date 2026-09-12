# 점검표 — M5 / 5D-golden · `ml-kernel` 커널 golden corpus

기준: `data-extract.md` §1·§3·§4·§5·§7 · `fixtures/manifest.yaml` 머리말과 `schema.extensions` ·
`agent-workflow.md`. 각 행은 **명령 또는 파일 좌표**로 확인 가능한 것만 적는다.

## 1. `data-extract.md` 준수

| # | 요구 | 확인 | 결과 |
| --- | --- | --- | --- |
| 1 | 출처 없는 fixture 금지(§3) | 14 case 전부 `source.kind: approved-spec` + `source.reference` 에 문서·절 좌표 | 충족 |
| 2 | 입력과 기대 결과의 version 고정(§3) | 정책 version 이 입력에 `syn-inference-v0` 로 박혀 있고 기대값은 그것만 참조 | 충족 |
| 3 | 정규화 전 원본과 정규화 후 구분(§3) | `normalization.source_form: authored-canonical` — 원본이 없다(합성 저작)는 사실을 case 마다 선언 | 충족 |
| 4 | hash 로 사후 변경 탐지(§3) | `sha256` · `expected_sha256` 28개 — `golden-manifest.json` 이 같은 값을 재게 | 충족 |
| 5 | 기대값 변경 시 덮어쓰기 금지(§3) | `change_history: []`(최초 등재) | 해당 없음 |
| 6 | §4 ML 7항목 대응 | 7 중 **5.5** 덮음 — 각 case 의 `data_extract_axis` 가 대응을 든다. 못 덮은 자리는 아래 3절 | 부분(선언됨) |
| 7 | 비식별화(§5 ②③) | 운영 데이터 유래 0. 식별자 전부 `SYN-` 접두 합성. G-6 grep 적중 0 | 충족 |
| 8 | 기대값을 공식 근거·승인 규칙으로 수작업 작성(§5 ⑥) | `expected_reasoning` 이 case 마다 승인 문면을 **축어로** 인용 | 충족 |
| 9 | 재생성 명령으로 golden 자동 승인 금지(§7) | 자동 재생성 경로 없음. legacy Python 미실행 | 충족 |
| 10 | 「가상 fixture 를 실운영 샘플로 표기」 금지(§7) | `privacy.synthetic: true` · `method: not-applicable-synthetic` 14/14 | 충족 |
| 11 | percent/fraction·원/천원·VAT 를 값 크기로 추측 금지(§7) | `normalization.rate_unit`·`money_unit` 이 case 마다 단위를 선언. 율은 `Rate.fraction` 문자열 | 충족 |
| 12 | 한 case 의 기대값을 여러 의미에 재사용 금지(§7) | `verifies` 가 case 당 한 문장. 012 의 주 경계 축을 014 로 분리한 것이 그 규율의 적용 | 충족 |

## 2. 기계 검사 (실행 결과)

| id | 명령 | 결과 |
| --- | --- | --- |
| G-1 | `python3 fixtures/tools/mutation_sweep_adversarial.py` | exit 1 — **base_sha 와 같다**. 강등 목록은 `verdict-004` 하나이고 그것은 이월 부채다. `ml-kernel-*` 변이체 **180 전부 caught, PASSES 0** |
| G-2 | 같은 명령 `--crosscheck-pyyaml` | `pyyaml crosscheck OK — cases 119` · `술어 self-check OK — 검사 22` |
| G-3 | `python3 fixtures/tools/mutation_sweep_targeted.py` | exit 0 |
| G-4 | `python3 fixtures/tools/check_legacy_numbers.py` | exit 1 — **base_sha 와 같다**(적중 81, 전부 `verdict-*`·`strategy-edit-*`). `ml-kernel` 적중 **0** |
| G-5 | `python3 fixtures/tools/manifest_prose_consistency.py` | 불일치 3 — **base_sha 와 같다**(변동 0) |
| G-6 | `grep -nEi -f config/quality/leak-patterns.txt fixtures/{input,expected}/ml-kernel-*.json` | 적중 0(grep exit 1) |
| G-7 | 개인정보·사업자 패턴 grep(사업자번호·전화·이메일·주민번호·`주식회사`) | 적중 0 |

**G-1·G-4·G-5 의 「base_sha 와 같다」가 이 레인의 판정선이다.** 세 도구 모두 HEAD 에서 이미 붉거나
값이 있었고(이월 부채), 이 레인은 **그 수치를 하나도 늘리지 않았다**. 대조는 `--manifest` 로 HEAD 판
manifest 를 물려 재실행해 얻었다.

## 3. 덮지 못한 것 — `golden-manifest.json` `not_covered` 가 정본

여덟 항목이고 소유가 전부 적혀 있다. 요약하면 **둘이 축 자체로 남고**(LightGBM 런타임 오류 ·
반사 보정 KDE singular — 각각 `OPEN-5D-REAL-BOOSTER` 와 D-M5-9 (a) 소유) **나머지 여섯은 승인 부재**다
(출하 정책 값 · 불확실성 산식 · 관측 밴드 경계 · 반올림 모드 · K5 분산 사다리 · 실 booster 결정성).
`fixtures/manifest.yaml` 의 `uncovered_axes` 「ML」 축을 그 둘로 **좁혀 갱신**했고, 앞 판이 사유로 들던
「M2 계약이 아직 없다」는 2A~2E 종결로 더 이상 참이 아님을 그 자리에 적었다.

## 4. `insufficient-evidence`

**0 건.** 14 case 전부 승인 문면에서 기대값을 유도할 수 있었다. 유도할 수 없던 자리는 case 를 만들지
않고 `not_covered` 로 보냈다 — 그것이 `data-extract.md` §6 의 어휘를 남용하지 않는 쪽이다.

## 5. 승인 상태

`review.approved_by_user` 는 14 case 전부 **`false`** 이고 `codex_verdict` 는 `pending` 이다.
`claude_commit` 은 `pending` — 이 manifest 를 담은 커밋이 자기 SHA 를 담을 수 없다.
**승인 요청은 팀장 레인이 사용자에게 올린다.** 승인 전까지 이 corpus 는 5D 구현 레인의
golden test 입력으로만 쓰이며 「승인된 authoritative corpus 통과」로 계상되지 않는다.

## 2b. rollback 실측

`rollback.md` 의 절차를 **임시 clone**(`git clone` — `cp -r` 금지, 2026-09-10 규율)에서 끝까지 실행했다.
「파일이 제자리로 갔는가」와 「그 트리가 서는가」를 둘 다 쟀다.

| 확인 | 결과 |
| --- | --- |
| `git diff f138280~1 --stat -- fixtures` | 출력 없음 — base 와 동일 |
| manifest reader | `cases 105 · auth 85` — base 값(신설 14 가 정확히 빠졌다) |
| `mutation_sweep_adversarial.py` | exit 1, 강등 `verdict-004` 하나 · 잔존 `authoritative` 84 — base 판정 재현 |
| `mutation_sweep_targeted.py` | exit 0 |
| `manifest_prose_consistency.py` | 불일치 3 — base 값 |

**남의 줄이 남았는가**: 공유 파일 둘은 `f138280` 의 hunk만 역적용했고 그 커밋 밖의 줄은 손대지 않았다.
이 브랜치에는 이 레인의 커밋만 있어 겹치는 slice 가 현재는 없다 — 다른 레인이 같은 파일을 만진 뒤
rollback 할 때를 위한 수동 해소 절차를 `rollback.md` 가 미리 적어 둔다.

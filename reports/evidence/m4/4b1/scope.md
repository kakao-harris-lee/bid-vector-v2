# Slice 계약 — M4 / 4B-1 · 투찰 판정 `Verdict` 커널 (게이트 사다리)

> **지위**: **착수 계약 2026-09-09.** `milestone-4.md` 4B 를 둘로 나눈 앞쪽이다.
>
> **왜 나눴나 (운영자 결정 2026-09-09).** 4B 의 「decision 후보 조립」은 **조립할 대상이 없었다** — `decision` 모듈에 투찰 판정 `Verdict` 축이 실재하지 않는다
> (실측: provenance·floor shortfall 커널 둘뿐). `data-dictionary.md` §3.6 이 어휘를 승인했으나 §13.2 가 구현을 **M1** 에 인계했고 `milestone-1.md` 1A~1E 에 게이트 사다리
> slice 가 없다. `capability-map.md` 가 이 어긋남을 이미 **「갈림」**(*"milestone-4.md 4B `- decision 후보 조립`이 그 자리다"*)으로 등재해 두었고, 운영자가 2026-09-09 에
> **4B-1(도메인 커널) / 4B-2(조합 use case)** 분할로 해소했다. 도메인 커널과 application 조합을 같은 slice 에 섞지 않는다.

```yaml
milestone: m4
slice: 4b1-bid-verdict-kernel
base_sha: 13cf0f63da18e13f0e2befd519710a8635742004   # 4C-1 첫 구현 커밋. **4C-1 종결 시 재고정**(공유 파일의 기준선을 안정시킨다)
head_sha: 리뷰 요청 시점의 `git rev-parse HEAD` — **값을 박지 않는다**(evidence 커밋 자신이 head 가 되어 즉시 낡는다, 4A r1 B-3).
branch: m4/2026-09-08
in_scope:
  - decision/src/main/kotlin/bidvector/decision/**    # Verdict·SkipReason·Reason 어휘·게이트 사다리 first-match·정책 데이터 슬롯
  - decision/src/test/**                              # 사다리 전수·first-match 순서 민감도·property·컴파일 폐쇄 증거
  - config/quality/gate-tests.properties              # `gate.tests.decision` 확장
  - fixtures/manifest.yaml                            # verdict-001~004 승격(조건부) + 신설 case
  - fixtures/input/verdict-*.json
  - fixtures/expected/verdict-*.json
  - app/src/test/kotlin/bidvector/app/conformance/**  # `verdict` 축 dispatch(실행자)
  - docs/discovery/data-dictionary.md                 # §3.6 의 `SkipReason` 전수성 확정 + §13.2 `OPEN-DIC-03` 종결만
  - docs/discovery/capability-map.md                  # `OPEN-DIC-03` 갈림 해소 표시만
  - milestone-4.md
  - reports/evidence/m4/4b1/**
out_of_scope:
  - workflow/**                                       # 조합 use case 는 4B-2. 이 slice 는 **순수 커널**이다
  - adapters/**  ·  "**/db/migration/**"              # `main` 의 M3 후속 레인 경로 — 건드리지 않는다
  - ML 호출·gRPC·4D                                    # 사다리는 ML 점수를 **입력 값**으로 받는다. 호출은 4D
  - procurement/** · qualification/** · strategy/** · shared-kernel/**   # 승인된 커널. 필요한 타입이 없으면 멈추고 보고
  - 이벤트 봉투·outbox(4C-1) · 알림(4E) · 저장(3D/4C-2)
  - 추천 투찰율 계산·ML 모델                            # M5
acceptance_commands:
  - "S-0  d=$(mktemp -d) && git clone --quiet --no-hardlinks --branch m4/2026-09-08 --single-branch <repo> \"$d/repo\" && (cd \"$d/repo\" && ./gradlew --no-build-cache clean check)"
  - "S-1  ./gradlew --no-build-cache clean check"
  - "S-2  ./gradlew :decision:test"
  - "S-3  ./gradlew :decision:domainApiTypeGate :decision:domainSourceReferenceGate :decision:moduleDependencyGate :decision:sizeGate :decision:cpdCheck"
  - "S-4  ./gradlew :app:test --tests '*Conformance*'"
  - "S-5  ./gradlew qualityBaseline"
  - "S-6  ./gradlew :app:gateExecutionGate"
rollback: |
    **정본은 `reports/evidence/m4/4b1/rollback.md`.** 경로 한정 `git restore --source=<base_sha> --staged --worktree -- <in_scope 경로 개별 인자>`,
    목록은 `git diff --name-status <base>..HEAD` 로 기계 산출, 임시 clone 에서 exit 0 · D/M 수 · in_scope diff 비어 있음 · **되돌린 트리의 compile · test** 까지 실측.
    승인 문서 둘(`data-dictionary.md`·`capability-map.md`)은 in_scope 이므로 되돌림 대상이다 — 하네스 경로는 아니다.
```

작성: 2026-09-09, 세션 모델 단독. 근거: `milestone-4.md` 4B · `data-dictionary.md` §3.6·§13.2(`OPEN-DIC-03`) · `capability-map.md` 의 `OPEN-DIC-03` 갈림 행 ·
`OPEN-DEC-04`(하한 override 거부)·`OPEN-DEC-05`(하나의 Skip + 필수 reason)·`OPEN-STR-03`(force-bid 우회 노출) · `ADR 0010` D-6 · `prep/m4-prep.md` D-M4-6 ·
조사 노트 `_workspace/m4-4b1/01_scout_verdict_ladder.md`.

---

## 운영자 결정 — 2026-09-09 착수 승인

| ID | 결정 | 귀결 |
| --- | --- | --- |
| **`OPEN-DIC-03` 갈림** | **4B-1 / 4B-2 분할** — 커널이 앞, 조합이 뒤 | `decision` 은 도메인 게이트가 전부 걸리는 모듈이라 조합과 섞으면 실패 면이 넓어진다 |
| **`SkipReason` 전수성** | **사다리를 옮기며 발견되는 사유를 등재하고 그때 닫는다** | §13.2 의 본래 의도(「지금 닫으면 옮기는 과정에서 발견될 사유가 갈 곳을 잃는다」). 목록의 입력은 preflight 조사, 확정은 이 slice 의 산출 |
| **D-M4-6** | **`Verdict.Review(reason = MlUnavailable(...))`** | 「추천 없음」이지 「낮은 추천」이 아니다. ADR 0010 D-6 이 4D 착수 시 사전 등재를 요구했고 4B-1 이 자리를 낸다. 사유 어휘는 4D 가 넓힌다 |

---

## 하네스 레인 변경 (상시 절)

`git log --oneline <base_sha>..HEAD -- CLAUDE.md .claude/`

- 착수 시점: **없음**(base == HEAD).
- **리뷰 요청 시점(구현 완료, 2026-09-09) 재확인 — 2건**(`git log --oneline
  13cf0f6..HEAD -- CLAUDE.md .claude/` 실측):
  1. `ea79355 harness(v2-slice-pipeline): (2b) 의 「경계로 처리」 행도 실측 대상으로`
     (`.claude/skills/v2-slice-pipeline/SKILL.md`·`CLAUDE.md`).
  2. `d9a39cc harness(evidence-pack): 공유 파일 rollback 의 커밋 해시 hunk 격리 절차`
     (`.claude/skills/evidence-pack/SKILL.md`·`CLAUDE.md`, `git show --stat` 실측 —
     2파일 M, +11줄) — 이 slice의 M-1 재실측(커밋 해시로 hunk 격리)이 세운 절차를
     팀장이 스킬 문서에 성문화한 커밋.
  둘 다 이 slice의 산출물 커밋(`381eaeb`·`7133ccc`·`7815d3b`·`25579a7`) **이후**,
  다른 세션(팀장)이 붙인 하네스 개정이다 — slice 산출물이 아니며 in_scope 밖, rollback
  대상이 아니다.
- rollback 은 in_scope 한정이라 하네스 경로를 되돌리지 않는다.

---

## 4C-1 과 공유하는 파일 — 줄 단위 되돌림

4C-1(이벤트 봉투·outbox)과 이 slice 는 모듈이 다르지만(`workflow` ↔ `decision`) **다음 파일을 함께 만진다**:
`config/quality/gate-tests.properties` · `fixtures/manifest.yaml` · `docs/discovery/data-dictionary.md` · `docs/discovery/capability-map.md` ·
`app/src/test/kotlin/bidvector/app/conformance/**` · `milestone-4.md`.

**그러므로 이 slice 의 rollback 은 그 파일들에서 `git restore` 로 파일 전체를 되돌리지 않는다** — 이 slice 가 넣은 줄만 걷는다(M3 교훈: 공유 build·catalog·gate-tests 는 줄 단위).
`rollback.md` 가 파일마다 「전체 복원」인지 「줄 단위」인지 명시하고, 줄 단위인 파일은 임시 clone 에서 되돌린 뒤 **compile·test 까지** 실측한다.
착수 시 base 를 4C-1 종결 head 로 재고정하면 이 목록의 기준선이 안정된다.

---

## 이 slice 가 하는 일

| # | 일 | 승인 문면 |
| --- | --- | --- |
| ① | **`Verdict` sealed** — `BidNow(reasons)` · `Review(reasons)` · `Skip(reason: SkipReason)`. `SkipReason` **필수**. **사유는 구조화 코드이고 문장이 아니다** | §3.6 문면 · `OPEN-DEC-05` 「금지되는 것은 한-verdict 가 아니라 **문장으로만 구분되는 형태**」 · §3.1 「사람이 읽는 문장은 렌더링 시점에 생성하고 영속하지 않는다」 |
| ② | **게이트 사다리 first-match** — 조사가 전수한 분기를 **순서 있는 정책 데이터**로 두고 first-match 로 판정한다. **순서가 결과를 바꾼다는 것을 test 가 관측**한다(1D `ProvenanceRules` 의 순서 민감도 test 와 같은 관례) | §3.6 「게이트 사다리 first-match」 · 1D 선례 |
| ③ | **`SkipReason` 전수 확정** — 조사가 모은 legacy 사유를 전수해 값 집합을 닫고, `data-dictionary.md` §3.6·§13.2 의 `OPEN-DIC-03` 을 **종결**한다. 최소 두 값(`CapacityHold`·`LowPriority`)은 하한이지 목록이 아니다 | 운영자 결정 2026-09-09 · `OPEN-DIC-03` |
| ④ | **`MlUnavailable`** — `Verdict.Review` 의 사유 하나로 등재. **ML 점수 부재가 `Skip` 도 `BidNow` 도 아니다**(fail-safe 이지 fail-open 이 아니다) | D-M4-6 (a) · ADR 0010 D-6 · `milestone-4.md` 완료 조건 「ML 장애가 위험한 추천으로 fail-open 하는지」 |
| ⑤ | **force-bid 우회의 출처 노출** — 우회를 **없애지 않고** 판정 결과에 출처를 싣는다(`BidNow` 의 reason 에 우회 표지). 우회의 존재를 감춘 것이 결함이었지 우회 자체가 아니다 | `OPEN-STR-03` 운영자 결정 |
| ⑥ | **하한 override 의 거부가 관측 가능** — 개연 밴드 밖 override 는 버리지도 통과시키지도 않고 **사유와 함께 거부**된다. 결과 타입이 그 거부를 나른다 | `OPEN-DEC-04` 운영자 결정 |
| ⑦ | **커널은 순수** — 입력(ML 점수·사정률·용량·전략 임계 등)은 **값으로 받는다**. DB·시각·난수·외부 호출 없음. `decision` 은 domain 층이라 `group.forbidden`·`external.allowed.domain` 이 실제로 걸린다 | ADR 0006 D-3 · §4.5 · 1D 선례 |
| ⑧ | **corpus** — `verdict-001~004` 는 지금 `insufficient-evidence`(2026-08-31 강등, `not_covered` 에 `OPEN-DIC-03` 명시)다. ③ 으로 어휘가 닫히면 **승격 후보**가 된다 — 승격은 운영자 승인 사항이고 `koneps-collection` 27건이 밟은 경로와 같다. 사다리 분기별 case 신설은 조사 결과에 따른다 | `data-extract.md` · manifest 의 `classification_policy.insufficient_evidence` 되돌림 경로 |

**만들지 않는 것**: 조합 use case(4B-2) · ML 호출 · 저장 · 이벤트 · 알림 · 추천 투찰율 계산 · 사람이 읽는 문장.

---

## 위협 모델 — 4B-1 고유 경계

**방어한다**: (a) 사유가 **문장으로 되살아나는 것**(① sealed + 문자열 사유 부재를 컴파일로) (b) 사다리 순서가 조용히 바뀌어 결과가 달라지는 것(② 순서 민감도 test + 정책 데이터)
(c) ML 부재의 **fail-open**(④ — 점수 없음이 `BidNow` 로 가는 경로가 타입에 없다) (d) force-bid 우회가 **결과에서 안 보이는 것**(⑤) (e) 밴드 밖 override 의 **조용한 통과 또는 조용한 폐기**(⑥)
(f) 커널이 시각·DB·프레임워크를 잡는 것(⑦ — domain 게이트가 실제로 잰다).

**방어하지 않는다**: 사다리 **임계값의 옳음**(정책 데이터, 값은 운영자 승인) · ML 점수의 옳음(M5) · 조합 경로의 순서(4B-2) · 저장·이벤트(4C) ·
**빌드 스크립트를 임의로 쓰는 저자**(2026-09-03 경계).

**우회 후보 — 값 위조 축**: (1) `Verdict.Skip` 을 reason 없이 → 필수 필드 (2) `SkipReason` 을 문자열로 → sealed 만, 문자열 필드 부재 (3) 사다리를 우회해 `Verdict` 직조 → 생성 경로를 판정 함수 하나로
(4) 정책 순서를 런타임에 바꿈 → 정책 데이터는 불변 + version (5) ML 점수 없음을 기본값 0 으로 접어 `Skip` → 부재는 값이 아니라 타입(`Fact`/`null` 구분)으로.

**우회 후보 — 값 획득 축 (하네스 (2b))**: `Verdict` 를 밖에서 **얻을 수 있는** 자리를 전수한다. `decision` 은 도메인 모듈이라 `app` conformance 실행자가 커널을 직접 부르는 것이
1C·1D 관례다 — **4A 처럼 커널을 `internal` 로 내릴 수 없다.** 대신 「`Verdict` 를 얻는다」가 무엇을 허락하는지 따진다: 판정 결과는 **읽기 값**이고 부작용 권한이 아니므로
획득 자체는 위협이 아니다. **위협은 「사다리를 지나지 않고 `Verdict` 를 만드는 것」**(위조 축 (3))이고 그것은 생성자 폐쇄로 닫는다. 이 판정을 설계 검토가 실측으로 확인한다.

---

## OPEN — 수령·신설

| OPEN | 4B-1 처리 |
| --- | --- |
| `OPEN-DIC-03` | **종결 목표** — ③ 이 전수 확정. 조사가 목록을 못 닫으면 종결하지 않고 근거와 함께 활성 유지 |
| `OPEN-DEC-04` | ⑥ 이 구현 |
| `OPEN-DEC-05` | ① 이 소비(이미 해소된 결정) |
| `OPEN-STR-03` | ⑤ 가 구현 |
| `OPEN-DIC-10`(십진 렌더링) | 범위 밖 — 사유는 코드이지 문장이 아니다 |
| 신설 후보 `OPEN-4B1-LADDER-THRESHOLDS` | ② 의 임계값은 정책 데이터 슬롯이고 **값은 운영자 승인 대상**이다. 조사가 legacy 값을 찾아도 `legacy-behavior` 로 test 정책에만 쓴다 |

# Slice 계약 — M3 / 3E · 개찰 fact 슬롯과 저장 행 키 — **착수 2026-09-08**

> **지위**: 3B-2 종결(2026-09-08)이 남긴 후속 둘을 한 slice 로 묶는다 — **D-3B2-8**(`OpeningResult` fact 슬롯 부재)과
> **`OPEN-3B2-STORAGE-ROW-KEY-COLLISION`**(행 식별자 부재 행이 저장에서 접힘). 둘 다 `procurement` fact 타입 + 3D 저장
> 스키마·키 유도를 건드리므로 **마이그레이션 하나**로 가는 편이 옳다. 세션 모델 단독 작성.
> **왜 지금인가**: 3B-2 가 개찰 축 raw 관측을 만들지만 **canonical 로 옮길 자리가 없고**, 옮기려 해도 복수 행이 저장에서
> 접힌다. M4 4B 가 개찰 fact 를 소비하기 전에 닫아야 한다(`milestone-3.md` M3 완료 문단).
> **먼저 읽어야 하는 것**: `_workspace/m3-3e/01_design-review.md`(Phase 2.5, 세션 모델 직접 — 키 안정성 대 행 구별의
> 긴장이 이 slice 의 중심이다) · `policy-values.md` §1.7·§1.9·§6b · `reports/evidence/m3/3b2/{scope,checklist}.md` ·
> `reports/evidence/m3/3d/{scope,checklist}.md` · `ADR 0004`·`ADR 0005` D-10.1 · `capability-map.md` COL-02·03·05·SET-01·02.

```yaml
milestone: m3
slice: 3e-opening-fact-and-row-key
base_sha: 9948c6e4056bbf71fa6683aa67d30c2a49fc6eae   # 착수 2026-09-08 = 하네스 개선 커밋(3B-2 종결·push 뒤)
head_sha: 리뷰 시점의 HEAD
in_scope:
  - procurement/src/main/kotlin/bidvector/procurement/{NoticeFacts.kt,OpeningResultRepository.kt,RawObservationStore.kt}, procurement/src/test/kotlin/bidvector/procurement/**   # ① `OpeningResult` 슬롯 확장(추가만) ② 행 구별 축을 나르는 타입. 그 밖의 procurement 파일 편집 금지
  - adapters/src/main/resources/db/migration/V4__opening_result_rows.sql   # 신규 마이그레이션 하나 — V1~V3 을 **고치지 않는다**(적용된 마이그레이션 수정 금지)
  - adapters/src/main/kotlin/bidvector/adapters/persistence/**             # 키 유도·repository 매핑
  - adapters/src/test/kotlin/bidvector/adapters/persistence/**             # Testcontainers 통합 test
  - adapters/src/main/kotlin/bidvector/adapters/koneps/**, adapters/src/test/kotlin/bidvector/adapters/koneps/**   # **조건부** — 어댑터가 행 구별 축을 관측에 실어야 할 때만(설계 검토 결론에 따름). 3B-2 시나리오 test 는 편집 없이 초록이어야 한다
  - config/quality/gate-tests.properties                                   # 신규 test 등재(추가만, 공유 파일)
  - adapters/src/test/kotlin/bidvector/adapters/persistence/{CleanMigrationTest.kt,CleanMigrationTriggerTest.kt}   # **운영자 승인 2026-09-08 — 3D test 편집 예외(추가만)**. 이 둘은 `information_schema` 를 질의해 테이블·컬럼·PK·FK·CHECK·트리거를 **정확히 일치**로 대조하는 **스키마 스냅샷 래칫**이라 마이그레이션이 하나라도 늘면 구조적으로 깨진다(우회 경로 없음 — 컨테이너를 전 test 클래스가 공유한다). **허용되는 편집은 기대값 집합에 신규 항목을 더하는 것뿐이다** — 기존 항목 삭제·수정 금지, 단언 완화 금지(`shouldContainExactlyInAnyOrder` → `shouldContain` 류로 바꾸지 마라: 그러면 래칫이 죽는다). 이 예외는 **3E 에 한정**되고, 뒤 slice 는 자기 계약에서 다시 받는다
  - milestone-3.md                                                          # 「Slice 3E」 착수 문단 — **문서 레인이 쓴다**
  - reports/evidence/m3/3e/**
  - docs/discovery/capability-map.md                                        # **문서 레인 전용** — `OPEN-3B2-STORAGE-ROW-KEY-COLLISION`·`OPEN-3B2-OPENING-FACT-SLOTS` 상태 갱신만
out_of_scope:
  - 개찰완료 오퍼레이션(`getOpengResultListInfoOpengCompt`)·추첨번호·투찰 축   # 후속 slice 3F(D-3B2-9 (a)). **이 slice 는 그 축의 슬롯을 미리 만들지 않는다** — 수집하지 않는 것의 자리를 짓지 않는다
  - 표적조회·실제 KONEPS 호출·서비스 키·운영 DB write·backfill                 # 사용자 승인 사항
  - `policy-values.md`·`fixtures/**`                                          # curator 레인 소유
  - V1~V3 마이그레이션 수정 · 기존 canonical 행의 backfill · 스케줄(M4)
  - LLM(3C)·ML(M5)·app workflow 배선(M4 4B)
acceptance_commands:
  - "git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check)"   # S-0
  - "./gradlew --no-build-cache clean check"                                                          # S-1
  - "./gradlew :adapters:test --tests 'bidvector.adapters.persistence.*'"                            # S-2 — Testcontainers 통합(Docker 필요, 부재는 붉게 — D-M3-7 (a))
  - "./gradlew :adapters:test --tests 'bidvector.adapters.koneps.*'"                                 # S-2b — 3B·3B-2 시나리오가 편집 없이 초록
  - "./gradlew :procurement:test"                                                                     # S-3 — fact 슬롯 확장, 3A corpus 27/27 불변
  - "./gradlew :adapters:moduleDependencyGate"                                                        # S-4
  - "./gradlew qualityBaseline"                                                                        # S-5
rollback: |
    **정본은 `reports/evidence/m3/3e/rollback.md`.** 경로 한정 + `git diff --name-status <base>..HEAD` 기계 산출.
    **되돌린 트리에서 `:adapters:compileKotlin`·`:procurement:compileKotlin` exit 0 과 test 초록까지 실측**(evidence-pack
    2026-09-08 규격 — 3B-2 r2 의 차단 사유가 그 자리였다). `V4__*.sql` 삭제는 **적용 이력이 있는 DB 에서는 되돌림이 아니다** —
    개발 DB 재생성 절차를 함께 적는다(운영 DB 는 out_of_scope).
```

작성: 2026-09-08, 세션 모델 단독. 근거: `milestone-3.md` 3B-2 종결 문단의 잔여 후속 · `capability-map.md` §14.3 의 두 `OPEN` ·
`reports/evidence/m3/3b2/checklist.md` 알려진 제한 · 3D `V1__schema.sql` `opening_result`·`raw_observation` · 3A `NoticeFacts.kt`.

---

## 하네스 레인 변경 (상시 절)

`git log --oneline 9948c6e..HEAD -- CLAUDE.md .claude/` — 착수 시점 **없음**(base 자신이 하네스 커밋이라 range 밖).

## 문서 레인 변경 (상시 절 — verifier r1 L-6)

`base..HEAD` 에는 **문서 레인(오케스트레이터) 커밋이 섞인다.** slice 의 커밋 집합은 range 가 아니라
**in_scope 경로의 변경**이라는 정의(2026-09-04)가 그대로 적용되므로 아래는 slice 산출물이 아니다.
리뷰 요청 시점마다 `git log --oneline <base>..HEAD -- reports/evidence/m3/3a docs/discovery milestone-3.md`
로 갱신한다.

| 경로 | 무엇 | 왜 이 range 에 있는가 |
| --- | --- | --- |
| `reports/evidence/m3/3a/policy-values.md` | P-7 승인 등재 · §1.9.7 실측 대조 · §1.10 첨부 문서 키 계약(P-8) | 3A curator 문서의 후속. 3E 는 이 파일을 **읽기만** 한다(out_of_scope 에 명시) |
| `docs/discovery/capability-map.md` | `OPEN-3B2-TARGETED-OPENING-QUERY` **닫힘**(실측) · 신설 넷 `OPEN-3E-RESERVE-FLAG-MISMATCH`·`OPEN-3E-ROW-ORDER-STABILITY`·`OPEN-3C-ATTACHMENT-SLOT-OVERFLOW`·**`OPEN-3E-OPENING-AMOUNT-AUTHORITY-GUARD`**(verifier r1 H-1 수정의 귀결) · `OPEN-3B2-STORAGE-ROW-KEY-COLLISION`·**`OPEN-3B2-OPENING-FACT-SLOTS`** 상태 갱신(3E 가 닫는 중) | in_scope 에 **문서 레인 전용**으로 선언돼 있다 |
| `docs/discovery/ux-journey-research.md` | 표적조회 문면 폐기(실측) | 위 `OPEN` 닫힘의 역방향 파급 |
| `CLAUDE.md`·`.claude/skills/evidence-pack/SKILL.md` | rollback 규격 개정 · 공유 워킹트리 커밋 규격 신설 | **하네스 레인** — 위 「하네스 레인 변경」 절이 정본 |

**되돌림 대상이 아니다** — 등재된 `OPEN` 과 승인 기록은 이 slice 가 만든 **지식**이고 코드를 걷어도
남아야 한다(되돌리면 다음 착수가 같은 것을 다시 발견한다). rollback.md 의 「되돌리지 않는 것」이
같은 목록을 갖는다.

## 스키마 스냅샷 래칫의 예외 — 운영자 승인 2026-09-08

**물음**: `opening_reserve_price` 자식 표와 `opening_result` 신규 컬럼을 더하면 3D 의
`CleanMigrationTest`·`CleanMigrationTriggerTest` 가 **반드시** 깨진다. 둘 다 정확 일치 대조이고
Testcontainers 인스턴스를 전 test 클래스가 공유해 격리 우회가 없다.

**결정 (a)**: 그 두 파일을 **3E in_scope 예외**로 두고 **기대값에 신규 항목을 더하는 편집만** 허용한다.

**왜 test 를 고치는 것이 여기서는 정당한가.** 이 둘은 행동 회귀 test 가 아니라 **스키마 형태 래칫**이다 —
「스키마가 내가 선언한 것과 정확히 같은가」를 재고, 스키마가 정당하게 늘면 선언도 늘어야 한다.
금지 규율이 겨누는 것은 「내 코드를 통과시키려고 남의 판정 기준을 **느슨하게** 만드는 것」이고,
**추가만 하는 편집은 기준을 느슨하게 만들지 않는다** — 래칫의 눈금이 하나 늘 뿐이다.

**그래서 경계를 문장으로 못 박는다.** 허용: 기대 집합에 신규 테이블·컬럼·제약·트리거 이름을 **더하기**.
금지: 기존 항목 삭제·이름 변경 · 단언을 포함 관계로 완화 · 대조 대상 축소 · 두 파일 밖의 3D test 편집.
**검증 레인이 이 diff 를 표적으로 재검증한다**(판정 기준을 바꾸는 편집이므로 severity 무관).

**되풀이될 문제라는 것을 안다** — 앞으로 모든 마이그레이션이 같은 자리에 걸린다. 이 예외는 3E 에
한정하고, 「기대값을 test 밖 선언으로 빼 마이그레이션 저자가 그 선언을 갱신하게」 하는 구조 개선은
**3D 후속 또는 M6 운영 축**으로 남긴다(신설 후보 `OPEN-3E-SCHEMA-SNAPSHOT-MAINTENANCE`).

## 레인 경계 — 커밋 혼입 선언 (2026-09-08, 이력 되쓰지 않음)

**커밋 `facc9b1` 은 메시지가 내용을 잘못 기술한다.** 메시지는 「docs(m3-3a): P-7 (a) 운영자 승인」인데
실제 내용은 **구현 레인의 3E 코드 7 파일 + 문서 레인의 `policy-values.md` 10줄**이다.

**원인은 문서 레인(오케스트레이터)에 있다.** 구현 레인이 in_scope 7 파일을 스테이징해 둔 상태에서
문서 레인이 `git add policy-values.md && git commit` 을 돌렸고, **경로 인자 없는 `git commit` 은
인덱스 전체를 커밋**하므로 남의 스테이징까지 실렸다. 양쪽 다 기존 규율(단일 `add && commit`)은
지켰다 — 그 규율은 자기 레인의 `add` 와 `commit` **사이**에 다른 레인이 끼는 것을 막을 뿐이고
이번 경우를 덮지 않았다. M1/1A(미커밋 편집이 남아 혼입)와 원인이 다르다.

**처리**: 이력을 되쓰지 않는다(재작업 규율). 이 절이 사실 선언이다. 규격은 그 자리에서 고쳤다 —
`evidence-pack` 스킬에 「공유 워킹트리의 커밋 — 경로를 커밋 명령에 준다」를 신설하고 `CLAUDE.md`
변경 이력에 등재했다(`91b1240`). **이후 모든 레인은 `git commit -m … -- <경로들>` 형식을 쓴다.**

**리뷰가 읽는 법**: `facc9b1` 의 3E 코드 7 파일은 이 slice 의 산출물이고, 같은 커밋의
`policy-values.md` 10줄은 **문서 레인 소유**(P-7 승인 등재)로 이 slice 의 in_scope 가 아니다.
slice 의 커밋 집합은 range 가 아니라 **in_scope 경로의 변경**이라는 정의(2026-09-04)가 그대로
적용되므로, 이 혼입은 rollback 대상 판정에 영향을 주지 않는다 — rollback 은 경로로 판단한다.

---

## 이 slice 가 하는 일

| # | 일 | 근거 |
| --- | --- | --- |
| ① | **저장 행 키가 복수 행을 구별한다** — `ObservationKeyDerivation.of` 의 재료가 `sourceEndpoint\|observedAt\|등재분 투영\|sourceText` 뿐이라, **행 식별자가 실제로 부재**(§1.7.3 이 `compnoRsrvtnPrceSno` 를 옵션으로 선언)하고 나머지가 같으면 키가 충돌해 `ON CONFLICT DO NOTHING` 이 둘째 행을 조용히 버린다. 3B-2 가 어댑터에서 살린 행이 저장에서 다시 접히는 자리다 | `OPEN-3B2-STORAGE-ROW-KEY-COLLISION` · verifier r3 이 키 재료를 직접 유도해 확인 |
| ② | **재시도 안정성 — 층을 갈라 본다(설계 검토가 이 행의 초안 전제를 정정했다).** `observedAt` 이 페이지 fetch 마다 새로 잡혀 **raw 키는 이미 fetch 마다 다르다**(raw 는 append-only 감사). 「retry 후 canonical effect 하나」는 **canonical 기본키**(`ON CONFLICT (notice_number, notice_round)`)가 지킨다. 따라서 raw 층에 응답 안 위치를 더해도 그 조건을 새로 깨지 않는다. **진짜 물음은 canonical 자식 행의 정체성**이고 그것만 fetch 사이에 안정해야 한다 | 실측(`KonepsPageUriBuilder`·`Sql.kt`·`V3__append_only.sql`) · 설계 검토 (1) |
| ③ | **`OpeningResult` fact 슬롯** — 현재 `winningRate`·`derivedBaseAmount`·`observedAt` 뿐이다. **3B-2 가 이미 수집하는 것만** 슬롯을 준다: 복수예비가격 행(예정가격·기초예정가격·복수예가순번·추첨여부·실개찰일시) · 최종낙찰금액 · **최종낙찰업체명**(P-10 (a) — 상호만) · 참가업체수 · 진행구분. **수집하지 않는 축(추첨번호·투찰)의 자리를 짓지 않는다** | D-3B2-8 · `policy-values.md` §1.7 |
| ④ | **P-10 (a) 가 저장 층까지 선다** — 사업자등록번호·대표자명 슬롯을 **만들지 않는다**(만들면 나중에 채워진다). 상호는 `SET-01` 이 상호 정규화 매칭으로 낙찰/패찰을 판정하는 입력이라 저장한다 | `policy-values.md` §6b P-10 · SET-01 |
| ⑤ | **복수예비가격은 1:N 이다** — `opening_result` 는 `PRIMARY KEY (notice_number, notice_round)` 로 공고당 한 행이라 15행을 담을 수 없다. 자식 표를 `V4` 로 신설하고 **부모의 파생값이 자식을 조용히 덮지 않도록** 3D 의 점유 가드 관례(`BEFORE UPDATE` 트리거 + `provenance_authority`)를 따른다 | COL-03 · 3D `V2__provenance_guard.sql` |
| ⑥ | **멱등 재수집** — 같은 공고를 다시 수집하면 자식 행이 중복 삽입되지 않고, **행이 사라진 응답**(15 → 12)이 기존 행을 조용히 지우지도 않는다. 어느 쪽인지 회계·감사에 남는다 | COL-06 · 3D 「retry 후 canonical effect 하나」 |

**만들지 않는 것**: 추첨번호·투찰 축 슬롯(3F) · 기존 canonical 행 backfill · V1~V3 수정 · 예정가 역산 계산식(3D 「만들지 않는 것」 유지) · 사업자등록번호·대표자명 컬럼 · app 배선.

---

## 운영자 결정 필요 — 착수 전

| ID | 물음 | 선택지 | 추천·근거 | 상태 |
| --- | --- | --- | --- | --- |
| **D-3E-1a** ✅ 확정 2026-09-08(설계 검토) | **raw 층 행 구별** | (a) **값 우선 + 부재 시 응답 안 위치**를 키 재료에 더한다 (b) UUID·수신 시각 (c) UNIQUE 해제 | **(a)** — raw 키는 이미 fetch 마다 다르므로(②) 위치를 더해도 새로 깨는 것이 없다. 값이 있으면 값이 우선이라 순서 변화에 취약해지지 않는다. (b)(c)는 감사·멱등을 버린다 | 확정 |
| **D-3E-1b** ✅ (a) 승인 2026-09-08 | **canonical 자식 행의 정체성 — 순번이 부재할 때** | (a) **승격하지 않는다**(raw 에 온전히 남고 「정체성 없음」으로 회계) (b) 응답 안 위치를 canonical 키로 (c) 행 값 해시를 키로 | **(a)** — (b)는 **문서가 선언하지 않는 행 순서 안정성**에 기대고, 틀리면 재수집이 자식 행을 늘려 사정률 분포 입력을 오염시킨다. (c)는 같은 값 두 행에서 다시 접혀 이 slice 가 고치려는 결함과 같은 형태다. **운영자 승인 2026-09-08 (a)** — 「모름」을 지어내지 않는 규율의 적용이자 데이터를 덜 갖는 선택이다. **되돌릴 수 있다** — 행 순서 안정성 관측이 쌓이면 승격으로 바꾸면 되고, 그때 raw 감사 기록이 소급 승격의 재료가 된다 | 승인 |
| **D-3E-2** ✅ (a) 확정 2026-09-08 | **⑤의 자식 표 경계** — 복수예비가격 행을 어디에 두는가 | (a) **`opening_reserve_price` 자식 표 신설**(부모 `opening_result` FK, 행 키는 **부모 + 복수예가순번**, 순번 `NOT NULL` — D-3E-1b (a) 의 귀결) (b) `opening_result` 에 JSONB 배열 컬럼 | **(a)** — (b)는 행 단위 가드·감사·질의를 잃는다(3D 가 JSONB 를 원문 보존에만 쓰고 canonical 에는 안 쓰는 것과 같은 선) | 확정 |
| **D-3E-3** ✅ (a) 확정 2026-09-08 | **⑥의 「행이 사라진 응답」** — 15행 뒤 12행이 오면 | (a) **기존 행을 지우지 않고 최신 관측만 갱신**(사라진 행은 그대로 남고 감사에 「이번 관측에 없었다」) (b) 없는 행 삭제 (c) 부모 revision 을 올리고 전량 교체 | **(a)** — 3D 가 이미 「파생이 authoritative 를 덮지 않는다」·append-only 감사를 택했고, 삭제는 그 방향과 반대다. 다만 **「지금 유효한 15행」을 소비자가 어떻게 아는가**가 남으므로 관측 시각으로 구분한다 | 확정 |
| **D-3E-4** ✅ (a) 확정 2026-09-08 | **fact 슬롯의 타입 깊이** — `OpeningResult` 가 자식 행을 값으로 안는가 | (a) **부모 fact + 자식 목록**(한 aggregate) (b) 자식을 별도 fact 로 | **(a)** — `OPEN-3A-AGGREGATE` 가 열려 있으나 D-3A-1 (a) 가 `NoticeId` 로 묶는 것을 이미 택했다. 자식은 부모 없이는 뜻이 없다 | 확정 |
| **D-3E-5** | **Docker 부재 시** | — | 3D D-M3-7 (a) 그대로: Testcontainers 로컬 실측, Docker 부재는 **붉게**(skip 경로 만들지 않는다) | 계약 고정 |

---

## 위협 모델 — 3E 고유 경계

**방어한다**: (a) 복수 행의 조용한 소실(①②, 저장 층) (b) 재시도가 중복 canonical 을 만드는 것(②) (c) 사업자등록번호·대표자명이 저장 스키마에 자리를 얻는 것(④ — **컬럼이 없으면 채울 수 없다**) (d) 파생값이 자식 행의 authoritative 값을 덮는 것(⑤, 3D 가드 관례) (e) 관측에 없던 행이 조용히 사라지는 것(⑥·D-3E-3).
**방어하지 않는다**: KONEPS 응답 안 행 순서의 안정성(**관측으로만 닫힌다** — D-3E-1 (a) 의 잔여 위험, 알려진 제한으로 등재) · 기존 canonical 행의 오염(backfill 은 out_of_scope) · 운영 DB 접근 통제(M6) · 개찰완료 축의 부재(3F).

**우회 후보(≥5)**: (1) 행 구별 축을 키에 안 넣고 「보통은 순번이 온다」로 넘어감 → 부재 시나리오 통합 test (2) 순번 부재를 `0` 이나 `""` 로 채움 → 3B-2 G-1 과 같은 형태, 「모름」 금지 규율 + test (3) 재시도 test 를 같은 페이지로만 돌려 순서 변화를 안 봄 → **행 순서를 섞은 재수집 test** (4) 자식 표를 안 만들고 부모에 첫 행만 저장 → 15행 저장·재조회 통합 test (5) 사업자번호 컬럼을 「나중에 쓸지 모르니」 만들어 둠 → 스키마 리뷰 + 컬럼 부재 단언 test (6) 사라진 행을 삭제로 처리해 감사 흔적을 없앰 → D-3E-3 (a) test (7) `V4` 대신 `V1` 을 고침 → 마이그레이션 이력 게이트.

---

## OPEN — 수령·신설

| OPEN | 3E 처리 |
| --- | --- |
| `OPEN-3B2-STORAGE-ROW-KEY-COLLISION` | **이 slice 가 닫는다**(①②). 닫히면 capability-map §14.3 에서 상태 갱신 |
| `OPEN-3B2-OPENING-FACT-SLOTS` | **이 slice 가 닫는다**(③④⑤ — 단, 3F 축은 그 slice 가 연다) |
| `OPEN-3A-AGGREGATE` | D-3E-4 (a) 는 D-3A-1 (a) 의 연장이고 이 `OPEN` 을 닫지 않는다 |
| 신설 후보 `OPEN-3E-ROW-ORDER-STABILITY` | D-3E-1 (a) 채택 시 — **KONEPS 응답 안 행 순서가 호출 사이에 안정적인가.** 불안정하면 순번 부재 행의 재수집이 중복을 만든다. 문서가 순서를 선언하지 않는다 — **실제 호출 승인 뒤 관측으로만 닫힌다** |

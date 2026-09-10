# Slice 계약 — M3 / 3G · 애플리케이션 역할 권한 래칫

> **지위**: **착수 계약 2026-09-10.** M4/4C-2 verifier r2 M-3 이 연 `OPEN-3D-GRANT-PUBLIC-BLINDSPOT` 의 후속(운영자 승인 2026-09-10).
> 3D 가 세운 권한 층은 **값이 맞는데 그 값을 지키는 게이트가 절반만 본다** — 그 절반을 마저 채운다.
>
> **왜 3D 가 아니라 새 slice 인가.** M3 는 종결됐고 3D 의 산출물은 승인된 형태다. 이 slice 는 그 산출물을 **바꾸지 않고** 게이트만 올린다 —
> 마이그레이션은 한 줄도 손대지 않는다(권한 **값**은 이미 옳다, 4C-2 가 역할로 행사해 실측했다).

```yaml
milestone: m3
slice: 3g-app-role-privilege-ratchet
base_sha: 471dd34a38e66e3b4cf5c15219bb3881b13591d1   # 4C-2 종결·정정 병합 뒤. main == m4/2026-09-08 == 이 커밋
head_sha: 리뷰 요청 시점의 `git rev-parse HEAD` — **값을 박지 않는다**(evidence 커밋 자신이 head 가 되어 즉시 낡는다, 4A r1 B-3)
branch: m4/2026-09-08   # worktree /Users/harris/Development/private/bid-vector-v2-m4
in_scope:
  - adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationTest.kt   # 권한 축 — 유효 권한 행렬 test 신설, 구 술어 test 둘 대체
  - milestone-3.md                                   # 3G 절 + 종결 문단(승인 시점)
  - docs/discovery/capability-map.md                 # `OPEN-3D-GRANT-PUBLIC-BLINDSPOT` 종결 표시
  - reports/evidence/m3/3g/**
out_of_scope:
  - adapters/src/main/resources/db/migration/**      # **마이그레이션 무편집.** 권한 값은 이미 옳다 — 이 slice 는 게이트만 올린다. 값을 바꾸고 싶어지면 멈추고 보고
  - GRANT/REVOKE 신설·회수 · admin 역할 · row-level security
  - 3D 의 다른 축(CHECK·트리거·컬럼·인덱스·FK) · `CleanMigrationCheckTest`·`ColumnTest`·`TriggerTest`
  - outbox·inbox 권한 test(4C-2 가 이미 유효 권한 술어다 — **행렬에 흡수되면 중복을 지우되 축은 유지**)
  - `adapters/persistence` 의 게이트 등재 완전성 test(별도 공백, 아래 OPEN)
  - config/quality/gate-tests.properties             # 신규 class 를 만들지 않으므로 편집 없음. 만들게 되면 계약을 갱신한다
acceptance_commands:
  - "S-0  임시 clone(git clone . 또는 worktree add --detach)에서 ./gradlew --no-build-cache clean check"
  - "S-1  ./gradlew --no-build-cache clean check"
  - "S-2  ./gradlew :adapters:test"          # Testcontainers — Docker 부재는 **실패**다(ADR 0004 D-1)
  - "S-3  ./gradlew :adapters:sizeGate :adapters:cpdCheck"
  - "S-4  ./gradlew :app:test"               # 필터 없이 전건
  - "S-5  ./gradlew qualityBaseline"
  - "S-6  ./gradlew :app:gateExecutionGate"  # S-4 와 **별도 호출**
rollback: |
    **정본은 `reports/evidence/m3/3g/rollback.md`.** 경로 한정 `git restore --source=<base_sha> --staged --worktree -- <in_scope 경로 개별 인자>`.
    **공유 파일**(`milestone-3.md`·`capability-map.md`·`CleanMigrationTest.kt`)은 **커밋 해시 hunk 격리**(evidence-pack 2026-09-09) —
    「내 줄 사라짐」과 **「남의 줄 남음」을 둘 다** 실측한다. `--3way` 도 자동 해소에 실패할 수 있다(2026-09-10).
    **rollback 목록은 자기를 담은 커밋을 가리킬 수 없다**(2026-09-10) — 공유 파일을 만지는 문서 커밋과 목록 갱신 커밋을 나누고, 후자는 evidence 경로만 만진다.
```

작성: 2026-09-10, 세션 모델 단독. 근거: `reports/evidence/m4/4c2/` verifier r2 M-3 · `V2__provenance_guard.sql`·`V3__append_only.sql`·`V4`·`V6` 의 GRANT/REVOKE · `milestone-3.md` 3D.

---

## 이 slice 가 하는 일

| # | 일 |
| --- | --- |
| ① | **유효 권한 행렬 하나로 전수 래칫.** `bidvector_app` 이 `public` 의 **모든 테이블**에 대해 갖는 권한을 축 일곱(SELECT·INSERT·UPDATE·DELETE·TRUNCATE·REFERENCES·TRIGGER)으로 재고 기대 행렬과 **정확히 일치**시킨다. 술어는 `has_table_privilege` — **역할 직접 부여 + PUBLIC 부여 + 역할 상속**을 전부 해소한다(4C-2 가 만든 `effectivePrivileges` 헬퍼 재사용) |
| ② | **테이블 목록을 DB 에서 발견한다** — 손으로 적은 목록을 순회하지 않는다. 기대 행렬에 없는 테이블이 생기면 **실패**한다(= 새 테이블은 권한을 선언해야 한다). 이것이 래칫의 본체다 |
| ③ | **구 술어 test 둘을 대체한다**(`provenance_authority` SELECT 만 · `notice_audit` INSERT 없음 — F-6). 행렬이 그 둘을 **행으로 포함**하므로 단언이 약해지지 않는다. 두 test 의 근거(F-6 등)는 행렬 KDoc 으로 옮긴다 |
| ④ | **mutation 으로 게이트임을 증명한다** — ⓐ `GRANT DELETE ... TO PUBLIC` ⓑ 역할 직접 `GRANT TRUNCATE`(초과 하나) ⓒ `REVOKE UPDATE`(부족 하나) ⓓ 신규 테이블 추가(기대 행렬 미선언). **넷 다 실패해야** 한다. 원복 후 diff 0 실측 |

**만들지 않는 것**: 마이그레이션 변경 · 새 GRANT/REVOKE · admin 역할 검사 · RLS · 신규 test class · 게이트 등재 완전성 test.

---

## 위협 모델 — 3G 고유 경계

**방어한다**: 마이그레이션이 **의도보다 넓은 권한**을 애플리케이션 역할에 주는 것 — 경로 불문(역할 직접·**PUBLIC 경유**·역할 상속). 그리고 **새 테이블이 권한 선언 없이 들어오는 것**.

**방어하지 않는다**: 권한 **값** 자체의 옳고 그름(3D·4C-2 가 정했고 이 slice 는 재확인만 한다) · admin 역할로 하는 일(3D 경계) · DB 관리자 권한으로 런타임에 `GRANT` 하는 주체(마이그레이션 밖) · row-level security · 컬럼 단위 권한(현재 쓰지 않는다).

**우회 후보**: (1) PUBLIC 경유 부여 → ①의 술어가 해소 (2) 역할 상속 경유 → 같은 술어 (3) 새 테이블에 몰래 GRANT → ②가 미선언 테이블에서 실패 (4) 기대 행렬을 함께 느슨하게 고침 → **diff 에 보인다**(리뷰 대상, 게이트가 막을 수 있는 층이 아님 — 2026-09-03 경계) (5) 컬럼 단위 GRANT → **경계 밖**(현재 스키마가 쓰지 않는다, 알려진 제한 등재).

**(2b) 값 획득 축**: 이 slice 는 **test 만 만진다** — 새 public 타입·함수·프로퍼티가 **0** 이다. 표는 「신설 0」으로 갱신하고, 수정 라운드가 생기면 그때 다시 잰다.

---

## OPEN

| OPEN | 3G 처리 |
| --- | --- |
| `OPEN-3D-GRANT-PUBLIC-BLINDSPOT` | **종결 목표** — ①②가 닫는다. 못 닫으면 근거와 함께 활성 유지 |
| `OPEN-3G-PERSISTENCE-GATE-REGISTRATION`(신설) | `adapters/persistence` 에는 `MlGateRegistrationTest`·`EventGateRegistrationTest` 같은 **등재 완전성 test 가 없다** — 새 test class 를 만들고 `gate-tests.properties` 에 안 적어도 아무도 못 잡는다(4C-2 verifier r3 「범위 밖 참고」). 이 slice 는 신규 class 를 만들지 않아 당장은 영향이 없다. 후속 |

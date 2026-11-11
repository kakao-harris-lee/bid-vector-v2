# M9 / ops-migrations-followups — 지적별 처분

PR #141 「팀장 처분」 코멘트의 「후속 PR 로 넘기는 것」 전수. 각 행의 **처방**은 그 지적이 요구한
것이고, **닫은 자리**는 그것이 실제로 어디에 있는지다(커밋은 `git log --oneline` 이 정본이라 여기
해시를 옮기지 않는다 — 지적 번호가 커밋 제목에 있다).

| 지적 | 처분 | 닫은 자리 |
| --- | --- | --- |
| **cr M-2** 낙찰 runbook 에 릴리스 키 없음 | 닫음 | `m9-award-collection.md` §1 표·§1 끝 문단·§3·**§4** · `m6-6e-operations.md` 의 `bidvector.release` 행(둘 → **셋**)과 키 census 셋 |
| **v M-2** 쌍 집합 등식이 정규식 토큰에 걸려 있음 | 닫음 | `OutboxTransitionSqlTest` — 전수 UPDATE + savepoint 되돌림 거동 test. **구판이 놓친 결함을 새 판이 잡는다**를 변이로 실측(commands.md §3) |
| **v M-1 · cr L-5** 고정점 모집단이 `contype='c'` 뿐 | 닫음 | `CheckConstraintRenderingFixpointTest` — 모집단에 EXCLUDE(`'x'`)와 트리거 정의(`WHEN`)를 더하고, 종류마다 접힐 식을 심는 **상설 양성 대조**를 둔다. 문면을 좁히는 대안(계약이 허용한 둘째 길)은 **고르지 않았다** — 실 스키마 쪽 계수가 0 이라는 제한만 등재 |
| **cr M-4** 라운드 10 계약 갱신 미등재 | 닫음 | `scope.md` 「원 slice 라운드 10 계약 갱신」 절 — 병합 넷(SHA 넷)·V26 재편집 사유와 재검증 범위·면제 KDoc 재작성·진리표 수의 정본 |
| **cr L-1** `7C⑤-b` 면제 근거 줄 소실 | 닫음 | `AppHttpDependencyGateTest` KDoc 근거 목록에 그 항목(`MyBidResultWiring`, 기준 ②). 게이트 거동·상수 불변 |
| **cr L-2** `tools/db-rehearsal.sh` 낡은 문면 | 닫음(문면) | 머리말 + Flyway 호출자 주석. **남는 차이 하나를 명시**했고 스크립트 거동은 무변경 — scope.md 「알려진 제한」 ① |
| **cr L-3** 고정점 탐침에 savepoint 없음 | 닫음 | 탐침마다 savepoint 로 되돌리고 **실패도 소견으로 싣는다**(조용한 건너뜀 = 모집단 축소와 구별 불가) |
| **cr L-4** 실패 목록에 기대/관측 없음 | 닫음 | 소견이 `기대`·`관측` 두 렌더링을 함께 든다 |

## PR #145 라운드 1 지적의 처분

verifier ready-for-review(low 2) · code-reviewer approve(medium 2 · low 7) — 차단 0. 팀장 처분이
「runbook 은 운영 사고로 이어지니 머지 전에」로 넷을 지목했고 나머지도 같은 커밋 계열에서 닫았다.

| 지적 | 처분 | 닫은 자리 |
| --- | --- | --- |
| **cr M-2** §4 가 릴리스 값을 「§3 과 같은 값」으로 못박음 | 닫음 — **사유는 사실로 교정**(scope.md 「수정 라운드 1」 절) | 값을 **이 호출을 실행하는 jar** 의 빌드 SHA 로 · write-once 오염이 실제로 무는 §3 에 조각 사이 베끼기 금지 |
| **cr M-1** 「기동 거부의 전수는 §1.5」 포인터가 거짓 | 닫음(양쪽) | §1.5 에 코드와 대조한 행 넷 + 포인터를 그 절 자신의 「거의 전부」에 맞춰 좁힘 |
| **cr L-1** `bidvector.koneps` 두 타입의 조건이 다름 | 닫음 | §1.2 조건 열에 적었다 — 행을 쪼개지 않아 「행 수 = prefix 수」로 세는 길을 막지 않는다 |
| **cr L-2** `bidvector.notification` 바인딩 조건 부재 | 닫음 | 「relay 를 켤 때만」 |
| **cr L-3** 계정 비밀 문구 leaf 이름 부재 | 닫음 | 이름을 적었다(스캔 어휘를 피해 고른 이름 — 참조형 스캔으로 확인) |
| **cr L-4** 「수집 모드 둘」 귀속 오류 | 닫음 | 관문 **둘**로 정정(러너 빈 수 관문 · 공유 타입 중복이 만드는 주입 모호함). 행을 지우지 않았다 |
| **cr L-5** 실행 블록에 HTTP 표면 끄기 없음 | 닫음 | §3·§4 블록 |
| **cr L-6 · v L-1** 후보 격자가 열거라 리터럴 없는 확장은 못 닫는다 | 닫음(문면) | `OutboxTransitionSqlTest` KDoc 한계 절 — 축 셋을 명시하고 기대 집합을 「후보 어휘 안에서」로 한정 |
| **cr L-7** 트리거/제약 탐침의 표 이름 받는 방식 비대칭 | 닫음(문면) | fail-closed(어긋나면 탐침 실패 → 소견)라서 거동 무변경 · 비대칭을 사실로 적음 |
| **v L-2** scope.md 자리표시 둘 | 닫음 | head_sha 는 **라운드마다 적지 않는다**는 규율로 바꾸고 라운드 1 대상 SHA 를 적었다 · 하네스 레인 줄은 재산출값(0 줄) |

## 이 slice 가 만들지 않은 것

- **새 test 클래스 0** — 기존 두 클래스에 메서드만 더했으므로 `config/quality/gate-tests.properties`
  편집이 없다(새 클래스를 만드는 커밋에서 함께 등재해야 하는 규율의 대상이 아니다).
- **게이트 술어·임계 변경 0** — `EXPECTED_EXEMPT_COUNT`·면제 목록·architecture policy 무편집.
- **Flyway `V*.sql` 편집 0**(팀장 지시). 변이는 **버릴 clone 에서만** 했고 worktree 의 V26 은
  base 와 같다.
- **production Kotlin 편집 0** — 이 slice 는 test·문서·셸 주석만 만진다.
- **새 OPEN id 등재 0** — 후보 이름은 scope.md 가 들지만 capability map 은 in_scope 밖이다.

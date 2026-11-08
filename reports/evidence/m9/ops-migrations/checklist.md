# M9 / ops-migrations — 계약 대조

마지막 산출물 커밋 **`6199b39f`** · 착수 base `df4976cc` · **내 병합 1**(main `56bd33ea` — `56bd33ea..HEAD` 구간에서 센 값) · 계약 갱신 2026-11-06 반영(DEC-08 제외 · 번호 V26·V27 ·
in_scope 밖 다섯 승인).

## acceptance 줄별 대조

| 계약 문면 | 상태 | 어디서 |
| --- | --- | --- |
| 각 마이그레이션 clean-migrate | **전건 실측** — `:adapters:test` 전체 **1327 tests · 0 실패**(V25 → V26 → V27 적용 순서 포함). 적용 **순간**의 축도 섰다 — 잠금 대기 상한이 **migrate 연결**에 걸린다(v r6 V-1). 전건 `check` 는 PR CI | `commands.md` 라운드 6·7·8 표 |
| CHECK·GRANT 등식 | 래칫 갱신 완료 — CHECK 개수(`collection_run` 13+1 · `outbox` 11+1+1, **증분 주석**) · 열 셋 · 트리거 셋. **GRANT 변경 0**(두 축이 열·제약·트리거만 더한다) | `CleanMigration{Check,Column,Trigger}Test` |
| 되돌림 리허설 두 갈래 | **둘 다 실측** — 미적용(목록 산출 + 트리 동일성) · 적용(버릴 Postgres 에서 forward-fix 를 **Flyway 로** 적용, 장부 기입·음성 대조 포함, 팀장 허용 1회) | `rollback.md` §3 / §5 |
| 기본값 제거로 깨지는 배선 전수 갱신 | **기동점 전수를 다시 셌다**(v H-1) — 6G runbook 의 스냅숏 추출 갈래가 빠져 있었고, 키를 공통 인자 **한 자리**로 모아 갈래가 늘어도 함께 가게 했다. 배선 test·출하 조립 속성 자리는 로컬 exit 0. **아직 키가 없는 기동점 하나**(`tools/image-hygiene-check.sh` jvm-app `PROBE_ENV`, 오늘 무해)는 `OPEN-M9-REQUIRED-KEY-ENTRYPOINT-CENSUS` 문면이 든다 | `commands.md` 「in_scope 밖」 표 · capability map 그 OPEN 행 |
| 변이 RED | **열여섯** 로컬 실측 — 라운드 1~3 의 셋(사유 바꿔치우기 · 릴리스 결속 상수화 · 생성자 기본값 복귀) · 라운드 4 의 셋(`gap_all` · V27 outbox 트리거 UPDATE 전용 · 출하 리소스 기본값) · 라운드 5 의 둘(출하 기동점 default properties · env 제거 무력화) · 라운드 7 의 넷(V26 상한 삭제 · 릴리스 바꿔치기 허용 · outbox 트리거 `BEFORE INSERT` · 출하 초기화자 기본값) · **라운드 8 의 넷**(`initSql` 제거 · V27 선언 제거 · 선언 값 `'0'` · 선언을 DDL 뒤로) + 통제군 | `commands.md` 변이 절 |
| 전건 PR CI | **미실행** — push 는 팀장 몫(공통 규율) | — |

## 설계 검토가 지목한 우회 ↔ 무엇이 막는가

| 우회 | 막는 것 |
| --- | --- |
| OPS-05 ① 사유 없이 종단 | 종단 명령이 사유만 받는다 — **컴파일되지 않는다**. 출하 경로 **밖**(원시 INSERT·UPDATE)은 관문 트리거가 막고, 그 거부를 종단 셋 전수로 재는 test 가 있다(v M-1) |
| OPS-05 ② 비종단 행에 사유 | 「사유가 있으면 종단」 CHECK |
| OPS-05 ③ 사유↔상태 어긋남 | 쌍 CHECK + 쌍 집합 등식 test(본문에서 쌍을 뽑아 Kotlin 전수와 대조) |
| OPS-05 ④ 어댑터가 어휘를 짓는다 | 어댑터는 사유 값의 이름만 바인딩한다(SQL 리터럴 0) |
| OPS-05 ⑤ 미지 payload 재접기 | `UNKNOWN_PAYLOAD` 별 사유 + 기존 계수와 같은 수를 잼 |
| OPS-05 ⑥ 고아가 사유 없이 | `ORPHANED_CLAIM` |
| OPS-10 ① 기본값이 다른 이름으로 | 키 하나·기본값 0 + 값 객체가 공백 거부. 「다른 **층**에서 되살리기」는 **출하 기동점(`productionApplication()`)이 준비한 환경**에 그 키가 없음을 재는 test 가 막는다 — default properties·ConfigData·시스템 속성·명령행이 그 한 환경에 쌓인다(v M-3 · v N-1) |
| OPS-10 ② 쓰기 경로가 NULL | INSERT 거부 트리거(두 표) — 거부 실측도 **두 표**다(v M-1 이 outbox 쪽을 더했다) |
| OPS-10 ③ 기존 행에 지어낸 값 | backfill 0 — NULL 이 「모름」이다 |
| OPS-10 ④ 키 셋 | 키 하나(수집 둘 제거) |
| OPS-10 ⑤ compose·runbook 미갱신 | 기동 실패 + 전수 갱신(열거표) |
| OPS-10 ⑥ test 배선이 기본값에 기댄다 | 배선 test 일곱·출하 조립 19 자리 갱신, 로컬 exit 0 |
| OPS-10 ⑦ 적힌 릴리스를 나중에 **덮는다** | 트리거가 「한 번만 적는다」로 지우기·바꿔치기를 함께 거부(cr M-1). 앱 역할이 UPDATE 권한을 가진 표가 `outbox` 하나라 그 길이 실제로 열려 있었다 |
| 두 축 공통 ⑧ 마이그레이션이 **기동을 멈춘다** | **migrate 연결의 `lock_timeout`**(`PersistenceWiring.ownerMigration` 의 `initSql`) — 연결이 **기본값**을 깔아 표기와 무관하게, 선언을 잊어도 전수가 그 값으로 시작한다. **깔개이지 하한이 아니다**(cr r4 M-1): 파일의 `SET LOCAL`(또는 `SET`·`RESET`)은 그 값을 덮을 수 있고 — 오늘 선언은 열둘 전부 `'5s'` — 「의도적 덮어쓰기」는 관례 lint 가 **기존 표 세 모양**에 한해 본다. 거동은 `MigrationLockBoundTest` 가 **상한을 선언하지 않은 파일**(V14)에서 잰다. 파일의 `SET LOCAL` 과 그 전수를 세는 census 는 **관례 lint** 이고 안전망이 아니다(v r6 V-1 정정 — 정규식이 평범한 표기 열에 빗나갔다) |

## 설계 검토의 「과잉·미달」 대조

- 과잉 후보 둘(시도 계수 · 입력 스냅샷 해시)은 **두지 않았다** — 계약과 OPEN 의 자리다.
- 미달 1(커밋 경로 외 판정 미기록)은 DEC-08 축과 함께 빠졌다 — 받는 slice 의 입력이다.
- 미달 2(릴리스 NULL 인 기존 행)는 알려진 제한 + 트리거가 이후 행을 막는다.
- 미달 3(`api_request_audit` 릴리스 칸 부재)은 계약이 지목하지 않은 표라 손대지 않았다 —
  요청 감사 행은 `correlation_id` 로 묶이고 릴리스 축은 그 표의 요구가 아니다.

## 수정 라운드가 만든 새 파일 ↔ in_scope 대조

이 slice 가 만든 새 파일 **일곱**(V26·V27 · `ReleaseIdentifier` · `ReleaseWiring` · 사유 enum ·
`ExecutionRowReleaseTest` · `ReleaseWiringTest`) 전부 계약의 in_scope 안이고, test 둘은 만드는
커밋에서 게이트 등록부에 함께 등재했다. **수정 라운드 4·5·7 이 만든 새 파일은 0 이고 라운드 8 이 하나다**(`MigrationLockBoundTest` — 만드는 커밋에서 게이트 등록부에 함께 등재했다) — 라운드 4 는
기존 파일 아홉, 라운드 5 는 `ReleaseWiringTest` 하나, 라운드 7 은 기존 파일 아홉을 고쳤다. 세 라운드
모두 **새 test 클래스가 없어** 게이트 등록부 편집도 0 이고, 새 test 들(라운드 7 의 넷 포함)은 이미
등재된 클래스에 메서드로 들어갔다. 새로 건드린 in_scope 경로는 라운드 4 의 둘
(`config/quality/architecture-policy.properties` · `AppHttpDependencyGateTest` — 둘 다 계약 문면
「`config/quality/**`」·「관련 app 코드와 test」 안)과 라운드 7 의 하나(`MigrationLockTimeoutTest`)이고,
라운드 5 는 더하지 않았다.

**라운드 6(병합)이 만든 새 파일도 0 이다** — main 이 들여온 새 파일은 많지만 내 커밋이 만든 것은
없고, 내가 고친 것은 병합 결과 둘(`CleanMigrationCheckTest` 의 본문 집합 · 남의 새 배선 test 에
릴리스 빈)과 내 문면 하나(여정 하네스 주석)다. 복원 목록이 106 → **107** 로 하나 는 것이 그
배선 test 가 내 경로로 들어온 자리다.

라운드 2 가 **되돌린** 넷(판정 표·Jdbc store·기록 port·그 test)은 HEAD 에 없다 — 되돌림 목록의
`HISTORY_ONLY` **여섯**이 그 넷과 번호 당김이 남긴 옛 이름 둘이고, `rollback.md` §1 이 그 분류를
적는다. in_scope 밖으로 나간 것은 `commands.md` 의 표 다섯(compose·env 템플릿·tools·CI·여정
하네스)이고 그 근거는 같은 표가 적는다.

## 하네스 레인 변경

**없다.** `.claude/**` · `CLAUDE.md` 무편집.

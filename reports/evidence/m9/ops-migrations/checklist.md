# M9 / ops-migrations — 계약 대조

마지막 산출물 커밋 **`41358ce1`** · base `df4976cc` · 계약 갱신 2026-11-06 반영(DEC-08 제외 · 번호 V26·V27).

## acceptance 줄별 대조

| 계약 문면 | 상태 | 어디서 |
| --- | --- | --- |
| 각 마이그레이션 clean-migrate | **CI 정본** — 두 V 파일이 빈 컨테이너 migrate 경로에 든다(`CleanMigration*Test`). 로컬 미실행(container 규율) | PR CI Kotlin `check` |
| CHECK·GRANT 등식 | 래칫 갱신 완료 — CHECK 개수(`collection_run` 13+1 · `outbox` 11+1+1, **증분 주석**) · 열 셋 · 트리거 셋. **GRANT 변경 0**(두 축이 열·제약·트리거만 더한다) | `CleanMigration{Check,Column,Trigger}Test` |
| 되돌림 리허설 두 갈래 | **미적용 갈래 실측**(파일 삭제 + 트리 동일성) · **적용 갈래는 모양만**(forward-fix V) — 실행은 container 가 필요해 못 돌렸다 | `rollback.md` §3 / §2 |
| 기본값 제거로 깨지는 배선 전수 갱신 | 완료 — 배선 test 일곱 로컬 exit 0, 출하 조립 속성 전수 19 자리, compose·CI·tools·runbook 둘 | `commands.md` 「in_scope 밖」 표 |
| 변이 RED | 둘 로컬 실측(사유 바꿔치우기 · 기록 호출 삭제) + 통제군 | `commands.md` 변이 절 |
| 전건 PR CI | **미실행** — push 는 팀장 몫(공통 규율) | — |

## 설계 검토가 지목한 우회 ↔ 무엇이 막는가

| 우회 | 막는 것 |
| --- | --- |
| OPS-05 ① 사유 없이 종단 | 종단 명령이 사유만 받는다 — **컴파일되지 않는다** |
| OPS-05 ② 비종단 행에 사유 | 「사유가 있으면 종단」 CHECK |
| OPS-05 ③ 사유↔상태 어긋남 | 쌍 CHECK + 쌍 집합 등식 test(본문에서 쌍을 뽑아 Kotlin 전수와 대조) |
| OPS-05 ④ 어댑터가 어휘를 짓는다 | 어댑터는 사유 값의 이름만 바인딩한다(SQL 리터럴 0) |
| OPS-05 ⑤ 미지 payload 재접기 | `UNKNOWN_PAYLOAD` 별 사유 + 기존 계수와 같은 수를 잼 |
| OPS-05 ⑥ 고아가 사유 없이 | `ORPHANED_CLAIM` |
| OPS-10 ① 기본값이 다른 이름으로 | 키 하나·기본값 0 + 값 객체가 공백 거부 |
| OPS-10 ② 쓰기 경로가 NULL | INSERT 거부 트리거(두 표) |
| OPS-10 ③ 기존 행에 지어낸 값 | backfill 0 — NULL 이 「모름」이다 |
| OPS-10 ④ 키 셋 | 키 하나(수집 둘 제거) |
| OPS-10 ⑤ compose·runbook 미갱신 | 기동 실패 + 전수 갱신(열거표) |
| OPS-10 ⑥ test 배선이 기본값에 기댄다 | 배선 test 일곱·출하 조립 19 자리 갱신, 로컬 exit 0 |

## 설계 검토의 「과잉·미달」 대조

- 과잉 후보 둘(시도 계수 · 입력 스냅샷 해시)은 **두지 않았다** — 계약과 OPEN 의 자리다.
- 미달 1(커밋 경로 외 판정 미기록)은 DEC-08 축과 함께 빠졌다 — 받는 slice 의 입력이다.
- 미달 2(릴리스 NULL 인 기존 행)는 알려진 제한 + 트리거가 이후 행을 막는다.
- 미달 3(`api_request_audit` 릴리스 칸 부재)은 계약이 지목하지 않은 표라 손대지 않았다 —
  요청 감사 행은 `correlation_id` 로 묶이고 릴리스 축은 그 표의 요구가 아니다.

## 수정 라운드가 만든 새 파일 ↔ in_scope 대조

이 slice 가 만든 새 파일 **다섯**(V26·V27 · `ReleaseIdentifier` · `ReleaseWiring` · 사유 enum ·
`ExecutionRowReleaseTest`) 전부 계약의 in_scope 안이다. 라운드 2 가 **되돌린** 넷(판정 표·Jdbc
store·기록 port·그 test)은 HEAD 에 없다 — 되돌림 목록의 `HISTORY_ONLY` 가 그 셋이고 `rollback.md`
§1 이 그 분류를 적는다. in_scope 밖으로 나간 것은 `commands.md` 의 표 다섯
(compose·env 템플릿·tools·CI·runbook 둘)이고 그 근거는 같은 표가 적는다.

## 하네스 레인 변경

**없다.** `.claude/**` · `CLAUDE.md` 무편집.

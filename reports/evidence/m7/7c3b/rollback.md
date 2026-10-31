# M7/7C③-b rollback

base `9463f6e1` · **실측 HEAD `6af4751d`**(이 slice 의 마지막 산출물 커밋). evidence 커밋은 언제나
이 뒤에 오므로 「실측 HEAD == 판정 SHA」가 아니다 — 대조해야 하는 것은 **그 사이에 되돌림 대상이
움직였는가**이고, 그 확인 명령은 §4 다.

## 1. 되돌리는 법

**range revert 가 아니라 in_scope 경로 한정 복원**이다. 목록은 손으로 적지 않고 기계가 낸다.

```
BASE=9463f6e1
# evidence 디렉터리는 뺀다 — 자기 자신을 되돌릴 수 없다.
mapfile -t MODIFIED < <(git diff --name-only --diff-filter=M $BASE..HEAD -- . ':(exclude)reports/evidence/m7/7c3b')
mapfile -t ADDED    < <(git diff --name-only --diff-filter=A $BASE..HEAD -- . ':(exclude)reports/evidence/m7/7c3b')
git restore --source=$BASE --staged --worktree -- "${MODIFIED[@]}"
rm -f "${ADDED[@]}"
```

**마이그레이션이 없다** — V23 은 7C③ 이 이미 세웠고 이 slice 는 스키마·권한·보존 등록부를 건드리지
않았다(`git diff --name-only $BASE..HEAD -- adapters/src/main/resources/db/migration docker deploy`
빈 출력). 그래서 배포 전/후를 가르는 둘째 경로가 없다 — 위 복원 하나가 전부다.

**코드를 되돌리지 않고 끄는 법이 따로 있다**(더 싸다): ⓐ 훑기는 `bidvector.notification-sweep.mode`
를 주지 않으면 러너도 후보 질의도 서지 않는다(출하 기본값이 그것이다) ⓑ 요청 경로 둘은
`NotificationInboxWiring.notificationInboxPort` 빈을 빼면 사라지고, 그때 **기동이 실패한다**
(세 자리가 그 타입을 요구한다) — 반만 켜진 상태가 생기지 않는 자리다.

## 2. 실측 (버릴 clone, `git clone --no-hardlinks`)

| 단계 | 실측 |
| --- | --- |
| ① 명령 종료 코드 | `restore` **0** · `rm` **0** |
| ② 계수 | 복원 **31**(M) · 삭제 **18**(D) — 기계 목록과 같다 |
| ③ 되돌린 경로의 diff | **0 줄** |
| ④⑤⑥ 복원 트리에서 `check` + `qualityBaseline` | **exit 0** |
| ⑦ base 와 남는 차이 | `reports/evidence/m7/7c3b/scope.md` **하나**(팀장 계약 파일 — 의도된 제외) |

**갈음을 「HEAD 초록」으로 하지 않았다** — ④⑤⑥ 은 복원한 트리에서 실제로 돌린 결과다.

### 표지 계수 — 「내 줄 사라짐」과 「남의 줄 남음」 둘 다

복원 뒤 저장소 전체에서 센 파일 수다. 종료 코드가 아니라 이 계수가 완료의 증거다.

| 내 표지(0 이어야 한다) | 복원 뒤 |
| --- | --- |
| `NotificationSweep` · `reflectedNotificationFact` · `asNotificationNoticeId` · `NOTIFICATION_WINDOW_POLICY` · `notificationInboxPort` · `InMemoryNotificationInbox` · `RECORDED_UPDATED` · `NO_ACCOUNT_SUBJECT` | **전부 0** |

| 남의 표지(남아 있어야 한다) | 복원 뒤 |
| --- | --- |
| `NotificationInboxStore`(7C③) | 8 |
| `BidCaseWorkflow`(7B) | 12 |
| `INBOX_RECORD_STATE_DISPLAY`(7C②-b 화면 표) | 2 |
| `AccountScopedRoutes`(7B) | 19 |

## 3. 공유 파일

이 slice 는 공유 파일 **열둘**을 만진다(`config/quality` 둘 · `openapi` 하나 · `docs/discovery` 둘 ·
`web` 다섯 · 공유 래칫 test 셋 가운데 `app/src/test` 쪽). **base `9463f6e1` 에서 복원하는 것이 맞다** —
이 브랜치는 그 뒤로 main 을 병합하지 않았고(`git merge-base --is-ancestor` 로 확인 가능), 그래서
기준이 하나다. **main 을 병합하면 기준은 병합된 main 의 고정 SHA 하나**로 바뀌고 이 문서의 ②~⑦ 을
그 SHA 에서 다시 잰다(기준 둘은 둘 다 바꾼 파일을 빠뜨린다 — 7C⑤-b r1 교훈).

## 4. 실측 HEAD 와 판정 SHA 사이가 움직였는가

evidence 커밋은 실측 뒤에 오므로 둘은 다르다. 유효성은 **되돌림 대상이 그 사이에 움직였는가**로 본다:

```
git diff --name-only 6af4751d..<판정 SHA> -- $(git diff --name-only 9463f6e1..6af4751d -- . ':(exclude)reports/evidence/m7/7c3b')
```

빈 출력이면 이 실측이 유효하고, 한 줄이라도 나오면 **미검증**이다(그때 ②~⑦ 을 다시 잰다).
동결 보고가 그 확인 결과를 적는다.

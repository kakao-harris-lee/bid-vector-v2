# M7/7C⑤ — 되돌리는 법과 그 실측

## 1. 되돌림의 모양

range revert 가 아니라 **in_scope 경로 한정**이다. 수정된 파일은 base 에서 복원하고, 새 파일은 삭제한다
(base 에 없는 경로는 `git restore --source=<base>` 가 성립하지 않는다).

```
BASE=56bb7474
git restore --source=$BASE --staged --worktree -- <M 목록>
rm -- <A 목록>
```

`reports/evidence/m7/7c5/**` 는 되돌림 대상이 아니다(계약 문면). `ml-engine`·`container`·`.github` 는 이 slice 가
만지지 않았다 — 아래 음성 대조가 그 사실을 센다.

## 2. 목록 — 기계 산출

```
git diff --name-status 56bb7474..HEAD | grep -v 'reports/evidence/'
```

- **A 15** = workflow main 넷 + workflow test 하나 + adapters main 둘 + adapters test 하나 +
  app main `http` 둘(공용 질의 읽기 자리 · controller) + app main `wiring` 둘 + app test 셋.
- **M 8** — `ErrorBody.kt` · `NoticeSearchController.kt` · `HttpTestSupport.kt` ·
  `config/quality/architecture-policy.properties` · `config/quality/gate-tests.properties` ·
  `docs/discovery/capability-map.md` · `openapi/bidvector-operator-api.yaml` ·
  `web/src/api/generated/operator-api.ts`.

**목록 등식** — 손 목록을 쓰지 않는다. 실측 스크립트가 같은 명령으로 목록을 **다시 산출**해 쓰므로, 이 문서의
수와 스크립트가 쓴 수가 다르면 그 자체가 신호다(계수를 §4 에 적는다).

**음성 대조(빈 등식이 건전성의 증거가 아니다)** — 「내가 커밋하지 않은 경로가 목록에 들어오는가」를 센다:

```
git diff --name-only 56bb7474..HEAD -- ml-engine/ container/ .github/   # 0
```

## 3. 공유 파일의 사정

M 여덟 가운데 **공유 파일 다섯**(`ErrorBody.kt` · `NoticeSearchController.kt` · `HttpTestSupport.kt` ·
게이트 정책 둘)과 **문서 하나**(capability map)는 다른 레인도 만지는 자리다. 이 브랜치는 **머지 전**이므로
복원 출처는 고정된 base SHA `56bb7474` 이다(rebase 뒤의 merge-base — 움직이는 `origin/main` 을 쓰지 않는다). 머지 뒤 되돌림은
PR revert 로 한다.

`NoticeSearchController.kt` 는 7C①b 레인도 만진다(응답 DTO 쪽). 이 slice 가 만진 hunk 는 질의 읽기
private 메서드 셋(삭제)과 그 호출부뿐이라 겹치지 않는다 — 복원은 그 hunk 를 base 상태(사본 셋이 있는 판)로
돌려놓는다.

## 4. ①~⑥ 실측

(실측 HEAD 와 결과는 마지막 산출물 커밋에서 돌린 뒤 이 절에 적는다 — 앞 라운드 실측을 옮기지 않는다.)

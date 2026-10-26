# M7/7C⑤ — 되돌리는 법과 그 실측

## 1. 되돌림의 모양

range revert 가 아니라 **in_scope 경로 한정**이다. 수정된 파일은 base 에서 복원하고, 새 파일은 삭제한다
(base 에 없는 경로는 `git restore --source=<base>` 가 성립하지 않는다).

```
BASE=46ac0949
git restore --source=$BASE --staged --worktree -- <M 목록>
rm -- <A 목록>
```

`reports/evidence/m7/7c5/**` 는 되돌림 대상이 아니다(계약 문면). **그 밖의 `reports/` 는 대상이다** —
이 slice 는 다른 slice 의 장부(`reports/evidence/m6/6e1/acceptance-trace.md`)도 고쳤고, 목록 필터가
`reports/evidence/` 를 통째로 빼면 그 행이 복원되지 않는다(r1 에서 필터를 `m7/7c5/` 로 좁혔다).
`ml-engine`·`container`·`.github` 는 이 slice 가 만지지 않았다 — 아래 음성 대조가 그 사실을 센다.

## 2. 목록 — 기계 산출

```
git diff --name-status 46ac0949..HEAD | grep -v 'reports/evidence/m7/7c5/'
```

- **A 21** — workflow main 넷 · workflow test 하나 · adapters main 둘 · adapters test 넷(결과 투영 · 원문 축 ·
  의존 게이트 · 공용 fixture) · app main `http` 둘(공용 질의 읽기 자리 · controller) · app main `wiring` 둘 ·
  app test 여섯(이중체 둘 · 계약 · endpoint · 질의 읽기 · 출하 조립 E2E).
- **M 11** — `ErrorBody.kt` · `NoticeSearchController.kt` · `AppHttpDependencyGateTest.kt` ·
  `ConstantTimeComparisonStructureTest.kt` · `HttpTestSupport.kt` ·
  `config/quality/architecture-policy.properties` · `config/quality/gate-tests.properties` ·
  `docs/discovery/capability-map.md` · `openapi/bidvector-operator-api.yaml` ·
  `reports/evidence/m6/6e1/acceptance-trace.md` · `web/src/api/generated/operator-api.ts`.

**목록 등식** — 손 목록을 쓰지 않는다. 실측 스크립트가 같은 명령으로 목록을 **다시 산출**해 쓰므로, 이 문서의
수와 스크립트가 쓴 수가 다르면 그 자체가 신호다(계수를 §4 에 적는다). 라운드마다 다시 산출한다 — r1 의 test
파일 분할이 A 를 19 → **21** 로 늘렸고, 앞 판의 수를 그대로 옮겼다면 새 파일 둘이 복원 목록에서 빠졌다.

**음성 대조(빈 등식이 건전성의 증거가 아니다)** — 「내가 커밋하지 않은 경로가 목록에 들어오는가」를 센다:

```
git diff --name-only 46ac0949..HEAD -- ml-engine/ container/ .github/   # 0
```

## 3. 공유 파일의 사정

M 열하나 가운데 **다른 레인도 만지는 자리가 여덟**이다 — 코드 둘(`ErrorBody.kt` · `NoticeSearchController.kt`) ·
게이트 test 둘(`AppHttpDependencyGateTest.kt` · `ConstantTimeComparisonStructureTest.kt` — 레인 경합이 가장
심한 자리다) · test 지원 하나(`HttpTestSupport.kt`) · 게이트 정책 둘 · 문서 하나(capability map). 추적표
(`acceptance-trace.md`)도 공유 장부다. 이 브랜치는 **머지 전**이므로 복원 출처는 고정된 base SHA
`46ac0949` 이다(**병합** 뒤의 merge-base — rebase 가 아니라 `git merge` 이고, 움직이는 `origin/main` 을 쓰지
않는다). 머지 뒤 되돌림은 PR revert 로 한다.

`NoticeSearchController.kt` 는 7C①b 레인도 만진다(응답 DTO 쪽). 이 slice 가 만진 hunk 는 질의 읽기
private 메서드 셋(삭제)과 그 호출부뿐이라 겹치지 않는다 — 복원은 그 hunk 를 base 상태(사본 셋이 있는 판)로
돌려놓는다.

## 4. ①~⑥ 실측

**실측 HEAD: `6cfda4f2`**(r1 의 마지막 산출물·장부 커밋에서 돌렸다). 버릴 clone 에서 돌고, 이 저장소의 작업
트리는 건드리지 않는다. 목록은 그 clone 안에서 **다시 기계 산출**했다 — 이 문서의 수와 다르면 그 자체가 신호다.

| 단계 | 무엇을 쟀나 | 실측 |
|---|---|---|
| 기계 산출 | `git diff --name-status 46ac0949..HEAD`(내 evidence 제외) | **M 11 · A 21** — §2 의 수와 같다 |
| ① | 되돌림 명령의 종료 코드 | `restore` **0** · `rm` **0** · 빈 디렉터리 정리 **0** |
| ② | 되돌린 트리 ↔ base 의 차이(내 evidence 제외) | **0 줄** — 트리가 base 와 같다 |
| ③ | 산출물 경로의 잔존 | 수정 파일 diff **0 바이트** · 새 파일 잔존 **0** |
| ③b | **남의 표지 보존**(빈 등식이 건전성의 증거가 아니다) | base 의 7C① 질의 읽기 private 사본 참조 **3** 되살아남 · 정책의 내 표지 **0** · 7C⑤-0 등재 **9** 보존 |
| ④ | 되돌린 트리에서 compile | exit **0** |
| ⑤ | 되돌린 트리에서 test(7C① 경로) | exit **0** |
| ⑥ | 되돌린 트리에서 게이트(size · 모듈 의존 · 게이트 등재) | exit **0** (21m55s) |

**③b 가 왜 따로 있나**: ②③ 만 보면 「다 지웠다」와 「너무 많이 지웠다」가 같은 값이다. 그래서 **내 표지가 0**인
것과 **남의 표지가 남아 있는 것**을 함께 센다 — 되돌림이 7C①·7C⑤-0 의 줄을 건드리지 않았다는 사실이 그 수다.

**게이트 등재의 되돌림이 특히 중요하다**: 복원은 `gate-tests.properties` 에서 이 slice 가 등재한 **여덟 줄**(r1 의
새 test 둘 포함)을 함께 지우고, 같은 복원이 test 파일들도 지운다. 둘 중 하나만 돌아가면 ⑥ 의 등재 게이트가
붉어진다 — 그 게이트가 exit 0 인 것이 「짝이 맞게 돌아갔다」의 실측이다.

**실측 HEAD ↔ 판정 SHA**: 그 사이에 되돌림 대상이 움직였는지로 유효성을 본다(두 SHA 가 같을 필요는 없다 —
evidence 커밋은 언제나 뒤에 온다).

```
git diff --name-only 6cfda4f2..<판정 SHA> -- <위 M·A 경로들>   # 빈 출력이어야 유효
```

**실행 결과(r1 동결 시점)**: 아래 §5 에 적는다.

## 5. 유효성 — r1 판정 SHA 기준

(동결 커밋 직후 이 절에 실행 결과를 적는다.)

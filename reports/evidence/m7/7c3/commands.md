# M7/7C③ — 실행 명령과 종료 코드

base `a61ddff1` · 브랜치 `m7-7c3/2026-10-23` · worktree `bid-vector-v2-m7-7c3`.

호스트 규율: 모든 Gradle·npm 명령은 `flock ~/.local/bid-vector-build.lock` 아래 돌렸고 점검 셋
(`pgrep`·`free -m`·`ps`)을 **별도 호출로 먼저** 봤다. 이 라운드 동안 같은 락에 **다른 레인
10~16개가 줄 서 있어** 측정이 직렬로 느리게 났다(available 11~13GB · swap free 6.1~6.6GB
구간에서만 시작했고, 00:30~03:00 창은 피했다).

**아직 실측하지 않은 항목은 `(기록)` 이다** — 돌지 않은 명령의 결과를 적지 않는다.

## 2026-10-23T02:15Z — workflow test (순수 커널·파싱)

```
./gradlew --no-daemon :workflow:test --tests 'bidvector.workflow.notificationinbox.*'
```

`BUILD SUCCESSFUL` · exit 0. test 수는 결과 XML 에서 센다 — `NotificationFactTest` 9 ·
`NotificationPageParsingTest` 12, **실패 0 · skip 0**.

## (기록) — 어댑터 test (실 PostgreSQL · Testcontainers)

```
./gradlew --no-daemon :adapters:test \
  --tests 'bidvector.adapters.notificationinbox.*' \
  --tests 'bidvector.adapters.persistence.CleanMigration*' \
  --tests 'bidvector.adapters.retention.*'
```

`CleanMigration*`·`retention.*` 를 함께 돌리는 이유: 이 slice 가 **표를 더하므로** 스키마 스냅샷
래칫 넉(표·PK·FK·CHECK 개수·권한 행렬)과 보존 분류의 **집합 등식 둘**(레지스트리↔카탈로그 ·
심기 fixture↔분류)이 같은 커밋에서 함께 서야 한다. 결과: (기록)

## (기록) — 생성 타입 재생성 (W-5 바이트 등식)

```
npm ci && npm run api:types     # web/
npm run api:types:check         # 재생성 결과가 커밋된 파일과 바이트 동일한지
```

이 worktree 에 `node_modules` 가 없어 설치부터 돌린다. 결과: (기록)

## (기록) — app test

```
./gradlew --no-daemon :app:test \
  --tests 'bidvector.app.http.SessionAccountTest' \
  --tests 'bidvector.app.http.OpenApiNotificationInboxContractTest'
```

결과: (기록)

## (기록) — 변이 (적용 → 표적 재실행 → 사본 복원)

표적은 `checklist.md` §0.9 의 우회 표다. **더하기만 한 변이는 초록이므로 바꿔치운다**(음성
대조를 함께 남긴다). 변이 **전에 커밋**한다 — 미커밋 산출물이 복원으로 함께 지워지지 않게.

## (기록) — 비밀값 스캔

```
grep -rniE -f config/quality/leak-patterns.txt <이 slice 가 더한 경로들>
```

참조형으로만 돌린다(어휘를 이 파일에 축어로 적지 않는다). 결과: (기록)

## (기록) — clean-tree 게이트

```
git status --porcelain -- <in_scope 개별 인자>
```

빈 출력 + 양성 대조 1회(비파괴 절삭 — `checkout --` 를 쓰지 않는다). 결과: (기록)

## (기록) — acceptance: `check` job · `web` job · `container` job

`ci.yml` 의 job 명령 **그대로**다. 마이그레이션이 있어 app 이미지가 V23 을 싣으므로 `container`
job 도 잰다(생략 불가 — path-limited diff 로 입력 변경을 보인다).

| 항목 | 명령 | 결과 |
|---|---|---|
| `check` job | `./gradlew --no-daemon check` · `./gradlew --no-daemon qualityBaseline` · `./tools/one-command-check.sh` | (기록) |
| `web` job | `npm ci` · `lint` · `typecheck` · `gate:src-tree` · `api:types:check` · `test` · `gate:tests` · `build` · `gate:boundary-canary` · Playwright 스모크 | (기록) |
| `container` job | app `bootJar` + 이미지 빌드 + 위생·취약점 게이트 | (기록) |
| `ml-engine` job | 입력 변경 0 → **생략** | 해당 없음 |

### 7B 머지 전에는 공유 test 둘이 붉다 (D-7C3-1 ① 이 예상한 것 · 실측으로 확인한 원인)

오늘 main 의 두 test 가 **계정 전용 endpoint** 를 전제하지 않는다:

- 본문 거부 전수 test 는 **자격 헤더 하나로 등록된 POST 매핑 전수**를 두드린다. 알림함의 POST
  둘은 그 헤더로 401 이라 전수 집합에 `(401, …)` 이 섞인다.
- 문서 계약 test 는 「인증을 요구하는 operation 은 **전부** 자격 헤더 대안을 남긴다」를 단언한다.
  알림함 operation 셋은 실제로 자격 헤더로 401 이므로 그 대안을 적으면 **문서가 구현보다
  넓어지고**, 적지 않으면 그 test 가 붉다.

**둘은 7B 가 같은 설계 사실(계정 전용 경로)로 이미 고친 축**이고(모집단을 「그 자격으로 실제로
닿는 매핑」으로 가름 + 문서 대안을 계정 전용에 대해 양방향 정확 일치로 조임), 운영자 결정
D-7C3-1 ① 로 **7B → 7C③ 머지 순서**가 정해졌다. 그래서 이 slice 는 그 고침을 **중복 구현하지
않고**, 7B 머지 뒤 rebase 에서 `AccountScopedRoutes.ROOTS` 를 둘로 만든 다음 전체 acceptance 를
잰다. 동결과 판정 SHA 고정은 그 rebase 뒤다.

## (기록) — rollback ①~⑥ (임시 clone, 되돌린 트리)

`rollback.md` 가 목록과 실측 HEAD 를 든다.

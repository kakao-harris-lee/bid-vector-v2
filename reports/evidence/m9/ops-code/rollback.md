# M9/ops-code rollback

착수 base `df4976cc` · **복원 원천 `3ad4f3f6`**(이 브랜치가 실제로 병합한 main 의 고정 SHA) ·
**실측 HEAD `ae248e3f`**(마지막 산출물 커밋 = 판정 SHA).

**원천이 착수 base 가 아니다.** 이 브랜치는 수정 라운드 끝에 main `3ad4f3f6` 을 병합했으므로
공유 파일 셋(§2)에는 다른 slice 의 줄이 들어왔다. 그래서 복원은 **움직이는 `origin/main` 이
아니라 그 고정 SHA** 에서 한다. 실행 전 단언 둘을 명령으로 실측했다:

- `git merge-base --is-ancestor 3ad4f3f6 HEAD` — **참**(원천이 HEAD 의 조상이다).
- `git merge-base --is-ancestor c724e1ee 3ad4f3f6` — **거짓**(원천이 이 slice 의 **첫 산출물
  커밋**을 담지 않는다). 그래서 main 으로부터 만든 트리가 남의 줄을 떨어뜨릴 수 없고, 커밋 해시
  목록이 필요 없다.

**마이그레이션이 없다.** 이 slice 는 DB 스키마를 건드리지 않는다 — 알림함 표(V23)·계정 표는
**이미 있는 표**이고 이 slice 가 더한 것은 그 표에 **쓰는 생산자**다. 그래서 「적용 DB 되돌림」
갈래가 없고 되돌림은 파일 복원 하나다. 되돌린 뒤 남는 `notification_inbox` 행은 **파생 기록**
이다(다음 훑기·평가가 다시 적는다) — 읽기 경로가 그 어휘(`NEW_CANDIDATE`)를 7C③ 부터 이미 알고
있어 화면이 깨지지 않는다.

## 1. 되돌리는 법

**range revert 가 아니라 in_scope 경로 한정 복원**이다. 목록은 손으로 적지 않고 기계가 낸다.

```
BASE=3ad4f3f6                                    # 병합한 main 의 고정 SHA
EXCL=':(exclude)reports/evidence/m9/ops-code'    # 자기 자신은 되돌릴 수 없다

# 목록은 **내 커밋만** — 병합 커밋의 해소 줄은 들지 않는다
mapfile -t MINE < <(git log --no-merges --format= --name-only $BASE..HEAD -- . "$EXCL" | sort -u)
mapfile -t MODIFIED < <(for f in "${MINE[@]}"; do git cat-file -e "$BASE:$f" 2>/dev/null && echo "$f"; done)
mapfile -t ADDED    < <(for f in "${MINE[@]}"; do git cat-file -e "$BASE:$f" 2>/dev/null || echo "$f"; done)

git restore --source=$BASE --staged --worktree -- "${MODIFIED[@]}"
git rm -q -f -- "${ADDED[@]}"
```

「있음/없음」을 `--diff-filter` 가 아니라 **`git cat-file -e`** 로 가른다: 필터는 범위의 양 끝을
비교하므로 병합이 끼면 남의 변경을 M 으로 센다. 원천에 그 경로가 있는지만 물으면 병합과 무관하다.

`git rm -f`(단순 `rm` 이 아니라)인 이유: 색인까지 지워야 복원 뒤 색인이 원천과 같아진다.

**hunk 격리가 필요 없다.** 공유 파일 셋을 **원천 트리에서 통째로 복원**하기 때문이다 — 그 트리는
main 이 만든 것이라 다른 slice 의 줄이 이미 들어 있고 내 줄만 사라진다. 자기 커밋 해시로 hunk 를
역적용하는 경로는 쓰지 않았다(병합 커밋의 해소 줄이 `--no-merges` 목록 밖이라 끝까지 완료되지
않는다 — 그 실패가 2026-10-09 에 실측됐다).

## 2. 실측 — 버릴 clone(`git clone --no-hardlinks`)에서 `ae248e3f` 체크아웃

| 단계 | 실측 |
| --- | --- |
| ① 명령 종료 코드 | `restore` **0** · `git rm` **0** |
| ② 계수 | 내 커밋 경로 **38**(evidence 제외) = 복원 **27**(M) · 삭제 **11**(A) — 기계 목록과 같다 |
| ③ 원천과의 diff | 복원 경로 전부에서 **빈 출력** |
| ③′ 작업 트리·색인·미추적 | 원천과 다른 파일이 `reports/evidence/m9/ops-code/**` **넷**뿐 · 미추적 **0** |
| ④⑤⑥ 복원 트리의 컴파일·test·게이트 | **트리 동일성으로 갈음** — 아래 |
| ⑦ 원천과 남는 차이 | 위 넷(의도된 제외 — `EXCL` 이 디렉터리 전체를 제외한다). 유효성은 §4 의 술어가 되돌림 대상에서 evidence 를 빼므로 그 수에 흔들리지 않는다 |

**공유 파일은 셋이다** — `config/quality/gate-tests.properties`(등록부) ·
`reports/evidence/m6/6e1/acceptance-trace.md`(장부) · `docs/discovery/capability-map.md`. 셋 모두
원천 `3ad4f3f6` 에 다른 slice 의 줄이 있고, 복원이 그 줄을 보존하는지를 아래 표지 계수가 잰다.

### ④⑤⑥ 의 갈음 — 「HEAD 초록」이 아니라 트리 동일성

**evidence 를 포함해** 복원하면 작업 트리의 트리 객체 해시가 원천 `3ad4f3f6` 의 트리 해시와
**같다**(둘 다 `810cacbd9284cf80b13711a48cb39af1eec735fa` — 같은 clone 에서 경로 42 로 실측).
파일 단위 비교가 아니라 트리 객체의 동일성이므로 「빠뜨린 파일」이 있을 수 없다. `3ad4f3f6` 은
병합된 main 이고 CI 가 초록으로 통과한 자리라, 그 트리에서 `check` 를 다시 돌리는 것은 같은
트리를 다시 재는 일이다.

실제 되돌림(§1)은 evidence 를 **남기므로** 그 트리는 원천 트리 + 이 디렉터리 넷이다. **같지 않은
입력에 이름을 붙인다**: 그 넷이고, 빌드 입력이 **맞다**(누출 게이트가 `reports/evidence` 를
스캔한다). 그 축은 이 slice 의 HEAD(그 파일들을 **포함한** 상위집합)에서 게이트가 초록임을
실측해 두었다(`commands.md` §6).

### 표지 계수 — 「내 줄 사라짐」과 「남의 줄 남음」 둘 다

복원 뒤 저장소에서 센 **파일 수**다. 종료 코드가 아니라 이 계수가 완료의 증거다. 내 evidence
디렉터리만 제외하고 셌다 — 그 디렉터리는 의도된 제외이고 그 안에 이 이름들이 인용으로 남는다.

| 내 표지(0 이어야 한다) | 복원 뒤 |
| --- | --- |
| `AccessBlocked` | **0** |
| `KonepsCallPacer` | **0** |
| `OpeningWalkDirective` | **0** |
| `InboxRecordingNotifications` | **0** |
| `NotificationAudienceSql` | **0** |
| `JdbcNotificationAudience` | **0** |
| `KonepsShippedPolicyWireTest` | **0** |
| `NotificationAudienceFailureTest` | **0** |
| `EvaluationCommitInboxProductionTest` | **0** |

표지를 고를 때 `NEW_CANDIDATE`·`NotificationInboxRecord`·`notification_inbox` 는 쓸 수 없다 —
**7C③ 이 이미 들고 있어 남의 줄까지 센다.** `NotificationAudiencePort` 도 쓰지 않았다: 그 선언이
7C③ 의 파일 안에 살아서 복원 뒤 **파일은 남고 선언만 사라지므로** 「파일 수 0」이 성립하지 않는다.
그래서 이 slice 가 **처음 들여온 이름들**로만 골랐다.

| 남의 표지(남아 있어야 한다) | 복원 뒤 / 원천 |
| --- | --- |
| `config/quality/gate-tests.properties` 줄 수 | **1069 / 1069** |
| `reports/evidence/m6/6e1/acceptance-trace.md` 줄 수 | **246 / 246** |
| `docs/discovery/capability-map.md` 줄 수 | **3971 / 3971** |
| `MockKonepsRoutingTest`(main 이 등재한 것) | **5** |
| `KonepsEndpointDefaultsTest`(같음) | **9** |
| `NotificationInboxStore`(7C③) | **16** |
| `EvaluationCommitRunE2ETest`(6F-10 · PR #127 축) | **10** |

공유 파일 셋이 복원 목록 안이라 **그 셋을 다시 만지면 재측정이다** — §4 가 그 술어를 든다. 새
test 클래스의 등재 한 줄도 그렇다.

## 3. 코드를 되돌리지 않고 끄는 법

**셋 있고 전부 한 자리다.**

| 끄는 것 | 어떻게 |
| --- | --- |
| OPS-09 상태 축 | 전송 정책의 `statusRules = emptyList()` — 모든 수신 응답이 봉투 해석으로 가고 앞 판의 거동으로 돌아간다. 표 등식 test 와 **출하 정책을 읽는 test** 가 붉어진다(끔이 조용하지 않다 — 403 행 하나만 지우는 변이로 실측했다) |
| OPS-08 간격 | 전송 정책의 `minimumCallInterval = Duration.ZERO` — 기다림이 0 회가 된다. **그 값을 끄면 출하 값을 읽는 test 가 붉어진다**(변이로 실측). 계수 기반 상한은 그대로 남는다 |
| NOTI-01 생산자 | **조용히 끄는 자리가 없다.** 청중 빈을 빼면 평가 커밋 배선이 서지 않는다(기동 실패) — 끄려면 청중 어댑터를 빈 목록 구현으로 갈아 꽂는 것이고(기록 0 건, 평가·채널은 정상) 그때 실 DB test 의 두 단언이 붉어진다 |

## 4. 실측 HEAD 와 판정 SHA 사이가 움직였는가

evidence 커밋은 실측 뒤에 오므로 유효성은 **되돌림 대상이 그 사이에 움직였는가**로 본다:

```
git diff --name-only ae248e3f..<판정 SHA> -- \
  $(git log --no-merges --format= --name-only 3ad4f3f6..ae248e3f \
      -- . ':(exclude)reports/evidence/m9/ops-code' | sort -u)
```

빈 출력이면 이 실측이 유효하고, 한 줄이라도 나오면 **미검증**이다(그때 ①~③′ 과 표지 계수를 다시
잰다). 이 라운드는 실측 HEAD 와 판정 SHA 가 **같은 커밋**이고, 뒤따르는 evidence 커밋은 제외된
디렉터리만 만진다 — 동결 보고가 그 확인 결과를 적는다.

**제외가 왜 술어의 일부인가**: evidence 는 실측 **뒤에** 커밋되고 그 파일들 자신이 내 커밋 경로
집합에 들어 있다. 제외하지 않으면 evidence 를 쓰는 행위가 매번 자기 실측을 무효로 만들어 술어가
영원히 참이 되지 못한다. 제외는 evidence 가 **되돌림 대상이 아니라는** 사실의 표기이고, 되돌림
대상(산출물 38 경로)은 그대로 술어 안에 있다.

**앞 라운드 실측을 옮기지 않았다.** 2차 제출의 실측 HEAD 는 `fe17d7d6`·원천은 `df4976cc` 였고
main 병합으로 **원천과 목록이 둘 다 바뀌었다**(복원 경로 30 → 38). 이 절의 수는 **다시 잰** 것이다.

## 하네스 레인 변경

계약이 명시한 것 외 없다. `.claude/**` · `CLAUDE.md` 를 건드리지 않았다.

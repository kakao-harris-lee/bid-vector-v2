# M9/ops-code rollback

착수 base **`df4976cc`** · **복원 원천 `df4976cc`** · **실측 HEAD `fe17d7d6`**(마지막 산출물 커밋).

**원천이 착수 base 와 같다.** 이 브랜치는 `origin/main` 을 합치지 않았다 — 병합 커밋이 0 이므로
`git log --no-merges … ^df4976cc` 와 `git diff --name-status df4976cc..HEAD` 의 경로 집합이
**같다**(`comm -23` 빈 출력으로 확인). 그래서 base 에서 복원해도 남의 변경을 지우지 않는다.
브랜치가 나중에 main 을 합치면 이 전제가 깨지므로 그때 원천을 **합친 main 의 고정 SHA** 로
다시 잡고 ①~③ 을 다시 잰다.

**마이그레이션이 없다.** 이 slice 는 DB 스키마를 건드리지 않는다 — 알림함 표(V23)·계정 표는
**이미 있는 표**이고 이 slice 가 더한 것은 그 표에 **쓰는 생산자**다. 그래서 「적용 DB 되돌림」
갈래가 없고 되돌림은 파일 복원 하나다. 되돌린 뒤 남는 `notification_inbox` 행은 **파생 기록**
이다(다음 훑기·평가가 다시 적는다) — 지우지 않아도 읽기 경로가 그 어휘(`NEW_CANDIDATE`)를
7C③ 부터 이미 알고 있어 화면이 깨지지 않는다.

evidence 커밋은 언제나 실측 뒤에 오므로 「실측 HEAD == 판정 SHA」가 아니다 — 대조해야 하는 것은
**그 사이에 되돌림 대상이 움직였는가**이고, 그 확인 명령은 §4 다.

## 1. 되돌리는 법

**range revert 가 아니라 in_scope 경로 한정 복원**이다. 목록은 손으로 적지 않고 기계가 낸다.

```
BASE=df4976cc

# 목록은 **내 커밋만** — 병합이 0 이라 diff 와 같지만 등식을 전제로 두지 않는다
mapfile -t MINE < <(git log --no-merges --format= --name-only $BASE..HEAD | sort -u)
mapfile -t MODIFIED < <(for f in "${MINE[@]}"; do git cat-file -e "$BASE:$f" 2>/dev/null && echo "$f"; done)
mapfile -t ADDED    < <(for f in "${MINE[@]}"; do git cat-file -e "$BASE:$f" 2>/dev/null || echo "$f"; done)

git restore --source=$BASE --staged --worktree -- "${MODIFIED[@]}"
git rm -q -f -- "${ADDED[@]}"
```

「있음/없음」을 `--diff-filter` 가 아니라 **`git cat-file -e`** 로 가른다: 필터는 범위의 양 끝을
비교하므로 나중에 병합이 끼면 남의 변경을 M 으로 센다. 원천에 그 경로가 있는지만 물으면 병합과
무관하다.

`git rm -f`(단순 `rm` 이 아니라)인 이유: 색인까지 지워야 복원 뒤 색인이 base 와 같아진다.

## 2. 실측 — 버릴 clone(`git clone --no-hardlinks`)

| 단계 | 실측 |
| --- | --- |
| ① 명령 종료 코드 | `restore` **0** |
| ② 계수 | 경로 **34** = 복원 **22**(M) · 삭제 **12**(A) — 기계 목록과 같다 |
| ③ base 와의 diff | 복원 경로 전부에서 **빈 출력** |
| ④⑤⑥ 복원 트리의 컴파일·test·게이트 | **트리 동일성으로 갈음** — 아래 |
| ⑦ 원천과 남는 차이 | **실측 시점(`fe17d7d6`)에 0** — 이 slice 가 만진 전부가 복원 목록 안이다 |

실측은 clone 에서 `fe17d7d6`(마지막 산출물 커밋)을 체크아웃한 뒤 돌렸다.

### ④⑤⑥ 의 갈음 — 「HEAD 초록」이 아니라 트리 동일성

복원한 **작업 트리 전체의 해시가 `df4976cc` 의 트리 해시와 같다**(둘 다
`29e4039d9bcf2dd592894b1a8bb95527c4420942`). 파일 단위 비교가 아니라 트리 객체의 동일성이므로
「빠뜨린 파일」이 있을 수 없다. `df4976cc` 는 병합된 main 이고 CI 가 초록으로 통과한 자리라,
복원 트리에서 `check` 를 다시 돌리는 것은 같은 트리를 다시 재는 일이다.

**같지 않은 입력이 없다**(⑦ 이 0 이다). 그 뒤의 evidence 커밋은 §4 의 술어가 되돌림 대상에서
빠져 있는지로 유효성을 본다 — evidence 는 빌드 입력이 **맞다**(누출 게이트가 `reports/evidence`
를 스캔한다). 그 축은 이 slice 의 HEAD(그 파일들을 **포함한** 상위집합)에서 그 게이트가 초록임을
실측해 두었다(`commands.md` §6).

### 표지 계수 — 「내 줄 사라짐」과 「남의 줄 남음」 둘 다

복원 뒤 저장소 전체에서 센 **파일 수**다. 종료 코드가 아니라 이 계수가 완료의 증거다.

| 내 표지(0 이어야 한다) | 복원 뒤 |
| --- | --- |
| `AccessBlocked` | **0** |
| `KonepsCallPacer` | **0** |
| `InboxRecordingNotifications` | **0** |
| `NotificationAudienceSql` | **0** |
| `JdbcNotificationAudience` | **0** |
| `EvaluationCommitInboxProductionTest` | **0** |

표지를 고를 때 `NEW_CANDIDATE`·`NotificationInboxRecord`·`notification_inbox` 는 쓸 수 없다 —
**7C③ 이 이미 들고 있어 남의 줄까지 센다.** `NotificationAudiencePort` 도 쓰지 않았다: 그 선언이
7C③ 의 파일 안에 살아서 복원 뒤 **파일은 남고 선언만 사라지므로** 「파일 수 0」이 성립하지 않는다.
그래서 이 slice 가 **처음 들여온 이름들**로만 골랐고, 그 가운데 둘(`NotificationAudienceSql`·
`JdbcNotificationAudience`)이 청중 축의 표지다.

| 남의 표지(남아 있어야 한다) | 복원 뒤 |
| --- | --- |
| `config/quality/gate-tests.properties` 줄 수 | **1066** |
| `reports/evidence/m6/6e1/acceptance-trace.md` 줄 수 | **228** |
| `NotificationInboxStore`(7C③) | **9** |
| `EvaluationCommitRunE2ETest`(6F-10) | **3** |

공유 파일 둘(등록부·장부)이 복원 목록 안이라 **그 둘을 다시 만지면 재측정이다** — §4 가 그
술어를 든다.

## 3. 코드를 되돌리지 않고 끄는 법

**셋 있고 전부 한 자리다.**

| 끄는 것 | 어떻게 |
| --- | --- |
| OPS-09 상태 축 | 전송 정책의 `statusRules = emptyList()` — 모든 수신 응답이 봉투 해석으로 가고 앞 판의 거동으로 돌아간다. 표 등식 test 와 403·401 test 가 **붉어진다**(끔이 조용하지 않다) |
| OPS-08 간격 | 전송 정책의 `minimumCallInterval = Duration.ZERO` — 기다림이 0 회가 된다(`KonepsCallPacer` 가 그 값을 끄는 자리로 들고 있고 test 가 그 거동을 고정한다). 계수 기반 상한은 그대로 남는다 |
| NOTI-01 생산자 | **조용히 끄는 자리가 없다.** 청중 빈을 빼면 평가 커밋 배선이 서지 않는다(기동 실패) — 끄려면 청중 어댑터를 빈 목록 구현으로 갈아 꽂는 것이고(기록 0 건, 평가·채널은 정상) 그때 실 DB test 의 두 단언이 붉어진다 |

## 4. 실측 HEAD 와 판정 SHA 사이가 움직였는가

evidence 커밋은 실측 뒤에 오므로 둘은 다르다. 유효성은 **되돌림 대상이 그 사이에 움직였는가**로
본다:

```
git diff --name-only fe17d7d6..<판정 SHA> -- \
  $(git log --no-merges --format= --name-only df4976cc..fe17d7d6 | sort -u)
```

빈 출력이면 이 실측이 유효하고, 한 줄이라도 나오면 **미검증**이다(그때 ①~③ 과 표지 계수를 다시
잰다). 동결 보고가 그 확인 결과를 적는다.

**앞 라운드 실측을 옮기지 않았다.** 첫 제출(evidence `8aa35d79`)의 실측 HEAD 는 `d5f8c0b6` 였고
NOTI-01 배선이 들어오며 무효가 됐다(복원 경로가 21 → 34 로 늘었다) — 이 절의 수는 **다시 잰**
것이다.

## 하네스 레인 변경

계약이 명시한 것 외 없다. `.claude/**` · `CLAUDE.md` 를 건드리지 않았다.

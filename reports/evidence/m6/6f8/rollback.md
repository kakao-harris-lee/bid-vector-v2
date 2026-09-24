# M6/6F-8 rollback

`실측 HEAD: b10ba803`(이 slice 의 마지막 산출물 커밋 — 이후 커밋은 evidence 전용). 앞 실측을 옮기지 않는다.

## 비활성화 (코드를 되돌리지 않고 즉시)

수집은 **기본 꺼짐**이다 — 속성 `bidvector.collection.mode`(환경변수 `BIDVECTOR_COLLECTION_MODE`)가 `once` 가 아니면 러너·서비스 키 바인딩·수집 어댑터가 하나도 올라오지 않는다.
실행 중인 일회성 러너는 프로세스를 끝내면 된다(스케줄러·HTTP endpoint 없음). 복구 시간: 즉시(속성 미설정 = 비활성).

## 개발 DB 폐기 (D-6F8-5 실수집이 만든 데이터)

이 slice 에는 **마이그레이션이 없다**(`db/migration` 무변경) — 스키마를 되돌릴 것이 없다. 실수집이 개발 DB 에 넣은 행(`notice`·`raw_observation`·`collection_run` 등)은 운영 데이터가 아니고,
컨테이너를 통째로 버린다: `docker rm -f -v bid-vector-v2-dev`(볼륨 포함 — 다른 프로젝트 컨테이너를 건드리지 않는다).

## 되돌리는 것 (코드)

`base = 74616367`(`git merge-base HEAD origin/main` — 라운드마다 재산출, base 가 움직이면 옛 base 로 산출한 목록은 exit 0 이면서 틀린다). 목록은 기계 산출:

```
git diff --name-status <base>..<실측 HEAD> -- . ':!reports/evidence' ':!milestone-6.md' ':!config/quality/gate-tests.properties' ':!config/quality/architecture-policy.properties' ':!docs/discovery/data-dictionary.md'
```

신규(A) 37 · 변경(M) 11 = 48개. **산출물이 바뀔 때마다 이 명령을 다시 돌려 목록을 재산출한다**(목록이 낡는 것이 이 결함의 실제 원인이다).

```
git restore --source=<base> --staged --worktree -- <위 목록의 경로, 개별 인자>
```

(`--source` 에 없는 신규 경로는 삭제되므로 별도 `git rm` 이 없다. 하네스 경로 `CLAUDE.md`·`.claude/**` 는 이 slice 가 만지지 않았다 — `git log --oneline <base>..HEAD -- CLAUDE.md .claude/` 가 빈 출력.)

## 공유 파일 — 커밋 해시 hunk 격리

`git restore` 로 덮지 않는다(다른 slice 의 줄까지 걷는다). 그 파일을 만진 이 slice 의 커밋을 `git log --oneline <base>..<실측 HEAD> -- <파일>` 로 열거하고, **최신부터 역순으로** 하나씩 되돌린다
(위 산출 명령의 `:!reports/evidence` 가 `policy-values.md` 도 목록에서 빼므로 그 파일도 이 표로 되돌린다):

```
git diff <sha>~1..<sha> -- <파일> | git apply -R
```

| 파일 | 이 slice 의 커밋(최신 → 과거) |
|---|---|
| `config/quality/gate-tests.properties` | `1b5b704c` · `9d63f922` · `84c72c12` · `c5c13fde` · `09e13abc` · `7becb41a` · `defcc09d` · `4504483a` |
| `config/quality/architecture-policy.properties` | `1b5b704c` · `e6ccd2e2` · `09e13abc` |
| `docs/discovery/data-dictionary.md` | `0bbc395b` |
| `reports/evidence/m3/3a/policy-values.md` | `d6d8d0a0` |

인접 삽입이라 `--3way` 가 필요할 수 있다는 우려는 **실측으로 닫았다** — 이 slice 의 커밋만 역순으로 걷으면 각 `apply -R` 이 exit 0 · conflict 0 이었다(아래 ②). 수동 해소 절차가 필요해지는 경우는 다른 slice 의 커밋이
같은 자리에 끼어든 뒤인데, 그때는 충돌 블록에서 **이 slice 의 줄(`M6/6F-8` 주석 블록과 그 아래 등재 항목)만 지우고** 나머지를 남긴다.

`milestone-6.md` 는 이 rollback 의 restore 대상이 아니다 — 착수 문단 `a4a73e9e` 는 팀장 레인이 쓴 것이고 되돌려야 하면 팀장이 같은 방식으로 격리한다:
종결 문단 `5b824126` 을 먼저, 착수 문단 `a4a73e9e` 를 다음으로 — `git diff 5b824126~1..5b824126 -- milestone-6.md | git apply -R` 뒤
`git diff a4a73e9e~1..a4a73e9e -- milestone-6.md | git apply -R`(팀장이 버릴 worktree 에서 두 단계 exit 0 · base 와 diff 0줄 실측).

## 임시 worktree 실측 (①~⑥, 버릴 worktree — `git worktree add --detach <scratch> b10ba803` 뒤 이 evidence 파일 셋을 그대로 얹음)

| 축 | 결과 |
|---|---|
| ① `git restore` exit / hunk 격리 exit | 0 / 열세 번 전부 0 |
| ② D/M 수 | D 37 · M 11 (위 목록과 일치) + 공유 파일 넷 hunk 되돌림(충돌 0) — 작업 트리 변경은 D 37 · M 15(그 밖에 얹은 evidence 파일 셋이 `git status` 에 M 으로 더 보인다 — 되돌리지 않는 것) |
| ③ 되돌린 경로 전체(목록 48 + 공유 넷)의 `git diff <base>` | 0줄(완전 일치) |
| ④ 모듈별 compile(`:procurement`·`:workflow`·`:adapters`·`:app` 의 `compileKotlin`+`compileTestKotlin`) | exit 0 |
| ⑤ `:procurement:test :workflow:test :adapters:test :app:test` | exit 0 |
| ⑥ 되돌린 트리에서 `./gradlew --no-daemon check`(전건 — 이 slice 가 닿은 게이트 전부 포함) | exit 0 |

「내 줄이 사라졌다」와 「남의 줄이 남았다」를 **둘 다** 쟀다: 공유 넷을 되돌린 트리에서 이 slice 의 주석·등재 항목 grep 은 네 파일 모두 0건, `gate-tests.properties` 의 등재 항목 수는 base 와 같다
(③ 의 0줄 diff 가 그 둘을 함께 증명한다 — base 가 남의 줄을 담고 있다).

되돌리지 않는 것(명시): 이 slice 의 evidence 디렉터리 `reports/evidence/m6/6f8/**` 와 하네스 레인의 `milestone-6.md` 편집. evidence 는 어떤 게이트도 붉히지 않았다(⑥ 이 evidence 를 남긴 채 exit 0).

## 그 사이 되돌림 대상이 움직였는가 (실측 HEAD ↔ 판정 SHA)

```
git diff --name-only b10ba803..<판정 SHA> -- <위 목록 48개 경로> config/quality/gate-tests.properties config/quality/architecture-policy.properties docs/discovery/data-dictionary.md reports/evidence/m3/3a/policy-values.md
```

이후 커밋이 evidence 파일만 만지면 빈 출력이어야 유효하다. 한 줄이라도 나오면 실측은 낡았고 그 절은 통과가 아니라 미검증이다.

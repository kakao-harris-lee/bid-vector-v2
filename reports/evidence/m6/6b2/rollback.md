# M6/6B-2 — rollback

**실측 HEAD: `36a3bff9`** (이 slice 의 마지막 산출물 커밋 — 복원·hunk 대상을 마지막으로 건드린 커밋).
버릴 clone 에서 ⓪~⑥ 을 그 커밋 위에서 돌렸다. 앞 라운드의 실측을 옮기지 않는다.

> verifier 가 대조할 것은 「실측 HEAD == 판정 SHA」가 아니다(evidence 커밋은 언제나 뒤에 온다).
> **그 사이에 되돌림 대상이 움직였는가**를 본다:
> `git diff --name-only 36a3bff9..<판정 SHA> -- <아래 1단계 목록 여섯 + milestone-6.md>` 가 **빈 출력**이면
> 유효하다.

## 되돌리는 것과 되돌리지 않는 것

**되돌린다** — in_scope 의 산출물 경로 여섯. 목록은 손으로 쓰지 않고
`git diff --name-status e922dc7b..36a3bff9` 에서 기계로 냈다(A 4 = 삭제 대상, M 2 = base 로 복원;
범위의 나머지 A 1·M 1 은 아래 「되돌리지 않는다」와 2단계가 가져간다).
**디렉터리로 접지 않는다** — 접으면 목록이 slice 가 만진 파일보다 넓어진다. 파일 그대로 두면
`comm` 으로 기계 산출과 문서 목록의 등식을 잴 수 있고, ⓪ 이 그것을 잰다.
**목록은 라운드마다 실측 HEAD 에서 다시 낸다** — 목록이 낡는 것이 이 결함의 실제 원인이다.

**되돌리지 않는다**:
- 하네스 경로(`CLAUDE.md`·`.claude/**`) — 이 range 에 변경 0(아래 「하네스 레인 변경」).
- `reports/evidence/m6/6b2/**` — 이 slice 의 evidence. 되돌리면 복구 상태가 「일이 있었다」는 기록을 잃는다.
  이 디렉터리를 남긴 채 게이트가 초록인지는 ⑥ 이 잰다. 누출 게이트의 scanRoot 가 evidence 라 여기가 자주
  붉는 자리인데, 이 slice 의 evidence 는 스캔 어휘를 축어로 담지 않는다(매치 0 — `commands.md` 「게이트 실측」).

## 절차

### 1단계 — in_scope 산출물을 base 로 (경로는 **개별 인자**)

```
git restore --source=e922dc7b --staged --worktree -- \
  .github/workflows/ci.yml \
  docker/compose.yaml \
  docs/runbook/m6-6b2-backup-restore.md \
  tools/db-backup.sh \
  tools/db-rehearsal.sh \
  tools/db-restore.sh
```

`--source` 에 없는 경로(신설 넷)는 삭제되므로 별도 `git rm` 이 필요 없다.
`git checkout <base> -- <경로>` 는 쓰지 않는다 — base 에 없는 경로마다 pathspec 오류로 끊긴다.

### 2단계 — `milestone-6.md` 는 커밋 해시 hunk 격리

공유 문서다. 이 range 에서 그 파일을 만진 커밋은 `git log --oneline e922dc7b..HEAD -- milestone-6.md`
가 내며, 실측 시점에는 **하나**(`93b04488`, 6B-2 착수 문단)였다. 뒤부터 역적용한다:

```
git diff 93b04488~1..93b04488 -- milestone-6.md | git apply -R
```

**라운드가 늘면 이 목록을 다시 낸다.** 커밋이 둘 이상이면 뒤에서부터 차례로 역적용하고, 삽입 지점이
다른 slice 의 문단과 인접해 conflict 가 나면 **6B-2 착수 문단 블록만 지우고 이웃 문단은 남긴다**
(`git apply -R --3way` 도 인접 삽입에서는 자동 해소에 실패한다).

### 3단계 — 확인

「내 줄이 사라졌다」와 **「남의 줄이 남았다」를 둘 다** 잰다. 전자만 재면 과잉 복원이 안 보인다.

## 되돌려도 운영 영향 0

이 slice 의 산출물은 전부 **절차 자산**이다 — production Kotlin·Python 0, 새 마이그레이션 SQL 0,
새 DB 객체 0. 되돌리면 백업·복원·리허설 스크립트 셋과 CI step 하나와 runbook 이 사라지고 compose
프로젝트 이름이 디렉터리 basename 으로 돌아갈 뿐, 돌고 있는 어떤 DB 도 바뀌지 않는다.
리허설은 자기가 만든 일회성 컨테이너 안에서만 쓰고 파괴하므로, 되돌림 시점에 치울 외부 상태도 없다.

## 비활성화(되돌리지 않고 끄는 법)

| 끄고 싶은 것 | 최소 조치 |
|---|---|
| CI 의 리허설 | `ci.yml` `container` job 에서 S-23c step 블록만 지운다(다른 step 무영향) |
| compose 프로젝트 이름 고정 | `docker/compose.yaml` 의 `name:` 한 줄을 지운다 — 이름이 `docker` 로 돌아간다 |
| 스크립트 셋 | 호출자는 S-23c 하나뿐이다. step 을 지우면 스크립트는 아무도 부르지 않는 채 남는다 |

## ⓪~⑥ 실측 (버릴 clone, 위 **실측 HEAD**)

| # | 확인 | 명령 | 결과 |
|---|---|---|---|
| ⓪ | 목록이 기계 산출과 같다 | `comm` 양방향 | 기계 6 · 문서 6, `comm -23`·`comm -13` 둘 다 빈 출력 |
| ① | 복원 명령이 선다 | 위 `git restore` | exit 0 |
| ② | 복원 규모 | `git status --porcelain` 상태 집계 | D 4 · M 2 |
| ③ | 복원 경로가 base 와 같다 | `git diff e922dc7b -- <복원 경로들>` | 빈 출력 |
| ③b | 공유 파일도 base 로 | 2단계 hunk 역적용 뒤 `git diff e922dc7b -- milestone-6.md` | apply exit 0 · conflict 0 · 빈 출력 |
| ③c | 내 줄 사라짐 · 남의 줄 남음 | 되돌린 `milestone-6.md` 의 언급 수 | 6B-2 2(= **base 에도 있는** 6B-1 분할 문단의 언급. HEAD 는 3 이었다) · 6B-1 10(base 와 같음) |
| ③d | **트리 동일성** | 되돌린 파일의 blob SHA 대 base | 수정 셋 전부 같음 · 신설 넷 전부 지워짐 |
| ④ | 되돌린 트리가 컴파일된다 | `./gradlew --no-daemon compileTestKotlin` | exit 0 |
| ⑤⑥ | test 와 게이트가 초록 | `./gradlew --no-daemon check` | exit 0(9분 6초, 359 task) · test 2,679(= HEAD 와 같은 수 — 이 slice 는 Kotlin test 를 더하지 않는다) · 게이트 넷(`leakPatternGate`·`scriptSizeGate`·`memberEffectGate`·`contractGate`) 전부 **수행**(UP-TO-DATE 아님) |

되돌린 트리에서 base 와 다른 추적 파일은 `reports/evidence/m6/6b2/scope.md` 하나뿐이다(이 slice 의
evidence — 의도). 그 디렉터리를 남긴 채 `leakPatternGate` 가 초록이라 ⑥ 이 성립한다.

## 하네스 레인 변경

`git log --oneline e922dc7b..HEAD -- CLAUDE.md .claude/` → **없음**. 이 slice 는 하네스 경로를 건드리지 않았다.

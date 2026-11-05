# M9 / portal-02 — 되돌림

- base: main `79f3e5c4` · **실측 HEAD: `24bd2803`**(이 slice 의 마지막 산출물 커밋)
- 전제: **main 에 이 slice 가 없다.** 이 브랜치는 `79f3e5c4` 에서 갈라졌고 main 을 되병합하지 않았다 —
  그래서 복원 출처가 고정 SHA `79f3e5c4` 이고 움직이는 `origin/main` 을 쓰지 않는다. 머지된 뒤의
  되돌림은 이 절차가 아니라 **그 PR 의 revert** 다(머지 순간 같은 명령은 아무것도 지우지 않으면서 exit 0).

## 재빌드 없는 되돌림 (먼저 쓸 것)

전환은 설정 값이라 **인자만으로** 옛 서비스로 돌아간다(옛 URL 이 공지일 기준 90일 — ≈2027-02-01 —
동안 살아 있을 때만 참이다).

```
--bidvector.koneps.base-url=https://apis.data.go.kr/1230000/ad/BidPublicInfoService
--bidvector.koneps.operations.construction.path=getBidPblancListInfoCnstwk
--bidvector.koneps.operations.service.path=getBidPblancListInfoServc
--bidvector.koneps.opening.operations.construction.base-amount-path=getBidPblancListInfoCnstwkBsisAmount
--bidvector.koneps.opening.operations.service.base-amount-path=getBidPblancListInfoServcBsisAmount
--bidvector.koneps.opening.bid-price-formula-a-path=getBidPblancListBidPrceCalclAInfo
```

낙찰정보서비스 인자는 **주지 않는다** — 이 slice 가 그 축을 움직이지 않았다.

## 코드 되돌림 — in_scope 경로 한정

목록은 손으로 쓰지 않는다. `git diff --name-status 79f3e5c4..HEAD` 에서 기계로 내고
(`A` = 삭제 대상, `M` = base 로 복원), **라운드마다 다시 낸다** — 파일이 늘면 이 절차를 재실행한다.

```
git restore --source=79f3e5c4 --staged --worktree -- \
  app/src/main/kotlin/bidvector/app/collection/CollectionProperties.kt \
  app/src/main/kotlin/bidvector/app/collection/OpeningCollectionProperties.kt \
  app/src/test/kotlin/bidvector/app/collection \
  app/src/test/kotlin/bidvector/app/wiring \
  adapters/src/test/kotlin/bidvector/adapters/koneps \
  config/quality \
  docs/runbook/m6-6g-real-collection.md \
  docs/discovery/capability-map.md
```

`git checkout <base> --` 를 쓰지 않는다 — base 에 없는 신규 경로에서 pathspec 오류로 아무것도 적용되지 않는다.

**되돌리지 않는 것**: 이 evidence 디렉터리(`reports/evidence/m9/portal-02/`). 복원 목록 **밖**이고,
그래서 아래 실측은 그 디렉터리를 남긴 채 돌렸다(`leakPatternGate` 가 `reports/evidence` 를 선언된
입력으로 잡으므로 「base 결과로 갈음」이 서지 않는다). 하네스 경로(`CLAUDE.md`·`.claude/**`)는 이 range
에 커밋이 없다.

**공유 파일에 남의 줄이 있는가 — 지금은 없다.** `config/quality/gate-tests.properties` ·
`docs/discovery/capability-map.md` · `docs/runbook/m6-6g-real-collection.md` 를 `79f3e5c4..HEAD` 에서
만진 커밋은 **전부 이 slice 의 것**이다(`git log --no-merges --oneline 79f3e5c4..HEAD -- <파일>`).
그래서 전체 복원이 남의 줄을 떨어뜨릴 수 없다. **발동 조건**: 이 브랜치에 main 을 병합하거나 다른
레인의 줄이 이 파일들에 들어오면 전체 복원을 쓸 수 없고, 커밋 해시 hunk 역적용으로 바꾼다
(해시 목록은 문서에 박지 않고 실행 시점에 `git log --no-merges --format=%h 79f3e5c4..HEAD -- <파일>`
최신부터 산출 → `git diff <sha>~1..<sha> -- <파일> | git apply -R`). 그때는 확인을 「내 표지 0」과
**「남의 표지 보존」** 두 계수로 적는다.

## 실측 (버릴 clone, 실측 HEAD `24bd2803`)

| 단계 | 결과 |
| --- | --- |
| ① 명령 exit | 0 |
| ② D/M 수 | D 5 · M 9 (= `git diff --name-status` 의 A 5 · M 9) |
| ③ in_scope diff | `git diff 79f3e5c4 --name-only -- app adapters config docs` 빈 출력 |
| 내 표지 계수 | `BidPublicInfoService02` 0건 · `MockKonepsRoutes.kt` 없음 — 이 slice 의 표지가 남지 않았다 |
| ④ compile · ⑤ test · ⑥ 게이트 | `./gradlew --no-daemon check` 한 명령이 셋을 든다 — `commands.md` 「rollback ④⑤⑥」 행 |

복원 목록 등식: `comm -23 <전체 변경> <복원 인자가 덮는 것>` 이 `reports/evidence/m9/portal-02/scope.md`
하나만 남긴다(= 복원 목록 밖으로 선언한 evidence). **이 등식이 비어 있다는 것만으로 목록이 건전하다고
읽지 않는다** — 물어야 하는 것은 「내가 커밋하지 않아도 이 집합에 경로가 들어오는가」다. 이 slice 의
답은 **들어온다**: 위 공유 파일 셋을 다른 레인이 머지하면 들어오므로, 그 발동 조건의 처분을 위에 미리 적었다.

## 유효성

verifier 가 볼 것은 「실측 HEAD == 판정 SHA」가 아니다(evidence 커밋은 언제나 뒤에 온다). **실측 HEAD 와
판정 SHA 사이에 되돌림 대상이 움직였는가**다:

```
git diff --name-only 24bd2803..<판정 SHA> -- <위 복원 인자들>
```

빈 출력이면 유효하고, 한 줄이라도 나오면 미검증이다. 이 slice 는 실측 뒤 evidence 세 파일만 커밋했다.

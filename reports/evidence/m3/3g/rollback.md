# rollback.md — M3 / 3G

**되돌림은 range revert가 아니라 in_scope 경로 한정이다**(evidence-pack 스킬, 2026-09-04 개정).

**base 진술 — verifier r1 L-2 시정.** 이 문서는 실측·복원 기준으로 `6e3aea4`를 쓴다.
scope.md의 계약값 `base_sha`는 `471dd34a38e66e3b4cf5c15219bb3881b13591d1`이고 `6e3aea4`는
그 바로 **다음 커밋**(계약 자신 — `docs(m3-3g): slice 계약 고정`, `A
reports/evidence/m3/3g/scope.md` 한 줄뿐)이다. 두 값은 **다르다** — 값을 슬쩍 맞추지
않는다. 둘의 차이는 `scope.md` 파일 하나뿐이고, 그 파일은 위 「evidence 자기 파일」 절에서
어차피 되돌림 대상이 아니므로(계약 문서 예외) **어느 base 를 기준으로 restore 해도 결과는
같다** — verifier r1 이 임시 clone에서 실행해 확인했다(검증 보고 §11).

**이 문단은 verifier r1 착수 시점(head `e6a6200`, 확인 절 실행 시점 `5499e9f`)의 낙차를
그대로 기록한 것이다(이력을 되쓰지 않는다) — verifier r1이 최종 head `6718740`에서
S-0~S-6과 rollback 전 단계를 직접 재실행해 전부 exit 0을 얻어 그 낙차를 메웠다(검증
보고 §10·§11). 이 L-1~L-7 시정 라운드의 「확인 지점」 절은 head `f78bd40`(이 라운드
자신의 커밋)에서 새로 실행한 것이라 낙차가 없다.**

## 목록 산출(기계적)

```
git diff --name-status 6e3aea4..HEAD
```
결과: `M adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationTest.kt` ·
`M docs/discovery/capability-map.md` · `M milestone-3.md`
(+ 이 문서를 담을 다음 커밋에서 `A reports/evidence/m3/3g/commands.md` ·
`A reports/evidence/m3/3g/rollback.md`가 추가된다 — 아래 「evidence 자기 파일」 절).

## 귀속 확인 — `git log --oneline 6e3aea4..HEAD` = `be9daa4`·`e6a6200`·`5499e9f`·`6718740`·`f78bd40` 전부 3G

이 range 안에서 파일 전부 **3G 단독**이다(다른 레인의 커밋이 이 range에 없다,
`git log --oneline 6e3aea4..HEAD` 실측). 그래도 `milestone-3.md`·
`docs/discovery/capability-map.md`는 성격상 여러 slice가 공유하는 문서이므로
**커밋 해시 hunk 격리**로 되돌린다(전체 `base` restore가 아니라) — 이 rollback.md가
쓰인 뒤 다른 레인이 같은 파일에 커밋을 얹을 수 있기 때문이다. `CleanMigrationTest.kt`는
이 range 안에서 3G만 만졌으므로 전체 restore로 충분하다.

**verifier r1 시정 커밋 `f78bd40`(L-1~L-7 일괄)도 `milestone-3.md`·`capability-map.md`를
다시 만졌다** — hunk 목록이 이제 파일마다 **둘**이다(최신 `f78bd40` 먼저, 그다음
`e6a6200`). 아래 M(2)를 갱신했다.

## M(1, 3G 단독) — 전체 복원

```
git restore --source=6e3aea4 --staged --worktree -- \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationTest.kt
```

## M(2, 공유 문서) — 커밋 해시 hunk 격리(**셋**, 최신부터)

```
git diff 162f5f6~1..162f5f6 -- milestone-3.md | git apply -R
git diff 162f5f6~1..162f5f6 -- docs/discovery/capability-map.md | git apply -R
git diff f78bd40~1..f78bd40 -- milestone-3.md | git apply -R
git diff f78bd40~1..f78bd40 -- docs/discovery/capability-map.md | git apply -R
git diff e6a6200~1..e6a6200 -- milestone-3.md | git apply -R
git diff e6a6200~1..e6a6200 -- docs/discovery/capability-map.md | git apply -R
```

넷 다 **conflict 없음**(실측 — `f78bd40`의 편집이 `e6a6200`이 만든 구간 안/바로 옆이라
먼저 역적용해 그 구간을 `e6a6200` 상태로 되돌린 뒤에야 `e6a6200` 자신의 hunk가 원래
계산된 트리 상태와 일치한다 — 순서를 바꾸면 두 번째 역적용이 낡은 base를 기대해 실패한다).

확인(둘 다 실측) — 「내 줄 사라짐」: `Slice 3G` · `OPEN-3D-GRANT-PUBLIC-BLINDSPOT` grep **0건**.
「남의 줄 남음」: `milestone-3.md`의 `### Slice 3F` 절 · `capability-map.md`의
`OPEN-3B2-PAGE-SIZE-VS-MESSAGE-CAP` 행 grep **전부 존재**(3G 바로 앞뒤 이웃 — 인접 삽입이라
가장 걸리기 쉬운 자리).

## evidence 자기 파일 — 삭제 대상(4C-2 rollback.md 전례를 따른다)

`reports/evidence/m3/3g/commands.md`·`reports/evidence/m3/3g/rollback.md`(이 파일 자신)는
**이 slice의 존재를 기록하는 자기 evidence**라 wiring과 함께 되돌린다 — `scope.md`만
계약 문서라 예외(이미 base에 있었다, 이 range 밖).

```
rm -f reports/evidence/m3/3g/commands.md reports/evidence/m3/3g/rollback.md
```

## 목록이 자기를 담은 커밋을 가리키지 않게 나눈 것

이 문서(rollback.md)와 `commands.md`는 **`reports/evidence/m3/3g/**`만** 만지는 별도
커밋으로 올린다(공유 문서 커밋과 분리) — 그래서 위 hunk 목록이 이 문서 자신의 커밋으로
낡지 않는다. **이 절 자체를 갱신하는 지금 커밋(rollback.md만 만짐)이 그 규율의 두 번째
적용이다** — `f78bd40`(공유 문서를 만진 L-1~L-7 커밋)의 해시를 이 문서에 추가하는 커밋은
`reports/evidence/m3/3g/rollback.md` 하나만 바꾼다.

## 확인 지점 — 임시 clone에서 실제로 실행(실측, 2026-09-10, head `f78bd40`)

1. `git clone .` (HEAD `f78bd40`) — exit 0.
2. M(1) restore(`CleanMigrationTest.kt`) exit 0 · M(2) hunk 역적용 **넷**
   (`f78bd40`→`e6a6200`, `milestone-3.md`·`capability-map.md` 각 둘) 전부 exit 0 —
   **conflict marker 없음**.
3. evidence 자기 파일 삭제(`rm -f`) — exit 0.
4. `git status --porcelain -- <in_scope 4경로>` — `CleanMigrationTest.kt`(M, staged)·
   `milestone-3.md`(M)·`docs/discovery/capability-map.md`(M)·`commands.md`(D)·
   `rollback.md`(D) 다섯 줄, `reports/evidence/m3/3g/scope.md`는 목록에 없음(삭제되지
   않고 그대로 남음 — 확인됨).
   내 줄 사라짐: `grep -c "Slice 3G" milestone-3.md` → 0,
   `grep -c "OPEN-3D-GRANT-PUBLIC-BLINDSPOT" docs/discovery/capability-map.md` → 0.
   남의 줄 남음: `grep -c "Slice 3F" milestone-3.md` → 1,
   `grep -c "OPEN-3B2-PAGE-SIZE-VS-MESSAGE-CAP" docs/discovery/capability-map.md` → 1.
   `wc -l CleanMigrationTest.kt` → 296(base 원본과 일치).
5. 되돌린 트리 **compile** — `:adapters:compileTestKotlin --no-daemon` exit 0
   (32 actionable tasks, BUILD SUCCESSFUL).
6. 되돌린 트리 **test** — `:adapters:test --tests "bidvector.adapters.persistence.CleanMigrationTest" --no-daemon --rerun-tasks`
   exit 0 — **9 tests, 0 failed**(base 판 — `provenance_authority`·`notice_audit` 술어
   test 둘과 outbox·inbox 개별 유효 권한 test 둘이 되살아난 것을 test 수로 확인, 6에서
   9로 복귀).

임시 clone은 확인 뒤 `rm -rf`로 제거했다.

## 되돌린 뒤 남는 것

유효 권한 행렬 test(축 9)가 사라지고 3D의 옛 술어 test 둘(PUBLIC 경유 사각 있음)과 4C-2의
outbox·inbox 개별 test가 되살아난다. `OPEN-3D-GRANT-PUBLIC-BLINDSPOT`은 다시 **활성**으로
돌아간다(capability-map.md 행이 사라짐). `milestone-3.md` Slice 3G 절이 사라지고 3F 종결
문단이 다시 M3의 마지막 문단이 된다. **마이그레이션은 이 slice가 손대지 않았으므로 DB
schema·GRANT 값에는 애초에 영향이 없다** — 되돌림이 필요해도 V7 무력화 절차가 필요 없다.

## 종결 등재 반영 — 2026-09-10

종결 승인 커밋 `162f5f6`(`milestone-3.md`·`capability-map.md`·`checklist.md`)이 공유 문서 둘을
다시 만졌으므로 위 목록의 **최신 자리**에 넣었다. **이 갱신 커밋은 evidence 경로만 만진다** —
공유 파일을 건드리면 목록이 자기만큼 다시 낡는다(2026-09-10 성문화, M4/4C-2 r3 M-5 의 뿌리).

**실측 범위를 정확히 적는다**: 위 「확인 지점」의 넷(`f78bd40`→`e6a6200`)은 head `f78bd40` 에서
compile·test 까지 끝까지 실행한 결과다. **`162f5f6` 두 hunk 의 역적용은 이 문단 작성 시점에
임시 clone 에서 별도로 실행**해 exit 0 과 conflict 0 을 확인했다(compile·test 는 다시 돌리지
않았다 — 문서 전용 커밋이라 트리의 코드가 같다).

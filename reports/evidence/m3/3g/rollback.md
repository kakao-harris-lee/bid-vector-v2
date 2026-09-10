# rollback.md — M3 / 3G

**되돌림은 range revert가 아니라 in_scope 경로 한정이다**(evidence-pack 스킬, 2026-09-04 개정).
base `6e3aea4`(scope.md 계약값 그대로). 이 문서 작성 시점 head `e6a6200`.

## 목록 산출(기계적)

```
git diff --name-status 6e3aea4..HEAD
```
결과: `M adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationTest.kt` ·
`M docs/discovery/capability-map.md` · `M milestone-3.md`
(+ 이 문서를 담을 다음 커밋에서 `A reports/evidence/m3/3g/commands.md` ·
`A reports/evidence/m3/3g/rollback.md`가 추가된다 — 아래 「evidence 자기 파일」 절).

## 귀속 확인 — `git log --oneline 6e3aea4..HEAD` = `be9daa4`·`e6a6200` 둘뿐

이 range 안에서 세 파일 전부 **3G 단독**이다(다른 레인의 커밋이 이 range에 없다,
`git log --oneline 6e3aea4..HEAD` 실측). 그래도 `milestone-3.md`·
`docs/discovery/capability-map.md`는 성격상 여러 slice가 공유하는 문서이므로
**커밋 해시 hunk 격리**로 되돌린다(전체 `base` restore가 아니라) — 이 rollback.md가
쓰인 뒤 다른 레인이 같은 파일에 커밋을 얹을 수 있기 때문이다. `CleanMigrationTest.kt`는
이 range 안에서 3G만 만졌으므로 전체 restore로 충분하다.

## M(1, 3G 단독) — 전체 복원

```
git restore --source=6e3aea4 --staged --worktree -- \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationTest.kt
```

## M(2, 공유 문서) — 커밋 해시 hunk 격리

```
git diff e6a6200~1..e6a6200 -- milestone-3.md | git apply -R
git diff e6a6200~1..e6a6200 -- docs/discovery/capability-map.md | git apply -R
```

확인(둘 다 실측) — 「내 줄 사라짐」: `Slice 3G` · `OPEN-3D-GRANT-PUBLIC-BLINDSPOT` (신설·닫힘
행) grep **0건**. 「남의 줄 남음」: `milestone-3.md`의 `### Slice 3F` 절 · `capability-map.md`의
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
커밋으로 올린다(공유 문서 커밋 `e6a6200`과 분리) — 그래서 위 hunk 목록(`e6a6200` 하나)이
이 문서 자신의 커밋으로 낡지 않는다.

## 확인 지점 — 임시 clone에서 실제로 실행

1. `git clone .` (HEAD가 evidence 커밋 이상)
2. M(1) restore, M(2) hunk 역적용 둘 — 셋 다 exit 0(clean, conflict 없음 — 인접 구간
   재확인 필요).
3. evidence 자기 파일 삭제.
4. `git status --porcelain -- adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationTest.kt milestone-3.md docs/discovery/capability-map.md reports/evidence/m3/3g` — 출력 없음(`reports/evidence/m3/3g/scope.md`는 base에 이미 있었으므로 삭제 대상 아님, 그대로 남아야 함).
5. 되돌린 트리 **compile** — `:adapters:compileTestKotlin` exit 0.
6. 되돌린 트리 **test** — `:adapters:test --tests "bidvector.adapters.persistence.CleanMigrationTest"`
   exit 0(base 판, 9개 test — provenance_authority·notice_audit 술어 test와 outbox·inbox
   개별 test가 다시 나타난다).

## 되돌린 뒤 남는 것

유효 권한 행렬 test(축 9)가 사라지고 3D의 옛 술어 test 둘(PUBLIC 경유 사각 있음)과 4C-2의
outbox·inbox 개별 test가 되살아난다. `OPEN-3D-GRANT-PUBLIC-BLINDSPOT`은 다시 **활성**으로
돌아간다(capability-map.md 행이 사라짐). `milestone-3.md` Slice 3G 절이 사라지고 3F 종결
문단이 다시 M3의 마지막 문단이 된다. **마이그레이션은 이 slice가 손대지 않았으므로 DB
schema·GRANT 값에는 애초에 영향이 없다** — 되돌림이 필요해도 V7 무력화 절차가 필요 없다.

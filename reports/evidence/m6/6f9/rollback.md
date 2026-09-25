# M6/6F-9 rollback

`실측 HEAD: 2a27286f`(이 slice 의 마지막 산출물 커밋 — 이후 커밋은 evidence 전용). 앞 실측을 옮기지 않는다 — 아래 ①~⑥ 과 목록·hunk 표는 이 HEAD 에서 다시 산출·실행한 값이다.

## 비활성화 (코드를 되돌리지 않고 즉시)

- **수집**은 기본 꺼짐이다 — `bidvector.collection.mode` 가 `once` 가 아니면 러너·수집 어댑터가 올라오지 않는다(6F-8). 켜져 있던 러너는 프로세스를 끝내면 된다. 복구 시간: 즉시.
- **읽는 쪽**: 새 필드는 재수집이 채운 열일 뿐 평가·전략 응답·OpenAPI 는 바뀌지 않았다. 감시 「관심 업종」이 새 값에 반응하는 것을 멈추려면 전략의 관심 업종 규칙을 비운다(규칙이 없으면 그 축은 판정하지 않는다) — 코드 되돌림은 아래.
- **ML 입력**: 용역 공고의 `business_category_code` 를 다시 비우려면 열 값을 지우는 것이 아니라 아래 코드 되돌림(분류 번호를 업무구분 코드로 옮기는 canonicalize)이다(`checklist.md` 알려진 제한 1).

## V17 — 두 갈래 (파일을 지우는 것과 적용된 DB 를 되돌리는 것은 다르다)

1. **미적용 DB**: `V17__notice_business_division.sql` 을 지운다 — 아래 restore 목록에 신규(A)로 들어 있어 코드 되돌림이 함께 처리한다.
2. **적용된 DB**(개발 DB 등): 파일 삭제로 되돌리지 않는다(Flyway 이력·체크섬). 새 마이그레이션(다음 V 번호)으로:
   **적용된 DB 에서는 V17 파일을 restore 목록에서 빼고**(그 경로만 개별 인자에서 제외한다 — 기계 산출 목록은 그것을 신규(A)로 담으므로
   `git restore` 가 파일을 지운다) **아래 `DROP COLUMN` 을 담은 V18 을 코드 되돌림과 같은 배포에 함께 낸다**(migration-reviewer r1 L-1).
   `PersistenceWiring.migrate` 의 Flyway 는 기본 검증이라, 이력에 V17 이 있는 DB 에서 로컬 V17 이 사라지면 부팅이 실패한다
   (「applied migration not resolved locally」). 순서를 나누면 V17 을 아는 바이너리가 열 없는 DB 를 보거나 그 반대가 된다.

```sql
ALTER TABLE notice DROP COLUMN business_division, DROP COLUMN service_division, DROP COLUMN main_construction_type;
```

열을 지우면 새 CHECK 셋도 함께 사라진다. 임시 Postgres 에 V1~V17 을 적용한 뒤 이 문장을 실행해 **열 3 → 0 · `notice` CHECK 16 → 13** 을 실측했다(`commands.md`). 개발 DB 는 운영 데이터가 아니라 컨테이너를 통째로 버려도 된다(`docker rm -f -v bid-vector-v2-dev` — 다른 프로젝트 컨테이너를 건드리지 않는다, 팀장 결정).

## 되돌리는 것 (코드)

`base = f6ebc047`(`git merge-base HEAD origin/main` — base 가 움직이면 옛 base 로 산출한 목록은 exit 0 이면서 틀린다). 목록은 기계 산출:

```
git diff --name-status <base>..<실측 HEAD> -- . ':!reports/evidence' ':!milestone-6.md' ':!config/quality/gate-tests.properties' ':!config/quality/architecture-policy.properties' ':!docs/discovery/data-dictionary.md'
```

**산출물이 바뀔 때마다(라운드마다) 이 명령을 다시 돌려 목록을 재산출한다**(목록이 낡는 것이 이 결함의 실제 원인이다).

```
git restore --source=<base> --staged --worktree -- <위 목록의 경로, 개별 인자>
```

(`--source` 에 없는 신규 경로는 삭제되므로 별도 `git rm` 이 없다. 하네스 경로 `CLAUDE.md`·`.claude/**` 는 이 slice 가 만지지 않았다 — `git log --oneline <base>..HEAD -- CLAUDE.md .claude/` 가 빈 출력.)

## 공유 파일 — 커밋 해시 hunk 격리

`git restore` 로 덮지 않는다(다른 slice 의 줄까지 걷는다). 이 slice 의 커밋을 `git log --oneline <base>..<실측 HEAD> -- <파일>` 로 열거하고 **최신부터 역순으로** 하나씩 되돌린다
(위 명령의 `:!reports/evidence` 가 `policy-values.md` 도 목록에서 빼므로 그 파일도 이 표로 되돌린다):

```
git diff <sha>~1..<sha> -- <파일> | git apply -R
```

| 파일 | 이 slice 의 커밋(최신 → 과거) |
|---|---|
| `config/quality/gate-tests.properties` | `ea2c2984` · `c3c241a7` · `a479a133` · `d40fcffc` · `72909a52` · `ed990046` · `74c78c6d` |
| `config/quality/architecture-policy.properties` | `2a27286f` · `181c42c0` · `c12eb889` · `9290a3de` |
| `docs/discovery/data-dictionary.md` | `0ca9a861` |
| `reports/evidence/m3/3a/policy-values.md` | `0ca9a861` |

이 slice 의 커밋만 역순으로 걷으면 각 `apply -R` 이 exit 0·conflict 0 이었다(`commands.md` ②, 열세 번). `architecture-policy.properties` 는 r1 이 앞 커밋의 줄을 **교체**하고 r2 가 같은 주석 블록을 다시 고쳤으므로 역순이 특히 중요하다 —
최신(`2a27286f`)부터 차례로 되돌려야 각 hunk 의 문맥이 맞는다. **다른 slice 의 줄이 같은 자리에 끼어든 뒤**에는 충돌 블록이 나오고 `--3way` 도 자동 해소하지 못할 수 있다 —
수동 절차: 충돌 블록에서 **이 slice 의 줄만** 지운다 —
`gate-tests.properties`: `M6/6F-9 —` 주석 블록들과 등재 항목 일곱(`BusinessDivisionTest`·`BusinessClassificationCanonicalizeTest`·`KonepsSourceDivisionTest`·`NoticeBusinessClassificationPersistenceTest`·`WatchCategoriesAssemblyTest`·`RequestCategoryCodeWireTest`·`OpportunitySampleSupplyDivisionTest`) ·
`architecture-policy.properties`: `(c')`·`(c'')` 블록과 `collection.classification-key.*`·`collection.division-value.*` 키 · `data-dictionary.md`: §6.3.4 절 전체 + §6.3.3 「소비」 행의 「세부 이름 … §6.3.4」 문구를 원문(「공종(`business_category_label`)」)으로 ·
`policy-values.md`: P-16 행 하나 + §5 의 「M6/6F-9(P-16)가 네 번째 자리를 더한다」 문단. 지운 뒤 「내 줄 사라짐」(위 식별어 grep 0)과 「남의 줄 남음」을 둘 다 확인한다.

`milestone-6.md` 는 이 rollback 의 restore 대상이 아니다 — 착수 문단은 팀장 레인이 쓴 것이고 되돌려야 하면 팀장이 같은 방식으로 격리한다.

## 확인 — 임시 clone 실측 (`commands.md` 표)

① restore exit 0(목록 61 — 삭제 19 · 변경 42) → ② 공유 파일 hunk 격리 열세 번 전부 exit 0·conflict 0 → ③ 목록·공유 파일 넷 `git diff <base>` 빈 출력이고 트리 전체에서 base 와 다른 것은 `milestone-6.md` 와 이 slice 의 evidence 뿐 → ④ `compileKotlin compileTestKotlin` exit 0 → ⑤ `test` exit 0 → ⑥ `check` exit 0
(**게이트 초록** — 되돌린 트리에서도 이 slice 의 evidence 가 남아 `leakPatternGate` 를 돌리지만 매치 0). 되돌리지 않기로 한 것: 이 slice 의 evidence 디렉터리(`reports/evidence/m6/6f9/**`)와 `milestone-6.md`.

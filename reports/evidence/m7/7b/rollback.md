# M7/7B — 되돌리기 (rollback)

**마이그레이션이 있으므로 forward-fix 가 정본이다**(6B-2 runbook 관례). 아래는 그 전제 위의 두
경우이고, 둘은 서로 다른 절차다 — 섞어 적으면 머지 뒤에 「파일을 지우면 된다」가 된다.

base: `998b59dd`(2026-10-24 세 번째 rebase — 착수 base `bee7c36f` 이후 main 이 움직였으므로 base 를 옮기지 않으면 아래 복원이 main 의 변경까지 되돌린다. 머지 전에 main 이 또 움직이면 rebase 와 함께 이 SHA 를 고친다). 대상 목록은 손으로 적지 않고 **기계 산출**이다:

```bash
git diff --name-status 998b59dd..HEAD
```

---

## 경우 ① — 머지 전 (이 브랜치에서 되돌린다)

**목록을 손으로 적지 않는다.** 앞 판은 파일 이름을 나열했고 그 목록은 **곧 드리프트했다**
(`HttpTestSupport.kt` 가 빠져 있었다 — 실측). 되돌림 대상은 그때그때 **기계로 뽑는다**:

```bash
cd /home/deploy/project/bid-vector-v2-m7-7b

# ⓐ 수정된 기존 파일 — base 의 내용으로 되돌린다
git diff --name-status 998b59dd..HEAD | awk '$1=="M"{print $2}' \
  | xargs -r git restore --source=998b59dd --

# ⓑ 새로 만든 것 — 지운다
git diff --name-status 998b59dd..HEAD | awk '$1=="A"{print $2}' | xargs -r rm -f

# ⓒ 빈 디렉터리 정리(새 모듈·패키지 뿌리)
find bidding workflow/src/*/kotlin/bidvector/workflow/bidding \
     adapters/src/*/kotlin/bidvector/adapters/bidding -type d -empty -delete 2>/dev/null
```

`A`·`M` 밖의 상태(`R` 이름 변경 등)는 이 slice 에 없다 — 있으면 위 두 줄이 **그것을 조용히
빼므로**, 되돌리기 전에 `git diff --name-status 998b59dd..HEAD | cut -f1 | sort -u` 가
`A`·`M` 둘뿐인지 먼저 본다.

**개발 DB 는 파일 삭제로 돌아오지 않는다.** V21 을 이미 적용한 DB 가 있으면 재생성한다
(`tools/db-rehearsal.sh` 가 쓰는 것과 같은 경로) — Flyway `validate()` 가 「적용된 적 있는데
파일이 없다」로 거부하기 때문이다.

**`ColumnSpec` 주의**: ⓐ 의 `CleanMigrationColumnTest.kt` 복원은 그 파일 안에 `private data
class ColumnSpec` 을 되살리고, ⓑ 의 `BidCaseColumnSpecs.kt` 삭제는 top-level `internal`
선언을 함께 지운다 — **둘을 같이 해야** 컴파일된다. 한쪽만 하면 선언이 둘이거나 0 이다.

---

## 경우 ② — 머지 뒤

**파일 삭제로 되돌리지 않는다**(6F-4 규율).

1. **PR revert** — `git revert -m 1 <merge-sha>`. 코드·문서·OpenAPI·생성 타입이 함께 돌아간다.
2. **개발 DB 재생성** — revert 는 `V21` 파일을 지우므로, 그 마이그레이션이 적용된 DB 는
   Flyway `validate()` 에서 거부된다. 개발·CI DB 는 **재생성**이 정답이다.
3. **운영 DB(있다면)** — 표를 `DROP` 하지 않는다. 운영자의 투찰 기록이 들어 있을 수 있고,
   그것은 `OPERATOR_BID_HISTORY` 로 시간 기준 파기에서 빠지는 데이터다. 되돌릴 일이 생기면
   **새 V 파일**로 접근하고(필요하면 읽기만 막고) 데이터 처분은 **운영자 승인**을 받는다.

---

## 되돌리기 어려운 것 — 미리 적는다

- **`V21` 번호 자체.** 다른 레인이 V22 를 올린 뒤에는 이 번호를 재사용할 수 없다. PR 직전에
  `main` 최대 번호를 D-6A1-12 정의대로 재확인한다.
- **`notice` FK 부재는 되돌리기 쉬운 쪽이 아니다.** 나중에 FK 를 **더하는** 편집은 6B-3 파기
  sweep 과 충돌한다(RESTRICT 면 sweep 실패, CASCADE 면 운영자 기록 소실). 이 결정은 V21
  머리말과 runbook 에 사유와 함께 적혀 있고, 바꾸려면 그 두 자리를 함께 고쳐야 한다.
- **`ColumnSpec` 가시성 승격**(private 중첩 → top-level `internal`)은 다른 slice 가 그 타입을
  쓰기 시작하면 되돌리기 어려워진다. 오늘은 쓰는 곳이 두 파일뿐이다.

## 실측

경우 ①의 ⓐ~ⓒ 를 버릴 clone 에서 실측했다(결과는 `checklist.md` §8).

실측 HEAD: `f000bc80` — PR 전 마지막 일괄(D-7B-8)의 마지막 산출물 커밋 「test(m7-7b): probe 의 매핑 조회는 이름으로 …」(①~③ 실행, ④~⑥ 은 되돌린 트리 객체가
`998b59dd` 트리와 같아 그 트리의 실행 실측으로 갈음 — `checklist.md` §8). 앞 판들의 실측은 같은 절에 이력으로 남는다. 판정 SHA 와의 사이에 되돌림 대상 경로가 움직였는지는 `git diff --name-only <실측 HEAD>..<판정 SHA> --`
(이 문서가 되돌리는 경로)로 본다. 실측 HEAD 뒤로 움직이는 것은 evidence 파일뿐이고, 그 파일들은 이 slice 가 **새로 만든(`A`)**
것이라 ⓑ 가 내용과 무관하게 통째로 지운다 — 되돌린 트리는 바뀌지 않는다. `M` 경로가 그 사이에 하나라도 움직이면 다시 잰다.

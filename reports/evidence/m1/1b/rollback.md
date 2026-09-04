# rollback — M1 / 1B · Money/Rate/Basis

**정본 절차**(`evidence-pack` SKILL, 2026-09-04 개정): 되돌림은 range revert 가 아니라
in_scope 경로 한정이다. **verifier r1 H-2 정정 — 경로 목록은 `scope.md` `in_scope`
전건이어야 한다.** 이전 판은 10경로 중 5경로만 들어 임시 clone 에서 512줄이 남았다(승인
문서 넷 + `fixtures/manifest.yaml`). 아래 목록은 `scope.md` `in_scope`와 **한 자리**여야
하므로, `scope.md`가 in_scope 를 넓히면 이 목록도 같은 커밋에서 갱신한다 — 어긋남을
막는 확인 명령은 「확인 지점」 3 이 갖는다.

```
git restore --source=66c1ab79af4c5a68145811a9e87008dfdb10da3c --staged --worktree -- \
  shared-kernel \
  config/quality/architecture-policy.properties \
  config/quality/member-effects.properties \
  config/quality/gate-tests.properties \
  fixtures/manifest.yaml \
  docs/discovery/data-dictionary.md \
  docs/adr/0002-money-rate-basis.md \
  docs/adr/0007-test-pyramid-and-ratchet.md \
  docs/discovery/capability-map.md \
  reports/evidence/m1/1b
```

`--source` 에 없는 경로(base 에 없던 신규 파일)는 이 명령으로 삭제된다 — 별도 `git rm` 이
필요 없다. `git checkout <base> -- <경로>` 는 쓰지 않는다(base 에 없는 경로마다 pathspec
오류로 exit 1, `evidence-pack` SKILL 인용: Codex 1A 16차 high).

**`docs/discovery/data-dictionary.md`·`docs/adr/0002-money-rate-basis.md`·
`docs/adr/0007-test-pyramid-and-ratchet.md`·`docs/discovery/capability-map.md` 는 승인
문서다.** 이 명령이 되돌리는 것은 **1B가 그 문서에 넣은 편집분**(운영자 결정 14건 등재
+ 후속 정정)이고, 그 편집 자체가 `scope.md` in_scope 에 「해당 절만」로 한정돼 있다 —
문서 전체가 base 이전 상태로 없어지는 것이 아니라 **base 시점의 그 문서 상태**로
돌아간다(그 문서가 1B 이전에 이미 있었으므로 삭제가 아니라 복원이다).

**`fixtures/manifest.yaml` 은 fixture-curator 레인 소유다.** 이 명령이 그 경로를
포함하는 이유는 `scope.md` in_scope 가 「경로만 조건부 등재」로 그 파일을 이미 들고
있어서다 — 레인 소유와 in_scope 여부는 다른 축이다. `fixtures.md` §6(그 레인이 쓴
rollback)이 **같은 경로를 독립적으로도** 되돌리므로 두 문서의 절차가 서로 다른 결론을
내면 안 된다 — 「확인 지점」 4가 그 일치를 확인한다.

하네스 경로(`CLAUDE.md`·`.claude/**`)와 승인 문서의 하네스 레인 편집은 **되돌리지 않는다.**

## 확인 지점

되돌린 뒤 **넷 다** 확인한다 — 1만으로는 부족하다(H-2가 지적한 공허 통과가 바로 이 지점이다).

1. **위 열 경로의 diff가 빈다**: `git diff 66c1ab79af4c5a68145811a9e87008dfdb10da3c --stat -- shared-kernel config/quality/architecture-policy.properties config/quality/member-effects.properties config/quality/gate-tests.properties fixtures/manifest.yaml docs/discovery/data-dictionary.md docs/adr/0002-money-rate-basis.md docs/adr/0007-test-pyramid-and-ratchet.md docs/discovery/capability-map.md reports/evidence/m1/1b`
2. **scope.md의 in_scope 전건과 대조** — 이 명령이 다루는 경로 수가 `scope.md` `in_scope`
   의 원소 수와 같아야 한다: `scope.md`의 `in_scope:` 블록 원소 수를 세어 위 명령의 경로
   수(`shared-kernel`·`config/quality` 셋·`fixtures/manifest.yaml`·승인 문서 넷·
   `reports/evidence/m1/1b` = **열**)와 비교한다. 어긋나면 이 파일이 낡은 것이다.
3. **하네스 경로는 그대로다**: `git diff HEAD -- CLAUDE.md .claude/` 가 비어 있다.
4. **`fixtures.md` §6과 결론이 같다**: 그 문서의 `git restore --source=<이 slice의 최신
   SHA> --staged --worktree -- fixtures/manifest.yaml`을 이 문서의 명령과 **같은 clone에서
   순서 무관하게** 실행해도 `fixtures/manifest.yaml`의 최종 상태(= base)가 같다 — 두 절차가
   같은 파일을 다른 목표로 되돌리지 않는다.

## 실측 (verifier r1 H-2 뒤 재측정, 임시 clone)

`git clone --no-hardlinks` 으로 만든 임시 clone에서 위 열 경로 전건으로 `git restore`를
실제로 실행했다 — 결과는 `commands.md`가 아니라 이 자리에 남긴다.

- `git restore --source=66c1ab7 --staged --worktree -- <위 열 경로>` → **exit 0**
- 확인 지점 1: `git diff 66c1ab7 --stat -- <같은 열 경로>` → **비어 있음**(0줄 — 이전 판의
  512줄 잔존이 사라졌다)
- `git status --porcelain`(HEAD 대비)이 낸 변경 종류(실행 시점 기록, 슬라이스가 자라면
  낡는다 — 재확인은 위 diff 명령으로 한다): A 1(`ModuleBoundaryAnchor.kt` 복원) · D 33
  (1B·curator 가 새로 만든 파일이 base 에 없어 삭제) · M 7(승인 문서 넷 + `config/quality`
  셋이 base 버전으로 되돌아감)
- 하네스 경로(`CLAUDE.md`·`.claude/**`) 확인: `git diff HEAD -- CLAUDE.md .claude/`
  **비어 있음**
- 확인 지점 4: 같은 clone에서 이 문서의 명령을 먼저 실행한 뒤 `fixtures.md` §6의 명령
  (`git restore --source=9a36aa8 --staged --worktree -- fixtures/manifest.yaml`)을 이어
  실행 — `fixtures/manifest.yaml`의 SHA-256이 **두 순서 모두 동일**(`ced1d8466b19…`)했다.
  두 절차가 그 파일을 다른 목표로 되돌리지 않는다는 뜻이다 — `93be34a`(curator 커밋)가
  `fixtures/manifest.yaml`을 실제로 편집했지만 base(`66c1ab7`)와 그 커밋의 부모(`9a36aa8`)
  사이에 그 파일 변경이 없었기 때문에(구현 레인 세 커밋은 그 경로를 건드리지 않았다)
  두 SHA 로의 복원이 같은 내용으로 수렴한다

## 되돌리는 flag/route/writer

없음 — 1B 는 신규 wiring(feature flag, route, writer)을 만들지 않는다. `shared-kernel` 은
순수 도메인 값 타입이고 그 자체가 어디서도 자동 호출되지 않는다(다른 모듈이 아직 참조하지
않는다). 되돌림은 파일 되돌리기로 완결되며 별도 비활성화 스위치가 필요 없다.

## 예상 복구 시간

수 초 — 위 `git restore` 명령 하나가 in_scope 전체를 되돌린다. DB write·외부 API 호출이
없으므로 후속 정리 단계가 없다.

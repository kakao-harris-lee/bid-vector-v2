# rollback — M1 / 1B · Money/Rate/Basis

**정본 절차**(`evidence-pack` SKILL, 2026-09-04 개정): 되돌림은 range revert 가 아니라
in_scope 경로 한정이다.

```
git restore --source=66c1ab79af4c5a68145811a9e87008dfdb10da3c --staged --worktree -- \
  shared-kernel \
  config/quality/architecture-policy.properties \
  config/quality/member-effects.properties \
  reports/evidence/m1/1b
```

`--source` 에 없는 경로(base 에 없던 신규 파일)는 이 명령으로 삭제된다 — 별도 `git rm` 이
필요 없다. `git checkout <base> -- <경로>` 는 쓰지 않는다(base 에 없는 경로마다 pathspec
오류로 exit 1, `evidence-pack` SKILL 인용: Codex 1A 16차 high).

하네스 경로(`CLAUDE.md`·`.claude/**`)와 승인 문서의 하네스 레인 편집은 **되돌리지 않는다.**

## 확인 지점

되돌린 뒤:

1. `git diff 66c1ab79af4c5a68145811a9e87008dfdb10da3c -- shared-kernel config/quality/architecture-policy.properties config/quality/member-effects.properties reports/evidence/m1/1b` 가 비어 있다.
2. 하네스 경로(`CLAUDE.md`·`.claude/**`)는 HEAD 그대로다 — `git diff HEAD -- CLAUDE.md .claude/` 가 비어 있다.

## 실측 (Phase 3, 임시 clone)

`git clone --no-hardlinks` 으로 만든 임시 clone에서 위 `git restore` 명령을 실제로
실행했다 — 결과는 `commands.md` 가 아니라 이 자리에 남긴다(같은 사실을 두 자리에
적지 않는다).

- `git restore --source=<base> --staged --worktree -- shared-kernel config/quality/architecture-policy.properties config/quality/member-effects.properties reports/evidence/m1/1b` → **exit 0**
- 복구 뒤 `git diff <base> -- <같은 경로>` → **비어 있음**(base 상태와 완전히 일치)
- `git status --porcelain`(HEAD 대비) 이 낸 변경 종류: **D 18**(1B 가 새로 만든
  main·test·evidence 파일이 base 에 없어 삭제됨) · **M 1**(`shared-kernel/build.gradle.kts`
  가 base 버전으로 되돌아감) · **A 1**(`ModuleBoundaryAnchor.kt` 가 base 에는 있고 HEAD 에는
  없어, HEAD 기준으로는 "새로 생긴" 파일로 보고된다 — 실제로는 1B 가 지운 것의 복원이다)
- 하네스 경로(`CLAUDE.md`·`.claude/**`) 확인: `git diff HEAD -- CLAUDE.md .claude/` **비어
  있음** — 되돌리지 않는다는 선언대로 손대지 않았다

## 되돌리는 flag/route/writer

없음 — 1B 는 신규 wiring(feature flag, route, writer)을 만들지 않는다. `shared-kernel` 은
순수 도메인 값 타입이고 그 자체가 어디서도 자동 호출되지 않는다(다른 모듈이 아직 참조하지
않는다). 되돌림은 파일 되돌리기로 완결되며 별도 비활성화 스위치가 필요 없다.

## 예상 복구 시간

수 초 — 위 `git restore` 명령 하나가 in_scope 전체를 되돌린다. DB write·외부 API 호출이
없으므로 후속 정리 단계가 없다.

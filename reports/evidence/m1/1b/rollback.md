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

## 실측

**Phase 1 시점에는 산출물이 없다** — `scope.md`·`rollback.md`·`commands.md`·`checklist.md`
넷뿐이고 코드 변경이 없다. `evidence-pack` SKILL 이 요구하는 **임시 clone 에서의 실행 실측**은
Phase 3 첫 구현 커밋 뒤, 코드 산출물이 생긴 시점에 한다 — `commands.md` 에 그 실측 명령·exit
code·D/M 수를 한 줄로 남긴다. 지금 실측하면 대상이 없어 「명령이 성립한다」만 확인하고 「exit
0 과 D/M 수」는 확인할 수 없다 — 그 둘 다를 실증하는 것이 `evidence-pack` 규격의 요구다.

## 되돌리는 flag/route/writer

없음 — 1B 는 신규 wiring(feature flag, route, writer)을 만들지 않는다. `shared-kernel` 은
순수 도메인 값 타입이고 그 자체가 어디서도 자동 호출되지 않는다(다른 모듈이 아직 참조하지
않는다). 되돌림은 파일 되돌리기로 완결되며 별도 비활성화 스위치가 필요 없다.

## 예상 복구 시간

수 초 — 위 `git restore` 명령 하나가 in_scope 전체를 되돌린다. DB write·외부 API 호출이
없으므로 후속 정리 단계가 없다.

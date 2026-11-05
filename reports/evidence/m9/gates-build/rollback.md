# M9 / gates-build — 되돌리기

**복원 원천은 고정된 main SHA `df4976cc`** 다 — 이 브랜치가 실제로 분기한 그 커밋이고, 그 SHA 에는 이
slice 가 없다(첫 산출물 커밋 미포함 단언: `git log --oneline df4976cc` 에 이 slice 의 커밋이 없다).
움직이는 `origin/main` 을 쓰지 않는다. 머지 뒤 되돌림은 PR revert 다.

**실측 HEAD: `b1793b78`.** 그 뒤에 움직인 되돌림 대상은 **이 파일 하나**(`rollback.md` 를 담은 커밋)다 —
verifier 의 유효성 확인은 `git diff --name-only b1793b78..<판정 SHA> -- <아래 목록>` 이 그 한 경로만 내는
것으로 본다.

## 복원 목록 — 기계 산출, 내 커밋만

```
git log --no-merges --format= --name-only HEAD ^df4976cc | sort -u
```

**78 경로**(추가 15 · 수정 63). 등식 확인: 위 목록과 `git diff --name-only df4976cc..HEAD | sort -u` 의
`comm -23`·`comm -13` 이 **둘 다 빈 출력**이다(한쪽 방향만 보면 「내가 커밋하지 않아도 들어오는 경로」를
못 본다). 라운드마다 재산출한다.

경로의 묶음은 다음 넷이다 — `build-logic/**`(게이트 구현·convention plugin·included build 스크립트) ·
`config/quality/**`(정책 데이터 넷: architecture · duplicate · gate-tests · 새 migration) ·
**새 게이트가 찾은 기존 위반을 고친 40 파일**(adapters·app·procurement·workflow 의 import 줄 삭제 —
목록은 `checklist.md`) · 문서 둘(`reports/evidence/m9/gates-build/**` · `reports/evidence/m6/closure/open-inventory.md`).

`open-inventory.md` 는 **공유 파일**이다 — 다른 레인도 덧붙인다. 이 slice 는 그 파일에 §8 한 절만
붙였고, 복원 원천이 고정 SHA 라 그 복원이 다른 레인의 뒤 편집을 지우지 않는다(머지 전 기준). 머지 뒤라면
PR revert 가 그 자리를 진다.

## 복원 명령 — range revert 가 아니라 경로 한정

```
git log --no-merges --format= --name-only HEAD ^df4976cc | sort -u \
  | xargs git restore --source=df4976cc --staged --worktree --
```

추가된 파일은 원천에 없으므로 이 명령이 **지운다**(별도 `rm` 이 필요 없다). 임시 clone 에서 exit 0.

## 리허설 실측 — 버릴 clone, ①~⑥

저장소 밖 임시 clone 에서 `b1793b78` 을 체크아웃하고 위 명령을 돌렸다.

| # | 항목 | 결과 |
| --- | --- | --- |
| ① | 복원 목록 ↔ diff 등식 | `comm -23`·`comm -13` 둘 다 빈 출력 · 78 경로 |
| ② | 복원 명령 exit | 0 |
| ③ | 복원 뒤 `git status --porcelain` 계수 | **D 15 · M 63**(= 추가 15 되돌림 + 수정 63 되돌림) |
| ④ | **트리 동일성** | `git diff --name-only df4976cc -- .` **빈 출력** — 되돌린 트리가 그 main 커밋의 트리와 같다. 갈음을 「HEAD 초록」으로 하지 않는다 |
| ⑤ | compile | `compileKotlin compileTestKotlin`(전 모듈) exit 0 |
| ⑥ | test · 게이트 | `:build-logic:test` · `jarContentGate` · `domainSourceReferenceGate` · `gateRegistrationGate`(전 모듈) **BUILD SUCCESSFUL** |

**되돌림이 실제로 무엇을 되돌리는지**가 ⑥ 의 리포트에 남는다 — 복원된 트리에서 app 의
`jarContentGate` 는 `entries=279`(앞 판의 `jar` 하나)이고, 이 slice 의 HEAD 에서는
`archives=2 entries=657 judged=558`(배포물 `bootJar` 포함)이다. `adapters` 의
`domain-source-reference` 리포트도 복원 뒤 `files=0`(앞 판은 domain 아닌 모듈에서 판정 없이 통과)으로
돌아간다.

## 축별 부분 비활성화 (전면 되돌림 없이)

정책 데이터 한 줄로 끈다 — ① `migration.module` 을 없는 모듈로 · ② `normalize.*=false`(오늘 거동은
어느 쪽이든 같다) · ③ `jar.class-roots`·`jar.external-class-roots` 를 비움(비우면 배포물 판정이 붉는다 —
그것이 의도다) · ④ 끌 수 없다(등식의 한 종류다) · ⑤ `source.import.syntax-names` 로 면제 확대 ·
⑥ `source.scoped-packages` 를 비움 · ⑧ `source.env-sniff.member-pairs` 를 비움.

## 예상 복구 시간

위 복원 명령 하나로 1 분 이내(트리 조작만). ⑤·⑥ 재실측까지 포함하면 clean clone 에서 약 35 분
(리허설 실측값 — cold build).

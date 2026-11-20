# M14 / wf-shell (14A) — rollback

**실측 HEAD: `d77988f91e0444efc38321dd4b71aac6cc8685b7`**(이 slice 의 **마지막 산출물 커밋**).
뒤에 오는 evidence 커밋은 아래 복원 목록 안의 경로를 건드리지 않는다 — 그 사실은 verifier 가
`git diff --name-only <실측 HEAD>..<판정 SHA> -- <복원 경로들>` 로 확인한다.

## 되돌리는 방법

range revert 가 아니라 **in_scope 경로 한정 복원**이다. 목록은 기계 산출이고 라운드마다 다시
뽑는다 — 손으로 적은 목록은 뒤 라운드가 만든 파일을 빠뜨린다.

```
git diff --name-status af6d9c0f4c0d83469ecbb1314eb6d30848f485b2..<판정 SHA> \
  | awk '{print $2}' | sort > /tmp/14a-restore.txt
xargs -a /tmp/14a-restore.txt \
  git restore --source=af6d9c0f4c0d83469ecbb1314eb6d30848f485b2 --staged --worktree --
```

## 복원 원천

착수 base `af6d9c0f4c0d83469ecbb1314eb6d30848f485b2` 하나다. **공유 파일 복원이 없다**: 이
slice 는 `docs/discovery/capability-map.md` 를 아직 건드리지 않았고(머지 차례의 일이다) 다른
레인과 겹치는 파일을 되돌리지 않는다. 그래서 「병합된 main 커밋에서 복원」 조항이 적용되는
자리가 없다.

**전칭식** — 실측 HEAD 와 판정 SHA 사이에서 복원 대상 경로가 움직였다면, 움직인 경로는 전부
**원천(`af6d9c0f`)에 없는 신설 파일**이다. 열거와 원천 부재 확인은 verifier 가 한다.

## 실측 (임시 clone, 저장소 밖)

`d77988f9` 를 체크아웃한 버릴 clone 에서 ①~⑥ 을 돌렸다(수정 라운드 1 뒤 재실측 — 앞 판의
수를 옮기지 않았다).

| 단계 | 결과 |
| --- | --- |
| ① 복원 명령 | 종료 코드 **0** |
| ② D/M 수 | **D 26 · M 45**(합 71 = 기계 산출 목록의 줄 수와 같다) |
| ③ 복원 경로의 base 대비 diff | **빈 출력** |
| ③' **트리 동일성** | 저장소 **전체**의 `git diff af6d9c0f` 가 **빈 출력** · 남은 미추적 **0** |
| ④ compile | `typecheck` 0 · `lint` 0 |
| ⑤ test | 파일 **60** · test **1544** 통과(= 착수 base 의 수) |
| ⑥ 게이트 | `gate:tests` 0 · `gate:src-tree` 0 · `api:types:check` 0 · `build` 0 · `gate:boundary-canary` 0 |

**갈음은 「HEAD 초록」이 아니라 트리 동일성이다**(③'). 되돌린 트리가 base 와 **바이트로 같으므로**
그 트리의 거동은 base 의 거동이고, e2e 두 레인을 다시 돌려 얻을 새 정보가 없다. 그래도 ⑤·⑥ 의
수가 착수 실측과 같은 것을 함께 적는다 — 동일성 단언이 틀렸다면 그 수가 먼저 어긋난다.

## 되돌리면 사라지는 것

캔버스 팔레트 · 사이드바 · 배치 원시 요소 다섯 · 부재 어휘 한 자리 · 게이트 셋(배치 · 부재값
훑기 · 대비) · M13 잔여 둘의 처분. 즉 **이 slice 가 더한 전부**이고, base 의 거동은 그대로
돌아온다(⑤ 의 수가 그것을 센다).

## 되돌림이 건드리지 않는 것

- `docs/usecase/captures/**` — 이 slice 는 그 산출물을 **커밋하지 않는다**(캡처는 in_scope 밖이고
  앞·뒤 비교용으로 scratchpad 에만 둔다). 그래서 복원 목록에 없다.
- `.github/workflows/ci.yml` · Kotlin · `openapi/**` · `api/generated/**` — 편집 0.

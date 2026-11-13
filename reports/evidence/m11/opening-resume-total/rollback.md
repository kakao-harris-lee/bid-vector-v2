# M11 / opening-resume-total — 되돌리는 방법과 그 실측

**실측 HEAD: `0e08d6e3cec0e4fa7da7e26284ed1d0a588b73a1`**(이 slice 의 마지막 산출물 커밋 — 이 파일을
담은 커밋은 그 뒤에 온다).

## 복원 원천

고정 SHA **`32dead40`**(main 에 병합된 커밋 · PR #153). 움직이는 `origin/main` 을 쓰지 않는다. 공유 파일
(`capability-map.md`·`gate-tests.properties`)이 복원 목록에 있으므로 원천은 **머지된 main 의 고정 SHA** 다.

**전제 — 그 SHA 에 이 slice 가 없다.** 이 slice 의 **일곱 커밋 전부**(첫 산출물 커밋 `b7bfda4c` 포함)가
`32dead40` 의 조상이 아니다(`git merge-base --is-ancestor` 가 일곱 번 거짓). 그래서 이 복원은 머지 순간에
아무것도 지우지 않는다.

**셋째 단언 — 원천을 바꿨으므로 쟀다.** 복원 대상 blob 이 착수 base `94ee445b` 와 새 원천 `32dead40` 에서
**여섯 경로 모두 같다**(수정 네 경로는 같은 blob 해시, 신설 둘은 양쪽에서 부재). 두 원천이 같은 복원을
내므로 아래 계수를 새 원천 기준으로 다시 세지 않았다.

## 되돌리는 명령

range revert 가 아니라 **in_scope 경로 한정 복원**이다.

```
git restore --source=32dead40 --staged --worktree -- \
  config/quality/gate-tests.properties \
  docs/discovery/capability-map.md \
  workflow/src/main/kotlin/bidvector/workflow/collection/ClaimedSourceTotal.kt \
  workflow/src/main/kotlin/bidvector/workflow/collection/OpeningSampleFramer.kt \
  workflow/src/test/kotlin/bidvector/workflow/collection/OpeningListResumeTotalTest.kt \
  reports/evidence/m11/opening-resume-total
```

배선 변경이 없어 **비활성화 스위치는 없다** — 되돌리면 개찰결과 목록 슬롯이 공유 누산기의 「마지막 값」
거동으로 돌아가고, 재개 첫 쪽의 `0` 이 다시 앞 배치의 총수를 덮는다.

## 목록 등식

목록은 **기계 산출**이다 — `git diff --name-status 32dead40..HEAD` 가 여덟 경로를 낸다(M 넷 · A 넷).
`comm -23 <변경 경로> <복원 목록>` **빈 출력**: 변경된 경로가 복원 목록에서 빠진 것이 없다.

**빈 등식만으로는 건전하지 않다**(「내가 커밋하지 않아도 경로가 들어오는가」). 그래서 반대 방향도 봤다 —
병합(`e19e7eda`)이 들여온 셋(`README.md`·`milestone-10.md`·`milestone-11.md`)은 원천 자신의 내용이라 이
범위에 **나타나지 않고**, 복원 목록에도 없다(내가 쓴 것이 아니므로 없어야 맞다).

## ①~⑥ 실측 (버릴 clone)

원본 worktree 를 건드리지 않고, 실측 HEAD 를 checkout 한 `--no-hardlinks` clone 에서 돌렸다.

| 단계 | 재는 것 | 결과 |
| --- | --- | --- |
| ① | 복원 명령 종료 코드 | **0** |
| ② | 복원 뒤 상태 계수 | **`D` 4 · `M` 4** — 신설 넷(test 하나 + 장부 셋)이 지워지고 수정 넷이 되돌려졌다 |
| ③ | **트리 동일성** — 그 경로들이 원천과 같은가 | `git diff 32dead40 -- <경로들>` **빈 출력** |
| ④ | compile | 전건 안에서 **초록 · 그러나 실행은 아니다** — compile task 23 개가 전부 `FROM-CACHE`/`UP-TO-DATE` 이고 **접미 없이 실행된 compile 은 0 개**다. cache 적중은 「이 입력이 이미 컴파일된 적 있다」는 뜻이라 ③ 의 결과(원천과 바이트 같은 트리)와 **같은 사실**이고, 그 이상은 아니다 |
| ⑤ | test | **실행했다** — 전건에서는 `:workflow:test` 도 `FROM-CACHE` 였으므로 같은 clone 에서 `:workflow:test --rerun` 을 따로 돌려 **접미 없이 실행**시켰다. exit 0 · workflow **85 클래스 762 통과·실패 0** |
| ⑥ | 게이트 | `./gradlew --no-daemon check` **exit 0 · `FAILED` 0 줄** · 433 task 중 **278 실행** — 부분 게이트로 갈음하지 않았다 |

**조용한 초록을 초록으로 적지 않는다.** ④ 와 ⑤ 는 전건에서 둘 다 cache 적중이었고, 그 가운데 거동을
재는 ⑤ 만 강제 실행으로 바꿨다. ④ 를 실행으로 바꾸지 않은 이유는 그 축이 ⑤ 의 전제라는 것이다 — test 가
실제로 돌았다면 그 클래스들은 컴파일된 것이다.

**갈음은 「HEAD 초록」이 아니라 ③ 의 트리 동일성으로만** 한다. ⑥ 이 초록인 것은 복원된 트리가 **원천과
같은 트리**라는 ③ 의 결과이고, 그 역은 성립하지 않는다.

## 표지 계수 — 완료는 종료 코드가 아니다

| 표지 | 기대 | 실측 |
| --- | --- | --- |
| 내 것 — capability map 의 이 slice 절 제목 | 0 | **0** |
| 내 것 — `gate-tests.properties` 의 이 slice test 등재 | 0 | **0** |
| 내 것 — 신설 test 파일 존재 | 0 | **0** |
| 남의 것 — capability map 의 M10/10D 절 제목 | 보존 | **2** |
| 남의 것 — `gate-tests.properties` 의 형제 갈래 등재 | 보존 | **1** |
| 남의 것 — 형제 갈래 test 파일 존재 | 보존 | **1** |
| 계수 차 — workflow test **클래스·test 수** | 판정 트리보다 각 1 적다 | **85 / 762**(판정 트리 **86 / 763**) |

「내 표지 0」과 「남의 표지 보존」을 **둘 다** 셌다. 표지는 어휘 grep 이 아니라 **항목**(절 제목·등재 키·
파일 존재)으로 셌다 — 상류가 같은 어휘를 쓰면 어휘 grep 은 조용히 공허해진다.

마지막 행이 **거동 층의 표지**다. 복원 트리의 test 실행은 판정 트리보다 클래스 하나·test 하나가 적고,
그 하나가 이 slice 의 것이다(그 결과 XML 이 복원 트리에 **없다**). 파일 존재 계수와 달리 이것은
**게이트가 실제로 돌려 센 수**라, 등재를 지우고 파일만 남기는 식의 반쪽 복원과 구별된다.

## 이 실측의 유효성 술어

실측 HEAD 와 판정 SHA 는 **영원히 다르다**(장부 커밋이 언제나 뒤에 온다). 그래서 대조하는 것은 둘의
동일성이 아니라 **그 사이에 되돌림 대상이 움직였는가**다.

- `git diff --name-only <실측 HEAD>..<판정 SHA> -- <위 복원 목록>` 이 **빈 출력**이면 유효하다.
- 한 줄이라도 나오면 미검증이다. **예외는 하나뿐이다** — 나온 경로가 전부 **복원 원천에 없는 신설 파일**일
  때다. 이 slice 에서 그 사이에 오는 커밋은 **이 파일을 담은 장부 커밋 하나**이고, 그 파일은 원천
  `32dead40` 에 **없다**. 열거와 원천 부재 확인은 verifier 가 한다(이 문서는 전칭식만 적는다).
- `git rev-list --merges <실측 HEAD>..<판정 SHA>` 도 **빈 출력**이어야 한다. main 병합(`e19e7eda`)은 실측
  **앞**에 두었으므로 이 술어가 선다.

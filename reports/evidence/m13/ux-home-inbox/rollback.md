# M13 / ux-home-inbox (13E) — 되돌리기

**range revert 가 아니다.** 이 slice 가 만진 **in_scope 경로만** 착수 base 의 내용으로 돌린다.
range 로 되돌리면 같은 range 에 섞인 남의 커밋까지 지운다.

## 복원 명령

```
git restore --source=bd5cd039 --staged --worktree -- \
  web/src web/config web/e2e/journey reports/evidence/m13/ux-home-inbox
```

경로를 **개별 인자**로 준다(`.`·`-A` 를 쓰지 않는다). 원천은 착수 base `bd5cd039` 의 고정 SHA 이고
움직이는 `origin/main` 이 아니다.

**머지 뒤에 되돌릴 때는 원천이 바뀐다** — 그때는 이 slice 를 담은 병합 커밋의 고정 SHA 를 원천으로
삼고, 복원 대상 blob 이 착수 base 와 그 원천에서 **같은지**를 먼저 센다(다르면 새 원천 기준으로
목록을 다시 산출한다). 저장소 규율의 셋째 단언이다.

## 되돌림 목록 — 기계 산출

`git diff --name-status bd5cd039..<실측 HEAD>` 로 뽑았고 **라운드마다 재산출한다**. 지금:
**16 경로 · 신설 6 · 수정 10.** 전부 위 네 경로 아래에 있다 — 변경 전수와 네 경로가 덮는 수가 둘 다
16 이고 `comm -23` 이 **빈 출력**이다(덮이지 않은 변경 0).

신설 6 은 짝 test 하나(2층 라벨) · 여정 spec 하나 · evidence 넷이다. 새 test 파일과 새 여정 spec 은
**만드는 커밋에서** 각자의 등록부(`web/config/gate-tests.txt` · `web/config/journey-specs.txt`)에
함께 적었다.

`docs/discovery/capability-map.md` 는 `scope.md` in_scope 에 적혀 있으나 **아직 만지지 않았다**
(§12.3 의 절 글자는 머지 차례에 정한다). 그때 이 목록과 아래 실측을 다시 낸다.

이 slice 는 `web/` 와 자기 evidence 디렉터리 **밖을 한 줄도** 만지지 않는다 — Kotlin·OpenAPI·
CI 워크플로·다른 마일스톤의 evidence 가 목록에 없다.

## 실측 — 버릴 clone 에서 ①~⑥

**실측 HEAD: `99961f0c44db23a519c7666d0b3c74e4aa1c30ef`** (복원 대상이 움직이면 앞 실측을 옮기지 않고
다시 잰다 — 이 값은 접힌 칩 칸 수정과 evidence 정정까지 담은 뒤의 재실측이다)

| # | 잰 것 | 결과 |
| --- | --- | --- |
| ① | 복원 명령의 종료 코드 | **0** |
| ② | 복원 뒤 상태 계수 | **D 6 · M 10** — 기계 산출 목록(신설 6 · 수정 10)과 같다 |
| ③ | in_scope 경로가 base 와 같은가 | `git diff bd5cd039 -- <네 경로>` 가 **0 바이트** — 트리 동일성 |
| ④ | 컴파일 | `npm run typecheck` **exit 0** |
| ⑤ | test | `npm run test` **51 파일 · 1169 test 통과** — base 의 수와 같다 |
| ⑥ | 게이트 | `npm run lint` · `npm run gate:src-tree` · `npm run gate:tests` **전부 exit 0** |

⑤ 의 수가 base 의 수와 **같은 것**이 ③ 의 트리 동일성을 값으로 다시 보인다 — 이 slice 가 더한
test **42 개**(1211 − 1169)와 파일 하나가 전부 사라졌다는 뜻이다. 탐침은 버릴 clone 이고 의존은
단단한 링크로 빌려 왔다(복원 경로에 `package.json` 이 없으므로 그대로 유효하다). 재고 나서 지웠다.

갈음은 「HEAD 가 초록이다」가 아니라 **③ 의 트리 동일성**이다. ④⑤⑥ 은 그 트리가 실제로 서는지를
값으로 확인한 것이고, 동일성이 성립하면 base 의 CI 결과가 그대로 적용된다.

## 실측 뒤에 움직인 것

`git diff --name-only <실측 HEAD>..<판정 SHA> -- <위 네 경로>` 가 비어 있지 않다면, **그 줄은 전부
원천(`bd5cd039`)에 없는 신설 파일이다** — 이 slice 의 evidence 파일뿐이고 출하 코드는 실측 HEAD
이후 한 줄도 움직이지 않는다. 그 전칭이 참인지(열거와 원천 부재 확인)는 verifier 가 센다.

`git rev-list --merges <실측 HEAD>..<판정 SHA>` 도 **빈 출력**이어야 한다(실측 뒤 main 병합이
없다). 머지 차례에 main 을 병합하면 실측 자리는 그 병합 커밋이 되고, 위 ①~⑥ 과 목록을 거기서
다시 낸다.

## 비활성화 — 되돌리지 않고 멈추는 길

이 slice 는 화면의 **차례·조건·펼침**을 바꾸므로 기능 토글이 없다. 되돌림의 입도는 위 복원
하나이고, 부분적으로 끌 수 있는 자리는 셋이다.

- **홈의 기본 목록** — `TodayNoticesScreen` 의 `enabled: searching`(공고 질의)과 `enabled: !searching`
  (제안 질의) 두 줄을 각각 `true`·`true` 로 두면 앞 판처럼 두 목록이 함께 선다. 칩은 남는다.
- **2층 라벨 펼침** — `StateBadge` 의 `why` 분기를 앞 판(글자 + 시각 은폐 문면)으로 되돌리면 앱
  전체가 한 번에 돌아간다. 그 자리가 **한 컴포넌트**인 것이 이 축의 되돌림 단위다.
- **알림함 「더 보기」** — `inbox.hasNextPage` 블록을 지우면 쪽 질의는 남고 버튼만 사라진다. 질의
  자체를 되돌리려면 `queryFn` 의 인자를 빼야 하는데, 그러면 여정 술어가 붉는다(아래 주의).

**게이트를 끄는 일은 레인이 임의로 하지 않는다.** 새 등록부 줄 둘(`gate-tests.txt` 의 짝 test ·
`journey-specs.txt` 의 여정 spec)은 **양방향 등식**이라 파일과 줄을 함께 다뤄야 한다.

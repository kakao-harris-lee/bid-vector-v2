# M13 / ux-copy (13A) — rollback

되돌림은 **range revert 가 아니라 in_scope 경로 한정 복원**이다.

```
git restore --source=bd5cd039eaf70f6423cf85e3bf1923bb367fa16e --staged --worktree -- <복원 목록>
```

**복원 목록은 기계 산출이다** — `git diff --name-status <base>..<실측 HEAD>` 그대로이고 손으로
적지 않는다. 라운드마다 재산출한다.

**실측 HEAD: `bdf8c2f10844668e40a9320089046d100293402d`**

이 파일 자신은 **원천(base)에 없는 신설 파일**이다. 그래서 실측 HEAD 와 판정 SHA 사이에서
움직이는 복원 대상은 **원천 부재 파일뿐**이라는 전칭식만 여기 적는다 — 그 열거와 원천 부재
확인은 verifier 가 한다. 같은 range 에 병합 커밋은 없다.

## 복원 목록 — 23 경로(신설 6 · 수정 17 · 삭제 0)

신설 여섯은 복원이 **지우는** 것이고, 수정 열일곱은 base 내용으로 **되돌리는** 것이다.

| 종류 | 경로 |
| --- | --- |
| 신설(A) | `reports/evidence/m13/ux-copy/` 의 scope · commands · design-review · checklist |
| 신설(A) | 문면 게이트 두 층 — `web/scripts/` 의 소스 층 · `web/src/screens/` 의 렌더 층 |
| 수정(M) | `web/config/gate-tests.txt` |
| 수정(M) | `web/e2e/journey/` 의 여정 spec 넷 |
| 수정(M) | `web/src/RouteError.tsx` · `web/src/ScreenPlaceholder.tsx` · `web/src/routes.tsx` |
| 수정(M) | `web/src/display/` 의 resultDisplay · worksheetDisplay · myBidResultDisplay |
| 수정(M) | `web/src/screens/` 의 Inbox·Onboarding·Settings 화면 셋과 그 짝 test 셋 |

`web/src/display/moneyDisplay.ts` 는 계약이 겹침으로 적었으나 **편집 0** 으로 끝나 목록에 없다.
금지 꼴의 데이터 파일 한 개는 같은 range 안에서 생겼다 사라졌으므로 목록에 없다(§계약 갱신 (3)).

## 실측 — 버릴 clone 에서 ①~⑥

저장소를 `--no-hardlinks` 로 clone 해 실측 HEAD 를 체크아웃하고, 설치본만 하드링크로 들여
복원을 돌렸다(그 clone 은 버린다).

| 걸음 | 결과 |
| --- | --- |
| ① 명령 종료 코드 | **0** |
| ② 바뀐 파일 수 | **D 6 · M 17 · 합 23** — 복원 목록과 같다 |
| ③ base 와의 diff | **0 바이트**. 더해 **추적 트리 전체**가 base 와 같다(`git diff <base> --stat` 이 0 줄) — 갈음이 아니라 트리 동일성이다 |
| ④ 컴파일 | `npm run typecheck` **exit 0** |
| ⑤ test | `npm run test` **exit 0** · **51 파일 · 1169 test**(이 slice 가 더한 2 파일 · 29 test 가 전부 사라진다) |
| ⑥ 게이트 | `npm run lint` **0** · `npm run gate:src-tree` **0** · `npm run gate:tests` **0** |

⑥ 이 초록인 것이 이 slice 의 되돌림에서 특히 중요하다: 게이트 test 둘이 사라지면서
`web/config/gate-tests.txt` 의 두 줄도 함께 되돌아가므로, **등록부와 실행 결과의 등식**이 그대로
선다(한쪽만 되돌리면 그 등식이 붉는다).

## 되돌리면 사라지는 것

- 문면 게이트 **두 층 전부**(소스 층의 전수 판정·포함 단언·접두사 등식·라우트 경계 축, 렌더 층의
  DOM 스윕·결합 대조·음성/양성 대조·fixture 어휘 단언·도달 단언·라벨 술어).
- 다섯 자리의 개발 코드와 고친 자리의 내부 용어가 **옛 문면으로 돌아온다**.
- 라우트 경계가 **던진 값의 원문을 다시 그리고** 「다시 시도」가 사라진다.
- 자리 표 라벨의 머리 글자가 돌아오고, 여정 locator 의 `exact: true` 도 함께 되돌아간다
  (둘은 짝이다 — 한쪽만 되돌리면 여정이 붉는다).

## 머지 뒤 되돌림

머지 뒤에는 이 복원을 쓰지 않는다 — **PR revert** 가 그 자리다. 공유 파일
(`web/config/gate-tests.txt` · 여정 spec 넷)은 다른 레인도 같은 range 에서 만지므로, 머지 뒤
경로 한정 복원은 남의 줄까지 되돌린다.

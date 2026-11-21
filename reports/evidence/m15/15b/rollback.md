# M15 / 15B — rollback

되돌림은 **range revert 가 아니라 in_scope 경로 한정 복원**이다. 목록은 손으로 적지 않고
`git diff --name-status <base>..<실측 HEAD>` 가 기계로 낸다 — 라운드마다 재산출한다.

```
base:      99f2fb25989078d7ab1640de0dc8e399f5d1688d
실측 HEAD: 48a88a1741e4a04054d86398baa7c63a46a34213
```

## 복원 명령

`<M>` 은 그 range 의 `--diff-filter=M` 경로 전수(38), `<A>` 는 `--diff-filter=A` 경로 전수(14)다.
둘 다 명령으로 뽑고, 개별 인자로 넘긴다(`-A`·`.` 를 쓰지 않는다).

```
git diff --name-only --diff-filter=M <base>..<HEAD>          # <M>
git diff --name-only --diff-filter=A <base>..<HEAD>          # <A>
git restore --source=<base> --staged --worktree -- <M>
git rm -f -- <A>
```

`<A>` 를 `git restore` 로 되돌릴 수 없는 이유: 그 경로들은 base 에 **없다**(pathspec 이 맞지
않는다). 신설 파일의 되돌림은 삭제다.

## 복원 원천

이 slice 는 **아직 머지되지 않았다.** 그래서 공유 파일(`config/quality/*` · `openapi/*` ·
`web/src/api/*` · `web/config/*`)도 복원 원천이 착수 base 다 — 그 SHA 에 이 slice 의 변경이
없고, 다른 레인의 변경도 아직 main 에 들어오지 않았다. **머지 뒤의 되돌림은 PR revert 가
받는다**(병합 커밋의 충돌 해소 줄은 경로 한정 복원 밖이다).

## 실측 (임시 clone, 2026-11-21)

버릴 clone 에서 위 네 명령을 그대로 돌렸다.

| 축 | 값 |
| --- | --- |
| ① 명령 종료 코드 | `git restore` 0 · `git rm` 0 |
| ② D/M 계수 | 삭제 14 · 수정 38 — 산출 목록의 `A` 14 · `M` 38 과 같다 |
| ③ 남은 diff | `git diff <base> --name-only` **빈 출력** |
| ④~⑥ 갈음 | **트리 동일성** — `git diff-index --quiet <base>`(워크트리)와 `--cached <base>`(색인)가 **둘 다 exit 0**. 되돌린 트리가 base 와 바이트까지 같으므로 컴파일·test·게이트를 다시 돌리지 않는다 |

clone 에 남은 미추적 둘은 이 리허설이 만든 경로 목록 파일이고 slice 산출물이 아니다.

## 실측 HEAD 이후의 이동

실측 뒤에 선 커밋은 **evidence 뿐**이다. 그 커밋이 움직인 경로는 전부 **착수 base 에 없는
신설 파일**(`reports/evidence/m15/15b/` 아래)이므로 복원 명령에서 `git rm` 쪽에 들고, 그
목록이 `--diff-filter=A` 재산출에서 함께 늘어난다 — 되돌림 대상의 **내용**이 움직인 것이
아니다. 열거와 「원천 부재」 확인은 검증 레인이 한다.

## 복원 뒤 사라지는 것 / 남는 것

- 사라진다: `POST /api/accounts` 와 그 화면·여정, V29(열 단위 INSERT·DEFAULT 셋·잠금 상한),
  `SignUpPolicyData`·`SignUpThrottle`·`AccountSignUpPort`, `OPEN_ROUTES` 의 둘째 자리.
- 남는다: 로그인·세션 경로 전부(이 slice 는 그 자리를 **더하기만** 했다), 운영자 명령 `rotate`,
  15A·15C 의 표(건드리지 않았다).
- **이미 만들어진 계정 행은 DB 에 남는다.** 복원은 소스만 되돌리고 데이터를 지우지 않는다 —
  그 계정들은 로그인으로 계속 들어온다(권한이 새는 방향이 아니다). 행을 지워야 하면 소유자
  연결의 일이고 이 slice 밖이다.

## 비활성화 경로(되돌리지 않고 막기)

소스를 되돌리지 않고 가입만 닫으려면 **`OPEN_ROUTES` 에서 그 한 줄을 빼면 된다** — 그 순간 기본
거부 필터가 `POST /api/accounts` 를 401 로 돌려보내고, 집합 등식 test 둘이 그 변경을 드러낸다.
DB 권한까지 닫으려면 `REVOKE INSERT (login_name, passphrase_hash) ON account FROM bidvector_app`
한 줄이 다음 마이그레이션에 선다(V29 의 역이고, 그 뒤 가입은 500 이 아니라 **권한 거부**다).

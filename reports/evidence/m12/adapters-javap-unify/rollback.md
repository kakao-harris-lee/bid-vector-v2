# M12 / adapters-javap-unify — rollback

**실측 HEAD: `2dfe41f5fa3c1b509f8e34a15d482b8e050d3de8`**(이 slice 의 마지막 산출물 커밋).

## 되돌리는 방법

in_scope 경로 한정 복원이다 — range revert 가 아니다.

```
git restore --source=<원천> --staged --worktree -- <복원 목록>
```

- **원천은 착수 base `10d208d2664c6bb87cd7082a2853c08c2c0d3236`**(고정 — 움직이는 `origin/main` 이 아니다).
- **복원 목록은 기계 산출**이다: `git diff --name-only <원천>..<HEAD>`. 라운드마다 다시 뽑는다.
  이 실측 시점의 수는 **26 경로**(아래 ② 가 그 계수를 함께 낸다).

## 머지 차례에 바뀌는 것 — 공유 파일 둘

이 slice 는 `config/quality/gate-tests.properties` 와 `docs/discovery/capability-map.md` 를 만진다. **둘 다
다른 slice 도 만지는 자리**이고, 팀장이 알린 머지 순서상 12A 는 12D 머지 뒤에 들어간다. 그러므로
머지 차례에는:

1. 원천을 **그 시점 병합된 main 의 고정 SHA** 로 바꾼다(착수 base 로 복원하면 그 사이 머지된
   12B·12C·12D 의 줄까지 지운다).
2. **셋째 단언**을 함께 잰다 — 복원 대상 blob 이 착수 base 와 새 원천에서 **같은가**. 다르면 새 원천
   기준으로 ②~⑥ 을 다시 센다.
3. 「내 표지 0 · 남의 표지 보존」을 **항목 집합**으로 다시 센다(아래 표와 같은 꼴).

## ①~⑥ 실측 — 버릴 임시 clone 에서

원본 worktree 를 건드리지 않았다. clone → 실측 HEAD 로 checkout → 복원 → 여섯 축.

| 축 | 재는 것 | 결과 |
| --- | --- | --- |
| **①** | 복원 명령의 종료 코드 | **0** |
| **②** | 복원 뒤 `git status --porcelain` 의 D/M 계수 | **D 7 · M 19**(합 26 = 복원 목록 수). D 7 은 base 에 없던 **신설 파일**이고 M 19 는 base 에 있던 파일이다 |
| **③** | `git diff <원천> -- <복원 목록>` | **0 바이트** |
| **④** | `:adapters:compileTestKotlin` | **exit 0** |
| **⑤** | `:adapters:test --rerun` | **exit 0** |
| **⑥** | `:adapters:gateRegistrationGate`(등재 양방향 등식) | **exit 0** |

⑥ 이 이 slice 에서 의미 있는 게이트인 이유: 신설 test class 를 등재한 줄이 **함께** 사라져야 양방향
등식이 성립한다. 한쪽만 되돌아가면 그 게이트가 붉는다.

## 「내 표지 0 · 남의 표지 보존」 — 어휘 grep 이 아니라 항목 집합으로

공유 파일 둘에서 **집합 차**를 쟀다. 어휘 grep 은 상류가 같은 낱말을 쓰면 조용히 공허해진다.

| 공유 파일 | 센 항목 | 내 표지(사라져야) | 남의 항목 유실(0 이어야) | 함께 확인 |
| --- | --- | --- | --- | --- |
| `gate-tests.properties` | 등재된 test class **FQN 집합** | **1**(이 slice 가 더한 판정 test) | **0** | M11 이 등재한 추출 층 대조 class 가 **보존** |
| `capability-map.md` | §12.3 **하위 절 제목 집합** | **1**(이 slice 의 절) | **0** | 절 수 **16 → 15** |

## 실측 뒤에 온 커밋 — 재측정이 필요한가

`rollback.md`(이 파일) 하나다. **착수 base 에 없는 신설 파일**이므로 CLAUDE.md 의 예외에 해당한다 —
실측 HEAD 와 판정 SHA 사이에 움직인 복원 대상이 **전부 원천에 없는 신설 파일**이면 실측은 유효하다.
그 전칭식만 여기 적고, **열거와 「원천 부재」 확인은 verifier 가 한다**. 두 SHA 사이에 **병합 커밋은
없다**(`git rev-list --merges` 가 빈 출력이어야 하고, 이 slice 는 머지 차례 전까지 main 을 병합하지
않는다).

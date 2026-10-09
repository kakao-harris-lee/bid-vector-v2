# M6/6E-2c — 산출물 점검 · 알려진 제한 · OPEN

## 산출물이 공허하지 않음을 재는 술어 (scope.md 표의 오른쪽 열)

| ID | 술어 | 결과 |
|---|---|---|
| **E-1** | 상향 **전** 현 값에서 초록 · 음성 대조 넷 | 두 이미지 exit 0(`os=` 줄이 선언과 일치, `os-pkgs-results=1`). 음성 대조 넷(정책 name·정책 family·`os-pkgs` Result·`.Metadata.OS`)이 **전부 exit 2**, 셋은 서로 다른 문면. **상향 커밋에서 정책을 안 올리면 exit 2** 를 E-2 에서 한 번 실측 |
| **E-2** | 위생 S-22a/b 초록 · 실행 파일 재실측 | 두 게이트 exit 0, `base-layer-접두-일치=true` 둘 다. 금지 실행 파일 9 · 필수 `curl` 1 · 의존 layer 98 · 크기 둘 다 상한 아래. `FROM` 넷 · `LABEL` 둘 · 정책 digest 둘이 **글자 그대로 같다** |
| **E-3** | 해석된 판을 test 가 잰다 · 변이 RED | 배포물 `BOOT-INF/lib` 에서 tomcat 11.0.26 · jackson 2.21.7 / 3.1.7. 변이 둘(tomcat constraint 제거 · jackson2 platform 제거)이 **각자 자기 축의 test 만** RED |
| **E-4** | 스캔 exit 0 · stale 0 · 남은 등재마다 사유·만료 | 등재 **0** 으로 두 이미지 exit 0. 차단 후보 ml 52→0 · 앱 15→0. **등재가 0 이라 사유·만료를 적을 줄이 없다** — 남은 HIGH/CRITICAL 은 전부 수정판 미공시라 차단 대상이 아니다(아래 알려진 제한 1) |
| **E-5** | — | runbook §8.2 표 두 행 · §8.3.1 신설 · §8.5·§8.7 재서술. 기존 절 번호 보존 |

## 받은 OPEN 의 처분

| OPEN | 처분 |
|---|---|
| `OPEN-6E2B-OS-MATCH-PREDICATE` | **해소**(D-6E2C-1) — 상향 **전** 커밋에서 닫았고, 바로 다음 커밋이 그 술어에 걸리는 것을 실측했다 |
| `OPEN-6E2B-BASE-IMAGE-BUMP` | **해소**(D-6E2C-2) — 두 베이스 상향 |
| `OPEN-6E2B-DEPENDENCY-BUMP` | **해소**(D-6E2C-3) — JVM 보안 하한 셋 |
| D-6E2B-1 의 기한 2026-10-31 | **소진** — 미루지 않고 그 전에 걷었다 |

## 리뷰 요청 조건 (evidence-pack)

- [x] 구현 diff 커밋됨 — `git status --porcelain -- <in_scope 개별 인자>` 빈 출력 · **양성 대조**: 변이·음성 대조마다 `git diff --numstat` 이 수치를 냈고 복원 뒤 빈 출력으로 돌아왔다(`checkout --` 은 전부 커밋 뒤에만 썼다)
- [x] acceptance 전부 exit 0 — commands.md. 측정 체인이 **시작과 끝에서 HEAD 를 찍어 같음을 단언**한다
- [x] 전건 게이트 — Kotlin `check` 전건 + `container` job 로컬 재현(부분 게이트로 줄이지 않았다)
- [x] 정책 version 근거 — 판정 **값**이 바뀐 것은 베이스 digest·배포판 선언·JVM 하한이고 **스키마는 불변**이라 `policy.version` 을 올리지 않았다(새 키 `scan.os.*` 는 추가이고, 셸 파서가 kind 전수로 부재를 끊는다)
- [x] 알려진 제한과 rollback — 아래 · rollback.md
- [x] 비밀값 스캔 — 참조형으로 실행, 매치 0(commands.md)

## 알려진 제한

1. **「통과」는 「취약점 없음」이 아니다.** ml-serving 에 CRITICAL 2 · HIGH 55 가 남아 있고, 차단 대상이 아닌 이유는 단 하나 — **수정판이 공시되지 않았다**(`block.only-fixed=true`). 상류가 수정판을 내는 날 이 게이트가 저절로 붉어진다. 앱 쪽에는 HIGH/CRITICAL 이 0 이다.
2. **차단 문턱 아래의 수정 가능 finding 을 걷지 않았다** — ml MEDIUM 5 · LOW 1, 앱 MEDIUM 2. 문턱을 내리는 것은 다른 모든 이미지의 판정을 함께 바꾸는 결정이라 이 slice 의 범위 밖이다.
3. **OS 매칭 술어는 `os-pkgs` 축만 잰다.** 언어 생태계 층(`lang-pkgs`)이 통째로 비는 경우는 여전히 나열 패키지 하한만 본다 — 6E-2b 의 알려진 제한 9 와 같은 계열이고 이 slice 가 좁힌 것은 OS 축 하나다.
4. **정확 일치의 대가.** 상류가 베이스 태그를 재발행해 point release 만 바뀌어도 게이트가 exit 2 로 끊는다. 의도한 fail-closed 지만, **저장소를 건드리지 않은 PR 이 붉어질 수 있다**(취약점 DB 축과 같은 성질). 처방은 runbook §8.3.1.
5. **보안 하한의 기대값이 카탈로그에서 온다.** `BootJarSecurityFloorTest` 는 「선언이 효과를 냈는가」만 잰다 — 카탈로그 값을 내리면 그 test 는 초록인 채로 하한이 내려가고, 그 축을 지는 것은 취약점 게이트다(해당 CVE 가 되살아나 exit 1). 두 게이트가 짝으로만 닫힌다.
6. **Boot 판을 올리지 않았다.** 4.1.x 는 4.1.1 이 최신이라 BOM 위에 하한을 얹는 형태다. Boot 4.2 가 안정판이 되면 이 세 하한은 BOM 과 겹쳐 **중복 선언**이 되므로 그때 걷는 것이 맞다(`OPEN-6E2C-BOOT-FLOOR-SUNSET`).
7. **6E-2b 의 알려진 제한 1~7·9 는 그대로 승계된다** — 서명 검증 부재 · 로컬 이미지만 잼 · DB 를 실행마다 받음 · 네트워크 의존 셋 · 셸 정책 파서 둘 · 두 셸 게이트의 자기 시험 부재 · 멀티아키 미대응.

## OPEN (신규)

| ID | 내용 |
|---|---|
| `OPEN-6E2C-BOOT-FLOOR-SUNSET` | Boot 가 tomcat 11.0.26 · jackson 2.21.7/3.1.7 이상을 BOM 으로 관리하는 판이 되면 카탈로그의 하한 셋과 `platform()`·`constraints` 선언을 걷는다. 남겨 두면 **BOM 보다 낮은 하한**이 되어 판 올림을 조용히 막는 날이 온다 |

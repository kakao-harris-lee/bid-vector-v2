# M6/6E-2c — 산출물 점검 · 알려진 제한 · OPEN

## 산출물이 공허하지 않음을 재는 술어 (scope.md 표의 오른쪽 열)

| ID | 술어 | 결과 |
|---|---|---|
| **E-1** | 상향 **전** 현 값에서 초록 · 음성 대조 넷 | 두 이미지 exit 0(`os=` 줄이 선언과 일치, `os-pkgs-results=1`). 음성 대조 넷(정책 name·정책 family·`os-pkgs` Result·`.Metadata.OS`)이 **전부 exit 2**, 셋은 서로 다른 문면. **상향 커밋에서 정책을 안 올리면 exit 2** 를 E-2 에서 한 번 실측. **일괄에서 ⓑ 를 「Result 실재」에서 「패키지를 담은 Result 실재」로 좁혔다**(vr F-2) — 부모판은 같은 변이에 exit 0 |
| **E-2** | 위생 S-22a/b 초록 · 실행 파일 재실측 | 두 게이트 exit 0, `base-layer-접두-일치=true` 둘 다. 금지 실행 파일 9 · 필수 `curl` 1 · 의존 layer 98 · 크기 둘 다 상한 아래. `FROM` 넷 · `LABEL` 둘 · 정책 digest 둘이 **글자 그대로 같다** |
| **E-3** | 해석된 판을 test 가 잰다 · 변이 RED | 배포물 `BOOT-INF/lib` 에서 tomcat 11.0.26 · jackson 2.21.7 / 3.1.7. 변이 둘(tomcat constraint 제거 · jackson2 platform 제거)이 **각자 자기 축의 test 만** RED. 일괄에서 test 를 보강했다(cr L-2 분해 실패를 실패로 · L-3 major 별 술어) — **그리고 배포물을 test task 의 입력으로 선언했다**: `dependsOn` 만으로는 jar 를 바꿔도 `:app:test` 가 UP-TO-DATE 로 건너뛰어 **아무것도 돌지 않은 채 exit 0** 이었다(그 침묵이 첫 변이 회차를 무효로 만들었고, 선언 뒤 같은 변이가 exit 1 로 끊는다) |
| **E-4** | 스캔 exit 0 · stale 0 · 남은 등재마다 사유·만료 | 등재 **0** 으로 두 이미지 exit 0. 차단 후보 ml 52→0 · 앱 15→0. **등재가 0 이라 사유·만료를 적을 줄이 없다** — 남은 HIGH/CRITICAL 은 전부 수정판 미공시라 차단 대상이 아니다(아래 알려진 제한 1) |
| **E-5** | — | runbook §8.2 표 두 행 · §8.3.1 신설 · §8.5·§8.7 재서술. 기존 절 번호 보존 |

## 받은 OPEN 의 처분

| OPEN | 처분 |
|---|---|
| `OPEN-6E2B-OS-MATCH-PREDICATE` | **부분 해소**(D-6E2C-1) — 「아무도 모르게 배포판이 바뀌는 일」은 닫았다(상향 **전** 커밋에서 닫았고 바로 다음 커밋이 걸리는 것을 실측). **남는 절반**: 바뀐 배포판을 정책에 그대로 옮겨 적으면 DB 가 그것을 덮지 않아도 통과한다 → `OPEN-6E2C-OS-DB-COVERAGE` |
| `OPEN-6E2B-BASE-IMAGE-BUMP` | **해소**(D-6E2C-2) — 두 베이스 상향 |
| `OPEN-6E2B-DEPENDENCY-BUMP` | **해소**(D-6E2C-3) — JVM 보안 하한 셋 |
| D-6E2B-1 의 기한 2026-10-31 | **소진** — 미루지 않고 그 전에 걷었다 |

## 리뷰 요청 조건 (evidence-pack)

- [x] 구현 diff 커밋됨 — `git status --porcelain -- <in_scope 개별 인자>` 빈 출력 · **양성 대조**: 변이·음성 대조마다 `git diff --numstat` 이 수치를 냈고 복원 뒤 빈 출력으로 돌아왔다(`checkout --` 은 전부 커밋 뒤에만 썼다)
- [x] acceptance 전부 exit 0 — commands.md. 측정 체인이 **시작과 끝에서 HEAD 를 찍어 같음을 단언**한다
- [x] 전건 게이트 — Kotlin `check` 전건 + `container` job 로컬 재현(부분 게이트로 줄이지 않았다)
- [x] 정책 version 근거 — **정정(이 문장의 앞 판은 틀렸다)**: `scan.os.*` 는 스키마 **추가**이므로 `vuln-policy.properties` 의 판을 **1 → 2** 로 올렸다(`contract-policy.properties` 선례 — 값 하나 바뀐 승인 태그에도 올렸다). `image-hygiene-policy*.properties` 는 digest **값**과 실측 주석만 바뀌어 **올리지 않는다** — 그 파일의 선례는 판을 **스키마**에 묶는다(판을 올린 세 커밋이 전부 스키마 변경이고, 키를 더하고도 안 올린 커밋이 있다). 저장소 안에서 두 선례가 엇갈리는 자리라 어느 쪽을 따랐는지 각 커밋 메시지에 남겼다. **선례를 양쪽 다 든다**(verifier r1 L-b): 올린 쪽 `contract-policy.properties`(`75706de7` — 값 하나) · 올리지 않은 쪽 `image-hygiene-policy.properties`(`ecaca874` — **키를 더하고도** 안 올림). 전자를 따랐다. **알려진 제한**: 이 키를 읽는 게이트는 없다(실측) — 사람이 읽는 표시다
- [x] 알려진 제한과 rollback — 아래 · rollback.md
- [x] 비밀값 스캔 — 참조형으로 실행, 매치 0(commands.md)

## 알려진 제한

1. **「통과」는 「취약점 없음」이 아니다.** ml-serving 에 CRITICAL 2 · HIGH 55 가 남아 있고, 차단 대상이 아닌 이유는 단 하나 — **수정판이 공시되지 않았다**(`block.only-fixed=true`). 상류가 수정판을 내는 날 이 게이트가 저절로 붉어진다. 앱 쪽에는 HIGH/CRITICAL 이 0 이다.
2. **차단 문턱 아래의 수정 가능 finding 을 걷지 않았다** — ml MEDIUM 5 · LOW 1, 앱 MEDIUM 2. 문턱을 내리는 것은 다른 모든 이미지의 판정을 함께 바꾸는 결정이라 이 slice 의 범위 밖이다.
3. **OS 매칭 술어는 `os-pkgs` 축만, 그것도 절반만 잰다.** ⓐ 언어 생태계 층(`lang-pkgs`)이 통째로 비는 경우는 여전히 나열 패키지 하한만 본다(6E-2b 제한 9 와 같은 계열). ⓑ **그리고 「선언과 실제가 같은가」를 볼 뿐 「그 배포판에 DB 가 있는가」를 보지 않는다** — `OPEN-6E2C-OS-DB-COVERAGE`. 그 절반은 runbook §8.2 의 사람 신호와 §8.3.1 의 사전 확인이 진다.
4. **정확 일치의 대가 — 다만 「무변경 PR 이 붉어진다」는 아니다**(verifier r1 F-4 정정). `FROM` 이 다이제스트 핀이고 `.Metadata.OS` 는 그 이미지 내용에서 나오며 trivy 도 핀이라, **저장소를 건드리지 않고 배포판이 바뀔 경로가 없다.** 실제 성질은 그 반대다: **베이스를 올릴 때마다 정책 동반 갱신을 강제**한다(올리지 않으면 exit 2). 그 강제가 목적이고, 대가는 상향 커밋이 늘 넷을 함께 움직여야 한다는 것이다(runbook §8.3.1).
5. **보안 하한의 기대값이 카탈로그에서 온다.** `BootJarSecurityFloorTest` 는 「선언이 효과를 냈는가」만 잰다 — 카탈로그 값을 내리면 그 test 는 초록인 채로 하한이 내려가고, 그 축을 지는 것은 취약점 게이트다(해당 CVE 가 되살아나 exit 1). 두 게이트가 짝으로만 닫힌다.
6. **Boot 판을 올리지 않았다.** 4.1.x 는 4.1.1 이 최신이라 BOM 위에 하한을 얹는 형태다. Boot 4.2 가 안정판이 되면 이 세 하한은 BOM 과 겹쳐 **중복 선언**이 되므로 그때 걷는 것이 맞다(`OPEN-6E2C-BOOT-FLOOR-SUNSET`).
7. **ml-serving 이미지가 크기 상한의 86% 다**(code-review r1 L-1). 상한 400,000,000 은 **재현되지 않는 113.7MB 기록 위에서** 고른 값이고(원인 미상), 실측은 343,090,786 이다. **의존 하나만 늘어도 S-22a 가 붉어진다** — 그때 상한을 올릴지 이미지를 줄일지는 정해져 있지 않다.
8. **`compatibilitySmoke.expectedModules` 의 `tools.jackson.core:jackson-databind` 는 test 전용 fixture 로 충족된다**(code-review r1 L-4). 같은 파일이 `testImplementation(libs.jackson.databind)` 로 그 좌표를 이미 올리므로, 그 한 줄은 **production 그래프가 jackson 3 databind 를 해석한다는 것을 증명하지 않는다**. 나머지 둘(jackson 2 databind · tomcat-embed-core)은 test 전용 출처가 없다. 실제 잠금은 배포물을 여는 `BootJarSecurityFloorTest` 라 커버리지 구멍은 아니다.
9. **6E-2b 의 알려진 제한 1~7·9 는 그대로 승계된다** — 서명 검증 부재 · 로컬 이미지만 잼 · DB 를 실행마다 받음 · 네트워크 의존 셋 · 셸 정책 파서 둘 · 두 셸 게이트의 자기 시험 부재 · 멀티아키 미대응.

## OPEN (신규)

| ID | 내용 |
|---|---|
| `OPEN-6E2C-OS-DB-COVERAGE` | OS 매칭 술어는 정책 선언과 스캔 결과가 **같은지**만 본다 — 스캐너 DB 가 그 배포판을 **덮는지**는 보지 않는다. 실측: SBOM 의 OS 판을 `99.0` 으로 바꾸고 정책을 같은 값으로 맞추면 **exit 0** 이고 findings 가 284 → 6 으로 준다. 게이트로 닫으려면 스캐너 경고 **문자열**에 술어를 걸어야 해(스타일 하나로 열린다) 두지 않았다 — 오늘은 runbook §8.2 의 사람 신호와 §8.3.1 의 사전 확인이 진다 |
| `OPEN-6E2C-BOOT-FLOOR-SUNSET` | Boot 가 tomcat 11.0.26 · jackson 2.21.7/3.1.7 이상을 BOM 으로 관리하는 판이 되면 카탈로그의 하한 셋과 `platform()`·`constraints` 선언을 걷는다. 남겨 두면 **BOM 보다 낮은 하한**이 되어 판 올림을 조용히 막는 날이 온다 |

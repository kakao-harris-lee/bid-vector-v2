# M6/6E-2b — 산출물 점검 · 알려진 제한 · OPEN

산출물 HEAD `ebe8ebfc` 기준. 수정 라운드 1(D-6E2B-5) 반영.

## 산출물이 공허하지 않음을 재는 술어 (scope.md 표의 오른쪽 열)

| ID | 술어 | 결과 |
|---|---|---|
| V-1 | 래퍼가 키 누락·값 모양 위반을 비-0 | 값 종류 일곱(`text`/`numeric`/`list`/**`enum-list`**/`bool`/`sha256`/`version`). 중복 키·빈 값·CRLF·허용 문자 밖 원소는 `exit 2`. **`enum-list` 는 값 영역까지 본다** — severity·scanners·pkg-types 가 도구 열거값에 없으면 거부(N4). 선언된 kind 전부가 `scan.min-packages`·`scan.min-analyzed-packages`·`scan.java-db-required` 셋을 갖는지 전수 확인 |
| V-2 | 음성 대조 전부 RED | (가) N1·N2·N3 · (나) N4~N8 · r0 승계 O1~O7·N9 — **전부 기대와 일치**(commands.md 표). 판정 불가 갈래는 exit 2, 차단은 exit 1 |
| V-3 | 로컬 재현 exit 0 · 워크플로에 버전 숫자 0 | `container` job 의 `run` step 전부 exit 0. trivy 관련 숫자는 워크플로에 **0개** — 버전·자산·체크섬 셋 다 정책에서 읽고, 설치 step 이 키마다 「정확히 한 번」과 값 모양을 본다 |
| V-4 | 등재마다 실재 finding 과 대응(stale 0) | 게이트가 매 실행에서 잰다. 2026-10-09 실측: ml-serving 52=52, app 15=15, 미등재 0, stale 0. 만료는 D-6E2B-1 대로 두 갈래(CRITICAL 10 → 2026-10-31 · HIGH 57 → 2026-12-31) |
| V-5 | 이미지 재빌드·위생 S-22a 초록 | S-21 재빌드 exit 0(빌드 로그가 uv 다이제스트로 메타데이터를 읽는다), S-22a exit 0 |
| V-6 | — | runbook §8 신설(§8.4.1 암묵 입력 절 추가, §8.2 exit 2 사유 표). 기존 절 번호 보존 |

## 리뷰 요청 조건 (evidence-pack)

- [x] 구현 diff 커밋됨 — `git status --porcelain -- <in_scope 개별 인자>` 빈 출력. **양성 대조**: 음성
      대조마다 `git diff --numstat` 이 실제 변경을 수치로 냈고 복원 뒤 빈 출력으로 돌아왔다
- [x] acceptance 전부 exit 0 — commands.md. **측정 체인이 시작과 끝에서 HEAD 를 찍어 같음을 단언**한다
- [x] 전건 게이트 — `container` job 전부 + Kotlin `check` 전건(부분 게이트로 줄이지 않았다)
- [x] 정책 version 근거 — 신설이라 `policy.version=1`. 도구 판 선택 근거는 commands.md 「도구 채택」
- [x] 알려진 제한과 rollback — 아래 · rollback.md
- [x] 비밀값 스캔 — 참조형으로 실행, 이 slice 가 쓴 파일에서 매치 0

## 운영자 결정

| ID | 결정 | 출처 |
|---|---|---|
| **D-6E2B-1** | 수정 가능 HIGH/CRITICAL 67건의 **상향은 후속 slice 6E-2c**. 이 slice 는 등재로 두되 **CRITICAL 만료를 2026-10-31 로 단축**(HIGH 는 2026-12-31) | **사용자 2026-10-08** (팀장 경유, 계약 r1) |

**결정의 마감 장치는 문서가 아니라 게이트다** — 6E-2c 가 2026-10-31 을 넘기면 CRITICAL 10건이 만료로
붉어져 결정을 다시 요구한다.

## 알려진 제한

1. **서명 검증을 돌리지 않았다.** cosign 이 호스트·러너에 없어 trivy 자산의 sigstore 서명을 암호학적으로
   검증하지 못했다(인증서 문면 확인까지). 구속력 있는 잠금은 저장소에 핀한 체크섬이다
   (`OPEN-6E2B-TOOL-SIGNATURE-VERIFICATION`).
2. **게이트가 재는 것은 「방금 만든 로컬 이미지」다.** 배포본과 같은 바이트라는 보증(서명·다이제스트
   승격·레지스트리 push)은 없다.
3. **취약점 DB 를 실행마다 받는다.** 저장소를 건드리지 않은 PR 이 새 CVE 공시만으로 붉어질 수 있다 —
   게이트의 목적이지만 무관한 PR 이 치르는 비용이다. **그리고 상류가 `scan.db.max-age-days`(7일)보다
   오래 멈추면 모든 PR 이 exit 2 가 된다.** 그때 할 일은 값을 늘리는 것이 아니라 상류 상태를 보는
   것이다(runbook §8.2) — 값을 늘리는 것은 「오래된 DB 로 판정해도 좋다」는 결정이다.
4. **네트워크 의존이 셋이다** — 릴리스 자산 · 취약점 DB · Java DB. 접근이 없는 환경에서는 판정 불가로
   실패한다(위생 게이트의 베이스 다이제스트 축과 같은 성질).
5. **셸 게이트 정책 파서가 둘이다**(`image-hygiene-check.sh` 와 이 스크립트) —
   `OPEN-6C-POLICY-GATE-STRUCTURAL` 의 모집단이 1 에서 2 로 늘었다. 공유 라이브러리 추출은 검증된 6C
   게이트를 이 slice 범위 밖에서 건드리므로 하지 않았다.
6. **두 셸 게이트 모두 CI 에 자기 시험이 없다**(verifier r1 참고). 스크립트를 상시 초록으로 바꾸는 변이를
   막는 것은 리뷰뿐이다. 같은 OPEN 의 모집단이다.
7. **멀티아키를 보지 않는다**(`OPEN-6C-MULTIARCH`). 더해, `tool.trivy.asset` 가 `Linux-64bit` 고정이고
   아키 확인이 없다 — 러너가 arm64 가 되면 실패는 설치가 아니라 **첫 실행의 exec 오류**로 난다(cr L-3).
8. **오늘의 등재 67 은 전부 상향 대기다.** 하나도 「영향 없음」 판단이 아니고, 올리는 일은 6E-2c 가
   진다. 그때까지 tomcat-embed-core 의 CRITICAL 셋을 포함한 10건이 열린 채 돈다 — 이 slice 가 더하는
   것은 그 사실의 가시성과 기한이지 수정이 아니다.
9. **하한은 kind 당 수치 둘(입력·결과)이다.** 「통째로 못 읽음」은 두 축 다 잡지만 **일부 생태계만
   놓치는 경우**(예: jar 층만 비는 경우)는 여전히 못 잡는다. 양성 대조에서 실제로 보인 성질이다 —
   다른 이미지를 같은 kind 로 재니 나열 패키지가 84 로 하한 80 가까이 내려갔다.
9b. **결과 쪽 수치는 「나열한」 패키지이지 「DB 와 맞춰 본」 패키지가 아니다**(verifier 표적 M-1,
   `OPEN-6E2B-OS-MATCH-PREDICATE`). `--list-all-pkgs` 는 DB 매칭과 무관하게 목록을 싣는다. 그래서
   **「나열은 했는데 맞춰 보지 않았다」는 이 하한이 잡지 못한다** — SBOM 의 OS 판이 DB 와 어긋나면
   trivy 는 경고만 내고 finding 이 454 → 6 으로 주는데 나열 수는 115 그대로다(verifier 실측, 등재가
   비면 exit 0). **이 slice 의 목표 상태가 정확히 그 위험 구간이다**: 6E-2c 가 베이스를 올리는 순간이
   배포판이 바뀌는 순간이고 등재가 비는 순간이다. 계약 D-6E2B-5 ②의 문면(「전체 패키지 목록 출력에서
   계수」)대로 구현했으므로 이 라운드에서는 **주장을 거동에 맞춰 좁히고** 술어 추가는 OPEN 으로 둔다.
10. **CRITICAL ≤ 2026-10-31 계층이 데이터에만 있다**(verifier r1 L-1). allowlist 키에 severity 성분이
    없어, 새 CRITICAL 을 90일로 등재해도 게이트가 막지 못한다. **날짜 결정을 게이트 술어로 굳히지
    않는다**는 것이 D-6E2B-5 의 선택이다 — 결정 문면이 「오늘의 67건」에 한정되기 때문이다.
11. **severity 승격이 조용히 덮인다**(cr L-6, 10 과 같은 가족). HIGH 로 등재한 CVE 가 DB 갱신에서
    CRITICAL 로 올라가도 같은 등재가 계속 덮고, 짧은 쪽이 아니라 긴 쪽 만료를 탄다.
12. **요약에 「이 kind 의 등재 수」가 없다**(cr L-8). `allowlist_전체`(두 kind 합)와 `이_kind_적용` 만
    있어 「적용 = 이 kind 전부인가」를 요약만으로 읽을 수 없다(stale 0 에서 추론은 되지만 수치가 아니다).
13. **`sbom.format` 이 자유 문자열인데 파일 이름·artifact 이름은 `cdx`·`cyclonedx` 로 고정이다**(cr L-5).
14. **CWD 가 저장소 루트라는 술어가 없다**(cr L-4 잔여). 산출물 경로는 절대 경로로 굳혔지만, 저장소 밖에서
    부르면 `allowlist.file` 상대 경로가 먼저 exit 2 를 내는 **우연한 방어**에 기댄다.
15. **등재 검사가 O(n²)다**(cr L-10). 67건에서는 무해하고, 이 slice 의 계획은 등재를 0 으로 보내는
    방향이다 — 등재가 수백으로 늘면 보라는 표시다.
16. **`timeout-minutes` 는 `container` job 에만 있다**(cr L-9 부분). `check`·`ml-engine` 두 job 은 이
    slice 의 범위 밖이라 그대로다.
17. **S-22f 의 잔여 경우 하나** — 스캔이 SBOM 을 쓰기 **전에** exit 2 로 끊기면(정책·환경 오류) 업로드가
    「파일 없음」으로 한 번 더 붉어진다. 가짜가 아니라 **관련된** 빨강이라 그대로 뒀다(cr M-4 처방의 경계).

## 받은 OPEN

- `OPEN-6C-IMAGE-VULN-SCAN`(D-6C-5) — **해소**. 흉내가 아닌 스캔임을 재는 술어 여섯: ⓐ 판정이 JSON
  집합이고 텍스트 표가 아니다 ⓑ **입력 쪽** 하한(SBOM 구성요소) ⓒ **결과 쪽** 하한(분석한 패키지) ⓓ DB
  메타데이터 실재·신선도 ⓔ 면제 축이 allowlist 하나뿐임을 게이트가 세 겹으로 확인 ⓕ stale 등재가 비-0.
  **ⓑ 만 있던 r1 에서는 ⓒⓓⓔ 가 전부 열려 있었다.**

## 신설 OPEN

- `OPEN-6E2B-BASE-IMAGE-BUMP` — 베이스 다이제스트 상향. 오늘 차단 후보 67 중 **54**. **처분: 6E-2c**.
- `OPEN-6E2B-DEPENDENCY-BUMP` — JVM 의존 상향. 오늘 **13**(tomcat-embed-core CRITICAL 셋 포함). **처분: 6E-2c**.
- `OPEN-6E2B-GRADLE-DEPENDENCY-VERIFICATION` — Gradle 의존성 검증·lockfile 부재. 이 게이트는 「해결된
  그래프에 무엇이 있나」를 이미지에서 사후로 볼 뿐, **받아 온 바이트가 기대한 것인지**는 재지 않는다.
  2026-03 사고의 교훈이 겨냥하는 자리다.
- `OPEN-6E2B-TOOL-SIGNATURE-VERIFICATION` — 제한 1.
- **`OPEN-6E2B-OS-MATCH-PREDICATE`** — 결과 쪽에 **매칭 술어**가 없다(제한 9b). 후보 둘: ⓐ kind 별 기대
  OS family/version 을 정책이 선언하고 스캔의 `.Metadata.OS` 와 대조 ⓑ OS 클래스 Result 의 존재·`Type`
  확인. **6E-2c 가 베이스를 올리기 전에 처리하는 것이 맞다** — 그 상향이 바로 이 축을 건드린다.

## 장부 — 사실 등재

- **동결 뒤 `_workspace` 문서 편집**(verifier 표적 L-3). 표적 재검증이 도는 동안
  `_workspace/m6-6e2b/05_implementer_fix1.md` 가 디스크에서 바뀌었다. 팀장이 그 편집을 명시로 허용했고
  (`_workspace` 는 untracked 라 산출물이 아니다), 내용은 실측 이미지 ID 기록이다. **산출물·evidence 는
  동결 동안 무변경**이었다. 사실로만 적는다.
- **실측 이미지 ID 가 evidence 에 없었다** — 이 일괄에서 `commands.md` 에 넣었다(아래 하네스 절 참고).

## 하네스 레인 변경

- 없음(`git log --oneline 2deb5f9d..HEAD -- CLAUDE.md .claude/` 빈 출력).

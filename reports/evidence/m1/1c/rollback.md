# rollback — M1 / 1C (Qualification, 면허 자격 판정 커널)

정본. `scope.md` 의 `rollback` 절이 가리키는 자리(evidence-pack 규격). 경로 한정 복원 +
신규 경로 삭제. **range revert 금지** — in_scope 경로에만 적용한다.

base_sha: `4a6ca5c4ee5bb666fe786fbe395c5c00be775e75`
head_sha(이 문서 정본 시점): `50ca8e1`(+ 이 문서를 포함한 evidence 커밋 예정)

## 변경 파일 전건 (`git diff --name-status <base>..HEAD`, in_scope 경로만)

`M` = base 에도 있던 파일(내용만 복원), `A` = 이 slice 가 신설(파일째 삭제).

```
M  app/build.gradle.kts
M  app/src/test/kotlin/bidvector/app/conformance/CorpusExecutors.kt
M  app/src/test/kotlin/bidvector/app/conformance/SharedKernelCorpusConformanceTest.kt
M  config/quality/gate-tests.properties
M  docs/discovery/capability-map.md
M  docs/discovery/data-dictionary.md
M  qualification/build.gradle.kts
A  qualification/src/main/kotlin/bidvector/qualification/LicenseEligibility.kt
A  qualification/src/main/kotlin/bidvector/qualification/LicenseInputs.kt
A  qualification/src/main/kotlin/bidvector/qualification/LicensePolicy.kt
A  qualification/src/main/kotlin/bidvector/qualification/LicenseVerdict.kt
A  qualification/src/test/kotlin/bidvector/qualification/LicenseEligibilityPropertyTest.kt
A  qualification/src/test/kotlin/bidvector/qualification/LicenseEligibilityTest.kt
A  reports/evidence/m1/1c/checklist.md
A  reports/evidence/m1/1c/commands.md
A  reports/evidence/m1/1c/rollback.md
A  reports/evidence/m1/1c/scope.md
```

`config/quality/api-type-policy.properties`·`architecture-policy.properties`·
`fixtures/manifest.yaml`·`milestone-1.md` 는 scope.md 가 조건부로 열어 두었으나 **무접촉**
(사유: `String` 이 이미 게이트 허용, `String`/`Number` 축 갱신 불필요, fixture 편집은
fixture-curator 소관, milestone 갱신은 「기획 문서 레인」이라 이 레인이 손대지 않음 —
checklist.md 「알려진 제한」 ⑥ 참고).

## 복원 명령 (경로 개별 인자 — 변수 확장 금지)

```bash
BASE=4a6ca5c4ee5bb666fe786fbe395c5c00be775e75

git restore --source="$BASE" --staged --worktree -- \
  app/build.gradle.kts \
  app/src/test/kotlin/bidvector/app/conformance/CorpusExecutors.kt \
  app/src/test/kotlin/bidvector/app/conformance/SharedKernelCorpusConformanceTest.kt \
  config/quality/gate-tests.properties \
  docs/discovery/capability-map.md \
  docs/discovery/data-dictionary.md \
  qualification/build.gradle.kts

git rm -f \
  qualification/src/main/kotlin/bidvector/qualification/LicenseEligibility.kt \
  qualification/src/main/kotlin/bidvector/qualification/LicenseInputs.kt \
  qualification/src/main/kotlin/bidvector/qualification/LicensePolicy.kt \
  qualification/src/main/kotlin/bidvector/qualification/LicenseVerdict.kt \
  qualification/src/test/kotlin/bidvector/qualification/LicenseEligibilityPropertyTest.kt \
  qualification/src/test/kotlin/bidvector/qualification/LicenseEligibilityTest.kt \
  reports/evidence/m1/1c/checklist.md \
  reports/evidence/m1/1c/commands.md \
  reports/evidence/m1/1c/rollback.md \
  reports/evidence/m1/1c/scope.md
```

복원 뒤 `qualification` 모듈은 자리표시자로 돌아간다(base 의 `build.gradle.kts`만 —
`ModuleBoundaryAnchor.kt` 는 base 에도 있던 파일이라 위 목록에 없다, 그대로 남는다).

## 임시 clone 실측

`git clone --no-hardlinks` 로 별도 디렉터리에 이 저장소를 복제하고 위 두 명령을
그대로 실행 — `git restore`·`git rm` 둘 다 exit 0, 이후 `git status --short` 가
`config/quality/gate-tests.properties`·`docs/discovery/capability-map.md`·
`docs/discovery/data-dictionary.md`·`app/build.gradle.kts`·`qualification/build.gradle.kts`
5개 `M`(base 로 복원된 diff)과 나머지 삭제 항목의 `D` 만 보임을 확인했다(clone 은
검증 뒤 삭제, 원본 저장소 무변경).

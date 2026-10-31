import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';

import {
  listConstructionCapacities,
  removeConstructionCapacity,
  upsertConstructionCapacity,
  type ConstructionCapacity,
} from '../api/queries';
import { ErrorFace } from '../components/ScreenFaces';
import { isUnauthorized } from '../components/WriteFailure';
import { capacityBodyOf, type CapacityForm } from '../display/profileDisplay';

import { attemptOf, CAPACITY_QUERY_KEY, ProfileWriteFailure, useProfileWrite } from './profileWrites';

const EMPTY_CAPACITY: CapacityForm = { licenseName: '', amountWon: '', assessmentYear: '' };

/**
 * 시공능력평가액 — **행 단위**이고 기준 연도가 필수다(연 1회 갱신되는 값이다).
 *
 * 체크리스트 항목 `CONSTRUCTION_CAPACITY` 하나가 이 카드다. 그 항목은 나라장터 등록분야에 공사가
 * 있을 때만 걸리고, 분야를 아직 모르면 **선행 대기**다 — 그 판정은 서버가 한다(7C④-3).
 */
export function ProfileCapacityCard({ onSaved }: { readonly onSaved?: () => void }) {
  const [capacity, setCapacity] = useState<CapacityForm>(EMPTY_CAPACITY);
  const [missing, setMissing] = useState(false);

  const capacities = useQuery({ queryKey: CAPACITY_QUERY_KEY, queryFn: listConstructionCapacities });
  const save = useProfileWrite(upsertConstructionCapacity, CAPACITY_QUERY_KEY, () => {
    setCapacity(EMPTY_CAPACITY);
    onSaved?.();
  });
  const drop = useProfileWrite(removeConstructionCapacity, CAPACITY_QUERY_KEY, onSaved);

  return (
    <>
      <ProfileWriteFailure attempts={[attemptOf(save), attemptOf(drop)]} />
      <div className="rounded-md border border-line p-3">
        <span className="text-sm font-medium">시공능력평가액</span>
        {capacities.isError && !isUnauthorized(capacities.error) ? (
          <ErrorFace testId="capacity-error" onRetry={() => void capacities.refetch()} />
        ) : null}
        {capacities.isSuccess && capacities.data.length === 0 ? (
          <p data-testid="capacity-empty" className="mt-2 text-xs text-ink-soft">
            신고한 행이 없습니다.
          </p>
        ) : null}
        <ul className="mt-2 flex flex-col gap-2">
          {(capacities.data ?? []).map((row: ConstructionCapacity) => (
            <li
              key={row.licenseName}
              data-testid={`capacity-${row.licenseName}`}
              className="flex flex-wrap items-center gap-2 text-sm"
            >
              <span>{row.licenseName}</span>
              <span className="font-numeric">{row.amountWon.toLocaleString('ko-KR')}원</span>
              <span className="text-xs text-ink-soft">{row.assessmentYear}년 평가</span>
              <button
                type="button"
                onClick={() => drop.mutate({ licenseName: row.licenseName })}
                className="rounded-md border border-line px-2 py-0.5 text-xs hover:bg-neutral-skin"
              >
                지우기
              </button>
            </li>
          ))}
        </ul>
        <div className="mt-2 grid gap-2 sm:grid-cols-3">
          <input
            aria-label="시평액 면허명"
            value={capacity.licenseName}
            onChange={(event) => setCapacity({ ...capacity, licenseName: event.target.value })}
            className="rounded-md border border-line bg-card px-2 py-1 text-sm"
          />
          <input
            aria-label="시평액 금액"
            inputMode="numeric"
            value={capacity.amountWon}
            onChange={(event) => setCapacity({ ...capacity, amountWon: event.target.value })}
            className="rounded-md border border-line bg-card px-2 py-1 font-numeric text-sm"
          />
          <input
            aria-label="평가 연도"
            inputMode="numeric"
            value={capacity.assessmentYear}
            onChange={(event) => setCapacity({ ...capacity, assessmentYear: event.target.value })}
            className="rounded-md border border-line bg-card px-2 py-1 font-numeric text-sm"
          />
        </div>
        {missing ? (
          <p data-testid="capacity-missing" role="alert" className="mt-2 text-xs text-bad-ink">
            면허명과 자리 구분 없는 정수 금액·평가 연도가 필요합니다 — 쉼표·단위가 있으면 보내지
            않습니다(잘린 금액이 저장되지 않게).
          </p>
        ) : null}
        <button
          type="button"
          disabled={save.isPending}
          onClick={() => {
            // 셋 다 **문자열 전체가 모양에 맞아야** 보낸다 — 0·올해를 지어내지 않고, 앞자리만
            // 읽어 잘린 금액을 보내지도 않는다(CR M-1 — 여기는 확인 단계가 없어 더 조용하다).
            const body = capacityBodyOf(capacity);
            if (body === null) {
              setMissing(true);
              return;
            }
            setMissing(false);
            save.mutate(body);
          }}
          className="mt-2 rounded-md bg-accent px-3 py-1 text-sm font-medium text-accent-ink disabled:opacity-50"
        >
          시평액 저장
        </button>
      </div>
    </>
  );
}

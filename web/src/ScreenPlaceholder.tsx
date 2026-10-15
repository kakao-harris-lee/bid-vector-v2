import type { ScreenSlot } from './routes';

/** 빈 화면 자리. 7A 는 셸이 뜨는 것까지만 책임진다 — 내용은 7E. */
export function ScreenPlaceholder({ slot }: { slot: ScreenSlot }) {
  return (
    <section data-testid={`screen-${slot.id}`} className="p-6">
      <h2 className="text-lg font-semibold">{slot.label}</h2>
      <p className="mt-2 text-sm text-gray-600">화면 구현은 M7/7E 다. 7A 는 자리만 둔다.</p>
    </section>
  );
}

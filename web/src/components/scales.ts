/**
 * Axis ticks at round values: 0, 50 000, 100 000... rather than wherever max / 4 happens to land.
 * The top tick is the first round value at or above the maximum, so the line never touches the
 * frame.
 */
export function niceTicks(max: number, targetCount = 4): number[] {
  if (!(max > 0)) {
    return [0, 1];
  }
  const magnitude = 10 ** Math.floor(Math.log10(max / targetCount));
  // The finest round step that needs at most one interval more than asked for: the top of the
  // axis then stays close to the data instead of leaving a third of the chart empty.
  const step =
    [1, 2, 2.5, 5, 10]
      .map((factor) => factor * magnitude)
      .find((candidate) => Math.ceil(max / candidate) <= targetCount + 1) ?? 10 * magnitude;
  const count = Math.ceil(max / step);
  return Array.from({ length: count + 1 }, (_, index) => index * step);
}

const MONTH_STEPS = [1, 2, 3, 6, 12, 24, 60, 120];

/**
 * Month boundaries between two instants, spaced so that no more than `maxTicks` appear: monthly for
 * a quarter, quarterly for a couple of years, yearly beyond.
 */
export function timeTicks(start: number, end: number, maxTicks = 6): number[] {
  const from = new Date(start);
  const to = new Date(end);
  const months = (to.getFullYear() - from.getFullYear()) * 12 + (to.getMonth() - from.getMonth());
  const step = MONTH_STEPS.find((candidate) => months / candidate < maxTicks) ?? 120;

  const ticks: number[] = [];
  const cursor = new Date(from.getFullYear(), from.getMonth() + 1, 1);
  // Align on multiples of the step so labels read as "janv., avr., juil." rather than odd months.
  while (cursor.getMonth() % Math.min(step, 12) !== 0 && step > 1) {
    cursor.setMonth(cursor.getMonth() + 1);
  }
  while (cursor.getTime() <= end) {
    ticks.push(cursor.getTime());
    cursor.setMonth(cursor.getMonth() + step);
  }
  return ticks;
}

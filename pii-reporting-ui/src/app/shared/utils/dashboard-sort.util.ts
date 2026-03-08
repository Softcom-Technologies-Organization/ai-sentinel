export interface SortableItem {
  originalIndex?: number;
  counts?: { high?: number; medium?: number; low?: number };
}

export function sortByFieldAndOrder<T extends SortableItem>(
  items: T[],
  field: string | null,
  order: number
): T[] {
  if (!field) return items;

  return [...items].sort((a, b) => {
    let compareValue = 0;

    if (field === 'name') {
      compareValue = (a.originalIndex ?? 0) - (b.originalIndex ?? 0);
    } else if (field === 'piiCount') {
      const priorities = ['high', 'medium', 'low'] as const;
      for (const priority of priorities) {
        const countA = a.counts?.[priority] ?? 0;
        const countB = b.counts?.[priority] ?? 0;
        if (countA !== countB) {
          compareValue = countB - countA;
          break;
        }
      }
    }

    return compareValue * order;
  });
}

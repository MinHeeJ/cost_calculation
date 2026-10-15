export function processorName(value: string | null | undefined): string {
  return value == null ? "기본 사용자" : value;
}

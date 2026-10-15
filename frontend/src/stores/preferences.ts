import { create } from "zustand";
import type { PageSize } from "../components/Pagination";

export type ScreenPreference = { screenId: string; name: string };
export const preferenceKeys = {
  favorites: "si.favorites",
  recentScreens: "si.recentScreens",
  menuCollapsed: "si.menuCollapsed",
  pageSize: "si.pageSize",
} as const;
const memory = new Map<string, unknown>();
export function readPreference<T>(
  key: keyof typeof preferenceKeys,
  fallback: T,
): T {
  const full = preferenceKeys[key];
  try {
    const raw = localStorage.getItem(full);
    if (raw != null) return JSON.parse(raw) as T;
  } catch {
    /* Storage denial/corruption is non-fatal. */
  }
  return (memory.get(full) as T | undefined) ?? fallback;
}
export function writePreference<T>(
  key: keyof typeof preferenceKeys,
  value: T,
): void {
  const full = preferenceKeys[key];
  memory.set(full, value);
  try {
    localStorage.setItem(full, JSON.stringify(value));
  } catch {
    /* Keep the memory copy. */
  }
}
function initialSize(): PageSize {
  const value = readPreference<number>("pageSize", 20);
  return value === 20 || value === 50 || value === 100 ? value : 20;
}
interface Preferences {
  pageSize: PageSize;
  menuCollapsed: boolean;
  setPageSize: (value: PageSize) => void;
  setMenuCollapsed: (value: boolean) => void;
}
const savedCollapse = readPreference<unknown>("menuCollapsed", null);
export const usePreferences = create<Preferences>((set) => ({
  pageSize: initialSize(),
  menuCollapsed: typeof savedCollapse === "boolean" ? savedCollapse : false,
  setPageSize: (value) => {
    if (![20, 50, 100].includes(value)) return;
    writePreference("pageSize", value);
    set({ pageSize: value });
  },
  setMenuCollapsed: (value) => {
    writePreference("menuCollapsed", value);
    set({ menuCollapsed: value });
  },
}));

import { create } from "zustand";
import { readPreference, writePreference } from "../../stores/preferences";
import { screenById } from "./registry";

function restore(key: "favorites" | "recentScreens", limit: number): string[] {
  const saved = readPreference<unknown>(key, []);
  if (!Array.isArray(saved)) return [];
  const ids = saved.map((item) =>
    typeof item === "string" ? item : item?.screenId,
  );
  return [
    ...new Set(
      ids.filter(
        (id): id is string => typeof id === "string" && !!screenById(id),
      ),
    ),
  ].slice(0, limit);
}
interface Shortcuts {
  favorites: string[];
  recent: string[];
  toggleFavorite: (id: string) => boolean;
  reorderFavorite: (from: string, to: string) => void;
  visit: (id: string) => void;
}
export const useShortcuts = create<Shortcuts>((set, get) => ({
  favorites: restore("favorites", 10),
  recent: restore("recentScreens", 5),
  toggleFavorite: (id) => {
    if (!screenById(id)) return false;
    const previous = get().favorites;
    if (!previous.includes(id) && previous.length >= 10) return false;
    const favorites = previous.includes(id)
      ? previous.filter((value) => value !== id)
      : [...previous, id];
    writePreference("favorites", favorites);
    set({ favorites });
    return true;
  },
  reorderFavorite: (from, to) => {
    const favorites = [...get().favorites];
    if (!favorites.includes(from) || !favorites.includes(to) || from === to)
      return;
    const targetIndex = favorites.indexOf(to);
    favorites.splice(favorites.indexOf(from), 1);
    favorites.splice(targetIndex, 0, from);
    writePreference("favorites", favorites);
    set({ favorites });
  },
  visit: (id) => {
    if (!screenById(id)) return;
    const recent = [id, ...get().recent.filter((value) => value !== id)].slice(
      0,
      5,
    );
    writePreference("recentScreens", recent);
    set({ recent });
  },
}));

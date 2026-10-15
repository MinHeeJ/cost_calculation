import { create } from "zustand";
import { screenById } from "./registry";

export type Confirmation = { kind: "limit" | "dirty"; screenId: string };
export type ConfirmNavigation = (question: Confirmation) => Promise<boolean>;
interface Navigation {
  active: string;
  tabs: string[];
  visits: string[];
  dirty: Record<string, boolean>;
  screenState: Record<string, unknown>;
  busy: boolean;
  setDirty: (id: string, dirty: boolean) => void;
  setScreenState: (id: string, value: unknown) => void;
  open: (id: string, confirm: ConfirmNavigation) => Promise<boolean>;
  close: (id: string, confirm: ConfirmNavigation) => Promise<boolean>;
}

/** Confirmations do not mutate route, recency, tabs or input until every answer is affirmative. */
export const useNavigation = create<Navigation>((set, get) => {
  async function transition(
    id: string,
    confirm: ConfirmNavigation,
    closing: boolean,
  ): Promise<boolean> {
    const before = get();
    if (
      before.busy ||
      !screenById(id) ||
      (closing && (id === "UI-16" || !before.tabs.includes(id)))
    )
      return false;
    set({ busy: true });
    try {
      const evict =
        !closing && !before.tabs.includes(id) && before.tabs.length >= 8
          ? before.visits.find((value) => value !== "UI-16")
          : undefined;
      if (evict && !(await confirm({ kind: "limit", screenId: evict })))
        return false;
      const target = closing
        ? id === before.active
          ? ([...before.visits].reverse().find((value) => value !== id) ??
            "UI-16")
          : before.active
        : id;
      const leaving = new Set([
        ...(target !== before.active ? [before.active] : []),
        ...(closing ? [id] : []),
        ...(evict ? [evict] : []),
      ]);
      for (const screenId of leaving) {
        if (
          before.dirty[screenId] &&
          !(await confirm({ kind: "dirty", screenId }))
        )
          return false;
      }
      const removed = new Set([
        ...(closing ? [id] : []),
        ...(evict ? [evict] : []),
      ]);
      const tabs = before.tabs.filter((value) => !removed.has(value));
      if (!tabs.includes(target)) tabs.push(target);
      const dirty = { ...before.dirty };
      const screenState = { ...before.screenState };
      for (const screenId of leaving) {
        if (dirty[screenId] || removed.has(screenId))
          delete screenState[screenId];
        delete dirty[screenId];
      }
      set({
        active: target,
        tabs,
        dirty,
        screenState,
        visits: [
          ...before.visits.filter(
            (value) => value !== target && !removed.has(value),
          ),
          target,
        ],
      });
      return true;
    } finally {
      set({ busy: false });
    }
  }
  return {
    active: "UI-16",
    tabs: ["UI-16"],
    visits: ["UI-16"],
    dirty: {},
    screenState: {},
    busy: false,
    setDirty: (id, dirty) =>
      set((state) => ({ dirty: { ...state.dirty, [id]: dirty } })),
    setScreenState: (id, value) =>
      set((state) => ({ screenState: { ...state.screenState, [id]: value } })),
    open: (id, confirm) => transition(id, confirm, false),
    close: (id, confirm) => transition(id, confirm, true),
  };
});

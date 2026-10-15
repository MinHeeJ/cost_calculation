import { useEffect, useState } from "react";
import { useRequest } from "../shared/useRequest";
import { getCodes, type CodeChoice } from "./api";

const subscribers = new Map<string, Set<() => void>>();

/** Call after a successful code save, not after its follow-up GET. Only this group reloads. */
export function invalidateCodes(group: string) {
  subscribers.get(group)?.forEach((refresh) => refresh());
}

/** All new code selections share this read/invalidation path; stored reference names stay separate. */
export function useCodes(group: string | null | undefined) {
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    if (!group) return;
    const refresh = () => setRevision((value) => value + 1);
    const listeners = subscribers.get(group) ?? new Set<() => void>();
    listeners.add(refresh);
    subscribers.set(group, listeners);
    return () => {
      listeners.delete(refresh);
      if (!listeners.size) subscribers.delete(group);
    };
  }, [group]);
  const state = useRequest<CodeChoice[]>(
    () => (group ? getCodes(group) : Promise.resolve([])),
    JSON.stringify([group, revision]),
  );
  return { ...state, codes: state.data ?? [] };
}

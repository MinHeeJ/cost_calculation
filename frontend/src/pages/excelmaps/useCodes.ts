import { useEffect, useState } from "react";
import { getCodeChoices, type CodeChoice } from "./api";

const subscribers = new Map<string, Set<() => void>>();

/** 코드 저장 후 같은 그룹의 신규 선택 목록 소비자만 재조회한다. */
export function invalidateCodes(group: string) {
  subscribers.get(group)?.forEach((reload) => reload());
}

/** 신규 선택은 공개 Y 코드 API만 사용하고 그룹 변경의 오래된 응답은 버린다. */
export function useCodes(group: string) {
  const [version, setVersion] = useState(0);
  const [state, setState] = useState<{
    group: string;
    data: CodeChoice[];
    loading: boolean;
    error?: Error;
  }>({ group, data: [], loading: !!group });
  useEffect(() => {
    const reload = () => setVersion((value) => value + 1);
    const listeners = subscribers.get(group) ?? new Set();
    subscribers.set(group, listeners);
    listeners.add(reload);
    return () => {
      listeners.delete(reload);
      if (!listeners.size) subscribers.delete(group);
    };
  }, [group]);
  useEffect(() => {
    let active = true;
    setState({ group, data: [], loading: !!group });
    if (group) {
      getCodeChoices(group).then(
        (data) => {
          if (active) setState({ group, data, loading: false });
        },
        (error: Error) => {
          if (active) setState({ group, data: [], error, loading: false });
        },
      );
    }
    return () => {
      active = false;
    };
  }, [group, version]);
  return {
    ...(state.group === group ? state : { group, data: [], loading: !!group }),
    retry: () => setVersion((value) => value + 1),
  };
}

import { useCallback, useEffect, useRef, useState } from "react";
import { RequestError } from "../../api/client";
import { commonCodeApi } from "./commonCode";

export function useRead<T>(load: () => Promise<T>, key: string) {
  const loader = useRef(load);
  loader.current = load;
  const sequence = useRef(0);
  const [data, setData] = useState<T>();
  const [error, setError] = useState<RequestError | null>(null);
  const [loading, setLoading] = useState(false);
  const retry = useCallback(async () => {
    const ticket = ++sequence.current;
    setLoading(true);
    try {
      const result = await loader.current();
      if (ticket === sequence.current) {
        setData(result);
        setError(null);
      }
      return result;
    } catch (failure) {
      if (ticket === sequence.current) setError(failure as RequestError);
      return undefined;
    } finally {
      if (ticket === sequence.current) setLoading(false);
    }
  }, []);
  useEffect(() => {
    setData(undefined);
    setError(null);
    if (key) void retry();
    return () => {
      sequence.current++;
    };
  }, [key, retry]);
  return { data, error, loading, retry };
}

export function invalidateCodes(group: string) {
  window.dispatchEvent(
    new CustomEvent("common-code-change", { detail: group }),
  );
}
export function useCodes(group: string) {
  const result = useRead(() => commonCodeApi.choices(group), group);
  useEffect(() => {
    const refresh = (event: Event) => {
      if ((event as CustomEvent<string>).detail === group) void result.retry();
    };
    window.addEventListener("common-code-change", refresh);
    return () => window.removeEventListener("common-code-change", refresh);
  }, [group, result.retry]);
  return result;
}

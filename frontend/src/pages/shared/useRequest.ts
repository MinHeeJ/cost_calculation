import { useCallback, useEffect, useRef, useState } from "react";
import { RequestError } from "../../api/client";

/** Manual retry only; stale completions cannot replace data for a newer period. */
export function useRequest<T>(load: () => Promise<T>, key: string) {
  const loader = useRef(load);
  loader.current = load;
  const current = useRef(0);
  const running = useRef(false);
  const [state, setState] = useState<{
    data?: T;
    error?: RequestError;
    loading: boolean;
  }>({ loading: true });
  const retry = useCallback(async () => {
    if (running.current) return;
    running.current = true;
    const generation = ++current.current;
    setState((previous) => ({ ...previous, loading: true }));
    try {
      const data = await loader.current();
      if (generation === current.current) setState({ data, loading: false });
    } catch (error) {
      if (generation === current.current)
        setState((previous) => ({
          ...previous,
          loading: false,
          error:
            error instanceof RequestError
              ? error
              : new RequestError(
                  "응답을 확인할 수 없습니다.",
                  crypto.randomUUID(),
                  "client",
                  null,
                ),
        }));
    } finally {
      if (generation === current.current) running.current = false;
    }
  }, []);
  useEffect(() => {
    running.current = false;
    setState({ loading: true });
    void retry();
    return () => {
      ++current.current;
      running.current = false;
    };
  }, [key, retry]);
  return { ...state, retry };
}

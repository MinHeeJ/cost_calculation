import { useId } from "react";
import { ChevronLeft, ChevronRight, LockKeyhole } from "lucide-react";
import { Button } from "../../components/Button";
import { Badge } from "../../components/Badge";
import { LockBanner } from "../../components/LockBanner";
import { RequestState } from "../../components/RequestState";
import { getMonthClose } from "./api";
import { monthKey, seoulToday, shiftPeriod, usePeriods } from "./periods";
import { useRequest } from "./useRequest";
import type { ScreenDefinition } from "./registry";
import styles from "./Shared.module.css";

function MonthStatus({ month, errorId }: { month: string; errorId: string }) {
  const result = useRequest(() => getMonthClose(month, month), month);
  const status = result.data?.find((value) => value.month === month)?.status;
  const fieldError =
    result.error?.status === 400 &&
    ["from", "to"].includes(result.error.field ?? "")
      ? result.error.message
      : undefined;
  return (
    <>
      <div id={errorId}>{fieldError && <p>{fieldError}</p>}</div>
      <RequestState
        loading={result.loading}
        error={result.error}
        onRetry={result.retry}
        empty={!result.loading && !result.error && !status}
        emptyReason="선택한 월의 마감 상태가 없습니다."
      >
        <div className={styles.monthFeedback}>
          {status && (
            <Badge tone={status === "CLOSED" ? "locked" : "info"}>
              {status === "CLOSED" && (
                <LockKeyhole size={16} aria-hidden="true" />
              )}
              {status === "CLOSED" ? "마감" : "작업중"}
            </Badge>
          )}
          {status === "CLOSED" && <LockBanner month={month} />}
        </div>
      </RequestState>
    </>
  );
}

export function PeriodBar({ screen }: { screen: ScreenDefinition }) {
  const errorId = useId();
  const saved = usePeriods((state) => state.values[screen.screenId]);
  const setPeriod = usePeriods((state) => state.setPeriod);
  const period = saved ?? seoulToday();
  if (screen.periodType === "NONE") return null;
  const monthly = screen.periodType === "MONTH";
  const change = (delta: number) =>
    setPeriod(
      screen.screenId,
      shiftPeriod(period, monthly ? delta : delta * 12),
    );
  return (
    <section className={styles.page} aria-label="조회 기간">
      <div className={styles.period}>
        <Button
          aria-label={monthly ? "이전 월" : "이전 연도"}
          disabled={period.year === 0 && (!monthly || period.month === 1)}
          onClick={() => change(-1)}
        >
          <ChevronLeft size={16} />
        </Button>
        {monthly ? (
          <input
            type="month"
            aria-label="선택 연월"
            aria-describedby={errorId}
            value={monthKey(period)}
            min="0000-01"
            max="9999-12"
            onChange={(event) => {
              const value = event.target.value;
              if (/^[0-9]{4}-(0[1-9]|1[0-2])$/.test(value)) {
                setPeriod(screen.screenId, {
                  year: Number(value.slice(0, 4)),
                  month: Number(value.slice(5)),
                });
              }
            }}
          />
        ) : (
          <label>
            연도{" "}
            <input
              type="number"
              aria-label="선택 연도"
              value={period.year}
              min={0}
              max={9999}
              onChange={(event) => {
                if (/^[0-9]{1,4}$/.test(event.target.value)) {
                  setPeriod(screen.screenId, {
                    ...period,
                    year: Number(event.target.value),
                  });
                }
              }}
            />
            년
          </label>
        )}
        <Button
          aria-label={monthly ? "다음 월" : "다음 연도"}
          disabled={period.year === 9999 && (!monthly || period.month === 12)}
          onClick={() => change(1)}
        >
          <ChevronRight size={16} />
        </Button>
        {screen.periodType === "YEAR_CUM" && (
          <>
            <label>
              종료월{" "}
              <select
                aria-label="누계 종료월"
                value={period.month}
                onChange={(event) =>
                  setPeriod(screen.screenId, {
                    ...period,
                    month: Number(event.target.value),
                  })
                }
              >
                {Array.from({ length: 12 }, (_, i) => i + 1).map((month) => (
                  <option key={month} value={month}>
                    {month}월
                  </option>
                ))}
              </select>
            </label>
            <span>
              {period.year}년 1월 ~ {period.month}월 누계
            </span>
          </>
        )}
      </div>
      {monthly && <MonthStatus month={monthKey(period)} errorId={errorId} />}
    </section>
  );
}

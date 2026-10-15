import { useEffect, useState } from "react";
import { Button } from "../../components/Button";
import { WorkDialog } from "../../components/WorkDialog";
import { Table, type TableColumn } from "../../components/Table";
import { RequestState } from "../../components/RequestState";
import { ErrorBanner } from "../../components/ErrorBanner";
import { MonthInput } from "../../components/MonthInput";
import { TextInput } from "../../components/TextInput";
import { RequestError } from "../../api/client";
import { Can } from "../../permissions/Can";
import { useRequest } from "../shared/useRequest";
import { useNavigation } from "../shared/navigation";
import {
  getOperatingStandards,
  getOperatingStandardHistory,
  saveOperatingStandard,
  type OperatingStandard,
} from "./api";
import styles from "./OperatingStandards.module.css";

function shown(row: OperatingStandard) {
  return `${row.num_val ?? row.txt_val ?? ""}${row.unit_nm ? ` ${row.unit_nm}` : ""}`;
}
function nextMonth(month: string | null) {
  if (!month) return undefined;
  const [year, number] = month.split("-").map(Number);
  if (year === 9999 && number === 12) return undefined;
  return (
    `${number === 12 ? year + 1 : year}`.padStart(4, "0") +
    `-${String(number === 12 ? 1 : number + 1).padStart(2, "0")}`
  );
}

/** UI-27의 기준값 탭 본문. 공통 shell/기간/모바일 안내는 등록 소유자가 유지한다. */
export function OperatingStandardsPanel() {
  const result = useRequest(getOperatingStandards, "operating-standards");
  const [editing, setEditing] = useState<OperatingStandard>();
  const [history, setHistory] = useState<OperatingStandard>();
  const [success, setSuccess] = useState("");
  const columns: TableColumn<OperatingStandard>[] = [
    {
      key: "key",
      label: "기준값 키",
      kind: "code",
      render: (row) => row.oprt_std_id,
    },
    { key: "name", label: "항목명", render: (row) => row.oprt_std_nm },
    { key: "value", label: "현재 값", render: shown },
    {
      key: "month",
      label: "적용 시작월",
      kind: "date",
      render: (row) => row.aply_bgn_ym,
    },
    {
      key: "usage",
      label: "쓰는 곳",
      render: (row) => row.use_plc_desc ?? "—",
    },
    {
      key: "actions",
      label: "작업",
      render: (row) => (
        <div className={styles.actions}>
          <Can perm="UI-27.update">
            <Button
              size="small"
              data-testid={`standard-edit-${row.oprt_std_id}`}
              onClick={() => setEditing(row)}
            >
              값 변경
            </Button>
          </Can>
          <Can perm="UI-27.read">
            <Button
              size="small"
              data-testid={`standard-history-${row.oprt_std_id}`}
              onClick={() => setHistory(row)}
            >
              이력 보기
            </Button>
          </Can>
        </div>
      ),
    },
  ];
  return (
    <section
      className={styles.panel}
      data-testid="operating-standards-panel"
      aria-label="기준값"
    >
      <h2>기준값</h2>
      {success && (
        <p role="status" className={styles.success}>
          {success}
        </p>
      )}
      <RequestState
        loading={result.loading}
        error={result.error}
        empty={result.data?.items.length === 0}
        emptyReason="조건에 맞는 결과가 없습니다"
        onRetry={result.retry}
      >
        <Table
          columns={columns}
          rows={result.data?.items ?? []}
          rowKey={(row) => row.oprt_std_id}
          total={result.data?.items.length ?? 0}
        />
      </RequestState>
      {editing && (
        <OperatingStandardEditDialog
          row={editing}
          lastClosed={result.data?.last_closed_ym ?? null}
          onClose={() => setEditing(undefined)}
          onSaved={async () => {
            setEditing(undefined);
            setSuccess("기준값을 저장했습니다.");
            await result.retry();
          }}
        />
      )}
      {history && (
        <OperatingStandardHistoryDialog
          row={history}
          onClose={() => setHistory(undefined)}
        />
      )}
    </section>
  );
}

export function OperatingStandardEditDialog({
  row,
  lastClosed,
  onClose,
  onSaved,
}: {
  row: OperatingStandard;
  lastClosed: string | null;
  onClose: () => void;
  onSaved: () => Promise<void>;
}) {
  const [value, setValue] = useState("");
  const [month, setMonth] = useState("");
  const [error, setError] = useState<RequestError>();
  const [fieldError, setFieldError] = useState("");
  const [busy, setBusy] = useState(false);
  const numeric = row.num_val !== null;
  const field = numeric ? "num_val" : "txt_val";
  const setDirty = useNavigation((state) => state.setDirty);
  useEffect(() => {
    setDirty("UI-27", value !== "" || month !== "");
    return () => setDirty("UI-27", false);
  }, [value, month, setDirty]);
  async function save() {
    if (busy) return;
    setFieldError("");
    if (
      !/^[0-9]{4}-(0[1-9]|1[0-2])$/.test(month) ||
      (lastClosed !== null && month <= lastClosed)
    ) {
      setFieldError("마감월 다음 달부터 올바른 적용 시작월을 입력하세요.");
      return;
    }
    const number = Number(value);
    if (
      value.trim() === "" ||
      (!numeric && value.length > 200) ||
      (numeric &&
        (!/^(0|[1-9][0-9]*)(\.[0-9]+)?$/.test(value.trim()) ||
          !Number.isFinite(number) ||
          number < 0 ||
          ((row.unit_nm === "%" || row.unit_nm === "%p") && number > 100)))
    ) {
      setFieldError("올바른 값을 입력하세요.");
      return;
    }
    setBusy(true);
    setError(undefined);
    try {
      await saveOperatingStandard(
        row.oprt_std_id,
        numeric
          ? { aply_bgn_ym: month, num_val: value.trim() }
          : { aply_bgn_ym: month, txt_val: value },
      );
    } catch (failure) {
      setError(failure as RequestError);
      setBusy(false);
      return;
    }
    setDirty("UI-27", false);
    // 커밋 이후 재조회 실패는 부모의 GET 재시도만 제공한다.
    await onSaved();
  }
  return (
    <WorkDialog
      open
      title={`기준값 변경: ${row.oprt_std_nm}`}
      onClose={() => {
        if (!busy) onClose();
      }}
      actions={
        <Button
          variant="primary"
          busy={busy}
          onClick={save}
          data-testid="standard-save"
        >
          저장
        </Button>
      }
    >
      <div className={styles.form} data-testid="standard-edit-dialog">
        <div className={styles.metadata}>
          <p>기준값 키: {row.oprt_std_id}</p>
          <p>현재 값: {shown(row)}</p>
          <p>쓰는 곳: {row.use_plc_desc ?? "—"}</p>
        </div>
        {error && <ErrorBanner error={error} onRetry={save} />}
        <div className={styles.field}>
          <label htmlFor="standard-new-value">새 값</label>
          <TextInput
            id="standard-new-value"
            data-testid="standard-new-value"
            value={value}
            disabled={busy}
            maxLength={numeric ? undefined : 200}
            aria-describedby="standard-value-error"
            onChange={(event) => setValue(event.target.value)}
          />
        </div>
        <p id="standard-value-error" className={styles.error}>
          {error?.field === field ? error.message : ""}
        </p>
        <div className={styles.field}>
          <label htmlFor="standard-start-month">적용 시작월</label>
          <MonthInput
            id="standard-start-month"
            data-testid="standard-start-month"
            min={nextMonth(lastClosed)}
            max="9999-12"
            value={month}
            disabled={busy}
            aria-describedby="standard-month-error"
            onChange={(event) => setMonth(event.target.value)}
          />
        </div>
        <p>마지막 마감월: {lastClosed ?? "없음"} · 마감월 다음 달부터 선택</p>
        <p id="standard-month-error" className={styles.error}>
          {error?.field === "aply_bgn_ym" ? error.message : ""}
        </p>
        {fieldError && (
          <p role="alert" className={styles.error}>
            {fieldError}
          </p>
        )}
      </div>
    </WorkDialog>
  );
}

export function OperatingStandardHistoryDialog({
  row,
  onClose,
}: {
  row: OperatingStandard;
  onClose: () => void;
}) {
  const history = useRequest(
    () => getOperatingStandardHistory(row.oprt_std_id),
    row.oprt_std_id,
  );
  return (
    <WorkDialog
      open
      title={`기준값 전체 이력: ${row.oprt_std_nm}`}
      onClose={onClose}
      cancelLabel="닫기"
    >
      <div data-testid="standard-history-dialog">
        <RequestState
          loading={history.loading}
          error={history.error}
          empty={history.data?.length === 0}
          emptyReason="조건에 맞는 결과가 없습니다"
          onRetry={history.retry}
        >
          <Table
            rows={history.data ?? []}
            rowKey={(item) => item.aply_bgn_ym}
            total={history.data?.length ?? 0}
            columns={[
              {
                key: "month",
                label: "적용 시작월",
                render: (item) => item.aply_bgn_ym,
              },
              { key: "value", label: "값·단위", render: shown },
            ]}
          />
        </RequestState>
      </div>
    </WorkDialog>
  );
}

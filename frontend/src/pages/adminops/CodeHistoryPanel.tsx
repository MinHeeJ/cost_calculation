import { useState } from "react";
import { Can } from "../../permissions/Can";
import { SelectInput } from "../../components/SelectInput";
import { Pagination, type PageSize } from "../../components/Pagination";
import { RequestState } from "../../components/RequestState";
import { Table, type TableColumn } from "../../components/Table";
import { formatDateTime } from "../../lib/format";
import { processorName } from "../../lib/processorName";
import { useRequest } from "../shared/useRequest";
import { getCodeHistory, getHistoryGroups, type CodeHistoryView } from "./api";
import styles from "./CodeHistoryPanel.module.css";

const changeNames = {
  ADD: "추가",
  UPDATE: "수정",
  DISABLE: "사용 중지",
  ENABLE: "다시 사용",
};
const columns: TableColumn<CodeHistoryView>[] = [
  {
    key: "chg_at",
    label: "변경 일시",
    kind: "date",
    render: (row) => formatDateTime(row.chg_at),
  },
  { key: "cd_grp_nm", label: "그룹", render: (row) => row.cd_grp_nm },
  {
    key: "dtl_cd",
    label: "코드",
    kind: "code",
    render: (row) => row.dtl_cd ?? "–",
  },
  {
    key: "cd_chg_ty_cd",
    label: "구분",
    render: (row) => changeNames[row.cd_chg_ty_cd],
  },
  { key: "item_nm", label: "항목", render: (row) => row.item_nm ?? "–" },
  { key: "bfr_val", label: "이전 값", render: (row) => row.bfr_val ?? "–" },
  { key: "aft_val", label: "이후 값", render: (row) => row.aft_val ?? "–" },
  {
    key: "chg_user_id",
    label: "변경자",
    render: (row) =>
      processorName(row.chg_user_id == null ? null : String(row.chg_user_id)),
  },
];

/** Read-only body for the existing UI-27 history tab; shell and dirty transitions stay with its owner. */
export function CodeHistoryPanel() {
  const [group, setGroup] = useState("");
  const [page, setPage] = useState(1);
  const [size, setSize] = useState<PageSize>(50);
  const groups = useRequest(getHistoryGroups, "code-history-groups");
  const history = useRequest(
    () => getCodeHistory(group, page, size),
    `${group}:${page}:${size}`,
  );
  return (
    <Can perm="UI-27.read">
      <section
        className={styles.panel}
        aria-label="변경 이력"
        data-testid="code-history-panel"
      >
        <h2 className={styles.heading}>변경 이력</h2>
        <div className={styles.toolbar}>
          <label htmlFor="code-history-group">그룹</label>
          <SelectInput
            id="code-history-group"
            data-testid="code-history-group"
            value={group}
            onChange={(event) => {
              setGroup(event.target.value);
              setPage(1);
            }}
          >
            <option value="">전체</option>
            {groups.data
              ?.filter((item) => item.cd_grp_id !== "PARAM")
              .map((item) => (
                <option key={item.cd_grp_id} value={item.cd_grp_id}>
                  {item.cd_grp_nm}
                </option>
              ))}
            <option value="PARAM">기준값</option>
          </SelectInput>
        </div>
        <RequestState
          loading={groups.loading}
          error={groups.error}
          onRetry={groups.retry}
        />
        <RequestState
          loading={history.loading}
          error={history.error}
          empty={history.data?.items.length === 0}
          emptyReason="조건에 맞는 결과가 없습니다"
          onRetry={history.retry}
        >
          {history.data && (
            <div className={styles.results}>
              <Table
                columns={columns}
                rows={history.data.items}
                rowKey={(row) => row.cd_hist_id}
                total={history.data.total}
                caption="공통코드 변경 이력 (읽기 전용)"
              />
            </div>
          )}
        </RequestState>
        {history.data && !history.loading && !history.error && (
          <div className={styles.results}>
            <Pagination
              total={history.data.total}
              page={history.data.page}
              size={history.data.size}
              onPageChange={setPage}
              onSizeChange={setSize}
            />
          </div>
        )}
      </section>
    </Can>
  );
}

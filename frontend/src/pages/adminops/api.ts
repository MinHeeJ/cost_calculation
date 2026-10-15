import { request } from "../../api/client";
import type { PageSize } from "../../components/Pagination";

export interface CodeHistoryView {
  cd_hist_id: number;
  cd_grp_id: string;
  dtl_cd: string | null;
  cd_chg_ty_cd: "ADD" | "UPDATE" | "DISABLE" | "ENABLE";
  item_nm: string | null;
  bfr_val: string | null;
  aft_val: string | null;
  chg_user_id: number | null;
  chg_at: string;
  cd_grp_nm: string;
}

export interface CodeHistoryPage {
  items: CodeHistoryView[];
  total: number;
  page: number;
  size: PageSize;
}

export interface HistoryGroupChoice {
  cd_grp_id: string;
  cd_grp_nm: string;
}

/** The sibling group operation supplies filter options; no local fixture list is used. */
export function getHistoryGroups(): Promise<HistoryGroupChoice[]> {
  return request<HistoryGroupChoice[]>("/api/admin/code-groups");
}

export function getCodeHistory(
  group = "",
  page = 1,
  size: PageSize = 50,
): Promise<CodeHistoryPage> {
  const query = new URLSearchParams({
    page: String(page),
    size: String(size),
    sort: "chg_at,desc",
  });
  if (group) query.set("cd_grp_id", group);
  return request<CodeHistoryPage>(`/api/admin/code-history?${query}`);
}

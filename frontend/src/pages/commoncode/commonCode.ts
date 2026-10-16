import { request } from "../../api/client";

export interface CodeGroup {
  cd_grp_id: string;
  cd_grp_nm: string;
  cd_grp_se_cd: "SYSTEM" | "BIZ";
  attr1_nm: string | null;
  attr2_nm: string | null;
  attr3_nm: string | null;
  sort_seq: number;
  use_yn: "Y" | "N";
  code_count: number;
}
export interface CodeRow {
  cd_grp_id?: string;
  dtl_cd: string;
  cd_nm: string;
  attr1_val: string | null;
  attr2_val: string | null;
  attr3_val: string | null;
  sort_seq: number;
  use_yn: "Y" | "N";
  aply_bgn_dt: string | null;
}
export interface CodeChoice {
  code: string;
  name: string;
  attr1: string | null;
  attr2: string | null;
  attr3: string | null;
}
export interface ExcelMap {
  map_id: number;
  excel_lbl_nm: string;
  trns_cd_grp_id: string;
  trns_cd: string;
  map_role_cd: "MAIN" | "SUB";
  rmrk_desc: string | null;
  use_yn: "Y" | "N";
  cd_grp_nm: string;
  cd_nm: string;
}
export interface OperatingStandard {
  oprt_std_id: string;
  oprt_std_nm: string;
  aply_bgn_ym: string;
  num_val: number | null;
  txt_val: string | null;
  unit_nm: string | null;
  use_plc_desc: string | null;
}
export interface CodeHistory {
  cd_hist_id: number;
  cd_grp_id: string;
  cd_grp_nm: string;
  dtl_cd: string | null;
  cd_chg_ty_cd: "ADD" | "UPDATE" | "DISABLE" | "ENABLE";
  item_nm: string | null;
  bfr_val: string | null;
  aft_val: string | null;
  chg_user_id: number | null;
  chg_at: string;
}
export interface HistoryPage {
  items: CodeHistory[];
  total: number;
  page: number;
  size: number;
}
export interface Standards {
  items: OperatingStandard[];
  last_closed_ym: string | null;
}
export function write(
  path: string,
  method: "POST" | "PUT" | "PATCH",
  body: unknown,
) {
  return request<void>(path, {
    method,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}
export const codePath = (group: string) =>
  `/api/admin/code-groups/${encodeURIComponent(group)}/codes`;
export const standardPath = (key: string) =>
  `/api/admin/operating-standards/${encodeURIComponent(key)}`;
export const commonCodeApi = {
  groups: () => request<CodeGroup[]>("/api/admin/code-groups"),
  codes: (group: string) => request<CodeRow[]>(codePath(group)),
  choices: (group: string) =>
    request<CodeChoice[]>(`/api/codes/${encodeURIComponent(group)}`),
  maps: () => request<ExcelMap[]>("/api/admin/excel-maps"),
  standards: () => request<Standards>("/api/admin/operating-standards"),
  standardHistory: (key: string) =>
    request<OperatingStandard[]>(`${standardPath(key)}/history`),
  history: (group: string, page: number, size = 50) => {
    const params = new URLSearchParams({
      page: String(page),
      size: String(size),
      sort: "chg_at,desc",
    });
    if (group) params.set("cd_grp_id", group);
    return request<HistoryPage>(`/api/admin/code-history?${params}`);
  },
};

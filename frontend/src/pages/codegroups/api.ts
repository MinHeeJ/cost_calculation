import { request } from "../../api/client";

export interface CodeGroupView {
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
export interface AdminCodeView {
  cd_grp_id: string;
  dtl_cd: string;
  cd_nm: string;
  attr1_val: string | null;
  attr2_val: string | null;
  attr3_val: string | null;
  sort_seq: number;
  use_yn: "Y" | "N";
  aply_bgn_dt: string | null;
}
export type SaveCodeRow = Omit<AdminCodeView, "cd_grp_id">;
export interface CreateCodeGroupRequest {
  cd_grp_id: string;
  cd_grp_nm: string;
  attr1_nm?: string | null;
  attr2_nm?: string | null;
  attr3_nm?: string | null;
}
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
  size: 20 | 50 | 100;
}
const json = (method: string, body: unknown): RequestInit => ({
  method,
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify(body),
});
export const getCodeGroups = () =>
  request<CodeGroupView[]>("/api/admin/code-groups");
export const createCodeGroup = (body: CreateCodeGroupRequest) =>
  request<void>("/api/admin/code-groups", json("POST", body));
export const getAdminCodes = (group: string) =>
  request<AdminCodeView[]>(
    `/api/admin/code-groups/${encodeURIComponent(group)}/codes`,
  );
export const saveCodes = (group: string, codes: SaveCodeRow[]) =>
  request<void>(
    `/api/admin/code-groups/${encodeURIComponent(group)}/codes`,
    json("PUT", { codes }),
  );
export function getCodeHistory(group = "", page = 1, size: 20 | 50 | 100 = 50) {
  const query = new URLSearchParams({
    page: String(page),
    size: String(size),
    sort: "chg_at,desc",
  });
  if (group) query.set("cd_grp_id", group);
  return request<CodeHistoryPage>(`/api/admin/code-history?${query}`);
}

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
export interface AdminCode {
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
export interface CreateCodeGroup {
  cd_grp_id: string;
  cd_grp_nm: string;
  attr1_nm?: string | null;
  attr2_nm?: string | null;
  attr3_nm?: string | null;
}
export const getCodeGroups = () =>
  request<CodeGroup[]>("/api/admin/code-groups");
export const getAdminCodes = (group: string) =>
  request<AdminCode[]>(
    `/api/admin/code-groups/${encodeURIComponent(group)}/codes`,
  );
export const createCodeGroup = (body: CreateCodeGroup) =>
  request<void>("/api/admin/code-groups", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
export const saveCodes = (group: string, codes: AdminCode[]) =>
  request<void>(`/api/admin/code-groups/${encodeURIComponent(group)}/codes`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      codes: codes.map(({ cd_grp_id: _group, ...row }) => row),
    }),
  });

/** Shared choice hooks can subscribe without importing this feature from the API client. */
export function invalidateCodeChoices(group: string) {
  window.dispatchEvent(
    new CustomEvent("codes-invalidated", { detail: { group } }),
  );
}

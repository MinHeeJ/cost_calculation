import { request } from "../../api/client";

export interface ExcelMapView {
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
export interface CreateExcelMapRequest {
  excel_lbl_nm: string;
  trns_cd_grp_id: string;
  trns_cd: string;
  map_role_cd: "MAIN" | "SUB";
  rmrk_desc?: string | null;
}
export type UpdateExcelMapRequest = Partial<
  Pick<ExcelMapView, "map_role_cd" | "rmrk_desc" | "use_yn">
>;
export interface ExcelMapGroup {
  cd_grp_id: string;
  cd_grp_nm: string;
}
export interface CodeChoice {
  code: string;
  name: string;
  attr1: string | null;
  attr2: string | null;
  attr3: string | null;
}
export const getExcelMaps = () =>
  request<ExcelMapView[]>("/api/admin/excel-maps");
export const createExcelMap = (input: CreateExcelMapRequest) =>
  request<void>("/api/admin/excel-maps", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
export const updateExcelMap = (mapId: number, input: UpdateExcelMapRequest) =>
  request<void>(`/api/admin/excel-maps/${mapId}`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
export const getExcelMapGroups = () =>
  request<ExcelMapGroup[]>("/api/admin/code-groups");
export const getCodeChoices = (group: string) =>
  request<CodeChoice[]>(`/api/codes/${encodeURIComponent(group)}`);

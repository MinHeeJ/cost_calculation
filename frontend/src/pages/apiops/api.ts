import { request } from "../../api/client";

export interface CodeChoice {
  code: string;
  name: string;
  attr1: string | null;
  attr2: string | null;
  attr3: string | null;
}

export interface ParameterView {
  oprt_std_id: string;
  aply_bgn_ym: string;
  oprt_std_nm: string;
  num_val: number | null;
  txt_val: string | null;
  unit_nm: string | null;
  use_plc_desc: string | null;
}

export const getCodes = (group: string) =>
  request<CodeChoice[]>(`/api/codes/${encodeURIComponent(group)}`);

export const getParam = (key: string, month: string) =>
  request<ParameterView>(
    `/api/params/${encodeURIComponent(key)}?${new URLSearchParams({ month })}`,
  );

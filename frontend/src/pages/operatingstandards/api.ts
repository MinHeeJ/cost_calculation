import { request } from "../../api/client";

export interface OperatingStandard {
  oprt_std_id: string;
  aply_bgn_ym: string;
  oprt_std_nm: string;
  num_val: number | null;
  txt_val: string | null;
  unit_nm: string | null;
  use_plc_desc: string | null;
}
export interface OperatingStandardsResult {
  items: OperatingStandard[];
  last_closed_ym: string | null;
}
export type SaveOperatingStandard = { aply_bgn_ym: string } & (
  | { num_val: number | string; txt_val?: never }
  | { txt_val: string; num_val?: never }
);
const path = "/api/admin/operating-standards";
export const getOperatingStandards = () =>
  request<OperatingStandardsResult>(path);
export const getOperatingStandardHistory = (key: string) =>
  request<OperatingStandard[]>(`${path}/${encodeURIComponent(key)}/history`);
/** Decimal text stays a JSON number token so IEEE-754 cannot round a user's 18,4 input. */
export function saveOperatingStandard(
  key: string,
  body: SaveOperatingStandard,
) {
  let payload = JSON.stringify(body);
  if (typeof body.num_val === "string") {
    const decimal = body.num_val.trim();
    if (!/^-?(0|[1-9][0-9]*)(\.[0-9]+)?$/.test(decimal)) {
      return Promise.reject(new Error("올바른 값을 입력하세요."));
    }
    payload = `{"aply_bgn_ym":${JSON.stringify(body.aply_bgn_ym)},"num_val":${decimal}}`;
  }
  return request<void>(`${path}/${encodeURIComponent(key)}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: payload,
  });
}

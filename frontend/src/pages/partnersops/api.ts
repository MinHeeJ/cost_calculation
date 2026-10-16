import { request, RequestError } from "../../api/client";

export interface PartnerOption {
  id: number;
  no: string;
  name: string;
}
export interface PartnerAssignment {
  projectName: string;
  developerName: string;
  affiliationName: string;
  jobName: string;
  assignmentRate: number;
  endDate: string;
}
export interface PartnerHistory {
  chgAt: string;
  dataChgTyCd: "INSERT" | "UPDATE" | "DISABLE";
  clmnNm: string | null;
  bfrVal: string | null;
  aftVal: string | null;
  chgUserId: number | null;
}
export interface PartnerExclusion {
  id: number;
  name: string;
  reason: string;
  ongoingProjectCount: number;
  affiliatedDeveloperCount: number;
}
export interface PartnerDisableResult {
  disabledIds: number[];
  disabledCount: number;
  inUseCount: number;
  alreadyDisabledCount: number;
  excluded: PartnerExclusion[];
}
export interface PartnerStatusDetail {
  id: number;
  no: string;
  ptnrSeCd: "CUST" | "VEND" | "BOTH";
  ptnrNm: string;
  bizRegNo: string;
  useYn: "Y" | "N";
  ceoNm: string | null;
  indtyCd: string | null;
  chrgNm: string | null;
  chrgTelNo: string | null;
  kindName: string;
  industryName: string | null;
  txinvEmailAddr?: string;
  bankCd?: string;
  acntNo?: string;
  dpstrNm?: string;
  bankName?: string;
}

export function getPartnerOptions(kind: "CUST" | "VEND") {
  return request<PartnerOption[]>(
    `/api/partners/options?${new URLSearchParams({ kind })}`,
  );
}
export function getPartnerAssignments(id: number) {
  return request<PartnerAssignment[]>(
    `/api/partners/${encodeURIComponent(id)}/assignments`,
  );
}
export function getPartnerHistory(id: number) {
  return request<PartnerHistory[]>(
    `/api/partners/${encodeURIComponent(id)}/history`,
  );
}
export function disablePartners(ids: number[]) {
  return request<PartnerDisableResult>("/api/partners/disable", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ ids }),
  });
}
export function enablePartner(id: number) {
  return request<PartnerStatusDetail>(
    `/api/partners/${encodeURIComponent(id)}/enable`,
    { method: "POST" },
  );
}

/** Error details are untrusted: M4 receives only the approved exclusion shape, never parsed message text. */
export function partnerExclusions(error: unknown): PartnerExclusion[] | null {
  if (
    !(error instanceof RequestError) ||
    error.status !== 409 ||
    error.code !== "PARTNER_IN_USE"
  )
    return null;
  if (
    !error.details ||
    typeof error.details !== "object" ||
    !("excluded" in error.details)
  )
    return null;
  const entries: unknown = error.details.excluded;
  if (!Array.isArray(entries) || entries.length === 0) return null;
  if (
    !entries.every((item: unknown) => {
      if (!item || typeof item !== "object") return false;
      const row = item as Record<string, unknown>;
      return (
        typeof row.id === "number" &&
        Number.isSafeInteger(row.id) &&
        typeof row.name === "string" &&
        typeof row.reason === "string" &&
        typeof row.ongoingProjectCount === "number" &&
        Number.isInteger(row.ongoingProjectCount) &&
        row.ongoingProjectCount >= 0 &&
        typeof row.affiliatedDeveloperCount === "number" &&
        Number.isInteger(row.affiliatedDeveloperCount) &&
        row.affiliatedDeveloperCount >= 0
      );
    })
  )
    return null;
  return entries as PartnerExclusion[];
}

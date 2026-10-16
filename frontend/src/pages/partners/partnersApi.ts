import { request } from "../../api/client";

export type PartnerKind = "CUST" | "VEND" | "BOTH";
export interface PartnerBasic {
  id: number;
  no: string;
  ptnrSeCd: PartnerKind;
  ptnrNm: string;
  bizRegNo: string;
  useYn: "Y" | "N";
  ceoNm: string | null;
  indtyCd: string | null;
  chrgNm: string | null;
  chrgTelNo: string | null;
  kindName: string;
  industryName: string | null;
}
export interface PartnerDetail extends PartnerBasic {
  txinvEmailAddr?: string;
  bankCd?: string;
  acntNo?: string;
  dpstrNm?: string;
  bankName?: string;
}
export interface PartnerListItem extends PartnerBasic {
  txinvEmailAddr: string | null;
  paymentAccountSummary: string | null;
  ongoingProjectCount: number;
  assignedDeveloperCount: number;
}
export interface PartnerPage {
  items: PartnerListItem[];
  total: number;
  page: number;
  size: number;
  sort: string;
}
export interface PartnerQuery {
  kind: string;
  useYn: string;
  q: string;
  page: number;
  size: number;
  sort: string;
}
export interface PartnerDraft {
  ptnrSeCd: string;
  ptnrNm: string;
  bizRegNo: string;
  ceoNm: string;
  indtyCd: string;
  chrgNm: string;
  chrgTelNo: string;
  txinvEmailAddr: string;
  bankCd: string;
  acntNo: string;
  dpstrNm: string;
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
  dataChgTyCd: string;
  clmnNm: string | null;
  bfrVal: string | null;
  aftVal: string | null;
  chgUserId: number | null;
}

export const initialPartnerQuery: PartnerQuery = {
  kind: "",
  useYn: "Y",
  q: "",
  page: 1,
  size: 50,
  sort: "ptnr_nm,asc",
};

/** A fresh draft has no implicit kind, identity or server-controlled state. */
export function partnerDraft(row?: PartnerDetail): PartnerDraft {
  return {
    ptnrSeCd: row?.ptnrSeCd ?? "",
    ptnrNm: row?.ptnrNm ?? "",
    bizRegNo: row?.bizRegNo ?? "",
    ceoNm: row?.ceoNm ?? "",
    indtyCd: row?.indtyCd ?? "",
    chrgNm: row?.chrgNm ?? "",
    chrgTelNo: row?.chrgTelNo ?? "",
    txinvEmailAddr: row?.txinvEmailAddr ?? "",
    bankCd: row?.bankCd ?? "",
    acntNo: row?.acntNo ?? "",
    dpstrNm: row?.dpstrNm ?? "",
  };
}

/** Excludes immutable business number from PUT and omits irrelevant kind fields. */
export function partnerPayload(draft: PartnerDraft, update: boolean) {
  const { bizRegNo, bankCd, acntNo, dpstrNm, txinvEmailAddr, ...common } =
    draft;
  return {
    ...common,
    ...(!update ? { bizRegNo } : {}),
    ...(draft.ptnrSeCd !== "VEND" ? { txinvEmailAddr } : {}),
    ...(draft.ptnrSeCd !== "CUST" ? { bankCd, acntNo, dpstrNm } : {}),
  };
}

const write = <T>(path: string, method: string, body: unknown) =>
  request<T>(path, {
    method,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });

export const partnersApi = {
  list(query: PartnerQuery) {
    const params = new URLSearchParams();
    Object.entries(query).forEach(([key, value]) => {
      if (value !== "") params.set(key, String(value));
    });
    return request<PartnerPage>(`/api/partners?${params}`);
  },
  detail: (id: number) => request<PartnerDetail>(`/api/partners/${id}`),
  create: (draft: PartnerDraft) =>
    write<PartnerDetail>("/api/partners", "POST", partnerPayload(draft, false)),
  update: (id: number, draft: PartnerDraft) =>
    write<PartnerDetail>(
      `/api/partners/${id}`,
      "PUT",
      partnerPayload(draft, true),
    ),
  assignments: (id: number) =>
    request<PartnerAssignment[]>(`/api/partners/${id}/assignments`),
  history: (id: number) =>
    request<PartnerHistory[]>(`/api/partners/${id}/history`),
};

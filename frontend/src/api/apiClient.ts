/** Public operation registry; feature adapters depend on client.ts, never on this registry. */
export { request, api, RequestError } from "./client";
export { getMenus, getMonthClose, getHealth } from "../pages/shared/api";
export { partnersApi } from "../pages/partners/partnersApi";
export {
  getCodeGroups,
  createCodeGroup,
  getAdminCodes,
  saveCodes,
  getCodeHistory,
} from "../pages/codegroups/api";
export { commonCodeApi, write } from "../pages/commoncode/commonCode";
export { useCodes, invalidateCodes } from "../pages/commoncode/useCodes";
export {
  getPartnerOptions,
  getPartnerAssignments,
  getPartnerHistory,
  disablePartners,
  enablePartner,
} from "../pages/partnersops/api";

import { request } from "./client";
import type { OperatingStandard } from "../pages/commoncode/commonCode";

/** Read the value effective in the caller's selected month, not the server's current month. */
export function getParam(
  key: string,
  month: string,
): Promise<OperatingStandard> {
  const query = new URLSearchParams({ month });
  return request<OperatingStandard>(
    `/api/params/${encodeURIComponent(key)}?${query}`,
  );
}

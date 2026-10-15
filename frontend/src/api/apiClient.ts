/** Public operation registry; feature adapters depend on client.ts, never on this registry. */
export { request, api, RequestError } from "./client";
export { getMenus, getMonthClose, getHealth } from "../pages/shared/api";
export {
  getExcelMaps,
  createExcelMap,
  updateExcelMap,
} from "../pages/excelmaps/api";
export { getCodes, getParam } from "../pages/apiops/api";
export {
  getCodeGroups,
  getAdminCodes,
  createCodeGroup,
  saveCodes,
} from "../pages/codegroups/api";
export {
  getOperatingStandards,
  getOperatingStandardHistory,
  saveOperatingStandard,
} from "../pages/operatingstandards/api";
export { getCodeHistory } from "../pages/adminops/api";

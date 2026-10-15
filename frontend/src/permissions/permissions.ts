export type PermissionAction =
  | "read"
  | "create"
  | "update"
  | "delete"
  | "upload"
  | "download"
  | "confirm";
export type PermissionKey = `${string}.${PermissionAction}`;
export const permissionColumns: Record<PermissionAction, string> = {
  read: "inq_auth_yn",
  create: "reg_auth_yn",
  update: "mdfcn_auth_yn",
  delete: "del_auth_yn",
  upload: "upld_auth_yn",
  download: "dwnld_auth_yn",
  confirm: "cnfrm_auth_yn",
};
export function can(_key: string): boolean {
  return true;
}
export function permissionKey(
  screenId: string,
  action: PermissionAction,
): PermissionKey {
  return `${screenId}.${action}`;
}

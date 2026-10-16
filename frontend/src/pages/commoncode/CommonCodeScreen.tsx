import { useEffect, useState, type ReactNode } from "react";
import { Button } from "../../components/Button";
import { WorkDialog } from "../../components/WorkDialog";
import { RequestState } from "../../components/RequestState";
import { ErrorBanner } from "../../components/ErrorBanner";
import { Can } from "../../permissions/Can";
import { RequestError } from "../../api/client";
import { useNavigation } from "../shared/navigation";
import { useNavigationConfirmation } from "../shared/UnsavedChangesGuard";
import {
  commonCodeApi,
  codePath,
  standardPath,
  write,
  type CodeGroup,
  type CodeRow,
  type ExcelMap,
  type OperatingStandard,
} from "./commonCode";
import { useRead, useCodes, invalidateCodes } from "./useCodes";
import styles from "./CommonCodeScreen.module.css";

const tabs = ["코드", "엑셀 표기 대응", "기준값", "변경 이력"];
const empty = "조건에 맞는 결과가 없습니다";
const same = (a: unknown, b: unknown) =>
  JSON.stringify(a) === JSON.stringify(b);
const attrs = [1, 2, 3] as const;
type DirtyPort = (value: boolean) => void;

export function CommonCodeScreen() {
  const groups = useRead(commonCodeApi.groups, "groups");
  const [tab, setTab] = useState(0);
  const [groupId, setGroupId] = useState("");
  const [contentDirty, setDirty] = useState(false);
  const [modalDirty, setModalDirty] = useState(false);
  const dirty = contentDirty || modalDirty;
  const [revision, setRevision] = useState(0);
  const { confirm, dialog } = useNavigationConfirmation();
  useEffect(() => {
    if (!groupId && groups.data?.length) setGroupId(groups.data[0].cd_grp_id);
  }, [groups.data, groupId]);
  useEffect(() => {
    useNavigation.getState().setDirty("UI-27", dirty);
    return () => useNavigation.getState().setDirty("UI-27", false);
  }, [dirty]);
  async function leave(action: () => void) {
    if (dirty && !(await confirm({ kind: "dirty", screenId: "UI-27" }))) return;
    setDirty(false);
    setModalDirty(false);
    setRevision((value) => value + 1);
    action();
  }
  const group = groups.data?.find((row) => row.cd_grp_id === groupId);
  return (
    <Can perm="UI-27.read">
      <section className={styles.screen} data-testid="common-code-screen">
        <div role="tablist" className={styles.tabs} aria-label="공통코드 관리">
          {tabs.map((label, index) => (
            <Button
              key={label}
              role="tab"
              aria-selected={tab === index}
              data-testid={`common-code-tab-${index}`}
              onClick={() => leave(() => setTab(index))}
            >
              {label}
            </Button>
          ))}
        </div>
        <RequestState
          {...groups}
          empty={groups.data?.length === 0}
          emptyReason={empty}
          onRetry={groups.retry}
        />
        <div
          key={`${tab}-${revision}`}
          role="tabpanel"
          data-testid="common-code-panel"
        >
          {tab === 0 && (
            <div className={styles.split}>
              <nav className={styles.groups} aria-label="코드 그룹">
                <CreateDialog
                  kind="group"
                  groups={groups.data ?? []}
                  onSaved={groups.retry}
                  setDirty={setModalDirty}
                />
                {groups.data?.map((row) => (
                  <Button
                    key={row.cd_grp_id}
                    data-testid={`group-${row.cd_grp_id.toLowerCase()}`}
                    aria-current={
                      groupId === row.cd_grp_id ? "true" : undefined
                    }
                    onClick={() => leave(() => setGroupId(row.cd_grp_id))}
                  >
                    {row.cd_grp_se_cd === "SYSTEM" ? "🔒 " : ""}
                    {row.cd_grp_nm}
                    {row.cd_grp_se_cd === "BIZ" && ` (${row.code_count})`}
                  </Button>
                ))}
              </nav>
              <div className={styles.pane}>
                {group && (
                  <CodeEditor
                    key={groupId}
                    group={group}
                    setDirty={setDirty}
                    onSaved={groups.retry}
                  />
                )}
              </div>
            </div>
          )}
          {tab === 1 && (
            <Maps
              groups={groups.data ?? []}
              setDirty={setDirty}
              setModalDirty={setModalDirty}
            />
          )}
          {tab === 2 && <Standards setDirty={setModalDirty} />}
          {tab === 3 && <History groups={groups.data ?? []} />}
        </div>
        {dialog}
      </section>
    </Can>
  );
}

function Grid({
  headers,
  emptyText,
  children,
}: {
  headers: string[];
  emptyText?: string;
  children: ReactNode;
}) {
  return (
    <div className={styles.tableScroll}>
      <table>
        <thead>
          <tr>
            {headers.map((label) => (
              <th key={label}>{label}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {emptyText ? (
            <tr>
              <td colSpan={headers.length}>{emptyText}</td>
            </tr>
          ) : (
            children
          )}
        </tbody>
      </table>
    </div>
  );
}

function CodeEditor({
  group,
  setDirty,
  onSaved,
}: {
  group: CodeGroup;
  setDirty: DirtyPort;
  onSaved: () => Promise<unknown>;
}) {
  const result = useRead(
    () => commonCodeApi.codes(group.cd_grp_id),
    group.cd_grp_id,
  );
  const [rows, setRows] = useState<CodeRow[]>([]);
  const [snapshot, setSnapshot] = useState<CodeRow[]>([]);
  const [error, setError] = useState<RequestError | null>(null);
  const [validation, setValidation] = useState("");
  const [success, setSuccess] = useState(false);
  const locked = group.cd_grp_se_cd === "SYSTEM";
  useEffect(() => {
    if (result.data) {
      setRows(result.data);
      setSnapshot(result.data);
    }
  }, [result.data]);
  function change(next: CodeRow[]) {
    setRows(next);
    setDirty(!same(next, snapshot));
    setSuccess(false);
  }
  async function save() {
    setValidation("");
    const invalid = rows.find(
      (row) => !/^[A-Z0-9_]{1,30}$/.test(row.dtl_cd) || !row.cd_nm.trim(),
    );
    if (invalid) {
      setValidation(
        !invalid.dtl_cd
          ? "코드를 입력하세요."
          : !invalid.cd_nm.trim()
            ? "이름을 입력하세요."
            : "코드는 영문 대문자·숫자·_ 1~30자로 입력하세요.",
      );
      return;
    }
    if (new Set(rows.map((row) => row.dtl_cd)).size !== rows.length) {
      setValidation("이미 있는 코드입니다.");
      return;
    }
    try {
      await write(codePath(group.cd_grp_id), "PUT", {
        codes: rows.map(({ cd_grp_id: _group, ...row }) => row),
      });
    } catch (failure) {
      setError(failure as RequestError);
      return;
    }
    setError(null);
    setSnapshot(rows);
    setDirty(false);
    setSuccess(true);
    invalidateCodes(group.cd_grp_id);
    await Promise.all([result.retry(), onSaved()]);
  }
  return (
    <div className={styles.stack}>
      <header className={styles.toolbar}>
        <h2>
          {group.cd_grp_nm} · {snapshot.length}
        </h2>
        {!locked && (
          <Can perm="UI-27.create">
            <Button
              disabled={result.loading || !!result.error}
              onClick={() =>
                change([
                  ...rows,
                  {
                    dtl_cd: "",
                    cd_nm: "",
                    attr1_val: null,
                    attr2_val: null,
                    attr3_val: null,
                    sort_seq:
                      Math.max(0, ...rows.map((row) => row.sort_seq)) + 1,
                    use_yn: "Y",
                    aply_bgn_dt: null,
                  },
                ])
              }
            >
              + 코드 추가
            </Button>
          </Can>
        )}
      </header>
      {locked && (
        <p className={styles.lock}>
          계산 규칙에 쓰는 시스템 코드입니다. 이름만 바꿀 수 있고 추가·삭제는 할
          수 없습니다.
        </p>
      )}
      <RequestState {...result} onRetry={result.retry} />
      {error && <ErrorBanner error={error} onRetry={save} />}
      {validation && (
        <p role="alert" className={styles.error}>
          {validation}
        </p>
      )}
      {success && <p role="status">공통코드를 저장했습니다.</p>}
      <Grid
        emptyText={
          !result.loading &&
          !result.error &&
          result.data?.length === 0 &&
          !rows.length
            ? empty
            : undefined
        }
        headers={[
          "코드",
          "이름",
          "순서",
          "사용",
          ...attrs.flatMap((n) =>
            group[`attr${n}_nm`] ? [group[`attr${n}_nm`]!] : [],
          ),
          "적용 시작일",
        ]}
      >
        {rows.map((row, index) => {
          const saved = snapshot.some((old) => old.dtl_cd === row.dtl_cd);
          const update = (key: keyof CodeRow, value: unknown) =>
            change(
              rows.map((item, i) =>
                i === index ? { ...item, [key]: value } : item,
              ),
            );
          return (
            <tr key={index} data-testid={`code-row-${index}`}>
              <td>
                <input
                  aria-label={`코드 ${index + 1}`}
                  value={row.dtl_cd}
                  readOnly={saved}
                  maxLength={30}
                  onChange={(e) => update("dtl_cd", e.target.value)}
                />
              </td>
              <td>
                <input
                  aria-label={`이름 ${index + 1}`}
                  value={row.cd_nm}
                  maxLength={100}
                  onChange={(e) => update("cd_nm", e.target.value)}
                />
              </td>
              <td>
                <input
                  aria-label={`순서 ${index + 1}`}
                  type="number"
                  value={row.sort_seq}
                  step="1"
                  onChange={(e) => update("sort_seq", Number(e.target.value))}
                />
              </td>
              <td>
                <Can perm="UI-27.delete">
                  <input
                    aria-label={`사용 ${index + 1}`}
                    type="checkbox"
                    checked={row.use_yn === "Y"}
                    disabled={locked || !saved}
                    onChange={(e) =>
                      update("use_yn", e.target.checked ? "Y" : "N")
                    }
                  />
                </Can>
              </td>
              {attrs
                .filter((n) => group[`attr${n}_nm`])
                .map((n) => (
                  <td key={n}>
                    <input
                      aria-label={`${group[`attr${n}_nm`]} ${index + 1}`}
                      value={row[`attr${n}_val`] ?? ""}
                      readOnly={locked}
                      maxLength={100}
                      onChange={(e) =>
                        update(`attr${n}_val`, e.target.value || null)
                      }
                    />
                  </td>
                ))}
              <td>
                <input
                  aria-label={`적용 시작일 ${index + 1}`}
                  type="date"
                  value={row.aply_bgn_dt ?? ""}
                  readOnly={locked}
                  onChange={(e) =>
                    update("aply_bgn_dt", e.target.value || null)
                  }
                />
              </td>
            </tr>
          );
        })}
      </Grid>
      <footer className={styles.toolbar}>
        <Button
          onClick={() => {
            change(snapshot);
            setError(null);
            setValidation("");
          }}
        >
          취소
        </Button>
        <Can perm="UI-27.update">
          <Button
            variant="primary"
            onClick={save}
            disabled={same(rows, snapshot) || result.loading || !!result.error}
          >
            저장
          </Button>
        </Can>
      </footer>
    </div>
  );
}

function Maps({
  groups,
  setDirty,
  setModalDirty,
}: {
  groups: CodeGroup[];
  setDirty: DirtyPort;
  setModalDirty: DirtyPort;
}) {
  const result = useRead(commonCodeApi.maps, "maps");
  const [drafts, setDrafts] = useState<Record<number, ExcelMap>>({});
  const [error, setError] = useState<RequestError | null>(null);
  function update(id: number, row?: ExcelMap) {
    const next = { ...drafts };
    if (row) next[id] = row;
    else delete next[id];
    setDrafts(next);
    setDirty(Object.keys(next).length > 0);
  }
  async function save(row: ExcelMap) {
    try {
      await write(`/api/admin/excel-maps/${row.map_id}`, "PATCH", {
        map_role_cd: row.map_role_cd,
        rmrk_desc: row.rmrk_desc,
        use_yn: row.use_yn,
      });
    } catch (failure) {
      setError(failure as RequestError);
      return;
    }
    setError(null);
    update(row.map_id);
    await result.retry();
  }
  return (
    <div className={styles.stack}>
      <CreateDialog
        kind="map"
        groups={groups}
        setDirty={setModalDirty}
        onSaved={result.retry}
      />
      <RequestState {...result} onRetry={result.retry} />
      {error && <ErrorBanner error={error} onRetry={result.retry} />}
      <Grid
        emptyText={
          !result.loading && !result.error && result.data?.length === 0
            ? empty
            : undefined
        }
        headers={["엑셀 표기", "바꾸는 값", "주/보조", "메모", "사용", "작업"]}
      >
        {result.data?.map((original) => {
          const row = drafts[original.map_id] ?? original;
          return (
            <tr key={row.map_id} data-testid={`map-row-${row.map_id}`}>
              <td>{row.excel_lbl_nm}</td>
              <td>
                {row.cd_grp_nm} · {row.cd_nm}
              </td>
              <td>
                <select
                  aria-label={`주/보조 ${row.map_id}`}
                  value={row.map_role_cd}
                  onChange={(e) =>
                    update(row.map_id, {
                      ...row,
                      map_role_cd: e.target.value as "MAIN" | "SUB",
                    })
                  }
                >
                  <option value="MAIN">주</option>
                  <option value="SUB">보조</option>
                </select>
              </td>
              <td>
                <input
                  aria-label={`메모 ${row.map_id}`}
                  value={row.rmrk_desc ?? ""}
                  maxLength={200}
                  onChange={(e) =>
                    update(row.map_id, {
                      ...row,
                      rmrk_desc: e.target.value || null,
                    })
                  }
                />
              </td>
              <td>
                <Can perm="UI-27.delete">
                  <input
                    type="checkbox"
                    aria-label={`사용 ${row.map_id}`}
                    checked={row.use_yn === "Y"}
                    onChange={(e) =>
                      update(row.map_id, {
                        ...row,
                        use_yn: e.target.checked ? "Y" : "N",
                      })
                    }
                  />
                </Can>
              </td>
              <td>
                <div className={styles.actions}>
                  <Button onClick={() => update(row.map_id)}>취소</Button>
                  <Can perm="UI-27.update">
                    <Button
                      disabled={!drafts[row.map_id]}
                      onClick={() => save(row)}
                    >
                      저장
                    </Button>
                  </Can>
                </div>
              </td>
            </tr>
          );
        })}
      </Grid>
    </div>
  );
}

function Standards({ setDirty }: { setDirty: DirtyPort }) {
  const result = useRead(commonCodeApi.standards, "standards");
  const [selected, setSelected] = useState<OperatingStandard>();
  const [history, setHistory] = useState<OperatingStandard>();
  return (
    <div className={styles.stack}>
      <RequestState {...result} onRetry={result.retry} />
      <Grid
        emptyText={
          !result.loading && !result.error && result.data?.items.length === 0
            ? empty
            : undefined
        }
        headers={["항목", "지금 값·단위", "적용 시작월", "쓰는 곳", "작업"]}
      >
        {result.data?.items.map((row) => (
          <tr
            key={row.oprt_std_id}
            data-testid={`standard-${row.oprt_std_id.toLowerCase()}`}
          >
            <td>{row.oprt_std_nm}</td>
            <td>
              {row.num_val ?? row.txt_val} {row.unit_nm}
            </td>
            <td>{row.aply_bgn_ym}</td>
            <td>{row.use_plc_desc}</td>
            <td>
              <div className={styles.actions}>
                <Can perm="UI-27.update">
                  <Button onClick={() => setSelected(row)}>값 변경</Button>
                </Can>
                <Button onClick={() => setHistory(row)}>이력 보기</Button>
              </div>
            </td>
          </tr>
        ))}
      </Grid>
      {selected && (
        <CreateDialog
          kind="standard"
          groups={[]}
          standard={selected}
          lastClosed={result.data?.last_closed_ym}
          setDirty={setDirty}
          onSaved={result.retry}
          onClose={() => setSelected(undefined)}
        />
      )}
      {history && (
        <StandardHistory row={history} onClose={() => setHistory(undefined)} />
      )}
    </div>
  );
}
function StandardHistory({
  row,
  onClose,
}: {
  row: OperatingStandard;
  onClose: () => void;
}) {
  const result = useRead(
    () => commonCodeApi.standardHistory(row.oprt_std_id),
    row.oprt_std_id,
  );
  return (
    <WorkDialog
      open
      title={`기준값 전체 이력: ${row.oprt_std_nm}`}
      onClose={onClose}
      cancelLabel="닫기"
    >
      <RequestState {...result} onRetry={result.retry} />
      <Grid
        emptyText={
          !result.loading && !result.error && result.data?.length === 0
            ? empty
            : undefined
        }
        headers={["적용 시작월", "값·단위"]}
      >
        {result.data?.map((item) => (
          <tr key={item.aply_bgn_ym}>
            <td>{item.aply_bgn_ym}</td>
            <td>
              {item.num_val ?? item.txt_val} {item.unit_nm}
            </td>
          </tr>
        ))}
      </Grid>
    </WorkDialog>
  );
}
function History({ groups }: { groups: CodeGroup[] }) {
  const [group, setGroup] = useState("");
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(50);
  const result = useRead(
    () => commonCodeApi.history(group, page, size),
    `${group}-${page}-${size}`,
  );
  const names = {
    ADD: "추가",
    UPDATE: "수정",
    DISABLE: "사용 중지",
    ENABLE: "다시 사용",
  };
  return (
    <div className={styles.stack}>
      <label>
        그룹{" "}
        <select
          value={group}
          onChange={(e) => {
            setGroup(e.target.value);
            setPage(1);
          }}
        >
          <option value="">전체</option>
          {groups.map((row) => (
            <option key={row.cd_grp_id} value={row.cd_grp_id}>
              {row.cd_grp_nm}
            </option>
          ))}
          <option value="PARAM">기준값</option>
        </select>
      </label>
      <RequestState {...result} onRetry={result.retry} />
      <Grid
        emptyText={
          !result.loading && !result.error && result.data?.items.length === 0
            ? empty
            : undefined
        }
        headers={[
          "일시",
          "그룹",
          "코드",
          "구분",
          "항목",
          "이전",
          "이후",
          "변경자",
        ]}
      >
        {result.data?.items.map((row) => (
          <tr key={row.cd_hist_id}>
            <td>{row.chg_at.replace("T", " ")}</td>
            <td>{row.cd_grp_nm}</td>
            <td>{row.dtl_cd ?? "–"}</td>
            <td>{names[row.cd_chg_ty_cd]}</td>
            <td>{row.item_nm ?? "–"}</td>
            <td>{row.bfr_val ?? "NULL"}</td>
            <td>{row.aft_val ?? "NULL"}</td>
            <td>{row.chg_user_id ?? "기본 사용자"}</td>
          </tr>
        ))}
      </Grid>
      <footer className={styles.actions}>
        <Button
          disabled={page <= 1 || result.loading}
          onClick={() => setPage(page - 1)}
        >
          이전 페이지
        </Button>
        <span>
          {page} 페이지 · 총 {result.data?.total ?? 0}건
        </span>
        <Button
          disabled={
            !result.data || page * size >= result.data.total || result.loading
          }
          onClick={() => setPage(page + 1)}
        >
          다음 페이지
        </Button>
        <label>
          페이지 크기{" "}
          <select
            value={size}
            onChange={(e) => {
              setSize(Number(e.target.value));
              setPage(1);
            }}
          >
            {[20, 50, 100].map((n) => (
              <option key={n} value={n}>
                {n}
              </option>
            ))}
          </select>
        </label>
      </footer>
    </div>
  );
}

function CreateDialog({
  kind,
  groups,
  standard,
  lastClosed,
  setDirty,
  onSaved,
  onClose,
}: {
  kind: "group" | "map" | "standard";
  groups: CodeGroup[];
  standard?: OperatingStandard;
  lastClosed?: string | null;
  setDirty: DirtyPort;
  onSaved: () => Promise<unknown>;
  onClose?: () => void;
}) {
  const [open, setOpen] = useState(kind === "standard");
  const [values, setValues] = useState<Record<string, string>>({});
  const [error, setError] = useState<RequestError | null>(null);
  const [validation, setValidation] = useState<Record<string, string>>({});
  const choices = useCodes(
    open && kind === "map" ? (values.trns_cd_grp_id ?? "") : "",
  );
  const { confirm, dialog } = useNavigationConfirmation();
  const title =
    kind === "group"
      ? "그룹 추가"
      : kind === "map"
        ? "엑셀 표기 대응 추가"
        : `기준값 변경: ${standard?.oprt_std_nm}`;
  function change(key: string, value: string) {
    setValues((old) => ({
      ...old,
      [key]: value,
      ...(key === "trns_cd_grp_id" ? { trns_cd: "" } : {}),
    }));
    setDirty(true);
  }
  async function close() {
    if (
      Object.values(values).some(Boolean) &&
      !(await confirm({ kind: "dirty", screenId: "UI-27" }))
    )
      return;
    setOpen(false);
    setValues({});
    setDirty(false);
    onClose?.();
  }
  const numeric = standard?.num_val != null;
  async function save() {
    const errors: Record<string, string> = {};
    let path: string;
    let payload: Record<string, unknown>;
    if (kind === "group") {
      if (!/^[A-Z_]{1,30}$/.test(values.cd_grp_id ?? ""))
        errors.cd_grp_id = "대문자·_ 1~30자로 입력하세요.";
      if (!values.cd_grp_nm?.trim()) errors.cd_grp_nm = "이름을 입력하세요.";
      path = "/api/admin/code-groups";
      payload = { cd_grp_id: values.cd_grp_id, cd_grp_nm: values.cd_grp_nm };
      attrs.forEach((n) => {
        payload[`attr${n}_nm`] = values[`attr${n}_nm`] || null;
      });
    } else if (kind === "map") {
      if (!values.excel_lbl_nm?.trim())
        errors.excel_lbl_nm = "엑셀 글자를 입력하세요.";
      if (!values.trns_cd) errors.trns_cd = "대응할 코드를 고르세요.";
      if (!values.map_role_cd) errors.map_role_cd = "주/보조를 고르세요.";
      path = "/api/admin/excel-maps";
      payload = { ...values, rmrk_desc: values.rmrk_desc || null };
    } else {
      path = standardPath(standard!.oprt_std_id);
      const month = values.aply_bgn_ym ?? "";
      if (!/^[0-9]{4}-(0[1-9]|1[0-2])$/.test(month))
        errors.aply_bgn_ym = "적용 시작월을 입력하세요.";
      else if (lastClosed && month <= lastClosed)
        errors.aply_bgn_ym = "마감월 다음 달부터 선택하세요.";
      const value = values.value ?? "";
      if (
        numeric
          ? !/^\d+(\.\d{1,4})?$/.test(value) ||
            Number(value) >= 1e14 ||
            ((standard!.unit_nm === "%" || standard!.unit_nm === "%p") &&
              Number(value) > 100)
          : !value.trim() || value.length > 200
      )
        errors.value = "올바른 값을 입력하세요.";
      payload = {
        aply_bgn_ym: month,
        [numeric ? "num_val" : "txt_val"]: numeric ? Number(value) : value,
      };
    }
    setValidation(errors);
    if (Object.keys(errors).length) return;
    try {
      await write(path, kind === "standard" ? "PUT" : "POST", payload);
    } catch (failure) {
      const problem = failure as RequestError;
      setError(problem);
      if (problem.field)
        setValidation({
          [problem.field === "num_val" || problem.field === "txt_val"
            ? "value"
            : problem.field]: problem.message,
        });
      return;
    }
    setError(null);
    setOpen(false);
    setValues({});
    setDirty(false);
    onClose?.();
    await onSaved();
  }
  function field(key: string, label: string, maxLength: number, type = "text") {
    return (
      <label className={styles.field} key={key}>
        <span>{label}</span>
        <input
          aria-label={label}
          value={values[key] ?? ""}
          maxLength={maxLength}
          type={type}
          aria-invalid={!!validation[key]}
          onChange={(e) => change(key, e.target.value)}
        />
        {validation[key] && (
          <small className={styles.error}>{validation[key]}</small>
        )}
      </label>
    );
  }
  function select(
    key: string,
    label: string,
    options: { value: string; label: string }[],
  ) {
    return (
      <label className={styles.field}>
        <span>{label}</span>
        <select
          aria-label={label}
          value={values[key] ?? ""}
          onChange={(e) => change(key, e.target.value)}
        >
          <option value="">선택하세요</option>
          {options.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
        {validation[key] && (
          <small className={styles.error}>{validation[key]}</small>
        )}
      </label>
    );
  }
  return (
    <>
      {kind !== "standard" && (
        <Can perm="UI-27.create">
          <Button onClick={() => setOpen(true)}>
            + {kind === "group" ? "그룹 추가" : "대응 추가"}
          </Button>
        </Can>
      )}
      <WorkDialog
        open={open}
        title={title}
        onClose={() => void close()}
        actions={
          <Button variant="primary" onClick={save}>
            저장
          </Button>
        }
      >
        <div
          className={`${styles.screen} ${styles.stack}`}
          data-testid={`common-code-${kind}-dialog`}
        >
          {error && <ErrorBanner error={error} onRetry={save} />}
          {kind === "group" && (
            <>
              {field("cd_grp_id", "그룹 코드", 30)}
              {field("cd_grp_nm", "그룹명", 100)}
              {attrs.map((n) => field(`attr${n}_nm`, `추가 값 이름 ${n}`, 50))}
            </>
          )}
          {kind === "map" && (
            <>
              {field("excel_lbl_nm", "엑셀 표기", 100)}
              {select(
                "trns_cd_grp_id",
                "그룹",
                groups.map((g) => ({ value: g.cd_grp_id, label: g.cd_grp_nm })),
              )}
              <RequestState
                {...choices}
                empty={!!values.trns_cd_grp_id && choices.data?.length === 0}
                emptyReason={empty}
                onRetry={choices.retry}
              />
              {select(
                "trns_cd",
                "코드",
                (choices.data ?? []).map((c) => ({
                  value: c.code,
                  label: c.name,
                })),
              )}
              {select("map_role_cd", "주/보조", [
                { value: "MAIN", label: "주" },
                { value: "SUB", label: "보조" },
              ])}
              {field("rmrk_desc", "메모", 200)}
            </>
          )}
          {kind === "standard" && (
            <>
              <p>
                현재 값 {standard?.num_val ?? standard?.txt_val}{" "}
                {standard?.unit_nm}
              </p>
              <p>쓰는 곳 {standard?.use_plc_desc}</p>
              {field("value", "새 값", numeric ? 20 : 200)}
              {field("aply_bgn_ym", "적용 시작월", 7, "month")}
              <p>
                {lastClosed
                  ? `마지막 마감월 ${lastClosed} 다음 달부터 선택`
                  : "마감된 달이 없습니다."}
              </p>
            </>
          )}
        </div>
      </WorkDialog>
      {dialog}
    </>
  );
}

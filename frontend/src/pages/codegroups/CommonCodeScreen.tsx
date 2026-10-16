import { useEffect, useRef, useState, type ReactNode } from "react";
import { Button } from "../../components/Button";
import { ErrorBanner } from "../../components/ErrorBanner";
import { LockBanner } from "../../components/LockBanner";
import { WorkDialog } from "../../components/WorkDialog";
import { UnderlineTabs } from "../../components/UnderlineTabs";
import { RequestError } from "../../api/client";
import { processorName } from "../../lib/processorName";
import { Can } from "../../permissions/Can";
import { useNavigation } from "../shared/navigation";
import { useNavigationConfirmation } from "../shared/UnsavedChangesGuard";
import {
  createCodeGroup,
  getAdminCodes,
  getCodeGroups,
  getCodeHistory,
  saveCodes,
  type AdminCodeView,
  type CodeGroupView,
  type CodeHistoryPage,
  type CreateCodeGroupRequest,
} from "./api";
import styles from "./CommonCode.module.css";

const tabs = [
  { id: "codes", label: "코드" },
  { id: "maps", label: "엑셀 표기 대응" },
  { id: "standards", label: "기준값" },
  { id: "history", label: "변경 이력" },
];
const asError = (error: unknown) =>
  error instanceof RequestError
    ? error
    : new RequestError(
        "처리 중 오류가 발생했습니다. 다시 시도해 주세요.",
        "",
        "client",
        null,
      );
const emptyGroup: CreateCodeGroupRequest = {
  cd_grp_id: "",
  cd_grp_nm: "",
  attr1_nm: "",
  attr2_nm: "",
  attr3_nm: "",
};

/** UI-27 body only. Other feature owners supply their tab bodies without importing this module back. */
export function CommonCodeScreen({
  excelMaps,
  operatingStandards,
}: {
  excelMaps?: ReactNode;
  operatingStandards?: ReactNode;
}) {
  const [groups, setGroups] = useState<CodeGroupView[]>([]);
  const [selected, setSelected] = useState("");
  const [tab, setTab] = useState("codes");
  const [snapshot, setSnapshot] = useState<AdminCodeView[]>([]);
  const [draft, setDraft] = useState<AdminCodeView[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<RequestError>();
  const [message, setMessage] = useState("");
  const [refreshPending, setRefreshPending] = useState(false);
  const [groupOpen, setGroupOpen] = useState(false);
  const [groupDraft, setGroupDraft] = useState({ ...emptyGroup });
  const [groupError, setGroupError] = useState<RequestError>();
  const [groupBusy, setGroupBusy] = useState(false);
  const { confirm, dialog } = useNavigationConfirmation();
  const sequence = useRef(0);
  const groupSequence = useRef(0);
  const dirty = JSON.stringify(snapshot) !== JSON.stringify(draft);
  const modalDirty =
    groupOpen && JSON.stringify(groupDraft) !== JSON.stringify(emptyGroup);
  const group = groups.find((item) => item.cd_grp_id === selected);
  const system = group?.cd_grp_se_cd === "SYSTEM";

  useEffect(() => {
    useNavigation.getState().setDirty("UI-27", dirty || modalDirty);
  }, [dirty, modalDirty]);
  useEffect(
    () => () => {
      sequence.current++;
      groupSequence.current++;
      useNavigation.getState().setDirty("UI-27", false);
    },
    [],
  );

  async function loadGroups() {
    const id = ++groupSequence.current;
    try {
      const rows = await getCodeGroups();
      if (id !== groupSequence.current) return;
      setGroups(rows);
      setSelected((old) => old || rows[0]?.cd_grp_id || "");
      if (!rows.length) setLoading(false);
    } catch (cause) {
      if (id === groupSequence.current) {
        setError(asError(cause));
        setLoading(false);
      }
    }
  }
  async function loadCodes(replaceDraft = true) {
    if (!selected) return;
    const id = ++sequence.current;
    setLoading(true);
    try {
      const rows = await getAdminCodes(selected);
      if (id !== sequence.current) return;
      setSnapshot(rows);
      if (replaceDraft) setDraft(rows);
      setRefreshPending(false);
      setError(undefined);
    } catch (cause) {
      if (id === sequence.current) setError(asError(cause));
    } finally {
      if (id === sequence.current) setLoading(false);
    }
  }
  useEffect(() => {
    void loadGroups();
  }, []);
  useEffect(() => {
    setSnapshot([]);
    setDraft([]);
    if (selected) void loadCodes();
    return () => {
      sequence.current++;
    };
  }, [selected]);

  async function transition(action: () => void) {
    if (busy || groupBusy || refreshPending) return;
    if (
      (dirty || modalDirty) &&
      !(await confirm({ kind: "dirty", screenId: "UI-27" }))
    )
      return;
    setDraft(snapshot);
    setMessage("");
    setError(undefined);
    action();
  }
  function edit(
    index: number,
    field: keyof AdminCodeView,
    value: string | number | null,
  ) {
    setDraft((rows) =>
      rows.map((row, i) => (i === index ? { ...row, [field]: value } : row)),
    );
    setMessage("");
  }
  async function save() {
    if (busy || refreshPending) return;
    const invalid = draft.find(
      (row) => !row.dtl_cd.trim() || !row.cd_nm.trim(),
    );
    if (invalid) {
      setError(
        new RequestError(
          !invalid.dtl_cd.trim() ? "코드를 입력하세요." : "이름을 입력하세요.",
          "",
          "client",
          400,
          !invalid.dtl_cd.trim() ? "dtl_cd" : "cd_nm",
        ),
      );
      return;
    }
    setBusy(true);
    setError(undefined);
    try {
      await saveCodes(
        selected,
        draft.map(({ cd_grp_id: _group, ...row }) => row),
      );
      setSnapshot(draft);
      setRefreshPending(true);
      setMessage("공통코드를 저장했습니다.");
      await loadCodes();
      await loadGroups();
    } catch (cause) {
      setError(asError(cause));
    } finally {
      setBusy(false);
    }
  }
  async function saveGroup() {
    if (groupBusy) return;
    setGroupBusy(true);
    setGroupError(undefined);
    try {
      await createCodeGroup(groupDraft);
      setGroupOpen(false);
      setGroupDraft({ ...emptyGroup });
      setMessage("그룹을 추가했습니다.");
      await loadGroups();
    } catch (cause) {
      setGroupError(asError(cause));
    } finally {
      setGroupBusy(false);
    }
  }
  const attrs = ([1, 2, 3] as const).filter((n) => group?.[`attr${n}_nm`]);
  return (
    <section
      className={styles.screen}
      data-testid="common-code-screen"
      aria-label="공통코드 관리"
    >
      <UnderlineTabs
        tabs={tabs}
        value={tab}
        onChange={(value) => void transition(() => setTab(value))}
      />
      {message && (
        <p role="status" className={styles.success}>
          {message}
        </p>
      )}
      {tab === "codes" && (
        <div className={styles.panes} role="tabpanel" data-testid="code-panel">
          <nav className={styles.groups} aria-label="코드 그룹">
            <Can perm="UI-27.create">
              <Button
                onClick={() => {
                  setGroupOpen(true);
                  setGroupError(undefined);
                }}
                data-testid="group-add"
              >
                + 그룹 추가
              </Button>
            </Can>
            {groups.map((item) => (
              <button
                key={item.cd_grp_id}
                className={styles.group}
                aria-current={item.cd_grp_id === selected ? "true" : undefined}
                data-testid={`group-${item.cd_grp_id.toLowerCase().replaceAll("_", "-")}`}
                onClick={() =>
                  item.cd_grp_id !== selected &&
                  void transition(() => setSelected(item.cd_grp_id))
                }
              >
                <span>{item.cd_grp_nm}</span>
                <span>
                  {item.cd_grp_se_cd === "SYSTEM" ? "🔒" : item.code_count}
                </span>
              </button>
            ))}
          </nav>
          <div className={styles.details}>
            {error && (
              <ErrorBanner
                error={error}
                onRetry={async () => {
                  await loadGroups();
                  await loadCodes(!dirty || refreshPending);
                }}
              />
            )}
            {error?.field && (
              <p className={styles.fieldError}>
                {error.field}: {error.message}
              </p>
            )}
            {loading && <p role="status">조회 중입니다.</p>}
            {!loading && !groups.length && !error && (
              <p>조건에 맞는 결과가 없습니다</p>
            )}
            {group && (
              <>
                <div className={styles.toolbar}>
                  <h2>
                    {group.cd_grp_nm} <span>{draft.length}개</span>
                  </h2>
                  <div className={styles.actions}>
                    {!system && (
                      <Can perm="UI-27.create">
                        <Button
                          disabled={loading || refreshPending || busy}
                          data-testid="code-add"
                          onClick={() => {
                            setDraft([
                              ...draft,
                              {
                                cd_grp_id: selected,
                                dtl_cd: "",
                                cd_nm: "",
                                sort_seq:
                                  Math.max(
                                    0,
                                    ...draft.map((row) => row.sort_seq),
                                  ) + 1,
                                use_yn: "Y",
                                attr1_val: null,
                                attr2_val: null,
                                attr3_val: null,
                                aply_bgn_dt: null,
                              },
                            ]);
                          }}
                        >
                          + 코드 추가
                        </Button>
                      </Can>
                    )}
                    <Button
                      disabled={!dirty || busy || refreshPending}
                      onClick={() => {
                        setDraft(snapshot);
                        setError(undefined);
                        setMessage("");
                      }}
                      data-testid="code-cancel"
                    >
                      취소
                    </Button>
                    <Can perm="UI-27.update">
                      <Button
                        variant="primary"
                        busy={busy}
                        disabled={!dirty || refreshPending || loading}
                        onClick={save}
                        data-testid="code-save"
                      >
                        저장
                      </Button>
                    </Can>
                  </div>
                </div>
                {system && (
                  <LockBanner>
                    계산 규칙에 쓰는 시스템 코드입니다. 이름만 바꿀 수 있고
                    추가·삭제는 할 수 없습니다.
                  </LockBanner>
                )}
                <div className={styles.tableScroll}>
                  <table>
                    <thead>
                      <tr>
                        <th>코드</th>
                        <th>이름</th>
                        {attrs.map((n) => (
                          <th key={n}>{group[`attr${n}_nm`]}</th>
                        ))}
                        <th>순서</th>
                        <th>사용</th>
                        <th>적용 시작일</th>
                      </tr>
                    </thead>
                    <tbody>
                      {draft.map((row, index) => (
                        <tr key={index} data-testid={`code-row-${index}`}>
                          <td>
                            <input
                              aria-label={`코드 ${index + 1}`}
                              value={row.dtl_cd}
                              maxLength={30}
                              readOnly={index < snapshot.length}
                              disabled={busy || refreshPending}
                              onChange={(event) =>
                                edit(index, "dtl_cd", event.target.value)
                              }
                            />
                          </td>
                          <td>
                            <input
                              aria-label={`이름 ${index + 1}`}
                              value={row.cd_nm}
                              maxLength={100}
                              disabled={busy || refreshPending}
                              onChange={(event) =>
                                edit(index, "cd_nm", event.target.value)
                              }
                            />
                          </td>
                          {attrs.map((n) => (
                            <td key={n}>
                              <input
                                aria-label={`${group[`attr${n}_nm`]} ${index + 1}`}
                                value={row[`attr${n}_val`] ?? ""}
                                maxLength={100}
                                readOnly={system}
                                disabled={busy || refreshPending}
                                onChange={(event) =>
                                  edit(
                                    index,
                                    `attr${n}_val`,
                                    event.target.value || null,
                                  )
                                }
                              />
                            </td>
                          ))}
                          <td>
                            <input
                              type="number"
                              aria-label={`순서 ${index + 1}`}
                              value={row.sort_seq}
                              disabled={busy || refreshPending}
                              onChange={(event) =>
                                edit(
                                  index,
                                  "sort_seq",
                                  Number(event.target.value),
                                )
                              }
                            />
                          </td>
                          <td>
                            <Can perm="UI-27.delete">
                              <input
                                type="checkbox"
                                aria-label={`사용 ${index + 1}`}
                                checked={row.use_yn === "Y"}
                                disabled={system || busy || refreshPending}
                                onChange={(event) =>
                                  edit(
                                    index,
                                    "use_yn",
                                    event.target.checked ? "Y" : "N",
                                  )
                                }
                              />
                            </Can>
                          </td>
                          <td>
                            <input
                              type="date"
                              aria-label={`적용 시작일 ${index + 1}`}
                              value={row.aply_bgn_dt ?? ""}
                              readOnly={system}
                              disabled={busy || refreshPending}
                              onChange={(event) =>
                                edit(
                                  index,
                                  "aply_bgn_dt",
                                  event.target.value || null,
                                )
                              }
                            />
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                  {!draft.length && !loading && (
                    <p className={styles.empty}>조건에 맞는 결과가 없습니다</p>
                  )}
                </div>
              </>
            )}
          </div>
        </div>
      )}
      {tab === "history" && <CodeHistoryPanel groups={groups} />}
      {tab === "maps" && (
        <div role="tabpanel" data-testid="excel-map-panel">
          {excelMaps}
        </div>
      )}
      {tab === "standards" && (
        <div role="tabpanel" data-testid="operating-standard-panel">
          {operatingStandards}
        </div>
      )}
      <WorkDialog
        open={groupOpen}
        title="그룹 추가"
        onClose={() => {
          if (groupBusy) return;
          void (async () => {
            if (
              modalDirty &&
              !(await confirm({ kind: "dirty", screenId: "UI-27" }))
            )
              return;
            setGroupOpen(false);
            setGroupDraft({ ...emptyGroup });
          })();
        }}
        actions={
          <Button variant="primary" busy={groupBusy} onClick={saveGroup}>
            저장
          </Button>
        }
      >
        <div className={styles.form} data-testid="group-dialog">
          {groupError && (
            <p role="alert" className={styles.fieldError}>
              {groupError.field}: {groupError.message} · {groupError.requestId}
            </p>
          )}
          {(
            [
              "cd_grp_id",
              "cd_grp_nm",
              "attr1_nm",
              "attr2_nm",
              "attr3_nm",
            ] as const
          ).map((key, index) => (
            <label key={key}>
              {
                [
                  "그룹 코드",
                  "그룹명",
                  "추가 값 이름1",
                  "추가 값 이름2",
                  "추가 값 이름3",
                ][index]
              }
              <input
                value={groupDraft[key] ?? ""}
                maxLength={
                  key === "cd_grp_id" ? 30 : key === "cd_grp_nm" ? 100 : 50
                }
                disabled={groupBusy}
                data-testid={`group-field-${key.replaceAll("_", "-")}`}
                onChange={(event) =>
                  setGroupDraft({ ...groupDraft, [key]: event.target.value })
                }
              />
              {groupError?.field === key && (
                <span className={styles.fieldError}>{groupError.message}</span>
              )}
            </label>
          ))}
        </div>
      </WorkDialog>
      {dialog}
    </section>
  );
}

export function CodeHistoryPanel({ groups }: { groups: CodeGroupView[] }) {
  const [group, setGroup] = useState("");
  const [page, setPage] = useState(1);
  const [size, setSize] = useState<20 | 50 | 100>(50);
  const [data, setData] = useState<CodeHistoryPage>();
  const [error, setError] = useState<RequestError>();
  const [loading, setLoading] = useState(true);
  const sequence = useRef(0);
  async function load() {
    const id = ++sequence.current;
    setLoading(true);
    try {
      const next = await getCodeHistory(group, page, size);
      if (id === sequence.current) {
        setData(next);
        setError(undefined);
      }
    } catch (cause) {
      if (id === sequence.current) setError(asError(cause));
    } finally {
      if (id === sequence.current) setLoading(false);
    }
  }
  useEffect(() => {
    void load();
    return () => {
      sequence.current++;
    };
  }, [group, page, size]);
  return (
    <section
      className={styles.history}
      role="tabpanel"
      data-testid="history-panel"
    >
      <div className={styles.toolbar}>
        <label>
          그룹{" "}
          <select
            value={group}
            onChange={(event) => {
              setGroup(event.target.value);
              setPage(1);
            }}
          >
            <option value="">전체</option>
            {groups.map((item) => (
              <option key={item.cd_grp_id} value={item.cd_grp_id}>
                {item.cd_grp_nm}
              </option>
            ))}
            <option value="PARAM">기준값</option>
          </select>
        </label>
        <label>
          표시 건수{" "}
          <select
            value={size}
            onChange={(event) => {
              setSize(Number(event.target.value) as 20 | 50 | 100);
              setPage(1);
            }}
          >
            {[20, 50, 100].map((value) => (
              <option key={value} value={value}>
                {value}
              </option>
            ))}
          </select>
        </label>
      </div>
      {error && <ErrorBanner error={error} onRetry={load} loading={loading} />}
      {loading && <p role="status">조회 중입니다.</p>}
      <div className={styles.tableScroll}>
        <table>
          <thead>
            <tr>
              {[
                "변경일시",
                "그룹",
                "코드",
                "구분",
                "항목",
                "변경 전",
                "변경 후",
                "처리자",
              ].map((title) => (
                <th key={title}>{title}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {data?.items.map((row) => (
              <tr
                key={row.cd_hist_id}
                data-testid={`history-row-${row.cd_hist_id}`}
              >
                <td>{row.chg_at.replace("T", " ")}</td>
                <td>{row.cd_grp_nm}</td>
                <td>{row.dtl_cd ?? "–"}</td>
                <td>{row.cd_chg_ty_cd}</td>
                <td>{row.item_nm ?? "–"}</td>
                <td>{row.bfr_val ?? "–"}</td>
                <td>{row.aft_val ?? "–"}</td>
                <td>{processorName(null)}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {!loading && !error && !data?.items.length && (
          <p className={styles.empty}>조건에 맞는 결과가 없습니다</p>
        )}
      </div>
      <div className={styles.actions}>
        <span>
          {data?.total ?? 0}건 · {page}페이지
        </span>
        <Button
          disabled={loading || page === 1}
          onClick={() => setPage(page - 1)}
        >
          이전 페이지
        </Button>
        <Button
          disabled={loading || !data || page * size >= data.total}
          onClick={() => setPage(page + 1)}
        >
          다음 페이지
        </Button>
      </div>
    </section>
  );
}

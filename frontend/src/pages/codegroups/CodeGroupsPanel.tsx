import { useEffect, useRef, useState, type ReactNode } from "react";
import { RequestError } from "../../api/client";
import { Button } from "../../components/Button";
import { WorkDialog } from "../../components/WorkDialog";
import { UnderlineTabs } from "../../components/UnderlineTabs";
import { Can } from "../../permissions/Can";
import { can } from "../../permissions/permissions";
import { useNavigation } from "../shared/navigation";
import { useNavigationConfirmation } from "../shared/UnsavedChangesGuard";
import {
  createCodeGroup,
  getAdminCodes,
  getCodeGroups,
  invalidateCodeChoices,
  saveCodes,
  type AdminCode,
  type CodeGroup,
  type CreateCodeGroup,
} from "./api";
import controls from "../../components/Common.module.css";
import styles from "./CodeGroups.module.css";

const tabs = [
  { id: "codes", label: "코드" },
  { id: "excel-maps", label: "엑셀 표기 대응" },
  { id: "operating-standards", label: "기준값" },
  { id: "code-history", label: "변경 이력" },
];
type OtherTab = "excel-maps" | "operating-standards" | "code-history";
export interface CommonCodeScreenProps {
  panels?: Partial<Record<OtherTab, ReactNode>>;
}
interface Stored {
  selected: string;
  tab: string;
  draft: AdminCode[];
  snapshot: AdminCode[];
}

/** The existing UI-27 shell hosts this desktop body; sibling slices supply their tab panels. */
export function CodeGroupsPanel({ panels = {} }: CommonCodeScreenProps) {
  const saved = useNavigation.getState().screenState["UI-27"] as
    | Stored
    | undefined;
  const [groups, setGroups] = useState<CodeGroup[]>([]);
  const [selected, setSelected] = useState(saved?.selected ?? "");
  const [tab, setTab] = useState(saved?.tab ?? "codes");
  const [draft, setDraft] = useState<AdminCode[]>(saved?.draft ?? []);
  const [snapshot, setSnapshot] = useState<AdminCode[]>(saved?.snapshot ?? []);
  const [loading, setLoading] = useState(true);
  const [groupError, setGroupError] = useState<Error>();
  const [error, setError] = useState<Error>();
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const [committed, setCommitted] = useState(false);
  const [modal, setModal] = useState(false);
  const [groupDraft, setGroupDraft] = useState<CreateCodeGroup>({
    cd_grp_id: "",
    cd_grp_nm: "",
  });
  const [modalError, setModalError] = useState<Error>();
  const { confirm, dialog } = useNavigationConfirmation();
  const generation = useRef(0);
  const restored = useRef(!!saved);
  const dirty = JSON.stringify(draft) !== JSON.stringify(snapshot);
  const group = groups.find((item) => item.cd_grp_id === selected);
  const system = group?.cd_grp_se_cd === "SYSTEM";
  const modalDirty = modal && Object.values(groupDraft).some(Boolean);
  useEffect(() => {
    useNavigation.getState().setDirty("UI-27", dirty || modalDirty);
    useNavigation
      .getState()
      .setScreenState("UI-27", { selected, tab, draft, snapshot });
  }, [dirty, modalDirty, selected, tab, draft, snapshot]);

  async function loadGroups() {
    setGroupError(undefined);
    try {
      const result = await getCodeGroups();
      setGroups(result);
      setSelected((current) => current || result[0]?.cd_grp_id || "");
      if (!result.length) setLoading(false);
    } catch (reason) {
      setGroupError(reason as Error);
      setLoading(false);
    }
  }
  async function loadCodes(id: string) {
    if (!id) return;
    const version = ++generation.current;
    setLoading(true);
    setError(undefined);
    try {
      const result = await getAdminCodes(id);
      if (version !== generation.current) return;
      setDraft(result);
      setSnapshot(result);
      setCommitted(false);
    } catch (reason) {
      if (version === generation.current) setError(reason as Error);
    } finally {
      if (version === generation.current) setLoading(false);
    }
  }
  useEffect(() => {
    void loadGroups();
  }, []);
  useEffect(() => {
    if (!selected) return;
    if (restored.current) {
      restored.current = false;
      setLoading(false);
      return;
    }
    void loadCodes(selected);
    return () => {
      ++generation.current;
    };
  }, [selected]);

  async function leave(action: () => void) {
    if (busy) return;
    if (
      (dirty || modalDirty) &&
      !(await confirm({ kind: "dirty", screenId: "UI-27" }))
    )
      return;
    setDraft(snapshot);
    setModal(false);
    setMessage("");
    action();
  }
  function edit(
    index: number,
    field: keyof AdminCode,
    value: string | number | null,
  ) {
    setDraft((rows) =>
      rows.map((row, i) => (i === index ? { ...row, [field]: value } : row)),
    );
  }
  function add() {
    setDraft((rows) => [
      ...rows,
      {
        cd_grp_id: selected,
        dtl_cd: "",
        cd_nm: "",
        sort_seq: Math.max(0, ...rows.map((row) => row.sort_seq)) + 1,
        attr1_val: null,
        attr2_val: null,
        attr3_val: null,
        use_yn: "Y",
        aply_bgn_dt: null,
      },
    ]);
  }
  async function save() {
    const missing = draft.find(
      (row) => !row.dtl_cd.trim() || !row.cd_nm.trim(),
    );
    if (missing) {
      setError(
        new Error(
          !missing.dtl_cd.trim() ? "코드를 입력하세요." : "이름을 입력하세요.",
        ),
      );
      return;
    }
    setBusy(true);
    setError(undefined);
    try {
      await saveCodes(selected, draft);
      // Commit is final: a failed refresh must never resubmit the command.
      setCommitted(true);
      setSnapshot(draft);
      setMessage("공통코드를 저장했습니다.");
      invalidateCodeChoices(selected);
      await Promise.all([loadCodes(selected), loadGroups()]);
    } catch (reason) {
      setError(reason as Error);
    } finally {
      setBusy(false);
    }
  }
  async function submitGroup() {
    setModalError(undefined);
    setBusy(true);
    try {
      await createCodeGroup(groupDraft);
      setModal(false);
      setMessage("그룹을 추가했습니다.");
      await loadGroups();
    } catch (reason) {
      setModalError(reason as Error);
    } finally {
      setBusy(false);
    }
  }
  const attrs = ["attr1", "attr2", "attr3"] as const;
  function errorText(problem: Error) {
    return problem instanceof RequestError
      ? `${problem.field ? `${problem.field}: ` : ""}${problem.message} (문의 번호: ${problem.requestId})`
      : problem.message;
  }
  return (
    <section className={styles.root} data-testid="common-code-screen">
      <UnderlineTabs
        tabs={tabs}
        value={tab}
        onChange={(value) => void leave(() => setTab(value))}
      />
      {message && (
        <p role="status" className={styles.success}>
          {message}
        </p>
      )}
      {tab === "codes" ? (
        <div className={styles.panes} role="tabpanel" data-testid="code-panel">
          <aside className={styles.groups} aria-label="코드 그룹">
            <Can perm="UI-27.create">
              <Button
                size="small"
                data-testid="group-add"
                disabled={busy}
                onClick={() => {
                  setGroupDraft({ cd_grp_id: "", cd_grp_nm: "" });
                  setModalError(undefined);
                  setModal(true);
                }}
              >
                + 그룹 추가
              </Button>
            </Can>
            {groupError && (
              <div role="alert" className={styles.error}>
                {errorText(groupError)}
                <Button onClick={loadGroups}>그룹 다시 시도</Button>
              </div>
            )}
            {groups.map((item) => (
              <button
                type="button"
                key={item.cd_grp_id}
                data-testid={`group-${item.cd_grp_id}`}
                className={styles.group}
                aria-pressed={selected === item.cd_grp_id}
                disabled={busy}
                onClick={() => leave(() => setSelected(item.cd_grp_id))}
              >
                <span>{item.cd_grp_nm}</span>
                <span>
                  {item.cd_grp_se_cd === "SYSTEM" ? "🔒" : item.code_count}
                </span>
              </button>
            ))}
            {!loading && !groups.length && !groupError && (
              <p>조건에 맞는 결과가 없습니다</p>
            )}
          </aside>
          <div className={styles.details} aria-busy={loading || busy}>
            <div className={styles.toolbar}>
              <h2>
                {group?.cd_grp_nm} <small>{draft.length}개</small>
              </h2>
              <div className={styles.actions}>
                {!system && group && (
                  <Can perm="UI-27.create">
                    <Button
                      size="small"
                      disabled={busy || loading || committed}
                      onClick={add}
                    >
                      + 코드 추가
                    </Button>
                  </Can>
                )}
                <Can perm="UI-27.update">
                  <Button
                    variant="primary"
                    size="small"
                    disabled={!dirty || loading || busy || committed}
                    onClick={save}
                  >
                    저장
                  </Button>
                </Can>
                <Button
                  size="small"
                  disabled={!dirty || busy}
                  onClick={() => {
                    setDraft(snapshot);
                    setError(undefined);
                  }}
                >
                  취소
                </Button>
              </div>
            </div>
            {system && (
              <p className={styles.lock}>
                계산 규칙에 쓰는 시스템 코드입니다. 이름만 바꿀 수 있고
                추가·삭제는 할 수 없습니다.
              </p>
            )}
            {error && (
              <div role="alert" className={styles.error}>
                {errorText(error)}
                {!dirty && (
                  <Button onClick={() => loadCodes(selected)}>다시 시도</Button>
                )}
              </div>
            )}
            {loading && <p role="status">불러오는 중입니다.</p>}
            {!loading && group && !draft.length && !error && (
              <p>조건에 맞는 결과가 없습니다</p>
            )}
            <fieldset
              disabled={busy || loading || committed}
              className={styles.editor}
            >
              <table className={styles.table}>
                <thead>
                  <tr>
                    <th>코드</th>
                    <th>이름</th>
                    <th>순서</th>
                    <th>사용</th>
                    {attrs.map(
                      (attr) =>
                        group?.[`${attr}_nm`] && (
                          <th key={attr}>{group[`${attr}_nm`]}</th>
                        ),
                    )}
                    <th>적용 시작일</th>
                  </tr>
                </thead>
                <tbody>
                  {draft.map((row, index) => (
                    <tr key={index} data-testid={`code-row-${index}`}>
                      <td>
                        <input
                          className={styles.cell}
                          data-testid={`code-id-${index}`}
                          aria-label={`코드 ${index + 1}`}
                          value={row.dtl_cd}
                          readOnly={index < snapshot.length}
                          maxLength={30}
                          onChange={(event) =>
                            edit(index, "dtl_cd", event.target.value)
                          }
                        />
                      </td>
                      <td>
                        <input
                          className={styles.cell}
                          data-testid={`code-name-${index}`}
                          aria-label={`이름 ${index + 1}`}
                          value={row.cd_nm}
                          maxLength={100}
                          onChange={(event) =>
                            edit(index, "cd_nm", event.target.value)
                          }
                        />
                      </td>
                      <td>
                        <input
                          className={`${styles.cell} ${styles.order}`}
                          data-testid={`code-order-${index}`}
                          aria-label={`순서 ${index + 1}`}
                          type="number"
                          value={row.sort_seq}
                          onChange={(event) =>
                            edit(index, "sort_seq", Number(event.target.value))
                          }
                        />
                      </td>
                      <td>
                        <input
                          type="checkbox"
                          data-testid={`code-use-${index}`}
                          aria-label={`사용 ${index + 1}`}
                          checked={row.use_yn === "Y"}
                          disabled={
                            system ||
                            index >= snapshot.length ||
                            !can("UI-27.delete")
                          }
                          onChange={(event) =>
                            edit(
                              index,
                              "use_yn",
                              event.target.checked ? "Y" : "N",
                            )
                          }
                        />
                      </td>
                      {attrs.map(
                        (attr) =>
                          group?.[`${attr}_nm`] && (
                            <td key={attr}>
                              <input
                                className={styles.cell}
                                data-testid={`code-${attr}-${index}`}
                                aria-label={`${group[`${attr}_nm`]} ${index + 1}`}
                                value={row[`${attr}_val`] ?? ""}
                                readOnly={system}
                                maxLength={100}
                                onChange={(event) =>
                                  edit(
                                    index,
                                    `${attr}_val`,
                                    event.target.value || null,
                                  )
                                }
                              />
                            </td>
                          ),
                      )}
                      <td>
                        <input
                          className={styles.cell}
                          data-testid={`code-date-${index}`}
                          aria-label={`적용 시작일 ${index + 1}`}
                          type="date"
                          value={row.aply_bgn_dt ?? ""}
                          readOnly={system}
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
            </fieldset>
          </div>
        </div>
      ) : (
        <div
          role="tabpanel"
          className={styles.other}
          data-testid={`${tab}-panel`}
        >
          {panels[tab as OtherTab] ?? (
            <p role="alert">연결된 화면을 불러올 수 없습니다.</p>
          )}
        </div>
      )}
      <WorkDialog
        open={modal}
        title="그룹 추가"
        compact
        onClose={() => {
          if (!busy) setModal(false);
        }}
        actions={
          <Button variant="primary" busy={busy} onClick={submitGroup}>
            그룹 저장
          </Button>
        }
      >
        <div className={styles.form}>
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
                className={controls.input}
                data-testid={`group-input-${key.replaceAll("_", "-")}`}
                value={groupDraft[key] ?? ""}
                maxLength={
                  key === "cd_grp_id" ? 30 : key === "cd_grp_nm" ? 100 : 50
                }
                disabled={busy}
                onChange={(event) =>
                  setGroupDraft((value) => ({
                    ...value,
                    [key]: event.target.value,
                  }))
                }
              />
              {modalError instanceof RequestError &&
                modalError.field === key && (
                  <span className={styles.error}>{modalError.message}</span>
                )}
            </label>
          ))}
          {modalError && (
            <p role="alert" className={styles.error}>
              {errorText(modalError)}
            </p>
          )}
        </div>
      </WorkDialog>
      {dialog}
    </section>
  );
}

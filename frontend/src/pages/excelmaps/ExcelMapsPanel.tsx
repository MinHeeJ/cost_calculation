import { useEffect, useState } from "react";
import { Button } from "../../components/Button";
import { Table, type TableColumn } from "../../components/Table";
import { WorkDialog } from "../../components/WorkDialog";
import { Can } from "../../permissions/Can";
import { RequestError } from "../../api/client";
import { useRequest } from "../shared/useRequest";
import { useNavigation } from "../shared/navigation";
import {
  createExcelMap,
  getExcelMapGroups,
  getExcelMaps,
  updateExcelMap,
  type CreateExcelMapRequest,
  type ExcelMapView,
  type UpdateExcelMapRequest,
} from "./api";
import { useCodes } from "./useCodes";
import common from "../../components/Common.module.css";
import styles from "./ExcelMaps.module.css";

const empty: CreateExcelMapRequest = {
  excel_lbl_nm: "",
  trns_cd_grp_id: "",
  trns_cd: "",
  map_role_cd: "MAIN",
  rmrk_desc: "",
};

/** /UI-27의 엑셀 대응 하위 탭 본문. 기존 shell의 제목·기간·모바일 정책은 변경하지 않는다. */
export function ExcelMapsPanel() {
  const list = useRequest(getExcelMaps, "excel-maps");
  const [drafts, setDrafts] = useState<Record<number, UpdateExcelMapRequest>>(
    {},
  );
  const [adding, setAdding] = useState(false);
  const [createDirty, setCreateDirty] = useState(false);
  const [error, setError] = useState<Error>();
  const [message, setMessage] = useState("");
  const [saving, setSaving] = useState(false);
  const [needsRefresh, setNeedsRefresh] = useState(false);
  const dirty = Object.keys(drafts).length > 0 || createDirty;
  useEffect(() => {
    useNavigation.getState().setDirty("UI-27", dirty);
    return () => useNavigation.getState().setDirty("UI-27", false);
  }, [dirty]);

  const edit = (row: ExcelMapView, change: UpdateExcelMapRequest) => {
    setError(undefined);
    setDrafts((previous) => {
      const next = { ...previous[row.map_id], ...change };
      for (const key of Object.keys(next) as (keyof UpdateExcelMapRequest)[]) {
        if ((next[key] ?? "") === (row[key] ?? "")) delete next[key];
      }
      const result = { ...previous };
      if (Object.keys(next).length) result[row.map_id] = next;
      else delete result[row.map_id];
      return result;
    });
  };
  const cancel = (id: number) =>
    setDrafts((previous) => {
      const next = { ...previous };
      delete next[id];
      return next;
    });
  const refresh = async () => {
    await list.retry();
    setNeedsRefresh(false);
  };
  async function save(row: ExcelMapView) {
    setSaving(true);
    setError(undefined);
    setMessage("");
    try {
      await updateExcelMap(row.map_id, drafts[row.map_id]);
      cancel(row.map_id);
      setMessage("엑셀 표기 대응을 저장했습니다.");
      setNeedsRefresh(true);
      await list.retry();
    } catch (failure) {
      setError(failure as Error);
    } finally {
      setSaving(false);
    }
  }

  const columns: TableColumn<ExcelMapView>[] = [
    { key: "label", label: "엑셀 표기", render: (row) => row.excel_lbl_nm },
    {
      key: "target",
      label: "바꾸는 값",
      render: (row) => `${row.cd_grp_nm} · ${row.cd_nm}`,
    },
    {
      key: "role",
      label: "주/보조",
      render: (row) => (
        <select
          aria-label={`${row.excel_lbl_nm} 주/보조`}
          className={common.input}
          value={drafts[row.map_id]?.map_role_cd ?? row.map_role_cd}
          disabled={saving || (needsRefresh && !!list.error)}
          onChange={(event) =>
            edit(row, { map_role_cd: event.target.value as "MAIN" | "SUB" })
          }
        >
          <option value="MAIN">주</option>
          <option value="SUB">보조</option>
        </select>
      ),
    },
    {
      key: "memo",
      label: "메모",
      render: (row) => (
        <input
          aria-label={`${row.excel_lbl_nm} 메모`}
          className={common.input}
          maxLength={200}
          disabled={saving || (needsRefresh && !!list.error)}
          value={
            drafts[row.map_id]?.rmrk_desc !== undefined
              ? (drafts[row.map_id].rmrk_desc ?? "")
              : (row.rmrk_desc ?? "")
          }
          onChange={(event) =>
            edit(row, { rmrk_desc: event.target.value || null })
          }
        />
      ),
    },
    {
      key: "use",
      label: "사용",
      render: (row) => (
        <Can perm="UI-27.delete">
          <input
            type="checkbox"
            aria-label={`${row.excel_lbl_nm} 사용`}
            checked={(drafts[row.map_id]?.use_yn ?? row.use_yn) === "Y"}
            disabled={saving || (needsRefresh && !!list.error)}
            onChange={(event) =>
              edit(row, { use_yn: event.target.checked ? "Y" : "N" })
            }
          />
        </Can>
      ),
    },
    {
      key: "actions",
      label: "작업",
      render: (row) => (
        <div className={styles.actions}>
          <Button
            disabled={!drafts[row.map_id] || saving}
            onClick={() => cancel(row.map_id)}
          >
            취소
          </Button>
          <Can perm="UI-27.update">
            <Button
              variant="primary"
              disabled={
                !drafts[row.map_id] || saving || (needsRefresh && !!list.error)
              }
              onClick={() => save(row)}
            >
              저장
            </Button>
          </Can>
        </div>
      ),
    },
  ];
  return (
    <section className={styles.panel} aria-label="엑셀 표기 대응">
      <header className={styles.toolbar}>
        <h2>엑셀 표기 대응</h2>
        <Can perm="UI-27.create">
          <Button
            disabled={saving || adding || (needsRefresh && !!list.error)}
            onClick={() => setAdding(true)}
          >
            + 대응 추가
          </Button>
        </Can>
      </header>
      <p className={styles.help}>
        대응 변경은 다음 업로드부터 적용되며 기존 업무 데이터는 변경되지
        않습니다.
      </p>
      {message && <p role="status">{message}</p>}
      {list.loading && <p role="status">불러오는 중입니다.</p>}
      {list.error && (
        <div role="alert" className={common.errorBanner}>
          {needsRefresh
            ? "저장은 완료되었습니다. 목록 조회만 다시 시도하세요. "
            : ""}
          {list.error.message}
          <Button onClick={refresh}>다시 시도</Button>
        </div>
      )}
      {error && <FieldError error={error} />}
      {list.data && list.data.length === 0 && (
        <p>조건에 맞는 결과가 없습니다</p>
      )}
      {list.data && list.data.length > 0 && (
        <Table
          columns={columns}
          rows={list.data}
          rowKey={(row) => row.map_id}
          total={list.data.length}
        />
      )}
      {adding && (
        <CreateExcelMapDialog
          onDirty={setCreateDirty}
          onClose={() => {
            setAdding(false);
            setCreateDirty(false);
          }}
          onSaved={async () => {
            setAdding(false);
            setCreateDirty(false);
            setMessage("엑셀 표기 대응을 저장했습니다.");
            setNeedsRefresh(true);
            await list.retry();
          }}
        />
      )}
    </section>
  );
}

function FieldError({ error }: { error: Error }) {
  return (
    <p role="alert" className={common.errorBanner}>
      {error instanceof RequestError && error.field ? `${error.field}: ` : ""}
      {error.message}
    </p>
  );
}

function CreateExcelMapDialog({
  onClose,
  onSaved,
  onDirty,
}: {
  onClose: () => void;
  onSaved: () => Promise<void>;
  onDirty: (dirty: boolean) => void;
}) {
  const [form, setForm] = useState<CreateExcelMapRequest>({ ...empty });
  const groups = useRequest(getExcelMapGroups, "excel-map-groups");
  const codes = useCodes(form.trns_cd_grp_id);
  const [error, setError] = useState<Error>();
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    onDirty(JSON.stringify(form) !== JSON.stringify(empty));
  }, [form, onDirty]);
  const field = (change: Partial<CreateExcelMapRequest>) => {
    setError(undefined);
    setForm((previous) => ({ ...previous, ...change }));
  };
  const save = async () => {
    if (!form.excel_lbl_nm.trim()) {
      setError(
        new RequestError(
          "엑셀 글자를 입력하세요.",
          "",
          "client",
          400,
          "excel_lbl_nm",
        ),
      );
      return;
    }
    if (!form.trns_cd_grp_id || !form.trns_cd) {
      setError(
        new RequestError(
          "대응할 코드를 고르세요.",
          "",
          "client",
          400,
          "trns_cd",
        ),
      );
      return;
    }
    setBusy(true);
    setError(undefined);
    try {
      await createExcelMap(form);
    } catch (failure) {
      setError(failure as Error);
      setBusy(false);
      return;
    }
    // 커밋 후의 실패는 GET만 재시도한다. 생성 요청을 다시 보내지 않는다.
    await onSaved();
  };
  return (
    <WorkDialog
      open
      title="엑셀 표기 대응 추가"
      onClose={() => {
        if (!busy) onClose();
      }}
      actions={
        <Button variant="primary" busy={busy} onClick={save}>
          저장
        </Button>
      }
    >
      <div className={styles.form}>
        <label>
          엑셀 표기
          <input
            className={common.input}
            maxLength={100}
            value={form.excel_lbl_nm}
            disabled={busy}
            onChange={(event) => field({ excel_lbl_nm: event.target.value })}
          />
        </label>
        <label>
          그룹
          <select
            className={common.input}
            value={form.trns_cd_grp_id}
            disabled={busy || groups.loading}
            onChange={(event) =>
              field({ trns_cd_grp_id: event.target.value, trns_cd: "" })
            }
          >
            <option value="">그룹 선택</option>
            {groups.data?.map((group) => (
              <option key={group.cd_grp_id} value={group.cd_grp_id}>
                {group.cd_grp_nm}
              </option>
            ))}
          </select>
        </label>
        {groups.loading && <p role="status">그룹을 불러오는 중입니다.</p>}
        {groups.error && (
          <div className={common.errorBanner} role="alert">
            {groups.error.message}
            <Button onClick={groups.retry}>그룹 다시 시도</Button>
          </div>
        )}
        <label>
          코드
          <select
            className={common.input}
            disabled={busy || !form.trns_cd_grp_id || codes.loading}
            value={form.trns_cd}
            onChange={(event) => field({ trns_cd: event.target.value })}
          >
            <option value="">코드 선택</option>
            {codes.data.map((code) => (
              <option key={code.code} value={code.code}>
                {code.name}
              </option>
            ))}
          </select>
        </label>
        {codes.loading && <p role="status">코드를 불러오는 중입니다.</p>}
        {codes.error && (
          <div className={common.errorBanner} role="alert">
            {codes.error.message}
            <Button onClick={codes.retry}>코드 다시 시도</Button>
          </div>
        )}
        {form.trns_cd_grp_id &&
          !codes.loading &&
          !codes.error &&
          !codes.data.length && <p>조건에 맞는 결과가 없습니다</p>}
        <label>
          주/보조
          <select
            className={common.input}
            value={form.map_role_cd}
            disabled={busy}
            onChange={(event) =>
              field({ map_role_cd: event.target.value as "MAIN" | "SUB" })
            }
          >
            <option value="MAIN">주</option>
            <option value="SUB">보조</option>
          </select>
        </label>
        <label>
          메모
          <input
            className={common.input}
            maxLength={200}
            value={form.rmrk_desc ?? ""}
            disabled={busy}
            onChange={(event) => field({ rmrk_desc: event.target.value })}
          />
        </label>
        {error && <FieldError error={error} />}
      </div>
    </WorkDialog>
  );
}

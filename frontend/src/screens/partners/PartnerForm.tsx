import { useEffect, useRef, useState } from "react";
import { Button } from "../../components/Button";
import { ConfirmDialog } from "../../components/ConfirmDialog";
import { RequestError } from "../../api/client";
import { useCodes } from "../../pages/commoncode/useCodes";
import { useNavigation } from "../../pages/shared/navigation";
import { useNavigationConfirmation } from "../../pages/shared/UnsavedChangesGuard";
import {
  partnerDraft,
  partnerPayload,
  partnersApi,
  type PartnerDetail,
  type PartnerDraft,
} from "../../pages/partners/partnersApi";
import styles from "./PartnerSalesPartScreen.module.css";

type Field = keyof PartnerDraft;
const labels: Record<Field, string> = {
  ptnrSeCd: "구분",
  ptnrNm: "상호",
  bizRegNo: "사업자번호",
  ceoNm: "대표자",
  indtyCd: "업종",
  chrgNm: "담당자",
  chrgTelNo: "연락처",
  txinvEmailAddr: "세금계산서 이메일",
  bankCd: "은행",
  acntNo: "계좌번호",
  dpstrNm: "예금주",
};
const limits: Partial<Record<Field, number>> = {
  ptnrNm: 50,
  bizRegNo: 12,
  ceoNm: 20,
  chrgNm: 20,
  chrgTelNo: 20,
  txinvEmailAddr: 100,
  acntNo: 20,
  dpstrNm: 30,
};
const same = (a: unknown, b: unknown) =>
  JSON.stringify(a) === JSON.stringify(b);

export function PartnerForm({
  row,
  onCancel,
  onSaved,
}: {
  row?: PartnerDetail;
  onCancel: () => void;
  onSaved: (row: PartnerDetail) => void;
}) {
  const original = useRef(partnerDraft(row));
  const [draft, setDraft] = useState(original.current);
  const [fields, setFields] = useState<Partial<Record<Field, string>>>({});
  const [error, setError] = useState<RequestError>();
  const [busy, setBusy] = useState(false);
  const running = useRef(false);
  const [accountQuestion, setAccountQuestion] = useState(false);
  const [message, setMessage] = useState("");
  const { confirm, dialog } = useNavigationConfirmation();
  const form = useRef<HTMLFormElement>(null);
  const kinds = useCodes("PARTNER_KIND");
  const industries = useCodes("INDUSTRY");
  const banks = useCodes("BANK");
  const dirty = !same(
    partnerPayload(draft, !!row),
    partnerPayload(original.current, !!row),
  );
  useEffect(() => {
    useNavigation.getState().setDirty("UI-15", dirty);
  }, [dirty]);

  async function cancel() {
    if (dirty && !(await confirm({ kind: "dirty", screenId: "UI-15" }))) return;
    useNavigation.getState().setDirty("UI-15", false);
    onCancel();
  }

  function showErrors(next: Partial<Record<Field, string>>) {
    setFields(next);
    const first = Object.keys(next)[0];
    if (first)
      form.current?.querySelector<HTMLElement>(`[name="${first}"]`)?.focus();
  }

  function validate() {
    const next: Partial<Record<Field, string>> = {};
    const required = (field: Field, message: string) => {
      if (!draft[field].trim()) next[field] = message;
    };
    required("ptnrSeCd", "구분을 골라 주세요.");
    required("ptnrNm", "상호를 입력해 주세요.");
    if (
      !row &&
      !/^([0-9]{10}|[0-9]{3}-[0-9]{2}-[0-9]{5})$/.test(draft.bizRegNo)
    ) {
      next.bizRegNo = "사업자번호 10자리를 입력해 주세요. (예: 123-45-67890)";
    }
    if (draft.ptnrSeCd && draft.ptnrSeCd !== "VEND") {
      required("txinvEmailAddr", "고객사는 세금계산서 이메일이 꼭 필요합니다.");
      if (
        draft.txinvEmailAddr &&
        !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(draft.txinvEmailAddr)
      ) {
        next.txinvEmailAddr =
          "이메일 형식이 맞지 않습니다. (예: tax@company.co.kr)";
      }
    }
    if (draft.ptnrSeCd && draft.ptnrSeCd !== "CUST") {
      required("bankCd", "은행을 골라 주세요.");
      if (!/^[0-9-]{8,20}$/.test(draft.acntNo)) {
        next.acntNo = "계좌번호를 숫자와 하이픈(-)으로 입력해 주세요.";
      }
      required("dpstrNm", "예금주를 입력해 주세요.");
    }
    for (const [field, max] of Object.entries(limits) as [Field, number][]) {
      if (field === "bizRegNo" && row) continue;
      if (
        (field === "txinvEmailAddr" && draft.ptnrSeCd === "VEND") ||
        (["acntNo", "dpstrNm"].includes(field) && draft.ptnrSeCd === "CUST")
      )
        continue;
      if ([...draft[field]].length > max)
        next[field] = `${max}자 이하로 입력해 주세요.`;
    }
    showErrors(next);
    return Object.keys(next).length === 0;
  }

  async function persist() {
    if (running.current) return;
    running.current = true;
    setBusy(true);
    setError(undefined);
    try {
      const saved = row
        ? await partnersApi.update(row.id, draft)
        : await partnersApi.create(draft);
      useNavigation.getState().setDirty("UI-15", false);
      onSaved(saved);
    } catch (failure) {
      const error = failure as RequestError;
      setError(error);
      if (error.field && error.field in labels)
        showErrors({ [error.field]: error.message });
    } finally {
      running.current = false;
      setBusy(false);
      setAccountQuestion(false);
    }
  }

  async function save() {
    if (busy || !validate()) return;
    if (row && !dirty) {
      setMessage("바뀐 내용이 없습니다.");
      return;
    }
    if (
      row?.acntNo &&
      (["bankCd", "acntNo", "dpstrNm"] as Field[]).some(
        (field) => draft[field] !== original.current[field],
      )
    ) {
      setAccountQuestion(true);
      return;
    }
    await persist();
  }

  const update = (field: Field, value: string) => {
    if (field === "bizRegNo" && /^\d{0,10}$/.test(value)) {
      value = value
        .replace(/^(\d{3})(\d)/, "$1-$2")
        .replace(/^(\d{3}-\d{2})(\d)/, "$1-$2");
    }
    setDraft((current) => ({ ...current, [field]: value }));
    setFields((current) => ({ ...current, [field]: undefined }));
  };
  const input = (field: Field, required = false, readonly = false) => {
    const choices =
      field === "ptnrSeCd"
        ? kinds
        : field === "indtyCd"
          ? industries
          : field === "bankCd"
            ? banks
            : null;
    const options = choices?.data ?? [];
    return (
      <div className={styles.field} key={field}>
        <label htmlFor={`partner-${field}`}>
          {labels[field]}
          {required && " *"}
        </label>
        {choices ? (
          <select
            id={`partner-${field}`}
            name={field}
            data-testid={
              field === "ptnrSeCd"
                ? "PTNR-KIND"
                : `partner-${field.toLowerCase()}`
            }
            aria-invalid={!!fields[field]}
            value={draft[field]}
            disabled={busy || choices.loading}
            onChange={(event) => update(field, event.target.value)}
          >
            <option value="">
              {choices.loading ? "불러오는 중…" : "선택"}
            </option>
            {!!draft[field] &&
              !options.some((option) => option.code === draft[field]) && (
                <option value={draft[field]}>{draft[field]}</option>
              )}
            {options.map((option) => (
              <option key={option.code} value={option.code}>
                {option.name}
              </option>
            ))}
          </select>
        ) : (
          <input
            id={`partner-${field}`}
            name={field}
            data-testid={`partner-${field.toLowerCase()}`}
            value={draft[field]}
            readOnly={readonly}
            disabled={busy}
            aria-invalid={!!fields[field]}
            aria-describedby={
              fields[field] ? `partner-error-${field}` : undefined
            }
            maxLength={limits[field]}
            onChange={(event) => update(field, event.target.value)}
          />
        )}
        {fields[field] && (
          <small id={`partner-error-${field}`} className={styles.fieldError}>
            {fields[field]}
          </small>
        )}
        {choices?.error && (
          <div className={styles.fieldError}>
            {choices.error.message} · 문의 번호: {choices.error.requestId}
            <Button onClick={choices.retry}>선택 목록 다시 시도</Button>
          </div>
        )}
      </div>
    );
  };
  return (
    <form
      ref={form}
      className={`${styles.form} ${styles.mutation}`}
      data-testid={row ? "partner-edit-form" : "partner-create-form"}
      onSubmit={(event) => {
        event.preventDefault();
        void save();
      }}
    >
      <header className={styles.toolbar}>
        <h2>{row ? "거래처 수정" : "거래처 등록"}</h2>
        {row && (
          <span>
            {row.no} / {row.useYn === "Y" ? "사용" : "사용 중지"}
          </span>
        )}
      </header>
      {error && (
        <div role="alert" className={styles.error}>
          {error.message} · 문의 번호: {error.requestId}
        </div>
      )}
      {message && <p role="status">{message}</p>}
      <div className={styles.formGrid}>
        {input("ptnrSeCd", true)}
        {input("ptnrNm", true)}
        {input("bizRegNo", !row, !!row)}
        {row && (
          <p className={styles.hint}>
            프로젝트·청구·지급과 연결되어 있어 바꿀 수 없습니다. 잘못 넣었으면
            사용 중지 후 새로 등록하세요.
          </p>
        )}
        {input("ceoNm")}
        {input("indtyCd")}
        {input("chrgNm")}
        {input("chrgTelNo")}
        {!!draft.ptnrSeCd &&
          draft.ptnrSeCd !== "VEND" &&
          input("txinvEmailAddr", true)}
        {!!draft.ptnrSeCd && draft.ptnrSeCd !== "CUST" && (
          <>
            {input("bankCd", true)}
            {input("acntNo", true)}
            {input("dpstrNm", true)}
          </>
        )}
      </div>
      <footer className={styles.actions}>
        <Button
          data-testid={row ? "PTNR-EDIT-CANCEL" : "PTNR-CREATE-CANCEL"}
          disabled={busy}
          onClick={cancel}
        >
          취소
        </Button>
        <Button
          data-testid={row ? "PTNR-EDIT-SAVE" : "PTNR-CREATE-SAVE"}
          variant="primary"
          busy={busy}
          onClick={save}
        >
          저장
        </Button>
      </footer>
      <ConfirmDialog
        open={accountQuestion}
        title="지급 계좌를 바꿀까요?"
        message={`다음 지급부터 새 계좌(${
          banks.data?.find((bank) => bank.code === draft.bankCd)?.name ??
          draft.bankCd
        } …${draft.acntNo.slice(-4)})로 보냅니다. 바뀐 기록이 남습니다.`}
        confirmLabel="바꾸기"
        busy={busy}
        onConfirm={persist}
        onCancel={() => setAccountQuestion(false)}
      />
      {dialog}
    </form>
  );
}

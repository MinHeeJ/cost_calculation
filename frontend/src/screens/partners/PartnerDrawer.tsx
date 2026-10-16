import { useEffect, useRef, useState } from "react";
import { Button } from "../../components/Button";
import { ErrorBanner } from "../../components/ErrorBanner";
import { Can } from "../../permissions/Can";
import { useRead } from "../../pages/commoncode/useCodes";
import { useNavigation } from "../../pages/shared/navigation";
import { useNavigationConfirmation } from "../../pages/shared/UnsavedChangesGuard";
import {
  partnersApi,
  type PartnerDetail,
} from "../../pages/partners/partnersApi";
import { PartnerActivityPanel } from "../../pages/partnersops/PartnerActivityScreen";
import { PartnerStatusActions } from "../../pages/partnersops/PartnerStatusActions";
import { PartnerForm } from "./PartnerForm";
import styles from "./PartnerSalesPartScreen.module.css";

/** The selected partner id drives all reads; assignments/history are supplied by the operations feature. */
export function PartnerDrawer({
  id,
  initialTab,
  mobile,
  onClose,
  onSaved,
  onStatusChange,
}: {
  id: number;
  initialTab: string;
  mobile: boolean;
  onClose: () => void;
  onSaved: () => void;
  onStatusChange: () => void;
}) {
  const detail = useRead(() => partnersApi.detail(id), String(id));
  const [tab, setTab] = useState(initialTab);
  const [editing, setEditing] = useState(false);
  const [revision, setRevision] = useState(0);
  const [message, setMessage] = useState("");
  const { confirm, dialog } = useNavigationConfirmation();
  const drawer = useRef<HTMLElement>(null);
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null;
    drawer.current?.focus();
    return () => previous?.focus();
  }, []);
  async function leave(action: () => void) {
    if (
      useNavigation.getState().dirty["UI-15"] &&
      !(await confirm({ kind: "dirty", screenId: "UI-15" }))
    )
      return;
    useNavigation.getState().setDirty("UI-15", false);
    setEditing(false);
    action();
  }
  async function saved(_row: PartnerDetail) {
    setEditing(false);
    setMessage("저장했습니다.");
    await detail.retry();
    setRevision((value) => value + 1);
    onSaved();
  }
  function statusChanged() {
    setRevision((value) => value + 1);
    void detail.retry();
    onStatusChange();
  }
  const row = detail.data;
  const facts: [string, unknown][] = row
    ? [
        ["거래처 코드", row.no],
        ["사용 여부", row.useYn === "Y" ? "사용" : "사용 중지"],
        ["구분", row.kindName],
        ["상호", row.ptnrNm],
        ["사업자번호", row.bizRegNo],
        ["대표자", row.ceoNm],
        ["업종", row.industryName],
        ["담당자", row.chrgNm],
        ["연락처", row.chrgTelNo],
        ...(row.ptnrSeCd !== "VEND"
          ? [["세금계산서 이메일", row.txinvEmailAddr] as [string, unknown]]
          : []),
        ...(row.ptnrSeCd !== "CUST"
          ? ([
              ["은행", row.bankName],
              ["계좌번호", row.acntNo],
              ["예금주", row.dpstrNm],
            ] as [string, unknown][])
          : []),
      ]
    : [];
  return (
    <div className={styles.backdrop}>
      <aside
        ref={drawer}
        role="dialog"
        aria-modal="true"
        aria-label="거래처 상세"
        tabIndex={-1}
        data-testid="partner-detail-drawer"
        className={`${styles.screen} ${styles.drawer}`}
        onKeyDown={(event) => {
          if (event.key !== "Tab") return;
          const focusable = Array.from(
            drawer.current?.querySelectorAll<HTMLElement>(
              "button:not(:disabled), input:not(:disabled), select:not(:disabled), [tabindex='0']",
            ) ?? [],
          );
          const first = focusable[0];
          const last = focusable[focusable.length - 1];
          if (
            event.shiftKey &&
            (document.activeElement === first ||
              document.activeElement === drawer.current)
          ) {
            event.preventDefault();
            last?.focus();
          } else if (
            !event.shiftKey &&
            (document.activeElement === last ||
              document.activeElement === drawer.current)
          ) {
            event.preventDefault();
            first?.focus();
          }
        }}
      >
        <header className={styles.drawerHeader}>
          <div className={styles.toolbar}>
            <h2>{row?.ptnrNm ?? "거래처 상세"}</h2>
            <div className={styles.actions}>
              {!!row && !detail.error && !detail.loading && !editing && (
                <PartnerStatusActions
                  partner={row}
                  mobile={mobile}
                  onDisabled={(result) => {
                    if (result.disabledCount > 0) {
                      setMessage(
                        `${result.disabledCount}건의 사용을 중지했습니다.`,
                      );
                      statusChanged();
                    }
                  }}
                  onEnabled={() => {
                    setMessage("다시 사용하도록 변경했습니다.");
                    statusChanged();
                  }}
                />
              )}
              {!!row && !mobile && !editing && (
                <Can perm="UI-15.update">
                  <Button
                    data-testid="PTNR-EDIT-OPEN"
                    className={styles.mutation}
                    onClick={() => setEditing(true)}
                  >
                    수정
                  </Button>
                </Can>
              )}
              <Button
                data-testid="PTNR-DRAWER-CLOSE"
                onClick={() => leave(onClose)}
              >
                닫기
              </Button>
            </div>
          </div>
          {row && (
            <p className={styles.hint}>
              {row.kindName} / {row.bizRegNo}
            </p>
          )}
          {row?.useYn === "N" && (
            <p className={styles.disabled}>사용 중지된 거래처입니다.</p>
          )}
          <div
            className={styles.tabs}
            role="tablist"
            aria-label="거래처 상세 탭"
          >
            {[
              ["basic", "기본 정보", "PTNR-BASIC"],
              ["assignments", "투입 내역", "PTNR-ASSIGNMENTS"],
              ["history", "변경 이력", "PTNR-HISTORY"],
            ].map(([key, label, testid]) => (
              <Button
                key={key}
                role="tab"
                aria-selected={tab === key}
                data-testid={testid}
                onClick={() => leave(() => setTab(key))}
              >
                {label}
              </Button>
            ))}
          </div>
        </header>
        <div
          className={styles.drawerBody}
          role="tabpanel"
          data-testid="partner-detail-panel"
        >
          {detail.loading && <p role="status">불러오는 중…</p>}
          {detail.error && (
            <ErrorBanner error={detail.error} onRetry={detail.retry} />
          )}
          {message && (
            <p role="status" className={styles.success}>
              {message}
            </p>
          )}
          {row &&
            (editing && !mobile ? (
              <PartnerForm
                row={row}
                onCancel={() => setEditing(false)}
                onSaved={saved}
              />
            ) : tab === "basic" ? (
              <dl className={styles.facts}>
                {facts.map(([label, value]) => (
                  <div key={label}>
                    <dt>{label}</dt>
                    <dd>
                      {value == null || value === "" ? "–" : String(value)}
                    </dd>
                  </div>
                ))}
              </dl>
            ) : (
              <PartnerActivityPanel
                partnerId={id}
                tab={tab === "assignments" ? "assignments" : "history"}
                revision={revision}
              />
            ))}
        </div>
        {dialog}
      </aside>
    </div>
  );
}

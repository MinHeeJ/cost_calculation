import { useRef, useState } from "react";
import { RequestError } from "../../api/client";
import { Button } from "../../components/Button";
import { WorkDialog } from "../../components/WorkDialog";
import { can } from "../../permissions/permissions";
import {
  disablePartners,
  enablePartner,
  partnerExclusions,
  type PartnerDisableResult,
  type PartnerExclusion,
  type PartnerStatusDetail,
} from "./api";
import styles from "./PartnerActivity.module.css";

export interface PartnerSelection {
  id: number;
  ptnrNm: string;
}

/** The list supplies selected rows; the detail supplies its actual partner. Neither invents IDs or routes. */
export function PartnerStatusActions({
  selected = [],
  partner,
  mobile = false,
  onDisabled,
  onEnabled,
}: {
  selected?: PartnerSelection[];
  partner?: PartnerSelection & { useYn: "Y" | "N" };
  mobile?: boolean;
  onDisabled: (result: PartnerDisableResult) => void;
  onEnabled?: (detail: PartnerStatusDetail) => void;
}) {
  const [more, setMore] = useState(false);
  const [targets, setTargets] = useState<PartnerSelection[] | null>(null);
  const [blocked, setBlocked] = useState<PartnerExclusion[] | null>(null);
  const [message, setMessage] = useState("");
  const [error, setError] = useState<RequestError | null>(null);
  const [busy, setBusy] = useState(false);
  const running = useRef(false);
  const candidates = partner ? [partner] : selected;

  function failure(value: unknown) {
    setError(
      value instanceof RequestError
        ? value
        : new RequestError(
            "응답을 확인할 수 없습니다.",
            crypto.randomUUID(),
            "client",
            null,
          ),
    );
  }

  async function disable() {
    if (!targets?.length || running.current) return;
    running.current = true;
    setBusy(true);
    setError(null);
    try {
      const result = await disablePartners(targets.map((row) => row.id));
      setTargets(null);
      setMore(false);
      if (result.disabledCount === 0 && result.inUseCount > 0) {
        setBlocked(result.excluded);
      } else if (result.disabledCount === 0) {
        setMessage("이미 사용 중지된 거래처입니다.");
      } else {
        let text = `${result.disabledCount}건의 사용을 중지했습니다.`;
        if (result.inUseCount)
          text += ` (${result.inUseCount}건은 거래가 남아 제외)`;
        if (result.alreadyDisabledCount)
          text += ` (${result.alreadyDisabledCount}건은 이미 중지되어 제외)`;
        setMessage(text);
      }
      onDisabled(result);
    } catch (value) {
      const exclusions = partnerExclusions(value);
      if (exclusions) {
        setTargets(null);
        setBlocked(exclusions);
      } else {
        failure(value);
      }
    } finally {
      running.current = false;
      setBusy(false);
    }
  }

  async function enable() {
    if (!partner || partner.useYn !== "N" || running.current) return;
    running.current = true;
    setBusy(true);
    setError(null);
    try {
      const detail = await enablePartner(partner.id);
      setMore(false);
      setMessage("다시 사용하도록 변경했습니다.");
      onEnabled?.(detail);
    } catch (value) {
      failure(value);
    } finally {
      running.current = false;
      setBusy(false);
    }
  }

  return (
    <section className={styles.screen} data-testid="partner-status-actions">
      {!mobile && can("UI-15.delete") && (
        <div className={styles.mutations}>
          {partner && (
            <Button
              data-testid="PTNR-MORE"
              aria-label="거래처 추가 작업"
              aria-expanded={more}
              disabled={busy}
              onClick={() => setMore(!more)}
            >
              ⋯
            </Button>
          )}
          {(!partner || more) &&
            (partner?.useYn === "N" ? (
              <Button data-testid="PTNR-ENABLE" busy={busy} onClick={enable}>
                다시 사용
              </Button>
            ) : (
              <Button
                data-testid="PTNR-DISABLE-OPEN"
                disabled={!candidates.length || busy}
                onClick={() => {
                  setTargets([...candidates]);
                  setMessage("");
                  setError(null);
                }}
              >
                사용 중지
              </Button>
            ))}
        </div>
      )}
      {message && (
        <p
          className={styles.notice}
          role="status"
          data-testid="partner-status-message"
        >
          {message}
        </p>
      )}
      {error && !targets && <MutationError error={error} />}
      <WorkDialog
        open={targets !== null}
        title="거래처 사용을 중지할까요?"
        compact
        onClose={() => {
          if (!busy) setTargets(null);
        }}
        actions={
          <Button
            data-testid="PTNR-DISABLE-CONFIRM"
            variant="danger"
            busy={busy}
            onClick={disable}
          >
            사용 중지
          </Button>
        }
      >
        <div
          className={styles.dialogContent}
          data-testid="partner-disable-dialog"
        >
          <p>{targets?.length ?? 0}건을 선택했습니다.</p>
          <ul>
            {targets?.map((row) => (
              <li key={row.id} data-testid={`partner-disable-target-${row.id}`}>
                {row.ptnrNm}
              </li>
            ))}
          </ul>
          <p>
            중지하면 새 프로젝트·지급에서 고를 수 없습니다. 기록은 그대로
            남습니다.
          </p>
          {error && <MutationError error={error} />}
        </div>
      </WorkDialog>
      <WorkDialog
        open={blocked !== null}
        title="사용 중지할 수 없습니다."
        compact
        cancelLabel="확인"
        onClose={() => setBlocked(null)}
      >
        <ul
          className={styles.dialogContent}
          data-testid="partner-blocked-dialog"
        >
          {blocked?.map((row) => (
            <li key={row.id} data-testid={`partner-exclusion-${row.id}`}>
              <strong>{row.name}</strong>: {row.reason}
            </li>
          ))}
        </ul>
      </WorkDialog>
    </section>
  );
}

/** Mutation retries remain explicit submit actions, not a read-retry button that could replay writes. */
function MutationError({ error }: { error: RequestError }) {
  return (
    <div
      className={styles.error}
      role="alert"
      data-testid="partner-mutation-error"
    >
      <p>{error.message}</p>
      <p>
        {error.source === "server" ? "문의 번호" : "클라이언트 문의 번호"}:{" "}
        {error.requestId}
      </p>
    </div>
  );
}

import { useEffect, useState } from "react";
import { Button } from "../../components/Button";
import { ErrorBanner } from "../../components/ErrorBanner";
import { Can } from "../../permissions/Can";
import { useCodes, useRead } from "../../pages/commoncode/useCodes";
import { useNavigation } from "../../pages/shared/navigation";
import { useNavigationConfirmation } from "../../pages/shared/UnsavedChangesGuard";
import {
  initialPartnerQuery,
  partnersApi,
  type PartnerDetail,
  type PartnerListItem,
  type PartnerQuery,
} from "../../pages/partners/partnersApi";
import { PartnerStatusActions } from "../../pages/partnersops/PartnerStatusActions";
import { PartnerForm } from "./PartnerForm";
import { PartnerDrawer } from "./PartnerDrawer";
import styles from "./PartnerSalesPartScreen.module.css";

const columns: [string, string][] = [
  ["ptnr_se_cd", "구분"],
  ["ptnr_no", "거래처 코드"],
  ["ptnr_nm", "상호"],
  ["biz_reg_no", "사업자번호"],
  ["ceo_nm", "대표자"],
  ["indty_cd", "업종"],
  ["chrg_nm", "담당자"],
  ["chrg_tel_no", "연락처"],
  ["txinv_email_addr", "세금계산서 이메일"],
  ["bank_account_display", "지급 계좌"],
  ["ongoing_project_count", "진행 프로젝트"],
  ["current_developer_count", "투입 인력"],
  ["use_yn", "사용 여부"],
];

/** Existing UI-15 shell hosts all partner modes; no extra product route is introduced. */
export function PartnerSalesPartScreen() {
  const restored = useNavigation.getState().screenState["UI-15"] as
    | PartnerQuery
    | undefined;
  const [query, setQuery] = useState<PartnerQuery>(
    restored ?? initialPartnerQuery,
  );
  const [filters, setFilters] = useState(query);
  const [tab, setTab] = useState("partners");
  const [creating, setCreating] = useState(false);
  const [selectedIds, setSelectedIds] = useState<number[]>([]);
  const [selection, setSelection] = useState<{ id: number; tab: string }>();
  const [mobile, setMobile] = useState(window.innerWidth <= 767);
  const [highlight, setHighlight] = useState<PartnerListItem>();
  const [message, setMessage] = useState("");
  const kinds = useCodes("PARTNER_KIND");
  const result = useRead(() => partnersApi.list(query), JSON.stringify(query));
  const { confirm, dialog } = useNavigationConfirmation();
  useEffect(() => {
    useNavigation.getState().setScreenState("UI-15", query);
  }, [query]);
  useEffect(() => {
    const resize = () => setMobile(window.innerWidth <= 767);
    window.addEventListener("resize", resize);
    return () => window.removeEventListener("resize", resize);
  }, []);

  async function leave(action: () => void) {
    if (
      useNavigation.getState().dirty["UI-15"] &&
      !(await confirm({ kind: "dirty", screenId: "UI-15" }))
    )
      return;
    useNavigation.getState().setDirty("UI-15", false);
    setCreating(false);
    action();
  }

  function search(next: PartnerQuery) {
    setSelectedIds([]);
    setHighlight(undefined);
    setFilters(next);
    if (JSON.stringify(next) === JSON.stringify(query)) void result.retry();
    else setQuery(next);
  }

  async function created(row: PartnerDetail) {
    setCreating(false);
    setMessage("저장했습니다.");
    await result.retry();
    // Read the real list DTO instead of fabricating aggregate counts from the create response.
    const saved = await partnersApi.list({
      ...initialPartnerQuery,
      useYn: "ALL",
      q: row.bizRegNo,
    });
    setHighlight(saved.items.find((item) => item.id === row.id));
  }

  const rows = highlight
    ? [
        highlight,
        ...(result.data?.items ?? []).filter(
          (item) => item.id !== highlight.id,
        ),
      ]
    : (result.data?.items ?? []);
  const selected = rows.filter((row) => selectedIds.includes(row.id));
  const pageSelected =
    rows.length > 0 && rows.every((row) => selectedIds.includes(row.id));
  function refreshStatus() {
    setHighlight(undefined);
    setSelectedIds([]);
    void result.retry();
  }
  const show = (id: number, tab = "basic") => setSelection({ id, tab });
  const count = (
    row: PartnerListItem,
    value: number,
    suffix: string,
    action: string,
  ) =>
    value > 0 ? (
      <button
        type="button"
        className={styles.link}
        data-testid={`${action}-${row.id}`}
        onClick={(event) => {
          event.stopPropagation();
          show(row.id, "assignments");
        }}
      >
        {value}
        {suffix}
      </button>
    ) : (
      <span>
        {value}
        {suffix}
      </span>
    );

  return (
    <Can perm="UI-15.read">
      <section
        className={styles.screen}
        data-testid="partner-sales-part-screen"
      >
        <div className={styles.tabs} role="tablist" aria-label="기준정보">
          <Button
            role="tab"
            aria-selected={tab === "partners"}
            data-testid="PTNR-TAB"
            onClick={() => leave(() => setTab("partners"))}
          >
            거래처
          </Button>
          <Button
            role="tab"
            aria-selected={tab === "sales"}
            data-testid="PTNR-SALES-TAB"
            onClick={() =>
              leave(() => {
                setSelection(undefined);
                setTab("sales");
              })
            }
          >
            영업 파트
          </Button>
        </div>
        <div role="tabpanel" data-testid="partner-tab-panel">
          {tab === "sales" ? (
            <p className={styles.empty}>이 화면은 다음 단계에서 만듭니다.</p>
          ) : creating && !mobile ? (
            <PartnerForm
              onCancel={() => setCreating(false)}
              onSaved={created}
            />
          ) : (
            <div className={styles.stack}>
              <form
                className={styles.filters}
                onSubmit={(event) => {
                  event.preventDefault();
                  search({ ...filters, page: 1 });
                }}
              >
                <label>
                  구분
                  <select
                    aria-label="조회 구분"
                    data-testid="partner-filter-kind"
                    value={filters.kind}
                    onChange={(event) =>
                      search({ ...filters, kind: event.target.value, page: 1 })
                    }
                  >
                    <option value="">전체</option>
                    {kinds.data?.map((option) => (
                      <option key={option.code} value={option.code}>
                        {option.name}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  사용 여부
                  <select
                    aria-label="조회 사용 여부"
                    data-testid="partner-filter-use"
                    value={filters.useYn}
                    onChange={(event) =>
                      search({ ...filters, useYn: event.target.value, page: 1 })
                    }
                  >
                    <option value="Y">사용</option>
                    <option value="N">사용 중지</option>
                    <option value="ALL">전체</option>
                  </select>
                </label>
                <label>
                  상호·사업자번호
                  <input
                    data-testid="partner-filter-query"
                    value={filters.q}
                    onChange={(event) =>
                      setFilters({ ...filters, q: event.target.value })
                    }
                  />
                </label>
                <Button
                  data-testid="PTNR-QUERY"
                  busy={result.loading}
                  onClick={() => search({ ...filters, page: 1 })}
                >
                  조회
                </Button>
                <Button
                  data-testid="PTNR-RESET"
                  onClick={() => search({ ...initialPartnerQuery })}
                >
                  조건 초기화
                </Button>
              </form>
              {kinds.error && (
                <ErrorBanner error={kinds.error} onRetry={kinds.retry} />
              )}
              <div className={styles.toolbar}>
                <span>총 {result.data?.total ?? "–"}건</span>
                {!selection && (
                  <PartnerStatusActions
                    selected={selected}
                    mobile={mobile}
                    onDisabled={refreshStatus}
                  />
                )}
                {!mobile && (
                  <Can perm="UI-15.create">
                    <Button
                      variant="primary"
                      className={styles.mutation}
                      data-testid="PTNR-CREATE-OPEN"
                      onClick={() => {
                        setMessage("");
                        setCreating(true);
                        setSelection(undefined);
                      }}
                    >
                      + 등록
                    </Button>
                  </Can>
                )}
              </div>
              {message && (
                <p role="status" className={styles.success}>
                  {message}
                </p>
              )}
              {result.error && (
                <ErrorBanner error={result.error} onRetry={result.retry} />
              )}
              {result.loading && <p role="status">불러오는 중…</p>}
              {!result.loading &&
                !result.error &&
                result.data?.total === 0 &&
                !highlight && (
                  <div className={styles.empty}>
                    조건에 맞는 결과가 없습니다.
                  </div>
                )}
              <div className={styles.tableScroll}>
                <table aria-label="거래처 목록">
                  <thead>
                    <tr>
                      {!mobile && (
                        <th>
                          <input
                            type="checkbox"
                            aria-label="현재 쪽 전체 선택"
                            data-testid="PTNR-SELECT-PAGE"
                            checked={pageSelected}
                            disabled={
                              !rows.length || result.loading || !!result.error
                            }
                            onChange={(event) =>
                              setSelectedIds(
                                event.target.checked
                                  ? rows.map((row) => row.id)
                                  : [],
                              )
                            }
                          />
                        </th>
                      )}
                      {columns.map(([key, label]) => (
                        <th
                          key={key}
                          aria-sort={
                            query.sort.startsWith(`${key},`)
                              ? query.sort.endsWith(",asc")
                                ? "ascending"
                                : "descending"
                              : "none"
                          }
                        >
                          <button
                            type="button"
                            data-testid={`PTNR-SORT-${key}`}
                            onClick={() =>
                              search({
                                ...query,
                                page: 1,
                                sort: `${key},${query.sort === `${key},asc` ? "desc" : "asc"}`,
                              })
                            }
                          >
                            {label}
                            {query.sort.startsWith(`${key},`)
                              ? query.sort.endsWith("asc")
                                ? " ↑"
                                : " ↓"
                              : ""}
                          </button>
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {rows.map((row) => (
                      <tr
                        key={row.id}
                        tabIndex={0}
                        data-testid={`PTNR-ROW-${row.id}`}
                        className={
                          highlight?.id === row.id
                            ? styles.highlight
                            : undefined
                        }
                        onClick={() => show(row.id)}
                        onKeyDown={(event) => {
                          if (
                            event.key === "Enter" &&
                            event.target === event.currentTarget
                          )
                            show(row.id);
                        }}
                      >
                        {!mobile && (
                          <td>
                            <input
                              type="checkbox"
                              aria-label={`${row.ptnrNm} 선택`}
                              data-testid={`PTNR-SELECT-${row.id}`}
                              checked={selectedIds.includes(row.id)}
                              disabled={result.loading || !!result.error}
                              onClick={(event) => event.stopPropagation()}
                              onChange={(event) =>
                                setSelectedIds((ids) =>
                                  event.target.checked
                                    ? [...ids, row.id]
                                    : ids.filter((id) => id !== row.id),
                                )
                              }
                            />
                          </td>
                        )}
                        <td>
                          <span className={styles.badge}>{row.kindName}</span>
                        </td>
                        <td>{row.no}</td>
                        <td>{row.ptnrNm}</td>
                        <td>{row.bizRegNo}</td>
                        <td>{row.ceoNm ?? "–"}</td>
                        <td>{row.industryName ?? "–"}</td>
                        <td>{row.chrgNm ?? "–"}</td>
                        <td>{row.chrgTelNo ?? "–"}</td>
                        <td>{row.txinvEmailAddr ?? "–"}</td>
                        <td>{row.paymentAccountSummary ?? "–"}</td>
                        <td>
                          {count(
                            row,
                            row.ongoingProjectCount,
                            "건",
                            "PTNR-PROJECT-COUNT",
                          )}
                        </td>
                        <td>
                          {count(
                            row,
                            row.assignedDeveloperCount,
                            "명",
                            "PTNR-PEOPLE-COUNT",
                          )}
                        </td>
                        <td>
                          <span className={styles.badge}>
                            {row.useYn === "Y" ? "사용" : "사용 중지"}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <footer className={styles.toolbar}>
                <span>
                  {query.page}쪽 / {result.data?.total ?? "–"}건
                </span>
                <div className={styles.actions}>
                  <label>
                    표시 건수
                    <select
                      data-testid="partner-page-size"
                      value={query.size}
                      onChange={(event) =>
                        search({
                          ...query,
                          size: Number(event.target.value),
                          page: 1,
                        })
                      }
                    >
                      {[20, 50, 100].map((size) => (
                        <option key={size} value={size}>
                          {size}건
                        </option>
                      ))}
                    </select>
                  </label>
                  <Button
                    data-testid="PTNR-PREV"
                    disabled={query.page === 1 || result.loading}
                    onClick={() => search({ ...query, page: query.page - 1 })}
                  >
                    이전
                  </Button>
                  <Button
                    data-testid="PTNR-NEXT"
                    disabled={
                      !result.data ||
                      query.page * query.size >= result.data.total ||
                      result.loading
                    }
                    onClick={() => search({ ...query, page: query.page + 1 })}
                  >
                    다음
                  </Button>
                </div>
              </footer>
            </div>
          )}
        </div>
        {selection && (
          <PartnerDrawer
            key={selection.id}
            id={selection.id}
            initialTab={selection.tab}
            mobile={mobile}
            onClose={() => setSelection(undefined)}
            onSaved={() => {
              setMessage("저장했습니다.");
              void result.retry();
            }}
            onStatusChange={refreshStatus}
          />
        )}
        {dialog}
      </section>
    </Can>
  );
}

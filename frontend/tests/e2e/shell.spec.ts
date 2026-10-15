import { test, expect } from "@playwright/test";
import { screenRegistry } from "../../src/pages/shared/registry";
import type { MenuNode } from "../../src/api/types";

for (const width of [1440, 1280, 1279, 1024, 768, 767, 375, 360]) {
  test(`공통 shell ${width}px는 문서를 가로로 넘치지 않는다`, async ({
    page,
  }) => {
    await page.setViewportSize({ width, height: 900 });
    await page.goto("/");
    const brand = page.getByRole("link", { name: "프로젝트·원가 관리" });
    await expect(brand).toBeVisible();
    await expect(
      page.getByRole("heading", { name: "대시보드", exact: true }),
    ).toBeVisible();
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= window.innerWidth,
      ),
    ).toBe(true);
    expect(
      await page
        .getByRole("banner")
        .filter({ has: brand })
        .evaluate((element) => element.getBoundingClientRect().height),
    ).toBe(56);
    if (width <= 767) {
      const toggle = page.getByRole("button", { name: "메뉴 열기" });
      expect(
        await toggle.evaluate(
          (element) => element.getBoundingClientRect().height,
        ),
      ).toBeGreaterThanOrEqual(44);
      await toggle.click();
      await expect(page.getByRole("complementary")).toBeInViewport();
    } else {
      await expect(
        page.getByRole("tablist", { name: "열린 화면" }),
      ).toBeVisible();
    }
  });
}

test("태블릿 메뉴는 헤더와 하단 접기 버튼을 함께 사용한다", async ({
  page,
}) => {
  await page.setViewportSize({ width: 1024, height: 900 });
  await page.goto("/UI-16");
  const aside = page.getByRole("complementary");
  await expect(
    page.getByRole("heading", { name: "대시보드", exact: true }),
  ).toBeVisible();
  expect(
    await aside.evaluate((element) => element.getBoundingClientRect().width),
  ).toBe(64);
  await page.getByRole("button", { name: "메뉴 열기" }).click();
  expect(
    await aside.evaluate((element) => element.getBoundingClientRect().width),
  ).toBe(240);
  await aside.getByRole("button", { name: "메뉴 접기", exact: true }).click();
  expect(
    await aside.evaluate((element) => element.getBoundingClientRect().width),
  ).toBe(64);
});

test("실제 DB 메뉴의 31개 route는 직접 진입과 새로고침을 지원한다", async ({
  page,
  request,
}) => {
  const response = await request.get("/api/menus");
  expect(response.ok()).toBe(true);
  const body = await response.json();
  const flatten = (nodes: MenuNode[]): MenuNode[] =>
    nodes.flatMap((node) => [node, ...flatten(node.children)]);
  const leaves = flatten(body.data).filter((node) => node.screenId);
  expect(leaves).toHaveLength(screenRegistry.length);
  const errors: string[] = [];
  page.on("pageerror", (error) => errors.push(error.message));
  await page.setViewportSize({ width: 1440, height: 900 });
  for (const node of leaves) {
    await page.goto(`/${node.screenId}`);
    await expect(
      page.getByRole("heading", { name: node.name, exact: true }),
    ).toBeVisible();
    await expect(
      page.getByText("이 화면은 다음 단계에서 만듭니다."),
    ).toBeVisible();
    await expect(
      page.getByRole("button", { name: /저장|삭제|등록/ }),
    ).toHaveCount(0);
  }
  await page.reload();
  await expect(
    page.getByRole("heading", { name: leaves.at(-1)!.name, exact: true }),
  ).toBeVisible();
  expect(errors).toEqual([]);
});

test("메뉴 탐색·즐겨찾기·최근·탭과 실제 월 마감 조회가 연결된다", async ({
  page,
}) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto("/UI-03");
  await expect(
    page.getByRole("heading", { name: "개인 원가·수익표", exact: true }),
  ).toBeVisible();
  await page.getByRole("button", { name: "즐겨찾기 추가" }).click();
  await expect(
    page
      .getByRole("region", { name: "즐겨찾기", exact: true })
      .getByRole("link", { name: "개인 원가·수익표" }),
  ).toBeVisible();
  const nav = page.getByRole("navigation", { name: "화면 메뉴" });
  await nav
    .getByRole("link", { name: "영업 파트 이익금", exact: true })
    .click();
  await expect(page).toHaveURL(/\/UI-19$/);
  await page.getByLabel("선택 연월").fill("2026-09");
  await expect(page.getByText("마감", { exact: true })).toBeVisible();
  await expect(page.getByText(/2026-09는 마감되었습니다/)).toBeVisible();
  await page.getByRole("button", { name: "다음 월" }).click();
  await expect(page.getByText("작업중", { exact: true })).toBeVisible();
  await page
    .getByRole("region", { name: "최근 본 화면", exact: true })
    .getByRole("link", { name: "개인 원가·수익표" })
    .click();
  await expect(page).toHaveURL(/\/UI-03$/);
  await page
    .getByRole("tablist")
    .getByRole("link", { name: "영업 파트 이익금" })
    .click();
  await expect(page.getByLabel("선택 연월")).toHaveValue("2026-10");
  await page.reload();
  await expect(
    page
      .getByRole("region", { name: "즐겨찾기", exact: true })
      .getByRole("link", { name: "개인 원가·수익표" }),
  ).toBeVisible();
});

test("모바일 메뉴는 이동 후 닫히며 PC 전용 화면은 기기 안내를 제공한다", async ({
  page,
}) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await page.goto("/UI-16");
  await expect(
    page.getByRole("heading", { name: "대시보드", exact: true }),
  ).toBeVisible();
  await page.getByRole("button", { name: "메뉴 열기" }).click();
  const nav = page.getByRole("navigation", { name: "화면 메뉴" });
  await nav.getByRole("button", { name: "원가·수익", exact: true }).click();
  await nav
    .getByRole("link", { name: "개인 원가·수익표", exact: true })
    .click();
  await expect(page).toHaveURL(/\/UI-03$/);
  await expect(page.getByRole("complementary")).not.toBeInViewport();
  await page.goto("/UI-14");
  await expect(
    page.getByRole("heading", { name: "PC에서 이용해 주세요" }),
  ).toBeVisible();
  await expect(page.getByLabel("선택 연월")).toHaveCount(0);
  await page.getByRole("button", { name: "대시보드로" }).click();
  await expect(page).toHaveURL(/\/UI-16$/);
});

test("네트워크 실패는 문의 번호와 수동 재시도를 제공한다", async ({ page }) => {
  await page.route("**/api/menus", (route) => route.abort("failed"));
  await page.goto("/UI-16");
  const main = page.getByRole("main");
  await expect(main.getByText(/문의 번호/)).toBeVisible();
  await page.unroute("**/api/menus");
  await main.getByRole("button", { name: "다시 시도" }).click();
  await expect(
    page.getByRole("heading", { name: "대시보드", exact: true }),
  ).toBeVisible();
});

const task = await taskSpace("Stage 4 final accessibility and copy review");
const page = task.page("p1");
const fs = await import("node:fs/promises");
const source = await fs.readFile(
  "D:/ProjectsX/stu_system/src/test/java/edu/stusystem/service/Stage4AccountSecurityMySqlTest.java",
  "utf8",
);
const fixture = source.match(/insertAccount\("stage4-admin", "admin", "ADMIN", "([^"]+)"\)/);
if (!fixture) throw new Error("Isolated administrator fixture was not found.");
const evidence = "D:/ProjectsX/stu_system/docs/evidence/stage4/ego";

await page.cdp("Emulation.setDeviceMetricsOverride", { width: 1440, height: 900, deviceScaleFactor: 1, mobile: false });
await page.goto("http://127.0.0.1:5175/");
await page.fill('.login-form input[autocomplete="username"]', "admin");
await page.fill('.login-form input[autocomplete="current-password"]', fixture[1]);
await page.click(".login-submit", { label: "sign in for final visual review" });
await page.waitForFunction(() => !!document.querySelector(".sidebar"), undefined, { timeout: 15_000 });

await page.click("loc=role:button[name='账户管理']", { label: "review final account heading" });
await page.waitForFunction(() => document.body.innerText.includes("5 条记录"));
if (await page.evaluate(() => !!document.querySelector(".page-heading .subheading"))) {
  throw new Error("Account workspace still has a redundant subtitle.");
}
await page.screenshot({ path: `${evidence}/final-copy-real-api-accounts-1440x900.png` });

await page.click("loc=role:button[name='成绩记录']", { label: "review final grade heading" });
await page.waitForFunction(() => !!document.querySelector(".page-heading h1"));
if (await page.evaluate(() => !!document.querySelector(".page-heading .subheading"))) {
  throw new Error("Grade workspace still has a redundant subtitle.");
}
await page.screenshot({ path: `${evidence}/final-copy-real-api-grades-1440x900.png` });

await page.click("loc=role:button[name='个人中心']", { label: "review labeled profile controls" });
await page.waitForFunction(() => document.querySelector('input[aria-label="用户名"]') && !document.querySelector(".error-banner"));
await page.focus('input[aria-label="用户名"]');
await page.keyboard.press("Tab");
const focus = await page.evaluate(() => ({
  tag: document.activeElement?.tagName,
  label: document.activeElement?.getAttribute("aria-label"),
  text: document.activeElement?.textContent?.trim(),
  focusVisible: document.activeElement?.matches(":focus-visible"),
}));
if (focus.tag !== "BUTTON" || !focus.focusVisible) throw new Error("Keyboard tab focus is not visible on the next profile action.");
await page.cdp("Emulation.setDeviceMetricsOverride", { width: 1280, height: 800, deviceScaleFactor: 1, mobile: false });
await page.screenshot({ path: `${evidence}/final-copy-real-api-profile-1280x800.png` });
console.log(JSON.stringify({ keyboardFocus: focus, labeledProfileInputs: await page.evaluate(() => [...document.querySelectorAll(".profile-grid input, .password-grid input")].map((input) => input.getAttribute("aria-label"))), screenshots: "final-copy-real-api-*" }));
console.log(await page.snapshot());
await task.finish({ keep: [] });

const task = await taskSpace(6);
const page = task.page("p1");
const evidence = "D:/ProjectsX/stu_system/docs/evidence/stage4/ego";

await page.cdp("Emulation.setDeviceMetricsOverride", { width: 1440, height: 900, deviceScaleFactor: 1, mobile: false });
await page.click("loc=role:button[name='账户管理']", { label: "check account workspace title" });
await page.waitForFunction(() => document.body.innerText.includes("5 条记录"), undefined, { timeout: 10_000 });
const accountSubtitleVisible = await page.evaluate(() => !!document.querySelector(".page-heading .subheading"));
await page.screenshot({ path: `${evidence}/final-copy-real-api-accounts-1440x900.png` });

await page.click("loc=role:button[name='成绩记录']", { label: "check grade workspace title" });
await page.waitForFunction(() => !!document.querySelector(".page-heading h1"));
const gradeSubtitleVisible = await page.evaluate(() => !!document.querySelector(".page-heading .subheading"));

await page.click("loc=role:button[name='个人中心']", { label: "check accessible profile fields" });
await page.waitForFunction(() => !!document.querySelector('input[aria-label="用户名"]') && !document.querySelector(".error-banner"));
const labels = await page.evaluate(() => [...document.querySelectorAll(".profile-grid input, .password-grid input")].map((input) => input.getAttribute("aria-label")));
await page.focus('input[aria-label="用户名"]');
await page.keyboard.press("Tab");
const focus = await page.evaluate(() => ({
  tag: document.activeElement?.tagName,
  text: document.activeElement?.textContent?.trim(),
  focusVisible: document.activeElement?.matches(":focus-visible"),
}));
await page.cdp("Emulation.setDeviceMetricsOverride", { width: 1280, height: 800, deviceScaleFactor: 1, mobile: false });
const profileScreenshot = await page.screenshot({ path: `${evidence}/final-copy-real-api-profile-1280x800.png` });
if (accountSubtitleVisible || gradeSubtitleVisible || !focus.focusVisible || labels.some((label) => !label)) {
  throw new Error("Final heading or keyboard accessibility check failed.");
}
console.log(JSON.stringify({ accountSubtitleVisible, gradeSubtitleVisible, labels, focus, profileScreenshot }));
console.log(await page.snapshot());
await task.finish({ keep: [] });

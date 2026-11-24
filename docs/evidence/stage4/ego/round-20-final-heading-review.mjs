const task = await taskSpace("Stage 4 final heading review");
const page = task.page("p1");
const fs = await import("node:fs/promises");
const source = await fs.readFile(
  "D:/ProjectsX/stu_system/src/test/java/edu/stusystem/service/Stage4AccountSecurityMySqlTest.java",
  "utf8",
);
const fixture = source.match(/insertAccount\("stage4-admin", "admin", "ADMIN", "([^"]+)"\)/);
if (!fixture) throw new Error("Isolated administrator fixture was not found.");

async function setViewport(width, height) {
  await page.cdp("Emulation.setDeviceMetricsOverride", {
    width,
    height,
    deviceScaleFactor: 1,
    mobile: false,
  });
}

async function capture(name, width, height) {
  return page.screenshot({
    path: `D:/ProjectsX/stu_system/docs/evidence/stage4/ego/final-copy-${name}-${width}x${height}.png`,
  });
}

await setViewport(1440, 900);
await page.goto("http://127.0.0.1:5175/");
await page.fill('.login-form input[autocomplete="username"]', "admin");
await page.fill('.login-form input[autocomplete="current-password"]', fixture[1]);
await page.click(".login-submit", { label: "sign in to isolated app" });
await page.waitForFunction(() => !!document.querySelector(".sidebar"), undefined, { timeout: 15_000 });

await page.click("loc=role:button[name='概览']", { label: "review overview heading" });
await page.waitForFunction(() => !!document.querySelector(".subheading"));
const overviewSubtitle = await page.evaluate(() => document.querySelector(".subheading")?.textContent.trim());

await page.click("loc=role:button[name='账户管理']", { label: "review account heading" });
await page.waitForFunction(() => document.body.innerText.includes("5 条记录"), undefined, { timeout: 10_000 });
const accountSubtitleVisible = await page.evaluate(() => !!document.querySelector(".page-heading .subheading"));
const accountWide = await capture("accounts", 1440, 900);

await page.click("loc=role:button[name='成绩记录']", { label: "review grade heading" });
await page.waitForFunction(() => !!document.querySelector(".page-heading h1"));
const gradeSubtitleVisible = await page.evaluate(() => !!document.querySelector(".page-heading .subheading"));

await setViewport(1280, 800);
await page.click("loc=role:button[name='个人中心']", { label: "review profile heading" });
await page.waitForFunction(() => document.body.innerText.includes("个人资料") && !document.querySelector(".error-banner"));
const profileSubtitleVisible = await page.evaluate(() => !!document.querySelector(".page-heading .subheading"));
const profileNarrow = await capture("profile", 1280, 800);

if (overviewSubtitle !== "学习与教学数据一览" || accountSubtitleVisible || gradeSubtitleVisible || profileSubtitleVisible) {
  throw new Error("Final workspace heading check did not match the expected copy.");
}
console.log(JSON.stringify({
  overviewSubtitle,
  accountSubtitleVisible,
  gradeSubtitleVisible,
  profileSubtitleVisible,
  screenshots: [accountWide, profileNarrow],
}));
await task.finish({ keep: [] });

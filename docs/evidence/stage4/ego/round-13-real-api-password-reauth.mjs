const task = await taskSpace(4);
const page = task.page("p1");
const fs = await import("node:fs/promises");
const crypto = await import("node:crypto");
const source = await fs.readFile(
  "D:/ProjectsX/stu_system/src/test/java/edu/stusystem/service/Stage4AccountSecurityMySqlTest.java",
  "utf8",
);
const fixture = source.match(/insertAccount\("stage4-admin", "admin", "ADMIN", "([^"]+)"\)/);
if (!fixture) throw new Error("Isolated administrator fixture was not found.");
const newPassword = `R${crypto.randomBytes(6).toString("hex")}!a9`;

await page.fill('input[placeholder="当前密码"]', fixture[1]);
await page.fill('input[placeholder="新密码"]', newPassword);
await page.click("text=验证密码", { label: "verify current password" });
await page.waitForFunction(
  () => [...document.querySelectorAll(".password-grid button")]
    .some((button) => button.textContent.includes("更新密码") && !button.disabled),
  undefined,
  { timeout: 15_000 },
);
await page.click("text=更新密码", { label: "change isolated account password" });
await page.waitForFunction(() => !!document.querySelector(".login-form"), undefined, { timeout: 10_000 });

await page.fill('.login-form input[autocomplete="username"]', "browser-admin");
await page.fill('.login-form input[autocomplete="current-password"]', newPassword);
await page.click(".login-submit", { label: "sign in with updated password" });
await page.waitForFunction(() => !!document.querySelector(".sidebar"), undefined, { timeout: 15_000 });
await page.click("loc=role:button[name='个人中心']", { label: "verify profile after re-login" });
await page.waitForFunction(() => document.body.innerText.includes("browser-admin"));
await page.cdp("Emulation.setDeviceMetricsOverride", {
  width: 1280,
  height: 800,
  deviceScaleFactor: 1,
  mobile: false,
});
const screenshot = await page.screenshot({
  path: "D:/ProjectsX/stu_system/docs/evidence/stage4/ego/real-api-profile-reauth-1280x800.png",
});
console.log(JSON.stringify({ passwordChanged: true, reauthenticated: true, screenshot }));
console.log(await page.snapshot());

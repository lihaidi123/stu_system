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

await page.fill('input[maxlength="30"]', "browser-admin");
await page.click("text=更新用户名", { label: "update isolated username" });
await page.waitForFunction(() => !!document.querySelector(".login-form"), undefined, { timeout: 10_000 });
const renamedUser = await page.evaluate(() => document.querySelector('.login-form input[autocomplete="username"]')?.value);
if (renamedUser !== "browser-admin") throw new Error("Username change did not carry into the re-login form.");

async function login(password) {
  await page.fill('.login-form input[autocomplete="username"]', "browser-admin");
  await page.fill('.login-form input[autocomplete="current-password"]', password);
  await page.click(".login-submit", { label: "sign in to isolated app" });
  await page.waitForFunction(() => !!document.querySelector(".sidebar"), undefined, { timeout: 15_000 });
}

await login(fixture[1]);
await page.click("loc=role:button[name='个人中心']", { label: "reopen profile workspace" });
await page.waitForFunction(() => document.body.innerText.includes("个人资料"));
await page.fill('input[type="email"]', "stage4-browser-20261123@example.org");
await page.fill('input[maxlength="11"]', "13800138010");
await page.click("text=保存联系方式", { label: "save isolated contact details" });
await page.waitForFunction(() => document.body.innerText.includes("联系方式已更新"), undefined, { timeout: 10_000 });

const chooserPromise = page.waitForFileChooser({ timeout: 10_000 });
await page.click("text=更换头像", { label: "choose local avatar fixture" });
const chooser = await chooserPromise;
await chooser.setFiles("D:/ProjectsX/stu_system/docs/evidence/stage4/ego/real-api-accounts-1440x900.png");
await page.waitForFunction(() => !!document.querySelector(".avatar-profile img"), undefined, { timeout: 15_000 });
await page.click("text=恢复默认头像", { label: "remove isolated avatar" });
await page.waitForFunction(() => !!document.querySelector(".avatar-profile .avatar-fallback"), undefined, { timeout: 10_000 });

await page.fill('input[placeholder="当前密码"]', fixture[1]);
await page.click("text=验证密码", { label: "verify current password" });
await page.waitForFunction(() => document.body.innerText.includes("密码验证通过"), undefined, { timeout: 10_000 });
await page.fill('input[placeholder="新密码"]', newPassword);
await page.click("text=更新密码", { label: "change isolated account password" });
await page.waitForFunction(() => !!document.querySelector(".login-form"), undefined, { timeout: 10_000 });
await login(newPassword);
await page.click("loc=role:button[name='个人中心']", { label: "verify re-login profile" });
await page.waitForFunction(() => document.body.innerText.includes("browser-admin"));
console.log(await page.snapshot());
console.log(JSON.stringify({
  usernameChangedAndReauthenticated: true,
  contactsSaved: true,
  avatarUploadedAndRemoved: true,
  passwordVerifiedChangedAndReauthenticated: true,
}));

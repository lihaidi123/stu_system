const task = await taskSpace(4);
const page = task.page("p1");
const crypto = await import("node:crypto");
const tempPassword = `T${crypto.randomBytes(8).toString("hex")}!a9`;

async function downloadAndDiscard(selector, label) {
  const pending = page.waitForEvent("download", { timeout: 20_000 });
  await page.click(selector, { label });
  const download = await pending;
  const name = download.suggestedFilename();
  await download.delete();
  return name;
}

await page.cdp("Emulation.setDeviceMetricsOverride", {
  width: 1440,
  height: 900,
  deviceScaleFactor: 1,
  mobile: false,
});
await page.click("loc=role:button[name='账户管理']", { label: "open account import workspace" });
await page.waitForFunction(() => document.body.innerText.includes("账户文件"));
await page.setInputFiles("input[type=file]", [
  "D:/ProjectsX/stu_system/target/browser-acceptance-fixtures/stage4-browser-accounts.xlsx",
]);
await page.fill('input[placeholder="为新账户设置临时密码"]', tempPassword);
await page.click("text=导入账户", { label: "import isolated account row" });
await page.waitForFunction(() => document.body.innerText.includes("新增 1") && document.body.innerText.includes("Browser Imported"), undefined, { timeout: 20_000 });
const accountExport = await downloadAndDiscard("text=导出账户", "export imported account");

await page.click("loc=role:button[name='成绩记录']", { label: "open grade import workspace" });
await page.waitForFunction(() => document.body.innerText.includes("成绩文件"));
await page.setInputFiles("input[type=file]", [
  "D:/ProjectsX/stu_system/target/browser-acceptance-fixtures/stage4-browser-grades.xlsx",
]);
await page.click("text=导入成绩", { label: "import isolated grade row" });
await page.waitForFunction(() => document.body.innerText.includes("已导入 1 条成绩"), undefined, { timeout: 20_000 });
const gradeExport = await downloadAndDiscard("text=导出筛选结果", "export imported grade");
console.log(JSON.stringify({ accountImport: "created 1", accountExport, gradeImport: "created 1", gradeExport }));
console.log(await page.snapshot());

const task = await taskSpace(4);
const page = task.page("p1");
const evidence = "D:/ProjectsX/stu_system/docs/evidence/stage4/ego";
const captures = [];

async function setViewport(width, height) {
  await page.cdp("Emulation.setDeviceMetricsOverride", {
    width,
    height,
    deviceScaleFactor: 1,
    mobile: false,
  });
}

async function captureWorkspace(label, width, height, filename) {
  const path = await page.screenshot({ path: `${evidence}/${filename}` });
  captures.push({ workspace: label, viewport: `${width}x${height}`, path });
}

async function downloadAndDiscard(selector, label) {
  const downloadPromise = page.waitForEvent("download", { timeout: 20_000 });
  await page.click(selector, { label });
  const download = await downloadPromise;
  const filename = download.suggestedFilename();
  await download.delete();
  return filename;
}

for (const [width, height] of [[1440, 900], [1280, 800]]) {
  await setViewport(width, height);
  await page.click("loc=role:button[name='账户管理']", { label: "open account workspace" });
  await page.waitForFunction(() => document.body.innerText.includes("账户文件"));
  await captureWorkspace("accounts", width, height, `real-api-accounts-${width}x${height}.png`);
  const accountTemplate = await downloadAndDiscard("text=下载模板", "download account template");
  const accountExport = await downloadAndDiscard("text=导出账户", "export accounts");

  await page.click("loc=role:button[name='成绩记录']", { label: "open grade workspace" });
  await page.waitForFunction(() => document.body.innerText.includes("成绩文件"));
  await captureWorkspace("grades", width, height, `real-api-grades-${width}x${height}.png`);
  const gradeTemplate = await downloadAndDiscard("text=下载模板", "download grade template");
  const gradeExport = await downloadAndDiscard("text=导出筛选结果", "export filtered grades");

  await page.click("loc=role:button[name='个人中心']", { label: "open profile workspace" });
  await page.waitForFunction(() => document.body.innerText.includes("个人资料"));
  await captureWorkspace("profile", width, height, `real-api-profile-${width}x${height}.png`);
  if (width === 1280) console.log(await page.snapshot());
  console.log(JSON.stringify({
    viewport: `${width}x${height}`,
    accountTemplate,
    accountExport,
    gradeTemplate,
    gradeExport,
  }));
}

console.log(JSON.stringify(captures));

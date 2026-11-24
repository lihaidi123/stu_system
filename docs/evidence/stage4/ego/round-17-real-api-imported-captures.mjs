const task = await taskSpace(4);
const page = task.page("p1");
const evidence = "D:/ProjectsX/stu_system/docs/evidence/stage4/ego";
const captures = [];

async function viewport(width, height) {
  await page.cdp("Emulation.setDeviceMetricsOverride", {
    width,
    height,
    deviceScaleFactor: 1,
    mobile: false,
  });
}

async function capture(name, width, height) {
  await page.screenshot({ path: `${evidence}/real-api-${name}-imported-${width}x${height}.png` });
  captures.push(`${name} ${width}x${height}`);
}

for (const [width, height] of [[1440, 900], [1280, 800]]) {
  await viewport(width, height);
  await page.click("loc=role:button[name='账户管理']", { label: "review imported account list" });
  await page.waitForFunction(() => document.body.innerText.includes("6 条记录"), undefined, { timeout: 10_000 });
  await page.waitForFunction(() => document.querySelectorAll(".el-table__body-wrapper tbody tr").length >= 6);
  await capture("accounts", width, height);

  await page.click("loc=role:button[name='成绩记录']", { label: "review imported grade list" });
  await page.waitForFunction(() => document.body.innerText.includes("1 条记录"), undefined, { timeout: 10_000 });
  await page.waitForFunction(() => document.querySelectorAll(".el-table__body-wrapper tbody tr").length === 1);
  await capture("grades", width, height);

  await page.click("loc=role:button[name='个人中心']", { label: "review updated profile" });
  await page.waitForFunction(() => {
    return document.querySelector('.profile-grid input[maxlength="30"]')?.value === "browser-admin"
      && document.querySelector('.profile-grid input[type="email"]')?.value === "stage4-browser-20261123@example.org"
      && !document.querySelector(".error-banner");
  }, undefined, { timeout: 10_000 });
  await capture("profile", width, height);
}

console.log(JSON.stringify(captures));

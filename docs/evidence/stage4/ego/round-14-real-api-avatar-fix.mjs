const task = await taskSpace(4);
const page = task.page("p1");

await page.click("loc=role:button[name='个人中心']", { label: "open profile after hot update" });
await page.waitForFunction(() => document.body.innerText.includes("个人资料"));
await page.waitForFunction(() => !!document.querySelector(".avatar-profile img"), undefined, { timeout: 15_000 });
await page.waitForFunction(() => !document.querySelector(".error-banner"), undefined, { timeout: 5_000 });
const image = await page.evaluate(() => ({
  loaded: !!document.querySelector(".avatar-profile img"),
  source: document.querySelector(".avatar-profile img")?.getAttribute("src"),
}));
await page.cdp("Emulation.setDeviceMetricsOverride", {
  width: 1280,
  height: 800,
  deviceScaleFactor: 1,
  mobile: false,
});
const screenshot = await page.screenshot({
  path: "D:/ProjectsX/stu_system/docs/evidence/stage4/ego/real-api-profile-avatar-loaded-1280x800.png",
});
await page.click("text=恢复默认头像", { label: "remove isolated avatar" });
await page.waitForFunction(() => !!document.querySelector(".avatar-profile .avatar-fallback"), undefined, { timeout: 10_000 });
await page.waitForFunction(
  () => ![...document.querySelectorAll("button")].some((button) => button.textContent.includes("恢复默认头像")),
  undefined,
  { timeout: 5_000 },
);
console.log(JSON.stringify({ imageLoaded: image.loaded, imageSource: image.source, screenshot, avatarRemoved: true }));
console.log(await page.snapshot());

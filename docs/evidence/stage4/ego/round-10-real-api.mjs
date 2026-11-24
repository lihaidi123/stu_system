const saved = (await listTaskSpaces()).find((item) => item.id === 4);
const task = !saved
  ? await taskSpace("Stage 4 authenticated real API review")
  : saved.ownership === "user"
    ? await claimTaskSpace(4)
    : saved.ownership === "agentDelegatedToUser"
      ? await takeOverTaskSpace(4)
      : await taskSpace(4);
const page = (await task.pages())[0] ?? await task.adopt((await task.tabs())[0].page);
const fs = await import("node:fs/promises");
const source = await fs.readFile(
  "D:/ProjectsX/stu_system/src/test/java/edu/stusystem/service/Stage4AccountSecurityMySqlTest.java",
  "utf8",
);
const fixture = source.match(/insertAccount\("stage4-admin", "admin", "ADMIN", "([^"]+)"\)/);
if (!fixture) throw new Error("Isolated administrator fixture was not found.");

await page.cdp("Emulation.setDeviceMetricsOverride", {
  width: 1440,
  height: 900,
  deviceScaleFactor: 1,
  mobile: false,
});
await page.goto("http://127.0.0.1:5175/");
await page.fill('.login-form input[autocomplete="username"]', "admin");
await page.fill('.login-form input[autocomplete="current-password"]', fixture[1]);
await page.click(".login-submit", { label: "sign in to isolated app" });
await page.waitForFunction(() => !!document.querySelector(".sidebar"), undefined, { timeout: 15_000 });

console.log(JSON.stringify({ taskSpaceId: task.spaceId, page: page.label, title: await page.title() }));
console.log(await page.snapshot());

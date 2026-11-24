const task = await taskSpace(4);
const page = task.page("p1");
const fs = await import("node:fs/promises");
const source = await fs.readFile(
  "D:/ProjectsX/stu_system/src/test/java/edu/stusystem/service/Stage4AccountSecurityMySqlTest.java",
  "utf8",
);
const fixture = source.match(/insertAccount\("stage4-admin", "admin", "ADMIN", "([^"]+)"\)/);
if (!fixture) throw new Error("Isolated administrator fixture was not found.");

await page.fill('.login-form input[autocomplete="username"]', "admin");
await page.fill('.login-form input[autocomplete="current-password"]', fixture[1]);
await page.click(".login-submit", { label: "sign in to reset isolated schema" });
await page.waitForFunction(() => !!document.querySelector(".sidebar"), undefined, { timeout: 15_000 });
console.log(JSON.stringify({ signedInToIsolatedStage4Api: true, title: await page.title() }));
console.log(await page.snapshot());

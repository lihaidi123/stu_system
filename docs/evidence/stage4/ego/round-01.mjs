const task = await taskSpace("Stage 4 desktop workflow review");
const page = await task.newPage();
await page.goto("http://127.0.0.1:5174/");
console.log({ taskSpaceId: task.spaceId, page: page.label, title: await page.title(), url: await page.url() });
console.log(await page.snapshot());

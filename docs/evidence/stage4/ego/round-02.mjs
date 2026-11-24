help("page.cdp");
help("page.screenshot");
const task = await taskSpace(1);
console.log(await task.tabs());

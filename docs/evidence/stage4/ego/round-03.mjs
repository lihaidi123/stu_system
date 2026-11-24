const task = await taskSpace(1);
const page = task.page("p2");
console.log(await page.screenshot({path:"D:/ProjectsX/stu_system/docs/evidence/stage4/ego/login-1440x900.png"}));
console.log(await page.evaluate(() => ({title: document.title, viewport: [innerWidth, innerHeight], h1: document.querySelector("h1")?.innerText})));

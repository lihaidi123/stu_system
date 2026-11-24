const task=await taskSpace(1);const page=task.page("p2");
await page.click("text=账户管理");
console.log(await page.snapshot());
console.log(await page.screenshot({path:"D:/ProjectsX/stu_system/docs/evidence/stage4/ego/accounts-1440x900.png",fullPage:true}));

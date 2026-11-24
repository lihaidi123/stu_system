const task=await taskSpace(1);const page=task.page("p2");
await page.click("text=成绩记录");
console.log((await page.snapshot()).slice(0,7000));
console.log(await page.screenshot({path:"D:/ProjectsX/stu_system/docs/evidence/stage4/ego/grades-1440x900.png",fullPage:true}));
await page.click("text=个人中心");
console.log((await page.snapshot()).slice(0,5000));
console.log(await page.screenshot({path:"D:/ProjectsX/stu_system/docs/evidence/stage4/ego/profile-1440x900.png",fullPage:true}));
await page.cdp("Emulation.setDeviceMetricsOverride",{width:1280,height:800,deviceScaleFactor:1,mobile:false});
console.log(await page.screenshot({path:"D:/ProjectsX/stu_system/docs/evidence/stage4/ego/profile-1280x800.png",fullPage:true}));

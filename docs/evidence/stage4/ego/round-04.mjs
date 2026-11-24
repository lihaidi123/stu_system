const task = await taskSpace(1);
const page = task.page("p2");
await page.cdp("Emulation.setDeviceMetricsOverride", {width:1440,height:900,deviceScaleFactor:1,mobile:false});
const mock = `(() => {
  const accounts = [
    {id:"101",business_no:"T-1008",username:"lin.teacher",real_name:"林老师",email:"lin@example.edu",phone:"13800138008",role:"TEACHER",status:"ACTIVE"},
    {id:"102",business_no:"C-2012",username:"zhou.counselor",real_name:"周老师",email:"",phone:"",role:"COUNSELOR",status:"ACTIVE"},
    {id:"103",business_no:"S-230017",username:"student.230017",real_name:"陈同学",email:"chen@example.edu",phone:"",role:"STUDENT",status:"ACTIVE"}
  ];
  const students = [
    {id:"201",student_no:"20230017",full_name:"陈同学",class_id:"21",class_name:"计科2301"},
    {id:"202",student_no:"20230021",full_name:"刘同学",class_id:"21",class_name:"计科2301"}
  ];
  const grades = [
    {id:"301",version:0,student_id:"201",student_no:"20230017",full_name:"陈同学",course_id:"11",course_name:"数据结构",term_id:"31",term_name:"2025-2026 学年 第一学期",credits_snapshot:3,assessment_type:"EXAM",score_value:92.5,grade_point:4.0},
    {id:"302",version:0,student_id:"202",student_no:"20230021",full_name:"刘同学",course_id:"12",course_name:"数据库原理",term_id:"31",term_name:"2025-2026 学年 第一学期",credits_snapshot:3,assessment_type:"ASSESSMENT",score_value:86,grade_point:3.7}
  ];
  const nativeOpen = XMLHttpRequest.prototype.open;
  const nativeSend = XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.open = function(method,url,...rest){ this.__visualUrl=url; return nativeOpen.call(this,method,url,...rest); };
  XMLHttpRequest.prototype.send = function(body){
    if (!String(this.__visualUrl||"").includes("/api/v1/")) return nativeSend.call(this,body);
    const path = new URL(this.__visualUrl,location.href).pathname;
    const profile = {id:"1",businessNo:"A-0001",username:"admin",realName:"系统管理员",email:"admin@example.edu",phone:"13800138001",role:"ADMIN",studentId:null,createdAt:"2026-11-01 09:00:00",avatarUrl:null};
    let data = {success:true,data:{}};
    if(path.endsWith("/auth/login")) data={success:true,data:{token:"visual-session-token",user:{id:"1",username:"admin",realName:"系统管理员",role:"ADMIN"}}};
    else if(path.endsWith("/me")) data={success:true,data:{id:"1",username:"admin",realName:"系统管理员",role:"ADMIN"}};
    else if(path.endsWith("/scope/options")) data={success:true,data:{departments:[{id:"1",name:"计算机学院"}],classes:[{id:"21",name:"计科2301",entry_year:2023,department_id:"1"}],courses:[{id:"11",name:"数据结构",default_credits:3,department_id:"1"},{id:"12",name:"数据库原理",default_credits:3,department_id:"1"}],terms:[{id:"31",name:"2025-2026 学年 第一学期"}],students}};
    else if(path.endsWith("/students")) data={success:true,data:{items:students,total:2,page:1,pageSize:20}};
    else if(path.endsWith("/admin/accounts")) data={success:true,data:accounts};
    else if(path.endsWith("/grades")) data={success:true,data:{items:grades,total:2,page:1,pageSize:20}};
    else if(path.endsWith("/me/profile")) data={success:true,data:profile};
    const bodyText=JSON.stringify(data);
    setTimeout(()=>{
      Object.defineProperty(this,"readyState",{configurable:true,value:4});
      Object.defineProperty(this,"status",{configurable:true,value:200});
      Object.defineProperty(this,"statusText",{configurable:true,value:"OK"});
      Object.defineProperty(this,"responseText",{configurable:true,value:bodyText});
      Object.defineProperty(this,"response",{configurable:true,value:bodyText});
      this.onreadystatechange?.(); this.onload?.(); this.onloadend?.();
    },0);
  };
})();`;
await page.cdp("Page.addScriptToEvaluateOnNewDocument", {source:mock});
await page.evaluate(() => { localStorage.setItem("token","visual-session-token"); localStorage.setItem("user",JSON.stringify({id:"1",username:"admin",realName:"系统管理员",role:"ADMIN"})); });
await page.reload();
await page.waitForSelector("text=账户管理",{state:"visible",timeout:10000});
console.log(await page.snapshot());
console.log({taskSpaceId:task.spaceId,page:page.label,viewport:await page.evaluate(()=>[innerWidth,innerHeight])});

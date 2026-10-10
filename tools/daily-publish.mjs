// After validation passes: updates daily/index.json, daily/hashes.json and daily/log.json.
// Usage: node tools/daily-publish.mjs daily/YYYY-MM-DD.json
import fs from "node:fs";import path from "node:path";import crypto from "node:crypto";
const norm=s=>String(s||"").normalize("NFC").toLowerCase().replace(/[^\p{L}\p{N}\p{M}]+/gu,"");const hash=s=>crypto.createHash("sha256").update(norm(s)).digest("hex");
const file=process.argv[2],dir=path.dirname(file),J=JSON.parse(fs.readFileSync(file,"utf8")),rd=(f,d)=>{try{return JSON.parse(fs.readFileSync(path.join(dir,f),"utf8"))}catch(e){return d}},wr=(f,o)=>fs.writeFileSync(path.join(dir,f),JSON.stringify(o,null,1)+"\n");
const idx=rd("index.json",{dates:[]});if(!idx.dates.includes(J.date))idx.dates.push(J.date);idx.dates.sort().reverse();idx.latest=idx.dates[0];idx.updated=new Date().toISOString();wr("index.json",idx);
const H=rd("hashes.json",{});J.arya.forEach(t=>t.questions.forEach(q=>{H[hash(q.q)]=J.date}));wr("hashes.json",H);
const L=rd("log.json",[]).filter(x=>x.date!==J.date);L.push({date:J.date,key:J.key,status:"success",completed_at:new Date().toISOString(),navamika_games_count:J.navamika.length,arya_test_1_questions:J.arya[0].questions.length,arya_test_2_questions:J.arya[1].questions.length,arya_test_3_questions:(J.arya[2]||{questions:[]}).questions.length,arya_test_4_questions:(J.arya[3]||{questions:[]}).questions.length,source:J.source||"scheduled"});wr("log.json",L.slice(-400));
console.log(JSON.stringify({published:J.date,dates:idx.dates.length,hashes:Object.keys(H).length}));

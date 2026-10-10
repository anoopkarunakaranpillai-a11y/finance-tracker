// After validation passes: updates daily/index.json (dates, reports, totals, next run), daily/hashes.json and daily/log.json.
// Usage: node tools/daily-publish.mjs daily/YYYY-MM-DD.json
import fs from "node:fs";import path from "node:path";
import {hash,rd,wr,loadConfig} from "./daily-lib.mjs";
const file=process.argv[2],dir=path.dirname(file),J=JSON.parse(fs.readFileSync(file,"utf8")),C=loadConfig(dir);
const P=f=>path.join(dir,f);
// questions in every published day file
const days=fs.readdirSync(dir).filter(f=>/^\d{4}-\d\d-\d\d\.json$/.test(f)).sort();
let dq=0;for(const f of days){const D=f===path.basename(file)?J:rd(P(f),{});dq+=(D.arya||[]).reduce((s,t)=>s+(t.questions||[]).length,0)+(D.bank||[]).length}
const builtin=rd(P("text/builtin.json"),[]).length;
// next scheduled run (Kuwait has no daylight saving: UTC+3)
const [hh,mm]=String(C.time||"08:48").split(":").map(Number);const now=new Date();const nx=new Date(Date.UTC(now.getUTCFullYear(),now.getUTCMonth(),now.getUTCDate(),hh-3,mm));if(nx<=now)nx.setUTCDate(nx.getUTCDate()+1);
const idx=rd(P("index.json"),{dates:[]});if(!idx.dates.includes(J.date))idx.dates.push(J.date);idx.dates.sort().reverse();idx.latest=idx.dates[0];idx.updated=new Date().toISOString();
idx.reports=idx.reports||{};const R=J.report||{status:"Completed (legacy format)",published:(J.arya||[]).reduce((s,t)=>s+t.questions.length,0),tests:(J.arya||[]).length};
idx.reports[J.date]={batch:R.batch||"",status:R.status,published:R.published,target:R.target||null,tests:R.tests,review:R.awaitingReview||0,rejected:R.rejected||0,duplicates:R.duplicates||0,officialNew:R.officialNew||0,ca:R.currentAffairs||0,finished:R.finished||J.generatedAt};
for(const d of Object.keys(idx.reports).sort().slice(0,-90))delete idx.reports[d];
idx.totals={builtin,daily:dq,questions:builtin+dq,days:days.length};idx.next=nx.toISOString();idx.config={enabled:C.enabled,time:C.time,timezone:C.timezone,target:C.target};
wr(P("index.json"),idx);
const H=rd(P("hashes.json"),{});(J.arya||[]).forEach(t=>t.questions.forEach(q=>{H[hash(q.q)]=H[hash(q.q)]||J.date}));(J.bank||[]).forEach(q=>{H[hash(q.q)]=J.date});wr(P("hashes.json"),H);
const L=rd(P("log.json"),[]);L.push({date:J.date,batch:R.batch||J.key,status:R.status||"success",completed_at:new Date().toISOString(),published:R.published,target:R.target||null,added_this_run:R.addedThisRun??null,review:R.awaitingReview||0,rejected:R.rejected||0,duplicates:R.duplicates||0,tests:R.tests,navamika_games_count:(J.navamika||[]).length,errors:(R.errors||[]).length,source:J.source||"scheduled"});wr(P("log.json"),L.slice(-1000));
console.log(JSON.stringify({published:J.date,status:R.status,questionsToday:R.published,bankTotal:builtin+dq,dates:idx.dates.length,next:idx.next}));

// Daily batch builder: validates candidate questions, removes duplicates, publishes the good ones,
// builds the daily mock tests and writes the automation report.
// Usage: node tools/daily-build.mjs YYYY-MM-DD [--dry]
// Reads   daily/work/DATE/candidates*.json, verify*.json, navamika.json, ca.json, official.json, run.json
// Writes  daily/DATE.json (v2), daily/logs/DATE.json (one line per candidate: outcome + reason)
// Safe to run again: questions already published for DATE keep their IDs and are never counted twice.
import fs from "node:fs";import path from "node:path";
import {norm,hash,words,jaccard,rd,wr,CATS,LEVELS,DIFFS,loadConfig,calc,numOf} from "./daily-lib.mjs";
const DATE=process.argv[2],DRY=process.argv.includes("--dry");
if(!/^\d{4}-\d\d-\d\d$/.test(DATE)){console.error("usage: daily-build.mjs YYYY-MM-DD");process.exit(2)}
const ROOT=path.resolve(path.dirname(new URL(import.meta.url).pathname),".."),DD=path.join(ROOT,"daily"),WK=path.join(DD,"work",DATE);
const C=loadConfig(DD),V=C.validation,YMD=DATE.replace(/-/g,"");
const list=(pre)=>fs.existsSync(WK)?fs.readdirSync(WK).filter(f=>f.startsWith(pre)&&f.endsWith(".json")).sort().map(f=>path.join(WK,f)):[];
const cands=[];const seenK=new Set();for(const f of list("candidates")){const a=rd(f,null);if(!Array.isArray(a)){console.error("not an array: "+f);continue}a.forEach(x=>{if(x&&x.k&&!seenK.has(x.k)){seenK.add(x.k);cands.push(x)}})}
const verify={};for(const f of list("verify"))Object.assign(verify,rd(f,{}));
const run=rd(path.join(WK,"run.json"),{runs:[],errors:[],retries:0});
const prev=rd(path.join(DD,DATE+".json"),null);
const out=prev&&prev.v===2?prev:Object.assign({v:2,date:DATE,key:"DAILY_LEARNING_"+YMD.slice(0,4)+"_"+YMD.slice(4,6)+"_"+YMD.slice(6)},prev||{},{v:2});
out.bank=out.bank||[];out.tests=out.tests||[];out.ca=out.ca||[];
const prevLog=rd(path.join(DD,"logs",DATE+".json"),{candidates:[]}).candidates||[];const done=new Map(prevLog.map(l=>[l.k,l.result]));
out.review=(out.review||[]).filter(r=>!seenK.has(r.k)||done.get(r.k)!=="awaiting review");
const nav=rd(path.join(WK,"navamika.json"),null);if(Array.isArray(nav)&&nav.length)out.navamika=nav;
const ca=rd(path.join(WK,"ca.json"),null);if(Array.isArray(ca))out.ca=ca.filter(x=>x&&x.t&&/^https:\/\//.test(x.url||"")&&/^\d{4}-\d\d-\d\d$/.test(x.d||""));
const off=rd(path.join(WK,"official.json"),null);if(off)out.official=off;
// ---- everything already in the question bank (built-in + every earlier day + today's published)
const exist=[];const exHash=new Map();const addEx=(id,q,o,where)=>{const h=hash(q);if(!exHash.has(h))exHash.set(h,where);exist.push({id,w:words(q+" "+(o||[]).join(" ")),wq:words(q),os:(o||[]).map(norm).sort().join("|"),where})};
rd(path.join(DD,"text","builtin.json"),[]).forEach(x=>addEx(x.id,x.q,x.o,"built-in bank"));
fs.readdirSync(DD).filter(f=>/^\d{4}-\d\d-\d\d\.json$/.test(f)&&f!==DATE+".json").forEach(f=>{const J=rd(path.join(DD,f),{});(J.arya||[]).forEach(t=>(t.questions||[]).forEach(q=>addEx(f,q.q,q.o,f.slice(0,10))));(J.bank||[]).forEach(q=>addEx(q.id,q.q,q.o,f.slice(0,10)))});
(prev&&prev.arya||[]).forEach(t=>(t.questions||[]).forEach(q=>addEx("legacy",q.q,q.o,DATE+" (earlier today)")));
const pubHash=new Set(out.bank.map(q=>hash(q.q)));out.bank.forEach(q=>addEx(q.id,q.q,q.o,DATE+" (already published)"));
// ---- checks
const ML=/[ഀ-ൿ]/;const LOG=[];const today=new Date(DATE+"T00:00:00Z");
const ageDays=d=>(today-new Date(d+"T00:00:00Z"))/864e5;
const leadNum=s=>{const m=String(s).replace(/,/g,"").match(/-?\d*\.?\d+/);return m?parseFloat(m[0]):NaN};
function check(x){const P=[],R=[];// P = reject (invalid), R = needs admin review
  const cat=CATS[x.cat];if(!cat)return{P:["unknown category "+x.cat]};
  if(!cat.subj.includes(x.s))P.push(`subject "${x.s}" doesn't belong to ${cat.n}`);
  if(!x.t||String(x.t).length<2)P.push("topic missing");
  if(!DIFFS.includes(x.d))P.push("difficulty must be easy|medium|hard|advanced");
  if(!LEVELS.includes(x.lvl))P.push("qualification level missing or unknown");
  if(x.cat==="deg"&&!["degree","entrance"].includes(x.lvl))P.push("degree questions need level degree or entrance");
  if(x.cat==="compx"&&!(C.exams||[]).includes(x.exam))P.push("competitive questions need an exam from the configured list");
  if(!x.q||String(x.q).trim().length<8)P.push("question text too short");
  if(!Array.isArray(x.o)||x.o.length!==4||x.o.some(o=>!String(o||"").trim()))P.push("needs exactly 4 non-empty options");
  else{const n=x.o.map(norm);if(new Set(n).size!==4)P.push("two options are the same");if(n.includes(norm(x.q)))P.push("an option repeats the question");
    if(V.rejectAllNoneOfAbove&&x.o.some(o=>/\b(all|none|both) of (the )?(above|these)\b|മുകളിൽ പറഞ്ഞ(വ)? (എല്ലാം|ഒന്നുമല്ല)|ഇവയെല്ലാം|ഇവയൊന്നുമല്ല/i.test(o)))P.push("'all/none of the above' options are not allowed (risk of more than one correct answer)");
    if(x.o.some(o=>/^\s*[A-D]\s*(and|&|,)\s*[A-D]\s*$/i.test(o)))P.push("combined options like 'A and B' are not allowed")}
  if(![0,1,2,3].includes(x.a))P.push("answer must be 0-3");
  if(!x.e||String(x.e).trim().length<12)P.push("explanation missing");
  else if(Array.isArray(x.o)&&[0,1,2,3].includes(x.a)){const right=norm(x.o[x.a]),e=norm(x.e);const others=x.o.filter((o,i)=>i!==x.a).map(norm).filter(o=>o.length>2&&e.includes(o)&&!right.includes(o));if(right.length>1&&!e.includes(right)&&others.length)P.push("the explanation points to a different option")}
  const want=(C.langs||{})[x.cat];if(want&&x.lang!==want&&!(x.cat==="gkca"&&x.s==="eng"))P.push(`language must be ${want} for ${cat.n}`);
  if(x.lang==="ml"&&!ML.test(x.q))P.push('lang "ml" but the question is not in Malayalam');if(x.lang==="en"&&ML.test(x.q))P.push('lang "en" but the question is in Malayalam');
  if(/\?\s*\?|\.\.\.\.|\s{3,}|<[a-z]+>|undefined|null/i.test(x.q+" "+(x.o||[]).join(" ")))P.push("broken formatting");
  if(x.s==="ca"){if(!/^https:\/\//.test(x.ref||""))P.push("current-affairs question needs a source link (ref)");if(!/^\d{4}-\d\d-\d\d$/.test(x.evd||""))P.push("current-affairs question needs the event date (evd)");else{const a=ageDays(x.evd);if(a<0)P.push("event date is in the future");else if(a>V.caMaxAgeDays)P.push(`event is older than ${V.caMaxAgeDays} days, not current`)}}
  // calculation check
  const needsCalc=["quant","math","dmath"].includes(x.s)&&!x.nocalc;
  if(V.calcCheck&&(x.calc||needsCalc)&&!P.length){if(!x.calc)R.push("numeric question without a calc expression to check the answer");else{const v=calc(x.calc),c=Number.isNaN(numOf(x.o[x.a]))?leadNum(x.o[x.a]):numOf(x.o[x.a]);
    if(!Number.isFinite(v))R.push("calc expression couldn't be evaluated");else if(!Number.isFinite(c)||Math.abs(v-c)>Math.max(0.006,Math.abs(v)*1e-4))P.push(`calculation gives ${+v.toFixed(4)} but the marked answer is "${x.o[x.a]}"`);
    else{const hits=x.o.filter(o=>{const n=Number.isNaN(numOf(o))?leadNum(o):numOf(o);return Number.isFinite(n)&&Math.abs(n-v)<=Math.max(0.006,Math.abs(v)*1e-4)}).length;if(hits>1)P.push("more than one option matches the calculated answer")}}}
  // independent (blind) answer check
  if(V.blindCheck&&!P.length){const b=verify[x.k];if(b===undefined||b===null)R.push("no independent answer check recorded");else if(+b!==x.a)P.push(`independent check chose ${"ABCD"[+b]||"?"} but the key says ${"ABCD"[x.a]}`)}
  return{P,R}}
// ---- process candidates
let gen=0,dupE=0,dupN=0,invalid=0,review=0,pub=0;const added=[];
const order=Object.keys(CATS);cands.sort((a,b)=>order.indexOf(a.cat)-order.indexOf(b.cat)||String(a.k).localeCompare(String(b.k)));
const batchH=new Set();let seq=out.bank.reduce((m,q)=>Math.max(m,+String(q.id).slice(-3)||0),0);
for(const x of cands){const pr=done.get(x.k);if(pr&&pr!=="awaiting review")continue;const re=pr==="awaiting review";if(!re)gen++;const h=hash(x.q);
  if(pubHash.has(h)){LOG.push({k:x.k,result:"already published today (not counted again)"});if(!re)gen--;continue}
  if(exHash.has(h)||batchH.has(h)){if(!re)dupE++;LOG.push({k:x.k,result:"duplicate",reason:"same question as "+(exHash.get(h)||"another candidate in this batch")});continue}
  const w=words(x.q+" "+(x.o||[]).join(" ")),wq=words(x.q),os=(x.o||[]).map(norm).sort().join("|");
  const near=exist.find(e=>jaccard(w,e.w)>=V.nearDupThreshold||(e.os===os&&jaccard(wq,e.wq)>=0.6));
  if(near){if(!re)dupN++;LOG.push({k:x.k,result:"near-duplicate",reason:"too close to "+near.where+" ("+near.id+")"});continue}
  const {P,R}=check(x);
  if(P.length){invalid++;LOG.push({k:x.k,result:"rejected",reason:P.join("; ")});continue}
  if(R.length){review++;out.review.push(Object.assign({},x,{reason:R.join("; ")}));LOG.push({k:x.k,result:"awaiting review",reason:R.join("; ")});continue}
  batchH.add(h);exist.push({id:"new",w,wq,os,where:"this batch"});seq++;
  const q={id:`d${YMD}x${String(seq).padStart(3,"0")}`,k:x.k,cat:x.cat,s:x.s,t:x.t,lvl:x.lvl,d:x.d,q:String(x.q).trim(),o:x.o.map(o=>String(o).trim()),a:x.a,e:String(x.e).trim(),lang:x.lang,marks:x.marks||1,neg:x.neg??((C.tests.find(t=>t.cats.includes(x.cat))||{}).ng??1/3),src:"ai-practice",created:DATE,verified:DATE,vby:[V.blindCheck?"independent-answer":null,x.calc?"calculation":null,x.s==="ca"?"source":null].filter(Boolean),status:"published"};
  if(x.exam)q.exam=x.exam;if(x.ref)q.ref=x.ref;if(x.evd){q.evd=x.evd;const e=new Date(x.evd+"T00:00:00Z");e.setUTCDate(e.getUTCDate()+V.caRetireDays);q.exp=e.toISOString().slice(0,10)}
  out.bank.push(q);added.push(q);pub++;LOG.push({k:x.k,result:"published",id:q.id})}
// ---- tests: new questions first, topped up from the built-in bank, no question in two tests
const builtin=rd(path.join(DD,"text","builtin.json"),[]);const catOf=s=>order.find(c=>c!=="compx"&&CATS[c].subj.includes(s));
const rand=(seed)=>{let h=2166136261;for(const c of seed)h=Math.imul(h^c.charCodeAt(0),16777619);return()=>{h=Math.imul(h^(h>>>15),2246822507);h=Math.imul(h^(h>>>13),3266489909);return((h^=h>>>16)>>>0)/4294967296}};
const shuf=(a,r)=>{a=a.slice();for(let i=a.length-1;i>0;i--){const j=Math.floor(r()*(i+1));[a[i],a[j]]=[a[j],a[i]]}return a};
const recent=new Set();fs.readdirSync(DD).filter(f=>/^\d{4}-\d\d-\d\d\.json$/.test(f)&&f<DATE+".json").sort().slice(-14).forEach(f=>(rd(path.join(DD,f),{}).tests||[]).forEach(t=>t.ids.forEach(id=>recent.add(id))));
const used=new Set(out.tests.flatMap(t=>t.ids));const legacyN=(out.arya||[]).length;const DL={easy:1,medium:2,hard:3,advanced:4};
for(const T of C.tests){let t=out.tests.find(x=>x.k===T.k);const r=rand(DATE+T.k);
  const fresh=shuf(out.bank.filter(q=>T.cats.includes(q.cat)&&!used.has(q.id)),r).map(q=>q.id);
  const top=shuf(builtin.filter(b=>T.cats.includes(catOf(b.s))&&b.type==="mcq"&&!used.has(b.id)&&!recent.has(b.id)),r).map(b=>b.id);
  if(!t){const ids=fresh.slice(0,T.n);const nNew=ids.length;ids.push(...top.slice(0,T.n-ids.length));if(nNew<Math.ceil(T.n*0.5))continue;
    t={n:legacyN+out.tests.length+1,k:T.k,id:"",title:T.title,cats:T.cats,cat:T.cats.map(c=>CATS[c].n).join(" · "),ids,newQ:nNew,min:T.min,mk:T.mk,ng:T.ng,pass:T.pass,published:new Date().toISOString()};t.id=`DMT-${YMD}-N${t.n}`;out.tests.push(t)}
  else if(t.ids.length<T.n){const add=fresh.slice(0,T.n-t.ids.length);t.ids.push(...add);t.newQ+=add.length}
  t.ids.forEach(id=>used.add(id));const ds=t.ids.map(id=>{const q=out.bank.find(x=>x.id===id);return q?DL[q.d]:(builtin.find(b=>b.id===id)||{}).d||2});const m=ds.reduce((s,x)=>s+x,0)/ds.length;t.diff=m>=2.6?"Advanced":m>=2.1?"Hard":m>=1.5?"Medium":"Easy"}
// ---- report
const all=out.bank,perCat={};order.forEach(c=>perCat[c]={target:(C.alloc||{})[c]||0,published:all.filter(q=>q.cat===c).length});
const dist={};DIFFS.forEach(d=>dist[d]=all.length?Math.round(all.filter(q=>q.d===d).length/all.length*100):0);
const prevR=prev&&prev.report||{};const sum=(k,v)=>(prevR[k]||0)+v;
const rep={batch:prevR.batch||`B-${YMD}-01`,date:DATE,scheduled:`${C.time} ${C.timezone}`,runs:run.runs||[],started:prevR.started||run.started||new Date().toISOString(),finished:new Date().toISOString(),
  generated:sum("generated",gen),duplicates:sum("duplicates",dupE+dupN),exactDuplicates:sum("exactDuplicates",dupE),nearDuplicates:sum("nearDuplicates",dupN),rejected:sum("rejected",invalid),awaitingReview:out.review.length,
  unique:sum("unique",gen-dupE-dupN),validated:all.length,published:all.length,addedThisRun:pub,target:C.target,achieved:all.length>=C.target,perCat,difficulty:dist,
  tests:out.tests.length+legacyN,newTests:out.tests.map(t=>t.id),modelPapers:out.tests.length,officialChecked:(out.official&&out.official.checked||[]).length,officialNew:(out.official&&out.official.found||[]).length,
  currentAffairs:out.ca.length,pdf:"Generated in the app for every test (question paper, answer key, solutions)",errors:(run.errors||[]).slice(-20),retries:run.retries||0,
  newSubjects:[...new Set(added.map(q=>q.s))],newTopics:[...new Set(added.map(q=>q.t))].slice(0,60)};
rep.status=rep.achieved?"Completed":(rep.published+rep.awaitingReview>=C.target&&rep.awaitingReview?"Awaiting Review":rep.published>0?"Partially Completed":"Failed");
if(!rep.achieved)rep.shortfall=`${C.target-rep.published} short of the ${C.target}-question target`+(rep.awaitingReview?`; ${rep.awaitingReview} waiting for admin review`:"");
out.report=rep;out.generatedAt=new Date().toISOString();out.source=out.source||"scheduled";
const bytes=Buffer.byteLength(JSON.stringify(out));rep.bytes=bytes;if(bytes>240000)rep.errors.push(`day file is ${Math.round(bytes/1024)} KB; the database limit is 256 KB`);
if(!DRY){wr(path.join(DD,DATE+".json"),out);wr(path.join(DD,"logs",DATE+".json"),{date:DATE,batch:rep.batch,at:rep.finished,candidates:prevLog.filter(l=>!LOG.some(n=>n.k===l.k)).concat(LOG)})}
console.log(JSON.stringify({status:rep.status,published:rep.published,target:rep.target,addedThisRun:pub,generated:gen,duplicates:dupE+dupN,rejected:invalid,review:out.review.length,tests:out.tests.map(t=>t.id+":"+t.ids.length+"("+t.newQ+" new)"),perCat:Object.fromEntries(Object.entries(perCat).map(([k,v])=>[k,v.published+"/"+v.target])),kb:Math.round(bytes/1024),rejectedSample:LOG.filter(l=>l.result!=="published").slice(0,12)},null,1));

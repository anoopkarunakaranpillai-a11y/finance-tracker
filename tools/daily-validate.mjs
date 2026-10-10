// Validates a daily learning file. Usage: node tools/daily-validate.mjs daily/YYYY-MM-DD.json
// Exit 0 = valid. Exit 1 = problems (printed as JSON so only the bad items get regenerated).
import fs from "node:fs";import crypto from "node:crypto";import path from "node:path";
const SUBJ=["gk","kh","ih","geo","con","kga","ca","sci","math","ment","eng","mal","comp","psc","econ","ph","law"];
const SUBJ_EN={comp:["quant","reas","veng","comp"],degree:["acc","dcs","dmath","dphy","dchem","dbio","decon","mgmt","hum","engg"]};
const NTYPES=["alphabet","numbers","counting","colors","shapes","animals","fruits","matching","memory","tracing","sounds","vocabulary","puzzles","observation","logic","rhymes"];
const SHAPES=["circle","square","triangle","rectangle","star","oval","heart","diamond","hexagon","balloon","flower","fish"];
const BAD=/\b(kill|blood|gun|knife|die|dead|death|sexy|hate|stupid|idiot|alcohol|beer|wine|cigarette)\b/i;
export const norm=s=>String(s||"").normalize("NFC").toLowerCase().replace(/[^\p{L}\p{N}\p{M}]+/gu,"");
export const hash=s=>crypto.createHash("sha256").update(norm(s)).digest("hex");
const file=process.argv[2];if(!file){console.error("usage: daily-validate.mjs <file>");process.exit(2)}
const dir=path.dirname(file),probs=[];const P=(where,msg)=>probs.push({where,msg});
let J;try{J=JSON.parse(fs.readFileSync(file,"utf8"))}catch(e){console.log(JSON.stringify({ok:false,problems:[{where:"file",msg:"not valid JSON: "+e.message}]},null,1));process.exit(1)}
const date=path.basename(file,".json");
if(J.date!==date)P("date",`date must be "${date}"`);
if(J.key!==`DAILY_LEARNING_${date.replace(/-/g,"_")}`)P("key",`key must be DAILY_LEARNING_${date.replace(/-/g,"_")}`);
let known=new Map();try{const h=JSON.parse(fs.readFileSync(path.join(dir,"hashes.json"),"utf8"));for(const [k,v] of Object.entries(h))if(v!==date)known.set(k,v)}catch(e){}
/* Navamika */
const N=J.navamika;if(!Array.isArray(N)||N.length<5)P("navamika","need at least 5 games");
const seenG=new Set();(N||[]).forEach((g,gi)=>{const w=`navamika[${gi}]`;
  if(!g||typeof g!=="object")return P(w,"not an object");
  if(!g.id||!/^[a-z0-9-]{2,30}$/.test(g.id))P(w+".id","id: lowercase letters/digits/dashes");
  if(!NTYPES.includes(g.type))P(w+".type","type must be one of "+NTYPES.join(","));
  if(!g.title||g.title.length>40)P(w+".title","title required, max 40 chars");if(seenG.has(norm(g.title)))P(w+".title","duplicate game title today");seenG.add(norm(g.title));
  if(!["easy","medium","challenge"].includes(g.difficulty))P(w+".difficulty","easy|medium|challenge");
  if(!g.instructions||g.instructions.length>140)P(w+".instructions","short instructions required (max 140)");
  if(g.type==="memory"){if(!Array.isArray(g.pairs)||g.pairs.length<2||g.pairs.length>6||new Set(g.pairs).size!==g.pairs.length)P(w+".pairs","memory games need pairs: 2-6 different emoji");return}
  if(g.type==="tracing"){if(!Array.isArray(g.chars)||g.chars.length<1||g.chars.length>8||g.chars.some(c=>!/^([A-Za-z]|[1-9]|1[0-9]|20)$/.test(c)))P(w+".chars","tracing games need chars: 1-8 items, letters A-Z/a-z or numbers 1-20");return}
  if(!Array.isArray(g.questions)||g.questions.length<5||g.questions.length>10)P(w+".questions","5-10 questions");
  const sq=new Set();(g.questions||[]).forEach((q,qi)=>{const ww=`${w}.questions[${qi}]`;
    if(!q.q||q.q.length>80)P(ww+".q","question required, max 80 chars");
    if(!Array.isArray(q.options)||q.options.length<2||q.options.length>4)P(ww+".options","2-4 options");
    else{q.options.forEach((o,oi)=>{if(!o||!(o.e||o.t||o.s||o.c))P(`${ww}.options[${oi}]`,"option needs e (emoji), t (word), s (shape) or c (colour)");if(o&&o.s&&!SHAPES.includes(o.s))P(`${ww}.options[${oi}].s`,"unknown shape");if(o&&o.c&&!/^#[0-9a-fA-F]{6}$/.test(o.c))P(`${ww}.options[${oi}].c`,"colour must be #RRGGBB")});
      const keys=q.options.map(o=>JSON.stringify([o.e||"",norm(o.t),o.s||"",(o.c||"").toLowerCase()]));if(new Set(keys).size!==keys.length)P(ww+".options","options must all be different")}
    if(!Number.isInteger(q.answer)||!q.options||q.answer<0||q.answer>=q.options.length)P(ww+".answer","answer must be the index of the correct option");
    const k=norm(q.q)+"|"+(q.show||"")+"|"+(q.options||[]).map(o=>o.e||o.t||o.s||o.c).join(",");if(sq.has(k))P(ww,"duplicate question inside this game");sq.add(k);
    const txt=[q.q,q.say,q.good,...(q.options||[]).map(o=>o.t)].join(" ");if(BAD.test(txt))P(ww,"not suitable for a KG1 child")})});
/* Arya */
const A=J.arya;if(!Array.isArray(A)||A.length<2||A.length>4)P("arya","2 to 4 tests (tests 1-2: Kerala PSC Malayalam, 25 questions; tests 3-4: English competitive/degree, 20 questions)");
const seenQ=new Map();(A||[]).forEach((t,ti)=>{const w=`arya[${ti}]`;
  if(t.n!==ti+1)P(w+".n",`n must be ${ti+1}`);if(!t.title)P(w+".title","title required");const EN=ti>=2;const NQ=EN?20:25;if(EN&&!["comp","degree"].includes(t.cat))P(w+".cat",'tests 3 and 4 need cat "comp" or "degree"');const SS=EN?(SUBJ_EN[t.cat]||[]):SUBJ;
  if(!Array.isArray(t.questions)||t.questions.length!==NQ)P(w+".questions",`exactly ${NQ} questions (has ${(t.questions||[]).length})`);
  if(Array.isArray(t.questions)&&t.questions.length===NQ){const c=[0,0,0,0],lo=EN?2:3,hi=EN?8:9;t.questions.forEach(q=>{if([0,1,2,3].includes(q.a))c[q.a]++});if(Math.max(...c)>hi||Math.min(...c)<lo)P(w+".questions",`correct answers must be spread over A-D (now A:${c[0]} B:${c[1]} C:${c[2]} D:${c[3]}; each must be ${lo}-${hi})`)}
  (t.questions||[]).forEach((q,qi)=>{const ww=`${w}.questions[${qi}]`;
    if(q.n!==qi+1)P(ww+".n",`n must be ${qi+1}`);
    if(!q.q||q.q.length<8)P(ww+".q","question text required");
    if(!Array.isArray(q.o)||q.o.length!==4||q.o.some(x=>!String(x||"").trim()))P(ww+".o","exactly 4 non-empty options");
    else if(new Set(q.o.map(norm)).size!==4)P(ww+".o","the 4 options must be different");
    if(![0,1,2,3].includes(q.a))P(ww+".a","a must be 0,1,2 or 3 (index of the one correct option)");
    if(!q.e||q.e.length<10)P(ww+".e","explanation required");
    else if(Array.isArray(q.o)&&[0,1,2,3].includes(q.a)){const right=norm(q.o[q.a]),e=norm(q.e);const others=q.o.filter((x,i)=>i!==q.a).map(norm).filter(x=>x.length>2&&e.includes(x)&&!right.includes(x));if(right.length>1&&!e.includes(right)&&others.length)P(ww+".e","explanation seems to support a different option than the answer")}
    if(!SS.includes(q.s))P(ww+".s","subject must be one of "+SS.join(","));
    if(!q.t)P(ww+".t","topic required");if(!["easy","medium","hard"].includes(q.d))P(ww+".d","easy|medium|hard");
    if(!Number.isInteger(q.marks)||q.marks<1)P(ww+".marks","positive integer");
    if(EN){if(q.lang!=="en")P(ww+".lang",'tests 3-4 are in English: lang:"en"')}else{if(q.s!=="eng"&&q.lang!=="ml")P(ww+".lang",'non-English questions must be in Malayalam with lang:"ml"');
    if(q.s!=="eng"&&!/[ഀ-ൿ]/.test(q.q))P(ww+".q","non-English questions must be written in Malayalam")}
    const h=hash(q.q+"|"+[...(q.o||[])].map(norm).sort().join("|"));const hq=hash(q.q);
    if(seenQ.has(hq))P(ww,"duplicate of "+seenQ.get(hq)+" today");seenQ.set(hq,ww);
    if(known.has(hq))P(ww,"repeats a question already used on "+known.get(hq))})});
const ok=!probs.length;console.log(JSON.stringify({ok,file,games:(N||[]).length,test1:((A||[])[0]||{}).questions?.length||0,test2:((A||[])[1]||{}).questions?.length||0,test3:((A||[])[2]||{}).questions?.length||0,test4:((A||[])[3]||{}).questions?.length||0,problems:probs},null,1));process.exit(ok?0:1);

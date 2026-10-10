// Shared helpers for the daily content pipeline.
import fs from "node:fs";import path from "node:path";import crypto from "node:crypto";
export const norm=s=>String(s||"").normalize("NFC").toLowerCase().replace(/[^\p{L}\p{N}\p{M}]+/gu,"");
export const hash=s=>crypto.createHash("sha256").update(norm(s)).digest("hex");
export const words=s=>new Set(String(s||"").normalize("NFC").toLowerCase().split(/[^\p{L}\p{N}\p{M}]+/u).filter(w=>w.length>1));
export function jaccard(a,b){if(!a.size||!b.size)return 0;let i=0;for(const w of a)if(b.has(w))i++;return i/(a.size+b.size-i)}
export const rd=(f,d)=>{try{return JSON.parse(fs.readFileSync(f,"utf8"))}catch(e){return d}};
export const wr=(f,o)=>{fs.mkdirSync(path.dirname(f),{recursive:true});fs.writeFileSync(f,JSON.stringify(o,null,1)+"\n")};
// Categories of the daily target and the subjects each may use.
export const CATS={
 gkca:{n:"General Knowledge & Current Affairs",subj:["gk","ca","kh","ih","geo","con","kga","econ","law","psc"]},
 eng:{n:"English Language & Grammar",subj:["eng","veng"]},
 quant:{n:"Quantitative Aptitude & Mathematics",subj:["math","quant"]},
 reas:{n:"Logical Reasoning",subj:["ment","reas"]},
 sci:{n:"Science & General Science",subj:["sci","ph"]},
 comp:{n:"Computer Knowledge",subj:["comp"]},
 deg:{n:"Degree-Level Subject Questions",subj:["acc","dcs","dmath","dphy","dchem","dbio","decon","mgmt","hum","engg"]},
 compx:{n:"Competitive & Recruitment Exams",subj:["quant","reas","veng","gk","comp","math","ment","eng","sci","con","econ"]}};
export const LEVELS=["school","hsec","diploma","degree","entrance","recruit"];
export const DIFFS=["easy","medium","hard","advanced"];
export const catOfSubject=s=>Object.entries(CATS).find(([k,c])=>k!=="compx"&&c.subj.includes(s))?.[0]||"compx";
export function loadConfig(dir){const def=rd(path.join(dir,"config.default.json"),{});const cur=rd(path.join(dir,"config.json"),{});return deepMerge(def,cur)}
function deepMerge(a,b){if(Array.isArray(b)||typeof b!=="object"||b===null)return b===undefined?a:b;const o=Object.assign({},a);for(const k of Object.keys(b))o[k]=k in a&&typeof a[k]==="object"&&!Array.isArray(a[k])?deepMerge(a[k],b[k]):b[k];return o}
// Safe arithmetic for the calculation check: digits, + - * / ( ) . % ^ and sqrt/pow/min/max/round/floor/ceil only.
export function calc(expr){const s=String(expr||"").replace(/\s+/g,"");if(!s||!/^[0-9+\-*/().,^a-z]*$/i.test(s))return NaN;const ok=s.replace(/sqrt|pow|min|max|round|floor|ceil|abs/g,"");if(/[a-z]/i.test(ok))return NaN;
  try{return Function("const {sqrt,pow,min,max,round,floor,ceil,abs}=Math;return ("+s.replace(/\^/g,"**")+")")()}catch(e){return NaN}}
export function numOf(opt){const s=String(opt||"").trim();const f=s.match(/^(-?\d+)\s*\/\s*(\d+)$/);if(f)return(+f[1])/(+f[2]);
  const m=s.match(/^(?:₹|Rs\.?|\$)?\s*(-?[\d,]*\.?\d+)\s*(?:%|°|[A-Za-z][A-Za-z\/² ]{0,12})?$/);return m?parseFloat(m[1].replace(/,/g,"")):NaN}

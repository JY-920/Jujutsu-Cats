import fs from 'node:fs/promises';
import path from 'node:path';
const endpoint='http://127.0.0.1:3000/bb-mcp';let id=1,session;
async function rpc(method,params={}){
 const r=await fetch(endpoint,{method:'POST',headers:{'Content-Type':'application/json',Accept:'application/json, text/event-stream',...(session?{'mcp-session-id':session}:{})},body:JSON.stringify({jsonrpc:'2.0',id:id++,method,params}),signal:AbortSignal.timeout(60000)});
 session=r.headers.get('mcp-session-id')||session;
 const value=await r.json();if(value.error)throw Error(JSON.stringify(value.error));return value.result;
}
export async function call(name,args={}){const r=await rpc('tools/call',{name,arguments:args});if(r.isError)throw Error(JSON.stringify(r));return r;}
await rpc('initialize',{protocolVersion:'2024-11-05',capabilities:{},clientInfo:{name:'rika-model-builder',version:'1.0'}});
if(process.argv[2]==='info')console.log(JSON.stringify(await call('get_project_info')));
if(process.argv[2]==='eval')console.log(JSON.stringify(await call('risky_eval',{code:await fs.readFile(process.argv[3],'utf8')})));
if(process.argv[2]==='tool')console.log(JSON.stringify(await call(process.argv[3],JSON.parse(process.argv[4]||'{}'))));
if(process.argv[2]==='shot'){
 const r=await call('capture_screenshot');const img=r.content.find(x=>x.type==='image');if(!img)throw Error('No image');
 await fs.writeFile(process.argv[3],Buffer.from(img.data,'base64'));console.log('Saved '+process.argv[3]);
}

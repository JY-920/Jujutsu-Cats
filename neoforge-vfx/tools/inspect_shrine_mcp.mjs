import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// Run with Blockbench and its MCP server already open. Uses a fresh project.
const art=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../art/malevolent-shrine');
const endpoint='http://127.0.0.1:3000/bb-mcp';let id=1,session;
async function rpc(method,params={}) {
 const response=await fetch(endpoint,{method:'POST',headers:{'Content-Type':'application/json',Accept:'application/json, text/event-stream',...(session?{'mcp-session-id':session}:{})},body:JSON.stringify({jsonrpc:'2.0',id:id++,method,params})});
 session=response.headers.get('mcp-session-id')||session;
 const result=await response.json();if(result.error)throw Error(JSON.stringify(result.error));return result.result;
}
async function call(name,args={}){const result=await rpc('tools/call',{name,arguments:args});if(result.isError)throw Error(JSON.stringify(result));return result;}
await rpc('initialize',{protocolVersion:'2024-11-05',capabilities:{},clientInfo:{name:'shrine-visual-inspector',version:'1.0'}});
await call('create_project',{name:'伏魔御厨子_视角检查',format:'free'});
const file=path.join(art,'伏魔御厨子_漫画还原.bbmodel').replaceAll('\\','/');
// Loading by path keeps the RPC short: sending a multi-megabyte eval can crash the MCP renderer.
await call('risky_eval',{code:`Codecs.project.parse(JSON.parse(require("fs").readFileSync(${JSON.stringify(file)},"utf8"))); Project.save_path=${JSON.stringify(file)}; Project.saved=true; "loaded"`});
const angles=JSON.parse(await fs.readFile(path.join(art,'previews/cameras.json'),'utf8'));
const views=[];
for(const [name,position,target]of angles){
 await call('set_camera_angle',{position,target,projection:'perspective'});
 const shot=(await call('capture_screenshot')).content.find(c=>c.type==='image');
 if(!shot)throw Error('Missing screenshot: '+name);
 const bytes=Buffer.from(shot.data,'base64');await fs.writeFile(path.join(art,'previews',name+'.png'),bytes);
 views.push({name,bytes:bytes.length});
}
await call('set_camera_angle',{position:angles[0][1],target:angles[0][2],projection:'perspective'});
console.log(JSON.stringify({views,project:await call('get_project_info')},null,2));

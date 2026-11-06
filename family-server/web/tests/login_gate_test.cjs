'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const source=fs.readFileSync(__dirname+'/../app.js','utf8').replace(/\ninit\(\);\s*$/,'');
const html=fs.readFileSync(__dirname+'/../index.html','utf8');
assert.match(html,/<div class="app-shell" hidden>/,'Portal must be closed before JavaScript starts');
function harness(options={}){
 const nodes=new Map();
 function node(key){if(!nodes.has(key))nodes.set(key,{hidden:key==='.app-shell',innerHTML:'',textContent:'',className:'',open:false,classList:{add(){},remove(){},toggle(){}},querySelector(s){return node(key+' '+s)},querySelectorAll(){return[]},addEventListener(){},focus(){},showModal(){this.open=true},close(){this.open=false}});return nodes.get(key)}
 for(const key of ['#auth-content','#main']){let content='';Object.defineProperty(node(key),'innerHTML',{get(){return content},set(v){content=v;node('#connection-code').value='';}});}
 const document={title:'',querySelector:node,querySelectorAll(){return[]},addEventListener(){}};
 const child={id:'child-1',name:'PRIVATE_CHILD',paired:true,version:1,applied:1,completed:[],seen:1,config:{limit:2,tasks:[{id:'t1',title:'PRIVATE_TASK',repeat:'daily'}]}};
 const calls=[],requests=[];
 const context=vm.createContext({document,location:{hash:options.hash||'',pathname:'/'},history:{replaceState(){}},window:{addEventListener(){}},setTimeout(){},clearTimeout(){},setInterval(){return 1},clearInterval(){},console,crypto:{getRandomValues(){}},FormData:class{},fetch:async(url,request={})=>{
  calls.push(url);requests.push({url,body:request.body?JSON.parse(request.body):null});
  if(options.fail)throw Error('Offline');
  if(options.fetch)return options.fetch(url,request);
  let data=url==='/web-api/status'?{configured:true,accountsEnabled:true,authenticated:!!options.authenticated}:url==='/web-api/children'?{children:[child]}:url==='/web-api/account/status'?{account:{email:'owner@example.test',verified:true}}:{ok:true};
  return{ok:true,status:200,json:async()=>data};
 }});
 vm.runInContext(source,context);return{node,calls,requests,context,run:code=>vm.runInContext(code,context)};
}
(async()=>{
 let release;const pending=harness({fetch:()=>new Promise(resolve=>release=resolve)});const init=pending.run('init()');
 assert.equal(pending.node('.app-shell').hidden,true);assert.match(pending.node('#auth-content').innerHTML,/Anmeldung wird geprüft/);
 release({ok:true,status:200,json:async()=>({configured:true,accountsEnabled:true,authenticated:false})});await init;
 assert.equal(pending.node('.app-shell').hidden,true);assert.match(pending.node('#auth-content').innerHTML,/account-login/);assert.equal(pending.node('#main').innerHTML,'');
 console.log('PASS initial render and pending/anonymous authentication stay closed');
 const deep=harness({hash:'#tasks'});await deep.run('init()');deep.run("goto('devices')");assert.equal(deep.node('.app-shell').hidden,true);assert.equal(deep.node('#main').innerHTML,'');assert.equal(deep.run('devices.length'),0);
 console.log('PASS private deep links cannot expose the portal or example data');
 const failed=harness({fail:true});await failed.run('init()');assert.equal(failed.node('.app-shell').hidden,true);assert.match(failed.node('#auth-content').innerHTML,/Erneut prüfen/);
 console.log('PASS network failure fails closed');
 const live=harness({authenticated:true});await live.run('init()');assert.equal(live.node('.app-shell').hidden,false);assert.equal(live.node('#auth-screen').hidden,true);assert.match(live.node('#main').innerHTML,/PRIVATE_CHILD/);
 live.run('expire()');assert.equal(live.node('.app-shell').hidden,true);assert.equal(live.node('#main').innerHTML,'');assert.equal(live.run('devices.length'),0);assert.equal(live.run('dirty'),false);
 console.log('PASS confirmed session unlocks; expiry removes private DOM and drafts');
 const out=harness({authenticated:true});await out.run('init()');out.run('actions.logout()');await out.run('dialogAction()');assert.equal(out.node('.app-shell').hidden,true);assert.equal(out.node('#main').innerHTML,'');assert.ok(out.calls.includes('/web-api/logout'));
 console.log('PASS logout revokes the browser session and closes the portal');
 const revoked=harness({fetch:async(url)=>({ok:url!=='/web-api/children',status:url==='/web-api/children'?401:200,json:async()=>url==='/web-api/status'?{configured:true,accountsEnabled:true,authenticated:true}:{error:'Expired'}})});await revoked.run('init()');assert.equal(revoked.node('.app-shell').hidden,true);assert.equal(revoked.node('#main').innerHTML,'');
 console.log('PASS revoked session never displays private content');
 const codeLogin=harness();await codeLogin.run('init()');codeLogin.node('#connection-code').value='abcde fghjk';await codeLogin.run("actions['login-code']()");
 assert.equal(codeLogin.requests.find(r=>r.url==='/web-api/login').body.code,'ABCDEFGHJK');assert.equal(codeLogin.node('.app-shell').hidden,false);
 console.log('PASS web code is captured before loading render clears the form');
 const pairing=harness({authenticated:true});await pairing.run('init()');pairing.run("goto('connection')");pairing.node('#connection-code').value='abcde-fghjk';await pairing.run("actions['claim-code']()");
 assert.equal(pairing.requests.find(r=>r.url==='/web-api/claims').body.code,'ABCDEFGHJK');
 console.log('PASS child pairing code survives the loading render');
 const invalid=harness();await invalid.run('init()');invalid.node('#connection-code').value='short';assert.throws(()=>invalid.run("actions['login-code']()"),/10 Zeichen/);assert.ok(!invalid.calls.includes('/web-api/login'));
 console.log('PASS incomplete codes are rejected locally without consuming a server request');

})().catch(e=>{console.error(e);process.exitCode=1});

"""End-to-end first installation on an isolated database, test TLS shim only."""
import base64,hashlib,hmac,json,os,pathlib,socket,struct,subprocess,tempfile,time,unittest,urllib.request,urllib.error
ROOT=pathlib.Path(__file__).resolve().parents[1]
class SetupHTTPTests(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  if os.environ.get('OSMON_TEST_DATABASE')!='osmon_test':raise RuntimeError('Dedicated test environment required')
  cls.temp=tempfile.TemporaryDirectory();cls.directory=pathlib.Path(cls.temp.name);cls.cfg=cls.directory/'config.php'
  result=subprocess.run(['php',str(ROOT/'tests/setup_http_fixture.php'),str(cls.directory)],capture_output=True,check=True,text=True)
  cls.code=json.loads(result.stdout)['code']
  with socket.socket()as s:s.bind(('127.0.0.1',0));cls.port=s.getsockname()[1]
  env=dict(os.environ,OSMON_CONFIG_FILE=str(cls.cfg),OSMON_HTTP_TEST='1',OSMON_SETUP_ORIGIN='https://parents.example.test')
  cls.proc=subprocess.Popen(['php','-S',f'127.0.0.1:{cls.port}','-t',str(ROOT/'public'),str(ROOT/'tests/http_router.php')],env=env,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
  for _ in range(100):
   if cls.proc.poll()is not None:raise RuntimeError('PHP server failed')
   try:
    with socket.create_connection(('127.0.0.1',cls.port),timeout=.1):break
   except OSError:time.sleep(.05)
  else:raise RuntimeError('PHP server not ready')
 @classmethod
 def tearDownClass(cls):cls.proc.terminate();cls.proc.wait(timeout=5);cls.temp.cleanup()
 def req(self,method,path,body=None,cookie='',csrf='',headers=None,raw=None):
  h={'Host':'parents.example.test','Content-Type':'application/json','Origin':'https://parents.example.test','X-OSMon-Setup':'1','X-OSMon-CSRF':csrf,'Cookie':cookie};h.update(headers or {})
  data=raw if raw is not None else json.dumps(body).encode()if body is not None else None
  request=urllib.request.Request(f'http://127.0.0.1:{self.port}'+path,data=data,method=method,headers=h)
  try:r=urllib.request.urlopen(request)
  except urllib.error.HTTPError as e:r=e
  content=r.read();value=json.loads(content)if r.headers.get('Content-Type','').startswith('application/json')else content
  return r.status,r.headers,value
 def test_01_bootstrap_transport_and_origin(self):
  self.assertEqual(self.req('GET','/setup')[0],200)
  self.assertFalse(self.req('GET','/setup-api/status')[2]['authenticated'])
  self.assertFalse((self.directory/'setup-enable.txt').exists())
  self.assertEqual(self.req('GET','/setup',headers={'X-Test-HTTP-Only':'1','X-Forwarded-Proto':'https'})[0],400)
  self.assertEqual(self.req('GET','/setup',headers={'Host':'evil.example'})[0],421)
  self.assertEqual(self.req('POST','/setup-api/unlock',{'code':self.code},headers={'Origin':'https://evil.example'})[0],403)
  self.assertEqual(self.req('POST','/setup-api/unlock',raw=b'[]')[0],400)
 def test_02_complete_real_http_installation(self):
  status,h,r=self.req('POST','/setup-api/unlock',{'code':self.code});self.assertEqual(status,200);self.assertNotIn('session',r)
  cookie=h['Set-Cookie'];self.assertIn('HttpOnly',cookie);self.assertIn('Secure',cookie);self.assertIn('SameSite=Strict',cookie);cookie=cookie.split(';')[0];csrf=r['csrf']
  db={'host':os.getenv('OSMON_TEST_HOST','127.0.0.1'),'port':int(os.getenv('OSMON_TEST_PORT','3306')),'database':'osmon_setup_http_test','username':os.getenv('OSMON_TEST_USER','root'),'password':os.getenv('OSMON_TEST_PASSWORD','test-only')}
  self.assertEqual(self.req('POST','/setup-api/database',db,cookie=cookie)[0],403)
  status,_,r=self.req('POST','/setup-api/database',db,cookie=cookie,csrf=csrf);self.assertEqual(status,200);self.assertEqual(r['stage'],'admin');self.assertNotIn('password',r)
  body={'username':'firstoperator','password':'Test-only-setup-password','repeat':'Test-only-setup-password'}
  status,_,r=self.req('POST','/setup-api/admin',body,cookie=cookie,csrf=csrf);self.assertEqual(status,200);self.assertNotIn('password',r)
  secret=base64.b32decode(r['secret']);step=int(time.time()/30);digest=hmac.new(secret,struct.pack('>Q',step),hashlib.sha1).digest();offset=digest[-1]&15;code=f"{(struct.unpack('>I',digest[offset:offset+4])[0]&0x7fffffff)%1000000:06d}"
  status,h,r=self.req('POST','/setup-api/finish',{'code':code},cookie=cookie,csrf=csrf);self.assertEqual(status,200);self.assertTrue(r['complete']);self.assertIn('Max-Age=0',h['Set-Cookie'])
  self.assertTrue(self.cfg.exists());self.assertFalse((self.directory/'.setup-state.json').exists());self.assertTrue((self.directory/'.setup-complete').exists())
  self.assertTrue(self.req('GET','/web-api/status')[2]['configured'])
 def test_03_finished_wizard_cannot_reopen(self):
  self.assertTrue(self.req('GET','/setup-api/status')[2]['complete'])
  self.assertEqual(self.req('POST','/setup-api/unlock',{'code':self.code})[0],410)
  self.assertEqual(self.req('POST','/setup-api/database',{})[0],410)
if __name__=='__main__':unittest.main()

import json,os,socket,subprocess,tempfile,time,unittest,urllib.request,urllib.error
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
class AccountsHTTP(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  subprocess.run(['php',str(ROOT/'tests/account_http_fixture.php')],check=True)
  cls.temp=tempfile.TemporaryDirectory();cfg=Path(cls.temp.name)/'config.php'
  settings={'origin':'https://parents.example.test','host':os.getenv('OSMON_TEST_HOST','127.0.0.1'),'port':int(os.getenv('OSMON_TEST_PORT','3306')),'database':'osmon_test','username':os.getenv('OSMON_TEST_USER','root'),'password':os.getenv('OSMON_TEST_PASSWORD','test-only'),'rate_secret':'a'*64,'admin_key':(b'k'*32).hex()}
  cfg.write_text('<?php return json_decode('+json.dumps(json.dumps(settings))+',true);')
  with socket.socket()as s:s.bind(('127.0.0.1',0));cls.port=s.getsockname()[1]
  cls.proc=subprocess.Popen(['php','-S',f'127.0.0.1:{cls.port}','-t',str(ROOT/'public'),str(ROOT/'tests/http_router.php')],env=dict(os.environ,OSMON_CONFIG_FILE=str(cfg),OSMON_HTTP_TEST='1'),stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
  for _ in range(100):
   try:
    with socket.create_connection(('127.0.0.1',cls.port),timeout=.1):break
   except OSError:time.sleep(.05)
  else:raise RuntimeError('PHP server not ready')
 @classmethod
 def tearDownClass(cls):cls.proc.terminate();cls.proc.wait(timeout=5);cls.temp.cleanup()
 def req(self,m,path,b=None,cookie='',token='',origin='https://parents.example.test'):
  h={'Host':'parents.example.test','Origin':origin,'X-OSMon-Web':'1','Content-Type':'application/json','Cookie':cookie,'Authorization':'Bearer '+token}
  r=urllib.request.Request(f'http://127.0.0.1:{self.port}'+path,data=None if b is None else json.dumps(b).encode(),method=m,headers=h)
  try:s=urllib.request.urlopen(r)
  except urllib.error.HTTPError as e:s=e
  return s.status,s.headers,json.loads(s.read())
 def test_native_and_browser_login_and_logout(self):
  body={'email':'httpaccount@example.test','password':'Test-only-password-123'}
  status,_,native=self.req('POST','/v1/account/login',body);self.assertEqual(status,200);self.assertIn('token',native)
  self.assertEqual(self.req('GET','/v1/account/status',token=native['token'])[2]['account']['email'],body['email'])
  self.assertEqual(self.req('POST','/web-api/account/login',body,origin='https://evil.example')[0],403)
  status,h,browser=self.req('POST','/web-api/account/login',body);self.assertEqual(status,200);self.assertNotIn('token',browser)
  raw=h['Set-Cookie'];self.assertIn('HttpOnly',raw);self.assertIn('Secure',raw);self.assertIn('SameSite=Strict',raw);cookie=raw.split(';')[0]
  self.assertEqual(self.req('GET','/web-api/account/status',cookie=cookie)[2]['account']['verified'],True)
  self.assertTrue(self.req('GET','/web-api/status')[2]['accountsEnabled'])
  self.assertEqual(self.req('GET','/web-api/children',cookie=cookie)[0],200)
  self.assertEqual(self.req('DELETE','/web-api/logout',cookie=cookie)[0],200)
  self.assertEqual(self.req('GET','/web-api/children',cookie=cookie)[0],401)
  self.assertEqual(self.req('GET','/v1/children',token=native['token'])[0],200)
 def test_public_routes_do_not_expose_account_or_secrets(self):
  for _ in range(6):self.assertEqual(self.req('GET','/web-api/account/status')[0],401)
  self.assertEqual(self.req('POST','/v1/account/login',{'email':'absent@example.test','password':'wrong'})[0],401)
  a=self.req('POST','/web-api/account/forgot',{'email':'absent@example.test'})
  b=self.req('POST','/web-api/account/forgot',{'email':'httpaccount@example.test'})
  self.assertEqual(a[0],200);self.assertEqual(a[2],b[2]);self.assertNotIn('token',b[2])
  self.assertEqual(self.req('POST','/admin-api/accounts/install',{})[0],403)
if __name__=='__main__':unittest.main()

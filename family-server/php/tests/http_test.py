"""Real PHP HTTP transport test, using an isolated test-only TLS shim router."""
import json, os, pathlib, socket, subprocess, tempfile, time, unittest, urllib.request, urllib.error
ROOT=pathlib.Path(__file__).resolve().parents[1]
class HTTPTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if os.environ.get('OSMON_TEST_DATABASE')!='osmon_test':raise RuntimeError('Dedicated test database required')
        subprocess.run(['php',str(ROOT/'tests/admin_http_fixture.php')],check=True)
        cls.temp=tempfile.TemporaryDirectory();cfg=pathlib.Path(cls.temp.name)/'config.php'
        # Secret values are test-only; never printed, never shipped.
        settings={'origin':'https://parents.example.test','host':os.getenv('OSMON_TEST_HOST','127.0.0.1'),'port':int(os.getenv('OSMON_TEST_PORT','3306')),'database':'osmon_test','username':os.getenv('OSMON_TEST_USER','root'),'password':os.getenv('OSMON_TEST_PASSWORD','test-only'),'rate_secret':'a'*64,'admin_key':(b'k'*32).hex()}
        cfg.write_text('<?php return json_decode('+json.dumps(json.dumps(settings))+',true);')
        with socket.socket() as s:s.bind(('127.0.0.1',0));cls.port=s.getsockname()[1]
        env=dict(os.environ,OSMON_CONFIG_FILE=str(cfg),OSMON_HTTP_TEST='1')
        cls.proc=subprocess.Popen(['php','-S',f'127.0.0.1:{cls.port}','-t',str(ROOT/'public'),str(ROOT/'tests/http_router.php')],env=env,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
        for _ in range(100):
            if cls.proc.poll() is not None:raise RuntimeError('PHP server failed')
            try:
                with socket.create_connection(('127.0.0.1',cls.port),timeout=.1):break
            except OSError:time.sleep(.05)
        else:raise RuntimeError('PHP server not ready')
    @classmethod
    def tearDownClass(cls):cls.proc.terminate();cls.proc.wait(timeout=5);cls.temp.cleanup()
    def req(self,method,path,body=None,token='',cookie='',headers=None,raw=None):
        h={'Host':'parents.example.test','Content-Type':'application/json','Origin':'https://parents.example.test','X-OSMon-Web':'1','Authorization':'Bearer '+token,'Cookie':cookie};h.update(headers or {})
        data=raw if raw is not None else json.dumps(body).encode() if body is not None else None
        request=urllib.request.Request(f'http://127.0.0.1:{self.port}'+path,data=data,method=method,headers=h)
        try:response=urllib.request.urlopen(request)
        except urllib.error.HTTPError as e:response=e
        return response.status,response.headers,response.read()
    def obj(self,*args,**kwargs):
        status,headers,raw=self.req(*args,**kwargs);return status,headers,json.loads(raw)
    def test_01_https_host_and_files(self):
        self.assertEqual(self.req('GET','/',headers={'X-Test-HTTP-Only':'1','X-Forwarded-Proto':'https'})[0],400)
        self.assertEqual(self.req('GET','/',headers={'Host':'evil.example'})[0],421)
        status,h,raw=self.req('GET','/');self.assertEqual(status,200);self.assertIn(b'OS-Mon Eltern',raw);self.assertIn("frame-ancestors 'none'",h['Content-Security-Policy'])
        for path in ['/private/config.php','/config.php','/schema.sql','/index.php','/v1/children?token=x']:
            self.assertIn(self.req('GET',path)[0],[400,404])
    def test_02_json_and_size(self):
        self.assertEqual(self.req('POST','/v1/pairings',raw=b'[]')[0],400)
        self.assertEqual(self.req('POST','/v1/pairings',raw=b'{broken')[0],400)
        self.assertEqual(self.req('POST','/v1/pairings',raw=b' '*17000)[0],413)
    def test_03_native_web_and_location_roundtrip(self):
        status,_,p=self.obj('POST','/v1/parents',{});self.assertEqual(status,200);parent=p['token']
        _,_,code=self.obj('POST','/v1/web-codes',{},token=parent)
        self.assertEqual(self.obj('POST','/web-api/login',{'code':code['code']},headers={'Origin':'https://evil.example'})[0],403)
        status,h,result=self.obj('POST','/web-api/login',{'code':code['code']});self.assertEqual(status,200);self.assertNotIn('token',result)
        raw=h['Set-Cookie'];self.assertIn('HttpOnly',raw);self.assertIn('Secure',raw);self.assertIn('SameSite=Strict',raw);cookie=raw.split(';')[0]
        _,_,pair=self.obj('POST','/v1/pairings',{'name':'HTTP Test','config':{'limit':2,'tasks':[]}})
        self.assertEqual(self.obj('POST','/web-api/claims',{'code':pair['code']},cookie=cookie)[0],200)
        _,_,children=self.obj('GET','/web-api/children',cookie=cookie);child=children['children'][0];ident=child['id']
        self.assertEqual(self.obj('PUT',f'/web-api/children/{ident}/config',{'version':1,'config':{'limit':0,'tasks':[]}},cookie=cookie)[0],200)
        _,_,device=self.obj('GET','/v1/device',token=pair['token']);self.assertEqual(device['config']['limit'],0)
        self.assertEqual(self.obj('POST',f'/web-api/children/{ident}/location-request',{},cookie=cookie)[0],409)
        self.assertEqual(self.obj('PUT','/v1/device/location-sharing',{'enabled':True},token=pair['token'])[0],200)
        status,_,req=self.obj('POST',f'/web-api/children/{ident}/location-request',{},cookie=cookie);self.assertEqual(status,200)
        fix={'latitude':53.86,'longitude':10.68,'accuracy':100,'measured':time.time(),'requestId':req['request']['id']}
        self.assertEqual(self.obj('POST','/v1/device/location',fix,token=pair['token'])[0],200)
        _,_,children=self.obj('GET','/web-api/children',cookie=cookie);self.assertEqual(children['children'][0]['locationRequest']['state'],'received')
        self.obj('PUT','/v1/device/location-sharing',{'enabled':False},token=pair['token'])
        _,_,children=self.obj('GET','/web-api/children',cookie=cookie);self.assertIsNone(children['children'][0]['location'])
        self.assertEqual(self.obj('DELETE','/web-api/logout',cookie=cookie)[0],200)
        self.assertFalse(self.obj('GET','/web-api/status',cookie=cookie)[2]['authenticated'])
        self.obj('DELETE','/v1/parents',token=parent)
    def test_04_admin_auth_cookie_csrf_and_privacy(self):
        import hmac, hashlib, struct
        step=int(time.time()/30);digest=hmac.new(b'12345678901234567890',struct.pack('>Q',step),hashlib.sha1).digest();offset=digest[-1]&15;code=f"{(struct.unpack('>I',digest[offset:offset+4])[0]&0x7fffffff)%1000000:06d}"
        h={'X-OSMon-Admin':'1'}
        self.assertEqual(self.obj('GET','/admin-api/overview')[0],401)
        self.assertEqual(self.req('GET','/admin')[0],200)
        body={'username':'httpoperator','password':'Test-only-password-123','code':code}
        self.assertEqual(self.obj('POST','/admin-api/login',body,headers=dict(h,Origin='https://evil.example'))[0],403)
        status,headers,result=self.obj('POST','/admin-api/login',body,headers=h);self.assertEqual(status,200);self.assertNotIn('token',result)
        cookie=headers['Set-Cookie'];self.assertIn('__Host-osmon_admin=',cookie);self.assertIn('HttpOnly',cookie);self.assertIn('Secure',cookie);cookie=cookie.split(';')[0]
        self.assertEqual(self.obj('POST','/admin-api/families',{'page':0},cookie=cookie,headers=h)[0],403)
        h['X-OSMon-CSRF']=result['csrf']
        self.assertEqual(self.obj('POST','/admin-api/families',{'page':0},cookie=cookie,headers=h)[0],200)
        status,_,summary=self.obj('GET','/admin-api/overview',cookie=cookie);self.assertEqual(status,200);self.assertNotIn('location',summary)
        self.assertEqual(self.obj('GET','/web-api/children',cookie=cookie)[0],401)
        self.assertEqual(self.obj('DELETE','/admin-api/logout',{},cookie=cookie,headers=h)[0],200)
        self.assertFalse(self.obj('GET','/admin-api/status',cookie=cookie)[2]['authenticated'])
if __name__=='__main__':unittest.main()

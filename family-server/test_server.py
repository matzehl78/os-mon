import json
import threading
import unittest
import urllib.request
import urllib.error
from server import APIError, Store, Server, digest

class FamilyTests(unittest.TestCase):
    def setUp(self):
        self.now=1000000
        self.s=Store(':memory:',clock=lambda:self.now)
        self.p=self.call('POST','/v1/parents')['token']
        self.other=self.call('POST','/v1/parents')['token']
        self.pair=self.call('POST','/v1/pairings',body={'name':'Testgerät','config':{'limit':2,'tasks':[]}})
        self.child=self.pair['token']
    def tearDown(self): self.s.db.close()
    def call(self,method,path,token='',body=None): return self.s.dispatch(method,path,token,body or {})
    def claim(self): self.call('POST','/v1/claims',self.p,{'code':self.pair['code']})
    def device(self): return self.call('GET','/v1/device',self.child)
    def assert_error(self,code,method,path,token='',body=None):
        with self.assertRaises(APIError) as e:self.call(method,path,token,body)
        self.assertEqual(e.exception.status,code)
    def test_unpaired_cannot_ack_and_claim_is_one_time(self):
        self.assertFalse(self.device()['paired'])
        self.assert_error(409,'POST','/v1/device/ack',self.child,{'version':1,'completed':[],'day':1})
        self.claim();self.assertTrue(self.device()['paired'])
        self.assert_error(404,'POST','/v1/claims',self.other,{'code':self.pair['code']})
    def test_pairing_expiry(self):
        self.now+=601
        self.assert_error(404,'POST','/v1/claims',self.p,{'code':self.pair['code']})
        self.assert_error(401,'GET','/v1/device',self.child)
    def test_tenant_isolation_and_child_cannot_set_config(self):
        self.claim();ident=self.device()['id'];path=f'/v1/children/{ident}/config'
        body={'version':1,'config':{'limit':0,'tasks':[]}}
        self.assert_error(404,'PUT',path,self.other,body)
        self.assert_error(401,'PUT',path,self.child,body)
        self.assert_error(401,'GET','/v1/children',self.child)
        self.assertEqual(self.call('GET','/v1/children',self.other)['children'],[])
    def test_config_revision_ack_and_invalid_status(self):
        self.claim();ident=self.device()['id'];tid='12345678-1234-1234-1234-123456789012'
        body={'version':1,'config':{'limit':0,'tasks':[{'id':tid,'title':'Zähne putzen','repeat':'daily'}]}}
        self.call('PUT',f'/v1/children/{ident}/config',self.p,body)
        self.assert_error(409,'PUT',f'/v1/children/{ident}/config',self.p,body)
        self.assert_error(409,'POST','/v1/device/ack',self.child,{'version':1,'completed':[],'day':1})
        self.assert_error(400,'POST','/v1/device/ack',self.child,{'version':2,'completed':['foreign'],'day':1})
        self.call('POST','/v1/device/ack',self.child,{'version':2,'completed':[tid],'day':1})
        d=self.call('GET','/v1/children',self.p)['children'][0]
        self.assertEqual((d['applied'],d['completed'],d['config']['limit']),(2,[tid],0))
    def test_validation(self):
        self.claim();path=f'/v1/children/{self.device()["id"]}/config'
        for config in [{'limit':True,'tasks':[]},{'limit':4,'tasks':[]},{'limit':2,'tasks':[{}]},{'limit':2,'tasks':[],'evil':'x'}]:
            self.assert_error(400,'PUT',path,self.p,{'version':1,'config':config})
    def test_revocation(self):
        self.claim();ident=self.device()['id']
        self.assert_error(404,'DELETE',f'/v1/children/{ident}',self.other)
        self.call('DELETE',f'/v1/children/{ident}',self.p)
        self.assert_error(401,'GET','/v1/device',self.child)
    def test_delete_parent_cascades(self):
        self.claim();self.call('DELETE','/v1/parents',self.p)
        self.assert_error(401,'GET','/v1/device',self.child)
        self.assert_error(401,'GET','/v1/children',self.p)
    def test_expired_parent_revokes_children(self):
        self.claim();self.now+=91*86400
        self.assert_error(401,'GET','/v1/device',self.child)
        self.assertEqual(self.s.db.execute('SELECT count(*) FROM devices').fetchone()[0],0)
    def test_tokens_and_code_are_stored_hashed(self):
        dump=''.join(self.s.db.iterdump())
        for secret in [self.p,self.other,self.child,self.pair['code']]:self.assertNotIn(secret,dump)
        self.assertIn(digest(self.child),dump)
    def test_child_can_remove_connection(self):
        self.claim();self.call('DELETE','/v1/device',self.child)
        self.assertEqual(self.call('GET','/v1/children',self.p)['children'],[])

class HTTPTests(unittest.TestCase):
    def test_real_http_json_validation_and_pairing_flow(self):
        s=Server(('127.0.0.1',0),Store(':memory:'))
        thread=threading.Thread(target=s.serve_forever,daemon=True);thread.start()
        base=f'http://127.0.0.1:{s.server_port}'
        def req(path,body,token=''):
            r=urllib.request.Request(base+path,data=json.dumps(body).encode(),headers={'Content-Type':'application/json','Authorization':'Bearer '+token})
            with urllib.request.urlopen(r) as response:return json.load(response)
        try:
            p=req('/v1/parents',{})['token']
            pair=req('/v1/pairings',{'name':'Demo','config':{'limit':2,'tasks':[]}})
            self.assertTrue(req('/v1/claims',{'code':pair['code']},p)['ok'])
            for i in range(10):
                try:req('/v1/claims',{'code':'AAAAAAAAAA'},p)
                except urllib.error.HTTPError as e:
                    if i==9:self.assertEqual(e.code,429)
            r=urllib.request.Request(base+'/v1/pairings',data=b'[]')
            with self.assertRaises(urllib.error.HTTPError) as error:urllib.request.urlopen(r)
            self.assertEqual(error.exception.code,400)
        finally:s.shutdown();s.server_close();s.store.db.close();thread.join()


class WebAccessTests(unittest.TestCase):
    setUp=FamilyTests.setUp
    tearDown=FamilyTests.tearDown
    call=FamilyTests.call
    claim=FamilyTests.claim
    device=FamilyTests.device
    assert_error=FamilyTests.assert_error
    def test_app_and_browser_share_devices_and_scope(self):
        self.claim()
        code=self.call('POST','/v1/web-codes',self.p)['code']
        token=self.call('POST','/v1/web-codes/redeem',body={'code':code})['token']
        self.assertEqual(self.call('GET','/v1/children',token),self.call('GET','/v1/children',self.p))
        self.assert_error(404,'POST','/v1/web-codes/redeem',body={'code':code})
        self.assert_error(404,'DELETE',f'/v1/children/{self.device()["id"]}',self.other)
        self.call('DELETE','/v1/browser-session',token)
        self.assert_error(401,'GET','/v1/children',token)
        self.assertEqual(len(self.call('GET','/v1/children',self.p)['children']),1)
    def test_web_code_expiry_and_parent_revocation(self):
        self.claim();code=self.call('POST','/v1/web-codes',self.p)['code'];self.now+=301
        self.assert_error(404,'POST','/v1/web-codes/redeem',body={'code':code})
        code=self.call('POST','/v1/web-codes',self.p)['code'];token=self.call('POST','/v1/web-codes/redeem',body={'code':code})['token']
        self.call('DELETE','/v1/parents',self.p)
        self.assert_error(401,'GET','/v1/children',token)
    def test_browser_session_expires_after_seven_days(self):
        token=self.call('POST','/v1/browser-access')['token'];self.now+=7*86400+1
        self.assert_error(401,'GET','/v1/children',token)

class BrowserHTTPTests(unittest.TestCase):
    def test_cookie_csrf_static_and_shared_device_control(self):
        from http.cookies import SimpleCookie
        s=Server(('127.0.0.1',0),Store(':memory:'));s.web_origin=f'http://127.0.0.1:{s.server_port}'
        thread=threading.Thread(target=s.serve_forever,daemon=True);thread.start();base=s.web_origin
        def req(method,path,body=None,cookie='',origin=None,token=''):
            headers={'Content-Type':'application/json','X-OSMon-Web':'1','Origin':origin or base,'Cookie':cookie,'Authorization':'Bearer '+token}
            r=urllib.request.Request(base+path,data=json.dumps(body).encode() if body is not None else None,method=method,headers=headers)
            return urllib.request.urlopen(r)
        try:
            with req('GET','/') as response:
                self.assertIn('OS-Mon Eltern',response.read().decode());self.assertIn("frame-ancestors 'none'",response.headers['Content-Security-Policy'])
            with req('POST','/v1/parents',{}) as response:parent=json.load(response)['token']
            with req('POST','/v1/web-codes',{},token=parent) as response:code=json.load(response)['code']
            with self.assertRaises(urllib.error.HTTPError) as e:req('POST','/web-api/login',{'code':code},origin='https://evil.invalid')
            self.assertEqual(e.exception.code,403)
            with req('POST','/web-api/login',{'code':code}) as response:
                result=json.load(response);self.assertNotIn('token',result)
                raw=response.headers['Set-Cookie'];self.assertIn('HttpOnly',raw);self.assertIn('Secure',raw);self.assertIn('SameSite=Strict',raw);cookie=raw.split(';')[0]
            with req('POST','/v1/pairings',{'name':'Webtest','config':{'limit':2,'tasks':[]}}) as response:pair=json.load(response)
            with req('POST','/web-api/claims',{'code':pair['code']},cookie) as response:self.assertTrue(json.load(response)['ok'])
            with req('GET','/web-api/children',cookie=cookie) as response:child=json.load(response)['children'][0]
            with req('PUT',f'/web-api/children/{child["id"]}/config',{'version':1,'config':{'limit':0,'tasks':[]}},cookie) as response:self.assertTrue(json.load(response)['ok'])
            with req('GET','/v1/device',token=pair['token']) as response:self.assertEqual(json.load(response)['config']['limit'],0)
            with req('DELETE','/web-api/logout',cookie=cookie) as response:self.assertIn('Max-Age=0',response.headers['Set-Cookie'])
            with req('GET','/web-api/status',cookie=cookie) as response:self.assertFalse(json.load(response)['authenticated'])
        finally:s.shutdown();s.server_close();s.store.db.close();thread.join()

if __name__=="__main__":unittest.main()

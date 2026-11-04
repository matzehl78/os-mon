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

if __name__=='__main__':unittest.main()

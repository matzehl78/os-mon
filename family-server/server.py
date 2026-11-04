"""OS-Mon Family API. Standard library only. Bind behind an HTTPS reverse proxy."""
import hashlib
import json
import os
import secrets
import sqlite3
import threading
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

class APIError(Exception):
    def __init__(self, status, message): self.status, self.message = status, message

def digest(token): return hashlib.sha256(token.encode()).hexdigest()

def validate_config(value):
    if not isinstance(value, dict) or set(value) != {'limit', 'tasks'}: raise APIError(400, 'Ungültige Einstellungen.')
    if type(value['limit']) is not int or not 0 <= value['limit'] <= 3: raise APIError(400, 'Spielelimit muss 0 bis 3 sein.')
    tasks = value['tasks']
    if not isinstance(tasks, list) or len(tasks) > 12: raise APIError(400, 'Höchstens 12 Aufgaben.')
    ids = set()
    for task in tasks:
        if not isinstance(task, dict) or set(task) != {'id', 'title', 'repeat'}: raise APIError(400, 'Ungültige Aufgabe.')
        try: uuid.UUID(task['id'])
        except (ValueError, TypeError, AttributeError): raise APIError(400, 'Ungültige Aufgaben-ID.')
        if task['id'] in ids: raise APIError(400, 'Doppelte Aufgabe.')
        ids.add(task['id'])
        if not isinstance(task['title'], str) or not 1 <= len(task['title'].strip()) <= 40 or any(ord(c) < 32 for c in task['title']): raise APIError(400, 'Aufgabentitel: 1 bis 40 Zeichen.')
        if task['repeat'] not in ['once', 'daily', 'weekdays', 'weekend']: raise APIError(400, 'Ungültige Wiederholung.')
    return value

class Store:
    def __init__(self, path, clock=time.time):
        self.clock, self.lock = clock, threading.RLock()
        self.db = sqlite3.connect(path, check_same_thread=False)
        self.db.row_factory = sqlite3.Row
        self.db.executescript('''PRAGMA foreign_keys=ON;
        CREATE TABLE IF NOT EXISTS parents(token TEXT PRIMARY KEY, expires REAL NOT NULL);
        CREATE TABLE IF NOT EXISTS devices(id TEXT PRIMARY KEY, token TEXT UNIQUE NOT NULL, name TEXT NOT NULL,
          owner TEXT REFERENCES parents(token) ON DELETE CASCADE, code TEXT UNIQUE, expires REAL,
          config TEXT NOT NULL, version INTEGER NOT NULL DEFAULT 1, applied INTEGER NOT NULL DEFAULT 0,
          completed TEXT NOT NULL DEFAULT '[]', seen REAL, status_day INTEGER);
        ''')
        self.db.commit()

    def parent(self, token):
        key = digest(token)
        if not self.db.execute('SELECT 1 FROM parents WHERE token=? AND expires>?', (key,self.clock())).fetchone(): raise APIError(401, 'Elternzugang abgelaufen oder ungültig. Bitte erneut koppeln.')
        return key

    def device(self, token):
        d = self.db.execute('SELECT * FROM devices WHERE token=?', (digest(token),)).fetchone()
        if not d or (not d['owner'] and d['expires'] < self.clock()): raise APIError(401, 'Verbindung ungültig oder abgelaufen.')
        if d['owner'] and not self.db.execute('SELECT 1 FROM parents WHERE token=? AND expires>?',(d['owner'],self.clock())).fetchone(): raise APIError(401, 'Elternzugang abgelaufen.')
        return d

    def output(self, d):
        return dict(id=d['id'], name=d['name'], paired=bool(d['owner']), config=json.loads(d['config']),
                    version=d['version'], applied=d['applied'], completed=json.loads(d['completed']), seen=d['seen'], day=d['status_day'])

    def dispatch(self, method, path, token, body):
        with self.lock, self.db:
            now = self.clock()
            self.db.execute('DELETE FROM devices WHERE owner IS NULL AND expires<?', (now,))
            self.db.execute('DELETE FROM parents WHERE expires<?', (now,))
            if method == 'POST' and path == '/v1/parents':
                raw = secrets.token_urlsafe(32)
                self.db.execute('INSERT INTO parents VALUES (?,?)', (digest(raw), now+90*86400))
                return {'token':raw}
            if method == 'POST' and path == '/v1/pairings':
                name = body.get('name','')
                if not isinstance(name,str) or not 1 <= len(name.strip()) <= 40: raise APIError(400, 'Bitte einen Gerätenamen mit 1 bis 40 Zeichen angeben.')
                config = validate_config(body.get('config'))
                raw, code, ident = secrets.token_urlsafe(32), ''.join(secrets.choice('ABCDEFGHJKLMNPQRSTUVWXYZ23456789') for _ in range(10)), str(uuid.uuid4())
                self.db.execute('INSERT INTO devices(id,token,name,code,expires,config) VALUES (?,?,?,?,?,?)', (ident,digest(raw),name.strip(),digest(code),now+600,json.dumps(config)))
                return {'token':raw,'code':code,'expires':int(now+600)}
            if method == 'POST' and path == '/v1/claims':
                owner = self.parent(token)
                code = body.get('code','')
                if not isinstance(code,str): raise APIError(400, 'Ungültiger Kopplungscode.')
                code = code.upper().replace(' ','').replace('-','')
                if len(code) != 10: raise APIError(400, 'Der Code hat 10 Zeichen.')
                if self.db.execute('SELECT count(*) FROM devices WHERE owner=?',(owner,)).fetchone()[0] >= 10: raise APIError(400, 'Höchstens zehn Geräte pro Zugang.')
                d = self.db.execute('SELECT * FROM devices WHERE code=? AND owner IS NULL AND expires>?',(digest(code),now)).fetchone()
                if not d: raise APIError(404, 'Code ungültig, bereits benutzt oder abgelaufen.')
                self.db.execute('UPDATE devices SET owner=?,code=NULL,expires=NULL WHERE id=?',(owner,d['id']))
                return {'ok':True}
            if method == 'GET' and path == '/v1/children':
                owner=self.parent(token)
                return {'children':[self.output(d) for d in self.db.execute('SELECT * FROM devices WHERE owner=? ORDER BY name',(owner,))]}
            if path.startswith('/v1/children/'):
                owner=self.parent(token)
                parts=path.split('/')
                ident=parts[3] if len(parts)>3 else ''
                d=self.db.execute('SELECT * FROM devices WHERE id=? AND owner=?',(ident,owner)).fetchone()
                if not d: raise APIError(404, 'Gerät nicht gefunden.')
                if method=='DELETE' and len(parts)==4:
                    self.db.execute('DELETE FROM devices WHERE id=?',(ident,));return {'ok':True}
                if method=='PUT' and len(parts)==5 and parts[4]=='config':
                    config=validate_config(body.get('config'))
                    if type(body.get('version')) is not int or body['version'] != d['version']: raise APIError(409, 'Einstellungen wurden zwischenzeitlich geändert. Bitte neu laden.')
                    self.db.execute('UPDATE devices SET config=?,version=version+1 WHERE id=?',(json.dumps(config),ident))
                    return {'ok':True}
            if path in ['/v1/device','/v1/device/ack']:
                d=self.device(token)
                if method=='GET' and path=='/v1/device': return self.output(d)
                if method=='DELETE' and path=='/v1/device':
                    self.db.execute('DELETE FROM devices WHERE id=?',(d['id'],));return {'ok':True}
                if method=='POST' and path=='/v1/device/ack':
                    if not d['owner']: raise APIError(409,'Noch nicht gekoppelt.')
                    if type(body.get('version')) is not int or body['version'] != d['version']: raise APIError(409,'Bitte aktuelle Einstellungen laden.')
                    done=body.get('completed');day=body.get('day')
                    allowed={t['id'] for t in json.loads(d['config'])['tasks']}
                    if not isinstance(done,list) or any(not isinstance(v,str) or v not in allowed for v in done) or len(done)>12 or type(day) is not int: raise APIError(400,'Ungültiger Status.')
                    self.db.execute('UPDATE devices SET applied=?,completed=?,seen=?,status_day=? WHERE id=?',(d['version'],json.dumps(sorted(set(done))),now,day,d['id']))
                    return {'ok':True}
            if method=='DELETE' and path=='/v1/parents':
                owner=self.parent(token);self.db.execute('DELETE FROM parents WHERE token=?',(owner,));return {'ok':True}
            raise APIError(404, 'Nicht gefunden.')

class Server(ThreadingHTTPServer):
    daemon_threads=True
    def __init__(self,address,store):
        super().__init__(address,Handler);self.store=store;self.rates={};self.rate_lock=threading.Lock()
    def rate(self, ip, route):
        # Never trust unverified X-Forwarded-For. Proxy must also rate-limit clients.
        key=(ip,route);now=time.time();limit=10 if route in ['/v1/parents','/v1/pairings','/v1/claims'] else 120
        with self.rate_lock:
            self.rates={k:v for k,v in self.rates.items() if v[0]>now-60}
            since,count=self.rates.get(key,(now,0))
            if count>=limit: raise APIError(429,'Zu viele Versuche. Bitte eine Minute warten.')
            self.rates[key]=(since,count+1)

class Handler(BaseHTTPRequestHandler):
    server_version='OSMonFamily/0.1'
    def log_message(self,*args): pass  # No codes, tokens or task titles in access logs.
    def handle_api(self):
        status=200
        try:
            if self.path.split('?')[0] != self.path: raise APIError(400,'Keine URL-Parameter erlaubt.')
            self.server.rate(self.client_address[0],self.path)
            size=int(self.headers.get('Content-Length','0'))
            if not 0 <= size <= 16384: raise APIError(413,'Anfrage zu groß.')
            if self.headers.get('Transfer-Encoding'): raise APIError(400,'Nicht unterstützt.')
            body=json.loads(self.rfile.read(size)) if size else {}
            if not isinstance(body,dict): raise APIError(400,'Ungültiges JSON.')
            auth=self.headers.get('Authorization','');token=auth[7:] if auth.startswith('Bearer ') else ''
            result=self.server.store.dispatch(self.command,self.path,token,body)
        except APIError as e: status,result=e.status,{'error':e.message}
        except (ValueError,UnicodeError): status,result=400,{'error':'Ungültige Anfrage.'}
        except Exception: status,result=500,{'error':'Interner Fehler.'}
        raw=json.dumps(result,ensure_ascii=False).encode()
        self.send_response(status);self.send_header('Content-Type','application/json; charset=utf-8');self.send_header('Content-Length',str(len(raw)));self.send_header('Cache-Control','no-store');self.send_header('X-Content-Type-Options','nosniff');self.end_headers();self.wfile.write(raw)
    do_GET=do_POST=do_PUT=do_DELETE=handle_api
    def setup(self): super().setup();self.connection.settimeout(10)

if __name__=='__main__':
    path=os.environ.get('OSMON_FAMILY_DB','family.sqlite3')
    if path != ':memory:':
        fd=os.open(path,os.O_CREAT|os.O_RDWR,0o600);os.close(fd)
    Server((os.environ.get('OSMON_FAMILY_HOST','127.0.0.1'),int(os.environ.get('PORT','8787'))),Store(path)).serve_forever()

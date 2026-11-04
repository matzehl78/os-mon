"""OS-Mon Family API. Standard library only. Bind behind an HTTPS reverse proxy."""
from http.cookies import SimpleCookie
from pathlib import Path
import hashlib
import json
import math
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
        self.db.executescript("""
        CREATE TABLE IF NOT EXISTS web_codes(code TEXT PRIMARY KEY, owner TEXT NOT NULL REFERENCES parents(token) ON DELETE CASCADE, expires REAL NOT NULL);
        CREATE TABLE IF NOT EXISTS web_sessions(token TEXT PRIMARY KEY, owner TEXT NOT NULL REFERENCES parents(token) ON DELETE CASCADE, expires REAL NOT NULL);
        """)
        columns={row[1] for row in self.db.execute('PRAGMA table_info(devices)')}
        if 'location' not in columns: self.db.execute('ALTER TABLE devices ADD COLUMN location TEXT')
        for name, definition in [('location_sharing','INTEGER NOT NULL DEFAULT 0'),('location_request','TEXT')]:
            if name not in columns: self.db.execute(f'ALTER TABLE devices ADD COLUMN {name} {definition}')
        self.db.commit()

    def session(self, owner):
        raw = secrets.token_urlsafe(32)
        self.db.execute('INSERT INTO web_sessions VALUES (?,?,?)',(digest(raw),owner,self.clock()+7*86400))
        return raw

    def parent(self, token):
        key = digest(token)
        if self.db.execute('SELECT 1 FROM parents WHERE token=? AND expires>?',(key,self.clock())).fetchone(): return key
        row=self.db.execute('SELECT s.owner FROM web_sessions s JOIN parents p ON p.token=s.owner WHERE s.token=? AND s.expires>? AND p.expires>?',(key,self.clock(),self.clock())).fetchone()
        if row: return row['owner']
        raise APIError(401, 'Elternzugang abgelaufen oder ungültig. Bitte erneut anmelden.')

    def device(self, token):
        d = self.db.execute('SELECT * FROM devices WHERE token=?', (digest(token),)).fetchone()
        if not d or (not d['owner'] and d['expires'] < self.clock()): raise APIError(401, 'Verbindung ungültig oder abgelaufen.')
        if d['owner'] and not self.db.execute('SELECT 1 FROM parents WHERE token=? AND expires>?',(d['owner'],self.clock())).fetchone(): raise APIError(401, 'Elternzugang abgelaufen.')
        return d

    def output(self, d):
        return dict(id=d['id'], name=d['name'], paired=bool(d['owner']), config=json.loads(d['config']),
                    version=d['version'], applied=d['applied'], completed=json.loads(d['completed']), seen=d['seen'], day=d['status_day'], location=json.loads(d['location']) if d['location'] else None, locationSharing=bool(d['location_sharing']), locationRequest=self.location_request(d))

    def location_request(self, d):
        value=json.loads(d['location_request']) if d['location_request'] else None
        if value and value['state']=='pending' and value['expires']<=self.clock(): value['state']='expired'
        return value

    def dispatch(self, method, path, token, body):
        with self.lock, self.db:
            now = self.clock()
            self.db.execute('DELETE FROM devices WHERE owner IS NULL AND expires<?', (now,))
            self.db.execute('DELETE FROM parents WHERE expires<?', (now,))
            self.db.execute('DELETE FROM web_codes WHERE expires<?',(now,))
            self.db.execute('DELETE FROM web_sessions WHERE expires<?',(now,))
            self.db.execute("UPDATE devices SET location=NULL WHERE location IS NOT NULL AND json_extract(location,'$.received')<?",(now-86400,))
            self.db.commit()  # Expiry cleanup must survive a subsequently rejected request.
            if method == 'POST' and path == '/v1/web-codes':
                owner=self.parent(token)
                self.db.execute('DELETE FROM web_codes WHERE owner=?',(owner,))
                code=''.join(secrets.choice('ABCDEFGHJKLMNPQRSTUVWXYZ23456789') for _ in range(10))
                self.db.execute('INSERT INTO web_codes VALUES (?,?,?)',(digest(code),owner,now+300))
                return {'code':code,'expires':int(now+300)}
            if method == 'POST' and path == '/v1/web-codes/redeem':
                code=body.get('code','')
                if not isinstance(code,str): raise APIError(400,'Ungültiger Code.')
                code=code.upper().replace(' ','').replace('-','')
                row=self.db.execute('SELECT c.owner FROM web_codes c JOIN parents p ON p.token=c.owner WHERE c.code=? AND c.expires>? AND p.expires>?',(digest(code),now,now)).fetchone()
                if not row: raise APIError(404,'Anmeldecode ungültig, abgelaufen oder bereits verwendet.')
                self.db.execute('DELETE FROM web_codes WHERE code=?',(digest(code),))
                return {'token':self.session(row['owner'])}
            if method == 'POST' and path == '/v1/browser-access':
                # Never return the primary parent credential to browser JavaScript.
                owner=digest(secrets.token_urlsafe(32))
                self.db.execute('INSERT INTO parents VALUES (?,?)',(owner,now+90*86400))
                return {'token':self.session(owner)}
            if method == 'DELETE' and path == '/v1/browser-session':
                self.db.execute('DELETE FROM web_sessions WHERE token=?',(digest(token),))
                return {'ok':True}
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
                if method=='POST' and len(parts)==5 and parts[4]=='location-request':
                    if not d['location_sharing']: raise APIError(409,'Standortanfragen sind auf dem Kindergerät nicht freigegeben.')
                    previous=self.location_request(d)
                    if previous and previous['state']=='pending': return {'request':previous}
                    if previous and now-previous['created']<30: raise APIError(429,'Bitte 30 Sekunden zwischen Standortanfragen warten.')
                    value={'id':str(uuid.uuid4()),'created':now,'expires':now+90,'state':'pending'}
                    self.db.execute('UPDATE devices SET location_request=? WHERE id=?',(json.dumps(value),ident))
                    return {'request':value}
                if method=='DELETE' and len(parts)==4:
                    self.db.execute('DELETE FROM devices WHERE id=?',(ident,));return {'ok':True}
                if method=='PUT' and len(parts)==5 and parts[4]=='config':
                    config=validate_config(body.get('config'))
                    if type(body.get('version')) is not int or body['version'] != d['version']: raise APIError(409, 'Einstellungen wurden zwischenzeitlich geändert. Bitte neu laden.')
                    self.db.execute('UPDATE devices SET config=?,version=version+1 WHERE id=?',(json.dumps(config),ident))
                    return {'ok':True}
            if path=='/v1/device/location-sharing' and method=='PUT':
                d=self.device(token)
                if not d['owner']: raise APIError(409,'Zuerst koppeln.')
                if set(body)!={'enabled'} or type(body['enabled']) is not bool: raise APIError(400,'Ungültige Freigabe.')
                if body['enabled']:
                    self.db.execute('UPDATE devices SET location_sharing=1 WHERE id=?',(d['id'],))
                else:
                    self.db.execute('UPDATE devices SET location_sharing=0,location=NULL,location_request=NULL WHERE id=?',(d['id'],))
                return {'ok':True}
            if path=='/v1/device/location-result' and method=='POST':
                d=self.device(token);request=self.location_request(d)
                if not d['location_sharing'] or not request or request['state']!='pending' or request['id']!=body.get('requestId'): raise APIError(409,'Standortanfrage nicht mehr gültig.')
                if set(body)!={'requestId','state'} or body['state'] not in ['unavailable','permissionDenied']: raise APIError(400,'Ungültiger Standortstatus.')
                request['state']=body['state']
                self.db.execute('UPDATE devices SET location_request=? WHERE id=?',(json.dumps(request),d['id']))
                return {'ok':True}
            if path=='/v1/device/location':
                d=self.device(token)
                if not d['owner']: raise APIError(409,'Zuerst mit Elternzugang koppeln.')
                if method=='DELETE':
                    self.db.execute('UPDATE devices SET location=NULL,location_request=NULL WHERE id=?',(d['id'],));return {'ok':True}
                if method=='POST':
                    if set(body) not in [{'latitude','longitude','accuracy','measured'},{'latitude','longitude','accuracy','measured','requestId'}]: raise APIError(400,'Ungültiger Standort.')
                    request=None
                    if 'requestId' in body:
                        request=self.location_request(d)
                        if not d['location_sharing'] or not request or request['state']!='pending' or request['id']!=body['requestId']: raise APIError(409,'Standortanfrage nicht mehr gültig.')
                    for key in ['latitude','longitude','accuracy','measured']:
                        if type(body[key]) not in (int,float) or not math.isfinite(body[key]): raise APIError(400,'Ungültiger Standort.')
                    if not -90<=body['latitude']<=90 or not -180<=body['longitude']<=180 or not 0<=body['accuracy']<=100000 or not now-120<=body['measured']<=now+30:
                        raise APIError(400,'Standort ungültig oder älter als zwei Minuten.')
                    if request and body['measured']<request['created']-5: raise APIError(400,'Messung liegt vor der Anfrage.')
                    value={k:body[k] for k in ['latitude','longitude','accuracy','measured']};value['received']=now
                    if request:
                        request['state']='received';value['requestId']=request['id']
                        self.db.execute('UPDATE devices SET location_request=? WHERE id=?',(json.dumps(request),d['id']))
                    self.db.execute('UPDATE devices SET location=? WHERE id=?',(json.dumps(value),d['id']))
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
    def __init__(self,address,store,web_origin=None):
        super().__init__(address,Handler);self.store=store;self.rates={};self.rate_lock=threading.Lock()
        self.web_origin=(web_origin or os.environ.get("OSMON_WEB_ORIGIN", "")).rstrip("/")
    def rate(self, ip, route):
        # Never trust unverified X-Forwarded-For. Proxy must also rate-limit clients.
        key=(ip,route);now=time.time();limit=10 if route in ['/v1/parents','/v1/browser-access','/v1/pairings','/v1/claims','/v1/web-codes','/v1/web-codes/redeem','/web-api/access','/web-api/login','/web-api/claims'] else 120
        with self.rate_lock:
            self.rates={k:v for k,v in self.rates.items() if v[0]>now-60}
            since,count=self.rates.get(key,(now,0))
            if count>=limit: raise APIError(429,'Zu viele Versuche. Bitte eine Minute warten.')
            self.rates[key]=(since,count+1)

class Handler(BaseHTTPRequestHandler):
    server_version='OSMonFamily/0.1'
    def log_message(self,*args): pass  # No codes, tokens or task titles in access logs.
    def handle_api(self):
        static={'/':'index.html','/app.js':'app.js','/style.css':'style.css','/lumo.svg':'lumo.svg'}
        if self.command=='GET' and self.path in static:
            asset=Path(__file__).parent/'web'/static[self.path]
            if not asset.is_file(): self.send_error(404);return
            raw=asset.read_bytes();types={'.html':'text/html; charset=utf-8','.js':'text/javascript; charset=utf-8','.css':'text/css; charset=utf-8','.svg':'image/svg+xml'}
            self.send_response(200);self.send_header('Content-Type',types[asset.suffix]);self.send_header('Content-Length',str(len(raw)))
            self.send_header('Content-Security-Policy',"default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'")
            self.send_header('X-Content-Type-Options','nosniff');self.send_header('Referrer-Policy','no-referrer');self.end_headers();self.wfile.write(raw);return
        status=200;cookie=None
        try:
            if self.path.split('?')[0] != self.path: raise APIError(400,'Keine URL-Parameter erlaubt.')
            self.server.rate(self.client_address[0],self.path)
            size=int(self.headers.get('Content-Length','0'))
            if not 0 <= size <= 16384: raise APIError(413,'Anfrage zu groß.')
            if self.headers.get('Transfer-Encoding'): raise APIError(400,'Nicht unterstützt.')
            body=json.loads(self.rfile.read(size)) if size else {}
            if not isinstance(body,dict): raise APIError(400,'Ungültiges JSON.')
            auth=self.headers.get('Authorization','');token=auth[7:] if auth.startswith('Bearer ') else ''
            if self.path.startswith('/web-api/'):
                if not self.server.web_origin: raise APIError(503,'Der Familienserver ist noch nicht für den Webzugang eingerichtet.')
                if self.command!='GET' and (self.headers.get('Origin') != self.server.web_origin or self.headers.get('X-OSMon-Web')!='1'):
                    raise APIError(403,'Anfrage von dieser Webseite nicht erlaubt.')
                cookies=SimpleCookie();cookies.load(self.headers.get('Cookie',''))
                token=cookies['__Host-osmon_parent'].value if '__Host-osmon_parent' in cookies else ''
                sub=self.path[len('/web-api/'):]
                if sub=='status' and self.command=='GET':
                    with self.server.store.lock:
                        try: self.server.store.parent(token);authenticated=True
                        except APIError: authenticated=False
                    result={'configured':True,'authenticated':authenticated}
                elif sub in ['access','login'] and self.command=='POST':
                    result=self.server.store.dispatch('POST','/v1/browser-access' if sub=='access' else '/v1/web-codes/redeem','',body)
                    cookie=f"__Host-osmon_parent={result['token']}; Path=/; HttpOnly; Secure; SameSite=Strict; Max-Age=604800"
                    result={'ok':True}
                elif sub=='logout' and self.command=='DELETE':
                    result=self.server.store.dispatch('DELETE','/v1/browser-session',token,{})
                    cookie='__Host-osmon_parent=; Path=/; HttpOnly; Secure; SameSite=Strict; Max-Age=0'
                elif sub in ['children','claims','parents'] or sub.startswith('children/'):
                    result=self.server.store.dispatch(self.command,'/v1/'+sub,token,body)
                    if sub=='parents' and self.command=='DELETE':cookie='__Host-osmon_parent=; Path=/; HttpOnly; Secure; SameSite=Strict; Max-Age=0'
                else: raise APIError(404,'Nicht gefunden.')
            else:
                result=self.server.store.dispatch(self.command,self.path,token,body)
        except APIError as e: status,result=e.status,{'error':e.message}
        except (ValueError,UnicodeError): status,result=400,{'error':'Ungültige Anfrage.'}
        except Exception: status,result=500,{'error':'Interner Fehler.'}
        raw=json.dumps(result,ensure_ascii=False).encode()
        self.send_response(status)
        if cookie:self.send_header('Set-Cookie',cookie)
        self.send_header('Content-Type','application/json; charset=utf-8');self.send_header('Content-Length',str(len(raw)));self.send_header('Cache-Control','no-store');self.send_header('X-Content-Type-Options','nosniff');self.end_headers();self.wfile.write(raw)
    do_GET=do_POST=do_PUT=do_DELETE=handle_api
    def setup(self): super().setup();self.connection.settimeout(10)

if __name__=='__main__':
    path=os.environ.get('OSMON_FAMILY_DB','family.sqlite3')
    if path != ':memory:':
        fd=os.open(path,os.O_CREAT|os.O_RDWR,0o600);os.close(fd)
    Server((os.environ.get('OSMON_FAMILY_HOST','127.0.0.1'),int(os.environ.get('PORT','8787'))),Store(path)).serve_forever()

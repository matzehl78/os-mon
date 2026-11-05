<?php
declare(strict_types=1);
if(PHP_SAPI!=='cli'){http_response_code(404);exit;}
require_once __DIR__.'/bootstrap.php';require_once __DIR__.'/AdminStore.php';
try {
    $c=familyConfig();$key=adminKey($c);
    echo "Adminname (3–40 Zeichen, a-z/0-9/._-): ";$username=trim((string)fgets(STDIN));
    $file=getenv('OSMON_ADMIN_PASSWORD_FILE');
    if($file){
        if(!is_file($file)||((fileperms($file)&0077)!==0))throw new RuntimeException('Passwortdatei muss nur für den Besitzer lesbar sein (chmod 600).');
        $password=rtrim((string)file_get_contents($file),"\r\n");
    }else{
        if(!stream_isatty(STDIN)||!function_exists('system'))throw new RuntimeException('Interaktives Terminal erforderlich. Alternativ private Passwortdatei über OSMON_ADMIN_PASSWORD_FILE angeben.');
        echo "Neues Adminpasswort (12–72 Bytes, Eingabe unsichtbar): ";system('stty -echo',$rc);
        if($rc!==0)throw new RuntimeException('Verdeckte Passworteingabe nicht verfügbar. Private Passwortdatei verwenden.');
        try{$password=rtrim((string)fgets(STDIN),"\r\n");echo "\nWiederholen: ";$repeat=rtrim((string)fgets(STDIN),"\r\n");}
        finally{system('stty echo');echo "\n";}
        if(!hash_equals($password,$repeat))throw new RuntimeException('Passwörter stimmen nicht überein.');
    }
    if(!preg_match('/^[a-z0-9._-]{3,40}$/D',$username)||strlen($password)<12||strlen($password)>72)throw new RuntimeException('Adminname oder Passwort ungültig.');
    $secret=random_bytes(20);$base32=base32Encode($secret);
    echo "\nNur lokal in deine Authenticator-App übernehmen. Nicht in Chats oder QR-Webdienste kopieren.\n";
    echo 'Geheimer Einrichtungsschlüssel: '.$base32."\n";
    echo 'otpauth://totp/'.rawurlencode('OS-Mon Admin:'.$username).'?secret='.$base32.'&issuer=OS-Mon&algorithm=SHA1&digits=6&period=30'."\n";
    echo "Aktueller sechsstelliger Authenticator-Code zur Bestätigung: ";$code=trim((string)fgets(STDIN));
    (new AdminStore(connectFamily($c),$key))->provision($username,$password,$secret,$code);
    echo "Admin eingerichtet. Bestehender gleichnamiger Zugang wurde zurückgesetzt, alte Sitzungen wurden entfernt.\nFür die Anmeldung den nächsten neuen Authenticator-Code abwarten.\n";
}catch(Throwable $e){fwrite(STDERR,"Einrichtung fehlgeschlagen: ".$e->getMessage()."\n");exit(1);}

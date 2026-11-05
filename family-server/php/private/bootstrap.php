<?php
declare(strict_types=1);
require_once __DIR__.'/FamilyStore.php';
function familyConfig(): array {
    $path=getenv('OSMON_CONFIG_FILE') ?: __DIR__.'/config.php';
    if (!is_file($path)) fail(503,'Serverkonfiguration fehlt.');
    $c=require $path;
    if (!is_array($c)) fail(503,'Serverkonfiguration ungültig.');
    foreach (['origin','host','port','database','username','password','rate_secret'] as $k) if (!isset($c[$k]) || $c[$k]==='') fail(503,'Serverkonfiguration unvollständig.');
    $u=parse_url($c['origin']);
    if (!$u || ($u['scheme']??'')!=='https' || empty($u['host']) || (isset($u['user']) || isset($u['pass'])) || isset($u['query']) || isset($u['fragment']) || !empty($u['path']) || !preg_match('/^[a-f0-9]{64}$/D',$c['rate_secret'])) fail(503,'HTTPS-Origin oder Rate-Schlüssel ungültig.');
    if (str_starts_with($c['database'],'YOUR_') || str_starts_with($c['password'],'REPLACE_')) fail(503,'Datenbankzugang noch nicht eingerichtet.');
    if (PHP_SAPI!=='cli') {
        $doc=realpath($_SERVER['DOCUMENT_ROOT']??'');$real=realpath($path);
        if (!$doc || !$real || str_starts_with($real,$doc.DIRECTORY_SEPARATOR)) fail(503,'Konfiguration muss außerhalb des öffentlichen Verzeichnisses liegen.');
    }
    return $c;
}

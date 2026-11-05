<?php
declare(strict_types=1);
if (PHP_SAPI!=='cli') { http_response_code(404);exit; }
require_once __DIR__.'/bootstrap.php';
try {
    $db=connectFamily(familyConfig());
    $sql=file_get_contents(__DIR__.'/schema.sql');
    foreach (explode(';',$sql) as $statement) if (trim($statement)!=='') $db->exec($statement);
    echo "OS-Mon-Schema eingerichtet. Keine vorhandenen Tabellen gelöscht.\n";
} catch (Throwable $e) { fwrite(STDERR,"Einrichtung fehlgeschlagen. PHP-Erweiterungen, Konfiguration und Datenbankrechte lokal prüfen.\n");exit(1); }

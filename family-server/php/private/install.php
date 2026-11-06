<?php
declare(strict_types=1);
if (PHP_SAPI!=='cli') { http_response_code(404);exit; }
require_once __DIR__.'/bootstrap.php';
try {
    $db=connectFamily(familyConfig());
    $sql=file_get_contents(__DIR__.'/schema.sql');
    foreach (explode(';',$sql) as $statement) if (trim($statement)!=='') $db->exec($statement);
    $columns=$db->query('SHOW COLUMNS FROM osmon_parents')->fetchAll(PDO::FETCH_COLUMN);
    if (!in_array('public_id',$columns,true)) $db->exec('ALTER TABLE osmon_parents ADD public_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL UNIQUE');
    if (!in_array('suspended',$columns,true)) $db->exec('ALTER TABLE osmon_parents ADD suspended TINYINT NOT NULL DEFAULT 0');
    echo "OS-Mon-Schema eingerichtet. Keine vorhandenen Tabellen gelöscht.\n";
} catch (Throwable $e) { fwrite(STDERR,"Einrichtung fehlgeschlagen. PHP-Erweiterungen, Konfiguration und Datenbankrechte lokal prüfen.\n");exit(1); }

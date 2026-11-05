<?php
// CI-only router. Never included in installation ZIP or public document root.
if (getenv('OSMON_HTTP_TEST')!=='1') { http_response_code(404);exit; }
if (!isset($_SERVER['HTTP_X_TEST_HTTP_ONLY'])) $_SERVER['HTTPS']='on';
require dirname(__DIR__).'/public/index.php';

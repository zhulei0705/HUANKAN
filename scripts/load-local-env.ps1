$names = @('HUANKAN_DB_URL','HUANKAN_DB_USER','HUANKAN_DB_PASSWORD','HUANKAN_ADMIN_TOKEN','HUANKAN_TEST_DB_URL','HUANKAN_TEST_DB_USER','HUANKAN_TEST_DB_PASSWORD','HUANKAN_LOCAL_ADMIN_PASSWORD','HUANKAN_COLLECTOR_ENABLED','SEC_CONTACT_EMAIL','SEC_USER_AGENT','SEC_FOLLOWED_CIKS','FRED_API_KEY','FRED_SERIES_WHITELIST','FRED_ENABLED','AI_ENABLED','AI_BASE_URL','AI_MODEL','AI_API_KEY','AI_MAX_REQUESTS_PER_DAY','AI_MAX_OUTPUT_TOKENS')
foreach ($name in $names) {
    $value = [Environment]::GetEnvironmentVariable($name, 'User')
    if ($value) { [Environment]::SetEnvironmentVariable($name, $value, 'Process') }
}
$secEnabled=[Environment]::GetEnvironmentVariable('SEC_ENABLED','User')
if($secEnabled){$env:SEC_ENABLED=$secEnabled}

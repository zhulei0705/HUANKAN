ALTER TABLE gdelt_batch ADD CONSTRAINT gdelt_batch_size_limit CHECK(expected_bytes>0 AND expected_bytes<=10485760);
ALTER TABLE gdelt_batch ADD CONSTRAINT gdelt_zip_integrity CHECK(raw_zip IS NULL OR (octet_length(raw_zip)=expected_bytes AND encode(sha256(raw_zip),'hex')=sha256 AND md5(raw_zip)=expected_md5));
CREATE INDEX aggregate_country_lookup ON aggregated_dependency(countries);

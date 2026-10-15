INSERT INTO source_registry(id,name,level,endpoint,enabled,interval_seconds,region) VALUES('ECB','欧洲央行','L0','https://www.ecb.europa.eu/rss/press.html',false,300,'欧元区');
INSERT INTO source_license(source_id,status,terms_url) VALUES('ECB','PENDING','https://www.ecb.europa.eu/services/using-our-site/disclaimer/html/index.en.html');
CREATE FUNCTION enforce_ecb_binding() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.source_id='ECB' AND NEW.source_url !~ '^https://www[.]ecb[.]europa[.]eu/' THEN RAISE EXCEPTION 'Official source URL mismatch'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER evidence_ecb_binding BEFORE INSERT ON raw_evidence FOR EACH ROW EXECUTE FUNCTION enforce_ecb_binding();

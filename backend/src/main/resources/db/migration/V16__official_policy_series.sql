CREATE OR REPLACE FUNCTION enforce_macro_source() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF NEW.source_id='NBS' AND NEW.source_url !~ '^https://www[.]stats[.]gov[.]cn/' THEN RAISE EXCEPTION 'NBS official source mismatch'; END IF;
 IF NEW.source_id='ECB_DATA' AND NEW.source_url !~ '^https://data-api[.]ecb[.]europa[.]eu/service/data/(EXR/D[.](USD|JPY|GBP|CNY)[.]EUR[.]SP00[.]A|FM/B[.]U2[.]EUR[.]4F[.]KR[.](DFR|MRR_FR|MLFR)[.]LEV)([?].*)?$' THEN RAISE EXCEPTION 'ECB official series source mismatch'; END IF;
 RETURN NEW; END $$;
-- Correct the derived entity taxonomy. Raw evidence and event references remain intact.
UPDATE financial_entity SET type='ORGANIZATION' WHERE source_id='NBS' AND type='REGULATOR';

-- Share the collector's transaction lock before allocating any journal cursor.
-- A later cursor must never commit before an earlier notification transaction.
CREATE OR REPLACE FUNCTION dispatch_notification_review() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF NEW.message_class IN('VERIFIED_FACT','IMPORTANT_ALERT') THEN
 PERFORM pg_advisory_xact_lock(742019);
 INSERT INTO notification_dispatch(review_id,outbox_id,fact_id,message_class)
 VALUES(NEW.id,NEW.outbox_id,NEW.fact_id,NEW.message_class) ON CONFLICT DO NOTHING;
 END IF;RETURN NEW;END $$;

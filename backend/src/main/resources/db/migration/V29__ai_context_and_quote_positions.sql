ALTER TABLE ai_analysis ADD COLUMN generated_by text NOT NULL DEFAULT 'AI' CHECK(generated_by='AI');
ALTER TABLE ai_analysis ADD COLUMN input_excerpt text;
ALTER TABLE ai_analysis ADD COLUMN excerpt_start_offset integer CHECK(excerpt_start_offset>=0);
CREATE FUNCTION completed_ai_immutable() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF OLD.status IN('COMPLETED','FAILED') THEN RAISE EXCEPTION 'Completed AI analyses are immutable; create a new analysis';END IF;RETURN NEW;END $$;
CREATE TRIGGER completed_ai_history BEFORE UPDATE OR DELETE ON ai_analysis FOR EACH ROW EXECUTE FUNCTION completed_ai_immutable();

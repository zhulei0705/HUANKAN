CREATE TABLE financial_topic_rule(id text PRIMARY KEY,display_name_zh text NOT NULL,terms jsonb NOT NULL CHECK(jsonb_typeof(terms)='array'),weight integer NOT NULL CHECK(weight BETWEEN 0 AND 40),version integer NOT NULL DEFAULT 1,enabled boolean NOT NULL DEFAULT true);
INSERT INTO financial_topic_rule VALUES
 ('RATE','央行利率政策','["interest rate","policy rate","利率","货币政策"]',30,1,true),
 ('MONEY','货币供应','["money supply","货币供应","社会融资"]',25,1,true),
 ('INFLATION','通货膨胀','["inflation","cpi","ppi","通胀","居民消费价格"]',25,1,true),
 ('EMPLOYMENT','就业','["employment","unemployment","就业","失业"]',25,1,true),
 ('GDP','国内生产总值','["gdp","gross domestic product","国内生产总值"]',25,1,true),
 ('INDUSTRY','工业生产','["industrial production","工业增加值"]',20,1,true),
 ('TRADE','贸易','["trade","tariff","贸易","关税"]',20,1,true),
 ('REGULATION','金融监管','["regulation","regulatory","监管"]',20,1,true),
 ('EARNINGS','企业财报','["earnings","annual report","财报","业绩"]',20,1,true),
 ('FINANCING','企业融资','["financing","融资"]',20,1,true),
 ('MA','并购','["acquisition","merger","并购","收购"]',20,1,true),
 ('CAPEX','大型资本开支','["capital expenditure","capex","资本开支"]',20,1,true),
 ('AI','人工智能技术','["artificial intelligence","人工智能"]',15,1,true),
 ('SEMI','半导体','["semiconductor","半导体","芯片"]',20,1,true),
 ('ENERGY','能源','["energy","oil","gas","能源"]',20,1,true),
 ('METALS','贵金属','["gold","silver","贵金属","黄金"]',20,1,true),
 ('GEO','地缘经济','["geopolitical","sanction","地缘","制裁"]',20,1,true),
 ('OTHER','其他重要产业变化','["supply chain","供应链"]',10,1,true);
CREATE TABLE governed_message(event_id uuid PRIMARY KEY REFERENCES financial_event(id),evidence_id uuid NOT NULL REFERENCES raw_evidence(id),topics jsonb NOT NULL,priority integer NOT NULL CHECK(priority BETWEEN 0 AND 100),basis jsonb NOT NULL,rule_version text NOT NULL,generated_by text NOT NULL DEFAULT 'RULE',processed_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE research_case(id uuid PRIMARY KEY,title text NOT NULL,cluster_key text NOT NULL UNIQUE,cluster_method text NOT NULL,verification text NOT NULL DEFAULT 'PENDING' CHECK(verification IN('PENDING','VERIFIED','CONFLICT','REJECTED')),created_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE research_case_message(case_id uuid REFERENCES research_case(id),event_id uuid REFERENCES financial_event(id),dependency text NOT NULL,PRIMARY KEY(case_id,event_id));
CREATE TABLE research_case_fact(id uuid PRIMARY KEY,case_id uuid REFERENCES research_case(id),claim text NOT NULL,evidence_id uuid NOT NULL REFERENCES raw_evidence(id),excerpt text NOT NULL,start_offset integer NOT NULL CHECK(start_offset>=0),end_offset integer NOT NULL CHECK(end_offset>start_offset),status text NOT NULL DEFAULT 'PENDING',created_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE research_hypothesis(id uuid PRIMARY KEY,case_id uuid REFERENCES research_case(id),mechanism text NOT NULL,direction text NOT NULL CHECK(direction IN('POSITIVE','NEGATIVE','UNCERTAIN')),generated_by text NOT NULL CHECK(generated_by IN('MANUAL','AI')),evidence_ids uuid[] NOT NULL,uncertainty text NOT NULL,created_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE governance_rule_audit(id bigserial PRIMARY KEY,rule_id text NOT NULL REFERENCES financial_topic_rule(id),old_value jsonb NOT NULL,new_value jsonb NOT NULL,reason text NOT NULL,created_at timestamptz NOT NULL DEFAULT clock_timestamp());
CREATE INDEX research_case_message_event ON research_case_message(event_id);

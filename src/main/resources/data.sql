-- Canonical SmartTriage question bank.
--
-- Runs on every startup (spring.sql.init.mode=always), after Hibernate has created
-- the schema (spring.jpa.defer-datasource-initialization=true). It must therefore
-- be idempotent: existing questions are never overwritten, so wording or flags
-- changed in the database survive restarts.
--
-- Questions are asked in id order, so the insert order below is the asking order
-- on a fresh database.

insert into triage_questions (question_key, question_text, answer_type, active, depends_on_question_key, depends_on_answer)
values ('CHEST_PAIN', 'Are you experiencing chest pain?', 'BOOLEAN', true, null, null)
on conflict (question_key) do nothing;

insert into triage_questions (question_key, question_text, answer_type, active, depends_on_question_key, depends_on_answer)
values ('FEVER', 'Do you have a fever?', 'BOOLEAN', true, null, null)
on conflict (question_key) do nothing;

insert into triage_questions (question_key, question_text, answer_type, active, depends_on_question_key, depends_on_answer)
values ('DURATION', 'How long have you had these symptoms?', 'TEXT', true, null, null)
on conflict (question_key) do nothing;

insert into triage_questions (question_key, question_text, answer_type, active, depends_on_question_key, depends_on_answer)
values ('BREATHING_DIFFICULTY', 'Are you having difficulty breathing?', 'BOOLEAN', true, 'CHEST_PAIN', 'YES')
on conflict (question_key) do nothing;

-- The rule engine reads temperatures as Celsius, so the question must say so.
insert into triage_questions (question_key, question_text, answer_type, active, depends_on_question_key, depends_on_answer)
values ('TEMPERATURE', 'What is your temperature (°C)?', 'NUMBER', true, 'FEVER', 'YES')
on conflict (question_key) do nothing;

-- One-off wording fix for databases seeded before the unit was stated. Matches the
-- exact old text only, so a later deliberate edit is never overwritten.
update triage_questions
set question_text = 'What is your temperature (°C)?'
where question_key = 'TEMPERATURE'
  and question_text = 'What is your temperature?';

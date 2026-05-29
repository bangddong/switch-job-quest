ALTER TABLE coding_problem ADD COLUMN category VARCHAR(50);
CREATE INDEX idx_coding_problem_category_lang ON coding_problem(category, language);

ALTER TABLE coding_submission ADD COLUMN category VARCHAR(50);

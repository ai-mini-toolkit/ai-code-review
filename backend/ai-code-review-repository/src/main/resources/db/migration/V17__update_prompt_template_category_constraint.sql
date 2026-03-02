-- V17: Add 'code-review' to prompt_template category check constraint
-- ReviewOrchestrator.CODE_REVIEW_CATEGORY uses 'code-review' but V4 constraint did not include it.

ALTER TABLE prompt_template DROP CONSTRAINT chk_prompt_template_category;

ALTER TABLE prompt_template
    ADD CONSTRAINT chk_prompt_template_category
        CHECK (category IN (
            'security',
            'performance',
            'maintainability',
            'correctness',
            'style',
            'best_practices',
            'code-review'
        ));

COMMENT ON COLUMN prompt_template.category IS
    'Review dimension: security, performance, maintainability, correctness, style, best_practices, code-review';

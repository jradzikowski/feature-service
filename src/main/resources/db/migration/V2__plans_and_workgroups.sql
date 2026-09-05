-- V2: plans and workgroups

CREATE TABLE workgroups (
    id          UUID         NOT NULL,
    name        VARCHAR(255) NOT NULL,
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL,
    PRIMARY KEY (id)
);
COMMENT ON TABLE workgroups IS 'Named registry of workgroup UUIDs; id equals the external workgroup_id used in flag_overrides';

CREATE TABLE plans (
    id             UUID         NOT NULL,
    application_id UUID         NOT NULL,
    name           VARCHAR(255) NOT NULL,
    description    TEXT,
    created_at     TIMESTAMP    NOT NULL,
    updated_at     TIMESTAMP    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_plans_application FOREIGN KEY (application_id) REFERENCES applications (id),
    CONSTRAINT uq_plans_app_name    UNIQUE (application_id, name)
);
COMMENT ON TABLE plans IS 'Named tier/plan grouping flag values for an application';

CREATE TABLE plan_flags (
    id      UUID NOT NULL,
    plan_id UUID NOT NULL,
    flag_id UUID NOT NULL,
    value   TEXT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_plan_flags_plan FOREIGN KEY (plan_id) REFERENCES plans (id) ON DELETE CASCADE,
    CONSTRAINT fk_plan_flags_flag FOREIGN KEY (flag_id) REFERENCES flags (id) ON DELETE CASCADE,
    CONSTRAINT uq_plan_flags       UNIQUE (plan_id, flag_id)
);
COMMENT ON COLUMN plan_flags.value IS 'JSON value matching the flag value_type';

CREATE INDEX idx_plan_flags_plan ON plan_flags (plan_id);

CREATE TABLE workgroup_plans (
    id             UUID      NOT NULL,
    workgroup_id   UUID      NOT NULL,
    plan_id        UUID      NOT NULL,
    application_id UUID      NOT NULL,
    assigned_at    TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_wg_plans_workgroup FOREIGN KEY (workgroup_id)   REFERENCES workgroups    (id),
    CONSTRAINT fk_wg_plans_plan      FOREIGN KEY (plan_id)        REFERENCES plans         (id),
    CONSTRAINT fk_wg_plans_app       FOREIGN KEY (application_id) REFERENCES applications  (id),
    CONSTRAINT uq_wg_plans           UNIQUE (workgroup_id, application_id)
);
COMMENT ON TABLE workgroup_plans IS 'One plan per workgroup per application; changing the plan row replaces the assignment';

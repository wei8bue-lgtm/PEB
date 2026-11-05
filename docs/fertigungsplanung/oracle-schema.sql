CREATE TABLE planning_project (
    id VARCHAR2(36 CHAR) NOT NULL,
    version NUMBER(19),
    name VARCHAR2(255 CHAR) NOT NULL,
    current_revision NUMBER(10) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    created_by VARCHAR2(100 CHAR) NOT NULL,
    modified_at TIMESTAMP(6) NOT NULL,
    modified_by VARCHAR2(100 CHAR) NOT NULL,
    CONSTRAINT pk_planning_project PRIMARY KEY (id)
);

CREATE TABLE planning_revision (
    id VARCHAR2(36 CHAR) NOT NULL,
    project_id VARCHAR2(36 CHAR) NOT NULL,
    revision_number NUMBER(10) NOT NULL,
    status VARCHAR2(20 CHAR) NOT NULL,
    snapshot CLOB NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    created_by VARCHAR2(100 CHAR) NOT NULL,
    released_at TIMESTAMP(6),
    released_by VARCHAR2(100 CHAR),
    CONSTRAINT pk_planning_revision PRIMARY KEY (id),
    CONSTRAINT fk_planning_rev_project FOREIGN KEY (project_id) REFERENCES planning_project(id),
    CONSTRAINT uq_planning_rev_number UNIQUE (project_id, revision_number),
    CONSTRAINT ck_planning_rev_status CHECK (status IN ('WORKING', 'RELEASED', 'OBSOLETE'))
);
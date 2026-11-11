-- Schema is owned by Flyway, never by VectorStore.initializeSchema().
-- This version deliberately binds one table to one embedding model/dimension/metric.
CREATE TABLE ai_vector_schema (
    id TINYINT UNSIGNED NOT NULL PRIMARY KEY,
    embedding_model VARCHAR(128) NOT NULL,
    dimensions SMALLINT UNSIGNED NOT NULL,
    distance_type VARCHAR(16) NOT NULL,
    CONSTRAINT ck_ai_vector_schema_singleton CHECK (id = 1),
    CONSTRAINT ck_ai_vector_dimensions CHECK (dimensions = 768),
    CONSTRAINT ck_ai_vector_distance CHECK (distance_type = 'COSINE')
) ENGINE=InnoDB;

INSERT INTO ai_vector_schema (id, embedding_model, dimensions, distance_type)
VALUES (1, 'gemini-embedding-001', 768, 'COSINE');

CREATE TABLE ai_document (
    id UUID NOT NULL PRIMARY KEY,
    content TEXT NOT NULL,
    metadata JSON NOT NULL,
    embedding VECTOR(768) NOT NULL,
    VECTOR INDEX ai_document_embedding_idx (embedding) DISTANCE=cosine,
    CONSTRAINT ck_ai_document_model CHECK (
        JSON_VALUE(metadata, '$.embedding_model') IS NOT NULL
        AND JSON_VALUE(metadata, '$.embedding_model') = 'gemini-embedding-001'
    ),
    CONSTRAINT ck_ai_document_scope CHECK (
        JSON_VALUE(metadata, '$.scope') IS NOT NULL
        AND JSON_VALUE(metadata, '$.scope') IN ('public', 'private')
    ),
    CONSTRAINT ck_ai_document_owner CHECK (
        (JSON_VALUE(metadata, '$.scope') = 'public'
            AND JSON_CONTAINS_PATH(metadata, 'one', '$.owner_id') = 0)
        OR (JSON_VALUE(metadata, '$.scope') = 'private'
            AND JSON_VALUE(metadata, '$.owner_id') IS NOT NULL
            AND CAST(JSON_VALUE(metadata, '$.owner_id') AS SIGNED) > 0)
    )
) ENGINE=InnoDB;

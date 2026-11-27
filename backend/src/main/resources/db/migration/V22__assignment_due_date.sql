-- =============================================================================
-- English Lab - V22: fecha de entrega opcional en asignaciones de tareas
-- =============================================================================

alter table assignments
    add column due_at timestamptz;

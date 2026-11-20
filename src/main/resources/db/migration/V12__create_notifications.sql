CREATE TABLE notifications (
    id UUID PRIMARY KEY ,
    company_id UUID NOT NULL REFERENCES companies (id) ON DELETE CASCADE,
    recipient_user_id UUID NOT NULL REFERENCES users (id) ON DELETE SET NULL ,
    title VARCHAR(250) NOT NULL ,
    message TEXT NOT NULL ,
    type VARCHAR(250) NOT NULL ,
    reference_id UUID,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
)
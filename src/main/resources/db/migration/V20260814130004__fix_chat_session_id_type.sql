ALTER TABLE support_tickets ALTER COLUMN chat_session_id TYPE UUID USING chat_session_id::UUID;

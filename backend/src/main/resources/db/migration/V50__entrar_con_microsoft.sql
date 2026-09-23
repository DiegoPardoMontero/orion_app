-- Entrar con Microsoft (Outlook, Hotmail, Live y cuentas de trabajo o universidad), 23/09/2026: en
-- lugar de Facebook, que Pardo descartó por lo difícil que es de configurar. El CHECK del proveedor
-- solo admitía los tres de la V45.
ALTER TABLE social_identities DROP CONSTRAINT social_identities_provider_check;
ALTER TABLE social_identities ADD CONSTRAINT social_identities_provider_check
    CHECK (provider IN ('GOOGLE', 'FACEBOOK', 'APPLE', 'MICROSOFT'));

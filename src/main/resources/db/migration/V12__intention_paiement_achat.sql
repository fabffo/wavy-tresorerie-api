-- Une proposition engagée ne peut plus être annulée tant que son paiement est à réconcilier.
ALTER TABLE rapprochement_bancaire ADD COLUMN paiement_engage BOOLEAN NOT NULL DEFAULT FALSE;

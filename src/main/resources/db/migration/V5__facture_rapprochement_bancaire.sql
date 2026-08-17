alter table operation_bancaire
    add column if not exists libelle_rapprochement varchar(180),
    add column if not exists date_rapprochement timestamp,
    add column if not exists facture_rapprochement_id bigint;

alter table rapprochement_bancaire
    add column if not exists mode_rapprochement varchar(60),
    add column if not exists libelle_rapprochement varchar(180);

create unique index if not exists uk_operation_bancaire_facture_rapprochement
    on operation_bancaire (tenant_id, societe_interne_id, facture_rapprochement_id)
    where facture_rapprochement_id is not null;

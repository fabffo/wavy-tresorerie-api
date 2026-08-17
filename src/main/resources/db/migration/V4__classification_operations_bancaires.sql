alter table operation_bancaire
    add column if not exists tiers_reconnu varchar(255),
    add column if not exists type_tiers_reconnu varchar(40),
    add column if not exists nature_operation varchar(40) not null default 'AUTRE',
    add column if not exists statut_classification varchar(40) not null default 'A_VERIFIER';

update operation_bancaire
set nature_operation = 'AUTRE'
where nature_operation is null;

update operation_bancaire
set statut_classification = 'A_VERIFIER'
where statut_classification is null;

create index if not exists idx_operation_bancaire_classification
    on operation_bancaire (tenant_id, societe_interne_id, actif, nature_operation, statut_classification);

alter table rapprochement_bancaire
    add column if not exists salaire_id bigint,
    add column if not exists periode_salaire varchar(7),
    add column if not exists matricule_salarie varchar(80);

alter table operation_bancaire
    add column if not exists salaire_id bigint;

alter table rapprochement_bancaire drop constraint if exists ck_rapprochement_document_unique;
alter table rapprochement_bancaire add constraint ck_rapprochement_document_unique check (
    (case when facture_id is null then 0 else 1 end)
  + (case when piece_rapprochement_id is null then 0 else 1 end)
  + (case when salaire_id is null then 0 else 1 end) <= 1
);

create index if not exists idx_rapprochement_bancaire_salaire
    on rapprochement_bancaire (tenant_id, societe_interne_id, salaire_id)
    where salaire_id is not null;

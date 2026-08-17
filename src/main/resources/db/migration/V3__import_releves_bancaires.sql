alter table import_releve_bancaire
    rename column format_import to type_fichier;

alter table import_releve_bancaire
    rename column nombre_operations to nombre_lignes_importees;

alter table import_releve_bancaire
    rename column montant_total to total_credit;

alter table import_releve_bancaire
    add column if not exists utilisateur_import_id bigint,
    add column if not exists nombre_lignes integer not null default 0,
    add column if not exists nombre_lignes_rejetees integer not null default 0,
    add column if not exists total_debit numeric(19,2) not null default 0;

update import_releve_bancaire
set nombre_lignes = greatest(nombre_lignes, nombre_lignes_importees)
where nombre_lignes = 0;

update import_releve_bancaire set statut_import = 'IMPORTE_AVEC_ERREURS' where statut_import = 'PARTIEL';
update import_releve_bancaire set statut_import = 'ECHEC' where statut_import = 'ERREUR';

alter table operation_bancaire
    rename column reference to reference_bancaire;

alter table operation_bancaire
    rename column statut to statut_rapprochement;

alter table operation_bancaire
    add column if not exists debit numeric(19,2) not null default 0,
    add column if not exists credit numeric(19,2) not null default 0,
    add column if not exists type_operation varchar(40) not null default 'AUTRE',
    add column if not exists rapprochee boolean not null default false,
    add column if not exists numero_ligne_import integer,
    add column if not exists hash_operation varchar(64),
    add column if not exists date_import timestamp,
    add column if not exists actif boolean not null default true;

update operation_bancaire
set statut_rapprochement = 'NON_RAPPROCHEE'
where statut_rapprochement = 'A_RAPPROCHER';

update operation_bancaire
set debit = abs(montant)
where sens = 'DEBIT' and debit = 0;

update operation_bancaire
set credit = montant
where sens = 'CREDIT' and credit = 0;

update operation_bancaire
set rapprochee = true
where statut_rapprochement = 'RAPPROCHEE';

update operation_bancaire
set hash_operation = md5(
    tenant_id || '|' || societe_interne_id || '|' || compte_bancaire_id || '|' ||
    date_operation || '|' || coalesce(libelle, '') || '|' || montant || '|' ||
    coalesce(reference_bancaire, '')
)
where hash_operation is null;

alter table operation_bancaire
    alter column hash_operation set not null;

create unique index if not exists uk_operation_bancaire_hash
    on operation_bancaire (tenant_id, societe_interne_id, compte_bancaire_id, hash_operation);

drop index if exists idx_operation_bancaire_contexte;
create index idx_operation_bancaire_contexte
    on operation_bancaire (tenant_id, societe_interne_id, actif, date_operation desc, id desc);

create index if not exists idx_import_releve_compte
    on import_releve_bancaire (tenant_id, societe_interne_id, compte_bancaire_id, date_import desc);

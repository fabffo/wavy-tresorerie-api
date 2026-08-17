create table if not exists piece_rapprochement (
    id bigserial primary key, tenant_id bigint not null, societe_interne_id bigint not null,
    operation_bancaire_id bigint not null references operation_bancaire(id), tiers_id bigint not null,
    type_piece varchar(40) not null, numero_piece varchar(40) not null, date_piece date not null,
    libelle varchar(500) not null, montant_ht numeric(19,2) not null default 0,
    montant_tva numeric(19,2) not null default 0, montant_ttc numeric(19,2) not null default 0,
    famille_activite varchar(120), categorie_budgetaire varchar(120), statut varchar(30) not null,
    origine varchar(30) not null, libelle_rapprochement varchar(180) not null,
    constraint uk_piece_rapprochement_operation unique (operation_bancaire_id),
    constraint uk_piece_rapprochement_numero unique (tenant_id, societe_interne_id, numero_piece)
);

create table if not exists compteur_piece_rapprochement (
    tenant_id bigint not null, societe_interne_id bigint not null, annee integer not null,
    dernier_numero bigint not null, primary key (tenant_id, societe_interne_id, annee)
);

alter table operation_bancaire add column if not exists piece_rapprochement_id bigint;
alter table rapprochement_bancaire add column if not exists piece_rapprochement_id bigint;

create table compte_bancaire (
    id bigserial primary key,
    tenant_id bigint not null,
    societe_interne_id bigint not null,
    libelle varchar(150) not null,
    banque varchar(150),
    iban varchar(34),
    bic varchar(11),
    devise varchar(3) not null default 'EUR',
    solde_initial numeric(19,2) not null default 0,
    solde_courant numeric(19,2) not null default 0,
    actif boolean not null default true,
    date_creation timestamp not null default now(),
    date_modification timestamp not null default now()
);

create table import_releve_bancaire (
    id bigserial primary key,
    tenant_id bigint not null,
    societe_interne_id bigint not null,
    compte_bancaire_id bigint not null references compte_bancaire(id),
    nom_fichier varchar(255) not null,
    format_import varchar(40) not null,
    statut_import varchar(40) not null,
    nombre_operations integer not null default 0,
    montant_total numeric(19,2) not null default 0,
    date_import timestamp not null default now(),
    message_erreur text
);

create table operation_bancaire (
    id bigserial primary key,
    tenant_id bigint not null,
    societe_interne_id bigint not null,
    compte_bancaire_id bigint not null references compte_bancaire(id),
    import_releve_bancaire_id bigint references import_releve_bancaire(id),
    date_operation date not null,
    date_valeur date,
    libelle varchar(500) not null,
    reference varchar(150),
    montant numeric(19,2) not null,
    sens varchar(20) not null,
    statut varchar(40) not null,
    tiers_id bigint,
    facture_id bigint,
    type_facture varchar(20),
    date_creation timestamp not null default now(),
    date_modification timestamp not null default now()
);

create table rapprochement_bancaire (
    id bigserial primary key,
    tenant_id bigint not null,
    societe_interne_id bigint not null,
    operation_bancaire_id bigint not null references operation_bancaire(id),
    facture_id bigint,
    type_facture varchar(20),
    montant_rapproche numeric(19,2) not null,
    ecart numeric(19,2) not null default 0,
    statut varchar(40) not null,
    commentaire text,
    date_rapprochement timestamp not null default now(),
    utilisateur_id bigint
);

create table regle_rapprochement_bancaire (
    id bigserial primary key,
    tenant_id bigint not null,
    societe_interne_id bigint not null,
    libelle varchar(150) not null,
    motif varchar(255),
    sens varchar(20),
    tiers_id bigint,
    priorite integer not null default 100,
    active boolean not null default true,
    date_creation timestamp not null default now(),
    date_modification timestamp not null default now()
);

create index idx_compte_bancaire_contexte on compte_bancaire (tenant_id, societe_interne_id);
create index idx_operation_bancaire_contexte on operation_bancaire (tenant_id, societe_interne_id, date_operation);
create index idx_operation_bancaire_compte on operation_bancaire (tenant_id, societe_interne_id, compte_bancaire_id);
create index idx_import_releve_contexte on import_releve_bancaire (tenant_id, societe_interne_id, date_import);
create index idx_rapprochement_contexte on rapprochement_bancaire (tenant_id, societe_interne_id, operation_bancaire_id);
create index idx_regle_rapprochement_contexte on regle_rapprochement_bancaire (tenant_id, societe_interne_id, active);

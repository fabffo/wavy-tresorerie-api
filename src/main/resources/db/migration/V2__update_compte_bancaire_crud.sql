do $$
begin
    if exists (
        select 1 from information_schema.columns
        where table_name = 'compte_bancaire' and column_name = 'libelle'
    ) and not exists (
        select 1 from information_schema.columns
        where table_name = 'compte_bancaire' and column_name = 'nom_compte'
    ) then
        alter table compte_bancaire rename column libelle to nom_compte;
    end if;

    if exists (
        select 1 from information_schema.columns
        where table_name = 'compte_bancaire' and column_name = 'solde_courant'
    ) and not exists (
        select 1 from information_schema.columns
        where table_name = 'compte_bancaire' and column_name = 'solde_actuel'
    ) then
        alter table compte_bancaire rename column solde_courant to solde_actuel;
    end if;
end $$;

alter table compte_bancaire
    add column if not exists date_solde_initial date;

update compte_bancaire set banque = 'Banque non renseignee' where banque is null or trim(banque) = '';
update compte_bancaire set iban = 'IBAN-' || id where iban is null or trim(iban) = '';
update compte_bancaire set nom_compte = 'Compte bancaire ' || id where nom_compte is null or trim(nom_compte) = '';
update compte_bancaire set solde_actuel = solde_initial where solde_actuel is null;

alter table compte_bancaire
    alter column banque set not null,
    alter column iban set not null;

do $$
begin
    if not exists (
        select 1 from pg_constraint where conname = 'uk_compte_bancaire_contexte_iban'
    ) then
        alter table compte_bancaire
            add constraint uk_compte_bancaire_contexte_iban unique (tenant_id, societe_interne_id, iban);
    end if;
end $$;

drop index if exists idx_compte_bancaire_contexte;
create index idx_compte_bancaire_contexte on compte_bancaire (tenant_id, societe_interne_id, actif);

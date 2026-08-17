alter table rapprochement_bancaire
    add column if not exists date_proposition timestamp,
    add column if not exists date_validation timestamp,
    add column if not exists utilisateur_validation_id bigint,
    add column if not exists date_annulation timestamp,
    add column if not exists utilisateur_annulation_id bigint,
    add column if not exists motif_annulation varchar(255);

update rapprochement_bancaire
set date_proposition = coalesce(date_proposition, date_rapprochement)
where statut = 'PROPOSE';

update rapprochement_bancaire
set date_validation = coalesce(date_validation, date_rapprochement),
    utilisateur_validation_id = coalesce(utilisateur_validation_id, utilisateur_id)
where statut = 'VALIDE';

update rapprochement_bancaire
set date_annulation = coalesce(date_annulation, date_rapprochement),
    utilisateur_annulation_id = coalesce(utilisateur_annulation_id, utilisateur_id)
where statut = 'ANNULE';

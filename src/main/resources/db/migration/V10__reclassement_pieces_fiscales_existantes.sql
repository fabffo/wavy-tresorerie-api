with candidates as (
    select o.*, coalesce((select max(right(p.numero_piece, 6)::int) from piece_rapprochement p
                          where p.tenant_id=o.tenant_id and p.societe_interne_id=o.societe_interne_id
                            and extract(year from p.date_piece)=extract(year from o.date_operation)), 0)
           + row_number() over (partition by o.tenant_id, o.societe_interne_id, extract(year from o.date_operation) order by o.date_operation, o.id) as numero
    from operation_bancaire o
    join rapprochement_bancaire r on r.operation_bancaire_id = o.id and r.facture_id is not null
    where (upper(o.libelle) like '%IMPOT%' or upper(o.libelle) like '%URSSAF%' or (upper(o.libelle) like '%TVA%' and upper(o.libelle) like '%MENSUELLE%'))
      and not exists (select 1 from piece_rapprochement p where p.operation_bancaire_id = o.id)
), inserees as (
    insert into piece_rapprochement (
        tenant_id, societe_interne_id, operation_bancaire_id, tiers_id, type_piece, numero_piece,
        date_piece, libelle, montant_ht, montant_tva, montant_ttc, statut, origine, libelle_rapprochement
    )
    select tenant_id, societe_interne_id, id, tiers_id, 'IMPOT_TAXE',
           'PR-' || extract(year from date_operation)::int || '-' || societe_interne_id || '-' || lpad(numero::text, 6, '0'),
           date_operation, libelle, abs(montant), 0, abs(montant), 'VALIDEE', 'MIGRATION_FACTURE_TECHNIQUE',
           'RAP-' || to_char(date_operation, 'YYYYMMDD') || '-PR-' || extract(year from date_operation)::int || '-' || societe_interne_id || '-' || lpad(numero::text, 6, '0') || '-OP-' || id
    from candidates
    returning *
)
update rapprochement_bancaire r set
    piece_rapprochement_id = p.id, facture_id = null, type_facture = null, type_piece = p.type_piece,
    document_associe_numero = p.numero_piece, document_associe_libelle = p.libelle,
    libelle_rapprochement = p.libelle_rapprochement, mode_rapprochement = 'MIGRATION_PIECE_TRESORERIE'
from inserees p where r.operation_bancaire_id = p.operation_bancaire_id;

update operation_bancaire o set
    piece_rapprochement_id = p.id, facture_id = null, facture_rapprochement_id = null,
    type_facture = null, libelle_rapprochement = p.libelle_rapprochement
from piece_rapprochement p
where p.operation_bancaire_id = o.id and p.origine = 'MIGRATION_FACTURE_TECHNIQUE';

insert into compteur_piece_rapprochement(tenant_id, societe_interne_id, annee, dernier_numero)
select tenant_id, societe_interne_id, extract(year from date_piece)::int, count(*)
from piece_rapprochement group by tenant_id, societe_interne_id, extract(year from date_piece)::int
on conflict (tenant_id, societe_interne_id, annee)
do update set dernier_numero = greatest(compteur_piece_rapprochement.dernier_numero, excluded.dernier_numero);

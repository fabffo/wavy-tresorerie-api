alter table rapprochement_bancaire
    add column if not exists type_piece varchar(40),
    add column if not exists document_associe_numero varchar(80),
    add column if not exists document_associe_libelle varchar(500);

alter table rapprochement_bancaire drop constraint if exists ck_rapprochement_document_unique;
alter table rapprochement_bancaire add constraint ck_rapprochement_document_unique check (
    not (facture_id is not null and piece_rapprochement_id is not null)
);

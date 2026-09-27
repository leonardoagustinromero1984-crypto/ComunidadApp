select
  (select count(*) from public.pets where name like 'SEC05-FIXTURE-%') as pets,
  (select count(*) from public.organizations where name like 'SEC05-FIXTURE-%') as orgs,
  (select count(*) from public.media_assets where object_path like 'sec05/%') as media,
  (select count(*) from public.messages where body like 'SEC05-FIXTURE-%') as messages;

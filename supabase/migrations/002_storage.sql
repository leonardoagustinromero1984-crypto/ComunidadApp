-- Leover Storage bucket and policies
-- Run AFTER creating bucket "leover" as PUBLIC in Supabase Dashboard → Storage

insert into storage.buckets (id, name, public)
values ('leover', 'leover', true)
on conflict (id) do update set public = true;

drop policy if exists leover_public_read on storage.objects;
create policy leover_public_read
    on storage.objects for select
    using (bucket_id = 'leover');

drop policy if exists leover_authenticated_upload on storage.objects;
create policy leover_authenticated_upload
    on storage.objects for insert to authenticated
    with check (bucket_id = 'leover');

drop policy if exists leover_authenticated_update on storage.objects;
create policy leover_authenticated_update
    on storage.objects for update to authenticated
    using (bucket_id = 'leover');

drop policy if exists leover_authenticated_delete on storage.objects;
create policy leover_authenticated_delete
    on storage.objects for delete to authenticated
    using (bucket_id = 'leover');

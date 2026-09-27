-- 1090: tighten FOUND wave cron so 15-minute next_wave_at is not inflated by */5.
-- Does not edit 1088. Tick still no-ops when next_wave_at > now().

do $cron$
begin
  if exists (select 1 from pg_extension where extname = 'pg_cron')
     or exists (select 1 from pg_namespace where nspname = 'cron') then
    begin
      perform cron.unschedule(jobid)
        from cron.job
       where jobname = 'leover-lost-found-waves';
    exception when others then
      null;
    end;
    begin
      perform cron.schedule(
        'leover-lost-found-waves',
        '* * * * *',
        $job$select public.canon_tick_lost_found_waves();$job$
      );
    exception when others then
      raise notice 'pg_cron 1min schedule skipped: %', sqlerrm;
    end;
  end if;
end;
$cron$;

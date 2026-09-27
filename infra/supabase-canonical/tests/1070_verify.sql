select
  (select count(*) from information_schema.columns
    where table_schema = 'public'
      and table_name = 'support_tickets'
      and column_name in (
        'category', 'description', 'priority', 'assigned_to', 'updated_at'
      )) as ticket_columns,
  (select count(*) from pg_tables
    where schemaname = 'public' and tablename = 'support_ticket_messages') as messages_table,
  (select relrowsecurity from pg_class c
    join pg_namespace n on n.oid = c.relnamespace
   where n.nspname = 'public' and c.relname = 'support_tickets') as tickets_rls,
  (select count(*) from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
   where n.nspname = 'public'
     and p.proname in (
       'canon_create_support_ticket',
       'canon_list_my_support_tickets',
       'canon_get_support_ticket_for_requester',
       'canon_list_support_queue',
       'canon_get_support_ticket_for_staff',
       'canon_assign_support_ticket',
       'canon_change_support_ticket_status',
       'canon_add_support_requester_message',
       'canon_add_support_internal_message'
     )) as support_rpcs;

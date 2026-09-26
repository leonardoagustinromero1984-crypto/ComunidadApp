-- 1074 static contract. Does not mutate 1071/1072/1073.
select
  to_regprocedure('public.canon_list_my_personal_memories(integer)') is not null as memories_rpc;

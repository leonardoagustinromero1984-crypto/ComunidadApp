select tablename, rowsecurity from pg_tables where schemaname='public' and tablename like 'm17_%' order by 1;

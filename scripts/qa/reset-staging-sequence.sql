select last_value::text as vitacora_sequence_last,
       is_called::text as vitacora_sequence_is_called
from public.vitacora_public_number_seq;

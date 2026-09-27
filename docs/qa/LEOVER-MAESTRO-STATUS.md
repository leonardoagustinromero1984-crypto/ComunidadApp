# LeoVer Maestro status

Prepared for the physical QA cycle on branch `cursor/physical-qa-maestro-prep-16a`.
Backend max remains migration `20260927230000_1104_vitacora_reunification_retirement`.
This block did not execute Maestro, an emulator, or a device, and it did not mutate STAGING.

Staging project: `tobqbddfcyitwgbkthhy`.
Application id: `com.comunidapp.app.staging`.

Static check: `python3 scripts/qa/validate-maestro-static.py`.
The Windows runner `scripts/qa/run-maestro-e2e.ps1` skips flows whose tags include `manual`, `reset-required`, `needs-fresh-fixture`, `physical-two-device`, or `physical-only`. `.maestro/config.yaml` excludes the same set except `physical-only`, which is also tagged `manual`.

## Completed fixtures — do not reset

| Fixture | Id | Current state | Maestro rule |
| --- | --- | --- | --- |
| Mora | `f58305a1-0b83-40ed-bbc3-fdcdc0120fb5` | ACTIVE survivor. One functional VitaCora. Provisional FOUND archived. Provisional VitaCora retired. Owner/finder reunification completed. | Read the survivor. Do not publish another LOST. Do not confirm the pair again. |
| Responder FOUND | `3e238403-174b-4ce8-82fd-58daa4faa636` | IN_CARE. QA03 is custodian, not OWNER. Location label `QA - Responder Path 12, CABA`. | Read the list. Do not tap Tomar caso. |
| Luna | `73e9997e-27fa-4265-946f-36001f27f815` | Publication CLOSED. QA14 application COMPLETED and PERSON OWNER. QA15 application CLOSED. Transfer ACCEPTED. | Read pet and applications. Do not apply or accept. |
| Bruno | `9d9a6e5b-8cc8-4bc8-aa0c-740a1725c830` | Request COMPLETED. Placement CLOSED. QA06 historical SELECTED, AUTHORIZED / ENDED. Shelter RESPONSIBLE / ACTIVE. No active transit. | Read historical copy. Do not publish, apply, choose, or end again. |

QA09 shelter verification is PENDING. It is not "not requested".

## Counts

| Class | Count |
| --- | --- |
| Flows total | 27 |
| READY_TO_RUN | 5 |
| UPDATED_READY | 13 |
| STALE | 0 |
| SUPERSEDED | 0 |
| PHYSICAL_ONLY | 1 |
| NEEDS_FRESH_FIXTURE | 7 |
| PHYSICAL_TWO_DEVICE_REQUIRED | 1 |
| BLOCKED_BY_COMPLETED_FIXTURE | 0 |

`04c_two_device_responder.yaml` is PHYSICAL_TWO_DEVICE_REQUIRED. It also needs a fresh OPEN FOUND, and it is not included in the 7.

BLOCKED_BY_COMPLETED_FIXTURE is 0 because the old mutation steps were replaced by read flows plus fail-closed fresh-fixture files. Those files assert `FRESH_FIXTURE_REQUIRED` before any tap, so a direct run cannot publish, claim, apply, or submit.

## Flow catalog

| Flow | Class | What it does now |
| --- | --- | --- |
| `00_smoke_login.yaml` | READY_TO_RUN | QA01 login. Home still shows Inicio and Feed. Logout goes through Configuración. |
| `00_smoke_qa02.yaml` | READY_TO_RUN | QA02 login shell. |
| `00_smoke_qa03.yaml` | READY_TO_RUN | QA03 login shell. |
| `01_lost_owner.yaml` | UPDATED_READY | Opens Perdí a mi mascota, sees Foto *, Mascota LeoVer *, and QA - Mora. Does not tap Publicar. |
| `01b_lost_publish_fresh.yaml` | NEEDS_FRESH_FIXTURE | Future LOST publish. Not Mora. |
| `01c_mora_survivor_vitacora.yaml` | UPDATED_READY | QA01 opens the survivor VitaCora, expects the hallazgo memory, and does not expect an archived provisional timeline. |
| `02_found_finder.yaml` | UPDATED_READY | Opens Encontré un animal and the provisional-copy warning. Does not tap Publicar. |
| `02b_found_publish_fresh.yaml` | NEEDS_FRESH_FIXTURE | Future FOUND publish. |
| `03_match_owner_notification.yaml` | UPDATED_READY | Home bell opens Notificaciones. Does not tap Podría ser mi mascota. Does not assert mark-read. |
| `03b_owner_assert_finder.yaml` | NEEDS_FRESH_FIXTURE | Future owner assert and finder review. Mora confirm is already one-way. |
| `04_found_claim.yaml` | UPDATED_READY | QA03 reads the IN_CARE list row and opens Mapa de alertas → Lista. Does not claim. |
| `04b_responder_exclusions.yaml` | UPDATED_READY | QA05, QA06, QA04, and QA07 do not see Tomar caso. QA06 continues to Solicitudes de tránsito. |
| `04c_two_device_responder.yaml` | PHYSICAL_TWO_DEVICE_REQUIRED | Device B denial copy is prepared. Not executable here. |
| `04d_found_claim_fresh.yaml` | NEEDS_FRESH_FIXTURE | Future single-device claim of a new OPEN FOUND by QA04. QA06 is excluded. |
| `05_verification.yaml` | UPDATED_READY | QA07 Verificado por LeoVer. QA08 and QA09 Verificación pendiente. No Enviar solicitud. |
| `05b_profile_switch.yaml` | READY_TO_RUN | Usar LeoVer como is still on Perfil. |
| `05c_qr_mora.yaml` | UPDATED_READY | On-screen QR of the survivor. Physical scan is a separate case. |
| `05d_verification_submit_fresh.yaml` | NEEDS_FRESH_FIXTURE | Future NOT_REQUESTED actor. Not QA09. |
| `06_community.yaml` | READY_TO_RUN | Cerca mío, QA - Veterinaria Centro, QA - Pet Shop Centro. |
| `07_adoption.yaml` | UPDATED_READY | Luna pet for QA14, Completada, Cerrada, and no Aceptar on the received list. |
| `07b_adoption_apply_fresh.yaml` | NEEDS_FRESH_FIXTURE | Future Quiero adoptar / Enviar postulación. Do not reopen Luna. |
| `08_transit.yaml` | UPDATED_READY | QA07: pet detail → Buscar hogar de tránsito → Solicitud COMPLETED. QA06: historical SELECTED line. |
| `08b_transit_mutation_fresh.yaml` | NEEDS_FRESH_FIXTURE | Future publish, apply, choose, and Finalizar tránsito. |
| `09_professional.yaml` | UPDATED_READY | Switches to the veterinary context, then Consultorio → Pacientes. |
| `09b_vitacora_proposals.yaml` | UPDATED_READY | Opens Propuestas VitaCora. Does not tap Aceptar or Rechazar. |
| `09c_vet_patient_email.yaml` | PHYSICAL_ONLY | Shows Email del responsable * and stops on El email es obligatorio. Inbox is physical. |
| `09d_agenda.yaml` | UPDATED_READY | Agenda opens Mis turnos. Does not create a turn. |

## Navigation that changed

| Old selector | Current path |
| --- | --- |
| Perfil → Notificaciones | Inicio bell `Notificaciones` opens the canonical list. Configuración → Notificaciones opens preferences. |
| Perfil → Cerrar sesión | Perfil → Configuración → Cerrar sesión. |
| Perfil → verification badge | Perfil → Usar LeoVer como → refuge context → Sumate → Refugios / ONG → Perfil del refugio → Editar organización. |
| Info → Compartir QR, and VitaCora hidden | Pet detail → Ver VitaCora → Compartir QR → Compartir VitaCora. The survivor VitaCora is visible. |
| Tap QA - Bruno on home, then Necesito hogar de tránsito, then Publicar | Perfil → Ver todas → pet → Buscar hogar de tránsito. Completed screen title is still Necesito hogar de tránsito, and Publicar solicitud is hidden. |
| Tránsitos → Postularme | Sumate → Solicitudes de tránsito. Completed Bruno renders as `QA - Bruno Transito · tránsito finalizado`. |
| Adopción → Quiero adoptar → Enviar → Aceptar | Public list hides closed Luna. Applications say Completada or Cerrada. The submit label is Enviar postulación. |
| Agenda → Nuevo turno → Atendido | Veterinary context → Agenda → Mis turnos. |
| Consultorio from the personal bar | Usar LeoVer como → Veterinaria · QA - Veterinaria Centro, then Consultorio. |

## Fresh fixtures required later

Do not seed them in this block.

| Need | Why |
| --- | --- |
| New LOST pet for QA01 | `01b` publish. Mora stays the survivor. |
| New OPEN FOUND | `02b` publish, `03b` match confirm, `04d` claim, and `04c` two-device race. |
| New NOT_REQUESTED shelter actor | `05d` submission. QA09 stays PENDING. |
| New OPEN adoption publication | `07b`. Luna stays CLOSED. |
| New pet with no completed transit | `08b`. Bruno stays historical. |

## Subflows

| Subflow | Role |
| --- | --- |
| `login-person.yaml` | Clears app state, then username login. |
| `login-person-keep.yaml` | Login without clearing state. |
| `logout-person.yaml` | Perfil → Configuración → Cerrar sesión. |
| `open-lost-found.yaml` | Sumate → Perdidos / Encontrados. |
| `open-notifications.yaml` | Inicio bell → Notificaciones. |
| `open-my-pet.yaml` | Perfil → Ver todas → pet name. |
| `open-refuge-profile.yaml` | Refuge context → Perfil del refugio. |
| `switch-profile.yaml` | Usar LeoVer como → `CONTEXT_LABEL`. |
| `open-sumate.yaml` | Sumate hub. |
| `open-community.yaml` | Comunidad. |
| `close-dialogs.yaml` | Permission and tutorial dismiss, when those buttons are visible. |
| `return-home.yaml` | Inicio. |

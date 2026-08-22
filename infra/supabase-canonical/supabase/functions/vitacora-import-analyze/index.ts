// LeoVer VitaCora import analyzer — parses XLSX server-side.
// Uses the caller's JWT. Never embeds service_role.

import { serve } from "https://deno.land/std@0.224.0/http/server.ts";
import * as XLSX from "https://esm.sh/xlsx@0.18.5";

const MAX_BYTES = 5 * 1024 * 1024;
const MAX_ROWS = 500;
const MAX_SHEETS = 12;
const TEMPLATE_TYPE = "LEOVER_VITACORA_IMPORT";
const HEADERS: Record<string, string> = {
  "Referencia interna": "external_pet_id",
  "Nombre": "name",
  "Especie": "species",
  "Sexo": "sex",
  "Estado": "status",
  "País": "country",
  "Provincia": "administrative_area",
  "Localidad": "locality",
  "Edad aproximada": "estimated_age",
  "Fecha de nacimiento": "birth_date",
  "Raza": "breed",
  "Tamaño": "size",
  "Color": "color",
  "Castrado": "neutered",
  "Vacunado": "vaccinated",
  "Descripción": "description",
  "Fecha de ingreso": "intake_date",
  "Convive con perros": "lives_with_dogs",
  "Convive con gatos": "lives_with_cats",
  "Convive con niños": "lives_with_kids",
  "Necesidades especiales": "special_needs",
  "Observaciones": "notes",
};

serve(async (req) => {
  const cors = {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Headers": "authorization, apikey, content-type",
  };
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });

  const auth = req.headers.get("Authorization") ?? "";
  const apikey = req.headers.get("apikey") ?? "";
  if (!auth.toLowerCase().startsWith("bearer ")) {
    return json({ error: "NOT_AUTHENTICATED" }, 401, cors);
  }

  let body: { job_id?: string };
  try {
    body = await req.json();
  } catch {
    return json({ error: "INVALID_JSON" }, 400, cors);
  }
  const jobId = body.job_id?.trim();
  if (!jobId) return json({ error: "JOB_ID_REQUIRED" }, 400, cors);

  const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
  const jobRes = await fetch(`${supabaseUrl}/rest/v1/rpc/canon_import_get`, {
    method: "POST",
    headers: {
      Authorization: auth,
      apikey,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ p_import_id: jobId }),
  });
  if (!jobRes.ok) {
    return json({ error: "JOB_FORBIDDEN", status: jobRes.status, detail: await jobRes.text() }, jobRes.status, cors);
  }
  const jobJson = await jobRes.json();
  const job = jobJson.job ?? jobJson;
  const bucket = job.storage_bucket ?? "vitacora-import";
  const path = job.storage_path as string;
  if (!path) return json({ error: "STORAGE_PATH_MISSING" }, 400, cors);

  const fileRes = await fetch(
    `${supabaseUrl}/storage/v1/object/${bucket}/${path}`,
    { headers: { Authorization: auth, apikey } },
  );
  if (!fileRes.ok) {
    return json({ error: "FILE_NOT_FOUND", status: fileRes.status }, 400, cors);
  }
  const bytes = new Uint8Array(await fileRes.arrayBuffer());
  if (bytes.byteLength > MAX_BYTES) {
    return analyze(jobId, auth, apikey, supabaseUrl, rejected("MAX_FILE_SIZE_EXCEEDED"), cors);
  }
  if (bytes[0] === 0xd0 && bytes[1] === 0xcf) {
    return analyze(jobId, auth, apikey, supabaseUrl, rejected("XLS_REJECTED"), cors);
  }
  if (bytes[0] !== 0x50 || bytes[1] !== 0x4b) {
    return analyze(jobId, auth, apikey, supabaseUrl, rejected("FAKE_XLSX"), cors);
  }

  let workbook: XLSX.WorkBook;
  try {
    workbook = XLSX.read(bytes, { type: "array", cellFormula: true, bookVBA: true });
  } catch {
    return analyze(jobId, auth, apikey, supabaseUrl, rejected("XLSX_CORRUPT"), cors);
  }
  if (workbook.Workbook?.WBProps?.codeName || workbook.vbaraw) {
    return analyze(jobId, auth, apikey, supabaseUrl, rejected("XLSM_REJECTED"), cors);
  }
  const names = workbook.SheetNames ?? [];
  if (names.length > MAX_SHEETS) {
    return analyze(jobId, auth, apikey, supabaseUrl, rejected("TOO_MANY_SHEETS"), cors);
  }
  const metaSheet = workbook.Sheets["_LEOVER_META"];
  let templateType: string | null = null;
  let templateVersion: number | null = null;
  if (metaSheet) {
    const meta = XLSX.utils.sheet_to_json<(string | number)[]>(metaSheet, { header: 1 }) as (string | number)[][];
    for (const row of meta) {
      const k = String(row[0] ?? "").trim();
      const v = String(row[1] ?? "").trim();
      if (k === "template_type") templateType = v;
      if (k === "template_version") templateVersion = Number(v);
    }
  }
  const petsSheet = workbook.Sheets["MASCOTAS"] ?? workbook.Sheets[names[1]] ?? workbook.Sheets[names[0]];
  const grid = XLSX.utils.sheet_to_json<(XLSX.CellObject | undefined)[]>(petsSheet, {
    header: 1,
    raw: false,
    defval: "",
  }) as unknown[][];
  if (!grid.length) {
    return analyze(jobId, auth, apikey, supabaseUrl, {
      template_type: templateType,
      template_version: templateVersion,
      rejected: "XLSX_CORRUPT",
      rows: [],
    }, cors);
  }
  const header = (grid[0] as unknown[]).map((h) => HEADERS[String(h ?? "").trim()] ?? null);
  const rows = [];
  for (let i = 1; i < grid.length; i++) {
    const raw = grid[i] as unknown[];
    if (!raw || raw.every((c) => String(c ?? "").trim() === "")) continue;
    const values: Record<string, string> = {};
    const formulaFields: string[] = [];
    header.forEach((key, col) => {
      if (!key) return;
      values[key] = String(raw[col] ?? "").trim();
      const addr = XLSX.utils.encode_cell({ r: i, c: col });
      const cell = petsSheet[addr];
      if (cell && cell.f) formulaFields.push(key);
    });
    rows.push({ row_number: i + 1, values, formula_fields: formulaFields });
  }
  if (rows.length > MAX_ROWS) {
    return analyze(jobId, auth, apikey, supabaseUrl, rejected("MAX_ROWS_EXCEEDED"), cors);
  }
  return analyze(jobId, auth, apikey, supabaseUrl, {
    template_type: templateType,
    template_version: templateVersion,
    rows,
  }, cors);
});

function rejected(code: string) {
  return { template_type: null, template_version: null, rejected: code, rows: [] };
}

async function analyze(
  jobId: string,
  auth: string,
  apikey: string,
  supabaseUrl: string,
  payload: unknown,
  cors: Record<string, string>,
) {
  const res = await fetch(`${supabaseUrl}/rest/v1/rpc/canon_import_analyze`, {
    method: "POST",
    headers: {
      Authorization: auth,
      apikey,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ p_import_id: jobId, p_payload: payload }),
  });
  const text = await res.text();
  return new Response(text, {
    status: res.status,
    headers: { ...cors, "Content-Type": "application/json" },
  });
}

function json(obj: unknown, status: number, cors: Record<string, string>) {
  return new Response(JSON.stringify(obj), {
    status,
    headers: { ...cors, "Content-Type": "application/json" },
  });
}

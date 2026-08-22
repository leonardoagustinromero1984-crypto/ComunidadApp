// LeoVer KLIPY proxy — key lives in Edge Function secrets, never in the APK.
// Secrets: KLIPY_API_KEY
// If missing, returns 503 { disabled: true } so the Android tab stays hidden.

import { serve } from "https://deno.land/std@0.224.0/http/server.ts";

const KLIPY_BASE = "https://api.klipy.com/api/v1";

serve(async (req) => {
  const cors = {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Headers": "authorization, apikey, content-type",
  };
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });

  const key = Deno.env.get("KLIPY_API_KEY")?.trim();
  if (!key) {
    return new Response(
      JSON.stringify({ disabled: true, reason: "KLIPY_KEY_REQUIRED" }),
      { status: 503, headers: { ...cors, "Content-Type": "application/json" } },
    );
  }

  const url = new URL(req.url);
  const action = url.searchParams.get("action") === "search" ? "search" : "trending";
  const kindRaw = (url.searchParams.get("kind") ?? "gif").toLowerCase();
  const kind = kindRaw === "sticker" ? "stickers" : "gifs";
  const q = url.searchParams.get("q") ?? "";
  const page = url.searchParams.get("page") ?? "1";
  const perPage = url.searchParams.get("per_page") ?? "24";
  const locale = url.searchParams.get("locale") ?? "es_AR";
  const rating = url.searchParams.get("rating") ?? "g";

  const target = new URL(`${KLIPY_BASE}/${key}/${kind}/${action}`);
  if (action === "search") target.searchParams.set("q", q);
  target.searchParams.set("page", page);
  target.searchParams.set("per_page", perPage);
  target.searchParams.set("locale", locale);
  target.searchParams.set("rating", rating);
  target.searchParams.set("content_filter", "safe");

  try {
    const upstream = await fetch(target, { headers: { Accept: "application/json" } });
    const text = await upstream.text();
    return new Response(text, {
      status: upstream.status,
      headers: { ...cors, "Content-Type": "application/json" },
    });
  } catch (_err) {
    return new Response(
      JSON.stringify({ error: "KLIPY_UPSTREAM_FAILURE" }),
      { status: 502, headers: { ...cors, "Content-Type": "application/json" } },
    );
  }
});

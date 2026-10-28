"use strict";

const { http } = require("@google-cloud/functions-framework");

const PHA_API = "https://www.press.org.tr/api/v1/haberler";
const WEATHER_API = "https://api.weatherapi.com/v1/forecast.json";
const FOOTBALL_API = "https://v3.football.api-sports.io";
const VOLLEYBALL_API = "https://v1.volleyball.api-sports.io";
const NEWS_HOSTS = new Set([
  "aa.com.tr", "bbc.com", "bbc.co.uk", "cnnturk.com", "diken.com.tr", "ensonhaber.com",
  "haberturk.com", "hurriyet.com.tr", "milliyet.com.tr", "ntv.com.tr", "press.org.tr",
  "sozcu.com.tr", "trthaber.com",
]);
const FOOTBALL_ENDPOINTS = new Set([
  "countries", "fixtures", "injuries", "leagues", "players", "predictions", "standings",
  "teams", "timezone", "topassists", "topscorers", "transfers", "trophies", "venues",
]);
const VOLLEYBALL_ENDPOINTS = new Set(["games", "leagues", "standings", "teams"]);
const cache = new Map();
const requestCounts = new Map();
const CACHE_MS = 2 * 60 * 1000;
const MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
const MAX_ARTICLE_BYTES = 1024 * 1024;
const RSS_NEWS_FEEDS = [
  ["https://www.ensonhaber.com/rss/ensonhaber.xml", "Ensonhaber", "Gündem"],
  ["https://feeds.bbci.co.uk/turkce/rss.xml", "BBC Türkçe", "Dünya"],
  ["https://www.ntv.com.tr/gundem.rss", "NTV", "Gündem"],
  ["https://www.ntv.com.tr/ekonomi.rss", "NTV", "Ekonomi"],
  ["https://www.ntv.com.tr/spor.rss", "NTV", "Spor"],
  ["https://www.trthaber.com/sondakika.rss", "TRT Haber", "Gündem"],
  ["https://www.trthaber.com/spor_articles.rss", "TRT Spor", "Spor"],
  ["https://www.haberturk.com/rss", "Habertürk", "Gündem"],
  ["https://www.aa.com.tr/tr/rss/default?cat=guncel", "Anadolu Ajansı", "Gündem"],
  ["https://www.aa.com.tr/tr/rss/default?cat=ekonomi", "Anadolu Ajansı", "Ekonomi"],
  ["https://www.aa.com.tr/tr/rss/default?cat=spor", "Anadolu Ajansı", "Spor"],
];

http("cizgiApi", async (req, res) => {
  applyCors(req, res);
  if (req.method === "OPTIONS") return res.status(204).send("");
  if (!withinRateLimit(req)) return sendJson(res, 429, { error: "rate_limited" });

  const path = new URL(req.originalUrl || req.url, "https://gateway.invalid").pathname;
  try {
    if (req.method === "GET" && path === "/health") {
      return sendJson(res, 200, {
        status: "ok",
        configured: {
          press: Boolean(process.env.PRESS_API_KEY),
          football: Boolean(process.env.FOOTBALL_API_KEY),
          volleyball: Boolean(process.env.VOLLEYBALL_API_KEY || process.env.FOOTBALL_API_KEY),
          weather: Boolean(process.env.WEATHER_API_KEY),
        },
      });
    }
    if (req.method === "GET" && path === "/v1/news") return await handleNews(req, res);
    if (req.method === "GET" && path === "/v1/weather") return await handleWeather(req, res);
    if (req.method === "GET" && path.startsWith("/v1/football/")) return await handleFootball(req, res, path);
    if (req.method === "GET" && path.startsWith("/v1/volleyball/")) return await handleVolleyball(req, res, path);
    return sendJson(res, 404, { error: "not_found" });
  } catch (error) {
    console.error("cizgi_api_error", error?.name || "Error");
    return sendJson(res, 502, { error: "upstream_unavailable" });
  }
});

async function handleNews(req, res) {
  const key = process.env.PRESS_API_KEY?.trim();
  if (!key) return await handleRssNews(req, res);
  const url = new URL(PHA_API);
  const page = parseInteger(req.query.page, 1, 1, 100_000);
  const limit = parseInteger(req.query.limit, 100, 1, 100);
  url.searchParams.set("sayfa", String(page));
  url.searchParams.set("adet", String(limit));
  if (req.query.since) {
    const since = new Date(String(req.query.since));
    if (!Number.isFinite(since.getTime())) return sendJson(res, 400, { error: "invalid_since" });
    url.searchParams.set("since", since.toISOString());
  }
  const cacheKey = `pha:${url.search}`;
  const cached = getCache(cacheKey);
  if (cached) return sendJson(res, 200, cached);
  const response = await fetch(url, {
    headers: { Authorization: `Bearer ${key}`, Accept: "application/json", "User-Agent": "CizgiNews/1.0" },
    signal: AbortSignal.timeout(12_000),
  });
  return forwardJson(response, res, cacheKey, 60_000);
}

async function handleRssNews(req, res) {
  const page = parseInteger(req.query.page, 1, 1, 100_000);
  const limit = parseInteger(req.query.limit, 100, 1, 100);
  const cacheKey = "rss:news";
  let stories = getCache(cacheKey);
  if (!stories) {
    const reports = await Promise.allSettled(RSS_NEWS_FEEDS.map(async ([feedUrl, publisher, topic]) => {
      const response = await fetch(feedUrl, {
        headers: {
          Accept: "application/rss+xml, application/atom+xml, application/xml, text/xml;q=0.9, */*;q=0.8",
          "User-Agent": "CizgiNews/1.0 (news reader)",
        },
        signal: AbortSignal.timeout(8_000),
      });
      if (!response.ok) return [];
      const xml = await readTextLimited(response, 2 * 1024 * 1024);
      return parseRssItems(xml, publisher, topic);
    }));
    stories = reports.flatMap((result) => result.status === "fulfilled" ? result.value : []);
    const unique = new Map();
    for (const story of stories) {
      const key = story.url || story.title.toLocaleLowerCase("tr-TR");
      if (!unique.has(key)) unique.set(key, story);
    }
    stories = [...unique.values()].sort((a, b) => Date.parse(b.date_published) - Date.parse(a.date_published));
    if (stories.length) putCache(cacheKey, stories, 60_000);
  }
  const start = (page - 1) * limit;
  return sendJson(res, 200, {
    items: stories.slice(start, start + limit),
    page,
    total: stories.length,
    total_pages: Math.max(1, Math.ceil(stories.length / limit)),
    source: "publisher_rss",
  });
}

function parseRssItems(xml, publisher, topic) {
  const items = [];
  const pattern = /<(?:[\w.-]+:)?(?:item|entry)\b[^>]*>([\s\S]*?)<\/(?:[\w.-]+:)?(?:item|entry)\s*>/gi;
  for (const match of xml.matchAll(pattern)) {
    const block = match[1];
    const readTag = (...names) => {
      for (const name of names) {
        const escaped = name.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
        const found = block.match(new RegExp(`<(?:[\\w.-]+:)?${escaped}\\b[^>]*>([\\s\\S]*?)<\\/(?:[\\w.-]+:)?${escaped}\\s*>`, "i"));
        if (found) return found[1].replace(/^<!\[CDATA\[/i, "").replace(/\]\]>$/, "").trim();
      }
      return "";
    };
    const title = stripHtml(readTag("title"));
    if (title.length < 5) continue;
    let url = readTag("link").trim();
    if (!url) url = block.match(/<(?:[\w.-]+:)?link\b[^>]*href=["']([^"']+)["']/i)?.[1] || "";
    const summary = stripHtml(readTag("description", "summary", "content:encoded", "content"));
    const published = readTag("pubDate", "published", "updated", "date");
    const timestamp = Date.parse(published);
    const datePublished = Number.isFinite(timestamp) ? new Date(timestamp).toISOString() : "";
    const image = block.match(/<(?:media:content|media:thumbnail|enclosure)\b[^>]*\burl=["']([^"']+)["']/i)?.[1] ||
      block.match(/<img\b[^>]*\bsrc=["']([^"']+)["']/i)?.[1] || "";
    items.push({
      id: url || title,
      title,
      summary,
      content_html: summary,
      url,
      date_published: datePublished,
      organization: publisher,
      image,
      sectors: [{ name: topic, slug: topic.toLocaleLowerCase("tr-TR") }],
      is_breaking: /son dakika|flaş gelişme/i.test(title),
    });
  }
  return items;
}

async function handleWeather(req, res) {
  const key = requireSecret("WEATHER_API_KEY");
  const city = String(req.query.q || "").trim().slice(0, 100);
  if (!city) return sendJson(res, 400, { error: "city_required" });
  const url = new URL(WEATHER_API);
  url.searchParams.set("key", key);
  url.searchParams.set("q", city);
  url.searchParams.set("days", String(parseInteger(req.query.days, 5, 1, 5)));
  url.searchParams.set("aqi", "no");
  url.searchParams.set("alerts", "no");
  const cacheKey = `weather:${city.toLowerCase()}`;
  const cached = getCache(cacheKey);
  if (cached) return sendJson(res, 200, cached);
  const response = await fetch(url, { signal: AbortSignal.timeout(10_000) });
  return forwardJson(response, res, cacheKey, 10 * 60_000);
}

async function handleFootball(req, res, path) {
  const key = requireSecret("FOOTBALL_API_KEY");
  const segments = path.substring("/v1/football/".length).split("/").filter(Boolean);
  const endpoint = segments[0];
  if (!FOOTBALL_ENDPOINTS.has(endpoint)) return sendJson(res, 404, { error: "football_endpoint_not_allowed" });
  const subresource = segments[1] || "";
  if (segments.length > 2 || (subresource && !(endpoint === "fixtures" && subresource === "lineups"))) {
    return sendJson(res, 404, { error: "football_subresource_not_allowed" });
  }
  const upstreamPath = subresource ? `${endpoint}/${subresource}` : endpoint;
  const url = new URL(`${FOOTBALL_API}/${upstreamPath}`);
  for (const [name, value] of Object.entries(req.query)) {
    if (!/^[a-z][a-z0-9_]{0,31}$/i.test(name)) continue;
    url.searchParams.set(name, String(value).slice(0, 120));
  }
  const cacheKey = `football:${url.pathname}${url.search}`;
  const cached = getCache(cacheKey);
  if (cached) return sendJson(res, 200, cached);
  const response = await fetch(url, {
    headers: { "x-apisports-key": key, Accept: "application/json" },
    signal: AbortSignal.timeout(10_000),
  });
  return forwardJson(response, res, cacheKey, sportsCacheTtl(endpoint));
}

async function handleVolleyball(req, res, path) {
  const key = process.env.VOLLEYBALL_API_KEY || process.env.FOOTBALL_API_KEY;
  if (!key) return sendJson(res, 503, { error: "volleyball_provider_not_configured" });
  const endpoint = path.substring("/v1/volleyball/".length).split("/")[0];
  if (!VOLLEYBALL_ENDPOINTS.has(endpoint)) return sendJson(res, 404, { error: "volleyball_endpoint_not_allowed" });
  const url = new URL(`${VOLLEYBALL_API}/${endpoint}`);
  for (const [name, value] of Object.entries(req.query)) {
    if (!/^[a-z][a-z0-9_]{0,31}$/i.test(name)) continue;
    url.searchParams.set(name, String(value).slice(0, 120));
  }
  const cacheKey = `volleyball:${url.pathname}${url.search}`;
  const cached = getCache(cacheKey);
  if (cached) return sendJson(res, 200, cached);
  const response = await fetch(url, {
    headers: { "x-apisports-key": key, Accept: "application/json" },
    signal: AbortSignal.timeout(10_000),
  });
  return forwardJson(response, res, cacheKey, sportsCacheTtl(endpoint));
}

function sportsCacheTtl(endpoint) {
  if (endpoint === "games" || endpoint === "fixtures") return 5 * 60_000;
  if (endpoint === "standings" || endpoint === "leagues") return 6 * 60 * 60_000;
  return 60_000;
}

async function fetchArticleText(rawUrl) {
  try {
    let currentUrl = rawUrl;
    let response;
    for (let redirectCount = 0; redirectCount <= 3; redirectCount += 1) {
      if (!isAllowedNewsUrl(currentUrl)) return "";
      response = await fetch(currentUrl, {
        headers: { Accept: "text/html,application/xhtml+xml", "User-Agent": "Mozilla/5.0 (compatible; CizgiNews/1.0)" },
        signal: AbortSignal.timeout(8_000),
        redirect: "manual",
      });
      if (![301, 302, 303, 307, 308].includes(response.status)) break;
      const location = response.headers.get("location");
      if (!location || redirectCount === 3) return "";
      currentUrl = new URL(location, currentUrl).toString();
    }
    if (!response?.ok || !isAllowedNewsUrl(currentUrl)) return "";
    const type = response.headers.get("content-type") || "";
    if (!type.includes("text/html")) return "";
    const html = await readTextLimited(response, MAX_ARTICLE_BYTES);
    const jsonLd = extractArticleBodyFromJsonLd(html);
    if (jsonLd.length >= 700) return stripHtml(jsonLd);
    const article = html.match(/<article\b[^>]*>([\s\S]*?)<\/article\s*>/i)?.[1] || "";
    const paragraphs = [...article.matchAll(/<p\b[^>]*>([\s\S]*?)<\/p\s*>/gi)]
      .map((match) => stripHtml(match[1]))
      .filter((paragraph) => paragraph.length > 35);
    if (paragraphs.length >= 2) return paragraphs.join("\n").slice(0, 12_000);
    return stripHtml(article).slice(0, 12_000);
  } catch {
    return "";
  }
}

function extractArticleBodyFromJsonLd(html) {
  for (const match of html.matchAll(/<script\b[^>]*type=["']application\/ld\+json["'][^>]*>([\s\S]*?)<\/script>/gi)) {
    try {
      const json = JSON.parse(match[1]);
      const objects = Array.isArray(json) ? json : [json];
      for (const item of objects) {
        const articleBody = item?.articleBody || item?.mainEntity?.articleBody;
        if (typeof articleBody === "string" && articleBody.length >= 700) return articleBody;
      }
    } catch {
      // Ignore malformed structured metadata and try the article element.
    }
  }
  return "";
}

async function forwardJson(upstream, res, cacheKey, ttlMs) {
  const payload = await readJsonResponse(upstream);
  if (!upstream.ok) {
    const retryAfter = upstream.headers.get("retry-after");
    if (retryAfter) res.set("Retry-After", retryAfter);
    return sendJson(res, upstream.status === 429 ? 429 : 502, { error: "provider_request_failed" });
  }
  putCache(cacheKey, payload, ttlMs);
  return sendJson(res, 200, payload);
}

async function readJsonResponse(response) {
  const text = await readTextLimited(response, MAX_RESPONSE_BYTES);
  return text ? JSON.parse(text) : {};
}

async function readTextLimited(response, maxBytes) {
  const reader = response.body?.getReader();
  if (!reader) return "";
  const chunks = [];
  let size = 0;
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    size += value.byteLength;
    if (size > maxBytes) {
      await reader.cancel();
      throw new Error("response_too_large");
    }
    chunks.push(value);
  }
  return Buffer.concat(chunks.map((chunk) => Buffer.from(chunk))).toString("utf8");
}

function stripHtml(text) {
  return String(text)
    .replace(/<(script|style|nav|footer|header|noscript|svg)\b[^>]*>[\s\S]*?<\/\1>/gi, " ")
    .replace(/<\/(p|div|li|h[1-6]|br|article|section)>/gi, "\n")
    .replace(/<[^>]+>/g, " ")
    .replace(/&nbsp;|&#160;/gi, " ")
    .replace(/&amp;/gi, "&")
    .replace(/&quot;|&#34;/gi, '"')
    .replace(/&#39;|&apos;/gi, "'")
    .replace(/&lt;/gi, "<")
    .replace(/&gt;/gi, ">")
    .replace(/&#(\d+);/g, (_, number) => String.fromCodePoint(Number(number)))
    .replace(/&#x([0-9a-f]+);/gi, (_, number) => String.fromCodePoint(parseInt(number, 16)))
    .replace(/[\t\r ]+/g, " ")
    .replace(/ *\n */g, "\n")
    .replace(/\n{3,}/g, "\n\n")
    .trim();
}

function isAllowedNewsUrl(rawUrl) {
  try {
    const url = new URL(rawUrl);
    if (url.protocol !== "https:" || url.username || url.password || url.port) return false;
    const host = url.hostname.toLowerCase();
    return [...NEWS_HOSTS].some((domain) => host === domain || host.endsWith(`.${domain}`));
  } catch {
    return false;
  }
}

function requireSecret(name) {
  const value = process.env[name]?.trim();
  if (!value) throw new Error(`missing_${name}`);
  return value;
}

function parseInteger(value, fallback, min, max) {
  const parsed = Number.parseInt(String(value ?? ""), 10);
  return Number.isFinite(parsed) ? Math.max(min, Math.min(max, parsed)) : fallback;
}

function getCache(key) {
  const entry = cache.get(key);
  if (!entry || entry.expiresAt <= Date.now()) {
    cache.delete(key);
    return null;
  }
  return entry.value;
}

function putCache(key, value, ttlMs) {
  if (cache.size >= 4_000) cache.delete(cache.keys().next().value);
  cache.set(key, { value, expiresAt: Date.now() + ttlMs });
}

function withinRateLimit(req) {
  const ip = String(req.headers["x-forwarded-for"] || req.socket.remoteAddress || "unknown").split(",")[0].trim();
  const now = Date.now();
  const entry = requestCounts.get(ip);
  if (!entry || entry.resetAt <= now) {
    requestCounts.set(ip, { count: 1, resetAt: now + 60_000 });
    if (requestCounts.size > 5_000) requestCounts.delete(requestCounts.keys().next().value);
    return true;
  }
  entry.count += 1;
  return entry.count <= 45;
}

function applyCors(req, res) {
  res.set("Access-Control-Allow-Origin", "*");
  res.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
  res.set("Access-Control-Allow-Headers", "Content-Type, X-Firebase-AppCheck");
  res.set("Cache-Control", "no-store");
}

function sendJson(res, status, body) {
  res.status(status).set("Content-Type", "application/json; charset=utf-8").send(JSON.stringify(body));
}


import * as admin from "firebase-admin";
import { onRequest } from "firebase-functions/v2/https";
import type { Request, Response } from "express";

admin.initializeApp();
const db = admin.firestore();

// Pricing Constants: 5x Premium Rate for International Multilingual Vision & Search
const BASE_AI_COST_CENTS = 1; // $0.01 standard base rate
const PREMIUM_MULTIPLIER = 5; // 5x multiplier as required
const MULTILINGUAL_5X_COST_CENTS = BASE_AI_COST_CENTS * PREMIUM_MULTIPLIER; // $0.05 (5 cents) per call

const VALID_ONE_TIME_SKUS: Record<string, number> = {
  docusnap_lifetime_99: 99,
  docusnap_lifetime_199: 199,
  docusnap_lifetime_299: 299,
  docusnap_lifetime_499: 499,
  docusnap_lifetime_749: 749,
  docusnap_lifetime_999: 999,
};

const GEMINI_ENDPOINT =
  "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent";

function applyCors(res: Response): void {
  res.set("Access-Control-Allow-Origin", "*");
  res.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
  res.set("Access-Control-Allow-Headers", "Content-Type, Authorization");
}

async function verifyAuthUid(req: Request): Promise<string> {
  const authHeader = req.headers.authorization || "";
  if (!authHeader.startsWith("Bearer ")) {
    throw new Error("UNAUTHORIZED: Missing Firebase Bearer token");
  }
  const idToken = authHeader.substring("Bearer ".length).trim();
  const decoded = await admin.auth().verifyIdToken(idToken);
  return decoded.uid;
}

async function getWalletBalanceCents(uid: string): Promise<number> {
  const walletSnap = await db.doc(`users/${uid}/wallet/main`).get();
  if (!walletSnap.exists) return 0;
  return Number(walletSnap.data()?.balanceCents ?? 0);
}

/**
 * Atomically deducts 5x usage charge from the user's pre-loaded One-Time balance.
 */
async function deduct5xBalance(
  uid: string,
  featureName: string,
  metadata: Record<string, unknown>
): Promise<number> {
  const walletRef = db.doc(`users/${uid}/wallet/main`);
  const txRef = db.collection(`users/${uid}/wallet_transactions`).doc();

  return await db.runTransaction(async (tx) => {
    const snap = await tx.get(walletRef);
    const currentCents = Number(snap.data()?.balanceCents ?? 0);
    if (currentCents < MULTILINGUAL_5X_COST_CENTS) {
      throw new Error("INSUFFICIENT_BALANCE");
    }

    const newBalanceCents = currentCents - MULTILINGUAL_5X_COST_CENTS;
    tx.set(
      walletRef,
      {
        balanceCents: newBalanceCents,
        totalSpentCents: admin.firestore.FieldValue.increment(MULTILINGUAL_5X_COST_CENTS),
        total5xCalls: admin.firestore.FieldValue.increment(1),
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      },
      { merge: true }
    );

    tx.set(txRef, {
      type: "DEBIT_5X_MULTILINGUAL_AI",
      feature: featureName,
      multiplier: `${PREMIUM_MULTIPLIER}x`,
      baseCostCents: BASE_AI_COST_CENTS,
      chargedCents: MULTILINGUAL_5X_COST_CENTS,
      balanceAfterCents: newBalanceCents,
      metadata,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
    });

    return newBalanceCents;
  });
}

/**
 * Calls Gemini API using Primary Key (`x-goog-api-key`) with automatic failover to Backup Key.
 * Keys remain strictly server-side inside Firebase Functions environment/secrets.
 */
async function callGeminiWithFailover(
  parts: Array<Record<string, unknown>>,
  jsonMode = true,
  temperature = 0.2
): Promise<string> {
  const keys = [
    process.env.GEMINI_PRIMARY_KEY?.trim(),
    process.env.GEMINI_BACKUP_KEY?.trim(),
  ].filter((k): k is string => Boolean(k));

  if (keys.length === 0) {
    throw new Error("SERVER_CONFIG_ERROR: Gemini keys are not configured on backend");
  }

  const body: Record<string, unknown> = {
    contents: [{ role: "user", parts }],
    generationConfig: {
      temperature,
      ...(jsonMode ? { responseMimeType: "application/json" } : {}),
    },
  };

  let lastError = "Unknown Gemini error";

  for (let i = 0; i < keys.length; i++) {
    const apiKey = keys[i];
    try {
      const response = await fetch(GEMINI_ENDPOINT, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "x-goog-api-key": apiKey,
        },
        body: JSON.stringify(body),
      });

      if (!response.ok) {
        const errText = await response.text();
        lastError = `Key#${i + 1} HTTP ${response.status}: ${errText.slice(0, 300)}`;
        console.warn(`Gemini primary/key #${i + 1} failed (${response.status}), trying backup...`);
        continue;
      }

      const data = (await response.json()) as {
        candidates?: Array<{ content?: { parts?: Array<{ text?: string }> } }>;
      };
      const text = data.candidates?.[0]?.content?.parts?.[0]?.text;
      if (text && text.trim().length > 0) {
        return text.trim();
      }
      lastError = `Key#${i + 1} returned empty candidate`;
    } catch (err) {
      lastError = `Key#${i + 1} network error: ${(err as Error).message}`;
      console.warn(lastError);
    }
  }

  throw new Error(`GEMINI_ALL_KEYS_FAILED: ${lastError}`);
}

/**
 * 1. Multilingual Vision OCR & Semantic Document Extraction (Charged at 5x rate from One-Time Balance)
 */
export const aiVisionExtract = onRequest({ region: "us-central1" }, async (req, res) => {
  applyCors(res);
  if (req.method === "OPTIONS") {
    res.status(204).send("");
    return;
  }
  if (req.method !== "POST") {
    res.status(405).json({ error: "Method not allowed" });
    return;
  }

  try {
    const uid = await verifyAuthUid(req);
    const currentBalance = await getWalletBalanceCents(uid);
    if (currentBalance < MULTILINGUAL_5X_COST_CENTS) {
      res.status(402).json({
        error: "INSUFFICIENT_BALANCE",
        message: "Insufficient One-Time Prepaid Balance for 5x Multilingual AI Vision.",
        requiredCents: MULTILINGUAL_5X_COST_CENTS,
        currentBalanceCents: currentBalance,
        multiplier: `${PREMIUM_MULTIPLIER}x`,
      });
      return;
    }

    const {
      imageBase64,
      mimeType = "image/jpeg",
      targetLanguage = "English",
      existingOcrText = "",
    } = req.body || {};

    if (!imageBase64 && !existingOcrText) {
      res.status(400).json({ error: "imageBase64 or existingOcrText is required" });
      return;
    }

    const prompt = `
You are an international multilingual AI document analyzer for DocuSnap AI.
Target response language: ${targetLanguage}.

Examine the attached document image (could be a receipt, bill, prescription, bank slip, handwritten note, or mobile screenshot in ANY language: Japanese, Chinese, Korean, Arabic, Urdu, Hindi, European languages, etc.).
1. Transcribe ALL visible text accurately in its ORIGINAL language & script inside "raw_ocr".
2. Summarize cleanly in "${targetLanguage}" inside "summary" and create a short descriptive "title".
3. Extract structured "key_entities" cleanly (use detected currency like JPY, USD, EUR, GBP, AED, INR, PKR).
4. Include BOTH original-language keywords AND English translated keywords in "searchable_tags" so users can search in their native script OR English!

Return ONLY valid JSON:
{
  "document_type": "Medical Prescription | Expense Receipt | Utility Bill | Bank Slip | General Document",
  "title": "Descriptive title",
  "summary": "1-2 sentence summary",
  "raw_ocr": "Complete transcribed text in original script",
  "key_entities": {
    "doctor_name": "string or null",
    "medicines": ["string"],
    "total_amount": 1200.0 or null,
    "vendor_or_hospital": "string or null",
    "date": "YYYY-MM-DD"
  },
  "searchable_tags": ["native_tag", "english_tag", "category"]
}`;

    const parts: Array<Record<string, unknown>> = [{ text: prompt }];
    if (existingOcrText) {
      parts.push({ text: `Supplementary OCR hint:\n${String(existingOcrText).slice(0, 1500)}` });
    }
    if (imageBase64) {
      parts.push({
        inlineData: {
          mimeType,
          data: imageBase64,
        },
      });
    }

    const rawJson = await callGeminiWithFailover(parts, true, 0.2);
    const parsed = JSON.parse(rawJson);

    const remainingBalanceCents = await deduct5xBalance(uid, "MULTILINGUAL_VISION_EXTRACT", {
      mimeType,
      targetLanguage,
    });

    res.status(200).json({
      result: parsed,
      billing: {
        multiplier: `${PREMIUM_MULTIPLIER}x`,
        chargedCents: MULTILINGUAL_5X_COST_CENTS,
        remainingBalanceCents,
      },
    });
  } catch (err) {
    const msg = (err as Error).message || "Internal error";
    const status = msg.startsWith("UNAUTHORIZED")
      ? 401
      : msg === "INSUFFICIENT_BALANCE"
      ? 402
      : 500;
    res.status(status).json({ error: msg });
  }
});

/**
 * 2. Cross-Lingual & Native-Language Semantic Search (Japanese query -> Japanese/English receipt, etc.)
 * Charged at 5x rate from pre-loaded One-Time Balance.
 */
export const aiMultilingualSearch = onRequest({ region: "us-central1" }, async (req, res) => {
  applyCors(res);
  if (req.method === "OPTIONS") {
    res.status(204).send("");
    return;
  }
  if (req.method !== "POST") {
    res.status(405).json({ error: "Method not allowed" });
    return;
  }

  try {
    const uid = await verifyAuthUid(req);
    const currentBalance = await getWalletBalanceCents(uid);
    if (currentBalance < MULTILINGUAL_5X_COST_CENTS) {
      res.status(402).json({
        error: "INSUFFICIENT_BALANCE",
        message: "Insufficient One-Time Prepaid Balance for 5x Multilingual AI Search.",
        requiredCents: MULTILINGUAL_5X_COST_CENTS,
        currentBalanceCents: currentBalance,
        multiplier: `${PREMIUM_MULTIPLIER}x`,
      });
      return;
    }

    const { query, documents = [], uiLanguage = "English" } = req.body || {};
    if (!query || typeof query !== "string") {
      res.status(400).json({ error: "query is required" });
      return;
    }

    const compactDocs = (Array.isArray(documents) ? documents : []).slice(0, 40).map((d) => ({
      id: d.id,
      title: d.title,
      type: d.type || d.documentType,
      date: d.date || d.dateDetected,
      summary: d.summary || d.aiSummary,
      entities: d.entities || d.keyEntitiesJson,
      tags: d.tags || d.searchableTagsJson,
      ocr: String(d.ocr || d.rawOcrText || "").slice(0, 500),
    }));

    const prompt = `
You are the Global Multilingual Semantic Search AI for DocuSnap AI.
A user typed a search query in ANY language (e.g., Japanese, English, Korean, Arabic, Spanish, French, German, Chinese, Hindi, Urdu).
UI Language setting: ${uiLanguage}.

CRITICAL RULES:
1. Respond in the EXACT SAME language and script as the USER QUERY (for example, if the user writes in Japanese, answer in natural Japanese; if English, answer in English).
2. Find ALL matching documents from the vault list below — including cross-lingual matches (e.g., Japanese query matching Japanese receipt text OR English tags) and synonyms (medicines, bills, totals, dates, doctors).
3. Return ONLY valid JSON matching:
{
  "matchedIds": ["id1", "id2"],
  "answer": "Concise 1-3 sentence answer citing exact figures, medicine names, vendors, or dates in the query's language."
}

USER QUERY:
${query}

VAULT DOCUMENTS (${compactDocs.length}):
${JSON.stringify(compactDocs)}`;

    const rawJson = await callGeminiWithFailover([{ text: prompt }], true, 0.2);
    const parsed = JSON.parse(rawJson) as { matchedIds?: string[]; answer?: string };

    const remainingBalanceCents = await deduct5xBalance(uid, "MULTILINGUAL_SEARCH", {
      queryPreview: query.slice(0, 60),
      scannedCount: compactDocs.length,
    });

    res.status(200).json({
      matchedIds: Array.isArray(parsed.matchedIds) ? parsed.matchedIds : [],
      answer: parsed.answer || "",
      billing: {
        multiplier: `${PREMIUM_MULTIPLIER}x`,
        chargedCents: MULTILINGUAL_5X_COST_CENTS,
        remainingBalanceCents,
      },
    });
  } catch (err) {
    const msg = (err as Error).message || "Internal error";
    const status = msg.startsWith("UNAUTHORIZED")
      ? 401
      : msg === "INSUFFICIENT_BALANCE"
      ? 402
      : 500;
    res.status(status).json({ error: msg });
  }
});

/**
 * 3. Credits Verified One-Time Payment ($99 - $999 USD) into User's Prepaid AI Balance
 */
export const creditOneTimePurchase = onRequest({ region: "us-central1" }, async (req, res) => {
  applyCors(res);
  if (req.method === "OPTIONS") {
    res.status(204).send("");
    return;
  }
  if (req.method !== "POST") {
    res.status(405).json({ error: "Method not allowed" });
    return;
  }

  try {
    const uid = await verifyAuthUid(req);
    const { productId, purchaseToken } = req.body || {};

    if (!productId || !purchaseToken) {
      res.status(400).json({ error: "productId and purchaseToken are required" });
      return;
    }

    const skuUsd = VALID_ONE_TIME_SKUS[String(productId)];
    if (!skuUsd) {
      res.status(400).json({ error: "Invalid one-time SKU productId" });
      return;
    }

    const creditCents = skuUsd * 100;
    const safeTokenId = String(purchaseToken).replace(/[^a-zA-Z0-9_-]/g, "_").slice(0, 120);
    const purchaseRef = db.doc(`users/${uid}/verified_purchases/${safeTokenId}`);
    const walletRef = db.doc(`users/${uid}/wallet/main`);
    const txRef = db.collection(`users/${uid}/wallet_transactions`).doc();

    const result = await db.runTransaction(async (tx) => {
      const existingPurchase = await tx.get(purchaseRef);
      if (existingPurchase.exists) {
        const currentWallet = await tx.get(walletRef);
        return {
          alreadyCredited: true,
          balanceCents: Number(currentWallet.data()?.balanceCents ?? 0),
        };
      }

      const walletSnap = await tx.get(walletRef);
      const prevCents = Number(walletSnap.data()?.balanceCents ?? 0);
      const newBalanceCents = prevCents + creditCents;

      tx.set(purchaseRef, {
        productId,
        amountUsd: skuUsd,
        creditCents,
        verifiedAt: admin.firestore.FieldValue.serverTimestamp(),
      });

      tx.set(
        walletRef,
        {
          balanceCents: newBalanceCents,
          totalLoadedCents: admin.firestore.FieldValue.increment(creditCents),
          lifetimeAmountUsd: Math.max(Number(walletSnap.data()?.lifetimeAmountUsd ?? 0), skuUsd),
          updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        },
        { merge: true }
      );

      tx.set(txRef, {
        type: "CREDIT_ONE_TIME_LOAD",
        productId,
        amountUsd: skuUsd,
        chargedCents: creditCents,
        balanceAfterCents: newBalanceCents,
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
      });

      return { alreadyCredited: false, balanceCents: newBalanceCents };
    });

    res.status(200).json({
      success: true,
      ...result,
      ratePerCallCents: MULTILINGUAL_5X_COST_CENTS,
      multiplier: `${PREMIUM_MULTIPLIER}x`,
    });
  } catch (err) {
    const msg = (err as Error).message || "Internal error";
    res.status(msg.startsWith("UNAUTHORIZED") ? 401 : 500).json({ error: msg });
  }
});

/**
 * 4. Fetch User Prepaid AI Wallet Balance & 5x Rate Info
 */
export const getWalletStatus = onRequest({ region: "us-central1" }, async (req, res) => {
  applyCors(res);
  if (req.method === "OPTIONS") {
    res.status(204).send("");
    return;
  }

  try {
    const uid = await verifyAuthUid(req);
    const snap = await db.doc(`users/${uid}/wallet/main`).get();
    const data = snap.data() || {};
    const balanceCents = Number(data.balanceCents ?? 0);

    res.status(200).json({
      balanceCents,
      balanceUsd: (balanceCents / 100).toFixed(2),
      totalSpentCents: Number(data.totalSpentCents ?? 0),
      total5xCalls: Number(data.total5xCalls ?? 0),
      lifetimeAmountUsd: Number(data.lifetimeAmountUsd ?? 0),
      ratePerCallCents: MULTILINGUAL_5X_COST_CENTS,
      baseStandardCents: BASE_AI_COST_CENTS,
      multiplier: `${PREMIUM_MULTIPLIER}x`,
    });
  } catch (err) {
    const msg = (err as Error).message || "Internal error";
    res.status(msg.startsWith("UNAUTHORIZED") ? 401 : 500).json({ error: msg });
  }
});


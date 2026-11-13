# Google Play Store Billing Activation Guide — DocuSnap AI

Package Name: `com.docusnap.ai`

Until this app and its SKUs are uploaded and published to Google Play Console (Internal Testing or Production), DocuSnap AI automatically displays a **Pre-Launch Preview Notice** instead of faking purchases or charging users.

---

## Step-by-Step Checklist to Activate Real Play Store Billing

### 1. Create App & Upload Signed AAB to Google Play Console
1. Open **Google Play Console** (`https://play.google.com/console`).
2. Create App: **DocuSnap AI: Bill & Rx Finder** (`com.docusnap.ai`).
3. Generate a signed release `.aab` (Android App Bundle) in Android Studio:
   `Build -> Generate Signed Bundle / APK -> Android App Bundle`.
4. Upload this `.aab` to **Testing -> Internal testing** (recommended first so Play Console unlocks the Monetize / Billing menus!).
5. Make sure `com.android.vending.BILLING` permission is in `AndroidManifest.xml` (already configured!).

### 2. Configure Monthly Subscription Products (`Monetize -> Products -> Subscriptions`)
Create **4 Subscriptions** with exact Product IDs matching `BillingManager.kt`:

| Tier Name | Exact Product ID (`productId`) | Base Plan Price (USD) | Billing Period | Doc Quota |
| :--- | :--- | :--- | :--- | :--- |
| Silver Vault | `docusnap_sub_silver_299` | `$2.99` | Monthly (Auto-renew) | 2,000 docs |
| Gold Vault | `docusnap_sub_gold_999` | `$9.99` | Monthly (Auto-renew) | 5,000 docs |
| Platinum Master | `docusnap_sub_platinum_2999` | `$29.99` | Monthly (Auto-renew) | 10,000 docs |
| Infinity Diamond | `docusnap_sub_enterprise_4999` | `$49.99` | Monthly (Auto-renew) | 15,000 docs |

**Important:** Inside each Subscription, create an active **Base Plan** (Auto-renewing) and set status to **Active**.

### 3. Configure One-Time Lifetime VIP Products (`Monetize -> Products -> In-app products`)
Create **6 In-App One-Time Products** with exact Product IDs matching `OneTimeSku` in `BillingManager.kt`:

| Package Label | Exact Product ID (`productId`) | Price (USD) | Doc Quota |
| :--- | :--- | :--- | :--- |
| Patron Starter Lifetime | `docusnap_lifetime_99` | `$99.00` | 10,000 docs |
| Business Lifetime Pass | `docusnap_lifetime_199` | `$199.00` | 25,000 docs |
| Executive Lifetime Vault | `docusnap_lifetime_299` | `$299.00` | 40,000 docs |
| Clinic Pro Lifetime Suite | `docusnap_lifetime_499` | `$499.00` | 65,000 docs |
| Platinum Sponsor Lifetime | `docusnap_lifetime_749` | `$749.00` | 85,000 docs |
| Diamond Founders Lifetime | `docusnap_lifetime_999` | `$999.00` | 150,000 docs |

Set each In-App Product status to **Active**.

### 4. Link Payments Profile & Merchant Account
1. In Play Console sidebar go to **Setup -> Payments profile**.
2. Verify your Google Merchant Account, Tax Info, and Bank Payout details. Without a verified Merchant account, Play Console blocks paid SKU purchases.

### 5. Test Real Billing Free with License Testers
1. In Play Console go to **Setup -> License testing**.
2. Add your Gmail address (and any tester emails) to the **License testers** list.
3. Install the app from the **Internal Testing opt-in link** on Google Play.
4. Click Subscribe or One-Time Pay: Google Play's real native sheet will appear with **"Test card, always approves"** — allowing 100% real verification without charging real money!
5. Remove test emails or install from Production when ready for real customer charges.


# Publishing Arslan TV — what is done, what is left

## Done

- **Upload key created.** `upload-keystore.jks` with alias `arslantv`, and
  `keystore.properties` holding its password. Both are chmod 600 and
  gitignored. The password was generated randomly and written straight to the
  file — it is not in any chat log. Open `keystore.properties` to read it.
- **Signed bundle built.** `ArslanTV-release.aab` (4.7 MB), signed
  `CN=Arslan TV` rather than the Android debug key. Verify any time with:

  ```bash
  keytool -printcert -jarfile ArslanTV-release.aab | head -2
  ```

- **Store graphics** — icon, feature graphic, TV banner, 3 phone and 4 TV
  screenshots, all in `screenshots/` and the files beside this one.
- **Listing copy** — `LISTING.md`, built around the focus keyword.
- **Legal pages** — `docs/privacy.html`, `docs/terms.html` and
  `docs/disclaimer.html`, written to match what the app actually does and
  carrying the cookie, Google-advertising, GDPR and CCPA disclosures AdSense
  asks for. Pushed to GitHub, ready for Pages.
- **Tester list** — `testers.txt`, the 19 addresses for the closed track.
- **Pushed to GitHub** — `github.com/arslanh2011/arslantv`, main branch. The
  keystore and `local.properties` are gitignored and were verified absent
  from the remote; build outputs are excluded too.
- **GitHub Pages is live** (11 Nov 2026). Pages needs a public repo on a free
  plan, so `arslantv` was switched from private to public first. Audited
  before flipping: `upload-keystore.jks` and `keystore.properties` were never
  committed in any commit, and no key- or password-like strings appear in the
  110 tracked files. Source is `main` / `/docs`. Verified serving:

  - `https://arslanh2011.github.io/arslantv/privacy.html` → "Privacy Policy — Arslan TV"
  - `https://arslanh2011.github.io/arslantv/terms.html`
  - `https://arslanh2011.github.io/arslantv/disclaimer.html`

**Back up `upload-keystore.jks` somewhere off this machine.** With Play App
Signing a lost upload key can be reset by Google, but it is a support round
trip you do not want mid-release.

## BLOCKED — the developer account is closed

Play Console for `arslanh2011@gmail.com` shows one developer account,
**Inside Australia**, with Policy status **Account closed**:

- Closed: **7 Mar 2025, 9:54 PM** — "because it was not being used"
- Warning sent 30 Dec 2024, deadline 28 Feb 2025
- "The developer account registration fee is not refundable. To start
  publishing apps on Google Play, create a new account."

Nothing in steps 2–5 can proceed on this account. It needs a **new** Play
developer account: US$25 registration plus identity verification. Creating
an account and paying the fee are both things the account holder must do in
person — they cannot be automated or delegated.

Note for when the new account exists: personal developer accounts created
after Nov 2023 must run a closed test with **at least 12 testers for 14
continuous days** before production access unlocks. The 19 addresses in
`testers.txt` already clear that bar, so the closed track below is the
required path, not an optional one.

## Left to do once there is a live developer account

### 2. Create the app in Play Console
App name `Arslan TV: Pakistan Live TV`, language English, type App, Free.

### 3. Store listing
Paste short and full descriptions from `LISTING.md`. Upload
`play-icon-512.png`, `play-feature-graphic-1024x500.png`, the three phone
screenshots, and — if you tick the Android TV form factor — the four TV
screenshots and `play-tv-banner-1280x720.png`.

### 4. App content — the declarations
These are statements made under your account, which is why they are yours:
privacy policy URL, ads (the app serves none of its own), app access (no
login), content rating questionnaire, target audience, data safety, and the
government-app and financial-features questions.

For data safety the honest answers are: the app collects and shares **no**
data itself; it stores preferences only on the device; the embedded YouTube
player is a third-party component with its own collection.

### 5. Closed testing
Create a closed track, upload `ArslanTV-release.aab`, and add the 19 testers
from `testers.txt` (Play accepts a pasted list, one address per line). Roll
out. Closed-track review is much lighter than production.

### 6. Before production
Settle the content-rights question. The listing names broadcasters whose
live feeds the app carries, and that is the first thing a production reviewer
will weigh.

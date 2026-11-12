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
- **Legal pages are live.** Published as a Google Site (account
  arslanh2011@gmail.com) and verified serving:

  - `https://sites.google.com/view/arslan-tv/` — home
  - **`https://sites.google.com/view/arslan-tv/privacy-policy`** ← the URL Play asks for
  - `https://sites.google.com/view/arslan-tv/terms-of-service`
  - `https://sites.google.com/view/arslan-tv/disclaimer-and-dmca`

  Visibility is Anyone, and "request search engines not to display" is off,
  so the pages are reachable and indexable. Source copies are kept on disk at
  `store-assets/legal/`.
- **SEO.** The site is built around the focus keyword *Free Pakistani Live TV
  Channels*: it is the page `<title>`, the H1, the opening sentence and the
  auto-generated meta description, and the section headings are real heading
  tags rather than bold text. Verified in the HTML a crawler receives — the
  exact phrase appears 12 times across the home page, with an FAQ block for
  long-tail queries.
- **Tester list** — `testers.txt`, the 19 addresses for the closed track.
- **Pushed to GitHub** — `github.com/arslanh2011/arslantv`, main branch, code
  only. `docs/` was removed and gitignored, so the repo no longer carries a
  website. The keystore and `local.properties` are gitignored and were
  verified absent from the remote; build outputs are excluded too.

**Back up `upload-keystore.jks` somewhere off this machine.** With Play App
Signing a lost upload key can be reset by Google, but it is a support round
trip you do not want mid-release.

## The Play Console account

Two accounts have come up, so be clear which one you are in:

- **Inside Australia** — Policy status **Account closed** (7 Mar 2025, for
  non-use). Nothing can be published from it; the registration fee is not
  refundable.
- **DrewsPro** — personal account, ID `8779729005053619314`, the one your
  Console link points at. It is live and holds one app, *Sequence King Pro*,
  in Draft. **This is the account to use.**

One account-level item blocks publishing on DrewsPro:

> **Declare your trader status by February 2027 — Action required.** "If you
> do not declare your trader status, your developer profile and apps will be
> removed from Google Play globally."

That is a legal declaration about whether you trade as a business or an
individual, and it carries EU consumer-law obligations. It has to be answered
by you before an app can go out.

Note for when the new account exists: personal developer accounts created
after Nov 2023 must run a closed test with **at least 12 testers for 14
continuous days** before production access unlocks. The 19 addresses in
`testers.txt` already clear that bar, so the closed track below is the
required path, not an optional one.

## Left to do

### 1. Declare trader status
Account-level, blocks everything else.

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

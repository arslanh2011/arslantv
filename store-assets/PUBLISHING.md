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
- **Privacy policy** — `privacy-policy.html`, written to match what the app
  actually does. Replace `REPLACE_WITH_YOUR_EMAIL` before you host it.

**Back up `upload-keystore.jks` somewhere off this machine.** With Play App
Signing a lost upload key can be reset by Google, but it is a support round
trip you do not want mid-release.

## Left to do — all of it in the browser, none of it automatable

### 1. Host the privacy policy (5 minutes)
Play needs a live URL. Any static host works. The quickest free route:

1. Make a public GitHub repo, e.g. `arslan-tv-privacy`.
2. Upload `privacy-policy.html`, renamed `index.html`.
3. Settings → Pages → deploy from `main` / root.
4. The URL will be `https://<your-username>.github.io/arslan-tv-privacy/`.

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
Create a closed track, add your 20 testers' Gmail addresses, upload
`ArslanTV-release.aab`, and roll out. Closed-track review is much lighter
than production.

### 6. Before production
Settle the content-rights question. The listing names broadcasters whose
live feeds the app carries, and that is the first thing a production reviewer
will weigh.

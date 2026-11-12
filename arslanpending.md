# Arslan TV — pending manual tasks

Everything that can be automated is done. What is left are declarations made
under your name, which is why each one is yours rather than mine. Each step
below is written against the exact screens as they look today.

Console: https://play.google.com/console/u/1/developers/8779729005053619314/home
Account: **DrewsPro**, personal, ID `8779729005053619314`

---

## 1. Declare trader status  (blocks everything else)

**Where:** Console home → the "Declare your trader status" card → **Get
started** → **Declare status**.

**What it asks** — one radio pair, and nothing else until you answer it:

> Under relevant laws, a trader is any individual or organisation acting for
> purposes relating to their trade, business, craft or profession.
>
> ( ) This is a trader account
> ( ) This is a non-trader account

**How to decide.** The test is whether you are acting commercially, not
whether you have earned anything yet:

- You told me you intend to monetise Arslan TV. If you will run ads or sell
  anything through your apps, or you already do on your other listings, you
  are a **trader**.
- **Non-trader** fits only a genuine hobbyist who publishes free apps with no
  commercial purpose at all.

**What follows from "trader":** Google publishes your name, physical address,
phone number and email on your Play store listing, and you take on EU
consumer-law duties (withdrawal rights, warranty). That is the cost of the
correct answer, not a reason to pick the other one. Declaring non-trader
while trading is a misdeclaration to Google and risks the account.

I cannot answer this for you — it is a statement about your own commercial
status, and I have no way to know your tax or business position.

Deadline: **February 2027**. If it lapses, the profile and every app are
removed from Google Play globally.

---

## 2. Create the app

**Where:** Console home → **Create app** (top right).

Fill in:

| Field | Value |
| --- | --- |
| App name | `Arslan TV: Pakistan Live TV` |
| Package name | `com.arslan.tv` |
| Default language | English (or English (United Kingdom)) |
| App or game | **App** |
| Free or paid | **Free** |

Note on "Free or paid": once published as free you cannot switch it to paid.
If a paid tier is ever part of the plan, decide now.

Then two checkboxes under **Declarations**, both of which are affirmations
about the app:

- [ ] **Confirm that app meets the Developer Programme Policies**
- [ ] **Accept US export laws**

These are the reason I stop here. The first is a statement that the app
complies with Play policy — and the open question on this app is whether you
hold the rights to carry the broadcasters' live feeds it lists. That is the
one thing a reviewer will actually test, and I will not affirm it on your
behalf.

Then **Create app**.

---

## 3. Store listing

**Where:** the new app → Grow users → Store presence → **Main store listing**.

Copy from `store-assets/LISTING.md`:

- App name, short description (76/80 chars), full description.
- App icon: `store-assets/play-icon-512.png`
- Feature graphic: `store-assets/play-feature-graphic-1024x500.png`
- Phone screenshots: the three in `store-assets/screenshots/phone/`
- If you tick the Android TV form factor: the four in
  `store-assets/screenshots/tv/` plus `play-tv-banner-1280x720.png`

---

## 4. App content — the declarations

**Where:** the app → Policy and programmes → **App content**.

| Section | Answer |
| --- | --- |
| Privacy policy | `https://sites.google.com/view/arslan-tv/privacy-policy` |
| Ads | The app contains **no** ads of its own today |
| App access | All functionality available without an account |
| Content rating | Complete the questionnaire; the app is a video player with news, drama and children's cartoons |
| Target audience | 13+ is the honest answer — cartoons are present but it is not a children's app |
| News app | It carries live news channels; answer accordingly |
| Data safety | **Collects no data, shares no data.** Preferences are stored on-device only. Declare the embedded YouTube player as a third-party component with its own collection |
| Government app | No |
| Financial features | None |

These are legal declarations under your account, which is why they are not
mine to tick. The answers above are what the app actually does — I verified
each against the source.

---

## 5. Closed testing

**Where:** Test and release → Testing → **Closed testing** → create a track.

1. Upload `ArslanTV-release.aab` (4.7 MB, signed `CN=Arslan TV`).
2. Testers: paste the 19 addresses from `store-assets/testers.txt`,
   one per line.
3. Roll out.

A personal developer account created after Nov 2023 must run a closed test
with **at least 12 testers for 14 continuous days** before production
unlocks. 19 addresses clears that bar, so this track is required, not
optional.

---

## 6. Optional housekeeping

- **GitHub Pages is still serving** at `arslanh2011.github.io/arslantv/`
  even though `docs/` was removed from the repo — Pages keeps the last
  successful build. To kill it: repo → Settings → Pages → Source → **None**.
  I cannot do this; it needs API auth I do not hold.
- The repo `arslanh2011/arslantv` is **public**. Worth a thought given the
  content-rights question.
- Back up `upload-keystore.jks` off this machine.

---

## Before production (not before closed testing)

Settle the rights question. The listing names broadcasters whose live feeds
the app carries. Closed-track review is light; production review is where
that gets weighed, and it is the single thing most likely to cost the
account.

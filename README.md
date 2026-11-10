# SplitBill

An ad-free, offline group expense splitter for Android: track shared costs, split them
any way, and settle up with UPI. No account, no cloud, no tracking.

The popular expense splitters want an account, sync everything to their servers and,
in the free tiers, show ads or ration how many expenses you can add. SplitBill does the
same core job on one phone, free and open source, and asks for no permissions at all.

## Features

- **Groups** (a trip, a flat, the office lunch) with members: a name and, optionally, a
  UPI ID. Each group has a currency symbol, ₹ by default.
- **Expenses**: description, amount, date and category, paid by one member or several.
  Split **equally** among any subset, by **exact amounts**, by **shares** (1.5 shares
  allowed) or by **percentage**. The editor shows live whether the split adds up and
  what is left or over. Edit or delete any time.
- **Payments** ("Ravi paid Asha ₹500") recorded as their own entries.
- **Balances** per member and a **settle-up plan** with the fewest possible payments.
- **Pay with UPI**: when the receiver has a UPI ID, opens your UPI app with a
  `upi://pay?pa=…&pn=…&am=…&cu=INR&tn=…` link, then offers to record the payment.
- **Share** a group summary as WhatsApp-friendly text or as an image.
- **Send or export a group as a `.json` file** and import it on another phone. Imports
  merge by stable IDs, so re-importing an updated file updates the group in place.
  Opening the file from a file manager or chat app imports it too.
- Material You dynamic colour, dark theme, edge to edge.

## Permissions

None. The manifest declares no permissions, including **no `INTERNET`**, so the app
cannot send your data anywhere even by accident. Files move through Android's document
picker and share sheet, which need no permission. The manifest's `<queries>` entry for
`upi://` links is not a permission; it only lets Android 11+ show which installed apps
can take a UPI payment. See [PRIVACY.md](PRIVACY.md).

## Building

Requires the Android SDK (compileSdk 37). This machine has no system JDK; Android
Studio's bundled JBR works:

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug testDebugUnitTest   # app + unit tests
./gradlew assembleRelease                   # R8-minified release build
```

Release signing reads an optional, git-ignored `keystore.properties` at the repo root
(`storeFile`, `storePassword`, `keyAlias`, `keyPassword`). Without it the release APK
is built unsigned.

## How it works, and why

The calculations live in plain Kotlin in `app/src/main/java/com/crylo/splitbill/core`
with JVM unit tests in `app/src/test`. The Android layers (Room in `data/`, Compose in
`ui/`) only store and display what `core` computes.

- **Money is a `Long` count of paise, everywhere.** Floating point cannot represent
  0.10 exactly, and an app whose whole job is making totals add up cannot afford
  "₹0.01 left over". Percentages are stored as basis points (12.5% = 1250) and shares
  as hundredths, so every input is an exact integer too.
- **Remainders go to fixed places.** ₹100 split three ways is 33.34 + 33.33 + 33.33.
  Every split rounds each share down and hands the spare paise to the largest dropped
  fractions, ties to whoever comes first in the group's member order (the
  largest-remainder method). The same expense therefore gives the same shares on every
  phone, and each expense's shares always sum to its amount, which is why balances
  always net to zero. Both the inputs and the resulting shares are stored, so editing
  reopens what was typed and old balances never shift if the maths is refined later.
- **The settle-up plan really is minimal.** A set of people whose balances sum to zero
  can always settle in (people − 1) payments, so the minimum for a group is the number
  of people with a non-zero balance minus the most zero-sum subgroups they can be split
  into. Finding that split is NP-hard in general, but a subset DP over 2^n states is
  instant for n ≤ 16 people with a non-zero balance, which covers any real group.
  Larger cases fall back to the usual greedy match (biggest debtor pays biggest
  creditor), which never takes more than n − 1 payments but may not be minimal. Tests
  compare the plan with a brute-force optimum on thousands of random groups.
- **File export is the sync for v1.** Google Drive sync would need Google Play
  Services, which F-Droid builds cannot use. A file sent over WhatsApp, Bluetooth or a
  cable works everywhere. Every entity has a random UUID and an `updatedAt` time, so an
  import matches records by ID and keeps the newer edit of each. Deleted expenses,
  payments and members leave a small tombstone in the file, so a deletion reaches the
  other phone instead of the old copy coming back. An edit made after the deletion wins
  over it. A member is never removed while an expense or payment still refers to them.
  "Newer" depends on the phones' clocks, which are normally set automatically.
- **Members can only be removed while they have no expenses or payments.** Removing
  someone who is part of a split would silently change everyone else's balance.
- **UPI is offered only in ₹ groups**, because UPI moves only rupees. SplitBill cannot
  see whether a UPI payment succeeded, so it opens the "record payment" dialog for you
  to confirm instead of recording it automatically.
- **No "me".** The app does not know which member holds the phone, so balances read
  "Ravi owes ₹600" rather than "you owe". This keeps one phone usable as the group's
  ledger and keeps exported files identical for everyone.
- **Cloud backup is off** (see `data_extraction_rules.xml`): Android's automatic backup
  would upload the database to Google, which contradicts "no cloud". Phone-to-phone
  transfer during new-phone setup is still allowed.
- **Navigation is a small hand-written back stack** with a ViewModelStore per screen.
  Four screens and no deep links did not justify a navigation library.

### Group file format

```json
{
  "format": "splitbill-group",
  "version": 1,
  "exportedAt": 1762790400000,
  "group": {
    "id": "…",
    "name": "Goa trip",
    "currency": "₹",
    "createdAt": 0,
    "updatedAt": 0
  },
  "members": [
    {
      "id": "…",
      "groupId": "…",
      "name": "Asha",
      "upiId": "asha@ybl",
      "createdAt": 0,
      "updatedAt": 0
    }
  ],
  "expenses": [
    {
      "id": "…",
      "amount": 120000,
      "date": 20402,
      "payers": [{ "memberId": "…", "amount": 120000 }],
      "splitMode": "EQUAL",
      "splits": [{ "memberId": "…", "input": 1, "share": 60000 }],
      "…": "…"
    }
  ],
  "settlements": [
    { "id": "…", "fromId": "…", "toId": "…", "amount": 60000, "…": "…" }
  ],
  "deleted": [{ "id": "…", "groupId": "…", "deletedAt": 0 }]
}
```

Amounts are in paise and `date` is days since 1970-01-01. Unknown fields are ignored, so
a later version can add fields without breaking this one. A file with a higher
`version`, or one whose payers or shares don't add up to the amount, is rejected rather
than imported.

## License

[MIT](LICENSE) © 2026 Vivek Kumar Gupta

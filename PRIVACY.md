# Privacy

SplitBill has no account, no server and no network access. The app does not declare
Android's `INTERNET` permission, so the operating system will not let it connect to
anything.

## What is stored

Groups, member names, optional UPI IDs, expenses and payments, in a database inside the
app's private storage on your phone. Nothing else: no analytics, no crash reports, no
device identifiers, no ads.

Android cloud backup is switched off for this data. A direct phone-to-phone transfer
when you set up a new phone may copy it.

## What leaves the phone, and only when you ask

- **Share summary (text or image)** and **Send group file** go through Android's share
  sheet to the app you pick (WhatsApp, email, Bluetooth, ...). The group file contains
  the whole group: names, UPI IDs, expenses and payments.
- **Export to file** writes the same group file wherever you choose in the file picker.
- **Pay with UPI** opens the UPI app you pick with the receiver's UPI ID, name, the
  amount and a short note filled in. SplitBill does not see whether the payment went
  through; you confirm it yourself with "Mark as paid".

What happens to data after it reaches another app is governed by that app.

## Contact

vivek@ivy.homes

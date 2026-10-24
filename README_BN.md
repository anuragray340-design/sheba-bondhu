# সেবা বন্ধু (Sheba Bondhu) — Android Source Project

এই প্রজেক্টটি Kotlin, Jetpack Compose এবং Firebase-ভিত্তিক একটি Android সেবাদাতা ডিরেক্টরির প্রাথমিক সোর্স কোড। এখানে লগইন/নিবন্ধন, সেবাদাতা যোগ, ছবি আপলোড, অনুমোদিত ডিরেক্টরি, সার্চ, ফোন ডায়ালার, প্রোফাইল এবং কাস্টম-claim-ভিত্তিক অ্যাডমিন স্ক্রিন রয়েছে।

## আগে যা লাগবে

- Android Studio-এর সাম্প্রতিক স্থিতিশীল সংস্করণ
- Android SDK 36
- JDK 17
- একটি Firebase project
- Gradle sync ও build করার জন্য ইন্টারনেট

## Firebase সেটআপ

1. https://console.firebase.google.com/ খুলে একটি project তৈরি করুন।
2. Android app যোগ করুন; package/application ID অবশ্যই `com.sheba.bondhu`।
3. Firebase থেকে `google-services.json` ডাউনলোড করে `app/google-services.json` হিসেবে রাখুন। এই ফাইলটি এই ZIP-এ নেই, কারণ এটি আপনার Firebase project-নির্দিষ্ট।
4. Authentication > Sign-in method-এ Email/Password চালু করুন।
5. Firestore Database তৈরি করুন এবং `firestore.rules`-এর নিয়মগুলো প্রকাশ করুন।
6. Storage চালু করুন এবং `storage.rules` প্রকাশ করুন।
7. Android Studio-তে project খুলে Gradle sync করুন।

## অ্যাডমিন নিরাপদে সেট করা

সাধারণ ব্যবহারকারী অ্যাপ থেকে নিজেকে admin করতে পারবে না। আগে Firebase Authentication-এ অ্যাডমিনের ইমেইল দিয়ে একটি account তৈরি করুন। তারপর নিরাপদ প্রশাসনিক কম্পিউটার/সার্ভার পরিবেশে Firebase Admin SDK সেটআপ করে:

```bash
cd functions
npm install
# Application Default Credentials আগে নিরাপদভাবে configure করুন
node set-admin.mjs admin@example.com
```

`admin@example.com`-এর বদলে আপনার অ্যাডমিন ইমেইল দিন। Service-account key কখনো Android app, Git repository বা এই ZIP-এ রাখবেন না। Admin claim পাওয়ার পর অ্যাপ থেকে sign out করে আবার sign in করুন।

## চালানো

1. Android Studio-তে এই folder খুলুন।
2. `app/google-services.json` যোগ করুন।
3. Gradle sync করুন।
4. Emulator বা Android ফোন নির্বাচন করে Run চাপুন।

## APK তৈরি

Android Studio: **Build > Build Bundle(s) / APK(s) > Build APK(s)**।

সাধারণত debug APK পাওয়া যাবে:
`app/build/outputs/apk/debug/app-debug.apk`

Release-এর জন্য keystore, signing configuration, privacy policy, app testing এবং Play Console-এর বর্তমান নীতিমালা আলাদাভাবে সম্পন্ন করতে হবে।

## গুরুত্বপূর্ণ সীমাবদ্ধতা ও পরবর্তী কাজ

- এখানে APK build বা বাস্তব Firebase project-এ পরীক্ষা করা হয়নি; এই ZIP হলো সোর্স প্রজেক্ট, প্রস্তুত ইনস্টলযোগ্য APK নয়।
- Firebase console configuration ও `google-services.json` আপনাকেই যোগ করতে হবে।
- Admin claim দিতে trusted Firebase Admin SDK পরিবেশ দরকার।
- ছবি আপলোডের জন্য Storage billing/plan এবং Firebase-এর বর্তমান Storage requirements যাচাই করুন।
- ব্যবহারকারীর ফোন ও ঠিকানা ব্যক্তিগত তথ্য; সংগ্রহের আগে সম্মতি, privacy policy, data retention ও deletion flow যুক্ত করুন।
- production release-এর আগে Firestore/Storage rules Emulator Suite দিয়ে পরীক্ষা করুন।
- অ্যাপের বাংলা UI-তে আরও polish, form validation, pagination, accessibility, error-state handling ও automated tests যোগ করা উচিত।

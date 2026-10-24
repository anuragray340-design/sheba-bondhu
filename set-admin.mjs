// Run only in a trusted environment after configuring Firebase Admin credentials.
// Usage: node set-admin.mjs user@example.com
import { initializeApp, applicationDefault } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";

initializeApp({ credential: applicationDefault() });

const email = process.argv[2];
if (!email) {
  console.error("Usage: node set-admin.mjs user@example.com");
  process.exit(1);
}
const user = await getAuth().getUserByEmail(email);
await getAuth().setCustomUserClaims(user.uid, { admin: true });
console.log(`Admin claim assigned to ${email}. Sign out and sign in again in the app.`);

# CardFlow Android v2.0 — GitHub Actions source

## Build
1. Create a **private** GitHub repository.
2. Upload the **contents** of this folder, including `.github/workflows/build-apk.yml`.
3. Open Actions > Build CardFlow APK > Run workflow.
4. Download the `CardFlow-debug-apk` artifact from a successful run and extract `app-debug.apk`.
5. Sideload the APK on Android, grant SMS permissions, and add cards with last four digits.

## Implemented in this source
- Existing v1 card dashboard, transaction ledger, bank SMS receiver/parser and review queue.
- Manually entered bank statements, due dates and minimum due, with **estimated** oldest-first payment allocation.
- EMI schedule tracking (no duplicate ledger posting), spending charts, reward points and annual-fee/renewal records.
- Android notification on app opening for near-due statements (not a reliable scheduled background reminder).
- Optional system biometric gate on API 28+ when a device lock is configured.
- Android PDF document picker; user must enter statement amounts manually. **No PDF text extraction.**
- Local JSON backup/restore (export behavior in WebView can vary by device).

## Not yet implemented / limitations
- **Encrypted database is not implemented**. Financial records remain in WebView local storage; Android app sandbox protects access from ordinary apps but is not equivalent to encryption at rest.
- No scheduled reminder service while the app is closed.
- SMS recognition covers a subset of English bank formats; ambiguous/unmatched messages require review.
- Statement payment allocation is an estimate, not a bank-certified ledger; refunds, EMI conversion, interest and bank payment allocation can differ.
- SMS may be spoofed. Always compare figures with official bank statements.
- No PDF parsing, OCR, automated bank statement reconciliation or automatic reward earning.
- The debug APK is not signed for production or Google Play publication. SMS permission is restricted by Play policies.
- No internet permission and no cloud syncing. Back up your data regularly.
- **Not compiled or tested in this environment.** GitHub Actions will perform the actual build; report build logs if it fails.

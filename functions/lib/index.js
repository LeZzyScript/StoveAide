"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.sendOtpEmail = void 0;
const admin = require("firebase-admin");
const firestore_1 = require("firebase-functions/v2/firestore");
const v2_1 = require("firebase-functions/v2");
const params_1 = require("firebase-functions/params");
const nodemailer = require("nodemailer");
const logger = require("firebase-functions/logger");
admin.initializeApp();
// Set the region closest to the Firebase project (Asia)
(0, v2_1.setGlobalOptions)({ region: "asia-southeast1" });
// ---------------------------------------------------------------------------
// Firebase Secrets – set once via CLI before deploying:
//   firebase functions:secrets:set GMAIL_USER
//   firebase functions:secrets:set GMAIL_APP_PASSWORD
// ---------------------------------------------------------------------------
const GMAIL_USER = (0, params_1.defineSecret)("GMAIL_USER");
const GMAIL_APP_PASSWORD = (0, params_1.defineSecret)("GMAIL_APP_PASSWORD");
// ---------------------------------------------------------------------------
// Helper – create Nodemailer transporter with Gmail App Password
// ---------------------------------------------------------------------------
function createTransporter(gmailUser, gmailPass) {
    return nodemailer.createTransport({
        service: "gmail",
        auth: {
            user: gmailUser,
            pass: gmailPass,
        },
    });
}
// ---------------------------------------------------------------------------
// Helper – build styled HTML email with the 6-digit OTP
// ---------------------------------------------------------------------------
function buildOtpEmail(code, purpose) {
    const isReg = purpose === "REGISTRATION";
    const subject = isReg
        ? "StoveAide – Verify Your Email"
        : "StoveAide – Password Reset Code";
    const heading = isReg ? "Email Verification" : "Password Reset";
    const intro = isReg
        ? "You're almost there! Use the code below to verify your email and complete your StoveAide registration."
        : "We received a request to reset your StoveAide password. Enter the code below to continue.";
    const footerNote = isReg
        ? "If you didn't create a StoveAide account, you can safely ignore this email."
        : "If you didn't request a password reset, please ignore this email or contact support.";
    const year = new Date().getFullYear();
    const html = `
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
  <title>${subject}</title>
  <style>
    body { margin:0; padding:0; background:#F5F5F5; font-family:'Segoe UI',Arial,sans-serif; }
    .wrapper { max-width:520px; margin:40px auto; background:#FFFFFF; border-radius:16px;
               box-shadow:0 4px 24px rgba(0,0,0,.10); overflow:hidden; }
    .header  { background:linear-gradient(135deg,#1A1A2E 0%,#16213E 100%);
               padding:36px 32px 28px; text-align:center; }
    .logo    { display:inline-flex; align-items:center; gap:10px; }
    .logo-icon { width:44px; height:44px; background:#E8A020; border-radius:10px;
                 display:inline-flex; align-items:center; justify-content:center;
                 font-size:24px; }
    .logo-text { color:#FFF; font-size:22px; font-weight:700; letter-spacing:-.5px; }
    .logo-text span { color:#E8A020; }
    .body    { padding:36px 32px; }
    .heading { font-size:22px; font-weight:700; color:#1A1A2E; margin:0 0 12px; }
    .intro   { font-size:15px; color:#555; line-height:1.6; margin:0 0 28px; }
    .code-box { background:#F0F4FF; border:2px solid #C5D3F5; border-radius:12px;
                text-align:center; padding:24px 16px; margin-bottom:28px; }
    .code-label { font-size:12px; font-weight:600; color:#7E8FA6;
                  text-transform:uppercase; letter-spacing:1px; margin-bottom:10px; }
    .code-digits { font-size:44px; font-weight:800; letter-spacing:14px; color:#1A1A2E;
                   font-family:'Courier New',monospace; }
    .validity { font-size:13px; color:#7E8FA6; text-align:center; margin-bottom:24px; }
    .divider { height:1px; background:#EFEFEF; margin:24px 0; }
    .footer-note { font-size:13px; color:#9BA8B5; line-height:1.5; }
    .footer  { background:#F8F9FB; padding:20px 32px; text-align:center;
               font-size:12px; color:#B0B8C5; }
  </style>
</head>
<body>
  <div class="wrapper">
    <div class="header">
      <div class="logo">
        <div class="logo-icon">&#x1F525;</div>
        <div class="logo-text">Stove<span>Aide</span></div>
      </div>
    </div>
    <div class="body">
      <p class="heading">${heading}</p>
      <p class="intro">${intro}</p>
      <div class="code-box">
        <div class="code-label">Your 6-Digit Verification Code</div>
        <div class="code-digits">${code}</div>
      </div>
      <p class="validity">&#x23F1; This code is valid for <strong>10 minutes</strong>. Do not share it with anyone.</p>
      <div class="divider"></div>
      <p class="footer-note">${footerNote}</p>
    </div>
    <div class="footer">
      &copy; ${year} StoveAide &middot; Smart Kitchen Safety
    </div>
  </div>
</body>
</html>`;
    return { subject, html };
}
// ---------------------------------------------------------------------------
// Cloud Function (v2) – triggers on Firestore otp_verifications/{emailDocId}
// ---------------------------------------------------------------------------
exports.sendOtpEmail = (0, firestore_1.onDocumentWritten)({
    document: "otp_verifications/{emailDocId}",
    secrets: [GMAIL_USER, GMAIL_APP_PASSWORD],
}, async (event) => {
    var _a;
    // Ignore deletes – no email needed when OTP is cleared
    if (!((_a = event.data) === null || _a === void 0 ? void 0 : _a.after.exists)) {
        logger.info("OTP doc deleted – no email needed.");
        return;
    }
    const data = event.data.after.data();
    if (!(data === null || data === void 0 ? void 0 : data.email) || !(data === null || data === void 0 ? void 0 : data.code)) {
        logger.error("OTP document missing required fields.", data);
        return;
    }
    const gmailUser = GMAIL_USER.value();
    const gmailPass = GMAIL_APP_PASSWORD.value();
    if (!gmailUser || !gmailPass) {
        logger.error("Gmail secrets not set. Run:\n" +
            "  firebase functions:secrets:set GMAIL_USER\n" +
            "  firebase functions:secrets:set GMAIL_APP_PASSWORD");
        return;
    }
    const purpose = (data.purpose === "FORGOT_PASSWORD" ? "FORGOT_PASSWORD" : "REGISTRATION");
    const { subject, html } = buildOtpEmail(data.code, purpose);
    const transporter = createTransporter(gmailUser, gmailPass);
    try {
        await transporter.sendMail({
            from: `"StoveAide" <${gmailUser}>`,
            to: data.email,
            subject,
            html,
        });
        logger.info(`OTP email sent → ${data.email} | Purpose: ${purpose}`);
    }
    catch (err) {
        logger.error("Failed to send OTP email:", err);
    }
});
//# sourceMappingURL=index.js.map
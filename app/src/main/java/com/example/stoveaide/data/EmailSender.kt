package com.example.stoveaide.data

import android.util.Log
import com.example.stoveaide.BuildConfig
import java.util.Calendar
import java.util.Properties
import java.util.concurrent.Executors
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

/**
 * Utility for dispatching OTP verification and password reset emails
 * directly from the Android client via Gmail SMTP (JavaMail).
 *
 * Sender credentials are read securely from local.properties via BuildConfig:
 *   SMTP_EMAIL=your_email@gmail.com
 *   SMTP_PASSWORD=your_16_char_app_password
 */
object EmailSender {

    private const val TAG = "EmailSender"
    private val executor = Executors.newSingleThreadExecutor()

    fun sendOtpEmail(
        toEmail: String,
        code: String,
        purpose: String,
        onComplete: ((Boolean, String?) -> Unit)? = null
    ) {
        val senderEmail = BuildConfig.SMTP_EMAIL.trim()
        val senderPassword = BuildConfig.SMTP_PASSWORD.trim()

        if (senderEmail.isEmpty() || senderPassword.isEmpty()) {
            val errorMsg = "SMTP credentials not configured. Please add SMTP_EMAIL and SMTP_PASSWORD to local.properties."
            Log.w(TAG, errorMsg)
            onComplete?.invoke(false, errorMsg)
            return
        }

        executor.execute {
            try {
                val isRegistration = purpose == OtpManager.PURPOSE_REGISTRATION
                val subject = if (isRegistration) {
                    "StoveAide – Verify Your Email"
                } else {
                    "StoveAide – Password Reset Code"
                }

                val htmlBody = buildHtmlTemplate(code, isRegistration)

                 val props = Properties().apply {
                     put("mail.smtp.host", "smtp.gmail.com")
                     put("mail.smtp.socketFactory.port", "465")
                     put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
                     put("mail.smtp.auth", "true")
                     put("mail.smtp.port", "465")
                     put("mail.smtp.ssl.enable", "true")
                     put("mail.smtp.connectiontimeout", "30000")
                     put("mail.smtp.timeout", "30000")
                 }

                val session = Session.getInstance(props, object : Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication {
                        return PasswordAuthentication(senderEmail, senderPassword)
                    }
                })

                val message = MimeMessage(session).apply {
                    setFrom(InternetAddress(senderEmail, "StoveAide"))
                    setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail))
                    setSubject(subject)
                    setContent(htmlBody, "text/html; charset=utf-8")
                }

                Transport.send(message)
                Log.d(TAG, "OTP email successfully sent to $toEmail (Purpose: $purpose)")
                onComplete?.invoke(true, null)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send OTP email to $toEmail: ${e.message}", e)
                onComplete?.invoke(false, e.localizedMessage)
            }
        }
    }

    private fun buildHtmlTemplate(code: String, isRegistration: Boolean): String {
        val heading = if (isRegistration) "Email Verification" else "Password Reset"
        val intro = if (isRegistration) {
            "You're almost there! Use the code below to verify your email and complete your StoveAide registration."
        } else {
            "We received a request to reset your StoveAide password. Enter the code below to continue."
        }
        val footerNote = if (isRegistration) {
            "If you didn't create a StoveAide account, you can safely ignore this email."
        } else {
            "If you didn't request a password reset, please ignore this email or contact support."
        }
        val year = Calendar.getInstance().get(Calendar.YEAR)

        return """
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
  <title>StoveAide Verification Code</title>
  <style>
    body { margin:0; padding:0; background:#F5F5F5; font-family:'Segoe UI',Arial,sans-serif; }
    .wrapper { max-width:520px; margin:40px auto; background:#FFFFFF; border-radius:16px;
               box-shadow:0 4px 24px rgba(0,0,0,.10); overflow:hidden; }
    .header  { background:linear-gradient(135deg,#1A1A2E 0%,#16213E 100%);
               padding:36px 32px 28px; text-align:center; }
    .logo-icon { display:inline-block; font-size:32px; margin-bottom:8px; }
    .logo-text { color:#FFF; font-size:24px; font-weight:700; letter-spacing:-.5px; }
    .logo-text span { color:#E8A020; }
    .body    { padding:36px 32px; }
    .heading { font-size:22px; font-weight:700; color:#1A1A2E; margin:0 0 12px; }
    .intro   { font-size:15px; color:#555; line-height:1.6; margin:0 0 28px; }
    .code-box { background:#F0F4FF; border:2px solid #C5D3F5; border-radius:12px;
                text-align:center; padding:24px 16px; margin-bottom:28px; }
    .code-label { font-size:12px; font-weight:600; color:#7E8FA6;
                  text-transform:uppercase; letter-spacing:1px; margin-bottom:10px; }
    .code-digits { font-size:42px; font-weight:800; letter-spacing:12px; color:#1A1A2E;
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
      <div class="logo-icon">&#x1F525;</div>
      <div class="logo-text">Stove<span>Aide</span></div>
    </div>
    <div class="body">
      <p class="heading">$heading</p>
      <p class="intro">$intro</p>
      <div class="code-box">
        <div class="code-label">Your 6-Digit Verification Code</div>
        <div class="code-digits">$code</div>
      </div>
      <p class="validity">&#x23F1; This code is valid for <strong>10 minutes</strong>. Do not share it with anyone.</p>
      <div class="divider"></div>
      <p class="footer-note">$footerNote</p>
    </div>
    <div class="footer">
      &copy; $year StoveAide &middot; Smart Kitchen Safety
    </div>
  </div>
</body>
</html>
        """.trimIndent()
    }
}

package com.devquest.core.domain

import jakarta.mail.internet.MimeMessage
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service

@Service
class MailService(
    private val mailSender: JavaMailSender,
    @Value("\${devquest.mail.from}") private val from: String,
    @Value("\${devquest.mail.enabled}") private val enabled: Boolean,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun sendDailyTechInterview(to: String, question: String, deepLink: String): Boolean {
        if (!enabled) {
            log.info("메일 발송 skip (MAIL_ENABLED=false): to=$to, type=tech-interview")
            return false
        }
        val html = buildTechInterviewHtml(question, deepLink)
        sendHtml(to, "[DevQuest] 오늘의 기술 면접 질문이 도착했어요! 🎯", html)
        log.info("기술 면접 메일 발송 완료: to=$to")
        return true
    }

    fun sendDailyCodingProblem(to: String, problemTitle: String, description: String, deepLink: String) {
        if (!enabled) {
            log.info("메일 발송 skip (MAIL_ENABLED=false): to=$to, type=coding-problem")
            return
        }
        val html = buildCodingProblemHtml(problemTitle, description, deepLink)
        sendHtml(to, "[DevQuest] 오늘의 코딩 문제가 도착했어요! 💻", html)
        log.info("코딩 문제 메일 발송 완료: to=$to")
    }

    private fun sendHtml(to: String, subject: String, html: String) {
        val message: MimeMessage = mailSender.createMimeMessage()
        MimeMessageHelper(message, false, "UTF-8").apply {
            setFrom(from)
            setTo(to)
            setSubject(subject)
            setText(html, true)
        }
        mailSender.send(message)
    }

    private fun buildTechInterviewHtml(question: String, deepLink: String): String {
        val questionHtml = question.replace("\n", "<br>")
        return """
            <!DOCTYPE html>
            <html lang="ko">
            <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0"></head>
            <body style="margin:0;padding:0;background:#f4f6fb;font-family:'Apple SD Gothic Neo',Arial,sans-serif;">
              <table width="100%" cellpadding="0" cellspacing="0" style="background:#f4f6fb;padding:40px 0;">
                <tr><td align="center">
                  <table width="600" cellpadding="0" cellspacing="0" style="max-width:600px;background:#fff;border-radius:16px;overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,0.08);">
                    <tr>
                      <td style="background:linear-gradient(135deg,#667eea 0%,#764ba2 100%);padding:36px 40px;text-align:center;">
                        <div style="font-size:28px;font-weight:700;color:#fff;letter-spacing:-0.5px;">⚔️ DevQuest</div>
                        <div style="font-size:14px;color:rgba(255,255,255,0.85);margin-top:6px;">이직 준비 RPG — 오늘의 기술 면접</div>
                      </td>
                    </tr>
                    <tr>
                      <td style="padding:32px 40px 0;">
                        <div style="font-size:13px;font-weight:600;color:#667eea;text-transform:uppercase;letter-spacing:1px;">Daily Challenge</div>
                        <div style="font-size:22px;font-weight:700;color:#1a1a2e;margin-top:8px;line-height:1.4;">오늘의 기술 면접 질문</div>
                      </td>
                    </tr>
                    <tr>
                      <td style="padding:20px 40px;">
                        <div style="background:#f8f7ff;border-left:4px solid #667eea;border-radius:0 12px 12px 0;padding:24px 28px;">
                          <div style="font-size:15px;line-height:1.8;color:#2d2d4e;">${questionHtml}</div>
                        </div>
                      </td>
                    </tr>
                    <tr>
                      <td style="padding:0 40px 24px;">
                        <div style="background:#fffbeb;border:1px solid #fde68a;border-radius:10px;padding:16px 20px;">
                          <div style="font-size:13px;color:#92400e;">
                            💡 <strong>답변 방법</strong>: 아래 버튼을 눌러 사이트에서 직접 답변을 입력하고 AI 피드백을 받아보세요!
                          </div>
                        </div>
                      </td>
                    </tr>
                    <tr>
                      <td style="padding:0 40px 40px;text-align:center;">
                        <a href="${deepLink}" style="display:inline-block;background:linear-gradient(135deg,#667eea 0%,#764ba2 100%);color:#fff;text-decoration:none;font-size:16px;font-weight:600;padding:14px 40px;border-radius:50px;letter-spacing:0.3px;">답변하러 가기 →</a>
                      </td>
                    </tr>
                    <tr>
                      <td style="background:#f8f9fc;padding:20px 40px;text-align:center;border-top:1px solid #eee;">
                        <div style="font-size:12px;color:#9ca3af;">매일 오전 9시, 새로운 질문이 기다립니다 🚀</div>
                        <div style="font-size:12px;color:#9ca3af;margin-top:4px;">© 2025 DevQuest — quest.dhbang.co.kr</div>
                      </td>
                    </tr>
                  </table>
                </td></tr>
              </table>
            </body>
            </html>
        """.trimIndent()
    }

    private fun buildCodingProblemHtml(problemTitle: String, description: String, deepLink: String): String {
        val descHtml = description.replace("\n", "<br>")
        return """
            <!DOCTYPE html>
            <html lang="ko">
            <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0"></head>
            <body style="margin:0;padding:0;background:#f4f6fb;font-family:'Apple SD Gothic Neo',Arial,sans-serif;">
              <table width="100%" cellpadding="0" cellspacing="0" style="background:#f4f6fb;padding:40px 0;">
                <tr><td align="center">
                  <table width="600" cellpadding="0" cellspacing="0" style="max-width:600px;background:#fff;border-radius:16px;overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,0.08);">
                    <tr>
                      <td style="background:linear-gradient(135deg,#11998e 0%,#38ef7d 100%);padding:36px 40px;text-align:center;">
                        <div style="font-size:28px;font-weight:700;color:#fff;letter-spacing:-0.5px;">⚔️ DevQuest</div>
                        <div style="font-size:14px;color:rgba(255,255,255,0.85);margin-top:6px;">이직 준비 RPG — 오늘의 코딩 문제</div>
                      </td>
                    </tr>
                    <tr>
                      <td style="padding:32px 40px 0;">
                        <div style="font-size:13px;font-weight:600;color:#11998e;text-transform:uppercase;letter-spacing:1px;">Coding Challenge</div>
                        <div style="font-size:22px;font-weight:700;color:#1a1a2e;margin-top:8px;line-height:1.4;">${problemTitle}</div>
                      </td>
                    </tr>
                    <tr>
                      <td style="padding:20px 40px;">
                        <div style="background:#f0fdf4;border-left:4px solid #11998e;border-radius:0 12px 12px 0;padding:24px 28px;">
                          <div style="font-size:15px;line-height:1.8;color:#2d2d4e;">${descHtml}</div>
                        </div>
                      </td>
                    </tr>
                    <tr>
                      <td style="padding:0 40px 24px;">
                        <div style="background:#fffbeb;border:1px solid #fde68a;border-radius:10px;padding:16px 20px;">
                          <div style="font-size:13px;color:#92400e;">
                            💡 <strong>풀이 방법</strong>: 아래 버튼을 눌러 사이트에서 코드를 작성하고 제출해보세요!
                          </div>
                        </div>
                      </td>
                    </tr>
                    <tr>
                      <td style="padding:0 40px 40px;text-align:center;">
                        <a href="${deepLink}" style="display:inline-block;background:linear-gradient(135deg,#11998e 0%,#38ef7d 100%);color:#fff;text-decoration:none;font-size:16px;font-weight:600;padding:14px 40px;border-radius:50px;letter-spacing:0.3px;">풀러 가기 →</a>
                      </td>
                    </tr>
                    <tr>
                      <td style="background:#f8f9fc;padding:20px 40px;text-align:center;border-top:1px solid #eee;">
                        <div style="font-size:12px;color:#9ca3af;">매일 오전 9시, 새로운 문제가 기다립니다 🚀</div>
                        <div style="font-size:12px;color:#9ca3af;margin-top:4px;">© 2025 DevQuest — quest.dhbang.co.kr</div>
                      </td>
                    </tr>
                  </table>
                </td></tr>
              </table>
            </body>
            </html>
        """.trimIndent()
    }
}

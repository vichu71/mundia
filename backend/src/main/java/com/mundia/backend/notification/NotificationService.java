package com.mundia.backend.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.List;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final JavaMailSender mailSender;

    @Value("${mundia.mail.from}")
    private String from;

    @Value("${mundia.mail.app-url}")
    private String appUrl;

    @Value("${mundia.mail.enabled:false}")
    private boolean enabled;

    public NotificationService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public record MatchReminder(String home, String homeFl, String away, String awayFl, String kickoff, String cutoff) {}

    /**
     * Notifies a user they've been added to a pool.
     * If initialPassword != null (newly created user), includes login instructions.
     */
    public void sendPoolInvite(String toEmail, String toName, String poolName, String initialPassword) {
        if (!enabled) {
            log.info("Mail disabled — skipping pool invite to {}", toEmail);
            return;
        }
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setFrom(from, "Mundia ⚽");
            helper.setTo(toEmail);
            helper.setSubject("⚽ Te han añadido a la porra \"" + poolName + "\"");
            helper.setText(buildPoolInviteHtml(toName, poolName, toEmail, initialPassword), true);
            mailSender.send(msg);
            log.info("Pool invite sent to {} for pool {}", toEmail, poolName);
        } catch (MessagingException | java.io.UnsupportedEncodingException e) {
            log.error("Failed to send pool invite to {}: {}", toEmail, e.getMessage());
        }
    }

    private String buildPoolInviteHtml(String name, String poolName, String email, String initialPassword) {
        String loginBlock = initialPassword == null ? """
                <p style="margin:0 0 20px;color:#a5b4fc;line-height:1.6;">
                  Entra con tu cuenta de siempre para empezar a predecir.
                </p>
                """ : String.format("""
                <div style="background:rgba(96,165,250,0.08);border:1px solid rgba(96,165,250,0.2);border-radius:12px;padding:16px;margin-bottom:24px;">
                  <p style="margin:0 0 8px;font-size:0.85rem;font-weight:800;color:#eef2ff;">Tus datos de acceso</p>
                  <p style="margin:0;font-size:0.85rem;color:#6b7db3;line-height:1.8;">
                    Email: <strong style="color:#eef2ff;">%s</strong><br>
                    Contraseña inicial: <strong style="color:#60a5fa;">%s</strong>
                  </p>
                  <p style="margin:8px 0 0;font-size:0.72rem;color:#3d4f7c;">Te recomendamos cambiarla al entrar.</p>
                </div>
                """, email, initialPassword);

        return String.format("""
            <!DOCTYPE html>
            <html>
            <body style="background:#07090f;color:#eef2ff;font-family:Inter,sans-serif;margin:0;padding:20px;">
              <div style="max-width:480px;margin:0 auto;background:#0d1220;border:1px solid rgba(96,165,250,0.25);border-radius:18px;padding:32px;">
                <div style="font-size:3rem;text-align:center;margin-bottom:8px;">⚽</div>
                <h1 style="font-size:1.5rem;font-weight:900;text-align:center;margin:0 0 4px;
                            background:linear-gradient(135deg,#fff 30%%,#60a5fa 100%%);
                            -webkit-background-clip:text;-webkit-text-fill-color:transparent;">¡Estás dentro!</h1>
                <p style="text-align:center;color:#6b7db3;margin:0 0 24px;font-size:0.85rem;">Porra familiar · Mundial 2026</p>

                <p style="margin:0 0 12px;">Hola <strong>%s</strong>, 👋</p>
                <p style="margin:0 0 20px;color:#a5b4fc;line-height:1.6;">
                  El administrador te ha añadido a la porra <strong style="color:#eef2ff;">%s</strong>. Ya puedes hacer tus predicciones para el Mundial 2026.
                </p>

                %s

                <div style="text-align:center;">
                  <a href="%s"
                     style="background:linear-gradient(135deg,#60a5fa,#818cf8);color:#fff;
                            text-decoration:none;padding:14px 32px;border-radius:10px;
                            font-weight:800;font-size:0.95rem;display:inline-block;">
                    👉 Entrar a la porra
                  </a>
                </div>

                <p style="margin-top:28px;color:#3d4f7c;font-size:0.75rem;text-align:center;">
                  ¡Que gane el mejor! 🏆
                </p>
              </div>
            </body>
            </html>
            """, name, poolName, loginBlock, appUrl);
    }

    public void sendWelcome(String toEmail, String toName) {
        if (!enabled) {
            log.info("Mail disabled — skipping welcome to {}", toEmail);
            return;
        }
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setFrom(from, "Mundia ⚽");
            helper.setTo(toEmail);
            helper.setSubject("⚽ Bienvenido a Mundia, " + toName + "!");
            helper.setText(buildWelcomeHtml(toName), true);
            mailSender.send(msg);
            log.info("Welcome sent to {}", toEmail);
        } catch (MessagingException | java.io.UnsupportedEncodingException e) {
            log.error("Failed to send welcome to {}: {}", toEmail, e.getMessage());
        }
    }

    private String buildWelcomeHtml(String name) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <body style="background:#07090f;color:#eef2ff;font-family:Inter,sans-serif;margin:0;padding:20px;">
              <div style="max-width:480px;margin:0 auto;background:#0d1220;border:1px solid rgba(96,165,250,0.25);border-radius:18px;padding:32px;">
                <div style="font-size:3rem;text-align:center;margin-bottom:8px;">⚽</div>
                <h1 style="font-size:1.6rem;font-weight:900;text-align:center;margin:0 0 4px;
                            background:linear-gradient(135deg,#fff 30%%,#60a5fa 100%%);
                            -webkit-background-clip:text;-webkit-text-fill-color:transparent;">¡Bienvenido a Mundia!</h1>
                <p style="text-align:center;color:#6b7db3;margin:0 0 28px;font-size:0.85rem;">Porra familiar · Mundial 2026</p>

                <p style="margin:0 0 12px;">Hola <strong>%s</strong>, 👋</p>
                <p style="margin:0 0 20px;color:#a5b4fc;line-height:1.6;">
                  Ya eres parte de la porra. Ahora ve a predecir los resultados de los partidos antes de que empiecen — <strong>las predicciones cierran 60 minutos antes de cada partido</strong>.
                </p>

                <div style="background:rgba(96,165,250,0.08);border:1px solid rgba(96,165,250,0.2);border-radius:12px;padding:16px;margin-bottom:24px;">
                  <p style="margin:0 0 8px;font-size:0.85rem;font-weight:800;color:#eef2ff;">¿Cómo puntúas?</p>
                  <p style="margin:0;font-size:0.8rem;color:#6b7db3;line-height:1.8;">
                    +2 pts · Aciertas el ganador o empate<br>
                    +2 pts · Aciertas el resultado exacto<br>
                    +1 pt &nbsp;· Aciertas los goles del local<br>
                    +1 pt &nbsp;· Aciertas los goles del visitante<br>
                    <strong style="color:#60a5fa;">Máximo 6 pts por partido</strong>
                  </p>
                </div>

                <div style="text-align:center;">
                  <a href="%s"
                     style="background:linear-gradient(135deg,#60a5fa,#818cf8);color:#fff;
                            text-decoration:none;padding:14px 32px;border-radius:10px;
                            font-weight:800;font-size:0.95rem;display:inline-block;">
                    👉 Empezar a predecir
                  </a>
                </div>

                <p style="margin-top:28px;color:#3d4f7c;font-size:0.75rem;text-align:center;">
                  ¡Buena suerte y que gane el mejor! 🏆
                </p>
              </div>
            </body>
            </html>
            """, name, appUrl);
    }

    public void sendMatchReminder(String toEmail, String toName, List<MatchReminder> matches) {
        if (!enabled) {
            log.info("Mail disabled — skipping reminder to {}", toEmail);
            return;
        }
        if (matches.isEmpty()) return;
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setFrom(from, "Mundia ⚽");
            helper.setTo(toEmail);
            helper.setSubject("⚽ Mundia — Tienes partidos sin predecir hoy");
            helper.setText(buildHtml(toName, matches), true);
            mailSender.send(msg);
            log.info("Reminder sent to {}", toEmail);
        } catch (MessagingException | java.io.UnsupportedEncodingException e) {
            log.error("Failed to send reminder to {}: {}", toEmail, e.getMessage());
        }
    }

    private String buildHtml(String name, List<MatchReminder> matches) {
        StringBuilder rows = new StringBuilder();
        for (MatchReminder m : matches) {
            rows.append(String.format("""
                <tr>
                  <td style="padding:10px 0;border-bottom:1px solid #1e2640;">
                    <span style="font-size:1rem;font-weight:800;color:#eef2ff;">%s vs %s</span><br>
                    <span style="font-size:0.8rem;color:#6b7db3;">%s &nbsp;·&nbsp; cierra a las %s</span>
                  </td>
                </tr>
                """, m.home(), m.away(), m.kickoff(), m.cutoff()));
        }

        return String.format("""
            <!DOCTYPE html>
            <html>
            <body style="background:#07090f;color:#eef2ff;font-family:Inter,sans-serif;margin:0;padding:20px;">
              <div style="max-width:480px;margin:0 auto;background:#0d1220;border:1px solid rgba(96,165,250,0.25);border-radius:18px;padding:32px;">
                <div style="font-size:2.5rem;text-align:center;margin-bottom:8px;">⚽</div>
                <h1 style="font-size:1.6rem;font-weight:900;text-align:center;margin:0 0 4px;
                            background:linear-gradient(135deg,#fff 30%%,#60a5fa 100%%);
                            -webkit-background-clip:text;-webkit-text-fill-color:transparent;">Mundia</h1>
                <p style="text-align:center;color:#6b7db3;margin:0 0 24px;font-size:0.85rem;">Porra familiar · Mundial 2026</p>

                <p style="margin:0 0 16px;">Hola <strong>%s</strong>,</p>
                <p style="margin:0 0 20px;color:#a5b4fc;">
                  Hoy juegan estos partidos y aún no has predicho:
                </p>

                <table style="width:100%%;border-collapse:collapse;">%s</table>

                <div style="text-align:center;margin-top:28px;">
                  <a href="%s"
                     style="background:linear-gradient(135deg,#60a5fa,#818cf8);color:#fff;
                            text-decoration:none;padding:14px 32px;border-radius:10px;
                            font-weight:800;font-size:0.95rem;display:inline-block;">
                    👉 Ir a predecir
                  </a>
                </div>

                <p style="margin-top:28px;color:#3d4f7c;font-size:0.75rem;text-align:center;">
                  Recibiste este aviso porque tienes partidos sin predecir en Mundia.<br>
                  Las predicciones cierran <strong>60 minutos</strong> antes de cada partido.
                </p>
              </div>
            </body>
            </html>
            """, name, rows.toString(), appUrl);
    }
}

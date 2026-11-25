package com.englishlab.service;

import java.time.Duration;
import org.springframework.web.util.HtmlUtils;

/** Contenido (texto y HTML) del correo de verificación. El nombre del usuario se escapa siempre. */
final class EmailVerificationTemplate {

    static final String SUBJECT = "Verifica tu correo en English Lab";

    private EmailVerificationTemplate() {
    }

    static String text(String name, String link, Duration ttl) {
        return "Hola " + name + ",\n\n"
                + "Gracias por registrarte en English Lab. Para activar tu cuenta, abre este enlace:\n\n"
                + link + "\n\n"
                + "El enlace caduca en " + humanTtl(ttl) + " y solo puede usarse una vez.\n\n"
                + "Si no creaste esta cuenta, ignora este mensaje.\n\n"
                + "— English Lab";
    }

    static String html(String name, String link, Duration ttl) {
        String safeName = HtmlUtils.htmlEscape(name);
        String safeLink = HtmlUtils.htmlEscape(link);
        return """
                <!doctype html>
                <html lang="es">
                <body style="margin:0;padding:0;background:#d0c9b2;font-family:Arial,Helvetica,sans-serif;color:#504c40;">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#d0c9b2;padding:32px 12px;">
                    <tr><td align="center">
                      <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:520px;background:#fffcf6;border:1px solid #c4bda8;">
                        <tr><td style="padding:28px 32px 8px;font-size:12px;letter-spacing:0.2em;color:#858071;">ENGLISH LAB</td></tr>
                        <tr><td style="padding:0 32px;font-family:Georgia,serif;font-size:26px;line-height:1.25;">Verifica tu correo</td></tr>
                        <tr><td style="padding:16px 32px 0;font-size:15px;line-height:1.6;">
                          Hola %s,<br><br>
                          Gracias por registrarte. Pulsa el botón para activar tu cuenta y empezar a practicar inglés.
                        </td></tr>
                        <tr><td style="padding:24px 32px;">
                          <a href="%s" style="display:inline-block;background:#504c40;color:#fffcf6;text-decoration:none;padding:13px 26px;font-size:15px;font-weight:bold;">Verificar mi cuenta</a>
                        </td></tr>
                        <tr><td style="padding:0 32px 8px;font-size:13px;line-height:1.6;color:#858071;">
                          El enlace caduca en %s y solo puede usarse una vez.<br>
                          Si el botón no funciona, copia y pega esta dirección en tu navegador:<br>
                          <span style="word-break:break-all;color:#a64c40;">%s</span>
                        </td></tr>
                        <tr><td style="padding:16px 32px 28px;font-size:12px;line-height:1.6;color:#858071;">
                          Si no creaste esta cuenta, puedes ignorar este mensaje con tranquilidad.
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(safeName, safeLink, humanTtl(ttl), safeLink);
    }

    private static String humanTtl(Duration ttl) {
        long hours = ttl.toHours();
        if (hours >= 1) {
            return hours == 1 ? "1 hora" : hours + " horas";
        }
        long minutes = Math.max(1, ttl.toMinutes());
        return minutes == 1 ? "1 minuto" : minutes + " minutos";
    }
}

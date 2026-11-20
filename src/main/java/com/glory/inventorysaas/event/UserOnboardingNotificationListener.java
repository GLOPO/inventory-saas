
package com.glory.inventorysaas.event;

import com.glory.inventorysaas.service.PasswordSetupLinkService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.HtmlUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserOnboardingNotificationListener {

    @Value("${spring.mail.username}")
    private String fromEmail;

    private final JavaMailSender mailSender;
    private final PasswordSetupLinkService passwordSetupLinkService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUserProvisioned(UserProvisionedEvent event) {

        try {
            String setupLink =
                    passwordSetupLinkService.generateSetupLink(event.userId());

            MimeMessage message = mailSender.createMimeMessage();

            MimeMessageHelper helper =
                    new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(event.email());
            helper.setSubject("Welcome to " + event.companyName());

            String emailContent = """
                    <h2>Welcome aboard!</h2>

                    <p>Your account has been created successfully.</p>

                    <h3>Account details</h3>

                    <p><strong>Company:</strong> %s</p>
                    <p><strong>Warehouse:</strong> %s</p>
                    <p><strong>Role:</strong> %s</p>
                    <p><strong>Email:</strong> %s</p>

                    <p>Before accessing your dashboard, you must set your password.</p>

                    <p>
                        <a href="%s">Set Your Password</a>
                    </p>

                    <p>This link expires in 30 minutes and can only be used once.</p>

                    <p>If you did not expect this account, please contact your administrator.</p>
                    """.formatted(
                    HtmlUtils.htmlEscape(event.companyName()),
                    HtmlUtils.htmlEscape(event.warehouseName()),
                    HtmlUtils.htmlEscape(event.roleName()),
                    HtmlUtils.htmlEscape(event.email()),
                    HtmlUtils.htmlEscape(setupLink)
            );

            helper.setText(emailContent, true);

            mailSender.send(message);

            log.info("Onboarding email sent successfully to: {}", event.email());

        } catch (MessagingException | RuntimeException ex) {
            log.error(
                    "Failed to send onboarding email to: {}",
                    event.email(),
                    ex
            );
        }
    }
}

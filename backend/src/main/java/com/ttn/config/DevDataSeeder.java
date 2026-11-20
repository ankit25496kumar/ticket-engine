package com.ttn.config;

import com.ttn.ticket.domain.TicketPriority;
import com.ttn.ticket.domain.TicketStatus;
import com.ttn.ticket.repository.TicketRepository;
import com.ttn.ticket.service.CommentService;
import com.ttn.ticket.service.TicketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
@Order(1000)
public class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private final TicketRepository ticketRepository;
    private final TicketService ticketService;
    private final CommentService commentService;

    public DevDataSeeder(
            TicketRepository ticketRepository,
            TicketService ticketService,
            CommentService commentService) {
        this.ticketRepository = ticketRepository;
        this.ticketService = ticketService;
        this.commentService = commentService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (ticketRepository.count() > 0) {
            log.info("Dev seed skipped: database already has {} ticket(s)", ticketRepository.count());
            return;
        }
        log.info("Seeding dev database with sample tickets");
        seedTickets();
    }

    private void seedTickets() {
        var billing =
                ticketService.create(
                        "Invoice total incorrect",
                        "Customer charged twice for the same order on invoice #8821.",
                        TicketPriority.HIGH,
                        "billing@support.com",
                        "billing");
        commentService.addComment(
                billing.getTicketId(),
                "billing@support.com",
                "Verified duplicate line item; finance notified.");

        var password =
                ticketService.create(
                        "Password reset link expired",
                        "User cannot complete reset; links expire after 30 minutes per policy.",
                        TicketPriority.MEDIUM,
                        "security@support.com",
                        "security");
        commentService.addComment(
                password.getTicketId(),
                "security@support.com",
                "Sent fresh reset email; advised clearing cache.");

        ticketService.create(
                "VPN drops hourly",
                "Remote workers lose VPN connection every hour on macOS 14.",
                TicketPriority.HIGH,
                "netops@support.com",
                "network");

        var onboarding =
                ticketService.create(
                        "New hire laptop request",
                        "Need MacBook Pro for engineer starting Monday.",
                        TicketPriority.LOW,
                        "hr@support.com",
                        "onboarding");
        ticketService.changeStatus(onboarding.getTicketId(), TicketStatus.IN_PROGRESS);

        var dbIssue =
                ticketService.create(
                        "Report query timeout",
                        "Sales dashboard times out after 30s on large date ranges.",
                        TicketPriority.MEDIUM,
                        "databases@support.com",
                        "database");
        ticketService.changeStatus(dbIssue.getTicketId(), TicketStatus.IN_PROGRESS);
        ticketService.changeStatus(dbIssue.getTicketId(), TicketStatus.RESOLVED);
        ticketService.updateFields(
                dbIssue.getTicketId(),
                null,
                null,
                null,
                null,
                null,
                "Added index on sales_fact.order_date; query under 5s.");

        ticketService.create(
                "Feature request: dark mode",
                "Admin UI should support dark theme for night shifts.",
                TicketPriority.LOW,
                "product@support.com",
                "enhancement");

        var cancelled =
                ticketService.create(
                        "Duplicate ticket submitted",
                        "Same issue as TKT-1005; closing duplicate.",
                        TicketPriority.LOW,
                        "billing@support.com",
                        "billing");
        ticketService.changeStatus(cancelled.getTicketId(), TicketStatus.CANCELLED);

        var closed =
                ticketService.create(
                        "Email not received",
                        "Outbound mail delayed; resolved after SMTP relay fix.",
                        TicketPriority.MEDIUM,
                        "mailops@support.com",
                        "email");
        ticketService.changeStatus(closed.getTicketId(), TicketStatus.IN_PROGRESS);
        ticketService.changeStatus(closed.getTicketId(), TicketStatus.RESOLVED);
        ticketService.changeStatus(closed.getTicketId(), TicketStatus.CLOSED);

        ticketService.create(
                "Mobile app crash on login",
                "Android 13 users see crash after biometric prompt.",
                TicketPriority.HIGH,
                "mobile@support.com",
                "mobile");

        log.info("Dev seed complete: {} tickets", ticketRepository.count());
    }
}
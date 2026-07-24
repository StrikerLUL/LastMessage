package com.dms.app.services.dispatch;

import com.dms.app.domain.models.*;
import java.util.ArrayList;
import java.util.List;

public class EmpiricalStressTest {

    public static void main(String[] args) {
        System.out.println("=== EMPIRICAL STRESS TEST SUITE ===");
        int passed = 0;
        int failed = 0;

        try {
            testSmtpBackoffDelays();
            passed++;
            System.out.println("[PASS] testSmtpBackoffDelays");
        } catch (Throwable e) {
            failed++;
            System.out.println("[FAIL] testSmtpBackoffDelays: " + e.getMessage());
            e.printStackTrace(System.out);
        }

        try {
            testSmsMultipartSplitting();
            passed++;
            System.out.println("[PASS] testSmsMultipartSplitting");
        } catch (Throwable e) {
            failed++;
            System.out.println("[FAIL] testSmsMultipartSplitting: " + e.getMessage());
            e.printStackTrace(System.out);
        }

        try {
            testSimCardAndFlightModeFailureMode();
            passed++;
            System.out.println("[PASS] testSimCardAndFlightModeFailureMode");
        } catch (Throwable e) {
            failed++;
            System.out.println("[FAIL] testSimCardAndFlightModeFailureMode: " + e.getMessage());
            e.printStackTrace(System.out);
        }

        try {
            testFallbackFailoverLogicSmsThenEmail();
            passed++;
            System.out.println("[PASS] testFallbackFailoverLogicSmsThenEmail");
        } catch (Throwable e) {
            failed++;
            System.out.println("[FAIL] testFallbackFailoverLogicSmsThenEmail: " + e.getMessage());
            e.printStackTrace(System.out);
        }

        try {
            testNetworkUnreachableAndMultiContactTimeout();
            passed++;
            System.out.println("[PASS] testNetworkUnreachableAndMultiContactTimeout");
        } catch (Throwable e) {
            failed++;
            System.out.println("[FAIL] testNetworkUnreachableAndMultiContactTimeout: " + e.getMessage());
            e.printStackTrace(System.out);
        }

        System.out.println("===================================");
        System.out.println("SUMMARY: Passed: " + passed + ", Failed: " + failed);
        if (failed > 0) {
            throw new RuntimeException("EmpiricalStressTest had " + failed + " failures");
        }
    }

    public static void testSmtpBackoffDelays() {
        SmtpMailer mailer = new SmtpMailer();
        SmtpCredentials smtp = new SmtpCredentials(
            1, "smtp.example.com", 587, "user@example.com", "secret", true, java.time.Instant.now().toString()
        );

        List<Long> delays = new ArrayList<>();
        mailer.setDelayProvider(delay -> {
            delays.add(delay);
            return kotlin.Unit.INSTANCE;
        });

        EmailResult res = mailer.sendSmtpEmailWithRetry(smtp, "recipient@example.com", "Body", 3, 2);

        if (!res.getSuccess()) {
            throw new AssertionError("Expected success on attempt 3");
        }
        if (res.getAttemptCount() != 3) {
            throw new AssertionError("Expected 3 attempts, got " + res.getAttemptCount());
        }
        if (delays.size() != 2) {
            throw new AssertionError("Expected 2 delays recorded, got " + delays.size());
        }
        if (delays.get(0) != 5000L) {
            throw new AssertionError("Attempt 2 delay expected 5000ms, got " + delays.get(0));
        }
        if (delays.get(1) != 15000L) {
            throw new AssertionError("Attempt 3 delay expected 15000ms, got " + delays.get(1));
        }
    }

    public static void testSmsMultipartSplitting() {
        SmsDispatcher dispatcher = new SmsDispatcher();

        // 160 chars (single part GSM-7)
        String msg160 = "A".repeat(160);
        List<String> parts160 = dispatcher.divideMessageText(msg160);
        if (parts160.size() != 1) {
            throw new AssertionError("Expected 1 part for 160 chars, got " + parts160.size());
        }

        // 161 chars (multi part GSM-7, 153 chars per part)
        String msg161 = "A".repeat(161);
        List<String> parts161 = dispatcher.divideMessageText(msg161);
        if (parts161.size() != 2) {
            throw new AssertionError("Expected 2 parts for 161 chars, got " + parts161.size());
        }
        if (parts161.get(0).length() != 153 || parts161.get(1).length() != 8) {
            throw new AssertionError("GSM-7 multi-part split divided 161 chars into " + parts161.get(0).length() + " and " + parts161.get(1).length());
        }
    }

    public static void testSimCardAndFlightModeFailureMode() {
        SmsDispatcher dispatcher = new SmsDispatcher();

        // Invalid phone number format returns success=false
        SmsResult res = dispatcher.sendMultipartSms("123", "Emergency payload");

        if (res.getSuccess()) {
            throw new AssertionError("Expected SmsDispatcher to return success=false for invalid phone number");
        }
    }

    public static void testFallbackFailoverLogicSmsThenEmail() {
        EmergencyDispatchEngine engine = new EmergencyDispatchEngine(new SmsDispatcher(), new SmtpMailer());

        DmsConfig config = new DmsConfig(
            1, 1440L, "SMS_THEN_EMAIL", 3, true, java.time.Instant.now().toString(), java.time.Instant.now().toString()
        );
        EmergencyMessage message = new EmergencyMessage(1, "Emergency Alert", false, java.time.Instant.now().toString());
        List<EmergencyContact> contacts = List.of(
            new EmergencyContact(1L, "Alice", "+15550100", "alice@example.com", 1, true, java.time.Instant.now().toString())
        );
        SmtpCredentials smtp = new SmtpCredentials(1, "smtp.example.com", 587, "user@example.com", "pass", true, java.time.Instant.now().toString());

        DispatchResult result = engine.triggerEmergencyDispatch(config, message, contacts, smtp);

        // In SMS_THEN_EMAIL mode, if SMS succeeds 100%, Email should NOT be sent!
        if (result.getSmsResults().get(0).getSuccess() && result.getEmailResults().size() > 0) {
            throw new AssertionError("SMS_THEN_EMAIL fallback defect: Email triggered when SMS succeeded!");
        }
    }

    public static void testNetworkUnreachableAndMultiContactTimeout() {
        SmtpMailer mailer = new SmtpMailer();
        SmtpCredentials smtp = new SmtpCredentials(1, "smtp.example.com", 587, "user@example.com", "pass", true, java.time.Instant.now().toString());

        List<Long> delays = new ArrayList<>();
        mailer.setDelayProvider(delay -> {
            delays.add(delay);
            return kotlin.Unit.INSTANCE;
        });

        EmailResult res = mailer.sendSmtpEmailWithRetry(smtp, "bob@example.com", "Alert", 3, 3);

        if (res.getSuccess()) {
            throw new AssertionError("Expected failure when network unreachable");
        }
        if (res.getAttemptCount() != 3) {
            throw new AssertionError("Expected 3 attempts for retries exhausted, got " + res.getAttemptCount());
        }
        long totalDelay = delays.stream().mapToLong(Long::longValue).sum();
        if (totalDelay != 20000L) {
            throw new AssertionError("Expected total backoff delay 20,000ms (5s + 15s), got " + totalDelay + "ms");
        }
    }
}

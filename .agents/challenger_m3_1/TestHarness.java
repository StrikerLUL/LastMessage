import com.dms.app.services.timer.TimerEngine;
import com.dms.app.domain.usecases.CheckInUseCase;
import com.dms.app.domain.usecases.EvaluateTimerUseCase;
import com.dms.app.domain.usecases.ScheduleNotificationsUseCase;
import com.dms.app.services.workmanager.BootReceiver;
import com.dms.app.domain.models.*;
import com.dms.app.domain.interfaces.*;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class TestHarness {

    // Mock ISecureStorage
    static class MockSecureStorage implements ISecureStorage {
        public String lastCheckIn = null;
        public DmsConfig config = new DmsConfig(1, 1440L, "SMS", 3, true, Instant.now().toString(), Instant.now().toString());
        public final List<CheckInLog> logs = Collections.synchronizedList(new ArrayList<>());

        @Override public void saveCheckInTimestamp(String timestamp) { this.lastCheckIn = timestamp; }
        @Override public String getLastCheckInTimestamp() { return this.lastCheckIn; }
        @Override public DmsConfig getConfig() { return this.config; }
        @Override public void saveConfig(DmsConfig config) { this.config = config; }
        @Override public List<EmergencyContact> getEmergencyContacts() { return new ArrayList<>(); }
        @Override public void saveEmergencyContacts(List<EmergencyContact> contacts) {}
        @Override public long addEmergencyContact(EmergencyContact contact) { return 1L; }
        @Override public void deleteEmergencyContact(long contactId) {}
        @Override public SmtpCredentials getSmtpCredentials() { return null; }
        @Override public void saveSmtpCredentials(SmtpCredentials credentials) {}
        @Override public EmergencyMessage getEmergencyMessage() { return new EmergencyMessage(1, "Help!", false, Instant.now().toString()); }
        @Override public void saveEmergencyMessage(EmergencyMessage message) {}
        @Override public String encryptSecret(String plainText) { return plainText; }
        @Override public String decryptSecret(String cipherText) { return cipherText; }
        @Override public long addCheckInLog(CheckInLog log) { logs.add(log); return logs.size(); }
        @Override public List<CheckInLog> getCheckInLogs() { return logs; }
    }

    // Mock INotificationScheduler
    static class MockNotificationScheduler implements INotificationScheduler {
        public List<MilestoneThreshold> lastScheduledMilestones = new ArrayList<>();
        public boolean cancelAllCalled = false;

        @Override public void createNotificationChannels() {}
        @Override public void scheduleThresholdNotifications(List<MilestoneThreshold> milestones) {
            long nowEpochMillis = Instant.now().toEpochMilli();
            this.lastScheduledMilestones = new ArrayList<>();
            for (MilestoneThreshold m : milestones) {
                if (m.getTriggerTimeEpochMillis() > nowEpochMillis) {
                    this.lastScheduledMilestones.add(m);
                }
            }
        }
        @Override public void cancelAllNotifications() { this.cancelAllCalled = true; }
        @Override public void sendWarningNotification(String title, String body) {}
    }

    public static void main(String[] args) throws Exception {
        System.out.println("==================================================");
        System.out.println("   EMPIRICAL CHALLENGER STRESS TEST SUITE        ");
        System.out.println("==================================================");

        TimerEngine engine = new TimerEngine();
        int totalTests = 0;
        int passed = 0;
        int defects = 0;

        // TEST 1: Short Interval Warning Threshold Defect (30m, 45m, 60m)
        System.out.println("\n--- TEST 1: Short Interval Warning Threshold Defect ---");
        totalTests++;
        long now = Instant.now().toEpochMilli();
        TimerEvaluation eval30m = engine.evaluateStatus(now, 30L, now);
        TimerEvaluation eval45m = engine.evaluateStatus(now, 45L, now);
        TimerEvaluation eval60m = engine.evaluateStatus(now, 60L, now);
        System.out.println("30m interval status after check-in: " + eval30m.getStatus());
        System.out.println("45m interval status after check-in: " + eval45m.getStatus());
        System.out.println("60m interval status after check-in: " + eval60m.getStatus());

        if (eval30m.getStatus() == TimerStatus.ACTIVE && eval45m.getStatus() == TimerStatus.ACTIVE && eval60m.getStatus() == TimerStatus.ACTIVE) {
            System.out.println("[PASS] Short intervals start as ACTIVE");
            passed++;
        } else {
            System.out.println("[DEFECT FOUND] Timers <= 60 mins evaluate to WARNING immediately upon check-in!");
            defects++;
        }

        // TEST 2: 0-minute and Negative Intervals
        System.out.println("\n--- TEST 2: 0-minute and Negative Intervals ---");
        totalTests++;
        try {
            List<MilestoneThreshold> thresholds0 = engine.calculateNotificationThresholds(now, 0L);
            MilestoneThreshold m1h = thresholds0.stream().filter(m -> m.getMilestoneName().equals("1_HOUR")).findFirst().orElse(null);
            if (m1h != null && Double.isInfinite(m1h.getPercentageRemaining())) {
                System.out.println("[DEFECT FOUND] 1_HOUR percentage remaining is INFINITY for 0-min interval!");
                defects++;
            } else {
                passed++;
            }
        } catch (Exception e) {
            System.out.println("[DEFECT FOUND] Exception during 0-min threshold calculation: " + e);
            defects++;
        }

        totalTests++;
        try {
            long remNeg = engine.getRemainingDurationMinutes(now, -30L, now);
            TimerEvaluation evalNeg = engine.evaluateStatus(now, -30L, now);
            System.out.println("Remaining mins for -30m interval: " + remNeg + ", Status: " + evalNeg.getStatus());
            passed++;
        } catch (Exception e) {
            System.out.println("[DEFECT FOUND] Exception for negative interval: " + e);
            defects++;
        }

        // TEST 3: Notification Threshold Calculation Accuracy (75%, 50%, 25%, 10%, 1h)
        System.out.println("\n--- TEST 3: Threshold Calculation Accuracy (24h Interval) ---");
        totalTests++;
        List<MilestoneThreshold> t24h = engine.calculateNotificationThresholds(now, 1440L);
        MilestoneThreshold t75 = t24h.stream().filter(m -> m.getMilestoneName().equals("75_PERCENT")).findFirst().get();
        MilestoneThreshold t50 = t24h.stream().filter(m -> m.getMilestoneName().equals("50_PERCENT")).findFirst().get();
        MilestoneThreshold t25 = t24h.stream().filter(m -> m.getMilestoneName().equals("25_PERCENT")).findFirst().get();
        MilestoneThreshold t10 = t24h.stream().filter(m -> m.getMilestoneName().equals("10_PERCENT")).findFirst().get();
        MilestoneThreshold t1h = t24h.stream().filter(m -> m.getMilestoneName().equals("1_HOUR")).findFirst().get();

        long offset75 = (t75.getTriggerTimeEpochMillis() - now) / 60000L;
        long offset50 = (t50.getTriggerTimeEpochMillis() - now) / 60000L;
        long offset25 = (t25.getTriggerTimeEpochMillis() - now) / 60000L;
        long offset10 = (t10.getTriggerTimeEpochMillis() - now) / 60000L;
        long offset1h = (t1h.getTriggerTimeEpochMillis() - now) / 60000L;

        if (offset75 == 360L && offset50 == 720L && offset25 == 1080L && offset10 == 1296L && offset1h == 1380L) {
            System.out.println("[PASS] 24h milestone offsets correct (75%@6h, 50%@12h, 25%@18h, 10%@21.6h, 1h@23h)");
            passed++;
        } else {
            System.out.println("[DEFECT FOUND] 24h milestone offsets incorrect! Got: " + offset75 + ", " + offset50 + ", " + offset25 + ", " + offset10 + ", " + offset1h);
            defects++;
        }

        // TEST 4: Threshold Ordering & Past Triggers for Short Intervals (< 60m)
        System.out.println("\n--- TEST 4: Threshold Ordering & Past Triggers for 30m Interval ---");
        totalTests++;
        List<MilestoneThreshold> t30m = engine.calculateNotificationThresholds(now, 30L);
        MilestoneThreshold t1h_30 = t30m.stream().filter(m -> m.getMilestoneName().equals("1_HOUR")).findFirst().get();
        if (t1h_30.getTriggerTimeEpochMillis() < now) {
            System.out.println("[DEFECT FOUND] 1_HOUR milestone trigger time is in the PAST (" + ((t1h_30.getTriggerTimeEpochMillis() - now) / 60000L) + " mins before check-in, pct=" + t1h_30.getPercentageRemaining() + ")!");
            defects++;
        } else {
            System.out.println("[PASS] 1_HOUR milestone trigger time is future");
            passed++;
        }

        // TEST 5: Leap Year & Timezone / Date Calculations
        System.out.println("\n--- TEST 5: Leap Year & Timezone Parsing ---");
        totalTests++;
        // Leap year test: Feb 28 2028 -> Feb 29 2028 (2028 is leap year)
        Instant Feb28_2028 = ZonedDateTime.of(2028, 2, 28, 12, 0, 0, 0, ZoneId.of("UTC")).toInstant();
        Instant Feb29_2028 = ZonedDateTime.of(2028, 2, 29, 12, 0, 0, 0, ZoneId.of("UTC")).toInstant();
        Instant Mar01_2028 = ZonedDateTime.of(2028, 3, 1, 12, 0, 0, 0, ZoneId.of("UTC")).toInstant();

        long leapMins1 = (Feb29_2028.toEpochMilli() - Feb28_2028.toEpochMilli()) / 60000L;
        long leapMins2 = (Mar01_2028.toEpochMilli() - Feb29_2028.toEpochMilli()) / 60000L;

        System.out.println("Leap Year 2028 Feb 28 to Feb 29: " + leapMins1 + " mins, Feb 29 to Mar 1: " + leapMins2 + " mins");
        if (leapMins1 == 1440L && leapMins2 == 1440L) {
            System.out.println("[PASS] Leap year date-time calculations accurate");
            passed++;
        } else {
            System.out.println("[DEFECT FOUND] Leap year calculation error");
            defects++;
        }

        // TEST 6: Epoch Rollover & Large Values
        System.out.println("\n--- TEST 6: Epoch Rollover & Large Interval Values ---");
        totalTests++;
        long y2038Epoch = 2147483647000L;
        TimerEvaluation eval2038 = engine.evaluateStatus(y2038Epoch, 1440L, y2038Epoch + 3600000L);
        if (eval2038.getRemainingMinutes() == 1380L) {
            System.out.println("[PASS] 64-bit Epoch Year 2038 handled correctly");
            passed++;
        } else {
            System.out.println("[DEFECT FOUND] Epoch Year 2038 error");
            defects++;
        }

        totalTests++;
        long largeIntervalMins = Long.MAX_VALUE / 60000L + 1L; // Overflow
        long remOverflow = engine.getRemainingDurationMinutes(now, largeIntervalMins, now);
        if (remOverflow <= 0) {
            System.out.println("[DEFECT FOUND] Arithmetic overflow on large interval resulting in remMins = " + remOverflow);
            defects++;
        } else {
            System.out.println("[PASS] Large interval overflow check");
            passed++;
        }

        // TEST 7: Concurrent & Rapid Sequential Check-ins
        System.out.println("\n--- TEST 7: Concurrent & Rapid Check-ins ---");
        totalTests++;
        MockSecureStorage storage = new MockSecureStorage();
        MockNotificationScheduler scheduler = new MockNotificationScheduler();
        CheckInUseCase checkInUseCase = new CheckInUseCase(storage, engine, scheduler);

        int threads = 10;
        int checkInsPerThread = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger(0);

        long startConcurrent = System.currentTimeMillis();
        for (int t = 0; t < threads; t++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < checkInsPerThread; i++) {
                        checkInUseCase.executeCheckIn("CONCURRENT_TEST", Instant.now().toString());
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();
        long endConcurrent = System.currentTimeMillis();
        System.out.println("Executed " + successCount.get() + " concurrent check-ins across 10 threads in " + (endConcurrent - startConcurrent) + " ms");
        System.out.println("Total audit logs recorded: " + storage.getCheckInLogs().size());
        if (storage.getCheckInLogs().size() == threads * checkInsPerThread) {
            System.out.println("[PASS] Concurrent check-ins thread-safe and audit log count matches");
            passed++;
        } else {
            System.out.println("[DEFECT FOUND] Race condition / log drop in concurrent check-ins!");
            defects++;
        }

        // TEST 8: Reboot Recovery & Boot Receiver Missed Expiry Defect
        System.out.println("\n--- TEST 8: Reboot Recovery & BootReceiver Missed Expiry ---");
        totalTests++;
        MockSecureStorage bootStorage = new MockSecureStorage();
        MockNotificationScheduler bootScheduler = new MockNotificationScheduler();
        
        // Simulate check-in at t0 = now - 25 hours (1500 mins ago for 1440m interval)
        long t0_25h_ago = now - (1500 * 60 * 1000L);
        bootStorage.saveCheckInTimestamp(Instant.ofEpochMilli(t0_25h_ago).toString());
        
        ScheduleNotificationsUseCase scheduleUseCase = new ScheduleNotificationsUseCase(bootStorage, engine, bootScheduler);
        BootReceiver bootReceiver = new BootReceiver(scheduleUseCase);

        // System reboots after timer HAS EXPIRED (25h after check-in)
        BootReceiver.BootResult bootResult = bootReceiver.onReceiveIntent(BootReceiver.ACTION_BOOT_COMPLETED);
        System.out.println("BootReceiver result: " + bootResult);
        System.out.println("Scheduled milestones count after boot: " + bootScheduler.lastScheduledMilestones.size());

        // Check if bootReceiver detected timer expired or triggered dispatch
        TimerEvaluation postBootEval = engine.evaluateStatus(t0_25h_ago, 1440L, now);
        System.out.println("Timer status at boot time: " + postBootEval.getStatus());

        if (postBootEval.getStatus() == TimerStatus.EXPIRED && bootScheduler.lastScheduledMilestones.isEmpty()) {
            System.out.println("[DEFECT FOUND] Device rebooted AFTER timer expired, but BootReceiver ONLY rescheduled notifications (0 alarms scheduled) and DID NOT trigger emergency dispatch or notify system!");
            defects++;
        } else {
            System.out.println("[PASS] Boot receiver handled expired state");
            passed++;
        }

        // TEST 9: EvaluateTimerUseCase ISO Parsing Resiliency
        System.out.println("\n--- TEST 9: EvaluateTimerUseCase Corrupt Timestamp Fallback ---");
        totalTests++;
        MockSecureStorage corruptStorage = new MockSecureStorage();
        corruptStorage.saveCheckInTimestamp("INVALID_TIMESTAMP_FORMAT");
        EvaluateTimerUseCase evalUseCase = new EvaluateTimerUseCase(corruptStorage, engine);

        try {
            TimerEvaluation corruptEval = evalUseCase.evaluateCurrentStatus(Instant.now().toString());
            System.out.println("Status on corrupt timestamp: " + corruptEval.getStatus() + ", Rem: " + corruptEval.getRemainingMinutes());
            System.out.println("[PASS] EvaluateTimerUseCase gracefully falls back on corrupt timestamp");
            passed++;
        } catch (Exception e) {
            System.out.println("[DEFECT FOUND] Unhandled exception on corrupt timestamp: " + e);
            defects++;
        }

        System.out.println("\n==================================================");
        System.out.println("   EMPIRICAL SUMMARY: Passed " + passed + ", Defects Found: " + defects + " / Total: " + totalTests);
        System.out.println("==================================================");
    }
}

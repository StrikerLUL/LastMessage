package com.dms.app.services.dispatch;

import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

public class RunJUnitTests {
    public static void main(String[] args) {
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
            .selectors(selectClass(EmergencyDispatchTest.class))
            .build();

        Launcher launcher = LauncherFactory.create();
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(request);

        System.out.println("JUnit Test Summary:");
        System.out.println("Tests Found: " + listener.getSummary().getTestsFoundCount());
        System.out.println("Tests Succeeded: " + listener.getSummary().getTestsSucceededCount());
        System.out.println("Tests Failed: " + listener.getSummary().getTestsFailedCount());
        listener.getSummary().getFailures().forEach(failure -> {
            System.out.println("Failure: " + failure.getTestIdentifier().getDisplayName() + " -> " + failure.getException());
        });

        // Also run EmpiricalStressTest
        System.out.println("\nRunning Empirical Stress Test...");
        EmpiricalStressTest.main(new String[0]);
    }
}

package com.scimplayground.validator.mgmt.service;

import base.ScimHttpExchange;
import base.ScimRunContext;
import com.scimplayground.validator.mgmt.dto.ValidationHttpExchangeView;
import com.scimplayground.validator.mgmt.dto.ValidationRunForm;
import com.scimplayground.validator.mgmt.dto.ValidationRunView;
import com.scimplayground.validator.mgmt.dto.ValidationTestResultView;
import com.scimplayground.validator.mgmt.model.MgmtUser;
import com.scimplayground.validator.mgmt.model.ValidationHttpExchange;
import com.scimplayground.validator.mgmt.model.ValidationRun;
import com.scimplayground.validator.mgmt.model.ValidationTestResult;
import com.scimplayground.validator.mgmt.repo.MgmtUserRepository;
import com.scimplayground.validator.mgmt.repo.ValidationHttpExchangeRepository;
import com.scimplayground.validator.mgmt.repo.ValidationRunRepository;
import com.scimplayground.validator.mgmt.repo.ValidationTestResultRepository;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

@Service
public class ValidationRunService {

    private static final List<String> SPEC_CLASS_NAMES = List.of(
        "specs.A1_ServiceDiscoverySpec",
        "specs.A2_SchemaValidationSpec",
        "specs.A3_UserCrudSpec",
        "specs.A4_PatchOperationsSpec",
        "specs.A5_FilteringSpec",
        "specs.A5_PaginationSpec",
        "specs.A5_SortingSpec",
        "specs.A6_GroupLifecycleSpec",
        "specs.A7_BulkOperationsSpec",
        "specs.A8_SecurityAndRobustnessSpec",
        "specs.A9_NegativeAndEdgeCasesSpec"
    );

    private final ValidationRunRepository runRepository;
    private final MgmtUserRepository mgmtUserRepository;
    private final ValidationTestResultRepository testResultRepository;
    private final ValidationHttpExchangeRepository exchangeRepository;

    public ValidationRunService(ValidationRunRepository runRepository,
                                MgmtUserRepository mgmtUserRepository,
                                ValidationTestResultRepository testResultRepository,
                                ValidationHttpExchangeRepository exchangeRepository) {
        this.runRepository = runRepository;
        this.mgmtUserRepository = mgmtUserRepository;
        this.testResultRepository = testResultRepository;
        this.exchangeRepository = exchangeRepository;
    }

    @Transactional
    public ValidationRun executeRun(ValidationRunForm form, String actorUserId, String actorDisplayName) {
        ValidationRun run = new ValidationRun();
        run.setName(form.name().trim());
        run.setTargetUrl(form.baseUrl().trim());
        run.setExecutedAt(OffsetDateTime.now());
        run.setStatus("RUNNING");
        MgmtUser owner = mgmtUserRepository.findById(actorUserId).orElse(null);
        run.setCreatedByUser(owner);
        run.setCreatedByUsername(actorDisplayName);
        run.setTotalTests(0);
        run.setPassedTests(0);
        run.setFailedTests(0);
        run = runRepository.save(run);

        Map<String, String> previousProperties = captureExistingProperties();

        try {
            System.setProperty("scim.baseUrl", form.baseUrl().trim());
            System.setProperty("scim.authToken", form.authToken().trim());

            ScimRunContext.reset();
            ScimRunContext.setCaptureEnabled(true);

            ValidationExecutionListener listener = new ValidationExecutionListener(run, testResultRepository, exchangeRepository);
            Launcher launcher = LauncherFactory.create();
            launcher.registerTestExecutionListeners(listener);
            launcher.execute(buildRequest());

            run.setTotalTests(listener.total);
            run.setPassedTests(listener.passed);
            run.setFailedTests(listener.failed);
            run.setStatus(listener.failed > 0 ? "FAILED" : "PASSED");
        } catch (Exception ex) {
            run.setStatus("ERROR");
        } finally {
            ScimRunContext.setCaptureEnabled(false);
            ScimRunContext.reset();
            restoreProperties(previousProperties);
        }

        return runRepository.save(run);
    }

    @Transactional(readOnly = true)
    public List<ValidationRunView> listRuns(String actorUserId, String actorUsername, boolean admin) {
        List<ValidationRun> runs;
        Sort sort = Sort.by(Sort.Direction.DESC, "executedAt");
        if (admin) {
            runs = runRepository.findAll(sort);
        } else {
            runs = runRepository.findOwnedRuns(actorUserId, actorUsername, sort);
        }
        return runs
            .stream()
            .map(ValidationRunView::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public ValidationRunView getRun(UUID runId, String actorUserId, String actorUsername, boolean admin) {
        ValidationRun run = requireRunAccess(runId, actorUserId, actorUsername, admin);
        return ValidationRunView.from(run);
    }

    @Transactional(readOnly = true)
    public List<ValidationTestResultView> getTestResults(UUID runId, String actorUserId, String actorUsername, boolean admin) {
        requireRunAccess(runId, actorUserId, actorUsername, admin);
        List<ValidationTestResult> testResults = testResultRepository.findByRunIdOrderByStartedAtAsc(runId);
        return testResults.stream()
            .map(testResult -> {
                List<ValidationHttpExchangeView> exchanges = exchangeRepository.findByTestResultIdOrderBySequenceNumberAsc(testResult.getId())
                    .stream()
                    .map(ValidationHttpExchangeView::from)
                    .toList();
                return ValidationTestResultView.from(testResult, exchanges);
            })
            .toList();
    }

    @Transactional
    public void deleteRun(UUID runId, String actorUserId, String actorUsername, boolean admin) {
        requireRunAccess(runId, actorUserId, actorUsername, admin);
        runRepository.deleteById(runId);
    }

    private ValidationRun requireRunAccess(UUID runId, String actorUserId, String actorUsername, boolean admin) {
        if (admin) {
            return runRepository.findById(runId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Validation run not found"));
        }
        return runRepository.findAccessibleById(runId, actorUserId, actorUsername)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Validation run not found"));
    }

    private static LauncherDiscoveryRequest buildRequest() throws ClassNotFoundException {
        LauncherDiscoveryRequestBuilder builder = LauncherDiscoveryRequestBuilder.request()
            .configurationParameter("junit.jupiter.execution.parallel.enabled", "false");

        for (String className : SPEC_CLASS_NAMES) {
            Class<?> specClass = Class.forName(className);
            builder.selectors(selectClass(specClass));
        }

        return builder.build();
    }

    private static Map<String, String> captureExistingProperties() {
        Map<String, String> values = new HashMap<>();
        values.put("scim.baseUrl", System.getProperty("scim.baseUrl"));
        values.put("scim.authToken", System.getProperty("scim.authToken"));
        return values;
    }

    private static void restoreProperties(Map<String, String> previousProperties) {
        restoreProperty("scim.baseUrl", previousProperties.get("scim.baseUrl"));
        restoreProperty("scim.authToken", previousProperties.get("scim.authToken"));
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    private static class ValidationExecutionListener implements TestExecutionListener {

        private final ValidationRun run;
        private final ValidationTestResultRepository testResultRepository;
        private final ValidationHttpExchangeRepository exchangeRepository;
        private final Map<String, OffsetDateTime> starts = new LinkedHashMap<>();

        private int total;
        private int passed;
        private int failed;

        private ValidationExecutionListener(ValidationRun run,
                                            ValidationTestResultRepository testResultRepository,
                                            ValidationHttpExchangeRepository exchangeRepository) {
            this.run = run;
            this.testResultRepository = testResultRepository;
            this.exchangeRepository = exchangeRepository;
        }

        @Override
        public void executionStarted(TestIdentifier testIdentifier) {
            if (!testIdentifier.isTest()) {
                return;
            }
            String uniqueId = testIdentifier.getUniqueId();
            starts.put(uniqueId, OffsetDateTime.now());
            ScimRunContext.beginTest(uniqueId);
        }

        @Override
        public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
            if (!testIdentifier.isTest()) {
                return;
            }

            String uniqueId = testIdentifier.getUniqueId();
            OffsetDateTime startedAt = starts.getOrDefault(uniqueId, OffsetDateTime.now());
            OffsetDateTime finishedAt = OffsetDateTime.now();

            ValidationTestResult testResult = new ValidationTestResult();
            testResult.setRun(run);
            testResult.setTestIdentifier(uniqueId);
            testResult.setDisplayName(testIdentifier.getDisplayName());
            testResult.setStatus(normalizeStatus(testExecutionResult.getStatus()));
            testResult.setStartedAt(startedAt);
            testResult.setFinishedAt(finishedAt);

            if (testIdentifier.getSource().isPresent() && testIdentifier.getSource().get() instanceof MethodSource methodSource) {
                testResult.setClassName(methodSource.getClassName());
                testResult.setTestName(methodSource.getMethodName());
            }

            if (testExecutionResult.getThrowable().isPresent()) {
                Throwable throwable = testExecutionResult.getThrowable().get();
                testResult.setErrorMessage(throwable.getMessage());
                testResult.setStackTrace(stackTrace(throwable));
            }

            testResult = testResultRepository.save(testResult);

            List<ScimHttpExchange> exchanges = ScimRunContext.getForTest(uniqueId);
            List<ValidationHttpExchange> persisted = new ArrayList<>();
            for (int i = 0; i < exchanges.size(); i++) {
                ScimHttpExchange captured = exchanges.get(i);
                ValidationHttpExchange exchange = new ValidationHttpExchange();
                exchange.setRun(run);
                exchange.setTestResult(testResult);
                exchange.setSequenceNumber(i + 1);
                exchange.setMethod(captured.getMethod());
                exchange.setUrl(captured.getUrl());
                exchange.setRequestHeaders(captured.getRequestHeaders());
                exchange.setRequestBody(captured.getRequestBody());
                exchange.setResponseStatus(captured.getResponseStatus());
                exchange.setResponseHeaders(captured.getResponseHeaders());
                exchange.setResponseBody(captured.getResponseBody());
                exchange.setCreatedAt(captured.getCreatedAt() == null ? OffsetDateTime.now() : captured.getCreatedAt());
                persisted.add(exchange);
            }
            exchangeRepository.saveAll(persisted);

            total++;
            if ("SUCCESS".equals(testResult.getStatus())) {
                passed++;
            } else {
                failed++;
            }
            ScimRunContext.endTest();
        }

        private static String normalizeStatus(TestExecutionResult.Status status) {
            return switch (status) {
                case SUCCESSFUL -> "SUCCESS";
                case ABORTED -> "ABORTED";
                case FAILED -> "FAILED";
            };
        }

        private static String stackTrace(Throwable throwable) {
            StringWriter stringWriter = new StringWriter();
            PrintWriter printWriter = new PrintWriter(stringWriter);
            throwable.printStackTrace(printWriter);
            return stringWriter.toString();
        }
    }
}

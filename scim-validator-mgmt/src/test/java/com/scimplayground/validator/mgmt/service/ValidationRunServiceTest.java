package com.scimplayground.validator.mgmt.service;

import org.junit.jupiter.api.Test;
import org.junit.platform.launcher.LauncherDiscoveryRequest;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class ValidationRunServiceTest {

    private static final String BASE_URL_PROPERTY = "scim.baseUrl";
    private static final String AUTH_TOKEN_PROPERTY = "scim.authToken";

    @Test
    void buildRequestLoadsValidatorSpecsFromRuntimeClasspath() throws Exception {
        String previousBaseUrl = System.getProperty(BASE_URL_PROPERTY);
        String previousAuthToken = System.getProperty(AUTH_TOKEN_PROPERTY);

        System.setProperty(BASE_URL_PROPERTY, "http://localhost:8080/ws/test/scim/v2");
        System.setProperty(AUTH_TOKEN_PROPERTY, "test-token");

        Method buildRequest = ValidationRunService.class.getDeclaredMethod("buildRequest");
        buildRequest.setAccessible(true);

        try {
            LauncherDiscoveryRequest request = (LauncherDiscoveryRequest) buildRequest.invoke(null);

            assertNotNull(request);
        } finally {
            restoreProperty(BASE_URL_PROPERTY, previousBaseUrl);
            restoreProperty(AUTH_TOKEN_PROPERTY, previousAuthToken);
        }
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}
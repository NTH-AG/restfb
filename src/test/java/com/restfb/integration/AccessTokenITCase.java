package com.restfb.integration;

import com.restfb.AccessToken;
import com.restfb.DefaultFacebookClient;
import com.restfb.Version;
import com.restfb.integration.base.RestFbIntegrationTestBase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class AccessTokenITCase extends RestFbIntegrationTestBase {

    @Test
    void convertToExtended() {
        DefaultFacebookClient client = new DefaultFacebookClient(getTestSettings().getUserAccessToken(), Version.LATEST);
        AccessToken accessToken = client.obtainExtendedAccessToken(getTestSettings().getAppId(), getTestSettings().getAppSecret());
        assertNotNull(accessToken);
        System.out.println("User Token: " + getTestSettings().getUserAccessToken());
        System.out.println("Long lived: " + accessToken.getAccessToken());
    }
}

package com.restfb.integration;

import java.util.ArrayList;
import java.util.List;

import com.restfb.integration.base.NeedFacebookWriteAccess;
import com.restfb.integration.base.RestFbIntegrationTestBase;
import org.junit.jupiter.api.Test;

import com.restfb.DefaultThreadsClient;
import com.restfb.Parameter;
import com.restfb.Version;
import com.restfb.types.GraphResponse;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@NeedFacebookWriteAccess
class ThreadsITCase extends RestFbIntegrationTestBase {

    @Test
    void publishImagePerUrl() {
        String thAccessToken = getTestSettings().getThreadsAccessToken();
        String clientSecret = getTestSettings().getThreadsClientSecret();
        Version version = Version.THREADS_LATEST;
        DefaultThreadsClient threadsClient = new DefaultThreadsClient(thAccessToken, clientSecret, version);

        String profileId = getTestSettings().getThreadsProfileId();
        String imageUrl = "https://placehold.co/600x400";

        List<Parameter> parameterList = new ArrayList<>();
        parameterList.add(Parameter.with("media_type","IMAGE"));
        parameterList.add(Parameter.with("image_url", imageUrl));
        parameterList.add(Parameter.with("text", "Test"));

        GraphResponse publish = threadsClient.publish(profileId + "/threads", GraphResponse.class, parameterList.toArray(Parameter[]::new));
        assertNotNull(publish);
        System.out.println(publish);
    }
}

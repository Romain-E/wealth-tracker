package fr.patrimoine.infrastructure.quote;

import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(QuoteProperties.class)
class QuoteConfiguration {

    private QuoteConfiguration() {}

    /**
     * The HTTP client every provider uses, with explicit timeouts.
     *
     * <p>Without them the JDK client waits indefinitely for a response. A provider that hangs
     * rather than fails would then hold a request thread, and possibly the database connection of
     * the request that triggered the lookup, for as long as it pleased. The circuit breaker cannot
     * help with a call that never returns; the timeout is what turns a hang into a countable
     * failure.
     *
     * <p>Not exposed as a bean on purpose: a {@code ClientHttpRequestFactory} bean would be picked
     * up by every other HTTP client in the application.
     */
    static ClientHttpRequestFactory requestFactory(QuoteProperties properties) {
        HttpClient client =
                HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(properties.readTimeout());
        return factory;
    }
}

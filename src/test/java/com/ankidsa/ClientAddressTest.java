package com.ankidsa;

import com.ankidsa.config.AuthRateLimitFilter;
import com.ankidsa.config.ClientAddress;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.junit.jupiter.api.Assertions.*;

class ClientAddressTest {
    private static final String ORIGIN = "https://ankidsa-backend.onrender.com";

    private ClientAddress render() {
        return new ClientAddress("", "render-cloudflare", true, "web", ORIGIN, ORIGIN);
    }

    private MockHttpServletRequest request(String client) {
        var request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("10.20.30.40");
        if (client != null) request.addHeader("CF-Connecting-IP", client);
        return request;
    }

    @Test void renderSeparatesClientsSharingOneProxyAndIgnoresForgedForwardingHeaders() {
        var address = render();
        var first = request("192.0.2.10");
        first.addHeader("X-Forwarded-For", "203.0.113.200, 10.0.0.1");
        first.addHeader("Forwarded", "for=203.0.113.200");
        first.addHeader("X-Real-IP", "203.0.113.200");
        assertEquals("192.0.2.10", address.resolve(first));
        assertNotEquals(address.resolve(first), address.resolve(request("192.0.2.11")));
        first.removeHeader("X-Forwarded-For");
        first.addHeader("X-Forwarded-For", "198.51.100.99");
        assertEquals("192.0.2.10", address.resolve(first));
    }

    @Test void renderCanonicalizesIpv6AndRejectsMalformedOrMultipleClientHeaders() {
        var address = render();
        assertEquals(address.resolve(request("2001:db8::1")), address.resolve(request("2001:0db8:0:0:0:0:0:1")));
        String denied = address.resolve(request(null));
        for (String value : new String[]{"", " ", "localhost", "999.1.2.3", "192.0.2.1:1234",
                "192.0.2.1, 192.0.2.2", "[2001:db8::1]", "fe80::1%eth0", " 192.0.2.1", "192.0.2.1 ", "2001:db8:::1"})
            assertEquals(denied, address.resolve(request(value)));
        var multiple = request("192.0.2.1");
        multiple.addHeader("CF-Connecting-IP", "192.0.2.2");
        assertEquals(denied, address.resolve(multiple));
        var noHeader = request(null);
        noHeader.setRemoteAddr("10.50.60.70");
        noHeader.addHeader("X-Forwarded-For", "192.0.2.99");
        assertEquals(denied, address.resolve(noHeader));
    }

    @Test void renderModeRequiresItsExactPublicRuntimeOriginAndRejectsAmbiguousConfiguration() {
        assertThrows(IllegalStateException.class, () -> new ClientAddress("", "render-cloudflare", false, "web", ORIGIN, ORIGIN));
        assertThrows(IllegalStateException.class, () -> new ClientAddress("", "render-cloudflare", true, "pserv", ORIGIN, ORIGIN));
        assertThrows(IllegalStateException.class, () -> new ClientAddress("", "render-cloudflare", true, "web", ORIGIN, "https://another.onrender.com"));
        assertThrows(IllegalStateException.class, () -> new ClientAddress("10.0.0.0/8", "render-cloudflare", true, "web", ORIGIN, ORIGIN));
        assertThrows(IllegalStateException.class, () -> new ClientAddress("", "unknown", true, "web", ORIGIN, ORIGIN));
        for (String origin : new String[]{"", "http://ankidsa-backend.onrender.com", "https://onrender.com",
                "https://ankidsa-backend.onrender.com.evil.test", "https://api.example.test",
                ORIGIN + ":443", ORIGIN + "/", ORIGIN + "?x=1", ORIGIN + "#x", "https://user@ankidsa-backend.onrender.com"})
            assertThrows(IllegalStateException.class, () -> new ClientAddress("", "render-cloudflare", true, "web", origin, origin));
    }

    @Test void existingCidrModeRetainsItsTrustedChainBoundary() {
        var direct = new ClientAddress("", "cidr", false, "", "", "");
        var request = request("192.0.2.50");
        request.addHeader("X-Forwarded-For", "192.0.2.60, 10.0.0.2");
        assertEquals("10.20.30.40", direct.resolve(request));
        var trusted = new ClientAddress("10.0.0.0/8", "cidr", false, "", "", "");
        assertEquals("192.0.2.60", trusted.resolve(request));
        request.setRemoteAddr("198.51.100.20");
        assertEquals("198.51.100.20", trusted.resolve(request));
    }

    @Test void oneClientCannotConsumeAnotherClientsLoginBudgetThroughTheSharedProxy() throws Exception {
        var filter = new AuthRateLimitFilter(render());
        for (int i = 0; i < 31; i++) {
            var response = new MockHttpServletResponse();
            var first = request("192.0.2.1");
            first.addHeader("X-Forwarded-For", "203.0.113." + (i + 1));
            filter.doFilter(first, response, (req, res) -> {});
            assertEquals(i < 30 ? 200 : 429, response.getStatus());
        }
        var secondResponse = new MockHttpServletResponse();
        filter.doFilter(request("192.0.2.2"), secondResponse, (req, res) -> {});
        assertEquals(200, secondResponse.getStatus());
    }
}

package com.sahha.gateway.filter;

import org.springframework.cloud.gateway.server.mvc.filter.HttpHeadersFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerResponse;

@Component
public class UpstreamRequestIdResponseHeadersFilter
		implements HttpHeadersFilter.ResponseHttpHeadersFilter, Ordered {

	@Override
	public HttpHeaders apply(
			HttpHeaders headers,
			ServerResponse response) {
		HttpHeaders filtered = new HttpHeaders();
		filtered.putAll(headers);
		filtered.remove(GatewayRequestContextFilter.REQUEST_ID_HEADER);
		return filtered;
	}

	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE;
	}
}

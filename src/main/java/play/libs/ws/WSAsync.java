package play.libs.ws;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import oauth.signpost.AbstractOAuthConsumer;
import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;
import oauth.signpost.http.HttpRequest;
import play.Logger;
import play.libs.ws.F.Promise;
import play.libs.ws.Http.Header;
import play.libs.ws.OAuth.ServiceInfo;
import play.libs.ws.WS.HttpResponse;
import play.libs.ws.WS.WSImpl;
import play.libs.ws.WS.WSRequest;

import com.ning.http.client.AsyncCompletionHandler;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClient.BoundRequestBuilder;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.AsyncHttpClientConfig.Builder;
import com.ning.http.client.ByteArrayPart;
import com.ning.http.client.FilePart;
import com.ning.http.client.Part;
import com.ning.http.client.PerRequestConfig;
import com.ning.http.client.ProxyServer;
import com.ning.http.client.Realm.AuthScheme;
import com.ning.http.client.Realm.RealmBuilder;
import com.ning.http.client.Response;

/**
 * Simple HTTP client to make webservices requests.
 *
 * <p/>
 * Get latest BBC World news as a RSS content
 * <pre>
 *    HttpResponse response = WS.url("http://newsrss.bbc.co.uk/rss/newsonline_world_edition/front_page/rss.xml").get();
 *    Document xmldoc = response.getXml();
 *    // the real pain begins here...
 * </pre>
 * <p/>
 *
 * Search what Yahoo! thinks of google (starting from the 30th result).
 * <pre>
 *    HttpResponse response = WS.url("http://search.yahoo.com/search?p=<em>%s</em>&pstart=1&b=<em>%s</em>", "Google killed me", "30").get();
 *    if( response.getStatus() == 200 ) {
 *       html = response.getString();
 *    }
 * </pre>
 */
public class WSAsync implements WSImpl {

    private final AsyncHttpClient httpClient;

    public WSAsync() {
        final String proxyHost = System.getProperty("http.proxyHost");//Play.configuration.getProperty("http.proxyHost", System.getProperty("http.proxyHost"));
        final String proxyPort = System.getProperty("http.proxyPort");//Play.configuration.getProperty("http.proxyPort", System.getProperty("http.proxyPort"));
        final String proxyUser = System.getProperty("http.proxyUser");//Play.configuration.getProperty("http.proxyUser", System.getProperty("http.proxyUser"));
        final String proxyPassword = System.getProperty("http.proxyPassword");//Play.configuration.getProperty("http.proxyPassword", System.getProperty("http.proxyPassword"));
        final String nonProxyHosts = System.getProperty("http.nonProxyHosts");//Play.configuration.getProperty("http.nonProxyHosts", System.getProperty("http.nonProxyHosts"));
        final String userAgent = "";//Play.configuration.getProperty("http.userAgent");

        final Builder confBuilder = new AsyncHttpClientConfig.Builder();
        if (proxyHost != null) {
            int proxyPortInt = 0;
            try {
                proxyPortInt = Integer.parseInt(proxyPort);
            } catch (final NumberFormatException e) {
                Logger.error("Cannot parse the proxy port property '%s'. Check property http.proxyPort either in System configuration or in Play config file.", proxyPort);
                throw new IllegalStateException("WS proxy is misconfigured -- check the logs for details");
            }
            final ProxyServer proxy = new ProxyServer(proxyHost, proxyPortInt, proxyUser, proxyPassword);
            if (nonProxyHosts != null) {
                final String[] strings = nonProxyHosts.split("\\|");
                for (final String uril : strings) {
                    proxy.addNonProxyHost(uril);
                }
            }
            confBuilder.setProxyServer(proxy);
        }
        if (userAgent != null) {
            confBuilder.setUserAgent(userAgent);
        }
        // when using raw urls, AHC does not encode the params in url.
        // this means we can/must encode it(with correct encoding) before passing it to AHC
        confBuilder.setUseRawUrl(true);
        httpClient = new AsyncHttpClient(confBuilder.build());
    }

    @Override
    public void stop() {
        Logger.trace("Releasing http client connections...");
        httpClient.close();
    }

    @Override
    public WSRequest newRequest(final String url, final String encoding) {
        return new WSAsyncRequest(url, encoding);
    }

    public class WSAsyncRequest extends WSRequest {

        protected String type = null;
        private String generatedContentType = null;


        protected WSAsyncRequest(final String url, final String encoding) {
            super(url, encoding);
        }

        /**
         * Returns the url but removed the queryString-part of it
         * The QueryString-info is later added with addQueryString()
         */
        protected String getUrlWithoutQueryString() {
            final int i = url.indexOf('?');
            if ( i > 0) {
                return url.substring(0,i);
            } else {
                return url;
            }
        }

        /**
         * Adds the queryString-part of the url to the BoundRequestBuilder
         */
        protected void addQueryString(final BoundRequestBuilder requestBuilder) {

            // AsyncHttpClient is by default encoding everything in utf-8 so for us to be able to use
            // different encoding we have configured AHC to use raw urls. When using raw urls,
            // AHC does not encode url and QueryParam with utf-8 - but there is another problem:
            // If we send raw (none-encoded) url (with queryString) to AHC, it does not url-encode it,
            // but transform all illegal chars to '?'.
            // If we pre-encoded the url with QueryString before sending it to AHC, ahc will decode it, and then
            // later break it with '?'.

            // This method basically does the same as RequestBuilderBase.buildUrl() except from destroying the
            // pre-encoding

            // does url contain query_string?
            int i = url.indexOf('?');
            if ( i > 0) {

                try {
                    // extract query-string-part
                    final String queryPart = url.substring(i+1);

                    // parse queryPart - and decode it... (it is going to be re-encoded later)
                    for( final String param : queryPart.split("&")) {

                        i = param.indexOf('=');
                        String name;
                        String value = null;
                        if ( i<=0) {
                            // only a flag
                            name = URLDecoder.decode(param, encoding);
                        } else {
                            name = URLDecoder.decode(param.substring(0,i), encoding);
                            value = URLDecoder.decode(param.substring(i+1), encoding);
                        }

                        if (value == null) {
                            requestBuilder.addQueryParameter(URLEncoder.encode(name, encoding), null);
                        } else {
                            requestBuilder.addQueryParameter(URLEncoder.encode(name, encoding), URLEncoder.encode(value, encoding));
                        }

                    }
                } catch (final UnsupportedEncodingException e) {
                    throw new RuntimeException("Error parsing query-part of url",e);
                }
            }
        }


        private BoundRequestBuilder prepareAll(final BoundRequestBuilder requestBuilder) {
            checkFileBody(requestBuilder);
            addQueryString(requestBuilder);
            addGeneratedContentType(requestBuilder);
            return requestBuilder;
        }


        public BoundRequestBuilder prepareGet() {
            return prepareAll(httpClient.prepareGet(getUrlWithoutQueryString()));
        }

        public BoundRequestBuilder prepareOptions() {
            return prepareAll(httpClient.prepareOptions(getUrlWithoutQueryString()));
        }

        public BoundRequestBuilder prepareHead() {
            return prepareAll(httpClient.prepareHead(getUrlWithoutQueryString()));
        }

        public BoundRequestBuilder preparePost() {
            return prepareAll(httpClient.preparePost(getUrlWithoutQueryString()));
        }

        public BoundRequestBuilder preparePut() {
            return prepareAll(httpClient.preparePut(getUrlWithoutQueryString()));
        }

        public BoundRequestBuilder prepareDelete() {
            return prepareAll(httpClient.prepareDelete(getUrlWithoutQueryString()));
        }

        /** Execute a GET request synchronously. */
        @Override
        public HttpResponse get() {
            this.type = "GET";
            sign();
            try {
                return new HttpAsyncResponse(prepare(prepareGet()).execute().get());
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }

        /** Execute a GET request asynchronously. */
        @Override
        public Promise<HttpResponse> getAsync() {
            this.type = "GET";
            sign();
            return execute(prepareGet());
        }


        /** Execute a POST request.*/
        @Override
        public HttpResponse post() {
            this.type = "POST";
            sign();
            try {
                return new HttpAsyncResponse(prepare(preparePost()).execute().get());
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }

        /** Execute a POST request asynchronously.*/
        @Override
        public Promise<HttpResponse> postAsync() {
            this.type = "POST";
            sign();
            return execute(preparePost());
        }

        /** Execute a PUT request.*/
        @Override
        public HttpResponse put() {
            this.type = "PUT";
            try {
                return new HttpAsyncResponse(prepare(preparePut()).execute().get());
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }

        /** Execute a PUT request asynchronously.*/
        @Override
        public Promise<HttpResponse> putAsync() {
            this.type = "PUT";
            return execute(preparePut());
        }

        /** Execute a DELETE request.*/
        @Override
        public HttpResponse delete() {
            this.type = "DELETE";
            try {
                return new HttpAsyncResponse(prepare(prepareDelete()).execute().get());
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }

        /** Execute a DELETE request asynchronously.*/
        @Override
        public Promise<HttpResponse> deleteAsync() {
            this.type = "DELETE";
            return execute(prepareDelete());
        }

        /** Execute a OPTIONS request.*/
        @Override
        public HttpResponse options() {
            this.type = "OPTIONS";
            try {
                return new HttpAsyncResponse(prepare(prepareOptions()).execute().get());
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }

        /** Execute a OPTIONS request asynchronously.*/
        @Override
        public Promise<HttpResponse> optionsAsync() {
            this.type = "OPTIONS";
            return execute(prepareOptions());
        }

        /** Execute a HEAD request.*/
        @Override
        public HttpResponse head() {
            this.type = "HEAD";
            try {
                return new HttpAsyncResponse(prepare(prepareHead()).execute().get());
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }

        /** Execute a HEAD request asynchronously.*/
        @Override
        public Promise<HttpResponse> headAsync() {
            this.type = "HEAD";
            return execute(prepareHead());
        }

        /** Execute a TRACE request.*/
        @Override
        public HttpResponse trace() {
            this.type = "TRACE";
            throw new UnsupportedOperationException("Not yet implemented.");
        }

        /** Execute a TRACE request asynchronously.*/
        @Override
        public Promise<HttpResponse> traceAsync() {
            this.type = "TRACE";
            throw new UnsupportedOperationException("Not yet implemented.");
        }

        private WSRequest sign() {
            if (this.oauthToken != null && this.oauthSecret != null) {
                final WSOAuthConsumer consumer = new WSOAuthConsumer(oauthInfo, oauthToken, oauthSecret);
                try {
                    consumer.sign(this, this.type);
                } catch (final Exception e) {
                    throw new RuntimeException(e);
                }
            }
            return this;
        }

        private BoundRequestBuilder prepare(final BoundRequestBuilder builder) {
            if (this.username != null && this.password != null && this.scheme != null) {
                AuthScheme authScheme;
                switch (this.scheme) {
                case DIGEST: authScheme = AuthScheme.DIGEST; break;
                case NTLM: authScheme = AuthScheme.NTLM; break;
                case KERBEROS: authScheme = AuthScheme.KERBEROS; break;
                case SPNEGO: authScheme = AuthScheme.SPNEGO; break;
                case BASIC: authScheme = AuthScheme.BASIC; break;
                default: throw new RuntimeException("Scheme " + this.scheme + " not supported by the UrlFetch WS backend.");
                }
                builder.setRealm(
                        (new RealmBuilder())
                        .setScheme(authScheme)
                        .setPrincipal(this.username)
                        .setPassword(this.password)
                        .setUsePreemptiveAuth(true)
                        .build()
                );
            }
            for (final String key: this.headers.keySet()) {
                builder.addHeader(key, headers.get(key));
            }
            builder.setFollowRedirects(this.followRedirects);
            final PerRequestConfig perRequestConfig = new PerRequestConfig();
            perRequestConfig.setRequestTimeoutInMs(
                    (int) Math.round(this.timeout.doubleValue() * 1000.0)
                    );
            builder.setPerRequestConfig(perRequestConfig);
            return builder;
        }

        private Promise<HttpResponse> execute(final BoundRequestBuilder builder) {
            try {
                final Promise<HttpResponse> smartFuture = new Promise<HttpResponse>();
                prepare(builder).execute(new AsyncCompletionHandler<HttpResponse>() {
                    @Override
                    public HttpResponse onCompleted(final Response response) throws Exception {
                        final HttpResponse httpResponse = new HttpAsyncResponse(response);
                        smartFuture.invoke(httpResponse);
                        return httpResponse;
                    }
                    @Override
                    public void onThrowable(final Throwable t) {
                        // An error happened - must "forward" the exception to the one waiting for the result
                        smartFuture.invokeWithException(t);
                    }
                });

                return smartFuture;
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }

        private void checkFileBody(final BoundRequestBuilder builder) {
            setResolvedContentType(null);
            if (this.fileParams != null) {
                //could be optimized, we know the size of this array.
                for (int i = 0; i < this.fileParams.length; i++) {
                    builder.addBodyPart(new FilePart(this.fileParams[i].paramName,
                            this.fileParams[i].file,
                            MimeTypes.getMimeType(this.fileParams[i].file.getName()),
                            encoding));
                }
                if (this.parameters != null) {
                    try {
                        // AHC only supports ascii chars in keys in multipart
                        for (final String key : this.parameters.keySet()) {
                            final Object value = this.parameters.get(key);
                            if (value instanceof Collection<?> || value.getClass().isArray()) {
                                final Collection<?> values = value.getClass().isArray() ? Arrays.asList((Object[]) value) : (Collection<?>) value;
                                for (final Object v : values) {
                                    final Part part = new ByteArrayPart(key, null, v.toString().getBytes(encoding), "text/plain", encoding);
                                    builder.addBodyPart( part );
                                }
                            } else {
                                final Part part = new ByteArrayPart(key, null, value.toString().getBytes(encoding), "text/plain", encoding);
                                builder.addBodyPart( part );
                            }
                        }
                    } catch(final UnsupportedEncodingException e) {
                        throw new RuntimeException(e);
                    }
                }

                // Don't have to set content-type: AHC will automatically choose multipart

                return;
            }
            if (this.parameters != null && !this.parameters.isEmpty()) {
                final boolean isPostPut = "POST".equals(this.type) || ("PUT".equals(this.type));

                if (isPostPut) {
                    // Since AHC is hard-coded to encode to use UTF-8, we must build
                    // the content ourself..
                    final StringBuilder sb = new StringBuilder();

                    for (final String key : this.parameters.keySet()) {
                        final Object value = this.parameters.get(key);
                        if (value == null) continue;

                        if (value instanceof Collection<?> || value.getClass().isArray()) {
                            final Collection<?> values = value.getClass().isArray() ? Arrays.asList((Object[]) value) : (Collection<?>) value;
                            for (final Object v: values) {
                                if (sb.length() > 0) {
                                    sb.append('&');
                                }
                                sb.append(encode(key));
                                sb.append('=');
                                sb.append(encode(v.toString()));
                            }
                        } else {
                            // Since AHC is hard-coded to encode using UTF-8, we must build
                            // the content ourself..
                            if (sb.length() > 0) {
                                sb.append('&');
                            }
                            sb.append(encode(key));
                            sb.append('=');
                            sb.append(encode(value.toString()));
                        }
                    }
                    try {
                        final byte[] bodyBytes = sb.toString().getBytes( this.encoding );
                        final InputStream bodyInStream = new ByteArrayInputStream( bodyBytes );
                        builder.setBody( bodyInStream );
                    } catch ( final UnsupportedEncodingException e) {
                        throw new RuntimeException(e);
                    }

                    setResolvedContentType("application/x-www-form-urlencoded; charset=" + encoding);

                } else {
                    for (final String key : this.parameters.keySet()) {
                        final Object value = this.parameters.get(key);
                        if (value == null) continue;
                        if (value instanceof Collection<?> || value.getClass().isArray()) {
                            final Collection<?> values = value.getClass().isArray() ? Arrays.asList((Object[]) value) : (Collection<?>) value;
                            for (final Object v: values) {
                                // must encode it since AHC uses raw urls
                                builder.addQueryParameter(encode(key), encode(v.toString()));
                            }
                        } else {
                            // must encode it since AHC uses raw urls
                            builder.addQueryParameter(encode(key), encode(value.toString()));
                        }
                    }
                    setResolvedContentType("text/html; charset=" + encoding);
                }
            }
            if (this.body != null) {
                if (this.parameters != null && !this.parameters.isEmpty()) {
                    throw new RuntimeException("POST or PUT method with parameters AND body are not supported.");
                }
                if(this.body instanceof InputStream) {
                    builder.setBody((InputStream)this.body);
                } else {
                    try {
                        final byte[] bodyBytes = this.body.toString().getBytes( this.encoding );
                        final InputStream bodyInStream = new ByteArrayInputStream( bodyBytes );
                        builder.setBody( bodyInStream );
                    } catch ( final UnsupportedEncodingException e) {
                        throw new RuntimeException(e);
                    }
                }
                setResolvedContentType("text/html; charset=" + encoding);
            }

            if(this.mimeType != null) {
                // User has specified mimeType
                this.headers.put("Content-Type", this.mimeType);
            }
        }

        /**
         * Sets the resolved Content-type - This is added as Content-type-header to AHC
         * if ser has not specified Content-type or mimeType manually
         * (Cannot add it directly to this.header since this cause problem
         * when Request-object is used multiple times with first GET, then POST)
         */
        private void setResolvedContentType(final String contentType) {
            generatedContentType = contentType;
        }

        /**
         * If generatedContentType is present AND if Content-type header is not already present,
         * add generatedContentType as Content-Type to headers in requestBuilder
         */
        private void addGeneratedContentType(final BoundRequestBuilder requestBuilder) {
            if (!headers.containsKey("Content-Type") && generatedContentType!=null) {
                requestBuilder.addHeader("Content-Type", generatedContentType);
            }
        }

    }

    /**
     * An HTTP response wrapper
     */
    public static class HttpAsyncResponse extends HttpResponse {

        private final Response response;

        /**
         * you shouldnt have to create an HttpResponse yourself
         * @param response
         */
        public HttpAsyncResponse(final Response response) {
            this.response = response;
        }

        /**
         * the HTTP status code
         * @return the status code of the http response
         */
        @Override
        public Integer getStatus() {
            return this.response.getStatusCode();
        }

        /**
         * the HTTP status text
         * @return the status text of the http response
         */
        @Override
        public String getStatusText() {
            return this.response.getStatusText();
        }

        @Override
        public String getHeader(final String key) {
            return response.getHeader(key);
        }

        @Override
        public List<Header> getHeaders() {
            final Map<String, List<String>> hdrs = response.getHeaders();
            final List<Header> result = new ArrayList<Header>();
            for (final String key: hdrs.keySet()) {
                result.add(new Header(key, hdrs.get(key)));
            }
            return result;
        }

        /**
         * get the response as a stream
         * @return an inputstream
         */
        @Override
        public InputStream getStream() {
            try {
                return response.getResponseBodyAsStream();
            } catch (final IllegalStateException e) {
                return new ByteArrayInputStream(new byte[]{}); // Workaround AHC's bug on empty responses
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }

    }

    private static class WSOAuthConsumer extends AbstractOAuthConsumer {

        public WSOAuthConsumer(final String consumerKey, final String consumerSecret) {
            super(consumerKey, consumerSecret);
        }

        public WSOAuthConsumer(final ServiceInfo info, final String token, final String secret) {
            super(info.consumerKey, info.consumerSecret);
            setTokenWithSecret(token, secret);
        }

        @Override
        protected HttpRequest wrap(final Object request) {
            if (!(request instanceof WSRequest)) {
                throw new IllegalArgumentException("WSOAuthConsumer expects requests of type play.libs.WS.WSRequest");
            }
            return new WSRequestAdapter((WSRequest)request);
        }

        public WSRequest sign(final WSRequest request, final String method) throws OAuthMessageSignerException, OAuthExpectationFailedException, OAuthCommunicationException {
            final WSRequestAdapter req = (WSRequestAdapter)wrap(request);
            req.setMethod(method);
            sign(req);
            return request;
        }

        public class WSRequestAdapter implements HttpRequest {

            private final WSRequest request;
            private String method;

            public WSRequestAdapter(final WSRequest request) {
                this.request = request;
            }

            @Override
            public Map<String, String> getAllHeaders() {
                return request.headers;
            }

            @Override
            public String getContentType() {
                return request.mimeType;
            }

            @Override
            public String getHeader(final String name) {
                return request.headers.get(name);
            }

            @Override
            public InputStream getMessagePayload() throws IOException {
                return null;
            }

            @Override
            public String getMethod() {
                return this.method;
            }

            private void setMethod(final String method) {
                this.method = method;
            }

            @Override
            public String getRequestUrl() {
                return request.url;
            }

            @Override
            public void setHeader(final String name, final String value) {
                request.setHeader(name, value);
            }

            @Override
            public void setRequestUrl(final String url) {
                request.url = url;
            }

        }

    }

}

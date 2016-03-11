package play.libs.ws;

import java.io.File;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import net.arnx.jsonic.JSON;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import play.Logger;
import play.libs.IO;
import play.libs.Time;
import play.libs.ws.F.Promise;
import play.libs.ws.Http.Header;
import play.libs.ws.Http.Request;
import play.libs.ws.OAuth.ServiceInfo;

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
public class WS {
    /** デフォルトの Web コンテンツの文字コード. */
    public static final String DEFAULT_WEB_ENCODING = "utf-8";
    private static WSImpl wsImpl = null;

    /** scheme. */
    public enum Scheme {
        BASIC, DIGEST, NTLM, KERBEROS, SPNEGO
    }

    /**
     * Singleton configured with default encoding - this one is used
     * when calling static method on WS.
     */
    private static WSWithEncoding wsWithDefaultEncoding;
    private static boolean setConnectionCloseHeader;

    /**
     * Internal class exposing all the methods previously exposed by WS.
     * This impl has information about encoding.
     * When calling original static methos on WS, then a singleton of
     * WSWithEncoding is called - configured with default encoding.
     * This makes this encoding-enabling backward compatible
     */
    public static class WSWithEncoding {
        public final String encoding;

        public WSWithEncoding(final String encoding) {
            this.encoding = encoding;
        }

        /**
         * Use thos method to get an instance to WS with diferent encoding
         * @param newEncoding the encoding to use in the communication
         * @return a new instance of WS with specified encoding
         */
        public WSWithEncoding withEncoding(final String newEncoding ) {
            return new WSWithEncoding( newEncoding );
        }

        /**
         * URL-encode a string to be used as a query string parameter.
         * @param part string to encode
         * @return url-encoded string
         */
        public String encode(final String part) {
            try {
                return URLEncoder.encode(part, encoding);
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }

        /**
         * Build a WebService Request with the given URL.
         * This object support chaining style programming for adding params, file, headers to requests.
         * @param url of the request
         * @return a WSRequest on which you can add params, file headers using a chaining style programming.
         */
        public WSRequest url(final String url) {
            init();
            return wsImpl.newRequest(url, encoding);
        }

        /**
         * Build a WebService Request with the given URL.
         * This constructor will format url using params passed in arguments.
         * This object support chaining style programming for adding params, file, headers to requests.
         * @param url to format using the given params.
         * @param params the params passed to format the URL.
         * @return a WSRequest on which you can add params, file headers using a chaining style programming.
         */
        public WSRequest url(final String url, final String... params) {
            final Object[] encodedParams = new String[params.length];
            for (int i = 0; i < params.length; i++) {
                encodedParams[i] = encode(params[i]);
            }
            return url(String.format(url, encodedParams));
        }

    }

    /**
     * Use thos method to get an instance to WS with diferent encoding
     * @param encoding the encoding to use in the communication
     * @return a new instance of WS with specified encoding
     */
    public static WSWithEncoding withEncoding(final String encoding ) {
        return wsWithDefaultEncoding.withEncoding(encoding);
    }

    //public void onApplicationStop() {
    @Override
    public void finalize() {
        if (wsImpl != null) {
            wsImpl.stop();
            wsImpl = null;
        }
    }

    //public void onApplicationStart() {
    static {
        wsWithDefaultEncoding = new WSWithEncoding(DEFAULT_WEB_ENCODING);

    }

    private synchronized static void init() {
        if (wsImpl != null) return;
        final String implementation = "async";//Play.configuration.getProperty("webservice", "async");
        if (implementation.equals("urlfetch")) {
            wsImpl = new WSUrlFetch();
            /*if (Logger.isTraceEnabled()) {
                Logger.trace("Using URLFetch for web service");
            }*/
        } else if (implementation.equals("async")) {
            /*if (Logger.isTraceEnabled()) {
                Logger.trace("Using Async for web service");
            }*/
            wsImpl = new WSAsync();
        } else {
            try {
                wsImpl = new WSAsync();//(WSImpl)Play.classloader.loadClass(implementation).newInstance();
                if (Logger.isTraceEnabled()) {
                    Logger.trace("Using the class:" + implementation + " for web service");
                }
            } catch (final Exception e) {
                throw new RuntimeException("Unable to load the class: " + implementation + " for web service");
            }
        }
    }

    /**
     * URL-encode a string to be used as a query string parameter.
     * @param part string to encode
     * @return url-encoded string
     */
    public static String encode(final String part) {
        return wsWithDefaultEncoding.encode(part);
    }


    /**
     * Build a WebService Request with the given URL.
     * This object support chaining style programming for adding params, file, headers to requests.
     * @param url of the request
     * @return a WSRequest on which you can add params, file headers using a chaining style programming.
     */
    public static WSRequest url(final String url) {
        return wsWithDefaultEncoding.url(url);
    }

    /**
     * Build a WebService Request with the given URL.
     * This constructor will format url using params passed in arguments.
     * This object support chaining style programming for adding params, file, headers to requests.
     * @param url to format using the given params.
     * @param params the params passed to format the URL.
     * @return a WSRequest on which you can add params, file headers using a chaining style programming.
     */
    public static WSRequest url(final String url, final String... params) {
        return wsWithDefaultEncoding.url(url, params);
    }

    public interface WSImpl {
        public WSRequest newRequest(String url, String encoding);
        public void stop();
    }

    public static abstract class WSRequest {
        public String url;
        public final String encoding;
        public String username;
        public String password;
        public Scheme scheme;
        public Object body;
        public FileParam[] fileParams;
        public Map<String, String> headers = new HashMap<String, String>();
        public Map<String, Object> parameters = new LinkedHashMap<String, Object>();
        public String mimeType;
        public boolean followRedirects = true;
        /**
         * timeout: value in seconds
         */
        public Number timeout = 60;

        public ServiceInfo oauthInfo = null;
        public String oauthToken = null;
        public String oauthSecret = null;

        public WSRequest() {
            this.encoding = DEFAULT_WEB_ENCODING;
        }

        public WSRequest(final String url, final String encoding) {
            try {
                this.url = new URI(url).toASCIIString();
            } catch (final Exception e) {
                this.url = url;
            }
            this.encoding = encoding;
        }

        /**
         * Add a MimeType to the web service request.
         * @param mimeType
         * @return the WSRequest for chaining.
         */
        public WSRequest mimeType(final String mimeType) {
            this.mimeType = mimeType;
            return this;
        }

        /**
         * define client authentication for a server host
         * provided credentials will be used during the request
         * @param username
         * @param password
         * @return the WSRequest for chaining.
         */
        public WSRequest authenticate(final String username, final String password, final Scheme scheme) {
            this.username = username;
            this.password = password;
            this.scheme = scheme;
            return this;
        }

        /**
         * define client authentication for a server host
         * provided credentials will be used during the request
         * the basic scheme will be used
         * @param username
         * @param password
         * @return the WSRequest for chaining.
         */
        public WSRequest authenticate(final String username, final String password) {
            return authenticate(username, password, Scheme.BASIC);
        }

        /**
         * Sign the request for do a call to a server protected by oauth
         * @return the WSRequest for chaining.
         */
        public WSRequest oauth(/*ServiceInfo oauthInfo, */final String token, final String secret) {
            //this.oauthInfo = oauthInfo;
            this.oauthToken = token;
            this.oauthSecret = secret;
            return this;
        }

        /**
         * Indicate if the WS should continue when hitting a 301 or 302
         * @return the WSRequest for chaining.
         */
        public WSRequest followRedirects(final boolean value) {
            this.followRedirects = value;
            return this;
        }

        /**
         * Set the value of the request timeout, i.e. the number of seconds before cutting the
         * connection - default to 60 seconds
         * @param timeout the timeout value, e.g. "30s", "1min"
         * @return the WSRequest for chaining
         */
        public WSRequest timeout(final String timeout) {
            this.timeout = Time.parseDuration(timeout);
            return this;
        }

        /**
         * Add files to request. This will only work with POST or PUT.
         * @param files
         * @return the WSRequest for chaining.
         */
        public WSRequest files(final File... files) {
            this.fileParams = FileParam.getFileParams(files);
            return this;
        }

        /**
         * Add fileParams aka File and Name parameter to the request. This will only work with POST or PUT.
         * @param fileParams
         * @return the WSRequest for chaining.
         */
        public WSRequest files(final FileParam... fileParams) {
            this.fileParams = fileParams;
            return this;
        }

        /**
         * Add the given body to the request.
         * @param body
         * @return the WSRequest for chaining.
         */
        public WSRequest body(final Object body) {
            this.body = body;
            return this;
        }

        /**
         * Add a header to the request
         * @param name header name
         * @param value header value
         * @return the WSRequest for chaining.
         */
        public WSRequest setHeader(final String name, final String value) {
            this.headers.put( Http.fixCaseForHttpHeader(name), value);
            return this;
        }

        /**
         * Add a parameter to the request
         * @param name parameter name
         * @param value parameter value
         * @return the WSRequest for chaining.
         */
        public WSRequest setParameter(final String name, final String value) {
            this.parameters.put(name, value);
            return this;
        }

        public WSRequest setParameter(final String name, final Object value) {
            this.parameters.put(name, value);
            return this;
        }

        /**
         * Use the provided headers when executing request.
         * @param headers
         * @return the WSRequest for chaining.
         */
        public WSRequest headers(final Map<String, String> headers) {
            this.headers = headers;
            return this;
        }

        /**
         * Add parameters to request.
         * If POST or PUT, parameters are passed in body using x-www-form-urlencoded if alone, or form-data if there is files too.
         * For any other method, those params are appended to the queryString.
         * @return the WSRequest for chaining.
         */
        public WSRequest params(final Map<String, Object> parameters) {
            this.parameters = parameters;
            return this;
        }

        /**
         * Add parameters to request.
         * If POST or PUT, parameters are passed in body using x-www-form-urlencoded if alone, or form-data if there is files too.
         * For any other method, those params are appended to the queryString.
         * @return the WSRequest for chaining.
         */
        public WSRequest setParameters(final Map<String, String> parameters) {
            this.parameters.putAll(parameters);
            return this;
        }

        /** Execute a GET request synchronously. */
        public abstract HttpResponse get();

        /** Execute a GET request asynchronously. */
        public Promise<HttpResponse> getAsync() {
            throw new UnsupportedOperationException("Not yet implemented.");
        }

        /** Execute a POST request.*/
        public abstract HttpResponse post();

        /** Execute a POST request asynchronously.*/
        public Promise<HttpResponse> postAsync() {
            throw new UnsupportedOperationException("Not yet implemented.");
        }

        /** Execute a PUT request.*/
        public abstract HttpResponse put();

        /** Execute a PUT request asynchronously.*/
        public Promise<HttpResponse> putAsync() {
            throw new UnsupportedOperationException("Not yet implemented.");
        }

        /** Execute a DELETE request.*/
        public abstract HttpResponse delete();

        /** Execute a DELETE request asynchronously.*/
        public Promise<HttpResponse> deleteAsync() {
            throw new UnsupportedOperationException("Not yet implemented.");
        }

        /** Execute a OPTIONS request.*/
        public abstract HttpResponse options();

        /** Execute a OPTIONS request asynchronously.*/
        public Promise<HttpResponse> optionsAsync() {
            throw new UnsupportedOperationException("Not yet implemented.");
        }

        /** Execute a HEAD request.*/
        public abstract HttpResponse head();

        /** Execute a HEAD request asynchronously.*/
        public Promise<HttpResponse> headAsync() {
            throw new UnsupportedOperationException("Not yet implemented.");
        }

        /** Execute a TRACE request.*/
        public abstract HttpResponse trace();

        /** Execute a TRACE request asynchronously.*/
        public Promise<HttpResponse> traceAsync() {
            throw new UnsupportedOperationException("Not yet implemented.");
        }

        protected String basicAuthHeader() {
            try {
                return  "Basic " + Codec.encodeBASE64(this.username + ":" + this.password);
            } catch (final UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            return null;
        }

        protected String encode(final String part) {
            try {
                return URLEncoder.encode(part, encoding);
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }

        protected String createQueryString() {
            final StringBuilder sb = new StringBuilder();
            for (final String key : this.parameters.keySet()) {
                if (sb.length() > 0) {
                    sb.append("&");
                }
                final Object value = this.parameters.get(key);

                if (value != null) {
                    if (value instanceof Collection<?> || value.getClass().isArray()) {
                        final Collection<?> values = value.getClass().isArray() ? Arrays.asList((Object[]) value) : (Collection<?>) value;
                        boolean first = true;
                        for (final Object v : values) {
                            if (!first) {
                                sb.append("&");
                            }
                            first = false;
                            sb.append(encode(key)).append("=").append(encode(v.toString()));
                        }
                    } else {
                        sb.append(encode(key)).append("=").append(encode(this.parameters.get(key).toString()));
                    }
                }
            }
            return sb.toString();
        }

    }

    public static class FileParam {
        public File file;
        public String paramName;

        public FileParam(final File file, final String name) {
            this.file = file;
            this.paramName = name;
        }

        public static FileParam[] getFileParams(final File[] files) {
            final FileParam[] filesp = new FileParam[files.length];
            for (int i = 0; i < files.length; i++) {
                filesp[i] = new FileParam(files[i], files[i].getName());
            }
            return filesp;
        }
    }

    /**
     * An HTTP response wrapper
     */
    public static abstract class HttpResponse {

        private String _encoding = null;

        /**
         * the HTTP status code
         * @return the status code of the http response
         */
        public abstract Integer getStatus();

        /**
         * The HTTP status text
         * @return the status text of the http response
         */
        public abstract String getStatusText();

        /**
         * @return true if the status code is 20x, false otherwise
         */
        public boolean success() {
            return Http.StatusCode.success(this.getStatus());
        }

        /**
         * The http response content type
         * @return the content type of the http response
         */
        public String getContentType() {
            return getHeader("content-type");
        }

        public String getEncoding() {
            // Have we already parsed it?
            if( _encoding != null ) {
                return _encoding;
            }

            // no! must parse it and remember
            final String contentType = getContentType();
            if( contentType == null ) {
                _encoding = DEFAULT_WEB_ENCODING;
            } else {
                final Http.ContentTypeWithEncoding contentTypeEncoding = Http.parseContentType( contentType );
                if( contentTypeEncoding.encoding == null ) {
                    _encoding = DEFAULT_WEB_ENCODING;
                } else {
                    _encoding = contentTypeEncoding.encoding;
                }
            }
            return _encoding;

        }

        public abstract String getHeader(String key);

        public abstract List<Header> getHeaders();

        /**
         * Parse and get the response body as a {@link Document DOM document}
         * @return a DOM document
         */
        public Document getXml() {
            return getXml( getEncoding() );
        }

        /**
         * parse and get the response body as a {@link Document DOM document}
         * @param encoding xml charset encoding
         * @return a DOM document
         */
        public Document getXml(final String encoding) {
            try {
                final InputSource source = new InputSource(getStream());
                source.setEncoding(encoding);
                final DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                //builder.setEntityResolver(new NoOpEntityResolver());
                return builder.parse(source);
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }

        /**
         * get the response body as a string
         * @return the body of the http response
         */
        public List<String> getStrings() {
            return IO.readLines(getStream(), getEncoding());
        }

        /**
         * get the response body as a string
         * @return the body of the http response
         */
        public String getString() {
            return IO.readContentAsString(getStream(), getEncoding());
        }

        /**
         * get the response body as a string
         * @param encoding string charset encoding
         * @return the body of the http response
         */
        public String getString(final String encoding) {
            return IO.readContentAsString(getStream(), encoding);
        }


        /**
         * Parse the response string as a query string.
         * @return The parameters as a Map. Return an empty map if the response
         * is not formed as a query string.
         */
        public Map<String, String> getQueryString() {
            final Map<String, String> result = new HashMap<String, String>();
            final String body = getString();
            for (final String entry: body.split("&")) {
                if (entry.indexOf("=") > 0) {
                    result.put(entry.split("=")[0], entry.split("=")[1]);
                }
            }
            return result;
        }

        /**
         * get the response as a stream
         * @return an inputstream
         */
        public abstract InputStream getStream();

        /**
         * get the response body as a {@link JSON#decode()}
         * @return the json response
         */
        public JSON getJson() {
            final String json = getString();
            try {
                return JSON.decode(json);
            } catch (final Exception e) {
                //Logger.error("Bad JSON: \n%s", json);
                throw new RuntimeException("Cannot parse JSON (check logs)", e);
            }
        }

    }

    /**
     * 非同期の Get 処理.
     * @param reqs WSRequest 配列
     * @return HttpResponse のリスト.
     */
    public static List<HttpResponse> asyncGet(final WSRequest... reqs) {
        return asyncGet(Arrays.asList(reqs));
    }

    /**
     * 非同期の Get 処理.
     * @param reqs WSRequest のリスト.
     * @return HttpResponse のリスト.
     */
    public static List<HttpResponse> asyncGet(final Collection<WSRequest> reqs) {
        final List<Promise<HttpResponse>> fResponses
            = new ArrayList<Promise<HttpResponse>>(reqs.size());
        reqs.stream().forEach(req -> {
            if (setConnectionCloseHeader) {
                req.setHeader("Connection", "Close");
            }
            fResponses.add(req.getAsync());
        });
        return await(Promise.waitAll(fResponses));
    }

    /**
     * 非同期の Post 処理.
     * @param reqs WSRequest 配列
     * @return HttpResponse のリスト.
     */
    public static List<HttpResponse> asyncPost(final WSRequest... reqs) {
        return asyncPost(Arrays.asList(reqs));
    }

    /**
     * 非同期の Post 処理.
     * @param reqs WSRequest のリスト.
     * @return HttpResponse のリスト.
     */
    public static List<HttpResponse> asyncPost(final Collection<WSRequest> reqs) {
        final List<Promise<HttpResponse>> fResponses
            = new ArrayList<Promise<HttpResponse>>(reqs.size());
        reqs.stream().forEach(req ->{
            if (setConnectionCloseHeader) {
                req.setHeader("Connection", "Close");
            }
            fResponses.add(req.postAsync());
        });
        return await(Promise.waitAll(fResponses));
    }

    /**
     * 停止処理.
     * @param future
     * @return
     */
    @SuppressWarnings("unchecked")
    public static <T> T await(final Future<T> future) {
        try {
            while (!future.isDone()) {
                Time.sleep(100L);
            }
        } catch (final InterruptedException e) {
            Logger.error("Unexpected!", e);
        }
        if (future.isDone()) {
            try {
                return future.get();
            } catch(final Exception e) {
                Logger.error("Unexpected!", e);
                return null;
            }
        } else {
            Request.current().isNew = false;
            return null;
        }
    }
}

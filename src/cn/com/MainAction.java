package cn.com;

import org.apache.http.*;
import org.apache.http.client.HttpClient;

import org.apache.http.client.methods.HttpGet;

import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;

import org.apache.http.impl.client.HttpClientBuilder;

import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLException;

import org.apache.http.conn.ConnectTimeoutException;


import org.apache.http.config.SocketConfig;

import org.apache.commons.codec.Charsets;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.client.HttpRequestRetryHandler;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;


/**
 * Description: 附件上传，正文上传，表单转pdf上传
 *
 * @author : Zao Yao
 * @date : 2022/06/30
 */

public class MainAction {


    private static HttpClient httpClient;
    // 最大连接数
    private static final int MAX_CONNECTION = 100;
    // 每个route能使用的最大连接数，一般和MAX_CONNECTION取值一样
    private static final int MAX_CONCURRENT_CONNECTIONS = 100;
    // 建立连接的超时时间，单位毫秒
    private static final int CONNECTION_TIME_OUT = 1000;
    // 请求超时时间，单位毫秒
    private static final int REQUEST_TIME_OUT = 1000;
    // 最大失败重试次数
    private static final int MAX_FAIL_RETRY_COUNT = 3;
    // 请求配置，可以复用
    private static RequestConfig requestConfig;


    static {
        SocketConfig socketConfig = SocketConfig.custom().setSoTimeout(REQUEST_TIME_OUT).setSoKeepAlive(true).setTcpNoDelay(true).build();

        requestConfig = RequestConfig.custom().setSocketTimeout(REQUEST_TIME_OUT).setConnectTimeout(CONNECTION_TIME_OUT).build();
        // 每个默认的 ClientConnectionPoolManager 实现将给每个route创建不超过2个并发连接，最多20个连接总数。
        PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager();
        connManager.setMaxTotal(MAX_CONNECTION);
        connManager.setDefaultMaxPerRoute(MAX_CONCURRENT_CONNECTIONS);
        connManager.setDefaultSocketConfig(socketConfig);

        httpClient = HttpClients.custom().setConnectionManager(connManager)
                // 添加重试处理器
                .setRetryHandler(new MyHttpRequestRetryHandler()).build();
    }


    public static void main(String[] args) throws IOException, IOException {

        String keyword = "java";
        String url = "https://search.jd.com/Search?keyword="+keyword;
        Document document = Jsoup.parse(new URL(url),300000);
        System.out.println(document.body().html());
    }

    /**
     * post请求
     *
     * @param url
     * @param paramMap
     * @param headers
     * @return
     * @throws Exception
     */
    public static String post(String url, Map<String, String> paramMap, List<Header> headers) throws Exception {
        URIBuilder uriBuilder = new URIBuilder(url);
        if (paramMap != null) {
            // 添加请求参数
            for (Map.Entry<String, String> entry : paramMap.entrySet()) {
                uriBuilder.addParameter(entry.getKey(), entry.getValue());
            }
        }

        HttpPost httpPost = new HttpPost(uriBuilder.build());
        if (headers != null) {
            // 添加请求首部
            for (Header header : headers) {
                httpPost.addHeader(header);
            }
        }

        httpPost.setConfig(requestConfig);

        // 执行请求
        HttpResponse response = httpClient.execute(httpPost);

        return EntityUtils.toString(response.getEntity(), Charsets.UTF_8);
    }


    public static String doFromPost(String url, List<Header> headers, Map<String, String> normalField, Map<String, File> fileField) throws IOException {
        HttpPost httpPost = new HttpPost(url);
        if (headers != null) {
            // 添加请求首部
            for (Header header : headers) {
                httpPost.addHeader(header);
            }
        }
        // 创建MultipartEntityBuilder
        MultipartEntityBuilder entityBuilder = MultipartEntityBuilder.create();
        // 追加普通表单字段
        for (Iterator<Map.Entry<String, String>> iterator = normalField.entrySet().iterator(); iterator.hasNext(); ) {

            Map.Entry<String, String> entity = iterator.next();

            entityBuilder.addPart(entity.getKey(), new StringBody(entity.getValue(), ContentType.create("text/plain", Consts.UTF_8)));

        }
        // 追加文件字段
        for (Iterator<Map.Entry<String, File>> iterator = fileField.entrySet().iterator(); iterator.hasNext(); ) {

            Map.Entry<String, File> entity = iterator.next();

            entityBuilder.addPart(entity.getKey(), new FileBody(entity.getValue()));

        }
        // 设置请求实体
        httpPost.setEntity(entityBuilder.build());
        httpPost.setConfig(requestConfig);
        // 发送请求
        HttpResponse response = httpClient.execute(httpPost);
        return EntityUtils.toString(response.getEntity(), Charsets.UTF_8);
    }


    /**
     * post请求，不带请求首部
     *
     * @param url
     * @param paramMap
     * @return
     * @throws Exception
     */
    public static String post(String url, Map<String, String> paramMap)
            throws Exception {

        return post(url, paramMap, null);
    }

    /**
     * get请求
     *
     * @param url
     * @param paramMap
     * @param headers
     * @return
     * @throws Exception
     */
    public static String get(String url, Map<String, String> paramMap,
                             List<Header> headers) throws Exception {
        URIBuilder uriBuilder = new URIBuilder(url);
        if (paramMap != null) {
            // 添加请求参数
            for (Map.Entry<String, String> entry : paramMap.entrySet()) {
                uriBuilder.addParameter(entry.getKey(), entry.getValue());
            }
        }

        HttpGet httpGet = new HttpGet(uriBuilder.build());
        if (headers != null) {
            // 添加请求首部
            for (Header header : headers) {
                httpGet.addHeader(header);
            }
        }

        httpGet.setConfig(requestConfig);

        // 执行请求
        HttpResponse response = httpClient.execute(httpGet);

        return EntityUtils.toString(response.getEntity(), Charsets.UTF_8);
    }

    /**
     * get请求，不带请求首部
     *
     * @param url
     * @param paramMap
     * @return
     * @throws Exception
     */
    public static String get(String url, Map<String, String> paramMap)
            throws Exception {

        return get(url, paramMap, null);
    }


    /**
     * 请求重试处理器
     *
     * @author manzhizhen
     */
    private static class MyHttpRequestRetryHandler implements HttpRequestRetryHandler {

        @Override
        public boolean retryRequest(IOException exception, int executionCount,
                                    HttpContext context) {
            if (executionCount >= MAX_FAIL_RETRY_COUNT) {
                return false;
            }

            if (exception instanceof InterruptedIOException) {
                // 超时
                return false;
            }
            if (exception instanceof UnknownHostException) {
                // 未知主机
                return false;
            }
            if (exception instanceof ConnectTimeoutException) {
                // 连接被拒绝
                return false;
            }
            if (exception instanceof SSLException) {
                // SSL handshake exception
                return false;
            }

            HttpClientContext clientContext = HttpClientContext.adapt(context);
            HttpRequest request = clientContext.getRequest();
            boolean idempotent = !(request instanceof HttpEntityEnclosingRequest);
            if (idempotent) {
                // 如果请求被认为是幂等的，则重试
                return true;
            }

            return false;
        }
    }
}

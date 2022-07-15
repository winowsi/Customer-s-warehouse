package weaver.interfaces.workflow.action.javacode;

import com.alibaba.fastjson.JSONObject;

import com.engine.common.util.ServiceUtil;
import com.engine.workflow.service.HtmlToPdfService;
import com.engine.workflow.service.impl.HtmlToPdfServiceImpl;

import com.sun.istack.Nullable;


import dm.jdbc.util.StringUtil;


import org.apache.http.Consts;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;

import org.apache.http.impl.client.HttpClientBuilder;
import weaver.conn.RecordSet;
import weaver.file.ImageFileManager;
import weaver.file.Prop;
import weaver.general.BaseBean;
import weaver.general.Util;
import weaver.hrm.User;
import weaver.integration.logging.Logger;
import weaver.integration.logging.LoggerFactory;
import weaver.interfaces.workflow.action.Action;
import weaver.soa.workflow.request.RequestInfo;
import weaver.workflow.request.RequestManager;

import java.io.*;


import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;


/**
 * Description: 附件上传，正文上传，表单转pdf上传
 *0
 * @author : Zao Yao
 * @date : 2022/06/30
 */

public class Action20220706102143 extends BaseBean implements Action {
    private static Logger newLog = LoggerFactory.getLogger(Action20220706102143.class);

    private static final String co = "200";

    /**
     * 临时目录
     */
    private static final String PATH = "/usr/weaver/temporaryFiles";


    @Override
    public String execute(RequestInfo requestInfo) {

        newLog.info("============进入 公文批阅单（上行文）流程推送 砼联 系统 程序 ===========");

        String APPLY_FORM = Prop.getPropValue("ConcreteSystems", "APPLY_FORM");
        newLog.info("从properties那到的融资OA申请单类型：" + APPLY_FORM);
        String MAIN_BODY = Prop.getPropValue("ConcreteSystems", "MAIN_BODY");
        newLog.info("从properties那到的正文类型：" + MAIN_BODY);
        String APPENDIX = Prop.getPropValue("ConcreteSystems", "APPENDIX");
        newLog.info("从properties那到的附件类型：" + APPENDIX);


        HashMap<String, Object> flowInfo = new HashMap<>(16);

        RequestManager requestManager = requestInfo.getRequestManager();
        //请求ID
        String requestId = requestInfo.getRequestid();
        //流程ID
        String workflowId = requestInfo.getWorkflowid();
        //流程数据库表名
        String billTableName = requestManager.getBillTableName();
        newLog.info("请求ID，流程ID，数据库表名" + requestId + ":" + workflowId + ":" + billTableName);
        RecordSet recordSet = new RecordSet();
        String sql = "select * from " + billTableName + " where requestId=" + requestId;
        recordSet.execute(sql);
        if (recordSet.next()) {
            //标题
            String title = Util.null2String(recordSet.getString("bt"));
            String[] strArray = {"银行", "供应链", "融资"};
            if (strContains(title, strArray)) {
                newLog.info("++++++++》》》》》》包含关键词开始流程推送 砼联 系统 程序");
                flowInfo.put("title", title);
                //流程ID
                flowInfo.put("flowCode", requestId);
                //来文编号
                String wordNo = Util.null2String(recordSet.getString("swbh"));
                flowInfo.put("wordNo", wordNo);
                //申请日期
                String date = Util.null2String(recordSet.getString("sj"));
                flowInfo.put("applyDate", date.replace("-", ""));
                //发起人姓名
                int swgly = Util.getIntValue(recordSet.getString("swgly"));
                flowInfo.put("applyUser", getUsr(swgly));
                //审批最终通过日期
                flowInfo.put("appliedDate", LocalDate.now().toString().replace("-", ""));
                //所有节点信息
                ArrayList<HashMap<String, String>> flowNodes = nodeInfos(requestId);
                flowInfo.put("flowNodes", flowNodes);
                String url = Prop.getPropValue("ConcreteSystems", "nodeUrl");
                //返回的信息
                String params = JSONObject.toJSONString(flowInfo);
                String postDoJson = postDoJson(url, params);
                Map<String, Object> pdf = JSONObject.parseObject(postDoJson, Map.class);
                String msg = pdf.get("msg").toString();
                newLog.info("流程表单基础信息响应结果：" + msg);
                if (!co.equals(pdf.get("code").toString())) {
                    requestInfo.getRequestManager().setMessageid("123#123");
                    requestInfo.getRequestManager().setMessagecontent("表单信息推送 砼联 失败：" + msg);
                    return Action.FAILURE_AND_CONTINUE;
                }


                //正文
                String content = Util.null2String(recordSet.getString("zw"));
                if (StringUtil.isNotEmpty(content)) {
                    ArrayList<Map<String, Object>> maps = upDocFile(content, requestId, MAIN_BODY);

                    for (Map<String, Object> map : maps) {
                        String msgzw = map.get("msg").toString();
                        newLog.info("流程正文上传响应结果：" + msgzw);
                        if (!co.equals(map.get("code").toString())) {
                            requestInfo.getRequestManager().setMessageid("123#123");
                            requestInfo.getRequestManager().setMessagecontent("正文推送 砼联 失败：" + msgzw);
                            return Action.FAILURE_AND_CONTINUE;
                        }
                    }
                }

                //附件
                String accessory = Util.null2String(recordSet.getString("fjsc"));
                if (StringUtil.isNotEmpty(accessory)) {
                    ArrayList<Map<String, Object>> maps = upDocFile(accessory, requestId, APPENDIX);
                    for (Map<String, Object> map : maps) {
                        String msgfj = map.get("msg").toString();
                        newLog.info("流程附件上传响应结果：" + msgfj);
                        if (!co.equals(map.get("code").toString())) {
                            requestInfo.getRequestManager().setMessageid("123#123");
                            requestInfo.getRequestManager().setMessagecontent("正文推送 砼联 失败：" + msgfj);
                            return Action.FAILURE_AND_CONTINUE;
                        }
                    }
                }


                //pdf表单
                Map<String, Object> map = uploadFileFromToPDF(requestId, APPLY_FORM);
                String msg1 = map.get("msg").toString();
                newLog.info("流程表单转PDF上传响应结果：" + msg1);
                if (!co.equals(map.get("code").toString())) {
                    requestInfo.getRequestManager().setMessageid("123#123");
                    requestInfo.getRequestManager().setMessagecontent("表单信息推送 砼联 失败：" + msg1);
                    return Action.FAILURE_AND_CONTINUE;
                }

            }
            return Action.SUCCESS;
        }

        requestInfo.getRequestManager().setMessageid("123#123");
        requestInfo.getRequestManager().setMessagecontent("表单信息推送 砼联 失败，请联系管理员！");
        return Action.FAILURE_AND_CONTINUE;
    }

    /**
     * 是否包含这三个关键此
     *
     * @param str
     * @return
     */
    public static boolean strContains(String str, String[] strArray) {
        boolean u = false;
        for (String s : strArray) {
            if (str.contains(s)) {
                u=true;
                return  u;
            }
        }
        return u;
    }

    /***
     *  正文 MAIN_BODY
     *  附件 APPENDIX
     *  融资OA申请单 APPLY_FORM
     * @param cid OA系统文件Id
     * @param flowCode 流程ID
     * @param  fileType 文件类型
     * @return 返回上传的信息builder
     */
    public ArrayList<Map<String, Object>> upDocFile(String cid, String flowCode, String fileType) {

        newLog.info("上传文件开始");
        ArrayList<Map<String, Object>> maps = new ArrayList<>();
        if ("".equals(Util.null2String(cid))) {
            newLog.info("未获取到文档的ID");
        } else {
            RecordSet recordSet = new RecordSet();
            String isextfile = "1";
            String MAIN_BODY = Prop.getPropValue("ConcreteSystems", "MAIN_BODY");
            if (fileType.equals(MAIN_BODY)) {
                isextfile = "''";
            }
            String sql = "select imagefilename,imagefileid,id,DOCID,isextfile  from docimagefile where docid in ("+cid+") and isextfile=" + isextfile;
            newLog.info("查询文件的sql："+sql);
            recordSet.execute(sql);
            if (recordSet.next()) {
                //文件名
                String imageFileName = Util.null2String(recordSet.getString("imagefilename"));
                //文件id
                String imageFileId = Util.null2String(recordSet.getString("imagefileid"));
                //遍历上传
                for (String id : stringToList(imageFileId)) {
                    ImageFileManager imageFileManager = new ImageFileManager();
                    imageFileManager.getImageFileInfoById(Integer.parseInt(id));
                    //获得文件输入流
                    InputStream stream = imageFileManager.getInputStream();
                    File file = new File(PATH + imageFileName);
                    //将二进制转文件
                    try {
                        inputStream2File(stream, file);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    // 判断是不是附件和附件类型是不是excel
                    String APPENDIX = Prop.getPropValue("ConcreteSystems", "APPENDIX");
                    if (fileType.equals(APPENDIX)){
                        String[] strArray = {".xls", ".xlsx"};
                        String name = file.getName();
                        String substring = name.substring(name.indexOf("."));
                        if (strContains(substring,strArray)) {
                            newLog.info("附件类型是excel开始上传");
                            Map<String, Object> map = upLoaderFile(file, flowCode, fileType);
                            maps.add(map);
                        }
                    }else {
                        //上传正文和pdf
                        Map<String, Object> map = upLoaderFile(file, flowCode, fileType);
                        maps.add(map);
                    }
                }
            }
        }
        return maps;
    }

    /**
     * 上传文件
     *
     * @param fileType 文件类型
     * @param flowCode 流程ID
     * @param stream   文件内容
     * @return success And defeated
     */
    @Nullable
    public Map<String, Object> upLoaderFile(File stream, String flowCode, String fileType) {

        HashMap<String, String> parameterMap = new HashMap<>(16);
        HashMap<String, File> fileMap = new HashMap<>(16);
        fileMap.put("file", stream);
        //文件类型
        parameterMap.put("fileType", fileType);
        //流程ID
        parameterMap.put("flowCode", flowCode);
        //文件有效日期
        parameterMap.put("expireDate", LocalDate.now().toString().replace("-", ""));
        //文件生效日期
        parameterMap.put("effectiveDate", LocalDate.now().toString().replace("-", ""));

        //上传文件地址
        //String url = "http://172.24.100.75:9203/expose/oa/upload";
        String url = Prop.getPropValue("ConcreteSystems", "TheAttachmentUrl");
        //发送请求
        String responseMessage = doFromPost(url, parameterMap, fileMap);
        String v = stream.delete() ? "清除临时文件" + stream.getName() + "成功" : "清除临时文件" + stream.getName() + "失败";
        newLog.info(v);
        return JSONObject.parseObject(responseMessage, Map.class);


    }


    /**
     * html转pdf
     * 上传pdf
     *
     * @param requestId requestId
     * @return success And defeated
     */
    public Map<String, Object> uploadFileFromToPDF(String requestId, String fileType) {

        HashMap<String, Object> params = new HashMap<>(16);
        params.put("requestid", requestId);
        params.put("isTest", "O");
        params.put("useWk", 1);
        //生成临时文件的目录
        params.put("path", PATH);
        params.put("filename", "批阅单" + requestId);

        //获取到html转pdf的service
        HtmlToPdfService htmlToPdfService = ServiceUtil.getService(HtmlToPdfServiceImpl.class, new User(1));
        //根据参数获取表单数据
        Map<String, Object> pathMap = htmlToPdfService.getFormDatas(params);
        //文件信息
        String path = pathMap.get("path").toString();
        String filename = pathMap.get("filename").toString();
        String filepath = path + "/" + filename;
        //集成日志输出文件地址
        newLog.info("===流程表单pdf路径:" + filepath);
        //根据文件路径获得文件对象
        File file = new File(filepath);
        newLog.info("流程表单转pdf方法执行完成");
        //上传文件
        return upLoaderFile(file, requestId, fileType);

    }

    /**
     * @param url         文件上传地址
     * @param normalField 字符map
     * @param fileField   文件 map
     * @return 字符传类型的结果
     */
    public static String doFromPost(String url, Map<String, String> normalField, Map<String, File> fileField) {

        HttpClient httpClient = HttpClientBuilder.create().build();
        String responseStr = "";
        try {
            HttpPost httpPost = new HttpPost(url);
            httpPost.addHeader("access-token", "access_799e5054eec64f828eef3ce7d4ad63fc");
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
            // 发送请求
            HttpResponse response = httpClient.execute(httpPost);
            responseStr = getHttpJSONString(response);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return responseStr;
    }

    /**
     * @param post 请求的响应结果
     * @return 返回响应结果的字符串
     * @throws java.io.IOException
     */

    public static String getHttpJSONString(HttpResponse post) throws java.io.IOException {
        InputStream inputStream = post.getEntity().getContent();
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            result.write(buffer, 0, length);
        }
        return result.toString(StandardCharsets.UTF_8.name());
    }

    /**
     * 将inputStream转化为file
     *
     * @param is   二进制流
     * @param file 要输出的文件目录
     */

    public static void inputStream2File(InputStream is, File file) throws IOException {
        OutputStream os = null;
        try {
            os = new FileOutputStream(file);
            int len = 0;
            byte[] buffer = new byte[8192];

            while ((len = is.read(buffer)) != -1) {
                os.write(buffer, 0, len);
            }
        } finally {
            os.close();
            is.close();
        }
    }

    /**
     * String 转化  list
     *
     * @param s 以逗号隔开的字符串
     * @return 返回去掉逗号的数组字符集合
     */

    public static List<String> stringToList(String s) {
        String[] split = s.split(",");
        return Arrays.asList(split);
    }

    /**
     * 根据流程 ID返回所有审批节点的信息 数组集合
     *
     * @param requestId 流程ID
     * @return 根据流程ID返回所有节点的审批信息
     */

    public static ArrayList<HashMap<String, String>> nodeInfos(String requestId) {
        ArrayList<HashMap<String, String>> flowNodes = new ArrayList<>();
        String sql = "select a.REMARK as msg,a.OPERATEDATE as date ,a.OPERATOR as usr,a.NODEID as node,b.NODENAME as name from workflow_requestlog as  a left join workflow_nodebase as b on a.NODEID=b.id where a.REQUESTID=" + requestId;
        RecordSet recordSet = new RecordSet();
        recordSet.execute(sql);
        while (recordSet.next()) {
            HashMap<String, String> nodeInfo = new HashMap<>(16);
            //流程ID
            nodeInfo.put("flowCode", requestId);
            //节点ID节点分组
            String node = Util.null2String(recordSet.getString("node"));
            nodeInfo.put("nodeId", node);
            //节点名
            String name = Util.null2String(recordSet.getString("name"));
            nodeInfo.put("nodeName", name);
            //审批人ID
            String usr = Util.null2String(recordSet.getString("usr"));
            nodeInfo.put("approveUserId", usr);
            nodeInfo.put("approveUserName", getUsr(Integer.parseInt(usr)));
            //审批意见
            String msg = Util.null2String(recordSet.getString("msg"));
            nodeInfo.put("approveMsg", msg);
            //审批时间
            String date = Util.null2String(recordSet.getString("date"));
            String replace = date.replace("-", "");
            nodeInfo.put("approveTime", replace);
            //添加到数组中
            flowNodes.add(nodeInfo);
        }
        newLog.info("收集所有节点的详细信息完成");
        return flowNodes;
    }


    public static String postDoJson(String url, String params) {
        String rep = "";
        HttpClient httpClient = new DefaultHttpClient();
        HttpPost httpPost = new HttpPost(url);
        httpPost.addHeader("access-token", "access_799e5054eec64f828eef3ce7d4ad63fc");
        httpPost.addHeader("Accept", "application/json");
        httpPost.setHeader("Content-Type", "application/json");
        String charSet = "UTF-8";
        StringEntity entity = new StringEntity(params, charSet);
        httpPost.setEntity(entity);
        HttpResponse response = null;
        try {
            response = httpClient.execute(httpPost);
            rep = getHttpJSONString(response);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return rep;
    }

    public static String getUsr(int id) {
        RecordSet recordSet = new RecordSet();
        recordSet.execute("select LASTNAME from hrmresource where ID=" + id);
        recordSet.next();
        return Util.null2String(recordSet.getString("LASTNAME"));
    }

}

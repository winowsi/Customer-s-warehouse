
import com.alibaba.fastjson.JSONObject;

import com.engine.common.util.ServiceUtil;
import com.engine.workflow.service.HtmlToPdfService;
import com.engine.workflow.service.impl.HtmlToPdfServiceImpl;
import com.engine.workrelate.logging.Logger;
import com.engine.workrelate.logging.LoggerFactory;
import com.sun.istack.Nullable;


import dm.jdbc.util.StringUtil;

import lombok.SneakyThrows;
import org.apache.http.Consts;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;

import weaver.conn.RecordSet;
import weaver.file.ImageFileManager;
import weaver.general.Util;
import weaver.interfaces.workflow.action.Action;
import weaver.soa.workflow.request.RequestInfo;
import weaver.workflow.request.RequestManager;
import weaver.integration.util.HTTPUtil;
import java.io.*;


import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;


/**
 * Description: 附件上传，正文上传，表单转pdf上传
 *
 * @author : Zao Yao
 * @date : 2022/06/30
 */

public class MainAction implements Action {
    private static Logger newLog = LoggerFactory.getLogger(MainAction.class);
    private static final String co = "200";
    /**
     * 融资OA申请单
     */
    private static final String APPLY_FORM = "APPLY_FORM";
    /**
     * 正文
     */
    private static final String MAIN_BODY = "MAIN_BODY";
    /**
     * 附件
     */
    private static final String APPENDIX = "APPENDIX";


    @SneakyThrows
    @Override
    public String execute(RequestInfo requestInfo) {

        HashMap<String, Object> flowInfo= new HashMap<>(16);

        RequestManager requestManager = requestInfo.getRequestManager();
        //请求ID
        String requestId = requestInfo.getRequestid();
        //流程ID
        String workflowId = requestInfo.getWorkflowid();
        //流程数据库表名
        String billTableName = requestManager.getBillTableName();

        RecordSet recordSet = new RecordSet();
        String sql ="select * from ? where requestId= ?";
        recordSet.executeQuery(sql,billTableName,requestId);
        if (recordSet.next()){

            /**
             * 文件信息推送
             */

            //正文
            String content = Util.null2String(recordSet.getString("zw"));
            if (StringUtil.isNotEmpty(content)){
                String s = upDocFile(content, workflowId, MAIN_BODY);
                newLog.info("流程正文上传响应结果："+s);
            }

            //附件
            String accessory = Util.null2String(recordSet.getString("fjsc"));
            if (StringUtil.isNotEmpty(content)){
                String s = upDocFile(accessory, workflowId, APPENDIX);
                newLog.info("流程附件上传响应结果："+s);
            }
            //pdf表单
            String s = uploadFileFromToPDF(requestId,workflowId, APPLY_FORM);
            newLog.info("流程表单转PDF上传响应结果："+s);

            /**
             * 基础信息推送
             */

            //流程ID
            flowInfo.put("flowCode",workflowId);
            //标题
            String title = Util.null2String(recordSet.getString("bt"));
            flowInfo.put( "title", title);
            //来文编号
            String wordNo = Util.null2String(recordSet.getString("swbh"));
            flowInfo.put("wordNo",wordNo);
            //TODO 工商号
            flowInfo.put("businessLicense","");
            //TODO 申请融资金额汇总
            flowInfo.put("financingAmount","22.19");
            //申请日期
            String date = Util.null2String(recordSet.getString("sj"));
            flowInfo.put("applyDate",date.replace("-", ""));
            //TODO 发起人名称
            String name = Util.null2String(recordSet.getString("swgly"));
            flowInfo.put("applyUser", "admin");
            //审批最终通过日期
            flowInfo.put("appliedDate",LocalDate.now().toString().replace("-", ""));
            //所有节点信息
            ArrayList<HashMap<String, String>> flowNodes = nodeInfos(workflowId);
            flowInfo.put("flowNodes",flowNodes);
            String url="http://172.24.100.75:9203/expose/oa/complete";
            //返回的信息
            String resp = HTTPUtil.doPost(url, flowInfo);
            newLog.info("补全表单基本信息响应结果："+resp);
            return Action.SUCCESS;


        }

        return Action.FAILURE_AND_CONTINUE;
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
    public String upDocFile(String cid, String flowCode, String fileType) throws IOException {

        StringBuilder message= new StringBuilder();
        if ("".equals(Util.null2String(cid))) {
            newLog.info("未获取到文档的ID");
        } else {
            RecordSet recordSet = new RecordSet();
            String sql = "select imagefilename,imagefileid,id from docimagefile where docid in (?)";
            recordSet.executeQuery(sql, cid);
            if(recordSet.next()) {
                //文件名
                String imageFileName = Util.null2String(recordSet.getString("imagefilename"));
                //文件id
                String imageFileId = Util.null2String(recordSet.getString("imagefileid"));
                //遍历上传
                stringToList(imageFileId).forEach(id->{

                    ImageFileManager imageFileManager = new ImageFileManager();
                    imageFileManager.getImageFileInfoById(Integer.parseInt(id));
                    //获得文件输入流
                    InputStream stream = imageFileManager.getInputStream();
                    File file = new File("E:\\WEAVER\\file_temp\\" + imageFileName);
                    //将二进制转文件
                    try {
                        inputStream2File(stream, file);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    //上传附件
                    String msg = upLoaderFile(file, flowCode, fileType);
                    message.append(msg);

                });
            }
        }
        return message.toString();
    }


    /**
     * 上传文件
     * @param fileType 文件类型
     * @param flowCode 流程ID
     * @param stream        文件内容
     * @return success And defeated
     */
    @Nullable
    public String upLoaderFile(File stream, String flowCode, String fileType) {

        HashMap<String, String> parameterMap = new HashMap<>(16);
        HashMap<String, File> fileMap = new HashMap<>(16);
        fileMap.put("file", stream);
        //文件类型
        parameterMap.put("fileType", fileType);
        //流程ID
        parameterMap.put("flowCode", flowCode);
        //文件有效日期
        parameterMap.put("expireDate", "");
        //文件生效日期
        parameterMap.put("effectiveDate", "20220708");

        //上传文件地址
        String url = "http://172.24.100.75:9203/expose/oa/upload";
        //发送请求
        String responseMessage = doFromPost(url, parameterMap, fileMap);
        newLog.info("上传文件响应的结果" + responseMessage);
        Map<String, String> messageMap = JSONObject.parseObject(responseMessage, Map.class);
        //响应代码
        String code = messageMap.get("code");
        if (co.equals(code)) {
            newLog.info("上传文件成功");
        } else {
            //响应消息
            String message = messageMap.get("msg");
            newLog.info("上传文件失败" + message);
        }
        return messageMap.get("msg");

    }

    /**
     * html转pdf
     * 上传pdf
     *
     * @param requestId requestId
     * @return success And defeated
     */
    public String uploadFileFromToPDF(String requestId,String flowCode, String fileType) {

            HashMap<String, Object> params = new HashMap<>(16);
            params.put("requestid", requestId);
            params.put("isTest", "O");
            params.put("useWk", 1);
            //生成临时文件的目录
            params.put("path", "E:/WEAVER/file_temp");
            params.put("filename", "Z"+"批阅单" + requestId);

            //获取到html转pdf的service
            HtmlToPdfService htmlToPdfService = ServiceUtil.getService(HtmlToPdfServiceImpl.class);
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
            //上传文件
            return upLoaderFile(file, flowCode,fileType);

    }

    /**
     *
     * @param url 文件上传地址
     * @param normalField 字符map
     * @param fileField 文件 map
     * @return 字符传类型的结果
     */
    public static String doFromPost(String url, Map<String, String> normalField, Map<String, File> fileField) {

        HttpClient httpClient = new DefaultHttpClient();
        String responseStr = "";
        try {
            HttpPost httpPost = new HttpPost(url);
            httpPost.addHeader("access_token", "access_799e5054eec64f828eef3ce7d4ad63fc");
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
     *
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
        String str = result.toString(StandardCharsets.UTF_8.name());
        return str;
    }

    /**
     * 将inputStream转化为file
     * @param is
     * @param file 要输出的文件目录
     */
    public static void inputStream2File (InputStream is, File file) throws IOException {
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
     * @param s
     * @return
     */
    public static List<String> stringToList(String s) {
        String[] split = s.split(",");
        return Arrays.asList(split);
    }


    /**
     * 根据流程 ID返回所有审批节点的信息 数组集合
     * @param workflowId 流程ID
     * @return 根据流程ID返回所有节点的审批信息
     */

    public  static  ArrayList<HashMap<String, String>> nodeInfos(String workflowId){
        ArrayList<HashMap<String, String>> flowNodes = new ArrayList<>();
        HashMap<String, String> nodeInfo= new HashMap<>(16);
        String sql="select a.REMARK,a.OPERATEDATE,a.OPERATOR,a.NODEID,b.GROUPNAME from ecology.workflow_requestlog as  a left join ecology.workflow_nodegroup as b on a.NODEID=b.NODEID where a.WORKFLOWID=?";
        RecordSet recordSet = new RecordSet();
        recordSet.executeQuery(sql,workflowId);
        while (recordSet.next()){
            //流程ID
            nodeInfo.put("flowCode",workflowId);
            //节点ID节点分组
            String NODEID = Util.null2String(recordSet.getString("NODEID"));
            nodeInfo.put("nodeId",NODEID);
            //节点名
            String GROUPNAME = Util.null2String(recordSet.getString("GROUPNAME"));
            nodeInfo.put("nodeName",GROUPNAME);
            //审批人ID
            String OPERATOR = Util.null2String(recordSet.getString("OPERATOR"));
            nodeInfo.put("approveUserId",OPERATOR);
            //审批意见
            String REMARK = Util.null2String(recordSet.getString("REMARK"));
            nodeInfo.put("approveMsg",REMARK);
            //审批时间
            String OPERATEDATE = Util.null2String(recordSet.getString("OPERATEDATE"));
            String replace = OPERATEDATE.replace("-", "");
            nodeInfo.put("approveTime",replace);
            //添加到数组中
            flowNodes.add(nodeInfo);
        }
        return flowNodes;
    }




}

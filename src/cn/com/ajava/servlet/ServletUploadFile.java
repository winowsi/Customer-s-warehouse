package cn.com.ajava.servlet;

import java.io.File;

import java.io.IOException;

import java.io.PrintWriter;

import java.util.Enumeration;

import java.util.Iterator;

import java.util.List;

import javax.servlet.ServletException;

import javax.servlet.http.HttpServlet;

import javax.servlet.http.HttpServletRequest;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItem;

import org.apache.commons.fileupload.disk.DiskFileItemFactory;

import org.apache.commons.fileupload.servlet.ServletFileUpload;

/**
 * 文件上传Servlet
 *
 * @author 曾繁添
 */

public class ServletUploadFile extends HttpServlet {

    private static final long serialVersionUID = 1L;

    // 限制文件的上传大小 1G

    private int maxPostSize = 1000 * 1024 * 10;

    public ServletUploadFile() {

        super();

    }

    @Override

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException,

            IOException {

        doPost(request, response);

    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException,

            IOException {

        PrintWriter out = response.getWriter();

        String contextPath = request.getSession().getServletContext().getRealPath("/");

        String webDir = "uploadfile" + File.separator + "images" + File.separator;

        String systemPath = request.getContextPath();

        String basePath = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + systemPath + "/";

        request.setCharacterEncoding("UTF-8");


        response.setContentType("text/html;charset=UTF-8");

        try {

            DiskFileItemFactory factory = new DiskFileItemFactory();

            // 设置写入大小
            factory.setSizeThreshold(1024 * 4);

            ServletFileUpload upload = new ServletFileUpload(factory);
            // 设置文件上传最大大小
            upload.setSizeMax(maxPostSize);

            System.out.println(request.getContentType());

            //获取action后面拼接的参数(如：http://www.baidu.com?q=android)

            Enumeration enumList = request.getParameterNames();

            while (enumList.hasMoreElements()) {

                String key = (String) enumList.nextElement();

                String value = request.getParameter(key);

                System.out.println(key + "=" + value);

            }

            //获取提交表单项目

            List listItems = upload.parseRequest(request);

            Iterator iterator = listItems.iterator();

            while (iterator.hasNext()) {

                FileItem item = (FileItem) iterator.next();

                //非普通表单项目

                if (!item.isFormField()) {

                    //获取扩展名

                    String ext = item.getName().substring(item.getName().lastIndexOf("."), item.getName().length());

                    String fileName = System.currentTimeMillis() + ext;

                    File dirFile = new File(contextPath + webDir + fileName);

                    if (!dirFile.exists()) {

                        dirFile.getParentFile().mkdirs();

                    }

                    item.write(dirFile);

                    System.out.println("fileName：" + item.getName() + "=====" + item.getFieldName() + " size： " + item.getSize());

                    System.out.println("文件已保存到： " + contextPath + webDir + fileName);

                    //响应客户端请求

                    out.print(basePath + webDir.replace("\\", "/") + fileName);

                    out.flush();

                } else {

                    //普通表单项目
                    // 显示表单内容。item.getString("UTF-8")可以保证中文内容不乱码

                    System.out.println("表单普通项目：" + item.getFieldName() + "=" + item.getString("UTF-8"));
                }

            }

        } catch (Exception e) {

            e.printStackTrace();

        } finally {

            out.close();

        }

    }

}

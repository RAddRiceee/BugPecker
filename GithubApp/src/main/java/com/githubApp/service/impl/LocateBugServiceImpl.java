package com.githubApp.service.impl;

import com.githubApp.service.LocateBugService;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

@Service
public class LocateBugServiceImpl implements LocateBugService {


    @Override
    public String initRepoKG(String repoInfo,String url) {

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(50000, TimeUnit.MILLISECONDS)
                .readTimeout(50000, TimeUnit.MILLISECONDS)
                .build();
        MediaType mediaType = MediaType.parse("application/json");
        RequestBody body = RequestBody.create(mediaType,repoInfo);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("content-type", "application/json")
                .addHeader("cache-control", "no-cache")
                .build();
        try {
            Response response = client.newCall(request).execute();
//            String result = response.body().string();
//            System.out.print(result);
            return response.body().string();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
//    public String getBugLocalization(String issueInfo) {
    public String getBugLocalization(String issueTitle,String issueBody,String commitId, String repoName) {

        Socket socket = new Socket();
        StringBuilder sb = new StringBuilder();
        try {
            // 初始化套接字，设置访问服务的主机和进程端口号，HOST是访问python进程的主机名称，可以是IP地址或者域名，PORT是python进程绑定的端口号
            socket = new Socket("202.120.40.28",4462);

            // 获取输出流对象
            OutputStream os = socket.getOutputStream();
            PrintStream out = new PrintStream(os);
            // 发送内容
//            out.println(issueInfo);
            out.println(repoName);
            out.println(issueTitle);
            out.println(issueBody);
            out.println(commitId);
            // 告诉服务进程，内容发送完毕，可以开始处理
            out.print("over");
            String tmp = null;
            // 获取服务进程的输入流
            InputStream is = socket.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(is,"utf-8"));
            // 读取内容
            while((tmp=br.readLine())!=null)
                sb.append(tmp).append('\n');
            System.out.print(sb);
        } catch (Exception e) {
            e.printStackTrace();
            return "error";
        }finally {
            try {if(socket!=null) socket.close();} catch (IOException e) {}
        }
        return sb.toString();
    }


}

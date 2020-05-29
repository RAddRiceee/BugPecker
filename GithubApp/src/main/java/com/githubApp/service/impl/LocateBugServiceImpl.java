package com.githubApp.service.impl;

import com.githubApp.service.LocateBugService;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

@Service
public class LocateBugServiceImpl implements LocateBugService {

    private final String IP = "202.120.40.28";

    private final int Port = 4462;

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
    public String getBugLocalization(String issueTitle,String issueBody,String commitId, String repoName) {

        Socket socket = new Socket();
        StringBuilder sb = new StringBuilder();
        try {
            socket = new Socket(IP,Port);
            OutputStream os = socket.getOutputStream();
            PrintStream out = new PrintStream(os);
            out.println(repoName);
            out.println(issueTitle);
            out.println(issueBody);
            out.println(commitId);
            out.print("over");
            String tmp = null;
            InputStream is = socket.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(is,"utf-8"));

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

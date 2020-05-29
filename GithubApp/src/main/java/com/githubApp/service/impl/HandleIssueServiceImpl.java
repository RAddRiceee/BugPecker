package com.githubApp.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.githubApp.model.*;
import com.githubApp.service.HandleIssueService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

@Service
public class HandleIssueServiceImpl implements HandleIssueService {

    @Autowired
    LocateBugServiceImpl locateBugServiceImpl;

    private final String CLIENT_ID = "Iv1.34b899201bef7d0d";
    private final String CLIENT_SECRET = "c89977d490d4893c8842b85a1a0ecc8864d3a589";
    private final String GITHUB_API_PREFIX = "https://api.github.com/";
    private final String GITHUB_URL_PREFIX = "https://github.com/";
    private final String initUrl = "http://202.120.40.28:5689/parser/KGWeb/initForGithubApp";
    private final String updateUrl = "http://202.120.40.28:5689/parser/KGWeb/versionUpdate";
    private final String resultUrl = "http://202.120.40.28:5655/";



    public String sendInfoToModel(String code) {
        String gitAuthUrl = "https://github.com/login/oauth/access_token?client_id=" + CLIENT_ID
                + "&client_secret=" + CLIENT_SECRET + "&code=" + code;
        String userName = "";
        try {
//            JSONObject tokenAsJson = JSON.parseObject(getUrlConnection(gitAuthUrl, null));
            String accessInfo = getUrlConnection(gitAuthUrl, null);
            if (accessInfo == null)
                return null;
            String accessToken = accessInfo.split("&")[0].split("=")[1];

            //get UserName
            String getUserNameUrl = GITHUB_API_PREFIX + "user";
            JSONObject userAsJson = JSON.parseObject(getUrlConnection(getUserNameUrl, accessToken));
            userName = (String) userAsJson.get("login");

            UserInfo userInfo = new UserInfo();
            userInfo.setAccessToken(accessToken);
            userInfo.setUserName(userName);
            saveInfoByFile(JSON.toJSONString(userInfo),"accessToken.txt");

            String installUrl = GITHUB_API_PREFIX + "user/installations";
            JSONObject installInfo = JSON.parseObject(getUrlConnection(installUrl, accessToken));
            int installId = (int) ((JSONObject) ((JSONArray) installInfo.get("installations")).get(0)).get("id");
            String accessRepoUrl = GITHUB_API_PREFIX + "user/installations/" + installId + "/repositories";
            JSONObject repoAccessInfo = JSON.parseObject(getUrlConnection(accessRepoUrl, accessToken));
            JSONArray repoListInfo = (JSONArray) repoAccessInfo.get("repositories");
            String[] repoInfoList = new String[repoListInfo.size()];
            int i = 0;
            for (Object temp : repoListInfo) {
                String repoName = (String) ((JSONObject) temp).get("name");
                String repoFullName = userName + "/" + repoName;
                String gitUrl = GITHUB_URL_PREFIX + repoFullName + ".git";
                RepoInfo repoInfo = new RepoInfo();
                repoInfo.setProjectID(repoName);
                HashMap<String, String> existProject = getExistProjects();
                if (existProject.containsKey(repoName)) {
                    repoInfo.setCommitid(existProject.get(repoName));
                } else {
                    String latestCommitId = getLatestCommitId(repoFullName);
                    repoInfo.setCommitid(latestCommitId);
                }
                repoInfo.setUrl(gitUrl);
                repoInfoList[i] = JSON.toJSONString(repoInfo);
                i++;
            }
            if (repoInfoList.length == 0)
                return null;
            String allRepoInfo = Arrays.toString(repoInfoList);
            return locateBugServiceImpl.initRepoKG(allRepoInfo, initUrl);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


    public void handleBugReport(String issueUrl, JSONObject json) {
        JSONObject issueJson = (JSONObject) json.get("issue");
        try {
            String issueTitle = (String) issueJson.get("title");
            String issueBody = (String) issueJson.get("body");
            int issueId = (int) issueJson.get("number");
            String gitUrl = (String) ((JSONObject) json.get("repository")).get("ssh_url");
            String userName = (String) ((JSONObject) json.get("sender")).get("login");
            String repoName = (String) ((JSONObject) json.get("repository")).get("name");
            String repoFullName = (String) ((JSONObject) json.get("repository")).get("full_name");

            String latestCommitId = null;
            HashMap<String, String> existProject = getExistProjects();
            if (existProject.containsKey(repoName)) {
                latestCommitId = existProject.get(repoName);
            } else {
                String[] titleInfo = issueTitle.split("&");
                //If commitId is assigned
                if (titleInfo.length > 1 && titleInfo[titleInfo.length - 1].split(":")[0].equals("commitId")) {
                    latestCommitId = titleInfo[titleInfo.length - 1].split(":")[1];
                } else {
                    latestCommitId = getLatestCommitId(repoFullName);
                }
            }
            RepoInfo repoInfo = new RepoInfo();
            repoInfo.setCommitid(latestCommitId);
            repoInfo.setProjectID(repoName);
            repoInfo.setUrl(gitUrl);
            String result = locateBugServiceImpl.initRepoKG(JSON.toJSONString(repoInfo), updateUrl);
            if (result == null) {
                return;
            }
            IssueInfo issueInfo = new IssueInfo();
            issueInfo.setProjectId(repoName);
            issueInfo.setCommitId(latestCommitId);
            issueInfo.setIssueTitle(issueTitle);
            issueInfo.setIssueBody(issueBody);
            String bugLocalization = locateBugServiceImpl.getBugLocalization(issueTitle, issueBody, latestCommitId, repoName);
            if (bugLocalization.equals("error") || bugLocalization.equals("500")) {
                editIssue(issueUrl, issueJson, "BugLocation Service is not in work");
                return;
            }
            BugInfo bugInfo = new BugInfo();
            bugInfo.setBugLocalization(bugLocalization);
            bugInfo.setIssueId(issueId);
            bugInfo.setIssueTitle(issueTitle);
            bugInfo.setRepoName(repoName);
            bugInfo.setUserName(userName);
            String bugInfoByIssue = JSON.toJSONString(bugInfo);
            saveInfoByFile(bugInfoByIssue, "bugInfo.txt");
            String locateInfo = "[Click here to get bug location]("+resultUrl+"result/" + userName + "/" + repoName + "/" + issueId + ")";

            editIssue(issueUrl, issueJson, locateInfo);
        }catch (Exception e){
            editIssue(issueUrl, issueJson, "BugLocation Service is not in work");
        }
    }

    private void editIssue(String issueUrl, JSONObject issue, String bugLocalization) {
        String userName = (String) ((JSONObject) issue.get("user")).get("login");
        String accessToken = getAccessToken(userName);
        try {
            URL gitUrl = new URL(issueUrl + "/comments");
            HttpURLConnection conn = (HttpURLConnection) gitUrl.openConnection();
            Comment comment = new Comment();
            comment.setBody(bugLocalization);
            String commentInfo = JSON.toJSON(comment).toString();

            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Charset", "UTF-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + accessToken);
            OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream(), "utf-8");
            wr.write(commentInfo);
            wr.flush();
            wr.close();
            conn.connect();

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {

//                BufferedInputStream inp = new BufferedInputStream(conn.getInputStream());
//                InputStreamReader in = new InputStreamReader(inp, Charset.forName("GBK"));
//                BufferedReader bufReader = new BufferedReader(in);
//                String tempStr = bufReader.readLine();
//                inp.close();
//                in.close();
            }
            conn.disconnect();

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public boolean isBugReport(JSONObject issue) {
        if (issue == null || issue.get("labels") == null) {
            return false;
        }
        List<JSONObject> labels = (List) issue.get("labels");
        if (labels == null) {
            return false;
        }
        for (JSONObject label : labels) {
            if (label.get("name").toString().contains("bug")) {
                return true;
            }
        }
        return false;
    }

    public String getLatestCommitId(String repoName) {
        String latestCommitUrl = GITHUB_API_PREFIX + "repos/" + repoName + "/commits?page=1&per_page=1";
        String commitInfos = getUrlConnection(latestCommitUrl, null);
        List<JSONObject> commitInfoList = JSONArray.parseArray(commitInfos, JSONObject.class);
        if (commitInfoList == null) {
            return null;
        }
        JSONObject commitInfo = commitInfoList.get(0);
        if (commitInfo == null) {
            return null;
        }
        return (String) commitInfo.get("sha");
    }


    private String getUrlConnection(String url, String accessToken) {
        try {
            URL gitUrl = new URL(url);
            HttpURLConnection conn = (HttpURLConnection) gitUrl.openConnection();
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setRequestMethod("GET");
            if (accessToken != null) {
                conn.setRequestProperty("Accept", "application/vnd.github.machine-man-preview+json");
                conn.setRequestProperty("Authorization", "Bearer " + accessToken);
                conn.setRequestProperty("Connection", "keep-alive");
            } else {
                conn.setRequestProperty("Accept", "*/*");
            }
            conn.connect();
            int responseCode = conn.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK) {

                BufferedInputStream inp = new BufferedInputStream(conn.getInputStream());
                InputStreamReader in = new InputStreamReader(inp, Charset.forName("UTF-8"));
                BufferedReader bufReader = new BufferedReader(in);
                String tempStr = bufReader.readLine();
                inp.close();
                in.close();
                return tempStr;
            }
            conn.disconnect();
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private HashMap<String, String> getExistProjects() {
        HashMap<String, String> existProject = new HashMap<String, String>();
        existProject.put("tomcat", "d9def07a87dc24ecebdeb2dd9c75b9ea1d0d5a30");
        existProject.put("birt", "2ce8dad75b644e552e0f07622ab70f27786d26b6");
        existProject.put("eclipse.platform.swt", "2ce8dad75b644e552e0f07622ab70f27786d26b6");
        existProject.put("org.aspectj", "2ce8dad75b644e552e0f07622ab70f27786d26b6");
        return existProject;
    }

    public BugLocator getBugDetail(String userName, String repoName, String issueId) {
        String[] bugInfo = getLocateInfoByFile(userName, repoName, issueId);
        if(bugInfo==null)
            return null;
        String bugLocalization = bugInfo[0];
        String issueTitle = bugInfo[1];
        if (bugLocalization.length() < 5)
            return null;
        String[] bugLocates = bugLocalization.substring(3, bugLocalization.length() - 2).split("\\), \\('");
        BugLocator bugLocator = new BugLocator();
        bugLocator.setRepoName(repoName);
        bugLocator.setIssueTitle(issueTitle);
        bugLocator.setIssueUrl(GITHUB_URL_PREFIX + userName + "/" + repoName + "/issues/" + issueId);
        List<MethodLocator> methodLocators = new ArrayList<>();
        for (String bug : bugLocates) {
            String[] probability = bug.split("',");
            MethodLocator methodLocator = new MethodLocator();
            methodLocator.setProbability(probability[1]);
            int repoNameLen = repoName.length();
            String fullPath = probability[0].split("-")[0].replace(".", "/").substring(repoNameLen + 1) + ".java";
            methodLocator.setFullPath(fullPath + "/" + probability[0].split("-")[1]);
            methodLocator.setMethodName(probability[0].split("-")[1]);
            methodLocator.setGitUrl(GITHUB_URL_PREFIX + userName + "/" + repoName + "/tree/master/" + fullPath);
            methodLocators.add(methodLocator);
        }
        bugLocator.setMethodLocatorList(methodLocators);
        return bugLocator;
    }


    private void saveInfoByFile(String info, String fileName) {
        File file = new File(fileName);
        try {
            if (!file.exists()) {
                file.createNewFile();
            }
            FileWriter fileWritter = new FileWriter(file.getName(), true);
            fileWritter.write(info);
            fileWritter.write(System.getProperty("line.separator"));
            fileWritter.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String getAccessToken(String userName) {
        File file = new File("accessToken.txt");
        InputStream is = null;
        Reader reader = null;
        BufferedReader bufferedReader = null;
        try {
            is = new FileInputStream(file);
            reader = new InputStreamReader(is);
            bufferedReader = new BufferedReader(reader);
            String line = null;
            while ((line = bufferedReader.readLine()) != null) {
                UserInfo userInfo = JSONObject.parseObject(line, UserInfo.class);
                if (userInfo.getUserName().equals(userName))
                    return userInfo.getAccessToken();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (null != bufferedReader)
                    bufferedReader.close();
                if (null != reader)
                    reader.close();
                if (null != is)
                    is.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    private String[] getLocateInfoByFile(String userName, String repoName, String issueId) {
        String[] result = new String[2];
        File file = new File("bugInfo.txt");
        InputStream is = null;
        Reader reader = null;
        BufferedReader bufferedReader = null;
        try {
            is = new FileInputStream(file);
            reader = new InputStreamReader(is);
            bufferedReader = new BufferedReader(reader);
            String line = null;
            while ((line = bufferedReader.readLine()) != null) {
                BugInfo bugInfo = JSONObject.parseObject(line, BugInfo.class);
                if (bugInfo.getUserName().equals(userName) && bugInfo.getRepoName().equals(repoName) && bugInfo.getIssueId() == Integer.valueOf(issueId)) {
                    result[0] = bugInfo.getBugLocalization();
                    result[1] = bugInfo.getIssueTitle();
                    return result;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (null != bufferedReader)
                    bufferedReader.close();
                if (null != reader)
                    reader.close();
                if (null != is)
                    is.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }
}

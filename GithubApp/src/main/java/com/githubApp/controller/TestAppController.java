package com.githubApp.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.githubApp.model.BugLocator;
import com.githubApp.model.CodeMetaData;
import com.githubApp.service.impl.HandleIssueServiceImpl;
import com.githubApp.service.impl.LocateBugServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;


@Controller
@RequestMapping("")
public class TestAppController {

    @Autowired
    HandleIssueServiceImpl handleIssueServiceImpl;
    @Autowired
    LocateBugServiceImpl locateBugServiceImpl;


    private static final Logger logger = LoggerFactory.getLogger(TestAppController.class);

    @RequestMapping("/hook")
    public void hook(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String temp = "";
        StringBuilder stringBuilder = new StringBuilder();
        req.setCharacterEncoding("utf-8");
        while ((temp = req.getReader().readLine()) != null) {
            stringBuilder.append(temp);
        }
        if (StringUtils.isEmpty(stringBuilder.toString())) {
            return;
        }
        String info = stringBuilder.toString();
        try {
            JSONObject json = JSON.parseObject(info);
            String action = (String) json.get("action");
//            if (action.equals("deleted") || action.equals("edited")) {
//                return;
//            } else if (action.equals("created")) {

            if (action.equals("opened")) {
                JSONObject issue = (JSONObject) json.get("issue");
                boolean isBuggy = handleIssueServiceImpl.isBugReport(issue);
                if (!isBuggy) {
                    return;
                }
                String issueUrl = (String) issue.get("url");
                handleIssueServiceImpl.handleBugReport(issueUrl, json);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @RequestMapping("/callback")
    public String index(@RequestParam String code, HttpServletRequest request) {

        String codeInfo = handleIssueServiceImpl.sendInfoToModel(code);
        List<JSONObject> codeDataList = JSONArray.parseArray(codeInfo, JSONObject.class);
        CodeMetaData combinedCodeData = new CodeMetaData();
        int versionNum = 0;
        int callRelNUm = 0;
        int methodNum = 0;
        int simRelNum = 0;
        if (codeDataList != null) {
            for (JSONObject data : codeDataList) {
                JSONObject codeMetaData = (JSONObject) data.get("codeMetaData");
                if (codeMetaData == null) {
                    continue;
                }
                versionNum += Integer.valueOf((String) codeMetaData.get("versionNum"));
                callRelNUm += Integer.valueOf((String) codeMetaData.get("callRelNUm"));
                methodNum += Integer.valueOf((String) codeMetaData.get("methodNum"));
                simRelNum += Integer.valueOf((String) codeMetaData.get("simRelNum"));
            }
        }
        combinedCodeData.setVersionNum(versionNum);
        combinedCodeData.setCallRelNUm(callRelNUm);
        combinedCodeData.setMethodNum(methodNum);
        combinedCodeData.setSimRelNum(simRelNum);
        request.setAttribute("codeData", combinedCodeData);
        return "index";
    }

    @RequestMapping("/result/{user}/{repo}/{issue}")
    public String showResult(@PathVariable(value = "user") String user, @PathVariable(value = "repo") String repo, @PathVariable(value = "issue") String issue, HttpServletRequest request) {
        BugLocator bugLocator = handleIssueServiceImpl.getBugDetail(user, repo, issue);
        request.setAttribute("result", bugLocator);
        return "result";
    }

}

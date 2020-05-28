package org.codeontology.version;

import com.alibaba.fastjson.JSON;
import org.apache.commons.lang3.StringUtils;
import org.codeontology.util.CommandUtil;
import org.codeontology.version.update.ParseDiffData;
import org.codeontology.version.update.UpdateInfo;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.File;
import java.io.IOException;
import java.util.*;

import org.codeontology.version.update.UpdateInfo.Operation;

public class GitUtil {

    /**
     * 寻找不晚于该commitId的已提取的最新commitId
     */
    public static String getLatestCommitId(String projectPath, List<String> extractCommitIdList, String queryCommitId){
        List<String> fullCommitIdList = getFullCommitIdListInOrder(projectPath);
        int index = fullCommitIdList.indexOf(queryCommitId);
        if(index<0){
            return null;
        }
        for(int i = extractCommitIdList.size()-1;i>=0;i--){
            if(fullCommitIdList.indexOf(extractCommitIdList.get(i)) >= index){
                return extractCommitIdList.get(i);
            }
        }
        return extractCommitIdList.get(0);
    }

    /**
     * 根据git log获取完整commitId列表, 按倒序
     * @param projectPath
     * @return
     */
    public static List<String> getFullCommitIdListInOrder(String projectPath){
        //git log
        List<String> commitOrderList = new ArrayList<>();
        Git git = null;
        try {
            git=Git.open(new File(projectPath));
            Iterable<RevCommit> iterable=git.log().call();
            Iterator<RevCommit> iter=iterable.iterator();
            while (iter.hasNext()) {
                RevCommit commit = iter.next();
                String commitID=commit.getName();  //这个应该就是提交的版本号
                commitOrderList.add(commitID);
//                String email=commit.getAuthorIdent().getEmailAddress();
//                String name=commit.getAuthorIdent().getName();  //作者
//                String commitEmail=commit.getCommitterIdent().getEmailAddress();//提交者
//                String commitName=commit.getCommitterIdent().getName();
//                int time=commit.getCommitTime();
//                String fullMessage=commit.getFullMessage();
//                String shortMessage=commit.getShortMessage();  //返回message的firstLine
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return commitOrderList;
    }

    /**
     * 获取新版本的变更信息
     */
    public static UpdateInfo getVersionUpdateInfo(String projectPath, String lastCommitId, String newCommitId){
        UpdateInfo updateInfo = new ParseDiffData().getVersionUpdateInfo(projectPath, lastCommitId, newCommitId);
        //处理方法签名中类似List<Object>的情况
        Map<String, Map<Operation, List<String>>> methodUpdateMap = updateInfo.getMethodUpdateMap();
        for(Map.Entry<String, Map<Operation, List<String>>> entry1 : updateInfo.getMethodUpdateMap().entrySet()){
            for(Map.Entry<Operation, List<String>> entry2 : entry1.getValue().entrySet()){
                for(int i=0;i<entry2.getValue().size();i++){
                    String method = entry2.getValue().get(i);
                    if(method != null && method.contains("<")) {
                        while (method.contains("<")) {
                            method = method.replace(method.substring(method.indexOf("<"), method.lastIndexOf(">") + 1), "");
                        }
                        entry2.getValue().remove(i);
                        entry2.getValue().add(i, method);
                    }
                }
            }
        }
        return updateInfo;
    }

    public static void main(String[] args){
        UpdateInfo updateInfo = GitUtil.getVersionUpdateInfo("/Users/hsz/projects/BugLocCodeBigData/defects4j/Math/1b/", "876d133334e8dde309cc11f884c0dd4cc9ce530e","924b6d76a2d44baf858c3aee46ab1439c9641959");
        System.out.println(JSON.toJSONString(updateInfo));
    }

    /**
     * git clone并checkout
     */
    public static boolean gitCloneAndCheckout(String gitURL, String projectPath, String commitId){
        List<String> commandList = new ArrayList<>();
        commandList.add("mkdir -p " + projectPath);
        commandList.add("git clone " + gitURL + " " + projectPath);
        commandList.add("cd " + projectPath);
        commandList.add("git checkout .");
        commandList.add("git checkout " + commitId);
        commandList.add("git checkout . && git clean -xdf");
        String[] command = {"bash","-c", StringUtils.join(commandList, ";")};
        try {
            String result = CommandUtil.run(command);
            return !CommandUtil.isError(result);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * git checkout
     */
    public static boolean gitCheckout(String projectPath, String commitId){
        String[] command = {"bash","-c", "cd "+ projectPath +";git checkout .;git checkout " + commitId + ";git checkout . && git clean -xdf"};
        try {
            String result = CommandUtil.run(command);
            return !CommandUtil.isError(result);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 找到某个commit的上一个commit,紧跟着当前commit的10bit的完整commitId号
     */
    public static List<String> findPreCommit(String projectPath, String commitId){
        String[] command = {"bash","-c", "cd "+ projectPath +";git log -2 --pretty='commit: %H - %s' " + commitId};
        try {
            String result = CommandUtil.runWithoutPrint(command);
            String[] lines =result.split("\\\n");
            List<String> twoCommitId = new ArrayList<>();
            for(String line : lines){
                if(line.startsWith("commit: ")){
                    String tmp = line.replace("commit: ","");
                    if(commitId.length()<=10){
                        twoCommitId.add(tmp.split(" - ")[0].substring(0,10));
                    }else{
                        twoCommitId.add(tmp.split(" - ")[0]);
                    }
                }
                if(twoCommitId.size()>=2){
                    break;
                }
            }
            Collections.reverse(twoCommitId);
            return twoCommitId;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 找到某个commit的提交时间,紧跟着当前commit的10bit的完整commitId号
     */
    public static String findCommitTime(String projectPath, String commitId){
        String[] command = {"bash","-c", "cd "+ projectPath +";git show --stat --date=format:'%Y-%m-%d %H:%M:%S' " + commitId};
        try {
            String result = CommandUtil.runWithoutPrint(command);
            String[] lines =result.split("\\\n");
            for(String line : lines){
                if(line.startsWith("Date:")){
                    String time = line.replace("Date:","");
                    return time.trim();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static UpdateInfo version11(){
        UpdateInfo updateInfo = emptyUpdateInfo();

        String class1 = "org.sjtu.Util";
        updateInfo.getClassUpdateMap().put(Operation.MODIFY, Arrays.asList(class1));

        Map<Operation, List<String>> classMethodUpdateMap1 = new HashMap<>();
        classMethodUpdateMap1.put(Operation.MODIFY, Arrays.asList("process(java.lang.String)"));
        updateInfo.getMethodUpdateMap().put(class1, classMethodUpdateMap1);

        return updateInfo;
    }

    private static UpdateInfo version12(){
        UpdateInfo updateInfo = emptyUpdateInfo();

        String class1 = "org.sjtu.Util";
        String class2 = "org.sjtu.HelloWorld";
        updateInfo.getClassUpdateMap().put(Operation.MODIFY, Arrays.asList(class1, class2));

        Map<Operation, List<String>> classMethodUpdateMap1 = new HashMap<>(), classMethodUpdateMap2 = new HashMap<>();
        classMethodUpdateMap1.put(Operation.MODIFY, Arrays.asList("process(java.lang.String)"));
        updateInfo.getMethodUpdateMap().put(class1, classMethodUpdateMap1);

        classMethodUpdateMap2.put(Operation.MODIFY, Arrays.asList("main(java.lang.String[])"));
        updateInfo.getMethodUpdateMap().put(class2, classMethodUpdateMap2);

        return updateInfo;
    }

    private static UpdateInfo version13(){
        UpdateInfo updateInfo = emptyUpdateInfo();

        String class1 = "org.sjtu.Add";
        String class2 = "org.sjtu.HelloWorld";

        updateInfo.getClassUpdateMap().put(Operation.ADD, Arrays.asList(class1));
        updateInfo.getClassUpdateMap().put(Operation.MODIFY, Arrays.asList(class2));

        Map<Operation, List<String>> fieldClassUpdateMap = new HashMap<>();
        fieldClassUpdateMap.put(Operation.ADD, Arrays.asList("addProperty"));
        updateInfo.getMethodUpdateMap().put(class2, fieldClassUpdateMap);

        return updateInfo;
    }

    private static UpdateInfo version14(){
        //更新类名和方法名
        UpdateInfo updateInfo = emptyUpdateInfo();

        String class1 = "org.sjtu.Add1";

        updateInfo.getClassUpdateMap().put(Operation.MODIFY, Arrays.asList(class1));

        updateInfo.getClassNameUpdateMap().put("org.sjtu.Add", class1);

        Map<String, String> classMethodNameUpdateMap = new HashMap<>();
        classMethodNameUpdateMap.put("add(int, int)", "add1(int, int)");
        updateInfo.getMethodNameUpdateMap().put(class1, classMethodNameUpdateMap);

        return updateInfo;
    }

    private static UpdateInfo version17(){
        UpdateInfo updateInfo = emptyUpdateInfo();

        String class1 = "org.sjtu.HelloWorld";

        updateInfo.getClassUpdateMap().put(Operation.MODIFY, Arrays.asList(class1));

        Map<Operation, List<String>> classMethodUpdateMap1 = new HashMap<>();
        classMethodUpdateMap1.put(Operation.MODIFY, Arrays.asList("main(java.lang.String[])"));
        updateInfo.getMethodUpdateMap().put(class1, classMethodUpdateMap1);

        return updateInfo;
    }

    public static UpdateInfo emptyUpdateInfo(){
        UpdateInfo updateInfo = new UpdateInfo();
        Map<Operation, List<String>> classUpdateMap = new HashMap<>();
        Map<String, Map<Operation, List<String>>> methodUpdateMap = new HashMap<>(), fieldUpdateMap = new HashMap<>();
        Map<String, String> classNameUpdateMap = new HashMap<>();
        Map<String, Map<String, String>> methodNameUpdateMap = new HashMap<>();
        updateInfo.setClassUpdateMap(classUpdateMap);
        updateInfo.setMethodUpdateMap(methodUpdateMap);
        updateInfo.setFieldUpdateMap(fieldUpdateMap);
        updateInfo.setClassNameUpdateMap(classNameUpdateMap);
        updateInfo.setMethodNameUpdateMap(methodNameUpdateMap);
        return updateInfo;
    }
}

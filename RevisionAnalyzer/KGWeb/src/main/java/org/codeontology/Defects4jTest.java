package org.codeontology;

import com.alibaba.fastjson.JSON;
import com.opencsv.CSVReader;
import okhttp3.*;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.codeontology.util.CommandUtil;
import org.codeontology.version.GitUtil;
import org.joda.time.Period;

import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 测试neo4j数据集的测试脚本，把6个项目的每个bug的fix前后版本都跑一遍
 */
public class Defects4jTest {

    public static final MediaType JSONType = MediaType.parse("application/json; charset=utf-8");
    public static OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(2400, TimeUnit.SECONDS)//40min
            .readTimeout(2400, TimeUnit.SECONDS)
            .build();
    public static SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public static void main(String[] args) throws Exception{
        //defects4j数据集
//        for(String project : Rest.defects4jProjects){
//            List<String> commitIDList = reaDefects4jCommitIDListByFile(project);
//            runExtractAndUpdate(project, commitIDList);
//        }
        //fse2014数据集
        for(String project : Rest.fse2014Projects){
            if(!project.equals("eclipse.platform.swt")){//eclipse.platform.swt birt
                continue;
            }
            String projectPath = "/Users/hsz/projects/BugLocCodeBigData/FSE2014/" + project;
            List<String> csvCommitIDList = readFSECommitIDListByCsvFile(project);
            runExtractAndUpdate(project, csvCommitIDList);
//            Map<String, List<String>> csvCommitIDMap = readFSECommitIDMapByCsv(project);
//            runExtractAndUpdateDividedByBugRep(project, csvCommitIDMap);
        }
    }

    public static void runExtractAndUpdateDividedByBugRep(String project, Map<String, List<String>> commitIDMap) throws Exception{
        System.out.println("Divide By BugReport - Project " +project + ":");
        Defects4jTest defects4jTest = new Defects4jTest();
        System.out.println("commitIDMap size: " + commitIDMap.size());
        if(commitIDMap==null || commitIDMap.size()==0){
            System.out.println("commitIDList is empty.");
            return;
        }

        //maven-load
//        System.out.println("Maven loading...");
//        List<String> keyList = new ArrayList<>(commitIDMap.keySet());
//        String lastCommitId = commitIDMap.get(keyList.get(keyList.size()-1)).get(1);
//        Param param = defects4jTest.new Param(project, lastCommitId, null, null, "--do-not-extract");
//        httpPost("http://localhost:8080/KGWeb/extractNewProject", JSON.toJSONString(param));

        int i = 0;
        for(Map.Entry<String, List<String>> entry : commitIDMap.entrySet()){
            i++;
            if(i < 3036){
                continue;
            }
            System.out.println("Bug rep index : "+ i +", bug_id "+ entry.getKey() +":");
            String projectId = project + File.separator + entry.getKey();
            String commitId = entry.getValue().get(0);

            //extract
            System.out.println("Extracting...");
            Param param = defects4jTest.new Param(projectId, commitId, null, null, null);
            httpPost("http://localhost:8080/KGWeb/extractNewProject", JSON.toJSONString(param));

            //versionUpdate
            System.out.println("VersionUpdateing: ");
            String updateCommitId = entry.getValue().get(1);
            param = defects4jTest.new Param(projectId, updateCommitId, null, null, null);
            httpPost("http://localhost:8080/KGWeb/versionUpdate", JSON.toJSONString(param));
        }
        System.out.println("Divide By BugReport - Project " +project + " process over.");
    }

    public static void runExtractAndUpdate(String project, List<String> commitIDList) throws Exception{
        System.out.println("Project " +project + ":");
        Defects4jTest defects4jTest = new Defects4jTest();
        System.out.println("commitIDList size: " + commitIDList.size());
        if(CollectionUtils.isEmpty(commitIDList)){
            System.out.println("commitIDList is empty.");
            return;
        }

        //maven-load
        System.out.println("Maven loading...");
        Param param = defects4jTest.new Param(project, commitIDList.get(commitIDList.size()-1), null, null, "--do-not-extract");
//        httpPost("http://localhost:8080/KGWeb/extractNewProject", JSON.toJSONString(param));

        //extract
        System.out.println("Extracting...");
        String commitId = commitIDList.get(0);
        param = defects4jTest.new Param(project, commitId, null, null, null);
//        httpPost("http://localhost:5689/new_parser/KGWeb/extractNewProject", JSON.toJSONString(param));
//        httpPost("http://localhost:8080/KGWeb/extractNewProject", JSON.toJSONString(param));

        //versionUpdate
        for(int i =3917; i< commitIDList.size(); i++){
            String updateCommitId = commitIDList.get(i);
            System.out.println("VersionUpdateing " + i +": commitId= " + updateCommitId);
            param = defects4jTest.new Param(project, updateCommitId, null, null, null);
//            httpPost("http://localhost:5689/new_parser/KGWeb/versionUpdate", JSON.toJSONString(param));
            httpPost("http://localhost:8080/KGWeb/versionUpdate", JSON.toJSONString(param));
        }
    }


    public static List<String> reaDefects4jCommitIDListByFile(String project){
        List<String> commitIDList = new ArrayList<>();
        try {
            String path = "/Users/hsz/projects/external/defects4j/framework/projects/" + project + "/commit-db";
            File file = new File(path);
            BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
            String strLine = null;
            while(null != (strLine = bufferedReader.readLine())){
                String[] split = strLine.split(",");
                String commitId1 = split[1], commitId2 = split[2];
                if(!commitIDList.contains(commitId2)) {
                    commitIDList.add(commitId2);
                }
//                if(!commitIDList.contains(commitId1)) {
//                    commitIDList.add(commitId1);
//                }
            }

            //逆序
            Collections.reverse(commitIDList);
        }catch(Exception e){
            e.printStackTrace();
        }

        return commitIDList;
    }

    public static Map<String, List<String>> readFSECommitIDMapByCsv(String project){
        List<String> fileNameList = new ArrayList<>(Arrays.asList("aspectj","tomcat","jdt","swt","birt","tomcat"));
        Map<String, List<String>> bugRepcommitIDMap = new LinkedHashMap<>();
        try {
            String path = "/Users/hsz/zhs/sjtu/缺陷定位/dataset/fse2014_mlevel_processed/" + fileNameList.get(Rest.fse2014Projects.indexOf(project)) + "_sorted.csv";
            Reader in = new FileReader(path);
            Iterable<CSVRecord> records = CSVFormat.EXCEL.withHeader().parse(in);
            int size = 0;

            for(CSVRecord record : records){
                String commitId = record.get("commit_id");
                String preCommitId = record.get("pre_commit_id");
                String bugRepId;
                try {
                    bugRepId = record.get("bug_id");
                }catch (Exception e){
                    bugRepId = record.get("index");
                }
                List<String> commitIDList = Arrays.asList(preCommitId, commitId);
                bugRepcommitIDMap.put(bugRepId, commitIDList);
                size++;
            }
            System.out.println("csv size: "+size);
            in.close();
        }catch (Exception e){
            e.printStackTrace();
        }
        return bugRepcommitIDMap;
    }

    public static List<String> readFSECommitIDListByCsvFile(String project){
        List<String> fileNameList = new ArrayList<>(Arrays.asList("aspectj","tomcat","jdt","swt","birt","tomcat"));
        List<String> commitIDList = new ArrayList<>();
        try {
            String path = "/Users/hsz/zhs/sjtu/缺陷定位/dataset/fse2014_mlevel_processed/" + fileNameList.get(Rest.fse2014Projects.indexOf(project)) + "_sorted.csv";
            Reader in = new FileReader(path);
            Iterable<CSVRecord> records = CSVFormat.EXCEL.withHeader().parse(in);
            int size = 0;
            for (CSVRecord record : records) {
                String commitId = record.get("commit_id");
                String preCommitId = record.get("pre_commit_id");
                if(!StringUtils.isEmpty(preCommitId) && !commitIDList.contains(preCommitId)){
                    commitIDList.add(preCommitId);
                }
                if(!StringUtils.isEmpty(commitId) && !commitIDList.contains(commitId)){
                    commitIDList.add(commitId);
                }
                size++;
            }
            System.out.println("csv size: "+size);
            in.close();
//            Collections.reverse(commitIDList);
        }catch (Exception e){
            e.printStackTrace();
        }
        return commitIDList;
    }

    /**
    public static void main(String[] args){
        try {
            String path = "/Users/hsz/zhs/sjtu/缺陷定位/dataset/swt_sorted.csv";
            String path1 = "/Users/hsz/zhs/sjtu/缺陷定位/dataset/fse2014_mlevel_processed/swt_sorted.csv";
            List<String> idList = new ArrayList<>();
            List<String> idList1 = new ArrayList<>();
            Reader in = new FileReader(path);
            Iterable<CSVRecord> records = CSVFormat.EXCEL.withHeader().parse(in);
            int size = 0;
            for (CSVRecord record : records) {
                idList.add(record.get("bug_id"));
                size++;
            }
            System.out.println("csv size: "+size);
            in.close();

            in = new FileReader(path1);
            records = CSVFormat.EXCEL.withHeader().parse(in);
            size = 0;
            for (CSVRecord record : records) {
                idList1.add(record.get("index"));
                size++;
            }
            System.out.println("csv size: "+size);
            in.close();

            List<String> diff=new ArrayList<>();
            for(int i=0;i<idList.size();i++){
                if(!idList.get(i).equals(idList1.get(i))){
                    diff.add(idList.get(i)+" "+idList1.get(i));
                }
            }

            System.out.println("diff size: "+diff.size());
            System.out.println(diff);
//            Collections.reverse(commitIDList);
        }catch (Exception e){
            e.printStackTrace();
        }
    }*/

    public static List<String> reaFSECommitIDListByFile(String project, String projectPath){
        List<String> fileNameList = new ArrayList<>(Arrays.asList("Aspectj","Eclipse_Platform_UI","JDT","SWT","Birt","Tomcat"));
        List<String> commitIDList = new ArrayList<>();
        try {
            String path = "/Users/hsz/zhs/sjtu/缺陷定位/dataset/951967/dataset/" + fileNameList.get(Rest.fse2014Projects.indexOf(project)) + ".xlsx";
            File file = new File(path);
            //创建工作簿
            XSSFWorkbook xssfWorkbook = new XSSFWorkbook(new FileInputStream(file));
            //读取第一个工作表(这里的下标与list一样的，从0开始取，之后的也是如此)
            XSSFSheet sheet = xssfWorkbook.getSheetAt(0);
            for(int i=sheet.getLastRowNum();i>0;i--){
                XSSFRow row = sheet.getRow(i);
                String commitId2 = row.getCell(7).toString();
                //根据git log找到前一个commit
                List<String> twoCommit = GitUtil.findPreCommit(projectPath, commitId2);
                String commitId1 = twoCommit.get(0);
                commitId2 = twoCommit.get(1);
                if(indexOfCommit(commitIDList, commitId1)<0){
                    commitIDList.add(commitId1);
                }
                if(indexOfCommit(commitIDList, commitId2)<0) {
                    commitIDList.add(commitId2);
                }
            }

            //逆序
//            Collections.reverse(commitIDList);
        }catch(Exception e){
            e.printStackTrace();
        }

        return commitIDList;
    }

    private static int indexOfCommit(List<String> commitIDList, String commitID){
        for(int i=commitIDList.size()-1;i>=0;i--){
            if(commitIDList.get(i).substring(0,7).equals(commitID.substring(0,7))){
                return i;
            }
        }
        return -1;
    }

    private static String httpPost(String url, String json) throws IOException {
        long start = System.currentTimeMillis();

        System.out.println("Post: url= "+url+" , param= " +json);
        RequestBody body = RequestBody.create(JSONType, json);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();
        Response response = client.newCall(request).execute();

        String result = response.body().string();
        response.close();

        System.out.println("Result: " + result);
        if(result.contains("fail")){
            throw  new RuntimeException("request fail.");
        }
        long end = System.currentTimeMillis();
        Period period = new Period(start, end);
        System.out.println("successfully in " + CodeOntology.formatter.print(period) + ", now is " + df.format(new Date()));
        return result;
    }

    public class Param{
        private String projectID;
        private String commitId;
        private String gitURL;
        private String projectPath;
        private String extra;


        public Param(String projectID, String commitId, String projectPath, String gitURL, String extra) {
            this.projectID = projectID;
            this.commitId = commitId;
            this.gitURL = gitURL;
            this.projectPath = projectPath;
            this.extra = extra;
        }

        public String getProjectID() {
            return projectID;
        }

        public void setProjectID(String projectID) {
            this.projectID = projectID;
        }

        public String getCommitId() {
            return commitId;
        }

        public void setCommitId(String commitId) {
            this.commitId = commitId;
        }

        public String getGitURL() {
            return gitURL;
        }

        public void setGitURL(String gitURL) {
            this.gitURL = gitURL;
        }

        public String getProjectPath() {
            return projectPath;
        }

        public void setProjectPath(String projectPath) {
            this.projectPath = projectPath;
        }

        public String getExtra() {
            return extra;
        }

        public void setExtra(String extra) {
            this.extra = extra;
        }
    }
}

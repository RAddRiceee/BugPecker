package org.codeontology;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.codeontology.neo4j.*;
import org.codeontology.util.FileUtil;
import org.codeontology.util.RealtimeProcess;
import org.codeontology.version.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Path("/KGWeb")
public class Rest {
    @Context
    UriInfo uriInfo;

    //TODO projectDir
    private static String projectDir = "/Users/hsz/projects/BugLocCodeBigData/";
    private static String jsonDir = "/Users/hsz/projects/BugLocJson/";
    private static SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public static List<String> defects4jProjects = new ArrayList<>(Arrays.asList("Time","Math","Lang","Closure","Mockito","Chart"));
    public static List<String> fse2014Projects = new ArrayList<>(Arrays.asList("org.aspectj","eclipse.platform.ui","eclipse.jdt.ui","eclipse.platform.swt","birt","tomcat"));

    @POST
    @Path("/ping")
    @Produces(MediaType.APPLICATION_JSON)
    public String ping(@Context HttpServletRequest request){
        JSONObject json = new JSONObject();
        json.put("result", "success");
        return json.toJSONString();
    }

    /**
     * initializes when users install the Github App authorization
     * @param request
     * @return
     */
    @POST
    @Path("/initForGithubApp")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public String initForGithubApp(@Context HttpServletRequest request) {
        JSONArray jsonArray = JSONArray.parseArray(getStringFromRequest(request));
        JSONArray returnJson = new JSONArray();
        for(int i=0; i< jsonArray.size();i++){
            JSONObject json = jsonArray.getJSONObject(i);
            String projectID = json.getString("projectID");
            String commitId = json.getString("commitid");
            String gitURL = json.getString("url");
            String oneResult = extractNewProject(projectID, commitId, gitURL, null, null, true);
            returnJson.add(JSONObject.parseObject(oneResult));
        }

        return returnJson.toJSONString();
    }

    /**
     * build a revision graph for a new given project.
     * steps：git clone first，and build the revision graph
     *
     * json body parameters:
     * projectID: the project name of the given project
     * commitId: the commitId corresponding to a specific version
     * gitURL: git repository url of the given project.
     * @return
     */
    @POST
    @Path("/extractNewProject")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public String extractNewProject(@Context HttpServletRequest request) {
        JSONObject json = getJSONFromRequest(request);
        String projectID = json.getString("projectID");
        String commitId = json.getString("commitId");
        String gitURL = json.getString("gitURL");
        String projectPath = json.getString("projectPath");
        String extra = json.getString("extra");

        return extractNewProject(projectID, commitId, gitURL, projectPath, extra, false);
    }

    private String extractNewProject(String projectID, String commitId, String gitURL, String projectPath, String extra, boolean retMessage){
        JSONObject json = new JSONObject();

        //in SWT, projectID=projectName/bug_id
        String projectName = projectID;
        if(projectID.contains(File.separator)){
            projectName = projectID.substring(0, projectID.indexOf(File.separator));
        }

        if(StringUtils.isEmpty(projectID) || StringUtils.isEmpty(commitId)){
            json.put("result", "input error");
            return json.toJSONString();
        }
        if(StringUtils.isEmpty(projectPath)) {
            projectPath = projectDir + projectName;
            projectPath = customizationProjectPath(projectName, projectPath);
        }

        //if projectPath(the local path of given project's source code) not exist，git clone and checkout
        if(!new File(projectPath).exists()){
            if(!GitUtil.gitCloneAndCheckout(gitURL, projectPath, commitId)) {
                json.put("result", "git clone error");
                return json.toJSONString();
            }
        }else{
            if(!GitUtil.gitCheckout(projectPath, commitId)){
                json.put("result", "commitID not exist");
                return json.toJSONString();
            }
        }

        String arg = String.format("-p %s -i %s --do-not-download --commit-id %s", projectID, projectPath, commitId);
        if(!StringUtils.isEmpty(extra)){
            arg = arg + " " + extra;
        }
        arg = customizationArgs(projectName, arg);

        json = new JSONObject();

        Neo4jConfig.configDatabaseChoose(projectID);
        if(Neo4jLogger.getInstance().getCommitIdList().size() <= 0){
            try {
                CodeOntology.main(arg.split(" "));
                json.put("result", "success");
            }catch (Exception e){
                json.put("result", "fail");
                json.put("message", e.getClass().getName() + ": " + e.getMessage());
            }
        }
        //return the meta information of the revision graph.
        if (retMessage) {
            CodeMetaData codeMetaData = Neo4jQuery.getCodeMetaData(projectID);
            json.put("codeMetaData", codeMetaData);
        }
        return json.toJSONString();
    }

    /**
     * update the given project's existed revision graph with a new version's source code.
     * update operations contain: add new entities and relations, or build update relations between old entities and new version entities, or mark as deleted some old entities.
     * steps：git checkout the given version，and update with new version.
     *
     * json body parameters:
     * projectID: the project name of the given project
     * commitId: the commitId corresponding to the new version
     */
    @POST
    @Path("/versionUpdate")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public String VersionUpdate(@Context HttpServletRequest request) {
        JSONObject json = getJSONFromRequest(request);
        String projectID = json.getString("projectID");
        String commitId = json.getString("commitId");
        if(StringUtils.isEmpty(commitId)){
            commitId = json.getString("commitid");
        }
        String projectPath = json.getString("projectPath");
        String extra = json.getString("extra");

        String projectName = projectID;
        if(projectID.contains(File.separator)){
            projectName = projectID.substring(0, projectID.indexOf(File.separator));
        }

        if(StringUtils.isEmpty(projectID) || StringUtils.isEmpty(commitId)){
            json.put("result", "input error");
            return json.toJSONString();
        }
        if(StringUtils.isEmpty(projectPath)) {
            projectPath = projectDir + projectName;
            projectPath = customizationProjectPath(projectName, projectPath);
        }

        //git checkout
        if(!GitUtil.gitCheckout(projectPath, commitId)){
            json.put("result", "commitID not exist");
            return json.toJSONString();
        }

        String arg = String.format("-p %s -i %s --do-not-download --commit-id %s --is-version-update", projectID, projectPath, commitId);
        if(!StringUtils.isEmpty(extra)){
            arg = arg + " " + extra;
        }
        arg = customizationArgs(projectName, arg);

        json = new JSONObject();

        Neo4jConfig.configDatabaseChoose(projectID);
        if(Neo4jLogger.getInstance().getCommitIdList().size() <= 0){
            json.put("result", "fail");
            json.put("message", "this projectID has not been extracted");
            return json.toJSONString();
        }
        try {
            CodeOntology.main(arg.split(" "));
            json.put("result", "success");
        }catch (Exception e){
            json.put("result", "fail");
            json.put("message", e.getClass().getName() + ": " + e.getMessage());
        }
        return json.toJSONString();
    }

    /**
     * obtain entities and associated relations, which corresponds to the specific version of the given project's source code, from the revision graph of a specific project.
     *
     * json body parameters:
     * projectID: the project name of the given project
     * commitId: the commitId corresponding to the query version
     *
     * @return the json form string of org.codeontology.version.VersionInfo Class
     */
    @POST
    @Path("/versionInfo")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public String querySpecificVersionInfo(@Context HttpServletRequest request, @Context HttpServletResponse response) {
        JSONObject json = getJSONFromRequest(request);
        String projectID = json.getString("projectID");
        String commitId = json.getString("commitId");
        String projectPath = json.getString("projectPath");

        //projectName区别于projectID，在数据量大的项目如swt和birt中，projectID=projectName/bug_id
        String projectName = projectID;
        if(projectID.contains(File.separator)){
            projectName = projectID.substring(0, projectID.indexOf(File.separator));
        }

        if(StringUtils.isEmpty(projectID) || StringUtils.isEmpty(commitId)){
            json.put("result", "input error");
            return json.toJSONString();
        }
        if(StringUtils.isEmpty(projectPath)) {
            projectPath = projectDir + projectName;
            projectPath = customizationProjectPath(projectName, projectPath);
        }

        json = new JSONObject();

        try {
            VersionInfo versionInfo = VersionAccess.querySpecificVersionInfo(projectPath, projectID, commitId, false);

//            String jsonFilePath = jsonDir + projectID.replace(File.separator, "_")+"_"+commitId+"_versionInfo_"+System.currentTimeMillis()+".json";
//            FileUtil.saveJsonToFile(JSON.toJSONString(versionInfo), jsonFilePath);
//            writeFileToResponse(jsonFilePath, response);
////            json.put("jsonFilePath", jsonFilePath);

            return JSON.toJSONString(versionInfo);
        }catch (Exception e){
            json.put("result", "fail");
            json.put("message", e.getClass().getName() + ": " + e.getMessage());
        }
        return json.toJSONString();
    }


    /**
     * obtain the update message between a given version and its previous version
     *
     * json body parameters:
     * projectID: the project name of the given project
     * commitId: the commitId corresponding to the target version
     *
     * @return the json form string of org.codeontology.version.VersionDiffInfo Class
     */
    @POST
    @Path("/versionDiffInfo")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public String querySpecificVersionDiffInfo(@Context HttpServletRequest request, @Context HttpServletResponse response) {
        JSONObject json = getJSONFromRequest(request);
        String projectID = json.getString("projectID");
        String commitId = json.getString("commitId");
        if(StringUtils.isEmpty(projectID) || StringUtils.isEmpty(commitId)){
            json.put("result", "input error");
            return json.toJSONString();
        }

        json = new JSONObject();
        try {
            VersionDiffInfo versionDiffInfo = VersionDiffAccess.queryVersionDiffInfo(projectID, commitId);
            String jsonFilePath = jsonDir + projectID.replace(File.separator, "_")+"_"+commitId+"_versionDiffInfo_"+System.currentTimeMillis()+".json";
            FileUtil.saveJsonToFile(JSON.toJSONString(versionDiffInfo), jsonFilePath);
//            writeFileToResponse(jsonFilePath, response);
            json.put("jsonFilePath", jsonFilePath);
        }catch (Exception e){
            json.put("result", "fail");
            json.put("message", e.getClass().getName() + ": " + e.getMessage());
        }
        return json.toJSONString();
    }

    /**
     * query the revision graph of a specific project through a Cypher statement.
     *
     * json body parameters:
     * projectID: the project name of the specific project
     * statement: cypher statement
     */
    @POST
    @Path("/cypherQuery")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public String cypherQuery(@Context HttpServletRequest request) {
        JSONObject json = getJSONFromRequest(request);
        String projectID = json.getString("projectID");
        String statement = json.getString("statement");
        if(StringUtils.isEmpty(projectID) || StringUtils.isEmpty(statement)){
            json.put("result", "input error");
            return json.toJSONString();
        }
        Neo4jConfig.configDatabaseChoose(projectID);
        Map<String, List<Object>> printMap = Neo4jQuery.cypherQuery(statement);
        return JSON.toJSONString(printMap);
    }

    /**
     * download file
     *
     * json body parameters:
     * path: file path
     */
    @GET
    @Path("/download")
    @Consumes(MediaType.APPLICATION_JSON)
    public void download(@Context HttpServletRequest request, @Context HttpServletResponse response) {
        JSONObject json = new JSONObject();
        String path = request.getParameter("path");
        if (StringUtils.isEmpty(path)) {
            json.put("result", "input error");
            writeDataToResponse(JSON.toJSONString(json), response);
        }
        writeFileToResponse(path, response);
    }

    private static void writeFileToResponse(String filePath, HttpServletResponse response){
        try {
            File file = new File(filePath);
            response.setHeader("content-disposition", "attachment;filename=" + file.getName());
            response.setContentType("application/octet-stream; charset=utf-8");
            response.setContentLength((int) file.length());
            // write to response stream
            outStream(new FileInputStream(file), response.getOutputStream());
        }catch (Exception e) {
            System.out.println( e.getMessage());

            JSONObject json = new JSONObject();
            json = new JSONObject();
            json.put("result", "fail");
            json.put("message", "download error：" + e.getClass().getName() + ": " + e.getMessage());
            writeDataToResponse(JSON.toJSONString(json), response);
        }
    }

    private static void writeDataToResponse(String data, HttpServletResponse response){
        try {
            OutputStreamWriter writer = new OutputStreamWriter(response.getOutputStream(), "UTF-8");
            writer.write(data);
            writer.close();
        }catch (IOException e){
            e.printStackTrace();
        }
    }

    private static void outStream(InputStream is, OutputStream os) throws IOException {
        byte[] buffer = new byte[10240];
        int length = -1;
        while ((length = is.read(buffer)) != -1) {
            os.write(buffer, 0, length);
            os.flush();
        }
        os.close();
        is.close();
    }

    /**
     * obtain triples
     * @param request
     * @return
     */
    @POST
    @Path("/triple")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public String queryTriples(@Context HttpServletRequest request) {
        JSONObject json = getJSONFromRequest(request);
        String projectID = json.getString("projectID");
        String commitId = json.getString("commitId");
        String jsonPath = json.getString("jsonPath");
        String tripleDir = json.getString("tripleDir");
        if(StringUtils.isEmpty(projectID)){
            json.put("result", "input error");
            return json.toJSONString();
        }

        if(StringUtils.isEmpty(tripleDir)){
            tripleDir = jsonDir;
        }

        json = new JSONObject();
        try {
            String triplePath = null;
            if(StringUtils.isEmpty(commitId)){
                triplePath = tripleDir + projectID.replace(File.separator, "_") + File.separator + "triple.txt";
                FileUtil.deleteFile(triplePath);
                Neo4jToTriple.transformNeo4jToTriple(triplePath, projectID);
            }else {
                VersionInfo versionInfo = null;
                if(!StringUtils.isEmpty(jsonPath)){
                    versionInfo = JSON.parseObject(FileUtil.readJsonFile(jsonPath), VersionInfo.class);
                }
                triplePath = tripleDir + projectID.replace(File.separator, "_") + File.separator + "triple_" + commitId + ".txt";
                FileUtil.deleteFile(triplePath);
                Neo4jToTriple.transformNeo4jToTriple(null, projectID, commitId, versionInfo, triplePath);
            }
            json.put("tripleFilePath", triplePath);
            System.out.println("saved triple to file: "+ triplePath);
        }catch (Exception e){
            json.put("result", "fail");
            json.put("message", e.getClass().getName() + ": " + e.getMessage());
        }
        return JSON.toJSONString(json);
    }

    @POST
    @Path("/addSimRank")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public String addSimRank(@Context HttpServletRequest request) {
        JSONObject json = getJSONFromRequest(request);
        String projectID = json.getString("projectID");
        String batchSize = json.getString("batchSize");
        if(StringUtils.isEmpty(projectID)){
            json.put("result", "input error");
            return json.toJSONString();
        }
        json = new JSONObject();
        try {
            AddSimRank addSimRank = new AddSimRank(projectID, StringUtils.isEmpty(batchSize)? null:Integer.valueOf(batchSize));
            addSimRank.addSimRankToNeo4j();
            json.put("result", "success");
        }catch (Exception e){
            json.put("result", "fail");
            json.put("message", e.getClass().getName() + ": " + e.getMessage());
        }
        return JSON.toJSONString(json);
    }

    private static String customizationArgs(String projectID, String arg){
        //mockito needs junit4.0
        if(projectID.toLowerCase().equals("mockito")){
            return arg + " --classpath /Users/hsz/.m2/repository/junit/junit/4.10/";
        }else if(projectID.toLowerCase().equals("org.aspectj")){
            return arg + " --ignore-dir saxon642,r2.0,incr1,circle,tests/new,tests/errors,bugs,bugs150,bugs151,bugs152,bugs153,bugs154,bugs160,bugs161,bugs162,bugs163,bugs164,bugs165,bugs166,bugs167,bugs169,bugs1610,bugs1611,bugs1612,bugs170,bugs171,bugs172,bugs173,bugs174,bugs175,bugs180,tests/pureJava,ajde/examples/spacewar,ajdoc/testdata/spacewar,ajdoc/testdata/figures-demo,classWAroundClosureRemoved,tests/incremental/model,java5,pr114875,testdata/binaryParents,multiIncremental,features151/newarrayjoinpoint,features151/ataround,weaver/testdata/forAsmDelegateTesting,features152/synchronization,features153,weaver5/java5-testsrc,tests/ltw,ajde/testdata/examples,ajde.core/testdata/figures-coverage,testing/testdata/figures-coverage,features160/annotationValueMatching,features160/parameterAnnotationMatching,features161/optimizedAnnotationBinding,incrementalPerformance/Proj64/inc1,features164/declareMixin,features167/intertype,features169/transparent,features167/overweaving,features169/itdInnerTypes,features1611/declareMinus,tests/indy " +
                    "--ignore-file Main,Driver,Target,Target.20,Target.30,Target.40,Target.50,XLintTypeThisPCD,XLintTypeDeclareParent,XLintTypeDeclareField,XLintTypeDeclareMethod,XLintTypeTargetPCD,Simple,If,Within,Statics,Coverage,Tricky1,Tricky2,Tricky3,C,tests/harness/Messages,Params,DeclareSoft,symbols/C,testdata/SimpleStructureModelTest/Good,testdata/simple-coverage/Good,Class1,InterTypeConstructors,Modifiers,design/intro/Overriding,design/intro/Interfaces,design/intro/MultiInheritCP,design/intro/ExceptionsCF,design/intro/ExceptionsCP,design/intro/MultiInheritCF,ajde/testdata/examples/inheritance/A,Main.20,Main.30,Main.40,Main.50,Main.60,Main-1,Main-2,DeleteMe.delete.20,Hello,testdata/src1/A1,testdata/src1/p1/A,testdata/src1/A2,testdata/src1/Ap,testdata/src1/ThisAndModifiers,testdata/src1/A,figures-cacm/figures/Point,testdata/src1/Parents,testdata/src1/ParentsFail,p1/C1,base/test107/C1,Foo,tests/ajde/examples/observer/clock/ClockTimer,testdata/src1/InterType,testdata/src1/InterTypeMethods,expected/TestUtilTest,actual/TestUtilTest,ajde/examples/figures-coverage/figures/primitives/planar/Point,ajde/testdata/examples/plainJava/apples/BigRigAspect" +
                    ",ajdoc/input/applesJava/BigRigAspect,ajdoc/input/applesJava/AppleCrate,figures-coverage/figures/FigureElement,figures-cacm/figures/FigureElement,applesAspectJ/TransportAspect,applesJava/TransportAspect,figures-cacm/figures/Line,figures-coverage/figures/composites/Line,ajde/examples/figures-coverage/figures/composites/Square,observer-gof/clock/DigitalClock,Example,applesAspectJ/Apple,applesJava/Apple,tests/harness/TestNoTester,moduleD/D,tests/ajde/examples/figures-coverage/figures/primitives/solid/SolidPoint,ajde/examples/figures-coverage/editor/Editor,testdata/src1/Handler,tests/incremental/injarSrc/one/InjarOneMain,tests/incremental/injarSrc/two/InjarTwoMain,injars/simple/DecParents,defaultPackage/src/lib/Lib,incrementalju/interPackage/src/lib/Lib,incremental/initialTests/TestNoTester,testing-drivers/testdata/incremental/harness/sourceDeleted/delete/DeleteMe,binding/UnfoundConstructor,testdata/Default,Changed.20,Removed.delete.20,ajdoc/testsrc/AjdocModuleTests,incremental/initialTests/sourceDeleted/delete/DeleteMe,injarTests/src/Hello2,HelloWorld,src1/C1,src1/C2,A,B,harness/CompoundMessage,tools/PointcutExpressionTest,patterns/ArgsTestCase," +
                    "AnnotatedClass,ajdoc/src/org/aspectj/tools/ajdoc/Util,bugs150/PR83303,ajde/testdata/examples/coverage/ModelCoverage,testdata/src1/Good,ajde/testdata/examples/coverage/ModelCoverage,ajdoc-testsrc/org/aspectj/tools/ajdoc/AjdocTests,TestClass,bcel-builder/testdata/SimpleType,org.aspectj.matcher/testsrc/org/aspectj/weaver/TestShadow,model/prX/Code,features1611/metamatching/Code,model/pr115607/pr115607,testing/testsrc/org/aspectj/testing/util/UtilTests";
        }else if(projectID.toLowerCase().equals("eclipse.platform.swt")){
            return arg + " --ignore-dir gtk,gtk1x,motif,photon,common_j2me,emulated,carbon,carbon2,wpf,cocoa,forms,common,wpf_win32,cairo,mozilla,qt" +
                    ",org.eclipse.swt.examples.paint/org,org.eclipse.swt.examples/org,org.eclipse.swt.examples.controls/org,org.eclipse.swt.examples.launcher/org,org.eclipse.swt.examples.ole.win32/org,org.eclipse.swt.examples.layouts/org,org.eclipse.swt.examples.paint/src/org/eclipse/swt/examples/paint " +
                    "--ignore-file org.eclipse.swt.opengl/win32/org/eclipse/swt/opengl/GLCanvas,Mozilla/win32/org/eclipse/swt/browser/Browser";
        }else if(projectID.toLowerCase().equals("birt")){
            return arg + " --ignore-dir org.eclipse.birt.report.designer.ui.views/src/org/eclipse/birt/report/designer/internal/ui/views/actions,data/org.eclipse.birt.data.tests/test/testutil,tutorial/extension-tutorial-1 " +
                    "--ignore-file org.eclipse.birt.report.designer.ui.ide/src/org/eclipse/birt/report/designer/ui/editors/LibraryActionBarContributor,org.eclipse.birt.report.designer.ui.ide/src/org/eclipse/birt/report/designer/ui/editors/ReportActionBarContributor,org.eclipse.birt.report.designer.ui.ide/src/org/eclipse/birt/report/designer/ui/editors/ReportDocumentActionContributor,org.eclipse.birt.report.designer.ui.ide/src/org/eclipse/birt/report/designer/ui/editors/TemplateActionBarContributor,org.eclipse.birt.report.designer.ui.editors/src/org/eclipse/birt/report/designer/internal/ui/views/outline/HeaderFooterFilter,org.eclipse.birt.report.designer.ui.editors/src/org/eclipse/birt/report/designer/internal/ui/views/outline/ListenerElementVisitor,org.eclipse.birt.report.designer.ui.data/src/org/eclipse/birt/report/designer/data/ui/util/ExpressionUtility,org.eclipse.birt.report.designer.ui.preview.web/src/org/eclipse/birt/report/designer/ui/preview/editors/ReportPreviewFormPage,org.eclipse.birt.report.designer.ui.preview.web/src/org/eclipse/birt/report/designer/ui/preview/Activator" +
                    ",TableBorderExTest,TableBorderPSTest,TableBorderPDFTest";
        }else if(projectID.toLowerCase().equals("tomcat")){
            return arg + " --ignore-file webapps/examples/jsp/plugin/applet/Clock2";
        }
        return arg;
    }

    private static String customizationProjectPath(String projectName, String projectPath){
        if(defects4jProjects.contains(projectName)) {
            return projectDir +"defects4j/" + projectName + "/1b";
        }
        if(fse2014Projects.contains(projectName)){
            return projectDir +"FSE2014/" + projectName;
        }
        return projectPath;
    }

    private static boolean execCodeOntology(HttpServletRequest request, String arg){
        String servletPath = request.getSession().getServletContext().getRealPath("/");
        String parserCmdPath = servletPath.split("/parser")[0] + "/parser/parser";
        RealtimeProcess realtimeProcess = new RealtimeProcess();
        realtimeProcess.setDirectory(parserCmdPath);
        realtimeProcess.setCommand("codeontology "+arg);
        String result = "";
        try {
            realtimeProcess.start();
            result = realtimeProcess.getAllResult();
        } catch (IOException|InterruptedException e) {
            e.printStackTrace();
        }
        return result.contains("Triples extracted successfully");
    }

    private JSONObject getJSONFromRequest(HttpServletRequest request){
        String paramStr= getStringFromRequest(request);
        JSONObject jsonObject = JSONObject.parseObject(paramStr);
        String param= jsonObject.toJSONString();
        System.out.println("call uri: " + request.getPathInfo()+" , param= "+param + ", time= "+ df.format(new Date()));
        return JSON.parseObject(param);
    }

    private String getStringFromRequest(HttpServletRequest request){
        String param= null;
        try {
            BufferedReader streamReader = new BufferedReader( new InputStreamReader(request.getInputStream(), "UTF-8"));
            StringBuilder responseStrBuilder = new StringBuilder();
            String inputStr;
            while ((inputStr = streamReader.readLine()) != null)
                responseStrBuilder.append(inputStr);

            return responseStrBuilder.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void setProjectDir(String projectDir) {
        Rest.projectDir = projectDir;
    }

    public static void setJsonDir(String jsonDir) {
        Rest.jsonDir = jsonDir;
    }
}




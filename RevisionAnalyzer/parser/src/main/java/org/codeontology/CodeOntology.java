/*
Copyright 2017 Mattia Atzeni, Maurizio Atzori

This file is part of CodeOntology.

CodeOntology is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

CodeOntology is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with CodeOntology.  If not, see <http://www.gnu.org/licenses/>
*/

package org.codeontology;

import org.apache.commons.collections.CollectionUtils;
import org.codeontology.build.DefaultProject;
import org.codeontology.build.DependenciesLoader;
import org.codeontology.build.Project;
import org.codeontology.build.ProjectFactory;
import org.codeontology.extraction.*;
import org.codeontology.neo4j.Neo4jConfig;
import org.codeontology.neo4j.Neo4jLogger;
import org.codeontology.util.ThreadPoolUtil;
import org.codeontology.version.GitUtil;
import org.codeontology.version.VersionUpdatePreProcessor;
import org.codeontology.version.update.UpdateInfo;
import org.codeontology.version.VersionUpdateProcessor;
import org.joda.time.Period;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;
import spoon.Launcher;
import spoon.compiler.ModelBuildingException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class CodeOntology {
    private static CodeOntology codeOntology;
    private static int status = 0;
    private boolean downloadDependencies;
    private CodeOntologyArguments arguments;
    private Launcher spoon;
    private boolean exploreJarsFlag;
    private boolean haveUpdateName = false;
    private Project project;
    private DependenciesLoader<? extends Project> loader;
    public static PeriodFormatter formatter = new PeriodFormatterBuilder().appendHours().appendSuffix(" h ").appendMinutes().appendSuffix(" min ").appendSeconds().appendSuffix(" s ").appendMillis().appendSuffix(" ms").toFormatter();;
    private int tries;
//    private List<String> directories = new ArrayList<>(Arrays.asList("test", "examples", "debug", "androidTest", "samples", "sample", "example", "demo", ".*test.*", ".*demo.*", ".*sample.*", ".*example.*", "app", "experimental", "mockmaker","JodaTimeContrib", "docs","saxon642"));
    private List<String> directories = new ArrayList<>(Arrays.asList("mockmaker","JodaTimeContrib", "docs"));

    private List<String> files = new ArrayList<>();
    public static final String SUFFIX = ".codeontology";

    private CodeOntology(String[] args) {
        try {
            arguments = new CodeOntologyArguments(args);
            exploreJarsFlag = arguments.exploreJars() || (arguments.getJarInput() != null);
            RDFLogger.getInstance().setOutputFile(arguments.getOutput());
            downloadDependencies = arguments.downloadDependencies();

//            setUncaughtExceptionHandler();

            //忽略的某些文件夹下的源码，默认包括test等文件夹的测试代码
            List<String> ignoreDirList = getArguments().getIgnoreDir();
            if(!CollectionUtils.isEmpty(ignoreDirList)) {
                directories.addAll(ignoreDirList);
            }
            List<String> ignoreFileList = getArguments().getIgnoreFile();
            if(!CollectionUtils.isEmpty(ignoreFileList)){
                files.addAll(ignoreFileList);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Could not process arguments");
        }
    }

    public static void main(String[] args) throws Exception {
        codeOntology = new CodeOntology(args);
//        File pom = new File(getProjectPath()+File.separator+"pom.xml");
//        if(pom.exists()){
//            throw new RuntimeException("pom occur.");
//        }
//        return;
        try {
            codeOntology.processSources();
        } catch (Exception e) {
            codeOntology.handleFailure(e);
            throw e;
        }finally {
            codeOntology.postCompletionTasks();
            codeOntology = null;
        }
//        exit(status);
    }

    private void processSources() {
        if (isInputSet()) {
            System.out.println("Running on " + getArguments().getInput());

            project = ProjectFactory.getInstance().getProject(getArguments().getInput());

            if(Neo4jLogger.getInstance().getCommitIdList().contains(getArguments().getCommitID())){
                throw new RuntimeException("error: this commitId version has already been extracted");
            }

            try {
                loadDependencies();
            }catch (Exception e){
                System.out.println("fail to solve as gradle project, change to default project.");
                project = new DefaultProject(new File(getArguments().getInput()));
                loadDependencies();
            }

            if (!getArguments().doNotExtractTriples()) {
                ignoreDirAndFiles();
                spoon();
                extractAllTriples();
            }
        }
    }

    public void handleFailure(Throwable t) {
        System.out.println("It was a good plan that went wrong.");
        t.printStackTrace();
        if (t != null) {
            if (t.getMessage() != null) {
                System.out.println(t.getMessage());
            }
            if (getArguments().stackTraceMode()) {
                t.printStackTrace();
            }
        }
        status = -1;
    }

    private void ignoreDirAndFiles(){
        checkInput();

        System.out.println("ignore some dir and files: ");
        long start = System.currentTimeMillis();
        String projectPath = getProjectPath();
        //移除需要忽略的某些文件夹下的源码
        removeDirectoriesByName(directories, projectPath);
        //移除一些重复类名的java文件
        removeFileByName(files);
        long end = System.currentTimeMillis();
        Period period = new Period(start, end);
        System.out.println("ignore dir and files in " + formatter.print(period));
    }

    private void spoon() {
        spoon = new Launcher();
        spoon.getEnvironment().setComplianceLevel(arguments.complianceLevel());//自定义jdk版本
        ReflectionFactory.getInstance().setParent(spoon.createFactory());
        try {
            long start = System.currentTimeMillis();
            spoon.addInputResource(getArguments().getInput());
            System.out.println("Building model...");
            spoon.buildModel();
            long end = System.currentTimeMillis();
            Period period = new Period(start, end);
            System.out.println("Model built successfully in " + formatter.print(period));

        } catch (ModelBuildingException e) {
            throw e;
        }
    }

    private void loadDependencies() {
        long start = System.currentTimeMillis();
        loader = project.getLoader();
        loader.loadDependencies();

        String classpath = getArguments().getClasspath();

        if (classpath != null) {
            loader.loadClasspath(classpath);
        }
        long end = System.currentTimeMillis();
        System.out.println("Dependencies loaded in " + formatter.print(new Period(start, end)) + ".");
    }

    private void extractAllTriples() {
        long start = System.currentTimeMillis();

        System.out.println("Extracting triples...");

        //设置commitID
        Neo4jLogger.getInstance().setCommitId(getArguments().getCommitID());

        if(getArguments().isVersionUpdate()){//走版本更新操作
            //获取版本更新信息
            String projectPath = project.getPath();
            String lastCommitId = Neo4jLogger.getInstance().getLastCommitId();
            String newCommitId = CodeOntology.getCommitID();
            checkCommitId(newCommitId);
            UpdateInfo updateInfo = GitUtil.getVersionUpdateInfo(projectPath, lastCommitId, newCommitId);
//            if(updateInfo!=null){
//                return;
//            }
            System.out.println("execute VersionUpdatePreProcessor: ");
            spoon.addProcessor(new VersionUpdatePreProcessor(updateInfo));//先进行类和方法名变更的处理
            spoon.process();
            ThreadPoolUtil.getInstance().awaitTermination();
            RDFLogger.getInstance().writeRDF();//更新名称的操作要先写入neo4j
            if(RDFLogger.getInstance().getNeo4jCsvLogger()!=null) {
                RDFLogger.getInstance().getNeo4jCsvLogger().loadCsvToNeo4j();
                RDFLogger.getInstance().getNeo4jCsvLogger().zeroCSV();
            }
            haveUpdateName = true;
            System.out.println("execute VersionUpdateProcessor: ");
            spoon.addProcessor(new VersionUpdateProcessor(updateInfo));//后续无关类和方法名变更的处理
            spoon.process();
            VersionUpdateProcessor.deleteClass(updateInfo);//删除类
            ThreadPoolUtil.getInstance().awaitTermination();
        }else {//新建项目的所有实体
            spoon.addProcessor(new SourceProcessor());
            spoon.process();
        }
        RDFLogger.getInstance().writeRDF();

        //将存到csv的三元组load到neo4j中
        if(RDFLogger.getInstance().getNeo4jCsvLogger()!=null) {
            RDFLogger.getInstance().getNeo4jCsvLogger().loadCsvToNeo4j();
        }

        long end = System.currentTimeMillis();
        Period period = new Period(start, end);
        System.out.println("Triples extracted successfully in " + formatter.print(period) + ".");

        spoon = new Launcher();
    }

    private void checkCommitId(String commitId){
        if(Neo4jLogger.getInstance().getCommitIdList().contains(commitId)){
            throw new RuntimeException("this commitId has already been extracted.");
        }
        return;
    }

    private void postCompletionTasks() {
        try {
            scheduleShutdownTask();
            restore();
            Neo4jConfig.getInstance().stopNeo4jDriver();

            //清空状态
            RDFLogger.clean();
            Neo4jLogger.clean();
            Neo4jConfig.clean();
            ThreadPoolUtil.clean();;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void restore() throws IOException {
        String root = getArguments().getInput();
        Files.walk(Paths.get(root))
                .map(Path::toFile)
                .filter(file -> file.getAbsolutePath().endsWith(SUFFIX))
                .forEach(this::restore);
    }

    private void restore(File file) {
        File original = removeSuffix(file);
        boolean success = true;
        if (original.exists()) {
            success = original.delete();
        }
        success = success && file.renameTo(original);

        if (!success) {
            showWarning("Could not restore file " + file.getPath());
        }
    }

    private File removeSuffix(File suffixed) {
        int suffixLength = SUFFIX.length();
        String path = suffixed.getPath();
        StringBuilder builder = new StringBuilder(path);
        int index = builder.lastIndexOf(SUFFIX);
        builder.replace(index, index + suffixLength, "");
        return new File(builder.toString());
    }

    private void scheduleShutdownTask() {
        if (getArguments().shutdownFlag()) {
            Thread shutdownThread = new Thread(() -> {
                try {
                    System.out.println("Shutting down...");
                    ProcessBuilder processBuilder = new ProcessBuilder("bash", "-c", "sleep 3; shutdown -h now");
                    processBuilder.start();
                } catch (Exception e) {
                    System.out.println("Shutdown failed");
                }
            });
            Runtime.getRuntime().addShutdownHook(shutdownThread);
        }
    }

    private void checkInput() {
        File input = new File(getArguments().getInput());
        if (!input.exists()) {
            System.out.println("File " + input.getPath() + " doesn't seem to exist.");
            System.exit(-1);
        }
        if (!input.canRead() && !input.setReadable(true)) {
            System.out.println("File " + input.getPath() + " doesn't seem to be readable.");
            System.exit(-1);
        }
    }

    public static CodeOntology getInstance() {
        return codeOntology;
    }

    public CodeOntologyArguments getArguments() {
        return arguments;
    }

    public static boolean downloadDependencies() {
        if (codeOntology == null) {
            return true;
        }
        return getInstance().downloadDependencies;
    }

    public static void signalDependenciesDownloaded() {
        getInstance().downloadDependencies = true;
    }

    public static boolean verboseMode() {
        return getInstance().getArguments().verboseMode();
    }

    public static boolean isJarExplorationEnabled() {
        return getInstance().exploreJarsFlag;
    }

    public static String getVersion() {
        String version = "1.0";
        try {
            version = Neo4jLogger.getInstance().getNextVersion();
        }catch (Exception e){
            e.printStackTrace();
        }
        return version;
    }

    public static String getCommitID() {
        return getInstance().getArguments().getCommitID();
    }

    public static boolean isVersionUpdate(){
        return getInstance().getArguments().isVersionUpdate();
    }

    public static String getProjectID() {
        return getInstance().getArguments().getProjectID();
    }

    public static String getProjectPath(){
        String projectPath = getInstance().getArguments().getInput();
        if(projectPath.endsWith(File.separator)){
            projectPath = projectPath.substring(0, projectPath.length()-1);
        }
        return projectPath;
    }

    public static boolean haveUpdateName(){
        return getInstance().haveUpdateName;
    }

    public static boolean doNotLoadMaven() {
        if (codeOntology == null) {
            return false;
        }
        return getInstance().getArguments().doNotLoadMaven();
    }

    private boolean isInputSet() {
        return getArguments().getInput() != null;
    }

    private boolean removeDirectoriesByName(List<String> nameList, String projectPath) {
        try {
            Path[] tests = Files.walk(Paths.get(getArguments().getInput()))
                    .filter(path -> path.toFile().isDirectory())
//                    .filter(path -> match(path, name) || !(path.startsWith(projectPath + "/src") || path.startsWith(projectPath+"/source") || path.startsWith(projectPath+"/gen")))//剔除test测试代码，有些项目其他目录包含了其他来源的源码，只考虑本项目src目录下源码
                    .filter(path -> match(path, nameList))
                    .toArray(Path[]::new);

            if (tests.length == 0) {
                return false;
            }

            for (Path testPath : tests) {
                String absolutePath = testPath.toFile().getAbsolutePath();
                if(absolutePath.equals(projectPath) || absolutePath.contains("/.git")){
                    continue;
                }
//                System.out.println("Ignoring sources in " + testPath.toFile().getAbsolutePath());
                Files.walk(testPath)
                        .filter(path -> path.toFile().getAbsolutePath().endsWith(".java"))
                        .forEach(path -> path.toFile().renameTo(
                                new File(path.toFile().getPath() + SUFFIX))
                        );
            }
        } catch (IOException e) {
            showWarning(e.getMessage());
        }

        return true;
    }

    private boolean removeFileByName(List<String> nameList){
        try {
            Path[] paths = Files.walk(Paths.get(getArguments().getInput()))
                        .filter(path -> {
                            for(String name: nameList) {
                                if(path.toFile().getAbsolutePath().contains(name + ".java")){
                                    return true;
                                }
                            }
                            return false;
                        })
                        .toArray(Path[]::new);
            if (paths.length == 0) {
                return false;
            }
            for (Path path : paths) {
                path.toFile().renameTo(new File(path.toFile().getPath() + SUFFIX));
            }
        } catch (IOException e) {
            showWarning(e.getMessage());
        }
        return true;
    }

    private boolean match(Path path, List<String> nameList){
        for(String name : nameList){
            if(match(path, name)){
                return true;
            }
        }
        return false;
    }

    private boolean match(Path path, String name) {
        if (!name.contains("*")) {
            if(name.contains("/")) {
                return path.toFile().getAbsolutePath().contains(name);
            }else{
                return path.toFile().getName().equals(name);
            }
        } else {
            Pattern pattern = Pattern.compile(name, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
            return pattern.matcher(path.toFile().getName()).matches();
        }
    }

    public static void showWarning(String message) {
        System.out.println("[WARNING] " + message);
    }

    private void setUncaughtExceptionHandler() {
        Thread.currentThread().setUncaughtExceptionHandler((t, e) ->  exit(-1));
    }

    private static void exit(final int status) {
        try {
            // setup a timer, so if nice exit fails, the nasty exit happens
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    Runtime.getRuntime().halt(status);
                }
            }, 30000);

            // try to exit nicely
            System.exit(status);

        } catch (Throwable t) {
            try {
                Thread.sleep(30000);
                Runtime.getRuntime().halt(status);
            } catch (Exception | Error e) {
                Runtime.getRuntime().halt(status);
            }
        }

        Runtime.getRuntime().halt(status);
    }
}
package org.codeontology.neo4j;

import org.apache.commons.lang3.StringUtils;
import org.codeontology.util.CommandUtil;
import org.neo4j.driver.v1.*;
import org.neo4j.driver.v1.exceptions.ServiceUnavailableException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Neo4jConfig {

    private static Neo4jConfig instance;

    private Driver driver;
    private Session session;
    private int connectTimes = 0;

    private static String neo4j_path = "/usr/local/var/neo4j/data/databases/";
    private static String neo4j_bolt = "bolt://localhost:7687";
    private static String neo4j_cmd = "neo4j";
    private static String neo4j_user_name = "neo4j";
    private static String neo4j_parseword = "a12345";
    private static String soft_link = "graph.db";//作为软连接连接到真实数据库

    public static Neo4jConfig getInstance(){
        if (instance == null) {
            instance = new Neo4jConfig();
        }
        return instance;
    }

    public static void clean(){
        instance = null;
    }

    private Neo4jConfig(){
    }

    public static String configDatabaseChoose(String projectID){
        try {
            //判断当前运行的数据库是否符合
            boolean sameDatabase = true;
            String[] command1 = {"bash","-c", "ls -l "+neo4j_path+"graph.db"};
            String result = CommandUtil.run(command1);
            if(StringUtils.isEmpty(result)){
                sameDatabase = false;
            }else {
                String[] split = result.split("->");
                if(split.length < 2){
                    sameDatabase = false;
                }else if(!split[1].trim().equals(projectID + ".db")){
                    sameDatabase = false;
                }
            }

            //不符合则，切换对应projectID数据库
            if(!sameDatabase) {
                List<String> commandList = new ArrayList<>();
                commandList.add("cd " + neo4j_path);
                commandList.add(neo4j_cmd +" stop");
                commandList.add("rm -rf " + soft_link);
                commandList.add("mkdir -p " + projectID + ".db");
                commandList.add("ln -s " + projectID + ".db " + soft_link);
                commandList.add(neo4j_cmd+" start");

                String[] command = {"bash", "-c", StringUtils.join(commandList, ";")};
                result = CommandUtil.run(command);
                Neo4jLogger.clean();
                return result;
            }
            return null;
        }catch (IOException e){
            e.printStackTrace();
            throw new RuntimeException(e.getMessage());
        }
    }

    public synchronized Session session(){
        if(session!=null && session.isOpen()){
            return session;
        }
        try {
            driver = GraphDatabase.driver(neo4j_bolt, AuthTokens.basic(neo4j_user_name, neo4j_parseword));
            connectTimes = 0;
        }catch (ServiceUnavailableException e){
            try {
                Thread.currentThread().sleep(1000);
                connectTimes++;
                System.out.println("waiting neo4j start.. " + connectTimes + " s");
            } catch (InterruptedException e1) {
                e1.printStackTrace();
            }
            if(connectTimes < 10) {
                return session();
            }
            throw new RuntimeException("neo4j fail to start.");
        }
        System.out.println("neo4j started.");
        session = driver.session();
        return session;
    }

    /**
     * 关闭与neo4j的连接
     */
    public void stopNeo4jDriver() {
        if(session != null) {
            session.close();
        }
        if(driver != null){
            driver.closeAsync();
        }
    }

    public static void setNeo4j_bolt(String neo4j_bolt) {
        Neo4jConfig.neo4j_bolt = neo4j_bolt;
    }

    public static void setNeo4j_cmd(String neo4j_cmd) {
        Neo4jConfig.neo4j_cmd = neo4j_cmd;
    }

    public static void setNeo4j_path(String database_path) {
        Neo4jConfig.neo4j_path = database_path;
    }

    public static void setNeo4j_user_name(String neo4j_user_name) {
        Neo4jConfig.neo4j_user_name = neo4j_user_name;
    }

    public static void setNeo4j_parseword(String neo4j_parseword) {
        Neo4jConfig.neo4j_parseword = neo4j_parseword;
    }
}

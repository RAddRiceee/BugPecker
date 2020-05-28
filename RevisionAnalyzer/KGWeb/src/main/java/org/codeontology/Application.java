package org.codeontology;

import org.apache.commons.lang3.StringUtils;
import org.codeontology.neo4j.Neo4jConfig;
import org.codeontology.neo4j.Neo4jCsvLogger;
import org.glassfish.jersey.server.ResourceConfig;

import javax.ws.rs.ApplicationPath;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Properties;

/**
 * 初始化启动类
 */
@ApplicationPath("/")
public class Application extends ResourceConfig {

    public static Properties systemProperties = new Properties();

    public Application() {
        System.out.println("项目初始化..");
        //配置项目参数
        try {
            InputStream in = Application.class.getClassLoader().getResourceAsStream("config.properties");
            systemProperties.load(in);

            String projectDir = getValue("projectDir");
            String jsonDir = getValue("jsonDir");
            String csvDir = getValue("csvDir");
            String neo4j_path = getValue("neo4j_path");
            String neo4j_bolt = getValue("neo4j_bolt");
            String neo4j_cmd = getValue("neo4j_cmd");
            String neo4j_user_name = getValue("neo4j_user_name");
            String neo4j_password = getValue("neo4j_password");
            if(!StringUtils.isEmpty(projectDir)){
                Rest.setProjectDir(projectDir);
            }
            if(!StringUtils.isEmpty(jsonDir)){
                Rest.setJsonDir(jsonDir);
            }
            if(!StringUtils.isEmpty(csvDir)){
                Neo4jCsvLogger.setCsvDir(csvDir);
            }
            if(!StringUtils.isEmpty(neo4j_path)){
                Neo4jConfig.setNeo4j_path(neo4j_path);
            }
            if(!StringUtils.isEmpty(neo4j_bolt)){
                Neo4jConfig.setNeo4j_bolt(neo4j_bolt);
            }
            if(!StringUtils.isEmpty(neo4j_cmd)){
                Neo4jConfig.setNeo4j_cmd(neo4j_cmd);
            }
            if(!StringUtils.isEmpty(neo4j_user_name)){
                Neo4jConfig.setNeo4j_user_name(neo4j_user_name);
            }
            if(!StringUtils.isEmpty(neo4j_password)){
                Neo4jConfig.setNeo4j_parseword(neo4j_password);
            }
            System.out.println("项目初始化成功！");
        } catch (IOException e) {
            System.out.println("读取配置文件失败");
            e.printStackTrace();
        }

    }

    public static String getValue(String key) {
        if(systemProperties != null) {
            return systemProperties.getProperty(key, null);
        }else {
            return null;
        }
    }
}

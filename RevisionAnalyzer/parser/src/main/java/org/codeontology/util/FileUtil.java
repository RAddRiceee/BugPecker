package org.codeontology.util;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.List;

public class FileUtil {

    /**
     * 读取json文件
     * @param filePath json文件路径
     * @return 返回json字符串
     */
    public static String readJsonFile(String filePath) {
        System.out.println("read json from file: "+ filePath);
        String jsonStr = "";
        try {
            File jsonFile = new File(filePath);
            jsonStr = IOUtils.toString(new FileInputStream(jsonFile), Charsets.UTF_8.toString());
            return jsonStr;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 保存为json文件
     * @param filePath 存储json的文件路径
     * @return 返回json字符串
     */
    public static void saveJsonToFile(String jsonStr, String filePath) {
        System.out.println("save json to file: "+ filePath);
        try {
            // 保证创建一个新文件
            File file = new File(filePath);
            if (!file.getParentFile().exists()) { // 如果父目录不存在，创建父目录
                file.getParentFile().mkdirs();
            }
            if (file.exists()) { // 如果已存在,删除旧文件
                file.delete();
            }
            file.createNewFile();
            IOUtils.write(jsonStr, new FileOutputStream(file), Charsets.UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 保存为List为txt文件
     */
    public static void appendListToFile(List<String> list, String filePath){
        if(CollectionUtils.isEmpty(list)) {
            return;
        }
        try {
            File file = new File(filePath);
            if (!file.getParentFile().exists()) { // 如果父目录不存在，创建父目录
                file.getParentFile().mkdirs();
            }
            if(!file.exists()){
                file.createNewFile();
            }
            String str = StringUtils.join(list, "\n") + "\n";
            IOUtils.write(str, new FileOutputStream(file, true), Charsets.UTF_8);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public static void deleteFile(String filePath){
        File file = new File(filePath);
        if (file.exists()) { // 如果已存在,删除旧文件
            file.delete();
        }
    }
}

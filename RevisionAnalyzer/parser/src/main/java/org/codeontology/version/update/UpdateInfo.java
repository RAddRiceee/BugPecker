package org.codeontology.version.update;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class UpdateInfo {

    public enum Operation {
        ADD,//增添
        MODIFY,//修改
        DELETE//删除
    }

    /**
     * 每种操作对应的class列表，Operation是包含ADD,MODIFY,DELETE的枚举类
     * 修改操作对应的class列表，要在下面的参数中具体指明class中method、field的增删改
     * class的String格式举例：java.io.PrintStream，即包含包路径的完整类名
     */
    private Map<Operation, List<String>> classUpdateMap;
    /**
     * key为第一个参数中修改操作对应的class的完整类名，如果类名有修改，这里是修改后的新类名;如果方法签名有修改，这里是修改后的新方法签名
     * value为指定class中增删改操作和方法列表对应的Map
     * method的String格式举例：calculateAnswer(java.lang.String, int, java.lang.Integer[]), 即方法签名,不要有多余空格，只有参数间的逗号后有一个空格
     */
    //TODO 方法签名的问题
    private Map<String, Map<Operation, List<String>>> methodUpdateMap;
    /**
     * 跟第二个参数类似，只不过List<String>指类的成员变量列表,String为变量名，Operation不包含MODIFY操作
     */
    private Map<String, Map<Operation, List<String>>> fieldUpdateMap;
    /**
     * 类名的修改，类名指包含包路径的完整类名
     * key为旧类名，value为新类名
     */
    private Map<String, String> classNameUpdateMap;
    /**
     * 方法签名的修改,key为方法所在的class,第二个map的key为旧方法签名，value为新方法签名
     */
    private Map<String, Map<String, String>> methodNameUpdateMap;



    public Map<Operation, List<String>> getClassUpdateMap() {
        return classUpdateMap;
    }

    public void setClassUpdateMap(Map<Operation, List<String>> classUpdateMap) {
        this.classUpdateMap = classUpdateMap;
    }

    public Map<String, Map<Operation, List<String>>> getMethodUpdateMap() {
        return methodUpdateMap;
    }

    public void setMethodUpdateMap(Map<String, Map<Operation, List<String>>> methodUpdateMap) {
        this.methodUpdateMap = methodUpdateMap;
    }

    public Map<String, Map<Operation, List<String>>> getFieldUpdateMap() {
        return fieldUpdateMap;
    }

    public void setFieldUpdateMap(Map<String, Map<Operation, List<String>>> fieldUpdateMap) {
        this.fieldUpdateMap = fieldUpdateMap;
    }

    public Map<String, String> getClassNameUpdateMap() {
        return classNameUpdateMap;
    }

    public void setClassNameUpdateMap(Map<String, String> classNameUpdateMap) {
        this.classNameUpdateMap = classNameUpdateMap;
    }

    public Map<String, Map<String, String>> getMethodNameUpdateMap() {
        return methodNameUpdateMap;
    }

    public void setMethodNameUpdateMap(Map<String, Map<String, String>> methodNameUpdateMap) {
        this.methodNameUpdateMap = methodNameUpdateMap;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UpdateInfo that = (UpdateInfo) o;
        return Objects.equals(classUpdateMap, that.classUpdateMap) &&
                Objects.equals(methodUpdateMap, that.methodUpdateMap) &&
                Objects.equals(fieldUpdateMap, that.fieldUpdateMap) &&
                Objects.equals(classNameUpdateMap, that.classNameUpdateMap) &&
                Objects.equals(methodNameUpdateMap, that.methodNameUpdateMap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(classUpdateMap, methodUpdateMap, fieldUpdateMap, classNameUpdateMap, methodNameUpdateMap);
    }
}

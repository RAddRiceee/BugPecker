package org.codeontology.version.update;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class ParseDiffData {

    private Map<UpdateInfo.Operation, List<String>> classUpdateMap = new HashMap<>();
    private Map<String, Map<UpdateInfo.Operation, List<String>>> methodUpdateMap = new HashMap<>();
    private Map<String, Map<UpdateInfo.Operation, List<String>>> fieldUpdateMap = new HashMap<>();
    private Map<String, String> classNameUpdateMap = new HashMap<>();
    private Map<String, Map<String, String>> methodNameUpdateMap = new HashMap<>();

    private ArrayList<String> getCmdOutput(String cmd, String dir){
        ArrayList<String> lines = new ArrayList<>();
        try {
            Process process = Runtime.getRuntime().exec(cmd, null, new File(dir));
            BufferedReader br = new BufferedReader(new InputStreamReader(
                    process.getInputStream(), "gbk"));
            String line;
            while((line = br.readLine()) != null){
                lines.add(line);
            }
            process.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return lines;
    }

    private String locateMethod(int lineNumber, Visitor visitor){
        // 二分查找
        ArrayList<Visitor.Bound> lns = visitor.bounds;
        int start = 0, end = lns.size()-1, mid, startLineNumber, endLineNumber;
        while(start <= end){
            mid = (start + end)/2;
            startLineNumber = lns.get(mid).startLineNumber;
            endLineNumber = lns.get(mid).endLineNumber;
            if(lineNumber >= startLineNumber && lineNumber <= endLineNumber){
                return visitor.boundIndexToSignature.get(mid);
            }else if(lineNumber < startLineNumber){
                end = mid -1;
            }else{
                start = mid + 1;
            }
        }
        return null;
    }

    private void mark(HashMap<Integer, Integer> lineNumbers, Visitor visitor1, Visitor visitor2,
                          HashMap<String, Integer> methodMap, String newClass, boolean deleteMode) {

        //首先判断成员方法是否被删除或者为新增方法
        for (int i = 0; i < visitor1.bounds.size(); i++) {
            int s = visitor1.bounds.get(i).startLineNumber;
            int e = visitor1.bounds.get(i).endLineNumber;
            boolean methodIsCovered = true;
            for (int j = s; j <= e; j++) {
                if (!lineNumbers.keySet().contains(j)) {
                    methodIsCovered = false;
                    break;
                }
            }
            if (methodIsCovered) {
                for (int j = s; j <= e; j++) {
                    lineNumbers.remove(j);
                }
                if(deleteMode) {
                    methodMap.put(visitor1.boundIndexToSignature.get(i), 4);
                }else{
                    methodMap.put(visitor1.boundIndexToSignature.get(i), 5);
                }
            }
        }

        for (int ln : lineNumbers.keySet()) {
            String m = locateMethod(ln, visitor1);
            if (m != null) {
                if(methodMap.get(m) >= 3){
                    continue;
                }
                boolean methodSignatureIsModified = visitor1.lineNumbersOfSignature.get(m).contains(ln);
                Integer i = methodMap.get(m);
                // 值为0：方法未被修改，1：仅方法签名被修改，2：仅方法体被修改 3：方法签名和方法体均被修改 4：方法被删除 5：方法被新增
                if (methodSignatureIsModified) { // 修改发生在方法签名
                    switch (methodMap.get(m)) {
                        case 0:
                            i = 1;
                            break;
                        case 2:
                            i = 3;
                            break;
                    }
                    if(!methodNameUpdateMap.keySet().contains(newClass) ||
                            !methodNameUpdateMap.get(newClass).keySet().contains(m) &&
                            !methodNameUpdateMap.get(newClass).values().contains(m)) {
                        String similarMethod = locateMethod(lineNumbers.get(ln), visitor2);
                        if (!methodNameUpdateMap.keySet().contains(newClass)) {
                            methodNameUpdateMap.put(newClass, new HashMap<>());
                        }
                        if (deleteMode) {
                            methodNameUpdateMap.get(newClass).put(m, similarMethod);
                        } else {
                            methodNameUpdateMap.get(newClass).put(similarMethod, m);
                        }
                    }
                } else { // 修改发生在方法体
                    switch (methodMap.get(m)) {
                        case 0:
                            i = 2;
                            break;
                        case 1:
                            i = 3;
                            break;
                    }
                }
                methodMap.put(m, i);
            }
        }
    }

    private void process(String oldClass, String newClass,
                        ArrayList<String> oldJavaFileLines, ArrayList<String> newJavaFileLines,
                        HashMap<Integer, Integer> deletedLineNumbers, HashMap<Integer, Integer> addedLineNumbers){

        Visitor oldVisitor = new Visitor(oldJavaFileLines);
        Visitor newVisitor = new Visitor(newJavaFileLines);
        if(oldVisitor.packageName != null && oldClass != null) oldClass = oldVisitor.packageName + "." + oldClass;
        if(newVisitor.packageName != null && newClass != null) newClass = newVisitor.packageName + "." + newClass;

        if(newClass == null){//类被删除
            if(!classUpdateMap.keySet().contains(UpdateInfo.Operation.DELETE)){
                classUpdateMap.put(UpdateInfo.Operation.DELETE, new ArrayList<>());
            }
            classUpdateMap.get(UpdateInfo.Operation.DELETE).add(oldClass);
            return;
        }
        else if(oldClass == null){//类被新增
            if(!classUpdateMap.keySet().contains(UpdateInfo.Operation.ADD)){
                classUpdateMap.put(UpdateInfo.Operation.ADD, new ArrayList<>());
            }
            classUpdateMap.get(UpdateInfo.Operation.ADD).add(newClass);
            return;
        }else {//类被修改
            if (!classUpdateMap.keySet().contains(UpdateInfo.Operation.MODIFY)) {
                classUpdateMap.put(UpdateInfo.Operation.MODIFY, new ArrayList<>());
            }
            classUpdateMap.get(UpdateInfo.Operation.MODIFY).add(newClass);
        }

        if(!oldClass.equals(newClass)){
            classNameUpdateMap.put(oldClass, newClass);
        }

        // 值为0：方法未被修改，1：仅方法签名被修改，2：仅方法体被修改 3：方法签名和方法体均被修改 4：方法被删除 5：方法被新增
        HashMap<String, Integer> oldMethodMap = new HashMap<>();
        for(String m : oldVisitor.lineNumbersOfSignature.keySet()){
            oldMethodMap.put(m, 0);
        }

        HashMap<String, Integer> newMethodMap = new HashMap<>();
        for(String m : newVisitor.lineNumbersOfSignature.keySet()){
            newMethodMap.put(m, 0);
        }

        mark(deletedLineNumbers, oldVisitor, newVisitor,  oldMethodMap, newClass, true);
        mark(addedLineNumbers, newVisitor, oldVisitor, newMethodMap, newClass, false);
        for(String m : oldMethodMap.keySet()){
            if(!newMethodMap.keySet().contains(m)){ // 成员方法被删除或者方法签名被修改，情况1、3、4
                switch (oldMethodMap.get(m)){
                    case 3:
                        String similarMethod = methodNameUpdateMap.get(newClass).get(m);
                        if(!methodUpdateMap.keySet().contains(newClass)){
                            methodUpdateMap.put(newClass, new HashMap<>());
                        }
                        if(!methodUpdateMap.get(newClass).keySet().contains(UpdateInfo.Operation.MODIFY)){
                            methodUpdateMap.get(newClass).put(UpdateInfo.Operation.MODIFY, new ArrayList<>());
                        }
                        methodUpdateMap.get(newClass).get(UpdateInfo.Operation.MODIFY).add(similarMethod);
                        break;
                    case 4:
                        if (!methodUpdateMap.keySet().contains(newClass)) {
                            methodUpdateMap.put(newClass, new HashMap<>());
                        }
                        if (!methodUpdateMap.get(newClass).keySet().contains(UpdateInfo.Operation.DELETE)) {
                            methodUpdateMap.get(newClass).put(UpdateInfo.Operation.DELETE, new ArrayList<>());
                        }
                        methodUpdateMap.get(newClass).get(UpdateInfo.Operation.DELETE).add(m);
                        break;
                }
            }else if(oldMethodMap.get(m) == 2 || newMethodMap.get(m) == 2){ // 成员方法被修改（包括删除、增加、修改）
                if(!methodUpdateMap.keySet().contains(newClass)){
                    methodUpdateMap.put(newClass, new HashMap<>());
                }
                if(!methodUpdateMap.get(newClass).keySet().contains(UpdateInfo.Operation.MODIFY)){
                    methodUpdateMap.get(newClass).put(UpdateInfo.Operation.MODIFY, new ArrayList<>());
                }
                methodUpdateMap.get(newClass).get(UpdateInfo.Operation.MODIFY).add(m);
            }
        }

        for(String m : newMethodMap.keySet()){
            if (newMethodMap.get(m) == 5) {
                if (!methodUpdateMap.keySet().contains(newClass)) {
                    methodUpdateMap.put(newClass, new HashMap<>());
                }
                if (!methodUpdateMap.get(newClass).keySet().contains(UpdateInfo.Operation.ADD)) {
                    methodUpdateMap.get(newClass).put(UpdateInfo.Operation.ADD, new ArrayList<>());
                }
                methodUpdateMap.get(newClass).get(UpdateInfo.Operation.ADD).add(m);
            }
        }

        for(String f : oldVisitor.fields){
            if(!newVisitor.fields.contains(f)){ // 成员变量被删除
                if(!fieldUpdateMap.keySet().contains(newClass)){
                    fieldUpdateMap.put(newClass, new HashMap<>());
                }
                if(!fieldUpdateMap.get(newClass).keySet().contains(UpdateInfo.Operation.DELETE)){
                    fieldUpdateMap.get(newClass).put(UpdateInfo.Operation.DELETE, new ArrayList<>());
                }
                fieldUpdateMap.get(newClass).get(UpdateInfo.Operation.DELETE).add(f);
            }
        }
        for(String f : newVisitor.fields){
            if(!oldVisitor.fields.contains(f)){ // 成员变量被增加
                if(!fieldUpdateMap.keySet().contains(newClass)){
                    fieldUpdateMap.put(newClass, new HashMap<>());
                }
                if(!fieldUpdateMap.get(newClass).keySet().contains(UpdateInfo.Operation.ADD)){
                    fieldUpdateMap.get(newClass).put(UpdateInfo.Operation.ADD, new ArrayList<>());
                }
                fieldUpdateMap.get(newClass).get(UpdateInfo.Operation.ADD).add(f);
            }
        }
    }

    public UpdateInfo getVersionUpdateInfo(String projectPath, String lastCommitId, String newCommitId){

        ArrayList<String> lines = getCmdOutput(
                String.format("git diff -M4 %s %s --unified=100000000", lastCommitId, newCommitId),
                projectPath);

        Pattern aPattern = Pattern.compile("^--- (.*\\.java|/dev/null)");
        Pattern bPattern = Pattern.compile("^\\+\\+\\+ (.*\\.java|/dev/null)");
        Pattern endPattern = Pattern.compile("^\\\\ No newline at end of file|^diff --git ");

        String oldClass = null;
        String newClass = null;
        ArrayList<String> oldJavaFileLines = new ArrayList<>();
        ArrayList<String> newJavaFileLines = new ArrayList<>();
        HashMap<Integer, Integer> deletedLineNumbers = new HashMap<>(); // <oldLineNumber, newLineNumber+1>
        HashMap<Integer, Integer> addedLineNumbers = new HashMap<>(); // <newLineNumber, oldLineNumber>
        int oldLineNumber = 0, newLineNumber = 0; //代码的行号从1开始
        for(String line : lines){
            if(line.startsWith("@@") && line.endsWith("@@")){
                oldLineNumber = 0;
                newLineNumber = 0;
                continue;
            }
            Matcher am = aPattern.matcher(line);
            Matcher bm = bPattern.matcher(line);
            Matcher em = endPattern.matcher(line);
            if(em.find()){
                if(oldClass != null || newClass != null){
                    process(oldClass, newClass,
                            oldJavaFileLines, newJavaFileLines,
                            deletedLineNumbers, addedLineNumbers);
                    oldClass = null;
                    newClass = null;
                    oldJavaFileLines = new ArrayList<>();
                    newJavaFileLines = new ArrayList<>();
                    deletedLineNumbers = new HashMap<>();
                    addedLineNumbers = new HashMap<>();
                }
            }
            if(am.find()){
                String g = am.group(1);
                if(g.endsWith(".java")) {
                    oldClass = g.substring(g.lastIndexOf("/")+1, g.length()-5);
                }

            }else if(bm.find()){
                String g = bm.group(1);
                if(g.endsWith(".java")) {
                    newClass = g.substring(g.lastIndexOf("/")+1, g.length()-5);
                }
            }
            if((oldClass != null || newClass != null) && !aPattern.matcher(line).find() &&
                    !bPattern.matcher(line).find()) {
                if(line.startsWith("-")){
                    oldLineNumber += 1;
                    deletedLineNumbers.put(oldLineNumber, newLineNumber+1);
                    oldJavaFileLines.add(line.substring(1));
                }
                else if(line.startsWith("+")){
                    newLineNumber += 1;
                    addedLineNumbers.put(newLineNumber, oldLineNumber);
                    newJavaFileLines.add(line.substring(1));
                }
                else{
                    oldLineNumber += 1;
                    newLineNumber += 1;
                    oldJavaFileLines.add(line);
                    newJavaFileLines.add(line);
                }
            }
        }
        if(oldClass != null || newClass != null){
            process(oldClass, newClass,
                    oldJavaFileLines, newJavaFileLines,
                    deletedLineNumbers, addedLineNumbers);
        }

        UpdateInfo updateInfo = new UpdateInfo();
        updateInfo.setClassUpdateMap(classUpdateMap);
        updateInfo.setMethodUpdateMap(methodUpdateMap);
        updateInfo.setFieldUpdateMap(fieldUpdateMap);
        updateInfo.setClassNameUpdateMap(classNameUpdateMap);
        updateInfo.setMethodNameUpdateMap(methodNameUpdateMap);
        return updateInfo;
    }

    public static void main(String[] args){
        new ParseDiffData().getVersionUpdateInfo("org.aspectj", "7d8b14cc98a2", "dd88d21ef6");
    }

}

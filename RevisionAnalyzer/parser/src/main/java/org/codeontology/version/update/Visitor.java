package org.codeontology.version.update;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;

import java.util.*;

public class Visitor extends ASTVisitor {

    class Bound {
        Integer startLineNumber;
        Integer endLineNumber;
        Bound(Integer startLineNumber, Integer endLineNumber){
            this.startLineNumber = startLineNumber;
            this.endLineNumber = endLineNumber;
        }
    }

    HashMap<String, HashSet<Integer>> lineNumbersOfSignature = new HashMap<>();
    HashSet<String> fields = new HashSet<>();
    ArrayList<Bound> bounds = new ArrayList<>();
    HashMap<Integer, String> boundIndexToSignature = new HashMap<>();
    String packageName = null;
    private CompilationUnit parser;

    CompilationUnit getCompilationUnit(String src) {
        ASTParser parser = ASTParser.newParser(AST.JLS10); //设置Java语言规范版本
        parser.setKind(ASTParser.K_COMPILATION_UNIT);

        parser.setCompilerOptions(null);
        parser.setResolveBindings(true);

        Map<String, String> compilerOptions = JavaCore.getOptions();
        compilerOptions.put(JavaCore.COMPILER_COMPLIANCE, JavaCore.VERSION_1_8); //设置Java语言版本
        compilerOptions.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, JavaCore.VERSION_1_8);
        compilerOptions.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_1_8);
        parser.setCompilerOptions(compilerOptions); //设置编译选项

        parser.setSource(src.toCharArray());
        return (CompilationUnit) parser.createAST(null);
    }

    Visitor(ArrayList<String> javaFileLines) {
        String javaCode = StringUtils.join(javaFileLines, "\n");
        parser = getCompilationUnit(javaCode);
        parser.accept(this);
    }

    @Override
    public boolean visit(PackageDeclaration node){
        packageName = node.getName().toString();
        return false;
    }

    @Override
    public boolean visit(FieldDeclaration node){
        List<VariableDeclarationFragment> vfs = node.fragments();
        for(VariableDeclarationFragment vf : vfs){
            String fieldName = vf.getName().getIdentifier();
            fields.add(fieldName);
        }
        return false;
    }

    @Override
    public boolean visit(MethodDeclaration node){

        HashSet<Integer> lineNumbers = new HashSet<>();
        lineNumbers.add(parser.getLineNumber(node.getName().getStartPosition()));
        String method_name = node.getName().toString();
        StringBuilder argsBuilder = new StringBuilder();
        List<SingleVariableDeclaration> ps = node.parameters();
        for(int i= 0; i < ps.size(); i++){
            lineNumbers.add(parser.getLineNumber(ps.get(i).getStartPosition()));
            lineNumbers.add(parser.getLineNumber(ps.get(i).getStartPosition()+ps.get(i).getLength()-1));
            argsBuilder.append(ps.get(i).getType().toString());
            if(i != ps.size()-1) argsBuilder.append(", ");
        }
        String args = argsBuilder.toString();
        String signature = String.format("%s(%s)", method_name, args);
        lineNumbersOfSignature.put(signature, lineNumbers);

        // 行号从1开始
        int startLineNumber = parser.getLineNumber(node.getStartPosition());
        int endLineNumber = parser.getLineNumber(node.getStartPosition()+node.getLength()-1);
        boundIndexToSignature.put(bounds.size(), signature);
        bounds.add(new Bound(startLineNumber, endLineNumber));
        return false;
    }

}


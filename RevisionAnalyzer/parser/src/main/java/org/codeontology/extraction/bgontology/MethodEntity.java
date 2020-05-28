package org.codeontology.extraction.bgontology;

import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.codeontology.BGOntology;
import org.codeontology.CodeOntology;
import org.codeontology.extraction.EntityFactory;
import org.codeontology.extraction.NamedElementEntity;
import org.codeontology.extraction.RDFLogger;
import org.codeontology.extraction.ReflectionFactory;
import org.codeontology.neo4j.Neo4jNode;
import org.codeontology.neo4j.Neo4jQuery;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtExecutableReferenceExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.ReferenceTypeFilter;
import spoon.support.reflect.reference.CtExecutableReferenceImpl;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.*;
import java.util.stream.Collectors;

public class MethodEntity extends NamedElementEntity<CtExecutable<?>> {

    private List<ParameterEntity> parameters;
    private Set<MethodEntity> methodInvocations;
    private String shotSignature;
    private boolean isConstructor = false;

    public MethodEntity(CtExecutable<?> method) {
        super(method);
        methodInvocations = new HashSet<>();
    }

    public MethodEntity(CtExecutableReference<?> reference) {
        super(reference);
        methodInvocations = new HashSet<>();
    }

    public void setIsConstructor(boolean isConstructor){
        this.isConstructor = isConstructor;
    }

    @Override
    protected RDFNode getType() {
        if(isDeclarationAvailable()) {
            return BGOntology.METHOD_ENTITY;
        }else {
            return BGOntology.API_ENTITY;
        }
    }

    @Override
    public String buildRelativeURI() {
        CtExecutableReference reference = (CtExecutableReference)getReference();
        TypeEntity parentClass;
        if(reference.getParent() instanceof CtTypeReference){
            parentClass = EntityFactory.getInstance().wrap((CtTypeReference<?>) reference.getParent());
        }else{
            if(reference.getDeclaringType() == null){
                String position = reference.getParent().getPosition().toString();
                throw new RuntimeException("cannot find method's parent class, method: "+reference.toString() + ", position="+position);
            }
            parentClass = EntityFactory.getInstance().wrap(reference.getDeclaringType());
        }
        String methodSignature = getReference().toString();
        String methodName = methodSignature.substring(0, methodSignature.indexOf("("));
        if(methodName.contains(".")){
            String[] split = methodName.split("\\.");
            methodSignature = methodSignature.replace(methodName, split[split.length-1]);
        }
        String uri = parentClass.getRelativeURI() + SEPARATOR + methodSignature;
//        uri = uri.replaceAll(",|#", SEPARATOR);
        return uri;
    }

    /**
     * 获取完整类名构成的方法签名
     */
    public String getMethodSignature(){
        String uri = getReference().toString();
        String[] split = uri.split("#");
        return split[split.length -1];
    }

    /**
     * 获取简单类名构成的方法签名
     */
    public String getShortMethodSignature(){
        if(!StringUtils.isEmpty(shotSignature)){
            return shotSignature;
        }
        String methodName = getReference().getSimpleName();
        if(getElement() instanceof CtConstructor){
            methodName = ((TypeEntity)getParent()).getReference().getSimpleName();
        }
        StringBuilder shotSigBuilder = new StringBuilder(methodName + "(");
        if(!CollectionUtils.isEmpty(getParameters())) {
            for (int i = 0; i < parameters.size(); i++) {
                ParameterEntity parameterEntity = parameters.get(i);
                String fullTypeName = parameterEntity.getJavaType().getRelativeURI();
                String[] split = fullTypeName.split("\\.");
                shotSigBuilder.append(split[split.length - 1]);
                if (i < parameters.size() - 1) {
                    shotSigBuilder.append(", ");
                }
            }
        }
        shotSigBuilder.append(")");
        shotSignature = shotSigBuilder.toString();
        return shotSignature;
    }

    public static String buildShotMethodSigRegex(String shotSignature, String className){
        String methodName = shotSignature.substring(0, shotSignature.indexOf('('));
        String paramStr = shotSignature.substring(shotSignature.indexOf('(')+1, shotSignature.indexOf(')'));
        List<String> paramList = Arrays.asList(paramStr.split(","));
        paramList = paramList.stream().map(p-> p.trim()).collect(Collectors.toList());
        StringBuilder regexBuilder = new StringBuilder(".*/"+className + SEPARATOR + methodName + "\\\\(");
        for(int i=0;i<paramList.size();i++){
            regexBuilder.append(".*"+escapeExprSpecialWord(paramList.get(i)));
            if(i<paramList.size()-1){
                regexBuilder.append(",");
            }
        }
        regexBuilder.append("\\\\).*");
        return regexBuilder.toString();
    }

    private static String escapeExprSpecialWord(String keyword) {
        if (StringUtils.isNotBlank(keyword)) {
            String[] fbsArr = {"\\", "$", "(", ")", "*", "+", ".", "[", "]", "?", "^", "{", "}", "|"};
            for (String key : fbsArr) {
                if (keyword.contains(key)) {
                    keyword = keyword.replace(key, "\\\\" + key);
                }
            }
        }
        return keyword;
    }

//    public static String tranSignatureToURI(String signature, String className){
//        String uri = className + "#" + signature;
//        uri = uri.replaceAll(",|#", SEPARATOR);
//        return uri;
//    }

    @Override
    public void extract() {
        tagType();
        tagCommitID();

        if (isDeclarationAvailable()) {
            tagPosition();
            tagParameters();
            if(isConstructor) {
                tagIsConstructor();
            }else{
                tagReturnType();
            }
            tagSourceCode();
            tagInvocation();
        }
    }

    public void tagIsConstructor(){
        getLogger().addTriple(this, BGOntology.IS_CONSTRUCTOR_PROPERTY, getModel().createLiteral("true"));
    }

    public void tagParameters() {
        List<ParameterEntity> parameters = getParameters();
        int size = parameters.size();
        for (int i = 0; i < size; i++) {
            ParameterEntity parameter = parameters.get(i);
            parameter.setParent(this);
            parameter.setPosition(i);
            getLogger().addTriple(this, BGOntology.HAS_PROPERTY, parameter);
            parameter.extract();
        }
    }

    public List<ParameterEntity> getParameters() {
        if (parameters == null) {
            setParameters();
        }
        return parameters;
    }

    private void setParameters() {
        if (isDeclarationAvailable()) {
            List<CtParameter<?>> parameterList = getElement().getParameters();
            parameters = parameterList.stream()
                    .map(getFactory()::wrap)
                    .collect(Collectors.toCollection(ArrayList::new));
        } else {
            List<CtTypeReference<?>> references = ((CtExecutableReference<?>) getReference()).getParameters();
            parameters = references.stream()
                    .map(getFactory()::wrapByTypeReference)
                    .collect(Collectors.toCollection(ArrayList::new));
        }
    }

    private void tagReturnType(){
        TypeEntity<?> returnType = getGenericReturnType();
        if (returnType != null) {
            getLogger().addTriple(this, BGOntology.RETURN_TYPE_PROPERTY, returnType);
        }

        CtTypeReference<?> reference = ((CtExecutableReference<?>) getReference()).getType();
        returnType = getFactory().wrap(reference);

        if (returnType != null) {
            if(TypeKind.getIgnoreTypeKind().contains(returnType.getKind())){
                return;
            }
            Resource javaType = null;
            if(CodeOntology.isVersionUpdate()){
                javaType = returnType.getLastVersionResource();
            }
            if(javaType == null){
                javaType = returnType.getResource();
                getLogger().addTriple(this, BGOntology.RETURN_TYPE_PROPERTY, javaType);
                returnType.setParent(this);
                returnType.follow();
            }else{
                getLogger().addTriple(this, BGOntology.RETURN_TYPE_PROPERTY, javaType);
            }
        }
    }

    private TypeEntity getGenericReturnType() {
        if (!isDeclarationAvailable()) {
            return null;
        }
        try {
            CtExecutableReference<?> reference = ((CtExecutableReference<?>) getReference());
            Method method = (Method) ReflectionFactory.getInstance().createActualExecutable(reference);
            Type returnType = method.getGenericReturnType();

            if (returnType instanceof GenericArrayType ||
                    returnType instanceof TypeVariable<?>) {

                TypeEntity<?> result = getFactory().wrap(returnType);
                result.setParent(this);
                return result;
            }

            return null;

        } catch (Throwable t) {
            return null;
        }
    }

    private void tagInvocation(){
        if(methodInvocations.size() == 0){
            getInvocations();
        }
        for(MethodEntity methodEntity : methodInvocations){
            Resource called = null;
            if(CodeOntology.isVersionUpdate()){
                called =  methodEntity.getLastVersionResource();
            }
            if(called == null){
                called = methodEntity.getResource();
                getLogger().addTriple(this, BGOntology.CALL_PROPERTY, called);
                methodEntity.follow();
            }else {
                getLogger().addTriple(this, BGOntology.CALL_PROPERTY, called);
            }
        }
    }

    private void getInvocations(){
        CtBlock<?> body = getElement().getBody();

        List<CtStatement> statements;
        try {
            statements = body.getStatements();
        } catch (NullPointerException e) {
            return;
        }
        for (CtStatement statement : statements) {
            List<CtExecutableReference<?>> references = statement.getElements(new ReferenceTypeFilter<>(CtExecutableReference.class));
            for (CtExecutableReference<?> reference : references) {
                if(reference.isConstructor()){//不考虑构造函数作为methodInvocation
                    continue;
                }
                if (!(reference.getParent() instanceof CtExecutableReferenceExpression<?, ?>)) {
                    try {
                        methodInvocations.add(getFactory().wrap(reference));
                    }catch (Exception e){
                        System.out.println("MethodEntity.getInvocations() Exception: " + e.getClass().getName() + e.getMessage());
                    }
                }
            }
        }

    }


    /**
     * 版本更新时新增方法
     */
    public void addNewMethod(String className){
        //method extract
        extract();
        //class has method关系建立
        Neo4jNode lastClass = Neo4jQuery.getLastVersionEntity(className, BGOntology.CLASS_ENTITY);
        if(lastClass== null){
            throw new RuntimeException("cannot find last version Class: " + className + ", method = "+getRelativeURI());
        }
        String uri = lastClass.uri();
        getLogger().addTriple(getModel().createResource(uri), BGOntology.HAS_PROPERTY, this.getResource());
    }

    /**
     * 版本更新时修改方法,不修改方法签名
     */
    public void modifyMethod(){
        extract();
        //建立新旧版本update关系
        Neo4jNode lastMethod = Neo4jQuery.getLastVersionEntity(getRelativeURI(), BGOntology.METHOD_ENTITY);
        if(lastMethod == null){
            System.out.println("WARN: MethodEntity.modifyMethod() cannot find lastMethod, method="+getRelativeURI());
            return;
        }
        String uri = lastMethod.uri();
        getLogger().addTriple(getModel().createResource(uri), BGOntology.UPDATE_PROPERTY, this.getResource());
    }

    /**
     * 版本更新时修改方法签名，同时重新extract方法体（主要是为了得到新旧对应关系）
     */
    public void updateEntityName(String oldName, String className){
        extract();
        //建立新旧版本实体的update关系
        Neo4jNode lastMethod = Neo4jQuery.getMethodByShotSignature(oldName, className);
        if(lastMethod== null){
            throw new RuntimeException("cannot find last version Method: " + className +"."+ oldName);
        }
        String uri = lastMethod.uri();
        if(lastMethod == null){
            return;
        }
        getLogger().addTriple(getModel().createResource(uri), BGOntology.UPDATE_PROPERTY, getResource());
    }
}

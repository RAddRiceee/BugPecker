package org.codeontology.version;

import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.codeontology.BGOntology;
import org.codeontology.CodeOntology;
import org.codeontology.extraction.EntityFactory;
import org.codeontology.extraction.RDFLogger;
import org.codeontology.extraction.bgontology.FieldEntity;
import org.codeontology.extraction.bgontology.MethodEntity;
import org.codeontology.extraction.bgontology.TypeEntity;
import org.codeontology.extraction.bgontology.TypeKind;
import org.codeontology.neo4j.Neo4jNode;
import org.codeontology.neo4j.Neo4jQuery;
import org.codeontology.util.ThreadPoolUtil;
import org.codeontology.version.update.UpdateInfo;
import org.codeontology.version.update.UpdateInfo.Operation;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class VersionUpdateProcessor extends AbstractProcessor<CtType<?>> {

    private UpdateInfo updateInfo;

    public VersionUpdateProcessor(UpdateInfo updateInfo){
        super();
        this.updateInfo = updateInfo;
    }

    @Override
    public void process(CtType<?> type) {
        if(!CodeOntology.haveUpdateName()){
            return;
        }

        if(TypeKind.getIgnoreTypeKind().contains(TypeKind.getKindOf(type))){
            return;
        }
        String className = type.getQualifiedName();

        if(updateInfo.getClassUpdateMap() == null){
            return;
        }
        TypeEntity typeEntity = EntityFactory.getInstance().wrap(type);

        //处理添加的class
        List<String> addClassList = updateInfo.getClassUpdateMap().get(Operation.ADD);
        if(!CollectionUtils.isEmpty(addClassList) && addClassList.contains(className)){
            typeEntity.extract();
            return;
        }

        /**
         * 修改class相关
         */
        List<String> modifyClassList = updateInfo.getClassUpdateMap().get(Operation.MODIFY);
        if(CollectionUtils.isEmpty(modifyClassList) || !modifyClassList.contains(className)){
            return;
        }

        ThreadPoolUtil.getInstance().execute(new Runnable() {
            @Override
            public void run() {

                //如果更新了类名，记录旧类名便于后续查找
                Map<String, String> classNameUpdateMap = updateInfo.getClassNameUpdateMap();
                String oldClassName = null;
                if(classNameUpdateMap!=null && classNameUpdateMap.size()!=0){
                    oldClassName = getKey(classNameUpdateMap, className);
                }

                /*************************************
                 * 成员变量相关
                 ************************************/
                if(updateInfo.getFieldUpdateMap() != null && updateInfo.getFieldUpdateMap().get(className) != null) {
                    Map<Operation, List<String>> fieldUpdateMap = updateInfo.getFieldUpdateMap().get(className);

                    List<FieldEntity> fieldEntityList = type.getFields().stream().map(ctField -> EntityFactory.getInstance().wrap(ctField)).collect(Collectors.toList());
                    List<String> fieldNameList = fieldEntityList.stream().map(fieldEntity -> fieldEntity.getReference().getSimpleName()).collect(Collectors.toList());
                    Map<String, FieldEntity> fieldNameEntityMap = fieldEntityList.stream().collect(Collectors.toMap(fieldEntity -> fieldEntity.getReference().getSimpleName(), fieldEntity -> fieldEntity));

                    //新增成员变量
                    List<String> addVariableList = CollectionUtils.isEmpty(fieldUpdateMap.get(Operation.ADD))? new ArrayList<>() : new ArrayList<>(fieldUpdateMap.get(Operation.ADD));
                    addVariableList.retainAll(fieldNameList);
                    if (!CollectionUtils.isEmpty(addVariableList)) {
                        for (String field : addVariableList) {
                            FieldEntity fieldEntity = fieldNameEntityMap.get(field);
                            try {
                                fieldEntity.addNewField(className);
                            }catch (RuntimeException e){
                                System.out.println("WARN: VersionUpdateProcessor call FieldEntity.addNewField() Exception: "+e.getMessage());
                            }
                        }
                    }

                    //删除成员变量
                    List<String> deleteVariableList = CollectionUtils.isEmpty(fieldUpdateMap.get(Operation.DELETE))? new ArrayList<>() : new ArrayList<>(fieldUpdateMap.get(Operation.DELETE));
                    if (!CollectionUtils.isEmpty(deleteVariableList)) {
                        for (String field : deleteVariableList) {
                            //考虑类名变更
                            String preClassName = StringUtils.isEmpty(oldClassName) ? className : oldClassName;
                            String relativeURI = FieldEntity.transNameToURI(field, preClassName);
                            setDelete(relativeURI, BGOntology.VARIABLE_ENTITY);
                        }
                    }
                }

                /*************************************
                 * 方法相关
                 ************************************/
                List<MethodEntity> methodEntityList = typeEntity.getMethods();
                if(TypeKind.getKindOf(type).equals(TypeKind.CLASS)){
                    methodEntityList.addAll(typeEntity.getConstructors());
                }
                List<String> methodSignatureList = methodEntityList.stream().map(methodEntity -> methodEntity.getShortMethodSignature()).collect(Collectors.toList());

                //进行了方法签名更新的方法列表newMethodNameList
                List<String> newMethodNameList = null;
                if(updateInfo.getMethodNameUpdateMap() != null && updateInfo.getMethodNameUpdateMap().get(className) != null) {
                    Map<String, String> methodNameUpdateMap = updateInfo.getMethodNameUpdateMap().get(className);
                    newMethodNameList = CollectionUtils.isEmpty(methodNameUpdateMap.values())? new ArrayList<>() : new ArrayList<>(methodNameUpdateMap.values());
                    newMethodNameList.retainAll(methodSignatureList);
                }

                if(updateInfo.getMethodUpdateMap() != null && updateInfo.getMethodUpdateMap().get(className) != null) {
                    Map<Operation, List<String>> methodUpdateMap = updateInfo.getMethodUpdateMap().get(className);

                    //修改方法
                    List<String> modifyMethodList = CollectionUtils.isEmpty(methodUpdateMap.get(Operation.MODIFY))? new ArrayList<>() : new ArrayList<>(methodUpdateMap.get(Operation.MODIFY));
                    modifyMethodList.retainAll(methodSignatureList);
                    if(!CollectionUtils.isEmpty(modifyMethodList)){
                        for(String signature : modifyMethodList){
                            MethodEntity methodEntity = methodEntityList.get(methodSignatureList.indexOf(signature));
                            Boolean modifySignature = !CollectionUtils.isEmpty(newMethodNameList) && newMethodNameList.contains(signature)? true :false;
                            if(!modifySignature){//如果同时修改了方法签名，再更新方法签名同时已经更新了方法体
                                methodEntity.modifyMethod();
                            }
                        }
                    }

                    //新增方法
                    List<String> addMethodList = CollectionUtils.isEmpty(methodUpdateMap.get(Operation.ADD))? new ArrayList<>() : new ArrayList<>(methodUpdateMap.get(Operation.ADD));
                    addMethodList.retainAll(methodSignatureList);
                    if (!CollectionUtils.isEmpty(addMethodList)) {
                        for (String signature : addMethodList) {
                            MethodEntity methodEntity = methodEntityList.get(methodSignatureList.indexOf(signature));
                            try {
                                methodEntity.addNewMethod(className);
                            }catch (RuntimeException e){
                                System.out.println("WARN: VersionUpdateProcessor call MethodEntity.addNewMethod() Exception: "+e.getMessage());
                            }
                        }
                    }

                    //删除方法
                    List<String> deleteMethodList = CollectionUtils.isEmpty(methodUpdateMap.get(Operation.DELETE))? new ArrayList<>() : new ArrayList<>(methodUpdateMap.get(Operation.DELETE));
                    if (!CollectionUtils.isEmpty(deleteMethodList)) {
                        for (String signature : deleteMethodList) {
                            //考虑类名变更
                            String preClassName = StringUtils.isEmpty(oldClassName) ? className : oldClassName;
                            Neo4jNode node = Neo4jQuery.getMethodByShotSignature(signature, preClassName);
                            if(node != null){
                                deleteByURI(node.uri());
                            }
                        }
                    }
                }
            }
        });

    }

    /**
     * 删除class
     */
    public static void deleteClass(UpdateInfo updateInfo){
        if(updateInfo == null || updateInfo.getClassUpdateMap() == null || updateInfo.getClassUpdateMap().size() == 0){
            return;
        }

        List<String> deleteClassList = updateInfo.getClassUpdateMap().get(Operation.DELETE);
        if(CollectionUtils.isEmpty(deleteClassList)){
            return;
        }
        for(String classURI : deleteClassList){
            setDelete(classURI, BGOntology.CLASS_ENTITY);
        }
    }

    /**
     * 类、成员变量的删除
     */
    private static void setDelete(String relativeURI, RDFNode entityType){
        Neo4jNode neo4jNode = Neo4jQuery.getLastVersionEntity(relativeURI, entityType);
        if(neo4jNode == null){
            return;
        }
        deleteByURI(neo4jNode.uri());
    }

    private static void deleteByURI(String uri){
        Resource resource = RDFLogger.getInstance().getModel().createResource(uri);
        RDFLogger.getInstance().addTriple(resource, BGOntology.DELETE_VERSION_PROPERTY, RDFLogger.getInstance().getModel().createLiteral(CodeOntology.getVersion()));
    }

    private static String getKey(Map<String,String> map,String value){
        String key=null;
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if(value.equals(entry.getValue())){
                key=entry.getKey();
            }
        }
        return key;
    }
}

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

/**
 * 先处理类名和方法名的变更
 */
public class VersionUpdatePreProcessor extends AbstractProcessor<CtType<?>> {

    private UpdateInfo updateInfo;

    public VersionUpdatePreProcessor(UpdateInfo updateInfo){
        super();
        this.updateInfo = updateInfo;
    }

    @Override
    public void process(CtType<?> type) {
        if(CodeOntology.haveUpdateName()){
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

        Map<String, String> classNameUpdateMap = updateInfo.getClassNameUpdateMap();
        if((updateInfo.getMethodNameUpdateMap() == null || updateInfo.getMethodNameUpdateMap().get(className) == null) &&
                StringUtils.isEmpty(getKey(classNameUpdateMap, className))){
            return;
        }

        ThreadPoolUtil.getInstance().execute(new Runnable() {
            @Override
            public void run() {
                //更新类名
                String oldClassName = null;
                if(classNameUpdateMap!=null && classNameUpdateMap.size()!=0){
                    oldClassName = getKey(classNameUpdateMap, className);
                    if(!StringUtils.isEmpty(oldClassName)){
                        try {
                            typeEntity.updateEntityName(oldClassName);
                        }catch (RuntimeException e){
                            System.out.println("WARN: VersionUpdatePreProcessor.updateClassEntityName Exception: " + e.getClass().getName() + e.getMessage());
                            //改为添加类
                            typeEntity.extract();
                            return;
                        }
                    }
                }

                //更新方法签名
                List<String> newMethodNameList = null;
                if(updateInfo.getMethodNameUpdateMap() != null && updateInfo.getMethodNameUpdateMap().get(className) != null) {
                    List<MethodEntity> methodEntityList = typeEntity.getMethods();
                    if(TypeKind.getKindOf(type).equals(TypeKind.CLASS)){
                        methodEntityList.addAll(typeEntity.getConstructors());
                    }
                    List<String> methodSignatureList = methodEntityList.stream().map(methodEntity -> methodEntity.getShortMethodSignature()).collect(Collectors.toList());

                    Map<String, String> methodNameUpdateMap = updateInfo.getMethodNameUpdateMap().get(className);
                    newMethodNameList = CollectionUtils.isEmpty(methodNameUpdateMap.values())? new ArrayList<>() : new ArrayList<>(methodNameUpdateMap.values());

                    newMethodNameList.retainAll(methodSignatureList);

                    if (!CollectionUtils.isEmpty(newMethodNameList)) {
                        for (String newMethodName : newMethodNameList) {
                            MethodEntity methodEntity = methodEntityList.get(methodSignatureList.indexOf(newMethodName));
                            String preClassName = StringUtils.isEmpty(oldClassName) ? className : oldClassName;
                            try {
                                methodEntity.updateEntityName(getKey(methodNameUpdateMap, newMethodName), preClassName);
                            }catch (RuntimeException e){
                                System.out.println("WARN: VersionUpdatePreProcessor call methodEntity.updateEntityName Exception: "+ e.getMessage());
                                //改为添加方法
                                try {
                                    methodEntity.addNewMethod(className);
                                }catch (RuntimeException e1){
                                    System.out.println("WARN: VersionUpdatePreProcessor call methodEntity.updateEntityName.addNewMethod Exception: "+ e1.getMessage());
                                }
                            }
                        }
                    }
                }
            }
        });
    }

    private static String getKey(Map<String,String> map,String value){
        if(map == null){
            return null;
        }
        String key=null;
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if(value.equals(entry.getValue())){
                key=entry.getKey();
            }
        }
        return key;
    }
}

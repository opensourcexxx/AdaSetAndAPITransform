import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.text.edits.TextEdit;
import org.simmetrics.StringMetric;
import org.simmetrics.metrics.StringMetrics;
import java.io.*;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class main {
    static double sum = 0;
    static int number = 0;
    static int correctNumber = 0;
    static int parseErrorNumber = 0;
    public static void recurrentComputeSimilarity(String dirPath) {
        File dir = new File(dirPath);
        for(File f: Objects.requireNonNull(dir.listFiles())){
            if (f.isDirectory()){recurrentComputeSimilarity(f.getAbsolutePath());}
            else {
                for(File f2: Objects.requireNonNull(dir.listFiles())){
                    if (f2.isDirectory()){continue;}
                    else if(f.getName().equals(f2.getName())){continue;}
                    else{
                        try {
                            float similarity = generateAndEvaluateAdaptation(f.getAbsolutePath(),f2.getAbsolutePath());
                            sum += similarity;
                            number +=1;
                            if (similarity>=0.90) correctNumber++;
                            System.out.println("number "+number+" correctNumber "+correctNumber+" similarity "+similarity+" avgSim "+sum/number+" sum "+sum+" f1Path:"+f.getAbsolutePath()+" f2Path:"+f2.getAbsolutePath());
                        } catch (IOException e) {
                            e.printStackTrace();
                            ;
                        }

                    }
                }
            }
        }

    }

    public static void main(String[] args) throws Exception {
        zipUncompress("AdaSet_AddedDeletedOnly.zip",".");
        recurrentComputeSimilarity("AdaSet_AddedDeletedOnly/");
        System.out.println("correctness "+correctNumber*1.0/number+" similarity "+sum/number+" parseErrorNumber "+parseErrorNumber);
    }

    public static void zipUncompress(String inputFile,String destDirPath) throws Exception {
        File srcFile = new File(inputFile);
        if (!srcFile.exists()) {
            throw new Exception(srcFile.getPath() + "file not exist");
        }
        ZipFile zipFile = new ZipFile(srcFile);
        Enumeration<?> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = (ZipEntry) entries.nextElement();
            if (entry.isDirectory()) {
//                if (entry.getName().equals("__MACOSX")) continue;
                String dirPath = destDirPath + "/" + entry.getName();
                srcFile.mkdirs();
            } else {
                File targetFile = new File(destDirPath + "/" + entry.getName());
                if (!targetFile.getParentFile().exists()) {
                    targetFile.getParentFile().mkdirs();
                }
                targetFile.createNewFile();
                InputStream is = zipFile.getInputStream(entry);
                FileOutputStream fos = new FileOutputStream(targetFile);
                int len;
                byte[] buf = new byte[1024];
                while ((len = is.read(buf)) != -1) {
                    fos.write(buf, 0, len);
                }
                fos.close();
                is.close();
            }
        }
    }

    public static float generateAndEvaluateAdaptation(String adaptationAPath, String adaptationBPath) throws IOException {
        String affectedAPath = adaptationAPath.replace("adaptation","affected");
        String affectedBPath = adaptationBPath.replace("adaptation","affected");
        File affectedAFile = new File(affectedAPath);
        File affectedBFile = new File(affectedBPath);
        File adaptationAFile = new File(adaptationAPath);
        File adaptationBFile = new File(adaptationBPath);
        String affectedASource = getFileContentWithoutPrefix(affectedAPath);
        String affectedBSource = getFileContentWithoutPrefix(affectedBPath);
        String adaptationASource = getFileContentWithoutPrefix(adaptationAPath);
        String adaptationBSource = getFileContentWithoutPrefix(adaptationBPath);
        int affectedALength = affectedASource.split("\n").length;
        int adaptationALength = adaptationASource.split("\n").length;
        int affectedBLength = affectedBSource.split("\n").length;
        int adaptationBLength = adaptationBSource.split("\n").length;
        File parentFile = new File( affectedAFile.getParent());
//        String brokenType = parentFile.getName().split("_")[0]; // for windows
//        String brokenSubType = parentFile.getName().split("_")[1]; // for windows
//        String brokenApi = parentFile.getName().replace(brokenType+"_","").replace(brokenSubType+"_","");
        String brokenType = parentFile.getName().split(":")[0];
        String brokenSubType = parentFile.getName().split(":")[1];
        String brokenApi = parentFile.getName().split(":")[2];
        String adaptationType = "";
        String generatedAffectedSource = "";
        String generatedAdaptationSource = "";
        int generatedAffectedLength = 0;
        int generatedAdaptationLength = 0;

        // determine transform type
        if(adaptationALength==0){
            adaptationType = "Delete";
        }
        else if (adaptationALength==affectedALength && adaptationALength==1){
            adaptationType = "AlterAPI";
        }
        else if (adaptationALength!=affectedALength && adaptationALength>=1){
            adaptationType = "AlterCode";
        }

        // generated adaptation
        if(brokenType.contains("Method")){
            String brokenMethodName = brokenApi.split(" ")[1];
            if(adaptationType.equals("Delete")){
                generatedAffectedLength = affectedALength;
                generatedAdaptationLength = 0;
                generatedAffectedSource = getGeneratedAffectedSource(generatedAffectedLength,affectedASource,affectedBSource);
                generatedAdaptationSource = "";
            }
            else if (adaptationType.equals("AlterAPI")){
                generatedAffectedLength = affectedALength;
                generatedAdaptationLength = adaptationALength;
                generatedAffectedSource = getGeneratedAffectedSource(generatedAffectedLength,affectedASource,affectedBSource);
                // find broken api and map parameters and return variable
//                generatedAdaptationSource = adaptationASource; // Directly Copy
//                generatedAdaptationSource = ""; // Directly Deleted
                try { // API Transformer
                    generatedAdaptationSource = getAlterAPIGeneratedAdaptationSource(generatedAdaptationLength, brokenApi,
                            affectedASource, affectedBSource, generatedAffectedSource,adaptationASource,adaptationBSource);
                }
                catch (Exception e){
                    e.printStackTrace();
                    generatedAdaptationSource = adaptationASource;
                    parseErrorNumber++;
                }
            }
            else{
                generatedAffectedLength = affectedALength;
                generatedAdaptationLength = adaptationALength;
                generatedAffectedSource = getGeneratedAffectedSource(generatedAffectedLength,affectedASource,affectedBSource);
//                generatedAdaptationSource = "";
                generatedAdaptationSource = adaptationASource;
            }
        }
        else if(brokenType.contains("Type")){
//            throw new IOException();

            String brokenTypeName = brokenApi;
            generatedAffectedLength = affectedALength;
            generatedAdaptationLength = adaptationALength;
            generatedAffectedSource = getGeneratedAffectedSource(generatedAffectedLength,affectedASource,affectedBSource);
            generatedAdaptationSource = adaptationASource;
        }
        else if(brokenType.contains("Filed")){
//            throw new IOException();

            String brokenTypeName = brokenApi.split(" ")[1];
            String brokenFiledName = brokenApi.split(" ")[0];
            generatedAffectedLength = affectedALength;
            generatedAdaptationLength = adaptationALength;
            generatedAffectedSource = getGeneratedAffectedSource(generatedAffectedLength,affectedASource,affectedBSource);
            generatedAdaptationSource = adaptationASource;
        }
        // remove blank space chart than evaluate similarity. String.replaceAll("\\s*","")

        // generate delete code
        // generate adding code
        String str1 = (affectedBSource + adaptationBSource).replaceAll("\\s*","");
        String str2 = (generatedAffectedSource + generatedAdaptationSource).replaceAll("\\s*","");
//        StringMetric metric = StringMetrics.cosineSimilarity();
        StringMetric metric1 = StringMetrics.levenshtein();
        float result = metric1.compare(str1, str2);
        return result;
    }

    public static class AlterMethodVisitor extends ASTVisitor {
        public APIUsage targetApiUsage = null;
        public APIUsage apiUsage=null;
        @Override
        public boolean visit(MethodInvocation node) { // for Method broken api
            List<Parameter> targetParameters = targetApiUsage.parameters;
            String targetParentExpression = targetApiUsage.parentExpression;
            String targetName = targetApiUsage.name;
            String methodName = node.getName().toString();
            String parentExpression;
            if(node.getExpression()==null){parentExpression = "null";}
            else{parentExpression = node.getExpression().toString();}
            double ratio = 0.0;
            int hitCount = 0;
            int targetParameterLength = targetParameters.size();
            List argumentsAST = node.arguments();
            List<Parameter> parameters = new LinkedList<>();
            int parameterLength = argumentsAST.size();
            int lagerLength = 1 + Math.max(targetParameterLength, parameterLength);
            // parent expression
            if (targetParentExpression.equals(parentExpression)){hitCount++;}
            // parameter list
            for (int i=0 ; i<parameterLength ; i++){
                if(argumentsAST.get(i) instanceof Expression){
                    Expression exp = (Expression) argumentsAST.get(i);
                    String parameterName = exp.toString();
                    int index = -1;
                    String type = "";
                    int oldHitCount= hitCount;
                    for(Parameter p:targetParameters){
                        if(p.content.equals(parameterName)){
                            hitCount++;
                            index = targetParameters.indexOf(p);
                            break;
                        }
                    }
                    if (oldHitCount==hitCount){parameters.add(new Parameter("new",parameterName,index));}
                    else {parameters.add(new Parameter("reserve",parameterName,index));}
                }
            }
            ratio= hitCount*1.0/lagerLength;
            if (targetName.equals(methodName)){apiUsage = new APIUsage(methodName,parameters,parentExpression);}
            else if(ratio>=0.3){apiUsage = new APIUsage(methodName,parameters,parentExpression);}// 超参
            return super.visit(node);
        }
    }
    public static class BrokenMethodVisitor extends ASTVisitor {
        public String targetMethodName = "";
        public APIUsage apiUsage=null;
        @Override
        public boolean visit(MethodInvocation node) { // for Method broken api
            String methodName = node.getName().toString();
            String parentExpression;
            if(node.getExpression()==null){parentExpression = "null";}
            else{parentExpression = node.getExpression().toString();}
            if (methodName.equals(targetMethodName)){
                // parameter list
                List argumentsAST = node.arguments();
                List<Parameter> parameters = new LinkedList<>();
                int parameterLength = argumentsAST.size();
                for (int i=0 ; i<parameterLength ; i++){
                    if(argumentsAST.get(i) instanceof Expression){
                        Expression exp = (Expression) argumentsAST.get(i);
                        parameters.add(new Parameter("unknown",exp.toString(),i));
                    }
                }
                apiUsage = new APIUsage(methodName,parameters,parentExpression);
            }
            return super.visit(node);
        }
    }
    public static class BrokenFieldVisitor extends ASTVisitor {
        @Override // QualifiedName = FieldAccess // not include member function
        public boolean visit(QualifiedName node)   { // for Field broken api
            String typeName;
            if (node.getQualifier().resolveTypeBinding()!=null){typeName = node.getQualifier().resolveTypeBinding().getName();}
            else{typeName="null";}
            String fieldName = node.getName().toString();
            int position  = node.getStartPosition();
            System.out.println(node);
            return super.visit(node);
        }
    }
    public static class BrokenTypeVisitor extends ASTVisitor {
        @Override // VariableDeclarationStatement
        public boolean visit(VariableDeclarationStatement node)   { // for Type broken api
            String typeName;
            if (node.getType().resolveBinding()!=null){typeName = node.getType().resolveBinding().getName();}
            else {typeName = "null";}
            int position = node.getStartPosition();
            System.out.println(typeName);
            System.out.println(position);
            System.out.println(node);
            return super.visit(node);
        }
    }
    public static class Parameter {
        public String type; // new, reserve for adaptation code or unknown for affected code
        public String content; // name of parameter
        public int index; // for AlterApi adaptation code, index is the position of correspond parameter in affected code
        Parameter(String _type, String _content, int _index){
            type = _type;
            content = _content;
            index = _index;
        }
    }
    public static class APIUsage{
        public String name; // method nmae
        public List<Parameter> parameters; // name of parameter
        public String parentExpression;
        APIUsage(String _name,List<Parameter> _parameters,String _parentExpression){
            name = _name;
            parameters = _parameters;
            parentExpression = _parentExpression;
        }
    }

    public static String getAlterAPIGeneratedAdaptationSource(int generatedAdaptationLength, String brokenApi, String affectedASource, String affectedBSource, String generatedAffectedSource,String adaptationASource,String adaptationBSource){
        String brokenMethodName = brokenApi.split(" ")[1];
        String generatedAdaptationSource = "";
        // find broken api and map parameters
        if (number==9){
            System.out.println("point");
        }
        APIUsage affectedAUsage =  getAPIUsageFromMethodName(brokenMethodName,affectedASource);
        if (affectedAUsage==null) {return adaptationASource;}
        APIUsage generatedAffectedUsage =  getAPIUsageFromMethodName(brokenMethodName,generatedAffectedSource);
        if (generatedAffectedUsage==null) {return adaptationASource;}
        APIUsage adaptationAUsage =  getAPIUsageFromParametersName(affectedAUsage,adaptationASource);
        if (adaptationAUsage==null){return adaptationASource;}
//        APIUsage adaptationAUsage =  getAPIUsageFromMethodName(brokenMethodName,adaptationASource);
//        if (adaptationAUsage==null){adaptationAUsage =  getAPIUsageFromParametersName(affectedAUsage,adaptationASource);}
        // generate
//        APIUsage generatedAdaptationUsage = generatedMethodAPIUsage(generatedAffectedUsage,adaptationAUsage);

        String result = generatedSourcecodeWithAPIUsage(generatedAffectedSource,generatedAffectedUsage,adaptationAUsage);
        return result;
    }

    public static String generatedSourcecodeWithAPIUsage(String sourcecode, APIUsage oldAPIUsage, APIUsage newAPIUsage){
        org.eclipse.jface.text.Document document = new org.eclipse.jface.text.Document(sourcecode);
        ASTParser parser = ASTParser.newParser(AST.JLS8);
        parser.setSource(document.get().toCharArray()); // read java file and remove prefix (-,+)
        parser.setKind(ASTParser.K_STATEMENTS);
        Block block = (Block) parser.createAST(null); // creat ast
        ASTRewrite astRewrite = ASTRewrite.create(block.getAST());
        AST ast = block.getAST();
        EidtMethodVisitor emv = new EidtMethodVisitor();
        emv.rewriter = astRewrite;
        emv.ast = ast;
        emv.document =document;
        emv.block = block;
        emv.oldAPIUsage = oldAPIUsage;
        emv.newAPIUsage =newAPIUsage;
        block.accept(emv);
        String result = document.get();

        TextEdit edits = astRewrite.rewriteAST(document,null);
        try {
            edits.apply(document);
        } catch (BadLocationException e) {
            e.printStackTrace();
        }

        return document.get();
    }
    public static MethodInvocation getMIFromString(AST ast,String exp){
        MethodInvocation mi = ast.newMethodInvocation();
        String name = exp.split("\\(")[0];
        String parameters = exp.replace(name,"").replace("\\)","");
        if (name.contains("\\.")){mi.setName((SimpleName) ast.newName(name));}
        else { mi.setName(ast.newSimpleName(name));}
        return mi;
    }
    public static class EidtMethodVisitor extends ASTVisitor {
        public ASTRewrite rewriter = null;
        public AST ast = null;
        public Block block = null;
        public org.eclipse.jface.text.Document document;
        public APIUsage oldAPIUsage;
        public APIUsage newAPIUsage;
        @Override
        public boolean visit(MethodInvocation node) { // for Method broken api
            String brokenMethodName = oldAPIUsage.name;
            String methodName = node.getName().toString();
            String parentExpression = newAPIUsage.parentExpression;
            if (methodName.equals(brokenMethodName)){
                // create new statements for insertion
                MethodInvocation newInvocation = ast.newMethodInvocation();
                newInvocation.setName(ast.newSimpleName(newAPIUsage.name));
                ASTParser parserT = ASTParser.newParser(AST.JLS8);
                parserT.setKind(ASTParser.K_EXPRESSION);
                parserT.setSource(parentExpression.toCharArray()); // read java file and remove prefix (-,+)
                Expression pExpression = (Expression) parserT.createAST(null); // creat ast
                newInvocation.setExpression((Expression) ASTNode.copySubtree(ast,pExpression));
                for (Parameter p:newAPIUsage.parameters){
                    int i = newAPIUsage.parameters.indexOf(p);
                    if(p.index==-1){
                        String source = p.content;
                        ASTParser parserT1 = ASTParser.newParser(AST.JLS8);
                        parserT1.setKind(ASTParser.K_EXPRESSION);
                        parserT1.setSource(p.content.toCharArray()); // read java file and remove prefix (-,+)
                        Expression expression = (Expression) parserT1.createAST(null); // creat ast
                        newInvocation.arguments().add(i, (Expression)  ASTNode.copySubtree(ast,expression));
                    }
                    else{
                        Expression exp = (Expression) node.arguments().get(p.index);
                        newInvocation.arguments().add(i,ASTNode.copySubtree(ast,exp));
                    }
                }
                rewriter.replace(node,newInvocation,null);

            }
            return super.visit(node);
        }
    }
    public static APIUsage generatedMethodAPIUsage(APIUsage generatedAffectedUsage,APIUsage adaptationAUsage){
        String methodName = adaptationAUsage.name;
        String parentExpression = generatedAffectedUsage.parentExpression;
        List<Parameter> parameters = new LinkedList<>();
        for (Parameter p:adaptationAUsage.parameters){
            if (p.index==-1){parameters.add(p);}
            else {parameters.add(generatedAffectedUsage.parameters.get(p.index));}
        }
        return new APIUsage(methodName,parameters,parentExpression);
    }

    // read java file and remove prefix (-,+) and creat ast ? yes for the find apis and parameters
    public static APIUsage getAPIUsageFromMethodName(String brokenMethodName,String sourcecode){
        ASTParser parser = ASTParser.newParser(AST.JLS8);
        parser.setSource(sourcecode.toCharArray()); // read java file and remove prefix (-,+)
        parser.setKind(ASTParser.K_STATEMENTS);
        Block block = (Block) parser.createAST(null); // creat ast
        BrokenMethodVisitor bmv = new BrokenMethodVisitor();
        bmv.targetMethodName = brokenMethodName;
        block.accept(bmv);
        return bmv.apiUsage;
    }
    public static APIUsage getAPIUsageFromParametersName(APIUsage targetApiUsage,String sourcecode){
        ASTParser parser = ASTParser.newParser(AST.JLS8);
        parser.setSource(sourcecode.toCharArray()); // read java file and remove prefix (-,+)
        parser.setKind(ASTParser.K_STATEMENTS);
        Block block = (Block) parser.createAST(null); // creat ast
        AlterMethodVisitor amv = new AlterMethodVisitor();
        amv.targetApiUsage = targetApiUsage;
        block.accept(amv);
        return amv.apiUsage;
    }

    public static String getGeneratedAffectedSource(int generatedAffectedLength,String affectedASource,String affectedBSource){
        StringBuilder generatedAffectedSource = new StringBuilder();
        int affectedALength = affectedASource.split("\n").length;
        int affectedBLength = affectedBSource.split("\n").length;
        if(generatedAffectedLength >= affectedBLength){return affectedBSource;} // todo: generate affected code from original sourcecode
        else{
            for(int i=0;i<generatedAffectedLength;i++){
                String line = affectedBSource.split("\n")[i];
                generatedAffectedSource.append(line);
            }
            return generatedAffectedSource.toString();
        }
    }

    public static String getFileContentWithoutPrefix(String filePath) throws IOException {
        StringBuilder content= new StringBuilder();
        String line;
        BufferedReader br = new BufferedReader(new FileReader(filePath));
        while ((line= br.readLine())!=null){
            line = line.substring(1);
            content.append(line);
        }
        br.close();
        return content.toString();
    }
}

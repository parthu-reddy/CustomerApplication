import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class DeLombokTool {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Usage: java DeLombokTool <file1> <file2> ...");
            System.exit(1);
        }
        
        ParserConfiguration parserConfiguration = new ParserConfiguration();
        parserConfiguration.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
        StaticJavaParser.setConfiguration(parserConfiguration);

        for (String filePath : args) {
            File file = new File(filePath);
            if (!file.exists()) continue;

            CompilationUnit cu = StaticJavaParser.parse(file);
            List<Node> nodesToRemove = new ArrayList<>();
            List<ClassOrInterfaceDeclaration> modifiedClasses = new ArrayList<>();

            cu.findAll(MethodDeclaration.class).forEach(n -> {
                if (hasSuppressWarningsAll(n.getAnnotations())) {
                    nodesToRemove.add(n);
                    n.findAncestor(ClassOrInterfaceDeclaration.class).ifPresent(modifiedClasses::add);
                }
            });

            cu.findAll(ConstructorDeclaration.class).forEach(n -> {
                if (hasSuppressWarningsAll(n.getAnnotations())) {
                    nodesToRemove.add(n);
                    n.findAncestor(ClassOrInterfaceDeclaration.class).ifPresent(modifiedClasses::add);
                }
            });

            cu.findAll(ClassOrInterfaceDeclaration.class).forEach(n -> {
                if (hasSuppressWarningsAll(n.getAnnotations())) {
                    nodesToRemove.add(n);
                }
            });

            if (nodesToRemove.isEmpty()) {
                continue; // No changes needed
            }

            nodesToRemove.forEach(Node::remove);

            // Only add lombok annotations to dto, entity, model
            if (filePath.contains("/dto/") || filePath.contains("/entity/") || filePath.contains("/model/")) {
                for (ClassOrInterfaceDeclaration c : modifiedClasses) {
                    if (!c.isNestedType() || (c.isNestedType() && c.getParentNode().isPresent() && c.getParentNode().get() instanceof ClassOrInterfaceDeclaration && !nodesToRemove.contains(c))) {
                        if (!c.isAnnotationPresent("Data")) c.addAnnotation("lombok.Data");
                        if (!c.isAnnotationPresent("Builder")) c.addAnnotation("lombok.Builder");
                        if (!c.isAnnotationPresent("NoArgsConstructor")) c.addAnnotation("lombok.NoArgsConstructor");
                        if (!c.isAnnotationPresent("AllArgsConstructor")) c.addAnnotation("lombok.AllArgsConstructor");
                    }
                }
            }

            Files.write(Paths.get(filePath), cu.toString().getBytes());
            System.out.println("Cleaned up: " + filePath);
        }
    }

    private static boolean hasSuppressWarningsAll(NodeList<AnnotationExpr> annotations) {
        for (AnnotationExpr ann : annotations) {
            if (ann.getNameAsString().equals("SuppressWarnings") || ann.getNameAsString().equals("java.lang.SuppressWarnings")) {
                if (ann instanceof SingleMemberAnnotationExpr) {
                    String val = ((SingleMemberAnnotationExpr) ann).getMemberValue().toString();
                    if (val.contains("\"all\"")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}

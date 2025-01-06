package TypeTransformationTool;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.PrettyPrinter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class TypeRemoverAndCaster {

    private static Map<String, Type> variableTypeMap = new HashMap<>();

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: java -jar TypeTransformationTool.jar <input-file> <output-file>");
            return;
        }

        String inputFilePath = args[0];
        String outputFilePath = args[1];

        try {
            CompilationUnit cu = StaticJavaParser.parse(new File(inputFilePath));
            cu.accept(new TypeRemoverAndCasterVisitor(), null);
            PrettyPrinter prettyPrinter = new PrettyPrinter();
            String transformedCode = prettyPrinter.print(cu);
            Files.write(Paths.get(outputFilePath), transformedCode.getBytes());
            System.out.println("Transformation complete. Output written to " + outputFilePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class TypeRemoverAndCasterVisitor extends ModifierVisitor<Void> {

        @Override
        public VariableDeclarationExpr visit(VariableDeclarationExpr vde, Void arg) {
            Type objectType = StaticJavaParser.parseType("Object");
            List<VariableDeclarator> variableDeclarators = vde.getVariables();
            for (VariableDeclarator declarator : variableDeclarators) {
                String varName = declarator.getNameAsString();
                Type originalType = declarator.getType().clone();
                variableTypeMap.put(varName, originalType);

                declarator.setType(objectType);

                if (declarator.getInitializer().isPresent()) {
                    Expression initializer = declarator.getInitializer().get();
                    CastExpr castExpr = new CastExpr(originalType, initializer.clone());
                    declarator.setInitializer(castExpr);
                }
            }
            return (VariableDeclarationExpr) super.visit(vde, arg);
        }

        @Override
        public MethodCallExpr visit(MethodCallExpr mc, Void arg) {
            Optional<Node> parentNode = mc.getParentNode();
            if (parentNode.isPresent() && parentNode.get() instanceof AssignExpr) {
                AssignExpr assignExpr = (AssignExpr) parentNode.get();
                Expression target = assignExpr.getTarget();
                if (target instanceof NameExpr) {
                    String varName = ((NameExpr) target).getNameAsString();
                    Type originalType = variableTypeMap.get(varName);
                    if (originalType != null) {
                        CastExpr castExpr = new CastExpr(originalType.clone(), mc.clone());
                        mc.replace(castExpr);
                    }
                }
            }
            return (MethodCallExpr) super.visit(mc, arg);
        }

        @Override
        public Visitable visit(MethodDeclaration md, Void arg) {
            Map<String, Type> previousMap = new HashMap<>(variableTypeMap);
            variableTypeMap.clear();
            super.visit(md, arg);
            variableTypeMap = previousMap;
            return md;
        }

        @Override
        public Visitable visit(BlockStmt bs, Void arg) {
            return super.visit(bs, arg);
        }
    }
}

package com.aicr.poc;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Random;

/**
 * Generates Java test files of various sizes for performance testing
 */
public class TestCodeGenerator {

    private static final Random random = new Random();

    /**
     * Generate a test Java file with specified number of lines
     *
     * @param targetLines Approximate number of lines to generate
     * @param outputFile Output file path
     * @throws IOException if file writing fails
     */
    public static void generateTestFile(int targetLines, File outputFile) throws IOException {
        try (FileWriter writer = new FileWriter(outputFile)) {
            int linesWritten = 0;

            // Package and imports (5-10 lines)
            writer.write("package com.example.test;\n\n");
            writer.write("import java.util.*;\n");
            writer.write("import java.io.*;\n");
            writer.write("import java.time.*;\n");
            writer.write("import java.util.concurrent.*;\n\n");
            linesWritten += 7;

            // Main class
            writer.write("/**\n");
            writer.write(" * Auto-generated test class for performance testing\n");
            writer.write(" * Target lines: " + targetLines + "\n");
            writer.write(" */\n");
            writer.write("public class TestClass" + targetLines + " {\n\n");
            linesWritten += 6;

            // Calculate number of methods needed
            int methodsNeeded = Math.max(1, (targetLines - linesWritten - 5) / 15);
            int classesNeeded = Math.max(1, methodsNeeded / 10);

            // Generate fields
            for (int i = 0; i < Math.min(10, classesNeeded * 2); i++) {
                writer.write("    private String field" + i + ";\n");
                linesWritten++;
            }
            writer.write("\n");
            linesWritten++;

            // Generate methods in main class
            int methodsInMainClass = Math.min(methodsNeeded, 15);
            for (int i = 0; i < methodsInMainClass; i++) {
                linesWritten += generateMethod(writer, i);
            }

            // Generate inner classes with methods
            int remainingMethods = methodsNeeded - methodsInMainClass;
            for (int i = 0; i < classesNeeded && linesWritten < targetLines - 10; i++) {
                int methodsPerClass = Math.min(10, remainingMethods / Math.max(1, classesNeeded - i));
                linesWritten += generateInnerClass(writer, i, methodsPerClass);
                remainingMethods -= methodsPerClass;
            }

            // Close main class
            writer.write("}\n");
            linesWritten++;

            System.out.println("Generated " + outputFile.getName() + " with approximately " + linesWritten + " lines");
        }
    }

    /**
     * Generate a single method
     */
    private static int generateMethod(FileWriter writer, int index) throws IOException {
        int lines = 0;
        String[] returnTypes = {"String", "int", "boolean", "void", "List<String>", "Map<String, Object>"};
        String returnType = returnTypes[random.nextInt(returnTypes.length)];

        writer.write("    /**\n");
        writer.write("     * Method documentation " + index + "\n");
        writer.write("     */\n");
        lines += 3;

        writer.write("    public " + returnType + " method" + index + "(String param1, int param2) {\n");
        lines++;

        // Add some logic
        writer.write("        System.out.println(\"Executing method" + index + "\");\n");
        writer.write("        if (param2 > 0) {\n");
        writer.write("            return " + getReturnValue(returnType) + ";\n");
        writer.write("        }\n");
        lines += 4;

        // Add a loop
        writer.write("        for (int i = 0; i < param2; i++) {\n");
        writer.write("            System.out.println(i);\n");
        writer.write("        }\n");
        lines += 3;

        writer.write("        " + getReturnStatement(returnType) + "\n");
        writer.write("    }\n\n");
        lines += 2;

        return lines;
    }

    /**
     * Generate an inner class with methods
     */
    private static int generateInnerClass(FileWriter writer, int classIndex, int methodCount) throws IOException {
        int lines = 0;

        writer.write("    /**\n");
        writer.write("     * Inner class " + classIndex + "\n");
        writer.write("     */\n");
        writer.write("    public static class InnerClass" + classIndex + " {\n\n");
        lines += 5;

        // Add fields
        writer.write("        private String innerField" + classIndex + ";\n");
        writer.write("        private int counter = 0;\n\n");
        lines += 3;

        // Add constructor
        writer.write("        public InnerClass" + classIndex + "() {\n");
        writer.write("            this.innerField" + classIndex + " = \"initialized\";\n");
        writer.write("        }\n\n");
        lines += 4;

        // Add methods
        for (int i = 0; i < methodCount; i++) {
            lines += generateMethod(writer, classIndex * 100 + i);
        }

        writer.write("    }\n\n");
        lines += 2;

        return lines;
    }

    /**
     * Get a return value based on type
     */
    private static String getReturnValue(String returnType) {
        return switch (returnType) {
            case "String" -> "\"result\"";
            case "int" -> "42";
            case "boolean" -> "true";
            case "void" -> "";
            case "List<String>" -> "new ArrayList<>()";
            case "Map<String, Object>" -> "new HashMap<>()";
            default -> "null";
        };
    }

    /**
     * Get a return statement based on type
     */
    private static String getReturnStatement(String returnType) {
        if ("void".equals(returnType)) {
            return "return;";
        }
        return "return " + getReturnValue(returnType) + ";";
    }

    /**
     * Main method for standalone test file generation
     */
    public static void main(String[] args) throws IOException {
        int[] testSizes = {100, 500, 1000, 5000};
        File resourceDir = new File("src/test/resources");
        resourceDir.mkdirs();

        for (int size : testSizes) {
            File outputFile = new File(resourceDir, "sample-" + size + "-lines.java");
            generateTestFile(size, outputFile);
        }

        System.out.println("All test files generated successfully!");
    }
}

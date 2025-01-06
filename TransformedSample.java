package TypeTransformationTool;

public class Sample {

    public static void main(String[] args) {
        Object number = (int) 42;
        Object text = (String) "Hello, World!";
        System.out.println(text + " " + number);
    }
}

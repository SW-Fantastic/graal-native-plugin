import com.google.common.reflect.ClassPath;
import io.github.classgraph.*;

import java.util.ResourceBundle;

public class ScannerTest {

    public static void main(String[] args) {
        ClassGraph graph = new ClassGraph();
        graph.acceptModules("java.base");
        graph.acceptPackages("java.util");
        graph.enableAllInfo();
        ScanResult result = graph.scan();
        ClassInfoList list = result.getAllClasses();
        for (ClassInfo info: list) {
            System.err.println(info.getName());
        }
        ResourceList resources = result.getAllResources();
        for (Resource resourceInfo : resources) {
            if (resourceInfo.getPath().endsWith("class")) {
                continue;
            }
            System.err.println(resourceInfo.getModuleRef().getName() + " - " + resourceInfo.getPath());
        }
    }

}

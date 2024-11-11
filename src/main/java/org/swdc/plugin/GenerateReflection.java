package org.swdc.plugin;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.classgraph.*;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.artifact.resolver.filter.IncludesArtifactFilter;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilderException;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.apache.maven.shared.dependency.graph.traversal.CollectingDependencyNodeVisitor;

import javax.inject.Inject;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * 在这里扫描编译好的类，并且为他们生成Graal需要的反射数据。
 *
 * 首先是plugin作用的工程，
 * 然后允许配置指定其他需要生成的Package。
 *
 */
@Mojo(name = "gen-reflect")
public class GenerateReflection extends AbstractMojo {

    @Parameter(required = true,property = "packages")
    private String[] packages;

    @Inject
    private MavenProject project;

    @Inject
    private RepositorySystem system;

    @Component
    private DependencyGraphBuilder dependencyGraphBuilder;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().info("Generate reflection data for graal");

        List<String> cp = new ArrayList<>();
        List<String> jarNames = new ArrayList<>();
        cp.add(project.getBuild().getOutputDirectory());

        ClassGraph graph = new ClassGraph();

        for (String name: packages) {
            if (name.isBlank() || name.isEmpty()) {
                continue;
            }
            graph = graph.acceptPackages(name.startsWith(">") ? name.substring(1):name);
        }

        Set<Artifact> artifacts = new HashSet<>();
        DefaultProjectBuildingRequest request = new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
        request.setProject(project);

        try {
            DependencyNode node = dependencyGraphBuilder.buildDependencyGraph(request,null);
            CollectingDependencyNodeVisitor visitor = new CollectingDependencyNodeVisitor();
            node.accept(visitor);
            List<DependencyNode> children = visitor.getNodes();
            for (DependencyNode item: children) {
                artifacts.add(item.getArtifact());
            }
        } catch (DependencyGraphBuilderException e) {
            throw new RuntimeException(e);
        }

        for (Artifact artifact: artifacts) {
            ArtifactResolutionRequest resolutionRequest = new ArtifactResolutionRequest();
            resolutionRequest.setArtifact(artifact);
            ArtifactResolutionResult resolved = system.resolve(resolutionRequest);
            if (resolved.getArtifacts().size() > 0) {
                artifacts.addAll(resolved.getArtifacts());
            }
        }

        Set<ResourceItem> resourceItems = new HashSet<>();

        for (Artifact artifact: artifacts) {
            if (artifact.getFile() != null) {
                cp.add(artifact.getFile().getAbsolutePath());
                jarNames.add(artifact.getFile().getName());
                if (artifact.getFile().isDirectory()) {
                    continue;
                }

                try {
                    resourceItems.addAll(
                            generateResources(artifact.getFile())
                    );
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                getLog().warn("No jar file : " + artifact.getGroupId() + ":" + artifact.getArtifactId());
            }
        }

        ScanResult result = graph
                .enableAllInfo()
                .overrideClasspath(cp.toArray())
                .acceptJars(jarNames.toArray(String[]::new))
                .scan();

        Set<ReflectionItem> items = new LinkedHashSet<>();

        List<ClassInfo> infos = result.getAllClasses();
        for (ClassInfo info: infos) {
            getLog().info("resolve: " + info.getName());
            // 扫描到的类，加载他们，获取反射对象然后写配置。
            items.addAll(ReflectionItem.resolve(info));
        }

        String root = project.getCompileSourceRoots()
                .stream()
                .findFirst()
                .orElse(null);

        if (root == null) {
            getLog().info("can not find source root.");
            return;
        }

        File sourceRoot = new File(root);
        Path resourcePath = sourceRoot.getAbsoluteFile().getParentFile()
                .toPath().resolve("resources/META-INF/native-image/" + project.getGroupId() + "/" + project.getArtifactId());

        try {
            if (!Files.exists(resourcePath)) {
                Files.createDirectories(resourcePath);
            }
            Path reflection = resourcePath.resolve("reachability-metadata.json");
            Map<String,Object> objects = null;
            ObjectMapper mapper = new ObjectMapper();

            if (Files.exists(reflection)) {
                JavaType type = mapper.getTypeFactory().constructParametricType(Map.class,String.class,Object.class);
                byte[] data = Files.readAllBytes(reflection);
                objects = mapper.readValue(data, type);
            } else {
                objects = new HashMap<>();
            }

            /*List<ReflectionItem> swingItems = generateSwingClasses();
            swingItems.add(new ReflectionItem("java.util.Locale"));
            swingItems.add(new ReflectionItem("java.lang.String"));
            swingItems.add(new ReflectionItem("java.util.HashMap"));
            items.add(new ReflectionItem("java.util.Locale"));
            items.add(new ReflectionItem("java.util.HashMap"));
            items.add(new ReflectionItem("java.lang.String"));*/

            objects.put("reflection",items);
            objects.put("resources", resourceItems);
            //objects.put("jni", swingItems);
            //objects.put("bundles", generateSwingBundle());

            Files.writeString(reflection,mapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(objects));

            File target = new File(project.getBuild().getOutputDirectory());
            Path targetReflect = target.toPath().resolve("/META-INF/native-image/" + project.getGroupId() + "/" + project.getArtifactId());
            if(!Files.exists(targetReflect)) {
                Files.createDirectories(targetReflect);
            }

            Path buildRes = targetReflect.resolve("reachability-metadata.json");
            if (Files.exists(buildRes)) {
                Files.delete(buildRes);
            }

            Files.writeString(buildRes,mapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(objects));

        } catch (Exception e) {
            getLog().error(e);
        }

        getLog().info("Reflection Generate complete");
    }

    public List<ReflectionItem> generateCommons() {
        List<ReflectionItem> items = new ArrayList<>();

        ClassGraph graph = new ClassGraph();
        graph.ignoreFieldVisibility();
        graph.acceptModules("java.base");
        graph.acceptPackages("java.util");
        graph.enableAllInfo();
        ScanResult result = graph.scan();
        ClassInfoList list = result.getAllClasses();

        for (ClassInfo info: list) {

            ReflectionItem item = new ReflectionItem(info.getName());
            List<ReflectionField> fields = info.getDeclaredFieldInfo().stream()
                    .map(FieldInfo::getName)
                    .map(ReflectionField::new)
                    .collect(Collectors.toList());
            item.setFields(fields);
            items.add(item);

        }
        return items;
    }

    public List<ReflectionItem> generateSwingClasses() {
        List<ReflectionItem> items = new ArrayList<>();
        ClassGraph graph = new ClassGraph();
        graph.ignoreFieldVisibility();
        graph.acceptModules("java.desktop");
        graph.acceptPackages("sun.awt","sun.java2d","sun.font","java.awt","javax.swing");
        graph.enableAllInfo();
        ScanResult result = graph.scan();
        ClassInfoList list = result.getAllClasses();
        for (ClassInfo info: list) {

            ReflectionItem item = new ReflectionItem(info.getName());
            List<ReflectionField> fields = info.getDeclaredFieldInfo().stream()
                    .map(FieldInfo::getName)
                    .map(ReflectionField::new)
                    .collect(Collectors.toList());
            item.setFields(fields);
            items.add(item);

        }
        return items;
    }

    public List<ResourceItem> generateResources(File jar) throws IOException {
        List<ResourceItem> items = new ArrayList<>();
        getLog().info("populate resource : " + jar.getName());
        ZipInputStream zin = new ZipInputStream(new FileInputStream(jar));
        ZipEntry entry = null;
        while ((entry = zin.getNextEntry()) != null) {
            if (!entry.getName().endsWith("class") && !entry.isDirectory()) {
                ResourceItem resourceItem = new ResourceItem();
                resourceItem.setGlob(entry.getName());
                items.add(resourceItem);
                getLog().info("Resource: " + entry.getName());
            }
        }
        zin.close();
        return items;
    }

    public List<BundleItem> generateSwingBundle() {

        return Arrays.asList(
                new BundleItem("sun.awt.resources.awt"),
                new BundleItem("com.sun.swing.internal.plaf.basic.resources.basic"),
                new BundleItem("com.sun.swing.internal.plaf.metal.resources.metal")
        );

    }

}

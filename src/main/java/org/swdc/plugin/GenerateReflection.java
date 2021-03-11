package org.swdc.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.repository.RepositorySystem;
import org.eclipse.aether.impl.ArtifactResolver;
import org.eclipse.aether.repository.RemoteRepository;

import javax.inject.Inject;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

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

        List<Artifact> artifacts = new ArrayList<>();

        for (Dependency dependency : project.getDependencies()) {
            Artifact artifact = system.createDependencyArtifact(dependency);
            ArtifactResolutionRequest resolutionRequest = new ArtifactResolutionRequest();
            resolutionRequest.setArtifact(artifact);
            ArtifactResolutionResult resolved = system.resolve(resolutionRequest);
            if (resolved.getArtifacts().size() > 0) {
                artifacts.addAll(resolved.getArtifacts());
            }
        }

        for (Artifact artifact: artifacts) {
            cp.add(artifact.getFile().getAbsolutePath());
            jarNames.add(artifact.getFile().getName());
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
            Path reflection = resourcePath.resolve("reflect-config.json");
            if (Files.exists(reflection)) {
                Files.delete(reflection);
            }
            ObjectMapper mapper = new ObjectMapper();
            Files.writeString(reflection,mapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(items));

            File target = new File(project.getBuild().getOutputDirectory());
            Path targetReflect = target.toPath().resolve("/META-INF/native-image/" + project.getGroupId() + "/" + project.getArtifactId());
            if(!Files.exists(targetReflect)) {
                Files.createDirectories(targetReflect);
            }

            Path buildRes = targetReflect.resolve("reflect-config.json");
            if (Files.exists(buildRes)) {
                Files.delete(buildRes);
            }

            Files.writeString(buildRes,mapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(items));

        } catch (Exception e) {
            getLog().error(e);
        }

        getLog().info("Reflection Generate complete");
    }

}
